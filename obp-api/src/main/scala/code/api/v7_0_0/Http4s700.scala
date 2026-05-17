package code.api.v7_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResourceDocs1_4_0.{ResourceDocs140, ResourceDocsAPIMethodsUtil}
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.{APIUtil, ApiRole, ApiVersionUtils, CallContext, CustomJsonFormats, Glossary, NewStyle}
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canCreateEntitlementAtOneBank, canCreateOrganisation, canCreateRoutingScheme, canDeleteEntitlementAtAnyBank, canDeleteOrganisation, canDeleteRoutingScheme, canGetAccountAccessTrace, canGetAnyOrganisation, canGetAnyUser, canGetCacheConfig, canGetCacheInfo, canGetCacheNamespaces, canGetConnectorHealth, canGetCustomersAtOneBank, canGetDatabasePoolInfo, canGetMigrations, canUpdateBankSupportedRoutingScheme, canUpdateOrganisation, canUpdateRoutingScheme, canUpdateSystemView}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.{ErrorResponseConverter, Http4sRequestAttributes, IdempotencyMiddleware, RequestScopeConnection, ResourceDocMiddleware}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.ViewNewStyle
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v2_0_0.{BasicViewJson, CreateEntitlementJSON, JSONFactory200}
import code.api.v4_0_0.JSONFactory400
import code.api.v6_0_0.{BasicAccountJsonV600, BasicAccountsJsonV600, BankJsonV600, CacheConfigJsonV600, CacheInfoJsonV600, CacheNamespaceInfoJsonV600, CacheNamespaceJsonV600, CacheNamespacesJsonV600, ConnectorInfoJsonV600, ConnectorsJsonV600, DatabasePoolInfoJsonV600, FeaturesJsonV600, InMemoryCacheStatusJsonV600, JSONFactory600, RedisCacheStatusJsonV600, StoredProcedureConnectorHealthJsonV600, UserV600}
import code.api.v6_0_0.JSONFactory600.ViewJsonV600
import code.api.cache.Redis
import code.bankconnectors.storedprocedure.StoredProcedureUtils
import code.migration.MigrationScriptLogProvider
import code.bankconnectors.{Connector => BankConnector}
import code.entitlement.Entitlement
import code.organisation.Organisations
import code.routingscheme.{RoutingSchemes, RoutingSchemeValidation}
import code.payeelookup.PayeeLookups
import code.bulkpayment.{BulkPaymentHandler, BulkPayments}
import code.transactionrequests.MappedTransactionRequestProvider
import com.openbankproject.commons.model.TransactionRequestCharge
import code.metadata.tags.Tags
import code.views.Views
import code.accountattribute.AccountAttributeX
import code.users.{Users => UserVend}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, CounterpartyId, CustomerId, ListResult, TransactionRequestType, ViewId}
import com.openbankproject.commons.model.enums.ChallengeType
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

  /* 
   * IMPORTANT: Endpoint Exclusion Pattern
   * 
   * excludeEndpoints is used to filter out old endpoints when v7.0.0 has a DIFFERENT URL pattern.
   * 
   * WHEN TO EXCLUDE:
   * - Old and new endpoints have DIFFERENT URLs (e.g., v4.0.0: /users/:username vs v7.0.0: /providers/:provider/users/:username)
   * - The old endpoint should not be accessible via v7.0.0 at all
   * 
   * WHEN NOT TO EXCLUDE:
   * - Old and new endpoints have the SAME URL and HTTP method (e.g., GET /api/versions)
   * - In this case, collectResourceDocs() automatically deduplicates by (URL, method) and keeps newest version
   * - Excluding by function name would remove BOTH versions since they share the same name!
   * 
   * Why? The routing works as follows:
   * 1. allResourceDocs = collectResourceDocs() deduplicates docs by (URL, method), keeps newest
   * 2. excludeEndpoints filters ResourceDocs by partialFunctionName (removes by name, not by version)
   * 3. The filtered docs determine which endpoints are available
   * 
   * Pattern: Add nameOf(Implementations{version}.endpointName) :: with a comment explaining why
   * 
   * NOTE: Currently empty - no v7-specific exclusions have been identified yet.
   * As v7.0.0 introduces endpoints with different URL patterns than previous versions,
   * add those old endpoint names here with explanatory comments.
   */
  lazy val excludeEndpoints: List[String] = 
    // Add exclusions here when v7.0.0 replaces old endpoints with different URLs
    // Example: nameOf(Implementations3_0_0.getUserByUsername) :: // v7.0.0 uses /providers/:provider/users/:username
    Nil

  /**
   * Aggregated resource docs from all API versions (v7.0.0 + v6.0.0 + v5.1.0 + ... + v1.3.0)
   * 
   * This method implements the resource docs aggregation pattern for v7.0.0:
   * 1. Takes OBPAPI6_0_0.allResourceDocs (which already contains v6.0.0 + v5.1.0 + ... + v1.3.0)
   * 2. Adds v7.0.0's own resourceDocs
   * 3. Deduplicates by (requestUrl, requestVerb), keeping the newest version
   * 4. Filters out explicitly excluded old endpoints
   * 
   * Note: We cannot extend OBPRestHelper (Lift framework) in Http4s700 (Http4s framework)
   * due to type incompatibilities. Instead, we implement the collectResourceDocs logic inline.
   * 
   * The deduplication algorithm:
   * - Sort all docs by API version (descending: v7.0.0, v6.0.0, v5.1.0, ...)
   * - For each doc, check if (requestUrl, requestVerb) has been seen
   * - If not seen, add to result (this keeps the newest version)
   * - If seen, skip (this omits older versions of the same endpoint)
   * 
   * Performance: Computed once and cached (lazy val) to avoid recomputation on every request.
   */
  lazy val allResourceDocs: ArrayBuffer[ResourceDoc] = {
    // Import v6.0.0's aggregated docs (v6.0.0 + v5.1.0 + ... + v1.3.0)
    import code.api.v6_0_0.OBPAPI6_0_0
    
    // Combine v6.0.0's aggregated docs with v7.0.0's docs
    val allDocs = OBPAPI6_0_0.allResourceDocs ++ resourceDocs
    
    // Deduplicate by (requestUrl, requestVerb), keeping newest version
    // Sort by API version (descending) so newer versions come first
    implicit val ordering = new Ordering[ScannedApiVersion] {
      override def compare(x: ScannedApiVersion, y: ScannedApiVersion): Int = 
        y.toString().compareTo(x.toString())
    }
    
    val sortedDocs = allDocs.sortBy(_.implementedInApiVersion)
    
    val result = ArrayBuffer[ResourceDoc]()
    val urlAndMethods = scala.collection.mutable.Set[(String, String)]()
    
    for (doc <- sortedDocs) {
      val urlAndMethod = (doc.requestUrl, doc.requestVerb)
      if (!urlAndMethods.contains(urlAndMethod)) {
        urlAndMethods.add(urlAndMethod)
        result += doc
      }
    }
    
    // Filter out explicitly excluded old endpoints
    if (excludeEndpoints.isEmpty) {
      result
    } else {
      result.filterNot(it => it.partialFunctionName.matches(excludeEndpoints.mkString("|")))
    }
  }

  object Implementations7_0_0 extends code.util.Helper.MdcLoggable {

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
            allDocs = {
              val raw = ResourceDocs140.ImplementationsResourceDocs.getResourceDocsList(requestedApiVersion).getOrElse(Nil)
              val seen = scala.collection.mutable.HashSet[(String, String)]()
              raw.filter(doc => seen.add((doc.requestVerb, doc.requestUrl)))
            }
            filteredDocs = ResourceDocsAPIMethodsUtil.filterResourceDocs(allDocs, tags, functions)
          } yield JSONFactory1_4_0.createResourceDocsJson(filteredDocs, isVersion4OrHigher = true, localeParam, includeTechnology = true)
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
            _ <- Helper.booleanToFuture(failMsg = EntitlementAlreadyExists, failCode = 409, cc = Some(cc))(
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

    // ── Account Access Trace ────────────────────────────────────────────────
    //
    // Path uses TARGET_VIEW_ID and TARGET_USER_ID (not VIEW_ID / USER_ID) on
    // purpose: the middleware's VIEW_ID validation runs an access check on the
    // CALLING user, which is wrong for a diagnostic that asks about ANOTHER user.
    // The caller's authority comes from CanGetAccountAccessTrace.
    val getAccountAccessTrace: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" / targetViewIdStr / "users" / targetUserIdStr / "account-access-trace" =>
        EndpointHelpers.withBankAccount(req) { (_, account, cc) =>
          val bankIdAccountId = BankIdAccountId(account.bankId, account.accountId)
          val targetViewId    = ViewId(targetViewIdStr)
          for {
            // Validate target view exists (custom or system)
            _ <- {
              Views.views.vend.customViewFuture(targetViewId, bankIdAccountId).flatMap {
                case Full(v) => Future.successful(Full(v))
                case _       => Views.views.vend.systemViewFuture(targetViewId)
              }
            }.map(unboxFullOrFail(_, Some(cc), s"$ViewNotFound Current ViewId is $targetViewIdStr"))

            // Validate target user exists
            targetUser <- UserVend.users.vend.getUserByUserIdFuture(targetUserIdStr).map(
              x => unboxFullOrFail(x, Some(cc), s"$UserNotFoundByUserId Current USER_ID($targetUserIdStr)", 404)
            )

            // Step A — AccountAccess trace
            permissions      <- Future(Views.views.vend.permissions(bankIdAccountId))
            targetUserPerm    = permissions.find(_.user.userId == targetUser.userId)
            accountAccessViewIds   = targetUserPerm.toList.flatMap(_.views.map(_.viewId.value))
            hasAccountAccessForView = accountAccessViewIds.contains(targetViewIdStr)

            // Step B — Entitlement trace (mirrors APIUtil.checkAbacAccountAccess gate)
            entitlementBox        = Entitlement.entitlement.vend.getEntitlement("", targetUser.userId, ApiRole.canExecuteAbacRule.toString)
            hasCanExecuteAbacRule = entitlementBox.isDefined

            // Step C — ABAC per-rule trace (lists ALL rules under the policy, active or not)
            allRules     = code.abacrule.MappedAbacRuleProvider.getAbacRulesByPolicy(ABAC_POLICY_ACCOUNT_ACCESS)
            ruleTraces  <- Future.sequence(allRules.map { rule =>
              if (!rule.isActive) {
                Future.successful(JSONFactory700.AbacRuleTraceJsonV700(
                  rule_id = rule.abacRuleId, rule_name = rule.ruleName,
                  is_active = false, result = "SKIPPED",
                  error_message = Some("Rule is not active")
                ))
              } else {
                code.abacrule.AbacRuleEngine.executeRule(
                  ruleId = rule.abacRuleId,
                  authenticatedUserId = targetUser.userId,
                  callContext = cc,
                  bankId  = Some(account.bankId.value),
                  accountId = Some(account.accountId.value),
                  viewId  = Some(targetViewIdStr)
                ).map {
                  case Full(true)  => JSONFactory700.AbacRuleTraceJsonV700(rule.abacRuleId, rule.ruleName, true, "PASS", None)
                  case Full(false) => JSONFactory700.AbacRuleTraceJsonV700(rule.abacRuleId, rule.ruleName, true, "FAIL", None)
                  case net.liftweb.common.Failure(msg, _, _) =>
                                      JSONFactory700.AbacRuleTraceJsonV700(rule.abacRuleId, rule.ruleName, true, "ERROR", Some(msg))
                  case _           => JSONFactory700.AbacRuleTraceJsonV700(rule.abacRuleId, rule.ruleName, true, "ERROR", Some("empty result"))
                }.recover { case ex =>
                  JSONFactory700.AbacRuleTraceJsonV700(rule.abacRuleId, rule.ruleName, true, "ERROR", Some(ex.getMessage))
                }
              }
            })

            allowAbacProp     = APIUtil.getPropsAsBoolValue("allow_abac_account_access", false)
            anyRulePassed     = ruleTraces.exists(_.result == "PASS")
            // Mirrors enforcement: prop ON + entitlement + at least one PASS
            standaloneAbacResult  = allowAbacProp && hasCanExecuteAbacRule && anyRulePassed

            hasAccess     = hasAccountAccessForView || standaloneAbacResult
            accessSource       =
              if      (hasAccountAccessForView) "ACCOUNT_ACCESS"
              else if (standaloneAbacResult)        "ABAC"
              else                              "NONE"
          } yield {
            JSONFactory700.AccountAccessTraceJsonV700(
              user_id      = targetUser.userId,
              bank_id      = account.bankId.value,
              account_id   = account.accountId.value,
              view_id      = targetViewIdStr,
              has_access      = hasAccess,
              access_source = accessSource,
              account_access_trace = JSONFactory700.AccountAccessLookupJsonV700(
                has_account_access_for_view = hasAccountAccessForView,
                account_access_view_ids = accountAccessViewIds
              ),
              entitlement_trace = JSONFactory700.EntitlementTraceJsonV700(
                has_can_execute_abac_rule = hasCanExecuteAbacRule
              ),
              abac_trace = JSONFactory700.AbacEvaluationTraceJsonV700(
                policy = ABAC_POLICY_ACCOUNT_ACCESS,
                allow_abac_account_access = allowAbacProp,
                standalone_abac_result = standaloneAbacResult,
                rules_evaluated = ruleTraces
              )
            )
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getAccountAccessTrace),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/TARGET_VIEW_ID/users/TARGET_USER_ID/account-access-trace",
      "Get Account Access Trace",
      s"""Return a diagnostic trace of how a target user's access to a view on an account is decided.
        |Use this for auditing, debugging "why doesn't user X have access?" support tickets, and
        |verifying ABAC rule behaviour against real users.
        |
        |Top-level verdict:
        |
        |* `has_access` — does the target user have access to the named view?
        |* `access_source` — what actually decided: `"ACCOUNT_ACCESS"` | `"ABAC"` | `"NONE"`.
        |  Use this (not `standalone_abac_result`) to answer "did ABAC grant this user's access?"
        |
        |Three diagnostic sections:
        |
        |* `account_access_trace` — what the AccountAccess table says: which views the target user
        |  holds on this account, and whether the asked view is among them.
        |* `entitlement_trace` — whether the target user has the `CanExecuteAbacRule` entitlement
        |  (the runtime opt-in for ABAC fallback).
        |* `abac_trace` — the ABAC subsystem evaluated standalone: master prop value, the standalone
        |  verdict, and each active rule under the `account-access` policy with result
        |  `PASS` / `FAIL` / `ERROR` / `SKIPPED`.
        |
        |Path uses `TARGET_VIEW_ID` and `TARGET_USER_ID` (not `VIEW_ID` / `USER_ID`) because the
        |trace asks about another user, not the caller. The caller's authority to read this comes
        |from `CanGetAccountAccessTrace`.
        |
        |Diagnostic only — does not affect enforcement. For the full runtime gate model, see
        |${Glossary.getGlossaryItemLink("ABAC_Account_Access_Enforcement")}.
        |
        |Authentication is Required.""".stripMargin,
      EmptyBody,
      JSONFactory700.AccountAccessTraceJsonV700(
        user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        bank_id = "gh.29.uk",
        account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
        view_id    = "owner",
        has_access      = true,
        access_source = "ACCOUNT_ACCESS",
        account_access_trace = JSONFactory700.AccountAccessLookupJsonV700(
          has_account_access_for_view = true,
          account_access_view_ids = List("owner")
        ),
        entitlement_trace = JSONFactory700.EntitlementTraceJsonV700(
          has_can_execute_abac_rule = false
        ),
        abac_trace = JSONFactory700.AbacEvaluationTraceJsonV700(
          policy = ABAC_POLICY_ACCOUNT_ACCESS,
          allow_abac_account_access = false,
          standalone_abac_result = false,
          rules_evaluated = Nil
        )
      ),
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, ViewNotFound, UserNotFoundByUserId, UnknownError),
      apiTagABAC :: apiTagAccount :: apiTagView :: Nil,
      Some(List(canGetAccountAccessTrace)),
      http4sPartialFunction = Some(getAccountAccessTrace)
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

    // Route: GET /obp/v7.0.0/api/error-messages
    val getErrorMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "error-messages" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(ListResult("error_messages", JSONFactory700.errorMessagesCatalog))
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getErrorMessages),
      "GET",
      "/api/error-messages",
      "Get Error Messages",
      """Returns the catalog of OBP error codes and messages defined in this API instance.
        |
        |Each entry has the OBP error code (e.g. `OBP-00001`), the internal name of the
        |constant, and the human-readable message text.
        |
        |The catalog is derived by reflecting over `ErrorMessages` at first access and
        |cached for the lifetime of the server.
        |
        |No Authentication is Required.""".stripMargin,
      EmptyBody,
      ListResult(
        "error_messages",
        List(JSONFactory700.ErrorMessageEntryJsonV700(
          code    = "OBP-00001",
          name    = "HostnameNotSpecified",
          message = "Hostname not specified. Could not get hostname from Props."
        ))
      ),
      List(UnknownError),
      apiTagDocumentation :: apiTagApi :: Nil,
      http4sPartialFunction = Some(getErrorMessages)
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
            rows <- UserVend.users.vend.getUsersV600F(obpQueryParams)
          } yield JSONFactory600.createUsersInfoJsonV600(rows)
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
      """**WORK IN PROGRESS**
        |
        |Create a new trading offer to buy or sell digital assets.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-15T10:30:00Z",
        updated_at = "2026-04-15T10:30:00Z"
      ),
      List(InvalidJsonFormat, InvalidOfferType, InvalidTradingAmount, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagTrade :: Nil,
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
      """**WORK IN PROGRESS**
        |
        |Get details of a specific trading offer including execution history.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-15T10:30:00Z",
        updated_at = "2026-04-15T10:30:00Z"
      ),
      List(OfferNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagTrade :: Nil,
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
      """**WORK IN PROGRESS**
        |
        |Get a list of trading offers for a specific account.
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
            user_id = "user-abc-123",
            consent_id = None,
            created_at = "2026-04-15T10:30:00Z",
            updated_at = "2026-04-15T10:30:00Z"
          )
        )
      ),
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagTrade :: Nil,
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
      """**WORK IN PROGRESS**
        |
        |Cancel an active trading offer.
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
      apiTagTrading :: apiTagTrade :: Nil,
      http4sPartialFunction = Some(cancelTradingOffer)
    )


    // ── End Phase 1 batch 2 ──────────────────────────────────────────────────

    // ── Market Endpoints (Phase 2) ─────────────────────────────────────────

    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/orders
    val createMarketOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "orders" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.CreateMarketOrderRequestJson, JSONFactory700.MarketOrderJson](req) { (user, createOrderJson, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Validate side
            _ <- Helper.booleanToFuture(
              failMsg = InvalidOrderSide,
              failCode = 400,
              cc = callContext
            )(createOrderJson.side == "BUY" || createOrderJson.side == "SELL")

            // Validate price
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = callContext
            )(createOrderJson.price > 0)

            // Validate quantity
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = callContext
            )(createOrderJson.quantity > 0)

            // Invoke connector
            (order, callContext2) <- NewStyle.function.createMarketOrder(
              BankId(bankId),
              AccountId(accountId),
              createOrderJson.side,
              createOrderJson.price,
              createOrderJson.quantity,
              createOrderJson.settlement_account_id,
              callContext
            )
          } yield JSONFactory700.createMarketOrderJson(order)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createMarketOrder),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/orders",
      "Create Market Order",
      """**WORK IN PROGRESS**
        |
        |Create a new market order to buy or sell assets.
        |
        |The order will be matched against existing orders in the order book.
        |The order_id is automatically generated as a UUID.
        |Each request creates a new order with a unique order_id.
        |
        |Authentication is required.""",
      JSONFactory700.CreateMarketOrderRequestJson(
        side = "BUY",
        price = BigDecimal("25.0"),
        quantity = BigDecimal("10.0"),
        settlement_account_id = "buyer-fiat-account"
      ),
      JSONFactory700.MarketOrderJson(
        order_id = "550e8400-e29b-41d4-a716-446655440000",
        side = "BUY",
        price = BigDecimal("25.0"),
        quantity = BigDecimal("10.0"),
        account_id = "buyer-fiat-account",
        status = "active",
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:30:00Z",
        updated_at = "2026-04-16T00:30:00Z"
      ),
      List(InvalidJsonFormat, InvalidOrderSide, InvalidTradingAmount, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(createMarketOrder)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/orders/ORDER_ID
    val getMarketOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "orders" / orderId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Get order
            (order, callContext2) <- NewStyle.function.getMarketOrder(
              BankId(bankId),
              AccountId(accountId),
              orderId,
              callContext
            )
          } yield JSONFactory700.createMarketOrderJson(order)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getMarketOrder),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/orders/ORDER_ID",
      "Get Market Order",
      """**WORK IN PROGRESS**
        |
        |Get details of a specific market order.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:30:00Z",
        updated_at = "2026-04-16T00:30:00Z"
      ),
      List(OrderNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(getMarketOrder)
    )

    // Route: DELETE /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/orders/ORDER_ID
    val cancelMarketOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "orders" / orderId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Cancel order
            (order, callContext2) <- NewStyle.function.cancelMarketOrder(
              BankId(bankId),
              AccountId(accountId),
              orderId,
              callContext
            )
          } yield JSONFactory700.createMarketOrderJson(order)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(cancelMarketOrder),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/orders/ORDER_ID",
      "Cancel Market Order",
      """**WORK IN PROGRESS**
        |
        |Cancel an active market order.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:30:00Z",
        updated_at = "2026-04-16T00:35:00Z"
      ),
      List(OrderNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(cancelMarketOrder)
    )

    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/matches
    val createMarketMatch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "matches" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.CreateMarketMatchRequestJson, JSONFactory700.MarketMatchJson](req) { (user, createMatchJson, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Validate amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidMatchParameters,
              failCode = 400,
              cc = callContext
            )(createMatchJson.amount > 0)

            // Validate price
            _ <- Helper.booleanToFuture(
              failMsg = InvalidMatchParameters,
              failCode = 400,
              cc = callContext
            )(createMatchJson.price > 0)

            // Invoke connector
            (matchResult, callContext2) <- NewStyle.function.createMarketMatch(
              BankId(bankId),
              AccountId(accountId),
              createMatchJson.order_id,
              createMatchJson.counter_order_id,
              createMatchJson.amount,
              createMatchJson.price,
              callContext
            )
          } yield JSONFactory700.createMarketMatchJson(matchResult)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createMarketMatch),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/matches",
      "Create Market Match",
      """**WORK IN PROGRESS**
        |
        |Create a match between two market orders.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:40:00Z"
      ),
      List(InvalidJsonFormat, InvalidMatchParameters, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(createMarketMatch)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/trades/TRADE_ID
    val getMarketTrade: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "trades" / tradeId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Get trade
            (trade, callContext2) <- NewStyle.function.getMarketTrade(
              BankId(bankId),
              AccountId(accountId),
              tradeId,
              callContext
            )
          } yield JSONFactory700.createMarketTradeJson(trade)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getMarketTrade),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/trades/TRADE_ID",
      "Get Market Trade",
      """**WORK IN PROGRESS**
        |
        |Get details of a specific market trade.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:40:00Z"
      ),
      List(TradeNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(getMarketTrade)
    )

    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/settlements
    val requestSettlement: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "settlements" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.RequestSettlementJson, JSONFactory700.SettlementJson](req) { (user, requestJson, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Invoke connector
            (settlement, callContext2) <- NewStyle.function.requestSettlement(
              BankId(bankId),
              AccountId(accountId),
              requestJson.trade_id,
              requestJson.step,
              callContext
            )
          } yield JSONFactory700.createSettlementJson(settlement)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(requestSettlement),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/settlements",
      "Request Settlement",
      """**WORK IN PROGRESS**
        |
        |Request settlement for a completed trade.
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
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:45:00Z",
        completed_at = None
      ),
      List(InvalidJsonFormat, SettlementFailed, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(requestSettlement)
    )

    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/deposits
//    val notifyDeposit: HttpRoutes[IO] = HttpRoutes.of[IO] {
//      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "deposits" =>
//        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.NotifyDepositJson, JSONFactory700.DepositJson](req) { (user, depositJson, cc) =>
//          for {
//            // Validate bank and account
//            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))
//
//            // Validate amount
//            _ <- Helper.booleanToFuture(
//              failMsg = InvalidTradingAmount,
//              failCode = 400,
//              cc = callContext
//            )(depositJson.amount > 0)
//
//            // Validate confirmations
//            _ <- Helper.booleanToFuture(
//              failMsg = InvalidMatchParameters,
//              failCode = 400,
//              cc = callContext
//            )(depositJson.confirmations >= 0)
//
//            // Invoke connector
//            (deposit, callContext2) <- NewStyle.function.notifyDeposit(
//              BankId(bankId),
//              AccountId(accountId),
//              depositJson.tx_hash,
//              depositJson.from,
//              depositJson.to,
//              depositJson.amount,
//              depositJson.confirmations,
//              12,  // Ethereum mainnet standard: 12 confirmations required
//              callContext
//            )
//          } yield JSONFactory700.createDepositJson(deposit)
//        }
//    }
//
//    resourceDocs += ResourceDoc(
//      null,
//      implementedInApiVersion,
//      nameOf(notifyDeposit),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/deposits",
//      "Notify Deposit",
//      """**WORK IN PROGRESS**
//        |
//        |Record a blockchain deposit notification.
//        |
//        |Authentication is required.""",
//      JSONFactory700.NotifyDepositJson(
//        tx_hash = "0x123abc",
//        from = "0xsender",
//        to = "0xreceiver",
//        amount = BigDecimal("100.0"),
//        confirmations = 6
//      ),
//      JSONFactory700.DepositJson(
//        deposit_id = "deposit-202",
//        tx_hash = "0x123abc",
//        from = "0xsender",
//        to = "0xreceiver",
//        amount = BigDecimal("100.0"),
//        confirmations = 6,
//        required_confirmations = 12,
//        status = "pending",
//        nonce = Some(123456L),
//        gas_used = Some(21000L),
//        error_message = None,
//        user_id = "user-abc-123",
//        consent_id = None,
//        created_at = "2026-04-16T00:50:00Z"
//      ),
//      List(InvalidJsonFormat, InvalidTradingAmount, InvalidMatchParameters, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
//      apiTagTrading :: apiTagMarket :: Nil,
//      http4sPartialFunction = Some(notifyDeposit)
//    )

    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/withdrawals
    val requestWithdrawal: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "withdrawals" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.RequestWithdrawalJson, JSONFactory700.WithdrawalJson](req) { (user, withdrawalJson, cc) =>
          for {
            // Validate bank and account
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))

            // Validate amount
            _ <- Helper.booleanToFuture(
              failMsg = InvalidTradingAmount,
              failCode = 400,
              cc = callContext
            )(withdrawalJson.amount > 0)

            // Invoke connector
            (withdrawal, callContext2) <- NewStyle.function.requestWithdrawal(
              BankId(bankId),
              AccountId(accountId),
              withdrawalJson.settlement_account_id,
              withdrawalJson.amount,
              withdrawalJson.address,
              12,  // Ethereum mainnet standard: 12 confirmations required
              callContext
            )
          } yield JSONFactory700.createWithdrawalJson(withdrawal)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(requestWithdrawal),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/withdrawals",
      "Request Withdrawal",
      """**WORK IN PROGRESS**
        |
        |Request a withdrawal to a blockchain address.
        |
        |The withdrawal_id is automatically generated as a UUID.
        |Each request creates a new withdrawal with a unique withdrawal_id.
        |
        |Authentication is required.""",
      JSONFactory700.RequestWithdrawalJson(
        settlement_account_id = "account-123",
        amount = BigDecimal("50.0"),
        address = "0xdestination"
      ),
      JSONFactory700.WithdrawalJson(
        withdrawal_id = "withdrawal-303",
        account_id = "account-123",
        amount = BigDecimal("50.0"),
        address = "0xdestination",
        status = "pending",
        tx_hash = None,
        confirmations = None,
        required_confirmations = 12,
        nonce = None,
        gas_used = None,
        error_message = None,
        user_id = "user-abc-123",
        consent_id = None,
        created_at = "2026-04-16T00:55:00Z"
      ),
      List(InvalidJsonFormat, InvalidTradingAmount, WithdrawalFailed, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
      apiTagTrading :: apiTagMarket :: Nil,
      http4sPartialFunction = Some(requestWithdrawal)
    )

