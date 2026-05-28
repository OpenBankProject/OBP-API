package code.api.UKOpenBanking.v2_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.NewStyle
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.json.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v2.0 — account-information endpoints migrated from Lift to http4s.
 *
 * Faithful migration of the three list/aggregate endpoints (getAccountList,
 * getAccount, getBalances) which only need getBankAccounts + a JSON factory.
 * The two account-scoped endpoints that rely on Lift Box helpers
 * (getAccountBalances at /accounts/ID/balances, getAccountTransactions at
 * /accounts/ID/transactions) are intentionally left on the Lift bridge: their
 * 3-segment paths don't match the patterns here, so they fall through unchanged.
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
    null,
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
        } yield JSONFactory_UKOpenBanking_200.createAccountJSON(accounts)
      }
  }
  resourceDocs += ResourceDoc(
    null,
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
    null,
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

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountList(req)
      .orElse(getAccount(req))
      .orElse(getBalances(req))
  }
}
