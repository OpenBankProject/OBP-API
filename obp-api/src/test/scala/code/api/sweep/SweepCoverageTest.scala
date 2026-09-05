package code.api.sweep

import code.api.util.APIUtil.ResourceDoc
import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

/**
 * The sweep is complete, and stays complete.
 *
 * A sweep driven off a registry has one failure mode that matters and that the sweep itself
 * cannot see: an endpoint quietly leaving the swept set. Whether that happens because someone
 * adds a skip, because a doc grows a tag, or because a filter is written slightly wrong, the
 * result looks identical from outside — the sweep passes, and it passes over less than it did
 * yesterday. This is the same shape as `run_tests_parallel.sh`'s "fewer than 2000 tests ran"
 * floor and the contract suite's MIN_ATTEMPTED, moved inside the suite: prove the denominator
 * before believing the numerator.
 *
 * The identity is exact, not a threshold:
 *
 *     |catalog| == |swept| + |skipped|
 *
 * and every member of `skipped` carries one of the three reasons enumerated in EndpointCatalog.
 * There is no fourth bucket. A sweep that wants to exclude something has to add a SkipReason
 * with a written justification, in the file this test reads — which is the point.
 */
class SweepCoverageTest extends ServerSetupWithTestData {

  object SweepCoverage extends Tag("SweepCoverage")

  private lazy val catalog: List[ResourceDoc] = EndpointCatalog.all

