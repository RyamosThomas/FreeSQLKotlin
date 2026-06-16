package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.extensions.*
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 3: Repository Pattern
// Demonstrates: IBaseRepository, BaseRepository, custom repositories, DI-like usage
// Port of: FreeSql.Repository
// =============================================================================

// --- Entity ---

@Table(name = "articles")
data class Article(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 300)
    val title: String = "",

    val content: String = "",

    @Column(name = "author_id")
    val authorId: Int = 0,

    @Column(name = "is_deleted")
    val isDeleted: Boolean = false,

    @Column(name = "view_count")
    val viewCount: Int = 0
)

// DSL columns
object Articles : TableColumns<Article>(Article::class) {
    val id = int("id")
    val title = varchar("title", 300)
    val content = text("content")
    val authorId = int("author_id")
    val isDeleted = boolean("is_deleted")
    val viewCount = int("view_count")
}

// --- Custom Repository ---

/**
 * Custom repository with domain-specific queries.
 * Extends BaseRepository with business logic.
 */
class ArticleRepository(orm: IFreeSql) : BaseRepository<Article>(Article::class, orm) {

    /** Find articles by author, excluding soft-deleted. */
    fun findByAuthor(authorId: Int): List<Article> {
        return select()
            .whereExpr(
                AndExpr(
                    Articles.authorId eq authorId,
                    Articles.isDeleted eq false
                )
            )
            .orderBy(Articles.id, SortDirection.DESC)
            .toList()
    }

    /** Soft delete an article. */
    fun softDelete(id: Int): Int {
        return orm.update(entityType)
            .set(Articles.isDeleted, true)
            .whereExpr(Articles.id eq id)
            .executeAffrows()
    }

    /** Increment view count. */
    fun incrementViews(id: Int): Int {
        return orm.executeNonQuery(
            "UPDATE articles SET view_count = view_count + 1 WHERE id = ?",
            listOf(id)
        )
    }

    /** Search by title keyword. */
    fun searchByTitle(keyword: String): List<Article> {
        return select()
            .whereExpr(Articles.title contains keyword)
            .whereExpr(Articles.isDeleted eq false)
            .toList()
    }
}

// --- Main demo ---

fun main() {
    val orm = FreeSqlBuilder()
        .useDataType("sqlite")
        .useConnectionString("jdbc:sqlite::memory:")
        .useAutoSyncStructure()
        .build()

    println("=== Repository Pattern Example ===\n")

    // --- Standard Repository ---
    println("--- Standard Repository ---")
    val articleRepo = orm.repository<Article>()

    articleRepo.insert(Article(title = "Getting Started with Kotlin", content = "Kotlin is great...", authorId = 1))
    articleRepo.insert(Article(title = "Advanced Kotlin Tips", content = "Here are some tips...", authorId = 1))
    articleRepo.insert(Article(title = "FreeSql for Kotlin", content = "An ORM port...", authorId = 2))
    articleRepo.insert(Article(title = "Soft Delete Patterns", content = "Let's discuss...", authorId = 2, isDeleted = true))

    println("Total articles: ${articleRepo.count()}")
    println("Has any? ${articleRepo.any()}")

    // Select with filter
    val author1Articles = articleRepo.select()
        .whereExpr(Articles.authorId eq 1)
        .toList()
    println("Author 1 articles: ${author1Articles.map { it.title }}")

    // Delete where
    articleRepo.deleteWhere(Articles.isDeleted eq true)
    println("After deleting soft-deleted: ${articleRepo.count()} articles")

    // --- Custom Repository ---
    println("\n--- Custom Repository ---")
    val customRepo = ArticleRepository(orm)

    // Reset data
    orm.delete<Article>().executeAffrows()
    orm.insert(Article::class).setSource(listOf(
        Article(title = "Kotlin Coroutines", content = "Deep dive...", authorId = 1),
        Article(title = "Kotlin DSL", content = "Building DSLs...", authorId = 1),
        Article(title = "SQLite Best Practices", content = "Optimize...", authorId = 2),
        Article(title = "Deleted Article", content = "Gone...", authorId = 1, isDeleted = true)
    )).executeAffrows()

    // Custom find by author
    val author1 = customRepo.findByAuthor(1)
    println("Author 1 (excl deleted): ${author1.map { it.title }}")

    // Search
    val searchResults = customRepo.searchByTitle("Kotlin")
    println("Search 'Kotlin': ${searchResults.map { it.title }}")

    // Soft delete
    customRepo.softDelete(1)
    val afterSoftDelete = customRepo.findByAuthor(1)
    println("Author 1 after soft delete id=1: ${afterSoftDelete.map { it.title }}")

    // Increment views
    customRepo.incrementViews(2)
    val article = orm.select<Article>().whereExpr(Articles.id eq 2).first()
    println("Article 2 view count: ${article?.viewCount}")

    // Cleanup
    (orm as SqliteProvider).close()
    println("\nDone!")
}
