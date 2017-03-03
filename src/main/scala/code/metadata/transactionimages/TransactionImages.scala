package code.metadata.transactionimages

import net.liftweb.util.SimpleInjector
import java.util.Date

import net.liftweb.common.Box
import code.model._
import code.remotedata.RemotedataTransactionImages

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(buildOne _) {}
  
  //def buildOne: TransactionImages = MapperTransactionImages
  def buildOne: TransactionImages = RemotedataTransactionImages
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage]
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean]

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean
  
}

class RemoteTransactionImagesCaseClasses {
  case class getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String)
  case class deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, imageId : String)
  case class bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId)
}

object RemoteTransactionImagesCaseClasses extends RemoteTransactionImagesCaseClasses