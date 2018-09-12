package code.metadata.tags

import code.model._
import java.util.Date
import code.util.Helper
import net.liftweb.common.{Full, Box}
import org.bson.types.ObjectId
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{LongField, StringField}
import com.mongodb.{DBObject, QueryBuilder}

private object MongoTransactionTags extends Tags {
  
  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionTag] = {
    OBPTag.findAll(bankId, accountId, transactionId, viewId)
  }
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag] = {
    OBPTag.createRecord.
      bankId(bankId.value).
      accountId(accountId.value).
      transactionId(transactionId.value).
      userId(userId.value).
      forView(viewId.value).
      tag(tagText).
      date(datePosted).saveTheRecord()
  }
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean] = {
    //use delete with find query to avoid concurrency issues
    OBPTag.delete(OBPTag.getFindQuery(bankId, accountId, transactionId, tagId))

    //we don't have any useful information here so just assume it worked
    Full(true)
  }

  def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean = ???
  
}


private class OBPTag private() extends MongoRecord[OBPTag] with ObjectIdPk[OBPTag] with TransactionTag {
  def meta = OBPTag

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends LongField(this)

  object forView extends StringField(this, 255)

  object tag extends StringField(this, 255)
  object date extends DateField(this)

  def id_ = id.get.toString
  def datePosted = date.get
  def postedBy = User.findByResourceUserId(userId.get)
  def viewId = ViewId(forView.get)
  def value = tag.get
}

private object OBPTag extends OBPTag with MongoMetaRecord[OBPTag] {
  def findAll(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId) : List[OBPTag] = {
    val query = QueryBuilder.
      start("bankId").is(bankId.value).
      put("accountId").is(accountId.value).
      put("transactionId").is(transactionId.value).
      put("forView").is(viewId.value).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : TransactionId, tagId : String) : DBObject = {
    QueryBuilder.start("_id").is(new ObjectId(tagId)).put("transactionId").is(transactionId.value).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}