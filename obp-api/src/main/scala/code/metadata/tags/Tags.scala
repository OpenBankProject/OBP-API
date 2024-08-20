package code.metadata.tags

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}

  def buildOne: Tags = MappedTags
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionTag]
  def getTagsOnAccount(bankId : BankId, accountId : AccountId)(viewId : ViewId) : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  def addTagOnAccount(bankId : BankId, accountId : AccountId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  //TODO: viewId? should tagId always be unique -> in that case bankId, accountId, and transactionId would not be required
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean]
  def deleteTagOnAccount(bankId : BankId, accountId : AccountId)(tagId : String) : Box[Boolean]
  def bulkDeleteTags(bankId: BankId, accountId: AccountId) : Boolean
  def bulkDeleteTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) : Boolean
  
}

