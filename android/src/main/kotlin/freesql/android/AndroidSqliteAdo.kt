package freesql.android

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import freesql.core.FreeSqlCursor
import freesql.core.IAdo
import freesql.core.IAop

class AndroidSqliteAdo(
    private val database: SQLiteDatabase
) : IAdo
{
    override var aop: IAop? = null

    /**
     * Convert named parameters (@name) to positional parameters (?).
     * Returns the rewritten SQL and the ordered raw parameter values.
     */
    private fun positionalSql(
        sql: String,
        parameters: Map<String, Any?>
    ): Pair<String, List<Any?>>
    {
        if (parameters.isEmpty()) return sql to emptyList()

        val orderedParams = mutableListOf<Any?>()
        val builder = StringBuilder()
        var insideString = false
        var charIndex = 0
        val paramNameBuffer = StringBuilder()
        var insideParam = false

        while (charIndex < sql.length)
        {
            val currentChar = sql[charIndex]
            if (currentChar == '\'' && !insideString)
            {
                insideString = true
                builder.append(currentChar)
            }
            else if (currentChar == '\'' && insideString)
            {
                if (charIndex + 1 < sql.length && sql[charIndex + 1] == '\'')
                {
                    builder.append("''")
                    charIndex += 2
                    continue
                }
                else
                {
                    insideString = false
                    builder.append(currentChar)
                }
            }
            else if (currentChar == '@' && !insideString)
            {
                insideParam = true
                paramNameBuffer.clear()
            }
            else if (insideParam && (currentChar.isLetterOrDigit() || currentChar == '_'))
            {
                paramNameBuffer.append(currentChar)
            }
            else if (insideParam)
            {
                // End of parameter name
                val paramName = paramNameBuffer.toString()
                builder.append('?')
                val value = parameters[paramName] ?: parameters["@${paramName}"]
                orderedParams.add(value)
                insideParam = false
                charIndex-- // Re-process current char
            }
            else
            {
                builder.append(currentChar)
            }
            charIndex++
        }

        // Handle param at end of string
        if (insideParam)
        {
            val paramName = paramNameBuffer.toString()
            builder.append('?')
            val value = parameters[paramName] ?: parameters["@${paramName}"]
            orderedParams.add(value)
        }

        return builder.toString() to orderedParams
    }

    /** Convert ordered raw values to string array for rawQuery. */
    private fun toStringArray(values: List<Any?>): Array<String?>
    {
        return values.map { it?.toString() }.toTypedArray()
    }

    private fun bindParameters(statement: SQLiteStatement, values: List<Any?>)
    {
        values.forEachIndexed { bindIndex, value ->
            val parameterIndex = bindIndex + 1
            when (value)
            {
                null -> statement.bindNull(parameterIndex)
                is String -> statement.bindString(parameterIndex, value)
                is Long -> statement.bindLong(parameterIndex, value)
                is Int -> statement.bindLong(parameterIndex, value.toLong())
                is Short -> statement.bindLong(parameterIndex, value.toLong())
                is Byte -> statement.bindLong(parameterIndex, value.toLong())
                is Double -> statement.bindDouble(parameterIndex, value)
                is Float -> statement.bindDouble(parameterIndex, value.toDouble())
                is Boolean -> statement.bindLong(parameterIndex, if (value) 1L else 0L)
                is ByteArray -> statement.bindBlob(parameterIndex, value)
                else -> statement.bindString(parameterIndex, value.toString())
            }
        }
    }

    override fun executeReader(
        sql: String,
        parameters: Map<String, Any?>,
        consumer: (FreeSqlCursor) -> Unit
    )
    {
        val (posSql, rawValues) = positionalSql(sql, parameters)
        val cursor = database.rawQuery(posSql, toStringArray(rawValues))
        cursor.use {
            consumer(AndroidSqliteCursor(it))
        }
    }

    override fun executeNonQuery(
        sql: String,
        parameters: Map<String, Any?>
    ): Int
    {
        val (posSql, rawValues) = positionalSql(sql, parameters)
        val statement = database.compileStatement(posSql)
        return statement.use {
            bindParameters(it, rawValues)
            it.executeUpdateDelete()
        }
    }

    override fun executeScalar(
        sql: String,
        parameters: Map<String, Any?>
    ): Any?
    {
        val (posSql, rawValues) = positionalSql(sql, parameters)
        val cursor = database.rawQuery(posSql, toStringArray(rawValues))
        cursor.use {
            if (it.moveToFirst())
            {
                return when (it.getType(0))
                {
                    android.database.Cursor.FIELD_TYPE_NULL -> null
                    android.database.Cursor.FIELD_TYPE_INTEGER -> it.getLong(0)
                    android.database.Cursor.FIELD_TYPE_FLOAT -> it.getDouble(0)
                    android.database.Cursor.FIELD_TYPE_STRING -> it.getString(0)
                    android.database.Cursor.FIELD_TYPE_BLOB -> it.getBlob(0)
                    else -> it.getString(0)
                }
            }
            return null
        }
    }

    override fun executeDataTable(
        sql: String,
        parameters: Map<String, Any?>
    ): List<Map<String, Any?>>
    {
        val results = mutableListOf<Map<String, Any?>>()
        executeReader(sql, parameters) { cursor ->
            val meta = cursor.metaData
            val colCount = meta.columnCount
            while (cursor.next())
            {
                val row = mutableMapOf<String, Any?>()
                for (columnIndex in 1 .. colCount)
                {
                    row[meta.getColumnLabel(columnIndex)] = cursor.getObject(columnIndex)
                }
                results.add(row)
            }
        }
        return results
    }

    override fun <T : Any> executeArray(
        sql: String,
        parameters: Map<String, Any?>,
        mapper: (FreeSqlCursor) -> T
    ): List<T>
    {
        val results = mutableListOf<T>()
        executeReader(sql, parameters) { cursor ->
            while (cursor.next())
            {
                results.add(mapper(cursor))
            }
        }
        return results
    }

    override fun transaction(action: () -> Unit)
    {
        database.beginTransaction()
        try
        {
            action()
            database.setTransactionSuccessful()
        }
        finally
        {
            database.endTransaction()
        }
    }

    override fun quoteName(name: String): String = "\"$name\""

    override fun parameterName(name: String): String = "@$name"

    override fun nowExpression(): String = "datetime('now','localtime')"

    override fun nowUtcExpression(): String = "datetime('now')"

    override fun isNullExpression(sql: String, value: String): String = "IFNULL($sql, $value)"

    override fun stringConcatOperator(): String = "||"
}
