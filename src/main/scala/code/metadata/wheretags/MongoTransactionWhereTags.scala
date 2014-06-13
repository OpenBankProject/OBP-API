package code.metadata.wheretags

import java.util.Date
import code.model.GeoTag

object MongoTransactionWhereTags extends WhereTags {

  def addWhereTag(bankId : String, accountId : String, transactionIdGivenByBank: String)
                 (userId: String, viewId : Long, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    //TODO
    false
  }

  def deleteWhereTag(bankId : String, accountId : String, transactionIdGivenByBank: String)(viewId : Long) : Boolean = {
    //TODO
    false
  }

  def getWhereTagsForTransaction(bankId : String, accountId : String, transactionIdGivenByBank: String) : Iterable[GeoTag] = {
    //TODO
    null
  }
}
