package code.metadata.transactionimages

import code.model._
import code.util.Helper
import net.liftweb.common.Box
import code.util.Helper.MdcLoggable
import java.net.URL
import java.util.Date
import org.bson.types.ObjectId
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{LongField, StringField}
import net.liftweb.util.Helpers._
import net.liftweb.common.Full
import com.mongodb.{DBObject, QueryBuilder}

private object MongoTransactionImages extends TransactionImages with MdcLoggable {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage] = {
    OBPTransactionImage.findAll(bankId, accountId, transactionId, viewId)
  }
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: UserPrimaryKey, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage] = {
    OBPTransactionImage.createRecord.
      bankId(bankId.value).
      accountId(accountId.value).
      transactionId(transactionId.value).
      userId(userId.value).
      forView(viewId.value).
      imageComment(description).
      date(datePosted).
      url(imageURL.toString).saveTheRecord()
  }
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean] = {
    //use delete with find query to avoid concurrency issues
    OBPTransactionImage.delete(OBPTransactionImage.getFindQuery(bankId, accountId, transactionId, imageId))

    //we don't have any useful information here so just assume it worked
    Full(true)
  }

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean = ???
  
}

private class OBPTransactionImage private() extends MongoRecord[OBPTransactionImage]
with ObjectIdPk[OBPTransactionImage] with TransactionImage {
  def meta = OBPTransactionImage

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends LongField(this)

  object forView extends StringField(this, 255)

  object imageComment extends StringField(this, 1000)
  object date extends DateField(this)
  object url extends StringField(this, 500)

  def id_ = id.get.toString
  def datePosted = date.get
  def postedBy = User.findByResourceUserId(userId.get)
  def viewId = ViewId(forView.get)
  def description = imageComment.get
  def imageUrl = {
    tryo {new URL(url.get)} getOrElse OBPTransactionImage.notFoundUrl
  }
}

private object OBPTransactionImage extends OBPTransactionImage with MongoMetaRecord[OBPTransactionImage] {
  val notFoundUrl = new URL("http://example.com/notfound.png") //TODO: Make this image exist?

  def findAll(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId) : List[OBPTransactionImage] = {
    val query = QueryBuilder.
      start("bankId").is(bankId.value).
      put("accountId").is(accountId.value).
      put("transactionId").is(transactionId.value).
      put("forView").is(viewId.value).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : TransactionId, imageId : String) : DBObject = {
    QueryBuilder.start("_id").is(new ObjectId(imageId)).put("transactionId").is(transactionId.value).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}