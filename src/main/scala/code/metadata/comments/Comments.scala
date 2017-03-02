package code.metadata.comments

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model._
import java.util.Date

import code.remotedata.Remotedata

object Comments extends SimpleInjector {

  val comments = new Inject(buildOne _) {}
  
  def buildOne: Comments = MappedComments
  //def buildOne: Comments = Remotedata
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment]
  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, text : String, datePosted : Date) : Box[Comment]
  //TODO: should commentId be unique among all comments, removing the need for the other parameters?
  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean]
  def bulkDeleteComments(bankId: BankId, accountId: AccountId) : Boolean
  
}

class RemoteCommentsCaseClasses {
  case class getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId, viewId : ViewId)
  case class addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, userId: UserId, viewId : ViewId, text : String, datePosted : Date)
  case class deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId, commentId : String)
  case class bulkDeleteComments(bankId: BankId, accountId: AccountId)
}

object RemoteCommentsCaseClasses extends RemoteCommentsCaseClasses