package freesql.core

/**
 * Fluent UPDATE builder with expression-based API.
 * Port of FreeSql IUpdate<T1>.
 */
interface IUpdate<T : Any> : ISqlQuery {

    /** Set the source entity (provides WHERE by PK + column values). */
    fun setSource(source: T): IUpdate<T>

    /** Set the source entities (batch update by PK). */
    fun setSource(sources: Collection<T>): IUpdate<T>

    /** Set a specific column to a value using a ColumnRef. */
    fun <TMember> set(column: ColumnRef<TMember>, value: TMember): IUpdate<T>

    /** Conditional set: only set column if condition is true. */
    fun <TMember> setIf(condition: Boolean, column: ColumnRef<TMember>, value: TMember): IUpdate<T> =
        if (condition) set(column, value) else this

    /** Set a column using raw SQL expression. */
    fun setRaw(column: String, sql: String, vararg params: Any?): IUpdate<T>

    /** Set multiple columns from a DTO (anonymous object or data class). */
    fun setDto(dto: Any): IUpdate<T>

    // --- WHERE ---

    /** Add WHERE from a TableColumns lambda that returns a SqlExpr. */
    fun where(predicate: (TableColumns<T>) -> SqlExpr): IUpdate<T>

    /** Add raw SQL WHERE condition with positional ? parameters. */
    fun where(sql: String, vararg params: Any?): IUpdate<T>

    /** Add WHERE from an already-built SqlExpr. */
    fun whereExpr(expr: SqlExpr): IUpdate<T>

    /** Add WHERE IN on a dynamic list of PK values or entities (extract PKs). */
    fun whereDynamic(ids: Collection<Any>): IUpdate<T> =
        throw UnsupportedOperationException("whereDynamic not implemented")

    // --- Column filtering ---

    /** Only update specific columns. */
    fun updateColumns(vararg columns: ColumnRef<*>): IUpdate<T>

    /** Exclude specific columns from update. */
    fun ignoreColumns(vararg columns: ColumnRef<*>): IUpdate<T>

    // --- Modifiers ---

    /** Sharding: target a specific table name. */
    fun asTable(tableName: String): IUpdate<T>

    // --- Split execution ---

    /** Execute split into batches (200 rows / 999 params) and return total affected. */
    fun splitExecuteAffrows(): Int =
        throw UnsupportedOperationException("splitExecuteAffrows not implemented")

    /** Execute and return affected rows. */
    fun executeAffrows(): Int

    /** Get the generated SQL without executing. */
    override fun toSql(): String
}
