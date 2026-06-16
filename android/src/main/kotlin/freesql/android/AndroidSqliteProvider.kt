package freesql.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import freesql.provider.sqlite.SqliteCodeFirst
import freesql.provider.sqlite.SqliteExpression
import freesql.provider.sqlite.SqliteUtils
import freesql.provider.sqlite.curd.SqliteDelete
import freesql.provider.sqlite.curd.SqliteInsert
import freesql.provider.sqlite.curd.SqliteInsertOrUpdate
import freesql.provider.sqlite.curd.SqliteSelect
import freesql.provider.sqlite.curd.SqliteUpdate
import freesql.core.CurdAfterEventArgs
import freesql.core.CurdBeforeEventArgs
import freesql.core.DbTableInfo
import freesql.core.ExecutingSqlInfo
import freesql.core.GlobalFilter
import freesql.core.IAdo
import freesql.core.IAop
import freesql.core.ICodeFirst
import freesql.core.IDbFirst
import freesql.core.IDelete
import freesql.core.IFreeSql
import freesql.core.IInsert
import freesql.core.IInsertOrUpdate
import freesql.core.ISelect
import freesql.core.IUpdate
import freesql.core.SyncStructureEventArgs

import kotlin.reflect.KClass

class AndroidSqliteProvider(
    context: Context,
    databaseName: String
) : IFreeSql
{
    private val database: SQLiteDatabase
    private val adoInstance: AndroidSqliteAdo
    private val aopInstance = SimpleAop()
    private val utilsInstance = SqliteUtils()
    private val expressionInstance = SqliteExpression(utilsInstance)
    private lateinit var codeFirstInstance: SqliteCodeFirst

    init
    {
        val helper = object : SQLiteOpenHelper(context, databaseName, null, 1)
        {
            override fun onCreate(db: SQLiteDatabase) {}
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        database = helper.writableDatabase
        adoInstance = AndroidSqliteAdo(database)
        adoInstance.aop = aopInstance
        codeFirstInstance = SqliteCodeFirst(adoInstance, utilsInstance)
    }

    override val ado: IAdo get() = adoInstance
    override val aop: IAop get() = aopInstance
    override val codeFirst: ICodeFirst get() = codeFirstInstance
    override val dbFirst: IDbFirst = NoOpDbFirst()
    override val globalFilter: GlobalFilter = GlobalFilter()

    override fun <T : Any> select(clazz: KClass<T>): ISelect<T>
    {
        return SqliteSelect(
            entityType = clazz,
            ado = adoInstance,
            utils = utilsInstance,
            expression = expressionInstance,
            codeFirst = codeFirstInstance,
            globalFilter = globalFilter
        )
    }

    override fun <T : Any> insert(clazz: KClass<T>): IInsert<T>
    {
        return SqliteInsert(
            entityType = clazz,
            ado = adoInstance,
            utils = utilsInstance,
            codeFirst = codeFirstInstance
        )
    }

    override fun <T : Any> update(clazz: KClass<T>): IUpdate<T>
    {
        return SqliteUpdate(
            entityType = clazz,
            ado = adoInstance,
            utils = utilsInstance,
            codeFirst = codeFirstInstance
        ).withGlobalFilter(globalFilter)
    }

    override fun <T : Any> delete(clazz: KClass<T>): IDelete<T>
    {
        return SqliteDelete(
            entityType = clazz,
            ado = adoInstance,
            utils = utilsInstance,
            codeFirst = codeFirstInstance
        ).withGlobalFilter(globalFilter)
    }

    override fun <T : Any> insertOrUpdate(clazz: KClass<T>): IInsertOrUpdate<T>
    {
        return SqliteInsertOrUpdate(
            entityType = clazz,
            ado = adoInstance,
            utils = utilsInstance,
            codeFirst = codeFirstInstance
        )
    }

    override fun transaction(action: () -> Unit)
    {
        adoInstance.transaction(action)
    }

    override fun executeNonQuery(sql: String, vararg params: Any?): Int
    {
        return adoInstance.executeNonQuery(sql)
    }

    fun close()
    {
        database.close()
    }
}

private class SimpleAop : IAop
{
    override var executingSql: ((ExecutingSqlInfo) -> Unit)? = null
    override var dbException: ((Exception) -> Unit)? = null
    override var curdBefore: ((CurdBeforeEventArgs) -> Unit)? = null
    override var curdAfter: ((CurdAfterEventArgs) -> Unit)? = null
    override var syncStructureBefore: ((SyncStructureEventArgs) -> Unit)? = null
    override var syncStructureAfter: ((SyncStructureEventArgs) -> Unit)? = null
}


private class NoOpDbFirst : IDbFirst
{
    override fun getDatabases(): List<String> = emptyList()
    override fun existsTable(tableName: String): Boolean = false
    override fun getTables(): List<DbTableInfo> = emptyList()
    override fun getEnumsByDatabase(): List<Any> = emptyList()
}
