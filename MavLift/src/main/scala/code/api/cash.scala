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

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST
import JsonAST._
import net.liftweb.common.{Failure,Full, Empty, Box,Loggable}
import net.liftweb.mongodb._
import com.mongodb.casbah.Imports._
import _root_.java.math.MathContext
import org.bson.types._
import org.joda.time.{ DateTime, DateTimeZone }
import java.util.regex.Pattern
import _root_.net.liftweb.util._
import _root_.net.liftweb.http._
import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util.Helpers._
import _root_.net.liftweb.sitemap._
import _root_.scala.xml._
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.http.RequestVar
import _root_.net.liftweb.common.Full
import net.liftweb.mongodb.{ Skip, Limit }
import _root_.net.liftweb.http.S._
import _root_.net.liftweb.mapper.view._
import com.mongodb._
import code.model.dataAccess._
import java.util.Date
import net.liftweb.util.Helpers.now
import net.liftweb.json.Extraction
import _root_.net.liftweb.json.Serialization
import net.liftweb.json.NoTypeHints
import scala.math.BigDecimal._

case class CashTransaction(
  otherParty : String,
  date : Date,
  amount : Double,
  kind : String,
  label : String,
  otherInformation : String
)

object CashAccountAPI extends RestHelper with Loggable {

  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  implicit def successToJson(msg: SuccessMessage): JValue = Extraction.decompose(msg)

  def isValidKey : Boolean = {
    val sentKey : Box[String] =
      for{
        req <- S.request
        sentKey <- req.header("cashApplicationKey")
      } yield sentKey
    val localKey : Box[String] = Props.get("cashApplicationKey")
    localKey == sentKey
  }

  serve("obp" / "v1.0" prefix {

    case "cash-accounts" :: id :: "transactions" :: Nil JsonPost json -> _ => {
      if(isValidKey)
        Account.find(id) match {
          case Full(account) =>
            tryo{
              json.extract[CashTransaction]
            } match {
                case Full(cashTransaction) => {

                  val thisAccountBank = OBPBank.createRecord.
                    IBAN("").
                    national_identifier("").
                    name(account.bankName)

                  val thisAccount = OBPAccount.createRecord.
                    holder(account.holder.get).
                    number(account.number.get).
                    kind(account.kind.get).
                    bank(thisAccountBank)

                  val otherAccountBank = OBPBank.createRecord.
                    IBAN("").
                    national_identifier("").
                    name("")

                  val otherAccount = OBPAccount.createRecord.
                    holder(cashTransaction.otherParty).
                    number("").
                    kind("").
                    bank(otherAccountBank)

                  val amount : BigDecimal = {
                    if(cashTransaction.kind == "in")
                      BigDecimal(cashTransaction.amount).setScale(2,RoundingMode.HALF_UP).abs
                    else
                      BigDecimal((cashTransaction.amount * (-1) )).setScale(2,RoundingMode.HALF_UP)
                  }

                  val newBalance : OBPBalance = OBPBalance.createRecord.
                    currency(account.currency.get).
                    amount(account.balance.get + amount)

                  val newValue : OBPValue = OBPValue.createRecord.
                    currency(account.currency.get).
                    amount(amount)

                  val details = OBPDetails.createRecord.
                    type_en("cash").
                    type_de("Bargeld").
                    posted(cashTransaction.date).
                    other_data(cashTransaction.otherInformation).
                    new_balance(newBalance).
                    value(newValue).
                    completed(cashTransaction.date).
                    label(cashTransaction.label)

                  val transaction = OBPTransaction.createRecord.
                    this_account(thisAccount).
                    other_account(otherAccount).
                    details(details)

                  val env = OBPEnvelope.createRecord.
                    obp_transaction(transaction).save
                  account.balance(account.balance.get + amount).lastUpdate(now)
                  env.createAliases
                  env.save

                  JsonResponse(SuccessMessage("transaction successfully added"), Nil, Nil, 200)
                }
                case _ =>{
                  val error = "Post data must be in the format: \n" +
                    pretty(
                      JsonAST.render(
                        Extraction.decompose(
                          CashTransaction(
                            otherParty = "other party",
                            date = new Date,
                            amount = 1231.12,
                            kind = "in / out",
                            label = "what is this transaction for",
                            otherInformation = "more info"
                          ))))
                  JsonResponse(ErrorMessage(error), Nil, Nil, 400)
                }
              }
          case _ => JsonResponse(ErrorMessage("Account " + id + " not found" ), Nil, Nil, 400)
        }
      else
        JsonResponse(ErrorMessage("No key found or wrong key"), Nil, Nil, 401)
    }
  })
}