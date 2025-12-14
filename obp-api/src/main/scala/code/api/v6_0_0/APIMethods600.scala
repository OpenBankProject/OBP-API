package code.api.v6_0_0

import code.accountattribute.AccountAttributeX
import code.api.Constant
import code.api.{DirectLogin, ObpApiFailure}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, InvalidDateFormat, InvalidJsonFormat, UnknownError, DynamicEntityOperationNotAllowed, _}
import code.api.util.FutureUtil.EndpointContext
import code.api.util.Glossary
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, CallContext, DiagnosticDynamicEntityCheck, ErrorMessages, NewStyle, RateLimitingUtil}
import code.api.util.NewStyle.function.extractQueryParams
import code.api.util.newstyle.ViewNewStyle
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAggregateMetricJson
import code.api.v2_0_0.JSONFactory200
import code.api.v3_1_0.{JSONFactory310, PostCustomerNumberJsonV310}
import code.api.v4_0_0.CallLimitPostJsonV400
import code.api.v4_0_0.JSONFactory400.createCallsLimitJson
import code.api.v5_0_0.JSONFactory500
import code.api.v5_0_0.{ViewJsonV500, ViewsJsonV500}
import code.api.v5_1_0.{JSONFactory510, PostCustomerLegalNameJsonV510}
import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo}
import code.api.v6_0_0.JSONFactory600.{DynamicEntityDiagnosticsJsonV600, DynamicEntityIssueJsonV600, GroupJsonV600, GroupMembershipJsonV600, GroupMembershipsJsonV600, GroupsJsonV600, PostGroupJsonV600, PostGroupMembershipJsonV600, PostResetPasswordUrlJsonV600, PutGroupJsonV600, ReferenceTypeJsonV600, ReferenceTypesJsonV600, ResetPasswordUrlJsonV600, RoleWithEntitlementCountJsonV600, RolesWithEntitlementCountsJsonV600, ScannedApiVersionJsonV600, ValidateUserEmailJsonV600, ValidateUserEmailResponseJsonV600, createActiveCallLimitsJsonV600, createCallLimitJsonV600, createCurrentUsageJson}
import code.api.v6_0_0.OBPAPI6_0_0
import code.metrics.APIMetrics
import code.bankconnectors.LocalMappedConnectorInternal
import code.bankconnectors.LocalMappedConnectorInternal._
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.model._
import code.users.{UserAgreement, UserAgreementProvider, Users}
import code.ratelimiting.RateLimitingDI
import code.util.Helper
import code.util.Helper.{MdcLoggable, ObpS, SILENCE_IS_GOLDEN}
import code.views.Views
import code.views.system.ViewDefinition
import code.webuiprops.{MappedWebUiPropsProvider, WebUiPropsCommons, WebUiPropsPutJsonV600}
import code.dynamicEntity.DynamicEntityCommons
import code.DynamicData.{DynamicData, DynamicDataProvider}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{CustomerAttribute, _}
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.{Empty, Failure, Full}
import org.apache.commons.lang3.StringUtils
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{Extraction, JsonParser}
import net.liftweb.json.JsonAST.{JArray, JObject, JString, JValue}
import net.liftweb.json.JsonDSL._
import net.liftweb.mapper.{By, Descending, MaxRows, NullRef, OrderBy}
import code.api.util.ExampleValue.dynamicEntityResponseBodyExample
import net.liftweb.common.Box

import java.text.SimpleDateFormat
import java.util.UUID.randomUUID
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Random


trait APIMethods600 {
  self: RestHelper =>

  val Implementations6_0_0 = new Implementations600()

  class Implementations600 extends MdcLoggable {

    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


    staticResourceDocs += ResourceDoc(
      root,
      implementedInApiVersion,
      nameOf(root),
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
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
            (JSONFactory510.getApiInfoJSON(OBPAPI6_0_0.version, OBPAPI6_0_0.versionStatus), HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createTransactionRequestHold,
      implementedInApiVersion,
      nameOf(createTransactionRequestHold),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/HOLD/transaction-requests",
      "Create Transaction Request (HOLD)",
      s"""
         |
         |Create a transaction request to move funds from the account to its Holding Account.
         |If the Holding Account does not exist, it will be created automatically.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyHoldJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestHold: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "HOLD" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("HOLD")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    // --- GET Holding Account by Parent ---
    staticResourceDocs += ResourceDoc(
      getHoldingAccountByReleaser,
      implementedInApiVersion,
      nameOf(getHoldingAccountByReleaser),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/holding-accounts",
      "Get Holding Accounts By Releaser",
      s"""
         |
         |Return the first Holding Account linked to the given releaser account via account attribute `RELEASER_ACCOUNT_ID`.
         |Response is wrapped in a list and includes account attributes.
         |
       """.stripMargin,
      EmptyBody,
      moderatedCoreAccountsJsonV300,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        $UserNoPermissionAccessView,
        UnknownError
      ),
      List(apiTagAccount)
    )

    lazy val getHoldingAccountByReleaser: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "holding-accounts" :: Nil JsonGet _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user @Full(u), _, _, view, callContext) <- SS.userBankAccountView
            // Find accounts by attribute RELEASER_ACCOUNT_ID
            (accountIdsBox, callContext) <- AccountAttributeX.accountAttributeProvider.vend.getAccountIdsByParams(bankId, Map("RELEASER_ACCOUNT_ID" -> List(accountId.value))) map { ids => (ids, callContext) }
            accountIds = accountIdsBox.getOrElse(Nil)
            // load the first holding account
            holdingOpt <- {
              def firstHolding(ids: List[String]): Future[Option[BankAccount]] = ids match {
                case Nil => Future.successful(None)
                case id :: tail =>
                  NewStyle.function.getBankAccount(bankId, AccountId(id), callContext).flatMap { case (acc, cc) =>
                    if (acc.accountType == "HOLDING") Future.successful(Some(acc)) else firstHolding(tail)
                  }
              }
              firstHolding(accountIds)
            }
            holding <- NewStyle.function.tryons($BankAccountNotFound, 404, callContext) { holdingOpt.get }
            moderatedAccount <- Future { holding.moderatedBankAccount(view, BankIdAccountId(holding.bankId, holding.accountId), user, callContext) } map {
              x => unboxFullOrFail(x, callContext, UnknownError)
            }
            (attributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(bankId, holding.accountId, callContext)
          } yield {
            val accountsJson = JSONFactory300.createFirehoseCoreBankAccountJSON(List(moderatedAccount), Some(attributes))
            (accountsJson, HttpCode.`200`(callContext))
          }
    }

    staticResourceDocs += ResourceDoc(
      getCurrentCallsLimit,
      implementedInApiVersion,
      nameOf(getCurrentCallsLimit),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/current-usage",
      "Get Rate Limits for a Consumer Usage",
      s"""
         |Get Rate Limits for a Consumer Usage.
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      redisCallLimitJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UpdateConsumerError,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canReadCallLimits)))


