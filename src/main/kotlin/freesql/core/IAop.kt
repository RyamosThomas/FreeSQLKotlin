package freesql.core

import kotlin.reflect.KClass

/**
 * AOP interceptor hooks for query/command lifecycle.
 * Port of FreeSql IAop / AopProvider.
 */
interface IAop {
    /** Called before SQL executes. Return false to cancel. */
    var executingSql: ((ExecutingSqlInfo) -> Unit)?

    /** Called on any DB exception. */
    var dbException: ((Exception) -> Unit)?

    /** Called before INSERT/UPDATE/DELETE/SELECT execution. */
    var curdBefore: ((CurdBeforeEventArgs) -> Unit)?

    /** Called after INSERT/UPDATE/DELETE/SELECT execution. */
    var curdAfter: ((CurdAfterEventArgs) -> Unit)?

    /** Called before schema sync/structure migration. */
    var syncStructureBefore: ((SyncStructureEventArgs) -> Unit)?

    /** Called after schema sync/structure migration. */
    var syncStructureAfter: ((SyncStructureEventArgs) -> Unit)?
}

class ExecutingSqlInfo(
    val sql: String,
    val parameters: List<Any?> = emptyList(),
    val commandType: CommandType = CommandType.TEXT
)

enum class CommandType { TEXT, SCHEMA }

/**
 * Event args fired before a CRUD SQL statement executes.
 */
class CurdBeforeEventArgs(
    val sql: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val commandType: CommandType = CommandType.TEXT
)

/**
 * Event args fired after a CRUD SQL statement executes.
 */
class CurdAfterEventArgs(
    val sql: String,
    val elapsed: Long = 0L,
    val rowsAffected: Int = 0
)

/**
 * Event args fired before/after schema structure sync.
 */
class SyncStructureEventArgs(
    val entityType: KClass<*>,
    val ddl: String = ""
)
