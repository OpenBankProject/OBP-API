
package code.api.STET.v1_4

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import com.github.dwickern.macros.NameOf.nameOf
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.api.STET.v1_4.OBP_STET_1_4
import code.api.util.ApiTag

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
&lt;h3&gt;Description&lt;/h3&gt;
  The PISP confirms one of the following requests&lt;br&gt;
  &lt;ul&gt;
    &lt;li&gt;payment request on behalf of a merchant&lt;/li&gt;
    &lt;li&gt;transfer request on behalf of the account&#39;s owner&lt;/li&gt;
    &lt;li&gt;standing-order request on behalf of the account&#39;s owner&lt;/li&gt;
  &lt;/ul&gt;
  The ASPSP answers with a status of the relevant request and the subsequent Credit Transfer.
&lt;h3&gt;Prerequisites&lt;/h3&gt;
  &lt;ul&gt;
    &lt;li&gt; The TPP has been registered by the Registration Authority for the PISP role&lt;/li&gt;
    &lt;li&gt; The TPP was provided with an OAUTH2 &quot;Client Credential&quot; access token by the ASPSP (cf. § 3.4.3).&lt;/li&gt;
    &lt;li&gt; The TPP has previously posted a Request which has been saved by the ASPSP (cf. § 4.5.3)&lt;/li&gt;
    &lt;ul&gt;
      &lt;li&gt;The ASPSP has answered with a location link to the saved Payment Request (cf. § 4.5.4)&lt;/li&gt;
      &lt;li&gt; The TPP has retrieved the saved request in order to get the relevant resource Ids (cf. § 4.6).&lt;/li&gt;
    &lt;/ul&gt;
    &lt;li&gt; The TPP and the ASPSP have successfully processed a mutual check and authentication &lt;/li&gt;
    &lt;li&gt; The TPP has presented its &quot;OAUTH2 Client Credential&quot; access token &lt;/li&gt;
  &lt;/ul&gt;
&lt;h3&gt;Business flow&lt;/h3&gt;
  Once the PSU has been authenticated, it is the due to the PISP to confirm the Request to the ASPSP in order to complete the process flow.&lt;br&gt;
  In REDIRECT and DECOUPLED approach, this confirmation is not a prerequisite to the execution of the Credit Transfer.&lt;br&gt;
