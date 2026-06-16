package freesql.core

/**
 * Fluent INSERT builder.
 * Port of FreeSql IInsert<T1>.
 */
interface IInsert<T : Any> : ISqlQuery {

    /** Set the source entity to insert. */
    fun setSource(source: T): IInsert<T>

    /** Set multiple source entities to insert (batch). */
    fun setSource(sources: Collection<T>): IInsert<T>

    /** Only insert specific columns (by name). */
    fun insertColumns(vararg columns: String): IInsert<T>

    /** Only insert specific columns (by ColumnRef). */
    fun insertColumns(vararg columns: ColumnRef<*>): IInsert<T> =
        insertColumns(*columns.map { it.columnName }.toTypedArray())

    /** Exclude specific columns from insert (by name). */
    fun ignoreColumns(vararg columns: String): IInsert<T>

    /** Exclude specific columns from insert (by ColumnRef). */
    fun ignoreColumns(vararg columns: ColumnRef<*>): IInsert<T> =
        ignoreColumns(*columns.map { it.columnName }.toTypedArray())

    /** Include identity columns in the insert. */
    fun insertIdentity(): IInsert<T>

    /** Set batch size for multi-row inserts. */
    fun batchOptions(size: Int): IInsert<T>

    /** Sharding: target a specific table name. */
    fun asTable(tableName: String): IInsert<T>

    /** Enable NoneParameter mode (inline SQL values, no @params). */
    fun noneParameter(): IInsert<T> =
        throw UnsupportedOperationException("noneParameter not implemented")

    /** Set a progress callback for batch operations. Called after each batch is inserted. */
    fun onProgress(callback: (inserted: Int, total: Int) -> Unit): IInsert<T> =
        throw UnsupportedOperationException("onProgress not implemented")

    // --- BulkCopy (high-performance batch insert) ---

    /**
     * High-performance bulk insert. For SQLite, this generates large multi-value INSERT statements.
     * Returns total rows inserted.
     */
    fun bulkCopy(sources: Collection<T>): Int {
        var total = 0
        sources.forEach { entity ->
            total += setSource(entity).executeAffrows()
        }
        return total
    }

    // --- Split execution (auto-batching) ---

    /** Execute split into batches and return total affected rows. */
    fun splitExecuteAffrows(): Int =
        throw UnsupportedOperationException("splitExecuteAffrows not implemented")

    /** Execute split into batches and return identity of first insert. */
    fun splitExecuteIdentity(): Long =
        throw UnsupportedOperationException("splitExecuteIdentity not implemented")

    /** Execute split into batches and return all source entities. */
    fun splitExecuteInserted(): List<T> =
        throw UnsupportedOperationException("splitExecuteInserted not implemented")

    // --- Standard execution ---

    /** Execute and return affected rows. */
    fun executeAffrows(): Int

    /** Execute and return the identity value of the first inserted row. */
    fun executeIdentity(): Long

    /** Execute and return the inserted entities (with generated IDs). */
    fun executeInserted(): List<T>

    /** Get the generated SQL without executing. */
    override fun toSql(): String
}
