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
