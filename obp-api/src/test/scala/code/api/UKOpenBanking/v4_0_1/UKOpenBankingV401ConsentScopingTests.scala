package code.api.UKOpenBanking.v4_0_1

import code.api.Constant
import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.{CallContext, Consent}
import code.consent.Consents
import code.model.UserExtended
import code.views.Views
import code.views.system.AccountAccess
import com.openbankproject.commons.model.{BankIdAccountId, ViewId}
import net.liftweb.common.Full
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * What a UK consent's declared Permissions are actually worth, once more than one consent exists.
 *
 * UK is the only standard that materialises consent permissions onto the *real* PSU: Berlin Group
 * and OBP-native mint a fresh shadow user per consent (createXxxConsentJWT sets sub to a random
 * UUID, and applyConsentRules resolves the principal with getOrCreateUser(consent.sub, ...)), so
 * their AccountAccess rows are isolated by construction. grantUKConsentAccountAccess instead grants
 * to the authorising PSU, so every UK consent for that PSU lands on the same (user, account, view)
 * rows -- and those rows carry no consent identity at all.
 *
 * That makes three properties worth pinning down, none of which any existing suite covers:
 *  - narrowing: re-authorising with fewer permissions -- or fewer accounts -- must actually drop
 *    what was left out;
 *  - isolation: one TPP's authorisation must not rewrite another TPP's access to the same account;
 *  - and the one place narrowing still cannot hold: two consents live at once under the SAME TPP,
 *    which share a single set of rows. That is pinned as a characterisation test at the bottom of
 *    this file rather than left unstated -- it is the remaining half of the same gap, and it only
 *    closes when AccountAccess carries a consent_id.
 *
 * Asserted at the UserExtended.hasAccountAccess layer because that is exactly what
 * APIUtil.checkViewAccessAndReturnView -- and therefore every UK data endpoint -- consults. The
 * full HTTP path can't be driven here: these OAuth1-signed requests carry no Bearer JWT with a
 * consent_id claim, so the data endpoints would stop at 403 ConsentIdClaimMissing.
 */
class UKOpenBankingV401ConsentScopingTests extends UKOpenBankingV401ServerSetup {

  object UKConsentScoping extends Tag("UKConsentScoping")

  private val acc = testAccountId1.value
  private val otherAcc = testAccountId0.value
  private val bankIdAccountId = BankIdAccountId(testBankId1, testAccountId1)
  private val otherBankIdAccountId = BankIdAccountId(testBankId1, testAccountId0)

  private val ReadAccountsBasic = Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID
  private val ReadBalances = Constant.SYSTEM_READ_BALANCES_VIEW_ID

  private def systemView(viewId: String) =
    Views.views.vend.getOrCreateSystemView(viewId).openOrThrowException(s"could not create system view $viewId")

  /**
   * Create a UK consent held by `consumerId` and run the authorise-time grant on it, i.e. the same
   * call the POST /consents/CONSENT_ID/authorise endpoint makes once SCA has passed.
   */
  private def authoriseConsentFor(consumerId: String,
                                  permissions: List[String],
                                  accountIds: List[String] = List(acc)): String = {
    val consentId = Consents.consentProvider.vend.saveUKConsent(
      user = Some(resourceUser1),
      bankId = None,
      accountIds = None,
      consumerId = Some(consumerId),
      permissions = permissions,
      expirationDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      transactionFromDateTime = Some(DateWithDayFormat.parse("2020-01-01")),
      transactionToDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      apiStandard = Some("UKOpenBanking"),
      apiVersion = Some("4.0.1")
    ).openOrThrowException("test consent creation failed").consentId

    // saveUKConsent hands back the ConsentTrait; grantUKConsentAccountAccess wants the MappedConsent.
    val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
      .openOrThrowException(s"consent $consentId not found")

    // The seven UK permission views are not seeded in the test DB (Boot only creates
    // owner/auditor/accountant/... unless additional_system_views is set, and test.default.props
    // does not set it), so they have to exist before the grant can bind them.
    permissions.foreach(systemView)

    Await.result(
      Consent.grantUKConsentAccountAccess(resourceUser1, testBankId1, accountIds, consent, None),
      10.seconds)
    consentId
  }

  /** Re-run the authorise-time grant on a consent that already exists and is still live -- the PSU
   *  re-authenticating against a consent the TPP never revoked. */
  private def reAuthorise(consentId: String, accountIds: List[String]): Unit = {
    val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
      .openOrThrowException(s"consent $consentId not found")
    Await.result(
      Consent.grantUKConsentAccountAccess(resourceUser1, testBankId1, accountIds, consent, None),
      10.seconds)
  }

