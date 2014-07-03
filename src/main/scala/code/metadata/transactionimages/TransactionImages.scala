package code.metadata.transactionimages

import net.liftweb.util.SimpleInjector
import java.util.Date
import java.net.URL
import net.liftweb.common.Box
import code.model.TransactionImage

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(buildOne _) {}
  
  def buildOne: TransactionImages = MongoTransactionImages
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : String, accountId : String, transactionId: String)() : List[TransactionImage]
  
  def addTransactionImage(bankId : String, accountId : String, transactionId: String)
  (userId: String, viewId : Long, description : String, datePosted : Date, imageURL: URL) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : String, accountId : String, transactionId: String)(imageId : String) : Box[Unit]
  
}
  