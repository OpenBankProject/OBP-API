package code.transaction.internalMapping

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TransactionId
import doobie.implicits._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo

/**
 * Doobie implementation of the transaction-id-mapping store, replacing the Lift
 * TransactionIdMapping entity. Sibling of DoobieAccountIdMappingProvider - same shape, same
 * schema gap (see the migration script and that provider's own comment): the unique indexes are
 * on TransactionId (fresh random UUID per insert, so it never collides) and on
 * (TransactionId, TransactionPlainTextReference), not on TransactionPlainTextReference alone, so
 * two concurrent creates for the same reference do not collide at the database level despite the
 * retry branch below implying otherwise. Not something this migration changes.
 */
object DoobieTransactionIdMappingProvider extends TransactionIdMappingProvider with MdcLoggable {

  override def getOrCreateTransactionId(transactionPlainTextReference: String): Box[TransactionId] = {
    findByReference(transactionPlainTextReference) match {
      case Full(transactionId) =>
        logger.debug(s"getOrCreateTransactionId --> the TransactionIdMapping has been existing in server !")
        Full(transactionId)
      case Empty =>
        val newTransactionId = APIUtil.generateUUID()
        val inserted: Box[Int] = tryo {
          DoobieUtil.runUpdate(
            sql"""INSERT INTO transactionidmapping (transactionid, transactionplaintextreference, createdat, updatedat)
                  VALUES ($newTransactionId, $transactionPlainTextReference, NOW(), NOW())"""
              .update.run)
        }
        inserted match {
          case Full(_) =>
            logger.debug(s"getOrCreateTransactionId--> create mappedTransactionIdMapping : $newTransactionId")
            Full(TransactionId(newTransactionId))
          case Failure(_, _, _) =>
            // Unique-index violation from a concurrent insert — re-fetch the committed row.
            findByReference(transactionPlainTextReference)
          case Empty =>
            findByReference(transactionPlainTextReference)
        }
      case failure => failure
    }
  }

  private def findByReference(transactionPlainTextReference: String): Box[TransactionId] =
    DoobieUtil.runQuery(
      sql"SELECT transactionid FROM transactionidmapping WHERE transactionplaintextreference = $transactionPlainTextReference LIMIT 1"
        .query[String].option
    ) match {
      case Some(id) => Full(TransactionId(id))
      case None     => Empty
    }

  override def getTransactionPlainTextReference(transactionId: TransactionId): Box[String] =
    DoobieUtil.runQuery(
      sql"SELECT transactionplaintextreference FROM transactionidmapping WHERE transactionid = ${transactionId.value} LIMIT 1"
        .query[String].option
    ) match {
      case Some(ref) => Full(ref)
      case None      => Empty
    }
}
