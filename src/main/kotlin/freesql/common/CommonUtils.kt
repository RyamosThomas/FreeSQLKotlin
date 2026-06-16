package freesql.common

import freesql.core.FreeSqlCursor
import freesql.model.ColumnInfo
import freesql.model.TableInfo
import freesql.model.IndexInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Abstract SQL utility base class.
 * Port of FreeSql CommonUtils.
 */
abstract class CommonUtils {

    // -- Abstract methods (provider-specific) --

    /** Quote a SQL identifier (table/column name). E.g. `"name"` for SQLite. */
    abstract fun quoteSqlName(name: String): String

    /** Get the parameter prefix. E.g. `@` for SQLite. */
    abstract fun parameterPrefix(): String

    /** Get the SQL expression for ISNULL/IFNULL. */
    abstract fun isNull(sql: String, value: String): String

    /** Get the SQL operator for string concatenation. E.g. `||`. */
    abstract fun stringConcat(): String

    /** Get the SQL expression for current local time. */
    abstract fun now(): String

    /** Get the SQL expression for current UTC time. */
    abstract fun nowUtc(): String

    /** Format a value as an inline SQL literal (no parameter). */
    abstract fun getNoneParameterSqlValue(value: Any?): String

    /** Create a JDBC parameter for a value. Handles Guid->String, TimeSpan->seconds, etc. */
    abstract fun formatParameter(value: Any?): Any?

    // -- Shared utilities --

    /** Format a table or column name for SQL. Handles dot-separated names. */
    fun quoteName(vararg names: String): String {
        return names.joinToString(".") { name: String ->
            if (name.contains(".")) {
                name.split(".").joinToString(".") { part: String -> quoteSqlName(part) }
            } else if (name == "*") {
                name
            } else {
                quoteSqlName(name)
            }
        }
    }

    /** Format a parameter name with the prefix. */
    fun paramName(name: String): String = "${parameterPrefix()}$name"

    /** Escape single quotes in a string for SQL. */
    fun addslashes(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "''")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    /** Format a value as an inline SQL string literal. */
    fun formatSqlValue(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is Boolean -> if (value) "1" else "0"
            is String -> "'${addslashes(value)}'"
            is Char -> "'${addslashes(value.toString())}'"
            is Enum<*> -> value.ordinal.toString()
            is LocalDateTime -> "'${value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
            is Instant -> {
                val ldt = LocalDateTime.ofInstant(value, ZoneId.systemDefault())
                "'${ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
            }
            is java.util.Date -> {
                val ldt = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())
                "'${ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'"
            }
            is ByteArray -> "'${String(value, Charsets.UTF_8)}'"
            is UUID -> "'${value}'"
            is java.math.BigDecimal -> value.toPlainString()
            else -> value.toString()
        }
    }

    /**
     * Read a value from a FreeSqlCursor by column index, handling type conversion.
     */
    fun readValue(cursor: FreeSqlCursor, index: Int, targetType: KClass<*>): Any? {
        val value = cursor.getObject(index) ?: return null
        return when {
            targetType == Boolean::class -> cursor.getBoolean(index)
            targetType == Byte::class -> (value as? Number)?.toByte() ?: value
            targetType == Short::class -> (value as? Number)?.toShort() ?: value
            targetType == Int::class -> cursor.getInt(index)
            targetType == Long::class -> cursor.getLong(index)
            targetType == Float::class -> cursor.getFloat(index)
            targetType == Double::class -> cursor.getDouble(index)
            targetType == String::class -> cursor.getString(index)
            targetType == ByteArray::class -> value as? ByteArray
            targetType == UUID::class -> {
                val str = cursor.getString(index)
                if (str != null) UUID.fromString(str) else null
            }
            targetType == java.math.BigDecimal::class -> {
                when (value) {
                    is java.math.BigDecimal -> value
                    is Number -> java.math.BigDecimal.valueOf(value.toDouble())
                    else -> value
                }
            }
            targetType == LocalDateTime::class -> {
                when (value) {
                    is LocalDateTime -> value
                    is String -> LocalDateTime.parse(value)
                    else -> value
                }
            }
            targetType == Instant::class -> {
                when (value) {
                    is Instant -> value
                    is LocalDateTime -> value.atZone(ZoneId.systemDefault()).toInstant()
                    is String -> LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant()
                    else -> value
                }
            }
            else -> value
        }
    }

    /**
     * Get all property names and values from an entity instance.
     * Respects [Column.isIgnore] when a [TableInfo] is available.
     */
    fun getEntityPropertyValues(entity: Any, table: TableInfo? = null): List<Pair<String, Any?>> {
        val clazz = entity::class
        return clazz.memberProperties.map { prop: KProperty1<out Any, *> ->
            val col = table?.columnsByCs?.get(prop.name)
            if (col != null && !col.isIgnore) {
                prop.name to prop.call(entity)
            } else if (table == null) {
                prop.name to prop.call(entity)
            } else {
                null
            }
        }.filterNotNull()
    }
}
