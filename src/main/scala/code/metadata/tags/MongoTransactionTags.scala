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
    //use delete with find query to avoid concurrency issues
    OBPTag.delete(OBPTag.getFindQuery(bankId, accountId, transactionId, tagId))

    //we don't have any useful information here so just assume it worked
    Full()
  }
  
}
