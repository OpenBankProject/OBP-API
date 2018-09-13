package code.metadata.comments

import code.model._
import java.util.Date
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Full, Box}
import org.bson.types.ObjectId
import com.mongodb.{DBObject, QueryBuilder}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{LongField, StringField}

private object MongoTransactionComments extends Comments {

  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment] = {
     OBPComment.findAll(bankId, accountId, transactionId, viewId)
  }
  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date) : Box[Comment] = {
    OBPComment.createRecord.userId(userId.value).
        transactionId(transactionId.value).
        accountId(accountId.value).
        bankId(bankId.value).
        textField(text).
        date(datePosted).
        forView(viewId.value).saveTheRecord()
  }

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean] = {
    //use delete with find query to avoid concurrency issues
    OBPComment.delete(OBPComment.getFindQuery(bankId, accountId, transactionId, commentId))

    //we don't have any useful information here so just assume it worked
    Full(true)
  }

  def bulkDeleteComments(bankId: BankId, accountId: AccountId): Boolean = ???
}

private class OBPComment private() extends MongoRecord[OBPComment] with ObjectIdPk[OBPComment] with Comment {
  def meta = OBPComment

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  def postedBy = User.findByResourceUserId(userId.get)
  def viewId = ViewId(forView.get)
  def text = textField.get
  def datePosted = date.get
  def id_ = id.get.toString
  def replyToID = replyTo.get
  object userId extends LongField(this)

  object forView extends StringField(this, 255)

  object textField extends StringField(this, 255)
  object date extends DateField(this)
  object replyTo extends StringField(this,255)
}

private object OBPComment extends OBPComment with MongoMetaRecord[OBPComment] with MdcLoggable {
  def findAll(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId) : List[OBPComment] = {
    val query = QueryBuilder.
      start("bankId").is(bankId.value).
      put("accountId").is(accountId.value).
      put("transactionId").is(transactionId.value).
      put("forView").is(viewId.value).get
    findAll(query)
  }

  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : TransactionId, commentId : String) : DBObject = {
    //in theory commentId should be enough as we're just using the mongoId
    QueryBuilder.start("_id").is(new ObjectId(commentId)).put("transactionId").is(transactionId.value).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}