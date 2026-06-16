package freesql.provider.sqlite

import freesql.common.CommonUtils
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * SQLite-specific SQL utilities.
 * Port of FreeSql SqliteUtils.
 */
class SqliteUtils : CommonUtils() {

    override fun quoteSqlName(name: String): String {
        return "\"${name.replace("\"", "\"\"")}\""
    }

    override fun parameterPrefix(): String = "@"

    override fun isNull(sql: String, value: String): String {
        return "ifnull($sql, $value)"
    }

    override fun stringConcat(): String = "||"

    override fun now(): String = "datetime(current_timestamp,'localtime')"

    override fun nowUtc(): String = "current_timestamp"

    override fun getNoneParameterSqlValue(value: Any?): String {
        return formatSqlValue(value)
    }

    override fun formatParameter(value: Any?): Any? {
        return when (value) {
            null -> null
            is UUID -> value.toString()
            is java.time.Duration -> value.toMillis() / 1000.0
            is LocalDateTime -> value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            is Instant -> {
                val ldt = LocalDateTime.ofInstant(value, ZoneId.systemDefault())
                ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
            is java.util.Date -> {
                val ldt = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault())
                ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
            is Boolean -> if (value) 1 else 0
            is Enum<*> -> value.ordinal.toLong()
            else -> value
        }
    }
}
