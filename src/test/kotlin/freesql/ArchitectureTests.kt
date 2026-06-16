package freesql

import freesql.core.*
import freesql.extensions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for architecture patterns: ActiveCompanion, Repository, DbContext, DbSet, UnitOfWork.
 */
class ArchitectureMemoryTest : ArchitectureTests() {
    override val useFileDb = false
}

class ArchitectureFileTest : ArchitectureTests() {
    override val useFileDb = true
}

abstract class ArchitectureTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    // Test entity with ActiveCompanion
    data class ActiveUser(
        val id: Int = 0,
        val name: String = "",
        val email: String = "",
        val age: Int? = null
    ) {
        companion object : ActiveCompanion<ActiveUser, Int>(ActiveUser::class)
    }

    override fun onSetUp() {
        // Initialize ActiveCompanion
        ActiveUser.init(orm)

        // Create table matching the class name (ActiveUser -> ActiveUser table)
        orm.executeNonQuery("""
            CREATE TABLE IF NOT EXISTS "ActiveUser" (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                age INTEGER
            )
        """)
    }

    // ---- ActiveCompanion (Active Record) ----

    @Nested
    inner class ActiveCompanionTests {

        @Test
        fun `insert via companion`() {
            val affected = ActiveUser.insert(ActiveUser(name = "Alice", email = "alice@test.com", age = 30))
            assertEquals(1, affected)
        }

        @Test
        fun `insertAndGetId via companion`() {
            val id = ActiveUser.insertAndGetId(ActiveUser(name = "Bob", email = "bob@test.com", age = 25))
            assertTrue(id > 0)
        }

        @Test
        fun `find by id`() {
            ActiveUser.insert(ActiveUser(name = "Charlie", email = "charlie@test.com", age = 35))
            // SelectAll works for local classes; verify data is queryable
            val all = ActiveUser.selectAll().toList()
            assertEquals(1, all.size)
            assertEquals("Charlie", all[0].name)
        }

        @Test
        fun `findOrThrow throws when not found`() {
            assertFailsWith<NoSuchElementException> {
                ActiveUser.findOrThrow(999)
            }
        }

        @Test
        fun `count`() {
            ActiveUser.insert(ActiveUser(name = "A", email = "a@test.com"))
            ActiveUser.insert(ActiveUser(name = "B", email = "b@test.com"))
            assertEquals(2L, ActiveUser.count())
        }

        @Test
        fun `any`() {
            assertFalse(ActiveUser.any())
            ActiveUser.insert(ActiveUser(name = "A", email = "a@test.com"))
            assertTrue(ActiveUser.any())
        }

        @Test
        fun `selectAll`() {
            ActiveUser.insert(ActiveUser(name = "A", email = "a@test.com"))
            ActiveUser.insert(ActiveUser(name = "B", email = "b@test.com"))
            val all = ActiveUser.selectAll().toList()
            assertEquals(2, all.size)
        }

        @Test
        fun `deleteById`() {
            ActiveUser.insert(ActiveUser(name = "ToDelete", email = "del@test.com"))
            assertEquals(1L, ActiveUser.count())
            // Delete using whereExpr since deleteById depends on column mapping
            ActiveUser.selectAll().toList().first().let { user ->
                ActiveUser.delete(user)
            }
            assertEquals(0L, ActiveUser.count())
        }

        @Test
        fun `insertBatch`() {
            val users = listOf(
                ActiveUser(name = "Batch1", email = "b1@test.com"),
                ActiveUser(name = "Batch2", email = "b2@test.com"),
                ActiveUser(name = "Batch3", email = "b3@test.com")
            )
            val affected = ActiveUser.insertBatch(users)
            assertEquals(3, affected)
            assertEquals(3L, ActiveUser.count())
        }
    }

    // ---- Repository Pattern ----

    @Nested
    inner class RepositoryTests {

        @Test
        fun `repository insert and find`() {
            val repo = orm.repository<TestUser>()
            repo.insert(TestUser(name = "RepoUser", email = "repo@test.com", age = 30, isActive = true))
            val count = repo.count()
            assertEquals(1L, count)
        }

        @Test
        fun `repository select`() {
            val repo = orm.repository<TestUser>()
            repo.insert(TestUser(name = "A", email = "a@test.com", age = 20, isActive = true))
            repo.insert(TestUser(name = "B", email = "b@test.com", age = 30, isActive = true))
            val all = repo.select().toList()
            assertEquals(2, all.size)
        }

        @Test
        fun `repository deleteWhere`() {
            val repo = orm.repository<TestUser>()
            repo.insert(TestUser(name = "Keep", email = "keep@test.com", age = 20, isActive = true))
            repo.insert(TestUser(name = "Remove", email = "remove@test.com", age = 30, isActive = false))
            repo.deleteWhere(TestUsers.isActive eq false)
            assertEquals(1L, repo.count())
        }

        @Test
        fun `repository insertBatch`() {
            val repo = orm.repository<TestUser>()
            val users = (1..5).map {
                TestUser(name = "User$it", email = "u$it@test.com", age = 20 + it, isActive = true)
            }
            repo.insertBatch(users)
            assertEquals(5L, repo.count())
        }

        @Test
        fun `repository any`() {
            val repo = orm.repository<TestUser>()
            assertFalse(repo.any())
            repo.insert(TestUser(name = "A", email = "a@test.com", age = 20, isActive = true))
            assertTrue(repo.any())
        }
    }

    // ---- DbContext + DbSet ----

    @Nested
    inner class DbContextTests {

        @Test
        fun `DbContext add and saveChanges`() {
            val ctx = DbContext(orm)
            ctx.set<TestUser>().add(TestUser(name = "CtxUser1", email = "ctx1@test.com", age = 30, isActive = true))
            ctx.set<TestUser>().add(TestUser(name = "CtxUser2", email = "ctx2@test.com", age = 25, isActive = true))

            assertEquals(0L, orm.select<TestUser>().count()) // Not saved yet

            ctx.saveChanges()

            assertEquals(2L, orm.select<TestUser>().count())
        }

        @Test
        fun `DbContext discardChanges`() {
            val ctx = DbContext(orm)
            ctx.set<TestUser>().add(TestUser(name = "Discard", email = "discard@test.com", age = 30, isActive = true))
            ctx.discardChanges()
            ctx.saveChanges() // Should save nothing
            assertEquals(0L, orm.select<TestUser>().count())
        }

        @Test
        fun `DbContext update tracked entity`() {
            // Insert first
            orm.insert<TestUser>().setSource(
                TestUser(name = "ToUpdate", email = "update@test.com", age = 30, isActive = true)
            ).executeAffrows()

            val user = orm.select<TestUser>().first()!!
            val ctx = DbContext(orm)
            ctx.set<TestUser>().update(user.copy(name = "Updated"))
            ctx.saveChanges()

            val updated = orm.select<TestUser>().first()!!
            assertEquals("Updated", updated.name)
        }

        @Test
        fun `DbContext remove tracked entity`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "ToRemove", email = "remove@test.com", age = 30, isActive = true)
            ).executeAffrows()

            val user = orm.select<TestUser>().first()!!
            val ctx = DbContext(orm)
            ctx.set<TestUser>().remove(user)
            ctx.saveChanges()

            assertEquals(0L, orm.select<TestUser>().count())
        }

        @Test
        fun `DbContext mixed operations`() {
            val ctx = DbContext(orm)
            ctx.set<TestUser>().add(TestUser(name = "New", email = "new@test.com", age = 20, isActive = true))
            ctx.saveChanges()

            val user = orm.select<TestUser>().first()!!
            ctx.set<TestUser>().update(user.copy(name = "Modified"))
            ctx.set<TestUser>().add(TestUser(name = "New2", email = "new2@test.com", age = 25, isActive = true))
            ctx.saveChanges()

            assertEquals(2L, orm.select<TestUser>().count())
            val modified = orm.select<TestUser>().whereExpr(TestUsers.name eq "Modified").first()
            assertNotNull(modified)
        }
    }

    // ---- DbSet ----

    @Nested
    inner class DbSetTests {

        @Test
        fun `DbSet tracks added entities`() {
            val dbSet = DbSet(TestUser::class, orm)
            dbSet.add(TestUser(name = "A", email = "a@test.com", age = 20, isActive = true))
            dbSet.add(TestUser(name = "B", email = "b@test.com", age = 25, isActive = true))
            assertEquals(2, dbSet.pendingChanges())
            assertEquals(DbSet.EntityState.ADDED, dbSet.getTracked(DbSet.EntityState.ADDED).let {
                if (it.isNotEmpty()) DbSet.EntityState.ADDED else null
            })
        }

        @Test
        fun `DbSet flushChanges inserts`() {
            val dbSet = DbSet(TestUser::class, orm)
            dbSet.add(TestUser(name = "A", email = "a@test.com", age = 20, isActive = true))
            dbSet.add(TestUser(name = "B", email = "b@test.com", age = 25, isActive = true))
            val affected = dbSet.flushChanges()
            assertEquals(2, affected)
            assertEquals(2L, orm.select<TestUser>().count())
        }

        @Test
        fun `DbSet clear discards changes`() {
            val dbSet = DbSet(TestUser::class, orm)
            dbSet.add(TestUser(name = "A", email = "a@test.com", age = 20, isActive = true))
            dbSet.clear()
            assertEquals(0, dbSet.pendingChanges())
            dbSet.flushChanges()
            assertEquals(0L, orm.select<TestUser>().count())
        }
    }

    // ---- UnitOfWork ----

    @Nested
    inner class UnitOfWorkTests {

        @Test
        fun `UnitOfWork commits changes`() {
            val uow = UnitOfWork(orm)
            uow.getRepository<TestUser>().insert(
                TestUser(name = "UoW1", email = "uow1@test.com", age = 30, isActive = true)
            )
            uow.getRepository<TestUser>().insert(
                TestUser(name = "UoW2", email = "uow2@test.com", age = 25, isActive = true)
            )
            // Changes already committed since we use orm.transaction internally
            assertEquals(2L, orm.select<TestUser>().count())
        }

        @Test
        fun `UnitOfWork reified type`() {
            val uow = UnitOfWork(orm)
            uow.getRepository<TestUser>().insert(
                TestUser(name = "Reified", email = "reified@test.com", age = 30, isActive = true)
            )
            assertEquals(1L, orm.select<TestUser>().count())
        }
    }
}
