/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v1_0

import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.util.Helpers._
import code.model._
import code.api.OAuthHandshake._
import net.liftweb.util.Helpers.now
import _root_.net.liftweb.json.Serialization
import net.liftweb.json.NoTypeHints
import code.api.OAuthHandshake.getUser
import code.bankconnectors._
import net.liftweb.json.JsonAST.JObject
import code.bankconnectors.OBPToDate
import net.liftweb.http.InMemoryResponse
import net.liftweb.common.Full
import code.metrics.APIMetrics

object OBPAPI1_0 extends RestHelper with Loggable {
  import java.text.SimpleDateFormat

  implicit val _formats = Serialization.formats(NoTypeHints)

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  private def logAPICall =
    APIMetrics.apiMetrics.vend.saveMetric(S.uriAndQueryString.getOrElse(""), (now: TimeSpan))

  serve("obp" / "v1.0" prefix {

    case Nil JsonGet json => {
      //log the API call
      logAPICall

      def gitCommit : String = {
        val commit = tryo{
          val properties = new java.util.Properties()
          properties.load(getClass().getClassLoader().getResourceAsStream("git.properties"))
          properties.getProperty("git.commit.id", "")
        }
        commit getOrElse ""
      }

      val apiDetails = {
        ("api" ->
          ("version" -> "1.0") ~
          ("git_commit" -> gitCommit) ~
          ("hosted_by" ->
            ("organisation" -> "TESOBE") ~
            ("email" -> "contact@tesobe.com") ~
            ("phone" -> "+49 (0)30 8145 3994"))) ~
        ("links" ->
          ("rel" -> "banks") ~
          ("href" -> "/banks") ~
          ("method" -> "GET") ~
          ("title" -> "Returns a list of banks supported on this server"))
      }

      JsonResponse(apiDetails)
    }

    case BankId(bankId) :: "accounts" :: accountAlias :: "transactions" :: viewName :: Nil JsonGet json => {

      //log the API call
      logAPICall

      import code.api.OAuthHandshake._
      val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil

      def asInt(s: Box[String], default: Int): Int = {
        s match {
          case Full(str) => tryo { str.toInt } getOrElse default
          case _ => default
        }
      }
      val limit = asInt(json.header("obp_limit"), 50)
      val offset = asInt(json.header("obp_offset"), 0)
      /**
       * sortBy is currently disabled as it would open up a security hole:
       *
       * sortBy as currently implemented will take in a parameter that searches on the mongo field names. The issue here
       * is that it will sort on the true value, and not the moderated output. So if a view is supposed to return an alias name
       * rather than the true value, but someone uses sortBy on the other bank account name/holder, not only will the returned data
       * have the wrong order, but information about the true account holder name will be exposed due to its position in the sorted order
       *
       * This applies to all fields that can have their data concealed... which in theory will eventually be most/all
       *
       */
      //val sortBy = json.header("obp_sort_by")
      val sortBy = None
      val sortDirection = OBPOrder(json.header("obp_sort_by"))
      val fromDate = tryo{dateFormat.parse(json.header("obp_from_date") getOrElse "")}.map(OBPFromDate(_))
      val toDate = tryo{dateFormat.parse(json.header("obp_to_date") getOrElse "")}.map(OBPToDate(_))

      val basicParams = List(OBPLimit(limit),
                      OBPOffset(offset),
                      OBPOrdering(sortBy, sortDirection))
      val params : List[OBPQueryParam] = fromDate.toList ::: toDate.toList ::: basicParams
      val response = for {
        bankAccount <- BankAccount(bankId, accountAlias)
        view <- View.fromUrl(viewName, bankAccount)
        transactions <- bankAccount.getModeratedTransactions(getUser(httpCode,oAuthParameters.get("oauth_token")), view, params : _*)
      } yield {
        JsonResponse("transactions" -> transactions.map(t => t.toJson(view)))
      }

      response match {
        case Full(r) => () => Full(r)
        case _ => () => Full(InMemoryResponse(data.getBytes, headers, Nil, 401))
      }

    }

    case BankId(bankId) :: "accounts" :: accountAlias :: "transactions" ::
      transactionID :: "transaction" :: viewName :: Nil JsonGet  json => {

      //log the API call
      logAPICall

      val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
      val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

      val moderatedTransactionAndView = for {
        bank <- Bank(bankId) ?~ { "bank "  + bankId + " not found"} ~> 404
        account <- BankAccount(bankId, accountAlias) ?~ { "account "  + accountAlias + " not found for bank"} ~> 404
        view <- View.fromUrl(viewName, account) ?~ { "view "  + viewName + " not found for account"} ~> 404
        moderatedTransaction <- account.moderatedTransaction(transactionID, view, user) ?~ "view/transaction not authorised" ~> 401
      } yield {
        (moderatedTransaction, view)
      }

      val links : List[JObject] = Nil

      moderatedTransactionAndView.map(mtAndView => JsonResponse(("transaction" -> mtAndView._1.toJson(mtAndView._2)) ~
                              ("links" -> links)))
    }

    case BankId(bankId) :: "accounts" :: accountAlias :: "transactions" ::
      transactionID :: "comments" :: viewName :: Nil JsonGet json => {

      //log the API call
      logAPICall

      val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
      val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

      val comments = for {
        bank <- Bank(bankId) ?~ { "bank "  + bankId + " not found"} ~> 404
        account <- BankAccount(bankId, accountAlias) ?~ { "account "  + accountAlias + " not found for bank"} ~> 404
        view <- View.fromUrl(viewName,account) ?~ { "view "  + viewName + " not found for account"} ~> 404
        moderatedTransaction <- account.moderatedTransaction(transactionID, view, user) ?~ "view/transaction not authorised" ~> 401
        comments <- Box(moderatedTransaction.metadata).flatMap(_.comments) ?~ "transaction metadata not authorised" ~> 401
      } yield comments

      val links : List[JObject] = Nil

        comments.map(cs => JsonResponse(("comments" -> cs.map(_.toJson)) ~
                        ("links" -> links)))
    }

    case BankId(bankId) :: "accounts" :: Nil JsonGet json => {

      //log the API call
      logAPICall

      val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
      val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

      def bankAccountSet2JsonResponse(bankAccounts: Set[BankAccount]): LiftResponse = {
        val accJson = bankAccounts.map(bAcc => bAcc.overviewJson(user))
        JsonResponse(("accounts" -> accJson))
      }

      Bank(bankId) match {
        case Full(bank) =>
        {
          if(httpCode == 200)
          {
            bank.accountv12AndBelow(user) match {
              case Full(a) =>  bankAccountSet2JsonResponse(a.toSet)
              case _ => InMemoryResponse("no account found".getBytes, Nil, Nil, 404)
            }
          }
          else
            InMemoryResponse(data.getBytes, Nil, Nil, httpCode)
        }
        case _ =>  {
          val error = "bank " + bankId + " not found"
          InMemoryResponse(error.getBytes(), headers, Nil, 404)
        }
      }
    }

    case BankId(bankId) :: "accounts" :: accountAlias :: "account" :: viewName :: Nil JsonGet json => {

      //log the API call
      logAPICall

      val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
      val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
      val user = getUser(httpCode,oAuthParameters.get("oauth_token"))

      case class ModeratedAccountAndViews(account: ModeratedBankAccount, views: List[View])

      val moderatedAccountAndViews = for {
        bank <- Bank(bankId) ?~ { "bank "  + bankId + " not found"} ~> 404
      account <- BankAccount(bankId, accountAlias) ?~ { "account "  + accountAlias + " not found for bank"} ~> 404
      view <- View.fromUrl(viewName, account) ?~ { "view "  + viewName + " not found for account"} ~> 404
      moderatedAccount <- account.moderatedBankAccount(view, user)  ?~ {"view/account not authorised"} ~> 401
      availableViews <- Full(account.permittedViews(user))
      } yield ModeratedAccountAndViews(moderatedAccount, availableViews)

      def linkJson(view: View): JObject = {
        ("rel" -> view.name) ~
        ("href" -> { "/" + bankId + "/accounts/" + accountAlias + "/transactions/" + view.permalink }) ~
        ("method" -> "GET") ~
        ("title" -> view.description)
      }

      def bankAccountMetaData(mv : ModeratedAccountAndViews) = {
        ("views_available" -> mv.views.map(_.toJson)) ~
        ("links" -> mv.views.map(linkJson))
      }

      moderatedAccountAndViews.map(mv => JsonResponse("account" -> mv.account.toJson ~ bankAccountMetaData(mv)))
    }

    case BankId(bankId) :: "offices" :: Nil JsonGet json => {

      //log the API call
      logAPICall

      //TODO: An office model needs to be created
      val offices : List[JObject] = Nil
      JsonResponse("offices" -> offices)
    }

    case BankId(bankId) :: "bank" :: Nil JsonGet json => {

      //log the API call
      logAPICall

      def links = {
        def accounts = {
          ("rel" -> "accounts") ~
          ("href" -> {"/" + bankId + "/accounts"}) ~
          ("method" -> "GET") ~
          ("title" -> "Get list of accounts available")
        }

        def offices = {
          ("rel" -> "offices") ~
          ("href" -> {"/" + bankId + "/offices"}) ~
          ("method" -> "GET") ~
          ("title" -> "Get list of offices")
        }

        List(accounts, offices)
      }

      val bank = for {
        bank <- Bank(bankId) ?~ { "bank " + bankId + " not found"} ~> 404
      } yield bank

      bank.map(b => JsonResponse(b.detailedJson ~ ("links" -> links)))
    }

    case "banks" :: Nil JsonGet json => {

      //log the API call
      logAPICall

      JsonResponse("banks" -> Bank.toJson(Bank.all))
    }
  })

}