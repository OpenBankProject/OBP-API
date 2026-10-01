/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util

import org.json4s._
import code.api.Constant.SHOW_USED_CONNECTOR_METHODS
import code.api.{APIFailureNewStyle, JsonResponseException}
import code.api.util.ErrorMessages.DynamicResourceDocMethodDependency
import cats.effect.IO
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.Functions.Memo
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import javassist.{ClassPool, LoaderClassPath}
import net.liftweb.common.{Box, Empty, Failure, Full, ParamFailure}
import org.json4s.{Extraction, JValue}
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.apache.commons.lang3.StringUtils
import org.graalvm.polyglot.{Context, Engine, HostAccess, PolyglotAccess}

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.script.ScriptEngineManager
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.runtimeMirror
import scala.runtime.NonLocalReturnControl
import scala.tools.reflect.{ToolBox, ToolBoxError}

object DynamicUtil extends MdcLoggable{

  // Master kill-switch for user-generated dynamic code (RCE surface). Defaults to OFF
  // everywhere — including test/dev — unless explicitly enabled via this prop. Tests that
  // exercise dynamic-code compilation must set allow_user_generated_scala_code=true
  // explicitly (see test.default.props / the CI "Setup props" step).
  def dynamicCodeExecutionEnabled: Boolean =
    APIUtil.getPropsValue("allow_user_generated_scala_code") match {
      case Full(v) => v.toBoolean
      case _ => false
    }

  val toolBox: ToolBox[universe.type] = runtimeMirror(getClass.getClassLoader).mkToolBox()

  /** One compiler diagnostic from [[checkScalaCode]]. `line`/`column` are 1-based in the code that was checked; 0 when unknown. */
  case class CompileProblem(line: Int, column: Int, severity: String, message: String)

  /** Collects compiler diagnostics with positions; the default toolbox front end only keeps the messages. */
  private class CollectingFrontEnd extends scala.tools.reflect.FrontEnd {
    // FrontEnd.log already records every diagnostic in `infos`; nothing to print.
    override def display(info: Info): Unit = ()
  }
  private val checkFrontEnd = new CollectingFrontEnd
  // A second toolbox, so a dry-run check never touches the memoised compile results of the real one.
  private val checkToolBox: ToolBox[universe.type] = runtimeMirror(getClass.getClassLoader).mkToolBox(frontEnd = checkFrontEnd)

  /**
   * Compile `code` for diagnostics only: nothing is evaluated, nothing is cached. Empty list = compiles.
   * Toolboxes are not thread-safe, so checks are serialised. Callers must apply the kill switch and a role.
   *
   * The code is checked inside a method, because that is how it is really compiled: ToolBox.compile
   * wraps the tree in a method before compiling it. Checked bare, a `return` in the code (which the
   * documented example method body uses to answer early with an error) is reported as "return
   * outside method definition", although a real create accepts it. The wrapper is added on the same
   * line as the start of the code, so line numbers are unchanged; columns on that first line are
   * corrected for it.
   */
  def checkScalaCode(code: String): List[CompileProblem] = checkToolBox.synchronized {
    checkFrontEnd.reset()
    val wrapperStart = "{ def dryRunCompileWrapper(): Any = { "
    val wrapperEnd = "\n}; () }"
    val failure: Option[String] = try {
      checkToolBox.typecheck(checkToolBox.parse(wrapperStart + code + wrapperEnd))
      None
    } catch {
      case e: ToolBoxError => Some(e.message)
    }
    val collected = checkFrontEnd.infos.toList.filter(_.severity == checkFrontEnd.ERROR).map { info =>
      val (line, column) = if (info.pos != null && info.pos.isDefined) (info.pos.line, info.pos.column) else (0, 0)
      val columnInCode = if (line == 1 && column > wrapperStart.length) column - wrapperStart.length else column
      CompileProblem(line, columnInCode, "ERROR", info.msg)
    }
    if (collected.nonEmpty) collected
    else failure.map(m => CompileProblem(0, 0, "ERROR", m.stripPrefix("reflective typecheck has failed:").stripPrefix("reflective compilation has failed:").trim)).toList
  }

