/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.      

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
    by 
    Simon Redfern : simon AT tesobe DOT com
    Everett Sochowski: everett AT tesobe DOT com
    Benali Ayoub : ayoub AT tesobe DOT com

 */
 
package code.model.dataAccess

import com.mongodb.QueryBuilder
import net.liftweb.mongodb.JsonObjectMeta
import net.liftweb.mongodb.JsonObject
import net.liftweb.mongodb.record.MongoMetaRecord
import net.liftweb.mongodb.record.field.ObjectIdPk
import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.mongodb.record.field.BsonRecordField
import net.liftweb.mongodb.record.field.MongoJsonObjectListField
import net.liftweb.mongodb.record.field.DateField
import net.liftweb.common.{ Box, Empty, Full }
import net.liftweb.mongodb.record.field.{BsonRecordListField,ObjectIdRefField}
import net.liftweb.mongodb.record.{ BsonRecord, BsonMetaRecord }
import net.liftweb.record.field.{ StringField, BooleanField}
import net.liftweb.mongodb.{Limit, Skip}
import code.model.dataAccess.OBPEnvelope._
import code.model.traits.ModeratedTransaction
import code.model.traits.BankAccount
import code.model.implementedTraits.{ BankAccountImpl, AccountOwnerImpl }

/**
 * There should be only one of these for every real life "this" account. TODO: Enforce this
 *
 * As a result, this can provide a single point from which to retrieve the aliases associated with
 * this account, rather than needing to duplicate the aliases into every single transaction.
 */

class Account extends MongoRecord[Account] with ObjectIdPk[Account] {
  def meta = Account

  object anonAccess extends BooleanField(this, false)
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
  object otherAccounts extends BsonRecordListField(this, OtherAccount)

  def bankName : String = bankID.obj match	{
    case Full(bank) => bank.name.get
    case _ => "" 
  }
  def bankPermalink : String  = bankID.obj match	{
    case Full(bank) => bank.permalink.get
    case _ => "" 
  }
  def baseQuery = QueryBuilder.start("obp_transaction.this_account.number").is(number.get).
    put("obp_transaction.this_account.kind").is(kind.get).
    put("obp_transaction.this_account.bank.name").is(bankName)

  //find all the envelopes related to this account 
  def allEnvelopes: List[OBPEnvelope] = OBPEnvelope.findAll(baseQuery.get)

  def envelopes(queryParams: OBPQueryParam*): List[OBPEnvelope] = {
    val DefaultSortField = "obp_transaction.details.completed"
    //This is ugly with the casts but it is a similar approach to mongo's .findAll implementation
    val limit = queryParams.find(q => q.isInstanceOf[OBPLimit]).asInstanceOf[Option[OBPLimit]].map(x => x.value).getOrElse(50)
    val offset = queryParams.find(q => q.isInstanceOf[OBPOffset]).asInstanceOf[Option[OBPOffset]].map(x => x.value).getOrElse(0)
    val orderingParams = queryParams.find(q => q.isInstanceOf[OBPOrdering]).
    						asInstanceOf[Option[OBPOrdering]].map(x => x).
    						getOrElse(OBPOrdering(Some(DefaultSortField), OBPDescending))
    
    val fromDate = queryParams.find(q => q.isInstanceOf[OBPFromDate]).asInstanceOf[Option[OBPFromDate]]
    val toDate = queryParams.find(q => q.isInstanceOf[OBPToDate]).asInstanceOf[Option[OBPToDate]]
    
    val mongoParams = {
      val start = baseQuery
      val start2 = if(fromDate.isDefined) start.put("obp_transaction.details.completed").greaterThanEquals(fromDate.get.value)
      			   else start
      val end = if(toDate.isDefined) start2.put("obp_transaction.details.completed").lessThanEquals(toDate.get.value)
      			else start2
      end.get
    }
    
    val ordering =  QueryBuilder.start(orderingParams.field.getOrElse(DefaultSortField)).is(orderingParams.order.orderValue).get
    
    OBPEnvelope.findAll(mongoParams, ordering, Limit(limit), Skip(offset))
  }

