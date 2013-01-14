/** 
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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
package com.tesobe.utils {

import code.actors.EnvelopeInserter
import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST._
import java.util.Calendar
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.mongodb._
import net.liftweb.json.JsonAST.JString
import com.mongodb.casbah.Imports._
import _root_.java.math.MathContext
import org.bson.types._
import org.joda.time.{ DateTime, DateTimeZone }
import java.util.regex.Pattern
import _root_.net.liftweb.common._
import _root_.net.liftweb.util._
import _root_.net.liftweb.http._
import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.sitemap._
import _root_.scala.xml._
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.http.RequestVar
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.common.Full
import net.liftweb.mongodb.{ Skip, Limit }
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.mapper.view._
import com.mongodb._
import code.model.dataAccess.{ Account, OBPEnvelope, OBPUser }
import code.model.dataAccess.HostedAccount
import code.model.dataAccess.LocalStorage
import code.model.traits.ModeratedTransaction
import code.model.traits.View
import code.model.implementedTraits.View
import code.model.dataAccess.OBPEnvelope._
import code.model.traits.BankAccount
import code.model.implementedTraits.Anonymous
import code.model.traits.Bank
import code.model.traits.User
import java.util.Date
import code.snippet.OAuthHandshake._
import code.model.traits.ModeratedBankAccount

  object OBPRest extends RestHelper with Loggable {

	  val dateFormat = ModeratedTransaction.dateFormat
    private def getUser(httpCode : Int, tokenID : Box[String]) : Box[OBPUser] = 
    if(httpCode==200)
    {
      import code.model.Token
      Token.find(By(Token.key, tokenID.get)) match {
        case Full(token) => OBPUser.find(By(OBPUser.id, token.userId))
        case _ => Empty   
      }         
    }
    else 
      Empty	
    
    serve("obp" / "v1.0" prefix {
      
      case Nil JsonGet json => {
        
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
      
      case bankAlias :: "accounts" :: accountAlias :: "transactions" :: viewName :: Nil JsonGet json => {
        import code.snippet.OAuthHandshake._
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

        def getTransactions(bankAccount: BankAccount, view: View, user: Option[OBPUser]) = {
          if(bankAccount.authorisedAccess(view, user)) {
            val basicParams = List(OBPLimit(limit), 
                						OBPOffset(offset), 
                						OBPOrdering(sortBy, sortDirection))
                
            val params : List[OBPQueryParam] = fromDate.toList ::: toDate.toList ::: basicParams
            bankAccount.getModeratedTransactions(params: _*)(view.moderate)
          } else Nil
        }
        
        val response = for {
          bankAccount <- BankAccount(bankAlias, accountAlias)
          view <- View.fromUrl(viewName) //TODO: This will have to change if we implement custom view names for different accounts
        } yield {
          val ts = getTransactions(bankAccount, view, getUser(httpCode,oAuthParameters.get("oauth_token")))
          JsonResponse("transactions" -> ts.map(t => t.toJson(view)))
        }
        
        response getOrElse InMemoryResponse(data, headers, Nil, 401) : LiftResponse
      }
      
      case bankAlias :: "accounts" :: accountAlias :: "transactions" ::
    	  transactionID :: "transaction" :: viewName :: Nil JsonGet  json => {
    	
    	val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")     
    	val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
    	
    	val moderatedTransactionAndView = for {
    	  bank <- Bank(bankAlias) ?~ { "bank "  + bankAlias + " not found"} ~> 404
    	  account <- BankAccount(bankAlias, accountAlias) ?~ { "account "  + accountAlias + " not found for bank"} ~> 404
    	  view <- View.fromUrl(viewName) ?~ { "view "  + viewName + " not found for account"} ~> 404
    	  moderatedTransaction <- account.moderatedTransaction(transactionID, view, user) ?~ "view/transaction not authorised" ~> 401
    	} yield {
    	  (moderatedTransaction, view)
    	}
    	
    	val links : List[JObject] = Nil
    	
        moderatedTransactionAndView.map(mtAndView => JsonResponse(("transaction" -> mtAndView._1.toJson(mtAndView._2)) ~
            										("links" -> links)))
      }
    	  
      case bankAlias :: "accounts" :: accountAlias :: "transactions" :: 
    	  transactionID :: "comments" :: viewName :: Nil JsonGet json => {
    	    
    	val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")     
    	val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
    	
    	val comments = for {
    	  bank <- Bank(bankAlias) ?~ { "bank "  + bankAlias + " not found"} ~> 404
    	  account <- BankAccount(bankAlias, accountAlias) ?~ { "account "  + accountAlias + " not found for bank"} ~> 404
    	  view <- View.fromUrl(viewName) ?~ { "view "  + viewName + " not found for account"} ~> 404
    	  moderatedTransaction <- account.moderatedTransaction(transactionID, view, user) ?~ "view/transaction not authorised" ~> 401
    	  comments <- Box(moderatedTransaction.metadata).flatMap(_.comments) ?~ "transaction metadata not authorised" ~> 401
    	} yield comments
    	    
    	val links : List[JObject] = Nil
    	
        comments.map(cs => JsonResponse(("comments" -> cs.map(_.toJson)) ~ 
        								("links" -> links)))
      }
      
      case bankPermalink :: "accounts" :: Nil JsonGet json => {
        val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET") 
        val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil 
        val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
        
        def bankAccountSet2JsonResponse(bankAccounts: Set[BankAccount]): LiftResponse = {
          val accJson = bankAccounts.map(bAcc => bAcc.overviewJson(user))
          JsonResponse(("accounts" -> accJson))
        }
        
        Bank(bankPermalink) match {
          case Full(bank) => 
          {
            val availableAccounts = bank.accounts.filter(_.permittedViews(user).size!=0)
            if(availableAccounts.size!=0)
              bankAccountSet2JsonResponse(availableAccounts) 
            else
              InMemoryResponse(data, headers, Nil, httpCode)
          }
          case _ =>  {
            val error = "bank " + bankPermalink + " not found"
            InMemoryResponse(error.getBytes(), headers, Nil, 404)
          }
        }
      }
      
      case bankAlias :: "accounts" :: accountAlias :: "account" :: viewName :: Nil JsonGet json => {
        val (httpCode, data, oAuthParameters) = validator("protectedResource", "GET") 
        val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil 
        val user = getUser(httpCode,oAuthParameters.get("oauth_token"))
        
        case class ModeratedAccountAndViews(account: ModeratedBankAccount, views: Set[View])
        
        val moderatedAccountAndViews = for {
          bank <- Bank(bankAlias) ?~ { "bank "  + bankAlias + " not found"} ~> 404
    	  account <- BankAccount(bankAlias, accountAlias) ?~ { "account "  + accountAlias + " not found for bank"} ~> 404
    	  view <- View.fromUrl(viewName) ?~ { "view "  + viewName + " not found for account"} ~> 404
    	  moderatedAccount <- account.moderatedBankAccount(view, user)  ?~ {"view/account not authorised"} ~> 401
    	  availableViews <- Full(account.permittedViews(user))
        } yield ModeratedAccountAndViews(moderatedAccount, availableViews)

        def linkJson(view: View): JObject = {
          ("rel" -> view.name) ~
          ("href" -> { "/" + bankAlias + "/accounts/" + accountAlias + "/transactions/" + view.permalink }) ~
          ("method" -> "GET") ~
          ("title" -> view.description)
        }
        
        def bankAccountMetaData(mv : ModeratedAccountAndViews) = {
          ("views_available" -> mv.views.map(_.toJson)) ~
          ("links" -> mv.views.map(linkJson))
        }
        
        moderatedAccountAndViews.map(mv => JsonResponse("account" -> mv.account.toJson ~ bankAccountMetaData(mv)))
      }
      
      case bankAlias :: "offices" :: Nil JsonGet json => {
        //TODO: An office model needs to be created
        val offices : List[JObject] = Nil
        JsonResponse("offices" -> offices)
      }
      
      case bankAlias :: "bank" :: Nil JsonGet json => {
        
        def links = {
          def accounts = {
            ("rel" -> "accounts") ~
            ("href" -> {"/" + bankAlias + "/accounts"}) ~
            ("method" -> "GET") ~
          	("title" -> "Get list of accounts available")
          }
          
          def offices = {
            ("rel" -> "offices") ~
            ("href" -> {"/" + bankAlias + "/offices"}) ~
            ("method" -> "GET") ~
          	("title" -> "Get list of offices")
          }
          
          List(accounts, offices)
        }
        
        val bank = for {
          bank <- Bank(bankAlias) ?~ { "bank " + bankAlias + " not found"} ~> 404
        } yield bank
        
        bank.map(b => JsonResponse(b.detailedJson ~ ("links" -> links)))
      }
      
      case "banks" :: Nil JsonGet json => {
        JsonResponse("banks" -> Bank.toJson(Bank.all))
      }
    }
    )

    serve {
	  //a temporary way to add transaction via api for a single specific exception case. should be removed later.
      case "api" :: "tmp" :: "transactions" :: Nil JsonPost json => {
        val secretKey = S.param("secret")

        def addMatchingTransactions(secret: String) = {
          val rawEnvelopes = json._1.children
          val envelopes = rawEnvelopes.flatMap(OBPEnvelope.fromJValue)
          val matchingEnvelopes = for {
            e <- envelopes
            bankName <- Props.get("exceptional_account_bankName")
            number <- Props.get("exceptional_account_number")
            kind <- Props.get("exceptional_account_kind")
            if(e.obp_transaction.get.this_account.get.bank.get.name.get == bankName)
            if(e.obp_transaction.get.this_account.get.number.get == number)
            if(e.obp_transaction.get.this_account.get.kind.get == kind)
          } yield e

          val ipAddress = json._2.remoteAddr
          logger.info("Received " + rawEnvelopes.size +
            " json transactions to insert from ip address " + ipAddress)
          logger.info("Received " + matchingEnvelopes.size +
            " valid transactions to insert from ip address " + ipAddress)

          /**
           * Using an actor to do insertions avoids concurrency issues with
           * duplicate transactions by processing transaction batches one
           * at a time. We'll have to monitor this to see if non-concurrent I/O
           * is too inefficient. If it is, we could break it up into one actor
           * per "Account".
           */
          val createdEnvelopes = EnvelopeInserter !? (3 seconds, matchingEnvelopes)

          createdEnvelopes match {
            case Full(l: List[JObject]) =>{
              if(matchingEnvelopes.size!=0)
              {  
                Account.find(("number" -> Props.get("exceptional_account_number").getOrElse("")) ~ 
                  ("bankName" -> Props.get("exceptional_account_bankName").getOrElse("")) ~ 
                  ("kind" -> Props.get("exceptional_account_kind").getOrElse("")))
                match {
                  case Full(account) =>  account.lastUpdate(new Date).save
                  case _ => 
                }
              }
              JsonResponse(JArray(l))
            }
            case _ => InternalServerErrorResponse()
          }
        }
        
        def valid(secret : String) = {
          val authorised = for (validSecret <- Props.get("exceptional_account_secret"))
        	  yield secret == validSecret 
        	  
          authorised getOrElse false
        }
        
        secretKey match {
          case Full(s) => if(valid(s)) 
                            addMatchingTransactions(s) 
                          else 
                            UnauthorizedResponse("wrong secret key")
          case _ => NotFoundResponse()
        }
        
      }
    }
	
    serve {

      /**
       * curl -i -H "Content-Type: application/json" -X POST -d '{
       * "obp_transaction":{
       * "this_account":{
       * "holder":"Music Pictures Limited",
       * "number":"123567",
       * "kind":"current",
       * "bank":{
       * "IBAN":"DE1235123612",
       * "national_identifier":"de.10010010",
       * "name":"Postbank"
       * }
       * },
       * "other_account":{
       * "holder":"Client 1",
       * "number":"123567",
       * "kind":"current",
       * "bank":{
       * "IBAN":"UK12222879",
       * "national_identifier":"uk.10010010",
       * "name":"HSBC"
       * }
       * },
       * "details":{
       * "type_en":"Transfer",
       * "type_de":"Überweisung",
       * "posted":{
       * "$dt":"2012-01-04T18:06:22.000Z"
       * },
       * "completed":{
       * "$dt":"2012-09-04T18:52:13.000Z"
       * },
       * "new_balance":{
       * "currency":"EUR",
       * "amount":"4323.45"
       * },
       * "value":{
       * "currency":"EUR",
       * "amount":"123.45"
       * },
       * "other_data":"9"
       * }
       * }
       * }  ' http://localhost:8080/api/transactions
       */
      case "api" :: "transactions" :: Nil JsonPost json => {

        //
        // WARNING!
        //
        // If you have not configured a web server to restrict this URL 
        // appropriately, anyone will be
        // able to post transactions to your database. This would obviously 
        // be undesirable. So you should
        // definitely sort that out.
        //
        //

        val rawEnvelopes = json._1.children

        val envelopes = rawEnvelopes.map(e => {
          OBPEnvelope.fromJValue(e)
        })

        val ipAddress = json._2.remoteAddr
        logger.info("Received " + rawEnvelopes.size +
          " json transactions to insert from ip address " + ipAddress)
        logger.info("Received " + envelopes.size +
          " valid transactions to insert from ip address " + ipAddress)

        /**
         * Using an actor to do insertions avoids concurrency issues with
         * duplicate transactions by processing transaction batches one
         * at a time. We'll have to monitor this to see if non-concurrent I/O
         * is too inefficient. If it is, we could break it up into one actor
         * per "Account".
         */
        val createdEnvelopes = EnvelopeInserter !? (3 seconds, envelopes.flatten)

        createdEnvelopes match {
          case Full(l: List[JObject]) =>{
              if(envelopes.size!=0)
              {  
                //we assume here that all the Envelopes concerns only one account 
                val accountNumber = envelopes(0).get.obp_transaction.get.this_account.get.number.get
                val bankName = envelopes(0).get.obp_transaction.get.this_account.get.bank.get.name.get
                val accountKind = envelopes(0).get.obp_transaction.get.this_account.get.kind.get
                Account.find(("number" -> accountNumber) ~ ("bankName" -> bankName) ~ ("kind" -> accountKind))
                match {
                  case Full(account) =>  account.lastUpdate(new Date).save
                  case _ => 
                }
              }
              JsonResponse(JArray(l))
            } 
          case _ => InternalServerErrorResponse()
        }
      }
    }
  }
} 
