package code.metadata.wheretags

import code.util.Helper
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

  object userId extends StringField(this,255)

  object forView extends StringField(this, 255)

  object date extends DateField(this)

  object geoLongitude extends DoubleField(this,0)
  object geoLatitude extends DoubleField(this,0)

  def datePosted = date.get
  def postedBy = User.findByApiId(userId.get)
  def viewId = ViewId(forView.get)
  def longitude = geoLongitude.get
  def latitude = geoLatitude.get
}

private object OBPWhereTag extends OBPWhereTag with MongoMetaRecord[OBPWhereTag] {
  def findAll(bankId : BankId, accountId : AccountId, transactionId : TransactionId) : List[OBPWhereTag] = {
    val query = QueryBuilder.start("bankId").is(bankId.value).put("accountId").is(accountId.value).put("transactionId").is(transactionId.value).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId) : DBObject = {
    QueryBuilder.start("forView").is(viewId.value).put("transactionId").is(transactionId.value).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}
