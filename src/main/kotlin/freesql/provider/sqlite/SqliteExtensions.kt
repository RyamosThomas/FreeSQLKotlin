package freesql.provider.sqlite

import freesql.core.IFreeSql
import freesql.core.insert
import kotlin.reflect.KClass

/**
 * SQLite-specific extension functions.
 * Port of FreeSql SqliteExtensions.
 */
object SqliteExtensions {

    /**
     * Format a string value for safe inclusion in SQLite SQL.
     * Provides SQL injection protection via proper escaping.
     *
     * Escaping rules:
     * - Backslashes are doubled
     * - Single quotes are doubled (SQLite escape for single quote)
     * - Newlines/carriage returns are escaped
     * - NUL characters are stripped (SQLite doesn't support NUL in strings)
     * - Control characters (0x00-0x1F except \n \r \t) are stripped
     */
    fun formatSqlite(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "''")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\u0000", "") // SQLite doesn't support NUL in strings
            .let { stripControlChars(it) }
    }

    /**
     * Strip non-printable control characters (0x00-0x1F) except common whitespace.
     */
    private fun stripControlChars(value: String): String {
        return value.filter { ch ->
            val code = ch.code
            code > 0x1F || ch == '\t' || ch == '\n' || ch == '\r'
        }
    }

    /**
     * Format a value as an inline SQL literal for SQLite.
     * Handles all common types safely with proper escaping.
     *
     * SQL injection protection: String values are always escaped via formatSqlite().
     * Binary data is hex-encoded. Dates use ISO format.
     */
    fun formatSqliteValue(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is Boolean -> if (value) "1" else "0"
            is String -> "'${formatSqlite(value)}'"
            is Char -> "'${formatSqlite(value.toString())}'"
            is Byte -> value.toString()
            is Short -> value.toString()
            is Int -> value.toString()
            is Long -> value.toString()
            is Float -> value.toString()
            is Double -> value.toString()
            is ByteArray -> "X'${value.joinToString("") { "%02X".format(it) }}'"
            is java.util.UUID -> "'${formatSqlite(value.toString())}'"
            is java.time.LocalDateTime -> "'${value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
            is java.time.Instant -> {
                val ldt = java.time.LocalDateTime.ofInstant(value, java.time.ZoneId.systemDefault())
                "'${ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
            }
            is java.util.Date -> {
                val ldt = java.time.LocalDateTime.ofInstant(value.toInstant(), java.time.ZoneId.systemDefault())
                "'${ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
            }
            is java.math.BigDecimal -> value.toPlainString()
            is Enum<*> -> value.ordinal.toString()
            else -> "'${formatSqlite(value.toString())}'"
        }
    }

    /**
     * Validate and sanitize a table/column name to prevent SQL injection.
     * Only allows alphanumeric characters, underscores, and dots.
     * Returns the name quoted with SQLite identifier quotes.
     */
    fun sanitizeIdentifier(name: String): String {
        require(name.matches(Regex("""^[a-zA-Z_][a-zA-Z0-9_]*$"""))) {
            "Invalid SQL identifier: '$name'. Identifiers must start with a letter or underscore " +
            "and contain only alphanumeric characters and underscores."
        }
        return "\"${name.replace("\"", "\"\"")}\""
    }

    /**
     * Detect potential SQL injection patterns in a raw SQL string.
     * Returns true if suspicious patterns are found.
     * Use for logging/warning, not as a security boundary.
     */
    fun detectSuspiciousSql(sql: String): Boolean {
        val suspiciousPatterns = listOf(
            """(?i)(;|--|/\*|\*/)""",       // Statement terminators, comments
            """(?i)\b(UNION\s+ALL|UNION)\b""", // UNION injection
            """(?i)\b(DROP|ALTER|CREATE|TRUNCATE)\b""", // DDL
            """(?i)\b(EXEC|EXECUTE|XP_)\b""" // Execution commands
        )
        return suspiciousPatterns.any { sql.contains(Regex(it)) }
    }
}

/**
 * Execute a bulk insert for the given entities using row-by-row parameterized commands.
 * Splits into batches to respect SQLite's parameter limits.
 *
 * @param entities The entities to insert.
 * @param batchSize Maximum number of rows per INSERT statement (default 5000).
 */
inline fun <reified T : Any> IFreeSql.bulkInsert(entities: List<T>, batchSize: Int = 5000) {
    if (entities.isEmpty()) return

    val insertOp = this.insert(T::class)
    entities.forEach { entity ->
        insertOp.setSource(entity)
    }
    insertOp.batchOptions(batchSize).executeAffrows()
}

/**
 * Execute a bulk insert using raw SQL for maximum performance.
 * Builds parameterized INSERT statements in batches.
 *
 * @param tableName The target table name.
 * @param columns Column names to insert.
 * @param rows List of rows, each row is a list of values matching columns order.
 * @param batchSize Maximum rows per INSERT statement.
 */
