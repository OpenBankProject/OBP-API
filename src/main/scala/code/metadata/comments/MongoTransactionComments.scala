package code.metadata.comments

import code.model.Comment
import java.util.Date
import net.liftweb.common.{Failure, Full, Box}
import code.model.dataAccess.{OBPComment, OBPEnvelope}
import org.bson.types.ObjectId
import net.liftweb.mongodb.BsonDSL._
import com.mongodb.QueryBuilder

object MongoTransactionComments extends Comments {

  
  def getComments(bankId : String, accountId : String, transactionId : String)() : List[Comment] = {
     OBPComment.findAll(bankId, accountId, transactionId)
  }
  def addComment(bankId : String, accountId : String, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment] = {
    OBPComment.createRecord.userId(userId).
        transactionId(transactionId).
        accountId(accountId).
        bankId(bankId).
        textField(text).
        date(datePosted).
        viewID(viewId).saveTheRecord()
  }

  def deleteComment(bankId : String, accountId : String, transactionId: String)(commentId : String) : Box[Unit] = {
    OBPComment.find(bankId, accountId, transactionId, commentId) match {
      case Full(comment) => {
        if(comment.delete_!) Full()
        else Failure("Delete not completed")
      }
      case _ => Failure("Comment "+commentId+" not found")
    }
  }
  
}