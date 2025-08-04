package code.api.v2_1_0

import code.TransactionTypes.TransactionType
import code.api.Constant.CAN_SEE_TRANSACTION_REQUESTS
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.TransactionDisabled
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, ApiRole, ErrorMessages, NewStyle}
import code.api.v1_2_1.JSONFactory
import code.api.v1_3_0._
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0._
import code.api.v2_1_0.JSONFactory210._
import code.atms.Atms
import code.bankconnectors._
import code.branches.Branches
import code.consumer.Consumers
import code.customer.CustomerX
import code.fx.fx
import code.metrics.APIMetrics
import code.model.{BankAccountX, BankX, Consumer, UserX}
import code.sandbox.SandboxData
import code.usercustomerlinks.UserCustomerLink
import code.users.Users
import code.util.Helper.booleanToBox
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{ChallengeType, SuppliedAnswerType, TransactionRequestTypes}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.StringHelpers

import java.util.Date
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
// Makes JValue assignment to Nil work
import code.api.ChargePolicy
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.{Box, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Serialization.write
import net.liftweb.json._

trait APIMethods210 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  // helper methods end here

  val Implementations2_1_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    
    val apiVersion = ApiVersion.v2_1_0 // was String "2_1_0"

    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      root,
      apiVersion,
      "root",
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil)

    lazy val root : OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
            (JSONFactory.getApiInfoJSON(OBPAPI2_1_0.version, OBPAPI2_1_0.versionStatus), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    
    // TODO Add example body below

    resourceDocs += ResourceDoc(
      sandboxDataImport,
      apiVersion,
      "sandboxDataImport",
      "POST",
      "/sandbox/data-import",
      "Create sandbox",
      s"""Import bulk data into the sandbox (Authenticated access).
          |
          |This call can be used to create banks, users, accounts and transactions which are stored in the local RDBMS.
          |
          |The user needs to have CanCreateSandbox entitlement.
          |
          |Note: This is a monolithic call. You could also use a combination of endpoints including create bank, create user, create account and create transaction request to create similar data.
          |
          |An example of an import set of data (json) can be found [here](https://raw.githubusercontent.com/OpenBankProject/OBP-API/develop/obp-api/src/main/scala/code/api/sandbox/example_data/2016-04-28/example_import.json)
         |${userAuthenticationMessage(true)}
          |""",
      SandboxData.importJson,
      successMessage,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        DataImportDisabled,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSandbox),
      Some(List(canCreateSandbox)))


    lazy val sandboxDataImport: OBPEndpoint = {
      // Import data into the sandbox
      case "sandbox" :: "data-import" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            importData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $SandboxDataImport ", 400, cc.callContext) {
              json.extract[SandboxDataImport]
            }
            _ <- Helper.booleanToFuture(s"$DataImportDisabled", 403, callContext) {
              APIUtil.getPropsAsBoolValue("allow_sandbox_data_import", defaultValue = false)
            }
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateSandbox, cc.callContext)
            _ <- Helper.booleanToFuture(s"Cannot import the sandbox data", 400, callContext) {
              OBPDataImport.importer.vend.importData(importData).isDefined
            }
          } yield {
            (successMessage, HttpCode.`201`(callContext))
          }
      }
    }


    val getTransactionRequestTypesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getTransactionRequestTypesIsPublic", true)

    resourceDocs += ResourceDoc(
      getTransactionRequestTypesSupportedByBank,
      apiVersion,
      "getTransactionRequestTypesSupportedByBank",
      "GET",
      "/banks/BANK_ID/transaction-request-types",
      "Get Transaction Request Types at Bank",
      s"""Get the list of the Transaction Request Types supported by the bank.
        |
        |${userAuthenticationMessage(!getTransactionRequestTypesIsPublic)}
        |""",
      EmptyBody,
      transactionRequestTypesJSON,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagTransactionRequest, apiTagBank))


    lazy val getTransactionRequestTypesSupportedByBank: OBPEndpoint = {
      // Get transaction request types supported by the bank
      case "banks" :: BankId(bankId) :: "transaction-request-types" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getTransactionRequestTypesIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
            transactionRequestTypes <- Future {
              APIUtil.getPropsValue("transactionRequests_supported_types", "")
            }
          } yield {
            // Format the data as json
            val json = JSONFactory210.createTransactionRequestTypeJSON(transactionRequestTypes.split(",").toList)
            // Return
            (json, HttpCode.`200`(callContext))
          }
      }
    }


    import net.liftweb.json.Extraction._
    import net.liftweb.json.JsonAST._
    val exchangeRates = prettyRender(decompose(fx.fallbackExchangeRates))


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
          |${userAuthenticationMessage(true)}
          |
          |"""




    // SANDBOX_TAN. (we no longer create a resource doc for the general case)
    resourceDocs += ResourceDoc(
      createTransactionRequestSandboxTan,
      apiVersion,
      "createTransactionRequestSandboxTan",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SANDBOX_TAN/transaction-requests",
      "Create Transaction Request (SANDBOX_TAN)",
      s"""When using SANDBOX_TAN, the payee is set in the request body.
         |
         |Money goes into the BANK_ID and ACCOUNT_ID specified in the request body.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyJsonV200,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

    // COUNTERPARTY
    resourceDocs += ResourceDoc(
      createTransactionRequestCounterparty,
      apiVersion,
      "createTransactionRequestCounterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests",
      "Create Transaction Request (COUNTERPARTY)",
      s"""
         |Special instructions for COUNTERPARTY:
         |
         |When using a COUNTERPARTY to create a Transaction Request, specificy the counterparty_id in the body of the request.
         |The routing details of the counterparty will be forwarded for the transfer.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyCounterpartyJSON,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))


    val lowAmount  = AmountOfMoneyJsonV121("EUR", "12.50")
    val sharedChargePolicy = ChargePolicy.withName("SHARED")

    // Transaction Request (SEPA)
    resourceDocs += ResourceDoc(
      createTransactionRequestSepa,
      apiVersion,
      "createTransactionRequestSepa",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests",
      "Create Transaction Request (SEPA)",
      s"""
         |Special instructions for SEPA:
         |
         |When using a SEPA Transaction Request, you specify the IBAN of a Counterparty in the body of the request.
         |The routing details (IBAN) of the counterparty will be forwarded to the core banking system for the transfer.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodySEPAJSON,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))


    // FREE_FORM.
    resourceDocs += ResourceDoc(
      createTransactionRequestFreeForm,
      apiVersion,
      "createTransactionRequestFreeForm",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests",
      "Create Transaction Request (FREE_FORM).",
      s"""$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyFreeFormJSON,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS),
      Some(List(canCreateAnyTransactionRequest)))




    // Different Transaction Request approaches:
    lazy val createTransactionRequestSandboxTan = createTransactionRequest
    lazy val createTransactionRequestSepa = createTransactionRequest
    lazy val createTransactionRequestCounterparty = createTransactionRequest
    lazy val createTransactionRequestFreeForm = createTransactionRequest

    // This handles the above cases
    lazy val createTransactionRequest: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}
            _ <- Helper.booleanToFuture(s"${InvalidTransactionRequestType}: '${transactionRequestType.value}'", cc=callContext) {
              APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext) 
            (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            
            account = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, u, callContext)
            // Check the input JSON format, here is just check the common parts of all four types
            transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $TransactionRequestBodyCommonJSON ", 400, callContext) {
              json.extract[TransactionRequestBodyCommonJSON]
            }
            
            isValidAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${transDetailsJson.value.amount} ", 400, callContext) {
              BigDecimal(transDetailsJson.value.amount)
            }
            
            _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${isValidAmountNumber}'", cc=callContext) {
              isValidAmountNumber > BigDecimal("0")
            }
            
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc=callContext) {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }
            
            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc=callContext) {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }
            
            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"$InvalidTransactionRequestCurrency From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.value.currency}", cc=callContext) {
              transDetailsJson.value.currency == fromAccount.currency
            }
            
            
            amountOfMoneyJSON = AmountOfMoneyJsonV121(transDetailsJson.value.currency, transDetailsJson.value.amount)

            (createdTransactionRequest,callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
              case SANDBOX_TAN => {
                for {
                  transactionRequestBodySandboxTan <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $SANDBOX_TAN json format", 400, callContext) {
                    json.extract[TransactionRequestBodySandBoxTanJSON]
                  }
                  
                  toBankId = BankId(transactionRequestBodySandboxTan.to.bank_id)
                  toAccountId = AccountId(transactionRequestBodySandboxTan.to.account_id)
                  (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext) 
                  
                  transDetailsSerialized <- NewStyle.function.tryons (UnknownError, 400, callContext){write(transactionRequestBodySandboxTan)(Serialization.formats(NoTypeHints))}
                  
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     toAccount,
                                                                                                     transactionRequestType,
                                                                                                     transactionRequestBodySandboxTan,
                                                                                                     transDetailsSerialized,
                                                                                                     sharedChargePolicy.toString,
                                                                                                     None,
                                                                                                     None,
                                                                                                     callContext) //in SANDBOX_TAN, ChargePolicy set default "SHARED"
                } yield (createdTransactionRequest, callContext)
              }
              case COUNTERPARTY => {
                for {
                  //For COUNTERPARTY, Use the counterpartyId to find the toCounterparty and set up the toAccount
                  transactionRequestBodyCounterparty <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $COUNTERPARTY json format", 400, callContext) {
                    json.extract[TransactionRequestBodyCounterpartyJSON]
                  }
                  toCounterpartyId = transactionRequestBodyCounterparty.to.counterparty_id
                  (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(toCounterpartyId), callContext)
                  (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext) 
                  // Check we can send money to it. 
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                    toCounterparty.isBeneficiary == true
                  }
                  chargePolicy = transactionRequestBodyCounterparty.charge_policy
                  _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
                    ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
                  }
                  transDetailsSerialized <- NewStyle.function.tryons (UnknownError, 400, callContext){write(transactionRequestBodyCounterparty)(Serialization.formats(NoTypeHints))}
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     toAccount,
                                                                                                     transactionRequestType,
                                                                                                     transactionRequestBodyCounterparty,
                                                                                                     transDetailsSerialized,
                                                                                                     chargePolicy,
                                                                                                     None,
                                                                                                     None,
                                                                                                     callContext)
                } yield (createdTransactionRequest, callContext)

              }
              case SEPA => {
                for {
                  //For SEPA, Use the iban to find the toCounterparty and set up the toAccount
                  transDetailsSEPAJson <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $SEPA json format", 400, callContext) {
                    json.extract[TransactionRequestBodySEPAJSON]
                  }
                  toIban = transDetailsSEPAJson.to.iban
                  (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIban(toIban, callContext)
                  (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                    toCounterparty.isBeneficiary == true
                  }
                  chargePolicy = transDetailsSEPAJson.charge_policy
                  _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
                    ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
                  }
                  transDetailsSerialized <- NewStyle.function.tryons (UnknownError, 400, callContext){write(transDetailsSEPAJson)(Serialization.formats(NoTypeHints))}
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     toAccount,
                                                                                                     transactionRequestType,
                                                                                                     transDetailsSEPAJson,
                                                                                                     transDetailsSerialized,
                                                                                                     chargePolicy,
                                                                                                     None,
                                                                                                     None,
                                                                                                     callContext)
                } yield (createdTransactionRequest, callContext)
              }
              case FREE_FORM => {
                for {
                  transactionRequestBodyFreeForm <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $FREE_FORM json format", 400, callContext) {
                    json.extract[TransactionRequestBodyFreeFormJSON]
                  }
                  // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                  transactionRequestAccountJSON = TransactionRequestAccountJsonV140(fromAccount.bankId.value, fromAccount.accountId.value) 
                  transDetailsSerialized <- NewStyle.function.tryons (UnknownError, 400, callContext){write(transactionRequestBodyFreeForm)(Serialization.formats(NoTypeHints))}
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     fromAccount,
                                                                                                     transactionRequestType,
                                                                                                     transactionRequestBodyFreeForm,
                                                                                                     transDetailsSerialized,
                                                                                                     sharedChargePolicy.toString,
                                                                                                     None,
                                                                                                     None,
                                                                                                     callContext)
                } yield
                  (createdTransactionRequest, callContext)
              }
            }
          } yield {
            (JSONFactory210.createTransactionRequestWithChargeJSON(createdTransactionRequest), HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      answerTransactionRequestChallenge,
      apiVersion,
      "answerTransactionRequestChallenge",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge",
      """In Sandbox mode, any string that can be converted to a positive integer will be accepted as an answer.
        |
        |This endpoint expects the following data as provided in the createTransactionRequest response body:
        |
        |1)`TRANSACTION_REQUEST_TYPE` : as per the selected createTransactionRequest type, part of the request URL.
        |
        |2)`TRANSACTION_REQUEST_ID` : the value of the `id` field of the createTransactionRequest response body.
        |
        |3) `id` :  the value of `challenge.id` in the createTransactionRequest response body. 
        |
        |4) `answer` : Defaults to `123`, if running in sandbox mode. In production mode, the value will be sent via the configured SCA method.
        |
      """.stripMargin,
      challengeAnswerJSON,
      transactionRequestWithChargeJson,
      List(
        UserNotLoggedIn,
        InvalidBankIdFormat, 
        InvalidAccountIdFormat, 
        InvalidJsonFormat,
        BankNotFound,
        UserNoPermissionAccessView,
        TransactionRequestStatusNotInitiated,
        TransactionRequestTypeHasChanged,
        InvalidTransactionRequestChallengeId,
        AllowedAttemptsUsedUp,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

    lazy val answerTransactionRequestChallenge: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
            for {
              // Check we have a User
              (Full(u), callContext) <- authenticatedAccess(cc)
              _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
              _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
              _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}
              challengeAnswerJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ChallengeAnswerJSON ", 400, callContext) {
                json.extract[ChallengeAnswerJSON]
              }
                
              (_, callContext) <- NewStyle.function.getBank(bankId, callContext) 
              (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

              account = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
              _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, u, callContext)


              // Check transReqId is valid
              (existingTransactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transReqId, callContext)

              // Check the Transaction Request is still INITIATED
              _ <- Helper.booleanToFuture(TransactionRequestStatusNotInitiated, cc=callContext) {
                existingTransactionRequest.status.equals("INITIATED")
              }
              
              // Check the input transactionRequestType is the same as when the user created the TransactionRequest
              existingTransactionRequestType = existingTransactionRequest.`type`
              _ <- Helper.booleanToFuture(s"${TransactionRequestTypeHasChanged} It should be :'$existingTransactionRequestType', but current value (${transactionRequestType.value}) ", cc=callContext) {
                existingTransactionRequestType.equals(transactionRequestType.value)
              }
              
              // Check the challengeId is valid for this existingTransactionRequest
              _ <- Helper.booleanToFuture(s"${InvalidTransactionRequestChallengeId}", cc=callContext) {
                existingTransactionRequest.challenge.id.equals(challengeAnswerJson.id)
              }
              
              //Check the challenge type, Note: not support yet, the default value is SANDBOX_TAN
              _ <- Helper.booleanToFuture(s"${InvalidChallengeType} ", cc=callContext) {
                existingTransactionRequest.challenge.challenge_type == ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString
              }
            
              (isChallengeAnswerValidated, callContext) <- NewStyle.function.validateChallengeAnswer(challengeAnswerJson.id, challengeAnswerJson.answer, SuppliedAnswerType.PLAIN_TEXT_VALUE, callContext)
              
              _ <- Helper.booleanToFuture(s"${InvalidChallengeAnswer
                  .replace("answer may be expired.", s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                  .replace("up your allowed attempts.", s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
              } ", cc = callContext) {
                (isChallengeAnswerValidated == true)
              }

              // All Good, proceed with the Transaction creation...
              (transactionRequest, callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
                case TRANSFER_TO_PHONE | TRANSFER_TO_ATM | TRANSFER_TO_ACCOUNT=>
                   NewStyle.function.createTransactionAfterChallengeV300(u, fromAccount, transReqId, transactionRequestType, callContext)
                case _ =>
                   NewStyle.function.createTransactionAfterChallengeV210(fromAccount, existingTransactionRequest, callContext)
              } 
            } yield {
              
              (JSONFactory210.createTransactionRequestWithChargeJSON(transactionRequest), HttpCode.`202`(callContext))
            }
      }
    }

    resourceDocs += ResourceDoc(
      getTransactionRequests,
      apiVersion,
      "getTransactionRequests",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get Transaction Requests." ,
      """Returns transaction requests for account specified by ACCOUNT_ID at bank specified by BANK_ID.
        |
        |The VIEW_ID specified must be 'owner' and the user must have access to this view.
        |
        |Version 2.0.0 now returns charge information.
        |
        |Transaction Requests serve to initiate transactions that may or may not proceed. They contain information including:
        |
        |* Transaction Request Id
        |* Type
        |* Status (INITIATED, COMPLETED)
        |* Challenge (in order to confirm the request)
        |* From Bank / Account
        |* Details including Currency, Value, Description and other initiation information specific to each type. (Could potentialy include a list of future transactions.)
        |* Related Transactions
        |
        |PSD2 Context: PSD2 requires transparency of charges to the customer.
        |This endpoint provides the charge that would be applied if the Transaction Request proceeds - and a record of that charge there after.
        |The customer can proceed with the Transaction by answering the security challenge.
        |
      """.stripMargin,
      EmptyBody,
      transactionRequestWithChargeJSONs210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        AccountNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPsd2, apiTagOldStyle))

    lazy val getTransactionRequests: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        cc =>
          if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false)) {
            for {
              u <- cc.user ?~ UserNotLoggedIn
              (bank, callContext ) <- BankX(bankId, Some(cc)) ?~! {BankNotFound}
              (fromAccount, callContext) <- BankAccountX(bankId, accountId, Some(cc)) ?~! {AccountNotFound}
              view <- APIUtil.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
              _ <- Helper.booleanToBox(view.allowed_actions.exists(_ == CAN_SEE_TRANSACTION_REQUESTS), 
                s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_REQUESTS)}` permission on the View(${viewId.value} )")
              (transactionRequests,callContext) <- Connector.connector.vend.getTransactionRequests210(u, fromAccount, callContext)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory210.createTransactionRequestJSONs(transactionRequests)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse(TransactionRequestsNotEnabled))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getRoles,
      apiVersion,
      "getRoles",
      "GET",
      "/roles",
      "Get Roles",
      s"""Returns all available roles
        |
        |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      availableRolesJSON,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagRole))

    lazy val getRoles: OBPEndpoint = {
      case "roles" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- authenticatedAccess(cc)
          }
          yield {
            // Format the data as V2.1.0 json
            val json = JSONFactory210.createAvailableRolesJSON(ApiRole.availableRoles.sorted)
            (json, HttpCode.`200`(cc))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getEntitlementsByBankAndUser,
      apiVersion,
      "getEntitlementsByBankAndUser",
      "GET",
      "/banks/BANK_ID/users/USER_ID/entitlements",
      "Get Entitlements for User at Bank",
      s"""
        |
        |Get Entitlements specified by BANK_ID and USER_ID
        |
        |${userAuthenticationMessage(true)}
        |
        |
      """.stripMargin,
      EmptyBody,
      entitlementJSONs,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementsForAnyUserAtOneBank, canGetEntitlementsForAnyUserAtAnyBank)))


    lazy val getEntitlementsByBankAndUser: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(loggedInUser), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            allowedEntitlements = canGetEntitlementsForAnyUserAtOneBank ::
                                  canGetEntitlementsForAnyUserAtAnyBank::
                                  Nil
            allowedEntitlementsTxt = UserHasMissingRoles + allowedEntitlements.mkString(" or ")
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = allowedEntitlementsTxt)(bankId.value, loggedInUser.userId, allowedEntitlements, callContext)
            entitlements <- NewStyle.function.getEntitlementsByUserId(userId, callContext)
          }
          yield {
            val filteredEntitlements = entitlements.filter(_.bankId == bankId.value)
            // Format the data as V2.1.0 json
            if (isSuperAdmin(userId)) {
              // If the user is SuperAdmin add it to the list
              val json = JSONFactory200.addedSuperAdminEntitlementJson(filteredEntitlements)
              successJsonResponse(Extraction.decompose(json))
              (json, HttpCode.`200`(callContext))
            } else {
              val json = JSONFactory200.createEntitlementJSONs(filteredEntitlements)
              (json, HttpCode.`200`(callContext))
            }
          }
      }
    }

    resourceDocs += ResourceDoc(
      getConsumer,
      apiVersion,
      "getConsumer",
      "GET",
      "/management/consumers/CONSUMER_ID",
      "Get Consumer",
      s"""Get the Consumer specified by CONSUMER_ID.
        |
        |""",
      EmptyBody,
      consumerJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidConsumerId,
        UnknownError
      ),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canGetConsumers)))


    lazy val getConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: Nil JsonGet _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn
            _ <- NewStyle.function.ownEntitlement("", u.userId, ApiRole.canGetConsumers, cc.callContext)

            consumerIdToLong <- tryo{consumerId.toLong} ?~! InvalidConsumerId
            consumer <- Consumers.consumers.vend.getConsumerByPrimaryId(consumerIdToLong)
          } yield {
            val json = createConsumerJSON(consumer)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getConsumers,
      apiVersion,
      "getConsumers",
      "GET",
      "/management/consumers",
      "Get Consumers",
      s"""Get the all Consumers.
          |
        |""",
      EmptyBody,
      consumersJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canGetConsumers)))


    lazy val getConsumers: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonGet _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn
            _ <- NewStyle.function.ownEntitlement("", u.userId, ApiRole.canGetConsumers, cc.callContext)
            consumers <- Some(Consumer.findAll())
          } yield {
            // Format the data as json
            val json = createConsumerJSONs(consumers.sortWith(_.id.get < _.id.get))
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      enableDisableConsumers,
      apiVersion,
      "enableDisableConsumers",
      "PUT",
      "/management/consumers/CONSUMER_ID",
      "Enable or Disable Consumers",
      s"""Enable/Disable a Consumer specified by CONSUMER_ID.
        |
        |""",
      putEnabledJSON,
      putEnabledJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canEnableConsumers,canDisableConsumers)))


    lazy val enableDisableConsumers: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn
            putData <- tryo{json.extract[PutEnabledJSON]} ?~! InvalidJsonFormat
            _ <- putData.enabled match {
              case true  => NewStyle.function.ownEntitlement("", u.userId, ApiRole.canEnableConsumers, cc.callContext)
              case false => NewStyle.function.ownEntitlement("", u.userId, ApiRole.canDisableConsumers, cc.callContext)
            }
            consumer <- Consumers.consumers.vend.getConsumerByPrimaryId(consumerId.toLong)
            updatedConsumer <- Consumers.consumers.vend.updateConsumer(consumer.id.get, None, None, Some(putData.enabled), None, None, None, None, None,  None, None, None) ?~! "Cannot update Consumer"
          } yield {
            // Format the data as json
            val json = PutEnabledJSON(updatedConsumer.isActive.get)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }



    resourceDocs += ResourceDoc(
      addCardForBank,
      apiVersion,
      "addCardsForBank",
      "POST",
      "/banks/BANK_ID/cards",
      "Create Card",
      s"""Create Card at bank specified by BANK_ID .
          |
          |${userAuthenticationMessage(true)}
          |""",
      postPhysicalCardJSON,
      physicalCardJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        AllowedValuesAre,
        UnknownError
      ),
      List(apiTagCard),
      Some(List(canCreateCardsForBank)))


    lazy val addCardForBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateCardsForBank, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostPhysicalCardJSON "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostPhysicalCardJSON]
            }

            _<-Helper.booleanToFuture(s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}", cc=callContext)(postJson.issue_number.length<= 10)

            _ <- postJson.allows match {
              case List() => Future {true}
              case _ => Helper.booleanToFuture(AllowedValuesAre + CardAction.availableValues.mkString(", "), cc=callContext)(postJson.allows.forall(a => CardAction.availableValues.contains(a)))
            }

            failMsg = AllowedValuesAre + CardReplacementReason.availableValues.mkString(", ")
            replacementReasionRequest <- NewStyle.function.tryons(failMsg, 400, callContext) {
              CardReplacementReason.valueOf(postJson.replacement.reason_requested)
            }

            (_, callContext)<- NewStyle.function.getBankAccount(bankId, AccountId(postJson.account_id), callContext)

            (card, callContext) <- NewStyle.function.createPhysicalCard(
              bankCardNumber=postJson.bank_card_number,
              nameOnCard=postJson.name_on_card,
              cardType = "",// this field is introduced from V310
              issueNumber=postJson.issue_number,
              serialNumber=postJson.serial_number,
              validFrom=postJson.valid_from_date,
              expires=postJson.expires_date,
              enabled=postJson.enabled,
              cancelled=false,
              onHotList=false,
              technology=postJson.technology,
              networks= postJson.networks,
              allows= postJson.allows,
              accountId= postJson.account_id,
              bankId=bankId.value,
              replacement= Some(CardReplacementInfo(requestedDate = postJson.replacement.requested_date, replacementReasionRequest)),
              pinResets= postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected= Option(CardCollectionInfo(postJson.collected)),
              posted= Option(CardPostedInfo(postJson.posted)),
              customerId = "",// this field is introduced from V310
              cvv = "",// this field is introduced from V500
              brand = "",// this field is introduced from V500
              callContext
            )
            
          } yield {
            (JSONFactory1_3_0.createPhysicalCardJSON(card, u), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUsers,
      apiVersion,
      "getUsers",
      "GET",
      "/users",
      "Get all Users",
      s"""Get all users
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
        |${urlParametersDocument(false, false)}
        |* locked_status (if null ignore)
        |
      """.stripMargin,
      EmptyBody,
      usersJsonV200,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyUser, callContext)
            queryParams <- unboxFullAndWrapIntoFuture{ createQueriesByHttpParams(callContext.get.requestHeaders) }
            users <- Users.users.vend.getAllUsersF(queryParams)
          } yield {
            (JSONFactory210.createUserJSONs (users), callContext)
          }
      }
    }

    val getTransactionTypesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getTransactionTypesIsPublic", true)

    resourceDocs += ResourceDoc(
      createTransactionType,
      apiVersion,
      "createTransactionType",
      "PUT",
      "/banks/BANK_ID/transaction-types",
      "Create Transaction Type at bank",
      // TODO get the documentation of the parameters from the scala doc of the case class we return
      s"""Create Transaction Types for the bank specified by BANK_ID:
          |
          |  * id : Unique transaction type id across the API instance. SHOULD be a UUID. MUST be unique.
          |  * bank_id : The bank that supports this TransactionType
          |  * short_code : A short code (SHOULD have no-spaces) which MUST be unique across the bank. May be stored with Transactions to link here
          |  * summary : A succinct summary
          |  * description : A longer description
          |  * charge : The charge to the customer for each one of these
          |
          |${userAuthenticationMessage(getTransactionTypesIsPublic)}""".stripMargin,
      transactionTypeJsonV200,
      transactionType,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        InsufficientAuthorisationToCreateTransactionType,
        UnknownError
      ),
      List(apiTagBank),
      Some(List(canCreateTransactionType))
    )



    lazy val createTransactionType: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "transaction-types" ::  Nil JsonPut json -> _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            postedData <- NewStyle.function.tryons(failMsg=InvalidJsonFormat, callContext=callContext) {json.extract[TransactionTypeJsonV200]}
            _ <- Future (NewStyle.function.ownEntitlement(bankId.value, u.userId, canCreateTransactionType, callContext, InsufficientAuthorisationToCreateTransactionType))map { fullBoxOrException(_)} map { unboxFull(_) }
            returnTranscationType <- Future(TransactionType.TransactionTypeProvider.vend.createOrUpdateTransactionType(postedData)) map { fullBoxOrException(_)} map { unboxFull(_) }
          } yield {
            (returnTranscationType, HttpCode.`200`(callContext))
          }
        }
      }
    }


    val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    resourceDocs += ResourceDoc(
      getAtm,
      apiVersion,
      "getAtm",
      "GET",
      "/banks/BANK_ID/atms/ATM_ID",
      "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID including:
          |
          |* Address
          |* Geo Location
          |* License the data under this endpoint is released under
          |
          |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmJson,
      List(UserNotLoggedIn, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      List(apiTagATM, apiTagOldStyle)
    )

    lazy val getAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet _ => {
        cc =>{
          for {
          // Get atm from the active provider
            _ <- if (getAtmsIsPublic)
              Box(Some(1))
            else
              cc.user ?~! UserNotLoggedIn
            (bank, callContext ) <- BankX(bankId, Some(cc)) ?~! {BankNotFound}
            atm  <- Box(Atms.atmsProvider.vend.getAtm(bankId, atmId)) ?~! {AtmNotFoundByAtmId}
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createAtmJson(atm)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    resourceDocs += ResourceDoc(
      getBranch,
      apiVersion,
      "getBranch",
      "GET",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Get Bank Branch",
      s"""Returns information about branches for a single bank specified by BANK_ID and BRANCH_ID including:
          | meta.license.id and eta.license.name fields must not be empty. 
          |
          |* Name
          |* Address
          |* Geo Location
          |* License the data under this endpoint is released under
          |
        |${userAuthenticationMessage(!getBranchesIsPublic)}""".stripMargin,
      EmptyBody,
      branchJson,
      List(
        UserNotLoggedIn,
        BranchNotFoundByBranchId,
        UnknownError
      ),
      List(apiTagBranch, apiTagOldStyle)
    )

    lazy val getBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId) :: Nil JsonGet _ => {
        cc =>{
          for {
            _ <- if (getBranchesIsPublic)
              Box(Some(1))
            else
              cc.user ?~! UserNotLoggedIn
            (bank, callContext ) <- BankX(bankId, Some(cc)) ?~! {BankNotFound}
            branch <- Box(Branches.branchesProvider.vend.getBranch(bankId, branchId)) ?~! BranchNotFoundByBranchId
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createBranchJson(branch)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    val getProductsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getProductsIsPublic", true)


    resourceDocs += ResourceDoc(
      getProduct,
      apiVersion,
      "getProduct",
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID and PRODUCT_CODE including:
          |
          |* Name
          |* Code
          |* Category
          |* Family
          |* Super Family
          |* More info URL
          |* Description
          |* Terms and Conditions
          |* License the data under this endpoint is released under
          |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV210,
      List(
        UserNotLoggedIn,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      List(apiTagProduct)
    )

    lazy val getProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (product, callContext) <- NewStyle.function.getProduct(bankId, productCode, callContext)
          } yield {
            (JSONFactory210.createProductJson(product), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getProducts,
      apiVersion,
      "getProducts",
      "GET",
      "/banks/BANK_ID/products",
      "Get Bank Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID including:
          |
          |* Name
          |* Code
          |* Category
          |* Family
          |* Super Family
          |* More info URL
          |* Description
          |* Terms and Conditions
          |* License the data under this endpoint is released under
          |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productsJsonV210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      List(apiTagProduct)
    )

    lazy val getProducts : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: Nil  JsonGet req => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            params = req.params.toList.map(kv => GetProductsParam(kv._1, kv._2))
            (products,callContext) <- NewStyle.function.getProducts(bankId, params, callContext)
          } yield {
            (JSONFactory210.createProductsJson(products), HttpCode.`200`(callContext))
          }
        }
      }
    }

    val createCustomerEntitlementsRequiredForSpecificBank = canCreateCustomer ::
      canCreateUserCustomerLink ::
      Nil
    val createCustomerEntitlementsRequiredForAnyBank = canCreateCustomerAtAnyBank ::
      canCreateUserCustomerLinkAtAnyBank ::
      Nil
    val createCustomeEntitlementsRequiredText = createCustomerEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createCustomerEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createCustomer,
      apiVersion,
      "createCustomer",
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""Add a customer linked to the user specified by user_id
          |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
          |Dates need to be in the format 2013-01-21T23:08:00Z
          |
          |${userAuthenticationMessage(true)}
          |
          |$createCustomeEntitlementsRequiredText
          |""",
      postCustomerJsonV210,
      customerJsonV210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        CustomerNumberAlreadyExists,
        UserNotFoundById,
        CustomerAlreadyExistsForUser,
        CreateConsumerError,
        UnknownError
      ),
      List(apiTagCustomer, apiTagPerson, apiTagOldStyle),
      Some(List(canCreateCustomer,canCreateUserCustomerLink,canCreateCustomerAtAnyBank,canCreateUserCustomerLinkAtAnyBank)))

    // TODO in next version?
    // Separate customer creation (keep here) from customer linking (remove from here)
    // Remove user_id from CreateCustomerJson

    // Note: Logged in user can no longer create a customer for himself


    lazy val createCustomer : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn // TODO. CHECK user has role to create a customer / create a customer for another user id.
            _ <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
            (bank, callContext ) <- BankX(bankId, Some(cc)) ?~! {BankNotFound}
            postedData <- tryo{json.extract[PostCustomerJsonV210]} ?~! InvalidJsonFormat
            _ <- Helper.booleanToBox(
              !`checkIfContains::::` (postedData.customer_number), s"$InvalidJsonFormat customer_number can not contain `::::` characters")
            _ <- NewStyle.function.hasAllEntitlements(bankId.value, u.userId, createCustomerEntitlementsRequiredForSpecificBank, createCustomerEntitlementsRequiredForAnyBank, callContext)
            _ <- tryo(assert(CustomerX.customerProvider.vend.checkCustomerNumberAvailable(bankId, postedData.customer_number) == true)) ?~! CustomerNumberAlreadyExists
            user_id <- tryo (if (postedData.user_id.nonEmpty) postedData.user_id else u.userId) ?~! s"Problem getting user_id"
            customer_user <- UserX.findByUserId(user_id) ?~! UserNotFoundById
            customer <- CustomerX.customerProvider.vend.addCustomer(bankId,
              postedData.customer_number,
              postedData.legal_name,
              postedData.mobile_phone_number,
              postedData.email,
              CustomerFaceImage(postedData.face_image.date, postedData.face_image.url),
              postedData.date_of_birth,
              postedData.relationship_status,
              postedData.dependants,
              postedData.dob_of_dependants,
              postedData.highest_education_attained,
              postedData.employment_status,
              postedData.kyc_status,
              postedData.last_ok_date,
              Option(CreditRating(postedData.credit_rating.rating, postedData.credit_rating.source)),
              Option(CreditLimit(postedData.credit_limit.currency, postedData.credit_limit.amount)),
              "",
              "",
              "") ?~! CreateConsumerError
            _ <- booleanToBox(UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(user_id, customer.customerId).isEmpty == true) ?~! CustomerAlreadyExistsForUser
            _ <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(user_id, customer.customerId, new Date(), true) ?~! CreateUserCustomerLinksError
            
          } yield {
            val json = JSONFactory210.createCustomerJson(customer)
            val successJson = Extraction.decompose(json)
            successJsonResponse(successJson, 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCustomersForUser,
      apiVersion,
      "getCustomersForUser",
      "GET",
      "/users/current/customers",
      "Get Customers for Current User",
      """Gets all Customers that are linked to a User.
        |
        |Authentication via OAuth is required.""",
      EmptyBody,
      customerJsonV210,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser, apiTagOldStyle))

    lazy val getCustomersForUser : OBPEndpoint = {
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        cc => {
          for {
            u <- cc.user ?~! UserNotLoggedIn
            customers <- tryo{CustomerX.customerProvider.vend.getCustomersByUserId(u.userId)} ?~! UserCustomerLinksNotFoundForUser
          } yield {
            val json = JSONFactory210.createCustomersJson(customers)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCustomersForCurrentUserAtBank,
      apiVersion,
      "getCustomersForCurrentUserAtBank",
      "GET",
      "/banks/BANK_ID/customers",
      "Get Customers for current User at Bank",
      s"""Returns a list of Customers at the Bank that are linked to the currently authenticated User.
        |
        |
        |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      customerJSONs,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UserCustomerLinksNotFoundForUser,
        CustomerNotFoundByCustomerId,
        UnknownError
      ),
      List(apiTagCustomer)
    )

    lazy val getCustomersForCurrentUserAtBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (customers, callContext) <- Connector.connector.vend.getCustomersByUserId(u.userId, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            // Filter so we only see the ones for the bank in question
            val bankCustomers = customers.filter(_.bankId==bankId.value)
            val json = JSONFactory210.createCustomersJson(bankCustomers)
            (json, HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      updateBranch,
      apiVersion,
      "updateBranch",
      "PUT",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Update Branch",
      s"""Update an existing branch for a bank account (Authenticated access).
         |${userAuthenticationMessage(true)}
         |""",
      branchJsonPut,
      branchJson,
      List(
        UserNotLoggedIn, 
        BankNotFound, 
        InvalidJsonFormat,
        UserHasMissingRoles, 
        UnknownError
      ),
      List(apiTagBranch),
      Some(List(canUpdateBranch)))


    lazy val updateBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId)::  Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            branchJsonPutV210 <- NewStyle.function.tryons(failMsg = InvalidJsonFormat + " BranchJsonPutV210", 400, callContext) {
              json.extract[BranchJsonPutV210]
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, callContext) {
              branchJsonPutV210.bank_id == bank.bankId.value
            }
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canUpdateBranch, callContext)
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, callContext) {
              transformToBranch(branchId, branchJsonPutV210).head
            }
            (success, callContext)  <- NewStyle.function.createOrUpdateBranch(branch, callContext)
          } yield {
            val json = JSONFactory1_4_0.createBranchJson(success)
            (json, HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createBranch,
      apiVersion,
      "createBranch",
      "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create branch for the bank (Authenticated access).
          |${userAuthenticationMessage(true)}
          |""",
      branchJsonPost,
      branchJson,
      List(
        UserNotLoggedIn, 
        BankNotFound, 
        InvalidJsonFormat, 
        InsufficientAuthorisationToCreateBranch, 
        UnknownError
      ),
      List(apiTagBranch, apiTagOpenData),
      Some(List(canCreateBranch, canCreateBranchAtAnyBank)))

    lazy val createBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ =>
        {
          cc => implicit val ec = EndpointContext(Some(cc))
            for {
              (Full(u), callContext) <- authenticatedAccess(cc)
              (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
              branchJsonPostV210 <- NewStyle.function.tryons(failMsg = InvalidJsonFormat + " BranchJsonPostV210", 400, callContext) {
                json.extract[BranchJsonPostV210]
              }
              _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, callContext) {
                branchJsonPostV210.bank_id == bank.bankId.value
              }
              _ <- Future(
                NewStyle.function.hasAllEntitlements(bank.bankId.value, u.userId, canCreateBranch::Nil, canCreateBranchAtAnyBank::Nil, cc.callContext)
              )
              branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, cc.callContext) {
                transformToBranch(branchJsonPostV210).head
              }
              (success, callContext) <- NewStyle.function.createOrUpdateBranch(branch, callContext)
            } yield {
              val json = JSONFactory1_4_0.createBranchJson(success)
              (json, HttpCode.`201`(callContext))
            }
        }
    }

    resourceDocs += ResourceDoc(
      updateConsumerRedirectUrl,
      apiVersion,
      "updateConsumerRedirectUrl",
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/redirect_url",
      "Update Consumer RedirectUrl",
      s"""Update an existing redirectUrl for a Consumer specified by CONSUMER_ID.
         |
         | CONSUMER_ID can be obtained after you register the application. 
         | 
         | Or use the endpoint 'Get Consumers' to get it  
         | 
       """.stripMargin,
      consumerRedirectUrlJSON,
      consumerJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canUpdateConsumerRedirectUrl))
    )
    
    lazy val updateConsumerRedirectUrl: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "redirect_url" :: Nil JsonPut json -> _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false) match {
              case true => Future(Full(Unit))
              case false => NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUpdateConsumerRedirectUrl, callContext)
            }
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) {
              json.extract[ConsumerRedirectUrlJSON]
            }
            consumerIdToLong <- NewStyle.function.tryons(InvalidConsumerId, 400, callContext) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdToLong, callContext) 
            //only the developer that created the Consumer should be able to edit it
            _ <- Helper.booleanToFuture(UserNoPermissionUpdateConsumer, 400, callContext) {
              consumer.createdByUserId.equals(u.userId)
            }
            //update the redirectURL and isactive (set to false when change redirectUrl) field in consumer table
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get,
              isActive = Some(APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false)),
              redirectURL = Some(postJson.redirect_url),
              callContext = callContext
            )
          } yield {
            val json = JSONFactory210.createConsumerJSON(updatedConsumer)
            (json, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getMetrics,
      apiVersion,
      "getMetrics",
      "GET",
      "/management/metrics",
      "Get Metrics",
      s"""Get the all metrics
        |
        |require CanReadMetrics role
        |
        |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
        |
        |Should be able to filter on the following metrics fields
        |
        |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
        |
        |1 from_date (defaults to one week before current date): eg:from_date=$DateWithMsExampleString
        |
        |2 to_date (defaults to current date) eg:to_date=$DateWithMsExampleString
        |
        |3 limit (for pagination: defaults to 50)  eg:limit=200
        |
        |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
        |
        |5 sort_by (defaults to date field) eg: sort_by=date
        |  possible values:
        |    "url",
        |    "date",
        |    "user_name",
        |    "app_name",
        |    "developer_email",
        |    "implemented_by_partial_function",
        |    "implemented_in_version",
        |    "consumer_id",
        |    "verb"
        |
        |6 direction (defaults to date desc) eg: direction=desc
        |
        |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=10000&offset=0&anon=false&app_name=TeatApp&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&user_name=susan.uk.29@example.com&consumer_id=78
        |
        |Other filters:
        |
        |7 consumer_id  (if null ignore)
        |
        |8 user_id (if null ignore)
        |
        |9 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
        |
        |10 url (if null ignore), note: can not contain '&'.
        |
        |11 app_name (if null ignore)
        |
        |12 implemented_by_partial_function (if null ignore),
        |
        |13 implemented_in_version (if null ignore)
        |
        |14 verb (if null ignore)
        |
        |15 correlation_id (if null ignore)
        |
        |16 duration (if null ignore) non digit chars will be silently omitted
        |
      """.stripMargin,
      EmptyBody,
      metricsJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMetric, apiTagApi),
      Some(List(canReadMetrics)))

    lazy val getMetrics : OBPEndpoint = {
      case "management" :: "metrics" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadMetrics, callContext)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams)) 
          } yield {
            (JSONFactory210.createMetricsJson(metrics), HttpCode.`200`(callContext))
          }
        }
      }
    }
  }
}
