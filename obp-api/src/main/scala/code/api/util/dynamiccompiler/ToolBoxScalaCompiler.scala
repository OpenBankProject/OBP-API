package code.api.util.dynamiccompiler

import code.util.Helper.MdcLoggable

import java.util.concurrent.ConcurrentHashMap
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.runtimeMirror
import scala.tools.reflect.{ToolBox, ToolBoxError}
import scala.util.control.NonFatal

/**
 * Scala 2.13 implementation of [[DynamicScalaCompiler]], using the reflection ToolBox.
 *
 * Behaviour is carried over from `DynamicUtil` unchanged, including two things that look
 * like accidents and are not:
 *
 *  - the retry. The ToolBox intermittently fails the first `compile` of a tree and succeeds
 *    on an identical second call, so a first failure is retried once before being reported.
 *    Dropping the retry turns a ToolBox quirk into an intermittent product failure.
 *  - the split between a compile error and an evaluation error. A `ToolBoxError` becomes a
 *    failure with no cause; anything thrown while evaluating the compiled code becomes a
 *    failure that carries the exception, which is how a customer's failing method_body keeps
 *    its stack trace.
 *
 * Replaced at the Scala 3 flip by a `dotty.tools.dotc`-based implementation; see the
 * interface for why the compiler cannot stay on 2.13.
 */
object ToolBoxScalaCompiler extends DynamicScalaCompiler with MdcLoggable {

  private val toolBox: ToolBox[universe.type] = runtimeMirror(getClass.getClassLoader).mkToolBox()

  // Keyed by source text: the same code always yields the same value, and dynamic endpoints
  // re-submit identical source on every request.
  private val compiled = new ConcurrentHashMap[String, Either[DynamicCompileFailure, Any]]()

  def cachedCount: Int = compiled.size()

  /**
   * Dry run: compile for diagnostics only, evaluate nothing, cache nothing.
   *
   * This is upstream's own ToolBox implementation of the check (develop commit bdcb4e671), kept
   * on the 2.13 side where a ToolBox exists at all. The front end is collected explicitly because
   * the default one keeps only the messages, and positions are what a caller wants to show against
   * the submitted body. A second ToolBox so a dry-run check never touches the memoised compile
   * results of the real one; ToolBoxes are not thread-safe, so checks are serialised.
   */
  private class CollectingFrontEnd extends scala.tools.reflect.FrontEnd {
    // FrontEnd.log already records every diagnostic in `infos`; nothing to print.
    override def display(info: Info): Unit = ()
  }
  private val checkFrontEnd = new CollectingFrontEnd
  private val checkToolBox: ToolBox[universe.type] =
    runtimeMirror(getClass.getClassLoader).mkToolBox(frontEnd = checkFrontEnd)

  def check(code: String): List[DynamicCompileDiagnostic] = checkToolBox.synchronized {
    checkFrontEnd.reset()
    val failure: Option[String] =
      try { checkToolBox.typecheck(checkToolBox.parse(code)); None }
      catch { case e: ToolBoxError => Some(e.message) }
    val collected = checkFrontEnd.infos.toList.filter(_.severity == checkFrontEnd.ERROR).map { info =>
      val (line, column) =
        if (info.pos != null && info.pos.isDefined) (info.pos.line, info.pos.column) else (0, 0)
      DynamicCompileDiagnostic(line, column, "ERROR", info.msg)
    }
    if (collected.nonEmpty) collected
    else failure.map(m => DynamicCompileDiagnostic(0, 0, "ERROR",
      m.stripPrefix("reflective typecheck has failed:")
       .stripPrefix("reflective compilation has failed:").trim)).toList
  }

  def compile(code: String): Either[DynamicCompileFailure, Any] = {
    logger.trace(s"ToolBoxScalaCompiler cache size is ${compiled.size()}")
    compiled.computeIfAbsent(code, _ => {
      val tree =
        try Right(toolBox.parse(code))
        catch { case e: ToolBoxError => Left(DynamicCompileFailure(e.message)) }

      tree.flatMap { t =>
        val fn =
          try Right(toolBox.compile(t))
          catch {
            case _: ToolBoxError =>
              // Known ToolBox flakiness: compiling the same tree again usually succeeds.
              try Right(toolBox.compile(t))
              catch { case e: ToolBoxError => Left(DynamicCompileFailure(e.message)) }
          }
        fn.flatMap { f =>
          try Right(f())
          catch { case NonFatal(e) => Left(DynamicCompileFailure(e.getMessage, Some(e))) }
        }
      }
    })
  }
}
