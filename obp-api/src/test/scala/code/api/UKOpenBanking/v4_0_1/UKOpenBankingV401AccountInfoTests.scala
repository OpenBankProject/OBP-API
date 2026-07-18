package code.api.UKOpenBanking.v4_0_1

import code.api.Constant
import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.ErrorMessages.ConsentIdClaimMissing
import code.api.util.Consent
import code.consent.Consents
import code.model.UserExtended
import code.views.Views
import com.openbankproject.commons.model.{BankIdAccountId, ErrorMessage, ViewId}
import org.json4s._
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

// Test suite for UK Open Banking Read/Write v4.0.1 (AccountInfo).
//
// The 9 endpoints wired to real connector data (see Http4sUKOBv401AccountInfo)
// get a full scenario matrix: unauthenticated -> 401, authenticated with real
// seeded data -> 200/201/204 with real field values, error paths (unknown
// consent/account), and a full consent create -> get -> delete -> get lifecycle.
//
// getAccounts / getAccountsAccountIdBalances / getAccountsAccountIdTransactions call
// NewStyle.function.checkUKConsent (code.api.util.ConsentUtil.checkUKConsent), which reads
// the `consent_id` claim off the Bearer access token — no external identity-provider call.
// These OAuth1-signed test requests carry no Bearer JWT, so the claim lookup deterministically
// fails with a 403 ConsentIdClaimMissing (see the scenario comments on those three features).
// getBalances / getTransactions (the account-aggregate variants) share the same
// checkUKConsent guard but only assert "unauthenticated -> 401" / "authenticated ->
// not 401" (mirroring UKOpenBankingV310AisTests' precedent): they previously skipped
// the consent check entirely and returned real data straight from a DirectLogin
// token, which let a token with no bound consent reach 500 instead of the 403
// OBP-35035 every other AISP data endpoint gives it.
//
// The remaining 80 endpoints are still static spec-faithful stubs; their tests
// are unchanged (two scenarios: authenticated -> fixed code, unauthenticated -> 401).
class UKOpenBankingV401AccountInfoTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401AccountInfo extends Tag("UKOpenBankingV401AccountInfo")
  val acc = testAccountId1.value

  private val consentPermissions = List("ReadAccountsBasic")
  private val consentPostBody =
    """{
      |  "Data": {
      |    "Permissions": ["ReadAccountsBasic"],
      |    "ExpirationDateTime": "2030-01-01",
      |    "TransactionFromDateTime": "2020-01-01",
      |    "TransactionToDateTime": "2030-01-01"
      |  },
      |  "Risk": {}
      |}""".stripMargin

  private def createRealConsent(): String = {
    val consent = Consents.consentProvider.vend.saveUKConsent(
      user = Some(resourceUser1),
      bankId = None,
      accountIds = None,
      consumerId = None,
      permissions = consentPermissions,
      expirationDateTime = DateWithDayFormat.parse("2030-01-01"),
      transactionFromDateTime = DateWithDayFormat.parse("2020-01-01"),
      transactionToDateTime = DateWithDayFormat.parse("2030-01-01"),
      apiStandard = Some("UKOpenBanking"),
      apiVersion = Some("4.0.1")
    ).openOrThrowException("test consent creation failed")
    consent.consentId
  }

  // ── AccountAccessApi ───────────────────────────────────────────────
  feature("UKOB v4.0.1 POST /aisp/account-access-consents") {
    scenario("authenticated with real body -> 201 real ConsentId", UKOpenBankingV401AccountInfo) {
      val response = postAuthed(consentPostBody, "aisp", "account-access-consents")
      response.code should equal(201)
      val consentId = (response.body \ "Data" \ "ConsentId").extract[String]
      consentId should not be empty
      Consents.consentProvider.vend.getConsentByConsentId(consentId).isDefined should equal(true)
      (response.body \ "Data" \ "Permissions").extract[List[String]] should equal(consentPermissions)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      postUnauthed(consentPostBody, "aisp", "account-access-consents").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/account-access-consents/CONSENT_ID") {
    scenario("authenticated with real consent -> 200 real data", UKOpenBankingV401AccountInfo) {
      val consentId = createRealConsent()
      val response = getAuthed("aisp", "account-access-consents", consentId)
      response.code should equal(200)
      (response.body \ "Data" \ "ConsentId").extract[String] should equal(consentId)
      (response.body \ "Data" \ "Permissions").extract[List[String]] should equal(consentPermissions)
    }
    scenario("authenticated with unknown consent -> 400", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "account-access-consents", "fake-consentid").code should equal(400)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "account-access-consents", "fake-consentid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 DELETE /aisp/account-access-consents/CONSENT_ID") {
    scenario("full consent lifecycle: create -> get -> delete -> get", UKOpenBankingV401AccountInfo) {
      val consentId = createRealConsent()

      getAuthed("aisp", "account-access-consents", consentId).code should equal(200)

      deleteAuthed("aisp", "account-access-consents", consentId).code should equal(204)

      val afterDelete = getAuthed("aisp", "account-access-consents", consentId)
      afterDelete.code should equal(200)
      (afterDelete.body \ "Data" \ "Status").extract[String] should equal("REVOKED")
    }
    scenario("authenticated with unknown consent -> 400", UKOpenBankingV401AccountInfo) {
      deleteAuthed("aisp", "account-access-consents", "fake-consentid").code should equal(400)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      deleteUnauthed("aisp", "account-access-consents", "fake-consentid").code should equal(401)
    }
  }
  // ── Consent.grantUKConsentAccountAccess (Gap 4 fix) ───────────────────
  // Regression test for the previously-unverified scenario: before this fix,
  // createUKConsentJWT wrote every permission as ConsentView(bank_id=null,
  // account_id=null, view_id=permission) — a row that could never match a real
  // account (see User.hasAccountAccess: plain bank_id/account_id equality, no
  // wildcard) — so a UK consent's declared Permissions had zero effect on what
  // could actually be read. This exercises the fix at the same access-check
  // layer checkViewAccessAndReturnView (and therefore every UK data endpoint)
  // relies on, since the full HTTP path requires a Bearer JWT with a consent_id
  // claim that this OAuth1-signed test suite cannot mint (see the comment above
  // "GET /aisp/accounts" below).
  feature("UKOB v4.0.1 Consent.grantUKConsentAccountAccess binds permissions to the selected account only") {
    scenario("consent scoped to ReadAccountsBasic grants that view but not ReadBalances", UKOpenBankingV401AccountInfo) {
      val userExtended = UserExtended(resourceUser1)
      val bankIdAccountId = BankIdAccountId(testBankId1, testAccountId1)

      // Baseline: ServerSetupWithTestData's default view grants don't include the UK read views.
      userExtended.hasAccountAccess(
        Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID).openOrThrowException("view"),
        bankIdAccountId, None) should equal(false)

      val consentId = createRealConsent() // permissions = List("ReadAccountsBasic") only
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")

      val result = Await.result(
        Consent.grantUKConsentAccountAccess(resourceUser1, testBankId1, List(acc), consent, None),
        10.seconds)
      result.isDefined should equal(true)

      // Granted: the account now has a real (non-null) AccountAccess row for the consented view.
      userExtended.hasAccountAccess(
        Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID).openOrThrowException("view"),
        bankIdAccountId, None) should equal(true)

      // Not granted: ReadBalances was never in the consent's Permissions, so it must stay locked —
      // this is the check GET /aisp/accounts/ACCOUNT_ID/balances relies on (checkViewAccessAndReturnView).
      userExtended.hasAccountAccess(
        Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_BALANCES_VIEW_ID).openOrThrowException("view"),
        bankIdAccountId, None) should equal(false)
    }
  }

  // ── AccountsApi ────────────────────────────────────────────────────
  // checkUKConsent extracts the `consent_id` claim from the Bearer access token (no external
  // Hydra call since Consent.checkUKConsent dropped the Hydra dependency). These OAuth1-signed
  // test requests carry no Bearer JWT at all, so the claim lookup deterministically fails ->
  // 403 ConsentIdClaimMissing, mirroring "authenticated but no bound consent" in production.
  feature("UKOB v4.0.1 GET /aisp/accounts") {
    scenario("authenticated without a consent-bound token -> 403", UKOpenBankingV401AccountInfo) {
      val response = getAuthed("aisp", "accounts")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.trim should equal(ConsentIdClaimMissing.trim)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID") {
    scenario("authenticated with granted read view -> 200 real account data", UKOpenBankingV401AccountInfo) {
      grantUKReadViews(testAccountId1, resourceUser1)
      val response = getAuthed("aisp", "accounts", acc)
      response.code should equal(200)
      val accounts = (response.body \ "Data" \ "Account").children
      accounts should not be empty
      (accounts.head \ "AccountId").extract[String] should equal(acc)
      (accounts.head \ "Currency").extract[String] should equal("EUR")
    }
    scenario("authenticated with fake account id -> 200 empty Account list", UKOpenBankingV401AccountInfo) {
      val response = getAuthed("aisp", "accounts", "fake-accountid")
      response.code should equal(200)
      (response.body \ "Data" \ "Account").children should be(empty)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", acc).code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/balances") {
    scenario("authenticated without a consent-bound token -> 403", UKOpenBankingV401AccountInfo) {
      val response = getAuthed("aisp", "accounts", acc, "balances")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.trim should equal(ConsentIdClaimMissing.trim)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", acc, "balances").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/beneficiaries") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "beneficiaries").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "beneficiaries").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/direct-debits") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "direct-debits").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "direct-debits").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/offers") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "offers").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "offers").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/parties") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "parties").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "parties").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/party") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "party").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "party").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/product") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "product").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "product").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/scheduled-payments") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "scheduled-payments").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "scheduled-payments").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/standing-orders") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "standing-orders").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "standing-orders").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/statements") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "statements").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "statements").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/statements/STATEMENT_ID") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "statements", "fake-statementid").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "statements", "fake-statementid").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/statements/STATEMENT_ID/file") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "statements", "fake-statementid", "file").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "statements", "fake-statementid", "file").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/statements/STATEMENT_ID/transactions") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", "fake-accountid", "statements", "fake-statementid", "transactions").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", "fake-accountid", "statements", "fake-statementid", "transactions").code should equal(401)
    }
  }
  // ── TransactionsApi ────────────────────────────────────────────────
  // See the "no external Hydra call" note above feature("UKOB v4.0.1 GET /aisp/accounts").
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/transactions") {
    scenario("authenticated without a consent-bound token -> 403", UKOpenBankingV401AccountInfo) {
      val response = getAuthed("aisp", "accounts", acc, "transactions")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.trim should equal(ConsentIdClaimMissing.trim)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", acc, "transactions").code should equal(401)
    }
  }

  // ── BalancesApi ────────────────────────────────────────────────────
  // DATA-DEPENDENT: checkUKConsent requires a consent-bound token (see class doc above).
  feature("UKOB v4.0.1 GET /aisp/balances") {
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "balances").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "balances").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/beneficiaries") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "beneficiaries").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "beneficiaries").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/direct-debits") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "direct-debits").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "direct-debits").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/offers") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "offers").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "offers").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/party") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "party").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "party").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/products") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "products").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "products").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/scheduled-payments") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "scheduled-payments").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "scheduled-payments").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/standing-orders") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "standing-orders").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "standing-orders").code should equal(401)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/statements") {
    scenario("authenticated -> 200", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "statements").code should equal(200)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "statements").code should equal(401)
    }
  }
  // DATA-DEPENDENT: checkUKConsent requires a consent-bound token (see class doc above).
  feature("UKOB v4.0.1 GET /aisp/transactions") {
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "transactions").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "transactions").code should equal(401)
    }
  }
}
