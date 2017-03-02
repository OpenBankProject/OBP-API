package code.metadata.tags

import net.liftweb.util.SimpleInjector
import java.util.Date

import net.liftweb.common.Box
import code.model._
import code.remotedata.Remotedata

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}
  
  def buildOne: Tags = MappedTags
  //def buildOne: Tags = Remotedata
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  //TODO: viewId? should tagId always be unique -> in that case bankId, accountId, and transactionId would not be required
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean]
  def bulkDeleteTags(bankId: BankId, accountId: AccountId) : Boolean
  
}

class RemoteTagsCaseClasses{
  case class getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, tagText : String, datePosted : Date)
  case class deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, tagId : String)
  case class bulkDeleteTags(bankId: BankId, accountId: AccountId)
}

object RemoteTagsCaseClasses extends RemoteTagsCaseClasses