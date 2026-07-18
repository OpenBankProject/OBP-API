package code.api.UKOpenBanking.v4_0_1

import code.api.Constant
import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.ErrorMessages.{ConsentDoesNotMatchUser, ConsentExpiredIssue, ConsentIdClaimMissing}
import code.api.util.{CallContext, CertificateUtil, Consent}
import code.consent.{ConsentStatus, Consents}
import code.model.UserExtended
import code.views.Views
import com.openbankproject.commons.model.{BankIdAccountId, ErrorMessage, ViewId}
import net.liftweb.common.{Failure, Full}
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
      expirationDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      transactionFromDateTime = Some(DateWithDayFormat.parse("2020-01-01")),
      transactionToDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
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
      // v4.0.1 four-letter status codes on the wire (not the stored long name) + StatusReason.
      (response.body \ "Data" \ "Status").extract[String] should equal("AWAU")
      (response.body \ "Data" \ "StatusReason" \ "StatusReasonCode").extract[List[String]] should equal(List("U036"))
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      postUnauthed(consentPostBody, "aisp", "account-access-consents").code should equal(401)
    }
    scenario("all three datetime fields omitted -> 201, open-ended (no expiry/date restriction)", UKOpenBankingV401AccountInfo) {
      val bodyWithoutDates =
        """{
          |  "Data": {
          |    "Permissions": ["ReadAccountsBasic"]
          |  },
          |  "Risk": {}
          |}""".stripMargin
      val response = postAuthed(bodyWithoutDates, "aisp", "account-access-consents")
      response.code should equal(201)
      val consentId = (response.body \ "Data" \ "ConsentId").extract[String]
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")
      // MappedDateTime fields left unset by a null write come back as Java null, not a sentinel date.
      consent.expirationDateTime should equal(null)
      consent.transactionFromDateTime should equal(null)
      consent.transactionToDateTime should equal(null)
    }
    scenario("full ISO-8601 datetime with time and offset is preserved, not truncated to a bare date", UKOpenBankingV401AccountInfo) {
      val bodyWithFullDatetime =
        """{
          |  "Data": {
          |    "Permissions": ["ReadAccountsBasic"],
          |    "ExpirationDateTime": "2030-06-15T13:45:30+02:00",
          |    "TransactionFromDateTime": "2020-01-01",
          |    "TransactionToDateTime": "2030-01-01"
          |  },
          |  "Risk": {}
          |}""".stripMargin
      val response = postAuthed(bodyWithFullDatetime, "aisp", "account-access-consents")
      response.code should equal(201)
      val consentId = (response.body \ "Data" \ "ConsentId").extract[String]
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")
      // 2030-06-15T13:45:30+02:00 == 2030-06-15T11:45:30Z -- if the parser truncated to the bare
      // date (the pre-fix DateWithDayFormat behaviour), this would be 2030-06-15T00:00:00Z instead.
      consent.expirationDateTime.getTime should equal(
        java.time.OffsetDateTime.parse("2030-06-15T13:45:30+02:00").toInstant.toEpochMilli)
    }
    scenario("malformed datetime -> 400, not 500", UKOpenBankingV401AccountInfo) {
      val bodyWithBadDate =
        """{
          |  "Data": {
          |    "Permissions": ["ReadAccountsBasic"],
          |    "ExpirationDateTime": "not-a-date",
          |    "TransactionFromDateTime": "2020-01-01",
          |    "TransactionToDateTime": "2030-01-01"
          |  },
          |  "Risk": {}
          |}""".stripMargin
      postAuthed(bodyWithBadDate, "aisp", "account-access-consents").code should equal(400)
    }
  }
  feature("UKOB v4.0.1 GET /aisp/account-access-consents/CONSENT_ID") {
    scenario("authenticated with real consent -> 200 real data", UKOpenBankingV401AccountInfo) {
      val consentId = createRealConsent()
      val response = getAuthed("aisp", "account-access-consents", consentId)
      response.code should equal(200)
      (response.body \ "Data" \ "ConsentId").extract[String] should equal(consentId)
      (response.body \ "Data" \ "Permissions").extract[List[String]] should equal(consentPermissions)
      // freshly-created consent is AWAITINGAUTHORISATION → wire code AWAU
      (response.body \ "Data" \ "Status").extract[String] should equal("AWAU")
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
      // stored status is REVOKED, but the v4.0.1 wire format reports the spec's CANC code
      (afterDelete.body \ "Data" \ "Status").extract[String] should equal("CANC")
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

  // Regression coverage for Gap 14 (UK consents previously never expired): checkUKConsent must
  // reactively reject an AUTHORISED consent whose ExpirationDateTime has passed, independent of
  // ConsentScheduler's proactive sweep timing. Exercised by calling Consent.checkUKConsent
  // directly with a hand-built CallContext carrying a self-signed Bearer JWT with a consent_id
  // claim -- JwtUtil.getOptionalClaim only parses the JWT structurally (SignedJWT.parse), it does
  // not verify the signature, so this doesn't need to be signed with the real shared secret. This
  // sidesteps the suite-wide limitation noted above (OAuth1-signed test requests carry no real
  // Bearer JWT) for the one scenario that specifically needs one.
  feature("UKOB v4.0.1 Consent.checkUKConsent rejects an authorised consent past its ExpirationDateTime") {
    scenario("expired consent -> Failure(ConsentExpiredIssue), not silently accepted", UKOpenBankingV401AccountInfo) {
      val consent = Consents.consentProvider.vend.saveUKConsent(
        user = Some(resourceUser1),
        bankId = None,
        accountIds = None,
        consumerId = None,
        permissions = consentPermissions,
        expirationDateTime = Some(new java.util.Date(System.currentTimeMillis() - 60000L)), // 1 minute ago
        transactionFromDateTime = None,
        transactionToDateTime = None,
        apiStandard = Some("UKOpenBanking"),
        apiVersion = Some("4.0.1")
      ).openOrThrowException("consent creation failed")
      Consents.consentProvider.vend.updateConsentUser(consent.consentId, resourceUser1)
      Consents.consentProvider.vend.updateConsentStatus(consent.consentId, ConsentStatus.AUTHORISED)

      val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
        .claim("consent_id", consent.consentId)
        .build()
      val jwt = CertificateUtil.jwtWithHmacProtection(claimsSet)
      val callContext = CallContext(authReqHeaderField = Full(s"Bearer $jwt"))

      Consent.checkUKConsent(resourceUser1, Some(callContext)) match {
        case Failure(msg, _, _) => msg.contains(ConsentExpiredIssue) should equal(true)
        case other => fail(s"expected Failure(ConsentExpiredIssue), got $other")
      }
    }
  }

  // Gap 12 (re-authentication) security guards on POST /obp/v5.1.0/banks/BANK_ID/consents/
  // CONSENT_ID/authorise/challenge -- the first step of the ceremony, so it can be exercised
  // without a completed SCA/OTP. The successful re-auth path (start challenge -> answer OTP ->
  // stays AUTHORISED) needs the full OTP ceremony, which has no test harness in this repo yet
  // (the authorise endpoints shipped with no coverage), so it's not asserted here; these cover
  // the security-relevant rejection branches the re-auth relaxation introduces.
  private def scaChallengeRequest(consentId: String) =
    (baseRequest / "obp" / "v5.1.0" / "banks" / testBankId1.value / "consents" / consentId / "authorise" / "challenge").POST
  private def createUKConsent(user: com.openbankproject.commons.model.User, expiration: Option[java.util.Date]): String = {
    val consent = Consents.consentProvider.vend.saveUKConsent(
      user = Some(user), bankId = None, accountIds = None, consumerId = None,
      permissions = consentPermissions, expirationDateTime = expiration,
      transactionFromDateTime = None, transactionToDateTime = None,
      apiStandard = Some("UKOpenBanking"), apiVersion = Some("4.0.1")
    ).openOrThrowException("consent creation failed")
    consent.consentId
  }
  feature("UKOB v4.0.1 re-authentication guards on the SCA challenge endpoint") {
    scenario("challenge-start on an already-authorised consent bound to a different PSU -> 403", UKOpenBankingV401AccountInfo) {
      val consentId = createUKConsent(resourceUser1, Some(new java.util.Date(System.currentTimeMillis() + 3600000L)))
      Consents.consentProvider.vend.updateConsentUser(consentId, resourceUser1)
      Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.AUTHORISED)
      // user2 != the bound resourceUser1 -> hijack guard rejects
      val response = makePostRequest(scaChallengeRequest(consentId) <@ (user2), "")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.contains(ConsentDoesNotMatchUser) should equal(true)
    }
    scenario("challenge-start on an authorised consent past its ExpirationDateTime -> 400 ConsentExpiredIssue", UKOpenBankingV401AccountInfo) {
      val consentId = createUKConsent(resourceUser1, Some(new java.util.Date(System.currentTimeMillis() - 60000L)))
      Consents.consentProvider.vend.updateConsentUser(consentId, resourceUser1)
      Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.AUTHORISED)
      val response = makePostRequest(scaChallengeRequest(consentId) <@ (user1), "")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message.contains(ConsentExpiredIssue) should equal(true)
    }
    // Note: the terminal-status rejection (EXPIRED/REJECTED can't be re-authed) is enforced by
    // ukReAuthableStatuses not containing those; it isn't asserted via a third HTTP scenario here
    // because a second same-user OAuth1 call within this block collides on the test harness's
    // nonce/timestamp replay check (a harness artifact -- production UK uses OAuth2 Bearer).
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
