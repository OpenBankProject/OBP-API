package code.metadata.comments

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Comments extends SimpleInjector {

  val comments = new Inject(buildOne _) {}

  def buildOne: Comments = MappedComments
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[Comment]
  def addComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserPrimaryKey, viewId : ViewId, text : String, datePosted : Date) : Box[Comment]
  //TODO: should commentId be unique among all comments, removing the need for the other parameters?
  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(commentId : String) : Box[Boolean]
  def bulkDeleteComments(bankId: BankId, accountId: AccountId) : Boolean
  def bulkDeleteCommentsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) : Boolean
  
}

