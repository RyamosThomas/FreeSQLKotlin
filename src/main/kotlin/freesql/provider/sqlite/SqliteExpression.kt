package freesql.provider.sqlite

/**
 * Translates Kotlin expression references into SQLite SQL.
 * Port of FreeSql SqliteExpression.
 *
 * Handles string methods, DateTime accessors, math functions,
 * type conversions, and collection operations.
 */
class SqliteExpression(
    private val utils: SqliteUtils
) {

    /**
     * Translate a string method call to SQLite SQL.
     */
    fun translateStringMethod(method: String, target: String, args: List<String>): String {
        return when (method) {
            "lowercase", "lower" -> "lower($target)"
            "uppercase", "upper" -> "upper($target)"
            "trim" -> "trim($target)"
            "trimStart" -> {
                if (args.isNotEmpty()) "ltrim($target, ${args[0]})" else "ltrim($target)"
            }
            "trimEnd" -> {
                if (args.isNotEmpty()) "rtrim($target, ${args[0]})" else "rtrim($target)"
            }
            "substring" -> {
                // Kotlin 0-based → SQLite substr 1-based
                if (args.size >= 2) {
                    "substr($target, ${args[0].toInt() + 1}, ${args[1]})"
                } else {
                    "substr($target, ${args[0].toInt() + 1})"
                }
            }
            "replace" -> "replace($target, ${args[0]}, ${args[1]})"
            "indexOf" -> {
                if (args.size >= 2) {
                    "instr(substr($target, ${args[1].toInt() + 1}), ${args[0]}) - 1 + ${args[1].toInt()}"
                } else {
                    "instr($target, ${args[0]}) - 1"
                }
            }
            "contains" -> "instr($target, ${args[0]}) > 0"
            "startsWith" -> "$target LIKE ${args[0]} || '%'"
            "endsWith" -> "$target LIKE '%' || ${args[0]}"
            "padStart" -> "substr(replace(hex(zeroblob(${args[0]})), '0', ${args.getOrElse(1) { "' '" }}) , 1, ${args[0]} - length($target)) || $target"
            "padEnd" -> "$target || substr(replace(hex(zeroblob(${args[0]})), '0', ${args.getOrElse(1) { "' '" }}), 1, ${args[0]} - length($target))"
            "isEmpty" -> "$target = ''"
            "isNotEmpty" -> "$target <> ''"
            "length" -> "length($target)"
            else -> "$method($target${if (args.isNotEmpty()) ", " + args.joinToString(", ") else ""})"
        }
    }

    /**
     * Translate a string property access to SQLite SQL.
     */
    fun translateStringProperty(property: String, target: String): String {
        return when (property) {
            "length" -> "length($target)"
            "isEmpty" -> "$target = ''"
            "isNotEmpty" -> "$target <> ''"
            else -> "$target"
        }
    }

    /**
     * Translate a DateTime method call to SQLite SQL.
     */
    fun translateDateTimeMethod(method: String, target: String, args: List<String>): String {
        return when (method) {
            "plusDays" -> "datetime($target, '+${args[0]} days')"
            "plusHours" -> "datetime($target, '+${args[0]} hours')"
            "plusMinutes" -> "datetime($target, '+${args[0]} minutes')"
            "plusMonths" -> "datetime($target, '+${args[0]} months')"
            "plusSeconds" -> "datetime($target, '+${args[0]} seconds')"
            "plusYears" -> "datetime($target, '+${args[0]} years')"
            "minusDays" -> "datetime($target, '-${args[0]} days')"
            "minusHours" -> "datetime($target, '-${args[0]} hours')"
            "minusMinutes" -> "datetime($target, '-${args[0]} minutes')"
            "minusMonths" -> "datetime($target, '-${args[0]} months')"
            "minusSeconds" -> "datetime($target, '-${args[0]} seconds')"
            "minusYears" -> "datetime($target, '-${args[0]} years')"
            else -> "$method($target)"
        }
    }

    /**
     * Translate a DateTime property access to SQLite SQL.
     */
    fun translateDateTimeProperty(property: String, target: String): String {
        return when (property) {
            "year" -> "CAST(strftime('%Y', $target) AS INTEGER)"
            "month" -> "CAST(strftime('%m', $target) AS INTEGER)"
            "day" -> "CAST(strftime('%d', $target) AS INTEGER)"
            "hour" -> "CAST(strftime('%H', $target) AS INTEGER)"
            "minute" -> "CAST(strftime('%M', $target) AS INTEGER)"
            "second" -> "CAST(strftime('%S', $target) AS INTEGER)"
            "dayOfWeek" -> "CAST(strftime('%w', $target) AS INTEGER)"
            "dayOfYear" -> "CAST(strftime('%j', $target) AS INTEGER)"
            "date" -> "date($target)"
            "time" -> "time($target)"
            "ticks" -> "CAST((julianday($target) - 2440587.5) * 864000000000 AS INTEGER)"
            else -> target
        }
    }

    /**
     * Translate a math function call to SQLite SQL.
     */
    fun translateMathFunction(function: String, args: List<String>): String {
        return when (function) {
            "abs" -> "abs(${args[0]})"
            "round" -> {
                if (args.size >= 2) "round(${args[0]}, ${args[1]})" else "round(${args[0]})"
            }
            "floor" -> "CAST(${args[0]} AS INTEGER) - CASE WHEN ${args[0]} < 0 AND CAST(${args[0]} AS INTEGER) <> ${args[0]} THEN 1 ELSE 0 END"
            "ceiling", "ceil" -> "CAST(${args[0]} AS INTEGER) + CASE WHEN ${args[0]} > 0 AND CAST(${args[0]} AS INTEGER) <> ${args[0]} THEN 1 ELSE 0 END"
            "exp" -> "exp(${args[0]})"
            "log" -> {
                if (args.size >= 2) "log(${args[1]}) / log(${args[0]})" else "log(${args[0]})"
            }
            "log10" -> "log10(${args[0]})"
            "sqrt" -> "sqrt(${args[0]})"
            "pow" -> "pow(${args[0]}, ${args[1]})"
            "cos" -> "cos(${args[0]})"
            "sin" -> "sin(${args[0]})"
            "tan" -> "tan(${args[0]})"
            "acos" -> "acos(${args[0]})"
            "asin" -> "asin(${args[0]})"
            "atan" -> "atan(${args[0]})"
            "atan2" -> "atan2(${args[0]}, ${args[1]})"
            "sign" -> "CASE WHEN ${args[0]} > 0 THEN 1 WHEN ${args[0]} < 0 THEN -1 ELSE 0 END"
            "random" -> "CAST(random() * 1000000000 AS INTEGER)"
            else -> "$function(${args.joinToString(", ")})"
        }
    }

    /**
     * Translate a type conversion to SQLite SQL.
     */
    fun translateConvert(method: String, target: String): String {
        return when (method) {
            "toBoolean" -> "CASE WHEN CAST($target AS INTEGER) = 0 THEN 0 ELSE 1 END"
            "toByte" -> "CAST($target AS INTEGER) & 0xFF"
            "toShort" -> "CAST($target AS SMALLINT)"
            "toInt", "toInt32" -> "CAST($target AS INTEGER)"
            "toLong", "toInt64" -> "CAST($target AS INTEGER)"
            "toFloat", "toSingle" -> "CAST($target AS FLOAT)"
            "toDouble" -> "CAST($target AS DOUBLE)"
            "toDecimal" -> "CAST($target AS DECIMAL)"
            "toString" -> "CAST($target AS TEXT)"
            "toChar" -> "substr(CAST($target AS TEXT), 1, 1)"
            else -> target
        }
    }

    /**
     * Build an IN clause with batching (max 500 elements per batch).
     */
    fun buildInClause(column: String, values: Collection<Any?>): String {
        if (values.isEmpty()) return "0"
        if (values.size <= 500) {
            val placeholders = values.joinToString(", ") { utils.formatSqlValue(it) }
            return "$column IN ($placeholders)"
        }
        // Batch into chunks of 500
        val chunks = values.chunked(500)
        val parts = chunks.map { chunk ->
            val placeholders = chunk.joinToString(", ") { utils.formatSqlValue(it) }
            "$column IN ($placeholders)"
        }
        return parts.joinToString(" OR ") { "($it)" }
    }
}
