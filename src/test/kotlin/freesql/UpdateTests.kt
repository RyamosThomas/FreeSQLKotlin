package freesql

import freesql.core.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for UPDATE operations — expression SET, raw SET, source-based, SetIf, whereDynamic, batch update.
 * Runs against both in-memory and file-based SQLite databases.
 */
class UpdateMemoryTest : UpdateTests() {
    override val useFileDb = false
}

class UpdateFileTest : UpdateTests() {
    override val useFileDb = true
}

abstract class UpdateTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    override fun onSetUp() {
        orm.insert<TestUser>().setSource(listOf(
            TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true),
            TestUser(name = "Bob", email = "bob@test.com", age = 25, isActive = true),
            TestUser(name = "Charlie", email = "charlie@test.com", age = 35, isActive = false),
            TestUser(name = "Diana", email = "diana@test.com", age = 28, isActive = true),
            TestUser(name = "Eve", email = "eve@test.com", age = null, isActive = true)
        )).executeAffrows()
    }

    @Test
    fun `update with expression WHERE`() {
        val affected = orm.update<TestUser>()
            .set(TestUsers.age, 31)
            .whereExpr(TestUsers.name eq "Alice")
            .executeAffrows()
        assertEquals(1, affected)

        val alice = orm.select<TestUser>().whereExpr(TestUsers.name eq "Alice").first()
        assertNotNull(alice)
        assertEquals(31, alice.age)
    }

    @Test
    fun `update with raw SQL SET`() {
        val affected = orm.update<TestUser>()
            .setRaw("age", "?", 26)
            .where("name = ?", "Bob")
            .executeAffrows()
        assertEquals(1, affected)

        val bob = orm.select<TestUser>().whereExpr(TestUsers.name eq "Bob").first()
        assertNotNull(bob)
        assertEquals(26, bob.age)
    }

    @Test
    fun `update with source entity`() {
        val bob = orm.select<TestUser>().whereExpr(TestUsers.name eq "Bob").first()!!
        val affected = orm.update<TestUser>()
            .setSource(bob.copy(age = 27))
            .executeAffrows()
        // PK-based WHERE should update exactly 1 row
        assertEquals(1, affected)
    }

    @Test
    fun `update setIf conditionally sets fields`() {
        // Only set age if the condition is true
        val newAge = 42

        orm.update<TestUser>()
            .setIf(newAge != null, TestUsers.age, newAge)
            .setIf(false, TestUsers.age, 99)
            .whereExpr(TestUsers.name eq "Alice")
            .executeAffrows()

        val alice = orm.select<TestUser>().whereExpr(TestUsers.name eq "Alice").first()
        assertNotNull(alice)
        assertEquals(42, alice.age)
    }

    @Test
    fun `update whereExpr with IN clause`() {
        val names = listOf("Diana", "Eve")
        val affected = orm.update<TestUser>()
            .set(TestUsers.isActive, false)
            .whereExpr(TestUsers.name within names)
            .executeAffrows()
        assertEquals(2, affected)
    }

    @Test
    fun `batch update multiple users`() {
        val users = orm.select<TestUser>().toList()
        users.forEach { user ->
            orm.update<TestUser>()
                .set(TestUsers.age, (user.age ?: 0) + 1)
                .whereExpr(TestUsers.id eq user.id)
                .executeAffrows()
        }

        val alice = orm.select<TestUser>().whereExpr(TestUsers.name eq "Alice").first()
        assertNotNull(alice)
        assertEquals(31, alice.age)
    }
}
