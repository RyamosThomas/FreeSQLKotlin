package freesql.core

/**
 * Fluent INSERT OR REPLACE / INSERT OR IGNORE builder.
 * Port of FreeSql IInsertOrUpdate<T1>.
 */
interface IInsertOrUpdate<T : Any> : ISqlQuery {

    /** Set the source entity to insert or update. */
    fun setSource(source: T): IInsertOrUpdate<T>

    /** Set multiple source entities. */
    fun setSource(sources: Collection<T>): IInsertOrUpdate<T>

    /** Only update these columns on conflict. */
    fun updateColumns(vararg columns: String): IInsertOrUpdate<T>

    /** If the row already exists, do nothing (INSERT OR IGNORE). */
    fun ifExistsDoNothing(): IInsertOrUpdate<T>

    /** Sharding: target a specific table name. */
    fun asTable(tableName: String): IInsertOrUpdate<T>

    /** Enable NoneParameter mode (inline SQL values, no @params). */
    fun noneParameter(): IInsertOrUpdate<T> =
        throw UnsupportedOperationException("noneParameter not implemented")

    /** Execute split into batches and return total affected rows. */
    fun splitExecuteAffrows(): Int =
        throw UnsupportedOperationException("splitExecuteAffrows not implemented")

    /** Execute and return affected rows. */
    fun executeAffrows(): Int

    /** Get the generated SQL without executing. */
    override fun toSql(): String
}
