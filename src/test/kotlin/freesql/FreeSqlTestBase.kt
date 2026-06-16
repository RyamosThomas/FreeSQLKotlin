package freesql

import freesql.core.IFreeSql
import freesql.provider.sqlite.SqliteProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Base class for FreeSQL tests. Provides both in-memory and file-based SQLite database support.
 *
 * Subclasses can override [useFileDb] to control which mode is used.
 * File-based tests get a fresh temp directory per test method, automatically cleaned up.
 */
abstract class FreeSqlTestBase {

    /** Override to true to use a file-based SQLite DB instead of in-memory. */
    protected open val useFileDb: Boolean = false

    /** The IFreeSql instance available in tests. */
    protected lateinit var orm: IFreeSql

    /** Path to the file-based DB file (only valid when [useFileDb] is true). */
    protected lateinit var dbFile: File

    @TempDir
    @JvmField
    var tempDir: Path? = null

    /** Entity classes to auto-sync before each test. Override in subclasses. */
    protected open val entityClasses: List<KClass<*>> = emptyList()

    @BeforeEach
    fun setUp() {
        val connectionString = if (useFileDb) {
            val dir = tempDir ?: throw IllegalStateException("tempDir not initialized")
            dbFile = dir.resolve("test_${System.nanoTime()}.db").toFile()
            "jdbc:sqlite:${dbFile.absolutePath}"
        } else {
            "jdbc:sqlite::memory:"
        }

        orm = freeSql {
            useDataType("sqlite")
            useConnectionString(connectionString)
            useAutoSyncStructure()
        }

        // Auto-sync entity structures
        if (entityClasses.isNotEmpty()) {
            orm.codeFirst.syncStructure(*entityClasses.toTypedArray())
        }

        onSetUp()
    }

    /** Override for additional setup after ORM is ready. */
    protected open fun onSetUp() {}

    @AfterEach
    fun tearDown() {
        onTearDown()
        // Close the provider to release all connections before deleting the file
        try {
            (orm as? SqliteProvider)?.close()
        } catch (_: Exception) {
            // Best effort cleanup
        }
        // Clean up file-based DB
        if (useFileDb && ::dbFile.isInitialized) {
            dbFile.delete()
            // Also delete WAL/SHM/journal files if they exist
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-journal").delete()
        }
    }

    /** Override for additional teardown before ORM is closed. */
    protected open fun onTearDown() {}
}
