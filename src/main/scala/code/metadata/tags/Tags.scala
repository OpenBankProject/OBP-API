package code.metadata.tags

import java.util.Date

import code.api.util.APIUtil
import code.model._
import code.remotedata.RemotedataTags
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}

  def buildOne: Tags =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedTags
      case true => RemotedataTags     // We will use Akka as a middleware
    }
  
}

trait Tags {

  def getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : List[TransactionTag]
  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag]
  //TODO: viewId? should tagId always be unique -> in that case bankId, accountId, and transactionId would not be required
  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean]
  def bulkDeleteTags(bankId: BankId, accountId: AccountId) : Boolean
  
}

class RemotedataTagsCaseClasses{
  case class getTags(bankId : BankId, accountId : AccountId, transactionId: TransactionId, viewId : ViewId)
  case class addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserPrimaryKey, viewId : ViewId, tagText : String, datePosted : Date)
  case class deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId, tagId : String)
  case class bulkDeleteTags(bankId: BankId, accountId: AccountId)
}

object RemotedataTagsCaseClasses extends RemotedataTagsCaseClasses
