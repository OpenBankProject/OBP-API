package code.metadata.tags

import net.liftweb.util.SimpleInjector
import java.util.Date
import net.liftweb.common.Box
import code.model.{AccountId, BankId, TransactionTag}

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}
  
  def buildOne: Tags = MongoTransactionTags
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: String)() : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: String)(userId: String, viewId : Long, tagText : String, datePosted : Date) : Box[TransactionTag]
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: String)(tagId : String) : Box[Unit]
  
}