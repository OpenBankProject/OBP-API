package code.api.v7_0_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.{APIUtil, ApiRole, CallContext, CustomJsonFormats, Glossary, NewStyle}
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canCreateEntitlementAtOneBank, canCreateMetricsArchiveRun, canCreateOrganisation, canCreateRoutingScheme, canCreateTestEmail, canCreateUtilityVendResult, canDeleteEntitlementAtAnyBank, canDeleteOrganisation, canDeleteRoutingScheme, canDeleteSchedulerJobLock, canGetAccountAccessTrace, canGetAnyOrganisation, canGetAnyUser, canGetCacheConfig, canGetCacheInfo, canGetCacheNamespaces, canGetConnectorHealth, canGetCustomersAtOneBank, canGetDatabasePoolInfo, canGetMetricsDiagnostics, canGetMigrations, canGetSchedulerJobLocks, canUpdateBankSupportedRoutingScheme, canUpdateOrganisation, canUpdateRoutingScheme, canUpdateSystemView}
import code.api.util.CommonsEmailWrapper
import code.model.dataAccess.AuthUser
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.{ErrorResponseConverter, Http4sRequestAttributes, IdempotencyMiddleware, RequestScopeConnection, ResourceDocMiddleware, ResourceDocMatcher}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.ViewNewStyle
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v2_0_0.AccountsHelper.accountTypeFilterText
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
import code.utilitypayment.{UtilityCallbackDispatcher, UtilityPaymentCallbacks}
import code.bulkpayment.{BulkPaymentHandler, BulkPayments}
import code.transactionrequests.MappedTransactionRequestProvider
import com.openbankproject.commons.model.TransactionRequestCharge
import code.metadata.tags.Tags
import code.views.Views
import code.accountattribute.AccountAttributeX
import code.users.{Users => UserVend}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, CoreAccount, CounterpartyId, CustomerId, ListResult, TransactionRequestType, ViewId}
import com.openbankproject.commons.model.enums.ChallengeType
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import code.loginattempts.LoginAttempt
import code.metrics.MappedMetric
import code.users.UserAgreementProvider
import net.liftweb.common.Full
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import net.liftweb.mapper.{By, Descending, MaxRows, OrderBy}
import org.http4s._
import org.http4s.dsl.io._
import org.typelevel.ci.CIString

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
  val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString
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
    // Ensure Implementations7_0_0 is initialized so that resourceDocs is populated.
    // The Kleisli wrapper in wrappedRoutesV700Services defers Implementations7_0_0 init
    // to the first actual API request, which may not have happened yet when a resource-docs
    // request arrives first. Accessing the object here forces its body (resourceDocs += calls).
    val _init = Implementations7_0_0
    // v6.0.0's aggregated docs (v6.0.0 + v5.1.0 + ... + v1.2.1), sourced Lift-free.
    // Combine with v7.0.0's docs.
    val allDocs = code.api.util.http4s.Http4sResourceDocAggregation.v600 ++ resourceDocs
    
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

    // Note: GET /obp/v7.0.0/banks is intentionally NOT defined here.
    // The v7 implementation used the older v4.0.0 shape (BanksJson400: `id`, `short_name`),
    // which is behind v6.0.0's BanksJsonV600 (`bank_id`, `bank_code`). Rather than duplicate
    // (and drift from) the v6 shape, we let the request fall through to `v700ToV600Bridge`,
    // which rewrites /obp/v7.0.0/banks → /obp/v6.0.0/banks and serves Http4s600.getBanks,
    // tagging the response `X-OBP-Version-Served: v6.0.0`. v7 thus inherits the latest shape.

    // Note: resource-docs requests (`GET /obp/v7.0.0/resource-docs/...`) are intercepted by
    // `Http4sResourceDocs.routes`, which is registered earlier in `Http4sApp.baseServices`
    // (line 109, ahead of `v700Routes` at line 113). The self-documenting ResourceDoc metadata
    // for those URLs is registered in `ResourceDocs1_4_0.ResourceDocsAPIMethods.localResourceDocs`
    // (getResourceDocsObp / Swagger / OpenAPI31 / bank-level) and surfaces through
    // `getResourceDocsList`'s localResourceDocs append for the obp standard.
    // There is intentionally no v7-specific handler here.

    // ── POC endpoints — one per EndpointHelper category ────────────────────

    // ─── corePrivateAccountsAllBanks (v7) ─────────────────────────────────────
    // Same semantics as v3.0.0 /my/accounts but with renamed fields so callers
    // can read the (bank_id, account_id, view_id) tuple without remapping.
    //   v3: { id, ..., views: [ { id, ... } ] }
    //   v7: { account_id, ..., views: [ { view_id, ... } ] }

    val corePrivateAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "accounts" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(user)
            (coreAccounts, _)        <- NewStyle.function.getCoreBankAccountsFuture(availablePrivateAccounts, Some(cc))
            filtered = filterCoreAccountsByType(coreAccounts, req)
          } yield JSONFactory700.createCoreAccountsByCoreAccountsJsonV700(filtered, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(corePrivateAccountsAllBanks),
      "GET",
      "/my/accounts",
      "Get Accounts at all Banks (private)",
      s"""Returns the list of accounts containing private views for the user.
      |Each account lists the views available to the user.
      |
      |This endpoint is the v7.0.0 version of `/obp/v3.0.0/my/accounts` with
      |renamed identifier fields: `account_id` (was `id`) and `view_id` (was `id` on each view)
      |so the response gives the `(bank_id, account_id, view_id)` tuple directly.
      |
      |${accountTypeFilterText("/my/accounts")}
      |
      |${userAuthenticationMessage(true)}
      |""",
      EmptyBody,
      JSONFactory700.coreAccountsJsonV700Example,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagAccount, apiTagPSD2AIS, apiTagPrivateData, apiTagPsd2),
      None,
      http4sPartialFunction = Some(corePrivateAccountsAllBanks)
    )

    private def filterCoreAccountsByType(accounts: List[CoreAccount], req: Request[IO]): List[CoreAccount] = {
      val qp = req.uri.query.multiParams
      val filters = qp.get("account_type_filter").toList.flatMap(_.flatMap(_.split(","))).filter(_.nonEmpty)
      val filtersOperation = qp.get("account_type_filter_operation").flatMap(_.headOption).getOrElse("INCLUDE")
      accounts.filter { account =>
        (filters, filtersOperation) match {
          case (f, "INCLUDE") if f.nonEmpty => f.contains(account.accountType)
          case (f, "EXCLUDE") if f.nonEmpty => !f.contains(account.accountType)
          case _                            => true
        }
      }
    }

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

    // Route: GET /obp/v7.0.0/consents/config
    // Anonymous: operator-published policy that TPPs/agents need to know before issuing a consent.
    val getConsentsConfig: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consents" / "config" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory700.ConsentsConfigJsonV700(
            consents_allowed            = APIUtil.getPropsAsBoolValue("consents.allowed", false),
            max_time_to_live_in_seconds = APIUtil.getPropsAsIntValue("consents.max_time_to_live", code.api.Constant.DEFAULT_CONSENT_TTL),
            sca_enabled                 = APIUtil.getPropsAsBoolValue("consents.sca.enabled", true)
          ))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentsConfig),
      "GET",
      "/consents/config",
      "Get Consents Configuration",
      """Returns the operator-configured consent policy for this OBP instance:
        |
        |* `consents_allowed` — whether consent issuance is enabled at all.
        |* `max_time_to_live_in_seconds` — the cap enforced when a client supplies `time_to_live` on consent creation. Exceeding this triggers `OBP-35020`.
        |* `sca_enabled` — whether Strong Customer Authentication is required for consent activation.
        |
        |No Authentication is Required — clients need these values before they hold credentials.""",
      EmptyBody,
      JSONFactory700.consentsConfigJsonV700Example,
      List(UnknownError),
      apiTagConsent :: apiTagApi :: Nil,
      http4sPartialFunction = Some(getConsentsConfig)
    )

    // Route: GET /obp/v7.0.0/api/error-messages
    val getErrorMessages: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "error-messages" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(ListResult("error_messages", JSONFactory700.errorMessagesCatalog))
        }
    }

    resourceDocs += ResourceDoc(
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

    // ── Phase 1 batch 2 ─────────────────────────────────────────────────────

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

    // ── End Phase 1 batch 3 ──────────────────────────────────────────────────

    // ── Test email (self) ─────────────────────────────────────────────────────
    // POST /management/self-test-emails — send a test email to the authenticated
    // user's own address. Useful for admins to verify SMTP configuration (host,
    // port, TLS, credentials, sender address) end-to-end without needing a
    // real-world trigger such as signup or password reset.
    //
    // Recipient is always the caller's emailAddress (not a body parameter): keeps
    // the DoS surface to "spam yourself", and the role gate (canCreateTestEmail)
    // restricts it further to trusted operators.

    case class TestEmailResponseJsonV700(
      to: String,
      from: String,
      subject: String,
      message_id: String
    )

    val createTestEmail: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "self-test-emails" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          val toAddress = Option(user.emailAddress).getOrElse("")
          val fromAddress = AuthUser.emailFrom
          val portalUrlBox = APIUtil.getPropsValue("portal_external_url")
          val subject = s"OBP test email from ${Constant.HostName}"
          val body =
            s"""Hello ${user.name},
               |
               |This is a test email sent from ${Constant.HostName} at ${java.time.Instant.now()}.
               |
               |If you received this, SMTP delivery from the OBP API server is working.
               |
               |Validation and password-reset links sent to users are built from:
               |  portal_external_url = ${portalUrlBox.getOrElse("(unset)")}
               |
               |Triggered by: ${user.userId}
               |""".stripMargin
          for {
            _ <- Helper.booleanToFuture(UserEmailAddressMissing, 400, Some(cc)) {
              toAddress.nonEmpty
            }
            _ <- Helper.booleanToFuture(
              s"$IncompleteServerConfiguration portal_external_url is not set — signup-validation and password-reset emails will not be delivered.",
              500, Some(cc)) {
              portalUrlBox.isDefined
            }
            _ <- Helper.booleanToFuture(
              s"$IncompleteServerConfiguration mail.users.userinfo.sender.address is still the default 'noreply@example.com' — most SMTP servers will reject this From address.",
              500, Some(cc)) {
              fromAddress != "noreply@example.com"
            }
            sendOutcome <- Future {
              CommonsEmailWrapper.sendTextEmailEither(
                CommonsEmailWrapper.EmailContent(
                  from = fromAddress,
                  to = List(toAddress),
                  subject = subject,
                  textContent = Some(body)
                )
              )
            }
            messageId <- sendOutcome match {
              case Right(id) => Future.successful(id)
              case Left(e) =>
                val (errMsg, status) = classifySmtpException(e)
                Helper.booleanToFuture(errMsg, status, Some(cc)) { false }.map(_ => "")
            }
          } yield TestEmailResponseJsonV700(
            to = toAddress,
            from = fromAddress,
            subject = subject,
            message_id = messageId
          )
        }
    }

    // Walk the exception chain and map the most specific known cause to a
    // dedicated OBP error code. Falls back to EmailSendingFailed for genuinely
    // unknown failures. Always appends the exception chain detail so the
    // operator sees the underlying server message (e.g. SMTP 535 from auth).
    private def classifySmtpException(e: Throwable): (String, Int) = {
      val chain = Iterator.iterate(e: Throwable)(_.getCause).takeWhile(_ != null).toList
      val detail = chain
        .map(t => s"${t.getClass.getSimpleName}: ${Option(t.getMessage).getOrElse("").take(200)}")
        .mkString(" -> ")
      val baseMsg = chain.collectFirst {
        case _: jakarta.mail.AuthenticationFailedException => SmtpAuthenticationFailed
        case _: jakarta.mail.SendFailedException           => SmtpRecipientRejected
        case _: javax.net.ssl.SSLException                 => SmtpTlsHandshakeFailed
        case _: java.net.UnknownHostException              => SmtpConnectionFailed
        case _: java.net.ConnectException                  => SmtpConnectionFailed
        case _: java.net.SocketTimeoutException            => SmtpConnectionFailed
        case _: jakarta.mail.MessagingException            => SmtpProtocolError
      }.getOrElse(EmailSendingFailed)
      (s"$baseMsg Detail: $detail", 500)
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createTestEmail),
      "POST",
      "/management/self-test-emails",
      "Send Self Test Email",
      """Send a test email to the authenticated user's own email address.
        |
        |Useful for admins to verify that emails sent during user signup, email
        |validation and password reset can actually be delivered. This endpoint uses
        |the same From address and the same SMTP path as those flows, so a successful
        |result here is a strong signal that those flows will work too.
        |
        |The From address comes from `AuthUser.emailFrom`, which reads the property
        |`mail.users.userinfo.sender.address`. The endpoint fails with 500 if this
        |is still the default `noreply@example.com`, because most SMTP servers will
        |reject that From address.
        |
        |The endpoint also fails with 500 if `portal_external_url` is not set,
        |because that property is required to build the links embedded in
        |signup-validation and password-reset emails. Without it, those flows
        |silently skip sending. The configured value (or `(unset)`) is included
        |in the body of the test email so the admin can confirm visually.
        |
        |The recipient is always the caller's own email address — there is no `to`
        |parameter. The role `CanCreateTestEmail` is required.
        |
        |Returns the recipient, sender, subject and the message-id assigned by the
        |SMTP server. If the email cannot be sent, returns 500 with the most
        |specific OBP error code for the underlying cause:
        |
        |- `$SmtpAuthenticationFailed` — credentials rejected by the SMTP server
        |- `$SmtpConnectionFailed` — TCP connect failed (host unreachable, port closed, timeout, DNS resolution failure)
        |- `$SmtpTlsHandshakeFailed` — STARTTLS/SSL handshake failed (protocol mismatch, untrusted certificate)
        |- `$SmtpRecipientRejected` — server accepted the connection but rejected the recipient, message, or From address
        |- `$SmtpProtocolError` — other Jakarta Mail protocol-level error
        |- `$EmailSendingFailed` — fallback when the underlying cause does not match any of the above
        |
        |In all cases the underlying exception chain (class name and message) is
        |appended after `Detail:` so the operator can diagnose without server logs.
        |""".stripMargin,
      EmptyBody,
      TestEmailResponseJsonV700(
        to = "alice@example.com",
        from = "noreply@openbankproject.com",
        subject = "OBP test email from openbankproject.com",
        message_id = "<abc123@smtp.example.com>"
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserEmailAddressMissing, IncompleteServerConfiguration,
           SmtpAuthenticationFailed, SmtpConnectionFailed, SmtpTlsHandshakeFailed, SmtpRecipientRejected, SmtpProtocolError,
           EmailSendingFailed, UnknownError),
      apiTagEmail :: apiTagSystem :: Nil,
      Some(List(canCreateTestEmail)),
      http4sPartialFunction = Some(createTestEmail)
    )

    // ── Resend validation email (anonymous) ────────────────────────────────────
    // The signup flow at Http4s600.createUser may fail to deliver the validation
    // email (SMTP unreachable, recipient mailbox full, user typo). Without a way
    // to retry, the unvalidated user is stuck — they can't log in (validated=false
    // blocks auth) and the anonymous password-reset endpoint at /users/password-
    // reset-url filters validated=true so it can't help either. This endpoint
    // closes that gap.
    //
    // Anti-enumeration: always returns the same 201 message regardless of whether
    // the user exists, is already validated, the rate limit was hit, or the SMTP
    // send failed. All decisions are logged server-side only.
    //
    // Provider scoping: locked to Constant.localIdentityProvider. OIDC/SSO users
    // never use the email-validation flow (they're created already-validated on
    // first login) so widening the scope would only risk false matches.
    //
    // uniqueId reuse: deliberately does NOT rotate AuthUser.uniqueId. The same
    // validation JWT link is regenerated each call. This avoids the "user clicks
    // older email after a newer resend" race and also avoids invalidating any
    // pending password-reset link the same user might have outstanding.

    private val ResendValidationRateLimit = 3
    private val ResendValidationRateLimitWindowSeconds = 3600

    private def sha256HexLower(s: String): String = {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
    }

    /** Per-key Redis counter. Returns (allowed, currentCount). Fails open if
     *  Redis is unreachable — losing the rate limit briefly is acceptable for
     *  this endpoint (downstream user-exists + validated=false checks bound the
     *  spam surface; the rate limit is defence-in-depth, not the only gate). */
    private def checkResendValidationRateLimit(emailLower: String): (Boolean, Long) = {
      val key = "resend-validation:" + sha256HexLower(emailLower)
      try {
        val ttl = Redis.use(code.api.JedisMethod.TTL, key).map(_.toLong)
        ttl match {
          case Some(-2) =>
            Redis.use(code.api.JedisMethod.SET, key, Some(ResendValidationRateLimitWindowSeconds), Some("1"))
            (true, 1L)
          case Some(t) if t > 0 =>
            val cnt = Redis.use(code.api.JedisMethod.INCR, key).map(_.toLong).getOrElse(1L)
            (cnt <= ResendValidationRateLimit, cnt)
          case _ =>
            Redis.use(code.api.JedisMethod.SET, key, Some(ResendValidationRateLimitWindowSeconds), Some("1"))
            (true, 1L)
        }
      } catch {
        case e: Throwable =>
          logger.warn(s"createValidationEmail says: rate-limit check failed, failing open: ${e.getMessage}")
          (true, 0L)
      }
    }

    val createValidationEmail: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / "validation-emails" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val standardAck = JSONFactory700.ValidationEmailResponseJsonV700(
            message = "If an unvalidated account exists for this username and email, a validation email has been sent."
          )
          for {
            posted <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[JSONFactory700.PostValidationEmailRequestJsonV700].getSimpleName}",
              400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(rawBody).extract[JSONFactory700.PostValidationEmailRequestJsonV700]
            }
          } yield {
            val username = Option(posted.username).map(_.trim).getOrElse("")
            val emailRaw = Option(posted.email).map(_.trim).getOrElse("")
            val emailLower = emailRaw.toLowerCase

            if (username.isEmpty || emailLower.isEmpty) {
              logger.info("createValidationEmail says: skipped (empty username or email)")
            } else {
              val (allowed, count) = checkResendValidationRateLimit(emailLower)
              if (!allowed) {
                logger.info(s"createValidationEmail says: skipped (rate limit exceeded, count=$count, max=$ResendValidationRateLimit per ${ResendValidationRateLimitWindowSeconds}s)")
              } else {
                AuthUser.find(
                  By(AuthUser.username, username),
                  By(AuthUser.provider, Constant.localIdentityProvider)
                ) match {
                  case Full(user) if user.email.get != null
                                  && user.email.get.toLowerCase == emailLower
                                  && !user.validated.get =>
                    val portalUrlBox = APIUtil.getPropsValue("portal_external_url")
                    val senderAddress = AuthUser.emailFrom
                    val portalMissing = portalUrlBox.isEmpty || portalUrlBox.exists(_.trim.isEmpty)
                    val senderIsDefault = senderAddress == "noreply@example.com"
                    if (portalMissing) {
                      logger.warn("createValidationEmail says: skipped — portal_external_url not set; cannot build validation link")
                    } else if (senderIsDefault) {
                      logger.warn("createValidationEmail says: skipped — mail.users.userinfo.sender.address is still the default 'noreply@example.com' (most SMTP servers will reject this From address)")
                    } else {
                      val portalUrl = portalUrlBox.openOr("")
                      val expiryMinutes = APIUtil.getPropsAsIntValue("email_validation_token_expiry_minutes", 1440)
                        val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                          .subject(user.uniqueId.get)
                          .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
                          .issueTime(new java.util.Date())
                          .build()
                      val jwtToken = code.api.util.CertificateUtil.jwtWithHmacProtection(claimsSet)
                      val emailLink = portalUrl + "/user-validation?token=" + java.net.URLEncoder.encode(jwtToken, "UTF-8")
                      val outcome = CommonsEmailWrapper.sendHtmlEmailEither(CommonsEmailWrapper.EmailContent(
                        from = senderAddress,
                        to = List(user.email.get),
                        bcc = AuthUser.bccEmail.toList,
                        subject = "Sign up confirmation",
                        textContent = Some(s"Welcome! Please validate your account: $emailLink"),
                        htmlContent = Some(s"<p>Welcome! Please <a href='$emailLink'>validate your account</a>.</p>")
                      ))
                      outcome match {
                        case Right(msgId) =>
                          logger.info(s"createValidationEmail says: resent validation email messageId=$msgId")
                        case Left(e) =>
                          val (errMsg, _) = classifySmtpException(e)
                          logger.warn(s"createValidationEmail says: SMTP send failed: $errMsg")
                      }
                    }
                  case Full(_) =>
                    logger.info("createValidationEmail says: skipped (user already validated or email mismatch)")
                  case _ =>
                    logger.info("createValidationEmail says: skipped (no local-provider user with that username)")
                }
              }
            }
            standardAck
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createValidationEmail),
      "POST",
      "/users/validation-emails",
      "Create Validation Email (Resend)",
      """Create a new account-validation email for a user and send it by email.
        |The validation link travels only via email; it is NOT returned in the
        |response.
        |
        |This is the recovery endpoint for users who signed up but did not receive
        |(or lost) the original validation email. The anonymous password-reset
        |endpoint cannot help them — it filters on `validated=true`, which an
        |unvalidated user is by definition not.
        |
        |No authentication or role is required. The endpoint is self-service: an
        |unvalidated user cannot authenticate, so any auth requirement would make
        |the endpoint useless to its intended caller.
        |
        |Anti-enumeration: the response is always the same generic acknowledgement,
        |regardless of whether the user exists, is already validated, the rate
        |limit was hit, or the SMTP send failed. The only way to find out what
        |actually happened is the server log.
        |
        |Rate-limit: 3 attempts per email per hour (Redis-backed). Over-limit
        |requests still receive the same 201 acknowledgement.
        |
        |The endpoint only operates on users whose provider is the local identity
        |provider (`local_identity_provider` prop). OIDC / SSO users never have a
        |validation-email flow and are not eligible.
        |
        |The validation token is the same one minted at signup (reuses
        |`AuthUser.uniqueId`). Multiple resends produce the same link, not
        |competing tokens — clicking any of the delivered emails works.
        |
        |Email configuration (portal_external_url, SMTP, sender address) must be
        |set up correctly for delivery to succeed. See /status (Email section) and
        |POST /obp/v7.0.0/management/self-test-emails for diagnostics.
        |""".stripMargin,
      JSONFactory700.PostValidationEmailRequestJsonV700(
        username = "alice",
        email = "alice@example.com"
      ),
      JSONFactory700.validationEmailResponseJsonV700Example,
      List(InvalidJsonFormat, UnknownError),
      apiTagUser :: apiTagEmail :: Nil,
      None,
      http4sPartialFunction = Some(createValidationEmail)
    )

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
    // TZ.BILL_CONTROL_NUMBER) so that downstream adapters and clients agree on
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
      implementedInApiVersion,
      nameOf(createRoutingScheme),
      "POST",
      "/routing-schemes",
      "Create Routing Scheme",
      """Register a new routing scheme.
        |
        |Scheme names follow the convention `<COUNTRY>.<LOCAL_SCHEME>` — uppercase ISO 3166-1 alpha-2 country code, a dot, then an uppercase local scheme name (e.g. `TZ.MSISDN`, `TZ.BILL_CONTROL_NUMBER`).
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
        |- `rail` — match against the `downstream_rails` list (e.g. `TIPS`, `RTGS`)
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
        bank_id = "bank.tz",
        supported_routing_schemes = List(
          JSONFactory700.BankSupportedRoutingSchemeJsonV700(
            scheme = "TZ.MSISDN",
            bank_notes = Some("Routed via the instant-payment rail (TIPS).")
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
        bank_notes = Some("Routed via the instant-payment rail (TIPS). Daily cutoff 22:00 EAT."),
        enabled = Some(true)
      ),
      JSONFactory700.BankSupportedRoutingSchemeJsonV700(
        scheme = "TZ.MSISDN",
        bank_notes = Some("Routed via the instant-payment rail (TIPS). Daily cutoff 22:00 EAT.")
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
        |- Bill inquiry: `identifier: { scheme: TZ.BILL_CONTROL_NUMBER, value: 991043383705 }`
        |- Utility meter inquiry: `identifier: { scheme: TZ.UTILITY_METER, value: 24730238417 }`
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
        network_provider = Some("PROVIDERA"),
        full_name = "Jane Doe",
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
        network_provider = Some("PROVIDERA"),
        full_name = Some("Jane Doe"),
        account_category = Some("PERSON"),
        account_type = Some("WALLET"),
        identity = None
      ),
      value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "TZS", amount = "1000"),
      description = "wallet payment",
      client_reference = Some("ref-0001"),
      verified_payee_lookup_id = None,
      country_code = Some("TZ"),
      data_fields = Some(List(JSONFactory700.MobileWalletDataFieldJsonV700("fieldName1", "fieldValue1"))),
      charge_policy = Some("SHARED")
    )

    resourceDocs += ResourceDoc(
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

    // ── UTILITY transaction request ───────────────────────────────────────────
    // Polymorphic bill / utility payment (prepaid utility meter token purchase, bill
    // payment, ...). The destination biller is identified by a QualifiedIdentifier
    // whose `scheme` must be a registered routing scheme of category UTILITY or BILL —
    // e.g. TZ.UTILITY_METER (prepaid electricity meter). Verify the destination first
    // via POST .../payees/lookup, then pay quoting `verified_payee_lookup_id`
    // (Confirmation-of-Payee handshake). Plugs into the v400 payment pipeline.
    // If `callback_url` is supplied, a one-shot callback is registered and the
    // result is POSTed back asynchronously — a failed callback never fails the
    // payment.
    val UtilityValidCategories: Set[String] = Set("UTILITY", "BILL")

    val createTransactionRequestUtility: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" / "UTILITY" / "transaction-requests" =>
        EndpointHelpers.withViewAndBodyCreated[JSONFactory700.TransactionRequestBodyUtilityJsonV700, JSONFactory700.TransactionRequestWithChargeUtilityJsonV700](req) { (user, fromAccount, view, body, cc) =>
          val callCtx = Some(cc)
          val chargePolicy = body.charge_policy.getOrElse("SHARED")
          for {
            // 1. identifier.scheme must be a registered routing scheme.
            scheme <- Future(RoutingSchemes.routingScheme.vend.getRoutingScheme(body.to.scheme))
              .map(unboxFullOrFail(_, callCtx, PayeeLookupIdentifierTypeNotRegistered, 400))
            // 2. scheme category must be UTILITY or BILL.
            _ <- Helper.booleanToFuture(UtilityIdentifierTypeWrongCategory, 400, callCtx) {
              UtilityValidCategories.contains(scheme.category)
            }
            // 3. identifier.value must match the scheme's address_pattern.
            _ <- Helper.booleanToFuture(UtilityInvalidIdentifier, 400, callCtx) {
              RoutingSchemeValidation.addressMatchesPattern(scheme.addressPattern, body.to.value)
            }
            // 4. optional Confirmation-of-Payee handshake against a prior lookup.
            _ <- body.verified_payee_lookup_id match {
              case Some(lkpId) =>
                for {
                  lkp <- Future(PayeeLookups.payeeLookup.vend.getActivePayeeLookup(lkpId))
                    .map(unboxFullOrFail(_, callCtx, PayeeLookupExpiredOrNotFound, 400))
                  _ <- Helper.booleanToFuture(PayeeLookupMismatch, 400, callCtx) {
                    lkp.identifier == body.to.value && lkp.identifierType == body.to.scheme
                  }
                } yield ()
              case None => Future.successful(())
            }
            // 5. resolve the destination biller/utility account via routing.
            destinationBox <- BankConnector.connector.vend
              .getBankAccountByRouting(None, body.to.scheme, body.to.value, callCtx)
              .map(_._1)
            toAccount <- Future {
              unboxFullOrFail(destinationBox, callCtx, UtilityDestinationNotFound, 404)
            }
            // 6. standard view authorisation check (same as v4 COUNTERPARTY).
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(
              view.viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), user, callCtx
            )
            // 7. serialise the body to JSON for the connector's audit blob.
            detailsPlain = prettyRender(Extraction.decompose(body))
            // 8. create the transaction request via the standard pipeline.
            txnReqType = TransactionRequestType("UTILITY")
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
            // 9. Register the one-shot result callback (step c), if asked. It is NOT fired
            //    here: the vend is asynchronous, so the token does not yet exist. The callback
            //    is fired by createUtilityVendResult once the rail delivers the vend result —
            //    carrying the real token, and from a separate, already-committed request (which
            //    also avoids racing this request's transaction commit).
            callbackJson = body.callback_url.flatMap { url =>
              val callbackId = APIUtil.generateUUID()
              UtilityPaymentCallbacks.utilityPaymentCallback.vend.createCallback(
                callbackId = callbackId,
                transactionRequestId = tr.id.value,
                callbackUrl = url,
                identifierType = body.to.scheme,
                identifier = body.to.value,
                fromBankId = fromAccount.bankId.value,
                fromAccountId = fromAccount.accountId.value,
                createdByUserId = user.userId
              ).toOption.map { stored =>
                JSONFactory700.UtilityCallbackJsonV700(
                  callback_id = stored.callbackId,
                  callback_url = stored.callbackUrl,
                  status = stored.status
                )
              }
            }
            // vend_result is None at creation — it arrives asynchronously via createUtilityVendResult.
          } yield JSONFactory700.createTransactionRequestWithChargeUtilityJsonV700(tr, body, callbackJson, None, Nil, Nil)
        }
    }

    val utilityBodyExample = JSONFactory700.TransactionRequestBodyUtilityJsonV700(
      to = JSONFactory700.QualifiedIdentifierJsonV700(
        scheme = "TZ.UTILITY_METER", value = "24730238417", fsp_id = None
      ),
      value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "TZS", amount = "1000"),
      description = "Prepaid utility meter token purchase",
      client_reference = Some("ref-0001"),
      verified_payee_lookup_id = None,
      payer = Some(JSONFactory700.UtilityPayerJsonV700(
        phone = Some("255700000000"),
        name = Some("Jane Doe"),
        email = Some("jane.doe@example.com")
      )),
      callback_url = Some("https://example.com/utility/callback"),
      data_fields = None,
      charge_policy = Some("SHARED")
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createTransactionRequestUtility),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/UTILITY/transaction-requests",
      "Create Transaction Request (UTILITY)",
      """Initiate a bill / utility payment — e.g. a prepaid-electricity meter token purchase, or a bill payment.
        |
        |The endpoint is **polymorphic on `to.scheme`**: the destination biller is identified by a `QualifiedIdentifier` whose `scheme` must be a registered routing scheme of category **UTILITY** or **BILL** (e.g. `TZ.UTILITY_METER`, `TZ.BILL_CONTROL_NUMBER`). The scheme must be registered in the routing-scheme catalogue (`GET /obp/v7.0.0/routing-schemes/TZ.UTILITY_METER`) and `to.value` must match its `address_pattern`.
        |
        |**Confirmation-of-Payee handshake** (recommended): call `POST /banks/.../accounts/.../payees/lookup` first (the meter-number / control-number inquiry), then pass the returned `lookup_id` here as `verified_payee_lookup_id`. The endpoint rejects the request if the lookup has expired or does not match the supplied identifier.
        |
        |**Payer block**: `payer` carries the depositor's phone / name / email for the biller receipt.
        |
        |**Callback** (optional): supply `callback_url` to register a one-shot callback. The vend is asynchronous — the electricity token does not exist yet at creation, so the response returns `vend_result: null` and the registered callback's status is `REGISTERED`. Once the downstream rail delivers the vend result (via the system endpoint `POST /banks/BANK_ID/utility-payments/UTILITY_TRANSACTION_REQUEST_ID/vend-result`), OBP records the token/receipt on the transaction request and POSTs the enriched result to `callback_url`. A failed or unreachable callback never fails the payment.
        |
        |**Provider passthrough**: `data_fields` carries arbitrary name/value pairs that adapters forward to the downstream rail without OBP interpretation.
        |
        |Authentication is Required.""".stripMargin,
      utilityBodyExample,
      JSONFactory700.TransactionRequestWithChargeUtilityJsonV700(
        id = "4050046c-63b3-4868-8a22-14b4181d33a6",
        `type` = "UTILITY",
        from = code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140(
          bank_id = "gh.29.uk",
          account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f1"
        ),
        details = utilityBodyExample,
        transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
        status = "COMPLETED",
        start_date = code.api.util.APIUtil.DateWithDayExampleObject,
        end_date = code.api.util.APIUtil.DateWithDayExampleObject,
        challenges = Nil,
        charge = code.api.v2_0_0.TransactionRequestChargeJsonV200(
          summary = "Total charges for completed transaction",
          value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "TZS", amount = "0.00")
        ),
        callback = Some(JSONFactory700.UtilityCallbackJsonV700(
          callback_id = "cbk_01HXY7Z8AB9C0D1E2F3G4H5J6K",
          callback_url = "https://example.com/utility/callback",
          status = "REGISTERED"
        )),
        vend_result = None,
        attributes = None
      ),
      List($AuthenticatedUserIsRequired, InvalidJsonFormat,
           PayeeLookupIdentifierTypeNotRegistered, UtilityIdentifierTypeWrongCategory,
           UtilityInvalidIdentifier, PayeeLookupExpiredOrNotFound, PayeeLookupMismatch,
           UtilityDestinationNotFound, UtilityPaymentError, UnknownError),
      apiTagTransactionRequest :: apiTagPayee :: Nil,
      None,
      http4sPartialFunction = Some(createTransactionRequestUtility)
    )

    // ── UTILITY vend-result delivery (inbound, asynchronous) ───────────────────
    // The downstream rail/adapter calls this once the utility vend settles, delivering
    // the token / receipt (e.g. a 20-digit STS prepaid-electricity token). OBP persists the
    // vend fields as transaction-request attributes and — if the payer registered a
    // callback_url on the original request — POSTs the vend result to it. The rail is a
    // trusted system actor, gated by canCreateUtilityVendResult. Returns 200.
    // System path: a flat /utility-payments/UTILITY_TRANSACTION_REQUEST_ID segment avoids
    // ACCOUNT_ID/VIEW_ID middleware resolution (the rail has no view on the payer's account).
    val createUtilityVendResult: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "utility-payments" / trIdStr / "vend-result" =>
        EndpointHelpers.withUserAndBody[JSONFactory700.PostUtilityVendResultJsonV700, JSONFactory700.UtilityVendResultResponseJsonV700](req) { (_, body, cc) =>
          val callCtx = Some(cc)
          val trId = com.openbankproject.commons.model.TransactionRequestId(trIdStr)
          // Only present fields are persisted; the vend status is always written.
          val attrs: List[(String, String)] =
            (JSONFactory700.UtilityVendAttribute.VendStatus -> body.status) :: List(
              body.token.map(JSONFactory700.UtilityVendAttribute.Token -> _),
              body.rcpt_num.map(JSONFactory700.UtilityVendAttribute.RcptNum -> _),
              body.units.map(JSONFactory700.UtilityVendAttribute.Units -> _),
              body.provider_reference.map(JSONFactory700.UtilityVendAttribute.ProviderReference -> _),
              body.provider_message.map(JSONFactory700.UtilityVendAttribute.ProviderMessage -> _)
            ).flatten
          for {
            // 1. The transaction request must exist (404 otherwise).
            (tr, _) <- Future(BankConnector.connector.vend.getTransactionRequestImpl(trId, callCtx))
              .map(unboxFullOrFail(_, callCtx, UtilityTransactionRequestNotFound, 404))
            bankId = BankId(tr.from.bank_id)
            // 2. Persist the vend fields as transaction-request attributes.
            _ <- Future.sequence(attrs.map { case (name, value) =>
              NewStyle.function.createOrUpdateTransactionRequestAttribute(
                bankId, trId, None, name,
                com.openbankproject.commons.model.enums.TransactionRequestAttributeType.STRING, value, callCtx
              )
            })
            // 3. Read attributes back and project the typed vend_result.
            (attributes, _) <- NewStyle.function.getTransactionRequestAttributesFromProvider(trId, callCtx)
            vendResult = JSONFactory700.utilityVendResultFromAttributes(attributes)
            // 4. If the payer registered a callback, deliver the vend result to it. The callback
            //    row was committed by the create request, so the dispatcher's async status update
            //    no longer races an uncommitted row. A failed callback never fails this request.
            callbackJson = UtilityPaymentCallbacks.utilityPaymentCallback.vend
              .getCallbackByTransactionRequestId(trIdStr).toOption.map { cb =>
                val payload = prettyRender(Extraction.decompose(
                  JSONFactory700.UtilityVendResultResponseJsonV700(
                    transaction_request_id = tr.id.value, `type` = tr.`type`,
                    status = tr.status, vend_result = vendResult, callback = None
                  )
                ))
                UtilityCallbackDispatcher.deliver(cb.callbackId, cb.callbackUrl, payload)
                JSONFactory700.UtilityCallbackJsonV700(
                  callback_id = cb.callbackId, callback_url = cb.callbackUrl, status = cb.status
                )
              }
          } yield JSONFactory700.UtilityVendResultResponseJsonV700(
            transaction_request_id = tr.id.value, `type` = tr.`type`,
            status = tr.status, vend_result = vendResult, callback = callbackJson
          )
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createUtilityVendResult),
      "POST",
      "/banks/BANK_ID/utility-payments/UTILITY_TRANSACTION_REQUEST_ID/vend-result",
      "Deliver UTILITY Vend Result",
      """**System endpoint** — called by the downstream rail/adapter (not the payer) to deliver the
        |asynchronous result of a UTILITY payment, e.g. a prepaid-electricity purchase.
        |
        |The vend is asynchronous: the original `POST .../transaction-request-types/UTILITY/transaction-requests`
        |returns immediately with `vend_result: null`, and the actual deliverable — the **STS prepaid token**
        |(typically 20 digits) plus receipt (`rcpt_num`, `units`, `provider_reference`) — arrives here once the
        |rail settles the vend. OBP records the vend fields as attributes on the transaction request and,
        |if the payer registered a `callback_url`, POSTs this vend result to that URL (a failed or
        |unreachable callback never fails this request).
        |
        |`UTILITY_TRANSACTION_REQUEST_ID` is the `id` returned by the original UTILITY transaction request.
        |
        |Requires the `CanCreateUtilityVendResult` system entitlement.""".stripMargin,
      JSONFactory700.PostUtilityVendResultJsonV700(
        status = "COMPLETED",
        token = Some("1234 5678 9012 3456 7890"),
        rcpt_num = Some("202306141018422348674"),
        units = Some("46.5"),
        provider_reference = Some("REF800930701197"),
        provider_message = Some("Vend successful")
      ),
      JSONFactory700.UtilityVendResultResponseJsonV700(
        transaction_request_id = "4050046c-63b3-4868-8a22-14b4181d33a6",
        `type` = "UTILITY",
        status = "COMPLETED",
        vend_result = Some(JSONFactory700.UtilityVendResultJsonV700(
          status = "COMPLETED",
          token = Some("1234 5678 9012 3456 7890"),
          rcpt_num = Some("202306141018422348674"),
          units = Some("46.5"),
          provider_reference = Some("REF800930701197"),
          provider_message = Some("Vend successful")
        )),
        callback = Some(JSONFactory700.UtilityCallbackJsonV700(
          callback_id = "cbk_01HXY7Z8AB9C0D1E2F3G4H5J6K",
          callback_url = "https://example.com/utility/callback",
          status = "REGISTERED"
        ))
      ),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
           UtilityTransactionRequestNotFound, UnknownError),
      apiTagTransactionRequest :: apiTagPayee :: Nil,
      Some(List(canCreateUtilityVendResult)),
      http4sPartialFunction = Some(createUtilityVendResult)
    )

    // ── End UTILITY ───────────────────────────────────────────────────────────

    // ── OPEN_CORRIDOR transaction request ─────────────────────────────────────
    // Travel-Rule-friendly TR with FATF Recommendation 16 originator block.
    // Money-movement is identical to SIMPLE; the originator is persisted as a
    // side-car on the TR row and surfaced on the v7 response. Lives natively at
    // v7 (rather than bridging to v4) because only v7's response shape carries
    // the originator block.
    val createTransactionRequestOpenCorridor: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" / "OPEN_CORRIDOR" / "transaction-requests" =>
        EndpointHelpers.withViewAndBodyCreated[JSONFactory700.TransactionRequestBodyOpenCorridorJsonV700, JSONFactory700.TransactionRequestWithChargeOpenCorridorJsonV700](req) { (user, fromAccount, view, body, cc) =>
          val callCtx = Some(cc)
          for {
            _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(
              view.viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), user, callCtx
            )
            (tr, _) <- code.bankconnectors.opencorridor.OpenCorridorProcessor.create(
              user, fromAccount.bankId, fromAccount.accountId, view.viewId, fromAccount, body, callCtx
            )
            (originatorJson, _) <- JSONFactory700.buildTransactionRequestOriginatorJson(tr, callCtx)
          } yield JSONFactory700.createTransactionRequestWithChargeOpenCorridorJsonV700(tr, body, originatorJson, Nil)
        }
    }

    val openCorridorBodyExample = JSONFactory700.TransactionRequestBodyOpenCorridorJsonV700(
      to = code.api.v4_0_0.PostSimpleCounterpartyJson400(
        name = "Other Bank",
        description = "Beneficiary at receiving institution",
        other_bank_routing_scheme = "BIC",
        other_bank_routing_address = "DEUTDEFF",
        other_account_routing_scheme = "IBAN",
        other_account_routing_address = "DE89 3704 0044 0532 0130 00",
        other_account_secondary_routing_scheme = "",
        other_account_secondary_routing_address = "",
        other_branch_routing_scheme = "",
        other_branch_routing_address = ""
      ),
      value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "EUR", amount = "100.00"),
      description = "OPEN_CORRIDOR Travel-Rule payment",
      charge_policy = "SHARED",
      originator = com.openbankproject.commons.model.TransactionRequestOriginator(
        name = "Alice Sender",
        address = "1 Sender Street, London, UK",
        account_routing = com.openbankproject.commons.model.TransactionRequestOriginatorAccountRouting(
          scheme = "IBAN",
          address = "GB29 NWBK 6016 1331 9268 19"
        )
      ),
      future_date = None
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createTransactionRequestOpenCorridor),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/OPEN_CORRIDOR/transaction-requests",
      "Create Transaction Request (OPEN_CORRIDOR)",
      """Initiate an OPEN_CORRIDOR Transaction Request — a Travel-Rule-friendly payment that carries FATF Recommendation 16 originator information about the actual payer.
        |
        |Money-movement is identical to the SIMPLE transaction request type (same beneficiary routing fields). What's distinct: the `originator` block is mandatory and is persisted alongside the transaction request. The v7 response includes a populated originator block.
        |
        |Authentication is Required.""".stripMargin,
      openCorridorBodyExample,
      JSONFactory700.TransactionRequestWithChargeOpenCorridorJsonV700(
        id = "4050046c-63b3-4868-8a22-14b4181d33a6",
        `type` = "OPEN_CORRIDOR",
        from = code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140(
          bank_id = "gh.29.uk",
          account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f1"
        ),
        details = openCorridorBodyExample,
        transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
        status = "COMPLETED",
        start_date = code.api.util.APIUtil.DateWithDayExampleObject,
        end_date = code.api.util.APIUtil.DateWithDayExampleObject,
        challenges = Nil,
        charge = code.api.v2_0_0.TransactionRequestChargeJsonV200(
          summary = "Total charges for completed transaction",
          value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = "EUR", amount = "0.00")
        ),
        originator = Some(JSONFactory700.TransactionRequestOriginatorJsonV700(
          name = "Alice Sender",
          address = "1 Sender Street, London, UK",
          account_routing = JSONFactory700.TransactionRequestOriginatorAccountRoutingJsonV700(
            scheme = "IBAN",
            address = "GB29 NWBK 6016 1331 9268 19"
          ),
          source = "explicit"
        ))
      ),
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidJsonValue,
           CounterpartyBeneficiaryPermit, InvalidChargePolicy,
           InsufficientAuthorisationToCreateTransactionRequest, UnknownError),
      apiTagTransactionRequest :: Nil,
      None,
      http4sPartialFunction = Some(createTransactionRequestOpenCorridor)
    )

    // ── End OPEN_CORRIDOR ─────────────────────────────────────────────────────

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
            trId = APIUtil.generateUUID()
            // 3. Claim the batch_reference for idempotency BEFORE creating the parent TR or
            //    fanning out any payment. The UniqueIndex(FromBankId, FromAccountId, BatchReference)
            //    makes this the single atomic point of idempotency: two concurrent submissions both
            //    pass the earlier isBatchReferenceUsed check, but only one wins the INSERT here. The
            //    loser's Box is a Failure — we must surface it (409) so it aborts before any payment,
            //    rather than dropping it and double-charging.
            _ <- Future {
              unboxFullOrFail(
                BulkPayments.bulkPayment.vend.claimBatchReference(
                  fromAccount.bankId.value, fromAccount.accountId.value, body.batch_reference, trId
                ),
                callCtx, BulkBatchReferenceAlreadyUsed, 409
              )
            }
            // 4. Create the parent BULK TR row. toAccount = self (envelope only;
            //    the real destinations live in the per-payment side-table).
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
              // Compensate before surfacing a parent-TR creation failure: the claim above has
              // already been written, but NO payment has executed yet, so releasing the
              // batch_reference is safe and lets the client retry a transient failure. Without
              // this, the committed claim would 409 every retry of a batch that never ran.
              // (Never release after the fan-out below — payments may have partially executed.)
              if (parentTrBox.isEmpty) {
                BulkPayments.bulkPayment.vend.releaseBatchReference(
                  fromAccount.bankId.value, fromAccount.accountId.value, body.batch_reference, trId
                )
              }
              unboxFullOrFail(parentTrBox, callCtx, BulkPaymentTransactionRequestError, 500)
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
          bank_id = "bank.tz", account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
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

    // Route: GET /obp/v7.0.0/management/system/diagnostics/metrics
    //
    // Operator diagnostic for the metrics-archiving pipeline. Reports the
    // archiving props plus row counts and the oldest/newest record in both the
    // `metric` and `metricarchive` tables, then runs integrity checks that
    // surface whether MetricsArchiveScheduler is keeping each table inside its
    // configured retention window. Intended for use from the API Manager.
    val getMetricsDiagnostics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "diagnostics" / "metrics" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            JSONFactory700.createMetricsAndArchiveMetricsDiagnosticsJsonV700()
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMetricsDiagnostics),
      "GET",
      "/management/system/diagnostics/metrics",
      "Get Metrics and Archive Metrics Diagnostics",
      s"""Diagnostic for the metrics-archiving pipeline (`MetricsArchiveScheduler`).
         |
         |Returns:
         |
         |* `config` — the relevant props as the scheduler reads them (configured
         |  value, or the code default when unset): `write_metrics`,
         |  `enable_metrics_scheduler`, `retain_metrics_scheduler_interval_in_seconds`,
         |  `retain_metrics_days`, `retain_archive_metrics_days`,
         |  `retain_metrics_move_limit`.
         |* `metric` — row count and oldest/newest record (date + age in days) of
         |  the live `metric` table.
         |* `metric_archive` — the same for the `metricarchive` table.
         |* `last_run` / `last_successful_run` — the most recent (and most recent
         |  successful) scheduler run from the `metricsarchiverun` audit log, including
         |  rows moved, rows deleted, duration and success. Absent if no run has been
         |  recorded yet.
         |* `checks` — a list of integrity checks, each with a `status` of `OK`,
         |  `WARNING`, or `ERROR`:
         |    * `check_metrics_are_being_written` — warns if `write_metrics` is off,
         |      so no new metrics are being recorded.
         |    * `check_archive_scheduler_is_enabled` — errors if `enable_metrics_scheduler`
         |      is off, so old metrics are never archived nor deleted.
         |    * `check_metric_retention_policy_is_respected` — flags if the oldest live
         |      metric is older than the retention window (move job not keeping up / stopped).
         |    * `check_all_old_metrics_can_be_archived` — always OK; old metric rows with no
         |      correlation id are now archived with a generated `ORIGINALLY_NOT_SET-<uuid>` id.
         |    * `check_archive_retention_policy_is_respected` — flags if the oldest archived
         |      metric is older than the archive retention (cleanup not keeping up / stopped).
         |    * `check_archive_metrics_is_fresh_enough` — flags if a backlog exists but
         |      the newest archived record is stale (move job stopped). "enough" because
         |      a fresh record is only required when there is a backlog to move.
         |    * `check_last_archive_run_succeeded` — reports the outcome of the most recent
         |      run from the run log (errors if the last run failed; warns if none recorded).
         |* `everything_as_expected` — `true` only when every check is `OK`.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      JSONFactory700.metricsAndArchiveMetricsDiagnosticsJsonV700Example,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagMetric :: apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetMetricsDiagnostics)),
      http4sPartialFunction = Some(getMetricsDiagnostics)
    )

    // Route: POST /obp/v7.0.0/management/system/diagnostics/metrics/run
    //
    // Manually trigger one metrics-archive run. This calls the exact same
    // `MetricsArchiveScheduler.runOnce()` the timer uses, so it honours the same
    // concurrency lock (won't start if a run is already in progress), the same
    // retention props, and records the run in the `metricsarchiverun` log.
    // The run executes synchronously and may take a while for large backlogs
    // (it moves up to `retain_metrics_move_limit` rows).
    val triggerMetricsArchiveRun: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "system" / "diagnostics" / "metrics" / "run" =>
        EndpointHelpers.withUser(req) { (user, _) =>
          Future {
            val outcome = code.scheduler.MetricsArchiveScheduler.runOnce()
            logger.info(s"AUDIT triggerMetricsArchiveRun: user_id=${user.userId} outcome=${outcome.getClass.getSimpleName}")
            JSONFactory700.createTriggerMetricsArchiveRunResponseJsonV700(outcome)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(triggerMetricsArchiveRun),
      "POST",
      "/management/system/diagnostics/metrics/run",
      "Trigger a Metrics Archive Run",
      s"""Manually run the metrics-archiving job once, on demand.
         |
         |This invokes the **same** code path as the scheduled
         |`MetricsArchiveScheduler` run, so it respects all the same checks:
         |
         |* **Concurrency lock** — if an archive run is already in progress (the
         |  `JobScheduler` lock is held, on this or another node), no new run is
         |  started and the response `status` is `skipped_already_in_progress`.
         |* **Retention rules** — moves `metric` rows older than
         |  `retain_metrics_days` to `metricarchive`, up to
         |  `retain_metrics_move_limit` rows; then deletes `metricarchive` rows
         |  older than `retain_archive_metrics_days`.
         |* **Run log** — the outcome is written to the `metricsarchiverun` audit
         |  log, exactly as a scheduled run would be.
         |
         |Response fields:
         |* `status` — `completed` (a run executed — inspect `run.success`) or
         |  `skipped_already_in_progress`.
         |* `message` — human-readable summary.
         |* `run` — the recorded run (run id, counts, duration, success, remark);
         |  absent when skipped.
         |* `in_progress` — present only when skipped: the lock that blocked the run
         |  (`job_id`, `api_instance_id`, `started_at`, `age_seconds`). A large
         |  `age_seconds` (much older than a normal run) indicates a stale lock left
         |  by a dead JVM — clear the matching `jobscheduler` row to unblock.
         |
         |Note: the run executes synchronously, so a large backlog may take a while.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      JSONFactory700.triggerMetricsArchiveRunResponseJsonV700Example,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagMetric :: apiTagSystem :: apiTagApi :: Nil,
      Some(List(canCreateMetricsArchiveRun)),
      http4sPartialFunction = Some(triggerMetricsArchiveRun)
    )

    // Route: GET /obp/v7.0.0/management/system/scheduler/job-locks
    //
    // List the `jobscheduler` lock rows (newest first, capped at 100). This table
    // holds a row only while a scheduled job holds its lock — the row is deleted
    // when the job finishes — so in healthy operation this is empty. Any row here
    // is a currently-running job or a stale lock left by a dead JVM; `age_seconds`
    // tells them apart, and the row can be cleared with the DELETE route below.
    val getSchedulerJobs: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "scheduler" / "job-locks" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future {
            JSONFactory700.createSchedulerJobsJsonV700(code.scheduler.JobScheduler.mostRecent(100))
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getSchedulerJobs),
      "GET",
      "/management/system/scheduler/job-locks",
      "Get Scheduler Job Locks",
      s"""List the scheduler lock rows from the `jobscheduler` table (most recent first, up to 100).
         |
         |**This is a lock table, not a job-history log.** A row exists only while a
         |scheduled job (e.g. `MetricsArchiveScheduler`) holds its lock; it is deleted
         |when the job finishes. So in healthy operation this list is **empty**.
         |
         |A row that is present is therefore one of:
         |* a job genuinely running right now (small `age_seconds`), or
         |* a **stale lock** left by a JVM that died mid-run (large `age_seconds`) —
         |  this blocks new runs of that job (e.g. "Trigger a Metrics Archive Run"
         |  returns `skipped_already_in_progress`). Clear it with
         |  `DELETE /management/system/scheduler/job-locks/JOB_ID`.
         |
         |Each row reports `job_id`, `name`, `api_instance_id`, `started_at` and
         |`age_seconds` (seconds since the lock was taken).
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      JSONFactory700.schedulerJobsJsonV700Example,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystem :: apiTagApi :: Nil,
      Some(List(canGetSchedulerJobLocks)),
      http4sPartialFunction = Some(getSchedulerJobs)
    )

    // Route: DELETE /obp/v7.0.0/management/system/scheduler/job-locks/JOB_ID
    //
    // Clear a scheduler lock row by its job id. Use this to release a stale lock
    // left by a dead JVM so the job (e.g. metrics archiving) can run again.
    // Idempotent — returns 204 even if the row is already gone.
    val deleteSchedulerJob: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "system" / "scheduler" / "job-locks" / jobId =>
        EndpointHelpers.withUserDelete(req) { (_, _) =>
          Future { code.scheduler.JobScheduler.deleteByJobId(jobId); () }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteSchedulerJob),
      "DELETE",
      "/management/system/scheduler/job-locks/JOB_ID",
      "Delete Scheduler Job Lock",
      s"""Clear a scheduler lock row from the `jobscheduler` table by its `JOB_ID`.
         |
         |Use this to release a **stale lock** left by a JVM that died mid-run, which
         |would otherwise keep a scheduled job (e.g. `MetricsArchiveScheduler`) from
         |starting — see "Get Scheduler Job Locks" to find the `job_id` and judge staleness
         |from its `age_seconds`.
         |
         |**Caution:** if the job is genuinely still running on some node, deleting its
         |lock lets a second run start concurrently. Only clear locks you have confirmed
         |are stale (much older than a normal run).
         |
         |Idempotent — returns 204 even if no row with that `JOB_ID` exists.
         |
         |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagSystem :: apiTagApi :: Nil,
      Some(List(canDeleteSchedulerJobLock)),
      http4sPartialFunction = Some(deleteSchedulerJob)
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

  // ─── path-rewriting bridge: /obp/v7.0.0/… → /obp/v6.0.0/… ─────────────
  // Catches v7.0.0 paths with NO matching v7 ResourceDoc and forwards them to
  // Http4s600 (which has all 243 v6.0.0 endpoints). Paths that DO have a v7
  // ResourceDoc are intentionally excluded: if the middleware returned
  // OptionT.none for such a path (e.g. api_disabled_endpoints), the bridge must
  // not silently re-serve them from v6. The index is built lazily from the same
  // resourceDocs buffer that the middleware uses, so it stays in sync.
  private lazy val v7ResourceDocIndex: ResourceDocMatcher.ResourceDocIndex =
    ResourceDocMatcher.buildIndex(resourceDocs)

  private val v700ToV600Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    val rawPath = req.uri.path.renderString
    if (rawPath.startsWith("/obp/v7.0.0/") &&
        ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, v7ResourceDocIndex).isEmpty) {
      val rewritten = rawPath.replaceFirst("/obp/v7\\.0\\.0/", "/obp/v6.0.0/")
      val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
      code.api.v6_0_0.Http4s600.wrappedRoutesV600Services.run(req.withUri(newUri))
        .map(_.putHeaders(Header.Raw(CIString("X-OBP-Version-Served"), "v6.0.0")))
    } else {
      OptionT.none[IO, Response[IO]]
    }
  }

  lazy val wrappedRoutesV700Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations7_0_0.allRoutesWithMiddleware.run(req)
        .orElse(v700ToV600Bridge.run(req))
    }
}
