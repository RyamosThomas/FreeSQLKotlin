package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.extensions.*
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 4: DbContext (Change Tracking)
// Demonstrates: DbContext, DbSet, change tracking, SaveChanges
// Port of: FreeSql.DbContext
// =============================================================================

// --- Entities ---

@Table(name = "categories")
data class Category(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 100)
    val name: String = "",

    @Column(name = "parent_id", isNullable = true)
    val parentId: Int? = null
)

@Table(name = "items")
data class Item(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 200)
    val name: String = "",

    @Column(name = "category_id")
    val categoryId: Int = 0,

    val price: Double = 0.0,

    @Column(name = "is_active")
    val isActive: Boolean = true
)

// DSL
object Categories : TableColumns<Category>(Category::class) {
    val id = int("id")
    val name = varchar("name", 100)
    val parentId = int("parent_id")
}

object Items : TableColumns<Item>(Item::class) {
    val id = int("id")
    val name = varchar("name", 200)
    val categoryId = int("category_id")
    val price = double("price")
    val isActive = boolean("is_active")
}

// --- Main demo ---

fun main() {
    val orm = FreeSqlBuilder()
        .useDataType("sqlite")
        .useConnectionString("jdbc:sqlite::memory:")
        .useAutoSyncStructure()
        .build()

    println("=== DbContext (Change Tracking) Example ===\n")

    // --- Create DbContext ---
    val ctx = DbContext(orm)

    // --- ADD entities to DbSet ---
    println("--- Add to DbSet ---")
    ctx.set<Category>().add(Category(name = "Electronics"))
    ctx.set<Category>().add(Category(name = "Books"))
    ctx.set<Category>().add(Category(name = "Clothing"))
    println("Pending changes: ${ctx.set<Category>().pendingChanges()}")

    // Not saved yet!
    println("Categories in DB: ${orm.select<Category>().count()}")

    // --- SaveChanges ---
    println("\n--- SaveChanges ---")
    ctx.saveChanges()
    println("Categories in DB after save: ${orm.select<Category>().count()}")

    // --- Track existing entity for update ---
    println("\n--- Track for Update ---")
    val electronics = orm.select<Category>().whereExpr(Categories.name eq "Electronics").first()!!
    val ctx2 = DbContext(orm)
    ctx2.set<Category>().update(electronics.copy(name = "Consumer Electronics"))
    ctx2.saveChanges()

    val updated = orm.select<Category>().whereExpr(Categories.id eq electronics.id).first()
    println("Updated: ${updated?.name}")

    // --- Mixed operations in one transaction ---
    println("\n--- Mixed Operations (Add + Update + Remove) ---")
    val ctx3 = DbContext(orm)

    // Add items
    ctx3.set<Item>().addAll(listOf(
        Item(name = "Laptop", categoryId = 1, price = 999.99),
        Item(name = "Phone", categoryId = 1, price = 699.99),
        Item(name = "Novel", categoryId = 2, price = 14.99),
        Item(name = "T-Shirt", categoryId = 3, price = 24.99)
    ))

    // Save items first
    ctx3.saveChanges()
    println("Items: ${orm.select<Item>().count()}")

    // Now update + remove in one go
    val ctx4 = DbContext(orm)
    val phone = orm.select<Item>().whereExpr(Items.name eq "Phone").first()!!
    ctx4.set<Item>().update(phone.copy(price = 649.99))

    val novel = orm.select<Item>().whereExpr(Items.name eq "Novel").first()!!
    ctx4.set<Item>().remove(novel)

    println("Pending: ${ctx4.set<Item>().pendingChanges()} changes")
    ctx4.saveChanges()
    println("Items after save: ${orm.select<Item>().count()}")

    // --- Discard changes ---
    println("\n--- Discard Changes ---")
    val ctx5 = DbContext(orm)
    ctx5.set<Item>().add(Item(name = "Ghost Item", categoryId = 1, price = 0.0))
    println("Pending: ${ctx5.set<Item>().pendingChanges()}")
    ctx5.discardChanges()
    println("After discard: ${ctx5.set<Item>().pendingChanges()}")
    ctx5.saveChanges() // saves nothing
    println("Items in DB: ${orm.select<Item>().count()}")

    // --- Final state ---
    println("\n--- Final State ---")
    orm.select<Category>().toList().forEach { println("  Category: ${it.name}") }
    orm.select<Item>().toList().forEach { println("  Item: ${it.name} @ $${it.price}") }

    // Cleanup
    (orm as SqliteProvider).close()
    println("\nDone!")
}
