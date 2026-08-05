package code.api.berlin.group.v1_3

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.model.ScaStatusResponse
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.{CallContext, Consent}
import code.api.util.ErrorMessages.{BerlinGroupPsuNotIdentified, ConsentDoesNotMatchConsumer, ConsentDoesNotMatchUser}
import code.consent.{ConsentStatus, Consents}
import code.model.TokenType.Access
import code.token.Tokens
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
  feature("Consent.checkBerlinGroupConsentAccess") {

    scenario("the TPP that lodged an unowned consent may authorise it", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(psu), Some(tpp)) should equal(None)
    }

    scenario("a second TPP may not authorise a consent it did not lodge", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(psu), Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("a PSU-less call may drive a consent its own Consumer lodged", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, None, Some(tpp)) should equal(None)
      Consent.checkBerlinGroupConsentAccess(psu, tpp, None, Some(tpp)) should equal(None)
    }

    scenario("a PSU-less call from a second TPP is still refused", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess("", tpp, None, Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchConsumer))
      Consent.checkBerlinGroupConsentAccess(psu, tpp, None, Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("a PSU-less call with no Consumer at all is refused", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, None, None) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }

    scenario("the PSU a consent is already bound to may re-authorise it", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(psu), Some(tpp)) should equal(None)
    }

    scenario("a different PSU may not re-bind a consent that is already owned", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(otherPsu), Some(tpp)) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    scenario("the PSU check wins over the Consumer once a consent is bound", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(otherPsu), Some(otherTpp)) should
        equal(Some(ConsentDoesNotMatchUser))
    }

    scenario("blank ids count as absent, not as a value to match", BerlinGroupV13ConsentAccess) {
      Consent.checkBerlinGroupConsentAccess(null, null, None, None) should equal(None)
      Consent.checkBerlinGroupConsentAccess("   ", tpp, Some(psu), Some(tpp)) should equal(None)
      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some("  "), Some(tpp)) should equal(None)
    }
  }

  // Pinned rather than left to the scaladoc because the Berlin Group authorisation handlers are
  // being reworked on top of this, and the case that matters there is the one that looks like an
  // absence: per the standard the caller of these endpoints is the TPP, with the PSU's factors
  // travelling in the body rather than in the session, so None is the normal answer for a
  // conforming call -- not a failure to defend against. checkBerlinGroupConsentAccess is written
  // for that: a caller with no PSU skips the PSU comparison and is judged on its Consumer alone.
  feature("Consent.genuinePsu") {

    scenario("a session with no user at all has no PSU", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(CallContext(user = Empty, consumer = Full(testConsumer))) should equal(None)
    }

    scenario("the Consumer's own pseudo-identity is not a PSU", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(
        CallContext(user = Full(pseudoUserOfTestConsumer), consumer = Full(testConsumer))) should equal(None)
    }

    scenario("a real person authenticated in the session is a PSU", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(CallContext(user = Full(resourceUser1), consumer = Full(testConsumer)))
        .map(_.userId) should equal(Some(resourceUser1.userId))
    }

    // Degenerate, and it fails closed rather than open: with no Consumer identified there is no key
    // to compare against, so the pseudo-user survives the filter -- but callerConsumerId is None
    // too, so a bound consent is refused on the PSU half and an unbound one on the Consumer half.
    scenario("with no Consumer on the call there is no key to filter against", BerlinGroupV13ConsentAccess) {
      Consent.genuinePsu(CallContext(user = Full(pseudoUserOfTestConsumer), consumer = Empty))
        .map(_.userId) should equal(Some(pseudoUserOfTestConsumer.userId))

      Consent.checkBerlinGroupConsentAccess(psu, tpp, Some(pseudoUserOfTestConsumer.userId), None) should
        equal(Some(ConsentDoesNotMatchUser))
      Consent.checkBerlinGroupConsentAccess("", tpp, Some(pseudoUserOfTestConsumer.userId), None) should
        equal(Some(ConsentDoesNotMatchConsumer))
    }
  }

  // The interesting cases here are absences -- no PSU in the session, no header, or both -- and an
  // OAuth1-signed test request always attaches a user, so the rule is pinned directly as well as
  // driven over HTTP. Same reasoning as the two blocks above.
  feature("Consent.resolveBerlinGroupPsu") {

    scenario("a consent that already names a PSU answers for itself", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu(psu, None, None) should equal(Right(psu))
      Consent.resolveBerlinGroupPsu(psu, None, Some(psu)) should equal(Right(psu))
    }

    scenario("an unbound consent takes the PSU from the session, which is the Redirect approach", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu("", Some(psu), None) should equal(Right(psu))
    }

    scenario("with no PSU in the session the PSU-ID header names one, which is Embedded", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu("", None, Some(psu)) should equal(Right(psu))
    }

    // Not a defensive branch: a conforming client-credentials call that omitted the header lands
    // here, and there is genuinely no one to mint the challenge for or send the OTP to.
    scenario("with none of the three there is nobody to authorise for", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu("", None, None) should equal(Left(BerlinGroupPsuNotIdentified))
      Consent.resolveBerlinGroupPsu("  ", None, Some("  ")) should equal(Left(BerlinGroupPsuNotIdentified))
    }

    // "the ASPSP might check whether PSU-ID and token match" -- Implementation Guidelines V1.3.12,
    // section 6.3.1, p.134. Refused rather than resolved by precedence: otherwise a lodging TPP
    // could name a third party and have the bound PSU's OTP mailed to them instead.
    scenario("a PSU-ID contradicting what the ASPSP already knows is refused", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu(psu, None, Some(otherPsu)) should equal(Left(ConsentDoesNotMatchUser))
      Consent.resolveBerlinGroupPsu("", Some(psu), Some(otherPsu)) should equal(Left(ConsentDoesNotMatchUser))
    }

    scenario("the consent outranks the session when both are present", BerlinGroupV13ConsentAccess) {
      Consent.resolveBerlinGroupPsu(psu, Some(psu), None) should equal(Right(psu))
    }
  }

  // resourceUser2 under testConsumer: a genuine second PSU of the *lodging* TPP, which is the only
  // way to reach the PSU half of the rule over HTTP -- user2 in the shared fixtures carries
  // testConsumer2, so it would be refused on the Consumer half first.
  private lazy val secondPsuOfTestConsumerToken = Tokens.tokens.vend.createToken(
    Access,
    Some(testConsumer.id.get),
    Some(resourceUser2.id.get),
    Some(randomString(40).toLowerCase),
    Some(randomString(40).toLowerCase),
    Some(tokenDuration),
    Some(TimeSpan(tokenDuration + System.currentTimeMillis())),
    Some(new Date(System.currentTimeMillis())),
    None
  ).openOrThrowException("test second PSU token creation failed")

  private lazy val secondPsuOfTestConsumerSession =
    Some(consumer, Token(secondPsuOfTestConsumerToken.key.get, secondPsuOfTestConsumerToken.secret.get))

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

  feature("BG v1.3 - a consent's authorisation sub-resources answer only to the TPP that lodged it") {

    scenario("A second TPP cannot start an authorisation on a consent it did not lodge", BerlinGroupV13ConsentAccess) {
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

    scenario("A second TPP cannot answer an authorisation the lodging TPP started", BerlinGroupV13ConsentAccess) {
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

    scenario("A second PSU of the lodging TPP cannot re-bind a consent another PSU authorised", BerlinGroupV13ConsentAccess) {
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

  feature("BG v1.3 - the guard does not bite the flows Berlin Group actually describes") {

    scenario("The lodging TPP's PSU completes SCA and the consent is bound to them", BerlinGroupV13ConsentAccess) {
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
    scenario("The lodging TPP may still drive a bound consent on a client-credentials session", BerlinGroupV13ConsentAccess) {
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
  feature("BG v1.3 - an Embedded SCA challenge belongs to the PSU, not to the TPP relaying it") {

    scenario("A client-credentials TPP completes SCA for the PSU it names in PSU-ID", BerlinGroupV13ConsentAccess) {
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

    scenario("With no PSU in the session and no PSU-ID there is nobody to mint the challenge for", BerlinGroupV13ConsentAccess) {
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

    scenario("A PSU-ID the ASPSP cannot resolve is refused", BerlinGroupV13ConsentAccess) {
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
    scenario("A PSU-ID naming someone other than the consent's PSU cannot redirect the OTP", BerlinGroupV13ConsentAccess) {
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
    scenario("A bound consent needs no PSU-ID: the consent itself already names the PSU", BerlinGroupV13ConsentAccess) {
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

    scenario("A challenge minted on one consent cannot be answered on another", BerlinGroupV13ConsentAccess) {
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
  feature("BG v1.3 - the consent authorisation pair accepts a client-credentials caller") {
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
      scenario(s"$name declares UserOrApplication", BerlinGroupV13ConsentAccess) {
        val docs = APIUtil.ResourceDoc.getResourceDocs(
          List(APIUtil.buildOperationId(ConstantsBG.berlinGroupVersion1, name)))
        docs should not be empty
        docs.foreach(_.authMode should equal(APIUtil.UserOrApplication))
      }
    }
  }
}
