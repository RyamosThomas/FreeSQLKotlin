package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.provider.sqlite.SqliteCodeFirst
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 7: CodeFirst (Schema Migration)
// Demonstrates: Auto-sync, DDL generation, rename detection, migration history,
//               fluent API, name conversion, uniqueIndex, serverTime
// Port of: FreeSql CodeFirst
// =============================================================================

// --- Entities with advanced annotations ---

@Table(name = "blog_posts")
data class BlogPost(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 300, position = 1)
    val title: String = "",

    val content: String = "",

    @Column(name = "author_email", uniqueIndex = true, position = 2)
    val authorEmail: String = "",

    @Column(name = "created_at", serverTime = ServerTimeType.INSERT, position = 3)
    val createdAt: String = "",

    @Column(name = "updated_at", serverTime = ServerTimeType.BOTH)
    val updatedAt: String = "",

    @Column(name = "view_count", isNullable = true)
    val viewCount: Int? = 0
)

@Table(name = "comments")
data class Comment(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(name = "post_id")
    val postId: Int = 0,

    val author: String = "",

    val body: String = ""
)

// --- Main demo ---

fun main() {
    val orm = FreeSqlBuilder()
        .useDataType("sqlite")
        .useConnectionString("jdbc:sqlite::memory:")
        .build() // No auto-sync — we'll do it manually

    println("=== CodeFirst (Schema Migration) Example ===\n")

    val codeFirst = orm.codeFirst

    // --- 1. Manual sync ---
    println("--- 1. Manual SyncStructure ---")
    codeFirst.syncStructure(BlogPost::class, Comment::class)
    val tables = orm.dbFirst.getTables()
    println("Tables: ${tables.map { it.name }}")

    // --- 2. DDL preview ---
    println("\n--- 2. DDL Preview ---")
    val ddl = codeFirst.getComparisonDDLStatements(BlogPost::class)
    println("BlogPost DDL:\n$ddl")

    // --- 3. Migration History ---
    println("\n--- 3. Migration History ---")
    val cf = codeFirst as SqliteCodeFirst
    val history = cf.getMigrationHistory()
    history.forEach { entry ->
        println("  Migration #${entry["id"]}: ${entry["entity_name"]} at ${entry["applied_at"]}")
    }

    // --- 4. ServerTime auto-fill ---
    println("\n--- 4. ServerTime ---")
    orm.insert(BlogPost::class).setSource(
        BlogPost(title = "First Post", content = "Hello world!", authorEmail = "author@example.com")
    ).executeAffrows()
    val post = orm.select(BlogPost::class).first()
    println("Created at: ${post?.createdAt}")
    println("Updated at: ${post?.updatedAt}")

    // --- 5. UniqueIndex enforcement ---
    println("\n--- 5. UniqueIndex ---")
    try {
        orm.insert(BlogPost::class).setSource(
            BlogPost(title = "Duplicate", content = "...", authorEmail = "author@example.com")
        ).executeAffrows()
        println("ERROR: Should have failed!")
    } catch (e: Exception) {
        println("Correctly rejected duplicate: ${e.message?.take(50)}...")
    }

    // --- 6. Column ordering (position) ---
    println("\n--- 6. Column Order (by position) ---")
    val blogTable = orm.dbFirst.getTables().find { it.name == "blog_posts" }
    blogTable?.columns?.forEach { col ->
        println("  ${col.name}: ${col.dbType} (nullable=${col.isNullable})")
    }

    // --- 7. Fluent API configuration ---
    println("\n--- 7. Fluent API ---")
    data class FluentDemo(val id: Int = 0, val label: String = "")
    cf.configEntity(FluentDemo::class) {
        tableName = "fluent_demo_table"
        column("label") {
            dbName = "display_label"
            stringLength = 500
        }
    }
    val fluentInfo = cf.buildTableInfo(FluentDemo::class)
    println("Fluent table name: ${fluentInfo.dbName}")
    println("Fluent column: ${fluentInfo.columnsByCs["label"]?.dbName}")

    // --- 8. Name conversion ---
    println("\n--- 8. Name Conversion ---")
    println("isSyncStructureToLower: ${cf.isSyncStructureToLower}")
    println("isSyncStructureToUpper: ${cf.isSyncStructureToUpper}")
    // When enabled, all table/column names are lowercased/uppercased in DDL

    // Cleanup
    (orm as SqliteProvider).close()
    println("\nDone!")
}
