package code.metadata.transactionimages

import java.util.Date

import code.model._
import code.remotedata.RemotedataTransactionImages
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(buildOne _) {}

  def buildOne: TransactionImages =
    Props.getBool("use_akka", false) match {
      case false  => MapperTransactionImages
      case true => RemotedataTransactionImages     // We will use Akka as a middleware
    }
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage]
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean]

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean
  
}

class RemotedataTransactionImagesCaseClasses {
  case class getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, description : String, datePosted : Date, imageURL: String)
  case class deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId, imageId : String)
  case class bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId)
}

object RemotedataTransactionImagesCaseClasses extends RemotedataTransactionImagesCaseClasses
