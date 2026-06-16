package freesql.core

/**
 * Fluent DELETE builder with expression-based API.
 * Port of FreeSql IDelete<T1>.
 */
interface IDelete<T : Any> : ISqlQuery {

    /** Set the source entity (provides WHERE by PK). */
    fun setSource(source: T): IDelete<T>

    /** Set the source entities (batch delete by PK). */
    fun setSource(sources: Collection<T>): IDelete<T>

    // --- WHERE ---

    /** Add WHERE from a TableColumns lambda that returns a SqlExpr. */
    fun where(predicate: (TableColumns<T>) -> SqlExpr): IDelete<T>

    /** Add raw SQL WHERE condition with positional ? parameters. */
    fun where(sql: String, vararg params: Any?): IDelete<T>

    /** Add WHERE from an already-built SqlExpr. */
    fun whereExpr(expr: SqlExpr): IDelete<T>

    /** Add WHERE IN on a dynamic list of PK values. */
    fun whereDynamic(ids: Collection<Any>): IDelete<T>

    // --- Modifiers ---

    /** Sharding: target a specific table name. */
    fun asTable(tableName: String): IDelete<T>

    /** Execute and return affected rows. */
    fun executeAffrows(): Int

    /** Get the generated SQL without executing. */
    override fun toSql(): String
}
