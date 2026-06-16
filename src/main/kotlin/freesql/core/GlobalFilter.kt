package freesql.core

import freesql.common.CommonUtils
import kotlin.reflect.KClass

/**
 * Global query filters applied to all SELECT/UPDATE/DELETE.
 * Port of FreeSql GlobalFilter.
 *
 * Supports:
 * - Named filters per entity type
 * - Parameter injection (filters can supply parameters alongside WHERE clauses)
 * - Table alias substitution in filter expressions
 */
class GlobalFilter {
    private val filters = mutableMapOf<String, FilterItem>()

    /**
     * Register a named filter for entity type [T].
     * The [where] lambda receives the table alias and returns a SQL WHERE fragment.
     * The [parameters] lambda returns a map of named parameters used by the WHERE fragment.
     */
    fun <T : Any> apply(
        name: String,
        clazz: KClass<T>,
        where: (tableAlias: String) -> String,
        parameters: () -> Map<String, Any?> = { emptyMap() }
    ): GlobalFilter {
        filters[name] = FilterItem(clazz, where, parameters)
        return this
    }

    /**
     * Register a named filter using SqlExpr for type-safe expressions.
     * The [where] lambda receives the table alias and returns a SqlExpr.
     * Parameters are extracted from the expression automatically.
     */
    fun <T : Any> applyExpr(
        name: String,
        clazz: KClass<T>,
        where: (tableAlias: String) -> SqlExpr
    ): GlobalFilter {
        filters[name] = FilterItem(
            clazz = clazz,
            where = { tableAlias -> where(tableAlias).toSql(null) },
            parameters = { emptyMap() }
        )
        return this
    }

    /**
     * Register a named filter with both a SqlExpr and parameters.
     */
    fun <T : Any> applyExprWithParams(
        name: String,
        clazz: KClass<T>,
        where: (tableAlias: String) -> SqlExpr,
        parameters: () -> Map<String, Any?> = { emptyMap() }
    ): GlobalFilter {
        filters[name] = FilterItem(
            clazz = clazz,
            where = { tableAlias -> where(tableAlias).toSql(null) },
            parameters = parameters
        )
        return this
    }

    /** Remove a named filter. */
    fun remove(name: String): GlobalFilter {
        filters.remove(name)
        return this
    }

    /** Get all filters applicable to entity type [clazz]. */
    fun getFilters(clazz: KClass<*>): List<FilterItem> {
        return filters.values.filter { it.clazz == clazz }
    }

    /** Check if any filters exist. */
    fun hasFilters(): Boolean = filters.isNotEmpty()

    /**
     * Get combined WHERE clauses and parameters for the given entity type.
     * This is a convenience method that returns both the SQL fragment and
     * all parameters needed by the filters.
     *
     * @param clazz The entity type to get filters for.
     * @param tableAlias The table alias to use in filter expressions.
     * @param utils Common utilities for formatting.
     * @return Pair of (combined WHERE clause, combined parameters).
     */
    fun getWhereClauses(
        clazz: KClass<*>,
        tableAlias: String,
        utils: CommonUtils
    ): Pair<String, Map<String, Any?>> {
        val entityFilters = getFilters(clazz)
        if (entityFilters.isEmpty()) return "" to emptyMap()

        val whereParts = mutableListOf<String>()
        val allParams = mutableMapOf<String, Any?>()

        entityFilters.forEach { filter ->
            val whereClause = filter.where(tableAlias)
            if (whereClause.isNotBlank()) {
                whereParts.add("($whereClause)")
            }
            val filterParams = filter.parameters()
            if (filterParams.isNotEmpty()) {
                allParams.putAll(filterParams)
            }
        }

        val combinedWhere = whereParts.joinToString(" AND ")
        return combinedWhere to allParams
    }

    /**
     * Apply all matching filters to a SQL WHERE clause.
     * Returns a modified SQL string with filter conditions injected.
     *
     * @param sql The original SQL statement.
     * @param clazz The entity type to apply filters for.
     * @param tableAlias The table alias used in the SQL (default "a").
     * @return The SQL with filter conditions added to WHERE clause.
     */
    fun applyToSql(
        sql: String,
        clazz: KClass<*>,
        tableAlias: String = "a"
    ): Pair<String, Map<String, Any?>> {
        val entityFilters = getFilters(clazz)
        if (entityFilters.isEmpty()) return sql to emptyMap()

        val filterWhereParts = mutableListOf<String>()
        val allParams = mutableMapOf<String, Any?>()

        entityFilters.forEach { filter ->
            val whereClause = filter.where(tableAlias)
            if (whereClause.isNotBlank()) {
                filterWhereParts.add("($whereClause)")
            }
            allParams.putAll(filter.parameters())
        }

        if (filterWhereParts.isEmpty()) return sql to allParams

        val filterSql = filterWhereParts.joinToString(" AND ")

        // Inject filter conditions into existing WHERE clause
        val upperSql = sql.uppercase()
        val whereIndex = upperSql.indexOf(" WHERE ")

        val modifiedSql = if (whereIndex >= 0) {
            // Existing WHERE clause — prepend filter conditions with AND
            val beforeWhere = sql.substring(0, whereIndex + 8) // " WHERE ".length
            val afterWhere = sql.substring(whereIndex + 8)
            "$beforeWhere$filterSql AND $afterWhere"
        } else {
            // No WHERE clause — need to insert one before ORDER BY, GROUP BY, LIMIT, etc.
            val insertPoints = listOf(" ORDER BY ", " GROUP BY ", " HAVING ", " LIMIT ")
            var insertAt = sql.length
            for (keyword in insertPoints) {
                val idx = sql.uppercase().indexOf(keyword)
                if (idx >= 0 && idx < insertAt) {
                    insertAt = idx
                }
            }
            if (insertAt < sql.length) {
                val before = sql.substring(0, insertAt)
                val after = sql.substring(insertAt)
                "$before WHERE $filterSql$after"
            } else {
                "$sql WHERE $filterSql"
            }
        }

        return modifiedSql to allParams
    }

    /** Get the count of registered filters. */
    fun size(): Int = filters.size

    /** Remove all filters. */
    fun clear(): GlobalFilter {
        filters.clear()
        return this
    }
}

/**
 * Represents a single global filter registration.
 */
class FilterItem(
    val clazz: KClass<*>,
    val where: (tableAlias: String) -> String,
    val parameters: () -> Map<String, Any?>
)