  feature("The endpoint sweep covers every reachable endpoint, or says why not") {

    scenario("the catalog is non-empty and deduplicated by (standard, url, verb)", SweepCoverage) {
      Given("the aggregated resource docs across every standard the dispatcher serves")
      // A sweep over an empty catalog passes every assertion it makes. The floor is deliberately
      // well below the ~870 observed on this branch: its job is to catch a catalog that failed to
      // initialise, not to freeze a number that legitimately grows with every new endpoint.
      withClue(s"catalog holds ${catalog.size} endpoints -- far below the expected several " +
               s"hundred, which means the registry did not initialise and every sweep in this " +
               s"package is asserting nothing. ") {
        catalog.size should be > 400
      }

      Then("no operation id appears twice, and every same-route group is genuinely distinct operations")
      // Two different checks, because the catalog now spans every standard the dispatcher serves
      // (ResourceDocRegistry.allStaticResourceDocs), not just OBP:
      //
      // 1. operationId has no duplicates. This is the real identity of a resource doc -- it is
      //    what every consumer (metrics, api-collection-endpoint, this catalog's own callers)
      //    keys off -- and ResourceDocRegistry.allStaticResourceDocs already guarantees it by
      //    construction (reverse/distinctBy/reverse). Asserting it here is a sanity check on
      //    that construction, not a new requirement.
      //
      // 2. Grouping by (urlPrefix, apiShortVersion, requestUrl, requestVerb) instead -- the naive
      //    check this scenario used to make -- is NOT a real invariant for a multi-standard
      //    catalog: Berlin Group's own spec routes several distinct SCA sub-steps through ONE
      //    URL and verb, disambiguated by request BODY, not by path -- e.g. PUT
      //    .../authorisations/AUTHORISATION_ID legitimately carries three separate operations
      //    (updatePsuAuthentication / selectPsuAuthenticationMethod / transactionAuthorisation)
      //    that this catalog cannot and should not collapse into one. So a same-route group is
      //    fine as long as every doc in it is a genuinely distinct operation (checked via 1
      //    above) -- what would still be wrong is the SAME operation id turning up under two
      //    different ResourceDoc objects, which duplicatesByOperationId below catches directly.
      val duplicatesByOperationId = catalog
        .groupBy(_.operationId)
        .collect { case (opId, docs) if docs.size > 1 => s"$opId -> ${docs.size} entries" }
      withClue(s"allStaticResourceDocs is supposed to be unique per operation id; these survived " +
               s"twice:\n${duplicatesByOperationId.mkString("\n")}\n") {
        duplicatesByOperationId shouldBe empty
      }
    }

    scenario("every endpoint is either swept or skipped for a stated reason", SweepCoverage) {
      Given(s"the ${catalog.size} endpoints in the catalog")
      val (skipped, swept) = catalog.partition(EndpointCatalog.skipReason(_).isDefined)

      Then("the two sets account for the catalog exactly, with nothing in between")
      withClue(s"swept=${swept.size} skipped=${skipped.size} catalog=${catalog.size} -- these " +
               s"must add up, or some endpoint is in a third bucket nobody is looking at. ") {
        swept.size + skipped.size shouldBe catalog.size
      }

      And("every skip names one of the enumerated reasons")
      val unexplained = skipped.filter(EndpointCatalog.skipReason(_).isEmpty)
      unexplained shouldBe empty

      And("the swept set is the large majority -- a skip list that has grown to swallow the " +
          "catalog is a sweep that has stopped working")
      withClue(s"only ${swept.size} of ${catalog.size} endpoints are swept; skips by reason: " +
               s"${skipped.groupBy(EndpointCatalog.skipReason(_).get.why).view.mapValues(_.size).toMap}. ") {
        swept.size should be > (catalog.size / 2)
      }
    }

    scenario("the auth classification is total -- every swept endpoint is public or protected", SweepCoverage) {
      val swept = catalog.filter(EndpointCatalog.skipReason(_).isEmpty)
      val protectedCount = swept.count(EndpointCatalog.needsAuthentication)
      val publicCount    = swept.size - protectedCount

      Then("the two classes partition the swept set")
      protectedCount + publicCount shouldBe swept.size

      And("both classes are non-empty -- a classifier that answers the same for everything is " +
          "not classifying")
      withClue(s"protected=$protectedCount public=$publicCount. If either is zero the predicate " +
               s"has stopped discriminating and both AuthSweepTest branches are vacuous. ") {
        protectedCount should be > 0
        publicCount should be > 0
      }
    }

    scenario("the failure sweep covers the same set the auth sweep does", SweepCoverage) {
      // Read each sweep's OWN scope rather than re-deriving a copy here: today both are
      // `EndpointCatalog.all.filter(EndpointCatalog.skipReason(_).isEmpty)`, so two independently
      // hand-typed copies of that expression would be equal by construction and this scenario
      // would pass even after a real future divergence -- one sweep grows a filter of its own,
      // the endpoints that fall between the two are covered by neither, and nothing here would
      // notice. Reading AuthSweepTest.scope / FailureSweepTest.scope means there is exactly one
      // definition of each sweep's coverage, so a change to either is automatically reflected
      // on both sides of this comparison.
      val authScope    = AuthSweepTest.scope.map(_.operationId).toSet
      val failureScope = FailureSweepTest.scope.map(_.operationId).toSet

      val onlyAuth    = authScope -- failureScope
      val onlyFailure = failureScope -- authScope
      withClue(s"endpoints swept for auth but not for crashes: ${onlyAuth.take(10).mkString(", ")}; " +
               s"the reverse: ${onlyFailure.take(10).mkString(", ")}. ") {
        onlyAuth shouldBe empty
        onlyFailure shouldBe empty
      }
    }

    scenario("enough endpoints carry an example body for the failure sweep to exercise writers",
             SweepCoverage) {
      // FailureSweepTest sends exampleRequestBody to every non-GET endpoint. If almost none of
      // them had one, the sweep would be a GET-only crash test wearing a broader name -- it
      // would pass while every write path went unexercised.
      val writers = catalog
        .filter(EndpointCatalog.skipReason(_).isEmpty)
        .filterNot(d => d.requestVerb.toUpperCase == "GET" || d.requestVerb.toUpperCase == "DELETE")
      val withBody = writers.count(_.exampleRequestBody != null)

      withClue(s"$withBody of ${writers.size} write endpoints carry an exampleRequestBody. " +
               s"Below half and the failure sweep is mostly not sending bodies at all. ") {
        writers.size should be > 100
        withBody should be > (writers.size / 2)
      }
    }

    scenario("role-gated endpoints declare the errors their gate produces", SweepCoverage) {
      val roleGated = catalog
        .filter(EndpointCatalog.isRoleGated)
        .filter(EndpointCatalog.roleSkipReason(_).isEmpty)

      Then("each one is also classified as needing authentication")
      // The middleware derives auth from `errorResponseBodies contains AuthenticatedUserIsRequired
      // OR roles.nonEmpty`, so a role-gated endpoint is authenticated by construction. If this
      // ever fails, the predicate and the middleware have diverged.
      val notAuthenticated = roleGated.filterNot(EndpointCatalog.needsAuthentication)
      withClue(s"role-gated but not classified as needing authentication: " +
               s"${notAuthenticated.map(_.operationId).mkString(", ")}. ") {
        notAuthenticated shouldBe empty
      }

      And("there are enough of them for the 403 sweep to be meaningful")
      withClue(s"only ${roleGated.size} role-gated endpoints are in scope for the 403 assertion. ") {
        roleGated.size should be > 100
      }
    }
  }
}
