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
package code.model

import net.liftweb.mongodb._
import net.liftweb.record.MandatoryTypedField
import net.liftweb.mongodb.record.field.{BsonRecordField, ObjectIdPk, DateField, MongoListField}
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

class Location private () extends BsonRecord[Location] {
  def meta = Location

  object longitude extends net.liftweb.record.field.IntField(this)
  object latitude extends net.liftweb.record.field.IntField(this)

}
object Location extends Location with BsonMetaRecord[Location]

/*

"World View":
[
   {
      "obp_transaction":{
         "from_account":{
            "holder":"Music Pictures Limited",
            "number":"123567",
            "type":"current",
            "bank":{
               "IBAN":"DE1235123612",
               "national_identifier":"de.10010010",
               "name":"Postbank"
            }
         },
         "to_account":{
            "holder":"Simon Redfern",
            "number":"3225446882",
            "type":"current",
            "bank":{
               "IBAN":"UK12789879",
               "national_identifier":"uk.10010010",
               "name":"HSBC"
            }
         },
         "details":{
            "type_en":"Transfer",
            "type_de":"Überweisung",
            "posted":"ISODate 2011-11-25T10:28:38.273Z",
            "completed":"ISODate 2011-11-26T10:28:38.273Z",
            "value":{
               "currency":"EUR",
               "amount":"354.99"
            },
            "other_data":"9Z65HCF/0723203600/68550030\nAU 100467978\nKD-Nr2767322"
         }
      }
   },
   {
         "obp_transaction":{
            "from_account":{
               "holder":"Client 1",
               "number":"123567",
               "kind":"current",
               "bank":{
                  "IBAN":"UK12222879",
                  "national_identifier":"de.10010010",
                  "name":"Postbank"
               }
            },
            "to_account":{
               "holder":"Music Pictures Limited",
               "number":"3225446882",
               "kind":"current",
               "bank":{
                  "IBAN":"UK12789879",
                  "national_identifier":"uk.10010010",
                  "name":"HSBC"
               }
            },
            "details":{
               "type_en":"Transfer",
               "type_de":"Überweisung",
               "posted":"ISODate 2011-11-25T10:28:38.273Z",
               "completed":"ISODate 2011-11-26T10:28:38.273Z",
               "value":{
                  "currency":"EUR",
                  "amount":"123.45"
               },
               "other_data":"9Z65HCF/0723203600/68550030\nAU 100467978\nKD-Nr2767322"
            }
         }
      }
]


 */


/**
 * "Current Account View"
curl -i -H "Content-Type: application/json" -X POST -d '{
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
            "holder":"Simon Redfern",
            "number":"3225446882",
            "kind":"current",
            "bank":{
               "IBAN":"UK12789879",
               "national_identifier":"uk.10010010",
               "name":"HSBC"
            }
         },
         "details":{
            "type_en":"Transfer",
            "type_de":"Überweisung",
            "posted":"ISODate 2011-11-25T10:28:38.273Z",
            "completed":"ISODate 2011-11-26T10:28:38.273Z",
            "new_balance":{
               "currency":"EUR",
               "amount":"-354.99"
            },
            "value":{
               "currency":"EUR",
               "amount":"-354.99"
            },
            "other_data":"9Z65HCF/0723203600/68550030\nAU 100467978\nKD-Nr2767322"
         }
      }
 }' http://localhost:8080/api/transactions
 */

// Seems to map to a collection of the plural name
class OBPEnvelope private() extends MongoRecord[OBPEnvelope] with ObjectIdPk[OBPEnvelope] {
  def meta = OBPEnvelope

  // This creates a json attribute called "obp_transaction"
  object obp_transaction extends BsonRecordField(this, OBPTransaction)
  
  //TODO: We might want to move where comments are stored
  object comments extends MongoListField[OBPEnvelope, String](this)
  
  def mediated_comments(user: String) : Box[List[String]] = {
    
    user match{
      case "our-network" => Full(comments.get)
      case "team" => Full(comments.get)
      case "board" => Full(comments.get)
      case "authorities" => Full(comments.get)
      case _ => Empty
    }
  }
  
