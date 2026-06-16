package freesql.core

/**
 * Base class for all SQL expression nodes.
 * Built via operator overloading on ColumnRef, converted to SQL by providers.
 */
abstract class SqlExpr {
    abstract fun toSql(quote: ((String) -> String)?): String

    /** Combine with another expression using AND. */
    infix fun and(other: SqlExpr): SqlExpr = AndExpr(this, other)

    /** Combine with another expression using OR. */
    infix fun or(other: SqlExpr): SqlExpr = OrExpr(this, other)

    /** Negate this expression. */
    operator fun not(): SqlExpr = NotExpr(this)
}

// ---- Binary comparison ----

class BinaryExpr(
    val left: ColumnRef<*>,
    val op: String,
    val right: SqlExpr
) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "${left.toSql(quote)} $op ${right.toSql(quote)}"
    }
}

class ColumnRefExpr(val column: ColumnRef<*>) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String = column.toSql(quote)
}

// ---- Value literal ----

class ValueExpr(val value: Any?) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return formatSqlLiteral(value)
    }

    companion object {
        fun formatSqlLiteral(value: Any?): String {
            return when (value) {
                null -> "NULL"
                is Boolean -> if (value) "1" else "0"
                is String -> "'${value.replace("\\", "\\\\").replace("'", "''")}'"
                is Char -> "'${value}'"
                is ByteArray -> "X'${value.joinToString("") { "%02X".format(it) }}'"
                is java.util.UUID -> "'${value}'"
                is java.time.LocalDateTime -> "'${value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
                is java.time.Instant -> {
                    val ldt = java.time.LocalDateTime.ofInstant(value, java.time.ZoneId.systemDefault())
                    "'${ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
                }
                is java.util.Date -> {
                    val ldt = java.time.LocalDateTime.ofInstant(value.toInstant(), java.time.ZoneId.systemDefault())
                    "'${ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
                }
                is Enum<*> -> value.ordinal.toString()
                is java.math.BigDecimal -> value.toPlainString()
                else -> value.toString()
            }
        }
    }
}

// ---- Logical operators ----

class AndExpr(val left: SqlExpr, val right: SqlExpr) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "(${left.toSql(quote)} AND ${right.toSql(quote)})"
    }
}

class OrExpr(val left: SqlExpr, val right: SqlExpr) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "(${left.toSql(quote)} OR ${right.toSql(quote)})"
    }
}

class NotExpr(val expr: SqlExpr) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "NOT (${expr.toSql(quote)})"
    }
}

// ---- Special predicates ----

class InExpr(val column: ColumnRef<*>, val values: List<SqlExpr>) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val vals = values.joinToString(", ") { it.toSql(quote) }
        return "${column.toSql(quote)} IN ($vals)"
    }
}

class NotInExpr(val column: ColumnRef<*>, val values: List<SqlExpr>) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val vals = values.joinToString(", ") { it.toSql(quote) }
        return "${column.toSql(quote)} NOT IN ($vals)"
    }
}

class BetweenExpr(val column: ColumnRef<*>, val lower: SqlExpr, val upper: SqlExpr) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "${column.toSql(quote)} BETWEEN ${lower.toSql(quote)} AND ${upper.toSql(quote)}"
    }
}

class LikeExpr(val column: ColumnRef<*>, val pattern: String, val negated: Boolean) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val op = if (negated) "NOT LIKE" else "LIKE"
        val escaped = pattern.replace("\\", "\\\\").replace("'", "''")
        return "${column.toSql(quote)} $op '$escaped'"
    }
}

class IsNullExpr(val column: ColumnRef<*>, val negated: Boolean) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "${column.toSql(quote)} IS ${if (negated) "NOT " else ""}NULL"
    }
}

// ---- Raw SQL expression (for complex expressions) ----

class RawSqlExpr(val sql: String) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String = sql
}

// ---- Function expressions ----

class FuncExpr(val funcName: String, val args: List<ColumnRef<*>>) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val argSql = args.joinToString(", ") { it.toSql(quote) }
        return "$funcName($argSql)"
    }
}

// ---- Subquery expressions ----

class SubQueryExpr(val sql: String) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String = "($sql)"
}

// ---- EXISTS / NOT EXISTS ----

class ExistsExpr(val subquery: String, val negated: Boolean = false) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        return "${if (negated) "NOT " else ""}EXISTS ($subquery)"
    }
}

// ---- ANY / ALL subquery expressions ----

class AnyExpr(val column: ColumnRef<*>, val operator: String, val subquery: String) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val colSql = column.toSql(quote)
        return "$colSql $operator ANY ($subquery)"
    }
}

class AllExpr(val column: ColumnRef<*>, val operator: String, val subquery: String) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val colSql = column.toSql(quote)
        return "$colSql $operator ALL ($subquery)"
    }
}

// ---- DateDiff expression ----

/**
 * Represents a date difference calculation between two columns or expressions.
 * Supports days, hours, minutes, and seconds.
 */
