/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
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

import code.util.Helper
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.record.field._
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, BsonMetaRecord, BsonRecord}
import net.liftweb.common.{Box, Empty, Failure}
import java.util.{UUID, Date}
import net.liftweb.record.field.{StringField,LongField}
import net.liftweb.json.JsonAST._
import code.model._
import code.util.Helper.MdcLoggable
import net.liftweb.record.field.{DoubleField,DecimalField}
import net.liftweb.util.FieldError
import org.bson.types.ObjectId
import scala.xml.Unparsed
import net.liftweb.json.JsonAST.JObject
import scala.Some
import net.liftweb.json.JsonAST.JString
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.JField
import code.metadata.counterparties.MongoCounterparties

/**
 * "Current Account View"
curl -i -H "Content-Type: application/json" -X POST -d '[{
         "obp_transaction":{
            "this_account":{
               "holder":"Music Pictures Limited",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"DE1235123612",
                  "national_identifier":"de.10010010",
                  "name":"Postbank"
               }
            },
            "other_account":{
               "holder":"Client 1",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"UK12222879",
                  "national_identifier":"uk.10010010",
                  "name":"HSBC"
               }
            },
            "details":{
               "type_en":"Transfer",
               "type_de":"Überweisung",
               "posted":{
                  "$dt":"2012-01-04T18:06:22.000Z"
                },
               "completed":{
                  "$dt":"2012-09-04T18:52:13.000Z"
                },
               "new_balance":{
                     "currency":"EUR",
                  "amount":"4323.45"
               },
               "value":{
                  "currency":"EUR",
                  "amount":"123.45"
               },
               "other_data":"9"
            }
         }
 },
{
         "obp_transaction":{
            "this_account":{
               "holder":"Music Pictures Limited",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"DE1235123612",
                  "national_identifier":"de.10010010",
                  "name":"Postbank"
               }
            },
            "other_account":{
               "holder":"Client 2",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"UK22222879",
                  "national_identifier":"uk.10010010",
                  "name":"HSBC"
               }
            },
            "details":{
               "type_en":"Transfer",
               "type_de":"Überweisung",
               "posted":{
                  "$dt":"2012-01-04T14:06:22.000Z"
                },
               "completed":{
                  "$dt":"2012-09-04T14:52:13.000Z"
                },
               "new_balance":{
                     "currency":"EUR",
                  "amount":"2222.45"
               },
               "value":{
                  "currency":"EUR",
                  "amount":"223.45"
               },
               "other_data":"9"
            }
         }
 }]' http://localhost:8080/api/transactions
 */

// Seems to map to a collection of the plural name
class OBPEnvelope private() extends MongoRecord[OBPEnvelope] with ObjectIdPk[OBPEnvelope] with MdcLoggable with TransactionUUID {
  def meta = OBPEnvelope

  def theTransactionId = TransactionId(transactionId.get)
  def theAccountId = theAccount.map(_.accountId).getOrElse(AccountId(""))
  def theBankId = theAccount.map(_.bankId).getOrElse(BankId(""))

  object transactionId extends StringField(this, 100) {
    override def defaultValue = UUID.randomUUID.toString
  }

  // This creates a json attribute called "obp_transaction"
  object obp_transaction extends BsonRecordField(this, OBPTransaction)

  override def validate: List[FieldError] =
    obp_transaction.get.validate ++
    super.validate

  object DateDescending extends Ordering[OBPEnvelope] {
    def compare(e1: OBPEnvelope, e2: OBPEnvelope) = {
      val date1 = e1.obp_transaction.get.details.get.completed.get
      val date2 = e2.obp_transaction.get.details.get.completed.get
      date1.compareTo(date2)
    }
  }

  lazy val theAccount: Box[Account] = {
    import net.liftweb.mongodb.BsonDSL._
    val thisAcc = obp_transaction.get.this_account.get
    val num = thisAcc.number.get
    val bankNationalIdentifier = thisAcc.bank.get.national_identifier.get
    for {
      bank <- HostedBank.find((HostedBank.national_identifier.name -> bankNationalIdentifier))
      bankMongoId : ObjectId = bank.id.get
      account <- Account.find((Account.accountNumber.name -> num) ~ (Account.bankID.name -> bankMongoId))
    } yield account
  }

  def orderByDateDescending = (e1: OBPEnvelope, e2: OBPEnvelope) => {
    val date1 = e1.obp_transaction.get.details.get.completed.get
    val date2 = e2.obp_transaction.get.details.get.completed.get
    date1.after(date2)
  }

}

object OBPEnvelope extends OBPEnvelope with MongoMetaRecord[OBPEnvelope] with MdcLoggable {

  def envelopesFromJValue(jval: JValue) : Box[OBPEnvelope] = {
    val createdBox = fromJValue(jval)

    createdBox match {
      case Full(created) =>
        val errors = created.validate
        if(errors.isEmpty) {
          Full(created)
        } else {
          logger.warn("could not create a obp envelope.errors: ")
          logger.warn(errors)
          Empty
        }
      case Failure(msg, _, _) =>
        Failure(s"could not create Envelope from JValue: $msg")
      case _ =>
        Failure(s"could not create Envelope from JValue")
    }
  }
}


class OBPTransaction private() extends BsonRecord[OBPTransaction]{
  def meta = OBPTransaction

  object this_account extends BsonRecordField(this, OBPAccount)
  object other_account extends BsonRecordField(this, OBPAccount)
  object details extends BsonRecordField(this, OBPDetails)

