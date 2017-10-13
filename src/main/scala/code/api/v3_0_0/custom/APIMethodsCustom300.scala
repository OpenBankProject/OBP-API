package code.api.v3_0_0.custom


import code.api.ChargePolicy
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole.CanCreateAnyTransactionRequest
import code.api.util.ErrorMessages._
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.ChallengeAnswerJSON
import code.api.v2_0_0.JSONFactory200
import code.api.v2_1_0.{JSONFactory210, TransactionRequestBodyCommonJSON}
import code.bankconnectors.Connector
import code.fx.fx
import code.metadata.counterparties.MappedCounterparty
import code.model._
import code.model.dataAccess.MappedBankAccount
import code.transactionChallenge.ExpectedChallengeAnswer
import code.transactionrequests.TransactionRequests
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.util.Helper.booleanToBox
import net.liftweb.common.{Box, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.Serialization.write
import net.liftweb.json.{Extraction, NoTypeHints, Serialization}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

trait CustomAPIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>
  val ImplementationsCustom3_0_0 = new Object() {
    
    def endpointsOfCustom3_0_0 = createTransactionRequestCustom ::
      Nil
    
    val implementedInApiVersion: String = "3_0_0"
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    
  
    import net.liftweb.json.Extraction._
    import net.liftweb.json.JsonAST._
    val exchangeRates = prettyRender(decompose(fx.exchangeRates))
  
  
    // This text is used in the various Create Transaction Request resource docs
    val transactionRequestGeneralText =
      s"""Initiate a Payment via creating a Transaction Request.
         |
          |In OBP, a `transaction request` may or may not result in a `transaction`. However, a `transaction` only has one possible state: completed.
         |
          |A `Transaction Request` can have one of several states.
         |
          |`Transactions` are modeled on items in a bank statement that represent the movement of money.
         |
          |`Transaction Requests` are requests to move money which may or may not succeeed and thus result in a `Transaction`.
         |
          |A `Transaction Request` might create a security challenge that needs to be answered before the `Transaction Request` proceeds.
         |
          |Transaction Requests contain charge information giving the client the opportunity to proceed or not (as long as the challenge level is appropriate).
         |
          |Transaction Requests can have one of several Transaction Request Types which expect different bodies. The escaped body is returned in the details key of the GET response.
         |This provides some commonality and one URL for many different payment or transfer types with enough flexibility to validate them differently.
         |
          |The payer is set in the URL. Money comes out of the BANK_ID and ACCOUNT_ID specified in the URL.
         |
          |The payee is set in the request body. Money goes into the BANK_ID and ACCOUNT_ID specified in the request body.
         |
          |In sandbox mode, TRANSACTION_REQUEST_TYPE is commonly set to SANDBOX_TAN. See getTransactionRequestTypesSupportedByBank for all supported types.
         |
          |In sandbox mode, if the amount is less than 1000 EUR (any currency, unless it is set differently on this server), the transaction request will create a transaction without a challenge, else the Transaction Request will be set to INITIALISED and a challenge will need to be answered.
         |
          |If a challenge is created you must answer it using Answer Transaction Request Challenge before the Transaction is created.
         |
          |You can transfer between different currency accounts. (new in 2.0.0). The currency in body must match the sending account.
         |
          |The following static FX rates are available in sandbox mode:
         |
          |${exchangeRates}
         |
          |
          |Transaction Requests satisfy PSD2 requirements thus:
         |
          |1) A transaction can be initiated by a third party application.
         |
          |2) The customer is informed of the charge that will incurred.
         |
          |3) The call supports delegated authentication (OAuth)
         |
          |See [this python code](https://github.com/OpenBankProject/Hello-OBP-DirectLogin-Python/blob/master/hello_payments.py) for a complete example of this flow.
         |
          |There is further documentation [here](https://github.com/OpenBankProject/OBP-API/wiki/Transaction-Requests)
         |
          |${authenticationRequiredMessage(true)}
         |
          |"""
  
    val lowAmount  = AmountOfMoneyJsonV121("EUR", "12.50")
    val sharedChargePolicy = ChargePolicy.withName("SHARED")
  
  
    // Transaction Request (TRANSFER_TO_PHONE)
    resourceDocs += ResourceDoc(
      createTransactionRequestTransferToPhone,
      implementedInApiVersion,
      "createTransactionRequestTransferToPhone",
      "POST",
      s"/custom/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/${TRANSFER_TO_PHONE.toString}/transaction-requests",
      s"Create Transaction Request ${TRANSFER_TO_PHONE.toString}",
      s"""$transactionRequestGeneralText
         |
         |Special instructions for ${TRANSFER_TO_PHONE.toString}:
         |
         |When using a ${TRANSFER_TO_PHONE.toString}, the following fields need to be filling in Json Body
         |from_account_owner_nickname : Nickname of the money sender (20 symbols)
         |from_account_phone_number   : Mobile number of the money sender (10 digits)
         |other_account_phone_number  : Mobile number of the money receiver (10 digits)
         |other_account_message       : Message text to the money receiver (50 symbols)
       """.stripMargin,
      transactionRequestBodyTransferToPhoneJson,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        InvalidPhoneNumber,
        InvalidChargePolicy,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))
  
    // Transaction Request (TRANSFER_TO_ATM)
    resourceDocs += ResourceDoc(
      createTransactionRequestTransferToATM,
      implementedInApiVersion,
      "createTransactionRequestTransferToATM",
      "POST",
      s"/custom/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/${TRANSFER_TO_ATM.toString}/transaction-requests",
      s"Create Transaction Request (${TRANSFER_TO_ATM.toString})",
      s"""$transactionRequestGeneralText
         |
         |Special instructions for ${TRANSFER_TO_ATM.toString}:
         |
         |When using a ${TRANSFER_TO_ATM.toString} //TODO add more info there.
         |
       """.stripMargin,
      transactionRequestBodyTransferToAtmJson,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))
  
    // Transaction Request (TRANSFER_TO_ACCOUNT)
    resourceDocs += ResourceDoc(
      createTransactionRequestTransferToAccount,
      implementedInApiVersion,
      "createTransactionRequestTransferToAccount",
      "POST",
      s"/custom/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/${TRANSFER_TO_ACCOUNT.toString}/transaction-requests",
      s"Create Transaction Request (${TRANSFER_TO_ACCOUNT.toString})",
      s"""$transactionRequestGeneralText
         |
         |Special instructions for ${TRANSFER_TO_ACCOUNT.toString}:
         |
         |When using a ${TRANSFER_TO_ACCOUNT.toString} //TODO add more info.
         |
       """.stripMargin,
      transactionRequestBodyAccountToAccount,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))
  
  
    lazy val createTransactionRequestTransferToPhone = createTransactionRequestCustom
    lazy val createTransactionRequestTransferToATM = createTransactionRequestCustom
    lazy val createTransactionRequestTransferToAccount = createTransactionRequestCustom
    lazy val createTransactionRequestCustom: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "custom" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        user =>
          for {
            _ <- booleanToBox(Props.getBool("transactionRequests_enabled", false)) ?~ TransactionDisabled
            u <- user ?~ UserNotLoggedIn
            _ <- tryo(assert(isValidID(accountId.value))) ?~! InvalidAccountIdFormat
            _ <- tryo(assert(isValidID(bankId.value))) ?~! InvalidBankIdFormat
            _ <- Bank(bankId) ?~! {BankNotFound}
            fromAccount <- BankAccount(bankId, accountId) ?~! {AccountNotFound}
            _ <- View.fromUrl(viewId, fromAccount) ?~! {ViewNotFound}
            isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true ||
              hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true, InsufficientAuthorisationToCreateTransactionRequest)
            _ <- tryo(assert(Props.get("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value))) ?~!
              s"${InvalidTransactionRequestType}: '${transactionRequestType.value}'"
          
            // Check the input JSON format, here is just check the common parts of all four tpyes
            transDetailsJson <- tryo {json.extract[TransactionRequestBodyCommonJSON]} ?~! InvalidJsonFormat
            isValidAmountNumber <- tryo(BigDecimal(transDetailsJson.value.amount)) ?~! InvalidNumber
            _ <- booleanToBox(isValidAmountNumber > BigDecimal("0"), NotPositiveAmount)
            _ <- tryo(assert(isValidCurrencyISOCode(transDetailsJson.value.currency))) ?~! InvalidISOCurrencyCode
          
            // Prevent default value for transaction request type (at least).
            _ <- tryo(assert(transDetailsJson.value.currency == fromAccount.currency)) ?~! {s"${InvalidTransactionRequestCurrency} " +
              s"From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.value.currency}"}
            amountOfMoneyJSON <- Full(AmountOfMoneyJsonV121(transDetailsJson.value.currency, transDetailsJson.value.amount))
          
            createdTransactionRequest <- TransactionRequestTypes.withName(transactionRequestType.value) match {
              case TRANSFER_TO_PHONE => {
                for {
                  transDetailsP2PJson <- tryo {json.extract[TransactionRequestBodyTransferToPhoneJson]} ?~! s"${InvalidJsonFormat} It should be ${TRANSFER_TO_PHONE.toString()} input format"
                  _ <- booleanToBox(validatePhoneNumber(transDetailsP2PJson.from.mobile_phone_number)) ?~! InvalidPhoneNumber
                  _ <- booleanToBox(validatePhoneNumber(transDetailsP2PJson.to.mobile_phone_number)) ?~! InvalidPhoneNumber
                  transDetailsSerialized <- tryo {write(transDetailsP2PJson)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv300(u,
                    viewId,
                    fromAccount,
                    new MappedBankAccount(),
                    new MappedCounterparty(),
                    transactionRequestType,
                    transDetailsP2PJson,
                    transDetailsSerialized,
                    sharedChargePolicy.toString)
                } yield createdTransactionRequest
              }
              case TRANSFER_TO_ATM => {
                for {
                  transDetailsP2PJson <- tryo {json.extract[TransactionRequestBodyTransferToAtmJson]} ?~! s"${InvalidJsonFormat} It should be be ${TRANSFER_TO_ATM.toString()} input format"
                  _ <- booleanToBox(validatePhoneNumber(transDetailsP2PJson.from.mobile_phone_number)) ?~! InvalidPhoneNumber
                  _ <- booleanToBox(validatePhoneNumber(transDetailsP2PJson.to.mobile_phone_number)) ?~! InvalidPhoneNumber
                  transDetailsSerialized <- tryo {write(transDetailsP2PJson)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv300(u,
                    viewId,
                    fromAccount,
                    new MappedBankAccount(),
                    new MappedCounterparty(),
                    transactionRequestType,
                    transDetailsP2PJson,
                    transDetailsSerialized,
                    sharedChargePolicy.toString)
                } yield createdTransactionRequest
              }
              case TRANSFER_TO_ACCOUNT => {
                for {
                  transDetailsP2PJson <- tryo {json.extract[TransactionRequestBodyTransferToAccount]} ?~! s"${InvalidJsonFormat} It should be ${TRANSFER_TO_ACCOUNT.toString} input format"
                  transDetailsSerialized <- tryo {write(transDetailsP2PJson)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv300(u,
                    viewId,
                    fromAccount,
                    new MappedBankAccount(),
                    new MappedCounterparty(),
                    transactionRequestType,
                    transDetailsP2PJson,
                    transDetailsSerialized,
                    sharedChargePolicy.toString)
                } yield createdTransactionRequest
              }
            }
          } yield {
            val json = JSONFactory210.createTransactionRequestWithChargeJSON(createdTransactionRequest)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }
  }
}

object CustomAPIMethods300 {
}
