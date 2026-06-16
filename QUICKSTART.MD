# FreeSQL Kotlin — Quick Start Guide

## 1. Add the Dependency

Since this library isn't published to Maven Central yet, you'd use a local dependency.

### Option A — JitPack (if the repo is on GitHub)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.YOUR_USER:FreeSQLKotlin:0.1.0")
}
```

### Option B — Local JAR (build + include manually)

```bash
# In the FreeSQLKotlin repo:
./gradlew build
# Copy build/libs/FreeSQLKotlin-0.1.0.jar into your project's libs/ folder
```

```kotlin
// In your project's build.gradle.kts:
dependencies {
    implementation(files("libs/FreeSQLKotlin-0.1.0.jar"))
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation(kotlin("reflect"))
}
```

### Option C — Composite build (best for development)

```kotlin
// In your consuming project's settings.gradle.kts:
includeBuild("../FreeSQLKotlin") {
    dependencySubstitution {
        substitute(module("io.freesql:freesql-kotlin"))
            .using(project(":"))
    }
}
```

---

## 2. Define Your Entities

```kotlin
import freesql.annotation.*

@Table(name = "users")
data class User(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    @Column(stringLength = 100)
    val name: String = "",

    @Column(stringLength = 255, isNullable = false)
    val email: String = "",

    @Column(isNullable = true)
    val age: Int? = null,

    @Column(name = "is_active")
    val isActive: Boolean = true
)

@Table(name = "posts")
data class Post(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,

    val title: String = "",

    @Column(name = "user_id")
    val userId: Int = 0
)
```

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@Table(name = "...")` | Maps class to a table name |
| `@Column(...)` | Column config: `isPrimary`, `isIdentity`, `stringLength`, `isNullable`, `name` |
| `@Index(name, fields)` | Table-level index (put on the class) |
| `@Navigate(bind = "fk")` | Navigation/relationship property |

---

## 3. Initialize the ORM

```kotlin
import freesql.freeSql

val orm = freeSql {
    useDataType("sqlite")
    useConnectionString("jdbc:sqlite:myapp.db")
    useAutoSyncStructure()       // auto-create/alter tables from entities
}

// OR for in-memory (testing):
val orm = freeSql {
    useDataType("sqlite")
    useConnectionString("jdbc:sqlite::memory:")
    useAutoSyncStructure()
}
```

**Builder options:**

| Option | Effect |
|---|---|
| `useDataType("sqlite")` | Database type (currently only SQLite) |
| `useConnectionString("jdbc:sqlite:...")` | Required. JDBC connection string |
| `useAutoSyncStructure()` | Auto-migrate tables on first query |
| `useSyncStructureToLower()` | Lowercase DDL names |
| `useSyncStructureToUpper()` | Uppercase DDL names |

---

## 4. Run Migrations

```kotlin
// Explicit migration — call once at startup
orm.codeFirst.syncStructure(User::class, Post::class)

// Or let auto-sync handle it (if useAutoSyncStructure() was set):
// Just start querying — tables get created/updated automatically.

// Preview the DDL without executing:
val ddl = orm.codeFirst.getComparisonDDLStatements(User::class)
println(ddl)
```

---

## 5. CRUD Operations

### Insert

```kotlin
// Single insert
orm.insert<User>()
    .setSource(User(name = "Alice", email = "alice@test.com", age = 30))
    .executeAffrows()            // returns rows affected

// Insert and get the generated ID back
val newId = orm.insert<User>()
    .setSource(User(name = "Bob", email = "bob@test.com"))
    .executeIdentity()           // returns the auto-generated ID

// Batch insert
orm.insert<User>()
    .setSource(listOf(
        User(name = "Charlie", email = "c@test.com"),
        User(name = "Diana", email = "d@test.com")
    ))
    .executeAffrows()
```

### Select

