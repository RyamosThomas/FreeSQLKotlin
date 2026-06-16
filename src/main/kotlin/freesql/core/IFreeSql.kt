package freesql.core

import freesql.common.CommonUtils
import freesql.model.TableInfo
import kotlin.reflect.KClass

/**
 * Top-level FreeSql ORM entry point.
 * Port of FreeSql IFreeSql.
 */
interface IFreeSql {

    /** Low-level ADO access for raw SQL. */
    val ado: IAdo

    /** AOP interceptor hooks. */
    val aop: IAop

    /** Schema migration manager. */
    val codeFirst: ICodeFirst

    /** Reverse-engineer existing database. */
    val dbFirst: IDbFirst

    /** Global query filters (e.g. tenant, soft-delete). */
    val globalFilter: GlobalFilter

    /** Start a fluent SELECT query for entity type [T]. */
    fun <T : Any> select(clazz: KClass<T>): ISelect<T>

    /** Start a fluent INSERT for entity type [T]. */
    fun <T : Any> insert(clazz: KClass<T>): IInsert<T>

    /** Start a fluent UPDATE for entity type [T]. */
    fun <T : Any> update(clazz: KClass<T>): IUpdate<T>

    /** Start a fluent DELETE for entity type [T]. */
    fun <T : Any> delete(clazz: KClass<T>): IDelete<T>

    /** Start a fluent INSERT OR REPLACE for entity type [T]. */
    fun <T : Any> insertOrUpdate(clazz: KClass<T>): IInsertOrUpdate<T>

    /** Execute [action] inside a transaction. */
    fun transaction(action: () -> Unit)

    /** Execute raw SQL that returns affected rows. */
    fun executeNonQuery(sql: String, vararg params: Any?): Int
}

// Inline reified extensions for cleaner API
inline fun <reified T : Any> IFreeSql.select(): ISelect<T> = select(T::class)
inline fun <reified T : Any> IFreeSql.insert(): IInsert<T> = insert(T::class)
inline fun <reified T : Any> IFreeSql.update(): IUpdate<T> = update(T::class)
inline fun <reified T : Any> IFreeSql.delete(): IDelete<T> = delete(T::class)
inline fun <reified T : Any> IFreeSql.insertOrUpdate(): IInsertOrUpdate<T> = insertOrUpdate(T::class)
