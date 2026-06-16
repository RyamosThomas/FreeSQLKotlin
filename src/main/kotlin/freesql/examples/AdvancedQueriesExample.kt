package freesql.examples

import freesql.annotation.*
import freesql.core.*
import freesql.FreeSqlBuilder
import freesql.provider.sqlite.SqliteProvider

// =============================================================================
// Example 6: Advanced Queries
// Demonstrates: Joins, subqueries, aggregation, DynamicFilter, ExpressionCall,
//               includeMany, UnionAll, DateDiff, EXISTS/ANY/ALL
// Port of: FreeSql advanced query features
// =============================================================================

// --- Entities ---

@Table(name = "departments")
data class Department(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    @Column(stringLength = 100)
    val name: String = ""
)

@Table(name = "employees")
data class Employee(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    @Column(stringLength = 100)
    val name: String = "",
    @Column(name = "dept_id")
    val deptId: Int = 0,
    val salary: Double = 0.0,
    @Column(name = "hired_at")
    val hiredAt: String = ""
)

@Table(name = "projects")
data class Project(
    @Column(isPrimary = true, isIdentity = true)
    val id: Int = 0,
    @Column(stringLength = 200)
    val name: String = "",
    @Column(name = "lead_id", isNullable = true)
    val leadId: Int? = null
)

// DSL
object Departments : TableColumns<Department>(Department::class) {
    val id = int("id"); val name = varchar("name", 100)
}
object Employees : TableColumns<Employee>(Employee::class) {
    val id = int("id"); val name = varchar("name", 100)
    val deptId = int("dept_id"); val salary = double("salary")
    val hiredAt = varchar("hired_at", 50)
}
object Projects : TableColumns<Project>(Project::class) {
    val id = int("id"); val name = varchar("name", 200)
    val leadId = int("lead_id")
}

// --- Main demo ---

