package code.metadata.comments

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model.{BankId, Comment}
import java.util.Date

object Comments extends SimpleInjector {

  val comments = new Inject(buildOne _) {}
  
  def buildOne: Comments = MongoTransactionComments
  
}

trait Comments {
  
  def getComments(bankId : BankId, accountId : String, transactionId : String)() : List[Comment]
  def addComment(bankId : BankId, accountId : String, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment]
  def deleteComment(bankId : BankId, accountId : String, transactionId: String)(commentId : String) : Box[Unit]
  
}