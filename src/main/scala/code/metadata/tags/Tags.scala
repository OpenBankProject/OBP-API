package code.metadata.tags

import net.liftweb.util.SimpleInjector
import java.util.Date
import net.liftweb.common.Box
import code.model._

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}
  
  def buildOne: Tags = MappedTags
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  //TODO: viewId? should tagId always be unique -> in that case bankId, accountId, and transactionId would not be required
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Unit]
  
}