fun IFreeSql.bulkInsertRaw(
    tableName: String,
    columns: List<String>,
    rows: List<List<Any?>>,
    batchSize: Int = 5000
) {
    if (rows.isEmpty() || columns.isEmpty()) return

    val quotedTable = ado.quoteName(tableName)
    val quotedCols = columns.joinToString(", ") { ado.quoteName(it) }
    val paramCount = columns.size

    // Calculate effective batch size (SQLite max 999 params per statement)
    val effectiveBatchSize = minOf(batchSize, 999 / paramCount)

    rows.chunked(effectiveBatchSize).forEach { batch ->
        val valuePlaceholders = batch.map { row ->
            val placeholders = row.map { "?" }
            "(${placeholders.joinToString(", ")})"
        }

        val sql = "INSERT INTO $quotedTable ($quotedCols) VALUES ${valuePlaceholders.joinToString(", ")}"

        // Flatten all parameter values
        val params = mutableMapOf<String, Any?>()
        var paramIdx = 0
        batch.forEach { row ->
            row.forEach { value ->
                params["p${paramIdx}"] = value
                paramIdx++
            }
        }

        // Build positional parameters (replace ? with @p0, @p1, etc.)
        var positionalSql = sql
        var idx = 0
        batch.forEach { row ->
            row.forEach { _ ->
                positionalSql = positionalSql.replaceFirst("?", "@p${idx}")
                idx++
            }
        }

        ado.executeNonQuery(positionalSql, params)
    }
}

/**
 * Execute a bulk insert using row-by-row parameterized INSERT statements.
 * This is the safest bulk insert method — one parameterized INSERT per row,
 * wrapped in a transaction for performance.
 *
 * @param tableName The target table name.
 * @param columns Column names to insert.
 * @param rows List of rows, each row is a list of values matching columns order.
 * @param useTransaction Whether to wrap in a transaction (default true, much faster).
 */
fun IFreeSql.executeSqliteBulkInsert(
    tableName: String,
    columns: List<String>,
    rows: List<List<Any?>>,
    useTransaction: Boolean = true
) {
    if (rows.isEmpty() || columns.isEmpty()) return

    val quotedTable = ado.quoteName(tableName)
    val quotedCols = columns.joinToString(", ") { ado.quoteName(it) }
    val placeholders = columns.joinToString(", ") { "?" }

    val sql = "INSERT INTO $quotedTable ($quotedCols) VALUES ($placeholders)"

    val action = {
        rows.forEach { row ->
            val params = mutableMapOf<String, Any?>()
            row.forEachIndexed { i, value ->
                params["p$i"] = value
            }

            // Replace ? with positional params
            var positionalSql = sql
            var idx = 0
            columns.forEach { _ ->
                positionalSql = positionalSql.replaceFirst("?", "@p${idx}")
                idx++
            }

            ado.executeNonQuery(positionalSql, params)
        }
    }

    if (useTransaction) {
        ado.transaction(action)
    } else {
        action()
    }
}

/**
 * Execute a bulk update using parameterized statements.
 *
 * @param tableName The target table name.
 * @param setColumns Column names to update.
 * @param whereColumns Column names used in WHERE clause.
 * @param rows List of rows, each row maps column name to value.
 * @param batchSize Maximum rows per statement.
 */
fun IFreeSql.bulkUpdate(
    tableName: String,
    setColumns: List<String>,
    whereColumns: List<String>,
    rows: List<Map<String, Any?>>,
    batchSize: Int = 5000
) {
    if (rows.isEmpty()) return

    // For single-row updates, just execute them individually in batches
    rows.chunked(batchSize).forEach { batch ->
        batch.forEach { row ->
            val setClauses = setColumns.mapIndexed { i, col ->
                "${ado.quoteName(col)} = @set$i"
            }.joinToString(", ")

            val whereClauses = whereColumns.mapIndexed { i, col ->
                "${ado.quoteName(col)} = @where$i"
            }.joinToString(" AND ")

            val sql = "UPDATE ${ado.quoteName(tableName)} SET $setClauses WHERE $whereClauses"

            val params = mutableMapOf<String, Any?>()
            setColumns.forEachIndexed { i, col ->
                params["set$i"] = row[col]
            }
            whereColumns.forEachIndexed { i, col ->
                params["where$i"] = row[col]
            }

            ado.executeNonQuery(sql, params)
        }
    }
}

/**
 * Execute a bulk delete using parameterized statements.
 *
 * @param tableName The target table name.
 * @param whereColumns Column names used in WHERE clause.
 * @param rows List of rows, each row maps column name to value.
 * @param batchSize Maximum rows per statement.
 */
fun IFreeSql.bulkDelete(
    tableName: String,
    whereColumns: List<String>,
    rows: List<Map<String, Any?>>,
    batchSize: Int = 5000
) {
    if (rows.isEmpty() || whereColumns.isEmpty()) return

    if (whereColumns.size == 1) {
        // Optimized: use IN clause for single-column PK
        val col = whereColumns[0]
        val quotedCol = ado.quoteName(col)

        rows.chunked(batchSize).forEach { batch ->
            val params = mutableMapOf<String, Any?>()
            val placeholders = batch.mapIndexed { i, row ->
                params["p$i"] = row[col]
                "@p$i"
            }

            val sql = "DELETE FROM ${ado.quoteName(tableName)} WHERE $quotedCol IN (${placeholders.joinToString(", ")})"
            ado.executeNonQuery(sql, params)
        }
    } else {
        // Multi-column PK: use OR of AND conditions
        rows.chunked(batchSize).forEach { batch ->
            val conditions = batch.mapIndexed { rowIdx, _ ->
                val colConditions = whereColumns.mapIndexed { colIdx, col ->
                    val paramName = "p${rowIdx}_$colIdx"
                    "${ado.quoteName(col)} = @$paramName"
                }
                "(${colConditions.joinToString(" AND ")})"
            }

            val sql = "DELETE FROM ${ado.quoteName(tableName)} WHERE ${conditions.joinToString(" OR ")}"

            val params = mutableMapOf<String, Any?>()
            batch.forEachIndexed { rowIdx, row ->
                whereColumns.forEachIndexed { colIdx, col ->
                    params["p${rowIdx}_$colIdx"] = row[col]
                }
            }

            ado.executeNonQuery(sql, params)
        }
    }
}
