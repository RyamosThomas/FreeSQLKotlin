package freesql

import freesql.annotation.*
import freesql.core.*
import freesql.provider.sqlite.SqliteCodeFirst
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.reflect.full.findAnnotation
import kotlin.test.*

/**
 * Tests for data annotation features: position, uniqueIndex, serverTime, mapType, navigate.
 */

// Top-level test entity for ServerTime tests (can't have annotations on local classes)
@Table(name = "servtime_test")
data class ServerTimeEntity(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    val name: String = "",
    @Column(serverTime = ServerTimeType.INSERT)
    val createdAt: String = "",
    @Column(serverTime = ServerTimeType.UPDATE)
    val updatedAt: String = "",
    @Column(serverTime = ServerTimeType.BOTH)
    val lastAccess: String = ""
)

class AnnotationMemoryTest : AnnotationTests() {
    override val useFileDb = false
}

class AnnotationFileTest : AnnotationTests() {
    override val useFileDb = true
}

abstract class AnnotationTests : FreeSqlTestBase() {

    override val entityClasses = listOf(TestUser::class, TestPost::class)

    // ---- Position ----

    @Nested
    inner class PositionTests {

        @Test
        fun `position controls column order in DDL`() {
            @Table(name = "pos_test")
            data class PosEntity(
                @Column(isPrimary = true, isIdentity = true, position = 3)
                val id: Int = 0,
                @Column(position = 1)
                val name: String = "",
                @Column(position = 2)
                val email: String = ""
            )

            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(PosEntity::class)

            // With position, order should be: name(1), email(2), id(3)
            assertEquals("name", tableInfo.columns[0].csName)
            assertEquals("email", tableInfo.columns[1].csName)
            assertEquals("id", tableInfo.columns[2].csName)
        }

        @Test
        fun `no position uses default reflection order`() {
            @Table(name = "nopos_test")
            data class NoPosEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = "",
                val email: String = ""
            )

            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(NoPosEntity::class)

            // Without position, all 3 columns should exist (order not guaranteed)
            assertEquals(3, tableInfo.columns.size)
            val colNames = tableInfo.columns.map { it.csName }.toSet()
            assertEquals(setOf("id", "name", "email"), colNames)
        }
    }

    // ---- UniqueIndex ----

    @Nested
    inner class UniqueIndexTests {

        @Test
        fun `uniqueIndex creates unique index on column`() {
            @Table(name = "uniq_test")
            data class UniqEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                @Column(uniqueIndex = true)
                val email: String = ""
            )

            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(UniqEntity::class)

            val idx = tableInfo.indexes.find { it.name == "IX_uniq_test_email" }
            assertNotNull(idx, "Should auto-create unique index: ${tableInfo.indexes.map { it.name }}")
            assertTrue(idx.isUnique)
            assertEquals(listOf("email"), idx.fields)
        }

        @Test
        fun `uniqueIndex column works end-to-end`() {
            @Table(name = "uniq_e2e")
            data class UniqE2E(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                @Column(uniqueIndex = true)
                val email: String = ""
            )

            orm.codeFirst.syncStructure(UniqE2E::class)

            // Insert first row
            orm.insert(UniqE2E::class).setSource(UniqE2E(email = "test@test.com")).executeAffrows()

            // Insert duplicate should fail
            val ex = assertFailsWith<Exception> {
                orm.insert(UniqE2E::class).setSource(UniqE2E(email = "test@test.com")).executeAffrows()
            }
            assertTrue(ex.message?.contains("UNIQUE") == true, "Should fail with UNIQUE constraint: ${ex.message}")

            orm.executeNonQuery("DROP TABLE IF EXISTS uniq_e2e")
        }
    }

    // ---- ServerTime ----

    @Nested
    inner class ServerTimeTests {

        @Test
        fun `serverTime INSERT sets value on insert`() {
            orm.codeFirst.syncStructure(ServerTimeEntity::class)
            orm.insert(ServerTimeEntity::class).setSource(
                ServerTimeEntity(name = "Test", createdAt = "", updatedAt = "", lastAccess = "")
            ).executeAffrows()

            val row = orm.select(ServerTimeEntity::class).first()
            assertNotNull(row)
            assertTrue(row.createdAt.isNotEmpty(), "createdAt should be auto-set on insert: '${row.createdAt}'")

            orm.executeNonQuery("DROP TABLE IF EXISTS servtime_test")
        }

        @Test
        fun `serverTime UPDATE sets value on update`() {
            orm.codeFirst.syncStructure(ServerTimeEntity::class)
            orm.insert(ServerTimeEntity::class).setSource(
                ServerTimeEntity(name = "Test", createdAt = "", updatedAt = "", lastAccess = "")
            ).executeAffrows()

            // Small delay to ensure timestamp difference
            Thread.sleep(1100)

            val entity = orm.select(ServerTimeEntity::class).first()!!
            orm.update(ServerTimeEntity::class)
                .setSource(entity.copy(name = "Updated"))
                .whereExpr(ColumnRef<Int>("id") eq entity.id)
                .executeAffrows()

            val updated = orm.select(ServerTimeEntity::class).first()!!
            assertTrue(updated.updatedAt.isNotEmpty(), "updatedAt should be auto-set on update: '${updated.updatedAt}'")
            assertTrue(updated.lastAccess.isNotEmpty(), "lastAccess should be auto-set on update: '${updated.lastAccess}'")

            orm.executeNonQuery("DROP TABLE IF EXISTS servtime_test")
        }
    }

    // ---- Navigate ----

    @Nested
    inner class NavigateTests {

        @Test
        fun `navigate annotation is readable`() {
            val prop = TestPost::class.members.find { it.name == "user" }
            assertNotNull(prop)
            val nav = prop.findAnnotation<Navigate>()
            assertNotNull(nav, "TestPost.user should have @Navigate annotation")
            assertEquals("userId", nav.bind)
        }
    }

    // ---- Existing @Table features ----

    @Nested
    inner class TableAnnotationTests {

        @Test
        fun `table name override`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(TestUser::class)
            assertEquals("users", tableInfo.dbName)
        }

        @Test
        fun `table disableSyncStructure`() {
            @Table(name = "disabled_ann_test", disableSyncStructure = true)
            data class DisabledEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0
            )

            orm.codeFirst.syncStructure(DisabledEntity::class)
            assertFalse(orm.dbFirst.existsTable("disabled_ann_test"))
        }
    }

    // ---- Existing @Column features ----

    @Nested
    inner class ColumnAnnotationTests {

        @Test
        fun `column name override`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(TestUser::class)
            // isActive maps to is_active in the table
            val isActiveCol = tableInfo.columnsByCs["isActive"]
            assertNotNull(isActiveCol)
            assertEquals("is_active", isActiveCol.dbName)
        }

        @Test
        fun `column isPrimary and isIdentity`() {
            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(TestUser::class)
            val idCol = tableInfo.columnsByCs["id"]
            assertNotNull(idCol)
            assertTrue(idCol.isPrimary)
            assertTrue(idCol.isIdentity)
        }

        @Test
        fun `column canInsert canUpdate`() {
            @Table(name = "canins_test")
            data class CanInsEntity(
                @Column(isPrimary = true, isIdentity = true)
                val id: Int = 0,
                val name: String = "",
                @Column(canInsert = false, canUpdate = false)
                val readOnly: String = ""
            )

            val codeFirst = orm.codeFirst as SqliteCodeFirst
            val tableInfo = codeFirst.buildTableInfo(CanInsEntity::class)
            val roCol = tableInfo.columnsByCs["readOnly"]
            assertNotNull(roCol)
            assertFalse(roCol.canInsert)
            assertFalse(roCol.canUpdate)
        }
    }
}
