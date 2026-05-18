package code.api.v6_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.{ErrorResponseConverter, RequestScopeConnection, ResourceDocMiddleware}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.ViewNewStyle
import code.api.v2_0_0.JSONFactory200
import code.api.v5_1_0.{Http4s510, JSONFactory510}
import code.api.v6_0_0.JSONFactory600.ScannedApiVersionJsonV600
import code.accountattribute.AccountAttributeX
import code.api.Constant
import code.api.Constant.{PARAM_LOCALE, PARAM_TIMESTAMP}
import code.api.cache.Redis
import code.bankconnectors.{Connector => BankConnector}
import code.bankconnectors.storedprocedure.StoredProcedureUtils
import code.migration.MigrationScriptLogProvider
import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.APIUtil.{createQueriesByHttpParamsFuture, unboxFull, unboxFullOrFail}
import code.api.util.{ApiVersionUtils, CertificateUtil, CommonsEmailWrapper, RateLimitingUtil}
import code.api.v2_0_0.{BasicViewJson, JSONFactory200}
import code.api.v3_0_0.JSONFactory300
import code.abacrule.{AbacRuleEngine, MappedAbacRuleProvider}
import code.api.v3_1_0.PostCustomerNumberJsonV310
import code.api.v4_0_0.CallLimitPostJsonV400
import code.api.v5_1_0.UserAttributesResponseJsonV510
import code.api.v5_1_0.PostCustomerLegalNameJsonV510
import code.api.v5_1_0.UserAttributeJsonV510
import code.api.v6_0_0.JSONFactory600.{createAbacRuleJsonV600, createAbacRulesJsonV600}
import code.api.v6_0_0.JSONFactory600.{GroupEntitlementJsonV600, GroupEntitlementsJsonV600, GroupJsonV600, GroupsJsonV600, PostGroupJsonV600, PutGroupJsonV600}
import code.group.{GroupTrait => GroupT}
import code.ratelimiting.RateLimitingDI
import com.openbankproject.commons.model.enums.UserAttributeType

import java.text.SimpleDateFormat
import java.util.UUID.randomUUID
import code.api.v6_0_0.JSONFactory600.UpdateViewJsonV600
import code.model._
import code.model.dataAccess.AuthUser
import code.users.{Users, DoobieUserQueries}
import code.util.Helper.SILENCE_IS_GOLDEN
import com.openbankproject.commons.dto.GetProductsParam
import code.model.ModeratedTransaction
import com.openbankproject.commons.model.{CreditLimit, CreditRating, CustomerFaceImage}
import net.liftweb.common.{Empty, Failure}
import net.liftweb.http.provider.HTTPParam

import scala.util.Random
import code.metrics.APIMetrics
import code.util.Helper
import code.webuiprops.{MappedWebUiPropsProvider, WebUiPropsCommons}
import code.DynamicData.DynamicData
import code.dynamicEntity.DynamicEntityCommons
import code.entitlement.Entitlement
import code.metadata.tags.Tags
import code.views.Views
import net.liftweb.mapper.{By, NullRef}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, BankIdAccountId, CustomerId, ListResult, ViewId}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.Full
import net.liftweb.json.{Extraction, Formats}
import net.liftweb.json.JsonAST.prettyRender
import org.http4s.{HttpRoutes, Request, Response, Uri}
import org.http4s.dsl.io._

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * v6.0.0 http4s endpoints — Phase 1 in progress.
 *
 * Wire-in into `Http4sApp.baseServices` is performed alongside this object.
 * The v600→v510 bridge (`v600ToV510Bridge`) is intentionally NOT appended to
 * `allRoutes`: unmigrated v6 paths must fall through the http4s chain to the
 * Lift fallback, which still serves the v6 Lift handlers. Adding the bridge
 * would let v6 *overrides* be hijacked into v5.1 handlers (CLAUDE.md →
 * "Bridge-cascade hijack"). The bridge val is kept here so it can be enabled
 * later if the team decides to short-circuit Lift for v6 originals.
 */
