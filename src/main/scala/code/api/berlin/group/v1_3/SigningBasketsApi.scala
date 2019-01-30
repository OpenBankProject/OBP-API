package code.api.builder.SigningBasketsApi
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

trait APIMethods_SigningBasketsApi { self: RestHelper =>
  val ImplementationsSigningBasketsApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
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
       "createSigningBasket",
       "POST", 
       "/v1/signing-baskets", 
       "Create a signing basket resource",
       """Create a signing basket resource for authorising several transactions with one SCA method. 
The resource identifications of these transactions are contained in the  payload of this access method
""", 
       json.parse("""{
  "consentIds" : "",
  "paymentIds" : ""
}"""),
       json.parse("""{
  "basketId" : "1234-basket-567",
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
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
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "self" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithTransactionAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "status" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "ACCP",
  "psuMessage" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val createSigningBasket : OBPEndpoint = {
       case "v1":: "signing-baskets" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "basketId" : "1234-basket-567",
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
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
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "self" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithTransactionAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "status" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "ACCP",
  "psuMessage" : { }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteSigningBasket, 
       apiVersion, 
       "deleteSigningBasket",
       "DELETE", 
       "/v1/signing-baskets/BASKETID", 
       "Delete the signing basket",
       """Delete the signing basket structure as long as no (partial) authorisation has yet been applied. 
The undlerying transactions are not affected by this deletion.

Remark: The signing basket as such is not deletable after a first (partial) authorisation has been applied. 
Nevertheless, single transactions might be cancelled on an individual basis on the XS2A interface.
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val deleteSigningBasket : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasket, 
       apiVersion, 
       "getSigningBasket",
       "GET", 
       "/v1/signing-baskets/BASKETID", 
       "Returns the content of an signing basket object.",
       """Returns the content of an signing basket object.""", 
       json.parse(""""""),
       json.parse("""{
  "transactionStatus" : "ACCP",
  "payments" : "",
  "consents" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasket : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "transactionStatus" : "ACCP",
  "payments" : "",
  "consents" : ""
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketAuthorisation, 
       apiVersion, 
       "getSigningBasketAuthorisation",
       "GET", 
       "/v1/signing-baskets/BASKETID/authorisations", 
       "Get Signing Basket Authorisation Sub-Resources Request",
       """Read a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""", 
       json.parse(""""""),
       json.parse("""{
  "authorisationIds" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasketAuthorisation : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "authorisationIds" : ""
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketScaStatus, 
       apiVersion, 
       "getSigningBasketScaStatus",
       "GET", 
       "/v1/signing-baskets/BASKETID/authorisations/AUTHORISATIONID", 
       "Read the SCA status of the signing basket authorisation",
       """This method returns the SCA status of a signing basket's authorisation sub-resource.
""", 
       json.parse(""""""),
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasketScaStatus : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketStatus, 
       apiVersion, 
       "getSigningBasketStatus",
       "GET", 
       "/v1/signing-baskets/BASKETID/status", 
       "Read the status of the signing basket",
       """Returns the status of a signing basket object. 
""", 
       json.parse(""""""),
       json.parse("""{
  "transactionStatus" : "RCVD"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasketStatus : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "transactionStatus" : "RCVD"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       startSigningBasketAuthorisation, 
       apiVersion, 
       "startSigningBasketAuthorisation",
       "POST", 
       "/v1/signing-baskets/BASKETID/authorisations", 
       "Start the authorisation process for a signing basket",
       """Create an authorisation sub-resource and start the authorisation process of a signing basket. 
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
       json.parse(""""""),
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
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val startSigningBasketAuthorisation : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updateSigningBasketPsuData, 
       apiVersion, 
       "updateSigningBasketPsuData",
       "PUT", 
       "/v1/signing-baskets/BASKETID/authorisations/AUTHORISATIONID", 
       "Update PSU Data for signing basket",
       """This method update PSU data on the signing basket resource if needed. 
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
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val updateSigningBasketPsuData : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""""), callContext)
           }
         }
       }

  }
}



