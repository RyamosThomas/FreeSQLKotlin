package freesql.annotation

/**
 * Maps an entity class to a database table.
 * Port of FreeSql [Table] attribute.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Table(
    /** Database table name. Defaults to class name. */
    val name: String = "",
    /** Previous table name (for rename migrations). */
    val oldName: String = "",
    /** Disable automatic schema synchronization for this table. */
    val disableSyncStructure: Boolean = false,
    /** Sharding expression, e.g. "create_time=2022-1-1(1 month)". */
    val asTable: String = ""
)
