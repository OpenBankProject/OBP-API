package code.api.util

import net.liftweb.common.{Full, Failure}

import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Verifies that the GraalVM Polyglot JS engine (org.graalvm.polyglot:polyglot 24.x)
 * loads and executes correctly at runtime. Requires JDK 17+ — GraalVM 24.x JARs are
 * compiled at class-file version 61.0 and throw UnsupportedClassVersionError on JDK 11.
 * If this test fails with that error, the runtime JDK must be upgraded to 17+.
 */
class DynamicUtilJsEngineTest extends AnyFlatSpec with Matchers {

  private val engineMustLoad = "GraalVM engine must load successfully"
  private val promiseMustResolve = "JS promise must resolve"

  "DynamicUtil.createJsFunction" should "load the GraalVM JS engine without error" in {
    val result = DynamicUtil.createJsFunction("return 42;")
    result shouldBe a [Full[_]]
  }

  it should "execute JS returning a literal and yield JSON-stringified result" in {
    val fn = DynamicUtil.createJsFunction("return 42;")
      .openOrThrowException(engineMustLoad)
    val boxResult = Await.result(fn(Array.empty[AnyRef], None), 10.seconds)
    boxResult shouldBe a [Full[_]]
    val (json, _) = boxResult.openOrThrowException(promiseMustResolve)
    json shouldBe "42"
  }

  it should "execute JS returning an object and yield valid JSON" in {
    val fn = DynamicUtil.createJsFunction("""return {"status": "ok", "value": 99};""")
      .openOrThrowException(engineMustLoad)
    val boxResult = Await.result(fn(Array.empty[AnyRef], None), 10.seconds)
    boxResult shouldBe a [Full[_]]
    val (json, _) = boxResult.openOrThrowException(promiseMustResolve)
    json should include ("\"status\"")
    json should include ("\"ok\"")
  }

  it should "return Failure on JS syntax error without throwing" in {
    val result = DynamicUtil.createJsFunction("{{ this is not valid JavaScript {{{{")
    result shouldBe a [Failure]
  }

  it should "pass args into JS and compute with them" in {
    val fn = DynamicUtil.createJsFunction("return args[0] * 2;")
      .openOrThrowException(engineMustLoad)
    val boxResult = Await.result(fn(Array[AnyRef](Integer.valueOf(21)), None), 10.seconds)
    boxResult shouldBe a [Full[_]]
    val (json, _) = boxResult.openOrThrowException(promiseMustResolve)
    json shouldBe "42"
  }
}
