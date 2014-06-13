package code.metadata.comments

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model.Comment
import java.util.Date
import net.liftweb.util.Vendor.funcToVender

object Comments  extends SimpleInjector {

  val comments = new Inject(buildOne _) {}
  
  def buildOne: Comments = MongoTransactionComments
  
}

trait Comments {
  
  def getComments(bankId : String, accountId : String, transactionId : String) : Iterable[Comment]
  def addComment(bankId : String, accountId : String, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment]
  def deleteComment(bankId : String, accountId : String, transactionId: String)(commentId : String) : Box[Unit]
  
}