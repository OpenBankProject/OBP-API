package code.api.util.http4s

import code.setup.ServerSetup

/**
 * Every version object must register resource docs.
 *
 * `ResourceDocMiddleware` builds its lookup index from the version object's own `resourceDocs`, and
 * a request whose version has no entries in that index gets no doc - so authentication, role checks
 * and entity resolution never run and the caller sees a bare 401. That was reported from a real-jar
 * process for v3.0.0, five times, with the matcher's own debug line as evidence:
 *
 *     Index keys for apiVersion=v3.0.0:
 *
 * followed by nothing. It has never reproduced in a Maven test JVM (nor in eight later real-jar
 * boots), and the cause remains open; version-disabling, initialisation order and interception by a
 * higher version's middleware were each ruled out with code evidence rather than by hunch.
 *
 * This does not reproduce it. It converts the one condition that was directly observed into an
 * assertion, so an empty registration fails here - naming the version - instead of surfacing as an
 * unexplained 401 in whichever environment happens to hit it. Touching each object also proves its
 * initialiser runs at all, which is the failure mode the evidence points at.
 */
class VersionResourceDocsNonEmptyTest extends ServerSetup {

  // ServerSetup rather than a bare spec: touching a version object initialises ExampleValue, which
  // reads props and the database. A plain AnyFlatSpec aborts in <clinit> before any assertion runs.
  // Touch the nested Implementations object, not the routes value.
  //
  // `wrappedRoutesVxxxServices` is a Kleisli whose reference to `Implementations…` sits inside the
  // lambda, so evaluating it does NOT run that object's initialiser - and it is the initialiser
  // that appends every ResourceDoc and then builds the middleware's index from them. Measured:
  // forcing the routes value leaves resourceDocs at 0 for twelve of the thirteen versions. This
  // matters beyond the test - the earlier investigation of the v3.0.0 401 ruled out an
  // initialisation cause on the grounds that "gate takes routes by-value, so the object is always
  // touched", and that reasoning does not hold.
  private def versions: List[(String, Int)] = List(
      ("v1.2.1", { code.api.v1_2_1.Http4s121.Implementations1_2_1.hashCode(); code.api.v1_2_1.Http4s121.resourceDocs.size }),
      ("v1.3.0", { code.api.v1_3_0.Http4s130.Implementations1_3_0.hashCode(); code.api.v1_3_0.Http4s130.resourceDocs.size }),
      ("v1.4.0", { code.api.v1_4_0.Http4s140.Implementations1_4_0.hashCode(); code.api.v1_4_0.Http4s140.resourceDocs.size }),
      ("v2.0.0", { code.api.v2_0_0.Http4s200.Implementations2_0_0.hashCode(); code.api.v2_0_0.Http4s200.resourceDocs.size }),
      ("v2.1.0", { code.api.v2_1_0.Http4s210.Implementations2_1_0.hashCode(); code.api.v2_1_0.Http4s210.resourceDocs.size }),
      ("v2.2.0", { code.api.v2_2_0.Http4s220.Implementations2_2_0.hashCode(); code.api.v2_2_0.Http4s220.resourceDocs.size }),
      ("v3.0.0", { code.api.v3_0_0.Http4s300.Implementations3_0_0.hashCode(); code.api.v3_0_0.Http4s300.resourceDocs.size }),
      ("v3.1.0", { code.api.v3_1_0.Http4s310.Implementations3_1_0.hashCode(); code.api.v3_1_0.Http4s310.resourceDocs.size }),
      ("v4.0.0", { code.api.v4_0_0.Http4s400.Implementations4_0_0.hashCode(); code.api.v4_0_0.Http4s400.resourceDocs.size }),
      ("v5.0.0", { code.api.v5_0_0.Http4s500.Implementations5_0_0.hashCode(); code.api.v5_0_0.Http4s500.resourceDocs.size }),
      ("v5.1.0", { code.api.v5_1_0.Http4s510.Implementations5_1_0.hashCode(); code.api.v5_1_0.Http4s510.resourceDocs.size }),
      ("v6.0.0", { code.api.v6_0_0.Http4s600.Implementations6_0_0.hashCode(); code.api.v6_0_0.Http4s600.resourceDocs.size }),
      ("v7.0.0", { code.api.v7_0_0.Http4s700.Implementations7_0_0.hashCode(); code.api.v7_0_0.Http4s700.resourceDocs.size }))

  feature("every API version registers resource docs") {

    scenario("no version registers an empty set") {
      val unregistered = versions.collect { case (name, 0) => name }
      withClue("these versions registered no resource docs, so ResourceDocMiddleware has no index " +
        s"entries for them and every request to them skips auth and role checks: ${unregistered.mkString(", ")} ") {
        unregistered should equal(List.empty[String])
      }
    }

    scenario("each version registers more than a token handful") {
      // A floor, not a count: this fails when a version stops registering, not when one is added.
      versions.foreach { case (name, size) =>
        withClue(s"$name registered only $size resource docs: ") {
          size should be >= 3
        }
      }
    }
  }
}
