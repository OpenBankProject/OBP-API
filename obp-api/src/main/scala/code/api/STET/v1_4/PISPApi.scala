package code.api.STET.v1_4

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global

object APIMethods_PISPApi extends RestHelper {
    val apiVersion =  OBP_STET_1_4.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      paymentRequestConfirmationPost ::
      paymentRequestPut ::
      paymentRequestsGet ::
      paymentRequestsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       paymentRequestConfirmationPost, 
       apiVersion, 
       nameOf(paymentRequestConfirmationPost),
       "POST", 
       "/payment-requests/PAYMENTREQUESTRESOURCEID/confirmation", 
       "Confirmation of a payment request or a modification request (PISP)",
       s"""${mockedDataText(true)}
            ### Description

The PISP confirms one of the following requests

* payment request on behalf of a merchant
* transfer request on behalf of the account's owner
* standing-order request on behalf of the account's owner

The ASPSP answers with a status of the relevant request and the subsequent Credit Transfer.

### Prerequisites

* The TPP has been registered by the Registration Authority for the PISP role
* The TPP was provided with an OAUTH2 "Client Credential" access token by the ASPSP (cf. § 3.4.3).
* The TPP has previously posted a Request which has been saved by the ASPSP (cf. § 4.5.3)
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its "OAUTH2 Client Credential" access token

### Business flow

Once the PSU has been authenticated, it is the due to the PISP to confirm the Request to the ASPSP in order to complete the process flow.  
In REDIRECT and DECOUPLED approach, this confirmation is not a prerequisite to the execution of the Credit Transfer.  

            """,
       json.parse("""{
  "psuAuthenticationFactor" : "JJKJKJ788GKJKJBK"
}"""),
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestConfirmationPost : OBPEndpoint = {
       case "payment-requests" :: paymentrequestresourceid:: "confirmation" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       paymentRequestPut, 
       apiVersion, 
       nameOf(paymentRequestPut),
       "PUT", 
       "/payment-requests/PAYMENTREQUESTRESOURCEID", 
       "Modification of a Payment/Transfer Request (PISP)",
       s"""${mockedDataText(true)}
            ### Description

The PISP sent a Payment/Transfer Request through a POST command.  
The ASPSP registered the Payment/Transfer Request, updated if necessary the relevant identifiers in order to avoid duplicates and returned the location of the updated Request.  
The PISP got the Payment/Transfer Request that has been updated with the resource identifiers, and eventually the status of the Payment/Transfer Request and the status of the subsequent credit transfer.  
The PISP request for the payment cancellation (global cancellation) or for some payment instructions cancellation (partial cancellation)  
No other modification of the Payment/Transfer Request is allowed.

### Prerequisites

* The TPP was registered by the Registration Authority for the PISP role
* The TPP was provided with an OAUTH2 "Client Credential" access token by the ASPSP (cf. § 3.4.3).
* The TPP previously posted a Payment/Transfer Request which was saved by the ASPSP (cf. § 4.5.3)
* The TPP and the ASPSP successfully processed a mutual check and authentication
* The TPP presented its "OAUTH2 Client Credential" access token.
* The TPP presented the payment/transfer request.
* The PSU was successfully authenticated.

### Business flow

the following cases can be applied:

* Case of a payment with multiple instructions or a standing order, the PISP asks to cancel the whole Payment/Transfer or Standing Order Request including all non-executed payment instructions by setting the [paymentInformationStatus] to "RJCT" and the relevant [statusReasonInformation] to "DS02" at payment level.
* Case of a payment with multiple instructions, the PISP asks to cancel one or several payment instructions by setting the [transactionStatus] to "RJCT" and the relevant [statusReasonInformation] to "DS02" at each relevant instruction level.

Since the modification request needs a PSU authentication before committing, the modification request includes:

<!-- -->

* The specification of the authentication approaches that are supported by the PISP (any combination of "REDIRECT", "EMBEDDED" and "DECOUPLED" values).
* In case of possible REDIRECT or DECOUPLED authentication approach, one or two call-back URLs to be used by the ASPSP at the finalisation of the authentication and consent process :
* In case of possible "EMBEDDED" or "DECOUPLED" approaches, a PSU identifier that can be processed by the ASPSP for PSU recognition.

* The ASPSP saves the updated Payment/Transfer Request and answers to the PISP. The answer embeds


            """,
       json.parse("""{
  "paymentInformationId" : "MyPmtInfId",
  "creationDateTime" : "2018-03-31T13:25:22.527+02:00",
  "numberOfTransactions" : 1,
  "initiatingParty" : {
    "name" : "MyPreferedPisp",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "organisationId" : {
      "identification" : "12FR5",
      "schemeName" : "COID",
      "issuer" : "ACPR"
    }
  },
  "paymentTypeInformation" : {
    "serviceLevel" : "SEPA",
    "localInstrument" : "INST",
    "categoryPurpose" : "DVPM"
  },
  "debtor" : {
    "name" : "MyCustomer",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "privateId" : {
      "identification" : "FD37G",
      "schemeName" : "BANK",
      "issuer" : "BICXYYTTZZZ"
    }
  },
  "creditor" : {
    "name" : "myMerchant",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "organisationId" : {
      "identification" : "852126789",
      "schemeName" : "SIREN",
      "issuer" : "FR"
    }
  },
  "creditorAccount" : {
    "iban" : "YY64COJH41059545330222956960771321"
  },
  "ultimateCreditor" : {
    "name" : "myPreferedUltimateMerchant",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "organisationId" : {
      "identification" : "85212678900025",
      "schemeName" : "SIRET",
      "issuer" : "FR"
    }
  },
  "purpose" : "COMC",
  "chargeBearer" : "SLEV",
  "creditTransferTransaction" : [ {
    "paymentId" : {
      "instructionId" : "MyInstrId",
      "endToEndId" : "MyEndToEndId"
    },
    "requestedExecutionDate" : "2016-12-31T00:00:00.000+01:00",
    "instructedAmount" : {
      "currency" : "EUR",
      "amount" : "124.35"
    },
    "remittanceInformation" : [ "MyRemittanceInformation" ]
  } ],
  "supplementaryData" : {
    "acceptedAuthenticationApproach" : [ "REDIRECT", "DECOUPLED" ],
    "successfulReportUrl" : "http://myPisp/PaymentSuccess",
    "unsuccessfulReportUrl" : "http://myPisp/PaymentFailure"
  }
}"""),
       json.parse("""{
  "appliedAuthenticationApproach" : {
    "appliedAuthenticationApproach" : "REDIRECT"
  },
  "_links" : {
    "consentApproval" : {
      "href" : "https://psd2.aspsp/consent-approval"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestPut : OBPEndpoint = {
       case "payment-requests" :: paymentrequestresourceid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "appliedAuthenticationApproach" : {
    "appliedAuthenticationApproach" : "REDIRECT"
  },
  "_links" : {
    "consentApproval" : {
      "href" : "https://psd2.aspsp/consent-approval"
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       paymentRequestsGet, 
       apiVersion, 
       nameOf(paymentRequestsGet),
       "GET", 
       "/payment-requests/PAYMENTREQUESTRESOURCEID", 
       "Retrieval of a payment request (PISP)",
       s"""${mockedDataText(true)}
            ### Description

The following use cases can be applied:

* retrieval of a payment request on behalf of a merchant
* retrieval of a transfer request on behalf of the account's owner
* retrieval of a standing-order request on behalf of the account's owner

The PISP has sent a Request through a POST command.   
The ASPSP has registered the Request, updated if necessary the relevant identifiers in order to avoid duplicates and returned the location of the updated Request.  
The PISP gets the Request that has been updated with the resource identifiers, and eventually the status of the Payment/Transfer Request and the status of the subsequent credit transfer.

### Prerequisites

* The TPP has been registered by the Registration Authority for the PISP role
* The TPP was provided with an OAUTH2 "Client Credential" access token by the ASPSP (cf. § 3.4.3).
* The TPP has previously posted a Request which has been saved by the ASPSP (cf. § 4.5.3)
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its "OAUTH2 Client Credential" access token

### Business flow

The PISP asks to retrieve the Payment/Transfer Request that has been saved by the ASPSP. The PISP uses the location link provided by the ASPSP in response of the posting of this request.  
The ASPSP returns the previously posted Payment/Transfer Request which is enriched with:

* The resource identifiers given by the ASPSP
* The status information of the Payment Request and of the subsequent credit transfer

The status information must be available during at least 30 calendar days after the posting of the Payment Request. However, the ASPSP may increase this availability duration, based on its own rules.  

            """,
       emptyObjectJson,
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestsGet : OBPEndpoint = {
       case "payment-requests" :: paymentrequestresourceid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       paymentRequestsPost, 
       apiVersion, 
       nameOf(paymentRequestsPost),
       "POST", 
       "/payment-requests", 
       "Payment request initiation (PISP)",
       s"""${mockedDataText(true)}
            ### Description

The following use cases can be applied:

* payment request on behalf of a merchant
* transfer request on behalf of the account's owner
* standing-order request on behalf of the account's owner

#### Data content

A payment request or a transfer request might embed several payment instructions having

* one single execution date or multiple execution dates
* one single beneficiary or multiple beneficiaries

Having at the same time multiple beneficiaries and multiple execution date might not be a relevant business case, although it is technically allowed.  
Each implementation will have to specify which business use cases are actually supported.  
A standing order request must embed one single payment instruction and must address one single beneficiary.

* The beneficiary must be set at the payment level
* The standing order specific characteristics (start date, periodicity...) must be set at the instruction level

Payment request can rely for execution on different payment instruments: - SEPA Credit Transfer (SCT) - Domestic Credit Transfer in a non Euro-currency - International payment The following table indicates how to use the different fields, depending on the payment instrument:

|                                      Structure                                      |                             SEPA payments                             |           Domestic payments in non-euro currency           |                                                          International payments                                                           |
|-------------------------------------------------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| PaymentTypeInformation/ InstructionPriority (payment level)                         | "HIGH" for high-priority SCT "NORM" for other SCT Ignored for SCTInst | "HIGH" for high-priority CT "NORM" or ignored for other CT | "HIGH" for high-priority payments "NORM" or ignored for other payments                                                                    |
| PaymentTypeInformation/ ServiceLevel (payment level)                                | "SEPA" for SCT and SCTInst                                            | ignored                                                    | ignored                                                                                                                                   |
| PaymentTypeInformation/ CategoryPurpose (payment level)                             | "CASH" for transfer request "DVPM" for payment request on behalf of a merchant                                                    || "CORT" for generic international payments "INTC" for transfers between two branches within the same company "TREA" for treasury transfers |
| PaymentTypeInformation/ LocalInstrument (payment level)                             | "INST" pour les SCTInst Otherwise ignored                             | ignored or valued with ISO20022 external code list values                                                                                                                                             ||
| RequestedExecutionDate (either at payment or transaction level)                     | Mandatory (indicates the date on debit on the ordering party account)                                                                                                                                                                                                        |||
| InstructedAmount (at each transaction level)                                        | Mandatory                                                                                                                                                                                                                                                                    |||
| ChargeBearer (at each transaction level)                                            | "SLEV" for SCT and SCTInst                                            | "SLEV" or "SHAR"                                           | "CRED", "DEBT" or "SHAR"                                                                                                                  |
| Purpose (at payment level)                                                          | Optional                                                                                                                                                                                                                                                                     |||
| RegulatoryReportingCode (at each transaction level)                                 | Not used                                                                                                                          || Mandatory (possibly multiple values)                                                                                                      |
| RemittanceInformation                                                               | Optional Unstructured                                                                                                                                                                                                                                                        |||
| Debtor (at payment level)                                                           | Mandatory 2 address lines only                                        | Mandatory 4 address lines only                                                                                                                                                                        ||
| DebtorAccount (at payment level)                                                    | Optional                                                              | Optional Account currency may be specified                                                                                                                                                            ||
| DebtorAgent (at payment level)                                                      | Optional                                                                                                                                                                                                                                                                     |||
| Creditor (either at payment or transaction level)                                   | Mandatory 2 address lines only                                                                                                                                                                                                                                               |||
| CreditorAccount (either at payment or transaction level)                            | Mandatory                                                             | Mandatory Account currency may be specified                                                                                                                                                           ||
| CreditorAgent (either at payment or transaction level)                              | Optional                                                                                                                                                                                                                                                                     |||
| UltimateCreditor (either at payment or transaction level)                           | Optional                                                                                                                                                                                                                                                                     |||
| ClearingSystemId et ClearingSystemMemberId (either at payment or transaction level) | Not used                                                                                                                          || Optional                                                                                                                                  |

<br />

#### Prerequisites for all use cases

* The TPP has been registered by the Registration Authority for the PISP role
* The TPP was provided with an OAUTH2 "Client Credential" access token by the ASPSP (cf. § 3.4.3).
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its "OAUTH2 Client Credential" access token

#### Business flow

##### Payment Request use case

The PISP forwards a payment request on behalf of a merchant.  
The PSU buys some goods or services on an e-commerce website held by a merchant. Among other payment method, the merchant suggests the use of a PISP service. As there is obviously a contract between the merchant and the PISP, there is no need of such a contract between the PSU and this PISP to initiate the process.  
Case of the PSU that chooses to use the PISP service:

* The merchant forwards the requested payment characteristics to the PISP and redirects the PSU to the PISP portal.
* The PISP requests from the PSU which ASPSP will be used.
* The PISP prepares the Payment Request and sends this request to the ASPSP.
* The Request can embed several payment instructions having different requested execution date.
* The beneficiary, as being the merchant, is set at the payment level.

##### Transfer Request use case

The PISP forwards a transfer request on behalf of the owner of the account.

* The PSU provides the PISP with all information needed for the transfer.
* The PISP prepares the Transfer Request and sends this request to the relevant ASPSP that holds the debtor account.
* The Request can embed several payment instructions having different beneficiaries.
* The requested execution date, as being the same for all instructions, is set at the payment level.

##### Standing Order Request use case

The PISP forwards a Standing Order request on behalf of the owner of the account.

* The PSU provides the PISP with all information needed for the Standing Order.
* The PISP prepares the Standing Order Request and sends this request to the relevant ASPSP that holds the debtor account.
* The Request embeds one single payment instruction with

#### Authentication flows for all use cases

As the request posted by the PISP to the ASPSP needs a PSU authentication before execution, this request will include:

* The specification of the authentication approaches that are supported by the PISP (any combination of "REDIRECT", "EMBEDDED" and "DECOUPLED" values).
* In case of possible REDIRECT or DECOUPLED authentication approach, one or two call-back URLs to be used by the ASPSP at the finalisation of the authentication and consent process :
* In case of possible "EMBEDDED" or "DECOUPLED" approaches, the PSU identifier that can be processed by the ASPSP for PSU recognition must have been set within the request body [debtor] structure.

The ASPSP saves the request and answers to the PISP. The answer embeds:

<!-- -->

* A location link of the saved Request that will be further used to retrieve the Request and its status information.
* The specification of the chosen authentication approach taking into account both the PISP and the PSU capabilities.
* In case of chosen REDIRECT authentication approach, the URL to be used by the PISP for redirecting the PSU in order to perform a authentication.

Case of the PSU neither gives nor denies his/her consent, the Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.

##### Redirect authentication approach

When the chosen authentication approach within the ASPSP answers is set to "REDIRECT":

* The PISP redirects the PSU to the ASPSP which authenticates the PSU
* The ASPSP asks the PSU to give (or deny) his/her consent to the Payment Request
* The PSU chooses or confirms which of his/her accounts shall be used by the ASPSP for the future Credit Transfer.
* The ASPSP is then able to initiate the subsequent Credit Transfer
* The ASPSP redirects the PSU to the PISP using one of the call-back URLs provided within the posted Payment Request

![](https://www.stet.eu//assets/files/documents-api/pisp-redirect-authentication.png) ![](https://www.stet.eu//assets/files/documents-api/pisp-redirect-authentication2.png)

##### Decoupled authentication approach

When the chosen authentication approach is "DECOUPLED":

* Based on the PSU identifier provided within the Payment Request by the PISP, the ASPSP gives the PSU with the Payment Request details and challenges the PSU for a Strong Customer Authentication on a decoupled device or application.
* The PSU chooses or confirms which of his/her accounts shall be used by the ASPSP for the future Credit Transfer.
* The ASPSP is then able to initiate the subsequent Credit Transfer
* The ASPSP notifies the PISP about the finalisation of the authentication and consent process by using one of the call-back URLs provided within the posted Payment Request

![](https://www.stet.eu//assets/files/documents-api/pisp-decoupled-authentication.png) ![](https://www.stet.eu//assets/files/documents-api/pisp-decoupled-authentication2.png)

##### Embedded authentication approach

When the chosen authentication approach within the ASPSP answers is set to "EMBEDDED":

* The TPP informs the PSU that a challenge is needed for completing the Payment Request processing. This challenge will be one of the following:
* The PSU unlock the device or application through a "knowledge factor" and/or an "inherence factor" (biometric), retrieves the Payment Request details and processes the data sent by the ASPSP;
* The PSU might choose or confirm which of his/her accounts shall be used by the ASPSP for the future Credit Transfer when the device or application allows it.
* When agreeing the Payment Request, the PSU enters the resulting authentication factor through the PISP interface which will forward it to the ASPSP through a confirmation request (cf. § 4.7)

![](https://www.stet.eu//assets/files/documents-api/pisp-embedded-authentication.png) ![](https://www.stet.eu//assets/files/documents-api/pisp-embedded-authentication2.png)

            """,
       json.parse("""{
  "paymentInformationId" : "MyPmtInfId",
  "creationDateTime" : "2018-03-31T13:25:22.527+02:00",
  "numberOfTransactions" : 1,
  "initiatingParty" : {
    "name" : "MyPreferedPisp",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "organisationId" : {
      "identification" : "12FR5",
      "schemeName" : "COID",
      "issuer" : "ACPR"
    }
  },
  "paymentTypeInformation" : {
    "serviceLevel" : "SEPA",
    "localInstrument" : "INST",
    "categoryPurpose" : "DVPM"
  },
  "debtor" : {
    "name" : "MyCustomer",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "privateId" : {
      "identification" : "FD37G",
      "schemeName" : "BANK",
      "issuer" : "BICXYYTTZZZ"
    }
  },
  "creditor" : {
    "name" : "myMerchant",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "organisationId" : {
      "identification" : "852126789",
      "schemeName" : "SIREN",
      "issuer" : "FR"
    }
  },
  "creditorAccount" : {
    "iban" : "YY64COJH41059545330222956960771321"
  },
  "ultimateCreditor" : {
    "name" : "myPreferedUltimateMerchant",
    "postalAddress" : {
      "country" : "FR",
      "addressLine" : [ "18 rue de la DSP2", "75008 PARIS" ]
    },
    "organisationId" : {
      "identification" : "85212678900025",
      "schemeName" : "SIRET",
      "issuer" : "FR"
    }
  },
  "purpose" : "COMC",
  "chargeBearer" : "SLEV",
  "creditTransferTransaction" : [ {
    "paymentId" : {
      "instructionId" : "MyInstrId",
      "endToEndId" : "MyEndToEndId"
    },
    "requestedExecutionDate" : "2016-12-31T00:00:00.000+01:00",
    "instructedAmount" : {
      "currency" : "EUR",
      "amount" : "124.35"
    },
    "remittanceInformation" : [ "MyRemittanceInformation" ]
  } ],
  "supplementaryData" : {
    "acceptedAuthenticationApproach" : [ "REDIRECT", "DECOUPLED" ],
    "successfulReportUrl" : "http://myPisp/PaymentSuccess",
    "unsuccessfulReportUrl" : "http://myPisp/PaymentFailure"
  }
}"""),
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestsPost : OBPEndpoint = {
       case "payment-requests" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

}



