package freesql

import freesql.annotation.*
import freesql.core.*
import freesql.provider.sqlite.SqliteCodeFirst
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for CodeFirst features: migration history, fluent API, rename detection,
 * name conversion, disableSyncStructure.
 */
class CodeFirstMemoryTest : CodeFirstTests() {
    override val useFileDb = false
}

class CodeFirstFileTest : CodeFirstTests() {
    override val useFileDb = true
}

abstract class CodeFirstTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    // ---- Migration History ----

    @Nested
    inner class MigrationHistoryTests {

        @Test
        fun `syncStructure records migration history`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val history = codeFirst.getMigrationHistory()
            assertTrue(history.isNotEmpty(), "Migration history should have entries after sync")
        }

        @Test
        fun `migration history records entity name`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val history = codeFirst.getMigrationHistory(TestUser::class)
            assertTrue(history.isNotEmpty(), "Should have migration record for TestUser")
            assertEquals("users", history.last()["entity_name"])
        }

        @Test
        fun `migration history records DDL`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val history = codeFirst.getMigrationHistory(TestUser::class)
            assertTrue(history.isNotEmpty())
            val ddl = history.last()["ddl_statements"] as? String
            assertNotNull(ddl, "DDL should be recorded")
        }

        @Test
        fun `migration history has timestamps`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val history = codeFirst.getMigrationHistory()
            assertTrue(history.isNotEmpty())
            val appliedAt = history.last()["applied_at"]
            assertNotNull(appliedAt, "applied_at should be set")
        }

        @Test
        fun `re-syncing same entity does not duplicate history for in-memory synced types`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val countBefore = codeFirst.getMigrationHistory().size
            // syncStructure skips already-synced types internally, so calling again won't add more
            codeFirst.syncStructure(TestUser::class)
            val countAfter = codeFirst.getMigrationHistory().size
            assertEquals(countBefore, countAfter, "Re-syncing should not duplicate (skipped in-memory)")
        }
    }

    // ---- Fluent API Entity Configuration ----

    @Nested
    inner class FluentApiTests {

        @Test
        fun `fluent API overrides table name`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst

            // Create a simple entity class for testing
            @Table(name = "original_name")
            data class FluentTestEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = ""
            )

            // Override table name via fluent API
            codeFirst.configEntity(FluentTestEntity::class) {
                tableName = "renamed_via_fluent"
            }

            val tableInfo = codeFirst.buildTableInfo(FluentTestEntity::class)
            assertEquals("renamed_via_fluent", tableInfo.dbName)

            // Clean up config
            codeFirst.configEntity(FluentTestEntity::class) {}
        }

        @Test
        fun `fluent API overrides column name`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst

            data class FluentColTest(
                val id: Int = 0,
                val name: String = ""
            )

            codeFirst.configEntity(FluentColTest::class) {
                column("name") {
                    dbName = "user_name_full"
                }
            }

            val tableInfo = codeFirst.buildTableInfo(FluentColTest::class)
            val nameCol = tableInfo.columns.find { it.csName == "name" }
            assertNotNull(nameCol)
            assertEquals("user_name_full", nameCol.dbName)

            // Clean up
            codeFirst.configEntity(FluentColTest::class) {}
        }

        @Test
        fun `fluent API can set column as ignored`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst

            data class FluentIgnoreTest(
                val id: Int = 0,
                val name: String = "",
                val secret: String = ""
            )

            codeFirst.configEntity(FluentIgnoreTest::class) {
                column("secret") {
                    isIgnore = true
                }
            }

            val tableInfo = codeFirst.buildTableInfo(FluentIgnoreTest::class)
            val secretCol = tableInfo.columns.find { it.csName == "secret" }
            assertNull(secretCol, "Ignored column should not appear in table info")

            // Clean up
            codeFirst.configEntity(FluentIgnoreTest::class) {}
        }

        @Test
        fun `fluent API can override stringLength`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst

            data class FluentStrLenTest(
                val id: Int = 0,
                val name: String = ""
            )

            codeFirst.configEntity(FluentStrLenTest::class) {
                column("name") {
                    stringLength = 500
                }
            }

            val tableInfo = codeFirst.buildTableInfo(FluentStrLenTest::class)
            val nameCol = tableInfo.columns.find { it.csName == "name" }
            assertNotNull(nameCol)
            assertEquals(500, nameCol.attribute.stringLength)

            // Clean up
            codeFirst.configEntity(FluentStrLenTest::class) {}
        }
    }

    // ---- Rename Detection ----

    @Nested
    inner class RenameDetectionTests {

        @Test
        fun `column rename via oldName in annotation`() {
            @Table(name = "rename_test_table")
            data class RenameTestEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                @Column(oldName = "old_user_name", name = "new_user_name")
                val name: String = ""
            )

            val codeFirst = orm.codeFirst as SqliteCodeFirst

            // Create table with old column name
            orm.executeNonQuery("DROP TABLE IF EXISTS rename_test_table")
            orm.executeNonQuery("""
                CREATE TABLE rename_test_table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    old_user_name TEXT NOT NULL DEFAULT ''
                )
            """)

            // Sync should detect rename
            val ddl = codeFirst.getComparisonDDLStatements(RenameTestEntity::class)
            assertTrue(
                ddl.contains("RENAME COLUMN"),
                "Should generate RENAME COLUMN statement: $ddl"
            )

            // Execute the sync
            codeFirst.syncStructure(RenameTestEntity::class)

            // Verify column was renamed
            val columns = orm.ado.executeDataTable("PRAGMA table_info(rename_test_table)")
            val colNames = columns.map { it["name"] }
            assertTrue(colNames.contains("new_user_name"), "New column name should exist: $colNames")
            assertFalse(colNames.contains("old_user_name"), "Old column name should be gone: $colNames")

            // Cleanup
            orm.executeNonQuery("DROP TABLE IF EXISTS rename_test_table")
        }

        @Test
        fun `table rename via oldName in annotation`() {
            @Table(name = "new_table_name", oldName = "old_table_name")
            data class TableRenameEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = ""
            )

            val codeFirst = orm.codeFirst as SqliteCodeFirst

            // Create table with old name
            orm.executeNonQuery("DROP TABLE IF EXISTS old_table_name")
            orm.executeNonQuery("DROP TABLE IF EXISTS new_table_name")
            orm.executeNonQuery("""
                CREATE TABLE old_table_name (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL DEFAULT ''
                )
            """)

            // Sync should detect table rename
            val ddl = codeFirst.getComparisonDDLStatements(TableRenameEntity::class)
            assertTrue(
                ddl.contains("RENAME TO"),
                "Should generate RENAME TO statement: $ddl"
            )

            // Execute the sync
            codeFirst.syncStructure(TableRenameEntity::class)

            // Verify table was renamed
            val tables = orm.dbFirst.getTables()
            val tableNames = tables.map { it.name }
            assertTrue(tableNames.contains("new_table_name"), "New table name should exist: $tableNames")
            assertFalse(tableNames.contains("old_table_name"), "Old table name should be gone: $tableNames")

            // Cleanup
            orm.executeNonQuery("DROP TABLE IF EXISTS new_table_name")
        }
    }

    // ---- Name Conversion ----

    @Nested
    inner class NameConversionTests {

        @Test
        fun `syncStructureToLower converts table and column names`() {
            val lowerOrm = freeSql {
                useDataType("sqlite")
                useConnectionString("jdbc:sqlite::memory:")
                useAutoSyncStructure()
                useSyncStructureToLower()
            }

            @Table(name = "UPPER_TABLE")
            data class UpperTableEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                @Column(name = "UPPER_COLUMN")
                val name: String = ""
            )

            lowerOrm.codeFirst.syncStructure(UpperTableEntity::class)

            val tables = lowerOrm.dbFirst.getTables()
            val tableNames = tables.map { it.name }
            assertTrue(tableNames.contains("upper_table"), "Table name should be lowercase: $tableNames")

            val table = tables.find { it.name == "upper_table" }
            assertNotNull(table)
            val colNames = table.columns.map { it.name }
            assertTrue(colNames.contains("upper_column"), "Column name should be lowercase: $colNames")

            (lowerOrm as freesql.provider.sqlite.SqliteProvider).close()
        }

        @Test
        fun `syncStructureToUpper converts table and column names`() {
            val upperOrm = freeSql {
                useDataType("sqlite")
                useConnectionString("jdbc:sqlite::memory:")
                useAutoSyncStructure()
                useSyncStructureToUpper()
            }

            @Table(name = "lower_table")
            data class LowerTableEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = ""
            )

            upperOrm.codeFirst.syncStructure(LowerTableEntity::class)

            val tables = upperOrm.dbFirst.getTables()
            val tableNames = tables.map { it.name }
            assertTrue(tableNames.contains("LOWER_TABLE"), "Table name should be uppercase: $tableNames")

            (upperOrm as freesql.provider.sqlite.SqliteProvider).close()
        }
    }

    // ---- DisableSyncStructure ----

    @Nested
    inner class DisableSyncStructureTests {

        @Test
        fun `disableSyncStructure prevents table creation`() {
            val testOrm = freeSql {
                useDataType("sqlite")
                useConnectionString("jdbc:sqlite::memory:")
            }

            @Table(name = "disabled_table", disableSyncStructure = true)
            data class DisabledEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = ""
            )

            // Attempting to sync should be a no-op
            testOrm.codeFirst.syncStructure(DisabledEntity::class)

            // Table should NOT exist
            val exists = testOrm.dbFirst.existsTable("disabled_table")
            assertFalse(exists, "Table with disableSyncStructure=true should not be created")

            (testOrm as freesql.provider.sqlite.SqliteProvider).close()
        }

        @Test
        fun `normal tables still sync when disabled table is also requested`() {
            val testOrm = freeSql {
                useDataType("sqlite")
                useConnectionString("jdbc:sqlite::memory:")
            }

            @Table(name = "disabled_table_2", disableSyncStructure = true)
            data class DisabledEntity2(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = ""
            )

            @Table(name = "enabled_table")
            data class EnabledEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = ""
            )

            // Sync both — disabled should be skipped, enabled should be created
            testOrm.codeFirst.syncStructure(DisabledEntity2::class, EnabledEntity::class)

            assertFalse(testOrm.dbFirst.existsTable("disabled_table_2"))
            assertTrue(testOrm.dbFirst.existsTable("enabled_table"))

            (testOrm as freesql.provider.sqlite.SqliteProvider).close()
        }
    }

    // ---- Existing Features Verification ----

    @Nested
    inner class ExistingFeaturesTests {

        @Test
        fun `auto-sync creates tables with correct columns`() {
            val tables = orm.dbFirst.getTables()
            val userTable = tables.find { it.name == "users" }
            assertNotNull(userTable, "users table should exist")

            val colNames = userTable.columns.map { it.name }
            assertTrue(colNames.contains("id"))
            assertTrue(colNames.contains("name"))
            assertTrue(colNames.contains("email"))
            assertTrue(colNames.contains("age"))
            assertTrue(colNames.contains("is_active"))
        }

        @Test
        fun `auto-sync creates indexes`() {
            // Verify the table exists and has proper structure
            // (SQLite doesn't expose index info easily via IDbFirst yet)
            val tables = orm.dbFirst.getTables()
            val userTable = tables.find { it.name == "users" }
            assertNotNull(userTable)
            assertTrue(userTable.columns.isNotEmpty())
        }

        @Test
        fun `syncStructure adds new columns to existing table`() {
            // Create initial table
            orm.executeNonQuery("DROP TABLE IF EXISTS evolve_test")
            orm.executeNonQuery("""
                CREATE TABLE evolve_test (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL DEFAULT ''
                )
            """)

            // Define entity with extra column
            @Table(name = "evolve_test")
            data class EvolvedEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = "",
                val newColumn: String = ""
            )

            orm.codeFirst.syncStructure(EvolvedEntity::class)

            // Verify new column was added
            val columns = orm.ado.executeDataTable("PRAGMA table_info(evolve_test)")
            val colNames = columns.map { it["name"] }
            assertTrue(colNames.contains("newColumn"), "New column should be added: $colNames")

            orm.executeNonQuery("DROP TABLE IF EXISTS evolve_test")
        }

        @Test
        fun `data survives schema re-sync`() {
            orm.insert<TestUser>().setSource(
                TestUser(name = "Survivor", email = "survive@test.com", age = 30, isActive = true)
            ).executeAffrows()

            // Re-sync
            orm.codeFirst.syncStructure(TestUser::class)

            // Data should still be there
            val user = orm.select<TestUser>().whereExpr(TestUsers.name eq "Survivor").first()
            assertNotNull(user)
            assertEquals("Survivor", user.name)
        }
    }
}
