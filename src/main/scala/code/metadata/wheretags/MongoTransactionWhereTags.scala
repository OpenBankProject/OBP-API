package code.metadata.wheretags

import java.util.Date
import code.model._
import net.liftweb.common.{Full, Loggable}

private object MongoTransactionWhereTags extends WhereTags with Loggable {

  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {

    //avoiding upsert for now as it seemed to behave a little strangely
    val findQuery = OBPWhereTag.getFindQuery(bankId, accountId, transactionId, viewId)
    val found = OBPWhereTag.find(findQuery)
    found match {
      case Full(f) => {
        f.date(datePosted).
          geoLongitude(longitude).
          geoLatitude(latitude).save(true)
      }
      case _ => {
        OBPWhereTag.createRecord.
          bankId(bankId.value).
          accountId(accountId.value).
          transactionId(transactionId.value).
          userId(userId.value).
          forView(viewId.value).
          date(datePosted).
          geoLongitude(longitude).
          geoLatitude(latitude).save(true)
      }
    }
    //we don't have any useful information here so just return true
    true
  }

  def deleteWhereTag(bankId: BankId, accountId: AccountId, transactionId: TransactionId)(viewId: ViewId): Boolean = {
    //use delete with find query to avoid concurrency issues
    OBPWhereTag.delete(OBPWhereTag.getFindQuery(bankId, accountId, transactionId, viewId))

    //we don't have any useful information here so just return true
    true
  }

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Option[GeoTag] = {
    OBPWhereTag.find(bankId, accountId, transactionId, viewId)
  }
}
