package code.metadata.narrative

import code.model.dataAccess.{OBPNarrative, OBPEnvelope}
import org.bson.types.ObjectId
import net.liftweb.common.Full

object MongoTransactionNarrative extends Narrative {

  def getNarrative(bankId: String, accountId: String, transactionId: String)() : String = {
    OBPNarrative.find(OBPNarrative.getFindQuery(bankId, accountId, transactionId)) match {
      case Full(n) => n.narrative.get
      case _ => ""
    }
  }

  def setNarrative(bankId: String, accountId: String, transactionId: String)(narrative: String) : Unit = {

    val findQuery = OBPNarrative.getFindQuery(bankId, accountId, transactionId)

    if(narrative.isEmpty) {
      //if we're setting the value of the narrative to "" then we can just delete it

      //use delete with find query to avoid concurrency issues
      OBPNarrative.delete(findQuery)
    } else {

      val newNarrative = OBPNarrative.createRecord.
        transactionId(transactionId).
        accountId(accountId).
        bankId(bankId).
        narrative(narrative)

      //use an upsert to avoid concurrency issues
      OBPNarrative.upsert(findQuery, newNarrative.asDBObject)
    }

    //we don't have any useful information here so just assume it worked
    Full()
  }

}
