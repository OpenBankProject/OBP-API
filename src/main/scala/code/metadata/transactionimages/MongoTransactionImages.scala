package code.metadata.transactionimages

import code.model.TransactionImage
import net.liftweb.common.Box
import java.net.URL
import java.util.Date

object MongoTransactionImages extends TransactionImages {

  def getImagesForTransaction(bankId : String, accountId : String, transactionIdGivenByBank: String) : Iterable[TransactionImage] = {
    //TODO
    null
  }
  
  def addTransactionImage(bankId : String, accountId : String, transactionId: String)
  (userId: String, viewId : Long, description : String, datePosted : Date, url: URL) : Box[TransactionImage] = {
    //TODO
    null
  }
  
  def deleteTransactionImage(transactionId : String) : Box[Unit] = {
    //TODO
    null
  }
  
}