```kotlin
// All rows
val allUsers = orm.select<User>().toList()

// Count / exists
val count = orm.select<User>().count()
val hasAny = orm.select<User>().any()

// First result
val first = orm.select<User>().first()

// WHERE (string-based)
val active = orm.select<User>()
    .where("is_active = ?", true)
    .toList()

// WHERE (type-safe DSL — requires a TableColumns object, see section 6)
val found = orm.select<User>()
    .whereExpr(Users.name eq "Alice")
    .first()
```

### Update

```kotlin
orm.update<User>()
    .setSource(found.copy(name = "Alice Updated"))
    .where("id = ?", found.id)
    .executeAffrows()
```

### Delete

```kotlin
orm.delete<User>()
    .where("id = ?", 42)
    .executeAffrows()
```

---

## 6. Type-Safe Column DSL (Optional but Recommended)

Define column references alongside your entities:

```kotlin
import freesql.core.*

object Users : TableColumns<User>(User::class) {
    val id = int("id")
    val name = varchar("name", 100)
    val email = varchar("email", 255)
    val age = int("age")
    val isActive = boolean("is_active")
}
```

Then use in queries:

```kotlin
// Equality
orm.select<User>()
    .whereExpr(Users.name eq "Alice")
    .toList()

// Compound expressions
orm.select<User>()
    .whereExpr(Users.age gt 25 and (Users.isActive eq true))
    .orderByExpr(Users.name.asc())
    .toList()
```

---

## 7. Extensions — Three Flavors

### a) Active Record Pattern (`ActiveCompanion`)

```kotlin
data class User(
    val id: Int = 0,
    val name: String = ""
) {
    companion object : ActiveCompanion<User, Int>(User::class)
}

// Init once at startup:
User.init(orm)

// Then static CRUD:
User.find(1)
User.insert(User(name = "Alice"))
User.selectAll().toList()
User.count()
User.deleteById(1)
```

### b) Repository Pattern

```kotlin
val userRepo = orm.repository<User>()

userRepo.insert(User(name = "Alice"))
userRepo.find(1)
userRepo.select().toList()
userRepo.count()
userRepo.delete(user)
```

### c) DbContext / Unit of Work

```kotlin
val ctx = DbContext(orm)
ctx.set<User>().add(User(name = "Alice"))
ctx.set<User>().add(User(name = "Bob"))
ctx.saveChanges()   // all inserts in one transaction
```

---

## 8. Transactions

```kotlin
orm.transaction {
    orm.insert<User>().setSource(User(name = "A")).executeAffrows()
    orm.insert<User>().setSource(User(name = "B")).executeAffrows()
    // if anything throws, both roll back
}
```

---

## 9. Raw SQL

```kotlin
// Execute a non-query statement
val rows = orm.executeNonQuery(
    "UPDATE users SET age = ? WHERE name = ?",
    31, "Alice"
)

// Low-level ADO access (direct JDBC)
val ado = orm.ado
```

---

## 10. Shutdown

When done, cast and close to release the SQLite connection:

```kotlin
(orm as? freesql.provider.sqlite.SqliteProvider)?.close()
```

---

## Full Minimal Example

```kotlin
import freesql.*
import freesql.annotation.*

@Table(name = "todos")
data class Todo(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    val title: String = "",
    val done: Boolean = false
)

fun main() {
    val orm = freeSql {
        useDataType("sqlite")
        useConnectionString("jdbc:sqlite:todo.db")
        useAutoSyncStructure()
    }

    // Seed
    orm.insert<Todo>().setSource(Todo(title = "Buy milk")).executeAffrows()
    orm.insert<Todo>().setSource(Todo(title = "Write code")).executeAffrows()

    // Query
    val todos = orm.select<Todo>().toList()
    todos.forEach { println("[${if (it.done) "x" else " "}] ${it.title}") }

    // Update
    val first = todos.first()
    orm.update<Todo>()
        .setSource(first.copy(done = true))
        .where("id = ?", first.id)
        .executeAffrows()

    (orm as? freesql.provider.sqlite.SqliteProvider)?.close()
}
```
