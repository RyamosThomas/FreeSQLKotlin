package freesql

import freesql.annotation.*
import freesql.core.*

// ========================================
// Shared test entity definitions
// ========================================

@Table(name = "users")
@Index(name = "ix_users_email", fields = "email", isUnique = true)
@Index(name = "ix_users_name", fields = "name")
data class TestUser(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    @Column(stringLength = 100)
    val name: String = "",
    @Column(stringLength = 255)
    val email: String = "",
    @Column(isNullable = true)
    val age: Int? = null,
    @Column(name = "is_active")
    val isActive: Boolean = true,
    @Column(name = "created_at")
    val createdAt: String = ""
)

@Table(name = "posts")
@Index(name = "ix_posts_user_id", fields = "user_id")
data class TestPost(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    @Column(stringLength = 200)
    val title: String = "",
    val content: String = "",
    @Column(name = "user_id")
    val userId: Int = 0,
    @Column(name = "view_count", isNullable = true)
    val viewCount: Int? = 0
) {
    @Navigate(bind = "userId")
    @Transient
    var user: TestUser? = null
}

@Table(name = "categories")
data class TestCategory(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    @Column(stringLength = 100)
    val name: String = "",
    @Column(name = "parent_id", isNullable = true)
    val parentId: Int? = null
)

// ========================================
// Type-safe column definitions (DSL)
// ========================================

object TestUsers : TableColumns<TestUser>(TestUser::class) {
    val id = int("id")
    val name = varchar("name", 100)
    val email = varchar("email", 255)
    val age = int("age")
    val isActive = boolean("is_active")
    val createdAt = varchar("created_at")
}

object TestPosts : TableColumns<TestPost>(TestPost::class) {
    val id = int("id")
    val title = varchar("title", 200)
    val content = varchar("content")
    val userId = int("user_id")
    val viewCount = int("view_count")
}

object TestCategories : TableColumns<TestCategory>(TestCategory::class) {
    val id = int("id")
    val name = varchar("name", 100)
    val parentId = int("parent_id")
}
