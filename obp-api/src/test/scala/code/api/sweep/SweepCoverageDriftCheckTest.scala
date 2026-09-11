package code.api.sweep

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import scala.io.Source

/**
 * Guards SweepCoverageTest's "the failure sweep covers the same set the auth sweep does"
 * scenario against being vacuous.
 *
 * That scenario computes `authScope` and `failureScope` and asserts their symmetric difference
 * is empty -- but if both are built by literally re-typing the same filter expression twice
 * (`catalog.filter(EndpointCatalog.skipReason(_).isEmpty).map(_.operationId).toSet`, written out
 * in SweepCoverageTest.scala rather than read from AuthSweepTest/FailureSweepTest themselves),
 * the two values are equal BY CONSTRUCTION regardless of what those two sweeps actually iterate
 * over. The assertion can then never fail, even after a real future divergence -- one sweep
 * gains a filter of its own, and the endpoints that fall between them are covered by neither,
 * silently, forever, because the "guard" was checking two copies of itself.
 *
 * The fix is for SweepCoverageTest to read AuthSweepTest.scope and FailureSweepTest.scope --
 * each sweep's own single definition of what it covers -- instead of re-deriving a copy. This
 * test is a source scan rather than a runtime assertion because a value-equality check on the
 * CURRENT catalog cannot distinguish "computed from the real source" from "coincidentally equal
 * duplicate" -- both produce the identical Set today; the difference only matters for whether a
 * FUTURE divergence gets caught, which a static duplicate can never do regardless of what the
 * catalog looks like when the test runs.
 */
class SweepCoverageDriftCheckTest extends AnyFlatSpec with Matchers {

  private def sourceOf(basename: String): String = {
    val candidates = List(
      new File(s"src/test/scala/code/api/sweep/$basename"),
      new File(s"obp-api/src/test/scala/code/api/sweep/$basename")
    )
    val file = candidates.find(_.isFile).getOrElse(
      fail(s"Cannot locate $basename under either candidate path - this guard must not pass by " +
           s"failing to look. Tried: ${candidates.mkString(", ")}"))
    val source = Source.fromFile(file, "UTF-8")
    try source.mkString finally source.close()
  }

  private lazy val sweepCoverageSource = sourceOf("SweepCoverageTest.scala")

  "the drift-check scenario" should "read AuthSweepTest's own scope, not a re-derived copy" in {
    withClue("SweepCoverageTest must reference AuthSweepTest.scope so a future change to " +
             "AuthSweepTest's own filtering is automatically reflected here instead of silently " +
             "diverging from a hand-copied duplicate: ") {
      sweepCoverageSource should include("AuthSweepTest.scope")
    }
  }

  it should "read FailureSweepTest's own scope, not a re-derived copy" in {
    withClue("SweepCoverageTest must reference FailureSweepTest.scope for the same reason: ") {
      sweepCoverageSource should include("FailureSweepTest.scope")
    }
  }

  it should "not compute authScope/failureScope by writing the skipReason filter out twice" in {
    val duplicateFilterPattern =
      """catalog\.filter\(EndpointCatalog\.skipReason\(_\)\.isEmpty\)\.map\(_\.operationId\)\.toSet""".r
    val occurrences = duplicateFilterPattern.findAllIn(sweepCoverageSource).length
    withClue(s"found $occurrences occurrence(s) of the raw filter expression written directly " +
             s"in SweepCoverageTest.scala. Two occurrences means authScope and failureScope are " +
             s"each an independent copy of the same literal, equal by construction regardless of " +
             s"what AuthSweepTest/FailureSweepTest actually cover -- the exact vacuousness this " +
             s"guard exists to catch. Expected zero: the scopes should come from " +
             s"AuthSweepTest.scope / FailureSweepTest.scope instead. ") {
      occurrences shouldBe 0
    }
  }
}
