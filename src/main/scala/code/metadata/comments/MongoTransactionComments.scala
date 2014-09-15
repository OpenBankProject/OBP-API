package code.metadata.comments

import code.model.{AccountId, BankId, User, Comment}
import java.util.Date
import net.liftweb.common.{Loggable, Full, Box}
import org.bson.types.ObjectId
import com.mongodb.{DBObject, QueryBuilder}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{LongField, StringField}

private object MongoTransactionComments extends Comments {

  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : String)() : List[Comment] = {
     OBPComment.findAll(bankId, accountId, transactionId)
  }
  def addComment(bankId : BankId, accountId : AccountId, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment] = {
    OBPComment.createRecord.userId(userId).
        transactionId(transactionId).
        accountId(accountId.value).
        bankId(bankId.value).
        textField(text).
        date(datePosted).
        viewID(viewId).saveTheRecord()
  }

  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: String)(commentId : String) : Box[Unit] = {
    //use delete with find query to avoid concurrency issues
    OBPComment.delete(OBPComment.getFindQuery(bankId, accountId, transactionId, commentId))

    //we don't have any useful information here so just assume it worked
    Full()
  }
}

private class OBPComment private() extends MongoRecord[OBPComment] with ObjectIdPk[OBPComment] with Comment {
  def meta = OBPComment

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  def postedBy = User.findByApiId(userId.get)
  def viewId = viewID.get
  def text = textField.get
  def datePosted = date.get
  def id_ = id.is.toString
  def replyToID = replyTo.get
  object userId extends StringField(this,255)
  object viewID extends LongField(this)
  object textField extends StringField(this, 255)
  object date extends DateField(this)
  object replyTo extends StringField(this,255)
}

private object OBPComment extends OBPComment with MongoMetaRecord[OBPComment] with Loggable {
  def findAll(bankId : BankId, accountId : AccountId, transactionId : String) : List[OBPComment] = {
    val query = QueryBuilder.start("bankId").is(bankId.value).put("accountId").is(accountId.value).put("transactionId").is(transactionId).get
    findAll(query)
  }

  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : String, commentId : String) : DBObject = {
    //in theory commentId should be enough as we're just using the mongoId
    QueryBuilder.start("_id").is(new ObjectId(commentId)).put("transactionId").is(transactionId).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}