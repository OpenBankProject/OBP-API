package code.api.sweep

import org.scalatest.{FlatSpec, Matchers}

import java.io.File
import scala.io.Source

/**
 * Guards against the "grant every role" and "find a real bank id" constructions drifting back
 * into byte-for-byte duplicates across the sweep test files.
 *
 * FailureSweepTest.omniscientUser and SuccessSweepTest.entitledCaller started as identical
 * bodies -- grant every ApiRole to resourceUser1, then build a DirectLogin header -- and
 * AuthSweepTest, FailureSweepTest and SuccessSweepTest each looked up
 * LocalMappedConnector.getBanksLegacy(None) independently. None of it lived in EndpointCatalog,
 * the module this package already treats as the one place shared sweep logic belongs (see
 * AuthSweepTest.scope / FailureSweepTest.scope and SweepCoverageDriftCheckTest, which exists for
 * exactly this reason on a different pair of definitions).
 *
 * A source scan, not a runtime assertion, for the same reason SweepCoverageDriftCheckTest is one:
 * a value-equality check on today's fixtures cannot distinguish "computed from one shared
 * definition" from "two copies that happen to still agree" -- both look identical today, and the
 * difference only matters for whether a FUTURE divergence gets caught.
 */
class SweepFixturesDuplicationTest extends FlatSpec with Matchers {

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

  "the 'grant every role, then build a DirectLogin header' construction" should
    "appear once, in a shared fixture, not once per sweep file" in {
    val pattern = """ApiRole\.availableRoles\.foreach""".r
    val occurrences = List("SweepFixtures.scala", "FailureSweepTest.scala", "SuccessSweepTest.scala")
      .map(f => f -> pattern.findAllIn(sourceOf(f)).length)
    val total = occurrences.map(_._2).sum

    withClue(s"occurrences per file: ${occurrences.mkString(", ")}. Two independent copies of " +
             s"the same 'grant every role' construction means a future change to how the " +
             s"omniscient test caller is built (a new role category, a different bank-selection " +
             s"rule) has to be applied by hand in both files, with nothing enforcing they stay " +
             s"in sync. Expected exactly one, in a fixture both files call. ") {
      total shouldBe 1
    }
  }

  "the LocalMappedConnector.getBanksLegacy(None) bank lookup" should
    "appear once, in a shared fixture, not once per sweep file" in {
    val pattern = """LocalMappedConnector\.getBanksLegacy\(None\)""".r
    val occurrences = List("SweepFixtures.scala", "AuthSweepTest.scala", "FailureSweepTest.scala", "SuccessSweepTest.scala")
      .map(f => f -> pattern.findAllIn(sourceOf(f)).length)
    val total = occurrences.map(_._2).sum

    withClue(s"occurrences per file: ${occurrences.mkString(", ")}. Three independent copies of " +
             s"the same bank lookup means a future change to how the fixture bank is found has " +
             s"to be applied by hand in three places, with nothing enforcing they stay in sync. " +
             s"Expected exactly one, in a fixture all three files call. ") {
      total shouldBe 1
    }
  }
}
