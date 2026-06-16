package freesql.provider.sqlite.curd

import freesql.annotation.ServerTimeType
import freesql.core.ColumnRef
import freesql.core.IInsert
import freesql.model.TableInfo
import freesql.provider.sqlite.*
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * SQLite INSERT builder.
 * Supports batch inserts with limits: max 5000 rows, 999 parameters per batch.
 *
 * Features:
 * - Batch splitting by row count (5000) and param count (999)
 * - SplitExecuteAffrows / SplitExecuteIdentity / SplitExecuteInserted
 * - InsertColumns / IgnoreColumns with String or ColumnRef support
 * - InsertIdentity mode
 * - NoneParameter mode (inline SQL values, no @params)
 * - AsTable sharding
 */
class SqliteInsert<T : Any>(
    private val entityType: KClass<T>,
    private val ado: SqliteAdo,
    private val utils: SqliteUtils,
    private val codeFirst: SqliteCodeFirst
) : IInsert<T> {

    private val sources = mutableListOf<T>()
    private var insertColumns: List<String>? = null
    private var ignoreColumns: List<String>? = null
    private var insertIdentityFlag: Boolean = false
    private var batchSize: Int = 5000
    private var tableOverride: String? = null
    private var noneParameterMode: Boolean = false
    private var progressCallback: ((inserted: Int, total: Int) -> Unit)? = null

    // Batch limits
    private val maxBatchRows = 5000
    private val maxParamsPerBatch = 999

    private val table: TableInfo by lazy {
        codeFirst.buildTableInfo(entityType)
    }

    private val tableName: String
        get() = tableOverride ?: utils.quoteSqlName(table.dbName)

    override fun setSource(source: T): IInsert<T> {
        sources.add(source)
        return this
    }

    override fun setSource(sources: Collection<T>): IInsert<T> {
        this.sources.addAll(sources)
        return this
    }

    override fun insertColumns(vararg columns: String): IInsert<T> {
        insertColumns = columns.toList()
        return this
    }

    override fun insertColumns(vararg columns: ColumnRef<*>): IInsert<T> {
        insertColumns = columns.map { it.columnName }.toList()
        return this
    }

    override fun ignoreColumns(vararg columns: String): IInsert<T> {
        ignoreColumns = columns.toList()
        return this
    }

    override fun ignoreColumns(vararg columns: ColumnRef<*>): IInsert<T> {
        ignoreColumns = columns.map { it.columnName }.toList()
        return this
    }

    override fun insertIdentity(): IInsert<T> {
        insertIdentityFlag = true
        return this
    }

    override fun batchOptions(size: Int): IInsert<T> {
        batchSize = minOf(size, maxBatchRows)
        return this
    }

    override fun asTable(tableName: String): IInsert<T> {
        tableOverride = tableName
        return this
    }

    override fun noneParameter(): IInsert<T> {
        noneParameterMode = true
        return this
    }

    override fun onProgress(callback: (inserted: Int, total: Int) -> Unit): IInsert<T> {
        progressCallback = callback
        return this
    }

    override fun bulkCopy(sources: Collection<T>): Int {
        val total = sources.size
        var inserted = 0
        sources.chunked(batchSize).forEach { chunk ->
            this.sources.clear()
            this.sources.addAll(chunk)
            inserted += executeAffrows()
            progressCallback?.invoke(inserted, total)
        }
        return inserted
    }

    // --- Split execution ---

    override fun splitExecuteAffrows(): Int {
        if (sources.isEmpty()) return 0
        val cols = getInsertableColumns()
        val effectiveBatchSize = calculateEffectiveBatchSize(cols.size)
        var totalAffected = 0
        sources.chunked(effectiveBatchSize).forEach { batch ->
            val sql = buildInsertSql(batch, cols)
            totalAffected += ado.executeNonQuery(sql)
        }
        return totalAffected
    }

    override fun splitExecuteIdentity(): Long {
        if (sources.isEmpty()) return 0L
        val cols = getInsertableColumns()
        val effectiveBatchSize = calculateEffectiveBatchSize(cols.size)
        var firstIdentity = 0L

        sources.chunked(effectiveBatchSize).forEachIndexed { index, batch ->
            val sql = buildInsertSql(batch, cols)
            ado.executeNonQuery(sql)
            if (index == 0) {
                val identity = ado.executeScalar("SELECT last_insert_rowid()")
                firstIdentity = (identity as? Number)?.toLong() ?: 0L
            }
        }
        return firstIdentity
    }

    override fun splitExecuteInserted(): List<T> {
        splitExecuteAffrows()
        return sources
    }

    // --- Standard execution ---

    override fun executeAffrows(): Int {
        if (sources.isEmpty()) return 0

        val cols = getInsertableColumns()
        val effectiveBatchSize = calculateEffectiveBatchSize(cols.size)

        var totalAffected = 0

        // Split into batches
        sources.chunked(effectiveBatchSize).forEach { batch ->
            val sql = buildInsertSql(batch, cols)
            totalAffected += ado.executeNonQuery(sql)
        }

        return totalAffected
    }

    override fun executeIdentity(): Long {
        if (sources.isEmpty()) return 0

        val cols = getInsertableColumns()
        val sql = buildInsertSql(listOf(sources[0]), cols)
        ado.executeNonQuery(sql)

        val identity = ado.executeScalar("SELECT last_insert_rowid()")
        return (identity as? Number)?.toLong() ?: 0L
    }

    override fun executeInserted(): List<T> {
        executeAffrows()
        return sources
    }

    override fun toSql(): String {
        if (sources.isEmpty()) return ""
        val cols = getInsertableColumns()
        return buildInsertSql(sources, cols)
    }

    // --- Internal ---

    private fun calculateEffectiveBatchSize(paramsPerRow: Int): Int {
        return if (paramsPerRow > 0) {
            minOf(batchSize, maxParamsPerBatch / paramsPerRow)
        } else {
            batchSize
        }
    }

    private fun buildInsertSql(batch: List<T>, cols: List<freesql.model.ColumnInfo>): String {
        if (cols.isEmpty()) {
            return "INSERT INTO $tableName DEFAULT VALUES"
        }

        val sb = StringBuilder()
        sb.append("INSERT INTO $tableName (")

        // Column names
        sb.append(cols.joinToString(", ") { utils.quoteSqlName(it.dbName) })
        sb.append(") VALUES ")

        if (noneParameterMode) {
            // NoneParameter mode: inline SQL values
            val valueRows = batch.map { entity ->
                val values = cols.map { col ->
                    val serverTimeType = col.serverTime
                    if (serverTimeType == ServerTimeType.INSERT || serverTimeType == ServerTimeType.BOTH) {
                        "datetime('now')"
                    } else {
                        val value = getEntityValue(entity, col.csName)
                        utils.formatSqlValue(utils.formatParameter(value))
                    }
                }
                "(${values.joinToString(", ")})"
            }
            sb.append(valueRows.joinToString(", "))
        } else {
            // Parameterized mode
            val valueRows = batch.map { entity ->
                val values = cols.map { col ->
                    val serverTimeType = col.serverTime
                    if (serverTimeType == ServerTimeType.INSERT || serverTimeType == ServerTimeType.BOTH) {
                        "datetime('now')"
                    } else {
                        val value = getEntityValue(entity, col.csName)
                        utils.formatSqlValue(utils.formatParameter(value))
                    }
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
        if (!insertIdentityFlag && col.isIdentity) return@filter false
        // Compare using dbName since ColumnRef stores DB column names
        if (insertColumns != null) return@filter insertColumns!!.contains(col.dbName)
        if (ignoreColumns != null) return@filter !ignoreColumns!!.contains(col.dbName)
        true
    }

    private fun getEntityValue(entity: Any, propertyName: String): Any? {
        val prop = entityType.memberProperties.find { it.name == propertyName }
        return prop?.call(entity)
    }
}
