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
    
 */
package code.model.dataAccess

import net.liftweb.mongodb._
import net.liftweb.record.MandatoryTypedField
import net.liftweb.mongodb.record.field._
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, BsonMetaRecord, BsonRecord}
import net.liftweb.common.{Box, Full, Empty, Failure}
import java.util.Calendar
import java.text.SimpleDateFormat
import net.liftweb.json.DefaultFormats
import java.util.Date
import net.liftweb.record.field.{StringField}
import net.liftweb.json.JsonAST._
import net.liftweb.mongodb.record.{MongoId}
import net.liftweb.mongodb.record.field.{MongoJsonObjectListField, MongoRefField, ObjectIdRefField}
import scala.util.Random
import com.mongodb.QueryBuilder
import com.mongodb.BasicDBObject

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
class OBPEnvelope private() extends MongoRecord[OBPEnvelope] with ObjectIdPk[OBPEnvelope] {
  def meta = OBPEnvelope

  /**
   * Add a user generated comment to the transaction. Saves the db model when called.
   * 
   * @param email The email address of the person posting the comment
   * @param text The text of the comment
   */
  def addComment(email: String, text: String) = {
    println("adding : "+ email + " "+ text)
    val comments = obp_comments.get
    val c2 = comments ++ List(OBPComment.createRecord.email(email).text(text))
    obp_comments(c2).saveTheRecord()
  }

  lazy val theAccount = {
    val thisAcc = obp_transaction.get.this_account.get
    val num = thisAcc.number.get
    val accKind = thisAcc.kind.get
    val bankName = thisAcc.bank.get.name.get
    val qry = QueryBuilder.start("number").is(num).
      put("kind").is(accKind).
      put("bankName").is(bankName).get
    Account.find(qry)
  }
   
  object DateDescending extends Ordering[OBPEnvelope] {
    def compare(e1: OBPEnvelope, e2: OBPEnvelope) = {
      val date1 = e1.obp_transaction.get.details.get.completed.get
      val date2 = e2.obp_transaction.get.details.get.completed.get
      date1.compareTo(date2)
    }
  }
  
  def orderByDateDescending = (e1: OBPEnvelope, e2: OBPEnvelope) => {
    val date1 = e1.obp_transaction.get.details.get.completed.get
    val date2 = e2.obp_transaction.get.details.get.completed.get
    date1.after(date2)
  }
  
  // This creates a json attribute called "obp_transaction"
  object obp_transaction extends BsonRecordField(this, OBPTransaction)
  
  //not named comments as "comments" was used in an older mongo document version
  object obp_comments extends BsonRecordListField[OBPEnvelope, OBPComment](this, OBPComment)
  object narrative extends StringField(this, 255)
  
  def mediated_obpComments(user: String) : Box[List[OBPComment]] = {
    user match{
      case "our-network" => Full(obp_comments.get)
      case "team" => Full(obp_comments.get)
      case "board" => Full(obp_comments.get)
      case "authorities" => Full(obp_comments.get)
      case "my-view" => Full(obp_comments.get)
      case _ => Empty
    }
  }
  
  def mediated_narrative(user: String) : Box[String] = {
    user match{
      case "our-network" => Full(narrative.get)
      case "team" => Full(narrative.get)
      case "board" => Full(narrative.get)
      case "authorities" => Full(narrative.get)
      case "my-view" => Full(narrative.get)
      case _ => Full(narrative.get)
    } 
  }
  

  def asMediatedJValue(user: String) : JObject  = {
    JObject(List(JField("obp_transaction", obp_transaction.get.asMediatedJValue(user,id.toString, theAccount)),
        		 JField("obp_comments", JArray(obp_comments.get.map(comment => {
        		   JObject(List(JField("email", JString(comment.email.is)), JField("text", JString(comment.text.is))))
        		 })))))
  }
}

class OBPComment private() extends BsonRecord[OBPComment] {
  def meta = OBPComment
  
  object email extends StringField(this, 255)
  object text extends StringField(this, 255)
}

object OBPComment extends OBPComment with BsonMetaRecord[OBPComment]

object OBPEnvelope extends OBPEnvelope with MongoMetaRecord[OBPEnvelope] {
  
  class OBPQueryParam
  trait OBPOrder { def orderValue : Int }
  object OBPOrder {
    def apply(s: Option[String]): OBPOrder = s match {
      case Some("asc") => OBPAscending
      case _ => OBPDescending
    }
  }
  object OBPAscending extends OBPOrder { def orderValue = 1 }
  object OBPDescending extends OBPOrder { def orderValue = -1}
  case class OBPLimit(value: Int) extends OBPQueryParam
  case class OBPOffset(value: Int) extends OBPQueryParam
  case class OBPFromDate(value: Date) extends OBPQueryParam
  case class OBPToDate(value: Date) extends OBPQueryParam
  case class OBPOrdering(field: String, order: OBPOrder) extends OBPQueryParam
  