  private def validateThisAccount: List[FieldError] = {
    val accountNumber = this_account.get.number
    val bankId = this_account.get.bank.get.national_identifier
    val accountNumberError =
      if(accountNumber.get.isEmpty)
        Some(new FieldError(accountNumber, Unparsed("this bank account number is empty")))
      else
        None
    val bankIdError =
      if(bankId.get.isEmpty)
        Some(new FieldError(bankId, Unparsed("this bank number is empty")))
      else
        None
    List(accountNumberError, bankIdError).flatten
  }

  override def validate: List[FieldError] =
    validateThisAccount ++
    this_account.get.validate ++
    other_account.get.validate ++
    details.get.validate ++
    super.validate

  @deprecated(Helper.deprecatedJsonGenerationMessage, null)
  def whenAddedJson(envelopeId : String) : JObject  = {
    JObject(List(JField("obp_transaction_uuid", JString(envelopeId)),
           JField("this_account", this_account.get.whenAddedJson),
             JField("other_account", other_account.get.whenAddedJson),
             JField("details", details.get.whenAddedJson)))
  }
}

object OBPTransaction extends OBPTransaction with BsonMetaRecord[OBPTransaction]

class OBPAccount private() extends BsonRecord[OBPAccount]{
  def meta = OBPAccount

  object holder extends StringField(this, 255){
    override def required_? = false
    override def optional_? = true
  }

  object number extends StringField(this, 255){
    override def required_? = false
    override def optional_? = true
  }
  object kind extends StringField(this, 255){
    override def required_? = false
    override def optional_? = true
  }
  object bank extends BsonRecordField(this, OBPBank)

  override def validate: List[FieldError] =
    holder.validate ++
    number.validate ++
    kind.validate ++
    bank.validate ++
    super.validate

  /**
   * @param moderatingAccount a temporary way to provide the obp account whose aliases should
   *  be used when displaying this account
   */
  def whenAddedJson: JObject = {

    JObject(List(JField("holder",
      JObject(List(
        JField("holder", JString(holder.get)),
        JField("alias", JString("no"))))),
      JField("number", JString(number.get)),
      JField("kind", JString(kind.get)),
      JField("bank", bank.get.whenAddedJson)))
  }
}

object OBPAccount extends OBPAccount with BsonMetaRecord[OBPAccount]{
  sealed abstract class AnAlias
  case object APublicAlias extends AnAlias
  case object APrivateAlias extends AnAlias
}

class OBPBank private() extends BsonRecord[OBPBank]{
  def meta = OBPBank

  object IBAN extends net.liftweb.record.field.StringField(this, 255)
  object national_identifier extends net.liftweb.record.field.StringField(this, 255)
  object name extends net.liftweb.record.field.StringField(this, 255)

  override def validate: List[FieldError] =
    IBAN.validate ++
    national_identifier.validate ++
    name.validate ++
    super.validate


  def whenAddedJson : JObject = {
    JObject(List( JField("IBAN", JString(IBAN.get)),
              JField("national_identifier", JString(national_identifier.get)),
              JField("name", JString(name.get))))
  }
}

object OBPBank extends OBPBank with BsonMetaRecord[OBPBank]



class OBPDetails private() extends BsonRecord[OBPDetails]{
  def meta = OBPDetails

  object kind extends net.liftweb.record.field.StringField(this, 255){
    override def required_? = false
    override def optional_? = true
  }
  object posted extends DateField(this)
  object other_data extends net.liftweb.record.field.StringField(this, 5000){
    override def required_? = false
    override def optional_? = true
  }
  object new_balance extends BsonRecordField(this, OBPBalance)
  object value extends BsonRecordField(this, OBPValue)
  object completed extends DateField(this)
  object label extends net.liftweb.record.field.StringField(this, 255){
    override def required_? = false
    override def optional_? = true
  }

  override def validate: List[FieldError] =
    kind.validate ++
    posted.validate ++
    other_data.validate ++
    new_balance.validate ++
    value.validate ++
    completed.validate ++
    label.validate ++
    super.validate


  def formatDate(date : Date) : String = {
    OBPDetails.formats.dateFormat.format(date)
  }

  def whenAddedJson : JObject = {
    JObject(List( JField("type_en", JString(kind.get)),
              JField("type", JString(kind.get)),
              JField("posted", JString(formatDate(posted.get))),
              JField("completed", JString(formatDate(completed.get))),
              JField("other_data", JString(other_data.get)),
              JField("new_balance", new_balance.get.whenAddedJson),
              JField("value", value.get.whenAddedJson)))
  }
}

object OBPDetails extends OBPDetails with BsonMetaRecord[OBPDetails]


class OBPBalance private() extends BsonRecord[OBPBalance]{
  def meta = OBPBalance

  object currency extends StringField(this, 5)
  object amount extends DecimalField(this, 0) // ok to use decimal?

  override def validate: List[FieldError] =
    currency.validate ++
    amount.validate ++
    super.validate

  def whenAddedJson : JObject = {
    JObject(List( JField("currency", JString(currency.get)),
              JField("amount", JString(amount.get.toString))))
  }
}

object OBPBalance extends OBPBalance with BsonMetaRecord[OBPBalance]

class OBPValue private() extends BsonRecord[OBPValue]{
  def meta = OBPValue

  object currency extends net.liftweb.record.field.StringField(this, 5)
  object amount extends net.liftweb.record.field.DecimalField(this, 0) // ok to use decimal?

  def whenAddedJson : JObject = {
    JObject(List( JField("currency", JString(currency.get)),
              JField("amount", JString(amount.get.toString))))
  }
}

object OBPValue extends OBPValue with BsonMetaRecord[OBPValue]