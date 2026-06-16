package freesql.extensions

import freesql.core.*
import kotlin.reflect.KClass

/**
 * Repository interface for CRUD operations on a specific entity type.
 * Port of FreeSql IBaseRepository<T>.
 */
interface IBaseRepository<T : Any> {
    /** The entity type this repository manages. */
    val entityType: KClass<T>

    /** Select query builder. */
    fun select(): ISelect<T>

    /** Insert one entity. Returns affected rows. */
    fun insert(entity: T): Int

    /** Insert and return identity. */
    fun insertAndGetId(entity: T): Long

    /** Insert multiple entities. Returns affected rows. */
    fun insertBatch(entities: Collection<T>): Int

    /** Update entity by PK. Returns affected rows. */
    fun update(entity: T): Int

    /** Delete entity by PK. Returns affected rows. */
    fun delete(entity: T): Int

    /** Delete by condition. Returns affected rows. */
    fun deleteWhere(predicate: SqlExpr): Int

    /** Find by primary key. */
    fun find(id: Any): T?

    /** Count all rows. */
    fun count(): Long

    /** Check if any rows exist. */
    fun any(): Boolean

    /** Get the underlying IFreeSql instance. */
    val orm: IFreeSql
}

/**
 * Default implementation of IBaseRepository.
 */
open class BaseRepository<T : Any>(
    override val entityType: KClass<T>,
    override val orm: IFreeSql
) : IBaseRepository<T> {

    override fun select(): ISelect<T> = orm.select(entityType)

    override fun insert(entity: T): Int =
        orm.insert(entityType).setSource(entity).executeAffrows()

    override fun insertAndGetId(entity: T): Long =
        orm.insert(entityType).setSource(entity).executeIdentity()

    override fun insertBatch(entities: Collection<T>): Int =
        orm.insert(entityType).setSource(entities).executeAffrows()

    override fun update(entity: T): Int =
        orm.update(entityType).setSource(entity).executeAffrows()

    override fun delete(entity: T): Int =
        orm.delete(entityType).setSource(entity).executeAffrows()

    override fun deleteWhere(predicate: SqlExpr): Int =
        orm.delete(entityType).whereExpr(predicate).executeAffrows()

    override fun find(id: Any): T? =
        orm.select(entityType).where("id = ?", id).first()

    override fun count(): Long = orm.select(entityType).count()

    override fun any(): Boolean = orm.select(entityType).any()
}

/**
 * Extension function to create a repository from IFreeSql.
 */
inline fun <reified T : Any> IFreeSql.repository(): IBaseRepository<T> =
    BaseRepository(T::class, this)
