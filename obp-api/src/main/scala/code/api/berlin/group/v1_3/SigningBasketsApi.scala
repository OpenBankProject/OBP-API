package code.api.builder.SigningBasketsApi

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{PostSigningBasketJsonV13, UpdatePaymentPsuDataJson, createSigningBasketResponseJson, createStartSigningBasketAuthorisationJson, getSigningBasketResponseJson, getSigningBasketStatusResponseJson}
import code.api.berlin.group.v1_3.{JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass}
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle
import code.api.util.NewStyle.HttpCode
import code.api.util.newstyle.SigningBasketNewStyle
import code.bankconnectors.Connector
import code.signingbaskets.SigningBasketX
import code.util.Helper.booleanToFuture
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.TransactionRequestStatus.{COMPLETED, REJECTED}
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus,SuppliedAnswerType}
import com.openbankproject.commons.model.{ChallengeTrait, TransactionRequestId}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object APIMethods_SigningBasketsApi extends RestHelper {
    val apiVersion = ConstantsBG.berlinGroupVersion1
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createSigningBasket ::
      deleteSigningBasket ::
      getSigningBasket ::
      getSigningBasketAuthorisation ::
      getSigningBasketScaStatus ::
      getSigningBasketStatus ::
      startSigningBasketAuthorisation ::
      updateSigningBasketPsuData ::
      Nil


     resourceDocs += ResourceDoc(
       createSigningBasket,
       apiVersion,
       nameOf(createSigningBasket),
       "POST",
       "/signing-baskets",
       "Create a signing basket resource",
       s"""${mockedDataText(false)}
Create a signing basket resource for authorising several transactions with one SCA method. 
The resource identifications of these transactions are contained in the  payload of this access method
""",
       PostSigningBasketJsonV13(paymentIds = Some(List("123qwert456789", "12345qwert7899")), None),
       json.parse("""{
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
  }, {
    "path" : "path",
    "code" : { },
    "text" : { },
    "category" : { }
  } ],
  "_links" : {
    "scaStatus" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "self" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithTransactionAuthorisation" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "status" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "ACCP",
  "psuMessage" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val createSigningBasket : OBPEndpoint = {
       case "signing-baskets" :: Nil JsonPost jsonPost -> _  =>  {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             failMsg = s"$InvalidJsonFormat The Json body should be the $PostSigningBasketJsonV13 "
             postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               jsonPost.extract[PostSigningBasketJsonV13]
             }
             _ <- booleanToFuture(failMsg, cc = callContext) {
               // One of them MUST be defined. Otherwise, post json is treated as empty one.
               !(jsonPost.extract[PostSigningBasketJsonV13].paymentIds.isEmpty &&
                 jsonPost.extract[PostSigningBasketJsonV13].consentIds.isEmpty)
             }
             signingBasket <- Future {
               SigningBasketX.signingBasketProvider.vend.createSigningBasket(
                 postJson.paymentIds,
                 postJson.consentIds,
               )
             } map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (createSigningBasketResponseJson(signingBasket), HttpCode.`201`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteSigningBasket,
       apiVersion,
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
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val deleteSigningBasket : OBPEndpoint = {
       case "signing-baskets" :: basketid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- Future {
               SigningBasketX.signingBasketProvider.vend.deleteSigningBasket(basketid)
             } map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (JsRaw(""), HttpCode.`204`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasket,
       apiVersion,
       nameOf(getSigningBasket),
       "GET",
       "/signing-baskets/BASKETID",
       "Returns the content of an signing basket object.",
       s"""${mockedDataText(false)}
Returns the content of an signing basket object.""",
       EmptyBody,
       json.parse("""{
  "transactionStatus" : "ACCP",
  "payments" : "",
  "consents" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val getSigningBasket : OBPEndpoint = {
       case "signing-baskets" :: basketid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             basket <- Future {
               SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketid)
             } map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (getSigningBasketResponseJson(basket), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketAuthorisation,
       apiVersion,
       nameOf(getSigningBasketAuthorisation),
       "GET",
       "/signing-baskets/BASKETID/authorisations",
       "Get Signing Basket Authorisation Sub-Resources Request",
       s"""${mockedDataText(false)}
Read a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
       EmptyBody,
       json.parse("""{
  "authorisationIds" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val getSigningBasketAuthorisation : OBPEndpoint = {
       case "signing-baskets" :: basketid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             (challenges, callContext) <- NewStyle.function.getChallengesByBasketId(basketid, callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.AuthorisationJsonV13(challenges.map(_.challengeId)), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketScaStatus,
       apiVersion,
       nameOf(getSigningBasketScaStatus),
       "GET",
       "/signing-baskets/BASKETID/authorisations/AUTHORISATIONID",
       "Read the SCA status of the signing basket authorisation",
       s"""${mockedDataText(false)}
This method returns the SCA status of a signing basket's authorisation sub-resource.
""",
       EmptyBody,
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val getSigningBasketScaStatus : OBPEndpoint = {
       case "signing-baskets" :: basketId:: "authorisations" :: authorisationId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- Future(SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId)) map {
               unboxFullOrFail(_, callContext, s"$ConsentNotFound ($basketId)", 403)
             }
             (challenges, callContext) <- NewStyle.function.getChallengesByBasketId(basketId, callContext)
           } yield {
             val challengeStatus = challenges.filter(_.challengeId == authorisationId)
               .flatMap(_.scaStatus).headOption.map(_.toString).getOrElse("None")
             (JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challengeStatus), HttpCode.`200`(callContext))
           }
         }
     }
            
     resourceDocs += ResourceDoc(
       getSigningBasketStatus,
       apiVersion,
       nameOf(getSigningBasketStatus),
       "GET",
       "/signing-baskets/BASKETID/status",
       "Read the status of the signing basket",
       s"""${mockedDataText(false)}
Returns the status of a signing basket object. 
""",
       EmptyBody,
       json.parse("""{
  "transactionStatus" : "RCVD"
}"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val getSigningBasketStatus : OBPEndpoint = {
       case "signing-baskets" :: basketid:: "status" :: Nil JsonGet _ =>
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             basket <- Future {
               SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketid)
             } map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (getSigningBasketStatusResponseJson(basket), HttpCode.`200`(callContext))
           }
       }
            
     resourceDocs += ResourceDoc(
       startSigningBasketAuthorisation,
       apiVersion,
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
       json.parse("""{
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
}"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val startSigningBasketAuthorisation : OBPEndpoint = {
       case "signing-baskets" :: basketId :: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             (challenges, callContext) <- NewStyle.function.createChallengesC3(
               List(u.userId),
               ChallengeType.BERLIN_GROUP_SIGNING_BASKETS_CHALLENGE,
               None,
               getSuggestedDefaultScaMethod(),
               Some(StrongCustomerAuthenticationStatus.received),
               None,
               Some(basketId),
               None,
               callContext
             )
             //NOTE: in OBP it support multiple challenges, but in Berlin Group it has only one challenge. The following guard is to make sure it return the 1st challenge properly.
             challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge, 400, callContext) {
               challenges.head
             }
           } yield {
             (createStartSigningBasketAuthorisationJson(basketId, challenge), HttpCode.`201`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updateSigningBasketPsuData,
       apiVersion,
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
       json.parse("""{"scaAuthenticationData":"123"}"""),
       json.parse("""{
                      "scaStatus":"finalised",
                      "authorisationId":"4f4a8b7f-9968-4183-92ab-ca512b396bfc",
                      "psuMessage":"Please check your SMS at a mobile device.",
                      "_links":{
                        "scaStatus":"/v1.3/payments/sepa-credit-transfers/PAYMENT_ID/4f4a8b7f-9968-4183-92ab-ca512b396bfc"
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       apiTagSigningBaskets :: Nil
     )

     lazy val updateSigningBasketPsuData : OBPEndpoint = {
       case "signing-baskets" :: basketId:: "authorisations" :: authorisationId :: Nil JsonPut json -> _  =>
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
             updateBasketPsuDataJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               json.extract[UpdatePaymentPsuDataJson]
             }
             _ <- SigningBasketNewStyle.checkSigningBasketPayments(basketId, callContext)
             // Validate a challenge answer and get an error if any
             (boxedChallenge: Box[ChallengeTrait], callContext) <- NewStyle.function.validateChallengeAnswerC5(
               ChallengeType.BERLIN_GROUP_SIGNING_BASKETS_CHALLENGE,
               None,
               None,
               Some(basketId),
               authorisationId,
               updateBasketPsuDataJson.scaAuthenticationData,
               SuppliedAnswerType.PLAIN_TEXT_VALUE,
               callContext
             )
             // Get the challenge after validation
             (challenge: ChallengeTrait, callContext) <- NewStyle.function.getChallenge(authorisationId, callContext)
             _ <- challenge.scaStatus match {
               case Some(status) if status.toString == StrongCustomerAuthenticationStatus.finalised.toString => // finalised
                 Future {
                   val basket = SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId)
                   val existAll: Box[Boolean] =
                     basket.flatMap(_.payments.map(_.forall(i => Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(i), callContext).isDefined)))
                   if (existAll.getOrElse(false)) {
                     basket.map { i =>
                       i.payments.map(_.map { i =>
                         NewStyle.function.saveTransactionRequestStatusImpl(TransactionRequestId(i), COMPLETED.toString, callContext)
                         Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(i), callContext) map { t =>
                           Connector.connector.vend.makePaymentV400(t._1, None, callContext)
                         }
                       })
                     }
                     SigningBasketX.signingBasketProvider.vend.saveSigningBasketStatus(basketId, ConstantsBG.SigningBasketsStatus.ACTC.toString)
                     unboxFullOrFail(boxedChallenge, callContext, s"$InvalidConnectorResponse validateChallengeAnswerC5")
                   } else { // Fail due to unexisting payment
                     val paymentIds = basket.flatMap(_.payments).getOrElse(Nil).mkString(",")
                     unboxFullOrFail(Empty, callContext, s"$InvalidConnectorResponse Some of paymentIds [${paymentIds}] are invalid")
                   }
                 }
               case Some(status) if status.toString == StrongCustomerAuthenticationStatus.failed.toString => // failed
                 Future {
                   // Reject all related transaction requests
                   val basket = SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId)
                   basket.map { i =>
                     i.payments.map(_.map { i =>
                       NewStyle.function.saveTransactionRequestStatusImpl(TransactionRequestId(i), REJECTED.toString, callContext)
                     })
                   }
                   // Fail in case of an error message
                   unboxFullOrFail(boxedChallenge, callContext, s"$InvalidConnectorResponse validateChallengeAnswerC5")
                 }
               case _ => // Fail in case of an error message
                 Future(unboxFullOrFail(Empty, callContext, s"$InvalidConnectorResponse getChallenge"))
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationJson(challenge), callContext)
           }
       }

}