  /**
   * This compiles a Java `method_body` for diagnostics only, the Java counterpart of
   * [[checkScalaCode]]. Empty list = compiles. Line numbers are relative to the body the author
   * wrote.
   *
   * The source is prepared exactly as createJavaHttp4sEndpoint prepares it (the author's `package`
   * line replaced by a fresh generated package), and compiled by the same system Java compiler with
   * the same in-memory file manager, so it reports what a real create would report. Unlike the real
   * compile, nothing is loaded or constructed: java-scriptengine's own compile also instantiates the
   * class, which would run the author's constructor and static initialisers. Nothing is cached.
   * Callers must apply the kill switch and a role.
   */
  def checkJavaCode(methodBody: String): List[CompileProblem] = {
    import javax.tools.{Diagnostic, DiagnosticCollector, JavaFileObject, ToolProvider}
    import scala.jdk.CollectionConverters._

    val compiler = ToolProvider.getSystemJavaCompiler
    if (compiler == null) {
      List(CompileProblem(0, 0, "ERROR", "No Java compiler is available: this server runs on a Java runtime without the compiler (a JRE rather than a JDK)."))
    } else {
      // One line is added above the author's code: the generated package statement.
      val linesAdded = 1
      val packageMatcher = Pattern.compile("""(?m)^\s*package\s+\S+?\s*;""").matcher(methodBody)
      val packageName = "code.api.util.dynamic." + UUID.randomUUID().toString.replaceAll("^|-", "_")
      val javaCode = s"package $packageName;\n${packageMatcher.replaceFirst("")}\n"

      (Box tryo { new ch.obermuhlner.scriptengine.java.name.DefaultNameStrategy().getFullName(javaCode) }) match {
        case Full(fullClassName) =>
          val diagnostics = new DiagnosticCollector[JavaFileObject]
          val fileManager = new ch.obermuhlner.scriptengine.java.MemoryFileManager(
            compiler.getStandardFileManager(diagnostics, null, null), null)
          val simpleClassName = StringUtils.substringAfterLast("." + fullClassName, ".")
          val source = fileManager.createSourceFileObject(null, simpleClassName, javaCode)
          val compiled: Boolean = compiler.getTask(null, fileManager, diagnostics, null, null, java.util.Arrays.asList(source)).call()
          val problems = diagnostics.getDiagnostics.asScala.toList.filter(_.getKind == Diagnostic.Kind.ERROR).map { d =>
            val line = if (d.getLineNumber > linesAdded) (d.getLineNumber - linesAdded).toInt else 0
            val column = if (line > 0 && d.getColumnNumber > 0) d.getColumnNumber.toInt else 0
            CompileProblem(line, column, "ERROR", d.getMessage(java.util.Locale.ENGLISH))
          }
          if (problems.nonEmpty || compiled) problems
          else List(CompileProblem(0, 0, "ERROR", "The Java compiler rejected the method body without reporting why."))
        case failure =>
          // DefaultNameStrategy fails when there is no class declaration to name the source file after.
          val reason = failure match { case f: Failure => f.msg; case _ => "no class declaration found" }
          List(CompileProblem(0, 0, "ERROR", s"A Java method body must declare a public class implementing Supplier<Function<Object[], Object>>: $reason"))
      }
    }
  }
  // Neither this nor memoJavaCompiledScript below ever evicts, so each distinct ClassLoader (and
  // therefore each distinct compiled Java method_body -- java-scriptengine hands createJavaHttp4sEndpoint
  // a fresh MemoryClassLoader per compile) is retained for the life of the process, along with its
  // ClassPool. This is the same unbounded-but-trusted-operator-only tradeoff dynamicCompileResult
  // below already makes for the Scala compile cache, predating the Java path: registering a dynamic
  // resource doc is gated behind canCreateDynamicResourceDoc / canCreateBankLevelDynamicResourceDoc,
  // not open to arbitrary callers, and a served endpoint's ClassLoader must stay reachable for as
  // long as that endpoint keeps serving requests -- an eviction policy here would need to be
  // reference-counted against currently-registered docs to avoid reclaiming a live one, which is a
  // larger change than this cache's existing (pre-Java) design accounted for.
  private val memoClassPool = new Memo[ClassLoader, ClassPool]
  // Caches only the compiled artifact (deterministic given the source string), never the
  // validation outcome built on top of it -- see createJavaHttp4sEndpoint's doc comment.
  private val memoJavaCompiledScript = new Memo[String, Box[ch.obermuhlner.scriptengine.java.JavaCompiledScript]]

  private def getClassPool(classLoader: ClassLoader) = memoClassPool.memoize(classLoader){
    val cp = ClassPool.getDefault
    cp.appendClassPath(new LoaderClassPath(classLoader))
    cp
  }

  // code -> dynamic method function
  // the same code should always be compiled once, so here cache them
  private val dynamicCompileResult = new ConcurrentHashMap[String, Box[Any]]()

  type DynamicFunction = (Array[AnyRef], Option[CallContext]) => Future[Box[(String, Option[CallContext])]]

  /**
   * Compile scala code
   * toolBox have bug that first compile fail, second or later compile success.
   * @param code
   * @return compiled Full[function|object|class] or Failure
   */
  def compileScalaCode[T](code: String): Box[T] = {
    if (!dynamicCodeExecutionEnabled)
      return Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    compileScalaCodeUnchecked[T](code)
  }

  // Used ONLY by DynamicUtil.Validation's props-driven config parsing (operator config,
  // not user-generated code) so the app can still boot with the kill-switch off.
  private def compileScalaCodeUnchecked[T](code: String): Box[T] = {
    logger.trace(s"code.api.util.DynamicUtil.compileScalaCode.size is ${dynamicCompileResult.size()}")
    val compiledResult: Box[Any] = dynamicCompileResult.computeIfAbsent(code, _ => {
      val tree = try {
        toolBox.parse(code)
      } catch {
        case e: ToolBoxError =>
          return Failure(e.message)
      }

      try {
        val func: () => Any = toolBox.compile(tree)
        Box.tryo(func())
      } catch {
        case _: ToolBoxError =>
          // try compile again
          try {
            val func: () => Any = toolBox.compile(tree)
            Box.tryo(func())
          } catch {
            case e: ToolBoxError =>
              Failure(e.message)
          }
      }
    })

    compiledResult.map(_.asInstanceOf[T])
  }

