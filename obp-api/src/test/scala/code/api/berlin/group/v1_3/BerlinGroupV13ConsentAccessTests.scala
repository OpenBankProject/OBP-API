package code.api.berlin.group.v1_3

import code.api.berlin.group.ConstantsBG
import org.json4s.jvalue2extractable
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.model.ScaStatusResponse
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.{CallContext, Consent}
import code.api.util.ErrorMessages.{BerlinGroupPsuNotIdentified, ConsentDoesNotMatchConsumer, ConsentDoesNotMatchUser}
import code.consent.{ConsentStatus, Consents}
import code.model.TokenType.Access
import code.token.Tokens
import code.userlocks.UserLocksProvider
import code.transactionChallenge.Challenges
import net.liftweb.common.{Empty, Full}
import net.liftweb.util.Helpers.randomString
import net.liftweb.util.TimeHelpers.TimeSpan
import org.scalatest.Tag

import java.util.Date

/**
 * Who may drive a Berlin Group consent's authorisation sub-resources.
 *
 * Two endpoints decide who a consent ends up belonging to: POST /consents/CONSENTID/authorisations
 * mints the SCA challenge, and PUT .../AUTHORISATIONID answers it and writes the PSU onto the
 * consent row. Neither had any ownership guard, so any authenticated caller could raise a challenge
 * on any consent id and then answer their own -- claiming a consent lodged by a different TPP, or
 * re-binding one another PSU had already authorised, since updateConsentUser overwrites mUserId
 * unconditionally.
 *
 * The rule and its derivation from the standard live in Consent.checkBerlinGroupConsentAccess. This
 * suite pins both halves of it, and pins the two ways it must NOT bite: the lodging TPP's own PSU
 * completing SCA, and the lodging TPP polling on a client-credentials session, where cc.user is an
 * auto-vivified pseudo-user rather than a person.
 */
class BerlinGroupV13ConsentAccessTests extends BerlinGroupConsentFixtures {

  object BerlinGroupV13ConsentAccess extends Tag("BerlinGroupV13ConsentAccess")

  private val psu = "psu-user-id"
  private val otherPsu = "someone-else-user-id"
  private val tpp = "lodging-consumer-id"
  private val otherTpp = "second-consumer-id"

