package code.metadata.transactionimages

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object TransactionImages  extends SimpleInjector {

  val transactionImages = new Inject(buildOne _) {}

  def buildOne: TransactionImages = MapperTransactionImages
  
}

trait TransactionImages {

  def getImagesForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionImage]
  
  def addTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
  (userId: UserPrimaryKey, viewId : ViewId, description : String, datePosted : Date, imageURL: String) : Box[TransactionImage]
  
  def deleteTransactionImage(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(imageId : String) : Box[Boolean]
  
  def bulkDeleteImagesOnTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId) : Boolean

  def bulkDeleteTransactionImage(bankId: BankId, accountId: AccountId): Boolean
  
}