class DateDiffExpr(
    val unit: DateDiffUnit,
    val start: SqlExpr,
    val end: SqlExpr
) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val startSql = start.toSql(quote)
        val endSql = end.toSql(quote)
        return when (unit) {
            DateDiffUnit.DAYS -> "CAST(julianday($endSql) - julianday($startSql) AS INTEGER)"
            DateDiffUnit.HOURS -> "CAST((julianday($endSql) - julianday($startSql)) * 24 AS INTEGER)"
            DateDiffUnit.MINUTES -> "CAST((julianday($endSql) - julianday($startSql)) * 1440 AS INTEGER)"
            DateDiffUnit.SECONDS -> "CAST((julianday($endSql) - julianday($startSql)) * 86400 AS INTEGER)"
        }
    }
}

enum class DateDiffUnit {
    DAYS, HOURS, MINUTES, SECONDS
}

// ---- Case expression ----

class CaseExprBuilder {
    private val cases = mutableListOf<Pair<SqlExpr, SqlExpr>>()
    private var elseValue: SqlExpr? = null

    fun `when`(condition: SqlExpr, then: Any?): CaseExprBuilder {
        cases.add(condition to ValueExpr(then))
        return this
    }

    fun `else`(value: Any?): CaseExprBuilder {
        elseValue = ValueExpr(value)
        return this
    }

    fun build(): SqlExpr = CaseExpr(cases, elseValue)
}

class CaseExpr(
    val cases: List<Pair<SqlExpr, SqlExpr>>,
    val elseValue: SqlExpr?
) : SqlExpr() {
    override fun toSql(quote: ((String) -> String)?): String {
        val sb = StringBuilder("CASE")
        for ((condition, then) in cases) {
            sb.append(" WHEN ${condition.toSql(quote)} THEN ${then.toSql(quote)}")
        }
        if (elseValue != null) {
            sb.append(" ELSE ${elseValue.toSql(quote)}")
        }
        sb.append(" END")
        return sb.toString()
    }
}

/** DSL builder for CASE expressions. */
fun caseExpr(): CaseExprBuilder = CaseExprBuilder()

// ---- Static SQL Functions ----

/**
 * Static SQL functions that can be used without a column reference.
 */
object SqlFunctions {
    /** Current local datetime */
    fun now(): SqlExpr = RawSqlExpr("datetime(current_timestamp,'localtime')")

    /** Current UTC datetime */
    fun utcNow(): SqlExpr = RawSqlExpr("current_timestamp")

    /** Current local date */
    fun today(): SqlExpr = RawSqlExpr("date(current_timestamp,'localtime')")

    /** Generate a new GUID */
    fun newGuid(): SqlExpr = RawSqlExpr("substr(hex(randomblob(16)),1,8)||'-'||substr(hex(randomblob(16)),1,4)||'-4'||substr(hex(randomblob(16)),1,3)||'-'||substr('89ab',(abs(random())%4)+1,1)||substr(hex(randomblob(16)),1,3)||'-'||substr(hex(randomblob(16)),1,12)")

    /** Random integer */
    fun random(): SqlExpr = RawSqlExpr("CAST(random() * 1000000000 AS INTEGER)")

    /** Random double between 0 and 1 */
    fun randomDouble(): SqlExpr = RawSqlExpr("random()")

    /** Date difference in days (end - start) */
    fun dateDiffDays(start: SqlExpr, end: SqlExpr): SqlExpr =
        DateDiffExpr(DateDiffUnit.DAYS, start, end)

    /** Date difference in hours (end - start) */
    fun dateDiffHours(start: SqlExpr, end: SqlExpr): SqlExpr =
        DateDiffExpr(DateDiffUnit.HOURS, start, end)

    /** Date difference in minutes (end - start) */
    fun dateDiffMinutes(start: SqlExpr, end: SqlExpr): SqlExpr =
        DateDiffExpr(DateDiffUnit.MINUTES, start, end)

    /** Date difference in seconds (end - start) */
    fun dateDiffSeconds(start: SqlExpr, end: SqlExpr): SqlExpr =
        DateDiffExpr(DateDiffUnit.SECONDS, start, end)

    /** Date difference in days between two columns */
    fun dateDiffDays(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
        DateDiffExpr(DateDiffUnit.DAYS, ColumnRefExpr(start), ColumnRefExpr(end))

    /** Date difference in hours between two columns */
    fun dateDiffHours(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
        DateDiffExpr(DateDiffUnit.HOURS, ColumnRefExpr(start), ColumnRefExpr(end))

    /** Date difference in minutes between two columns */
    fun dateDiffMinutes(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
        DateDiffExpr(DateDiffUnit.MINUTES, ColumnRefExpr(start), ColumnRefExpr(end))

    /** Date difference in seconds between two columns */
    fun dateDiffSeconds(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
        DateDiffExpr(DateDiffUnit.SECONDS, ColumnRefExpr(start), ColumnRefExpr(end))
}
