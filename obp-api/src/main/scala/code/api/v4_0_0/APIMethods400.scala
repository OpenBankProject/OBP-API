package code.api.v4_0_0

import code.DynamicData.{DynamicData, DynamicDataProvider}
import code.DynamicEndpoint.DynamicEndpointSwagger
import code.accountattribute.AccountAttributeX
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{fullBoxOrException, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.util.migration.Migration
import code.api.util.newstyle.AttributeDefinition._
import code.api.util.newstyle.Consumer._
import code.api.util.newstyle.UserCustomerLinkNewStyle
import code.api.v1_2_1.{JSONFactory, PostTransactionTagJSON}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200}
import code.api.v2_1_0._
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0._
import code.api.v4_0_0.JSONFactory400._
import code.api.v4_0_0.dynamic.DynamicEndpointHelper.DynamicReq
import code.api.v4_0_0.dynamic.practise.{DynamicEndpointCodeGenerator, PractiseEndpoint}
import code.api.v4_0_0.dynamic._
import code.api.{ChargePolicy, JsonResponseException}
import code.apicollection.MappedApiCollectionsProvider
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.authtypevalidation.JsonAuthTypeValidation
import code.bankconnectors.{Connector, DynamicConnector, InternalConnector}
import code.connectormethod.{JsonConnectorMethod, JsonConnectorMethodMethodBody}
import code.consent.{ConsentStatus, Consents}
import code.dynamicEntity.{DynamicEntityCommons, ReferenceType}
import code.dynamicMessageDoc.JsonDynamicMessageDoc
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import code.endpointMapping.EndpointMappingCommons
import code.entitlement.Entitlement
import code.metadata.counterparties.{Counterparties, MappedCounterparty}
import code.metadata.tags.Tags
import code.model.dataAccess.{AuthUser, BankAccountCreation, ResourceUser}
import code.model.{toUserExtended, _}
import code.ratelimiting.RateLimitingDI
import code.snippet.{WebUIPlaceholder, WebUITemplate}
import code.transactionChallenge.MappedExpectedChallengeAnswer
import code.transactionrequests.MappedTransactionRequestProvider
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _, _}
import code.userlocks.UserLocksProvider
import code.users.{UserAgreement, Users}
import code.util.Helper.booleanToFuture
import code.util.{Helper, JsonSchemaUtil}
import code.validation.JsonValidation
import code.views.Views
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums.{TransactionRequestStatus, _}
import com.openbankproject.commons.model.{ListResult, _}
import com.openbankproject.commons.util.{ApiVersion, JsonUtils, ScannedApiVersion}
import deletion.{DeleteAccountCascade, DeleteProductCascade, DeleteTransactionCascade}
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req, S}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.write
import net.liftweb.json.{compactRender, prettyRender, _}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.now
import net.liftweb.util.Mailer.{From, PlainMailBodyType, Subject, To, XHTMLMailBodyType}
import net.liftweb.util.{Helpers, Mailer, StringHelpers}
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
import scala.math.BigDecimal
import scala.xml.XML

trait APIMethods400 {
  self: RestHelper =>

  val Implementations4_0_0 = new Implementations400()

  class Implementations400 {

    val implementedInApiVersion = ApiVersion.v4_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    // createDynamicEntityDoc and updateDynamicEntityDoc are dynamic, So here dynamic create resourceDocs
    def resourceDocs = staticResourceDocs ++ ArrayBuffer[ResourceDoc](createDynamicEntityDoc,
      createBankLevelDynamicEntityDoc, updateDynamicEntityDoc, updateBankLevelDynamicEntityDoc, updateMyDynamicEntityDoc)

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


    staticResourceDocs += ResourceDoc(
      getMapperDatabaseInfo,
      implementedInApiVersion,
      nameOf(getMapperDatabaseInfo),
      "GET",
      "/database/info",
      "Get Mapper Database Info",
      s"""Get basic information about the Mapper Database.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      adapterInfoJsonV300,
      List($UserNotLoggedIn, UnknownError),
      List(apiTagApi, apiTagNewStyle),
      Some(List(canGetDatabaseInfo)))


    lazy val getMapperDatabaseInfo: OBPEndpoint = {
      case "database" :: "info" :: Nil JsonGet _ => {
        cc => Future {
          (Migration.DbFunction.mapperDatabaseInfo(), HttpCode.`200`(cc.callContext))
        }
      }
    }


    staticResourceDocs += ResourceDoc(
      getLogoutLink,
      implementedInApiVersion,
      nameOf(getLogoutLink), // TODO can we get this string from the val two lines above?
      "GET",
      "/users/current/logout-link",
      "Get Logout Link",
      s"""Get the Logout Link
         |
         |${authenticationRequiredMessage(true)}
      """.stripMargin,
      EmptyBody,
      logoutLinkV400,
      List($UserNotLoggedIn, UnknownError),
      List(apiTagUser, apiTagNewStyle))

    lazy val getLogoutLink: OBPEndpoint = {
      case "users" :: "current" :: "logout-link" :: Nil JsonGet _ => {
        cc => Future {
            val link = code.api.Constant.HostName + AuthUser.logoutPath.foldLeft("")(_ + "/" + _)
            val logoutLink = LogoutLinkJson(link)
            (logoutLink, HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      callsLimit,
      implementedInApiVersion,
      nameOf(callsLimit),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Set Calls Limit for a Consumer",
      s"""
         |Set the API call limits for a Consumer:
         |
         |Per Second
         |Per Minute
         |Per Hour
         |Per Week
         |Per Month
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      callLimitPostJsonV400,
      callLimitPostJsonV400,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UpdateConsumerError,
        UnknownError
      ),
      List(apiTagConsumer, apiTagNewStyle),
      Some(List(canSetCallLimits)))

    lazy val callsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canSetCallLimits, callContext)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJsonV400 ", 400, callContext) {
              json.extract[CallLimitPostJsonV400]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.createOrUpdateConsumerCallLimits(
              consumerId,
              postJson.from_date,
              postJson.to_date,
              postJson.api_version,
              postJson.api_name,
              postJson.bank_id,
              Some(postJson.per_second_call_limit),
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)) map {
              unboxFullOrFail(_, callContext, UpdateConsumerError)
            }
          } yield {
            (createCallsLimitJson(rateLimiting), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
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
      EmptyBody,
      banksJSON400,
      List(UnknownError),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
    )

    lazy val getBanks: OBPEndpoint = {
      case "banks" :: Nil JsonGet _ => {
        cc =>
          for {
            (banks, callContext) <- NewStyle.function.getBanks(cc.callContext)
          } yield {
            (JSONFactory400.createBanksJson(banks), HttpCode.`200`(callContext))
          }

      }
    }


    staticResourceDocs += ResourceDoc(
      getBank,
      implementedInApiVersion,
      nameOf(getBank),
      "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID
        |Returns information about a single bank specified by BANK_ID including:
        |
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      bankJson400,
      List(UnknownError, BankNotFound),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
    )

    lazy val getBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: Nil JsonGet _ => {
        cc =>
          for {
            (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
            (attributes, callContext) <- NewStyle.function.getBankAttributesByBank(bankId, callContext)
          } yield
            (JSONFactory400.createBankJSON400(bank, attributes), HttpCode.`200`(callContext))
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      ibanChecker,
      implementedInApiVersion,
      nameOf(ibanChecker),
      "POST",
      "/account/check/scheme/iban",
      "Validate and check IBAN number",
      """Validate and check IBAN number for errors
        |
        |""",
      ibanCheckerPostJsonV400,
      ibanCheckerJsonV400,
      List(UnknownError),
      apiTagAccount :: apiTagNewStyle :: Nil
    )

    lazy val ibanChecker: OBPEndpoint = {
      case "account" :: "check" :: "scheme" :: "iban" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(ibanCheckerPostJsonV400))}"
          for {
            ibanJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[IbanAddress]
            }
            (ibanChecker, callContext) <- NewStyle.function.validateAndCheckIbanNumber(ibanJson.address, cc.callContext)
          } yield {
            (JSONFactory400.createIbanCheckerJson(ibanChecker), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getDoubleEntryTransaction,
      implementedInApiVersion,
      nameOf(getDoubleEntryTransaction),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/double-entry-transaction",
      "Get Double Entry Transaction",
      s"""Get Double Entry Transaction
         |
         |This endpoint can be used to see the double entry transactions. It returns the `bank_id`, `account_id` and `transaction_id`
         |for the debit end the credit transaction. The other side account can be a settlement account or an OBP account.
         |
         |The endpoint also provide the `transaction_request` object which contains the `bank_id`, `account_id` and
         |`transaction_request_id` of the transaction request at the origin of the transaction. Please note that if none
         |transaction request is at the origin of the transaction, the `transaction_request` object will be `null`.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      doubleEntryTransactionJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canGetDoubleEntryTransactionAtOneBank))
    )

    lazy val getDoubleEntryTransaction : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "double-entry-transaction" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            (doubleEntryTransaction, callContext) <- NewStyle.function.getDoubleEntryBookTransaction(bankId, accountId, transactionId, callContext)
          } yield {
            (JSONFactory400.createDoubleEntryTransactionJson(doubleEntryTransaction), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createSettlementAccount,
      implementedInApiVersion,
      nameOf(createSettlementAccount),
      "POST",
      "/banks/BANK_ID/settlement-accounts",
      "Create Settlement Account",
      s"""Create a new settlement account at a bank.
         |
         |The created settlement account id will be the concatenation of the payment system and the account currency.
         |For examples: SEPA_SETTLEMENT_ACCOUNT_EUR, CARD_SETTLEMENT_ACCOUNT_USD
         |
         |By default, when you create a new bank, two settlements accounts are created automatically: OBP_DEFAULT_INCOMING_ACCOUNT_ID and OBP_DEFAULT_OUTGOING_ACCOUNT_ID
         |Those two accounts have EUR as default currency.
         |
         |If you want to create default settlement account for a specific currency, you can fill the `payment_system` field with the `DEFAULT` value.
         |
         |When a transaction is saved in OBP through the mapped connector, OBP-API look for the account to save the double-entry transaction.
         |If no OBP account can be found from the counterparty, the double-entry transaction will be saved on a bank settlement account.
         |- First, the mapped connector looks for a settlement account specific to the payment system and currency. E.g SEPA_SETTLEMENT_ACCOUNT_EUR.
         |- If we don't find any specific settlement account with the payment system, we look for a default settlement account for the counterparty currency. E.g DEFAULT_SETTLEMENT_ACCOUNT_EUR.
         |- Else, we select one of the two OBP default settlement accounts (OBP_DEFAULT_INCOMING_ACCOUNT_ID/OBP_DEFAULT_OUTGOING_ACCOUNT_ID) according to the transaction direction.
         |
         |If the POST body USER_ID *is* specified, the logged in user must have the Role CanCreateAccount. Once created, the Account will be owned by the User specified by USER_ID.
         |
         |If the POST body USER_ID is *not* specified, the account will be owned by the logged in User.
         |
         |Note: The Amount MUST be zero.
         |""".stripMargin,
      settlementAccountRequestJson,
      settlementAccountResponseJson,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        $BankNotFound,
        InvalidAccountInitialBalance,
        InitialBalanceMustBeZero,
        InvalidISOCurrencyCode,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canCreateSettlementAccountAtOneBank))
    )

    lazy val createSettlementAccount: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "settlement-accounts" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(settlementAccountRequestJson))}"
          for {
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[SettlementAccountRequestJson]
            }
            loggedInUserId = cc.userId
            userIdAccountOwner = if (createAccountJson.user_id.nonEmpty) createAccountJson.user_id else loggedInUserId
            (postedOrLoggedInUser,callContext) <- NewStyle.function.findByUserId(userIdAccountOwner, cc.callContext)

            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(Unit))
                 else NewStyle.function.hasEntitlement(bankId.value, loggedInUserId, canCreateSettlementAccountAtOneBank, callContext)

            initialBalanceAsString = createAccountJson.balance.amount
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, callContext) {
              BigDecimal(initialBalanceAsString)
            }
            _ <-  Helper.booleanToFuture(InitialBalanceMustBeZero, cc=callContext){0 == initialBalanceAsNumber}
            currency = createAccountJson.balance.currency
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode, cc=callContext){isValidCurrencyISOCode(currency)}

            (_, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme", cc=callContext) {
              createAccountJson.account_routings.map(_.scheme).distinct.size == createAccountJson.account_routings.size
            }
            alreadyExistAccountRoutings <- Future.sequence(createAccountJson.account_routings.map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bankId), accountRouting.scheme, accountRouting.address, callContext).map(_ => Some(accountRouting)).fallbackTo(Future.successful(None))
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(accountRouting) => s"bankId: $bankId, scheme: ${accountRouting.scheme}, address: ${accountRouting.address}"
            }
            _ <- Helper.booleanToFuture(s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})", cc=callContext) {
              alreadyExistingAccountRouting.isEmpty
            }
            _ <- Helper.booleanToFuture(s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme", cc=callContext) {
              createAccountJson.account_routings.map(_.scheme).distinct.size == createAccountJson.account_routings.size
            }
            _ <- Helper.booleanToFuture(s"$InvalidPaymentSystemName Space characters are not allowed.", cc=callContext) {
              !createAccountJson.payment_system.contains(" ")
            }
            accountId = AccountId(createAccountJson.payment_system.toUpperCase + "_SETTLEMENT_ACCOUNT_" + currency.toUpperCase)
            (bankAccount,callContext) <- NewStyle.function.createBankAccount(
              bankId,
              accountId,
              "SETTLEMENT",
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id,
              createAccountJson.account_routings.map(r => AccountRouting(r.scheme, r.address)),
              callContext
            )
            accountId = bankAccount.accountId
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, ProductCode("SETTLEMENT"), callContext)
            (accountAttributes, callContext) <- NewStyle.function.createAccountAttributes(
              bankId,
              accountId,
              ProductCode("SETTLEMENT"),
              productAttributes,
              callContext: Option[CallContext]
            )
          } yield {
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            BankAccountCreation.setAsOwner(bankId, accountId, postedOrLoggedInUser)
            (JSONFactory400.createSettlementAccountJson(userIdAccountOwner, bankAccount, accountAttributes), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getSettlementAccounts,
      implementedInApiVersion,
      nameOf(getSettlementAccounts),
      "GET",
      "/banks/BANK_ID/settlement-accounts",
      "Get Settlement accounts at Bank",
      """Get settlement accounts on this API instance
        |Returns a list of settlement accounts at this Bank
        |
        |Note: a settlement account is considered as a bank account.
        |So you can update it and add account attributes to it using the regular account endpoints
        |""",
      EmptyBody,
      settlementAccountsJson,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagBank, apiTagPsd2, apiTagNewStyle),
      Some(List(canGetSettlementAccountAtOneBank))
    )

    lazy val getSettlementAccounts: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "settlement-accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankId.value, cc.userId, canGetSettlementAccountAtOneBank, cc.callContext)

            (accounts, callContext) <- NewStyle.function.getBankSettlementAccounts(bankId, cc.callContext)
            settlementAccounts <- Future.sequence(accounts.map(account => {
              NewStyle.function.getAccountAttributesByAccount(bankId, account.accountId, callContext).map(accountAttributes =>
                JSONFactory400.getSettlementAccountJson(account, accountAttributes._1)
              )
            }))
          } yield {
            (SettlementAccountsJson(settlementAccounts), HttpCode.`200`(callContext))
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
         |A `Transaction Request` can have one of several states: INITIATED, NEXT_CHALLENGE_PENDING etc.
         |
         |`Transactions` are modeled on items in a bank statement that represent the movement of money.
         |
         |`Transaction Requests` are requests to move money which may or may not succeed and thus result in a `Transaction`.
         |
         |A `Transaction Request` might create a security challenge that needs to be answered before the `Transaction Request` proceeds.
         |In case 1 person needs to answer security challenge we have next flow of state of an `transaction request`:
         |  INITIATED => COMPLETED
         |In case n persons needs to answer security challenge we have next flow of state of an `transaction request`:
         |  INITIATED => NEXT_CHALLENGE_PENDING => ... => NEXT_CHALLENGE_PENDING => COMPLETED
         |
         |The security challenge is bound to a user i.e. in case of right answer and the user is different than expected one the challenge will fail.
         |
         |Rule for calculating number of security challenges:
         |If product Account attribute REQUIRED_CHALLENGE_ANSWERS=N then create N challenges
         |(one for every user that has a View where permission "can_add_transaction_request_to_any_account"=true)
         |In case REQUIRED_CHALLENGE_ANSWERS is not defined as an account attribute default value is 1.
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
         |"""


    // ACCOUNT. (we no longer create a resource doc for the general case)
    staticResourceDocs += ResourceDoc(
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
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        AccountNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))

    // ACCOUNT_OTP. (we no longer create a resource doc for the general case)
    staticResourceDocs += ResourceDoc(
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
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        AccountNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))

    // COUNTERPARTY
    staticResourceDocs += ResourceDoc(
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
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        AccountNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))


    val lowAmount = AmountOfMoneyJsonV121("EUR", "12.50")
    val sharedChargePolicy = ChargePolicy.withName("SHARED")

    // Transaction Request (SEPA)
    staticResourceDocs += ResourceDoc(
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
      transactionRequestBodySEPAJsonV400,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        AccountNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))

    staticResourceDocs += ResourceDoc(
      createTransactionRequestRefund,
      implementedInApiVersion,
      nameOf(createTransactionRequestRefund),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/REFUND/transaction-requests",
      "Create Transaction Request (REFUND)",
      s"""
         |
         |Either the `from` or the `to` field must be filled. Those fields refers to the information about the party that will be refunded.
         |
         |In case the `from` object is used, it means that the refund comes from the part that sent you a transaction.
         |In the `from` object, you have two choices :
         |- Use `bank_id` and `account_id` fields if the other account is registered on the OBP-API
         |- Use the `counterparty_id` field in case the counterparty account is out of the OBP-API
         |
         |In case the `to` object is used, it means you send a request to a counterparty to ask for a refund on a previous transaction you sent.
         |(This case is not managed by the OBP-API and require an external adapter)
         |
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyRefundJsonV400,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        AccountNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))

    // FREE_FORM.
    staticResourceDocs += ResourceDoc(
      createTransactionRequestFreeForm,
      implementedInApiVersion,
      "createTransactionRequestFreeForm",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests",
      "Create Transaction Request (FREE_FORM)",
      s"""$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyFreeFormJSON,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        AccountNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle),
      Some(List(canCreateAnyTransactionRequest)))


    // Different Transaction Request approaches:
    lazy val createTransactionRequestAccount = createTransactionRequest
    lazy val createTransactionRequestAccountOtp = createTransactionRequest
    lazy val createTransactionRequestSepa = createTransactionRequest
    lazy val createTransactionRequestCounterparty = createTransactionRequest
    lazy val createTransactionRequestRefund = createTransactionRequest
    lazy val createTransactionRequestFreeForm = createTransactionRequest

    // This handles the above cases
    lazy val createTransactionRequest: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), fromAccount, callContext) <- SS.userAccount
            _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {
              isValidID(bankId.value)
            }

            account = BankIdAccountId(bankId, accountId)
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, u, callContext)

            _ <- if (u.hasOwnerViewAccess(BankIdAccountId(bankId, accountId))) Future.successful(Full(Unit))
            else NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateAnyTransactionRequest, callContext, InsufficientAuthorisationToCreateTransactionRequest)

            _ <- Helper.booleanToFuture(s"${InvalidTransactionRequestType}: '${transactionRequestType.value}'", cc=callContext) {
              APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value)
            }

            // Check the input JSON format, here is just check the common parts of all four types
            transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $TransactionRequestBodyCommonJSON ", 400, callContext) {
              json.extract[TransactionRequestBodyCommonJSON]
            }

            transactionAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${transDetailsJson.value.amount} ", 400, callContext) {
              BigDecimal(transDetailsJson.value.amount)
            }

            _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${transactionAmountNumber}'", cc=callContext) {
              transactionAmountNumber > BigDecimal("0")
            }

            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc=callContext) {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }

            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc=callContext) {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }

            (createdTransactionRequest, callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
              case REFUND => {
                for {
                  transactionRequestBodyRefundJson <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $ACCOUNT json format", 400, callContext) {
                    json.extract[TransactionRequestBodyRefundJsonV400]
                  }

                  transactionId = TransactionId(transactionRequestBodyRefundJson.refund.transaction_id)

                  (fromAccount, toAccount, transaction, callContext) <- transactionRequestBodyRefundJson.to match {
                    case Some(refundRequestTo) if refundRequestTo.account_id.isDefined && refundRequestTo.bank_id.isDefined =>
                      val toBankId = BankId(refundRequestTo.bank_id.get)
                      val toAccountId = AccountId(refundRequestTo.account_id.get)
                      for {
                        (transaction, callContext) <- NewStyle.function.getTransaction(fromAccount.bankId, fromAccount.accountId, transactionId, callContext)
                        (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext)
                      } yield (fromAccount, toAccount, transaction, callContext)

                    case Some(refundRequestTo) if refundRequestTo.counterparty_id.isDefined =>
                      val toCounterpartyId = CounterpartyId(refundRequestTo.counterparty_id.get)
                      for {
                        (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(toCounterpartyId, callContext)
                        toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, isOutgoingAccount = true, callContext)
                        _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                          toCounterparty.isBeneficiary
                        }
                        (transaction, callContext) <- NewStyle.function.getTransaction(fromAccount.bankId, fromAccount.accountId, transactionId, callContext)
                      } yield (fromAccount, toAccount, transaction, callContext)

                    case None if transactionRequestBodyRefundJson.from.isDefined =>
                      val fromCounterpartyId = CounterpartyId(transactionRequestBodyRefundJson.from.get.counterparty_id)
                      val toAccount = fromAccount
                      for {
                        (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(fromCounterpartyId, callContext)
                        fromAccount <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, isOutgoingAccount = false, callContext)
                        _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                          fromCounterparty.isBeneficiary
                        }
                        (transaction, callContext) <- NewStyle.function.getTransaction(toAccount.bankId, toAccount.accountId, transactionId, callContext)
                      } yield (fromAccount, toAccount, transaction, callContext)
                  }

                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodyRefundJson)(Serialization.formats(NoTypeHints))
                  }

                  _ <- Helper.booleanToFuture(s"${RefundedTransaction} Current input amount is: '${transDetailsJson.value.amount}'. It can not be more than the original amount(${(transaction.amount).abs})", cc=callContext) {
                    (transaction.amount).abs  >= transactionAmountNumber
                  }
                  //TODO, we need additional field to guarantee the transaction is refunded...
