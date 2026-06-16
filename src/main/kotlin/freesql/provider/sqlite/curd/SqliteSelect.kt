package freesql.provider.sqlite.curd

import freesql.core.*
import freesql.model.TableInfo
import freesql.provider.sqlite.*
import java.sql.ResultSet
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * SQLite SELECT query builder and executor.
 * Full rewrite with expression-based API using SqlExpr.
 *
 * Features:
 * - Expression-based WHERE, JOIN, GROUP BY, HAVING
 * - UNION ALL support
 * - Aggregate methods (avg, max, min, sum, count with predicate)
 * - Subquery support in SELECT fields
 * - Pagination, DISTINCT, ORDER BY
 * - Navigation loading (includeMany)
 * - Note: SQLite does NOT support FOR UPDATE / FOR SHARE / FOR NO WAIT.
 *   Use BEGIN IMMEDIATE or EXCLUSIVE transactions for write contention.
 */
class SqliteSelect<T : Any>(
    private val entityType: KClass<T>,
    private val ado: SqliteAdo,
    private val utils: SqliteUtils,
    private val expression: SqliteExpression,
    private val codeFirst: SqliteCodeFirst,
    private val globalFilter: GlobalFilter?
) : ISelect<T> {

    private val quote: (String) -> String = { utils.quoteSqlName(it) }

    private val whereParts = mutableListOf<String>()
    private val whereParams = mutableMapOf<String, Any?>()
    private val joins = mutableListOf<String>()
    private val orderByParts = mutableListOf<String>()
    private val groupByParts = mutableListOf<String>()
    private var havingPart: String? = null
    private var havingParams = mutableMapOf<String, Any?>()
    private var skipCount: Int = 0
    private var takeCount: Int = 0
    private var isDistinct: Boolean = false
    private var tableOverride: String? = null
    private var paramCounter = 0
    private val includeManys = mutableListOf<IncludeManySpec<*>>()
    private var selectColumnsOverride: String? = null

    // UNION ALL support
    private val unionParts = mutableListOf<String>()
    private val unionParams = mutableMapOf<String, Any?>()

    // FROM subquery support
    private var fromQuerySql: String? = null

    private val table: TableInfo by lazy {
        codeFirst.buildTableInfo(entityType)
    }

    private val tableName: String
        get() = tableOverride ?: utils.quoteSqlName(table.dbName)

    private fun nextParamName(prefix: String = "s"): String {
        return "@_${prefix}${paramCounter++}"
    }

    // --- WHERE ---

    override fun where(predicate: (TableColumns<T>) -> SqlExpr): ISelect<T> {
        val tableColumns = TableColumns(entityType)
        val expr = predicate(tableColumns)
        // SqlExpr renders values inline via ValueExpr.toSql()
        val sql = expr.toSql(quote)
        whereParts.add("($sql)")
        return this
    }

    override fun where(sql: String, vararg params: Any?): ISelect<T> {
        var paramSql = sql
        params.forEach { value ->
            val paramName = nextParamName()
            paramSql = paramSql.replaceFirst("?", paramName)
            whereParams[paramName] = utils.formatParameter(value)
        }
        whereParts.add("($paramSql)")
        return this
    }

    override fun whereExpr(expr: SqlExpr): ISelect<T> {
        val sql = expr.toSql(quote)
        whereParts.add("($sql)")
        return this
    }

    // --- JOIN ---

    override fun <TJoin : Any> leftJoin(table: TableColumns<TJoin>, on: SqlExpr): ISelect<T> {
        val joinTable = utils.quoteSqlName(table.tableName)
        val onSql = on.toSql(quote)
        joins.add("LEFT JOIN $joinTable ON $onSql")
        return this
    }

    override fun <TJoin : Any> innerJoin(table: TableColumns<TJoin>, on: SqlExpr): ISelect<T> {
        val joinTable = utils.quoteSqlName(table.tableName)
        val onSql = on.toSql(quote)
        joins.add("INNER JOIN $joinTable ON $onSql")
        return this
    }

    override fun <TJoin : Any> rightJoin(table: TableColumns<TJoin>, on: SqlExpr): ISelect<T> {
        val joinTable = utils.quoteSqlName(table.tableName)
        val onSql = on.toSql(quote)
        joins.add("RIGHT JOIN $joinTable ON $onSql")
        return this
    }

    // --- ORDER BY ---

    override fun orderBy(vararg columns: Pair<ColumnRef<*>, SortDirection>): ISelect<T> {
        columns.forEach { (col, dir) ->
            orderByParts.add("${col.toSql(quote)} ${dir.name}")
        }
        return this
    }

    override fun orderBy(column: ColumnRef<*>, direction: SortDirection): ISelect<T> {
        orderByParts.add("${column.toSql(quote)} ${direction.name}")
        return this
    }

    // --- GROUP BY / HAVING ---

    override fun groupBy(vararg columns: ColumnRef<*>): ISelect<T> {
        columns.forEach { col ->
            groupByParts.add(col.toSql(quote))
        }
        return this
    }

    override fun having(expr: SqlExpr): ISelect<T> {
        havingPart = expr.toSql(quote)
        return this
    }

    // --- Modifiers ---

    override fun distinct(): ISelect<T> {
        isDistinct = true
        return this
    }

    override fun skip(count: Int): ISelect<T> {
        skipCount = count
        return this
    }

    override fun take(count: Int): ISelect<T> {
        takeCount = count
        return this
    }

    override fun page(pageNumber: Int, pageSize: Int): ISelect<T> {
        skipCount = (pageNumber - 1) * pageSize
        takeCount = pageSize
        return this
    }

    override fun asTable(tableName: String): ISelect<T> {
        tableOverride = tableName
        return this
    }

    // --- Navigation loading ---

    override fun <TNav : Any> includeMany(
        relationProperty: String,
        select: ISelect<TNav>?
    ): ISelect<T> {
        @Suppress("UNCHECKED_CAST")
        includeManys.add(IncludeManySpec(relationProperty, select) as IncludeManySpec<*>)
        return this
    }

    // --- UNION ALL ---

    override fun unionAll(vararg queries: ISelect<T>): ISelect<T> {
        for (query in queries) {
            val sql = query.toSql()
            // Extract params from the union query if it's a SqliteSelect
            if (query is SqliteSelect) {
                unionParams.putAll(query.getAllParams())
            }
            unionParts.add(sql)
        }
        return this
    }

    // --- Aggregate functions ---

    override fun avg(column: ColumnRef<*>): Double {
        val colSql = column.toSql(quote)
        val sql = "SELECT AVG($colSql) FROM $tableName${buildFromTail()}"
        val result = ado.executeScalar(sql, getAllParams())
        return (result as? Number)?.toDouble() ?: 0.0
    }

    @Suppress("UNCHECKED_CAST")
    override fun <TMember : Comparable<TMember>> max(column: ColumnRef<TMember>): TMember? {
        val colSql = column.toSql(quote)
        val sql = "SELECT MAX($colSql) FROM $tableName${buildFromTail()}"
        val result = ado.executeScalar(sql, getAllParams())
        return result as? TMember
    }

    @Suppress("UNCHECKED_CAST")
    override fun <TMember : Comparable<TMember>> min(column: ColumnRef<TMember>): TMember? {
        val colSql = column.toSql(quote)
        val sql = "SELECT MIN($colSql) FROM $tableName${buildFromTail()}"
        val result = ado.executeScalar(sql, getAllParams())
        return result as? TMember
    }

    @Suppress("UNCHECKED_CAST")
    override fun <TMember : Number> sum(column: ColumnRef<TMember>): TMember? {
        val colSql = column.toSql(quote)
        val sql = "SELECT SUM($colSql) FROM $tableName${buildFromTail()}"
        val result = ado.executeScalar(sql, getAllParams())
        return result as? TMember
    }

    override fun count(predicate: SqlExpr): Long {
        val exprSql = predicate.toSql(quote)
        val sql = "SELECT SUM(CASE WHEN $exprSql THEN 1 ELSE 0 END) FROM $tableName${buildFromTail()}"
        val result = ado.executeScalar(sql, getAllParams())
        return (result as? Number)?.toLong() ?: 0L
    }

    // --- Subquery / custom SELECT fields ---

    override fun selectColumns(vararg columns: String): ISelect<T> {
        selectColumnsOverride = columns.joinToString(", ")
        return this
    }

    override fun fromQuery(subQuery: ISelect<T>): ISelect<T> {
        fromQuerySql = "(${subQuery.toSql()})"
        return this
    }

    override fun insertInto(targetTable: String): Int {
        val sql = buildSql()
        val insertSql = "INSERT INTO ${utils.quoteSqlName(targetTable)} $sql"
        return ado.executeNonQuery(insertSql, getAllParams())
    }

    // --- Execution ---

    override fun toList(): List<T> {
        val sql = buildSql()
        val allParams = getAllParams()
        ado.aop?.curdBefore?.invoke(CurdBeforeEventArgs(sql, allParams))
        val results = mutableListOf<T>()
        ado.executeReader(sql, allParams) { rs ->
            while (rs.next()) {
                results.add(mapRow(rs))
            }
        }
        if (includeManys.isNotEmpty()) {
            loadChildren(results)
        }
        ado.aop?.curdAfter?.invoke(CurdAfterEventArgs(sql, results.size.toLong()))
        return results
    }

    override fun first(): T? {
        takeCount = 1
        val sql = buildSql()
        var result: T? = null
        ado.executeReader(sql, getAllParams()) { rs ->
            if (rs.next()) {
                result = mapRow(rs)
            }
        }
        return result
    }

    override fun firstOrThrow(): T {
        return first() ?: throw NoSuchElementException("No row found")
    }

    override fun count(): Long {
        val sql = buildCountSql()
        val result = ado.executeScalar(sql, getAllParams())
        return (result as? Number)?.toLong() ?: 0L
    }

    override fun any(): Boolean {
        return count() > 0
    }

    override fun <TColumn> toList(column: ColumnRef<TColumn>): List<TColumn> {
        val originalOverride = selectColumnsOverride
        selectColumnsOverride = column.toSql(quote)
        val sql = buildSql()
        selectColumnsOverride = originalOverride

        val results = mutableListOf<TColumn>()
        ado.executeReader(sql, getAllParams()) { rs ->
            while (rs.next()) {
                @Suppress("UNCHECKED_CAST")
                results.add(rs.getObject(1) as TColumn)
            }
        }
        return results
    }

    override fun <K, V> toDictionary(
        keySelector: (T) -> K,
        valueSelector: (T) -> V
    ): Map<K, V> {
        return toList().associateBy(keySelector, valueSelector)
    }

    override fun toSql(): String = buildSql()

    override fun toSubQuery(): String {
        return "(${buildSql()})"
    }

    // --- Internal SQL building ---

    private fun buildSql(): String {
        val sb = StringBuilder()

        // SELECT
        sb.append("SELECT ")
        if (isDistinct) sb.append("DISTINCT ")

        // Columns
        if (selectColumnsOverride != null) {
            sb.append(selectColumnsOverride)
        } else {
            val columns = table.columns.filter { !it.isIgnore }
            if (columns.isEmpty()) {
                sb.append("*")
            } else {
                sb.append(columns.joinToString(", ") { utils.quoteSqlName(it.dbName) })
            }
        }

        // FROM
        val fromSource = fromQuerySql ?: tableName
        sb.append(" FROM $fromSource")

        // JOINs
        if (joins.isNotEmpty()) {
            sb.append(" ")
            sb.append(joins.joinToString(" "))
        }

        // WHERE
        appendWhereClause(sb)

        // GROUP BY
        if (groupByParts.isNotEmpty()) {
            sb.append(" GROUP BY ")
            sb.append(groupByParts.joinToString(", "))
        }

        // HAVING
        if (havingPart != null) {
            sb.append(" HAVING $havingPart")
        }

        // ORDER BY
        if (orderByParts.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(orderByParts.joinToString(", "))
        }

        // LIMIT/OFFSET (SQLite syntax)
        if (takeCount > 0) {
            sb.append(" LIMIT $takeCount")
            if (skipCount > 0) {
                sb.append(" OFFSET $skipCount")
            }
        }

        // UNION ALL
        if (unionParts.isNotEmpty()) {
            unionParts.forEach { unionSql ->
                sb.append(" UNION ALL $unionSql")
            }
        }

        return sb.toString()
    }

    private fun buildCountSql(): String {
        // If there are GROUP BY, wrap as subquery; otherwise just SELECT COUNT(*) FROM table
        if (groupByParts.isEmpty() && !isDistinct) {
            val sb = StringBuilder()
            val fromSource = fromQuerySql ?: tableName
            sb.append("SELECT COUNT(*) FROM $fromSource")

            if (joins.isNotEmpty()) {
                sb.append(" ")
                sb.append(joins.joinToString(" "))
            }

            appendWhereClause(sb)

            return sb.toString()
        }

        val sb = StringBuilder()
        sb.append("SELECT 1")
        val fromSource2 = fromQuerySql ?: tableName
        sb.append(" FROM $fromSource2")

        if (joins.isNotEmpty()) {
            sb.append(" ")
            sb.append(joins.joinToString(" "))
        }

        appendWhereClause(sb)

        if (groupByParts.isNotEmpty()) {
            sb.append(" GROUP BY ")
            sb.append(groupByParts.joinToString(", "))
        }

        return "SELECT COUNT(*) FROM (${sb})"
    }

    /** Build the FROM tail (JOINs + WHERE + GROUP BY + HAVING) for aggregate queries. */
    private fun buildFromTail(): String {
        val sb = StringBuilder()
        if (joins.isNotEmpty()) {
            sb.append(" ")
            sb.append(joins.joinToString(" "))
        }
        appendWhereClause(sb)
        if (groupByParts.isNotEmpty()) {
            sb.append(" GROUP BY ")
            sb.append(groupByParts.joinToString(", "))
        }
        if (havingPart != null) {
            sb.append(" HAVING $havingPart")
        }
        return sb.toString()
    }

    private fun appendWhereClause(sb: StringBuilder) {
        val allParts = mutableListOf<String>()

        // Global filters first
        globalFilter?.getFilters(entityType)?.forEach { filter ->
            allParts.add(filter.where("a"))
        }

        allParts.addAll(whereParts)

        if (allParts.isNotEmpty()) {
            sb.append(" WHERE ")
            sb.append(allParts.joinToString(" AND "))
        }
    }

    internal fun getAllParams(): Map<String, Any?> {
        val allParams = mutableMapOf<String, Any?>()
        allParams.putAll(whereParams)
        allParams.putAll(havingParams)
        allParams.putAll(unionParams)

        // Global filter params
        globalFilter?.getFilters(entityType)?.forEach { filter ->
            allParams.putAll(filter.parameters())
        }

        return allParams
    }

    // --- Row mapping ---

    private fun mapRow(rs: ResultSet): T {
        val columns = table.columns.filter { !it.isIgnore }
        val constructor = entityType.primaryConstructor
            ?: entityType.constructors.firstOrNull()
            ?: throw IllegalStateException("No constructor found for ${entityType.simpleName}")

        val args = constructor.parameters.map { param ->
            val col = columns.find { it.csName == param.name }
            if (col != null) {
                val colIndex = columns.indexOf(col) + 1
                utils.readValue(rs, colIndex, col.csType)
            } else {
                null
            }
        }

        return constructor.call(*args.toTypedArray())
    }

    // --- Navigation loading ---

    private fun loadChildren(parentEntities: List<T>) {
        if (parentEntities.isEmpty()) return

        for (spec in includeManys) {
            @Suppress("UNCHECKED_CAST")
            loadChildrenForProperty(parentEntities, spec as IncludeManySpec<Any>)
        }
    }

    private fun <TNav : Any> loadChildrenForProperty(
        parentEntities: List<T>,
        spec: IncludeManySpec<TNav>
    ) {
        // Find the navigation relationship
        val ref = table.refs.find { ref ->
            spec.relationProperty.let { prop ->
                entityType.memberProperties.any { it.name == prop }
            }
        } ?: return

        // Get FK values from parent entities
        val fkCol = ref.columns.firstOrNull() ?: return
        val fkValues = parentEntities.mapNotNull { entity ->
            val prop = entityType.memberProperties.find { it.name == fkCol.csName }
            prop?.call(entity)
        }.distinct()

        if (fkValues.isEmpty()) return

        // Query child entities
        val refTable = codeFirst.buildTableInfo(ref.refEntityType)
        val refPkCol = ref.refColumns.firstOrNull() ?: return

        val sql = buildString {
            append("SELECT * FROM ${utils.quoteSqlName(refTable.dbName)}")
            append(" WHERE ${utils.quoteSqlName(refPkCol.dbName)}")
            append(" IN (${fkValues.joinToString(", ") { utils.formatSqlValue(it) }})")
        }

        val childEntities = ado.executeArray(sql, emptyMap()) { rs ->
            mapRowToType(rs, ref.refEntityType, refTable)
        }

        // Assign children to parents via reflection
        for (entity in parentEntities) {
            val parentFkProp = entityType.memberProperties.find { it.name == fkCol.csName } ?: continue
            val parentFkValue = parentFkProp.call(entity)

            val matchingChildren = childEntities.filter { child ->
                val childPkProp = ref.refEntityType.memberProperties.find { it.name == refPkCol.csName }
                childPkProp?.call(child) == parentFkValue
            }

            // Try to set the collection on the parent entity
            try {
                val prop = entityType.memberProperties.find { it.name == spec.relationProperty }
                if (prop is KMutableProperty1<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    (prop as KMutableProperty1<T, Any?>).set(entity, matchingChildren)
                }
            } catch (_: Exception) {
                // Property might not be settable
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R : Any> mapRowToType(rs: ResultSet, type: KClass<R>, tableInfo: TableInfo): R {
        val columns = tableInfo.columns.filter { !it.isIgnore }
        val constructor = type.primaryConstructor
            ?: type.constructors.firstOrNull()
            ?: throw IllegalStateException("No constructor found for ${type.simpleName}")

        val args = constructor.parameters.map { param ->
            val col = columns.find { it.csName == param.name }
            if (col != null) {
                val colIndex = columns.indexOf(col) + 1
                utils.readValue(rs, colIndex, col.csType)
            } else {
                null
            }
        }

        return constructor.call(*args.toTypedArray())
    }

    // --- Helper class ---

    private class IncludeManySpec<TNav : Any>(
        val relationProperty: String,
        val select: ISelect<TNav>?
    )
}
