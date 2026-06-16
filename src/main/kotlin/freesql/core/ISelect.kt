package freesql.core

/**
 * Fluent SELECT query builder with expression-based API.
 * Port of FreeSql ISelect<T1>.
 */
interface ISelect<T : Any> : ISqlQuery {

    // --- WHERE ---

    /** Add WHERE from a TableColumns lambda that returns a SqlExpr. */
    fun where(predicate: (TableColumns<T>) -> SqlExpr): ISelect<T>

    /** Add raw SQL WHERE condition with positional ? parameters. */
    fun where(sql: String, vararg params: Any?): ISelect<T>

    /** Add WHERE from an already-built SqlExpr. */
    fun whereExpr(expr: SqlExpr): ISelect<T>

    // --- JOIN ---

    /** LEFT JOIN with a table columns object and an ON expression. */
    fun <TJoin : Any> leftJoin(table: TableColumns<TJoin>, on: SqlExpr): ISelect<T>

    /** INNER JOIN with a table columns object and an ON expression. */
    fun <TJoin : Any> innerJoin(table: TableColumns<TJoin>, on: SqlExpr): ISelect<T>

    /** RIGHT JOIN with a table columns object and an ON expression. */
    fun <TJoin : Any> rightJoin(table: TableColumns<TJoin>, on: SqlExpr): ISelect<T>

    // --- ORDER BY ---

    /** ORDER BY multiple columns with explicit directions. */
    fun orderBy(vararg columns: Pair<ColumnRef<*>, SortDirection>): ISelect<T>

    /** ORDER BY a single column. */
    fun orderBy(column: ColumnRef<*>, direction: SortDirection = SortDirection.ASC): ISelect<T>

    // --- GROUP BY / HAVING ---

    /** GROUP BY columns. */
    fun groupBy(vararg columns: ColumnRef<*>): ISelect<T>

    /** HAVING condition (requires groupBy). */
    fun having(expr: SqlExpr): ISelect<T>

    // --- Modifiers ---

    /** SELECT DISTINCT. */
    fun distinct(): ISelect<T>

    /** Skip [count] rows (pagination offset). */
    fun skip(count: Int): ISelect<T>

    /** Take [count] rows (pagination limit). */
    fun take(count: Int): ISelect<T>

    /** Pagination: page [pageNumber] (1-based) with [pageSize] rows. */
    fun page(pageNumber: Int, pageSize: Int): ISelect<T>

    /** Sharding: query a specific table name override. */
    fun asTable(tableName: String): ISelect<T>

    // --- Navigation loading ---

    /** Include a navigation collection on the result entities. */
    fun <TNav : Any> includeMany(
        relationProperty: String,
        select: ISelect<TNav>? = null
    ): ISelect<T>

    // --- UNION ---

    /** UNION ALL with one or more queries of the same type. */
    fun unionAll(vararg queries: ISelect<T>): ISelect<T> =
        throw UnsupportedOperationException("unionAll not implemented")

    // --- Aggregate functions (return scalars) ---

    /** Execute and return AVG(column) as Double. */
    fun avg(column: ColumnRef<*>): Double =
        throw UnsupportedOperationException("avg not implemented")

    /** Execute and return MAX(column). */
    fun <TMember : Comparable<TMember>> max(column: ColumnRef<TMember>): TMember? =
        throw UnsupportedOperationException("max not implemented")

    /** Execute and return MIN(column). */
    fun <TMember : Comparable<TMember>> min(column: ColumnRef<TMember>): TMember? =
        throw UnsupportedOperationException("min not implemented")

    /** Execute and return SUM(column) as the given type. */
    fun <TMember : Number> sum(column: ColumnRef<TMember>): TMember? =
        throw UnsupportedOperationException("sum not implemented")

    /** Execute COUNT with a CASE WHEN predicate: SUM(CASE WHEN expr THEN 1 ELSE 0 END). */
    fun count(predicate: SqlExpr): Long =
        throw UnsupportedOperationException("count(predicate) not implemented")

    // --- Subquery / custom SELECT fields ---

    /** Override the SELECT columns with custom SQL expressions (e.g., subqueries). */
    fun selectColumns(vararg columns: String): ISelect<T> =
        throw UnsupportedOperationException("selectColumns not implemented")

    /** Use a subquery as the FROM source. Replaces the table in FROM clause. */
    fun fromQuery(subQuery: ISelect<T>): ISelect<T> =
        throw UnsupportedOperationException("fromQuery not implemented")

    // --- Chunked reading ---

    /** Read results in chunks of [size], calling [callback] for each chunk. */
    fun toChunk(size: Int, callback: (List<T>) -> Unit) {
        var offset = 0
        while (true) {
            val chunk = this.skip(offset).take(size).toList()
            if (chunk.isEmpty()) break
            callback(chunk)
            if (chunk.size < size) break
            offset += size
        }
    }

    // --- InsertInto ---

    /** INSERT INTO [targetTable] SELECT ... FROM this query. Returns affected rows. */
    fun insertInto(targetTable: String): Int =
        throw UnsupportedOperationException("insertInto not implemented")

    // --- Pessimistic locking (documented, not supported in SQLite) ---
    // Note: SQLite does not support FOR UPDATE / FOR SHARE / FOR NO WAIT.
    // Use BEGIN IMMEDIATE or EXCLUSIVE transactions for write contention handling.

    // --- Execution ---

    /** Execute and return all matching rows. */
    fun toList(): List<T>

    /** Execute and return the first row or null. */
    fun first(): T?

    /** Execute and return the first row (throws if empty). */
    fun firstOrThrow(): T

    /** Execute and return the count of matching rows. */
    fun count(): Long

    /** Execute and check if any rows match. */
    fun any(): Boolean

    /** Execute and return one column's values as a list. */
    fun <TColumn> toList(column: ColumnRef<TColumn>): List<TColumn>

    /** Execute and return results as a Map. */
    fun <K, V> toDictionary(keySelector: (T) -> K, valueSelector: (T) -> V): Map<K, V>

    /** Get the generated SQL without executing. */
    override fun toSql(): String

    /** Returns the SQL for use in subqueries. */
    fun toSubQuery(): String
}

/**
 * Shared SQL query interface for types that produce SQL strings.
 */
interface ISqlQuery {
    fun toSql(): String
}
