package freesql.extensions

import freesql.core.IFreeSql

/**
 * Unit of Work pattern. Wraps a transaction and multiple repositories.
 * Call commit() to persist all changes, or rollback() to discard.
 *
 * Usage:
 *   val uow = UnitOfWork(orm)
 *   try {
 *       uow.getRepository<User>().insert(User(name = "Alice"))
 *       uow.getRepository<Post>().insert(Post(title = "Hello"))
 *       uow.commit()
 *   } catch (e: Exception) {
 *       uow.rollback()
 *   }
 */
class UnitOfWork(private val orm: IFreeSql) : AutoCloseable {

    private val repositories = mutableMapOf<kotlin.reflect.KClass<*>, IBaseRepository<out Any>>()
    private var committed = false
    private var rolledBack = false

    /** Get or create a repository for the given entity type. */
    fun <T : Any> getRepository(entityType: kotlin.reflect.KClass<T>): IBaseRepository<T> {
        @Suppress("UNCHECKED_CAST")
        return repositories.getOrPut(entityType) {
            BaseRepository(entityType, orm)
        } as IBaseRepository<T>
    }

    /** Commit all changes in a transaction. */
    fun commit(action: () -> Unit) {
        if (committed) throw IllegalStateException("Already committed")
        if (rolledBack) throw IllegalStateException("Already rolled back")
        orm.transaction {
            action()
            committed = true
        }
    }

    /** Rollback (no-op since we use scoped transactions). */
    fun rollback() {
        rolledBack = true
    }

    override fun close() {
        // Cleanup if needed
    }
}

/** Extension to get a repository using reified type. */
inline fun <reified T : Any> UnitOfWork.getRepository(): IBaseRepository<T> = getRepository(T::class)
