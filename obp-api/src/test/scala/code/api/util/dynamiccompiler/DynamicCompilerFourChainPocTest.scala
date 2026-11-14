package code.api.util.dynamiccompiler

import code.api.util.DynamicUtil
import code.setup.PropsReset
import net.liftweb.common.{Box, Failure, Full}
import org.scalatest.Tag
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object DynamicCompilerPocTag extends Tag("DynamicCompilerPoc")

/**
 * The migration plan's S2 acceptance test: a legacy-style `method_body` must still compile and
 * execute through every chain that compiles Scala at run time, with the compiler behind the
 * DynamicScalaCompiler seam rather than called directly.
 *
 * The four chains and the shape each one wraps a customer's method_body in:
 *   1. Dynamic Connector      - `DynamicConnector`: importStatements + body, returns a function
 *   2. Internal Connector     - `InternalConnector.createScalaFunction`: a def whose signature
 *                               comes from the Connector trait, body wrapped, then `name _`
 *   3. Dynamic Endpoints      - `DynamicCompileEndpoint`: body compiled to a function of
 *                               (json, callContext)
 *   4. ABAC rules             - `AbacRuleEngine`: a boolean expression over rule inputs
 *
 * The snippets below are deliberately written the way stored method_body values are written -
 * plain Scala 2 style, no Scala 3 syntax - because that is the thing that has to keep working.
 * At the flip this suite is what proves a `dotty.tools.dotc` implementation still accepts them
 * (plan risk F-9: dynamic code semantics must not change).
 *
 * The kill-switch has its own suite (DynamicCompilerKillSwitchTest) rather than a scenario
 * here: every test in this one pushes allow_user_generated_scala_code=true, and Lift's
 * provider precedence means a later push of "false" does not win over them - the assertion
 * passed alone and failed in the full suite. PropsReset clears owned pushes per suite, so a
 * suite that only ever pushes "false" is deterministic.
 */
class DynamicCompilerFourChainPocTest extends AnyFlatSpec with Matchers with PropsReset {

  // PropsReset removes what a test pushed once the suite ends, so setting the switch per test
  // is enough here; the off case lives in its own suite for the reason given above.
  private def withDynamicCodeEnabled[A](f: => A): A = {
    setPropsValues("allow_user_generated_scala_code" -> "true")
    f
  }

  private def compiled[T](code: String): T =
    DynamicUtil.compileScalaCode[T](code) match {
      case Full(v)              => v
      case Failure(msg, ex, _)  => fail(s"compile failed: $msg${ex.map(e => s" / ${e.getMessage}").openOr("")}")
      case other                => fail(s"compile returned $other")
    }

  "chain 1 - Dynamic Connector style" should "compile a method_body that returns a value" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      // A Dynamic Connector body: an expression over the method's parameters.
      val fn = compiled[String => String](
        """def getBankName(bankId: String): String = { "bank-" + bankId }
          |getBankName _""".stripMargin)
      fn("gh.29.uk.x1") should be("bank-gh.29.uk.x1")
    }
  }

  "chain 2 - Internal Connector style" should "compile a def whose body is wrapped like createScalaFunction wraps it" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      // Mirrors InternalConnector.createScalaFunction: the customer body becomes the right-hand
      // side of `val _$result$_`, then a post-processing call, then eta-expansion.
      val fn = compiled[Int => Int](
        """def getChargeLevel(amount: Int) = {
          |  val _$result$_ = { amount * 2 }
          |  _$result$_ + 1
          |}
          |getChargeLevel _""".stripMargin)
      fn(21) should be(43)
    }
  }

  "chain 3 - Dynamic Endpoint style" should "compile a two-argument function over a request and a context" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      // DynamicCompileEndpoint compiles to a function of (body, context); modelled here with
      // plain types so the POC does not depend on endpoint plumbing.
      val fn = compiled[(String, String) => String](
        """def process(body: String, context: String): String = {
          |  val parts = List(body, context).filter(_.nonEmpty)
          |  parts.mkString("|")
          |}
          |process _""".stripMargin)
      fn("payload", "ctx") should be("payload|ctx")
    }
  }

  "chain 4 - ABAC rule style" should "compile a boolean rule expression" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      val rule = compiled[(String, Int) => Boolean](
        """def evaluate(role: String, amount: Int): Boolean = {
          |  role == "CanCreateTransactionRequest" && amount <= 1000
          |}
          |evaluate _""".stripMargin)
      rule("CanCreateTransactionRequest", 999) should be(true)
      rule("CanCreateTransactionRequest", 1001) should be(false)
      rule("SomethingElse", 1) should be(false)
    }
  }

  "the compiler" should "report a compile error as a failure rather than throwing" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      val result: Box[Any] = DynamicUtil.compileScalaCode[Any]("def broken(: = ???")
      result.isDefined should be(false)
    }
  }

  it should "carry the exception when the failure comes from evaluating, not compiling" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      // Compiles cleanly, throws when the top-level expression is evaluated. DynamicUtil
      // distinguished these two cases and callers surface the cause, so the seam must too.
      val result: Box[Any] = DynamicUtil.compileScalaCode[Any]("""throw new RuntimeException("boom-from-evaluation")""")
      result match {
        case Failure(_, ex, _) =>
          // The whole chain, not just getMessage: the ToolBox hands back the user's exception
          // wrapped, and the wrapper's own message is null. Box.tryo behaved the same way
          // before this seam existed, so asserting on the top-level message would be asserting
          // a change that did not happen.
          def chain(t: Throwable): List[Throwable] = if (t == null) Nil else t :: chain(t.getCause)
          val messages = ex.toList.flatMap(chain).map(t => s"${t.getClass.getName}: ${t.getMessage}")
          withClue(s"exception chain was $messages: ") {
            messages.exists(_.contains("boom-from-evaluation")) should be(true)
          }
        case other => fail(s"expected a Failure carrying the exception, got $other")
      }
    }
  }

  "compiling the same source twice" should "evaluate it once and reuse the result" taggedAs DynamicCompilerPocTag in {
    withDynamicCodeEnabled {
      // A counter in the compiled source proves the caching contract: re-submitting identical
      // source must not re-run the top-level expression.
      val source =
        """object PocCounter { val id = java.util.UUID.randomUUID().toString }
          |PocCounter.id""".stripMargin
      val first  = compiled[String](source)
      val second = compiled[String](source)
      second should be(first)
    }
  }
}
