package code.metadata.transactionimages

import net.liftweb.util.SimpleInjector
import java.util.Date
import java.net.URL
import net.liftweb.common.Box
import code.model.{BankId, TransactionImage}

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(buildOne _) {}
  
  def buildOne: TransactionImages = MongoTransactionImages
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : BankId, accountId : String, transactionId: String)() : List[TransactionImage]
  
  def addTransactionImage(bankId : BankId, accountId : String, transactionId: String)
  (userId: String, viewId : Long, description : String, datePosted : Date, imageURL: URL) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : BankId, accountId : String, transactionId: String)(imageId : String) : Box[Unit]
  
}
  