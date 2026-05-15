package code.api.v6_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.{APIUtil, CustomJsonFormats, NewStyle}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.newstyle.ViewNewStyle
import code.api.v2_0_0.JSONFactory200
import code.api.v5_1_0.{Http4s510, JSONFactory510}
import code.api.v6_0_0.JSONFactory600.ScannedApiVersionJsonV600
import code.api.util.RateLimitingUtil
import code.api.v3_1_0.PostCustomerNumberJsonV310
import code.api.v5_1_0.UserAttributesResponseJsonV510
import code.api.v5_1_0.PostCustomerLegalNameJsonV510
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
            dynamicEntities <- Future(NewStyle.function.getDynamicEntitiesByUserId(Some(user.userId)))
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
      }

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
