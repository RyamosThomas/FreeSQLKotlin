package freesql.annotation

/**
 * Marks a class as a custom SQL expression function.
 * Port of FreeSql [ExpressionCall] attribute.
 *
 * Usage:
 *   @ExpressionCall
 *   class MyFunctions {
 *       companion object {
 *           @JvmStatic
 *           fun format(column: ColumnRef<String>, format: String): SqlExpr {
 *               return SqlExpr.Raw("printf(${column.toSql(null)}, '$format')")
 *           }
 *       }
 *   }
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExpressionCall
