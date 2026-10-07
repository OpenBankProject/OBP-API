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
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, getPropsAsBoolValue, getSuggestedDefaultScaMethod, mockedDataText, passesPsd2Pisp, unboxFullOrFail}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.api.util.{ApiTag, CallContext, NewStyle}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.SigningBasketNewStyle
import code.bankconnectors.Connector
import code.signingbaskets.SigningBasketX
import code.util.Helper.{MdcLoggable, booleanToFuture}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.TransactionRequestStatus.{COMPLETED, REJECTED}
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus, SuppliedAnswerType}
import com.openbankproject.commons.model.{AccountId, BankId, ChallengeTrait, TransactionRequestId}
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
   * Berlin Group hangs several request bodies off the authorisation paths. Baskets support the two that need
   * no data this ASPSP holds back: an empty body, which starts the authorisation, and
   * `transactionAuthorisation`, which answers it. The others are Embedded-approach steps that are not
   * implemented for any Berlin Group resource here. They are refused by name rather than answered as if the
   * credential or the choice had been processed, and a body that matches no variant is a format error.
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

  /** The authorisation, if it is one of this basket's. */
  private def getBasketAuthorisation(basketId: String, authorisationId: String, callContext: Option[CallContext]): Future[ChallengeTrait] =
    for {
      (challenges, _) <- NewStyle.function.getChallengesByBasketId(basketId, callContext)
      found = challenges.find(_.challengeId == authorisationId)
      _ <- booleanToFuture(SigningBasketAuthorisationNotFound, failCode = 404, cc = callContext)(found.isDefined)
    } yield found.get

  // ── POST /signing-baskets ──────────────────────────────────────────────
  val createSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "signing-baskets" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc = req.callContext
        val callContext = Some(cc)
        val failMsg = s"$InvalidJsonFormat The Json body should be the $PostSigningBasketJsonV13 "
        for {
          _ <- passesPsd2Pisp(callContext)
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
          // Authorising a consent through a basket is not implemented yet, and a basket that silently
          // ignored its consents would claim an authorisation that never happened.
          _ <- booleanToFuture(SigningBasketConsentsNotSupported, cc = callContext)(postJson.consentIds.forall(_.isEmpty))
          // The basket belongs to the TPP that creates it; nothing else identifies who may address it later.
          consumerId <- Future.successful(cc.consumer.toOption.map(_.consumerId.get))
            .map(unboxFullOrFail(_, callContext, AuthenticatedUserIsRequired, 401))
          // Every payment must be one this TPP lodged and SCA can still authorise.
          _ <- SigningBasketNewStyle.admitPayments(postJson.paymentIds.getOrElse(Nil), callContext)
          signingBasket <- Future {
            SigningBasketX.signingBasketProvider.vend.createSigningBasket(postJson.paymentIds, None, consumerId)
          }.map(unboxFullOrFail(_, callContext, UnknownError))
        } yield {
          createSigningBasketResponseJson(signingBasket)
        }
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
  "transactionStatus" : "ACCP",
  "psuMessage" : { }
}""")),
    List(AuthenticatedUserIsRequired, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(createSigningBasket)
  )

  // ── DELETE /signing-baskets/BASKETID ──────────────────────────────────
  val deleteSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `bgV13Prefix` / "signing-baskets" / basketid =>
      EndpointHelpers.executeDelete(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          basket <- SigningBasketNewStyle.getOwnBasket(basketid, callContext)
          alreadyCancelled = basket.basket.status == ConstantsBG.SigningBasketsStatus.CANC.toString
          // One conditional update: a basket whose authorisation has been answered is no longer RCVD and is not
          // deletable. Deleting one that is already deleted changes nothing.
          cancelled <- if (alreadyCancelled) Future.successful(Full(true))
                       else Future(SigningBasketX.signingBasketProvider.vend.transitionSigningBasketStatus(
                         basketid, ConstantsBG.SigningBasketsStatus.RCVD.toString, ConstantsBG.SigningBasketsStatus.CANC.toString))
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext)(cancelled.openOr(false))
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
The undlerying transactions are not affected by this deletion.

Remark: The signing basket as such is not deletable after a first (partial) authorisation has been applied.
Nevertheless, single transactions might be cancelled on an individual basis on the XS2A interface.
""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(deleteSigningBasket)
  )

  // ── GET /signing-baskets/BASKETID ─────────────────────────────────────
  val getSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketid =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          basket <- SigningBasketNewStyle.getOwnBasket(basketid, callContext)
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
  "transactionStatus" : "ACCP",
  "payments" : "",
  "consents" : ""
}""")),
    List(AuthenticatedUserIsRequired, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasket)
  )

  // ── GET /signing-baskets/BASKETID/authorisations ──────────────────────
  val getSigningBasketAuthorisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketid / "authorisations" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- SigningBasketNewStyle.getOwnBasket(basketid, callContext)
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
    List(AuthenticatedUserIsRequired, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasketAuthorisation)
  )

  // ── GET /signing-baskets/BASKETID/authorisations/AUTHORISATIONID ───────
  val getSigningBasketScaStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- SigningBasketNewStyle.getOwnBasket(basketId, callContext)
          challenge <- getBasketAuthorisation(basketId, authorisationId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challenge.scaStatus.map(_.toString).getOrElse("None"))
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
    List(AuthenticatedUserIsRequired, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(getSigningBasketScaStatus)
  )

  // ── GET /signing-baskets/BASKETID/status ──────────────────────────────
  val getSigningBasketStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / "signing-baskets" / basketid / "status" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          basket <- SigningBasketNewStyle.getOwnBasket(basketid, callContext)
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
    List(AuthenticatedUserIsRequired, UnknownError),
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
          _ <- passesPsd2Pisp(callContext)
          basket <- SigningBasketNewStyle.getOwnBasket(basketId, callContext)
          failMsg = s"$InvalidJsonFormat The Json body should be empty, or one of updatePsuAuthentication, selectPsuAuthenticationMethod or transactionAuthorisation."
          _ <- requireSupportedAuthorisationBody(cc.httpBody.getOrElse(""), answering = false, failMsg, callContext)
          // Only a basket still waiting for its authorisation can be given one.
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
            basket.basket.status == ConstantsBG.SigningBasketsStatus.RCVD.toString
          }
          (challenges, _) <- NewStyle.function.createChallengesC3(
            List(cc.user.map(_.userId).openOr("")),
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
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
  },
  "scaMethods" : "",
  "scaStatus" : "psuAuthenticated",
  "_links" : {
    "scaStatus" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "selectAuthenticationMethod" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "authoriseTransaction" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "updatePsuIdentification" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "psuMessage" : { }
}""")),
    List(AuthenticatedUserIsRequired, UnknownError),
    apiTagSigningBaskets :: Nil,
    http4sPartialFunction = Some(startSigningBasketAuthorisation)
  )

  // ── PUT /signing-baskets/BASKETID/authorisations/AUTHORISATIONID ───────
  /**
   * A wrong, expired or used-up one-time password is the PSU's credentials being refused, not a malformed
   * request: 401, which the standard has a code for. An answer given a second time, concurrently or later, is
   * a conflict.
   */
  private def challengeFailure(message: String): (String, Int) =
    if (message.contains("Challenge already answered")) (SigningBasketStatusInvalid, 409)
    else if (message.contains("OBP-40016") || message.contains("OBP-20211") || message.contains("OBP-40014")) (message, 401)
    else (message, 400)

  /**
   * Books the payments one after another, each awaited, so that the money has moved when the response is sent.
   * Stops at the first that cannot be booked and says so; the ones before it stay booked, which each
   * payment's own status shows.
   */
  private def bookPayments(paymentIds: List[String], callContext: Option[CallContext]): Future[Boolean] =
    paymentIds match {
      case Nil => Future.successful(true)
      case paymentId :: rest =>
        bookPayment(paymentId, callContext).flatMap(booked => if (booked) bookPayments(rest, callContext) else Future.successful(false))
    }

  private def bookPayment(paymentId: String, callContext: Option[CallContext]): Future[Boolean] =
    (for {
      (payment, _) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
      (fromAccount, _) <- NewStyle.function.checkBankAccountExists(BankId(payment.from.bank_id), AccountId(payment.from.account_id), callContext)
      _ <- NewStyle.function.createTransactionAfterChallengeV210(fromAccount, payment, callContext)
      _ <- NewStyle.function.saveTransactionRequestStatusImpl(payment.id, COMPLETED.toString, callContext)
    } yield true).recover {
      case error =>
        logger.warn(s"Signing basket: payment $paymentId could not be booked: ${error.getMessage}")
        false
    }

  // ── PUT /signing-baskets/BASKETID/authorisations/AUTHORISATIONID ───────
  //
  // Order matters, and nothing may be changed until the answer has been checked:
  //   1. whether the instance allows it, and whether this is the caller's basket and one of its authorisations;
  //   2. whether the request can succeed at all (basket and challenge state, every payment still waiting for SCA);
  //   3. the answer;
  //   4. the basket is claimed with one conditional update, so two answers racing each other have one winner;
  //   5. only then are the payments booked, and the basket becomes ACTC if every one was.
  val updateSigningBasketPsuData: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV13Prefix` / "signing-baskets" / basketId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val provider = SigningBasketX.signingBasketProvider.vend
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- booleanToFuture(SigningBasketAuthorisationDisabled, failCode = 403, cc = callContext) {
            getPropsAsBoolValue("signing_basket_authorisation_enabled", false)
          }
          basket <- SigningBasketNewStyle.getOwnBasket(basketId, callContext)
          startedChallenge <- getBasketAuthorisation(basketId, authorisationId, callContext)
          failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
          _ <- requireSupportedAuthorisationBody(cc.httpBody.getOrElse(""), answering = true, failMsg, callContext)
          updateBasketPsuDataJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[UpdatePaymentPsuDataJson]
          }
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
            basket.basket.status == ConstantsBG.SigningBasketsStatus.RCVD.toString
          }
          // An authorisation already answered, for good or for bad, cannot be answered again.
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext) {
            !startedChallenge.scaStatus.exists(status =>
              status == StrongCustomerAuthenticationStatus.finalised || status == StrongCustomerAuthenticationStatus.failed)
          }
          paymentIds = basket.payments.getOrElse(Nil)
          payments <- Future(paymentIds.map(id => Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(id), callContext)))
          _ <- booleanToFuture(SigningBasketMemberNotFound, failCode = 400, cc = callContext)(payments.forall(_.isDefined))
          // Every payment has to be waiting for SCA now, so that the answer does not book some of them and then stop.
          _ <- booleanToFuture(SigningBasketMemberStatusInvalid, failCode = 409, cc = callContext) {
            payments.forall(_.exists(payment => SigningBasketNewStyle.awaitingScaPaymentStatuses.contains(payment._1.status)))
          }
          (boxedChallenge, _) <- NewStyle.function.validateChallengeAnswerC5(
            ChallengeType.BERLIN_GROUP_SIGNING_BASKETS_CHALLENGE,
            None,
            None,
            Some(basketId),
            authorisationId,
            updateBasketPsuDataJson.scaAuthenticationData,
            SuppliedAnswerType.PLAIN_TEXT_VALUE,
            callContext
          )
          // Only an answer the challenge records as finalised authorises anything. A connector may hand back the
          // challenge itself with another status, which is a refusal, not a success.
          challenge <- Future {
            boxedChallenge match {
              case Full(answered) if answered.scaStatus.contains(StrongCustomerAuthenticationStatus.finalised) => answered
              case other =>
                val (message, status) = challengeFailure(other match {
                  case f: Failure => f.msg
                  case _ => InvalidChallengeAnswer
                })
                unboxFullOrFail(Empty: Box[ChallengeTrait], callContext, message, status)
            }
          }
          claimed <- Future(provider.transitionSigningBasketStatus(
            basketId, ConstantsBG.SigningBasketsStatus.RCVD.toString, ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL))
          _ <- booleanToFuture(SigningBasketStatusInvalid, failCode = 409, cc = callContext)(claimed.openOr(false))
          allBooked <- bookPayments(paymentIds, callContext)
          _ <- if (allBooked) Future(provider.transitionSigningBasketStatus(
                 basketId, ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL, ConstantsBG.SigningBasketsStatus.ACTC.toString))
               else Future.successful(())
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createUpdateSigningBasketPsuDataJson(basketId, challenge, executionIncomplete = !allBooked)
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
                  "authorisationId":"4f4a8b7f-9968-4183-92ab-ca512b396bfc",
                  "psuMessage":"Please check your SMS at a mobile device.",
                  "_links":{
                    "scaStatus":"/v1.3/payments/sepa-credit-transfers/PAYMENT_ID/4f4a8b7f-9968-4183-92ab-ca512b396bfc"
                  }
                }""")),
    List(AuthenticatedUserIsRequired, UnknownError),
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
