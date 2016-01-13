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
      apiVersion,
      "allAccountsAllBanks",
      "GET",
      "/accounts",
      "Get all accounts a user has access to at all banks (private + public)",
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



  }
}

object APIMethods200 {
}
