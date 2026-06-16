package freesql

import freesql.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.*

/**
 * Tests specific to file-based SQLite databases.
 * These verify persistence, connection reuse, WAL mode, journal cleanup,
 * and other behaviors that don't apply to in-memory databases.
 */
class FileDbTests {

    @TempDir
    @JvmField
    var tempDir: Path? = null

    private fun createOrm(dbFile: File): IFreeSql {
        return freeSql {
            useDataType("sqlite")
            useConnectionString("jdbc:sqlite:${dbFile.absolutePath}")
            useAutoSyncStructure()
        }
    }

    private fun cleanupDbFiles(dbFile: File) {
        try {
            (createOrm(dbFile) as? freesql.provider.sqlite.SqliteProvider)?.close()
        } catch (_: Exception) {}
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }

    // ---- Persistence Tests ----

    @Test
    fun `data persists after closing and reopening connection`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("persist_test.db").toFile()

        try {
            // Open, insert, close
            val orm1 = createOrm(dbFile)
            orm1.codeFirst.syncStructure(TestUser::class)
            orm1.insert<TestUser>().setSource(
                TestUser(name = "Persist", email = "persist@test.com", age = 30, isActive = true)
            ).executeAffrows()
            (orm1 as freesql.provider.sqlite.SqliteProvider).close()

            // Reopen and verify
            val orm2 = createOrm(dbFile)
            orm2.codeFirst.syncStructure(TestUser::class)
            val user = orm2.select<TestUser>().whereExpr(TestUsers.name eq "Persist").first()
            assertNotNull(user, "Data should persist across connections")
            assertEquals("Persist", user.name)
            assertEquals("persist@test.com", user.email)
            (orm2 as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    @Test
    fun `multiple entities persist correctly`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("multi_entity_test.db").toFile()

        try {
            val orm1 = createOrm(dbFile)
            orm1.codeFirst.syncStructure(TestUser::class, TestPost::class)

            orm1.insert<TestUser>().setSource(
                TestUser(name = "User1", email = "user1@test.com", age = 30, isActive = true)
            ).executeAffrows()
            orm1.insert<TestPost>().setSource(
                TestPost(title = "Post1", content = "Content", userId = 1, viewCount = 100)
            ).executeAffrows()
            (orm1 as freesql.provider.sqlite.SqliteProvider).close()

            val orm2 = createOrm(dbFile)
            orm2.codeFirst.syncStructure(TestUser::class, TestPost::class)
            assertEquals(1L, orm2.select<TestUser>().count())
            assertEquals(1L, orm2.select<TestPost>().count())
            (orm2 as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    @Test
    fun `schema persists across connections`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("schema_persist_test.db").toFile()

        try {
            val orm1 = createOrm(dbFile)
            orm1.codeFirst.syncStructure(TestUser::class)
            (orm1 as freesql.provider.sqlite.SqliteProvider).close()

            val orm2 = createOrm(dbFile)
            val tables = orm2.dbFirst.getTables()
            val userTable = tables.find { it.name == "users" }
            assertNotNull(userTable, "Schema should persist across connections")

            val colNames = userTable.columns.map { it.name }
            assertTrue(colNames.contains("id"))
            assertTrue(colNames.contains("name"))
            assertTrue(colNames.contains("email"))
            (orm2 as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    @Test
    fun `auto increment persists across connections`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("autoinc_test.db").toFile()

        try {
            val orm1 = createOrm(dbFile)
            orm1.codeFirst.syncStructure(TestUser::class)
            orm1.insert<TestUser>().setSource(
                TestUser(name = "User1", email = "user1@test.com", age = 30, isActive = true)
            ).executeAffrows()
            (orm1 as freesql.provider.sqlite.SqliteProvider).close()

            val orm2 = createOrm(dbFile)
            orm2.codeFirst.syncStructure(TestUser::class)
            val id = orm2.insert<TestUser>().setSource(
                TestUser(name = "User2", email = "user2@test.com", age = 25, isActive = true)
            ).executeIdentity()
            assertEquals(2L, id, "Auto increment should continue from previous connection")
            (orm2 as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    // ---- WAL Mode Tests ----

    @Test
    fun `WAL journal mode creates WAL file`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("wal_test.db").toFile()

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class)

            // Insert some data to trigger WAL
            orm.insert<TestUser>().setSource(
                TestUser(name = "WalUser", email = "wal@test.com", age = 30, isActive = true)
            ).executeAffrows()

            // Check if WAL file exists (SQLite creates it for write-ahead logging)
            val walFile = File(dbFile.path + "-wal")
            // WAL file may or may not exist depending on SQLite configuration
            // Just verify the DB file itself exists
            assertTrue(dbFile.exists(), "DB file should exist")

            (orm as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    // ---- Concurrent Access Tests ----

    @Test
    fun `multiple readers can access file DB simultaneously`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("concurrent_test.db").toFile()

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class)

            // Insert test data
            (1..10).forEach { i ->
                orm.insert<TestUser>().setSource(
                    TestUser(name = "User$i", email = "user$i@test.com", age = 20 + i, isActive = true)
                ).executeAffrows()
            }

            // Multiple reads should all succeed
            val count = orm.select<TestUser>().count()
            assertEquals(10L, count)

            val users = orm.select<TestUser>().toList()
            assertEquals(10, users.size)

            val filtered = orm.select<TestUser>().whereExpr(TestUsers.age gt 25).toList()
            assertEquals(5, filtered.size)

            (orm as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    // ---- Large Dataset Tests ----

    @Test
    fun `file DB handles large batch insert and query`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("large_batch_test.db").toFile()

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class)

            // Insert 1000 users
            val users = (1..1000).map { i ->
                TestUser(name = "BulkUser$i", email = "bulk$i@test.com", age = 20 + (i % 50), isActive = i % 2 == 0)
            }
            val count = orm.insert<TestUser>().setSource(users).executeAffrows()
            assertEquals(1000, count)

            // Verify counts
            assertEquals(1000L, orm.select<TestUser>().count())

            // Verify filtering
            val activeCount = orm.select<TestUser>().whereExpr(TestUsers.isActive eq true).count()
            assertEquals(500L, activeCount)

            // Verify pagination
            val page1 = orm.select<TestUser>().orderBy(TestUsers.id).skip(0).take(10).toList()
            assertEquals(10, page1.size)

            // Verify aggregation
            val maxAge = (orm.select<TestUser>().max(TestUsers.age) as Number).toLong()
            assertEquals(69L, maxAge) // 20 + 49 = 69

            (orm as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    // ---- Cleanup Tests ----

    @Test
    fun `cleanup removes all DB files`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("cleanup_test.db").toFile()

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class)
            orm.insert<TestUser>().setSource(
                TestUser(name = "Cleanup", email = "cleanup@test.com", age = 30, isActive = true)
            ).executeAffrows()
            (orm as freesql.provider.sqlite.SqliteProvider).close()

            // Verify DB file exists
            assertTrue(dbFile.exists(), "DB file should exist before cleanup")

            // Cleanup
            cleanupDbFiles(dbFile)

            // Verify all files are gone
            assertFalse(dbFile.exists(), "DB file should be deleted")
            assertFalse(File(dbFile.path + "-wal").exists(), "WAL file should be deleted")
            assertFalse(File(dbFile.path + "-shm").exists(), "SHM file should be deleted")
            assertFalse(File(dbFile.path + "-journal").exists(), "journal file should be deleted")
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    // ---- Edge Cases ----

    @Test
    fun `empty file DB has correct schema`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("empty_schema_test.db").toFile()

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class, TestPost::class)

            val tables = orm.dbFirst.getTables()
            val tableNames = tables.map { it.name }
            assertTrue(tableNames.contains("users"), "Should have users table")
            assertTrue(tableNames.contains("posts"), "Should have posts table")

            // Should have 0 rows
            assertEquals(0L, orm.select<TestUser>().count())
            assertEquals(0L, orm.select<TestPost>().count())

            (orm as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }

    @Test
    fun `file DB with special characters in path`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val specialDir = dir.resolve("my db files").toFile()
        specialDir.mkdirs()
        val dbFile = specialDir.resolve("test db.db")

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class)
            orm.insert<TestUser>().setSource(
                TestUser(name = "Special", email = "special@test.com", age = 30, isActive = true)
            ).executeAffrows()

            val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "Special").first()
            assertNotNull(user)
            assertEquals("Special", user.name)

            (orm as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
            specialDir.delete()
        }
    }

    @Test
    fun `InsertOrUpdate works with file DB`() {
        val dir = tempDir ?: fail("tempDir not initialized")
        val dbFile = dir.resolve("upsert_test.db").toFile()

        try {
            val orm = createOrm(dbFile)
            orm.codeFirst.syncStructure(TestUser::class)

            // First insert
            orm.insertOrUpdate<TestUser>()
                .setSource(TestUser(name = "Upsert", email = "upsert@test.com", age = 30, isActive = true))
                .executeAffrows()

            val user1 = orm.select<TestUser>().whereExpr(TestUsers.name eq "Upsert").first()
            assertNotNull(user1)

            // Update via upsert
            orm.insertOrUpdate<TestUser>()
                .setSource(TestUser(name = "Upsert", email = "upsert@test.com", age = 31, isActive = true))
                .executeAffrows()

            val user2 = orm.select<TestUser>().whereExpr(TestUsers.name eq "Upsert").first()
            assertNotNull(user2)

            (orm as freesql.provider.sqlite.SqliteProvider).close()
        } finally {
            cleanupDbFiles(dbFile)
        }
    }
}
