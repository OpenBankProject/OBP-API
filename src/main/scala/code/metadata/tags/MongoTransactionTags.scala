package code.metadata.tags

import code.model.Tag
import java.util.Date
import net.liftweb.common.{Failure, Full, Box}
import code.model.dataAccess.{OBPEnvelope, OBPTag}
import org.bson.types.ObjectId

object MongoTransactionTags extends Tags {
  
  def getTags(bankId : String, accountId : String, transactionId: String)() : List[Tag] = {
    OBPTag.findAll(bankId, accountId, transactionId)
  }
  def addTag(bankId : String, accountId : String, transactionId: String)(userId: String, viewId : Long, tagText : String, datePosted : Date) : Box[Tag] = {
    OBPTag.createRecord.
      bankId(bankId).
      accountId(accountId).
      transactionId(transactionId).
      userId(userId).
      viewID(viewId).
      tag(tagText).
      date(datePosted).saveTheRecord()
  }
  def deleteTag(bankId : String, accountId : String, transactionId: String)(tagId : String) : Box[Unit] = {
    OBPTag.find(bankId, accountId, transactionId, tagId) match {
      case Full(tag) => {
        if(tag.delete_!) Full()
        else Failure("Delete not completed")
      }
      case _ => Failure("Tag "+tagId+" not found")
    }
  }
  
}