fun main() {
    val orm = FreeSqlBuilder()
        .useDataType("sqlite")
        .useConnectionString("jdbc:sqlite::memory:")
        .useAutoSyncStructure()
        .build()

    println("=== Advanced Queries Example ===\n")

    // Seed data
    orm.insert(Department::class).setSource(listOf(
        Department(name = "Engineering"),
        Department(name = "Marketing"),
        Department(name = "Sales")
    )).executeAffrows()

    orm.insert(Employee::class).setSource(listOf(
        Employee(name = "Alice", deptId = 1, salary = 120000.0, hiredAt = "2020-01-15"),
        Employee(name = "Bob", deptId = 1, salary = 110000.0, hiredAt = "2021-03-20"),
        Employee(name = "Charlie", deptId = 2, salary = 95000.0, hiredAt = "2019-06-01"),
        Employee(name = "Diana", deptId = 2, salary = 105000.0, hiredAt = "2020-09-10"),
        Employee(name = "Eve", deptId = 3, salary = 85000.0, hiredAt = "2022-01-05"),
        Employee(name = "Frank", deptId = 3, salary = 90000.0, hiredAt = "2021-11-15")
    )).executeAffrows()

    orm.insert(Project::class).setSource(listOf(
        Project(name = "Project Alpha", leadId = 1),
        Project(name = "Project Beta", leadId = 3),
        Project(name = "Project Gamma", leadId = null)
    )).executeAffrows()

    // --- 1. WHERE with DSL ---
    println("--- 1. WHERE with DSL ---")
    val highEarners = orm.select<Employee>()
        .whereExpr(Employees.salary gt 100000.0)
        .orderBy(Employees.salary, SortDirection.DESC)
        .toList()
    println("High earners: ${highEarners.map { "${it.name} ($${it.salary})" }}")

    // --- 2. Pagination ---
    println("\n--- 2. Pagination ---")
    val page1 = orm.select<Employee>().orderBy(Employees.id).page(1, 2).toList()
    val page2 = orm.select<Employee>().orderBy(Employees.id).page(2, 2).toList()
    println("Page 1: ${page1.map { it.name }}")
    println("Page 2: ${page2.map { it.name }}")

    // --- 3. Aggregation ---
    println("\n--- 3. Aggregation ---")
    val empQuery = orm.select<Employee>()
    val empCount = empQuery.count()
    val empTotal = empQuery.sum(Employees.salary)
    val empAvg = empQuery.avg(Employees.salary)
    val empMin = empQuery.min(Employees.salary)
    val empMax = empQuery.max(Employees.salary)
    println("Stats: count=$empCount, total=$empTotal, avg=$empAvg, min=$empMin, max=$empMax")

    // --- 4. Group By ---
    println("\n--- 4. Group By ---")
    val deptStats = orm.ado.executeDataTable(
        "SELECT dept_id, AVG(salary) as avg_salary, COUNT(id) as headcount FROM employees GROUP BY dept_id"
    )
    deptStats.forEach { row ->
        println("  Dept ${row["dept_id"]}: avg=$${row["avg_salary"]}, count=${row["headcount"]}")
    }

    // --- 5. Raw SQL WHERE ---
    println("\n--- 5. Raw SQL ---")
    val rawResults = orm.select<Employee>()
        .where("salary > ? AND dept_id IN (?, ?)", 90000, 1, 2)
        .toList()
    println("Raw query: ${rawResults.map { it.name }}")

    // --- 6. Subquery: EXISTS ---
    println("\n--- 6. EXISTS subquery ---")
    val hasProject = orm.select<Project>().whereExpr(
        ColumnRef<Int>("lead_id") eq ColumnRef<Int>("employees.id")
    )
    val leads = orm.select<Employee>()
        .whereExpr(ColumnRef.exists(hasProject))
        .toList()
    println("Project leads: ${leads.map { it.name }}")

    // --- 7. ANY subquery ---
    println("\n--- 7. ANY subquery ---")
    val deptSalaries = orm.select<Employee>().selectColumns("salary")
    val aboveAny = orm.select<Employee>()
        .whereExpr(Employees.salary gtAny deptSalaries)
        .toList()
    // This finds employees whose salary is greater than ANY salary (i.e., not the minimum)
    println("Above any salary: ${aboveAny.map { it.name }}")

    // --- 8. DynamicFilterInfo ---
    println("\n--- 8. DynamicFilterInfo ---")
    val filter = DynamicFilterInfo(
        logic = "and",
        filters = listOf(
            DynamicFilterInfo(field = "salary", operator = DynamicFilterOperator.GreaterThan, value = 90000),
            DynamicFilterInfo(field = "dept_id", operator = DynamicFilterOperator.Equals, value = 1)
        )
    )
    val columns = mapOf(
        "salary" to Employees.salary,
        "dept_id" to Employees.deptId
    )
    val filterExpr = filter.toSqlExpr(columns)
    if (filterExpr != null) {
        val filtered = orm.select<Employee>().whereExpr(filterExpr).toList()
        println("Filtered (salary>90k AND dept=1): ${filtered.map { it.name }}")
    }

    // --- 9. ExpressionCallRegistry ---
    println("\n--- 9. Custom ExpressionCall ---")
    ExpressionCallRegistry.register("bonus") { args ->
        "(${args[0].qualified()} * 1.1)"
    }
    val bonusExpr = ExpressionCallRegistry.call("bonus", listOf(Employees.salary))
    val bonusSql = bonusExpr.toSql(null)
    println("Custom 'bonus' function SQL: $bonusSql")
    ExpressionCallRegistry.clear()

    // --- 10. DateDiff ---
    println("\n--- 10. DateDiff ---")
    val daysEmployed = ColumnRef.dateDiffDays(
        ColumnRef<String>("hired_at", "employees"),
        ColumnRef<String>("datetime('now')", "")
    )
    println("DateDiff SQL: ${daysEmployed.toSql(null)}")

    // --- 11. UNION ALL ---
    println("\n--- 11. UNION ALL ---")
    // unionAll requires same entity type; for cross-table unions use raw SQL:
    val unionResults = orm.ado.executeDataTable(
        "SELECT name, 'Employee' as type FROM employees UNION ALL SELECT name, 'Department' as type FROM departments"
    )
    println("Union results: $unionResults")

    // --- 12. toSql preview ---
    println("\n--- 12. SQL Preview ---")
    val complexSql = orm.select<Employee>()
        .whereExpr(Employees.salary gt 80000.0)
        .orderBy(Employees.salary, SortDirection.DESC)
        .page(1, 5)
        .toSql()
    println("Complex query: $complexSql")

    // Cleanup
    (orm as SqliteProvider).close()
    println("\nDone!")
}
