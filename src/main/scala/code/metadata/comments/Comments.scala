package code.metadata.comments

import java.util.Date

import code.api.util.APIUtil
import code.model._
import code.remotedata.RemotedataComments
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Comments extends SimpleInjector {

  val comments = new Inject(buildOne _) {}

  def buildOne: Comments =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedComments
      case true => RemotedataComments     // We will use Akka as a middleware
    }
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment]
  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date) : Box[Comment]
  //TODO: should commentId be unique among all comments, removing the need for the other parameters?
  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean]
  def bulkDeleteComments(bankId: BankId, accountId: AccountId) : Boolean
  
}

class RemotedataCommentsCaseClasses {
  case class getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId)
  case class addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date)
  case class deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String)
  case class bulkDeleteComments(bankId: BankId, accountId: AccountId)
}

object RemotedataCommentsCaseClasses extends RemotedataCommentsCaseClasses
