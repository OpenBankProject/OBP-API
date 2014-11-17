package code.metadata.comments

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model._
import java.util.Date

object Comments extends SimpleInjector {

  val comments = new Inject(buildOne _) {}
  
  def buildOne: Comments = MappedComments
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment]
  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, text : String, datePosted : Date) : Box[Comment]
  //TODO: should commentId be unique among all comments, removing the need for the other parameters?
  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Unit]
  
}