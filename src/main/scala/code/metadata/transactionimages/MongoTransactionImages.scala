package code.metadata.transactionimages

import code.model._
import code.util.Helper
import net.liftweb.common.{Loggable, Box}
import java.net.URL
import java.util.Date
import org.bson.types.ObjectId
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{DateField, ObjectIdPk}
import net.liftweb.record.field.{LongField, StringField}
import net.liftweb.util.Helpers._
import net.liftweb.common.Full
import com.mongodb.{DBObject, QueryBuilder}

private object MongoTransactionImages extends TransactionImages with Loggable {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)() : List[TransactionImage] = {
    OBPTransactionImage.findAll(bankId, accountId, transactionId)
  }
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: String, viewId : ViewId, description : String, datePosted : Date, imageURL: URL) : Box[TransactionImage] = {
    OBPTransactionImage.createRecord.
      bankId(bankId.value).
      accountId(accountId.value).
      transactionId(transactionId.value).
      userId(userId).
      forView(viewId.value).
      imageComment(description).
      date(datePosted).
      url(imageURL.toString).saveTheRecord()
  }
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Unit] = {
    //use delete with find query to avoid concurrency issues
    OBPTransactionImage.delete(OBPTransactionImage.getFindQuery(bankId, accountId, transactionId, imageId))

    //we don't have any useful information here so just assume it worked
    Full()
  }
  
}

private class OBPTransactionImage private() extends MongoRecord[OBPTransactionImage]
with ObjectIdPk[OBPTransactionImage] with TransactionImage {
  def meta = OBPTransactionImage

  //These fields are used to link this to its transaction
  object transactionId extends StringField(this, 255)
  object accountId extends StringField(this, 255)
  object bankId extends StringField(this, 255)

  object userId extends StringField(this,255)

  @deprecated(Helper.deprecatedViewIdMessage)
  object viewID extends LongField(this)

  object forView extends StringField(this, 255)

  object imageComment extends StringField(this, 1000)
  object date extends DateField(this)
  object url extends StringField(this, 500)

  def id_ = id.is.toString
  def datePosted = date.get
  def postedBy = User.findByApiId(userId.get)
  def viewId = ViewId(forView.get)
  def description = imageComment.get
  def imageUrl = {
    tryo {new URL(url.get)} getOrElse OBPTransactionImage.notFoundUrl
  }
}

private object OBPTransactionImage extends OBPTransactionImage with MongoMetaRecord[OBPTransactionImage] {
  val notFoundUrl = new URL("http://google.com" + "/notfound.png") //TODO: Make this image exist?

  def findAll(bankId : BankId, accountId : AccountId, transactionId : TransactionId) : List[OBPTransactionImage] = {
    val query = QueryBuilder.start("bankId").is(bankId.value).put("accountId").is(accountId.value).put("transactionId").is(transactionId.value).get
    findAll(query)
  }

  //in theory commentId should be enough as we're just using the mongoId
  def getFindQuery(bankId : BankId, accountId : AccountId, transactionId : TransactionId, imageId : String) : DBObject = {
    QueryBuilder.start("_id").is(new ObjectId(imageId)).put("transactionId").is(transactionId.value).
      put("accountId").is(accountId.value).put("bankId").is(bankId.value).get()
  }
}