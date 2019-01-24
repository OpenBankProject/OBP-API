package code.api.builder.PaymentInitiationServicePISApi
import java.util.UUID

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.builder.{APIBuilder_Connector, CreateTemplateJson, JsonFactory_APIBuilder}
import code.api.builder.JsonFactory_APIBuilder._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.Extraction._
import net.liftweb.json._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_PaymentInitiationServicePISApi { self: RestHelper =>
  val ImplementationsPaymentInitiationServicePISApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    implicit val formats = net.liftweb.json.DefaultFormats
    protected implicit def JvalueToSuper(in: JValue): JvalueCaseClass = JvalueCaseClass(in)

    val endpoints =
      cancelPayment ::
      getPaymentCancellationScaStatus ::
      getPaymentInformation ::
      getPaymentInitiationAuthorisation ::
      getPaymentInitiationCancellationAuthorisationInformation ::
      getPaymentInitiationScaStatus ::
      getPaymentInitiationStatus ::
      initiatePayment ::
      startPaymentAuthorisation ::
      startPaymentInitiationCancellationAuthorisation ::
      updatePaymentCancellationPsuData ::
      updatePaymentPsuData ::
      Nil

            
     resourceDocs += ResourceDoc(
       cancelPayment, 
       apiVersion, 
       "cancelPayment",
       "DELETE", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID", 
       "Payment Cancellation Request",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
  },
  "scaMethods" : "",
  "_links" : {
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "ACCP"
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val cancelPayment : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentCancellationScaStatus, 
       apiVersion, 
       "getPaymentCancellationScaStatus",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations/CANCELLATIONID", 
       "Read the SCA status of the payment cancellation's authorisation.",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
  "scaStatus" : "psuAuthenticated"
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val getPaymentCancellationScaStatus : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: cancellationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInformation, 
       apiVersion, 
       "getPaymentInformation",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID", 
       "Get Payment Information",
       "", 
       JvalueToSuper(json.parse("""""")),
       """""""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val getPaymentInformation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationAuthorisation, 
       apiVersion, 
       "getPaymentInitiationAuthorisation",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/authorisations", 
       "Get Payment Initiation Authorisation Sub-Resources Request",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
  "authorisationIds" : ""
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val getPaymentInitiationAuthorisation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationCancellationAuthorisationInformation, 
       apiVersion, 
       "getPaymentInitiationCancellationAuthorisationInformation",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations", 
       "Will deliver an array of resource identifications to all generated cancellation authorisation sub-resources.",
       "", 
       JvalueToSuper(json.parse("""""")),
       """""""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val getPaymentInitiationCancellationAuthorisationInformation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationScaStatus, 
       apiVersion, 
       "getPaymentInitiationScaStatus",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/authorisations/AUTHORISATIONID", 
       "Read the SCA Status of the payment authorisation",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
  "scaStatus" : "psuAuthenticated"
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val getPaymentInitiationScaStatus : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationStatus, 
       apiVersion, 
       "getPaymentInitiationStatus",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/status", 
       "Payment initiation status request",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
  "transactionStatus" : "ACCP"
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val getPaymentInitiationStatus : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       initiatePayment, 
       apiVersion, 
       "initiatePayment",
       "POST", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT", 
       "Payment initiation request",
       "", 
       JvalueToSuper(json.parse("""""")),
       """""""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val initiatePayment : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       startPaymentAuthorisation, 
       apiVersion, 
       "startPaymentAuthorisation",
       "POST", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/authorisations", 
       "Start the authorisation process for a payment initiation",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
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
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "selectAuthenticationMethod" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "authoriseTransaction" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "updatePsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "psuMessage" : { }
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val startPaymentAuthorisation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       startPaymentInitiationCancellationAuthorisation, 
       apiVersion, 
       "startPaymentInitiationCancellationAuthorisation",
       "POST", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations", 
       "Start the authorisation process for the cancellation of the addressed payment",
       "", 
       JvalueToSuper(json.parse("""""")),
       """{
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
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "selectAuthenticationMethod" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "authoriseTransaction" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "updatePsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "psuMessage" : { }
}""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val startPaymentInitiationCancellationAuthorisation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updatePaymentCancellationPsuData, 
       apiVersion, 
       "updatePaymentCancellationPsuData",
       "PUT", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations/CANCELLATIONID", 
       "Update PSU Data for payment initiation cancellation",
       "", 
       JvalueToSuper(json.parse("""""")),
       """""""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val updatePaymentCancellationPsuData : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: cancellationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updatePaymentPsuData, 
       apiVersion, 
       "updatePaymentPsuData",
       "PUT", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/authorisations/AUTHORISATIONID", 
       "Update PSU data for payment initiation",
       "", 
       JvalueToSuper(json.parse("""""")),
       """""""",
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: Nil
     )

     lazy val updatePaymentPsuData : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

  }
}



