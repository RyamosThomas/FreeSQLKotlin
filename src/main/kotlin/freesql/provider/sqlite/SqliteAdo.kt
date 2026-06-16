package freesql.provider.sqlite

import freesql.core.IAdo
import freesql.core.IAop
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types

/**
 * SQLite ADO layer using JDBC with connection pooling.
 * Port of FreeSql SqliteAdo.
 *
 * For :memory: databases, the pool uses a single shared connection.
 * For file databases, connections are pooled with configurable size.
 * All operations acquire/release from the pool properly.
 * Transactions hold a single connection for the duration.
 */
class SqliteAdo(
    private val connectionString: String,
    private val utils: SqliteUtils,
    private val pool: SqliteConnectionPool
) : IAdo {

    /** Set by the provider after construction. */
    var aop: IAop? = null

    init {
        Class.forName("org.sqlite.JDBC")
    }

    /**
     * Replace named parameters (@name) with ? placeholders and return ordered values.
     * Skips @ signs inside single-quoted string literals.
     */
    private fun positionalSql(
        sql: String,
        parameters: Map<String, Any?>
    ): Pair<String, List<Any?>> {
        if (parameters.isEmpty()) return sql to emptyList()

        data class ParamMatch(val paramName: String, val startIndex: Int, val endIndex: Int)

        val matches = mutableListOf<ParamMatch>()
        var inString = false
        var i = 0
        while (i < sql.length) {
            val ch = sql[i]
            if (ch == '\'' && !inString) {
                inString = true
                i++
            } else if (ch == '\'' && inString) {
                // Check for escaped quote ''
                if (i + 1 < sql.length && sql[i + 1] == '\'') {
                    i += 2
                } else {
                    inString = false
                    i++
                }
            } else if (ch == '@' && !inString) {
                // Found a potential parameter
                val paramStart = i
                i++
                val nameBuilder = StringBuilder()
                while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) {
                    nameBuilder.append(sql[i])
                    i++
                }
                if (nameBuilder.isNotEmpty()) {
                    matches.add(ParamMatch(nameBuilder.toString(), paramStart, i))
                }
            } else {
                i++
            }
        }

        if (matches.isEmpty()) return sql to emptyList()

        // Build result: replace @param with ? in order
        val orderedParams = mutableListOf<Any?>()
        val sb = StringBuilder()
        var lastEnd = 0
        for (match in matches) {
            sb.append(sql, lastEnd, match.startIndex)
            sb.append('?')
            val value = parameters[match.paramName] ?: parameters["@${match.paramName}"]
            orderedParams.add(utils.formatParameter(value))
            lastEnd = match.endIndex
        }
        sb.append(sql.substring(lastEnd))

        return sb.toString() to orderedParams
    }

    override fun executeReader(
        sql: String,
        parameters: Map<String, Any?>,
        consumer: (ResultSet) -> Unit
    ) {
        val (posSql, values) = positionalSql(sql, parameters)
        val pooled = pool.acquire()
        try {
            val stmt = pooled.connection.prepareStatement(posSql)
            stmt.use { s ->
                values.forEachIndexed { i, v ->
                    if (v == null) s.setNull(i + 1, Types.NULL)
                    else s.setObject(i + 1, v)
                }
                val rs = s.executeQuery()
                rs.use { consumer(it) }
            }
        } finally {
            pool.release(pooled)
        }
    }

    override fun executeNonQuery(
        sql: String,
        parameters: Map<String, Any?>
    ): Int {
        val (posSql, values) = positionalSql(sql, parameters)
        val pooled = pool.acquire()
        try {
            val stmt = pooled.connection.prepareStatement(posSql)
            stmt.use { s ->
                values.forEachIndexed { i, v ->
                    if (v == null) s.setNull(i + 1, Types.NULL)
                    else s.setObject(i + 1, v)
                }
                return s.executeUpdate()
            }
        } finally {
            pool.release(pooled)
        }
    }

    override fun executeScalar(
        sql: String,
        parameters: Map<String, Any?>
    ): Any? {
        val (posSql, values) = positionalSql(sql, parameters)
        val pooled = pool.acquire()
        try {
            val stmt = pooled.connection.prepareStatement(posSql)
            stmt.use { s ->
                values.forEachIndexed { i, v ->
                    if (v == null) s.setNull(i + 1, Types.NULL)
                    else s.setObject(i + 1, v)
                }
                val rs = s.executeQuery()
                rs.use {
                    return if (it.next()) it.getObject(1) else null
                }
            }
        } finally {
            pool.release(pooled)
        }
    }

    override fun executeDataTable(
        sql: String,
        parameters: Map<String, Any?>
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        executeReader(sql, parameters) { rs ->
            val meta = rs.metaData
            val colCount = meta.columnCount
            while (rs.next()) {
                val row = mutableMapOf<String, Any?>()
                for (i in 1..colCount) {
                    row[meta.getColumnLabel(i)] = rs.getObject(i)
                }
                results.add(row)
            }
        }
        return results
    }

    override fun <T : Any> executeArray(
        sql: String,
        parameters: Map<String, Any?>,
        mapper: (ResultSet) -> T
    ): List<T> {
        val results = mutableListOf<T>()
        executeReader(sql, parameters) { rs ->
            while (rs.next()) {
                results.add(mapper(rs))
            }
        }
        return results
    }

    /**
     * Execute action inside a database transaction.
     * Acquires a single connection, disables autocommit, runs the action,
     * then commits or rollbacks before releasing.
     */
    override fun transaction(action: () -> Unit) {
        val pooled = pool.acquire()
        try {
            val conn = pooled.connection
            val originalAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                action()
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = originalAutoCommit
            }
        } finally {
            pool.release(pooled)
        }
    }

    override fun quoteName(name: String): String = utils.quoteSqlName(name)

    override fun parameterName(name: String): String = utils.paramName(name)

    override fun nowExpression(): String = utils.now()

    override fun nowUtcExpression(): String = utils.nowUtc()

    override fun isNullExpression(sql: String, value: String): String = utils.isNull(sql, value)

    override fun stringConcatOperator(): String = utils.stringConcat()
}
