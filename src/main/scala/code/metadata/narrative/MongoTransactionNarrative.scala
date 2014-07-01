package code.metadata.narrative

import code.model.dataAccess.{OBPNarrative, OBPEnvelope}
import org.bson.types.ObjectId
import net.liftweb.common.Full

object MongoTransactionNarrative extends Narrative {

  def getNarrative(bankId: String, accountId: String, transactionId: String)() : String = {
    OBPNarrative.find(bankId, accountId, transactionId) match {
      case Full(n) => n.narrative.get
      case _ => ""
    }
  }

  def setNarrative(bankId: String, accountId: String, transactionId: String)(narrative: String) : Unit = {

    OBPNarrative.find(bankId, accountId, transactionId) match {
      case Full(n) => {
        if(narrative.isEmpty) n.delete_! //if we're setting the value of the narrative to "" then we can just delete it
        else n.narrative(narrative).save //otherwise we set it and save it
      }
      case _ => {
        //none exists, we need to create one and save it
        OBPNarrative.createRecord.
          transactionId(transactionId).
          accountId(accountId).
          bankId(bankId).
          narrative(narrative).save
      }
    }
  }

}