//    // ── TCC Payment Authorization Endpoints (Phase 3 - P3) ─────────────────
//
//    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths
//    val createPaymentAuth: HttpRoutes[IO] = HttpRoutes.of[IO] {
//      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "payment-auths" =>
//        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.CreatePaymentAuthRequestJson, JSONFactory700.PaymentAuthJson](req) { (user, createAuthJson, cc) =>
//          for {
//            // Validate bank and account
//            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))
//            
//            // Validate amount
//            _ <- Helper.booleanToFuture(
//              failMsg = InvalidTradingAmount,
//              failCode = 400,
//              cc = callContext
//            )(createAuthJson.amount_fiat > 0)
//            
//            // Invoke connector to create payment authorization (PREAUTH state)
//            (auth, callContext2) <- NewStyle.function.createPaymentAuth(
//              BankId(bankId),
//              AccountId(accountId),
//              createAuthJson.trade_id,
//              createAuthJson.buyer_account_id,
//              createAuthJson.seller_account_id,
//              createAuthJson.amount_fiat,
//              createAuthJson.currency,
//              callContext
//            )
//          } yield JSONFactory700.createPaymentAuthJson(auth)
//        }
//    }
//
//    resourceDocs += ResourceDoc(
//      null,
//      implementedInApiVersion,
//      nameOf(createPaymentAuth),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths",
//      "Create Payment Authorization (TCC Preauth)",
//      """**WORK IN PROGRESS**
//        |
//        |Create a payment authorization for a trade settlement using the Try-Confirm-Cancel (TCC) pattern.
//        |
//        |This creates a PREAUTH state authorization that freezes funds for the trade.
//        |The auth_id is automatically generated as a UUID.
//        |
//        |TCC Flow:
//        |- PREAUTH: Funds are frozen (this endpoint)
//        |- CAPTURED: Funds are actually deducted (capture endpoint)
//        |- RELEASED: Funds are unfrozen/refunded (release endpoint)
//        |
//        |Authentication is required.""",
//      JSONFactory700.CreatePaymentAuthRequestJson(
//        trade_id = "trade-789",
//        buyer_account_id = "buyer-account-456",
//        seller_account_id = "seller-account-789",
//        amount_fiat = BigDecimal("1000.0"),
//        currency = "EUR"
//      ),
//      JSONFactory700.PaymentAuthJson(
//        auth_id = "auth-101",
//        trade_id = "trade-789",
//        buyer_account_id = "buyer-account-456",
//        seller_account_id = "seller-account-789",
//        amount_fiat = BigDecimal("1000.0"),
//        currency = "EUR",
//        state = "PREAUTH",
//        hold_id = None,
//        error_message = None,
//        user_id = "user-abc-123",
//        consent_id = None,
//        created_at = "2026-04-17T10:00:00Z",
//        updated_at = "2026-04-17T10:00:00Z"
//      ),
//      List(InvalidJsonFormat, InvalidTradingAmount, CreatePaymentAuthError, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
//      apiTagTrading :: apiTagMarket :: Nil,
//      http4sPartialFunction = Some(createPaymentAuth)
//    )
//
//    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths/AUTH_ID/capture
//    val capturePaymentAuth: HttpRoutes[IO] = HttpRoutes.of[IO] {
//      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "payment-auths" / authId / "capture" =>
//        EndpointHelpers.withUser(req) { (user, cc) =>
//          for {
//            // Validate bank and account
//            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))
//            
//            // Invoke connector to capture payment (PREAUTH → CAPTURED)
//            (auth, callContext2) <- NewStyle.function.capturePaymentAuth(
//              BankId(bankId),
//              AccountId(accountId),
//              authId,
//              callContext
//            )
//          } yield JSONFactory700.createPaymentAuthJson(auth)
//        }
//    }
//
//    resourceDocs += ResourceDoc(
//      null,
//      implementedInApiVersion,
//      nameOf(capturePaymentAuth),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths/AUTH_ID/capture",
//      "Capture Payment Authorization (TCC Confirm)",
//      """**WORK IN PROGRESS**
//        |
//        |Capture a payment authorization to complete the trade settlement.
//        |
//        |This transitions the authorization from PREAUTH to CAPTURED state.
//        |Funds are actually deducted from the buyer's account.
//        |
//        |Only PREAUTH state authorizations can be captured.
//        |
//        |Authentication is required.""",
//      EmptyBody,
//      JSONFactory700.PaymentAuthJson(
//        auth_id = "auth-101",
//        trade_id = "trade-789",
//        buyer_account_id = "buyer-account-456",
//        seller_account_id = "seller-account-789",
//        amount_fiat = BigDecimal("1000.0"),
//        currency = "EUR",
//        state = "CAPTURED",
//        hold_id = None,
//        error_message = None,
//        user_id = "user-abc-123",
//        consent_id = None,
//        created_at = "2026-04-17T10:00:00Z",
//        updated_at = "2026-04-17T10:05:00Z"
//      ),
//      List(PaymentAuthNotFound, InvalidPaymentAuthState, PaymentAuthAlreadyCaptured, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
//      apiTagTrading :: apiTagMarket :: Nil,
//      http4sPartialFunction = Some(capturePaymentAuth)
//    )
//
//    // Route: POST /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths/AUTH_ID/release
//    val releasePaymentAuth: HttpRoutes[IO] = HttpRoutes.of[IO] {
//      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "payment-auths" / authId / "release" =>
//        EndpointHelpers.withUser(req) { (user, cc) =>
//          for {
//            // Validate bank and account
//            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))
//            
//            // Invoke connector to release payment (PREAUTH/CAPTURED → RELEASED)
//            (auth, callContext2) <- NewStyle.function.releasePaymentAuth(
//              BankId(bankId),
//              AccountId(accountId),
//              authId,
//              callContext
//            )
//          } yield JSONFactory700.createPaymentAuthJson(auth)
//        }
//    }
//
//    resourceDocs += ResourceDoc(
//      null,
//      implementedInApiVersion,
//      nameOf(releasePaymentAuth),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths/AUTH_ID/release",
//      "Release Payment Authorization (TCC Cancel)",
//      """**WORK IN PROGRESS**
//        |
//        |Release a payment authorization to cancel the trade settlement.
//        |
//        |This transitions the authorization to RELEASED state.
//        |Frozen funds are unfrozen (if PREAUTH) or refunded (if CAPTURED).
//        |
//        |Both PREAUTH and CAPTURED state authorizations can be released.
//        |
//        |Authentication is required.""",
//      EmptyBody,
//      JSONFactory700.PaymentAuthJson(
//        auth_id = "auth-101",
//        trade_id = "trade-789",
//        buyer_account_id = "buyer-account-456",
//        seller_account_id = "seller-account-789",
//        amount_fiat = BigDecimal("1000.0"),
//        currency = "EUR",
//        state = "RELEASED",
//        hold_id = None,
//        error_message = None,
//        user_id = "user-abc-123",
//        consent_id = None,
//        created_at = "2026-04-17T10:00:00Z",
//        updated_at = "2026-04-17T10:10:00Z"
//      ),
//      List(PaymentAuthNotFound, InvalidPaymentAuthState, PaymentAuthAlreadyReleased, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
//      apiTagTrading :: apiTagMarket :: Nil,
//      http4sPartialFunction = Some(releasePaymentAuth)
//    )
//
//    // Route: GET /obp/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths/AUTH_ID
//    val getPaymentAuth: HttpRoutes[IO] = HttpRoutes.of[IO] {
//      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId / "market" / "payment-auths" / authId =>
//        EndpointHelpers.withUser(req) { (user, cc) =>
//          for {
//            // Validate bank and account
//            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), Some(cc))
//            
//            // Invoke connector to get payment authorization
//            (auth, callContext2) <- NewStyle.function.getPaymentAuth(
//              BankId(bankId),
//              AccountId(accountId),
//              authId,
//              callContext
//            )
//          } yield JSONFactory700.createPaymentAuthJson(auth)
//        }
//    }
//
//    resourceDocs += ResourceDoc(
//      null,
//      implementedInApiVersion,
//      nameOf(getPaymentAuth),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/market/payment-auths/AUTH_ID",
//      "Get Payment Authorization",
//      """**WORK IN PROGRESS**
//        |
//        |Get details of a payment authorization.
//        |
//        |Returns the current state and details of the authorization.
//        |
//        |Authentication is required.""",
//      EmptyBody,
//      JSONFactory700.PaymentAuthJson(
//        auth_id = "auth-101",
//        trade_id = "trade-789",
//        buyer_account_id = "buyer-account-456",
//        seller_account_id = "seller-account-789",
//        amount_fiat = BigDecimal("1000.0"),
//        currency = "EUR",
//        state = "PREAUTH",
//        hold_id = None,
//        error_message = None,
//        user_id = "user-abc-123",
//        consent_id = None,
//        created_at = "2026-04-17T10:00:00Z",
//        updated_at = "2026-04-17T10:00:00Z"
//      ),
//      List(PaymentAuthNotFound, $AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UnknownError),
//      apiTagTrading :: apiTagMarket :: Nil,
//      http4sPartialFunction = Some(getPaymentAuth)
//    )

    // ── End Market Endpoints (Phase 2) ─────────────────────────────────────

    // ── Phase 1 batch 3 — system endpoints ──────────────────────────────────

    // Route: GET /obp/v7.0.0/system/cache/config
    val getCacheConfig: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "cache" / "config" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future.successful(JSONFactory600.createCacheConfigJsonV600())
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCacheConfig),
      "GET",
      "/system/cache/config",
      "Get Cache Configuration",
      """Returns cache configuration including Redis status, in-memory cache status, instance ID, environment and global prefix.""",
      EmptyBody,
      CacheConfigJsonV600(
        redis_status = RedisCacheStatusJsonV600(available = true, url = "127.0.0.1", port = 6379, use_ssl = false),
        in_memory_status = InMemoryCacheStatusJsonV600(available = true, current_size = 42),
        instance_id = "obp",
        environment = "dev",
        global_prefix = "obp_dev_"
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetCacheConfig)),
      http4sPartialFunction = Some(getCacheConfig)
    )

    // Route: GET /obp/v7.0.0/system/cache/info
    val getCacheInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "cache" / "info" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future.successful(JSONFactory600.createCacheInfoJsonV600())
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCacheInfo),
      "GET",
      "/system/cache/info",
      "Get Cache Information",
      """Returns detailed cache information for all namespaces including key counts, TTL info and storage location.""",
      EmptyBody,
      CacheInfoJsonV600(
        namespaces = List(CacheNamespaceInfoJsonV600(
          namespace_id = "call_counter",
          prefix = "obp_dev_call_counter_1_",
          current_version = 1,
          key_count = 42,
          description = "Rate limit call counters",
          category = "Rate Limiting",
          storage_location = "redis",
          ttl_info = "range 60s to 86400s (avg 3600s)"
        )),
        total_keys = 42,
        redis_available = true
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetCacheInfo)),
      http4sPartialFunction = Some(getCacheInfo)
    )

    // Route: GET /obp/v7.0.0/system/database/pool
    val getDatabasePoolInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "database" / "pool" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future.successful(JSONFactory600.createDatabasePoolInfoJsonV600())
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getDatabasePoolInfo),
      "GET",
      "/system/database/pool",
      "Get Database Pool Information",
      """Returns HikariCP connection pool information including active/idle connections, pool size and timeouts.""",
      EmptyBody,
      DatabasePoolInfoJsonV600(
        pool_name = "HikariPool-1",
        active_connections = 5,
        idle_connections = 3,
        total_connections = 8,
        threads_awaiting_connection = 0,
        maximum_pool_size = 10,
        minimum_idle = 2,
        connection_timeout_ms = 30000,
        idle_timeout_ms = 600000,
        max_lifetime_ms = 1800000,
        keepalive_time_ms = 0
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetDatabasePoolInfo)),
      http4sPartialFunction = Some(getDatabasePoolInfo)
    )

    // Route: GET /obp/v7.0.0/system/connectors/stored_procedure_vDec2019/health
    val getStoredProcedureConnectorHealth: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "connectors" / "stored_procedure_vDec2019" / "health" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future {
            val health = StoredProcedureUtils.getHealth()
            StoredProcedureConnectorHealthJsonV600(
              status = health.status,
              server_name = health.serverName,
              server_ip = health.serverIp,
              database_name = health.databaseName,
              response_time_ms = health.responseTimeMs,
              error_message = health.errorMessage
            )
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getStoredProcedureConnectorHealth),
      "GET",
      "/system/connectors/stored_procedure_vDec2019/health",
      "Get Stored Procedure Connector Health",
      """Returns health status of the stored procedure connector including connection status, server name and response time.""",
      EmptyBody,
      StoredProcedureConnectorHealthJsonV600(
        status = "ok",
        server_name = Some("DBSERVER01"),
        server_ip = Some("10.0.1.50"),
        database_name = Some("obp_adapter"),
        response_time_ms = 45,
        error_message = None
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagConnector :: apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetConnectorHealth)),
      http4sPartialFunction = Some(getStoredProcedureConnectorHealth)
    )

    // Route: GET /obp/v7.0.0/system/migrations
    val getMigrations: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "migrations" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future {
            val migrations = MigrationScriptLogProvider.migrationScriptLogProvider.vend.getMigrationScriptLogs()
            JSONFactory600.createMigrationScriptLogsJsonV600(migrations)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getMigrations),
      "GET",
      "/system/migrations",
      "Get Database Migrations",
      """Get all database migration script logs. Returns information about all migration scripts that have been executed or attempted.""",
      EmptyBody,
      migrationScriptLogsJsonV600,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetMigrations)),
      http4sPartialFunction = Some(getMigrations)
    )

    // Route: GET /obp/v7.0.0/system/cache/namespaces
    val getCacheNamespaces: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "cache" / "namespaces" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          Future {
            val namespaces = List(
              (Constant.CALL_COUNTER_PREFIX,                   "Rate limiting counters per consumer and time period", "varies",                                             "Rate Limiting"),
              (Constant.RATE_LIMIT_ACTIVE_PREFIX,              "Active rate limit configurations",                   Constant.RATE_LIMIT_ACTIVE_CACHE_TTL.toString,        "Rate Limiting"),
              (Constant.LOCALISED_RESOURCE_DOC_PREFIX,         "Localized resource documentation",                  Constant.CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL.toString, "Resource Documentation"),
              (Constant.DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, "Dynamic resource documentation",                    Constant.GET_DYNAMIC_RESOURCE_DOCS_TTL.toString,      "Resource Documentation"),
              (Constant.STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX,  "Static resource documentation",                    Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString,       "Resource Documentation"),
              (Constant.ALL_RESOURCE_DOC_CACHE_KEY_PREFIX,     "All resource documentation",                        Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString,       "Resource Documentation"),
              (Constant.STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX,   "Swagger documentation",                            Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString,       "Resource Documentation"),
              (Constant.CONNECTOR_PREFIX,                      "Connector method names and metadata",               "3600",                                                "Connector"),
              (Constant.METRICS_STABLE_PREFIX,                 "Stable metrics (historical)",                       "86400",                                               "Metrics"),
              (Constant.METRICS_RECENT_PREFIX,                 "Recent metrics",                                    "7",                                                   "Metrics"),
              (Constant.ABAC_RULE_PREFIX,                      "ABAC rule cache",                                   "indefinite",                                          "ABAC")
            ).map { case (prefix, description, ttl, category) =>
              JSONFactory600.createCacheNamespaceJsonV600(
                prefix, description, ttl, category,
                Redis.countKeys(s"${prefix}*"),
                Redis.getSampleKey(s"${prefix}*")
              )
            }
            JSONFactory600.createCacheNamespacesJsonV600(namespaces)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCacheNamespaces),
      "GET",
      "/system/cache/namespaces",
      "Get Cache Namespaces",
      """Returns information about all cache namespaces in the system including key counts, TTL and example keys.""",
      EmptyBody,
      CacheNamespacesJsonV600(List(
        CacheNamespaceJsonV600(
          prefix = "obp_dev_call_counter_1_",
          description = "Rate limiting counters per consumer and time period",
          ttl_seconds = "varies",
          category = "Rate Limiting",
          key_count = 42,
          example_key = "obp_dev_call_counter_1_consumer123_PER_MINUTE"
        )
      )),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagCache :: apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetCacheNamespaces)),
      http4sPartialFunction = Some(getCacheNamespaces)
    )

    // ── End Phase 1 batch 3 ──────────────────────────────────────────────────

    // ── Organisations ─────────────────────────────────────────────────────────
    // CRUD for the Organisation resource. Migrated from v6.0.0 (Lift) to v7.0.0
    // (http4s). Path uses ORGANISATION_ID; not resolved by middleware (only BANK_ID
    // / ACCOUNT_ID / VIEW_ID / COUNTERPARTY_ID are), so endpoints fetch directly
    // via Organisations.organisation.vend.

    private val ValidOrganisationStatuses    = Set("active", "suspended", "archived")
    private val ValidOrganisationVisibilities = Set("public", "unlisted", "private")
    private val OrganisationIdRegex          = "^[a-zA-Z0-9._-]{2,64}$".r

    val createOrganisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "organisations" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.PostOrganisationJsonV700, JSONFactory700.OrganisationJsonV700](req) { (user, body, cc) =>
          for {
            _ <- Helper.booleanToFuture(InvalidOrganisationIdFormat, 400, Some(cc)) {
              OrganisationIdRegex.findFirstIn(body.organisation_id).isDefined
            }
            status     = body.status.getOrElse("active")
            visibility = body.visibility.getOrElse("public")
            _ <- Helper.booleanToFuture(InvalidOrganisationStatus, 400, Some(cc)) {
              ValidOrganisationStatuses.contains(status)
            }
            _ <- Helper.booleanToFuture(InvalidOrganisationVisibility, 400, Some(cc)) {
              ValidOrganisationVisibilities.contains(visibility)
            }
            existing <- Future(Organisations.organisation.vend.getOrganisation(body.organisation_id))
            _ <- Helper.booleanToFuture(OrganisationAlreadyExists, 409, Some(cc))(existing.isEmpty)
            created <- Future {
              Organisations.organisation.vend.createOrganisation(
                body.organisation_id, body.name, body.website, body.logo_url,
                status, visibility, user.userId
              )
            }.map(unboxFullOrFail(_, Some(cc), CreateOrganisationError, 400))
          } yield JSONFactory700.createOrganisationJsonV700(created)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createOrganisation),
      "POST",
      "/organisations",
      "Create Organisation",
      """Create an Organisation.
        |
        |The organisation_id must be a URL-safe string (a-z, A-Z, 0-9, '-', '.', '_'), between 2 and 64 characters in length, and is immutable.
        |
        |Optional fields:
        |- status: one of active, suspended, archived (defaults to active)
        |- visibility: one of public, unlisted, private (defaults to public)
        |- website, logo_url
        |
        |Authentication is Required.""".stripMargin,
      JSONFactory700.PostOrganisationJsonV700(
        organisation_id = "tesobe",
        name = "TESOBE GmbH",
        website = Some("https://www.tesobe.com"),
        logo_url = None,
        status = Some("active"),
        visibility = Some("public")
      ),
      JSONFactory700.OrganisationJsonV700(
        organisation_id = "tesobe",
        name = "TESOBE GmbH",
        website = Some("https://www.tesobe.com"),
        logo_url = None,
        status = "active",
        visibility = "public",
        created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        created_at = new java.util.Date(),
        updated_at = new java.util.Date()
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
           InvalidOrganisationIdFormat, InvalidOrganisationStatus,
           InvalidOrganisationVisibility, OrganisationAlreadyExists,
           CreateOrganisationError, UnknownError),
      apiTagOrganisation :: Nil,
      Some(List(canCreateOrganisation)),
      http4sPartialFunction = Some(createOrganisation)
    )

    val getOrganisations: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "organisations" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            allOrgs <- Organisations.organisation.vend.getAllOrganisations()
              .map(unboxFullOrFail(_, Some(cc), UnknownError, 500))
            hasGetAny = APIUtil.hasEntitlement("", user.userId, canGetAnyOrganisation)
            visible   = if (hasGetAny) allOrgs else allOrgs.filter(_.visibility == "public")
          } yield JSONFactory700.createOrganisationsJsonV700(visible)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getOrganisations),
      "GET",
      "/organisations",
      "Get Organisations",
      """Returns Organisations.
        |
        |By default returns only Organisations whose visibility is `public`. Users granted CanGetAnyOrganisation see all Organisations including unlisted and private.
        |
        |Authentication is Required.""".stripMargin,
      EmptyBody,
      JSONFactory700.OrganisationsJsonV700(organisations = List(
        JSONFactory700.OrganisationJsonV700(
          organisation_id = "tesobe",
          name = "TESOBE GmbH",
          website = Some("https://www.tesobe.com"),
          logo_url = None,
          status = "active",
          visibility = "public",
          created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
          created_at = new java.util.Date(),
          updated_at = new java.util.Date()
        )
      )),
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagOrganisation :: Nil,
      None,
      http4sPartialFunction = Some(getOrganisations)
    )

    val getOrganisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "organisations" / organisationId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            org <- Future(Organisations.organisation.vend.getOrganisation(organisationId))
              .map(unboxFullOrFail(_, Some(cc), OrganisationNotFound, 404))
            _ <- if (org.visibility == "private")
                   NewStyle.function.hasEntitlement("", user.userId, canGetAnyOrganisation, Some(cc))
                 else Future.successful(())
          } yield JSONFactory700.createOrganisationJsonV700(org)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getOrganisation),
      "GET",
      "/organisations/ORGANISATION_ID",
      "Get Organisation",
      """Returns the Organisation specified by ORGANISATION_ID.
        |
        |Organisations with visibility `public` or `unlisted` are visible to any authenticated user. Organisations with visibility `private` require CanGetAnyOrganisation.
        |
        |Authentication is Required.""".stripMargin,
      EmptyBody,
      JSONFactory700.OrganisationJsonV700(
        organisation_id = "tesobe",
        name = "TESOBE GmbH",
        website = Some("https://www.tesobe.com"),
        logo_url = None,
        status = "active",
        visibility = "public",
        created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        created_at = new java.util.Date(),
        updated_at = new java.util.Date()
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound, UnknownError),
      apiTagOrganisation :: Nil,
      None,
      http4sPartialFunction = Some(getOrganisation)
    )

    val updateOrganisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "organisations" / organisationId =>
        EndpointHelpers.withUserAndBody[JSONFactory700.PutOrganisationJsonV700, JSONFactory700.OrganisationJsonV700](req) { (_, body, cc) =>
          for {
            _ <- Future(Organisations.organisation.vend.getOrganisation(organisationId))
              .map(unboxFullOrFail(_, Some(cc), OrganisationNotFound, 404))
            _ <- Helper.booleanToFuture(InvalidOrganisationStatus, 400, Some(cc)) {
              body.status.forall(ValidOrganisationStatuses.contains)
            }
            _ <- Helper.booleanToFuture(InvalidOrganisationVisibility, 400, Some(cc)) {
              body.visibility.forall(ValidOrganisationVisibilities.contains)
            }
            updated <- Future {
              Organisations.organisation.vend.updateOrganisation(
                organisationId, body.name, body.website, body.logo_url, body.status, body.visibility
              )
            }.map(unboxFullOrFail(_, Some(cc), UpdateOrganisationError, 400))
          } yield JSONFactory700.createOrganisationJsonV700(updated)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(updateOrganisation),
      "PUT",
      "/organisations/ORGANISATION_ID",
      "Update Organisation",
      """Update an Organisation. All body fields are optional. The organisation_id is immutable and cannot be changed.
        |
        |Authentication is Required.""".stripMargin,
      JSONFactory700.PutOrganisationJsonV700(
        name = Some("TESOBE GmbH"),
        website = Some("https://www.tesobe.com"),
        logo_url = None,
        status = Some("active"),
        visibility = Some("public")
      ),
      JSONFactory700.OrganisationJsonV700(
        organisation_id = "tesobe",
        name = "TESOBE GmbH",
        website = Some("https://www.tesobe.com"),
        logo_url = None,
        status = "active",
        visibility = "public",
        created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        created_at = new java.util.Date(),
        updated_at = new java.util.Date()
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
           OrganisationNotFound, InvalidOrganisationStatus,
           InvalidOrganisationVisibility, UpdateOrganisationError, UnknownError),
      apiTagOrganisation :: Nil,
      Some(List(canUpdateOrganisation)),
      http4sPartialFunction = Some(updateOrganisation)
    )

    val deleteOrganisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "organisations" / organisationId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            _ <- Future(Organisations.organisation.vend.getOrganisation(organisationId))
              .map(unboxFullOrFail(_, Some(cc), OrganisationNotFound, 404))
            _ <- Future(Organisations.organisation.vend.deleteOrganisation(organisationId))
              .map(unboxFullOrFail(_, Some(cc), DeleteOrganisationError, 400))
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(deleteOrganisation),
      "DELETE",
      "/organisations/ORGANISATION_ID",
      "Delete Organisation",
      """Delete the Organisation specified by ORGANISATION_ID.
        |
        |Authentication is Required.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, OrganisationNotFound,
           DeleteOrganisationError, UnknownError),
      apiTagOrganisation :: Nil,
      Some(List(canDeleteOrganisation)),
      http4sPartialFunction = Some(deleteOrganisation)
    )

    // ── End Organisations ─────────────────────────────────────────────────────

    // ── Routing Schemes ───────────────────────────────────────────────────────
    // A registry of country-qualified routing scheme names (e.g. TZ.MSISDN,
    // TZ.GEPG_CONTROL_NUMBER) so that downstream adapters and clients agree on
    // identifier scheme semantics. Two tiers:
    //   • /routing-schemes              — system catalogue (5 endpoints)
    //   • /banks/BANK_ID/supported-routing-schemes — per-bank subset (2 endpoints)
    // Scheme is the resource key. SCHEME segments may contain '.' — http4s
    // matches path segments by '/', not by '.', so "TZ.MSISDN" is a single
    // segment.

    val createRoutingScheme: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "routing-schemes" =>
        EndpointHelpers.withUserAndBodyCreated[JSONFactory700.PostRoutingSchemeJsonV700, JSONFactory700.RoutingSchemeJsonV700](req) { (user, body, cc) =>
          for {
            _ <- Helper.booleanToFuture(InvalidRoutingSchemeName, 400, Some(cc)) {
              RoutingSchemeValidation.isValidSchemeName(body.scheme)
            }
            _ <- Helper.booleanToFuture(RoutingSchemeCountryMismatch, 400, Some(cc)) {
              RoutingSchemeValidation.countryMatchesPrefix(body.scheme, body.country)
            }
            _ <- Helper.booleanToFuture(InvalidRoutingSchemeCategory, 400, Some(cc)) {
              RoutingSchemeValidation.ValidCategories.contains(body.category)
            }
            status = body.status.getOrElse("ACTIVE")
            _ <- Helper.booleanToFuture(InvalidRoutingSchemeStatus, 400, Some(cc)) {
              RoutingSchemeValidation.ValidStatuses.contains(status)
            }
            _ <- Helper.booleanToFuture(InvalidRoutingSchemeAddressPattern, 400, Some(cc)) {
              RoutingSchemeValidation.isValidRegex(body.address_pattern)
            }
            _ <- Helper.booleanToFuture(RoutingSchemeExampleAddressMismatch, 400, Some(cc)) {
              RoutingSchemeValidation.addressMatchesPattern(body.address_pattern, body.example_address)
            }
            existing <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(body.scheme))
            _ <- Helper.booleanToFuture(RoutingSchemeAlreadyExists, 409, Some(cc))(existing.isEmpty)
            created <- Future {
              RoutingSchemes.routingScheme.vend.createRoutingScheme(
                scheme = body.scheme,
                country = body.country,
                category = body.category,
                addressPattern = body.address_pattern,
                secondaryAddressPattern = body.secondary_address_pattern,
                exampleAddress = body.example_address,
                description = body.description,
                downstreamRails = body.downstream_rails.getOrElse(Nil),
                status = status,
                createdByUserId = user.userId
              )
            }.map(unboxFullOrFail(_, Some(cc), CreateRoutingSchemeError, 400))
          } yield JSONFactory700.createRoutingSchemeJsonV700(created)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createRoutingScheme),
      "POST",
      "/routing-schemes",
      "Create Routing Scheme",
      """Register a new routing scheme.
        |
        |Scheme names follow the convention `<COUNTRY>.<LOCAL_SCHEME>` — uppercase ISO 3166-1 alpha-2 country code, a dot, then an uppercase local scheme name (e.g. `TZ.MSISDN`, `TZ.GEPG_CONTROL_NUMBER`).
        |
        |Globally-unique schemes `IBAN`, `BIC`, `OBP` are accepted unprefixed; their `country` MUST be the literal `INT`.
        |
        |Categories: ACCOUNT, BANK, BRANCH, IDENTITY, BILL, UTILITY. The category constrains which OBP fields may carry a routing of this scheme.
        |
        |`address_pattern` is a regex used to validate addresses presented in this scheme. `example_address` MUST match the pattern.
        |
        |Authentication is Required.""".stripMargin,
      JSONFactory700.PostRoutingSchemeJsonV700(
        scheme = "TZ.MSISDN",
        country = "TZ",
        category = "ACCOUNT",
        address_pattern = "^255[0-9]{9}$",
        secondary_address_pattern = None,
        example_address = "255778300336",
        description = "Tanzanian mobile number, E.164 without leading +.",
        downstream_rails = Some(List("TIPS", "MNO_DIRECT")),
        status = Some("ACTIVE")
      ),
      JSONFactory700.RoutingSchemeJsonV700(
        scheme = "TZ.MSISDN",
        country = "TZ",
        category = "ACCOUNT",
        address_pattern = "^255[0-9]{9}$",
        secondary_address_pattern = None,
        example_address = "255778300336",
        description = "Tanzanian mobile number, E.164 without leading +.",
        downstream_rails = List("TIPS", "MNO_DIRECT"),
        status = "ACTIVE",
        created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        created_at = new java.util.Date(),
        updated_at = new java.util.Date()
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
           InvalidRoutingSchemeName, RoutingSchemeCountryMismatch,
           InvalidRoutingSchemeCategory, InvalidRoutingSchemeStatus,
           InvalidRoutingSchemeAddressPattern, RoutingSchemeExampleAddressMismatch,
           RoutingSchemeAlreadyExists, CreateRoutingSchemeError, UnknownError),
      apiTagRoutingScheme :: Nil,
      Some(List(canCreateRoutingScheme)),
      http4sPartialFunction = Some(createRoutingScheme)
    )

    val getRoutingSchemes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "routing-schemes" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          val q = req.uri.query.params
          val country  = q.get("country").filter(_.nonEmpty)
          val category = q.get("category").filter(_.nonEmpty)
          val rail     = q.get("rail").filter(_.nonEmpty)
          // Default to ACTIVE only; pass status=ALL to include retired/deprecated.
          val rawStatus = q.get("status").filter(_.nonEmpty).getOrElse("ACTIVE")
          val statusFilter = if (rawStatus.equalsIgnoreCase("ALL")) None else Some(rawStatus.toUpperCase)
          val limit  = q.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(100).max(1).min(500)
          val offset = q.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0).max(0)
          for {
            page <- RoutingSchemes.routingScheme.vend.getRoutingSchemes(country, category, statusFilter, rail, limit, offset)
              .map(unboxFullOrFail(_, Some(cc), UnknownError, 500))
            (rows, total) = page
          } yield JSONFactory700.createRoutingSchemesJsonV700(rows, total, limit, offset)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getRoutingSchemes),
      "GET",
      "/routing-schemes",
      "Get Routing Schemes",
      """Lists registered routing schemes.
        |
        |Query parameters (all optional):
        |- `country` — ISO 3166-1 alpha-2, e.g. `TZ`
        |- `category` — ACCOUNT, BANK, BRANCH, IDENTITY, BILL, UTILITY
        |- `status` — defaults to `ACTIVE`. Pass `ALL` to include DEPRECATED and RETIRED.
        |- `rail` — match against the `downstream_rails` list (e.g. `TIPS`, `GEPG`)
        |- `limit` (default 100, max 500), `offset` (default 0)""".stripMargin,
      EmptyBody,
      JSONFactory700.RoutingSchemesJsonV700(
        routing_schemes = List(
          JSONFactory700.RoutingSchemeSummaryJsonV700(
            scheme = "TZ.MSISDN", country = "TZ", category = "ACCOUNT",
            status = "ACTIVE", address_pattern = "^255[0-9]{9}$",
            example_address = "255778300336"
          )
        ),
        pagination = JSONFactory700.RoutingSchemePaginationJsonV700(total = 1, limit = 100, offset = 0)
      ),
      List(UnknownError),
      apiTagRoutingScheme :: Nil,
      None,
      http4sPartialFunction = Some(getRoutingSchemes)
    )

    val getRoutingScheme: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "routing-schemes" / schemeName =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            row <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(schemeName))
              .map(unboxFullOrFail(_, Some(cc), RoutingSchemeNotFound, 404))
          } yield JSONFactory700.createRoutingSchemeJsonV700(row)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getRoutingScheme),
      "GET",
      "/routing-schemes/SCHEME",
      "Get Routing Scheme",
      """Returns the routing scheme identified by `SCHEME` (e.g. `TZ.MSISDN`).""",
      EmptyBody,
      JSONFactory700.RoutingSchemeJsonV700(
        scheme = "TZ.MSISDN",
        country = "TZ",
        category = "ACCOUNT",
        address_pattern = "^255[0-9]{9}$",
        secondary_address_pattern = None,
        example_address = "255778300336",
        description = "Tanzanian mobile number, E.164 without leading +.",
        downstream_rails = List("TIPS", "MNO_DIRECT"),
        status = "ACTIVE",
        created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        created_at = new java.util.Date(),
        updated_at = new java.util.Date()
      ),
      List(RoutingSchemeNotFound, UnknownError),
      apiTagRoutingScheme :: Nil,
      None,
      http4sPartialFunction = Some(getRoutingScheme)
    )

    val updateRoutingScheme: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "routing-schemes" / schemeName =>
        EndpointHelpers.withUserAndBody[JSONFactory700.PutRoutingSchemeJsonV700, JSONFactory700.RoutingSchemeJsonV700](req) { (_, body, cc) =>
          for {
            existing <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(schemeName))
              .map(unboxFullOrFail(_, Some(cc), RoutingSchemeNotFound, 404))
            _ <- Helper.booleanToFuture(InvalidRoutingSchemeStatus, 400, Some(cc)) {
              body.status.forall(RoutingSchemeValidation.ValidStatuses.contains)
            }
            _ <- Helper.booleanToFuture(InvalidRoutingSchemeAddressPattern, 400, Some(cc)) {
              body.address_pattern.forall(RoutingSchemeValidation.isValidRegex)
            }
            // If either pattern or example is being updated, the post-update
            // pair must be consistent.
            effectivePattern = body.address_pattern.getOrElse(existing.addressPattern)
            effectiveExample = body.example_address.getOrElse(existing.exampleAddress)
            _ <- Helper.booleanToFuture(RoutingSchemeExampleAddressMismatch, 400, Some(cc)) {
              RoutingSchemeValidation.addressMatchesPattern(effectivePattern, effectiveExample)
            }
            updated <- Future {
              RoutingSchemes.routingScheme.vend.updateRoutingScheme(
                scheme = schemeName,
                addressPattern = body.address_pattern,
                secondaryAddressPattern = body.secondary_address_pattern,
                exampleAddress = body.example_address,
                description = body.description,
                downstreamRails = body.downstream_rails,
                status = body.status
              )
            }.map(unboxFullOrFail(_, Some(cc), UpdateRoutingSchemeError, 400))
          } yield JSONFactory700.createRoutingSchemeJsonV700(updated)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(updateRoutingScheme),
      "PUT",
      "/routing-schemes/SCHEME",
      "Update Routing Scheme",
      """Updates a routing scheme. All body fields are optional.
        |
        |Immutable fields (cannot be changed via this endpoint): `scheme`, `country`, `category`.
        |
        |If you tighten `address_pattern`, existing addresses already on the books are not retroactively rejected — the change applies only to new validations.
        |
        |Authentication is Required.""".stripMargin,
      JSONFactory700.PutRoutingSchemeJsonV700(
        address_pattern = Some("^255[0-9]{9}$"),
        secondary_address_pattern = None,
        example_address = Some("255778300336"),
        description = Some("Tanzanian mobile number, E.164 without leading +."),
        downstream_rails = Some(List("TIPS", "MNO_DIRECT")),
        status = Some("ACTIVE")
      ),
      JSONFactory700.RoutingSchemeJsonV700(
        scheme = "TZ.MSISDN",
        country = "TZ",
        category = "ACCOUNT",
        address_pattern = "^255[0-9]{9}$",
        secondary_address_pattern = None,
        example_address = "255778300336",
        description = "Tanzanian mobile number, E.164 without leading +.",
        downstream_rails = List("TIPS", "MNO_DIRECT"),
        status = "ACTIVE",
        created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
        created_at = new java.util.Date(),
        updated_at = new java.util.Date()
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
           RoutingSchemeNotFound, InvalidRoutingSchemeStatus,
           InvalidRoutingSchemeAddressPattern, RoutingSchemeExampleAddressMismatch,
           UpdateRoutingSchemeError, UnknownError),
      apiTagRoutingScheme :: Nil,
      Some(List(canUpdateRoutingScheme)),
      http4sPartialFunction = Some(updateRoutingScheme)
    )

    val deleteRoutingScheme: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "routing-schemes" / schemeName =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            _ <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(schemeName))
              .map(unboxFullOrFail(_, Some(cc), RoutingSchemeNotFound, 404))
            _ <- Future(RoutingSchemes.routingScheme.vend.deleteRoutingScheme(schemeName))
              .map(unboxFullOrFail(_, Some(cc), DeleteRoutingSchemeError, 400))
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(deleteRoutingScheme),
      "DELETE",
      "/routing-schemes/SCHEME",
      "Delete Routing Scheme",
      """Soft-deletes the routing scheme — sets its status to `RETIRED`. The row is kept for audit and resolution of historical records that reference it; subsequent attempts to use the scheme in a routing or payment fail with `OBP-30525`.
        |
        |Authentication is Required.""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, RoutingSchemeNotFound,
           DeleteRoutingSchemeError, UnknownError),
      apiTagRoutingScheme :: Nil,
      Some(List(canDeleteRoutingScheme)),
      http4sPartialFunction = Some(deleteRoutingScheme)
    )

    val getBankSupportedRoutingSchemes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "supported-routing-schemes" =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            rows <- RoutingSchemes.routingScheme.vend.getBankSupportedRoutingSchemes(bank.bankId.value)
              .map(unboxFullOrFail(_, Some(cc), UnknownError, 500))
          } yield JSONFactory700.createBankSupportedRoutingSchemesJsonV700(bank.bankId.value, rows)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBankSupportedRoutingSchemes),
      "GET",
      "/banks/BANK_ID/supported-routing-schemes",
      "Get Bank Supported Routing Schemes",
      """Returns the subset of routing schemes the bank's adapter routes for, with optional per-bank notes (e.g. cutoff times, downstream rail caveats).
        |
        |Use this to gate UI options: a transaction-request creation form should list payee-type choices based on what this bank supports, not the global registry.
        |
        |Authentication is Required.""".stripMargin,
      EmptyBody,
      JSONFactory700.BankSupportedRoutingSchemesJsonV700(
        bank_id = "nmb.tz",
        supported_routing_schemes = List(
          JSONFactory700.BankSupportedRoutingSchemeJsonV700(
            scheme = "TZ.MSISDN",
            bank_notes = Some("Routed via Gateway X to TIPS.")
          )
        )
      ),
      List($AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      apiTagRoutingScheme :: apiTagBank :: Nil,
      None,
      http4sPartialFunction = Some(getBankSupportedRoutingSchemes)
    )

    val putBankSupportedRoutingScheme: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "supported-routing-schemes" / schemeName =>
        EndpointHelpers.withUserAndBankAndBody[JSONFactory700.PutBankSupportedRoutingSchemeJsonV700, JSONFactory700.BankSupportedRoutingSchemeJsonV700](req) { (_, bank, body, cc) =>
          for {
            // Scheme must exist in the global registry (and not be retired)
            // before a bank can opt in / out of it.
            scheme <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(schemeName))
              .map(unboxFullOrFail(_, Some(cc), RoutingSchemeNotFound, 404))
            _ <- Helper.booleanToFuture(RoutingSchemeNotSupportedByBank, 400, Some(cc)) {
              scheme.status != "RETIRED"
            }
            row <- Future {
              RoutingSchemes.routingScheme.vend.putBankSupportedRoutingScheme(
                bankId = bank.bankId.value,
                scheme = schemeName,
                enabled = body.enabled.getOrElse(true),
                bankNotes = body.bank_notes
              )
            }.map(unboxFullOrFail(_, Some(cc), UpdateRoutingSchemeError, 400))
          } yield JSONFactory700.BankSupportedRoutingSchemeJsonV700(
            scheme = row.scheme,
            bank_notes = row.bankNotes
          )
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(putBankSupportedRoutingScheme),
      "PUT",
      "/banks/BANK_ID/supported-routing-schemes/SCHEME",
      "Set Bank Supported Routing Scheme",
      """Opt this bank in to (or out of) a registered routing scheme. Set `enabled: false` to opt out without losing the per-bank notes.
        |
        |The scheme must exist in the global registry (`GET /routing-schemes/SCHEME`) and not be RETIRED.
        |
        |Authentication is Required.""".stripMargin,
      JSONFactory700.PutBankSupportedRoutingSchemeJsonV700(
        bank_notes = Some("Routed via Gateway X to TIPS. Daily cutoff 22:00 EAT."),
        enabled = Some(true)
      ),
      JSONFactory700.BankSupportedRoutingSchemeJsonV700(
        scheme = "TZ.MSISDN",
        bank_notes = Some("Routed via Gateway X to TIPS. Daily cutoff 22:00 EAT.")
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
           BankNotFound, RoutingSchemeNotFound, RoutingSchemeNotSupportedByBank,
           UpdateRoutingSchemeError, UnknownError),
      apiTagRoutingScheme :: apiTagBank :: Nil,
      Some(List(canUpdateBankSupportedRoutingScheme)),
      http4sPartialFunction = Some(putBankSupportedRoutingScheme)
    )

    // ── End Routing Schemes ───────────────────────────────────────────────────

    // ── Payee Lookup ──────────────────────────────────────────────────────────
    // Generic "confirmation-of-payee" / pre-payment lookup. Caller supplies
    // an identifier { scheme, address } pair (e.g. {TZ.MSISDN, 255778300336});
    // endpoint resolves to a payee name and returns a short-lived lookup_id
    // that can be quoted in a subsequent transaction-request as evidence the
    // payer saw the resolved name. Auth perimeter is the source account's
    // view: the same view that lets you pay from this account lets you lookup
    // a payee.

    private val PayeeLookupValidCategories: Set[String] = Set("ACCOUNT", "BILL", "UTILITY")
    private val PayeeLookupTtlSeconds: Long = 600 // 10 minutes

    val createPayeeLookup: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "payees" / "lookup" =>
        EndpointHelpers.withViewAndBodyCreated[JSONFactory700.PostPayeeLookupJsonV700, JSONFactory700.PayeeLookupResponseJsonV700](req) { (user, bankAccount, _, body, cc) =>
          for {
            // 1. identifier.scheme must exist in the registry.
            scheme <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(body.identifier.scheme))
              .map(unboxFullOrFail(_, Some(cc), PayeeLookupIdentifierTypeNotRegistered, 400))
            // 2. Scheme must be in a payee-lookup-valid category.
            _ <- Helper.booleanToFuture(PayeeLookupIdentifierTypeWrongCategory, 400, Some(cc)) {
              PayeeLookupValidCategories.contains(scheme.category)
            }
            // 3. identifier.value must match the scheme's address_pattern.
            _ <- Helper.booleanToFuture(PayeeLookupAddressMismatch, 400, Some(cc)) {
              RoutingSchemeValidation.addressMatchesPattern(scheme.addressPattern, body.identifier.value)
            }
            // 4. Resolve payee. In mapped mode the destination account is
            //    located by its account_routing (scheme,address). In adapter
            //    mode the south-side connector handles this.
            payeeBox <- BankConnector.connector.vend
              .getBankAccountByRouting(None, body.identifier.scheme, body.identifier.value, Some(cc))
              .map(_._1)
            payeeAccount <- Future {
              unboxFullOrFail(payeeBox, Some(cc), PayeeNotFound, 404)
            }
            // 5. Persist a lookup record with a 10-minute TTL.
            lookupId = APIUtil.generateUUID()
            stored <- Future {
              PayeeLookups.payeeLookup.vend.createPayeeLookup(
                lookupId = lookupId,
                identifierType = body.identifier.scheme,
                identifier = body.identifier.value,
                fspId = body.identifier.fsp_id,
                networkProvider = None,
                fullName = payeeAccount.label,
                accountCategory = None,
                accountType = Some(payeeAccount.accountType),
                identityType = None,
                identityValue = None,
                fromBankId = bankAccount.bankId.value,
                fromAccountId = bankAccount.accountId.value,
                createdByUserId = user.userId,
                ttlSeconds = PayeeLookupTtlSeconds
              )
            }.map(unboxFullOrFail(_, Some(cc), PayeeLookupCreateError, 500))
          } yield JSONFactory700.PayeeLookupResponseJsonV700(
            lookup_id = stored.lookupId,
            expires_at = stored.expiresAt,
            identifier = JSONFactory700.QualifiedIdentifierJsonV700(
              scheme = stored.identifierType,
              value = stored.identifier,
              fsp_id = stored.fspId
            ),
            network_provider = stored.networkProvider,
            full_name = stored.fullName,
            account_category = stored.accountCategory,
            account_type = stored.accountType,
            identity = None
          )
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createPayeeLookup),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/payees/lookup",
      "Create Payee Lookup",
      """Look up a payee (Confirmation-of-Payee) before initiating a payment.
        |
        |The endpoint is **polymorphic on `identifier.scheme`**: pass any registered routing scheme as the `identifier.scheme` and the corresponding `identifier.value`. The scheme's `category` must be one of ACCOUNT, BILL, UTILITY for it to be valid here.
        |
        |The `identifier` is a `QualifiedIdentifier` — `scheme` and `value` travel as a pair because neither is meaningful on its own. Optionally include `fsp_id` (Financial Service Provider) for multi-FSP namespaces where the same value may live with different providers (e.g. TZ.MSISDN); for such namespaces `scheme + value` alone may not uniquely identify the wallet.
        |
        |Examples:
        |- Mobile-money / TIPS payee: `identifier: { scheme: TZ.MSISDN, value: 255778300336, fsp_id: 503 }`
        |- TIPS bank-account name verify: `identifier: { scheme: TZ.BANK_ACCOUNT, value: 24110000296 }`
        |- GePG bill inquiry: `identifier: { scheme: TZ.GEPG_CONTROL_NUMBER, value: 991043383705 }`
        |- Luku meter inquiry: `identifier: { scheme: TZ.LUKU_METER, value: 24730238417 }`
        |
        |The response includes a `lookup_id` valid for 10 minutes. A subsequent transaction-request can quote it via `verified_payee_lookup_id` to prove the payer saw the resolved name (Confirmation-of-Payee handshake).
        |
        |Authentication is Required. The caller must have a view on the source account (`/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID`) — the same authorization perimeter as paying from it.""".stripMargin,
      JSONFactory700.PostPayeeLookupJsonV700(
        identifier = JSONFactory700.QualifiedIdentifierJsonV700(
          scheme = "TZ.MSISDN", value = "255778300336", fsp_id = Some("503")
        )
      ),
      JSONFactory700.PayeeLookupResponseJsonV700(
        lookup_id = "lkp_01HXY7Z8AB9C0D1E2F3G4H5J6K",
        expires_at = new java.util.Date(System.currentTimeMillis() + 10L * 60 * 1000),
        identifier = JSONFactory700.QualifiedIdentifierJsonV700(
          scheme = "TZ.MSISDN", value = "255778300336", fsp_id = Some("503")
        ),
        network_provider = Some("ZANTEL"),
        full_name = "ERASTO EMILE MALEMA",
        account_category = Some("PERSON"),
        account_type = Some("WALLET"),
        identity = None
      ),
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
           PayeeLookupIdentifierTypeNotRegistered, PayeeLookupIdentifierTypeWrongCategory,
           PayeeLookupAddressMismatch, PayeeNotFound, PayeeLookupCreateError, UnknownError),
      apiTagPayee :: apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(createPayeeLookup)
    )

    // ── End Payee Lookup ──────────────────────────────────────────────────────

    // ── MOBILE_WALLET transaction request ─────────────────────────────────────
    // POST to a mobile-money wallet identified by an MSISDN. In mapped mode the
    // destination resolves via the country-qualified MSISDN routing scheme
    // (defaults to TZ.MSISDN; override via `country_code`). The endpoint plugs
    // into the existing v400 payment pipeline so the standard transaction-request
    // response shape is preserved.

    val createTransactionRequestMobileWallet: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" / "MOBILE_WALLET" / "transaction-requests" =>
        EndpointHelpers.withViewAndBodyCreated[JSONFactory700.TransactionRequestBodyMobileWalletJsonV700, JSONFactory700.TransactionRequestWithChargeMobileWalletJsonV700](req) { (user, fromAccount, view, body, cc) =>
          val countryCode = body.country_code.getOrElse("TZ")
          val msisdnScheme = s"${countryCode}.MSISDN"
          val chargePolicy = body.charge_policy.getOrElse("SHARED")
          val callCtx = Some(cc)
          for {
            // 1. The MSISDN routing scheme must exist in the registry and
            //    msisdn must match its address_pattern.
            scheme <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(msisdnScheme))
              .map(unboxFullOrFail(_, callCtx, PayeeLookupIdentifierTypeNotRegistered, 400))
            _ <- Helper.booleanToFuture(MobileWalletInvalidMsisdn, 400, callCtx) {
              RoutingSchemeValidation.addressMatchesPattern(scheme.addressPattern, body.to.msisdn)
            }
            // 2. If the caller provided a verified_payee_lookup_id, validate it
            //    is unexpired AND matches the supplied msisdn. This is the
            //    Confirmation-of-Payee handshake.
            _ <- body.verified_payee_lookup_id match {
              case Some(lkpId) =>
                for {
                  lkp <- Future(PayeeLookups.payeeLookup.vend.getActivePayeeLookup(lkpId))
                    .map(unboxFullOrFail(_, callCtx, PayeeLookupExpiredOrNotFound, 400))
                  _ <- Helper.booleanToFuture(PayeeLookupMismatch, 400, callCtx) {
                    lkp.identifier == body.to.msisdn && lkp.identifierType == msisdnScheme
                  }
                } yield ()
              case None => Future.successful(())
            }
            // 3. Resolve destination account via routing (mapped-mode path).
            destinationBox <- BankConnector.connector.vend
              .getBankAccountByRouting(None, msisdnScheme, body.to.msisdn, callCtx)
              .map(_._1)
            toAccount <- Future {
              unboxFullOrFail(destinationBox, callCtx, MobileWalletDestinationNotFound, 404)
            }
            // 4. Standard view authorisation check (same as v4 COUNTERPARTY).
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(
              view.viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), user, callCtx
            )
            // 5. Serialise the body to JSON for the connector's audit blob.
            detailsPlain = prettyRender(Extraction.decompose(body))
            // 6. Create the transaction request via the standard pipeline.
            txnReqType = TransactionRequestType("MOBILE_WALLET")
            (tr, _) <- NewStyle.function.createTransactionRequestv400(
              user,
              view.viewId,
              fromAccount,
              toAccount,
              txnReqType,
              body,
              detailsPlain,
              chargePolicy,
              Some(ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE),
              None,
              None,
              callCtx
            )
          } yield JSONFactory700.createTransactionRequestWithChargeMobileWalletJsonV700(tr, body, Nil, Nil)
        }
    }

    val mobileWalletBodyExample = JSONFactory700.TransactionRequestBodyMobileWalletJsonV700(
      to = JSONFactory700.MobileWalletToJsonV700(
        msisdn = "255778300336",
        fsp_id = Some("503"),
        network_provider = Some("AIRTEL"),
        full_name = Some("Chinua Achebe"),
        account_category = Some("PERSON"),
        account_type = Some("WALLET"),
        identity = None
      ),
      value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "TZS", amount = "1000"),
      description = "buy airtime",
      client_reference = Some("MK45078200"),
      verified_payee_lookup_id = None,
      country_code = Some("TZ"),
      data_fields = Some(List(JSONFactory700.MobileWalletDataFieldJsonV700("fieldName1", "fieldValue1"))),
      charge_policy = Some("SHARED")
    )

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createTransactionRequestMobileWallet),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/MOBILE_WALLET/transaction-requests",
      "Create Transaction Request (MOBILE_WALLET)",
      """Initiate a payment to a mobile-money wallet identified by an MSISDN (phone number).
        |
        |The destination wallet is resolved via the country-qualified MSISDN routing scheme — by default `TZ.MSISDN`; override via the `country_code` field. The scheme must be registered in the routing-scheme catalogue (`GET /obp/v7.0.0/routing-schemes/TZ.MSISDN`) and the wallet account must have a matching `account_routings` entry.
        |
        |**Confirmation-of-Payee handshake** (optional): call `POST /banks/.../accounts/.../payees/lookup` first, then pass the returned `lookup_id` here as `verified_payee_lookup_id`. The endpoint will reject the request if the lookup has expired or does not match the supplied `msisdn`.
        |
        |**Provider passthrough**: `data_fields` carries arbitrary name/value pairs that adapters can forward to the downstream MNO / TIPS rail without OBP interpretation.
        |
        |Authentication is Required.""".stripMargin,
      mobileWalletBodyExample,
      JSONFactory700.TransactionRequestWithChargeMobileWalletJsonV700(
        id = "4050046c-63b3-4868-8a22-14b4181d33a6",
        `type` = "MOBILE_WALLET",
        from = code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140(
          bank_id = "gh.29.uk",
          account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f1"
        ),
        details = mobileWalletBodyExample,
        transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
        status = "COMPLETED",
        start_date = code.api.util.APIUtil.DateWithDayExampleObject,
        end_date = code.api.util.APIUtil.DateWithDayExampleObject,
        challenges = Nil,
        charge = code.api.v2_0_0.TransactionRequestChargeJsonV200(
          summary = "Total charges for completed transaction",
          value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "TZS", amount = "0.00")
        ),
        attributes = None
      ),
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
           PayeeLookupIdentifierTypeNotRegistered, MobileWalletInvalidMsisdn,
           PayeeLookupExpiredOrNotFound, PayeeLookupMismatch,
           MobileWalletDestinationNotFound, MobileWalletPaymentError, UnknownError),
      apiTagTransactionRequest :: apiTagPayee :: Nil,
      None,
      http4sPartialFunction = Some(createTransactionRequestMobileWallet)
    )

    // ── End MOBILE_WALLET ─────────────────────────────────────────────────────

    // ── BULK transaction request ──────────────────────────────────────────────
    // One TransactionRequest with type=BULK serves as the envelope; N actual
    // Transactions (one per payment) are linked back to it via transaction_ids.
    // Per-payment outcomes live in BulkPayment so each result can be
    // mapped back to its end_to_end_id. Validation failures (unknown scheme,
    // bad address, missing destination) mark the individual payment FAILED but
    // do not abort the whole batch — matches how real CBS bulk processing
    // behaves. See BulkPaymentHandler for the orchestration.

    val createTransactionRequestBulk: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" / "BULK" / "transaction-requests" =>
        EndpointHelpers.withViewAndBodyCreated[JSONFactory700.TransactionRequestBodyBulkJsonV700, JSONFactory700.BulkTransactionRequestResponseJsonV700](req) { (user, fromAccount, view, body, cc) =>
          val callCtx = Some(cc)
          val chargePolicy = body.charge_policy.getOrElse("SHARED")
          for {
            // 1. Envelope-level validation (idempotency, size, currency, totals).
            _ <- BulkPaymentHandler.validateEnvelope(body, fromAccount, callCtx)
            // 2. Standard view-based authorisation check.
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(
              view.viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), user, callCtx
            )
            // 3. Create the parent BULK TR row. toAccount = self (envelope only;
            //    the real destinations live in the per-payment side-table).
            trId = APIUtil.generateUUID()
            detailsPlain = prettyRender(Extraction.decompose(body))
            parentTrBox = MappedTransactionRequestProvider.createTransactionRequestImpl210(
              com.openbankproject.commons.model.TransactionRequestId(trId),
              TransactionRequestType("BULK"),
              fromAccount,
              fromAccount,
              body,
              detailsPlain,
              "INITIATED",
              TransactionRequestCharge(
                "Bulk payment",
                com.openbankproject.commons.model.AmountOfMoney(fromAccount.currency, "0")
              ),
              chargePolicy,
              None, None, None, None,
              callCtx
            )
            _ <- Future {
              unboxFullOrFail(parentTrBox, callCtx, BulkPaymentTransactionRequestError, 500)
            }
            // 4. Claim the batch_reference for idempotency. After this, a
            //    second submission with the same batch_reference fails fast.
            _ <- Future {
              BulkPayments.bulkPayment.vend.claimBatchReference(
                fromAccount.bankId.value, fromAccount.accountId.value, body.batch_reference, trId
              )
            }
            // 5. Fan-out — sequential per-payment execution. Returns one row
            //    per input item (SUCCEEDED / FAILED + reason).
            itemRows <- BulkPaymentHandler.executeAllItems(body, fromAccount, trId, chargePolicy, callCtx)
            // 6. Roll up the parent status.
            rollupStatus = BulkPaymentHandler.computeStatus(itemRows)
            _ <- Future {
              MappedTransactionRequestProvider.saveTransactionRequestStatusImpl(
                com.openbankproject.commons.model.TransactionRequestId(trId), rollupStatus
              )
            }
            // 7. Read back the final TR with rolled-up status + transaction_ids.
            finalTr <- Future {
              unboxFullOrFail(
                MappedTransactionRequestProvider.getTransactionRequest(
                  com.openbankproject.commons.model.TransactionRequestId(trId)
                ),
                callCtx, BulkPaymentTransactionRequestError, 500
              )
            }
          } yield JSONFactory700.createBulkTransactionRequestResponseJsonV700(
            finalTr, body.batch_reference, itemRows
          )
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(createTransactionRequestBulk),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/BULK/transaction-requests",
      "Create Transaction Request (BULK)",
      """Submit a batch of payments against a single source account.
        |
        |Each item in `payments` is a heterogeneous payment instruction:
        |- `end_to_end_id` — caller-supplied unique reference (ISO 20022 convention). Must be unique within the batch.
        |- `to_account_routing.scheme` — any registered routing scheme of category `ACCOUNT` (e.g. `TZ.BANK_ACCOUNT`, `TZ.MSISDN`).
        |- `to_account_routing.address` — must match the scheme's `address_pattern`.
        |- `value` + `description` — per-payment amount and label. Currency must match the source account's currency.
        |
        |The envelope `value` must equal the sum of item amounts (caller declares the total; the server validates it).
        |
        |**Idempotency**: `batch_reference` is unique per (source account, batch). Re-submitting the same batch_reference returns `OBP-30536`.
        |
        |**Atomicity**: validation failures (unknown scheme, address mismatch, missing destination) mark the individual payment as `FAILED` and do not abort the batch. The TR-level `status` rolls up to `COMPLETED`, `PARTIALLY_COMPLETED`, or `FAILED` accordingly.
        |
        |**Maximum size**: `bulk_payments.max_items_per_batch` (default 1000).
        |
        |Authentication is Required.""".stripMargin,
      JSONFactory700.TransactionRequestBodyBulkJsonV700(
        batch_reference = "BATCH-2026-05-13-001",
        payments = List(
          JSONFactory700.BulkPaymentItemJsonV700(
            end_to_end_id = "E2E-0001",
            to_account_routing = com.openbankproject.commons.model.AccountRoutingJsonV121(
              scheme = "TZ.BANK_ACCOUNT", address = "24110000296"
            ),
            value = com.openbankproject.commons.model.AmountOfMoneyJsonV121("TZS", "50000.00"),
            description = "Payroll April 2026 — beneficiary 1"
          ),
          JSONFactory700.BulkPaymentItemJsonV700(
            end_to_end_id = "E2E-0002",
            to_account_routing = com.openbankproject.commons.model.AccountRoutingJsonV121(
              scheme = "TZ.MSISDN", address = "255778300336"
            ),
            value = com.openbankproject.commons.model.AmountOfMoneyJsonV121("TZS", "25000.00"),
            description = "Payroll April 2026 — beneficiary 2"
          )
        ),
        requested_execution_date = None,
        value = com.openbankproject.commons.model.AmountOfMoneyJsonV121("TZS", "75000.00"),
        description = "Payroll batch April 2026",
        charge_policy = Some("SHARED")
      ),
      JSONFactory700.BulkTransactionRequestResponseJsonV700(
        id = "d8839721-ad8f-45dd-9f78-2080414b93f9",
        batch_reference = "BATCH-2026-05-13-001",
        status = "COMPLETED",
        from = code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140(
          bank_id = "nmb.tz", account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
        ),
        total_value = com.openbankproject.commons.model.AmountOfMoneyJsonV121("TZS", "75000.00"),
        total_payments = 2,
        succeeded_count = 2,
        failed_count = 0,
        payments = List(
          JSONFactory700.BulkPaymentItemResultJsonV700(
            end_to_end_id = "E2E-0001",
            to_account_routing = com.openbankproject.commons.model.AccountRoutingJsonV121(
              scheme = "TZ.BANK_ACCOUNT", address = "24110000296"
            ),
            value = com.openbankproject.commons.model.AmountOfMoneyJsonV121("TZS", "50000.00"),
            status = "SUCCEEDED",
            transaction_id = Some("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
            failure_reason = None
          ),
          JSONFactory700.BulkPaymentItemResultJsonV700(
            end_to_end_id = "E2E-0002",
            to_account_routing = com.openbankproject.commons.model.AccountRoutingJsonV121(
              scheme = "TZ.MSISDN", address = "255778300336"
            ),
            value = com.openbankproject.commons.model.AmountOfMoneyJsonV121("TZS", "25000.00"),
            status = "SUCCEEDED",
            transaction_id = Some("a3b40c2c-fff5-462b-924e-ab8eb4c89523"),
            failure_reason = None
          )
        ),
        transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1", "a3b40c2c-fff5-462b-924e-ab8eb4c89523"),
        start_date = new java.util.Date(),
        end_date = new java.util.Date()
      ),
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
           BulkPaymentsArrayEmpty, BulkPaymentsArrayTooLarge,
           BulkPaymentCurrencyMismatch, BulkDuplicateEndToEndId,
           BulkBatchReferenceAlreadyUsed, BulkPaymentTransactionRequestError,
           UnknownError),
      apiTagTransactionRequest :: Nil,
      None,
      http4sPartialFunction = Some(createTransactionRequestBulk)
    )

    // ── End BULK ──────────────────────────────────────────────────────────────

    // ── Test-only rollback endpoint ───────────────────────────────────────────
    // Route: POST /obp/v7.0.0/management/system-views/VIEW_ID/factory-reset
    //
    // Reset an existing system view's permissions and view-level flags to the
    // code-defined defaults. The ViewDefinition row is preserved so any
    // AccountAccess records that reference this view remain valid — only the
    // contents of the view are wiped and rewritten.
    //
    // Each successful invocation is audit-logged at INFO level with the
    // calling user_id and the reset view_id; this is a high-impact admin
    // action and we want a trace of who reset what.
    val factoryResetSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "system-views" / viewIdStr / "factory-reset" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val viewId = ViewId(viewIdStr)
          for {
            view <- ViewNewStyle.factoryResetSystemView(viewId, Some(cc))
          } yield {
            logger.info(
              s"AUDIT factoryResetSystemView: user_id=${user.userId} provider=${user.provider} " +
              s"view_id=${viewId.value} permissions_count=${view.allowed_actions.size}"
            )
            JSONFactory600.createViewJsonV600(view)
          }
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(factoryResetSystemView),
      "POST",
      "/management/system-views/VIEW_ID/factory-reset",
      "Factory Reset a System View",
      s"""Reset the system view identified by VIEW_ID to the code-defined defaults.
         |
         |This wipes the view's existing permissions and re-applies whatever the
         |running OBP-API code currently defines as the default permission set
         |for that system view id. View-level flags (name, description, is_firehose,
         |alias settings, is_public) are also restored to defaults.
         |
         |The underlying view row is preserved, so any AccountAccess records that
         |grant users this view on specific accounts remain in place — only the
         |contents of the view itself are reset.
         |
         |Each successful invocation is audit-logged with the calling user_id and
         |the reset view_id.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      ViewJsonV600(
        bank_id = "",
        account_id = "",
        view_id = "auditor",
        view_name = "Auditor",
        description = "auditor",
        metadata_view = "",
        is_public = false,
        is_system = true,
        is_firehose = Some(false),
        alias = "",
        hide_metadata_if_alias_used = false,
        can_grant_access_to_views = Nil,
        can_revoke_access_to_views = Nil,
        allowed_actions = List(
          "can_see_bank_account_balance",
          "can_see_transaction_amount",
          "can_add_comment",
          "can_add_tag"
        )
      ),
      List(
        $AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        SystemViewNotFound,
        UnknownError
      ),
      apiTagSystemView :: Nil,
      Some(List(canUpdateSystemView)),
      http4sPartialFunction = Some(factoryResetSystemView)
    )

    // Enabled only in Lift test mode (Props.testMode == true, i.e. -Drun.mode=test).
    // Props.testMode is set from the JVM system property before any props file loads,
    // so it is reliably available at object-initialization time unlike file-based props.
    // POST /obp/v7.0.0/test/rollback-check: writes one entitlement to DB via
    // RequestScopeConnection.fromFuture, then raises IO.raiseError so the middleware
    // hits Outcome.Errored → rollback.  Used by Http4s700TransactionTest to verify
    // that data written inside a failed request is never committed.
    if (net.liftweb.util.Props.testMode) {
      val testRollbackEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case req @ POST -> `prefixPath` / "test" / "rollback-check" =>
          val cc = req.callContext
          cc.user.toOption match {
            case Some(user) =>
              RequestScopeConnection.fromFuture(
                Future(Entitlement.entitlement.vend.addEntitlement("", user.userId, "TestRollbackSentinel"))
              ).flatMap(_ => IO.raiseError[Response[IO]](new RuntimeException("[test] intentional rollback")))
            case None =>
              IO.pure(Response[IO](Status.Unauthorized))
          }
      }
      resourceDocs += ResourceDoc(
        null,
        implementedInApiVersion,
        "testRollbackEndpoint",
        "POST", "/test/rollback-check", "Test rollback", "Test-only: write then throw to verify rollback",
        EmptyBody, EmptyBody,
        List($AuthenticatedUserIsRequired, UnknownError),
        Nil,
        None,
        http4sPartialFunction = Some(testRollbackEndpoint)
      )
    }

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

    // Routes wrapped with ResourceDocMiddleware for automatic validation.
    // IdempotencyMiddleware is nested inside so that auth/CallContext is populated
    // before the idempotency scope key is computed; on a cache hit the inner
    // routes (and any DB transaction) are skipped.
    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
  }

  // Routes with ResourceDocMiddleware - provides automatic validation based on ResourceDoc metadata
  // Authentication is automatic based on $AuthenticatedUserIsRequired in ResourceDoc errorResponseBodies
  // This matches Lift's wrappedWithAuthCheck behavior
  val wrappedRoutesV700Services: HttpRoutes[IO] = Implementations7_0_0.allRoutesWithMiddleware
}
