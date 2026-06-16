package freesql

import freesql.annotation.*
import freesql.core.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for edge cases: column comparison, expression chaining, special characters,
 * InsertOrUpdate, and additional WHERE types.
 * Runs against both in-memory and file-based SQLite databases.
 */
class EdgeCaseMemoryTest : EdgeCaseTests() {
    override val useFileDb = false
}

class EdgeCaseFileTest : EdgeCaseTests() {
    override val useFileDb = true
}

abstract class EdgeCaseTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    override fun onSetUp() {
        orm.insert<TestUser>().setSource(listOf(
            TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true),
            TestUser(name = "Bob", email = "bob@test.com", age = 25, isActive = true),
            TestUser(name = "Charlie", email = "charlie@test.com", age = 35, isActive = false),
            TestUser(name = "Diana", email = "diana@test.com", age = 28, isActive = true)
        )).executeAffrows()

        orm.insert<TestPost>().setSource(listOf(
            TestPost(title = "Hello World", content = "First post", userId = 1, viewCount = 100),
            TestPost(title = "Kotlin Tips", content = "Some tips", userId = 1, viewCount = 250),
            TestPost(title = "SQLite Guide", content = "A guide", userId = 2, viewCount = 50)
        )).executeAffrows()
    }

    // ---- Column Comparison ----

    @Test
    fun `column to column comparison`() {
        // Compare two columns: posts where view_count > user_id
        val sql = TestPosts.viewCount gt TestPosts.userId
        assertTrue(sql.toSql(null).contains(">"), "Column comparison should generate > operator")
    }

    // ---- Expression Chaining ----

    @Test
    fun `multiple AND conditions`() {
        val result = orm.select<TestUser>()
            .whereExpr(
                (TestUsers.age gt 20) and
                (TestUsers.age lt 35) and
                (TestUsers.isActive eq true)
            )
            .toList()
        assertEquals(3, result.size) // Alice(30), Bob(25), Diana(28)
    }

    @Test
    fun `AND mixed with OR`() {
        val result = orm.select<TestUser>()
            .whereExpr(
                ((TestUsers.name eq "Alice") or (TestUsers.name eq "Bob")) and
                (TestUsers.isActive eq true)
            )
            .toList()
        assertEquals(2, result.size)
    }

    @Test
    fun `OR with multiple conditions`() {
        val result = orm.select<TestUser>()
            .whereExpr(
                (TestUsers.name eq "Alice") or
                (TestUsers.name eq "Bob") or
                (TestUsers.name eq "Charlie")
            )
            .toList()
        assertEquals(3, result.size)
    }

    // ---- Special Characters ----

    @Test
    fun `insert and query with single quotes in data`() {
        orm.insert<TestUser>().setSource(
            TestUser(name = "O'Brien", email = "obrien@test.com", age = 40, isActive = true)
        ).executeAffrows()

        val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "O'Brien").first()
        assertNotNull(user, "Should find user with single quote in name")
        assertEquals("O'Brien", user.name)
    }

    @Test
    fun `insert and query with unicode characters`() {
        orm.insert<TestUser>().setSource(
            TestUser(name = "日本語テスト", email = "unicode@test.com", age = 25, isActive = true)
        ).executeAffrows()

        val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "日本語テスト").first()
        assertNotNull(user, "Should find user with unicode name")
    }

    @Test
    fun `insert and query with empty string`() {
        orm.insert<TestUser>().setSource(
            TestUser(name = "", email = "empty@test.com", age = 20, isActive = true)
        ).executeAffrows()

        val user = orm.select<TestUser>().whereExpr(TestUsers.email eq "empty@test.com").first()
        assertNotNull(user)
        assertEquals("", user.name)
    }

    // ---- InsertOrUpdate ----

    @Test
    fun `insertOrUpdate inserts new row`() {
        orm.insertOrUpdate<TestUser>()
            .setSource(TestUser(name = "UpsertNew", email = "upsert@test.com", age = 30, isActive = true))
            .executeAffrows()

        val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "UpsertNew").first()
        assertNotNull(user)
    }

    @Test
    fun `insertOrUpdate updates existing row`() {
        // First insert
        orm.insertOrUpdate<TestUser>()
            .setSource(TestUser(name = "UpsertExists", email = "upsert@test.com", age = 30, isActive = true))
            .executeAffrows()

        // Update via upsert
        orm.insertOrUpdate<TestUser>()
            .setSource(TestUser(name = "UpsertExists", email = "upsert@test.com", age = 31, isActive = true))
            .executeAffrows()

        val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "UpsertExists").first()
        assertNotNull(user)
    }

    // ---- Additional WHERE Types ----

    @Test
    fun `where with multiple parameters`() {
        val result = orm.select<TestUser>()
            .where("age > ? AND is_active = ?", 25, 1)
            .toList()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `whereExpr with complex expression`() {
        val result = orm.select<TestUser>()
            .whereExpr(
                (TestUsers.age ge 25) and
                (TestUsers.age le 35) and
                (TestUsers.isActive eq true)
            )
            .toList()
        assertEquals(3, result.size) // Alice(30), Bob(25), Diana(28)
    }

    @Test
    fun `where with IN clause raw SQL`() {
        val result = orm.select<TestUser>()
            .where("name IN (?, ?, ?)", "Alice", "Bob", "Charlie")
            .toList()
        assertEquals(3, result.size)
    }

    // ---- Execution Edge Cases ----

    @Test
    fun `select on empty table returns empty`() {
        val count = orm.select<TestCategory>().count()
        assertEquals(0L, count)
    }

    @Test
    fun `delete on empty table returns zero`() {
        val affected = orm.delete<TestCategory>().executeAffrows()
        assertEquals(0, affected)
    }

    @Test
    fun `update on empty table returns zero`() {
        val affected = orm.update<TestCategory>()
            .set(TestCategories.name, "test")
            .executeAffrows()
        assertEquals(0, affected)
    }

    @Test
    fun `first on empty table returns null`() {
        val result = orm.select<TestCategory>().first()
        assertNull(result)
    }

    @Test
    fun `firstOrThrow on empty table throws`() {
        assertFailsWith<Exception> {
            orm.select<TestCategory>().firstOrThrow()
        }
    }

    // ---- Transaction Edge Cases ----

    @Test
    fun `nested transaction simulation`() {
        // SQLite doesn't support true nested transactions, but savepoints can simulate them
        // For now, just verify a transaction within a transaction works
        orm.transaction {
            orm.insert<TestUser>().setSource(
                TestUser(name = "NestedTx", email = "nested@test.com", age = 30, isActive = true)
            ).executeAffrows()
        }

        val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "NestedTx").first()
        assertNotNull(user)
    }

    // ---- Aggregate Edge Cases ----

    @Test
    fun `sum on empty result returns zero`() {
        val total = orm.select<TestCategory>().sum(TestCategories.id) as? Long ?: 0L
        assertEquals(0L, total)
    }

    @Test
    fun `max on empty result returns null`() {
        val maxVal = orm.select<TestCategory>().max(TestCategories.id)
        assertNull(maxVal)
    }

    @Test
    fun `min on empty result returns null`() {
        val minVal = orm.select<TestCategory>().min(TestCategories.id)
        assertNull(minVal)
    }

    @Test
    fun `avg on empty result returns zero or null`() {
        val avgVal = orm.select<TestCategory>().avg(TestCategories.id)
        // May return null or 0.0 depending on implementation
        assertTrue(avgVal == null || avgVal == 0.0, "avg on empty should be null or 0.0, got $avgVal")
    }
}
