package code.api.berlin.group.v1_3

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.berlin.group.v1_3.OBP_BERLIN_GROUP_1_3.Implementations1_3
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_BERLIN_GROUP_1_3 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3 = new Object() {
    val implementedInApiVersion: ApiVersion = ApiVersion.berlinGroupV1_3

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    def endpoints = 
      getAccountList ::
      getAccountBalances ::
      getTransactionList ::
      getTransactionDetails ::
      getCardAccount ::
      ReadCardAccount ::
      getCardAccountBalances ::
      getCardAccountTransactionList ::
      createConsent ::
      getConsentInformation ::
      deleteConsent ::
      getConsentStatus ::
      initiatePayment ::
      checkAvailabilityOfFunds ::
      createSigningBasket ::
      getPaymentInitiationScaStatus ::
      Nil
    
    
    resourceDocs += ResourceDoc(
      getAccountList,
      implementedInApiVersion,
      "getAccountList",
      "GET",
      "/accounts",
      "Read Account List",
      s"""
         |Reads a list of bank accounts, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
         |${authenticationRequiredMessage(true)}
         |
         |This endpoint is work in progress. Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.coreAccountsJsonV1,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAIS, apiTagAccount, apiTagPrivateData))


    apiRelations += ApiRelation(getAccountList, getAccountList, "self")



    lazy val getAccountList : OBPEndpoint = {
      //get private accounts for one bank
      case "accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
  
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
  
            bankId = BankId(defaultBankId)
  
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
  
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            
            Full((coreAccounts,callContext1)) <- {Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)}
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createTransactionListJSON(coreAccounts), callContext)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getAccountBalances,
      implementedInApiVersion,
      "getAccountBalances",
      "GET",
      "/accounts/ACCOUNT_ID/balances",
      "Read Balance",
      s"""
        |Reads account data from a given account addressed by “account-id”.
        |
        |${authenticationRequiredMessage(true)}
        |
        |This endpoint is work in progress. Experimental!
        |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountBalances,
      List(UserNotLoggedIn, ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAIS, apiTagAccount, apiTagPrivateData))
  
    lazy val getAccountBalances : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
            (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), accountId, callContext)
            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})") {(u.hasViewAccess(view))}
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            moderatedAccount <- Future {bankAccount.moderatedBankAccount(view, Full(u))} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountBalanceJSON(moderatedAccount, transactionRequests), HttpCode.`200`(callContext))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getTransactionList,
      implementedInApiVersion,
      "getTransactionList",
      "GET",
      "/accounts/ACCOUNT_ID/transactions",
      "Read transaction list of an account",
      s"""
        |Reads account data from a given account addressed by “account-id”. 
        |${authenticationRequiredMessage(true)}
        |
        |This endpoint is work in progress. Experimental!
        |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.transactionsJsonV1,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAIS, apiTagTransaction, apiTagPrivateData))
  
    lazy val getTransactionList : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet _ => {
        cc =>
          for {
            
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
            
            bankId = BankId(defaultBankId)
            
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            
            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 
            
            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(Full(u), view, callContext, params: _*)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            } yield {
              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(transactions, transactionRequests), callContext)
            }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      initiatePayment,
      implementedInApiVersion,
      nameOf(initiatePayment),
      "GET",
      "/v1/{payment-service}/{payment-product}",
      "Payment initiation request",
      s"This method is used to initiate a payment at the ASPSP.\n\n## Variants of Payment Initiation Requests\n\nThis method to initiate a payment initiation at the ASPSP can be sent with either a JSON body or an pain.001 body depending on the payment product in the path.\n\nThere are the following **payment products**:\n\n  - Payment products with payment information in *JSON* format:\n    - ***sepa-credit-transfers***\n    - ***instant-sepa-credit-transfers***\n    - ***target-2-payments***\n    - ***cross-border-credit-transfers***\n  - Payment products with payment information in *pain.001* XML format:\n    - ***pain.001-sepa-credit-transfers***\n    - ***pain.001-instant-sepa-credit-transfers***\n    - ***pain.001-target-2-payments***\n    - ***pain.001-cross-border-credit-transfers***\n\nFurthermore the request body depends on the **payment-service**\n  * ***payments***: A single payment initiation request.\n  * ***bulk-payments***: A collection of several payment iniatiation requests.\n  \n    In case of a *pain.001* message there are more than one payments contained in the *pain.001 message.\n    \n    In case of a *JSON* there are several JSON payment blocks contained in a joining list.\n  * ***periodic-payments***: \n    Create a standing order initiation resource for recurrent i.e. periodic payments addressable under {paymentId} \n     with all data relevant for the corresponding payment product and the execution of the standing order contained in a JSON body. \n\nThis is the first step in the API to initiate the related recurring/periodic payment.\n  \n## Single and mulitilevel SCA Processes\n\nThe Payment Initiation Requests are independent from the need of one ore multilevel \nSCA processing, i.e. independent from the number of authorisations needed for the execution of payments. \n\nBut the response messages are specific to either one SCA processing or multilevel SCA processing. \n\nFor payment initiation with multilevel SCA, this specification requires an explicit start of the authorisation, \ni.e. links directly associated with SCA processing like 'scaRedirect' or 'scaOAuth' cannot be contained in the \nresponse message of a Payment Initation Request for a payment, where multiple authorisations are needed. \nAlso if any data is needed for the next action, like selecting an SCA method is not supported in the response, \nsince all starts of the multiple authorisations are fully equal. \nIn these cases, first an authorisation sub-resource has to be generated following the 'startAuthorisation' link.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagPIS, apiTagAccount, apiTagPrivateData))
    
    lazy val initiatePayment : OBPEndpoint = {
      //get private accounts for one bank
      case "accounts" :: "1":: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getTransactionDetails,
      implementedInApiVersion,
      nameOf(getTransactionDetails),
      "GET",
      "/v1/accounts/{account-id}/transactions/{resourceId}",
      "Read Transaction Detailst",
      s"Reads transaction details from a given transaction addressed b",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAIS, apiTagAccount, apiTagPrivateData))
    
    lazy val getTransactionDetails : OBPEndpoint = {
      //get private accounts for one bank
      case "accounts" :: "1":: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCardAccount,
      implementedInApiVersion,
      nameOf(getCardAccount),
      "GET",
      "/v1/accounts/{account-id}/transactions/{resourceId}",
      "Reads a list of card accounts",
      s"Reads transaction details from a given transaction addressed by ",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAIS, apiTagAccount, apiTagPrivateData))
    
    lazy val getCardAccount : OBPEndpoint = {
      //get private accounts for one bank
      case "accounts" :: "1":: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      checkAvailabilityOfFunds,
      implementedInApiVersion,
      nameOf(checkAvailabilityOfFunds),
      "GET",
      "/v1/funds-confirmations",
      "Confirmation of Funds Request",
      s"Creates a confirmation of funds request at the ASPSP. Checks whether a specific amount is available at point of time of the request on an account linked to a given tuple card issuer(TPP)/card number, or addressed by IBAN and TPP respectively",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagPIIS, apiTagAccount, apiTagPrivateData))
    
    lazy val checkAvailabilityOfFunds : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      createSigningBasket,
      implementedInApiVersion,
      nameOf(createSigningBasket),
      "POST",
      "/v1/signing-baskets",
      "Create a signing basket resource",
      s"Create a signing basket resource for authorising several transactions with one SCA method. \nThe resource identifications of these transactions are contained in the  payload of this access method\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagSigningBaskets, apiTagAccount, apiTagPrivateData))
    
    lazy val createSigningBasket : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getPaymentInitiationScaStatus,
      implementedInApiVersion,
      nameOf(getPaymentInitiationScaStatus),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Read the SCA Status of the payment authorisation",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val getPaymentInitiationScaStatus : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      ReadCardAccount,
      implementedInApiVersion,
      nameOf(ReadCardAccount),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Reads details about a card account",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val ReadCardAccount : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCardAccountBalances,
      implementedInApiVersion,
      nameOf(getCardAccountBalances),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Read card account balances",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val getCardAccountBalances : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCardAccountTransactionList,
      implementedInApiVersion,
      nameOf(getCardAccountTransactionList),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Read transaction list of an account",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val getCardAccountTransactionList : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      createConsent,
      implementedInApiVersion,
      nameOf(createConsent),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Create consent",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val createConsent : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    resourceDocs += ResourceDoc(
      getConsentInformation,
      implementedInApiVersion,
      nameOf(getConsentInformation),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Get Consent Request",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val getConsentInformation : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      deleteConsent,
      implementedInApiVersion,
      nameOf(deleteConsent),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Delete Consent",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val deleteConsent : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getConsentStatus,
      implementedInApiVersion,
      nameOf(deleteConsent),
      "GET",
      "/v1/{payment-service}/{payment-product}/{paymentId}/authorisations/{authorisationId}",
      "Consent status request",
      s"This method returns the SCA status of a payment initiation's authorisation sub-resource.\n",
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCommonServices, apiTagAccount, apiTagPrivateData))
    
    lazy val getConsentStatus : OBPEndpoint = {
      //get private accounts for one bank
      case "funds-confirmations" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(NotImplemented, cc)
            } yield {
            (emptyObjectJson, callContext)
          }
      }
    }
    
  }

}


object APIMethods_BERLIN_GROUP_1_3 {
}
