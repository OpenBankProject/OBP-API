package code.api.v1_2_1

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps, callContextKey}
import code.api.util.http4s.Http4sCallContextBuilder
import code.api.util.http4s.IdempotencyMiddleware
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{CallContext, CustomJsonFormats, NewStyle}
import code.bankconnectors.Connector
import code.metadata.counterparties.Counterparties
import code.model.{BankAccountX, BankX, ModeratedTransactionMetadata, UserX, toBankAccountExtended, toBankExtended}
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Box, Full}
import org.json4s.{Extraction, Formats}
import net.liftweb.util.Helpers._
import org.http4s._
import org.http4s.dsl.io._

import java.net.URL
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

object Http4s121 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v1_2_1
  val versionStatus: String = ApiVersionStatus.DEPRECATED.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  // Carried over verbatim from APIMethods121.scala:906 — referenced inside
  // restored `s"""..."""` doc interpolations for revoke-access endpoints.
  private val generalRevokeAccessToViewText: String =
    """
      |The User is identified by PROVIDER_ID at their PROVIDER.
      |
      |The Account is specified by BANK_ID and ACCOUNT_ID.
      |
      |The View is specified by VIEW_ID.
      |
      |
      |PROVIDER (may be a URL so) must be URL Encoded.
      |
      |PROVIDER_ID is normally equivalent to USERNAME. However, see Get User by ID or GET Current User for Provider information.
      |
      |Attempting to revoke access to a public view will return an error message.
      |
      |An Account Owner cannot revoke access to an Owner View unless at least one other User has Owner View access.
      |
    """.stripMargin

  object Implementations1_2_1 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    private def fail400(msg: String): Future[Nothing] = {
      val json = s"""{"failCode":400,"failMsg":"${msg.replace("\"", "\\\"")}"}"""
      Future.failed(new Exception(json))
    }

    private def privateBankAccountsListToJson(bankAccounts: List[BankAccount], privateViewsUserCanAccess: List[View]) = {
      val accJson = bankAccounts.map { account =>
        val viewsAvailable =
          (privateViewsUserCanAccess
            .filter(v => v.bankId == account.bankId && v.accountId == account.accountId && v.isPrivate)
            .map(JSONFactory.createViewJSON(_))
            .distinct) ++
            (privateViewsUserCanAccess
              .filter(v => v.isSystem && v.isPrivate)
              .map(JSONFactory.createViewJSON(_))
              .distinct)
        JSONFactory.createAccountJSON(account, viewsAvailable)
      }
      new AccountsJSON(accJson)
    }

    private def publicBankAccountsListToJson(bankAccounts: List[BankAccount], publicViews: List[View]) = {
      val accJson = bankAccounts.map { account =>
        val viewsAvailable =
          publicViews
            .filter(v => v.bankId == account.bankId && v.accountId == account.accountId && v.isPublic)
            .map(v => JSONFactory.createViewJSON(v))
            .distinct
        JSONFactory.createAccountJSON(account, viewsAvailable)
      }
      new AccountsJSON(accJson)
    }

    private def checkIfLocationPossible(lat: Double, lon: Double): Boolean =
      scala.math.abs(lat) <= 90 && scala.math.abs(lon) <= 180

    private def moderatedTransactionMetadataFuture(
      bankId: BankId, accountId: AccountId, viewId: ViewId,
      transactionID: TransactionId, user: Box[User], callContext: Option[CallContext]
    ): Future[ModeratedTransactionMetadata] =
      for {
        (account, cc2) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
        view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), user, cc2)
        (moderatedTransaction, cc3) <- account.moderatedTransactionFuture(transactionID, view, user, cc2) map {
          unboxFullOrFail(_, cc2, GetTransactionsException)
        }
        metadata <- Future(moderatedTransaction.metadata) map {
          unboxFullOrFail(_, cc3, s"$NoViewPermission can_see_transaction_metadata. Current ViewId($viewId)")
        }
      } yield metadata

    // ─── root ───────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v1_2_1, "STABLE"))
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
      http4sPartialFunction = Some(root)
    )

    // ─── getBanks ────────────────────────────────────────────────────────────

    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (banks, _) <- NewStyle.function.getBanks(Some(cc))
          } yield {
            val banksJSON = banks.map(b => JSONFactory.createBankJSON(b))
            new BanksJSON(banksJSON)
          }
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
      apiTagBank :: apiTagPsd2 :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(getBanks)
    )

    // ─── bankById ────────────────────────────────────────────────────────────

    // bankById runs outside ResourceDocMiddleware so it can return 400 (not 464) for unknown bank,
    // preserving the v1.2.1 Lift behavior.  Builds its own CallContext via Http4sCallContextBuilder.
    val bankById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId =>
        Http4sCallContextBuilder.fromRequest(req, implementedInApiVersion.apiShortVersion).flatMap { cc =>
          val reqWithCc = req.withAttribute(callContextKey, cc)
          EndpointHelpers.executeAndRespond(reqWithCc) { _ =>
            Future {
              unboxFullOrFail(BankX(BankId(bankId), Some(cc)), Some(cc), BankNotFound, 400)
            }.map { case (bank, _) => JSONFactory.createBankJSON(bank) }
          }
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
      bankJSON,
      List(AuthenticatedUserIsRequired, UnknownError, BankNotFound),
      apiTagBank :: apiTagPsd2 :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(bankById)
    )

    // ─── getPrivateAccountsAllBanks ──────────────────────────────────────────

    val getPrivateAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "accounts" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future {
            val (privateViewsUserCanAccess, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccess(user)
            val availablePrivateAccounts = BankAccountX.privateAccounts(privateAccountAccess)
            privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccess)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPrivateAccountsAllBanks),
      "GET",
      "/accounts",
      "Get accounts at all banks (Private, inc views)",
      s"""Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      apiTagAccount :: apiTagPsd2 :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(getPrivateAccountsAllBanks)
    )

    // ─── privateAccountsAllBanks ─────────────────────────────────────────────

    val privateAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "accounts" / "private" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future {
            val (privateViewsUserCanAccess, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccess(user)
            val privateAccounts = BankAccountX.privateAccounts(privateAccountAccess)
            privateBankAccountsListToJson(privateAccounts, privateViewsUserCanAccess)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(privateAccountsAllBanks),
      "GET",
      "/accounts/private",
      "Get private accounts at all banks (Authenticated access)",
      """Returns the list of private accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""".stripMargin,
      EmptyBody,
      accountJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      apiTagAccount :: apiTagPsd2 :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(privateAccountsAllBanks)
    )

    // ─── publicAccountsAllBanks ──────────────────────────────────────────────

    val publicAccountsAllBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "accounts" / "public" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          Future {
            val (publicViews, publicAccountAccess) = Views.views.vend.publicViews
            val publicAccounts = BankAccountX.publicAccounts(publicAccountAccess)
            publicBankAccountsListToJson(publicAccounts, publicViews)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(publicAccountsAllBanks),
      "GET",
      "/accounts/public",
      "Get public accounts at all banks (Anonymous access)",
      """
      |Returns the list of private accounts the user has access to at all banks.
      |For each account the API returns the ID and the available views. 
      |Authentication via OAuth is required.
      |
      |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UnknownError),
      apiTagAccount :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(publicAccountsAllBanks)
    )

    // ─── getPrivateAccountsAtOneBank ─────────────────────────────────────────

    val getPrivateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          Future {
            val (privateViewsUserCanAccessAtOneBank, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            val availablePrivateAccounts = toBankExtended(bank).privateAccounts(privateAccountAccess)
            privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccessAtOneBank)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPrivateAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/accounts",
      "Get accounts at bank (Private)",
      s"""Returns the list of accounts at BANK_ID that the user has access to.
         |For each account the API returns the account ID and the available views.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(AuthenticatedUserIsRequired, UnknownError, BankNotFound),
      apiTagAccount :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(getPrivateAccountsAtOneBank)
    )

    // ─── privateAccountsAtOneBank ────────────────────────────────────────────

    val privateAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / "private" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          Future {
            val (privateViewsUserCanAccessAtOneBank, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccessAtBank(user, bank.bankId)
            val availablePrivateAccounts = toBankExtended(bank).privateAccounts(privateAccountAccess)
            privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccessAtOneBank)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(privateAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get private accounts at one bank",
      s"""Returns the list of private accounts at BANK_ID that the user has access to.
         |For each account the API returns the ID and the available views.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(AuthenticatedUserIsRequired, UnknownError, BankNotFound),
      List(apiTagAccount, apiTagPsd2, apiTagOldStyle),
      http4sPartialFunction = Some(privateAccountsAtOneBank)
    )

    // ─── publicAccountsAtOneBank ─────────────────────────────────────────────

    val publicAccountsAtOneBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / "public" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          Future {
            val (publicViewsForBank, publicAccountAccess) = Views.views.vend.publicViewsForBank(bank.bankId)
            val publicAccounts = toBankExtended(bank).publicAccounts(publicAccountAccess)
            publicBankAccountsListToJson(publicAccounts, publicViewsForBank)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(publicAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/accounts/public",
      "Get public accounts at one bank (Anonymous access)",
      """Returns a list of the public accounts at BANK_ID. For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is not required.""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UnknownError, BankNotFound),
      apiTagAccountPublic :: apiTagAccount :: apiTagPublicData :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(publicAccountsAtOneBank)
    )

    // ─── accountById ─────────────────────────────────────────────────────────

    val accountById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            availableViews <- Future(Views.views.vend.privateViewsUserCanAccessForAccount(user, BankIdAccountId(account.bankId, account.accountId)))
            moderatedAccount <- Future(account.moderatedBankAccount(view, BankIdAccountId(account.bankId, account.accountId), Full(user), Some(cc))) map {
              unboxFullOrFail(_, Some(cc), BankAccountNotFound)
            }
          } yield {
            val viewsAvailable = availableViews.map(JSONFactory.createViewJSON)
            JSONFactory.createBankAccountJSON(moderatedAccount, viewsAvailable)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(accountById),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get account by id",
      s"""Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
      |
      |* Number
      |* Owners
      |* Type
      |* Balance
      |* IBAN
      |* Available views
      |
      |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
      |
      |${userAuthenticationMessage(false)}
      |
      |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
      |
      |""".stripMargin,
      EmptyBody,
      moderatedAccountJSON,
      List(AuthenticatedUserIsRequired, UnknownError, BankAccountNotFound),
      apiTagAccount :: apiTagOldStyle :: Nil,
      http4sPartialFunction = Some(accountById)
    )

    // ─── updateAccountLabel ──────────────────────────────────────────────────

    val updateAccountLabel: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId =>
        EndpointHelpers.executeFuture(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[UpdateAccountJSON]
            }
            (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(bankId), AccountId(accountId), Some(cc))
            permission <- NewStyle.function.permission(account.bankId, account.accountId, user, callContext)
            anyViewContainsPermission = permission.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_BANK_ACCOUNT_LABEL)).find(_ == true).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_UPDATE_BANK_ACCOUNT_LABEL}` permission on any your views",
              cc = callContext
            )(anyViewContainsPermission)
            _ <- Connector.connector.vend.updateAccountLabel(BankId(bankId), AccountId(accountId), json.label, callContext) map { i =>
              unboxFullOrFail(i._1, i._2, s"$UpdateBankAccountLabelError Current BankId is $bankId and Current AccountId is $accountId", 404)
            }
          } yield successMessage
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAccountLabel),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Update Account Label",
      s"""Update the label for the account. The label is how the account is known to the account owner e.g. 'My savings account'
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      updateAccountJSON,
      successMessage,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, UnknownError, BankAccountNotFound, "user does not have access to owner view on account"),
      List(apiTagAccount),
      http4sPartialFunction = Some(updateAccountLabel)
    )

    // ─── getViewsForBankAccount ───────────────────────────────────────────────

    val getViewsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "views" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            permission <- Future(Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), user)) map {
              unboxFullOrFail(_, Some(cc), BankAccountNotFound)
            }
            anyViewContainsPermission = permission.views.map(_.allowed_actions.exists(_ == CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT)).find(_ == true).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT}` permission on any your views",
              cc = Some(cc)
            )(anyViewContainsPermission)
            views <- Future(Views.views.vend.availableViewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
          } yield JSONFactory.createViewsJSON(views)
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
      viewsJSONV121,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, "user does not have owner access"),
      List(apiTagView, apiTagAccount, apiTagOldStyle),
      http4sPartialFunction = Some(getViewsForBankAccount)
    )

    // ─── createViewForBankAccount ─────────────────────────────────────────────

    val createViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            (rawAccountBox, _) <- Connector.connector.vend.checkBankAccountExists(BankId(bankId), AccountId(accountId), Some(cc))
            account <- Future { unboxFullOrFail(rawAccountBox, Some(cc), s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId") }
            createViewJsonV121 <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[CreateViewJsonV121]
            }
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current view_name (${createViewJsonV121.name})", cc = Some(cc)) {
              isValidCustomViewName(createViewJsonV121.name)
            }
            createViewJson = CreateViewJson(
              createViewJsonV121.name, createViewJsonV121.description,
              metadata_view = "",
              createViewJsonV121.is_public, createViewJsonV121.which_alias_to_use,
              createViewJsonV121.hide_metadata_if_alias_used, createViewJsonV121.allowed_actions
            )
            anyViewContainsPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), user)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_CREATE_CUSTOM_VIEW))).getOrElse(Nil).find(_ == true).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${CreateCustomViewError} You need the `${CAN_CREATE_CUSTOM_VIEW}` permission on any your views",
              cc = Some(cc)
            )(anyViewContainsPermission)
            view <- Future(Views.views.vend.createCustomView(BankIdAccountId(account.bankId, account.accountId), createViewJson)) map {
              unboxFullOrFail(_, Some(cc), CreateCustomViewError)
            }
          } yield JSONFactory.createViewJSON(view)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createViewForBankAccount),
      "POST",
      "/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/views",
      "Create View",
      s"""#Create a view on bank account
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
      | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.""",
      createViewJsonV121,
      viewJSONV121,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankAccountNotFound, UnknownError, "user does not have owner access"),
      List(apiTagAccount, apiTagView, apiTagOldStyle),
      http4sPartialFunction = Some(createViewForBankAccount)
    )

    // ─── updateViewForBankAccount ─────────────────────────────────────────────

    val updateViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId =>
        EndpointHelpers.executeFutureWithBody[UpdateViewJsonV121, ViewJSONV121](req) { (updateJsonV121, cc) =>
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            (rawAccountBox, _) <- Connector.connector.vend.checkBankAccountExists(BankId(bankId), AccountId(accountId), Some(cc))
            account <- Future { unboxFullOrFail(rawAccountBox, Some(cc), s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId") }
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current view_id ($viewId)", cc = Some(cc)) {
              viewId.startsWith("_")
            }
            view <- Future(Views.views.vend.customView(ViewId(viewId), BankIdAccountId(account.bankId, account.accountId))) map {
              unboxFullOrFail(_, Some(cc), ViewNotFound)
            }
            _ <- Helper.booleanToFuture(SystemViewsCanNotBeModified, cc = Some(cc))(!view.isSystem)
            updateViewJson = UpdateViewJSON(
              description = updateJsonV121.description,
              metadata_view = view.metadataView,
              is_public = updateJsonV121.is_public,
              which_alias_to_use = updateJsonV121.which_alias_to_use,
              hide_metadata_if_alias_used = updateJsonV121.hide_metadata_if_alias_used,
              allowed_actions = updateJsonV121.allowed_actions
            )
            anyViewContainsPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), user)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_CUSTOM_VIEW))).getOrElse(Nil).find(_ == true).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${CreateCustomViewError} You need the `${CAN_UPDATE_CUSTOM_VIEW}` permission on any your views",
              cc = Some(cc)
            )(anyViewContainsPermission)
            updatedView <- Future(Views.views.vend.updateCustomView(BankIdAccountId(account.bankId, account.accountId), ViewId(viewId), updateViewJson)) map {
              unboxFullOrFail(_, Some(cc), CreateCustomViewError)
            }
          } yield JSONFactory.createViewJSON(updatedView)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateViewForBankAccount),
      "PUT",
      "/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/views/CUSTOM_VIEW_ID",
      "Update View",
      s"""Update an existing view on a bank account
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      |
      |The json sent is the same as during view creation (above), with one difference: the 'name' field
      |of a view is not editable (it is only set when a view is created)""",
      updateViewJsonV121,
      viewJSONV121,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound, UnknownError, "user does not have owner access"),
      List(apiTagAccount, apiTagView, apiTagOldStyle),
      http4sPartialFunction = Some(updateViewForBankAccount)
    )

    // ─── deleteViewForBankAccount ─────────────────────────────────────────────

    val deleteViewForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "views" / viewId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Some(cc))
            (account, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current view_name ($viewId)", cc = callContext)(viewId.startsWith("_"))
            _ <- ViewNewStyle.customView(ViewId(viewId), BankIdAccountId(BankId(bankId), AccountId(accountId)), callContext)
            anyViewContainsPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), user)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_DELETE_CUSTOM_VIEW))).getOrElse(Nil).find(_ == true).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_DELETE_CUSTOM_VIEW}` permission on any your views",
              cc = callContext
            )(anyViewContainsPermission)
            _ <- ViewNewStyle.removeCustomView(ViewId(viewId), BankIdAccountId(BankId(bankId), AccountId(accountId)), callContext)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteViewForBankAccount),
      "DELETE",
      "/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/views/CUSTOM_VIEW_ID",
      "Delete Custom View",
      "Deletes the custom view specified by VIEW_ID on the bank account specified by ACCOUNT_ID at bank BANK_ID",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, "user does not have owner access"),
      List(apiTagView, apiTagAccount),
      http4sPartialFunction = Some(deleteViewForBankAccount)
    )

    // ─── getPermissionsForBankAccount ─────────────────────────────────────────

    val getPermissionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "permissions" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          val permissionBox = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), user)
          val anyViewContainsPermission = permissionBox.map(_.views.map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS))).getOrElse(Nil).find(_ == true).getOrElse(false)
          for {
            _ <- Helper.booleanToFuture(
              s"${CreateCustomViewError} You need the `${CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS}` permission on any your views",
              cc = Some(cc)
            )(anyViewContainsPermission)
            permissions = Views.views.vend.permissions(BankIdAccountId(account.bankId, account.accountId))
          } yield JSONFactory.createPermissionsJSON(permissions)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPermissionsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions",
      "Get access",
      s"""Returns the list of the permissions at BANK_ID for account ACCOUNT_ID, with each time a pair composed of the user and the views that he has access to.
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      permissionsJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagView, apiTagAccount, apiTagEntitlement, apiTagOldStyle),
      http4sPartialFunction = Some(getPermissionsForBankAccount)
    )

    // ─── getPermissionForUserForBankAccount ───────────────────────────────────

    val getPermissionForUserForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "permissions" / provider / providerId =>
        EndpointHelpers.withBankAccount(req) { (loggedInUser, account, cc) =>
          val loggedInUserPermissionBox = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), loggedInUser)
          val anyViewContainsPermission = loggedInUserPermissionBox.map(_.views.map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER)))
            .getOrElse(Nil).find(_ == true).getOrElse(false)
          for {
            _ <- Helper.booleanToFuture(
              s"${CreateCustomViewError} You need the `${CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER}` permission on any your views",
              cc = Some(cc)
            )(anyViewContainsPermission)
            userFromURL <- Future(UserX.findByProviderId(provider, providerId)) map {
              unboxFullOrFail(_, Some(cc), UserNotFoundByProviderAndProvideId)
            }
            permission <- Future(Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), userFromURL)) map {
              unboxFullOrFail(_, Some(cc), UnknownError)
            }
          } yield JSONFactory.createViewsJSON(permission.views)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPermissionForUserForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID",
      "Get access for specific user",
      s"""Returns the list of the views at BANK_ID for account ACCOUNT_ID that a USER_ID at their provider PROVIDER_ID has access to.
      |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      viewsJSONV121,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, "user does not have access to owner view on account"),
      List(apiTagAccount, apiTagView, apiTagEntitlement, apiTagOldStyle),
      http4sPartialFunction = Some(getPermissionForUserForBankAccount)
    )

    // ─── addPermissionForUserForBankAccountForMultipleViews ───────────────────

    val addPermissionForUserForBankAccountForMultipleViews: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "permissions" / provider / providerId / "views" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Some(cc))
            (account, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            viewIds <- NewStyle.function.tryons("wrong format JSON", 400, callContext) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[ViewIdsJson]
            }
            (addedViews, callContext) <- ViewNewStyle.grantAccessToMultipleViews(
              account, user,
              viewIds.views.map(viewIdString => BankIdAccountIdViewId(BankId(bankId), AccountId(accountId), ViewId(viewIdString))),
              provider, providerId, callContext
            )
          } yield JSONFactory.createViewsJSON(addedViews)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(addPermissionForUserForBankAccountForMultipleViews),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views",
      "Grant User access to a list of views",
      s"""Grants the user identified by PROVIDER_ID at their provider PROVIDER access to a list of views at BANK_ID for account ACCOUNT_ID.
      |
      |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for PROVIDER_ID and PROVIDER.
      |
      |${userAuthenticationMessage(true)}
      |
      |The User needs to have access to the owner view.""",
      viewIdsJson,
      viewsJSONV121,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, "wrong format JSON", "could not save the privilege", "user does not have access to owner view on account"),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired),
      http4sPartialFunction = Some(addPermissionForUserForBankAccountForMultipleViews)
    )

    // ─── addPermissionForUserForBankAccountForOneView ─────────────────────────

    val addPermissionForUserForBankAccountForOneView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "permissions" / provider / providerId / "views" / viewId =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Some(cc))
            (account, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            (addedView, callContext) <- ViewNewStyle.grantAccessToView(account, user, BankIdAccountIdViewId(BankId(bankId), AccountId(accountId), ViewId(viewId)), provider, providerId, callContext)
          } yield JSONFactory.createViewJSON(addedView)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(addPermissionForUserForBankAccountForOneView),
      "POST",
      "/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views/GRANT_VIEW_ID",
      "Grant User access to View",
      s"""Grants the User identified by PROVIDER_ID at PROVIDER access to the view VIEW_ID at BANK_ID for account ACCOUNT_ID.
      |
      |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for PROVIDER and PROVIDER_ID.
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      |
      |Granting access to a public view will return an error message, as the user already has access.""",
      EmptyBody,
      // No Json body required
      viewJSONV121,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, UserLacksPermissionCanGrantAccessToViewForTargetAccount, "could not save the privilege", "user does not have access to owner view on account"),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired),
      http4sPartialFunction = Some(addPermissionForUserForBankAccountForOneView)
    )

    // ─── removePermissionForUserForBankAccountForOneView ──────────────────────

    val removePermissionForUserForBankAccountForOneView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "permissions" / provider / providerId / "views" / viewId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Some(cc))
            (account, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            _ <- ViewNewStyle.revokeAccessToView(account, user, BankIdAccountIdViewId(BankId(bankId), AccountId(accountId), ViewId(viewId)), provider, providerId, callContext)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(removePermissionForUserForBankAccountForOneView),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views/VIEW_ID",
      "Revoke access to one View",
      s"""Revokes access to a View on an Account for a certain User.
       |
       |$generalRevokeAccessToViewText
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "could not save the privilege", "user does not have access to owner view on account", UnknownError),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagEntitlement, apiTagOwnerRequired),
      http4sPartialFunction = Some(removePermissionForUserForBankAccountForOneView)
    )

    // ─── removePermissionForUserForBankAccountForAllViews ────────────────────

    val removePermissionForUserForBankAccountForAllViews: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankId / "accounts" / accountId / "permissions" / provider / providerId / "views" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Some(cc))
            (account, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            _ <- NewStyle.function.revokeAllAccountAccess(account, user, provider, providerId, callContext)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(removePermissionForUserForBankAccountForAllViews),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views",
      "Revoke access to all Views on Account",
      s""""Revokes access to all Views on an Account for a certain User.
       |
       |$generalRevokeAccessToViewText
       |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, "user does not have access to owner view on account"),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired),
      http4sPartialFunction = Some(removePermissionForUserForBankAccountForAllViews)
    )

    // ─── getOtherAccountsForBankAccount ──────────────────────────────────────

    val getOtherAccountsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccounts, _) <- NewStyle.function.moderatedOtherBankAccounts(account, view, Full(user), Some(cc))
          } yield JSONFactory.createOtherBankAccountsJSON(otherBankAccounts)
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
         |Authentication is required if the view VIEW_ID is not public.""",
      EmptyBody,
      otherAccountsJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagCounterparty, apiTagAccount, apiTagPsd2, apiTagOldStyle),
      http4sPartialFunction = Some(getOtherAccountsForBankAccount)
    )

    // ─── getOtherAccountByIdForBankAccount ───────────────────────────────────

    val getOtherAccountByIdForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
          } yield JSONFactory.createOtherBankAccount(otherBankAccount)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getOtherAccountByIdForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
      "Get Other Account by Id",
      s"""Returns data about the Other Account that has shared at least one transaction with ACCOUNT_ID at BANK_ID.
      |${userAuthenticationMessage(true)}
      |Authentication is required if the view is not public.""",
      EmptyBody,
      otherAccountJSON,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound, UnknownError),
      List(apiTagCounterparty, apiTagAccount),
      http4sPartialFunction = Some(getOtherAccountByIdForBankAccount)
    )

    // ─── getOtherAccountMetadata ──────────────────────────────────────────────

    val getOtherAccountMetadata: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc)) {
              otherBankAccount.metadata.isDefined
            }
          } yield JSONFactory.createOtherAccountMetaDataJSON(otherBankAccount.metadata.get)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getOtherAccountMetadata),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata",
      "Get Other Account Metadata",
      """Get metadata of one other account.
      |Returns only the metadata about one other bank account (OTHER_ACCOUNT_ID) that had shared at least one transaction with ACCOUNT_ID at BANK_ID.
      |
      |Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      otherAccountMetadataJSON,
      List(AuthenticatedUserIsRequired, UnknownError, "the view does not allow metadata access"),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(getOtherAccountMetadata)
    )

    // ─── getCounterpartyPublicAlias ───────────────────────────────────────────

    val getCounterpartyPublicAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "public_alias" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a public alias", cc = Some(cc))(otherBankAccount.metadata.get.publicAlias.isDefined)
          } yield JSONFactory.createAliasJSON(otherBankAccount.metadata.get.publicAlias.get)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCounterpartyPublicAlias), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Get public alias of other bank account",
      s"""Returns the public alias of the other account OTHER_ACCOUNT_ID.
      |${userAuthenticationMessage(false)}
      |${userAuthenticationMessage(true)} if the view is not public.""",
      EmptyBody, aliasJSON,
      List(
        BankAccountNotFound,
        UnknownError,
        "the view does not allow metadata access",
        "the view does not allow public alias access"
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(getCounterpartyPublicAlias)
    )

    // ─── addCounterpartyPublicAlias ───────────────────────────────────────────

    val addCounterpartyPublicAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "public_alias" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a public alias", cc = Some(cc))(otherBankAccount.metadata.get.addPublicAlias.isDefined)
            aliasJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[AliasJSON] }
            added <- Future(Counterparties.counterparties.vend.addPublicAlias(otherAccountId, aliasJson.alias)) map { unboxFullOrFail(_, Some(cc), "Alias cannot be added", 400) }
            _ <- Helper.booleanToFuture("Alias cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("public alias added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyPublicAlias), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Add public alias to other bank account", s"""Creates the public alias for the other account OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.
        |
        |Note: Public aliases are automatically generated for new 'other accounts / counterparties', so this call should only be used if
        |the public alias was deleted.
        |
        |The VIEW_ID parameter should be a view the caller is permitted to access to and that has permission to create public aliases.""",
      aliasJSON, successMessage,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError,
        "the view does not allow metadata access",
        "the view does not allow adding a public alias",
        "Alias cannot be added",
        "public alias added"
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyPublicAlias)
    )

    // ─── updateCounterpartyPublicAlias ────────────────────────────────────────

    val updateCounterpartyPublicAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "public_alias" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating a public alias", cc = Some(cc))(otherBankAccount.metadata.get.addPublicAlias.isDefined)
            aliasJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[AliasJSON] }
            updated <- Future(Counterparties.counterparties.vend.addPublicAlias(otherAccountId, aliasJson.alias)) map { unboxFullOrFail(_, Some(cc), "Alias cannot be updated", 400) }
            _ <- Helper.booleanToFuture("Alias cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("public alias updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyPublicAlias), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Update public alias of other bank account", s"""Updates the public alias of the other account / counterparty OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.""",
      aliasJSON, successMessage,
      List(BankAccountNotFound, InvalidJsonFormat, AuthenticatedUserIsRequired, "the view does not allow metadata access", "the view does not allow updating the public alias", "Alias cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyPublicAlias)
    )

    // ─── deleteCounterpartyPublicAlias ────────────────────────────────────────

    val deleteCounterpartyPublicAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "public_alias" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting a public alias", cc = callContext)(otherBankAccount.metadata.get.addPublicAlias.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.addPublicAlias(otherAccountId, "")) map { unboxFullOrFail(_, callContext, "Alias cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("Alias cannot be deleted", 400, callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyPublicAlias), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Delete Counterparty Public Alias", s"""Deletes the public alias of the other account OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.""",
      EmptyBody, EmptyBody,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting the public alias",
        "Alias cannot be deleted",
        UnknownError
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyPublicAlias)
    )

    // ─── getOtherAccountPrivateAlias ──────────────────────────────────────────

    val getOtherAccountPrivateAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "private_alias" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a private alias", cc = Some(cc))(otherBankAccount.metadata.get.privateAlias.isDefined)
          } yield JSONFactory.createAliasJSON(otherBankAccount.metadata.get.privateAlias.get)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getOtherAccountPrivateAlias), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Get Other Account Private Alias", s"""Returns the private alias of the other account OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.""",
      EmptyBody, aliasJSON,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "the view does not allow private alias access", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(getOtherAccountPrivateAlias)
    )

    // ─── addOtherAccountPrivateAlias ──────────────────────────────────────────

    val addOtherAccountPrivateAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "private_alias" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a private alias", cc = Some(cc))(otherBankAccount.metadata.get.addPrivateAlias.isDefined)
            aliasJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[AliasJSON] }
            added <- Future(Counterparties.counterparties.vend.addPrivateAlias(otherAccountId, aliasJson.alias)) map { unboxFullOrFail(_, Some(cc), "Alias cannot be added", 400) }
            _ <- Helper.booleanToFuture("Alias cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("private alias added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addOtherAccountPrivateAlias), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Create Other Account Private Alias", s"""Creates a private alias for the other account OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.""",
      aliasJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow adding a private alias", "Alias cannot be added", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addOtherAccountPrivateAlias)
    )

    // ─── updateCounterpartyPrivateAlias ───────────────────────────────────────

    val updateCounterpartyPrivateAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "private_alias" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating a private alias", cc = Some(cc))(otherBankAccount.metadata.get.addPrivateAlias.isDefined)
            aliasJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[AliasJSON] }
            updated <- Future(Counterparties.counterparties.vend.addPrivateAlias(otherAccountId, aliasJson.alias)) map { unboxFullOrFail(_, Some(cc), "Alias cannot be updated", 400) }
            _ <- Helper.booleanToFuture("Alias cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("private alias updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyPrivateAlias), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Update Counterparty Private Alias", s"""Updates the private alias of the counterparty (AKA other account) OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.""",
      aliasJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow updating the private alias", "Alias cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyPrivateAlias)
    )

    // ─── deleteCounterpartyPrivateAlias ───────────────────────────────────────

    val deleteCounterpartyPrivateAlias: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "private_alias" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting a private alias", cc = callContext)(otherBankAccount.metadata.get.addPrivateAlias.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.addPrivateAlias(otherAccountId, "")) map { unboxFullOrFail(_, callContext, "Alias cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("Alias cannot be deleted", 400, callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyPrivateAlias), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Delete Counterparty Private Alias", s"""Deletes the private alias of the other account OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(true)}
        |Authentication is required if the view is not public.""",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "the view does not allow deleting the private alias", "Alias cannot be deleted", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyPrivateAlias)
    )

    // ─── addCounterpartyMoreInfo ──────────────────────────────────────────────

    val addCounterpartyMoreInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "more_info" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding more info", cc = Some(cc))(otherBankAccount.metadata.get.addMoreInfo.isDefined)
            moreInfoJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[MoreInfoJSON] }
            added <- Future(Counterparties.counterparties.vend.addMoreInfo(otherAccountId, moreInfoJson.more_info)) map { unboxFullOrFail(_, Some(cc), "More Info cannot be added", 400) }
            _ <- Helper.booleanToFuture("More Info cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("more info added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyMoreInfo), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Add Counterparty More Info",
      // Intentional drift from Lift's APIMethods121.scala source-of-truth:
      // typo fixes "counter party" → "counterparty" and "perpestive" → "perspective".
      "Add a description of the counterparty from the perspective of the account e.g. My dentist",
      moreInfoJSON, successMessage,
      List(
        AuthenticatedUserIsRequired,
        BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        "the view " + viewIdSwagger + "does not allow adding more info",
        "More Info cannot be added",
        UnknownError
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyMoreInfo)
    )

    // ─── updateCounterpartyMoreInfo ───────────────────────────────────────────

    val updateCounterpartyMoreInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "more_info" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating more info", cc = Some(cc))(otherBankAccount.metadata.get.addMoreInfo.isDefined)
            moreInfoJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[MoreInfoJSON] }
            updated <- Future(Counterparties.counterparties.vend.addMoreInfo(otherAccountId, moreInfoJson.more_info)) map { unboxFullOrFail(_, Some(cc), "More Info cannot be updated", 400) }
            _ <- Helper.booleanToFuture("More Info cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("more info updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyMoreInfo), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Update Counterparty More Info",
      // Intentional drift from Lift's APIMethods121.scala source-of-truth:
      // typo fixes "counter party" → "counterparty" and "perpestive" → "perspective".
      "Update the more info description of the counterparty from the perspective of the account e.g. My dentist",
      moreInfoJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow updating more info", "More Info cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyMoreInfo)
    )

    // ─── deleteCounterpartyMoreInfo ───────────────────────────────────────────

    val deleteCounterpartyMoreInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "more_info" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting more info", cc = callContext)(otherBankAccount.metadata.get.addMoreInfo.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.addMoreInfo(otherAccountId, "")) map { unboxFullOrFail(_, callContext, "More Info cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("More Info cannot be deleted", 400, callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyMoreInfo), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Delete more info of other bank account", "",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "the view does not allow deleting more info", "More Info cannot be deleted", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyMoreInfo)
    )

    // ─── addCounterpartyUrl ───────────────────────────────────────────────────

    val addCounterpartyUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "url" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a url", cc = Some(cc))(otherBankAccount.metadata.get.addURL.isDefined)
            urlJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[UrlJSON] }
            added <- Future(Counterparties.counterparties.vend.addURL(otherAccountId, urlJson.URL)) map { unboxFullOrFail(_, Some(cc), "URL cannot be added", 400) }
            _ <- Helper.booleanToFuture("URL cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("url added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyUrl), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Add url to other bank account", "A url which represents the counterparty (home page url etc.)",
      urlJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow adding a url", "URL cannot be added", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyUrl)
    )

    // ─── updateCounterpartyUrl ────────────────────────────────────────────────

    val updateCounterpartyUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "url" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating a url", cc = Some(cc))(otherBankAccount.metadata.get.addURL.isDefined)
            urlJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[UrlJSON] }
            updated <- Future(Counterparties.counterparties.vend.addURL(otherAccountId, urlJson.URL)) map { unboxFullOrFail(_, Some(cc), "URL cannot be updated", 400) }
            _ <- Helper.booleanToFuture("URL cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("url updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyUrl), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Update url of other bank account", "A url which represents the counterparty (home page url etc.)",
      urlJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, NoViewPermission, ViewNotFound, "URL cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyUrl)
    )

    // ─── deleteCounterpartyUrl ────────────────────────────────────────────────

    val deleteCounterpartyUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "url" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting a url", cc = callContext)(otherBankAccount.metadata.get.addURL.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.addURL(otherAccountId, "")) map { unboxFullOrFail(_, callContext, "URL cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("URL cannot be deleted", 400, callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyUrl), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Delete url of other bank account", "",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "the view does not allow deleting a url", "URL cannot be deleted", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyUrl)
    )

    // ─── addCounterpartyImageUrl ──────────────────────────────────────────────

    val addCounterpartyImageUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "image_url" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding an image url", cc = Some(cc))(otherBankAccount.metadata.get.addImageURL.isDefined)
            imageUrlJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[ImageUrlJSON] }
            added <- Future(Counterparties.counterparties.vend.addImageURL(otherAccountId, imageUrlJson.image_URL)) map { unboxFullOrFail(_, Some(cc), "URL cannot be added", 400) }
            _ <- Helper.booleanToFuture("URL cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("image url added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyImageUrl), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Add image url to other bank account", "Add a url that points to the logo of the counterparty",
      imageUrlJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow adding an image url", "URL cannot be added", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyImageUrl)
    )

    // ─── updateCounterpartyImageUrl ───────────────────────────────────────────

    val updateCounterpartyImageUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "image_url" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating an image url", cc = Some(cc))(otherBankAccount.metadata.get.addImageURL.isDefined)
            imageUrlJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[ImageUrlJSON] }
            updated <- Future(Counterparties.counterparties.vend.addImageURL(otherAccountId, imageUrlJson.image_URL)) map { unboxFullOrFail(_, Some(cc), "URL cannot be updated", 400) }
            _ <- Helper.booleanToFuture("URL cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("image url updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyImageUrl), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Update Counterparty Image Url", "Update the url that points to the logo of the counterparty",
      imageUrlJSON, successMessage,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
      InvalidJsonFormat,
      "the view does not allow metadata access",
      "the view does not allow updating an image url",
      "URL cannot be updated",
      UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyImageUrl)
    )

    // ─── deleteCounterpartyImageUrl ───────────────────────────────────────────

    val deleteCounterpartyImageUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "image_url" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting an image url", cc = callContext)(otherBankAccount.metadata.get.addImageURL.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.addImageURL(otherAccountId, "")) map { unboxFullOrFail(_, callContext, "URL cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("URL cannot be deleted", 400, callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyImageUrl), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Delete Counterparty Image URL", "Delete image url of other bank account",
      EmptyBody, EmptyBody,
      List(
        $AuthenticatedUserIsRequired,
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyImageUrl)
    )

    // ─── addCounterpartyOpenCorporatesUrl ─────────────────────────────────────

    val addCounterpartyOpenCorporatesUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "open_corporates_url" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding an open corporate url", cc = Some(cc))(otherBankAccount.metadata.get.addOpenCorporatesURL.isDefined)
            openCorpUrl <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[OpenCorporateUrlJSON] }
            added <- Future(Counterparties.counterparties.vend.addOpenCorporatesURL(otherAccountId, openCorpUrl.open_corporates_URL)) map { unboxFullOrFail(_, Some(cc), "URL cannot be added", 400) }
            _ <- Helper.booleanToFuture("URL cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("open corporate url added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyOpenCorporatesUrl), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/open_corporates_url",
      "Add Open Corporates URL to Counterparty", "Add open corporates url to other bank account",
      openCorporateUrlJSON, successMessage,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
      InvalidJsonFormat,
      "the view does not allow metadata access",
      "the view does not allow adding an open corporate url",
      "URL cannot be added",
      UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyOpenCorporatesUrl)
    )

    // ─── updateCounterpartyOpenCorporatesUrl ──────────────────────────────────

    val updateCounterpartyOpenCorporatesUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "open_corporates_url" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating an open corporate url", cc = Some(cc))(otherBankAccount.metadata.get.addOpenCorporatesURL.isDefined)
            openCorpUrl <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[OpenCorporateUrlJSON] }
            updated <- Future(Counterparties.counterparties.vend.addOpenCorporatesURL(otherAccountId, openCorpUrl.open_corporates_URL)) map { unboxFullOrFail(_, Some(cc), "URL cannot be updated", 400) }
            _ <- Helper.booleanToFuture("URL cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("open corporate url updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyOpenCorporatesUrl), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/open_corporates_url",
      "Update Open Corporates Url of Counterparty", "Update open corporate url of other bank account",
      openCorporateUrlJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow updating an open corporate url", "URL cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyOpenCorporatesUrl)
    )

    // ─── deleteCounterpartyOpenCorporatesUrl ──────────────────────────────────

    val deleteCounterpartyOpenCorporatesUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "open_corporates_url" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting an open corporate url", cc = callContext)(otherBankAccount.metadata.get.addOpenCorporatesURL.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.addOpenCorporatesURL(otherAccountId, "")) map { unboxFullOrFail(_, callContext, "URL cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("URL cannot be deleted", 400, callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyOpenCorporatesUrl), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/open_corporates_url",
      "Delete Counterparty Open Corporates URL", "Delete open corporate url of other bank account",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "the view does not allow deleting an open corporate url", "URL cannot be deleted", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyOpenCorporatesUrl)
    )

    // ─── addCounterpartyCorporateLocation ────────────────────────────────────

    val addCounterpartyCorporateLocation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "corporate_location" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a corporate location", cc = Some(cc))(otherBankAccount.metadata.get.addCorporateLocation.isDefined)
            corpLocationJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[CorporateLocationJSON] }
            _ <- Helper.booleanToFuture("Coordinates not possible", 400, Some(cc)) { checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude) }
            added <- Future(Counterparties.counterparties.vend.addCorporateLocation(otherAccountId, user.userPrimaryKey, (now: TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude)) map { unboxFullOrFail(_, Some(cc), "Corporate Location cannot be added", 400) }
            _ <- Helper.booleanToFuture("Corporate Location cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("corporate location added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyCorporateLocation), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Add Corporate Location to Counterparty", "Add the geolocation of the counterparty's registered address",
      corporateLocationJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "the view does not allow adding a corporate location", "Coordinates not possible", "Corporate Location cannot be deleted", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyCorporateLocation)
    )

    // ─── updateCounterpartyCorporateLocation ──────────────────────────────────

    val updateCounterpartyCorporateLocation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "corporate_location" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating a corporate location", cc = Some(cc))(otherBankAccount.metadata.get.addCorporateLocation.isDefined)
            corpLocationJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[CorporateLocationJSON] }
            _ <- Helper.booleanToFuture("Coordinates not possible", 400, Some(cc)) { checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude) }
            updated <- Future(Counterparties.counterparties.vend.addCorporateLocation(otherAccountId, user.userPrimaryKey, (now: TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude)) map { unboxFullOrFail(_, Some(cc), "Corporate Location cannot be updated", 400) }
            _ <- Helper.booleanToFuture("Corporate Location cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("corporate location updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyCorporateLocation), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Update Counterparty Corporate Location", "Update the geolocation of the counterparty's registered address",
      corporateLocationJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow updating a corporate location", "Coordinates not possible", "Corporate Location cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyCorporateLocation)
    )

    // ─── deleteCounterpartyCorporateLocation ──────────────────────────────────

    val deleteCounterpartyCorporateLocation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "corporate_location" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting a Corporate Location", cc = callContext)(otherBankAccount.metadata.get.deleteCorporateLocation.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.deleteCorporateLocation(otherAccountId)) map { unboxFullOrFail(_, callContext, "Corporate Location cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("Delete not completed", cc = callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyCorporateLocation), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Delete Counterparty Corporate Location", "Delete corporate location of other bank account. Delete the geolocation of the counterparty's registered address",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, "the view does not allow metadata access", "Corporate Location cannot be deleted", "Delete not completed", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyCorporateLocation)
    )

    // ─── addCounterpartyPhysicalLocation ──────────────────────────────────────

    val addCounterpartyPhysicalLocation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "physical_location" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow adding a physical location", cc = Some(cc))(otherBankAccount.metadata.get.addPhysicalLocation.isDefined)
            physicalLocationJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PhysicalLocationJSON] }
            _ <- Helper.booleanToFuture("Coordinates not possible", 400, Some(cc)) { checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude) }
            added <- Future(Counterparties.counterparties.vend.addPhysicalLocation(otherAccountId, user.userPrimaryKey, (now: TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude)) map { unboxFullOrFail(_, Some(cc), "Physical Location cannot be added", 400) }
            _ <- Helper.booleanToFuture("Physical Location cannot be added", 400, Some(cc))(added)
          } yield SuccessMessage("physical location added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCounterpartyPhysicalLocation), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Add physical location to other bank account", "Add geocoordinates of the counterparty's main location",
      physicalLocationJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow adding a physical location", "Coordinates not possible", "Physical Location cannot be added", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(addCounterpartyPhysicalLocation)
    )

    // ─── updateCounterpartyPhysicalLocation ───────────────────────────────────

    val updateCounterpartyPhysicalLocation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "physical_location" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = Some(cc))(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow updating a physical location", cc = Some(cc))(otherBankAccount.metadata.get.addPhysicalLocation.isDefined)
            physicalLocationJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PhysicalLocationJSON] }
            _ <- Helper.booleanToFuture("Coordinates not possible", 400, Some(cc)) { checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude) }
            updated <- Future(Counterparties.counterparties.vend.addPhysicalLocation(otherAccountId, user.userPrimaryKey, (now: TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude)) map { unboxFullOrFail(_, Some(cc), "Physical Location cannot be updated", 400) }
            _ <- Helper.booleanToFuture("Physical Location cannot be updated", 400, Some(cc))(updated)
          } yield SuccessMessage("physical location updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCounterpartyPhysicalLocation), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Update Counterparty Physical Location", "Update geocoordinates of the counterparty's main location",
      physicalLocationJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, "the view does not allow metadata access", "the view does not allow updating a physical location", "Coordinates not possible", "Physical Location cannot be updated", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(updateCounterpartyPhysicalLocation)
    )

    // ─── deleteCounterpartyPhysicalLocation ───────────────────────────────────

    val deleteCounterpartyPhysicalLocation: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "other_accounts" / otherAccountId / "metadata" / "physical_location" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(cc.bankAccount.get.bankId, cc.bankAccount.get.accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(cc.view.get.viewId, BankIdAccountId(account.bankId, account.accountId), Full(user), callContext)
            (otherBankAccount, _) <- NewStyle.function.moderatedOtherBankAccount(account, otherAccountId, view, Full(user), callContext)
            _ <- Helper.booleanToFuture(s"$NoViewPermission can_see_other_account_metadata. Current ViewId(${view.viewId})", cc = callContext)(otherBankAccount.metadata.isDefined)
            _ <- Helper.booleanToFuture(s"the view ${view.viewId} does not allow deleting a Physical Location", cc = callContext)(otherBankAccount.metadata.get.deletePhysicalLocation.isDefined)
            deleted <- Future(Counterparties.counterparties.vend.deletePhysicalLocation(otherAccountId)) map { unboxFullOrFail(_, callContext, "Physical Location cannot be deleted", 400) }
            _ <- Helper.booleanToFuture("Delete not completed", cc = callContext)(deleted)
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCounterpartyPhysicalLocation), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Delete Counterparty Physical Location", "Delete physical location of other bank account",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, NoViewPermission, "Physical Location cannot be deleted", "Delete not completed", UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty),
      http4sPartialFunction = Some(deleteCounterpartyPhysicalLocation)
    )

    // ─── getTransactionsForBankAccount ───────────────────────────────────────

    val getTransactionsForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / viewId / "transactions" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          val httpParams: List[HTTPParam] = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value))
          for {
            (rawAccountBox, callContext) <- Connector.connector.vend.checkBankAccountExists(BankId(bankId), AccountId(accountId), Some(cc))
            account <- Future { unboxFullOrFail(rawAccountBox, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId") }
            view <- ViewNewStyle.checkViewAccessAndReturnView(ViewId(viewId), BankIdAccountId(BankId(bankId), AccountId(accountId)), cc.user.toOption, callContext)
            (bank, callContext2) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (params, callContext3) <- createQueriesByHttpParamsFuture(httpParams, callContext2)
            (transactions, _) <- account.getModeratedTransactionsFuture(bank, cc.user, view, callContext3, params) map {
              unboxFullOrFail(_, callContext3, GetTransactionsException)
            }
          } yield JSONFactory.createTransactionsJSON(transactions)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionsForBankAccount), "GET",
      "/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/TRANSACTIONS_VIEW_ID/transactions",
      "Get Transactions for Account (Full)",
      s"""Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
      |
      |Authentication via OAuth is required if the view is not public.
      |
      |${urlParametersDocument(true, true)}
      |
      |""",
      EmptyBody, transactionsJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagAccount, apiTagPsd2, apiTagOldStyle),
      http4sPartialFunction = Some(getTransactionsForBankAccount)
    )

    // ─── getTransactionByIdForBankAccount ─────────────────────────────────────

    val getTransactionByIdForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "transaction" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (rawAccountBox, callContext) <- Connector.connector.vend.checkBankAccountExists(BankId(bankId), AccountId(accountId), Some(cc))
            account <- Future { unboxFullOrFail(rawAccountBox, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId") }
            view <- ViewNewStyle.checkViewAccessAndReturnView(ViewId(viewId), BankIdAccountId(BankId(bankId), AccountId(accountId)), cc.user.toOption, callContext)
            (moderatedTransaction, _) <- account.moderatedTransactionFuture(TransactionId(transactionId), view, cc.user, callContext) map {
              unboxFullOrFail(_, callContext, GetTransactionsException)
            }
          } yield JSONFactory.createTransactionJSON(moderatedTransaction)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionByIdForBankAccount), "GET",
      "/banks/BANK_ID/accounts/BANK_ACCOUNT_ID/TRANSACTIONS_VIEW_ID/transactions/TRANSACTION_ID/transaction",
      "Get Transaction by Id",
      s"""Returns one transaction specified by TRANSACTION_ID of the account ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
      |
      |${userAuthenticationMessage(false)}
      |Authentication is required if the view is not public.
      |
      |
      |""",
      EmptyBody, transactionJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagPsd2, apiTagOldStyle),
      http4sPartialFunction = Some(getTransactionByIdForBankAccount)
    )

    // ─── getTransactionNarrative ──────────────────────────────────────────────

    val getTransactionNarrative: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "narrative" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            narrative <- Future(metadata.ownerComment) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield JSONFactory.createTransactionNarrativeJSON(narrative)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionNarrative), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Get a Transaction Narrative",
      """Returns the account owner description of the transaction [moderated](#1_2_1-getViewsForBankAccount) by the view.
      |
      |Authentication via OAuth is required if the view is not public.""",
      EmptyBody, transactionNarrativeJSON,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
      NoViewPermission,
      ViewNotFound,
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(getTransactionNarrative)
    )

    // ─── addTransactionNarrative ──────────────────────────────────────────────

    val addTransactionNarrative: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "narrative" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            narrativeJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[TransactionNarrativeJSON]
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addNarrative <- Future(metadata.addOwnerComment) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_add_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield {
            addNarrative(narrativeJson.narrative)
            SuccessMessage("narrative added")
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addTransactionNarrative), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Add a Transaction Narrative",
      // Intentional drift from Lift's APIMethods121.scala source-of-truth.
      // Lift's description had userAuthenticationMessage(false), but the handler
      // is authenticated. The ResourceDoc constructor strips $AuthenticatedUserIsRequired
      // from errorResponseBodies when description carries `authenticationIsOptional`,
      // making the middleware skip the 401 — view-permission check then returned
      // 403 for unauthenticated requests. Flip the marker to (true).
      s"""Creates a description of the transaction TRANSACTION_ID.
      |
      |Note: Unlike other items of metadata, there is only one "narrative" per transaction accross all views.
      |If you set narrative via a view e.g. view-x it will be seen via view-y (as long as view-y has permission to see the narrative).
      |
      |${userAuthenticationMessage(true)}
      |""",
      transactionNarrativeJSON, successMessage,
      List(
        $AuthenticatedUserIsRequired,
        InvalidJsonFormat,
      BankAccountNotFound,
      NoViewPermission,
      ViewNotFound,
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(addTransactionNarrative)
    )

    // ─── updateTransactionNarrative ───────────────────────────────────────────

    val updateTransactionNarrative: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "narrative" =>
        EndpointHelpers.executeFuture(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            narrativeJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[TransactionNarrativeJSON]
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addNarrative <- Future(metadata.addOwnerComment) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_add_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield {
            addNarrative(narrativeJson.narrative)
            SuccessMessage("narrative updated")
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateTransactionNarrative), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Update a Transaction Narrative",
      """Updates the description of the transaction TRANSACTION_ID.
        |
        |Authentication via OAuth is required if the view is not public.""",
      transactionNarrativeJSON, successMessage,
      List(
        $AuthenticatedUserIsRequired,
        InvalidJsonFormat,
      BankAccountNotFound,
      NoViewPermission,
      ViewNotFound,
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(updateTransactionNarrative)
    )

    // ─── deleteTransactionNarrative ───────────────────────────────────────────

    val deleteTransactionNarrative: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "narrative" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          val bankAccount = cc.bankAccount.get
          val view = cc.view.get
          for {
            metadata <- moderatedTransactionMetadataFuture(bankAccount.bankId, bankAccount.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addNarrative <- Future(metadata.addOwnerComment) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield addNarrative("")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteTransactionNarrative), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Delete a Transaction Narrative",
      """Deletes the description of the transaction TRANSACTION_ID.
        |
        |Authentication via OAuth is required if the view is not public.""",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, NoViewPermission, UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(deleteTransactionNarrative)
    )

    // ─── getCommentsForViewOnTransaction ─────────────────────────────────────

    val getCommentsForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "comments" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            comments <- Future(metadata.comments) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield JSONFactory.createTransactionCommentsJSON(comments)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCommentsForViewOnTransaction), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments",
      "Get Transaction Comments",
      """Returns the transaction TRANSACTION_ID comments made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
      |
      |Authentication via OAuth is required if the view is not public.""",
      EmptyBody, transactionCommentsJSON,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, NoViewPermission, ViewNotFound, UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(getCommentsForViewOnTransaction)
    )

    // ─── addCommentForViewOnTransaction ───────────────────────────────────────

    val addCommentForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "comments" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            commentJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PostTransactionCommentJSON]
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addCommentFunc <- Future(metadata.addComment) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
            postedComment <- Future(addCommentFunc(user.userPrimaryKey, view.viewId, commentJson.value, now)) map {
              unboxFullOrFail(_, Some(cc), s"Cannot add the comment ${commentJson.value}")
            }
          } yield JSONFactory.createTransactionCommentJSON(postedComment)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCommentForViewOnTransaction), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments",
      "Add a Transaction Comment",
      """Posts a comment about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
      |
      |${authenticationRequiredMessage(false)}
      |
      |Authentication is required since the comment is linked with the user.""",
      postTransactionCommentJSON, transactionCommentJSON,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, BankAccountNotFound, NoViewPermission, ViewNotFound, UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(addCommentForViewOnTransaction)
    )

    // ─── deleteCommentForViewOnTransaction ────────────────────────────────────

    val deleteCommentForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "comments" / commentId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          val bankAccount = cc.bankAccount.get
          val view = cc.view.get
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankAccount.bankId, bankAccount.accountId, Some(cc))
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), callContext)
            _ <- Future(metadata.deleteComment(commentId, Some(user), account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "Comment could not be deleted")
            }
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteCommentForViewOnTransaction), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments/COMMENT_ID",
      "Delete a Transaction Comment",
      """Delete the comment COMMENT_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
      |
      |Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the comment.""",
      EmptyBody, EmptyBody,
      List(BankAccountNotFound, NoViewPermission, ViewNotFound, AuthenticatedUserIsRequired, UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(deleteCommentForViewOnTransaction)
    )

    // ─── getTagsForViewOnTransaction ─────────────────────────────────────────

    val getTagsForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "tags" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            tags <- Future(metadata.tags) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield JSONFactory.createTransactionTagsJSON(tags)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTagsForViewOnTransaction), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags",
      "Get Transaction Tags",
      """Returns the transaction TRANSACTION_ID tags made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
      Authentication via OAuth is required if the view is not public.""",
      EmptyBody, transactionTagJSON,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError
      ),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(getTagsForViewOnTransaction)
    )

    // ─── addTagForViewOnTransaction ───────────────────────────────────────────

    val addTagForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "tags" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            tagJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PostTransactionTagJSON]
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addTagFunc <- Future(metadata.addTag) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
            postedTag <- Future(addTagFunc(user.userPrimaryKey, view.viewId, tagJson.value, now)) map {
              unboxFullOrFail(_, Some(cc), s"Cannot add the tag ${tagJson.value}")
            }
          } yield JSONFactory.createTransactionTagJSON(postedTag)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addTagForViewOnTransaction), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags",
      "Add a Transaction Tag",
      s"""Posts a tag about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
      |
      |${userAuthenticationMessage(true)}
      |
      |Authentication is required as the tag is linked with the user.""",
      postTransactionTagJSON, transactionTagJSON,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, NoViewPermission, ViewNotFound, UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(addTagForViewOnTransaction)
    )

    // ─── deleteTagForViewOnTransaction ────────────────────────────────────────

    val deleteTagForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "tags" / tagId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          val bankAccount = cc.bankAccount.get
          val view = cc.view.get
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankAccount.bankId, bankAccount.accountId, Some(cc))
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), callContext)
            _ <- Future(metadata.deleteTag(tagId, Some(user), account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "Tag could not be deleted")
            }
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteTagForViewOnTransaction), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags/TAG_ID",
      "Delete a Transaction Tag",
      """Deletes the tag TAG_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
      |Authentication via OAuth is required. The user must either have owner privileges for this account, 
      |or must be the user that posted the tag.
      |""".stripMargin,
      EmptyBody, EmptyBody,
      List(
        $AuthenticatedUserIsRequired,
        NoViewPermission,
      ViewNotFound,
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(deleteTagForViewOnTransaction)
    )

    // ─── getImagesForViewOnTransaction ────────────────────────────────────────

    val getImagesForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "images" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            images <- Future(metadata.images) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield JSONFactory.createTransactionImagesJSON(images)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getImagesForViewOnTransaction), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images",
      "Get Transaction Images",
      """Returns the transaction TRANSACTION_ID images made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
      Authentication via OAuth is required if the view is not public.""",
      EmptyBody, transactionImagesJSON,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, NoViewPermission, ViewNotFound, UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(getImagesForViewOnTransaction)
    )

    // ─── addImageForViewOnTransaction ─────────────────────────────────────────

    val addImageForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "images" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            imageJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PostTransactionImageJSON]
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addImageFunc <- Future(metadata.addImage) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
            url <- NewStyle.function.tryons(s"$InvalidUrl Could not parse url string as a valid URL", 400, Some(cc)) {
              new URL(imageJson.URL)
            }
            postedImage <- Future(addImageFunc(user.userPrimaryKey, view.viewId, imageJson.label, now, url.toString)) map {
              unboxFullOrFail(_, Some(cc), s"Cannot add the image ${imageJson.label}")
            }
          } yield JSONFactory.createTransactionImageJSON(postedImage)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addImageForViewOnTransaction), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images",
      "Add a Transaction Image",
      s"""Posts an image about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
      |
      |${userAuthenticationMessage(true) }
      |
      |The image is linked with the user.""",
      postTransactionImageJSON, transactionImageJSON,
      List(
      InvalidJsonFormat,
      BankAccountNotFound,
      NoViewPermission,
      ViewNotFound,
      InvalidUrl,
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(addImageForViewOnTransaction)
    )

    // ─── deleteImageForViewOnTransaction ─────────────────────────────────────

    val deleteImageForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "images" / imageId =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          val bankAccount = cc.bankAccount.get
          val view = cc.view.get
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankAccount.bankId, bankAccount.accountId, Some(cc))
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), callContext)
            _ <- Future(metadata.deleteImage(imageId, Some(user), account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "Image could not be deleted")
            }
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteImageForViewOnTransaction), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images/IMAGE_ID",
      "Delete a Transaction Image",
      """Deletes the image IMAGE_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
      |
      |Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the image.""",
      EmptyBody, EmptyBody,
      List(
      BankAccountNotFound,
      NoViewPermission,
      AuthenticatedUserIsRequired,
      "You must be able to see images in order to delete them",
      "Image not found for this transaction",
      "Deleting images not permitted for this view",
      "Deleting images not permitted for the current user",
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(deleteImageForViewOnTransaction)
    )

    // ─── getWhereTagForViewOnTransaction ─────────────────────────────────────

    val getWhereTagForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "where" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            where <- Future(metadata.whereTag) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_see_owner_comment. Current ViewId(${view.viewId})")
            }
          } yield {
            val json = JSONFactory.createLocationJSON(where)
            TransactionWhereJSON(json)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getWhereTagForViewOnTransaction), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Get a Transaction where Tag",
      """Returns the "where" Geo tag added to the transaction TRANSACTION_ID made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
      |It represents the location where the transaction has been initiated.
      |
      |Authentication via OAuth is required if the view is not public.""",
      EmptyBody, transactionWhereJSON,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound,
      NoViewPermission,
      ViewNotFound,
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(getWhereTagForViewOnTransaction)
    )

    // ─── addWhereTagForViewOnTransaction ─────────────────────────────────────

    val addWhereTagForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "where" =>
        EndpointHelpers.executeFutureCreated(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addWhereTagFunc <- Future(metadata.addWhereTag) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_add_where_tag. Current ViewId(${view.viewId})")
            }
            whereJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PostTransactionWhereJSON]
            }
            _ <- Helper.booleanToFuture("Coordinates not possible", 400, Some(cc)) {
              checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
            }
            _ <- Helper.booleanToFuture("Where tag could not be saved", 400, Some(cc)) {
              addWhereTagFunc(user.userPrimaryKey, view.viewId, now, whereJson.where.longitude, whereJson.where.latitude)
            }
          } yield SuccessMessage("where tag added")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addWhereTagForViewOnTransaction), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Add a Transaction where Tag",
      s"""Creates a "where" Geo tag on a transaction TRANSACTION_ID in a [view](#1_2_1-getViewsForBankAccount).
      |
      |${userAuthenticationMessage(true)}
      |
      |The geo tag is linked with the user.""",
      postTransactionWhereJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, ViewNotFound, NoViewPermission, "Coordinates not possible", UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(addWhereTagForViewOnTransaction)
    )

    // ─── updateWhereTagForViewOnTransaction ───────────────────────────────────

    val updateWhereTagForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "where" =>
        EndpointHelpers.executeFuture(req) {
          val cc = req.callContext
          val bodyStr = cc.httpBody.getOrElse("")
          for {
            user <- cc.user match {
              case Full(u) => Future.successful(u)
              case _ => Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
            }
            account <- cc.bankAccount match {
              case Some(a) => Future.successful(a)
              case None => Future.failed(new RuntimeException(BankAccountNotFound))
            }
            view <- cc.view match {
              case Some(v) => Future.successful(v)
              case None => Future.failed(new RuntimeException(ViewNotFound))
            }
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), Some(cc))
            addWhereTagFunc <- Future(metadata.addWhereTag) map {
              unboxFullOrFail(_, Some(cc), s"$NoViewPermission can_add_where_tag. Current ViewId(${view.viewId})")
            }
            whereJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(bodyStr).extract[PostTransactionWhereJSON]
            }
            _ <- Helper.booleanToFuture("Coordinates not possible", 400, Some(cc)) {
              checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
            }
            _ <- Helper.booleanToFuture("Where tag could not be saved", 400, Some(cc)) {
              addWhereTagFunc(user.userPrimaryKey, view.viewId, now, whereJson.where.longitude, whereJson.where.latitude)
            }
          } yield SuccessMessage("where tag updated")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateWhereTagForViewOnTransaction), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Update a Transaction where Tag",
      s"""Updates the "where" Geo tag on a transaction TRANSACTION_ID in a [view](#1_2_1-getViewsForBankAccount).
      |
      |${userAuthenticationMessage(true)}
      |
      |The geo tag is linked with the user.""",
      postTransactionWhereJSON, successMessage,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, InvalidJsonFormat, ViewNotFound, NoViewPermission, "Coordinates not possible", UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(updateWhereTagForViewOnTransaction)
    )

    // ─── deleteWhereTagForViewOnTransaction ───────────────────────────────────

    val deleteWhereTagForViewOnTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "metadata" / "where" =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          val bankAccount = cc.bankAccount.get
          val view = cc.view.get
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankAccount.bankId, bankAccount.accountId, Some(cc))
            metadata <- moderatedTransactionMetadataFuture(account.bankId, account.accountId, view.viewId, TransactionId(transactionId), Full(user), callContext)
            _ <- Future(metadata.deleteWhereTag(view.viewId, Some(user), account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "Delete not completed")
            }
          } yield ()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteWhereTagForViewOnTransaction), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Delete a Transaction Tag",
      s"""Deletes the where tag of the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
       |
      |${userAuthenticationMessage(true)}
      |
      |The user must either have owner privileges for this account, or must be the user that posted the geo tag.""",
      EmptyBody, EmptyBody,
      List(
      AuthenticatedUserIsRequired,
      BankAccountNotFound,
      NoViewPermission,
      AuthenticatedUserIsRequired,
      ViewNotFound,
      "there is no tag to delete",
      "Delete not completed",
      UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction),
      http4sPartialFunction = Some(deleteWhereTagForViewOnTransaction)
    )

    // ─── getOtherAccountForTransaction ───────────────────────────────────────

    val getOtherAccountForTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionId / "other_account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            (moderatedTransaction, _) <- account.moderatedTransactionFuture(TransactionId(transactionId), view, Full(user), Some(cc)) map {
              unboxFullOrFail(_, Some(cc), GetTransactionsException)
            }
            _ <- Helper.booleanToFuture(GetTransactionsException, 400, Some(cc)) {
              moderatedTransaction.otherBankAccount.isDefined
            }
          } yield JSONFactory.createOtherBankAccount(moderatedTransaction.otherBankAccount.get)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getOtherAccountForTransaction), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/other_account",
      "Get Other Account of Transaction",
      """Get other account of a transaction.
      |Returns details of the other party involved in the transaction, moderated by the [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
       Authentication via OAuth is required if the view is not public.""",
      EmptyBody, otherAccountJSON,
      List(
        $AuthenticatedUserIsRequired,
        BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagCounterparty),
      http4sPartialFunction = Some(getOtherAccountForTransaction)
    )

    // ─── allRoutes ────────────────────────────────────────────────────────────

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
        root(req)
          .orElse(getBanks(req))
          // bankById is intentionally absent — it runs outside middleware (see allRoutesWithMiddleware)
          .orElse(getPrivateAccountsAllBanks(req))
          .orElse(privateAccountsAllBanks(req))
          .orElse(publicAccountsAllBanks(req))
          .orElse(getPrivateAccountsAtOneBank(req))
          .orElse(privateAccountsAtOneBank(req))
          .orElse(publicAccountsAtOneBank(req))
          .orElse(accountById(req))
          .orElse(updateAccountLabel(req))
          .orElse(getViewsForBankAccount(req))
          .orElse(createViewForBankAccount(req))
          .orElse(updateViewForBankAccount(req))
          .orElse(deleteViewForBankAccount(req))
          .orElse(getPermissionsForBankAccount(req))
          .orElse(getPermissionForUserForBankAccount(req))
          .orElse(addPermissionForUserForBankAccountForMultipleViews(req))
          .orElse(addPermissionForUserForBankAccountForOneView(req))
          .orElse(removePermissionForUserForBankAccountForOneView(req))
          .orElse(removePermissionForUserForBankAccountForAllViews(req))
          .orElse(getOtherAccountsForBankAccount(req))
          .orElse(getOtherAccountByIdForBankAccount(req))
          .orElse(getOtherAccountMetadata(req))
          .orElse(getCounterpartyPublicAlias(req))
          .orElse(addCounterpartyPublicAlias(req))
          .orElse(updateCounterpartyPublicAlias(req))
          .orElse(deleteCounterpartyPublicAlias(req))
          .orElse(getOtherAccountPrivateAlias(req))
          .orElse(addOtherAccountPrivateAlias(req))
          .orElse(updateCounterpartyPrivateAlias(req))
          .orElse(deleteCounterpartyPrivateAlias(req))
          .orElse(addCounterpartyMoreInfo(req))
          .orElse(updateCounterpartyMoreInfo(req))
          .orElse(deleteCounterpartyMoreInfo(req))
          .orElse(addCounterpartyUrl(req))
          .orElse(updateCounterpartyUrl(req))
          .orElse(deleteCounterpartyUrl(req))
          .orElse(addCounterpartyImageUrl(req))
          .orElse(updateCounterpartyImageUrl(req))
          .orElse(deleteCounterpartyImageUrl(req))
          .orElse(addCounterpartyOpenCorporatesUrl(req))
          .orElse(updateCounterpartyOpenCorporatesUrl(req))
          .orElse(deleteCounterpartyOpenCorporatesUrl(req))
          .orElse(addCounterpartyCorporateLocation(req))
          .orElse(updateCounterpartyCorporateLocation(req))
          .orElse(deleteCounterpartyCorporateLocation(req))
          .orElse(addCounterpartyPhysicalLocation(req))
          .orElse(updateCounterpartyPhysicalLocation(req))
          .orElse(deleteCounterpartyPhysicalLocation(req))
          .orElse(getTransactionsForBankAccount(req))
          .orElse(getTransactionByIdForBankAccount(req))
          .orElse(getTransactionNarrative(req))
          .orElse(addTransactionNarrative(req))
          .orElse(updateTransactionNarrative(req))
          .orElse(deleteTransactionNarrative(req))
          .orElse(getCommentsForViewOnTransaction(req))
          .orElse(addCommentForViewOnTransaction(req))
          .orElse(deleteCommentForViewOnTransaction(req))
          .orElse(getTagsForViewOnTransaction(req))
          .orElse(addTagForViewOnTransaction(req))
          .orElse(deleteTagForViewOnTransaction(req))
          .orElse(getImagesForViewOnTransaction(req))
          .orElse(addImageForViewOnTransaction(req))
          .orElse(deleteImageForViewOnTransaction(req))
          .orElse(getWhereTagForViewOnTransaction(req))
          .orElse(addWhereTagForViewOnTransaction(req))
          .orElse(updateWhereTagForViewOnTransaction(req))
          .orElse(deleteWhereTagForViewOnTransaction(req))
          .orElse(getOtherAccountForTransaction(req))
      }

    val allRoutesWithMiddleware: HttpRoutes[IO] = {
      val middlewareWrapped = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
      // bankById runs before middleware so it can return 400 (not 404) for unknown bank
      Kleisli[HttpF, Request[IO], Response[IO]] { req =>
        bankById.run(req).orElse(middlewareWrapped.run(req))
      }
    }
  }

  lazy val wrappedRoutesV121Services: HttpRoutes[IO] = Implementations1_2_1.allRoutesWithMiddleware
}