  def getUnmediatedOtherAccountUrl(user: String, otherAccountHolder: String): Box[String] = {
    for {
      o <- otherAccounts.get.find(acc => {
        acc.holder.get.equals(otherAccountHolder)
      })
    } yield o.url.get
  }

  def getMediatedOtherAccountURL(user: String, otherAccountHolder: String): Box[String] = {
    val otherAccountURL = for {
      o <- otherAccounts.get.find(acc => {
        acc.holder.get.equals(otherAccountHolder)
      })
    } yield o.url.get

    user match {
      case "team" => otherAccountURL
      case "board" => otherAccountURL
      case "authorities" => otherAccountURL
      case "my-view" => otherAccountURL
      case "our-network" => otherAccountURL
      case _ => Empty
    }
  }

  def getMediatedOtherAccountImageURL(user: String, otherAccountHolder: String): Box[String] = {
    val otherAccountImageURL = for {
      o <- otherAccounts.get.find(acc => {
        acc.holder.get.equals(otherAccountHolder)
      })
    } yield o.imageUrl.get

    user match {
      case "team" => otherAccountImageURL
      case "board" => otherAccountImageURL
      case "authorities" => otherAccountImageURL
      case "my-view" => otherAccountImageURL
      case "our-network" => otherAccountImageURL
      case _ => Empty
    }
  }

  def getMediatedOtherAccountMoreInfo(user: String, otherAccountHolder: String): Box[String] = {
    val otherAccountMoreInfo = for {
      o <- otherAccounts.get.find(acc => {
        acc.holder.get.equals(otherAccountHolder)
      })
    } yield o.moreInfo.get

    user match {
      case "team" => otherAccountMoreInfo
      case "board" => otherAccountMoreInfo
      case "authorities" => otherAccountMoreInfo
      case "my-view" => otherAccountMoreInfo
      case "our-network" => otherAccountMoreInfo
      case _ => Empty
    }
  }
}

object Account extends Account with MongoMetaRecord[Account] {
  def toBankAccount(account: Account): BankAccount = {
    val iban = if (account.iban.toString.isEmpty) None else Some(account.iban.toString)
    var bankAccount = new BankAccountImpl(account.id.toString, Set(), account.kind.toString, account.currency.toString, account.label.toString,
      "", None, iban, account.anonAccess.get, account.number.get, account.bankName, account.bankPermalink, account.permalink.get)
    val owners = Set(new AccountOwnerImpl("", account.holder.toString, Set(bankAccount)))
    bankAccount.owners = Set(new AccountOwnerImpl("", account.holder.toString, Set(bankAccount)))
    
    bankAccount
  }
}

class OtherAccount private () extends BsonRecord[OtherAccount] {
  def meta = OtherAccount

  object holder extends StringField(this, 200)

  object publicAlias extends StringField(this, 100)
  object privateAlias extends StringField(this, 100)
  object moreInfo extends StringField(this, 100)
  object url extends StringField(this, 100)
  object imageUrl extends StringField(this, 100)
  object openCorporatesUrl extends StringField(this, 100) {
    override def optional_? = true
  }
}

object OtherAccount extends OtherAccount with BsonMetaRecord[OtherAccount]

class HostedBank extends MongoRecord[HostedBank] with ObjectIdPk[HostedBank]{
  def meta = HostedBank

  object name extends StringField(this, 255)
  object alias extends StringField(this, 255)
  object logo extends StringField(this, 255)
  object website extends StringField(this, 255)
  object email extends StringField(this, 255)
  object permalink extends StringField(this, 255)
  object SWIFT_BIC extends StringField(this, 255)
  object national_identifier extends StringField(this, 255)
}

object HostedBank extends HostedBank with MongoMetaRecord[HostedBank]