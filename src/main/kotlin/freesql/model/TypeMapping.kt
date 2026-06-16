package freesql.model

import kotlin.reflect.KClass

/**
 * Result of mapping a Kotlin type to a SQLite database type.
 * Port of FreeSql CsToDb / DbInfoResult.
 */
class DbTypeInfo(
    /** The JDBC DbType equivalent. */
    val type: DbType,
    /** Short database type name (e.g. "integer"). */
    val dbType: String,
    /** Full type with NOT NULL (e.g. "integer NOT NULL"). */
    val dbTypeFull: String,
    /** Is the DB type nullable. */
    val isNullable: Boolean = false,
    /** Default value expression. */
    val defaultValue: String = ""
)

/**
 * Simplified DbType enum for SQLite type affinity.
 */
enum class DbType {
    Boolean,
    Byte,
    Int16,
    Int32,
    Int64,
    Single,
    Double,
    Decimal,
    DateTime,
    String,
    AnsiString,
    Binary,
    Guid,
    TimeSpan,
    Object
}

/**
 * Kotlin-to-SQLite type mapping.
 * Port of FreeSql SqliteCodeFirst._dicCsToDb.
 */
object TypeMapping {
    /** Map of Kotlin type → (dbType, dbTypeFull, isNullable, defaultValue). */
    val kotlinToDb: Map<KClass<*>, List<DbTypeInfo>> by lazy {
        buildMap {
            fun add(
                type: KClass<*>,
                dbType: DbType,
                db: String,
                nullable: Boolean = false,
                default: String = ""
            ) {
                val full = if (nullable) db else "$db NOT NULL"
                put(type, listOf(
                    DbTypeInfo(type = dbType, dbType = db, dbTypeFull = db, isNullable = nullable, defaultValue = default),
                    DbTypeInfo(type = dbType, dbType = db, dbTypeFull = full, isNullable = false, defaultValue = default)
                ))
            }

            // Boolean
            add(Boolean::class, DbType.Boolean, "boolean")
            // Byte
            add(Byte::class, DbType.Byte, "int2")
            // Short
            add(Short::class, DbType.Int16, "smallint")
            // Int
            add(Int::class, DbType.Int32, "integer")
            // Long
            add(Long::class, DbType.Int64, "integer")
            // Float
            add(Float::class, DbType.Single, "float")
            // Double
            add(Double::class, DbType.Double, "double")
            // BigDecimal
            add(java.math.BigDecimal::class, DbType.Decimal, "decimal(10,2)")
            // ByteArray
            add(ByteArray::class, DbType.Binary, "blob")
            // String
            put(String::class, listOf(
                DbTypeInfo(type = DbType.String, dbType = "nvarchar(255)", dbTypeFull = "nvarchar(255)", isNullable = true),
                DbTypeInfo(type = DbType.String, dbType = "nvarchar(255)", dbTypeFull = "nvarchar(255) NOT NULL", isNullable = false)
            ))
            // Char
            add(Char::class, DbType.AnsiString, "char(1)")
            // java.util.UUID
            add(java.util.UUID::class, DbType.AnsiString, "character(36)")
            // java.time.LocalDateTime
            add(java.time.LocalDateTime::class, DbType.DateTime, "datetime")
            // java.time.Instant
            add(java.time.Instant::class, DbType.DateTime, "datetime")
            // java.util.Date
            add(java.util.Date::class, DbType.DateTime, "datetime")
        }
    }

    /** Nullable Kotlin type counterpart lookup. */
    val nullableMap: Map<KClass<*>, KClass<*>> by lazy {
        mapOf(
            Boolean::class to Boolean::class,
            Byte::class to Byte::class,
            Short::class to Short::class,
            Int::class to Int::class,
            Long::class to Long::class,
            Float::class to Float::class,
            Double::class to Double::class
        )
    }

    /**
     * Get the SQLite database type info for a Kotlin type.
     * Returns the nullable entry if [isNullable] is true, NOT NULL otherwise.
     */
    fun getDbType(kotlinType: KClass<*>, isNullable: Boolean = false): DbTypeInfo? {
        val entries = kotlinToDb[kotlinType] ?: return null
        return if (isNullable) entries.getOrNull(0) else entries.getOrNull(1) ?: entries.getOrNull(0)
    }
}
