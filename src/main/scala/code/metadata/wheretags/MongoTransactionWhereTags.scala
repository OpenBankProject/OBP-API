package code.metadata.wheretags

import java.util.Date
import code.model._
import net.liftweb.common.Loggable

private object MongoTransactionWhereTags extends WhereTags with Loggable {

  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: String, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {


    val newTag = OBPWhereTag.createRecord.
      bankId(bankId.value).
      accountId(accountId.value).
      transactionId(transactionId.value).
      userId(userId).
      forView(viewId.value).
      date(datePosted).
      geoLongitude(longitude).
      geoLatitude(latitude)

    //use an upsert to avoid concurrency issues
    // find query takes into account viewId as we only allow one geotag per view
    OBPWhereTag.upsert(OBPWhereTag.getFindQuery(bankId, accountId, transactionId, viewId), newTag.asDBObject)

    //we don't have any useful information here so just return true
    true
  }

  def deleteWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Boolean = {
    //use delete with find query to avoid concurrency issues
    OBPWhereTag.delete(OBPWhereTag.getFindQuery(bankId, accountId, transactionId, viewId))

    //we don't have any useful information here so just return true
    true
  }

  def getWhereTagsForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)() : List[GeoTag] = {
    OBPWhereTag.findAll(bankId, accountId, transactionId)
  }
}
