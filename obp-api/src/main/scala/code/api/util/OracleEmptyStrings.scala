package code.api.util

import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method, Proxy}
import java.sql.{CallableStatement, Connection, PreparedStatement, ResultSet, Statement}

/**
 * Oracle stores an empty string as NULL, so `col = ''` is never true there and a value written as "" reads back
 * as null. OBP uses "" as an ordinary value - the bank id of a system-level Role, an optional id that is absent -
 * and every other database it runs on keeps "" and NULL apart.
 *
 * On Oracle, connections from the pool are wrapped so that the difference survives: an empty string bound to a
 * statement is written as a one-character sentinel, CHR(0), a sentinel read back is returned as "", and an empty
 * literal '' in the SQL text becomes CHR(0) as well. To the application Oracle then behaves as the other
 * databases do. A tool reading the Oracle tables directly sees CHR(0) where the application stored "".
 */
object OracleEmptyStrings {

  /** What an empty string is stored as on Oracle. */
  val Sentinel: String = "\u0000"

  def wrap(connection: Connection): Connection = proxy(classOf[Connection], connection, new ConnectionHandler(connection))

  private def proxy[A](interface: Class[A], target: AnyRef, handler: InvocationHandler): A =
    Proxy.newProxyInstance(getClass.getClassLoader, Array[Class[?]](interface), handler).asInstanceOf[A]

  private def call(target: AnyRef, method: Method, args: Array[AnyRef]): AnyRef =
    try method.invoke(target, (if (args == null) Array.empty[AnyRef] else args)*)
    catch { case e: InvocationTargetException => throw e.getCause }

  /**
   * `sql` with every empty literal '' written as CHR(0). Quoted text, double-quoted identifiers and comments are
   * skipped, so an escaped quote inside a literal ('it''s') is left as it is.
   */
  def rewriteSql(sql: String): String = {
    if (sql == null || !sql.contains("''")) return sql
    val out = new StringBuilder(sql.length + 16)
    var i = 0
    val n = sql.length
    while (i < n) {
      val c = sql.charAt(i)
      if (c == '\'') {
        if (i + 1 < n && sql.charAt(i + 1) == '\'' && (i + 2 >= n || sql.charAt(i + 2) != '\'')) {
          out.append("CHR(0)"); i += 2
        } else {
          // A literal with something in it: copy through to its closing quote, '' inside being an escaped quote.
          out.append(c); i += 1
          var closed = false
          while (i < n && !closed) {
            val d = sql.charAt(i)
            out.append(d); i += 1
            if (d == '\'') {
              if (i < n && sql.charAt(i) == '\'') { out.append('\''); i += 1 } else closed = true
            }
          }
        }
      } else if (c == '"') {
        val end = sql.indexOf('"', i + 1)
        val stop = if (end < 0) n else end + 1
        out.append(sql.substring(i, stop)); i = stop
      } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
        val end = sql.indexOf('\n', i)
        val stop = if (end < 0) n else end
        out.append(sql.substring(i, stop)); i = stop
      } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
        val end = sql.indexOf("*/", i + 2)
        val stop = if (end < 0) n else end + 2
        out.append(sql.substring(i, stop)); i = stop
      } else {
        out.append(c); i += 1
      }
    }
    out.toString
  }

  private def written(value: AnyRef): AnyRef = value match {
    case s: String if s.isEmpty => Sentinel
    case other => other
  }

  private def read(value: AnyRef): AnyRef = value match {
    case Sentinel => ""
    case other => other
  }

  private class ConnectionHandler(target: Connection) extends InvocationHandler {
    def invoke(self: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
      case "prepareStatement" | "prepareCall" | "nativeSQL" if args != null && args.nonEmpty =>
        args(0) = rewriteSql(args(0).asInstanceOf[String])
        call(target, method, args) match {
          case cs: CallableStatement => proxy(classOf[CallableStatement], cs, new StatementHandler(cs, self))
          case ps: PreparedStatement => proxy(classOf[PreparedStatement], ps, new StatementHandler(ps, self))
          case other => other
        }
      case "createStatement" =>
        call(target, method, args) match {
          case st: Statement => proxy(classOf[Statement], st, new StatementHandler(st, self))
          case other => other
        }
      case _ => call(target, method, args)
    }
  }

  private class StatementHandler(target: Statement, connection: AnyRef) extends InvocationHandler {
    def invoke(self: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
      method.getName match {
        case "execute" | "executeQuery" | "executeUpdate" | "executeLargeUpdate" | "addBatch"
          if args != null && args.nonEmpty && args(0).isInstanceOf[String] =>
          args(0) = rewriteSql(args(0).asInstanceOf[String])
        case "setString" | "setNString" | "setObject" if args != null && args.length >= 2 =>
          args(1) = written(args(1))
        case _ =>
      }
      if (method.getName == "getConnection") connection
      else call(target, method, args) match {
        case rs: ResultSet => proxy(classOf[ResultSet], rs, new ResultSetHandler(rs, self))
        case other => other
      }
    }
  }

  private class ResultSetHandler(target: ResultSet, statement: AnyRef) extends InvocationHandler {
    def invoke(self: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
      case "getString" | "getNString" | "getObject" => read(call(target, method, args))
      case "getStatement" => statement
      case _ => call(target, method, args)
    }
  }
}
