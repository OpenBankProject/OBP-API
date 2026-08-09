package code.messageoutbox

import net.liftweb.mapper._

/**
 * Generic transactional outbox for asynchronous messages OBP-API must deliver.
 *
 * The business event (e.g. an Open Corridor settle) commits in one DB
 * transaction; the outbound messages must survive a crash between that commit
 * and the publish. So they are written as rows here in the SAME transaction,
 * and the relay publishes them afterwards with at-least-once redelivery,
 * recording each reply. Publishing to a broker cannot participate in the DB
 * transaction — this table is what closes that atomicity gap.
 *
 * `outbox_type` discriminates message families; each type contributes its own
 * publish behavior to the relay. Types so far:
 *   OPEN_CORRIDOR — Interface C messages to a bank's RabbitMQ vhost
 *                   (`target_id` = bank_id, publisher = OpenCorridorPublisher).
 *
 * Row lifecycle:
 *   PENDING   — not yet delivered; the relay keeps publishing with backoff.
 *   DELIVERED — the receiver replied success.
 *   STICKY    — the receiver replied with an error that retrying cannot fix.
 *               Needs operator reconciliation: visible via
 *               GET /management/message-outbox, re-queued via its /retry.
 */
class MessageOutbox extends LongKeyedMapper[MessageOutbox] with IdPK {
  def getSingleton = MessageOutbox

  /** Message family; decides how the relay publishes the row. */
  object OutboxType extends MappedString(this, 32) {
    override def dbColumnName = "outbox_type"
  }
  /** The id of the business object this message is about. NOT the
    * per-REST-call Correlation-Id, and not the AMQP reply correlationId. */
  object SubjectId extends MappedString(this, 64) {
    override def dbColumnName = "subject_id"
  }
  /** The OBP id-field name whose value space subject_id belongs to, e.g.
    * transaction_request_id / settlement_id — makes rows self-describing
    * instead of relying on per-operation conventions. */
  object SubjectIdType extends MappedString(this, 32) {
    override def dbColumnName = "subject_id_type"
  }
  /** The operation this message performs, e.g. obp_credit_notification /
    * obp_settlement_advice. On the OPEN_CORRIDOR wire this becomes the AMQP
    * messageId property (locked contract). Named operation_name here to avoid
    * colliding with message_id-as-instance-id elsewhere in OBP (e.g. signal
    * channel messages). */
  object OperationName extends MappedString(this, 64) {
    override def dbColumnName = "operation_name"
  }
  /** Delivery target, per outbox_type (OPEN_CORRIDOR: the bank id whose
    * vhost the message is published to). */
  object TargetId extends MappedString(this, 255) {
    override def dbColumnName = "target_id"
  }
  /** The wire body, serialized at enqueue time. */
  object PayloadJson extends MappedText(this) {
    override def dbColumnName = "payload_json"
  }
  object Status extends MappedString(this, 16) {
    override def dbColumnName = "status"
    override def defaultValue = MessageOutbox.STATUS_PENDING
  }
  object Attempts extends MappedInt(this) {
    override def dbColumnName = "attempts"
    override def defaultValue = 0
  }
  object LastError extends MappedString(this, 2000) {
    override def dbColumnName = "last_error"
  }
  /** The receiver's last reply, verbatim, for audit/reconciliation. */
  object LastReplyJson extends MappedText(this) {
    override def dbColumnName = "last_reply_json"
  }
  /** Per-type optional extras; empty for OPEN_CORRIDOR. */
  object MetadataJson extends MappedText(this) {
    override def dbColumnName = "metadata_json"
  }
  object CreatedAt extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new java.util.Date()
  }
  object UpdatedAt extends MappedDateTime(this) {
    override def dbColumnName = "updated_at"
    override def defaultValue = new java.util.Date()
  }

  def outboxType: String = OutboxType.get
  def subjectId: String = SubjectId.get
  def subjectIdType: String = SubjectIdType.get
  def operationName: String = OperationName.get
  def targetId: String = TargetId.get
  def payloadJson: String = PayloadJson.get
  def status: String = Status.get
  def attempts: Int = Attempts.get

  // updated_at drives the relay's backoff; stamp it on every save.
  override def save: Boolean = {
    UpdatedAt(new java.util.Date())
    super.save
  }
}

object MessageOutbox extends MessageOutbox with LongKeyedMetaMapper[MessageOutbox] {
  val STATUS_PENDING = "PENDING"
  val STATUS_DELIVERED = "DELIVERED"
  val STATUS_STICKY = "STICKY"

  val TYPE_OPEN_CORRIDOR = "OPEN_CORRIDOR"

  // subject_id_type holds the OBP id-field name whose value space subject_id
  // belongs to (exact snake_case field name, e.g. transaction_request_id,
  // settlement_id, consent_id, customer_id ...).
  val SUBJECT_TYPE_SETTLEMENT_ID = "settlement_id"
  val SUBJECT_TYPE_TRANSACTION_REQUEST_ID = "transaction_request_id"

  override def dbTableName = "message_outbox"

  override def dbIndexes: List[BaseIndex[MessageOutbox]] =
    Index(Status) :: Index(SubjectId) :: Index(OutboxType) :: super.dbIndexes

  def enqueue(
    outboxType: String,
    subjectId: String,
    subjectIdType: String,
    operationName: String,
    targetId: String,
    payloadJson: String
  ): MessageOutbox =
    MessageOutbox.create
      .OutboxType(outboxType)
      .SubjectId(subjectId)
      .SubjectIdType(subjectIdType)
      .OperationName(operationName)
      .TargetId(targetId)
      .PayloadJson(payloadJson)
      .Status(STATUS_PENDING)
      .saveMe()

  def pending(): List[MessageOutbox] =
    MessageOutbox.findAll(By(MessageOutbox.Status, STATUS_PENDING))

  def bySubjectId(subjectId: String): List[MessageOutbox] =
    MessageOutbox.findAll(By(MessageOutbox.SubjectId, subjectId))
}
