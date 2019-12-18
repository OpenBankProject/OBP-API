package code.api.v4_0_0

import java.util.Date
import code.api.Constant._
import code.api.ChargePolicy
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{fullBoxOrException, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue.{dynamicEntityRequestBodyExample, dynamicEntityResponseBodyExample, userIdExample}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.{JSONFactory, PostTransactionTagJSON}
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, TransactionRequestAccountJsonV140}
import code.api.v2_0_0.{EntitlementJSON, EntitlementJSONs, JSONFactory200}
import code.api.v2_1_0._
import code.api.v2_2_0.{BankJSONV220, CreateAccountJSONV220, JSONFactory220}
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0.{CreateAccountRequestJsonV310, JSONFactory310, ListResult}
import code.api.v4_0_0.JSONFactory400.{createBankAccountJSON, createNewCoreBankAccountJson}
import code.dynamicEntity.DynamicEntityCommons
import code.entitlement.Entitlement
import code.metadata.tags.Tags
import code.model.dataAccess.{AuthUser, BankAccountCreation}
import code.model.toUserExtended
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _, _}
import code.users.Users
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import net.liftweb.common.{Box, Full, ParamFailure}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import net.liftweb.util.Helpers.now
import net.liftweb.util.StringHelpers
import org.atteo.evo.inflector.English

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods400 {
  self: RestHelper =>

  val Implementations4_0_0 = new Implementations400()

  class Implementations400 {

    val implementedInApiVersion = ApiVersion.v4_0_0

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      getBanks,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      """Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      emptyObjectJson,
      banksJSON,
      List(UnknownError),
      Catalogs(Core, PSD2, OBWG),
      apiTagBank :: apiTagPSD2AIS :: apiTagNewStyle :: Nil)

    lazy val getBanks: OBPEndpoint = {
      case "banks" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (banks, callContext) <- NewStyle.function.getBanks(callContext)
          } yield {
            (JSONFactory400.createBanksJson(banks), HttpCode.`200`(callContext))
          }

      }
    }

    val exchangeRates =
      APIUtil.getPropsValue("webui_api_explorer_url", "") +
        "/more?version=OBPv4.0.0&list-all-banks=false&core=&psd2=&obwg=#OBPv2_2_0-getCurrentFxRate"


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
         |In sandbox mode, TRANSACTION_REQUEST_TYPE is commonly set to ACCOUNT. See getTransactionRequestTypesSupportedByBank for all supported types.
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


    // ACCOUNT. (we no longer create a resource doc for the general case)
    resourceDocs += ResourceDoc(
      createTransactionRequestAccount,
      implementedInApiVersion,
      "createTransactionRequestAccount",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/ACCOUNT/transaction-requests",
      "Create Transaction Request (ACCOUNT)",
      s"""When using ACCOUNT, the payee is set in the request body.
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle))

    // ACCOUNT_OTP. (we no longer create a resource doc for the general case)
    resourceDocs += ResourceDoc(
      createTransactionRequestAccountOtp,
      implementedInApiVersion,
      "createTransactionRequestAccountOtp",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/ACCOUNT_OTP/transaction-requests",
      "Create Transaction Request (ACCOUNT_OTP)",
      s"""When using ACCOUNT, the payee is set in the request body.
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle))

    // COUNTERPARTY
    resourceDocs += ResourceDoc(
      createTransactionRequestCounterparty,
      implementedInApiVersion,
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle))


    val lowAmount = AmountOfMoneyJsonV121("EUR", "12.50")
    val sharedChargePolicy = ChargePolicy.withName("SHARED")

    // Transaction Request (SEPA)
    resourceDocs += ResourceDoc(
      createTransactionRequestSepa,
      implementedInApiVersion,
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle))


    // FREE_FORM.
    resourceDocs += ResourceDoc(
      createTransactionRequestFreeForm,
      implementedInApiVersion,
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
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle),
      Some(List(canCreateAnyTransactionRequest)))


    // Different Transaction Request approaches:
    lazy val createTransactionRequestAccount = createTransactionRequest
    lazy val createTransactionRequestAccountOtp = createTransactionRequest
    lazy val createTransactionRequestSepa = createTransactionRequest
    lazy val createTransactionRequestCounterparty = createTransactionRequest
    lazy val createTransactionRequestFreeForm = createTransactionRequest

    // This handles the above cases
    lazy val createTransactionRequest: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.isEnabledTransactionRequests()
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat) {
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat) {
              isValidID(bankId.value)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            
            account = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, u, callContext)
            
            _ <- Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionRequest) {
              u.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)) == true ||
                hasEntitlement(fromAccount.bankId.value, u.userId, ApiRole.canCreateAnyTransactionRequest) == true
            }

            _ <- Helper.booleanToFuture(s"${InvalidTransactionRequestType}: '${transactionRequestType.value}'") {
              APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value)
            }

            // Check the input JSON format, here is just check the common parts of all four types
            transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $TransactionRequestBodyCommonJSON ", 400, callContext) {
              json.extract[TransactionRequestBodyCommonJSON]
            }

            isValidAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${transDetailsJson.value.amount} ", 400, callContext) {
              BigDecimal(transDetailsJson.value.amount)
            }

            _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${isValidAmountNumber}'") {
              isValidAmountNumber > BigDecimal("0")
            }

            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'") {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }

            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'") {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }

            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.value.currency}") {
              transDetailsJson.value.currency == fromAccount.currency
            }

            (createdTransactionRequest, callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
              case ACCOUNT | SANDBOX_TAN => {
                for {
                  transactionRequestBodySandboxTan <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $ACCOUNT json format", 400, callContext) {
                    json.extract[TransactionRequestBodySandBoxTanJSON]
                  }

                  toBankId = BankId(transactionRequestBodySandboxTan.to.bank_id)
                  toAccountId = AccountId(transactionRequestBodySandboxTan.to.account_id)
                  (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext)

                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodySandboxTan)(Serialization.formats(NoTypeHints))
                  }

                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transactionRequestBodySandboxTan,
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    callContext) //in ACCOUNT, ChargePolicy set default "SHARED"
                } yield (createdTransactionRequest, callContext)
              }
              case ACCOUNT_OTP => {
                for {
                  transactionRequestBodySandboxTan <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $ACCOUNT json format", 400, callContext) {
                    json.extract[TransactionRequestBodySandBoxTanJSON]
                  }

                  toBankId = BankId(transactionRequestBodySandboxTan.to.bank_id)
                  toAccountId = AccountId(transactionRequestBodySandboxTan.to.account_id)
                  (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext)

                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodySandboxTan)(Serialization.formats(NoTypeHints))
                  }

                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transactionRequestBodySandboxTan,
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_WEB_FORM.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    callContext) //in ACCOUNT, ChargePolicy set default "SHARED"
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
                  toAccount <- NewStyle.function.toBankAccount(toCounterparty, callContext)
                  // Check we can send money to it.
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit") {
                    toCounterparty.isBeneficiary == true
                  }
                  chargePolicy = transactionRequestBodyCounterparty.charge_policy
                  _ <- Helper.booleanToFuture(s"$InvalidChargePolicy") {
                    ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
                  }
                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodyCounterparty)(Serialization.formats(NoTypeHints))
                  }
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transactionRequestBodyCounterparty,
                    transDetailsSerialized,
                    chargePolicy,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
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
                  toAccount <- NewStyle.function.toBankAccount(toCounterparty, callContext)
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit") {
                    toCounterparty.isBeneficiary == true
                  }
                  chargePolicy = transDetailsSEPAJson.charge_policy
                  _ <- Helper.booleanToFuture(s"$InvalidChargePolicy") {
                    ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
                  }
                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transDetailsSEPAJson)(Serialization.formats(NoTypeHints))
                  }
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transDetailsSEPAJson,
                    transDetailsSerialized,
                    chargePolicy,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
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
                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodyFreeForm)(Serialization.formats(NoTypeHints))
                  }
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(u,
                    viewId,
                    fromAccount,
                    fromAccount,
                    transactionRequestType,
                    transactionRequestBodyFreeForm,
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    callContext)
                } yield
                  (createdTransactionRequest, callContext)
              }
            }
          } yield {
            (JSONFactory400.createTransactionRequestWithChargeJSON(createdTransactionRequest), HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      answerTransactionRequestChallenge,
      implementedInApiVersion,
      "answerTransactionRequestChallenge",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge.",
      """In Sandbox mode, any string that can be converted to a positive integer will be accepted as an answer.
        |
        |This endpoint totally depends on createTransactionRequest, it need get the following data from createTransactionRequest response body.
        |
        |1)`TRANSACTION_REQUEST_TYPE` : is the same as createTransactionRequest request URL .
        |
        |2)`TRANSACTION_REQUEST_ID` : is the `id` field in createTransactionRequest response body.
        |
        |3) `id` :  is `challenge.id` field in createTransactionRequest response body.
        |
        |4) `answer` : must be `123`. if it is in sandbox mode. If it kafka mode, the answer can be got by phone message or other security ways.
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
        InvalidTransactionRequesChallengeId,
        AllowedAttemptsUsedUp,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle))

    lazy val answerTransactionRequestChallenge: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            // Check we have a User
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.isEnabledTransactionRequests()
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat) {
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat) {
              isValidID(bankId.value)
            }
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
            _ <- Helper.booleanToFuture(TransactionRequestStatusNotInitiated) {
              existingTransactionRequest.status.equals("INITIATED")
            }

            // Check the input transactionRequestType is the same as when the user created the TransactionRequest
            existingTransactionRequestType = existingTransactionRequest.`type`
            _ <- Helper.booleanToFuture(s"${TransactionRequestTypeHasChanged} It should be :'$existingTransactionRequestType', but current value (${transactionRequestType.value}) ") {
              existingTransactionRequestType.equals(transactionRequestType.value)
            }

            // Check the challengeId is valid for this existingTransactionRequest
            _ <- Helper.booleanToFuture(s"${InvalidTransactionRequesChallengeId}") {
              existingTransactionRequest.challenge.id.equals(challengeAnswerJson.id)
            }

            //Check the allowed attemps, Note: not support yet, the default value is 3
            _ <- Helper.booleanToFuture(s"${AllowedAttemptsUsedUp}") {
              existingTransactionRequest.challenge.allowed_attempts > 0
            }

            //Check the challenge type, Note: not support yet, the default value is SANDBOX_TAN
            _ <- Helper.booleanToFuture(s"${InvalidChallengeType} ") {
              List(
                OTP_VIA_API.toString,
                OTP_VIA_WEB_FORM.toString
              ).exists(_ == existingTransactionRequest.challenge.challenge_type)
            }

            challengeAnswerOBP <- NewStyle.function.validateChallengeAnswerInOBPSide(challengeAnswerJson.id, challengeAnswerJson.answer, callContext)

            _ <- Helper.booleanToFuture(s"$InvalidChallengeAnswer") {
              challengeAnswerOBP == true
            }

            (challengeAnswerKafka, callContext) <- NewStyle.function.validateChallengeAnswer(challengeAnswerJson.id, challengeAnswerJson.answer, callContext)

            _ <- Helper.booleanToFuture(s"${InvalidChallengeAnswer} ") {
              (challengeAnswerKafka == true)
            }

            // All Good, proceed with the Transaction creation...
            (transactionRequest, callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
              case TRANSFER_TO_PHONE | TRANSFER_TO_ATM | TRANSFER_TO_ACCOUNT =>
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
      getDynamicEntities,
      implementedInApiVersion,
      nameOf(getDynamicEntities),
      "GET",
      "/management/dynamic_entities",
      "Get DynamicEntities",
      s"""Get the all DynamicEntities.""",
      emptyObjectJson,
      ListResult(
        "dynamic_entities",
        List(dynamicEntityResponseBodyExample)
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canGetDynamicEntities))
    )


    lazy val getDynamicEntities: OBPEndpoint = {
      case "management" :: "dynamic_entities" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetDynamicEntities, callContext)
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities())
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            val jObjects = listCommons.map(_.jValue)
            (ListResult("dynamic_entities", jObjects), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createDynamicEntity,
      implementedInApiVersion,
      nameOf(createDynamicEntity),
      "POST",
      "/management/dynamic_entities",
      "Create DynamicEntity",
      s"""Create a DynamicEntity.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |Create one DynamicEntity, after created success, the corresponding CURD endpoints will be generated automatically
         |
         |Current support filed types as follow:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", "]")}
         |
         |""",
      dynamicEntityRequestBodyExample,
      dynamicEntityResponseBodyExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canCreateDynamicEntity)))

    lazy val createDynamicEntity: OBPEndpoint = {
      case "management" :: "dynamic_entities" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateDynamicEntity, callContext)

            jsonObject = json.asInstanceOf[JObject]
            dynamicEntity = DynamicEntityCommons(jsonObject, None)
            Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, callContext)
          } yield {
            val commonsData: DynamicEntityCommons = result
            (commonsData.jValue, HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateDynamicEntity,
      implementedInApiVersion,
      nameOf(updateDynamicEntity),
      "PUT",
      "/management/dynamic_entities/DYNAMIC_ENTITY_ID",
      "Update DynamicEntity",
      s"""Update a DynamicEntity.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |Update one DynamicEntity, after update finished, the corresponding CURD endpoints will be changed.
         |
         |Current support filed types as follow:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", "]")}
         |
         |""",
      dynamicEntityRequestBodyExample,
      dynamicEntityResponseBodyExample,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateDynamicEntity)))

    lazy val updateDynamicEntity: OBPEndpoint = {
      case "management" :: "dynamic_entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canUpdateDynamicEntity, callContext)

            // Check whether there are uploaded data, only if no uploaded data allow to update DynamicEntity.
            (entity, _) <- NewStyle.function.getDynamicEntityById(dynamicEntityId, callContext)
            (isExists, _) <- NewStyle.function.invokeDynamicConnector(IS_EXISTS_DATA, entity.entityName, None, None, callContext)
            _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed) {
              isExists.isDefined && isExists.contains(JBool(false))
            }

            jsonObject = json.asInstanceOf[JObject]
            dynamicEntity = DynamicEntityCommons(jsonObject, Some(dynamicEntityId))
            Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, callContext)
          } yield {
            val commonsData: DynamicEntityCommons = result
            (commonsData.jValue, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteDynamicEntity,
      implementedInApiVersion,
      nameOf(deleteDynamicEntity),
      "DELETE",
      "/management/dynamic_entities/DYNAMIC_ENTITY_ID",
      "Delete DynamicEntity",
      s"""Delete a DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteDynamicEntity)))

    lazy val deleteDynamicEntity: OBPEndpoint = {
      case "management" :: "dynamic_entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteDynamicEntity, callContext)
            // Check whether there are uploaded data, only if no uploaded data allow to delete DynamicEntity.
            (entity, _) <- NewStyle.function.getDynamicEntityById(dynamicEntityId, callContext)
            (isExists, _) <- NewStyle.function.invokeDynamicConnector(IS_EXISTS_DATA, entity.entityName, None, None, callContext)
            _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed) {
              isExists.isDefined && isExists.contains(JBool(false))
            }
            deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(dynamicEntityId)
          } yield {
            (deleted, HttpCode.`200`(callContext))
          }
      }
    }


    private def unboxResult[T: Manifest](box: Box[T]): T = {
       if(box.isInstanceOf[ParamFailure[_]]) {
         fullBoxOrException[T](box)
      }

      box.openOrThrowException("impossible error")
    }
    lazy val genericEndpoint: OBPEndpoint = {
      case EntityName(entityName) :: Nil JsonGet req => { cc =>
        val listName = StringHelpers.snakify(English.plural(entityName))
        for {
          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entityName, None, None, Some(cc))
          resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]])
        } yield {
          import net.liftweb.json.JsonDSL._
          val jValue: JObject = listName -> resultList
          (jValue, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(entityName, id) JsonGet req => {cc =>
        for {
          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), Some(cc))
           entity: JValue = unboxResult(box.asInstanceOf[Box[JValue]])
        } yield {
          (entity, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(entityName) :: Nil JsonPost json -> _ => {cc =>
        for {
          (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(json.asInstanceOf[JObject]), None, Some(cc))
          entity: JValue = unboxResult(box.asInstanceOf[Box[JValue]])
        } yield {
          (entity, HttpCode.`201`(Some(cc)))
        }
      }
      case EntityName(entityName, id) JsonPut json -> _ => { cc =>
        for {
          (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(UPDATE, entityName, Some(json.asInstanceOf[JObject]), Some(id), Some(cc))
          entity: JValue = unboxResult(box.asInstanceOf[Box[JValue]])
        } yield {
          (entity, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(entityName, id) JsonDelete req => { cc =>
        for {
          (box, _) <- NewStyle.function.invokeDynamicConnector(DELETE, entityName, None, Some(id), Some(cc))
          deleteResult: JBool = unboxResult(box.asInstanceOf[Box[JBool]])
        } yield {
          (deleteResult, HttpCode.`200`(Some(cc)))
        }
      }
    }



    resourceDocs += ResourceDoc(
      resetPasswordUrl,
      implementedInApiVersion,
      nameOf(resetPasswordUrl),
      "POST",
      "/management/user/reset-password-url",
      "Create password reset url",
      s"""Create password reset url.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      PostResetPasswordUrlJsonV400("jobloggs", "jo@gmail.com", "74a8ebcc-10e4-4036-bef3-9835922246bf"),
      ResetPasswordUrlJsonV400( "https://apisandbox.openbankproject.com/user_mgt/reset_password/QOL1CPNJPCZ4BRMPX3Z01DPOX1HMGU3L"),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagApi, apiTagNewStyle),
      Some(List(canCreateResetPasswordUrl)))

    lazy val resetPasswordUrl : OBPEndpoint = {
      case "management" :: "user" :: "reset-password-url" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.NotAllowedEndpoint) {
              APIUtil.getPropsAsBoolValue("ResetPasswordUrlEnabled", false)
            }
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateResetPasswordUrl, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the ${classOf[PostResetPasswordUrlJsonV400]} "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostResetPasswordUrlJsonV400]
            }
          } yield {
             val resetLink = AuthUser.passwordResetUrl(postedData.username, postedData.email, postedData.user_id) 
            (ResetPasswordUrlJsonV400(resetLink), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addAccount,
      implementedInApiVersion,
      nameOf(addAccount),
      "POST",
      "/banks/BANK_ID/accounts",
      "Create Account (POST)",
      """Create Account at bank specified by BANK_ID.
        |
        |The User can create an Account for himself  - or -  the User that has the USER_ID specified in the POST body.
        |
        |If the POST body USER_ID *is* specified, the logged in user must have the Role CanCreateAccount. Once created, the Account will be owned by the User specified by USER_ID.
        |
        |If the POST body USER_ID is *not* specified, the account will be owned by the logged in User.
        |
        |The 'product_code' field SHOULD be a product_code from Product.
        |If the product_code matches a product_code from Product, account attributes will be created that match the Product Attributes.
        |
        |Note: The Amount MUST be zero.""".stripMargin,
      createAccountRequestJsonV310,
      createAccountResponseJsonV310,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidAccountBalanceAmount,
        InvalidAccountInitialBalance,
        InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount,apiTagOnboarding),
      Some(List(canCreateAccount))
    )


    lazy val addAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonPost json -> _ => {
        cc =>{
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(createAccountRequestJsonV310))} "
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateAccountRequestJsonV310]
            }
            loggedInUserId = u.userId
            userIdAccountOwner = if (createAccountJson.user_id.nonEmpty) createAccountJson.user_id else loggedInUserId
            (postedOrLoggedInUser,callContext) <- NewStyle.function.findByUserId(userIdAccountOwner, callContext)
            _ <- Helper.booleanToFuture(s"${UserHasMissingRoles} $canCreateAccount or create account for self") {
              hasEntitlement(bankId.value, loggedInUserId, canCreateAccount) || userIdAccountOwner == loggedInUserId
            }
            initialBalanceAsString = createAccountJson.balance.amount
            //Note: here we map the product_code to account_type 
            accountType = createAccountJson.product_code
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, callContext) {
              BigDecimal(initialBalanceAsString)
            }
            _ <-  Helper.booleanToFuture(InitialBalanceMustBeZero){0 == initialBalanceAsNumber}
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode){isValidCurrencyISOCode(createAccountJson.balance.currency)}
            currency = createAccountJson.balance.currency
            (_, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            (bankAccount,callContext) <- NewStyle.function.addBankAccount(
              bankId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id,
              createAccountJson.account_routing.scheme,
              createAccountJson.account_routing.address,
              callContext
            )
            accountId = bankAccount.accountId
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, ProductCode(accountType), callContext)
            (accountAttributes, callContext) <- NewStyle.function.createAccountAttributes(
              bankId,
              accountId,
              ProductCode(accountType),
              productAttributes,
              callContext: Option[CallContext]
            )
          } yield {
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            BankAccountCreation.setAsOwner(bankId, accountId, postedOrLoggedInUser)
            (JSONFactory310.createAccountJSON(userIdAccountOwner, bankAccount, accountAttributes), HttpCode.`201`(callContext))
          }
        }
      }
    }
    
    
    
    private def getApiInfoJSON() = {
      val (apiVersion, apiVersionStatus) = (implementedInApiVersion, OBPAPI4_0_0.versionStatus)
      val organisation = APIUtil.getPropsValue("hosted_by.organisation", "TESOBE")
      val email = APIUtil.getPropsValue("hosted_by.email", "contact@tesobe.com")
      val phone = APIUtil.getPropsValue("hosted_by.phone", "+49 (0)30 8145 3994")
      val organisationWebsite = APIUtil.getPropsValue("organisation_website", "https://www.tesobe.com")
      val hostedBy = new HostedBy400(organisation, email, phone, organisationWebsite)

      val organisationHostedAt = APIUtil.getPropsValue("hosted_at.organisation", "")
      val organisationWebsiteHostedAt = APIUtil.getPropsValue("hosted_at.organisation_website", "")
      val hostedAt = new HostedAt400(organisationHostedAt, organisationWebsiteHostedAt)

      val organisationEnergySource = APIUtil.getPropsValue("energy_source.organisation", "")
      val organisationWebsiteEnergySource = APIUtil.getPropsValue("energy_source.organisation_website", "")
      val energySource = new EnergySource400(organisationEnergySource, organisationWebsiteEnergySource)

      val connector = APIUtil.getPropsValue("connector").openOrThrowException("no connector set")

      APIInfoJson400(apiVersion.vDottedApiVersion(), apiVersionStatus, gitCommit, connector, hostedBy, hostedAt, energySource)
    }


    resourceDocs += ResourceDoc(
      root,
      implementedInApiVersion,
      "root",
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Hosted at information
        |* Energy source information
        |* Git Commit""",
      emptyObjectJson,
      apiInfoJson400,
      List(UnknownError, "no connector set"),
      Catalogs(Core, notPSD2, OBWG),
      apiTagApi :: apiTagNewStyle :: Nil)

    lazy val root : OBPEndpoint = {
      case "root" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
          } yield {
            (getApiInfoJSON(), HttpCode.`200`(callContext))
          }
      }
      case Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
          } yield {
            (getApiInfoJSON(), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCallContext,
      implementedInApiVersion,
      nameOf(getCallContext),
      "GET",
      "/development/call_context",
      "Get the Call Context of a current call",
      s"""Get the Call Context of the current call.
         |
         |${authenticationRequiredMessage(true)}
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagApi, apiTagNewStyle),
      Some(List(canGetCallContext)))

    lazy val getCallContext: OBPEndpoint = {
      case "development" :: "call_context" :: Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetCallContext, callContext)
          } yield {
            (callContext, HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getEntitlements,
      implementedInApiVersion,
      "getEntitlements",
      "GET",
      "/users/USER_ID/entitlements",
      "Get Entitlements for User",
      s"""
         |
         |${authenticationRequiredMessage(true)}
         |
         |
      """.stripMargin,
      emptyObjectJson,
      entitlementJSONs,
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)))


    lazy val getEntitlements: OBPEndpoint = {
      case "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetEntitlementsForAnyUserAtAnyBank, callContext)
            entitlements <- NewStyle.function.getEntitlementsByUserId(userId, callContext)
          } yield {
            var json = EntitlementJSONs(Nil)
            // Format the data as V2.0.0 json
            if (isSuperAdmin(userId)) {
              // If the user is SuperAdmin add it to the list
              json = EntitlementJSONs(JSONFactory200.createEntitlementJSONs(entitlements).list:::List(EntitlementJSON("", "SuperAdmin", "")))
            } else {
              json = JSONFactory200.createEntitlementJSONs(entitlements)
            }
            (json, HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getEntitlementsForBank,
      implementedInApiVersion,
      nameOf(getEntitlementsForBank),
      "GET",
      "/banks/BANK_ID/entitlements",
      "Get Entitlements for One Bank",
      s"""
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      emptyObjectJson,
      entitlementJSONs,
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canGetEntitlementsForOneBank,canGetEntitlementsForAnyBank)))

    val allowedEntitlements = canGetEntitlementsForOneBank:: canGetEntitlementsForAnyBank :: Nil
    val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")

    lazy val getEntitlementsForBank: OBPEndpoint = {
      case "banks" :: bankId :: "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + allowedEntitlementsTxt)(bankId, u.userId, allowedEntitlements)
            entitlements <- NewStyle.function.getEntitlementsByBankId(bankId, callContext)
          } yield {
            val json = JSONFactory400.createEntitlementJSONs(entitlements)
            (json, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addTagForViewOnAccount,
      implementedInApiVersion,
      "addTagForViewOnAccount",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags",
      "Add a tag on account.",
      s"""Posts a tag about an account ACCOUNT_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${authenticationRequiredMessage(true)}
         |
         |Authentication is required as the tag is linked with the user.""",
      postAccountTagJSON,
      accountTagJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountMetadata, apiTagAccount))

    lazy val addTagForViewOnAccount : OBPEndpoint = {
      //add a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_add_tag. Current ViewId($viewId)") {
              view.canAddTag
            }
            tagJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostTransactionTagJSON ", 400, callContext) {
              json.extract[PostTransactionTagJSON]
            }
            (postedTag, callContext) <- Future(Tags.tags.vend.addTagOnAccount(bankId, accountId)(u.userPrimaryKey, viewId, tagJson.value, now)) map {
              i => (connectorEmptyResponse(i, callContext), callContext)
            }
          } yield {
            (JSONFactory400.createAccountTagJSON(postedTag), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteTagForViewOnAccount,
      implementedInApiVersion,
      "deleteTagForViewOnAccount",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags/TAG_ID",
      "Delete a tag on account.",
      s"""Deletes the tag TAG_ID about the account ACCOUNT_ID made on [view](#1_2_1-getViewsForBankAccount).
        |
        |${authenticationRequiredMessage(true)}
        |
        |Authentication is required as the tag is linked with the user.""",
      emptyObjectJson,
      emptyObjectJson,
      List(NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountMetadata, apiTagAccount))

    lazy val deleteTagForViewOnAccount : OBPEndpoint = {
      //delete a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_delete_tag. Current ViewId($viewId)") {
              view.canDeleteTag
            }
            deleted <- Future(Tags.tags.vend.deleteTagOnAccount(bankId, accountId)(tagId)) map {
              i => (connectorEmptyResponse(i, callContext), callContext)
            }
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getTagsForViewOnAccount,
      implementedInApiVersion,
      "getTagsForViewOnAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags",
      "Get tags on account.",
      s"""Returns the account ACCOUNT_ID tags made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
         |${authenticationRequiredMessage(true)}
         |
         |Authentication is required as the tag is linked with the user.""",
      emptyObjectJson,
      accountTagsJSON,
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountMetadata, apiTagAccount))

    lazy val getTagsForViewOnAccount : OBPEndpoint = {
      //get tags
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_tags. Current ViewId($viewId)") {
              view.canSeeTags
            }
            tags <- Future(Tags.tags.vend.getTagsOnAccount(bankId, accountId)(viewId))
          } yield {
            val json = JSONFactory400.createAccountTagsJSON(tags)
            (json, HttpCode.`200`(callContext))
          }
      }
    }




    resourceDocs += ResourceDoc(
      getCoreAccountById,
      implementedInApiVersion,
      nameOf(getCoreAccountById),
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      s"""Information returned about the account specified by ACCOUNT_ID:
         |
         |* Number - The human readable account number given by the bank that identifies the account.
         |* Label - A label given by the owner of the account
         |* Owners - Users that own this account
         |* Type - The type of account
         |* Balance - Currency and Value
         |* Account Routings - A list that might include IBAN or national account identifiers
         |* Account Rules - A list that might include Overdraft and other bank specific rules
         |* Account Attributes - A list that might include custom defined account attribute
         |* Tags - A list of Tags assigned to this account
         |
         |This call returns the owner view and requires access to that view.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV400,
      List(BankAccountNotFound,UnknownError),
      Catalogs(Core, PSD2, notOBWG),
      apiTagAccount :: apiTagPSD2AIS ::  apiTagNewStyle :: Nil)
    lazy val getCoreAccountById : OBPEndpoint = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankId, accountId), callContext) 
            moderatedAccount <- NewStyle.function.moderatedBankAccount(account, view, Full(u), callContext)
            (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(
              bankId,
              accountId,
              callContext: Option[CallContext])
            tags <- Future(Tags.tags.vend.getTagsOnAccount(bankId, accountId)(view.viewId))
          } yield {
            val availableViews: List[View] = Views.views.vend.privateViewsUserCanAccessForAccount(u, BankIdAccountId(account.bankId, account.accountId))
            (createNewCoreBankAccountJson(moderatedAccount, availableViews, accountAttributes, tags), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getPrivateAccountByIdFull,
      implementedInApiVersion,
      nameOf(getPrivateAccountByIdFull),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (Full)",
      """Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* IBAN
        |* Available views (sorted by short_name)
        |
        |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
        |
        |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
        |This call provides balance and other account information via delegated authentication using OAuth.
        |
        |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
        |""".stripMargin,
      emptyObjectJson,
      moderatedAccountJSON400,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  apiTagNewStyle :: Nil)
    lazy val getPrivateAccountByIdFull : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext) 
            moderatedAccount <- NewStyle.function.moderatedBankAccount(account, view, Full(u), callContext)
            (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(
              bankId,
              accountId,
              callContext: Option[CallContext])
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(u, BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews.map(JSONFactory.createViewJSON).sortBy(_.short_name)
            val tags = Tags.tags.vend.getTagsOnAccount(bankId, accountId)(viewId)
            (createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes, tags), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCustomersByCustomerPhoneNumber,
      implementedInApiVersion,
      nameOf(getCustomersByCustomerPhoneNumber),
      "POST",
      "/banks/BANK_ID/search/customers/mobile-phone-number",
      "Get Customers by MOBILE_PHONE_NUMBER",
      s"""Gets the Customers specified by MOBILE_PHONE_NUMBER.
         |
         |There are two wildcards often used in conjunction with the LIKE operator:
         |    % - The percent sign represents zero, one, or multiple characters
         |    _ - The underscore represents a single character
         |For example {"customer_phone_number":"%381%"} lists all numbers which contain 381 sequence
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postCustomerPhoneNumberJsonV400,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc ,apiTagNewStyle))

    lazy val getCustomersByCustomerPhoneNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "search"  :: "customers" :: "mobile-phone-number" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomer, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerPhoneNumberJsonV400 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerPhoneNumberJsonV400]
            }
            (customers, callContext) <- NewStyle.function.getCustomersByCustomerPhoneNumber(bank.bankId, postedData.mobile_phone_number , callContext)
          } yield {
            (JSONFactory300.createCustomersJson(customers), HttpCode.`201`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      createBank,
      implementedInApiVersion,
      "createBank",
      "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |${authenticationRequiredMessage(true) }
         |""",
      bankJSONV220,
      bankJSONV220,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $BankJSONV220 "
            bank <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[BankJSONV220]
            }
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials) {
              callContext.map(_.consumer.isDefined == true).isDefined == true
            }
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateBank, callContext)
            (success, callContext) <- NewStyle.function.createOrUpdateBank(
              bank.id,
              bank.full_name,
              bank.short_name,
              bank.logo_url,
              bank.website_url,
              bank.swift_bic,
              bank.national_identifier,
              bank.bank_routing.scheme,
              bank.bank_routing.address,
              callContext
              )
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
            _ <- entitlements.filter(_.roleName == CanCreateEntitlementAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(bank.id, u.userId, CanCreateEntitlementAtOneBank.toString()))
            }
          } yield {
            (JSONFactory220.createBankJSON(success), HttpCode.`201`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      createDirectDebit,
      implementedInApiVersion,
      nameOf(createDirectDebit),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/direct-debit",
      "Create Direct Debit",
      s"""Create direct debit for an account.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postDirectDebitJsonV400,
      directDebitJsonV400,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        CounterpartyNotFoundByCounterpartyId,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDirectDebit, apiTagAccount, apiTagNewStyle))

    lazy val createDirectDebit : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "direct-debit" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_create_direct_debit. Current ViewId($viewId)") {
              view.canCreateDirectDebit == true
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostDirectDebitJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostDirectDebitJsonV400]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)
            _ <- Users.users.vend.getUserByUserIdFuture(postJson.user_id) map {
              x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId(${postJson.user_id})")
            }
            (_, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(postJson.counterparty_id), callContext)
            (directDebit, callContext) <- NewStyle.function.createDirectDebit(
              bankId.value,
              accountId.value, 
              postJson.customer_id, 
              postJson.user_id, 
              postJson.counterparty_id,
              if (postJson.date_signed.isDefined) postJson.date_signed.get else new Date(),
              postJson.date_starts,
              postJson.date_expires,
              callContext)
          } yield {
            (JSONFactory400.createDirectDebitJSON(directDebit), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createDirectDebitManagement,
      implementedInApiVersion,
      nameOf(createDirectDebitManagement),
      "POST",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/direct-debit",
      "Create Direct Debit (management)",
      s"""Create direct debit for an account.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postDirectDebitJsonV400,
      directDebitJsonV400,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        CounterpartyNotFoundByCounterpartyId,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDirectDebit, apiTagAccount, apiTagNewStyle),
      Some(List(canCreateDirectDebitAtOneBank))
    )

    lazy val createDirectDebitManagement : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "direct-debit" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canCreateDirectDebitAtOneBank, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostDirectDebitJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostDirectDebitJsonV400]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)
            _ <- Users.users.vend.getUserByUserIdFuture(postJson.user_id) map {
              x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId(${postJson.user_id})")
            }
            (_, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(postJson.counterparty_id), callContext)
            (directDebit, callContext) <- NewStyle.function.createDirectDebit(
              bankId.value,
              accountId.value,
              postJson.customer_id,
              postJson.user_id,
              postJson.counterparty_id,
              if (postJson.date_signed.isDefined) postJson.date_signed.get else new Date(),
              postJson.date_starts,
              postJson.date_expires,
              callContext)
          } yield {
            (JSONFactory400.createDirectDebitJSON(directDebit), HttpCode.`201`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      createStandingOrder,
      implementedInApiVersion,
      nameOf(createStandingOrder),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/standing-order",
      "Create Standing Order",
      s"""Create standing order for an account.
         |
         |when -> frequency = {‘YEARLY’,’MONTHLY, ‘WEEKLY’, ‘BI-WEEKLY’, DAILY’}
         |when -> detail = { ‘FIRST_MONDAY’, ‘FIRST_DAY’, ‘LAST_DAY’}}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postStandingOrderJsonV400,
      standingOrderJsonV400,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        InvalidNumber,
        InvalidISOCurrencyCode,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagStandingOrder, apiTagAccount, apiTagNewStyle))

    lazy val createStandingOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "standing-order" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_create_standing_order. Current ViewId($viewId)") {
              view.canCreateStandingOrder == true
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostStandingOrderJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostStandingOrderJsonV400]
            }
            amountValue <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, callContext) {
              BigDecimal(postJson.amount.amount)
            }
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'") {
              isValidCurrencyISOCode(postJson.amount.currency)
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)
            _ <- Users.users.vend.getUserByUserIdFuture(postJson.user_id) map {
              x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId(${postJson.user_id})")
            }
            (_, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(postJson.counterparty_id), callContext)
            (directDebit, callContext) <- NewStyle.function.createStandingOrder(
              bankId.value,
              accountId.value,
              postJson.customer_id,
              postJson.user_id,
              postJson.counterparty_id,
              amountValue,
              postJson.amount.currency,
              postJson.when.frequency,
              postJson.when.detail,
              if (postJson.date_signed.isDefined) postJson.date_signed.get else new Date(),
              postJson.date_starts,
              postJson.date_expires,
              callContext)
          } yield {
            (JSONFactory400.createStandingOrderJSON(directDebit), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createStandingOrderManagement,
      implementedInApiVersion,
      nameOf(createStandingOrderManagement),
      "POST",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/standing-order",
      "Create Standing Order (management)",
      s"""Create standing order for an account.
         |
         |when -> frequency = {‘YEARLY’,’MONTHLY, ‘WEEKLY’, ‘BI-WEEKLY’, DAILY’}
         |when -> detail = { ‘FIRST_MONDAY’, ‘FIRST_DAY’, ‘LAST_DAY’}}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postStandingOrderJsonV400,
      standingOrderJsonV400,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        InvalidNumber,
        InvalidISOCurrencyCode,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagStandingOrder, apiTagAccount, apiTagNewStyle),
      Some(List(canCreateStandingOrderAtOneBank))
    )

    lazy val createStandingOrderManagement : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "standing-order" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canCreateStandingOrderAtOneBank, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostStandingOrderJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostStandingOrderJsonV400]
            }
            amountValue <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, callContext) {
              BigDecimal(postJson.amount.amount)
            }
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'") {
              isValidCurrencyISOCode(postJson.amount.currency)
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)
            _ <- Users.users.vend.getUserByUserIdFuture(postJson.user_id) map {
              x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId(${postJson.user_id})")
            }
            (_, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(postJson.counterparty_id), callContext)
            (directDebit, callContext) <- NewStyle.function.createStandingOrder(
              bankId.value,
              accountId.value,
              postJson.customer_id,
              postJson.user_id,
              postJson.counterparty_id,
              amountValue,
              postJson.amount.currency,
              postJson.when.frequency,
              postJson.when.detail,
              if (postJson.date_signed.isDefined) postJson.date_signed.get else new Date(),
              postJson.date_starts,
              postJson.date_expires,
              callContext)
          } yield {
            (JSONFactory400.createStandingOrderJSON(directDebit), HttpCode.`201`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      grantUserAccessToView,
      implementedInApiVersion,
      "grantUserAccessToView",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access/grant",
      "Grant User access to View.",
      s"""Grants the User identified by USER_ID access to the view identified by VIEW_ID.
         |
         |${authenticationRequiredMessage(true)} and the user needs to be account holder.
         |
         |""",
      postAccountAccessJsonV400,
      viewJsonV300,
      List(
        UserNotLoggedIn,
        UserMissOwnerViewOrNotAccountHolder,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotGrantAccountAccess,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val grantUserAccessToView : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: "grant" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(loggedInUser), callContext) <- authorizedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostAccountAccessJsonV400]
            }
            _ <- NewStyle.function.canGrantAccessToView(bankId, accountId, loggedInUser, callContext)
            (user, callContext) <- NewStyle.function.findByUserId(postJson.user_id, callContext)
            view <- postJson.view.is_system match {
              case true => NewStyle.function.systemView(ViewId(postJson.view.view_id), callContext)
              case false => NewStyle.function.customView(ViewId(postJson.view.view_id), BankIdAccountId(bankId, accountId), callContext)
            }
            addedView <- postJson.view.is_system match {
              case true => NewStyle.function.grantAccessToSystemView(bankId, accountId, view, user, callContext)
              case false => NewStyle.function.grantAccessToCustomView(view, user, callContext)
            }
          } yield {
            val viewJson = JSONFactory300.createViewJSON(addedView)
            (viewJson, HttpCode.`201`(callContext))
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      revokeUserAccessToView,
      implementedInApiVersion,
      "revokeUserAccessToView",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access/revoke",
      "Revoke User access to View.",
      s"""Revoke the User identified by USER_ID access to the view identified by VIEW_ID.
         |
         |${authenticationRequiredMessage(true)} and the user needs to be account holder.
         |
         |""",
      postAccountAccessJsonV400,
      revokedJsonV400,
      List(
        UserNotLoggedIn,
        UserMissOwnerViewOrNotAccountHolder,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotRevokeAccountAccess,
        CannotFindAccountAccess,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val revokeUserAccessToView : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: "revoke" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(loggedInUser), callContext) <- authorizedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostAccountAccessJsonV400]
            }
            _ <- NewStyle.function.canRevokeAccessToView(bankId, accountId, loggedInUser, callContext)
            (user, callContext) <- NewStyle.function.findByUserId(postJson.user_id, callContext)
            view <- postJson.view.is_system match {
              case true => NewStyle.function.systemView(ViewId(postJson.view.view_id), callContext)
              case false => NewStyle.function.customView(ViewId(postJson.view.view_id), BankIdAccountId(bankId, accountId), callContext)
            }
            revoked <- postJson.view.is_system match {
              case true => NewStyle.function.revokeAccessToSystemView(bankId, accountId, view, user, callContext)
              case false => NewStyle.function.revokeAccessToCustomView(view, user, callContext)
            }
          } yield {
            (RevokedJsonV400(revoked), HttpCode.`201`(callContext))
          }
      }
    }
    

  }

}

object APIMethods400 extends RestHelper with APIMethods400 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations4_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

