package freesql.provider.sqlite

import freesql.annotation.Column
import freesql.annotation.Index
import freesql.annotation.ServerTimeType
import freesql.annotation.Table
import freesql.core.FreeSqlCursor
import freesql.core.IAdo
import freesql.core.ICodeFirst
import freesql.model.*
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties

/**
 * SQLite schema migration manager.
 * Port of FreeSql SqliteCodeFirst.
 */
class SqliteCodeFirst(
    private val ado: IAdo,
    private val utils: SqliteUtils
) : ICodeFirst {

    override var isAutoSyncStructure: Boolean = false
    override var isSyncStructureToLower: Boolean = false
    override var isSyncStructureToUpper: Boolean = false
    override var isNoneCommandParameter: Boolean = false

    private val syncedTypes = mutableSetOf<KClass<*>>()

    // Fluent API entity configuration
    private val entityConfigs = mutableMapOf<KClass<*>, EntityConfig>()

    /** Fluent API: configure entity metadata programmatically. */
    fun <T : Any> configEntity(clazz: KClass<T>, block: EntityConfigBuilder.() -> Unit) {
        val config = EntityConfigBuilder().apply(block).build()
        entityConfigs[clazz] = config
    }

    /** Get the merged entity config (fluent API overrides annotation defaults). */
    fun getEntityConfig(clazz: KClass<*>): EntityConfig? = entityConfigs[clazz]

    private fun ensureMigrationTable() {
        ado.executeNonQuery("""
            CREATE TABLE IF NOT EXISTS __FreeSql_Migrations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_name TEXT NOT NULL,
                entity_hash TEXT NOT NULL,
                applied_at TEXT NOT NULL DEFAULT (datetime('now')),
                ddl_statements TEXT
            )
        """)
    }

    private fun recordMigration(type: KClass<*>) {
        ensureMigrationTable()
        val tableName = buildTableInfo(type).dbName
        val ddl = getComparisonDDLStatements(type)
        val hash = type.qualifiedName ?: type.simpleName ?: "unknown"
        ado.executeNonQuery(
            "INSERT INTO __FreeSql_Migrations (entity_name, entity_hash, ddl_statements) VALUES (@name, @hash, @ddl)",
            mapOf("@name" to tableName, "@hash" to hash, "@ddl" to ddl)
        )
    }

    /** Get migration history for all entities. */
    fun getMigrationHistory(): List<Map<String, Any?>> {
        ensureMigrationTable()
        return ado.executeDataTable("SELECT * FROM __FreeSql_Migrations ORDER BY id")
    }

    /** Get migration history for a specific entity. */
    fun getMigrationHistory(entityType: KClass<*>): List<Map<String, Any?>> {
        ensureMigrationTable()
        val tableName = buildTableInfo(entityType).dbName
        return ado.executeDataTable(
            "SELECT * FROM __FreeSql_Migrations WHERE entity_name = @name ORDER BY id",
            mapOf("@name" to tableName)
        )
    }

    override fun syncStructure(vararg entityTypes: KClass<*>) {
        for (type in entityTypes) {
            if (syncedTypes.contains(type)) continue
            // Check disableSyncStructure flag
            val tableAnnotation = type.findAnnotation<Table>()
            if (tableAnnotation?.disableSyncStructure == true) continue
            val ddl = getComparisonDDLStatements(type)
            if (ddl.isNotBlank()) {
                ddl.split(";").filter { it.isNotBlank() }.forEach { stmt: String ->
                    ado.executeNonQuery(stmt.trim())
                }
            }
            syncedTypes.add(type)
            // Record in migration history
            recordMigration(type)
        }
    }

    override fun getComparisonDDLStatements(entityType: KClass<*>): String {
        val table = buildTableInfo(entityType)
        val sb = StringBuilder()

        // Check if table exists (by new name)
        var exists = ado.executeScalar(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=@name",
            mapOf("@name" to table.dbName)
        )
        var tableExistsByNewName = exists != null && (exists as Number).toInt() > 0

        // Check if old table name exists (for table rename)
        var renamedFromOld = false
        if (!tableExistsByNewName && table.dbOldName.isNotEmpty()) {
            val oldExists = ado.executeScalar(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=@name",
                mapOf("@name" to table.dbOldName)
            )
            if (oldExists != null && (oldExists as Number).toInt() > 0) {
                // Rename table first
                sb.appendLine("ALTER TABLE ${utils.quoteSqlName(table.dbOldName)} RENAME TO ${utils.quoteSqlName(table.dbName)};")
                tableExistsByNewName = true
                renamedFromOld = true
            }
        }

        if (!tableExistsByNewName) {
            // CREATE TABLE
            sb.appendLine(createTableDDL(table) + ";")
            // Create indexes
            table.indexes.forEach { idx: IndexInfo ->
                sb.appendLine(createIndexDDL(table.dbName, idx) + ";")
            }
        } else {
            // ALTER TABLE — detect changes
            // If we renamed from old, read columns from old table name (rename not executed yet)
            val readTableName = if (renamedFromOld) table.dbOldName else table.dbName
            val existingColumns = getExistingColumns(readTableName)

            // Check for column renames via oldName
            val renameStatements = generateRenameStatements(table, existingColumns)
            renameStatements.forEach { sb.appendLine("$it;") }

            // Track renamed columns: old name -> new name
            val renamedColumns = mutableMapOf<String, String>()
            for (col in table.columns) {
                val dbColName = if (isSyncStructureToLower) col.dbName.lowercase() else col.dbName
                val oldName = col.attribute.dbOldName
                if (oldName.isNotEmpty() && existingColumns.containsKey(oldName) && !existingColumns.containsKey(dbColName)) {
                    renamedColumns[oldName] = dbColName
                }
            }

            // Build updated column map reflecting renames (in-memory, since DDL isn't executed yet)
            val updatedColumns = existingColumns.toMutableMap()
            for ((oldName, newName) in renamedColumns) {
                val colData = updatedColumns.remove(oldName)
                if (colData != null) {
                    updatedColumns[newName] = colData.toMutableMap().apply { put("name", newName) }
                }
            }

            val alterStatements = generateAlterStatements(table, updatedColumns)
            alterStatements.forEach { sb.appendLine("$it;") }

            // Sync indexes for existing tables
            val existingIndexes = getExistingIndexes(readTableName)
            val indexStatements = generateIndexSyncStatements(table, existingIndexes)
            indexStatements.forEach { sb.appendLine("$it;") }
        }
        return sb.toString().trim()
    }

    override fun getDbInfo(kotlinType: KClass<*>): DbTypeInfo? {
        return TypeMapping.getDbType(kotlinType)
    }

    /**
     * Build a TableInfo from a Kotlin class using annotations and conventions.
     */
    fun buildTableInfo(entityType: KClass<*>): TableInfo {
        val tableAnnotation = entityType.findAnnotation<Table>()
        val fluentConfig = entityConfigs[entityType]

        // Fluent API tableName overrides annotation, annotation overrides class name
        val tableName = fluentConfig?.tableName
            ?: tableAnnotation?.name?.ifEmpty { entityType.simpleName!! }
            ?: entityType.simpleName!!

        val info = TableInfo(
            type = entityType,
            csName = entityType.simpleName!!,
            dbName = if (isSyncStructureToLower) tableName.lowercase()
                     else if (isSyncStructureToUpper) tableName.uppercase()
                     else tableName,
            dbOldName = tableAnnotation?.oldName ?: "",
            disableSyncStructure = tableAnnotation?.disableSyncStructure ?: false,
            asTable = tableAnnotation?.asTable ?: ""
        )

        // Process properties
        entityType.memberProperties.forEach { prop ->
            val colAnnotation = prop.findAnnotation<Column>()
            val fluentColConfig = fluentConfig?.columnConfigs?.get(prop.name)

            // Check ignore from both annotation and fluent API
            if (colAnnotation?.isIgnore == true) return@forEach
            if (fluentColConfig?.isIgnore == true) return@forEach

            val isNullable = prop.returnType.isMarkedNullable
            val rawType = prop.returnType.classifier as? KClass<*> ?: return@forEach

            // Fluent API overrides annotation, annotation overrides property name
            val rawColName = fluentColConfig?.dbName?.ifEmpty { null }
                ?: colAnnotation?.name?.ifEmpty { null }
                ?: prop.name
            val colDbName = if (isSyncStructureToLower) rawColName.lowercase()
                else if (isSyncStructureToUpper) rawColName.uppercase()
                else rawColName

            val colIsIdentity = fluentColConfig?.isIdentity
                    ?: colAnnotation?.isIdentity ?: false

            val colAttr = ColumnAttribute(
                dbName = colDbName,
                dbOldName = colAnnotation?.oldName ?: "",
                dbType = fluentColConfig?.dbType?.ifEmpty { null }
                    ?: colAnnotation?.dbType ?: "",
                isPrimary = fluentColConfig?.isPrimary
                    ?: colAnnotation?.isPrimary ?: false,
                isIdentity = colIsIdentity,
                isNullable = fluentColConfig?.isNullable
                    ?: colAnnotation?.isNullable ?: isNullable,
                isIgnore = false,
                isVersion = colAnnotation?.isVersion ?: false,
                stringLength = fluentColConfig?.stringLength
                    ?: colAnnotation?.stringLength ?: 0,
                precision = colAnnotation?.precision ?: 0,
                scale = colAnnotation?.scale ?: 0,
                canInsert = fluentColConfig?.canInsert
                    ?: if (colIsIdentity) false else (colAnnotation?.canInsert ?: true),
                canUpdate = fluentColConfig?.canUpdate
                    ?: colAnnotation?.canUpdate ?: true,
                insertValueSql = fluentColConfig?.insertValueSql?.ifEmpty { null }
                    ?: colAnnotation?.insertValueSql ?: "",
                serverTime = colAnnotation?.serverTime ?: ServerTimeType.NONE,
                position = colAnnotation?.position ?: 0,
                mapType = colAnnotation?.mapType ?: "",
                uniqueIndex = colAnnotation?.uniqueIndex ?: false
            )

            val colInfo = ColumnInfo(
                table = info,
                csName = prop.name,
                csType = rawType,
                isNullableCsType = isNullable,
                attribute = colAttr
            )

            // Set DB type text
            if (colAttr.dbType.isNotEmpty()) {
                colInfo.dbTypeText = colAttr.dbType
            } else {
                // Check if it's an enum type — map to integer (ordinal) by default
                if (rawType.isSubclassOf(Enum::class)) {
                    colInfo.dbTypeText = "integer"
                } else {
                    val dbTypeInfo = TypeMapping.getDbType(rawType, isNullable)
                    colInfo.dbTypeText = dbTypeInfo?.dbType ?: "text"
                }
            }

            // Set default value from insertValueSql if specified
            if (colAttr.insertValueSql.isNotEmpty()) {
                colInfo.dbDefaultValue = colAttr.insertValueSql
            } else if (!colAttr.isNullable && !colAttr.isIdentity) {
                // Provide sensible defaults for NOT NULL columns
                colInfo.dbDefaultValue = getDefaultValueForType(rawType, colInfo.dbTypeText, isNullable)
            }

            info.addColumn(colInfo)
        }

        // Sort columns by position (0 = declaration order, lower = earlier)
        val hasPositions = info.columns.any { it.position != 0 }
        if (hasPositions) {
            val sorted = info.columns.sortedBy { it.position }
            info.columns.clear()
            info.columnsByCs.clear()
            sorted.forEach { col ->
                info.columns.add(col)
                if (!col.isIgnore) info.columnsByCs[col.csName] = col
            }
        }

        // Auto-create unique indexes from @Column(uniqueIndex = true)
        info.columns.filter { it.uniqueIndex && !it.isIgnore }.forEach { col ->
            val idxName = "IX_${tableName}_${col.dbName}"
            if (info.indexes.none { it.name == idxName }) {
                info.indexes.add(IndexInfo(name = idxName, fields = listOf(col.dbName), isUnique = true))
            }
        }

        // Process indexes
        entityType.findAnnotation<Index>()?.let { idx: Index ->
            info.indexes.add(IndexInfo(
                name = idx.name.ifEmpty { "IX_${tableName}_${idx.fields.replace(",", "_")}" },
                fields = idx.fields.split(",").map { it.trim() },
                isUnique = idx.isUnique
            ))
        }

        // Handle multiple @Index annotations
        entityType.annotations.filterIsInstance<Index>().forEach { idx: Index ->
            if (info.indexes.none { it.name == idx.name }) {
                info.indexes.add(IndexInfo(
                    name = idx.name.ifEmpty { "IX_${tableName}_${idx.fields.replace(",", "_")}" },
                    fields = idx.fields.split(",").map { it.trim() },
                    isUnique = idx.isUnique
                ))
            }
        }

        return info
    }

    /**
     * Get sensible default value for NOT NULL columns.
     */
    private fun getDefaultValueForType(csType: KClass<*>, @Suppress("UNUSED_PARAMETER") dbTypeText: String, isNullable: Boolean): String {
        if (isNullable) return ""

        // Check enum first
        if (csType.isSubclassOf(Enum::class)) return "0"

        return when (csType) {
            Boolean::class -> "0"
            Byte::class, Short::class, Int::class, Long::class -> "0"
            Float::class, Double::class -> "0"
            java.math.BigDecimal::class -> "0"
            String::class -> "''"
            Char::class -> "''"
            java.util.UUID::class -> "''"
            java.time.LocalDateTime::class, java.time.Instant::class, java.util.Date::class -> "''"
            ByteArray::class -> "X''"
            else -> "''"
        }
    }

    private fun createTableDDL(table: TableInfo): String {
        val sb = StringBuilder()
        sb.append("CREATE TABLE IF NOT EXISTS ${utils.quoteSqlName(table.dbName)} (\n")

        val columnDefs = table.columns.map { col ->
            val name = utils.quoteSqlName(col.dbName)
            var type = col.dbTypeText
            if (isSyncStructureToLower) type = type.lowercase()

            val parts = mutableListOf("$name $type")

            if (col.isIdentity) {
                parts.add("PRIMARY KEY AUTOINCREMENT")
            } else if (col.isPrimary && table.identitys.isEmpty()) {
                parts.add("PRIMARY KEY")
            }

            if (!col.isNullableCsType && !col.isIdentity) {
                parts.add("NOT NULL")
            }

            // Default value handling: insertValueSql takes priority
            if (col.attribute.insertValueSql.isNotEmpty()) {
                parts.add("DEFAULT ${col.attribute.insertValueSql}")
            } else if (col.dbDefaultValue.isNotEmpty()) {
                parts.add("DEFAULT ${col.dbDefaultValue}")
            }

            parts.joinToString(" ")
        }

        // Handle composite primary key
        val compositePrimarys = table.primarys.filter { !it.isIdentity }
        if (compositePrimarys.size > 1 && table.identitys.isEmpty()) {
            val pkNames = compositePrimarys.joinToString(", ") { utils.quoteSqlName(it.dbName) }
            val colsWithoutPk = columnDefs.toMutableList()
            // Remove PRIMARY KEY from individual columns
            for (i in colsWithoutPk.indices) {
                colsWithoutPk[i] = colsWithoutPk[i].replace(" PRIMARY KEY", "")
            }
            sb.append(colsWithoutPk.joinToString(",\n"))
            sb.append(",\nPRIMARY KEY ($pkNames)")
        } else {
            sb.append(columnDefs.joinToString(",\n"))
        }

        sb.append("\n)")
        return sb.toString()
    }

    private fun createIndexDDL(tableName: String, index: IndexInfo): String {
        val unique = if (index.isUnique) "UNIQUE " else ""
        val indexName = if (isSyncStructureToLower) index.name.lowercase() else index.name
        val fields = index.fields.joinToString(", ") { utils.quoteSqlName(it) }
        return "CREATE ${unique}INDEX IF NOT EXISTS ${utils.quoteSqlName(indexName)} ON ${utils.quoteSqlName(tableName)} ($fields)"
    }

    private fun getExistingColumns(tableName: String): Map<String, Map<String, Any?>> {
        val columns = mutableMapOf<String, MutableMap<String, Any?>>()
        ado.executeReader("PRAGMA table_info(${utils.quoteSqlName(tableName)})") { cursor: FreeSqlCursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val notnullIndex = cursor.getColumnIndex("notnull")
            val dfltValueIndex = cursor.getColumnIndex("dflt_value")
            val pkIndex = cursor.getColumnIndex("pk")
            while (cursor.next()) {
                val colName = cursor.getString(nameIndex) ?: ""
                columns[colName] = mutableMapOf(
                    "name" to colName,
                    "type" to cursor.getString(typeIndex),
                    "notnull" to cursor.getInt(notnullIndex),
                    "dflt_value" to cursor.getString(dfltValueIndex),
                    "pk" to cursor.getInt(pkIndex)
                )
            }
        }
        return columns
    }

    /**
     * Get existing indexes on a table (excluding auto-generated SQLite indexes).
     */
    private fun getExistingIndexes(tableName: String): Set<String> {
        val indexNames = mutableSetOf<String>()
        ado.executeReader("PRAGMA index_list(${utils.quoteSqlName(tableName)})") { cursor: FreeSqlCursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.next()) {
                val indexName = cursor.getString(nameIndex) ?: ""
                if (!indexName.startsWith("sqlite_") && !indexName.startsWith("autoindex")) {
                    indexNames.add(indexName)
                }
            }
        }
        return indexNames
    }

    /**
     * Generate CREATE INDEX statements for indexes that don't exist yet.
     */
    private fun generateIndexSyncStatements(
        table: TableInfo,
        existingIndexNames: Set<String>
    ): List<String> {
        val statements = mutableListOf<String>()
        for (idx in table.indexes) {
            val indexName = if (isSyncStructureToLower) idx.name.lowercase() else idx.name
            if (!existingIndexNames.contains(indexName)) {
                statements.add(createIndexDDL(table.dbName, idx))
            }
        }
        return statements
    }

    /**
     * Detect column renames via @Column(oldName="x").
     * If a column with the old name exists in the DB but not the new name,
     * issue ALTER TABLE RENAME COLUMN (SQLite 3.25+).
     */
    private fun generateRenameStatements(
        table: TableInfo,
        existingColumns: Map<String, Map<String, Any?>>
    ): List<String> {
        val statements = mutableListOf<String>()

        for (col in table.columns) {
            val dbColName = if (isSyncStructureToLower) col.dbName.lowercase() else col.dbName
            val oldName = col.attribute.dbOldName

            if (oldName.isNotEmpty() && existingColumns.containsKey(oldName) && !existingColumns.containsKey(dbColName)) {
                // Old column exists, new column doesn't — rename it
                statements.add(
                    "ALTER TABLE ${utils.quoteSqlName(table.dbName)} RENAME COLUMN ${utils.quoteSqlName(oldName)} TO ${utils.quoteSqlName(dbColName)}"
                )
            }
        }

        return statements
    }

    private fun generateAlterStatements(
        table: TableInfo,
        existingColumns: Map<String, Map<String, Any?>>
    ): List<String> {
        val statements = mutableListOf<String>()

        // Check for new columns
        for (col in table.columns) {
            val dbColName = if (isSyncStructureToLower) col.dbName.lowercase() else col.dbName
            if (!existingColumns.containsKey(dbColName)) {
                // SQLite supports ALTER TABLE ADD COLUMN
                val type = if (isSyncStructureToLower) col.dbTypeText.lowercase() else col.dbTypeText
                val nullable = if (col.isNullableCsType) "" else " NOT NULL DEFAULT ${getDefaultValueForType(col.csType, col.dbTypeText, col.isNullableCsType)}"
                statements.add(
                    "ALTER TABLE ${utils.quoteSqlName(table.dbName)} ADD COLUMN ${utils.quoteSqlName(dbColName)} $type$nullable"
                )
            }
        }

        // For type changes, nullable changes, etc. — use the temp table strategy
        val needsRebuild = existingColumns.any { (colName: String, existing: Map<String, Any?>) ->
            val col = table.columns.find {
                val dbName = if (isSyncStructureToLower) it.dbName.lowercase() else it.dbName
                dbName == colName
            }
            if (col == null) return@any false

            val existingType = (existing["type"] as? String)?.lowercase() ?: ""
            val newType = col.dbTypeText.lowercase()
            existingType != newType && newType.isNotEmpty()
        }

        if (needsRebuild && statements.isEmpty()) {
            // Only do temp table rebuild if we have type changes but no new columns
            statements.addAll(rebuildTableWithTempStrategy(table))
        }

        return statements
    }

    private fun rebuildTableWithTempStrategy(table: TableInfo): List<String> {
        val tmpName = "_FreeSqlTmp_${table.dbName}"
        val statements = mutableListOf<String>()

        // 1. Create temp table with new schema
        val tmpTable = TableInfo(
            type = table.type,
            csName = table.csName,
            dbName = tmpName
        )
        table.columns.forEach { tmpTable.addColumn(it) }
        statements.add(createTableDDL(tmpTable))

        // 2. Copy data from old table
        val selectCols = table.columns.joinToString(", ") { col ->
            val dbColName = utils.quoteSqlName(col.dbName)
            if (!col.isNullableCsType) {
                "ifnull($dbColName, '')"
            } else {
                dbColName
            }
        }
        statements.add(
            "INSERT INTO ${utils.quoteSqlName(tmpName)} SELECT $selectCols FROM ${utils.quoteSqlName(table.dbName)}"
        )

        // 3. Drop old table
        statements.add("DROP TABLE IF EXISTS ${utils.quoteSqlName(table.dbName)}")

        // 4. Rename temp to original
        statements.add("ALTER TABLE ${utils.quoteSqlName(tmpName)} RENAME TO ${utils.quoteSqlName(table.dbName)}")

        return statements
    }
}
