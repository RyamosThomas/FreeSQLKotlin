package freesql.provider.sqlite.curd

import freesql.core.*
import freesql.model.TableInfo
import freesql.provider.sqlite.*
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * SQLite DELETE builder with expression-based API.
 * Supports PK-based WHERE, whereDynamic, and global filter injection.
 *
 * Features:
 * - whereDynamic with entity PK extraction (entities or plain ID values)
 * - Batch delete with IN clause optimization for single PK
 * - setSource with single or multiple entities (auto-builds WHERE by PK)
 * - AsTable sharding
 * - GlobalFilter support
 */
class SqliteDelete<T : Any>(
    private val entityType: KClass<T>,
    private val ado: IAdo,
    private val utils: SqliteUtils,
    private val codeFirst: SqliteCodeFirst
) : IDelete<T> {

    private val quote: (String) -> String = { utils.quoteSqlName(it) }

    private val sources = mutableListOf<T>()
    private val whereParts = mutableListOf<String>()
    private val whereParams = mutableMapOf<String, Any?>()
    private var tableOverride: String? = null
    private var paramCounter = 0

    // Global filter
    private var globalFilter: GlobalFilter? = null

    private val table: TableInfo by lazy {
        codeFirst.buildTableInfo(entityType)
    }

    private val tableName: String
        get() = tableOverride ?: utils.quoteSqlName(table.dbName)

    private fun nextParamName(prefix: String = "d"): String {
        return "@_${prefix}${paramCounter++}"
    }

    fun withGlobalFilter(filter: GlobalFilter?): SqliteDelete<T> {
        this.globalFilter = filter
        return this
    }

    // --- Source ---

    override fun setSource(source: T): IDelete<T> {
        sources.add(source)
        return this
    }

    override fun setSource(sources: Collection<T>): IDelete<T> {
        this.sources.addAll(sources)
        return this
    }

    // --- WHERE ---

    override fun where(predicate: (TableColumns<T>) -> SqlExpr): IDelete<T> {
        val tableColumns = TableColumns(entityType)
        val expr = predicate(tableColumns)
        val sql = expr.toSql(quote)
        whereParts.add("($sql)")
        return this
    }

    override fun where(sql: String, vararg params: Any?): IDelete<T> {
        var paramSql = sql
        params.forEach { value ->
            val paramName = nextParamName()
            paramSql = paramSql.replaceFirst("?", paramName)
            whereParams[paramName] = utils.formatParameter(value)
        }
        whereParts.add("($paramSql)")
        return this
    }

    override fun whereExpr(expr: SqlExpr): IDelete<T> {
        var sql = expr.toSql(quote)
        // Strip table-qualified column names (e.g. "users"."name" → "name")
        // SQLite doesn't allow them in DELETE WHERE clauses
        val quotedTable = utils.quoteSqlName(table.dbName)
        sql = sql.replace("$quotedTable.", "")
        whereParts.add("($sql)")
        return this
    }

    override fun whereDynamic(ids: Collection<Any>): IDelete<T> {
        if (ids.isEmpty()) return this

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
            // Extract PK values from entities for batch delete with IN clause
            val pkValues = ids.map { entity ->
                val prop = entityType.memberProperties.find { it.name == pk.csName }
                prop?.call(entity)
            }
            buildInClause(pk, pkValues)
        } else {
            // Plain ID values
            buildInClause(pk, ids.toList())
        }
        return this
    }

    /**
     * Build an optimized IN clause for batch delete with single primary key.
     */
    private fun buildInClause(pk: freesql.model.ColumnInfo, values: List<Any?>) {
        val placeholders = values.map { value ->
            val paramName = nextParamName()
            whereParams[paramName] = utils.formatParameter(value)
            paramName
        }
        whereParts.add("${utils.quoteSqlName(pk.dbName)} IN (${placeholders.joinToString(", ")})")
    }

    // --- Modifiers ---

    override fun asTable(tableName: String): IDelete<T> {
        tableOverride = tableName
        return this
    }

    // --- Execution ---

    override fun executeAffrows(): Int {
        return ado.executeNonQuery(toSql(), getAllParams())
    }

    override fun toSql(): String {
        val sb = StringBuilder()
        sb.append("DELETE FROM $tableName")

        // Build WHERE from source entities (by PK) if no explicit WHERE
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

    private fun buildWhereFromSources() {
        val pks = table.primarys
        if (pks.isEmpty()) return

        if (pks.size == 1) {
            // Single PK: use IN clause optimization for batch delete
            val pk = pks[0]
            val pkValues = sources.map { entity ->
                getEntityValue(entity, pk.csName)
            }
            buildInClause(pk, pkValues)
        } else {
            // Composite PK: use OR conditions
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
    }

    private fun getAllParams(): Map<String, Any?> {
        val allParams = mutableMapOf<String, Any?>()
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
