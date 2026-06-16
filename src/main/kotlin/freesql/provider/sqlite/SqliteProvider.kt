package freesql.provider.sqlite

import freesql.core.*
import freesql.provider.sqlite.curd.*
import kotlin.reflect.KClass

/**
 * SQLite provider — main entry point.
 * Creates and wires all SQLite-specific components including
 * connection pooling and global filter integration.
 * Port of FreeSql SqliteProvider<TMark>.
 */
class SqliteProvider(
    private val connectionString: String
) : IFreeSql {

    // Core components
    val utils = SqliteUtils()
    val expression = SqliteExpression(utils)
    val pool = SqliteConnectionPool(connectionString)
    override val ado: SqliteAdo = SqliteAdo(connectionString, utils, pool)
    override val aop: IAop = SimpleAop().also { ado.aop = it }
    override val codeFirst: SqliteCodeFirst = SqliteCodeFirst(ado, utils)
    override val dbFirst: SqliteDbFirst = SqliteDbFirst(ado, utils)
    override val globalFilter: GlobalFilter = GlobalFilter()

    // CRUD providers
    override fun <T : Any> select(clazz: KClass<T>): ISelect<T> {
        autoSyncStructure(clazz)
        return SqliteSelect(clazz, ado, utils, expression, codeFirst, globalFilter)
    }

    override fun <T : Any> insert(clazz: KClass<T>): IInsert<T> {
        autoSyncStructure(clazz)
        return SqliteInsert(clazz, ado, utils, codeFirst)
    }

    override fun <T : Any> update(clazz: KClass<T>): IUpdate<T> {
        autoSyncStructure(clazz)
        return SqliteUpdate(clazz, ado, utils, codeFirst).withGlobalFilter(globalFilter)
    }

    override fun <T : Any> delete(clazz: KClass<T>): IDelete<T> {
        autoSyncStructure(clazz)
        return SqliteDelete(clazz, ado, utils, codeFirst).withGlobalFilter(globalFilter)
    }

    override fun <T : Any> insertOrUpdate(clazz: KClass<T>): IInsertOrUpdate<T> {
        autoSyncStructure(clazz)
        return SqliteInsertOrUpdate(clazz, ado, utils, codeFirst)
    }

    override fun transaction(action: () -> Unit) {
        ado.transaction(action)
    }

    override fun executeNonQuery(sql: String, vararg params: Any?): Int {
        return ado.executeNonQuery(sql)
    }

    /**
     * Close the connection pool and release all resources.
     * Should be called when the provider is no longer needed.
     */
    fun close() {
        pool.close()
    }

    /**
     * Auto-sync entity structure on first access if enabled.
     */
    private fun autoSyncStructure(clazz: KClass<*>) {
        if (codeFirst.isAutoSyncStructure) {
            codeFirst.syncStructure(clazz)
        }
    }
}

/**
 * Simple AOP implementation.
 */
private class SimpleAop : IAop {
    override var executingSql: ((ExecutingSqlInfo) -> Unit)? = null
    override var dbException: ((Exception) -> Unit)? = null
    override var curdBefore: ((CurdBeforeEventArgs) -> Unit)? = null
    override var curdAfter: ((CurdAfterEventArgs) -> Unit)? = null
    override var syncStructureBefore: ((SyncStructureEventArgs) -> Unit)? = null
    override var syncStructureAfter: ((SyncStructureEventArgs) -> Unit)? = null
}
