package code.api.UKOpenBanking.v4_0_1

import code.api.util.APIUtil.{ResourceDoc, UserOrApplication, buildOperationId}
import code.api.util.Consent
import code.api.util.ErrorMessages.{ConsentDoesNotMatchConsumer, ConsentDoesNotMatchUser}
import com.openbankproject.commons.util.ApiVersion
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

  feature("Consent.checkUKConsentAccess") {

    scenario("the PSU a consent is bound to may use it", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, Some(psu), Some(tpp)) should equal(None)
    }

    scenario("a different PSU may not use a bound consent", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, Some(otherPsu), Some(tpp)) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    scenario("the PSU check wins over the Consumer once a consent is bound", UKOpenBankingV401ConsentAccess) {
      // Even the Consumer that lodged it cannot act as another PSU.
      Consent.checkUKConsentAccess(psu, tpp, Some(otherPsu), Some(tpp)) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    scenario("an unbound consent may be used by the Consumer that lodged it", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, Some(psu), Some(tpp)) should equal(None)
    }

    scenario("an unbound consent may not be used by a second TPP", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, Some(psu), Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    // The client-credentials cases: no PSU in the session at all.
    scenario("a PSU-less call may use an unbound consent it lodged", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess("", tpp, None, Some(tpp)) should equal(None)
    }

    scenario("a PSU-less call may use a bound consent it lodged", UKOpenBankingV401ConsentAccess) {
      // The one combination whose outcome changes, and the reason: this is how the standard has the
      // AISP poll and revoke its own consent after the PSU has authorised it. It used to be refused.
      Consent.checkUKConsentAccess(psu, tpp, None, Some(tpp)) should equal(None)
    }

    scenario("a PSU-less call from a second TPP is still refused", UKOpenBankingV401ConsentAccess) {
      // Dropping the user check does not open the consent to everyone: the Consumer still decides.
      Consent.checkUKConsentAccess(psu, tpp, None, Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkUKConsentAccess("", tpp, None, Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("a PSU-less call with no Consumer at all is refused", UKOpenBankingV401ConsentAccess) {
      Consent.checkUKConsentAccess(psu, tpp, None, None) should equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("blank ids count as absent, not as a value to match", UKOpenBankingV401ConsentAccess) {
      // A consent lodged before consumer binding existed stores no consumer id; nothing identifies a
      // wrong caller, so it cannot be refused on that basis.
      Consent.checkUKConsentAccess("", "", None, Some(tpp)) should equal(None)
      Consent.checkUKConsentAccess(null, null, None, None) should equal(None)
      // A blank caller user id is not a PSU either -- it must not accidentally match a blank binding.
      Consent.checkUKConsentAccess(psu, tpp, Some("  "), Some(tpp)) should equal(None)
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
