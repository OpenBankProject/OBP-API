package code.api.builder.PaymentInitiationServicePISApi

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.sepaCreditTransfersBerlinGroupV13
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{CancelPaymentResponseJson, CancelPaymentResponseLinks, LinkHrefJson, UpdatePaymentPsuDataJson, createCancellationTransactionRequestJson}
import code.api.berlin.group.v1_3.{JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiRole, ApiTag, NewStyle}
import code.bankconnectors.Connector
import code.fx.fx
import code.model._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.SEPA_CREDIT_TRANSFERS
import code.transactionrequests.TransactionRequests.{PaymentServiceTypes, TransactionRequestTypes}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestStatus._
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus, TransactionRequestStatus}
import net.liftweb.common.Full
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.Serialization.write
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object APIMethods_PaymentInitiationServicePISApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

  def checkPaymentServerError(paymentService: String) = s"${InvalidTransactionRequestType.replaceAll("TRANSACTION_REQUEST_TYPE", "PAYMENT_SERVICE in the URL.")}: '${paymentService}'.It should be `payments` for now, will support (bulk-payments, periodic-payments) soon"
  def checkPaymentProductError(paymentProduct: String) = s"${InvalidTransactionRequestType.replaceAll("TRANSACTION_REQUEST_TYPE", "PAYMENT_PRODUCT in the URL.")}: '${paymentProduct}'.It should be `sepa-credit-transfers`for now, will support (instant-sepa-credit-transfers, target-2-payments, cross-border-credit-transfers) soon."


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
       nameOf(cancelPayment),
       "DELETE",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID",
       "Payment Cancellation Request",
       s"""${mockedDataText(true)}
This method initiates the cancellation of a payment. Depending on the payment-service, the payment-product 
and the ASPSP's implementation, this TPP call might be sufficient to cancel a payment. If an authorisation 
of the payment cancellation is mandated by the ASPSP, a corresponding hyperlink will be contained in the 
response message. Cancels the addressed payment with resource identification paymentId if applicable to the 
payment-service, payment-product and received in product related timelines (e.g. before end of business day 
for scheduled payments of the last business day before the scheduled execution day). The response to this 
DELETE command will tell the TPP whether the * access method was rejected * access method was successful, 
or * access method is generally applicable, but further authorisation processes are needed.
""",
       emptyObjectJson,
       CancelPaymentResponseJson(
         "ACTC",
         _links = CancelPaymentResponseLinks(
           self = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"),
           status = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/1234-wertiq-983/status"),
           startAuthorisation = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/cancellation-authorisations/1234-wertiq-983/status")
         )
       ),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: Nil
     )

     lazy val cancelPayment : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             transactionRequestTypes <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)

             transactionRequestBody <- NewStyle.function.tryons(s"${UnknownError} No data for Payment Body ",400, callContext) {
               transactionRequest.body.to_sepa_credit_transfers.get
             }
             fromAccountIban = transactionRequestBody.debtorAccount.iban
             toAccountIban = transactionRequestBody.creditorAccount.iban
             (_, callContext) <- NewStyle.function.getBankAccountByIban(fromAccountIban, callContext)
             (ibanChecker, callContext) <- NewStyle.function.validateAndCheckIbanNumber(toAccountIban, callContext)
             _ <- Helper.booleanToFuture(invalidIban) { ibanChecker.isValid == true }
             (_, callContext) <- NewStyle.function.getToBankAccountByIban(toAccountIban, callContext)
             (canBeCancelled, _, startSca) <- transactionRequestTypes match {
               case TransactionRequestTypes.SEPA_CREDIT_TRANSFERS => {
                 transactionRequest.status.toUpperCase() match {
                   case "COMPLETED" =>
                     NewStyle.function.cancelPaymentV400(TransactionId(transactionRequest.transaction_ids), callContext) map {
                       x => x._1 match {
                         case CancelPayment(true, Some(startSca)) if startSca == true => 
                           Connector.connector.vend.saveTransactionRequestStatusImpl(transactionRequest.id, CANCELLATION_PENDING.toString)
                           (true, x._2, Some(startSca))
                         case CancelPayment(true, Some(startSca)) if startSca == false =>
                           Connector.connector.vend.saveTransactionRequestStatusImpl(transactionRequest.id, CANCELLED.toString)
                           (true, x._2, Some(startSca))
                         case CancelPayment(false, _) =>
                           (false, x._2, Some(false))
                       }
                     }
                   case "INITIATED" => 
                     Connector.connector.vend.saveTransactionRequestStatusImpl(transactionRequest.id, CANCELLED.toString)
                     Future(true, callContext, Some(false))
                   case "CANCELLED" => 
                     Future(true, callContext, Some(false))
                 }
               }
             }
             _ <- Helper.booleanToFuture(failMsg= TransactionRequestCannotBeCancelled) { canBeCancelled == true }
             (updatedTransactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
           } yield {
             startSca.getOrElse(false) match {
               case true => (createCancellationTransactionRequestJson(updatedTransactionRequest), HttpCode.`202`(callContext))
               case false => (JsRaw(""), HttpCode.`204`(callContext))
             }
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentCancellationScaStatus,
       apiVersion,
       nameOf(getPaymentCancellationScaStatus),
       "GET",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations/CANCELLATIONID",
       "Read the SCA status of the payment cancellation's authorisation.",
       s"""${mockedDataText(false)}
This method returns the SCA status of a payment initiation's authorisation sub-resource.
""",
       emptyObjectJson,
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getPaymentCancellationScaStatus : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId:: "cancellation-authorisations" :: cancellationId :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (_, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
             (challenge, callContext) <- NewStyle.function.getChallenge(cancellationId, callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challenge.scaStatus.map(_.toString).getOrElse("None")), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInformation,
       apiVersion,
       nameOf(getPaymentInformation),
       "GET",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID",
       "Get Payment Information",
       s"""${mockedDataText(false)}
Returns the content of a payment object""",
       emptyObjectJson,
       json.parse("""{
                      "debtorAccount":{
                        "iban":"GR12 1234 5123 4511 3981 4475 477"
                      },
                      "instructedAmount":{
                        "currency":"EUR",
                        "amount":"1234"
                      },
                      "creditorAccount":{
                        "iban":"GR12 1234 5123 4514 4575 3645 077"
                      },
                      "creditorName":"70charname"
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM ::Nil
     )

     lazy val getPaymentInformation : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             transactionRequestTypes <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)

             transactionRequestBody <- NewStyle.function.tryons(s"${UnknownError} No data for Payment Body ",400, callContext) {
               transactionRequest.body.to_sepa_credit_transfers.get
             }
             
             } yield {
             (transactionRequestBody, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationAuthorisation,
       apiVersion,
       nameOf(getPaymentInitiationAuthorisation),
       "GET",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/authorisations",
       "Get Payment Initiation Authorisation Sub-Resources Request",
       s"""${mockedDataText(false)}
Read a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
       emptyObjectJson,
       json.parse("""[
                       {
                           "scaStatus": "received",
                           "authorisationId": "940948c7-1c86-4d88-977e-e739bf2c1492",
                           "psuMessage": "Please check your SMS at a mobile device.",
                           "_links": {
                               "scaStatus": "/v1.3/payments/sepa-credit-transfers/940948c7-1c86-4d88-977e-e739bf2c1492"
                           }
                       },
                       {
                           "scaStatus": "received",
                           "authorisationId": "0ae75eee-deba-41d6-8116-1a4d6e05dd83",
                           "psuMessage": "Please check your SMS at a mobile device.",
                           "_links": {
                               "scaStatus": "/v1.3/payments/sepa-credit-transfers/0ae75eee-deba-41d6-8116-1a4d6e05dd83"
                           }
                       }
                     ]"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getPaymentInitiationAuthorisation : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId :: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (_, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
             (challenges, callContext) <-  NewStyle.function.getChallengesByTransactionRequestId(paymentId, callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationsJson(challenges), callContext)
           }
       }
     }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationCancellationAuthorisationInformation,
       apiVersion,
       nameOf(getPaymentInitiationCancellationAuthorisationInformation),
       "GET",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations",
       "Get Cancellation Authorisation Sub-Resources Request",
       s"""${mockedDataText(false)}
Retrieve a list of all created cancellation authorisation sub-resources.
""",
       emptyObjectJson,
       json.parse("""{
  "cancellationIds" : ["faa3657e-13f0-4feb-a6c3-34bf21a9ae8e]"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getPaymentInitiationCancellationAuthorisationInformation : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId :: "cancellation-authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (challenges, callContext) <-  NewStyle.function.getChallengesByTransactionRequestId(paymentId, callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.CancellationJsonV13(challenges.map(_.challengeId)), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationScaStatus,
       apiVersion,
       nameOf(getPaymentInitiationScaStatus),
       "GET",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
       "Read the SCA Status of the payment authorisation",
       s"""${mockedDataText(false)}
This method returns the SCA status of a payment initiation's authorisation sub-resource.
""",
       emptyObjectJson,
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getPaymentInitiationScaStatus : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (_, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
             (challenge, callContext) <- NewStyle.function.getChallenge(authorisationid, callContext)
             
           } yield {
             (json.parse(
               s"""{"scaStatus" : "${challenge.scaStatus.getOrElse("None")}"}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getPaymentInitiationStatus,
       apiVersion,
       nameOf(getPaymentInitiationStatus),
       "GET",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/status",
       "Payment initiation status request",
       s"""${mockedDataText(false)}
Check the transaction status of a payment initiation.""",
       emptyObjectJson,
       json.parse("""{
                      "transactionStatus": "ACCP"
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getPaymentInitiationStatus : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             transactionRequestTypes <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)

             transactionRequestStatus = transactionRequest.status match {
               case "COMPLETED" => "ACCP"
               case "INITIATED" => "RCVD"
             }

             transactionRequestAmount <- NewStyle.function.tryons(s"${UnknownError} transction request amount can not convert to a Decimal",400, callContext) {
               BigDecimal(transactionRequest.body.to_sepa_credit_transfers.get.instructedAmount.amount)
             }
             transactionRequestCurrency <- NewStyle.function.tryons(s"${UnknownError} can not get currency from this paymentId(${paymentId})",400, callContext) {
               transactionRequest.body.to_sepa_credit_transfers.get.instructedAmount.currency
             }
             
             
             transactionRequestFromAccount = transactionRequest.from
             (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(transactionRequestFromAccount.bank_id), AccountId(transactionRequestFromAccount.account_id), callContext)
             fromAccountBalance = fromAccount.balance
             fromAccountCurrency = fromAccount.currency
             fundsAvalible = fromAccountBalance >= transactionRequestAmount


             //From change from requestAccount Currency to currentBankAccount Currency
             rate = fx.exchangeRate(transactionRequestCurrency, fromAccountCurrency)
             _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion (${transactionRequestCurrency} to ${fromAccountCurrency}) is not supported.") {
               rate.isDefined
             }
             
             requestChangedCurrencyAmount = fx.convert(transactionRequestAmount, rate)

             fundsAvailable = (fromAccountBalance >= requestChangedCurrencyAmount)

             transactionRequestStatusChekedFunds = if(fundsAvailable) transactionRequestStatus else "RCVD"

           } yield {
             (json.parse(s"""{
                           "transactionStatus": "$transactionRequestStatusChekedFunds"
                           "fundsAvailable": $fundsAvailable
                          }"""
             ), callContext)
           }
         }
       }


  val additionalInstructions : String =
    """
      |Additional Instructions:
      |
      |for PAYMENT_SERVICE use payments
      |
      |for PAYMENT_PRODUCT use sepa-credit-transfers
      |
    """.stripMargin


     resourceDocs += ResourceDoc(
       initiatePayment,
       apiVersion,
       nameOf(initiatePayment),
       "POST",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT",
       "Payment initiation request",
       s"""${mockedDataText(false)}
This method is used to initiate a payment at the ASPSP.

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

  - Furthermore the request body depends on the **payment-service**
    - ***payments***: A single payment initiation request.
    - ***bulk-payments***: A collection of several payment iniatiation requests.
      In case of a *pain.001* message there are more than one payments contained in the *pain.001 message.
      In case of a *JSON* there are several JSON payment blocks contained in a joining list.
    - ***periodic-payments***: 
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


$additionalInstructions

""",
       sepaCreditTransfersBerlinGroupV13,
       json.parse(s"""{
                      "transactionStatus": "RCVD",
                      "paymentId": "1234-wertiq-983",
                      "_links":
                        {
                        "scaRedirect": {"href": "$getServerUrl/otp?flow=payment&paymentService=payments&paymentProduct=sepa_credit_transfers&paymentId=b0472c21-6cea-4ee0-b036-3e253adb3b0b"},
                        "self": {"href": "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"},
                        "status": {"href": "/v1.3/payments/1234-wertiq-983/status"},
                        "scaStatus": {"href": "/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}
                        }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val initiatePayment : OBPEndpoint = {
       case paymentService :: paymentProduct :: Nil JsonPost json -> _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             transactionRequestTypes <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }

             transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $SepaCreditTransfersBerlinGroupV13 ", 400, callContext) {
               json.extract[SepaCreditTransfersBerlinGroupV13]
             }

             transDetailsSerialized <- NewStyle.function.tryons (s"$UnknownError Can not serialize in request Json ", 400, callContext){write(transDetailsJson)(Serialization.formats(NoTypeHints))}
             
             isValidAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${transDetailsJson.instructedAmount.amount} ", 400, callContext) {
               BigDecimal(transDetailsJson.instructedAmount.amount)
             }

             _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${isValidAmountNumber}'") {
               isValidAmountNumber > BigDecimal("0")
             }

             // Prevent default value for transaction request type (at least).
             _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.instructedAmount.currency}'") {
               isValidCurrencyISOCode(transDetailsJson.instructedAmount.currency)
             }

             _ <- NewStyle.function.isEnabledTransactionRequests()
             fromAccountIban = transDetailsJson.debtorAccount.iban
             toAccountIban = transDetailsJson.creditorAccount.iban

             (fromAccount, callContext) <- NewStyle.function.getBankAccountByIban(fromAccountIban, callContext)
             (ibanChecker, callContext) <- NewStyle.function.validateAndCheckIbanNumber(toAccountIban, callContext)
             _ <- Helper.booleanToFuture(invalidIban) { ibanChecker.isValid == true }
             (toAccount, callContext) <- NewStyle.function.getToBankAccountByIban(toAccountIban, callContext)

             _ <- if (u.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId))) Future.successful(Full(Unit))
                  else NewStyle.function.hasEntitlement(fromAccount.bankId.value, u.userId, ApiRole.canCreateAnyTransactionRequest, callContext, InsufficientAuthorisationToCreateTransactionRequest)

               // Prevent default value for transaction request type (at least).
             _ <- Helper.booleanToFuture(s"From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.instructedAmount.currency}") {
               transDetailsJson.instructedAmount.currency == fromAccount.currency
             }

             amountOfMoneyJSON = transDetailsJson.instructedAmount

             (createdTransactionRequest,callContext) <- transactionRequestTypes match {
               case TransactionRequestTypes.SEPA_CREDIT_TRANSFERS => {
                 for {
                   (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(
                     u,
                     ViewId("Owner"),//This is the default 
                     fromAccount,
                     toAccount,
                     TransactionRequestType(transactionRequestTypes.toString),
                     TransactionRequestCommonBodyJSONCommons(
                       amountOfMoneyJSON,
                      ""
                     ),
                     transDetailsSerialized,
                     "",
                     None,
                     None,
                     None,
                     Some(transDetailsJson),
                     callContext
                   ) //in SANDBOX_TAN, ChargePolicy set default "SHARED"
                 } yield (createdTransactionRequest, callContext)
               }
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createTransactionRequestJson(createdTransactionRequest), HttpCode.`201`(callContext))
           }
       }
     }
            
     resourceDocs += ResourceDoc(
       startPaymentAuthorisation,
       apiVersion,
       nameOf(startPaymentAuthorisation),
       "POST",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations",
       "Start the authorisation process for a payment initiation",
       s"""${mockedDataText(false)}
Create an authorisation sub-resource and start the authorisation process. 
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
       emptyObjectJson,
       json.parse("""{
                      "challengeData": {
                        "scaStatus": "received",
                        "authorisationId": "88695566-6642-46d5-9985-0d824624f507",
                        "psuMessage": "Please check your SMS at a mobile device.",
                        "_links": {
                          "scaStatus": "/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"
                        }
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

  lazy val startPaymentAuthorisation : OBPEndpoint = {
    case paymentService :: paymentProduct :: paymentId :: "authorisations" :: Nil JsonPost json -> _  => {
      cc =>
        for {
          (Full(u), callContext) <- authenticatedAccess(cc)
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
          }
          (_, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)

          (challenges, callContext) <- NewStyle.function.createChallengesC2(
            List(u.userId),
            ChallengeType.BERLINGROUP_PAYMENT,
            Some(paymentId),
            getScaMethodAtInstance(SEPA_CREDIT_TRANSFERS.toString).toOption,
            Some(StrongCustomerAuthenticationStatus.received),
            None,
            None,
            callContext
          )
          //NOTE: in OBP it support multiple challenges, but in Berlin Group it has only one challenge. The following guard is to make sure it return the 1st challenge properly.
          challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge,400, callContext) {
            challenges.head
          }
        } yield {
          (JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationJson(challenge), callContext)
        }
    }
  }
            
     resourceDocs += ResourceDoc(
       startPaymentInitiationCancellationAuthorisation,
       apiVersion,
       nameOf(startPaymentInitiationCancellationAuthorisation),
       "POST",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations",
       "Start the authorisation process for the cancellation of the addressed payment",
       s"""${mockedDataText(false)}
Creates an authorisation sub-resource and start the authorisation process of the cancellation of the addressed payment. 
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
       emptyObjectJson,
       json.parse("""{
                      "scaStatus":"received",
                      "authorisationId":"8a49b79b-b400-4e6b-b88d-637c3a71479d",
                      "psuMessage":"Please check your SMS at a mobile device.",
                      "_links":{
                        "scaStatus":"/v1.3/payments/sepa-credit-transfers/PAYMENT_ID/8a49b79b-b400-4e6b-b88d-637c3a71479d"
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val startPaymentInitiationCancellationAuthorisation : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId:: "cancellation-authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
             _ <- Helper.booleanToFuture(failMsg= CannotStartTheAuthorisationProcessForTheCancellation) {
               transactionRequest.status == TransactionRequestStatus.CANCELLATION_PENDING.toString
             }
             (challenges, callContext) <- NewStyle.function.createChallengesC2(
               List(u.userId),
               ChallengeType.BERLINGROUP_PAYMENT,
               Some(paymentId),
               getScaMethodAtInstance(SEPA_CREDIT_TRANSFERS.toString).toOption,
               Some(StrongCustomerAuthenticationStatus.received),
               None,
               None,
               callContext
             )
             //NOTE: in OBP it support multiple challenges, but in Berlin Group it has only one challenge. The following guard is to make sure it return the 1st challenge properly.
             challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge,400, callContext) {
               challenges.head
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createStartPaymentCancellationAuthorisationJson(
               challenge,
               paymentService,
               paymentProduct,
               paymentId
             ), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updatePaymentCancellationPsuData,
       apiVersion,
       nameOf(updatePaymentCancellationPsuData),
       "PUT",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations/CANCELLATIONID",
       "Update PSU Data for payment initiation cancellation",
       s"""${mockedDataText(false)}
This method updates PSU data on the cancellation authorisation resource if needed. 
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
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val updatePaymentCancellationPsuData : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId:: "cancellation-authorisations" :: cancellationId :: Nil JsonPut json -> _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
             updatePaymentPsuDataJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               json.extract[UpdatePaymentPsuDataJson]
             }

             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             //Map obp transaction request id with BerlinGroup PaymentId
             transactionRequestId = TransactionRequestId(paymentId)
             (existingTransactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, callContext)
             _ <- Helper.booleanToFuture(failMsg= CannotUpdatePSUDataCancellation) { 
               existingTransactionRequest.status == TransactionRequestStatus.INITIATED.toString ||
               existingTransactionRequest.status == TransactionRequestStatus.CANCELLATION_PENDING.toString ||
               existingTransactionRequest.status == TransactionRequestStatus.COMPLETED.toString
             }
             (_, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
             (challenge, callContext) <- NewStyle.function.validateChallengeAnswerC2(
               ChallengeType.BERLINGROUP_PAYMENT,
               Some(paymentId),
               None,
               cancellationId,
               updatePaymentPsuDataJson.scaAuthenticationData,
               callContext
             )

             (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(
               BankId(existingTransactionRequest.from.bank_id),
               AccountId(existingTransactionRequest.from.account_id),
               callContext
             )
             _ <- challenge.scaStatus match {
               case Some(status) if status == StrongCustomerAuthenticationStatus.finalised => // finalised
                 Future(Connector.connector.vend.saveTransactionRequestStatusImpl(existingTransactionRequest.id, CANCELLED.toString))
               case Some(status) if status == StrongCustomerAuthenticationStatus.failed => // failed
                 Future(Connector.connector.vend.saveTransactionRequestStatusImpl(existingTransactionRequest.id, REJECTED.toString))
               case _ => // all other cases
                 Future(Full(true))
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createStartPaymentCancellationAuthorisationJson(
               challenge,
               paymentService,
               paymentProduct,
               paymentId
             ), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updatePaymentPsuData,
       apiVersion,
       nameOf(updatePaymentPsuData),
       "PUT",
       "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
       "Update PSU data for payment initiation",
       s"""${mockedDataText(false)}
This methods updates PSU data on the authorisation resource if needed. 
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
    
    NOTE: For this endpoint, for sandbox mode, the `scaAuthenticationData` is fixed value: 123. To make the process work.
          Normally the app use will get SMS/EMAIL to get the value for this process.
      
""",
       json.parse("""{"scaAuthenticationData":"123"}"""),
       json.parse("""{
                        "scaStatus": "finalised",
                        "authorisationId": "88695566-6642-46d5-9985-0d824624f507",
                        "psuMessage": "Please check your SMS at a mobile device.",
                        "_links": {
                            "scaStatus": "/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"
                        }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val updatePaymentPsuData : OBPEndpoint = {
       case paymentService :: paymentProduct :: paymentId:: "authorisations" :: authorisationid :: Nil JsonPut json -> _ =>  {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Pisp(callContext)
             failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
             updatePaymentPsuDataJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               json.extract[UpdatePaymentPsuDataJson]
             }
             
             _ <- NewStyle.function.tryons(checkPaymentServerError(paymentService),400, callContext) {
               PaymentServiceTypes.withName(paymentService.replaceAll("-","_"))
             }
             _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct),400, callContext) {
               TransactionRequestTypes.withName(paymentProduct.replaceAll("-","_").toUpperCase)
             }
             //Map obp transaction request id with BerlinGroup PaymentId
             transactionRequestId = TransactionRequestId(paymentId)
             (existingTransactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, callContext)
             _ <- Helper.booleanToFuture(failMsg= CannotUpdatePSUData) {
               existingTransactionRequest.status == TransactionRequestStatus.INITIATED.toString
             }
             (challenge, callContext) <- NewStyle.function.validateChallengeAnswerC2(
               ChallengeType.BERLINGROUP_PAYMENT,
               Some(paymentId),
               None,
               authorisationid,
               updatePaymentPsuDataJson.scaAuthenticationData,
               callContext
             )
             
             (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(
               BankId(existingTransactionRequest.from.bank_id), 
               AccountId(existingTransactionRequest.from.account_id), 
               callContext
             )
             _ <- challenge.scaStatus match {
               case Some(status) if status == StrongCustomerAuthenticationStatus.finalised => // finalised
                 NewStyle.function.createTransactionAfterChallengeV210(fromAccount, existingTransactionRequest, callContext) map {
                   response => 
                     Connector.connector.vend.saveTransactionRequestStatusImpl(existingTransactionRequest.id, COMPLETED.toString)
                 }
               case Some(status) if status == StrongCustomerAuthenticationStatus.failed => // failed
                 Future(Connector.connector.vend.saveTransactionRequestStatusImpl(existingTransactionRequest.id, REJECTED.toString))
               case _ => // started and all other cases
                 Future(Full(true))
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationJson(challenge), callContext)
           }
         }
       }

}