    lazy val getCurrentCallsLimit: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "current-usage" :: Nil JsonGet _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, cc.callContext)
            currentUsage <- Future(RateLimitingUtil.consumerRateLimitState(consumerId).toList)
          } yield {
            (createCurrentUsageJson(currentUsage), HttpCode.`200`(cc.callContext))
          }
    }


    staticResourceDocs += ResourceDoc(
      createCallLimits,
      implementedInApiVersion,
      nameOf(createCallLimits),
      "POST",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits",
      "Create Rate Limits for a Consumer",
      s"""
         |Create Rate Limits for a Consumer
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      callLimitPostJsonV600,
      callLimitJsonV600,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canCreateRateLimits)))


    lazy val createCallLimits: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: Nil JsonPost json -> _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateRateLimits, callContext)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJsonV600 ", 400, callContext) {
              json.extract[CallLimitPostJsonV600]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.createConsumerCallLimits(
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
              Some(postJson.per_month_call_limit)
            )
          } yield {
            rateLimiting match {
              case Full(rateLimitingObj) => (createCallLimitJsonV600(rateLimitingObj), HttpCode.`201`(callContext))
              case _ => (UnknownError, HttpCode.`400`(callContext))
            }
          }
    }


    staticResourceDocs += ResourceDoc(
      updateRateLimits,
      implementedInApiVersion,
      nameOf(updateRateLimits),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID",
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
      Some(List(canUpdateRateLimits)))

    lazy val updateRateLimits: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: rateLimitingId :: Nil JsonPut json -> _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.handleEntitlementsAndScopes("", u.userId, List(canUpdateRateLimits), callContext)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJsonV400 ", 400, callContext) {
              json.extract[CallLimitPostJsonV400]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.updateConsumerCallLimits(
              rateLimitingId,
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
      deleteCallLimits,
      implementedInApiVersion,
      nameOf(deleteCallLimits),
      "DELETE",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID",
      "Delete Rate Limit by Rate Limiting ID",
      s"""
         |Delete a specific Rate Limit by Rate Limiting ID
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canDeleteRateLimits)))


    lazy val deleteCallLimits: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: rateLimitingId :: Nil JsonDelete _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteRateLimits, callContext)
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.getByRateLimitingId(rateLimitingId)
            _ <- rateLimiting match {
              case Full(rl) if rl.consumerId == consumerId =>
                Future.successful(Full(rl))
              case Full(_) =>
                Future.successful(ObpApiFailure(s"Rate limiting ID $rateLimitingId does not belong to consumer $consumerId", 400, callContext))
              case _ =>
                Future.successful(ObpApiFailure(s"Rate limiting ID $rateLimitingId not found", 404, callContext))
            }
            deleteResult <- RateLimitingDI.rateLimiting.vend.deleteByRateLimitingId(rateLimitingId)
          } yield {
            deleteResult match {
              case Full(true) => (EmptyBody, HttpCode.`204`(callContext))
              case _ => (UnknownError, HttpCode.`400`(callContext))
            }
          }
    }


    staticResourceDocs += ResourceDoc(
      getActiveCallLimitsAtDate,
      implementedInApiVersion,
      nameOf(getActiveCallLimitsAtDate),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits/active-at-date/DATE",
      "Get Active Rate Limits at Date",
      s"""
         |Get the sum of rate limits at a certain date time. This returns a SUM of all the records that span that time.
         |
         |Date format: YYYY-MM-DDTHH:MM:SSZ (e.g. 1099-12-31T23:00:00Z)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      activeCallLimitsJsonV600,
      List(
        $UserNotLoggedIn,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        InvalidDateFormat,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canReadCallLimits)))


    lazy val getActiveCallLimitsAtDate: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: "active-at-date" :: dateString :: Nil JsonGet _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadCallLimits, callContext)
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            date <- NewStyle.function.tryons(s"$InvalidDateFormat Current date format is: $dateString. Please use this format: YYYY-MM-DDTHH:MM:SSZ (e.g. 1099-12-31T23:00:00Z)", 400, callContext) {
              val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
              format.parse(dateString)
            }
            activeCallLimits <- RateLimitingDI.rateLimiting.vend.getActiveCallLimitsByConsumerIdAtDate(consumerId, date)
          } yield {
            (createActiveCallLimitsJsonV600(activeCallLimits, date), HttpCode.`200`(callContext))
          }
    }

    staticResourceDocs += ResourceDoc(
      getDynamicEntityDiagnostics,
      implementedInApiVersion,
      nameOf(getDynamicEntityDiagnostics),
      "GET",
      "/management/diagnostics/dynamic-entities",
      "Get Dynamic Entity Diagnostics",
      s"""Get diagnostic information about Dynamic Entities to help troubleshoot Swagger generation issues.
         |
         |**Use Case:**
         |This endpoint is particularly useful when:
         |* The Swagger endpoint (`/obp/v6.0.0/resource-docs/OBPv6.0.0/swagger?content=dynamic`) fails with errors like "expected boolean"
         |* The OBP endpoint (`/obp/v6.0.0/resource-docs/OBPv6.0.0/obp?content=dynamic`) works fine
         |* You need to identify which dynamic entity has malformed field definitions
         |
         |**What It Checks:**
         |This endpoint analyzes all dynamic entities (both system and bank level) for:
         |* Boolean fields with invalid example values (e.g., actual JSON booleans or invalid strings instead of `"true"` or `"false"`)
         |* Malformed JSON in field definitions
         |* Fields that cannot be converted to their declared types
         |* Other validation issues that cause Swagger generation to fail
         |
         |**Response Format:**
         |The response contains:
         |* `issues` - List of issues found, each with:
         |  * `entity_name` - Name of the problematic entity
         |  * `bank_id` - Bank ID (or "SYSTEM_LEVEL" for system entities)
         |  * `field_name` - Name of the problematic field
         |  * `example_value` - The current (invalid) example value
         |  * `error_message` - Description of what's wrong and how to fix it
         |* `total_issues` - Count of total issues found
         |* `scanned_entities` - List of all dynamic entities that were scanned (format: "EntityName (BANK_ID)" or "EntityName (SYSTEM)")
         |
         |**How to Fix Issues:**
         |1. Identify the problematic entity from the diagnostic output
         |2. Update the entity definition using PUT `/management/system-dynamic-entities/DYNAMIC_ENTITY_ID` or PUT `/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID`
         |3. For boolean fields, ensure the example value is either `"true"` or `"false"` (as strings)
         |4. Re-run this diagnostic to verify the fix
         |5. Check that the Swagger endpoint now works
         |
         |**Example Issue:**
         |```
         |{
         |  "entity_name": "Customer",
         |  "bank_id": "gh.29.uk",
         |  "field_name": "is_active",
         |  "example_value": "malformed_value",
         |  "error_message": "Boolean field has invalid example value. Expected 'true' or 'false', got: 'malformed_value'"
         |}
         |```
         |
         |${userAuthenticationMessage(true)}
         |
         |**Required Role:** `CanGetDynamicEntityDiagnostics`
         |
         |If no issues are found, the response will contain an empty issues list with `total_issues: 0`, but `scanned_entities` will show which entities were checked.
         |""",
      EmptyBody,
      DynamicEntityDiagnosticsJsonV600(
        scanned_entities = List("MyEntity (gh.29.uk)", "AnotherEntity (SYSTEM)"),
        issues = List(
          DynamicEntityIssueJsonV600(
            entity_name = "MyEntity",
            bank_id = "gh.29.uk",
            field_name = "is_active",
            example_value = "malformed_value",
            error_message = "Boolean field has invalid example value. Expected 'true' or 'false', got: 'malformed_value'"
          )
        ),
        total_issues = 1
      ),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicEntity, apiTagApi),
      Some(List(canGetDynamicEntityDiagnostics))
    )

    lazy val getDynamicEntityDiagnostics: OBPEndpoint = {
      case "management" :: "diagnostics" :: "dynamic-entities" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetDynamicEntityDiagnostics, callContext)
          } yield {
            val result = DiagnosticDynamicEntityCheck.checkAllDynamicEntities()
            val issuesJson = result.issues.map { issue =>
              DynamicEntityIssueJsonV600(
                entity_name = issue.entityName,
                bank_id = issue.bankId.getOrElse("SYSTEM_LEVEL"),
                field_name = issue.fieldName,
                example_value = issue.exampleValue,
                error_message = issue.errorMessage
              )
            }
            val response = DynamicEntityDiagnosticsJsonV600(result.scannedEntities, issuesJson, result.issues.length)
            (response, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getReferenceTypes,
      implementedInApiVersion,
      nameOf(getReferenceTypes),
      "GET",
      "/management/dynamic-entities/reference-types",
      "Get Reference Types for Dynamic Entities",
      s"""Get a list of all available reference types that can be used in Dynamic Entity field definitions.
         |
         |Reference types allow Dynamic Entity fields to reference other entities (similar to foreign keys).
         |This endpoint returns both:
         |* **Static reference types** - Built-in reference types for core OBP entities (e.g., Customer, Account, Transaction)
         |* **Dynamic reference types** - Reference types for Dynamic Entities that have been created
         |
         |Each reference type includes:
         |* `type_name` - The full reference type string to use in entity definitions (e.g., "reference:Customer")
         |* `example_value` - An example value showing the correct format
         |* `description` - Description of what the reference type represents
         |
         |**Use Case:**
         |When creating a Dynamic Entity with a field that references another entity, you need to know:
         |1. What reference types are available
         |2. The correct format for the type name
         |3. The correct format for example values
         |
         |This endpoint provides all that information.
         |
         |**Example Usage:**
         |If you want to create a Dynamic Entity with a field that references a Customer, you would:
         |1. Call this endpoint to see that "reference:Customer" is available
         |2. Use it in your entity definition like:
         |```json
         |{
         |  "customer_id": {
         |    "type": "reference:Customer",
         |    "example": "a8770fca-3d1d-47af-b6d0-7a6c3f124388"
         |  }
         |}
         |```
         |
         |${userAuthenticationMessage(true)}
         |
         |**Required Role:** `CanGetDynamicEntityReferenceTypes`
         |""",
      EmptyBody,
      ReferenceTypesJsonV600(
        reference_types = List(
          ReferenceTypeJsonV600(
            type_name = "reference:Customer",
            example_value = "a8770fca-3d1d-47af-b6d0-7a6c3f124388",
            description = "Reference to a Customer entity"
          ),
          ReferenceTypeJsonV600(
            type_name = "reference:Account:BANK_ID&ACCOUNT_ID",
            example_value = "BANK_ID=b9881ecb-4e2e-58bg-c7e1-8b7d4e235499&ACCOUNT_ID=c0992fdb-5f3f-69ch-d8f2-9c8e5f346600",
            description = "Composite reference to an Account by bank ID and account ID"
          ),
          ReferenceTypeJsonV600(
            type_name = "reference:MyDynamicEntity",
            example_value = "d1aa3gec-6g4g-70di-e9g3-0d9f6g457711",
            description = "Reference to MyDynamicEntity (dynamic entity)"
          )
        )
      ),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagDynamicEntity, apiTagApi),
      Some(List(canGetDynamicEntityReferenceTypes))
    )

    lazy val getReferenceTypes: OBPEndpoint = {
      case "management" :: "dynamic-entities" :: "reference-types" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetDynamicEntityReferenceTypes, callContext)
          } yield {
            val referenceTypeNames = code.dynamicEntity.ReferenceType.referenceTypeNames

            // Get list of dynamic entity names to distinguish from static references
            val dynamicEntityNames = NewStyle.function.getDynamicEntities(None, true)
              .map(entity => s"reference:${entity.entityName}")
              .toSet

            val exampleId1 = APIUtil.generateUUID()
            val exampleId2 = APIUtil.generateUUID()
            val exampleId3 = APIUtil.generateUUID()
            val exampleId4 = APIUtil.generateUUID()

            val reg1 = """reference:([^:]+)""".r
            val reg2 = """reference:(?:[^:]+):([^&]+)&([^&]+)""".r
            val reg3 = """reference:(?:[^:]+):([^&]+)&([^&]+)&([^&]+)""".r
            val reg4 = """reference:(?:[^:]+):([^&]+)&([^&]+)&([^&]+)&([^&]+)""".r

            val referenceTypes = referenceTypeNames.map { refTypeName =>
              val example = refTypeName match {
                case reg1(entityName) =>
                  val description = if (dynamicEntityNames.contains(refTypeName)) {
                    s"Reference to $entityName (dynamic entity)"
                  } else {
                    s"Reference to $entityName entity"
                  }
                  (exampleId1, description)
                case reg2(a, b) =>
                  (s"$a=$exampleId1&$b=$exampleId2", s"Composite reference with $a and $b")
                case reg3(a, b, c) =>
                  (s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3", s"Composite reference with $a, $b and $c")
                case reg4(a, b, c, d) =>
                  (s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3&$d=$exampleId4", s"Composite reference with $a, $b, $c and $d")
                case _ => (exampleId1, "Reference type")
              }

              ReferenceTypeJsonV600(
                type_name = refTypeName,
                example_value = example._1,
                description = example._2
              )
            }

            val response = ReferenceTypesJsonV600(referenceTypes)
            (response, HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getCurrentUser,
      implementedInApiVersion,
      nameOf(getCurrentUser), // TODO can we get this string from the val two lines above?
      "GET",
      "/users/current",
      "Get User (Current)",
      s"""Get the logged in user
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      userJsonV300,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagUser))

    lazy val getCurrentUser: OBPEndpoint = {
      case "users" :: "current" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
          } yield {
            val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(u).toOption
            val currentUser = UserV600(u, entitlements, permissions)
            val onBehalfOfUser = if(cc.onBehalfOfUser.isDefined) {
              val user = cc.onBehalfOfUser.toOption.get
              val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).headOption.toList.flatten
              val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(user).toOption
              Some(UserV600(user, entitlements, permissions))
            } else {
              None
            }
            (JSONFactory600.createUserInfoJSON(currentUser, onBehalfOfUser), HttpCode.`200`(callContext))
          }
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
         |* is_deleted (default: false)
         |
      """.stripMargin,
      EmptyBody,
      usersInfoJsonV600,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canGetAnyUser))
    )

    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => { cc =>
        implicit val ec = EndpointContext(Some(cc))
        for {
          httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
          (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(
            httpParams,
            cc.callContext
          )
          users <- code.users.Users.users.vend.getUsers(obpQueryParams)
        } yield {
          (JSONFactory600.createUsersInfoJsonV600(users), HttpCode.`200`(callContext))
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUserByUserId,
      implementedInApiVersion,
      nameOf(getUserByUserId),
      "GET",
      "/users/user-id/USER_ID",
      "Get User by USER_ID",
      s"""Get user by USER_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      userInfoJsonV600,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canGetAnyUser))
    )

    lazy val getUserByUserId: OBPEndpoint = {
      case "users" :: "user-id" :: userId :: Nil JsonGet _ => { cc =>
        implicit val ec = EndpointContext(Some(cc))
        for {
          (Full(u), callContext) <- authenticatedAccess(cc)
          _ <- NewStyle.function.hasEntitlement("", u.userId, canGetAnyUser, callContext)
          user <- Users.users.vend.getUserByUserIdFuture(userId) map {
            x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId($userId)")
          }
          entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, callContext)
          // Fetch user agreements
          agreements <- Future {
            val acceptMarketingInfo = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "accept_marketing_info")
            val termsAndConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "terms_and_conditions")
            val privacyConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "privacy_conditions")
            val agreementList = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
            if (agreementList.isEmpty) None else Some(agreementList)
          }
          isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
          // Fetch metrics data for the user
          userMetrics <- Future {
            code.metrics.MappedMetric.findAll(
              By(code.metrics.MappedMetric.userId, userId),
              OrderBy(code.metrics.MappedMetric.date, Descending),
              MaxRows(5)
            )
          }
          lastActivityDate = userMetrics.headOption.map(_.getDate())
          recentOperationIds = userMetrics.map(_.getImplementedByPartialFunction()).distinct.take(5)
        } yield {
          (JSONFactory600.createUserInfoJsonV600(user, entitlements, agreements, isLocked, lastActivityDate, recentOperationIds), HttpCode.`200`(callContext))
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMigrations,
      implementedInApiVersion,
      nameOf(getMigrations),
      "GET",
      "/system/migrations",
      "Get Database Migrations",
      s"""Get all database migration script logs.
         |
         |This endpoint returns information about all migration scripts that have been executed or attempted.
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetMigrations entitlement is required.
         |
      """.stripMargin,
      EmptyBody,
      migrationScriptLogsJsonV600,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystem, apiTagApi),
      Some(List(canGetMigrations))
    )

    lazy val getMigrations: OBPEndpoint = {
      case "system" :: "migrations" :: Nil JsonGet _ => { cc =>
        implicit val ec = EndpointContext(Some(cc))
        for {
          (Full(u), callContext) <- authenticatedAccess(cc)
          _ <- NewStyle.function.hasEntitlement("", u.userId, canGetMigrations, callContext)
        } yield {
          val migrations = code.migration.MigrationScriptLogProvider.migrationScriptLogProvider.vend.getMigrationScriptLogs()
          (JSONFactory600.createMigrationScriptLogsJsonV600(migrations), HttpCode.`200`(callContext))
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      createTransactionRequestCardano,
      implementedInApiVersion,
      nameOf(createTransactionRequestCardano),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/CARDANO/transaction-requests",
      "Create Transaction Request (CARDANO)",
      s"""
         |
         |For sandbox mode, it will use the Cardano Preprod Network.
         |The accountId can be the wallet_id for now, as it uses cardano-wallet in the backend.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyCardanoJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestCardano: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "CARDANO" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("CARDANO")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    staticResourceDocs += ResourceDoc(
      createTransactionRequestEthereumeSendTransaction,
      implementedInApiVersion,
      nameOf(createTransactionRequestEthereumeSendTransaction),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_TRANSACTION/transaction-requests",
      "Create Transaction Request (ETH_SEND_TRANSACTION)",
      s"""
         |
         |Send ETH via Ethereum JSON-RPC.
         |AccountId should hold the 0x address for now.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyEthereumJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestEthereumeSendTransaction: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "ETH_SEND_TRANSACTION" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("ETH_SEND_TRANSACTION")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }
    staticResourceDocs += ResourceDoc(
      createTransactionRequestEthSendRawTransaction,
      implementedInApiVersion,
      nameOf(createTransactionRequestEthSendRawTransaction),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_RAW_TRANSACTION/transaction-requests",
      "CREATE TRANSACTION REQUEST (ETH_SEND_RAW_TRANSACTION )",
      s"""
         |
         |Send ETH via Ethereum JSON-RPC.
         |AccountId should hold the 0x address for now.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyEthSendRawTransactionJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestEthSendRawTransaction: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "ETH_SEND_RAW_TRANSACTION" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("ETH_SEND_RAW_TRANSACTION")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
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
         Only SANDBOX mode (i.e. when connector=mapped in properties file)
         |The settlement accounts are automatically created by the system when the bank is created.
         |Name and account id are created in accordance to the next rules:
         |  - Incoming account (name: Default incoming settlement account, Account ID: OBP_DEFAULT_INCOMING_ACCOUNT_ID, currency: EUR)
         |  - Outgoing account (name: Default outgoing settlement account, Account ID: OBP_DEFAULT_OUTGOING_ACCOUNT_ID, currency: EUR)
         |
         |""",

      postBankJson600,
      bankJson600,
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
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson600 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostBankJson600]
            }

            checkShortStringValue = APIUtil.checkOptionalShortString(postJson.bank_id)
            _ <- Helper.booleanToFuture(failMsg = s"$checkShortStringValue.", cc = cc.callContext) {
              checkShortStringValue == SILENCE_IS_GOLDEN
            }

            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc = cc.callContext) {
              cc.callContext.map(_.consumer.isDefined == true).isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc = cc.callContext) {
              postJson.bank_id.length > 3
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc = cc.callContext) {
              !postJson.bank_id.contains(" ")
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc = cc.callContext) {
              !`checkIfContains::::`(postJson.bank_id)
            }
            (banks, callContext) <- NewStyle.function.getBanks(cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.bankIdAlreadyExists, cc = cc.callContext) {
              !banks.exists { b => postJson.bank_id.contains(b.bankId.value) }
            }
            (success, callContext) <- NewStyle.function.createOrUpdateBank(
              postJson.bank_id,
              postJson.full_name.getOrElse(""),
              postJson.bank_code,
              postJson.logo.getOrElse(""),
              postJson.website.getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              callContext
            )
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, callContext)
            entitlementsByBank = entitlements.filter(_.bankId == postJson.bank_id)
            _ <- entitlementsByBank.exists(_.roleName == CanCreateEntitlementAtOneBank.toString()) match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanCreateEntitlementAtOneBank.toString()))
            }
            _ <- entitlementsByBank.exists(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()) match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
            }
          } yield {
            (JSONFactory600.createBankJSON600(success), HttpCode.`201`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      getProviders,
      implementedInApiVersion,
      nameOf(getProviders),
      "GET",
      "/providers",
      "Get Providers",
      s"""Get the list of authentication providers that have been used to create users on this OBP instance.
         |
         |This endpoint returns a distinct list of provider values from the resource_user table.
         |
         |Providers may include:
         |* Local OBP provider (e.g., "http://127.0.0.1:8080")
         |* OAuth 2.0 / OpenID Connect providers (e.g., "google.com", "microsoft.com")
         |* Custom authentication providers
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      JSONFactory600.createProvidersJson(List("http://127.0.0.1:8080", "OBP", "google.com")),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canGetProviders))
    )

    lazy val getProviders: OBPEndpoint = {
      case "providers" :: Nil JsonGet _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetProviders, callContext)
            providers <- Future { code.model.dataAccess.ResourceUser.getDistinctProviders }
          } yield {
            (JSONFactory600.createProvidersJson(providers), HttpCode.`200`(callContext))
          }
    }

    staticResourceDocs += ResourceDoc(
      getConnectorMethodNames,
      implementedInApiVersion,
      nameOf(getConnectorMethodNames),
      "GET",
      "/system/connector-method-names",
      "Get Connector Method Names",
      s"""Get the list of all available connector method names.
         |
         |These are the method names that can be used in Method Routing configuration.
         |
         |## Data Source
         |
         |The data comes from **scanning the actual Scala connector code at runtime** using reflection, NOT from a database or configuration file.
         |
         |The endpoint:
         |1. Reads the connector name from props (e.g., `connector=mapped`)
         |2. Gets the connector instance (e.g., LocalMappedConnector, KafkaConnector, StarConnector)
         |3. Uses Scala reflection to scan all public methods that override the base Connector trait
         |4. Filters for valid connector methods (public, has parameters, overrides base trait)
         |5. Returns the method names as a sorted list
         |
         |## Which Connector?
         |
         |Depends on your `connector` property:
         |* `connector=mapped` → Returns methods from LocalMappedConnector
         |* `connector=kafka_vSept2018` → Returns methods from KafkaConnector
         |* `connector=star` → Returns methods from StarConnector
         |* `connector=rest_vMar2019` → Returns methods from RestConnector
         |
         |## When Does It Change?
         |
         |The list only changes when:
         |* Code is deployed with new/modified connector methods
         |* The `connector` property is changed to point to a different connector
         |
         |## Performance
         |
         |This endpoint uses caching (default: 1 hour) because Scala reflection is expensive.
         |Configure via: `getConnectorMethodNames.cache.ttl.seconds=3600`
         |
         |## Use Case
         |
         |Use this endpoint to discover which connector methods are available when configuring Method Routing.
         |These method names are different from API endpoint operation IDs (which you get from /resource-docs).
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetMethodRoutings entitlement is required.
         |
      """.stripMargin,
      EmptyBody,
      ConnectorMethodNamesJsonV600(List("getBank", "getBanks", "getUser", "getAccount", "makePayment", "getTransactions")),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystem, apiTagMethodRouting, apiTagApi),
      Some(List(canGetMethodRoutings))
    )

    lazy val getConnectorMethodNames: OBPEndpoint = {
      case "system" :: "connector-method-names" :: Nil JsonGet _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetMethodRoutings, callContext)
            // Fetch connector method names with caching
            methodNames <- Future {
              /**
               * Connector methods rarely change (only on deployment), so we cache for a long time.
               */
              val cacheKey = "getConnectorMethodNames"
              val cacheTTL = APIUtil.getPropsAsIntValue("getConnectorMethodNames.cache.ttl.seconds", 3600)
              Caching.memoizeSyncWithProvider(Some(cacheKey))(cacheTTL seconds) {
                val connectorName = APIUtil.getPropsValue("connector", "mapped")
                val connector = code.bankconnectors.Connector.getConnectorInstance(connectorName)
                connector.callableMethods.keys.toList
              }
            }
          } yield {
            (JSONFactory600.createConnectorMethodNamesJson(methodNames), HttpCode.`200`(callContext))
          }
    }

    staticResourceDocs += ResourceDoc(
      getScannedApiVersions,
      implementedInApiVersion,
      nameOf(getScannedApiVersions),
      "GET",
      "/api/versions",
      "Get Scanned API Versions",
      s"""Get all scanned API versions available in this codebase.
         |
         |This endpoint returns all API versions that have been discovered/scanned, along with their active status.
         |
         |**Response Fields:**
         |
         |* `url_prefix`: The URL prefix for the version (e.g., "obp", "berlin-group", "open-banking")
         |* `api_standard`: The API standard name (e.g., "OBP", "BG", "UK", "STET")
         |* `api_short_version`: The version number (e.g., "v4.0.0", "v1.3")
         |* `fully_qualified_version`: The fully qualified version combining standard and version (e.g., "OBPv4.0.0", "BGv1.3")
         |* `is_active`: Boolean indicating if the version is currently enabled and accessible
         |
         |**Active Status:**
         |
         |* `is_active=true`: Version is enabled and can be accessed via its URL prefix
         |* `is_active=false`: Version is scanned but disabled (via `api_disabled_versions` props)
         |
         |**Use Cases:**
         |
         |* Discover what API versions are available in the codebase
         |* Check which versions are currently enabled
         |* Verify that disabled versions configuration is working correctly
         |* API documentation and discovery
         |
         |**Note:** This differs from v4.0.0's `/api/versions` endpoint which shows all scanned versions without is_active status.
         |
         |""",
      EmptyBody,
      ListResult(
        "scanned_api_versions",
        List(
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v1.2.1", fully_qualified_version = "OBPv1.2.1", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v1.3.0", fully_qualified_version = "OBPv1.3.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v1.4.0", fully_qualified_version = "OBPv1.4.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v2.0.0", fully_qualified_version = "OBPv2.0.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v2.1.0", fully_qualified_version = "OBPv2.1.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v2.2.0", fully_qualified_version = "OBPv2.2.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v3.0.0", fully_qualified_version = "OBPv3.0.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v3.1.0", fully_qualified_version = "OBPv3.1.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v4.0.0", fully_qualified_version = "OBPv4.0.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v5.0.0", fully_qualified_version = "OBPv5.0.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v5.1.0", fully_qualified_version = "OBPv5.1.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = "v6.0.0", fully_qualified_version = "OBPv6.0.0", is_active = true),
          ScannedApiVersionJsonV600(url_prefix = "berlin-group", api_standard = "BG", api_short_version = "v1.3", fully_qualified_version = "BGv1.3", is_active = false)
        )
      ),
      List(
        UnknownError
      ),
      List(apiTagDocumentation, apiTagApi),
      Some(Nil)
    )

    lazy val getScannedApiVersions: OBPEndpoint = {
      case "api" :: "versions" :: Nil JsonGet _ => { cc =>
        implicit val ec = EndpointContext(Some(cc))
        Future {
          val versions: List[ScannedApiVersionJsonV600] =
            ApiVersion.allScannedApiVersion.asScala.toList
              .filter(version => version.urlPrefix.trim.nonEmpty)
              .map { version =>
                ScannedApiVersionJsonV600(
                  url_prefix = version.urlPrefix,
                  api_standard = version.apiStandard,
                  api_short_version = version.apiShortVersion,
                  fully_qualified_version = version.fullyQualifiedVersion,
                  is_active = versionIsAllowed(version)
                )
              }
          (
            ListResult("scanned_api_versions", versions),
            HttpCode.`200`(cc.callContext)
          )
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
         |The Customer resource stores the customer number, legal name, email, phone number, date of birth, relationship status,
         |education attained, a url for a profile image, KYC status, credit rating, credit limit, and other customer information.
         |
         |**Required Fields:**
         |- legal_name: The customer's full legal name
         |- mobile_phone_number: The customer's mobile phone number
         |
         |**Optional Fields:**
         |- customer_number: If not provided, a random number will be generated
         |- email: Customer's email address
         |- face_image: Customer's face image (url and date)
         |- date_of_birth: Customer's date of birth in YYYY-MM-DD format
         |- relationship_status: Customer's relationship status
         |- dependants: Number of dependants (must match the length of dob_of_dependants array)
         |- dob_of_dependants: Array of dependant birth dates in YYYY-MM-DD format
         |- credit_rating: Customer's credit rating (rating and source)
         |- credit_limit: Customer's credit limit (currency and amount)
         |- highest_education_attained: Customer's highest education level
         |- employment_status: Customer's employment status
         |- kyc_status: Know Your Customer verification status (true/false). Default: false
         |- last_ok_date: Last verification date
         |- title: Customer's title (e.g., Mr., Mrs., Dr.)
         |- branch_id: Associated branch identifier
         |- name_suffix: Customer's name suffix (e.g., Jr., Sr.)
         |
         |**Date Format:**
         |In v6.0.0, date_of_birth and dob_of_dependants must be provided in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
         |The dates are strictly validated and must be valid calendar dates.
         |Dates are stored with time set to midnight (00:00:00) UTC for consistency.
         |
         |**Validations:**
         |- customer_number cannot contain `::::` characters
         |- customer_number must be unique for the bank
         |- The number of dependants must equal the length of the dob_of_dependants array
         |- date_of_birth must be in valid YYYY-MM-DD format if provided
         |- Each date in dob_of_dependants must be in valid YYYY-MM-DD format
         |
         |Note: If you need to set a specific customer number, use the Update Customer Number endpoint after this call.
         |
         |${userAuthenticationMessage(true)}
         |""",
      postCustomerJsonV600,
      customerJsonV600,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        InvalidJsonContent,
        InvalidDateFormat,
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
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV600 ", 400, cc.callContext) {
              json.extract[PostCustomerJsonV600]
            }
            _ <- Helper.booleanToFuture(failMsg =  InvalidJsonContent + s" The field dependants(${postedData.dependants.getOrElse(0)}) not equal the length(${postedData.dob_of_dependants.getOrElse(Nil).length }) of dob_of_dependants array", 400, cc.callContext) {
              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
            }

            // Validate and parse date_of_birth (YYYY-MM-DD format)
            dateOfBirth <- Future {
              postedData.date_of_birth.map { dateStr =>
                try {
                  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
                  formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                  formatter.setLenient(false)
                  formatter.parse(dateStr)
                } catch {
                  case _: Exception =>
                    throw new Exception(s"$InvalidJsonFormat date_of_birth must be in YYYY-MM-DD format (e.g., 1990-05-15), got: $dateStr")
                }
              }.orNull
            }

            // Validate and parse dob_of_dependants (YYYY-MM-DD format)
            dobOfDependants <- Future {
              postedData.dob_of_dependants.getOrElse(Nil).map { dateStr =>
                try {
                  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
                  formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                  formatter.setLenient(false)
                  formatter.parse(dateStr)
                } catch {
                  case _: Exception =>
                    throw new Exception(s"$InvalidJsonFormat dob_of_dependants must contain dates in YYYY-MM-DD format (e.g., 2010-03-20), got: $dateStr")
                }
              }
            }

            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc=cc.callContext) {
              !`checkIfContains::::` (customerNumber)
            }
            (_, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, cc.callContext)
            (customer, callContext) <- NewStyle.function.createCustomerC2(
              bankId,
              postedData.legal_name,
              customerNumber,
              postedData.mobile_phone_number,
              postedData.email.getOrElse(""),
              CustomerFaceImage(
                postedData.face_image.map(_.date).getOrElse(null),
                postedData.face_image.map(_.url).getOrElse("")
              ),
              dateOfBirth,
              postedData.relationship_status.getOrElse(""),
              postedData.dependants.getOrElse(0),
              dobOfDependants,
              postedData.highest_education_attained.getOrElse(""),
              postedData.employment_status.getOrElse(""),
              postedData.kyc_status.getOrElse(false),
              postedData.last_ok_date.getOrElse(null),
              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
              postedData.title.getOrElse(""),
              postedData.branch_id.getOrElse(""),
              postedData.name_suffix.getOrElse(""),
              callContext,
            )
          } yield {
            (JSONFactory600.createCustomerJson(customer), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomersAtAllBanks,
      implementedInApiVersion,
      nameOf(getCustomersAtAllBanks),
      "GET",
      "/customers",
      "Get Customers at All Banks",
      s"""Get Customers at All Banks.
         |
         |Returns a list of all customers across all banks.
         |
         |**Date Format:**
         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
         |
         |**Query Parameters:**
         |- limit: Maximum number of customers to return (optional)
         |- offset: Number of customers to skip for pagination (optional)
         |- sort_direction: Sort direction - ASC or DESC (optional)
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customerJSONsV600,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser),
      Some(List(canGetCustomersAtAllBanks))
    )

    lazy val getCustomersAtAllBanks : OBPEndpoint = {
      case "customers" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            (customers, callContext) <- NewStyle.function.getCustomersAtAllBanks(callContext, requestParams)
          } yield {
            (JSONFactory600.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomersByLegalName,
      implementedInApiVersion,
      nameOf(getCustomersByLegalName),
      "POST",
      "/banks/BANK_ID/customers/legal-name",
      "Get Customers by Legal Name",
      s"""Gets the Customers specified by Legal Name.
         |
         |Returns a list of customers that match the provided legal name.
         |
         |**Date Format:**
         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      PostCustomerLegalNameJsonV510(legal_name = "John Smith"),
      customerJSONsV600,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomersAtOneBank))
    )

    lazy val getCustomersByLegalName: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "legal-name" :: Nil JsonPost json -> _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerLegalNameJsonV510 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerLegalNameJsonV510]
            }
            (customers, callContext) <- NewStyle.function.getCustomersByCustomerLegalName(bank.bankId, postedData.legal_name, callContext)
          } yield {
            (JSONFactory600.createCustomersJson(customers), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomersAtOneBank,
      implementedInApiVersion,
      nameOf(getCustomersAtOneBank),
      "GET",
      "/banks/BANK_ID/customers",
      "Get Customers at Bank",
      s"""Get Customers at Bank.
         |
         |Returns a list of all customers at the specified bank.
         |
         |**Date Format:**
         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
         |
         |**Query Parameters:**
         |- limit: Maximum number of customers to return (optional)
         |- offset: Number of customers to skip for pagination (optional)
         |- sort_direction: Sort direction - ASC or DESC (optional)
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customerJSONsV600,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser),
      Some(List(canGetCustomersAtOneBank))
    )

    lazy val getCustomersAtOneBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            customers <- NewStyle.function.getCustomers(bankId, callContext, requestParams)
          } yield {
            (JSONFactory600.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerByCustomerId,
      implementedInApiVersion,
      nameOf(getCustomerByCustomerId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID",
      "Get Customer by CUSTOMER_ID",
      s"""Gets the Customer specified by CUSTOMER_ID.
         |
         |**Date Format:**
         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      EmptyBody,
      customerWithAttributesJsonV600,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canGetCustomersAtOneBank)))

    lazy val getCustomerByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId ::  Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
              bankId,
              CustomerId(customerId),
              callContext: Option[CallContext])
          } yield {
            (JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerByCustomerNumber,
      implementedInApiVersion,
      nameOf(getCustomerByCustomerNumber),
      "POST",
      "/banks/BANK_ID/customers/customer-number",
      "Get Customer by CUSTOMER_NUMBER",
      s"""Gets the Customer specified by CUSTOMER_NUMBER.
         |
         |**Date Format:**
         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
         |
         |${userAuthenticationMessage(true)}
         |
         |""",
      postCustomerNumberJsonV310,
      customerWithAttributesJsonV600,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomersAtOneBank))
    )

    lazy val getCustomerByCustomerNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "customer-number" ::  Nil JsonPost  json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerNumberJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerNumberJsonV310]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bank.bankId, callContext)
            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
              bankId,
              CustomerId(customer.customerId),
              callContext: Option[CallContext])
          } yield {
            (JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMetrics,
      implementedInApiVersion,
      nameOf(getMetrics),
      "GET",
      "/management/metrics",
      "Get Metrics",
      s"""Get API metrics rows. These are records of each REST API call.
         |
         |require CanReadMetrics role
         |
         |**NOTE: Automatic from_date Default**
         |
         |If you do not provide a `from_date` parameter, this endpoint will automatically set it to:
         |**now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes ago**
         |
         |This prevents accidentally querying all metrics since Unix Epoch and ensures reasonable response times.
         |For historical/reporting queries, always explicitly specify your desired `from_date`.
         |
         |**IMPORTANT: Smart Caching & Performance**
         |
         |This endpoint uses intelligent two-tier caching to optimize performance:
         |
         |**Stable Data Cache (Long TTL):**
         |- Metrics older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600")} seconds (${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} minutes) are considered immutable/stable
         |- These are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400")} seconds (${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours)
         |- Used when your query's from_date is older than the stable boundary
         |
         |**Recent Data Cache (Short TTL):**
         |- Recent metrics (within the stable boundary) are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds
         |- Used when your query includes recent data or has no from_date
         |
         |**STRONGLY RECOMMENDED: Always specify from_date in your queries!**
         |
         |**Why from_date matters:**
         |- Queries WITH from_date older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} mins → cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours (fast!)
         |- Queries WITHOUT from_date → cached for only ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds (slower)
         |
         |**Examples:**
         |- `from_date=2025-01-01T00:00:00.000Z` → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours cache (historical data)
         |- `from_date=$DateWithMsExampleString` (recent date) → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds cache (recent data)
         |- No from_date → **Automatically set to ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes ago** → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds cache (recent data)
         |
         |For best performance on historical/reporting queries, always include a from_date parameter!
         |
         |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
         |
         |You can filter by the following fields by applying url parameters
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
         |
         |1 from_date e.g.:from_date=$DateWithMsExampleString
         |   **DEFAULT**: If not provided, automatically set to now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes (keeps queries in recent data zone)
         |   **IMPORTANT**: Including from_date enables long-term caching for historical data queries!
         |
         |2 to_date e.g.:to_date=$DateWithMsExampleString Defaults to a far future date i.e. ${APIUtil.ToDateInFuture}
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
         |16 duration (if null ignore) - Returns calls where duration > specified value (in milliseconds). Use this to find slow API calls. eg: duration=5000 returns calls taking more than 5 seconds
         |
      """.stripMargin,
      EmptyBody,
      metricsJsonV510,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMetric, apiTagApi),
      Some(List(canReadMetrics)))

    lazy val getMetrics: OBPEndpoint = {
      case "management" :: "metrics" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadMetrics, callContext)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            // If from_date is not provided, set it to now - (stable.boundary - 1 second)
            // This ensures we get recent data with the shorter cache TTL
            httpParamsWithDefault = {
              val hasFromDate = httpParams.exists(p => p.name == "from_date" || p.name == "obp_from_date")
              if (!hasFromDate) {
                val stableBoundarySeconds = APIUtil.getPropsAsIntValue("MappedMetrics.stable.boundary.seconds", 600)
                val defaultFromDate = new java.util.Date(System.currentTimeMillis() - ((stableBoundarySeconds - 1) * 1000L))
                val dateStr = APIUtil.DateWithMsFormat.format(defaultFromDate)
                HTTPParam("from_date", List(dateStr)) :: httpParams
              } else {
                httpParams
              }
            }
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParamsWithDefault, callContext)
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams))
            _ <- Future {
              if (metrics.isEmpty) {
                logger.warn(s"getMetrics returned empty list. Query params: $obpQueryParams, URL: ${cc.url}")
              }
            }
          } yield {
            (JSONFactory510.createMetricsJson(metrics), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAggregateMetrics,
      implementedInApiVersion,
      nameOf(getAggregateMetrics),
      "GET",
      "/management/aggregate-metrics",
      "Get Aggregate Metrics",
      s"""Returns aggregate metrics on api usage eg. total count, response time (in ms), etc.
         |
         |require CanReadAggregateMetrics role
         |
         |**NOTE: Automatic from_date Default**
         |
         |If you do not provide a `from_date` parameter, this endpoint will automatically set it to:
         |**now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes ago**
         |
         |This prevents accidentally querying all metrics since Unix Epoch and ensures reasonable response times.
         |For historical/reporting queries, always explicitly specify your desired `from_date`.
         |
         |**IMPORTANT: Smart Caching & Performance**
         |
         |This endpoint uses intelligent two-tier caching to optimize performance:
         |
         |**Stable Data Cache (Long TTL):**
         |- Metrics older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600")} seconds (${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} minutes) are considered immutable/stable
         |- These are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400")} seconds (${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours)
         |- Used when your query's from_date is older than the stable boundary
         |
         |**Recent Data Cache (Short TTL):**
         |- Recent metrics (within the stable boundary) are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds
         |- Used when your query includes recent data or has no from_date
         |
         |**Why from_date matters:**
         |- Queries WITH from_date older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} mins → cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours (fast!)
         |- Queries WITHOUT from_date → cached for only ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds (slower)
         |
         |Should be able to filter on the following fields
         |
         |eg: /management/aggregate-metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5
         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
         |&verb=GET&anon=false&app_name=MapperPostman
         |&include_app_names=API-EXPLORER,API-Manager,SOFI,null&http_status_code=200
         |
         |**IMPORTANT: v6.0.0+ Breaking Change**
         |
         |This version does NOT support the old `exclude_*` parameters:
         |- ❌ `exclude_app_names` - NOT supported (returns error)
         |- ❌ `exclude_url_patterns` - NOT supported (returns error)
         |- ❌ `exclude_implemented_by_partial_functions` - NOT supported (returns error)
         |
         |Use `include_*` parameters instead (all optional):
         |- ✅ `include_app_names` - Optional - include only these apps
         |- ✅ `include_url_patterns` - Optional - include only URLs matching these patterns
         |- ✅ `include_implemented_by_partial_functions` - Optional - include only these functions
         |
         |1 from_date e.g.:from_date=$DateWithMsExampleString
         |   **DEFAULT**: If not provided, automatically set to now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes (keeps queries in recent data zone)
         |   **IMPORTANT**: Including from_date enables long-term caching for historical data queries!
         |
         |2 to_date (defaults to the current date) eg:to_date=$DateWithMsExampleString
         |
         |3 consumer_id  (if null ignore)
         |
         |4 user_id (if null ignore)
         |
         |5 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
         |
         |6 url (if null ignore), note: can not contain '&'.
         |
         |7 app_name (if null ignore)
         |
         |8 implemented_by_partial_function (if null ignore)
         |
         |9 implemented_in_version (if null ignore)
         |
         |10 verb (if null ignore)
         |
         |11 correlation_id (if null ignore)
         |
         |12 include_app_names (if null ignore).eg: &include_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |13 include_url_patterns (if null ignore).you can design you own SQL LIKE pattern. eg: &include_url_patterns=%management/metrics%,%management/aggregate-metrics%
         |
         |14 include_implemented_by_partial_functions (if null ignore).eg: &include_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
         |
         |15 http_status_code (if null ignore) - Filter by HTTP status code. eg: http_status_code=200 returns only successful calls, http_status_code=500 returns server errors
         |
      """.stripMargin,
      EmptyBody,
      aggregateMetricsJSONV300,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMetric, apiTagAggregateMetrics),
      Some(List(canReadAggregateMetrics)))

    lazy val getAggregateMetrics: OBPEndpoint = {
      case "management" :: "aggregate-metrics" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadAggregateMetrics, callContext)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            // Reject old exclude_* parameters in v6.0.0+
            _ <- Future {
              val excludeParams = httpParams.filter(p =>
                p.name == "exclude_app_names" ||
                p.name == "exclude_url_patterns" ||
                p.name == "exclude_implemented_by_partial_functions"
              )
              if (excludeParams.nonEmpty) {
                val paramNames = excludeParams.map(_.name).mkString(", ")
                throw new Exception(s"${ErrorMessages.ExcludeParametersNotSupported} Parameters found: [$paramNames]")
              }
            }
            // If from_date is not provided, set it to now - (stable.boundary - 1 second)
            // This ensures we get recent data with the shorter cache TTL
            httpParamsWithDefault = {
              val hasFromDate = httpParams.exists(p => p.name == "from_date" || p.name == "obp_from_date")
              if (!hasFromDate) {
                val stableBoundarySeconds = APIUtil.getPropsAsIntValue("MappedMetrics.stable.boundary.seconds", 600)
                val defaultFromDate = new java.util.Date(System.currentTimeMillis() - ((stableBoundarySeconds - 1) * 1000L))
                val dateStr = APIUtil.DateWithMsFormat.format(defaultFromDate)
                HTTPParam("from_date", List(dateStr)) :: httpParams
              } else {
                httpParams
              }
            }
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParamsWithDefault, callContext)
            aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams, true) map {
              x => unboxFullOrFail(x, callContext, GetAggregateMetricsError)
            }
            _ <- Future {
              if (aggregateMetrics.isEmpty) {
                logger.warn(s"getAggregateMetrics returned empty list. Query params: $obpQueryParams, URL: ${cc.url}")
              }
            }
          } yield {
            (createAggregateMetricJson(aggregateMetrics), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      directLoginEndpoint,
      implementedInApiVersion,
      nameOf(directLoginEndpoint),
      "POST",
      "/my/logins/direct",
      "Direct Login",
      s"""DirectLogin is a simple authentication flow. You POST your credentials (username, password, and consumer key)
         |to the DirectLogin endpoint and receive a token in return.
         |
         |This is an alias to the DirectLogin endpoint that includes the standard API versioning prefix.
         |
         |This endpoint requires the following headers:
         |- DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY
         |OR
         |- Authorization: DirectLogin username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY
         |
         |Example header:
         |DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=GET-YOUR-OWN-API-KEY-FROM-THE-OBP
         |
         |The token returned can be used as a bearer token in subsequent API calls.
         |
         |""".stripMargin,
      EmptyBody,
      JSONFactory600.createTokenJSON("DirectLoginToken{eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJpYXQiOjE0NTU4OTQyNzYsImV4cCI6MTQ1NTg5Nzg3NiwiYXVkIjoib2JwLWFwaSIsInN1YiI6IjA2Zjc0YjUwLTA5OGYtNDYwNi1hOGNjLTBjNDc5MjAyNmI5ZCIsImNvbnN1bWVyX2tleSI6IjYwNGY3ZTAyNGQ5MWU2MzMwNGMzOGM0YzRmZjc0MjMwZGU5NDk4NTEwNjgxZWNjM2Q5MzViNWQ5MGEwOTI3ODciLCJyb2xlIjoiY2FuX2FjY2Vzc19hcGkifQ.f8xHvXP5fDxo5-LlfTj1OQS9oqHNZfFd7N-WkV2o4Cc}"),
      List(
        InvalidDirectLoginParameters,
        InvalidLoginCredentials,
        InvalidConsumerCredentials,
        UnknownError
      ),
      List(apiTagUser),
      Some(List()))


    lazy val directLoginEndpoint: OBPEndpoint = {
      case "my" :: "logins" :: "direct" :: Nil JsonPost _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (httpCode: Int, message: String, userId: Long) <- DirectLogin.createTokenFuture(DirectLogin.getAllParameters)
            _ <- Future { DirectLogin.grantEntitlementsToUseDynamicEndpointsInSpacesInDirectLogin(userId) }
          } yield {
            if (httpCode == 200) {
              (JSONFactory600.createTokenJSON(message), HttpCode.`201`(cc.callContext))
            } else {
              unboxFullOrFail(Empty, None, message, httpCode)
            }
          }
    }

    staticResourceDocs += ResourceDoc(
      validateUserEmail,
      implementedInApiVersion,
      nameOf(validateUserEmail),
      "POST",
      "/users/email-validation",
      "Validate User Email",
      s"""Validate a user's email address using the token sent via email.
         |
         |This endpoint is called anonymously (no authentication required).
         |
         |When a user signs up and email validation is enabled (authUser.skipEmailValidation=false),
         |they receive an email with a validation link containing a unique token.
         |
         |This endpoint:
         |- Validates the token
         |- Sets the user's validated status to true
         |- Resets the unique ID token (invalidating the link)
         |- Grants default entitlements to the user
         |
         |**Important: This is a single-use token.** Once the email is validated, the token is invalidated.
         |Any subsequent attempts to use the same token will return a 404 error (UserNotFoundByToken or UserAlreadyValidated).
         |
         |The token is a unique identifier (UUID) that was generated when the user was created.
         |
         |Example token from validation email URL:
         |https://your-obp-instance.com/user_mgt/validate_user/a1b2c3d4-e5f6-7890-abcd-ef1234567890
         |
         |In this case, the token would be: a1b2c3d4-e5f6-7890-abcd-ef1234567890
         |
         |""".stripMargin,
      JSONFactory600.ValidateUserEmailJsonV600(
        token = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
      ),
      JSONFactory600.ValidateUserEmailResponseJsonV600(
        user_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
        email = "user@example.com",
        username = "username",
        provider = "https://localhost:8080",
        validated = true,
        message = "Email validated successfully"
      ),
      List(
        InvalidJsonFormat,
        UserNotFoundByToken,
        UserAlreadyValidated,
        UnknownError
      ),
      List(apiTagUser),
      Some(List())
    )

    lazy val validateUserEmail: OBPEndpoint = {
      case "users" :: "email-validation" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ValidateUserEmailJsonV600 ", 400, cc.callContext) {
              json.extract[JSONFactory600.ValidateUserEmailJsonV600]
            }
            token = postedData.token.trim
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Token cannot be empty", cc = cc.callContext) {
              token.nonEmpty
            }
            // Find user by unique ID (the validation token)
            authUser <- Future {
              code.model.dataAccess.AuthUser.findUserByValidationToken(token) match {
                case Full(user) => Full(user)
                case Empty => Empty
                case f: net.liftweb.common.Failure => f
              }
            }
            user <- NewStyle.function.tryons(s"$UserNotFoundByToken Invalid or expired validation token", 404, cc.callContext) {
              authUser.openOrThrowException("User not found")
            }
            // Check if user is already validated
            _ <- Helper.booleanToFuture(s"$UserAlreadyValidated User email is already validated", cc = cc.callContext) {
              !user.validated.get
            }
            // Validate the user and reset the unique ID token
            validatedUser <- Future {
              code.model.dataAccess.AuthUser.validateAndResetToken(user)
            }
            // Grant default entitlements
            _ <- Future {
              code.model.dataAccess.AuthUser.grantDefaultEntitlementsToAuthUser(validatedUser)
            }
          } yield {
            val response = JSONFactory600.ValidateUserEmailResponseJsonV600(
              user_id = validatedUser.user.obj.map(_.userId).getOrElse(""),
              email = validatedUser.email.get,
              username = validatedUser.username.get,
              provider = validatedUser.provider.get,
              validated = validatedUser.validated.get,
              message = "Email validated successfully"
            )
            (response, HttpCode.`200`(cc.callContext))
          }
    }

    // ============================================ GROUP MANAGEMENT ============================================

    staticResourceDocs += ResourceDoc(
      createGroup,
      implementedInApiVersion,
      nameOf(createGroup),
      "POST",
      "/management/groups",
      "Create Group",
      s"""Create a new group of roles.
         |
         |Groups can be either:
         |- System-level (bank_id = null) - requires CanCreateGroupAtAllBanks role
         |- Bank-level (bank_id provided) - requires CanCreateGroupAtOneBank role
         |
         |A group contains a list of role names that can be assigned together.
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      PostGroupJsonV600(
        bank_id = Some("gh.29.uk"),
        group_name = "Teller Group",
        group_description = "Standard teller roles for branch operations",
        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
        is_enabled = true
      ),
      GroupJsonV600(
        group_id = "group-id-123",
        bank_id = Some("gh.29.uk"),
        group_name = "Teller Group",
        group_description = "Standard teller roles for branch operations",
        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
        is_enabled = true
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagGroup),
      Some(List(canCreateGroupAtAllBanks, canCreateGroupAtOneBank))
    )

    lazy val createGroup: OBPEndpoint = {
      case "management" :: "groups" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostGroupJsonV600", 400, callContext) {
              json.extract[PostGroupJsonV600]
            }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidJsonFormat} bank_id and group_name cannot be empty", cc = callContext) {
              postJson.group_name.nonEmpty
            }
            _ <- postJson.bank_id match {
              case Some(bankId) if bankId.nonEmpty =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canCreateGroupAtOneBank :: canCreateGroupAtAllBanks :: Nil, callContext)
              case _ =>
                NewStyle.function.hasEntitlement("", u.userId, canCreateGroupAtAllBanks, callContext)
            }
            group <- Future {
              code.group.GroupTrait.group.vend.createGroup(
                postJson.bank_id.filter(_.nonEmpty),
                postJson.group_name,
                postJson.group_description,
                postJson.list_of_roles,
                postJson.is_enabled
              )
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot create group", 400)
            }
          } yield {
            val response = GroupJsonV600(
              group_id = group.groupId,
              bank_id = group.bankId,
              group_name = group.groupName,
              group_description = group.groupDescription,
              list_of_roles = group.listOfRoles,
              is_enabled = group.isEnabled
            )
            (response, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getGroup,
      implementedInApiVersion,
      nameOf(getGroup),
      "GET",
      "/management/groups/GROUP_ID",
      "Get Group",
      s"""Get a group by its ID.
         |
         |Requires either:
         |- CanGetGroupsAtAllBanks (for any group)
         |- CanGetGroupsAtOneBank (for groups at specific bank)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      GroupJsonV600(
        group_id = "group-id-123",
        bank_id = Some("gh.29.uk"),
        group_name = "Teller Group",
        group_description = "Standard teller roles for branch operations",
        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
        is_enabled = true
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagGroup),
      Some(List(canGetGroupsAtAllBanks, canGetGroupsAtOneBank))
    )

    lazy val getGroup: OBPEndpoint = {
      case "management" :: "groups" :: groupId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            group <- Future {
              code.group.GroupTrait.group.vend.getGroup(groupId)
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
            }
            _ <- group.bankId match {
              case Some(bankId) =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canGetGroupsAtOneBank :: canGetGroupsAtAllBanks :: Nil, callContext)
              case None =>
                NewStyle.function.hasEntitlement("", u.userId, canGetGroupsAtAllBanks, callContext)
            }
          } yield {
            val response = GroupJsonV600(
              group_id = group.groupId,
              bank_id = group.bankId,
              group_name = group.groupName,
              group_description = group.groupDescription,
              list_of_roles = group.listOfRoles,
              is_enabled = group.isEnabled
            )
            (response, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getGroups,
      implementedInApiVersion,
      nameOf(getGroups),
      "GET",
      "/management/groups",
      "Get Groups",
      s"""Get all groups. Optionally filter by bank_id.
         |
         |Query parameters:
         |- bank_id (optional): Filter groups by bank. Use "null" or omit for system-level groups.
         |
         |Requires either:
         |- CanGetGroupsAtAllBanks (for any/all groups)
         |- CanGetGroupsAtOneBank (for groups at specific bank with bank_id parameter)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      GroupsJsonV600(
        groups = List(
          GroupJsonV600(
            group_id = "group-id-123",
            bank_id = Some("gh.29.uk"),
            group_name = "Teller Group",
            group_description = "Standard teller roles",
            list_of_roles = List("CanGetCustomer", "CanGetAccount"),
            is_enabled = true
          )
        )
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagGroup),
      Some(List(canGetGroupsAtAllBanks, canGetGroupsAtOneBank))
    )

    lazy val getGroups: OBPEndpoint = {
      case "management" :: "groups" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            bankIdParam = httpParams.find(_.name == "bank_id").flatMap(_.values.headOption)
            bankIdFilter = bankIdParam match {
              case Some("null") | Some("") => None
              case Some(id) => Some(id)
              case None => None
            }
            _ <- bankIdFilter match {
              case Some(bankId) =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canGetGroupsAtOneBank :: canGetGroupsAtAllBanks :: Nil, callContext)
              case None =>
                NewStyle.function.hasEntitlement("", u.userId, canGetGroupsAtAllBanks, callContext)
            }
            groups <- bankIdFilter match {
              case Some(bankId) =>
                code.group.GroupTrait.group.vend.getGroupsByBankId(Some(bankId)) map {
                  x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get groups", 400)
                }
              case None if bankIdParam.isDefined =>
                code.group.GroupTrait.group.vend.getGroupsByBankId(None) map {
                  x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get groups", 400)
                }
              case None =>
                code.group.GroupTrait.group.vend.getAllGroups() map {
                  x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get groups", 400)
                }
            }
          } yield {
            val response = GroupsJsonV600(
              groups = groups.map(group =>
                GroupJsonV600(
                  group_id = group.groupId,
                  bank_id = group.bankId,
                  group_name = group.groupName,
                  group_description = group.groupDescription,
                  list_of_roles = group.listOfRoles,
                  is_enabled = group.isEnabled
                )
              )
            )
            (response, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateGroup,
      implementedInApiVersion,
      nameOf(updateGroup),
      "PUT",
      "/management/groups/GROUP_ID",
      "Update Group",
      s"""Update a group. All fields are optional.
         |
         |Requires either:
         |- CanUpdateGroupAtAllBanks (for any group)
         |- CanUpdateGroupAtOneBank (for groups at specific bank)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      PutGroupJsonV600(
        group_name = Some("Updated Teller Group"),
        group_description = Some("Updated description"),
        list_of_roles = Some(List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction", "CanGetTransaction")),
        is_enabled = Some(true)
      ),
      GroupJsonV600(
        group_id = "group-id-123",
        bank_id = Some("gh.29.uk"),
        group_name = "Updated Teller Group",
        group_description = "Updated description",
        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction", "CanGetTransaction"),
        is_enabled = true
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagGroup),
      Some(List(canUpdateGroupAtAllBanks, canUpdateGroupAtOneBank))
    )

    lazy val updateGroup: OBPEndpoint = {
      case "management" :: "groups" :: groupId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutGroupJsonV600", 400, callContext) {
              json.extract[PutGroupJsonV600]
            }
            existingGroup <- Future {
              code.group.GroupTrait.group.vend.getGroup(groupId)
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
            }
            _ <- existingGroup.bankId match {
              case Some(bankId) =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canUpdateGroupAtOneBank :: canUpdateGroupAtAllBanks :: Nil, callContext)
              case None =>
                NewStyle.function.hasEntitlement("", u.userId, canUpdateGroupAtAllBanks, callContext)
            }
            updatedGroup <- Future {
              code.group.GroupTrait.group.vend.updateGroup(
                groupId,
                putJson.group_name,
                putJson.group_description,
                putJson.list_of_roles,
                putJson.is_enabled
              )
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update group", 400)
            }
          } yield {
            val response = GroupJsonV600(
              group_id = updatedGroup.groupId,
              bank_id = updatedGroup.bankId,
              group_name = updatedGroup.groupName,
              group_description = updatedGroup.groupDescription,
              list_of_roles = updatedGroup.listOfRoles,
              is_enabled = updatedGroup.isEnabled
            )
            (response, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createUser,
      implementedInApiVersion,
      nameOf(createUser),
      "POST",
      "/users",
      "Create User (v6.0.0)",
      s"""Creates OBP user.
         | No authorisation required.
         |
         | Mimics current webform to Register.
         |
         | Requires username(email), password, first_name, last_name, and email.
         |
         | Optional fields:
         | - validating_application: Optional application name that will validate the user's email (e.g., "LEGACY_PORTAL")
         |   When set to "LEGACY_PORTAL", the validation link will use the API hostname property
         |   When set to any other value or not provided, the validation link will use the portal_external_url property (default behavior)
         |
         | Validation checks performed:
         | - Password must meet strong password requirements (InvalidStrongPasswordFormat error if not)
         | - Username must be unique (409 error if username already exists)
         | - All required fields must be present in valid JSON format
         |
         | Email validation behavior:
         | - Controlled by property 'authUser.skipEmailValidation' (default: false)
         | - When false: User is created with validated=false and a validation email is sent to the user's email address
         | - Validation link domain is determined by validating_application:
         |   * "LEGACY_PORTAL": Uses API hostname property (e.g., https://api.example.com)
         |   * Other/None (default): Uses portal_external_url property (e.g., https://external-portal.example.com)
         | - When true: User is created with validated=true and no validation email is sent
         | - Default entitlements are granted immediately regardless of validation status
         |
         | Note: If email validation is required (skipEmailValidation=false), the user must click the validation link
         | in the email before they can log in, even though entitlements are already granted.
         |
         |""",
      createUserJsonV600,
      userJsonV200,
      List(InvalidJsonFormat, InvalidStrongPasswordFormat, DuplicateUsername, "Error occurred during user creation.", UnknownError),
      List(apiTagUser, apiTagOnboarding))

    lazy val createUser: OBPEndpoint = {
      case "users" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            // STEP 1: Extract and validate JSON structure
            postedData <- NewStyle.function.tryons(ErrorMessages.InvalidJsonFormat, 400, cc.callContext) {
              json.extract[code.api.v6_0_0.CreateUserJsonV600]
            }

            // STEP 2: Validate password strength
            _ <- Helper.booleanToFuture(ErrorMessages.InvalidStrongPasswordFormat, 400, cc.callContext) {
              fullPasswordValidation(postedData.password)
            }

            // STEP 3: Check username uniqueness (returns 409 Conflict if exists)
            _ <- Helper.booleanToFuture(ErrorMessages.DuplicateUsername, 409, cc.callContext) {
              code.model.dataAccess.AuthUser.find(net.liftweb.mapper.By(code.model.dataAccess.AuthUser.username, postedData.username)).isEmpty
            }

            // STEP 4: Create AuthUser object
            userCreated <- Future {
              code.model.dataAccess.AuthUser.create
                .firstName(postedData.first_name)
                .lastName(postedData.last_name)
                .username(postedData.username)
                .email(postedData.email)
                .password(postedData.password)
                .validated(APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false))
            }

            // STEP 5: Validate Lift field validators
            _ <- Helper.booleanToFuture(ErrorMessages.InvalidJsonFormat+userCreated.validate.map(_.msg).mkString(";"), 400, cc.callContext) {
              userCreated.validate.size == 0
            }

            // STEP 6: Save user to database
            savedUser <- NewStyle.function.tryons(ErrorMessages.InvalidJsonFormat, 400, cc.callContext) {
              userCreated.saveMe()
            }

            // STEP 7: Verify save was successful
            _ <- Helper.booleanToFuture(s"$UnknownError Error occurred during user creation.", 400, cc.callContext) {
              userCreated.saved_?
            }
          } yield {
            // STEP 8: Send validation email (if required)
            val skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false)
            if (!skipEmailValidation) {
              // Construct validation link based on validating_application and portal_external_url
              val portalExternalUrl = APIUtil.getPropsValue("portal_external_url")
              
              val emailValidationLink = postedData.validating_application match {
                case Some("LEGACY_PORTAL") =>
                  // Use API hostname with legacy path
                  Constant.HostName + "/" + code.model.dataAccess.AuthUser.validateUserPath.mkString("/") + "/" + java.net.URLEncoder.encode(savedUser.uniqueId.get, "UTF-8")
                case _ =>
                  // If portal_external_url is set, use modern portal path
                  // Otherwise fall back to API hostname with legacy path
                  portalExternalUrl match {
                    case Full(portalUrl) =>
                      // Portal is configured - use modern frontend route
                      portalUrl + "/user-validation?token=" + java.net.URLEncoder.encode(savedUser.uniqueId.get, "UTF-8")
                    case _ =>
                      // No portal configured - fall back to API hostname with legacy path
                      Constant.HostName + "/" + code.model.dataAccess.AuthUser.validateUserPath.mkString("/") + "/" + java.net.URLEncoder.encode(savedUser.uniqueId.get, "UTF-8")
                  }
              }

              val textContent = Some(s"Welcome! Please validate your account by clicking the following link: $emailValidationLink")
              val htmlContent = Some(s"<p>Welcome! Please validate your account by clicking the following link:</p><p><a href='$emailValidationLink'>$emailValidationLink</a></p>")
              val subjectContent = "Sign up confirmation"

              val emailContent = code.api.util.CommonsEmailWrapper.EmailContent(
                from = code.model.dataAccess.AuthUser.emailFrom,
                to = List(savedUser.email.get),
                bcc = code.model.dataAccess.AuthUser.bccEmail.toList,
                subject = subjectContent,
                textContent = textContent,
                htmlContent = htmlContent
              )

              code.api.util.CommonsEmailWrapper.sendHtmlEmail(emailContent)
            }

            // STEP 9: Grant default entitlements
            code.model.dataAccess.AuthUser.grantDefaultEntitlementsToAuthUser(savedUser)

            // STEP 10: Return JSON response
            val json = JSONFactory200.createUserJSONfromAuthUser(userCreated)
            (json, HttpCode.`201`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteEntitlement,
      implementedInApiVersion,
      nameOf(deleteEntitlement),
      "DELETE",
      "/entitlements/ENTITLEMENT_ID",
      "Delete Entitlement",
      s"""Delete Entitlement specified by ENTITLEMENT_ID
         |
         |${userAuthenticationMessage(true)}
         |
         |Requires the $canDeleteEntitlementAtAnyBank role.
         |
         |This endpoint is idempotent - if the entitlement does not exist, it returns 204 No Content.
         |
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        EntitlementCannotBeDeleted,
        UnknownError
      ),
      List(apiTagRole, apiTagUser, apiTagEntitlement),
      Some(List(canDeleteEntitlementAtAnyBank)))

    lazy val deleteEntitlement: OBPEndpoint = {
      case "entitlements" :: entitlementId :: Nil JsonDelete _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            // TODO: This role check may be redundant since role is already specified in ResourceDoc.
            // See ideas/should_fix_role_docs.md for details on removing duplicate role checks.
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteEntitlementAtAnyBank, callContext)
            entitlementBox <- Future(Entitlement.entitlement.vend.getEntitlementById(entitlementId))
            _ <- entitlementBox match {
              case Full(entitlement) =>
                // Entitlement exists - delete it
                Future(Entitlement.entitlement.vend.deleteEntitlement(Some(entitlement))) map {
                  case Full(true) => Full(())
                  case _ => ObpApiFailure(EntitlementCannotBeDeleted, 500, callContext)
                }
              case _ =>
                // Entitlement not found - idempotent delete returns success
                Future.successful(Full(()))
            }
          } yield {
            (EmptyBody, HttpCode.`204`(callContext))
          }
    }

    staticResourceDocs += ResourceDoc(
      getRolesWithEntitlementCountsAtAllBanks,
      implementedInApiVersion,
      nameOf(getRolesWithEntitlementCountsAtAllBanks),
      "GET",
      "/management/roles-with-entitlement-counts",
      "Get Roles with Entitlement Counts",
      s"""Returns all available roles with the count of entitlements that use each role.
         |
         |This endpoint provides statistics about role usage across all banks by counting
         |how many entitlements have been granted for each role.
         |
         |${userAuthenticationMessage(true)}
         |
         |Requires the CanGetRolesWithEntitlementCountsAtAllBanks role.
         |
         |""",
      EmptyBody,
      RolesWithEntitlementCountsJsonV600(
        roles = List(
          RoleWithEntitlementCountJsonV600(
            role = "CanGetCustomer",
            requires_bank_id = true,
            entitlement_count = 5
          ),
          RoleWithEntitlementCountJsonV600(
            role = "CanGetBank",
            requires_bank_id = false,
            entitlement_count = 3
          )
        )
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagRole, apiTagEntitlement),
      Some(List(canGetRolesWithEntitlementCountsAtAllBanks))
    )

    lazy val getRolesWithEntitlementCountsAtAllBanks: OBPEndpoint = {
      case "management" :: "roles-with-entitlement-counts" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            // Get all available roles
            allRoles = ApiRole.availableRoles.sorted

            // Get entitlement counts for each role
            rolesWithCounts <- Future.sequence {
              allRoles.map { role =>
                Entitlement.entitlement.vend.getEntitlementsByRoleFuture(role).map { entitlementsBox =>
                  val count = entitlementsBox.map(_.length).getOrElse(0)
                  (role, count)
                }
              }
            }
          } yield {
            val json = JSONFactory600.createRolesWithEntitlementCountsJson(rolesWithCounts)
            (json, HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteGroup,
      implementedInApiVersion,
      nameOf(deleteGroup),
      "DELETE",
      "/management/groups/GROUP_ID",
      "Delete Group",
      s"""Delete a Group.
         |
         |Requires either:
         |- CanDeleteGroupAtAllBanks (for any group)
         |- CanDeleteGroupAtOneBank (for groups at specific bank)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagGroup),
      Some(List(canDeleteGroupAtAllBanks, canDeleteGroupAtOneBank))
    )

    lazy val deleteGroup: OBPEndpoint = {
      case "management" :: "groups" :: groupId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            existingGroup <- Future {
              code.group.GroupTrait.group.vend.getGroup(groupId)
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
            }
            _ <- existingGroup.bankId match {
              case Some(bankId) =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canDeleteGroupAtOneBank :: canDeleteGroupAtAllBanks :: Nil, callContext)
              case None =>
                NewStyle.function.hasEntitlement("", u.userId, canDeleteGroupAtAllBanks, callContext)
            }
            deleted <- Future {
              code.group.GroupTrait.group.vend.deleteGroup(groupId)
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot delete group", 400)
            }
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      addUserToGroup,
      implementedInApiVersion,
      nameOf(addUserToGroup),
      "POST",
      "/users/USER_ID/group-memberships",
      "Add User to Group",
      s"""Add a user to a group. This will create entitlements for all roles in the group.
         |
         |Each entitlement will have:
         |- group_id set to the group ID
         |- process set to "GROUP_MEMBERSHIP"
         |
         |Requires either:
         |- CanAddUserToGroupAtAllBanks (for any group)
         |- CanAddUserToGroupAtOneBank (for groups at specific bank)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      PostGroupMembershipJsonV600(
        group_id = "group-id-123"
      ),
      GroupMembershipJsonV600(
        group_id = "group-id-123",
        user_id = "user-id-123",
        bank_id = Some("gh.29.uk"),
        group_name = "Teller Group",
        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction")
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagGroup, apiTagUser, apiTagEntitlement),
      Some(List(canAddUserToGroupAtAllBanks, canAddUserToGroupAtOneBank))
    )

    lazy val addUserToGroup: OBPEndpoint = {
      case "users" :: userId :: "group-memberships" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostGroupMembershipJsonV600", 400, callContext) {
              json.extract[PostGroupMembershipJsonV600]
            }
            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            group <- Future {
              code.group.GroupTrait.group.vend.getGroup(postJson.group_id)
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
            }
            _ <- group.bankId match {
              case Some(bankId) =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canAddUserToGroupAtOneBank :: canAddUserToGroupAtAllBanks :: Nil, callContext)
              case None =>
                NewStyle.function.hasEntitlement("", u.userId, canAddUserToGroupAtAllBanks, callContext)
            }
            _ <- Helper.booleanToFuture(failMsg = s"$UnknownError Group is not enabled", 400, callContext) {
              group.isEnabled
            }
            // Get existing entitlements for this user
            existingEntitlements <- Future {
              Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
            }
            // Create entitlements for all roles in the group, skipping duplicates
            _ <- Future.sequence {
              group.listOfRoles.map { roleName =>
                Future {
                  // Check if user already has this role at this bank
                  val alreadyHasRole = existingEntitlements.toOption.exists(_.exists { ent =>
                    ent.roleName == roleName && ent.bankId == group.bankId.getOrElse("")
                  })
                  
                  if (!alreadyHasRole) {
                    Entitlement.entitlement.vend.addEntitlement(
                      group.bankId.getOrElse(""),
                      userId,
                      roleName,
                      "manual",
                      None,
                      Some(postJson.group_id),
                      Some("GROUP_MEMBERSHIP")
                    )
                  }
                }
              }
            }
          } yield {
            val response = GroupMembershipJsonV600(
              group_id = group.groupId,
              user_id = userId,
              bank_id = group.bankId,
              group_name = group.groupName,
              list_of_roles = group.listOfRoles
            )
            (response, HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getUserGroupMemberships,
      implementedInApiVersion,
      nameOf(getUserGroupMemberships),
      "GET",
      "/users/USER_ID/group-memberships",
      "Get User's Group Memberships",
      s"""Get all groups a user is a member of.
         |
         |Returns groups where the user has entitlements with process = "GROUP_MEMBERSHIP".
         |
         |Requires either:
         |- CanGetUserGroupMembershipsAtAllBanks (for any user)
         |- CanGetUserGroupMembershipsAtOneBank (for users at specific bank)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      GroupMembershipsJsonV600(
        group_memberships = List(
          GroupMembershipJsonV600(
            group_id = "group-id-123",
            user_id = "user-id-123",
            bank_id = Some("gh.29.uk"),
            group_name = "Teller Group",
            list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction")
          )
        )
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagGroup, apiTagUser, apiTagEntitlement),
      Some(List(canGetUserGroupMembershipsAtAllBanks, canGetUserGroupMembershipsAtOneBank))
    )

    lazy val getUserGroupMemberships: OBPEndpoint = {
      case "users" :: userId :: "group-memberships" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            // Get all entitlements for this user that came from groups
            entitlements <- Future {
              Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
            }
            groupEntitlements = entitlements.toOption.getOrElse(List.empty).filter(_.process == Some("GROUP_MEMBERSHIP"))
            // Get unique group IDs
            groupIds = groupEntitlements.flatMap(_.groupId).distinct
            // Check permissions for each bank
            _ <- Future.sequence {
              groupIds.flatMap { groupId =>
                // Get the group to find its bank_id
                code.group.GroupTrait.group.vend.getGroup(groupId).toOption.map { group =>
                  group.bankId match {
                    case Some(bankId) =>
                      NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canGetUserGroupMembershipsAtOneBank :: canGetUserGroupMembershipsAtAllBanks :: Nil, callContext)
                    case None =>
                      NewStyle.function.hasEntitlement("", u.userId, canGetUserGroupMembershipsAtAllBanks, callContext)
                  }
                }
              }
            }
            // Get full group details
            groups <- Future.sequence {
              groupIds.map { groupId =>
                Future {
                  code.group.GroupTrait.group.vend.getGroup(groupId)
                }
              }
            }
            validGroups = groups.flatten
          } yield {
            val memberships = validGroups.map { group =>
              GroupMembershipJsonV600(
                group_id = group.groupId,
                user_id = userId,
                bank_id = group.bankId,
                group_name = group.groupName,
                list_of_roles = group.listOfRoles
              )
            }
            (GroupMembershipsJsonV600(memberships), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      removeUserFromGroup,
      implementedInApiVersion,
      nameOf(removeUserFromGroup),
      "DELETE",
      "/users/USER_ID/group-memberships/GROUP_ID",
      "Remove User from Group",
      s"""Remove a user from a group. This will delete all entitlements that were created by this group membership.
         |
         |Only removes entitlements with:
         |- group_id matching GROUP_ID
         |- process = "GROUP_MEMBERSHIP"
         |
         |Requires either:
         |- CanRemoveUserFromGroupAtAllBanks (for any group)
         |- CanRemoveUserFromGroupAtOneBank (for groups at specific bank)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagGroup, apiTagUser, apiTagEntitlement),
      Some(List(canRemoveUserFromGroupAtAllBanks, canRemoveUserFromGroupAtOneBank))
    )

    lazy val removeUserFromGroup: OBPEndpoint = {
      case "users" :: userId :: "group-memberships" :: groupId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            group <- Future {
              code.group.GroupTrait.group.vend.getGroup(groupId)
            } map {
              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
            }
            _ <- group.bankId match {
              case Some(bankId) =>
                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canRemoveUserFromGroupAtOneBank :: canRemoveUserFromGroupAtAllBanks :: Nil, callContext)
              case None =>
                NewStyle.function.hasEntitlement("", u.userId, canRemoveUserFromGroupAtAllBanks, callContext)
            }
            // Get all entitlements for this user from this group
            entitlements <- Future {
              Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
            }
            groupEntitlements = entitlements.toOption.getOrElse(List.empty).filter(e => 
              e.groupId == Some(groupId) && e.process == Some("GROUP_MEMBERSHIP")
            )
            // Delete all entitlements from this group
            _ <- Future.sequence {
              groupEntitlements.map { entitlement =>
                Future {
                  Entitlement.entitlement.vend.deleteEntitlement(Full(entitlement))
                }
              }
            }
          } yield {
            (Full(true), HttpCode.`204`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getSystemViews,
      implementedInApiVersion,
      nameOf(getSystemViews),
      "GET",
      "/management/system-views",
      "Get System Views",
      s"""Get all system views.
         |
         |System views are predefined views that apply to all accounts, such as:
         |- owner
         |- accountant
         |- auditor
         |- standard
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      ViewsJsonV500(List()),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystemView, apiTagView),
      Some(List(canGetSystemViews))
    )

    lazy val getSystemViews: OBPEndpoint = {
      case "management" :: "system-views" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            views <- Views.views.vend.getSystemViews()
          } yield {
            (JSONFactory500.createViewsJsonV500(views), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getSystemViewById,
      implementedInApiVersion,
      nameOf(getSystemViewById),
      "GET",
      "/management/system-views/VIEW_ID",
      "Get System View",
      s"""Get a single system view by its ID.
         |
         |System views are predefined views that apply to all accounts, such as:
         |- owner
         |- accountant
         |- auditor
         |- standard
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      ViewJsonV500(
        id = "owner",
        short_name = "Owner",
        description = "The owner of the account. Has full privileges.",
        metadata_view = "owner",
        is_public = false,
        is_system = true,
        is_firehose = Some(false),
        alias = "private",
        hide_metadata_if_alias_used = false,
        can_grant_access_to_views = List("owner", "accountant"),
        can_revoke_access_to_views = List("owner", "accountant"),
        can_add_comment = true,
        can_add_corporate_location = true,
        can_add_image = true,
        can_add_image_url = true,
        can_add_more_info = true,
        can_add_open_corporates_url = true,
        can_add_physical_location = true,
        can_add_private_alias = true,
        can_add_public_alias = true,
        can_add_tag = true,
        can_add_url = true,
        can_add_where_tag = true,
        can_delete_comment = true,
        can_add_counterparty = true,
        can_delete_corporate_location = true,
        can_delete_image = true,
        can_delete_physical_location = true,
        can_delete_tag = true,
        can_delete_where_tag = true,
        can_edit_owner_comment = true,
        can_see_bank_account_balance = true,
        can_query_available_funds = true,
        can_see_bank_account_bank_name = true,
        can_see_bank_account_currency = true,
        can_see_bank_account_iban = true,
        can_see_bank_account_label = true,
        can_see_bank_account_national_identifier = true,
        can_see_bank_account_number = true,
        can_see_bank_account_owners = true,
        can_see_bank_account_swift_bic = true,
        can_see_bank_account_type = true,
        can_see_comments = true,
        can_see_corporate_location = true,
        can_see_image_url = true,
        can_see_images = true,
        can_see_more_info = true,
        can_see_open_corporates_url = true,
        can_see_other_account_bank_name = true,
        can_see_other_account_iban = true,
        can_see_other_account_kind = true,
        can_see_other_account_metadata = true,
        can_see_other_account_national_identifier = true,
        can_see_other_account_number = true,
        can_see_other_account_swift_bic = true,
        can_see_owner_comment = true,
        can_see_physical_location = true,
        can_see_private_alias = true,
        can_see_public_alias = true,
        can_see_tags = true,
        can_see_transaction_amount = true,
        can_see_transaction_balance = true,
        can_see_transaction_currency = true,
        can_see_transaction_description = true,
        can_see_transaction_finish_date = true,
        can_see_transaction_metadata = true,
        can_see_transaction_other_bank_account = true,
        can_see_transaction_start_date = true,
        can_see_transaction_this_bank_account = true,
        can_see_transaction_type = true,
        can_see_url = true,
        can_see_where_tag = true,
        can_see_bank_routing_scheme = true,
        can_see_bank_routing_address = true,
        can_see_bank_account_routing_scheme = true,
        can_see_bank_account_routing_address = true,
        can_see_other_bank_routing_scheme = true,
        can_see_other_bank_routing_address = true,
        can_see_other_account_routing_scheme = true,
        can_see_other_account_routing_address = true,
        can_add_transaction_request_to_own_account = true,
        can_add_transaction_request_to_any_account = true,
        can_see_bank_account_credit_limit = true,
        can_create_direct_debit = true,
        can_create_standing_order = true
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        SystemViewNotFound,
        UnknownError
      ),
      List(apiTagSystemView, apiTagView),
      Some(List(canGetSystemViews))
    )

    lazy val getSystemViewById: OBPEndpoint = {
      case "management" :: "system-views" :: viewId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            view <- ViewNewStyle.systemView(ViewId(viewId), callContext)
          } yield {
            (JSONFactory500.createViewJsonV500(view), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createCustomViewManagement,
      implementedInApiVersion,
      nameOf(createCustomViewManagement),
      "POST",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Create Custom View (Management)",
      s"""Create a custom view on a bank account via management endpoint.
         |
         |This is a **management endpoint** that requires the `CanCreateCustomView` role (entitlement).
         |
         |This endpoint provides a simpler, role-based authorization model compared to the original 
         |v3.0.0 endpoint which requires view-level permissions. Use this endpoint when you want to 
         |grant view creation ability through direct role assignment rather than through view access.
         |
         |For the original endpoint that checks account-level view permissions, see:
         |POST /obp/v3.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views
         |
         |${userAuthenticationMessage(true)}
         |
         |The 'alias' field in the JSON can take one of three values:
         |
         | * _public_: to use the public alias if there is one specified for the other account.
         | * _private_: to use the private alias if there is one specified for the other account.
         |
         | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
         |
         | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
         |
         | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
         |
         | You MUST use a leading _ (underscore) in the view name because other view names are reserved for OBP [system views](/index#group-View-System).
         |
         |""".stripMargin,
      createViewJsonV300,
      viewJsonV300,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        InvalidCustomViewFormat,
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagView, apiTagAccount),
      Some(List(canCreateCustomView))
    )

    lazy val createCustomViewManagement: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            createViewJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CreateViewJson ", 400, callContext) {
              json.extract[CreateViewJson]
            }
            //customer views are started with `_`, eg _life, _work, and System views start with letter, eg: owner
            _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat + s"Current view_name (${createViewJson.name})", cc = callContext) {
              isValidCustomViewName(createViewJson.name)
            }
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            (view, callContext) <- ViewNewStyle.createCustomView(BankIdAccountId(bankId, accountId), createViewJson, callContext)
          } yield {
            (JSONFactory300.createViewJSON(view), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomViews,
      implementedInApiVersion,
      nameOf(getCustomViews),
      "GET",
      "/management/custom-views",
      "Get Custom Views",
      s"""Get all custom views.
         |
         |Custom views are user-created views with names starting with underscore (_), such as:
         |- _work
         |- _personal
         |- _audit
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      ViewsJsonV500(List()),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagView, apiTagSystemView),
      Some(List(canGetCustomViews))
    )

    lazy val getCustomViews: OBPEndpoint = {
      case "management" :: "custom-views" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            customViews <- Future { ViewDefinition.getCustomViews() }
          } yield {
            (JSONFactory500.createViewsJsonV500(customViews), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      resetPasswordUrl,
      implementedInApiVersion,
      nameOf(resetPasswordUrl),
      "POST",
      "/management/user/reset-password-url",
      "Create Password Reset URL and Send Email",
      s"""Create a password reset URL for a user and automatically send it via email.
         |
         |This endpoint generates a password reset URL and sends it to the user's email address.
         |
         |${userAuthenticationMessage(true)}
         |
         |Behavior:
         |- Generates a unique password reset token
         |- Creates a reset URL using the portal_external_url property (falls back to API hostname)
         |- Sends an email to the user with the reset link
         |- Returns the reset URL in the response for logging/tracking purposes
         |
         |Required fields:
         |- username: The user's username (typically email)
         |- email: The user's email address (must match username)
         |- user_id: The user's UUID
         |
         |The user must exist and be validated before a reset URL can be generated.
         |
         |Email configuration must be set up correctly for email delivery to work.
         |
         |""".stripMargin,
      PostResetPasswordUrlJsonV600(
        "user@example.com",
        "user@example.com",
        "74a8ebcc-10e4-4036-bef3-9835922246bf"
      ),
      ResetPasswordUrlJsonV600(
        "https://api.example.com/user_mgt/reset_password/QOL1CPNJPCZ4BRMPX3Z01DPOX1HMGU3L"
      ),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canCreateResetPasswordUrl))
    )

    lazy val resetPasswordUrl: OBPEndpoint = {
      case "management" :: "user" :: "reset-password-url" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- Helper.booleanToFuture(
              failMsg = ErrorMessages.NotAllowedEndpoint,
              cc = callContext
            ) {
              APIUtil.getPropsAsBoolValue("ResetPasswordUrlEnabled", false)
            }
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[PostResetPasswordUrlJsonV600]}",
              400,
              callContext
            ) {
              json.extract[PostResetPasswordUrlJsonV600]
            }
            // Find the AuthUser
            authUserBox <- Future {
              code.model.dataAccess.AuthUser.find(
                net.liftweb.mapper.By(code.model.dataAccess.AuthUser.username, postedData.username)
              )
            }
            authUser <- NewStyle.function.tryons(
              s"$UnknownError User not found or validation failed",
              400,
              callContext
            ) {
              authUserBox match {
                case Full(user) if user.validated.get && user.email.get == postedData.email =>
                  // Verify user_id matches
                  Users.users.vend.getUserByUserId(postedData.user_id) match {
                    case Full(resourceUser) if resourceUser.name == postedData.username && 
                                                 resourceUser.emailAddress == postedData.email =>
                      user
                    case _ => throw new Exception("User ID does not match username and email")
                  }
                case _ => throw new Exception("User not found, not validated, or email mismatch")
              }
            }
          } yield {
            // Explicitly type the user to ensure proper method resolution
            val user: code.model.dataAccess.AuthUser = authUser
            
            // Generate new reset token
            // Reset the unique ID token by generating a new random value (32 chars, no hyphens)
            user.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
            user.save
            
            // Construct reset URL using portal_hostname
            // Get the unique ID value for the reset token URL
            val resetPasswordLink = APIUtil.getPropsValue("portal_external_url", Constant.HostName) + 
              "/user_mgt/reset_password/" + 
              java.net.URLEncoder.encode(user.uniqueId.get, "UTF-8")
            
            // Send email using CommonsEmailWrapper (like createUser does)
            val textContent = Some(s"Please use the following link to reset your password: $resetPasswordLink")
            val htmlContent = Some(s"<p>Please use the following link to reset your password:</p><p><a href='$resetPasswordLink'>$resetPasswordLink</a></p>")
            val subjectContent = "Reset your password - " + user.username.get
            
            val emailContent = code.api.util.CommonsEmailWrapper.EmailContent(
              from = code.model.dataAccess.AuthUser.emailFrom,
              to = List(user.email.get),
              bcc = code.model.dataAccess.AuthUser.bccEmail.toList,
              subject = subjectContent,
              textContent = textContent,
              htmlContent = htmlContent
            )
            
            code.api.util.CommonsEmailWrapper.sendHtmlEmail(emailContent)
            
            (
              ResetPasswordUrlJsonV600(resetPasswordLink),
              HttpCode.`201`(callContext)
            )
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getWebUiProp,
      implementedInApiVersion,
      nameOf(getWebUiProp),
      "GET",
      "/webui-props/WEBUI_PROP_NAME",
      "Get WebUiProp by Name",
      s"""
         |
         |Get a single WebUiProp by name.
         |
         |Properties with names starting with "webui_" can be stored in the database and managed via API.
         |
         |**Data Sources:**
         |
         |1. **Explicit WebUiProps (Database)**: Custom values created/updated via the API and stored in the database.
         |
         |2. **Implicit WebUiProps (Configuration File)**: Default values defined in the `sample.props.template` configuration file.
         |
         |**Response Fields:**
         |
         |* `name`: The property name
         |* `value`: The property value
         |* `webUiPropsId` (optional): UUID for database props, omitted for config props
         |* `source`: Either "database" (editable via API) or "config" (read-only from config file)
         |
         |**Query Parameter:**
         |
         |* `active` (optional, boolean string, default: "false")
         |  - If `active=false` or omitted: Returns only explicit prop from the database (source="database")
         |  - If `active=true`: Returns explicit prop from database, or if not found, returns implicit (default) prop from configuration file (source="config")
         |
         |**Examples:**
         |
         |Get database-stored prop only:
         |${getObpApiRoot}/v6.0.0/webui-props/webui_api_explorer_url
         |
         |Get database prop or fallback to default:
         |${getObpApiRoot}/v6.0.0/webui-props/webui_api_explorer_url?active=true
         |
         |""",
      EmptyBody,
      WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"), Some("config")),
      List(
        WebUiPropsNotFoundByName,
        UnknownError
      ),
      List(apiTagWebUiProps)
    )
    lazy val getWebUiProp: OBPEndpoint = {
      case "webui-props" :: webUiPropName :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          logger.info(s"========== GET /obp/v6.0.0/webui-props/$webUiPropName (SINGLE PROP) called ==========")
          val active = ObpS.param("active").getOrElse("false")
          for {
            invalidMsg <- Future(s"""$InvalidFilterParameterFormat `active` must be a boolean, but current `active` value is: ${active} """)
            isActived <- NewStyle.function.tryons(invalidMsg, 400, cc.callContext) {
              active.toBoolean
            }
            explicitWebUiProps <- Future{ MappedWebUiPropsProvider.getAll() }
            explicitProp = explicitWebUiProps.find(_.name == webUiPropName)
            result <- {
              explicitProp match {
                case Some(prop) =>
                  // Found in database
                  Future.successful(WebUiPropsCommons(prop.name, prop.value, prop.webUiPropsId, source = Some("database")))
                case None if isActived =>
                  // Not in database, check implicit props if active=true
                  val implicitWebUiProps = getWebUIPropsPairs.map(webUIPropsPairs =>
                    WebUiPropsCommons(webUIPropsPairs._1, webUIPropsPairs._2, webUiPropsId = Some("default"), source = Some("config"))
                  )
                  val implicitProp = implicitWebUiProps.find(_.name == webUiPropName)
                  implicitProp match {
                    case Some(prop) => Future.successful(prop)
                    case None => Future.failed(new Exception(s"$WebUiPropsNotFoundByName Current WEBUI_PROP_NAME($webUiPropName)"))
                  }
                case None =>
                  // Not in database and active=false
                  Future.failed(new Exception(s"$WebUiPropsNotFoundByName Current WEBUI_PROP_NAME($webUiPropName)"))
              }
            }
          } yield {
            (result, HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getWebUiProps,
      implementedInApiVersion,
      nameOf(getWebUiProps),
      "GET",
      "/webui-props",
      "Get WebUiProps",
      s"""
         |
         |Get WebUiProps - properties that configure the Web UI behavior and appearance.
         |
         |Properties with names starting with "webui_" can be stored in the database and managed via API.
         |
         |**Data Sources:**
         |
         |1. **Explicit WebUiProps (Database)**: Custom values created/updated via the API and stored in the database.
         |
         |2. **Implicit WebUiProps (Configuration File)**: Default values defined in the `sample.props.template` configuration file.
         |
         |**Response Fields:**
         |
         |* `name`: The property name
         |* `value`: The property value
         |* `webUiPropsId` (optional): UUID for database props, omitted for config props
         |* `source`: Either "database" (editable via API) or "config" (read-only from config file)
         |
         |**Query Parameter:**
         |
         |* `what` (optional, string, default: "active")
         |  - `active`: Returns one value per property name
         |    - If property exists in database: returns database value (source="database")
         |    - If property only in config file: returns config default value (source="config")
         |  - `database`: Returns ONLY properties explicitly stored in the database (source="database")
         |  - `config`: Returns ONLY default properties from configuration file (source="config")
         |
         |**Examples:**
         |
         |Get active props (database overrides config, one value per prop):
         |${getObpApiRoot}/v6.0.0/webui-props
         |${getObpApiRoot}/v6.0.0/webui-props?what=active
         |
         |Get only database-stored props:
         |${getObpApiRoot}/v6.0.0/webui-props?what=database
         |
         |Get only default props from configuration:
         |${getObpApiRoot}/v6.0.0/webui-props?what=config
         |
         |For more details about WebUI Props, including how to set config file defaults and precedence order, see ${Glossary.getGlossaryItemLink("webui_props")}.
         |
         |""",
      EmptyBody,
      ListResult(
        "webui_props",
        (List(WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"), Some("database"))))
      )
      ,
      List(
        UnknownError
      ),
      List(apiTagWebUiProps)
    )


    lazy val getWebUiProps: OBPEndpoint = {
      case "webui-props":: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val what = ObpS.param("what").getOrElse("active")
          logger.info(s"========== GET /obp/v6.0.0/webui-props (ALL PROPS) called with what=$what ==========")
          for {
            callContext <- Future.successful(cc.callContext)
            _ <- NewStyle.function.tryons(s"""$InvalidFilterParameterFormat `what` must be one of: active, database, config. Current value: $what""", 400, callContext) {
              what match {
                case "active" | "database" | "config" => true
                case _ => false
              }
            }
            explicitWebUiProps <- Future{ MappedWebUiPropsProvider.getAll() }
            explicitWebUiPropsWithSource = explicitWebUiProps.map(prop => WebUiPropsCommons(prop.name, prop.value, prop.webUiPropsId, source = Some("database")))
            implicitWebUiProps = getWebUIPropsPairs.map(webUIPropsPairs=>WebUiPropsCommons(webUIPropsPairs._1, webUIPropsPairs._2, webUiPropsId = Some("default"), source = Some("config")))
            result = what match {
              case "database" => 
                // Return only database props
                explicitWebUiPropsWithSource
              case "config" =>
                // Return only config file props
                implicitWebUiProps.distinct
              case "active" =>
                // Return one value per prop: database value if exists, otherwise config value
                val databasePropNames = explicitWebUiPropsWithSource.map(_.name).toSet
                val configPropsNotInDatabase = implicitWebUiProps.distinct.filterNot(prop => databasePropNames.contains(prop.name))
                explicitWebUiPropsWithSource ++ configPropsNotInDatabase
            }
          } yield {
            logger.info(s"========== GET /obp/v6.0.0/webui-props returning ${result.size} records ==========")
            result.foreach { prop =>
              logger.info(s"  - name: ${prop.name}, value: ${prop.value}, webUiPropsId: ${prop.webUiPropsId}")
            }
            logger.info(s"========== END GET /obp/v6.0.0/webui-props ==========")
            (ListResult("webui_props", result), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createOrUpdateWebUiProps,
      implementedInApiVersion,
      nameOf(createOrUpdateWebUiProps),
      "PUT",
      "/management/webui_props/WEBUI_PROP_NAME",
      "Create or Update WebUiProps",
      s"""Create or Update a WebUiProps.
         |
         |${userAuthenticationMessage(true)}
         |
         |This endpoint is idempotent - it will create the property if it doesn't exist, or update it if it does.
         |The property is identified by WEBUI_PROP_NAME in the URL path.
         |
         |Explanation of Fields:
         |
         |* WEBUI_PROP_NAME in URL path (must start with `webui_`, contain only alphanumeric characters, underscore, and dot, not exceed 255 characters, and will be converted to lowercase)
         |* value is required String value in request body
         |
         |The line break and double quotations should be escaped, example:
         |
         |```
         |
         |{"name": "webui_some", "value": "this value
         |have "line break" and double quotations."}
         |
         |```
         |should be escaped like this:
         |
         |```
         |
         |{"name": "webui_some", "value": "this value\\nhave \\"line break\\" and double quotations."}
         |
         |```
         |
         |Insert image examples:
         |
         |```
         |// set width=100 and height=50
         |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =100x50)"}
         |
         |// only set height=50
         |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =x50)"}
         |
         |// only width=20%
         |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =20%x)"}
         |
         |```
         |
         |""",
      WebUiPropsPutJsonV600("https://apiexplorer.openbankproject.com"),
      WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("some-web-ui-props-id")),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        InvalidWebUiProps,
        UnknownError
      ),
      List(apiTagWebUiProps),
      Some(List(canCreateWebUiProps))
    )

    lazy val createOrUpdateWebUiProps: OBPEndpoint = {
      case "management" :: "webui_props" :: webUiPropName :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateWebUiProps, callContext)
            // Convert name to lowercase
            webUiPropNameLower = webUiPropName.toLowerCase
            invalidMsg = s"""$InvalidWebUiProps name must start with webui_, but current name is: ${webUiPropNameLower} """
            _ <- NewStyle.function.tryons(invalidMsg, 400, callContext) {
              require(webUiPropNameLower.startsWith("webui_"))
            }
            invalidCharsMsg = s"""$InvalidWebUiProps name must contain only alphanumeric characters, underscore, and dot. Current name: ${webUiPropNameLower} """
            _ <- NewStyle.function.tryons(invalidCharsMsg, 400, callContext) {
              require(webUiPropNameLower.matches("^[a-zA-Z0-9_.]+$"))
            }
            invalidLengthMsg = s"""$InvalidWebUiProps name must not exceed 255 characters. Current length: ${webUiPropNameLower.length} """
            _ <- NewStyle.function.tryons(invalidLengthMsg, 400, callContext) {
              require(webUiPropNameLower.length <= 255)
            }
            // Check if resource already exists to determine status code
            existingProp <- Future { MappedWebUiPropsProvider.getByName(webUiPropNameLower) }
            resourceExists = existingProp.isDefined
            failMsg = s"$InvalidJsonFormat The Json body should contain a value field"
            valueJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[WebUiPropsPutJsonV600]
            }
            webUiPropsData = WebUiPropsCommons(webUiPropNameLower, valueJson.value)
            Full(webUiProps) <- Future { MappedWebUiPropsProvider.createOrUpdate(webUiPropsData) }
          } yield {
            val commonsData: WebUiPropsCommons = webUiProps
            val statusCode = if (resourceExists) HttpCode.`200`(callContext) else HttpCode.`201`(callContext)
            (commonsData, statusCode)
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteWebUiProps,
      implementedInApiVersion,
      nameOf(deleteWebUiProps),
      "DELETE",
      "/management/webui_props/WEBUI_PROP_NAME",
      "Delete WebUiProps",
      s"""Delete a WebUiProps specified by WEBUI_PROP_NAME.
         |
         |${userAuthenticationMessage(true)}
         |
         |The property name will be converted to lowercase before deletion.
         |
         |Returns 204 No Content on successful deletion.
         |
         |This endpoint is idempotent - if the property does not exist, it still returns 204 No Content.
         |
         |Requires the $canDeleteWebUiProps role.
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidWebUiProps,
        UnknownError
      ),
      List(apiTagWebUiProps),
      Some(List(canDeleteWebUiProps))
    )

    lazy val deleteWebUiProps: OBPEndpoint = {
      case "management" :: "webui_props" :: webUiPropName :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteWebUiProps, callContext)
            // Convert name to lowercase
            webUiPropNameLower = webUiPropName.toLowerCase
            invalidMsg = s"""$InvalidWebUiProps name must start with webui_, but current name is: ${webUiPropNameLower} """
            _ <- NewStyle.function.tryons(invalidMsg, 400, callContext) {
              require(webUiPropNameLower.startsWith("webui_"))
            }
            invalidCharsMsg = s"""$InvalidWebUiProps name must contain only alphanumeric characters, underscore, and dot. Current name: ${webUiPropNameLower} """
            _ <- NewStyle.function.tryons(invalidCharsMsg, 400, callContext) {
              require(webUiPropNameLower.matches("^[a-zA-Z0-9_.]+$"))
            }
            invalidLengthMsg = s"""$InvalidWebUiProps name must not exceed 255 characters. Current length: ${webUiPropNameLower.length} """
            _ <- NewStyle.function.tryons(invalidLengthMsg, 400, callContext) {
              require(webUiPropNameLower.length <= 255)
            }
            // Check if resource exists
            existingProp <- Future { MappedWebUiPropsProvider.getByName(webUiPropNameLower) }
            _ <- existingProp match {
              case Full(prop) =>
                // Property exists - delete it
                Future { MappedWebUiPropsProvider.delete(prop.webUiPropsId.getOrElse("")) } map {
                  case Full(true) => Full(())
                  case Full(false) => ObpApiFailure(s"$UnknownError Cannot delete WebUI prop", 500, callContext)
                  case Empty => ObpApiFailure(s"$UnknownError Cannot delete WebUI prop", 500, callContext)
                  case Failure(msg, _, _) => ObpApiFailure(msg, 500, callContext)
                }
              case Empty =>
                // Property not found - idempotent delete returns success
                Future.successful(Full(()))
              case Failure(msg, _, _) =>
                Future.failed(new Exception(msg))
            }
          } yield {
            (EmptyBody, HttpCode.`204`(callContext))
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
      s"""Get all System Dynamic Entities with record counts.
         |
         |Each dynamic entity in the response includes a `record_count` field showing how many data records exist for that entity.
         |
         |For more information see ${Glossary.getGlossaryItemLink(
          "Dynamic-Entities"
        )} """,
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
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            dynamicEntities <- Future(
              NewStyle.function.getDynamicEntities(None, false)
            )
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities.sortBy(_.entityName)
            val jObjectsWithCounts = listCommons.map { entity =>
              val recordCount = DynamicData.count(
                By(DynamicData.DynamicEntityName, entity.entityName),
                By(DynamicData.IsPersonalEntity, false),
                if (entity.bankId.isEmpty) NullRef(DynamicData.BankId) else By(DynamicData.BankId, entity.bankId.get)
              )
              entity.jValue.asInstanceOf[JObject] ~ ("record_count" -> recordCount)
            }
            (
              ListResult("dynamic_entities", jObjectsWithCounts),
              HttpCode.`200`(cc.callContext)
            )
          }
      }
    }

    private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
      if (box.isInstanceOf[Failure]) {
        val failure = box.asInstanceOf[Failure]
        // change the internal db column name 'dynamicdataid' to entity's id name
        val msg = failure.msg.replace(
          DynamicData.DynamicDataId.dbColumnName,
          StringUtils.uncapitalize(entityName) + "Id"
        )
        val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
        fullBoxOrException[T](changedMsgFailure)
      }
      box.openOrThrowException(s"$UnknownError ")
    }

    staticResourceDocs += ResourceDoc(
      deleteSystemDynamicEntityCascade,
      implementedInApiVersion,
      nameOf(deleteSystemDynamicEntityCascade),
      "DELETE",
      "/management/system-dynamic-entities/cascade/DYNAMIC_ENTITY_ID",
      "Delete System Level Dynamic Entity Cascade",
      s"""Delete a DynamicEntity specified by DYNAMIC_ENTITY_ID and all its data records.
         |
         |This endpoint performs a cascade delete:
         |1. Deletes all data records associated with the dynamic entity
         |2. Deletes the dynamic entity definition itself
         |
         |Use with caution - this operation cannot be undone.
         |
         |For more information see ${Glossary.getGlossaryItemLink(
          "Dynamic-Entities"
        )}/
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
      Some(List(canDeleteCascadeSystemDynamicEntity))
    )
    lazy val deleteSystemDynamicEntityCascade: OBPEndpoint = {
      case "management" :: "system-dynamic-entities" :: "cascade" :: dynamicEntityId :: Nil JsonDelete _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          deleteDynamicEntityCascadeMethod(None, dynamicEntityId, cc)
      }
    }

    private def deleteDynamicEntityCascadeMethod(
        bankId: Option[String],
        dynamicEntityId: String,
        cc: CallContext
    ) = {
      for {
        // Get the dynamic entity
        (entity, _) <- NewStyle.function.getDynamicEntityById(
          bankId,
          dynamicEntityId,
          cc.callContext
        )
        // Get all data records for this entity
        (box, _) <- NewStyle.function.invokeDynamicConnector(
          GET_ALL,
          entity.entityName,
          None,
          None,
          entity.bankId,
          None,
          None,
          false,
          cc.callContext
        )
        resultList: JArray = unboxResult(
          box.asInstanceOf[Box[JArray]],
          entity.entityName
        )
        // Delete all data records
        _ <- Future.sequence {
          resultList.arr.map { record =>
            val idFieldName = DynamicEntityHelper.createEntityId(entity.entityName)
            val recordId = (record \ idFieldName).asInstanceOf[JString].s
            Future {
              DynamicDataProvider.connectorMethodProvider.vend.delete(
                entity.bankId,
                entity.entityName,
                recordId,
                None,
                false
              )
            }
          }
        }
        // Delete the dynamic entity definition
        deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(
          bankId,
          dynamicEntityId
        )
      } yield {
        (deleted, HttpCode.`200`(cc.callContext))
      }
    }

  }
}



object APIMethods600 extends RestHelper with APIMethods600 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations6_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}
