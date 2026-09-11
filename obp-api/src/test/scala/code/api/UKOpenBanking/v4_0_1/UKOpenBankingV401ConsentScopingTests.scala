package code.api.UKOpenBanking.v4_0_1

import code.api.Constant
import code.api.util.APIUtil.DateWithDayFormat
import code.api.util.{CallContext, Consent, ErrorMessages}
import code.consent.{ConsentStatus, Consents}
import code.entitlement.Entitlement
import code.model.UserExtended
import code.views.Views
import code.views.system.AccountAccess
import com.openbankproject.commons.model.{BankIdAccountId, User, ViewId}
import net.liftweb.common.{Box, Full}
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * What a UK consent's declared scope is actually worth, once more than one consent exists.
 *
 * A UK consent's data access runs as the consent's own shadow user: the consent JWT carries a
 * random UUID in `sub`, and applyUKRules resolves that to a user that exists only for this consent
 * and grants it exactly the views the JWT names (see Consent.resolveUKConsentPrincipal). Berlin
 * Group and OBP-native have always worked this way; UK used to grant to the real PSU instead, which
 * meant every consent that PSU had granted wrote to one shared set of AccountAccess rows and
 * silently rewrote each other's.
 *
 * The properties worth pinning down, in the order they were lost historically:
 *  - narrowing: a consent that names fewer permissions, or fewer accounts, must have less;
 *  - independence: two consents live at once under the SAME TPP must not see each other's scope --
 *    this is the one that was still open when access was keyed on the PSU;
 *  - isolation: one TPP's authorisation must not rewrite another TPP's access;
 *  - and the scope must be all the principal has: no account ownership, no roles, so none of the
 *    checks that run before the AccountAccess lookup (firehose, ABAC) can answer for it.
 *
 * Asserted at the UserExtended.hasAccountAccess layer, driven through the real applyUKRules
 * entry point, because that is exactly what APIUtil.checkViewAccessAndReturnView -- and therefore
 * every UK data endpoint -- consults.
 */
class UKOpenBankingV401ConsentScopingTests extends UKOpenBankingV401ServerSetup {

  object UKConsentScoping extends Tag("UKConsentScoping")

  private val acc = testAccountId1.value
  private val otherAcc = testAccountId0.value
  private val bankIdAccountId = BankIdAccountId(testBankId1, testAccountId1)
  private val otherBankIdAccountId = BankIdAccountId(testBankId1, testAccountId0)

  private val ReadAccountsBasic = Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID
  private val ReadBalances = Constant.SYSTEM_READ_BALANCES_VIEW_ID
  private val FirehoseRole = code.api.util.ApiRole.canUseAccountFirehoseAtAnyBank.toString

  private def systemView(viewId: String) =
    Views.views.vend.getOrCreateSystemView(viewId).openOrThrowException(s"could not create system view $viewId")

  /**
   * Create a UK consent held by `consumerId`, bind it to accounts, and mark it AUTHORISED -- i.e.
   * everything the POST /consents/CONSENT_ID/authorise endpoint does once SCA has passed.
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

    // The seven UK permission views are not seeded in the test DB (Boot only creates
    // owner/auditor/accountant/... unless additional_system_views is set, and test.default.props
    // does not set it), so they have to exist before the grant can bind them.
    permissions.foreach(systemView)

    reAuthorise(consentId, accountIds)
    Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.AUTHORISED)
    consentId
  }

  /** Re-run the authorise-time binding on a consent that already exists and is still live -- the
   *  PSU re-authenticating against a consent the TPP never revoked. */
  private def reAuthorise(consentId: String, accountIds: List[String]): Unit = {
    val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
      .openOrThrowException(s"consent $consentId not found")
    Await.result(
      Consent.grantUKConsentAccountAccess(resourceUser1, testBankId1, accountIds, consent, None),
      10.seconds)
  }

  /**
   * Authenticate a request the way a caller presenting this consent would, and hand back the
   * principal it resolves to plus the CallContext the endpoint would see. This is the real
   * Consent-Id / Consent-JWT header path, gates and all.
   */
  private def authenticateWith(consentId: String, consumer: code.model.Consumer): (User, CallContext) = {
    val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
      .openOrThrowException(s"consent $consentId not found")
    val (user, callContext) = Await.result(
      Consent.applyUKRules(consent, "", CallContext(consumer = Full(consumer))),
      10.seconds)
    (user.openOrThrowException(s"consent $consentId did not authenticate: $user"),
      callContext.getOrElse(CallContext(consumer = Full(consumer))))
  }

