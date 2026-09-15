/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v6_0_0

import code.api.RequestHeader
import code.api.util.APIUtil.OAuth._
import code.api.util.Consent
import code.api.util.ErrorMessages._
import code.api.v3_1_0.{ConsentJsonV310, PostConsentChallengeJsonV310}
import code.consumer.Consumers
import code.setup.TestConnectorSetupWithStandardPermissions
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.util.Helpers.randomString
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * Every Consent names exactly one Consumer, and only that Consumer may present it.
 *
 * Two independent gates enforce this, and they key off different inputs -- which is why both are
 * exercised here rather than one standing in for the other:
 *
 *  - `Consent.tppIsConsentHolder` compares consumer *ids*: the Consent's stored consumer_id against
 *    whichever Consumer the request layer resolved. It runs first, before signature, expiry and
 *    status, and refuses with ConsentNotFound so a caller cannot tell a Consent that is not theirs
 *    from one that does not exist.
 *  - `Consent.checkConsumerIsActiveAndMatched` looks the Consumer up by the JWT's `aud` claim and
 *    then compares *credential material from one specific header* -- which header depends on
 *    consumer_validation_method_for_consent. It catches the cases the first gate cannot see: a
 *    Consumer disabled after the Consent was granted, and a caller whose identifying credential is
 *    not the credential the instance validates.
 *
 * The pre-existing consent suites cover the "caller OBP cannot identify" shape (no Consumer-Key, or
 * an unknown one -- both leave CallContext.consumer empty). What they do not cover, and what this
 * suite adds, is a caller that is a different *real, active* Consumer, the grantor/grantee split the
 * portal-creates-a-Consent-for-an-agent flow depends on, and the header asymmetry above.
 */
class ConsentConsumerPinningTest extends V600ServerSetup with TestConnectorSetupWithStandardPermissions {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ConsentPinning extends Tag("ConsentConsumerPinning")

  // Consumer A is the one user1's OAuth credentials sign with; Consumer B is user2's. Both are real
  // and active, which is the point: every existing test of a refused Consent leaves the caller
  // unidentified, so nothing until now has exercised "identified, active, and not the right one".
  private lazy val consumerAKey = user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")
  private lazy val consumerBKey = user2.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")
  private lazy val consumerAId = testConsumer.consumerId.get
  private lazy val consumerBId = testConsumer2.consumerId.get

  private def consumerKeyHeader(key: String) = List((RequestHeader.`Consumer-Key`, key))
  private def consentJwtHeader(jwt: String) = List((RequestHeader.`Consent-JWT`, jwt))

  /** A Consumer of this suite's own, so disabling it or giving it a certificate cannot disturb others. */
  private def createLocalConsumer(name: String, certificate: Option[String] = None) =
    Consumers.consumers.vend.createConsumer(
      key = Some(randomString(40).toLowerCase),
      secret = Some(randomString(40).toLowerCase),
      isActive = Some(true),
      name = Some(name),
      appType = None,
      description = Some(s"$name description"),
      developerEmail = Some("eveline@example.com"),
      redirectURL = None,
      createdByUserId = Some(resourceUser1.userId),
      clientCertificate = certificate,
      company = None,
      logoURL = None
    ).openOrThrowException("Could not create the test Consumer")

  private def consentBody(consumerId: Option[String]): JValue = {
    val base: JObject =
      ("everything" -> false) ~
      ("views" -> JArray(Nil)) ~
      ("entitlements" -> JArray(Nil)) ~
      ("time_to_live" -> 3600)
    consumerId.map(id => base ~ ("consumer_id" -> id)).getOrElse(base)
  }

  /**
   * Create a Consent as user1 and answer its challenge, returning the JWT. The creating call always
   * signs as Consumer A -- `consumerId` is who the Consent is *for*, which is a separate thing and
   * is exactly what the grantee scenarios below vary.
   */
  private def acceptedConsentJwt(consumerId: Option[String]): String = {
    setPropsValues("consents.allowed" -> "true", "consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")
    val created = makePostRequest(
      (v6_0_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1),
      write(consentBody(consumerId)), consumerKeyHeader(consumerAKey))
    created.code should equal(201)
    val consent = created.body.extract[ConsentJsonV310]
    val answered = makePostRequest(
      (v5_1_0_Request / "banks" / testBankId1.value / "consents" / consent.consent_id / "challenge").POST <@ (user1),
      write(PostConsentChallengeJsonV310(answer = Consent.challengeAnswerAtTestEnvironment)))
    answered.code should equal(201)
    consent.jwt
  }