  def asMediatedJValue(user: String) : JObject  = {
    JObject(List(JField("obp_transaction", obp_transaction.get.asMediatedJValue(user)),
        		 JField("comments", JArray(comments.get.map(JString(_))))))
  }
}

object OBPEnvelope extends OBPEnvelope with MongoMetaRecord[OBPEnvelope]


class OBPTransaction private() extends BsonRecord[OBPTransaction]{
  def meta = OBPTransaction // what does meta do?
  
  object this_account extends BsonRecordField(this, OBPAccount)
  object other_account extends BsonRecordField(this, OBPAccount)
  object details extends BsonRecordField(this, OBPDetails)
  
  def asMediatedJValue(user: String) : JObject  = {
    JObject(List(JField("this_account", this_account.get.asMediatedJValue(user)),
        		 JField("other_account", other_account.get.asMediatedJValue(user)),
        		 JField("details", details.get.asMediatedJValue(user))))
  }
}

object OBPTransaction extends OBPTransaction with BsonMetaRecord[OBPTransaction]


///

/**
 * There should be only one of these for every real life "this" account. TODO: Enforce this
 * 
 * As a result, this can provide a single point from which to retrieve the aliases associated with
 * this account, rather than needing to duplicate the aliases into every single transaction.
 */
class Account extends MongoRecord[Account] with ObjectIdPk[Account]{
 def meta = Account 
 
  protected object holder extends net.liftweb.record.field.StringField(this, 255)
  protected object number extends net.liftweb.record.field.StringField(this, 255)
  protected object kind extends net.liftweb.record.field.StringField(this, 255)
  protected object bank extends BsonRecordField(this, OBPBank)
  object privateAliases extends MongoJsonObjectListField[Account, Alias](this, Alias)
  object publicAliases extends MongoJsonObjectListField[Account, Alias](this, Alias)
  
}

object Account extends Account with MongoMetaRecord[Account]

//TODO: This should somehow be unique
case class Alias(realValue: String, aliasValue: String) extends JsonObject[Alias]{
  def meta = Alias
}

object Alias extends JsonObjectMeta[Alias]

class OBPAccount private() extends BsonRecord[OBPAccount]{
  def meta = OBPAccount

  protected object holder extends StringField(this, 255){
    override def setFromString(s: String) = {
      val v = super.setFromString(s)
      //once again, a temporary measure
      if(!s.equals("Music Pictures Limited")){
        if(!publicAliasExists(s)) {
          if(isACompany(s)){
            createPlaceholderPublicAlias()
          }else{
            createPublicAlias()
          }
        }
        if(!privateAliasExists(s)) createPlaceholderPrivateAlias()
      }
      v
    }
  }
  protected object number extends StringField(this, 255)
  protected object kind extends StringField(this, 255)
  object bank extends BsonRecordField(this, OBPBank)
  
  def theAccount = {
    //TODO: Allow creation of more than just the Music Pictures account
    val accJObj = JObject(List(JField("holder", JString("Music Pictures Limited"))))
    Account.find(accJObj) match{
	    case Full(a) => Full(a)
	    case _ => {
	      val newAccount = Account.createRecord
	      newAccount.setFieldsFromJValue(JObject(List(JField("holder", JString("Music Pictures Limited")))))
	      newAccount.saveTheRecord()
	    }
    }
  }
  
  def publicAliasExists(realValue : String) : Boolean = {
    val acc = theAccount
    acc match{
      case Full(a) =>{
        val publicAliases = a.publicAliases.get
        val aliasInQuestion = publicAliases.find(alias =>
          alias match{
            case Alias(`realValue`, _) => true
            case _ => false
          })
       aliasInQuestion.isDefined
      }
      case _ => false
    }
  }
  
  def privateAliasExists(realValue : String) : Boolean = {
    val acc = theAccount
    acc match{
      case Full(a) =>{
        val privateAliases = a.privateAliases.get
        val aliasInQuestion = privateAliases.find(alias =>
          alias match{
            case Alias(`realValue`, _) => true
            case _ => false
          })
       aliasInQuestion.isDefined
      }
      case _ => false
    }
  }
  
