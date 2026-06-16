package freesql

import freesql.core.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DELETE operations — expression WHERE, raw WHERE, whereDynamic, and cascading scenarios.
 * Runs against both in-memory and file-based SQLite databases.
 */
class DeleteMemoryTest : DeleteTests() {
    override val useFileDb = false
}

class DeleteFileTest : DeleteTests() {
    override val useFileDb = true
}

abstract class DeleteTests : FreeSqlTestBase() {

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
    fun `delete with expression WHERE`() {
        val affected = orm.delete<TestUser>()
            .whereExpr(TestUsers.name eq "Eve")
            .executeAffrows()
        assertEquals(1, affected)
        assertEquals(4L, orm.select<TestUser>().count())
    }

    @Test
    fun `delete with raw SQL WHERE`() {
        val affected = orm.delete<TestUser>()
            .where("name = ?", "Diana")
            .executeAffrows()
        assertEquals(1, affected)
        assertEquals(4L, orm.select<TestUser>().count())
    }

    @Test
    fun `delete whereExpr with IN clause`() {
        val names = listOf("Alice", "Bob")
        val affected = orm.delete<TestUser>()
            .whereExpr(TestUsers.name within names)
            .executeAffrows()
        assertEquals(2, affected)
        assertEquals(3L, orm.select<TestUser>().count())
    }

    @Test
    fun `delete and verify gone`() {
        orm.insert<TestUser>().setSource(
            TestUser(name = "DelVerif", email = "delv@test.com", age = 40, isActive = true)
        ).executeAffrows()

        val found = orm.select<TestUser>().whereExpr(TestUsers.name eq "DelVerif").first()
        assertNotNull(found)

        orm.delete<TestUser>().whereExpr(TestUsers.name eq "DelVerif").executeAffrows()

        val gone = orm.select<TestUser>().whereExpr(TestUsers.name eq "DelVerif").first()
        assertNull(gone)
    }

    @Test
    fun `delete all`() {
        val affected = orm.delete<TestUser>().executeAffrows()
        assertEquals(5, affected)
        assertEquals(0L, orm.select<TestUser>().count())
    }

    @Test
    fun `delete with IN clause on multiple conditions`() {
        val affected = orm.delete<TestUser>()
            .whereExpr(
                (TestUsers.age lt 28) or (TestUsers.isActive eq false)
            )
            .executeAffrows()
        // Bob(25), Charlie(35, inactive), Eve(null < 28 is false in SQL)
        // Bob: age 25 < 28 -> yes
        // Charlie: inactive -> yes
        // Eve: null < 28 -> null (false)
        assertEquals(2, affected)
    }
}
