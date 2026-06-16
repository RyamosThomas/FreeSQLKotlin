# FreeSQL Kotlin — Feature Parity Tracker

Last updated: 2026-06-15

## 1. CORE CRUD

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| Insert (single, batch, identity)     | DONE   | |
| Insert with ColumnRef DSL            | DONE   | |
| Insert split-execute (large batches) | DONE   | |
| Insert NoneParameter mode            | DONE   | |
| Select (where, orderBy, skip/take)   | DONE   | |
| Select expression DSL                | DONE   | |
| Select Column projection             | DONE   | |
| Update (set, setRaw, setSource)      | DONE   | |
| Update setIf                         | DONE   | |
| Update whereDynamic                  | DONE   | |
| Delete (expression, raw SQL)         | DONE   | |
| InsertOrUpdate (upsert)              | DONE   | |
| Transactions                         | DONE   | |
| SQL preview (toSql())                | DONE   | |
| UnionAll                             | DONE   | |
| GroupBy / Having                     | DONE   | |
| Distinct                             | DONE   | |
| Aggregates (count/sum/min/max/avg)   | DONE   | |
| count(predicate) — conditional count | DONE   | |
| selectColumns — custom SQL columns   | DONE   | |
| toDictionary                         | DONE   | |
| includeMany (eager loading)          | DONE   | |
| asTable (table name override)        | DONE   | |
| FromQuery (subquery as FROM source)  | DONE   | Outer WHERE/ORDER BY can't use qualified names |
| InsertInto (SELECT INTO)             | DONE   | |
| BulkCopy (high-perf batch insert)    | DONE   | With onProgress callback |
| ToChunk (chunked batch reading)      | DONE   | |
| ToDataTable                          | SKIP   | No DataTable in Kotlin stdlib |
| ForUpdate (pessimistic locking)      | SKIP   | SQLite does not support FOR UPDATE |
| CancellationToken / async API        | TODO   | Kotlin coroutines |
| Batch progress reporting             | DONE   | onProgress callback on IInsert |
| WithConnection / WithTransaction     | SKIP   | ORM manages connections internally |

## 2. EXPRESSION DSL

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| Comparisons (eq, ne, gt, lt, etc.)   | DONE   | |
| String ops                           | DONE   | |
| DateTime ops                         | DONE   | |
| Math ops + trig                      | DONE   | |
| Type conversion                      | DONE   | |
| Bitwise                              | DONE   | |
| Arithmetic operators                 | DONE   | |
| Coalesce                             | DONE   | |
| Subquery: EXISTS / NOT EXISTS        | DONE   | ColumnRef.exists/notExists |
| Subquery: ANY / ALL                  | DONE   | eqAny, gtAll, ltAny, etc. |
| DynamicFilterInfo (JSON filters)     | DONE   | Serializable filter model |
| ExpressionCallRegistry               | DONE   | Custom SQL function registry |
| DateDiff (days/hours/min/sec)        | DONE   | Static methods on ColumnRef |
| Navigation property in expressions   | TODO   | Requires @Navigate wiring |

## 3. CODEFIRST

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| Auto-sync entity structure           | DONE   | |
| DDL comparison                       | DONE   | |
| Migration versioning / history       | DONE   | __FreeSql_Migrations table |
| Rename column detection (OldName)    | DONE   | @Column(oldName) triggers RENAME COLUMN |
| Rename table detection (OldName)     | DONE   | @Table(oldName) triggers RENAME TO |
| Fluent API entity configuration      | DONE   | configEntity + column config |
| Name conversion (ToLower, etc.)      | DONE   | isSyncStructureToLower/ToUpper |
| DisableSyncStructure per entity      | DONE   | @Table(disableSyncStructure=true) |

## 4. DBFIRST

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| GetDatabases, GetTables, ExistsTable | DONE   | |
| Column introspection                 | DONE   | |
| GetTablesByDatabase                  | TODO   | |
| GetTableByName                       | TODO   | |
| Index introspection                  | TODO   | |
| Foreign key introspection            | TODO   | |
| Code generation (DB → Kotlin)        | TODO   | |

## 5. AOP / INTERCEPTORS

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| CurdBefore / CurdAfter               | DONE   | |
| ConfigEntity / ConfigEntityProperty  | TODO   | |
| SyncStructureBefore / After          | TODO   | |
| AuditValue                           | TODO   | |
| CommandBefore / CommandAfter         | TODO   | |
| TraceBefore / TraceAfter             | TODO   | |

## 6. GLOBAL FILTERS

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| Named filters per entity type        | DONE   | |
| applyExpr with SqlExpr               | DONE   | |
| Filter removal                       | DONE   | |
| FilterType flags (Query/Update/Del)  | TODO   | |
| ApplyIf / ApplyOnly / ApplyOnlyIf    | TODO   | |
| RepositoryDataFilter                 | TODO   | |

## 7. CONNECTION MANAGEMENT

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| Connection pool (in-memory)          | DONE   | |
| File-based DB connection pooling     | DONE   | |
| Pool close / cleanup                 | DONE   | |
| Read/write splitting (master/slave)  | SKIP   | SQLite-only, not applicable |
| Circuit-breaking / health monitoring | TODO   | |
| Statistics                           | TODO   | |

## 8. ARCHITECTURE PATTERNS

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| IFreeSql entry point                 | DONE   | |
| Provider pattern (SQLite)            | DONE   | |
| ActiveCompanion (Active Record)      | DONE   | Static CRUD via companion object |
| IBaseRepository<T>                   | DONE   | Repository with CRUD + filtering |
| UnitOfWork                           | DONE   | Transactional unit with repos |
| DbContext (change tracking)          | DONE   | Add/Update/Remove + saveChanges |
| DbSet<T> (tracked operations)        | DONE   | EntityState tracking + flush |
| DI extensions                        | TODO   | |
| AggregateRoot (DDD)                  | TODO   | |

## 9. DATA ANNOTATIONS

| Feature                              | Status | Notes |
|--------------------------------------|--------|-------|
| @Table (name, oldName)               | DONE   | |
| @Table (disableSyncStructure)        | DONE   | |
| @Table (asTable)                     | DONE   | Annotation defined |
| @Column (name, isPrimary, isIdentity)| DONE   | |
| @Column (isNullable, stringLength)   | DONE   | |
| @Column (canInsert, canUpdate)       | DONE   | |
| @Column (dbType, oldName)            | DONE   | |
| @Column (insertValueSql, isVersion)  | DONE   | |
| @Column (serverTime)                 | DONE   | INSERT/UPDATE/BOTH auto-set |
| @Column (position)                   | DONE   | Controls DDL column order |
| @Column (uniqueIndex)                | DONE   | Auto-creates unique index |
| @Column (mapType)                    | DONE   | Annotation defined, read in metadata |
| @Index (name, fields, isUnique)      | DONE   | |
| @Navigate (bind, manyToMany)         | DONE   | Annotation defined, readable via reflection |
| @ExpressionCall                      | DONE   | Annotation defined |
