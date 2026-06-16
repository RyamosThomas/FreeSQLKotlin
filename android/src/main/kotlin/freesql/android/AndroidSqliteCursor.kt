package freesql.android

import android.database.Cursor
import freesql.core.FreeSqlCursor
import freesql.core.FreeSqlCursorMetaData

class AndroidSqliteCursor(private val cursor: Cursor) : FreeSqlCursor
{
    override val metaData: FreeSqlCursorMetaData = AndroidCursorMetaData(cursor)

    override fun next(): Boolean = cursor.moveToNext()

    override fun close() = cursor.close()

    override fun getObject(columnIndex: Int): Any?
    {
        // Android Cursor doesn't have getObject; need to check type
        // Use getType() API (available since API 11, min is 26)
        return when (cursor.getType(columnIndex))
        {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(columnIndex)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(columnIndex)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(columnIndex)
            else -> cursor.getString(columnIndex)
        }
    }

    override fun getString(columnIndex: Int): String? =
        if (cursor.isNull(columnIndex)) null else cursor.getString(columnIndex)

    override fun getInt(columnIndex: Int): Int = cursor.getInt(columnIndex)

    override fun getLong(columnIndex: Int): Long = cursor.getLong(columnIndex)

    override fun getDouble(columnIndex: Int): Double = cursor.getDouble(columnIndex)

    override fun getFloat(columnIndex: Int): Float = cursor.getFloat(columnIndex)

    override fun getBoolean(columnIndex: Int): Boolean = cursor.getInt(columnIndex) != 0

    override fun isNull(columnIndex: Int): Boolean = cursor.isNull(columnIndex)

    override fun getColumnIndex(columnName: String): Int
    {
        val index = cursor.getColumnIndex(columnName)
        if (index < 0)
        {
            // Try with table prefix stripped (e.g., "users.name" -> "name")
            val dotIndex = columnName.lastIndexOf('.')
            if (dotIndex >= 0)
            {
                return cursor.getColumnIndex(columnName.substring(dotIndex + 1))
            }
        }
        return index
    }
}

private class AndroidCursorMetaData(
    private val cursor: Cursor
) : FreeSqlCursorMetaData
{
    override val columnCount: Int get() = cursor.columnCount
    override fun getColumnLabel(columnIndex: Int): String = cursor.getColumnName(columnIndex)
    override fun getColumnName(columnIndex: Int): String = cursor.getColumnName(columnIndex)
}
