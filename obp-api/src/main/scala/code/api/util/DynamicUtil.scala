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
import code.api.{APIFailureNewStyle, JsonResponseException}
import code.api.util.ErrorMessages.DynamicResourceDocMethodDependency
import code.api.util.dynamiccompiler.{DotcScalaCompiler, DynamicCompileFailure, DynamicScalaCompiler}
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

  // The Scala-source compiler, behind an interface so the Scala 3 flip swaps the
  // implementation (Scala 3 has no ToolBox) without touching any caller.
  private val scalaCompiler: DynamicScalaCompiler = DotcScalaCompiler

  /** One compiler diagnostic from [[checkScalaCode]]. `line`/`column` are 1-based in the code that was checked; 0 when unknown. */
  case class CompileProblem(line: Int, column: Int, severity: String, message: String)

  /**
   * Compile `code` for diagnostics only: nothing is evaluated, nothing is cached. Empty list = compiles.
   * Callers must apply the kill switch and a role.
   *
   * Upstream implemented this on a second Scala 2 `ToolBox`; Scala 3 has no ToolBox, so it
   * delegates to the same `DynamicScalaCompiler` the real compile path uses, whose `check` runs
   * the compiler without evaluating or caching. That also removes upstream's serialisation
   * requirement - there is no shared mutable front end to guard.
   *
   * Upstream wraps the code in a method before checking it, because a bare `return` (which the
   * documented example method body uses) is otherwise reported as "return outside method
   * definition". DotcScalaCompiler.check already compiles the code as the body of `def result`,
   * exactly as the real compile does, so the same code is accepted here without a wrapper.
   */
  def checkScalaCode(code: String): List[CompileProblem] =
    scalaCompiler.check(code).map(d => CompileProblem(d.line, d.column, d.severity, d.message))

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

  /**
   * A javassist pool scoped to one classloader.
   *
   * This used to hand back `ClassPool.getDefault` - a process-wide singleton - after appending a
   * LoaderClassPath for the caller's loader, so every distinct classloader added another search
   * path to the one shared pool and none was ever removed. Harmless while the only caller was the
   * `show_used_connector_methods` diagnostic (off by default, so this ran approximately never);
   * not harmless once the dependency scan runs for real, because dynamic compilation mints a fresh
   * classloader per snippet. The pool then accumulates a path per snippet, each pinning a
   * classloader whose temp output directory is gone, and every later lookup searches all of them
   * in turn - which is also what the `MEMORY_USER` notes on the callers were worried about.
   *
   * `new ClassPool(true)` starts from the system path, exactly as `getDefault` does, so lookups
   * resolve the same way; it is just not shared. Still memoized per classloader, so a repeated
   * scan against the same loader reuses its pool and its parsed CtClasses.
   *
   * Retraction, recorded here because 125950aa2's message got it wrong: that commit presented the
   * shared pool as the proven cause of two failures seen at the time - DynamicUtilTest and
   * InternalConnectorTest reporting `missing reference, looking for JValue/T in package object
   * json4s` - and said so was "verified by isolation". It was not the cause. Those failures were a
   * cross-checkout `~/.m2` overwrite: another checkout's `mvn install` replacing
   * com.tesobe:obp-commons, which carries no Scala suffix, so nothing detects the mismatch. The
   * error named it four lines below the line that gets read - "A signature in
   * ~/.m2/.../obp-commons-1.10.1.jar refers to JValue/T in package object org.json4s.package which
   * is not available" - and fingerprinting the jar during a later run caught the swap live. The
   * isolation experiment was confounded: a green run only meant `~/.m2` happened to be right that
   * time. With the repository isolated (`-Dmaven.repo.local`), the suite is 3870/0 on H2 and
   * Postgres with this scoping in place and no other change.
   *
   * The scoping below stands on its own regardless: a process-wide singleton that grows a search
   * path per classloader and never releases one is a hazard under forkMode=once, where a single
   * JVM runs a whole shard. Fixing the right thing and explaining it wrongly are different
   * mistakes; only the explanation is retracted.
   */
  private def getClassPool(classLoader: ClassLoader) = memoClassPool.memoize(classLoader){
    val cp = new ClassPool(true)
    cp.appendClassPath(new LoaderClassPath(classLoader))
    cp
  }

  // The "compile each distinct source once" cache moved into DynamicScalaCompiler, which is
  // where the compiling happens now.

  type DynamicFunction = (Array[AnyRef], Option[CallContext]) => Future[Box[(String, Option[CallContext])]]

  /**
   * True when a SecurityManager is installed, i.e. when anything could confine dynamic code.
   *
   * JEP 486 removed SecurityManager in JDK 24, and the permission-set wrapper that used it has been
   * removed too (see DynamicCodeBody below), so on every JVM OBP supports this is false and
   * dynamic code runs with the full rights of the process. Kept as the condition rather than
   * hard-coded so the second acceptance below states what it is guarding against.
   */
  private def sandboxCanEnforce: Boolean = System.getSecurityManager != null

  /**
   * True when the operator has explicitly accepted running user code with no enforceable sandbox.
   */
  private def unsandboxedExecutionAccepted: Boolean =
    APIUtil.getPropsAsBoolValue("allow_user_generated_scala_code_without_sandbox", false)

  /**
   * Compile scala code
   * toolBox have bug that first compile fail, second or later compile success.
   * @param code
   * @return compiled Full[function|object|class] or Failure
   */
  def compileScalaCode[T](code: String): Box[T] = {
    if (!dynamicCodeExecutionEnabled)
      return Failure(ErrorMessages.DynamicCodeExecutionDisabled)
    // Second consent, only on a runtime where nothing can confine the code. `allow_user_generated_scala_code`
    // may have been turned on when a sandbox still restricted file, network and reflection access;
    // on JDK 24+ nothing does, so the same switch now means something much larger than it did when
    // it was set. Refusing to compile - rather than refusing to boot - keeps the failure
    // scoped to the feature that lost its isolation, and leaves a deployment that means it one
    // deliberate edit away from working. Default deployments are unaffected: the feature is off.
    if (!sandboxCanEnforce && !unsandboxedExecutionAccepted)
      return Failure(ErrorMessages.DynamicCodeExecutionUnsandboxed)
    compileScalaCodeUnchecked[T](code)
  }

  // Used ONLY by DynamicUtil.Validation's props-driven config parsing (operator config,
  // not user-generated code) so the app can still boot with the kill-switch off.
  //
  // The compiler itself lives behind DynamicScalaCompiler: Scala 3 has no ToolBox, so the
  // flip swaps the implementation instead of rewriting this method's callers. Caching and
  // the compile-error / evaluation-error distinction moved into the implementation with it;
  // this method only adapts the result back to the Box shape callers expect.
  private def compileScalaCodeUnchecked[T](code: String): Box[T] = {
    logger.trace(s"code.api.util.DynamicUtil.compileScalaCode.size is ${scalaCompiler.cachedCount}")
    scalaCompiler.compile(code) match {
      case Right(value)                                   => Full(value.asInstanceOf[T])
      case Left(DynamicCompileFailure(message, None))     => Failure(message)
      case Left(DynamicCompileFailure(message, Some(ex))) => Failure(message, Full(ex), Empty)
    }
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
   * @param force accepted from callers written for a version that gated this scan on
   *              SHOW_USED_CONNECTOR_METHODS. The scan is not gated: SHOW_USED_CONNECTOR_METHODS opts in
   *              to an unrelated diagnostic (which connector methods a static endpoint touches), and
   *              gating the security validation that reuses this bytecode scan on it handed
   *              Validation.validateDependency an always-empty list on every deployment that left the
   *              diagnostic at its default, so every restricted call passed.
   *              DynamicCodeDependencyScanTest fails if the scan goes quiet again.
   * @return
   */
  def getDynamicCodeDependentMethods(clazz: Class[_], predicate:  String => Boolean = _ => true, force: Boolean = false): List[(String, String, String)] =
  {
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
    // A class the snippet's own loader defines (the wrapper object, a local object or class, a closure)
    // is part of the snippet whatever its name: the compiler names the wrapper without a package, so the
    // package-prefix test above never matches it, and a call into it would stay an unpoliced leaf with
    // the restricted call inside it never scanned. The loader's parent classes (OBP's own) are not.
    val snippetLoader = clazz.getClassLoader
    def definedBySnippet(typeName: String): Boolean =
      snippetLoader != null && snippetLoader != classOf[APIUtil.type].getClassLoader &&
        scala.util.Try(Class.forName(typeName, false, snippetLoader)).toOption.exists(_.getClassLoader eq snippetLoader)

    def expand(typeName: String, methodName: String, signature: String, visited: Set[(String, String, String)]): List[(String, String, String)] = {
      val key = (typeName, methodName, signature)
      val sameUnit = typeName == className ||
        (className.startsWith(typeName) && methodName.startsWith(clazz.getPackage.getName + "$")) ||
        definedBySnippet(typeName)
      if (!sameUnit) {
        List(key)
      } else if (visited.contains(key)) {
        Nil
      } else {
        APIUtil.getDependentMethods(typeName, methodName, signature, force, classPool).flatMap { case (t, m, s) =>
          expand(t, m, s, visited + key)
        }
      }
    }

    // The compiler puts a closure's body in a class of its own, reached from the class under test through
    // an invokedynamic call that bytecode inspection does not see as a method call. So every class the
    // snippet's loader defines is scanned, not only the one that was handed in.
    val snippetClassNames: List[String] = snippetLoader match {
      case urlLoader: java.net.URLClassLoader =>
        urlLoader.getURLs.toList.flatMap { url =>
          scala.util.Try(java.nio.file.Paths.get(url.toURI)).toOption.filter(java.nio.file.Files.isDirectory(_)).toList.flatMap { dir =>
            val walk = java.nio.file.Files.walk(dir)
            try scala.jdk.CollectionConverters.IteratorHasAsScala(walk.iterator()).asScala.toList
              .map(dir.relativize(_).toString)
              .filter(_.endsWith(".class"))
              .map(_.stripSuffix(".class").replace(java.io.File.separatorChar, '.'))
            finally walk.close()
          }
        }
      case _ => Nil
    }

    for {
      scannedName <- (className :: snippetClassNames.filterNot(_ == className)).distinct
      scannedCtClass = if (scannedName == className) ctClass else classPool.get(scannedName)
      method <- scannedCtClass.getDeclaredMethods.toList
      if predicate(method.getName)
      (typeName, methodName, signature) <- APIUtil.getDependentMethods(scannedName, method.getName, method.getSignature, force, classPool)
    } yield {
      listBuffer.appendAll(expand(typeName, methodName, signature, Set.empty))
    }

    listBuffer.distinct.toList
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
