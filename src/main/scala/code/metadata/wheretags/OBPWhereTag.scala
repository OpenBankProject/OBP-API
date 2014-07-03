package code.metadata.wheretags

import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{DoubleField, LongField, StringField}
import code.model.{GeoTag, User}
import net.liftweb.common.Box
import com.mongodb.{DBObject, QueryBuilder}

class OBPWhereTag private() extends MongoRecord[OBPWhereTag] with ObjectIdPk[OBPWhereTag] with GeoTag {
  def meta = OBPWhereTag

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

object OBPWhereTag extends OBPWhereTag with MongoMetaRecord[OBPWhereTag] {
  def findAll(bankId : String, accountId : String, transactionId : String) : List[OBPWhereTag] = {
    val query = QueryBuilder.start("bankId").is(bankId).put("accountId").is(accountId).put("transactionId").is(transactionId).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : String, accountId : String, transactionId : String, viewId : Long) : DBObject = {
    QueryBuilder.start("viewID").is(viewId).put("transactionId").is(transactionId).
      put("accountId").is(accountId).put("bankId").is(bankId).get()
  }
}
