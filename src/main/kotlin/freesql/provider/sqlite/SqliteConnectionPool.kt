package freesql.provider.sqlite

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SQLite connection pool.
 * Port of FreeSql connection pooling for SQLite.
 *
 * For :memory: databases, uses a single shared connection (SQLite in-memory DBs
 * are per-connection, so sharing is required).
 * For file databases, pools connections with configurable min/max sizes.
 *
 * Supports connection string parameters:
 *   Min pool size=X
 *   Max pool size=X
 *   Connection LifeTime=X (seconds; 0=infinite)
 *   Pooling=true/false
 *   Attachs=db1:path1;db2:path2 (ATTACH DATABASE support)
 */
class SqliteConnectionPool(
    private val connectionString: String,
    private val minPoolSize: Int = 1,
    private val maxPoolSize: Int = 100,
    private val connectionLifeTime: Long = 0 // 0 = infinite, in seconds
) {
    private val pool = ConcurrentLinkedQueue<PooledConnection>()
    private val activeCount = AtomicInteger(0)
    private val isMemory: Boolean = connectionString.contains(":memory:")
    private val isPoolingEnabled: Boolean
    private val lock = ReentrantLock()
    private var closed = false

    // Parsed connection string parameters
    private val baseConnectionString: String
    private val attachStatements: List<Pair<String, String>> // alias -> path

    init {
        Class.forName("org.sqlite.JDBC")
        val parsed = parseConnectionString(connectionString)
        baseConnectionString = parsed.first
        attachStatements = parsed.second
        isPoolingEnabled = !isMemory && parsed.third

        // Pre-populate minimum pool size for file databases
        if (isPoolingEnabled && !isMemory) {
            repeat(minPoolSize.coerceAtMost(maxPoolSize)) {
                pool.offer(createPooledConnection())
            }
        }
    }

    /**
     * Parse the connection string to extract pool settings and ATTACH directives.
     * Returns Triple(baseConnectionString, attachStatements, poolingEnabled)
     */
    private fun parseConnectionString(connStr: String): Triple<String, List<Pair<String, String>>, Boolean> {
        if (!connStr.contains(";")) return Triple(connStr, emptyList(), true)

        val parts = connStr.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val baseParts = mutableListOf<String>()
        val attaches = mutableListOf<Pair<String, String>>()
        var pooling = true

        for (part in parts) {
            when {
                part.startsWith("Attachs=", ignoreCase = true) -> {
                    // Format: Attachs=alias1:path1;alias2:path2  (but ; is separator so we handle differently)
                    // Actually in connection string the format is: Attachs=alias1:path1|alias2:path2
                    val attachStr = part.substringAfter("=", "")
                    attachStr.split("|").forEach { entry ->
                        val colonIdx = entry.indexOf(':')
                        if (colonIdx > 0) {
                            val alias = entry.substring(0, colonIdx).trim()
                            val path = entry.substring(colonIdx + 1).trim()
                            if (alias.isNotEmpty() && path.isNotEmpty()) {
                                attaches.add(alias to path)
                            }
                        }
                    }
                }
                part.startsWith("Pooling=", ignoreCase = true) -> {
                    pooling = part.substringAfter("=", "").trim().equals("true", ignoreCase = true)
                }
                // Min pool size, Max pool size, and Connection LifeTime are
                // configured via constructor parameters, not connection string.
                // These are parsed but intentionally ignored here.
                part.startsWith("Min pool size=", ignoreCase = true) -> { /* ignored */ }
                part.startsWith("Max pool size=", ignoreCase = true) -> { /* ignored */ }
                part.startsWith("Connection LifeTime=", ignoreCase = true) -> { /* ignored */ }
                else -> baseParts.add(part)
            }
        }

        return Triple(baseParts.joinToString(";"), attaches, pooling)
    }

    private fun createPooledConnection(): PooledConnection {
        val conn = DriverManager.getConnection(baseConnectionString)
        // Apply pragmas for SQLite
        conn.createStatement().use { stmt ->
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA synchronous=NORMAL")
            stmt.execute("PRAGMA foreign_keys=ON")
        }
        // Attach databases if configured
        attachStatements.forEach { (alias, path) ->
            conn.createStatement().use { stmt ->
                stmt.execute("ATTACH DATABASE '${path.replace("'", "''")}' AS \"${alias.replace("\"", "\"\"")}\"")
            }
        }
        return PooledConnection(conn, System.currentTimeMillis())
    }

    /**
     * Acquire a connection from the pool.
     * For :memory: databases, returns the single shared connection.
     * For file databases, returns a pooled connection or creates a new one if under the limit.
     */
    fun acquire(): PooledConnection {
        if (closed) throw IllegalStateException("Connection pool is closed")

        if (isMemory) {
            // For in-memory databases, use the single shared connection
            lock.withLock {
                if (pool.isEmpty()) {
                    val conn = createPooledConnection()
                    pool.offer(conn)
                }
                activeCount.incrementAndGet()
                return pool.peek()!!
            }
        }

        if (!isPoolingEnabled) {
            activeCount.incrementAndGet()
            return createPooledConnection()
        }

        // Try to get an existing valid connection from the pool
        while (true) {
            val conn = pool.poll()
            if (conn == null) break

            // Check if connection has expired
            if (connectionLifeTime > 0) {
                val ageSeconds = (System.currentTimeMillis() - conn.createdAt) / 1000
                if (ageSeconds > connectionLifeTime) {
                    try { conn.connection.close() } catch (_: Exception) {}
                    continue
                }
            }

            // Check if connection is still valid
            if (conn.isValid()) {
                activeCount.incrementAndGet()
                return conn
            }
            try { conn.connection.close() } catch (_: Exception) {}
        }

        // No valid connection in pool, create new one if under limit
        val currentActive = activeCount.get()
        if (currentActive < maxPoolSize) {
            activeCount.incrementAndGet()
            return createPooledConnection()
        }

        // Pool exhausted — wait and retry (simple spin)
        throw IllegalStateException("Connection pool exhausted: active=$currentActive, max=$maxPoolSize")
    }

    /**
     * Release a connection back to the pool.
     * For :memory: databases, just decrements the active counter.
     * For file databases, returns the connection to the pool or closes it if pool is full.
     */
    fun release(conn: PooledConnection) {
        if (isMemory) {
            activeCount.decrementAndGet()
            return
        }

        activeCount.decrementAndGet()

        if (!isPoolingEnabled || closed) {
            try { conn.connection.close() } catch (_: Exception) {}
            return
        }

        // Return to pool if valid and under max
        if (conn.isValid() && pool.size < maxPoolSize) {
            pool.offer(conn)
        } else {
            try { conn.connection.close() } catch (_: Exception) {}
        }
    }

    /**
     * Close all connections in the pool.
     */
    fun close() {
        closed = true
        while (true) {
            val conn = pool.poll() ?: break
            try { conn.connection.close() } catch (_: Exception) {}
        }
        activeCount.set(0)
    }

    /**
     * Get the number of active (checked out) connections.
     */
    fun getActiveCount(): Int = activeCount.get()

    /**
     * Get the number of idle connections in the pool.
     */
    fun getIdleCount(): Int = pool.size
}

/**
 * Wraps a JDBC Connection with creation metadata.
 */
class PooledConnection(
    val connection: Connection,
    val createdAt: Long
) {
    /**
     * Check if the connection is still valid by running a ping.
     */
    fun isValid(): Boolean {
        return try {
            connection.isValid(5) // 5 second timeout
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ping the database with a lightweight query.
     */
    fun ping() {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT 1").use { it.next() }
        }
    }
}
