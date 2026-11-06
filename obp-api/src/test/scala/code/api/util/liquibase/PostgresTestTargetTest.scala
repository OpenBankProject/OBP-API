package code.api.util.liquibase

import org.scalatest.exceptions.{TestCanceledException, TestFailedException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The skip has to be opt-in, because a skip reports as a pass.
 *
 * PostgresMigrationTest is the only thing that exercises the Postgres DDL, and it cancelled itself
 * wherever no Postgres was listening - which was everywhere in CI. This pins the switch that makes
 * that a failure instead, so the Postgres check cannot go back to being silently absent.
 */
class PostgresTestTargetTest extends AnyFlatSpec with Matchers {

  private val target = "jdbc:postgresql://nowhere:5432/x"

  "an unreachable Postgres" should "cancel the test when it is not required" in {
    val thrown = the[TestCanceledException] thrownBy {
      PostgresTestTarget.requireReachable(reachable = false, target = target, required = false)
    }
    thrown.getMessage should include("skipping")
  }

  it should "fail the test when it is required" in {
    val thrown = the[TestFailedException] thrownBy {
      PostgresTestTarget.requireReachable(reachable = false, target = target, required = true)
    }
    thrown.getMessage should include("OBP_TEST_POSTGRES_REQUIRED")
    thrown.getMessage should include(target)
  }

  "a reachable Postgres" should "neither cancel nor fail, required or not" in {
    noException should be thrownBy {
      PostgresTestTarget.requireReachable(reachable = true, target = target, required = true)
    }
    noException should be thrownBy {
      PostgresTestTarget.requireReachable(reachable = true, target = target, required = false)
    }
  }

  "the switch" should "be off unless the environment says exactly true" in {
    // The environment cannot be set from inside the JVM, so what is pinned is the parsing rule:
    // only an explicit `true` (any case, surrounding spaces allowed) turns the skip into a failure.
    // Anything else - unset, empty, "1", "yes" - leaves the developer default in place.
    def parse(v: Option[String]): Boolean = v.exists(_.trim.equalsIgnoreCase("true"))
    parse(None) should equal(false)
    parse(Some("")) should equal(false)
    parse(Some("1")) should equal(false)
    parse(Some("yes")) should equal(false)
    parse(Some("false")) should equal(false)
    parse(Some("true")) should equal(true)
    parse(Some(" TRUE ")) should equal(true)
    PostgresTestTarget.required should equal(parse(sys.env.get("OBP_TEST_POSTGRES_REQUIRED")))
  }
}
