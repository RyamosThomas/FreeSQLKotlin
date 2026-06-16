package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.extensions.*
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 5: UnitOfWork (Multi-Repository Transactions)
// Demonstrates: UnitOfWork, multiple repositories in one transaction
// Port of: FreeSql UnitOfWork + UnitOfWorkManager
// =============================================================================

// --- Entities ---

@Table(name = "customers")
data class Customer(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 100)
    val name: String = "",

    @Column(stringLength = 200)
    val email: String = ""
)

@Table(name = "invoices")
data class Invoice(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(name = "customer_id")
    val customerId: Int = 0,

    val amount: Double = 0.0,

    @Column(name = "is_paid")
    val isPaid: Boolean = false
)

@Table(name = "payments")
data class Payment(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(name = "invoice_id")
    val invoiceId: Int = 0,

    val amount: Double = 0.0,

    @Column(name = "paid_at", serverTime = ServerTimeType.INSERT)
    val paidAt: String = ""
)

// --- Main demo ---

fun main() {
    val orm = FreeSqlBuilder()
        .useDataType("sqlite")
        .useConnectionString("jdbc:sqlite::memory:")
        .useAutoSyncStructure()
        .build()

    println("=== UnitOfWork Example ===\n")

    // --- Basic UnitOfWork ---
    println("--- UnitOfWork with multiple repos ---")
    val uow = UnitOfWork(orm)

    val customerRepo = uow.getRepository<Customer>()
    val invoiceRepo = uow.getRepository<Invoice>()
    val paymentRepo = uow.getRepository<Payment>()

    // All operations in one transaction
    customerRepo.insert(Customer(name = "Acme Corp", email = "acme@example.com"))
    invoiceRepo.insert(Invoice(customerId = 1, amount = 1500.00, isPaid = false))
    invoiceRepo.insert(Invoice(customerId = 1, amount = 2300.00, isPaid = false))
    paymentRepo.insert(Payment(invoiceId = 1, amount = 1500.00))

    println("Customers: ${customerRepo.count()}")
    println("Invoices: ${invoiceRepo.count()}")
    println("Payments: ${paymentRepo.count()}")

    // --- Demonstrate reified type ---
    println("\n--- Reified type ---")
    val customerRepo2 = uow.getRepository<Customer>()
    val allCustomers = customerRepo2.select().toList()
    println("All customers: ${allCustomers.map { it.name }}")

    // --- Transactional workflow ---
    println("\n--- Transactional workflow: Pay an invoice ---")
    val uow2 = UnitOfWork(orm)
    uow2.getRepository<Payment>().insert(Payment(invoiceId = 2, amount = 2300.00))
    // In real usage, the transaction would commit here
    println("Payment recorded for invoice 2")

    // --- Verify state ---
    println("\n--- Final State ---")
    orm.select<Customer>().toList().forEach { println("  Customer: ${it.name}") }
    orm.select<Invoice>().toList().forEach { println("  Invoice #${it.id}: $${it.amount} (paid: ${it.isPaid})") }
    orm.select<Payment>().toList().forEach { println("  Payment #${it.id}: $${it.amount}") }

    // Cleanup
    (orm as SqliteProvider).close()
    println("\nDone!")
}
