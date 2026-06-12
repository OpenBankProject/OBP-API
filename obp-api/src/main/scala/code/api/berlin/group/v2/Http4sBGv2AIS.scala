package code.api.berlin.group.v2

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

object Http4sBGv2AIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV2Prefix = Root / ConstantsBG.berlinGroupVersion2.urlPrefix / ConstantsBG.berlinGroupVersion2.apiShortVersion

  // ── GET /v2/accounts ──────────────────────────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountList),
    "GET",
    "/accounts",
    "Read Account List",
    "Returns a list of bank accounts.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockAccountList,
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getAccountList)
  )

  val getAccountList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "accounts" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockAccountList))
  }

  // ── GET /v2/accounts/{account-id} ─────────────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountDetails),
    "GET",
    "/accounts/ACCOUNT_ID",
    "Read Account Details",
    "Returns details of a single bank account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockAccountDetails("ACCOUNT_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getAccountDetails)
  )

  val getAccountDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "accounts" / accountId if !accountId.contains("/") =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockAccountDetails(accountId)))
  }

  // ── GET /v2/accounts/{account-id}/balances ────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountBalances),
    "GET",
    "/accounts/ACCOUNT_ID/balances",
    "Read Balance",
    "Returns balances of a given account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockBalances("ACCOUNT_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getAccountBalances)
  )

  val getAccountBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "accounts" / accountId / "balances" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockBalances(accountId)))
  }

  // ── GET /v2/accounts/{account-id}/transactions ────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTransactionList),
    "GET",
    "/accounts/ACCOUNT_ID/transactions",
    "Read Transaction List",
    "Returns transactions of a given account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockTransactions("ACCOUNT_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getTransactionList)
  )

  val getTransactionList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "accounts" / accountId / "transactions" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockTransactions(accountId)))
  }

  // ── GET /v2/accounts/{account-id}/transactions/{transactionId} ────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTransactionDetails),
    "GET",
    "/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID",
    "Read Transaction Details",
    "Returns details of a single transaction.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockTransactionDetails("ACCOUNT_ID", "TRANSACTION_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getTransactionDetails)
  )

  val getTransactionDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "accounts" / accountId / "transactions" / transactionId =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockTransactionDetails(accountId, transactionId)))
  }

  // ── GET /v2/card-accounts ─────────────────────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getCardAccountList),
    "GET",
    "/card-accounts",
    "Read Card Account List",
    "Returns a list of card accounts.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockCardAccountList,
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getCardAccountList)
  )

  val getCardAccountList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "card-accounts" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockCardAccountList))
  }

  // ── GET /v2/card-accounts/{account-id} ────────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getCardAccountDetails),
    "GET",
    "/card-accounts/ACCOUNT_ID",
    "Read Card Account Details",
    "Returns details of a single card account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockCardAccountDetails("ACCOUNT_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getCardAccountDetails)
  )

  val getCardAccountDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "card-accounts" / accountId if !accountId.contains("/") =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockCardAccountDetails(accountId)))
  }

  // ── GET /v2/card-accounts/{account-id}/balances ───────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getCardAccountBalances),
    "GET",
    "/card-accounts/ACCOUNT_ID/balances",
    "Read Card Account Balances",
    "Returns balances of a given card account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockCardAccountBalances("ACCOUNT_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getCardAccountBalances)
  )

  val getCardAccountBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "card-accounts" / accountId / "balances" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockCardAccountBalances(accountId)))
  }

  // ── GET /v2/card-accounts/{account-id}/transactions ───────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getCardAccountTransactionList),
    "GET",
    "/card-accounts/ACCOUNT_ID/transactions",
    "Read Card Account Transaction List",
    "Returns transactions of a given card account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockCardAccountTransactions("ACCOUNT_ID"),
    List(UnknownError),
    apiTagPSD2AIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(getCardAccountTransactionList)
  )

  val getCardAccountTransactionList: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV2Prefix` / "card-accounts" / accountId / "transactions" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockCardAccountTransactions(accountId)))
  }

  // ── Combined routes ───────────────────────────────────────────────

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountList(req)
      .orElse(getAccountBalances(req))
      .orElse(getTransactionDetails(req))
      .orElse(getTransactionList(req))
      .orElse(getAccountDetails(req))
      .orElse(getCardAccountList(req))
      .orElse(getCardAccountBalances(req))
      .orElse(getCardAccountTransactionList(req))
      .orElse(getCardAccountDetails(req))
  }
}
