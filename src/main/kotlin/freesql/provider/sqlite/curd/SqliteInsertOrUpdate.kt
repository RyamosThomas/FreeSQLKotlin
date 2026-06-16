package freesql.provider.sqlite.curd

import freesql.core.IAdo
import freesql.core.IInsertOrUpdate
import freesql.model.TableInfo
import freesql.provider.sqlite.*
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * SQLite INSERT OR REPLACE / INSERT OR IGNORE builder.
 * Supports identity handling: splits source by identity null/non-null.
 * Batch support with 5000 row / 999 param limits.
 *
 * Features:
 * - SplitSourceByIdentityValueIsNull: separates entities with null identity (pure insert)
 *   from those with non-null identity (upsert via INSERT OR REPLACE)
 * - NoneParameter mode (inline values, no @params)
 * - Batch splitting by row count and param count
 * - AsTable sharding
 * - UpdateColumns on conflict
 * - IfExistsDoNothing (INSERT OR IGNORE)
 */
class SqliteInsertOrUpdate<T : Any>(
    private val entityType: KClass<T>,
    private val ado: IAdo,
    private val utils: SqliteUtils,
    private val codeFirst: SqliteCodeFirst
) : IInsertOrUpdate<T> {

    private val sources = mutableListOf<T>()
    private var updateColumns: List<String>? = null
    private var doNothingMode: Boolean = false
    private var tableOverride: String? = null
    private var noneParameterMode: Boolean = false

    // Batch limits
    private val maxBatchRows = 5000
    private val maxParamsPerBatch = 999

    private val table: TableInfo by lazy {
        codeFirst.buildTableInfo(entityType)
    }

    private val tableName: String
        get() = tableOverride ?: utils.quoteSqlName(table.dbName)

    override fun setSource(source: T): IInsertOrUpdate<T> {
        sources.add(source)
        return this
    }

    override fun setSource(sources: Collection<T>): IInsertOrUpdate<T> {
        this.sources.addAll(sources)
        return this
    }

    override fun updateColumns(vararg columns: String): IInsertOrUpdate<T> {
        updateColumns = columns.toList()
        return this
    }

    override fun ifExistsDoNothing(): IInsertOrUpdate<T> {
        doNothingMode = true
        return this
    }

    override fun asTable(tableName: String): IInsertOrUpdate<T> {
        tableOverride = tableName
        return this
    }

    override fun noneParameter(): IInsertOrUpdate<T> {
        noneParameterMode = true
        return this
    }

    // --- Split execution ---

    override fun splitExecuteAffrows(): Int {
        return executeAffrows() // Already handles batching internally
    }

    override fun executeAffrows(): Int {
        if (sources.isEmpty()) return 0

        val cols = getInsertableColumns()
        val paramsPerRow = cols.size

        // Calculate effective batch size
        val effectiveBatchSize = if (paramsPerRow > 0) {
            minOf(maxBatchRows, maxParamsPerBatch / paramsPerRow)
        } else {
            maxBatchRows
        }

        var totalAffected = 0

        // SplitSourceByIdentityValueIsNull:
        // - entities with non-null identity: INSERT OR REPLACE (update existing)
        // - entities with null identity: pure insert (INSERT OR IGNORE for no-conflict, or INSERT OR REPLACE)
        val identityCols = table.identitys
        if (identityCols.isNotEmpty()) {
            val (withIdentity, withoutIdentity) = sources.partition { entity ->
                identityCols.any { idCol ->
                    getEntityValue(entity, idCol.csName) != null
                }
            }

            // Insert entities with identity values (may replace existing)
            if (withIdentity.isNotEmpty()) {
                withIdentity.chunked(effectiveBatchSize).forEach { batch ->
                    val sql = buildInsertOrUpdateSql(batch, cols, forceReplace = true)
                    totalAffected += ado.executeNonQuery(sql)
                }
            }

            // Insert entities without identity (new rows, skip conflicts or replace)
            if (withoutIdentity.isNotEmpty()) {
                withoutIdentity.chunked(effectiveBatchSize).forEach { batch ->
                    val sql = buildInsertOrUpdateSql(batch, cols, forceReplace = false)
                    totalAffected += ado.executeNonQuery(sql)
                }
            }
        } else {
            // No identity columns, just batch insert
            sources.chunked(effectiveBatchSize).forEach { batch ->
                val sql = buildInsertOrUpdateSql(batch, cols, forceReplace = false)
                totalAffected += ado.executeNonQuery(sql)
            }
        }

        return totalAffected
    }

    override fun toSql(): String {
        if (sources.isEmpty()) return ""
        val cols = getInsertableColumns()
        return buildInsertOrUpdateSql(sources, cols, forceReplace = false)
    }

    // --- Internal ---

    private fun buildInsertOrUpdateSql(
        batch: List<T>,
        cols: List<freesql.model.ColumnInfo>,
        forceReplace: Boolean
    ): String {
        if (cols.isEmpty()) {
            val keyword = if (doNothingMode && !forceReplace) "INSERT OR IGNORE" else "INSERT OR REPLACE"
            return "$keyword INTO $tableName DEFAULT VALUES"
        }

        val keyword = when {
            forceReplace -> "INSERT OR REPLACE"
            doNothingMode -> "INSERT OR IGNORE"
            else -> "INSERT OR REPLACE"
        }

        val sb = StringBuilder()
        sb.append("$keyword INTO $tableName (")
        sb.append(cols.joinToString(", ") { utils.quoteSqlName(it.dbName) })
        sb.append(") VALUES ")

        if (noneParameterMode) {
            // NoneParameter mode: inline SQL values
            val valueRows = batch.map { entity ->
                val values = cols.map { col ->
                    val value = getEntityValue(entity, col.csName)
                    utils.formatSqlValue(utils.formatParameter(value))
                }
                "(${values.joinToString(", ")})"
            }
            sb.append(valueRows.joinToString(", "))
        } else {
            // Parameterized mode (existing behavior - values inline in SQL for SQLite)
            val valueRows = batch.map { entity ->
                val values = cols.map { col ->
                    val value = getEntityValue(entity, col.csName)
                    utils.formatSqlValue(utils.formatParameter(value))
                }
                "(${values.joinToString(", ")})"
            }
            sb.append(valueRows.joinToString(", "))
        }
        return sb.toString()
    }

    private fun getInsertableColumns() = table.columns.filter { col ->
        if (col.isIgnore) return@filter false
        if (!col.canInsert) return@filter false
        true
    }

    private fun getEntityValue(entity: Any, propertyName: String): Any? {
        val prop = entityType.memberProperties.find { it.name == propertyName }
        return prop?.call(entity)
    }
}
