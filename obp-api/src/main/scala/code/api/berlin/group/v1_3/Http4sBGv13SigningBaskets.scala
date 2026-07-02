package code.api.berlin.group.v1_3

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, connectorEmptyResponse, getSuggestedDefaultScaMethod, mockedDataText, passesPsd2Pisp, unboxFullOrFail}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.api.util.{ApiTag, NewStyle}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.SigningBasketNewStyle
import code.bankconnectors.Connector
import code.signingbaskets.SigningBasketX
import code.util.Helper.{MdcLoggable, booleanToFuture}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.TransactionRequestStatus.{COMPLETED, REJECTED}
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus, SuppliedAnswerType}
import com.openbankproject.commons.model.{ChallengeTrait, TransactionRequestId}
import net.liftweb.common.Empty
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

  // ── POST /signing-baskets ──────────────────────────────────────────────
  val createSigningBasket: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "signing-baskets" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc = req.callContext
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          failMsg = s"$InvalidJsonFormat The Json body should be the $PostSigningBasketJsonV13 "
          postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[PostSigningBasketJsonV13]
          }
          _ <- booleanToFuture(failMsg, cc = callContext) {
            !(postJson.paymentIds.isEmpty && postJson.consentIds.isEmpty)
          }
          signingBasket <- Future {
            SigningBasketX.signingBasketProvider.vend.createSigningBasket(
              postJson.paymentIds,
              postJson.consentIds,
            )
          }.map(connectorEmptyResponse(_, callContext))
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
          _ <- Future {
            SigningBasketX.signingBasketProvider.vend.deleteSigningBasket(basketid)
          }.map(connectorEmptyResponse(_, callContext))
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
          basket <- Future {
            SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketid)
          }.map(connectorEmptyResponse(_, callContext))
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
          _ <- Future(SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId))
                 .map(unboxFullOrFail(_, callContext, s"$ConsentNotFound ($basketId)", 403))
          (challenges, _) <- NewStyle.function.getChallengesByBasketId(basketId, callContext)
        } yield {
          val challengeStatus = challenges.filter(_.challengeId == authorisationId)
            .flatMap(_.scaStatus).headOption.map(_.toString).getOrElse("None")
          JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challengeStatus)
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
          basket <- Future {
            SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketid)
          }.map(connectorEmptyResponse(_, callContext))
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
  val updateSigningBasketPsuData: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV13Prefix` / "signing-baskets" / basketId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
          updateBasketPsuDataJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
            json.parse(cc.httpBody.getOrElse("")).extract[UpdatePaymentPsuDataJson]
          }
          _ <- SigningBasketNewStyle.checkSigningBasketPayments(basketId, callContext)
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
          (challenge, updatedCC) <- NewStyle.function.getChallenge(authorisationId, callContext)
          _ <- challenge.scaStatus match {
            case Some(status) if status.toString == StrongCustomerAuthenticationStatus.finalised.toString =>
              Future {
                val basket = SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId)
                val existAll =
                  basket.flatMap(_.payments.map(_.forall(i => Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(i), updatedCC).isDefined)))
                val alreadyCompleted: List[String] =
                  basket.flatMap(_.payments).getOrElse(Nil).filter { i =>
                    Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(i), updatedCC)
                      .exists(_._1.status == COMPLETED.toString)
                  }
                if (alreadyCompleted.nonEmpty) {
                  unboxFullOrFail(Empty, updatedCC, s"$InvalidConnectorResponse Some of paymentIds [${alreadyCompleted.mkString(",")}] are already completed")
                } else if (existAll.getOrElse(false)) {
                  basket.map { i =>
                    i.payments.map(_.map { i =>
                      NewStyle.function.saveTransactionRequestStatusImpl(TransactionRequestId(i), COMPLETED.toString, updatedCC)
                      Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(i), updatedCC).map { t =>
                        Connector.connector.vend.makePaymentV400(t._1, None, updatedCC)
                      }
                    })
                  }
                  SigningBasketX.signingBasketProvider.vend.saveSigningBasketStatus(basketId, ConstantsBG.SigningBasketsStatus.ACTC.toString)
                  unboxFullOrFail(boxedChallenge, updatedCC, s"$InvalidConnectorResponse validateChallengeAnswerC5")
                } else {
                  val paymentIds = basket.flatMap(_.payments).getOrElse(Nil).mkString(",")
                  unboxFullOrFail(Empty, updatedCC, s"$InvalidConnectorResponse Some of paymentIds [${paymentIds}] are invalid")
                }
              }
            case Some(status) if status.toString == StrongCustomerAuthenticationStatus.failed.toString =>
              Future {
                val basket = SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId)
                basket.map { i =>
                  i.payments.map(_.map { i =>
                    NewStyle.function.saveTransactionRequestStatusImpl(TransactionRequestId(i), REJECTED.toString, updatedCC)
                  })
                }
                unboxFullOrFail(boxedChallenge, updatedCC, s"$InvalidConnectorResponse validateChallengeAnswerC5")
              }
            case _ =>
              Future(unboxFullOrFail(Empty, updatedCC, s"$InvalidConnectorResponse getChallenge"))
          }
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationJson(challenge)
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
