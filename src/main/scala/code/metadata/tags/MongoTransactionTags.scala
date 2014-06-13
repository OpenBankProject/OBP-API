package code.metadata.tags

import code.model.Tag
import java.util.Date
import net.liftweb.common.{Failure, Full, Box}
import code.model.dataAccess.{OBPEnvelope, OBPTag}
import org.bson.types.ObjectId

object MongoTransactionTags extends Tags {
  
  def getTags(bankId : String, accountId : String, transactionId: String) : List[Tag] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    val env = OBPEnvelope.find(new ObjectId(transactionId))
    val tags = env.map(e => {
      e.tags.objs
    })

    tags.getOrElse(Nil)
  }
  def addTag(bankId : String, accountId : String, transactionId: String)(userId: String, viewId : Long, tagText : String, datePosted : Date) : Box[Tag] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      val tag = OBPTag.createRecord.
        userId(userId).
        tag(tagText).
        date(datePosted).
        viewID(viewId).save
      env.tags(tag.id.is :: env.tags.get ).save
      tag
    }
  }
  def deleteTag(bankId : String, accountId : String, transactionId: String)(tagId : String) : Box[Unit] = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      OBPTag.find(tagId) match {
        case Full(tag) => {
          if(tag.delete_!){
            env.tags(env.tags.get.diff(Seq(new ObjectId(tagId)))).save
            Full()
          }
          else Failure("Delete not completed")
        }
        case _ => Failure("Tag "+tagId+" not found")
      }
    }
  }
  
}
