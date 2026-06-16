package freesql.provider.sqlite

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Time-based table sharding support.
 * Port of FreeSql DateTimeAsTableImpl.
 *
 * Expression format: "columnName=startDate(period)"
 * Example: "create_time=2022-1-1(1 month)"
 * Generates table names like: log_202201, log_202202, etc.
 */
class SqliteAsTable(
    private val baseTableName: String,
    private val asTableExpression: String
) {
    /** The column name used for sharding (e.g. "create_time"). */
    val columnName: String

    /** The start date string (e.g. "2022-1-1"). */
    val startDate: String

    /** The period string (e.g. "1 month", "1 day", "1 year"). */
    val period: String

    /** The parsed period unit: DAY, MONTH, or YEAR. */
    private val periodUnit: PeriodUnit

    /** The parsed period count (e.g. 1, 2, 3). */
    private val periodCount: Int

    enum class PeriodUnit { DAY, MONTH, YEAR }

    init {
        // Parse expression: "columnName=startDate(period)"
        val regex = Regex("""(\w+)\s*=\s*(.+?)\((.+?)\)""")
        val match = regex.matchEntire(asTableExpression.trim())
            ?: throw IllegalArgumentException(
                "Invalid asTable expression: '$asTableExpression'. Expected format: 'columnName=startDate(period)' e.g. 'create_time=2022-1-1(1 month)'"
            )

        columnName = match.groupValues[1]
        startDate = match.groupValues[2].trim()
        period = match.groupValues[3].trim()

        // Parse period: "1 month", "2 day", "1 year"
        val periodParts = period.split(Regex("""\s+"""))
        require(periodParts.size >= 2) { "Invalid period format: '$period'. Expected: '<count> <unit>'" }

        periodCount = periodParts[0].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid period count: '${periodParts[0]}'")
        periodUnit = when (periodParts[1].lowercase()) {
            "day", "days", "d" -> PeriodUnit.DAY
            "month", "months", "m" -> PeriodUnit.MONTH
            "year", "years", "y" -> PeriodUnit.YEAR
            else -> throw IllegalArgumentException("Unknown period unit: '${periodParts[1]}'. Use day/month/year.")
        }
    }

    /**
     * Get the shard table name for a given column value (date string or temporal object).
     *
     * @param columnValue A date string (e.g. "2022-03-15"), LocalDate, LocalDateTime, or null.
     * @return The shard table name, e.g. "log_202203".
     */
    fun getTableName(columnValue: Any?): String {
        val date = parseToLocalDate(columnValue)
            ?: return baseTableName // fallback if can't parse
        val suffix = formatDateToSuffix(date)
        return "${baseTableName}_$suffix"
    }

    /**
     * Get all shard table names that match a date range.
     *
     * @param start Start date string (inclusive), or null for unbounded.
     * @param end End date string (inclusive), or null for unbounded.
     * @return List of shard table names in order.
     */
    fun getTableNames(start: String?, end: String?): List<String> {
        val startDateParsed = start?.let { parseToLocalDate(it) }
            ?: LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val endDateParsed = end?.let { parseToLocalDate(it) }
            ?: startDateParsed.plusYears(10) // far future if unbounded

        val results = mutableListOf<String>()
        var current = startDateParsed
        while (!current.isAfter(endDateParsed)) {
            results.add("${baseTableName}_${formatDateToSuffix(current)}")
            current = advancePeriod(current, 1)
        }
        return results
    }

    /**
     * Get all shard table names that a WHERE clause might touch.
     * This is a simplified heuristic — extracts date-like patterns from the WHERE clause.
     *
     * @param whereSql A SQL WHERE clause string.
     * @return List of shard table names.
     */
    fun getTableNamesFromWhere(whereSql: String): List<String> {
        // Try to extract date patterns from the WHERE SQL
        val datePattern = Regex("""(\d{4}[-/]\d{1,2}[-/]\d{1,2})""")
        val dates = datePattern.findAll(whereSql).map { it.groupValues[1] }.toList()

        return when {
            dates.size >= 2 -> getTableNames(dates[0], dates[1])
            dates.size == 1 -> getTableNames(dates[0], dates[0])
            else -> getTableNames(startDate, null)
        }
    }

    /**
     * Parse various date representations to LocalDate.
     */
    private fun parseToLocalDate(value: Any?): LocalDate? {
        return when (value) {
            is LocalDate -> value
            is LocalDateTime -> value.toLocalDate()
            is String -> {
                try {
                    // Try ISO format first
                    LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (_: Exception) {
                    try {
                        // Try yyyy/M/d format
                        LocalDate.parse(value.replace("/", "-"), DateTimeFormatter.ISO_LOCAL_DATE)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            else -> null
        }
    }

    /**
     * Format a date to the appropriate suffix based on period unit.
     */
    private fun formatDateToSuffix(date: LocalDate): String {
        return when (periodUnit) {
            PeriodUnit.YEAR -> date.format(DateTimeFormatter.ofPattern("yyyy"))
            PeriodUnit.MONTH -> date.format(DateTimeFormatter.ofPattern("yyyyMM"))
            PeriodUnit.DAY -> date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        }
    }

    /**
     * Advance a date by [count] periods of the configured unit.
     */
    private fun advancePeriod(date: LocalDate, count: Int): LocalDate {
        val total = count * periodCount
        return when (periodUnit) {
            PeriodUnit.DAY -> date.plusDays(total.toLong())
            PeriodUnit.MONTH -> date.plusMonths(total.toLong())
            PeriodUnit.YEAR -> date.plusYears(total.toLong())
        }
    }
}
