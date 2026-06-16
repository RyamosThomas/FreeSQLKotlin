package freesql.core

import freesql.model.ColumnInfo
import freesql.model.DbTypeInfo
import freesql.model.TableInfo
import kotlin.reflect.KClass

/**
 * Schema migration interface.
 * Port of FreeSql ICodeFirst.
 */
interface ICodeFirst {

    /** Auto-sync entity structures on first access. */
    var isAutoSyncStructure: Boolean

    /** Convert table/column names to lowercase in DDL. */
    var isSyncStructureToLower: Boolean

    /** Convert table/column names to uppercase in DDL. */
    var isSyncStructureToUpper: Boolean

    /** Disable parameterized queries (inline values). */
    var isNoneCommandParameter: Boolean

    /** Explicitly sync the schema for the given entity types. */
    fun syncStructure(vararg entityTypes: KClass<*>)

    /** Get the DDL statements needed to sync an entity type. */
    fun getComparisonDDLStatements(entityType: KClass<*>): String

    /** Get the SQLite database type info for a Kotlin type. */
    fun getDbInfo(kotlinType: KClass<*>): DbTypeInfo?
}
