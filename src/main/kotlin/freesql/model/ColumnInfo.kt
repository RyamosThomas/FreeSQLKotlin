package freesql.model

import freesql.annotation.ServerTimeType
import kotlin.reflect.KClass

class ColumnAttribute(
    val dbName: String = "",
    val dbOldName: String = "",
    val dbType: String = "",
    val isPrimary: Boolean = false,
    val isIdentity: Boolean = false,
    val isNullable: Boolean = false,
    val isIgnore: Boolean = false,
    val isVersion: Boolean = false,
    val stringLength: Int = 0,
    val precision: Int = 0,
    val scale: Int = 0,
    val canInsert: Boolean = true,
    val canUpdate: Boolean = true,
    val insertValueSql: String = "",
    val serverTime: ServerTimeType = ServerTimeType.NONE,
    val position: Int = 0,
    val mapType: String = "",
    val uniqueIndex: Boolean = false
)

/**
 * Runtime metadata for a mapped column.
 * Port of FreeSql ColumnInfo.
 */
class ColumnInfo(
    val table: TableInfo,
    val csName: String,
    val csType: KClass<*>,
    val isNullableCsType: Boolean = false,
    val attribute: ColumnAttribute
) {
    /** Database column name. */
    val dbName: String get() = attribute.dbName

    /** Is primary key. */
    val isPrimary: Boolean get() = attribute.isPrimary

    /** Is auto-increment identity. */
    val isIdentity: Boolean get() = attribute.isIdentity

    /** Is nullable. */
    val isNullable: Boolean get() = attribute.isNullable

    /** Is ignored (not mapped). */
    val isIgnore: Boolean get() = attribute.isIgnore

    /** Is version column for optimistic concurrency. */
    val isVersion: Boolean get() = attribute.isVersion

    /** Can appear in INSERT. */
    val canInsert: Boolean get() = attribute.canInsert

    /** Can appear in UPDATE. */
    val canUpdate: Boolean get() = attribute.canUpdate

    /** Insert value SQL expression. */
    val insertValueSql: String get() = attribute.insertValueSql

    /** Server time behavior. */
    val serverTime: ServerTimeType get() = attribute.serverTime

    /** Column position for ordering. */
    val position: Int get() = attribute.position

    /** Custom type mapping. */
    val mapType: String get() = attribute.mapType

    /** Create unique index. */
    val uniqueIndex: Boolean get() = attribute.uniqueIndex

    /** DB type text for DDL (e.g., "integer", "nvarchar(255)"). */
    var dbTypeText: String = ""

    /** DB default value expression. */
    var dbDefaultValue: String = ""
}
