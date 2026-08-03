package code.api.UKOpenBanking.v4_0_1

import code.api.Constant
import code.api.util.APIUtil.{DateWithDayFormat, ResourceDoc, UserOrApplication, buildOperationId}
import code.api.util.ErrorMessages.{ConsentDoesNotMatchConsumer, ConsentDoesNotMatchUser, ConsentExpiredIssue, ConsentIdClaimMissing}
import code.api.util.{CallContext, CertificateUtil, Consent}
import code.consent.{ConsentStatus, Consents}
import code.model.UserExtended
import code.views.Views
import com.openbankproject.commons.model.{BankIdAccountId, ErrorMessage, ViewId}
import com.openbankproject.commons.util.ApiVersion
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

  // Create a UK consent row with an arbitrary apiStandard value (to simulate a foreign-standard
  // or legacy consent for the cross-standard boundary test).
  private def createConsentWithStandard(standard: Option[String]): String =
    Consents.consentProvider.vend.saveUKConsent(
      user = Some(resourceUser1),
      bankId = None,
      accountIds = None,
      consumerId = None,
      permissions = consentPermissions,
      expirationDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      transactionFromDateTime = Some(DateWithDayFormat.parse("2020-01-01")),
      transactionToDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      apiStandard = standard,
      apiVersion = Some("4.0.1")
    ).openOrThrowException("test consent creation failed").consentId

  // A pending (not yet authorised -- no bound PSU) consent, created by "consumer" (the OAuth1
  // consumer backing user1). For the cross-consumer regression tests: user2 authenticates with a
  // *different* consumer (consumer2, see DefaultUsers), so this simulates a second TPP trying to
  // reach a first TPP's still-pending consent before any PSU has authorised it.
  private def createPendingConsentForConsumer1(): String =
    Consents.consentProvider.vend.saveUKConsent(
      user = None,
      bankId = None,
      accountIds = None,
      consumerId = Some(testConsumer.consumerId.get),
      permissions = consentPermissions,
      expirationDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      transactionFromDateTime = Some(DateWithDayFormat.parse("2020-01-01")),
      transactionToDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      apiStandard = Some("UKOpenBanking"),
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
      // v4.0.1 four-letter status codes on the wire (not the stored long name) + StatusReason.
      (response.body \ "Data" \ "Status").extract[String] should equal("AWAU")
      (response.body \ "Data" \ "StatusReason" \ "StatusReasonCode").extract[List[String]] should equal(List("U036"))
    }
    scenario("unauthenticated -> 401", UKOpenBankingV401AccountInfo) {
      postUnauthed(consentPostBody, "aisp", "account-access-consents").code should equal(401)
    }
    // Lodging a consent is a client-credentials call: the TPP is authenticated as an app and no PSU
    // exists yet. UserOnly (the ResourceDoc default) sends ResourceDocMiddleware down anonymousAccess,
    // which 401s a request carrying no user. Pinned here because nothing else would notice the doc
    // silently reverting to the default -- the endpoint would keep working for as long as OAuth2
    // token parsing auto-vivifies a user for a client-credentials token, and start 401ing the day
    // that stops.
    scenario("ResourceDoc declares UserOrApplication so a PSU-less TPP call is not rejected", UKOpenBankingV401AccountInfo) {
      val docs = ResourceDoc.getResourceDocs(
        List(buildOperationId(ApiVersion.ukOpenBankingV401, "createAccountAccessConsents")))
      docs should not be empty
      docs.foreach(_.authMode should equal(UserOrApplication))
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
    // IDOR regression (currently RED): the endpoint only checks that the consent exists, never
    // that the caller owns it (see Http4sUKOBv401AccountInfo.getAccountAccessConsentsConsentId,
    // which discards the resolved user via `(_, cc)` before the lookup). Any authenticated party
    // can currently read any other party's consent details -- this must become a 403
    // ConsentDoesNotMatchUser once the ownership check is added, mirroring the identity contract
    // already enforced at consent authorise time (Http4s510: consent.userId == user.userId).
    scenario("authenticated as a different user than the consent owner -> 403, not 200", UKOpenBankingV401AccountInfo) {
      val consentId = createRealConsent() // owned by resourceUser1
      val response = getAuthedAsUser2("aisp", "account-access-consents", consentId)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ConsentDoesNotMatchUser)
    }
    // Cross-consumer regression (currently RED): a pending consent (no bound PSU yet) is
    // currently readable by ANY authenticated party, because the ownership check
    // (Option(consent.userId).forall(_.isBlank) || ...) deliberately lets pending consents
    // through -- it only guards against a *different bound user*, not a *different consumer*.
    // getAuthedAsUser2 authenticates as consumer2 (see DefaultUsers), a different OAuth1
    // consumer than the one that created this pending consent (testConsumer/consumer). Once
    // fixed, a different consumer must get 403 ConsentDoesNotMatchConsumer here.
    scenario("authenticated as a different consumer than the creator, pending consent -> 403, not 200", UKOpenBankingV401AccountInfo) {
      val consentId = createPendingConsentForConsumer1()
      val response = getAuthedAsUser2("aisp", "account-access-consents", consentId)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ConsentDoesNotMatchConsumer)
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
    // IDOR regression (currently RED, most severe of the two): deleteAccountAccessConsentsConsentId
    // (Http4sUKOBv401AccountInfo) resolves the consent by id and calls
    // Consents.consentProvider.vend.revoke(consentId) directly -- no ownership check at all. Any
    // authenticated party can currently revoke any other party's consent. Once fixed this must be
    // a 403 ConsentDoesNotMatchUser, and -- critically -- the consent's stored status must be left
    // untouched (still AWAITINGAUTHORISATION), proving the rejected delete had zero side effect.
    scenario("authenticated as a different user than the consent owner -> 403, and consent is left untouched", UKOpenBankingV401AccountInfo) {
      val consentId = createRealConsent() // owned by resourceUser1
      val response = deleteAuthedAsUser2("aisp", "account-access-consents", consentId)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ConsentDoesNotMatchUser)

      val stillThere = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")
      stillThere.status should equal(ConsentStatus.AWAITINGAUTHORISATION.toString)
    }
    // Cross-consumer regression (currently RED, most severe of this pair): a pending consent
    // (no bound PSU yet) is currently revokable by ANY authenticated party -- same root cause
    // as the GET cross-consumer gap above (the ownership check treats "no bound user yet" as
    // "anyone may proceed"). getAuthedAsUser2/deleteAuthedAsUser2 authenticate as consumer2, a
    // different OAuth1 consumer than the one that created this pending consent. Once fixed,
    // this must be 403 ConsentDoesNotMatchConsumer, and the consent must be left untouched.
    scenario("authenticated as a different consumer than the creator, pending consent -> 403, and consent is left untouched", UKOpenBankingV401AccountInfo) {
      val consentId = createPendingConsentForConsumer1()
      val response = deleteAuthedAsUser2("aisp", "account-access-consents", consentId)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ConsentDoesNotMatchConsumer)

      val stillThere = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")
      stillThere.status should equal(ConsentStatus.AWAITINGAUTHORISATION.toString)
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
    scenario("the consent's scope lands in its JWT, and the PSU gains nothing", UKOpenBankingV401AccountInfo) {
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

      // The JWT is the consent's scope: binding replaces the (null, null, permission) placeholders
      // createUKConsentJWT wrote with the real account, for the declared permission and no other.
      // What that scope is worth at request time is pinned in UKOpenBankingV401ConsentScopingTests,
      // which drives the whole applyUKRules path.
      val boundViews = {
        implicit val formats = code.api.util.CustomJsonFormats.formats
        val updated = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")
        code.api.util.JwtUtil.getSignedPayloadAsJson(updated.jsonWebToken)
          .map(com.openbankproject.commons.util.JsonAliases.parse(_).extract[code.api.util.ConsentJWT])
          .openOrThrowException("consent jwt").views
      }
      boundViews.map(v => (v.bank_id, v.account_id, v.view_id)) should equal(
        List((testBankId1.value, acc, Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)))

      // And the PSU is left exactly as they were. Binding used to write AccountAccess rows under
      // the PSU, which is what made every consent that PSU granted share one set of rows; the
      // consent's access now belongs to a principal of its own.
      userExtended.hasAccountAccess(
        Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID).openOrThrowException("view"),
        bankIdAccountId, None) should equal(false)
      userExtended.hasAccountAccess(
        Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_BALANCES_VIEW_ID).openOrThrowException("view"),
        bankIdAccountId, None) should equal(false)
    }
  }

  // ── Consent.grantUKConsentAccountAccess must reject accounts the PSU does not hold ──
  // Regression test for the account-ownership gap: grantUKConsentAccountAccess only verified
  // that the requested account_ids *exist* (checkBankAccountExists), never that the PSU
  // authorising the consent actually holds them. Without this check, any authenticated user
  // could authorise a UK consent naming ANY existing account_id (e.g. testAccountId1, held by
  // resourceUser1) and be granted every consented view on it -- an account-access IDOR. This
  // mirrors the existing "binds permissions to the selected account only" scenario above but
  // asserts on account holder-ship (AccountHolders.getAccountsHeld) rather than permission scope.
  feature("UKOB v4.0.1 Consent.grantUKConsentAccountAccess rejects an account the PSU does not hold") {
    scenario("resourceUser2 tries to authorise a consent naming resourceUser1's account -> rejected, no access granted", UKOpenBankingV401AccountInfo) {
      val userExtended = UserExtended(resourceUser2)
      val bankIdAccountId = BankIdAccountId(testBankId1, testAccountId1)

      // testAccountId1 is held by resourceUser1 (ServerSetupWithTestData.beforeEach), not resourceUser2.
      code.accountholders.AccountHolders.accountHolders.vend
        .getAccountsHeld(testBankId1, resourceUser2)
        .contains(bankIdAccountId) should equal(false)

      val consentId = createRealConsent() // permissions = List("ReadAccountsBasic")
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId).openOrThrowException("consent")

      val result = Await.result(
        Consent.grantUKConsentAccountAccess(resourceUser2, testBankId1, List(acc), consent, None),
        10.seconds)
      result.isDefined should equal(false)
      result match {
        case Failure(msg, _, _) => msg should include("OBP-35037")
        case other => fail(s"expected Failure(OBP-35037...), got $other")
      }

      // No AccountAccess row must have been created for resourceUser2 on this account.
      userExtended.hasAccountAccess(
        Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID).openOrThrowException("view"),
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

  // Gap 15: every UK v4.0.1 response carries x-fapi-interaction-id (FAPI tracing). Uses a stub
  // endpoint that returns 200 so the assertion is purely about the header, independent of consent.
  feature("UKOB v4.0.1 x-fapi-interaction-id response header") {
    scenario("generated as a UUID when the request omits it", UKOpenBankingV401AccountInfo) {
      val response = getAuthed("aisp", "accounts", "fake-accountid", "beneficiaries")
      response.code should equal(200)
      val interactionId = response.headers.map(_.get("x-fapi-interaction-id")).orNull
      interactionId should not be null
      interactionId should not be empty
      // a generated value is a UUID
      java.util.UUID.fromString(interactionId).toString should equal(interactionId)
    }
    scenario("echoed verbatim when the request supplies it", UKOpenBankingV401AccountInfo) {
      val supplied = "test-interaction-id-12345"
      val response = makeGetRequest(
        v401("aisp", "accounts", "fake-accountid", "beneficiaries").GET <@ (user1),
        List(("x-fapi-interaction-id", supplied)))
      response.code should equal(200)
      response.headers.map(_.get("x-fapi-interaction-id")).orNull should equal(supplied)
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
    // Issue A fix: this endpoint now runs checkUKConsent before the account lookup, matching
    // its sibling /balances and /transactions endpoints below. This OAuth1-signed test suite
    // carries no Bearer JWT, so the consent check deterministically 403s here -- the previous
    // "200 real account data" scenarios (dropped) actually reached real data with zero consent
    // enforcement, which was Issue A itself.
    scenario("authenticated without a consent-bound token -> 403", UKOpenBankingV401AccountInfo) {
      grantUKReadViews(testAccountId1, resourceUser1)
      val response = getAuthed("aisp", "accounts", acc)
      response.code should equal(403)
      response.body.extract[ErrorMessage].message.trim should equal(ConsentIdClaimMissing.trim)
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
