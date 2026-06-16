# FreeSql Kotlin

A Kotlin port of [FreeSql](https://github.com/dotnetcore/FreeSql) — a powerful ORM component for SQLite.

## Features

- **Full CRUD** — Insert, Select, Update, Delete with type-safe DSL
- **Expression DSL** — ColumnRef-based expressions (eq, gt, contains, dateDiff, etc.)
- **CodeFirst** — Auto schema migration, rename detection, migration history
- **DbFirst** — Introspect existing databases
- **Repository Pattern** — IBaseRepository with CRUD + custom repositories
- **Active Record** — ActiveCompanion for static CRUD via companion objects
- **DbContext** — Change tracking with SaveChanges
- **DbSet** — Entity state tracking (Added/Modified/Removed)
- **UnitOfWork** — Multi-repository transactions
- **Global Filters** — Named filters per entity type
- **AOP** — CurdBefore/CurdAfter interceptors
- **Subqueries** — EXISTS, ANY/ALL, FromQuery
- **Dynamic Filters** — JSON-serializable filter model
- **Custom Functions** — ExpressionCallRegistry for custom SQL
- **Annotations** — @Table, @Column, @Index, @Navigate, @ExpressionCall

## Quick Start

```kotlin
import freesql.core.*
import freesql.annotation.*

// Define entity
@Table(name = "users")
data class User(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
    val age: Int = 0
)

// Type-safe columns
object Users : TableColumns<User>(User::class) {
    val id = int("id")
    val name = varchar("name", 100)
    val email = varchar("email", 255)
    val age = int("age")
}

// Build ORM
val orm = FreeSqlBuilder()
    .useDataType("sqlite")
    .useConnectionString("jdbc:sqlite:myapp.db")
    .useAutoSyncStructure()
    .build()

// Insert
orm.insert<User>().setSource(
    User(name = "Alice", email = "alice@example.com", age = 30)
).executeAffrows()

// Query with DSL
val users = orm.select<User>()
    .whereExpr(Users.age gt 18)
    .orderByDesc(Users.name)
    .page(1, 10)
    .toList()

// Update
orm.update<User>()
    .set(Users.name, "Alice Updated")
    .whereExpr(Users.id eq 1)
    .executeAffrows()

// Delete
orm.delete<User>()
    .whereExpr(Users.age lt 13)
    .executeAffrows()
```

## Patterns

### Active Record

```kotlin
@Table(name = "products")
data class Product(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    val name: String = "",
    val price: Double = 0.0
) {
    companion object : ActiveCompanion<Product, Int>(Product::class)
}

// Initialize
Product.init(orm)

// Use
Product.insert(Product(name = "Widget", price = 9.99))
val widget = Product.find(1)
val expensive = Product.selectAll().whereExpr(Products.price gt 50.0).toList()
Product.deleteById(1)
```

### Repository

```kotlin
val repo = orm.repository<User>()
repo.insert(User(name = "Bob", email = "bob@example.com"))
val count = repo.count()
repo.deleteWhere(Users.age lt 13)
```

### DbContext (Change Tracking)

```kotlin
val ctx = DbContext(orm)
ctx.set<User>().add(User(name = "Charlie"))
ctx.set<User>().update(existingUser.copy(name = "Updated"))
ctx.set<User>().remove(oldUser)
ctx.saveChanges() // All in one transaction
```

### UnitOfWork

```kotlin
val uow = UnitOfWork(orm)
uow.getRepository<User>().insert(User(name = "Diana"))
uow.getRepository<Post>().insert(Post(title = "Hello"))
// Changes committed in transaction
```

## Examples

See `examples/src/main/kotlin/freesql/examples/`:

| File | Description |
|------|-------------|
| `EntityBasicsExample.kt` | Entity annotations, CRUD, DSL queries, pagination |
| `ActiveRecordExample.kt` | ActiveCompanion pattern, static CRUD |
| `RepositoryExample.kt` | Repository pattern, custom repositories |
| `DbContextExample.kt` | Change tracking, SaveChanges, discard |
| `UnitOfWorkExample.kt` | Multi-repository transactions |
| `AdvancedQueriesExample.kt` | Subqueries, DynamicFilter, ExpressionCall, UNION |
| `CodeFirstExample.kt` | Schema migration, fluent API, serverTime |

## Annotations

| Annotation | Target | Key Parameters |
|------------|--------|----------------|
| `@Table` | Class | name, oldName, disableSyncStructure, asTable |
| `@Column` | Property | name, isPrimary, isIdentity, isNullable, stringLength, dbType, oldName, canInsert, canUpdate, serverTime, position, uniqueIndex, mapType |
| `@Index` | Class | name, fields, isUnique |
| `@Navigate` | Property | bind, manyToMany |
| `@ExpressionCall` | Class | (marker) |

## Test Count

**497 tests, all passing**

## Building

```bash
./gradlew build
./gradlew test
```

## License

MIT

## AI Disclosure
💩 100% AI Slop 💩
this was used as a quick test of the MiMo-V2.5-Pro-UltraSpeed model, execution time was around 1 hour and was 90 million tokens total.