  /** Present a Consent against an endpoint that needs no view and no role of its own. */
  private def callAsConsent(jwt: String, extraHeaders: List[(String, String)]) =
    makeGetRequest((v5_1_0_Request / "users" / "current").GET, consentJwtHeader(jwt) ::: extraHeaders)

  feature("A Consent may only be presented by the Consumer it names") {

    scenario("the Consumer the Consent names may present it", VersionOfApi, ConsentPinning) {
      val jwt = acceptedConsentJwt(Some(consumerAId))
      When("Consumer A presents a Consent naming Consumer A")
      val response = callAsConsent(jwt, consumerKeyHeader(consumerAKey))
      Then("We should get a 200")
      response.code should equal(200)
    }

    scenario("a second, active Consumer may not present a Consent naming another", VersionOfApi, ConsentPinning) {
      val jwt = acceptedConsentJwt(Some(consumerAId))
      When("Consumer B presents a Consent naming Consumer A")
      val response = callAsConsent(jwt, consumerKeyHeader(consumerBKey))
      Then("We should get a 401")
      response.code should equal(401)
      And("the refusal must not distinguish a Consent that is not ours from one that does not exist")
      response.body.extract[ErrorMessage].message should include(ConsentNotFound)
    }

    scenario("a missing Consumer-Key is refused by the pin, before the Consumer is validated", VersionOfApi, ConsentPinning) {
      val jwt = acceptedConsentJwt(Some(consumerAId))
      When("the Consent is presented with no Consumer-Key at all")
      val response = callAsConsent(jwt, Nil)
      Then("the pin refuses it first, so the message is ConsentNotFound and not ConsumerKeyHeaderMissing")
      response.body.extract[ErrorMessage].message should include(ConsentNotFound)
      response.body.extract[ErrorMessage].message should not include (ConsumerKeyHeaderMissing)
    }
  }

  feature("A Consent may be created for an application other than the one creating it") {

    // The portal-creates-a-Consent-for-an-agent flow: the caller is the grantor, consumer_id is the
    // grantee, and skip_consent_sca_for_consumer_id_pairs keys off exactly that pair. Without this
    // scenario nothing verifies that the grantee -- rather than the creator -- is what gets pinned.
    scenario("the named application may present it and the creating application may not", VersionOfApi, ConsentPinning) {
      When("Consumer A creates a Consent naming Consumer B")
      val jwt = acceptedConsentJwt(Some(consumerBId))

      Then("Consumer B may present it")
      callAsConsent(jwt, consumerKeyHeader(consumerBKey)).code should equal(200)

      And("Consumer A, which created it, may not")
      val refused = callAsConsent(jwt, consumerKeyHeader(consumerAKey))
      refused.code should equal(401)
      refused.body.extract[ErrorMessage].message should include(ConsentNotFound)
    }

    scenario("omitting consumer_id names the Consumer making the call", VersionOfApi, ConsentPinning) {
      When("Consumer A creates a Consent with no consumer_id in the body")
      val jwt = acceptedConsentJwt(None)

      Then("the Consent is pinned to Consumer A, which may present it")
      callAsConsent(jwt, consumerKeyHeader(consumerAKey)).code should equal(200)

      And("Consumer B may not -- an omitted consumer_id is not a wildcard")
      val refused = callAsConsent(jwt, consumerKeyHeader(consumerBKey))
      refused.code should equal(401)
      refused.body.extract[ErrorMessage].message should include(ConsentNotFound)
    }
  }