//                  _ <- Helper.booleanToFuture(s"${RefundedTransaction}") {
//                    !((transaction.description.toString contains(" Refund to ")) && (transaction.description.toString contains(" and transaction_id(")))
//                  }

                  //we add the extra info (counterparty name + transaction_id) for this special Refund endpoint.
                  newDescription = s"${transactionRequestBodyRefundJson.description} - Refund for transaction_id: (${transactionId.value}) to ${transaction.otherAccount.counterpartyName}"

                  //This is the refund endpoint, the original fromAccount is the `toAccount` which will receive money.
                  refundToAccount = fromAccount
                  //This is the refund endpoint, the original toAccount is the `fromAccount` which will lose money.
                  refundFromAccount = toAccount

                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
                    viewId,
                    refundFromAccount,
                    refundToAccount,
                    transactionRequestType,
                    transactionRequestBodyRefundJson.copy(description = newDescription),
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    None,
                    None,
                    callContext) //in ACCOUNT, ChargePolicy set default "SHARED"

                  _ <- NewStyle.function.createOrUpdateTransactionRequestAttribute(
                    bankId = bankId,
                    transactionRequestId = createdTransactionRequest.id,
                    transactionRequestAttributeId = None,
                    name = "original_transaction_id",
                    attributeType = TransactionRequestAttributeType.withName("STRING"),
                    value = transactionId.value,
                    callContext = callContext
                  )

                  refundReasonCode = transactionRequestBodyRefundJson.refund.reason_code
                  _ <- if (refundReasonCode.nonEmpty) {
                    NewStyle.function.createOrUpdateTransactionRequestAttribute(
                      bankId = bankId,
                      transactionRequestId = createdTransactionRequest.id,
                      transactionRequestAttributeId = None,
                      name = "refund_reason_code",
                      attributeType = TransactionRequestAttributeType.withName("STRING"),
                      value = refundReasonCode,
                      callContext = callContext)
                  } else Future.successful()

                  (newTransactionRequestStatus, callContext) <- NewStyle.function.notifyTransactionRequest(refundFromAccount, refundToAccount, createdTransactionRequest, callContext)
                  _ <- Future(Connector.connector.vend.saveTransactionRequestStatusImpl(createdTransactionRequest.id, newTransactionRequestStatus.toString))
                  createdTransactionRequest <- Future(createdTransactionRequest.copy(status = newTransactionRequestStatus.toString))

                } yield (createdTransactionRequest, callContext)
              }
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

                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transactionRequestBodySandboxTan,
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    None,
                    None,
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

                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transactionRequestBodySandboxTan,
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_WEB_FORM.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    None,
                    None,
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
                  toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                  // Check we can send money to it.
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                    toCounterparty.isBeneficiary
                  }
                  chargePolicy = transactionRequestBodyCounterparty.charge_policy
                  _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
                    ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
                  }
                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodyCounterparty)(Serialization.formats(NoTypeHints))
                  }
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transactionRequestBodyCounterparty,
                    transDetailsSerialized,
                    chargePolicy,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    None,
                    None,
                    callContext)
                } yield (createdTransactionRequest, callContext)

              }
              case SEPA => {
                for {
                  //For SEPA, Use the iban to find the toCounterparty and set up the toAccount
                  transDetailsSEPAJson <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $SEPA json format", 400, callContext) {
                    json.extract[TransactionRequestBodySEPAJsonV400]
                  }
                  toIban = transDetailsSEPAJson.to.iban
                  (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(toIban, fromAccount.bankId, fromAccount.accountId, callContext)
                  toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                    toCounterparty.isBeneficiary
                  }
                  chargePolicy = transDetailsSEPAJson.charge_policy
                  _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
                    ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
                  }
                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transDetailsSEPAJson)(Serialization.formats(NoTypeHints))
                  }
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
                    viewId,
                    fromAccount,
                    toAccount,
                    transactionRequestType,
                    transDetailsSEPAJson,
                    transDetailsSerialized,
                    chargePolicy,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    transDetailsSEPAJson.reasons.map(_.map(_.transform)),
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
                  transactionRequestAccountJSON = TransactionRequestAccountJsonV140(bankId.value, accountId.value)
                  transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
                    write(transactionRequestBodyFreeForm)(Serialization.formats(NoTypeHints))
                  }
                  (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
                    viewId,
                    fromAccount,
                    fromAccount,
                    transactionRequestType,
                    transactionRequestBodyFreeForm,
                    transDetailsSerialized,
                    sharedChargePolicy.toString,
                    Some(OTP_VIA_API.toString),
                    getScaMethodAtInstance(transactionRequestType.value).toOption,
                    None,
                    None,
                    callContext)
                } yield
                  (createdTransactionRequest, callContext)
              }
            }
          } yield {
            //TODO, remove this `connector` guard logic, the challenges should come from other places.
            // The OBP mapped V400 payment.challenges are not done yet, the we should use `createChallengesC2` instead of `createChallenges` in createTransactionRequestv400 method,
            // and get the challenges from connector level, not prepare them here.
            val challenges : List[ChallengeJson] = if(APIUtil.getPropsValue("connector").openOrThrowException(attemptedToOpenAnEmptyBox).toString.equalsIgnoreCase("mapped")){
               MappedExpectedChallengeAnswer
                .findAll(By(MappedExpectedChallengeAnswer.mTransactionRequestId, createdTransactionRequest.id.value))
                .map(mappedExpectedChallengeAnswer => 
                  ChallengeJson(mappedExpectedChallengeAnswer.challengeId,mappedExpectedChallengeAnswer.transactionRequestId,mappedExpectedChallengeAnswer.expectedUserId) )
            } else {
              if(!("COMPLETED").equals(createdTransactionRequest.status)) 
                List(ChallengeJson(createdTransactionRequest.challenge.id, createdTransactionRequest.id.value, u.userId))
              else 
                null
            }
            (JSONFactory400.createTransactionRequestWithChargeJSON(createdTransactionRequest, challenges), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      answerTransactionRequestChallenge,
      implementedInApiVersion,
      "answerTransactionRequestChallenge",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge",
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
        |4) `answer` : must be `123` in case that Strong Customer Authentication method for OTP challenge is dummy.
        |    For instance: SANDBOX_TAN_OTP_INSTRUCTION_TRANSPORT=dummy
        |    Possible values are dummy,email and sms
        |    In kafka mode, the answer can be got by phone message or other SCA methods.
        |
        |Note that each Transaction Request Type can have its own OTP_INSTRUCTION_TRANSPORT method.
        |OTP_INSTRUCTION_TRANSPORT methods are set in Props. See sample.props.template for instructions.
        |
        |Single or Multiple authorisations
        |
        |OBP allows single or multi party authorisations.
        |
        |Single party authorisation:
        |
        |In the case that only one person needs to authorise i.e. answer a security challenge we have the following change of state of a `transaction request`:
        |  INITIATED => COMPLETED
        |
        |
        |Multiparty authorisation:
        |
        |In the case that multiple parties (n persons) need to authorise a transaction request i.e. answer security challenges, we have the followings state flow for a `transaction request`:
        |  INITIATED => NEXT_CHALLENGE_PENDING => ... => NEXT_CHALLENGE_PENDING => COMPLETED
        |
        |The security challenge is bound to a user i.e. in the case of a correct answer but the user is different than expected the challenge will fail.
        |
        |Rule for calculating number of security challenges:
        |If Product Account attribute REQUIRED_CHALLENGE_ANSWERS=N then create N challenges
        |(one for every user that has a View where permission "can_add_transaction_request_to_any_account"=true)
        |In the case REQUIRED_CHALLENGE_ANSWERS is not defined as an account attribute, the default number of security challenges created is one.
        |
      """.stripMargin,
      challengeAnswerJson400,
      transactionRequestWithChargeJSON210,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        $BankNotFound,
        $BankAccountNotFound,
        TransactionRequestStatusNotInitiated,
        TransactionRequestTypeHasChanged,
        AllowedAttemptsUsedUp,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))

    lazy val answerTransactionRequestChallenge: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), fromAccount, callContext) <- SS.userAccount
            _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {
              isValidID(bankId.value)
            }
            challengeAnswerJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ChallengeAnswerJson400", 400, callContext) {
              json.extract[ChallengeAnswerJson400]
            }

            account = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, u, callContext)

            // Check transReqId is valid
            (existingTransactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transReqId, callContext)

            // Check the Transaction Request is still INITIATED or NEXT_CHALLENGE_PENDING or FORWARDED
            _ <- Helper.booleanToFuture(TransactionRequestStatusNotInitiatedOrPendingOrForwarded, cc=callContext) {
              existingTransactionRequest.status.equals(TransactionRequestStatus.INITIATED.toString) ||
              existingTransactionRequest.status.equals(TransactionRequestStatus.NEXT_CHALLENGE_PENDING.toString) ||
              existingTransactionRequest.status.equals(TransactionRequestStatus.FORWARDED.toString)
            }

            // Check the input transactionRequestType is the same as when the user created the TransactionRequest
            existingTransactionRequestType = existingTransactionRequest.`type`
            _ <- Helper.booleanToFuture(s"${TransactionRequestTypeHasChanged} It should be :'$existingTransactionRequestType', but current value (${transactionRequestType.value}) ", cc=callContext) {
              existingTransactionRequestType.equals(transactionRequestType.value)
            }
            
            //Check the allowed attempts, Note: not supported yet, the default value is 3
            _ <- Helper.booleanToFuture(s"${AllowedAttemptsUsedUp}", cc=callContext) {
              existingTransactionRequest.challenge.allowed_attempts > 0
            }

            //Check the challenge type, Note: not supported yet, the default value is SANDBOX_TAN
            _ <- Helper.booleanToFuture(s"${InvalidChallengeType} ", cc=callContext) {
              List(
                OTP_VIA_API.toString,
                OTP_VIA_WEB_FORM.toString
              ).exists(_ == existingTransactionRequest.challenge.challenge_type)
            }

            (transactionRequest, callContext) <- challengeAnswerJson.answer match {
              // If the challenge answer is `REJECT` - Currently only to Reject a SEPA transaction request REFUND
              case "REJECT" =>
                val transactionRequest = existingTransactionRequest.copy(status = TransactionRequestStatus.REJECTED.toString)
                for {
                  (fromAccount, toAccount, callContext) <- {
                    // If the transaction request comes from the account to debit
                    if (fromAccount.accountId.value == transactionRequest.from.account_id) {
                      val toCounterpartyIban = transactionRequest.other_account_routing_address
                      for {
                        (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(toCounterpartyIban, fromAccount.bankId, fromAccount.accountId, callContext)
                        toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                      } yield (fromAccount, toAccount, callContext)
                    } else {
                      // Else, the transaction request debit a counterparty (Iban)
                      val fromCounterpartyIban = transactionRequest.from.account_id
                      // and the creditor is the obp account owner
                      val toAccount = fromAccount
                      for {
                        (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(fromCounterpartyIban, toAccount.bankId, toAccount.accountId, callContext)
                        fromAccount <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, false, callContext)
                      } yield (fromAccount, toAccount, callContext)
                    }
                  }
                  rejectReasonCode = challengeAnswerJson.reason_code.getOrElse("")
                  _ <- if (rejectReasonCode.nonEmpty) {
                    NewStyle.function.createOrUpdateTransactionRequestAttribute(
                      bankId = bankId,
                      transactionRequestId = transactionRequest.id,
                      transactionRequestAttributeId = None,
                      name = "reject_reason_code",
                      attributeType = TransactionRequestAttributeType.withName("STRING"),
                      value = rejectReasonCode,
                      callContext = callContext)
                  } else Future.successful()
                  rejectAdditionalInformation = challengeAnswerJson.additional_information.getOrElse("")
                  _ <- if (rejectAdditionalInformation.nonEmpty) {
                    NewStyle.function.createOrUpdateTransactionRequestAttribute(
                      bankId = bankId,
                      transactionRequestId = transactionRequest.id,
                      transactionRequestAttributeId = None,
                      name = "reject_additional_information",
                      attributeType = TransactionRequestAttributeType.withName("STRING"),
                      value = rejectAdditionalInformation,
                      callContext = callContext)
                  } else Future.successful()
                  _ <- NewStyle.function.notifyTransactionRequest(fromAccount, toAccount, transactionRequest, callContext)
                  _ <- Future(Connector.connector.vend.saveTransactionRequestStatusImpl(transactionRequest.id, transactionRequest.status))
                } yield (transactionRequest, callContext)
              case _ =>
                for {
                  // Check the challengeId is valid for this existingTransactionRequest
                  _ <- Helper.booleanToFuture(s"${InvalidTransactionRequestChallengeId}", cc=callContext) {
                    if (APIUtil.isDataFromOBPSide("validateChallengeAnswer")) {
                      MappedExpectedChallengeAnswer
                        .findAll(By(MappedExpectedChallengeAnswer.mTransactionRequestId, transReqId.value))
                        .exists(_.challengeId == challengeAnswerJson.id)
                    }else{
                      existingTransactionRequest.challenge.id.equals(challengeAnswerJson.id)
                    }
                  }

                  (challengeAnswerIsValidated, callContext) <- NewStyle.function.validateChallengeAnswer(challengeAnswerJson.id, challengeAnswerJson.answer, callContext)

                  _ <- Helper.booleanToFuture(s"${InvalidChallengeAnswer} ", cc=callContext) {
                    challengeAnswerIsValidated
                  }


                  //TODO, this is a temporary solution, we only checked single challenge Id for remote connectors. here is only for the localMapped Connector logic
                  _ <- if (APIUtil.isDataFromOBPSide("validateChallengeAnswer")){
                    for{
                      accountAttributes <- Connector.connector.vend.getAccountAttributesByAccount(bankId, accountId, None)
                      _ <- Helper.booleanToFuture(s"$NextChallengePending", cc=callContext) {
                        val quorum = accountAttributes._1.toList.flatten.find(_.name == "REQUIRED_CHALLENGE_ANSWERS").map(_.value).getOrElse("1").toInt
                        MappedExpectedChallengeAnswer
                          .findAll(By(MappedExpectedChallengeAnswer.mTransactionRequestId, transReqId.value))
                          .count(_.successful == true) match {
                          case number if number >= quorum => true
                          case _ =>
                            MappedTransactionRequestProvider.saveTransactionRequestStatusImpl(transReqId, TransactionRequestStatus.NEXT_CHALLENGE_PENDING.toString)
                            false
                        }
                      }
                    } yield {
                      true
                    }
                  } else{
                    Future{true}
                  }

                  // All Good, proceed with the Transaction creation...
                  (transactionRequest, callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
                    case TRANSFER_TO_PHONE | TRANSFER_TO_ATM | TRANSFER_TO_ACCOUNT =>
                      NewStyle.function.createTransactionAfterChallengeV300(u, fromAccount, transReqId, transactionRequestType, callContext)
                    case _ =>
                      NewStyle.function.createTransactionAfterChallengeV210(fromAccount, existingTransactionRequest, callContext)
                  }
                } yield (transactionRequest, callContext)
            }
          } yield {

            (JSONFactory210.createTransactionRequestWithChargeJSON(transactionRequest), HttpCode.`202`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createTransactionRequestAttribute,
      implementedInApiVersion,
      nameOf(createTransactionRequestAttribute),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attribute",
      "Create Transaction Request Attribute",
      s""" Create Transaction Request Attribute
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      transactionRequestAttributeJsonV400,
      transactionRequestAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canCreateTransactionRequestAttributeAtOneBank))
    )

    lazy val createTransactionRequestAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $transactionRequestAttributeJsonV400 "
          for {
            (_, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              json.extract[TransactionRequestAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionRequestAttributeType.DOUBLE}(12.1234), ${TransactionRequestAttributeType.STRING}(TAX_NUMBER), ${TransactionRequestAttributeType.INTEGER}(123) and ${TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)"
            transactionRequestAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              TransactionRequestAttributeType.withName(postedData.`type`)
            }
            (transactionRequestAttribute, callContext) <- NewStyle.function.createOrUpdateTransactionRequestAttribute(
              bankId,
              transactionRequestId,
              None,
              postedData.name,
              transactionRequestAttributeType,
              postedData.value,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionRequestAttributeJson(transactionRequestAttribute), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getTransactionRequestAttributeById,
      implementedInApiVersion,
      nameOf(getTransactionRequestAttributeById),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attributes/ATTRIBUTE_ID",
      "Get Transaction Request Attribute By Id",
      s""" Get Transaction Request Attribute By Id
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionRequestAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canGetTransactionRequestAttributeAtOneBank))
    )

    lazy val getTransactionRequestAttributeById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) ::  "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attributes" :: transactionRequestAttributeId :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, cc.callContext)
            (transactionRequestAttribute, callContext) <- NewStyle.function.getTransactionRequestAttributeById(
              transactionRequestAttributeId,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionRequestAttributeJson(transactionRequestAttribute), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getTransactionRequestAttributes,
      implementedInApiVersion,
      nameOf(getTransactionRequestAttributes),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attributes",
      "Get Transaction Request Attributes",
      s""" Get Transaction Request Attributes
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionRequestAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canGetTransactionRequestAttributesAtOneBank))
    )

    lazy val getTransactionRequestAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attributes" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, cc.callContext)
            (transactionRequestAttribute, callContext) <- NewStyle.function.getTransactionRequestAttributes(
              bankId,
              transactionRequestId,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionRequestAttributesJson(transactionRequestAttribute), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      updateTransactionRequestAttribute,
      implementedInApiVersion,
      nameOf(updateTransactionRequestAttribute),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transaction-requests/TRANSACTION_REQUEST_ID/attributes/ATTRIBUTE_ID",
      "Update Transaction Request Attribute",
      s""" Update Transaction Request Attribute
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      transactionRequestAttributeJsonV400,
      transactionRequestAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canUpdateTransactionRequestAttributeAtOneBank))
    )

    lazy val updateTransactionRequestAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attributes" :: transactionRequestAttributeId :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionRequestAttributeJsonV400"
          for {
            (_, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              json.extract[TransactionRequestAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionRequestAttributeType.DOUBLE}(12.1234), ${TransactionRequestAttributeType.STRING}(TAX_NUMBER), ${TransactionRequestAttributeType.INTEGER}(123) and ${TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)"
            transactionRequestAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              TransactionRequestAttributeType.withName(postedData.`type`)
            }
            (_, callContext) <- NewStyle.function.getTransactionRequestAttributeById(transactionRequestAttributeId, callContext)
            (transactionRequestAttribute, callContext) <- NewStyle.function.createOrUpdateTransactionRequestAttribute(
              bankId,
              transactionRequestId,
              Some(transactionRequestAttributeId),
              postedData.name,
              transactionRequestAttributeType,
              postedData.value,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionRequestAttributeJson(transactionRequestAttribute), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createOrUpdateTransactionRequestAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateTransactionRequestAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/transaction-request",
      "Create or Update Transaction Request Attribute Definition",
      s""" Create or Update Transaction Request Attribute Definition
         |
         |The category field must be ${AttributeCategory.TransactionRequest}
         |
         |The type field must be one of: ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      transactionRequestAttributeDefinitionJsonV400,
      transactionRequestAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canCreateTransactionRequestAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateTransactionRequestAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction-request" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER}(123) and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.TransactionRequest}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getTransactionRequestAttributeDefinition,
      implementedInApiVersion,
      nameOf(getTransactionRequestAttributeDefinition),
      "GET",
      "/banks/BANK_ID/attribute-definitions/transaction-request",
      "Get Transaction Request Attribute Definition",
      s""" Get Transaction Request Attribute Definition
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionRequestAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canGetTransactionRequestAttributeDefinitionAtOneBank)))

    lazy val getTransactionRequestAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction-request" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributeDefinitions, callContext) <- getAttributeDefinition(
              AttributeCategory.withName(AttributeCategory.TransactionRequest.toString),
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionsJson(attributeDefinitions), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteTransactionRequestAttributeDefinition,
      implementedInApiVersion,
      nameOf(deleteTransactionRequestAttributeDefinition),
      "DELETE",
      "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/transaction-request",
      "Delete Transaction Request Attribute Definition",
      s""" Delete Transaction Request Attribute Definition by ATTRIBUTE_DEFINITION_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canDeleteTransactionRequestAttributeDefinitionAtOneBank)))

    lazy val deleteTransactionRequestAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "transaction-request" :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- deleteAttributeDefinition(
              attributeDefinitionId,
              AttributeCategory.withName(AttributeCategory.TransactionRequest.toString),
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      getDynamicEntities,
      implementedInApiVersion,
      nameOf(getDynamicEntities),
      "GET",
      "/management/dynamic-entities",
      "Get Dynamic Entities",
      s"""Get the all Dynamic Entities.""",
      EmptyBody,
      ListResult(
        "dynamic_entities",
        List(dynamicEntityResponseBodyExample)
      ),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canGetDynamicEntities))
    )


    lazy val getDynamicEntities: OBPEndpoint = {
      case "management" :: "dynamic-entities" :: Nil JsonGet req => {
        cc =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(None))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            val jObjects = listCommons.map(_.jValue)
            (ListResult("dynamic_entities", jObjects), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelDynamicEntities,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicEntities),
      "GET",
      "/management/banks/BANK_ID/dynamic-entities",
      "Get Bank Level Dynamic Entities",
      s"""Get all the bank level Dynamic Entities for one bank.""",
      EmptyBody,
      ListResult(
        "dynamic_entities",
        List(dynamicEntityResponseBodyExample)
      ),
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canGetBankLevelDynamicEntities, canGetDynamicEntities))
    )

    lazy val getBankLevelDynamicEntities: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-entities" :: Nil JsonGet req => {
        cc =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(Some(bankId)))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            val jObjects = listCommons.map(_.jValue)
            (ListResult("dynamic_entities", jObjects), HttpCode.`200`(cc.callContext))
          }
      }
    }

    private def createDynamicEntityMethod(cc: CallContext, dynamicEntity: DynamicEntityCommons) = {
      for {
        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, cc.callContext)
        //granted the CRUD roles to the loggedIn User
        curdRoles = List(
          DynamicEntityInfo.canCreateRole(result.entityName, dynamicEntity.bankId),
          DynamicEntityInfo.canUpdateRole(result.entityName, dynamicEntity.bankId),
          DynamicEntityInfo.canGetRole(result.entityName, dynamicEntity.bankId),
          DynamicEntityInfo.canDeleteRole(result.entityName, dynamicEntity.bankId)
        )
      } yield {
        curdRoles.map(role => Entitlement.entitlement.vend.addEntitlement(dynamicEntity.bankId.getOrElse(""), cc.userId, role.toString()))
        val commonsData: DynamicEntityCommons = result
        (commonsData.jValue, HttpCode.`201`(cc.callContext))
      }
    }
    
    private def createDynamicEntityDoc = ResourceDoc(
      createDynamicEntity,
      implementedInApiVersion,
      nameOf(createDynamicEntity),
      "POST",
      "/management/dynamic-entities",
      "Create Dynamic Entity",
      s"""Create a DynamicEntity.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |Create a DynamicEntity. If creation is successful, the corresponding POST, GET, PUT and DELETE (Create, Read, Update, Delete or CRUD for short) endpoints will be generated automatically
         |
         |The following field types are as supported:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", ", reference]")}
         |
         |The ${DynamicEntityFieldType.DATE_WITH_DAY} format is: ${DynamicEntityFieldType.DATE_WITH_DAY.dateFormat}
         |
         |Reference types are like foreign keys and composite foreign keys are supported. The value you need to supply as the (composite) foreign key is a UUID (or several UUIDs in the case of a composite key) that match value in another Entity..
         |See the following list of currently available reference types and examples of how to construct key values correctly. Note: As more Dynamic Entities are created on this instance, this list will grow:
         |```
         |${ReferenceType.referenceTypeAndExample.mkString("\n")}
         |```
         | Note: BankId filed is optional, 
         |          if you add it, the entity will be the Bank level.
         |          if you omit it, the entity will be the System level.  
         |""",
      dynamicEntityRequestBodyExample,
      dynamicEntityResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canCreateDynamicEntity)))

    lazy val createDynamicEntity: OBPEndpoint = {
      case "management" :: "dynamic-entities" :: Nil JsonPost json -> _ => {
        cc =>
          val dynamicEntity = DynamicEntityCommons(json.asInstanceOf[JObject], None, cc.userId)
          createDynamicEntityMethod(cc, dynamicEntity)
      }
    }

    private def createBankLevelDynamicEntityDoc = ResourceDoc(
      createBankLevelDynamicEntity,
      implementedInApiVersion,
      nameOf(createBankLevelDynamicEntity),
      "POST",
      "/management/banks/BANK_ID/dynamic-entities",
      "Create Bank Level Dynamic Entity",
      s"""Create a Bank Level DynamicEntity.
         |
         |${authenticationRequiredMessage(true)}
         |
         |Create a DynamicEntity. If creation is successful, the corresponding POST, GET, PUT and DELETE (Create, Read, Update, Delete or CRUD for short) endpoints will be generated automatically
         |
         |The following field types are as supported:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", ", reference]")}
         |
         |The ${DynamicEntityFieldType.DATE_WITH_DAY} format is: ${DynamicEntityFieldType.DATE_WITH_DAY.dateFormat}
         |
         |Reference types are like foreign keys and composite foreign keys are supported. The value you need to supply as the (composite) foreign key is a UUID (or several UUIDs in the case of a composite key) that match value in another Entity..
         |The following list shows all the possible reference types in the system with corresponding examples values so you can see how to construct each reference type value.
         |```
         |${ReferenceType.referenceTypeAndExample.mkString("\n")}
         |```
         | Note: BankId filed is optional, 
         |          if you add it, the entity will be the Bank level.
         |          if you omit it, the entity will be the System level.  
         |""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canCreateBankLevelDynamicEntity, canCreateDynamicEntity)))
    lazy val createBankLevelDynamicEntity: OBPEndpoint = {
      case "management" ::"banks" :: BankId(bankId) :: "dynamic-entities" :: Nil JsonPost json -> _ => {
        cc =>
          val dynamicEntity = DynamicEntityCommons(json.asInstanceOf[JObject], None, cc.userId).copy(bankId = Some(bankId.value))
          createDynamicEntityMethod(cc, dynamicEntity)
      }
    }
    
    //bankId is option, if it is bankLevelEntity, we need BankId, if system Level Entity, bankId is None.
    private def updateDynamicEntityMethod(bankId: Option[String], dynamicEntityId: String, json: JValue, cc: CallContext) = {
      for {
        // Check whether there are uploaded data, only if no uploaded data allow to update DynamicEntity.
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankId, dynamicEntityId, cc.callContext)
        (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entity.entityName, None, None, entity.bankId, None, cc.callContext)
        resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
        _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = cc.callContext) {
          resultList.arr.isEmpty
        }

        jsonObject = json.asInstanceOf[JObject]
        dynamicEntity = DynamicEntityCommons(jsonObject, Some(dynamicEntityId), cc.userId).copy(bankId = bankId)
        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, cc.callContext)
      } yield {
        val commonsData: DynamicEntityCommons = result
        (commonsData.jValue, HttpCode.`200`(cc.callContext))
      }
    }


    private def updateDynamicEntityDoc = ResourceDoc(
      updateDynamicEntity,
      implementedInApiVersion,
      nameOf(updateDynamicEntity),
      "PUT",
      "/management/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update Dynamic Entity",
      s"""Update a DynamicEntity.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |Update one DynamicEntity, after update finished, the corresponding CRUD endpoints will be changed.
         |
         |The following field types are as supported:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", ", reference]")}
         |
         |${DynamicEntityFieldType.DATE_WITH_DAY} format: ${DynamicEntityFieldType.DATE_WITH_DAY.dateFormat}
         |
         |Reference types are like foreign keys and composite foreign keys are supported. The value you need to supply as the (composite) foreign key is a UUID (or several UUIDs in the case of a composite key) that match value in another Entity..
         |The following list shows all the possible reference types in the system with corresponding examples values so you can see how to construct each reference type value.
         |```
         |${ReferenceType.referenceTypeAndExample.mkString("\n")}
         |```
         |""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateDynamicEntity)))
    lazy val updateDynamicEntity: OBPEndpoint = {
      case "management" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc =>
          updateDynamicEntityMethod(None, dynamicEntityId, json, cc)
      }
    }    
    
    private def updateBankLevelDynamicEntityDoc = ResourceDoc(
      updateBankLevelDynamicEntity,
      implementedInApiVersion,
      nameOf(updateBankLevelDynamicEntity),
      "PUT",
      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update Bank Level Dynamic Entity",
      s"""Update a Bank Level DynamicEntity.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |Update one DynamicEntity, after update finished, the corresponding CRUD endpoints will be changed.
         |
         |The following field types are as supported:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", ", reference]")}
         |
         |${DynamicEntityFieldType.DATE_WITH_DAY} format: ${DynamicEntityFieldType.DATE_WITH_DAY.dateFormat}
         |
         |Reference types are like foreign keys and composite foreign keys are supported. The value you need to supply as the (composite) foreign key is a UUID (or several UUIDs in the case of a composite key) that match value in another Entity..
         |The following list shows all the possible reference types in the system with corresponding examples values so you can see how to construct each reference type value.
         |```
         |${ReferenceType.referenceTypeAndExample.mkString("\n")}
         |```
         |""",
      dynamicEntityRequestBodyExample.copy(bankId=None),
      dynamicEntityResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateBankLevelDynamicEntity, canUpdateDynamicEntity)))
    lazy val updateBankLevelDynamicEntity: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc =>
          updateDynamicEntityMethod(Some(bankId),dynamicEntityId, json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteDynamicEntity,
      implementedInApiVersion,
      nameOf(deleteDynamicEntity),
      "DELETE",
      "/management/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete Dynamic Entity",
      s"""Delete a DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteDynamicEntity)))
    lazy val deleteDynamicEntity: OBPEndpoint = {
      case "management" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc =>
          deleteDynamicEntityMethod(None, dynamicEntityId, cc)
      }
    }

    private def deleteDynamicEntityMethod(bankId: Option[String], dynamicEntityId: String, cc: CallContext) = {
      for {
        // Check whether there are uploaded data, only if no uploaded data allow to delete DynamicEntity.
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankId, dynamicEntityId, cc.callContext)
        (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entity.entityName, None, None, entity.bankId, None, cc.callContext)
        resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
        _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = cc.callContext) {
          resultList.arr.isEmpty
        }
        deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(bankId, dynamicEntityId)
      } yield {
        (deleted, HttpCode.`204`(cc.callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteBankLevelDynamicEntity,
      implementedInApiVersion,
      nameOf(deleteBankLevelDynamicEntity),
      "DELETE",
      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete Bank Level Dynamic Entity",
      s"""Delete a Bank Level DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteBankLevelDynamicEntity, canDeleteDynamicEntity)))
    lazy val deleteBankLevelDynamicEntity: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc =>
          deleteDynamicEntityMethod(Some(bankId), dynamicEntityId, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyDynamicEntities,
      implementedInApiVersion,
      nameOf(getMyDynamicEntities),
      "GET",
      "/my/dynamic-entities",
      "Get My Dynamic Entities",
      s"""Get all my Dynamic Entities.""",
      EmptyBody,
      ListResult(
        "dynamic_entities",
        List(dynamicEntityResponseBodyExample)
      ),
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle)
    )

    lazy val getMyDynamicEntities: OBPEndpoint = {
      case "my" :: "dynamic-entities" :: Nil JsonGet req => {
        cc =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(cc.userId))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            val jObjects = listCommons.map(_.jValue)
            (ListResult("dynamic_entities", jObjects), HttpCode.`200`(cc.callContext))
          }
      }
    }

    private def updateMyDynamicEntityDoc = ResourceDoc(
      updateMyDynamicEntity,
      implementedInApiVersion,
      nameOf(updateMyDynamicEntity),
      "PUT",
      "/my/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update My Dynamic Entity",
      s"""Update my DynamicEntity.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |Update one of my DynamicEntity, after update finished, the corresponding CRUD endpoints will be changed.
         |
         |Current support filed types as follow:
         |${DynamicEntityFieldType.values.map(_.toString).mkString("[", ", ", ", reference]")}
         |
         |${DynamicEntityFieldType.DATE_WITH_DAY} format: ${DynamicEntityFieldType.DATE_WITH_DAY.dateFormat}
         |
         |Reference types are like foreign keys and composite foreign keys are supported. The value you need to supply as the (composite) foreign key is a UUID (or several UUIDs in the case of a composite key) that match value in another Entity..
         |The following list shows all the possible reference types in the system with corresponding examples values so you can see how to construct each reference type value.
         |```
         |${ReferenceType.referenceTypeAndExample.mkString("\n")}
         |```
         |""",
      dynamicEntityRequestBodyExample.copy(bankId=None),
      dynamicEntityResponseBodyExample,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle)
    )

    lazy val updateMyDynamicEntity: OBPEndpoint = {
      case "my" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            // Check whether there are uploaded data, only if no uploaded data allow to update DynamicEntity.
            (entity, _) <- NewStyle.function.getDynamicEntityById(None, dynamicEntityId, cc.callContext)
            _ <- Helper.booleanToFuture(InvalidMyDynamicEntityUser, cc=cc.callContext) {
              entity.userId.equals(cc.userId)
            }
            (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entity.entityName, None, None, entity.bankId, None, cc.callContext)
            resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
            _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc=cc.callContext) {
              resultList.arr.isEmpty
            }
            jsonObject = json.asInstanceOf[JObject]
            dynamicEntity = DynamicEntityCommons(jsonObject, Some(dynamicEntityId), cc.userId)
            Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, cc.callContext)
          } yield {
            val commonsData: DynamicEntityCommons = result
            (commonsData.jValue, HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteMyDynamicEntity,
      implementedInApiVersion,
      nameOf(deleteMyDynamicEntity),
      "DELETE",
      "/my/dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete My Dynamic Entity",
      s"""Delete my DynamicEntity specified by DYNAMIC_ENTITY_ID.
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi, apiTagNewStyle)
    )

    lazy val deleteMyDynamicEntity: OBPEndpoint = {
      case "my" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc =>
          for {
            // Check whether there are uploaded data, only if no uploaded data allow to delete DynamicEntity.
            (entity, _) <- NewStyle.function.getDynamicEntityById(None, dynamicEntityId, cc.callContext)
            _ <- Helper.booleanToFuture(InvalidMyDynamicEntityUser, cc=cc.callContext) {
              entity.userId.equals(cc.userId)
            }
            (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entity.entityName, None, None, entity.bankId, None, cc.callContext)
            resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
            _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc=cc.callContext) {
              resultList.arr.isEmpty
            }
            deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(None, dynamicEntityId)
          } yield {
            (deleted, HttpCode.`200`(cc.callContext))
          }
      }
    }


    private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
       if(box.isInstanceOf[Failure]) {
         val failure = box.asInstanceOf[Failure]
         // change the internal db column name 'dynamicdataid' to entity's id name
         val msg = failure.msg.replace(DynamicData.DynamicDataId.dbColumnName, StringUtils.uncapitalize(entityName) + "Id")
         val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
         fullBoxOrException[T](changedMsgFailure)
      }

      box.openOrThrowException("impossible error")
    }

    //TODO temp solution to support query by field name and value
    private def filterDynamicObjects(resultList: JArray, req: Req): JArray = {
      req.params match {
        case map if map.isEmpty => resultList
        case params =>
          val filteredWithFieldValue = resultList.arr.filter { jValue =>
            params.forall { kv =>
              val (path, values) = kv
              values.exists(JsonUtils.isFieldEquals(jValue, path, _))
            }
          }

          JArray(filteredWithFieldValue)
      }
    }

    lazy val genericEndpoint: OBPEndpoint = {
      case EntityName(bankId, entityName, id) JsonGet req => { cc =>
        val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val isGetAll = StringUtils.isBlank(id)

        val operation: DynamicEntityOperation = if(StringUtils.isBlank(id)) GET_ALL else GET_ONE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)
        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if(beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.

          (_, callContext ) <- 
            if(bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else { 
              Future.successful{("", callContext)}
            }
          
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc=callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, Some(cc))
          
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc=callContext) {box.isDefined}
        } yield {
          val jValue = if(isGetAll) {
            val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
            if (bankId.isDefined){
              val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
              val result: JObject = (listName -> filterDynamicObjects(resultList, req))
              bankIdJobject merge result
            } else{
              val result: JObject = (listName -> filterDynamicObjects(resultList, req))
              result
            }
          }else{
              val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
              if (bankId.isDefined) {
                val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
                val result: JObject = (singleName -> singleObject)
                bankIdJobject merge result
              }else{
                val result: JObject = (singleName -> singleObject)
                result
              }
          }
          (jValue, HttpCode.`200`(Some(cc)))
        }
      }
        
      case EntityName(bankId, entityName, _) JsonPost json -> _ => {cc =>
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val operation: DynamicEntityOperation = CREATE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)

        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if(beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          (_, callContext ) <-
            if(bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful{("", callContext)}
            }
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canCreateRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc=callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), None, bankId, None, Some(cc))
          singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
        } yield {
          val result: JObject = (singleName -> singleObject)
          val entity = if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            bankIdJobject merge result
          } else {
            result
          }  
          (entity, HttpCode.`201`(Some(cc)))
        }
      }
      case EntityName(bankId, entityName, id) JsonPut json -> _ => { cc =>
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val operation: DynamicEntityOperation = UPDATE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)

        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if(beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          (_, callContext ) <-
            if(bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful{("", callContext)}
            }
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canUpdateRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc=callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(cc))
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc=callContext) {
            box.isDefined
          }
          (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), Some(id), bankId, None, Some(cc))
          singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
        } yield {
          val result: JObject = (singleName -> singleObject)
          val entity = if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            bankIdJobject merge result
          } else {
            result
          }
          (entity, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(bankId, entityName, id) JsonDelete _ => { cc =>
        val operation: DynamicEntityOperation = DELETE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)

        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if(beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          (_, callContext ) <-
            if(bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful{("", callContext)}
            }
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canDeleteRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc=callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(cc))
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc=callContext) {
            box.isDefined
          }
          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Some(id), bankId, None, Some(cc))
          deleteResult: JBool = unboxResult(box.asInstanceOf[Box[JBool]], entityName)
        } yield {
          (deleteResult, HttpCode.`204`(Some(cc)))
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      resetPasswordUrl,
      implementedInApiVersion,
      nameOf(resetPasswordUrl),
      "POST",
      "/management/user/reset-password-url",
      "Create password reset url",
      s"""Create password reset url.
         |
         |""",
      PostResetPasswordUrlJsonV400("jobloggs", "jo@gmail.com", "74a8ebcc-10e4-4036-bef3-9835922246bf"),
      ResetPasswordUrlJsonV400( "https://apisandbox.openbankproject.com/user_mgt/reset_password/QOL1CPNJPCZ4BRMPX3Z01DPOX1HMGU3L"),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canCreateResetPasswordUrl)))

    lazy val resetPasswordUrl : OBPEndpoint = {
      case "management" :: "user" :: "reset-password-url" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.NotAllowedEndpoint, cc=cc.callContext) {
              APIUtil.getPropsAsBoolValue("ResetPasswordUrlEnabled", false)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the ${classOf[PostResetPasswordUrlJsonV400]} "
            postedData <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostResetPasswordUrlJsonV400]
            }
          } yield {
             val resetLink = AuthUser.passwordResetUrl(postedData.username, postedData.email, postedData.user_id)
            (ResetPasswordUrlJsonV400(resetLink), HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
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
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidAccountBalanceAmount,
        InvalidAccountInitialBalance,
        InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canCreateAccount))
    ).disableAutoValidateRoles()  // this means disabled auto roles validation, will manually do the roles validation .


    lazy val addAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonPost json -> _ => {
        cc =>{
          val failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(createAccountRequestJsonV310))} "
          for {
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[CreateAccountRequestJsonV310]
            }
            loggedInUserId = cc.userId
            userIdAccountOwner = if (createAccountJson.user_id.nonEmpty) createAccountJson.user_id else loggedInUserId
            (postedOrLoggedInUser,callContext) <- NewStyle.function.findByUserId(userIdAccountOwner, cc.callContext)

            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(Unit))
              else NewStyle.function.hasEntitlement(bankId.value, loggedInUserId, canCreateAccount, callContext, s"${UserHasMissingRoles} $canCreateAccount or create account for self")

            initialBalanceAsString = createAccountJson.balance.amount
            //Note: here we map the product_code to account_type
            accountType = createAccountJson.product_code
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, callContext) {
              BigDecimal(initialBalanceAsString)
            }
            _ <-  Helper.booleanToFuture(InitialBalanceMustBeZero, cc=callContext){0 == initialBalanceAsNumber}
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode, cc=callContext){isValidCurrencyISOCode(createAccountJson.balance.currency)}
            currency = createAccountJson.balance.currency
            (_, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme", cc=callContext) {
              createAccountJson.account_routings.map(_.scheme).distinct.size == createAccountJson.account_routings.size
            }
            alreadyExistAccountRoutings <- Future.sequence(createAccountJson.account_routings.map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bankId), accountRouting.scheme, accountRouting.address, callContext).map(_ => Some(accountRouting)).fallbackTo(Future.successful(None))
              ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(accountRouting) => s"bankId: $bankId, scheme: ${accountRouting.scheme}, address: ${accountRouting.address}"
            }
            _ <- Helper.booleanToFuture(s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})", cc=callContext) {
              alreadyExistingAccountRouting.isEmpty
            }
            (bankAccount,callContext) <- NewStyle.function.addBankAccount(
              bankId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id,
              createAccountJson.account_routings.map(r => AccountRouting(r.scheme, r.address)),
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
      val resourceDocsRequiresRole = APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)

      APIInfoJson400(apiVersion.vDottedApiVersion, apiVersionStatus, gitCommit, connector, hostedBy, hostedAt, energySource, resourceDocsRequiresRole)
    }


    staticResourceDocs += ResourceDoc(
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
      EmptyBody,
      apiInfoJson400,
      List(UnknownError, "no connector set"),
      apiTagApi :: apiTagNewStyle :: Nil)

    lazy val root : OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc => Future {
          getApiInfoJSON() -> HttpCode.`200`(cc.callContext)
        }
      }
    }


    staticResourceDocs += ResourceDoc(
      getCallContext,
      implementedInApiVersion,
      nameOf(getCallContext),
      "GET",
      "/development/call_context",
      "Get the Call Context of a current call",
      s"""Get the Call Context of the current call.
         |
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List($UserNotLoggedIn, UnknownError),
      List(apiTagApi, apiTagNewStyle),
      Some(List(canGetCallContext)))

    lazy val getCallContext: OBPEndpoint = {
      case "development" :: "call_context" :: Nil JsonGet _ => {
        cc => Future{
            (cc.callContext, HttpCode.`200`(cc.callContext))
          }
        }
    }

    staticResourceDocs += ResourceDoc(
      verifyRequestSignResponse,
      implementedInApiVersion,
      nameOf(verifyRequestSignResponse),
      "GET",
      "/development/echo/jws-verified-request-jws-signed-response",
      "Verify Request and Sign Response of a current call",
      s"""Verify Request and Sign Response of a current call.
         |
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List($UserNotLoggedIn, UnknownError),
      List(apiTagApi, apiTagNewStyle),
      Some(Nil))

    lazy val verifyRequestSignResponse: OBPEndpoint = {
      case "development" :: "echo":: "jws-verified-request-jws-signed-response" :: Nil JsonGet _ => {
        cc => Future{
            (cc.callContext, HttpCode.`200`(cc.callContext))
          }
        }
    }


    staticResourceDocs += ResourceDoc(
      updateAccountLabel,
      implementedInApiVersion,
      "updateAccountLabel",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Update Account Label",
      s"""Update the label for the account. The label is how the account is known to the account owner e.g. 'My savings account'
         |
         |
         |${authenticationRequiredMessage(true)}
         |
       """.stripMargin,
      updateAccountJsonV400,
      successMessage,
      List(InvalidJsonFormat, $UserNotLoggedIn, $BankNotFound, UnknownError, $BankAccountNotFound, "user does not have access to owner view on account"),
      List(apiTagAccount, apiTagNewStyle)
    )

    lazy val updateAccountLabel : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), account, callContext) <- SS.userAccount
            failMsg = s"$InvalidJsonFormat The Json body should be the $InvalidJsonFormat "
            json <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[UpdateAccountJsonV400]
            }
          } yield {
            account.updateLabel(u, json.label)
            (Extraction.decompose(successMessage), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      lockUser,
      implementedInApiVersion,
      nameOf(lockUser),
      "POST",
      "/users/USERNAME/locks",
      "Lock the user",
      s"""
         |Lock a User.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      userLockStatusJson,
      List($UserNotLoggedIn, UserNotFoundByUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canLockUser)))

    lazy val lockUser : OBPEndpoint = {
      case "users" :: username ::  "locks" :: Nil JsonPost req => {
        cc =>
          for {
            (Full(u), callContext) <-  SS.user
            userLocks <- Future { UserLocksProvider.lockUser(username) } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)", 404)
            }
          } yield {
            (JSONFactory400.createUserLockStatusJson(userLocks), HttpCode.`200`(callContext))
          }
      }
    }
    

    staticResourceDocs += ResourceDoc(
      getEntitlements,
      implementedInApiVersion,
      "getEntitlements",
      "GET",
      "/users/USER_ID/entitlements",
      "Get Entitlements for User",
      s"""
         |
         |
      """.stripMargin,
      EmptyBody,
      entitlementJSONs,
      List($UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)))


    lazy val getEntitlements: OBPEndpoint = {
      case "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(userId, cc.callContext)
          } yield {
            var json = EntitlementJSONs(Nil)
            // Format the data as V2.0.0 json
            if (isSuperAdmin(userId)) {
              // If the user is SuperAdmin add it to the list
              json = JSONFactory200.addedSuperAdminEntitlementJson(entitlements)
            } else {
              json = JSONFactory200.createEntitlementJSONs(entitlements)
            }
            (json, HttpCode.`200`(cc.callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getEntitlementsForBank,
      implementedInApiVersion,
      nameOf(getEntitlementsForBank),
      "GET",
      "/banks/BANK_ID/entitlements",
      "Get Entitlements for One Bank",
      s"""
         |
      """.stripMargin,
      EmptyBody,
      entitlementJSONs,
      List($UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canGetEntitlementsForOneBank,canGetEntitlementsForAnyBank)))

    val allowedEntitlements = canGetEntitlementsForOneBank:: canGetEntitlementsForAnyBank :: Nil
    val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")

    lazy val getEntitlementsForBank: OBPEndpoint = {
      case "banks" :: bankId :: "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByBankId(bankId, cc.callContext)
          } yield {
            val json = JSONFactory400.createEntitlementJSONs(entitlements)
            (json, HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      addTagForViewOnAccount,
      implementedInApiVersion,
      "addTagForViewOnAccount",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags",
      "Create a tag on account",
      s"""Posts a tag about an account ACCOUNT_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${authenticationRequiredMessage(true)}
         |
         |Authentication is required as the tag is linked with the user.""",
      postAccountTagJSON,
      accountTagJSON,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        $UserNoPermissionAccessView,
        UnknownError),
      List(apiTagAccountMetadata, apiTagAccount, apiTagNewStyle))

    lazy val addTagForViewOnAccount : OBPEndpoint = {
      //add a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), view, callContext) <- SS.userView
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_add_tag. Current ViewId($viewId)", cc=callContext) {
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

    staticResourceDocs += ResourceDoc(
      deleteTagForViewOnAccount,
      implementedInApiVersion,
      "deleteTagForViewOnAccount",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags/TAG_ID",
      "Delete a tag on account",
      s"""Deletes the tag TAG_ID about the account ACCOUNT_ID made on [view](#1_2_1-getViewsForBankAccount).
        |
        |${authenticationRequiredMessage(true)}
        |
        |Authentication is required as the tag is linked with the user.""",
      EmptyBody,
      EmptyBody,
      List(NoViewPermission,
        ViewNotFound,
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UnknownError),
      List(apiTagAccountMetadata, apiTagAccount, apiTagNewStyle))

    lazy val deleteTagForViewOnAccount : OBPEndpoint = {
      //delete a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {
        cc =>
          for {
            (view, callContext) <- SS.view
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_delete_tag. Current ViewId($viewId)", cc=callContext) {
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


    staticResourceDocs += ResourceDoc(
      getTagsForViewOnAccount,
      implementedInApiVersion,
      "getTagsForViewOnAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/metadata/tags",
      "Get tags on account",
      s"""Returns the account ACCOUNT_ID tags made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
         |${authenticationRequiredMessage(true)}
         |
         |Authentication is required as the tag is linked with the user.""",
      EmptyBody,
      accountTagsJSON,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        NoViewPermission,
        $UserNoPermissionAccessView,
        UnknownError
      ),
      List(apiTagAccountMetadata, apiTagAccount, apiTagNewStyle))

    lazy val getTagsForViewOnAccount : OBPEndpoint = {
      //get tags
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: Nil JsonGet req => {
        cc =>
          for {
            (view, callContext) <- SS.view
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_tags. Current ViewId($viewId)", cc=callContext) {
              view.canSeeTags
            }
            tags <- Future(Tags.tags.vend.getTagsOnAccount(bankId, accountId)(viewId))
          } yield {
            val json = JSONFactory400.createAccountTagsJSON(tags)
            (json, HttpCode.`200`(callContext))
          }
      }
    }




    staticResourceDocs += ResourceDoc(
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
         |* Tags - A list of Tags assigned to this account
         |
         |This call returns the owner view and requires access to that view.
         |
         |
         |""".stripMargin,
      EmptyBody,
      moderatedCoreAccountJsonV400,
      List($UserNotLoggedIn, $BankAccountNotFound,UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 ::  apiTagNewStyle :: Nil
    )
    lazy val getCoreAccountById : OBPEndpoint = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (user @Full(u), account, callContext) <- SS.userAccount
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, user, callContext)
          } yield {
            val availableViews: List[View] = Views.views.vend.privateViewsUserCanAccessForAccount(u, BankIdAccountId(account.bankId, account.accountId))
            (createNewCoreBankAccountJson(moderatedAccount, availableViews), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
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
      EmptyBody,
      moderatedAccountJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UnknownError),
      apiTagAccount ::  apiTagNewStyle :: Nil
    )
    lazy val getPrivateAccountByIdFull : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, user, callContext)
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


    staticResourceDocs += ResourceDoc(
      getAccountByAccountRouting,
      implementedInApiVersion,
      nameOf(getAccountByAccountRouting),
      "POST",
      "/management/accounts/account-routing-query",
      "Get Account by Account Routing",
      """This endpoint returns the account (if it exists) linked with the provided scheme and address.
        |
        |The `bank_id` field is optional, but if it's not provided, we don't guarantee that the returned account is unique across all the banks.
        |
        |Example of account routing scheme: `IBAN`, "OBP", "AccountNumber", ...
        |Example of account routing address: `DE17500105178275645584`, "321774cc-fccd-11ea-adc1-0242ac120002", "55897106215", ...
        |
        |""".stripMargin,
      bankAccountRoutingJson,
      moderatedAccountJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UnknownError),
      List(apiTagAccount, apiTagNewStyle),
    )
    lazy val getAccountByAccountRouting : OBPEndpoint = {
      case "management" :: "accounts" :: "account-routing-query" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $accountRoutingJsonV121"
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[BankAccountRoutingJson]
            }

            (account, callContext) <- NewStyle.function.getBankAccountByRouting(postJson.bank_id.map(BankId(_)),
              postJson.account_routing.scheme, postJson.account_routing.address, cc.callContext)

            user @Full(u) = cc.user
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, user, callContext)

            (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(
              account.bankId,
              account.accountId,
              callContext: Option[CallContext])
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(cc.user.openOrThrowException("Exception user"), BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews.map(JSONFactory.createViewJSON).sortBy(_.short_name)
            val tags = Tags.tags.vend.getTagsOnAccount(account.bankId, account.accountId)(view.viewId)
            (createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes, tags), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAccountsByAccountRoutingRegex,
      implementedInApiVersion,
      nameOf(getAccountsByAccountRoutingRegex),
      "POST",
      "/management/accounts/account-routing-regex-query",
      "Get Accounts by Account Routing Regex",
      """This endpoint returns an array of accounts matching the provided routing scheme and the routing address regex.
        |
        |The `bank_id` field is optional.
        |
        |Example of account routing scheme: `IBAN`, `OBP`, `AccountNumber`, ...
        |Example of account routing address regex: `DE175.*`, `55897106215-[A-Z]{3}`, ...
        |
        |This endpoint can be used to retrieve multiples accounts matching a same account routing address pattern.
        |For example, if you want to link multiple accounts having different currencies, you can create an account
        |with `123456789-EUR` as Account Number and an other account with `123456789-USD` as Account Number.
        |So we can identify the Account Number as `123456789`, so to get all the accounts with the same account number
        |and the different currencies, we can use this body in the request :
        |
        |```
        |{
        |   "bank_id": "BANK_ID",
        |   "account_routing": {
        |       "scheme": "AccountNumber",
        |       "address": "123456789-[A-Z]{3}"
        |   }
        |}
        |```
        |
        |This request will returns the accounts matching the routing address regex (`123456789-EUR` and `123456789-USD`).
        |
        |""".stripMargin,
      bankAccountRoutingJson,
      moderatedAccountsJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UnknownError),
      List(apiTagAccount, apiTagNewStyle),
    )
    lazy val getAccountsByAccountRoutingRegex : OBPEndpoint = {
      case "management" :: "accounts" :: "account-routing-regex-query" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $accountRoutingJsonV121"
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[BankAccountRoutingJson]
            }

            (accountRoutings, callContext) <- NewStyle.function.getAccountRoutingsByScheme(postJson.bank_id.map(BankId(_)),
              postJson.account_routing.scheme, cc.callContext)

            accountRoutingAddressRegex = postJson.account_routing.address.r
            filteredAccountRoutings = accountRoutings.filter(accountRouting =>
              accountRoutingAddressRegex.findFirstIn(accountRouting.accountRouting.address).isDefined)

            user @Full(u) = cc.user

            accountsJson <- Future.sequence(filteredAccountRoutings.map(accountRouting => for {
              (account, callContext) <- NewStyle.function.getBankAccount(accountRouting.bankId, accountRouting.accountId, callContext)
              view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
              moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, user, callContext)
              (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(
                account.bankId,
                account.accountId,
                callContext: Option[CallContext])
              availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(cc.user.openOrThrowException("Exception user"), BankIdAccountId(account.bankId, account.accountId))
              viewsAvailable = availableViews.map(JSONFactory.createViewJSON).sortBy(_.short_name)
              tags = Tags.tags.vend.getTagsOnAccount(account.bankId, account.accountId)(view.viewId)
            } yield createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes, tags)
            ))
          } yield {
            (ModeratedAccountsJSON400(accountsJson), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankAccountsBalances,
      implementedInApiVersion,
      nameOf(getBankAccountsBalances),
      "GET",
      "/banks/BANK_ID/balances",
      "Get Accounts Balances",
      """Get the Balances for the Accounts of the current User at one bank.""",
      EmptyBody,
      accountBalancesV400Json,
      List($UserNotLoggedIn, $BankNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
    )

    lazy val getBankAccountsBalances : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            (accountsBalances, callContext)<- NewStyle.function.getBankAccountsBalances(availablePrivateAccounts, callContext)
          } yield{
            (createBalancesJson(accountsBalances), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankAccountBalances,
      implementedInApiVersion,
      nameOf(getBankAccountBalances),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances",
      "Get Account Balances",
      """Get the Balances for one Account of the current User at one bank.""",
      EmptyBody,
      accountBalanceV400,
      List($UserNotLoggedIn, $BankNotFound, CannotFindAccountAccess, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
    )

    lazy val getBankAccountBalances : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            bankIdAcconutId <- NewStyle.function.tryons(s"$CannotFindAccountAccess AccountId(${accountId.value})", 400, cc.callContext) {availablePrivateAccounts.find(_.accountId==accountId).get}
            (accountBalances, callContext)<- NewStyle.function.getBankAccountBalances(bankIdAcconutId, callContext)
          } yield{
            (createAccountBalancesJson(accountBalances), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getFirehoseAccountsAtOneBank,
      implementedInApiVersion,
      nameOf(getFirehoseAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/firehose/accounts/views/VIEW_ID",
      "Get Firehose Accounts at Bank",
      s"""
         |Get Accounts which have a firehose view assigned to them.
         |
         |This endpoint allows bulk access to accounts.
         |
         |Requires the CanUseFirehoseAtAnyBank Role
         |
         |To be shown on the list, each Account must have a firehose View linked to it.
         |
         |A firehose view has is_firehose = true
         |
         |For VIEW_ID try 'owner'
         |
         |optional request parameters for filter with attributes
         |URL params example:
         |  /banks/some-bank-id/firehose/accounts/views/owner?manager=John&count=8
         |
         |to invalid Browser cache, add timestamp query parameter as follow, the parameter name must be `_timestamp_`
         |URL params example:
         |  `/banks/some-bank-id/firehose/accounts/views/owner?manager=John&count=8&_timestamp_=1596762180358`
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      moderatedFirehoseAccountsJsonV400,
      List($BankNotFound),
      List(apiTagAccount, apiTagAccountFirehose, apiTagFirehoseData, apiTagNewStyle),
      Some(List(canUseAccountFirehoseAtAnyBank, ApiRole.canUseAccountFirehose))
    )

    lazy val getFirehoseAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" :: "accounts"  :: "views" :: ViewId(viewId):: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), bank, callContext) <- SS.userBank
            _ <- Helper.booleanToFuture(failMsg = AccountFirehoseNotAllowedOnThisInstance, cc=cc.callContext) {
              allowAccountFirehose
            }
            // here must be a system view, not accountIds in the URL
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, AccountId("")), Some(u), callContext)
            availableBankIdAccountIdList <- Future {
              Views.views.vend.getAllFirehoseAccounts(bank.bankId).map(a => BankIdAccountId(a.bankId,a.accountId))
            }
            params = req.params.filterNot(_._1 == "_timestamp_") // ignore `_timestamp_` parameter, it is for invalid Browser caching
            availableBankIdAccountIdList2 <- if(params.isEmpty) {
              Future.successful(availableBankIdAccountIdList)
            } else {
              AccountAttributeX.accountAttributeProvider.vend
                .getAccountIdsByParams(bankId, params)
                .map { boxedAccountIds =>
                  val accountIds = boxedAccountIds.getOrElse(Nil)
                  availableBankIdAccountIdList.filter(availableBankIdAccountId => accountIds.contains(availableBankIdAccountId.accountId.value))
                }
            }
            moderatedAccounts: List[ModeratedBankAccount] = for {
              //Here is a new for-loop to get the moderated accouts for the firehose user, according to the viewId.
              //1 each accountId-> find a proper bankAccount object.
              //2 each bankAccount object find the proper view.
              //3 use view and user to moderate the bankaccount object.
              bankIdAccountId <- availableBankIdAccountIdList2
              bankAccount <- Connector.connector.vend.getBankAccountOld(bankIdAccountId.bankId, bankIdAccountId.accountId) ?~! s"$BankAccountNotFound Current Bank_Id(${bankIdAccountId.bankId}), Account_Id(${bankIdAccountId.accountId}) "
              moderatedAccount <- bankAccount.moderatedBankAccount(view, bankIdAccountId, Full(u), callContext) //Error handling is in lower method
            } yield {
              moderatedAccount
            }
            // if there are accountAttribute query parameter, link to corresponding accountAttributes.
            (accountAttributes: Option[List[AccountAttribute]], callContext) <- if(moderatedAccounts.nonEmpty && params.nonEmpty) {
              val futures: List[OBPReturnType[List[AccountAttribute]]] = availableBankIdAccountIdList2.map { bankIdAccount =>
                val BankIdAccountId(bId, accountId) = bankIdAccount
                NewStyle.function.getAccountAttributesByAccount(
                  bId,
                  accountId,
                  callContext: Option[CallContext])
              }
              Future.reduceLeft(futures){ (r, t) => // combine to one future
                r.copy(_1 = t._1 ::: t._1)
              } map (it => (Some(it._1), it._2)) // convert list to Option[List[AccountAttribute]]
            } else {
              Future.successful(None, callContext)
            }
          } yield {
            (JSONFactory400.createFirehoseCoreBankAccountJSON(moderatedAccounts, accountAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getFastFirehoseAccountsAtOneBank,
      implementedInApiVersion,
      nameOf(getFastFirehoseAccountsAtOneBank),
      "GET",
      "/management/banks/BANK_ID/fast-firehose/accounts",
      "Get Fast Firehose Accounts at Bank",
      s"""
         |
         |This endpoint allows bulk access to accounts.
         |
         |optional pagination parameters for filter with accounts
         |${urlParametersDocument(true, false)}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      fastFirehoseAccountsJsonV400,
      List($BankNotFound),
      List(apiTagAccount, apiTagAccountFirehose, apiTagFirehoseData, apiTagNewStyle),
      Some(List(canUseAccountFirehoseAtAnyBank, ApiRole.canUseAccountFirehose))
    )

    lazy val getFastFirehoseAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for all banks
      case "management":: "banks" :: BankId(bankId):: "fast-firehose" :: "accounts"  :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), bank, callContext) <- SS.userBank
            _ <- Helper.booleanToFuture(failMsg = AccountFirehoseNotAllowedOnThisInstance, cc=cc.callContext) {
              allowAccountFirehose
            }
            allowedParams = List("limit", "offset", "sort_direction")
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            obpQueryParams <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
            (firehoseAccounts, callContext) <- NewStyle.function.getBankAccountsWithAttributes(bankId, obpQueryParams, cc.callContext)
          } yield {
            (JSONFactory400.createFirehoseBankAccountJSON(firehoseAccounts), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
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
         |""",
      postCustomerPhoneNumberJsonV400,
      customerJsonV310,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc ,apiTagNewStyle))

    lazy val getCustomersByCustomerPhoneNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "search"  :: "customers" :: "mobile-phone-number" ::  Nil JsonPost  json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerPhoneNumberJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostCustomerPhoneNumberJsonV400]
            }
            (customers, callContext) <- NewStyle.function.getCustomersByCustomerPhoneNumber(bankId, postedData.mobile_phone_number , cc.callContext)
          } yield {
            (JSONFactory300.createCustomersJson(customers), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCurrentUserId,
      implementedInApiVersion,
      nameOf(getCurrentUserId),
      "GET",
      "/users/current/user_id",
      "Get User Id (Current)",
      s"""Get the USER_ID of the logged in user
         |
         |${authenticationRequiredMessage(true)}
      """.stripMargin,
      EmptyBody,
      userIdJsonV400,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagUser, apiTagNewStyle))

    lazy val getCurrentUserId: OBPEndpoint = {
      case "users" :: "current" :: "user_id" :: Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
          } yield {
            (JSONFactory400.createUserIdInfoJson(u), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUserByUserId,
      implementedInApiVersion,
      nameOf(getUserByUserId),
      "GET",
      "/users/user_id/USER_ID",
      "Get User by USER_ID",
      s"""Get user by USER_ID
         |
         |${authenticationRequiredMessage(true)}
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      userJsonWithAgreementsV400,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundById, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))


    lazy val getUserByUserId: OBPEndpoint = {
      case "users" :: "user_id" :: userId :: Nil JsonGet _ => {
        cc =>
          for {
            user <- Users.users.vend.getUserByUserIdFuture(userId) map {
              x => unboxFullOrFail(x, cc.callContext, s"$UserNotFoundByUserId Current UserId($userId)")
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
            acceptMarketingInfo <- NewStyle.function.getAgreementByUserId(user.userId, "accept_marketing_info", cc.callContext)
            termsAndConditions <- NewStyle.function.getAgreementByUserId(user.userId, "terms_and_conditions", cc.callContext)
            privacyConditions <- NewStyle.function.getAgreementByUserId(user.userId, "privacy_conditions", cc.callContext)
          } yield {
            val agreements = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
            (JSONFactory400.createUserInfoJSON(user, entitlements, Some(agreements)), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUserByUsername,
      implementedInApiVersion,
      nameOf(getUserByUsername),
      "GET",
      "/users/username/USERNAME",
      "Get User by USERNAME",
      s"""Get user by USERNAME
         |
         |${authenticationRequiredMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      emptyObjectJson,
      userJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByUsername, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))


    lazy val getUserByUsername: OBPEndpoint = {
      case "users" :: "username" :: username :: Nil JsonGet _ => {
        cc =>
          for {
            user <- Users.users.vend.getUserByUserNameFuture(username) map {
              x => unboxFullOrFail(x, cc.callContext, UserNotFoundByUsername, 404)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
          } yield {
            (JSONFactory400.createUserInfoJSON(user, entitlements, None), HttpCode.`200`(cc.callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getUsersByEmail,
      implementedInApiVersion,
      nameOf(getUsersByEmail),
      "GET",
      "/users/email/EMAIL/terminator",
      "Get Users by Email Address",
      s"""Get users by email address
         |
         |${authenticationRequiredMessage(true)}
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      emptyObjectJson,
      usersJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))


    lazy val getUsersByEmail: OBPEndpoint = {
      case "users" :: "email" :: email :: "terminator" :: Nil JsonGet _ => {
        cc =>
          for {
            users <- Users.users.vend.getUsersByEmail(email)
          } yield {
            (JSONFactory400.createUsersJson(users), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUsers,
      implementedInApiVersion,
      nameOf(getUsers),
      "GET",
      "/users",
      "Get all Users",
      s"""Get all users
         |
         |${authenticationRequiredMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
         |${urlParametersDocument(false, false)}
         |* locked_status (if null ignore)
         |
      """.stripMargin,
      emptyObjectJson,
      usersJsonV400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))

    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => {
        cc =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              x => unboxFullOrFail(x, cc.callContext, InvalidFilterParameterFormat)
            }
            users <- Users.users.vend.getUsers(obpQueryParams)
          } yield {
            (JSONFactory400.createUsersJson(users), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createUserInvitation,
      implementedInApiVersion,
      nameOf(createUserInvitation),
      "POST",
      "/banks/BANK_ID/user-invitation",
      "Create User Invitation",
      s"""Create User Invitation.
         |
         |""",
      userInvitationPostJsonV400,
      userInvitationJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagUserInvitation, apiTagKyc ,apiTagNewStyle),
      Some(canCreateUserInvitation :: Nil)
    )

    lazy val createUserInvitation : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitation" ::  Nil JsonPost  json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserInvitationJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostUserInvitationJsonV400]
            }
            (invitation, callContext) <- NewStyle.function.createUserInvitation(
              bankId, 
              postedData.first_name, 
              postedData.last_name, 
              postedData.email, 
              postedData.company, 
              postedData.country, 
              postedData.purpose, 
              cc.callContext)
          } yield {
            val subject = getWebUiPropsValue("webui_developer_user_invitation_email_subject", "Welcome to the API Playground")
            val from = getWebUiPropsValue("webui_developer_user_invitation_email_from", "do-not-reply@openbankproject.com")
            val link = s"${APIUtil.getPropsValue("portal_hostname", APIUtil.getPropsValue("hostname", ""))}/user-invitation?id=${invitation.secretKey}"
            val customText = getWebUiPropsValue("webui_developer_user_invitation_email_text", WebUITemplate.webUiDeveloperUserInvitationEmailText)
            val customHtmlText = getWebUiPropsValue("webui_developer_user_invitation_email_html_text", WebUITemplate.webUiDeveloperUserInvitationEmailHtmlText)
              .replace(WebUIPlaceholder.emailRecipient, invitation.firstName)
              .replace(WebUIPlaceholder.activateYourAccount, link)
            Mailer.sendMail(From(from), Subject(subject), To(invitation.email), PlainMailBodyType(customText), XHTMLMailBodyType(XML.loadString(customHtmlText)))
            (JSONFactory400.createUserInvitationJson(invitation), HttpCode.`201`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      getUserInvitationAnonymous,
      implementedInApiVersion,
      nameOf(getUserInvitationAnonymous),
      "POST",
      "/banks/BANK_ID/user-invitations",
      "Get User Invitation Information",
      s"""Create User Invitation Information.
         |
         |${authenticationRequiredMessage(false)}
         |""",
      PostUserInvitationAnonymousJsonV400(secret_key = 5819479115482092878L),
      userInvitationJsonV400,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagUserInvitation, apiTagKyc ,apiTagNewStyle)
    )

    lazy val getUserInvitationAnonymous : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitations" ::  Nil JsonPost  json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserInvitationAnonymousJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostUserInvitationAnonymousJsonV400]
            }
            (invitation, callContext) <- NewStyle.function.getUserInvitation(bankId, postedData.secret_key, cc.callContext)
            _ <- Helper.booleanToFuture(CannotFindUserInvitation, 404, cc.callContext) {
              invitation.status == "CREATED"
            }
            _ <- Helper.booleanToFuture(CannotFindUserInvitation, 404, cc.callContext) {
              val validUntil = Calendar.getInstance
              validUntil.setTime(invitation.createdAt.get)
              validUntil.add(Calendar.HOUR, 24)
              validUntil.getTime.after(new Date())
            }
          } yield {
            (JSONFactory400.createUserInvitationJson(invitation), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUserInvitation,
      implementedInApiVersion,
      nameOf(getUserInvitation),
      "GET",
      "/banks/BANK_ID/user-invitations/SECRET_LINK",
      "Get User Invitation",
      s""" Get User Invitation
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      userInvitationJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUserInvitation, apiTagNewStyle),
      Some(List(canGetUserInvitation))
    )

    lazy val getUserInvitation : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitations" :: secretLink :: Nil JsonGet _ => {
        cc =>
          for {
            (invitation, callContext) <- NewStyle.function.getUserInvitation(bankId, secretLink.toLong, cc.callContext)
          } yield {
            (JSONFactory400.createUserInvitationJson(invitation), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getUserInvitations,
      implementedInApiVersion,
      nameOf(getUserInvitations),
      "GET",
      "/banks/BANK_ID/user-invitations",
      "Get User Invitations",
      s""" Get User Invitations
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      userInvitationJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUserInvitation, apiTagNewStyle),
      Some(List(canGetUserInvitation))
    )

    lazy val getUserInvitations : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitations" :: Nil JsonGet _ => {
        cc =>
          for {
            (invitations, callContext) <- NewStyle.function.getUserInvitations(bankId, cc.callContext)
          } yield {
            (JSONFactory400.createUserInvitationJson(invitations), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteUser,
      implementedInApiVersion,
      nameOf(deleteUser),
      "DELETE",
      "/users/USER_ID",
      "Delete a User",
      s"""Delete a User.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canDeleteUser)))

    lazy val deleteUser : OBPEndpoint = {
      case "users" :: userId :: Nil JsonDelete _ => {
        cc =>
          for {
            (user, callContext) <- NewStyle.function.findByUserId(userId, cc.callContext)
            (userDeleted, callContext) <- NewStyle.function.deleteUser(user.userPrimaryKey, callContext)
          } yield {
            (Full(userDeleted), HttpCode.`200`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      createBank,
      implementedInApiVersion,
      "createBank",
      "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |
         |The user creating this will be automatically assigned the Role CanCreateEntitlementAtOneBank.
         |Thus the User can manage the bank they create and assign Roles to other Users.
         |
         |""",
      bankJson400,
      bankJson400,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $BankJson400 "
          for {
            bank <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[BankJson400]
            }
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc=cc.callContext) {
              cc.callContext.map(_.consumer.isDefined == true).isDefined
            }

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc=cc.callContext) {
              bank.id.length > 3
            }

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc=cc.callContext) {
              !bank.id.contains(" ")
            }

            (success, callContext) <- NewStyle.function.createOrUpdateBank(
              bank.id,
              bank.full_name,
              bank.short_name,
              bank.logo,
              bank.website,
              bank.bank_routings.find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              bank.bank_routings.filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              bank.bank_routings.filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              cc.callContext
              )
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, callContext)
            entitlementsByBank = entitlements.filter(_.bankId==bank.id)
            _ <- entitlementsByBank.filter(_.roleName == CanCreateEntitlementAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(bank.id, cc.userId, CanCreateEntitlementAtOneBank.toString()))
            }
          } yield {
            (JSONFactory400.createBankJSON400(success), HttpCode.`201`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      createDirectDebit,
      implementedInApiVersion,
      nameOf(createDirectDebit),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/direct-debit",
      "Create Direct Debit",
      s"""Create direct debit for an account.
         |
         |""",
      postDirectDebitJsonV400,
      directDebitJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        NoViewPermission,
        $UserNoPermissionAccessView,
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        CounterpartyNotFoundByCounterpartyId,
        UnknownError
      ),
      List(apiTagDirectDebit, apiTagAccount, apiTagNewStyle))

    lazy val createDirectDebit : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "direct-debit" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (view, callContext) <- SS.view
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_create_direct_debit. Current ViewId($viewId)", cc=callContext) {
              view.canCreateDirectDebit
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

    staticResourceDocs += ResourceDoc(
      createDirectDebitManagement,
      implementedInApiVersion,
      nameOf(createDirectDebitManagement),
      "POST",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/direct-debit",
      "Create Direct Debit (management)",
      s"""Create direct debit for an account.
         |
         |""",
      postDirectDebitJsonV400,
      directDebitJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        CounterpartyNotFoundByCounterpartyId,
        UnknownError
      ),
      List(apiTagDirectDebit, apiTagAccount, apiTagNewStyle),
      Some(List(canCreateDirectDebitAtOneBank))
    )

    lazy val createDirectDebitManagement : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "direct-debit" ::  Nil JsonPost  json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostDirectDebitJsonV400 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostDirectDebitJsonV400]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, cc.callContext)
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

    staticResourceDocs += ResourceDoc(
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
         |""",
      postStandingOrderJsonV400,
      standingOrderJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        InvalidNumber,
        InvalidISOCurrencyCode,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        $UserNoPermissionAccessView,
        UnknownError
      ),
      List(apiTagStandingOrder, apiTagAccount, apiTagNewStyle))

    lazy val createStandingOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "standing-order" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (view, callContext) <- SS.view
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_create_standing_order. Current ViewId($viewId)", cc=callContext) {
              view.canCreateStandingOrder
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostStandingOrderJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostStandingOrderJsonV400]
            }
            amountValue <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, callContext) {
              BigDecimal(postJson.amount.amount)
            }
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'", cc=callContext) {
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

    staticResourceDocs += ResourceDoc(
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
         |
         |""",
      postStandingOrderJsonV400,
      standingOrderJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        NoViewPermission,
        InvalidJsonFormat,
        InvalidNumber,
        InvalidISOCurrencyCode,
        CustomerNotFoundByCustomerId,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagStandingOrder, apiTagAccount, apiTagNewStyle),
      Some(List(canCreateStandingOrderAtOneBank))
    )

    lazy val createStandingOrderManagement : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "standing-order" ::  Nil JsonPost  json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostStandingOrderJsonV400 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostStandingOrderJsonV400]
            }
            amountValue <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, cc.callContext) {
              BigDecimal(postJson.amount.amount)
            }
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'", cc=cc.callContext) {
              isValidCurrencyISOCode(postJson.amount.currency)
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, cc.callContext)
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



    staticResourceDocs += ResourceDoc(
      grantUserAccessToView,
      implementedInApiVersion,
      "grantUserAccessToView",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access/grant",
      "Grant User access to View",
      s"""Grants the User identified by USER_ID access to the view identified by VIEW_ID.
         |
         |${authenticationRequiredMessage(true)} and the user needs to be account holder.
         |
         |""",
      postAccountAccessJsonV400,
      viewJsonV300,
      List(
        $UserNotLoggedIn,
        UserMissOwnerViewOrNotAccountHolder,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotGrantAccountAccess,
        UnknownError
      ),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired, apiTagNewStyle))

    lazy val grantUserAccessToView : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: "grant" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostAccountAccessJsonV400]
            }
            _ <- NewStyle.function.canGrantAccessToView(bankId, accountId, cc.loggedInUser, cc.callContext)
            (user, callContext) <- NewStyle.function.findByUserId(postJson.user_id, cc.callContext)
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


    staticResourceDocs += ResourceDoc(
      revokeUserAccessToView,
      implementedInApiVersion,
      "revokeUserAccessToView",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access/revoke",
      "Revoke User access to View",
      s"""Revoke the User identified by USER_ID access to the view identified by VIEW_ID.
         |
         |${authenticationRequiredMessage(true)} and the user needs to be account holder.
         |
         |""",
      postAccountAccessJsonV400,
      revokedJsonV400,
      List(
        $UserNotLoggedIn,
        UserMissOwnerViewOrNotAccountHolder,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotRevokeAccountAccess,
        CannotFindAccountAccess,
        UnknownError
      ),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired, apiTagNewStyle))

    lazy val revokeUserAccessToView : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: "revoke" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostAccountAccessJsonV400]
            }
            _ <- NewStyle.function.canRevokeAccessToView(bankId, accountId, cc.loggedInUser, cc.callContext)
            (user, callContext) <- NewStyle.function.findByUserId(postJson.user_id, cc.callContext)
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

    staticResourceDocs += ResourceDoc(
      revokeGrantUserAccessToViews,
      implementedInApiVersion,
      "revokeGrantUserAccessToViews",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access",
      "Revoke/Grant User access to View",
      s"""Revoke/Grant the logged in User access to the views identified by json.
         |
         |${authenticationRequiredMessage(true)} and the user needs to be an account holder or has owner view access.
         |
         |""",
      postRevokeGrantAccountAccessJsonV400,
      revokedJsonV400,
      List(
        $UserNotLoggedIn,
        UserMissOwnerViewOrNotAccountHolder,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotRevokeAccountAccess,
        CannotFindAccountAccess,
        UnknownError
      ),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val revokeGrantUserAccessToViews : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: Nil JsonPut json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostRevokeGrantAccountAccessJsonV400]
            }
            _ <- NewStyle.function.canRevokeAccessToView(bankId, accountId, cc.loggedInUser, cc.callContext)
            (user, callContext) <- NewStyle.function.findByUserId(cc.loggedInUser.userId, cc.callContext)
           _ <- Future(Views.views.vend.revokeAccountAccessesByUser(bankId, accountId, user)) map {
              unboxFullOrFail(_, callContext, s"Cannot revoke")
            }
            grantViews = for (viewId <- postJson.views) yield ViewIdBankIdAccountId(ViewId(viewId), bankId, accountId)
            _ <- Future(Views.views.vend.grantAccessToMultipleViews(grantViews, user)) map {
              unboxFullOrFail(_, callContext, s"Cannot grant the views: ${postJson.views.mkString(",")}")
            }
          } yield {
            (RevokedJsonV400(true), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createCustomerAttribute,
      implementedInApiVersion,
      nameOf(createCustomerAttribute),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/attribute",
      "Create Customer Attribute",
      s""" Create Customer Attribute
         |
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      customerAttributeJsonV400,
      customerAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canCreateCustomerAttributeAtOneBank)))

    lazy val createCustomerAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $CustomerAttributeJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[CustomerAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${CustomerAttributeType.DOUBLE}(12.1234), ${CustomerAttributeType.STRING}(TAX_NUMBER), ${CustomerAttributeType.INTEGER}(123) and ${CustomerAttributeType.DATE_WITH_DAY}(2012-04-23)"
            customerAttributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              CustomerAttributeType.withName(postedData.`type`)
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            _ <-  Helper.booleanToFuture(InvalidCustomerBankId.replaceAll("Bank Id.",s"Bank Id ($bankId).").replaceAll("The Customer",s"The Customer($customerId)"), cc=callContext){customer.bankId == bankId}
            (accountAttribute, callContext) <- NewStyle.function.createOrUpdateCustomerAttribute(
              BankId(bankId),
              CustomerId(customerId),
              None,
              postedData.name,
              customerAttributeType,
              postedData.value,
              callContext
            )
          } yield {
            (JSONFactory400.createCustomerAttributeJson(accountAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateCustomerAttribute,
      implementedInApiVersion,
      nameOf(updateCustomerAttribute),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/attributes/CUSTOMER_ATTRIBUTE_ID",
      "Update Customer Attribute",
      s""" Update Customer Attribute
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      customerAttributeJsonV400,
      customerAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canUpdateCustomerAttributeAtOneBank))
    )

    lazy val updateCustomerAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attributes" :: customerAttributeId :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $CustomerAttributeJsonV400"
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[CustomerAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${CustomerAttributeType.DOUBLE}(12.1234), ${CustomerAttributeType.STRING}(TAX_NUMBER), ${CustomerAttributeType.INTEGER}(123) and ${CustomerAttributeType.DATE_WITH_DAY}(2012-04-23)"
            customerAttributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              CustomerAttributeType.withName(postedData.`type`)
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            _ <-  Helper.booleanToFuture(InvalidCustomerBankId.replaceAll("Bank Id.",s"Bank Id ($bankId).").replaceAll("The Customer",s"The Customer($customerId)"), cc=callContext){customer.bankId == bankId}
            (accountAttribute, callContext) <- NewStyle.function.getCustomerAttributeById(
              customerAttributeId,
              callContext
            )
            (accountAttribute, callContext) <- NewStyle.function.createOrUpdateCustomerAttribute(
              BankId(bankId),
              CustomerId(customerId),
              Some(customerAttributeId),
              postedData.name,
              customerAttributeType,
              postedData.value,
              callContext
            )
          } yield {
            (JSONFactory400.createCustomerAttributeJson(accountAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerAttributes,
      implementedInApiVersion,
      nameOf(getCustomerAttributes),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/attributes",
      "Get Customer Attributes",
      s""" Get Customer Attributes
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customerAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomerAttributesAtOneBank))
    )

    lazy val getCustomerAttributes : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attributes" :: Nil JsonGet _ => {
        cc =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            _ <-  Helper.booleanToFuture(InvalidCustomerBankId.replaceAll("Bank Id.",s"Bank Id ($bankId).").replaceAll("The Customer",s"The Customer($customerId)"), cc=callContext){customer.bankId == bankId}
            (accountAttribute, callContext) <- NewStyle.function.getCustomerAttributes(
              BankId(bankId),
              CustomerId(customerId),
              callContext
            )
          } yield {
            (JSONFactory400.createCustomerAttributesJson(accountAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerAttributeById,
      implementedInApiVersion,
      nameOf(getCustomerAttributeById),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/attributes/ATTRIBUTE_ID",
      "Get Customer Attribute By Id",
      s""" Get Customer Attribute By Id
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customerAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomerAttributeAtOneBank))
    )

    lazy val getCustomerAttributeById : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attributes" :: customerAttributeId ::Nil JsonGet _ => {
        cc =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            _ <-  Helper.booleanToFuture(InvalidCustomerBankId.replaceAll("Bank Id.",s"Bank Id ($bankId).").replaceAll("The Customer",s"The Customer($customerId)"), cc=callContext){customer.bankId == bankId}
            (accountAttribute, callContext) <- NewStyle.function.getCustomerAttributeById(
              customerAttributeId,
              callContext
            )
          } yield {
            (JSONFactory400.createCustomerAttributeJson(accountAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomersByAttributes,
      implementedInApiVersion,
      nameOf(getCustomersByAttributes),
      "GET",
      "/banks/BANK_ID/customers",
      "Get Customers by ATTRIBUTES",
      s"""Gets the Customers specified by attributes
         |
         |URL params example: /banks/some-bank-id/customers?name=John&age=8
         |URL params example: /banks/some-bank-id/customers?manager=John&count=8
         |
         |
         |""",
      EmptyBody,
      ListResult(
        "customers",
        List(customerWithAttributesJsonV310)
      ),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomer))
    )

    lazy val getCustomersByAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" ::  Nil JsonGet req => {
        cc =>
          for {
            (customerIds, callContext) <- NewStyle.function.getCustomerIdsByAttributeNameValues(bankId, req.params, Some(cc))
            list: List[CustomerWithAttributesJsonV310] <- {
              val listCustomerFuture: List[Future[CustomerWithAttributesJsonV310]] = customerIds.map{ customerId =>
                val customerFuture = NewStyle.function.getCustomerByCustomerId(customerId.value, callContext)
                customerFuture.flatMap { customerAndCc =>
                  val (customer, cc) = customerAndCc
                  NewStyle.function.getCustomerAttributes(bankId, customerId, cc).map { attributesAndCc =>
                    val (attributes, _) = attributesAndCc
                    JSONFactory310.createCustomerWithAttributesJson(customer, attributes)
                  }
                }
              }
              Future.sequence(listCustomerFuture)
            }
          } yield {
            (ListResult("customers", list), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createTransactionAttribute,
      implementedInApiVersion,
      nameOf(createTransactionAttribute),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attribute",
      "Create Transaction Attribute",
      s""" Create Transaction Attribute
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      transactionAttributeJsonV400,
      transactionAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canCreateTransactionAttributeAtOneBank)))

    lazy val createTransactionAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: TransactionId(transactionId) :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionAttributeJsonV400 "
          for {
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              json.extract[TransactionAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionAttributeType.DOUBLE}(12.1234), ${TransactionAttributeType.STRING}(TAX_NUMBER), ${TransactionAttributeType.INTEGER} (123)and ${TransactionAttributeType.DATE_WITH_DAY}(2012-04-23)"
            transactionAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              TransactionAttributeType.withName(postedData.`type`)
            }
            (accountAttribute, callContext) <- NewStyle.function.createOrUpdateTransactionAttribute(
              bankId,
              transactionId,
              None,
              postedData.name,
              transactionAttributeType,
              postedData.value,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionAttributeJson(accountAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateTransactionAttribute,
      implementedInApiVersion,
      nameOf(updateTransactionAttribute),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attributes/ACCOUNT_ATTRIBUTE_ID",
      "Update Transaction Attribute",
      s""" Update Transaction Attribute
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      transactionAttributeJsonV400,
      transactionAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canUpdateTransactionAttributeAtOneBank))
    )

    lazy val updateTransactionAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: TransactionId(transactionId) :: "attributes" :: transactionAttributeId :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionAttributeJsonV400"
          for {
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              json.extract[TransactionAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionAttributeType.DOUBLE}(12.1234), ${TransactionAttributeType.STRING}(TAX_NUMBER), ${TransactionAttributeType.INTEGER} (123)and ${TransactionAttributeType.DATE_WITH_DAY}(2012-04-23)"
            transactionAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              TransactionAttributeType.withName(postedData.`type`)
            }
            (_, callContext) <- NewStyle.function.getTransactionAttributeById(transactionAttributeId, callContext)
            (transactionAttribute, callContext) <- NewStyle.function.createOrUpdateTransactionAttribute(
              bankId,
              transactionId,
              Some(transactionAttributeId),
              postedData.name,
              transactionAttributeType,
              postedData.value,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionAttributeJson(transactionAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getTransactionAttributes,
      implementedInApiVersion,
      nameOf(getTransactionAttributes),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attributes",
      "Get Transaction Attributes",
      s""" Get Transaction Attributes
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canGetTransactionAttributesAtOneBank))
    )

    lazy val getTransactionAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: TransactionId(transactionId) :: "attributes" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            (accountAttribute, callContext) <- NewStyle.function.getTransactionAttributes(
              bankId,
              transactionId,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionAttributesJson(accountAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getTransactionAttributeById,
      implementedInApiVersion,
      nameOf(getTransactionAttributeById),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID/attributes/ATTRIBUTE_ID",
      "Get Transaction Attribute By Id",
      s""" Get Transaction Attribute By Id
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canGetTransactionAttributeAtOneBank))
    )

    lazy val getTransactionAttributeById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) ::  "transactions" :: TransactionId(transactionId) :: "attributes" :: transactionAttributeId :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            (accountAttribute, callContext) <- NewStyle.function.getTransactionAttributeById(
              transactionAttributeId,
              callContext
            )
          } yield {
            (JSONFactory400.createTransactionAttributeJson(accountAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createHistoricalTransactionAtBank,
      implementedInApiVersion,
      nameOf(createHistoricalTransactionAtBank),
      "POST",
      "/banks/BANK_ID/management/historical/transactions",
      "Create Historical Transactions ",
      s"""
         |Create historical transactions at one Bank
         |
         |Use this endpoint to create transactions between any two accounts at the same bank. 
         |From account and to account must be at the same bank.
         |Example:
         |{
         |  "from_account_id": "1ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "to_account_id": "2ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "value": {
         |    "currency": "GBP",
         |    "amount": "10"
         |  },
         |  "description": "this is for work",
         |  "posted": "2017-09-19T02:31:05Z",
         |  "completed": "2017-09-19T02:31:05Z",
         |  "type": "SANDBOX_TAN",
         |  "charge_policy": "SHARED"
         |}
         |
         |This call is experimental.
       """.stripMargin,
      postHistoricalTransactionAtBankJson,
      postHistoricalTransactionResponseJson,
      List(
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        CounterpartyNotFoundByCounterpartyId,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canCreateHistoricalTransactionAtBank))
    )


    lazy val createHistoricalTransactionAtBank : OBPEndpoint =  {
      case "banks" :: BankId(bankId) :: "management"  :: "historical" :: "transactions" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateHistoricalTransactionAtBank, callContext)

            // Check the input JSON format, here is just check the common parts of all four types
            transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostHistoricalTransactionJson ", 400, callContext) {
              json.extract[PostHistoricalTransactionAtBankJson]
            }
            (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, AccountId(transDetailsJson.from_account_id), callContext)
            (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, AccountId(transDetailsJson.to_account_id), callContext)
            amountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is ${transDetailsJson.value.amount} ", 400, callContext) {
              BigDecimal(transDetailsJson.value.amount)
            }
            _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${amountNumber}'", cc=callContext) {
              amountNumber > BigDecimal("0")
            }
            posted <- NewStyle.function.tryons(s"$InvalidDateFormat Current `posted` field is ${transDetailsJson.posted}. Please use this format ${DateWithSecondsFormat.toPattern}! ", 400, callContext) {
              new SimpleDateFormat(DateWithSeconds).parse(transDetailsJson.posted)
            }
            completed <- NewStyle.function.tryons(s"$InvalidDateFormat Current `completed` field  is ${transDetailsJson.completed}. Please use this format ${DateWithSecondsFormat.toPattern}! ", 400, callContext) {
              new SimpleDateFormat(DateWithSeconds).parse(transDetailsJson.completed)
            }
            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc=callContext) {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }
            amountOfMoneyJson = AmountOfMoneyJsonV121(transDetailsJson.value.currency, transDetailsJson.value.amount)
            chargePolicy = transDetailsJson.charge_policy
            //There is no constraint for the type at the moment  
            transactionType = transDetailsJson.`type`
            (transactionId, callContext) <- NewStyle.function.makeHistoricalPayment(
              fromAccount,
              toAccount,
              posted,
              completed,
              amountNumber,
              transDetailsJson.value.currency,
              transDetailsJson.description,
              transactionType,
              chargePolicy,
              callContext
            )
          } yield {
            (JSONFactory400.createPostHistoricalTransactionResponseJson(
              bankId,
              transactionId,
              fromAccount.accountId,
              toAccount.accountId,
              value= amountOfMoneyJson,
              description = transDetailsJson.description,
              posted,
              completed,
              transactionRequestType = transactionType,
              chargePolicy =transDetailsJson.charge_policy), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getTransactionRequest,
      implementedInApiVersion,
      nameOf(getTransactionRequest),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests/TRANSACTION_REQUEST_ID",
      "Get Transaction Request." ,
      """Returns transaction request for transaction specified by TRANSACTION_REQUEST_ID and for account specified by ACCOUNT_ID at bank specified by BANK_ID.
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
      transactionRequestWithChargeJSON210,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UserNoOwnerView,
        GetTransactionRequestsException,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2, apiTagNewStyle))

    lazy val getTransactionRequest: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: TransactionRequestId(requestId) :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
            _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView, cc=callContext) {
              u.hasOwnerViewAccess(BankIdAccountId(bankId,accountId))
            }
            (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(requestId, callContext)
          } yield {
            val json = JSONFactory210.createTransactionRequestWithChargeJSON(transactionRequest)
            (json, HttpCode.`200`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      getPrivateAccountsAtOneBank,
      implementedInApiVersion,
      "getPrivateAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts",
      "Get Accounts at Bank",
      s"""
         |Returns the list of accounts at BANK_ID that the user has access to.
         |For each account the API returns the account ID and the views available to the user..
         |Each account must have at least one private View.
         |
         |optional request parameters for filter with attributes
         |URL params example: /banks/some-bank-id/accounts?manager=John&count=8
         |
         |
      """.stripMargin,
      EmptyBody,
      basicAccountsJSON,
      List($UserNotLoggedIn, $BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData, apiTagNewStyle)
    )

    lazy val getPrivateAccountsAtOneBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), bank, callContext) <- SS.userBank
            (privateViewsUserCanAccessAtOneBank, privateAccountAccesses) = Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)
            params = req.params
            privateAccountAccesses2 <- if(params.isEmpty || privateAccountAccesses.isEmpty) {
              Future.successful(privateAccountAccesses)
            } else {
              AccountAttributeX.accountAttributeProvider.vend
                .getAccountIdsByParams(bankId, req.params)
                .map { boxedAccountIds =>
                  val accountIds = boxedAccountIds.getOrElse(Nil)
                  privateAccountAccesses.filter(aa => accountIds.contains(aa.account_id.get))
                }
            }
            (availablePrivateAccounts, callContext) <- bank.privateAccountsFuture(privateAccountAccesses2, callContext)
          } yield {
            val bankAccounts = Implementations2_0_0.processAccounts(privateViewsUserCanAccessAtOneBank, availablePrivateAccounts)
            (bankAccounts, HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createConsumer,
      implementedInApiVersion,
      "createConsumer",
      "POST",
      "/management/consumers",
      "Post a Consumer",
      s"""Create a Consumer (Authenticated access).
         |
         |""",
      ConsumerPostJSON(
        "Test",
        "Test",
        "Description",
        "some@email.com",
        "redirecturl",
        "createdby",
        true,
        new Date(),
        """-----BEGIN CERTIFICATE-----
          |client_certificate_content
          |-----END CERTIFICATE-----""".stripMargin
      ),
      consumerJsonV400,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagConsumer, apiTagNewStyle),
      Some(List(canCreateConsumer)))


    lazy val createConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            postedJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) {
              json.extract[ConsumerPostJSON]
            }
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canCreateConsumer, callContext)
            (consumer, callContext) <- createConsumerNewStyle(
              key = Some(Helpers.randomString(40).toLowerCase),
              secret = Some(Helpers.randomString(40).toLowerCase),
              isActive = Some(postedJson.enabled),
              name= Some(postedJson.app_name),
              appType = None,
              description = Some(postedJson.description),
              developerEmail = Some(postedJson.developer_email),
              redirectURL = Some(postedJson.redirect_url),
              createdByUserId = Some(u.userId),
              clientCertificate = Some(postedJson.clientCertificate),
              callContext
            )
            user <- Users.users.vend.getUserByUserIdFuture(u.userId)
          } yield {
            // Format the data as json
            val json = JSONFactory400.createConsumerJSON(consumer, user)
            // Return
            (json, HttpCode.`201`(callContext))
          }
      }
    }

    val customerAttributeGeneralInfo =
      s"""
         |CustomerAttributes are used to enhance the OBP Customer object with Bank specific entities.
         |
       """.stripMargin

    staticResourceDocs += ResourceDoc(
      deleteCustomerAttribute,
      implementedInApiVersion,
      nameOf(deleteCustomerAttribute),
      "DELETE",
      "/banks/BANK_ID/CUSTOMER_ID/attributes/CUSTOMER_ATTRIBUTE_ID",
      "Delete Customer Attribute",
      s""" Delete Customer Attribute
         |
         |$customerAttributeGeneralInfo
         |
         |Delete a Customer Attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canDeleteCustomerAttributeAtOneBank)))

    lazy val deleteCustomerAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: "attributes" :: customerAttributeId ::  Nil JsonDelete _=> {
        cc =>
          for {
            (customerAttribute, callContext) <- NewStyle.function.deleteCustomerAttribute(customerAttributeId, cc.callContext)
          } yield {
            (Full(customerAttribute), HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createDynamicEndpoint,
      implementedInApiVersion,
      nameOf(createDynamicEndpoint),
      "POST",
      "/management/dynamic-endpoints",
      "Create Dynamic Endpoint",
      s"""Create dynamic endpoints.
         |
         |Create dynamic endpoints with one json format swagger content.
         |
         |If the host of swagger is `dynamic_entity`, then you need link the swagger fields to the dynamic entity fields, 
         |please check `Endpoint Mapping` endpoints.
         |
         |If the host of swagger is `obp_mock`, every dynamic endpoint will return example response of swagger,\n
         |when create MethodRouting for given dynamic endpoint, it will be routed to given url.
         |
         |""",
      dynamicEndpointRequestBodyExample,
      dynamicEndpointResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEndpointExists,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canCreateDynamicEndpoint)))

    lazy val createDynamicEndpoint: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: Nil JsonPost json -> _ => {
        cc =>
          createDynamicEndpointMethod(None, json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      createBankLevelDynamicEndpoint,
      implementedInApiVersion,
      nameOf(createBankLevelDynamicEndpoint),
      "POST",
      "/management/banks/BANK_ID/dynamic-endpoints",
      "Create Bank Level Dynamic Endpoint",
      s"""Create dynamic endpoints.
         |
         |Create dynamic endpoints with one json format swagger content.
         |
         |If the host of swagger is `dynamic_entity`, then you need link the swagger fields to the dynamic entity fields, 
         |please check `Endpoint Mapping` endpoints.
         |
         |If the host of swagger is `obp_mock`, every dynamic endpoint will return example response of swagger,\n
         |when create MethodRouting for given dynamic endpoint, it will be routed to given url.
         |
         |""",
      dynamicEndpointRequestBodyExample,
      dynamicEndpointResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEndpointExists,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canCreateBankLevelDynamicEndpoint, canCreateDynamicEndpoint)))

    lazy val createBankLevelDynamicEndpoint: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) ::"dynamic-endpoints" :: Nil JsonPost json -> _ => {
        cc =>
          createDynamicEndpointMethod(Some(bankId.value), json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      updateDynamicEndpointHost,
      implementedInApiVersion,
      nameOf(updateDynamicEndpointHost),
      "PUT",
      "/management/dynamic-endpoints/DYNAMIC_ENDPOINT_ID/host",
      " Update Dynamic Endpoint Host",
      s"""Update dynamic endpoint Host.
         |The value can be obp_mock, dynamic_entity, or some service url.
         |""",
      dynamicEndpointHostJson400,
      dynamicEndpointHostJson400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEntityNotFoundByDynamicEntityId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateDynamicEndpoint)))

    lazy val updateDynamicEndpointHost: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: dynamicEndpointId :: "host" :: Nil JsonPut json -> _ => {
        cc =>
          updateDynamicEndpointHostMethod(None, dynamicEndpointId, json, cc)
      }
    }

    private def updateDynamicEndpointHostMethod(bankId: Option[String], dynamicEndpointId: String, json: JValue, cc: CallContext) = {
      for {
        (_, callContext) <- NewStyle.function.getDynamicEndpoint(bankId, dynamicEndpointId, cc.callContext)
        failMsg = s"$InvalidJsonFormat The Json body should be the $DynamicEndpointHostJson400"
        postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
          json.extract[DynamicEndpointHostJson400]
        }
        (dynamicEndpoint, callContext) <- NewStyle.function.updateDynamicEndpointHost(bankId, dynamicEndpointId, postedData.host, cc.callContext)
      } yield {
        (postedData, HttpCode.`201`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      updateBankLevelDynamicEndpointHost,
      implementedInApiVersion,
      nameOf(updateBankLevelDynamicEndpointHost),
      "PUT",
      "/management/banks/BANK_ID/dynamic-endpoints/DYNAMIC_ENDPOINT_ID/host",
      " Update Bank Level Dynamic Endpoint Host",
      s"""Update Bank Level  dynamic endpoint Host.
         |The value can be obp_mock, dynamic_entity, or some service url.
         |""",
      dynamicEndpointHostJson400,
      dynamicEndpointHostJson400,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEntityNotFoundByDynamicEntityId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateBankLevelDynamicEndpoint, canUpdateDynamicEndpoint)))

    lazy val updateBankLevelDynamicEndpointHost: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: dynamicEndpointId :: "host" :: Nil JsonPut json -> _ => {
        cc =>
          updateDynamicEndpointHostMethod(Some(bankId), dynamicEndpointId, json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      getDynamicEndpoint,
      implementedInApiVersion,
      nameOf(getDynamicEndpoint),
      "GET",
      "/management/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      "Get Dynamic Endpoint",
      s"""Get a Dynamic Endpoint.
         |
         |
         |Get one DynamicEndpoint,
         |
         |""",
      EmptyBody,
      dynamicEndpointResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEndpointNotFoundByDynamicEndpointId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canGetDynamicEndpoint)))

    lazy val getDynamicEndpoint: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: dynamicEndpointId :: Nil JsonGet req => {
        cc =>
          getDynamicEndpointMethod(None, dynamicEndpointId, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      getDynamicEndpoints,
      implementedInApiVersion,
      nameOf(getDynamicEndpoints),
      "GET",
      "/management/dynamic-endpoints",
      " Get Dynamic Endpoints",
      s"""
         |
         |Get Dynamic Endpoints.
         |
         |""",
      EmptyBody,
      ListResult(
        "dynamic_endpoints",
        List(dynamicEndpointResponseBodyExample)
      ),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canGetDynamicEndpoints)))

    lazy val getDynamicEndpoints: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: Nil JsonGet _ => {
        cc =>
          getDynamicEndpointsMethod(None, cc)
      }
    }

    private def getDynamicEndpointsMethod(bankId: Option[String], cc: CallContext) = {
      for {
        (dynamicEndpoints, _) <- NewStyle.function.getDynamicEndpoints(bankId, cc.callContext)
      } yield {
        val resultList = dynamicEndpoints.map[JObject, List[JObject]] { dynamicEndpoint =>
          val swaggerJson = parse(dynamicEndpoint.swaggerString)
          ("user_id", cc.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~ ("swagger_string", swaggerJson)
        }
        (ListResult("dynamic_endpoints", resultList), HttpCode.`200`(cc.callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelDynamicEndpoint,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicEndpoint),
      "GET",
      "/management/banks/BANK_ID/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      " Get Bank Level Dynamic Endpoint",
      s"""Get a Bank Level Dynamic Endpoint.
         |""",
      EmptyBody,
      dynamicEndpointResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEndpointNotFoundByDynamicEndpointId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canGetBankLevelDynamicEndpoint, canGetDynamicEndpoint)))

    lazy val getBankLevelDynamicEndpoint: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: dynamicEndpointId :: Nil JsonGet req => {
        cc =>
          getDynamicEndpointMethod(Some(bankId), dynamicEndpointId, cc)
      }
    }

    private def getDynamicEndpointMethod(bankId: Option[String], dynamicEndpointId: String, cc: CallContext) = {
      for {
        (dynamicEndpoint, callContext) <- NewStyle.function.getDynamicEndpoint(bankId, dynamicEndpointId, cc.callContext)
      } yield {
        val swaggerJson = parse(dynamicEndpoint.swaggerString)
        val responseJson: JObject = ("user_id", cc.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~ ("swagger_string", swaggerJson)
        (responseJson, HttpCode.`200`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelDynamicEndpoints,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicEndpoints),
      "GET",
      "/management/banks/BANK_ID/dynamic-endpoints",
      "Get Bank Level Dynamic Endpoints",
      s"""
         |
         |Get Bank Level Dynamic Endpoints.
         |
         |""",
      EmptyBody,
      ListResult(
        "dynamic_endpoints",
        List(dynamicEndpointResponseBodyExample)
      ),
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canGetBankLevelDynamicEndpoints, canGetDynamicEndpoints)))

    lazy val getBankLevelDynamicEndpoints: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: Nil JsonGet _ => {
        cc =>
          getDynamicEndpointsMethod(Some(bankId), cc)
      }
    }

    private def deleteDynamicEndpointMethod(bankId: Option[String], dynamicEndpointId: String, cc: CallContext) = {
      for {
        deleted <- NewStyle.function.deleteDynamicEndpoint(bankId, dynamicEndpointId, cc.callContext)
      } yield {
        (deleted, HttpCode.`204`(cc.callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteDynamicEndpoint,
      implementedInApiVersion,
      nameOf(deleteDynamicEndpoint),
      "DELETE",
      "/management/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      " Delete Dynamic Endpoint",
      s"""Delete a DynamicEndpoint specified by DYNAMIC_ENDPOINT_ID.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        DynamicEndpointNotFoundByDynamicEndpointId,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteDynamicEndpoint)))

    lazy val deleteDynamicEndpoint : OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: dynamicEndpointId ::  Nil JsonDelete _ => {
        cc =>
          deleteDynamicEndpointMethod(None, dynamicEndpointId, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteBankLevelDynamicEndpoint,
      implementedInApiVersion,
      nameOf(deleteBankLevelDynamicEndpoint),
      "DELETE",
      "/management/banks/BANK_ID/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      " Delete Bank Level Dynamic Endpoint",
      s"""Delete a Bank Level DynamicEndpoint specified by DYNAMIC_ENDPOINT_ID.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        DynamicEndpointNotFoundByDynamicEndpointId,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteBankLevelDynamicEndpoint ,canDeleteDynamicEndpoint)))

    lazy val deleteBankLevelDynamicEndpoint : OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: dynamicEndpointId ::  Nil JsonDelete _ => {
        cc =>
          deleteDynamicEndpointMethod(Some(bankId), dynamicEndpointId, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyDynamicEndpoints,
      implementedInApiVersion,
      nameOf(getMyDynamicEndpoints),
      "GET",
      "/my/dynamic-endpoints",
      "Get My Dynamic Endpoints",
      s"""Get My Dynamic Endpoints.""".stripMargin,
      EmptyBody,
      ListResult(
        "dynamic_endpoints",
        List(dynamicEndpointResponseBodyExample)
      ),
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle)
    )

    lazy val getMyDynamicEndpoints: OBPEndpoint = {
      case "my" :: "dynamic-endpoints" :: Nil JsonGet _ => {
        cc =>
          for {
            (dynamicEndpoints, _) <- NewStyle.function.getDynamicEndpointsByUserId(cc.userId, cc.callContext)
          } yield {
            val resultList = dynamicEndpoints.map[JObject, List[JObject]] { dynamicEndpoint=>
              val swaggerJson = parse(dynamicEndpoint.swaggerString)
              ("user_id", cc.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~ ("swagger_string", swaggerJson)
            }
            (ListResult("dynamic_endpoints", resultList), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteMyDynamicEndpoint,
      implementedInApiVersion,
      nameOf(deleteMyDynamicEndpoint),
      "DELETE",
      "/my/dynamic-endpoints/DYNAMIC_ENDPOINT_ID",
      "Delete My Dynamic Endpoint",
      s"""Delete a DynamicEndpoint specified by DYNAMIC_ENDPOINT_ID.""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        DynamicEndpointNotFoundByDynamicEndpointId,
        UnknownError
      ),
      List(apiTagManageDynamicEndpoint, apiTagApi, apiTagNewStyle),
    )

    lazy val deleteMyDynamicEndpoint : OBPEndpoint = {
      case "my" :: "dynamic-endpoints" :: dynamicEndpointId ::  Nil JsonDelete _ => {
        cc =>
          for {
            (dynamicEndpoint, callContext) <- NewStyle.function.getDynamicEndpoint(None, dynamicEndpointId, cc.callContext)
            _ <- Helper.booleanToFuture(InvalidMyDynamicEndpointUser, cc=callContext) {
              dynamicEndpoint.userId.equals(cc.userId)
            }
            deleted <- NewStyle.function.deleteDynamicEndpoint(None, dynamicEndpointId, callContext)
            
          } yield {
            (deleted, HttpCode.`204`(callContext))
          }
      }
    }

    lazy val dynamicEndpoint: OBPEndpoint = {
      case DynamicReq(url, json, method, params, pathParams, role, operationId, mockResponse, bankId) => { cc =>
        // process before authentication interceptor, get intercept result
        val resourceDoc = DynamicEndpointHelper.doc.find(_.operationId == operationId)
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if(beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
            (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
            _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, role, callContext)

            // validate request json payload
            httpRequestMethod = cc.verb
            path = StringUtils.substringAfter(cc.url, DynamicEndpointHelper.urlPrefix)

            // process after authentication interceptor, get intercept result
            jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
              case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
            })
            _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc=callContext) {
              jsonResponse.isEmpty
            }
            (box, callContext) <- if (DynamicEndpointHelper.isDynamicEntityResponse(url)) {
              for {
                (endpointMapping, callContext) <- if (DynamicEndpointHelper.isDynamicEntityResponse(url)) {
                  NewStyle.function.getEndpointMappingByOperationId(bankId, operationId, cc.callContext)
                } else{
                  Future.successful((EndpointMappingCommons(None,"","","", None), callContext))
                }
                requestMappingString = endpointMapping.requestMapping
//                requestMappingJvalue = net.liftweb.json.parse(requestMappingString)
                responseMappingString = endpointMapping.responseMapping
                responseMappingJvalue = net.liftweb.json.parse(responseMappingString)

                (entityName, entityIdKey, entityIdValueFromUrl) <- if (method.value.equalsIgnoreCase("get")) {
                  NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                  } 
                } else {
                  NewStyle.function.tryons(s"$InvalidEndpointMapping `request_mapping` must  be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(requestMappingString, pathParams)
                  }
                }

                dynamicData <- Future{DynamicDataProvider.connectorMethodProvider.vend.getAll(entityName)}
                dynamicJsonData = JArray(dynamicData.map(it => net.liftweb.json.parse(it.dataJson)).map(_.asInstanceOf[JObject]))

                //                //We only get the value, but not sure the field name of it.
                //                // we can get the field name from the mapping: `primary_query_key`
                //                //requestBodyMapping --> Convert `RequestJson` --> `DynamicEntity Model.`  
                //                targetRequestBody = JsonUtils.buildJson(json, requestBodySchemeJvalue)
                //                requestBody = targetRequestBody match {
                //                  case j@JObject(_) => Some(j)
                //                  case _ => None
                //                }

                result = if (method.value.equalsIgnoreCase("get") && entityIdValueFromUrl.isDefined) {
                  DynamicEndpointHelper.getObjectByKeyValuePair(dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                } else if (method.value.equalsIgnoreCase("get") && entityIdValueFromUrl.isEmpty) {
                  val newParams = DynamicEndpointHelper.convertToMappingQueryParams(responseMappingJvalue, params)
                  DynamicEndpointHelper.getObjectsByParams(dynamicJsonData, newParams)
//                } else if (method.value.equalsIgnoreCase("post")) {
//                  //this post need the dynamicId to update it.
//                  //1st: we need to find the data by json.field1 --> dynamicId --> update the table.
//                  dynamicJsonData
//                } else if (method.value.equalsIgnoreCase("put")) {
//                  dynamicJsonData
                } else if (method.value.equalsIgnoreCase("delete") && entityIdValueFromUrl.isDefined) {
                  DynamicEndpointHelper.deleteObjectByKeyValuePair(dynamicData, dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                } else {
                  throw new RuntimeException(s"$NotImplemented Only support Http Method `GET` yet, current  is ${method.value}")
                }
                responseBodyScheme = DynamicEndpointHelper.prepareMappingFields(responseMappingJvalue)
                responseBody = JsonUtils.buildJson(result, responseBodyScheme)
              } yield{
                (Full(("code", 200) ~ ("value", responseBody)), callContext)
              }
            }else{
              MockResponseHolder.init(mockResponse) { // if target url domain is `obp_mock`, set mock response to current thread
                NewStyle.function.dynamicEndpointProcess(url, json, method, params, pathParams, callContext)
              }
            }
          } yield {
            box match {
              case Full(v) =>
                val code = (v \ "code").asInstanceOf[JInt].num.toInt
                (v \ "value", callContext.map(_.copy(httpCode = Some(code))))

              case e: Failure =>
                val changedMsgFailure = e.copy(msg = s"$InternalServerError ${e.msg}")
                fullBoxOrException[JValue](changedMsgFailure)
                ??? // will not execute to here, Because the failure message is thrown by upper line.
            }

        }
      }
    }

    staticResourceDocs += ResourceDoc(
      createOrUpdateCustomerAttributeAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateCustomerAttributeAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/customer",
      "Create or Update Customer Attribute Definition",
      s""" Create or Update Customer Attribute Definition
         |
         |The category field must be one of: ${AttributeCategory.Customer}
         |
         |The type field must be one of; ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      templateAttributeDefinitionJsonV400,
      templateAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canCreateCustomerAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateCustomerAttributeAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "customer" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER} (123)and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.Customer}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      createOrUpdateAccountAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateAccountAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/account",
      "Create or Update Account Attribute Definition",
      s""" Create or Update Account Attribute Definition
         |
         |The category field must be ${AttributeCategory.Account}
         |
         |The type field must be one of; ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      accountAttributeDefinitionJsonV400,
      accountAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canCreateAccountAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateAccountAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "account" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER} (123)and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.Account}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createOrUpdateProductAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateProductAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/product",
      "Create or Update Product Attribute Definition",
      s""" Create or Update Product Attribute Definition
         |
         |The category field must be ${AttributeCategory.Product}
         |
         |The type field must be one of; ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productAttributeDefinitionJsonV400,
      productAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProductAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateProductAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "product" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER} (123)and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.Product}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }


    val productAttributeGeneralInfo =
      s"""
         |Product Attributes are used to describe a financial Product with a list of typed key value pairs.
         |
         |Each Product Attribute is linked to its Product by PRODUCT_CODE
         |
         |
       """.stripMargin

    staticResourceDocs += ResourceDoc(
      createProductAttribute,
      implementedInApiVersion,
      nameOf(createProductAttribute),
      "POST",
      "/banks/BANK_ID/products/PRODUCT_CODE/attribute",
      "Create Product Attribute",
      s""" Create Product Attribute
         |
         |$productAttributeGeneralInfo
         |
         |Typical product attributes might be:
         |
         |ISIN (for International bonds)
         |VKN (for German bonds)
         |REDCODE (markit short code for credit derivative)
         |LOAN_ID (e.g. used for Anacredit reporting)
         |
         |ISSUE_DATE (When the bond was issued in the market)
         |MATURITY_DATE (End of life time of a product)
         |TRADABLE
         |
         |See [FPML](http://www.fpml.org/) for more examples.
         |
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productAttributeJsonV400,
      productAttributeResponseJsonV400,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProductAttribute))
    )

    lazy val createProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canCreateProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $ProductAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)"
            productAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              ProductAttributeType.withName(postedData.`type`)
            }
            _  <- Future(Connector.connector.vend.getProduct(BankId(bankId), ProductCode(productCode))) map {
              getFullBoxOrFail(_, callContext, ProductNotFoundByProductCode + " {" + productCode + "}", 400)
            }
            (productAttribute, callContext) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankId),
              ProductCode(productCode),
              None,
              postedData.name,
              productAttributeType,
              postedData.value,
              postedData.is_active,
              callContext: Option[CallContext]
            )
          } yield {
            (createProductAttributeJson(productAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateProductAttribute,
      implementedInApiVersion,
      nameOf(updateProductAttribute),
      "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Update Product Attribute",
      s""" Update Product Attribute. 
         |

         |$productAttributeGeneralInfo
         |
         |Update one Product Attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productAttributeJsonV400,
      productAttributeResponseJsonV400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle))

    lazy val updateProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonPut json -> _ =>{
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canUpdateProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $ProductAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)"
            productAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              ProductAttributeType.withName(postedData.`type`)
            }
            (_, callContext) <- NewStyle.function.getProductAttributeById(productAttributeId, callContext)
            (productAttribute, callContext) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankId),
              ProductCode(productCode),
              Some(productAttributeId),
              postedData.name,
              productAttributeType,
              postedData.value,
              postedData.is_active,
              callContext: Option[CallContext]
            )
          } yield {
            (createProductAttributeJson(productAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getProductAttribute,
      implementedInApiVersion,
      nameOf(getProductAttribute),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Get Product Attribute",
      s""" Get Product Attribute
         |
         |$productAttributeGeneralInfo
         |
         |Get one product attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      productAttributeResponseJsonV400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle))

    lazy val getProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canGetProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (productAttribute, callContext) <- NewStyle.function.getProductAttributeById(productAttributeId, callContext)

          } yield {
            (createProductAttributeJson(productAttribute), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createProductFee,
      implementedInApiVersion,
      nameOf(createProductFee),
      "POST",
      "/banks/BANK_ID/products/PRODUCT_CODE/fee",
      "Create Product Fee",
      s"""Create Product Fee
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productFeeJsonV400.copy(product_fee_id = None),
      productFeeResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProductFee))
    )

    lazy val createProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fee" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ProductFeeJsonV400 " , 400, Some(cc)) {
              json.extract[ProductFeeJsonV400]
            }
            (_, callContext) <- NewStyle.function.getProduct(BankId(bankId), ProductCode(productCode), Some(cc))
            (productFee, callContext) <- NewStyle.function.createOrUpdateProductFee(
              BankId(bankId),
              ProductCode(productCode),
              None,
              postedData.name,
              postedData.is_active,
              postedData.more_info,
              postedData.value.currency,
              postedData.value.amount,
              postedData.value.frequency,
              postedData.value.`type`,
              callContext: Option[CallContext]
            )
          } yield {
            (createProductFeeJson(productFee), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateProductFee,
      implementedInApiVersion,
      nameOf(updateProductFee),
      "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE/fees/PRODUCT_FEE_ID",
      "Update Product Fee",
      s""" Update Product Fee. 
         |
         |Update one Product Fee by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productFeeJsonV400.copy(product_fee_id = None),
      productFeeResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canUpdateProductFee)))

    lazy val updateProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: productFeeId :: Nil JsonPut json -> _ =>{
        cc =>
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ProductFeeJsonV400 ", 400, Some(cc)) {
              json.extract[ProductFeeJsonV400]
            }
            (_, callContext) <- NewStyle.function.getProduct(BankId(bankId), ProductCode(productCode), Some(cc))
            (_, callContext) <- NewStyle.function.getProductFeeById(productFeeId, callContext)
            (productFee, callContext) <- NewStyle.function.createOrUpdateProductFee(
              BankId(bankId),
              ProductCode(productCode),
              Some(productFeeId),
              postedData.name,
              postedData.is_active,
              postedData.more_info,
              postedData.value.currency,
              postedData.value.amount,
              postedData.value.frequency,
              postedData.value.`type`,
              callContext: Option[CallContext]
            )
          } yield {
            (createProductFeeJson(productFee), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getProductFee,
      implementedInApiVersion,
      nameOf(getProductFee),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE/fees/PRODUCT_FEE_ID",
      "Get Product Fee",
      s""" Get Product Fee
         |
         |Get one product fee by its id.
         |
         |${authenticationRequiredMessage(false)}
         |
         |""",
      EmptyBody,
      productFeeResponseJsonV400,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )

    lazy val getProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: productFeeId :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (productFee, callContext) <- NewStyle.function.getProductFeeById(productFeeId, Some(cc))
          } yield {
            (createProductFeeJson(productFee), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getProductFees,
      implementedInApiVersion,
      nameOf(getProductFees),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE/fees",
      "Get Product Fees",
      s"""Get Product Fees
         |
         |${authenticationRequiredMessage(false)}
         |
         |""",
      EmptyBody,
      productFeesResponseJsonV400,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )

    lazy val getProductFees : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (productFees, callContext) <- NewStyle.function.getProductFeesFromProvider(BankId(bankId), ProductCode(productCode), Some(cc))
          } yield {
            (createProductFeesJson(productFees), HttpCode.`200`(callContext))
          }
      }
    }
    staticResourceDocs += ResourceDoc(
      deleteProductFee,
      implementedInApiVersion,
      nameOf(deleteProductFee),
      "DELETE",
      "/banks/BANK_ID/products/PRODUCT_CODE/fees/PRODUCT_FEE_ID",
      "Delete Product Fee",
      s"""Delete Product Fee
         |
         |Delete one product fee by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canDeleteProductFee)))

    lazy val deleteProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: productFeeId :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getProductFeeById(productFeeId, Some(cc))
            (productFee, callContext) <- NewStyle.function.deleteProductFee(productFeeId, Some(cc))
          } yield {
            (productFee, HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createOrUpdateBankAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateBankAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/bank",
      "Create or Update Bank Attribute Definition",
      s""" Create or Update Bank Attribute Definition
         |
         |The category field must be ${AttributeCategory.Bank}
         |
         |The type field must be one of; ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      bankAttributeDefinitionJsonV400,
      bankAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canCreateBankAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateBankAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "bank" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER} (123)and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.Bank}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createBankAttribute,
      implementedInApiVersion,
      nameOf(createBankAttribute),
      "POST",
      "/banks/BANK_ID/attribute",
      "Create Bank Attribute",
      s""" Create Bank Attribute
         |
         |Typical product attributes might be:
         |
         |ISIN (for International bonds)
         |VKN (for German bonds)
         |REDCODE (markit short code for credit derivative)
         |LOAN_ID (e.g. used for Anacredit reporting)
         |
         |ISSUE_DATE (When the bond was issued in the market)
         |MATURITY_DATE (End of life time of a product)
         |TRADABLE
         |
         |See [FPML](http://www.fpml.org/) for more examples.
         |
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      bankAttributeJsonV400,
      bankAttributeResponseJsonV400,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canCreateBankAttribute))
    )

    lazy val createBankAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canCreateProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $BankAttributeJsonV400 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[BankAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${BankAttributeType.DOUBLE}(12.1234), ${BankAttributeType.STRING}(TAX_NUMBER), ${BankAttributeType.INTEGER}(123) and ${BankAttributeType.DATE_WITH_DAY}(2012-04-23)"
            bankAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              BankAttributeType.withName(postedData.`type`)
            }
            (bankAttribute, callContext) <- NewStyle.function.createOrUpdateBankAttribute(
              BankId(bankId),
              None,
              postedData.name,
              bankAttributeType,
              postedData.value,
              postedData.is_active,
              callContext: Option[CallContext]
            )
          } yield {
            (createBankAttributeJson(bankAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankAttributes,
      implementedInApiVersion,
      nameOf(getBankAttributes),
      "GET",
      "/banks/BANK_ID/attributes",
      "Get Bank Attributes",
      s""" Get Bank Attributes
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canGetBankAttribute))
    )

    lazy val getBankAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attributes" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributes, callContext) <- NewStyle.function.getBankAttributesByBank(bankId, cc.callContext)
          } yield {
            (createBankAttributesJson(attributes), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getBankAttribute,
      implementedInApiVersion,
      nameOf(getBankAttribute),
      "GET",
      "/banks/BANK_ID/attributes/BANK_ATTRIBUTE_ID",
      "Get Bank Attribute By BANK_ATTRIBUTE_ID",
      s""" Get Bank Attribute By BANK_ATTRIBUTE_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canGetBankAttribute))
    )

    lazy val getBankAttribute : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attributes" :: bankAttributeId :: Nil JsonGet _ => {
        cc =>
          for {
            (attribute, callContext) <- NewStyle.function.getBankAttributeById(bankAttributeId, cc.callContext)
          } yield {
            (createBankAttributeJson(attribute), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      updateBankAttribute,
      implementedInApiVersion,
      nameOf(updateBankAttribute),
      "PUT",
      "/banks/BANK_ID/attributes/BANK_ATTRIBUTE_ID",
      "Update Bank Attribute",
      s""" Update Bank Attribute. 
         |
         |Update one Bak Attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      bankAttributeJsonV400,
      bankAttributeDefinitionJsonV400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle))

    lazy val updateBankAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "attributes" :: bankAttributeId :: Nil JsonPut json -> _ =>{
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canUpdateBankAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $BankAttributeJsonV400 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[BankAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${BankAttributeType.DOUBLE}(12.1234), ${BankAttributeType.STRING}(TAX_NUMBER), ${BankAttributeType.INTEGER}(123) and ${BankAttributeType.DATE_WITH_DAY}(2012-04-23)"
            productAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              BankAttributeType.withName(postedData.`type`)
            }
            (_, callContext) <- NewStyle.function.getBankAttributeById(bankAttributeId, callContext)
            (bankAttribute, callContext) <- NewStyle.function.createOrUpdateBankAttribute(
              BankId(bankId),
              Some(bankAttributeId),
              postedData.name,
              productAttributeType,
              postedData.value,
              postedData.is_active,
              callContext: Option[CallContext]
            )
          } yield {
            (createBankAttributeJson(bankAttribute), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteBankAttribute,
      implementedInApiVersion,
      nameOf(deleteBankAttribute),
      "DELETE",
      "/banks/BANK_ID/attributes/BANK_ATTRIBUTE_ID",
      "Delete Bank Attribute",
      s""" Delete Bank Attribute
         |
         |Delete a Bank Attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        UserHasMissingRoles,
        BankNotFound,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle))

    lazy val deleteBankAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "attributes" :: bankAttributeId ::  Nil JsonDelete _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canDeleteBankAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (bankAttribute, callContext) <- NewStyle.function.deleteBankAttribute(bankAttributeId, callContext)
          } yield {
            (Full(bankAttribute), HttpCode.`204`(callContext))
          }
      }
    }
    

    staticResourceDocs += ResourceDoc(
      createOrUpdateTransactionAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateTransactionAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/transaction",
      "Create or Update Transaction Attribute Definition",
      s""" Create or Update Transaction Attribute Definition
         |
         |The category field must be ${AttributeCategory.Transaction}
         |
         |The type field must be one of; ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      transactionAttributeDefinitionJsonV400,
      transactionAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canCreateTransactionAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateTransactionAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER} (123)and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.Transaction}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      createOrUpdateCardAttributeDefinition,
      implementedInApiVersion,
      nameOf(createOrUpdateCardAttributeDefinition),
      "PUT",
      "/banks/BANK_ID/attribute-definitions/card",
      "Create or Update Card Attribute Definition",
      s""" Create or Update Card Attribute Definition
         |
         |The category field must be ${AttributeCategory.Card}
         |
         |The type field must be one of; ${AttributeType.DOUBLE}, ${AttributeType.STRING}, ${AttributeType.INTEGER} and ${AttributeType.DATE_WITH_DAY}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      cardAttributeDefinitionJsonV400,
      cardAttributeDefinitionResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canCreateCardAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateCardAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "card" :: Nil JsonPut json -> _=> {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $AttributeDefinitionJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[AttributeDefinitionJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AttributeType.DOUBLE}(12.1234), ${AttributeType.STRING}(TAX_NUMBER), ${AttributeType.INTEGER} (123)and ${AttributeType.DATE_WITH_DAY}(2012-04-23)"
            attributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeType.withName(postedData.`type`)
            }
            failMsg = s"$InvalidJsonFormat The `Category` field can only accept the following field: " +
              s"${AttributeCategory.Card}"
            category <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              AttributeCategory.withName(postedData.category)
            }
            (attributeDefinition, callContext) <- createOrUpdateAttributeDefinition(
              bankId,
              postedData.name,
              category,
              attributeType,
              postedData.description,
              postedData.alias,
              postedData.can_be_seen_on_views,
              postedData.is_active,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionJson(attributeDefinition), HttpCode.`201`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      deleteTransactionAttributeDefinition,
      implementedInApiVersion,
      nameOf(deleteTransactionAttributeDefinition),
      "DELETE",
      "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/transaction",
      "Delete Transaction Attribute Definition",
      s""" Delete Transaction Attribute Definition by ATTRIBUTE_DEFINITION_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canDeleteTransactionAttributeDefinitionAtOneBank)))

    lazy val deleteTransactionAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "transaction" :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- deleteAttributeDefinition(
              attributeDefinitionId,
              AttributeCategory.withName(AttributeCategory.Transaction.toString),
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteCustomerAttributeDefinition,
      implementedInApiVersion,
      nameOf(deleteCustomerAttributeDefinition),
      "DELETE",
      "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/customer",
      "Delete Customer Attribute Definition",
      s""" Delete Customer Attribute Definition by ATTRIBUTE_DEFINITION_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canDeleteCustomerAttributeDefinitionAtOneBank)))

    lazy val deleteCustomerAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "customer" :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- deleteAttributeDefinition(
              attributeDefinitionId,
              AttributeCategory.withName(AttributeCategory.Customer.toString),
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteAccountAttributeDefinition,
      implementedInApiVersion,
      nameOf(deleteAccountAttributeDefinition),
      "DELETE",
      "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/account",
      "Delete Account Attribute Definition",
      s""" Delete Account Attribute Definition by ATTRIBUTE_DEFINITION_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canDeleteAccountAttributeDefinitionAtOneBank)))

    lazy val deleteAccountAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "account" :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- deleteAttributeDefinition(
              attributeDefinitionId,
              AttributeCategory.withName(AttributeCategory.Account.toString),
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteProductAttributeDefinition,
      implementedInApiVersion,
      nameOf(deleteProductAttributeDefinition),
      "DELETE",
      "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/product",
      "Delete Product Attribute Definition",
      s""" Delete Product Attribute Definition by ATTRIBUTE_DEFINITION_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canDeleteProductAttributeDefinitionAtOneBank)))

    lazy val deleteProductAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "product" :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- deleteAttributeDefinition(
              attributeDefinitionId,
              AttributeCategory.withName(AttributeCategory.Product.toString),
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteCardAttributeDefinition,
      implementedInApiVersion,
      nameOf(deleteCardAttributeDefinition),
      "DELETE",
      "/banks/BANK_ID/attribute-definitions/ATTRIBUTE_DEFINITION_ID/card",
      "Delete Card Attribute Definition",
      s""" Delete Card Attribute Definition by ATTRIBUTE_DEFINITION_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canDeleteCardAttributeDefinitionAtOneBank)))

    lazy val deleteCardAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "card" :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- deleteAttributeDefinition(
              attributeDefinitionId,
              AttributeCategory.withName(AttributeCategory.Card.toString),
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getProductAttributeDefinition,
      implementedInApiVersion,
      nameOf(getProductAttributeDefinition),
      "GET",
      "/banks/BANK_ID/attribute-definitions/product",
      "Get Product Attribute Definition",
      s""" Get Product Attribute Definition
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      productAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canGetProductAttributeDefinitionAtOneBank)))

    lazy val getProductAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "product" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributeDefinitions, callContext) <- getAttributeDefinition(
              AttributeCategory.withName(AttributeCategory.Product.toString),
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionsJson(attributeDefinitions), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getCustomerAttributeDefinition,
      implementedInApiVersion,
      nameOf(getCustomerAttributeDefinition),
      "GET",
      "/banks/BANK_ID/attribute-definitions/customer",
      "Get Customer Attribute Definition",
      s""" Get Customer Attribute Definition
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customerAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomerAttributeDefinitionAtOneBank)))

    lazy val getCustomerAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "customer" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributeDefinitions, callContext) <- getAttributeDefinition(
              AttributeCategory.withName(AttributeCategory.Customer.toString),
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionsJson(attributeDefinitions), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getAccountAttributeDefinition,
      implementedInApiVersion,
      nameOf(getAccountAttributeDefinition),
      "GET",
      "/banks/BANK_ID/attribute-definitions/account",
      "Get Account Attribute Definition",
      s""" Get Account Attribute Definition
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      accountAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canGetAccountAttributeDefinitionAtOneBank)))

    lazy val getAccountAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "account" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributeDefinitions, callContext) <- getAttributeDefinition(
              AttributeCategory.withName(AttributeCategory.Account.toString),
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionsJson(attributeDefinitions), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getTransactionAttributeDefinition,
      implementedInApiVersion,
      nameOf(getTransactionAttributeDefinition),
      "GET",
      "/banks/BANK_ID/attribute-definitions/transaction",
      "Get Transaction Attribute Definition",
      s""" Get Transaction Attribute Definition
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      transactionAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canGetTransactionAttributeDefinitionAtOneBank)))

    lazy val getTransactionAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributeDefinitions, callContext) <- getAttributeDefinition(
              AttributeCategory.withName(AttributeCategory.Transaction.toString),
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionsJson(attributeDefinitions), HttpCode.`200`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      getCardAttributeDefinition,
      implementedInApiVersion,
      nameOf(getCardAttributeDefinition),
      "GET",
      "/banks/BANK_ID/attribute-definitions/card",
      "Get Card Attribute Definition",
      s""" Get Card Attribute Definition
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      cardAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canGetCardAttributeDefinitionAtOneBank)))

    lazy val getCardAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "card" :: Nil JsonGet _ => {
        cc =>
          for {
            (attributeDefinitions, callContext) <- getAttributeDefinition(
              AttributeCategory.withName(AttributeCategory.Card.toString),
              cc.callContext
            )
          } yield {
            (JSONFactory400.createAttributeDefinitionsJson(attributeDefinitions), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteUserCustomerLink,
      implementedInApiVersion,
      nameOf(deleteUserCustomerLink),
      "DELETE",
      "/banks/BANK_ID/user_customer_links/USER_CUSTOMER_LINK_ID",
      "Delete User Customer Link",
      s""" Delete User Customer Link by USER_CUSTOMER_LINK_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canDeleteUserCustomerLink)))

    lazy val deleteUserCustomerLink : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user_customer_links" :: userCustomerLinkId :: Nil JsonDelete _ => {
        cc =>
          for {
            (deleted, callContext) <- UserCustomerLinkNewStyle.deleteUserCustomerLink(
              userCustomerLinkId,
              cc.callContext
            )
          } yield {
            (Full(deleted), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getUserCustomerLinksByUserId,
      implementedInApiVersion,
      nameOf(getUserCustomerLinksByUserId),
      "GET",
      "/banks/BANK_ID/user_customer_links/users/USER_ID",
      "Get User Customer Links by User",
      s""" Get User Customer Links by USER_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      userCustomerLinksJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetUserCustomerLink)))

    lazy val getUserCustomerLinksByUserId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user_customer_links" :: "users" :: userId :: Nil JsonGet _ => {
        cc =>
          for {
            (userCustomerLinks, callContext) <- UserCustomerLinkNewStyle.getUserCustomerLink(
              userId,
              cc.callContext
            )
          } yield {
            (JSONFactory200.createUserCustomerLinkJSONs(userCustomerLinks), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUserCustomerLinksByCustomerId,
      implementedInApiVersion,
      nameOf(getUserCustomerLinksByCustomerId),
      "GET",
      "/banks/BANK_ID/user_customer_links/customers/CUSTOMER_ID",
      "Get User Customer Links by Customer",
      s""" Get User Customer Links by CUSTOMER_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      userCustomerLinksJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetUserCustomerLink)))

    lazy val getUserCustomerLinksByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user_customer_links" :: "customers" :: customerId :: Nil JsonGet _ => {
        cc =>
          for {
            (userCustomerLinks, callContext) <- UserCustomerLinkNewStyle.getUserCustomerLinks(
              customerId,
              cc.callContext
            )
          } yield {
            (JSONFactory200.createUserCustomerLinkJSONs(userCustomerLinks), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteTransactionCascade,
      implementedInApiVersion,
      nameOf(deleteTransactionCascade),
      "DELETE",
      "/management/cascading/banks/BANK_ID/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID",
      "Delete Transaction Cascade",
      s"""Delete a Transaction Cascade specified by TRANSACTION_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagTransaction, apiTagNewStyle),
      Some(List(canDeleteTransactionCascade)))

    lazy val deleteTransactionCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: 
        "transactions" :: TransactionId(transactionId) :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            _ <- Future(DeleteTransactionCascade.atomicDelete(bankId, accountId, transactionId))
          } yield {
            (Full(true), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteAccountCascade,
      implementedInApiVersion,
      nameOf(deleteAccountCascade),
      "DELETE",
      "/management/cascading/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Delete Account Cascade",
      s"""Delete an Account Cascade specified by ACCOUNT_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canDeleteAccountCascade)))

    lazy val deleteAccountCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonDelete _ => {
        cc =>
          for {
            _ <- Future(DeleteAccountCascade.atomicDelete(bankId, accountId))
          } yield {
            (Full(true), HttpCode.`200`(cc))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteProductCascade,
      implementedInApiVersion,
      nameOf(deleteProductCascade),
      "DELETE",
      "/management/cascading/banks/BANK_ID/products/PRODUCT_CODE",
      "Delete Product Cascade",
      s"""Delete a Product Cascade specified by PRODUCT_CODE.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canDeleteProductCascade)))

    lazy val deleteProductCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "products" :: ProductCode(code) :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getProduct(bankId, code, Some(cc))
            _ <- Future(DeleteProductCascade.atomicDelete(bankId, code))
          } yield {
            (Full(true), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createCounterparty,
      implementedInApiVersion,
      "createCounterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty (Explicit)",
      s"""Create Counterparty (Explicit) for an Account.
         |
         |In OBP, there are two types of Counterparty.
         |
         |* Explicit Counterparties (those here) which we create explicitly and are used in COUNTERPARTY Transaction Requests
         |
         |* Implicit Counterparties (AKA Other Accounts) which are generated automatically from the other sides of Transactions.
         |
         |Explicit Counterparties are created for the account / view
         |They are how the user of the view (e.g. account owner) refers to the other side of the transaction
         |
         |name : the human readable name (e.g. Piano teacher, Miss Nipa)
         |
         |description : the human readable name (e.g. Piano teacher, Miss Nipa)
         |
         |currency : counterparty account currency (e.g. EUR, GBP, USD, ...)
         |
         |bank_routing_scheme : eg: bankId or bankCode or any other strings
         |
         |bank_routing_address : eg: `gh.29.uk`, must be valid sandbox bankIds
         |
         |account_routing_scheme : eg: AccountId or AccountNumber or any other strings
         |
         |account_routing_address : eg: `1d65db7c-a7b2-4839-af41-95`, must be valid accountIds
         |
         |other_account_secondary_routing_scheme : eg: IBan or any other strings
         |
         |other_account_secondary_routing_address : if it is an IBAN, it should be unique for each counterparty.
         |
         |other_branch_routing_scheme : eg: branchId or any other strings or you can leave it empty, not useful in sandbox mode.
         |
         |other_branch_routing_address : eg: `branch-id-123` or you can leave it empty, not useful in sandbox mode.
         |
         |is_beneficiary : must be set to `true` in order to send payments to this counterparty
         |
         |bespoke: It supports a list of key-value, you can add it to the counterparty.
         |
         |bespoke.key : any info-key you want to add to this counterparty
         |
         |bespoke.value : any info-value you want to add to this counterparty
         |
         |The view specified by VIEW_ID must have the canAddCounterparty permission
         |
         |A minimal example for TransactionRequestType == COUNTERPARTY
         | {
         |  "name": "Tesobe1",
         |  "description": "Good Company",
         |  "currency": "EUR",
         |  "other_bank_routing_scheme": "OBP",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "OBP",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "is_beneficiary": true,
         |  "other_account_secondary_routing_scheme": "",
         |  "other_account_secondary_routing_address": "",
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         |
         |A minimal example for TransactionRequestType == SEPA
         |
         | {
         |  "name": "Tesobe2",
         |  "description": "Good Company",
         |  "currency": "EUR",
         |  "other_bank_routing_scheme": "OBP",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "OBP",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "other_account_secondary_routing_scheme": "IBAN",
         |  "other_account_secondary_routing_address": "DE89 3704 0044 0532 0130 00",
         |  "is_beneficiary": true,
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      postCounterpartyJson400,
      counterpartyWithMetadataJson400,
      List(
        $UserNotLoggedIn,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        InvalidJsonFormat,
        InvalidISOCurrencyCode,
        ViewNotFound,
        CounterpartyAlreadyExists,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount, apiTagNewStyle))


    lazy val createCounterparty: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), view, callContext) <-  SS.userView
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCounterpartyJSON", 400, callContext) {
              json.extract[PostCounterpartyJson400]
            }

            _ <- Helper.booleanToFuture(s"$NoViewPermission can_add_counterparty. Please use a view with that permission or add the permission to this view.", cc=callContext) {view.canAddCounterparty}

            (counterparty, callContext) <- Connector.connector.vend.checkCounterpartyExists(postJson.name, bankId.value, accountId.value, viewId.value, callContext)

            _ <- Helper.booleanToFuture(CounterpartyAlreadyExists.replace("value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
              s"COUNTERPARTY_NAME(${postJson.name}) for the BANK_ID(${bankId.value}) and ACCOUNT_ID(${accountId.value}) and VIEW_ID($viewId)"), cc=callContext){
              counterparty.isEmpty
            }
            _ <- booleanToFuture(s"$InvalidValueLength. The maximum length of `description` field is ${MappedCounterparty.mDescription.maxLen}", cc=callContext){
              postJson.description.length <= 36
            }
            _ <- Helper.booleanToFuture(s"$InvalidISOCurrencyCode Current input is: '${postJson.currency}'", cc=callContext) {
              isValidCurrencyISOCode(postJson.currency)
            }

            //If other_account_routing_scheme=="OBP" or other_account_secondary_routing_address=="OBP" we will check if it is a real obp bank account.
            (_, callContext)<- if (postJson.other_bank_routing_scheme == "OBP" && postJson.other_account_routing_scheme =="OBP"){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            } else if (postJson.other_bank_routing_scheme == "OBP" && postJson.other_account_secondary_routing_scheme=="OBP"){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            }
            else
              Future{(Full(), Some(cc))}

            (counterparty, callContext) <- NewStyle.function.createCounterparty(
              name=postJson.name,
              description=postJson.description,
              currency=postJson.currency,
              createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              otherAccountRoutingScheme=postJson.other_account_routing_scheme,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme=postJson.other_account_secondary_routing_scheme,
              otherAccountSecondaryRoutingAddress=postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=postJson.other_branch_routing_scheme,
              otherBranchRoutingAddress=postJson.other_branch_routing_address,
              isBeneficiary=postJson.is_beneficiary,
              bespoke=postJson.bespoke.map(bespoke =>CounterpartyBespoke(bespoke.key,bespoke.value))
              , callContext)

            (counterpartyMetadata, callContext) <- NewStyle.function.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, postJson.name, callContext)

          } yield {
            (JSONFactory400.createCounterpartyWithMetadataJson400(counterparty,counterpartyMetadata), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createCounterpartyForAnyAccount,
      implementedInApiVersion,
      "createCounterpartyForAnyAccount",
      "POST",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty for any account (Explicit)",
      s"""Create Counterparty for any Account. (Explicit)
         |
         |In OBP, there are two types of Counterparty.
         |
         |* Explicit Counterparties (those here) which we create explicitly and are used in COUNTERPARTY Transaction Requests
         |
         |* Implicit Counterparties (AKA Other Accounts) which are generated automatically from the other sides of Transactions.
         |
         |Explicit Counterparties are created for the account / view
         |They are how the user of the view (e.g. account owner) refers to the other side of the transaction
         |
         |name : the human readable name (e.g. Piano teacher, Miss Nipa)
         |
         |description : the human readable name (e.g. Piano teacher, Miss Nipa)
         |
         |currency : counterparty account currency (e.g. EUR, GBP, USD, ...)
         |
         |bank_routing_scheme : eg: bankId or bankCode or any other strings
         |
         |bank_routing_address : eg: `gh.29.uk`, must be valid sandbox bankIds
         |
         |account_routing_scheme : eg: AccountId or AccountNumber or any other strings
         |
         |account_routing_address : eg: `1d65db7c-a7b2-4839-af41-95`, must be valid accountIds
         |
         |other_account_secondary_routing_scheme : eg: IBan or any other strings
         |
         |other_account_secondary_routing_address : if it is an IBAN, it should be unique for each counterparty.
         |
         |other_branch_routing_scheme : eg: branchId or any other strings or you can leave it empty, not useful in sandbox mode.
         |
         |other_branch_routing_address : eg: `branch-id-123` or you can leave it empty, not useful in sandbox mode.
         |
         |is_beneficiary : must be set to `true` in order to send payments to this counterparty
         |
         |bespoke: It supports a list of key-value, you can add it to the counterparty.
         |
         |bespoke.key : any info-key you want to add to this counterparty
         |
         |bespoke.value : any info-value you want to add to this counterparty
         |
         |The view specified by VIEW_ID must have the canAddCounterparty permission
         |
         |A minimal example for TransactionRequestType == COUNTERPARTY
         | {
         |  "name": "Tesobe1",
         |  "description": "Good Company",
         |  "currency": "EUR",
         |  "other_bank_routing_scheme": "OBP",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "OBP",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "is_beneficiary": true,
         |  "other_account_secondary_routing_scheme": "",
         |  "other_account_secondary_routing_address": "",
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         |
         |A minimal example for TransactionRequestType == SEPA
         |
         | {
         |  "name": "Tesobe2",
         |  "description": "Good Company",
         |  "currency": "EUR",
         |  "other_bank_routing_scheme": "OBP",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "OBP",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "other_account_secondary_routing_scheme": "IBAN",
         |  "other_account_secondary_routing_address": "DE89 3704 0044 0532 0130 00",
         |  "is_beneficiary": true,
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      postCounterpartyJson400,
      counterpartyWithMetadataJson400,
      List(
        $UserNotLoggedIn,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        $BankNotFound,
        AccountNotFound,
        InvalidJsonFormat,
        InvalidISOCurrencyCode,
        ViewNotFound,
        CounterpartyAlreadyExists,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount, apiTagNewStyle),
      Some(List(canCreateCounterparty, canCreateCounterpartyAtAnyBank)))


    lazy val createCounterpartyForAnyAccount: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId):: "counterparties" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400,  callContext) {
              json.extract[PostCounterpartyJson400]
            }
            _ <- Helper.booleanToFuture(s"$InvalidValueLength. The maximum length of `description` field is ${MappedCounterparty.mDescription.maxLen}", cc=callContext){postJson.description.length <= 36}


            (counterparty, callContext) <- Connector.connector.vend.checkCounterpartyExists(postJson.name, bankId.value, accountId.value, viewId.value, callContext)

            _ <- Helper.booleanToFuture(CounterpartyAlreadyExists.replace("value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
              s"COUNTERPARTY_NAME(${postJson.name}) for the BANK_ID(${bankId.value}) and ACCOUNT_ID(${accountId.value}) and VIEW_ID($viewId)"), cc=callContext){
              counterparty.isEmpty
            }

            _ <- Helper.booleanToFuture(s"$InvalidISOCurrencyCode Current input is: '${postJson.currency}'", cc=callContext) {
              isValidCurrencyISOCode(postJson.currency)
            }

            //If other_account_routing_scheme=="OBP" or other_account_secondary_routing_address=="OBP" we will check if it is a real obp bank account.
            (_, callContext)<- if (postJson.other_bank_routing_scheme == "OBP" && postJson.other_account_routing_scheme =="OBP"){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            } else if (postJson.other_bank_routing_scheme == "OBP" && postJson.other_account_secondary_routing_scheme=="OBP"){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            }
            else
              Future{(Full(), Some(cc))}

            (counterparty, callContext) <- NewStyle.function.createCounterparty(
              name=postJson.name,
              description=postJson.description,
              currency=postJson.currency,
              createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = "owner",
              otherAccountRoutingScheme=postJson.other_account_routing_scheme,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme=postJson.other_account_secondary_routing_scheme,
              otherAccountSecondaryRoutingAddress=postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=postJson.other_branch_routing_scheme,
              otherBranchRoutingAddress=postJson.other_branch_routing_address,
              isBeneficiary=postJson.is_beneficiary,
              bespoke=postJson.bespoke.map(bespoke =>CounterpartyBespoke(bespoke.key,bespoke.value))
              , callContext)

            (counterpartyMetadata, callContext) <- NewStyle.function.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, postJson.name, callContext)

          } yield {
            (JSONFactory400.createCounterpartyWithMetadataJson400(counterparty,counterpartyMetadata), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getExplictCounterpartiesForAccount,
      implementedInApiVersion,
      "getExplictCounterpartiesForAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit)",
      s"""Get the Counterparties (Explicit) for the account / view.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      counterpartiesJson400,
      List(
        $UserNotLoggedIn,
        $BankAccountNotFound,
        ViewNotFound,
        NoViewPermission,
        $UserNoPermissionAccessView,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagAccount, apiTagNewStyle))

    lazy val getExplictCounterpartiesForAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet req => {
        cc =>
          for {
            (view, callContext) <- SS.view
            _ <- Helper.booleanToFuture(failMsg = s"${NoViewPermission}canAddCounterparty", cc=callContext) {
              view.canAddCounterparty == true
            }
            (counterparties, callContext) <- NewStyle.function.getCounterparties(bankId,accountId,viewId, callContext)
            //Here we need create the metadata for all the explicit counterparties. maybe show them in json response.
            //Note: actually we need update all the counterparty metadata when they from adapter. Some counterparties may be the first time to api, there is no metadata.
            _ <- Helper.booleanToFuture(CreateOrUpdateCounterpartyMetadataError, 400, cc=callContext) {
              {
                for {
                  counterparty <- counterparties
                } yield {
                  Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, counterparty.name) match {
                    case Full(_) => true
                    case _ => false
                  }
                }
              }.forall(_ == true)
            }
          } yield {
            val counterpartiesJson = JSONFactory400.createCounterpartiesJson400(counterparties)
            (counterpartiesJson, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getExplictCounterpartyById,
      implementedInApiVersion,
      "getExplictCounterpartyById",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Counterparty Id (Explicit)",
      s"""Information returned about the Counterparty specified by COUNTERPARTY_ID:
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      counterpartyWithMetadataJson400,
      List($UserNotLoggedIn, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagCounterpartyMetaData, apiTagNewStyle)
    )

    lazy val getExplictCounterpartyById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonGet req => {
        cc =>
          for {
            (view, callContext) <- SS.view
            _ <- Helper.booleanToFuture(failMsg = s"${NoViewPermission}canAddCounterparty", cc=callContext) {
              view.canAddCounterparty == true
            }
            counterpartyMetadata <- NewStyle.function.getMetadata(bankId, accountId, counterpartyId.value, callContext)
            (counterparty, callContext) <- NewStyle.function.getCounterpartyTrait(bankId, accountId, counterpartyId.value, callContext)
          } yield {
            val counterpartyJson = JSONFactory400.createCounterpartyWithMetadataJson400(counterparty,counterpartyMetadata)
            (counterpartyJson, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCounterpartyByNameForAnyAccount,
      implementedInApiVersion,
      nameOf(getCounterpartyByNameForAnyAccount),
      "GET",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_NAME",
      "Get Counterparty by name for any account (Explicit) ",
      s"""
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      counterpartyWithMetadataJson400,
      List(
        $UserNotLoggedIn,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        $BankNotFound,
        $BankAccountNotFound,
        InvalidJsonFormat,
        ViewNotFound,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount, apiTagNewStyle),
      Some(List(canGetCounterpartyAtAnyBank, canGetCounterparty)))

    lazy val getCounterpartyByNameForAnyAccount: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId):: "counterparties" :: counterpartyName :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user

            (counterparty, callContext) <- Connector.connector.vend.checkCounterpartyExists(counterpartyName, bankId.value, accountId.value, viewId.value, callContext)

            counterparty <- NewStyle.function.tryons(CounterpartyNotFound.replace(
              "The BANK_ID / ACCOUNT_ID specified does not exist on this server.",
              s"COUNTERPARTY_NAME(${counterpartyName}) for the BANK_ID(${bankId.value}) and ACCOUNT_ID(${accountId.value}) and VIEW_ID($viewId)"), 400,  callContext) {
              counterparty.head
            }
            
            (counterpartyMetadata, callContext) <- NewStyle.function.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, counterparty.name, callContext)

          } yield {
            (JSONFactory400.createCounterpartyWithMetadataJson400(counterparty,counterpartyMetadata), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      addConsentUser,
      implementedInApiVersion,
      nameOf(addConsentUser),
      "PUT",
      "/banks/BANK_ID/consents/CONSENT_ID/user-update-request",
      "Add User to a Consent",
      s"""
         |
         |
         |This endpoint is used to add the User of Consent.
         |
         |Each Consent has one of the following states: ${ConsentStatus.values.toList.sorted.mkString(", ") }.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      PutConsentUserJsonV400(user_id = "ed7a7c01-db37-45cc-ba12-0ae8891c195c"),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "AUTHORISED"
      ),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        $BankNotFound,
        ConsentUserAlreadyAdded,
        InvalidJsonFormat,
        ConsentNotFound,
        UnknownError
      ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagNewStyle :: Nil)

    lazy val addConsentUser : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents"  :: consentId :: "user-update-request" :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (_, callContext) <- SS.user
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutConsentUserJsonV400 "
            putJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutConsentUserJsonV400]
            }
            user <- Users.users.vend.getUserByUserIdFuture(putJson.user_id) map {
              x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId(${putJson.user_id})")
            }
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            _ <- Helper.booleanToFuture(ConsentUserAlreadyAdded, cc=callContext) { consent.userId != null }
            consent <- Future(Consents.consentProvider.vend.updateConsentUser(consentId, user)) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateConsentStatus,
      implementedInApiVersion,
      nameOf(updateConsentStatus),
      "PUT",
      "/banks/BANK_ID/consents/CONSENT_ID",
      "Update Consent Status",
      s"""
         |
         |
         |This endpoint is used to update the Status of Consent.
         |
         |Each Consent has one of the following states: ${ConsentStatus.values.toList.sorted.mkString(", ") }.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      PutConsentStatusJsonV400(status = "AUTHORISED"),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "AUTHORISED"
      ),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagNewStyle :: Nil)

    lazy val updateConsentStatus : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents"  :: consentId :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(user), callContext) <- SS.user
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutConsentStatusJsonV400 "
            consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutConsentStatusJsonV400]
            }
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            status = ConsentStatus.withName(consentJson.status)
            (consent, code) <- APIUtil.getPropsAsBoolValue("consents.sca.enabled", true) match {
              case true =>
                Future(consent, HttpCode.`202`(callContext))
              case false =>
                Future(Consents.consentProvider.vend.updateConsentStatus(consentId, status)) map {
                  i => connectorEmptyResponse(i, callContext)
                } map ((_, HttpCode.`200`(callContext)))
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), code)
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getConsents,
      implementedInApiVersion,
      nameOf(getConsents),
      "GET",
      "/banks/BANK_ID/my/consents",
      "Get Consents",
      s"""
         |
         |This endpoint gets the Consents that the current User created.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentsJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))

    lazy val getConsents: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consents" :: Nil JsonGet _ => {
        cc =>
          for {
            consents <- Future { Consents.consentProvider.vend.getConsentsByUser(cc.userId)
              .sortBy(i => (i.creationDateTime, i.apiStandard)).reverse
            }
          } yield {
            val consentsOfBank = Consent.filterByBankId(consents, bankId)
            (JSONFactory400.createConsentsJsonV400(consentsOfBank), HttpCode.`200`(cc))
          }
      }
    }
    staticResourceDocs += ResourceDoc(
      getConsentInfos,
      implementedInApiVersion,
      nameOf(getConsentInfos),
      "GET",
      "/banks/BANK_ID/my/consent-infos",
      "Get Consents Info",
      s"""
         |
         |This endpoint gets the Consents that the current User created.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentInfosJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))

    lazy val getConsentInfos: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consent-infos" :: Nil JsonGet _ => {
        cc =>
          for {
            consents <- Future { Consents.consentProvider.vend.getConsentsByUser(cc.userId)
              .sortBy(i => (i.creationDateTime, i.apiStandard)).reverse
            }
          } yield {
            val consentsOfBank = Consent.filterByBankId(consents, bankId)
            (JSONFactory400.createConsentInfosJsonV400(consentsOfBank), HttpCode.`200`(cc))
          }
      }
    }
    

    staticResourceDocs += ResourceDoc(
      getScannedApiVersions,
      implementedInApiVersion,
      nameOf(getScannedApiVersions),
      "GET",
      "/api/versions",
      "Get scanned API Versions",
      s"""Get all the scanned API Versions.""",
      EmptyBody,
      ListResult(
        "scanned_api_versions",
        List(ApiVersion.v3_1_0)
      ),
      List(
        UnknownError
      ),
      List(apiTagDocumentation, apiTagApi),
      Some(Nil)
    )

    lazy val getScannedApiVersions: OBPEndpoint = {
      case "api" :: "versions" :: Nil JsonGet _ => {
        cc =>
          Future {
            val versions: List[ScannedApiVersion] = ApiVersion.allScannedApiVersion.asScala.toList
            (ListResult("scanned_api_versions", versions), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createMyApiCollection,
      implementedInApiVersion,
      nameOf(createMyApiCollection),
      "POST",
      "/my/api-collections",
      "Create My Api Collection",
      s"""Create Api Collection for logged in user.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      postApiCollectionJson400,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val createMyApiCollection: OBPEndpoint = {
      case "my" :: "api-collections" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionJson400]
            }
            apiCollection <- Future{MappedApiCollectionsProvider.getApiCollectionByUserIdAndCollectionName(cc.userId, postJson.api_collection_name)}
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionAlreadyExisting Current api_collection_name(${postJson.api_collection_name}) is already existing for the log in user.", cc=cc.callContext) {
              apiCollection.isEmpty
            }
            (apiCollection, callContext) <- NewStyle.function.createApiCollection(
              cc.userId,
              postJson.api_collection_name,
              postJson.is_sharable,
              postJson.description,
              Some(cc)
            )
          } yield {
            (JSONFactory400.createApiCollectionJsonV400(apiCollection), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyApiCollectionByName,
      implementedInApiVersion,
      nameOf(getMyApiCollectionByName),
      "GET",
      "/my/api-collections/name/API_COLLECTION_NAME",
      "Get My Api Collection By Name",
      s"""Get Api Collection By API_COLLECTION_NAME.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getMyApiCollectionByName: OBPEndpoint = {
      case "my" :: "api-collections" :: "name" ::apiCollectionName :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(cc.userId, apiCollectionName, Some(cc))
          } yield {
            (JSONFactory400.createApiCollectionJsonV400(apiCollection), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyApiCollectionById,
      implementedInApiVersion,
      nameOf(getMyApiCollectionById),
      "GET",
      "/my/api-collections/API_COLLECTION_ID",
      "Get My Api Collection By Id",
      s"""Get Api Collection By API_COLLECTION_ID.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getMyApiCollectionById: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionId :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
          } yield {
            (JSONFactory400.createApiCollectionJsonV400(apiCollection), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getSharableApiCollectionById,
      implementedInApiVersion,
      nameOf(getSharableApiCollectionById),
      "GET",
      "/api-collections/sharable/API_COLLECTION_ID",
      "Get Sharable Api Collection By Id",
      s"""Get Sharable Api Collection By Id.
         |${authenticationRequiredMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionJson400,
      List(
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getSharableApiCollectionById: OBPEndpoint = {
      case "api-collections" :: "sharable" :: apiCollectionId :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionEndpointNotFound Current api_collection_id(${apiCollectionId}) is not sharable.", cc=callContext) {
              apiCollection.isSharable
            }
          } yield {
            (JSONFactory400.createApiCollectionJsonV400(apiCollection), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getApiCollections,
      implementedInApiVersion,
      nameOf(getApiCollections),
      "GET",
      "/users/USER_ID/api-collections",
      "Get Api Collections",
      s"""Get Api Collections.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle),
      Some(canGetAllApiCollections :: Nil)
    )

    lazy val getApiCollections: OBPEndpoint = {
      case "users" :: userId :: "api-collections" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.findByUserId(userId, Some(cc))
            (apiCollections, callContext) <- NewStyle.function.getApiCollectionsByUserId(userId, callContext)
          } yield {
            (JSONFactory400.createApiCollectionsJsonV400(apiCollections), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getFeaturedApiCollections,
      implementedInApiVersion,
      nameOf(getFeaturedApiCollections),
      "GET",
      "/api-collections/featured",
      "Get Featured Api Collections",
      s"""Get Featured Api Collections.
         |
         |${authenticationRequiredMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getFeaturedApiCollections: OBPEndpoint = {
      case "api-collections" :: "featured" ::  Nil JsonGet _ => {
        cc =>
          for {
            (apiCollections, callContext) <- NewStyle.function.getFeaturedApiCollections(cc.callContext)
          } yield {
            (JSONFactory400.createApiCollectionsJsonV400(apiCollections), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      getMyApiCollections,
      implementedInApiVersion,
      nameOf(getMyApiCollections),
      "GET",
      "/my/api-collections",
      "Get My Api Collections",
      s"""Get all the apiCollections for logged in user.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getMyApiCollections: OBPEndpoint = {
      case "my" :: "api-collections" :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollections, callContext) <- NewStyle.function.getApiCollectionsByUserId(cc.userId, Some(cc))
          } yield {
            (JSONFactory400.createApiCollectionsJsonV400(apiCollections), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteMyApiCollection,
      implementedInApiVersion,
      nameOf(deleteMyApiCollection),
      "DELETE",
      "/my/api-collections/API_COLLECTION_ID",
      "Delete My Api Collection",
      s"""Delete Api Collection By API_COLLECTION_ID
         |
         |${Glossary.getGlossaryItem("API Collections")}
         |
         |${authenticationRequiredMessage(true)}
         |
         |
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val deleteMyApiCollection : OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionId :: Nil JsonDelete _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            (deleted, callContext) <- NewStyle.function.deleteApiCollectionById(apiCollectionId, callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createMyApiCollectionEndpoint,
      implementedInApiVersion,
      nameOf(createMyApiCollectionEndpoint),
      "POST",
      "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints",
      "Create My Api Collection Endpoint",
      s"""Create Api Collection Endpoint.
         |
         |${Glossary.getGlossaryItem("API Collections")}
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      postApiCollectionEndpointJson400,
      apiCollectionEndpointJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val createMyApiCollectionEndpoint: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionEndpointJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionEndpointJson400]
            }
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(cc.userId, apiCollectionName, Some(cc))
            apiCollectionEndpoint <- Future{MappedApiCollectionEndpointsProvider.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollection.apiCollectionId, postJson.operation_id)} 
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionEndpointAlreadyExisting Current OPERATION_ID(${postJson.operation_id}) is already in API_COLLECTION_NAME($apiCollectionName) ", cc=callContext) {
              apiCollectionEndpoint.isEmpty
            }
            (apiCollectionEndpoint, callContext) <- NewStyle.function.createApiCollectionEndpoint(
              apiCollection.apiCollectionId,
              postJson.operation_id,
              callContext
            )
          } yield {
            (JSONFactory400.createApiCollectionEndpointJsonV400(apiCollectionEndpoint), HttpCode.`201`(callContext))
          }
      }
    }
    staticResourceDocs += ResourceDoc(
      createMyApiCollectionEndpointById,
      implementedInApiVersion,
      nameOf(createMyApiCollectionEndpointById),
      "POST",
      "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoints",
      "Create My Api Collection Endpoint By Id",
      s"""Create Api Collection Endpoint By Id.
         |
         |${Glossary.getGlossaryItem("API Collections")}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      postApiCollectionEndpointJson400,
      apiCollectionEndpointJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val createMyApiCollectionEndpointById: OBPEndpoint = {
      case "my" :: "api-collection-ids" :: apiCollectioId :: "api-collection-endpoints" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionEndpointJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionEndpointJson400]
            }
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectioId, Some(cc))
            apiCollectionEndpoint <- Future{MappedApiCollectionEndpointsProvider.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollection.apiCollectionId, postJson.operation_id)} 
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionEndpointAlreadyExisting Current OPERATION_ID(${postJson.operation_id}) is already in API_COLLECTION_ID($apiCollectioId) ", cc=callContext) {
              apiCollectionEndpoint.isEmpty
            }
            (apiCollectionEndpoint, callContext) <- NewStyle.function.createApiCollectionEndpoint(
              apiCollection.apiCollectionId,
              postJson.operation_id,
              callContext
            )
          } yield {
            (JSONFactory400.createApiCollectionEndpointJsonV400(apiCollectionEndpoint), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyApiCollectionEndpoint,
      implementedInApiVersion,
      nameOf(getMyApiCollectionEndpoint),
      "GET",
      "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints/OPERATION_ID",
      "Get My Api Collection Endpoint",
      s"""Get Api Collection Endpoint By API_COLLECTION_NAME and OPERATION_ID.
         |
         |${authenticationRequiredMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )
    
    lazy val getMyApiCollectionEndpoint: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints" :: operationId :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(cc.userId, apiCollectionName, Some(cc) )
            (apiCollectionEndpoint, callContext) <- NewStyle.function.getApiCollectionEndpointByApiCollectionIdAndOperationId(
              apiCollection.apiCollectionId,
              operationId, 
              Some(cc)
            )
          } yield {
            (JSONFactory400.createApiCollectionEndpointJsonV400(apiCollectionEndpoint), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getApiCollectionEndpoints,
      implementedInApiVersion,
      nameOf(getApiCollectionEndpoints),
      "GET",
      "/api-collections/API_COLLECTION_ID/api-collection-endpoints",
      "Get Api Collection Endpoints",
      s"""Get Api Collection Endpoints By API_COLLECTION_ID.
         |
         |${authenticationRequiredMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointsJson400,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getApiCollectionEndpoints: OBPEndpoint = {
      case "api-collections" :: apiCollectionId :: "api-collection-endpoints" :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollectionEndpoints, callContext) <- NewStyle.function.getApiCollectionEndpoints(apiCollectionId, Some(cc))
          } yield {
            (JSONFactory400.createApiCollectionEndpointsJsonV400(apiCollectionEndpoints), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getMyApiCollectionEndpoints,
      implementedInApiVersion,
      nameOf(getMyApiCollectionEndpoints),
      "GET",
      "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints",
      "Get My Api Collection Endpoints",
      s"""Get Api Collection Endpoints By API_COLLECTION_NAME.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointsJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getMyApiCollectionEndpoints: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints":: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(cc.userId, apiCollectionName, Some(cc) )
            (apiCollectionEndpoints, callContext) <- NewStyle.function.getApiCollectionEndpoints(apiCollection.apiCollectionId, callContext)
          } yield {
            (JSONFactory400.createApiCollectionEndpointsJsonV400(apiCollectionEndpoints), HttpCode.`200`(callContext))
          }
      }
    } 
    
    staticResourceDocs += ResourceDoc(
      getMyApiCollectionEndpointsById,
      implementedInApiVersion,
      nameOf(getMyApiCollectionEndpointsById),
      "GET",
      "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoints",
      "Get My Api Collection Endpoints By Id",
      s"""Get Api Collection Endpoints By API_COLLECTION_ID.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointsJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val getMyApiCollectionEndpointsById: OBPEndpoint = {
      case "my" :: "api-collection-ids" :: apiCollectionId :: "api-collection-endpoints":: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc) )
            (apiCollectionEndpoints, callContext) <- NewStyle.function.getApiCollectionEndpoints(apiCollection.apiCollectionId, callContext)
          } yield {
            (JSONFactory400.createApiCollectionEndpointsJsonV400(apiCollectionEndpoints), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteMyApiCollectionEndpoint,
      implementedInApiVersion,
      nameOf(deleteMyApiCollectionEndpoint),
      "DELETE",
      "/my/api-collections/API_COLLECTION_NAME/api-collection-endpoints/OPERATION_ID",
      "Delete My Api Collection Endpoint",
      s"""${Glossary.getGlossaryItem("API Collections")}
         |
         |
         |Delete Api Collection Endpoint By Id
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val deleteMyApiCollectionEndpoint : OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints" :: operationId :: Nil JsonDelete _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(cc.userId, apiCollectionName, Some(cc) )
            (apiCollectionEndpoint, callContext) <- NewStyle.function.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollection.apiCollectionId, operationId, callContext)
            (deleted, callContext) <- NewStyle.function.deleteApiCollectionEndpointById(apiCollectionEndpoint.apiCollectionEndpointId, callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteMyApiCollectionEndpointById,
      implementedInApiVersion,
      nameOf(deleteMyApiCollectionEndpointById),
      "DELETE",
      "/my/api-collections-ids/API_COLLECTION_ID/api-collection-endpoints/OPERATION_ID",
      "Delete My Api Collection Endpoint By Id",
      s"""${Glossary.getGlossaryItem("API Collections")}
         |
         |Delete Api Collection Endpoint By Id
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val deleteMyApiCollectionEndpointById : OBPEndpoint = {
      case "my" :: "api-collections-ids" :: apiCollectionId :: "api-collection-endpoints" :: operationId :: Nil JsonDelete _ => {
        cc =>
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc) )
            (apiCollectionEndpoint, callContext) <- NewStyle.function.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollection.apiCollectionId, operationId, callContext)
            (deleted, callContext) <- NewStyle.function.deleteApiCollectionEndpointById(apiCollectionEndpoint.apiCollectionEndpointId, callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      createJsonSchemaValidation,
      implementedInApiVersion,
      nameOf(createJsonSchemaValidation),
      "POST",
      "/management/json-schema-validations/OPERATION_ID",
      "Create a JSON Schema Validation",
      s"""Create a JSON Schema Validation.
         |
         |Please supply a json-schema as request body.
         |""",
      postOrPutJsonSchemaV400,
      responseJsonSchema,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation, apiTagNewStyle),
      Some(List(canCreateJsonSchemaValidation)))


    lazy val createJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonPost _ -> _ => {
        cc =>
          val Some(httpBody): Option[String] = cc.httpBody
          for {
            (Full(u), callContext) <- SS.user

            schemaErrors = JsonSchemaUtil.validateSchema(httpBody)
            _ <- Helper.booleanToFuture(failMsg = s"$JsonSchemaIllegal${StringUtils.join(schemaErrors, "; ")}", cc=callContext) {
              CollectionUtils.isEmpty(schemaErrors)
            }

            (isExists, callContext) <- NewStyle.function.isJsonSchemaValidationExists(operationId, callContext)
            _ <- Helper.booleanToFuture(failMsg = OperationIdExistsError, cc=callContext) {
              !isExists
            }
            (validation, callContext) <- NewStyle.function.createJsonSchemaValidation(JsonValidation(operationId, httpBody), callContext)
          } yield {
            (validation, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateJsonSchemaValidation,
      implementedInApiVersion,
      nameOf(updateJsonSchemaValidation),
      "PUT",
      "/management/json-schema-validations/OPERATION_ID",
      "Update a JSON Schema Validation",
      s"""Update a JSON Schema Validation.
         |
         |Please supply a json-schema as request body
         |""",
      postOrPutJsonSchemaV400,
      responseJsonSchema,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation, apiTagNewStyle),
      Some(List(canUpdateJsonSchemaValidation)))


    lazy val updateJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonPut _ -> _ => {
        cc =>
          val Some(httpBody): Option[String] = cc.httpBody
          for {
            (Full(u), callContext) <- SS.user

            schemaErrors = JsonSchemaUtil.validateSchema(httpBody)
            _ <- Helper.booleanToFuture(failMsg = s"$JsonSchemaIllegal${StringUtils.join(schemaErrors, "; ")}", cc=callContext) {
              CollectionUtils.isEmpty(schemaErrors)
            }

            (isExists, callContext) <- NewStyle.function.isJsonSchemaValidationExists(operationId, callContext)
            _ <- Helper.booleanToFuture(failMsg = JsonSchemaValidationNotFound, cc=callContext) {
              isExists
            }
            (validation, callContext) <- NewStyle.function.updateJsonSchemaValidation(operationId, httpBody, callContext)
          } yield {
            (validation, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteJsonSchemaValidation,
      implementedInApiVersion,
      nameOf(deleteJsonSchemaValidation),
      "DELETE",
      "/management/json-schema-validations/OPERATION_ID",
      "Delete a JSON Schema Validation",
      s"""Delete a JSON Schema Validation by operation_id.
         |
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation, apiTagNewStyle),
      Some(List(canDeleteJsonSchemaValidation)))


    lazy val deleteJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user

            (isExists, callContext) <- NewStyle.function.isJsonSchemaValidationExists(operationId, callContext)
            _ <- Helper.booleanToFuture(failMsg = JsonSchemaValidationNotFound, cc=callContext) {
              isExists
            }

            (deleteResult, callContext) <- NewStyle.function.deleteJsonSchemaValidation(operationId, callContext)
          } yield {
            (deleteResult, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getJsonSchemaValidation,
      implementedInApiVersion,
      nameOf(getJsonSchemaValidation),
      "GET",
      "/management/json-schema-validations/OPERATION_ID",
      "Get a JSON Schema Validation",
      s"""Get a JSON Schema Validation by operation_id.
         |
         |""",
      EmptyBody,
      responseJsonSchema,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation, apiTagNewStyle),
      Some(List(canGetJsonSchemaValidation)))

    lazy val getJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonGet _ => {
        cc =>
          for {
            (validation, callContext) <- NewStyle.function.getJsonSchemaValidationByOperationId(operationId, cc.callContext)
          } yield {
            (validation, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllJsonSchemaValidations,
      implementedInApiVersion,
      nameOf(getAllJsonSchemaValidations),
      "GET",
      "/management/json-schema-validations",
      "Get all JSON Schema Validations",
      s"""Get all JSON Schema Validations.
         |
         |""",
      EmptyBody,
      ListResult("json_schema_validations", responseJsonSchema::Nil),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation, apiTagNewStyle),
      Some(List(canGetJsonSchemaValidation)))


    lazy val getAllJsonSchemaValidations: OBPEndpoint = {
      case ("management" | "endpoints") :: "json-schema-validations" :: Nil JsonGet _ => {
        cc =>
          for {
            (jsonSchemaValidations, callContext) <- NewStyle.function.getJsonSchemaValidations(cc.callContext)
          } yield {
            (ListResult("json_schema_validations", jsonSchemaValidations), HttpCode.`200`(callContext))
          }
      }
    }

    private val jsonSchemaValidationRequiresRole: Boolean = APIUtil.getPropsAsBoolValue("read_json_schema_validation_requires_role", false)
    lazy val getAllJsonSchemaValidationsPublic = getAllJsonSchemaValidations

    staticResourceDocs += ResourceDoc(
      getAllJsonSchemaValidationsPublic,
      implementedInApiVersion,
      nameOf(getAllJsonSchemaValidationsPublic),
      "GET",
      "/endpoints/json-schema-validations",
      "Get all JSON Schema Validations - public",
      s"""Get all JSON Schema Validations - public.
         |
         |""",
      EmptyBody,
      ListResult("json_schema_validations", responseJsonSchema::Nil),
      (if (jsonSchemaValidationRequiresRole) List($UserNotLoggedIn) else Nil)
        ::: List(
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation, apiTagNewStyle),
      None)


    // auth type validation related endpoints
    private val allowedAuthTypes = AuthenticationType.values.filterNot(AuthenticationType.Anonymous==)
    staticResourceDocs += ResourceDoc(
      createAuthenticationTypeValidation,
      implementedInApiVersion,
      nameOf(createAuthenticationTypeValidation),
      "POST",
      "/management/authentication-type-validations/OPERATION_ID",
      "Create an Authentication Type Validation",
      s"""Create an Authentication Type Validation.
         |
         |Please supply allowed authentication types.
         |""",
      allowedAuthTypes,
      JsonAuthTypeValidation("OBPv4.0.0-updateXxx", allowedAuthTypes),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAuthenticationTypeValidation, apiTagNewStyle),
      Some(List(canCreateAuthenticationTypeValidation)))


    lazy val createAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonPost jArray -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user

            authTypes <- NewStyle.function.tryons(s"$AuthenticationTypeNameIllegal Allowed Authentication Type names: ${allowedAuthTypes.mkString("[", ", ", "]")}", 400, cc.callContext) {
              jArray.extract[List[AuthenticationType]]
            }

            (isExists, callContext) <- NewStyle.function.isAuthenticationTypeValidationExists(operationId, callContext)
            _ <- Helper.booleanToFuture(failMsg = OperationIdExistsError, cc=callContext) {
              !isExists
            }
            (authenticationTypeValidation, callContext) <- NewStyle.function.createAuthenticationTypeValidation(JsonAuthTypeValidation(operationId, authTypes), callContext)
          } yield {
            (authenticationTypeValidation, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAuthenticationTypeValidation,
      implementedInApiVersion,
      nameOf(updateAuthenticationTypeValidation),
      "PUT",
      "/management/authentication-type-validations/OPERATION_ID",
      "Update an Authentication Type Validation",
      s"""Update an Authentication Type Validation.
         |
         |Please supply allowed authentication types.
         |""",
      allowedAuthTypes,
      JsonAuthTypeValidation("OBPv4.0.0-updateXxx", allowedAuthTypes),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAuthenticationTypeValidation, apiTagNewStyle),
      Some(List(canUpdateAuthenticationTypeValidation)))


    lazy val updateAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonPut jArray -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user

            authTypes <- NewStyle.function.tryons(s"$AuthenticationTypeNameIllegal Allowed AuthenticationType names: ${allowedAuthTypes.mkString("[", ", ", "]")}", 400, cc.callContext) {
              jArray.extract[List[AuthenticationType]]
            }

            (isExists, callContext) <- NewStyle.function.isAuthenticationTypeValidationExists(operationId, callContext)
            _ <- Helper.booleanToFuture(failMsg = AuthenticationTypeValidationNotFound, cc=callContext) {
              isExists
            }
            (authenticationTypeValidation, callContext) <- NewStyle.function.updateAuthenticationTypeValidation(operationId, authTypes, callContext)
          } yield {
            (authenticationTypeValidation, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteAuthenticationTypeValidation,
      implementedInApiVersion,
      nameOf(deleteAuthenticationTypeValidation),
      "DELETE",
      "/management/authentication-type-validations/OPERATION_ID",
      "Delete an Authentication Type Validation",
      s"""Delete an Authentication Type Validation by operation_id.
         |
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAuthenticationTypeValidation, apiTagNewStyle),
      Some(List(canDeleteAuthenticationValidation)))


    lazy val deleteAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user

            (isExists, callContext) <- NewStyle.function.isAuthenticationTypeValidationExists(operationId, callContext)
            _ <- Helper.booleanToFuture(failMsg = AuthenticationTypeValidationNotFound, cc=callContext) {
              isExists
            }

            (deleteResult, callContext) <- NewStyle.function.deleteAuthenticationTypeValidation(operationId, callContext)
          } yield {
            (deleteResult, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAuthenticationTypeValidation,
      implementedInApiVersion,
      nameOf(getAuthenticationTypeValidation),
      "GET",
      "/management/authentication-type-validations/OPERATION_ID",
      "Get an Authentication Type Validation",
      s"""Get an Authentication Type Validation by operation_id.
         |
         |""",
      EmptyBody,
      JsonAuthTypeValidation("OBPv4.0.0-updateXxx", allowedAuthTypes),
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAuthenticationTypeValidation, apiTagNewStyle),
      Some(List(canGetAuthenticationTypeValidation)))


    lazy val getAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonGet _ => {
        cc =>
          for {
            (authenticationTypeValidation, callContext) <- NewStyle.function.getAuthenticationTypeValidationByOperationId(operationId, cc.callContext)
          } yield {
            (authenticationTypeValidation, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllAuthenticationTypeValidations,
      implementedInApiVersion,
      nameOf(getAllAuthenticationTypeValidations),
      "GET",
      "/management/authentication-type-validations",
      "Get all Authentication Type Validations",
      s"""Get all Authentication Type Validations.
         |
         |""",
      EmptyBody,
      ListResult("authentication_types_validations",List(JsonAuthTypeValidation("OBPv4.0.0-updateXxx", allowedAuthTypes))),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAuthenticationTypeValidation, apiTagNewStyle),
      Some(List(canGetAuthenticationTypeValidation)))


    lazy val getAllAuthenticationTypeValidations: OBPEndpoint = {
      case ("management" | "endpoints") :: "authentication-type-validations" :: Nil JsonGet _ => {
        cc =>
          for {
            (authenticationTypeValidations, callContext) <- NewStyle.function.getAuthenticationTypeValidations(cc.callContext)
          } yield {
            (ListResult("authentication_types_validations", authenticationTypeValidations), HttpCode.`200`(callContext))
          }
      }
    }

    private val authenticationTypeValidationRequiresRole: Boolean = APIUtil.getPropsAsBoolValue("read_authentication_type_validation_requires_role", false)
    lazy val getAllAuthenticationTypeValidationsPublic = getAllAuthenticationTypeValidations

    staticResourceDocs += ResourceDoc(
      getAllAuthenticationTypeValidationsPublic,
      implementedInApiVersion,
      nameOf(getAllAuthenticationTypeValidationsPublic),
      "GET",
      "/endpoints/authentication-type-validations",
      "Get all Authentication Type Validations - public",
      s"""Get all Authentication Type Validations - public.
         |
         |""",
      EmptyBody,
      ListResult("authentication_types_validations",List(JsonAuthTypeValidation("OBPv4.0.0-updateXxx", allowedAuthTypes))),
      (if (authenticationTypeValidationRequiresRole) List($UserNotLoggedIn) else Nil)
        ::: List(
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAuthenticationTypeValidation, apiTagNewStyle),
      None)

    staticResourceDocs += ResourceDoc(
      createConnectorMethod,
      implementedInApiVersion,
      nameOf(createConnectorMethod),
      "POST",
      "/management/connector-methods",
      "Create Connector Method",
      s"""Create an internal connector.
         |
         |The method_body is URL-encoded format String
         |""",
      jsonConnectorMethod.copy(connectorMethodId=None),
      jsonConnectorMethod,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagConnectorMethod, apiTagNewStyle),
      Some(List(canCreateConnectorMethod)))

    lazy val createConnectorMethod: OBPEndpoint = {
      case "management" :: "connector-methods" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            jsonConnectorMethod <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonConnectorMethod", 400, cc.callContext) {
              json.extract[JsonConnectorMethod]
            }
            
            (isExists, callContext) <- NewStyle.function.connectorMethodNameExists(jsonConnectorMethod.methodName, Some(cc))
            _ <- Helper.booleanToFuture(failMsg = s"$ConnectorMethodAlreadyExists Please use a different method_name(${jsonConnectorMethod.methodName})", cc=callContext) {
              (!isExists)
            }
            connectorMethod = InternalConnector.createFunction(jsonConnectorMethod.methodName, jsonConnectorMethod.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            
            (connectorMethod, callContext) <- NewStyle.function.createJsonConnectorMethod(jsonConnectorMethod, callContext)
          } yield {
            (connectorMethod, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateConnectorMethod,
      implementedInApiVersion,
      nameOf(updateConnectorMethod),
      "PUT",
      "/management/connector-methods/CONNECTOR_METHOD_ID",
      "Update Connector Method",
      s"""Update an internal connector.
         |
         |The method_body is URL-encoded format String
         |""",
      jsonConnectorMethodMethodBody,
      jsonConnectorMethod,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagConnectorMethod, apiTagNewStyle),
      Some(List(canUpdateConnectorMethod)))

    lazy val updateConnectorMethod: OBPEndpoint = {
      case "management" :: "connector-methods" :: connectorMethodId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            connectorMethodBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonConnectorMethod", 400, cc.callContext) {
              json.extract[JsonConnectorMethodMethodBody]
            }

            (cm, callContext) <- NewStyle.function.getJsonConnectorMethodById(connectorMethodId, cc.callContext)

            connectorMethod = InternalConnector.createFunction(cm.methodName, connectorMethodBody.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            (connectorMethod, callContext) <- NewStyle.function.updateJsonConnectorMethod(connectorMethodId, connectorMethodBody.methodBody, callContext)
          } yield {
            (connectorMethod, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getConnectorMethod,
      implementedInApiVersion,
      nameOf(getConnectorMethod),
      "GET",
      "/management/connector-methods/CONNECTOR_METHOD_ID",
      "Get Connector Method by Id",
      s"""Get an internal connector by CONNECTOR_METHOD_ID.
         |
         |""",
      EmptyBody,
      jsonConnectorMethod,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConnectorMethod, apiTagNewStyle),
      Some(List(canGetConnectorMethod)))

    lazy val getConnectorMethod: OBPEndpoint = {
      case "management" :: "connector-methods" :: connectorMethodId :: Nil JsonGet _ => {
        cc =>
          for {
            (connectorMethod, callContext) <- NewStyle.function.getJsonConnectorMethodById(connectorMethodId, cc.callContext)
          } yield {
            (connectorMethod, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllConnectorMethods,
      implementedInApiVersion,
      nameOf(getAllConnectorMethods),
      "GET",
      "/management/connector-methods",
      "Get all Connector Methods",
      s"""Get all Connector Methods.
         |
         |""",
      EmptyBody,
      ListResult("connectors_methods", jsonConnectorMethod::Nil),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConnectorMethod, apiTagNewStyle),
      Some(List(canGetAllConnectorMethods)))

    lazy val getAllConnectorMethods: OBPEndpoint = {
      case "management" :: "connector-methods" :: Nil JsonGet _ => {
        cc =>
          for {
            (connectorMethods, callContext) <- NewStyle.function.getJsonConnectorMethods(cc.callContext)
          } yield {
            (ListResult("connector_methods", connectorMethods), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(createDynamicResourceDoc),
      "POST",
      "/management/dynamic-resource-docs",
      "Create Dynamic Resource Doc",
      s"""Create a Dynamic Resource Doc.
         |
         |The connector_method_body is URL-encoded format String
         |""",
      jsonDynamicResourceDoc.copy(dynamicResourceDocId=None),
      jsonDynamicResourceDoc,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc, apiTagNewStyle),
      Some(List(canCreateDynamicResourceDoc)))

    lazy val createDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            jsonDynamicResourceDoc <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicResourceDoc", 400, cc.callContext) {
              json.extract[JsonDynamicResourceDoc]
            }
            _ <- Helper.booleanToFuture(failMsg = s"""$InvalidJsonFormat The request_verb must be one of ["POST", "PUT", "GET", "DELETE"]""", cc=cc.callContext) {
              Set("POST", "PUT", "GET", "DELETE").contains(jsonDynamicResourceDoc.requestVerb)
            }
            _ <- Helper.booleanToFuture(failMsg = s"""$InvalidJsonFormat When request_verb is "GET" or "DELETE", the example_request_body must be a blank String "" or just totally omit the field""", cc=cc.callContext) {
              (jsonDynamicResourceDoc.requestVerb, jsonDynamicResourceDoc.exampleRequestBody) match {
                case ("GET" | "DELETE", Some(JString(s))) => //we support the empty string "" here
                  StringUtils.isBlank(s)
                case ("GET" | "DELETE", Some(requestBody)) => //we add the guard, we forbid any json objects in GET/DELETE request body.
                  requestBody == JNothing
                case _ => true
              }
            }
            _ = try {
              CompiledObjects(jsonDynamicResourceDoc.exampleRequestBody, jsonDynamicResourceDoc.successResponseBody, jsonDynamicResourceDoc.methodBody)
            } catch {
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            (isExists, callContext) <- NewStyle.function.isJsonDynamicResourceDocExists(jsonDynamicResourceDoc.requestVerb, jsonDynamicResourceDoc.requestUrl, Some(cc))
            _ <- Helper.booleanToFuture(failMsg = s"$DynamicResourceDocAlreadyExists The combination of request_url(${jsonDynamicResourceDoc.requestUrl}) and request_verb(${jsonDynamicResourceDoc.requestVerb}) must be unique", cc=callContext) {
              (!isExists)
            }

            (dynamicResourceDoc, callContext) <- NewStyle.function.createJsonDynamicResourceDoc(jsonDynamicResourceDoc, callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(updateDynamicResourceDoc),
      "PUT",
      "/management/dynamic-resource-docs/DYNAMIC-RESOURCE-DOC-ID",
      "Update Dynamic Resource Doc",
      s"""Update a Dynamic Resource Doc.
         |
         |The connector_method_body is URL-encoded format String
         |""",
      jsonDynamicResourceDoc.copy(dynamicResourceDocId = None),
      jsonDynamicResourceDoc,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc, apiTagNewStyle),
      Some(List(canUpdateDynamicResourceDoc)))

    lazy val updateDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            dynamicResourceDocBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicResourceDoc", 400, cc.callContext) {
              json.extract[JsonDynamicResourceDoc]
            }

            _ <- Helper.booleanToFuture(failMsg = s"""$InvalidJsonFormat The request_verb must be one of ["POST", "PUT", "GET", "DELETE"]""", cc=cc.callContext) {
              Set("POST", "PUT", "GET", "DELETE").contains(dynamicResourceDocBody.requestVerb)
            }

            _ <- Helper.booleanToFuture(failMsg = s"""$InvalidJsonFormat When request_verb is "GET" or "DELETE", the example_request_body must be a blank String""", cc=cc.callContext) {
              (dynamicResourceDocBody.requestVerb, dynamicResourceDocBody.exampleRequestBody) match {
                case ("GET" | "DELETE", Some(JString(s))) =>
                  StringUtils.isBlank(s)
                case ("GET" | "DELETE", Some(requestBody)) =>
                  requestBody == JNothing
                case _ => true
              }
            }

            _ = try {
              CompiledObjects(jsonDynamicResourceDoc.exampleRequestBody, jsonDynamicResourceDoc.successResponseBody, jsonDynamicResourceDoc.methodBody)
            } catch {
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            (_, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(dynamicResourceDocId, cc.callContext)

            (dynamicResourceDoc, callContext) <- NewStyle.function.updateJsonDynamicResourceDoc(dynamicResourceDocBody.copy(dynamicResourceDocId = Some(dynamicResourceDocId)), callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(deleteDynamicResourceDoc),
      "DELETE",
      "/management/dynamic-resource-docs/DYNAMIC-RESOURCE-DOC-ID",
      "Delete Dynamic Resource Doc",
      s"""Delete a Dynamic Resource Doc.
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc, apiTagNewStyle),
      Some(List(canDeleteDynamicResourceDoc)))

    lazy val deleteDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(dynamicResourceDocId, cc.callContext)
            (dynamicResourceDoc, callContext) <- NewStyle.function.deleteJsonDynamicResourceDocById(dynamicResourceDocId, callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(getDynamicResourceDoc),
      "GET",
      "/management/dynamic-resource-docs/DYNAMIC-RESOURCE-DOC-ID",
      "Get Dynamic Resource Doc by Id",
      s"""Get a Dynamic Resource Doc by DYNAMIC-RESOURCE-DOC-ID.
         |
         |""",
      EmptyBody,
      jsonDynamicResourceDoc,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc, apiTagNewStyle),
      Some(List(canGetDynamicResourceDoc)))

    lazy val getDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonGet _ => {
        cc =>
          for {
            (dynamicResourceDoc, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(dynamicResourceDocId, cc.callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllDynamicResourceDocs,
      implementedInApiVersion,
      nameOf(getAllDynamicResourceDocs),
      "GET",
      "/management/dynamic-resource-docs",
      "Get all Dynamic Resource Docs",
      s"""Get all Dynamic Resource Docs.
         |
         |""",
      EmptyBody,
      ListResult("dynamic-resource-docs", jsonDynamicResourceDoc::Nil),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc, apiTagNewStyle),
      Some(List(canGetAllDynamicResourceDocs)))

    lazy val getAllDynamicResourceDocs: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: Nil JsonGet _ => {
        cc =>
          for {
            (dynamicResourceDocs, callContext) <- NewStyle.function.getJsonDynamicResourceDocs(cc.callContext)
          } yield {
            (ListResult("dynamic-resource-docs", dynamicResourceDocs), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      buildDynamicEndpointTemplate,
      implementedInApiVersion,
      nameOf(buildDynamicEndpointTemplate),
      "POST",
      "/management/dynamic-resource-docs/endpoint-code",
      "Create Dynamic Resource Doc endpoint code",
      s"""Create a Dynamic Resource Doc endpoint code.
         |
         |copy the response and past to ${nameOf(PractiseEndpoint)}, So you can have the benefits of
         |auto compilation and debug
         |""",
      jsonResourceDocFragment,
      jsonCodeTemplate,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc, apiTagNewStyle),
      None)

    lazy val buildDynamicEndpointTemplate: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: "endpoint-code" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            resourceDocFragment <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ResourceDocFragment", 400, cc.callContext) {
              json.extract[ResourceDocFragment]
            }
            _ <- Helper.booleanToFuture(failMsg = s"""$InvalidJsonFormat The request_verb must be one of ["POST", "PUT", "GET", "DELETE"]""", cc=cc.callContext) {
               Set("POST", "PUT", "GET", "DELETE").contains(resourceDocFragment.requestVerb)
            }

            _ <- Helper.booleanToFuture(failMsg = s"""$InvalidJsonFormat When request_verb is "GET" or "DELETE", the example_request_body must be a blank String""", cc=cc.callContext) {
              (resourceDocFragment.requestVerb, resourceDocFragment.exampleRequestBody) match {
                case ("GET" | "DELETE", Some(JString(s))) =>
                  StringUtils.isBlank(s)
                case ("GET" | "DELETE", Some(requestBody)) =>
                  requestBody == JNothing
                case _ => true
              }
            }

            code = DynamicEndpointCodeGenerator.buildTemplate(resourceDocFragment)

          } yield {
            ("code" -> URLEncoder.encode(code, "UTF-8"), HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(createDynamicMessageDoc),
      "POST",
      "/management/dynamic-message-docs",
      "Create Dynamic Message Doc",
      s"""Create a Dynamic Message Doc.
         |""",
      jsonDynamicMessageDoc.copy(dynamicMessageDocId=None),
      jsonDynamicMessageDoc,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc, apiTagNewStyle),
      Some(List(canCreateDynamicMessageDoc)))

    lazy val createDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            dynamicMessageDoc <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicMessageDoc", 400, cc.callContext) {
              json.extract[JsonDynamicMessageDoc]
            }
            (dynamicMessageDocExisted, callContext) <- NewStyle.function.isJsonDynamicMessageDocExists(dynamicMessageDoc.process, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$DynamicMessageDocAlreadyExists The json body process(${dynamicMessageDoc.process}) already exists", cc=callContext) {
              (!dynamicMessageDocExisted)
            }
            connectorMethod = DynamicConnector.createFunction(dynamicMessageDoc.process, dynamicMessageDoc.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            (dynamicMessageDoc, callContext) <- NewStyle.function.createJsonDynamicMessageDoc(dynamicMessageDoc, callContext)
          } yield {
            (dynamicMessageDoc, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(updateDynamicMessageDoc),
      "PUT",
      "/management/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID",
      "Update Dynamic Message Doc",
      s"""Update a Dynamic Message Doc.
         |""",
      jsonDynamicMessageDoc.copy(dynamicMessageDocId=None),
      jsonDynamicMessageDoc,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc, apiTagNewStyle),
      Some(List(canUpdateDynamicMessageDoc)))

    lazy val updateDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            dynamicMessageDocBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicMessageDoc", 400, cc.callContext) {
              json.extract[JsonDynamicMessageDoc]
            }
            connectorMethod = DynamicConnector.createFunction(dynamicMessageDocBody.process, dynamicMessageDocBody.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=cc.callContext) {
              connectorMethod.isDefined
            }
            (_, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(dynamicMessageDocId, cc.callContext)
            (dynamicMessageDoc, callContext) <- NewStyle.function.updateJsonDynamicMessageDoc(dynamicMessageDocBody.copy(dynamicMessageDocId=Some(dynamicMessageDocId)), callContext)
          } yield {
            (dynamicMessageDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(getDynamicMessageDoc),
      "GET",
      "/management/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID",
      "Get Dynamic Message Doc",
      s"""Get a Dynamic Message Doc by DYNAMIC_MESSAGE_DOC_ID.
         |
         |""",
      EmptyBody,
      jsonDynamicMessageDoc,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc, apiTagNewStyle),
      Some(List(canGetDynamicMessageDoc)))

    lazy val getDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonGet _ => {
        cc =>
          for {
            (dynamicMessageDoc, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(dynamicMessageDocId, cc.callContext)
          } yield {
            (dynamicMessageDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllDynamicMessageDocs,
      implementedInApiVersion,
      nameOf(getAllDynamicMessageDocs),
      "GET",
      "/management/dynamic-message-docs",
      "Get all Dynamic Message Docs",
      s"""Get all Dynamic Message Docs.
         |
         |""",
      EmptyBody,
      ListResult("dynamic-message-docs", jsonDynamicMessageDoc::Nil),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc, apiTagNewStyle),
      Some(List(canGetAllDynamicMessageDocs)))

    lazy val getAllDynamicMessageDocs: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: Nil JsonGet _ => {
        cc =>
          for {
            (dynamicMessageDocs, callContext) <- NewStyle.function.getJsonDynamicMessageDocs(cc.callContext)
          } yield {
            (ListResult("dynamic-message-docs", dynamicMessageDocs), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(deleteDynamicMessageDoc),
      "DELETE",
      "/management/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID",
      "Delete Dynamic Message Doc",
      s"""Delete a Dynamic Message Doc.
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc, apiTagNewStyle),
      Some(List(canDeleteDynamicMessageDoc)))

    lazy val deleteDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(dynamicMessageDocId, cc.callContext)
            (dynamicResourceDoc, callContext) <- NewStyle.function.deleteJsonDynamicMessageDocById(dynamicMessageDocId, callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createEndpointMapping,
      implementedInApiVersion,
      nameOf(createEndpointMapping),
      "POST",
      "/management/endpoint-mappings",
      "Create Endpoint Mapping",
      s"""Create an Endpoint Mapping. 
         |
         |Note: at moment only support the dynamic endpoints
         |""",
      endpointMappingRequestBodyExample,
      endpointMappingResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canCreateEndpointMapping)))

    lazy val createEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: Nil JsonPost json -> _ => {
        cc =>
          createEndpointMappingMethod(None, json, cc)
      }
    }

    private def createEndpointMappingMethod(bankId: Option[String],json: JValue, cc: CallContext) = {
      for {
        endpointMapping <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[EndpointMappingCommons]}", 400, cc.callContext) {
          json.extract[EndpointMappingCommons].copy(bankId= bankId)
        }
        (endpointMapping, callContext) <- NewStyle.function.createOrUpdateEndpointMapping(bankId, 
          endpointMapping.copy(endpointMappingId = None, bankId= bankId), // create need to make sure, endpointMappingId is None, and bankId must be from URL.
          cc.callContext)
      } yield {
        val commonsData: EndpointMappingCommons = endpointMapping
        (commonsData.toJson, HttpCode.`201`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      updateEndpointMapping,
      implementedInApiVersion,
      nameOf(updateEndpointMapping),
      "PUT",
      "/management/endpoint-mappings/ENDPOINT_MAPPING_ID",
      "Update Endpoint Mapping",
      s"""Update an Endpoint Mapping.
         |""",
      endpointMappingRequestBodyExample,
      endpointMappingResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canUpdateEndpointMapping)))

    lazy val updateEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: endpointMappingId :: Nil JsonPut json -> _ => {
        cc =>
          updateEndpointMappingMethod(None, endpointMappingId, json, cc)
      }
    }

    private def updateEndpointMappingMethod(bankId: Option[String], endpointMappingId: String, json: JValue, cc: CallContext) = {
      for {
        endpointMappingBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[EndpointMappingCommons]}", 400, cc.callContext) {
          json.extract[EndpointMappingCommons].copy(bankId = bankId)
        }
        (endpointMapping, callContext) <- NewStyle.function.getEndpointMappingById(bankId, endpointMappingId, cc.callContext)
        _ <-  Helper.booleanToFuture(s"$InvalidJsonFormat operation_id has to be the same in the URL (${endpointMapping.operationId}) and Body (${endpointMappingBody.operationId}). ", 400, cc.callContext){
          endpointMapping.operationId == endpointMappingBody.operationId
        }
        (endpointMapping, callContext) <- NewStyle.function.createOrUpdateEndpointMapping(
          bankId, 
          endpointMappingBody.copy(endpointMappingId = Some(endpointMappingId), bankId = bankId), //Update must set the endpointId and BankId must be from URL
          callContext)
      } yield {
        val commonsData: EndpointMappingCommons = endpointMapping
        (commonsData.toJson, HttpCode.`201`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      getEndpointMapping,
      implementedInApiVersion,
      nameOf(getEndpointMapping),
      "GET",
      "/management/endpoint-mappings/ENDPOINT_MAPPING_ID",
      "Get Endpoint Mapping by Id",
      s"""Get an Endpoint Mapping by ENDPOINT_MAPPING_ID.
         |
         |""",
      EmptyBody,
      endpointMappingResponseBodyExample,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canGetEndpointMapping)))

    lazy val getEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: endpointMappingId :: Nil JsonGet _ => {
        cc =>
          getEndpointMappingMethod(None, endpointMappingId, cc)
      }
    }

    private def getEndpointMappingMethod(bankId: Option[String], endpointMappingId: String, cc: CallContext) = {
      for {
        (endpointMapping, callContext) <- NewStyle.function.getEndpointMappingById(bankId, endpointMappingId, cc.callContext)
      } yield {
        val commonsData: EndpointMappingCommons = endpointMapping
        (commonsData.toJson, HttpCode.`201`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllEndpointMappings,
      implementedInApiVersion,
      nameOf(getAllEndpointMappings),
      "GET",
      "/management/endpoint-mappings",
      "Get all Endpoint Mappings",
      s"""Get all Endpoint Mappings.
         |
         |""",
      EmptyBody,
      ListResult("endpoint-mappings", endpointMappingResponseBodyExample::Nil),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canGetAllEndpointMappings)))

    lazy val getAllEndpointMappings: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: Nil JsonGet _ => {
        cc =>
          getEndpointMappingsMethod(None, cc)
      }
    }

    private def getEndpointMappingsMethod(bankId: Option[String], cc: CallContext) = {
      for {
        (endpointMappings, callContext) <- NewStyle.function.getEndpointMappings(bankId, cc.callContext)
      } yield {
        val listCommons: List[EndpointMappingCommons] = endpointMappings
        (ListResult("endpoint-mappings", listCommons.map(_.toJson)), HttpCode.`200`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteEndpointMapping,
      implementedInApiVersion,
      nameOf(deleteEndpointMapping),
      "DELETE",
      "/management/endpoint-mappings/ENDPOINT_MAPPING_ID",
      "Delete Endpoint Mapping",
      s"""Delete a Endpoint Mapping.
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canDeleteEndpointMapping)))

    lazy val deleteEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: endpointMappingId :: Nil JsonDelete _ => {
        cc =>
          deleteEndpointMappingMethod(None, endpointMappingId, cc)
      }
    }
    
    private def deleteEndpointMappingMethod(bankId: Option[String], endpointMappingId: String, cc: CallContext) = {
      for {
        (deleted, callContext) <- NewStyle.function.deleteEndpointMapping(bankId, endpointMappingId, cc.callContext)
      } yield {
        (deleted, HttpCode.`200`(callContext))
      }
    }

    staticResourceDocs += ResourceDoc(
      createBankLevelEndpointMapping,
      implementedInApiVersion,
      nameOf(createBankLevelEndpointMapping),
      "POST",
      "/management/banks/BANK_ID/endpoint-mappings",
      "Create Bank Level Endpoint Mapping",
      s"""Create an Bank Level Endpoint Mapping. 
         |
         |Note: at moment only support the dynamic endpoints
         |""",
      endpointMappingRequestBodyExample,
      endpointMappingResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canCreateBankLevelEndpointMapping, canCreateEndpointMapping)))

    lazy val createBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: Nil JsonPost json -> _ => {
        cc =>
          createEndpointMappingMethod(Some(bankId), json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      updateBankLevelEndpointMapping,
      implementedInApiVersion,
      nameOf(updateBankLevelEndpointMapping),
      "PUT",
      "/management/banks/BANK_ID/endpoint-mappings/ENDPOINT_MAPPING_ID",
      "Update Bank Level Endpoint Mapping",
      s"""Update an Bank Level Endpoint Mapping.
         |""",
      endpointMappingRequestBodyExample,
      endpointMappingResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canUpdateBankLevelEndpointMapping, canUpdateEndpointMapping)))

    lazy val updateBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: endpointMappingId :: Nil JsonPut json -> _ => {
        cc =>
          updateEndpointMappingMethod(Some(bankId), endpointMappingId, json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelEndpointMapping,
      implementedInApiVersion,
      nameOf(getBankLevelEndpointMapping),
      "GET",
      "/management/banks/BANK_ID/endpoint-mappings/ENDPOINT_MAPPING_ID",
      "Get Bank Level Endpoint Mapping",
      s"""Get an Bank Level Endpoint Mapping by ENDPOINT_MAPPING_ID.
         |
         |""",
      EmptyBody,
      endpointMappingResponseBodyExample,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canGetBankLevelEndpointMapping, canGetEndpointMapping)))

    lazy val getBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: endpointMappingId :: Nil JsonGet _ => {
        cc =>
          getEndpointMappingMethod(Some(bankId), endpointMappingId, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllBankLevelEndpointMappings,
      implementedInApiVersion,
      nameOf(getAllBankLevelEndpointMappings),
      "GET",
      "/management/banks/BANK_ID/endpoint-mappings",
      "Get all Bank Level Endpoint Mappings",
      s"""Get all Bank Level Endpoint Mappings.
         |
         |""",
      EmptyBody,
      ListResult("endpoint-mappings", endpointMappingResponseBodyExample::Nil),
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canGetAllBankLevelEndpointMappings, canGetAllEndpointMappings)))

    lazy val getAllBankLevelEndpointMappings: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: Nil JsonGet _ => {
        cc =>
          getEndpointMappingsMethod(Some(bankId), cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteBankLevelEndpointMapping,
      implementedInApiVersion,
      nameOf(deleteBankLevelEndpointMapping),
      "DELETE",
      "/management/banks/BANK_ID/endpoint-mappings/ENDPOINT_MAPPING_ID",
      "Delete Bank Level Endpoint Mapping",
      s"""Delete a Bank Level Endpoint Mapping.
         |""",
      EmptyBody,
      BooleanBody(true),
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagEndpointMapping, apiTagNewStyle),
      Some(List(canDeleteBankLevelEndpointMapping, canDeleteEndpointMapping)))

    lazy val deleteBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: endpointMappingId :: Nil JsonDelete _ => {
        cc =>
          deleteEndpointMappingMethod(Some(bankId), endpointMappingId, cc)
      }
    }
    
    staticResourceDocs += ResourceDoc(
      updateAtmSupportedCurrencies,
      implementedInApiVersion,
      nameOf(updateAtmSupportedCurrencies),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/supported-currencies",
      "Update ATM Supported Currencies",
      s"""Update ATM Supported Currencies.
         |""",
      supportedCurrenciesJson,
      atmSupportedCurrenciesJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    
    lazy val updateAtmSupportedCurrencies : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "supported-currencies" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            supportedCurrencies <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[SupportedCurrenciesJson]}", 400, cc.callContext) {
              json.extract[SupportedCurrenciesJson].supported_currencies
            }
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atm, callContext) <- NewStyle.function.updateAtmSupportedCurrencies(bankId, atmId, supportedCurrencies, cc.callContext)
          } yield {
            (AtmSupportedCurrenciesJson(atm.atmId.value, atm.supportedCurrencies.getOrElse(Nil)), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAtmSupportedLanguages,
      implementedInApiVersion,
      nameOf(updateAtmSupportedLanguages),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/supported-languages",
      "Update ATM Supported Languages",
      s"""Update ATM Supported Languages.
         |""",
      supportedLanguagesJson,
      atmSupportedLanguagesJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    
    lazy val updateAtmSupportedLanguages : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "supported-languages" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            supportedLanguages <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[SupportedLanguagesJson]}", 400, cc.callContext) {
              json.extract[SupportedLanguagesJson].supported_languages
            }
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atm, callContext) <- NewStyle.function.updateAtmSupportedLanguages(bankId, atmId, supportedLanguages, cc.callContext)
          } yield {
            (AtmSupportedLanguagesJson(atm.atmId.value, atm.supportedLanguages.getOrElse(Nil)), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAtmAccessibilityFeatures,
      implementedInApiVersion,
      nameOf(updateAtmAccessibilityFeatures),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/accessibility-features",
      "Update ATM Accessibility Features",
      s"""Update ATM Accessibility Features.
         |""",
      accessibilityFeaturesJson,
      atmAccessibilityFeaturesJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    
    lazy val updateAtmAccessibilityFeatures : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "accessibility-features" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            accessibilityFeatures <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AccessibilityFeaturesJson]}", 400, cc.callContext) {
              json.extract[AccessibilityFeaturesJson].accessibility_features
            }
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atm, callContext) <- NewStyle.function.updateAtmAccessibilityFeatures(bankId, atmId, accessibilityFeatures, cc.callContext)
          } yield {
            (AtmAccessibilityFeaturesJson(atm.atmId.value, atm.accessibilityFeatures.getOrElse(Nil)), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAtmServices,
      implementedInApiVersion,
      nameOf(updateAtmServices),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/services",
      "Update ATM Services",
      s"""Update ATM Services.
         |""",
      atmServicesJson,
      atmServicesResponseJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    
    lazy val updateAtmServices : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "services" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            services <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmServicesJsonV400]}", 400, cc.callContext) {
              json.extract[AtmServicesJsonV400].services
            }
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atm, callContext) <- NewStyle.function.updateAtmServices(bankId, atmId, services, cc.callContext)
          } yield {
            (AtmServicesResponseJsonV400(atm.atmId.value, atm.services.getOrElse(Nil)), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAtmNotes,
      implementedInApiVersion,
      nameOf(updateAtmNotes),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/notes",
      "Update ATM Notes",
      s"""Update ATM Notes.
         |""",
      atmNotesJson,
      atmNotesResponseJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    
    lazy val updateAtmNotes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "notes" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            notes <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmNotesJsonV400]}", 400, cc.callContext) {
              json.extract[AtmNotesJsonV400].notes
            }
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atm, callContext) <- NewStyle.function.updateAtmNotes(bankId, atmId, notes, cc.callContext)
          } yield {
            (AtmServicesResponseJsonV400(atm.atmId.value, atm.notes.getOrElse(Nil)), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAtmLocationCategories,
      implementedInApiVersion,
      nameOf(updateAtmLocationCategories),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/location-categories",
      "Update ATM Location Categories",
      s"""Update ATM Location Categories.
         |""",
      atmLocationCategoriesJsonV400,
      atmLocationCategoriesResponseJsonV400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    
    lazy val updateAtmLocationCategories : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "location-categories" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            locationCategories <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmLocationCategoriesJsonV400]}", 400, cc.callContext) {
              json.extract[AtmLocationCategoriesJsonV400].location_categories
            }
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atm, callContext) <- NewStyle.function.updateAtmLocationCategories(bankId, atmId, locationCategories, cc.callContext)
          } yield {
            (AtmLocationCategoriesResponseJsonV400(atm.atmId.value, atm.locationCategories.getOrElse(Nil)), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createAtm,
      implementedInApiVersion,
      nameOf(createAtm),
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM.""",
      atmJsonV400,
      atmJsonV400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle),
      Some(List(canCreateAtm,canCreateAtmAtAnyBank))
    )
    lazy val createAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            atmJsonV400 <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV400]}", 400, cc.callContext) {
              val atm = json.extract[AtmJsonV400]
              //Make sure the Create contains proper ATM ID
              atm.id.get
              atm
            }
            _ <-  Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, cc.callContext){atmJsonV400.bank_id == bankId.value}
            atm <- NewStyle.function.tryons(ErrorMessages.CouldNotTransformJsonToInternalModel + " Atm", 400, cc.callContext) {
              JSONFactory400.transformToAtmFromV400(atmJsonV400)
            }
            (atm, callContext) <- NewStyle.function.createOrUpdateAtm(atm, cc.callContext)
          } yield {
            (JSONFactory400.createAtmJsonV400(atm), HttpCode.`201`(callContext))
          }
      }
    }    
    
    staticResourceDocs += ResourceDoc(
      updateAtm,
      implementedInApiVersion,
      nameOf(updateAtm),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID",
      "UPDATE ATM",
      s"""Update ATM.""",
      atmJsonV400.copy(id= None),
      atmJsonV400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle),
      Some(List(canUpdateAtm, canUpdateAtmAtAnyBank))
    )
    lazy val updateAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            atmJsonV400 <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV400]}", 400, cc.callContext) {
              json.extract[AtmJsonV400]
            }
            _ <-  Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, cc.callContext){atmJsonV400.bank_id == bankId.value}
            atm <- NewStyle.function.tryons(ErrorMessages.CouldNotTransformJsonToInternalModel + " Atm", 400, cc.callContext) {
              JSONFactory400.transformToAtmFromV400(atmJsonV400.copy(id = Some(atmId.value)))
            }
            (atm, callContext) <- NewStyle.function.createOrUpdateAtm(atm, cc.callContext)
          } yield {
            (JSONFactory400.createAtmJsonV400(atm), HttpCode.`201`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getAtms,
      implementedInApiVersion,
      nameOf(getAtms),
      "GET",
      "/banks/BANK_ID/atms",
      "Get Bank ATMS",
      s"""Get Bank ATMS.""",
      EmptyBody,
      atmsJsonV400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    lazy val getAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet _ => {
        cc =>
          val limit = S.param("limit")
          val offset = S.param("offset")
          for {
            (_, callContext) <- getAtmsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidNumber } limit:${limit.getOrElse("")}", cc=callContext) {
              limit match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c) == true)
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = maximumLimitExceeded, cc=callContext) {
              limit match {
                case Full(i) if i.toInt > 10000 => false
                case _ => true
              }
            }
            (atms, callContext) <- Connector.connector.vend.getAtms(bankId, callContext) map {
              case Empty =>
                fullBoxOrException(Empty ?~! atmsNotFound)
              case Full((List(), callContext)) =>
                Full(List())
              case Full((list, _)) =>
                val branchesWithLicense = for { branch <- list if branch.meta.license.name.size > 3 } yield branch
                if (branchesWithLicense.size == 0) fullBoxOrException(Empty ?~! atmsNotFoundLicense)
                else Full(branchesWithLicense)
              case Failure(msg, _, _) => fullBoxOrException(Empty ?~! msg)
              case ParamFailure(msg,_,_,_) => fullBoxOrException(Empty ?~! msg)
            } map { unboxFull(_) } map {
              branch =>
                // Before we slice we need to sort in order to keep consistent results
                (branch.sortWith(_.atmId.value < _.atmId.value)
                  // Slice the result in next way: from=offset and until=offset + limit
                  .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
                  ,callContext)
            }
          } yield {
            (JSONFactory400.createAtmsJsonV400(atms), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAtm,
      implementedInApiVersion,
      nameOf(getAtm),
      "GET",
      "/banks/BANK_ID/atms/ATM_ID",
      "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID including:
         |
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |${authenticationRequiredMessage(!getAtmsIsPublic)}
         |""".stripMargin,
      EmptyBody,
      atmJsonV400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    lazy val getAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet req => {
        cc =>
          for {
            (_, callContext) <- getAtmsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, callContext)
          } yield {
            (JSONFactory400.createAtmJsonV400(atm), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createSystemLevelEndpointTag,
      implementedInApiVersion,
      nameOf(createSystemLevelEndpointTag),
      "POST",
      "/management/endpoints/OPERATION_ID/tags",
      "Create System Level Endpoint Tag",
      s"""Create System Level Endpoint Tag""",
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canCreateSystemLevelEndpointTag)))
    lazy val createSystemLevelEndpointTag: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            endpointTag <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $EndpointTagJson400", 400, cc.callContext) {
              json.extract[EndpointTagJson400]
            }
            (endpointTagExisted, callContext) <- NewStyle.function.checkSystemLevelEndpointTagExists(operationId, endpointTag.tag_name, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$EndpointTagAlreadyExists OPERATION_ID ($operationId) and tag_name(${endpointTag.tag_name})", cc=callContext) {
              (!endpointTagExisted)
            }
            (endpointTag, callContext) <- NewStyle.function.createSystemLevelEndpointTag(operationId,endpointTag.tag_name, cc.callContext)
          } yield {
            (SystemLevelEndpointTagResponseJson400(
              endpointTag.endpointTagId.getOrElse(""),
              endpointTag.operationId,
              endpointTag.tagName
            ), HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateSystemLevelEndpointTag,
      implementedInApiVersion,
      nameOf(updateSystemLevelEndpointTag),
      "PUT",
      "/management/endpoints/OPERATION_ID/tags/ENDPOINT_TAG_ID",
      "Update System Level Endpoint Tag",
      s"""Update System Level Endpoint Tag, you can only update the tag_name here, operation_id can not be updated.""",
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        EndpointTagNotFoundByEndpointTagId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateSystemLevelEndpointTag)))
    lazy val updateSystemLevelEndpointTag: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            endpointTag <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $EndpointTagJson400", 400, cc.callContext) {
              json.extract[EndpointTagJson400]
            }
            (_, callContext) <- NewStyle.function.getEndpointTag(endpointTagId, cc.callContext)
            (endpointTagExisted, callContext) <- NewStyle.function.checkSystemLevelEndpointTagExists(operationId, endpointTag.tag_name, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$EndpointTagAlreadyExists OPERATION_ID ($operationId) and tag_name(${endpointTag.tag_name}), please choose another tag_name", cc=callContext) {
              (!endpointTagExisted)
            }
            (endpointTagT, callContext) <- NewStyle.function.updateSystemLevelEndpointTag(endpointTagId, operationId,endpointTag.tag_name, cc.callContext)
          } yield {
            (SystemLevelEndpointTagResponseJson400(
              endpointTagT.endpointTagId.getOrElse(""),
              endpointTagT.operationId,
              endpointTagT.tagName
            ), HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getSystemLevelEndpointTags,
      implementedInApiVersion,
      nameOf(getSystemLevelEndpointTags),
      "GET",
      "/management/endpoints/OPERATION_ID/tags",
      "Get System Level Endpoint Tags",
      s"""Get System Level Endpoint Tags.""",
      EmptyBody,
      bankLevelEndpointTagResponseJson400 :: Nil,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canGetSystemLevelEndpointTag)))
    lazy val getSystemLevelEndpointTags: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" ::  Nil JsonGet _ => {
        cc =>
          for {
            (endpointTags, callContext) <- NewStyle.function.getSystemLevelEndpointTags(operationId, cc.callContext)
          } yield {
            (endpointTags.map(endpointTagT => SystemLevelEndpointTagResponseJson400(
              endpointTagT.endpointTagId.getOrElse(""),
              endpointTagT.operationId,
              endpointTagT.tagName
            )), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteSystemLevelEndpointTag,
      implementedInApiVersion,
      nameOf(deleteSystemLevelEndpointTag),
      "DELETE",
      "/management/endpoints/OPERATION_ID/tags/ENDPOINT_TAG_ID",
      "Delete System Level Endpoint Tag",
      s"""Delete System Level Endpoint Tag.""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteSystemLevelEndpointTag)))
    lazy val deleteSystemLevelEndpointTag: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getEndpointTag(endpointTagId, cc.callContext)
            
            (deleted, callContext) <- NewStyle.function.deleteEndpointTag(endpointTagId, cc.callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      createBankLevelEndpointTag,
      implementedInApiVersion,
      nameOf(createBankLevelEndpointTag),
      "POST",
      "/management/banks/BANK_ID/endpoints/OPERATION_ID/tags",
      "Create Bank Level Endpoint Tag",
      s"""Create Bank Level Endpoint Tag""",
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canCreateBankLevelEndpointTag)))
    lazy val createBankLevelEndpointTag: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoints" :: operationId :: "tags" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            endpointTag <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $EndpointTagJson400", 400, cc.callContext) {
              json.extract[EndpointTagJson400]
            }
            (endpointTagExisted, callContext) <- NewStyle.function.checkBankLevelEndpointTagExists(bankId, operationId, endpointTag.tag_name, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$EndpointTagAlreadyExists OPERATION_ID ($operationId) and tag_name(${endpointTag.tag_name})", cc=callContext) {
              (!endpointTagExisted)
            }
            (endpointTagT, callContext) <- NewStyle.function.createBankLevelEndpointTag(bankId, operationId, endpointTag.tag_name, cc.callContext)
          } yield {
            (BankLevelEndpointTagResponseJson400(
              endpointTagT.bankId.getOrElse(""),
              endpointTagT.endpointTagId.getOrElse(""),
              endpointTagT.operationId,
              endpointTagT.tagName
            ), HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateBankLevelEndpointTag,
      implementedInApiVersion,
      nameOf(updateBankLevelEndpointTag),
      "PUT",
      "/management/banks/BANK_ID/endpoints/OPERATION_ID/tags/ENDPOINT_TAG_ID",
      "Update Bank Level Endpoint Tag",
      s"""Update Endpoint Tag, you can only update the tag_name here, operation_id can not be updated.""",
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        EndpointTagNotFoundByEndpointTagId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateBankLevelEndpointTag)))
    lazy val updateBankLevelEndpointTag: OBPEndpoint = {
      case "management":: "banks" :: bankId :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            endpointTag <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $EndpointTagJson400", 400, cc.callContext) {
              json.extract[EndpointTagJson400]
            }
            (_, callContext) <- NewStyle.function.getEndpointTag(endpointTagId, cc.callContext)
            (endpointTagExisted, callContext) <- NewStyle.function.checkBankLevelEndpointTagExists(bankId, operationId, endpointTag.tag_name, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$EndpointTagAlreadyExists BANK_ID($bankId), OPERATION_ID ($operationId) and tag_name(${endpointTag.tag_name}), please choose another tag_name", cc=callContext) {
              (!endpointTagExisted)
            }
            (endpointTagT, callContext) <- NewStyle.function.updateBankLevelEndpointTag(bankId, endpointTagId, operationId, endpointTag.tag_name, cc.callContext)
          } yield {
            (BankLevelEndpointTagResponseJson400(
              endpointTagT.bankId.getOrElse(""),
              endpointTagT.endpointTagId.getOrElse(""),
              endpointTagT.operationId,
              endpointTagT.tagName
            ), HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelEndpointTags,
      implementedInApiVersion,
      nameOf(getBankLevelEndpointTags),
      "GET",
      "/management/banks/BANK_ID/endpoints/OPERATION_ID/tags",
      "Get Bank Level Endpoint Tags",
      s"""Get Bank Level Endpoint Tags.""",
      EmptyBody,
      bankLevelEndpointTagResponseJson400 :: Nil,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canGetBankLevelEndpointTag)))
    lazy val getBankLevelEndpointTags: OBPEndpoint = {
      case "management":: "banks" :: bankId :: "endpoints" :: operationId :: "tags" ::  Nil JsonGet _ => {
        cc =>
          for {
            (endpointTags, callContext) <- NewStyle.function.getBankLevelEndpointTags(bankId, operationId, cc.callContext)
          } yield {
            (endpointTags.map(endpointTagT => BankLevelEndpointTagResponseJson400(
              endpointTagT.bankId.getOrElse(""),
              endpointTagT.endpointTagId.getOrElse(""),
              endpointTagT.operationId,
              endpointTagT.tagName
            )), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteBankLevelEndpointTag,
      implementedInApiVersion,
      nameOf(deleteBankLevelEndpointTag),
      "DELETE",
      "/management/banks/BANK_ID/endpoints/OPERATION_ID/tags/ENDPOINT_TAG_ID",
      "Delete Bank Level Endpoint Tag",
      s"""Delete Bank Level Endpoint Tag.""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApi, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteBankLevelEndpointTag)))
    lazy val deleteBankLevelEndpointTag: OBPEndpoint = {
      case "management":: "banks" :: bankId :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonDelete _ => {
        cc =>
          for {
            (_, callContext) <- NewStyle.function.getEndpointTag(endpointTagId, cc.callContext)

            (deleted, callContext) <- NewStyle.function.deleteEndpointTag(endpointTagId, cc.callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMySpaces,
      implementedInApiVersion,
      nameOf(getMySpaces),
      "GET",
      "/my/spaces",
      "Get My Spaces",
      s"""Get My Spaces.""",
      EmptyBody,
      mySpaces,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle)
    )
    lazy val getMySpaces: OBPEndpoint = {
      case "my" :: "spaces" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
          } yield {
            (
              MySpaces(entitlements
                .filter(_.roleName == canReadDynamicResourceDocsAtOneBank.toString())
                .map(entitlement => entitlement.bankId)), 
              HttpCode.`200`(callContext)
            )
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getProducts,
      implementedInApiVersion,
      "getProducts",
      "GET",
      "/banks/BANK_ID/products",
      "Get Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID including:
         |
         |* Name
         |* Code
         |* Parent Product Code
         |* More info URL
         |* Terms And Conditions URL
         |* Description
         |* Terms and Conditions
         |* License the data under this endpoint is released under
         |
         |Can filter with attributes name and values.
         |URL params example: /banks/some-bank-id/products?manager=John&count=8
         |
         |${authenticationRequiredMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV400.copy(attributes = None, fees = None),
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )
    lazy val getProducts : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet req => {
        cc => {
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            params = req.params.toList.map(kv => GetProductsParam(kv._1, kv._2))
            products <- Future(Connector.connector.vend.getProducts(bankId, params)) map {
              unboxFullOrFail(_, callContext, ProductNotFoundByProductCode)
            }
          } yield {
            (JSONFactory400.createProductsJson(products), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      createProduct,
      implementedInApiVersion,
      nameOf(createProduct),
      "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Create Product",
      s"""Create or Update Product for the Bank.
         |
         |
         |Typical Super Family values / Asset classes are:
         |
         |Debt
         |Equity
         |FX
         |Commodity
         |Derivative
         |
         |$productHiearchyAndCollectionNote
         |
         |
         |${authenticationRequiredMessage(true) }
         |
         |
         |""",
      putProductJsonV400,
      productJsonV400.copy(attributes = None, fees = None),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )
    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutProductJsonV400 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutProductJsonV400]
            }
            parentProductCode <- product.parent_product_code.trim.nonEmpty match {
              case false =>
                Future(Empty)
              case true =>
                Future(Connector.connector.vend.getProduct(bankId, ProductCode(product.parent_product_code))) map {
                  getFullBoxOrFail(_, callContext, ParentProductNotFoundByProductCode + " {" + product.parent_product_code + "}", 400)
                }
            }
            success <- Future(Connector.connector.vend.createOrUpdateProduct(
              bankId = bankId.value,
              code = productCode.value,
              parentProductCode = parentProductCode.map(_.code.value).toOption,
              name = product.name,
              category = null,
              family = null,
              superFamily = null,
              moreInfoUrl = product.more_info_url,
              termsAndConditionsUrl = product.terms_and_conditions_url,
              details = null,
              description = product.description,
              metaLicenceId = product.meta.license.id,
              metaLicenceName = product.meta.license.name
            )) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory400.createProductJson(success), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getProduct,
      implementedInApiVersion,
      nameOf(getProduct),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about a financial Product offered by the bank specified by BANK_ID and PRODUCT_CODE including:
         |
         |* Name
         |* Code
         |* Parent Product Code
         |* More info URL
         |* Description
         |* Terms and Conditions
         |* Description
         |* Meta
         |* Attributes
         |* Fees
         |
         |${authenticationRequiredMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV400,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )

    lazy val getProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            product <- Future(Connector.connector.vend.getProduct(bankId, productCode)) map {
              unboxFullOrFail(_, callContext, ProductNotFoundByProductCode)
            }
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, productCode, callContext)
            
            (productFees, callContext) <- NewStyle.function.getProductFeesFromProvider(bankId, productCode, callContext)
            
          } yield {
            (JSONFactory400.createProductJson(product, productAttributes, productFees), HttpCode.`200`(callContext))
          }
        }
      }
    }

  }

  private def createDynamicEndpointMethod(bankId: Option[String], json: JValue, cc: CallContext) = {
    for {
      (postedJson, openAPI) <- NewStyle.function.tryons(InvalidJsonFormat, 400, cc.callContext) {
        //If it is bank level, we manully added /banks/bankId in all the paths:
        val jsonTweakedPath = DynamicEndpointHelper.addedBankToPath(json, bankId) 
        val swaggerContent = compactRender(jsonTweakedPath)

        (DynamicEndpointSwagger(swaggerContent), DynamicEndpointHelper.parseSwaggerContent(swaggerContent))
      }
      duplicatedUrl = DynamicEndpointHelper.findExistingDynamicEndpoints(openAPI).map(kv => s"${kv._1}:${kv._2}")
      errorMsg = s"""$DynamicEndpointExists Duplicated ${if (duplicatedUrl.size > 1) "endpoints" else "endpoint"}: ${duplicatedUrl.mkString("; ")}"""
      _ <- Helper.booleanToFuture(errorMsg, cc = cc.callContext) {
        duplicatedUrl.isEmpty
      }
      dynamicEndpointInfo <- NewStyle.function.tryons(InvalidJsonFormat+"Can not convert to OBP Internal Resource Docs", 400, cc.callContext) {
        DynamicEndpointHelper.buildDynamicEndpointInfo(openAPI, "current_request_json_body", bankId)
      }
      roles <- NewStyle.function.tryons(InvalidJsonFormat+"Can not generate OBP roles", 400, cc.callContext) {
        DynamicEndpointHelper.getRoles(dynamicEndpointInfo)
      }
      _ <- NewStyle.function.tryons(InvalidJsonFormat+"Can not generate OBP external Resource Docs", 400, cc.callContext) {
        JSONFactory1_4_0.createResourceDocsJson(dynamicEndpointInfo.resourceDocs.toList, false)
      }
      (dynamicEndpoint, callContext) <- NewStyle.function.createDynamicEndpoint(bankId, cc.userId, postedJson.swaggerString, cc.callContext)
      _ <- NewStyle.function.tryons(InvalidJsonFormat+s"Can not grant these roles ${roles.toString} ", 400, cc.callContext) {
        roles.map(role => Entitlement.entitlement.vend.addEntitlement(bankId.getOrElse(""), cc.userId, role.toString()))
      }
    } yield {
      val swaggerJson = parse(dynamicEndpoint.swaggerString)
      val responseJson: JObject = ("bank_id", dynamicEndpoint.bankId) ~ ("user_id", cc.userId) ~ ("dynamic_endpoint_id", dynamicEndpoint.dynamicEndpointId) ~ ("swagger_string", swaggerJson)
      (responseJson, HttpCode.`201`(callContext))
    }
  }
}

object APIMethods400 extends RestHelper with APIMethods400 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations4_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

