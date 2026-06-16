package freesql

import freesql.core.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for expression DSL — string, datetime, math, type conversion, bitwise, arithmetic, coalesce.
 * These tests primarily verify SQL generation (toSql), not database execution.
 * Runs against both in-memory and file-based SQLite databases.
 */
class ExpressionMemoryTest : ExpressionTests() {
    override val useFileDb = false
}

class ExpressionFileTest : ExpressionTests() {
    override val useFileDb = true
}

abstract class ExpressionTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    // ---- String Expressions ----

    @Nested
    inner class StringExpressions {

        @Test
        fun `toLower generates LOWER`() {
            val sql = TestUsers.name.lower().toSql(null)
            assertTrue(sql.contains("LOWER"), "Expected LOWER in sql: $sql")
            assertTrue(sql.contains("users.name"), "Expected users.name in sql: $sql")
        }

        @Test
        fun `toUpper generates UPPER`() {
            val sql = TestUsers.name.upper().toSql(null)
            assertTrue(sql.contains("UPPER"), "Expected UPPER in sql: $sql")
            assertTrue(sql.contains("users.name"), "Expected users.name in sql: $sql")
        }

        @Test
        fun `trim generates TRIM`() {
            val sql = TestUsers.name.trim().toSql(null)
            assertTrue(sql.contains("TRIM"), "Expected TRIM in sql: $sql")
            assertTrue(sql.contains("users.name"), "Expected users.name in sql: $sql")
        }

        @Test
        fun `length generates LENGTH`() {
            val sql = TestUsers.name.length().toSql(null)
            assertEquals("LENGTH(users.name)", sql)
        }

        @Test
        fun `indexOf generates instr`() {
            val sql = TestUsers.name.indexOf("test").toSql(null)
            assertTrue(sql.contains("instr"), "Expected instr in sql: $sql")
            assertTrue(sql.contains("'test'"), "Expected 'test' in sql: $sql")
            assertTrue(sql.contains("- 1"), "Expected -1 offset for 0-based index: $sql")
        }

        @Test
        fun `contains generates LIKE percent pattern`() {
            val expr = TestUsers.name.contains("hello")
            val sql = expr.toSql(null)
            assertTrue(sql.contains("LIKE"), "Expected LIKE: $sql")
            assertTrue(sql.contains("%hello%"), "Expected %hello% pattern: $sql")
        }

        @Test
        fun `contains with special chars`() {
            val expr = TestUsers.name.contains("50%")
            val sql = expr.toSql(null)
            assertTrue(sql.contains("LIKE"), "Expected LIKE: $sql")
            assertTrue(sql.contains("%50"), "Escaped percent: $sql")
        }

        @Test
        fun `startsWith generates LIKE prefix`() {
            val expr = TestUsers.name.startsWith("Al")
            val sql = expr.toSql(null)
            assertTrue(sql.contains("LIKE"), "Expected LIKE: $sql")
            assertTrue(sql.contains("Al%"), "Expected Al% pattern: $sql")
        }

        @Test
        fun `endsWith generates LIKE suffix`() {
            val expr = TestUsers.name.endsWith("ice")
            val sql = expr.toSql(null)
            assertTrue(sql.contains("LIKE"), "Expected LIKE: $sql")
            assertTrue(sql.contains("%ice"), "Expected %ice pattern: $sql")
        }

        @Test
        fun `isNullOrEmptySql generates IS NULL and empty check`() {
            val sql = TestUsers.name.isNullOrEmptySql().toSql(null)
            assertTrue(sql.contains("IS NULL"), "Expected IS NULL: $sql")
            assertTrue(sql.contains("= ''"), "Expected = '': $sql")
        }

        @Test
        fun `isNullOrWhiteSpaceSql generates IS NULL and ltrim check`() {
            val sql = TestUsers.name.isNullOrWhiteSpaceSql().toSql(null)
            assertTrue(sql.contains("IS NULL"), "Expected IS NULL: $sql")
            assertTrue(sql.contains("ltrim"), "Expected ltrim: $sql")
        }

        @Test
        fun `compareTo generates CASE WHEN`() {
            val sql = TestUsers.name.compareTo(TestUsers.email).toSql(null)
            assertTrue(sql.contains("CASE WHEN"), "Expected CASE WHEN: $sql")
            assertTrue(sql.contains("THEN 0"), "Expected THEN 0: $sql")
            assertTrue(sql.contains("THEN 1"), "Expected THEN 1: $sql")
            assertTrue(sql.contains("ELSE -1"), "Expected ELSE -1: $sql")
        }

        @Test
        fun `containsExpr uses INSTR`() {
            val sql = TestUsers.name.containsExpr("test").toSql(null)
            assertTrue(sql.contains("INSTR"), "Expected INSTR: $sql")
            assertTrue(sql.contains("> 0"), "Expected > 0: $sql")
        }

        @Test
        fun `lower execution with real data`() {
            // Seed a user
            orm.insert<TestUser>().setSource(
                TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true)
            ).executeAffrows()

            val result = orm.select<TestUser>()
                .where("LOWER(\"name\") = ?", "alice")
                .toList()
            assertEquals(1, result.size, "Expected 1 user 'alice' (lowered)")
            assertEquals("Alice", result[0].name)
        }

        @Test
        fun `upper execution with real data`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true)
            ).executeAffrows()

            val result = orm.select<TestUser>()
                .where("UPPER(\"name\") = ?", "ALICE")
                .toList()
            assertEquals(1, result.size, "Expected 1 user 'ALICE' (uppered)")
            assertEquals("Alice", result[0].name)
        }

        @Test
        fun `length in WHERE with real data`() {
            orm.insert<TestUser>().setSource(listOf(
                TestUser(name = "Bob", email = "bob@test.com", age = 25, isActive = true),
                TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true)
            )).executeAffrows()

            val lengthSql = TestUsers.name.length().toSql(null)
            val result = orm.select<TestUser>()
                .where("$lengthSql = ?", 3)
                .toList()
            assertEquals(1, result.size, "Expected 1 user with name length 3")
            assertEquals("Bob", result[0].name)
        }

        @Test
        fun `isNullOrEmptySql in WHERE with real data`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "Alice", email = "alice@test.com", age = 30, isActive = true)
            ).executeAffrows()

            val result = orm.select<TestUser>()
                .whereExpr(TestUsers.name.isNullOrEmptySql())
                .toList()
            assertEquals(0, result.size, "Expected 0 users with null/empty name")
        }
    }

    // ---- DateTime Expressions ----

    @Nested
    inner class DateTimeExpressions {

        @Test
        fun `year generates strftime`() {
            val sql = TestUsers.createdAt.year().toSql(null)
            assertTrue(sql.contains("strftime('%Y'"), "Expected strftime('%Y'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `month generates strftime`() {
            val sql = TestUsers.createdAt.month().toSql(null)
            assertTrue(sql.contains("strftime('%m'"), "Expected strftime('%m'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `day generates strftime`() {
            val sql = TestUsers.createdAt.day().toSql(null)
            assertTrue(sql.contains("strftime('%d'"), "Expected strftime('%d'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `hour generates strftime`() {
            val sql = TestUsers.createdAt.hour().toSql(null)
            assertTrue(sql.contains("strftime('%H'"), "Expected strftime('%H'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `minute generates strftime`() {
            val sql = TestUsers.createdAt.minute().toSql(null)
            assertTrue(sql.contains("strftime('%M'"), "Expected strftime('%M'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `second generates strftime`() {
            val sql = TestUsers.createdAt.second().toSql(null)
            assertTrue(sql.contains("strftime('%S'"), "Expected strftime('%S'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `dayOfWeek generates strftime`() {
            val sql = TestUsers.createdAt.dayOfWeek().toSql(null)
            assertTrue(sql.contains("strftime('%w'"), "Expected strftime('%w'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `dayOfYear generates strftime`() {
            val sql = TestUsers.createdAt.dayOfYear().toSql(null)
            assertTrue(sql.contains("strftime('%j'"), "Expected strftime('%j'): $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `date generates date function`() {
            val sql = TestUsers.createdAt.date().toSql(null)
            assertTrue(sql.contains("date("), "Expected date(): $sql")
        }

        @Test
        fun `addDays generates datetime modifier`() {
            val sql = TestUsers.createdAt.addDays(5.0).toSql(null)
            assertTrue(sql.contains("datetime("), "Expected datetime(): $sql")
            assertTrue(sql.contains("5.0 days"), "Expected 5.0 days: $sql")
        }

        @Test
        fun `addHours generates datetime modifier`() {
            val sql = TestUsers.createdAt.addHours(2.0).toSql(null)
            assertTrue(sql.contains("datetime("), "Expected datetime(): $sql")
            assertTrue(sql.contains("2.0 hours"), "Expected 2.0 hours: $sql")
        }

        @Test
        fun `addMinutes generates datetime modifier`() {
            val sql = TestUsers.createdAt.addMinutes(30.0).toSql(null)
            assertTrue(sql.contains("datetime("), "Expected datetime(): $sql")
            assertTrue(sql.contains("30.0 minutes"), "Expected 30.0 minutes: $sql")
        }

        @Test
        fun `addMonths generates datetime modifier`() {
            val sql = TestUsers.createdAt.addMonths(1).toSql(null)
            assertTrue(sql.contains("datetime("), "Expected datetime(): $sql")
            assertTrue(sql.contains("1 months"), "Expected 1 months: $sql")
        }

        @Test
        fun `addSeconds generates datetime modifier`() {
            val sql = TestUsers.createdAt.addSeconds(60.0).toSql(null)
            assertTrue(sql.contains("datetime("), "Expected datetime(): $sql")
            assertTrue(sql.contains("60.0 seconds"), "Expected 60.0 seconds: $sql")
        }

        @Test
        fun `addYears generates datetime modifier`() {
            val sql = TestUsers.createdAt.addYears(1).toSql(null)
            assertTrue(sql.contains("datetime("), "Expected datetime(): $sql")
            assertTrue(sql.contains("1 years"), "Expected 1 years: $sql")
        }

        @Test
        fun `year execution with real data`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "DateTest", email = "date@test.com", age = 25, isActive = true, createdAt = "2024-06-15 10:30:45")
            ).noneParameter().executeAffrows()

            val yearSql = TestUsers.createdAt.year().toSql(null)
            val result = orm.ado.executeDataTable(
                "SELECT * FROM users WHERE name = 'DateTest' AND $yearSql = 2024"
            )
            assertEquals(1, result.size, "Expected 1 user with year 2024")
        }

        @Test
        fun `date execution with real data`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "DateTest2", email = "date2@test.com", age = 25, isActive = true, createdAt = "2024-06-15 10:30:45")
            ).noneParameter().executeAffrows()

            val dateSql = TestUsers.createdAt.date().toSql(null)
            val result = orm.ado.executeDataTable(
                "SELECT * FROM users WHERE name = 'DateTest2' AND $dateSql = '2024-06-15'"
            )
            assertEquals(1, result.size, "Expected 1 user with date 2024-06-15")
        }
    }

    // ---- Math Expressions ----

    @Nested
    inner class MathExpressions {

        @Test
        fun `abs generates ABS`() {
            val sql = TestUsers.age.abs().toSql(null)
            assertTrue(sql.contains("ABS"), "Expected ABS: $sql")
            assertTrue(sql.contains("users.age"), "Expected users.age: $sql")
        }

        @Test
        fun `round generates ROUND`() {
            val sql = TestUsers.age.round().toSql(null)
            assertTrue(sql.contains("ROUND"), "Expected ROUND: $sql")
        }

        @Test
        fun `round with decimals generates ROUND`() {
            val sql = TestUsers.age.round(2).toSql(null)
            assertTrue(sql.contains("ROUND"), "Expected ROUND: $sql")
            assertTrue(sql.contains("2"), "Expected decimals=2: $sql")
        }

        @Test
        fun `floor generates CAST-based floor`() {
            val sql = TestUsers.age.floor().toSql(null)
            assertTrue(sql.contains("CAST("), "Expected CAST: $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
            assertTrue(sql.contains("CASE WHEN"), "Expected CASE WHEN: $sql")
        }

        @Test
        fun `ceiling generates CAST-based ceiling`() {
            val sql = TestUsers.age.ceiling().toSql(null)
            assertTrue(sql.contains("CAST("), "Expected CAST: $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
            assertTrue(sql.contains("CASE WHEN"), "Expected CASE WHEN: $sql")
        }

        @Test
        fun `exp generates exp`() {
            val sql = TestUsers.age.exp().toSql(null)
            assertEquals("exp(users.age)", sql)
        }

        @Test
        fun `log generates log`() {
            val sql = TestUsers.age.log().toSql(null)
            assertEquals("log(users.age)", sql)
        }

        @Test
        fun `log10 generates log10`() {
            val sql = TestUsers.age.log10().toSql(null)
            assertEquals("log10(users.age)", sql)
        }

        @Test
        fun `sqrt generates sqrt`() {
            val sql = TestUsers.age.sqrt().toSql(null)
            assertEquals("sqrt(users.age)", sql)
        }

        @Test
        fun `cos generates cos`() {
            val sql = TestUsers.age.cos().toSql(null)
            assertEquals("cos(users.age)", sql)
        }

        @Test
        fun `sin generates sin`() {
            val sql = TestUsers.age.sin().toSql(null)
            assertEquals("sin(users.age)", sql)
        }

        @Test
        fun `tan generates tan`() {
            val sql = TestUsers.age.tan().toSql(null)
            assertEquals("tan(users.age)", sql)
        }

        @Test
        fun `acos generates acos`() {
            val sql = TestUsers.age.acos().toSql(null)
            assertEquals("acos(users.age)", sql)
        }

        @Test
        fun `asin generates asin`() {
            val sql = TestUsers.age.asin().toSql(null)
            assertEquals("asin(users.age)", sql)
        }

        @Test
        fun `atan generates atan`() {
            val sql = TestUsers.age.atan().toSql(null)
            assertEquals("atan(users.age)", sql)
        }

        @Test
        fun `sign generates CASE expression`() {
            val sql = TestUsers.age.sign().toSql(null)
            assertTrue(sql.contains("CASE WHEN"), "Expected CASE WHEN: $sql")
            assertTrue(sql.contains("> 0 THEN 1"), "Expected > 0 THEN 1: $sql")
            assertTrue(sql.contains("< 0 THEN -1"), "Expected < 0 THEN -1: $sql")
            assertTrue(sql.contains("ELSE 0"), "Expected ELSE 0: $sql")
        }
    }

    // ---- Type Conversion Expressions ----

    @Nested
    inner class TypeConversionExpressions {

        @Test
        fun `toBoolean generates NOT IN`() {
            val sql = TestUsers.name.toBoolean().toSql(null)
            assertTrue(sql.contains("NOT IN"), "Expected NOT IN: $sql")
            assertTrue(sql.contains("'0'"), "Expected '0': $sql")
            assertTrue(sql.contains("'false'"), "Expected 'false': $sql")
        }

        @Test
        fun `toInt generates CAST AS INTEGER`() {
            val sql = TestUsers.name.toInt().toSql(null)
            assertTrue(sql.contains("CAST("), "Expected CAST: $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `toLong generates CAST AS INTEGER`() {
            val sql = TestUsers.name.toLong().toSql(null)
            assertTrue(sql.contains("CAST("), "Expected CAST: $sql")
            assertTrue(sql.contains("AS INTEGER"), "Expected AS INTEGER: $sql")
        }

        @Test
        fun `toDouble generates CAST AS DOUBLE`() {
            val sql = TestUsers.name.toDouble().toSql(null)
            assertTrue(sql.contains("CAST("), "Expected CAST: $sql")
            assertTrue(sql.contains("AS DOUBLE") || sql.contains("AS REAL"), "Expected AS DOUBLE or AS REAL: $sql")
        }

        @Test
        fun `toString generates CAST`() {
            val sql = TestUsers.age.toStr().toSql(null)
            assertTrue(sql.contains("CAST("), "Expected CAST: $sql")
        }
    }

    // ---- Bitwise Expressions ----

    @Nested
    inner class BitwiseExpressions {

        @Test
        fun `bitwiseAnd generates ampersand`() {
            val other = ColumnRef<Int>("255", "")
            val sql = TestUsers.age.bitwiseAnd(other).toSql(null)
            assertTrue(sql.contains("&"), "Expected &: $sql")
        }

        @Test
        fun `bitwiseOr generates pipe`() {
            val other = ColumnRef<Int>("1", "")
            val sql = TestUsers.age.bitwiseOr(other).toSql(null)
            assertTrue(sql.contains("|"), "Expected |: $sql")
        }

        @Test
        fun `shl generates left shift`() {
            val sql = TestUsers.age.shl(2).toSql(null)
            assertTrue(sql.contains("<<"), "Expected <<: $sql")
        }

        @Test
        fun `shr generates right shift`() {
            val sql = TestUsers.age.shr(1).toSql(null)
            assertTrue(sql.contains(">>"), "Expected >>: $sql")
        }

        @Test
        fun `bitwiseNot generates complement`() {
            val sql = TestUsers.age.bitwiseNot().toSql(null)
            assertTrue(sql.contains("~"), "Expected ~: $sql")
        }
    }

    // ---- Arithmetic Expressions ----

    @Nested
    inner class ArithmeticExpressions {

        @Test
        fun `plus generates addition`() {
            val other = ColumnRef<Int>("5", "")
            val sql = (TestUsers.age + other).toSql(null)
            assertTrue(sql.contains("+"), "Expected +: $sql")
        }

        @Test
        fun `minus generates subtraction`() {
            val other = ColumnRef<Int>("3", "")
            val sql = (TestUsers.age - other).toSql(null)
            assertTrue(sql.contains("-"), "Expected -: $sql")
        }

        @Test
        fun `div generates division`() {
            val other = ColumnRef<Int>("2", "")
            val sql = (TestUsers.age / other).toSql(null)
            assertTrue(sql.contains("/"), "Expected /: $sql")
        }
    }

    // ---- Coalesce Expressions ----

    @Nested
    inner class CoalesceExpressions {

        @Test
        fun `coalesce generates ifnull`() {
            val sql = TestUsers.age.coalesce(0).toSql(null).uppercase()
            assertTrue(sql.contains("IFNULL"), "Expected IFNULL: $sql")
        }

        @Test
        fun `coalesceColumn generates ifnull`() {
            val sql = TestUsers.age.coalesceColumn(TestUsers.id).toSql(null).uppercase()
            assertTrue(sql.contains("IFNULL"), "Expected IFNULL: $sql")
        }
    }
}
