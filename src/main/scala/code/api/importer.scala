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
package code.api

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
import code.model.dataAccess.{ Account, OBPEnvelope, OBPUser, HostedAccount, LocalStorage }
import code.model.{ModeratedTransaction, ModeratedBankAccount, View, BankAccount, Public, Bank, User}
import code.model.dataAccess.OBPEnvelope._
import java.util.Date
import code.api.OAuthHandshake._

object ImporterAPI extends RestHelper with Loggable {
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

      val envelopes : List[OBPEnvelope]= rawEnvelopes.flatMap(e => {
        OBPEnvelope.envlopesFromJvalue(e)
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
      val createdEnvelopes = EnvelopeInserter !? (3 seconds, envelopes)

      createdEnvelopes match {
        case Full(l: List[JObject]) =>{
            logger.info("inserted " + l.size + "transactions")
            if(envelopes.size!=0)
            {
              //we assume here that all the Envelopes concerns only one account
              val accountNumber = envelopes(0).obp_transaction.get.this_account.get.number.get
              val bankName = envelopes(0).obp_transaction.get.this_account.get.bank.get.name.get
              val accountKind = envelopes(0).obp_transaction.get.this_account.get.kind.get
              val holder = envelopes(0).obp_transaction.get.this_account.get.holder.get
              //Get all accounts with this account number and kind
              val accounts = Account.findAll(("number" -> accountNumber) ~ ("kind" -> accountKind) ~ ("holder" -> holder))
              //Now get the one that actually belongs to the right bank
              val wantedAccount = accounts.find(_.bankName == bankName)
              wantedAccount match {
                case Some(account) =>  {
                  def updateAccountBalance() = {
                    val newest = OBPEnvelope.findAll(("obp_transaction.this_account.number" -> accountNumber) ~
                    								 ("obp_transaction.this_account.kind" -> accountKind) ~
                    								 ("obp_transaction.this_account.bank.name" -> bankName),
                    								 ("obp_transaction.details.completed" -> -1), Limit(1)).headOption
                    if(newest.isDefined) {
                      logger.debug("Updating current balance for " + bankName + "/" + accountNumber + "/" + accountKind)
                      account.balance(newest.get.obp_transaction.get.details.get.new_balance.get.amount.get).save
                    }
                    else logger.warn("Could not update latest account balance")
                  }
                  account.lastUpdate(new Date).save
                  updateAccountBalance()
                }
                case _ => logger.info("BankName/accountNumber/kind not found :  " + bankName + "/" + accountNumber + "/" + accountKind)
              }
            }
            JsonResponse(JArray(l))
          }
        case _ => InternalServerErrorResponse()
      }
    }
  }
}