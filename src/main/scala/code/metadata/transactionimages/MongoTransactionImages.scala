package code.metadata.transactionimages

import code.model.TransactionImage
import net.liftweb.common.{Failure, Loggable, Full, Box}
import java.net.URL
import java.util.Date
import code.model.dataAccess.{OBPTransactionImage, OBPEnvelope}
import org.bson.types.ObjectId

object MongoTransactionImages extends TransactionImages with Loggable {

  def getImagesForTransaction(bankId : String, accountId : String, transactionId: String)() : List[TransactionImage] = {
    OBPTransactionImage.findAll(bankId, accountId, transactionId)
  }
  
  def addTransactionImage(bankId : String, accountId : String, transactionId: String)
  (userId: String, viewId : Long, description : String, datePosted : Date, imageURL: URL) : Box[TransactionImage] = {
    OBPTransactionImage.createRecord.
      bankId(bankId).
      accountId(accountId).
      transactionId(transactionId).
      userId(userId).
      viewID(viewId).
      imageComment(description).
      date(datePosted).
      url(imageURL.toString).saveTheRecord()
  }
  
  def deleteTransactionImage(bankId : String, accountId : String, transactionId: String)(imageId : String) : Box[Unit] = {
    OBPTransactionImage.find(bankId, accountId, transactionId, imageId) match {
      case Full(image) => {
        if(image.delete_!) Full()
        else Failure("Delete not completed")
      }
      case _ => Failure("Image "+imageId+" not found")
    }
  }
  
}