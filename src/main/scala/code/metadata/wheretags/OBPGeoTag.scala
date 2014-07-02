package code.metadata.wheretags

import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{DoubleField, LongField, StringField}
import code.model.{GeoTag, User}
import net.liftweb.common.Box
import com.mongodb.QueryBuilder

class OBPGeoTag private() extends MongoRecord[OBPGeoTag] with ObjectIdPk[OBPGeoTag] with GeoTag {
  def meta = OBPGeoTag

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends StringField(this,255)
  object viewID extends LongField(this)
  object date extends DateField(this)

  object geoLongitude extends DoubleField(this,0)
  object geoLatitude extends DoubleField(this,0)

  def datePosted = date.get
  def postedBy = User.findByApiId(userId.get)
  def viewId = viewID.get
  def longitude = geoLongitude.get
  def latitude = geoLatitude.get
}

object OBPGeoTag extends OBPGeoTag with MongoMetaRecord[OBPGeoTag] {
  def findAll(bankId : String, accountId : String, transactionId : String) : List[OBPGeoTag] = {
    val query = QueryBuilder.start("bankId").is(bankId).put("accountId").is(accountId).put("transactionId").is(transactionId).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def find(bankId : String, accountId : String, transactionId : String, viewId : Long) : Box[OBPGeoTag] = {
    val query = QueryBuilder.start("viewID").is(viewId).put("transactionId").is(transactionId).
      put("accountId").is(accountId).put("bankId").is(bankId).get()
    find(query)
  }
}
