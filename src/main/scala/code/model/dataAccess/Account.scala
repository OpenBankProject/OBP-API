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

package code.model.dataAccess

import com.mongodb.QueryBuilder
import net.liftweb.mongodb.record.MongoMetaRecord
import net.liftweb.mongodb.record.field.ObjectIdPk
import net.liftweb.mongodb.record.field.ObjectIdRefListField
import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.mongodb.record.field.ObjectIdRefField
import net.liftweb.mongodb.record.field.DateField
import net.liftweb.common.{ Box, Full, Loggable }
import net.liftweb.mongodb.record.field.BsonRecordField
import net.liftweb.mongodb.record.BsonRecord
import net.liftweb.record.field.{ StringField, BooleanField, DecimalField }
import net.liftweb.mongodb.{Limit, Skip}
import code.model.{AccountId, BankId, AccountOwner, BankAccount}
import net.liftweb.mongodb.BsonDSL._
import OBPEnvelope._
import code.bankconnectors._
import code.bankconnectors.OBPOffset
import net.liftweb.common.Full
import scala.Some
import code.bankconnectors.OBPLimit
import code.bankconnectors.OBPOrdering
import net.liftweb.mongodb.Limit
import net.liftweb.mongodb.Skip


class Account extends MongoRecord[Account] with ObjectIdPk[Account] with Loggable{
  def meta = Account

  object balance extends DecimalField(this, 0)
  object holder extends StringField(this, 255)
  object number extends StringField(this, 255)
  object kind extends StringField(this, 255)
  object name extends StringField(this, 255)
  object permalink extends StringField(this, 255)
  object bankID extends ObjectIdRefField(this, HostedBank)
  object label extends StringField(this, 255)
  object currency extends StringField(this, 255)
  object iban extends StringField(this, 255)
  object lastUpdate extends DateField(this)

  def bankName: String ={
    bankID.obj match {
      case Full(bank) => bank.name.get
      case _ => ""
    }
  }

  def accountId : AccountId = {
    AccountId(permalink.get)
  }

  def bankNationalIdentifier: String = {
    bankID.obj match {
      case Full(bank) => bank.national_identifier.get
      case _ => ""
    }
  }

  def bankId: BankId = {
    bankID.obj match  {
      case Full(bank) => BankId(bank.permalink.get)
      case _ => BankId("")
    }
  }

  def transactionsForAccount: QueryBuilder = {
    QueryBuilder
    .start("obp_transaction.this_account.number")
    .is(number.get)
    //FIX: change that to use the bank identifier
    .put("obp_transaction.this_account.bank.national_identifier")
    .is(bankNationalIdentifier)
  }

  //find all the envelopes related to this account
  def allEnvelopes: List[OBPEnvelope] = OBPEnvelope.findAll(transactionsForAccount.get)

  def envelopes(queryParams: OBPQueryParam*): List[OBPEnvelope] = {
    import com.mongodb.DBObject
    import net.liftweb.mongodb.FindOption

    val limit: Seq[Limit] = queryParams.collect { case OBPLimit(value) => Limit(value) }
    val offset: Seq[Skip] = queryParams.collect { case OBPOffset(value) => Skip(value) }
    val limitAndOffset: Seq[FindOption] = limit ++ offset

    val fromDate: Option[OBPFromDate] = queryParams.collect { case param: OBPFromDate => param }.headOption
    val toDate: Option[OBPToDate] = queryParams.collect { case param: OBPToDate => param }.headOption

    val query: DBObject = {
      val queryWithOptionalFromDate = fromDate.map{
          date => {
            transactionsForAccount
            .put("obp_transaction.details.completed")
            .greaterThanEquals(date.value)
          }
        }.getOrElse(transactionsForAccount)

      val queryWithOptionalFromDateAndToDate = toDate.map{
          date => {
            queryWithOptionalFromDate
            .put("obp_transaction.details.completed")
            .lessThanEquals(date.value)
          }
        }.getOrElse(queryWithOptionalFromDate)

      queryWithOptionalFromDateAndToDate.get
    }

    val defaultSortField = "obp_transaction.details.completed"
    val orderingParams = queryParams
      .collect { case param: OBPOrdering => param}
      .headOption

    val ordering: Option[DBObject] =
      orderingParams.map{
        o => {
          QueryBuilder
          .start(defaultSortField)
          .is(o.order.orderValue)
          .get
        }
      }

    ordering match {
      case Some(o) =>{
        OBPEnvelope.findAll(query, o, limitAndOffset: _*)
      }
      case _ =>{
        OBPEnvelope.findAll(query, limitAndOffset: _*)
      }
    }
  }
}

object Account extends Account with MongoMetaRecord[Account] {
  def toBankAccount(account: Account): BankAccount = {
    val iban = if (account.iban.toString.isEmpty) None else Some(account.iban.toString)
    val nationalIdentifier = account.bankID.obj match {
      case Full(b) => b.national_identifier.get
      case _ => ""
    }

    val bankAccount =
      new BankAccount(
        accountId = account.accountId,
        owners= Set(new AccountOwner("", account.holder.toString)),
        accountType = account.kind.toString,
        balance = account.balance.get,
        currency = account.currency.toString,
        name = account.name.get,
        label = account.label.toString,
        //TODO: it is used for the bank national ID when populating Bank json model
        //either we removed if from here or get it from some where else
        nationalIdentifier = nationalIdentifier,
        swift_bic = None,
        iban = iban,
        number = account.number.get,
        bankName = account.bankName,
        bankId = account.bankId
      )
    bankAccount
  }
}

class HostedBank extends MongoRecord[HostedBank] with ObjectIdPk[HostedBank]{
  def meta = HostedBank

  object name extends StringField(this, 255)
  object alias extends StringField(this, 255)
  object logoURL extends StringField(this, 255)
  object website extends StringField(this, 255)
  object email extends StringField(this, 255)
  object permalink extends StringField(this, 255)
  object SWIFT_BIC extends StringField(this, 255)
  object national_identifier extends StringField(this, 255)

  def getAccount(bankAccountId: AccountId) : Box[Account] = {
    Account.find(("permalink" -> bankAccountId.value) ~ ("bankID" -> id.is)) ?~ {"account " + bankAccountId +" not found at bank " + permalink}
  }

  def isAccount(bankAccountId : AccountId) : Boolean =
    Account.count(("permalink" -> bankAccountId.value) ~ ("bankID" -> id.is)) == 1

}

//TODO: enforce unique permalink (easier if we upgrade to Lift 2.6)
object HostedBank extends HostedBank with MongoMetaRecord[HostedBank] {

  def find(bankId : BankId) : Box[HostedBank] = find("permalink" -> bankId.value)

}
