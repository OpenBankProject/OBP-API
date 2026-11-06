package code.context

import code.setup.ServerSetup
import com.openbankproject.commons.model.BasicUserAuthContext

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Characterization of the consent-auth-context provider, written before the implementation moves
 * to Doobie.
 *
 * No test in the suite exercises this table directly - only the SCA/consent flows in
 * ConsentUtil and the Berlin Group AIS endpoints call it, and none of those pin the storage
 * contract on their own. Driven through ConsentAuthContextProvider.vend, the same seam the real
 * callers use, so this keeps testing whichever implementation buildOne returns.
 *
 * The two write paths behave differently and both matter:
 *
 *  - createConsentAuthContext always inserts, with no existence check. Calling it twice with the
 *    same (consentId, key) must produce two rows - that is deliberate, per the "developers are
 *    encouraged to use name space in the key" comment on the Mapper provider. It relies on the
 *    unique index including createdAt: two calls in the same millisecond collide and the second
 *    is rejected, which back-to-back calls on the same thread hit often enough that the test
 *    below spaces them out rather than pretend the race does not exist.
 *  - createOrUpdateConsentAuthContexts is find-then-write per key: a fresh key is inserted, an
 *    existing key is overwritten in place, and the result stays one row per key. The Mapper
 *    version has a variable-shadowing bug in the update branch - the inner lambda parameter
 *    `authContext` shadows the outer one, so it saves the found row's own current key/value
 *    back onto itself instead of the incoming ones, making every update a no-op. Nothing tests
 *    this today; this is the test, and it is written against the documented contract
 *    ("creates or replaces"), not against the bug. It fails on the Mapper version for exactly
 *    that reason and is expected to pass once the Doobie provider fixes it.
 */
class ConsentAuthContextProviderTest extends ServerSetup {

  private def provider = ConsentAuthContextProvider.consentAuthContextProvider.vend
  private def await[A](f: scala.concurrent.Future[A]) = Await.result(f, 10.seconds)

  private val consentA = "consent-auth-context-test-A"
  private val consentB = "consent-auth-context-test-B"

  override def beforeEach() = {
    super.beforeEach()
    await(provider.deleteConsentAuthContexts(consentA))
    await(provider.deleteConsentAuthContexts(consentB))
  }

  Feature("consent auth context storage") {

    Scenario("create then read back") {
      val created = await(provider.createConsentAuthContext(consentA, "psuId", "u1"))
      created.isDefined should equal(true)

      val all = provider.getConsentAuthContextsBox(consentA).openOrThrowException("just created")
      all.map(_.key) should equal(List("psuId"))
      all.head.value should equal("u1")
    }

    Scenario("createConsentAuthContext always inserts, even for a repeated key") {
      val first = await(provider.createConsentAuthContext(consentA, "psuId", "u1"))
      first.isDefined should equal(true)
      // The unique index is (consentId, key, createdAt): two writes for the same key in the
      // same millisecond collide. Space them out so this checks "always inserts", not timing.
      Thread.sleep(5)
      val second = await(provider.createConsentAuthContext(consentA, "psuId", "u2"))
      second.isDefined should equal(true)

      val all = provider.getConsentAuthContextsBox(consentA).openOrThrowException("created twice")
      all.count(_.key == "psuId") should equal(2)
      all.map(_.value).toSet should equal(Set("u1", "u2"))
    }

    Scenario("createOrUpdateConsentAuthContexts inserts a fresh key") {
      val result = provider.createOrUpdateConsentAuthContexts(
        consentA, List(BasicUserAuthContext("psuId", "u1")))
      result.openOrThrowException("created").map(_.value) should equal(List("u1"))
    }

    Scenario("createOrUpdateConsentAuthContexts overwrites an existing key rather than adding a row") {
      provider.createOrUpdateConsentAuthContexts(consentA, List(BasicUserAuthContext("psuId", "u1")))
      provider.createOrUpdateConsentAuthContexts(consentA, List(BasicUserAuthContext("psuId", "u2")))

      val all = provider.getConsentAuthContextsBox(consentA).openOrThrowException("updated")
      all.count(_.key == "psuId") should equal(1)
      all.head.value should equal("u2")
    }

    Scenario("deleteConsentAuthContexts is scoped to one consent id") {
      await(provider.createConsentAuthContext(consentA, "psuId", "u1"))
      await(provider.createConsentAuthContext(consentB, "psuId", "u1"))

      await(provider.deleteConsentAuthContexts(consentA))

      provider.getConsentAuthContextsBox(consentA).openOrThrowException("checked").isEmpty should equal(true)
      provider.getConsentAuthContextsBox(consentB).openOrThrowException("checked").isEmpty should equal(false)
    }

    Scenario("deleteConsentAuthContextById removes just that row") {
      val created = await(provider.createConsentAuthContext(consentA, "psuId", "u1"))
        .openOrThrowException("just created")
      await(provider.createConsentAuthContext(consentA, "other", "u2"))

      await(provider.deleteConsentAuthContextById(created.consentAuthContextId))

      val remaining = provider.getConsentAuthContextsBox(consentA).openOrThrowException("checked")
      remaining.map(_.key) should equal(List("other"))
    }
  }
}