  //For now, if it's all upper case, treat it as a company
  def isACompany(holder: String) = {
    holder.equals(holder.toUpperCase())
  }
  
  def createPublicAlias() = {
        val randomAliasName = "ALIAS_" + Random.nextLong().toString.take(6)
            theAccount match {
              case Full(a) => {
                val updatedAccount = a.publicAliases(a.publicAliases.get ++ List(Alias(holder.get, randomAliasName)))
                updatedAccount.saveTheRecord()
                Full(randomAliasName)
              }
              case _ => Empty
            }
      }
  
  def createPlaceholderPublicAlias() = {
        theAccount match {
              case Full(a) => {
                val updatedAccount = a.publicAliases(a.publicAliases.get ++ List(Alias(holder.get, "")))
                updatedAccount.saveTheRecord()
                Full("")
              }
              case _ => Empty
            }
      }
  
  def createPlaceholderPrivateAlias() = {
        theAccount match {
              case Full(a) => {
                val updatedAccount = a.privateAliases(a.privateAliases.get ++ List(Alias(holder.get, "")))
                updatedAccount.saveTheRecord()
                Full("")
              }
              case _ => Empty
            }
      }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_holder(user: String) : (Box[String], Box[OBPAccount.AnAlias]) = {
    val theHolder = holder.get
    
    def usePrivateAliasIfExists() : (Box[String], Box[OBPAccount.AnAlias])= {
      val privateAlias = for{
        account <- theAccount
        alias <- account.privateAliases.get.find(a => a match{
		        case Alias(`theHolder`, "") => false
		        case Alias(`theHolder`, _) => true
		        case _ => false
        	})
      } yield alias.aliasValue
      
      privateAlias match{
        case Full(a) => (Full(a), Full(OBPAccount.APrivateAlias))
        case _ => (Full(theHolder), Empty)
      }
    }
    
    def usePublicAlias() : (Box[String], Box[OBPAccount.AnAlias])= {
      val publicAlias = for{
        account <- theAccount
        alias <- account.publicAliases.get.find(a => a match{
		        case Alias(`theHolder`, "") => false
		        case Alias(`theHolder`, _) => true
		        case _ => false
        	})
      } yield alias.aliasValue
      
      publicAlias match{
        case Full(a) => (Full(a), Full(OBPAccount.APublicAlias))
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
      case _ => Empty
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_kind(user: String) : Box[String] = {
    user match{
      case "team" => Full(kind.get)
      case "board" => Full(kind.get)
      case "authorities" => Full(kind.get)
      case _ => Empty
    }
  }
  //JString(mediated_holder(user) getOrElse ("---")
  def asMediatedJValue(user: String) : JObject = {
    val h = mediated_holder(user)
    JObject(List( JField("holder", 
    				JObject(List(
    				    JField("holder", JString(h._1.getOrElse("---"))),
    				    JField("alias", JString(h._2 match{
    				      case Full(OBPAccount.APublicAlias) => "public"
    				      case Full(OBPAccount.APrivateAlias) => "private"
    				      case _ => "no"
    				    }))))),
        		  JField("number", JString(mediated_number(user) getOrElse "---")),
        		  JField("kind", JString(mediated_kind(user) getOrElse "---")),
        		  JField("bank", bank.get.asMediatedJValue(user))))
  }
}

object OBPAccount extends OBPAccount with BsonMetaRecord[OBPAccount]{
  sealed abstract class AnAlias
  case object APublicAlias extends AnAlias
  case object APrivateAlias extends AnAlias
}



/*
class OBPAccount private() extends MongoRecord[OBPAccount] with ObjectIdPk[OBPAccount] {
  def meta = OBPAccount

  object holder extends net.liftweb.record.field.StringField(this, 255)
  object number extends net.liftweb.record.field.StringField(this, 255)
  object kind extends net.liftweb.record.field.StringField(this, 255)
  object bank extends BsonRecordField(this, OBPBank)


}

object OBPAccount extends OBPAccount with MongoMetaRecord[OBPAccount]

*/


///////////

class OBPBank private() extends BsonRecord[OBPBank]{
  def meta = OBPBank

  protected object IBAN extends net.liftweb.record.field.StringField(this, 255)
  protected object national_identifier extends net.liftweb.record.field.StringField(this, 255)
  protected object name extends net.liftweb.record.field.StringField(this, 255)

  //TODO: Access levels are currently the same across all transactions
  def mediated_IBAN(user: String) : Box[String] = {
    user match{
      case "team" => Full(IBAN.get)
      case "board" => Full(IBAN.get)
      case "authorities" => Full(IBAN.get)
      case _ => Empty
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_national_identifier(user: String) : Box[String] = {
    user match{
      case "team" => Full(national_identifier.get)
      case "board" => Full(national_identifier.get)
      case "authorities" => Full(national_identifier.get)
      case _ => Empty
    }
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_name(user: String) : Box[String] = {
    user match{
      case "team" => Full(name.get)
      case "board" => Full(name.get)
      case "authorities" => Full(name.get)
      case _ => Empty
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("IBAN", JString(mediated_IBAN(user) getOrElse "---")),
        		  JField("national_identifier", JString(mediated_national_identifier(user) getOrElse "---")),
        		  JField("name", JString(mediated_name(user) getOrElse "---"))))
  }
}

object OBPBank extends OBPBank with BsonMetaRecord[OBPBank]



class OBPDetails private() extends BsonRecord[OBPDetails]{
  def meta = OBPDetails

  protected object type_en extends net.liftweb.record.field.StringField(this, 255)
  protected object type_de extends net.liftweb.record.field.StringField(this, 255)
  protected object posted extends DateField(this)
  protected object completed extends DateField(this)
  protected object other_data extends net.liftweb.record.field.StringField(this, 5000)
  object new_balance extends BsonRecordField(this, OBPBalance)
  object value extends BsonRecordField(this, OBPValue)
  
  
  def formatDate(date : Box[Date]) : String = {
    date match{
      case Full(d) => OBPDetails.formats.dateFormat.format(d)
      case _ => "---"
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
      case _ => Empty
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("type_en", JString(mediated_type_en(user) getOrElse "---")),
        		  JField("type_de", JString(mediated_type_de(user) getOrElse "---")),
        		  JField("posted", JString(formatDate(mediated_posted(user)))),
        		  JField("completed", JString(formatDate(mediated_completed(user)))),
        		  JField("other_data", JString(mediated_other_data(user) getOrElse "---")),
        		  JField("new_balance", new_balance.get.asMediatedJValue(user)),
        		  JField("value", value.get.asMediatedJValue(user))))
  }
}

object OBPDetails extends OBPDetails with BsonMetaRecord[OBPDetails]


class OBPBalance private() extends BsonRecord[OBPBalance]{
  def meta = OBPBalance

  protected object currency extends net.liftweb.record.field.StringField(this, 5)
  protected object amount extends net.liftweb.record.field.DecimalField(this, 0) // ok to use decimal?

  //TODO: Access levels are currently the same across all transactions
  def mediated_currency(user: String) : Box[String] = {
    user match{
      case "our-network" => Full(currency.get)
      case "team" => Full(currency.get)
      case "board" => Full(currency.get)
      case "authorities" => Full(currency.get)
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
      case _ => {
        if(amount.get.toString.startsWith("-")) Full("-") else Full("+")
      }
    }
  }
  
  def asMediatedJValue(user: String) : JObject = {
    JObject(List( JField("currency", JString(mediated_currency(user) getOrElse "---")),
        		  JField("amount", JString(mediated_amount(user) getOrElse "---"))))
  }
}

object OBPBalance extends OBPBalance with BsonMetaRecord[OBPBalance]

class OBPValue private() extends BsonRecord[OBPValue]{
  def meta = OBPValue

  protected object currency extends net.liftweb.record.field.StringField(this, 5)
  protected object amount extends net.liftweb.record.field.DecimalField(this, 0) // ok to use decimal?

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
    JObject(List( JField("currency", JString(mediated_currency(user) getOrElse "---")),
        		  JField("amount", JString(mediated_amount(user) getOrElse "---"))))
  }
}

object OBPValue extends OBPValue with BsonMetaRecord[OBPValue]


