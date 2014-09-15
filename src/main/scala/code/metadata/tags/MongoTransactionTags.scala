package code.metadata.tags

import code.model.{AccountId, BankId, User, TransactionTag}
import java.util.Date
import net.liftweb.common.{Full, Box}
import org.bson.types.ObjectId
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{LongField, StringField}
import com.mongodb.{DBObject, QueryBuilder}

private object MongoTransactionTags extends Tags {
  
  def getTags(bankId : BankId, accountId : AccountId, transactionId: String)() : List[TransactionTag] = {
    OBPTag.findAll(bankId, accountId, transactionId)
  }
  def addTag(bankId : BankId, accountId : AccountId, transactionId: String)(userId: String, viewId : Long, tagText : String, datePosted : Date) : Box[TransactionTag] = {
    OBPTag.createRecord.
      bankId(bankId.value).
      accountId(accountId.value).
      transactionId(transactionId).
      userId(userId).
      viewID(viewId).
      tag(tagText).
      date(datePosted).saveTheRecord()
  }
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: String)(tagId : String) : Box[Unit] = {
    //use delete with find query to avoid concurrency issues
    OBPTag.delete(OBPTag.getFindQuery(bankId, accountId, transactionId, tagId))

    //we don't have any useful information here so just assume it worked
    Full()
  }
  
}


private class OBPTag private() extends MongoRecord[OBPTag] with ObjectIdPk[OBPTag] with TransactionTag {
  def meta = OBPTag

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends StringField(this,255)
  object viewID extends LongField(this)
  object tag extends StringField(this, 255)
  object date extends DateField(this)

  def id_ = id.is.toString
  def datePosted = date.get
  def postedBy = User.findByApiId(userId.get)
  def viewId = viewID.get
  def value = tag.get
}

private object OBPTag extends OBPTag with MongoMetaRecord[OBPTag] {
  def findAll(bankId : BankId, accountId : AccountId, transactionId : String) : List[OBPTag] = {
    val query = QueryBuilder.start("bankId").is(bankId.value).put("accountId").is(accountId.value).put("transactionId").is(transactionId).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : String, tagId : String) : DBObject = {
    QueryBuilder.start("_id").is(new ObjectId(tagId)).put("transactionId").is(transactionId).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}