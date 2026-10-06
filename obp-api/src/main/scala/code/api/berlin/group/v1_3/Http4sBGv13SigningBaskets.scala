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

package code.api.berlin.group.v1_3

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, connectorEmptyResponse, getPropsAsBoolValue, getSuggestedDefaultScaMethod, mockedDataText, passesPsd2Aisp, passesPsd2Pisp, unboxFullOrFail}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.api.util.{ApiTag, CallContext, NewStyle}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.SigningBasketNewStyle
import code.api.util.newstyle.SigningBasketNewStyle.{AuthorisationOperation, CreatorOnly}
import code.bankconnectors.Connector
import code.signingbaskets.{SigningBasketMemberState, SigningBasketX}
import code.util.Helper.{MdcLoggable, booleanToFuture}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.TransactionRequestStatus.{COMPLETED, REJECTED}
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus, SuppliedAnswerType}
import com.openbankproject.commons.model.{ChallengeTrait, SigningBasketTrait, TransactionRequestId}
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4sBGv13SigningBaskets extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  // ResourceDoc example bodies are written as `json.parse(...)` (JValue). Since the json4s
  // migration, JValue itself extends scala.Product, so an implicit JValue => JvalueCaseClass
  // conversion never fires. Each example body is therefore wrapped explicitly in
  // JvalueCaseClass(...) so resource-docs serialization takes its special-case path (no field
  // reflection; the jvalueToCaseclass wrapper key is stripped) instead of reflecting on a raw JObject.

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion1
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV13Prefix = Root / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  /**
   * Berlin Group hangs several request bodies off the authorisation paths (L3653, L3867). Baskets
   * support the two that need no data this ASPSP holds back: an empty body, which starts the
   * authorisation, and `transactionAuthorisation`, which answers it. The others are Embedded-approach
   * steps (PSU authentication, authentication method selection, confirmation code) that are not
   * implemented for any Berlin Group resource here. They are refused by name rather than answered
   * as if the credential or the choice had been processed, and a body that matches no variant is a
   * format error.
   */
  private def requireSupportedAuthorisationBody(rawBody: String, answering: Boolean, failMsg: String, callContext: Option[CallContext]): Future[Boolean] = {
    val parsed = scala.util.Try(json.parse(rawBody)).getOrElse(json.JNothing)
    val supported = if (answering) checkTransactionAuthorisation(parsed) else startsAuthorisation(parsed)
    val knownButUnsupported = !supported && (
      checkUpdatePsuAuthentication(parsed) || checkSelectPsuAuthenticationMethod(parsed) ||
        checkAuthorisationConfirmation(parsed) || (answering && parsed == json.JObject(Nil)))
    for {
      _ <- booleanToFuture(SigningBasketAuthorisationVariantNotSupported, cc = callContext)(!knownButUnsupported)
      _ <- booleanToFuture(failMsg, cc = callContext)(supported)
    } yield true
  }

  // ── POST /signing-baskets ──────────────────────────────────────────────
  val createSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "signing-baskets" =>
      EndpointHelpers.executeFutureCreatedWithHeaders(req) {
        val cc = req.callContext
        val callContext = Some(cc)
        val failMsg = s"$InvalidJsonFormat The Json body should be the $PostSigningBasketJsonV13 "
        for {
          postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[PostSigningBasketJsonV13]
          }
          // The body shall contain at least one entry, and each list that is present at least one id
          // (minItems: 1). A list naming the same id twice is refused as well, rather than silently
          // collapsed, so the TPP learns its request was malformed.
          idLists = List(postJson.paymentIds, postJson.consentIds).flatten
          _ <- booleanToFuture(failMsg, cc = callContext) {
            idLists.nonEmpty && idLists.forall(ids => ids.nonEmpty && ids.distinct.size == ids.size)
          }
          // Which role the TPP needs follows from what it names: payments need PISP, consents AISP, both for a mix.
          _ <- if (postJson.paymentIds.exists(_.nonEmpty)) passesPsd2Pisp(callContext) else Future.successful(())
          _ <- if (postJson.consentIds.exists(_.nonEmpty)) passesPsd2Aisp(callContext) else Future.successful(())
          // The basket belongs to the TPP that creates it; nothing else identifies who may address it later.
          consumerId <- Future.successful(cc.consumer.map(_.consumerId.get))
            .map(unboxFullOrFail(_, callContext, AuthenticatedUserIsRequired, 401))
          // Every member must be one this TPP may address and SCA can still authorise.
          psuUserId <- SigningBasketNewStyle.admitMembers(
            postJson.paymentIds.getOrElse(Nil), postJson.consentIds.getOrElse(Nil), cc, callContext)
          signingBasket <- Future {
            SigningBasketX.signingBasketProvider.vend.createSigningBasket(
              postJson.paymentIds,
              postJson.consentIds,
              consumerId,
              psuUserId
            )
          }.map {
            // A member that another active basket already holds: the standard's REFERENCE_STATUS_INVALID.
            case Failure(SigningBasketMemberStatusInvalid, _, _) =>
              unboxFullOrFail(Empty: Box[SigningBasketTrait], callContext, SigningBasketMemberStatusInvalid, 409)
            case created => connectorEmptyResponse(created, callContext)
          }
        } yield {
          createSigningBasketResponseJson(signingBasket)
        }
      } { created =>
        // Location of the created resource (IG 8.1, Mandatory), under the path the request came in on.
        List("Location" -> s"${req.callContext.url.takeWhile(_ != '?').stripSuffix("/")}/${created.basketId}")
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createSigningBasket),
    "POST",
    "/signing-baskets",
    "Create a signing basket resource",
    s"""${mockedDataText(false)}
Create a signing basket resource for authorising several transactions with one SCA method.
The resource identifications of these transactions are contained in the  payload of this access method
""",
    PostSigningBasketJsonV13(paymentIds = Some(List("123qwert456789", "12345qwert7899")), None),
    JvalueCaseClass(json.parse("""{
  "basketId" : "1234-basket-567",
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : [ "data", "data" ]
  },
  "scaMethods" : "",
  "tppMessages" : [ {
    "path" : "path",
    "code" : { },
    "text" : { },
    "category" : { }
  } ],
  "_links" : {
    "scaStatus" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "status" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "RCVD",
  "psuMessage" : { }
}""")),
    List(AuthenticatedUserIsRequired, InvalidJsonFormat, SigningBasketMemberNotFound, SigningBasketMemberStatusInvalid, SigningBasketMemberMixInvalid, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(createSigningBasket)
  )

  // ── DELETE /signing-baskets/BASKETID ──────────────────────────────────
  val deleteSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `bgV13Prefix` / "signing-baskets" / basketid =>
      EndpointHelpers.executeDelete(req) { cc =>
        val callContext = Some(cc)
        for {
          (basket, _) <- SigningBasketNewStyle.getOwnBasket(basketid, CreatorOnly, callContext)
          // Deleting a basket that is already cancelled changes nothing and is not an error.
          alreadyCancelled = basket.basket.status == ConstantsBG.SigningBasketsStatus.CANC.toString
          _ <- if (alreadyCancelled) Future.successful(true) else for {
            // "As long as no (partial) authorisation has yet been applied" (L3399): the basket must
            // still be RCVD and none of its authorisations may be finalised.
            _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
              basket.basket.status == ConstantsBG.SigningBasketsStatus.RCVD.toString
            }
            (challenges, _) <- NewStyle.function.getChallengesByBasketId(basketid, callContext)
            _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
              !challenges.exists(_.scaStatus.contains(StrongCustomerAuthenticationStatus.finalised))
            }
            // One conditional update. A final answer racing this delete claims the basket first or
            // loses to it, and the loser is told so; never both.
            cancelled <- Future(SigningBasketX.signingBasketProvider.vend.transitionSigningBasketStatus(
              basketid, ConstantsBG.SigningBasketsStatus.RCVD.toString, ConstantsBG.SigningBasketsStatus.CANC.toString))
            _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext)(cancelled.openOr(false))
            // The members are free to join another basket.
            _ <- Future(SigningBasketX.signingBasketProvider.vend.releaseSigningBasketMembers(basketid))
          } yield true
        } yield ()
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteSigningBasket),
    "DELETE",
    "/signing-baskets/BASKETID",
    "Delete the signing basket",
    s"""${mockedDataText(false)}
Delete the signing basket structure as long as no (partial) authorisation has yet been applied.
The underlying transactions are not affected by this deletion.

Only the TPP that created the basket may delete it. A basket that is already cancelled answers 204 again.

Remark: The signing basket as such is not deletable after a first (partial) authorisation has been applied.
Nevertheless, single transactions might be cancelled on an individual basis on the XS2A interface.
""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, SigningBasketNotFound, SigningBasketStatusInvalid, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(deleteSigningBasket)
  )

  // ── GET /signing-baskets/BASKETID ─────────────────────────────────────
  val getSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketid =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          (basket, _) <- SigningBasketNewStyle.getOwnBasket(basketid, CreatorOnly, callContext)
        } yield {
          getSigningBasketResponseJson(basket)
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getSigningBasket),
    "GET",
    "/signing-baskets/BASKETID",
    "Returns the content of an signing basket object.",
    s"""${mockedDataText(false)}
