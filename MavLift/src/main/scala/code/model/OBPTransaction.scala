package code.model

import net.liftweb.mongodb.record.field.{BsonRecordField, ObjectIdPk}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, BsonMetaRecord, BsonRecord}

/**
 * Created by IntelliJ IDEA.
 * User: simonredfern
 * Date: 10/17/11
 * Time: 1:43 AM
 * To change this template use File | Settings | File Templates.
 */


class Location private () extends BsonRecord[Location] {
  def meta = Location

  object longitude extends net.liftweb.record.field.IntField(this)
  object latitude extends net.liftweb.record.field.IntField(this)

}
object Location extends Location with BsonMetaRecord[Location]



class OBPTransaction private() extends MongoRecord[OBPTransaction] with ObjectIdPk[OBPTransaction] {
  def meta = OBPTransaction

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

}

object OBPTransaction extends OBPTransaction with MongoMetaRecord[OBPTransaction]
