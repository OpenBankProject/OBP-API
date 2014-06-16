package code.metadata.narrative

import code.model.dataAccess.OBPEnvelope
import org.bson.types.ObjectId

object MongoTransactionNarrative extends Narrative {

  def getNarrative(bankId: String, accountId: String, transactionId: String)() : String = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    (for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      env.narrative.get
    }).getOrElse("")
  }

  def setNarrative(bankId: String, accountId: String, transactionId: String)(narrative: String) : Unit = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      env.narrative(narrative).save
    }
  }

}
