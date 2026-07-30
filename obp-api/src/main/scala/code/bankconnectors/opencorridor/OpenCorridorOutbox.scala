package code.bankconnectors.opencorridor

import net.liftweb.mapper._

/**
 * Transactional outbox for Open Corridor Interface C messages.
 *
 * The settle-pair step commits money movement (the net Transaction, the promise
 * discharges) in one DB transaction; the RabbitMQ publishes must survive a crash
 * between that commit and the publish. So the outbound messages are written as
 * rows here in the SAME transaction, and `OpenCorridorOutboxRelay` publishes
 * them afterwards, recording each reply. Publish-after-commit without this
 * outbox would lose credit notifications and settlement instructions on a crash
 * — with real money that is not acceptable. The Bank Node side is
 * idempotent-friendly (`idempotency_key`, evidence upserts), so redelivery from
 * here is always safe.
 *
 * Row lifecycle:
 *   PENDING   — not yet delivered; the relay keeps publishing with backoff.
 *               For `obp_settlement_instruction` a reply of SUBMITTED/SETTLING
 *               keeps the row PENDING deliberately: redelivery doubles as the
 *               SUBMITTED → FINAL status poll (locked wire contract §4.4).
 *   DELIVERED — the bank replied success (credit notification acked, or
 *               settlement reported FINAL).
 *   STICKY    — the bank replied with a business error that retrying cannot fix
 *               (e.g. COMMITMENT-MISMATCH). Needs operator reconciliation; the
 *               error and full reply are on the row. Never silently swallowed.
 */
class OpenCorridorOutbox extends LongKeyedMapper[OpenCorridorOutbox] with IdPK with CreatedUpdated {
  def getSingleton = OpenCorridorOutbox

  /** The settle event this message belongs to (TR B's transaction request id). */
  object SettlementId extends MappedString(this, 64)
  /** AMQP messageId: obp_credit_notification / obp_settlement_instruction. */
  object MessageId extends MappedString(this, 64)
  /** The bank whose vhost this message is published to. */
  object TargetBankId extends MappedString(this, 255)
  /** The flat lower_snake_case wire body, serialized at settle time. */
  object PayloadJson extends MappedText(this)
  object Status extends MappedString(this, 16) {
    override def defaultValue = OpenCorridorOutbox.STATUS_PENDING
  }
  object Attempts extends MappedInt(this) {
    override def defaultValue = 0
  }
  object LastError extends MappedString(this, 2000)
  /** The bank's last §4.2 reply envelope, verbatim, for audit/reconciliation. */
  object LastReplyJson extends MappedText(this)

  def settlementId: String = SettlementId.get
  def messageId: String = MessageId.get
  def targetBankId: String = TargetBankId.get
  def payloadJson: String = PayloadJson.get
  def status: String = Status.get
  def attempts: Int = Attempts.get
}

object OpenCorridorOutbox extends OpenCorridorOutbox with LongKeyedMetaMapper[OpenCorridorOutbox] {
  val STATUS_PENDING = "PENDING"
  val STATUS_DELIVERED = "DELIVERED"
  val STATUS_STICKY = "STICKY"

  override def dbIndexes: List[BaseIndex[OpenCorridorOutbox]] =
    Index(Status) :: Index(SettlementId) :: super.dbIndexes

  def enqueue(settlementId: String, messageId: String, targetBankId: String, payloadJson: String): OpenCorridorOutbox =
    OpenCorridorOutbox.create
      .SettlementId(settlementId)
      .MessageId(messageId)
      .TargetBankId(targetBankId)
      .PayloadJson(payloadJson)
      .Status(STATUS_PENDING)
      .saveMe()

  def pending(): List[OpenCorridorOutbox] =
    OpenCorridorOutbox.findAll(By(OpenCorridorOutbox.Status, STATUS_PENDING))

  def bySettlementId(settlementId: String): List[OpenCorridorOutbox] =
    OpenCorridorOutbox.findAll(By(OpenCorridorOutbox.SettlementId, settlementId))
}
