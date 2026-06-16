package freesql.provider.sqlite

/**
 * Entity configuration set via fluent API (ConfigEntity).
 * Merged with annotation-based config — fluent API takes priority.
 */
data class EntityConfig(
    val tableName: String? = null,
    val columnConfigs: Map<String, ColumnConfig> = emptyMap()
)

data class ColumnConfig(
    val dbName: String? = null,
    val dbType: String? = null,
    val isPrimary: Boolean? = null,
    val isIdentity: Boolean? = null,
    val isNullable: Boolean? = null,
    val isIgnore: Boolean? = null,
    val stringLength: Int? = null,
    val canInsert: Boolean? = null,
    val canUpdate: Boolean? = null,
    val insertValueSql: String? = null
)

/**
 * Builder for EntityConfig using fluent API.
 * Usage:
 *   codeFirst.configEntity(User::class) {
 *       tableName = "sys_user"
 *       column("name") {
 *           dbName = "user_name"
 *           stringLength = 200
 *       }
 *   }
 */
class EntityConfigBuilder {
    var tableName: String? = null
    private val columnConfigs = mutableMapOf<String, ColumnConfigBuilder>()

    fun column(propertyName: String, block: ColumnConfigBuilder.() -> Unit) {
        columnConfigs[propertyName] = ColumnConfigBuilder().apply(block)
    }

    fun build(): EntityConfig = EntityConfig(
        tableName = tableName,
        columnConfigs = columnConfigs.mapValues { (_, builder) -> builder.build() }
    )
}

class ColumnConfigBuilder {
    var dbName: String? = null
    var dbType: String? = null
    var isPrimary: Boolean? = null
    var isIdentity: Boolean? = null
    var isNullable: Boolean? = null
    var isIgnore: Boolean? = null
    var stringLength: Int? = null
    var canInsert: Boolean? = null
    var canUpdate: Boolean? = null
    var insertValueSql: String? = null

    fun build(): ColumnConfig = ColumnConfig(
        dbName = dbName,
        dbType = dbType,
        isPrimary = isPrimary,
        isIdentity = isIdentity,
        isNullable = isNullable,
        isIgnore = isIgnore,
        stringLength = stringLength,
        canInsert = canInsert,
        canUpdate = canUpdate,
        insertValueSql = insertValueSql
    )
}
