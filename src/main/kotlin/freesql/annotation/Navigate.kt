package freesql.annotation

/**
 * Defines a navigation relationship between entities.
 * Port of FreeSql [Navigate] attribute.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Navigate(
    /** Foreign key binding. Comma-separated for composite. */
    val bind: String = "",
    /** Temporary primary key for many-to-many. */
    val tempPrimary: String = "",
    /** Middle entity class name for many-to-many. */
    val manyToMany: String = ""
)

/**
 * Relationship types for navigation properties.
 */
enum class RefType {
    OneToOne,
    ManyToOne,
    OneToMany,
    ManyToMany
}
