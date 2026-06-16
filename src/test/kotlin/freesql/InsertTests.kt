package freesql

import freesql.core.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for INSERT operations — single, batch, with ColumnRef, split execute, and noneParameter modes.
 * Runs against both in-memory and file-based SQLite databases.
 */
class InsertMemoryTest : InsertTests() {
    override val useFileDb = false
}

class InsertFileTest : InsertTests() {
    override val useFileDb = true
}

abstract class InsertTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    @Test
    fun `single insert with identity`() {
        val id = orm.insert<TestUser>().setSource(
            TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true)
        ).executeIdentity()
        assertEquals(1L, id)
    }

    @Test
    fun `batch insert users`() {
        val users = listOf(
            TestUser(name = "Bob", email = "bob@test.com", age = 25, isActive = true),
            TestUser(name = "Charlie", email = "charlie@test.com", age = 35, isActive = false),
            TestUser(name = "Diana", email = "diana@test.com", age = 28, isActive = true),
            TestUser(name = "Eve", email = "eve@test.com", age = null, isActive = true)
        )
        val count = orm.insert<TestUser>().setSource(users).executeAffrows()
        assertEquals(4, count)
    }

    @Test
    fun `batch insert posts`() {
        // Insert users first for foreign key context
        orm.insert<TestUser>().setSource(
            TestUser(name = "User1", email = "user1@test.com", age = 30, isActive = true)
        ).executeAffrows()

        val posts = listOf(
            TestPost(title = "Hello World", content = "First post", userId = 1, viewCount = 100),
            TestPost(title = "Kotlin Tips", content = "Some tips", userId = 1, viewCount = 250),
            TestPost(title = "Draft", content = "WIP", userId = 1, viewCount = null)
        )
        val count = orm.insert<TestPost>().setSource(posts).executeAffrows()
        assertEquals(3, count)
    }

    @Test
    fun `insert with source entity`() {
        val id = orm.insert<TestUser>()
            .setSource(TestUser(name = "Frank", email = "frank@test.com", age = 40, isActive = true))
            .executeIdentity()
        assertTrue(id > 0)
    }

    @Test
    fun `insert split execute large batch`() {
        // Insert 150 users in one call — should be split automatically
        val users = (1..150).map { i ->
            TestUser(name = "BulkUser$i", email = "bulk$i@test.com", age = 20 + (i % 50), isActive = true)
        }
        val count = orm.insert<TestUser>().setSource(users).executeAffrows()
        assertEquals(150, count)

        // Verify they're all there
        val total = orm.select<TestUser>().count()
        assertEquals(150L, total)
    }

    @Test
    fun `insert with noneParameter mode`() {
        val ormNone = freeSql {
            useDataType("sqlite")
            useConnectionString("jdbc:sqlite::memory:")
            useAutoSyncStructure()
            useNoneCommandParameter()
        }
        ormNone.codeFirst.syncStructure(TestUser::class)

        try {
            val id = ormNone.insert<TestUser>().setSource(
                TestUser(name = "NoParam", email = "noparam@test.com", age = 50, isActive = true)
            ).executeIdentity()
            assertEquals(1L, id)
        } finally {
            (ormNone as? freesql.provider.sqlite.SqliteProvider)?.close()
        }
    }
}
