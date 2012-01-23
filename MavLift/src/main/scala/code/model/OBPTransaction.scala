package code.model

import net.liftweb.mongodb._

import net.liftweb.mongodb.record.field.{BsonRecordField, ObjectIdPk}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, BsonMetaRecord, BsonRecord}


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
               "type":"current",
               "bank":{
                  "IBAN":"UK12222879",
                  "national_identifier":"de.10010010",
                  "name":"Postbank"
               }
            },
            "to_account":{
               "holder":"Music Pictures Limited",
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
                  "amount":"123.45"
               },
               "other_data":"9Z65HCF/0723203600/68550030\nAU 100467978\nKD-Nr2767322"
            }
         }
      }
]


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


  object obp_transaction_date_start extends net.liftweb.record.field.DateTimeField(this)
  object obp_transaction_date_complete extends net.liftweb.record.field.DateTimeField(this)

  object obp_transaction_type_en extends net.liftweb.record.field.StringField(this, 255)
  object obp_transaction_type_de extends net.liftweb.record.field.StringField(this, 255)
  object obp_transaction_data_blob extends net.liftweb.record.field.StringField(this, 999999)
  object opb_transaction_other_account extends net.liftweb.record.field.StringField(this, 255)

  object obp_transaction_currency extends net.liftweb.record.field.StringField(this, 10)
  object obp_transaction_amount extends net.liftweb.record.field.StringField(this, 20)
  object obp_transaction_new_balance extends net.liftweb.record.field.StringField(this, 20)

  object obp_transaction_location extends BsonRecordField(this, Location)



  object from_account extends BsonRecordField(this, OBPAccount)
  object to_account extends BsonRecordField(this, OBPAccount)
  object details extends BsonRecordField(this, OBPDetails)

  object new_balance extends net.liftweb.record.field.StringField(this, 20)




}

object OBPTransaction extends OBPTransaction with MongoMetaRecord[OBPTransaction]


///


class OBPAccount private() extends MongoRecord[OBPAccount] with ObjectIdPk[OBPAccount] {
  def meta = OBPAccount

  object holder extends net.liftweb.record.field.StringField(this, 255)
  object number extends net.liftweb.record.field.StringField(this, 255)
  object kind extends net.liftweb.record.field.StringField(this, 255)
  object bank extends BsonRecordField(this, OBPBank)

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

  object IBAN extends net.liftweb.record.field.StringField(this, 255)
  object national_identifier extends net.liftweb.record.field.StringField(this, 255)
  object name extends net.liftweb.record.field.StringField(this, 255)

}

object OBPBank extends OBPBank with MongoMetaRecord[OBPBank]



class OBPDetails private() extends MongoRecord[OBPDetails] with ObjectIdPk[OBPDetails] {
  def meta = OBPDetails

  object type_en extends net.liftweb.record.field.StringField(this, 255)
  object type_de extends net.liftweb.record.field.StringField(this, 255)
  object posted extends net.liftweb.record.field.DateTimeField(this)
  object completed extends net.liftweb.record.field.DateTimeField(this)
  object value extends BsonRecordField(this, OBPValue)
  object other_data extends net.liftweb.record.field.StringField(this, 5000)

}

object OBPDetails extends OBPDetails with MongoMetaRecord[OBPDetails]


class OBPValue private() extends MongoRecord[OBPValue] with ObjectIdPk[OBPValue] {
  def meta = OBPValue

  object currency extends net.liftweb.record.field.StringField(this, 5)
  object amount extends net.liftweb.record.field.DecimalField(this, 255) // ok to use decimal?

}

object OBPValue extends OBPValue with MongoMetaRecord[OBPValue]


