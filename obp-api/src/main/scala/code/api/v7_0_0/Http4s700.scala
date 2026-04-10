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
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
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

    // ── End Phase 1 batch 2 ──────────────────────────────────────────────────

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
