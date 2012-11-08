/**
 * Open Bank Project
 *
 * Copyright 2011,2012 TESOBE / Music Pictures Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Open Bank Project (http://www.openbankproject.com)
 * Copyright 2011,2012 TESOBE / Music Pictures Ltd
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 * by
 * Simon Redfern : simon AT tesobe DOT com
 * Everett Sochowski: everett AT tesobe DOT com
 *
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
import code.model.dataAccess.{ OBPEnvelope, OBPUser }
import code.model.dataAccess.HostedAccount
import code.model.dataAccess.LocalStorage
import code.model.traits.ModeratedTransaction
import code.model.traits.View
import code.model.implementedTraits.View
import code.model.dataAccess.OBPEnvelope._
import code.model.traits.BankAccount
import code.model.implementedTraits.Anonymous

  // Note: on mongo console db.chooseitems.ensureIndex( { location : "2d" } )

  // Call like http://localhost:8080/api/balance/theaccountnumber/call.json
  // See http://www.assembla.com/spaces/liftweb/wiki/REST_Web_Services

  object OBPRest extends RestHelper with Loggable {

	val dateFormat = ModeratedTransaction.dateFormat
	
    serve("obp" / "v1.0" prefix {
      case bankAlias :: "accounts" :: accountAlias :: "transactions" :: viewName :: Nil JsonGet json => {

        def asInt(s: Box[String], default: Int): Int = {
          s match {
            case Full(str) => tryo { str.toInt } getOrElse default
            case _ => default
          }
        }
        val bankAccount = BankAccount(bankAlias, accountAlias)
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
        
        def getUser() : Box[OBPUser] = {
          None //TODO: Implement oAuth
        }
        
        val transactions = for {
          b <- bankAccount
          v <- View.fromUrl(viewName) //TODO: This will have to change if we implement custom view names for different accounts
        } yield getTransactions(b, v, getUser())
        
        transactions match {
          case Full(t) => t
          case _ => NotFoundResponse("no account found")
        }
      }

    })

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
          logger.info("Received " + envelopes.size +
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
            case Full(l: List[JObject]) => JsonResponse(JArray(l))
            case _ => InternalServerErrorResponse()
          }
        }
        
        def valid(secret : String) = {
          val authorised = for (validSecret <- Props.get("exceptional_account_secret"))
        	  yield secret == validSecret 
        	  
          authorised getOrElse false
        }
        
        secretKey match {
          case Full(s) => if(valid(s)) addMatchingTransactions(s) else NotFoundResponse()
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
          case Full(l: List[JObject]) => JsonResponse(JArray(l))
          case _ => InternalServerErrorResponse()
        }
      }
      /*
      returns the anonymous view automatically.
      For the moment the is only one account so there is no check to do 
      before returning the Json
    */
      case Req("api" :: "accounts" :: "tesobe" :: "anonymous" :: Nil, _, GetRequest) =>
        {
          val allEnvelopes = OBPEnvelope.findAll(QueryBuilder.start().get)
          val envelopeJson = allEnvelopes.map(envelope =>
            envelope.asMediatedJValue("accessLevel"))
          Full(JsonResponse(envelopeJson))
        }
      /*
      return a JSon of all the transactions.
      requires an OAuth token as authentication mechanism. 
    */
      case Req("api" :: "accounts" :: "tesobe" :: accessLevel :: Nil, _, GetRequest) =>
        {
          //check if the accessLeve required already exists 
          def doesTheViewExists(accessLevel: String): Boolean =
            {
              /* 
            for the moment the number of views is limited this why 
            they are hard coded. The work on 'dataAbstraction' branch 
            will avoid this kind of verification. 
          */
              val currentExistingViews = List("anonymous", "our-network", "team",
                "board", "my-view")
              currentExistingViews.contains(accessLevel)
            }
          //check of the token authorize the application to have access 
          //to accessLevel
          def doesTheTokenHasAccess(accessLevel: String, tokenID: String): Boolean =
            {
              import code.model.dataAccess.{ Account, Privilege }
              //check if the privileges of the user authorize if to access
              // to the access level
              def doesTheUserHasAccess(accessLevel: String, user: OBPUser): Boolean =
                {

                  Account.find(("holder", "Music Pictures Limited")) match {
                    case Full(account) => if (accessLevel == "anonymous")
                      account.anonAccess.is
                    else
                      HostedAccount.find(By(HostedAccount.accountID, account.id.toString)) match {
                        case Full(hostedAccount) =>
                          Privilege.find(By(Privilege.account, hostedAccount), By(Privilege.user, user.id)) match {
                            case Full(privilege) => privilegeCheck(accessLevel, privilege)
                            case _ => false
                          }
                        case _ => false
                      }
                    case _ => false
                  }
                }

              //due to the actual privilege mechanism : the access level (API) 
              //and the privilege of the account are stored differently 
              //So the function do the match 
              def privilegeCheck(accessLevel: String, privilege: Privilege): Boolean =
                if (accessLevel == "my-view")
                  privilege.ownerPermission.is
                else if (accessLevel == "our-network")
                  privilege.ourNetworkPermission.is
                else if (accessLevel == "team")
                  privilege.teamPermission.is
                else if (accessLevel == "board")
                  privilege.boardPermission.is
                else
                  false

              import code.model.Token
              Token.find(By(Token.key, tokenID)) match {
                case Full(token) => OBPUser.find(By(OBPUser.id, token.userId)) match {
                  case Full(user) =>
                    doesTheUserHasAccess(accessLevel, user)
                  case _ => false
                }
                case _ => false
              }
            }

          import code.snippet.OAuthHandshake._
          val headers = ("Content-type" -> "application/x-www-form-urlencoded") :: Nil
          //Extract the OAuth parameters from the header and test if the request is valid
          var (httpCode, data, oAuthParameters) = validator("protectedResource", "GET")
          //Test if the view exists and the OAuth request is valid
          if (doesTheViewExists(accessLevel) && httpCode == 200) {
            //check that the token gives access to the required view
            if (doesTheTokenHasAccess(accessLevel, oAuthParameters.get("oauth_token").get)) {
              val allEnvelopes = OBPEnvelope.findAll(QueryBuilder.start().get)
              val envelopeJson = allEnvelopes.map(envelope =>
                envelope.asMediatedJValue(accessLevel))
              Full(JsonResponse(envelopeJson))
            } else
              Full(InMemoryResponse(data, headers, Nil, 401))
          } else if (!doesTheViewExists(accessLevel) && httpCode == 200)
            Full(InMemoryResponse(data, headers, Nil, 404))
          else
            Full(InMemoryResponse(data, headers, Nil, httpCode))
        }
    }
  }
} 
