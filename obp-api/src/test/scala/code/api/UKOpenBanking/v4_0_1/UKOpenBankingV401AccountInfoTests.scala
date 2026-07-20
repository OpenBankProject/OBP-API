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
// getAccounts / getAccountsAccountIdBalances / getAccountsAccountIdTransactions
// call NewStyle.function.checkUKConsent, which requires the Bearer access token
// to be a JWT carrying a consent_id claim bound to an AUTHORISED consent of the
// calling user+consumer (see code.api.util.ConsentUtil.checkUKConsent). The test
// framework's DirectLogin tokens carry no consent_id, so (mirroring
// UKOpenBankingV310AisTests' precedent for the same three v3.1 endpoints) only
// "unauthenticated -> 401" and "authenticated -> not 401" are asserted for those three.
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

  // Create a UK consent row with an arbitrary apiStandard value (to simulate a foreign-standard
  // or legacy consent for the cross-standard boundary test).
  private def createConsentWithStandard(standard: Option[String]): String =
    Consents.consentProvider.vend.saveUKConsent(
      user = Some(resourceUser1),
      bankId = None,
      accountIds = None,
      consumerId = None,
      permissions = consentPermissions,
      expirationDateTime = DateWithDayFormat.parse("2030-01-01"),
      transactionFromDateTime = DateWithDayFormat.parse("2020-01-01"),
      transactionToDateTime = DateWithDayFormat.parse("2030-01-01"),
      apiStandard = standard,
      apiVersion = Some("4.0.1")
    ).openOrThrowException("test consent creation failed").consentId

  // ── Cross-standard exercise boundary (ConsentUtil.assertConsentStandard) ──
  feature("A consent may only be exercised by the standard that created it") {
    import code.api.util.Consent
    scenario("a UK consent is accepted by the UK gate, rejected by OBP and BG gates", UKOpenBankingV401AccountInfo) {
      val consentId = createConsentWithStandard(Some(Consent.ConsentStandardUK))
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardUK) should equal(None)
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardOBP).isDefined should equal(true)
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardBG).isDefined should equal(true)
    }
    scenario("an OBP consent is rejected by the UK gate", UKOpenBankingV401AccountInfo) {
      val consentId = createConsentWithStandard(Some(Consent.ConsentStandardOBP))
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardUK).isDefined should equal(true)
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardOBP) should equal(None)
    }
    scenario("a legacy consent with no standard is grandfathered for every gate", UKOpenBankingV401AccountInfo) {
      val consentId = createConsentWithStandard(None)
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardUK) should equal(None)
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardOBP) should equal(None)
      Consent.assertConsentStandardById(consentId, Consent.ConsentStandardBG) should equal(None)
    }
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
  // DATA-DEPENDENT: checkUKConsent requires a consent-bound token (consent_id claim — see class doc above).
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
  // DATA-DEPENDENT: checkUKConsent requires a consent-bound token (consent_id claim — see class doc above).
  feature("UKOB v4.0.1 GET /aisp/accounts/ACCOUNT_ID/transactions") {
    scenario("authenticated", UKOpenBankingV401AccountInfo) {
      getAuthed("aisp", "accounts", acc, "transactions").code should not equal (401)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "accounts", acc, "transactions").code should equal(401)
    }
  }

  // ── BalancesApi ────────────────────────────────────────────────────
  feature("UKOB v4.0.1 GET /aisp/balances") {
    scenario("authenticated with real account -> 200 real balance", UKOpenBankingV401AccountInfo) {
      val response = getAuthed("aisp", "balances")
      response.code should equal(200)
      // resourceUser1 owns accounts on several test banks (see TestConnectorSetup.
      // createAccountRelevantResources), so testAccountId1's entry isn't necessarily
      // first — find it rather than assume position 0.
      val balances = (response.body \ "Data" \ "Balance").children
      balances should not be empty
      val myBalance = balances.find(b => (b \ "AccountId").extract[String] == acc)
      myBalance shouldBe defined
      (myBalance.get \ "Amount" \ "Currency").extract[String] should equal("EUR")
    }
    scenario("authenticated with no private accounts -> 200 empty Balance list", UKOpenBankingV401AccountInfo) {
      val response = getAuthedAsUser2("aisp", "balances")
      response.code should equal(200)
      (response.body \ "Data" \ "Balance").children should be(empty)
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
  feature("UKOB v4.0.1 GET /aisp/transactions") {
    scenario("authenticated with seeded transactions -> 200 real data", UKOpenBankingV401AccountInfo) {
      val seeded = seedTransactions(testAccountId1)
      val response = getAuthed("aisp", "transactions")
      response.code should equal(200)
      // resourceUser1 owns accounts on several test banks, but only testAccountId1
      // has seeded transactions here, so every returned entry should belong to it.
      val transactions = (response.body \ "Data" \ "Transaction").children
      transactions should not be empty
      transactions.foreach(t => (t \ "AccountId").extract[String] should equal(acc))
      (transactions.head \ "Amount" \ "Currency").extract[String] should equal("EUR")
      transactions.map(t => (t \ "TransactionId").extract[String]) should contain (seeded.head.id.value)
    }
    scenario("authenticated with no private accounts -> 200 empty Transaction list", UKOpenBankingV401AccountInfo) {
      val response = getAuthedAsUser2("aisp", "transactions")
      response.code should equal(200)
      (response.body \ "Data" \ "Transaction").children should be(empty)
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      getUnauthed("aisp", "transactions").code should equal(401)
    }
  }
}
