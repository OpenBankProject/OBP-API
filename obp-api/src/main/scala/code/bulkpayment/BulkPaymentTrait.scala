package code.bulkpayment

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object BulkPayments extends SimpleInjector {
  val bulkPayment = new Inject(buildOne _) {}

  def buildOne: BulkPaymentProvider = MappedBulkPaymentProvider
}

trait BulkPaymentProvider {
  /** Append one payment row to a bulk transaction-request. */
  def createBulkPayment(
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
  ): Box[BulkPaymentTrait]

  /** All payment rows for a bulk TR, in item_index order. */
  def getBulkPaymentsForTransactionRequest(transactionRequestId: String): List[BulkPaymentTrait]

  /** True iff any row already exists with the given batch_reference (caller-supplied)
   *  for the same source account — used for idempotency check before insertion. */
  def isBatchReferenceUsed(fromBankId: String, fromAccountId: String, batchReference: String): Boolean

  /** Mark that a batch_reference has been claimed by a TR (separate row in
   *  the batch-references table so the check above is O(1)). */
  def claimBatchReference(fromBankId: String, fromAccountId: String, batchReference: String, transactionRequestId: String): Box[Unit]

  /** Release a previously claimed batch_reference — compensation for a claim whose bulk request
   *  failed BEFORE any payment executed (e.g. the parent TR row could not be created), so the
   *  client can retry the same batch_reference. Scoped to the claiming transactionRequestId so a
   *  release can never delete a claim owned by a different request. */
  def releaseBatchReference(fromBankId: String, fromAccountId: String, batchReference: String, transactionRequestId: String): Unit
}

/** One row per payment inside a bulk TR. */
trait BulkPaymentTrait {
  def transactionRequestId: String
  def itemIndex: Int
  def endToEndId: String
  def routingScheme: String
  def address: String
  def currency: String
  def amount: String
  def description: String
  def status: String              // PENDING | SUCCEEDED | FAILED
  def failureReason: Option[String]
  def transactionId: Option[String]
}
