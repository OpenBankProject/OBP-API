package code.metadata.transactionimages

import net.liftweb.util.SimpleInjector
import java.util.Date
import java.net.URL
import net.liftweb.common.Box
import code.model._

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(buildOne _) {}
  
  def buildOne: TransactionImages = MongoTransactionImages
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage]
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: String, viewId : ViewId, description : String, datePosted : Date, imageURL: URL) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Unit]
  
}
  