package freesql.provider.sqlite

import freesql.core.FreeSqlCursor
import freesql.core.FreeSqlCursorMetaData
import java.sql.ResultSet

/**
 * JDBC implementation of [FreeSqlCursorMetaData] that wraps a [java.sql.ResultSetMetaData].
 */
private class JdbcCursorMetaData(
    private val resultSetMetaData: java.sql.ResultSetMetaData
) : FreeSqlCursorMetaData {
    override val columnCount: Int get() = resultSetMetaData.columnCount
    override fun getColumnLabel(columnIndex: Int): String = resultSetMetaData.getColumnLabel(columnIndex)
    override fun getColumnName(columnIndex: Int): String = resultSetMetaData.getColumnName(columnIndex)
}

/**
 * JDBC implementation of [FreeSqlCursor] that wraps a [java.sql.ResultSet].
 * Used by JVM-based providers (SQLite, PostgreSQL, MySQL, etc.).
 */
class JdbcFreeSqlCursor(
    private val resultSet: ResultSet
) : FreeSqlCursor {

    override val metaData: FreeSqlCursorMetaData by lazy {
        JdbcCursorMetaData(resultSet.metaData)
    }

    override fun next(): Boolean = resultSet.next()

    override fun close() = resultSet.close()

    override fun getObject(columnIndex: Int): Any? = resultSet.getObject(columnIndex)

    override fun getString(columnIndex: Int): String? = resultSet.getString(columnIndex)

    override fun getInt(columnIndex: Int): Int = resultSet.getInt(columnIndex)

    override fun getLong(columnIndex: Int): Long = resultSet.getLong(columnIndex)

    override fun getDouble(columnIndex: Int): Double = resultSet.getDouble(columnIndex)

    override fun getFloat(columnIndex: Int): Float = resultSet.getFloat(columnIndex)

    override fun getBoolean(columnIndex: Int): Boolean = resultSet.getBoolean(columnIndex)

    override fun isNull(columnIndex: Int): Boolean {
        resultSet.getObject(columnIndex)
        return resultSet.wasNull()
    }

    override fun getColumnIndex(columnName: String): Int {
        return resultSet.findColumn(columnName)
    }
}
