package freesql.core

/**
 * Reverse-engineer existing database schema.
 * Port of FreeSql IDbFirst.
 */
interface IDbFirst {
    /** Get all database names. */
    fun getDatabases(): List<String>

    /** Check if a table exists. */
    fun existsTable(tableName: String): Boolean

    /** Get all table metadata from the database. */
    fun getTables(): List<DbTableInfo>

    /** Get all enum types defined in the database (empty for SQLite). */
    fun getEnumsByDatabase(): List<Any>
}

/**
 * Metadata for a database table read from the schema.
 */
class DbTableInfo(
    val name: String,
    val comment: String = "",
    val columns: MutableList<DbColumnInfo> = mutableListOf(),
    val primaryColumns: MutableList<String> = mutableListOf(),
    val identityColumns: MutableList<String> = mutableListOf(),
    val indexes: MutableList<DbIndexInfo> = mutableListOf(),
    val foreignKeys: MutableList<DbForeignKeyInfo> = mutableListOf()
)

/**
 * Metadata for a database column.
 */
class DbColumnInfo(
    val name: String,
    val dbType: String,
    val isNullable: Boolean = false,
    val isPrimary: Boolean = false,
    val isIdentity: Boolean = false,
    val defaultValue: String = "",
    val comment: String = "",
    val maxLen: Int = 0,
    val precision: Int = 0,
    val scale: Int = 0
)

/**
 * Metadata for a database index.
 */
class DbIndexInfo(
    val name: String,
    val columns: List<String>,
    val isUnique: Boolean = false
)

/**
 * Metadata for a foreign key.
 */
class DbForeignKeyInfo(
    val name: String,
    val column: String,
    val refTable: String,
    val refColumn: String
)
