package freesql

import freesql.core.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SELECT operations — filtering, pagination, ordering, distinct, joins, aggregation.
 * Runs against both in-memory and file-based SQLite databases.
 */
class SelectMemoryTest : SelectTests() {
    override val useFileDb = false
}

class SelectFileTest : SelectTests() {
    override val useFileDb = true
}

abstract class SelectTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    override fun onSetUp() {
        // Seed test data
        orm.insert<TestUser>().setSource(listOf(
            TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true),
            TestUser(name = "Bob", email = "bob@test.com", age = 25, isActive = true),
            TestUser(name = "Charlie", email = "charlie@test.com", age = 35, isActive = false),
            TestUser(name = "Diana", email = "diana@test.com", age = 28, isActive = true),
            TestUser(name = "Eve", email = "eve@test.com", age = null, isActive = true)
        )).executeAffrows()

        orm.insert<TestPost>().setSource(listOf(
            TestPost(title = "Hello World", content = "First post", userId = 1, viewCount = 100),
            TestPost(title = "Kotlin Tips", content = "Some tips", userId = 1, viewCount = 250),
            TestPost(title = "SQLite Guide", content = "A guide", userId = 2, viewCount = 50),
            TestPost(title = "ORM Design", content = "Architecture", userId = 3, viewCount = 0),
            TestPost(title = "Draft", content = "WIP", userId = 2, viewCount = null)
        )).executeAffrows()
    }

    // ---- Basic queries ----

    @Test
    fun `select all`() {
        val all = orm.select<TestUser>().toList()
        assertEquals(5, all.size)
    }

    @Test
    fun `select with count`() {
        val count = orm.select<TestUser>().count()
        assertEquals(5L, count)
    }

    @Test
    fun `select any`() {
        assertTrue(orm.select<TestUser>().any())
    }

    @Test
    fun `select first`() {
        val user = orm.select<TestUser>().first()
        assertNotNull(user)
    }

    @Test
    fun `select firstOrThrow`() {
        val user = orm.select<TestUser>()
            .whereExpr(TestUsers.name eq "Alice")
            .firstOrThrow()
        assertEquals("Alice", user.name)
    }

    // ---- Expression DSL WHERE ----

    @Test
    fun `select with gt and eq expression`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.age gt 25 and (TestUsers.isActive eq true))
            .toList()
        assertEquals(2, result.size)
    }

    @Test
    fun `select with like expression`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.email like "%test.com")
            .toList()
        assertEquals(5, result.size)
    }

    @Test
    fun `select with startsWith`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.name startsWith "A")
            .toList()
        assertEquals(1, result.size)
        assertEquals("Alice", result[0].name)
    }

    @Test
    fun `select with contains`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.name contains "li")
            .toList()
        assertEquals(2, result.size) // Alice, Charlie
    }

    @Test
    fun `select with IN clause`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.name within listOf("Alice", "Bob", "Charlie"))
            .toList()
        assertEquals(3, result.size)
    }

    @Test
    fun `select with IS NULL`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.age.isNull)
            .toList()
        assertEquals(1, result.size)
        assertEquals("Eve", result[0].name)
    }

    @Test
    fun `select with IS NOT NULL`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.age.isNotNull)
            .toList()
        assertEquals(4, result.size)
    }

    @Test
    fun `select with BETWEEN`() {
        val result = orm.select<TestUser>()
            .whereExpr(TestUsers.age.between(25, 30))
            .toList()
        assertEquals(3, result.size)
    }

    @Test
    fun `select with OR expressions`() {
        val result = orm.select<TestUser>()
            .whereExpr((TestUsers.name eq "Alice") or (TestUsers.name eq "Bob"))
            .toList()
        assertEquals(2, result.size)
    }

    @Test
    fun `select with raw SQL WHERE`() {
        val result = orm.select<TestUser>()
            .where("age > ?", 28)
            .toList()
        assertEquals(2, result.size)
    }

    // ---- Pagination ----

    @Test
    fun `pagination skip take`() {
        val page1 = orm.select<TestUser>()
            .orderBy(TestUsers.id)
            .skip(0).take(2)
            .toList()
        assertEquals(2, page1.size)
        assertEquals("Alice", page1[0].name)
        assertEquals("Bob", page1[1].name)
    }

    @Test
    fun `pagination page number and size`() {
        val page2 = orm.select<TestUser>()
            .orderBy(TestUsers.id)
            .page(2, 2)
            .toList()
        assertEquals(2, page2.size)
        assertEquals("Charlie", page2[0].name)
    }

    // ---- Ordering and Distinct ----

    @Test
    fun `orderBy descending`() {
        val result = orm.select<TestUser>()
            .orderBy(TestUsers.age, SortDirection.DESC)
            .toList()
        assertEquals("Charlie", result[0].name) // Oldest is Charlie (35)
    }

    @Test
    fun `distinct`() {
        val ages = orm.select<TestUser>()
            .whereExpr(TestUsers.age.isNotNull)
            .distinct()
            .toList(TestUsers.age)
        assertEquals(4, ages.size)
    }

    // ---- Joins ----

    @Test
    fun `left join with expression ON`() {
        // Verify join SQL generation works (test via raw SQL since joins may have ambiguity)
        val sql = orm.select<TestUser>()
            .leftJoin(TestPosts, TestUsers.id eq TestPosts.userId)
            .whereExpr(TestUsers.name eq "Alice")
            .toSql()
        assertTrue(sql.contains("LEFT JOIN"), "Should generate LEFT JOIN: $sql")
        assertTrue(sql.contains("users"), "Should reference users table")
        assertTrue(sql.contains("posts"), "Should reference posts table")
    }

    @Test
    fun `inner join`() {
        val sql = orm.select<TestUser>()
            .innerJoin(TestPosts, TestUsers.id eq TestPosts.userId)
            .toSql()
        assertTrue(sql.contains("INNER JOIN"), "Should generate INNER JOIN: $sql")
    }

    // ---- Aggregation ----

    @Test
    fun `sum`() {
        val totalViews = (orm.select<TestPost>().sum(TestPosts.viewCount) as Number).toLong()
        assertEquals(400L, totalViews) // 100 + 250 + 50 + 0
    }

    @Test
    fun `max`() {
        val maxViews = (orm.select<TestPost>().max(TestPosts.viewCount) as Number).toLong()
        assertEquals(250L, maxViews)
    }

    @Test
    fun `min`() {
        val minViews = (orm.select<TestPost>().min(TestPosts.viewCount) as Number).toLong()
        assertEquals(0L, minViews)
    }

    @Test
    fun `avg`() {
        val avgViews = orm.select<TestPost>().avg(TestPosts.viewCount)
        assertNotNull(avgViews)
    }

    // ---- UnionAll ----

    @Test
    fun `unionAll`() {
        val q1 = orm.select<TestUser>().whereExpr(TestUsers.name eq "Alice")
        val q2 = orm.select<TestUser>().whereExpr(TestUsers.name eq "Bob")
        val combined = q1.unionAll(q2).toList()
        assertEquals(2, combined.size)
    }

    // ---- SelectColumns (projection) ----

    @Test
    fun `select specific columns`() {
        val names = orm.select<TestUser>()
            .toList(TestUsers.name)
        assertEquals(5, names.size)
        assertTrue(names.contains("Alice"))
    }
}
