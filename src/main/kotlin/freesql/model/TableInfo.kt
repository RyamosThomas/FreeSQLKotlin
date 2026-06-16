package freesql.model

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Runtime metadata for a mapped entity class.
 * Port of FreeSql TableInfo.
 */
class TableInfo(
    /** The Kotlin class. */
    val type: KClass<*>,
    /** Kotlin class simple name. */
    val csName: String,
    /** Database table name. */
    val dbName: String,
    /** Previous DB name (for rename migrations). */
    val dbOldName: String = "",
    /** Disable auto schema sync. */
    val disableSyncStructure: Boolean = false,
    /** Sharding expression. */
    val asTable: String = ""
) {
    /** All columns in order. */
    val columns: MutableList<ColumnInfo> = mutableListOf()

    /** Columns keyed by Kotlin property name. */
    val columnsByCs: MutableMap<String, ColumnInfo> = mutableMapOf()

    /** Ignored columns (not mapped to DB). */
    val columnsByCsIgnore: MutableMap<String, ColumnInfo> = mutableMapOf()

    /** Primary key columns. */
    val primarys: List<ColumnInfo>
        get() = columns.filter { it.isPrimary }

    /** Identity (auto-increment) columns. */
    val identitys: List<ColumnInfo>
        get() = columns.filter { it.isIdentity }

    /** All indexes defined on this table. */
    val indexes: MutableList<IndexInfo> = mutableListOf()

    /** Navigation/relationship refs. */
    val refs: MutableList<TableRef> = mutableListOf()

    fun addColumn(col: ColumnInfo) {
        columns.add(col)
        if (col.isIgnore) {
            columnsByCsIgnore[col.csName] = col
        } else {
            columnsByCs[col.csName] = col
        }
    }
}

/**
 * Metadata for a database index.
 */
class IndexInfo(
    val name: String,
    val fields: List<String>,
    val isUnique: Boolean = false
)
