package code.api.v3_0_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.v2_0_0.AccountsHelper._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.{ApiRole, FutureUtil}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.JSONFactory
import code.api.v2_0_0.JSONFactory200
import code.api.v3_0_0.JSONFactory300._
import code.bankconnectors.Connector
import code.consumer.Consumers
import code.entitlementrequest.EntitlementRequest
import code.metrics.APIMetrics
import code.model._
import code.scope.Scope
import code.search.elasticsearchWarehouse
import code.users.Users
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.grum.geocalc.{Coordinate, EarthCalc, Point}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Empty, Failure, Full, ParamFailure}
import org.json4s.JsonAST.JField
import com.openbankproject.commons.util.JsonAliases.compactRender
import org.json4s.{Extraction, Formats}
import org.json4s.native.Serialization
import net.liftweb.util.Helpers.tryo
import org.http4s._
import org.http4s.dsl.io._

import java.util.regex.Pattern
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import com.openbankproject.commons.util.JsonAliases.RichJField

object Http4s300 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v3_0_0
  val versionStatus: String                       = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]       = ArrayBuffer[ResourceDoc]()

  implicit val formats: Formats = CustomJsonFormats.formats

  type HttpF[A] = OptionT[IO, A]

  object Implementations3_0_0 {
    val prefixPath: Path = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root ─────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v3_0_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v3_0_0, versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
      |
      |* API version
      |* Hosted by information
      |* Git Commit""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    // ─── getViewsForBankAccount ───────────────────────────────────────────────

    val getViewsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
            anyCanSee = permission.views
              .map(_.allowed_actions.exists(_ == CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT))
              .contains(true)
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT}` permission on any your views",
              cc = Some(cc)) { anyCanSee }
            views <- Future(Views.views.vend.availableViewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
          } yield JSONFactory300.createViewsJSON(views)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getViewsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account",
      s"""#Views
      |
      |
      |Views in Open Bank Project provide a mechanism for fine grained access control and delegation to Accounts and Transactions. Account holders use the 'owner' view by default. Delegated access is made through other views for example 'accountants', 'share-holders' or 'tagging-application'. Views can be created via the API and each view has a list of entitlements.
      |
      |Views on accounts and transactions filter the underlying data to redact certain fields for certain users. For instance the balance on an account may be hidden from the public. The way to know what is possible on a view is determined in the following JSON.
      |
      |**Data:** When a view moderates a set of data, some fields my contain the value `null` rather than the original value. This indicates either that the user is not allowed to see the original data or the field is empty.
      |
      |There is currently one exception to this rule; the 'holder' field in the JSON contains always a value which is either an alias or the real name - indicated by the 'is_alias' field.
      |
      |**Action:** When a user performs an action like trying to post a comment (with POST API call), if he is not allowed, the body response will contain an error message.
      |
      |**Metadata:**
      |Transaction metadata (like images, tags, comments, etc.) will appears *ONLY* on the view where they have been created e.g. comments posted to the public view only appear on the public view.
      |
      |The other account metadata fields (like image_URL, more_info, etc.) are unique through all the views. Example, if a user edits the 'more_info' field in the 'team' view, then the view 'authorities' will show the new value (if it is allowed to do it).
      |
      |# All
      |*Optional*
      |
      |Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      viewsJsonV300,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(getViewsForBankAccount)
    )

    // ─── createViewForBankAccount ─────────────────────────────────────────────

    val createViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / accountIdStr / "views" =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          bank   <- IO.fromOption(cc.bank)(new RuntimeException(BankNotFound))
          rawBox <- IO.fromFuture(IO(Connector.connector.vend.checkBankAccountExists(bank.bankId, AccountId(accountIdStr), Some(cc)).map(_._1)))
          account <- IO(unboxFullOrFail(rawBox, Some(cc), BankAccountNotFound, 404))
          body   <- IO.pure(cc.httpBody.getOrElse(""))
          result <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            createViewImpl300(user, account, body, cc))
        } yield result
        io.attempt.flatMap {
          case Right(result) =>
            Created(com.openbankproject.commons.util.JsonAliases.prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createViewForBankAccount),
      "POST",
      "/banks/BANK_ID/accounts/VIEW_ACCOUNT_ID/views",
      "Create Custom View",
      s"""Create a custom view on bank account
      |
      | ${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      | The 'alias' field in the JSON can take one of three values:
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
      | The 'metadata_view' field determines where metadata (comments, tags, images, where tags) for transactions are stored and retrieved. If set to another view's ID (e.g. 'owner'), metadata added through this view will be shared with all other views that also use the same metadata_view value. If left empty, metadata is stored under this view's own ID and is not shared with other views.
      |
      | You MUST use a leading _ (underscore) in the view name because other view names are reserved for OBP [system views](/index#group-View-System).
      | """,
      SwaggerDefinitionsJSON.createViewJsonV300,
      viewJsonV300,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankAccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(createViewForBankAccount)
    )

    private def createViewImpl300(user: User, account: BankAccount, body: String, cc: CallContext): Future[ViewJsonV300] = {
      for {
        createBodyJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CreateViewJsonV300", 400, Some(cc)) {
          com.openbankproject.commons.util.JsonAliases.parse(body).extract[CreateViewJsonV300]
        }
        _ <- code.util.Helper.booleanToFuture(
          s"$InvalidCustomViewFormat Current view_name (${createBodyJson.name})", cc = Some(cc)) {
          isValidCustomViewName(createBodyJson.name)
        }
        permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
        anyCanCreate = permission.views.map(_.allowed_actions.exists(_ == CAN_CREATE_CUSTOM_VIEW)).contains(true)
        _ <- code.util.Helper.booleanToFuture(
          s"${ViewDoesNotPermitAccess} You need the `${CAN_CREATE_CUSTOM_VIEW}` permission on any your views",
          cc = Some(cc)) { anyCanCreate }
        (view, _) <- ViewNewStyle.createCustomView(BankIdAccountId(account.bankId, account.accountId), createBodyJson.toCreateViewJson, Some(cc))
      } yield JSONFactory300.createViewJSON(view)
    }

    // ─── updateViewForBankAccount ─────────────────────────────────────────────

    val updateViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" / viewIdStr =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user    <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          account <- IO.fromOption(cc.bankAccount)(new RuntimeException(AccountNotFound))
          body    <- IO.pure(cc.httpBody.getOrElse(""))
          result  <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            updateViewImpl300(user, account, ViewId(viewIdStr), body, cc))
        } yield result
        io.attempt.flatMap {
          case Right(result) =>
            Ok(com.openbankproject.commons.util.JsonAliases.prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateViewForBankAccount),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/UPD_VIEW_ID",
      "Update Custom View",
      s"""Update an existing custom view on a bank account
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      |
      |The json sent is the same as during view creation (above), with one difference: the 'name' field
      |of a view is not editable (it is only set when a view is created)""",
      updateViewJsonV300,
      viewJsonV300,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(updateViewForBankAccount)
    )

    private def updateViewImpl300(user: User, account: BankAccount, viewId: ViewId, body: String, cc: CallContext): Future[ViewJsonV300] = {
      for {
        updateBodyJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $UpdateViewJsonV300", 400, Some(cc)) {
          com.openbankproject.commons.util.JsonAliases.parse(body).extract[UpdateViewJsonV300]
        }
        _ <- code.util.Helper.booleanToFuture(
          s"$InvalidCustomViewFormat Current view_name (${viewId.value})", cc = Some(cc)) {
          updateBodyJson.metadata_view.startsWith("_")
        }
        _ <- Views.views.vend.customViewFuture(viewId, BankIdAccountId(account.bankId, account.accountId)) map { x =>
          unboxFull(fullBoxOrException(x ~> code.api.APIFailureNewStyle(
            s"$ViewNotFound. Check your post json body, metadata_view = ${updateBodyJson.metadata_view}. It should be an existing VIEW_ID, eg: owner",
            400, Some(cc.toLight))))
        }
        view   <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), Some(user), Some(cc))
        _      <- code.util.Helper.booleanToFuture(SystemViewsCanNotBeModified, cc = Some(cc)) { !view.isSystem }
        permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
        anyCanUpdate = permission.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_CUSTOM_VIEW)).contains(true)
        _ <- code.util.Helper.booleanToFuture(
          s"${ViewDoesNotPermitAccess} You need the `${CAN_UPDATE_CUSTOM_VIEW}` permission on any your views",
          cc = Some(cc)) { anyCanUpdate }
        (updatedView, _) <- ViewNewStyle.updateCustomView(BankIdAccountId(account.bankId, account.accountId), viewId, updateBodyJson.toUpdateViewJson, Some(cc))
      } yield JSONFactory300.createViewJSON(updatedView)
    }

    // ─── getPermissionForUserForBankAccount ───────────────────────────────────

    val getPermissionForUserForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "permissions" / providerStr / providerIdStr =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            permission <- NewStyle.function.permission(account.bankId, account.accountId, user, Some(cc))
            anyCanSeePermissions = permission.views
              .map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER))
              .contains(true)
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER}` permission on any your views",
              cc = Some(cc)) { anyCanSeePermissions }
            userFromURL <- Future {
              unboxFullOrFail(
                UserX.findByProviderId(providerStr, providerIdStr),
                Some(cc), UserNotFoundByProviderAndProvideId, 404)
            }
            userPermission <- NewStyle.function.permission(account.bankId, account.accountId, userFromURL, Some(cc))
          } yield createViewsJSON(userPermission.views.sortBy(_.viewId.value))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPermissionForUserForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID",
      "Get Account access for User",
      s"""Returns the list of the views at BANK_ID for account ACCOUNT_ID that a user identified by PROVIDER_ID at their provider PROVIDER has access to.
      |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER.
      |
      |${userAuthenticationMessage(true)}
      |
      |The user needs to have access to the owner view.""",
      EmptyBody,
      viewsJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, AccountNotFound, UnknownError),
      List(apiTagView, apiTagAccount, apiTagUser),
      None,
      http4sPartialFunction = Some(getPermissionForUserForBankAccount)
    )

    // ─── getPrivateAccountById ────────────────────────────────────────────────

    val getPrivateAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
          } yield createCoreBankAccountJSON(moderatedAccount)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPrivateAccountById),
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
      moderatedCoreAccountJsonV300,
      List(BankNotFound, AccountNotFound, ViewNotFound, UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(getPrivateAccountById)
    )

    // ─── getPublicAccountById ─────────────────────────────────────────────────

    val getPublicAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "public" / "accounts" / _ / _ / "account" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (bank, _)   <- NewStyle.function.getBank(cc.bank.map(_.bankId).getOrElse(BankId("")), Some(cc))
            (account, _) <- NewStyle.function.getBankAccount(bank.bankId, cc.bankAccount.map(_.accountId).getOrElse(AccountId("")), Some(cc))
            view         <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.map(_.viewId).getOrElse(ViewId("")), BankIdAccountId(account.bankId, account.accountId), cc.user, Some(cc))
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Empty, Some(cc))
          } yield createCoreBankAccountJSON(moderatedAccount)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPublicAccountById),
      "GET",
      "/banks/BANK_ID/public/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Public Account by Id",
      s"""
      |Returns information about an account that has a public view.
      |
      |The account is specified by ACCOUNT_ID. The information is moderated by the view specified by VIEW_ID.
      |
      |* Number
      |* Owners
      |* Type
      |* Balance
      |* Routing
      |
      |
      |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
      |This call provides balance and other account information via delegated authentication using OAuth.
      |
      |${userAuthenticationMessage(false)}
      |
      |""".stripMargin,
      EmptyBody,
      moderatedCoreAccountJsonV300,
      List(BankNotFound, AccountNotFound, ViewNotFound, UnknownError),
      apiTagAccountPublic :: apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(getPublicAccountById)
    )

    // ─── getCoreAccountById ───────────────────────────────────────────────────

    val getCoreAccountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" / _ / "account" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            view            <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(user, BankIdAccountId(account.bankId, account.accountId))
            createNewCoreBankAccountJson(moderatedAccount, availableViews)
          }
        }
    }

    resourceDocs += ResourceDoc(
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
      |
      |This call returns the owner view and requires access to that view.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      newModeratedCoreAccountJsonV300,
      List(BankAccountNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getCoreAccountById)
    )

    // ─── corePrivateAccountsAllBanks ──────────────────────────────────────────

    val corePrivateAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "accounts" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(user)
            (coreAccounts, _)        <- NewStyle.function.getCoreBankAccountsFuture(availablePrivateAccounts, Some(cc))
            filtered = filterCoreAccountsByType(coreAccounts, req)
          } yield JSONFactory300.createCoreAccountsByCoreAccountsJSON(filtered, user)
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
      |${accountTypeFilterText("/my/accounts")}
      |
      |${userAuthenticationMessage(true)}
      |""",
      EmptyBody,
      coreAccountsJsonV300,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagAccount, apiTagPSD2AIS, apiTagPrivateData, apiTagPsd2),
      None,
      http4sPartialFunction = Some(corePrivateAccountsAllBanks)
    )

    // ─── getFirehoseAccountsAtOneBank ─────────────────────────────────────────
    // Uses FIREHOSE_BANK_ID / FIREHOSE_VIEW_ID in the ResourceDoc URL template so middleware
    // does NOT resolve the bank/view (validateBank checks pathParams("BANK_ID") exactly).
    // Order: prop check (400) → role check (403) → bank lookup (404) — matches test expectations.

    val getFirehoseAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "firehose" / "accounts" / "views" / viewIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val roles = ApiRole.canUseAccountFirehose :: canUseAccountFirehoseAtAnyBank :: Nil
          val roleMsg = UserHasMissingRoles + roles.mkString(" or ")
          for {
            _ <- code.util.Helper.booleanToFuture(AccountFirehoseNotAllowedOnThisInstance, cc = Some(cc)) {
              allowAccountFirehose
            }
            _ <- code.util.Helper.booleanToFuture(roleMsg, failCode = 403, cc = Some(cc)) {
              APIUtil.hasAtLeastOneEntitlement(bankIdStr, user.userId, roles)
            }
            (bank, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(ViewId(viewIdStr), BankIdAccountId(bank.bankId, AccountId("")), Some(user), Some(cc))
            availableBankIdAccountIdList <- Future {
              Views.views.vend.getAllFirehoseAccounts(bank.bankId).map(a => BankIdAccountId(a.bankId, a.accountId))
            }
            params = req.uri.query.multiParams.filterNot { case (k, _) => k == PARAM_TIMESTAMP || k == PARAM_LOCALE }
            filteredList <- if (params.isEmpty) {
              Future.successful(availableBankIdAccountIdList)
            } else {
              code.accountattribute.AccountAttributeX.accountAttributeProvider.vend
                .getAccountIdsByParams(bank.bankId, params.map { case (k, vs) => k -> vs.toList })
                .map { boxedAccountIds =>
                  val accountIds = boxedAccountIds.getOrElse(Nil)
                  availableBankIdAccountIdList.filter(ba => accountIds.contains(ba.accountId.value))
                }
            }
            moderatedAccounts: List[ModeratedBankAccount] = for {
              bankIdAccountId <- filteredList
              (bankAccount, callContext) <- Connector.connector.vend.getBankAccountLegacy(bankIdAccountId.bankId, bankIdAccountId.accountId, Some(cc)) ?~! s"$BankAccountNotFound Current Bank_Id(${bankIdAccountId.bankId}), Account_Id(${bankIdAccountId.accountId})"
              moderatedAccount <- bankAccount.moderatedBankAccount(view, bankIdAccountId, Full(user), Some(cc))
            } yield moderatedAccount
            (accountAttributes: Option[List[AccountAttribute]], _) <- if (moderatedAccounts.nonEmpty && params.nonEmpty) {
              val futures = filteredList.map { bankIdAccount =>
                NewStyle.function.getAccountAttributesByAccount(bankIdAccount.bankId, bankIdAccount.accountId, Some(cc))
              }
              Future.reduceLeft(futures)((r, t) => r.copy(_1 = r._1 ::: t._1))
                .map(it => (Some(it._1), it._2))
            } else {
              Future.successful((None, Some(cc)))
            }
          } yield JSONFactory300.createFirehoseCoreBankAccountJSON(moderatedAccounts, accountAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getFirehoseAccountsAtOneBank),
      "GET",
      "/banks/FIREHOSE_BANK_ID/firehose/accounts/views/FIREHOSE_VIEW_ID",
      "Get Firehose Accounts at Bank",
      s"""
      |Get all Accounts at a Bank.
      |
      |This endpoint allows bulk access to all accounts at the specified bank.
      |
      |Requires the CanUseFirehoseAtAnyBank Role or CanUseAccountFirehose Role
      |
      |Returns all accounts at the bank. The VIEW_ID parameter determines what account data fields are visible according to the view's permissions.
      |
      |The view specified must have is_firehose = true
      |
      |For VIEW_ID try 'owner' or 'firehose'
      |
      |Optional request parameters for filtering by account attributes:
      |URL params example:
      |  /banks/some-bank-id/firehose/accounts/views/owner?limit=50&offset=1
      |
      |To invalidate browser cache, add timestamp query parameter as follows (the parameter name must be `_timestamp_`):
      |URL params example:
      |  `/banks/some-bank-id/firehose/accounts/views/owner?limit=50&offset=1&_timestamp_=1596762180358`
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      moderatedCoreAccountsJsonV300,
      List(AuthenticatedUserIsRequired, AccountFirehoseNotAllowedOnThisInstance, UnknownError),
      List(apiTagAccount, apiTagAccountFirehose, apiTagFirehoseData),
      None,
      http4sPartialFunction = Some(getFirehoseAccountsAtOneBank)
    )

    // ─── getFirehoseTransactionsForBankAccount ────────────────────────────────
    // Uses non-standard FIREHOSE_* vars so middleware skips bank/account/view validation.
    // Order: prop check (400) → role check (403) → bank/account/view lookups — matches tests.

    val getFirehoseTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "firehose" / "accounts" / accountIdStr / "views" / viewIdStr / "transactions" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val roles = ApiRole.canUseAccountFirehose :: canUseAccountFirehoseAtAnyBank :: Nil
          val roleMsg = UserHasMissingRoles + roles.mkString(" or ")
          for {
            _ <- code.util.Helper.booleanToFuture(AccountFirehoseNotAllowedOnThisInstance, cc = Some(cc)) {
              allowAccountFirehose
            }
            _ <- code.util.Helper.booleanToFuture(roleMsg, failCode = 403, cc = Some(cc)) {
              APIUtil.hasAtLeastOneEntitlement(bankIdStr, user.userId, roles)
            }
            (bank, _)    <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (account, _) <- NewStyle.function.getBankAccount(BankId(bankIdStr), AccountId(accountIdStr), Some(cc))
            view         <- ViewNewStyle.checkViewAccessAndReturnView(ViewId(viewIdStr), BankIdAccountId(bank.bankId, account.accountId), Some(user), Some(cc))
            allowedParams = List("sort_direction", "limit", "offset", "from_date", "to_date")
            httpParams    = req.uri.query.multiParams.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }.toList
            (obpQueryParams, _) <- NewStyle.function.createObpParams(httpParams, allowedParams, Some(cc))
            reqParams = req.uri.query.multiParams.filterNot { case (k, _) => allowedParams.contains(k) }
            (transactionIds, _) <- if (reqParams.nonEmpty)
              NewStyle.function.getTransactionIdsByAttributeNameValues(account.bankId, reqParams.map { case (k, vs) => k -> vs.toList }, Some(cc))
            else
              Future((List.empty[TransactionId], Some(cc)))
            (transactions, _) <- Future(account.getModeratedTransactions(bank, Full(user), view, BankIdAccountId(account.bankId, account.accountId), Some(cc), obpQueryParams)) map {
              unboxFullOrFail(_, Some(cc), UnknownError)
            }
            moderatedTransactionsWithAttributes <- Future.sequence(
              transactions.map(transaction =>
                NewStyle.function.getTransactionAttributes(account.bankId, transaction.id, Some(cc))
                  .map(attributes => ModeratedTransactionWithAttributes(transaction, attributes._1))
              )
            )
            transactionsFiltered = if (reqParams.isEmpty) moderatedTransactionsWithAttributes
            else moderatedTransactionsWithAttributes.filter(t => transactionIds.contains(t.transaction.id))
          } yield createTransactionsJson(transactionsFiltered)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getFirehoseTransactionsForBankAccount),
      "GET",
      "/banks/FIREHOSE_BANK_ID/firehose/accounts/FIREHOSE_ACCOUNT_ID/views/FIREHOSE_VIEW_ID/transactions",
      "Get Firehose Transactions for Account",
      s"""
      |Get Transactions for an Account that has a firehose View.
      |
      |Allows bulk access to an account's transactions.
      |User must have the CanUseFirehoseAtAnyBank Role
      |
      |To find ACCOUNT_IDs, use the getFirehoseAccountsAtOneBank call.
      |
      |For VIEW_ID try 'owner'
      |
      |${urlParametersDocument(true, true)}
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      transactionsJsonV300,
      List(AuthenticatedUserIsRequired, AccountFirehoseNotAllowedOnThisInstance, UserHasMissingRoles, UnknownError),
      List(apiTagTransaction, apiTagAccountFirehose, apiTagTransactionFirehose, apiTagFirehoseData),
      None,
      http4sPartialFunction = Some(getFirehoseTransactionsForBankAccount)
    )

    // ─── getCoreTransactionsForBankAccount ────────────────────────────────────

    val getCoreTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "banks" / _ / "accounts" / _ / "transactions" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            (bank, _)     <- NewStyle.function.getBank(account.bankId, Some(cc))
            view          <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(account.bankId, account.accountId), Some(cc))
            httpParams    = req.uri.query.multiParams.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }.toList
            (params, _)   <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (transactionsCore, _) <- account.getModeratedTransactionsCore(bank, Some(user), view, BankIdAccountId(account.bankId, account.accountId), params, Some(cc)) map {
              i => (unboxFullOrFail(i._1, Some(cc), UnknownError), i._2)
            }
            moderatedTransactionsCoreWithAttributes <- Future.sequence(
              transactionsCore.map(transaction =>
                NewStyle.function.getTransactionAttributes(account.bankId, transaction.id, Some(cc))
                  .map(attributes => ModeratedTransactionCoreWithAttributes(transaction, attributes._1))
              )
            )
          } yield createCoreTransactionsJSON(moderatedTransactionsCoreWithAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCoreTransactionsForBankAccount),
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions",
      "Get Transactions for Account (Core)",
      s"""Returns transactions list (Core info) of the account specified by ACCOUNT_ID.
      |
      |${userAuthenticationMessage(true)}
      |
      |${urlParametersDocument(true, true)}
      |
      |""",
      EmptyBody,
      coreTransactionsJsonV300,
      List(FilterSortDirectionError, FilterOffersetError, FilterLimitError, FilterDateFormatError,
      AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound, UnknownError),
      List(apiTagTransaction, apiTagPSD2AIS, apiTagAccount, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getCoreTransactionsForBankAccount)
    )

    // ─── getTransactionsForBankAccount ────────────────────────────────────────

    val getTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (bank, _)   <- NewStyle.function.getBank(account.bankId, Some(cc))
            httpParams  = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value))
            (params, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (transactions, _) <- account.getModeratedTransactionsFuture(bank, Some(user), view, Some(cc), params) map {
              connectorEmptyResponse(_, Some(cc))
            }
            moderatedTransactionsWithAttributes <- Future.sequence(
              transactions.map(transaction =>
                NewStyle.function.getTransactionAttributes(account.bankId, transaction.id, Some(cc))
                  .map(attributes => ModeratedTransactionWithAttributes(transaction, attributes._1))
              )
            )
          } yield createTransactionsJson(moderatedTransactionsWithAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions",
      "Get Transactions for Account (Full)",
      s"""Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
      |
      |${userAuthenticationMessage(false)}
      |
      |Authentication is required if the view is not public.
      |
      |${urlParametersDocument(true, true)}
      |
      |""",
      EmptyBody,
      transactionsJsonV300,
      List(FilterSortDirectionError, FilterOffersetError, FilterLimitError, FilterDateFormatError,
      AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound, UnknownError),
      List(apiTagTransaction, apiTagAccount),
      None,
      http4sPartialFunction = Some(getTransactionsForBankAccount)
    )

    // ─── dataWarehouseSearch ──────────────────────────────────────────────────

    private val esw = new elasticsearchWarehouse

    val dataWarehouseSearch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "search" / "warehouse" / indexStr =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user     <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          bodyText <- IO.pure(cc.httpBody.getOrElse(""))
          result   <- code.api.util.http4s.RequestScopeConnection.fromFuture {
            for {
              _ <- code.util.Helper.booleanToFuture(ElasticSearchDisabled, cc = Some(cc)) { esw.isEnabled() }
              json     <- Future { unboxFullOrFail(tryo(com.openbankproject.commons.util.JsonAliases.parse(bodyText)), Some(cc), ElasticSearchEmptyQueryBody) }
              maximumSize = APIUtil.getPropsAsIntValue("es.warehouse.allowed.maximum.pagesize", 10000)
              _ <- code.util.Helper.booleanToFuture(
                maximumLimitExceeded.replace("Maximum number is 10000.", s"Please check query body, the maximum size is $maximumSize."),
                cc = Some(cc)) {
                val allSizeFields = json filterField { case JField(key, _) => key.equals("size") }
                allSizeFields.map(_.value.values.toString.toInt).find(_ > maximumSize).isEmpty
              }
              indexPart <- Future { unboxFullOrFail(esw.getElasticSearchUri(indexStr), Some(cc), ElasticSearchIndexNotFound) }
              bodyPart  <- Future { unboxFullOrFail(tryo(compactRender(json)), Some(cc), ElasticSearchEmptyQueryBody) }
              result    <- esw.searchProxyAsyncV300(user.userId, indexPart, bodyPart)
            } yield esw.parseResponse(result)
          }
        } yield result
        io.attempt.flatMap {
          case Right(r) => Ok(com.openbankproject.commons.util.JsonAliases.prettyRender(org.json4s.Extraction.decompose(r)))
          case Left(e)  => code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(e, cc)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(dataWarehouseSearch),
      "POST",
      "/search/warehouse/INDEX",
      "Data Warehouse Search",
      s"""
       |Search the data warehouse and get row level results.
       |
       |${userAuthenticationMessage(true)}
       |
       |CanSearchWarehouse entitlement is required. You can request the Role below.
       |
       |Elastic (search) is used in the background. See links below for syntax.
       |
       |Examples of usage:
       |
       |
       |POST /search/warehouse/THE_INDEX_YOU_WANT_TO_USE
       |
       |POST /search/warehouse/INDEX1,INDEX2
       |
       |POST /search/warehouse/ALL
       |
       |{ Any valid elasticsearch query DSL in the body }
       |
       |
       |[Elasticsearch query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
       |
       |[Elastic simple query](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-request-body.html)
       |
       |[Elastic aggregations](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-aggregations.html)
       |
       |
      """,
      elasticSearchJsonV300,
      emptyElasticSearch,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSearchWarehouse),
      Some(List(canSearchWarehouse)),
      http4sPartialFunction = Some(dataWarehouseSearch)
    )

    // ─── dataWarehouseStatistics ──────────────────────────────────────────────

    val dataWarehouseStatistics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "search" / "warehouse" / "statistics" / indexStr / fieldStr =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user     <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          bodyText <- IO.pure(cc.httpBody.getOrElse(""))
          result   <- code.api.util.http4s.RequestScopeConnection.fromFuture {
            for {
              _ <- code.util.Helper.booleanToFuture(ElasticSearchDisabled, cc = Some(cc)) { esw.isEnabled() }
              json     <- Future { unboxFullOrFail(tryo(com.openbankproject.commons.util.JsonAliases.parse(bodyText)), Some(cc), ElasticSearchEmptyQueryBody) }
              maximumSize = APIUtil.getPropsAsIntValue("es.warehouse.allowed.maximum.pagesize", 10000)
              _ <- code.util.Helper.booleanToFuture(
                maximumLimitExceeded.replace("Maximum number is 10000.", s"Please check query body, the maximum size is $maximumSize."),
                cc = Some(cc)) {
                val allSizeFields = json filterField { case JField(key, _) => key.equals("size") }
                allSizeFields.map(_.value.values.toString.toInt).find(_ > maximumSize).isEmpty
              }
              indexPart <- Future { unboxFullOrFail(esw.getElasticSearchUri(indexStr), Some(cc), ElasticSearchIndexNotFound) }
              bodyPart  <- Future { unboxFullOrFail(tryo(compactRender(json)), Some(cc), ElasticSearchEmptyQueryBody) }
              result    <- esw.searchProxyStatsAsyncV300(user.userId, indexPart, bodyPart, fieldStr)
            } yield esw.parseResponse(result, true)
          }
        } yield result
        io.attempt.flatMap {
          case Right(r) => Ok(com.openbankproject.commons.util.JsonAliases.prettyRender(org.json4s.Extraction.decompose(r)))
          case Left(e)  => code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(e, cc)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(dataWarehouseStatistics),
      "POST",
      "/search/warehouse/statistics/INDEX/FIELD",
      "Data Warehouse Statistics",
      s"""
       |Search the data warehouse and get statistical aggregations over a warehouse field
       |
       |Does a stats aggregation over some numeric field:
       |
       |https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-stats-aggregation.html
       |
       |${userAuthenticationMessage(true)}
       |
       |CanSearchWarehouseStats Role is required. You can request this below.
       |
       |Elastic (search) is used in the background. See links below for syntax.
       |
       |Examples of usage:
       |
       |POST /search/warehouse/statistics/INDEX/FIELD
       |
       |POST /search/warehouse/statistics/ALL/FIELD
       |
       |{ Any valid elasticsearch query DSL in the body }
       |
       |
       |[Elasticsearch query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
       |
       |[Elastic simple query](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-request-body.html)
       |
       |[Elastic aggregations](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-aggregations.html)
       |
       |
      """,
      elasticSearchJsonV300,
      emptyElasticSearch,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSearchWarehouse),
      Some(List(canSearchWarehouseStatistics)),
      http4sPartialFunction = Some(dataWarehouseStatistics)
    )

    // ─── getUser (by email) ───────────────────────────────────────────────────

    val getUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "email" / emailStr / "terminator" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetAnyUser, Some(cc))
            users <- Users.users.vend.getUserByEmailFuture(emailStr)
          } yield JSONFactory300.createUserJSONs(users)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getUser),
      "GET",
      "/users/email/USER_EMAIL/terminator",
      "Get Users by Email Address",
      s"""Get users by email address
         |
         |${userAuthenticationMessage(true)}
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      usersJsonV200,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUser)
    )

    // ─── getUserByUserId ──────────────────────────────────────────────────────

    val getUserByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "user_id" / userIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetAnyUser, Some(cc))
            targetUser <- Users.users.vend.getUserByUserIdFuture(userIdStr) map {
              x => unboxFullOrFail(x, Some(cc), s"$UserNotFoundByUserId Current UserId($userIdStr)")
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(targetUser.userId, Some(cc))
          } yield JSONFactory300.createUserJSON(targetUser, entitlements)
        }
    }

    resourceDocs += ResourceDoc(
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
      usersJsonV200,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundById, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUserByUserId)
    )

    // ─── getUserByUsername ────────────────────────────────────────────────────

    val getUserByUsername: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "username" / usernameStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetAnyUser, Some(cc))
            targetUser <- Users.users.vend.getUserByProviderAndUsernameFuture(Constant.localIdentityProvider, usernameStr) map {
              x => unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(targetUser.userId, Some(cc))
          } yield JSONFactory300.createUserJSON(targetUser, entitlements)
        }
    }

    resourceDocs += ResourceDoc(
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
      usersJsonV200,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByProviderAndUsername, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUserByUsername)
    )

    // ─── getAdapterInfoForBank ────────────────────────────────────────────────

    val getAdapterInfoForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "adapter" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _        <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canGetAdapterInfoAtOneBank, Some(cc))
            (ai, _)  <- NewStyle.function.getAdapterInfo(Some(cc))
          } yield createAdapterInfoJson(ai)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAdapterInfoForBank),
      "GET",
      "/banks/BANK_ID/adapter",
      "Get Adapter Info for a bank",
      s"""Get basic information about the Adapter listening on behalf of this bank.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      adapterInfoJsonV300,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagApi),
      Some(List(canGetAdapterInfoAtOneBank)),
      http4sPartialFunction = Some(getAdapterInfoForBank)
    )

    // ─── createBranch ─────────────────────────────────────────────────────────

    val createBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "branches" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[BranchJsonV300, BranchJsonV300](req) { (user, bank, body, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(InsufficientAuthorisationToCreateBranch)(
              bank.bankId.value, user.userId, canCreateBranch :: canCreateBranchAtAnyBank :: Nil, Some(cc))
            _ <- code.util.Helper.booleanToFuture("BANK_ID has to be the same in the URL and Body", 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, Some(cc)) {
              transformToBranch(body)
            }
            (success, _) <- NewStyle.function.createOrUpdateBranch(branch, Some(cc))
          } yield JSONFactory300.createBranchJsonV300(success)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createBranch),
      "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create Branch for the Bank.
      |
      |${userAuthenticationMessage(true) }
      |
      |""",
      branchJsonV300,
      branchJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, InsufficientAuthorisationToCreateBranch, UnknownError),
      List(apiTagBranch),
      Some(List(canCreateBranch, canCreateBranchAtAnyBank)),
      http4sPartialFunction = Some(createBranch)
    )

    // ─── updateBranch ─────────────────────────────────────────────────────────

    val updateBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "branches" / branchIdStr =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostBranchJsonV300, BranchJsonV300](req) { (user, bank, body, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateBranch, Some(cc))
            _ <- code.util.Helper.booleanToFuture("BANK_ID has to be the same in the URL and Body", 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            branchJson = BranchJsonV300(
              id = branchIdStr,
              body.bank_id, body.name, body.address, body.location, body.meta,
              body.lobby, body.drive_up, body.branch_routing,
              body.is_accessible, body.accessibleFeatures, body.branch_type, body.more_info, body.phone_number)
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, Some(cc)) {
              transformToBranchFromV300(branchJson).head
            }
            (success, _) <- NewStyle.function.createOrUpdateBranch(branch, Some(cc))
          } yield JSONFactory300.createBranchJsonV300(success)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateBranch),
      "PUT",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Update Branch",
      s"""Update an existing branch for a bank account (Authenticated access).
      |
      |${userAuthenticationMessage(true) }
      |
      |""",
      postBranchJsonV300,
      branchJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, InsufficientAuthorisationToCreateBranch, UnknownError),
      List(apiTagBranch),
      Some(List(canUpdateBranch)),
      http4sPartialFunction = Some(updateBranch)
    )

    // ─── createAtm ────────────────────────────────────────────────────────────

    val createAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AtmJsonV300, AtmJsonV300](req) { (user, bank, body, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(createAtmEntitlementsRequiredText)(
              bank.bankId.value, user.userId, createAtmEntitlements, Some(cc))
            _ <- code.util.Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            atm <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Atm", 400, Some(cc)) {
              transformToAtmFromV300(body).head
            }
            (createdAtm, _) <- NewStyle.function.createOrUpdateAtm(atm, Some(cc))
          } yield JSONFactory300.createAtmJsonV300(createdAtm)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAtm),
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM for the Bank.
      |
      |${userAuthenticationMessage(true) }
      |
      |""",
      atmJsonV300,
      atmJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagATM),
      Some(List(canCreateAtm, canCreateAtmAtAnyBank)),
      http4sPartialFunction = Some(createAtm)
    )

    // ─── getBranch ────────────────────────────────────────────────────────────

    private val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    val getBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "branches" / branchIdStr =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getBranchesIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            (branch, _) <- NewStyle.function.getBranch(bank.bankId, BranchId(branchIdStr), Some(cc))
          } yield JSONFactory300.createBranchJsonV300(branch)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBranch),
      "GET",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Get Branch",
      s"""Returns information about a single Branch specified by BANK_ID and BRANCH_ID including:
      |
      |* Name
      |* Address
      |* Geo Location
      |* License the data under this endpoint is released under.
      |
      |${userAuthenticationMessage(!getBranchesIsPublic)}""".stripMargin,
      EmptyBody,
      branchJsonV300,
      List(AuthenticatedUserIsRequired, BranchNotFoundByBranchId, UnknownError),
      List(apiTagBranch, apiTagBank),
      None,
      http4sPartialFunction = Some(getBranch)
    )

    // ─── getBranches ──────────────────────────────────────────────────────────

    private[this] val branchCityPredicate = (city: Option[String], branchCity: String) =>
      city.isEmpty || city.contains(branchCity)

    private[this] val reg = Pattern.compile("^[-+]?(\\d+\\.?\\d*$|\\d*\\.?\\d+$)")

    private[this] def distancePredicate(
      withinMetersOf: Option[String], nearLatitude: Option[String], nearLongitude: Option[String],
      latitude: Double, longitude: Double): Boolean = {
      (withinMetersOf, nearLatitude, nearLongitude) match {
        case (None, None, None) => true
        case (Some(wm), Some(nlat), Some(nlng)) =>
          val fromLat = Coordinate.fromDegrees(nlat.toDouble)
          val fromLng = Coordinate.fromDegrees(nlng.toDouble)
          val fromPoint = Point.at(fromLat, fromLng)
          val branchLat = Coordinate.fromDegrees(latitude)
          val branchLng = Coordinate.fromDegrees(longitude)
          val branchPoint = Point.at(branchLat, branchLng)
          val distance = EarthCalc.harvesineDistance(branchPoint, fromPoint)
          wm.toDouble >= distance
        case _ => true
      }
    }

    val getBranches: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "branches" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          val qp = req.uri.query.params
          val limit            = qp.get("limit")
          val offset           = qp.get("offset")
          val city             = qp.get("city")
          val withinMetersOf   = qp.get("withinMetersOf")
          val nearLatitude     = qp.get("nearLatitude")
          val nearLongitude    = qp.get("nearLongitude")
          for {
            _ <- if (!getBranchesIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            _ <- code.util.Helper.booleanToFuture(s"${InvalidNumber} limit:${limit.getOrElse("")}", cc = Some(cc)) {
              limit.forall(_.forall(Character.isDigit))
            }
            _ <- code.util.Helper.booleanToFuture(maximumLimitExceeded, cc = Some(cc)) {
              !limit.exists(_.toInt > 10000)
            }
            _ <- code.util.Helper.booleanToFuture(s"${InvalidNumber} offset:${offset.getOrElse("")}", cc = Some(cc)) {
              offset.forall(_.forall(Character.isDigit))
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${MissingQueryParams} withinMetersOf, nearLatitude and nearLongitude must be either all empty or all float value",
              cc = Some(cc)) {
              (withinMetersOf, nearLatitude, nearLongitude) match {
                case (Some(i), Some(j), Some(k)) => reg.matcher(i).matches() && reg.matcher(j).matches() && reg.matcher(k).matches()
                case (None, None, None)           => true
                case _                            => false
              }
            }
            branches <- Connector.connector.vend.getBranches(bank.bankId, Some(cc)) map {
              case Empty                          => unboxFullOrFail(Empty ?~! BranchesNotFound, Some(cc), BranchesNotFound)
              case Full((Nil, _))                 => Nil
              case Full((list, _))                => list
              case Failure(msg, _, _)             => unboxFullOrFail(Empty ?~! msg, Some(cc), msg)
              case ParamFailure(msg, _, _, _)     => unboxFullOrFail(Empty ?~! msg, Some(cc), msg)
            } map { branches =>
              branches
                .sortWith(_.branchId.value < _.branchId.value)
                .filter(_.isDeleted != Some(true))
                .filter(b => branchCityPredicate(city, b.address.city))
                .filter(b => distancePredicate(withinMetersOf, nearLatitude, nearLongitude, b.location.latitude, b.location.longitude))
                .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
            }
          } yield JSONFactory300.createBranchesJson(branches)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBranches),
      "GET",
      "/banks/BANK_ID/branches",
      "Get Branches for a Bank",
      s"""Returns information about branches for a single bank specified by BANK_ID including:
      |
      |* Name
      |* Address
      |* Geo Location
      |* License the data under this endpoint is released under
      |* Structured opening hours
      |* Accessible flag
      |* Branch Type
      |* More Info
      |
      |Pagination:
      |
      |By default, 50 records are returned.
      |
      |You can use the url query parameters *limit* and *offset* for pagination
      |You can also use the follow url query parameters:
      |
      |  - city - string, find Branches those in this city, optional
      |
      |
      |  - withinMetersOf - number, find Branches within given meters distance, optional
      |  - nearLatitude - number, a position of latitude value, cooperate with withMetersOf do query filter, optional
      |  - nearLongitude - number, a position of longitude value, cooperate with withMetersOf do query filter, optional
      |
      |note: withinMetersOf, nearLatitude and nearLongitude either all empty or all have value.
      |
      |${userAuthenticationMessage(!getBranchesIsPublic)}""".stripMargin,
      EmptyBody,
      branchesJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, BranchesNotFoundLicense, UnknownError),
      List(apiTagBranch, apiTagBank),
      None,
      http4sPartialFunction = Some(getBranches)
    )

    // ─── getAtm ───────────────────────────────────────────────────────────────

    private val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    val getAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "atms" / atmIdStr =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getAtmsIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            (atm, _) <- NewStyle.function.getAtm(bank.bankId, AtmId(atmIdStr), Some(cc))
          } yield JSONFactory300.createAtmJsonV300(atm)
        }
    }

    resourceDocs += ResourceDoc(
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
      |
      |
      |
      |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      List(apiTagATM),
      None,
      http4sPartialFunction = Some(getAtm)
    )

    // ─── getAtms ──────────────────────────────────────────────────────────────

    val getAtms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "atms" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          val qp     = req.uri.query.params
          val limit  = qp.get("limit")
          val offset = qp.get("offset")
          for {
            _ <- if (!getAtmsIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            _ <- code.util.Helper.booleanToFuture(s"${InvalidNumber} limit:${limit.getOrElse("")}", cc = Some(cc)) {
              limit.forall(_.forall(Character.isDigit))
            }
            _ <- code.util.Helper.booleanToFuture(maximumLimitExceeded, cc = Some(cc)) {
              !limit.exists(_.toInt > 10000)
            }
            _ <- code.util.Helper.booleanToFuture(s"${InvalidNumber} offset:${offset.getOrElse("")}", cc = Some(cc)) {
              offset.forall(_.forall(Character.isDigit))
            }
            atms <- Connector.connector.vend.getAtms(bank.bankId, Some(cc)) map {
              case Empty                      => unboxFullOrFail(Empty ?~! atmsNotFound, Some(cc), atmsNotFound)
              case Full((Nil, _))             => Nil
              case Full((list, _))            => list
              case Failure(msg, _, _)         => unboxFullOrFail(Empty ?~! msg, Some(cc), msg)
              case ParamFailure(msg, _, _, _) => unboxFullOrFail(Empty ?~! msg, Some(cc), msg)
            } map { atms =>
              atms
                .sortWith(_.atmId.value < _.atmId.value)
                .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
            }
          } yield JSONFactory300.createAtmsJsonV300(atms)
        }
    }

    resourceDocs += ResourceDoc(
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
      atmJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagATM),
      None,
      http4sPartialFunction = Some(getAtms)
    )

    // ─── getUsers ─────────────────────────────────────────────────────────────

    val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetAnyUser, Some(cc))
            httpParams    = req.uri.query.multiParams.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }.toList
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            users <- Users.users.vend.getAllUsersF(obpQueryParams)
          } yield JSONFactory300.createUserJSONs(users)
        }
    }

    resourceDocs += ResourceDoc(
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
      usersJsonV200,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUsers)
    )

    // ─── getCustomersForUser ──────────────────────────────────────────────────

    val getCustomersForUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "customers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (customers, _) <- Connector.connector.vend.getCustomersByUserId(user.userId, Some(cc)) map {
              connectorEmptyResponse(_, Some(cc))
            }
            (customersAndAttributes, _) <- NewStyle.function.getCustomerAttributesForCustomers(customers, Some(cc))
          } yield JSONFactory300.createCustomersWithAttributesJson(customersAndAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomersForUser),
      "GET",
      "/users/current/customers",
      "Get Customers for Current User",
      s"""Gets all Customers that are linked to a User.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      customersWithAttributesJsonV300,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagUser),
      None,
      http4sPartialFunction = Some(getCustomersForUser)
    )

    // ─── getCurrentUser ───────────────────────────────────────────────────────

    val getCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
          } yield {
            val permissions = Views.views.vend.getPermissionForUser(user).toOption
            JSONFactory300.createUserInfoJSON(user, entitlements, permissions)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCurrentUser),
      "GET",
      "/users/current",
      "Get User (Current)",
      s"""Get the logged in user
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      userJsonV300,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagUser),
      None,
      http4sPartialFunction = Some(getCurrentUser)
    )

    // ─── privateAccountsAtOneBank ─────────────────────────────────────────────

    val privateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / "private" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(user, bank.bankId)
            (accounts, _)            <- NewStyle.function.getCoreBankAccountsFuture(availablePrivateAccounts, Some(cc))
            filtered = filterCoreAccountsByType(accounts, req)
          } yield JSONFactory300.createCoreAccountsByCoreAccountsJSON(filtered, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(privateAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get Accounts at Bank (Minimal)",
      s"""Returns the minimal list of private accounts at BANK_ID that the user has access to.
      |For each account, the API returns the ID, routing addresses and the views available to the current user.
      |
      |If you want to see more information on the Views, use the Account Detail call.
      |
      |${accountTypeFilterText("/banks/BANK_ID/accounts/private")}
      |
      |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      coreAccountsJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(privateAccountsAtOneBank)
    )

    // ─── getPrivateAccountIdsbyBankId ─────────────────────────────────────────

    val getPrivateAccountIdsbyBankId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / "account_ids" / "private" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(user, bank.bankId)
            (coreAccounts, _)        <- NewStyle.function.getCoreBankAccountsFuture(availablePrivateAccounts, Some(cc))
            filtered = filterCoreAccountsByType(coreAccounts, req)
            bankIdAccountIds = filtered.map(a => BankIdAccountId(bank.bankId, AccountId(a.id)))
          } yield JSONFactory300.createAccountsIdsByBankIdAccountIds(bankIdAccountIds)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPrivateAccountIdsbyBankId),
      "GET",
      "/banks/BANK_ID/accounts/account_ids/private",
      "Get Accounts at Bank (IDs only)",
      s"""Returns only the list of accounts ids at BANK_ID that the user has access to.
      |
      |Each account must have at least one private View.
      |
      |For each account the API returns its account ID.
      |
      |If you want to see more information on the Views, use the Account Detail call.
      |
      |${accountTypeFilterText("/banks/BANK_ID/accounts/account_ids/private")}
      |
      |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      accountsIdsJsonV300,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagAccount, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getPrivateAccountIdsbyBankId)
    )

    // ─── getOtherAccountsForBankAccount ───────────────────────────────────────

    val getOtherAccountsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccounts, _) <- NewStyle.function.moderatedOtherBankAccounts(account, view, Some(user), Some(cc))
          } yield createOtherBankAccountsJson(otherBankAccounts)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getOtherAccountsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts",
      "Get Other Accounts of one Account",
      s"""Returns data about all the other accounts that have shared at least one transaction with the ACCOUNT_ID at BANK_ID.
      |${userAuthenticationMessage(false)}
      |
      |Authentication is required if the view VIEW_ID is not public.""",
      EmptyBody,
      otherAccountsJsonV300,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound, InvalidConnectorResponse, UnknownError),
      List(apiTagCounterparty, apiTagAccount),
      None,
      http4sPartialFunction = Some(getOtherAccountsForBankAccount)
    )

    // ─── getOtherAccountByIdForBankAccount ────────────────────────────────────

    val getOtherAccountByIdForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountIdStr =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountIdStr, view, Some(user), Some(cc))
          } yield createOtherBankAccount(otherBankAccount)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getOtherAccountByIdForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
      "Get Other Account by Id",
      s"""Returns data about the Other Account that has shared at least one transaction with ACCOUNT_ID at BANK_ID.
      |${userAuthenticationMessage(false)}
      |
      |Authentication is required if the view is not public.""",
      EmptyBody,
      otherAccountJsonV300,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound, InvalidConnectorResponse, UnknownError),
      List(apiTagCounterparty, apiTagAccount),
      None,
      http4sPartialFunction = Some(getOtherAccountByIdForBankAccount)
    )

    // ─── addEntitlementRequest ────────────────────────────────────────────────

    val addEntitlementRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "entitlement-requests" =>
        EndpointHelpers.withUserAndBodyCreated[CreateEntitlementRequestJSON, EntitlementRequestJSON](req) { (user, body, cc) =>
          for {
            _ <- if (body.bank_id.isEmpty) Future.successful(())
                 else NewStyle.function.getBank(BankId(body.bank_id), Some(cc)).map(_ => ())
            _ <- code.util.Helper.booleanToFuture(
              IncorrectRoleName + body.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", "),
              cc = Some(cc)) { availableRoles.exists(_ == body.role_name) }
            _ <- code.util.Helper.booleanToFuture(
              if (ApiRole.valueOf(body.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole,
              cc = Some(cc)) { ApiRole.valueOf(body.role_name).requiresBankId == body.bank_id.nonEmpty }
            // A request for power is a request BY the human: under a Consent the caller is a
            // per-consent shadow, and a request filed for it would have an admin granting to
            // an identity that dies with the consent (the grant endpoint now rejects that).
            requesterUserId = cc.accountableUserId
            _ <- code.util.Helper.booleanToFuture(EntitlementRequestAlreadyExists, cc = Some(cc)) {
              EntitlementRequest.entitlementRequest.vend.getEntitlementRequest(body.bank_id, requesterUserId, body.role_name).isEmpty
            }
            addedEntitlementRequest <- EntitlementRequest.entitlementRequest.vend.addEntitlementRequestFuture(body.bank_id, requesterUserId, body.role_name) map {
              x => unboxFullOrFail(x, Some(cc), EntitlementRequestCannotBeAdded)
            }
          } yield JSONFactory300.createEntitlementRequestJSON(addedEntitlementRequest)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(addEntitlementRequest),
      "POST",
      "/entitlement-requests",
      "Create Entitlement Request for current User",
      s"""Create Entitlement Request.
       |
       |Any logged in User can use this endpoint to request an Entitlement
       |
       |Entitlements are used to grant System or Bank level roles to Users. (For Account level privileges, see Views)
       |
       |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
       |
       |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
       |
       |
       |
       |${userAuthenticationMessage(true)}
       |
      """.stripMargin,
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createEntitlementJSON,
      entitlementRequestJSON,
      List(AuthenticatedUserIsRequired, UserNotFoundById, InvalidJsonFormat, IncorrectRoleName,
      EntitlementIsBankRole, EntitlementIsSystemRole, EntitlementRequestAlreadyExists, EntitlementRequestCannotBeAdded, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None,
      http4sPartialFunction = Some(addEntitlementRequest)
    )

    // ─── getAllEntitlementRequests ─────────────────────────────────────────────

    val getAllEntitlementRequests: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "entitlement-requests" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val allowedEntitlements    = canGetEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(s"$UserHasMissingRoles $allowedEntitlementsTxt")("", user.userId, allowedEntitlements, Some(cc))
            httpParams = req.uri.query.multiParams.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }.toList
            (requestParams, _) <- NewStyle.function.extractQueryParams(req.uri.renderString, List("limit", "offset", "sort_direction", "from_date", "to_date"), Some(cc))
            entitlementRequests <- NewStyle.function.getEntitlementRequestsFuture(requestParams, Some(cc))
          } yield JSONFactory300.createEntitlementRequestsJSON(entitlementRequests)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAllEntitlementRequests),
      "GET",
      "/entitlement-requests",
      "Get all Entitlement Requests",
      s"""
         |Get all Entitlement Requests
         |
         |${urlParametersDocument(true, true)}
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      entitlementRequestsJSON,
      List(AuthenticatedUserIsRequired, InvalidConnectorResponse, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementRequestsAtOneBank, canGetEntitlementRequestsAtAnyBank)),
      http4sPartialFunction = Some(getAllEntitlementRequests)
    )

    // ─── getEntitlementRequests ───────────────────────────────────────────────

    val getEntitlementRequests: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userIdStr / "entitlement-requests" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val allowedEntitlements    = canGetEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(s"$UserHasMissingRoles $allowedEntitlementsTxt")("", user.userId, allowedEntitlements, Some(cc))
            (requestParams, _) <- NewStyle.function.extractQueryParams(req.uri.renderString, List("limit", "offset", "sort_direction", "from_date", "to_date"), Some(cc))
            entitlementRequests <- NewStyle.function.getEntitlementRequestsFuture(userIdStr, requestParams, Some(cc))
          } yield JSONFactory300.createEntitlementRequestsJSON(entitlementRequests)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getEntitlementRequests),
      "GET",
      "/users/USER_ID/entitlement-requests",
      "Get Entitlement Requests for a User",
      s"""Get Entitlement Requests for a User.
       |
       |${urlParametersDocument(true, true)}
       |
       |${userAuthenticationMessage(true)}
       |
      """.stripMargin,
      EmptyBody,
      entitlementRequestsJSON,
      List(AuthenticatedUserIsRequired, InvalidConnectorResponse, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementRequestsAtOneBank, canGetEntitlementRequestsAtAnyBank)),
      http4sPartialFunction = Some(getEntitlementRequests)
    )

    // ─── getEntitlementRequestsForCurrentUser ─────────────────────────────────

    val getEntitlementRequestsForCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "entitlement-requests" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(req.uri.renderString, List("limit", "offset", "sort_direction", "from_date", "to_date"), Some(cc))
            entitlementRequests <- NewStyle.function.getEntitlementRequestsFuture(user.userId, requestParams, Some(cc))
          } yield JSONFactory300.createEntitlementRequestsJSON(entitlementRequests)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getEntitlementRequestsForCurrentUser),
      "GET",
      "/my/entitlement-requests",
      "Get Entitlement Requests for the current User",
      s"""Get Entitlement Requests for the current User.
       |
       |${urlParametersDocument(true, true)}
       |
       |${userAuthenticationMessage(true)}
       |
      """.stripMargin,
      EmptyBody,
      entitlementRequestsJSON,
      List(AuthenticatedUserIsRequired, InvalidConnectorResponse, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None,
      http4sPartialFunction = Some(getEntitlementRequestsForCurrentUser)
    )

    // ─── deleteEntitlementRequest ─────────────────────────────────────────────

    val deleteEntitlementRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "entitlement-requests" / entitlementRequestIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val allowedEntitlements = canDeleteEntitlementRequestsAtOneBank :: canDeleteEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = s"$UserHasMissingRoles ${allowedEntitlements.mkString(" or ")}"
          for {
            entitlementRequest <- EntitlementRequest.entitlementRequest.vend.getEntitlementRequestFuture(entitlementRequestIdStr) map {
              connectorEmptyResponse(_, Some(cc))
            }
            _ <- NewStyle.function.hasAtLeastOneEntitlement(allowedEntitlementsTxt)(entitlementRequest.bankId, user.userId, allowedEntitlements, Some(cc))
            result <- EntitlementRequest.entitlementRequest.vend.deleteEntitlementRequestFuture(entitlementRequestIdStr) map {
              connectorEmptyResponse(_, Some(cc))
            }
          } yield org.json4s.JBool(result)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteEntitlementRequest),
      "DELETE",
      "/entitlement-requests/ENTITLEMENT_REQUEST_ID",
      "Delete Entitlement Request",
      s"""Delete the Entitlement Request specified by ENTITLEMENT_REQUEST_ID for a user specified by USER_ID
         |
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, InvalidConnectorResponse, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canDeleteEntitlementRequestsAtOneBank, canDeleteEntitlementRequestsAtAnyBank)),
      http4sPartialFunction = Some(deleteEntitlementRequest)
    )

    // ─── getEntitlementsForCurrentUser ────────────────────────────────────────

    val getEntitlementsForCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "entitlements" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
          } yield {
            if (isSuperAdmin(user.userId))
              JSONFactory200.withVirtualEntitlements(entitlements, JSONFactory200.superAdminVirtualRoles)
            else if (isOidcOperator(user.userId))
              JSONFactory200.withVirtualEntitlements(entitlements, JSONFactory200.oidcOperatorVirtualRoles)
            else
              JSONFactory200.createEntitlementJSONs(entitlements)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getEntitlementsForCurrentUser),
      "GET",
      "/my/entitlements",
      "Get Entitlements for the current User",
      s"""Get Entitlements for the current User.
       |
       |
       |${userAuthenticationMessage(true)}
       |
      """.stripMargin,
      EmptyBody,
      entitlementJSONs,
      List(AuthenticatedUserIsRequired, InvalidConnectorResponse, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None,
      http4sPartialFunction = Some(getEntitlementsForCurrentUser)
    )

    // ─── getApiGlossary ───────────────────────────────────────────────────────

    private val glossaryDocsRequireRole = APIUtil.getPropsAsBoolValue("apiOptions.glossaryDocsRequireRole", false)
    private lazy val cachedGlossaryJson = JSONFactory300.createGlossaryItemsJsonV300(getGlossaryItems)

    val getApiGlossary: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "api" / "glossary" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            _ <- if (glossaryDocsRequireRole) {
              code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }.flatMap { _ =>
                NewStyle.function.hasEntitlement("", cc.user.openOrThrowException("user required").userId, ApiRole.canReadGlossary, Some(cc))
              }
            } else Future.unit
          } yield cachedGlossaryJson
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getApiGlossary),
      "GET",
      "/api/glossary",
      "Get Glossary of the API",
      """Get API Glossary
      |
      |Returns the glossary of the API.
      |
      |The glossary content is static and only changes when the API is redeployed.
      |This endpoint supports HTTP caching:
      |
      |* The response includes a **Cache-Control** header (max-age=3600) indicating clients should cache for 1 hour.
      |* The response includes an **ETag** header. Clients can send **If-None-Match** with the ETag value on subsequent requests to receive a **304 Not Modified** if the content has not changed.
      |
      |Clients and agents are encouraged to cache the glossary response locally.
      |
      |""",
      EmptyBody,
      glossaryItemsJsonV300,
      List(UnknownError),
      apiTagDocumentation :: Nil,
      None,
      http4sPartialFunction = Some(getApiGlossary)
    )

    // ─── getAccountsHeld ──────────────────────────────────────────────────────

    val getAccountsHeld: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts-held" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (availableAccounts, _) <- NewStyle.function.getAccountsHeld(bank.bankId, user, Some(cc))
            (accounts, _)          <- NewStyle.function.getBankAccountsHeldFuture(availableAccounts.toList, Some(cc))
            (coreAccounts, _)      <- NewStyle.function.getCoreBankAccountsFuture(availableAccounts.toList, Some(cc))
            filtered = filterCoreAccountsByType(coreAccounts, req)
            accountHelds = accounts.filter(a => filtered.map(_.id).contains(a.id))
          } yield JSONFactory300.createCoreAccountsByCoreAccountsJSON(accountHelds)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountsHeld),
      "GET",
      "/banks/BANK_ID/accounts-held",
      "Get Accounts Held",
      s"""Get Accounts held by the current User if even the User has not been assigned the owner View yet.
       |
       |Can be used to onboard the account to the API - since all other account and transaction endpoints require views to be assigned.
       |
       |${accountTypeFilterText("/banks/BANK_ID/accounts-held")}
       |
       |
       |
      """.stripMargin,
      EmptyBody,
      coreAccountsHeldJsonV300,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagAccount, apiTagPSD2AIS, apiTagView, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getAccountsHeld)
    )

    // ─── getAggregateMetrics ──────────────────────────────────────────────────

    val getAggregateMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "aggregate-metrics" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canReadAggregateMetrics, Some(cc))
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams, false) map {
              x => unboxFullOrFail(x, Some(cc), GetAggregateMetricsError)
            }
          } yield createAggregateMetricJson(aggregateMetrics)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAggregateMetrics),
      "GET",
      "/management/aggregate-metrics",
      "Get Aggregate Metrics",
      s"""Returns aggregate metrics on api usage eg. total count, response time (in ms), etc.
         |
         |Should be able to filter on the following fields
         |
         |eg: /management/aggregate-metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5
         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
         |&verb=GET&anon=false&app_name=MapperPostman
         |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |1 from_date (defaults to the day before the current date): eg:from_date=$DateWithMsExampleString
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
         |8 implemented_by_partial_function (if null ignore),
         |
         |9 implemented_in_version (if null ignore)
         |
         |10 verb (if null ignore)
         |
         |11 correlation_id (if null ignore)
         |
         |12 duration (if null ignore) non digit chars will be silently omitted
         |
         |13 exclude_app_names (if null ignore).eg: &exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |14 exclude_url_patterns (if null ignore).you can design you own SQL NOT LIKE pattern. eg: &exclude_url_patterns=%management/metrics%,%management/aggregate-metrics%
         |
         |15 exclude_implemented_by_partial_functions (if null ignore).eg: &exclude_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      aggregateMetricsJSONV300,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagAggregateMetrics),
      Some(List(canReadAggregateMetrics)),
      http4sPartialFunction = Some(getAggregateMetrics)
    )

    // ─── addScope ─────────────────────────────────────────────────────────────

    val addScope: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "consumers" / consumerIdStr / "scopes" =>
        EndpointHelpers.withUserAndBodyCreated[CreateScopeJson, ScopeJson](req) { (user, body, cc) =>
          for {
            consumerIdInt <- Future { tryo(consumerIdStr.toInt) } map {
              x => unboxFullOrFail(x, Some(cc), s"$ConsumerNotFoundById Current Value is $consumerIdStr")
            }
            _ <- Future { Consumers.consumers.vend.getConsumerByPrimaryId(consumerIdInt) } map {
              x => unboxFullOrFail(x, Some(cc), ConsumerNotFoundById)
            }
            role <- Future { tryo(valueOf(body.role_name)) } map {
              x => unboxFullOrFail(x, Some(cc), IncorrectRoleName + body.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", "))
            }
            _ <- code.util.Helper.booleanToFuture(
              if (ApiRole.valueOf(body.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole,
              cc = Some(cc)) { ApiRole.valueOf(body.role_name).requiresBankId == body.bank_id.nonEmpty }
            allowedEntitlements    = canCreateScopeAtOneBank :: canCreateScopeAtAnyBank :: Nil
            allowedEntitlementsTxt = s"$UserHasMissingRoles ${allowedEntitlements.mkString(", ")}!"
            _ <- NewStyle.function.hasAtLeastOneEntitlement(allowedEntitlementsTxt)(body.bank_id, user.userId, allowedEntitlements, Some(cc))
            _ <- code.util.Helper.booleanToFuture(BankNotFound, cc = Some(cc)) {
              body.bank_id.nonEmpty == false || BankX(BankId(body.bank_id), Some(cc)).map(_._1).isDefined
            }
            _ <- code.util.Helper.booleanToFuture(EntitlementAlreadyExists, cc = Some(cc)) {
              !hasScope(body.bank_id, consumerIdStr, role)
            }
            addedScope <- Future { Scope.scope.vend.addScope(body.bank_id, consumerIdStr, body.role_name) } map { unboxFull(_) }
          } yield JSONFactory300.createScopeJson(addedScope)
        }
    }

    resourceDocs += ResourceDoc(
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
      List(AuthenticatedUserIsRequired, ConsumerNotFoundById, InvalidJsonFormat, IncorrectRoleName,
      EntitlementIsBankRole, EntitlementIsSystemRole, EntitlementAlreadyExists, UnknownError),
      List(apiTagScope, apiTagConsumer),
      Some(List(canCreateScopeAtOneBank, canCreateScopeAtAnyBank)),
      http4sPartialFunction = Some(addScope)
    )

    // ─── deleteScope ──────────────────────────────────────────────────────────

    val deleteScope: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "consumers" / consumerIdStr / "scope" / scopeIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            consumer <- Future { cc.consumer } map {
              x => unboxFullOrFail(x, Some(cc), InvalidConsumerCredentials)
            }
            scope <- Future { Scope.scope.vend.getScopeById(scopeIdStr) ?~! ScopeNotFound } map {
              x => unboxFullOrFail(x, Some(cc), s"$ScopeNotFound Current Value is $scopeIdStr")
            }
            _ <- Future {
              NewStyle.function.hasEntitlementAndScope(scope.bankId, user.userId, consumer.id.get.toString, canDeleteScopeAtOneBank, Some(cc))
            } map (fullBoxOrException(_)) recoverWith {
              case _ => Future {
                NewStyle.function.hasEntitlementAndScope("", user.userId, consumer.id.get.toString, canDeleteScopeAtAnyBank, Some(cc))
              } map (fullBoxOrException(_))
            }
            _ <- code.util.Helper.booleanToFuture(ConsumerDoesNotHaveScope, cc = Some(cc)) { scope.scopeId == scopeIdStr }
            _ <- Future { Scope.scope.vend.deleteScope(Full(scope)) }
          } yield org.json4s.JObject(Nil)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteScope),
      "DELETE",
      "/consumers/CONSUMER_ID/scope/SCOPE_ID",
      "Delete Consumer Scope",
      """Delete Consumer Scope specified by SCOPE_ID for an consumer specified by CONSUMER_ID
        |
        |Authentication is required and the user needs to be a Super Admin.
        |Super Admins are listed in the Props file.
        |
        |
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, EntitlementNotFound, UnknownError),
      List(apiTagScope, apiTagConsumer),
      Some(List(canDeleteScopeAtOneBank, canDeleteScopeAtAnyBank)),
      http4sPartialFunction = Some(deleteScope)
    )

    // ─── getScopes ────────────────────────────────────────────────────────────

    val getScopes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consumers" / consumerIdStr / "scopes" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            consumer <- Future { cc.consumer } map {
              x => unboxFullOrFail(x, Some(cc), InvalidConsumerCredentials)
            }
            _ <- Future {
              NewStyle.function.hasEntitlementAndScope("", user.userId, consumer.id.get.toString, canGetEntitlementsForAnyUserAtAnyBank, Some(cc))
            } flatMap { unboxFullAndWrapIntoFuture(_) }
            scopes <- Future { Scope.scope.vend.getScopesByConsumerId(consumerIdStr) } map { unboxFull(_) }
          } yield JSONFactory300.createScopeJSONs(scopes)
        }
    }

    resourceDocs += ResourceDoc(
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
      List(AuthenticatedUserIsRequired, EntitlementNotFound, UnknownError),
      List(apiTagScope, apiTagConsumer),
      None,
      http4sPartialFunction = Some(getScopes)
    )

    // ─── getBanks ─────────────────────────────────────────────────────────────

    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            _ <- code.util.Helper.booleanToFuture(ServiceIsTooBusy + "Current Service(NewStyle.function.getBanks)", 503, cc = Some(cc)) {
              canOpenFuture("NewStyle.function.getBanks")
            }
            (banks, _) <- FutureUtil.futureWithLimits(NewStyle.function.getBanks(Some(cc)), "NewStyle.function.getBanks")
          } yield JSONFactory300.createBanksJson(banks)
        }
    }

    resourceDocs += ResourceDoc(
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
      banksJSON,
      List(UnknownError),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getBanks)
    )

    // ─── bankById ─────────────────────────────────────────────────────────────

    val bankById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          Future.successful(code.api.v4_0_0.JSONFactory400.createBankJSON400(bank))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(bankById),
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
      List(AuthenticatedUserIsRequired, UnknownError, BankNotFound),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(bankById)
    )

    // ─── helpers ──────────────────────────────────────────────────────────────

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

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getViewsForBankAccount.run(req))
        .orElse(createViewForBankAccount.run(req))
        .orElse(updateViewForBankAccount.run(req))
        .orElse(getPermissionForUserForBankAccount.run(req))
        .orElse(getPrivateAccountById.run(req))
        .orElse(getPublicAccountById.run(req))
        .orElse(getCoreAccountById.run(req))
        .orElse(corePrivateAccountsAllBanks.run(req))
        .orElse(getFirehoseAccountsAtOneBank.run(req))
        .orElse(getFirehoseTransactionsForBankAccount.run(req))
        .orElse(getCoreTransactionsForBankAccount.run(req))
        .orElse(getTransactionsForBankAccount.run(req))
        .orElse(dataWarehouseSearch.run(req))
        .orElse(dataWarehouseStatistics.run(req))
        .orElse(getUser.run(req))
        .orElse(getUserByUserId.run(req))
        .orElse(getUserByUsername.run(req))
        .orElse(getAdapterInfoForBank.run(req))
        .orElse(createBranch.run(req))
        .orElse(updateBranch.run(req))
        .orElse(createAtm.run(req))
        .orElse(getBranch.run(req))
        .orElse(getBranches.run(req))
        .orElse(getAtm.run(req))
        .orElse(getAtms.run(req))
        .orElse(getUsers.run(req))
        .orElse(getCustomersForUser.run(req))
        .orElse(getCurrentUser.run(req))
        .orElse(privateAccountsAtOneBank.run(req))
        .orElse(getPrivateAccountIdsbyBankId.run(req))
        .orElse(getOtherAccountsForBankAccount.run(req))
        .orElse(getOtherAccountByIdForBankAccount.run(req))
        .orElse(addEntitlementRequest.run(req))
        .orElse(getAllEntitlementRequests.run(req))
        .orElse(getEntitlementRequests.run(req))
        .orElse(getEntitlementRequestsForCurrentUser.run(req))
        .orElse(deleteEntitlementRequest.run(req))
        .orElse(getEntitlementsForCurrentUser.run(req))
        .orElse(getApiGlossary.run(req))
        .orElse(getAccountsHeld.run(req))
        .orElse(getAggregateMetrics.run(req))
        .orElse(addScope.run(req))
        .orElse(deleteScope.run(req))
        .orElse(getScopes.run(req))
        .orElse(getBanks.run(req))
        .orElse(bankById.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allOwnRoutes))

    // ─── path-rewriting bridge: /obp/v3.0.0/… → /obp/v2.2.0/… ──────────────

    val v300ToV220Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v3.0.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v3\\.0\\.0/", "/obp/v2.2.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v2_2_0.Http4s220.wrappedRoutesV220Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV300Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations3_0_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations3_0_0.v300ToV220Bridge.run(req))
    }
}
