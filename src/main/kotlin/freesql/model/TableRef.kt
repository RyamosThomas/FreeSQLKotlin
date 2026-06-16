package freesql.model

import freesql.annotation.RefType
import kotlin.reflect.KClass

/**
 * Metadata for a navigation relationship between entities.
 * Port of FreeSql TableRef.
 */
class TableRef(
    /** Relationship type. */
    val refType: RefType,
    /** The related entity class. */
    val refEntityType: KClass<*>,
    /** Middle entity class (for ManyToMany). */
    val refMiddleEntityType: KClass<*>? = null,
    /** Foreign key columns on this side. */
    val columns: List<ColumnInfo> = emptyList(),
    /** Foreign key columns on the middle entity (ManyToMany). */
    val middleColumns: List<ColumnInfo> = emptyList(),
    /** Primary/referenced columns on the related entity. */
    val refColumns: List<ColumnInfo> = emptyList()
)
