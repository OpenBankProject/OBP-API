package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.Constant
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, passesPsd2Aisp}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError, attemptedToOpenAnEmptyBox}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, NewStyle}
import code.util.Helper.MdcLoggable
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BankAccount, BankIdAccountId, View, ViewId}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.{Box, Empty, Full}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v3.1 — AccountsApi, migrated from Lift to http4s.
 * Genuine data-backed endpoints: getAccounts (GET /accounts, 200),
 * getAccountsAccountId (GET /accounts/{accountId}, 200).
 */
object Http4sUKOBv310Accounts extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): code.api.berlin.group.v1_3.JvalueCaseClass = code.api.berlin.group.v1_3.JvalueCaseClass(com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject])
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion

  lazy val getAccounts: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val detailViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID)
        val basicViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
          (moderatedAttributes, _) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
            accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
            basicViewId,
            Some(cc))
        } yield {
          val allAccounts: List[Box[(BankAccount, View)]] = for (account: BankAccount <- accounts) yield {
            APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc)).or(
              APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc))
            ) match {
              case Full(view) => Full((account, view))
              case _          => Empty
            }
          }
          val accountsWithView: List[(BankAccount, View)] = allAccounts.filter(_.isDefined).map(_.openOrThrowException(attemptedToOpenAnEmptyBox))
          JSONFactory_UKOpenBanking_310.createAccountsListJSON(accountsWithView, moderatedAttributes)
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccounts),
    "GET",
    "/accounts",
    "Get Accounts",
    s"""""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : {},
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Account" : [ {
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Accounts") :: Nil,
    http4sPartialFunction = Some(getAccounts)
  )

  lazy val getAccountsAccountId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / accountIdStr =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val accountId = AccountId(accountIdStr)
        val detailViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID)
        val basicViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)
        for {
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u) map {
            _.filter(_.accountId.value == accountId.value)
          }
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
          (moderatedAttributes, _) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
            accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
            basicViewId,
            Some(cc))
        } yield {
          val allAccounts: List[Box[(BankAccount, View)]] = for (account: BankAccount <- accounts) yield {
            APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc)).or(
              APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc))
            ) match {
              case Full(view) => Full((account, view))
              case _          => Empty
            }
          }
          val accountsWithView: List[(BankAccount, View)] = allAccounts.filter(_.isDefined).map(_.openOrThrowException(attemptedToOpenAnEmptyBox))
          JSONFactory_UKOpenBanking_310.createAccountsListJSON(accountsWithView, moderatedAttributes)
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountId),
    "GET",
    "/accounts/ACCOUNT_ID",
    "Get Accounts",
    s"""
""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-05T13:09:30.399Z",
    "LastAvailableDateTime": "2019-03-05T13:09:30.399Z",
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
    "Account" : [ {
      "Account" : [ {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      }, {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      } ],
      "Servicer" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    }, {
      "Account" : [ {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      }, {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      } ],
      "Servicer" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Accounts") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccounts(req)
      .orElse(getAccountsAccountId(req))
  }
}
