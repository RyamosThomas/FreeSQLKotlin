package freesql.core

import java.sql.ResultSet

/**
 * Low-level database access interface.
 * Port of FreeSql IAdo.
 */
interface IAdo {

    /** Execute a query and iterate over the ResultSet. */
    fun executeReader(
        sql: String,
        parameters: Map<String, Any?> = emptyMap(),
        consumer: (ResultSet) -> Unit
    )

    /** Execute a non-query and return affected rows. */
    fun executeNonQuery(
        sql: String,
        parameters: Map<String, Any?> = emptyMap()
    ): Int

    /** Execute a scalar query and return the first column of the first row. */
    fun executeScalar(
        sql: String,
        parameters: Map<String, Any?> = emptyMap()
    ): Any?

    /** Execute a query and return results as a list of maps. */
    fun executeDataTable(
        sql: String,
        parameters: Map<String, Any?> = emptyMap()
    ): List<Map<String, Any?>>

    /** Execute a query and return typed results. */
    fun <T : Any> executeArray(
        sql: String,
        parameters: Map<String, Any?> = emptyMap(),
        mapper: (ResultSet) -> T
    ): List<T>

    /** Run [action] inside a database transaction. */
    fun transaction(action: () -> Unit)

    /** Escape and quote a SQL identifier (table/column name). */
    fun quoteName(name: String): String

    /** Create a named parameter (e.g. "@name"). */
    fun parameterName(name: String): String

    /** Get the SQL expression for the current time. */
    fun nowExpression(): String

    /** Get the SQL expression for the current UTC time. */
    fun nowUtcExpression(): String

    /** Get the SQL expression for ISNULL/IFNULL. */
    fun isNullExpression(sql: String, value: String): String

    /** Get the SQL operator for string concatenation. */
    fun stringConcatOperator(): String
}
