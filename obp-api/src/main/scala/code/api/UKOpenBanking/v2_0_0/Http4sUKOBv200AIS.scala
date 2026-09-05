package code.api.UKOpenBanking.v2_0_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.{HTTPParam, EmptyBody, ResourceDoc, createQueriesByHttpParams, fullBoxOrException, unboxFull}
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankAccountNotFoundByAccountId, UnknownError}
import code.api.util.NewStyle
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.newstyle.ViewNewStyle
import code.model.BankAccountExtended
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BankIdAccountId}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.Full
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v2.0 — account-information endpoints migrated from Lift to http4s.
 *
 * Faithful migration of all 5 endpoints:
 *  - getAccountList (/accounts), getAccount (/accounts/ID), getBalances (/balances)
 *    — list/aggregate endpoints using getBankAccounts + a JSON factory.
 *  - getAccountBalances (/accounts/ID/balances), getAccountTransactions
 *    (/accounts/ID/transactions) — account-scoped endpoints; their 3-segment
 *    patterns are distinct from the 2-segment ones, so http4s route matching
 *    picks the correct handler.
 * No v2.0 endpoint is left on Lift.
 */
object Http4sUKOBv200AIS extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV20
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  val ukV20Prefix = Root / ApiVersion.ukOpenBankingV20.urlPrefix / ApiVersion.ukOpenBankingV20.apiShortVersion

  // GET /accounts — list all private accounts of the logged-in user
  lazy val getAccountList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV20Prefix` / "accounts" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val callContext = Some(cc)
        for {
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
        } yield JSONFactory_UKOpenBanking_200.createAccountsListJSON(accounts)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountList),
    "GET",
    "/accounts",
    "UK Open Banking: Get Account List",
    """Reads a list of bank accounts, with balances where required.""",
    EmptyBody,
    SwaggerDefinitionsJSON.accountsJsonUKOpenBanking_v200,
    List(AuthenticatedUserIsRequired, UnknownError),
    List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData),
    http4sPartialFunction = Some(getAccountList)
  )

  // GET /accounts/{accountId} — single account
  lazy val getAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV20Prefix` / "accounts" / accountId =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val callContext = Some(cc)
        for {
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u).map(_.filter(_.accountId.value == accountId))
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          // accountId names one of the caller's own private accounts, or none exist for it -- an
          // id belonging to somebody else's account looks identical here, and correctly resolves
          // to zero results (this never leaks another user's account). createAccountJSON's own
          // Links.Self does list.head.AccountId unconditionally, so without this check a
          // nonexistent (or not-mine) account id crashed with a raw NoSuchElementException (500)
          // instead of the 404 UK Open Banking's spec calls for.
          _ <- Helper.booleanToFuture(
            s"$BankAccountNotFoundByAccountId Current account_id is $accountId",
            failCode = 404, cc = Some(cc))(accounts.nonEmpty)
        } yield JSONFactory_UKOpenBanking_200.createAccountJSON(accounts)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccount),
    "GET",
    "/accounts/ACCOUNT_ID",
    "UK Open Banking: Get Account",
    """Reads a bank account, with balances where required.""",
    EmptyBody,
    SwaggerDefinitionsJSON.accountsJsonUKOpenBanking_v200,
    List(AuthenticatedUserIsRequired, UnknownError),
    List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData),
    http4sPartialFunction = Some(getAccount)
  )

  // GET /balances — bulk balances for all private accounts
  lazy val getBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV20Prefix` / "balances" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val callContext = Some(cc)
        for {
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
        } yield JSONFactory_UKOpenBanking_200.createBalancesJSON(accounts)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getBalances),
    "GET",
    "/balances",
    "UK Open Banking: Get Balances",
    """Bulk retrieval of balances for all authorised accounts.""",
    EmptyBody,
    SwaggerDefinitionsJSON.accountBalancesUKV200,
    List(AuthenticatedUserIsRequired, UnknownError),
    List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData),
    http4sPartialFunction = Some(getBalances)
  )

  // GET /accounts/{accountId}/balances — account-level balances
  lazy val getAccountBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV20Prefix` / "accounts" / accountIdStr / "balances" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        for {
          (account, _) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountIdStr), Some(cc))
          view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), Some(cc))
          moderatedAccount <- Future {
            BankAccountExtended(account).moderatedBankAccount(view, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc))
          } map { x => unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight)))) }
        } yield JSONFactory_UKOpenBanking_200.createAccountBalanceJSON(moderatedAccount)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountBalances),
    "GET",
    "/accounts/ACCOUNT_ID/balances",
    "UK Open Banking: Get Account Balances",
    """An AISP may retrieve the account balance information resource for a specific AccountId.""",
    EmptyBody,
    SwaggerDefinitionsJSON.accountBalancesUKV200,
    List(AuthenticatedUserIsRequired, UnknownError),
    List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData),
    http4sPartialFunction = Some(getAccountBalances)
  )

  // GET /accounts/{accountId}/transactions — account-level transactions
  lazy val getAccountTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV20Prefix` / "accounts" / accountIdStr / "transactions" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        for {
          (account, _) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountIdStr), Some(cc))
          (bank, _) <- NewStyle.function.getBank(account.bankId, Some(cc))
          view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), Some(cc))
          params <- Future {
            createQueriesByHttpParams(req.headers.headers.toList.map(h => HTTPParam(h.name.toString, List(h.value))))
          } map { x => unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight)))) }
          (transactions, _) <- Future {
            BankAccountExtended(account).getModeratedTransactions(bank, Full(u), view, BankIdAccountId(account.bankId, account.accountId), Some(cc), params)
          } map { x => unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight)))) }
        } yield JSONFactory_UKOpenBanking_200.createTransactionsJson(transactions, Nil)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountTransactions),
    "GET",
    "/accounts/ACCOUNT_ID/transactions",
    "UK Open Banking: Get Account Transactions",
    """Reads account data from a given account addressed by "account-id".""",
    EmptyBody,
    SwaggerDefinitionsJSON.transactionsJsonUKV200,
    List(AuthenticatedUserIsRequired, UnknownError),
    List(apiTagUKOpenBanking, apiTagTransaction, apiTagPrivateData),
    http4sPartialFunction = Some(getAccountTransactions)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountList(req)
      .orElse(getAccount(req))
      .orElse(getAccountBalances(req))
      .orElse(getAccountTransactions(req))
      .orElse(getBalances(req))
  }
}
