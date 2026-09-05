package code.api.util.dynamiccompiler

/**
 * Why a compile failed: the compiler's own message, plus the exception when the failure came
 * from evaluating the compiled code rather than from compiling it.
 *
 * The distinction is not cosmetic. `DynamicUtil` turned a compile error into `Failure(message)`
 * but an evaluation error into `Box.tryo`'s `Failure(message, Full(exception), Empty)`, and the
 * dynamic-endpoint error paths surface that cause. Collapsing both to a bare string would drop
 * the stack trace of a customer's failing method_body.
 */
case class DynamicCompileFailure(message: String, cause: Option[Throwable] = None)

/**
 * The one place that turns a string of Scala source into a runnable value.
 *
 * This exists for the Scala 3 migration. Today the implementation is a Scala 2.13
 * `ToolBox`; Scala 3 has no ToolBox at all (`scala.quoted.staging` compiles quotes, not
 * strings), so the flip replaces the implementation behind this interface rather than
 * editing every caller.
 *
 * Why the compiler cannot simply stay on 2.13 while obp-api moves to Scala 3 - the
 * arrangement the migration plan originally assumed: the code being compiled is not
 * self-contained. `DynamicUtil.importStatements` puts obp-api's own classes in scope
 * (`code.api.util.{APIUtil, CallContext}`, `code.bankconnectors._`, `code.api.cache.Caching`,
 * …), and `InternalConnector` generates method signatures from the `Connector` trait and
 * calls back into `InternalConnector.postProcessConnectorMethodResult`. After the flip those
 * are Scala 3 classes carrying TASTy, and a 2.13 compiler cannot read TASTy. So the dynamic
 * compiler must always be the same Scala version as obp-api itself, and the isolation this
 * seam provides is of the compiler API, not of the Scala version.
 *
 * Contract that any implementation must keep, because callers depend on all of it:
 *  - the result is the value of the source's last expression, so a source ending in
 *    `methodName _` yields that eta-expanded function;
 *  - the same source text compiles and evaluates ONCE - the result is cached and reused.
 *    Dynamic endpoints re-submit identical source on every request, and re-evaluating a
 *    top-level expression per request would change both cost and behaviour;
 *  - neither a compile error nor an error thrown while evaluating escapes as an exception:
 *    both come back as `Left`, with `cause` set for the second kind.
 */
trait DynamicScalaCompiler {

  /** Compile `code`, evaluate it, and return its value - cached per source text. */
  def compile(code: String): Either[DynamicCompileFailure, Any]

  /** Number of distinct sources currently held in the compile cache (for logging). */
  def cachedCount: Int
}
