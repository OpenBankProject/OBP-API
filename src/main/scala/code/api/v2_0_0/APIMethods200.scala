package code.api.v2_0_0

import code.api.util.APIUtil
import code.api.v1_2_1.APIMethods121
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.Extraction
import net.liftweb.common._
import code.model._
import net.liftweb.json.JsonAST.JValue
import APIUtil._
import net.liftweb.util.Helpers._
import net.liftweb.http.rest.RestHelper
import java.net.URL
import net.liftweb.util.Props
import code.bankconnectors._
import code.bankconnectors.OBPOffset
import code.bankconnectors.OBPFromDate
import code.bankconnectors.OBPToDate
import code.model.ViewCreationJSON
import net.liftweb.common.Full
import code.model.ViewUpdateData

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import net.liftweb.json.JsonDSL._


trait APIMethods200 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here

  // New 2.0.0
  private def bankAccountBasicListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(bankAccountBasicList(bankAccounts, user))
  }

  private def bankAccountBasicList(bankAccounts: List[BankAccount], user : Box[User]): List[AccountBasicJSON] = {
    val accJson : List[AccountBasicJSON] = bankAccounts.map( account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[ViewBasicJSON] =
        views.map( v => {
          JSONFactory.createViewBasicJSON(v)
        })
      JSONFactory.createAccountBasicJSON(account,viewsAvailable)
    })
    accJson
  }


  // helper methods end here

  val Implementations2_0_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "2_0_0"

    resourceDocs += ResourceDoc(
      allAccountsAllBanks,
      apiVersion,
      "allAccountsAllBanks",
      "GET",
      "/accounts",
      "Get accounts a user can view (all banks).",
      """Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views. If
         |the user is authenticated, the list will contain non-public accounts to which the user has access, in addition to
         |all public accounts.
         |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val allAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for all banks (private + public)
      case "accounts" :: Nil JsonGet json => {
        user =>
          Full(successJsonResponse(bankAccountBasicListToJson(BankAccount.accounts(user), user)))
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAllBanks,
      apiVersion,
      "privateAccountsAllBanks",
      "GET",
      "/accounts/private",
      "Get accounts a user has privileged access to (all banks).",
      """Returns the list of accounts containing private (non-public) views for the user at all banks.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val privateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for all banks
      case "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
          } yield {
            val availableAccounts = BankAccount.nonPublicAccounts(u)
            successJsonResponse(bankAccountBasicListToJson(availableAccounts, Full(u)))
          }
      }
    }


    resourceDocs += ResourceDoc(
      publicAccountsAllBanks,
      apiVersion,
      "publicAccountsAllBanks",
      "GET",
      "/accounts/public",
      "Get accounts that have public views (all banks).",
      """Returns the list of accounts containing public views at all banks
        |For each account the API returns the ID and the available views. Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val publicAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for all banks
      case "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          val publicAccountsJson = bankAccountBasicListToJson(BankAccount.publicAccounts, Empty)
          Full(successJsonResponse(publicAccountsJson))
      }
    }




    resourceDocs += ResourceDoc(
      allAccountsAtOneBank,
      apiVersion,
      "allAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts",
      "Get accounts for a single bank the user has either private or public access to.",
      """Returns the list of accounts at BANK_ID that the user has access to.
        |For each account the API returns the account ID and the available views.
        |
        |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views.
      """,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val allAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for a single bank (private + public)
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet json => {
        user =>
          for{
            bank <- Bank(bankId)
          } yield {
            val availableAccounts = bank.accounts(user)
            successJsonResponse(bankAccountBasicListToJson(availableAccounts, user))
          }
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      apiVersion,
      "privateAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get accounts at one bank where the user has private access.",
      """Returns the list of private (non-public) accounts at BANK_ID that the user has access to.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val privateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            bank <- Bank(bankId)
          } yield {
            val availableAccounts = bank.nonPublicAccounts(u)
            successJsonResponse(bankAccountBasicListToJson(availableAccounts, Full(u)))
          }
      }
    }

    resourceDocs += ResourceDoc(
      publicAccountsAtOneBank,
      apiVersion,
      "publicAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/public",
      "Get accounts (public) for a single bank.",
      """Returns a list of the public accounts at BANK_ID. For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is not required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val publicAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          for {
            bank <- Bank(bankId)
          } yield {
            val publicAccountsJson = bankAccountBasicListToJson(bank.publicAccounts, Empty)
            successJsonResponse(publicAccountsJson)
          }
      }
    }







  }
}

object APIMethods200 {
}
