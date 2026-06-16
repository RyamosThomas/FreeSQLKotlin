package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 1: Entity Basics
// Demonstrates: @Table, @Column, @Index, @Navigate annotations, data classes
// Port of: FreeSql README entity definitions
// =============================================================================

// --- Entity Definitions ---

@Table(name = "songs")
data class Song(
	@Column(isPrimary = true, isIdentity = true)
	val id: Int = 0,

	@Column(stringLength = 200, isNullable = false)
	val title: String = "",

	@Column(stringLength = 500)
	val url: String = "",

	@Column(name = "created_at")
	val createdAt: String = "",

	@Column(name = "user_id", isNullable = true)
	val userId: Int? = null
)
{
	// Navigation property (not persisted — marked @Transient via var)
	@Navigate(bind = "userId")
	@Transient
	var user: User? = null

	@Transient
	var tags: List<Tag> = emptyList()
}

@Table(name = "tags")
data class Tag(
	@Column(isPrimary = true, isIdentity = true)
	val id: Int = 0,

	@Column(stringLength = 100)
	val name: String = "",

	@Column(name = "parent_id", isNullable = true)
	val parentId: Int? = null
)

@Table(name = "song_tags")
data class SongTag(
	@Column(name = "song_id", isPrimary = true)
	val songId: Int = 0,

	@Column(name = "tag_id", isPrimary = true)
	val tagId: Int = 0
)

@Table(name = "users")
data class User(
	@Column(isPrimary = true, isIdentity = true)
	val id: Int = 0,

	@Column(stringLength = 100)
	val name: String = "",

	@Column(stringLength = 200, uniqueIndex = true)
	val email: String = "",

	val age: Int = 0,

	@Column(name = "is_active")
	val isActive: Boolean = true
)

// --- Type-safe column references (DSL) ---

object Songs : TableColumns<Song>(Song::class)
{
	val id = int("id")
	val title = varchar("title", 200)
	val url = varchar("url", 500)
	val createdAt = varchar("created_at", 50)
	val userId = int("user_id")
}

object Tags : TableColumns<Tag>(Tag::class)
{
	val id = int("id")
	val name = varchar("name", 100)
	val parentId = int("parent_id")
}

object Users : TableColumns<User>(User::class)
{
	val id = int("id")
	val name = varchar("name", 100)
	val email = varchar("email", 200)
	val age = int("age")
	val isActive = boolean("is_active")
}

// --- Main demo ---

fun main()
{
	// Build FreeSql instance
	val orm = FreeSqlBuilder()
		.useDataType("sqlite")
		.useConnectionString("jdbc:sqlite::memory:")
		.useAutoSyncStructure()
		.build()

	println("=== Entity Basics Example ===\n")

	// --- INSERT ---
	println("--- Insert ---")
	orm.insert(User::class).setSource(
		User(name = "Alice", email = "alice@example.com", age = 30, isActive = true)
	).executeAffrows()
	orm.insert(User::class).setSource(
		User(name = "Bob", email = "bob@example.com", age = 25, isActive = true)
	).executeAffrows()
	orm.insert(User::class).setSource(
		User(name = "Charlie", email = "charlie@example.com", age = 35, isActive = false)
	).executeAffrows()
	println("Inserted 3 users")

	orm.insert(Song::class).setSource(
		Song(title = "Yesterday", url = "https://example.com/yesterday", userId = 1)
	).executeAffrows()
	orm.insert(Song::class).setSource(
		Song(title = "Imagine", url = "https://example.com/imagine", userId = 1)
	).executeAffrows()
	orm.insert(Song::class).setSource(
		Song(title = "Let It Be", url = "https://example.com/letitbe", userId = 2)
	).executeAffrows()
	println("Inserted 3 songs")

	// --- SELECT with DSL ---
	println("\n--- Select with DSL ---")
	val activeUsers = orm.select<User>()
	    .whereExpr(Users.isActive eq true)
	    .orderBy(Users.id, SortDirection.DESC)
	    .toList()
	println("Active users: ${activeUsers.map { it.name }}")

	// --- SELECT with pagination ---
	println("\n--- Pagination ---")
	val page1 = orm.select<User>()
		.orderBy(Users.id)
		.page(1, 2)
		.toList()
	println("Page 1 (size 2): ${page1.map { it.name }}")

	// --- SELECT with conditional WHERE ---
	println("\n--- Conditional WhereIf ---")
	val keyword: String? = "li"
	val filtered = orm.select<User>()
	    .whereExpr(Users.isActive eq true)
	    .let { if (keyword != null) it.whereExpr(Users.name contains keyword) else it }
	    .toList()
	println("Filtered (name contains '$keyword'): ${filtered.map { it.name }}")

	// --- AGGREGATES ---
	println("\n--- Aggregates ---")
	val count = orm.select<User>().count()
	val avgAge = orm.select<User>().avg(Users.age)
	println("User count: $count, Avg age: $avgAge")

	// --- UPDATE ---
	println("\n--- Update ---")
	orm.update<User>()
		.set(Users.name, "Alice Updated")
		.whereExpr(Users.id eq 1)
		.executeAffrows()
	val updated = orm.select<User>().whereExpr(Users.id eq 1).first()
	println("Updated user: ${updated?.name}")

	// --- DELETE ---
	println("\n--- Delete ---")
	orm.delete<User>()
		.whereExpr(Users.isActive eq false)
		.executeAffrows()
	val remaining = orm.select<User>().count()
	println("Users after delete: $remaining")

	// --- TO SQL PREVIEW ---
	println("\n--- SQL Preview ---")
	val sql = orm.select<User>()
	    .whereExpr(Users.age gt 20)
	    .orderBy(Users.id, SortDirection.DESC)
	    .page(1, 10)
		.toSql()
	println("Generated SQL: $sql")

	// Cleanup
	(orm as SqliteProvider).close()
	println("\nDone!")
}
