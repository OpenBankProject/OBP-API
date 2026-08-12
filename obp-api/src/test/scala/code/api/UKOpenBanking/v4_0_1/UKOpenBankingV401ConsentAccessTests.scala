package code.api.UKOpenBanking.v4_0_1

import code.api.util.APIUtil.{ResourceDoc, UserOrApplication, buildOperationId}
import code.api.util.{CallContext, Consent}
import code.api.util.ErrorMessages.{ConsentDoesNotMatchConsumer, ConsentDoesNotMatchUser}
import code.model.UserX
import code.model.dataAccess.ResourceUser
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Empty, Failure, Full}
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

// Who may read or revoke a UK account-access-consent.
//
// The standard names one caller and it is not the PSU: GET and DELETE on account-access-consents
// carry Grant Type "Client Credentials" in both v3.1 and v4.0.1, so a standard caller has no PSU in
// the session and the Consumer the consent was lodged under decides everything. The rule used to be
// four copies of the same inline pair of checks across v3.1/v4.0.1 GET/DELETE, keyed on the caller's
// user id -- which silently assumed a user is always present.
//
// Those PSU-less combinations are covered here explicitly, including the one whose outcome changes:
// an authorised consent reached with no PSU used to be refused with ConsentDoesNotMatchUser and is
// now decided by the Consumer, which is what the standard asks for.
//
// The user check still applies to a caller that does present a PSU -- OBP allows credentials the
// standard does not describe here -- so those combinations are covered too, unchanged.
//
// The rule function is unit-tested rather than driven over HTTP because the test framework signs
// with OAuth1, which always attaches a user -- there is no way to make a genuinely PSU-less request
// from here. The ResourceDoc auth mode is pinned separately, since that is what lets such a request
// reach the handler at all.
class UKOpenBankingV401ConsentAccessTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401ConsentAccess extends Tag("UKOpenBankingV401ConsentAccess")

  private val psu = "psu-user-id"
  private val otherPsu = "someone-else-user-id"
  private val tpp = "lodging-consumer-id"
  private val otherTpp = "second-consumer-id"

  // A client_credentials token carries the caller's own client id in `sub`, and OAuth2's
  // getOrCreateResourceUser turns `sub` into idGivenByProvider -- so the token resolves cc.user to an
  // auto-vivified pseudo-user keyed on the consumer's client key rather than leaving it Empty. The
  // OAuth1-signed harness cannot mint that token, so build the same shape directly.
  private lazy val pseudoUserOfConsumer: ResourceUser =
    getOrCreateUser(idGivenByProvider = testConsumer.key.get, name = testConsumer.key.get)

  // What applyUKRules puts on cc.user for a request authenticated by the consent itself: a user
  // minted from the consent JWT's `sub`, which is a random UUID per consent. Nothing about it says
  // "not a person" -- that is precisely why genuinePsu cannot filter it and consenter is needed.
  private lazy val shadowUserOfConsent: ResourceUser =
    getOrCreateUser(idGivenByProvider = s"uk-consent-shadow-${randomString(16)}", name = "")

  private def getOrCreateUser(idGivenByProvider: String, name: String): ResourceUser =
    UserX.findByProviderId(provider = defaultProvider, idGivenByProvider = idGivenByProvider)
      .map(_.asInstanceOf[ResourceUser])
      .getOrElse {
        UserX.createResourceUser(
          provider = defaultProvider,
          providerId = Some(idGivenByProvider),
          createdByConsentId = None,
          name = Some(name),
          email = Some(s"${randomString(10)}@example.com"),
          userId = None,
          company = None
        ).openOrThrowException(s"test user creation failed for $idGivenByProvider")
      }

  feature("Consent.checkUKConsentAccess") {

    // The ASPSP's own approval screen arrives under its own Consumer, never the TPP's, so the
    // lodging-Consumer comparison refuses exactly the caller whose job is to show the PSU what they
    // are being asked to grant -- and the screen renders with no permissions, status or expiry.
    scenario("a declared SCA front end may read a consent nobody has claimed yet", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = true) should equal(None)
      Consent.checkUKConsentAccess("", tpp, None, Some(otherTpp), callerIsScaFrontEnd = true) should equal(None)
    }

    scenario("but not once a PSU has claimed it", UKOpenBankingV401ConsentAccess) {
      // The window the approval screen exists for has closed; from here the PSU half governs, and a
      // declared front end gets no further than anyone else would.
      Consent.checkUKConsentAccess(psu, tpp, Some(otherPsu), Some(otherTpp), callerIsScaFrontEnd = true) should
        equal(Some(ConsentDoesNotMatchUser))
      Consent.checkUKConsentAccess(psu, tpp, None, Some(otherTpp), callerIsScaFrontEnd = true) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("and an undeclared caller is still refused on an unclaimed consent", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("the PSU a consent is bound to may use it", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, Some(psu), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    scenario("a different PSU may not use a bound consent", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, Some(otherPsu), Some(tpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    scenario("the PSU check wins over the Consumer once a consent is bound", UKOpenBankingV401ConsentAccess) {
      // Even the Consumer that lodged it cannot act as another PSU.
      Consent.checkUKConsentAccess(psu, tpp, Some(otherPsu), Some(tpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    scenario("an unbound consent may be used by the Consumer that lodged it", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, Some(psu), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    scenario("an unbound consent may not be used by a second TPP", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    // The client-credentials cases: no PSU in the session at all.
    scenario("a PSU-less call may use an unbound consent it lodged", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, None, Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    scenario("a PSU-less call may use a bound consent it lodged", UKOpenBankingV401ConsentAccess) {
      // The one combination whose outcome changes, and the reason: this is how the standard has the
      // AISP poll and revoke its own consent after the PSU has authorised it. It used to be refused.
      Consent.checkUKConsentAccess(psu, tpp, None, Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    scenario("a PSU-less call from a second TPP is still refused", UKOpenBankingV401ConsentAccess) {
      // Dropping the user check does not open the consent to everyone: the Consumer still decides.
      Consent.checkUKConsentAccess(psu, tpp, None, Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkUKConsentAccess("", tpp, None, Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("a PSU-less call with no Consumer at all is refused", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, None, None, callerIsScaFrontEnd = false) should equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("blank ids count as absent, not as a value to match", UKOpenBankingV401ConsentAccess) {
      // A blank caller user id is not a PSU -- it must not accidentally match a blank binding.
      Consent.checkUKConsentAccess(psu, tpp, Some("  "), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    // The two assertions that used to sit in the scenario above have inverted. They read
    //   checkUKConsentAccess("", "", None, Some(tpp), false) should equal(None)
    //   checkUKConsentAccess(null, null, None, None, false)  should equal(None)
    // on the reasoning that a consent lodged before consumer binding existed "cannot be refused on
    // that basis" because nothing identifies a wrong caller. The other half of that is that nothing
    // identifies a RIGHT one either, and the profile scopes these endpoints to "an
    // account-access-consent resource that they have created" -- a row naming no creator matches no
    // caller rather than every caller. 4 of 753 UK consents on a long-lived instance record no
    // consumer, and until now any authenticated caller could read and revoke them.
    scenario("a consent that records no lodging TPP belongs to nobody", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", "", None, Some(tpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkUKConsentAccess(null, null, None, None, callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    // Matching the PSU is necessary, not sufficient: the rule used to stop as soon as the two PSU
    // ids agreed, so a second TPP holding a session for the same person reached a first TPP's
    // consent. The UK case for the Consumer comparison is per-endpoint rather than blanket -- the
    // Endpoints table marks GET and DELETE Client Credentials, so no PSU is party to them at all,
    // and a PSU session here is an OBP extension that cannot be the thing that waives it.
    scenario("a second TPP holding a session for the consent's own PSU is still refused", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }
  }

  // The rule above is only ever as good as the identity handed to it, and that is where this family
  // was actually failing: every combination with `None` for the caller is tested there, and no
  // caller could produce one. cc.user is never Empty on a request that reaches these handlers -- a
  // client-credentials token auto-vivifies a pseudo-user, and consent-header authentication swaps in
  // the consent's shadow user -- so the rule was being asked about the wrong person and an
  // authorised consent answered 403 ConsentDoesNotMatchUser to both callers the standard describes.
  //
  // Consent.actingPsu is the missing step. These pin the four shapes a caller can arrive in, and the
  // last scenario pins the composition, which is the part that regressed rather than either half.
  feature("Consent.actingPsu") {

    scenario("a session with no user at all is acting as nobody", UKOpenBankingV401ConsentAccess) {
      Consent.actingPsu(CallContext(user = Empty, consumer = Full(testConsumer))) should equal(None)
    }

    scenario("a client-credentials caller is acting only as itself", UKOpenBankingV401ConsentAccess) {
      // The AISP call the standard describes. None is the right answer, not a missing one: it is
      // what lets checkUKConsentAccess fall through to the Consumer rule.
      Consent.actingPsu(
        CallContext(user = Full(pseudoUserOfConsumer), consumer = Full(testConsumer))) should equal(None)
    }

    scenario("a real person authenticated in the session is the PSU", UKOpenBankingV401ConsentAccess) {
      Consent.actingPsu(CallContext(user = Full(resourceUser1), consumer = Full(testConsumer)))
        .map(_.userId) should equal(Some(resourceUser1.userId))
    }

    scenario("under consent-header authentication the PSU is the one the swap set aside", UKOpenBankingV401ConsentAccess) {
      // applyUKRules leaves the consent's shadow user on `user` and the real PSU on `consenter`. A
      // shadow user's idGivenByProvider is a random UUID rather than the consumer key, so genuinePsu
      // alone waves it through -- this is the case that needs consenter.
      val consentHeaderContext = CallContext(
        user = Full(shadowUserOfConsent), consenter = Full(resourceUser1), consumer = Full(testConsumer))

      Consent.actingPsu(consentHeaderContext).map(_.userId) should equal(Some(resourceUser1.userId))
      Consent.genuinePsu(consentHeaderContext).map(_.userId) should equal(Some(shadowUserOfConsent.userId))
    }

    scenario("the consenter outranks the session principal whenever both are present", UKOpenBankingV401ConsentAccess) {
      Consent.actingPsu(CallContext(
        user = Full(resourceUser2), consenter = Full(resourceUser1), consumer = Full(testConsumer)))
        .map(_.userId) should equal(Some(resourceUser1.userId))
    }

    scenario("the composition the endpoints perform lets both standard callers through", UKOpenBankingV401ConsentAccess) {
      // A consent bound to resourceUser1 and lodged by testConsumer, reached the two ways a TPP can
      // reach it. Both used to be refused with ConsentDoesNotMatchUser.
      val bound = resourceUser1.userId
      val lodger = testConsumer.consumerId.get

      val viaClientCredentials =
        CallContext(user = Full(pseudoUserOfConsumer), consumer = Full(testConsumer))
      Consent.checkUKConsentAccess(
        bound, lodger, Consent.actingPsu(viaClientCredentials).map(_.userId), Some(lodger), callerIsScaFrontEnd = false) should equal(None)

      val viaConsentHeader = CallContext(
        user = Full(shadowUserOfConsent), consenter = Full(resourceUser1), consumer = Full(testConsumer))
      Consent.checkUKConsentAccess(
        bound, lodger, Consent.actingPsu(viaConsentHeader).map(_.userId), Some(lodger), callerIsScaFrontEnd = false) should equal(None)

      // And it still narrows: a session acting as a different PSU cannot reach the consent, which is
      // the whole reason the user half is kept.
      val viaOtherPsu = CallContext(user = Full(resourceUser2), consumer = Full(testConsumer))
      Consent.checkUKConsentAccess(
        bound, lodger, Consent.actingPsu(viaOtherPsu).map(_.userId), Some(lodger), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchUser))
    }
  }

  // A consent of another standard reaching a UK endpoint is a refusal, not a server fault.
  // checkUKConsent used to throw when there was no Authorization header, which is exactly the
  // shape such a request has: the dispatcher routes the consent into its own standard's branch,
  // that branch authenticates the request, and ukConsentId is never set. The uncaught throw came
  // back as OBP-50000 Unknown Error at 500.
  feature("Consent.checkUKConsent refuses rather than throws when no UK consent is in play") {

    scenario("a request with neither a UK consent nor an Authorization header is refused", UKOpenBankingV401ConsentAccess) {
      val result = Consent.checkUKConsent(resourceUser1, Some(CallContext()))
      result match {
        case Failure(msg, _, _) => msg should include("OBP-35036")
        case other => fail(s"expected a Failure naming the standard mismatch, got $other")
      }
    }

    scenario("a request the consent header already settled is still waved through", UKOpenBankingV401ConsentAccess) {
      // applyUKRules sets ukConsentId once it has run every gate, and this short-circuit is what
      // keeps consent-header authentication working -- the refusal above must not reach it.
      Consent.checkUKConsent(resourceUser1, Some(CallContext(ukConsentId = Some("any-consent-id")))) should
        equal(Full(true))
    }
  }

  feature("consent-by-id ResourceDocs accept a client-credentials caller") {
    // Without this the docs default to UserOnly, which sends ResourceDocMiddleware down
    // anonymousAccess and 401s any request carrying no user -- so the rule above would never be
    // reached. Pinned because nothing else would notice a revert: these endpoints keep working for
    // as long as OAuth2 token parsing auto-vivifies a user for a client-credentials token.
    for (name <- List("getAccountAccessConsentsConsentId", "deleteAccountAccessConsentsConsentId")) {
      scenario(s"v4.0.1 $name declares UserOrApplication", UKOpenBankingV401ConsentAccess) {
        val docs = ResourceDoc.getResourceDocs(List(buildOperationId(ApiVersion.ukOpenBankingV401, name)))
        docs should not be empty
        docs.foreach(_.authMode should equal(UserOrApplication))
      }
      scenario(s"v3.1 $name declares UserOrApplication", UKOpenBankingV401ConsentAccess) {
        val docs = ResourceDoc.getResourceDocs(List(buildOperationId(ApiVersion.ukOpenBankingV31, name)))
        docs should not be empty
        docs.foreach(_.authMode should equal(UserOrApplication))
      }
    }
  }
}
