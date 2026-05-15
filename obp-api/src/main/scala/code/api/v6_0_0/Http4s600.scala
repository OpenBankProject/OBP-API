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
import code.api.util.http4s.ResourceDocMiddleware
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
import code.api.v3_1_0.PostCustomerNumberJsonV310
import code.api.v5_1_0.UserAttributesResponseJsonV510
import code.api.v5_1_0.PostCustomerLegalNameJsonV510
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
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getScannedApiVersions: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCustomersAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCustomerByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCoreAccountByIdV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMyDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getSystemDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getBankLevelDynamicEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCustomersAtAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getUserAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getPrivateAccountByIdFull: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCustomerByCustomerNumber: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCustomersByLegalName: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
      Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(dynamicEntity, Some(cc))
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
    } yield {
      JSONFactory600.createMyDynamicEntitiesJson(List(result: DynamicEntityCommons)).dynamic_entities.head
    }

    // Route: POST /obp/v6.0.0/management/system-dynamic-entities (201)
    val createSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "system-dynamic-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            internalJson = JSONFactory600.convertV600RequestToInternal(request)
            dynamicEntity = DynamicEntityCommons(internalJson, None, cc.userId, None)
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
    val createBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "dynamic-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          for {
            request <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(rawBody).extract[CreateDynamicEntityRequestJsonV600]
            }
            _ <- validateEntityNameV600(request.entity_name, cc)
            internalJson = JSONFactory600.convertV600RequestToInternal(request)
            dynamicEntity = DynamicEntityCommons(internalJson, None, cc.userId, Some(bankIdStr))
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
    val updateSystemDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val updateBankLevelDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val updateMyDynamicEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val updateSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getAggregateMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getTopAPIs: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getAccountsAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getProductsV600: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val resetPasswordUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getConnectors: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCacheConfig: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCacheInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getCacheNamespaces: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getDatabasePoolInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMigrations: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getStoredProcedureConnectorHealth: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getConnectorMethodNames: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
      }

    // ─── Phase 2: api-products bucket (9 endpoints) ───────────────────────
    // All endpoints always require auth + role; the v6 Lift conditional
    // public-access path (getApiProductsIsPublic) is simplified — public
    // gating would be a Phase 3 follow-up if needed.

    // Route: POST /obp/v6.0.0/banks/BANK_ID/api-products/API_PRODUCT_CODE (201)
    val createApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createOrUpdateApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getApiProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val deleteApiProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val updateApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val deleteApiProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMandates: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val updateMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val deleteMandate: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val createMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMandateProvisions: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val getMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val updateMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
    val deleteMandateProvision: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
