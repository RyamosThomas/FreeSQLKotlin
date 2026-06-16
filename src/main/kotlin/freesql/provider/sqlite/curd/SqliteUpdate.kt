package freesql.provider.sqlite.curd

import freesql.annotation.ServerTimeType
import freesql.core.*
import freesql.model.TableInfo
import freesql.provider.sqlite.*
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * SQLite UPDATE builder with expression-based API.
 * Supports batch updates via CASE/WHEN and PK-based WHERE.
 * Max 200 rows per batch, 999 parameters per statement.
 *
 * Features:
 * - Single entity update with SET from values
 * - Multi-row CASE/WHEN batch update
 * - SplitExecuteAffrows (auto-batching: 200 rows / 999 params)
 * - SetIf (conditional SET)
 * - WhereDynamic (PK extraction from entities or ID list)
 * - ToSqlCase / ToSqlWhen for composite PKs (|| '+' || concatenation)
 * - UpdateColumns / IgnoreColumns with ColumnRef
 * - AsTable sharding
 * - GlobalFilter support
 */
class SqliteUpdate<T : Any>(
    private val entityType: KClass<T>,
    private val ado: IAdo,
    private val utils: SqliteUtils,
    private val codeFirst: SqliteCodeFirst
) : IUpdate<T> {

    private val quote: (String) -> String = { utils.quoteSqlName(it) }

    private val sources = mutableListOf<T>()
    private val setParts = mutableListOf<String>()
    private val setParams = mutableMapOf<String, Any?>()
    private val whereParts = mutableListOf<String>()
    private val whereParams = mutableMapOf<String, Any?>()
    private var updateColumns: List<String>? = null
    private var ignoreColumns: List<String>? = null
    private var tableOverride: String? = null
    private var paramCounter = 0

    // Global filter
    private var globalFilter: GlobalFilter? = null

    // Batch limits
    /** Maximum rows per batch for CASE/WHEN updates. */
    val _batchRowsLimit = 200
    /** Maximum parameters per SQL statement. */
    val _batchParameterLimit = 999

    private val table: TableInfo by lazy {
        codeFirst.buildTableInfo(entityType)
    }

    private val tableName: String
        get() = tableOverride ?: utils.quoteSqlName(table.dbName)

    private fun nextParamName(prefix: String = "u"): String {
        return "@_${prefix}${paramCounter++}"
    }

    fun withGlobalFilter(filter: GlobalFilter?): SqliteUpdate<T> {
        this.globalFilter = filter
        return this
    }

    // --- Source ---

    override fun setSource(source: T): IUpdate<T> {
        sources.add(source)
        return this
    }

    override fun setSource(sources: Collection<T>): IUpdate<T> {
        this.sources.addAll(sources)
        return this
    }

    // --- SET ---

    override fun <TMember> set(column: ColumnRef<TMember>, value: TMember): IUpdate<T> {
        val paramName = nextParamName()
        // Use unqualified column name in SET (SQLite doesn't allow table.column in SET)
        val colSql = column.toSql(quote).replace("${utils.quoteSqlName(table.dbName)}.", "")
        setParts.add("$colSql = $paramName")
        setParams[paramName] = utils.formatParameter(value)
        return this
    }

    override fun <TMember> setIf(condition: Boolean, column: ColumnRef<TMember>, value: TMember): IUpdate<T> {
        if (condition) {
            set(column, value)
        }
        return this
    }

    override fun setRaw(column: String, sql: String, vararg params: Any?): IUpdate<T> {
        var paramSql = sql
        params.forEach { value ->
            val paramName = nextParamName()
            paramSql = paramSql.replaceFirst("?", paramName)
            setParams[paramName] = utils.formatParameter(value)
        }
        setParts.add("${utils.quoteSqlName(column)} = $paramSql")
        return this
    }

    override fun setDto(dto: Any): IUpdate<T> {
        val props = dto::class.memberProperties
        props.forEach { prop ->
            val value = prop.call(dto)
            if (value != null) {
                val paramName = nextParamName()
                setParts.add("${utils.quoteSqlName(prop.name)} = $paramName")
                setParams[paramName] = utils.formatParameter(value)
            }
        }
        return this
    }

    // --- WHERE ---

    override fun where(predicate: (TableColumns<T>) -> SqlExpr): IUpdate<T> {
        val tableColumns = TableColumns(entityType)
        val expr = predicate(tableColumns)
        val sql = expr.toSql(quote)
        whereParts.add("($sql)")
        return this
    }

    override fun where(sql: String, vararg params: Any?): IUpdate<T> {
        var paramSql = sql
        params.forEach { value ->
            val paramName = nextParamName()
            paramSql = paramSql.replaceFirst("?", paramName)
            whereParams[paramName] = utils.formatParameter(value)
        }
        whereParts.add("($paramSql)")
        return this
    }

    override fun whereExpr(expr: SqlExpr): IUpdate<T> {
        var sql = expr.toSql(quote)
        // Strip table-qualified column names (e.g. "users"."name" → "name")
        // SQLite doesn't allow them in UPDATE WHERE clauses
        val quotedTable = utils.quoteSqlName(table.dbName)
        sql = sql.replace("$quotedTable.", "")
        whereParts.add("($sql)")
        return this
    }

    override fun whereDynamic(ids: Collection<Any>): IUpdate<T> {
        val pks = table.primarys
        if (pks.size != 1) {
            throw IllegalStateException("whereDynamic requires exactly one primary key")
        }
        val pk = pks[0]

        // Check if the items are entities (have the PK property) or plain IDs
        val firstItem = ids.first()
        val isEntity = try {
            entityType.memberProperties.any { it.name == pk.csName && it.call(firstItem) != null }
        } catch (_: Exception) {
            false
        }

        if (isEntity) {
            // Extract PK values from entities
            val pkValues = ids.map { entity ->
                val prop = entityType.memberProperties.find { it.name == pk.csName }
                prop?.call(entity)
            }
            val placeholders = pkValues.map { pkValue ->
                val paramName = nextParamName()
                whereParams[paramName] = utils.formatParameter(pkValue)
                paramName
            }
            whereParts.add("${utils.quoteSqlName(pk.dbName)} IN (${placeholders.joinToString(", ")})")
        } else {
            // Plain ID values
            val placeholders = ids.map { id ->
                val paramName = nextParamName()
                whereParams[paramName] = utils.formatParameter(id)
                paramName
            }
            whereParts.add("${utils.quoteSqlName(pk.dbName)} IN (${placeholders.joinToString(", ")})")
        }
        return this
    }

    // --- Column filtering ---

    override fun updateColumns(vararg columns: ColumnRef<*>): IUpdate<T> {
        updateColumns = columns.map { it.columnName }
        return this
    }

    override fun ignoreColumns(vararg columns: ColumnRef<*>): IUpdate<T> {
        ignoreColumns = columns.map { it.columnName }
        return this
    }

    // --- Modifiers ---

    override fun asTable(tableName: String): IUpdate<T> {
        tableOverride = tableName
        return this
    }

    // --- Split execution ---

    override fun splitExecuteAffrows(): Int {
        if (sources.isEmpty()) return 0
        val pks = table.primarys
        if (pks.isEmpty()) return 0

        val updatableCols = getUpdatableColumns()
        val paramsPerRow = updatableCols.size + pks.size
        val effectiveBatchSize = if (paramsPerRow > 0) {
            minOf(_batchRowsLimit, _batchParameterLimit / paramsPerRow)
        } else {
            _batchRowsLimit
        }

        var totalAffected = 0
        sources.chunked(effectiveBatchSize).forEach { batch ->
            // Build a CASE/WHEN update for each batch
            val savedSources = mutableListOf<T>()
            savedSources.addAll(this.sources)
            this.sources.clear()
            setParts.clear()
            setParams.clear()
            whereParts.clear()
            whereParams.clear()

            this.sources.addAll(batch)
            val sql = toSql()
            val allParams = getAllParams()
            totalAffected += ado.executeNonQuery(sql, allParams)

            this.sources.clear()
            this.sources.addAll(savedSources)
            setParts.clear()
            setParams.clear()
            whereParts.clear()
            whereParams.clear()
        }
        return totalAffected
    }

    // --- Execution ---

    override fun executeAffrows(): Int {
        if (setParts.isEmpty() && sources.isEmpty()) return 0
        val sql = toSql()
        val allParams = getAllParams()
        return ado.executeNonQuery(sql, allParams)
    }

    override fun toSql(): String {
        // If we have sources and no explicit SET parts, build SET from entity values
        if (sources.isNotEmpty() && setParts.isEmpty()) {
            buildSetFromSources()
        }

        if (setParts.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("UPDATE $tableName SET ")
        sb.append(setParts.joinToString(", "))

        // WHERE from source entities (by PK)
        if (sources.isNotEmpty() && whereParts.isEmpty()) {
            buildWhereFromSources()
        }

        // Global filters
        val allWhereParts = mutableListOf<String>()
        globalFilter?.getFilters(entityType)?.forEach { filter ->
            allWhereParts.add(filter.where("a"))
        }
        allWhereParts.addAll(whereParts)

        if (allWhereParts.isNotEmpty()) {
            sb.append(" WHERE ")
            sb.append(allWhereParts.joinToString(" AND "))
        }

        return sb.toString()
    }

    // --- Internal ---

    private fun getUpdatableColumns() = table.columns.filter { col ->
        if (col.isIgnore) return@filter false
        if (!col.canUpdate) return@filter false
        if (col.isIdentity) return@filter false
        if (updateColumns != null) return@filter updateColumns!!.contains(col.dbName)
        if (ignoreColumns != null) return@filter !ignoreColumns!!.contains(col.dbName)
        true
    }

    private fun buildSetFromSources() {
        val updatableCols = getUpdatableColumns()

        if (sources.size == 1) {
            // Single entity update
            val entity = sources[0]
            updatableCols.forEach { col ->
                val serverTimeType = col.serverTime
                if (serverTimeType == ServerTimeType.UPDATE || serverTimeType == ServerTimeType.BOTH) {
                    setParts.add("${utils.quoteSqlName(col.dbName)} = datetime('now')")
                } else {
                    val value = getEntityValue(entity, col.csName)
                    val paramName = nextParamName()
                    setParts.add("${utils.quoteSqlName(col.dbName)} = $paramName")
                    setParams[paramName] = utils.formatParameter(value)
                }
            }
        } else if (sources.size <= _batchRowsLimit) {
            // Batch update using CASE/WHEN
            val pks = table.primarys
            if (pks.size == 1) {
                buildBatchCaseWhenUpdate(pks[0], updatableCols)
            } else if (pks.size > 1) {
                // Composite PK: use || '+' || concatenation for CASE/WHEN
                buildCompositeBatchCaseWhenUpdate(pks, updatableCols)
            }
        }
    }

    /**
     * Build CASE/WHEN batch update for single primary key.
     * Generates: UPDATE table SET col = CASE pk WHEN @pk0 THEN @val0 ... END WHERE pk IN (@pk0, ...)
     */
    private fun buildBatchCaseWhenUpdate(pkCol: freesql.model.ColumnInfo, updatableCols: List<freesql.model.ColumnInfo>) {
        val pkValues = sources.map { entity ->
            getEntityValue(entity, pkCol.csName)
        }

        updatableCols.forEach { col ->
            val caseWhen = StringBuilder()
            caseWhen.append("CASE ${utils.quoteSqlName(pkCol.dbName)} ")
            pkValues.forEachIndexed { index, pkValue ->
                val pkParam = nextParamName()
                setParams[pkParam] = utils.formatParameter(pkValue)

                val valParam = nextParamName()
                val entity = sources[index]
                val value = getEntityValue(entity, col.csName)
                setParams[valParam] = utils.formatParameter(value)

                caseWhen.append("WHEN $pkParam THEN $valParam ")
            }
            caseWhen.append("END")
            setParts.add("${utils.quoteSqlName(col.dbName)} = $caseWhen")
        }

        // WHERE pk IN (values)
        val pkPlaceholders = pkValues.map { pkValue ->
            val paramName = nextParamName()
            setParams[paramName] = utils.formatParameter(pkValue)
            paramName
        }
        whereParts.add("${utils.quoteSqlName(pkCol.dbName)} IN (${pkPlaceholders.joinToString(", ")})")
    }

    /**
     * Build CASE/WHEN batch update for composite primary keys.
     * For composite PKs, concatenate key parts with || '+' || for ToSqlCase / ToSqlWhen.
     * Generates: UPDATE table SET col = CASE pk1||'+'||pk2 WHEN @v1 THEN @val1 ... END WHERE (pk1=@p1 AND pk2=@p2) OR ...
     */
    private fun buildCompositeBatchCaseWhenUpdate(
        pks: List<freesql.model.ColumnInfo>,
        updatableCols: List<freesql.model.ColumnInfo>
    ) {
        // ToSqlCase: concatenate PK columns with || '+' ||
        val caseExpr = pks.joinToString(" || '+' || ") { utils.quoteSqlName(it.dbName) }

        val pkValueGroups = sources.map { entity ->
            pks.map { pk -> getEntityValue(entity, pk.csName) }
        }

        updatableCols.forEach { col ->
            val caseWhen = StringBuilder()
            caseWhen.append("CASE $caseExpr ")
            pkValueGroups.forEachIndexed { index, pkValues ->
                // ToSqlWhen: concatenate PK values with || '+' ||
                val whenExpr = pkValues.joinToString(" || '+' || ") { pkValue ->
                    val pkParam = nextParamName()
                    setParams[pkParam] = utils.formatParameter(pkValue)
                    pkParam
                }

                val valParam = nextParamName()
                val entity = sources[index]
                val value = getEntityValue(entity, col.csName)
                setParams[valParam] = utils.formatParameter(value)

                caseWhen.append("WHEN $whenExpr THEN $valParam ")
            }
            caseWhen.append("END")
            setParts.add("${utils.quoteSqlName(col.dbName)} = $caseWhen")
        }

        // WHERE: (pk1=@v1 AND pk2=@v2) OR ...
        val pkConditions = sources.map { entity ->
            pks.map { pk ->
                val value = getEntityValue(entity, pk.csName)
                val paramName = nextParamName()
                whereParams[paramName] = utils.formatParameter(value)
                "${utils.quoteSqlName(pk.dbName)} = $paramName"
            }.joinToString(" AND ")
        }
        if (pkConditions.size == 1) {
            whereParts.add("(${pkConditions[0]})")
        } else {
            whereParts.add("(${pkConditions.joinToString(") OR (")})")
        }
    }

    private fun buildWhereFromSources() {
        val pks = table.primarys
        if (pks.isEmpty()) return

        val pkConditions = sources.map { entity ->
            pks.map { pk ->
                val value = getEntityValue(entity, pk.csName)
                val paramName = nextParamName()
                whereParams[paramName] = utils.formatParameter(value)
                "${utils.quoteSqlName(pk.dbName)} = $paramName"
            }.joinToString(" AND ")
        }

        if (pkConditions.size == 1) {
            whereParts.add("(${pkConditions[0]})")
        } else {
            whereParts.add("(${pkConditions.joinToString(") OR (")})")
        }
    }

    private fun getAllParams(): Map<String, Any?> {
        val allParams = mutableMapOf<String, Any?>()
        allParams.putAll(setParams)
        allParams.putAll(whereParams)

        globalFilter?.getFilters(entityType)?.forEach { filter ->
            allParams.putAll(filter.parameters())
        }

        return allParams
    }

    private fun getEntityValue(entity: Any, propertyName: String): Any? {
        val prop = entityType.memberProperties.find { it.name == propertyName }
        return prop?.call(entity)
    }
}
