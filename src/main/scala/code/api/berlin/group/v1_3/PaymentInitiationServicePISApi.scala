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
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

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
       """This method initiates the cancellation of a payment. 
Depending on the payment-service, the payment-product and the ASPSP's implementation, 
this TPP call might be sufficient to cancel a payment. 
If an authorisation of the payment cancellation is mandated by the ASPSP, 
a corresponding hyperlink will be contained in the response message.

Cancels the addressed payment with resource identification paymentId if applicable to the payment-service, payment-product and received in product related timelines (e.g. before end of business day for scheduled payments of the last business day before the scheduled execution day). 

The response to this DELETE command will tell the TPP whether the 
  * access method was rejected
  * access method was successful, or
  * access method is generally applicable, but further authorisation processes are needed.
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
  "_links" : {
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "ACCP"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val cancelPayment : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid :: Nil JsonDelete _ => {
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
  "_links" : {
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "transactionStatus" : "ACCP"
}"""), callContext)
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
       """This method returns the SCA status of a payment initiation's authorisation sub-resource.
""", 
       json.parse(""""""),
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val getPaymentCancellationScaStatus : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: cancellationid :: Nil JsonGet _ => {
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
       getPaymentInformation, 
       apiVersion, 
       "getPaymentInformation",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID", 
       "Get Payment Information",
       """Returns the content of a payment object""", 
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val getPaymentInformation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""""), callContext)
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
       """Read a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""", 
       json.parse(""""""),
       json.parse("""{
  "authorisationIds" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val getPaymentInitiationAuthorisation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: Nil JsonGet _ => {
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
       getPaymentInitiationCancellationAuthorisationInformation, 
       apiVersion, 
       "getPaymentInitiationCancellationAuthorisationInformation",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations", 
       "Will deliver an array of resource identifications to all generated cancellation authorisation sub-resources.",
       """Retrieve a list of all created cancellation authorisation sub-resources.
""", 
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val getPaymentInitiationCancellationAuthorisationInformation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""""), callContext)
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
       """This method returns the SCA status of a payment initiation's authorisation sub-resource.
""", 
       json.parse(""""""),
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val getPaymentInitiationScaStatus : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
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
       getPaymentInitiationStatus, 
       apiVersion, 
       "getPaymentInitiationStatus",
       "GET", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/status", 
       "Payment initiation status request",
       """Check the transaction status of a payment initiation.""", 
       json.parse(""""""),
       json.parse("""{
  "transactionStatus" : "ACCP"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val getPaymentInitiationStatus : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "transactionStatus" : "ACCP"
}"""), callContext)
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
       """This method is used to initiate a payment at the ASPSP.

## Variants of Payment Initiation Requests

This method to initiate a payment initiation at the ASPSP can be sent with either a JSON body or an pain.001 body depending on the payment product in the path.

There are the following **payment products**:

  - Payment products with payment information in *JSON* format:
    - ***sepa-credit-transfers***
    - ***instant-sepa-credit-transfers***
    - ***target-2-payments***
    - ***cross-border-credit-transfers***
  - Payment products with payment information in *pain.001* XML format:
    - ***pain.001-sepa-credit-transfers***
    - ***pain.001-instant-sepa-credit-transfers***
    - ***pain.001-target-2-payments***
    - ***pain.001-cross-border-credit-transfers***

Furthermore the request body depends on the **payment-service**
  * ***payments***: A single payment initiation request.
  * ***bulk-payments***: A collection of several payment iniatiation requests.
  
    In case of a *pain.001* message there are more than one payments contained in the *pain.001 message.
    
    In case of a *JSON* there are several JSON payment blocks contained in a joining list.
  * ***periodic-payments***: 
    Create a standing order initiation resource for recurrent i.e. periodic payments addressable under {paymentId} 
     with all data relevant for the corresponding payment product and the execution of the standing order contained in a JSON body. 

This is the first step in the API to initiate the related recurring/periodic payment.
  
## Single and mulitilevel SCA Processes

The Payment Initiation Requests are independent from the need of one ore multilevel 
SCA processing, i.e. independent from the number of authorisations needed for the execution of payments. 

But the response messages are specific to either one SCA processing or multilevel SCA processing. 

For payment initiation with multilevel SCA, this specification requires an explicit start of the authorisation, 
i.e. links directly associated with SCA processing like 'scaRedirect' or 'scaOAuth' cannot be contained in the 
response message of a Payment Initation Request for a payment, where multiple authorisations are needed. 
Also if any data is needed for the next action, like selecting an SCA method is not supported in the response, 
since all starts of the multiple authorisations are fully equal. 
In these cases, first an authorisation sub-resource has to be generated following the 'startAuthorisation' link.
""", 
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val initiatePayment : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""""), callContext)
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
       """Create an authorisation sub-resource and start the authorisation process. 
The message might in addition transmit authentication and authorisation related data. 

This method is iterated n times for a n times SCA authorisation in a 
corporate context, each creating an own authorisation sub-endpoint for 
the corresponding PSU authorising the transaction.

The ASPSP might make the usage of this access method unnecessary in case 
of only one SCA process needed, since the related authorisation resource 
might be automatically created by the ASPSP after the submission of the 
payment data with the first POST payments/{payment-product} call.

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
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val startPaymentAuthorisation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: Nil JsonPost _ => {
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
       startPaymentInitiationCancellationAuthorisation, 
       apiVersion, 
       "startPaymentInitiationCancellationAuthorisation",
       "POST", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations", 
       "Start the authorisation process for the cancellation of the addressed payment",
       """Creates an authorisation sub-resource and start the authorisation process of the cancellation of the addressed payment. 
The message might in addition transmit authentication and authorisation related data.

This method is iterated n times for a n times SCA authorisation in a 
corporate context, each creating an own authorisation sub-endpoint for 
the corresponding PSU authorising the cancellation-authorisation.

The ASPSP might make the usage of this access method unnecessary in case 
of only one SCA process needed, since the related authorisation resource 
might be automatically created by the ASPSP after the submission of the 
payment data with the first POST payments/{payment-product} call.

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
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val startPaymentInitiationCancellationAuthorisation : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: Nil JsonPost _ => {
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
       updatePaymentCancellationPsuData, 
       apiVersion, 
       "updatePaymentCancellationPsuData",
       "PUT", 
       "/v1/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations/CANCELLATIONID", 
       "Update PSU Data for payment initiation cancellation",
       """This method updates PSU data on the cancellation authorisation resource if needed. 
It may authorise a cancellation of the payment within the Embedded SCA Approach where needed.

Independently from the SCA Approach it supports e.g. the selection of 
the authentication method and a non-SCA PSU authentication.

This methods updates PSU data on the cancellation authorisation resource if needed. 

There are several possible Update PSU Data requests in the context of a cancellation authorisation within the payment initiation services needed, 
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
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val updatePaymentCancellationPsuData : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "cancellation-authorisations" :: cancellationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""""), callContext)
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
       """This methods updates PSU data on the authorisation resource if needed. 
It may authorise a payment within the Embedded SCA Approach where needed.

Independently from the SCA Approach it supports e.g. the selection of 
the authentication method and a non-SCA PSU authentication.

There are several possible Update PSU Data requests in the context of payment initiation services needed, 
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
       PaymentInitiationServicePISApi :: apiTagDummyData :: Nil
     )

     lazy val updatePaymentPsuData : OBPEndpoint = {
       case "v1" :: payment_service :: payment_product :: paymentid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
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