Returns the content of an signing basket object.""",
    EmptyBody,
    JvalueCaseClass(json.parse("""{
  "transactionStatus" : "RCVD",
  "payments" : "",
  "consents" : ""
}""")),
    List(AuthenticatedUserIsRequired, SigningBasketNotFound, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasket)
  )

  // ── GET /signing-baskets/BASKETID/authorisations ──────────────────────
  val getSigningBasketAuthorisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketid / "authorisations" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- SigningBasketNewStyle.getOwnBasket(basketid, AuthorisationOperation, callContext)
          (challenges, _) <- NewStyle.function.getChallengesByBasketId(basketid, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.AuthorisationJsonV13(challenges.map(_.challengeId))
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getSigningBasketAuthorisation),
    "GET",
    "/signing-baskets/BASKETID/authorisations",
    "Get Signing Basket Authorisation Sub-Resources Request",
    s"""${mockedDataText(false)}
Read a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
    EmptyBody,
    JvalueCaseClass(json.parse("""{
  "authorisationIds" : ""
}""")),
    List(AuthenticatedUserIsRequired, SigningBasketNotFound, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasketAuthorisation)
  )

  // ── GET /signing-baskets/BASKETID/authorisations/AUTHORISATIONID ───────
  val getSigningBasketScaStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- SigningBasketNewStyle.getOwnBasket(basketId, AuthorisationOperation, callContext)
          (challenge, _) <- SigningBasketNewStyle.getBasketAuthorisation(basketId, authorisationId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challenge.scaStatus.map(_.toString).getOrElse(""))
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getSigningBasketScaStatus),
    "GET",
    "/signing-baskets/BASKETID/authorisations/AUTHORISATIONID",
    "Read the SCA status of the signing basket authorisation",
    s"""${mockedDataText(false)}
This method returns the SCA status of a signing basket's authorisation sub-resource.
""",
    EmptyBody,
    JvalueCaseClass(json.parse("""{
  "scaStatus" : "psuAuthenticated"
}""")),
    List(AuthenticatedUserIsRequired, SigningBasketNotFound, SigningBasketAuthorisationNotFound, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasketScaStatus)
  )

  // ── GET /signing-baskets/BASKETID/status ──────────────────────────────
  val getSigningBasketStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketid / "status" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          (basket, _) <- SigningBasketNewStyle.getOwnBasket(basketid, CreatorOnly, callContext)
        } yield {
          getSigningBasketStatusResponseJson(basket)
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getSigningBasketStatus),
    "GET",
    "/signing-baskets/BASKETID/status",
    "Read the status of the signing basket",
    s"""${mockedDataText(false)}
Returns the status of a signing basket object.
""",
    EmptyBody,
    JvalueCaseClass(json.parse("""{
  "transactionStatus" : "RCVD"
}""")),
    List(AuthenticatedUserIsRequired, SigningBasketNotFound, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasketStatus)
  )

  // ── POST /signing-baskets/BASKETID/authorisations ─────────────────────
  val startSigningBasketAuthorisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "signing-baskets" / basketId / "authorisations" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc = req.callContext
        val callContext = Some(cc)
        for {
          (basket, _) <- SigningBasketNewStyle.getOwnBasket(basketId, AuthorisationOperation, callContext)
          _ <- requireSupportedAuthorisationBody(
            cc.httpBody.getOrElse(""), answering = false,
            s"$InvalidJsonFormat The Json body should be empty, or the transactionAuthorisation body. ", callContext)
          // An authorisation can only be started on a basket still waiting for one.
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
            basket.basket.status == ConstantsBG.SigningBasketsStatus.RCVD.toString
          }
          // Whose challenge this is, which is also where the OTP goes: the PSU, not the calling TPP.
          psuUserId <- SigningBasketNewStyle.bindAuthorisingPsu(basket, cc, callContext)
          (challenges, _) <- NewStyle.function.createChallengesC3(
            List(psuUserId),
            ChallengeType.BERLIN_GROUP_SIGNING_BASKETS_CHALLENGE,
            None,
            getSuggestedDefaultScaMethod(),
            Some(StrongCustomerAuthenticationStatus.received),
            None,
            Some(basketId),
            None,
            callContext
          )
          challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge, 400, callContext) {
            challenges.head
          }
        } yield {
          createStartSigningBasketAuthorisationJson(basketId, challenge)
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(startSigningBasketAuthorisation),
    "POST",
    "/signing-baskets/BASKETID/authorisations",
    "Start the authorisation process for a signing basket",
    s"""${mockedDataText(false)}
Create an authorisation sub-resource and start the authorisation process of a signing basket.
The message might in addition transmit authentication and authorisation related data.

This method is iterated n times for a n times SCA authorisation in a
corporate context, each creating an own authorisation sub-endpoint for
the corresponding PSU authorising the signing-baskets.

The ASPSP might make the usage of this access method unnecessary in case
of only one SCA process needed, since the related authorisation resource
might be automatically created by the ASPSP after the submission of the
payment data with the first POST signing basket call.

The start authorisation process is a process which is needed for creating a new authorisation
or cancellation sub-resource.

This applies in the following scenarios:

  * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding Payment
    Initiation Response that an explicit start of the authorisation process is needed by the TPP.
    The 'startAuthorisation' hyperlink can transport more information about data which needs to be
    uploaded by using the extended forms.
    * 'startAuthorisationWithPsuIdentfication',
    * 'startAuthorisationWithPsuAuthentication' #TODO
    * 'startAuthorisationWithAuthentciationMethodSelection'
  * The related payment initiation cannot yet be executed since a multilevel SCA is mandated.
  * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding
    Payment Cancellation Response that an explicit start of the authorisation process is needed by the TPP.
    The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded
    by using the extended forms as indicated above.
  * The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for
    executing the cancellation.
  * The signing basket needs to be authorised yet.
""",
    EmptyBody,
    JvalueCaseClass(json.parse("""{
  "scaStatus" : "received",
  "authorisationId" : "4f4a8b7f-9968-4183-92ab-ca512b396bfc",
  "psuMessage" : "Please check your SMS at a mobile device.",
  "_links" : {
    "scaStatus" : {
      "href" : "/v1.3/signing-baskets/1234-basket-567/authorisations/4f4a8b7f-9968-4183-92ab-ca512b396bfc"
    }
  }
}""")),
    List(AuthenticatedUserIsRequired, InvalidJsonFormat, SigningBasketNotFound, SigningBasketStatusInvalid, SigningBasketAuthorisationVariantNotSupported, BerlinGroupPsuNotIdentified, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(startSigningBasketAuthorisation)
  )

  /**
   * How a failed challenge validation is answered. A wrong, expired or used-up one-time password is
   * the standard's PSU_CREDENTIALS_INVALID (401, "the password/OTP is incorrect"); an authorisation
   * answered a second time, concurrently or later, is a conflict.
   */
  private def challengeFailure(message: String): (String, Int) =
    if (message.contains("Challenge already answered")) (SigningBasketStatusInvalid, 409)
    else if (message.contains("OBP-40016") || message.contains("OBP-20211") || message.contains("OBP-40014")) (message, 401)
    else (message, 400)

  // ── PUT /signing-baskets/BASKETID/authorisations/AUTHORISATIONID ───────
  //
  // Order matters, and nothing may be changed until the answer has been checked:
  //   1. who the caller is and whether this is their basket and their authorisation;
  //   2. whether the request can succeed at all (instance setting, members, basket state, challenge state);
  //   3. the answer, checked as the PSU the challenge was minted for;
  //   4. the basket is claimed with one conditional update, so the final answer and a delete racing it
  //      have exactly one winner;
  //   5. only then do members change, and the basket is completed.
  val updateSigningBasketPsuData: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV13Prefix` / "signing-baskets" / basketId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val provider = SigningBasketX.signingBasketProvider.vend
        for {
          (basket, _) <- SigningBasketNewStyle.getOwnBasket(basketId, AuthorisationOperation, callContext)
          (startedChallenge, _) <- SigningBasketNewStyle.getBasketAuthorisation(basketId, authorisationId, callContext)
          failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
          _ <- requireSupportedAuthorisationBody(cc.httpBody.getOrElse(""), answering = true, failMsg, callContext)
          updateBasketPsuDataJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[UpdatePaymentPsuDataJson]
          }
          _ <- booleanToFuture(SigningBasketAuthorisationDisabled, failCode = 403, cc = callContext) {
            getPropsAsBoolValue("signing_basket_authorisation_enabled", defaultValue = false)
          }
          _ <- booleanToFuture(SigningBasketConsentNotSupported, failCode = 400, cc = callContext) {
            basket.consents.forall(_.isEmpty)
          }
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
            basket.basket.status == ConstantsBG.SigningBasketsStatus.RCVD.toString
          }
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
            !startedChallenge.scaStatus.exists(status =>
              status == StrongCustomerAuthenticationStatus.finalised || status == StrongCustomerAuthenticationStatus.failed)
          }
          paymentIds = basket.payments.getOrElse(Nil)
          members <- Future(paymentIds.map(id => id -> Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(id), callContext)))
          _ <- booleanToFuture(SigningBasketMemberNotFound, failCode = 400, cc = callContext)(members.forall(_._2.isDefined))
          _ <- booleanToFuture(SigningBasketMemberStatusInvalid, failCode = 409, cc = callContext) {
            !members.exists(_._2.exists(_._1.status == COMPLETED.toString))
          }
          // The answer is the PSU's, relayed by the TPP under Embedded, so it is checked against the
          // challenge's own PSU rather than the principal on the token.
          (psu, _) <- NewStyle.function.findByUserId(startedChallenge.expectedUserId, callContext)
          (boxedChallenge, _) <- NewStyle.function.validateChallengeAnswerC5(
            ChallengeType.BERLIN_GROUP_SIGNING_BASKETS_CHALLENGE,
            None,
            None,
            Some(basketId),
            authorisationId,
            updateBasketPsuDataJson.scaAuthenticationData,
            SuppliedAnswerType.PLAIN_TEXT_VALUE,
            callContext.map(_.copy(user = Full(psu)))
          )
          challenge <- Future {
            boxedChallenge match {
              case Full(answered) => answered
              case failure =>
                val (message, code) = challengeFailure(failure match {
                  case f: Failure => f.msg
                  case _ => InvalidConnectorResponse
                })
                unboxFullOrFail(Empty: Box[ChallengeTrait], callContext, message, code)
            }
          }
          claimed <- Future(provider.transitionSigningBasketStatus(
            basketId, ConstantsBG.SigningBasketsStatus.RCVD.toString, ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL))
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext)(claimed.openOr(false))
          // Each member is recorded, then booked in order. The basket becomes ACTC only if every one is.
          _ <- Future(provider.createSigningBasketMemberExecutions(basketId, paymentIds.map(SigningBasketMemberState.PaymentType -> _)))
          allDone <- SigningBasketExecution.execute(basketId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createUpdateSigningBasketPsuDataJson(basketId, challenge, executionIncomplete = !allDone)
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateSigningBasketPsuData),
    "PUT",
    "/signing-baskets/BASKETID/authorisations/AUTHORISATIONID",
    "Update PSU Data for signing basket",
    s"""${mockedDataText(false)}
This method update PSU data on the signing basket resource if needed.
It may authorise a igning basket within the Embedded SCA Approach where needed.

Independently from the SCA Approach it supports e.g. the selection of
the authentication method and a non-SCA PSU authentication.

This methods updates PSU data on the cancellation authorisation resource if needed.

There are several possible Update PSU Data requests in the context of a consent request if needed,
which depends on the SCA approach:

* Redirect SCA Approach:
  A specific Update PSU Data Request is applicable for
    * the selection of authentication methods, before choosing the actual SCA approach.
* Decoupled SCA Approach:
  A specific Update PSU Data Request is only applicable for
  * adding the PSU Identification, if not provided yet in the Payment Initiation Request or the Account Information Consent Request, or if no OAuth2 access token is used, or
  * the selection of authentication methods.
* Embedded SCA Approach:
  The Update PSU Data Request might be used
  * to add credentials as a first factor authentication data of the PSU and
  * to select the authentication method and
  * transaction authorisation.

The SCA Approach might depend on the chosen SCA method.
For that reason, the following possible Update PSU Data request can apply to all SCA approaches:

* Select an SCA method in case of several SCA methods are available for the customer.

There are the following request types on this access path:
  * Update PSU Identification
  * Update PSU Authentication
  * Select PSU Autorization Method
    WARNING: This method need a reduced header,
    therefore many optional elements are not present.
    Maybe in a later version the access path will change.
  * Transaction Authorisation
    WARNING: This method need a reduced header,
    therefore many optional elements are not present.
    Maybe in a later version the access path will change.
""",
    JvalueCaseClass(json.parse("""{"scaAuthenticationData":"123"}""")),
    JvalueCaseClass(json.parse("""{
                  "scaStatus":"finalised",
                  "psuMessage":"Please check your SMS at a mobile device.",
                  "_links":{
                    "scaStatus":{
                      "href":"/v1.3/signing-baskets/1234-basket-567/authorisations/4f4a8b7f-9968-4183-92ab-ca512b396bfc"
                    }
                  }
                }""")),
    List(AuthenticatedUserIsRequired, InvalidJsonFormat, SigningBasketNotFound, SigningBasketAuthorisationNotFound, SigningBasketAuthorisationVariantNotSupported, SigningBasketAuthorisationDisabled, SigningBasketConsentNotSupported, SigningBasketStatusInvalid, SigningBasketMemberNotFound, SigningBasketMemberStatusInvalid, InvalidChallengeAnswer, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(updateSigningBasketPsuData)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createSigningBasket(req)
      .orElse(deleteSigningBasket(req))
      .orElse(getSigningBasket(req))
      .orElse(getSigningBasketAuthorisation(req))
      .orElse(getSigningBasketScaStatus(req))
      .orElse(getSigningBasketStatus(req))
      .orElse(startSigningBasketAuthorisation(req))
      .orElse(updateSigningBasketPsuData(req))
  }
}
