package freesql.extensions

import freesql.core.IFreeSql
import kotlin.reflect.KClass

/**
 * DbContext provides change tracking and unit-of-work semantics.
 * Track entities, then call saveChanges() to persist all changes in a transaction.
 *
 * Usage:
 *   val ctx = DbContext(orm)
 *   ctx.users.add(User(name = "Alice"))
 *   ctx.users.add(User(name = "Bob"))
 *   val user = ctx.users[1] // tracked
 *   user.name = "Updated"
 *   ctx.saveChanges() // inserts + updates in one transaction
 */
class DbContext(val orm: IFreeSql) {

    private val dbSets = mutableMapOf<KClass<*>, DbSet<out Any>>()

    /** Get or create a DbSet for the given entity type. */
    fun <T : Any> set(entityType: KClass<T>): DbSet<T> {
        @Suppress("UNCHECKED_CAST")
        return dbSets.getOrPut(entityType) { DbSet(entityType, orm) } as DbSet<T>
    }

    /** Save all tracked changes in a transaction. Returns total affected rows. */
    fun saveChanges(): Int {
        var total = 0
        orm.transaction {
            dbSets.values.forEach { dbSet ->
                total += dbSet.flushChanges()
            }
        }
        return total
    }

    /** Discard all tracked changes. */
    fun discardChanges() {
        dbSets.values.forEach { it.clear() }
    }
}

/**
 * Extension to get a DbSet using reified type.
 */
inline fun <reified T : Any> DbContext.set(): DbSet<T> = set(T::class)

/**
 * DbSet provides change tracking for a specific entity type.
 * Entities are tracked as Added, Modified, or Removed.
 */
class DbSet<T : Any>(
    val entityType: KClass<T>,
    private val orm: IFreeSql
) {
    enum class EntityState { ADDED, MODIFIED, REMOVED, UNCHANGED }

    private val tracked = mutableMapOf<T, EntityState>()
    private val added = mutableListOf<T>()
    private val modified = mutableListOf<T>()
    private val removed = mutableListOf<T>()

    /** Track a new entity for insertion. */
    fun add(entity: T): DbSet<T> {
        tracked[entity] = EntityState.ADDED
        added.add(entity)
        return this
    }

    /** Track multiple new entities for insertion. */
    fun addAll(entities: Collection<T>): DbSet<T> {
        entities.forEach { add(it) }
        return this
    }

    /** Track an existing entity for update. */
    fun update(entity: T): DbSet<T> {
        tracked[entity] = EntityState.MODIFIED
        modified.add(entity)
        return this
    }

    /** Track an entity for deletion. */
    fun remove(entity: T): DbSet<T> {
        tracked[entity] = EntityState.REMOVED
        removed.add(entity)
        return this
    }

    /** Attach an entity as unchanged (for tracking). */
    fun attach(entity: T): DbSet<T> {
        tracked[entity] = EntityState.UNCHANGED
        return this
    }

    /** Get the state of a tracked entity. */
    fun getState(entity: T): EntityState? = tracked[entity]

    /** Check if an entity is being tracked. */
    fun isTracked(entity: T): Boolean = tracked.containsKey(entity)

    /** Get all tracked entities of a given state. */
    fun getTracked(state: EntityState): List<T> = tracked.filter { it.value == state }.keys.toList()

    /** Get count of pending changes. */
    fun pendingChanges(): Int = added.size + modified.size + removed.size

    /** Flush all pending changes to the database. Returns affected rows. */
    fun flushChanges(): Int {
        var total = 0

        // Inserts
        if (added.isNotEmpty()) {
            total += orm.insert(entityType).setSource(added).executeAffrows()
            added.clear()
        }

        // Updates
        modified.forEach { entity ->
            total += orm.update(entityType).setSource(entity).executeAffrows()
        }
        modified.clear()

        // Deletes
        removed.forEach { entity ->
            total += orm.delete(entityType).setSource(entity).executeAffrows()
        }
        removed.clear()

        // Reset all to unchanged
        tracked.keys.forEach { tracked[it] = EntityState.UNCHANGED }

        return total
    }

    /** Clear all tracking. */
    fun clear() {
        tracked.clear()
        added.clear()
        modified.clear()
        removed.clear()
    }

    /** Get all tracked entity count. */
    fun trackedCount(): Int = tracked.size
}