  /** Access as it is evaluated for a request arriving from `consumer` -- the consumer is what
   *  User.hasAccountAccess keys its consumer-specific lookup on before falling back to ALL_CONSUMERS. */
  private def canRead(viewId: String,
                      consumer: code.model.Consumer,
                      account: BankIdAccountId = bankIdAccountId): Boolean =
    UserExtended(resourceUser1).hasAccountAccess(
      systemView(viewId),
      account,
      Some(CallContext(consumer = Full(consumer))))

  feature("A UK consent is authoritative for the permissions it declares") {
    scenario("re-authorising with fewer permissions drops the ones left out", UKConsentScoping) {
      authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic, ReadBalances))
      canRead(ReadAccountsBasic, testConsumer) should equal(true)
      canRead(ReadBalances, testConsumer) should equal(true)

      // Same TPP, narrower consent: ReadBalances was not asked for this time, so it must go.
      authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic))
      canRead(ReadAccountsBasic, testConsumer) should equal(true)
      canRead(ReadBalances, testConsumer) should equal(false)
    }
  }

  feature("A UK consent is authoritative for the accounts it names") {
    scenario("re-authorising with fewer accounts drops the accounts left out", UKConsentScoping) {
      authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic, ReadBalances),
        accountIds = List(acc, otherAcc))
      canRead(ReadAccountsBasic, testConsumer, otherBankIdAccountId) should equal(true)
      canRead(ReadBalances, testConsumer, otherBankIdAccountId) should equal(true)

      // Same TPP, same permissions, but the PSU dropped otherAcc from the selection this time.
      // The account the consent no longer names must lose every UK permission view with it --
      // otherwise the consent reads accounts it never declared.
      authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic, ReadBalances),
        accountIds = List(acc))
      canRead(ReadAccountsBasic, testConsumer) should equal(true)
      canRead(ReadAccountsBasic, testConsumer, otherBankIdAccountId) should equal(false)
      canRead(ReadBalances, testConsumer, otherBankIdAccountId) should equal(false)
    }
  }

  /**
   * Characterisation test: this pins behaviour that is WRONG but currently accepted, so that the
   * limitation is visible in the suite rather than only in a status file. When AccountAccess grows
   * a consent_id column the assertions below become false and this scenario fails -- that failure
   * is the signal to flip them to the values the comments name, not to relax them.
   */
  feature("KNOWN LIMITATION: two live consents held by the same TPP share one set of rows") {
    scenario("re-authorising the wider consent widens a narrower live one", UKConsentScoping) {
      val wide = authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic),
        accountIds = List(acc, otherAcc))
      val narrow = authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic),
        accountIds = List(acc))

      // The fix this suite pins: authorising `narrow` drops the account it does not name.
      canRead(ReadAccountsBasic, testConsumer, otherBankIdAccountId) should equal(false)

      // But `wide` was never revoked, and the PSU re-authenticating against it re-grants otherAcc.
      // Both consents are live and both resolve to the same (user, account, view, consumer) rows,
      // so there is no answer that is right for both: whichever was authorised last wins.
      reAuthorise(wide, List(acc, otherAcc))

      // Correct for `wide`. WRONG for `narrow`, which still names only `acc` -- with a consent_id
      // column this stays false when the request presents `narrow`. Asserted true because that is
      // what the code does today, and pretending otherwise would hide the hole.
      canRead(ReadAccountsBasic, testConsumer, otherBankIdAccountId) should equal(true)
    }
  }

  feature("One TPP's UK consent does not rewrite another TPP's access") {
    scenario("a second consumer authorising a narrower consent leaves the first consumer's access intact", UKConsentScoping) {
      authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic, ReadBalances))
      authoriseConsentFor(testConsumer2.consumerId.get, List(ReadAccountsBasic))

      // The second TPP never asked for balances, so it must not have them.
      canRead(ReadBalances, testConsumer2) should equal(false)

      // ...and the first TPP's consent is none of the second TPP's business: authorising a consent
      // must not narrow access that was granted to somebody else.
      canRead(ReadBalances, testConsumer) should equal(true)
    }
  }

  feature("A UK consent grant leaves account-ownership access alone") {
    scenario("the owner view survives, still granted to every consumer", UKConsentScoping) {
      authoriseConsentFor(testConsumer.consumerId.get, List(ReadAccountsBasic))

      // owner comes from holding the account, not from any consent, so it stays ALL_CONSUMERS and
      // must never be caught by a consent's revoke pass.
      AccountAccess.findByUniqueIndex(
        testBankId1,
        testAccountId1,
        ViewId(Constant.SYSTEM_OWNER_VIEW_ID),
        resourceUser1.userPrimaryKey,
        Constant.ALL_CONSUMERS
      ).isDefined should equal(true)
    }
  }
}
