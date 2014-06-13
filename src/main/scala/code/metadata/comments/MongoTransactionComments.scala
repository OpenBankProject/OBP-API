package code.metadata.comments

import code.model.Comment
import java.util.Date
import net.liftweb.common.{Failure, Full, Box}
import code.model.dataAccess.{OBPComment, OBPEnvelope}
import org.bson.types.ObjectId

object MongoTransactionComments extends Comments {

  
  def getComments(bankId : String, accountId : String, transactionId : String) : Iterable[Comment] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    val env = OBPEnvelope.find(new ObjectId(transactionId))
    val comments = env.map(e => {
      e.obp_comments.objs
    })

    comments.getOrElse(Nil)
  }
  def addComment(bankId : String, accountId : String, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      val comment = OBPComment.createRecord.userId(userId).
        textField(text).
        date(datePosted).
        viewID(viewId).save
      env.obp_comments(comment.id.is :: env.obp_comments.get ).save
      comment
    }
  }

  def deleteComment(bankId : String, accountId : String, transactionId: String)(commentId : String) : Box[Unit] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      OBPComment.find(commentId) match {
        case Full(comment) => {
          if(comment.delete_!){
            env.obp_comments(env.obp_comments.get.diff(Seq(new ObjectId(commentId)))).save
            Full()
          }
          else Failure("Delete not completed")
        }
        case _ => Failure("Comment "+commentId+" not found")
      }
    }
  }
  
}