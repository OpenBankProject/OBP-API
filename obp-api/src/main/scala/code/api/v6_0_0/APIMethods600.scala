package code.api.v6_0_0

import code.accountattribute.AccountAttributeX
import code.api.{DirectLogin, ObpApiFailure}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, InvalidDateFormat, InvalidJsonFormat, UnknownError, _}
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, CallContext, DiagnosticDynamicEntityCheck, ErrorMessages, NewStyle, RateLimitingUtil}
import code.api.util.NewStyle.function.extractQueryParams
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0.{JSONFactory310, PostCustomerNumberJsonV310}
import code.api.v4_0_0.CallLimitPostJsonV400
import code.api.v4_0_0.JSONFactory400.createCallsLimitJson
import code.api.v5_0_0.JSONFactory500
import code.api.v5_1_0.PostCustomerLegalNameJsonV510
import code.api.v6_0_0.JSONFactory600.{DynamicEntityDiagnosticsJsonV600, DynamicEntityIssueJsonV600, ReferenceTypeJsonV600, ReferenceTypesJsonV600, createActiveCallLimitsJsonV600, createCallLimitJsonV600, createCurrentUsageJson}
import code.bankconnectors.LocalMappedConnectorInternal
import code.bankconnectors.LocalMappedConnectorInternal._
import code.entitlement.Entitlement
import code.model._
import code.ratelimiting.RateLimitingDI
import code.util.Helper
import code.util.Helper.SILENCE_IS_GOLDEN
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{CustomerAttribute, _}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.{Empty, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{Extraction, JsonParser}
import net.liftweb.json.JsonAST.JValue

import java.text.SimpleDateFormat
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Random


trait APIMethods600 {
  self: RestHelper =>

  val Implementations6_0_0 = new Implementations600()

  class Implementations600 {

    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


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
      bankJson500,
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
            (JSONFactory500.createBankJSON500(success), HttpCode.`201`(callContext))
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
      directLoginEndpoint,
      implementedInApiVersion,
      nameOf(directLoginEndpoint),
      "POST",
      "/my/logins/direct",
      "Direct Login",
      s"""This endpoint allows users to create a DirectLogin token to access the API.
         |
         |DirectLogin is a simple authentication flow. You POST your credentials (username, password, and consumer key)
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

  }
}



object APIMethods600 extends RestHelper with APIMethods600 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations6_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}
