package code.bulkpayment

import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

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
    BulkPayment.create
      .TransactionRequestId(transactionRequestId)
      .ItemIndex(itemIndex)
      .EndToEndId(endToEndId)
      .RoutingScheme(routingScheme)
      .Address(address)
      .Currency(currency)
      .Amount(amount)
      .Description(description)
      .Status(status)
      .FailureReason(failureReason.orNull)
      .TransactionId(transactionId.orNull)
      .saveMe()
  }

  override def getBulkPaymentsForTransactionRequest(transactionRequestId: String): List[BulkPaymentTrait] =
    BulkPayment.findAll(
      By(BulkPayment.TransactionRequestId, transactionRequestId),
      OrderBy(BulkPayment.ItemIndex, Ascending)
    ).asInstanceOf[List[BulkPaymentTrait]]

  override def isBatchReferenceUsed(fromBankId: String, fromAccountId: String, batchReference: String): Boolean =
    BulkBatchReference.find(
      By(BulkBatchReference.FromBankId, fromBankId),
      By(BulkBatchReference.FromAccountId, fromAccountId),
      By(BulkBatchReference.BatchReference, batchReference)
    ).isDefined

  override def claimBatchReference(fromBankId: String, fromAccountId: String, batchReference: String, transactionRequestId: String): Box[Unit] =
    tryo {
      BulkBatchReference.create
        .FromBankId(fromBankId)
        .FromAccountId(fromAccountId)
        .BatchReference(batchReference)
        .TransactionRequestId(transactionRequestId)
        .saveMe()
      ()
    }

  override def releaseBatchReference(fromBankId: String, fromAccountId: String, batchReference: String, transactionRequestId: String): Unit =
    BulkBatchReference.find(
      By(BulkBatchReference.FromBankId, fromBankId),
      By(BulkBatchReference.FromAccountId, fromAccountId),
      By(BulkBatchReference.BatchReference, batchReference),
      By(BulkBatchReference.TransactionRequestId, transactionRequestId)
    ).foreach(_.delete_!)
}

class BulkPayment extends BulkPaymentTrait with LongKeyedMapper[BulkPayment] with IdPK {
  def getSingleton = BulkPayment

  object TransactionRequestId extends MappedString(this, 64)
  object ItemIndex extends MappedInt(this)
  object EndToEndId extends MappedString(this, 64)
  object RoutingScheme extends MappedString(this, 64)
  object Address extends MappedString(this, 128)
  object Currency extends MappedString(this, 8)
  object Amount extends MappedString(this, 32)
  object Description extends MappedString(this, 2000)
  object Status extends MappedString(this, 16)
  object FailureReason extends MappedString(this, 1000) {
    override def dbNotNull_? = false
  }
  object TransactionId extends MappedString(this, 64) {
    override def dbNotNull_? = false
  }

  override def transactionRequestId: String = TransactionRequestId.get
  override def itemIndex: Int = ItemIndex.get
  override def endToEndId: String = EndToEndId.get
  override def routingScheme: String = RoutingScheme.get
  override def address: String = Address.get
  override def currency: String = Currency.get
  override def amount: String = Amount.get
  override def description: String = Description.get
  override def status: String = Status.get
  override def failureReason: Option[String] = Option(FailureReason.get)
  override def transactionId: Option[String] = Option(TransactionId.get)
}

object BulkPayment extends BulkPayment with LongKeyedMetaMapper[BulkPayment] {
  override def dbTableName = "BulkPayment"
  override def dbIndexes =
    Index(TransactionRequestId) :: UniqueIndex(TransactionRequestId, ItemIndex) :: super.dbIndexes
}

/** One row per claimed batch_reference, scoped to a source account.
 *  Existence is checked at submission time for idempotency. */
class BulkBatchReference extends LongKeyedMapper[BulkBatchReference] with IdPK {
  def getSingleton = BulkBatchReference

  object FromBankId extends MappedString(this, 255)
  object FromAccountId extends MappedString(this, 255)
  object BatchReference extends MappedString(this, 64)
  object TransactionRequestId extends MappedString(this, 64)
}

object BulkBatchReference extends BulkBatchReference with LongKeyedMetaMapper[BulkBatchReference] {
  override def dbTableName = "BulkBatchReference"
  override def dbIndexes =
    UniqueIndex(FromBankId, FromAccountId, BatchReference) :: super.dbIndexes
}
