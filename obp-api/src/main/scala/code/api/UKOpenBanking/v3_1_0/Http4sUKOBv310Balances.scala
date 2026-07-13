package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.Constant
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, passesPsd2Aisp}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.NewStyle
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.newstyle.ViewNewStyle
import code.util.Helper.MdcLoggable
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BankIdAccountId, ViewId}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.Full
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer

/**
 * UK Open Banking v3.1 — BalancesApi, migrated from Lift to http4s.
 * getAccountsAccountIdBalances: real business logic with UK consent check.
 * getBalances: real business logic, no consent check (mirrors Lift).
 */
object Http4sUKOBv310Balances extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): code.api.berlin.group.v1_3.JvalueCaseClass = code.api.berlin.group.v1_3.JvalueCaseClass(com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject])
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Balances") :: Nil

  private val balancesSuccessBody = parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "CreditDebitIndicator" : "Credit",
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "CreditDebitIndicator" : "Credit",
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}""")

  lazy val getAccountsAccountIdBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / accountIdStr / "balances" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val accountId = AccountId(accountIdStr)
        val viewId = ViewId(Constant.SYSTEM_READ_BALANCES_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          (account, _) <- NewStyle.function.getBankAccountByAccountId(accountId, Some(cc))
          view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, accountId), Full(u), Some(cc))
          moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(u), Some(cc))
        } yield JSONFactory_UKOpenBanking_310.createAccountBalanceJSON(moderatedAccount)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdBalances),
    "GET",
    "/accounts/ACCOUNT_ID/balances",
    "Get Balances",
    s"""""",
    EmptyBody,
    balancesSuccessBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdBalances)
  )

  lazy val getBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "balances" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        for {
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
        } yield JSONFactory_UKOpenBanking_310.createBalancesJSON(accounts)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getBalances),
    "GET",
    "/balances",
    "Get Balances",
    s"""""",
    EmptyBody,
    balancesSuccessBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getBalances)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdBalances(req)
      .orElse(getBalances(req))
  }
}
