package freesql.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for custom SQL expression functions.
 * Port of FreeSql ExpressionCall mechanism.
 *
 * Usage:
 *   // Register a custom function
 *   ExpressionCallRegistry.register("formatDate") { args ->
 *       "strftime('%Y-%m-%d', ${args[0]})"
 *   }
 *
 *   // Use in expressions
 *   val expr = ExpressionCallRegistry.call("formatDate", listOf(TestUsers.createdAt))
 */
object ExpressionCallRegistry {
    private val functions = ConcurrentHashMap<String, (List<ColumnRef<*>>) -> String>()

    /** Register a custom SQL function. */
    fun register(name: String, generator: (List<ColumnRef<*>>) -> String) {
        functions[name] = generator
    }

    /** Check if a function is registered. */
    fun has(name: String): Boolean = functions.containsKey(name)

    /** Call a registered function and get the SQL expression. */
    fun call(name: String, args: List<ColumnRef<*>>): SqlExpr {
        val generator = functions[name]
            ?: throw IllegalArgumentException("ExpressionCall function '$name' not registered")
        val sql = generator(args)
        return RawSqlExpr(sql)
    }

    /** Get all registered function names. */
    fun registeredNames(): Set<String> = functions.keys.toSet()

    /** Clear all registered functions. */
    fun clear() {
        functions.clear()
    }
}