  override def fromJValue(jval: JValue) = {

    def createAliases(env: OBPEnvelope) = {
      val realOtherAccHolder = env.obp_transaction.get.other_account.get.holder.get

      def publicAliasExists(realValue: String): Boolean = {
        env.theAccount match {
          case Full(a) => {
            val otherAccs = a.otherAccounts.get
            val aliasInQuestion = otherAccs.find(o =>
              o.holder.get.equals(realValue))
            aliasInQuestion.isDefined
          }
          case _ => false
        }
      }

      def privateAliasExists(realValue: String): Boolean = {
        env.theAccount match {
          case Full(a) => {
            val otherAccs = a.otherAccounts.get
            val aliasInQuestion = otherAccs.find(o =>
              o.holder.get.equals(realValue))
            aliasInQuestion.isDefined
          }
          case _ => false
        }
      }

      def createPublicAlias() = {
        //TODO: Guarantee a unique public alias string

        /**
         * Generates a new alias name that is guaranteed not to collide with any existing public alias names
         * for the account in question
         */
        def newPublicAliasName(account: Account): String = {
          val newAlias = "ALIAS_" + Random.nextLong().toString.take(6)

          /**
           * Returns true if @publicAlias is already the name of a public alias within @account
           */
          def isDuplicate(publicAlias: String, account: Account) = {
            account.otherAccounts.get.find(oAcc => {
              oAcc.publicAlias.get == newAlias
            }).isDefined
          }

          /**
           * Appends things to @publicAlias until it a unique public alias name within @account
           */
          def appendUntilUnique(publicAlias: String, account: Account): String = {
            val newAlias = publicAlias + Random.nextLong().toString.take(1)
            if (isDuplicate(newAlias, account)) appendUntilUnique(newAlias, account)
            else newAlias
          }

          if (isDuplicate(newAlias, account)) appendUntilUnique(newAlias, account)
          else newAlias
        }

        env.theAccount match {
          case Full(a) => {
            val randomAliasName = newPublicAliasName(a)
            val oAccHolderName = env.obp_transaction.get.other_account.get.holder.get
            val otherAccount = a.otherAccounts.get.find(acc => acc.holder.equals(oAccHolderName))
            val updatedAccount = otherAccount match {
              case Some(o) => {
                //update the "otherAccount"
                val newOtherAcc = o.publicAlias(randomAliasName)
                a.otherAccounts(a.otherAccounts.get -- List(o) ++ List(newOtherAcc))
              }
              case _ => {
                //create a new "otherAccount"
                a.otherAccounts(a.otherAccounts.get ++ List(OtherAccount.createRecord.holder(oAccHolderName).publicAlias(randomAliasName)))
              }
            }

            updatedAccount.saveTheRecord()
            Full(randomAliasName)
          }
          case _ => Empty
        }
      }

      def createPlaceholderPrivateAlias() = {
        env.theAccount match {
          case Full(a) => {
            val oAccHolderName = env.obp_transaction.get.other_account.get.holder.get
            val otherAccount = a.otherAccounts.get.find(acc => acc.holder.equals(oAccHolderName))
            val updatedAccount = otherAccount match {
              case Some(o) => {
                //update the "otherAccount"
                val newOtherAcc = o.privateAlias("")
                a.otherAccounts(a.otherAccounts.get -- List(o) ++ List(newOtherAcc))
              }
              case _ => {
                //create a new "otherAccount"
                a.otherAccounts(a.otherAccounts.get ++ List(OtherAccount.createRecord.holder(oAccHolderName)))
              }
            }
            updatedAccount.saveTheRecord()
            Full("")
          }
          case _ => Empty
        }
      }
      
      
      if (!publicAliasExists(realOtherAccHolder)) {
        createPublicAlias()
      }
      if (!privateAliasExists(realOtherAccHolder)) createPlaceholderPrivateAlias()
    }
    
    val created = super.fromJValue(jval)
    created match {
      case Full(c) => createAliases(c)
      case _ => //don't create anything
    }
    created
  }
  
}


class OBPTransaction private() extends BsonRecord[OBPTransaction]{
  def meta = OBPTransaction
  
  object this_account extends BsonRecordField(this, OBPAccount)
  object other_account extends BsonRecordField(this, OBPAccount)
  object details extends BsonRecordField(this, OBPDetails)
  
  def asMediatedJValue(user: String, envelopeId : String, theAccount: Option[Account]) : JObject  = {
    JObject(List(JField("obp_transaction_uuid", JString(envelopeId)),
    			 JField("this_account", this_account.get.asMediatedJValue(user, None)),
        		 JField("other_account", other_account.get.asMediatedJValue(user, theAccount)),
        		 JField("details", details.get.asMediatedJValue(user))))
  }
}

