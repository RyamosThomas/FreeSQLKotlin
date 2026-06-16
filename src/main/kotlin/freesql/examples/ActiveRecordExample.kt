package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.extensions.ActiveCompanion
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 2: Active Record Pattern (BaseEntity)
// Demonstrates: ActiveCompanion for static CRUD methods
// Port of: FreeSql.Extensions.BaseEntity
// =============================================================================

// --- Entity with ActiveCompanion ---

@Table(name = "products")
data class Product(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 200)
    val name: String = "",

    val price: Double = 0.0,

    @Column(name = "stock_qty")
    val stockQty: Int = 0,

    @Column(name = "is_available")
    val isAvailable: Boolean = true
) {
    companion object : ActiveCompanion<Product, Int>(Product::class)
}

@Table(name = "orders")
data class Order(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(name = "product_id")
    val productId: Int = 0,

    val quantity: Int = 0,

    @Column(name = "total_price")
    val totalPrice: Double = 0.0,

    @Column(name = "created_at", serverTime = ServerTimeType.INSERT)
    val createdAt: String = ""
) {
    companion object : ActiveCompanion<Order, Int>(Order::class)
}

// --- DSL columns ---
object Products : TableColumns<Product>(Product::class) {
    val id = int("id")
    val name = varchar("name", 200)
    val price = double("price")
    val stockQty = int("stock_qty")
    val isAvailable = boolean("is_available")
}

object Orders : TableColumns<Order>(Order::class) {
    val id = int("id")
    val productId = int("product_id")
    val quantity = int("quantity")
    val totalPrice = double("total_price")
    val createdAt = varchar("created_at", 50)
}

// --- Main demo ---

fun main() {
    val orm = FreeSqlBuilder()
        .useDataType("sqlite")
        .useConnectionString("jdbc:sqlite::memory:")
        .useAutoSyncStructure()
        .build()

    // Initialize companions
    Product.init(orm)
    Order.init(orm)

    println("=== Active Record (BaseEntity) Example ===\n")

    // --- INSERT via companion ---
    println("--- Insert via companion ---")
    Product.insert(Product(name = "Laptop", price = 999.99, stockQty = 50))
    Product.insert(Product(name = "Mouse", price = 29.99, stockQty = 200))
    Product.insert(Product(name = "Keyboard", price = 79.99, stockQty = 150))
    Product.insert(Product(name = "Monitor", price = 499.99, stockQty = 0))
    println("Inserted ${Product.count()} products")

    // --- FIND by ID ---
    println("\n--- Find by ID ---")
    val laptop = Product.find(1)
    println("Found: ${laptop?.name} @ $${laptop?.price}")

    // --- SELECT ALL ---
    println("\n--- Select All ---")
    val allProducts = Product.selectAll().toList()
    allProducts.forEach { println("  ${it.name}: $${it.price} (stock: ${it.stockQty})") }

    // --- SELECT with filter ---
    println("\n--- Filter: price > 50 ---")
    val expensive = Product.selectAll()
        .whereExpr(Products.price gt 50.0)
        .toList()
    println("Expensive: ${expensive.map { it.name }}")

    // --- COUNT ---
    println("\n--- Count ---")
    println("Total products: ${Product.count()}")
    println("Any products? ${Product.any()}")

    // --- BATCH INSERT ---
    println("\n--- Batch Insert ---")
    Order.insertBatch(listOf(
        Order(productId = 1, quantity = 2, totalPrice = 1999.98),
        Order(productId = 2, quantity = 5, totalPrice = 149.95),
        Order(productId = 3, quantity = 1, totalPrice = 79.99)
    ))
    println("Inserted ${Order.count()} orders")

    // --- DELETE ---
    println("\n--- Delete unavailable products ---")
    val unavailable = Product.selectAll()
        .whereExpr(Products.stockQty eq 0)
        .toList()
    unavailable.forEach { Product.delete(it) }
    println("Remaining products: ${Product.count()}")

    // --- AGGREGATES ---
    println("\n--- Aggregates ---")
    val totalRevenue = Order.selectAll().sum(Orders.totalPrice)
    val avgOrderValue = Order.selectAll().avg(Orders.totalPrice)
    println("Total revenue: $${totalRevenue}")
    println("Avg order value: $${avgOrderValue}")

    // Cleanup
    (orm as SqliteProvider).close()
    println("\nDone!")
}
