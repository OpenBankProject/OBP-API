package code.metadata.wheretags

import net.liftweb.mongodb.BsonDSL._
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{DoubleField, LongField, StringField}
import code.model._
import com.mongodb.{DBObject, QueryBuilder}

private class OBPWhereTag private() extends MongoRecord[OBPWhereTag] with ObjectIdPk[OBPWhereTag] with GeoTag {
  def meta = OBPWhereTag

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends LongField(this)

  object forView extends StringField(this, 255)

  object date extends DateField(this)

  object geoLongitude extends DoubleField(this,0)
  object geoLatitude extends DoubleField(this,0)

  override def datePosted = date.get
  override def postedBy = User.findByResourceUserId(userId.get)
  override def longitude = geoLongitude.get
  override def latitude = geoLatitude.get
}

private object OBPWhereTag extends OBPWhereTag with MongoMetaRecord[OBPWhereTag] {

  def init = createIndex((transactionId.name -> 1) ~ (accountId.name -> 1) ~ (bankId.name -> 1) ~ (forView.name -> 1), true)

  def find(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId) : Option[OBPWhereTag] = {
    val query = getFindQuery(bankId, accountId, transactionId, viewId)
    find(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId) : DBObject = {
    QueryBuilder.start("forView").is(viewId.value).put("transactionId").is(transactionId.value).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}

object OBPWhereTagInit {
  def init = OBPWhereTag.init
}
