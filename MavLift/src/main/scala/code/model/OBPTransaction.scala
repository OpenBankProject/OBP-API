package code.model

import net.liftweb.mongodb._
import net.liftweb.record.MandatoryTypedField
import net.liftweb.mongodb.record.field.{BsonRecordField, ObjectIdPk}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, BsonMetaRecord, BsonRecord}
import net.liftweb.common.{Box, Full, Empty, Failure}
import java.util.Calendar


import net.liftweb.json.JsonAST._

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
            "type":"current",
            "bank":{
               "IBAN":"DE1235123612",
               "national_identifier":"de.10010010",
               "name":"Postbank"
            }
         },
         "other_account":{
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
}

object OBPEnvelope extends OBPEnvelope with MongoMetaRecord[OBPEnvelope]


class OBPTransaction private() extends MongoRecord[OBPTransaction] with ObjectIdPk[OBPTransaction] {
  def meta = OBPTransaction // what does meta do?
  
  object this_account extends BsonRecordField(this, OBPAccount)
  object other_account extends BsonRecordField(this, OBPAccount)
  object details extends BsonRecordField(this, OBPDetails)
  
}

object OBPTransaction extends OBPTransaction with MongoMetaRecord[OBPTransaction]


///


class OBPAccount private() extends MongoRecord[OBPAccount] with ObjectIdPk[OBPAccount] {
  def meta = OBPAccount

  protected object holder extends net.liftweb.record.field.StringField(this, 255)
  protected object number extends net.liftweb.record.field.StringField(this, 255)
  protected object kind extends net.liftweb.record.field.StringField(this, 255)
  object bank extends BsonRecordField(this, OBPBank)

  
  def accountAliases : Map[String,String] = {
    Map("Neils Hapke" -> "The Chicken", "Yoav Aner" -> "Software Developer 1", "Jan Slabiak" -> "Alex")
  }
  
  //TODO: Access levels are currently the same across all transactions
  def mediated_holder(user: String) : Box[String] = {
    
    def useAliases = {
      val theHolder = holder.get
      val alias = accountAliases.get(theHolder)
      if(alias.isDefined) Full(alias.get)
      else Full(theHolder)
    }
    
    user match{
      case "team" => Full(holder.get)
      case "board" => Full(holder.get)
      case "authorities" => Full(holder.get)
      case _ => useAliases
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
      case "team" => Full(number.get)
      case "board" => Full(number.get)
      case "authorities" => Full(number.get)
      case _ => Empty
    }
  }
}

object OBPAccount extends OBPAccount with MongoMetaRecord[OBPAccount]



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

class OBPBank private() extends MongoRecord[OBPBank] with ObjectIdPk[OBPBank] {
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
}

object OBPBank extends OBPBank with MongoMetaRecord[OBPBank]



class OBPDetails private() extends MongoRecord[OBPDetails] with ObjectIdPk[OBPDetails] {
  def meta = OBPDetails

  protected object type_en extends net.liftweb.record.field.StringField(this, 255)
  protected object type_de extends net.liftweb.record.field.StringField(this, 255)
  protected object posted extends net.liftweb.record.field.DateTimeField(this)
  protected object completed extends net.liftweb.record.field.DateTimeField(this)
  protected object other_data extends net.liftweb.record.field.StringField(this, 5000)
  object value extends BsonRecordField(this, OBPValue)
  

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
  def mediated_posted(user: String) : Box[String] = {
    user match{
      case _ => Full(posted.get.toString)
    }
  }
  //TODO: Access levels are currently the same across all transactions
  def mediated_completed(user: String) : Box[String] = {
    user match{
      case _ => Full(completed.get.toString)
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
}

object OBPDetails extends OBPDetails with MongoMetaRecord[OBPDetails]


class OBPValue private() extends MongoRecord[OBPValue] with ObjectIdPk[OBPValue] {
  def meta = OBPValue

  protected object currency extends net.liftweb.record.field.StringField(this, 5)
  protected object amount extends net.liftweb.record.field.DecimalField(this, 0) // ok to use decimal?

  //TODO: Access levels are currently the same across all transactions
  def mediated_currency(user: String) : Box[String] = {
    user match{
      case "team" => Full(currency.get)
      case "board" => Full(currency.get)
      case "our_network" => Full(currency.get)
      case "authorities" => Full(currency.get)
      case "anonymous" => {
        if (currency.get.startsWith("-") ) Full("-") else Full("+")
      }
      case _ => Empty
    }
  }
  //TODO: Access levels are currently the same across all transactions
  def mediated_amount(user: String) : Box[String] = {
    user match{
      case "team" => Full(currency.get)
      case "board" => Full(currency.get)
      case "our_network" => Full(currency.get)
      case "authorities" => Full(currency.get)
      case "anonymous" => {
        if (currency.get.startsWith("-") ) Full("-") else Full("+")
      }
      case _ => Empty
    }
  }
}

object OBPValue extends OBPValue with MongoMetaRecord[OBPValue]


