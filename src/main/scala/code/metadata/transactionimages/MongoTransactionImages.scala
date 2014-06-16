package code.metadata.transactionimages

import code.model.TransactionImage
import net.liftweb.common.{Loggable, Full, Box}
import java.net.URL
import java.util.Date
import code.model.dataAccess.{OBPTransactionImage, OBPEnvelope}
import org.bson.types.ObjectId

object MongoTransactionImages extends TransactionImages with Loggable {

  def getImagesForTransaction(bankId : String, accountId : String, transactionId: String)() : List[TransactionImage] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    val env = OBPEnvelope.find(new ObjectId(transactionId))
    val images = env.map(e => {
      e.images.objs
    })

    images.getOrElse(Nil)
  }
  
  def addTransactionImage(bankId : String, accountId : String, transactionId: String)
  (userId: String, viewId : Long, description : String, datePosted : Date, imageURL: URL) : Box[TransactionImage] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      val image = OBPTransactionImage.createRecord.
        userId(userId).imageComment(description).date(datePosted).viewID(viewId).url(imageURL.toString).save
      env.images(image.id.is :: env.images.get).save
      image
    }
  }
  
  def deleteTransactionImage(bankId : String, accountId : String, transactionId: String)(imageId : String) : Box[Unit] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      OBPTransactionImage.find(imageId) match {
        case Full(image) => {
          //if(image.postedBy.isDefined && image.postedBy.get.id.get == userId) {
          if (image.delete_!) {
            logger.info("==> deleted image id : " + imageId)
            env.images(env.images.get.diff(Seq(new ObjectId(imageId)))).save
          }
        }
        case _ => logger.warn("Could not find image with id " + imageId + " to delete.")
      }
    }
  }
  
}