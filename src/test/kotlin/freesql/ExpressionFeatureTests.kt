package freesql

import freesql.core.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for expression features: subquery expressions (any/all/exists),
 * DynamicFilterInfo, ExpressionCall registry, DateDiff.
 */
class ExpressionFeatureMemoryTest : ExpressionFeatureTests() {
    override val useFileDb = false
}

class ExpressionFeatureFileTest : ExpressionFeatureTests() {
    override val useFileDb = true
}

abstract class ExpressionFeatureTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    // ---- Subquery: EXISTS / NOT EXISTS ----

    @Nested
    inner class ExistsTests {

        @Test
        fun `exists generates correct SQL`() {
            val subQuery = orm.select<TestUser>().whereExpr(TestUsers.isActive eq true)
            val expr = ColumnRef.exists(subQuery)
            val sql = expr.toSql(null)
            assertTrue(sql.startsWith("EXISTS ("), "Expected EXISTS: $sql")
        }

        @Test
        fun `notExists generates correct SQL`() {
            val subQuery = orm.select<TestUser>().whereExpr(TestUsers.isActive eq true)
            val expr = ColumnRef.notExists(subQuery)
            val sql = expr.toSql(null)
            assertTrue(sql.startsWith("NOT EXISTS ("), "Expected NOT EXISTS: $sql")
        }

        @Test
        fun `exists works in where clause`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true)
            ).executeAffrows()
            orm.insert<TestPost>().setSource(
                TestPost(title = "Hello", content = "World", userId = 1)
            ).executeAffrows()

            // Verify EXISTS SQL generates correctly (correlated subquery with raw SQL)
            val existsSql = "EXISTS (SELECT 1 FROM posts WHERE posts.user_id = users.id)"
            val users = orm.select<TestUser>()
                .where(existsSql)
                .toList()

            assertEquals(1, users.size)
            assertEquals("Alice", users[0].name)
        }
    }

    // ---- Subquery: ANY / ALL ----

    @Nested
    inner class AnyAllTests {

        @Test
        fun `eqAny generates correct SQL`() {
            val subQuery = orm.select<TestUser>().selectColumns("age")
            val expr = TestUsers.age eqAny subQuery
            val sql = expr.toSql(null)
            assertTrue(sql.contains("= ANY ("), "Expected = ANY: $sql")
        }

        @Test
        fun `gtAll generates correct SQL`() {
            val subQuery = orm.select<TestUser>().selectColumns("age")
            val expr = TestUsers.age gtAll subQuery
            val sql = expr.toSql(null)
            assertTrue(sql.contains("> ALL ("), "Expected > ALL: $sql")
        }

        @Test
        fun `ltAny generates correct SQL`() {
            val subQuery = orm.select<TestUser>().selectColumns("age")
            val expr = TestUsers.age ltAny subQuery
            val sql = expr.toSql(null)
            assertTrue(sql.contains("< ANY ("), "Expected < ANY: $sql")
        }

        @Test
        fun `geAny generates correct SQL`() {
            val subQuery = orm.select<TestUser>().selectColumns("age")
            val expr = TestUsers.age geAny subQuery
            val sql = expr.toSql(null)
            assertTrue(sql.contains(">= ANY ("), "Expected >= ANY: $sql")
        }

        @Test
        fun `leAll generates correct SQL`() {
            val subQuery = orm.select<TestUser>().selectColumns("age")
            val expr = TestUsers.age leAll subQuery
            val sql = expr.toSql(null)
            assertTrue(sql.contains("<= ALL ("), "Expected <= ALL: $sql")
        }

        @Test
        fun `neAny generates correct SQL`() {
            val subQuery = orm.select<TestUser>().selectColumns("age")
            val expr = TestUsers.age neAny subQuery
            val sql = expr.toSql(null)
            assertTrue(sql.contains("<> ANY ("), "Expected <> ANY: $sql")
        }
    }

    // ---- DynamicFilterInfo ----

    @Nested
    inner class DynamicFilterTests {

        @Test
        fun `simple equals filter`() {
            val filter = DynamicFilterInfo(
                field = "name",
                operator = DynamicFilterOperator.Equals,
                value = "Alice"
            )
            val expr = filter.toSqlExpr(mapOf("name" to TestUsers.name))
            assertNotNull(expr)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("="), "Expected equals: $sql")
        }

        @Test
        fun `contains filter`() {
            val filter = DynamicFilterInfo(
                field = "name",
                operator = DynamicFilterOperator.Contains,
                value = "Ali"
            )
            val expr = filter.toSqlExpr(mapOf("name" to TestUsers.name))
            assertNotNull(expr)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("INSTR") || sql.contains("LIKE"), "Expected INSTR or LIKE: $sql")
        }

        @Test
        fun `combined AND filter`() {
            val filter = DynamicFilterInfo(
                logic = "and",
                filters = listOf(
                    DynamicFilterInfo(field = "name", operator = DynamicFilterOperator.Contains, value = "Ali"),
                    DynamicFilterInfo(field = "age", operator = DynamicFilterOperator.GreaterThanOrEqual, value = 18)
                )
            )
            val expr = filter.toSqlExpr(mapOf("name" to TestUsers.name, "age" to TestUsers.age))
            assertNotNull(expr)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("AND"), "Expected AND: $sql")
        }

        @Test
        fun `combined OR filter`() {
            val filter = DynamicFilterInfo(
                logic = "or",
                filters = listOf(
                    DynamicFilterInfo(field = "name", operator = DynamicFilterOperator.Equals, value = "Alice"),
                    DynamicFilterInfo(field = "name", operator = DynamicFilterOperator.Equals, value = "Bob")
                )
            )
            val expr = filter.toSqlExpr(mapOf("name" to TestUsers.name))
            assertNotNull(expr)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("OR"), "Expected OR: $sql")
        }

        @Test
        fun `isNull filter`() {
            val filter = DynamicFilterInfo(
                field = "email",
                operator = DynamicFilterOperator.IsNull
            )
            val expr = filter.toSqlExpr(mapOf("email" to TestUsers.email))
            assertNotNull(expr)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("IS NULL"), "Expected IS NULL: $sql")
        }

        @Test
        fun `filter with unknown field returns null`() {
            val filter = DynamicFilterInfo(
                field = "nonexistent",
                operator = DynamicFilterOperator.Equals,
                value = "test"
            )
            val expr = filter.toSqlExpr(mapOf("name" to TestUsers.name))
            assertNull(expr)
        }
    }

    // ---- ExpressionCallRegistry ----

    @Nested
    inner class ExpressionCallTests {

        @Test
        fun `register and call custom function`() {
            ExpressionCallRegistry.register("doubleIt") { args ->
                "(${args[0].qualified()} * 2)"
            }

            assertTrue(ExpressionCallRegistry.has("doubleIt"))

            val expr = ExpressionCallRegistry.call("doubleIt", listOf(TestUsers.age))
            val sql = expr.toSql(null)
            assertTrue(sql.contains("* 2"), "Expected * 2 in SQL: $sql")

            // Clean up
            ExpressionCallRegistry.clear()
        }

        @Test
        fun `unregistered function throws`() {
            ExpressionCallRegistry.clear()
            assertFailsWith<IllegalArgumentException> {
                ExpressionCallRegistry.call("nonexistent", listOf(TestUsers.age))
            }
        }

        @Test
        fun `registeredNames returns all`() {
            ExpressionCallRegistry.register("fn1") { "1" }
            ExpressionCallRegistry.register("fn2") { "2" }

            val names = ExpressionCallRegistry.registeredNames()
            assertTrue(names.contains("fn1"))
            assertTrue(names.contains("fn2"))

            ExpressionCallRegistry.clear()
        }
    }

    // ---- DateDiff (already exists, verify) ----

    @Nested
    inner class DateDiffTests {

        @Test
        fun `dateDiffDays generates correct SQL`() {
            val startCol = ColumnRef<String>("created_at", "t")
            val endCol = ColumnRef<String>("updated_at", "t")
            val expr = ColumnRef.dateDiffDays(startCol, endCol)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("julianday"), "Expected julianday: $sql")
            assertTrue(sql.contains("INTEGER"), "Expected INTEGER cast: $sql")
        }

        @Test
        fun `dateDiffHours generates correct SQL`() {
            val startCol = ColumnRef<String>("created_at", "t")
            val endCol = ColumnRef<String>("updated_at", "t")
            val expr = ColumnRef.dateDiffHours(startCol, endCol)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("* 24"), "Expected * 24: $sql")
        }

        @Test
        fun `dateDiffMinutes generates correct SQL`() {
            val startCol = ColumnRef<String>("created_at", "t")
            val endCol = ColumnRef<String>("updated_at", "t")
            val expr = ColumnRef.dateDiffMinutes(startCol, endCol)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("* 1440"), "Expected * 1440: $sql")
        }

        @Test
        fun `dateDiffSeconds generates correct SQL`() {
            val startCol = ColumnRef<String>("created_at", "t")
            val endCol = ColumnRef<String>("updated_at", "t")
            val expr = ColumnRef.dateDiffSeconds(startCol, endCol)
            val sql = expr.toSql(null)
            assertTrue(sql.contains("* 86400"), "Expected * 86400: $sql")
        }
    }
}
