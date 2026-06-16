package freesql.extensions

import freesql.core.IFreeSql
import kotlin.reflect.KClass

/**
 * Active Record pattern base companion. Provides static CRUD methods.
 *
 * Usage:
 *   data class User(
 *       val id: Int = 0,
 *       val name: String = ""
 *   ) {
 *       companion object : ActiveCompanion<User, Int>(User::class)
 *   }
 *
 *   // Initialize once:
 *   User.init(orm)
 *
 *   // Then use:
 *   User.find(1)
 *   User.insert(User(name = "Alice"))
 *   User.selectAll().toList()
 *   User.count()
 */
abstract class ActiveCompanion<TEntity : Any, TKey : Any>(
    private val entityType: KClass<TEntity>
) {
    private var orm: IFreeSql? = null

    /** Initialize with the IFreeSql instance. Call once at startup. */
    fun init(freeSql: IFreeSql) {
        orm = freeSql
    }

    private fun getOrm(): IFreeSql {
        return orm ?: throw IllegalStateException("${entityType.simpleName} not initialized. Call init(orm) first.")
    }

    /** Select query builder. */
    fun selectAll() = getOrm().select(entityType)

    /** Find by primary key. */
    fun find(id: TKey): TEntity? {
        return getOrm().select(entityType).where("id = ?", id).first()
    }

    /** Find by primary key or throw. */
    fun findOrThrow(id: TKey): TEntity {
        return find(id) ?: throw NoSuchElementException("${entityType.simpleName} with id=$id not found")
    }

    /** Insert one entity. Returns affected rows. */
    fun insert(entity: TEntity): Int {
        return getOrm().insert(entityType).setSource(entity).executeAffrows()
    }

    /** Insert and return identity. */
    fun insertAndGetId(entity: TEntity): Long {
        return getOrm().insert(entityType).setSource(entity).executeIdentity()
    }

    /** Batch insert. Returns affected rows. */
    fun insertBatch(entities: Collection<TEntity>): Int {
        return getOrm().insert(entityType).setSource(entities).executeAffrows()
    }

    /** Update entity by PK. Returns affected rows. */
    fun update(entity: TEntity): Int {
        return getOrm().update(entityType).setSource(entity).executeAffrows()
    }

    /** Delete entity by PK. Returns affected rows. */
    fun delete(entity: TEntity): Int {
        return getOrm().delete(entityType).setSource(entity).executeAffrows()
    }

    /** Delete by primary key value. Returns affected rows. */
    fun deleteById(id: TKey): Int {
        return getOrm().delete(entityType).where("id = ?", id).executeAffrows()
    }

    /** Count all rows. */
    fun count(): Long = getOrm().select(entityType).count()

    /** Check if any rows exist. */
    fun any(): Boolean = getOrm().select(entityType).any()

    /** Get the underlying ORM instance. */
    fun getFreeSql(): IFreeSql = getOrm()
}
