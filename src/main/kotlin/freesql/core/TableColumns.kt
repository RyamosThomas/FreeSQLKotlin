package freesql.core

import freesql.annotation.Column
import freesql.annotation.Table
import freesql.model.TableInfo
import freesql.model.ColumnAttribute
import freesql.model.ColumnInfo
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Base class for type-safe table column definitions.
 * Subclass this to define a table's columns as properties.
 *
 * Usage:
 * ```
 * object Users : TableColumns<User>(User::class) {
 *     val id = int("id")
 *     val name = varchar("name", 255)
 *     val email = varchar("email", 255)
 *     val age = int("age")
 * }
 * ```
 *
 * Then use in queries:
 * ```
 * orm.select<User>().where { Users.age gt 18 }.toList()
 * ```
 */
open class TableColumns<T : Any>(
    /** The entity class this table maps to. */
    val entityClass: KClass<T>,
    /** Optional table name override. Defaults to annotation or class name. */
    tableName: String = ""
) {
    /** Database table name. */
    val tableName: String

    init {
        val tableAnnotation = entityClass.findAnnotation<Table>()
        this.tableName = tableName.ifEmpty {
            tableAnnotation?.name?.ifEmpty { entityClass.simpleName!! } ?: entityClass.simpleName!!
        }
    }

    /** Create a column reference with the given database name. */
    fun col(name: String): ColumnRef<Any?> = ColumnRef(name, this.tableName)

    /** Create an integer column reference. */
    fun int(name: String): ColumnRef<Int> = ColumnRef(name, this.tableName)

    /** Create a long column reference. */
    fun long(name: String): ColumnRef<Long> = ColumnRef(name, this.tableName)

    /** Create a string (varchar) column reference. */
    fun varchar(name: String, length: Int = 255): ColumnRef<String> = ColumnRef(name, this.tableName)

    /** Create a text column reference. */
    fun text(name: String): ColumnRef<String> = ColumnRef(name, this.tableName)

    /** Create a boolean column reference. */
    fun boolean(name: String): ColumnRef<Boolean> = ColumnRef(name, this.tableName)

    /** Create a double column reference. */
    fun double(name: String): ColumnRef<Double> = ColumnRef(name, this.tableName)

    /** Create a float column reference. */
    fun float(name: String): ColumnRef<Float> = ColumnRef(name, this.tableName)

    /** Create a datetime column reference. */
    fun datetime(name: String): ColumnRef<String> = ColumnRef(name, this.tableName)

    /** Create a column reference for any type. */
    fun <R> typed(name: String): ColumnRef<R> = ColumnRef(name, this.tableName)

    /** Raw SQL expression for use in SELECT, WHERE, etc. */
    fun raw(sql: String): RawSqlExpr = RawSqlExpr(sql)

    /** Subquery expression. */
    fun subQuery(sql: String): SubQueryExpr = SubQueryExpr(sql)

    /** Wildcard: all columns. */
    val allColumns: ColumnRef<Any?> get() = ColumnRef("*", this.tableName)

    /** Build the TableInfo from annotations. */
    fun buildTableInfo(): TableInfo {
        val info = TableInfo(
            type = entityClass,
            csName = entityClass.simpleName!!,
            dbName = tableName
        )

        entityClass.memberProperties.forEach { prop ->
            val colAnnotation = prop.findAnnotation<Column>()
            if (colAnnotation != null && colAnnotation.isIgnore) return@forEach

            val isNullable = prop.returnType.isMarkedNullable
            val rawType = prop.returnType.classifier as? KClass<*> ?: return@forEach

            val colAttr = ColumnAttribute(
                dbName = colAnnotation?.name?.ifEmpty { prop.name } ?: prop.name,
                dbOldName = colAnnotation?.oldName ?: "",
                dbType = colAnnotation?.dbType ?: "",
                isPrimary = colAnnotation?.isPrimary ?: false,
                isIdentity = colAnnotation?.isIdentity ?: false,
                isNullable = colAnnotation?.isNullable ?: isNullable,
                isIgnore = colAnnotation?.isIgnore ?: false,
                isVersion = colAnnotation?.isVersion ?: false,
                stringLength = colAnnotation?.stringLength ?: 0,
                precision = colAnnotation?.precision ?: 0,
                scale = colAnnotation?.scale ?: 0,
                canInsert = colAnnotation?.canInsert ?: true,
                canUpdate = colAnnotation?.canUpdate ?: true,
                insertValueSql = colAnnotation?.insertValueSql ?: "",
                serverTime = colAnnotation?.serverTime ?: freesql.annotation.ServerTimeType.NONE,
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

            info.addColumn(colInfo)
        }

        return info
    }
}

/**
 * Join specification.
 */
class JoinClause(
    val type: JoinType,
    val table: String,
    val alias: String = "",
    val on: SqlExpr? = null,
    val onRaw: String = ""
) {
    fun toSql(quote: (String) -> String): String {
        val joinKeyword = when (type) {
            JoinType.LEFT -> "LEFT JOIN"
            JoinType.INNER -> "INNER JOIN"
            JoinType.RIGHT -> "RIGHT JOIN"
            JoinType.CROSS -> "CROSS JOIN"
        }
        val tableRef = if (alias.isNotEmpty()) "${quote(table)} $alias" else quote(table)
        val onClause = when {
            on != null -> " ON ${on!!.toSql(quote)}"
            onRaw.isNotEmpty() -> " ON $onRaw"
            else -> ""
        }
        return "$joinKeyword $tableRef$onClause"
    }
}

enum class JoinType { LEFT, INNER, RIGHT, CROSS }
