package code.api.v7_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResourceDocs1_4_0.{ResourceDocs140, ResourceDocsAPIMethodsUtil}
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.{APIUtil, ApiRole, ApiVersionUtils, CallContext, CustomJsonFormats, NewStyle}
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canCreateEntitlementAtOneBank, canDeleteEntitlementAtAnyBank, canGetAnyUser, canGetCardsForBank, canGetCustomersAtOneBank}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.{ErrorResponseConverter, Http4sRequestAttributes, ResourceDocMiddleware}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.ViewNewStyle
import code.api.v1_3_0.JSONFactory1_3_0
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v2_0_0.{BasicViewJson, CreateEntitlementJSON, JSONFactory200}
import code.api.v4_0_0.JSONFactory400
import code.api.v6_0_0.{BasicAccountJsonV600, BasicAccountsJsonV600, BankJsonV600, ConnectorInfoJsonV600, ConnectorsJsonV600, FeaturesJsonV600, JSONFactory600, UserV600}
import code.bankconnectors.{Connector => BankConnector}
import code.entitlement.Entitlement
import code.metadata.tags.Tags
import code.views.Views
import code.accountattribute.AccountAttributeX
import code.users.{Users => UserVend}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, CounterpartyId, CustomerId, ListResult, ViewId}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import code.loginattempts.LoginAttempt
import code.metrics.MappedMetric
import code.users.UserAgreementProvider
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import net.liftweb.mapper.{By, Descending, MaxRows, OrderBy}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}
import code.util.Helper

