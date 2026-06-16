package freesql

import freesql.core.IFreeSql
import freesql.provider.sqlite.SqliteProvider

/**
 * Builder for creating IFreeSql instances.
 * Port of FreeSql FreeSqlBuilder.
 */
class FreeSqlBuilder {

    private var dataType: String = "sqlite"
    private var masterConnectionString: String = ""
    private var autoSyncStructure: Boolean = false
    private var syncStructureToLower: Boolean = false
    private var syncStructureToUpper: Boolean = false
    private var noneCommandParameter: Boolean = false

    /**
     * Set the database type. Currently only "sqlite" is supported.
     */
    fun useDataType(dataType: String): FreeSqlBuilder {
        this.dataType = dataType.lowercase()
        return this
    }

    /**
     * Set the master connection string.
     * SQLite example: "jdbc:sqlite:mydb.db" or "jdbc:sqlite::memory:"
     */
    fun useConnectionString(connectionString: String): FreeSqlBuilder {
        this.masterConnectionString = connectionString
        return this
    }

    /**
     * Enable auto-sync of entity structures (auto-migration).
     */
    fun useAutoSyncStructure(enable: Boolean = true): FreeSqlBuilder {
        this.autoSyncStructure = enable
        return this
    }

    /**
     * Convert table/column names to lowercase in DDL.
     */
    fun useSyncStructureToLower(enable: Boolean = true): FreeSqlBuilder {
        this.syncStructureToLower = enable
        return this
    }

    /**
     * Convert table/column names to uppercase in DDL.
     */
    fun useSyncStructureToUpper(enable: Boolean = true): FreeSqlBuilder {
        this.syncStructureToUpper = enable
        return this
    }

    /**
     * Disable parameterized queries (inline values).
     */
    fun useNoneCommandParameter(enable: Boolean = true): FreeSqlBuilder {
        this.noneCommandParameter = enable
        return this
    }

    /**
     * Build the IFreeSql instance.
     */
    fun build(): IFreeSql {
        require(masterConnectionString.isNotEmpty()) { "Connection string is required" }

        val provider = when (dataType) {
            "sqlite" -> SqliteProvider(masterConnectionString)
            else -> throw UnsupportedOperationException("Unsupported data type: $dataType")
        }

        // Apply configuration
        provider.codeFirst.isAutoSyncStructure = autoSyncStructure
        provider.codeFirst.isSyncStructureToLower = syncStructureToLower
        provider.codeFirst.isSyncStructureToUpper = syncStructureToUpper
        provider.codeFirst.isNoneCommandParameter = noneCommandParameter

        return provider
    }
}

/**
 * Top-level DSL function for creating a FreeSql instance.
 *
 * Usage:
 * ```kotlin
 * val orm = freeSql {
 *     useDataType("sqlite")
 *     useConnectionString("jdbc:sqlite:mydb.db")
 *     useAutoSyncStructure()
 * }
 * ```
 */
fun freeSql(block: FreeSqlBuilder.() -> Unit): IFreeSql {
    return FreeSqlBuilder().apply(block).build()
}
