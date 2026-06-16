package freesql.annotation

/**
 * Defines a database index on a table.
 * Port of FreeSql [Index] attribute.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class Index(
    /** Index name. */
    val name: String = "",
    /** Comma-separated field names. */
    val fields: String = "",
    /** Unique index. */
    val isUnique: Boolean = false
)