  feature("The Consumer a Consent names must still be active, and must be the one the instance validates") {

    // The refusal is the generic ConsumerIsDisabled, not the consent-specific ConsumerAtConsentDisabled,
    // and that is not a slip in the assertion. checkConsumerIsActiveAndMatched does produce
    // ConsumerAtConsentDisabled here, but AfterApiAuth.checkConsumerIsDisabled runs afterwards as common
    // post-authentication code (APIUtil.getUserAndSessionContextFuture) and replaces any result whose
    // CallContext carries a disabled Consumer. Since tppIsConsentHolder only lets the call through when
    // the caller *is* the Consent's Consumer, that Consumer is always the one on the CallContext, so the
    // generic check always wins. ConsumerAtConsentDisabled survives only for Consents whose stored
    // consumer_id and JWT aud disagree -- i.e. rows created before the consumer pin was made mandatory.
    // Asserting the message that actually ships is the point: a test written against the unreachable one
    // would pass for the wrong reason the day the ordering changed.
    scenario("a Consent whose Consumer is disabled afterwards is refused", VersionOfApi, ConsentPinning) {
      val disabledLater = createLocalConsumer("consent-pinning-disabled-later")
      val jwt = acceptedConsentJwt(Some(disabledLater.consumerId.get))

      When("the Consent's Consumer is disabled after the Consent was granted")
      Consumers.consumers.vend.updateConsumer(disabledLater.id.get, isActive = Some(false))

      Then("the pin still passes -- the ids match -- but the disabled Consumer is refused")
      val response = callAsConsent(jwt, consumerKeyHeader(disabledLater.key.get))
      response.body.extract[ErrorMessage].message should include(ConsumerIsDisabled)
    }

    // The two gates read different inputs. A caller identified by its QSealC passes the pin, because
    // the pin only compares consumer ids -- but CONSUMER_CERTIFICATE validates the QWAC in PSD2-CERT,
    // which this caller never sent. Without this scenario nothing shows that the second gate is a real
    // gate rather than a restatement of the first.
    scenario("a caller identified by TPP-Signature-Certificate is refused when the instance validates PSD2-CERT",
      VersionOfApi, ConsentPinning) {
      val certificate = s"-----BEGIN CERTIFICATE-----CONSENTPINNINGQSEALC${randomString(20)}-----END CERTIFICATE-----"
      val certConsumer = createLocalConsumer("consent-pinning-qsealc", Some(certificate))
      val jwt = acceptedConsentJwt(Some(certConsumer.consumerId.get))

      When("the instance validates the MTLS certificate but the caller only sent its signing certificate")
      setPropsValues("consumer_validation_method_for_consent" -> "CONSUMER_CERTIFICATE")
      val response = try {
        callAsConsent(jwt, List((RequestHeader.`TPP-Signature-Certificate`, certificate)))
      } finally setPropsValues("consumer_validation_method_for_consent" -> "CONSUMER_KEY_VALUE")

      Then("the pin passes on the resolved Consumer, and the certificate comparison refuses it")
      response.body.extract[ErrorMessage].message should include(ConsentDoesNotMatchConsumer)
    }

    // Same asymmetry the other way round: the certificate wins when resolving the caller
    // (consumerByCertificate.orElse(consumerByConsumerKey)), so the Consumer-Key naming a different
    // application is not what identified this call -- but under CONSUMER_KEY_VALUE it is what gets
    // compared.
    scenario("a caller identified by certificate is refused when its Consumer-Key names a different Consumer",
      VersionOfApi, ConsentPinning) {
      val certificate = s"-----BEGIN CERTIFICATE-----CONSENTPINNINGQWAC${randomString(20)}-----END CERTIFICATE-----"
      val certConsumer = createLocalConsumer("consent-pinning-qwac", Some(certificate))
      val jwt = acceptedConsentJwt(Some(certConsumer.consumerId.get))

      When("the caller presents the certificate of the named Consumer and the Consumer-Key of another")
      val response = callAsConsent(jwt,
        List((RequestHeader.`PSD2-CERT`, certificate)) ::: consumerKeyHeader(consumerAKey))

      Then("the pin passes on the certificate-resolved Consumer, and the key comparison refuses it")
      response.body.extract[ErrorMessage].message should include(ConsentDoesNotMatchConsumer)
    }
  }
}
