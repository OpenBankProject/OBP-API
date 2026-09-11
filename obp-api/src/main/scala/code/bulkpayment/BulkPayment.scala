package code.bulkpayment

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

/** One item of a bulk transaction request. */
case class BulkPayment(
  transactionRequestId: String,
  itemIndex: Int,
  endToEndId: String,
  routingScheme: String,
  address: String,
  currency: String,
  amount: String,
  description: String,
  status: String,
  failureReason: Option[String],
  transactionId: Option[String]
) extends BulkPaymentTrait

object BulkPayment {

  private val selectColumns =
    fr"""SELECT transactionrequestid, itemindex, endtoendid, routingscheme, address, currency,
                amount, description, status, failurereason, transactionid
         FROM BulkPayment"""

  private type Row = (Option[String], Option[Int], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String])

  private def fromRow(row: Row): BulkPayment = row match {
    case (transactionRequestId, itemIndex, endToEndId, routingScheme, address, currency,
          amount, description, status, failureReason, transactionId) =>
      BulkPayment(transactionRequestId.orNull, itemIndex.getOrElse(0), endToEndId.orNull,
        routingScheme.orNull, address.orNull, currency.orNull, amount.orNull, description.orNull,
        status.orNull, failureReason, transactionId)
  }

  def insert(transactionRequestId: String, itemIndex: Int, endToEndId: String, routingScheme: String,
             address: String, currency: String, amount: String, description: String, status: String,
             failureReason: Option[String], transactionId: Option[String]): BulkPayment = {
    // failureReason and transactionId are genuinely nullable and bound as Option, so an absent
    // value becomes SQL NULL and reads back as None — matching the Mapper's orNull / Option pair.
    DoobieUtil.runUpdate(
      sql"""INSERT INTO BulkPayment
            (transactionrequestid, itemindex, endtoendid, routingscheme, address, currency,
             amount, description, status, failurereason, transactionid)
            VALUES ($transactionRequestId, $itemIndex, $endToEndId, $routingScheme, $address,
             $currency, $amount, $description, $status, $failureReason, $transactionId)"""
        .update.run)
    BulkPayment(transactionRequestId, itemIndex, endToEndId, routingScheme, address, currency,
      amount, description, status, failureReason, transactionId)
  }

  /** Items of one request, in submission order. */
  def findAllByTransactionRequestId(transactionRequestId: String): List[BulkPayment] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE transactionrequestid = $transactionRequestId ORDER BY itemindex ASC")
        .query[Row].to[List]
    ).map(fromRow)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM BulkPayment".update.run)
    ()
  }
}

/**
 * One row per claimed batch_reference, scoped to a source account.
 * Existence is checked at submission time for idempotency.
 */
object BulkBatchReference {

  def count(fromBankId: String, fromAccountId: String, batchReference: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM BulkBatchReference
            WHERE frombankid = $fromBankId AND fromaccountid = $fromAccountId
              AND batchreference = $batchReference"""
        .query[Long].unique)

  def exists(fromBankId: String, fromAccountId: String, batchReference: String): Boolean =
    count(fromBankId, fromAccountId, batchReference) > 0

  /**
   * Claim a batch reference. The unique index on
   * (frombankid, fromaccountid, batchreference) is what makes this safe: a concurrent duplicate
   * claim is rejected by the database, and the caller's tryo turns that into a Failure. Without
   * the constraint both submissions would be accepted and the batch would execute twice.
   */
  def claim(fromBankId: String, fromAccountId: String, batchReference: String,
            transactionRequestId: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO BulkBatchReference
            (frombankid, fromaccountid, batchreference, transactionrequestid)
            VALUES ($fromBankId, $fromAccountId, $batchReference, $transactionRequestId)"""
        .update.run)
    ()
  }

  def release(fromBankId: String, fromAccountId: String, batchReference: String,
              transactionRequestId: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM BulkBatchReference
            WHERE frombankid = $fromBankId AND fromaccountid = $fromAccountId
              AND batchreference = $batchReference AND transactionrequestid = $transactionRequestId"""
        .update.run)
    ()
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM BulkBatchReference".update.run)
    ()
  }
}

object MappedBulkPaymentProvider extends BulkPaymentProvider {

  override def createBulkPayment(
    transactionRequestId: String,
    itemIndex: Int,
    endToEndId: String,
    routingScheme: String,
    address: String,
    currency: String,
    amount: String,
    description: String,
    status: String,
    failureReason: Option[String],
    transactionId: Option[String]
  ): Box[BulkPaymentTrait] = tryo {
    BulkPayment.insert(transactionRequestId, itemIndex, endToEndId, routingScheme, address,
      currency, amount, description, status, failureReason, transactionId)
  }

  override def getBulkPaymentsForTransactionRequest(transactionRequestId: String): List[BulkPaymentTrait] =
    BulkPayment.findAllByTransactionRequestId(transactionRequestId)

  override def isBatchReferenceUsed(fromBankId: String, fromAccountId: String, batchReference: String): Boolean =
    BulkBatchReference.exists(fromBankId, fromAccountId, batchReference)

  override def claimBatchReference(fromBankId: String, fromAccountId: String, batchReference: String,
                                   transactionRequestId: String): Box[Unit] =
    tryo(BulkBatchReference.claim(fromBankId, fromAccountId, batchReference, transactionRequestId))

  override def releaseBatchReference(fromBankId: String, fromAccountId: String, batchReference: String,
                                     transactionRequestId: String): Unit =
    BulkBatchReference.release(fromBankId, fromAccountId, batchReference, transactionRequestId)
}