object OBPTransaction extends OBPTransaction with BsonMetaRecord[OBPTransaction]

class OBPAccount private() extends BsonRecord[OBPAccount]{
  def meta = OBPAccount

  object holder extends StringField(this, 255)
  object number extends StringField(this, 255)
  object kind extends StringField(this, 255)
  object bank extends BsonRecordField(this, OBPBank)
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_holder(user: String, mediator: Account) : (Box[String], Box[OBPAccount.AnAlias]) = {
    val theHolder = holder.get
    
    def usePrivateAliasIfExists() : (Box[String], Box[OBPAccount.AnAlias])= {
      val privateAlias = for{
        otheracc <- mediator.otherAccounts.get.find(o => 
          o.holder.get.equals(theHolder)
        )
      } yield otheracc.privateAlias.get
      
      privateAlias match{
        case Some("") => (Full(theHolder), Empty)
        case Some(a) => (Full(a), Full(OBPAccount.APrivateAlias))
        case _ => (Full(theHolder), Empty)
      }
    }
    
    def usePublicAlias() : (Box[String], Box[OBPAccount.AnAlias])= {
      val publicAlias = for{
        otheracc <- mediator.otherAccounts.get.find(o => 
        	o.holder.get.equals(theHolder)
        )
      } yield otheracc.publicAlias.get
      
      publicAlias match{
        case Some("") => (Full(theHolder), Empty)
        case Some(a) => (Full(a), Full(OBPAccount.APublicAlias))
        case _ => {
            //No alias found, so don't use one
            (Full(theHolder), Empty) 
          }
      }
    }
    
    user match{
      case "team" => (Full(theHolder), Empty)
      case "board" => (Full(theHolder), Empty)
      case "authorities" => (Full(theHolder), Empty)
      case "my-view" => (Full(theHolder), Empty)
      case "our-network" => usePrivateAliasIfExists
      case _ => usePublicAlias
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_number(user: String) : Box[String] = {
    user match{
      case "team" => Full(number.get)
      case "board" => Full(number.get)
      case "authorities" => Full(number.get)
      case "my-view" => Full(number.get)
      case _ => Empty
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_kind(user: String) : Box[String] = {
    user match{
      case "team" => Full(kind.get)
      case "board" => Full(kind.get)
      case "authorities" => Full(kind.get)
      case "my-view" => Full(kind.get)
      case _ => Empty
    }
  }
  /**
   * @param moderatingAccount a temporary way to provide the obp account whose aliases should
   *  be used when displaying this account
   */
  def asMediatedJValue(user: String, moderatingAccount: Option[Account]) : JObject = {
    
    def rawData = {
      JObject(List(JField("holder",
        JObject(List(
          JField("holder", JString(holder.get)),
          JField("alias", JString("no"))))),
        JField("number", JString(number.get)),
        JField("kind", JString(kind.get)),
        JField("bank", bank.get.asMediatedJValue(user))))
    }

    def moderate(moderator: Account) = {
      val h = mediated_holder(user, moderator)
      JObject(List(JField("holder",
        JObject(List(
          JField("holder", JString(h._1.getOrElse(""))),
          JField("alias", JString(h._2 match {
            case Full(OBPAccount.APublicAlias) => "public"
            case Full(OBPAccount.APrivateAlias) => "private"
            case _ => "no"
          }))))),
        JField("number", JString(mediated_number(user) getOrElse "")),
        JField("kind", JString(mediated_kind(user) getOrElse "")),
        JField("bank", bank.get.asMediatedJValue(user))))
    }
        		  
    moderatingAccount match {
      case Some(m) => moderate(m)
      case _ => rawData
    }
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

  //TODO: Access levels are currently the same across all transactions
  def mediated_IBAN(user: String) : Box[String] = {
    user match{
      case "team" => Full(IBAN.get)
      case "board" => Full(IBAN.get)
      case "authorities" => Full(IBAN.get)
      case "my-view" => Full(IBAN.get)
      case _ => Empty
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_national_identifier(user: String) : Box[String] = {
    user match{
      case "team" => Full(national_identifier.get)
      case "board" => Full(national_identifier.get)
      case "authorities" => Full(national_identifier.get)
      case "my-view" => Full(national_identifier.get)
      case _ => Empty
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_name(user: String) : Box[String] = {
    user match{
      case "team" => Full(name.get)
      case "board" => Full(name.get)
      case "authorities" => Full(name.get)
      case "my-view" => Full(name.get)
      case _ => Empty
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("IBAN", JString(mediated_IBAN(user) getOrElse "")),
        		  JField("national_identifier", JString(mediated_national_identifier(user) getOrElse "")),
        		  JField("name", JString(mediated_name(user) getOrElse ""))))
  }
}

object OBPBank extends OBPBank with BsonMetaRecord[OBPBank]



class OBPDetails private() extends BsonRecord[OBPDetails]{
  def meta = OBPDetails

  object type_en extends net.liftweb.record.field.StringField(this, 255)
  object type_de extends net.liftweb.record.field.StringField(this, 255)
  object posted extends DateField(this)
  object other_data extends net.liftweb.record.field.StringField(this, 5000)
  object new_balance extends BsonRecordField(this, OBPBalance)
  object value extends BsonRecordField(this, OBPValue)
  object completed extends DateField(this)
  
  
  def formatDate(date : Box[Date]) : String = {
    date match{
      case Full(d) => OBPDetails.formats.dateFormat.format(d)
      case _ => ""
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_type_en(user: String) : Box[String] = {
    user match{
      case _ => Full(type_en.get)
    }
  }
  //TODO: Access levels are currently the same across all transactions
  def mediated_type_de(user: String) : Box[String] = {
    user match{
      case _ => Full(type_de.get)
    }
  }
  //TODO: Access levels are currently the same across all transactions
  def mediated_posted(user: String) : Box[Date] = {
    user match{
      case _ => Full(posted.get)
    }
  }
  //TODO: Access levels are currently the same across all transactions
  def mediated_completed(user: String) : Box[Date] = {
    user match{
      case _ => Full(completed.get)
    }
  }
  //TODO: Access levels are currently the same across all transactions
  def mediated_other_data(user: String) : Box[String] = {
    user match{
      case "team" => Full(other_data.get)
      case "board" => Full(other_data.get)
      case "authorities" => Full(other_data.get)
      case "my-view" => Full(other_data.get)
      case _ => Empty
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("type_en", JString(mediated_type_en(user) getOrElse "")),
        		  JField("type_de", JString(mediated_type_de(user) getOrElse "")),
        		  JField("posted", JString(formatDate(mediated_posted(user)))),
        		  JField("completed", JString(formatDate(mediated_completed(user)))),
        		  JField("other_data", JString(mediated_other_data(user) getOrElse "")),
        		  JField("new_balance", new_balance.get.asMediatedJValue(user)),
        		  JField("value", value.get.asMediatedJValue(user))))
  }
}

object OBPDetails extends OBPDetails with BsonMetaRecord[OBPDetails]


class OBPBalance private() extends BsonRecord[OBPBalance]{
  def meta = OBPBalance

  object currency extends net.liftweb.record.field.StringField(this, 5)
  object amount extends net.liftweb.record.field.DecimalField(this, 0) // ok to use decimal?

  //TODO: Access levels are currently the same across all transactions
  def mediated_currency(user: String) : Box[String] = {
    user match{
      case "our-network" => Full(currency.get)
      case "team" => Full(currency.get)
      case "board" => Full(currency.get)
      case "authorities" => Full(currency.get)
      case "my-view" => Full(currency.get)
      case _ => Empty
    }
  }
  //TODO: Access levels are currently the same across all transactions
  /**
   * TODO: This should probably return an actual decimal rather than a string -E.S.
   */
  def mediated_amount(user: String) : Box[String] = {
    user match{
      case "our-network" => Full(amount.get.toString)
      case "team" => Full(amount.get.toString)
      case "board" => Full(amount.get.toString)
      case "authorities" => Full(amount.get.toString)
      case "my-view" => Full(amount.get.toString)
      case _ => {
        if(amount.get.toString.startsWith("-")) Full("-") else Full("+")
      }
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("currency", JString(mediated_currency(user) getOrElse "")),
        		  JField("amount", JString(mediated_amount(user) getOrElse ""))))
  }
}

object OBPBalance extends OBPBalance with BsonMetaRecord[OBPBalance]

class OBPValue private() extends BsonRecord[OBPValue]{
  def meta = OBPValue

  object currency extends net.liftweb.record.field.StringField(this, 5)
  object amount extends net.liftweb.record.field.DecimalField(this, 0) // ok to use decimal?

  //TODO: Access levels are currently the same across all transactions
  def mediated_currency(user: String) : Box[String] = {
    user match{
      case _ => Full(currency.get.toString)
    }
  }
  //TODO: Access levels are currently the same across all transactions
  /**
   * TODO: This should probably return an actual decimal rather than a string -E.S.
   */
  def mediated_amount(user: String) : Box[String] = {
    user match{
      case _ => Full(amount.get.toString)
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("currency", JString(mediated_currency(user) getOrElse "")),
        		  JField("amount", JString(mediated_amount(user) getOrElse ""))))
  }
}

object OBPValue extends OBPValue with BsonMetaRecord[OBPValue]