  // The rule is unit-tested as well as driven over HTTP because the interesting caller shapes -- a
  // session with no PSU at all -- cannot be produced by an OAuth1-signed test request, which always
  // attaches a user. Same reasoning as UKOpenBankingV401ConsentAccessTests.
  Feature("Consent.checkBerlinGroupConsentAccess") {

    Scenario("the TPP that lodged an unowned consent may authorise it", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(psu), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    Scenario("a second TPP may not authorise a consent it did not lodge", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    Scenario("a PSU-less call may drive a consent its own Consumer lodged", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, None, Some(tpp), callerIsScaFrontEnd = false) should equal(None)
      Consent.checkBerlinGroupConsentAccess(psu, tpp, None, Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    Scenario("a PSU-less call from a second TPP is still refused", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, None, Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkBerlinGroupConsentAccess(psu, tpp, None, Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    Scenario("a PSU-less call with no Consumer at all is refused", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, None, None, callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    Scenario("the PSU a consent is already bound to may re-authorise it", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(psu), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    Scenario("a different PSU may not re-bind a consent that is already owned", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(otherPsu), Some(tpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    Scenario("the PSU check wins over the Consumer once a consent is bound", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(otherPsu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    // Matching the PSU is necessary, not sufficient. It used to be sufficient: the rule returned
    // None the moment the two PSU ids agreed, without ever looking at the Consumer, so a second TPP
    // holding a session for the same person could read and revoke the consent a first TPP lodged.
    // One TPP's mandate over a consent is not another's -- the same principle the payment guard in
    // Http4sBGv13PIS states, and IG 4.11's "may only apply to resources which have been created by
    // the same TPP before" admits no PSU exception.
    Scenario("a second TPP holding a session for the consent's own PSU is still refused", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    Scenario("blank ids count as absent, not as a value to match", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("   ", tpp, Some(psu), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some("  "), Some(tpp), callerIsScaFrontEnd = false) should equal(None)
    }

    // This assertion is inverted from what it used to say. It read
    //   checkBerlinGroupConsentAccess(null, null, None, None, false) should equal(None)
    // which pinned a consent recording no TPP as addressable by anybody -- "absent" was being read
    // as "matches everything" rather than as "there is nobody this belongs to". Neither standard
    // supports that: IG 4.11 scopes a resource to the TPP that created it, and UK scopes GET and
    // DELETE to "an account-access-consent resource that they have created". A row naming no TPP
    // satisfies neither, so it belongs to nobody and is refused.
    //
    // It is a real population, not a hypothetical: 10 of 566 Berlin Group consents and 4 of 753 UK
    // consents record no consumer on a long-lived instance. They are already unreachable in Berlin
    // Group today, by accident -- the hand-rolled `null == "None"` compare in the five reads is
    // false for everyone -- so refusing them here changes nothing for those callers and only stops
    // the UK pair, and the reads once they move onto this rule, from opening up.
    Scenario("a consent that records no lodging TPP belongs to nobody", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(null, null, None, None, callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkBerlinGroupConsentAccess(null, "  ", Some(psu), Some(tpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    Scenario("an operator can restore the old behaviour for a migration window", BerlinGroupV13ConsentAccess) {
      setPropsValues("consent_allow_legacy_unrecorded_tpp" -> "true")
      Consent.checkBerlinGroupConsentAccess(null, null, None, None, callerIsScaFrontEnd = false) should equal(None)
    }
  }

  // The Redirect approach: the PSU authenticates at the ASPSP, so these calls arrive from the
  // ASPSP's own front end under its own Consumer -- never the one that lodged the consent. The
  // same-TPP rule would refuse the only caller Redirect has, which is what blocked the scaRedirect
  // ceremony outright. Nothing in the request separates that front end from a second TPP holding a
  // PSU session, so it is declared rather than inferred; these pin that the declaration is the only
  // thing that changes, and that it does not reach the PSU half.
  Feature("Consent.checkBerlinGroupConsentAccess and the ASPSP's declared SCA front end") {

    Scenario("a declared front end may start an authorisation on a consent it did not lodge", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = true) should
        equal(None)
    }

    Scenario("a declared front end still cannot re-bind another PSU's consent", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(otherPsu), Some(otherTpp), callerIsScaFrontEnd = true) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    Scenario("a declared front end acting for the consent's own PSU is fine", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(psu), Some(otherTpp), callerIsScaFrontEnd = true) should
        equal(None)
    }

    Scenario("the declaration is by consumer id and nothing else", BerlinGroupV13ConsentAccess) {
      // Empty config is the default, and it must leave the same-TPP rule applying to everyone.
      Consent.isScaFrontEnd(Some(otherTpp)) should equal(false)
      Consent.isScaFrontEnd(None) should equal(false)
      Consent.isScaFrontEnd(Some("   ")) should equal(false)
    }
  }

  // Pinned rather than left to the scaladoc because the Berlin Group authorisation handlers are
  // being reworked on top of this, and the case that matters there is the one that looks like an
  // absence: per the standard the caller of these endpoints is the TPP, with the PSU's factors
  // travelling in the body rather than in the session, so None is the normal answer for a
  // conforming call -- not a failure to defend against. checkBerlinGroupConsentAccess is written
  // for that: a caller with no PSU skips the PSU comparison and is judged on its Consumer alone.
  // A refusal tells the TPP which view and which account it was refused, and that is all it
  // should tell them. Under consent authentication the principal is the consent's own shadow
  // user, so putting its id in the message hands the TPP an internal identifier it cannot act
  // on and was never party to.
  Feature("BG v1.3 - a view refusal does not disclose the internal principal") {
    Scenario("the refusal names the view and the account, and no user id", BerlinGroupV13ConsentAccess) {
      // user2 holds no Berlin Group view on testAccountId1, so this is a real refusal.
      val response = makeGetRequest((V1_3_BG / "accounts" / testAccountId1.value / "balances").GET <@ (user2))
      response.code should equal(403)
      val body = response.body.toString
      body should include("OBP-20060")
      body should not include "userId :"
      body should not include resourceUser2.userId
    }
  }

  // The PSU-ID header names a person the ASPSP then acts for: it resolves to a user, that user is
  // checked against the consent's accounts, an SCA challenge is minted for them and an OTP goes out
  // to them, and the PUT twin binds the consent to them. None of that authenticates the PSU -- under
  // Berlin Group the caller is the TPP -- so the guard every authenticated request gets from
  // AfterApiAuth.checkUserIsDeletedOrLocked never runs on this path. A lock says the ASPSP has
  // decided this user may not authenticate; resolving them here anyway routes around that decision,
  // and every later check passes because the accounts really are theirs.
  Feature("Consent.findPsuByPsuId only resolves a user who may still act") {

    Scenario("a live user resolves", BerlinGroupV13ConsentAccess) {
      Consent.findPsuByPsuId(resourceUser1.name).map(_.userId) should equal(Full(resourceUser1.userId))
    }

    Scenario("a locked user does not resolve", BerlinGroupV13ConsentAccess) {
      UserLocksProvider.lockUser(resourceUser2.provider, resourceUser2.name)
      try {
        Consent.findPsuByPsuId(resourceUser2.name) should equal(Empty)
      } finally {
        UserLocksProvider.unlockUser(resourceUser2.provider, resourceUser2.name)
      }
      And("unlocking puts them back")
      Consent.findPsuByPsuId(resourceUser2.name).map(_.userId) should equal(Full(resourceUser2.userId))
    }

    // Empty rather than a distinct "this user is locked": resolvePsuIdHeader turns an unresolved
    // header into UserNotFoundByProviderAndUsername at 401, and a locked user must get that same
    // answer. Telling the two apart would hand a TPP a way to confirm that a username exists, which
    // is the oracle the consent reads were unified to close.
    Scenario("the refusal does not say which of the two it was", BerlinGroupV13ConsentAccess) {
      Consent.findPsuByPsuId("no-such-user-at-all") should equal(Empty)
    }
  }

  Feature("Consent.genuinePsu") {

    Scenario("a session with no user at all has no PSU", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(CallContext(user = Empty, consumer = Full(testConsumer))) should equal(None)
    }

    Scenario("the Consumer's own pseudo-identity is not a PSU", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(
        CallContext(user = Full(pseudoUserOfTestConsumer), consumer = Full(testConsumer))) should equal(None)
    }

    Scenario("a real person authenticated in the session is a PSU", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(CallContext(user = Full(resourceUser1), consumer = Full(testConsumer)))
        .map(_.userId) should equal(Some(resourceUser1.userId))
    }

    // Degenerate, and it fails closed rather than open: with no Consumer identified there is no key
    // to compare against, so the pseudo-user survives the filter -- but callerConsumerId is None
    // too, so a bound consent is refused on the PSU half and an unbound one on the Consumer half.
    Scenario("with no Consumer on the call there is no key to filter against", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(CallContext(user = Full(pseudoUserOfTestConsumer), consumer = Empty))
        .map(_.userId) should equal(Some(pseudoUserOfTestConsumer.userId))

      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(pseudoUserOfTestConsumer.userId), None, callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchUser))
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(pseudoUserOfTestConsumer.userId), None, callerIsScaFrontEnd = false) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }
  }

  // The interesting cases here are absences -- no PSU in the session, no header, or both -- and an
  // OAuth1-signed test request always attaches a user, so the rule is pinned directly as well as
  // driven over HTTP. Same reasoning as the two blocks above.
  Feature("Consent.resolveBerlinGroupPsu") {

    Scenario("a consent that already names a PSU answers for itself", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu(psu, None, None) should equal(Right(psu))
      Consent.resolveBerlinGroupPsu(psu, None, Some(psu)) should equal(Right(psu))
    }

    Scenario("an unbound consent takes the PSU from the session, which is the Redirect approach", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu("", Some(psu), None) should equal(Right(psu))
    }

    Scenario("with no PSU in the session the PSU-ID header names one, which is Embedded", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu("", None, Some(psu)) should equal(Right(psu))
    }

    // Not a defensive branch: a conforming client-credentials call that omitted the header lands
    // here, and there is genuinely no one to mint the challenge for or send the OTP to.
    Scenario("with none of the three there is nobody to authorise for", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu("", None, None) should equal(Left(BerlinGroupPsuNotIdentified))
      Consent.resolveBerlinGroupPsu("  ", None, Some("  ")) should equal(Left(BerlinGroupPsuNotIdentified))
    }

    // "the ASPSP might check whether PSU-ID and token match" -- Implementation Guidelines V1.3.12,
    // section 6.3.1, p.134. Refused rather than resolved by precedence: otherwise a lodging TPP
    // could name a third party and have the bound PSU's OTP mailed to them instead.
    Scenario("a PSU-ID contradicting what the ASPSP already knows is refused", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu(psu, None, Some(otherPsu)) should equal(Left(ConsentDoesNotMatchUser))
      Consent.resolveBerlinGroupPsu("", Some(psu), Some(otherPsu)) should equal(Left(ConsentDoesNotMatchUser))
    }

    Scenario("the consent outranks the session when both are present", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu(psu, Some(psu), None) should equal(Right(psu))
    }
  }

  // resourceUser2 under testConsumer: a genuine second PSU of the *lodging* TPP, which is the only
  // way to reach the PSU half of the rule over HTTP -- user2 in the shared fixtures carries
  // testConsumer2, so it would be refused on the Consumer half first.
  private lazy val secondPsuOfTestConsumerToken = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id),
    Some(resourceUser2.id),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException("test second PSU token creation failed")

  private lazy val secondPsuOfTestConsumerSession =
    Some(consumer, Token(secondPsuOfTestConsumerToken.key, secondPsuOfTestConsumerToken.secret))

  private def startAuthorisation(
    consentId: String,
    session: Option[(Consumer, Token)],
    headers: List[(String, String)] = Nil
  ) =
    makePostRequest(
      (V1_3_BG / "consents" / consentId / "authorisations").POST <@ (session),
      """{"scaAuthenticationData":""}""",
      headers)

  private def answerAuthorisation(
    consentId: String,
    authorisationId: String,
    session: Option[(Consumer, Token)],
    otp: String,
    headers: List[(String, String)] = Nil
  ) =
    makePutRequest(
      (V1_3_BG / "consents" / consentId / "authorisations" / authorisationId).PUT <@ (session),
      s"""{"scaAuthenticationData":"$otp"}""",
      headers: _*)

  private def psuIdHeader(userName: String) = List(("PSU-ID", userName))

  // The five reads of a consent resource. They each used to compare
  //   consent.mConsumerId.get == cc.consumer.map(_.consumerId.get).getOrElse("None")
  // by hand, five separate times, rather than going through checkBerlinGroupConsentAccess like the
  // authorisation pair above. Five copies of one security decision is how the two fail-opens fixed
  // in the previous commit came to be fixed in the rule and not at these call sites: a hand-rolled
  // Consumer compare cannot notice that the PSU half exists at all.
  private def consentReads(consentId: String, session: Option[(Consumer, Token)]) = List(
    "read the consent" -> makeGetRequest((V1_3_BG / "consents" / consentId).GET <@ (session)),
    "read its status" -> makeGetRequest((V1_3_BG / "consents" / consentId / "status").GET <@ (session)),
    "list its authorisations" -> makeGetRequest((V1_3_BG / "consents" / consentId / "authorisations").GET <@ (session)),
    "read an authorisation's SCA status" ->
      makeGetRequest((V1_3_BG / "consents" / consentId / "authorisations" / "any-authorisation-id").GET <@ (session))
  )

  Feature("BG v1.3 - the consent reads apply the same ownership rule as the authorisation pair") {

    // A caller not entitled to a consent must not be able to tell "there is no such consent" from
    // "that one is not yours", or the endpoint confirms which ids are real. Four of these five reads
    // were unified to a bare ConsentNotFound; getConsentScaStatus kept spelling the id back, because
    // the replacement matched the default-status-code spelling and this site already passed 403
    // explicitly. Same status either way, different body -- so the oracle survived at one endpoint.
    Scenario("every read answers a missing consent exactly as it answers a foreign one", BerlinGroupV13ConsentAccess) {
      val someoneElses = createUnclaimedBerlinGroupConsent().consentId
      val missing = "no-such-consent-at-all"

      // Status and the tppMessages entry, not the whole body: the Berlin Group error envelope
      // carries a `path` field holding the request path, so it always contains whichever id the
      // caller themselves put in the URL. That is not a leak -- they already knew it -- and
      // comparing it would make this scenario impossible to satisfy for the wrong reason.
      def answers(consentId: String) = consentReads(consentId, user2).map { case (what, r) =>
        val message = r.body.extract[ErrorMessagesBG].tppMessages.head
        what -> (r.code, message.code, message.text)
      }

      answers(someoneElses).zip(answers(missing)).foreach { case ((what, foreign), (_, absent)) =>
        withClue(s"'$what' tells a foreign consent from a missing one: ") {
          foreign should equal(absent)
        }
      }

      And("and the message names no consent")
      answers(missing).foreach { case (what, (_, _, text)) =>
        withClue(s"'$what' echoed the id: ") {
          text should not include missing
        }
      }
    }

    Scenario("the lodging TPP acting for a second PSU cannot read a consent bound to the first", BerlinGroupV13ConsentAccess) {
      val consentId = createUnclaimedBerlinGroupConsent().consentId
      Consents.consentProvider.vend.updateConsentUser(consentId, resourceUser1)

      Then("the same TPP, but acting for a different person, is refused on every read")
      // The Consumer matches -- this IS the TPP that lodged it -- so a Consumer-only compare says
      // yes. The consent is bound to resourceUser1 and the session is resourceUser2's, and once a
      // consent names a PSU the rule requires that PSU, which is what the authorisation pair has
      // enforced all along and these reads did not.
      consentReads(consentId, secondPsuOfTestConsumerSession).foreach { case (what, response) =>
        withClue(s"a second PSU of the lodging TPP was allowed to $what: ") {
          response.code should equal(403)
        }
      }

      And("the lodging TPP on a client-credentials session still can")
      // The normal Berlin Group caller: no genuine PSU in the session at all, so the PSU half does
      // not apply and the Consumer decides. This is the half that must not regress.
      consentReads(consentId, clientCredentialsSession).foreach { case (what, response) =>
        withClue(s"the lodging TPP could not $what: ") {
          response.code should equal(200)
        }
      }

      And("so does DELETE, which is the fifth site and mutates, hence last")
      makeDeleteRequest((V1_3_BG / "consents" / consentId).DELETE <@ (secondPsuOfTestConsumerSession))
        .code should equal(403)
      makeDeleteRequest((V1_3_BG / "consents" / consentId).DELETE <@ (clientCredentialsSession))
        .code should equal(204)
    }
  }

  Feature("BG v1.3 - a consent's authorisation sub-resources answer only to the TPP that lodged it") {

    Scenario("A second TPP cannot start an authorisation on a consent it did not lodge", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      Then("user2 carries testConsumer2, which did not lodge this consent")
      val response = startAuthorisation(consentId, user2)
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should include(ConsentDoesNotMatchConsumer)

      Then("The consent is untouched: no PSU was bound and no challenge was minted for the caller")
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed")
      Option(consent.userId).forall(_.isBlank) should be (true)
      consent.status should be (ConsentStatus.received.toString)
    }

    Scenario("A second TPP cannot answer an authorisation the lodging TPP started", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      val started = startAuthorisation(consentId, user1)
      started.code should equal(201)
      val authorisationId = started.body.extract[StartConsentAuthorisationJson].authorisationId

      Then("Answering it from a second TPP's session is refused before the OTP is even checked")
      val response = answerAuthorisation(consentId, authorisationId, user2, "123")
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should include(ConsentDoesNotMatchConsumer)

      Then("The consent stays unclaimed")
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed")
      Option(consent.userId).forall(_.isBlank) should be (true)
    }

    Scenario("A second PSU of the lodging TPP cannot re-bind a consent another PSU authorised", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      Then("resourceUser1 authorises it")
      val started = startAuthorisation(consentId, user1)
      started.code should equal(201)
      val authorisationId = started.body.extract[StartConsentAuthorisationJson].authorisationId
      answerAuthorisation(consentId, authorisationId, user1, "123").code should equal(200)
      Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed").userId should be (resourceUser1.userId)

      Then("resourceUser2, on the same Consumer, is refused on the PSU half of the rule")
      val response = startAuthorisation(consentId, secondPsuOfTestConsumerSession)
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should include(ConsentDoesNotMatchUser)

      Then("The consent still belongs to the PSU that authorised it")
      Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed").userId should be (resourceUser1.userId)
    }
  }

  Feature("BG v1.3 - the guard does not bite the flows Berlin Group actually describes") {

    Scenario("The lodging TPP's PSU completes SCA and the consent is bound to them", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      val started = startAuthorisation(consentId, user1)
      started.code should equal(201)
      val authorisationId = started.body.extract[StartConsentAuthorisationJson].authorisationId

      val answered = answerAuthorisation(consentId, authorisationId, user1, "123")
      answered.code should equal(200)
      answered.body.extract[ScaStatusResponse].scaStatus should be ("valid")

      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed")
      consent.userId should be (resourceUser1.userId)
      consent.status should be (ConsentStatus.valid.toString)
    }

    // The discriminating case for Consent.genuinePsu. A client_credentials token resolves cc.user to
    // a pseudo-user keyed on the caller's own client key, not to a person. Compare that against the
    // consent's real owner and a legitimate TPP poll on its own bound consent turns into a 403 --
    // which is exactly what Berlin Group's Redirect approach does, the PSU having authenticated at
    // the ASPSP rather than through the TPP.
    Scenario("The lodging TPP may still drive a bound consent on a client-credentials session", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      val started = startAuthorisation(consentId, user1)
      started.code should equal(201)
      val authorisationId = started.body.extract[StartConsentAuthorisationJson].authorisationId
      answerAuthorisation(consentId, authorisationId, user1, "123").code should equal(200)

      Then("A client-credentials session of the same Consumer is not mistaken for a foreign PSU")
      val response = startAuthorisation(consentId, clientCredentialsSession)
      response.code should equal(201)
    }
  }

  /**
   * Embedded SCA: the TPP calls, the PSU is named in the PSU-ID header.
   *
   * These endpoints used to take the challenge's owner from the session principal. Under a
   * client_credentials token that principal is the TPP's own auto-vivified pseudo-user, and the
   * challenge answer is delivered to whoever the challenge names -- createChallengeInternal mails it
   * to getEmailsByUserId(userId) -- so the OTP went to the TPP and the consent bound to the TPP.
   *
   * The standard puts the PSU's identity in the PSU-ID header: "Shall be transmitted if this Request
   * is indicated by startAuthorisationWithPsuIdentification ... and this field has not yet been
   * transmitted before" (Implementation Guidelines V1.3.12, section 7.1 Start Authorisation Process,
   * p.195). It is not in the body: the psuData object carries passwords only and no identifier at
   * all.
   */
  Feature("BG v1.3 - an Embedded SCA challenge belongs to the PSU, not to the TPP relaying it") {

    // The refusal has to land before the challenge is minted, not after. Starting an authorisation
    // sends an OTP to the person PSU-ID names, out of band -- so a locked user being resolvable here
    // means the ASPSP messages somebody it has already decided may not authenticate, and the TPP is
    // one answered code away from binding their accounts to a consent.
    Scenario("A locked PSU named in PSU-ID gets no challenge at all", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      UserLocksProvider.lockUser(resourceUser1.provider, resourceUser1Name)
      val refused =
        try startAuthorisation(consentId, clientCredentialsSession, psuIdHeader(resourceUser1Name))
        finally UserLocksProvider.unlockUser(resourceUser1.provider, resourceUser1Name)

      Then("it is refused as an unresolvable PSU-ID, saying nothing about the lock")
      refused.code should equal(401)

      And("no challenge was minted, so nobody was messaged")
      Challenges.ChallengeProvider.vend.getChallengesByConsentId(consentId) match {
        case Full(challenges) => challenges shouldBe empty
        case _                => // no rows at all is the same answer
      }
    }

    // assertBerlinGroupConsentAccountsHeld had no test of its own. These give it one, and the third
    // is the point: it read a JWT it could not parse as "this consent names no accounts", which is a
    // legitimate state (the availableAccounts shape) rather than an error, so the guard passed on a
    // consent it had learned nothing about.
    //
    // Reaching that needs a STRUCTURALLY invalid JWT. A well-formed one carrying the wrong payload
    // throws inside Box.map instead and already fails closed with a 500. An empty string is the
    // shape a real instance produces: createConsent writes the consent row before computing and
    // storing the JWT, so a consent whose JWT generation failed persists with none.
    Scenario("the accounts-held guard refuses a PSU who does not hold the consent's accounts", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      Then("the PSU who holds the named account may start the authorisation")
      startAuthorisation(consentId, clientCredentialsSession, psuIdHeader(resourceUser1Name)).code should equal(201)

      And("a PSU who does not hold it may not")
      val refused = startAuthorisation(consentId, clientCredentialsSession, psuIdHeader(resourceUser2.name))
      refused.code should equal(403)
    }

    Scenario("a consent whose JWT cannot be read grants nobody the benefit of the doubt", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId
      Consents.consentProvider.vend.setJsonWebToken(consentId, "")

      When("a PSU who does not hold the consent's accounts starts an authorisation")
      val response = startAuthorisation(consentId, clientCredentialsSession, psuIdHeader(resourceUser2.name))

      Then("it is refused, rather than passing because the account list came back empty")
      response.code should not equal 201

      And("no challenge was minted for them")
      Challenges.ChallengeProvider.vend.getChallengesByConsentId(consentId) match {
        case Full(challenges) => challenges shouldBe empty
        case _                => // no rows at all is the same answer
      }
    }

    Scenario("A client-credentials TPP completes SCA for the PSU it names in PSU-ID", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      Then("The TPP starts the authorisation on its own client-credentials session")
      val started = startAuthorisation(consentId, clientCredentialsSession, psuIdHeader(resourceUser1Name))
      started.code should equal(201)
      val authorisationId = started.body.extract[StartConsentAuthorisationJson].authorisationId

      Then("The challenge is minted for the named PSU, so the OTP reaches them and not the TPP")
      Challenges.ChallengeProvider.vend.getChallenge(authorisationId)
        .openOrThrowException("test challenge lookup failed")
        .expectedUserId should be (resourceUser1.userId)

      Then("The TPP relays the PSU's OTP and the consent binds to the PSU")
      val answered = answerAuthorisation(consentId, authorisationId, clientCredentialsSession, "123")
      answered.code should equal(200)
      answered.body.extract[ScaStatusResponse].scaStatus should be ("valid")

      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed")
      consent.userId should be (resourceUser1.userId)
      consent.status should be (ConsentStatus.valid.toString)
    }

    Scenario("With no PSU in the session and no PSU-ID there is nobody to mint the challenge for", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      val response = startAuthorisation(consentId, clientCredentialsSession)
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.code should be ("PSU_CREDENTIALS_INVALID")

      Then("The consent is untouched")
      val consent = Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed")
      Option(consent.userId).forall(_.isBlank) should be (true)
      consent.status should be (ConsentStatus.received.toString)
    }

    Scenario("A PSU-ID the ASPSP cannot resolve is refused", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      val response = startAuthorisation(consentId, clientCredentialsSession, psuIdHeader("no-such-psu-at-this-aspsp"))
      response.code should equal(401)
      response.body.extract[ErrorMessagesBG].tppMessages.head.code should be ("PSU_CREDENTIALS_INVALID")

      Option(Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed").userId).forall(_.isBlank) should be (true)
    }

    // "It might be contained even if an OAuth2 based authentication was performed in a pre-step. In
    // this case the ASPSP might check whether PSU-ID and token match, according to ASPSP
    // documentation" -- Implementation Guidelines V1.3.12, section 6.3.1, p.134. Taken up here, and
    // extended to the consent's own PSU, which is the same fact recorded a step earlier.
    Scenario("A PSU-ID naming someone other than the consent's PSU cannot redirect the OTP", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      Then("resourceUser1 authorises it")
      val started = startAuthorisation(consentId, user1)
      started.code should equal(201)
      answerAuthorisation(
        consentId, started.body.extract[StartConsentAuthorisationJson].authorisationId, user1, "123"
      ).code should equal(200)

      Then("The lodging TPP may not now name a different PSU")
      val response = startAuthorisation(consentId, clientCredentialsSession, psuIdHeader(resourceUser2Name))
      response.code should equal(403)
      response.body.extract[ErrorMessagesBG].tppMessages.head.text should include(ConsentDoesNotMatchUser)

      Consents.consentProvider.vend.getConsentByConsentId(consentId)
        .openOrThrowException("test consent lookup failed").userId should be (resourceUser1.userId)
    }

    // The consent already records who it belongs to, which is the "not yet contained in a pre-ceeding
    // request" case the standard makes PSU-ID conditional on (section 7.2.1, p.206).
    Scenario("A bound consent needs no PSU-ID: the consent itself already names the PSU", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val consentId = createUnclaimedBerlinGroupConsent().consentId

      val started = startAuthorisation(consentId, user1)
      started.code should equal(201)
      answerAuthorisation(
        consentId, started.body.extract[StartConsentAuthorisationJson].authorisationId, user1, "123"
      ).code should equal(200)

      val restarted = startAuthorisation(consentId, clientCredentialsSession)
      restarted.code should equal(201)
      Challenges.ChallengeProvider.vend
        .getChallenge(restarted.body.extract[StartConsentAuthorisationJson].authorisationId)
        .openOrThrowException("test challenge lookup failed")
        .expectedUserId should be (resourceUser1.userId)
    }

    Scenario("A challenge minted on one consent cannot be answered on another", BerlinGroupV13ConsentAccess) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      val firstConsentId = createUnclaimedBerlinGroupConsent().consentId
      val secondConsentId = createUnclaimedBerlinGroupConsent().consentId

      val started = startAuthorisation(firstConsentId, user1)
      started.code should equal(201)
      val authorisationId = started.body.extract[StartConsentAuthorisationJson].authorisationId

      val response = answerAuthorisation(secondConsentId, authorisationId, user1, "123")
      response.code should equal(400)

      Then("Neither consent was bound")
      Option(Consents.consentProvider.vend.getConsentByConsentId(secondConsentId)
        .openOrThrowException("test consent lookup failed").userId).forall(_.isBlank) should be (true)
    }
  }

  // The GET siblings were brought to UserOrApplication on their own; these two were held back
  // because a doc's auth mode says nothing useful until the handler has an answer to which PSU an
  // authorisation is for. They now do, and the answer does not come from the session, so the docs
  // can state what these calls have always been: the TPP acting as itself.
  //
  // Pinned because nothing else would notice a revert -- these keep working for as long as OAuth2
  // token parsing auto-vivifies a user for a client-credentials token, and start 401ing the day
  // that stops.
  Feature("BG v1.3 - the consent authorisation pair accepts a client-credentials caller") {
    val authorisationDocs = List(
      "startConsentAuthorisationTransactionAuthorisation",
      "startConsentAuthorisationUpdatePsuAuthentication",
      "startConsentAuthorisationSelectPsuAuthenticationMethod",
      "updateConsentsPsuDataTransactionAuthorisation",
      "updateConsentsPsuDataUpdatePsuAuthentication",
      "updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod",
      "updateConsentsPsuDataUpdateAuthorisationConfirmation"
    )
    for (name <- authorisationDocs) {
      Scenario(s"$name declares UserOrApplication", BerlinGroupV13ConsentAccess) {
        val docs = APIUtil.ResourceDoc.getResourceDocs(
          List(APIUtil.buildOperationId(ConstantsBG.berlinGroupVersion1, name)))
        docs should not be empty
        docs.foreach(_.authMode should equal(APIUtil.UserOrApplication))
      }
    }
  }
}
