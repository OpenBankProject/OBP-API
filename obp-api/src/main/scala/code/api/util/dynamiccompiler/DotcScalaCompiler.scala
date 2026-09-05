package code.api.util.dynamiccompiler

import code.util.Helper.MdcLoggable

import java.net.{URL, URLClassLoader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.util.control.NonFatal

import dotty.tools.dotc.Driver
import dotty.tools.dotc.reporting.Diagnostic

/**
 * Scala 3 implementation of [[DynamicScalaCompiler]], driving the compiler directly
 * (`dotty.tools.dotc.Driver`) rather than through a REPL-style API - Scala 3 has no
 * ToolBox, and `scala.quoted.staging` compiles quotes rather than arbitrary source text.
 *
 * The shape of a `dotc` invocation is the CLI's: source file(s) in, `-classpath`/`-d` as
 * flags, a `Reporter` back. So each distinct source is wrapped as the body of a synthetic
 * top-level object, written to a temp source file, compiled to a temp output directory, then
 * loaded with a `URLClassLoader` whose parent is the current thread's context classloader (so
 * the snippet can resolve obp-api's own classes, exactly like the classes it was compiled
 * alongside). `evaluate` calls the module's `result` method exactly once - that IS the
 * "evaluate the snippet" step, and it happens inside the cache's `computeIfAbsent`, matching
 * the contract in [[DynamicScalaCompiler]].
 *
 * The wrapping hoists only the snippet's top-level type definitions (`case class`, `class`,
 * `object`, `trait`, `enum`) out to be direct siblings of `result`; everything else - imports,
 * `val`/`def` bindings, and the trailing expression whose value the caller wants - stays
 * together inside `result`'s own body, in original order:
 *
 * {{{
 *   object DynCompiled_<n> {
 *     <hoisted type definitions, verbatim>
 *     def result: Any = {
 *       <everything else, verbatim, trailing expression last>
 *     }
 *   }
 * }}}
 *
 * The hoist (see `hoistTypeDefinitions`) exists because `json4s`'s `Reflector` refuses to
 * extract into a "case class defined in function bodies": a case class produced by
 * `JsonUtils.toCaseClasses` and consumed later in the same snippet (`DynamicUtil.toCaseObject`)
 * must be a genuine member of the wrapping object, not nested inside a block - a whole-snippet
 * `result: Any = { <code> }` wrap would put it exactly there. Everything else stays inside the
 * block deliberately: an `implicit val` with no explicit type (a very common shape here -
 * `implicit val formats = ...`) only type-checks without one when it is local, since Scala 3
 * requires an explicit result type on an implicit that is a class/object member.
 *
 * `result` is a `def`, not a `val`, so that a `return` inside a lambda the snippet defines
 * (a runtime-compiled dynamic-endpoint body legitimately does this - see
 * `code.api.dynamic.endpoint.helper.DynamicEndpointCodeGenerator`, and the non-local-return
 * recovery in `DynamicUtil.Sandbox.runInSandboxIO`) has a real enclosing method to target.
 * Wrapping in a `val` instead compiles to a *field* initializer with no enclosing method at
 * all, and such a `return` fails with "return outside method definition". Evaluating twice is
 * not a risk this creates: nothing here calls `result` more than the one time `evaluate` does.
 */
object DotcScalaCompiler extends DynamicScalaCompiler with MdcLoggable {

  // Keyed by source text: the same code always yields the same value, and dynamic endpoints
  // re-submit identical source on every request.
  private val compiled = new ConcurrentHashMap[String, Either[DynamicCompileFailure, Any]]()

  private val nextId = new AtomicLong(0)

  // The classpath this JVM was started with - the compiled snippet must resolve obp-api's
  // own classes (code.api.util.APIUtil, code.bankconnectors.Connector, ...), so it has to be
  // compiled against the same classpath obp-api itself is currently running with, not a
  // freshly-resolved one. Read once: it does not change for the lifetime of the JVM.
  private val runtimeClasspath: String = System.getProperty("java.class.path")

  def cachedCount: Int = compiled.size()

  def compile(code: String): Either[DynamicCompileFailure, Any] = {
    logger.trace(s"DotcScalaCompiler cache size is ${compiled.size()}")
    compiled.computeIfAbsent(code, _ => compileAndEvaluate(code))
  }

  private def compileAndEvaluate(code: String): Either[DynamicCompileFailure, Any] = {
    val wrapperName = s"DynCompiled_${nextId.incrementAndGet()}"
    // `code.api.util.DynamicUtil.importStatements` is prepended unconditionally - some
    // callers already prepend it themselves (harmless: Scala tolerates a repeated identical
    // import), but others pass a snippet that only compiles because these imports (and, in
    // particular, `org.json4s.{JValue, _}`'s extraction extension methods) are in scope. That
    // was true for free under Scala 2's ToolBox, whose implicit-scope search reached
    // `org.json4s`'s package-object implicits via the `JValue` type alias without an explicit
    // import; Scala 3's extension-method lookup does not extend that far, so a snippet like
    // `jValue.extract[T]` (see `DynamicUtil.toCaseObject`) fails to compile unless the import
    // is actually present in this compilation unit.
    val withImports = s"${_root_.code.api.util.DynamicUtil.importStatements}\n$code"
    val (hoisted, kept) = hoistTypeDefinitions(withImports)
    val source =
      s"""object $wrapperName {
         |${hoisted.mkString("\n")}
         |  def result: Any = {
         |${kept.mkString("\n")}
         |  }
         |}
         |""".stripMargin

    var workDir: Path = null
    try {
      workDir = Files.createTempDirectory("obp-dynamic-compile")
      val srcDir = Files.createDirectory(workDir.resolve("src"))
      val outDir = Files.createDirectory(workDir.resolve("out"))
      val sourceFile = srcDir.resolve(s"$wrapperName.scala")
      Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8))

      val args = Array(
        "-classpath", runtimeClasspath,
        "-d", outDir.toString,
        "-deprecation",
        "-feature",
        "-source:3.3",
        sourceFile.toString
      )

      val reporter = new Driver().process(args)

      if (reporter.hasErrors) {
        Left(DynamicCompileFailure(formatErrors(reporter.allErrors), None))
      } else {
        evaluate(wrapperName, outDir)
      }
    } catch {
      case NonFatal(e) => Left(DynamicCompileFailure(e.getMessage, Some(e)))
    } finally {
      // Not an immediate delete: the JVM does not necessarily resolve every class the
      // compiled module references at `evaluate` time. A class referenced only inside a
      // returned closure's body (e.g. the case classes `DynamicUtil.toCaseObject` generates,
      // used only inside the lambda it returns) is linked lazily, the first time that closure
      // is actually invoked - which for a cached, reused dynamic function can be long after
      // this method returns. Deleting `outDir` here intermittently broke exactly that case:
      // the class loaded fine, but a *later* call into it failed with a misleading error from
      // `json4s`'s `Reflector` (it collapses several distinct reflection failures, including
      // "class file no longer readable", into the same "defined in function bodies" message).
      // So the compiled classes are kept on disk for the life of the JVM and only registered
      // for best-effort deletion at shutdown - acceptable because how many distinct sources
      // get compiled is bounded by the same cache that makes this a compile-once operation.
      if (workDir != null) registerForDeleteOnExit(workDir)
    }
  }

  // Only these introduce a class file `json4s`'s `Reflector` (or ordinary reflection) might
  // need to see as a proper named member rather than a local class - a plain `val`/`def`/
  // `import`, or the trailing expression, has no such requirement and is left where a naive
  // whole-snippet wrap would have put it: inside `result`'s own block.
  private val typeDefKeywords = Set("case class", "case object", "class", "object", "trait", "enum")

  private val modifierKeywords =
    Set("private", "protected", "implicit", "final", "sealed", "abstract", "lazy", "override")

  private def isTypeDefinition(statement: String): Boolean = {
    var rest = statement.trim
    var strippedAModifier = true
    while (strippedAModifier) {
      strippedAModifier = false
      val keyword = modifierKeywords.find(k => rest.startsWith(k) && rest.drop(k.length).headOption.exists(_.isWhitespace))
      keyword.foreach { k => rest = rest.drop(k.length).trim; strippedAModifier = true }
    }
    typeDefKeywords.exists(k => rest.startsWith(k) && rest.drop(k.length).headOption.forall(c => c.isWhitespace || c == '['))
  }

  /**
   * Splits `code` into the top-level type definitions it contains (hoisted out, so they end
   * up as siblings of `result` rather than nested inside its block) and everything else
   * (kept, in original relative order, to go inside `result`'s block).
   *
   * This is a lightweight scan, not a real parser: it tracks bracket/brace/paren depth and
   * string/comment state well enough for the shapes this compiler actually receives (import
   * lines, `case class`/`def`/`val` definitions each on their own line(s), and a trailing
   * expression or multi-line lambda) - not full Scala grammar. A line is only considered a
   * fresh top-level statement boundary when it starts at bracket depth 0 outside any string
   * or comment; a line starting with `.` is treated as a continuation of the previous
   * statement (a chained call split across lines), the one multi-line-expression shape these
   * snippets are known to use.
   */
  private def hoistTypeDefinitions(code: String): (List[String], List[String]) = {
    val n = code.length
    var i = 0
    var depth = 0
    var inLineComment = false
    var inBlockComment = false
    var blockCommentDepth = 0
    var inTripleQuote = false
    var inString = false
    var inChar = false
    val boundaries = scala.collection.mutable.ListBuffer(0)

    while (i < n) {
      val c = code.charAt(i)
      if (c == '\n') {
        inLineComment = false
        if (depth == 0 && !inBlockComment && !inString && !inTripleQuote && !inChar) {
          val nextLineStart = i + 1
          var j = nextLineStart
          while (j < n && code.charAt(j) != '\n') j += 1
          val nextLine = code.substring(nextLineStart, j).trim
          if (nextLine.nonEmpty && !nextLine.startsWith("//") && !nextLine.startsWith("."))
            boundaries += nextLineStart
        }
        i += 1
      } else if (inLineComment) {
        i += 1
      } else if (inBlockComment) {
        if (c == '*' && i + 1 < n && code.charAt(i + 1) == '/') {
          blockCommentDepth -= 1
          if (blockCommentDepth == 0) inBlockComment = false
          i += 2
        } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
          blockCommentDepth += 1
          i += 2
        } else i += 1
      } else if (inTripleQuote) {
        if (code.startsWith("\"\"\"", i)) { inTripleQuote = false; i += 3 }
        else i += 1
      } else if (inString) {
        if (c == '\\') i += 2
        else if (c == '"') { inString = false; i += 1 }
        else i += 1
      } else if (inChar) {
        if (c == '\\') i += 2
        else if (c == '\'') { inChar = false; i += 1 }
        else i += 1
      } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
        inLineComment = true; i += 2
      } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
        inBlockComment = true; blockCommentDepth = 1; i += 2
      } else if (code.startsWith("\"\"\"", i)) {
        inTripleQuote = true; i += 3
      } else if (c == '"') {
        inString = true; i += 1
      } else if (c == '\'') {
        inChar = true; i += 1
      } else if (c == '{' || c == '(' || c == '[') {
        depth += 1; i += 1
      } else if (c == '}' || c == ')' || c == ']') {
        depth -= 1; i += 1
      } else {
        i += 1
      }
    }

    val offsets = boundaries.distinct.sorted.toList
    val statements = offsets.zip(offsets.drop(1) :+ n).map { case (start, end) => code.substring(start, end) }

    val hoisted = scala.collection.mutable.ListBuffer.empty[String]
    val kept = scala.collection.mutable.ListBuffer.empty[String]
    statements.foreach { statement =>
      if (statement.trim.nonEmpty) {
        if (isTypeDefinition(statement)) hoisted += statement else kept += statement
      }
    }
    (hoisted.toList, kept.toList)
  }

  // Uses the Java-facing `interfaces.Diagnostic`/`interfaces.SourcePosition` API rather than
  // the Scala-side `Diagnostic.pos`/`SourcePosition.line` accessors: the latter take an
  // implicit `Contexts.Context` (needed for expanding macro-generated positions), which this
  // call site has no reason to construct. The interface's no-arg methods give the same
  // information for a plain compile-error report.
  private def formatErrors(errors: List[Diagnostic.Error]): String =
    errors.map { error =>
      val diagnostic: dotty.tools.dotc.interfaces.Diagnostic = error
      val position = diagnostic.position()
      val prefix =
        if (position.isPresent) {
          val p = position.get()
          s"${p.source().name()}:${p.line() + 1}: "
        } else ""
      prefix + diagnostic.message()
    }.mkString("; ")

  // Loads the compiled module and calls its `result` method exactly once - that call is
  // where the wrapped snippet's body (including its trailing expression) actually executes,
  // so a `Throwable` from it is an evaluation-time failure of the snippet's own logic, not a
  // compile error, and must carry its cause. `InvocationTargetException` is the expected
  // shape (`result` is a `def`, invoked through reflection); `ExceptionInInitializerError` is
  // kept as a safety net in case merely loading/constructing the module itself ever fails.
  //
  // The loader is intentionally not closed here: the returned value may still reference
  // classes the module defined (e.g. an eta-expanded function, or a case class instance), and
  // some of those classes can still be *unresolved* at this point - the JVM does not
  // necessarily link a class referenced only inside a closure's body until that closure is
  // actually invoked, which can happen long after this method returns. Closing the loader (or
  // deleting `outDir`) before then breaks that later, deferred resolution.
  private def evaluate(wrapperName: String, outDir: Path): Either[DynamicCompileFailure, Any] = {
    try {
      val parent = Thread.currentThread().getContextClassLoader
      val loader = new URLClassLoader(Array[URL](outDir.toUri.toURL), parent)
      val moduleClass = Class.forName(s"$wrapperName$$", true, loader)
      val moduleInstance = moduleClass.getField("MODULE$").get(null)
      val resultMethod = moduleClass.getMethod("result")
      resultMethod.setAccessible(true)
      Right(resultMethod.invoke(moduleInstance))
    } catch {
      case e: ExceptionInInitializerError =>
        val cause = if (e.getCause != null) e.getCause else e
        Left(DynamicCompileFailure(cause.getMessage, Some(cause)))
      case e: java.lang.reflect.InvocationTargetException =>
        val cause = if (e.getCause != null) e.getCause else e
        Left(DynamicCompileFailure(cause.getMessage, Some(cause)))
      case NonFatal(e) =>
        Left(DynamicCompileFailure(e.getMessage, Some(e)))
    }
  }

  // Registers `path` (and, recursively, everything under it) for best-effort deletion when
  // the JVM exits normally. `File.deleteOnExit` deletes in the *reverse* of registration
  // order, so a parent must be registered before its children for the parent's removal to
  // actually happen after its children are gone rather than being attempted (and silently
  // failing, since a non-empty directory can't be deleted) first.
  private def registerForDeleteOnExit(path: Path): Unit = {
    try {
      path.toFile.deleteOnExit()
      if (Files.isDirectory(path)) {
        val stream = Files.list(path)
        try stream.forEach(registerForDeleteOnExit) finally stream.close()
      }
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Could not register temp file $path for delete-on-exit after dynamic compilation", e)
    }
  }
}
