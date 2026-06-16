package freesql.provider.sqlite

import freesql.core.*

/**
 * Async extension functions for FreeSql interfaces.
 *
 * These are synchronous stubs that provide API parity with the C# FreeSql async methods.
 * For true async execution, Android/JVM users should wrap these calls in their own
 * coroutine scope with withContext(Dispatchers.IO) or equivalent.
 *
 * Example usage with coroutines:
 * ```
 * // Using kotlinx.coroutines:
 * suspend fun <T : Any> ISelect<T>.toListAsync(): List<T> =
 *     withContext(Dispatchers.IO) { toList() }
 * ```
 *
 * These stubs exist for API compatibility so that C# FreeSql code can be ported 1:1
 * without changing call sites.
 */
object SqliteAsync {

    // --- ISelect async stubs ---

    /** Async stub for ISelect.toList(). Returns synchronously. */
    fun <T : Any> toListAsync(select: ISelect<T>): List<T> = select.toList()

    /** Async stub for ISelect.first(). Returns synchronously. */
    fun <T : Any> firstAsync(select: ISelect<T>): T? = select.first()

    /** Async stub for ISelect.count(). Returns synchronously. */
    fun <T : Any> countAsync(select: ISelect<T>): Long = select.count()

    /** Async stub for ISelect.any(). Returns synchronously. */
    fun <T : Any> anyAsync(select: ISelect<T>): Boolean = select.any()

    // --- IInsert async stubs ---

    /** Async stub for IInsert.executeAffrows(). Returns synchronously. */
    fun <T : Any> executeAffrowsAsync(insert: IInsert<T>): Int = insert.executeAffrows()

    /** Async stub for IInsert.executeIdentity(). Returns synchronously. */
    fun <T : Any> executeIdentityAsync(insert: IInsert<T>): Long = insert.executeIdentity()

    /** Async stub for IInsert.executeInserted(). Returns synchronously. */
    fun <T : Any> executeInsertedAsync(insert: IInsert<T>): List<T> = insert.executeInserted()

    // --- IUpdate async stubs ---

    /** Async stub for IUpdate.executeAffrows(). Returns synchronously. */
    fun <T : Any> executeAffrowsAsync(update: IUpdate<T>): Int = update.executeAffrows()

    // --- IDelete async stubs ---

    /** Async stub for IDelete.executeAffrows(). Returns synchronously. */
    fun <T : Any> executeAffrowsAsync(delete: IDelete<T>): Int = delete.executeAffrows()

    // --- IInsertOrUpdate async stubs ---

    /** Async stub for IInsertOrUpdate.executeAffrows(). Returns synchronously. */
    fun <T : Any> executeAffrowsAsync(insertOrUpdate: IInsertOrUpdate<T>): Int = insertOrUpdate.executeAffrows()
}

/**
 * Extension function variants for a more fluent async-style API.
 * These run synchronously — wrap in your own coroutine scope for true async behavior.
 */
object SqliteAsyncExtensions {

    /** Async stub. */
    fun <T : Any> ISelect<T>.toListAsync(): List<T> = toList()

    /** Async stub. */
    fun <T : Any> ISelect<T>.firstAsync(): T? = first()

    /** Async stub. */
    fun <T : Any> ISelect<T>.countAsync(): Long = count()

    /** Async stub. */
    fun <T : Any> ISelect<T>.anyAsync(): Boolean = any()

    /** Async stub. */
    fun <T : Any> IInsert<T>.executeAffrowsAsync(): Int = executeAffrows()

    /** Async stub. */
    fun <T : Any> IInsert<T>.executeIdentityAsync(): Long = executeIdentity()

    /** Async stub. */
    fun <T : Any> IInsert<T>.executeInsertedAsync(): List<T> = executeInserted()

    /** Async stub. */
    fun <T : Any> IUpdate<T>.executeAffrowsAsync(): Int = executeAffrows()

    /** Async stub. */
    fun <T : Any> IDelete<T>.executeAffrowsAsync(): Int = executeAffrows()

    /** Async stub. */
    fun <T : Any> IInsertOrUpdate<T>.executeAffrowsAsync(): Int = executeAffrows()
}
