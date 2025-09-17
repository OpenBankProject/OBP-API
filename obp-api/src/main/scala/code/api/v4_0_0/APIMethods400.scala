package code.api.v4_0_0

import code.DynamicData.DynamicData
import code.DynamicEndpoint.DynamicEndpointSwagger
import code.accountattribute.AccountAttributeX
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{jsonDynamicResourceDoc, _}
import code.api.dynamic.endpoint.helper.practise.{DynamicEndpointCodeGenerator, PractiseEndpoint}
import code.api.dynamic.endpoint.helper.{CompiledObjects, DynamicEndpointHelper}
import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.APIUtil.{fullBoxOrException, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.CommonsEmailWrapper._
import code.api.util.DynamicUtil.Validation
import code.api.util.ErrorMessages.{BankNotFound, _}
import code.api.util.ExampleValue._
import code.api.util.FutureUtil.EndpointContext
import code.api.util.Glossary.getGlossaryItem
import code.api.util.NewStyle.HttpCode
import code.api.util.NewStyle.function._
import code.api.util._
import code.api.util.migration.Migration
import code.api.util.newstyle.AttributeDefinition._
import code.api.util.newstyle.Consumer._
import code.api.util.newstyle.UserCustomerLinkNewStyle.getUserCustomerLinks
import code.api.util.newstyle.{BalanceNewStyle, UserCustomerLinkNewStyle, ViewNewStyle}
import code.api.v1_2_1.{JSONFactory, PostTransactionTagJSON}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_0_0.{CreateEntitlementJSON, CreateUserCustomerLinkJson, EntitlementJSONs, JSONFactory200}
import code.api.v2_1_0._
import code.api.v3_0_0.{CreateScopeJson, JSONFactory300}
import code.api.v3_1_0._
import code.api.v4_0_0.JSONFactory400._
import code.api.{Constant, JsonResponseException}
import code.apicollection.MappedApiCollectionsProvider
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.authtypevalidation.JsonAuthTypeValidation
import code.bankconnectors.LocalMappedConnectorInternal._
import code.bankconnectors.{Connector, DynamicConnector, InternalConnector, LocalMappedConnectorInternal}
import code.connectormethod.{JsonConnectorMethod, JsonConnectorMethodMethodBody}
import code.consent.{ConsentStatus, Consents}
import code.dynamicEntity.{DynamicEntityCommons, ReferenceType}
import code.dynamicMessageDoc.JsonDynamicMessageDoc
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import code.endpointMapping.EndpointMappingCommons
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.metadata.counterparties.{Counterparties, MappedCounterparty}
import code.metadata.tags.Tags
import code.model._
import code.model.dataAccess.{AuthUser, BankAccountCreation}
import code.ratelimiting.RateLimitingDI
import code.scope.Scope
import code.snippet.{WebUIPlaceholder, WebUITemplate}
import code.usercustomerlinks.UserCustomerLink
import code.userlocks.UserLocksProvider
import code.users.Users
import code.util.Helper.{MdcLoggable, ObpS, SILENCE_IS_GOLDEN, booleanToFuture}
import code.util.{Helper, JsonSchemaUtil}
import code.validation.JsonValidation
import code.views.Views
import code.webhook.{BankAccountNotificationWebhookTrait, SystemAccountNotificationWebhookTrait}
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.github.dwickern.macros.NameOf.nameOf
import com.networknt.schema.ValidationMessage
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{TransactionRequestStatus, _}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import deletion._
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.Helpers.{now, tryo}
import net.liftweb.util.{Helpers, StringHelpers}
import org.apache.commons.lang3.StringUtils

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util
import java.util.{Calendar, Date}
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

trait APIMethods400 extends MdcLoggable {
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
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      adapterInfoJsonV300,
      List($UserNotLoggedIn, UnknownError),
      List(apiTagApi),
      Some(List(canGetDatabaseInfo)))


    lazy val getMapperDatabaseInfo: OBPEndpoint = {
      case "database" :: "info" :: Nil JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          Future {
            (Migration.DbFunction.mapperDatabaseInfo, HttpCode.`200`(cc.callContext))
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
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      logoutLinkV400,
      List($UserNotLoggedIn, UnknownError),
      List(apiTagUser))

    lazy val getLogoutLink: OBPEndpoint = {
      case "users" :: "current" :: "logout-link" :: Nil JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          Future {
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
      "Set Rate Limits / Call Limits per Consumer",
      s"""
         |Set the API rate limits / call limits for a Consumer:
         |
         |Rate limiting can be set:
         |
         |Per Second
         |Per Minute
         |Per Hour
         |Per Week
         |Per Month
         |
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagConsumer, apiTagRateLimits),
      Some(List(canSetCallLimits)))

    lazy val callsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.handleEntitlementsAndScopes("", u.userId, List(canSetCallLimits), callContext)
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
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2  :: Nil
    )

    lazy val getBanks: OBPEndpoint = {
      case "banks" :: Nil JsonGet _ => {
        cc => 
          implicit val ec = EndpointContext(Some(cc))
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
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2  :: Nil
    )

    lazy val getBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      "Validate and check IBAN",
      """Validate and check IBAN for errors
        |
        |""",
      ibanCheckerPostJsonV400,
      ibanCheckerJsonV400,
      List(UnknownError),
      apiTagAccount  :: Nil
    )

    lazy val ibanChecker: OBPEndpoint = {
      case "account" :: "check" :: "scheme" :: "iban" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      doubleEntryTransactionJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction),
      Some(List(canGetDoubleEntryTransactionAtAnyBank, canGetDoubleEntryTransactionAtOneBank))
    )

    lazy val getDoubleEntryTransaction : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "double-entry-transaction" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            (_, callContext) <- NewStyle.function.getTransaction(bankId, accountId, transactionId, cc.callContext)
            (doubleEntryTransaction, callContext) <- NewStyle.function.getDoubleEntryBookTransaction(bankId, accountId, transactionId, callContext)
          } yield {
            (JSONFactory400.createDoubleEntryTransactionJson(doubleEntryTransaction), HttpCode.`200`(callContext))
          }
      }
    }
    staticResourceDocs += ResourceDoc(
      getBalancingTransaction,
      implementedInApiVersion,
      nameOf(getBalancingTransaction),
      "GET",
      "/transactions/TRANSACTION_ID/balancing-transaction",
      "Get Balancing Transaction",
      s"""Get Balancing Transaction
         |
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      doubleEntryTransactionJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagTransaction),
      Some(List())
    )

    lazy val getBalancingTransaction : OBPEndpoint = {
      case "transactions" :: TransactionId(transactionId) :: "balancing-transaction" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (doubleEntryTransaction, callContext) <- NewStyle.function.getBalancingTransaction(transactionId, cc.callContext)
            _ <- ViewNewStyle.checkBalancingTransactionAccountAccessAndReturnView(doubleEntryTransaction, cc.user, cc.callContext)
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
      List(apiTagBank),
      Some(List(canCreateSettlementAccountAtOneBank))
    )

    lazy val createSettlementAccount: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "settlement-accounts" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode, cc=callContext){APIUtil.isValidCurrencyISOCode(currency)}

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
              None,
              callContext: Option[CallContext]
            )
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bankId, accountId, postedOrLoggedInUser, callContext)
          } yield {
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
      List(apiTagBank, apiTagPsd2),
      Some(List(canGetSettlementAccountAtOneBank))
    )

    lazy val getSettlementAccounts: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "settlement-accounts" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

    // COUNTERPARTY
    staticResourceDocs += ResourceDoc(
      createTransactionRequestCounterparty,
      implementedInApiVersion,
      "createTransactionRequestCounterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests",
      "Create Transaction Request (COUNTERPARTY)",
      s"""
         |$transactionRequestGeneralText
         |
         |When using a COUNTERPARTY to create a Transaction Request, specify the counterparty_id in the body of the request.
         |The routing details of the counterparty will be forwarded to the Core Banking System (CBS) for the transfer.
         |
         |COUNTERPARTY Transaction Requests are used for Variable Recurring Payments (VRP). Use the following ${Glossary.getApiExplorerLink("endpoint", "OBPv5.1.0-createVRPConsentRequest")} to create a consent for VRPs.
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

    // SIMPLE
    staticResourceDocs += ResourceDoc(
      createTransactionRequestSimple,
      implementedInApiVersion,
      nameOf(createTransactionRequestSimple),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SIMPLE/transaction-requests",
      "Create Transaction Request (SIMPLE)",
      s"""
         |Special instructions for SIMPLE:
         |
         |You can transfer money to the Bank Account Number or IBAN directly.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodySimpleJsonV400,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

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
      List(apiTagTransactionRequest, apiTagPSD2PIS),
      Some(List(canCreateAnyTransactionRequest)))


    staticResourceDocs += ResourceDoc(
      createTransactionRequestAgentCashWithDrawal,
      implementedInApiVersion,
      nameOf(createTransactionRequestAgentCashWithDrawal),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/AGENT_CASH_WITHDRAWAL/transaction-requests",
      "Create Transaction Request (AGENT_CASH_WITHDRAWAL)",
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
      transactionRequestBodyAgentJsonV400,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestAgentCashWithDrawal: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "AGENT_CASH_WITHDRAWAL" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("AGENT_CASH_WITHDRAWAL")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId, transactionRequestType, json)
    }
    
    lazy val createTransactionRequestAccount: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "ACCOUNT" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("ACCOUNT")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    lazy val createTransactionRequestAccountOtp: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "ACCOUNT_OTP" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("ACCOUNT_OTP")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }
    
    lazy val createTransactionRequestSepa: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "SEPA" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("SEPA")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }
    
    lazy val createTransactionRequestCounterparty: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "COUNTERPARTY" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("COUNTERPARTY")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    lazy val createTransactionRequestRefund: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "REFUND" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("REFUND")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    lazy val createTransactionRequestFreeForm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "FREE_FORM" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("FREE_FORM")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    lazy val createTransactionRequestSimple: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "SIMPLE" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("SIMPLE")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }


    staticResourceDocs += ResourceDoc(
      createTransactionRequestCard,
      implementedInApiVersion,
      nameOf(createTransactionRequestCard),
      "POST",
      "/transaction-request-types/CARD/transaction-requests",
      "Create Transaction Request (CARD)",
      s"""
         |
         |When using CARD, the payee is set in the request body .
         |
         |Money goes into the Counterparty in the request body.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyCardJsonV400,
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )
    
    lazy val createTransactionRequestCard: OBPEndpoint = {
      case "transaction-request-types" :: "CARD" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("CARD")
          LocalMappedConnectorInternal.createTransactionRequest(BankId(""), AccountId(""), ViewId(Constant.SYSTEM_OWNER_VIEW_ID), transactionRequestType, json)
    }



    staticResourceDocs += ResourceDoc(
      answerTransactionRequestChallenge,
      implementedInApiVersion,
      "answerTransactionRequestChallenge",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge",
      s"""In Sandbox mode, any string that can be converted to a positive integer will be accepted as an answer.
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
        |    In CBS mode, the answer can be got by phone message or other SCA methods.
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
        |(one for every user that has a View where permission $CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT=true)
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
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

    lazy val answerTransactionRequestChallenge: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, fromAccount, callContext) <- SS.userBankAccount
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

            (challenges, callContext) <-  NewStyle.function.getChallengesByTransactionRequestId(transReqId.value, callContext)
            
            //Check the challenge type, Note: not supported yet, the default value is SANDBOX_TAN
            _ <- Helper.booleanToFuture(s"$InvalidChallengeType Current Type is ${challenges.map(_.challengeType)}" , cc=callContext) {
              challenges.map(_.challengeType)
                .filterNot(challengeType => challengeType.equals(ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString))
                .isEmpty
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
                        (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                      } yield (fromAccount, toAccount, callContext)
                    } else {
                      // Else, the transaction request debit a counterparty (Iban)
                      val fromCounterpartyIban = transactionRequest.from.account_id
                      // and the creditor is the obp account owner
                      val toAccount = fromAccount
                      for {
                        (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(fromCounterpartyIban, toAccount.bankId, toAccount.accountId, callContext)
                        (fromAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, false, callContext)
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
                  _ <- NewStyle.function.saveTransactionRequestStatusImpl(transactionRequest.id, transactionRequest.status, callContext)
                } yield (transactionRequest, callContext)
              case _ =>
                for {
  
                  (challengeAnswerIsValidated, callContext) <- NewStyle.function.validateChallengeAnswer(challengeAnswerJson.id, challengeAnswerJson.answer, SuppliedAnswerType.PLAIN_TEXT_VALUE,callContext)

                  _ <- Helper.booleanToFuture(s"${InvalidChallengeAnswer
                    .replace("answer may be expired.",s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                    .replace("up your allowed attempts.",s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
                  }", cc=callContext) {
                    challengeAnswerIsValidated
                  }

                  (challengeAnswerIsValidated, callContext) <- NewStyle.function.allChallengesSuccessfullyAnswered(bankId, accountId, transReqId, callContext)
                  _ <- Helper.booleanToFuture(s"$NextChallengePending", cc=callContext) {
                    challengeAnswerIsValidated
                  }
                  (transactionRequest, callContext) <- TransactionRequestTypes.withName(transactionRequestType.value) match {
                    case TRANSFER_TO_PHONE | TRANSFER_TO_ATM | TRANSFER_TO_ACCOUNT =>
                      NewStyle.function.createTransactionAfterChallengeV300(u, fromAccount, transReqId, transactionRequestType, callContext)
                    case _ =>
                      NewStyle.function.createTransactionAfterChallengeV210(fromAccount, existingTransactionRequest, callContext)
                  }
                } yield (transactionRequest, callContext)
            }

            (transactionRequestAttribute, callContext) <- NewStyle.function.getTransactionRequestAttributes(bankId, transactionRequest.id, callContext)
          } yield {

            (JSONFactory400.createTransactionRequestWithChargeJSON(transactionRequest, challenges, transactionRequestAttribute), HttpCode.`202`(callContext))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionRequest),
      Some(List(canCreateTransactionRequestAttributeAtOneBank))
    )

    lazy val createTransactionRequestAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attribute" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $transactionRequestAttributeJsonV400 "
          for {
            (_, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              json.extract[TransactionRequestAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionRequestAttributeType.DOUBLE}(12.1234), ${TransactionRequestAttributeType.STRING}(TAX_NUMBER), ${TransactionRequestAttributeType.INTEGER}(123) and ${TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)"
            transactionRequestAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              TransactionRequestAttributeType.withName(postedData.attribute_type)
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionRequest),
      Some(List(canGetTransactionRequestAttributeAtOneBank))
    )

    lazy val getTransactionRequestAttributeById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) ::  "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attributes" :: transactionRequestAttributeId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionRequest),
      Some(List(canGetTransactionRequestAttributesAtOneBank))
    )

    lazy val getTransactionRequestAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionRequest),
      Some(List(canUpdateTransactionRequestAttributeAtOneBank))
    )

    lazy val updateTransactionRequestAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transaction-requests" :: TransactionRequestId(transactionRequestId) :: "attributes" :: transactionRequestAttributeId :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionRequestAttributeJsonV400"
          for {
            (_, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              json.extract[TransactionRequestAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionRequestAttributeType.DOUBLE}(12.1234), ${TransactionRequestAttributeType.STRING}(TAX_NUMBER), ${TransactionRequestAttributeType.INTEGER}(123) and ${TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)"
            transactionRequestAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              TransactionRequestAttributeType.withName(postedData.attribute_type)
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionRequest),
      Some(List(canCreateTransactionRequestAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateTransactionRequestAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction-request" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      transactionRequestAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransactionRequest),
      Some(List(canGetTransactionRequestAttributeDefinitionAtOneBank)))

    lazy val getTransactionRequestAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction-request" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransactionRequest),
      Some(List(canDeleteTransactionRequestAttributeDefinitionAtOneBank)))

    lazy val deleteTransactionRequestAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "transaction-request" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      getSystemDynamicEntities,
      implementedInApiVersion,
      nameOf(getSystemDynamicEntities),
      "GET",
      "/management/system-dynamic-entities",
      "Get System Dynamic Entities",
      s"""Get all System Dynamic Entities """,
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
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canGetSystemLevelDynamicEntities))
    )

    lazy val getSystemDynamicEntities: OBPEndpoint = {
      case "management" :: "system-dynamic-entities" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(None, false))
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
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canGetBankLevelDynamicEntities))
    )

    lazy val getBankLevelDynamicEntities: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-entities" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(Some(bankId),false))
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
      createSystemDynamicEntity,
      implementedInApiVersion,
      nameOf(createSystemDynamicEntity),
      "POST",
      "/management/system-dynamic-entities",
      "Create System Level Dynamic Entity",
      s"""Create a system level Dynamic Entity.
         |
         |
         |${userAuthenticationMessage(true)}
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
         |
         |Note: if you set `hasPersonalEntity` = false, then OBP will not generate the CRUD my FooBar endpoints.
         |""",
      dynamicEntityRequestBodyExample.copy(bankId = None),
      dynamicEntityResponseBodyExample.copy(bankId = None),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canCreateSystemLevelDynamicEntity)))

    lazy val createSystemDynamicEntity: OBPEndpoint = {
      case "management" :: "system-dynamic-entities" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val dynamicEntity = DynamicEntityCommons(json.asInstanceOf[JObject], None, cc.userId, None)
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
         |${userAuthenticationMessage(true)}
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
         | Note: if you set `hasPersonalEntity` = false, then OBP will not generate the CRUD my FooBar endpoints.
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
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canCreateBankLevelDynamicEntity)))
    lazy val createBankLevelDynamicEntity: OBPEndpoint = {
      case "management" ::"banks" :: BankId(bankId) :: "dynamic-entities" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val dynamicEntity = DynamicEntityCommons(json.asInstanceOf[JObject], None, cc.userId, Some(bankId.value))
          createDynamicEntityMethod(cc, dynamicEntity)
      }
    }
    
    //bankId is option, if it is bankLevelEntity, we need BankId, if system Level Entity, bankId is None.
    private def updateDynamicEntityMethod(bankId: Option[String], dynamicEntityId: String, json: JValue, cc: CallContext) = {
      for {
        // Check whether there are uploaded data, only if no uploaded data allow to update DynamicEntity.
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankId, dynamicEntityId, cc.callContext)
        (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entity.entityName, None, None, entity.bankId, None, None, false, cc.callContext)
        resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
        _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = cc.callContext) {
          resultList.arr.isEmpty
        }

        jsonObject = json.asInstanceOf[JObject]
        dynamicEntity = DynamicEntityCommons(jsonObject, Some(dynamicEntityId), cc.userId, bankId)
        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, cc.callContext)
      } yield {
        val commonsData: DynamicEntityCommons = result
        (commonsData.jValue, HttpCode.`200`(cc.callContext))
      }
    }


    private def updateDynamicEntityDoc = ResourceDoc(
      updateSystemDynamicEntity,
      implementedInApiVersion,
      nameOf(updateSystemDynamicEntity),
      "PUT",
      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID",
      "Update System Level Dynamic Entity",
      s"""Update a System Level Dynamic Entity.
         |
         |
         |${userAuthenticationMessage(true)}
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
      dynamicEntityResponseBodyExample.copy(bankId= None),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        DynamicEntityNotFoundByDynamicEntityId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canUpdateSystemDynamicEntity)))
    lazy val updateSystemDynamicEntity: OBPEndpoint = {
      case "management" :: "system-dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canUpdateBankLevelDynamicEntity)))
    lazy val updateBankLevelDynamicEntity: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          updateDynamicEntityMethod(Some(bankId),dynamicEntityId, json, cc)
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteSystemDynamicEntity,
      implementedInApiVersion,
      nameOf(deleteSystemDynamicEntity),
      "DELETE",
      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID",
      "Delete System Level Dynamic Entity",
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
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canDeleteSystemLevelDynamicEntity)))
    lazy val deleteSystemDynamicEntity: OBPEndpoint = {
      case "management" :: "system-dynamic-entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          deleteDynamicEntityMethod(None, dynamicEntityId, cc)
      }
    }

    private def deleteDynamicEntityMethod(bankId: Option[String], dynamicEntityId: String, cc: CallContext) = {
      for {
        // Check whether there are uploaded data, only if no uploaded data allow to delete DynamicEntity.
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankId, dynamicEntityId, cc.callContext)
        (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, entity.entityName, None, None, entity.bankId, None, None, false, cc.callContext)
        resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entity.entityName)
        _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc = cc.callContext) {
          resultList.arr.isEmpty
        }
        deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(bankId, dynamicEntityId)
      } yield {
        (deleted, HttpCode.`200`(cc.callContext))
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
      List(apiTagManageDynamicEntity, apiTagApi),
      Some(List(canDeleteBankLevelDynamicEntity)))
    lazy val deleteBankLevelDynamicEntity: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEntity, apiTagApi)
    )

    lazy val getMyDynamicEntities: OBPEndpoint = {
      case "my" :: "dynamic-entities" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
        DynamicEntityNotFoundByDynamicEntityId,
        UnknownError
      ),
      List(apiTagManageDynamicEntity, apiTagApi)
    )

    lazy val updateMyDynamicEntity: OBPEndpoint = {
      case "my" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(cc.userId))
            entityOption =  dynamicEntities.find(_.dynamicEntityId.equals(Some(dynamicEntityId)))
            myEntity <- NewStyle.function.tryons(InvalidMyDynamicEntityUser, 400, cc.callContext) {
                entityOption.get
            }
            // Check whether there are uploaded data, only if no uploaded data allow to update DynamicEntity.
            (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, myEntity.entityName, None, myEntity.dynamicEntityId, myEntity.bankId, None, Some(myEntity.userId), false, cc.callContext)
            resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], myEntity.entityName)
            _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc=cc.callContext) {
              resultList.arr.isEmpty
            }
            jsonObject = json.asInstanceOf[JObject]
            dynamicEntity = DynamicEntityCommons(jsonObject, Some(dynamicEntityId), cc.userId, myEntity.bankId)
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
      List(apiTagManageDynamicEntity, apiTagApi)
    )

    lazy val deleteMyDynamicEntity: OBPEndpoint = {
      case "my" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(cc.userId))
            entityOption =  dynamicEntities.find(_.dynamicEntityId.equals(Some(dynamicEntityId)))
            myEntity <- NewStyle.function.tryons(InvalidMyDynamicEntityUser, 400, cc.callContext) {
              entityOption.get
            }
            // Check whether there are uploaded data, only if no uploaded data allow to delete DynamicEntity.
            (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ALL, myEntity.entityName, None, myEntity.dynamicEntityId, myEntity.bankId, None, Some(myEntity.userId), false, cc.callContext)
            resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], myEntity.entityName)
            _ <- Helper.booleanToFuture(DynamicEntityOperationNotAllowed, cc=cc.callContext) {
              resultList.arr.isEmpty
            }
            deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(myEntity.bankId, dynamicEntityId)
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
      List(apiTagUser),
      Some(List(canCreateResetPasswordUrl)))

    lazy val resetPasswordUrl : OBPEndpoint = {
      case "management" :: "user" :: "reset-password-url" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
        |The User can create an Account for themself  - or -  the User that has the USER_ID specified in the POST body.
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
      List(apiTagAccount),
      Some(List(canCreateAccount))
    ).disableAutoValidateRoles()  // this means disabled auto roles validation, will manually do the roles validation .


    lazy val addAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonPost json -> _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
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
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode, cc=callContext){APIUtil.isValidCurrencyISOCode(createAccountJson.balance.currency)}
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
            (bankAccount,callContext) <- NewStyle.function.createBankAccount(
              bankId,
              AccountId(APIUtil.generateUUID()),
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
              None,
              callContext: Option[CallContext]
            )
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bankId, accountId, postedOrLoggedInUser, callContext)
          } yield {
            (JSONFactory310.createAccountJSON(userIdAccountOwner, bankAccount, accountAttributes), HttpCode.`201`(callContext))
          }
        }
      }
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
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi  :: Nil)

    lazy val root: OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc => 
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
            (JSONFactory400.getApiInfoJSON(OBPAPI4_0_0.version,OBPAPI4_0_0.versionStatus), HttpCode.`200`(cc.callContext))
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
      List(apiTagApi),
      Some(List(canGetCallContext)))

    lazy val getCallContext: OBPEndpoint = {
      case "development" :: "call_context" :: Nil JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
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
      List(apiTagApi),
      Some(Nil))

    lazy val verifyRequestSignResponse: OBPEndpoint = {
      case "development" :: "echo":: "jws-verified-request-jws-signed-response" :: Nil JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
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
         |${userAuthenticationMessage(true)}
         |
       """.stripMargin,
      updateAccountJsonV400,
      successMessage,
      List(InvalidJsonFormat, $UserNotLoggedIn, $BankNotFound, UnknownError, $BankAccountNotFound, "user does not have access to owner view on account"),
      List(apiTagAccount)
    )

    lazy val updateAccountLabel : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), account, callContext) <- SS.userAccount
            failMsg = s"$InvalidJsonFormat The Json body should be the $InvalidJsonFormat "
            json <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[UpdateAccountJsonV400]
            }
            anyViewContainsCanUpdateBankAccountLabelPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_BANK_ACCOUNT_LABEL))).getOrElse(Nil).find(_.==(true)).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_UPDATE_BANK_ACCOUNT_LABEL)}` permission on any your views",
              cc = callContext
            ) {
              anyViewContainsCanUpdateBankAccountLabelPermission
            }
            (success, callContext) <- Connector.connector.vend.updateAccountLabel(bankId, accountId, json.label, callContext)  map { i =>
              (unboxFullOrFail(i._1, i._2, s"$UpdateBankAccountLabelError Current BankId is $bankId and Current AccountId is $accountId", 404), i._2)
            }
          } yield {
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
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      userLockStatusJson,
      List($UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canLockUser)))

    lazy val lockUser : OBPEndpoint = {
      case "users" :: username ::  "locks" :: Nil JsonPost req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <-  SS.user
            userLocks <- Future { UserLocksProvider.lockUser(localIdentityProvider,username) } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername($username)", 404)
            }
          } yield {
            (JSONFactory400.createUserLockStatusJson(userLocks), HttpCode.`200`(callContext))
          }
      }
    }
    staticResourceDocs += ResourceDoc(
      createUserWithRoles,
      implementedInApiVersion,
      nameOf(createUserWithRoles),
      "POST",
      "/user-entitlements",
      "Create (DAuth) User with Roles",
      s"""
        |This endpoint is used as part of the DAuth solution to grant Entitlements for Roles to a smart contract on the blockchain.
        |
        |Put the smart contract address in username
        |
        |For provider use "dauth"
        |
        |This endpoint will create the User with username and provider if the User does not already exist.
        |
        |Then it will create Entitlements i.e. grant Roles to the User.
        |
        |Entitlements are used to grant System or Bank level roles to Users. (For Account level privileges, see Views)
        |
        |i.e. Entitlements are used to create / consume system or bank level resources where as views / account access are used to consume / create customer level resources.
        |
        |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
        |
        |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
        |
        |Note: The Roles actually granted will depend on the Roles that the calling user has.
        |
        |If you try to grant Entitlements to a user that already exist (duplicate entitilements) you will get an error.
        |
        |For information about DAuth see below:
        |
        |${getGlossaryItem("DAuth")}
        |
        |""",
      postCreateUserWithRolesJsonV400,
      entitlementsJsonV400,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        IncorrectRoleName,
        EntitlementIsBankRole,
        EntitlementIsSystemRole,
        EntitlementAlreadyExists,
        InvalidUserProvider,
        UnknownError
      ),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagDAuth))

    lazy val createUserWithRoles: OBPEndpoint = {
      case "user-entitlements" :: Nil JsonPost json -> _ =>  {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(loggedInUser), callContext) <- authenticatedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCreateUserWithRolesJsonV400 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCreateUserWithRolesJsonV400]
            }

            //provider must start with dauth., can not create other provider users.
            _ <- Helper.booleanToFuture(s"$InvalidUserProvider The user.provider must be start with 'dauth.'", cc=Some(cc)) {
              postedData.provider.startsWith("dauth.")
            }

            //check the system role bankId is Empty, but bank level role need bankId 
            _ <- checkRoleBankIdMappings(callContext, postedData)

            _ <- checkRolesBankIdExsiting(callContext, postedData)

            _ <- checkRolesName(callContext, postedData)

            canCreateEntitlementAtAnyBankRole = Entitlement.entitlement.vend.getEntitlement("", loggedInUser.userId, canCreateEntitlementAtAnyBank.toString())
            
            (targetUser, callContext) <- NewStyle.function.getOrCreateResourceUser(postedData.provider, postedData.username, callContext)

            _ <- if (canCreateEntitlementAtAnyBankRole.isDefined) { 
              //If the loggedIn User has `CanCreateEntitlementAtAnyBankRole` role, then we can grant all the requestRoles to the requestUser.
              //But we must check if the requestUser already has the requestRoles or not.
              assertTargetUserLacksRoles(targetUser.userId, postedData.roles,callContext)
            } else {
              //If the loggedIn user does not have the `CanCreateEntitlementAtAnyBankRole` role, we can only grant the roles which the loggedIn user have.
              //So we need to check if the requestRoles are beyond the current loggedIn user has.
              assertUserCanGrantRoles(loggedInUser.userId, postedData.roles, callContext)
            }

            addedEntitlements <- addEntitlementsToUser(targetUser.userId, postedData, callContext)
            
          } yield {
            (JSONFactory400.createEntitlementJSONs(addedEntitlements), HttpCode.`201`(callContext))
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
      entitlementsJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)))


    lazy val getEntitlements: OBPEndpoint = {
      case "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      entitlementsJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementsForOneBank,canGetEntitlementsForAnyBank)))

    val allowedEntitlements = canGetEntitlementsForOneBank:: canGetEntitlementsForAnyBank :: Nil
    val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")

    lazy val getEntitlementsForBank: OBPEndpoint = {
      case "banks" :: bankId :: "entitlements" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |Authentication is required as the tag is linked with the user.""",
      postAccountTagJSON,
      accountTagJSON,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        InvalidJsonFormat,
        NoViewPermission,
        $UserNoPermissionAccessView,
        UnknownError),
      List(apiTagAccountMetadata, apiTagAccount))

    lazy val addTagForViewOnAccount : OBPEndpoint = {
      //add a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_add_tag. Current ViewId($viewId)", cc=callContext) {
              view.allowed_actions.exists( _ == CAN_ADD_TAG)
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
        |${userAuthenticationMessage(true)}
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
      List(apiTagAccountMetadata, apiTagAccount))

    lazy val deleteTagForViewOnAccount : OBPEndpoint = {
      //delete a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_delete_tag. Current ViewId($viewId)", cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_DELETE_TAG)
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagAccountMetadata, apiTagAccount))

    lazy val getTagsForViewOnAccount : OBPEndpoint = {
      //get tags
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "metadata" :: "tags" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_tags. Current ViewId($viewId)", cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_SEE_TAGS)
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
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2  :: Nil
    )
    lazy val getCoreAccountById : OBPEndpoint = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), account, callContext) <- SS.userAccount
            view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
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
      apiTagAccount  :: Nil
    )
    lazy val getPrivateAccountByIdFull : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagAccount),
    )
    lazy val getAccountByAccountRouting : OBPEndpoint = {
      case "management" :: "accounts" :: "account-routing-query" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $accountRoutingJsonV121"
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[BankAccountRoutingJson]
            }

            (account, callContext) <- NewStyle.function.getBankAccountByRouting(postJson.bank_id.map(BankId(_)),
              postJson.account_routing.scheme, postJson.account_routing.address, cc.callContext)

            user @Full(u) = cc.user
            view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
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
      List(apiTagAccount),
    )
    lazy val getAccountsByAccountRoutingRegex : OBPEndpoint = {
      case "management" :: "accounts" :: "account-routing-regex-query" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
              view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
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
      getBankAccountsBalancesForCurrentUser,
      implementedInApiVersion,
      nameOf(getBankAccountsBalancesForCurrentUser),
      "GET",
      "/banks/BANK_ID/balances",
      "Get Accounts Balances",
      """Get the Balances for the Accounts of the current User at one bank.""",
      EmptyBody,
      accountBalancesV400Json,
      List($UserNotLoggedIn, $BankNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2  :: Nil
    )

    lazy val getBankAccountsBalancesForCurrentUser : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "balances" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            (allowedAccounts, callContext) <- BalanceNewStyle.getAccountAccessAtBank(u, bankId, callContext)
            (accountsBalances, callContext)<- BalanceNewStyle.getBankAccountsBalances(allowedAccounts, callContext)
          } yield {
            (createBalancesJson(accountsBalances), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankAccountBalancesForCurrentUser,
      implementedInApiVersion,
      nameOf(getBankAccountBalancesForCurrentUser),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances",
      "Get Account Balances",
      """Get the Balances for one Account of the current User at one bank.""",
      EmptyBody,
      accountBalanceV400,
      List($UserNotLoggedIn, $BankNotFound, CannotFindAccountAccess, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2  :: Nil
    )

    lazy val getBankAccountBalancesForCurrentUser : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            (allowedAccounts, callContext) <- BalanceNewStyle.getAccountAccessAtBank(u, bankId, callContext)
            msg = s"$CannotFindAccountAccess AccountId(${accountId.value})"
            bankIdAccountId <- NewStyle.function.tryons(msg, 400, cc.callContext) {
              allowedAccounts.find(_.accountId==accountId).get
            }
            (accountBalances, callContext)<- BalanceNewStyle.getBankAccountBalances(bankIdAccountId, callContext)
          } yield {
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
         |  /banks/some-bank-id/firehose/accounts/views/owner?&limit=50&offset=1
         |
         |to invalid Browser cache, add timestamp query parameter as follow, the parameter name must be `_timestamp_`
         |URL params example:
         |  `/banks/some-bank-id/firehose/accounts/views/owner?&limit=50&offset=1&_timestamp_=1596762180358`
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      moderatedFirehoseAccountsJsonV400,
      List($BankNotFound),
      List(apiTagAccount, apiTagAccountFirehose, apiTagFirehoseData),
      Some(List(canUseAccountFirehoseAtAnyBank, ApiRole.canUseAccountFirehose))
    )

    lazy val getFirehoseAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" :: "accounts"  :: "views" :: ViewId(viewId):: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), bank, callContext) <- SS.userBank
            _ <- Helper.booleanToFuture(failMsg = AccountFirehoseNotAllowedOnThisInstance, cc=cc.callContext) {
              allowAccountFirehose
            }
            // here must be a system view, not accountIds in the URL
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, AccountId("")), Some(u), callContext)
            availableBankIdAccountIdList <- Future {
              Views.views.vend.getAllFirehoseAccounts(bank.bankId).map(a => BankIdAccountId(a.bankId,a.accountId))
            }
            params = req.params.filterNot(_._1 == PARAM_TIMESTAMP) // ignore `_timestamp_` parameter, it is for invalid Browser caching
              .filterNot(_._1 == PARAM_LOCALE)
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
              (bankAccount, callContext) <- Connector.connector.vend.getBankAccountLegacy(bankIdAccountId.bankId, bankIdAccountId.accountId,callContext) ?~! s"$BankAccountNotFound Current Bank_Id(${bankIdAccountId.bankId}), Account_Id(${bankIdAccountId.accountId}) "
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
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      fastFirehoseAccountsJsonV400,
      List($BankNotFound),
      List(apiTagAccount, apiTagAccountFirehose, apiTagFirehoseData),
      Some(List(canUseAccountFirehoseAtAnyBank, ApiRole.canUseAccountFirehose))
    )

    lazy val getFastFirehoseAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for all banks
      case "management":: "banks" :: BankId(bankId):: "fast-firehose" :: "accounts"  :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), bank, callContext) <- SS.userBank
            _ <- Helper.booleanToFuture(failMsg = AccountFirehoseNotAllowedOnThisInstance, cc=cc.callContext) {
              allowAccountFirehose
            }
            allowedParams = List("limit", "offset", "sort_direction")
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
            (firehoseAccounts, callContext) <- NewStyle.function.getBankAccountsWithAttributes(bankId, obpQueryParams, callContext)
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
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomer))
    )

    lazy val getCustomersByCustomerPhoneNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "search"  :: "customers" :: "mobile-phone-number" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerPhoneNumberJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostCustomerPhoneNumberJsonV400]
            }
            (customers, callContext) <- NewStyle.function.getCustomersByCustomerPhoneNumber(bankId, postedData.mobile_phone_number , cc.callContext)
          } yield {
            (JSONFactory300.createCustomersJson(customers), HttpCode.`200`(callContext))
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
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      userIdJsonV400,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagUser))

    lazy val getCurrentUserId: OBPEndpoint = {
      case "users" :: "current" :: "user_id" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      userJsonV400,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundById, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUserByUserId: OBPEndpoint = {
      case "users" :: "user_id" :: userId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            user <- Users.users.vend.getUserByUserIdFuture(userId) map {
              x => unboxFullOrFail(x, cc.callContext, s"$UserNotFoundByUserId Current UserId($userId)")
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
            acceptMarketingInfo <- NewStyle.function.getAgreementByUserId(user.userId, "accept_marketing_info", cc.callContext)
            termsAndConditions <- NewStyle.function.getAgreementByUserId(user.userId, "terms_and_conditions", cc.callContext)
            privacyConditions <- NewStyle.function.getAgreementByUserId(user.userId, "privacy_conditions", cc.callContext)
            isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
          } yield {
            val agreements = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
            (JSONFactory400.createUserInfoJSON(user, entitlements, Some(agreements), isLocked), HttpCode.`200`(cc.callContext))
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
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      userJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByProviderAndUsername, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUserByUsername: OBPEndpoint = {
      case "users" :: "username" :: username :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            user <- Users.users.vend.getUserByProviderAndUsernameFuture(Constant.localIdentityProvider, username) map {
              x => unboxFullOrFail(x, cc.callContext, UserNotFoundByProviderAndUsername, 404)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
            isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
          } yield {
            (JSONFactory400.createUserInfoJSON(user, entitlements, None, isLocked), HttpCode.`200`(cc.callContext))
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
         |${userAuthenticationMessage(true)}
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      usersJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUsersByEmail: OBPEndpoint = {
      case "users" :: "email" :: email :: "terminator" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
         |${urlParametersDocument(false, false)}
         |* locked_status (if null ignore)
         |
      """.stripMargin,
      EmptyBody,
      usersJsonV400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canGetAnyUser)))

    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, cc.callContext)
            users <- Users.users.vend.getUsers(obpQueryParams)
          } yield {
            (JSONFactory400.createUsersJson(users), HttpCode.`200`(callContext))
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
         | This endpoint will send an invitation email to the developers, then they can use the link to create the obp user.
         | 
         | purpose filed only support:${UserInvitationPurpose.values.toString()}.
         | 
         | You can customise the email details use the following webui props:
         | 
         | when purpose == ${UserInvitationPurpose.DEVELOPER.toString}
         | webui_developer_user_invitation_email_subject
         | webui_developer_user_invitation_email_from
         | webui_developer_user_invitation_email_text
         | webui_developer_user_invitation_email_html_text
         | 
         | when purpose = == ${UserInvitationPurpose.CUSTOMER.toString}
         | webui_customer_user_invitation_email_subject
         | webui_customer_user_invitation_email_from
         | webui_customer_user_invitation_email_text
         | webui_customer_user_invitation_email_html_text
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
      List(apiTagUserInvitation, apiTagKyc),
      Some(canCreateUserInvitation :: Nil)
    )

    lazy val createUserInvitation : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitation" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          logger.debug(s"Hello from the endpoint {$createUserInvitation}")
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserInvitationJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostUserInvitationJsonV400]
            }

            _ <- NewStyle.function.tryons(s"$InvalidJsonValue postedData.purpose only support ${UserInvitationPurpose.values.toString()}", 400, cc.callContext) {
              UserInvitationPurpose.withName(postedData.purpose)
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
            val link = s"${APIUtil.getPropsValue("user_invitation_link_base_URL", APIUtil.getPropsValue("portal_hostname", Constant.HostName))}/user-invitation?id=${invitation.secretKey}"
            if (postedData.purpose == UserInvitationPurpose.DEVELOPER.toString){
              val subject = getWebUiPropsValue("webui_developer_user_invitation_email_subject", "Welcome to the API Playground")
              val from = getWebUiPropsValue("webui_developer_user_invitation_email_from", "do-not-reply@openbankproject.com")
              val customText = getWebUiPropsValue("webui_developer_user_invitation_email_text", WebUITemplate.webUiDeveloperUserInvitationEmailText)
              logger.debug(s"customText: ${customText}")
              val customHtmlText = getWebUiPropsValue("webui_developer_user_invitation_email_html_text", WebUITemplate.webUiDeveloperUserInvitationEmailHtmlText)
                .replace(WebUIPlaceholder.emailRecipient, invitation.firstName)
                .replace(WebUIPlaceholder.activateYourAccount, link)
              logger.debug(s"customHtmlText: ${customHtmlText}")
              logger.debug(s"Before send user invitation by email. Purpose: ${UserInvitationPurpose.DEVELOPER}")
              
              // Use Apache Commons Email wrapper instead of Lift Mailer
              val emailContent = EmailContent(
                from = from,
                to = List(invitation.email),
                subject = subject,
                textContent = Some(customText),
                htmlContent = Some(customHtmlText)
              )
              
              sendHtmlEmail(emailContent) match {
                case Full(messageId) => logger.debug(s"Email sent successfully with Message-ID: $messageId")
                case Empty => logger.error("Failed to send user invitation email")
              }
              
              logger.debug(s"After send user invitation by email. Purpose: ${UserInvitationPurpose.DEVELOPER}")
            } else {
              val subject = getWebUiPropsValue("webui_customer_user_invitation_email_subject", "Welcome to the API Playground")
              val from = getWebUiPropsValue("webui_customer_user_invitation_email_from", "do-not-reply@openbankproject.com")
              val customText = getWebUiPropsValue("webui_customer_user_invitation_email_text", WebUITemplate.webUiDeveloperUserInvitationEmailText)
              logger.debug(s"customText: ${customText}")
              val customHtmlText = getWebUiPropsValue("webui_customer_user_invitation_email_html_text", WebUITemplate.webUiDeveloperUserInvitationEmailHtmlText)
                .replace(WebUIPlaceholder.emailRecipient, invitation.firstName)
                .replace(WebUIPlaceholder.activateYourAccount, link)
              logger.debug(s"customHtmlText: ${customHtmlText}")
              logger.debug(s"Before send user invitation by email.")
              
              // Use Apache Commons Email wrapper instead of Lift Mailer
              val emailContent = EmailContent(
                from = from,
                to = List(invitation.email),
                subject = subject,
                textContent = Some(customText),
                htmlContent = Some(customHtmlText)
              )
              
              sendHtmlEmail(emailContent) match {
                case Full(messageId) => logger.debug(s"Email sent successfully with Message-ID: $messageId")
                case Empty => logger.error("Failed to send user invitation email")
              }
              
              logger.debug(s"After send user invitation by email.")
            }
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
      s"""Get User Invitation Information.
         |
         |${userAuthenticationMessage(false)}
         |""",
      PostUserInvitationAnonymousJsonV400(secret_key = 5819479115482092878L),
      userInvitationJsonV400,
      List(
        $BankNotFound,
        UserCustomerLinksNotFoundForUser,
        CannotGetUserInvitation,
        CannotFindUserInvitation,
        UnknownError
      ),
      List(apiTagUserInvitation, apiTagKyc)
    )

    lazy val getUserInvitationAnonymous : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitations" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagUserInvitation),
      Some(List(canGetUserInvitation))
    )

    lazy val getUserInvitation : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitations" :: secretLink :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagUserInvitation),
      Some(List(canGetUserInvitation))
    )

    lazy val getUserInvitations : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user-invitations" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canDeleteUser)))

    lazy val deleteUser : OBPEndpoint = {
      case "users" :: userId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |Only SANDBOX mode
         |The settlement accounts are created specified by the bank in the POST body.
         |Name and account id are created in accordance to the next rules:
         |  - Incoming account (name: Default incoming settlement account, Account ID: OBP_DEFAULT_INCOMING_ACCOUNT_ID, currency: EUR)
         |  - Outgoing account (name: Default outgoing settlement account, Account ID: OBP_DEFAULT_OUTGOING_ACCOUNT_ID, currency: EUR)
         |
         |""",
      postBankJson400,
      bankJson400,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      List(apiTagBank),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $BankJson400 "
          for {
            bank <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[BankJson400]
            }
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc=cc.callContext) {
              cc.callContext.map(_.consumer.isDefined == true).isDefined
            }

            checkShortStringValue = APIUtil.checkShortString(bank.id)
            
            _ <- Helper.booleanToFuture(failMsg = s"$checkShortStringValue.", cc=cc.callContext) {
              checkShortStringValue==SILENCE_IS_GOLDEN
            }

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc=cc.callContext) {
              bank.id.length > 3
            }

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc=cc.callContext) {
              !bank.id.contains(" ")
            }

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc=cc.callContext) {
              !`checkIfContains::::` (bank.id)
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
            _ <- entitlementsByBank.filter(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(bank.id, cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
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
      List(apiTagDirectDebit, apiTagAccount))

    lazy val createDirectDebit : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "direct-debit" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_create_direct_debit. Current ViewId($viewId)", cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_CREATE_DIRECT_DEBIT)
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
      List(apiTagDirectDebit, apiTagAccount),
      Some(List(canCreateDirectDebitAtOneBank))
    )

    lazy val createDirectDebitManagement : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "direct-debit" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagStandingOrder, apiTagAccount))

    lazy val createStandingOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "standing-order" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_create_standing_order. Current ViewId($viewId)", cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_CREATE_STANDING_ORDER)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostStandingOrderJsonV400 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostStandingOrderJsonV400]
            }
            amountValue <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, callContext) {
              BigDecimal(postJson.amount.amount)
            }
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'", cc=callContext) {
              APIUtil.isValidCurrencyISOCode(postJson.amount.currency)
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
      List(apiTagStandingOrder, apiTagAccount),
      Some(List(canCreateStandingOrderAtOneBank))
    )

    lazy val createStandingOrderManagement : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "standing-order" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostStandingOrderJsonV400 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostStandingOrderJsonV400]
            }
            amountValue <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${postJson.amount.amount} ", 400, cc.callContext) {
              BigDecimal(postJson.amount.amount)
            }
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${postJson.amount.currency}'", cc=cc.callContext) {
              APIUtil.isValidCurrencyISOCode(postJson.amount.currency)
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
         |${userAuthenticationMessage(true)} and the user needs to be account holder.
         |
         |""",
      postAccountAccessJsonV400,
      viewJsonV300,
      List(
        $UserNotLoggedIn,
        UserLacksPermissionCanGrantAccessToViewForTargetAccount,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotGrantAccountAccess,
        UnknownError
      ),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val grantUserAccessToView : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: "grant" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
          for {
            (Full(u), callContext) <- SS.user
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostAccountAccessJsonV400]
            }
            msg = UserLacksPermissionCanGrantAccessToViewForTargetAccount + s"Current ViewId(${postJson.view.view_id}) and current UserId(${u.userId})"
            _ <- Helper.booleanToFuture(msg, cc = cc.callContext) {
              APIUtil.canGrantAccessToView(bankId, accountId, ViewId(postJson.view.view_id), u, callContext)
            }
            (user, callContext) <- NewStyle.function.findByUserId(postJson.user_id, callContext)
            view <- getView(bankId, accountId, postJson.view, callContext)
            addedView <- grantAccountAccessToUser(bankId, accountId, user, view, callContext)
          } yield {
            val viewJson = JSONFactory300.createViewJSON(addedView)
            (viewJson, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createUserWithAccountAccess,
      implementedInApiVersion,
      nameOf(createUserWithAccountAccess),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/user-account-access",
      "Create (DAuth) User with Account Access",
      s"""This endpoint is used as part of the DAuth solution to grant access to account and transaction data to a smart contract on the blockchain.
         |
         |Put the smart contract address in username
         |
         |For provider use "dauth"
         |
         |This endpoint will create the (DAuth) User with username and provider if the User does not already exist.
         |
         |${userAuthenticationMessage(true)} and the logged in user needs to be account holder.
         |
         |For information about DAuth see below:
         |
         |${getGlossaryItem("DAuth")}
         |
         |""",
      postCreateUserAccountAccessJsonV400,
      List(viewJsonV300),
      List(
        $UserNotLoggedIn,
        UserLacksPermissionCanGrantAccessToViewForTargetAccount,
        InvalidJsonFormat,
        SystemViewNotFound,
        ViewNotFound,
        CannotGrantAccountAccess,
        UnknownError
      ),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired, apiTagDAuth))

    lazy val createUserWithAccountAccess : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "user-account-access" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostCreateUserAccountAccessJsonV400 "
          for {
            (Full(u), callContext) <- SS.user
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostCreateUserAccountAccessJsonV400]
            }
            //provider must start with dauth., can not create other provider users.
            _ <- Helper.booleanToFuture(s"$InvalidUserProvider The user.provider must be start with 'dauth.'", cc=Some(cc)) {
              postJson.provider.startsWith("dauth.")
            }
            viewIdList = postJson.views.map(view =>ViewId(view.view_id))
            msg = UserLacksPermissionCanGrantAccessToViewForTargetAccount + s"Current ViewIds(${viewIdList.mkString}) and current UserId(${u.userId})"
            _ <- Helper.booleanToFuture(msg, 403, cc = Some(cc)) {
              APIUtil.canGrantAccessToMultipleViews(bankId, accountId, viewIdList, u, callContext)
            }
            (targetUser, callContext) <- NewStyle.function.getOrCreateResourceUser(postJson.provider, postJson.username, cc.callContext)
            views <- getViews(bankId, accountId, postJson, callContext)
            addedView <- grantMultpleAccountAccessToUser(bankId, accountId, targetUser, views, callContext)
          } yield {
            val viewsJson = addedView.map(JSONFactory300.createViewJSON(_))
            (viewsJson, HttpCode.`201`(callContext))
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
         |${userAuthenticationMessage(true)} and the user needs to be account holder.
         |
         |""",
      postAccountAccessJsonV400,
      revokedJsonV400,
      List(
        $UserNotLoggedIn,
        UserLacksPermissionCanRevokeAccessToViewForTargetAccount,
        InvalidJsonFormat,
        UserNotFoundById,
        SystemViewNotFound,
        ViewNotFound,
        CannotRevokeAccountAccess,
        CannotFindAccountAccess,
        UnknownError
      ),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val revokeUserAccessToView : OBPEndpoint = {
      //add access for specific user to a specific system view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: "revoke" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
          for {
            (Full(u), callContext) <- SS.user
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostAccountAccessJsonV400]
            }
            viewId = ViewId(postJson.view.view_id)
            msg = UserLacksPermissionCanGrantAccessToViewForTargetAccount + s"Current ViewId(${viewId}) and current UserId(${u.userId})"
            _ <- Helper.booleanToFuture(msg, cc = cc.callContext) {
              APIUtil.canRevokeAccessToView(bankId, accountId, viewId, u, callContext)
            }
            (user, callContext) <- NewStyle.function.findByUserId(postJson.user_id, cc.callContext)
            view <- postJson.view.is_system match {
              case true => ViewNewStyle.systemView(viewId, callContext)
              case false => ViewNewStyle.customView(viewId, BankIdAccountId(bankId, accountId), callContext)
            }
            revoked <- postJson.view.is_system match {
              case true => ViewNewStyle.revokeAccessToSystemView(bankId, accountId, view, user, callContext)
              case false => ViewNewStyle.revokeAccessToCustomView(view, user, callContext)
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
         |${userAuthenticationMessage(true)} and the user needs to be an account holder or has owner view access.
         |
         |""",
      postRevokeGrantAccountAccessJsonV400,
      revokedJsonV400,
      List(
        $UserNotLoggedIn,
        UserLacksPermissionCanGrantAccessToViewForTargetAccount,
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
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV400 "
          for {
            (Full(u), callContext) <- SS.user
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostRevokeGrantAccountAccessJsonV400]
            }
            msg = UserLacksPermissionCanGrantAccessToViewForTargetAccount + s"Current ViewIds(${postJson.views.mkString}) and current UserId(${u.userId})"
            _ <- Helper.booleanToFuture(msg, cc = cc.callContext) {
              APIUtil.canRevokeAccessToAllViews(bankId, accountId, u, callContext)
            }
           _ <- Future(Views.views.vend.revokeAccountAccessByUser(bankId, accountId, u, callContext)) map {
              unboxFullOrFail(_, callContext, s"Cannot revoke")
            }
            grantViews = for (viewId <- postJson.views) yield BankIdAccountIdViewId(bankId, accountId, ViewId(viewId))
            _ <- Future(Views.views.vend.grantAccessToMultipleViews(grantViews, u, callContext)) map {
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canCreateCustomerAttributeAtOneBank, canCreateCustomerAttributeAtAnyBank)))

    lazy val createCustomerAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attribute" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canUpdateCustomerAttributeAtOneBank, canUpdateCustomerAttributeAtAnyBank))
    )

    lazy val updateCustomerAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attributes" :: customerAttributeId :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canGetCustomerAttributesAtOneBank, canGetCustomerAttributesAtAnyBank))
    )

    lazy val getCustomerAttributes : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canGetCustomerAttributeAtOneBank, canGetCustomerAttributeAtAnyBank))
    )

    lazy val getCustomerAttributeById : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: customerId :: "attributes" :: customerAttributeId ::Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |URL params example: /banks/some-bank-id/customers?&limit=50&offset=1
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
      List(apiTagCustomer),
      Some(List(canGetCustomer))
    )

    lazy val getCustomersByAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" ::  Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransaction),
      Some(List(canCreateTransactionAttributeAtOneBank)))

    lazy val createTransactionAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: TransactionId(transactionId) :: "attribute" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransaction),
      Some(List(canUpdateTransactionAttributeAtOneBank))
    )

    lazy val updateTransactionAttribute : OBPEndpoint = {
      case "banks" ::  BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: TransactionId(transactionId) :: "attributes" :: transactionAttributeId :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransaction),
      Some(List(canGetTransactionAttributesAtOneBank))
    )

    lazy val getTransactionAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: TransactionId(transactionId) :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransaction),
      Some(List(canGetTransactionAttributeAtOneBank))
    )

    lazy val getTransactionAttributeById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) ::  "transactions" :: TransactionId(transactionId) :: "attributes" :: transactionAttributeId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagTransactionRequest),
      Some(List(canCreateHistoricalTransactionAtBank))
    )


    lazy val createHistoricalTransactionAtBank : OBPEndpoint =  {
      case "banks" :: BankId(bankId) :: "management"  :: "historical" :: "transactions" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
              APIUtil.isValidCurrencyISOCode(transDetailsJson.value.currency)
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
        GetTransactionRequestsException,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2))

    lazy val getTransactionRequest: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: TransactionRequestId(requestId) :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
            view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, BankIdAccountId(bankId, accountId), Full(u), callContext)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_REQUESTS)}` permission on the View(${viewId.value})",
              cc = callContext) {
              view.allowed_actions.exists(_ ==CAN_SEE_TRANSACTION_REQUESTS)
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
         |URL params example: /banks/some-bank-id/accounts?&limit=50&offset=1
         |
         |
      """.stripMargin,
      EmptyBody,
      basicAccountsJSON,
      List($UserNotLoggedIn, $BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData)
    )

    lazy val getPrivateAccountsAtOneBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), bank, callContext) <- SS.userBank
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)
            params = req.params.filterNot(_._1 == PARAM_TIMESTAMP) // ignore `_timestamp_` parameter, it is for invalid Browser caching
              .filterNot(_._1 == PARAM_LOCALE)
            privateAccountAccess2 <- if(params.isEmpty || privateAccountAccess.isEmpty) {
              Future.successful(privateAccountAccess)
            } else {
              AccountAttributeX.accountAttributeProvider.vend
                .getAccountIdsByParams(bankId, params)
                .map { boxedAccountIds => 
                  val accountIds = boxedAccountIds.getOrElse(Nil)
                  privateAccountAccess.filter(aa => accountIds.contains(aa.account_id.get))
                }
            }
            (availablePrivateAccounts, callContext) <- bank.privateAccountsFuture(privateAccountAccess2, callContext)
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
        "Web",
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
      List(apiTagConsumer),
      Some(List(canCreateConsumer)))


    lazy val createConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            (postedJson,appType) <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) {
              val consumerPostJSON = json.extract[ConsumerPostJSON]
              val appType = if(consumerPostJSON.app_type.equals("Confidential")) AppType.valueOf("Confidential") else AppType.valueOf("Public")
              (consumerPostJSON, appType)
            }
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canCreateConsumer, callContext)
            (consumer, callContext) <- createConsumerNewStyle(
              key = Some(Helpers.randomString(40).toLowerCase),
              secret = Some(Helpers.randomString(40).toLowerCase),
              isActive = Some(postedJson.enabled),
              name= Some(postedJson.app_name),
              appType = Some(appType),
              description = Some(postedJson.description),
              developerEmail = Some(postedJson.developer_email),
              company = None,
              redirectURL = Some(postedJson.redirect_url),
              createdByUserId = Some(u.userId),
              clientCertificate = Some(postedJson.clientCertificate),
              logoURL = None,
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


    staticResourceDocs += ResourceDoc(
      getCustomersAtAnyBank,
      implementedInApiVersion,
      nameOf(getCustomersAtAnyBank),
      "GET",
      "/customers",
      "Get Customers at Any Bank",
      s"""Get Customers at Any Bank.
         |
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customersJsonV300,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser),
      Some(List(canGetCustomersAtAnyBank))
    )
    lazy val getCustomersAtAnyBank : OBPEndpoint = {
      case "customers" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            (customers, callContext) <- getCustomersAtAllBanks(callContext, requestParams)
          } yield {
            (JSONFactory300.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomersMinimalAtAnyBank,
      implementedInApiVersion,
      nameOf(getCustomersMinimalAtAnyBank),
      "GET",
      "/customers-minimal",
      "Get Customers Minimal at Any Bank",
      s"""Get Customers Minimal at Any Bank.
         |
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customersMinimalJsonV300,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser),
      Some(List(canGetCustomersMinimalAtAnyBank))
    )
    lazy val getCustomersMinimalAtAnyBank : OBPEndpoint = {
      case "customers-minimal" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            (customers, callContext) <- getCustomersAtAllBanks(callContext, requestParams)
          } yield {
            (createCustomersMinimalJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
          }
        }
      }
    }


    staticResourceDocs += ResourceDoc(
      getScopes,
      implementedInApiVersion,
      nameOf(getScopes),
      "GET",
      "/consumers/CONSUMER_ID/scopes",
      "Get Scopes for Consumer",
      s"""Get all the scopes for an consumer specified by CONSUMER_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |
      """.stripMargin,
      EmptyBody,
      scopeJsons,
      List(UserNotLoggedIn, EntitlementNotFound, ConsumerNotFoundByConsumerId, UnknownError),
      List(apiTagScope, apiTagConsumer))

    lazy val getScopes: OBPEndpoint = {
      case "consumers" :: uuidOfConsumer :: "scopes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            consumer <- Future{callContext.get.consumer} map {
              x => unboxFullOrFail(x , callContext, InvalidConsumerCredentials)
            }
            _ <- Future {NewStyle.function.hasEntitlementAndScope("", u.userId, consumer.id.get.toString, canGetEntitlementsForAnyUserAtAnyBank, callContext)} flatMap {unboxFullAndWrapIntoFuture(_)}
            consumer <- NewStyle.function.getConsumerByConsumerId(uuidOfConsumer, callContext)
            primaryKeyOfConsumer = consumer.id.get.toString
            scopes <- Future { Scope.scope.vend.getScopesByConsumerId(primaryKeyOfConsumer)} map { unboxFull(_) }
          } yield
            (JSONFactory300.createScopeJSONs(scopes), HttpCode.`200`(callContext))
      }
    }
    
    staticResourceDocs += ResourceDoc(
      addScope,
      implementedInApiVersion,
      nameOf(addScope),
      "POST",
      "/consumers/CONSUMER_ID/scopes",
      "Create Scope for a Consumer",
      """Create Scope. Grant Role to Consumer.
        |
        |Scopes are used to grant System or Bank level roles to the Consumer (App). (For Account level privileges, see Views)
        |
        |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
        |
        |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
        |
        |""",
      SwaggerDefinitionsJSON.createScopeJson,
      scopeJson,
      List(
        UserNotLoggedIn,
        ConsumerNotFoundById,
        InvalidJsonFormat,
        IncorrectRoleName,
        EntitlementIsBankRole,
        EntitlementIsSystemRole,
        EntitlementAlreadyExists,
        UnknownError
      ),
      List(apiTagScope, apiTagConsumer),
      Some(List(canCreateScopeAtAnyBank, canCreateScopeAtOneBank)))

    lazy val addScope : OBPEndpoint = {
      case "consumers" :: consumerId :: "scopes" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            postedData <- Future { tryo{json.extract[CreateScopeJson]} } map {
              val msg = s"$InvalidJsonFormat The Json body should be the $CreateScopeJson "
              x => unboxFullOrFail(x, callContext, msg)
            }
            role <- Future { tryo{valueOf(postedData.role_name)} } map {
              val msg = IncorrectRoleName + postedData.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", ")
              x => unboxFullOrFail(x, callContext, msg)
            }
            _ <- Helper.booleanToFuture(failMsg = if (ApiRole.valueOf(postedData.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole, cc=callContext) {
              ApiRole.valueOf(postedData.role_name).requiresBankId == postedData.bank_id.nonEmpty
            }
            allowedEntitlements = canCreateScopeAtOneBank :: canCreateScopeAtAnyBank :: Nil
            allowedEntitlementsTxt = s"$UserHasMissingRoles ${allowedEntitlements.mkString(", ")}!"
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = allowedEntitlementsTxt)(postedData.bank_id, u.userId, allowedEntitlements, callContext)
            _ <- Helper.booleanToFuture(failMsg = BankNotFound, cc=callContext) {
              postedData.bank_id.nonEmpty == false || BankX(BankId(postedData.bank_id), callContext).map(_._1).isEmpty == false
            }
            _ <- Helper.booleanToFuture(failMsg = EntitlementAlreadyExists, cc=callContext) {
              hasScope(postedData.bank_id, consumerId, role) == false
            }
            addedEntitlement <- Future {Scope.scope.vend.addScope(postedData.bank_id, consumer.id.get.toString, postedData.role_name)} map { unboxFull(_) }
          } yield {
            (JSONFactory300.createScopeJson(addedEntitlement), HttpCode.`201`(callContext))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canDeleteCustomerAttributeAtOneBank, canDeleteCustomerAttributeAtAnyBank)))

    lazy val deleteCustomerAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "customers" :: "attributes" :: customerAttributeId ::  Nil JsonDelete _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canCreateDynamicEndpoint)))

    lazy val createDynamicEndpoint: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canCreateBankLevelDynamicEndpoint, canCreateDynamicEndpoint)))

    lazy val createBankLevelDynamicEndpoint: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) ::"dynamic-endpoints" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canUpdateDynamicEndpoint)))

    lazy val updateDynamicEndpointHost: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: dynamicEndpointId :: "host" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canUpdateBankLevelDynamicEndpoint, canUpdateDynamicEndpoint)))

    lazy val updateBankLevelDynamicEndpointHost: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: dynamicEndpointId :: "host" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetDynamicEndpoint)))

    lazy val getDynamicEndpoint: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: dynamicEndpointId :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetDynamicEndpoints)))

    lazy val getDynamicEndpoints: OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetBankLevelDynamicEndpoint, canGetDynamicEndpoint)))

    lazy val getBankLevelDynamicEndpoint: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: dynamicEndpointId :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canGetBankLevelDynamicEndpoints, canGetDynamicEndpoints)))

    lazy val getBankLevelDynamicEndpoints: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canDeleteDynamicEndpoint)))

    lazy val deleteDynamicEndpoint : OBPEndpoint = {
      case "management" :: "dynamic-endpoints" :: dynamicEndpointId ::  Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
      Some(List(canDeleteBankLevelDynamicEndpoint ,canDeleteDynamicEndpoint)))

    lazy val deleteBankLevelDynamicEndpoint : OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-endpoints" :: dynamicEndpointId ::  Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi)
    )

    lazy val getMyDynamicEndpoints: OBPEndpoint = {
      case "my" :: "dynamic-endpoints" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagManageDynamicEndpoint, apiTagApi),
    )

    lazy val deleteMyDynamicEndpoint : OBPEndpoint = {
      case "my" :: "dynamic-endpoints" :: dynamicEndpointId ::  Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canCreateCustomerAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateCustomerAttributeAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "customer" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagAccount),
      Some(List(canCreateAccountAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateAccountAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "account" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagProduct),
      Some(List(canCreateProductAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateProductAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "product" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      productAttributeJsonV400,
      productAttributeResponseJsonV400,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagProduct),
      Some(List(canCreateProductAttribute))
    )

    lazy val createProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attribute" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
            (products, callContext) <-NewStyle.function.getProduct(BankId(bankId), ProductCode(productCode), callContext)
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      productAttributeJsonV400,
      productAttributeResponseJsonV400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct),
      Some(List(canUpdateProductAttribute))
    )

    lazy val updateProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonPut json -> _ =>{
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      productAttributeResponseJsonV400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct),
      Some(List(canUpdateProductAttribute))
      )

    lazy val getProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagProduct),
      Some(List(canCreateProductFee))
    )

    lazy val createProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fee" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagProduct),
      Some(List(canUpdateProductFee)))

    lazy val updateProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: productFeeId :: Nil JsonPut json -> _ =>{
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(false)}
         |
         |""",
      EmptyBody,
      productFeeResponseJsonV400,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct)
    )

    lazy val getProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: productFeeId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(false)}
         |
         |""",
      EmptyBody,
      productFeesResponseJsonV400,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct)
    )

    lazy val getProductFees : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagProduct),
      Some(List(canDeleteProductFee)))

    lazy val deleteProductFee : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "fees" :: productFeeId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagBank),
      Some(List(canCreateBankAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateBankAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "bank" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      bankAttributeJsonV400,
      bankAttributeResponseJsonV400,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank),
      Some(List(canCreateBankAttribute))
    )

    lazy val createBankAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "attribute" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      bankAttributesResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank),
      Some(List(canGetBankAttribute))
    )

    lazy val getBankAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      bankAttributeResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagBank),
      Some(List(canGetBankAttribute))
    )

    lazy val getBankAttribute : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attributes" :: bankAttributeId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      bankAttributeJsonV400,
      bankAttributeDefinitionJsonV400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagBank))

    lazy val updateBankAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "attributes" :: bankAttributeId :: Nil JsonPut json -> _ =>{
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        UserHasMissingRoles,
        BankNotFound,
        UnknownError
      ),
      List(apiTagBank))

    lazy val deleteBankAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "attributes" :: bankAttributeId ::  Nil JsonDelete _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransaction),
      Some(List(canCreateTransactionAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateTransactionAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCard),
      Some(List(canCreateCardAttributeDefinitionAtOneBank)))

    lazy val createOrUpdateCardAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "card" :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransaction),
      Some(List(canDeleteTransactionAttributeDefinitionAtOneBank)))

    lazy val deleteTransactionAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "transaction" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canDeleteCustomerAttributeDefinitionAtOneBank)))

    lazy val deleteCustomerAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "customer" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagAccount),
      Some(List(canDeleteAccountAttributeDefinitionAtOneBank)))

    lazy val deleteAccountAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "account" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct),
      Some(List(canDeleteProductAttributeDefinitionAtOneBank)))

    lazy val deleteProductAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "product" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCard),
      Some(List(canDeleteCardAttributeDefinitionAtOneBank)))

    lazy val deleteCardAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: attributeDefinitionId :: "card" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      productAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagProduct),
      Some(List(canGetProductAttributeDefinitionAtOneBank)))

    lazy val getProductAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "product" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customerAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canGetCustomerAttributeDefinitionAtOneBank)))

    lazy val getCustomerAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "customer" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      accountAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagAccount),
      Some(List(canGetAccountAttributeDefinitionAtOneBank)))

    lazy val getAccountAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "account" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      transactionAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagTransaction),
      Some(List(canGetTransactionAttributeDefinitionAtOneBank)))

    lazy val getTransactionAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "transaction" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      cardAttributeDefinitionsResponseJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCard),
      Some(List(canGetCardAttributeDefinitionAtOneBank)))

    lazy val getCardAttributeDefinition : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "attribute-definitions" :: "card" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagCustomer),
      Some(List(canDeleteUserCustomerLink)))

    lazy val deleteUserCustomerLink : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user_customer_links" :: userCustomerLinkId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      userCustomerLinksJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canGetUserCustomerLink)))

    lazy val getUserCustomerLinksByUserId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user_customer_links" :: "users" :: userId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (userCustomerLinks, callContext) <- UserCustomerLinkNewStyle.getUserCustomerLinksByUserId(
              userId,
              cc.callContext
            )
          } yield {
            (JSONFactory200.createUserCustomerLinkJSONs(userCustomerLinks), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      createUserCustomerLinks,
      implementedInApiVersion,
      "createUserCustomerLinks",
      "POST",
      "/banks/BANK_ID/user_customer_links",
      "Create User Customer Link",
      s"""Link a User to a Customer
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      createUserCustomerLinkJson,
      userCustomerLinkJson,
      List(
        $UserNotLoggedIn,
        InvalidBankIdFormat,
        $BankNotFound,
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId,
        UserHasMissingRoles,
        CustomerAlreadyExistsForUser,
        CreateUserCustomerLinksError,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser),
      Some(List(canCreateUserCustomerLinkAtAnyBank, canCreateUserCustomerLink)))

    lazy val createUserCustomerLinks : OBPEndpoint = {
      case "banks" :: BankId(bankId):: "user_customer_links" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- NewStyle.function.tryons(s"$InvalidBankIdFormat", 400, cc.callContext) {
              assert(isValidID(bankId.value))
            }
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CreateUserCustomerLinkJson ", 400, cc.callContext) {
              json.extract[CreateUserCustomerLinkJson]
            }
            user <- Users.users.vend.getUserByUserIdFuture(postedData.user_id) map {
              x => unboxFullOrFail(x, cc.callContext, UserNotFoundByUserId, 404)
            }
            _ <- booleanToFuture("Field customer_id is not defined in the posted json!", 400, cc.callContext) {
              postedData.customer_id.nonEmpty
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(postedData.customer_id, cc.callContext)
            _ <- booleanToFuture(s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bankId.value}) in URL", 400, callContext) {
              customer.bankId == bankId.value
            }
            _ <- booleanToFuture(CustomerAlreadyExistsForUser, 400, callContext) {
              UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(postedData.user_id, postedData.customer_id).isEmpty == true
            }
            userCustomerLink <- Future {
              UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(postedData.user_id, postedData.customer_id, new Date(), true)
            } map {
              x => unboxFullOrFail(x, callContext, CreateUserCustomerLinksError, 400)
            }
          } yield {
            (code.api.v2_0_0.JSONFactory200.createUserCustomerLinkJSON(userCustomerLink), HttpCode.`201`(callContext))
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
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      userCustomerLinksJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canGetUserCustomerLink)))

    lazy val getUserCustomerLinksByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "user_customer_links" :: "customers" :: customerId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (userCustomerLinks, callContext) <- getUserCustomerLinks(customerId, cc.callContext)
          } yield {
            (JSONFactory200.createUserCustomerLinkJSONs(userCustomerLinks), HttpCode.`200`(callContext))
          }
      }
    }    

    staticResourceDocs += ResourceDoc(
      getCorrelatedUsersInfoByCustomerId,
      implementedInApiVersion,
      nameOf(getCorrelatedUsersInfoByCustomerId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/correlated-users",
      "Get Correlated User Info by Customer",
      s"""Get Correlated User Info by CUSTOMER_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customerAndUsersWithAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canGetCorrelatedUsersInfoAtAnyBank, canGetCorrelatedUsersInfo)))

    lazy val getCorrelatedUsersInfoByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "correlated-users" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            (userCustomerLinks, callContext) <- getUserCustomerLinks(customerId, callContext)
            (users, callContext) <- NewStyle.function.getUsersByUserIds(userCustomerLinks.map(_.userId), callContext)
            (attributes, callContext) <- NewStyle.function.getUserAttributesByUsers(userCustomerLinks.map(_.userId), callContext)
          } yield {
            (JSONFactory400.createCustomerAdUsersWithAttributesJson(customer, users, attributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyCorrelatedEntities,
      implementedInApiVersion,
      nameOf(getMyCorrelatedEntities),
      "GET",
      "/my/correlated-entities",
      "Get Correlated Entities for the current User",
      s"""Correlated Entities are users and customers linked to the currently authenticated user via User-Customer-Links
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      correlatedUsersResponseJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagCustomer))

    private def getCorrelatedUsersInfo(userCustomerLink:UserCustomerLink, callContext: Option[CallContext]) = for {
      (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(userCustomerLink.customerId, callContext)
      (userCustomerLinks, callContext) <- getUserCustomerLinks(userCustomerLink.customerId, callContext)
      (users, callContext) <- NewStyle.function.getUsersByUserIds(userCustomerLinks.map(_.userId), callContext)
      (attributes, callContext) <- NewStyle.function.getUserAttributesByUsers(userCustomerLinks.map(_.userId), callContext)
    } yield{
      (customer, users, attributes, callContext)
    }
    
    lazy val getMyCorrelatedEntities : OBPEndpoint = {
      case "my" :: "correlated-entities" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user  
            (userCustomerLinks, callContext) <- UserCustomerLinkNewStyle.getUserCustomerLinksByUserId(
              u.userId,
              callContext
            )
            correlatedUserInfoList <- Future.sequence(userCustomerLinks.map(getCorrelatedUsersInfo(_, callContext)))
          } yield {
            (CorrelatedEntities(correlatedUserInfoList.map(
              correlatedUserInfo => 
                JSONFactory400.createCustomerAdUsersWithAttributesJson(
                  correlatedUserInfo._1, 
                  correlatedUserInfo._2, 
                  correlatedUserInfo._3))), 
              HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createCustomer,
      implementedInApiVersion,
      nameOf(createCustomer),
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""
         |The Customer resource stores the customer number (which is set by the backend), legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
         |Dates need to be in the format 2013-01-21T23:08:00Z
         |
         |Note: If you need to set a specific customer number, use the Update Customer Number endpoint after this call.
         |
         |${userAuthenticationMessage(true)}
         |""",
      postCustomerJsonV310,
      customerJsonV310,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        CustomerNumberAlreadyExists,
        UserNotFoundById,
        CustomerAlreadyExistsForUser,
        CreateConsumerError,
        UnknownError
      ),
      List(apiTagCustomer, apiTagPerson),
      Some(List(canCreateCustomer,canCreateCustomerAtAnyBank))
    )
    lazy val createCustomer : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV310 ", 400, cc.callContext) {
              json.extract[PostCustomerJsonV310]
            }
            _ <- Helper.booleanToFuture(failMsg =  InvalidJsonContent + s" The field dependants(${postedData.dependants}) not equal the length(${postedData.dob_of_dependants.length }) of dob_of_dependants array", 400, cc.callContext) {
              postedData.dependants == postedData.dob_of_dependants.length
            }
            (customer, callContext) <- NewStyle.function.createCustomer(
              bankId,
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
              postedData.title,
              postedData.branch_id,
              postedData.name_suffix,
              cc.callContext,
            )
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getAccountsMinimalByCustomerId,
      implementedInApiVersion,
      nameOf(getAccountsMinimalByCustomerId),
      "GET",
      "/customers/CUSTOMER_ID/accounts-minimal",
      "Get Accounts Minimal for a Customer",
      s"""Get Accounts Minimal by CUSTOMER_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      accountsMinimalJson400,
      List(
        $UserNotLoggedIn,
        CustomerNotFound,
        UnknownError
      ),
      List(apiTagAccount),
      Some(List(canGetAccountsMinimalForCustomerAtAnyBank)))

    lazy val getAccountsMinimalByCustomerId : OBPEndpoint = {
      case "customers" :: customerId :: "accounts-minimal" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getCustomerByCustomerId(customerId, cc.callContext)
            (userCustomerLinks, callContext) <- getUserCustomerLinks(customerId, callContext)
            (users, callContext) <- getUsersByUserIds(userCustomerLinks.map(_.userId), callContext)
          } yield {
            val accountAccess = for (user <- users) yield Views.views.vend.privateViewsUserCanAccess(user)._2
            (JSONFactory400.createAccountsMinimalJson400(accountAccess.flatten), HttpCode.`200`(callContext))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransaction),
      Some(List(canDeleteTransactionCascade)))

    lazy val deleteTransactionCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: 
        "transactions" :: TransactionId(transactionId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagAccount),
      Some(List(canDeleteAccountCascade)))

    lazy val deleteAccountCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            result <- Future(DeleteAccountCascade.atomicDelete(bankId, accountId))
          } yield {
            if(result.getOrElse(false))
              (Full(true), HttpCode.`200`(cc))
            else
              (Full(false), HttpCode.`404`(cc))
          }
      }
    }  
    
    staticResourceDocs += ResourceDoc(
      deleteBankCascade,
      implementedInApiVersion,
      nameOf(deleteBankCascade),
      "DELETE",
      "/management/cascading/banks/BANK_ID",
      "Delete Bank Cascade",
      s"""Delete a Bank Cascade specified by BANK_ID.
         |
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagBank),
      Some(List(canDeleteBankCascade)))

    lazy val deleteBankCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future(DeleteBankCascade.atomicDelete(bankId))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagProduct),
      Some(List(canDeleteProductCascade)))

    lazy val deleteProductCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "products" :: ProductCode(code) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getProduct(bankId, code, Some(cc))
            _ <- Future(DeleteProductCascade.atomicDelete(bankId, code))
          } yield {
            (Full(true), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      deleteCustomerCascade,
      implementedInApiVersion,
      nameOf(deleteCustomerCascade),
      "DELETE",
      "/management/cascading/banks/BANK_ID/customers/CUSTOMER_ID",
      "Delete Customer Cascade",
      s"""Delete a Customer Cascade specified by CUSTOMER_ID.
         |
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        CustomerNotFoundByCustomerId,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canDeleteCustomerCascade)))

    lazy val deleteCustomerCascade : OBPEndpoint = {
      case "management" :: "cascading" :: "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId.value, Some(cc))
            _ <- Future(DeleteCustomerCascade.atomicDelete(customerId))
          } yield {
            (Full(true), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createExplicitCounterparty,
      implementedInApiVersion,
      "createCounterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty (Explicit)",
      s"""This endpoint creates an (Explicit) Counterparty for an Account.
         |
         |For an introduction to Counterparties in OBP see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagCounterparty, apiTagAccount))


    lazy val createExplicitCounterparty: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCounterpartyJSON", 400, callContext) {
              json.extract[PostCounterpartyJson400]
            }

            _ <- Helper.booleanToFuture(s"$NoViewPermission can_add_counterparty. Please use a view with that permission or add the permission to this view.", 403, cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_ADD_COUNTERPARTY)
            }

            (counterparty, callContext) <- Connector.connector.vend.checkCounterpartyExists(postJson.name, bankId.value, accountId.value, viewId.value, callContext)

            _ <- Helper.booleanToFuture(CounterpartyAlreadyExists.replace("value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
              s"COUNTERPARTY_NAME(${postJson.name}) for the BANK_ID(${bankId.value}) and ACCOUNT_ID(${accountId.value}) and VIEW_ID($viewId)"), cc=callContext){
              counterparty.isEmpty
            }
            _ <- booleanToFuture(s"$InvalidValueLength. The maximum length of `description` field is ${MappedCounterparty.mDescription.maxLen}", cc=callContext){
              postJson.description.length <= 36
            }
            _ <- Helper.booleanToFuture(s"$InvalidISOCurrencyCode Current input is: '${postJson.currency}'", cc=callContext) {
              APIUtil.isValidCurrencyISOCode(postJson.currency)
            }

            //If other_account_routing_scheme=="OBP" or other_account_secondary_routing_address=="OBP" we will check if it is a real obp bank account.
            (_, callContext)<- if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_routing_scheme.equalsIgnoreCase("OBP")){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            } else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_secondary_routing_scheme.equalsIgnoreCase("OBP")){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            }else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NUMBER")|| postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NO")) {
              for {
                bankIdOption <- Future.successful(if (postJson.other_bank_routing_address.isEmpty) None else Some(postJson.other_bank_routing_address))
                (account, callContext) <- NewStyle.function.getBankAccountByNumber(
                  bankIdOption.map(BankId(_)),
                  postJson.other_bank_routing_address,
                  callContext)
              } yield {
                (account, callContext)
              }
            }else
              Future{(Full(), Some(cc))}


            otherAccountRoutingSchemeOBPFormat = if(postJson.other_account_routing_scheme.equalsIgnoreCase("AccountNo")) "ACCOUNT_NUMBER" else StringHelpers.snakify(postJson.other_account_routing_scheme).toUpperCase


            (counterparty, callContext) <- NewStyle.function.createCounterparty(
              name=postJson.name,
              description=postJson.description,
              currency=postJson.currency,
              createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              otherAccountRoutingScheme=otherAccountRoutingSchemeOBPFormat,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme=StringHelpers.snakify(postJson.other_account_secondary_routing_scheme).toUpperCase,
              otherAccountSecondaryRoutingAddress=postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme=StringHelpers.snakify(postJson.other_bank_routing_scheme).toUpperCase,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=StringHelpers.snakify(postJson.other_branch_routing_scheme).toUpperCase,
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
      deleteExplicitCounterparty,
      implementedInApiVersion,
      nameOf(deleteExplicitCounterparty),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Delete Counterparty (Explicit)",
      s"""This endpoint deletes the Counterparty on the Account / View specified by the COUNTERPARTY_ID.
         |It also deletes any related Counterparty Metadata.
         |
         |The User calling this endpoint must have access to the View specified in the URL and that View must have the permission `can_delete_counterparty`.
         |
         |For a general introduction to Counterparties in OBP see ${Glossary.getGlossaryItemLink("Counterparties")}
         |         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount)
    )

    lazy val deleteExplicitCounterparty: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}

            _ <- Helper.booleanToFuture(s"$NoViewPermission can_delete_counterparty. Please use a view with that permission or add the permission to this view.",403, cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_DELETE_COUNTERPARTY)
            }

            (counterparty, callContext) <- NewStyle.function.deleteCounterpartyByCounterpartyId(counterpartyId, callContext)

            (counterpartyMetadata, callContext) <- NewStyle.function.deleteMetadata(bankId, accountId, counterpartyId.value, callContext)

          } yield {
            (Full(counterparty), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteCounterpartyForAnyAccount,
      implementedInApiVersion,
      nameOf(deleteCounterpartyForAnyAccount),
      "DELETE",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Delete Counterparty for any account (Explicit)",
      s"""This is a management endpoint that enables the deletion of any specified Counterparty along with any related Metadata of that Counterparty.
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankAccountNotFound,
        $BankNotFound,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount),
      Some(List(canDeleteCounterparty, canDeleteCounterpartyAtAnyBank)))

    lazy val deleteCounterpartyForAnyAccount: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), bank, account, callContext) <- SS.userBankAccount
            
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
            
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}

            (counterparty, callContext) <- NewStyle.function.deleteCounterpartyByCounterpartyId(counterpartyId, callContext)

            (counterpartyMetadata, callContext) <- NewStyle.function.deleteMetadata(bankId, accountId, counterpartyId.value, callContext)

          } yield {
            (Full(counterparty), HttpCode.`200`(callContext))
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
      s"""This is a management endpoint that allows the creation of a Counterparty on any Account.
         |
         |For an introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
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
        AccountNotFound,
        InvalidJsonFormat,
        InvalidISOCurrencyCode,
        ViewNotFound,
        CounterpartyAlreadyExists,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount),
      Some(List(canCreateCounterparty, canCreateCounterpartyAtAnyBank)))


    lazy val createCounterpartyForAnyAccount: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId):: "counterparties" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), bank, account, callContext) <- SS.userBankAccount
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
              APIUtil.isValidCurrencyISOCode(postJson.currency)
            }

            //If other_account_routing_scheme=="OBP" or other_account_secondary_routing_address=="OBP" we will check if it is a real obp bank account.
            (_, callContext)<- if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_routing_scheme.equalsIgnoreCase("OBP")){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            } else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_secondary_routing_scheme.equalsIgnoreCase("OBP")){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            }else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NUMBER")|| postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NO")) {
              for {
                bankIdOption <- Future.successful(if (postJson.other_bank_routing_address.isEmpty) None else Some(postJson.other_bank_routing_address))
                (account, callContext) <- NewStyle.function.getBankAccountByNumber(
                  bankIdOption.map(BankId(_)),
                  postJson.other_bank_routing_address,
                  callContext)
              } yield {
                (account, callContext)
              }
            }else
              Future{(Full(), Some(cc))}


            otherAccountRoutingSchemeOBPFormat = if(postJson.other_account_routing_scheme.equalsIgnoreCase("AccountNo")) "ACCOUNT_NUMBER" else StringHelpers.snakify(postJson.other_account_routing_scheme).toUpperCase


            (counterparty, callContext) <- NewStyle.function.createCounterparty(
              name=postJson.name,
              description=postJson.description,
              currency=postJson.currency,
              createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = Constant.SYSTEM_OWNER_VIEW_ID,
              otherAccountRoutingScheme=otherAccountRoutingSchemeOBPFormat,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme=StringHelpers.snakify(postJson.other_account_secondary_routing_scheme).toUpperCase,
              otherAccountSecondaryRoutingAddress=postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme=StringHelpers.snakify(postJson.other_bank_routing_scheme).toUpperCase,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=StringHelpers.snakify(postJson.other_branch_routing_scheme).toUpperCase,
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
      getExplicitCounterpartiesForAccount,
      implementedInApiVersion,
      "getExplicitCounterpartiesForAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit)",
      s"""Get the Counterparties that have been explicitly created on the specified Account / View.
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      counterpartiesJson400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        ViewNotFound,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagAccount))

    lazy val getExplicitCounterpartiesForAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"${NoViewPermission}can_get_counterparty", 403, cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_GET_COUNTERPARTY)
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
      getCounterpartiesForAnyAccount,
      implementedInApiVersion,
      nameOf(getCounterpartiesForAnyAccount),
      "GET",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties for any account (Explicit)",
      s"""This is a management endpoint that gets the Counterparties that have been explicitly created for an Account / View.
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      counterpartiesJson400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagAccount),
    Some(List(canGetCounterparties, canGetCounterpartiesAtAnyBank))
    )

    lazy val getCounterpartiesForAnyAccount : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), bank, account, callContext) <- SS.userBankAccount
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
      getExplicitCounterpartyById,
      implementedInApiVersion,
      "getExplicitCounterpartyById",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Id (Explicit)",
      s"""This endpoint returns a single Counterparty on an Account View specified by its COUNTERPARTY_ID:
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      counterpartyWithMetadataJson400,
      List($UserNotLoggedIn, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagPsd2, apiTagCounterpartyMetaData)
    )

    lazy val getExplicitCounterpartyById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, view, callContext) <- SS.userBankAccountView
            _ <- Helper.booleanToFuture(failMsg = s"${NoViewPermission}can_get_counterparty", 403, cc=callContext) {
              view.allowed_actions.exists(_ ==CAN_GET_COUNTERPARTY)
            }
            (counterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(counterpartyId, callContext)
            counterpartyMetadata <- NewStyle.function.getMetadata(bankId, accountId, counterpartyId.value, callContext)
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
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparty-names/COUNTERPARTY_NAME",
      "Get Counterparty by name for any account (Explicit) ",
      s"""This is a management endpoint that allows the retrieval of any Counterparty on an Account / View by its Name.
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagCounterparty, apiTagAccount),
      Some(List(canGetCounterpartyAtAnyBank, canGetCounterparty)))

    lazy val getCounterpartyByNameForAnyAccount: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId):: "counterparty-names" :: counterpartyName :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), bank, account, callContext) <- SS.userBankAccount

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
      getCounterpartyByIdForAnyAccount,
      implementedInApiVersion,
      nameOf(getCounterpartyByIdForAnyAccount),
      "GET",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Id for any account (Explicit)",
      s"""This is a management endpoint that gets information about any single explicitly created Counterparty on an Account / View specified by its COUNTERPARTY_ID",
         |
         |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
         |
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagCounterparty, apiTagAccount),
      Some(List(canGetCounterpartyAtAnyBank, canGetCounterparty)))

    lazy val getCounterpartyByIdForAnyAccount: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId):: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, account, callContext) <- SS.userBankAccount
            (counterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(counterpartyId, callContext)
            counterpartyMetadata <- NewStyle.function.getMetadata(bankId, accountId, counterpartyId.value, callContext)
          } yield {
            val counterpartyJson = JSONFactory400.createCounterpartyWithMetadataJson400(counterparty,counterpartyMetadata)
            (counterpartyJson, HttpCode.`200`(callContext))
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
         |${userAuthenticationMessage(true)}
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
      apiTagConsent :: apiTagPSD2AIS  :: Nil)

    lazy val addConsentUser : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents"  :: consentId :: "user-update-request" :: Nil JsonPut json -> _  => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
            _ <- Helper.booleanToFuture(ConsentUserAlreadyAdded, cc = cc.callContext) {
              Option(consent.userId).forall(_.isBlank) // checks whether userId is not populated
            }
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
         |${userAuthenticationMessage(true)}
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
      apiTagConsent :: apiTagPSD2AIS  :: Nil)

    lazy val updateConsentStatus : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents"  :: consentId :: Nil JsonPut json -> _  => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentsJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2))

    lazy val getConsents: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consents" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      getConsentInfosByBank,
      implementedInApiVersion,
      nameOf(getConsentInfosByBank),
      "GET",
      "/banks/BANK_ID/my/consent-infos",
      "Get My Consents Info At Bank",
      s"""
         |
         |This endpoint gets the Consents that the current User created at bank.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentInfosJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2))

    lazy val getConsentInfosByBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consent-infos" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      getConsentInfos,
      implementedInApiVersion,
      nameOf(getConsentInfos),
      "GET",
      "/my/consent-infos",
      "Get My Consents Info",
      s"""
         |
         |This endpoint gets the Consents that the current User created.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentInfosJsonV400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2))

    lazy val getConsentInfos: OBPEndpoint = {
      case "my" :: "consent-infos" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            consents <- Future { Consents.consentProvider.vend.getConsentsByUser(cc.userId)
              .sortBy(i => (i.creationDateTime, i.apiStandard)).reverse
            }
          } yield {
            (JSONFactory400.createConsentInfosJsonV400(consents), HttpCode.`200`(cc))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyPersonalUserAttributes,
      implementedInApiVersion,
      nameOf(getMyPersonalUserAttributes),
      "GET",
      "/my/user/attributes",
      "Get My Personal User Attributes",
      s"""Get My Personal User Attributes.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      userAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagUser)
    )

    lazy val getMyPersonalUserAttributes: OBPEndpoint = {
      case "my" ::  "user" :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (attributes, callContext) <- NewStyle.function.getPersonalUserAttributes(cc.userId, cc.callContext)
          } yield {
            (JSONFactory400.createUserAttributesJson(attributes), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      getUserWithAttributes,
      implementedInApiVersion,
      nameOf(getUserWithAttributes),
      "GET",
      "/users/USER_ID/attributes",
      "Get User with Attributes by USER_ID",
      s"""Get User Attributes for the user defined via USER_ID.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      userWithAttributesResponseJson,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagUser),
      Some(canGetUsersWithAttributes :: Nil)
    )

    lazy val getUserWithAttributes: OBPEndpoint = {
      case "users" :: userId :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userId, cc.callContext)
            (attributes, callContext) <- NewStyle.function.getUserAttributes(user.userId, callContext)
          } yield {
            (JSONFactory400.createUserWithAttributesJson(user, attributes), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createMyPersonalUserAttribute,
      implementedInApiVersion,
      nameOf(createMyPersonalUserAttribute),
      "POST",
      "/my/user/attributes",
      "Create My Personal User Attribute",
      s""" Create My Personal User Attribute
         |
         |The `type` field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      userAttributeJsonV400,
      userAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUser),
      Some(List()))

    lazy val createMyPersonalUserAttribute : OBPEndpoint = {
      case "my" ::  "user" :: "attributes" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $UserAttributeJsonV400 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              json.extract[UserAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${TransactionAttributeType.DOUBLE}(12.1234), ${UserAttributeType.STRING}(TAX_NUMBER), ${UserAttributeType.INTEGER} (123)and ${UserAttributeType.DATE_WITH_DAY}(2012-04-23)"
            userAttributeType <- NewStyle.function.tryons(failMsg, 400,  cc.callContext) {
              UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
              cc.userId,
              None,
              postedData.name,
              userAttributeType,
              postedData.value,
              true,
              cc.callContext
            )
          } yield {
            (JSONFactory400.createUserAttributeJson(userAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateMyPersonalUserAttribute,
      implementedInApiVersion,
      nameOf(updateMyPersonalUserAttribute),
      "PUT",
      "/my/user/attributes/USER_ATTRIBUTE_ID",
      "Update My Personal User Attribute",
      s"""Update My Personal User Attribute for current user by USER_ATTRIBUTE_ID
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      userAttributeJsonV400,
      userAttributeResponseJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUser),
      Some(List()))

    lazy val updateMyPersonalUserAttribute : OBPEndpoint = {
      case "my" ::  "user" :: "attributes" :: userAttributeId :: Nil JsonPut json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (attributes, callContext) <- NewStyle.function.getPersonalUserAttributes(cc.userId, cc.callContext)
            failMsg = s"$UserAttributeNotFound"
            _ <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              attributes.exists(_.userAttributeId == userAttributeId)
            }
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $UserAttributeJsonV400 ", 400,  callContext) {
              json.extract[UserAttributeJsonV400]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${UserAttributeType.DOUBLE}(12.1234), ${UserAttributeType.STRING}(TAX_NUMBER), ${UserAttributeType.INTEGER} (123)and ${UserAttributeType.DATE_WITH_DAY}(2012-04-23)"
            userAttributeType <- NewStyle.function.tryons(failMsg, 400,  callContext) {
              UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
              cc.userId,
              Some(userAttributeId),
              postedData.name,
              userAttributeType,
              postedData.value,
              true,
              callContext
            )
          } yield {
            (JSONFactory400.createUserAttributeJson(userAttribute), HttpCode.`200`(callContext))
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
        List(Extraction.decompose(ApiVersion.v3_1_0))
      ),
      List(
        UnknownError
      ),
      List(apiTagDocumentation, apiTagApi),
      Some(Nil)
    )

    lazy val getScannedApiVersions: OBPEndpoint = {
      case "api" :: "versions" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      postApiCollectionJson400,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val createMyApiCollection: OBPEndpoint = {
      case "my" :: "api-collections" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionJson400]
            }
            apiCollection <- Future{MappedApiCollectionsProvider.getApiCollectionByUserIdAndCollectionName(cc.userId, postJson.api_collection_name)}
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionAlreadyExists Current api_collection_name(${postJson.api_collection_name}) is already existing for the log in user.", cc=cc.callContext) {
              apiCollection.isEmpty
            }
            (apiCollection, callContext) <- NewStyle.function.createApiCollection(
              cc.userId,
              postJson.api_collection_name,
              postJson.is_sharable,
              postJson.description.getOrElse(""),
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
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getMyApiCollectionByName: OBPEndpoint = {
      case "my" :: "api-collections" :: "name" ::apiCollectionName :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getMyApiCollectionById: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionJson400,
      List(
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getSharableApiCollectionById: OBPEndpoint = {
      case "api-collections" :: "sharable" :: apiCollectionId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      getApiCollectionsForUser,
      implementedInApiVersion,
      nameOf(getApiCollectionsForUser),
      "GET",
      "/users/USER_ID/api-collections",
      "Get Api Collections for User",
      s"""Get Api Collections for User.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection),
      Some(canGetApiCollectionsForUser :: Nil)
    )

    lazy val getApiCollectionsForUser: OBPEndpoint = {
      case "users" :: userId :: "api-collections" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getFeaturedApiCollections: OBPEndpoint = {
      case "api-collections" :: "featured" ::  Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getMyApiCollections: OBPEndpoint = {
      case "my" :: "api-collections" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
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
      List(apiTagApiCollection)
    )

    lazy val deleteMyApiCollection : OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      postApiCollectionEndpointJson400,
      apiCollectionEndpointJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val createMyApiCollectionEndpoint: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionEndpointJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionEndpointJson400]
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidOperationId Current OPERATION_ID(${postJson.operation_id})", cc=Some(cc)) {
              getAllResourceDocs.find(_.operationId==postJson.operation_id.trim).isDefined
            }
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionByUserIdAndCollectionName(cc.userId, apiCollectionName, Some(cc))
            apiCollectionEndpoint <- Future{MappedApiCollectionEndpointsProvider.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollection.apiCollectionId, postJson.operation_id)} 
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionEndpointAlreadyExists Current OPERATION_ID(${postJson.operation_id}) is already in API_COLLECTION_NAME($apiCollectionName) ", cc=callContext) {
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
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      postApiCollectionEndpointJson400,
      apiCollectionEndpointJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val createMyApiCollectionEndpointById: OBPEndpoint = {
      case "my" :: "api-collection-ids" :: apiCollectionId :: "api-collection-endpoints" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionEndpointJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionEndpointJson400]
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidOperationId Current OPERATION_ID(${postJson.operation_id})", cc=Some(cc)) {
              getAllResourceDocs.find(_.operationId==postJson.operation_id.trim).isDefined
            }
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            apiCollectionEndpoint <- Future{MappedApiCollectionEndpointsProvider.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollection.apiCollectionId, postJson.operation_id)} 
            _ <- Helper.booleanToFuture(failMsg = s"$ApiCollectionEndpointAlreadyExists Current OPERATION_ID(${postJson.operation_id}) is already in API_COLLECTION_ID($apiCollectionId) ", cc=callContext) {
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
         |${userAuthenticationMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )
    
    lazy val getMyApiCollectionEndpoint: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints" :: operationId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(false)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointsJson400,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getApiCollectionEndpoints: OBPEndpoint = {
      case "api-collections" :: apiCollectionId :: "api-collection-endpoints" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointsJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getMyApiCollectionEndpoints: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints":: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionEndpointsJson400,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val getMyApiCollectionEndpointsById: OBPEndpoint = {
      case "my" :: "api-collection-ids" :: apiCollectionId :: "api-collection-endpoints":: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |Delete Api Collection Endpoint By OPERATION_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val deleteMyApiCollectionEndpoint : OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionName :: "api-collection-endpoints" :: operationId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      deleteMyApiCollectionEndpointByOperationId,
      implementedInApiVersion,
      nameOf(deleteMyApiCollectionEndpointByOperationId),
      "DELETE",
      "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoints/OPERATION_ID",
      "Delete My Api Collection Endpoint By Id",
      s"""${Glossary.getGlossaryItem("API Collections")}
         |
         |Delete Api Collection Endpoint By OPERATION_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val deleteMyApiCollectionEndpointByOperationId : OBPEndpoint = {
      case "my" :: "api-collection-ids" :: apiCollectionId :: "api-collection-endpoints" :: operationId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      deleteMyApiCollectionEndpointById,
      implementedInApiVersion,
      nameOf(deleteMyApiCollectionEndpointById),
      "DELETE",
      "/my/api-collection-ids/API_COLLECTION_ID/api-collection-endpoint-ids/API_COLLECTION_ENDPOINT_ID",
      "Delete My Api Collection Endpoint By Id",
      s"""${Glossary.getGlossaryItem("API Collections")}
         |Delete Api Collection Endpoint
         |Delete Api Collection Endpoint By Id
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      Full(true),
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val deleteMyApiCollectionEndpointById : OBPEndpoint = {
      case "my" :: "api-collection-ids" :: apiCollectionId :: "api-collection-endpoint-ids" :: apiCollectionEndpointId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc) )
            (apiCollectionEndpoint, callContext) <- NewStyle.function.getApiCollectionEndpointById(apiCollectionEndpointId, callContext)
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
         |Introduction:
         |${Glossary.getGlossaryItemSimple("JSON Schema Validation")}
         |
         |To use this endpoint, please supply a valid json-schema in the request body.
         |
         |Note: It might take a few minutes for the newly created JSON Schema to take effect!
         |""",
      postOrPutJsonSchemaV400,
      responseJsonSchema,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation),
      Some(List(canCreateJsonSchemaValidation)))


    lazy val createJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonPost _ -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val httpBody: String = cc.httpBody.getOrElse("")
          for {
            (Full(u), callContext) <- SS.user

            schemaErrors: util.Set[ValidationMessage] = JsonSchemaUtil.validateSchema(httpBody)
            _ <- Helper.booleanToFuture(failMsg = s"$JsonSchemaIllegal${StringUtils.join(schemaErrors, "; ")}", cc=callContext) {
              CommonUtil.Collections.isEmpty(schemaErrors)
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
         |Introduction:
         |${Glossary.getGlossaryItemSimple("JSON Schema Validation")}
         |
         |To use this endpoint, please supply a valid json-schema in the request body.
         |
         |""",
      postOrPutJsonSchemaV400,
      responseJsonSchema,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagJsonSchemaValidation),
      Some(List(canUpdateJsonSchemaValidation)))


    lazy val updateJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonPut _ -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val httpBody: String = cc.httpBody.getOrElse("")
          for {
            (Full(u), callContext) <- SS.user

            schemaErrors = JsonSchemaUtil.validateSchema(httpBody)
            _ <- Helper.booleanToFuture(failMsg = s"$JsonSchemaIllegal${StringUtils.join(schemaErrors, "; ")}", cc=callContext) {
              CommonUtil.Collections.isEmpty(schemaErrors)
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
      List(apiTagJsonSchemaValidation),
      Some(List(canDeleteJsonSchemaValidation)))


    lazy val deleteJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagJsonSchemaValidation),
      Some(List(canGetJsonSchemaValidation)))

    lazy val getJsonSchemaValidation: OBPEndpoint = {
      case "management" :: "json-schema-validations" :: operationId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagJsonSchemaValidation),
      Some(List(canGetJsonSchemaValidation)))


    lazy val getAllJsonSchemaValidations: OBPEndpoint = {
      case ("management" | "endpoints") :: "json-schema-validations" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagJsonSchemaValidation),
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
      List(apiTagAuthenticationTypeValidation),
      Some(List(canCreateAuthenticationTypeValidation)))


    lazy val createAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonPost jArray -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagAuthenticationTypeValidation),
      Some(List(canUpdateAuthenticationTypeValidation)))


    lazy val updateAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonPut jArray -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagAuthenticationTypeValidation),
      Some(List(canDeleteAuthenticationValidation)))


    lazy val deleteAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagAuthenticationTypeValidation),
      Some(List(canGetAuthenticationTypeValidation)))


    lazy val getAuthenticationTypeValidation: OBPEndpoint = {
      case "management" :: "authentication-type-validations" :: operationId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagAuthenticationTypeValidation),
      Some(List(canGetAuthenticationTypeValidation)))


    lazy val getAllAuthenticationTypeValidations: OBPEndpoint = {
      case ("management" | "endpoints") :: "authentication-type-validations" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagAuthenticationTypeValidation),
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
      jsonScalaConnectorMethod.copy(connectorMethodId=None),
      jsonScalaConnectorMethod,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagConnectorMethod),
      Some(List(canCreateConnectorMethod)))

    lazy val createConnectorMethod: OBPEndpoint = {
      case "management" :: "connector-methods" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            jsonConnectorMethod <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonConnectorMethod", 400, cc.callContext) {
              json.extract[JsonConnectorMethod]
            }
            
            (isExists, callContext) <- NewStyle.function.connectorMethodNameExists(jsonConnectorMethod.methodName, Some(cc))
            _ <- Helper.booleanToFuture(failMsg = s"$ConnectorMethodAlreadyExists Please use a different method_name(${jsonConnectorMethod.methodName})", cc=callContext) {
              (!isExists)
            }
            connectorMethod = InternalConnector.createFunction(jsonConnectorMethod.methodName, jsonConnectorMethod.decodedMethodBody, jsonConnectorMethod.programmingLang)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            _ =  Validation.validateDependency(connectorMethod.head)
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
      jsonScalaConnectorMethodMethodBody,
      jsonScalaConnectorMethod,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagConnectorMethod),
      Some(List(canUpdateConnectorMethod)))

    lazy val updateConnectorMethod: OBPEndpoint = {
      case "management" :: "connector-methods" :: connectorMethodId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            connectorMethodBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonConnectorMethod", 400, cc.callContext) {
              json.extract[JsonConnectorMethodMethodBody]
            }

            (cm, callContext) <- NewStyle.function.getJsonConnectorMethodById(connectorMethodId, cc.callContext)

            connectorMethod = InternalConnector.createFunction(cm.methodName, connectorMethodBody.decodedMethodBody, connectorMethodBody.programmingLang)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            _ =  Validation.validateDependency(connectorMethod.head)
            (connectorMethod, callContext) <- NewStyle.function.updateJsonConnectorMethod(connectorMethodId, connectorMethodBody.methodBody, connectorMethodBody.programmingLang, callContext)
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
      jsonScalaConnectorMethod,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConnectorMethod),
      Some(List(canGetConnectorMethod)))

    lazy val getConnectorMethod: OBPEndpoint = {
      case "management" :: "connector-methods" :: connectorMethodId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      ListResult("connectors_methods", jsonScalaConnectorMethod::Nil),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConnectorMethod),
      Some(List(canGetAllConnectorMethods)))

    lazy val getAllConnectorMethods: OBPEndpoint = {
      case "management" :: "connector-methods" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagDynamicResourceDoc),
      Some(List(canCreateDynamicResourceDoc)))

    lazy val createDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
                .validateDependency()
            } catch {
              case e: JsonResponseException =>
                throw e
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            (isExists, callContext) <- NewStyle.function.isJsonDynamicResourceDocExists(None, jsonDynamicResourceDoc.requestVerb, jsonDynamicResourceDoc.requestUrl, Some(cc))
            _ <- Helper.booleanToFuture(failMsg = s"$DynamicResourceDocAlreadyExists The combination of request_url(${jsonDynamicResourceDoc.requestUrl}) and request_verb(${jsonDynamicResourceDoc.requestVerb}) must be unique", cc=callContext) {
              (!isExists)
            }

            (dynamicResourceDoc, callContext) <- NewStyle.function.createJsonDynamicResourceDoc(None, jsonDynamicResourceDoc, callContext)
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
      List(apiTagDynamicResourceDoc),
      Some(List(canUpdateDynamicResourceDoc)))

    lazy val updateDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
                .validateDependency()
            } catch {
              case e: JsonResponseException =>
                throw e
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            (_, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(None, dynamicResourceDocId, cc.callContext)

            (dynamicResourceDoc, callContext) <- NewStyle.function.updateJsonDynamicResourceDoc(None, dynamicResourceDocBody.copy(dynamicResourceDocId = Some(dynamicResourceDocId)), callContext)
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
      List(apiTagDynamicResourceDoc),
      Some(List(canDeleteDynamicResourceDoc)))

    lazy val deleteDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(None, dynamicResourceDocId, cc.callContext)
            (dynamicResourceDoc, callContext) <- NewStyle.function.deleteJsonDynamicResourceDocById(None, dynamicResourceDocId, callContext)
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
      List(apiTagDynamicResourceDoc),
      Some(List(canGetDynamicResourceDoc)))

    lazy val getDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicResourceDoc, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(None, dynamicResourceDocId, cc.callContext)
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
      List(apiTagDynamicResourceDoc),
      Some(List(canGetAllDynamicResourceDocs)))

    lazy val getAllDynamicResourceDocs: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicResourceDocs, callContext) <- NewStyle.function.getJsonDynamicResourceDocs(None, cc.callContext)
          } yield {
            (ListResult("dynamic-resource-docs", dynamicResourceDocs), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createBankLevelDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(createBankLevelDynamicResourceDoc),
      "POST",
      "/management/banks/BANK_ID/dynamic-resource-docs",
      "Create Bank Level Dynamic Resource Doc",
      s"""Create a Bank Level Dynamic Resource Doc.
         |
         |The connector_method_body is URL-encoded format String
         |""",
      jsonDynamicResourceDoc.copy(dynamicResourceDocId=None),
      jsonDynamicResourceDoc,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc),
      Some(List(canCreateBankLevelDynamicResourceDoc))) 

    lazy val createBankLevelDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-resource-docs" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
                .validateDependency()
            } catch {
              case e: JsonResponseException =>
                throw e
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }
            _ = try {
              CompiledObjects(jsonDynamicResourceDoc.exampleRequestBody, jsonDynamicResourceDoc.successResponseBody, jsonDynamicResourceDoc.methodBody)
            } catch {
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            (isExists, callContext) <- NewStyle.function.isJsonDynamicResourceDocExists(Some(bankId), jsonDynamicResourceDoc.requestVerb, jsonDynamicResourceDoc.requestUrl, Some(cc))
            _ <- Helper.booleanToFuture(failMsg = s"$DynamicResourceDocAlreadyExists The combination of request_url(${jsonDynamicResourceDoc.requestUrl}) and request_verb(${jsonDynamicResourceDoc.requestVerb}) must be unique", cc=callContext) {
              (!isExists)
            }

            (dynamicResourceDoc, callContext) <- NewStyle.function.createJsonDynamicResourceDoc(Some(bankId), jsonDynamicResourceDoc, callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateBankLevelDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(updateBankLevelDynamicResourceDoc),
      "PUT",
      "/management/banks/BANK_ID/dynamic-resource-docs/DYNAMIC-RESOURCE-DOC-ID",
      "Update Bank Level Dynamic Resource Doc",
      s"""Update a Bank Level Dynamic Resource Doc.
         |
         |The connector_method_body is URL-encoded format String
         |""",
      jsonDynamicResourceDoc.copy(dynamicResourceDocId = None),
      jsonDynamicResourceDoc,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc),
      Some(List(canUpdateBankLevelDynamicResourceDoc)))

    lazy val updateBankLevelDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
              CompiledObjects(dynamicResourceDocBody.exampleRequestBody, dynamicResourceDocBody.successResponseBody, dynamicResourceDocBody.methodBody)
                .validateDependency()
            } catch {
              case e: JsonResponseException =>
                throw e
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            _ = try {
              CompiledObjects(dynamicResourceDocBody.exampleRequestBody, dynamicResourceDocBody.successResponseBody, jsonDynamicResourceDoc.methodBody)
            } catch {
              case e: Exception =>
                val jsonResponse = createErrorJsonResponse(s"$DynamicCodeCompileFail ${e.getMessage}", 400, cc.correlationId)
                throw JsonResponseException(jsonResponse)
            }

            (_, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(Some(bankId), dynamicResourceDocId, cc.callContext)

            (dynamicResourceDoc, callContext) <- NewStyle.function.updateJsonDynamicResourceDoc(Some(bankId), dynamicResourceDocBody.copy(dynamicResourceDocId = Some(dynamicResourceDocId)), callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteBankLevelDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(deleteBankLevelDynamicResourceDoc),
      "DELETE",
      "/management/banks/BANK_ID/dynamic-resource-docs/DYNAMIC-RESOURCE-DOC-ID",
      "Delete Bank Level Dynamic Resource Doc",
      s"""Delete a Bank Level Dynamic Resource Doc.
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
      List(apiTagDynamicResourceDoc),
      Some(List(canDeleteBankLevelDynamicResourceDoc)))

    lazy val deleteBankLevelDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(Some(bankId), dynamicResourceDocId, cc.callContext)
            (dynamicResourceDoc, callContext) <- NewStyle.function.deleteJsonDynamicResourceDocById(Some(bankId), dynamicResourceDocId, callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelDynamicResourceDoc,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicResourceDoc),
      "GET",
      "/management/banks/BANK_ID/dynamic-resource-docs/DYNAMIC-RESOURCE-DOC-ID",
      "Get Bank Level Dynamic Resource Doc by Id",
      s"""Get a Bank Level Dynamic Resource Doc by DYNAMIC-RESOURCE-DOC-ID.
         |
         |""",
      EmptyBody,
      jsonDynamicResourceDoc,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc),
      Some(List(canGetBankLevelDynamicResourceDoc)))

    lazy val getBankLevelDynamicResourceDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-resource-docs" :: dynamicResourceDocId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicResourceDoc, callContext) <- NewStyle.function.getJsonDynamicResourceDocById(Some(bankId), dynamicResourceDocId, cc.callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllBankLevelDynamicResourceDocs,
      implementedInApiVersion,
      nameOf(getAllBankLevelDynamicResourceDocs),
      "GET",
      "/management/banks/BANK_ID/dynamic-resource-docs",
      "Get all Bank Level Dynamic Resource Docs",
      s"""Get all Bank Level Dynamic Resource Docs.
         |
         |""",
      EmptyBody,
      ListResult("dynamic-resource-docs", jsonDynamicResourceDoc::Nil),
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc),
      Some(List(canGetAllBankLevelDynamicResourceDocs)))

    lazy val getAllBankLevelDynamicResourceDocs: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-resource-docs" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicResourceDocs, callContext) <- NewStyle.function.getJsonDynamicResourceDocs(Some(bankId), cc.callContext)
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
      jsonCodeTemplateJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicResourceDoc),
      None)

    lazy val buildDynamicEndpointTemplate: OBPEndpoint = {
      case "management" :: "dynamic-resource-docs" :: "endpoint-code" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
            (JsonCodeTemplateJson(URLEncoder.encode(code, "UTF-8")), HttpCode.`201`(cc.callContext))
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
      List(apiTagDynamicMessageDoc),
      Some(List(canCreateDynamicMessageDoc)))

    lazy val createDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicMessageDoc <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicMessageDoc", 400, cc.callContext) {
              json.extract[JsonDynamicMessageDoc]
            }
            (dynamicMessageDocExisted, callContext) <- NewStyle.function.isJsonDynamicMessageDocExists(None, dynamicMessageDoc.process, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$DynamicMessageDocAlreadyExists The json body process(${dynamicMessageDoc.process}) already exists", cc=callContext) {
              (!dynamicMessageDocExisted)
            }
            connectorMethod = DynamicConnector.createFunction(dynamicMessageDoc.programmingLang, dynamicMessageDoc.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            _ =  Validation.validateDependency(connectorMethod.orNull)
            (dynamicMessageDoc, callContext) <- NewStyle.function.createJsonDynamicMessageDoc(None, dynamicMessageDoc, callContext)
          } yield {
            (dynamicMessageDoc, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createBankLevelDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(createBankLevelDynamicMessageDoc),
      "POST",
      "/management/banks/BANK_ID/dynamic-message-docs",
      "Create Bank Level Dynamic Message Doc",
      s"""Create a Bank Level Dynamic Message Doc.
         |""",
      jsonDynamicMessageDoc.copy(dynamicMessageDocId=None),
      jsonDynamicMessageDoc,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc),
      Some(List(canCreateBankLevelDynamicMessageDoc)))

    lazy val createBankLevelDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId ::"dynamic-message-docs" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicMessageDoc <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicMessageDoc", 400, cc.callContext) {
              json.extract[JsonDynamicMessageDoc]
            }
            (dynamicMessageDocExisted, callContext) <- NewStyle.function.isJsonDynamicMessageDocExists(Some(bankId), dynamicMessageDoc.process, cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$DynamicMessageDocAlreadyExists The json body process(${dynamicMessageDoc.process}) already exists", cc=callContext) {
              (!dynamicMessageDocExisted)
            }
            connectorMethod = DynamicConnector.createFunction(dynamicMessageDoc.programmingLang, dynamicMessageDoc.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=callContext) {
              connectorMethod.isDefined
            }
            _ =  Validation.validateDependency(connectorMethod.orNull)
            (dynamicMessageDoc, callContext) <- NewStyle.function.createJsonDynamicMessageDoc(Some(bankId), dynamicMessageDoc, callContext)
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
      List(apiTagDynamicMessageDoc),
      Some(List(canUpdateDynamicMessageDoc)))

    lazy val updateDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicMessageDocBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicMessageDoc", 400, cc.callContext) {
              json.extract[JsonDynamicMessageDoc]
            }
            connectorMethod = DynamicConnector.createFunction(dynamicMessageDocBody.programmingLang, dynamicMessageDocBody.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=cc.callContext) {
              connectorMethod.isDefined
            }
            _ =  Validation.validateDependency(connectorMethod.orNull)
            (_, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(None, dynamicMessageDocId, cc.callContext)
            (dynamicMessageDoc, callContext) <- NewStyle.function.updateJsonDynamicMessageDoc(None, dynamicMessageDocBody.copy(dynamicMessageDocId=Some(dynamicMessageDocId)), callContext)
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
      List(apiTagDynamicMessageDoc),
      Some(List(canGetDynamicMessageDoc)))

    lazy val getDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicMessageDoc, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(None, dynamicMessageDocId, cc.callContext)
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
      List(apiTagDynamicMessageDoc),
      Some(List(canGetAllDynamicMessageDocs)))

    lazy val getAllDynamicMessageDocs: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicMessageDocs, callContext) <- NewStyle.function.getJsonDynamicMessageDocs(None, cc.callContext)
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
      List(apiTagDynamicMessageDoc),
      Some(List(canDeleteDynamicMessageDoc)))

    lazy val deleteDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(None, dynamicMessageDocId, cc.callContext)
            (dynamicResourceDoc, callContext) <- NewStyle.function.deleteJsonDynamicMessageDocById(None, dynamicMessageDocId, callContext)
          } yield {
            (dynamicResourceDoc, HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateBankLevelDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(updateBankLevelDynamicMessageDoc),
      "PUT",
      "/management/banks/BANK_ID/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID",
      "Update Bank Level Dynamic Message Doc",
      s"""Update a Bank Level Dynamic Message Doc.
         |""",
      jsonDynamicMessageDoc.copy(dynamicMessageDocId=None),
      jsonDynamicMessageDoc,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc),
      Some(List(canUpdateDynamicMessageDoc)))

    lazy val updateBankLevelDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId::"dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicMessageDocBody <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $JsonDynamicMessageDoc", 400, cc.callContext) {
              json.extract[JsonDynamicMessageDoc]
            }
            connectorMethod = DynamicConnector.createFunction(dynamicMessageDocBody.programmingLang, dynamicMessageDocBody.decodedMethodBody)
            errorMsg = if(connectorMethod.isEmpty) s"$ConnectorMethodBodyCompileFail ${connectorMethod.asInstanceOf[Failure].msg}" else ""
            _ <- Helper.booleanToFuture(failMsg = errorMsg, cc=cc.callContext) {
              connectorMethod.isDefined
            }
            _ =  Validation.validateDependency(connectorMethod.orNull)
            (_, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(Some(bankId), dynamicMessageDocId, cc.callContext)
            (dynamicMessageDoc, callContext) <- NewStyle.function.updateJsonDynamicMessageDoc(Some(bankId), dynamicMessageDocBody.copy(dynamicMessageDocId=Some(dynamicMessageDocId)), callContext)
          } yield {
            (dynamicMessageDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getBankLevelDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicMessageDoc),
      "GET",
      "/management/banks/BANK_ID/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID",
      "Get Bank Level Dynamic Message Doc",
      s"""Get a Bank Level Dynamic Message Doc by DYNAMIC_MESSAGE_DOC_ID.
         |
         |""",
      EmptyBody,
      jsonDynamicMessageDoc,
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc),
      Some(List(canGetBankLevelDynamicMessageDoc)))

    lazy val getBankLevelDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicMessageDoc, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(None, dynamicMessageDocId, cc.callContext)
          } yield {
            (dynamicMessageDoc, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAllBankLevelDynamicMessageDocs,
      implementedInApiVersion,
      nameOf(getAllBankLevelDynamicMessageDocs),
      "GET",
      "/management/banks/BANK_ID/dynamic-message-docs",
      "Get all Bank Level Dynamic Message Docs",
      s"""Get all Bank Level Dynamic Message Docs.
         |
         |""",
      EmptyBody,
      ListResult("dynamic-message-docs", jsonDynamicMessageDoc::Nil),
      List(
        $BankNotFound,
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicMessageDoc),
      Some(List(canGetAllDynamicMessageDocs)))

    lazy val getAllBankLevelDynamicMessageDocs: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-message-docs" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (dynamicMessageDocs, callContext) <- NewStyle.function.getJsonDynamicMessageDocs(Some(bankId), cc.callContext)
          } yield {
            (ListResult("dynamic-message-docs", dynamicMessageDocs), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteBankLevelDynamicMessageDoc,
      implementedInApiVersion,
      nameOf(deleteBankLevelDynamicMessageDoc),
      "DELETE",
      "/management/banks/BANK_ID/dynamic-message-docs/DYNAMIC_MESSAGE_DOC_ID",
      "Delete Bank Level Dynamic Message Doc",
      s"""Delete a Bank Level Dynamic Message Doc.
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
      List(apiTagDynamicMessageDoc),
      Some(List(canDeleteBankLevelDynamicMessageDoc)))

    lazy val deleteBankLevelDynamicMessageDoc: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "dynamic-message-docs" :: dynamicMessageDocId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getJsonDynamicMessageDocById(Some(bankId), dynamicMessageDocId, cc.callContext)
            (dynamicResourceDoc, callContext) <- NewStyle.function.deleteJsonDynamicMessageDocById(Some(bankId), dynamicMessageDocId, callContext)
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
      List(apiTagEndpointMapping),
      Some(List(canCreateEndpointMapping)))

    lazy val createEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canUpdateEndpointMapping)))

    lazy val updateEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: endpointMappingId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canGetEndpointMapping)))

    lazy val getEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: endpointMappingId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canGetAllEndpointMappings)))

    lazy val getAllEndpointMappings: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canDeleteEndpointMapping)))

    lazy val deleteEndpointMapping: OBPEndpoint = {
      case "management" :: "endpoint-mappings" :: endpointMappingId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canCreateBankLevelEndpointMapping, canCreateEndpointMapping)))

    lazy val createBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canUpdateBankLevelEndpointMapping, canUpdateEndpointMapping)))

    lazy val updateBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: endpointMappingId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canGetBankLevelEndpointMapping, canGetEndpointMapping)))

    lazy val getBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: endpointMappingId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canGetAllBankLevelEndpointMappings, canGetAllEndpointMappings)))

    lazy val getAllBankLevelEndpointMappings: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagEndpointMapping),
      Some(List(canDeleteBankLevelEndpointMapping, canDeleteEndpointMapping)))

    lazy val deleteBankLevelEndpointMapping: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoint-mappings" :: endpointMappingId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM)
    )
    
    lazy val updateAtmSupportedCurrencies : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "supported-currencies" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM)
    )
    
    lazy val updateAtmSupportedLanguages : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "supported-languages" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM)
    )
    
    lazy val updateAtmAccessibilityFeatures : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "accessibility-features" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM)
    )
    
    lazy val updateAtmServices : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "services" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM)
    )
    
    lazy val updateAtmNotes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "notes" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM)
    )
    
    lazy val updateAtmLocationCategories : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "location-categories" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM),
      Some(List(canCreateAtm,canCreateAtmAtAnyBank))
    )
    lazy val createAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagATM),
      Some(List(canUpdateAtm, canUpdateAtmAtAnyBank))
    )
    lazy val updateAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      deleteAtm,
      implementedInApiVersion,
      nameOf(deleteAtm),
      "DELETE",
      "/banks/BANK_ID/atms/ATM_ID",
      "Delete ATM",
      s"""Delete ATM.""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canDeleteAtmAtAnyBank, canDeleteAtm))
    )
    lazy val deleteAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (deleted, callContext) <- NewStyle.function.deleteAtm(atm, callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
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
      s"""Returns information about ATMs for a single bank specified by BANK_ID including:
         |
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |
         |Pagination:
         |
         |By default, 100 records are returned.
         |
         |You can use the url query parameters *limit* and *offset* for pagination
         |
         |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmsJsonV400,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagATM)
    )
    lazy val getAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val limit = ObpS.param("limit")
          val offset = ObpS.param("offset")
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
            (atms, callContext) <- NewStyle.function.getAtmsByBankId(bankId, offset, limit, cc.callContext)
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
         |${userAuthenticationMessage(!getAtmsIsPublic)}
         |""".stripMargin,
      EmptyBody,
      atmJsonV400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM)
    )
    lazy val getAtm : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      s"""Create System Level Endpoint Tag
         |
         |Note: Resource Docs are cached, TTL is ${CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL} seconds
         |
         |""".stripMargin,
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi),
      Some(List(canCreateSystemLevelEndpointTag)))
    lazy val createSystemLevelEndpointTag: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      s"""Update System Level Endpoint Tag, you can only update the tag_name here, operation_id can not be updated.
         |
         |Note: Resource Docs are cached, TTL is ${CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL} seconds
         |
         |""".stripMargin,
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        EndpointTagNotFoundByEndpointTagId,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi),
      Some(List(canUpdateSystemLevelEndpointTag)))
    lazy val updateSystemLevelEndpointTag: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagApi),
      Some(List(canGetSystemLevelEndpointTag)))
    lazy val getSystemLevelEndpointTags: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" ::  Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagApi),
      Some(List(canDeleteSystemLevelEndpointTag)))
    lazy val deleteSystemLevelEndpointTag: OBPEndpoint = {
      case "management" :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      s"""Create Bank Level Endpoint Tag
         |
         |Note: Resource Docs are cached, TTL is ${CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL} seconds
         |
         |
         |""".stripMargin,
      endpointTagJson400,
      bankLevelEndpointTagResponseJson400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagApi),
      Some(List(canCreateBankLevelEndpointTag)))
    lazy val createBankLevelEndpointTag: OBPEndpoint = {
      case "management" :: "banks" :: bankId :: "endpoints" :: operationId :: "tags" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      s"""Update Endpoint Tag, you can only update the tag_name here, operation_id can not be updated.
         |
         |Note: Resource Docs are cached, TTL is ${CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL} seconds
         |
         |""".stripMargin,
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
      List(apiTagApi),
      Some(List(canUpdateBankLevelEndpointTag)))
    lazy val updateBankLevelEndpointTag: OBPEndpoint = {
      case "management":: "banks" :: bankId :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagApi),
      Some(List(canGetBankLevelEndpointTag)))
    lazy val getBankLevelEndpointTags: OBPEndpoint = {
      case "management":: "banks" :: bankId :: "endpoints" :: operationId :: "tags" ::  Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagApi),
      Some(List(canDeleteBankLevelEndpointTag)))
    lazy val deleteBankLevelEndpointTag: OBPEndpoint = {
      case "management":: "banks" :: bankId :: "endpoints" :: operationId :: "tags" :: endpointTagId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
      List(apiTagUser)
    )
    lazy val getMySpaces: OBPEndpoint = {
      case "my" :: "spaces" ::  Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
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
         |URL params example: /banks/some-bank-id/products?&limit=50&offset=1
         |
         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productsJsonV400,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagProduct)
    )
    lazy val getProducts : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet req => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getProductsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            params = req.params.toList.map(kv => GetProductsParam(kv._1, kv._2))
            (products, callContext) <-NewStyle.function.getProducts(bankId, params, callContext)
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
         |${userAuthenticationMessage(true) }
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
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )
    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutProductJsonV400 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutProductJsonV400]
            }
            (parentProduct, callContext) <- product.parent_product_code.trim.nonEmpty match {
              case false =>
                Future((Empty, callContext))
              case true =>
                NewStyle.function.getProduct(bankId, ProductCode(product.parent_product_code), callContext).map(product => (Full(product._1),product._2))
            }
            (success, callContext) <- NewStyle.function.createOrUpdateProduct(
              bankId = bankId.value,
              code = productCode.value,
              parentProductCode = parentProduct.map(_.code.value).toOption,
              name = product.name,
              category = null,
              family = null,
              superFamily = null,
              moreInfoUrl = product.more_info_url,
              termsAndConditionsUrl = product.terms_and_conditions_url,
              details = null,
              description = product.description,
              metaLicenceId = product.meta.license.id,
              metaLicenceName = product.meta.license.name,
              callContext
            )
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
         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV400,
      List(
        UserNotLoggedIn,
        $BankNotFound,
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
            (product, callContext)<- NewStyle.function.getProduct(bankId, productCode, callContext)
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, productCode, callContext)
            
            (productFees, callContext) <- NewStyle.function.getProductFeesFromProvider(bankId, productCode, callContext)
            
          } yield {
            (JSONFactory400.createProductJson(product, productAttributes, productFees), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      createCustomerMessage,
      implementedInApiVersion,
      nameOf(createCustomerMessage),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/messages",
      "Create Customer Message",
      s"""
         |Create a message for the customer specified by CUSTOMER_ID 
         |${userAuthenticationMessage(true)}
         | 
         |""".stripMargin,
      createMessageJsonV400,
      successMessage,
      List(
        UserNotLoggedIn,
        $BankNotFound
      ),
      List(apiTagMessage, apiTagCustomer, apiTagPerson),
      Some(List(canCreateCustomerMessage))
    )

    lazy val createCustomerMessage : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "messages" :: Nil JsonPost json -> _ => {
        cc =>{
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user 
            failMsg = s"$InvalidJsonFormat The Json body should be the $CreateMessageJsonV400 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateMessageJsonV400]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (_, callContext)<- NewStyle.function.createCustomerMessage(
              customer,
              bankId,
              postedData.transport,
              postedData.message,
              postedData.from_department,
              postedData.from_person,
              callContext
            )
          } yield {
            (successMessage, HttpCode.`201`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
     getCustomerMessages,
     implementedInApiVersion,
     nameOf(getCustomerMessages),
     "GET",
     "/banks/BANK_ID/customers/CUSTOMER_ID/messages",
     "Get Customer Messages for a Customer",
     s"""Get messages for the customer specified by CUSTOMER_ID
         ${userAuthenticationMessage(true)}
        """,
     EmptyBody,
     customerMessagesJsonV400,
     List(
       UserNotLoggedIn,
       $BankNotFound,
       UnknownError),
     List(apiTagMessage, apiTagCustomer),
     Some(List(canGetCustomerMessages)) 
    )

    lazy val getCustomerMessages: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "messages" :: Nil JsonGet _ => {
        cc =>{
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user 
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)   
            (messages, callContext) <- NewStyle.function.getCustomerMessages(customer, bankId, callContext)   
          } yield {
            (JSONFactory400.createCustomerMessagesJson(messages), HttpCode.`200`(callContext))
          }
        }
      }
    }

    val generalWebHookInfo = s"""
      |Webhooks are used to call external web services when certain events happen.
      |
      |For instance, a webhook can be used to notify an external service if a transaction is created on an account.
      |
      |"""


    val accountNotificationWebhookInfo = s"""
                         |When an account notification webhook fires it will POST to the URL you specify during the creation of the webhook.
                         |
                         |Inside the payload you will find account_id and transaction_id and also user_ids and customer_ids of the Users / Customers linked to the Account.
                         |                     |
                         |The webhook will POST the following structure to your service:
                         |
                         |{
                         |  "event_name": "OnCreateTransaction",
                         |  "event_id": "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                         |  "bank_id": "gh.29.uk",
                         |  "account_id": "8ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                         |  "transaction_id": "7ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                         |  "related_entities": [
                         |    {
                         |      "user_id": "8ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                         |      "customer_ids": ["3ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"]
                         |    }
                         |  ]
                         |}
                         |
                         |Thus, your service should accept the above POST body structure.
                         |
                         |In this way, your web service can be informed about an event on an account and act accordingly.
                         |
                         |Further information about the account, transaction or related entities can then be retrieved using the standard REST APIs.
                         |"""




    staticResourceDocs += ResourceDoc(
      createSystemAccountNotificationWebhook,
      implementedInApiVersion,
      nameOf(createSystemAccountNotificationWebhook),
      "POST",
      "/web-hooks/account/notifications/on-create-transaction",
      "Create system level Account Notification Webhook",
      s"""
         |Create a notification Webhook that will fire for all accounts on the system.
         |
         |$generalWebHookInfo
         |
         |$accountNotificationWebhookInfo
         |
         |""",
      accountNotificationWebhookPostJson,
      systemAccountNotificationWebhookJson,
      List(UnknownError),
      apiTagWebhook :: apiTagBank  :: Nil,
      Some(List(canCreateSystemAccountNotificationWebhook))
    )

    lazy val createSystemAccountNotificationWebhook : OBPEndpoint = {
      case "web-hooks" ::"account" ::"notifications" ::"on-create-transaction" :: Nil JsonPost json -> _  => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountNotificationWebhookPostJson "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountNotificationWebhookPostJson]
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidHttpMethod Only Support `POST` currently. Current value is (${postJson.http_method})", cc=callContext) {
              postJson.http_method.equals("POST")
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidHttpProtocol Only Support `HTTP/1.1` currently. Current value is (${postJson.http_protocol})", cc=callContext) {
              postJson.http_protocol.equals("HTTP/1.1")
            }
            onCreateTransaction = ApiTrigger.onCreateTransaction.toString()
            wh <- SystemAccountNotificationWebhookTrait.systemAccountNotificationWebhook.vend.createSystemAccountNotificationWebhookFuture(
              userId = u.userId,
              triggerName= onCreateTransaction,
              url = postJson.url,
              httpMethod = postJson.http_method,
              httpProtocol= postJson.http_protocol,
            ) map {
              unboxFullOrFail(_, callContext, CreateWebhookError)
            }
          } yield {
            (createSystemLevelAccountWebhookJsonV400(wh), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createBankAccountNotificationWebhook,
      implementedInApiVersion,
      nameOf(createBankAccountNotificationWebhook),
      "POST",
      "/banks/BANK_ID/web-hooks/account/notifications/on-create-transaction",
      "Create bank level Account Notification Webhook",
      s"""Create a notification Webhook that will fire for all accounts on the specified Bank.
         |
         |$generalWebHookInfo
         |
         |$accountNotificationWebhookInfo
         |
         |""",
      accountNotificationWebhookPostJson,
      bankAccountNotificationWebhookJson,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      apiTagWebhook :: apiTagBank  :: Nil,
      Some(List(canCreateAccountNotificationWebhookAtOneBank))
    )

    lazy val createBankAccountNotificationWebhook : OBPEndpoint = {
      case  "banks" :: BankId(bankId) :: "web-hooks" ::"account" ::"notifications" ::"on-create-transaction" :: Nil JsonPost json -> _  => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountNotificationWebhookPostJson "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountNotificationWebhookPostJson]
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidHttpMethod Only Support `POST` currently. Current value is (${postJson.http_method})", cc=callContext) {
              postJson.http_method.equals("POST")
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidHttpProtocol Only Support `HTTP/1.1` currently. Current value is (${postJson.http_protocol})", cc=callContext) {
              postJson.http_protocol.equals("HTTP/1.1")
            }
            onCreateTransaction = ApiTrigger.onCreateTransaction.toString()
            wh <- BankAccountNotificationWebhookTrait.bankAccountNotificationWebhook.vend.createBankAccountNotificationWebhookFuture(
              bankId = bankId.value,
              userId = u.userId,
              triggerName = onCreateTransaction,
              url = postJson.url,
              httpMethod = postJson.http_method,
              httpProtocol = postJson.http_protocol
            ) map {
              unboxFullOrFail(_, callContext, CreateWebhookError)
            }
          } yield {
            (createBankLevelAccountWebhookJsonV400(wh), HttpCode.`201`(callContext))
          }
      }
    }

  }

  private def checkRoleBankIdExsiting(callContext: Option[CallContext], entitlement: CreateEntitlementJSON) = {
    Helper.booleanToFuture(failMsg = s"$BankNotFound Current BANK_ID (${entitlement.bank_id})", cc=callContext) {
      entitlement.bank_id.nonEmpty == false || BankX(BankId(entitlement.bank_id), callContext).map(_._1).isEmpty == false
    }
  }
  
  private def checkRolesBankIdExsiting(callContext: Option[CallContext], postedData: PostCreateUserWithRolesJsonV400) = {
    Future.sequence(postedData.roles.map(checkRoleBankIdExsiting(callContext,_)))
  }
  
  private def addEntitlementToUser(userId:String, entitlement: CreateEntitlementJSON, callContext: Option[CallContext]) = {
    Future(Entitlement.entitlement.vend.addEntitlement(entitlement.bank_id, userId, entitlement.role_name)) map { unboxFull(_) }
  }
  
  private def addEntitlementsToUser(userId:String, postedData: PostCreateUserWithRolesJsonV400, callContext: Option[CallContext]) = {
    Future.sequence(postedData.roles.distinct.map(addEntitlementToUser(userId, _, callContext)))
  }

  /**
   * This method will check all the roles the request user already has and the request roles:
   * It will find the roles the requestUser already have, then show the error to the developer.
   * (We can not grant the same roles to the request user twice)
   */
  private def assertTargetUserLacksRoles(userId:String, requestedEntitlements: List[CreateEntitlementJSON], callContext: Option[CallContext]) = {
    //1st:  get all the entitlements for the user:
    val userEntitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
    val userRoles = userEntitlements.map(_.map(entitlement => (entitlement.roleName, entitlement.bankId))).getOrElse(List.empty[(String,String)]).toSet
    
    val targetRoles = requestedEntitlements.map(entitlement => (entitlement.role_name, entitlement.bank_id)).toSet
    
    //2rd: find the duplicated ones:
    val duplicatedRoles = userRoles.filter(targetRoles)
    
    //3rd: We can not grant the roles again, so we show the error to the developer.
    if(duplicatedRoles.size >0){
      val errorMessages = s"$EntitlementAlreadyExists user_id($userId) ${duplicatedRoles.mkString(",")}"
        Helper.booleanToFuture(errorMessages, cc=callContext) {false}
    }else 
      Future.successful(Full())
  }

  /**
   * This method will check all the roles the loggedIn user already has and the request roles:
   * It will find the not existing roles from the loggedIn user  --> we will show the error to the developer
   *  (We can only grant the roles which the loggedIn User has to the requestUser)
   */
  private def assertUserCanGrantRoles(userId:String, requestedEntitlements: List[CreateEntitlementJSON], callContext: Option[CallContext]) = {
    //1st:  get all the entitlements for the user:
    val userEntitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
    val userRoles = userEntitlements.map(_.map(entitlement => (entitlement.roleName, entitlement.bankId))).getOrElse(List.empty[(String,String)]).toSet
    
    val targetRoles = requestedEntitlements.map(entitlement => (entitlement.role_name, entitlement.bank_id)).toSet
    
    //2rd: find the roles which the loggedIn user does not have,
    val roleLacking = targetRoles.filterNot(userRoles)
    
    if(roleLacking.size >0){
      val errorMessages = s"$EntitlementCannotBeGranted user_id($userId). The login user does not have the following roles: ${roleLacking.mkString(",")}"
      Helper.booleanToFuture(errorMessages, cc=callContext) {false}
    }else 
      Future.successful(Full())
  }
  
  private def checkRoleBankIdMapping(callContext: Option[CallContext], entitlement: CreateEntitlementJSON) = {
    Helper.booleanToFuture(failMsg = if (ApiRole.valueOf(entitlement.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole, cc = callContext) {
        ApiRole.valueOf(entitlement.role_name).requiresBankId == entitlement.bank_id.nonEmpty
    } 
  }

  private def checkRoleBankIdMappings(callContext: Option[CallContext], postedData: PostCreateUserWithRolesJsonV400) = {
    Future.sequence(postedData.roles.map(checkRoleBankIdMapping(callContext,_)))
  }
  
  private def checkRoleName(callContext: Option[CallContext], entitlement: CreateEntitlementJSON) = {
    Future{
      tryo {
        valueOf(entitlement.role_name)
      }
    } map {
      val msg = IncorrectRoleName + entitlement.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", ")
      x => unboxFullOrFail(x, callContext, msg)
    }
  }

  private def checkRolesName(callContext: Option[CallContext], postJsonBody: PostCreateUserWithRolesJsonV400) = {
    Future.sequence(postJsonBody.roles.map(checkRoleName(callContext,_)))
  }
  
  private def grantMultpleAccountAccessToUser(bankId: BankId, accountId: AccountId, user: User, views: List[View], callContext: Option[CallContext]) = {
    Future.sequence(views.map(view =>
      grantAccountAccessToUser(bankId: BankId, accountId: AccountId, user: User, view, callContext: Option[CallContext])
    ))
  }
  
  private def getViews(bankId: BankId, accountId: AccountId, postJson: PostCreateUserAccountAccessJsonV400, callContext: Option[CallContext]) = {
    Future.sequence(postJson.views.map(view => getView(bankId: BankId, accountId: AccountId, view: PostViewJsonV400, callContext: Option[CallContext])))
  }

  private def createDynamicEndpointMethod(bankId: Option[String], json: JValue, cc: CallContext) = {
    for {
      (postedJson, openAPI) <- NewStyle.function.tryons(InvalidJsonFormat+"The request json is not valid OpenAPIV3.0.x or Swagger 2.0.x Please check it in Swagger Editor or similar tools ", 400, cc.callContext) {
        //If it is bank level, we manually added /banks/bankId in all the paths:
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
        JSONFactory1_4_0.createResourceDocsJson(dynamicEndpointInfo.resourceDocs.toList, false, None)
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