object Http4s700 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v7_0_0
  val versionStatus = ApiVersionStatus.STABLE.toString
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  object Implementations7_0_0 {

    // Common prefix: /obp/v7.0.0
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // IMPORTANT: each `val endpoint` MUST be declared BEFORE its `resourceDocs +=` line.
    //
    // `allRoutes` sorts resourceDocs by URL segment count and reads `http4sPartialFunction`
    // from each entry. In a Scala object, vals are initialized in declaration order.
    // If `resourceDocs += ResourceDoc(..., http4sPartialFunction = Some(myEndpoint))` runs
    // before `val myEndpoint` is initialized, `Some(null)` is stored. The sort+fold then
    // produces a null-route chain that NPEs on every request — and because OptionT.orElse
    // only recovers from None (not failed IO), the NPE propagates up and kills the entire
    // http4s handler chain, including the Lift bridge fallback.
    //
    // Convention: val → resourceDocs +=, never the other way around.

    // Route: GET /obp/v7.0.0/root
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        val responseJson = convertAnyToJsonString(
          JSONFactory700.getApiInfoJSON(implementedInApiVersion, versionStatus)
        )
        Ok(responseJson)
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      s"""Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit
        """,
      EmptyBody,
      apiInfoJSON,
      List(
        UnknownError
      ),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(root)
    )

    // Route: GET /obp/v7.0.0/banks
    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (banks, callContext) <- NewStyle.function.getBanks(Some(cc))
          } yield JSONFactory400.createBanksJson(banks)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      s"""Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      banksJSON,
      List(
        UnknownError
      ),
      apiTagBank :: Nil,
      http4sPartialFunction = Some(getBanks)
    )

    // Route: GET /obp/v7.0.0/cards
    // Authentication handled by ResourceDocMiddleware based on AuthenticatedUserIsRequired
    val getCards: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "cards" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (cards, callContext) <- NewStyle.function.getPhysicalCardsForUser(user, Some(cc))
          } yield JSONFactory1_3_0.createPhysicalCardsJSON(cards, user)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCards),
      "GET",
      "/cards",
      "Get cards for the current user",
      "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
      EmptyBody,
      physicalCardsJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      apiTagCard :: Nil,
      http4sPartialFunction = Some(getCards)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/cards
    // Authentication and bank validation handled by ResourceDocMiddleware
    val getCardsForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "cards" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (cards, callContext) <- NewStyle.function.getPhysicalCardsForBank(bank, user, obpQueryParams, callContext)
          } yield JSONFactory1_3_0.createPhysicalCardsJSON(cards, user)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCardsForBank),
      "GET",
      "/banks/BANK_ID/cards",
      "Get cards for the specified bank",
      "",
      EmptyBody,
      physicalCardsJSON,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      apiTagCard :: Nil,
      Some(List(canGetCardsForBank)),
      http4sPartialFunction = Some(getCardsForBank)
    )

    // Route: GET /obp/v7.0.0/resource-docs/API_VERSION/obp
    val getResourceDocsObpV700: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "resource-docs" / requestedApiVersionString / "obp" =>
        implicit val cc: CallContext = req.callContext
        val queryParams = req.uri.query.multiParams
        val tags = queryParams
          .get("tags")
          .map(_.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).map(ResourceDocTag(_)).toList)
        val functions = queryParams
          .get("functions")
          .map(_.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).toList)
        val localeParam = queryParams
          .get("locale")
          .flatMap(_.headOption)
          .orElse(queryParams.get("language").flatMap(_.headOption))
          .map(_.trim)
          .filter(_.nonEmpty)

        EndpointHelpers.executeAndRespond(req) { _ =>
          for {
            requestedApiVersion <- NewStyle.function.tryons(
              failMsg = s"$InvalidApiVersionString Current value: $requestedApiVersionString",
              failCode = 400,
              callContext = Some(cc)
            ) {
              ApiVersionUtils.valueOf(requestedApiVersionString)
            }
            _ <- Helper.booleanToFuture(
              failMsg = s"$InvalidApiVersionString This server supports only ${ApiVersion.v7_0_0}. Current value: $requestedApiVersionString",
              failCode = 400,
              cc = Some(cc)
            ) {
              requestedApiVersion == ApiVersion.v7_0_0
            }
            http4sOnlyDocs = ResourceDocsAPIMethodsUtil.filterResourceDocs(resourceDocs.toList, tags, functions)
          } yield JSONFactory1_4_0.createResourceDocsJson(http4sOnlyDocs, isVersion4OrHigher = true, localeParam, includeTechnology = true)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getResourceDocsObpV700),
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs",
      s"""Get documentation about the RESTful resources on this server including example body payloads.
        |
        |* API_VERSION: The version of the API for which you want documentation
        |
        |Returns JSON containing information about the endpoints including:
        |* Method (GET, POST, etc.)
        |* URL path
        |* Summary and description
        |* Example request and response bodies
        |* Required roles and permissions
        |
        |Optional query parameters:
        |* tags - filter by API tags
        |* functions - filter by function names
        |* locale - specify language for descriptions
        |* content - filter by content type""",
      EmptyBody,
      EmptyBody,
      List(
        UnknownError
      ),
      List(apiTagDocumentation, apiTagApi),
      http4sPartialFunction = Some(getResourceDocsObpV700)
    )

    // ── POC endpoints — one per EndpointHelper category ────────────────────

    // Category: withBank (no user auth, bank resolved from BANK_ID by middleware)
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
      """Get the bank specified by BANK_ID. Returns information about a single bank including name, logo and website.""",
      EmptyBody,
      BankJsonV600("gh.29.uk", "OBP", "Open Bank Project", "https://example.com/logo.png", "https://openbankproject.com", Nil, None),
      List(BankNotFound, UnknownError),
      apiTagBank :: Nil,
      http4sPartialFunction = Some(getBank)
    )

    // Category: withUser (user auth, no bank)
    val getCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
          } yield {
            val permissions = Views.views.vend.getPermissionForUser(user).toOption
            val virtualRoleNames =
              if (APIUtil.isSuperAdmin(user.userId)) APIUtil.superAdminVirtualRoles
              else if (APIUtil.isOidcOperator(user.userId)) APIUtil.oidcOperatorVirtualRoles
              else Nil
            val existingRoleNames = entitlements.map(_.roleName).toSet
            val virtualEntitlements = virtualRoleNames.filterNot(existingRoleNames.contains).map { role =>
              new Entitlement {
                def entitlementId      = ""
                def bankId             = ""
                def userId             = user.userId
                def roleName           = role
                def createdByProcess   = if (APIUtil.isSuperAdmin(user.userId)) "super_admin_user_ids" else "oidc_operator_user_ids"
                def entitlementRequestId: Option[String] = None
                def groupId: Option[String]              = None
                def process: Option[String]              = None
              }
            }
            JSONFactory600.createUserInfoJSON(UserV600(user, entitlements ::: virtualEntitlements, permissions), None)
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
      """Get the logged in user. Returns profile, entitlements and views.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagUser :: Nil,
      http4sPartialFunction = Some(getCurrentUser)
    )

    // Category: withBankAccount (user + account resolved from BANK_ID + ACCOUNT_ID by middleware)
    val getCoreAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
      nameOf(getCoreAccountById),
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      """Returns core information about the account specified by ACCOUNT_ID including balance, routings and available views.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankAccountNotFound, UnknownError),
      apiTagAccount :: Nil,
      http4sPartialFunction = Some(getCoreAccountById)
    )

    // Category: withView (user + account + view resolved from BANK_ID + ACCOUNT_ID + VIEW_ID by middleware)
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
      http4sPartialFunction = Some(getPrivateAccountByIdFull)
    )

    // Category: withCounterparty (user + account + view + counterparty resolved by middleware)
    val getExplicitCounterpartyById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "counterparties" / counterpartyIdStr =>
        EndpointHelpers.withCounterparty(req) { (user, account, view, counterparty, cc) =>
          for {
            _ <- Helper.booleanToFuture(
              failMsg = s"${NoViewPermission}can_get_counterparty", 403, cc = Some(cc))(
              view.allowed_actions.exists(_ == CAN_GET_COUNTERPARTY))
            counterpartyMetadata <- NewStyle.function.getMetadata(
              account.bankId, account.accountId, counterpartyIdStr, Some(cc))
          } yield JSONFactory400.createCounterpartyWithMetadataJson400(counterparty, counterpartyMetadata)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getExplicitCounterpartyById),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Id (Explicit)",
      """Returns a single Counterparty on an Account View specified by COUNTERPARTY_ID.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      apiTagCounterparty :: Nil,
      http4sPartialFunction = Some(getExplicitCounterpartyById)
    )

    // Category: withUserDelete (user auth, 204 No Content)
    val deleteEntitlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "entitlements" / entitlementId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          Entitlement.entitlement.vend.getEntitlementById(entitlementId) match {
            case Full(e) => Future(Entitlement.entitlement.vend.deleteEntitlement(Some(e))).map(_ => ())
            case _       => Future.successful(()) // idempotent — already gone
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(deleteEntitlement),
      "DELETE",
      "/entitlements/ENTITLEMENT_ID",
      "Delete Entitlement",
      """Delete the Entitlement specified by ENTITLEMENT_ID. Idempotent — returns 204 even if not found.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagEntitlement :: apiTagRole :: Nil,
      Some(List(canDeleteEntitlementAtAnyBank)),
      http4sPartialFunction = Some(deleteEntitlement)
    )

    // Category: withUserAndBodyCreated (user auth, body parsing, 201 Created)
    val addEntitlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userId / "entitlements" =>
        EndpointHelpers.withUserAndBodyCreated[CreateEntitlementJSON, AnyRef](req) { (user, body, cc) =>
          for {
            (_, _)   <- NewStyle.function.findByUserId(userId, Some(cc))
            role     <- NewStyle.function.tryons(
              s"$InvalidJsonFormat Unknown role: ${body.role_name}. Possible roles: ${ApiRole.availableRoles.sorted.mkString(", ")}",
              400, Some(cc)) { ApiRole.valueOf(body.role_name) }
            _ <- Helper.booleanToFuture(
              failMsg = if (role.requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole,
              cc = Some(cc))(role.requiresBankId == body.bank_id.nonEmpty)
            _ <- if (APIUtil.isSuperAdmin(user.userId)) Future.successful(())
                 else NewStyle.function.hasAtLeastOneEntitlement(
                   UserHasMissingRoles + s" $canCreateEntitlementAtOneBank or $canCreateEntitlementAtAnyBank")(
                   body.bank_id, user.userId,
                   canCreateEntitlementAtOneBank :: canCreateEntitlementAtAnyBank :: Nil, Some(cc)).map(_ => ())
            _ <- Helper.booleanToFuture(failMsg = EntitlementAlreadyExists, cc = Some(cc))(
              !hasEntitlement(body.bank_id, userId, role))
            entitlement <- Future(Entitlement.entitlement.vend.addEntitlement(body.bank_id, userId, body.role_name))
              .map(e => unboxFull(e))
          } yield JSONFactory200.createEntitlementJSON(entitlement)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(addEntitlement),
      "POST",
      "/users/USER_ID/entitlements",
      "Add Entitlement for a User",
      """Grant a Role to a User. Set bank_id to "" for system-level roles, or a valid bank_id for bank-level roles.""",
      CreateEntitlementJSON("gh.29.uk", "CanGetAnyUser"),
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserNotFoundById, InvalidJsonFormat, EntitlementAlreadyExists, UnknownError),
      apiTagEntitlement :: apiTagRole :: apiTagUser :: Nil,
      Some(List(canCreateEntitlementAtOneBank, canCreateEntitlementAtAnyBank)),
      http4sPartialFunction = Some(addEntitlement)
    )

    // ── Phase 1 — Simple GETs ───────────────────────────────────────────────

    // Route: GET /obp/v7.0.0/features
    val getFeatures: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "features" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(FeaturesJsonV600(
            allow_public_views                = APIUtil.getPropsAsBoolValue("allow_public_views", false),
            allow_abac_account_access         = APIUtil.getPropsAsBoolValue("allow_abac_account_access", false),
            allow_account_firehose            = APIUtil.getPropsAsBoolValue("allow_account_firehose", false),
            allow_customer_firehose           = APIUtil.getPropsAsBoolValue("allow_customer_firehose", false),
            allow_direct_login                = APIUtil.getPropsAsBoolValue("allow_direct_login", true),
            allow_gateway_login               = APIUtil.getPropsAsBoolValue("allow_gateway_login", false),
            allow_oauth2_login                = APIUtil.getPropsAsBoolValue("allow_oauth2_login", true),
            allow_dauth                       = APIUtil.getPropsAsBoolValue("allow_dauth", false),
            allow_sandbox_account_creation    = APIUtil.getPropsAsBoolValue("allow_sandbox_account_creation", false),
            allow_sandbox_data_import         = APIUtil.getPropsAsBoolValue("allow_sandbox_data_import", false),
            allow_account_deletion            = APIUtil.getPropsAsBoolValue("allow_account_deletion", false),
            allow_just_in_time_entitlements   = APIUtil.getPropsAsBoolValue("create_just_in_time_entitlements", false)
          ))
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getFeatures),
      "GET",
      "/features",
      "Get Features",
      """Returns information about the features enabled on this OBP instance.
        |
        |No Authentication is Required.""",
      EmptyBody,
      FeaturesJsonV600(false, false, false, false, true, false, true, false, false, false, false, false),
      List(UnknownError),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(getFeatures)
    )

    // Route: GET /obp/v7.0.0/api/versions
    val getScannedApiVersions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "versions" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future {
            val versions = ApiVersion.allScannedApiVersion.asScala.toList
              .filter(v => v.urlPrefix.trim.nonEmpty)
              .map { v =>
                JSONFactory600.ScannedApiVersionJsonV600(
                  url_prefix             = v.urlPrefix,
                  api_standard           = v.apiStandard,
                  api_short_version      = v.apiShortVersion,
                  fully_qualified_version= v.fullyQualifiedVersion,
                  is_active              = versionIsAllowed(v)
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
      """Get all scanned API versions available in this codebase including their active status.""",
      EmptyBody,
      ListResult(
        "scanned_api_versions",
        List(JSONFactory600.ScannedApiVersionJsonV600("obp", "OBP", "v6.0.0", "OBPv6.0.0", true))
      ),
      List(UnknownError),
      apiTagDocumentation :: apiTagApi :: Nil,
      http4sPartialFunction = Some(getScannedApiVersions)
    )

    // Route: GET /obp/v7.0.0/system/connectors
    val getConnectors: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "connectors" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful {
            val connectorNames = BankConnector.nameToConnector.keys.toList :+ "star"
            val connectorInfos = connectorNames.map { name =>
              ConnectorInfoJsonV600(
                connector_name                    = name,
                is_available_in_method_routing    = NewStyle.function.getConnectorByName(name).isDefined
              )
            }
            JSONFactory600.createConnectorsJson(connectorInfos)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getConnectors),
      "GET",
      "/system/connectors",
      "Get Connectors",
      """Get the list of connectors and their availability for method routing.
        |
        |Authentication is Optional.""",
      EmptyBody,
      ConnectorsJsonV600(List(
        ConnectorInfoJsonV600("mapped", true),
        ConnectorInfoJsonV600("star", true)
      )),
      List(UnknownError),
      apiTagConnector :: apiTagSystem :: apiTagApi :: Nil,
      http4sPartialFunction = Some(getConnectors)
    )

    // Route: GET /obp/v7.0.0/providers
    val getProviders: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "providers" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            providers <- Future { code.model.dataAccess.ResourceUser.getDistinctProviders }
          } yield JSONFactory600.createProvidersJson(providers)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getProviders),
      "GET",
      "/providers",
      "Get Providers",
      """Get the list of authentication providers that have been used to create users on this OBP instance.""",
      EmptyBody,
      JSONFactory600.createProvidersJson(List("http://127.0.0.1:8080", "OBP")),
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagUser :: Nil,
      http4sPartialFunction = Some(getProviders)
    )

    // ── Phase 1 batch 2 ─────────────────────────────────────────────────────

    // Route: GET /obp/v7.0.0/users
    val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, cc.callContext)
            users <- UserVend.users.vend.getUsers(obpQueryParams)
          } yield JSONFactory600.createUsersInfoJsonV600(users)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getUsers),
      "GET",
      "/users",
      "Get all Users",
      """Get all users.
        |
        |Authentication is required.
        |
        |CanGetAnyUser entitlement is required.""",
      EmptyBody,
      usersInfoJsonV600,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagUser :: Nil,
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUsers)
    )

    // Route: GET /obp/v7.0.0/users/user-id/USER_ID
    val getUserByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "user-id" / userId =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          for {
            user <- UserVend.users.vend.getUserByUserIdFuture(userId).map(
              x => unboxFullOrFail(x, cc.callContext, s"$UserNotFoundByUserId Current USER_ID($userId)", 404)
            )
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
            agreements <- Future {
              val acceptMarketingInfo = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "accept_marketing_info")
              val termsAndConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "terms_and_conditions")
              val privacyConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "privacy_conditions")
              val agreementList = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
              if (agreementList.isEmpty) None else Some(agreementList)
            }
            isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
            authUser = code.model.dataAccess.AuthUser.find(
              By(code.model.dataAccess.AuthUser.user, user.userPrimaryKey.value)
            )
            userMetrics <- Future {
              MappedMetric.findAll(
                By(MappedMetric.userId, userId),
                OrderBy(MappedMetric.date, Descending),
                MaxRows(5)
              )
            }
            lastActivityDate = userMetrics.headOption.map(_.getDate())
            recentOperationIds = userMetrics.map(_.getImplementedByPartialFunction()).distinct.take(5)
          } yield JSONFactory600.createUserInfoJsonV600(
            user,
            authUser.map(_.firstName.get).getOrElse(""),
            authUser.map(_.lastName.get).getOrElse(""),
            entitlements,
            agreements,
            isLocked,
            lastActivityDate,
            recentOperationIds
          )
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getUserByUserId),
      "GET",
      "/users/user-id/USER_ID",
      "Get User by USER_ID",
      """Get user by USER_ID.
        |
        |Authentication is required.
        |
        |CanGetAnyUser entitlement is required.""",
      EmptyBody,
      userInfoJsonV600,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByUserId, UnknownError),
      apiTagUser :: Nil,
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUserByUserId)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/customers
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
      """Get Customers at Bank.
        |
        |Returns a list of all customers at the specified bank.
        |
        |Authentication is required.""",
      EmptyBody,
      customerJSONsV600,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: apiTagUser :: Nil,
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomersAtOneBank)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/customers/CUSTOMER_ID
    val getCustomerByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerId =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(
              bank.bankId,
              CustomerId(customerId),
              callContext
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
      """Gets the Customer specified by CUSTOMER_ID.
        |
        |Authentication is required.""",
      EmptyBody,
      customerWithAttributesJsonV600,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCustomer :: Nil,
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomerByCustomerId)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts
    val getAccountsAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (u, bank, cc) =>
          val bankId = BankId(bankIdStr)
          for {
            (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
              Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)
            }
            params <- Future {
              req.uri.query.multiParams
                .filterNot(_._1 == PARAM_TIMESTAMP)
                .filterNot(_._1 == PARAM_LOCALE)
                .map { case (k, vs) => k -> vs.toList }
            }
            privateAccountAccess2 <-
              if (params.isEmpty || privateAccountAccess.isEmpty) {
                Future.successful(privateAccountAccess)
              } else {
                AccountAttributeX.accountAttributeProvider.vend
                  .getAccountIdsByParams(bankId, params)
                  .map { boxedAccountIds =>
                    val accountIds = boxedAccountIds.getOrElse(Nil)
                    privateAccountAccess.filter(aa => accountIds.contains(aa.account_id.get))
                  }
              }
            (availablePrivateAccounts, _) <- code.model.BankExtended(bank).privateAccountsFuture(privateAccountAccess2, cc.callContext)
          } yield {
            val accountsJson = availablePrivateAccounts.map { account =>
              val viewsAvailable = privateViewsUserCanAccessAtOneBank
                .filter(v => v.bankId == bankId && v.accountId == account.accountId)
                .map(v => BasicViewJson(v.viewId.value, v.name, v.isPublic))
              JSONFactory600.createBasicAccountJsonV600(account, viewsAvailable)
            }
            BasicAccountsJsonV600(accountsJson)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getAccountsAtBank),
      "GET",
      "/banks/BANK_ID/accounts",
      "Get Accounts at Bank",
      """Returns the list of accounts at BANK_ID that the user has access to.
        |
        |Authentication is required.""",
      EmptyBody,
      BasicAccountsJsonV600(List(BasicAccountJsonV600(
        account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bank_id = "gh.29.uk",
        label = "My Account",
        views_available = List(BasicViewJson("owner", "Owner", false))
      ))),
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      apiTagAccount :: apiTagPrivateData :: apiTagPublicData :: Nil,
      http4sPartialFunction = Some(getAccountsAtBank)
    )

    // ── Trading Endpoints ──────────────────────────────────────────────────
    
    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers
    val createTradingOffer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "trading" / "offers" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.CreateOfferRequestJson, JSONFactory700.TradingOfferJson](req) { (user, createOfferJson, cc) =>
          for {
            // Validate offer_type
            _ <- Helper.booleanToFuture(
              failMsg = InvalidOfferType,
              failCode = 400,
              cc = Some(cc)
            )(createOfferJson.offer_type == "BUY" || createOfferJson.offer_type == "SELL")

            // Validate asset_amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = Some(cc)
            )(createOfferJson.asset_amount > 0)

            // Validate price_amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = Some(cc)
            )(createOfferJson.price_amount > 0)

            // Invoke connector
            (offer, callContext) <- NewStyle.function.createTradingOffer(
              BankId(bankId),
              AccountId(accountId),
              createOfferJson.offer_type,
              createOfferJson.asset_code,
              createOfferJson.asset_amount,
              createOfferJson.price_currency,
              createOfferJson.price_amount,
              createOfferJson.settlement_account_id,
              Some(cc)
            )
          } yield JSONFactory700.createTradingOfferJson(offer)
        }
    }
    
    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createTradingOffer),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers",
      "Create Trading Offer",
      """Create a new trading offer to buy or sell digital assets.
        |
        |The offer will be matched against existing offers in the order book.
        |The offer_id is automatically generated as a UUID.
        |
        |Authentication is required.""",
      JSONFactory700.CreateOfferRequestJson(
        offer_type = "BUY",
        asset_code = "OGCR",
        asset_amount = BigDecimal("100.00"),
        price_currency = "EUR",
        price_amount = BigDecimal("1.50"),
        settlement_account_id = "settlement-account-123"
      ),
      JSONFactory700.TradingOfferJson(
        offer_id = "550e8400-e29b-41d4-a716-446655440000",
        status = "active",
        offer_details = JSONFactory700.OfferDetailsJson(
          offer_type = "BUY",
          asset_code = "OGCR",
          asset_amount = BigDecimal("100.00"),
          price_currency = "EUR",
          price_amount = BigDecimal("1.50"),
          settlement_account_id = "settlement-account-123",
          expiry_datetime = None,
          minimum_fill = None
        ),
        account_info = JSONFactory700.AccountInfoJson(
          bank_id = "gh.29.uk",
          account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          view_id = "owner"
        ),
        executions = List.empty,
        created_at = "2026-04-15T10:30:00Z",
        updated_at = "2026-04-15T10:30:00Z"
      ),
      List(InvalidJsonFormat, InvalidOfferType, InvalidTradingAmount, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: Nil,
      http4sPartialFunction = Some(createTradingOffer)
    )
    
    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID
    val getTradingOffer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "trading" / "offers" / offerId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            // Invoke connector
            (offer, callContext) <- NewStyle.function.getTradingOffer(offerId, Some(cc))
          } yield JSONFactory700.createTradingOfferJson(offer)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getTradingOffer),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID",
      "Get Trading Offer",
      """Get details of a specific trading offer including execution history.
        |
        |Authentication is required.""",
      EmptyBody,
      JSONFactory700.TradingOfferJson(
        offer_id = "550e8400-e29b-41d4-a716-446655440000",
        status = "active",
        offer_details = JSONFactory700.OfferDetailsJson(
          offer_type = "BUY",
          asset_code = "OGCR",
          asset_amount = BigDecimal("100.00"),
          price_currency = "EUR",
          price_amount = BigDecimal("1.50"),
          settlement_account_id = "settlement-account-123",
          expiry_datetime = None,
          minimum_fill = None
        ),
        account_info = JSONFactory700.AccountInfoJson(
          bank_id = "gh.29.uk",
          account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
          view_id = "owner"
        ),
        executions = List.empty,
        created_at = "2026-04-15T10:30:00Z",
        updated_at = "2026-04-15T10:30:00Z"
      ),
      List(OfferNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: Nil,
      http4sPartialFunction = Some(getTradingOffer)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers
    val getTradingOffers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "trading" / "offers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          // Extract query parameters
          val status = req.uri.query.params.get("status")
          val offerType = req.uri.query.params.get("offer_type")
          
          for {
            // Invoke connector
            (offers, callContext) <- NewStyle.function.getTradingOffers(
              BankId(bankId),
              AccountId(accountId),
              status,
              offerType,
              Some(cc)
            )
          } yield {
            // Convert to JSON
            val offersJson = offers.map(JSONFactory700.createTradingOfferJson)
            JSONFactory700.TradingOffersJson(offersJson)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getTradingOffers),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers",
      "Get Trading Offers",
      """Get a list of trading offers for a specific account.
        |
        |Optional query parameters:
        |- status: Filter by offer status (e.g., "active", "cancelled", "filled", "expired")
        |- offer_type: Filter by offer type ("BUY" or "SELL")
        |
        |Results are sorted by creation date (most recent first).
        |
        |Authentication is required.""",
      EmptyBody,
      JSONFactory700.TradingOffersJson(
        offers = List(
          JSONFactory700.TradingOfferJson(
            offer_id = "550e8400-e29b-41d4-a716-446655440000",
            status = "active",
            offer_details = JSONFactory700.OfferDetailsJson(
              offer_type = "BUY",
              asset_code = "OGCR",
              asset_amount = BigDecimal("100.00"),
              price_currency = "EUR",
              price_amount = BigDecimal("1.50"),
              settlement_account_id = "settlement-account-123",
              expiry_datetime = None,
              minimum_fill = None
            ),
            account_info = JSONFactory700.AccountInfoJson(
              bank_id = "gh.29.uk",
              account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
              view_id = "owner"
            ),
            executions = List.empty,
            created_at = "2026-04-15T10:30:00Z",
            updated_at = "2026-04-15T10:30:00Z"
          )
        )
      ),
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: Nil,
      http4sPartialFunction = Some(getTradingOffers)
    )

    // Route: DELETE /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID
    val cancelTradingOffer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "trading" / "offers" / offerId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            // Invoke connector
            (offer, callContext) <- NewStyle.function.cancelTradingOffer(offerId, Some(cc))
          } yield JSONFactory700.createCancelOfferResponseJson(offer)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(cancelTradingOffer),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/trading/offers/OFFER_ID",
      "Cancel Trading Offer",
      """Cancel an active trading offer.
        |
        |This operation is idempotent - canceling an already-cancelled offer returns success.
        |
        |Authentication is required.""",
      EmptyBody,
      JSONFactory700.CancelOfferResponseJson(
        offer_id = "550e8400-e29b-41d4-a716-446655440000",
        status = "cancelled"
      ),
      List(OfferNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: Nil,
      http4sPartialFunction = Some(cancelTradingOffer)
    )


    // ── End Phase 1 batch 2 ──────────────────────────────────────────────────

    // ── Market Endpoints (Phase 2) ─────────────────────────────────────────

    // Route: POST /obp/v7.0.0/market/orders
    val createMarketOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "market" / "orders" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.CreateMarketOrderRequestJson, JSONFactory700.MarketOrderJson](req) { (user, createOrderJson, cc) =>
          for {
            // Validate side
            _ <- Helper.booleanToFuture(
              failMsg = InvalidOrderSide,
              failCode = 400,
              cc = Some(cc)
            )(createOrderJson.side == "BUY" || createOrderJson.side == "SELL")

            // Validate price
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = Some(cc)
            )(createOrderJson.price > 0)

            // Validate quantity
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = Some(cc)
            )(createOrderJson.quantity > 0)

            // Invoke connector
            (order, callContext) <- NewStyle.function.createMarketOrder(
              createOrderJson.side,
              createOrderJson.price,
              createOrderJson.quantity,
              createOrderJson.account_id,
              createOrderJson.idempotency_key,
              Some(cc)
            )
          } yield JSONFactory700.createMarketOrderJson(order)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createMarketOrder),
      "POST",
      "/market/orders",
      "Create Market Order",
      """Create a new market order to buy or sell assets.
        |
        |The order will be matched against existing orders in the order book.
        |The order_id is automatically generated as a UUID.
        |
        |Authentication is required.""",
      JSONFactory700.CreateMarketOrderRequestJson(
        side = "BUY",
        price = BigDecimal("25.0"),
        quantity = BigDecimal("10.0"),
        account_id = "buyer-fiat-account",
        idempotency_key = "order-12345"
      ),
      JSONFactory700.MarketOrderJson(
        order_id = "550e8400-e29b-41d4-a716-446655440000",
        side = "BUY",
        price = BigDecimal("25.0"),
        quantity = BigDecimal("10.0"),
        account_id = "buyer-fiat-account",
        status = "active",
        created_at = "2026-04-16T00:30:00Z",
        updated_at = "2026-04-16T00:30:00Z"
      ),
      List(InvalidJsonFormat, InvalidOrderSide, InvalidTradingAmount, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(createMarketOrder)
    )

    // Route: GET /obp/v7.0.0/market/orders/ORDER_ID
    val getMarketOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "market" / "orders" / orderId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (order, callContext) <- NewStyle.function.getMarketOrder(orderId, Some(cc))
          } yield JSONFactory700.createMarketOrderJson(order)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getMarketOrder),
      "GET",
      "/market/orders/ORDER_ID",
      "Get Market Order",
      """Get details of a specific market order.
        |
        |Authentication is required.""",
      EmptyBody,
      JSONFactory700.MarketOrderJson(
        order_id = "550e8400-e29b-41d4-a716-446655440000",
        side = "BUY",
        price = BigDecimal("25.0"),
        quantity = BigDecimal("10.0"),
        account_id = "buyer-fiat-account",
        status = "active",
        created_at = "2026-04-16T00:30:00Z",
        updated_at = "2026-04-16T00:30:00Z"
      ),
      List(OrderNotFound, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(getMarketOrder)
    )

    // Route: DELETE /obp/v7.0.0/market/orders/ORDER_ID
    val cancelMarketOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "market" / "orders" / orderId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (order, callContext) <- NewStyle.function.cancelMarketOrder(orderId, Some(cc))
          } yield JSONFactory700.createMarketOrderJson(order)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(cancelMarketOrder),
      "DELETE",
      "/market/orders/ORDER_ID",
      "Cancel Market Order",
      """Cancel an active market order.
        |
        |This operation is idempotent - canceling an already-cancelled order returns success.
        |
        |Authentication is required.""",
      EmptyBody,
      JSONFactory700.MarketOrderJson(
        order_id = "550e8400-e29b-41d4-a716-446655440000",
        side = "BUY",
        price = BigDecimal("25.0"),
        quantity = BigDecimal("10.0"),
        account_id = "buyer-fiat-account",
        status = "cancelled",
        created_at = "2026-04-16T00:30:00Z",
        updated_at = "2026-04-16T00:35:00Z"
      ),
      List(OrderNotFound, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(cancelMarketOrder)
    )

    // Route: POST /obp/v7.0.0/market/matches
    val createMarketMatch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "market" / "matches" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.CreateMarketMatchRequestJson, JSONFactory700.MarketMatchJson](req) { (user, createMatchJson, cc) =>
          for {
            // Validate amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidMatchParameters,
              failCode = 400,
              cc = Some(cc)
            )(createMatchJson.amount > 0)

            // Validate price
            _ <- Helper.booleanToFuture(
              failMsg = InvalidMatchParameters,
              failCode = 400,
              cc = Some(cc)
            )(createMatchJson.price > 0)

            // Invoke connector
            (matchResult, callContext) <- NewStyle.function.createMarketMatch(
              createMatchJson.order_id,
              createMatchJson.counter_order_id,
              createMatchJson.amount,
              createMatchJson.price,
              Some(cc)
            )
          } yield JSONFactory700.createMarketMatchJson(matchResult)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createMarketMatch),
      "POST",
      "/market/matches",
      "Create Market Match",
      """Create a match between two market orders.
        |
        |This creates a MarketMatch and automatically generates a corresponding MarketTrade.
        |
        |Authentication is required.""",
      JSONFactory700.CreateMarketMatchRequestJson(
        order_id = "order-123",
        counter_order_id = "order-456",
        amount = BigDecimal("5.0"),
        price = BigDecimal("25.0")
      ),
      JSONFactory700.MarketMatchJson(
        match_id = "match-789",
        order_id = "order-123",
        counter_order_id = "order-456",
        amount = BigDecimal("5.0"),
        price = BigDecimal("25.0"),
        created_at = "2026-04-16T00:40:00Z"
      ),
      List(InvalidJsonFormat, InvalidMatchParameters, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(createMarketMatch)
    )

    // Route: GET /obp/v7.0.0/market/trades/TRADE_ID
    val getMarketTrade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "market" / "trades" / tradeId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (trade, callContext) <- NewStyle.function.getMarketTrade(tradeId, Some(cc))
          } yield JSONFactory700.createMarketTradeJson(trade)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getMarketTrade),
      "GET",
      "/market/trades/TRADE_ID",
      "Get Market Trade",
      """Get details of a specific market trade.
        |
        |Authentication is required.""",
      EmptyBody,
      JSONFactory700.MarketTradeJson(
        trade_id = "trade-789",
        buy_order_id = "order-123",
        sell_order_id = "order-456",
        amount = BigDecimal("5.0"),
        price = BigDecimal("25.0"),
        status = "pending",
        created_at = "2026-04-16T00:40:00Z"
      ),
      List(TradeNotFound, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(getMarketTrade)
    )

    // Route: POST /obp/v7.0.0/market/settlements
    val requestSettlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "market" / "settlements" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.RequestSettlementJson, JSONFactory700.SettlementJson](req) { (user, requestJson, cc) =>
          for {
            // Invoke connector
            (settlement, callContext) <- NewStyle.function.requestSettlement(
              requestJson.trade_id,
              requestJson.step,
              Some(cc)
            )
          } yield JSONFactory700.createSettlementJson(settlement)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(requestSettlement),
      "POST",
      "/market/settlements",
      "Request Settlement",
      """Request settlement for a completed trade.
        |
        |Authentication is required.""",
      JSONFactory700.RequestSettlementJson(
        trade_id = "trade-789",
        step = Some("step1")
      ),
      JSONFactory700.SettlementJson(
        settlement_id = "settlement-101",
        trade_id = "trade-789",
        step = Some("step1"),
        status = "pending",
        created_at = "2026-04-16T00:45:00Z",
        completed_at = None
      ),
      List(InvalidJsonFormat, SettlementFailed, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(requestSettlement)
    )

    // Route: POST /obp/v7.0.0/market/deposits
    val notifyDeposit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "market" / "deposits" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.NotifyDepositJson, JSONFactory700.DepositJson](req) { (user, depositJson, cc) =>
          for {
            // Validate amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = Some(cc)
            )(depositJson.amount > 0)

            // Validate confirmations
            _ <- Helper.booleanToFuture(
              failMsg = InvalidMatchParameters,
              failCode = 400,
              cc = Some(cc)
            )(depositJson.confirmations >= 0)

            // Invoke connector
            (deposit, callContext) <- NewStyle.function.notifyDeposit(
              depositJson.tx_hash,
              depositJson.from,
              depositJson.to,
              depositJson.amount,
              depositJson.confirmations,
              Some(cc)
            )
          } yield JSONFactory700.createDepositJson(deposit)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(notifyDeposit),
      "POST",
      "/market/deposits",
      "Notify Deposit",
      """Record a blockchain deposit notification.
        |
        |Authentication is required.""",
      JSONFactory700.NotifyDepositJson(
        tx_hash = "0x123abc",
        from = "0xsender",
        to = "0xreceiver",
        amount = BigDecimal("100.0"),
        confirmations = 6
      ),
      JSONFactory700.DepositJson(
        deposit_id = "deposit-202",
        tx_hash = "0x123abc",
        from = "0xsender",
        to = "0xreceiver",
        amount = BigDecimal("100.0"),
        confirmations = 6,
        status = "confirmed",
        created_at = "2026-04-16T00:50:00Z"
      ),
      List(InvalidJsonFormat, InvalidTradingAmount, InvalidMatchParameters, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(notifyDeposit)
    )

    // Route: POST /obp/v7.0.0/market/withdrawals
    val requestWithdrawal: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "market" / "withdrawals" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.RequestWithdrawalJson, JSONFactory700.WithdrawalJson](req) { (user, withdrawalJson, cc) =>
          for {
            // Validate amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = Some(cc)
            )(withdrawalJson.amount > 0)

            // Invoke connector
            (withdrawal, callContext) <- NewStyle.function.requestWithdrawal(
              withdrawalJson.account_id,
              withdrawalJson.amount,
              withdrawalJson.address,
              withdrawalJson.idempotency_key,
              Some(cc)
            )
          } yield JSONFactory700.createWithdrawalJson(withdrawal)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(requestWithdrawal),
      "POST",
      "/market/withdrawals",
      "Request Withdrawal",
      """Request a withdrawal to a blockchain address.
        |
        |This operation is idempotent via the idempotency_key.
        |
        |Authentication is required.""",
      JSONFactory700.RequestWithdrawalJson(
        account_id = "account-123",
        amount = BigDecimal("50.0"),
        address = "0xdestination",
        idempotency_key = "withdrawal-456"
      ),
      JSONFactory700.WithdrawalJson(
        withdrawal_id = "withdrawal-303",
        account_id = "account-123",
        amount = BigDecimal("50.0"),
        address = "0xdestination",
        status = "pending",
        tx_hash = None,
        created_at = "2026-04-16T00:55:00Z"
      ),
      List(InvalidJsonFormat, InvalidTradingAmount, WithdrawalFailed, $AuthenticatedUserIsRequired, UnknownError),
      apiTagMarket :: Nil,
      http4sPartialFunction = Some(requestWithdrawal)
    )

    // ── End Market Endpoints (Phase 2) ─────────────────────────────────────

    // All routes combined (without middleware - for direct use).
    //
    // Routes are sorted automatically by URL template specificity (segment count,
    // descending) derived from each ResourceDoc's requestUrl. This guarantees
    // most-specific-first ordering without manual maintenance — adding a new
    // ResourceDoc with http4sPartialFunction places it correctly at startup.
    //
    // Two routes with equal segment count keep declaration order (stable sort).
    // If two equal-length routes could ever conflict, add an explicit tiebreaker
    // by giving the higher-priority route more segments (e.g. use a literal
    // segment instead of a variable).
    //
    // REQUIREMENT: each `val endpoint` must be declared BEFORE its `resourceDocs +=`
    // so that `Some(endpoint)` captures the initialized route, not null.
    val allRoutes: HttpRoutes[IO] = {
      val sorted = resourceDocs
        .sortBy(rd => -rd.requestUrl.split("/").count(_.nonEmpty))
        .flatMap(_.http4sPartialFunction)
      sorted.foldLeft(HttpRoutes.empty[IO]) { (acc, route) =>
        HttpRoutes[IO](req => acc.run(req).orElse(route.run(req)))
      }
    }

    // Routes wrapped with ResourceDocMiddleware for automatic validation
    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
  }

  // Routes with ResourceDocMiddleware - provides automatic validation based on ResourceDoc metadata
  // Authentication is automatic based on $AuthenticatedUserIsRequired in ResourceDoc errorResponseBodies
  // This matches Lift's wrappedWithAuthCheck behavior
  val wrappedRoutesV700Services: HttpRoutes[IO] = Implementations7_0_0.allRoutesWithMiddleware
}