object Http4s600 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0
  val versionStatus: String = ApiVersionStatus.BLEEDING_EDGE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations6_0_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // Route: GET /obp/v6.0.0/ and GET /obp/v6.0.0/root
    // Mirrors v6 Lift root — both bare prefix and /root return the same
    // info JSON. Reuses JSONFactory510.getApiInfoJSON because v6's API-info
    // shape is unchanged from v5.1.
    lazy val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> `prefixPath` =>
        Ok(convertAnyToJsonString(
          JSONFactory510.getApiInfoJSON(implementedInApiVersion, versionStatus)
        ))
      case GET -> `prefixPath` / "root" =>
        Ok(convertAnyToJsonString(
          JSONFactory510.getApiInfoJSON(implementedInApiVersion, versionStatus)
        ))
    }

    resourceDocs += ResourceDoc(
      null,
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
        |* Git Commit""".stripMargin,
      EmptyBody,
      apiInfoJson400,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    // Route: GET /obp/v6.0.0/api/versions
    // Returns the list of scanned API versions with `is_active` reflecting
    // current `api_disabled_versions`/`api_enabled_versions` props.
    lazy val getScannedApiVersions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "versions" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future {
            val versions: List[ScannedApiVersionJsonV600] =
              ApiVersion.allScannedApiVersion.asScala.toList
                .filter(v => v.urlPrefix.trim.nonEmpty)
                .map { v =>
                  ScannedApiVersionJsonV600(
                    url_prefix              = v.urlPrefix,
                    api_standard            = v.apiStandard,
                    api_short_version       = v.apiShortVersion,
                    fully_qualified_version = v.fullyQualifiedVersion,
                    is_active               = APIUtil.versionIsAllowed(v)
                  )
                }
            ListResult("scanned_api_versions", versions)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getScannedApiVersions),
      "GET",
      "/api/versions",
      "Get Scanned API Versions",
      """Get all scanned API versions available in this codebase along with their active status.""",
      EmptyBody,
      ListResult(
        "scanned_api_versions",
        List(ScannedApiVersionJsonV600("obp", "OBP", "v6.0.0", "OBPv6.0.0", is_active = true))
      ),
      List(UnknownError),
      apiTagDocumentation :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(getScannedApiVersions)
    )

    // Route: GET /obp/v6.0.0/users/current
    // Auth-only. Returns the logged-in user enriched with entitlements,
    // virtual roles (super_admin / oidc_operator), permissions, and the
    // optional on-behalf-of user when impersonation headers are set.
    lazy val getCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
          } yield {
            val permissions = Views.views.vend.getPermissionForUser(user).toOption
            val virtualRoleNames =
              if (APIUtil.isSuperAdmin(user.userId)) JSONFactory200.superAdminVirtualRoles
              else if (APIUtil.isOidcOperator(user.userId)) JSONFactory200.oidcOperatorVirtualRoles
              else List.empty
            val existingRoleNames = entitlements.map(_.roleName).toSet
            val virtualEntitlements = virtualRoleNames.filterNot(existingRoleNames.contains).map { role =>
              new Entitlement {
                def entitlementId    = ""
                def bankId           = ""
                def userId           = user.userId
                def roleName         = role
                def createdByProcess =
                  if (APIUtil.isSuperAdmin(user.userId)) "super_admin_user_ids"
                  else "oidc_operator_user_ids"
                def entitlementRequestId: Option[String] = None
                def groupId: Option[String]              = None
                def process: Option[String]              = None
              }
            }
            val currentUser = UserV600(user, entitlements ::: virtualEntitlements, permissions)
            val onBehalfOfUser =
              if (cc.onBehalfOfUser.isDefined) {
                val u = cc.onBehalfOfUser.toOption.get
                val ents = Entitlement.entitlement.vend.getEntitlementsByUserId(u.userId)
                  .headOption.toList.flatten
                val perms = Views.views.vend.getPermissionForUser(u).toOption
                Some(UserV600(u, ents, perms))
              } else None
            JSONFactory600.createUserInfoJSON(currentUser, onBehalfOfUser)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCurrentUser),
      "GET",
      "/users/current",
      "Get User (Current)",
      """Get the logged-in user (with entitlements, permissions, virtual roles,
        |and the on-behalf-of user if impersonation headers are set).""".stripMargin,
      EmptyBody,
      userJsonV300,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagUser :: Nil,
      None,
      http4sPartialFunction = Some(getCurrentUser)
    )

    // Route: GET /obp/v6.0.0/banks
    lazy val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (banks, _) <- NewStyle.function.getBanks(Some(cc))
          } yield JSONFactory600.createBanksJsonV600(banks)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      """Get banks on this API instance.
        |Returns a list of banks supported on this server.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(UnknownError),
      apiTagBank :: Nil,
      None,
      http4sPartialFunction = Some(getBanks)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID
    lazy val getBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getBankAttributesByBank(bank.bankId, Some(cc))
          } yield JSONFactory600.createBankJsonV600(bank, attributes)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBank),
      "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID. Returns id, name, logo, website,
        |routings and attributes.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($BankNotFound, UnknownError),
      apiTagBank :: Nil,
      None,
      http4sPartialFunction = Some(getBank)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customers
    lazy val getCustomersAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "customers" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          val bankId = BankId(bankIdStr)
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString,
              List("limit", "offset", "sort_direction"),
              cc.callContext
            )
            customers <- NewStyle.function.getCustomers(bankId, cc.callContext, requestParams)
          } yield JSONFactory600.createCustomersJson(customers.sortBy(_.bankId))
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCustomersAtOneBank),
      "GET",
      "/banks/BANK_ID/customers",
      "Get Customers at Bank",
      """Get Customers at Bank. Returns a list of all customers at the
        |specified bank.""".stripMargin,
      EmptyBody,
      customerJSONsV600,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCustomersAtOneBank)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customers/CUSTOMER_ID
    lazy val getCustomerByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(
              bank.bankId, CustomerId(customerId), callContext
            )
          } yield JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCustomerByCustomerId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID",
      "Get Customer by CUSTOMER_ID",
      """Gets the Customer specified by CUSTOMER_ID.""",
      EmptyBody,
      customerWithAttributesJsonV600,
      List($AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCustomerByCustomerId)
    )

    // Route: GET /obp/v6.0.0/my/banks/BANK_ID/accounts/ACCOUNT_ID/account
    lazy val getCoreAccountByIdV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" / _ / "account" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(
              user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(
              user, BankIdAccountId(account.bankId, account.accountId))
            JSONFactory600.createModeratedCoreAccountJsonV600(moderatedAccount, availableViews)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCoreAccountByIdV600),
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      """Returns core information about the account specified by ACCOUNT_ID
        |including balance, routings and available views.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankAccountNotFound, UnknownError),
      apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(getCoreAccountByIdV600)
    )

    // Route: GET /obp/v6.0.0/my/dynamic-entities
    lazy val getMyDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "dynamic-entities" =>
        EndpointHelpers.withUser(req) { (user, _) =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(user.userId))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities
            JSONFactory600.createMyDynamicEntitiesJson(listCommons)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getMyDynamicEntities),
      "GET",
      "/my/dynamic-entities",
      "Get My Dynamic Entities",
      """Get all Dynamic Entity definitions I created.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(getMyDynamicEntities)
    )

    // Route: GET /obp/v6.0.0/management/system-dynamic-entities
    lazy val getSystemDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system-dynamic-entities" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(None, false))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities.sortBy(_.entityName)
            val entitiesWithCounts = listCommons.map { entity =>
              val recordCount = DynamicData.count(
                By(DynamicData.DynamicEntityName, entity.entityName),
                By(DynamicData.IsPersonalEntity, false),
                if (entity.bankId.isEmpty) NullRef(DynamicData.BankId) else By(DynamicData.BankId, entity.bankId.get)
              )
              (entity, recordCount)
            }
            JSONFactory600.createDynamicEntitiesWithCountJson(entitiesWithCounts)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getSystemDynamicEntities),
      "GET",
      "/management/system-dynamic-entities",
      "Get System Dynamic Entities",
      """Get all system-level Dynamic Entities with record counts.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      Some(canGetSystemLevelDynamicEntities :: Nil),
      http4sPartialFunction = Some(getSystemDynamicEntities)
    )

    // Route: GET /obp/v6.0.0/management/banks/BANK_ID/dynamic-entities
    lazy val getBankLevelDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / bankIdStr / "dynamic-entities" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, _) =>
          for {
            dynamicEntities <- Future(NewStyle.function.getDynamicEntities(Some(bankIdStr), false))
          } yield {
            val listCommons: List[DynamicEntityCommons] = dynamicEntities.sortBy(_.entityName)
            val entitiesWithCounts = listCommons.map { entity =>
              val recordCount = DynamicData.count(
                By(DynamicData.DynamicEntityName, entity.entityName),
                By(DynamicData.IsPersonalEntity, false),
                By(DynamicData.BankId, bankIdStr)
              )
              (entity, recordCount)
            }
            JSONFactory600.createDynamicEntitiesWithCountJson(entitiesWithCounts)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicEntities),
      "GET",
      "/management/banks/BANK_ID/dynamic-entities",
      "Get Bank-Level Dynamic Entities",
      """Get all bank-level Dynamic Entities with record counts for the
        |specified bank.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      Some(canGetBankLevelDynamicEntities :: canGetAnyBankLevelDynamicEntities :: Nil),
      http4sPartialFunction = Some(getBankLevelDynamicEntities)
    )

    // Route: GET /obp/v6.0.0/management/consumers/CONSUMER_ID
    lazy val getConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, cc.callContext)
            currentConsumerCallCounters <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
            date = new java.util.Date()
            (activeRateLimit, rateLimitIds) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumer.consumerId.get, date)
            activeRateLimitsJson = JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(activeRateLimit, rateLimitIds, date)
            callCountersJson = JSONFactory600.createRedisCallCountersJson(currentConsumerCallCounters)
          } yield {
            JSONFactory600.createConsumerJsonV600(consumer, None, activeRateLimitsJson, callCountersJson)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getConsumer),
      "GET",
      "/management/consumers/CONSUMER_ID",
      "Get Consumer",
      """Get the Consumer specified by CONSUMER_ID, including rate limits and
        |current call counters.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConsumer :: apiTagApi :: Nil,
      Some(canGetConsumers :: Nil),
      http4sPartialFunction = Some(getConsumer)
    )

    // Route: GET /obp/v6.0.0/customers
    lazy val getCustomersAtAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "customers" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (requestParams, callContext) <- NewStyle.function.extractQueryParams(
              req.uri.renderString,
              List("limit", "offset", "sort_direction"),
              cc.callContext
            )
            (customers, _) <- NewStyle.function.getCustomersAtAllBanks(callContext, requestParams)
          } yield JSONFactory600.createCustomersJson(customers.sortBy(_.bankId))
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCustomersAtAllBanks),
      "GET",
      "/customers",
      "Get Customers at All Banks",
      """Get Customers at All Banks. Returns all customers across all banks
        |the caller has permission to see.""".stripMargin,
      EmptyBody,
      customerJSONsV600,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtAllBanks :: Nil),
      http4sPartialFunction = Some(getCustomersAtAllBanks)
    )

    // Route: GET /obp/v6.0.0/users/USER_ID/attributes
    lazy val getUserAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userIdStr / "attributes" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userIdStr, cc.callContext)
            (attributes, _) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
          } yield UserAttributesResponseJsonV510(attributes.map(JSONFactory510.createUserAttributeJson))
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getUserAttributes),
      "GET",
      "/users/USER_ID/attributes",
      "Get User Attributes",
      """Get all non-personal attributes for the specified user.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByUserId, UnknownError),
      apiTagUser :: apiTagUserAttribute :: apiTagAttribute :: Nil,
      Some(canGetUserAttributes :: Nil),
      http4sPartialFunction = Some(getUserAttributes)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account
    lazy val getPrivateAccountByIdFull: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / viewIdStr / "account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
            (accountAttributes, _) <- NewStyle.function.getAccountAttributesByAccount(
              account.bankId, account.accountId, Some(cc))
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(
              user, BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews.map(JSONFactory600.createViewJsonV600).sortBy(_.view_name)
            val tags = Tags.tags.vend.getTagsOnAccount(account.bankId, account.accountId)(ViewId(viewIdStr))
            JSONFactory600.createBankAccountJSON600(moderatedAccount, viewsAvailable, accountAttributes, tags)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getPrivateAccountByIdFull),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (Full)",
      """Returns full information about an account as moderated by the view (VIEW_ID).""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(getPrivateAccountByIdFull)
    )

    // Route: POST /obp/v6.0.0/banks/BANK_ID/customers/customer-number
    // POST that GETs (returns 200) — used to fetch a customer by their customer_number.
    // Body is parsed manually so we preserve v6 Lift's "The Json body should be the …"
    // wording verbatim, which the test suites assert on.
    lazy val getCustomerByCustomerNumber: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / "customer-number" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[PostCustomerNumberJsonV310].getSimpleName}",
              400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostCustomerNumberJsonV310]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(
              postedData.customer_number, bank.bankId, Some(cc))
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(
              bank.bankId, CustomerId(customer.customerId), callContext)
          } yield JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCustomerByCustomerNumber),
      "POST",
      "/banks/BANK_ID/customers/customer-number",
      "Get Customer by CUSTOMER_NUMBER",
      """Gets the Customer specified by CUSTOMER_NUMBER.""",
      EmptyBody,
      customerWithAttributesJsonV600,
      List($AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCustomerByCustomerNumber)
    )

    // Route: POST /obp/v6.0.0/banks/BANK_ID/customers/legal-name
    // POST that GETs (returns 200) — fetch customers by legal name.
    lazy val getCustomersByLegalName: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / "legal-name" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[PostCustomerLegalNameJsonV510].getSimpleName}",
              400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostCustomerLegalNameJsonV510]
            }
            (customers, _) <- NewStyle.function.getCustomersByCustomerLegalName(
              bank.bankId, postedData.legal_name, Some(cc))
          } yield JSONFactory600.createCustomersJson(customers)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCustomersByLegalName),
      "POST",
      "/banks/BANK_ID/customers/legal-name",
      "Get Customers by Legal Name",
      """Gets the Customers matching the provided legal name at the specified bank.""",
      EmptyBody,
      customerJSONsV600,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCustomersByLegalName)
    )

    // Inlined helpers — match the v6 Lift private versions in APIMethods600.
    private val validEntityNamePattern = "^[a-z][a-z0-9_]*$".r.pattern
    private def validateEntityNameV600(entityName: String, cc: CallContext): Future[Unit] =
      if (validEntityNamePattern.matcher(entityName).matches()) Future.successful(())
      else Future.failed(new RuntimeException(s"$InvalidDynamicEntityName Current value: '$entityName'"))

    private def createDynamicEntityV600(cc: CallContext, dynamicEntity: DynamicEntityCommons) = for {
      // Wrap the connector call so a thrown RuntimeException (bad schema, etc.)
      // becomes a 400 InvalidJsonFormat — matches v6 Lift's dispatch wrapper.
      Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, Some(cc))
        .recoverWith {
          case e: Throwable if !Option(e.getMessage).exists(_.startsWith("OBP-")) =>
            val json = net.liftweb.json.Serialization.write(
              code.api.APIFailureNewStyle(s"$InvalidJsonFormat ${e.getMessage}", 400, Some(cc).map(_.toLight))
            )(net.liftweb.json.DefaultFormats)
            Future.failed(new Exception(json))
        }
      crudRoles = List(
        DynamicEntityInfo.canCreateRole(result.entityName, dynamicEntity.bankId),
        DynamicEntityInfo.canUpdateRole(result.entityName, dynamicEntity.bankId),
        DynamicEntityInfo.canGetRole(result.entityName, dynamicEntity.bankId),
        DynamicEntityInfo.canDeleteRole(result.entityName, dynamicEntity.bankId)
      )
    } yield {
      crudRoles.foreach(role =>
        Entitlement.entitlement.vend.addEntitlement(dynamicEntity.bankId.getOrElse(""), cc.userId, role.toString()))
      JSONFactory600.createMyDynamicEntitiesJson(List(result: DynamicEntityCommons)).dynamic_entities.head
    }

    private def updateDynamicEntityV600(cc: CallContext, dynamicEntity: DynamicEntityCommons) = for {
      Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, Some(cc))
        .recoverWith {
          case e: Throwable if !Option(e.getMessage).exists(_.startsWith("OBP-")) =>
            val json = net.liftweb.json.Serialization.write(
              code.api.APIFailureNewStyle(s"$InvalidJsonFormat ${e.getMessage}", 400, Some(cc).map(_.toLight))
            )(net.liftweb.json.DefaultFormats)
            Future.failed(new Exception(json))
        }
    } yield {
      JSONFactory600.createMyDynamicEntitiesJson(List(result: DynamicEntityCommons)).dynamic_entities.head
    }

    // Route: POST /obp/v6.0.0/management/system-dynamic-entities (201)
    lazy val createSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "system-dynamic-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            dynamicEntity <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              DynamicEntityCommons(JSONFactory600.convertV600RequestToInternal(request), None, cc.userId, None)
            }
            result <- createDynamicEntityV600(cc, dynamicEntity)
          } yield result
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createSystemDynamicEntity), "POST",
      "/management/system-dynamic-entities", "Create System Level Dynamic Entity",
      """Create a system-level Dynamic Entity.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      Some(canCreateSystemLevelDynamicEntity :: Nil),
      authMode = code.api.util.APIUtil.UserOrApplication,
      http4sPartialFunction = Some(createSystemDynamicEntity)
    )

    // Route: POST /obp/v6.0.0/management/banks/BANK_ID/dynamic-entities (201)
    lazy val createBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "dynamic-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            dynamicEntity <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              DynamicEntityCommons(JSONFactory600.convertV600RequestToInternal(request), None, cc.userId, Some(bankIdStr))
            }
            result <- createDynamicEntityV600(cc, dynamicEntity)
          } yield result
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBankLevelDynamicEntity), "POST",
      "/management/banks/BANK_ID/dynamic-entities", "Create Bank Level Dynamic Entity",
      """Create a bank-level Dynamic Entity for the specified bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      Some(canCreateBankLevelDynamicEntity :: Nil),
      authMode = code.api.util.APIUtil.UserOrApplication,
      http4sPartialFunction = Some(createBankLevelDynamicEntity)
    )

    // Route: PUT /obp/v6.0.0/management/system-dynamic-entities/DYNAMIC_ENTITY_ID (200)
    lazy val updateSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "system-dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
            dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId, None)
            result <- updateDynamicEntityV600(cc, dynamicEntity)
          } yield result
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateSystemDynamicEntity), "PUT",
      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID", "Update System Level Dynamic Entity",
      """Update a system-level Dynamic Entity.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      Some(canUpdateSystemDynamicEntity :: Nil),
      http4sPartialFunction = Some(updateSystemDynamicEntity)
    )

    // Route: PUT /obp/v6.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID (200)
    lazy val updateBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / bankIdStr / "dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
            dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId, Some(bankIdStr))
            result <- updateDynamicEntityV600(cc, dynamicEntity)
          } yield result
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateBankLevelDynamicEntity), "PUT",
      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID", "Update Bank Level Dynamic Entity",
      """Update a bank-level Dynamic Entity.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      Some(canUpdateBankLevelDynamicEntity :: Nil),
      http4sPartialFunction = Some(updateBankLevelDynamicEntity)
    )

    // Route: PUT /obp/v6.0.0/my/dynamic-entities/DYNAMIC_ENTITY_ID (200)
    lazy val updateMyDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "my" / "dynamic-entities" / dynamicEntityId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            existingEntity <- Future(
              NewStyle.function.getDynamicEntitiesByUserId(cc.userId).find(_.dynamicEntityId.contains(dynamicEntityId))
            )
            _ <- Helper.booleanToFuture(s"$DynamicEntityNotFoundByDynamicEntityId dynamicEntityId = $dynamicEntityId", cc = Some(cc)) {
              existingEntity.isDefined
            }
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
            dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId, existingEntity.get.bankId)
            result <- updateDynamicEntityV600(cc, dynamicEntity)
          } yield result
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateMyDynamicEntity), "PUT",
      "/my/dynamic-entities/DYNAMIC_ENTITY_ID", "Update My Dynamic Entity",
      """Update a Dynamic Entity I created.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, DynamicEntityNotFoundByDynamicEntityId, InvalidJsonFormat, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(updateMyDynamicEntity)
    )

    // Route: PUT /obp/v6.0.0/system-views/UPD_VIEW_ID (200)
    // Uses UPD_VIEW_ID (non-standard ALL_CAPS) so middleware skips view validation;
    // system views aren't in the regular view tables that VIEW_ID resolution checks.
    lazy val updateSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "system-views" / viewIdStr if viewIdStr.nonEmpty =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            user <- Future(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            _ <- Helper.booleanToFuture(UserHasMissingRoles + canUpdateSystemView, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canUpdateSystemView)
            }
            updateJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the UpdateViewJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateViewJsonV600]
            }
            _ <- Helper.booleanToFuture(SystemViewCannotBePublicError, failCode = 400, cc = Some(cc)) {
              updateJson.is_public == false
            }
            _ <- ViewNewStyle.systemView(ViewId(viewIdStr), Some(cc))
            updatedView <- ViewNewStyle.updateSystemView(ViewId(viewIdStr), updateJson.toUpdateViewJson, Some(cc))
          } yield JSONFactory600.createViewJsonV600(updatedView)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateSystemView), "PUT",
      "/system-views/UPD_VIEW_ID", "Update System View",
      """Update an existing system view.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, SystemViewCannotBePublicError, UnknownError),
      apiTagView :: Nil,
      None,
      http4sPartialFunction = Some(updateSystemView)
    )

    // Inject default from_date so metrics queries don't hit all rows since epoch.
    private def applyMetricsFromDateDefault(httpParams: List[HTTPParam]): List[HTTPParam] = {
      val hasFromDate = httpParams.exists(p => p.name == "from_date" || p.name == "obp_from_date")
      if (hasFromDate) httpParams
      else {
        val stableBoundary = APIUtil.getPropsAsIntValue("MappedMetrics.stable.boundary.seconds", 600)
        val defaultFromDate = new java.util.Date(System.currentTimeMillis() - ((stableBoundary - 1) * 1000L))
        HTTPParam("from_date", List(APIUtil.DateWithMsFormat.format(defaultFromDate))) :: httpParams
      }
    }

    // Route: GET /obp/v6.0.0/management/metrics
    lazy val getMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(
              applyMetricsFromDateDefault(httpParams), cc.callContext)
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams))
          } yield {
            val lookupMap = APIUtil.getAllResourceDocs.map(d => d.partialFunctionName -> d.operationId).toMap
            JSONFactory600.createMetricsJsonV600(metrics, lookupMap)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMetrics), "GET",
      "/management/metrics", "Get Metrics",
      """Returns metrics on API usage. Requires canReadMetrics role.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagMetric :: apiTagApi :: Nil,
      Some(canReadMetrics :: Nil),
      http4sPartialFunction = Some(getMetrics)
    )

    // Route: GET /obp/v6.0.0/management/aggregate-metrics
    lazy val getAggregateMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "aggregate-metrics" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            _ <- NewStyle.function.tryons(ExcludeParametersNotSupported, 400, Some(cc)) {
              val excludes = httpParams.filter(p =>
                p.name == "exclude_app_names" ||
                p.name == "exclude_url_patterns" ||
                p.name == "exclude_implemented_by_partial_functions")
              if (excludes.nonEmpty)
                throw new Exception(s"$ExcludeParametersNotSupported Parameters found: [${excludes.map(_.name).mkString(", ")}]")
              else true
            }
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(
              applyMetricsFromDateDefault(httpParams), cc.callContext)
            aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams, false) map {
              APIUtil.unboxFullOrFail(_, callContext, GetAggregateMetricsError)
            }
          } yield JSONFactory300.createAggregateMetricJson(aggregateMetrics)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAggregateMetrics), "GET",
      "/management/aggregate-metrics", "Get Aggregate Metrics",
      """Returns aggregate metrics on API usage. Requires canReadAggregateMetrics role.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagMetric :: apiTagApi :: Nil,
      Some(canReadAggregateMetrics :: Nil),
      http4sPartialFunction = Some(getAggregateMetrics)
    )

    // Route: GET /obp/v6.0.0/management/metrics/top-apis
    lazy val getTopAPIs: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" / "top-apis" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, cc.callContext)
            topApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(obpQueryParams) map {
              APIUtil.unboxFullOrFail(_, callContext, GetTopApisError)
            }
          } yield {
            val lookupMap: Map[String, String] = APIUtil.getAllResourceDocs.map(d =>
              d.partialFunctionName -> d.operationId).toMap
            val topApisWithOperationId = topApis.map { api =>
              val operationId = lookupMap.getOrElse(
                api.ImplementedByPartialFunction,
                scala.util.Try(APIUtil.buildOperationId(
                  ApiVersionUtils.valueOf(api.implementedInVersion), api.ImplementedByPartialFunction))
                  .getOrElse(s"${api.implementedInVersion}-${api.ImplementedByPartialFunction}"))
              TopApiJsonV600(
                count = api.count,
                implemented_by_partial_function = api.ImplementedByPartialFunction,
                implemented_in_version = api.implementedInVersion,
                operation_id = operationId)
            }
            JSONFactory600.createTopApisJsonV600(topApisWithOperationId)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getTopAPIs), "GET",
      "/management/metrics/top-apis", "Get Top APIs",
      """Returns the top APIs by call count. Requires canReadMetrics role.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagMetric :: apiTagApi :: Nil,
      Some(canReadMetrics :: Nil),
      http4sPartialFunction = Some(getTopAPIs)
    )

    // Route: GET /obp/v6.0.0/webui-props
    lazy val getWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "webui-props" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val what = req.uri.query.params.getOrElse("what", "active")
          for {
            _ <- NewStyle.function.tryons(
              s"$InvalidFilterParameterFormat `what` must be one of: active, database, config. Current value: $what",
              400, Some(cc)) {
              what match { case "active" | "database" | "config" => true case _ => false }
            }
            explicitWebUiProps <- Future(MappedWebUiPropsProvider.getAll())
          } yield {
            val explicitWebUiPropsWithSource = explicitWebUiProps.map(prop =>
              WebUiPropsCommons(prop.name, prop.value, prop.webUiPropsId, source = Some("database")))
            val implicitWebUiProps = APIUtil.getWebUIPropsPairs.map(p =>
              WebUiPropsCommons(p._1, p._2, webUiPropsId = Some("default"), source = Some("config")))
            val result = what match {
              case "database" => explicitWebUiPropsWithSource
              case "config" => implicitWebUiProps.distinct
              case "active" =>
                val databasePropNames = explicitWebUiPropsWithSource.map(_.name).toSet
                val configNotInDatabase = implicitWebUiProps.distinct.filterNot(p => databasePropNames.contains(p.name))
                explicitWebUiPropsWithSource ++ configNotInDatabase
            }
            ListResult("webui_props", result)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getWebUiProps), "GET",
      "/webui-props", "Get WebUiProps",
      """Get the WebUI props. Optional ?what=active|database|config filter.""",
      EmptyBody, EmptyBody,
      List(InvalidFilterParameterFormat, UnknownError),
      apiTagWebUiProps :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(getWebUiProps)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/accounts
    lazy val getAccountsAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          val filteredParams: Map[String, List[String]] = req.uri.query.multiParams
            .filterKeys(k => k != PARAM_TIMESTAMP && k != PARAM_LOCALE)
            .map { case (k, vs) => k -> vs.toList }
            .toMap
          for {
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            }
            privateAccountAccess2 <-
              if (filteredParams.isEmpty || privateAccountAccess.isEmpty) Future.successful(privateAccountAccess)
              else AccountAttributeX.accountAttributeProvider.vend
                .getAccountIdsByParams(bank.bankId, filteredParams)
                .map { boxedAccountIds =>
                  val accountIds = boxedAccountIds.getOrElse(Nil)
                  privateAccountAccess.filter(aa => accountIds.contains(aa.account_id.get))
                }
            (availablePrivateAccounts, _) <- BankExtended(bank).privateAccountsFuture(privateAccountAccess2, Some(cc))
          } yield {
            val accountsJson = availablePrivateAccounts.map { account =>
              val viewsAvailable = privateViewsUserCanAccessAtOneBank
                .filter(v => v.bankId == bank.bankId && v.accountId == account.accountId)
                .map(v => BasicViewJson(v.viewId.value, v.name, v.isPublic))
              JSONFactory600.createBasicAccountJsonV600(account, viewsAvailable)
            }
            BasicAccountsJsonV600(accountsJson)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAccountsAtBank), "GET",
      "/banks/BANK_ID/accounts", "Get Accounts at Bank",
      """Get private accounts the caller has access to at the specified bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(getAccountsAtBank)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions
    lazy val getTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" =>
        EndpointHelpers.withView(req) { (user, bankAccount, view, cc) =>
          for {
            (bank, _) <- NewStyle.function.getBank(bankAccount.bankId, Some(cc))
            (params, _) <- createQueriesByHttpParamsFuture(
              req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)), Some(cc))
            transactionsResult <- bankAccount.getModeratedTransactionsFuture(bank, Full(user), view, Some(cc), params) map {
              APIUtil.connectorEmptyResponse(_, Some(cc))
            }
            (transactions: List[ModeratedTransaction], _) = transactionsResult
            moderatedTransactionsWithAttributes <- Future.sequence(transactions.map(transaction =>
              NewStyle.function.getTransactionAttributes(bankAccount.bankId, transaction.id, Some(cc))
                .map(attrs => code.api.v3_0_0.ModeratedTransactionWithAttributes(transaction, attrs._1))))
          } yield JSONFactory600.createTransactionsJsonV600(moderatedTransactionsWithAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getTransactionsForBankAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions", "Get Transactions for Account (Full)",
      """Returns transactions list of the account specified by ACCOUNT_ID, moderated by VIEW_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      apiTagTransaction :: Nil,
      None,
      http4sPartialFunction = Some(getTransactionsForBankAccount)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/products
    // Simplified port — skips the Redis cache layer (perf optimization only).
    lazy val getProductsV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "products" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          val params = req.uri.query.multiParams.toList.map { case (k, vs) => GetProductsParam(k, vs.toList) }
          for {
            (products, _) <- NewStyle.function.getProducts(bank.bankId, params, Some(cc))
          } yield JSONFactory600.createProductsJsonV600(products, Map.empty)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getProductsV600), "GET",
      "/banks/BANK_ID/products", "Get Products",
      """Returns the financial Products offered by the specified bank.""",
      EmptyBody, EmptyBody,
      List($BankNotFound, UnknownError),
      apiTagProduct :: Nil,
      None,
      http4sPartialFunction = Some(getProductsV600)
    )

    // Route: GET /obp/v6.0.0/users
    lazy val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, cc.callContext)
            _ <- Future {
              val requestedSort = obpQueryParams.collectFirst { case code.api.util.OBPSortBy(v) => v }
              val allowed = DoobieUserQueries.SortableColumns.keySet
              val valid = requestedSort match {
                case Some(v) if !allowed.contains(v) =>
                  Failure(filterSortByNotAllowedForEndpointDetail("GET /users", v, allowed))
                case _ => Full(())
              }
              unboxFullOrFail(valid, callContext, FilterSortByNotAllowedForEndpoint, 400)
            }
            rows <- Users.users.vend.getUsersV600F(obpQueryParams)
          } yield JSONFactory600.createUsersInfoJsonV600(rows)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getUsers), "GET",
      "/users", "Get Users",
      """Get all Users (paginated, sortable).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagUser :: Nil,
      Some(canGetAnyUser :: Nil),
      http4sPartialFunction = Some(getUsers)
    )

    // Route: POST /obp/v6.0.0/banks (201)
    lazy val createBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val failMsg = s"$InvalidJsonFormat The Json body should be the PostBankJson600"
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostBankJson600]
            }
            checkShortStringValue = APIUtil.checkOptionalShortString(postJson.bank_id)
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat BANK_ID: $checkShortStringValue", cc = Some(cc)) {
              checkShortStringValue == SILENCE_IS_GOLDEN
            }
            _ <- Helper.booleanToFuture(InvalidConsumerCredentials, cc = Some(cc)) {
              cc.consumer.isDefined
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc = Some(cc)) {
              postJson.bank_id.length > 3
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat BANK_ID can not contain space characters", cc = Some(cc)) {
              !postJson.bank_id.contains(" ")
            }
            (banks, _) <- NewStyle.function.getBanks(Some(cc))
            _ <- Helper.booleanToFuture(bankIdAlreadyExists, failCode = 409, cc = Some(cc)) {
              !banks.exists(_.bankId.value == postJson.bank_id)
            }
            (success, _) <- NewStyle.function.createOrUpdateBank(
              postJson.bank_id,
              postJson.full_name.getOrElse(""),
              postJson.bank_code,
              postJson.logo.getOrElse(""),
              postJson.website.getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              Some(cc)
            )
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, Some(cc))
            entitlementsByBank = entitlements.filter(_.bankId == postJson.bank_id)
            _ = if (!entitlementsByBank.exists(_.roleName == CanCreateEntitlementAtOneBank.toString))
              Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanCreateEntitlementAtOneBank.toString)
          } yield JSONFactory600.createBankJSON600(success)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBank), "POST",
      "/banks", "Create Bank",
      """Create a new bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, bankIdAlreadyExists, UnknownError),
      apiTagBank :: Nil,
      Some(canCreateBank :: Nil),
      http4sPartialFunction = Some(createBank)
    )

    // Route: POST /obp/v6.0.0/banks/BANK_ID/customers (201)
    lazy val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bankId = BankId(bankIdStr)
          val failMsg = s"$InvalidJsonFormat The Json body should be the PostCustomerJsonV600 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostCustomerJsonV600]
            }
            _ <- Helper.booleanToFuture(InvalidJsonContent, 400, Some(cc)) {
              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
            }
            dateOfBirth <- Future {
              postedData.date_of_birth.map { dateStr =>
                val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
                formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                formatter.setLenient(false)
                formatter.parse(dateStr)
              }.orNull
            }
            dobOfDependants <- Future {
              postedData.dob_of_dependants.getOrElse(Nil).map { dateStr =>
                val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
                formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                formatter.setLenient(false)
                formatter.parse(dateStr)
              }
            }
            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)
            (_, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, Some(cc))
            customerType = postedData.customer_type.getOrElse("INDIVIDUAL")
            _ <- Helper.booleanToFuture(InvalidCustomerType, 400, callContext) {
              List("INDIVIDUAL", "CORPORATE", "SUBSIDIARY").contains(customerType)
            }
            parentCustomerIdValue = postedData.parent_customer_id.getOrElse("")
            _ <- if (parentCustomerIdValue.nonEmpty) NewStyle.function.getCustomerByCustomerId(parentCustomerIdValue, callContext).map(_ => ())
                 else Future.successful(())
            (customer, _) <- NewStyle.function.createCustomerC2(
              bankId, postedData.legal_name, customerNumber, postedData.mobile_phone_number,
              postedData.email.getOrElse(""),
              CustomerFaceImage(postedData.face_image.map(_.date).getOrElse(null), postedData.face_image.map(_.url).getOrElse("")),
              dateOfBirth, postedData.relationship_status.getOrElse(""),
              postedData.dependants.getOrElse(0), dobOfDependants,
              postedData.highest_education_attained.getOrElse(""), postedData.employment_status.getOrElse(""),
              postedData.kyc_status.getOrElse(false), postedData.last_ok_date.getOrElse(null),
              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
              postedData.title.getOrElse(""), postedData.branch_id.getOrElse(""),
              postedData.name_suffix.getOrElse(""), customerType, parentCustomerIdValue, callContext)
          } yield JSONFactory600.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCustomer), "POST",
      "/banks/BANK_ID/customers", "Create Customer",
      """Create a new customer at the specified bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canCreateCustomer :: Nil),
      http4sPartialFunction = Some(createCustomer)
    )

    // Route: POST /obp/v6.0.0/users (201)
    lazy val createUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateUserJsonV600]
            }
            _ <- Helper.booleanToFuture(InvalidStrongPasswordFormat, 400, Some(cc)) {
              APIUtil.fullPasswordValidation(postedData.password)
            }
            _ <- Helper.booleanToFuture(DuplicateUsername, 409, Some(cc)) {
              AuthUser.find(net.liftweb.mapper.By(AuthUser.username, postedData.username)).isEmpty
            }
            userCreated <- Future {
              AuthUser.create
                .firstName(postedData.first_name).lastName(postedData.last_name)
                .username(postedData.username).email(postedData.email)
                .password(postedData.password)
                .validated(APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false))
            }
            _ <- Helper.booleanToFuture(InvalidJsonFormat + userCreated.validate.map(_.msg).mkString(";"), 400, Some(cc)) {
              userCreated.validate.size == 0
            }
            savedUser <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { userCreated.saveMe() }
            _ <- Helper.booleanToFuture(s"$UnknownError Error occurred during user creation.", 400, Some(cc)) {
              userCreated.saved_?
            }
          } yield {
            val skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false)
            if (!skipEmailValidation) {
              APIUtil.getPropsValue("portal_external_url").foreach { portalUrl =>
                val expiryMinutes = APIUtil.getPropsAsIntValue("email_validation_token_expiry_minutes", 1440)
                val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                  .subject(savedUser.uniqueId.get)
                  .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
                  .issueTime(new java.util.Date()).build()
                val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
                val emailLink = portalUrl + "/user-validation?token=" + java.net.URLEncoder.encode(jwtToken, "UTF-8")
                CommonsEmailWrapper.sendHtmlEmail(CommonsEmailWrapper.EmailContent(
                  from = AuthUser.emailFrom,
                  to = List(savedUser.email.get),
                  bcc = AuthUser.bccEmail.toList,
                  subject = "Sign up confirmation",
                  textContent = Some(s"Welcome! Please validate your account: $emailLink"),
                  htmlContent = Some(s"<p>Welcome! Please <a href='$emailLink'>validate your account</a>.</p>")
                ))
              }
            }
            AuthUser.grantDefaultEntitlementsToAuthUser(savedUser)
            JSONFactory200.createUserJSONfromAuthUser(userCreated)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createUser), "POST",
      "/users", "Create User",
      """Create a new user with username, email, password.""",
      EmptyBody, EmptyBody,
      List(InvalidJsonFormat, InvalidStrongPasswordFormat, DuplicateUsername, UnknownError),
      apiTagUser :: Nil,
      None,
      http4sPartialFunction = Some(createUser)
    )

    // Route: POST /obp/v6.0.0/management/user/reset-password-url (201)
    lazy val resetPasswordUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "user" / "reset-password-url" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[code.api.v6_0_0.JSONFactory600.PostResetPasswordUrlJsonV600]}",
              400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[code.api.v6_0_0.JSONFactory600.PostResetPasswordUrlJsonV600]
            }
            authUserBox <- Future {
              AuthUser.find(net.liftweb.mapper.By(AuthUser.username, postedData.username))
            }
            authUser <- NewStyle.function.tryons(s"$UnknownError User not found or validation failed", 400, Some(cc)) {
              authUserBox match {
                case Full(user) if user.validated.get && user.email.get == postedData.email =>
                  Users.users.vend.getUserByUserId(postedData.user_id) match {
                    case Full(resourceUser) if resourceUser.name == postedData.username &&
                                               resourceUser.emailAddress == postedData.email => user
                    case _ => throw new Exception("User ID does not match username and email")
                  }
                case _ => throw new Exception("User not found, not validated, or email mismatch")
              }
            }
            portalUrl <- APIUtil.getPropsValue("portal_external_url") match {
              case Full(url) => Future.successful(url)
              case _ => Future.failed(new Exception(s"$IncompleteServerConfiguration portal_external_url is not set"))
            }
          } yield {
            val user: AuthUser = authUser
            user.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
            user.save
            val expiryMinutes = APIUtil.getPropsAsIntValue("password_reset_token_expiry_minutes", 120)
            val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
              .subject(user.uniqueId.get)
              .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
              .issueTime(new java.util.Date()).build()
            val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
            val resetLink = portalUrl + "/reset-password/" + java.net.URLEncoder.encode(jwtToken, "UTF-8")
            CommonsEmailWrapper.sendHtmlEmail(CommonsEmailWrapper.EmailContent(
              from = AuthUser.emailFrom,
              to = List(user.email.get),
              bcc = AuthUser.bccEmail.toList,
              subject = "Reset your password - " + user.username.get,
              textContent = Some(s"Please reset your password: $resetLink"),
              htmlContent = Some(s"<p>Please reset your password: <a href='$resetLink'>$resetLink</a></p>")
            ))
            JSONFactory600.ResetPasswordUrlJsonV600(resetLink)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(resetPasswordUrl), "POST",
      "/management/user/reset-password-url", "Send Password Reset URL",
      """Generate and email a password reset URL for the specified user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagUser :: Nil,
      Some(canCreateResetPasswordUrl :: Nil),
      http4sPartialFunction = Some(resetPasswordUrl)
    )

    // ─── Phase 2: system bucket (8 GETs) — wholly new in v6, no override risk ────

    // Route: GET /obp/v6.0.0/system/connectors
    lazy val getConnectors: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "connectors" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful {
            val connectorNames = BankConnector.nameToConnector.keys.toList :+ "star"
            val connectorInfos = connectorNames.map { name =>
              ConnectorInfoJsonV600(
                connector_name = name,
                is_available_in_method_routing = NewStyle.function.getConnectorByName(name).isDefined)
            }
            JSONFactory600.createConnectorsJson(connectorInfos)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConnectors), "GET",
      "/system/connectors", "Get Connectors",
      """Get the list of connectors and their availability for method routing.""",
      EmptyBody, EmptyBody,
      List(UnknownError),
      apiTagConnector :: apiTagSystem :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(getConnectors)
    )

    // Route: GET /obp/v6.0.0/system/cache/config
    lazy val getCacheConfig: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "cache" / "config" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future.successful(JSONFactory600.createCacheConfigJsonV600())
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCacheConfig), "GET",
      "/system/cache/config", "Get Cache Configuration",
      """Returns cache configuration including Redis status, in-memory cache status, instance ID, environment and global prefix.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(canGetCacheConfig :: Nil),
      http4sPartialFunction = Some(getCacheConfig)
    )

    // Route: GET /obp/v6.0.0/system/cache/info
    lazy val getCacheInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "cache" / "info" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future.successful(JSONFactory600.createCacheInfoJsonV600())
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCacheInfo), "GET",
      "/system/cache/info", "Get Cache Information",
      """Returns detailed cache information for all namespaces.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(canGetCacheInfo :: Nil),
      http4sPartialFunction = Some(getCacheInfo)
    )

    // Route: GET /obp/v6.0.0/system/cache/namespaces
    lazy val getCacheNamespaces: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "cache" / "namespaces" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val namespaces = List(
              (Constant.CALL_COUNTER_PREFIX, "Rate limiting counters per consumer and time period", "varies", "Rate Limiting"),
              (Constant.RATE_LIMIT_ACTIVE_PREFIX, "Active rate limit configurations", Constant.RATE_LIMIT_ACTIVE_CACHE_TTL.toString, "Rate Limiting"),
              (Constant.LOCALISED_RESOURCE_DOC_PREFIX, "Localized resource documentation", Constant.CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL.toString, "Resource Documentation"),
              (Constant.DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, "Dynamic resource documentation", Constant.GET_DYNAMIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
              (Constant.STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX, "Static resource documentation", Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
              (Constant.ALL_RESOURCE_DOC_CACHE_KEY_PREFIX, "All resource documentation", Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
              (Constant.STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX, "Swagger documentation", Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
              (Constant.CONNECTOR_PREFIX, "Connector method names and metadata", "3600", "Connector"),
              (Constant.METRICS_STABLE_PREFIX, "Stable metrics (historical)", "86400", "Metrics"),
              (Constant.METRICS_RECENT_PREFIX, "Recent metrics", "7", "Metrics"),
              (Constant.ABAC_RULE_PREFIX, "ABAC rule cache", "indefinite", "ABAC")
            ).map { case (prefix, description, ttl, category) =>
              JSONFactory600.createCacheNamespaceJsonV600(
                prefix, description, ttl, category,
                Redis.countKeys(s"${prefix}*"),
                Redis.getSampleKey(s"${prefix}*"))
            }
            JSONFactory600.createCacheNamespacesJsonV600(namespaces)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCacheNamespaces), "GET",
      "/system/cache/namespaces", "Get Cache Namespaces",
      """Returns all OBP cache namespaces with their prefixes, descriptions, TTLs, and current key counts.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(canGetCacheNamespaces :: Nil),
      http4sPartialFunction = Some(getCacheNamespaces)
    )

    // Route: GET /obp/v6.0.0/system/database/pool
    lazy val getDatabasePoolInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "database" / "pool" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future.successful(JSONFactory600.createDatabasePoolInfoJsonV600())
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getDatabasePoolInfo), "GET",
      "/system/database/pool", "Get Database Pool Information",
      """Returns HikariCP connection pool information.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystem :: apiTagApi :: Nil,
      Some(canGetDatabasePoolInfo :: Nil),
      http4sPartialFunction = Some(getDatabasePoolInfo)
    )

    // Route: GET /obp/v6.0.0/system/migrations
    lazy val getMigrations: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "migrations" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val migrations = MigrationScriptLogProvider.migrationScriptLogProvider.vend.getMigrationScriptLogs()
            JSONFactory600.createMigrationScriptLogsJsonV600(migrations)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMigrations), "GET",
      "/system/migrations", "Get Database Migrations",
      """Get all database migration script logs.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystem :: apiTagApi :: Nil,
      Some(canGetMigrations :: Nil),
      http4sPartialFunction = Some(getMigrations)
    )

    // Route: GET /obp/v6.0.0/system/connectors/stored_procedure_vDec2019/health
    lazy val getStoredProcedureConnectorHealth: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "connectors" / "stored_procedure_vDec2019" / "health" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val health = StoredProcedureUtils.getHealth()
            StoredProcedureConnectorHealthJsonV600(
              status = health.status,
              server_name = health.serverName,
              server_ip = health.serverIp,
              database_name = health.databaseName,
              response_time_ms = health.responseTimeMs,
              error_message = health.errorMessage)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getStoredProcedureConnectorHealth), "GET",
      "/system/connectors/stored_procedure_vDec2019/health", "Get Stored Procedure Connector Health",
      """Returns health status of the stored procedure connector.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConnector :: apiTagSystem :: apiTagApi :: Nil,
      Some(canGetConnectorHealth :: Nil),
      http4sPartialFunction = Some(getStoredProcedureConnectorHealth)
    )

    // Route: GET /obp/v6.0.0/system/connector-method-names
    // Simplified port — skips the Redis cache wrapper (perf only).
    lazy val getConnectorMethodNames: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "connector-method-names" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val connectorName = APIUtil.getPropsValue("connector", "mapped")
            val connector = code.bankconnectors.Connector.getConnectorInstance(connectorName)
            JSONFactory600.createConnectorMethodNamesJson(connector.callableMethods.keys.toList)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConnectorMethodNames), "GET",
      "/system/connector-method-names", "Get Connector Method Names",
      """Get all callable method names for the configured connector.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConnectorMethod :: apiTagSystem :: apiTagMethodRouting :: apiTagApi :: Nil,
      Some(canGetSystemConnectorMethodNames :: Nil),
      http4sPartialFunction = Some(getConnectorMethodNames)
    )

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getScannedApiVersions(req))
          .orElse(getCurrentUser(req))
          .orElse(getBanks(req))
          .orElse(getBank(req))
          .orElse(getCustomersAtOneBank(req))
          .orElse(getCustomerByCustomerId(req))
          .orElse(getCoreAccountByIdV600(req))
          .orElse(getMyDynamicEntities(req))
          .orElse(getSystemDynamicEntities(req))
          .orElse(getBankLevelDynamicEntities(req))
          .orElse(getConsumer(req))
          .orElse(getCustomersAtAllBanks(req))
          .orElse(getUserAttributes(req))
          .orElse(getPrivateAccountByIdFull(req))
          .orElse(getCustomerByCustomerNumber(req))
          .orElse(getCustomersByLegalName(req))
          .orElse(createSystemDynamicEntity(req))
          .orElse(createBankLevelDynamicEntity(req))
          .orElse(updateSystemDynamicEntity(req))
          .orElse(updateBankLevelDynamicEntity(req))
          .orElse(updateMyDynamicEntity(req))
          .orElse(updateSystemView(req))
          .orElse(getMetrics(req))
          .orElse(getAggregateMetrics(req))
          .orElse(getTopAPIs(req))
          .orElse(getWebUiProps(req))
          .orElse(getAccountsAtBank(req))
          .orElse(getTransactionsForBankAccount(req))
          .orElse(getProductsV600(req))
          .orElse(getUsers(req))
          .orElse(createBank(req))
          .orElse(createCustomer(req))
          .orElse(createUser(req))
          .orElse(resetPasswordUrl(req))
          .orElse(getConnectors(req))
          .orElse(getCacheConfig(req))
          .orElse(getCacheInfo(req))
          .orElse(getCacheNamespaces(req))
          .orElse(getDatabasePoolInfo(req))
          .orElse(getMigrations(req))
          .orElse(getStoredProcedureConnectorHealth(req))
          .orElse(getConnectorMethodNames(req))
          .orElse(createMandate(req))
          .orElse(getMandates(req))
          .orElse(getMandate(req))
          .orElse(updateMandate(req))
          .orElse(deleteMandate(req))
          .orElse(createMandateProvision(req))
          .orElse(getMandateProvisions(req))
          .orElse(getMandateProvision(req))
          .orElse(updateMandateProvision(req))
          .orElse(deleteMandateProvision(req))
          .orElse(createApiProduct(req))
          .orElse(createOrUpdateApiProduct(req))
          .orElse(getApiProduct(req))
          .orElse(getApiProducts(req))
          .orElse(deleteApiProduct(req))
          .orElse(createApiProductAttribute(req))
          .orElse(updateApiProductAttribute(req))
          .orElse(getApiProductAttribute(req))
          .orElse(deleteApiProductAttribute(req))
          .orElse(createFeaturedApiCollection(req))
          .orElse(getFeaturedApiCollectionsAdmin(req))
          .orElse(updateFeaturedApiCollection(req))
          .orElse(deleteFeaturedApiCollection(req))
          .orElse(createPersonalDataField(req))
          .orElse(getPersonalDataFields(req))
          .orElse(getPersonalDataFieldById(req))
          .orElse(updatePersonalDataField(req))
          .orElse(deletePersonalDataField(req))
          .orElse(getConsumerCallCounters(req))
          .orElse(createCallLimits(req))
          .orElse(updateRateLimits(req))
          .orElse(deleteCallLimits(req))
          .orElse(getActiveRateLimitsNow(req))
          .orElse(getActiveRateLimitsAtDate(req))
          .orElse(createGroup(req))
          .orElse(getGroup(req))
          .orElse(getGroups(req))
          .orElse(updateGroup(req))
          .orElse(deleteGroup(req))
          .orElse(getGroupEntitlements(req))
          .orElse(createAbacRule(req))
          .orElse(getAbacRule(req))
          .orElse(getAbacRules(req))
          .orElse(getAbacRulesByPolicy(req))
          .orElse(updateAbacRule(req))
          .orElse(deleteAbacRule(req))
          .orElse(getFeatures(req))
          .orElse(getProviders(req))
          .orElse(getCurrentConsumer(req))
          .orElse(getPopularApis(req))
          .orElse(getAccountDirectory(req))
          .orElse(getConfigProps(req))
          .orElse(getAppDirectory(req))
          .orElse(getCustomViews(req))
          .orElse(getRolesWithEntitlementCountsAtAllBanks(req))
          .orElse(getCustomViewById(req))
          .orElse(invalidateCacheNamespace(req))
          .orElse(createCustomerLink(req))
          .orElse(getCustomerLinksByBankId(req))
          .orElse(getCustomerLinkById(req))
          .orElse(updateCustomerLink(req))
          .orElse(deleteCustomerLink(req))
          .orElse(getCorporateCustomersAtOneBank(req))
          .orElse(getCorporateCustomerByCustomerId(req))
          .orElse(getCorporateCustomerSubsidiaries(req))
          .orElse(getRetailCustomersAtOneBank(req))
          .orElse(getRetailCustomerByCustomerId(req))
          .orElse(getCustomerChildren(req))
          .orElse(getCustomerLinksByCustomerId(req))
          .orElse(getCustomerInvestigationReport(req))
          .orElse(getSystemViews(req))
          .orElse(getSystemViewById(req))
          .orElse(getAbacPolicies(req))
          .orElse(getConnectorCallCounts(req))
          .orElse(getConnectorTraces(req))
          .orElse(getDynamicEntityDiagnostics(req))
          .orElse(cleanupOrphanedDynamicEntityRecords(req))
          .orElse(createOrUpdateWebUiProps(req))
          .orElse(deleteWebUiProps(req))
          .orElse(createCustomViewManagement(req))
          .orElse(getProductTagsV600(req))
          .orElse(updateProductTagsV600(req))
          .orElse(getOidcClient(req))
          .orElse(verifyOidcClient(req))
          .orElse(getUserAttributeById(req))
          .orElse(createUserAttribute(req))
          .orElse(updateUserAttribute(req))
          .orElse(deleteUserAttribute(req))
          .orElse(addUserToGroup(req))
          .orElse(removeUserFromGroup(req))
          .orElse(deleteEntitlement(req))
          .orElse(getAvailablePersonalDynamicEntities(req))
          .orElse(getReferenceTypes(req))
          .orElse(joinSystemChatRoom(req))
          .orElse(createCounterpartyAttribute(req))
          .orElse(deleteCounterpartyAttribute(req))
          .orElse(getCounterpartyAttributeById(req))
          .orElse(getAllCounterpartyAttributes(req))
          .orElse(updateCounterpartyAttribute(req))
          .orElse(hasAccountAccess(req))
          .orElse(getMyAccountAccessRequests(req))
          .orElse(getWebUiProp(req))
          .orElse(getMessageDocsJsonSchema(req))
          .orElse(verifyUserCredentials(req))
          .orElse(getViewPermissions(req))
          .orElse(getAllApiProductsV600(req))
          .orElse(getAllProductsV600(req))
          .orElse(getAccountAccessRequestsForAccount(req))
          .orElse(getAccountAccessRequestById(req))
          .orElse(getHoldingAccountByReleaser(req))
          .orElse(createAccountAccessRequest(req))
          .orElse(approveAccountAccessRequest(req))
          .orElse(rejectAccountAccessRequest(req))
          .orElse(getSignalChannels(req))
          .orElse(getSignalChannelInfo(req))
          .orElse(getSignalStats(req))
          .orElse(publishSignalMessage(req))
          .orElse(getSignalMessages(req))
          .orElse(deleteSignalChannel(req))
          .orElse(getBankChatRooms(req))
          .orElse(getSystemChatRooms(req))
          .orElse(getBankChatRoom(req))
          .orElse(getSystemChatRoom(req))
          .orElse(getMyChatRooms(req))
          .orElse(getMyUnreadCounts(req))
          .orElse(markChatRoomRead(req))
          .orElse(getMyMentions(req))
          .orElse(searchChatRooms(req))
          .orElse(getBulkReactions(req))
          .orElse(archiveBankChatRoom(req))
          .orElse(archiveSystemChatRoom(req))
          .orElse(joinBankChatRoom(req))
          .orElse(refreshBankJoiningKey(req))
          .orElse(refreshSystemJoiningKey(req))
          .orElse(createBankChatRoom(req))
          .orElse(createSystemChatRoom(req))
          .orElse(updateBankChatRoom(req))
          .orElse(updateSystemChatRoom(req))
          .orElse(deleteBankChatRoom(req))
          .orElse(deleteSystemChatRoom(req))
          .orElse(setBankChatRoomOpenRoom(req))
          .orElse(setSystemChatRoomOpenRoom(req))
          .orElse(addBankChatRoomParticipant(req))
          .orElse(addSystemChatRoomParticipant(req))
          .orElse(getBankChatRoomParticipants(req))
          .orElse(getSystemChatRoomParticipants(req))
          .orElse(updateBankParticipantPermissions(req))
          .orElse(updateSystemParticipantPermissions(req))
          .orElse(removeBankChatRoomParticipant(req))
          .orElse(removeSystemChatRoomParticipant(req))
          .orElse(sendBankChatMessage(req))
          .orElse(sendSystemChatMessage(req))
          .orElse(getBankChatMessages(req))
          .orElse(getSystemChatMessages(req))
          .orElse(getBankChatMessage(req))
          .orElse(getSystemChatMessage(req))
          .orElse(editBankChatMessage(req))
          .orElse(editSystemChatMessage(req))
          .orElse(deleteBankChatMessage(req))
          .orElse(deleteSystemChatMessage(req))
          .orElse(getBankThreadReplies(req))
          .orElse(getSystemThreadReplies(req))
          .orElse(replyInBankThread(req))
          .orElse(replyInSystemThread(req))
          .orElse(addBankReaction(req))
          .orElse(addSystemReaction(req))
          .orElse(removeBankReaction(req))
          .orElse(removeSystemReaction(req))
          .orElse(getBankReactions(req))
          .orElse(getSystemReactions(req))
          .orElse(signalBankTyping(req))
          .orElse(signalSystemTyping(req))
          .orElse(getBankTypingUsers(req))
          .orElse(getSystemTypingUsers(req))
          .orElse(createSignatoryPanel(req))
          .orElse(getSignatoryPanels(req))
          .orElse(getSignatoryPanel(req))
          .orElse(updateSignatoryPanel(req))
          .orElse(deleteSignatoryPanel(req))
          .orElse(validateUserEmail(req))
          .orElse(resetPasswordComplete(req))
          .orElse(resetPasswordUrlAnonymous(req))
          .orElse(validateDynamicResourceDoc(req))
          .orElse(createTransactionRequestHold(req))
          .orElse(createTransactionRequestCardano(req))
          .orElse(createTransactionRequestEthereumeSendTransaction(req))
          .orElse(createTransactionRequestEthSendRawTransaction(req))
          .orElse(getUserGroupMemberships(req))
          .orElse(getUsersWithAccountAccess(req))
          .orElse(createRetailCustomer(req))
          .orElse(createCorporateCustomer(req))
          .orElse(getUserByUserId(req))
          .orElse(directLoginEndpoint(req))
          .orElse(validateAbacRule(req))
          .orElse(executeAbacRule(req))
          .orElse(executeAbacPolicy(req))
          .orElse(getAbacRuleSchema(req))
          .orElse(backupSystemDynamicEntity(req))
          .orElse(backupBankLevelDynamicEntity(req))
          .orElse(deleteSystemDynamicEntityCascade(req))
          // createCorporateCustomer + createRetailCustomer deferred — share
          // the 60-line date-parsing/customer-number generation logic of
          // createCustomer (already migrated); will batch as a focused pass.
      }

    // ─── Phase 2: corporate-customers + retail-customers + banks/customers/* (8) ───

    // Route: GET /obp/v6.0.0/banks/BANK_ID/corporate-customers
    lazy val getCorporateCustomersAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "corporate-customers" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            (customers, _) <- NewStyle.function.getCustomersByCustomerTypes(
              bank.bankId, List("CORPORATE", "SUBSIDIARY"), Some(cc), requestParams)
          } yield JSONFactory600.createCustomersJson(customers)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCorporateCustomersAtOneBank), "GET",
      "/banks/BANK_ID/corporate-customers", "Get Corporate Customers at Bank",
      """Get all corporate (and subsidiary) customers at the specified bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCorporateCustomersAtOneBank)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/corporate-customers/CUSTOMER_ID
    lazy val getCorporateCustomerByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "corporate-customers" / customerId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- Helper.booleanToFuture(CustomerTypeMismatch, 404, callContext) {
              customer.customerType.exists(ct => List("CORPORATE", "SUBSIDIARY").contains(ct))
            }
            (attrs, _) <- NewStyle.function.getCustomerAttributes(bank.bankId, CustomerId(customerId), callContext)
          } yield JSONFactory600.createCustomerWithAttributesJson(customer, attrs)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCorporateCustomerByCustomerId), "GET",
      "/banks/BANK_ID/corporate-customers/CUSTOMER_ID", "Get Corporate Customer by Id",
      """Get a corporate customer by CUSTOMER_ID. Returns 404 if the customer
        |is not of type CORPORATE or SUBSIDIARY.""".stripMargin,
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, CustomerTypeMismatch, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCorporateCustomerByCustomerId)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/corporate-customers/CUSTOMER_ID/subsidiaries
    lazy val getCorporateCustomerSubsidiaries: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "corporate-customers" / customerId / "subsidiaries" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- Helper.booleanToFuture(CustomerTypeMismatch, 404, callContext) {
              customer.customerType.exists(ct => List("CORPORATE", "SUBSIDIARY").contains(ct))
            }
            (children, _) <- NewStyle.function.getCustomersByParentCustomerId(bank.bankId, customerId, callContext)
          } yield JSONFactory600.createCustomersJson(children)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCorporateCustomerSubsidiaries), "GET",
      "/banks/BANK_ID/corporate-customers/CUSTOMER_ID/subsidiaries", "Get Subsidiaries",
      """Get the subsidiaries of a corporate customer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, CustomerTypeMismatch, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCorporateCustomerSubsidiaries)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/retail-customers
    lazy val getRetailCustomersAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "retail-customers" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(
              req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            (customers, _) <- NewStyle.function.getCustomersByCustomerTypes(
              bank.bankId, List("INDIVIDUAL"), Some(cc), requestParams)
          } yield JSONFactory600.createCustomersJson(customers)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getRetailCustomersAtOneBank), "GET",
      "/banks/BANK_ID/retail-customers", "Get Retail Customers at Bank",
      """Get all retail (individual) customers at the specified bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getRetailCustomersAtOneBank)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/retail-customers/CUSTOMER_ID
    lazy val getRetailCustomerByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "retail-customers" / customerId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- Helper.booleanToFuture(CustomerTypeMismatch, 404, callContext) {
              customer.customerType.contains("INDIVIDUAL")
            }
            (attrs, _) <- NewStyle.function.getCustomerAttributes(bank.bankId, CustomerId(customerId), callContext)
          } yield JSONFactory600.createCustomerWithAttributesJson(customer, attrs)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getRetailCustomerByCustomerId), "GET",
      "/banks/BANK_ID/retail-customers/CUSTOMER_ID", "Get Retail Customer by Id",
      """Get a retail customer by CUSTOMER_ID. Returns 404 if the customer
        |is not of type INDIVIDUAL.""".stripMargin,
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, CustomerTypeMismatch, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getRetailCustomerByCustomerId)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customers/CUSTOMER_ID/children
    lazy val getCustomerChildren: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId / "children" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            _ <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (children, _) <- NewStyle.function.getCustomersByParentCustomerId(bank.bankId, customerId, Some(cc))
          } yield JSONFactory600.createCustomersJson(children)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerChildren), "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/children", "Get Customer Children",
      """Get the child customers for the specified customer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomersAtOneBank :: Nil),
      http4sPartialFunction = Some(getCustomerChildren)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customers/CUSTOMER_ID/customer-links
    lazy val getCustomerLinksByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId / "customer-links" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            _ <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            (links, _) <- NewStyle.function.getCustomerLinksByCustomerId(customerId, Some(cc))
          } yield JSONFactory600.createCustomerLinksJson(links)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerLinksByCustomerId), "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/customer-links", "Get Customer Links by Customer Id",
      """Get all customer links involving the specified customer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomerLinks :: Nil),
      http4sPartialFunction = Some(getCustomerLinksByCustomerId)
    )

    // ─── Phase 2: six 2-endpoint management/* buckets (9 of 12) ───────────
    // Deferred: executeAbacPolicy (large response-building chain),
    // backupSystemDynamicEntity (private backupDynamicEntityMethod helper),
    // deleteSystemDynamicEntityCascade (private deleteDynamicEntityCascadeMethod).

    // GET /obp/v6.0.0/management/system-views
    lazy val getSystemViews: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system-views" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Views.views.vend.getSystemViews().map(JSONFactory600.createViewsJsonV600)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSystemViews), "GET",
      "/management/system-views", "Get System Views",
      """Get all system views.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagView :: Nil, Some(canGetSystemViews :: Nil),
      http4sPartialFunction = Some(getSystemViews))

    // GET /obp/v6.0.0/management/system-views/SYS_VIEW_ID  (non-standard var so middleware skips view validation)
    lazy val getSystemViewById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system-views" / viewIdStr =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          ViewNewStyle.systemView(ViewId(viewIdStr), Some(cc)).map(JSONFactory600.createViewJsonV600)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSystemViewById), "GET",
      "/management/system-views/SYS_VIEW_ID", "Get System View by Id",
      """Get a system view by its VIEW_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagView :: Nil, Some(canGetSystemViews :: Nil),
      http4sPartialFunction = Some(getSystemViewById))

    // GET /obp/v6.0.0/management/abac-policies
    lazy val getAbacPolicies: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "abac-policies" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val policies = Constant.ABAC_POLICIES.map { p =>
              AbacPolicyJsonV600(policy = p,
                description = Constant.ABAC_POLICY_DESCRIPTIONS.getOrElse(p, "No description available"))
            }
            AbacPoliciesJsonV600(policies)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAbacPolicies), "GET",
      "/management/abac-policies", "Get ABAC Policies",
      """List all available ABAC policies.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagABAC :: Nil, Some(canGetAbacRule :: Nil),
      http4sPartialFunction = Some(getAbacPolicies))

    // GET /obp/v6.0.0/management/connector/metrics/counts
    lazy val getConnectorCallCounts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "connector" / "metrics" / "counts" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val counts = code.metrics.ConnectorCountsRedis.getAllCounts()
            ConnectorCountsJsonV600(
              enabled = code.metrics.ConnectorCountsRedis.isEnabled,
              connector_counts = counts.map(c => ConnectorCountJsonV600(
                connector_name = c.connector_name, method_name = c.method_name,
                per_hour_outbound_count = c.per_hour_outbound_count,
                per_hour_inbound_success_count = c.per_hour_inbound_success_count,
                per_hour_inbound_failure_count = c.per_hour_inbound_failure_count,
                ttl_seconds = c.ttl_seconds)))
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConnectorCallCounts), "GET",
      "/management/connector/metrics/counts", "Get Connector Call Counts",
      """Per-hour Redis counters for connector outbound and inbound messages.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagMetric :: Nil, Some(canReadMetrics :: Nil),
      http4sPartialFunction = Some(getConnectorCallCounts))

    // GET /obp/v6.0.0/management/connector/traces
    lazy val getConnectorTraces: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "connector" / "traces" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            traces <- Future(code.metrics.ConnectorTraceProvider.getAllConnectorTraces(obpQueryParams))
          } yield JSONFactory600.createConnectorTracesJsonV600(traces)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConnectorTraces), "GET",
      "/management/connector/traces", "Get Connector Traces",
      """Get recent connector trace records (paginated).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagMetric :: Nil, None,
      http4sPartialFunction = Some(getConnectorTraces))

    // GET /obp/v6.0.0/management/diagnostics/dynamic-entities
    lazy val getDynamicEntityDiagnostics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "diagnostics" / "dynamic-entities" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val result = code.api.util.DiagnosticDynamicEntityCheck.checkAllDynamicEntities()
            val issuesJson = result.issues.map(i => JSONFactory600.DynamicEntityIssueJsonV600(
              entity_name = i.entityName, bank_id = i.bankId.getOrElse("SYSTEM_LEVEL"),
              field_name = i.fieldName, example_value = i.exampleValue, error_message = i.errorMessage))
            val orphanedJson = result.orphanedEntities.map(o =>
              JSONFactory600.OrphanedDynamicEntityJsonV600(o.entityName, o.bankId, o.recordCount))
            JSONFactory600.DynamicEntityDiagnosticsJsonV600(result.scannedEntities, issuesJson, result.issues.length, orphanedJson)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getDynamicEntityDiagnostics), "GET",
      "/management/diagnostics/dynamic-entities", "Get Dynamic Entity Diagnostics",
      """Scan all Dynamic Entities for issues + orphaned data records.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagManageDynamicEntity :: Nil,
      Some(canGetDynamicEntityDiagnostics :: Nil),
      http4sPartialFunction = Some(getDynamicEntityDiagnostics))

    // DELETE /obp/v6.0.0/management/diagnostics/dynamic-entities/orphaned-records
    lazy val cleanupOrphanedDynamicEntityRecords: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "diagnostics" / "dynamic-entities" / "orphaned-records" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val definitions = code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities(None, true)
            val orphaned = code.api.util.DiagnosticDynamicEntityCheck.checkOrphanedRecords(definitions)
            var totalDeleted: Long = 0
            orphaned.foreach { orphan =>
              val records = if (orphan.bankId.isEmpty)
                DynamicData.findAll(By(DynamicData.DynamicEntityName, orphan.entityName), NullRef(DynamicData.BankId))
              else
                DynamicData.findAll(By(DynamicData.DynamicEntityName, orphan.entityName), By(DynamicData.BankId, orphan.bankId))
              records.foreach { r => r.delete_!; totalDeleted += 1 }
            }
            val orphanedJson = orphaned.map(o => JSONFactory600.OrphanedDynamicEntityJsonV600(o.entityName, o.bankId, o.recordCount))
            JSONFactory600.CleanupOrphanedDynamicEntityResponseJsonV600(orphanedJson, totalDeleted)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(cleanupOrphanedDynamicEntityRecords), "DELETE",
      "/management/diagnostics/dynamic-entities/orphaned-records", "Cleanup Orphaned Dynamic Entity Records",
      """Delete orphaned dynamic-entity data records (rows whose entityName/bankId has no matching definition).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagManageDynamicEntity :: Nil,
      Some(canCleanupOrphanedDynamicEntityRecords :: Nil),
      http4sPartialFunction = Some(cleanupOrphanedDynamicEntityRecords))

    // PUT /obp/v6.0.0/management/webui_props/WEBUI_PROP_NAME
    lazy val createOrUpdateWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "webui_props" / webUiPropName =>
        implicit val cc: CallContext = req.callContext
        implicit val formats: Formats = code.api.util.CustomJsonFormats.formats
        val rawBody = cc.httpBody.getOrElse("")
        val nameLower = webUiPropName.toLowerCase
        val fut: Future[(WebUiPropsCommons, Boolean)] = for {
          _ <- NewStyle.function.tryons(s"$InvalidWebUiProps name must start with webui_, but current name is: $nameLower", 400, Some(cc)) {
            require(nameLower.startsWith("webui_"))
          }
          _ <- NewStyle.function.tryons(s"$InvalidWebUiProps name must contain only alphanumeric characters, underscore, and dot. Current name: $nameLower", 400, Some(cc)) {
            require(nameLower.matches("^[a-zA-Z0-9_.]+$"))
          }
          _ <- NewStyle.function.tryons(s"$InvalidWebUiProps name must not exceed 255 characters. Current length: ${nameLower.length}", 400, Some(cc)) {
            require(nameLower.length <= 255)
          }
          existing <- Future(MappedWebUiPropsProvider.getByName(nameLower))
          resourceExists = existing.isDefined
          valueJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should contain a value field", 400, Some(cc)) {
            net.liftweb.json.parse(rawBody).extract[code.webuiprops.WebUiPropsPutJsonV600]
          }
          saved <- Future(MappedWebUiPropsProvider.createOrUpdate(WebUiPropsCommons(nameLower, valueJson.value)))
        } yield (saved.openOrThrowException("Could not save WebUiProps"), resourceExists)

        RequestScopeConnection.fromFuture(fut).attempt.flatMap {
          case Right((commons, existed)) =>
            val jsonString = prettyRender(Extraction.decompose(commons))
            if (existed) Ok(jsonString) else Created(jsonString)
          case Left(err) =>
            ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createOrUpdateWebUiProps), "PUT",
      "/management/webui_props/WEBUI_PROP_NAME", "Create or Update WebUiProps",
      """Create or update a WebUiProps. Name is converted to lowercase, must start with `webui_`.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidWebUiProps, InvalidJsonFormat, UnknownError),
      apiTagWebUiProps :: Nil,
      Some(canCreateWebUiProps :: Nil),
      http4sPartialFunction = Some(createOrUpdateWebUiProps))

    // DELETE /obp/v6.0.0/management/webui_props/WEBUI_PROP_NAME
    lazy val deleteWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "webui_props" / webUiPropName =>
        EndpointHelpers.executeDelete(req) { cc =>
          val nameLower = webUiPropName.toLowerCase
          for {
            _ <- NewStyle.function.tryons(s"$InvalidWebUiProps name must start with webui_, but current name is: $nameLower", 400, Some(cc)) {
              require(nameLower.startsWith("webui_"))
            }
            _ <- NewStyle.function.tryons(s"$InvalidWebUiProps name must contain only alphanumeric characters, underscore, and dot. Current name: $nameLower", 400, Some(cc)) {
              require(nameLower.matches("^[a-zA-Z0-9_.]+$"))
            }
            _ <- NewStyle.function.tryons(s"$InvalidWebUiProps name must not exceed 255 characters. Current length: ${nameLower.length}", 400, Some(cc)) {
              require(nameLower.length <= 255)
            }
            existing <- Future(MappedWebUiPropsProvider.getByName(nameLower))
            _ <- existing match {
              case Full(prop) => Future(MappedWebUiPropsProvider.delete(prop.webUiPropsId.getOrElse("")))
              case _ => Future.successful(Full(true))
            }
          } yield ()
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteWebUiProps), "DELETE",
      "/management/webui_props/WEBUI_PROP_NAME", "Delete WebUiProps",
      """Delete a WebUiProps by name. Idempotent — 204 even if it didn't exist.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidWebUiProps, UnknownError),
      apiTagWebUiProps :: Nil,
      Some(canDeleteWebUiProps :: Nil),
      http4sPartialFunction = Some(deleteWebUiProps))

    // ─── Phase 2: 3 small mixed buckets (5 endpoints) ─────────────────────

    // POST /obp/v6.0.0/management/banks/BANK_ID/accounts/ACCOUNT_ID/views (201)
    lazy val createCustomViewManagement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "accounts" / accountIdStr / "views" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bankId = BankId(bankIdStr)
          val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
          for {
            createViewJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the CreateViewJson", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[com.openbankproject.commons.model.CreateViewJson]
            }
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current view_name (${createViewJson.name})", cc = Some(cc)) {
              APIUtil.isValidCustomViewName(createViewJson.name)
            }
            (_, _) <- NewStyle.function.getBankAccount(bankId, accountId, Some(cc))
            (view, _) <- ViewNewStyle.createCustomView(BankIdAccountId(bankId, accountId), createViewJson, Some(cc))
          } yield JSONFactory600.createViewJsonV600(view)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCustomViewManagement), "POST",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/views", "Create Custom View (Management)",
      """Create a custom view for an account.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, InvalidJsonFormat, InvalidCustomViewFormat, UnknownError),
      apiTagView :: Nil,
      Some(canCreateCustomView :: Nil),
      http4sPartialFunction = Some(createCustomViewManagement))

    // GET /obp/v6.0.0/banks/BANK_ID/products/PRODUCT_CODE/tags
    lazy val getProductTagsV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "products" / productCodeStr / "tags" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val productCode = com.openbankproject.commons.model.ProductCode(productCodeStr)
          for {
            (_, _) <- NewStyle.function.getProduct(bank.bankId, productCode, Some(cc))
            tags = code.products.ProductTagsProvider.getTags(bank.bankId, productCode)
          } yield JSONFactory600.createProductTagsJsonV600(tags)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getProductTagsV600), "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE/tags", "Get Product Tags",
      """Get all tags for the specified bank product.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      apiTagProduct :: Nil, None,
      http4sPartialFunction = Some(getProductTagsV600))

    // PUT /obp/v6.0.0/banks/BANK_ID/products/PRODUCT_CODE/tags
    lazy val updateProductTagsV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "products" / productCodeStr / "tags" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          val productCode = com.openbankproject.commons.model.ProductCode(productCodeStr)
          val updateProductTagsEntitlements = canUpdateProductTagsAtOneBank :: canUpdateProductTagsAtAnyBank :: Nil
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(
              failMsg = UserHasMissingRoles + updateProductTagsEntitlements.mkString(" or "))(
              bank.bankId.value, user.userId, updateProductTagsEntitlements, Some(cc))
            (_, _) <- NewStyle.function.getProduct(bank.bankId, productCode, Some(cc))
            body <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ProductTagsJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ProductTagsJsonV600]
            }
            updatedTags <- NewStyle.function.tryons(UpdateProductError, 400, Some(cc)) {
              code.products.ProductTagsProvider.setTags(bank.bankId, productCode, body.tags)
                .openOrThrowException(UpdateProductError)
            }
          } yield JSONFactory600.createProductTagsJsonV600(updatedTags)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateProductTagsV600), "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE/tags", "Update Product Tags",
      """Replace the tags on the specified bank product.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UpdateProductError, UnknownError),
      apiTagProduct :: Nil, None,
      http4sPartialFunction = Some(updateProductTagsV600))

    // GET /obp/v6.0.0/oidc/clients/CLIENT_ID
    lazy val getOidcClient: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "oidc" / "clients" / clientId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            consumerBox <- Future(code.consumer.Consumers.consumers.vend.getConsumerByConsumerKey(clientId))
            consumer <- NewStyle.function.tryons(s"OBP-OIDC-003: Client not found: $clientId", 404, Some(cc)) {
              consumerBox match {
                case Full(c) => c
                case _ => throw new RuntimeException("Client not found")
              }
            }
          } yield {
            val redirectUris = Option(consumer.redirectURL.get).filter(_.nonEmpty)
              .map(_.split("[,\\s]+").map(_.trim).filter(_.nonEmpty).toList).getOrElse(List.empty)
            GetOidcClientResponseJsonV600(
              client_id = clientId, client_name = consumer.name.get,
              consumer_id = consumer.consumerId.get,
              redirect_uris = redirectUris, enabled = consumer.isActive.get)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getOidcClient), "GET",
      "/oidc/clients/CLIENT_ID", "Get OIDC Client",
      """Get an OIDC/OAuth2 client's metadata by client_id.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagOIDC :: apiTagConsumer :: apiTagOAuth :: Nil,
      Some(canGetOidcClient :: Nil),
      authMode = code.api.util.APIUtil.UserOrApplication,
      http4sPartialFunction = Some(getOidcClient))

    // POST /obp/v6.0.0/oidc/clients/verify
    lazy val verifyOidcClient: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "oidc" / "clients" / "verify" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the VerifyOidcClientRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[VerifyOidcClientRequestJsonV600]
            }
            consumerBox <- Future(code.consumer.Consumers.consumers.vend.getConsumerByConsumerKey(postedData.client_id))
          } yield {
            consumerBox match {
              case Full(consumer) if consumer.isActive.get && consumer.secret.get == postedData.client_secret =>
                val redirectUris = Option(consumer.redirectURL.get).filter(_.nonEmpty)
                  .map(_.split("[,\\s]+").map(_.trim).filter(_.nonEmpty).toList)
                VerifyOidcClientResponseJsonV600(
                  valid = true,
                  client_id = Some(postedData.client_id),
                  consumer_id = Some(consumer.consumerId.get),
                  redirect_uris = redirectUris)
              case _ => VerifyOidcClientResponseJsonV600(valid = false)
            }
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(verifyOidcClient), "POST",
      "/oidc/clients/verify", "Verify OIDC Client",
      """Verify an OIDC client_id + client_secret pair. Returns valid=true on a successful match.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagOIDC :: apiTagConsumer :: apiTagOAuth :: Nil,
      Some(canVerifyOidcClient :: Nil),
      authMode = code.api.util.APIUtil.UserOrApplication,
      http4sPartialFunction = Some(verifyOidcClient))

    // ─── Phase 2: users bucket (6 of 16; chat-room + special-purpose deferred) ───

    // GET /obp/v6.0.0/users/USER_ID/attributes/USER_ATTRIBUTE_ID
    lazy val getUserAttributeById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userIdStr / "attributes" / userAttributeId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userIdStr, Some(cc))
            (attributes, _) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
            attribute <- Future(attributes.find(_.userAttributeId == userAttributeId))
              .map(unboxFullOrFail(_, Some(cc), UserAttributeNotFound, 404))
          } yield JSONFactory510.createUserAttributeJson(attribute)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getUserAttributeById), "GET",
      "/users/USER_ID/attributes/USER_ATTRIBUTE_ID", "Get User Attribute by Id",
      """Get a user attribute by USER_ATTRIBUTE_ID for the specified user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserAttributeNotFound, UnknownError),
      apiTagUser :: apiTagUserAttribute :: Nil,
      Some(canGetUserAttributes :: Nil),
      http4sPartialFunction = Some(getUserAttributeById))

    // POST /obp/v6.0.0/users/USER_ID/attributes (201)
    lazy val createUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userIdStr / "attributes" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userIdStr, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UserAttributeJsonV510]
            }
            userAttributeType <- NewStyle.function.tryons(personalDataTypeErrorMsg, 400, Some(cc)) {
              UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, None, postedData.name, userAttributeType, postedData.value, false, Some(cc))
          } yield JSONFactory510.createUserAttributeJson(userAttribute)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createUserAttribute), "POST",
      "/users/USER_ID/attributes", "Create User Attribute",
      """Create a non-personal user attribute for the specified user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagUser :: apiTagUserAttribute :: Nil,
      Some(canCreateUserAttribute :: Nil),
      http4sPartialFunction = Some(createUserAttribute))

    // PUT /obp/v6.0.0/users/USER_ID/attributes/USER_ATTRIBUTE_ID
    lazy val updateUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "users" / userIdStr / "attributes" / userAttributeId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userIdStr, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UserAttributeJsonV510]
            }
            userAttributeType <- NewStyle.function.tryons(personalDataTypeErrorMsg, 400, Some(cc)) {
              UserAttributeType.withName(postedData.`type`)
            }
            (attributes, _) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
            _ <- Future(attributes.find(_.userAttributeId == userAttributeId))
              .map(unboxFullOrFail(_, Some(cc), UserAttributeNotFound, 404))
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, Some(userAttributeId), postedData.name, userAttributeType, postedData.value, false, Some(cc))
          } yield JSONFactory510.createUserAttributeJson(userAttribute)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateUserAttribute), "PUT",
      "/users/USER_ID/attributes/USER_ATTRIBUTE_ID", "Update User Attribute",
      """Update a user attribute by USER_ATTRIBUTE_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UserAttributeNotFound, UnknownError),
      apiTagUser :: apiTagUserAttribute :: Nil,
      Some(canUpdateUserAttribute :: Nil),
      http4sPartialFunction = Some(updateUserAttribute))

    // DELETE /obp/v6.0.0/users/USER_ID/attributes/USER_ATTRIBUTE_ID
    lazy val deleteUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userIdStr / "attributes" / userAttributeId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userIdStr, Some(cc))
            (attributes, _) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
            _ <- Future(attributes.find(_.userAttributeId == userAttributeId))
              .map(unboxFullOrFail(_, Some(cc), UserAttributeNotFound, 404))
            _ <- BankConnector.connector.vend.deleteUserAttribute(userAttributeId, Some(cc))
          } yield ""
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteUserAttribute), "DELETE",
      "/users/USER_ID/attributes/USER_ATTRIBUTE_ID", "Delete User Attribute",
      """Delete a user attribute by USER_ATTRIBUTE_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserAttributeNotFound, UnknownError),
      apiTagUser :: apiTagUserAttribute :: Nil,
      Some(canDeleteUserAttribute :: Nil),
      http4sPartialFunction = Some(deleteUserAttribute))

    // POST /obp/v6.0.0/users/USER_ID/group-entitlements (201)
    lazy val addUserToGroup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userIdStr / "group-entitlements" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostGroupMembershipJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.PostGroupMembershipJsonV600]
            }
            _ <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            group <- Future(code.group.GroupTrait.group.vend.getGroup(postJson.group_id))
              .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Group not found", 404))
            _ <- groupRoleCheck(group.bankId, user.userId, canAddUserToGroupAtOneBank, canAddUserToGroupAtAllBanks, cc)
            _ <- Helper.booleanToFuture(s"$UnknownError Group is not enabled", 400, Some(cc))(group.isEnabled)
            existingEntitlements <- Future(Entitlement.entitlement.vend.getEntitlementsByUserId(userIdStr))
            entitlementResults <- Future.sequence(group.listOfRoles.map { roleName =>
              Future {
                val alreadyHas = existingEntitlements.toOption.exists(_.exists { ent =>
                  ent.roleName == roleName && ent.bankId == group.bankId.getOrElse("")
                })
                if (!alreadyHas) {
                  Entitlement.entitlement.vend.addEntitlement(
                    group.bankId.getOrElse(""), userIdStr, roleName, "manual",
                    None, Some(postJson.group_id), Some("GROUP_MEMBERSHIP"))
                  (roleName, true)
                } else (roleName, false)
              }
            })
            entitlementsAdded = entitlementResults.filter(_._2).map(_._1)
            entitlementsAlreadyPresent = entitlementResults.filterNot(_._2).map(_._1)
          } yield JSONFactory600.AddUserToGroupResponseJsonV600(
            group_id = group.groupId, user_id = userIdStr, bank_id = group.bankId,
            group_name = group.groupName, target_entitlements = group.listOfRoles,
            entitlements_created = entitlementsAdded,
            entitlements_skipped = entitlementsAlreadyPresent)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(addUserToGroup), "POST",
      "/users/USER_ID/group-entitlements", "Add User to Group",
      """Add a user to a group (grants the group's entitlements to the user).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagGroup :: apiTagUser :: Nil, None,
      http4sPartialFunction = Some(addUserToGroup))

    // DELETE /obp/v6.0.0/users/USER_ID/group-entitlements/GROUP_ID
    lazy val removeUserFromGroup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userIdStr / "group-entitlements" / groupId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            _ <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            group <- Future(code.group.GroupTrait.group.vend.getGroup(groupId))
              .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Group not found", 404))
            _ <- groupRoleCheck(group.bankId, user.userId, canRemoveUserFromGroupAtOneBank, canRemoveUserFromGroupAtAllBanks, cc)
            entitlements <- Future(Entitlement.entitlement.vend.getEntitlementsByUserId(userIdStr))
            groupEntitlements = entitlements.toOption.getOrElse(List.empty).filter(e =>
              e.groupId == Some(groupId) && e.process == Some("GROUP_MEMBERSHIP"))
            _ <- Future.sequence(groupEntitlements.map(e =>
              Future(Entitlement.entitlement.vend.deleteEntitlement(Full(e)))))
          } yield ""
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(removeUserFromGroup), "DELETE",
      "/users/USER_ID/group-entitlements/GROUP_ID", "Remove User from Group",
      """Remove a user from a group (deletes all entitlements that came from the group).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagGroup :: apiTagUser :: Nil, None,
      http4sPartialFunction = Some(removeUserFromGroup))

    // ─── Phase 2: 4 more single-endpoint buckets ──────────────────────────

    // DELETE /obp/v6.0.0/entitlements/ENTITLEMENT_ID
    lazy val deleteEntitlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "entitlements" / entitlementId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            entitlementBox <- Future(Entitlement.entitlement.vend.getEntitlementById(entitlementId))
            _ <- entitlementBox match {
              case Full(ent) => Future(Entitlement.entitlement.vend.deleteEntitlement(Some(ent)))
              case _ => Future.successful(Full(()))
            }
          } yield ""
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteEntitlement), "DELETE",
      "/entitlements/ENTITLEMENT_ID", "Delete Entitlement",
      """Delete an entitlement by ENTITLEMENT_ID. Idempotent.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagEntitlement :: Nil,
      Some(canDeleteEntitlementAtAnyBank :: Nil),
      http4sPartialFunction = Some(deleteEntitlement))

    // GET /obp/v6.0.0/personal-dynamic-entities/available
    lazy val getAvailablePersonalDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "personal-dynamic-entities" / "available" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future(NewStyle.function.getDynamicEntities(None, true))
            .map(all => JSONFactory600.createMyDynamicEntitiesJson(all.filter(_.hasPersonalEntity)))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAvailablePersonalDynamicEntities), "GET",
      "/personal-dynamic-entities/available", "Get Available Personal Dynamic Entities",
      """List Dynamic Entities that support personal data storage.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagManageDynamicEntity :: apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getAvailablePersonalDynamicEntities))

    // GET /obp/v6.0.0/management/dynamic-entities/reference-types
    lazy val getReferenceTypes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "dynamic-entities" / "reference-types" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val referenceTypeNames = code.dynamicEntity.ReferenceType.referenceTypeNames
            val dynamicEntityNames = NewStyle.function.getDynamicEntities(None, true)
              .map(e => s"reference:${e.entityName}").toSet
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
                  val description = if (dynamicEntityNames.contains(refTypeName))
                    s"Reference to $entityName (dynamic entity)"
                  else s"Reference to $entityName entity"
                  (exampleId1, description)
                case reg2(a, b) =>
                  (s"$a=$exampleId1&$b=$exampleId2", s"Composite reference with $a and $b")
                case reg3(a, b, c) =>
                  (s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3", s"Composite reference with $a, $b and $c")
                case reg4(a, b, c, d) =>
                  (s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3&$d=$exampleId4", s"Composite reference with $a, $b, $c and $d")
                case _ => (exampleId1, "Reference type")
              }
              JSONFactory600.ReferenceTypeJsonV600(
                type_name = refTypeName,
                example_value = example._1,
                description = example._2)
            }
            JSONFactory600.ReferenceTypesJsonV600(referenceTypes)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getReferenceTypes), "GET",
      "/management/dynamic-entities/reference-types", "Get Reference Types for Dynamic Entities",
      """List all reference types available for use in Dynamic Entity field definitions.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagManageDynamicEntity :: Nil,
      Some(canGetDynamicEntityReferenceTypes :: Nil),
      http4sPartialFunction = Some(getReferenceTypes))

    // POST /obp/v6.0.0/chat-room-participants (201) — join a system chat room by joining_key
    lazy val joinSystemChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-room-participants" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            joiningKey <- Future(
              (net.liftweb.json.parse(rawBody) \ "joining_key").extractOpt[String].getOrElse(""))
            room <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomByJoiningKey(joiningKey))
              .map(unboxFullOrFail(_, Some(cc), InvalidJoiningKey, 404))
            _ <- Helper.booleanToFuture(ChatRoomIsArchived, cc = Some(cc))(!room.isArchived)
            existing <- Future(code.chat.ChatPermissions.isParticipant(room.chatRoomId, user.userId))
            _ <- Helper.booleanToFuture(ChatRoomParticipantAlreadyExists, failCode = 409, cc = Some(cc))(existing.isEmpty)
            participant <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .addParticipant(room.chatRoomId, user.userId, "", List.empty, ""))
              .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Cannot join chat room", 400))
          } yield JSONFactory600.createParticipantJson(participant)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(joinSystemChatRoom), "POST",
      "/chat-room-participants", "Join Chat Room",
      """Join a chat room by providing its joining_key.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJoiningKey, ChatRoomIsArchived, ChatRoomParticipantAlreadyExists, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(joinSystemChatRoom))

    // ─── Phase 2: 6 banks/.../accounts subset (counterparty attrs + hasAccountAccess) ───

    private val counterpartyAttributeTypeErrorMsg =
      s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
        s"${com.openbankproject.commons.model.enums.CounterpartyAttributeType.DOUBLE}(12.1234), " +
        s"${com.openbankproject.commons.model.enums.CounterpartyAttributeType.STRING}(TAX_NUMBER), " +
        s"${com.openbankproject.commons.model.enums.CounterpartyAttributeType.INTEGER}(123) and " +
        s"${com.openbankproject.commons.model.enums.CounterpartyAttributeType.DATE_WITH_DAY}(2012-04-23)"

    // POST /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes (201)
    lazy val createCounterpartyAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / "counterparties" / counterpartyId / "attributes" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the CounterpartyAttributeRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CounterpartyAttributeRequestJsonV600]
            }
            counterpartyAttributeType <- NewStyle.function.tryons(counterpartyAttributeTypeErrorMsg, 400, Some(cc)) {
              com.openbankproject.commons.model.enums.CounterpartyAttributeType.withName(postedData.attribute_type)
            }
            (attribute, _) <- code.api.util.newstyle.CounterpartyAttributeNewStyle.createOrUpdateCounterpartyAttribute(
              counterpartyId = com.openbankproject.commons.model.CounterpartyId(counterpartyId),
              counterpartyAttributeId = None,
              name = postedData.name,
              attributeType = counterpartyAttributeType,
              value = postedData.value,
              isActive = postedData.is_active,
              callContext = Some(cc))
          } yield JSONFactory600.createCounterpartyAttributeJson(attribute)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCounterpartyAttribute), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID_PARAM/attributes", "Create Counterparty Attribute",
      """Create a new attribute on the specified counterparty.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagCounterparty :: Nil,
      Some(canCreateCounterpartyAttribute :: Nil),
      http4sPartialFunction = Some(createCounterpartyAttribute))

    // DELETE /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes/COUNTERPARTY_ATTRIBUTE_ID
    lazy val deleteCounterpartyAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / "counterparties" / _ / "attributes" / attributeId =>
        EndpointHelpers.executeDelete(req) { cc =>
          code.api.util.newstyle.CounterpartyAttributeNewStyle.deleteCounterpartyAttribute(attributeId, Some(cc))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteCounterpartyAttribute), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID_PARAM/attributes/COUNTERPARTY_ATTRIBUTE_ID",
      "Delete Counterparty Attribute",
      """Delete a counterparty attribute.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCounterparty :: Nil,
      Some(canDeleteCounterpartyAttribute :: Nil),
      http4sPartialFunction = Some(deleteCounterpartyAttribute))

    // GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes/COUNTERPARTY_ATTRIBUTE_ID
    lazy val getCounterpartyAttributeById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "counterparties" / _ / "attributes" / attributeId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (attribute, _) <- code.api.util.newstyle.CounterpartyAttributeNewStyle.getCounterpartyAttributeById(attributeId, Some(cc))
          } yield JSONFactory600.createCounterpartyAttributeJson(attribute)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCounterpartyAttributeById), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID_PARAM/attributes/COUNTERPARTY_ATTRIBUTE_ID",
      "Get Counterparty Attribute By Id",
      """Get a counterparty attribute by its ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCounterparty :: Nil,
      Some(canGetCounterpartyAttribute :: Nil),
      http4sPartialFunction = Some(getCounterpartyAttributeById))

    // GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes
    lazy val getAllCounterpartyAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "counterparties" / counterpartyId / "attributes" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (attributes, _) <- code.api.util.newstyle.CounterpartyAttributeNewStyle.getCounterpartyAttributes(
              com.openbankproject.commons.model.CounterpartyId(counterpartyId), Some(cc))
          } yield JSONFactory600.createCounterpartyAttributesJson(attributes)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAllCounterpartyAttributes), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID_PARAM/attributes",
      "Get All Counterparty Attributes",
      """Get all attributes for the specified counterparty.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCounterparty :: Nil,
      Some(canGetCounterpartyAttributes :: Nil),
      http4sPartialFunction = Some(getAllCounterpartyAttributes))

    // PUT /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes/COUNTERPARTY_ATTRIBUTE_ID
    lazy val updateCounterpartyAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / "counterparties" / counterpartyId / "attributes" / attributeId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the CounterpartyAttributeRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CounterpartyAttributeRequestJsonV600]
            }
            counterpartyAttributeType <- NewStyle.function.tryons(counterpartyAttributeTypeErrorMsg, 400, Some(cc)) {
              com.openbankproject.commons.model.enums.CounterpartyAttributeType.withName(postedData.attribute_type)
            }
            (updated, _) <- code.api.util.newstyle.CounterpartyAttributeNewStyle.createOrUpdateCounterpartyAttribute(
              counterpartyId = com.openbankproject.commons.model.CounterpartyId(counterpartyId),
              counterpartyAttributeId = Some(attributeId),
              name = postedData.name,
              attributeType = counterpartyAttributeType,
              value = postedData.value,
              isActive = postedData.is_active,
              callContext = Some(cc))
          } yield JSONFactory600.createCounterpartyAttributeJson(updated)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateCounterpartyAttribute), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID_PARAM/attributes/COUNTERPARTY_ATTRIBUTE_ID",
      "Update Counterparty Attribute",
      """Update a counterparty attribute by its ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagCounterparty :: Nil,
      Some(canUpdateCounterpartyAttribute :: Nil),
      http4sPartialFunction = Some(updateCounterpartyAttribute))

    // GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/has-account-access
    lazy val hasAccountAccess: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "has-account-access" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
          val viewId = ViewId(viewIdStr)
          val bia = BankIdAccountId(bankId, accountId)
          for {
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            _ <- Future {
              Views.views.vend.customViewFuture(viewId, bia).flatMap {
                case Full(v) => Future.successful(Full(v))
                case _ => Views.views.vend.systemViewFuture(viewId)
              }
            }.flatten.map(unboxFullOrFail(_, Some(cc), s"$ViewNotFound Current ViewId is ${viewId.value}"))
            accessOpt <- Future(code.views.system.AccountAccess.findByBankIdAccountIdViewIdUserPrimaryKey(
              bankId, accountId, viewId, user.userPrimaryKey))
          } yield accessOpt match {
            case Full(aa) => JSONFactory600.HasAccountAccessJsonV600(
              has_account_access = true,
              access_source = "ACCOUNT_ACCESS",
              account_access_id = aa.id.get.toString,
              abac_rule_id = "")
            case _ => JSONFactory600.HasAccountAccessJsonV600(
              has_account_access = false, access_source = "",
              account_access_id = "", abac_rule_id = "")
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(hasAccountAccess), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/has-account-access", "Has Account Access",
      """Check whether the caller has account access via this view.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, ViewNotFound, UnknownError),
      apiTagAccount :: Nil, None,
      http4sPartialFunction = Some(hasAccountAccess))

    // GET /obp/v6.0.0/my/account-access-requests
    lazy val getMyAccountAccessRequests: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "account-access-requests" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            requests <- Future(code.accountaccessrequest.AccountAccessRequestTrait
              .accountAccessRequest.vend.getByRequestorUserId(user.userId))
              .map(unboxFullOrFail(_, Some(cc), s"$UnknownError Cannot get account access requests", 400))
          } yield JSONFactory600.createAccountAccessRequestsJsonV600(requests)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyAccountAccessRequests), "GET",
      "/my/account-access-requests", "Get My Account Access Requests",
      """List account-access requests submitted by the logged-in user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagAccountAccess :: Nil, None,
      http4sPartialFunction = Some(getMyAccountAccessRequests))

    // ─── Phase 2: 3 anonymous/UserOrApplication endpoints ─────────────────

    // GET /obp/v6.0.0/webui-props/WEBUI_PROP_NAME
    lazy val getWebUiProp: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "webui-props" / webUiPropName =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val active = req.uri.query.params.getOrElse("active", "false")
          for {
            isActived <- NewStyle.function.tryons(
              s"$InvalidFilterParameterFormat `active` must be a boolean, but current `active` value is: $active",
              400, Some(cc)) {
              active.toBoolean
            }
            explicitWebUiProps <- Future(MappedWebUiPropsProvider.getAll())
            explicitProp = explicitWebUiProps.find(_.name == webUiPropName)
            result <- explicitProp match {
              case Some(prop) =>
                Future.successful(
                  WebUiPropsCommons(prop.name, prop.value, prop.webUiPropsId, source = Some("database")))
              case None if isActived =>
                val implicitProps = APIUtil.getWebUIPropsPairs.map { case (k, v) =>
                  WebUiPropsCommons(k, v, webUiPropsId = Some("default"), source = Some("config"))
                }
                implicitProps.find(_.name == webUiPropName) match {
                  case Some(prop) => Future.successful(prop)
                  case None => Future(unboxFullOrFail[WebUiPropsCommons](
                    Empty, Some(cc),
                    s"$WebUiPropsNotFoundByName Current WEBUI_PROP_NAME($webUiPropName)", 400))
                }
              case None =>
                Future(unboxFullOrFail[WebUiPropsCommons](
                  Empty, Some(cc),
                  s"$WebUiPropsNotFoundByName Current WEBUI_PROP_NAME($webUiPropName)", 400))
            }
          } yield result
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getWebUiProp), "GET",
      "/webui-props/WEBUI_PROP_NAME", "Get WebUiProp by Name",
      """Get a single WebUiProp by name. Anonymous endpoint.""",
      EmptyBody, EmptyBody,
      List(WebUiPropsNotFoundByName, InvalidFilterParameterFormat, UnknownError),
      apiTagWebUiProps :: Nil, None,
      http4sPartialFunction = Some(getWebUiProp))

    // GET /obp/v6.0.0/message-docs/CONNECTOR/json-schema
    lazy val getMessageDocsJsonSchema: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "message-docs" / connector / "json-schema" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val cacheKey = s"message-docs-json-schema-$connector"
          val cacheValueFromRedis = code.api.cache.Caching.getStaticSwaggerDocCache(cacheKey)
          for {
            jsonSchema <- if (cacheValueFromRedis.isDefined) {
              NewStyle.function.tryons(s"$UnknownError Cannot parse cached JSON Schema.", 400, Some(cc)) {
                net.liftweb.json.parse(cacheValueFromRedis.get).asInstanceOf[net.liftweb.json.JObject]
              }
            } else {
              NewStyle.function.tryons(s"$UnknownError Cannot generate JSON Schema.", 400, Some(cc)) {
                val connectorObjectBox = net.liftweb.util.Helpers.tryo { BankConnector.getConnectorInstance(connector) }
                val connectorObject = unboxFullOrFail(
                  connectorObjectBox, Some(cc),
                  s"$InvalidConnector Current input is: $connector. Valid connectors include: rabbitmq_vOct2024, rest_vMar2019, akka_vDec2018"
                )
                val schema = code.api.util.JsonSchemaGenerator.messageDocsToJsonSchema(
                  connectorObject.messageDocs.toList, connector)
                val schemaString = net.liftweb.json.compactRender(schema)
                code.api.cache.Caching.setStaticSwaggerDocCache(cacheKey, schemaString)
                schema
              }
            }
          } yield jsonSchema
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMessageDocsJsonSchema), "GET",
      "/message-docs/CONNECTOR/json-schema", "Get Message Docs as JSON Schema",
      """Returns the message-docs for a connector as a JSON Schema. Anonymous endpoint.""",
      EmptyBody, EmptyBody,
      List(InvalidConnector, UnknownError),
      apiTagMessageDoc :: apiTagDocumentation :: apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getMessageDocsJsonSchema))

    // POST /obp/v6.0.0/users/verify-credentials
    lazy val verifyUserCredentials: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / "verify-credentials" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostVerifyUserCredentialsJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostVerifyUserCredentialsJsonV600]
            }
            decodedProvider = java.net.URLDecoder.decode(postedData.provider, java.nio.charset.StandardCharsets.UTF_8)
            resourceUserIdBox = code.model.dataAccess.AuthUser.getResourceUserId(
              postedData.username, postedData.password, decodedProvider)
            _ <- Helper.booleanToFuture(UsernameHasBeenLocked, 401, Some(cc)) {
              resourceUserIdBox != Full(code.model.dataAccess.AuthUser.usernameLockedStateCode)
            }
            _ <- Helper.booleanToFuture(UserEmailNotValidated, 401, Some(cc)) {
              resourceUserIdBox != Full(code.model.dataAccess.AuthUser.userEmailNotValidatedStateCode)
            }
            resourceUserId <- Future(resourceUserIdBox).map(
              unboxFullOrFail(_, Some(cc), s"$InvalidLoginCredentials Failed to authenticate user credentials.", 401))
            user <- Future(code.users.Users.users.vend.getUserByResourceUserId(resourceUserId)).map(
              unboxFullOrFail(_, Some(cc), s"$InvalidLoginCredentials User account not found in system.", 401))
            _ <- Helper.booleanToFuture(s"$InvalidLoginCredentials Authentication provider mismatch.", 401, Some(cc)) {
              decodedProvider.isEmpty || user.provider == decodedProvider
            }
          } yield JSONFactory200.createUserJSON(user)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(verifyUserCredentials), "POST",
      "/users/verify-credentials", "Verify User Credentials",
      """Verify a user's credentials (username, password, provider) and return user information if valid.""",
      EmptyBody, EmptyBody,
      List(UserHasMissingRoles, InvalidJsonFormat, InvalidLoginCredentials, UsernameHasBeenLocked, UnknownError),
      apiTagUser :: Nil,
      Some(canVerifyUserCredentials :: Nil),
      authMode = code.api.util.APIUtil.UserOrApplication,
      http4sPartialFunction = Some(verifyUserCredentials))

    // GET /obp/v6.0.0/management/view-permissions
    lazy val getViewPermissions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "view-permissions" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future {
            def categorize(permission: String): String = permission match {
              case p if p.contains("transaction") && !p.contains("request") => "Transaction"
              case p if p.contains("bank_account") || p.contains("bank_routing") || p.contains("available_funds") => "Account"
              case p if p.contains("other_account") || p.contains("other_bank") ||
                        p.contains("counterparty") || p.contains("more_info") ||
                        p.contains("url") || p.contains("corporates") ||
                        p.contains("location") || p.contains("alias") => "Counterparty"
              case p if p.contains("comment") || p.contains("tag") ||
                        p.contains("image") || p.contains("where_tag") => "Metadata"
              case p if p.contains("transaction_request") || p.contains("direct_debit") ||
                        p.contains("standing_order") => "Transaction Request"
              case p if p.contains("view") => "View"
              case p if p.contains("grant") || p.contains("revoke") => "Access Control"
              case _ => "Other"
            }
            val permissions = ALL_VIEW_PERMISSION_NAMES.map { p =>
              JSONFactory600.ViewPermissionJsonV600(p, categorize(p))
            }.sortBy(p => (p.category, p.permission))
            JSONFactory600.ViewPermissionsJsonV600(permissions)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getViewPermissions), "GET",
      "/management/view-permissions", "Get View Permissions",
      """Get a list of all available view permissions, organised by category.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystemView :: apiTagView :: Nil,
      Some(canGetViewPermissionsAtAllBanks :: Nil),
      http4sPartialFunction = Some(getViewPermissions))

    // GET /obp/v6.0.0/api-products  (all banks; auth-required; cached)
    lazy val getAllApiProductsV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api-products" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          val tagFilter = req.uri.query.params.get("tag").map(_.trim).filter(_.nonEmpty)
          val cacheKey = s"all:${tagFilter.getOrElse("")}"
          val cacheTTL = APIUtil.getPropsAsIntValue("getAllApiProductsV600.cache.ttl.seconds", 5)
          val hit = code.api.cache.Caching.getApiProductsCache(cacheKey, cacheTTL)
            .flatMap(s => try Some(net.liftweb.json.parse(s).extract[ApiProductsJsonV600])
                          catch { case _: Throwable => None })
          hit match {
            case Some(cached) => Future.successful(cached)
            case None =>
              for {
                (banks, _) <- NewStyle.function.getBanks(Some(cc))
                perBank <- Future.sequence(
                  banks.map(b => NewStyle.function.getApiProductsByBankId(b.bankId.value, tagFilter, Some(cc)).map(_._1)))
                apiProducts = perBank.flatten
              } yield {
                val result = JSONFactory600.createApiProductsJsonV600(apiProducts)
                code.api.cache.Caching.setApiProductsCache(
                  cacheKey, net.liftweb.json.compactRender(Extraction.decompose(result)), cacheTTL)
                result
              }
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAllApiProductsV600), "GET",
      "/api-products", "Get Api Products At All Banks",
      """Returns the Api Products across every bank, merged into a single list. Each product carries its bank_id.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagApi :: apiTagApiProduct :: Nil, None,
      http4sPartialFunction = Some(getAllApiProductsV600))

    // GET /obp/v6.0.0/products  (all banks; auth-required; cached)
    lazy val getAllProductsV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "products" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          val params = req.uri.query.multiParams.toList.map { case (k, vs) => GetProductsParam(k, vs.toList) }
          val cacheKey = APIMethods600.productsCacheKey("__all__", params)
          val cacheTTL = APIUtil.getPropsAsIntValue("getAllProductsV600.cache.ttl.seconds", 60)
          val hit = code.api.cache.Caching.getFinancialProductsCache(cacheKey, cacheTTL)
            .flatMap(s => try Some(net.liftweb.json.parse(s).extract[ProductsJsonV600])
                          catch { case _: Throwable => None })
          hit match {
            case Some(cached) => Future.successful(cached)
            case None =>
              for {
                (banks, _) <- NewStyle.function.getBanks(Some(cc))
                perBank <- Future.sequence(
                  banks.map(b => NewStyle.function.getProducts(b.bankId, params, Some(cc)).map(_._1)))
                products = perBank.flatten
              } yield {
                val result = JSONFactory600.createProductsJsonV600(products, Map.empty)
                code.api.cache.Caching.setFinancialProductsCache(
                  cacheKey, net.liftweb.json.compactRender(Extraction.decompose(result)), cacheTTL)
                result
              }
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAllProductsV600), "GET",
      "/products", "Get Products At All Banks",
      """Returns the financial Products offered by every bank merged into a single list. Each product carries its bank_id.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagProduct :: Nil, None,
      http4sPartialFunction = Some(getAllProductsV600))

    // ─── Phase 2: account-access-requests + holding-accounts (3 endpoints) ─

    // GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests
    lazy val getAccountAccessRequestsForAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access-requests" =>
        EndpointHelpers.withBankAccount(req) { (_, _, cc) =>
          val status = req.uri.query.params.get("status")
          for {
            requestsBox <- Future {
              status match {
                case Some(s) =>
                  code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
                    .getByAccountAndStatus(bankIdStr, accountIdStr, s)
                case _ =>
                  code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
                    .getByAccount(bankIdStr, accountIdStr)
              }
            }
            requests <- Future(unboxFullOrFail(requestsBox, Some(cc),
              s"$UnknownError Cannot get account access requests", 400))
          } yield JSONFactory600.createAccountAccessRequestsJsonV600(requests)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAccountAccessRequestsForAccount), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests",
      "Get Account Access Requests for Account",
      """Get all Account Access Requests on the specified account. Optional `status` query param filters by status.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagAccountAccess :: Nil,
      Some(canGetAccountAccessRequestsAtOneBank :: canGetAccountAccessRequestsAtAnyBank :: Nil),
      http4sPartialFunction = Some(getAccountAccessRequestsForAccount))

    // GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID
    lazy val getAccountAccessRequestById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access-requests" / requestId =>
        EndpointHelpers.withBankAccount(req) { (_, _, cc) =>
          for {
            requestBox <- Future {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.getById(requestId)
            }
            request <- Future(unboxFullOrFail(requestBox, Some(cc), AccountAccessRequestNotFound, 404))
            _ <- Helper.booleanToFuture(AccountAccessRequestNotFound, cc = Some(cc)) {
              request.bankId == bankIdStr && request.accountId == accountIdStr
            }
          } yield JSONFactory600.createAccountAccessRequestJsonV600(request)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAccountAccessRequestById), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID",
      "Get Account Access Request by Id",
      """Get a single Account Access Request by its ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, $BankAccountNotFound, AccountAccessRequestNotFound, UnknownError),
      apiTagAccountAccess :: Nil,
      Some(canGetAccountAccessRequestsAtOneBank :: canGetAccountAccessRequestsAtAnyBank :: Nil),
      http4sPartialFunction = Some(getAccountAccessRequestById))

    // GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/holding-accounts
    lazy val getHoldingAccountByReleaser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr / "holding-accounts" =>
        EndpointHelpers.withView(req) { (user, _, view, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
          for {
            (accountIdsBox, _) <- AccountAttributeX.accountAttributeProvider.vend
              .getAccountIdsByParams(bankId, Map("RELEASER_ACCOUNT_ID" -> List(accountId.value)))
              .map(b => (b, Some(cc)))
            accountIds = accountIdsBox.getOrElse(Nil)
            holdingOpt <- {
              def firstHolding(ids: List[String]): Future[Option[com.openbankproject.commons.model.BankAccount]] = ids match {
                case Nil => Future.successful(None)
                case id :: tail =>
                  NewStyle.function.getBankAccount(bankId, com.openbankproject.commons.model.AccountId(id), Some(cc)).flatMap { case (acc, _) =>
                    if (acc.accountType == "HOLDING") Future.successful(Some(acc)) else firstHolding(tail)
                  }
              }
              firstHolding(accountIds)
            }
            holding <- NewStyle.function.tryons($BankAccountNotFound, 404, Some(cc)) { holdingOpt.get }
            moderatedAccount <- Future {
              holding.moderatedBankAccount(view,
                com.openbankproject.commons.model.BankIdAccountId(holding.bankId, holding.accountId),
                Full(user), Some(cc))
            }.map(unboxFullOrFail(_, Some(cc), UnknownError))
            (attributes, _) <- NewStyle.function.getAccountAttributesByAccount(bankId, holding.accountId, Some(cc))
          } yield JSONFactory300.createFirehoseCoreBankAccountJSON(List(moderatedAccount), Some(attributes))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getHoldingAccountByReleaser), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/holding-accounts",
      "Get Holding Accounts By Releaser",
      """Return the first Holding Account linked to the given releaser account via account attribute RELEASER_ACCOUNT_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: Nil, None,
      http4sPartialFunction = Some(getHoldingAccountByReleaser))

    // ─── Phase 2: account-access-request lifecycle (3 endpoints) ─────────

    // POST /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests (201)
    lazy val createAccountAccessRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access-requests" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bankId = BankId(bankIdStr)
          val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostAccountAccessRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.PostAccountAccessRequestJsonV600]
            }
            _ <- Helper.booleanToFuture(BusinessJustificationRequired, cc = Some(cc)) {
              postJson.business_justification.trim.nonEmpty
            }
            (_, _) <- NewStyle.function.findByUserId(postJson.target_user_id, Some(cc))
            _ <- Helper.booleanToFuture(AccountAccessRequestAlreadyExists, 409, Some(cc)) {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
                .getByUserAccountView(postJson.target_user_id, bankIdStr, accountIdStr, postJson.view_id)
                .isEmpty
            }
            _ <- if (postJson.is_system_view) {
              ViewNewStyle.systemView(ViewId(postJson.view_id), Some(cc)).map(_ => ())
            } else {
              ViewNewStyle.customView(ViewId(postJson.view_id),
                com.openbankproject.commons.model.BankIdAccountId(bankId, accountId), Some(cc)).map(_ => ())
            }
            requestBox <- Future {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.createAccountAccessRequest(
                bankIdStr, accountIdStr, postJson.view_id, postJson.is_system_view,
                u.userId, postJson.target_user_id, postJson.business_justification)
            }
            request <- Future(unboxFullOrFail(requestBox, Some(cc), AccountAccessRequestCannotBeCreated, 400))
          } yield JSONFactory600.createAccountAccessRequestJsonV600(request)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createAccountAccessRequest), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests",
      "Create Account Access Request",
      """Create a new Account Access Request (maker step in maker/checker workflow).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
        $BankNotFound, $BankAccountNotFound, BusinessJustificationRequired,
        AccountAccessRequestAlreadyExists, AccountAccessRequestCannotBeCreated, UnknownError),
      apiTagAccountAccess :: Nil,
      Some(canCreateAccountAccessRequestAtOneBank :: canCreateAccountAccessRequestAtAnyBank :: Nil),
      http4sPartialFunction = Some(createAccountAccessRequest))

    // POST /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/.../approval (201)
    lazy val approveAccountAccessRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access-requests" / requestIdStr / "approval" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bankId = BankId(bankIdStr)
          val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostApproveAccountAccessRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.PostApproveAccountAccessRequestJsonV600]
            }
            requestBox <- Future {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.getById(requestIdStr)
            }
            request <- Future(unboxFullOrFail(requestBox, Some(cc), AccountAccessRequestNotFound, 404))
            _ <- Helper.booleanToFuture(AccountAccessRequestNotFound, cc = Some(cc)) {
              request.bankId == bankIdStr && request.accountId == accountIdStr
            }
            _ <- Helper.booleanToFuture(AccountAccessRequestStatusNotInitiated, cc = Some(cc)) {
              request.status == com.openbankproject.commons.model.enums.AccountAccessRequestStatus.INITIATED.toString
            }
            _ <- Helper.booleanToFuture(MakerCheckerSameUser, cc = Some(cc)) {
              u.userId != request.requestorUserId
            }
            (targetUser, _) <- NewStyle.function.findByUserId(request.targetUserId, Some(cc))
            _ <- if (request.isSystemView) {
              ViewNewStyle.systemView(ViewId(request.viewId), Some(cc)).flatMap { view =>
                ViewNewStyle.grantAccessToSystemView(bankId, accountId, view, targetUser, Some(cc))
              }
            } else {
              ViewNewStyle.customView(ViewId(request.viewId),
                com.openbankproject.commons.model.BankIdAccountId(bankId, accountId), Some(cc)).flatMap { view =>
                ViewNewStyle.grantAccessToCustomView(view, targetUser, Some(cc))
              }
            }
            updatedBox <- Future {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.updateStatus(
                requestIdStr,
                com.openbankproject.commons.model.enums.AccountAccessRequestStatus.APPROVED.toString,
                u.userId,
                postJson.comment.getOrElse(""))
            }
            updated <- Future(unboxFullOrFail(updatedBox, Some(cc), AccountAccessRequestCannotBeUpdated, 400))
          } yield JSONFactory600.createAccountAccessRequestJsonV600(updated)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(approveAccountAccessRequest), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID/approval",
      "Approve Account Access Request",
      """Approve an Account Access Request (checker step in maker/checker workflow).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
        $BankNotFound, $BankAccountNotFound, AccountAccessRequestNotFound,
        AccountAccessRequestStatusNotInitiated, MakerCheckerSameUser,
        AccountAccessRequestCannotBeUpdated, UnknownError),
      apiTagAccountAccess :: Nil,
      Some(canUpdateAccountAccessRequestAtOneBank :: canUpdateAccountAccessRequestAtAnyBank :: Nil),
      http4sPartialFunction = Some(approveAccountAccessRequest))

    // POST /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/.../rejection (201)
    lazy val rejectAccountAccessRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "account-access-requests" / requestIdStr / "rejection" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostRejectAccountAccessRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.PostRejectAccountAccessRequestJsonV600]
            }
            _ <- Helper.booleanToFuture(CheckerCommentRequiredForRejection, cc = Some(cc)) {
              postJson.comment.trim.nonEmpty
            }
            requestBox <- Future {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.getById(requestIdStr)
            }
            request <- Future(unboxFullOrFail(requestBox, Some(cc), AccountAccessRequestNotFound, 404))
            _ <- Helper.booleanToFuture(AccountAccessRequestNotFound, cc = Some(cc)) {
              request.bankId == bankIdStr && request.accountId == accountIdStr
            }
            _ <- Helper.booleanToFuture(AccountAccessRequestStatusNotInitiated, cc = Some(cc)) {
              request.status == com.openbankproject.commons.model.enums.AccountAccessRequestStatus.INITIATED.toString
            }
            _ <- Helper.booleanToFuture(MakerCheckerSameUser, cc = Some(cc)) {
              u.userId != request.requestorUserId
            }
            updatedBox <- Future {
              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.updateStatus(
                requestIdStr,
                com.openbankproject.commons.model.enums.AccountAccessRequestStatus.REJECTED.toString,
                u.userId, postJson.comment)
            }
            updated <- Future(unboxFullOrFail(updatedBox, Some(cc), AccountAccessRequestCannotBeUpdated, 400))
          } yield JSONFactory600.createAccountAccessRequestJsonV600(updated)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(rejectAccountAccessRequest), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID/rejection",
      "Reject Account Access Request",
      """Reject an Account Access Request (checker step in maker/checker workflow).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
        $BankNotFound, $BankAccountNotFound, AccountAccessRequestNotFound,
        AccountAccessRequestStatusNotInitiated, MakerCheckerSameUser,
        CheckerCommentRequiredForRejection, AccountAccessRequestCannotBeUpdated, UnknownError),
      apiTagAccountAccess :: Nil,
      Some(canUpdateAccountAccessRequestAtOneBank :: canUpdateAccountAccessRequestAtAnyBank :: Nil),
      http4sPartialFunction = Some(rejectAccountAccessRequest))

    // ─── Phase 2: Signal bucket (6 endpoints) ────────────────────────────

    // GET /obp/v6.0.0/signal/channels
    lazy val getSignalChannels: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "signal" / "channels" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future {
            val names = code.api.cache.RedisMessaging.listChannels()
            val infos = names.flatMap { name =>
              code.api.cache.RedisMessaging.channelInfo(name).map { case (count, ttl) =>
                val (messages, _) = code.api.cache.RedisMessaging.fetchMessages(name, 0, count.toInt)
                val hasBroadcast = messages.exists { s =>
                  scala.util.Try(net.liftweb.json.parse(s).extract[SignalMessageJsonV600].to_user_id.isEmpty).getOrElse(false)
                }
                (name, count, ttl, hasBroadcast)
              }
            }
            val channels = infos.filter(_._4).map { case (name, count, ttl, _) =>
              SignalChannelInfoJsonV600(name, count, ttl)
            }
            SignalChannelsJsonV600(channels)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSignalChannels), "GET",
      "/signal/channels", "List Signal Channels",
      """List active signal channels with broadcast messages.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagAiAgent :: apiTagSignal :: apiTagSignalling :: apiTagChannel :: Nil, None,
      http4sPartialFunction = Some(getSignalChannels))

    // GET /obp/v6.0.0/signal/channels/CHANNEL_NAME/info
    lazy val getSignalChannelInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "signal" / "channels" / channelName / "info" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            _ <- Helper.booleanToFuture(InvalidSignalChannelName, cc = Some(cc)) {
              code.api.cache.RedisMessaging.validateChannelName(channelName)
            }
            info <- Future(code.api.cache.RedisMessaging.channelInfo(channelName))
            (count, ttl) <- info match {
              case Some((c, t)) => Future.successful((c, t))
              case None => Future.failed(new RuntimeException(s"Channel '$channelName' not found"))
            }
          } yield SignalChannelInfoJsonV600(channelName, count, ttl)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSignalChannelInfo), "GET",
      "/signal/channels/CHANNEL_NAME/info", "Get Signal Channel Info",
      """Get metadata for a signal channel (message count + remaining TTL).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidSignalChannelName, UnknownError),
      apiTagAiAgent :: apiTagSignal :: apiTagSignalling :: apiTagChannel :: Nil, None,
      http4sPartialFunction = Some(getSignalChannelInfo))

    // GET /obp/v6.0.0/signal/channels/stats
    lazy val getSignalStats: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "signal" / "channels" / "stats" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future {
            val names = code.api.cache.RedisMessaging.listChannels()
            val channels = names.flatMap { name =>
              code.api.cache.RedisMessaging.channelInfo(name).map { case (count, ttl) =>
                SignalChannelInfoJsonV600(name, count, ttl)
              }
            }
            SignalStatsJsonV600(
              total_channels = channels.size,
              total_messages = channels.map(_.message_count).sum,
              channels = channels)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSignalStats), "GET",
      "/signal/channels/stats", "Get Signal Channel Stats",
      """Stats for all signal channels including private-only.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagAiAgent :: apiTagSignal :: apiTagSignalling :: apiTagChannel :: Nil,
      Some(canGetSignalStats :: Nil),
      http4sPartialFunction = Some(getSignalStats))

    // POST /obp/v6.0.0/signal/channels/CHANNEL_NAME/messages (201)
    lazy val publishSignalMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "signal" / "channels" / channelName / "messages" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostSignalMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostSignalMessageJsonV600]
            }
            _ <- Helper.booleanToFuture(InvalidSignalChannelName, cc = Some(cc)) {
              code.api.cache.RedisMessaging.validateChannelName(channelName)
            }
            published <- Future {
              val consumerId = cc.consumer match { case Full(c) => c.consumerId.get; case _ => "" }
              val messageId = randomUUID().toString
              val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
              sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
              val timestamp = sdf.format(new java.util.Date())
              val envelope = SignalMessageJsonV600(
                message_id = messageId, channel_name = channelName,
                sender_consumer_id = consumerId, sender_user_id = u.userId,
                to_user_id = postJson.to_user_id, timestamp = timestamp,
                message_type = postJson.message_type.getOrElse(""),
                payload = postJson.payload)
              val msgStr = net.liftweb.json.compactRender(Extraction.decompose(envelope))
              val count = code.api.cache.RedisMessaging.publishMessage(channelName, msgStr)
              SignalMessagePublishedJsonV600(messageId, channelName, timestamp, count)
            }
          } yield published
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(publishSignalMessage), "POST",
      "/signal/channels/CHANNEL_NAME/messages", "Publish Signal Message",
      """Publish a message to a signal channel.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidSignalChannelName, InvalidJsonFormat, UnknownError),
      apiTagAiAgent :: apiTagSignal :: apiTagSignalling :: apiTagChannel :: Nil, None,
      http4sPartialFunction = Some(publishSignalMessage))

    // GET /obp/v6.0.0/signal/channels/CHANNEL_NAME/messages
    lazy val getSignalMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "signal" / "channels" / channelName / "messages" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- Helper.booleanToFuture(InvalidSignalChannelName, cc = Some(cc)) {
              code.api.cache.RedisMessaging.validateChannelName(channelName)
            }
            httpParams = req.headers.headers.toList.map(h =>
              net.liftweb.http.provider.HTTPParam(h.name.toString, h.value))
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            limit = obpQueryParams.collectFirst { case code.api.util.OBPLimit(value) => value }.getOrElse(50)
            offset = obpQueryParams.collectFirst { case code.api.util.OBPOffset(value) => value }.getOrElse(0)
            (rawMessages, totalCount) <- Future(code.api.cache.RedisMessaging.fetchMessages(channelName, offset, limit))
          } yield {
            val parsed = rawMessages.flatMap { s =>
              scala.util.Try(net.liftweb.json.parse(s).extract[SignalMessageJsonV600]).toOption
            }
            val filtered = parsed.filter { msg =>
              msg.to_user_id.isEmpty ||
                msg.to_user_id.contains(user.userId) ||
                msg.sender_user_id == user.userId
            }
            SignalMessagesJsonV600(channelName, filtered, totalCount, (offset + limit) < totalCount)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSignalMessages), "GET",
      "/signal/channels/CHANNEL_NAME/messages", "Get Signal Messages",
      """Fetch messages from a signal channel with offset/limit pagination.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidSignalChannelName, UnknownError),
      apiTagAiAgent :: apiTagSignal :: apiTagSignalling :: apiTagChannel :: Nil, None,
      http4sPartialFunction = Some(getSignalMessages))

    // DELETE /obp/v6.0.0/signal/channels/CHANNEL_NAME (200 with body — not 204)
    lazy val deleteSignalChannel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "signal" / "channels" / channelName =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            _ <- Helper.booleanToFuture(InvalidSignalChannelName, cc = Some(cc)) {
              code.api.cache.RedisMessaging.validateChannelName(channelName)
            }
            deleted <- Future(code.api.cache.RedisMessaging.deleteChannel(channelName))
          } yield SignalChannelDeletedJsonV600(channelName, deleted)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteSignalChannel), "DELETE",
      "/signal/channels/CHANNEL_NAME", "Delete Signal Channel",
      """Delete a signal channel and all its messages.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidSignalChannelName, UnknownError),
      apiTagAiAgent :: apiTagSignal :: apiTagSignalling :: apiTagChannel :: Nil, None,
      http4sPartialFunction = Some(deleteSignalChannel))

    // ─── Phase 2: Chat-room reads (4 endpoints) ──────────────────────────

    private def computeParticipantCount(chatRoomId: String): Long =
      code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId)
        .map(_.length.toLong).openOr(0L)

    private def computeParticipantCounts(rooms: List[code.chat.ChatRoomTrait]): Map[String, Long] =
      rooms.map(room => room.chatRoomId -> computeParticipantCount(room.chatRoomId)).toMap

    private def computeUnreadCounts(rooms: List[code.chat.ChatRoomTrait], userId: String): Map[String, Long] =
      rooms.flatMap { room =>
        val participant = code.chat.ChatPermissions.isParticipant(room.chatRoomId, userId)
        participant.toList.map { p =>
          val count = if (room.isOpenRoom)
            code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadMentionCount(room.chatRoomId, userId, p.lastReadAt)
          else
            code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadCount(room.chatRoomId, userId, p.lastReadAt)
          room.chatRoomId -> count.openOr(0L)
        }
      }.toMap

    // GET /obp/v6.0.0/banks/BANK_ID/chat-rooms
    lazy val getBankChatRooms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "chat-rooms" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomsBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .getChatRoomsByBankIdForUser(bankIdStr, user.userId))
            rooms <- Future(unboxFullOrFail(roomsBox, Some(cc),
              s"$UnknownError Cannot get chat rooms", 400))
            unreadCounts <- Future(computeUnreadCounts(rooms, user.userId))
            participantCounts <- Future(computeParticipantCounts(rooms))
          } yield JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBankChatRooms), "GET",
      "/banks/BANK_ID/chat-rooms", "Get Bank Chat Rooms",
      """Get all bank-scoped chat rooms the current user is a participant of.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getBankChatRooms))

    // GET /obp/v6.0.0/chat-rooms
    lazy val getSystemChatRooms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomsBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .getChatRoomsByBankIdForUser("", user.userId))
            rooms <- Future(unboxFullOrFail(roomsBox, Some(cc),
              s"$UnknownError Cannot get chat rooms", 400))
            unreadCounts <- Future(computeUnreadCounts(rooms, user.userId))
            participantCounts <- Future(computeParticipantCounts(rooms))
          } yield JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSystemChatRooms), "GET",
      "/chat-rooms", "Get System Chat Rooms",
      """Get all system-level chat rooms the current user is a participant of.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getSystemChatRooms))

    // GET /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID
    lazy val getBankChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            participantBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(participantBox, Some(cc), NotChatRoomParticipant, 403))
          } yield JSONFactory600.createChatRoomJson(room, participantCount = computeParticipantCount(room.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBankChatRoom), "GET",
      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID", "Get Bank Chat Room",
      """Get a specific bank chat room by ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getBankChatRoom))

    // GET /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID
    lazy val getSystemChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            participantBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(participantBox, Some(cc), NotChatRoomParticipant, 403))
          } yield JSONFactory600.createChatRoomJson(room, participantCount = computeParticipantCount(room.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getSystemChatRoom), "GET",
      "/chat-rooms/CHAT_ROOM_ID", "Get System Chat Room",
      """Get a specific system chat room by ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getSystemChatRoom))

    // ─── Phase 2: Chat-room my-views (6 endpoints) ────────────────────────

    // GET /obp/v6.0.0/users/current/chat-rooms
    lazy val getMyChatRooms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "chat-rooms" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            participantBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .getParticipantRoomsByUserId(user.userId))
            participantRecords <- Future(unboxFullOrFail(participantBox, Some(cc),
              s"$UnknownError Cannot get participant records", 400))
            roomsAndCounts <- Future {
              participantRecords.flatMap { p =>
                code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(p.chatRoomId).toList.map { room =>
                  val count = if (room.isOpenRoom)
                    code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadMentionCount(p.chatRoomId, p.userId, p.lastReadAt)
                  else
                    code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadCount(p.chatRoomId, p.userId, p.lastReadAt)
                  (room, count.openOr(0L))
                }
              }
            }
            participantCounts <- Future(computeParticipantCounts(roomsAndCounts.map(_._1)))
          } yield {
            val rooms = roomsAndCounts.map(_._1)
            val unread = roomsAndCounts.map { case (r, c) => r.chatRoomId -> c }.toMap
            JSONFactory600.createChatRoomsJson(rooms, unread, participantCounts)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyChatRooms), "GET",
      "/users/current/chat-rooms", "Get My Chat Rooms",
      """Get all chat rooms (any bank or system) the current user is a participant of.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getMyChatRooms))

    // GET /obp/v6.0.0/users/current/chat-rooms/unread
    lazy val getMyUnreadCounts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "chat-rooms" / "unread" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            participantBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .getParticipantRoomsByUserId(user.userId))
            participantRecords <- Future(unboxFullOrFail(participantBox, Some(cc),
              s"$UnknownError Cannot get participant records", 400))
            counts <- Future {
              participantRecords.flatMap { p =>
                val room = code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(p.chatRoomId)
                val isOpen = room.map(_.isOpenRoom).openOr(false)
                val count = if (isOpen)
                  code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadMentionCount(p.chatRoomId, p.userId, p.lastReadAt)
                else
                  code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadCount(p.chatRoomId, p.userId, p.lastReadAt)
                count.toList.map(c => UnreadCountJsonV600(chat_room_id = p.chatRoomId, unread_count = c))
              }
            }
          } yield UnreadCountsJsonV600(unread_counts = counts)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyUnreadCounts), "GET",
      "/users/current/chat-rooms/unread", "Get My Unread Counts",
      """Unread-message counts for every chat room the current user is in.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getMyUnreadCounts))

    // PUT /obp/v6.0.0/users/current/chat-rooms/CHAT_ROOM_ID/read-marker
    lazy val markChatRoomRead: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "users" / "current" / "chat-rooms" / chatRoomId / "read-marker" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val user = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            updBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .updateLastReadAt(chatRoomId, user.userId))
            updated <- Future(unboxFullOrFail(updBox, Some(cc), s"$UnknownError Cannot mark as read", 400))
          } yield JSONFactory600.createParticipantJson(updated)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(markChatRoomRead), "PUT",
      "/users/current/chat-rooms/CHAT_ROOM_ID/read-marker", "Mark Chat Room Read",
      """Mark all messages in a chat room as read for the current user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(markChatRoomRead))

    // GET /obp/v6.0.0/users/current/mentions
    lazy val getMyMentions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "mentions" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val qp = req.uri.query.params
          val limit = qp.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offset = qp.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          for {
            msgsBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend
              .getMentionsForUser(user.userId, limit, offset))
            messages <- Future(unboxFullOrFail(msgsBox, Some(cc),
              s"$UnknownError Cannot get mentions", 400))
            allReactions <- Future {
              messages.map { msg =>
                val r = code.chat.ReactionTrait.reactionProvider.vend.getReactions(msg.chatMessageId).openOr(List.empty)
                msg.chatMessageId -> r
              }.toMap
            }
          } yield JSONFactory600.createChatMessagesJson(messages, allReactions)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMyMentions), "GET",
      "/users/current/mentions", "Get My Mentions",
      """Messages where the current user is mentioned. Supports limit/offset query params.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getMyMentions))

    // POST /obp/v6.0.0/chat-rooms/search (200, NOT 201)
    lazy val searchChatRooms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-rooms" / "search" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ChatRoomSearchRequestJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ChatRoomSearchRequestJsonV600]
            }
            roomsBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .searchChatRoomsForUserWithParticipants(user.userId, postJson.with_user_ids,
                postJson.exact_participants.getOrElse(false)))
            rooms <- Future(unboxFullOrFail(roomsBox, Some(cc),
              s"$UnknownError Cannot search chat rooms", 400))
            unreadCounts <- Future(computeUnreadCounts(rooms, user.userId))
            participantCounts <- Future(computeParticipantCounts(rooms))
          } yield JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(searchChatRooms), "POST",
      "/chat-rooms/search", "Search Chat Rooms",
      """Search chat rooms by participant set. POST body lists with_user_ids; response shape matches Get My Chat Rooms.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(searchChatRooms))

    // GET /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/messages/reactions
    lazy val getBulkReactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / "reactions" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            messageIds = req.uri.query.params.get("message_ids")
              .map(_.split(",").map(_.trim).filter(_.nonEmpty).toList).getOrElse(List.empty)
            reactionsBox <- Future(code.chat.ReactionTrait.reactionProvider.vend
              .getReactionsForMessages(messageIds))
            allReactions <- Future(unboxFullOrFail(reactionsBox, Some(cc),
              s"$UnknownError Cannot get reactions", 400))
          } yield JSONFactory600.createBulkReactionsJson(allReactions, messageIds)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getBulkReactions), "GET",
      "/chat-rooms/CHAT_ROOM_ID/messages/reactions", "Get Bulk Reactions",
      """Get reactions for multiple messages in one call (?message_ids=id1,id2,id3).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(getBulkReactions))

    // ─── Phase 2: Chat-room admin (5 endpoints) ───────────────────────────

    // PUT /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/archive-status
    lazy val archiveBankChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "archive-status" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            archivedBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.archiveChatRoom(chatRoomId))
            archived <- Future(unboxFullOrFail(archivedBox, Some(cc),
              s"$UnknownError Cannot archive chat room", 400))
          } yield JSONFactory600.createChatRoomJson(archived,
            participantCount = computeParticipantCount(archived.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(archiveBankChatRoom), "PUT",
      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/archive-status", "Archive Bank Chat Room",
      """Archive a bank chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, ChatRoomNotFound, UnknownError),
      apiTagChat :: Nil,
      Some(canArchiveBankChatRoom :: Nil),
      http4sPartialFunction = Some(archiveBankChatRoom))

    // PUT /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/archive-status
    lazy val archiveSystemChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId / "archive-status" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            archivedBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.archiveChatRoom(chatRoomId))
            archived <- Future(unboxFullOrFail(archivedBox, Some(cc),
              s"$UnknownError Cannot archive chat room", 400))
          } yield JSONFactory600.createChatRoomJson(archived,
            participantCount = computeParticipantCount(archived.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(archiveSystemChatRoom), "PUT",
      "/chat-rooms/CHAT_ROOM_ID/archive-status", "Archive System Chat Room",
      """Archive a system chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, ChatRoomNotFound, UnknownError),
      apiTagChat :: Nil,
      Some(canArchiveSystemChatRoom :: Nil),
      http4sPartialFunction = Some(archiveSystemChatRoom))

    // POST /obp/v6.0.0/banks/BANK_ID/chat-room-participants (201)
    lazy val joinBankChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "chat-room-participants" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            json <- Future(net.liftweb.json.parse(rawBody))
            joiningKey = (json \ "joining_key").extractOpt[String].getOrElse("")
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomByJoiningKey(joiningKey))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), InvalidJoiningKey, 404))
            _ <- Helper.booleanToFuture(ChatRoomIsArchived, cc = Some(cc)) { !room.isArchived }
            existing <- Future(code.chat.ChatPermissions.isParticipant(room.chatRoomId, u.userId))
            _ <- Helper.booleanToFuture(ChatRoomParticipantAlreadyExists, 409, Some(cc)) {
              existing.isEmpty
            }
            partBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .addParticipant(room.chatRoomId, u.userId, "", List.empty, ""))
            participant <- Future(unboxFullOrFail(partBox, Some(cc),
              s"$UnknownError Cannot join chat room", 400))
          } yield JSONFactory600.createParticipantJson(participant)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(joinBankChatRoom), "POST",
      "/banks/BANK_ID/chat-room-participants", "Join Bank Chat Room",
      """Join a bank chat room using a joining key.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJoiningKey,
        ChatRoomIsArchived, ChatRoomParticipantAlreadyExists, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(joinBankChatRoom))

    // PUT /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/joining-key
    lazy val refreshBankJoiningKey: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "joining-key" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, user.userId, code.chat.ChatPermissions.CAN_REFRESH_JOINING_KEY))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            updBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.refreshJoiningKey(chatRoomId))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot refresh joining key", 400))
          } yield JoiningKeyJsonV600(joining_key = updated.joiningKey)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(refreshBankJoiningKey), "PUT",
      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/joining-key", "Refresh Bank Chat Room Joining Key",
      """Refresh the joining key for a bank chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, ChatRoomNotFound,
        InsufficientChatPermission, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(refreshBankJoiningKey))

    // PUT /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/joining-key
    lazy val refreshSystemJoiningKey: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId / "joining-key" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, user.userId, code.chat.ChatPermissions.CAN_REFRESH_JOINING_KEY))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            updBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.refreshJoiningKey(chatRoomId))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot refresh joining key", 400))
          } yield JoiningKeyJsonV600(joining_key = updated.joiningKey)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(refreshSystemJoiningKey), "PUT",
      "/chat-rooms/CHAT_ROOM_ID/joining-key", "Refresh System Chat Room Joining Key",
      """Refresh the joining key for a system chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, ChatRoomNotFound,
        InsufficientChatPermission, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(refreshSystemJoiningKey))

    // ─── Phase 2: Chat-room mutations (8 endpoints) ───────────────────────

    // POST /obp/v6.0.0/banks/BANK_ID/chat-rooms (201)
    lazy val createBankChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "chat-rooms" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostChatRoomJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostChatRoomJsonV600]
            }
            existing <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .getChatRoomByBankIdAndName(bankIdStr, postJson.name))
            _ <- Helper.booleanToFuture(ChatRoomAlreadyExists, 409, Some(cc)) { existing.isEmpty }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .createChatRoom(bankIdStr, postJson.name, postJson.description, u.userId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc),
              s"$UnknownError Cannot create chat room", 400))
            partBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .addParticipant(room.chatRoomId, u.userId, "",
                code.chat.ChatPermissions.ALL_PERMISSIONS, ""))
            _ <- Future(unboxFullOrFail(partBox, Some(cc),
              s"$UnknownError Cannot add creator as participant", 400))
          } yield JSONFactory600.createChatRoomJson(room,
            participantCount = computeParticipantCount(room.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createBankChatRoom), "POST",
      "/banks/BANK_ID/chat-rooms", "Create Bank Chat Room",
      """Create a new bank-scoped chat room. Creator becomes participant with all permissions.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
        ChatRoomAlreadyExists, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(createBankChatRoom))

    // POST /obp/v6.0.0/chat-rooms (201)
    lazy val createSystemChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-rooms" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostChatRoomJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostChatRoomJsonV600]
            }
            existing <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .getChatRoomByBankIdAndName("", postJson.name))
            _ <- Helper.booleanToFuture(ChatRoomAlreadyExists, 409, Some(cc)) { existing.isEmpty }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .createChatRoom("", postJson.name, postJson.description, u.userId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc),
              s"$UnknownError Cannot create chat room", 400))
            partBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .addParticipant(room.chatRoomId, u.userId, "",
                code.chat.ChatPermissions.ALL_PERMISSIONS, ""))
            _ <- Future(unboxFullOrFail(partBox, Some(cc),
              s"$UnknownError Cannot add creator as participant", 400))
          } yield JSONFactory600.createChatRoomJson(room,
            participantCount = computeParticipantCount(room.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createSystemChatRoom), "POST",
      "/chat-rooms", "Create System Chat Room",
      """Create a new system-level chat room. Creator becomes participant with all permissions.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
        ChatRoomAlreadyExists, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(createSystemChatRoom))

    // PUT /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID
    lazy val updateBankChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            putJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PutChatRoomJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutChatRoomJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_UPDATE_ROOM))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            updBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .updateChatRoom(chatRoomId, putJson.name, putJson.description))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot update chat room", 400))
          } yield JSONFactory600.createChatRoomJson(updated,
            participantCount = computeParticipantCount(updated.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateBankChatRoom), "PUT",
      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID", "Update Bank Chat Room",
      """Update the name/description of a bank chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
        ChatRoomNotFound, NotChatRoomParticipant, InsufficientChatPermission, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(updateBankChatRoom))

    // PUT /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID
    lazy val updateSystemChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            putJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PutChatRoomJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutChatRoomJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_UPDATE_ROOM))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            updBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .updateChatRoom(chatRoomId, putJson.name, putJson.description))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot update chat room", 400))
          } yield JSONFactory600.createChatRoomJson(updated,
            participantCount = computeParticipantCount(updated.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateSystemChatRoom), "PUT",
      "/chat-rooms/CHAT_ROOM_ID", "Update System Chat Room",
      """Update the name/description of a system chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
        ChatRoomNotFound, NotChatRoomParticipant, InsufficientChatPermission, UnknownError),
      apiTagChat :: Nil, None,
      http4sPartialFunction = Some(updateSystemChatRoom))

    // DELETE /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID (204)
    lazy val deleteBankChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId =>
        EndpointHelpers.executeDelete(req) { cc =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            delBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.deleteChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(delBox, Some(cc),
              s"$UnknownError Cannot delete chat room", 400))
          } yield ()
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteBankChatRoom), "DELETE",
      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID", "Delete Bank Chat Room",
      """Delete a bank chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles,
        $BankNotFound, ChatRoomNotFound, UnknownError),
      apiTagChat :: Nil,
      Some(canDeleteBankChatRoom :: Nil),
      http4sPartialFunction = Some(deleteBankChatRoom))

    // DELETE /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID (204)
    lazy val deleteSystemChatRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "chat-rooms" / chatRoomId =>
        EndpointHelpers.executeDelete(req) { cc =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            delBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.deleteChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(delBox, Some(cc),
              s"$UnknownError Cannot delete chat room", 400))
          } yield ()
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteSystemChatRoom), "DELETE",
      "/chat-rooms/CHAT_ROOM_ID", "Delete System Chat Room",
      """Delete a system chat room.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles,
        ChatRoomNotFound, UnknownError),
      apiTagChat :: Nil,
      Some(canDeleteSystemChatRoom :: Nil),
      http4sPartialFunction = Some(deleteSystemChatRoom))

    // PUT /obp/v6.0.0/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/open-room
    lazy val setBankChatRoomOpenRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "open-room" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- Future(net.liftweb.json.parse(rawBody))
            isOpenRoom = (json \ "is_open_room").extractOrElse[Boolean](false)
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            updBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .setIsOpenRoom(chatRoomId, isOpenRoom))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot update chat room", 400))
          } yield JSONFactory600.createChatRoomJson(updated,
            participantCount = computeParticipantCount(updated.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(setBankChatRoomOpenRoom), "PUT",
      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/open-room", "Set Bank Chat Room Open Room",
      """Mark a bank chat room as open (all bank users implicit participants) or closed.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles,
        $BankNotFound, ChatRoomNotFound, UnknownError),
      apiTagChat :: Nil,
      Some(canSetBankChatRoomIsOpenRoom :: Nil),
      http4sPartialFunction = Some(setBankChatRoomOpenRoom))

    // PUT /obp/v6.0.0/chat-rooms/CHAT_ROOM_ID/open-room
    lazy val setSystemChatRoomOpenRoom: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId / "open-room" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            json <- Future(net.liftweb.json.parse(rawBody))
            isOpenRoom = (json \ "is_open_room").extractOrElse[Boolean](false)
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            updBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend
              .setIsOpenRoom(chatRoomId, isOpenRoom))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot update chat room", 400))
          } yield JSONFactory600.createChatRoomJson(updated,
            participantCount = computeParticipantCount(updated.chatRoomId))
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(setSystemChatRoomOpenRoom), "PUT",
      "/chat-rooms/CHAT_ROOM_ID/open-room", "Set System Chat Room Open Room",
      """Mark a system chat room as open (all users implicit participants) or closed.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles,
        ChatRoomNotFound, UnknownError),
      apiTagChat :: Nil,
      Some(canSetSystemChatRoomIsOpenRoom :: Nil),
      http4sPartialFunction = Some(setSystemChatRoomOpenRoom))

    // ─── Phase 2: Chat-room participants (8 endpoints) ────────────────────
    // Pattern: lazy val at object level so allRoutes can see them; ResourceDoc
    // registrations live in a separate private def to keep <init> under
    // the JVM 64KB method-size limit. Apply this pattern for future batches.

    lazy val addBankChatRoomParticipant: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "participants" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostParticipantJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostParticipantJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            userId = postJson.user_id.getOrElse("")
            consumerId = postJson.consumer_id.getOrElse("")
            _ <- Helper.booleanToFuture(MustSpecifyUserIdOrConsumerId, cc = Some(cc)) {
              (userId.nonEmpty || consumerId.nonEmpty) && !(userId.nonEmpty && consumerId.nonEmpty)
            }
            existing <- Future {
              if (userId.nonEmpty) code.chat.ChatPermissions.isParticipant(chatRoomId, userId)
              else code.chat.ChatPermissions.isParticipantByConsumerId(chatRoomId, consumerId)
            }
            _ <- Helper.booleanToFuture(ChatRoomParticipantAlreadyExists, 409, Some(cc)) {
              existing.isEmpty
            }
            partBox <- Future(code.chat.ParticipantTrait.participantProvider.vend.addParticipant(
              chatRoomId, userId, consumerId,
              postJson.permissions.getOrElse(List.empty),
              postJson.webhook_url.getOrElse("")))
            participant <- Future(unboxFullOrFail(partBox, Some(cc),
              s"$UnknownError Cannot add participant", 400))
          } yield JSONFactory600.createParticipantJson(participant)
        }
    }

    lazy val addSystemChatRoomParticipant: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-rooms" / chatRoomId / "participants" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostParticipantJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostParticipantJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            userId = postJson.user_id.getOrElse("")
            consumerId = postJson.consumer_id.getOrElse("")
            _ <- Helper.booleanToFuture(MustSpecifyUserIdOrConsumerId, cc = Some(cc)) {
              (userId.nonEmpty || consumerId.nonEmpty) && !(userId.nonEmpty && consumerId.nonEmpty)
            }
            existing <- Future {
              if (userId.nonEmpty) code.chat.ChatPermissions.isParticipant(chatRoomId, userId)
              else code.chat.ChatPermissions.isParticipantByConsumerId(chatRoomId, consumerId)
            }
            _ <- Helper.booleanToFuture(ChatRoomParticipantAlreadyExists, 409, Some(cc)) {
              existing.isEmpty
            }
            partBox <- Future(code.chat.ParticipantTrait.participantProvider.vend.addParticipant(
              chatRoomId, userId, consumerId,
              postJson.permissions.getOrElse(List.empty),
              postJson.webhook_url.getOrElse("")))
            participant <- Future(unboxFullOrFail(partBox, Some(cc),
              s"$UnknownError Cannot add participant", 400))
          } yield JSONFactory600.createParticipantJson(participant)
        }
    }

    lazy val getBankChatRoomParticipants: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "participants" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            listBox <- Future(code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId))
            participants <- Future(unboxFullOrFail(listBox, Some(cc),
              s"$UnknownError Cannot get participants", 400))
          } yield JSONFactory600.createParticipantsJson(participants)
        }
    }

    lazy val getSystemChatRoomParticipants: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "participants" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            listBox <- Future(code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId))
            participants <- Future(unboxFullOrFail(listBox, Some(cc),
              s"$UnknownError Cannot get participants", 400))
          } yield JSONFactory600.createParticipantsJson(participants)
        }
    }

    lazy val updateBankParticipantPermissions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "participants" / targetUserId =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            putJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PutParticipantPermissionsJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutParticipantPermissionsJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, user.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            tgtBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId))
            _ <- Future(unboxFullOrFail(tgtBox, Some(cc), ChatRoomParticipantNotFound, 404))
            updBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .updateParticipantPermissions(chatRoomId, targetUserId, putJson.permissions))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot update participant permissions", 400))
          } yield JSONFactory600.createParticipantJson(updated)
        }
    }

    lazy val updateSystemParticipantPermissions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId / "participants" / targetUserId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            putJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PutParticipantPermissionsJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutParticipantPermissionsJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            permBox <- Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, user.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS))
            _ <- Future(unboxFullOrFail(permBox, Some(cc), InsufficientChatPermission, 403))
            tgtBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId))
            _ <- Future(unboxFullOrFail(tgtBox, Some(cc), ChatRoomParticipantNotFound, 404))
            updBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .updateParticipantPermissions(chatRoomId, targetUserId, putJson.permissions))
            updated <- Future(unboxFullOrFail(updBox, Some(cc),
              s"$UnknownError Cannot update participant permissions", 400))
          } yield JSONFactory600.createParticipantJson(updated)
        }
    }

    lazy val removeBankChatRoomParticipant: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "participants" / targetUserId =>
        EndpointHelpers.executeDelete(req) { cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            _ <- if (u.userId == targetUserId) Future.successful(())
            else Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_REMOVE_PARTICIPANT))
              .map(b => unboxFullOrFail(b, Some(cc), InsufficientChatPermission, 403))
            tgtBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId))
            _ <- Future(unboxFullOrFail(tgtBox, Some(cc), ChatRoomParticipantNotFound, 404))
            delBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .removeParticipant(chatRoomId, targetUserId))
            _ <- Future(unboxFullOrFail(delBox, Some(cc),
              s"$UnknownError Cannot remove participant", 400))
          } yield ()
        }
    }

    lazy val removeSystemChatRoomParticipant: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "chat-rooms" / chatRoomId / "participants" / targetUserId =>
        EndpointHelpers.executeDelete(req) { cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            _ <- if (u.userId == targetUserId) Future.successful(())
            else Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_REMOVE_PARTICIPANT))
              .map(b => unboxFullOrFail(b, Some(cc), InsufficientChatPermission, 403))
            tgtBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId))
            _ <- Future(unboxFullOrFail(tgtBox, Some(cc), ChatRoomParticipantNotFound, 404))
            delBox <- Future(code.chat.ParticipantTrait.participantProvider.vend
              .removeParticipant(chatRoomId, targetUserId))
            _ <- Future(unboxFullOrFail(delBox, Some(cc),
              s"$UnknownError Cannot remove participant", 400))
          } yield ()
        }
    }

    private def initParticipantResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(addBankChatRoomParticipant), "POST",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants", "Add Bank Chat Room Participant",
        """Add a participant to a bank chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          ChatRoomNotFound, InsufficientChatPermission, MustSpecifyUserIdOrConsumerId,
          ChatRoomParticipantAlreadyExists, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(addBankChatRoomParticipant))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(addSystemChatRoomParticipant), "POST",
        "/chat-rooms/CHAT_ROOM_ID/participants", "Add System Chat Room Participant",
        """Add a participant to a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat,
          ChatRoomNotFound, InsufficientChatPermission, MustSpecifyUserIdOrConsumerId,
          ChatRoomParticipantAlreadyExists, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(addSystemChatRoomParticipant))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankChatRoomParticipants), "GET",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants", "Get Bank Chat Room Participants",
        """List participants of a bank chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getBankChatRoomParticipants))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemChatRoomParticipants), "GET",
        "/chat-rooms/CHAT_ROOM_ID/participants", "Get System Chat Room Participants",
        """List participants of a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getSystemChatRoomParticipants))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateBankParticipantPermissions), "PUT",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
        "Update Bank Chat Room Participant Permissions",
        """Update permissions for a participant in a bank chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          ChatRoomNotFound, InsufficientChatPermission, ChatRoomParticipantNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(updateBankParticipantPermissions))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateSystemParticipantPermissions), "PUT",
        "/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
        "Update System Chat Room Participant Permissions",
        """Update permissions for a participant in a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat,
          ChatRoomNotFound, InsufficientChatPermission, ChatRoomParticipantNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(updateSystemParticipantPermissions))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(removeBankChatRoomParticipant), "DELETE",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
        "Remove Bank Chat Room Participant",
        """Remove a participant from a bank chat room (self-removal allowed).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, InsufficientChatPermission, ChatRoomParticipantNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(removeBankChatRoomParticipant))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(removeSystemChatRoomParticipant), "DELETE",
        "/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
        "Remove System Chat Room Participant",
        """Remove a participant from a system chat room (self-removal allowed).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, ChatRoomNotFound,
          InsufficientChatPermission, ChatRoomParticipantNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(removeSystemChatRoomParticipant))
    }
    initParticipantResourceDocs()

    // ─── Phase 2: Chat messages (10 endpoints) ────────────────────────────

    lazy val sendBankChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostChatMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostChatMessageJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            _ <- Helper.booleanToFuture(ChatRoomIsArchived, cc = Some(cc)) { !room.isArchived }
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
              chatRoomId, u.userId, "", postJson.content,
              postJson.message_type.getOrElse("text"),
              postJson.mentioned_user_ids.getOrElse(List.empty),
              postJson.reply_to_message_id.getOrElse(""),
              postJson.thread_id.getOrElse("")))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc),
              s"$UnknownError Cannot send message", 400))
          } yield {
            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
            JSONFactory600.createChatMessageJson(msg, List.empty)
          }
        }
    }

    lazy val sendSystemChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostChatMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostChatMessageJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            _ <- Helper.booleanToFuture(ChatRoomIsArchived, cc = Some(cc)) { !room.isArchived }
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
              chatRoomId, u.userId, "", postJson.content,
              postJson.message_type.getOrElse("text"),
              postJson.mentioned_user_ids.getOrElse(List.empty),
              postJson.reply_to_message_id.getOrElse(""),
              postJson.thread_id.getOrElse("")))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc),
              s"$UnknownError Cannot send message", 400))
          } yield {
            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
            JSONFactory600.createChatMessageJson(msg, List.empty)
          }
        }
    }

    lazy val getBankChatMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          val qp = req.uri.query.params
          val limit = qp.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offset = qp.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          val fromDate = qp.get("from_date").flatMap(APIUtil.parseObpStandardDate(_).toOption).getOrElse(APIUtil.theEpochTime)
          val toDate = qp.get("to_date").flatMap(APIUtil.parseObpStandardDate(_).toOption).getOrElse(APIUtil.DefaultToDate)
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            tuple <- Future(code.chat.DoobieChatMessageQueries
              .getMessagesWithReactions(chatRoomId, fromDate, toDate, limit, offset))
          } yield JSONFactory600.createChatMessagesJsonFromRows(tuple._1, tuple._2)
        }
    }

    lazy val getSystemChatMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val qp = req.uri.query.params
          val limit = qp.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offset = qp.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          val fromDate = qp.get("from_date").flatMap(APIUtil.parseObpStandardDate(_).toOption).getOrElse(APIUtil.theEpochTime)
          val toDate = qp.get("to_date").flatMap(APIUtil.parseObpStandardDate(_).toOption).getOrElse(APIUtil.DefaultToDate)
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            tuple <- Future(code.chat.DoobieChatMessageQueries
              .getMessagesWithReactions(chatRoomId, fromDate, toDate, limit, offset))
          } yield JSONFactory600.createChatMessagesJsonFromRows(tuple._1, tuple._2)
        }
    }

    lazy val getBankChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            reactions <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty))
          } yield JSONFactory600.createChatMessageJson(msg, reactions)
        }
    }

    lazy val getSystemChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            reactions <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty))
          } yield JSONFactory600.createChatMessageJson(msg, reactions)
        }
    }

    lazy val editBankChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            putJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PutChatMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutChatMessageJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            _ <- Helper.booleanToFuture(CannotEditOthersMessage, cc = Some(cc)) {
              msg.senderUserId == user.userId
            }
            updBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.updateMessage(chatMessageId, putJson.content))
            updated <- Future(unboxFullOrFail(updBox, Some(cc), s"$UnknownError Cannot edit message", 400))
            reactions <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty))
          } yield {
            code.chat.ChatEventPublisher.afterUpdate(updated, user.name, user.provider, "")
            JSONFactory600.createChatMessageJson(updated, reactions)
          }
        }
    }

    lazy val editSystemChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            putJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PutChatMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutChatMessageJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            _ <- Helper.booleanToFuture(CannotEditOthersMessage, cc = Some(cc)) {
              msg.senderUserId == user.userId
            }
            updBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.updateMessage(chatMessageId, putJson.content))
            updated <- Future(unboxFullOrFail(updBox, Some(cc), s"$UnknownError Cannot edit message", 400))
            reactions <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty))
          } yield {
            code.chat.ChatEventPublisher.afterUpdate(updated, user.name, user.provider, "")
            JSONFactory600.createChatMessageJson(updated, reactions)
          }
        }
    }

    lazy val deleteBankChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId =>
        EndpointHelpers.executeDelete(req) { cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            _ <- if (msg.senderUserId == u.userId) Future.successful(())
            else Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_DELETE_MESSAGE))
              .map(b => unboxFullOrFail(b, Some(cc), CannotDeleteMessage, 403))
            delBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.softDeleteMessage(chatMessageId))
            deleted <- Future(unboxFullOrFail(delBox, Some(cc), s"$UnknownError Cannot delete message", 400))
          } yield {
            code.chat.ChatEventPublisher.afterDelete(deleted, u.name, u.provider, "")
            ()
          }
        }
    }

    lazy val deleteSystemChatMessage: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId =>
        EndpointHelpers.executeDelete(req) { cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            _ <- if (msg.senderUserId == u.userId) Future.successful(())
            else Future(code.chat.ChatPermissions.checkParticipantPermission(
              chatRoomId, u.userId, code.chat.ChatPermissions.CAN_DELETE_MESSAGE))
              .map(b => unboxFullOrFail(b, Some(cc), CannotDeleteMessage, 403))
            delBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.softDeleteMessage(chatMessageId))
            deleted <- Future(unboxFullOrFail(delBox, Some(cc), s"$UnknownError Cannot delete message", 400))
          } yield {
            code.chat.ChatEventPublisher.afterDelete(deleted, u.name, u.provider, "")
            ()
          }
        }
    }

    private def initChatMessageResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(sendBankChatMessage), "POST",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages", "Send Bank Chat Message",
        """Send a message in a bank chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatRoomIsArchived, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(sendBankChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(sendSystemChatMessage), "POST",
        "/chat-rooms/CHAT_ROOM_ID/messages", "Send System Chat Message",
        """Send a message in a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatRoomIsArchived, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(sendSystemChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankChatMessages), "GET",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages", "Get Bank Chat Messages",
        """Get messages in a bank chat room (limit/offset/from_date/to_date).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getBankChatMessages))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemChatMessages), "GET",
        "/chat-rooms/CHAT_ROOM_ID/messages", "Get System Chat Messages",
        """Get messages in a system chat room (limit/offset/from_date/to_date).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getSystemChatMessages))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankChatMessage), "GET",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
        "Get Bank Chat Message",
        """Get a specific message in a bank chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getBankChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemChatMessage), "GET",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID", "Get System Chat Message",
        """Get a specific message in a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getSystemChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(editBankChatMessage), "PUT",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
        "Edit Bank Chat Message",
        """Edit a message in a bank chat room (sender only).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          CannotEditOthersMessage, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(editBankChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(editSystemChatMessage), "PUT",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID", "Edit System Chat Message",
        """Edit a message in a system chat room (sender only).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          CannotEditOthersMessage, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(editSystemChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteBankChatMessage), "DELETE",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
        "Delete Bank Chat Message",
        """Soft-delete a chat message (sender or participant with can_delete_message).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          CannotDeleteMessage, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(deleteBankChatMessage))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteSystemChatMessage), "DELETE",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID", "Delete System Chat Message",
        """Soft-delete a chat message in a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          CannotDeleteMessage, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(deleteSystemChatMessage))
    }
    initChatMessageResourceDocs()

    // ─── Phase 2: Chat threads + reactions + typing (14 endpoints) ────────

    lazy val getBankThreadReplies: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "thread" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            repliesBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getThreadReplies(chatMessageId))
            replies <- Future(unboxFullOrFail(repliesBox, Some(cc),
              s"$UnknownError Cannot get thread replies", 400))
            allReactions <- Future {
              replies.map { msg =>
                val r = code.chat.ReactionTrait.reactionProvider.vend.getReactions(msg.chatMessageId).openOr(List.empty)
                msg.chatMessageId -> r
              }.toMap
            }
          } yield JSONFactory600.createChatMessagesJson(replies, allReactions)
        }
    }

    lazy val getSystemThreadReplies: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "thread" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            repliesBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getThreadReplies(chatMessageId))
            replies <- Future(unboxFullOrFail(repliesBox, Some(cc),
              s"$UnknownError Cannot get thread replies", 400))
            allReactions <- Future {
              replies.map { msg =>
                val r = code.chat.ReactionTrait.reactionProvider.vend.getReactions(msg.chatMessageId).openOr(List.empty)
                msg.chatMessageId -> r
              }.toMap
            }
          } yield JSONFactory600.createChatMessagesJson(replies, allReactions)
        }
    }

    lazy val replyInBankThread: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "thread" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostChatMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostChatMessageJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            _ <- Helper.booleanToFuture(ChatRoomIsArchived, cc = Some(cc)) { !room.isArchived }
            parentBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(parentBox, Some(cc), ChatMessageNotFound, 404))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
              chatRoomId, u.userId, "", postJson.content,
              postJson.message_type.getOrElse("text"),
              postJson.mentioned_user_ids.getOrElse(List.empty),
              postJson.reply_to_message_id.getOrElse(""),
              chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc),
              s"$UnknownError Cannot send thread reply", 400))
          } yield {
            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
            JSONFactory600.createChatMessageJson(msg, List.empty)
          }
        }
    }

    lazy val replyInSystemThread: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "thread" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostChatMessageJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostChatMessageJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            room <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            _ <- Helper.booleanToFuture(ChatRoomIsArchived, cc = Some(cc)) { !room.isArchived }
            parentBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(parentBox, Some(cc), ChatMessageNotFound, 404))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
              chatRoomId, u.userId, "", postJson.content,
              postJson.message_type.getOrElse("text"),
              postJson.mentioned_user_ids.getOrElse(List.empty),
              postJson.reply_to_message_id.getOrElse(""),
              chatMessageId))
            msg <- Future(unboxFullOrFail(msgBox, Some(cc),
              s"$UnknownError Cannot send thread reply", 400))
          } yield {
            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
            JSONFactory600.createChatMessageJson(msg, List.empty)
          }
        }
    }

    lazy val addBankReaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "reactions" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostReactionJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostReactionJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            existing <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, postJson.emoji))
            _ <- Helper.booleanToFuture(ReactionAlreadyExists, 409, Some(cc)) { existing.isEmpty }
            reactBox <- Future(code.chat.ReactionTrait.reactionProvider.vend.addReaction(chatMessageId, u.userId, postJson.emoji))
            reaction <- Future(unboxFullOrFail(reactBox, Some(cc), s"$UnknownError Cannot add reaction", 400))
          } yield {
            code.chat.ChatEventPublisher.afterReactionAdd(chatRoomId, chatMessageId, postJson.emoji, u.userId, u.name, u.provider)
            JSONFactory600.createReactionJson(reaction)
          }
        }
    }

    lazy val addSystemReaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "reactions" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            postJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostReactionJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostReactionJsonV600]
            }
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            existing <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, postJson.emoji))
            _ <- Helper.booleanToFuture(ReactionAlreadyExists, 409, Some(cc)) { existing.isEmpty }
            reactBox <- Future(code.chat.ReactionTrait.reactionProvider.vend.addReaction(chatMessageId, u.userId, postJson.emoji))
            reaction <- Future(unboxFullOrFail(reactBox, Some(cc), s"$UnknownError Cannot add reaction", 400))
          } yield {
            code.chat.ChatEventPublisher.afterReactionAdd(chatRoomId, chatMessageId, postJson.emoji, u.userId, u.name, u.provider)
            JSONFactory600.createReactionJson(reaction)
          }
        }
    }

    lazy val removeBankReaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "reactions" / emoji =>
        EndpointHelpers.executeDelete(req) { cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          val decodedEmoji = java.net.URLDecoder.decode(emoji, java.nio.charset.StandardCharsets.UTF_8.name())
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            existing <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, decodedEmoji))
            _ <- Helper.booleanToFuture(ReactionNotFound, cc = Some(cc)) { existing.isDefined }
            delBox <- Future(code.chat.ReactionTrait.reactionProvider.vend.removeReaction(chatMessageId, u.userId, decodedEmoji))
            _ <- Future(unboxFullOrFail(delBox, Some(cc), s"$UnknownError Cannot remove reaction", 400))
          } yield {
            code.chat.ChatEventPublisher.afterReactionRemove(chatRoomId, chatMessageId, decodedEmoji, u.userId, u.name, u.provider)
            ()
          }
        }
    }

    lazy val removeSystemReaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "reactions" / emoji =>
        EndpointHelpers.executeDelete(req) { cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          val decodedEmoji = java.net.URLDecoder.decode(emoji, java.nio.charset.StandardCharsets.UTF_8.name())
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            existing <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, decodedEmoji))
            _ <- Helper.booleanToFuture(ReactionNotFound, cc = Some(cc)) { existing.isDefined }
            delBox <- Future(code.chat.ReactionTrait.reactionProvider.vend.removeReaction(chatMessageId, u.userId, decodedEmoji))
            _ <- Future(unboxFullOrFail(delBox, Some(cc), s"$UnknownError Cannot remove reaction", 400))
          } yield {
            code.chat.ChatEventPublisher.afterReactionRemove(chatRoomId, chatMessageId, decodedEmoji, u.userId, u.name, u.provider)
            ()
          }
        }
    }

    lazy val getBankReactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "reactions" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            reactionsBox <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId))
            reactions <- Future(unboxFullOrFail(reactionsBox, Some(cc),
              s"$UnknownError Cannot get reactions", 400))
          } yield JSONFactory600.createReactionsJson(reactions)
        }
    }

    lazy val getSystemReactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "messages" / chatMessageId / "reactions" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            msgBox <- Future(code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId))
            _ <- Future(unboxFullOrFail(msgBox, Some(cc), ChatMessageNotFound, 404))
            reactionsBox <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId))
            reactions <- Future(unboxFullOrFail(reactionsBox, Some(cc),
              s"$UnknownError Cannot get reactions", 400))
          } yield JSONFactory600.createReactionsJson(reactions)
        }
    }

    lazy val signalBankTyping: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "typing-indicators" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            _ <- Future {
              val key = s"chat_typing_${chatRoomId}_${u.userId}"
              Redis.use(code.api.JedisMethod.SET, key, Some(5), Some("1"))
              code.chat.ChatEventPublisher.afterTyping(chatRoomId, u.userId, u.name, u.provider, true)
            }
          } yield ""
        }
    }

    lazy val signalSystemTyping: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "chat-rooms" / chatRoomId / "typing-indicators" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            _ <- Future {
              val key = s"chat_typing_${chatRoomId}_${u.userId}"
              Redis.use(code.api.JedisMethod.SET, key, Some(5), Some("1"))
              code.chat.ChatEventPublisher.afterTyping(chatRoomId, u.userId, u.name, u.provider, true)
            }
          } yield ""
        }
    }

    lazy val getBankTypingUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "chat-rooms" / chatRoomId / "typing-indicators" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            participantsBox <- Future(code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId))
            participants <- Future(unboxFullOrFail(participantsBox, Some(cc),
              s"$UnknownError Cannot get participants", 400))
            typingUsers <- Future {
              participants.filter(_.userId.nonEmpty).flatMap { p =>
                val key = s"chat_typing_${chatRoomId}_${p.userId}"
                try {
                  Redis.use(code.api.JedisMethod.GET, key) match {
                    case Some(_) =>
                      val tu = code.users.Users.users.vend.getUserByUserId(p.userId)
                      Some(TypingUserJsonV600(p.userId,
                        tu.map(_.name).getOrElse(""),
                        tu.map(_.provider).getOrElse("")))
                    case None => None
                  }
                } catch { case _: Throwable => None }
              }
            }
          } yield TypingUsersJsonV600(typingUsers)
        }
    }

    lazy val getSystemTypingUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "chat-rooms" / chatRoomId / "typing-indicators" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            roomBox <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId))
            _ <- Future(unboxFullOrFail(roomBox, Some(cc), ChatRoomNotFound, 404))
            partBox <- Future(code.chat.ChatPermissions.isParticipant(chatRoomId, user.userId))
            _ <- Future(unboxFullOrFail(partBox, Some(cc), NotChatRoomParticipant, 403))
            participantsBox <- Future(code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId))
            participants <- Future(unboxFullOrFail(participantsBox, Some(cc),
              s"$UnknownError Cannot get participants", 400))
            typingUsers <- Future {
              participants.filter(_.userId.nonEmpty).flatMap { p =>
                val key = s"chat_typing_${chatRoomId}_${p.userId}"
                try {
                  Redis.use(code.api.JedisMethod.GET, key) match {
                    case Some(_) =>
                      val tu = code.users.Users.users.vend.getUserByUserId(p.userId)
                      Some(TypingUserJsonV600(p.userId,
                        tu.map(_.name).getOrElse(""),
                        tu.map(_.provider).getOrElse("")))
                    case None => None
                  }
                } catch { case _: Throwable => None }
              }
            }
          } yield TypingUsersJsonV600(typingUsers)
        }
    }

    private def initChatThreadReactionTypingResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankThreadReplies), "GET",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread",
        "Get Bank Thread Replies",
        """Get replies in a message thread (bank chat room).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getBankThreadReplies))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemThreadReplies), "GET",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread", "Get System Thread Replies",
        """Get replies in a message thread (system chat room).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getSystemThreadReplies))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(replyInBankThread), "POST",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread",
        "Reply In Bank Thread",
        """Reply to a message in a bank-chat-room thread.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatRoomIsArchived, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(replyInBankThread))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(replyInSystemThread), "POST",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread", "Reply In System Thread",
        """Reply to a message in a system-chat-room thread.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatRoomIsArchived, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(replyInSystemThread))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(addBankReaction), "POST",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions",
        "Add Bank Reaction",
        """Add an emoji reaction to a message.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          ReactionAlreadyExists, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(addBankReaction))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(addSystemReaction), "POST",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions", "Add System Reaction",
        """Add an emoji reaction to a message in a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, InvalidJsonFormat,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          ReactionAlreadyExists, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(addSystemReaction))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(removeBankReaction), "DELETE",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions/EMOJI_REACTION",
        "Remove Bank Reaction",
        """Remove your own reaction from a message.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          ReactionNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(removeBankReaction))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(removeSystemReaction), "DELETE",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions/EMOJI_REACTION",
        "Remove System Reaction",
        """Remove your own reaction from a system-chat-room message.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound,
          ReactionNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(removeSystemReaction))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankReactions), "GET",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions",
        "Get Bank Reactions",
        """List reactions on a message.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getBankReactions))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemReactions), "GET",
        "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions", "Get System Reactions",
        """List reactions on a system-chat-room message.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, ChatMessageNotFound, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getSystemReactions))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(signalBankTyping), "PUT",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/typing-indicators", "Signal Bank Typing",
        """Signal that the current user is typing in a bank chat room (TTL 5s).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(signalBankTyping))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(signalSystemTyping), "PUT",
        "/chat-rooms/CHAT_ROOM_ID/typing-indicators", "Signal System Typing",
        """Signal that the current user is typing in a system chat room (TTL 5s).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(signalSystemTyping))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getBankTypingUsers), "GET",
        "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/typing-indicators", "Get Bank Typing Users",
        """List users currently typing in a bank chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, $BankNotFound,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getBankTypingUsers))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSystemTypingUsers), "GET",
        "/chat-rooms/CHAT_ROOM_ID/typing-indicators", "Get System Typing Users",
        """List users currently typing in a system chat room.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired,
          ChatRoomNotFound, NotChatRoomParticipant, UnknownError),
        apiTagChat :: Nil, None,
        http4sPartialFunction = Some(getSystemTypingUsers))
    }
    initChatThreadReactionTypingResourceDocs()

    // ─── Phase 2: Signatory Panels (5 endpoints) ─────────────────────────

    lazy val createSignatoryPanel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "mandates" / mandateId / "signatory-panels" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            createJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateSignatoryPanelJsonV600]
            }
            (panelBox, _) <- BankConnector.connector.vend.createSignatoryPanel(
              mandateId, createJson.panel_name, createJson.description,
              createJson.user_ids.mkString(","), Some(cc))
              .map(i => (i._1, i._2))
            panel <- Future(unboxFullOrFail(panelBox, Some(cc), "Could not create signatory panel"))
          } yield JSONFactory600.createSignatoryPanelJsonV600(panel)
        }
    }

    lazy val getSignatoryPanels: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "mandates" / mandateId / "signatory-panels" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (panelsBox, _) <- BankConnector.connector.vend.getSignatoryPanelsByMandateId(
              mandateId, Some(cc)).map(i => (i._1, i._2))
            panels <- Future(unboxFullOrFail(panelsBox, Some(cc),
              s"Could not get signatory panels for mandate: $mandateId"))
          } yield JSONFactory600.createSignatoryPanelsJsonV600(panels)
        }
    }

    lazy val getSignatoryPanel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "mandates" / _ / "signatory-panels" / panelId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (panelBox, _) <- BankConnector.connector.vend.getSignatoryPanelById(
              panelId, Some(cc)).map(i => (i._1, i._2))
            panel <- Future(unboxFullOrFail(panelBox, Some(cc),
              s"Signatory panel not found. Panel ID: $panelId", 404))
          } yield JSONFactory600.createSignatoryPanelJsonV600(panel)
        }
    }

    lazy val updateSignatoryPanel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "mandates" / _ / "signatory-panels" / panelId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            updateJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateSignatoryPanelJsonV600]
            }
            (panelBox, _) <- BankConnector.connector.vend.updateSignatoryPanel(
              panelId, updateJson.panel_name, updateJson.description,
              updateJson.user_ids.mkString(","), Some(cc))
              .map(i => (i._1, i._2))
            panel <- Future(unboxFullOrFail(panelBox, Some(cc),
              s"Could not update signatory panel. Panel ID: $panelId"))
          } yield JSONFactory600.createSignatoryPanelJsonV600(panel)
        }
    }

    lazy val deleteSignatoryPanel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "mandates" / _ / "signatory-panels" / panelId =>
        EndpointHelpers.executeDelete(req) { cc =>
          for {
            (delBox, _) <- BankConnector.connector.vend.deleteSignatoryPanel(
              panelId, Some(cc)).map(i => (i._1, i._2))
            _ <- Future(unboxFullOrFail(delBox, Some(cc),
              s"Could not delete signatory panel. Panel ID: $panelId"))
          } yield ()
        }
    }

    private def initSignatoryPanelResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createSignatoryPanel), "POST",
        "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels", "Create Signatory Panel",
        """Create a new signatory panel for a mandate.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound,
          InvalidJsonFormat, UnknownError),
        apiTagMandate :: Nil,
        Some(canCreateSignatoryPanel :: Nil),
        http4sPartialFunction = Some(createSignatoryPanel))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSignatoryPanels), "GET",
        "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels", "Get Signatory Panels",
        """Get all signatory panels for a mandate.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, UnknownError),
        apiTagMandate :: Nil,
        Some(canGetSignatoryPanel :: Nil),
        http4sPartialFunction = Some(getSignatoryPanels))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getSignatoryPanel), "GET",
        "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels/PANEL_ID", "Get Signatory Panel",
        """Get a specific signatory panel.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, UnknownError),
        apiTagMandate :: Nil,
        Some(canGetSignatoryPanel :: Nil),
        http4sPartialFunction = Some(getSignatoryPanel))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(updateSignatoryPanel), "PUT",
        "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels/PANEL_ID", "Update Signatory Panel",
        """Update a signatory panel.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound,
          InvalidJsonFormat, UnknownError),
        apiTagMandate :: Nil,
        Some(canUpdateSignatoryPanel :: Nil),
        http4sPartialFunction = Some(updateSignatoryPanel))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteSignatoryPanel), "DELETE",
        "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels/PANEL_ID", "Delete Signatory Panel",
        """Delete a signatory panel.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound, UnknownError),
        apiTagMandate :: Nil,
        Some(canDeleteSignatoryPanel :: Nil),
        http4sPartialFunction = Some(deleteSignatoryPanel))
    }
    initSignatoryPanelResourceDocs()

    // ─── Phase 2: Auth/JWT/validation/transaction-request endpoints (7) ──

    lazy val validateUserEmail: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / "email-validation" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ValidateUserEmailJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.ValidateUserEmailJsonV600]
            }
            token = postedData.token.trim
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Token cannot be empty", cc = Some(cc)) {
              token.nonEmpty
            }
            uniqueId <- NewStyle.function.tryons(
              s"$UserNotFoundByToken Invalid or expired validation token", 404, Some(cc)) {
              val signedJWT = com.nimbusds.jwt.SignedJWT.parse(token)
              val expiration = signedJWT.getJWTClaimsSet.getExpirationTime
              if (expiration == null || expiration.before(new java.util.Date()))
                throw new Exception("Token has expired")
              if (!CertificateUtil.verifywtWithHmacProtection(token))
                throw new Exception("Invalid token signature")
              signedJWT.getJWTClaimsSet.getSubject
            }
            authUser <- Future {
              code.model.dataAccess.AuthUser.findUserByValidationToken(uniqueId) match {
                case Full(u) => Full(u)
                case Empty => Empty
                case f: net.liftweb.common.Failure => f
              }
            }
            user <- NewStyle.function.tryons(
              s"$UserNotFoundByToken Invalid or expired validation token", 404, Some(cc)) {
              authUser.openOrThrowException("User not found")
            }
            _ <- Helper.booleanToFuture(s"$UserAlreadyValidated User email is already validated", cc = Some(cc)) {
              !user.validated.get
            }
            validatedUser <- Future(code.model.dataAccess.AuthUser.validateAndResetToken(user))
            _ <- Future(code.model.dataAccess.AuthUser.grantDefaultEntitlementsToAuthUser(validatedUser))
          } yield JSONFactory600.ValidateUserEmailResponseJsonV600(
            user_id = validatedUser.user.obj.map(_.userId).getOrElse(""),
            email = validatedUser.email.get,
            username = validatedUser.username.get,
            provider = validatedUser.provider.get,
            validated = validatedUser.validated.get,
            message = "Email validated successfully")
        }
    }

    lazy val resetPasswordComplete: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / "password" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostResetPasswordCompleteJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.PostResetPasswordCompleteJsonV600]
            }
            token = postedData.token.trim
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Token cannot be empty", cc = Some(cc)) {
              token.nonEmpty
            }
            _ <- Helper.booleanToFuture(InvalidStrongPasswordFormat, 400, Some(cc)) {
              APIUtil.fullPasswordValidation(postedData.new_password)
            }
            _ <- Helper.booleanToFuture(s"$UnknownError Invalid or expired reset token", cc = Some(cc)) {
              try CertificateUtil.verifywtWithHmacProtection(token) catch { case _: Exception => false }
            }
            uniqueId <- NewStyle.function.tryons(
              s"$UnknownError Invalid or expired reset token", 400, Some(cc)) {
              val signedJWT = com.nimbusds.jwt.SignedJWT.parse(token)
              val expiration = signedJWT.getJWTClaimsSet.getExpirationTime
              if (expiration == null || expiration.before(new java.util.Date()))
                throw new Exception("Token has expired")
              signedJWT.getJWTClaimsSet.getSubject
            }
            authUserBox <- Future(code.model.dataAccess.AuthUser.findUserByValidationToken(uniqueId))
            user <- NewStyle.function.tryons(
              s"$UnknownError Invalid or expired reset token", 400, Some(cc)) {
              authUserBox.openOrThrowException("User not found")
            }
          } yield {
            user.password.set(postedData.new_password)
            user.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
            user.save
            JSONFactory600.ResetPasswordCompleteResponseJsonV600("Password has been reset successfully.")
          }
        }
    }

    lazy val resetPasswordUrlAnonymous: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / "password-reset-url" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostResetPasswordUrlAnonymousJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[JSONFactory600.PostResetPasswordUrlAnonymousJsonV600]
            }
          } yield {
            val authUserBox = code.model.dataAccess.AuthUser.find(
              net.liftweb.mapper.By(code.model.dataAccess.AuthUser.username, postedData.username))
            (authUserBox, APIUtil.getPropsValue("portal_external_url")) match {
              case (Full(u), Full(portalUrl)) if u.validated.get && u.email.get == postedData.email =>
                u.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
                u.save
                val expiryMinutes = APIUtil.getPropsAsIntValue("password_reset_token_expiry_minutes", 120)
                val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                  .subject(u.uniqueId.get)
                  .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
                  .issueTime(new java.util.Date()).build()
                val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
                val resetLink = portalUrl + "/reset-password/" + java.net.URLEncoder.encode(jwtToken, "UTF-8")
                CommonsEmailWrapper.sendHtmlEmail(CommonsEmailWrapper.EmailContent(
                  from = code.model.dataAccess.AuthUser.emailFrom,
                  to = List(u.email.get),
                  bcc = code.model.dataAccess.AuthUser.bccEmail.toList,
                  subject = "Reset your password - " + u.username.get,
                  textContent = Some(s"Please use the following link to reset your password: $resetLink"),
                  htmlContent = Some(s"<p>Please use the following link to reset your password:</p><p><a href='$resetLink'>$resetLink</a></p>")))
              case _ => // do nothing — return same response to prevent user enumeration
            }
            JSONFactory600.ResetPasswordUrlAnonymousResponseJsonV600(
              "If the account exists, a password reset email has been sent.")
          }
        }
    }

    lazy val validateDynamicResourceDoc: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "dynamic-resource-docs" / "validate" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            body <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the JsonDynamicResourceDoc", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[code.dynamicResourceDoc.JsonDynamicResourceDoc]
            }
            _ <- Helper.booleanToFuture(
              s"""$InvalidJsonFormat The request_verb must be one of ["POST", "PUT", "GET", "DELETE"]""",
              cc = Some(cc)) {
              Set("POST", "PUT", "GET", "DELETE").contains(body.requestVerb)
            }
            _ <- Helper.booleanToFuture(
              s"""$InvalidJsonFormat When request_verb is "GET" or "DELETE", the example_request_body must be a blank String "" or just totally omit the field""",
              cc = Some(cc)) {
              (body.requestVerb, body.exampleRequestBody) match {
                case ("GET" | "DELETE", Some(net.liftweb.json.JString(s))) =>
                  org.apache.commons.lang3.StringUtils.isBlank(s)
                case ("GET" | "DELETE", Some(rb)) => rb == net.liftweb.json.JNothing
                case _ => true
              }
            }
          } yield try {
            code.api.dynamic.endpoint.helper.CompiledObjects(
              body.exampleRequestBody, body.successResponseBody, body.methodBody).validateDependency()
            ValidateDynamicResourceDocSuccessJsonV600(
              valid = true,
              message = "Dynamic Resource Doc method body is valid Scala and uses allowed dependencies.")
          } catch {
            case e: code.api.JsonResponseException =>
              val errorText = e.jsonResponse match {
                case code.api.util.APIUtil.JsonResponseExtractor(msg, _) => msg
                case _ => ""
              }
              ValidateDynamicResourceDocFailureJsonV600(
                valid = false, error = errorText, message = DynamicResourceDocMethodDependency,
                details = ValidateDynamicResourceDocErrorDetailsJsonV600(error_type = "DependencyError"))
            case e: Exception =>
              ValidateDynamicResourceDocFailureJsonV600(
                valid = false, error = Option(e.getMessage).getOrElse(""), message = DynamicCodeCompileFail,
                details = ValidateDynamicResourceDocErrorDetailsJsonV600(error_type = "CompilationError"))
          }
        }
    }

    // 4 transaction request types — all delegate to LocalMappedConnectorInternal
    private def txReqDelegate(req: org.http4s.Request[IO], bankIdStr: String, accountIdStr: String,
                              viewIdStr: String, kind: String): IO[org.http4s.Response[IO]] = {
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        val rawBody = cc.httpBody.getOrElse("")
        val bankId = BankId(bankIdStr)
        val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
        val viewId = ViewId(viewIdStr)
        val txType = com.openbankproject.commons.model.TransactionRequestType(kind)
        for {
          json <- Future(net.liftweb.json.parse(rawBody))
          (resp, _) <- code.bankconnectors.LocalMappedConnectorInternal
            .createTransactionRequest(bankId, accountId, viewId, txType, json)
        } yield resp
      }
    }

    lazy val createTransactionRequestHold: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr /
        "transaction-request-types" / "HOLD" / "transaction-requests" =>
        txReqDelegate(req, bankIdStr, accountIdStr, viewIdStr, "HOLD")
    }

    lazy val createTransactionRequestCardano: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr /
        "transaction-request-types" / "CARDANO" / "transaction-requests" =>
        txReqDelegate(req, bankIdStr, accountIdStr, viewIdStr, "CARDANO")
    }

    lazy val createTransactionRequestEthereumeSendTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr /
        "transaction-request-types" / "ETH_SEND_TRANSACTION" / "transaction-requests" =>
        txReqDelegate(req, bankIdStr, accountIdStr, viewIdStr, "ETH_SEND_TRANSACTION")
    }

    lazy val createTransactionRequestEthSendRawTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr /
        "transaction-request-types" / "ETH_SEND_RAW_TRANSACTION" / "transaction-requests" =>
        txReqDelegate(req, bankIdStr, accountIdStr, viewIdStr, "ETH_SEND_RAW_TRANSACTION")
    }

    private def initAuthAndTxReqResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(validateUserEmail), "POST",
        "/users/email-validation", "Validate User Email",
        """Validate a user's email using a JWT token.""",
        EmptyBody, EmptyBody,
        List(InvalidJsonFormat, UserNotFoundByToken, UserAlreadyValidated, UnknownError),
        apiTagUser :: Nil, None,
        http4sPartialFunction = Some(validateUserEmail))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(resetPasswordComplete), "POST",
        "/users/password", "Complete Password Reset",
        """Complete a password reset using a JWT token.""",
        EmptyBody, EmptyBody,
        List(InvalidJsonFormat, InvalidStrongPasswordFormat, UnknownError),
        apiTagUser :: Nil, None,
        http4sPartialFunction = Some(resetPasswordComplete))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(resetPasswordUrlAnonymous), "POST",
        "/users/password-reset-url", "Request Password Reset URL",
        """Anonymous endpoint — generates and emails a password-reset link.""",
        EmptyBody, EmptyBody,
        List(InvalidJsonFormat, UnknownError),
        apiTagUser :: Nil, None,
        http4sPartialFunction = Some(resetPasswordUrlAnonymous))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(validateDynamicResourceDoc), "POST",
        "/management/dynamic-resource-docs/validate", "Validate Dynamic Resource Doc",
        """Dry-run validation of a Dynamic Resource Doc (compile + dependency check).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        apiTagDynamicResourceDoc :: Nil,
        Some(canCreateDynamicResourceDoc :: Nil),
        http4sPartialFunction = Some(validateDynamicResourceDoc))
      val txReqErrors = List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest, InvalidTransactionRequestType,
        InvalidJsonFormat, NotPositiveAmount, InvalidTransactionRequestCurrency,
        TransactionDisabled, UnknownError)
      val txReqTags = apiTagTransactionRequest :: apiTagPSD2PIS :: apiTagPsd2 :: Nil
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createTransactionRequestHold), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/HOLD/transaction-requests",
        "Create Transaction Request (HOLD)",
        """Create a HOLD transaction request.""",
        EmptyBody, EmptyBody, txReqErrors, txReqTags, None,
        http4sPartialFunction = Some(createTransactionRequestHold))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createTransactionRequestCardano), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/CARDANO/transaction-requests",
        "Create Transaction Request (CARDANO)",
        """Create a CARDANO transaction request.""",
        EmptyBody, EmptyBody, txReqErrors, txReqTags, None,
        http4sPartialFunction = Some(createTransactionRequestCardano))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createTransactionRequestEthereumeSendTransaction), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_TRANSACTION/transaction-requests",
        "Create Transaction Request (ETH_SEND_TRANSACTION)",
        """Create an ETH_SEND_TRANSACTION transaction request.""",
        EmptyBody, EmptyBody, txReqErrors, txReqTags, None,
        http4sPartialFunction = Some(createTransactionRequestEthereumeSendTransaction))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createTransactionRequestEthSendRawTransaction), "POST",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_RAW_TRANSACTION/transaction-requests",
        "Create Transaction Request (ETH_SEND_RAW_TRANSACTION)",
        """Create an ETH_SEND_RAW_TRANSACTION transaction request.""",
        EmptyBody, EmptyBody, txReqErrors, txReqTags, None,
        http4sPartialFunction = Some(createTransactionRequestEthSendRawTransaction))
    }
    initAuthAndTxReqResourceDocs()

    // ─── Phase 2: User memberships, access listing, customer creation (4) ─

    lazy val getUserGroupMemberships: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "group-entitlements" =>
        EndpointHelpers.withUser(req) { (u, cc) =>
          for {
            (_, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            entitlements <- Future(code.entitlement.Entitlement.entitlement.vend.getEntitlementsByUserId(userId))
            groupEntitlements = entitlements.toOption.getOrElse(List.empty).filter(_.process == Some("GROUP_MEMBERSHIP"))
            groupIds = groupEntitlements.flatMap(_.groupId).distinct
            _ <- Future.sequence {
              groupIds.flatMap { gid =>
                code.group.GroupTrait.group.vend.getGroup(gid).toOption.map { g =>
                  g.bankId match {
                    case Some(bid) =>
                      NewStyle.function.hasAtLeastOneEntitlement(bid, u.userId,
                        canGetUserGroupMembershipsAtOneBank :: canGetUserGroupMembershipsAtAllBanks :: Nil, Some(cc))
                    case None =>
                      NewStyle.function.hasEntitlement("", u.userId, canGetUserGroupMembershipsAtAllBanks, Some(cc))
                  }
                }
              }
            }
            groups <- Future.sequence(groupIds.map(gid =>
              Future(code.group.GroupTrait.group.vend.getGroup(gid))))
            validGroups = groups.flatten
          } yield {
            val memberships = validGroups.map { g =>
              val grpEnts = groupEntitlements.filter(_.groupId.contains(g.groupId)).map(_.roleName).distinct
              JSONFactory600.UserGroupMembershipJsonV600(
                group_id = g.groupId, user_id = userId, bank_id = g.bankId,
                group_name = g.groupName, list_of_entitlements = grpEnts)
            }
            JSONFactory600.UserGroupMembershipsJsonV600(group_entitlements = memberships)
          }
        }
    }

    lazy val getUsersWithAccountAccess: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "users-with-access" =>
        EndpointHelpers.withBankAccount(req) { (_, _, cc) =>
          val bankId = BankId(bankIdStr)
          val accountId = com.openbankproject.commons.model.AccountId(accountIdStr)
          val viewId = ViewId(viewIdStr)
          val bia = com.openbankproject.commons.model.BankIdAccountId(bankId, accountId)
          for {
            _ <- Future {
              code.views.Views.views.vend.customViewFuture(viewId, bia).flatMap {
                case Full(v) => Future.successful(Full(v))
                case _ => code.views.Views.views.vend.systemViewFuture(viewId)
              }
            }.flatten.map(unboxFullOrFail(_, Some(cc), s"$ViewNotFound Current ViewId is ${viewId.value}"))
            permissions <- Future(code.views.Views.views.vend.permissions(bia))
            accountAccessUsers = permissions.flatMap { perm =>
              if (perm.views.exists(_.viewId == viewId))
                Some(JSONFactory600.UserWithViewAccessJsonV600(
                  user_id = perm.user.userId, username = perm.user.name,
                  email = perm.user.emailAddress, provider = perm.user.provider,
                  access_source = "ACCOUNT_ACCESS"))
              else None
            }
            accountAccessUserIds = accountAccessUsers.map(_.user_id).toSet
            abacEntitlements = code.entitlement.Entitlement.entitlement.vend.getEntitlementsByRole(canExecuteAbacRule.toString).getOrElse(Nil)
            abacUserIds = abacEntitlements.map(_.userId).distinct.filterNot(accountAccessUserIds.contains)
            abacUsersF: Future[List[JSONFactory600.UserWithViewAccessJsonV600]] = if (abacUserIds.isEmpty)
              Future.successful(List.empty[JSONFactory600.UserWithViewAccessJsonV600])
            else
              code.users.Users.users.vend.getUsersByUserIdsFuture(abacUserIds).flatMap { users =>
                Future.sequence(users.map { user =>
                  code.abacrule.AbacRuleEngine.executeRulesByPolicyDetailed(
                    policy = ABAC_POLICY_ACCOUNT_ACCESS,
                    authenticatedUserId = user.userId, callContext = cc,
                    bankId = Some(bankId.value), accountId = Some(accountId.value),
                    viewId = Some(viewId.value)
                  ).map[Option[JSONFactory600.UserWithViewAccessJsonV600]] {
                    case Full((true, _)) => Some(JSONFactory600.UserWithViewAccessJsonV600(
                      user_id = user.userId, username = user.name,
                      email = user.emailAddress, provider = user.provider,
                      access_source = "ABAC"))
                    case _ => None
                  }.recover { case _ => None }
                }).map(_.flatten)
              }
            abacUsers <- abacUsersF
          } yield JSONFactory600.UsersWithViewAccessJsonV600(users = accountAccessUsers ++ abacUsers)
        }
    }

    lazy val createRetailCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "retail-customers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bankId = BankId(bankIdStr)
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostRetailCustomerJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostRetailCustomerJsonV600]
            }
            _ <- Helper.booleanToFuture(
              InvalidJsonContent + s" The field dependants(${postedData.dependants.getOrElse(0)}) not equal the length(${postedData.dob_of_dependants.getOrElse(Nil).length}) of dob_of_dependants array",
              400, Some(cc)) {
              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
            }
            dateOfBirth <- NewStyle.function.tryons(
              s"$InvalidJsonFormat date_of_birth must be in YYYY-MM-DD format (e.g., 1990-05-15)",
              400, Some(cc)) {
              postedData.date_of_birth.map { ds =>
                val f = new java.text.SimpleDateFormat("yyyy-MM-dd")
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                f.setLenient(false); f.parse(ds)
              }.orNull
            }
            dobOfDependants <- NewStyle.function.tryons(
              s"$InvalidJsonFormat dob_of_dependants must contain dates in YYYY-MM-DD format (e.g., 2010-03-20)",
              400, Some(cc)) {
              postedData.dob_of_dependants.getOrElse(Nil).map { ds =>
                val f = new java.text.SimpleDateFormat("yyyy-MM-dd")
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                f.setLenient(false); f.parse(ds)
              }
            }
            customerNumber = postedData.customer_number.getOrElse(scala.util.Random.nextInt(Integer.MAX_VALUE).toString)
            _ <- Helper.booleanToFuture(
              s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc = Some(cc)) {
              !APIUtil.`checkIfContains::::`(customerNumber)
            }
            _ <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, Some(cc))
            (customer, _) <- NewStyle.function.createCustomerC2(
              bankId, postedData.legal_name, customerNumber, postedData.mobile_phone_number,
              postedData.email.getOrElse(""),
              com.openbankproject.commons.model.CustomerFaceImage(
                postedData.face_image.map(_.date).getOrElse(null),
                postedData.face_image.map(_.url).getOrElse("")),
              dateOfBirth, postedData.relationship_status.getOrElse(""),
              postedData.dependants.getOrElse(0), dobOfDependants,
              postedData.highest_education_attained.getOrElse(""),
              postedData.employment_status.getOrElse(""),
              postedData.kyc_status.getOrElse(false),
              postedData.last_ok_date.getOrElse(null),
              postedData.credit_rating.map(i => com.openbankproject.commons.model.CreditRating(i.rating, i.source)),
              postedData.credit_limit.map(i => com.openbankproject.commons.model.CreditLimit(i.currency, i.amount)),
              postedData.title.getOrElse(""), postedData.branch_id.getOrElse(""),
              postedData.name_suffix.getOrElse(""), "INDIVIDUAL", "", Some(cc))
          } yield JSONFactory600.createCustomerJson(customer)
        }
    }

    lazy val createCorporateCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "corporate-customers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bankId = BankId(bankIdStr)
          for {
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the PostCorporateCustomerJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostCorporateCustomerJsonV600]
            }
            customerNumber = postedData.customer_number.getOrElse(scala.util.Random.nextInt(Integer.MAX_VALUE).toString)
            _ <- Helper.booleanToFuture(
              s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc = Some(cc)) {
              !APIUtil.`checkIfContains::::`(customerNumber)
            }
            _ <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, Some(cc))
            customerType = postedData.customer_type.getOrElse("CORPORATE")
            _ <- Helper.booleanToFuture(
              InvalidCustomerType + " For corporate customers, must be CORPORATE or SUBSIDIARY.",
              400, Some(cc)) {
              List("CORPORATE", "SUBSIDIARY").contains(customerType)
            }
            parentId = postedData.parent_customer_id.getOrElse("")
            _ <- if (parentId.nonEmpty)
              NewStyle.function.getCustomerByCustomerId(parentId, Some(cc)).map(_ => ())
            else Future.successful(())
            (customer, _) <- NewStyle.function.createCustomerC2(
              bankId, postedData.legal_name, customerNumber, postedData.mobile_phone_number,
              postedData.email.getOrElse(""),
              com.openbankproject.commons.model.CustomerFaceImage(null, ""),
              null, "", 0, Nil, "", "",
              postedData.kyc_status.getOrElse(false),
              postedData.last_ok_date.getOrElse(null),
              postedData.credit_rating.map(i => com.openbankproject.commons.model.CreditRating(i.rating, i.source)),
              postedData.credit_limit.map(i => com.openbankproject.commons.model.CreditLimit(i.currency, i.amount)),
              "", postedData.branch_id.getOrElse(""), "", customerType, parentId, Some(cc))
          } yield JSONFactory600.createCustomerJson(customer)
        }
    }

    private def initUserCustomerResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserGroupMemberships), "GET",
        "/users/USER_ID/group-entitlements", "Get User Group Memberships",
        """Get all group memberships for a user.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByUserId, UnknownError),
        apiTagGroup :: apiTagUser :: apiTagEntitlement :: Nil,
        Some(canGetUserGroupMembershipsAtAllBanks :: canGetUserGroupMembershipsAtOneBank :: Nil),
        http4sPartialFunction = Some(getUserGroupMemberships))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUsersWithAccountAccess), "GET",
        "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/users-with-access", "Get Users With Account Access",
        """List users with access to the specified view (AccountAccess + ABAC).""",
        EmptyBody, EmptyBody,
        List($BankNotFound, $BankAccountNotFound, ViewNotFound, UnknownError),
        apiTagAccount :: apiTagView :: Nil,
        Some(canSeeAccountAccessForAnyUser :: Nil),
        http4sPartialFunction = Some(getUsersWithAccountAccess))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createRetailCustomer), "POST",
        "/banks/BANK_ID/retail-customers", "Create Retail Customer",
        """Create a retail (individual) customer.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound,
          InvalidJsonFormat, InvalidJsonContent, UnknownError),
        apiTagRetailCustomer :: apiTagCustomer :: Nil,
        Some(canCreateCustomer :: canCreateCustomerAtAnyBank :: Nil),
        http4sPartialFunction = Some(createRetailCustomer))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(createCorporateCustomer), "POST",
        "/banks/BANK_ID/corporate-customers", "Create Corporate Customer",
        """Create a corporate (or subsidiary) customer.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, $BankNotFound,
          InvalidJsonFormat, InvalidCustomerType, UnknownError),
        apiTagCorporateCustomer :: apiTagCustomer :: Nil,
        Some(canCreateCustomer :: canCreateCustomerAtAnyBank :: Nil),
        http4sPartialFunction = Some(createCorporateCustomer))
    }
    initUserCustomerResourceDocs()

    // ─── Phase 2: Final batch — getUserByUserId, directLogin, ABAC (5), dynamic-entity backup/cascade (3) ─

    lazy val getUserByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "user-id" / userId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            userBox <- code.users.Users.users.vend.getUserByUserIdFuture(userId)
            user <- Future(unboxFullOrFail(userBox, Some(cc),
              s"$UserNotFoundByUserId Current UserId($userId)"))
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
            agreements <- Future {
              val ami = code.users.UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "accept_marketing_info")
              val tac = code.users.UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "terms_and_conditions")
              val pc = code.users.UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "privacy_conditions")
              val all = ami.toList ::: tac.toList ::: pc.toList
              if (all.isEmpty) None else Some(all)
            }
            isLocked = code.loginattempts.LoginAttempt.userIsLocked(user.provider, user.name)
            authUser = code.model.dataAccess.AuthUser.find(
              By(code.model.dataAccess.AuthUser.user, user.userPrimaryKey.value))
            userMetrics <- Future {
              code.metrics.MappedMetric.findAll(
                By(code.metrics.MappedMetric.userId, userId),
                net.liftweb.mapper.OrderBy(code.metrics.MappedMetric.date, net.liftweb.mapper.Descending),
                net.liftweb.mapper.MaxRows(5))
            }
            lastActivityDate = userMetrics.headOption.map(_.getDate())
            recentOperationIds = userMetrics.map(_.getImplementedByPartialFunction()).distinct.take(5)
          } yield JSONFactory600.createUserInfoJsonV600(
            user,
            authUser.map(_.firstName.get).getOrElse(""),
            authUser.map(_.lastName.get).getOrElse(""),
            entitlements, agreements, isLocked, lastActivityDate, recentOperationIds)
        }
    }

    // DirectLogin header parser — mirrors the parsing in code.api.directlogin.DirectLogin.getAllParameters
    // but reads from CallContext.requestHeaders (populated by the http4s context builder) instead of
    // Lift's thread-local S.request.
    private def parseDirectLoginParams(cc: CallContext): Map[String, String] = {
      def find(name: String): Option[String] = cc.requestHeaders
        .find(_.name.equalsIgnoreCase(name))
        .flatMap(_.values.headOption)
      val directLoginHeader = find("DirectLogin")
      val authHeader = find("Authorization")
      val raw = directLoginHeader
        .orElse(authHeader.filter(h => h.startsWith("DirectLogin") || h.contains("DirectLogin")))
        .getOrElse("")
      val cleaned = raw.stripPrefix("DirectLogin").split(",").map(_.trim).toList
      val keys = Set("consumer_key", "token", "username", "password")
      cleaned.flatMap { entry =>
        if (entry.contains("=")) {
          val s = entry.split("=", 2)
          val v = s(1).replaceAll("^\"|\"$", "")
          if (keys.contains(s(0)) && v.nonEmpty) Some(s(0) -> v) else None
        } else None
      }.toMap
    }

    lazy val directLoginEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "logins" / "direct" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          // If the parser found nothing usable, fall back to a single "error" key so that
          // validatorFutureWithParams returns the MissingDirectLoginHeader message (preserves
          // Lift's getAllParameters behaviour for the no-header case).
          val parsed = parseDirectLoginParams(cc)
          val params =
            if (parsed.isEmpty) Map("error" -> code.api.util.ErrorMessages.MissingDirectLoginHeader)
            else parsed
          for {
            triple <- code.api.DirectLogin.validatorFutureWithParams("authorizationToken", "POST", params)
            (httpCode, message, dlParams) = triple
            tokenTriple = code.api.DirectLogin.createTokenCommonPart(httpCode, message, dlParams)
            _ <- Future(code.api.DirectLogin.grantEntitlementsToUseDynamicEndpointsInSpacesInDirectLogin(tokenTriple._3))
          } yield {
            if (tokenTriple._1 == 200) JSONFactory600.createTokenJSON(tokenTriple._2)
            else unboxFullOrFail(Empty, None, tokenTriple._2, tokenTriple._1)
          }
        }
    }

    lazy val validateAbacRule: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "abac-rules" / "validate" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            validateJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ValidateAbacRuleJsonV600]
            }
            _ <- NewStyle.function.tryons(AbacRuleCodeEmpty, 400, Some(cc)) {
              validateJson.rule_code.trim.nonEmpty
            }
            box <- code.abacrule.AbacRuleEngine.validateRuleCodeAsync(validateJson.rule_code)
          } yield box match {
            case Full(msg) => ValidateAbacRuleSuccessJsonV600(valid = true, message = msg): Any
            case Failure(errorMsg, _, _) =>
              val cleanError = errorMsg.replace("Invalid ABAC rule code: ", "")
                .replace("Failed to compile ABAC rule: ", "")
              val (obpMsg, errorType) =
                if (cleanError.toLowerCase.contains("too permissive") || cleanError.toLowerCase.contains("tautological")) {
                  val ec = if (cleanError.toLowerCase.contains("statistical"))
                    AbacRuleStatisticallyTooPermissive else AbacRuleTooPermissive
                  (ec, "PermissivenessError")
                } else if (cleanError.toLowerCase.contains("type mismatch") ||
                  (cleanError.toLowerCase.contains("found:") && cleanError.toLowerCase.contains("required: boolean")))
                  (AbacRuleTypeMismatch, "TypeError")
                else if (cleanError.toLowerCase.contains("syntax") || cleanError.toLowerCase.contains("parse"))
                  (AbacRuleSyntaxError, "SyntaxError")
                else if (cleanError.toLowerCase.contains("not found") || cleanError.toLowerCase.contains("not a member"))
                  (AbacRuleFieldReferenceError, "FieldReferenceError")
                else if (cleanError.toLowerCase.contains("compilation failed") ||
                  cleanError.toLowerCase.contains("reflective compilation has failed"))
                  (AbacRuleCompilationFailed, "CompilationError")
                else (AbacRuleValidationFailed, "ValidationError")
              ValidateAbacRuleFailureJsonV600(
                valid = false, error = cleanError, message = obpMsg,
                details = ValidateAbacRuleErrorDetailsJsonV600(error_type = errorType))
            case _ =>
              ValidateAbacRuleFailureJsonV600(
                valid = false, error = "Unknown validation error",
                message = AbacRuleValidationFailed,
                details = ValidateAbacRuleErrorDetailsJsonV600(error_type = "UnknownError"))
          }
        }
    }

    lazy val executeAbacRule: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "abac-rules" / ruleId / "execute" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            execJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ExecuteAbacRuleJsonV600]
            }
            ruleBox <- Future(code.abacrule.MappedAbacRuleProvider.getAbacRuleById(ruleId))
            _ <- Future(unboxFullOrFail(ruleBox, Some(cc), s"ABAC Rule not found with ID: $ruleId", 404))
            effectiveUserId = execJson.authenticated_user_id.getOrElse(u.userId)
            result <- code.abacrule.AbacRuleEngine.executeRule(
              ruleId = ruleId, authenticatedUserId = effectiveUserId,
              onBehalfOfUserId = execJson.on_behalf_of_user_id, userId = execJson.user_id,
              callContext = cc, bankId = execJson.bank_id, accountId = execJson.account_id,
              viewId = execJson.view_id, transactionId = execJson.transaction_id,
              transactionRequestId = execJson.transaction_request_id, customerId = execJson.customer_id)
              .map {
                case Full(allowed) => AbacRuleResultJsonV600(result = allowed)
                case _ => AbacRuleResultJsonV600(result = false)
              }
          } yield result
        }
    }

    lazy val executeAbacPolicy: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "abac-policies" / policy / "execute" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val u = cc.user.openOrThrowException("User not found in CallContext")
          for {
            execJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ExecuteAbacRuleJsonV600]
            }
            _ <- Future {
              if (Constant.ABAC_POLICIES.contains(policy)) Full(true)
              else Failure(s"Policy not found: $policy. Available policies: ${Constant.ABAC_POLICIES.mkString(", ")}")
            }.map(unboxFullOrFail(_, Some(cc), s"Invalid ABAC Policy: $policy", 404))
            effectiveUserId = execJson.authenticated_user_id.getOrElse(u.userId)
            result <- code.abacrule.AbacRuleEngine.executeRulesByPolicy(
              policy = policy, authenticatedUserId = effectiveUserId,
              onBehalfOfUserId = execJson.on_behalf_of_user_id, userId = execJson.user_id,
              callContext = cc, bankId = execJson.bank_id, accountId = execJson.account_id,
              viewId = execJson.view_id, transactionId = execJson.transaction_id,
              transactionRequestId = execJson.transaction_request_id, customerId = execJson.customer_id)
              .map {
                case Full(allowed) => AbacRuleResultJsonV600(result = allowed)
                case _ => AbacRuleResultJsonV600(result = false)
              }
          } yield result
        }
    }

    // 218-line static ABAC schema. Same shape as Lift's. Built once per call (no caching).
    private def buildAbacRuleSchemaJson(): AbacRuleSchemaJsonV600 = AbacRuleSchemaJsonV600(
      parameters = List(
        AbacParameterJsonV600("authenticatedUser", "User", "The logged-in user (always present)", required = true, "User"),
        AbacParameterJsonV600("authenticatedUserAttributes", "List[UserAttributeTrait]", "Non-personal attributes of authenticated user", required = true, "User"),
        AbacParameterJsonV600("authenticatedUserAuthContext", "List[UserAuthContext]", "Auth context of authenticated user", required = true, "User"),
        AbacParameterJsonV600("authenticatedUserEntitlements", "List[Entitlement]", "Entitlements (roles) of authenticated user", required = true, "User"),
        AbacParameterJsonV600("onBehalfOfUserOpt", "Option[User]", "User being acted on behalf of (delegation)", required = false, "User"),
        AbacParameterJsonV600("onBehalfOfUserAttributes", "List[UserAttributeTrait]", "Attributes of delegation user", required = false, "User"),
        AbacParameterJsonV600("onBehalfOfUserAuthContext", "List[UserAuthContext]", "Auth context of delegation user", required = false, "User"),
        AbacParameterJsonV600("onBehalfOfUserEntitlements", "List[Entitlement]", "Entitlements (roles) of delegation user", required = false, "User"),
        AbacParameterJsonV600("userOpt", "Option[User]", "Target user being evaluated", required = false, "User"),
        AbacParameterJsonV600("userAttributes", "List[UserAttributeTrait]", "Attributes of target user", required = false, "User"),
        AbacParameterJsonV600("bankOpt", "Option[Bank]", "Bank context", required = false, "Bank"),
        AbacParameterJsonV600("bankAttributes", "List[BankAttributeTrait]", "Bank attributes", required = false, "Bank"),
        AbacParameterJsonV600("accountOpt", "Option[BankAccount]", "Account context", required = false, "Account"),
        AbacParameterJsonV600("accountAttributes", "List[AccountAttribute]", "Account attributes", required = false, "Account"),
        AbacParameterJsonV600("transactionOpt", "Option[Transaction]", "Transaction context", required = false, "Transaction"),
        AbacParameterJsonV600("transactionAttributes", "List[TransactionAttribute]", "Transaction attributes", required = false, "Transaction"),
        AbacParameterJsonV600("transactionRequestOpt", "Option[TransactionRequest]", "Transaction request context", required = false, "TransactionRequest"),
        AbacParameterJsonV600("transactionRequestAttributes", "List[TransactionRequestAttributeTrait]", "Transaction request attributes", required = false, "TransactionRequest"),
        AbacParameterJsonV600("customerOpt", "Option[Customer]", "Customer context", required = false, "Customer"),
        AbacParameterJsonV600("customerAttributes", "List[CustomerAttribute]", "Customer attributes", required = false, "Customer"),
        AbacParameterJsonV600("callContext", "Option[CallContext]", "Request call context with metadata (IP, user agent, etc.)", required = false, "Context")
      ),
      object_types = List(
        AbacObjectTypeJsonV600("User", "User object with profile and authentication information", List(
          AbacObjectPropertyJsonV600("userId", "String", "Unique user ID"),
          AbacObjectPropertyJsonV600("emailAddress", "String", "User email address"),
          AbacObjectPropertyJsonV600("provider", "String", "Authentication provider (e.g., 'obp')"),
          AbacObjectPropertyJsonV600("name", "String", "User display name"),
          AbacObjectPropertyJsonV600("idGivenByProvider", "String", "ID given by provider (same as username)"),
          AbacObjectPropertyJsonV600("createdByConsentId", "Option[String]", "Consent ID that created the user (if any)"),
          AbacObjectPropertyJsonV600("isDeleted", "Option[Boolean]", "Whether user is deleted")
        )),
        AbacObjectTypeJsonV600("Bank", "Bank object", List(
          AbacObjectPropertyJsonV600("bankId", "BankId", "Bank ID"),
          AbacObjectPropertyJsonV600("fullName", "String", "Bank full name"),
          AbacObjectPropertyJsonV600("shortName", "String", "Bank short name"),
          AbacObjectPropertyJsonV600("logoUrl", "String", "Bank logo URL"),
          AbacObjectPropertyJsonV600("websiteUrl", "String", "Bank website URL"),
          AbacObjectPropertyJsonV600("bankRoutingScheme", "String", "Bank routing scheme"),
          AbacObjectPropertyJsonV600("bankRoutingAddress", "String", "Bank routing address")
        )),
        AbacObjectTypeJsonV600("BankAccount", "Bank account object", List(
          AbacObjectPropertyJsonV600("accountId", "AccountId", "Account ID"),
          AbacObjectPropertyJsonV600("bankId", "BankId", "Bank ID"),
          AbacObjectPropertyJsonV600("accountType", "String", "Account type"),
          AbacObjectPropertyJsonV600("balance", "BigDecimal", "Account balance"),
          AbacObjectPropertyJsonV600("currency", "String", "Account currency"),
          AbacObjectPropertyJsonV600("name", "String", "Account name"),
          AbacObjectPropertyJsonV600("label", "String", "Account label"),
          AbacObjectPropertyJsonV600("number", "String", "Account number"),
          AbacObjectPropertyJsonV600("lastUpdate", "Date", "Last update date"),
          AbacObjectPropertyJsonV600("branchId", "String", "Branch ID"),
          AbacObjectPropertyJsonV600("accountRoutings", "List[AccountRouting]", "Account routings")
        )),
        AbacObjectTypeJsonV600("Transaction", "Transaction object", List(
          AbacObjectPropertyJsonV600("id", "TransactionId", "Transaction ID"),
          AbacObjectPropertyJsonV600("uuid", "String", "Universally unique ID"),
          AbacObjectPropertyJsonV600("thisAccount", "BankAccount", "This account"),
          AbacObjectPropertyJsonV600("otherAccount", "Counterparty", "Other account/counterparty"),
          AbacObjectPropertyJsonV600("transactionType", "String", "Transaction type (e.g., cash withdrawal)"),
          AbacObjectPropertyJsonV600("amount", "BigDecimal", "Transaction amount"),
          AbacObjectPropertyJsonV600("currency", "String", "Transaction currency (ISO 4217)"),
          AbacObjectPropertyJsonV600("description", "Option[String]", "Bank provided label"),
          AbacObjectPropertyJsonV600("startDate", "Date", "Date transaction was initiated"),
          AbacObjectPropertyJsonV600("finishDate", "Option[Date]", "Date money finished changing hands"),
          AbacObjectPropertyJsonV600("balance", "BigDecimal", "New balance after transaction"),
          AbacObjectPropertyJsonV600("status", "Option[String]", "Transaction status")
        )),
        AbacObjectTypeJsonV600("TransactionRequest", "Transaction request object", List(
          AbacObjectPropertyJsonV600("id", "TransactionRequestId", "Transaction request ID"),
          AbacObjectPropertyJsonV600("type", "String", "Transaction request type"),
          AbacObjectPropertyJsonV600("from", "TransactionRequestAccount", "From account"),
          AbacObjectPropertyJsonV600("status", "String", "Transaction request status"),
          AbacObjectPropertyJsonV600("start_date", "Date", "Start date"),
          AbacObjectPropertyJsonV600("end_date", "Date", "End date"),
          AbacObjectPropertyJsonV600("transaction_ids", "String", "Associated transaction IDs"),
          AbacObjectPropertyJsonV600("charge", "TransactionRequestCharge", "Charge information"),
          AbacObjectPropertyJsonV600("this_bank_id", "BankId", "This bank ID"),
          AbacObjectPropertyJsonV600("this_account_id", "AccountId", "This account ID"),
          AbacObjectPropertyJsonV600("counterparty_id", "CounterpartyId", "Counterparty ID")
        )),
        AbacObjectTypeJsonV600("Customer", "Customer object", List(
          AbacObjectPropertyJsonV600("customerId", "String", "Customer ID (UUID)"),
          AbacObjectPropertyJsonV600("bankId", "String", "Bank ID"),
          AbacObjectPropertyJsonV600("number", "String", "Customer number (bank identifier)"),
          AbacObjectPropertyJsonV600("legalName", "String", "Customer legal name"),
          AbacObjectPropertyJsonV600("mobileNumber", "String", "Customer mobile number"),
          AbacObjectPropertyJsonV600("email", "String", "Customer email"),
          AbacObjectPropertyJsonV600("dateOfBirth", "Date", "Date of birth"),
          AbacObjectPropertyJsonV600("relationshipStatus", "String", "Relationship status"),
          AbacObjectPropertyJsonV600("dependents", "Integer", "Number of dependents")
        )),
        AbacObjectTypeJsonV600("UserAttributeTrait", "User attribute", List(
          AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
          AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
          AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type (STRING, INTEGER, DOUBLE, DATE_WITH_DAY)")
        )),
        AbacObjectTypeJsonV600("AccountAttribute", "Account attribute", List(
          AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
          AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
          AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type")
        )),
        AbacObjectTypeJsonV600("TransactionAttribute", "Transaction attribute", List(
          AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
          AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
          AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type")
        )),
        AbacObjectTypeJsonV600("CustomerAttribute", "Customer attribute", List(
          AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
          AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
          AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type")
        )),
        AbacObjectTypeJsonV600("Entitlement", "User entitlement (role)", List(
          AbacObjectPropertyJsonV600("entitlementId", "String", "Entitlement ID"),
          AbacObjectPropertyJsonV600("roleName", "String", "Role name (e.g., CanCreateAccount, CanReadTransactions)"),
          AbacObjectPropertyJsonV600("bankId", "String", "Bank ID (empty string for system-wide roles)"),
          AbacObjectPropertyJsonV600("userId", "String", "User ID this entitlement belongs to")
        )),
        AbacObjectTypeJsonV600("CallContext", "Request context with metadata", List(
          AbacObjectPropertyJsonV600("correlationId", "String", "Correlation ID for request tracking"),
          AbacObjectPropertyJsonV600("url", "Option[String]", "Request URL"),
          AbacObjectPropertyJsonV600("verb", "Option[String]", "HTTP verb (GET, POST, etc.)"),
          AbacObjectPropertyJsonV600("ipAddress", "Option[String]", "Client IP address"),
          AbacObjectPropertyJsonV600("userAgent", "Option[String]", "Client user agent"),
          AbacObjectPropertyJsonV600("implementedByPartialFunction", "Option[String]", "Endpoint implementation name"),
          AbacObjectPropertyJsonV600("startTime", "Option[Date]", "Request start time"),
          AbacObjectPropertyJsonV600("endTime", "Option[Date]", "Request end time")
        ))
      ),
      examples = List(
        AbacRuleExampleJsonV600(
          rule_name = "Branch Manager Internal Account Access",
          rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadAccountsAtOneBank\") && authenticatedUserAttributes.exists(a => a.name == \"branch\" && accountAttributes.exists(aa => aa.name == \"branch\" && a.value == aa.value)) && callContext.exists(_.verb.exists(_ == \"GET\")) && accountOpt.exists(_.accountType == \"CURRENT\")",
          description = "Allow GET access to current accounts when user has CanReadAccountsAtOneBank role and branch matches account's branch",
          policy = "account-access", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "Internal Network High-Value Transaction Review",
          rule_code = "callContext.exists(_.ipAddress.exists(_.startsWith(\"10.\"))) && authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadTransactionsAtOneBank\") && transactionOpt.exists(_.amount > 10000)",
          description = "Allow users with CanReadTransactionsAtOneBank role on internal network to review high-value transactions over 10,000",
          policy = "transaction-access", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "Department Head Same-Department Account Read where overdrawn",
          rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadAccountsAtOneBank\") && authenticatedUserAttributes.exists(ua => ua.name == \"department\" && accountAttributes.exists(aa => aa.name == \"department\" && ua.value == aa.value)) && callContext.exists(_.url.exists(_.contains(\"/accounts/\"))) && accountOpt.exists(_.balance < 0)",
          description = "Allow users with CanReadAccountsAtOneBank role to read overdrawn accounts in their department",
          policy = "account-access", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "Manager Internal Network Transaction Approval",
          rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanCreateTransactionRequest\") && callContext.exists(_.ipAddress.exists(ip => ip.startsWith(\"10.\") || ip.startsWith(\"192.168.\"))) && transactionRequestOpt.exists(tr => tr.status == \"PENDING\" && tr.charge.value.toDouble < 50000)",
          description = "Allow users with CanCreateTransactionRequest role on internal network to approve pending transaction requests under 50,000",
          policy = "transaction-request", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "KYC Officer Customer Creation from Branch",
          rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanCreateCustomer\") && authenticatedUserAttributes.exists(a => a.name == \"certification\" && a.value == \"kyc_certified\") && callContext.exists(_.verb.exists(_ == \"POST\")) && callContext.exists(_.ipAddress.exists(_.startsWith(\"10.20.\"))) && customerAttributes.exists(ca => ca.name == \"onboarding_status\" && ca.value == \"pending\")",
          description = "Allow users with CanCreateCustomer role and KYC certification to create customers via POST from branch network (10.20.x.x) when status is pending",
          policy = "customer-access", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "International Team Foreign Currency Transaction",
          rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadTransactionsAtOneBank\") && authenticatedUserAttributes.exists(a => a.name == \"team\" && a.value == \"international\") && callContext.exists(_.url.exists(_.contains(\"/transactions/\"))) && transactionOpt.exists(t => t.currency != \"USD\" && t.amount < 100000) && accountOpt.exists(a => accountAttributes.exists(aa => aa.name == \"international_enabled\" && aa.value == \"true\"))",
          description = "Allow international team users with CanReadTransactionsAtOneBank role to access foreign currency transactions under 100k on international-enabled accounts",
          policy = "transaction-access", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "Assistant with Limited Delegation Account View",
          rule_code = "onBehalfOfUserOpt.isDefined && onBehalfOfUserEntitlements.exists(e => e.roleName == \"CanReadAccountsAtOneBank\") && authenticatedUserAttributes.exists(a => a.name == \"assistant_of\" && onBehalfOfUserOpt.exists(u => a.value == u.userId)) && callContext.exists(_.verb.exists(_ == \"GET\")) && accountOpt.exists(a => accountAttributes.exists(aa => aa.name == \"tier\" && List(\"gold\", \"platinum\").contains(aa.value)))",
          description = "Allow assistants to view gold/platinum accounts via GET when acting on behalf of a user with CanReadAccountsAtOneBank role",
          policy = "account-access", is_active = true
        ),
        AbacRuleExampleJsonV600(
          rule_name = "Fraud Analyst High-Risk Transaction Access",
          rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadTransactionsAtOneBank\") && callContext.exists(c => c.verb.exists(_ == \"GET\") && c.implementedByPartialFunction.exists(_.contains(\"Transaction\"))) && transactionAttributes.exists(ta => ta.name == \"risk_score\" && ta.value.toInt >= 75) && transactionOpt.exists(_.status.exists(_ != \"COMPLETED\"))",
          description = "Allow users with CanReadTransactionsAtOneBank role to GET high-risk (score ≥75) non-completed transactions",
          policy = "transaction-access", is_active = true
        )
      ),
      available_operators = List(
        "==", "!=", "&&", "||", "!", ">", "<", ">=", "<=",
        "contains", "startsWith", "endsWith",
        "isDefined", "isEmpty", "nonEmpty",
        "exists", "forall", "find", "filter",
        "get", "getOrElse"
      ),
      notes = List(
        "PARAMETER NAMES: Use authenticatedUser, userOpt, accountOpt, bankOpt, transactionOpt, etc. (NOT user, account, bank)",
        "PROPERTY NAMES: Use camelCase - userId (NOT user_id), accountId (NOT account_id), emailAddress (NOT email_address)",
        "OPTION TYPES: Only authenticatedUser is guaranteed to exist. All others are Option types - check isDefined before using .get",
        "ATTRIBUTES: All attributes are Lists - use Scala collection methods like exists(), find(), filter()",
        "SAFE OPTION HANDLING: Use pattern matching: userOpt match { case Some(u) => u.userId == ... case None => false }",
        "RETURN TYPE: Rule must return Boolean - true = access granted, false = access denied",
        "AUTO-FETCHING: Objects are automatically fetched based on IDs passed to execute endpoint",
        "COMMON MISTAKE: Writing 'user.user_id' instead of 'userOpt.get.userId' or 'authenticatedUser.userId'"
      )
    )

    lazy val getAbacRuleSchema: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "abac-rules-schema" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          Future.successful(buildAbacRuleSchemaJson())
        }
    }

    // Inlined dynamic-entity backup helper (mirrors Lift's private backupDynamicEntity).
    private def backupDynamicEntityIo(
        entity: code.dynamicEntity.DynamicEntityT,
        backupName: String,
        dataRecords: net.liftweb.json.JsonAST.JArray
    ): Unit = {
      code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend
        .getByEntityName(entity.bankId, backupName).foreach { existingBackup =>
          code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend
            .getAll(entity.bankId, backupName, None, false)
            .foreach { record =>
              code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend.delete(
                entity.bankId, backupName, record.dynamicDataId.getOrElse(""), None, false)
            }
          code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend.delete(existingBackup)
        }
      val originalMetadata = net.liftweb.json.parse(entity.metadataJson).asInstanceOf[net.liftweb.json.JObject]
      val backupMetadata = net.liftweb.json.JObject(originalMetadata.obj.map {
        case net.liftweb.json.JField(name, value) if name == entity.entityName =>
          net.liftweb.json.JField(backupName, value)
        case other => other
      })
      val backupEntity = code.dynamicEntity.DynamicEntityCommons(
        entityName = backupName,
        metadataJson = net.liftweb.json.compactRender(backupMetadata),
        dynamicEntityId = None,
        userId = entity.userId,
        bankId = entity.bankId,
        hasPersonalEntity = entity.hasPersonalEntity)
      code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(backupEntity)
      val originalIdField = code.api.dynamic.entity.helper.DynamicEntityHelper.createEntityId(entity.entityName)
      val backupIdField = code.api.dynamic.entity.helper.DynamicEntityHelper.createEntityId(backupName)
      dataRecords.arr.foreach { record =>
        val recordObj = record.asInstanceOf[net.liftweb.json.JObject]
        val transformedFields = recordObj.obj.map {
          case net.liftweb.json.JField(name, _) if name == originalIdField =>
            net.liftweb.json.JField(backupIdField,
              net.liftweb.json.JString(java.util.UUID.randomUUID().toString))
          case other => other
        }
        code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend.save(
          entity.bankId, backupName, net.liftweb.json.JObject(transformedFields),
          Some(entity.userId), entity.hasPersonalEntity)
      }
    }

    private def computeBackupNameIo(bankId: Option[String], baseName: String): String = {
      val first = s"${baseName}_BAK"
      if (code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend
        .getByEntityName(bankId, first).isEmpty) first
      else {
        var suffix = 2
        var candidate = s"${baseName}_BAK$suffix"
        while (code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend
          .getByEntityName(bankId, candidate).isDefined) {
          suffix += 1
          candidate = s"${baseName}_BAK$suffix"
        }
        candidate
      }
    }

    private def backupDynamicEntityFut(
        bankIdOpt: Option[String],
        dynamicEntityId: String,
        cc: CallContext
    ): scala.concurrent.Future[code.api.v6_0_0.DynamicEntityDefinitionJsonV600] = {
      for {
        (entity, _) <- NewStyle.function.getDynamicEntityById(bankIdOpt, dynamicEntityId, Some(cc))
        canGetRole = code.api.dynamic.entity.helper.DynamicEntityInfo.canGetRole(entity.entityName, entity.bankId)
        _ <- NewStyle.function.hasEntitlement(entity.bankId.getOrElse(""), cc.userId, canGetRole, Some(cc))
        (box, _) <- NewStyle.function.invokeDynamicConnector(
          com.openbankproject.commons.model.enums.DynamicEntityOperation.GET_ALL,
          entity.entityName, None, None, entity.bankId, None, None, false, Some(cc))
        resultList <- Future {
          box.asInstanceOf[net.liftweb.common.Box[net.liftweb.json.JsonAST.JArray]]
            .openOrThrowException(s"$UnknownError ")
        }
        backupName = computeBackupNameIo(entity.bankId, entity.entityName)
        _ <- Future(backupDynamicEntityIo(entity, backupName, resultList))
        backupCanGetRole = code.api.dynamic.entity.helper.DynamicEntityInfo.canGetRole(backupName, entity.bankId)
        _ <- Future(code.entitlement.Entitlement.entitlement.vend.addEntitlement(
          entity.bankId.getOrElse(""), cc.userId, backupCanGetRole.toString()))
        backupEntity <- Future {
          code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend
            .getByEntityName(entity.bankId, backupName)
            .openOrThrowException("Backup entity not found after creation")
        }
      } yield {
        val commonsData: code.dynamicEntity.DynamicEntityCommons = backupEntity
        JSONFactory600.createMyDynamicEntitiesJson(List(commonsData)).dynamic_entities.head
      }
    }

    lazy val backupSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "system-dynamic-entities" / dynamicEntityId / "backup" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          backupDynamicEntityFut(None, dynamicEntityId, cc)
        }
    }

    lazy val backupBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "dynamic-entities" / dynamicEntityId / "backup" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          backupDynamicEntityFut(Some(bankIdStr), dynamicEntityId, cc)
        }
    }

    lazy val deleteSystemDynamicEntityCascade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "system-dynamic-entities" / "cascade" / dynamicEntityId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (entity, _) <- NewStyle.function.getDynamicEntityById(None, dynamicEntityId, Some(cc))
            _ <- Helper.booleanToFuture(CannotDeleteCascadePersonalEntity, cc = Some(cc)) {
              !entity.hasPersonalEntity
            }
            (box, _) <- NewStyle.function.invokeDynamicConnector(
              com.openbankproject.commons.model.enums.DynamicEntityOperation.GET_ALL,
              entity.entityName, None, None, entity.bankId, None, None, false, Some(cc))
            resultList <- Future {
              box.asInstanceOf[net.liftweb.common.Box[net.liftweb.json.JsonAST.JArray]]
                .openOrThrowException(s"$UnknownError ")
            }
            _ <- Future {
              if (!entity.entityName.startsWith("ZZ_BAK_"))
                backupDynamicEntityIo(entity, s"ZZ_BAK_${entity.entityName}", resultList)
            }
            _ <- Future.sequence {
              resultList.arr.map { record =>
                val idField = code.api.dynamic.entity.helper.DynamicEntityHelper.createEntityId(entity.entityName)
                val recordId = (record \ idField).asInstanceOf[net.liftweb.json.JString].s
                Future(code.DynamicData.DynamicDataProvider.connectorMethodProvider.vend.delete(
                  entity.bankId, entity.entityName, recordId, None, false))
              }
            }
            deleted <- NewStyle.function.deleteDynamicEntity(None, dynamicEntityId)
          } yield deleted
        }
    }

    private def initFinal9ResourceDocs(): Unit = {
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getUserByUserId), "GET",
        "/users/user-id/USER_ID", "Get User By User Id",
        """Get detailed user info by user_id (entitlements, agreements, recent metrics).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByUserId, UnknownError),
        apiTagUser :: Nil,
        Some(canGetAnyUser :: Nil),
        http4sPartialFunction = Some(getUserByUserId))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(directLoginEndpoint), "POST",
        "/my/logins/direct", "Direct Login",
        """Direct Login — exchange username/password/consumer_key for a token (via DirectLogin header).""",
        EmptyBody, EmptyBody,
        List(InvalidLoginCredentials, UsernameHasBeenLocked, UnknownError),
        apiTagUser :: Nil, None,
        http4sPartialFunction = Some(directLoginEndpoint))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(validateAbacRule), "POST",
        "/management/abac-rules/validate", "Validate ABAC Rule",
        """Compile-and-validate an ABAC rule body (dry run, no persistence).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
          AbacRuleCodeEmpty, UnknownError),
        apiTagABAC :: Nil,
        Some(canCreateAbacRule :: Nil),
        http4sPartialFunction = Some(validateAbacRule))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(executeAbacRule), "POST",
        "/management/abac-rules/ABAC_RULE_ID/execute", "Execute ABAC Rule",
        """Execute an ABAC rule with a specific context.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        apiTagABAC :: Nil,
        Some(canExecuteAbacRule :: Nil),
        http4sPartialFunction = Some(executeAbacRule))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(executeAbacPolicy), "POST",
        "/management/abac-policies/POLICY/execute", "Execute ABAC Policy",
        """Execute all ABAC rules in a policy (OR logic — first rule that returns true wins).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
        apiTagABAC :: Nil,
        Some(canExecuteAbacRule :: Nil),
        http4sPartialFunction = Some(executeAbacPolicy))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(getAbacRuleSchema), "GET",
        "/management/abac-rules-schema", "Get ABAC Rule Schema",
        """Static schema describing parameters, object types, examples and operators usable in ABAC rules.""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        apiTagABAC :: Nil,
        Some(canGetAbacRule :: Nil),
        http4sPartialFunction = Some(getAbacRuleSchema))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(backupSystemDynamicEntity), "POST",
        "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID/backup", "Backup System Level Dynamic Entity",
        """Create a _BAK backup of a system-level dynamic entity (definition + data).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        apiTagManageDynamicEntity :: apiTagApi :: Nil,
        Some(canBackupSystemDynamicEntity :: Nil),
        http4sPartialFunction = Some(backupSystemDynamicEntity))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(backupBankLevelDynamicEntity), "POST",
        "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/backup", "Backup Bank Level Dynamic Entity",
        """Create a _BAK backup of a bank-level dynamic entity (definition + data).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
        apiTagManageDynamicEntity :: apiTagApi :: Nil,
        Some(canBackupBankLevelDynamicEntity :: Nil),
        http4sPartialFunction = Some(backupBankLevelDynamicEntity))
      resourceDocs += ResourceDoc(
        null, implementedInApiVersion, nameOf(deleteSystemDynamicEntityCascade), "DELETE",
        "/management/system-dynamic-entities/cascade/DYNAMIC_ENTITY_ID", "Delete System Dynamic Entity Cascade",
        """Delete a system-level dynamic entity and all its data records (auto-backs-up to ZZ_BAK_ first).""",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UserHasMissingRoles,
          CannotDeleteCascadePersonalEntity, UnknownError),
        apiTagManageDynamicEntity :: apiTagApi :: Nil,
        Some(canDeleteCascadeSystemDynamicEntity :: Nil),
        http4sPartialFunction = Some(deleteSystemDynamicEntityCascade))
    }
    initFinal9ResourceDocs()

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customers/CUSTOMER_ID/investigation-report
    lazy val getCustomerInvestigationReport: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId / "investigation-report" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val qp = req.uri.query.params
          for {
            connectorName <- Future(code.api.Constant.CONNECTOR.openOrThrowException("connector not set"))
            _ <- Helper.booleanToFuture(InvestigationReportNotAvailable, cc = Some(cc)) {
              connectorName == "mapped"
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerId, Some(cc))
            _ <- Helper.booleanToFuture(
              s"Customer bank (${customer.bankId}) does not match BANK_ID (${bank.bankId.value})",
              400, Some(cc))(customer.bankId == bank.bankId.value)
            fromDate = qp.get("from_date").flatMap(APIUtil.parseDate)
              .getOrElse(new java.util.Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000))
            toDate = qp.get("to_date").flatMap(APIUtil.parseDate).getOrElse(new java.util.Date())
            limit = qp.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(500)
            accounts <- Future(code.investigation.DoobieInvestigationQueries.getAccountsForCustomer(customerId))
            transactions <- Future(code.investigation.DoobieInvestigationQueries.getTransactionsForAccounts(
              accounts.map(_.accountId), bank.bankId.value,
              new java.sql.Timestamp(fromDate.getTime),
              new java.sql.Timestamp(toDate.getTime), limit))
            customerLinks <- Future(code.investigation.DoobieInvestigationQueries.getCustomerLinks(customerId))
            customerRow = code.investigation.DoobieInvestigationQueries.CustomerRow(
              customerId = customer.customerId, legalName = customer.legalName,
              email = customer.email, mobileNumber = customer.mobileNumber,
              kycStatus = customer.kycStatus)
          } yield JSONFactory600.createInvestigationReportJson(
            customerRow, bank.bankId.value, accounts, transactions, customerLinks, fromDate, toDate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerInvestigationReport), "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/investigation-report", "Get Customer Investigation Report",
      """Generate an AML/fraud investigation report for the specified customer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvestigationReportNotAvailable, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetInvestigationReport :: Nil),
      http4sPartialFunction = Some(getCustomerInvestigationReport)
    )

    // ─── Phase 2: banks/.../customer-links bucket (5 endpoints) ───────────

    // Route: POST /obp/v6.0.0/banks/BANK_ID/customer-links (201)
    lazy val createCustomerLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customer-links" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostCustomerLinkJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostCustomerLinkJsonV600]
            }
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(postedData.customer_id, Some(cc))
            _ <- Helper.booleanToFuture(
              s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to match BANK_ID(${bank.bankId.value}) in URL",
              400, Some(cc))(customer.bankId == bank.bankId.value)
            (_, _) <- NewStyle.function.getBank(BankId(postedData.other_bank_id), Some(cc))
            (otherCustomer, _) <- NewStyle.function.getCustomerByCustomerId(postedData.other_customer_id, Some(cc))
            _ <- Helper.booleanToFuture(
              s"Bank of the other customer specified by the OTHER_CUSTOMER_ID(${otherCustomer.bankId}) has to match OTHER_BANK_ID(${postedData.other_bank_id})",
              400, Some(cc))(otherCustomer.bankId == postedData.other_bank_id)
            (customerLink, _) <- NewStyle.function.createCustomerLink(
              bank.bankId.value, postedData.customer_id, postedData.other_bank_id,
              postedData.other_customer_id, postedData.relationship_to, Some(cc))
          } yield JSONFactory600.createCustomerLinkJson(customerLink)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCustomerLink), "POST",
      "/banks/BANK_ID/customer-links", "Create Customer Link",
      """Create a link between two customers.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagCustomer :: Nil,
      Some(canCreateCustomerLink :: Nil),
      http4sPartialFunction = Some(createCustomerLink)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customer-links
    lazy val getCustomerLinksByBankId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customer-links" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (links, _) <- NewStyle.function.getCustomerLinksByBankId(bank.bankId.value, Some(cc))
          } yield JSONFactory600.createCustomerLinksJson(links)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerLinksByBankId), "GET",
      "/banks/BANK_ID/customer-links", "Get Customer Links by Bank",
      """Get all customer links for the specified bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomerLinks :: Nil),
      http4sPartialFunction = Some(getCustomerLinksByBankId)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID
    lazy val getCustomerLinkById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customer-links" / customerLinkId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (link, _) <- NewStyle.function.getCustomerLinkById(customerLinkId, Some(cc))
          } yield JSONFactory600.createCustomerLinkJson(link)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomerLinkById), "GET",
      "/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID", "Get Customer Link by Id",
      """Get a customer link by CUSTOMER_LINK_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canGetCustomerLink :: Nil),
      http4sPartialFunction = Some(getCustomerLinkById)
    )

    // Route: PUT /obp/v6.0.0/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID
    lazy val updateCustomerLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customer-links" / customerLinkId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PutCustomerLinkJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutCustomerLinkJsonV600]
            }
            (updated, _) <- NewStyle.function.updateCustomerLinkById(customerLinkId, postedData.relationship_to, Some(cc))
          } yield JSONFactory600.createCustomerLinkJson(updated)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateCustomerLink), "PUT",
      "/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID", "Update Customer Link",
      """Update the relationship of an existing customer link.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagCustomer :: Nil,
      Some(canUpdateCustomerLink :: Nil),
      http4sPartialFunction = Some(updateCustomerLink)
    )

    // Route: DELETE /obp/v6.0.0/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID
    lazy val deleteCustomerLink: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "customer-links" / customerLinkId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            _ <- NewStyle.function.deleteCustomerLinkById(customerLinkId, Some(cc))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteCustomerLink), "DELETE",
      "/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID", "Delete Customer Link",
      """Delete a customer link by CUSTOMER_LINK_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(canDeleteCustomerLink :: Nil),
      http4sPartialFunction = Some(deleteCustomerLink)
    )

    // Route: GET /obp/v6.0.0/management/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID
    lazy val getCustomViewById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            view <- ViewNewStyle.customView(
              ViewId(viewIdStr),
              BankIdAccountId(BankId(bankIdStr), com.openbankproject.commons.model.AccountId(accountIdStr)),
              Some(cc))
          } yield JSONFactory600.createViewJsonV600(view)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomViewById), "GET",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID", "Get Custom View by Id",
      """Get a single custom view by VIEW_ID for the given account.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagView :: Nil, None,
      http4sPartialFunction = Some(getCustomViewById)
    )

    // Route: POST /obp/v6.0.0/management/cache/namespaces/invalidate
    lazy val invalidateCacheNamespace: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "cache" / "namespaces" / "invalidate" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[InvalidateCacheNamespaceJsonV600]
            }
            namespaceId = postJson.namespace_id
            _ <- Helper.booleanToFuture(
              s"$InvalidCacheNamespaceId $namespaceId. Valid values: ${Constant.ALL_CACHE_NAMESPACES.mkString(", ")}",
              400, Some(cc))(Constant.ALL_CACHE_NAMESPACES.contains(namespaceId))
            oldVersion = Constant.getCacheNamespaceVersion(namespaceId)
            newVersionOpt = Constant.incrementCacheNamespaceVersion(namespaceId)
            _ <- Helper.booleanToFuture(
              s"Failed to increment cache namespace version for: $namespaceId",
              500, Some(cc))(newVersionOpt.isDefined)
          } yield InvalidatedCacheNamespaceJsonV600(
            namespace_id = namespaceId,
            old_version = oldVersion,
            new_version = newVersionOpt.get,
            status = "invalidated"
          )
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(invalidateCacheNamespace), "POST",
      "/management/cache/namespaces/invalidate", "Invalidate Cache Namespace",
      """Increment the version of the specified cache namespace, invalidating its keys.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(canInvalidateCacheNamespace :: Nil),
      http4sPartialFunction = Some(invalidateCacheNamespace)
    )

    // Route: GET /obp/v6.0.0/management/config-props
    lazy val getConfigProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "config-props" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            val props = APIUtil.getConfigPropsPairs.map { case (k, v) =>
              ConfigPropJsonV600(k, APIUtil.maskSensitivePropValue(k, v))
            }
            ListResult("config_props", props)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConfigProps), "GET",
      "/management/config-props", "Get Config Props",
      """Return all OBP config-file props (sensitive values masked).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getConfigProps)
    )

    // Route: GET /obp/v6.0.0/app-directory
    lazy val getAppDirectory: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "app-directory" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future {
            val entries = APIUtil.getAppDiscoveryPairs.map { case (k, v) => ConfigPropJsonV600(k, v) }
            ListResult("app_directory", entries)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAppDirectory), "GET",
      "/app-directory", "Get App Directory",
      """Return apps registered in this OBP instance's discovery directory.""",
      EmptyBody, EmptyBody,
      List(UnknownError),
      apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getAppDirectory)
    )

    // Route: GET /obp/v6.0.0/management/custom-views
    lazy val getCustomViews: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "custom-views" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future(JSONFactory600.createViewsJsonV600(code.views.system.ViewDefinition.getCustomViews()))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomViews), "GET",
      "/management/custom-views", "Get Custom Views",
      """Get all custom views defined in this instance.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagView :: Nil,
      Some(canGetCustomViews :: Nil),
      http4sPartialFunction = Some(getCustomViews)
    )

    // Route: GET /obp/v6.0.0/management/roles-with-entitlement-counts
    lazy val getRolesWithEntitlementCountsAtAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "roles-with-entitlement-counts" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          val allRoles = code.api.util.ApiRole.availableRoles.sorted
          Future.sequence(allRoles.map { role =>
            Entitlement.entitlement.vend.getEntitlementsByRoleFuture(role).map { box =>
              (role, box.map(_.length).getOrElse(0))
            }
          }).map(JSONFactory600.createRolesWithEntitlementCountsJson)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getRolesWithEntitlementCountsAtAllBanks), "GET",
      "/management/roles-with-entitlement-counts", "Get Roles with Entitlement Counts",
      """List all available roles along with how many entitlements each has.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagRole :: Nil,
      Some(canGetRolesWithEntitlementCountsAtAllBanks :: Nil),
      http4sPartialFunction = Some(getRolesWithEntitlementCountsAtAllBanks)
    )

    // ─── Phase 2: 5 small single-endpoint buckets ─────────────────────────

    // Route: GET /obp/v6.0.0/features
    lazy val getFeatures: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "features" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(FeaturesJsonV600(
            allow_public_views = APIUtil.getPropsAsBoolValue("allow_public_views", false),
            allow_abac_account_access = APIUtil.getPropsAsBoolValue("allow_abac_account_access", false),
            allow_account_firehose = APIUtil.getPropsAsBoolValue("allow_account_firehose", false),
            allow_customer_firehose = APIUtil.getPropsAsBoolValue("allow_customer_firehose", false),
            allow_direct_login = APIUtil.getPropsAsBoolValue("allow_direct_login", true),
            allow_gateway_login = APIUtil.getPropsAsBoolValue("allow_gateway_login", false),
            allow_oauth2_login = APIUtil.getPropsAsBoolValue("allow_oauth2_login", true),
            allow_dauth = APIUtil.getPropsAsBoolValue("allow_dauth", false),
            allow_sandbox_account_creation = APIUtil.getPropsAsBoolValue("allow_sandbox_account_creation", false),
            allow_sandbox_data_import = APIUtil.getPropsAsBoolValue("allow_sandbox_data_import", false),
            allow_account_deletion = APIUtil.getPropsAsBoolValue("allow_account_deletion", false),
            allow_just_in_time_entitlements = APIUtil.getPropsAsBoolValue("create_just_in_time_entitlements", false)
          ))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getFeatures), "GET",
      "/features", "Get Features",
      """Returns the enabled features for this OBP instance.""",
      EmptyBody, EmptyBody,
      List(UnknownError),
      apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getFeatures)
    )

    // Route: GET /obp/v6.0.0/providers
    lazy val getProviders: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "providers" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future(code.model.dataAccess.ResourceUser.getDistinctProviders)
            .map(JSONFactory600.createProvidersJson)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getProviders), "GET",
      "/providers", "Get Providers",
      """Get the distinct list of auth providers that have been used to create users.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagUser :: Nil, None,
      http4sPartialFunction = Some(getProviders)
    )

    // Route: GET /obp/v6.0.0/consumers/current
    lazy val getCurrentConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consumers" / "current" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            consumer <- Future(cc.consumer match {
              case Full(c) => Full(c)
              case _ => Empty
            }).map(unboxFullOrFail(_, Some(cc), InvalidConsumerCredentials, 401))
            counters <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
            date = new java.util.Date()
            (activeRateLimit, ids) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumer.consumerId.get, date)
          } yield CurrentConsumerJsonV600(
            consumer.name.get, consumer.appType.get, consumer.description.get, consumer.consumerId.get,
            JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(activeRateLimit, ids, date),
            JSONFactory600.createRedisCallCountersJson(counters))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCurrentConsumer), "GET",
      "/consumers/current", "Get Current Consumer",
      """Get the Consumer used to make this request, including active rate limits and call counters.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidConsumerCredentials, UnknownError),
      apiTagConsumer :: Nil,
      Some(canGetCurrentConsumer :: Nil),
      http4sPartialFunction = Some(getCurrentConsumer)
    )

    // Route: GET /obp/v6.0.0/api/popular-endpoints
    lazy val getPopularApis: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "popular-endpoints" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (qp, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            withLimit = qp ++ List(code.api.util.OBPLimit(50))
            topApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(withLimit)
              .map(unboxFullOrFail(_, Some(cc), UnknownError))
          } yield {
            val lookupMap = APIUtil.getAllResourceDocs.map(d => d.partialFunctionName -> d.operationId).toMap
            val operationIds = topApis.flatMap(api =>
              lookupMap.get(api.ImplementedByPartialFunction).orElse(
                scala.util.Try(Some(APIUtil.buildOperationId(
                  ApiVersionUtils.valueOf(api.implementedInVersion), api.ImplementedByPartialFunction))).getOrElse(None)))
            PopularApisJsonV600(operationIds)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getPopularApis), "GET",
      "/api/popular-endpoints", "Get Popular Endpoints",
      """Returns the operation IDs of the 50 most popular endpoints by usage.""",
      EmptyBody, EmptyBody,
      List(UnknownError),
      apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getPopularApis)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/account-directory
    lazy val getAccountDirectory: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "account-directory" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val allowedParams = List("limit", "offset", "sort_direction")
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- NewStyle.function.createObpParams(httpParams, allowedParams, Some(cc))
            (accounts, _) <- NewStyle.function.getAccountDirectory(bank.bankId, obpQueryParams, Some(cc))
          } yield {
            val viewsPerAccount: Map[BankIdAccountId, List[String]] = accounts.map { a =>
              val biaId = BankIdAccountId(BankId(a.bankId), com.openbankproject.commons.model.AccountId(a.id))
              biaId -> Views.views.vend.availableViewsForAccount(biaId).map(_.viewId.value)
            }.toMap
            JSONFactory600.createAccountDirectoryJsonV600(accounts, viewsPerAccount)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAccountDirectory), "GET",
      "/banks/BANK_ID/account-directory", "Get Account Directory",
      """Get the list of accounts in the bank's account directory (paginated, sortable).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagAccount :: Nil,
      Some(canGetAccountDirectoryAtOneBank :: Nil),
      http4sPartialFunction = Some(getAccountDirectory)
    )

    // ─── Phase 2: management/groups bucket (6 endpoints) ──────────────────
    // Doc roles are kept None and the bank-scoped vs system-scoped role check
    // is done inline (it depends on whether bank_id is supplied in the body or
    // on the existing group).

    private def groupRoleCheck(bankId: Option[String], userId: String,
                                bankRole: code.api.util.ApiRole,
                                allBanksRole: code.api.util.ApiRole,
                                cc: CallContext): Future[Any] = bankId match {
      case Some(b) if b.nonEmpty =>
        NewStyle.function.hasAtLeastOneEntitlement(b, userId, bankRole :: allBanksRole :: Nil, Some(cc))
      case _ =>
        NewStyle.function.hasEntitlement("", userId, allBanksRole, Some(cc))
    }

    private def groupToJsonV600(group: GroupT): GroupJsonV600 =
      GroupJsonV600(
        group_id = group.groupId, bank_id = group.bankId,
        group_name = group.groupName, group_description = group.groupDescription,
        list_of_roles = group.listOfRoles, is_enabled = group.isEnabled)

    // Route: POST /obp/v6.0.0/management/groups (201)
    lazy val createGroup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "groups" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostGroupJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostGroupJsonV600]
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat group_name cannot be empty", cc = Some(cc)) {
              postJson.group_name.nonEmpty
            }
            _ <- groupRoleCheck(postJson.bank_id, user.userId, canCreateGroupAtOneBank, canCreateGroupAtAllBanks, cc)
            group <- Future(code.group.GroupTrait.group.vend.createGroup(
              postJson.bank_id.filter(_.nonEmpty), postJson.group_name,
              postJson.group_description, postJson.list_of_roles, postJson.is_enabled
            )).map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Cannot create group", 400))
          } yield groupToJsonV600(group)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createGroup), "POST",
      "/management/groups", "Create Group",
      """Create a new Group.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagGroup :: Nil, None,
      http4sPartialFunction = Some(createGroup)
    )

    // Route: GET /obp/v6.0.0/management/groups/GROUP_ID
    lazy val getGroup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "groups" / groupId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            group <- Future(code.group.GroupTrait.group.vend.getGroup(groupId))
              .map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Group not found", 404))
            _ <- groupRoleCheck(group.bankId, user.userId, canGetGroupsAtOneBank, canGetGroupsAtAllBanks, cc)
          } yield groupToJsonV600(group)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getGroup), "GET",
      "/management/groups/GROUP_ID", "Get Group",
      """Get a Group by ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagGroup :: Nil, None,
      http4sPartialFunction = Some(getGroup)
    )

    // Route: GET /obp/v6.0.0/management/groups
    lazy val getGroups: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "groups" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val bankIdParam = req.uri.query.params.get("bank_id")
          val bankIdFilter = bankIdParam match {
            case Some("null") | Some("") => None
            case Some(id) => Some(id)
            case None => None
          }
          for {
            _ <- groupRoleCheck(bankIdFilter, user.userId, canGetGroupsAtOneBank, canGetGroupsAtAllBanks, cc)
            groups <- (bankIdFilter match {
              case Some(b) => code.group.GroupTrait.group.vend.getGroupsByBankId(Some(b))
              case None if bankIdParam.isDefined => code.group.GroupTrait.group.vend.getGroupsByBankId(None)
              case None => code.group.GroupTrait.group.vend.getAllGroups()
            }).map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Cannot get groups", 400))
          } yield GroupsJsonV600(groups.map(groupToJsonV600))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getGroups), "GET",
      "/management/groups", "Get Groups",
      """Get all Groups (optional ?bank_id= filter).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagGroup :: Nil, None,
      http4sPartialFunction = Some(getGroups)
    )

    // Route: PUT /obp/v6.0.0/management/groups/GROUP_ID
    lazy val updateGroup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "groups" / groupId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PutGroupJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutGroupJsonV600]
            }
            existing <- Future(code.group.GroupTrait.group.vend.getGroup(groupId))
              .map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Group not found", 404))
            _ <- groupRoleCheck(existing.bankId, user.userId, canUpdateGroupAtOneBank, canUpdateGroupAtAllBanks, cc)
            updated <- Future(code.group.GroupTrait.group.vend.updateGroup(
              groupId, putJson.group_name, putJson.group_description,
              putJson.list_of_roles, putJson.is_enabled
            )).map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Cannot update group", 400))
          } yield groupToJsonV600(updated)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateGroup), "PUT",
      "/management/groups/GROUP_ID", "Update Group",
      """Update an existing Group.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagGroup :: Nil, None,
      http4sPartialFunction = Some(updateGroup)
    )

    // Route: DELETE /obp/v6.0.0/management/groups/GROUP_ID
    lazy val deleteGroup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "groups" / groupId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            existing <- Future(code.group.GroupTrait.group.vend.getGroup(groupId))
              .map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Group not found", 404))
            _ <- groupRoleCheck(existing.bankId, user.userId, canDeleteGroupAtOneBank, canDeleteGroupAtAllBanks, cc)
            _ <- Future(code.group.GroupTrait.group.vend.deleteGroup(groupId))
              .map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Cannot delete group", 400))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteGroup), "DELETE",
      "/management/groups/GROUP_ID", "Delete Group",
      """Delete a Group.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagGroup :: Nil, None,
      http4sPartialFunction = Some(deleteGroup)
    )

    // Route: GET /obp/v6.0.0/management/groups/GROUP_ID/entitlements
    lazy val getGroupEntitlements: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "groups" / groupId / "entitlements" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            _ <- Future(code.group.GroupTrait.group.vend.getGroup(groupId))
              .map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Group not found", 404))
            groupEntitlements <- Entitlement.entitlement.vend.getEntitlementsByGroupId(groupId)
              .map(x => unboxFullOrFail(x, Some(cc), s"$UnknownError Cannot get entitlements", 400))
            withUsernames <- Future.sequence(groupEntitlements.map { ent =>
              Users.users.vend.getUserByUserIdFuture(ent.userId).map { userBox =>
                GroupEntitlementJsonV600(
                  entitlement_id = ent.entitlementId, role_name = ent.roleName,
                  bank_id = ent.bankId, user_id = ent.userId,
                  username = userBox.map(_.name).getOrElse(""),
                  group_id = ent.groupId, process = ent.process)
              }
            })
          } yield GroupEntitlementsJsonV600(withUsernames)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getGroupEntitlements), "GET",
      "/management/groups/GROUP_ID/entitlements", "Get Group Entitlements",
      """Get all entitlements granted to the specified group.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagGroup :: Nil,
      Some(canGetEntitlementsForAnyBank :: Nil),
      http4sPartialFunction = Some(getGroupEntitlements)
    )

    // ─── Phase 2: management/abac-rules bucket (6 of 8) ───────────────────
    // executeAbacRule + validateAbacRule deferred — complex error
    // classification + rule-engine integration warrants its own batch.

    // Route: POST /obp/v6.0.0/management/abac-rules (201)
    lazy val createAbacRule: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "abac-rules" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            createJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateAbacRuleJsonV600]
            }
            _ <- Helper.booleanToFuture("Rule name must not be empty", cc = Some(cc)) { createJson.rule_name.nonEmpty }
            _ <- Helper.booleanToFuture("Rule code must not be empty", cc = Some(cc)) { createJson.rule_code.nonEmpty }
            _ <- AbacRuleEngine.validateRuleCodeAsync(createJson.rule_code)
              .map(unboxFullOrFail(_, Some(cc), "Invalid ABAC rule code", 400))
            rule <- Future(MappedAbacRuleProvider.createAbacRule(
              ruleName = createJson.rule_name, ruleCode = createJson.rule_code,
              description = createJson.description, policy = createJson.policy,
              isActive = createJson.is_active, createdBy = user.userId
            )).map(unboxFullOrFail(_, Some(cc), "Could not create ABAC rule", 400))
          } yield createAbacRuleJsonV600(rule)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createAbacRule), "POST",
      "/management/abac-rules", "Create ABAC Rule",
      """Create a new ABAC rule.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagABAC :: Nil,
      Some(canCreateAbacRule :: Nil),
      http4sPartialFunction = Some(createAbacRule)
    )

    // Route: GET /obp/v6.0.0/management/abac-rules/ABAC_RULE_ID
    lazy val getAbacRule: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "abac-rules" / ruleId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            rule <- Future(MappedAbacRuleProvider.getAbacRuleById(ruleId))
              .map(unboxFullOrFail(_, Some(cc), s"ABAC Rule not found with ID: $ruleId", 404))
          } yield createAbacRuleJsonV600(rule)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAbacRule), "GET",
      "/management/abac-rules/ABAC_RULE_ID", "Get ABAC Rule",
      """Get a single ABAC rule by ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagABAC :: Nil,
      Some(canGetAbacRule :: Nil),
      http4sPartialFunction = Some(getAbacRule)
    )

    // Route: GET /obp/v6.0.0/management/abac-rules
    lazy val getAbacRules: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "abac-rules" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future(createAbacRulesJsonV600(MappedAbacRuleProvider.getAllAbacRules()))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAbacRules), "GET",
      "/management/abac-rules", "Get ABAC Rules",
      """Get all ABAC rules.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagABAC :: Nil,
      Some(canGetAbacRule :: Nil),
      http4sPartialFunction = Some(getAbacRules)
    )

    // Route: GET /obp/v6.0.0/management/abac-rules/policy/POLICY
    lazy val getAbacRulesByPolicy: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "abac-rules" / "policy" / policy =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future(createAbacRulesJsonV600(MappedAbacRuleProvider.getAbacRulesByPolicy(policy)))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAbacRulesByPolicy), "GET",
      "/management/abac-rules/policy/POLICY", "Get ABAC Rules by Policy",
      """Get all ABAC rules for a given policy.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagABAC :: Nil,
      Some(canGetAbacRule :: Nil),
      http4sPartialFunction = Some(getAbacRulesByPolicy)
    )

    // Route: PUT /obp/v6.0.0/management/abac-rules/ABAC_RULE_ID
    lazy val updateAbacRule: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "abac-rules" / ruleId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            updateJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateAbacRuleJsonV600]
            }
            _ <- AbacRuleEngine.validateRuleCodeAsync(updateJson.rule_code)
              .map(unboxFullOrFail(_, Some(cc), "Invalid ABAC rule code", 400))
            rule <- Future(MappedAbacRuleProvider.updateAbacRule(
              ruleId = ruleId, ruleName = updateJson.rule_name,
              ruleCode = updateJson.rule_code, description = updateJson.description,
              policy = updateJson.policy, isActive = updateJson.is_active,
              updatedBy = user.userId
            )).map(unboxFullOrFail(_, Some(cc), s"Could not update ABAC rule with ID: $ruleId", 400))
            _ <- Future(AbacRuleEngine.clearRuleFromCache(ruleId))
          } yield createAbacRuleJsonV600(rule)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateAbacRule), "PUT",
      "/management/abac-rules/ABAC_RULE_ID", "Update ABAC Rule",
      """Update an existing ABAC rule.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagABAC :: Nil,
      Some(canUpdateAbacRule :: Nil),
      http4sPartialFunction = Some(updateAbacRule)
    )

    // Route: DELETE /obp/v6.0.0/management/abac-rules/ABAC_RULE_ID
    lazy val deleteAbacRule: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "abac-rules" / ruleId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            _ <- Future(MappedAbacRuleProvider.deleteAbacRule(ruleId))
              .map(unboxFullOrFail(_, Some(cc), s"Could not delete ABAC rule with ID: $ruleId", 400))
            _ <- Future(AbacRuleEngine.clearRuleFromCache(ruleId))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteAbacRule), "DELETE",
      "/management/abac-rules/ABAC_RULE_ID", "Delete ABAC Rule",
      """Delete an ABAC rule.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagABAC :: Nil,
      Some(canDeleteAbacRule :: Nil),
      http4sPartialFunction = Some(deleteAbacRule)
    )


    // ─── Phase 2: my/personal-data-fields bucket (5 endpoints) ────────────
    // Auth-only; the v6 Lift docs declare `Some(List())` empty role list.

    private val personalDataTypeErrorMsg =
      s"$InvalidJsonFormat The `type` field can only accept: ${UserAttributeType.DOUBLE}, ${UserAttributeType.STRING}, ${UserAttributeType.INTEGER}, ${UserAttributeType.DATE_WITH_DAY}"

    // Route: POST /obp/v6.0.0/my/personal-data-fields (201)
    lazy val createPersonalDataField: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "personal-data-fields" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UserAttributeJsonV510]
            }
            userAttributeType <- NewStyle.function.tryons(personalDataTypeErrorMsg, 400, Some(cc)) {
              UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, None, postedData.name, userAttributeType, postedData.value, true, Some(cc))
          } yield JSONFactory510.createUserAttributeJson(userAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createPersonalDataField), "POST",
      "/my/personal-data-fields", "Create Personal Data Field",
      """Create a personal data field for the logged-in user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      apiTagUser :: Nil,
      Some(Nil),
      http4sPartialFunction = Some(createPersonalDataField)
    )

    // Route: GET /obp/v6.0.0/my/personal-data-fields
    lazy val getPersonalDataFields: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "personal-data-fields" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getPersonalUserAttributes(user.userId, Some(cc))
          } yield UserAttributesResponseJsonV510(attributes.map(JSONFactory510.createUserAttributeJson))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getPersonalDataFields), "GET",
      "/my/personal-data-fields", "Get Personal Data Fields",
      """Get all personal data fields for the logged-in user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagUser :: Nil,
      Some(Nil),
      http4sPartialFunction = Some(getPersonalDataFields)
    )

    // Route: GET /obp/v6.0.0/my/personal-data-fields/USER_ATTRIBUTE_ID
    lazy val getPersonalDataFieldById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "personal-data-fields" / userAttributeId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (attributes, _) <- NewStyle.function.getPersonalUserAttributes(user.userId, Some(cc))
            attribute <- Future(attributes.find(_.userAttributeId == userAttributeId))
              .map(unboxFullOrFail(_, Some(cc), UserAttributeNotFound, 404))
          } yield JSONFactory510.createUserAttributeJson(attribute)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getPersonalDataFieldById), "GET",
      "/my/personal-data-fields/USER_ATTRIBUTE_ID", "Get Personal Data Field By Id",
      """Get a personal data field by USER_ATTRIBUTE_ID for the logged-in user.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserAttributeNotFound, UnknownError),
      apiTagUser :: Nil,
      Some(Nil),
      http4sPartialFunction = Some(getPersonalDataFieldById)
    )

    // Route: PUT /obp/v6.0.0/my/personal-data-fields/USER_ATTRIBUTE_ID
    lazy val updatePersonalDataField: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "my" / "personal-data-fields" / userAttributeId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UserAttributeJsonV510]
            }
            userAttributeType <- NewStyle.function.tryons(personalDataTypeErrorMsg, 400, Some(cc)) {
              UserAttributeType.withName(postedData.`type`)
            }
            (attributes, _) <- NewStyle.function.getPersonalUserAttributes(user.userId, Some(cc))
            _ <- Future(attributes.find(_.userAttributeId == userAttributeId))
              .map(unboxFullOrFail(_, Some(cc), UserAttributeNotFound, 404))
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, Some(userAttributeId), postedData.name, userAttributeType, postedData.value, true, Some(cc))
          } yield JSONFactory510.createUserAttributeJson(userAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updatePersonalDataField), "PUT",
      "/my/personal-data-fields/USER_ATTRIBUTE_ID", "Update Personal Data Field",
      """Update a personal data field by USER_ATTRIBUTE_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UserAttributeNotFound, UnknownError),
      apiTagUser :: Nil,
      Some(Nil),
      http4sPartialFunction = Some(updatePersonalDataField)
    )

    // Route: DELETE /obp/v6.0.0/my/personal-data-fields/USER_ATTRIBUTE_ID
    lazy val deletePersonalDataField: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "personal-data-fields" / userAttributeId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            (attributes, _) <- NewStyle.function.getPersonalUserAttributes(user.userId, Some(cc))
            _ <- Future(attributes.find(_.userAttributeId == userAttributeId))
              .map(unboxFullOrFail(_, Some(cc), UserAttributeNotFound, 404))
            _ <- BankConnector.connector.vend.deleteUserAttribute(userAttributeId, Some(cc))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deletePersonalDataField), "DELETE",
      "/my/personal-data-fields/USER_ATTRIBUTE_ID", "Delete Personal Data Field",
      """Delete a personal data field by USER_ATTRIBUTE_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserAttributeNotFound, UnknownError),
      apiTagUser :: Nil,
      Some(Nil),
      http4sPartialFunction = Some(deletePersonalDataField)
    )

    // ─── Phase 2: management/consumers bucket (6 endpoints) ───────────────

    // Route: GET /obp/v6.0.0/management/consumers/CONSUMER_ID/call-counters
    lazy val getConsumerCallCounters: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId / "call-counters" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            counters <- Future(RateLimitingUtil.consumerRateLimitState(consumerId).toList)
          } yield JSONFactory600.createRedisCallCountersJson(counters)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConsumerCallCounters), "GET",
      "/management/consumers/CONSUMER_ID/call-counters", "Get Consumer Call Counters",
      """Get the current call counters (Redis-backed) for a specific consumer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConsumer :: Nil,
      Some(canGetRateLimits :: Nil),
      http4sPartialFunction = Some(getConsumerCallCounters)
    )

    // Route: POST /obp/v6.0.0/management/consumers/CONSUMER_ID/consumer/rate-limits (201)
    lazy val createCallLimits: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "rate-limits" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the CallLimitPostJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CallLimitPostJsonV600]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            rateLimitingBox <- RateLimitingDI.rateLimiting.vend.createConsumerCallLimits(
              consumerId, postJson.from_date, postJson.to_date,
              postJson.api_version, postJson.api_name, postJson.bank_id,
              Some(postJson.per_second_call_limit), Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit), Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit), Some(postJson.per_month_call_limit))
            rateLimiting <- Future(unboxFullOrFail(rateLimitingBox, Some(cc), UnknownError, 400))
          } yield JSONFactory600.createCallLimitJsonV600(rateLimiting)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createCallLimits), "POST",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits", "Create Rate Limits for a Consumer",
      """Create a rate-limit configuration for a Consumer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagConsumer :: Nil,
      Some(canCreateRateLimits :: Nil),
      http4sPartialFunction = Some(createCallLimits)
    )

    // Route: PUT /obp/v6.0.0/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID
    lazy val updateRateLimits: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "rate-limits" / rateLimitingId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the CallLimitPostJsonV400", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CallLimitPostJsonV400]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            _ <- RateLimitingDI.rateLimiting.vend.updateConsumerCallLimits(
              rateLimitingId, postJson.from_date, postJson.to_date,
              postJson.api_version, postJson.api_name, postJson.bank_id,
              Some(postJson.per_second_call_limit), Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit), Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit), Some(postJson.per_month_call_limit))
            date = new java.util.Date()
            (activeRateLimit, ids) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumerId, date)
          } yield JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(activeRateLimit, ids, date)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateRateLimits), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID", "Update Rate Limits for a Consumer",
      """Update an existing rate-limit configuration for a Consumer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagConsumer :: Nil,
      Some(canUpdateRateLimits :: Nil),
      http4sPartialFunction = Some(updateRateLimits)
    )

    // Route: DELETE /obp/v6.0.0/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID
    lazy val deleteCallLimits: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "rate-limits" / rateLimitingId =>
        EndpointHelpers.executeDelete(req) { cc =>
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            _ <- RateLimitingDI.rateLimiting.vend.deleteByRateLimitingId(rateLimitingId)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteCallLimits), "DELETE",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID", "Delete Rate Limits for a Consumer",
      """Delete a rate-limit configuration.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConsumer :: Nil,
      Some(canDeleteRateLimits :: Nil),
      http4sPartialFunction = Some(deleteCallLimits)
    )

    // Route: GET /obp/v6.0.0/management/consumers/CONSUMER_ID/active-rate-limits
    lazy val getActiveRateLimitsNow: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId / "active-rate-limits" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            date = new java.util.Date()
            (rateLimit, ids) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumerId, date)
          } yield JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(rateLimit, ids, date)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getActiveRateLimitsNow), "GET",
      "/management/consumers/CONSUMER_ID/active-rate-limits", "Get Active Rate Limits (now)",
      """Get the currently active rate limits for a Consumer.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConsumer :: Nil,
      Some(canGetRateLimits :: Nil),
      http4sPartialFunction = Some(getActiveRateLimitsNow)
    )

    // Route: GET /obp/v6.0.0/management/consumers/CONSUMER_ID/active-rate-limits/DATE_WITH_HOUR
    lazy val getActiveRateLimitsAtDate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId / "active-rate-limits" / dateWithHourString =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            date <- NewStyle.function.tryons(
              s"$InvalidDateFormat Current date format is: $dateWithHourString. Please use this format: YYYY-MM-DD-HH in UTC",
              400, Some(cc)) {
              val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
              val ldt = java.time.LocalDateTime.parse(dateWithHourString, fmt)
              java.util.Date.from(ldt.atZone(java.time.ZoneOffset.UTC).toInstant())
            }
            (rateLimit, ids) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumerId, date)
          } yield JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(rateLimit, ids, date)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getActiveRateLimitsAtDate), "GET",
      "/management/consumers/CONSUMER_ID/active-rate-limits/DATE_WITH_HOUR", "Get Active Rate Limits at Date",
      """Get the active rate limits for a Consumer at the specified UTC hour (YYYY-MM-DD-HH).""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidDateFormat, UnknownError),
      apiTagConsumer :: Nil,
      Some(canGetRateLimits :: Nil),
      http4sPartialFunction = Some(getActiveRateLimitsAtDate)
    )

    // ─── Phase 2: management/api-collections bucket (4 endpoints) ─────────

    // Route: POST /obp/v6.0.0/management/api-collections/featured (201)
    lazy val createFeaturedApiCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "api-collections" / "featured" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostFeaturedApiCollectionJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostFeaturedApiCollectionJsonV600]
            }
            (apiCollection, _) <- NewStyle.function.getApiCollectionById(postJson.api_collection_id, Some(cc))
            _ <- Helper.booleanToFuture(s"$ApiCollectionNotFound The API Collection must be sharable to be featured.", cc = Some(cc)) {
              apiCollection.isSharable
            }
            _ <- NewStyle.function.checkFeaturedApiCollectionDoesNotExist(postJson.api_collection_id, Some(cc))
            (featured, _) <- NewStyle.function.createFeaturedApiCollection(
              postJson.api_collection_id, postJson.sort_order, Some(cc))
          } yield JSONFactory600.createFeaturedApiCollectionJsonV600(featured)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createFeaturedApiCollection), "POST",
      "/management/api-collections/featured", "Create Featured Api Collection",
      """Mark an API collection as featured.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, ApiCollectionNotFound, UnknownError),
      apiTagApiCollection :: Nil,
      Some(canManageFeaturedApiCollections :: Nil),
      http4sPartialFunction = Some(createFeaturedApiCollection)
    )

    // Route: GET /obp/v6.0.0/management/api-collections/featured
    lazy val getFeaturedApiCollectionsAdmin: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "api-collections" / "featured" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            (featured, _) <- NewStyle.function.getAllFeaturedApiCollectionsAdmin(Some(cc))
          } yield JSONFactory600.createFeaturedApiCollectionsJsonV600(featured)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getFeaturedApiCollectionsAdmin), "GET",
      "/management/api-collections/featured", "Get Featured Api Collections (Admin)",
      """Get all featured API collections.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagApiCollection :: Nil,
      Some(canManageFeaturedApiCollections :: Nil),
      http4sPartialFunction = Some(getFeaturedApiCollectionsAdmin)
    )

    // Route: PUT /obp/v6.0.0/management/api-collections/featured/API_COLLECTION_ID
    lazy val updateFeaturedApiCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "api-collections" / "featured" / apiCollectionId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PutFeaturedApiCollectionJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PutFeaturedApiCollectionJsonV600]
            }
            (updated, _) <- NewStyle.function.updateFeaturedApiCollection(
              apiCollectionId, putJson.sort_order, Some(cc))
          } yield JSONFactory600.createFeaturedApiCollectionJsonV600(updated)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateFeaturedApiCollection), "PUT",
      "/management/api-collections/featured/API_COLLECTION_ID", "Update Featured Api Collection",
      """Update the sort order of a featured API collection.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagApiCollection :: Nil,
      Some(canManageFeaturedApiCollections :: Nil),
      http4sPartialFunction = Some(updateFeaturedApiCollection)
    )

    // Route: DELETE /obp/v6.0.0/management/api-collections/featured/API_COLLECTION_ID
    lazy val deleteFeaturedApiCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "api-collections" / "featured" / apiCollectionId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            _ <- NewStyle.function.deleteFeaturedApiCollectionByApiCollectionId(apiCollectionId, Some(cc))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteFeaturedApiCollection), "DELETE",
      "/management/api-collections/featured/API_COLLECTION_ID", "Delete Featured Api Collection",
      """Remove a featured API collection.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagApiCollection :: Nil,
      Some(canManageFeaturedApiCollections :: Nil),
      http4sPartialFunction = Some(deleteFeaturedApiCollection)
    )

    // ─── Phase 2: api-products bucket (9 endpoints) ───────────────────────
    // All endpoints always require auth + role; the v6 Lift conditional
    // public-access path (getApiProductsIsPublic) is simplified — public
    // gating would be a Phase 3 follow-up if needed.

    // Route: POST /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE (201)
    lazy val createApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "api-products" / apiProductCode =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostPutApiProductJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostPutApiProductJsonV600]
            }
            (apiProduct, _) <- NewStyle.function.createOrUpdateApiProduct(
              bank.bankId.value, apiProductCode,
              postJson.parent_api_product_code.getOrElse(""),
              postJson.name, postJson.category.getOrElse(""),
              postJson.more_info_url.getOrElse(""), postJson.terms_and_conditions_url.getOrElse(""),
              postJson.description.getOrElse(""), postJson.collection_id.getOrElse(""),
              postJson.monthly_subscription_currency.getOrElse(""), postJson.monthly_subscription_amount.getOrElse(""),
              postJson.per_second_call_limit.getOrElse(-1L), postJson.per_minute_call_limit.getOrElse(-1L),
              postJson.per_hour_call_limit.getOrElse(-1L), postJson.per_day_call_limit.getOrElse(-1L),
              postJson.per_week_call_limit.getOrElse(-1L), postJson.per_month_call_limit.getOrElse(-1L),
              postJson.tags.getOrElse(Nil), Some(cc)
            )
          } yield JSONFactory600.createApiProductJsonV600(apiProduct, None)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createApiProduct), "POST",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE", "Create Api Product",
      """Create an Api Product for the Bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagApi :: apiTagApiProduct :: Nil,
      Some(canCreateApiProduct :: Nil),
      http4sPartialFunction = Some(createApiProduct)
    )

    // Route: PUT /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE (201 — Lift returns 201)
    lazy val createOrUpdateApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "api-products" / apiProductCode =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          for {
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostPutApiProductJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[PostPutApiProductJsonV600]
            }
            (apiProduct, _) <- NewStyle.function.createOrUpdateApiProduct(
              bank.bankId.value, apiProductCode,
              postJson.parent_api_product_code.getOrElse(""),
              postJson.name, postJson.category.getOrElse(""),
              postJson.more_info_url.getOrElse(""), postJson.terms_and_conditions_url.getOrElse(""),
              postJson.description.getOrElse(""), postJson.collection_id.getOrElse(""),
              postJson.monthly_subscription_currency.getOrElse(""), postJson.monthly_subscription_amount.getOrElse(""),
              postJson.per_second_call_limit.getOrElse(-1L), postJson.per_minute_call_limit.getOrElse(-1L),
              postJson.per_hour_call_limit.getOrElse(-1L), postJson.per_day_call_limit.getOrElse(-1L),
              postJson.per_week_call_limit.getOrElse(-1L), postJson.per_month_call_limit.getOrElse(-1L),
              postJson.tags.getOrElse(Nil), Some(cc)
            )
          } yield JSONFactory600.createApiProductJsonV600(apiProduct, None)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createOrUpdateApiProduct), "PUT",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE", "Create or Update Api Product",
      """Create or update an Api Product for the Bank.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagApi :: apiTagApiProduct :: Nil,
      Some(canUpdateApiProduct :: Nil),
      http4sPartialFunction = Some(createOrUpdateApiProduct)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE
    lazy val getApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "api-products" / apiProductCode =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (apiProduct, _) <- NewStyle.function.getApiProductByBankIdAndCode(bank.bankId.value, apiProductCode, Some(cc))
            (attributes, _) <- NewStyle.function.getApiProductAttributesByBankIdAndCode(bank.bankId.value, apiProductCode, Some(cc))
          } yield JSONFactory600.createApiProductJsonV600(apiProduct, Some(attributes))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getApiProduct), "GET",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE", "Get Api Product",
      """Get an Api Product by BANK_ID and API_PRODUCT_CODE.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagApi :: apiTagApiProduct :: Nil,
      Some(canGetApiProduct :: Nil),
      http4sPartialFunction = Some(getApiProduct)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/api-products
    lazy val getApiProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "api-products" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          val tagFilter = req.uri.query.params.get("tag").map(_.trim).filter(_.nonEmpty)
          for {
            (apiProducts, _) <- NewStyle.function.getApiProductsByBankId(bank.bankId.value, tagFilter, Some(cc))
          } yield JSONFactory600.createApiProductsJsonV600(apiProducts)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getApiProducts), "GET",
      "/banks/BANK_ID/api-products", "Get Api Products",
      """Get all Api Products for the Bank. Optional ?tag= filter.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagApi :: apiTagApiProduct :: Nil,
      Some(canGetApiProduct :: Nil),
      http4sPartialFunction = Some(getApiProducts)
    )

    // Route: DELETE /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE
    lazy val deleteApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "api-products" / apiProductCode =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            _ <- NewStyle.function.deleteApiProductAttributesByBankIdAndCode(bank.bankId.value, apiProductCode, Some(cc))
            _ <- NewStyle.function.deleteApiProduct(bank.bankId.value, apiProductCode, Some(cc))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteApiProduct), "DELETE",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE", "Delete Api Product",
      """Delete an Api Product by BANK_ID and API_PRODUCT_CODE.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagApi :: apiTagApiProduct :: Nil,
      Some(canDeleteApiProduct :: Nil),
      http4sPartialFunction = Some(deleteApiProduct)
    )

    // Route: POST /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE/attribute (201)
    lazy val createApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "api-products" / apiProductCode / "attribute" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          for {
            _ <- NewStyle.function.getApiProductByBankIdAndCode(bank.bankId.value, apiProductCode, Some(cc))
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ApiProductAttributeJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ApiProductAttributeJsonV600]
            }
            (attribute, _) <- NewStyle.function.createOrUpdateApiProductAttribute(
              bank.bankId.value, apiProductCode, None,
              postJson.name, postJson.`type`, postJson.value, postJson.is_active, Some(cc))
          } yield JSONFactory600.createApiProductAttributeResponseJsonV600(attribute)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createApiProductAttribute), "POST",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attribute", "Create Api Product Attribute",
      """Create an attribute for the specified Api Product.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagApi :: apiTagApiProductAttribute :: Nil,
      Some(canCreateApiProductAttribute :: Nil),
      http4sPartialFunction = Some(createApiProductAttribute)
    )

    // Route: PUT /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID
    lazy val updateApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "api-products" / apiProductCode / "attributes" / apiProductAttributeId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          for {
            _ <- NewStyle.function.getApiProductByBankIdAndCode(bank.bankId.value, apiProductCode, Some(cc))
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ApiProductAttributeJsonV600", 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[ApiProductAttributeJsonV600]
            }
            (attribute, _) <- NewStyle.function.createOrUpdateApiProductAttribute(
              bank.bankId.value, apiProductCode, Some(apiProductAttributeId),
              postJson.name, postJson.`type`, postJson.value, postJson.is_active, Some(cc))
          } yield JSONFactory600.createApiProductAttributeResponseJsonV600(attribute)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateApiProductAttribute), "PUT",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID", "Update Api Product Attribute",
      """Update an Api Product Attribute.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      apiTagApi :: apiTagApiProductAttribute :: Nil,
      Some(canUpdateApiProductAttribute :: Nil),
      http4sPartialFunction = Some(updateApiProductAttribute)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID
    lazy val getApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "api-products" / _ / "attributes" / apiProductAttributeId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (attribute, _) <- NewStyle.function.getApiProductAttributeById(apiProductAttributeId, Some(cc))
          } yield JSONFactory600.createApiProductAttributeResponseJsonV600(attribute)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getApiProductAttribute), "GET",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID", "Get Api Product Attribute",
      """Get an Api Product Attribute by API_PRODUCT_ATTRIBUTE_ID.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagApi :: apiTagApiProductAttribute :: Nil,
      Some(canGetApiProductAttribute :: Nil),
      http4sPartialFunction = Some(getApiProductAttribute)
    )

    // Route: DELETE /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID
    lazy val deleteApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "api-products" / _ / "attributes" / apiProductAttributeId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            _ <- NewStyle.function.deleteApiProductAttribute(apiProductAttributeId, Some(cc))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteApiProductAttribute), "DELETE",
      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID", "Delete Api Product Attribute",
      """Delete an Api Product Attribute.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagApi :: apiTagApiProductAttribute :: Nil,
      Some(canDeleteApiProductAttribute :: Nil),
      http4sPartialFunction = Some(deleteApiProductAttribute)
    )

    // ─── Phase 2: mandates bucket (10 endpoints) ──────────────────────────

    // Parse `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC strings; v6 Lift's exact format.
    private def parseMandateDate(s: String, field: String, cc: CallContext): Future[java.util.Date] =
      NewStyle.function.tryons(s"$InvalidDateFormat $field must be in yyyy-MM-dd'T'HH:mm:ss'Z' format", 400, Some(cc)) {
        val fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
        fmt.setLenient(false)
        fmt.parse(s)
      }

    // Route: POST /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/mandates (201)
    lazy val createMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / "mandates" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val bank = cc.bank.get
          val account = cc.bankAccount.get
          for {
            createJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateMandateJsonV600]
            }
            validFrom <- parseMandateDate(createJson.valid_from, "valid_from", cc)
            validTo <- parseMandateDate(createJson.valid_to, "valid_to", cc)
            (mandate, _) <- BankConnector.connector.vend.createMandate(
              bank.bankId, account.accountId, createJson.customer_id,
              createJson.mandate_name, createJson.mandate_reference,
              createJson.legal_text, createJson.description, createJson.status,
              validFrom, validTo, cc.userId, Some(cc)
            ).map(i => (unboxFullOrFail(i._1, Some(cc), "Could not create mandate"), i._2))
          } yield JSONFactory600.createMandateJsonV600(mandate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createMandate), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates", "Create Mandate",
      """Create a new mandate for an account.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canCreateMandate :: Nil),
      http4sPartialFunction = Some(createMandate)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/mandates
    lazy val getMandates: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "mandates" =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          for {
            (mandates, _) <- BankConnector.connector.vend.getMandatesByBankAndAccount(
              account.bankId, account.accountId, Some(cc))
              .map(i => (unboxFullOrFail(i._1, Some(cc), "Could not get mandates"), i._2))
          } yield JSONFactory600.createMandatesJsonV600(mandates)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMandates), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates", "Get Mandates",
      """Get all mandates for an account.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canGetMandate :: Nil),
      http4sPartialFunction = Some(getMandates)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID
    lazy val getMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "mandates" / mandateId =>
        EndpointHelpers.withBankAccount(req) { (_, _, cc) =>
          for {
            (mandate, _) <- BankConnector.connector.vend.getMandateById(mandateId, Some(cc))
              .map(i => (unboxFullOrFail(i._1, Some(cc), s"Mandate not found. Mandate ID: $mandateId", 404), i._2))
          } yield JSONFactory600.createMandateJsonV600(mandate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMandate), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID", "Get Mandate",
      """Get a specific mandate.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canGetMandate :: Nil),
      http4sPartialFunction = Some(getMandate)
    )

    // Route: PUT /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID
    lazy val updateMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / "mandates" / mandateId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            updateJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateMandateJsonV600]
            }
            validFrom <- parseMandateDate(updateJson.valid_from, "valid_from", cc)
            validTo <- parseMandateDate(updateJson.valid_to, "valid_to", cc)
            (mandate, _) <- BankConnector.connector.vend.updateMandate(
              mandateId, updateJson.mandate_name, updateJson.mandate_reference,
              updateJson.legal_text, updateJson.description, updateJson.status,
              validFrom, validTo, cc.userId, Some(cc)
            ).map(i => (unboxFullOrFail(i._1, Some(cc), s"Could not update mandate. Mandate ID: $mandateId"), i._2))
          } yield JSONFactory600.createMandateJsonV600(mandate)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateMandate), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID", "Update Mandate",
      """Update a mandate.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canUpdateMandate :: Nil),
      http4sPartialFunction = Some(updateMandate)
    )

    // Route: DELETE /obp/v6.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID (204)
    lazy val deleteMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / "mandates" / mandateId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            _ <- BankConnector.connector.vend.deleteMandate(mandateId, Some(cc))
              .map(i => (unboxFullOrFail(i._1, Some(cc), s"Could not delete mandate. Mandate ID: $mandateId"), i._2))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteMandate), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID", "Delete Mandate",
      """Delete a mandate and all its provisions and signatory panels.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canDeleteMandate :: Nil),
      http4sPartialFunction = Some(deleteMandate)
    )

    // Provision serializer — match Lift exactly.
    private def serializeSignatoryRequirements(any: Any): String = {
      net.liftweb.json.Serialization.write(any.asInstanceOf[AnyRef])(net.liftweb.json.DefaultFormats)
    }

    // Route: POST /obp/v6.0.0/banks/BANK_ID/mandates/MANDATE_ID/provisions (201)
    lazy val createMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "mandates" / mandateId / "provisions" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            createJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateMandateProvisionJsonV600]
            }
            sigReqJson = serializeSignatoryRequirements(createJson.signatory_requirements)
            (provision, _) <- BankConnector.connector.vend.createMandateProvision(
              mandateId, createJson.provision_name, createJson.provision_description,
              createJson.legal_reference, createJson.provision_type, createJson.conditions,
              sigReqJson,
              createJson.linked_view_id.getOrElse(""),
              createJson.linked_abac_rule_id.getOrElse(""),
              createJson.linked_challenge_type.getOrElse(""),
              createJson.is_active, createJson.sort_order, Some(cc)
            ).map(i => (unboxFullOrFail(i._1, Some(cc), "Could not create mandate provision"), i._2))
          } yield JSONFactory600.createMandateProvisionJsonV600(provision)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(createMandateProvision), "POST",
      "/banks/BANK_ID/mandates/MANDATE_ID/provisions", "Create Mandate Provision",
      """Create a provision under a mandate.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canCreateMandateProvision :: Nil),
      http4sPartialFunction = Some(createMandateProvision)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/mandates/MANDATE_ID/provisions
    lazy val getMandateProvisions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "mandates" / mandateId / "provisions" =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (provisions, _) <- BankConnector.connector.vend.getMandateProvisionsByMandateId(mandateId, Some(cc))
              .map(i => (unboxFullOrFail(i._1, Some(cc), s"Could not get provisions for mandate: $mandateId"), i._2))
          } yield JSONFactory600.createMandateProvisionsJsonV600(provisions)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMandateProvisions), "GET",
      "/banks/BANK_ID/mandates/MANDATE_ID/provisions", "Get Mandate Provisions",
      """Get all provisions for a mandate.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canGetMandateProvision :: Nil),
      http4sPartialFunction = Some(getMandateProvisions)
    )

    // Route: GET /obp/v6.0.0/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID
    lazy val getMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "mandates" / _ / "provisions" / provisionId =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (provision, _) <- BankConnector.connector.vend.getMandateProvisionById(provisionId, Some(cc))
              .map(i => (unboxFullOrFail(i._1, Some(cc), s"Mandate provision not found. Provision ID: $provisionId", 404), i._2))
          } yield JSONFactory600.createMandateProvisionJsonV600(provision)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getMandateProvision), "GET",
      "/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID", "Get Mandate Provision",
      """Get a specific mandate provision.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canGetMandateProvision :: Nil),
      http4sPartialFunction = Some(getMandateProvision)
    )

    // Route: PUT /obp/v6.0.0/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID
    lazy val updateMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "mandates" / _ / "provisions" / provisionId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          val rawBody = cc.httpBody.getOrElse("")
          for {
            updateJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[UpdateMandateProvisionJsonV600]
            }
            sigReqJson = serializeSignatoryRequirements(updateJson.signatory_requirements)
            (provision, _) <- BankConnector.connector.vend.updateMandateProvision(
              provisionId, updateJson.provision_name, updateJson.provision_description,
              updateJson.legal_reference, updateJson.provision_type, updateJson.conditions,
              sigReqJson,
              updateJson.linked_view_id.getOrElse(""),
              updateJson.linked_abac_rule_id.getOrElse(""),
              updateJson.linked_challenge_type.getOrElse(""),
              updateJson.is_active, updateJson.sort_order, Some(cc)
            ).map(i => (unboxFullOrFail(i._1, Some(cc), s"Could not update provision. Provision ID: $provisionId"), i._2))
          } yield JSONFactory600.createMandateProvisionJsonV600(provision)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateMandateProvision), "PUT",
      "/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID", "Update Mandate Provision",
      """Update a mandate provision.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canUpdateMandateProvision :: Nil),
      http4sPartialFunction = Some(updateMandateProvision)
    )

    // Route: DELETE /obp/v6.0.0/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID (204)
    lazy val deleteMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "mandates" / _ / "provisions" / provisionId =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            _ <- BankConnector.connector.vend.deleteMandateProvision(provisionId, Some(cc))
              .map(i => (unboxFullOrFail(i._1, Some(cc), s"Could not delete provision. Provision ID: $provisionId"), i._2))
          } yield ""
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(deleteMandateProvision), "DELETE",
      "/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID", "Delete Mandate Provision",
      """Delete a mandate provision.""",
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      apiTagMandate :: Nil,
      Some(canDeleteMandateProvision :: Nil),
      http4sPartialFunction = Some(deleteMandateProvision)
    )

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)

    // ─── path-rewriting bridge: /obp/v6.0.0/… → /obp/v5.1.0/… ─────────────
    // NOT appended to allRoutes — see object-level scaladoc.
    val v600ToV510Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v6.0.0/")) {
        val rewritten = rawPath.replaceFirst("/obp/v6\\.0\\.0/", "/obp/v5.1.0/")
        val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        Http4s510.wrappedRoutesV510Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV600Services: HttpRoutes[IO] =
    Implementations6_0_0.allRoutesWithMiddleware
}