  /**
   * 
   * @param methodName the method name
   * @param function the method body, if it is empty, then throw exception. if it is existing, then call this function.
   * @param args the method parameters
   * @return the result of the execution of the function.
   */
  def executeFunction(methodName: String, function: Box[Any], args: Array[AnyRef]) = {
    val result = function.orNull match {
      case func: Function0[AnyRef] => func()
      case func: Function[AnyRef, AnyRef] => func(args.head)
      case func: Function2[AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1))
      case func: Function3[AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2))
      case func: Function4[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3))
      case func: Function5[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4))
      case func: Function6[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5))
      case func: Function7[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6))
      case func: Function8[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7))
      case func: Function9[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8))
      case func: Function10[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9))
      case func: Function11[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10))
      case func: Function12[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11))
      case func: Function13[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12))
      case func: Function14[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13))
      case func: Function15[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14))
      case func: Function16[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15))
      case func: Function17[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16))
      case func: Function18[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17))
      case func: Function19[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18))
      case func: Function20[AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef, AnyRef] => func(args.head, args.apply(1), args.apply(2), args.apply(3), args.apply(4), args.apply(5), args.apply(6), args.apply(7), args.apply(8), args.apply(9), args.apply(10), args.apply(11), args.apply(12), args.apply(13), args.apply(14), args.apply(15), args.apply(16), args.apply(17), args.apply(18), args.apply(19))
      case null => throw new IllegalStateException(s"There is  no method $methodName, it should not be called here")
      case _ => throw new IllegalStateException(s"$methodName can not be called here.")
    }
    result.asInstanceOf[AnyRef]
  }

  /**
   * this method will create a object from the JValue.
   * from JValue --> Case Class String -->  DynamicUtil.compileScalaCode(code) --> object 
   * @param jValue
   * @return 
   */
  def toCaseObject(jValue: JValue): Product = {
    val caseClasses = JsonUtils.toCaseClasses(jValue)
    val code =
      s"""
         | $caseClasses
         |
         | // throws exception: org.json4s.MappingException:
         | //No usable value for name
         | //Did not find value which can be converted into java.lang.String
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |(jValue: org.json4s.JsonAST.JValue) => {
         |  jValue.extract[RootJsonClass]
         |}
         |""".stripMargin
    val fun: Box[JValue => Product] = DynamicUtil.compileScalaCode(code)
    fun match {
      case Full(func) => func.apply(jValue)
      case Failure(msg: String, exception: Box[Throwable], _) =>
        throw exception.getOrElse(new RuntimeException(msg))
      case _ => throw new RuntimeException(s"Json extract to case object fail, json: \n ${prettyRender(jValue)}")
    }
  }

  /**
   * NOTE: MEMORY_USER this ctClass will be cached in ClassPool, it may load too many classes into heap.
   * @param clazz
   * @param predicate
   * @param force bypasses the SHOW_USED_CONNECTOR_METHODS gate below. SHOW_USED_CONNECTOR_METHODS
   *              exists to opt in to an unrelated, expensive introspection/reporting feature (which
   *              connector methods a static endpoint touches) — it was never meant to gate SECURITY
   *              validation, which reuses this same bytecode scan. Without `force`, a deployment
   *              that sets dynamic_code_obp_calls_are_restricted=true (the documented, security-
   *              relevant prop) but leaves the unrelated show_used_connector_methods at its default
   *              false would silently get an always-empty dependency list here — every dynamic-code
   *              call looks "allowed" no matter what it does, because there is nothing to check
   *              against the whitelist. Validation.validateDependency passes force=true so it is
   *              controlled solely by dynamic_code_obp_calls_are_restricted, matching what an
   *              operator following that prop's own documentation would expect.
   * @return
   */
  def getDynamicCodeDependentMethods(clazz: Class[_], predicate:  String => Boolean = _ => true, force: Boolean = false): List[(String, String, String)] =
  if (SHOW_USED_CONNECTOR_METHODS || force) {
    val className = clazz.getTypeName
    val listBuffer = new ListBuffer[(String, String, String)]()
    val classPool = getClassPool(clazz.getClassLoader)
    //NOTE: MEMORY_USER this ctClass will be cached in ClassPool, it may load too many classes into heap.
    val ctClass = classPool.get(className)

    // A same-class (or same-generated-unit, for the Scala nested-closure case below) call is not
    // itself a dependency to police -- recurse into what the TARGET method calls instead of
    // flagging the call itself as forbidden, all the way down until a genuinely foreign
    // dependency is reached. This is required for Java: every Java dynamic resource doc
    // implements Supplier<Function<Object[], Object>> (the documented convention), and the
    // compiler always erases that generic Supplier.get() to a synthetic bridge method
    // `Object get()` whose body is just `return this.get();` -- an ordinary same-class
    // invokevirtual call to the real, properly-typed get(). A single level of unrolling only
    // fixes that one hop: any Java body that factors logic into its own private helper methods
    // (an entirely normal thing to do) reintroduces the exact same false rejection one level
    // deeper, since the un-recursed helper's own callees would otherwise be appended as raw
    // (thisClass, method) tuples and then rejected as calls to an unwhitelistable random-UUID
    // class. `visited` guards against a call cycle -- direct or mutual recursion between
    // same-class private methods (e.g. a fibonacci/factorial helper) is entirely normal Java and
    // would otherwise recurse forever. On hitting a cycle this contributes nothing further (Nil),
    // not a leaf: the recursive call is still a same-class call, not a foreign dependency, and
    // whatever it in turn depends on is already being expanded by the in-progress call further up
    // this same path -- returning it as a leaf here would flag the method's own name
    // (unwhitelistable, like any other randomly-named dynamic class) as a forbidden dependency,
    // exactly the bug this whole function exists to avoid.
    def expand(typeName: String, methodName: String, signature: String, visited: Set[(String, String, String)]): List[(String, String, String)] = {
      val key = (typeName, methodName, signature)
      val sameUnit = typeName == className ||
        (className.startsWith(typeName) && methodName.startsWith(clazz.getPackage.getName + "$"))
      if (!sameUnit) {
        List(key)
      } else if (visited.contains(key)) {
        Nil
      } else {
        APIUtil.getDependentMethods(typeName, methodName, signature, force).flatMap { case (t, m, s) =>
          expand(t, m, s, visited + key)
        }
      }
    }

    for {
      method <- ctClass.getDeclaredMethods.toList
      if predicate(method.getName)
      (typeName, methodName, signature) <- APIUtil.getDependentMethods(className, method.getName, method.getSignature, force)
    } yield {
      listBuffer.appendAll(expand(typeName, methodName, signature, Set.empty))
    }

    listBuffer.distinct.toList
  } else {
    Nil
  }

  /**
   * Forces a dynamic body's IO and recovers the non-local `return` a compiled closure
   * uses for an early exit, so `return errorResponse(...)` in user code yields its
   * response rather than a 500. (In PractiseEndpoint the body is a real method, so
   * `return` is ordinary and never reaches the catch.)
   *
   * This is what remains of the former `Sandbox`. That wrapper also ran dynamic code
   * under `AccessController.doPrivileged` with a restricted permission set, configured
   * by the `dynamic_code_sandbox_enable` / `dynamic_code_sandbox_permissions` props.
   * SecurityManager was removed in JDK 24 (JEP 486) and OBP now requires JVM 25, so
   * `doPrivileged` is a pass-through on every runtime that can run this code: the
   * sandbox could not restrict file, network or reflection access, while still costing
   * a privileged wrapper on every dynamic call. It has been removed rather than left as
   * a switch that implies isolation it cannot deliver.
   *
   * Dynamic code therefore runs with the full privileges of the OBP process. The
   * controls that do work are `allow_user_generated_scala_code` (the master gate),
   * `dynamic_code_obp_calls_are_restricted` (the allowlist of callable OBP methods)
   * and `dynamic_code_requires_approval` (maker-checker).
   */
  object DynamicCodeBody {
    def force[A](io: => IO[A]): IO[A] = {
      def forced(): IO[A] =
        try io
        catch { case e: scala.runtime.NonLocalReturnControl[_] => e.value.asInstanceOf[IO[A]] }
      IO.defer(forced())
    }
  }

  /**
   * common import statements those are used by compiler
   */
 val importStatements =
    """
      |import java.net.{ConnectException, URLEncoder, UnknownHostException}
      |import java.util.Date
      |import java.util.UUID.randomUUID
      |
      |import _root_.org.apache.pekko.stream.StreamTcpException
      |import org.apache.pekko.http.scaladsl.model.headers.RawHeader
      |import org.apache.pekko.http.scaladsl.model.{HttpProtocol, _}
      |import org.apache.pekko.util.ByteString
      |import code.api.APIFailureNewStyle
      |import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
      |import code.api.cache.Caching
      |import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, writeMetricEndpointTiming, _}
      |import code.api.util.ErrorMessages._
      |import code.api.util.ExampleValue._
      |import code.api.util.{APIUtil, CallContext, OBPQueryParam}
      |import code.api.dynamic.endpoint.helper.MockResponseHolder
      |import code.bankconnectors._
      |import code.customer.internalMapping.MappedCustomerIdMappingProvider
      |import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
      |import code.util.AkkaHttpClient._
      |import code.util.Helper.MdcLoggable
      |import com.openbankproject.commons.dto.{InBoundTrait, _}
      |import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
      |import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, DynamicEntityOperation, ProductAttributeType}
      |import com.openbankproject.commons.model.{ErrorMessage, TopicTrait, _}
      |import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
      |// import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
      |import net.liftweb.common.{Box, Empty, _}
      |import com.openbankproject.commons.util.json
      |import org.json4s.Extraction.decompose
      |import org.json4s.JsonDSL._
      |import org.json4s.ParserUtil.ParseException
      |import org.json4s.{JValue, _}
      |import com.openbankproject.commons.util.JsonAliases._
      |import net.liftweb.util.Helpers.tryo
      |import org.apache.commons.lang3.StringUtils
      |
      |import scala.collection.immutable.List
      |import scala.collection.mutable.ArrayBuffer
      |import scala.concurrent.duration._
      |import scala.concurrent.{Await, Future}
      |import com.openbankproject.commons.dto._
      |import code.api.util.APIUtil.ResourceDoc
      |import code.api.util.NewStyle.HttpCode
      |import code.api.util._
      |import code.api.v4_0_0.JSONFactory400
      |import code.api.dynamic.endpoint.helper.{CompiledObjects, DynamicCompileEndpoint}
      |import code.api.dynamic.endpoint.helper.practise.PractiseEndpoint
      |import com.openbankproject.commons.ExecutionContext
      |import code.api.util.CustomJsonFormats
      |import com.openbankproject.commons.model.BankId
      |import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
      |import net.liftweb.common.{Box, Full}
      |import org.apache.commons.lang3.StringUtils
      |
      |import java.io.File
      |import java.security.{AccessControlException, Permission}
      |import java.util.PropertyPermission
      |import scala.collection.immutable.List
      |import scala.io.Source
      |""".stripMargin


  object Validation {

    // def, not val, throughout this object: these must react to a props change (e.g. test-time
    // setPropsValues) without a restart, not freeze at whatever the props held the moment
    // Validation was first touched (typically by whichever dynamic-code test happens to run
    // first in a shared test JVM). This costs nothing extra in production -- the only expensive
    // step, DynamicUtil.compileScalaCodeUnchecked, is already memoized by the exact source
    // string, so re-evaluating these on every call is a cache hit unless the underlying props
    // value actually changed.

    /**
     * Turn the `dynamic_code_allowed_obp_methods` props value into the Scala source
     * that, once compiled, yields the whitelist.
     *
     * A named function rather than an inline expression so a test can drive the real thing.
     * DynamicUtilTest used to hold a character-for-character copy of it, which meant the two
     * could diverge with the test still green -- the copy was only kept in step here because
     * whoever edited one happened to see the other. This is the only compile that happens
     * reflectively at boot, so nothing at compile time would have caught the divergence either.
     *
     * `Map[String, String](` rather than `Map(`: the props default is an empty list, and a bare
     * `Map()` leaves its type parameters undetermined, so the trailing `.toMap` cannot prove the
     * elements are pairs and the reflective compilation fails. The `.toMap` is itself needed
     * because `mapValues` returns a view rather than a Map on 2.13.
     */
    def dependenciesScalaCode(dependenciesString: String): String =
      s"${DynamicUtil.importStatements}" +
        dependenciesString.replaceFirst("\\[", "Map[String, String](").dropRight(1) +
        ").mapValues(v => StringUtils.split(v, ',').map(_.trim).toSet).toMap"


    // Runtime permission control was removed with the sandbox: SecurityManager is gone
    // from JDK 24+ (JEP 486) and OBP requires JVM 25, so it could not be enforced. See
    // DynamicUtil.DynamicCodeBody for what replaced it and which controls still work.

    /**
     * Renamed 2026-09-23: dynamic_code_compile_validate_enable -> dynamic_code_obp_calls_are_restricted
     * and dynamic_code_compile_validate_dependencies -> dynamic_code_allowed_obp_methods. The old
     * names said "compile validate", which reads as "check that it compiles"; what they actually
     * control is an allowlist of the OBP methods dynamic code may call.
     *
     * Both legacy names are still read, because dropping them would silently disable a restriction
     * an operator had deliberately turned on. A deployment using either is warned once per name;
     * this is read on every validation (see "def, not val" above), so the warning must not repeat.
     */
    private val legacyPropsWarned = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
    private def legacyProp(current: String, legacy: String): Box[String] = {
      val legacyValue = APIUtil.getPropsValue(legacy)
      if (legacyValue.isDefined && APIUtil.getPropsValue(current).isEmpty && legacyPropsWarned.add(legacy)) {
        logger.warn(s"Props `$legacy` is deprecated and has been renamed to `$current`. The old " +
                    s"name is still honoured, but rename it: support will be removed.")
      }
      APIUtil.getPropsValue(current) or legacyValue
    }

    def dependenciesString =
      legacyProp("dynamic_code_allowed_obp_methods", "dynamic_code_compile_validate_dependencies")
        .openOr("[]").trim
    def scalaCodeDependencies = dependenciesScalaCode(dependenciesString)
    def dependenciesBox: Box[Map[String, Set[String]]] = DynamicUtil.compileScalaCodeUnchecked(scalaCodeDependencies)
    
    /**
     * Compilation OBP Dependencies Guard, only checked the OBP methods, not scala/Java libraies(are checked during the runtime.).
     * 
     * allowedCompilationMethods --> 
     * The following methods will be checked when you call the `Create Dynamic ResourceDoc/MessageDoc` endpoints.
     *  You can control all the OBP methods here.
     */
    // all allowed methods put at here, typeName -> methods
//    val allowedCompilationMethods: Map[String, Set[String]] = Map(
//      // companion objects methods
//      NewStyle.function.getClass.getTypeName -> "*",
//      CompiledObjects.getClass.getTypeName -> "sandbox",
//      HttpCode.getClass.getTypeName -> "200",
//      DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse",
//      APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse",
//      ErrorMessages.getClass.getTypeName -> "*",
//      ExecutionContext.Implicits.getClass.getTypeName -> "global",
//      JSONFactory400.getClass.getTypeName -> "createBanksJson",
//
//      // class methods
//      classOf[CallContext].getTypeName -> "*",
//      classOf[ResourceDoc].getTypeName -> "getPathParams",
//      "scala.reflect.runtime.package$" -> "universe",
//
//      // allow any method of PractiseEndpoint for test
//      PractiseEndpoint.getClass.getTypeName + "*" -> "*",
//
//    ).mapValues(v => StringUtils.split(v, ',').map(_.trim).toSet)
    def allowedCompilationMethods: Map[String, Set[String]] = dependenciesBox.openOrThrowException("Can not compile the props `dynamic_code_allowed_obp_methods` to Map")

    //Do not touch this Set, try to use the `allowedPermissions` and `allowedMethods` to control the sandbox 
    val restrictedTypes = Set(
      "scala.reflect.runtime.",
      "java.lang.reflect.",
      "scala.concurrent.ExecutionContext"
    )

    private def isRestrictedType(typeName: String) = ReflectUtils.isObpClass(typeName) || restrictedTypes.exists(typeName.startsWith)

    /**
     * validate dependencies, (className, methodName, signature)
     * 
     * Here only validate the restricted types(isObpClass + val restrictedTypes), not all scala/java types.
     */
    private def validateDependency(dependentMethods: List[(String, String, String)]) = {
      // Bound once per call, not re-derived per dependency tuple: allowedCompilationMethods is a
      // def (see the "def, not val" comment above) so it observes a live props change, but it
      // recompiles the whitelist source on every access -- reading it twice per element inside
      // the `collect` guard below would mean up to 2N re-derivations for N dependency tuples.
      val allowedCompilationMethods = this.allowedCompilationMethods
      val notAllowedDependentMethods = dependentMethods collect {
        case (typeName, method, _)
          if isRestrictedType(typeName) &&
            !allowedCompilationMethods.get(typeName).exists(set => set.contains(method) || set.contains("*")) &&
            !allowedCompilationMethods.exists { it =>
              val (tpName, allowedMethods) = it
              tpName.endsWith("*") &&
                typeName.startsWith(StringUtils.substringBeforeLast(tpName, "*")) &&
                (allowedMethods.contains(method) || allowedMethods.contains("*"))
            }
        =>
          s"$typeName.$method"
      }
      // change to JsonResponseException
      if(notAllowedDependentMethods.nonEmpty) {
        val illegalDependency = notAllowedDependentMethods.mkString("[", ", ", "]")
        throw JsonResponseException(s"$DynamicResourceDocMethodDependency $illegalDependency", 400, "none")
      }
    }

    def validateDependency(obj: AnyRef): Unit = {
      val restricted = legacyProp("dynamic_code_obp_calls_are_restricted", "dynamic_code_compile_validate_enable")
        .map(_.trim.equalsIgnoreCase("true")).openOr(false)
      if(restricted){
        // force=true: this check must not also require the unrelated show_used_connector_methods
        // prop -- see getDynamicCodeDependentMethods' doc comment for why.
        val dependentMethods: List[(String, String, String)] = DynamicUtil.getDynamicCodeDependentMethods(obj.getClass, force = true)
        validateDependency(dependentMethods)
      } else{ // If false, nothing to do here.
        ;
      }
    }
  }

  private val jsEngine = Engine.newBuilder.option("engine.WarnInterpreterOnly", "false")
    .allowExperimentalOptions(true)
    .build()

  private val memoDynamicFunction = new Memo[String, Box[DynamicFunction]]

  def createJsFunction(methodBody:String, bindingVars: Map[String, AnyRef] = Map.empty): Box[DynamicFunction] =
    if (!dynamicCodeExecutionEnabled) Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    else memoDynamicFunction.memoize("Javascript:" + methodBody) {
    Box tryo {
      val jsCode = s"""async function processor(args, callContext) {
       $methodBody
      }
      // wrap function in order to convert return value to json string
      async (args) => JSON.stringify(await processor(args));
      """;
      val context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowPolyglotAccess(PolyglotAccess.ALL)
        .allowHostClassLookup(_ => true)
        .option("js.ecmascript-version", "2020")
        .engine(jsEngine).build

      // bind variables
      val bindings = context.getBindings("js")
      bindingVars.foreach(it => bindings.putMember(it._1, it._2))

      // call js
      val jsFunc = context.eval("js", jsCode)

      (args: Array[AnyRef], cc: Option[CallContext]) => {
        val p = Promise[Box[(String, Option[CallContext])]]()
        // to JValue: Extraction.decompose(it)(formats)
        val resolve: Consumer[String] = (it: String) =>
          p.success(Full(it -> cc))

        // TODO refactor APIFailureNewStyle error message.
        val reject:Consumer[Any]= e =>
          p.success(ParamFailure(s"Js reject error message: $e", Empty, Empty, APIFailureNewStyle(e.toString, 400, cc.map(_.toLight))))

        //cc.map(_.toOutboundAdapterCallContext).orNull
        jsFunc.execute(args ++ cc)
          .invokeMember("then", resolve, reject)
          .invokeMember("catch", reject)
        p.future
      }
    }
  }

  private val javaEngine = (new ScriptEngineManager).getEngineByName("java")

  def createJavaFunction(methodBody:String): Box[DynamicFunction] =
    if (!dynamicCodeExecutionEnabled) Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    else memoDynamicFunction.memoize("java:" + methodBody) {
    import com.openbankproject.commons.ExecutionContext.Implicits.global
    import com.openbankproject.commons.util.JsonAliases.compactRender

    Box tryo {
      val packageExp = UUID.randomUUID().toString.replaceAll("^|-", "_")
      val packageMatcher = Pattern.compile("""(?m)^\s*package\s+\S+?\s*;""").matcher(methodBody)

      val javaCode = s"""package code.api.util.dynamic.${packageExp};
                        |${packageMatcher.replaceFirst("")}
                        |""".stripMargin

      val func = javaEngine.eval(javaCode).asInstanceOf[java.util.function.Function[Array[AnyRef], Any]]

      (args: Array[AnyRef], cc: Option[CallContext]) => Future {
        val value = func(args ++ cc)
        val jValue = Extraction.decompose(value)(CustomJsonFormats.formats)
        val zson = compactRender(jValue)
        Box !! (zson-> cc)
      }
    }
  }

  /**
   * Converts a plain value returned by a compiled Java `method_body` into a JValue, for endpoints
   * where json4s' `Extraction.decompose` cannot help: it works by Scala-case-class/collection
   * reflection, so a `java.util.Map`/`java.util.List` returned from Java decomposes to `{}`/`[]`
   * (its entries are invisible to Scala reflection) rather than throwing — a silent data-loss bug,
   * not a compile or runtime error, so it only surfaces as an empty response body. Recurses through
   * the Java collection types directly; anything else (including a Scala case class constructed
   * from Java, as ConnectorMethod's Java example does) falls back to Extraction.decompose.
   */
  private def javaValueToJValue(value: Any): JValue = {
    import scala.jdk.CollectionConverters._
    value match {
      case null => JNull
      case jv: JValue => jv
      case m: java.util.Map[_, _] =>
        JObject(m.asScala.toList.map { case (k, v) => (String.valueOf(k), javaValueToJValue(v)) })
      case l: java.util.List[_] =>
        JArray(l.asScala.toList.map(javaValueToJValue))
      case s: String => JString(s)
      case b: java.lang.Boolean => JBool(b)
      case i: java.lang.Integer => JInt(BigInt(i.intValue()))
      case l: java.lang.Long => JInt(BigInt(l.longValue()))
      case d: java.lang.Double => JDouble(d.doubleValue())
      case f: java.lang.Float => JDouble(f.doubleValue())
      case bd: java.math.BigDecimal => JDecimal(BigDecimal(bd))
      case other => Extraction.decompose(other)(CustomJsonFormats.formats)
    }
  }

  /**
   * Compiles a Java `method_body` for a DynamicResourceDoc endpoint into a native
   * `Http4sEndpointIO` (`PartialFunction[Request[IO], CallContext => IO[Response[IO]]]`), the same
   * type the Scala template compiles to in DynamicEndpoints.CompiledObjects.
   *
   * Reuses the same JSR-223 "java" engine (backed by a real javax.tools.JavaCompiler via
   * ch.obermuhlner:java-scriptengine — see createJavaFunction above) and the same
   * package-uniquification trick, but — unlike createJavaFunction, whose DynamicFunction shape is
   * specific to the ConnectorMethod feature — wraps the compiled function in a hand-written
   * Http4sEndpointIO here in Scala. The Java method_body never has to construct cats.effect.IO,
   * org.http4s.Response, or a Scala PartialFunction: it only ever returns a plain Java object
   * (Map/List/String/number/boolean/etc.), which this adapter serializes via javaValueToJValue
   * above (NOT Extraction.decompose directly — see that method's doc comment for why).
   *
   * Java-side convention (identical to the existing ConnectorMethod convention): the pasted class
   * implements java.util.function.Supplier<java.util.function.Function<Object[], Object>>. The
   * compiled function is invoked with:
   *   args(0) = the raw request body (String, or null if the request had none)
   *   args(1) = path params (java.util.Map<String, String>)
   *   args(2) = the CallContext (present whenever this endpoint is actually being served)
   * mirroring createJavaFunction's own `func(args ++ cc)` call (line above): appending an
   * Option[CallContext] via `++` appends its *contents* (0 or 1 raw CallContext), not the Option
   * wrapper itself, so Java reads args[2] directly as a CallContext, no unwrapping needed.
   *
   * Unlike createJavaFunction, this validates the actual compiled Java class (not just its Scala
   * wrapper) against `dynamic_code_allowed_obp_methods`/`dynamic_code_obp_calls_are_restricted`.
   * CompiledObjects.validateDependency() (called by the ResourceDoc-creation flow) only ever sees
   * `this.partialFunction` — the hand-written Http4sEndpointIO below — whose own bytecode just
   * calls `java.util.function.Function.apply`, a non-restricted type; it can't see what the pasted
   * Java class does inside apply(Object[]). Worse, `func` itself (the Function returned by the
   * pasted class's get()) is commonly a method reference (`this::apply`), which the JVM
   * materialises as a synthetic lambda class whose bytecode is just a delegating call — validating
   * `func.getClass` would be equally blind. So we go through the JSR-223 Compilable API directly
   * (JavaScriptEngine implements it) instead of plain eval(), to get the real top-level compiled
   * class/instance (JavaCompiledScript.getCompiledClass/getCompiledInstance) and validate that
   * before the function is ever returned or invoked.
   */
  def createJavaHttp4sEndpoint(methodBody: String): Box[code.api.util.APIUtil.Http4sEndpointIO] =
    if (!dynamicCodeExecutionEnabled) Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    else {
      import cats.effect.IO
      import code.api.util.APIUtil.Http4sEndpointIO
      import com.openbankproject.commons.ExecutionContext.Implicits.global
      import com.openbankproject.commons.util.JsonAliases.compactRender
      import org.http4s.headers.`Content-Type`
      import org.http4s.dsl.io._
      import org.http4s.{MediaType, Request, Response}

      import scala.jdk.CollectionConverters._

      // Only the compile step is memoized — deterministic given the same source string, and the
      // one genuinely expensive part (a real javax.tools.JavaCompiler invocation). Dependency
      // validation below is NOT memoized: it depends on mutable external config
      // (dynamic_code_obp_calls_are_restricted/dynamic_code_allowed_obp_methods), which can change between two
      // createJavaHttp4sEndpoint calls for the identical source string — e.g. a doc compiled once
      // while validation was off, then a later create/update call resubmitting the exact same
      // method_body after validation was turned on and the whitelist tightened. An earlier version
      // of this function memoized the validated *result* (Box[Http4sEndpointIO]) as a single unit,
      // so that second call silently reused the first call's unvalidated success — bypassing the
      // now-stricter policy for any resubmitted source. Re-running validation on every call costs
      // little: it is Javassist bytecode inspection plus a Map lookup, not another compile.
      val compiledScriptBox: Box[ch.obermuhlner.scriptengine.java.JavaCompiledScript] =
        memoJavaCompiledScript.memoize("java-http4s-endpoint:" + methodBody) {
          // Real compile happens here (javax.tools.JavaCompiler via the JSR-223 "java" engine) —
          // any Java syntax/type error surfaces as an exception, caught by this `Box tryo` and
          // turned into a Failure.
          Box tryo {
            val packageExp = UUID.randomUUID().toString.replaceAll("^|-", "_")
            val packageMatcher = Pattern.compile("""(?m)^\s*package\s+\S+?\s*;""").matcher(methodBody)

            val javaCode = s"""package code.api.util.dynamic.${packageExp};
                              |${packageMatcher.replaceFirst("")}
                              |""".stripMargin

            val compiledScript = javaEngine.asInstanceOf[javax.script.Compilable].compile(javaCode)
              .asInstanceOf[ch.obermuhlner.scriptengine.java.JavaCompiledScript]

            // getDynamicCodeDependentMethods loads a class's bytecode via Javassist's
            // LoaderClassPath, which reads it through classLoader.getResourceAsStream(...). The
            // compiler's ch.obermuhlner.scriptengine.java.MemoryClassLoader only overrides
            // loadClass() — it never exposes the compiled bytes as a classpath resource — so that
            // lookup silently fails (javassist.NotFoundException) and validation would see zero
            // dependent methods no matter what the Java code actually calls. Read the bytes
            // directly from the classloader's private byte map (reflection is unavoidable here:
            // java-scriptengine exposes no public accessor) and hand them to Javassist explicitly
            // via ByteArrayClassPath, so the real method bodies — including any restricted OBP
            // call — are visible to validation. Done here, inside the compile memoization, so it
            // runs exactly once per distinct source: ClassPool.appendClassPath has no dedup of its
            // own, so doing this on every createJavaHttp4sEndpoint call (as an earlier version of
            // this function did, on every resourceDocs-list rebuild for the process's lifetime)
            // grew that ClassPool's classpath chain without bound.
            val compiledClass = compiledScript.getCompiledClass
            val classBytesField = compiledClass.getClassLoader.getClass.getDeclaredField("mapClassBytes")
            classBytesField.setAccessible(true)
            val classBytes = classBytesField.get(compiledClass.getClassLoader)
              .asInstanceOf[java.util.Map[String, Array[Byte]]].get(compiledClass.getName)
            // Fail loudly and specifically here rather than handing Javassist a null byte array --
            // that would only surface later, inside ByteArrayClassPath/ClassPool, as an opaque NPE
            // with no indication that the cause was this reflective read (e.g. a java-scriptengine
            // upgrade that changes mapClassBytes' keying from binary name to internal name, or that
            // stops using that field name at all).
            if (classBytes == null) {
              throw new IllegalStateException(
                s"createJavaHttp4sEndpoint: MemoryClassLoader.mapClassBytes has no entry for " +
                  s"${compiledClass.getName} -- java-scriptengine's internal layout may have changed")
            }
            getClassPool(compiledClass.getClassLoader)
              .appendClassPath(new javassist.ByteArrayClassPath(compiledClass.getName, classBytes))

            compiledScript
          }
        }

      // Deliberately outside compiledScriptBox's `Box tryo` AND outside the memoization above: a
      // rejection here throws JsonResponseException, which must propagate UNCAUGHT (mirroring the
      // Scala path's CompiledObjects.validateDependency(), also never wrapped in tryo) so
      // compileDynamicResourceDoc's `case e: JsonResponseException => throw e` sees it intact.
      // JsonResponseException never sets a Throwable message (getMessage == null); Box.tryo would
      // catch it into Failure(null, Full(theException), Empty), and DynamicEndpoints.scala's
      // `case Failure(msg: String, ...)` pattern silently fails to match a null msg — falling
      // through to "compiled code return nothing" and discarding the real rejection reason. `.map`
      // does not swallow exceptions the way `Box tryo` does, so this stays uncaught here.
      compiledScriptBox.map { compiledScript =>
        // Validate the real compiled Supplier class before it's ever invoked — see the doc comment
        // above for why this must run against getCompiledInstance, not `func`/`this.partialFunction`,
        // and why it must run fresh on every call rather than being cached with the compile result.
        Validation.validateDependency(compiledScript.getCompiledInstance)

        val func = compiledScript.eval().asInstanceOf[java.util.function.Function[Array[AnyRef], Any]]
        val jsonContentType = `Content-Type`(MediaType.application.json)

        new Http4sEndpointIO {
          override def isDefinedAt(req: Request[IO]): Boolean = true

          override def apply(req: Request[IO]): CallContext => IO[Response[IO]] = { cc =>
            val pathParams: java.util.Map[String, String] = cc.resourceDocument
              .map(_.getPathParams(req.uri.path.segments.toList.map(_.encoded)))
              .getOrElse(Map.empty[String, String])
              .asJava

            val valueIO: IO[Any] = IO.fromFuture(IO {
              Future {
                val args: Array[AnyRef] = Array(cc.httpBody.orNull, pathParams)
                func(args ++ Some(cc))
              }
            })

            valueIO.flatMap { value =>
              Ok(compactRender(javaValueToJValue(value)), jsonContentType)
            }.handleErrorWith { e =>
              logger.warn(s"createJavaHttp4sEndpoint: Java method_body threw", e)
              InternalServerError(
                compactRender(Extraction.decompose(
                  Map("code" -> 500, "message" -> s"OBP-50000: Unknown Error. ${e.getMessage}")
                )(CustomJsonFormats.formats)),
                jsonContentType
              )
            }
          }
        }
      }
    }
}
