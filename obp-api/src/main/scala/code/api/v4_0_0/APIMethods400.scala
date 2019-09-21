package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{AccountNotFound, AllowedAttemptsUsedUp, BankNotFound, CounterpartyBeneficiaryPermit, InsufficientAuthorisationToCreateTransactionRequest, InvalidAccountIdFormat, InvalidBankIdFormat, InvalidChallengeAnswer, InvalidChallengeType, InvalidChargePolicy, InvalidISOCurrencyCode, InvalidJsonFormat, InvalidNumber, InvalidTransactionRequesChallengeId, InvalidTransactionRequestCurrency, InvalidTransactionRequestType, NotPositiveAmount, TransactionDisabled, TransactionRequestStatusNotInitiated, TransactionRequestTypeHasChanged, UnknownError, UserHasMissingRoles, UserNoPermissionAccessView, UserNotLoggedIn, ViewNotFound}
import code.api.util.ExampleValue.{dynamicEntityRequestBodyExample, dynamicEntityResponseBodyExample}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, TransactionRequestAccountJsonV140}
import code.api.v2_1_0._
import code.api.v3_1_0.ListResult
import code.api.{APIFailureNewStyle, ChargePolicy}
import code.dynamicEntity.DynamicEntityCommons
import code.model.dataAccess.AuthUser
import code.model.toUserExtended
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _, _}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import net.liftweb.common.{Box, Full, ParamFailure}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.Serialization.write
import net.liftweb.json._
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
            _ <- NewStyle.function.view(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), callContext)

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
            _ <- NewStyle.function.view(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), callContext)

            _ <- Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionRequest) {
              u.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)) == true ||
                hasEntitlement(fromAccount.bankId.value, u.userId, ApiRole.canCreateAnyTransactionRequest) == true
            }

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
            deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(dynamicEntityId)
          } yield {
            (deleted, HttpCode.`200`(callContext))
          }
      }
    }


    private def unboxResult[T](box: Box[T]): T = {
       if(box.isInstanceOf[ParamFailure[APIFailureNewStyle]]) {
         fullBoxOrException[Any](box)
      }

      box.openOrThrowException("impossible error")
    }
    lazy val genericEndpoint: OBPEndpoint = {
      case EntityName(entityName) :: Nil JsonGet req => { cc =>
        val listName = StringHelpers.snakify(English.plural(entityName))
        for {
          (box: Box[JArray], _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entityName, None, None, Some(cc))
          resultList = unboxResult(box)
        } yield {
          import net.liftweb.json.JsonDSL._
          val jValue: JObject = listName -> resultList
          (jValue, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(entityName, id) JsonGet req => {cc =>
        for {
          (box: Box[JObject], _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), Some(cc))
           entity = unboxResult(box)
        } yield {
          (entity, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(entityName) :: Nil JsonPost json -> _ => {cc =>
        for {
          (box: Box[JObject], _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(json.asInstanceOf[JObject]), None, Some(cc))
          entity = unboxResult(box)
        } yield {
          (entity, HttpCode.`201`(Some(cc)))
        }
      }
      case EntityName(entityName, id) JsonPut json -> _ => { cc =>
        for {
          (box: Box[JObject], _) <- NewStyle.function.invokeDynamicConnector(UPDATE, entityName, Some(json.asInstanceOf[JObject]), Some(id), Some(cc))
          entity = unboxResult(box)
        } yield {
          (entity, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(entityName, id) JsonDelete req => { cc =>
        for {
          (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(DELETE, entityName, None, Some(id), Some(cc))
          deleteResult = unboxResult(box)
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
      apiTagApi :: Nil)

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
    

  }

}

object APIMethods400 extends RestHelper with APIMethods400 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations4_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

