package freesql.android

import android.database.Cursor
import freesql.core.FreeSqlCursor
import freesql.core.FreeSqlCursorMetaData

/**
 * Adapts Android's 0-based [Cursor] to FreeSqlCursor's 1-based (JDBC-convention) API.
 */
class AndroidSqliteCursor(private val cursor: Cursor) : FreeSqlCursor
{
    override val metaData: FreeSqlCursorMetaData = AndroidCursorMetaData(cursor)

    override fun next(): Boolean = cursor.moveToNext()

    override fun close() = cursor.close()

    override fun getObject(columnIndex: Int): Any?
    {
        val androidIndex = columnIndex - 1
        return when (cursor.getType(androidIndex))
        {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(androidIndex)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(androidIndex)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(androidIndex)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(androidIndex)
            else -> cursor.getString(androidIndex)
        }
    }

    override fun getString(columnIndex: Int): String?
    {
        val androidIndex = columnIndex - 1
        return if (cursor.isNull(androidIndex)) null else cursor.getString(androidIndex)
    }

    override fun getInt(columnIndex: Int): Int = cursor.getInt(columnIndex - 1)

    override fun getLong(columnIndex: Int): Long = cursor.getLong(columnIndex - 1)

    override fun getDouble(columnIndex: Int): Double = cursor.getDouble(columnIndex - 1)

    override fun getFloat(columnIndex: Int): Float = cursor.getFloat(columnIndex - 1)

    override fun getBoolean(columnIndex: Int): Boolean = cursor.getInt(columnIndex - 1) != 0

    override fun isNull(columnIndex: Int): Boolean = cursor.isNull(columnIndex - 1)

    override fun getColumnIndex(columnName: String): Int
    {
        val index = cursor.getColumnIndex(columnName)
        if (index < 0)
        {
            // Try with table prefix stripped (e.g., "users.name" -> "name")
            val dotIndex = columnName.lastIndexOf('.')
            if (dotIndex >= 0)
            {
                val strippedIndex = cursor.getColumnIndex(columnName.substring(dotIndex + 1))
                // Android returns 0-based; convert to 1-based for FreeSqlCursor convention
                return if (strippedIndex >= 0) strippedIndex + 1 else strippedIndex
            }
        }
        // Android returns 0-based; convert to 1-based for FreeSqlCursor convention
        return if (index >= 0) index + 1 else index
    }
}

private class AndroidCursorMetaData(
    private val cursor: Cursor
) : FreeSqlCursorMetaData
{
    override val columnCount: Int get() = cursor.columnCount

    override fun getColumnLabel(columnIndex: Int): String =
        cursor.getColumnName(columnIndex - 1)

    override fun getColumnName(columnIndex: Int): String =
        cursor.getColumnName(columnIndex - 1)
}