""", 
       json.parse("""{
  "psuAuthenticationFactor" : "JJKJKJ788GKJKJBK"
}"""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestConfirmationPost : OBPEndpoint = {
       case "payment-requests" :: paymentrequestresourceid:: "confirmation" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
&lt;h3&gt;Description&lt;/h3&gt;
The PISP sent a Payment/Transfer Request through a POST command.&lt;br&gt;
  The ASPSP registered the Payment/Transfer Request, updated if necessary the relevant identifiers in order to avoid duplicates and returned the location of the updated Request.&lt;br&gt;
  The PISP got the Payment/Transfer Request that has been updated with the resource identifiers, and eventually the status of the Payment/Transfer Request and the status of the subsequent credit transfer.&lt;br&gt;
  The PISP request for the payment cancellation (global cancellation) or for some payment instructions cancellation (partial cancellation)&lt;br&gt;
  No other modification of the Payment/Transfer Request is allowed.&lt;br/&gt;
&lt;h3&gt;Prerequisites&lt;/h3&gt;
&lt;ul&gt;
  &lt;li&gt;The TPP was registered by the Registration Authority for the PISP role&lt;/li&gt;
  &lt;li&gt;The TPP was provided with an OAUTH2 &quot;Client Credential&quot; access token by the ASPSP (cf. § 3.4.3).&lt;/li&gt;
  &lt;li&gt;The TPP previously posted a Payment/Transfer Request which was saved by the ASPSP (cf. § 4.5.3)&lt;/li&gt;
  &lt;ul&gt;
    &lt;li&gt;The ASPSP answered with a location link to the saved Payment/Transfer Request (cf. § 4.5.4)&lt;/li&gt;
    &lt;li&gt;The PISP retrieved the saved Payment/Transfer Request (cf. § 4.5.4)&lt;/li&gt;
  &lt;/ul&gt;
  &lt;li&gt;The TPP and the ASPSP successfully processed a mutual check and authentication &lt;/li&gt;
  &lt;li&gt;The TPP presented its &quot;OAUTH2 Client Credential&quot; access token.&lt;/li&gt;
  &lt;li&gt;The TPP presented the payment/transfer request.&lt;/li&gt;
  &lt;li&gt;The PSU was successfully authenticated.&lt;/li&gt;
&lt;/ul&gt;
&lt;h3&gt;Business flow&lt;/h3&gt;
the following cases can be applied:
&lt;ul&gt;
  &lt;li&gt;Case of a payment with multiple instructions or a standing order, the PISP asks to cancel the whole Payment/Transfer or Standing Order Request including all non-executed payment instructions by setting the [paymentInformationStatus] to &quot;RJCT&quot; and the relevant [statusReasonInformation] to &quot;DS02&quot; at payment level.&lt;/li&gt;
  &lt;li&gt;Case of a payment with multiple instructions, the PISP asks to cancel one or several payment instructions by setting the [transactionStatus] to &quot;RJCT&quot; and the relevant [statusReasonInformation] to &quot;DS02&quot; at each relevant instruction level.&lt;/li&gt;
&lt;/ul&gt;
Since the modification request needs a PSU authentication before committing, the modification request includes:&lt;/li&gt;
  &lt;ul&gt;
    &lt;li&gt;The specification of the authentication approaches that are supported by the PISP (any combination of &quot;REDIRECT&quot;, &quot;EMBEDDED&quot; and &quot;DECOUPLED&quot; values).&lt;/li&gt;
    &lt;li&gt;In case of possible REDIRECT or DECOUPLED authentication approach, one or two call-back URLs to be used by the ASPSP at the finalisation of the authentication and consent process :&lt;/li&gt;
    &lt;ul&gt;
      &lt;li&gt;The first call-back URL will be called by the ASPSP if the Transfer Request is processed without any error or rejection by the PSU&lt;/li&gt;
      &lt;li&gt;The second call-back URL is to be used by the ASPSP in case of processing error or rejection by the PSU. Since this second URL is optional, the PISP might not provide it. In this case, the ASPSP will use the same URL for any processing result.&lt;/li&gt;
      &lt;li&gt;Both call-back URLS must be used in a TLS-secured request.&lt;/li&gt;
    &lt;/ul&gt;
    &lt;li&gt;In case of possible &quot;EMBEDDED&quot; or &quot;DECOUPLED&quot; approaches, a PSU identifier that can be processed by the ASPSP for PSU recognition.&lt;/li&gt;
  &lt;/ul&gt;
  &lt;li&gt;The ASPSP saves the updated Payment/Transfer Request and answers to the PISP. The answer embeds &lt;/li&gt;
  &lt;ul&gt;
    &lt;li&gt;The specification of the chosen authentication approach taking into account both the PISP and the PSU capabilities.&lt;/li&gt;
    &lt;li&gt;In case of chosen REDIRECT authentication approach, the URL to be used by the PISP for redirecting the PSU in order to perform an authentication.&lt;/li&gt;
  &lt;/ul&gt;
&lt;/ul&gt;
&lt;h3&gt;Authentication flows for both use cases&lt;/h3&gt;
&lt;h4&gt;Redirect authentication approach &lt;/h4&gt;
When the chosen authentication approach within the ASPSP answers is set to &quot;REDIRECT&quot;:&lt;br&gt;
&lt;ul&gt;
  &lt;li&gt;The PISP redirects the PSU to the ASPSP which authenticates the PSU &lt;/li&gt;
  &lt;li&gt;The ASPSP asks the PSU to give (or deny) his/her consent to the Payment Request global or partial Cancellation&lt;/li&gt;
  &lt;li&gt;The ASPSP is then able to initiate the subsequent cancellation&lt;/li&gt;
  &lt;li&gt;The ASPSP redirects the PSU to the PISP using one of the call-back URLs provided within the posted Payment Request cancellation&lt;/li&gt;
&lt;/ul&gt;
If the PSU neither gives nor denies his/her consent, the Cancellation Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.&lt;br&gt;
&lt;h4&gt;Decoupled authentication approach&lt;/h4&gt;
When the chosen authentication approach is &quot;DECOUPLED&quot;:&lt;br&gt;
&lt;ul&gt;
  &lt;li&gt;Based on the PSU identifier provided within the Payment Request by the PISP, the ASPSP provides the PSU with the Cancellation Request details and challenges the PSU for a Strong Customer Authentication on a decoupled device or application.&lt;/li&gt;
  &lt;li&gt;The PSU confirms or not the Payment Request global or partial Cancellation&lt;/li&gt;
  &lt;li&gt;The ASPSP is then able to initiate the subsequent cancellation&lt;/li&gt;
  &lt;li&gt;The ASPSP notifies the PISP about the finalisation of the authentication and cancellation process by using one of the call-back URLs provided within the posted Payment Request&lt;/li&gt;
&lt;/ul&gt;
If the PSU neither gives nor denies his/her consent, the Cancellation Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.&lt;br&gt;
&lt;h4&gt;Embedded authentication approach&lt;/h4&gt;
When the chosen authentication approach within the ASPSP answers is set to &quot;EMBEDDED&quot;:&lt;br&gt;
&lt;ul&gt;
  &lt;li&gt;The TPP informs the PSU that a challenge is needed for completing the Payment Request cancellation processing. This challenge will be one of the following:&lt;/li&gt;
  &lt;ul&gt;
    &lt;li&gt;A One-Time-Password sent by the ASPSP to the PSU on a separate device or application.&lt;/li&gt;
    &lt;li&gt;A response computed by a specific device on base of a challenge sent by the ASPSP to the PSU on a separate device or application.&lt;/li&gt;
  &lt;/ul&gt;
  &lt;li&gt;The PSU unlock the device or application through a &quot;knowledge factor&quot; and/or an &quot;inherence factor&quot; (biometric), retrieves the cancellation details.&lt;/li&gt;
  &lt;li&gt;The PSU confirms or not the Payment Request global or partial Cancellation&lt;/li&gt;
  &lt;li&gt;When agreeing the Payment Request cancellation, the PSU enters the resulting authentication factor through the PISP interface which will forward it to the ASPSP through a confirmation request (cf. § 4.7)&lt;/li&gt;
&lt;/ul&gt;
Case of the PSU neither gives nor denies his/her consent, the Cancellation Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.&lt;br&gt;
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
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestPut : OBPEndpoint = {
       case "payment-requests" :: paymentrequestresourceid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
&lt;h3&gt;Description&lt;/h3&gt;
The following use cases can be applied:
&lt;ul&gt;
  &lt;li&gt;retrieval of a payment request on behalf of a merchant&lt;/li&gt;
  &lt;li&gt;retrieval of a transfer request on behalf of the account&#39;s owner&lt;/li&gt;
  &lt;li&gt;retrieval of a standing-order request on behalf of the account&#39;s owner&lt;/li&gt;
&lt;/ul&gt;
The PISP has sent a Request through a POST command. &lt;br&gt;
  The ASPSP has registered the Request, updated if necessary the relevant identifiers in order to avoid duplicates and returned the location of the updated Request.&lt;br&gt;
  The PISP gets the Request that has been updated with the resource identifiers, and eventually the status of the Payment/Transfer Request and the status of the subsequent credit transfer.&lt;br&gt;
&lt;h3&gt;Prerequisites&lt;/h3&gt;
&lt;ul&gt;
  &lt;li&gt;The TPP has been registered by the Registration Authority for the PISP role&lt;/li&gt;
  &lt;li&gt;The TPP was provided with an OAUTH2 &quot;Client Credential&quot; access token by the ASPSP (cf. § 3.4.3).&lt;/li&gt;
  &lt;li&gt;The TPP has previously posted a Request which has been saved by the ASPSP (cf. § 4.5.3)&lt;/li&gt;
  &lt;ul&gt;
    &lt;li&gt;The ASPSP has answered with a location link to the saved Payment/Transfer Request (cf. § 4.5.4)&lt;/li&gt;
  &lt;/ul&gt;
  &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication &lt;/li&gt;
  &lt;li&gt;The TPP has presented its &quot;OAUTH2 Client Credential&quot; access token&lt;/li&gt;
&lt;/ul&gt;
&lt;h3&gt;Business flow&lt;/h3&gt;
The PISP asks to retrieve the Payment/Transfer Request that has been saved by the ASPSP. The PISP uses the location link provided by the ASPSP in response of the posting of this request.&lt;br&gt;
The ASPSP returns the previously posted Payment/Transfer Request which is enriched with:&lt;br&gt;
&lt;ul&gt;
  &lt;li&gt;The resource identifiers given by the ASPSP&lt;/li&gt;
  &lt;li&gt;The status information of the Payment Request and of the subsequent credit transfer&lt;/li&gt;
&lt;/ul&gt;
The status information must be available during at least 30 calendar days after the posting of the Payment Request. However, the ASPSP may increase this availability duration, based on its own rules.&lt;br&gt;
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestsGet : OBPEndpoint = {
       case "payment-requests" :: paymentrequestresourceid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
&lt;h3&gt;Description&lt;/h3&gt;
The following use cases can be applied:
&lt;ul&gt;
  &lt;li&gt;payment request on behalf of a merchant&lt;/li&gt;
  &lt;li&gt;transfer request on behalf of the account&#39;s owner&lt;/li&gt;
  &lt;li&gt;standing-order request on behalf of the account&#39;s owner&lt;/li&gt;
&lt;/ul&gt;
&lt;h4&gt;Data content&lt;/h4&gt;
  A payment request or a transfer request might embed several payment instructions having
  &lt;ul&gt;
    &lt;li&gt;one single execution date or multiple execution dates&lt;/li&gt;
    &lt;ul&gt;
      &lt;li&gt;case of one single execution date, this date must be set at the payment level&lt;/li&gt;
      &lt;li&gt;case of multiple execution dates, those dates must be set at each payment instruction level&lt;/li&gt;
    &lt;/ul&gt;                 
    &lt;li&gt;one single beneficiary or multiple beneficiaries&lt;/li&gt;
    &lt;ul&gt;
      &lt;li&gt;case of one single beneficiary, this beneficiary must be set at the payment level&lt;/li&gt;
      &lt;li&gt;case of multiple beneficiaries, those beneficiaries must be set at each payment instruction level&lt;/li&gt;
    &lt;/ul&gt;                 
  &lt;/ul&gt; 
  Having at the same time multiple beneficiaries and multiple execution date might not be a relevant business case, although it is technically allowed.&lt;br/&gt;
  Each implementation will have to specify which business use cases are actually supported.&lt;br/&gt;       
  A standing order request must embed one single payment instruction and must address one single beneficiary.
  &lt;ul&gt;
    &lt;li&gt;The beneficiary must be set at the payment level&lt;/li&gt;
    &lt;li&gt;The standing order specific characteristics (start date, periodicity...) must be set at the instruction level&lt;/li&gt;
  &lt;/ul&gt;                 
  Payment request can rely for execution on different payment instruments:
  - SEPA Credit Transfer (SCT)
  - Domestic Credit Transfer in a non Euro-currency
  - International payment
  The following table indicates how to use the different fields, depending on the payment instrument:
  &lt;table border&#x3D;&quot;1&quot;&gt;
    &lt;thead&gt;
      &lt;tr&gt;
        &lt;td&gt;Structure&lt;/td&gt;
        &lt;td&gt;SEPA payments&lt;/td&gt;
        &lt;td&gt;Domestic payments in non-euro currency&lt;/td&gt;
        &lt;td&gt;International payments&lt;/td&gt;
      &lt;/tr&gt;
    &lt;/thead&gt;
    &lt;tbody&gt;
      &lt;tr&gt;
        &lt;td&gt;PaymentTypeInformation/ InstructionPriority (payment
          level)&lt;/td&gt;
        &lt;td&gt;&quot;HIGH&quot; for high-priority SCT&lt;br /&gt;&quot;NORM&quot; for other SCT&lt;br /&gt;Ignored
          for SCTInst
        &lt;/td&gt;
        &lt;td&gt;&quot;HIGH&quot; for high-priority CT&lt;br /&gt;&quot;NORM&quot; or ignored for
          other CT&lt;br&gt;
        &lt;/td&gt;
        &lt;td&gt;&quot;HIGH&quot; for high-priority payments&lt;br /&gt;&quot;NORM&quot; or ignored
          for other payments&lt;br&gt;
        &lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;PaymentTypeInformation/ ServiceLevel (payment level)&lt;/td&gt;
        &lt;td&gt;&quot;SEPA&quot; for SCT and SCTInst&lt;/td&gt;
        &lt;td&gt;ignored&lt;/td&gt;
        &lt;td&gt;ignored&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;PaymentTypeInformation/ CategoryPurpose (payment level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;&quot;CASH&quot; for transfer request&lt;br /&gt;&quot;DVPM&quot; for
          payment request on behalf of a merchant
        &lt;/td&gt;
        &lt;td&gt;&quot;CORT&quot; for generic international payments&lt;br /&gt;&quot;INTC&quot; for
          transfers between two branches within the same company&lt;br /&gt;&quot;TREA&quot;
          for treasury transfers
        &lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;PaymentTypeInformation/ LocalInstrument (payment level)&lt;/td&gt;
        &lt;td&gt;&quot;INST&quot; pour les SCTInst&lt;br /&gt;Otherwise ignored
        &lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;ignored or valued with ISO20022 external code
          list values&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;RequestedExecutionDate (either at payment or transaction
          level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Mandatory (indicates the date on debit on the
          ordering party account)&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;InstructedAmount (at each transaction level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Mandatory&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;ChargeBearer (at each transaction level)&lt;/td&gt;
        &lt;td&gt;&quot;SLEV&quot; for SCT and SCTInst&lt;/td&gt;
        &lt;td&gt;&quot;SLEV&quot; or &quot;SHAR&quot;&lt;/td&gt;
        &lt;td&gt;&quot;CRED&quot;, &quot;DEBT&quot; or &quot;SHAR&quot;&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;Purpose (at payment level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Optional&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;RegulatoryReportingCode (at each transaction level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;Not used&lt;/td&gt;
        &lt;td&gt;Mandatory (possibly multiple values)&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;RemittanceInformation&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Optional&lt;br /&gt;Unstructured&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;Debtor (at payment level)&lt;/td&gt;
        &lt;td&gt;Mandatory&lt;br /&gt;2 address lines only
        &lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;Mandatory&lt;br /&gt;4 address lines only
        &lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;DebtorAccount (at payment level)&lt;/td&gt;
        &lt;td&gt;Optional&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;Optional&lt;br /&gt;Account currency may be
          specified
        &lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;DebtorAgent (at payment level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Optional&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;Creditor (either at payment or transaction level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Mandatory&lt;br /&gt;2 address lines only
        &lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;CreditorAccount (either at payment or transaction level)&lt;/td&gt;
        &lt;td&gt;Mandatory&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;Mandatory&lt;br /&gt;Account currency may be
          specified
        &lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;CreditorAgent (either at payment or transaction level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Optional&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;UltimateCreditor (either at payment or transaction level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;3&quot;&gt;Optional&lt;/td&gt;
      &lt;/tr&gt;
      &lt;tr&gt;
        &lt;td&gt;ClearingSystemId et ClearingSystemMemberId (either at
          payment or transaction level)&lt;/td&gt;
        &lt;td colspan&#x3D;&quot;2&quot;&gt;Not used&lt;/td&gt;
        &lt;td&gt;Optional&lt;/td&gt;
      &lt;/tr&gt;
    &lt;/tbody&gt;
  &lt;/table&gt;
  &lt;br/&gt;
&lt;h4&gt;Prerequisites for all use cases&lt;/h4&gt;
&lt;ul&gt;
  &lt;li&gt;The TPP has been registered by the Registration Authority for the PISP role&lt;/li&gt;
  &lt;li&gt;The TPP was provided with an OAUTH2 &quot;Client Credential&quot; access token by the ASPSP (cf. § 3.4.3).&lt;/li&gt;
  &lt;li&gt;The TPP and the ASPSP have successfully processed a mutual check and authentication &lt;/li&gt;
  &lt;li&gt;The TPP has presented its &quot;OAUTH2 Client Credential&quot; access token&lt;/li&gt;
&lt;/ul&gt;
&lt;h4&gt;Business flow&lt;/h4&gt;
  &lt;h5&gt;Payment Request use case&lt;/h5&gt;
    The PISP forwards a payment request on behalf of a merchant.&lt;br&gt;
    The PSU buys some goods or services on an e-commerce website held by a merchant. Among other payment method, the merchant suggests the use of a PISP service. As there is obviously a contract between the merchant and the PISP, there is no need of such a contract between the PSU and this PISP to initiate the process.&lt;br&gt;
    Case of the PSU that chooses to use the PISP service:&lt;br&gt;
    &lt;ul&gt;
      &lt;li&gt;The merchant forwards the requested payment characteristics to the PISP and redirects the PSU to the PISP portal.&lt;/li&gt;
      &lt;li&gt;The PISP requests from the PSU which ASPSP will be used.&lt;/li&gt;
      &lt;li&gt;The PISP prepares the Payment Request and sends this request to the ASPSP.&lt;/li&gt;
      &lt;li&gt;The Request can embed several payment instructions having different requested execution date.&lt;/li&gt;
      &lt;li&gt;The beneficiary, as being the merchant, is set at the payment level.&lt;/li&gt;
    &lt;/ul&gt; 
  &lt;h5&gt;Transfer Request use case&lt;/h5&gt;
    The PISP forwards a transfer request on behalf of the owner of the account.
    &lt;ul&gt;
      &lt;li&gt;The PSU provides the PISP with all information needed for the transfer.&lt;/li&gt;
      &lt;li&gt;The PISP prepares the Transfer Request and sends this request to the relevant ASPSP that holds the debtor account.&lt;/li&gt;
      &lt;li&gt;The Request can embed several payment instructions having different beneficiaries.&lt;/li&gt;
      &lt;li&gt;The requested execution date, as being the same for all instructions, is set at the payment level.&lt;/li&gt;
    &lt;/ul&gt; 
  &lt;h5&gt;Standing Order Request use case&lt;/h5&gt;
    The PISP forwards a Standing Order request on behalf of the owner of the account.
    &lt;ul&gt;
      &lt;li&gt;The PSU provides the PISP with all information needed for the Standing Order.&lt;/li&gt;
      &lt;li&gt;The PISP prepares the Standing Order Request and sends this request to the relevant ASPSP that holds the debtor account.&lt;/li&gt;
      &lt;li&gt;The Request embeds one single payment instruction with&lt;/li&gt;
      &lt;ul&gt;
        &lt;li&gt;The requested execution date of the first occurrence&lt;/li&gt;
        &lt;li&gt;The requested execution frequency of the payment in order to compute further execution dates&lt;/li&gt;
        &lt;li&gt;An execution rule to handle cases when the computed execution dates cannot be processed (e.g. bank holydays)&lt;/li&gt;
        &lt;li&gt;An optional end date for closing the standing Order&lt;/li&gt;
      &lt;/ul&gt;
    &lt;/ul&gt; 
&lt;h4&gt;Authentication flows for all use cases&lt;/h4&gt;
  As the request posted by the PISP to the ASPSP needs a PSU authentication before execution, this request will include:
  &lt;ul&gt;
    &lt;li&gt;The specification of the authentication approaches that are supported by the PISP (any combination of &quot;REDIRECT&quot;, &quot;EMBEDDED&quot; and &quot;DECOUPLED&quot; values).&lt;/li&gt;
    &lt;li&gt;In case of possible REDIRECT or DECOUPLED authentication approach, one or two call-back URLs to be used by the ASPSP at the finalisation of the authentication and consent process :&lt;/li&gt;
    &lt;ul&gt;
      &lt;li&gt;The first call-back URL will be called by the ASPSP if the Payment Request is processed without any error or rejection by the PSU&lt;/li&gt;
      &lt;li&gt;The second call-back URL is to be used by the ASPSP in case of processing error or rejection by the PSU. Since this second URL is optional, the PISP might not provide it. In this case, the ASPSP will use the same URL for any processing result.&lt;/li&gt;
      &lt;li&gt;Both call-back URLS must be used in a TLS-secured request.&lt;/li&gt;
    &lt;/ul&gt;
    &lt;li&gt;In case of possible &quot;EMBEDDED&quot; or &quot;DECOUPLED&quot; approaches, the PSU identifier that can be processed by the ASPSP for PSU recognition must have been set within the request body [debtor] structure.&lt;/li&gt;
  &lt;/ul&gt;
  The ASPSP saves the request and answers to the PISP. The answer embeds:
  &lt;ul&gt;
    &lt;li&gt;A location link of the saved Request that will be further used to retrieve the Request and its status information.&lt;/li&gt;
    &lt;li&gt;The specification of the chosen authentication approach taking into account both the PISP and the PSU capabilities.&lt;/li&gt;
    &lt;li&gt;In case of chosen REDIRECT authentication approach, the URL to be used by the PISP for redirecting the PSU in order to perform a authentication.&lt;/li&gt;
  &lt;/ul&gt;
  Case of the PSU neither gives nor denies his/her consent, the Request shall expire and is then rejected to the PISP. The expiration delay is specified by each ASPSP.&lt;br&gt;
  &lt;h5&gt;Redirect authentication approach &lt;/h5&gt;
    When the chosen authentication approach within the ASPSP answers is set to &quot;REDIRECT&quot;:&lt;br&gt;
    &lt;ul&gt;
      &lt;li&gt;The PISP redirects the PSU to the ASPSP which authenticates the PSU &lt;/li&gt;
      &lt;li&gt;The ASPSP asks the PSU to give (or deny) his/her consent to the Payment Request&lt;/li&gt;
      &lt;li&gt;The PSU chooses or confirms which of his/her accounts shall be used by the ASPSP for the future Credit Transfer.&lt;/li&gt;
      &lt;li&gt;The ASPSP is then able to initiate the subsequent Credit Transfer&lt;/li&gt;
      &lt;li&gt;The ASPSP redirects the PSU to the PISP using one of the call-back URLs provided within the posted Payment Request&lt;/li&gt;
    &lt;/ul&gt;
    &lt;img src&#x3D;&quot;https://www.stet.eu//assets/files/documents-api/pisp-redirect-authentication.png&quot; /&gt;
    &lt;img src&#x3D;&quot;https://www.stet.eu//assets/files/documents-api/pisp-redirect-authentication2.png&quot; /&gt;
  &lt;h5&gt;Decoupled authentication approach&lt;/h5&gt;
    When the chosen authentication approach is &quot;DECOUPLED&quot;:&lt;br&gt;
    &lt;ul&gt;
      &lt;li&gt;Based on the PSU identifier provided within the Payment Request by the PISP, the ASPSP gives the PSU with the Payment Request details and challenges the PSU for a Strong Customer Authentication on a decoupled device or application.&lt;/li&gt;
      &lt;li&gt;The PSU chooses or confirms which of his/her accounts shall be used by the ASPSP for the future Credit Transfer.&lt;/li&gt;
      &lt;li&gt;The ASPSP is then able to initiate the subsequent Credit Transfer&lt;/li&gt;
      &lt;li&gt;The ASPSP notifies the PISP about the finalisation of the authentication and consent process by using one of the call-back URLs provided within the posted Payment Request&lt;/li&gt;
    &lt;/ul&gt;
    &lt;img src&#x3D;&quot;https://www.stet.eu//assets/files/documents-api/pisp-decoupled-authentication.png&quot; /&gt;
    &lt;img src&#x3D;&quot;https://www.stet.eu//assets/files/documents-api/pisp-decoupled-authentication2.png&quot; /&gt;
  &lt;h5&gt;Embedded authentication approach&lt;/h5&gt;
    When the chosen authentication approach within the ASPSP answers is set to &quot;EMBEDDED&quot;:&lt;br&gt;
    &lt;ul&gt;
      &lt;li&gt;The TPP informs the PSU that a challenge is needed for completing the Payment Request processing. This challenge will be one of the following:&lt;/li&gt;
      &lt;ul&gt;
        &lt;li&gt;A One-Time-Password sent by the ASPSP to the PSU on a separate device or application.&lt;/li&gt;
        &lt;li&gt;A response computed by a specific device on base of a challenge sent by the ASPSP to the PSU on a separate device or application.&lt;/li&gt;
      &lt;/ul&gt;
      &lt;li&gt;The PSU unlock the device or application through a &quot;knowledge factor&quot; and/or an &quot;inherence factor&quot; (biometric), retrieves the Payment Request details and processes the data sent by the ASPSP; &lt;/li&gt;
      &lt;li&gt;The PSU might choose or confirm which of his/her accounts shall be used by the ASPSP for the future Credit Transfer when the device or application allows it.&lt;/li&gt;
      &lt;li&gt;When agreeing the Payment Request, the PSU enters the resulting authentication factor through the PISP interface which will forward it to the ASPSP through a confirmation request (cf. § 4.7)&lt;/li&gt;
    &lt;/ul&gt;
    &lt;img src&#x3D;&quot;https://www.stet.eu//assets/files/documents-api/pisp-embedded-authentication.png&quot; /&gt;
    &lt;img src&#x3D;&quot;https://www.stet.eu//assets/files/documents-api/pisp-embedded-authentication2.png&quot; /&gt;
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
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("PISP") :: apiTagMockedData :: Nil
     )

     lazy val paymentRequestsPost : OBPEndpoint = {
       case "payment-requests" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

}



