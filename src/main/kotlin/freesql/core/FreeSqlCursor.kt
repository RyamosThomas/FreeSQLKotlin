package freesql.core

/**
 * Platform-agnostic cursor metadata interface.
 * Wraps java.sql.ResultSetMetaData or android.database.Cursor column info.
 */
interface FreeSqlCursorMetaData {
    val columnCount: Int
    fun getColumnLabel(columnIndex: Int): String
    fun getColumnName(columnIndex: Int): String
}

/**
 * Platform-agnostic database cursor interface.
 * Wraps java.sql.ResultSet (JVM) or android.database.Cursor (Android).
 */
interface FreeSqlCursor {
    fun next(): Boolean
    fun close()
    fun getObject(columnIndex: Int): Any?
    fun getString(columnIndex: Int): String?
    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getDouble(columnIndex: Int): Double
    fun getFloat(columnIndex: Int): Float
    fun getBoolean(columnIndex: Int): Boolean
    fun isNull(columnIndex: Int): Boolean
    fun getColumnIndex(columnName: String): Int
    val metaData: FreeSqlCursorMetaData
}
