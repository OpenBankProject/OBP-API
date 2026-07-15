package code.api.UKOpenBanking.v4_0_1

import code.api.util.APIUtil.DateWithDayFormat
import code.consent.Consents
import org.json4s._
import org.scalatest.Tag

// Test suite for UK Open Banking Read/Write v4.0.1 (AccountInfo).
//
// The 9 endpoints wired to real connector data (see Http4sUKOBv401AccountInfo)
// get a full scenario matrix: unauthenticated -> 401, authenticated with real
// seeded data -> 200/201/204 with real field values, error paths (unknown
// consent/account), and a full consent create -> get -> delete -> get lifecycle.
//
// getAccounts / getAccountsAccountIdBalances / getAccountsAccountIdTransactions /
// getBalances / getTransactions call NewStyle.function.checkUKConsent, which
// requires a live Hydra OAuth2 introspection endpoint (see
// code.api.util.ConsentUtil.checkUKConsent) — there is no local test double for
// Hydra, so (mirroring UKOpenBankingV310AisTests' precedent for the same v3.1
// endpoints) only "unauthenticated -> 401" and "authenticated -> not 401" are
// asserted for those five. getBalances / getTransactions previously skipped the
// consent check entirely and returned real data straight from a DirectLogin
// token, which let a token with no bound consent reach 500 instead of the
// 403 OBP-35035 every other AISP data endpoint gives it.
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
  // ── AccountsApi ────────────────────────────────────────────────────
  // DATA-DEPENDENT: checkUKConsent requires live Hydra (see class doc above).
  feature("UKOB v4.0.1 GET /aisp/accounts") {
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts").code should not equal (401)
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
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", acc, "balances").code should not equal (401)
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
  // DATA-DEPENDENT: checkUKConsent requires live Hydra (see class doc above).
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/transactions") {
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", acc, "transactions").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", acc, "transactions").code should equal(401)
    }
  }

  // ── BalancesApi ────────────────────────────────────────────────────
  // DATA-DEPENDENT: checkUKConsent requires live Hydra (see class doc above).
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
  // DATA-DEPENDENT: checkUKConsent requires live Hydra (see class doc above).
  feature("UKOB v4.0.1 GET /aisp/transactions") {
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "transactions").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "transactions").code should equal(401)
    }
  }
}
