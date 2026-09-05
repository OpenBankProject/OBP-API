package code.setup

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.exceptions.{TestCanceledException, TestFailedException}

/**
 * The gate itself, both ways.
 *
 * PostgresTestTargetTest exists for the same reason and states it plainly: the skip has to be
 * opt-in, because a skip reports as a pass. A guard whose strict branch is never entered by any
 * test is a guard nobody has checked, and this one's whole purpose is to stop exactly that shape
 * of untested branch.
 *
 * `required` is passed explicitly rather than read from the environment, because the environment
 * cannot be changed from inside a running JVM -- which is why RedisTestTarget takes it as a
 * parameter in the first place.
 */
class RedisTestTargetTest extends AnyFlatSpec with Matchers {

  /** Arbitrary label passed as `what`; only its identity across calls matters, not its text. */
  private val CheckLabel = "a check"

  "requireReachable" should "return normally when Redis is reachable, whether or not it is required" in {
    noException should be thrownBy RedisTestTarget.requireReachable(
      reachable = true, what = CheckLabel, required = false)
    noException should be thrownBy RedisTestTarget.requireReachable(
      reachable = true, what = CheckLabel, required = true)
  }

  it should "cancel when Redis is absent and optional" in {
    val e = intercept[TestCanceledException] {
      RedisTestTarget.requireReachable(reachable = false, what = CheckLabel, required = false)
    }
    e.getMessage should include("Redis not reachable")
    e.getMessage should include(CheckLabel)
  }

  it should "FAIL, not cancel, when Redis is absent and required" in {
    val e = intercept[TestFailedException] {
      RedisTestTarget.requireReachable(reachable = false, what = CheckLabel, required = true)
    }
    e.getMessage should include("OBP_TEST_REDIS_REQUIRED=true")
    e.getMessage should include(CheckLabel)
  }

  "required" should "read OBP_TEST_REDIS_REQUIRED and default to false" in {
    // Whatever this environment says, the value has to be a Boolean derived from that one
    // variable -- and unset must mean false, so a developer machine keeps the skip.
    val fromEnv = sys.env.get("OBP_TEST_REDIS_REQUIRED").exists(_.trim.equalsIgnoreCase("true"))
    RedisTestTarget.required shouldBe fromEnv
    if (sys.env.get("OBP_TEST_REDIS_REQUIRED").isEmpty) RedisTestTarget.required shouldBe false
  }
}
