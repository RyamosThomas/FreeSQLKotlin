package freesql.core

/**
 * JSON-serializable filter model for dynamic query building.
 * Port of FreeSql DynamicFilterInfo.
 *
 * Usage:
 *   val filter = DynamicFilterInfo(
 *       logic = "and",
 *       filters = listOf(
 *           DynamicFilterInfo(field = "name", operator = "contains", value = "John"),
 *           DynamicFilterInfo(field = "age", operator = "gte", value = 18)
 *       )
 *   )
 *   val expr = filter.toSqlExpr(columnsMap)
 */
data class DynamicFilterInfo(
    /** Logical operator: "and" or "or". */
    val logic: String = "and",
    /** Property/field name. */
    val field: String = "",
    /** Comparison operator. */
    val operator: DynamicFilterOperator = DynamicFilterOperator.Contains,
    /** Filter value. */
    val value: Any? = null,
    /** Nested sub-filters. */
    val filters: List<DynamicFilterInfo>? = null
) {
    /**
     * Convert this filter tree to a SqlExpr using column references from the given table.
     * The columns map should map field names to ColumnRef instances.
     */
    fun toSqlExpr(columns: Map<String, ColumnRef<*>>): SqlExpr? {
        // If this has sub-filters, combine them with the logic operator
        if (!filters.isNullOrEmpty()) {
            val subExprs = filters.mapNotNull { it.toSqlExpr(columns) }
            if (subExprs.isEmpty()) return null
            return if (logic.equals("or", ignoreCase = true)) {
                subExprs.reduce { acc, expr -> OrExpr(acc, expr) }
            } else {
                subExprs.reduce { acc, expr -> AndExpr(acc, expr) }
            }
        }

        // Single field filter
        if (field.isEmpty()) return null
        val col = columns[field] ?: return null

        return buildExpr(col, operator, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildExpr(col: ColumnRef<*>, op: DynamicFilterOperator, value: Any?): SqlExpr? {
        return when (op) {
            DynamicFilterOperator.Equals -> (col as ColumnRef<Any>).eq(value)
            DynamicFilterOperator.NotEquals -> (col as ColumnRef<Any>).ne(value)
            DynamicFilterOperator.GreaterThan -> (col as ColumnRef<Comparable<Any>>).gt(value as Comparable<Any>)
            DynamicFilterOperator.GreaterThanOrEqual -> (col as ColumnRef<Comparable<Any>>).ge(value as Comparable<Any>)
            DynamicFilterOperator.LessThan -> (col as ColumnRef<Comparable<Any>>).lt(value as Comparable<Any>)
            DynamicFilterOperator.LessThanOrEqual -> (col as ColumnRef<Comparable<Any>>).le(value as Comparable<Any>)
            DynamicFilterOperator.Contains -> col.containsExpr(value?.toString() ?: "")
            DynamicFilterOperator.StartsWith -> col.startsWith(value?.toString() ?: "")
            DynamicFilterOperator.EndsWith -> col.endsWith(value?.toString() ?: "")
            DynamicFilterOperator.NotContains -> NotExpr(col.containsExpr(value?.toString() ?: ""))
            DynamicFilterOperator.IsNull -> col.isNull
            DynamicFilterOperator.IsNotNull -> col.isNotNull
            DynamicFilterOperator.Between -> {
                val list = value as? List<*> ?: return null
                if (list.size >= 2) {
                    (col as ColumnRef<Comparable<Any>>).between(list[0] as Comparable<Any>, list[1] as Comparable<Any>)
                } else null
            }
            DynamicFilterOperator.NotBetween -> {
                val list = value as? List<*> ?: return null
                if (list.size >= 2) {
                    NotExpr((col as ColumnRef<Comparable<Any>>).between(list[0] as Comparable<Any>, list[1] as Comparable<Any>))
                } else null
            }
        }
    }
}

/**
 * Dynamic filter operators.
 */
enum class DynamicFilterOperator {
    Equals,
    NotEquals,
    GreaterThan,
    GreaterThanOrEqual,
    LessThan,
    LessThanOrEqual,
    Contains,
    NotContains,
    StartsWith,
    EndsWith,
    IsNull,
    IsNotNull,
    Between,
    NotBetween
}
