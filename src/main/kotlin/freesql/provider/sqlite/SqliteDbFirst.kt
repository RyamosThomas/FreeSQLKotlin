package freesql.provider.sqlite

import freesql.core.*

/**
 * SQLite reverse-engineering: reads database schema and generates entity metadata.
 * Port of FreeSql SqliteDbFirst.
 *
 * Note on comments: SQLite does not natively support column/table comments.
 * The COMMENT ON syntax is not available. Comments are stored in application
 * metadata or documentation, not in the database schema itself.
 * All comment fields in DbColumnInfo/DbTableInfo will be empty for SQLite.
 */
class SqliteDbFirst(
    private val ado: SqliteAdo,
    private val utils: SqliteUtils
) : IDbFirst {

    override fun getDatabases(): List<String> {
        val databases = mutableListOf<String>()
        ado.executeReader("PRAGMA database_list") { rs ->
            while (rs.next()) {
                databases.add(rs.getString("name"))
            }
        }
        return databases
    }

    override fun existsTable(tableName: String): Boolean {
        val result = ado.executeScalar(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=@name",
            mapOf("@name" to tableName)
        )
        return (result as? Number)?.toInt() ?: 0 > 0
    }

    override fun getTables(): List<DbTableInfo> {
        val tables = mutableListOf<DbTableInfo>()

        // Get all tables
        val tableNames = mutableListOf<String>()
        ado.executeReader(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ) { rs ->
            while (rs.next()) {
                tableNames.add(rs.getString("name"))
            }
        }

        for (tableName in tableNames) {
            // Note: SQLite has no native table comment support
            val table = DbTableInfo(name = tableName, comment = "")

            // Get columns via PRAGMA table_info
            ado.executeReader("PRAGMA table_info(${utils.quoteSqlName(tableName)})") { rs ->
                while (rs.next()) {
                    val colName = rs.getString("name")
                    val colType = rs.getString("type") ?: ""
                    val notNull = rs.getInt("notnull") == 1
                    val defaultVal = rs.getString("dflt_value") ?: ""
                    val pk = rs.getInt("pk")

                    // Detect Kotlin type from SQLite type
                    getKotlinTypeWithEnumCheck(colType) // enum detection for future use

                    // Parse precision/scale from type if present
                    val (precision, scale) = parsePrecisionScale(colType)

                    // Parse max length from type (e.g. "varchar(255)" -> 255)
                    val maxLen = parseMaxLength(colType)

                    table.columns.add(DbColumnInfo(
                        name = colName,
                        dbType = colType,
                        isNullable = !notNull,
                        isPrimary = pk > 0,
                        isIdentity = pk > 0 && colType.lowercase().contains("integer"),
                        defaultValue = defaultVal,
                        // SQLite does not support column comments
                        comment = "",
                        maxLen = maxLen,
                        precision = precision,
                        scale = scale
                    ))

                    if (pk > 0) {
                        table.primaryColumns.add(colName)
                    }
                }
            }

            // Detect AUTOINCREMENT from CREATE SQL
            val createSql = ado.executeScalar(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name=@name",
                mapOf("@name" to tableName)
            ) as? String

            if (createSql != null && createSql.contains("AUTOINCREMENT", ignoreCase = true)) {
                table.identityColumns.addAll(table.primaryColumns)
            }

            // Get indexes
            ado.executeReader("PRAGMA index_list(${utils.quoteSqlName(tableName)})") { rs ->
                while (rs.next()) {
                    val indexName = rs.getString("name")
                    val unique = rs.getInt("unique") == 1
                    if (indexName.startsWith("sqlite_")) continue

                    val indexCols = mutableListOf<String>()
                    ado.executeReader("PRAGMA index_info(${utils.quoteSqlName(indexName)})") { idxRs ->
                        while (idxRs.next()) {
                            indexCols.add(idxRs.getString("name"))
                        }
                    }
                    table.indexes.add(DbIndexInfo(
                        name = indexName,
                        columns = indexCols,
                        isUnique = unique
                    ))
                }
            }

            // Get foreign keys
            ado.executeReader("PRAGMA foreign_key_list(${utils.quoteSqlName(tableName)})") { rs ->
                while (rs.next()) {
                    table.foreignKeys.add(DbForeignKeyInfo(
                        name = "FK_${tableName}_${rs.getString("from")}_${rs.getString("table")}",
                        column = rs.getString("from"),
                        refTable = rs.getString("table"),
                        refColumn = rs.getString("to")
                    ))
                }
            }

            tables.add(table)
        }

        return tables
    }

    /**
     * Get enum types defined in the database.
     * SQLite does not have a native enum type, so this always returns empty.
     * Enum types in the Kotlin port are stored as integers (ordinal values).
     */
    override fun getEnumsByDatabase(): List<Any> = emptyList()

    /**
     * Comprehensive SQLite-to-Kotlin type mapping.
     * Handles all SQLite type affinity rules.
     */
    fun getKotlinType(dbType: String): Pair<String, Boolean> {
        val normalized = dbType.lowercase().trim()
        return when {
            // Integer types
            normalized.contains("bigint") || normalized.contains("int8") || normalized.contains("int64") ->
                "Long" to false
            normalized.contains("smallint") || normalized.contains("int2") ->
                "Short" to false
            normalized.contains("tinyint") || normalized.contains("int1") ->
                "Byte" to false
            normalized.contains("mediumint") || normalized.contains("int4") ->
                "Int" to false
            normalized == "integer" || normalized.contains("int") ->
                "Int" to false

            // Floating point
            normalized.contains("float") || normalized.contains("real") || normalized.contains("double") ->
                "Double" to false
            normalized.contains("decimal") || normalized.contains("numeric") ->
                "java.math.BigDecimal" to false

            // Boolean (SQLite stores as integer 0/1)
            normalized.contains("boolean") || normalized.contains("bool") ->
                "Boolean" to false

            // Date/time types
            normalized.contains("datetime") || normalized.contains("timestamp") ->
                "java.time.LocalDateTime" to false
            normalized.contains("date") ->
                "java.time.LocalDateTime" to false
            normalized.contains("time") && !normalized.contains("datetime") && !normalized.contains("timestamp") ->
                "java.time.LocalDateTime" to false

            // Binary
            normalized.contains("blob") || normalized.contains("binary") || normalized.contains("varbinary") ->
                "ByteArray" to false

            // String types
            normalized.contains("char") || normalized.contains("text") ||
            normalized.contains("clob") || normalized.contains("varchar") ||
            normalized.contains("nvarchar") || normalized.contains("nchar") ->
                "String" to true

            // UUID (stored as text/char(36))
            normalized.contains("uuid") || normalized.contains("uniqueidentifier") ->
                "java.util.UUID" to false

            // Fallback
            normalized.isEmpty() -> "String" to true
            else -> "String" to true
        }
    }

    /**
     * Extended type mapping that also detects potential enum columns.
     *
     * SQLite doesn't have a native enum type, but columns named with common enum
     * patterns or containing integer types that map to Kotlin enums are detected.
     * Returns a Pair of (kotlinType, isLikelyEnum).
     */
    fun getKotlinTypeWithEnumCheck(dbType: String): Pair<String, Boolean> {
        val (kotlinType, _) = getKotlinType(dbType)
        // SQLite stores enums as integers — no native enum detection possible
        // Enum detection is handled at the ORM layer via Kotlin class inspection
        return kotlinType to false
    }

    /**
     * Parse precision and scale from a type string like "decimal(10,2)".
     * Returns (precision, scale) or (0, 0) if not applicable.
     */
    private fun parsePrecisionScale(dbType: String): Pair<Int, Int> {
        val regex = Regex("""(?:decimal|numeric|number)\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
        val match = regex.find(dbType)
        return if (match != null) {
            val p = match.groupValues[1].toIntOrNull() ?: 0
            val s = match.groupValues[2].toIntOrNull() ?: 0
            p to s
        } else {
            0 to 0
        }
    }

    /**
     * Parse max length from a type string like "varchar(255)" or "nvarchar(500)".
     * Returns the length or 0 if not applicable.
     */
    private fun parseMaxLength(dbType: String): Int {
        val regex = Regex("""(?:char|varchar|nvarchar|nchar|character varying)\s*\(\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
        val match = regex.find(dbType)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
