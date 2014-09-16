package code.metadata.tags

import net.liftweb.util.SimpleInjector
import java.util.Date
import net.liftweb.common.Box
import code.model.{TransactionId, AccountId, BankId, TransactionTag}

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}
  
  def buildOne: Tags = MongoTransactionTags
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)() : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: String, viewId : Long, tagText : String, datePosted : Date) : Box[TransactionTag]
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Unit]
  
}