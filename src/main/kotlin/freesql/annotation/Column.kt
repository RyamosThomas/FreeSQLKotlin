package freesql.annotation

/**
 * Maps a property to a database column.
 * Port of FreeSql [Column] attribute.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Column(
    /** Database column name. Defaults to property name. */
    val name: String = "",
    /** Previous column name (for rename migrations). */
    val oldName: String = "",
    /** Explicit database type override, e.g. "varchar(500)". */
    val dbType: String = "",
    /** Mark as primary key. */
    val isPrimary: Boolean = false,
    /** Mark as auto-increment identity. */
    val isIdentity: Boolean = false,
    /** Explicit nullable override. */
    val isNullable: Boolean = false,
    /** Exclude from ORM mapping. */
    val isIgnore: Boolean = false,
    /** Optimistic concurrency version column. */
    val isVersion: Boolean = false,
    /** String max length. */
    val stringLength: Int = 0,
    /** Numeric precision. */
    val precision: Int = 0,
    /** Numeric scale. */
    val scale: Int = 0,
    /** Can this column appear in INSERT. */
    val canInsert: Boolean = true,
    /** Can this column appear in UPDATE. */
    val canUpdate: Boolean = true,
    /** SQL expression for default insert value. */
    val insertValueSql: String = "",
    /** Server-side time behavior: INSERT, UPDATE, or BOTH. */
    val serverTime: ServerTimeType = ServerTimeType.NONE,
    /** Column position/order in DDL (lower = earlier). Default 0 = declaration order. */
    val position: Int = 0,
    /** Custom type mapping: map the database type to a different Kotlin type. */
    val mapType: String = "",
    /** Create a unique index on this column. Index name defaults to "IX_{table}_{column}". */
    val uniqueIndex: Boolean = false
)

enum class ServerTimeType { NONE, INSERT, UPDATE, BOTH }
