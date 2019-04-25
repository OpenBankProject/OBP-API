/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.model.dataAccess

import java.util.Date

import code.api.util._
import code.model._
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.mongodb.QueryBuilder
import com.openbankproject.commons.model._
import net.liftweb.common._
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk, ObjectIdRefField}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.{Limit, Skip}
import net.liftweb.record.field.{DecimalField, StringField}

import scala.collection.immutable.List

class Account extends BankAccount with MongoRecord[Account] with ObjectIdPk[Account] with MdcLoggable{
  def meta = Account

  object accountBalance extends DecimalField(this, 0) {
    //this is the legacy db field name
    override def name = "balance"
  }
  object holder extends StringField(this, 255)
  object accountNumber extends StringField(this, 255){
    //this is the legacy db field name
    override def name = "number"
  }
  object kind extends StringField(this, 255)

  // object productCode extends StringField(this, 255)

  object accountName extends StringField(this, 255){
    //this is the legacy db field name
    override def name = "name"
  }
  object permalink extends StringField(this, 255)
  object bankID extends ObjectIdRefField(this, HostedBank)
  object accountLabel extends StringField(this, 255){
    //this is the legacy db field name
    override def name = "label"
  }
  object accountCurrency extends StringField(this, 255){
    //this is the legacy db field name
    override def name = "currency"
  }
  object accountIban extends StringField(this, 255){
    //this is the legacy db field name
    override def name = "iban"
  }
  object accountLastUpdate extends DateField(this)
  object mAccountRoutingScheme extends StringField(this, 255)
  object mAccountRoutingAddress extends StringField(this, 255)
  object mBranchId extends StringField(this, 255)

  object mAccountRuleScheme1 extends StringField(this, 10)
  object mAccountRuleValue1 extends DecimalField(this, 0)
  object mAccountRuleScheme2 extends StringField(this, 10)
  object mAccountRuleValue2 extends DecimalField(this, 0)
  
  
  def transactionsForAccount: QueryBuilder = {
    QueryBuilder
    .start("obp_transaction.this_account.number")
    .is(accountNumber.get)
    //FIX: change that to use the bank identifier
    .put("obp_transaction.this_account.bank.national_identifier")
    .is(this.nationalIdentifier)
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

  override def bankId: BankId = {
    bankID.obj match  {
      case Full(bank) => BankId(bank.permalink.get)
      case _ => BankId("")
    }
  }
  override def accountId : AccountId = AccountId(permalink.get)
  override def iban: Option[String] = {
    val i = accountIban.get
    if (i.isEmpty) None else Some(i)
  }
  override def currency: String = accountCurrency.get
  override def number: String = accountNumber.get
  override def balance: BigDecimal = accountBalance.get
  override def name: String = accountName.get
  override def accountType: String = kind.get
  override def label: String = accountLabel.get
  override def accountHolder: String = holder.get
  override def lastUpdate: Date = accountLastUpdate.get
  override def accountRoutingScheme: String = mAccountRoutingScheme.get
  override def accountRoutingAddress: String = mAccountRoutingAddress.get
  override def branchId: String = mBranchId.get
  def createAccountRule(scheme: String, value: Long) = {
    scheme match {
      case s: String if s.equalsIgnoreCase("") == false =>
        val v = Helper.smallestCurrencyUnitToBigDecimal(value, accountCurrency.get)
        List(AccountRule(scheme, v.toString()))
      case _ =>
        Nil
    }
  }
  override def accountRoutings: List[AccountRouting] = List(AccountRouting(mAccountRoutingScheme.get, mAccountRoutingAddress.get))
  override def accountRules: List[AccountRule] = createAccountRule(mAccountRuleScheme1.get, mAccountRuleValue1.get.toLong) :::
                                                  createAccountRule(mAccountRuleScheme2.get, mAccountRuleValue2.get.toLong)
}

object Account extends Account with MongoMetaRecord[Account] {
  def init = createIndex((permalink.name -> 1) ~ (bankID.name -> 1), true)
}

class HostedBank extends Bank with MongoRecord[HostedBank] with ObjectIdPk[HostedBank]{
  def meta = HostedBank

  object name extends StringField(this, 255)
  object alias extends StringField(this, 255)
  object logoURL extends StringField(this, 255)
  object website extends StringField(this, 255)
  object email extends StringField(this, 255)
  object permalink extends StringField(this, 255)
  object swiftBIC extends StringField(this, 255)
  object national_identifier extends StringField(this, 255)
  object mBankRoutingScheme extends StringField(this, 255)
  object mBankRoutingAddress extends StringField(this, 255)

  def getAccount(bankAccountId: AccountId) : Box[Account] = {
    Account.find((Account.permalink.name -> bankAccountId.value) ~ (Account.bankID.name -> id.get)) ?~ {"account " + bankAccountId +" not found at bank " + permalink}
  }

  def isAccount(bankAccountId : AccountId) : Boolean =
    Account.count((Account.permalink.name -> bankAccountId.value) ~ (Account.bankID.name -> id.get)) == 1

  override def bankId: BankId = BankId(permalink.get)
  override def shortName: String = alias.get
  override def fullName: String = name.get
  override def logoUrl: String = logoURL.get
  override def websiteUrl: String = website.get
  override def swiftBic: String = swiftBIC.get
  override def nationalIdentifier: String = national_identifier.get
  override def bankRoutingScheme = mBankRoutingAddress.get
  override def bankRoutingAddress = mBankRoutingAddress.get
}

object HostedBank extends HostedBank with MongoMetaRecord[HostedBank] {

  def init = createIndex((permalink.name -> 1), true)

  def find(bankId : BankId) : Box[HostedBank] = find(HostedBank.permalink.name -> bankId.value)

}
