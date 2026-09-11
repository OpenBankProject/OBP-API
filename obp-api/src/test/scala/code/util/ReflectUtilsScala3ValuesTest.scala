package code.util

import com.openbankproject.commons.util.ReflectUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * `ReflectUtils.getValues` has to see the members of a Scala-3-compiled object.
 *
 * It is the collector behind every `allFields` in this module - SwaggerDefinitionsJSON,
 * MessageDocsSwaggerDefinitions, JSONFactoryCustom300, SandboxData - and it selects members with
 * `symbol.isVal || symbol.isVar`. Those answer from Scala's own declaration metadata, which for
 * Scala 3 lives in TASTy, and scala.reflect.runtime.universe (the Scala 2.13 reflection library
 * obp-commons is pinned to) has no TASTy reader: both come back false for every member of a
 * Scala-3-compiled class. So `getValues` returns nothing here, and each of those `allFields` is an
 * empty list - silently, because nothing asserted on their size.
 *
 * This test lives in obp-api rather than beside ReflectUtils in obp-commons for the reason it
 * exists: obp-commons compiles on 2.13, where `isVal` works and the bug cannot be reproduced.
 */
/**
 * Top-level, like every real caller (SwaggerDefinitionsJSON, MessageDocsSwaggerDefinitions, ...).
 * A nested object would not reproduce the case: scala-reflect cannot even load the symbol for an
 * object declared inside a class, so the test would fail on the fixture instead of on the bug.
 */
object ReflectUtilsScala3ValuesSample {
  lazy val lazyOne: String = "one"
  lazy val lazyTwo: Int = 2
  val plainThree: String = "three"
  var mutableFour: Int = 4
  lazy val excluded: String = "skip me"
  def notAField: String = "method, not a value"
}

class ReflectUtilsScala3ValuesTest extends AnyFlatSpec with Matchers {

  private val sample = ReflectUtilsScala3ValuesSample

  "getValues on a Scala 3 object" should "return its vals, lazy vals and vars" in {
    val values = ReflectUtils.getValues(sample, List("excluded"))

    withClue("getValues returned nothing at all - the Scala 3 members were not recognised: ") {
      values should not be empty
    }
    values should contain allOf ("one", 2, "three", 4)
  }

  it should "honour the excludes list" in {
    val values = ReflectUtils.getValues(sample, List("excluded"))
    values should not contain "skip me"
  }

  it should "not report a plain zero-arg def as a value" in {
    val values = ReflectUtils.getValues(sample, List("excluded"))
    withClue("a method with no backing field must not be collected as a field: ") {
      values should not contain "method, not a value"
    }
  }
}