  /** Access as it is evaluated for a request arriving with `consentId` from `consumer`. */
  private def canRead(viewId: String,
                      consentId: String,
                      consumer: code.model.Consumer,
                      account: BankIdAccountId = bankIdAccountId): Boolean = {
    val (principal, callContext) = authenticateWith(consentId, consumer)
    UserExtended(principal).hasAccountAccess(systemView(viewId), account, Some(callContext))
  }

  Feature("A UK consent is authoritative for the permissions it declares") {
    Scenario("a consent that did not ask for a permission does not have it", UKConsentScoping) {
      val wide = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic, ReadBalances))
      canRead(ReadAccountsBasic, wide, testConsumer) should equal(true)
      canRead(ReadBalances, wide, testConsumer) should equal(true)

      // Same TPP, same PSU, same account -- but this consent never asked for balances.
      val narrow = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic))
      canRead(ReadAccountsBasic, narrow, testConsumer) should equal(true)
      canRead(ReadBalances, narrow, testConsumer) should equal(false)
    }

  }

  Feature("A UK consent is authoritative for the accounts it names") {
    Scenario("a consent does not reach an account it never named", UKConsentScoping) {
      val both = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic, ReadBalances),
        accountIds = List(acc, otherAcc))
      canRead(ReadAccountsBasic, both, testConsumer, otherBankIdAccountId) should equal(true)

      val onlyOne = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic, ReadBalances),
        accountIds = List(acc))
      canRead(ReadAccountsBasic, onlyOne, testConsumer) should equal(true)
      canRead(ReadAccountsBasic, onlyOne, testConsumer, otherBankIdAccountId) should equal(false)
      canRead(ReadBalances, onlyOne, testConsumer, otherBankIdAccountId) should equal(false)
    }

    Scenario("re-authorising one consent with fewer accounts narrows it", UKConsentScoping) {
      val consentId = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic),
        accountIds = List(acc, otherAcc))
      canRead(ReadAccountsBasic, consentId, testConsumer, otherBankIdAccountId) should equal(true)

      // The PSU re-authorises and drops otherAcc from the selection. Nothing sweeps anything: the
      // JWT no longer names that account, so the next request simply does not re-grant it.
      reAuthorise(consentId, List(acc))
      canRead(ReadAccountsBasic, consentId, testConsumer) should equal(true)
      canRead(ReadAccountsBasic, consentId, testConsumer, otherBankIdAccountId) should equal(false)
    }
  }

  Feature("Two live consents held by the same TPP are scoped independently") {
    Scenario("re-authorising the wider consent does not widen the narrower one", UKConsentScoping) {
      val wide = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic),
        accountIds = List(acc, otherAcc))
      val narrow = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic),
        accountIds = List(acc))

      canRead(ReadAccountsBasic, narrow, testConsumer, otherBankIdAccountId) should equal(false)

      // Both consents are live and both belong to the same TPP and the same PSU. While access was
      // keyed on the PSU they shared one set of rows, so re-authorising the wider one handed the
      // narrower one an account it never named. Each now has a principal of its own.
      reAuthorise(wide, List(acc, otherAcc))

      canRead(ReadAccountsBasic, wide, testConsumer, otherBankIdAccountId) should equal(true)
      canRead(ReadAccountsBasic, narrow, testConsumer, otherBankIdAccountId) should equal(false)
    }
  }

  Feature("One TPP's UK consent does not rewrite another TPP's access") {
    Scenario("a second consumer authorising a narrower consent leaves the first consumer's access intact", UKConsentScoping) {
      val first = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic, ReadBalances))
      val second = authoriseConsentFor(testConsumer2.consumerId, List(ReadAccountsBasic))

      canRead(ReadBalances, second, testConsumer2) should equal(false)
      canRead(ReadBalances, first, testConsumer) should equal(true)
    }
  }

  Feature("A UK consent's principal has the consent's scope and nothing else") {
    Scenario("it holds no account ownership and no roles, so no check above the view lookup can answer for it", UKConsentScoping) {
      // Give the PSU the role that lets account firehose bypass the AccountAccess check entirely.
      // APIUtil.hasAccountAccess consults firehose (and then ABAC) BEFORE the view lookup, so if the
      // consent ran as the PSU this role would make its declared scope meaningless.
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, FirehoseRole)

      val consentId = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic))
      val (principal, _) = authenticateWith(consentId, testConsumer)

      principal.userId should not equal resourceUser1.userId
      Entitlement.entitlement.vend.getEntitlementsByUserId(principal.userId)
        .getOrElse(Nil) shouldBe empty

      // ...and no ownership of any account, so the owner view is not reachable either.
      AccountAccess.findByUniqueIndex(
        testBankId1, testAccountId1, ViewId(Constant.SYSTEM_OWNER_VIEW_ID),
        principal.userPrimaryKey, Constant.ALL_CONSUMERS
      ).isDefined should equal(false)
    }

    Scenario("account ownership is left alone: the PSU keeps the owner view", UKConsentScoping) {
      authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic))

      // owner comes from holding the account, not from any consent. Nothing in the consent flow
      // writes or removes a row for the PSU any more, so it is untouched.
      AccountAccess.findByUniqueIndex(
        testBankId1, testAccountId1, ViewId(Constant.SYSTEM_OWNER_VIEW_ID),
        resourceUser1.userPrimaryKey, Constant.ALL_CONSUMERS
      ).isDefined should equal(true)
    }
  }

  /**
   * The consent normally arrives as a `consent_id` claim inside the OAuth2 access token, not in a
   * Consent-Id header. On that path the request is authenticated as the PSU before any UK code
   * runs, so the principal is swapped at the end of authentication instead
   * (Consent.applyUKConsentPrincipalFromToken). Everything above drives the header path; this
   * drives the token one, since it is the path the standard actually specifies.
   *
   * The token is self-signed: JwtUtil.getOptionalClaim parses structurally and does not verify, and
   * the real access token would have been verified by OAuth2Login long before this point.
   */
  private def bearerContextFor(consentId: String, consumer: code.model.Consumer): CallContext = {
    val claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().claim("consent_id", consentId).build()
    CallContext(
      user = Full(resourceUser1),
      consumer = Full(consumer),
      authReqHeaderField = Full(s"Bearer ${code.api.util.CertificateUtil.jwtWithHmacProtection(claims)}"))
  }

  Feature("A UK consent presented in an access token resolves the same way as one in a header") {
    Scenario("the principal is swapped, the PSU is kept, and the scope is the consent's", UKConsentScoping) {
      val consentId = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic),
        accountIds = List(acc))

      val (principal, callContext) =
        Consent.applyUKConsentPrincipalFromToken(Full(resourceUser1), Some(bearerContextFor(consentId, testConsumer)))
      val resolved = principal.openOrThrowException("token path did not resolve a principal")
      val cc = callContext.getOrElse(fail("token path dropped the CallContext"))

      resolved.userId should not equal resourceUser1.userId
      // The PSU has to survive the swap: checkUKConsent compares the consent's owner against it,
      // and the CBS adapter names it. (Metric rows record the principal; the PSU behind a
      // consent-borne row is resolved via the consent table at read time.)
      cc.consenter.map(_.userId) should equal(Full(resourceUser1.userId))

      UserExtended(resolved).hasAccountAccess(systemView(ReadAccountsBasic), bankIdAccountId, Some(cc)) should equal(true)
      UserExtended(resolved).hasAccountAccess(systemView(ReadBalances), bankIdAccountId, Some(cc)) should equal(false)
      UserExtended(resolved).hasAccountAccess(systemView(ReadAccountsBasic), otherBankIdAccountId, Some(cc)) should equal(false)

      // And the consent's ownership check still passes, because it is asked about the PSU.
      Consent.checkUKConsent(resolved, Some(cc)).isDefined should equal(true)
    }

    Scenario("a token with no consent claim is left exactly as it is", UKConsentScoping) {
      val plain = CallContext(user = Full(resourceUser1), consumer = Full(testConsumer))
      val (principal, callContext) = Consent.applyUKConsentPrincipalFromToken(Full(resourceUser1), Some(plain))
      principal.map(_.userId) should equal(Full(resourceUser1.userId))
      callContext.flatMap(_.consenter.toOption) should equal(None)
    }

    // The PSU used to be taken from the token rather than from the consent, and nothing here
    // compared the two. So a token belonging to anyone at all, carrying a consent_id that is not
    // theirs, was swapped onto that consent's shadow user -- with `consenter` set to the token's
    // holder rather than to the person the consent is about.
    //
    // checkUKConsent then caught it, because it compares the consent's mUserId against `consenter`
    // and they disagreed. But only the UK read endpoints call checkUKConsent: on every other
    // endpoint family the swapped principal stood, and it carries the consent's account access. The
    // refusal has to be decided here, where it is recorded on the CallContext and enforced for every
    // endpoint by ResourceDocMiddleware.
    Scenario("a token whose subject is not the consent's PSU is refused, not swapped", UKConsentScoping) {
      val consentId = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic),
        accountIds = List(acc))

      Given("resourceUser2's session presenting a consent authorised by resourceUser1")
      val (principal, callContext) =
        Consent.applyUKConsentPrincipalFromToken(Full(resourceUser2), Some(bearerContextFor(consentId, testConsumer)))
      val cc = callContext.getOrElse(fail("token path dropped the CallContext"))

      Then("the principal is left alone rather than swapped onto the consent")
      principal.map(_.userId) should equal(Full(resourceUser2.userId))
      cc.consenter.toOption should equal(None)

      And("the refusal is recorded, so every endpoint family enforces it and not just the UK reads")
      cc.ukConsentUnresolved should equal(Some(ErrorMessages.ConsentDoesNotMatchUser))
      Consent.unresolvedUKConsentRefusal(cc.ukConsentUnresolved, "/obp/v5.1.0/my/accounts") should
        equal(Some(ErrorMessages.ConsentDoesNotMatchUser))
    }
  }

  /**
   * A UK consent the token names but that cannot be resolved to its shadow user.
   *
   * The principal then stays the PSU, and the PSU's own AccountAccess rows are wider than any
   * consent -- so serving the request would hand the caller everything the PSU can see under a
   * consent that named something else, or nothing. That is the widest possible reading of a consent
   * we just failed to understand, and it is what the token path used to do silently: every failure
   * inside resolveUKConsentPrincipal collapsed to None, and None meant "leave the request as it
   * was". The Consent-Id header path refused the same consent all along.
   *
   * The refusal deliberately lands in checkUKConsent rather than in authentication -- see the third
   * scenario, which is what stops it being "simplified" back.
   */
  private def unresolvableConsent(permissions: List[String], bindAccounts: Boolean): String = {
    val consentId = Consents.consentProvider.vend.saveUKConsent(
      user = Some(resourceUser1),
      bankId = None,
      accountIds = None,
      consumerId = Some(testConsumer.consumerId),
      permissions = permissions,
      expirationDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      transactionFromDateTime = Some(DateWithDayFormat.parse("2020-01-01")),
      transactionToDateTime = Some(DateWithDayFormat.parse("2030-01-01")),
      apiStandard = Some("UKOpenBanking"),
      apiVersion = Some("4.0.1")
    ).openOrThrowException("test consent creation failed").consentId
    // Deliberately no `permissions.foreach(systemView)`: the views these name must NOT exist.
    if (bindAccounts) reAuthorise(consentId, List(acc))
    Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.AUTHORISED)
    consentId
  }

  /** The swap's verdict on a consent presented in an access token. */
  private def swapFor(consentId: String): (Box[User], CallContext) = {
    val (principal, callContext) =
      Consent.applyUKConsentPrincipalFromToken(Full(resourceUser1), Some(bearerContextFor(consentId, testConsumer)))
    (principal, callContext.getOrElse(fail("token path dropped the CallContext")))
  }

  Feature("A UK consent named by a token but not resolvable is refused, not served as the PSU") {

    Scenario("a consent that names no account: the principal is not swapped and data access is refused", UKConsentScoping) {
      // Never bound to accounts, so its JWT still carries createUKConsentJWT's
      // (bank_id=null, account_id=null, permission) placeholders -- a consent authorised before
      // account binding existed.
      val consentId = unresolvableConsent(List(ReadAccountsBasic), bindAccounts = false)
      val (principal, cc) = swapFor(consentId)

      principal.map(_.userId) should equal(Full(resourceUser1.userId))
      cc.consenter.toOption should equal(None)
      cc.ukConsentUnresolved.getOrElse("") should include("OBP-35040")

      // checkUKConsent throws a JSON-encoded APIFailureNewStyle, which ErrorResponseConverter turns
      // into the response -- the same mechanism checkConsent uses for its own coded refusals.
      val refusal = intercept[Exception] {
        Consent.checkUKConsent(principal.openOrThrowException("no principal"), Some(cc))
      }
      refusal.getMessage should include("OBP-35040")
      refusal.getMessage should include("403")
    }

    Scenario("a consent naming a view that does not exist is refused too", UKConsentScoping) {
      // Bound to a real account, but for a permission whose system view was never created --
      // exactly what an instance whose additional_system_views predates ReadTransactionsCredits
      // does with a conforming consent. grantAccessToViews then fails and, before this fix, the
      // request was served as the PSU with a partially-granted shadow user left behind.
      val consentId = unresolvableConsent(List("ReadTransactionsCreditsNotSeededInThisTestDb"), bindAccounts = true)
      val (principal, cc) = swapFor(consentId)

      principal.map(_.userId) should equal(Full(resourceUser1.userId))
      cc.ukConsentUnresolved.isDefined should equal(true)

      val refusal = intercept[Exception] {
        Consent.checkUKConsent(principal.openOrThrowException("no principal"), Some(cc))
      }
      // The reason must be the one this scenario is about -- a bare "403" would also be satisfied
      // by a role, consumer or ownership refusal arriving from somewhere else entirely.
      refusal.getMessage should include("OBP-20059") // CouldNotAssignAccountAccess
      refusal.getMessage should include("403")
    }

    /**
     * The rule ResourceDocMiddleware applies to every endpoint family, including the ones that
     * never call checkUKConsent.
     *
     * Tested here rather than over HTTP because the token path cannot be driven from this suite:
     * setting ukConsentUnresolved requires an authenticated request whose Authorization header is a
     * Bearer JWT, and authenticating one needs a JWKS the suite has no signing key for. That is also
     * why the scenarios above call applyUKConsentPrincipalFromToken directly. The wiring -- that the
     * middleware consults this rule at all -- is covered by the probe matrix, which drives a real
     * OBP-native endpoint with a real token against a running instance.
     */
    Scenario("the refusal rule covers other endpoint families, and exempts consent management", UKConsentScoping) {
      val reason = Some(ErrorMessages.ConsentNamesNoAccount)

      Given("a request whose token named a UK consent that could not be resolved")
      Then("an endpoint that does not call checkUKConsent is refused all the same")
      Consent.unresolvedUKConsentRefusal(reason, "/obp/v5.1.0/my/accounts") should equal(reason)
      Consent.unresolvedUKConsentRefusal(reason, "/obp/v4.0.0/banks/BANK_ID/accounts") should equal(reason)

      And("so is a UK data endpoint, which checkUKConsent would also have caught")
      Consent.unresolvedUKConsentRefusal(reason, "/open-banking/v4.0.1/aisp/accounts") should equal(reason)

      And("but the consent-management endpoints stay reachable, or the TPP cannot clear it up")
      Consent.unresolvedUKConsentRefusal(
        reason, "/open-banking/v4.0.1/aisp/account-access-consents/CONSENT_ID") should equal(None)
      Consent.unresolvedUKConsentRefusal(
        reason, "/open-banking/v3.1/account-access-consents") should equal(None)

      And("a request with no unresolved consent is never refused by this rule")
      Consent.unresolvedUKConsentRefusal(None, "/obp/v5.1.0/my/accounts") should equal(None)
      Consent.unresolvedUKConsentRefusal(
        None, "/open-banking/v4.0.1/aisp/account-access-consents") should equal(None)
    }

    /**
     * Which PSU the token path runs for. Extracted for the same reason as the rule above: the
     * decision is worth pinning and the path that uses it cannot be driven from this suite.
     *
     * The consent row is the source of truth, as it already is on the header path, and the token's
     * subject must agree with it. Losing the second half is the easy mistake, and it is silent:
     * checkUKConsent compares the consent's mUserId against `consenter`, so deriving `consenter`
     * from mUserId without also comparing the token would have that check compare the consent's user
     * with itself and pass for anybody's token.
     */
    Scenario("the token path takes its PSU from the consent, and the token must agree", UKConsentScoping) {
      val psu = "the-psu-user-id"
      val someoneElse = "a-different-user-id"

      Given("a consent bound to a PSU")
      Then("a token for that PSU resolves to them")
      Consent.ukTokenPathPsuId(psu, psu) should equal(Right(psu))

      And("a token for anyone else is refused, which is the check that must not be lost")
      Consent.ukTokenPathPsuId(psu, someoneElse) should equal(Left(ErrorMessages.ConsentDoesNotMatchUser))

      And("a consent naming no PSU was never authorised, so there is nobody it is on behalf of")
      // Not a fallback to the token's user: that would serve everything that user can see, which is
      // the whole of what a consent exists to narrow.
      Consent.ukTokenPathPsuId("", psu) should equal(Left(ErrorMessages.ConsentNotFound))
      Consent.ukTokenPathPsuId(null, psu) should equal(Left(ErrorMessages.ConsentNotFound))
      Consent.ukTokenPathPsuId("   ", psu) should equal(Left(ErrorMessages.ConsentNotFound))
    }

    Scenario("the consent stays inspectable and revocable by the TPP that lodged it", UKConsentScoping) {
      val consentId = unresolvableConsent(List(ReadAccountsBasic), bindAccounts = false)
      val (principal, cc) = swapFor(consentId)
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException(s"consent $consentId not found")

      // Authentication itself must still succeed. If the refusal were raised in the swap instead,
      // this box would carry the failure and GET/DELETE account-access-consents -- which never call
      // checkUKConsent -- would 401 as ApplicationNotIdentified, locking the TPP out of the only
      // endpoints that can clear up the consent this is all about.
      principal.isDefined should equal(true)

      // And the rule those two endpoints actually apply still says yes, for the same request the
      // data endpoints refuse.
      Consent.checkUKConsentAccess(
        consent.userId, consent.consumerId,
        Consent.actingPsu(cc).map(_.userId),
        cc.consumer.map(_.consumerId),
        Consent.isScaFrontEnd(cc.consumer.map(_.consumerId))
      ) should equal(None)
    }
  }

  Feature("Revoking a UK consent takes its access away") {
    Scenario("the granted rows are gone, not merely unreachable", UKConsentScoping) {
      val consentId = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic))
      val (principal, _) = authenticateWith(consentId, testConsumer)
      Views.views.vend.accessGrantedToUserForConsumer(principal, Constant.ALL_CONSUMERS) should not be empty

      Consents.consentProvider.vend.revoke(consentId)

      // Revocation used to flip a status column and leave the granted rows in the table for good.
      Views.views.vend.accessGrantedToUserForConsumer(principal, Constant.ALL_CONSUMERS) shouldBe empty
    }

    // The clean-up runs after conditionalRevoke has already committed the status change, so a throw
    // in it does not undo the revoke -- it only hides that the revoke happened. The caller gets a
    // 500 and the retry gets ConsentAlreadyRevoked, which leaves an AISP unable to complete the
    // DELETE the profile requires of it, with no correct next step. The Berlin Group sibling a few
    // lines away in MappedConsent already wraps its equivalent; this one did not.
    //
    // A stored JWT whose payload does not extract is enough to get there. getSignedPayloadAsJson
    // does not verify the signature -- it parses the structure and hands back the claims -- so the
    // extract that follows is what throws, and Box.map does not catch. The same trap is already
    // documented on applyUKConsentPrincipalFromToken.
    Scenario("a consent whose stored JWT cannot be read is still revoked, and says so", UKConsentScoping) {
      val consentId = authoriseConsentFor(testConsumer.consumerId, List(ReadAccountsBasic))

      // Structurally a JWT, and the claims parse as JSON -- they are simply not a ConsentJWT.
      def b64(s: String) = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(s.getBytes("UTF-8"))
      Consents.consentProvider.vend.setJsonWebToken(
        consentId, s"${b64("""{"alg":"HS256"}""")}.${b64("""{"not":"a consent"}""")}.signature-is-never-checked")

      val revoked = Consents.consentProvider.vend.revoke(consentId)

      withClue("the revoke threw rather than reporting the outcome: ") {
        revoked.isDefined should equal(true)
      }
      Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .map(_.status) should equal(Full(ConsentStatus.REVOKED.toString))

      // And it stays revoked: a second attempt is the ConsentAlreadyRevoked the caller used to be
      // pushed into by the failure, rather than a way out of it.
      Consents.consentProvider.vend.revoke(consentId).isDefined should equal(false)
    }
  }

}
