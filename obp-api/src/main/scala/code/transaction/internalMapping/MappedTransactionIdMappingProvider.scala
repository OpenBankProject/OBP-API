package code.transaction.internalMapping

import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TransactionId
import net.liftweb.common._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo


object MappedTransactionIdMappingProvider extends TransactionIdMappingProvider with MdcLoggable
{

  override def getOrCreateTransactionId(
    transactionPlainTextReference: String
  ) =
  {

    val transactionIdMapping = TransactionIdMapping.find(
      By(TransactionIdMapping.TransactionPlainTextReference, transactionPlainTextReference)
    )

    transactionIdMapping match
    {
      case Full(vImpl) =>
      {
        logger.debug(s"getOrCreateTransactionId --> the TransactionIdMapping has been existing in server !")
        transactionIdMapping.map(_.transactionId)
      }
      case Empty =>
        tryo {
          TransactionIdMapping
            .create
            .TransactionPlainTextReference(transactionPlainTextReference)
            .saveMe
        } match {
          case Full(m) =>
            logger.debug(s"getOrCreateTransactionId--> create mappedTransactionIdMapping : $m")
            Full(m.transactionId)
          case Failure(_, _, _) =>
            // UniqueIndex violation from concurrent insert — re-fetch the committed row
            TransactionIdMapping.find(
              By(TransactionIdMapping.TransactionPlainTextReference, transactionPlainTextReference)
            ).map(_.transactionId)
          case other => other.map(_.transactionId)
        }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }


  override def getTransactionPlainTextReference(transactionId: TransactionId) = {
    TransactionIdMapping.find(
      By(TransactionIdMapping.TransactionId, transactionId.value),
    ).map(_.transactionPlainTextReference)
  }
}

