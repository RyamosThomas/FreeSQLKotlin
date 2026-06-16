package freesql.core

/**
 * A type-safe reference to a database column.
 * Enables building expressions via operator overloading,
 * replacing C#'s Expression<Func<T, bool>> with a Kotlin DSL approach.
 *
 * Usage:
 * ```
 * object Users : TableColumns<User>(User::class) {
 *     val id = int("id")
 *     val name = varchar("name", 255)
 *     val age = int("age")
 * }
 * // Then:
 * where { Users.age gt 18 and (Users.name like "%john%") }
 * ```
 */
class ColumnRef<T>(
    /** Database column name. */
    val columnName: String,
    /** Owning table. */
    val tableName: String = "",
    /** Full qualified name (table.column). */
    val qualifiedName: String = if (tableName.isEmpty()) columnName else "$tableName.$columnName"
) {
    // ---- Comparison operators → SqlExpr ----

    /** column = value */
    infix fun eq(value: T?): SqlExpr = BinaryExpr(this, "=", ValueExpr(value))

    /** column = column */
    infix fun eq(other: ColumnRef<T>): SqlExpr = BinaryExpr(this, "=", ColumnRefExpr(other))

    /** column != value */
    infix fun ne(value: T?): SqlExpr = BinaryExpr(this, "<>", ValueExpr(value))

    /** column != column */
    infix fun ne(other: ColumnRef<T>): SqlExpr = BinaryExpr(this, "<>", ColumnRefExpr(other))

    /** column > value */
    infix fun gt(value: T): SqlExpr = BinaryExpr(this, ">", ValueExpr(value))

    /** column > column */
    infix fun gt(other: ColumnRef<T>): SqlExpr = BinaryExpr(this, ">", ColumnRefExpr(other))

    /** column >= value */
    infix fun ge(value: T): SqlExpr = BinaryExpr(this, ">=", ValueExpr(value))

    /** column >= column */
    infix fun ge(other: ColumnRef<T>): SqlExpr = BinaryExpr(this, ">=", ColumnRefExpr(other))

    /** column < value */
    infix fun lt(value: T): SqlExpr = BinaryExpr(this, "<", ValueExpr(value))

    /** column < column */
    infix fun lt(other: ColumnRef<T>): SqlExpr = BinaryExpr(this, "<", ColumnRefExpr(other))

    /** column <= value */
    infix fun le(value: T): SqlExpr = BinaryExpr(this, "<=", ValueExpr(value))

    /** column <= column */
    infix fun le(other: ColumnRef<T>): SqlExpr = BinaryExpr(this, "<=", ColumnRefExpr(other))

    /** column IN (values) */
    infix fun within(values: Collection<T>): SqlExpr = InExpr(this, values.map { ValueExpr(it) })

    /** column NOT IN (values) */
    infix fun notIn(values: Collection<T>): SqlExpr = NotInExpr(this, values.map { ValueExpr(it) })

    /** column BETWEEN lower AND upper */
    fun between(lower: T, upper: T): SqlExpr = BetweenExpr(this, ValueExpr(lower), ValueExpr(upper))

    /** column LIKE pattern */
    infix fun like(pattern: String): SqlExpr = LikeExpr(this, pattern, negated = false)

    /** column NOT LIKE pattern */
    infix fun notLike(pattern: String): SqlExpr = LikeExpr(this, pattern, negated = true)

    /** column IS NULL / IS NOT NULL */
    val isNull: SqlExpr get() = IsNullExpr(this, negated = false)
    val isNotNull: SqlExpr get() = IsNullExpr(this, negated = true)

    // ---- String-specific methods (only valid for ColumnRef<String>) ----

    /** column LIKE '%value%' */
    infix fun contains(value: String): SqlExpr =
        LikeExpr(this, "%$value%", negated = false)

    /** column LIKE 'value%' */
    infix fun startsWith(value: String): SqlExpr =
        LikeExpr(this, "$value%", negated = false)

    /** column LIKE '%value' */
    infix fun endsWith(value: String): SqlExpr =
        LikeExpr(this, "%$value", negated = false)

    /** INSTR(column, value) > 0 */
    fun containsExpr(value: String): SqlExpr =
        RawSqlExpr("INSTR(${qualified()}, ${ValueExpr(value).toSql(null)}) > 0")

    /** LENGTH(column) */
    fun length(): SqlExpr = FuncExpr("LENGTH", listOf(this))

    /** LOWER(column) */
    fun lower(): ColumnRef<String> = ColumnRef("LOWER(${qualified()})", "")

    /** UPPER(column) */
    fun upper(): ColumnRef<String> = ColumnRef("UPPER(${qualified()})", "")

    /** TRIM(column) */
    fun trim(): ColumnRef<String> = ColumnRef("TRIM(${qualified()})", "")

    // ---- Numeric methods ----

    /** ABS(column) */
    fun abs(): ColumnRef<T> = ColumnRef("ABS(${qualified()})", "")

    /** ROUND(column, decimals) */
    fun round(decimals: Int = 0): ColumnRef<T> = ColumnRef("ROUND(${qualified()}, $decimals)", "")

    // ---- Aggregate functions ----

    fun count(): ColumnRef<Long> = ColumnRef("COUNT(${qualified()})", "")
    fun countDistinct(): ColumnRef<Long> = ColumnRef("COUNT(DISTINCT ${qualified()})", "")
    fun sum(): ColumnRef<T> = ColumnRef("SUM(${qualified()})", "")
    fun avg(): ColumnRef<Double> = ColumnRef("AVG(${qualified()})", "")
    fun min(): ColumnRef<T> = ColumnRef("MIN(${qualified()})", "")
    fun max(): ColumnRef<T> = ColumnRef("MAX(${qualified()})", "")

    // ---- DateTime properties ----

    /** Extract year from datetime column */
    fun year(): SqlExpr = RawSqlExpr("CAST(strftime('%Y', ${qualified()}) AS INTEGER)")

    /** Extract month from datetime column */
    fun month(): SqlExpr = RawSqlExpr("CAST(strftime('%m', ${qualified()}) AS INTEGER)")

    /** Extract day from datetime column */
    fun day(): SqlExpr = RawSqlExpr("CAST(strftime('%d', ${qualified()}) AS INTEGER)")

    /** Extract hour from datetime column */
    fun hour(): SqlExpr = RawSqlExpr("CAST(strftime('%H', ${qualified()}) AS INTEGER)")

    /** Extract minute from datetime column */
    fun minute(): SqlExpr = RawSqlExpr("CAST(strftime('%M', ${qualified()}) AS INTEGER)")

    /** Extract second from datetime column */
    fun second(): SqlExpr = RawSqlExpr("CAST(strftime('%S', ${qualified()}) AS INTEGER)")

    /** Extract day of week from datetime column (0=Sunday) */
    fun dayOfWeek(): SqlExpr = RawSqlExpr("CAST(strftime('%w', ${qualified()}) AS INTEGER)")

    /** Extract day of year from datetime column */
    fun dayOfYear(): SqlExpr = RawSqlExpr("CAST(strftime('%j', ${qualified()}) AS INTEGER)")

    /** Extract date portion from datetime column */
    fun date(): SqlExpr = RawSqlExpr("date(${qualified()})")

    /** Extract time portion from datetime column */
    fun time(): SqlExpr = RawSqlExpr("time(${qualified()})")

    /** Get ticks from datetime column */
    fun ticks(): SqlExpr = RawSqlExpr("CAST(((strftime('%J', ${qualified()}) - 1721425.5) * 864000000000) AS INTEGER)")

    /** Extract millisecond from datetime column */
    fun millisecond(): SqlExpr = RawSqlExpr("CAST(strftime('%f', ${qualified()})*1000.0%1000.0 AS INTEGER)")

    // ---- DateTime methods ----

    /** Add days to datetime column */
    fun addDays(n: Double): SqlExpr = RawSqlExpr("datetime(${qualified()}, '${n} days')")

    /** Add hours to datetime column */
    fun addHours(n: Double): SqlExpr = RawSqlExpr("datetime(${qualified()}, '${n} hours')")

    /** Add minutes to datetime column */
    fun addMinutes(n: Double): SqlExpr = RawSqlExpr("datetime(${qualified()}, '${n} minutes')")

    /** Add months to datetime column */
    fun addMonths(n: Int): SqlExpr = RawSqlExpr("datetime(${qualified()}, '${n} months')")

    /** Add seconds to datetime column */
    fun addSeconds(n: Double): SqlExpr = RawSqlExpr("datetime(${qualified()}, '${n} seconds')")

    /** Add years to datetime column */
    fun addYears(n: Int): SqlExpr = RawSqlExpr("datetime(${qualified()}, '${n} years')")

    // ---- Math functions ----

    /** Returns -1, 0, or 1 */
    fun sign(): SqlExpr = RawSqlExpr("CASE WHEN ${qualified()} > 0 THEN 1 WHEN ${qualified()} < 0 THEN -1 ELSE 0 END")

    /** Floor function */
    fun floor(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS INTEGER) - CASE WHEN ${qualified()} < 0 AND CAST(${qualified()} AS INTEGER) <> ${qualified()} THEN 1 ELSE 0 END")

    /** Ceiling function */
    fun ceiling(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS INTEGER) + CASE WHEN ${qualified()} > 0 AND CAST(${qualified()} AS INTEGER) <> ${qualified()} THEN 1 ELSE 0 END")

    /** Exponential function */
    fun exp(): SqlExpr = RawSqlExpr("exp(${qualified()})")

    /** Natural logarithm */
    fun log(): SqlExpr = RawSqlExpr("log(${qualified()})")

    /** Base-10 logarithm */
    fun log10(): SqlExpr = RawSqlExpr("log10(${qualified()})")

    /** Square root */
    fun sqrt(): SqlExpr = RawSqlExpr("sqrt(${qualified()})")

    /** Cosine */
    fun cos(): SqlExpr = RawSqlExpr("cos(${qualified()})")

    /** Sine */
    fun sin(): SqlExpr = RawSqlExpr("sin(${qualified()})")

    /** Tangent */
    fun tan(): SqlExpr = RawSqlExpr("tan(${qualified()})")

    /** Arc cosine */
    fun acos(): SqlExpr = RawSqlExpr("acos(${qualified()})")

    /** Arc sine */
    fun asin(): SqlExpr = RawSqlExpr("asin(${qualified()})")

    /** Arc tangent */
    fun atan(): SqlExpr = RawSqlExpr("atan(${qualified()})")

    // ---- Type conversion methods ----

    /** Convert to boolean */
    fun toBoolean(): SqlExpr = RawSqlExpr("(${qualified()} NOT IN ('0','false'))")

    /** Convert to integer */
    fun toInt(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS INTEGER)")

    /** Convert to long */
    fun toLong(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS INTEGER)")

    /** Convert to double */
    fun toDouble(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS DOUBLE)")

    /** Convert to float */
    fun toFloat(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS FLOAT)")

    /** Convert to decimal */
    fun toDecimal(): SqlExpr = RawSqlExpr("CAST(${qualified()} AS DECIMAL(36,18))")

    /** Convert to char */
    fun toChar(): SqlExpr = RawSqlExpr("substr(CAST(${qualified()} AS CHARACTER), 1, 1)")

    /** Convert to GUID string */
    fun toGuid(): SqlExpr = RawSqlExpr("substr(CAST(\${qualified()} AS CHARACTER), 1, 36)")

    // ---- Additional type conversions (matching FreeSql ConvertTest) ----

    /** Convert to Byte (tinyint) */
    fun toByte(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS TINYINT)")

    /** Convert to Short/Int16 (smallint) */
    fun toShort(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS SMALLINT)")

    /** Convert to SByte (tinyint, signed) */
    fun toSByte(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS TINYINT)")

    /** Convert to UInt16 (smallint) */
    fun toUInt16(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS SMALLINT)")

    /** Convert to UInt32 (int) */
    fun toUInt32(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS INT)")

    /** Convert to UInt64 (bigint) */
    fun toUInt64(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS BIGINT)")

    /** Convert to DateTime (datetime) */
    fun toDateTime(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS DATETIME)")

    /** Convert to String (varchar) */
    fun toStr(): SqlExpr = RawSqlExpr("CAST(\${qualified()} AS VARCHAR)")

    // ---- String additional methods ----

    /** SUBSTR(column, start) — 1-based start position */
    fun substring(start: Int): ColumnRef<String> = ColumnRef("substr(${qualified()}, $start)", "")

    /** SUBSTR(column, start, length) — 1-based start position */
    fun substring(start: Int, length: Int): ColumnRef<String> = ColumnRef("substr(${qualified()}, $start, $length)", "")

    /** REPLACE(column, old, new) */
    fun replace(oldValue: String, newValue: String): ColumnRef<String> =
        ColumnRef("replace(${qualified()}, ${ValueExpr(oldValue).toSql(null)}, ${ValueExpr(newValue).toSql(null)})", "")

    /** Find index of substring (0-based, -1 if not found) */
    fun indexOf(value: String): SqlExpr = RawSqlExpr("(instr(${qualified()}, ${ValueExpr(value).toSql(null)}) - 1)")

    /** Pad string on the left */
    fun padLeft(width: Int, padChar: Char = ' '): SqlExpr = RawSqlExpr("replace(hex(zeroblob(($width - length(${qualified()} + 1) / 2))), '0', '${padChar}') || ${qualified()}")

    /** Pad string on the right */
    fun padRight(width: Int, padChar: Char = ' '): SqlExpr = RawSqlExpr("${qualified()} || replace(hex(zeroblob(($width - length(${qualified()} + 1) / 2))), '0', '${padChar}')")

    /** Compare two columns, returns -1, 0, or 1 */
    fun compareTo(other: ColumnRef<*>): SqlExpr = RawSqlExpr("CASE WHEN ${qualified()} = ${other.qualified()} THEN 0 WHEN ${qualified()} > ${other.qualified()} THEN 1 ELSE -1 END")

    /** Check if column is null or empty */
    fun isNullOrEmptySql(): SqlExpr = RawSqlExpr("(${qualified()} IS NULL OR ${qualified()} = '')")

    /** Check if column is null, empty, or whitespace */
    fun isNullOrWhiteSpaceSql(): SqlExpr = RawSqlExpr("(${qualified()} IS NULL OR ${qualified()} = '' OR ltrim(${qualified()}) = '')")

    // ---- Bitwise operations ----

    /** Bitwise AND */
    infix fun bitwiseAnd(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} & ${other.qualified()})")

    /** Bitwise OR */
    infix fun bitwiseOr(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} | ${other.qualified()})")

    /** Bitwise XOR */
    infix fun bitwiseXor(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} ^ ${other.qualified()})")

    /** Left shift */
    infix fun shl(bits: Int): SqlExpr = RawSqlExpr("(${qualified()} << $bits)")

    /** Right shift */
    infix fun shr(bits: Int): SqlExpr = RawSqlExpr("(${qualified()} >> $bits)")

    /** Bitwise NOT */
    fun bitwiseNot(): SqlExpr = RawSqlExpr("~${qualified()}")

    // ---- Coalesce ----

    /** Return column value if not null, otherwise fallback */
    fun coalesce(fallback: T): SqlExpr = RawSqlExpr("ifnull(${qualified()}, ${ValueExpr(fallback).toSql(null)})")

    /** Return column value if not null, otherwise other column */
    fun coalesceColumn(other: ColumnRef<T>): SqlExpr = RawSqlExpr("ifnull(${qualified()}, ${other.qualified()})")

    // ---- Arithmetic on numeric columns ----

    /** Addition */
    operator fun plus(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} + ${other.qualified()})")

    /** Subtraction */
    operator fun minus(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} - ${other.qualified()})")

    /** Multiplication */
    operator fun times(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} * ${other.qualified()})")

    /** Division */
    operator fun div(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} / ${other.qualified()})")

    /** Modulo */
    operator fun rem(other: ColumnRef<T>): SqlExpr = RawSqlExpr("(${qualified()} % ${other.qualified()})")

    /** Unary minus */
    operator fun unaryMinus(): SqlExpr = RawSqlExpr("(-${qualified()})")

    // ---- Subquery expressions ----

    /** Column = ANY (subquery) */
    infix fun eqAny(subQuery: ISelect<*>): SqlExpr = AnyExpr(this, "=", subQuery.toSql())

    /** Column > ANY (subquery) */
    infix fun gtAny(subQuery: ISelect<*>): SqlExpr = AnyExpr(this, ">", subQuery.toSql())

    /** Column < ANY (subquery) */
    infix fun ltAny(subQuery: ISelect<*>): SqlExpr = AnyExpr(this, "<", subQuery.toSql())

    /** Column >= ANY (subquery) */
    infix fun geAny(subQuery: ISelect<*>): SqlExpr = AnyExpr(this, ">=", subQuery.toSql())

    /** Column <= ANY (subquery) */
    infix fun leAny(subQuery: ISelect<*>): SqlExpr = AnyExpr(this, "<=", subQuery.toSql())

    /** Column <> ANY (subquery) */
    infix fun neAny(subQuery: ISelect<*>): SqlExpr = AnyExpr(this, "<>", subQuery.toSql())

    /** Column = ALL (subquery) */
    infix fun eqAll(subQuery: ISelect<*>): SqlExpr = AllExpr(this, "=", subQuery.toSql())

    /** Column > ALL (subquery) */
    infix fun gtAll(subQuery: ISelect<*>): SqlExpr = AllExpr(this, ">", subQuery.toSql())

    /** Column < ALL (subquery) */
    infix fun ltAll(subQuery: ISelect<*>): SqlExpr = AllExpr(this, "<", subQuery.toSql())

    /** Column >= ALL (subquery) */
    infix fun geAll(subQuery: ISelect<*>): SqlExpr = AllExpr(this, ">=", subQuery.toSql())

    /** Column <= ALL (subquery) */
    infix fun leAll(subQuery: ISelect<*>): SqlExpr = AllExpr(this, "<=", subQuery.toSql())

    /** Column <> ALL (subquery) */
    infix fun neAll(subQuery: ISelect<*>): SqlExpr = AllExpr(this, "<>", subQuery.toSql())

    // ---- Utility ----

    /** Get the qualified column name for SQL. */
    fun qualified(quote: (String) -> String = { it }): String {
        return if (tableName.isEmpty()) quote(columnName)
        else "${quote(tableName)}.${quote(columnName)}"
    }

    /** Get the SQL representation for expressions. */
    fun toSql(quote: ((String) -> String)?): String {
        if (quote == null) return qualifiedName
        return qualified(quote)
    }

    override fun toString(): String = qualifiedName

    companion object {
        /** Current local datetime */
        fun dateTimeNow(): SqlExpr = RawSqlExpr("datetime(current_timestamp,'localtime')")

        /** Current UTC datetime */
        fun dateTimeUtcNow(): SqlExpr = RawSqlExpr("current_timestamp")

        /** Current local date */
        fun dateTimeToday(): SqlExpr = RawSqlExpr("date(current_timestamp,'localtime')")

        /** Generate a new GUID */
        fun newGuid(): SqlExpr = RawSqlExpr("substr(hex(randomblob(16)),1,8)||'-'||substr(hex(randomblob(16)),1,4)||'-4'||substr(hex(randomblob(16)),1,3)||'-'||substr('89ab',(abs(random())%4)+1,1)||substr(hex(randomblob(16)),1,3)||'-'||substr(hex(randomblob(16)),1,12)")

        /** Random integer */
        fun random(): SqlExpr = RawSqlExpr("CAST(random() * 1000000000 AS INTEGER)")

        /** Random double between 0 and 1 */
        fun randomDouble(): SqlExpr = RawSqlExpr("random()")

        /** Date difference in days (end - start) */
        fun dateDiffDays(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
            RawSqlExpr("CAST(julianday(${end.qualified()}) - julianday(${start.qualified()}) AS INTEGER)")

        /** Date difference in hours (end - start) */
        fun dateDiffHours(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
            RawSqlExpr("CAST((julianday(${end.qualified()}) - julianday(${start.qualified()})) * 24 AS INTEGER)")

        /** Date difference in minutes (end - start) */
        fun dateDiffMinutes(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
            RawSqlExpr("CAST((julianday(${end.qualified()}) - julianday(${start.qualified()})) * 1440 AS INTEGER)")

        /** Date difference in seconds (end - start) */
        fun dateDiffSeconds(start: ColumnRef<*>, end: ColumnRef<*>): SqlExpr =
            RawSqlExpr("CAST((julianday(${end.qualified()}) - julianday(${start.qualified()})) * 86400 AS INTEGER)")

        /** EXISTS (subquery) */
        fun exists(subQuery: ISelect<*>): SqlExpr = ExistsExpr(subQuery.toSql())

        /** NOT EXISTS (subquery) */
        fun notExists(subQuery: ISelect<*>): SqlExpr = ExistsExpr(subQuery.toSql(), negated = true)
    }
}

// ---- Sort direction ----

enum class SortDirection { ASC, DESC }

class OrderByClause(val column: ColumnRef<*>, val direction: SortDirection)
