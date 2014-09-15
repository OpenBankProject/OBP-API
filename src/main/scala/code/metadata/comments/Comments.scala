package code.metadata.comments

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model.{AccountId, BankId, Comment}
import java.util.Date

object Comments extends SimpleInjector {

  val comments = new Inject(buildOne _) {}
  
  def buildOne: Comments = MongoTransactionComments
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : AccountId, transactionId : String)() : List[Comment]
  def addComment(bankId : BankId, accountId : AccountId, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment]
  def deleteComment(bankId : BankId, accountId : AccountId, transactionId: String)(commentId : String) : Box[Unit]
  
}