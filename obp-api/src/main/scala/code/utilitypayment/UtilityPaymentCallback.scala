package code.utilitypayment

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

/**
 * Per-request callback registry for UTILITY transaction-requests. When a caller
 * supplies a `callback_url` on a UTILITY payment, OBP persists a row here and
 * fires a fire-and-forget POST of the final result to that URL via
 * [[UtilityCallbackDispatcher]].
 *
 * Distinct from the account-event webhook system (code.webhook.*): those are
 * standing, account-scoped subscriptions; this is a one-shot callback bound to
 * a single transaction request.
 */
object UtilityPaymentCallbacks extends SimpleInjector {
  val utilityPaymentCallback = new Inject(buildOne _) {}

  def buildOne: UtilityPaymentCallbackProvider = MappedUtilityPaymentCallbackProvider
}

object UtilityCallbackStatus {
  val Registered = "REGISTERED"
  val Delivered  = "DELIVERED"
  val Failed     = "FAILED"
}

trait UtilityPaymentCallbackProvider {
  def createCallback(
    callbackId: String,
    transactionRequestId: String,
    callbackUrl: String,
    identifierType: String,
    identifier: String,
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String
  ): Box[UtilityPaymentCallbackTrait]

  def getCallbackByTransactionRequestId(transactionRequestId: String): Box[UtilityPaymentCallbackTrait]

  /** Record a delivery attempt outcome (increments the attempt counter). */
  def recordAttempt(
    callbackId: String,
    status: String,
    responseCode: Option[String]
  ): Box[UtilityPaymentCallbackTrait]
}

trait UtilityPaymentCallbackTrait {
  def callbackId: String
  def transactionRequestId: String
  def callbackUrl: String
  def identifierType: String
  def identifier: String
  def fromBankId: String
  def fromAccountId: String
  def createdByUserId: String
  def status: String
  def attempts: Int
  def responseCode: Option[String]
  def createdAt: java.util.Date
  def lastAttemptAt: Option[java.util.Date]
}

object MappedUtilityPaymentCallbackProvider extends UtilityPaymentCallbackProvider {

  override def createCallback(
    callbackId: String,
    transactionRequestId: String,
    callbackUrl: String,
    identifierType: String,
    identifier: String,
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String
  ): Box[UtilityPaymentCallbackTrait] = tryo {
    UtilityPaymentCallback.create
      .CallbackId(callbackId)
      .TransactionRequestId(transactionRequestId)
      .CallbackUrl(callbackUrl)
      .IdentifierType(identifierType)
      .Identifier(identifier)
      .FromBankId(fromBankId)
      .FromAccountId(fromAccountId)
      .CreatedByUserId(createdByUserId)
      .Status(UtilityCallbackStatus.Registered)
      .Attempts(0)
      .CreationDate(new java.util.Date())
      .saveMe()
  }

  override def getCallbackByTransactionRequestId(transactionRequestId: String): Box[UtilityPaymentCallbackTrait] =
    UtilityPaymentCallback.find(By(UtilityPaymentCallback.TransactionRequestId, transactionRequestId))

  override def recordAttempt(
    callbackId: String,
    status: String,
    responseCode: Option[String]
  ): Box[UtilityPaymentCallbackTrait] =
    UtilityPaymentCallback.find(By(UtilityPaymentCallback.CallbackId, callbackId)).map { row =>
      row
        .Status(status)
        .Attempts(row.Attempts.get + 1)
        .ResponseCode(responseCode.getOrElse(""))
        .LastAttemptDate(new java.util.Date())
        .saveMe()
    }
}

class UtilityPaymentCallback extends UtilityPaymentCallbackTrait with LongKeyedMapper[UtilityPaymentCallback] with IdPK {
  def getSingleton = UtilityPaymentCallback

  object CallbackId extends MappedString(this, 64)
  object TransactionRequestId extends MappedString(this, 64)
  object CallbackUrl extends MappedString(this, 2048)
  object IdentifierType extends MappedString(this, 64)
  object Identifier extends MappedString(this, 255)
  object FromBankId extends MappedString(this, 255)
  object FromAccountId extends MappedString(this, 255)
  object CreatedByUserId extends MappedString(this, 255)
  object Status extends MappedString(this, 32)
  object Attempts extends MappedInt(this)
  object ResponseCode extends MappedString(this, 32)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastAttemptDate extends MappedDateTime(this)

  private def opt(s: String): Option[String] =
    if (s == null || s.isEmpty) None else Some(s)

  override def callbackId: String = CallbackId.get
  override def transactionRequestId: String = TransactionRequestId.get
  override def callbackUrl: String = CallbackUrl.get
  override def identifierType: String = IdentifierType.get
  override def identifier: String = Identifier.get
  override def fromBankId: String = FromBankId.get
  override def fromAccountId: String = FromAccountId.get
  override def createdByUserId: String = CreatedByUserId.get
  override def status: String = Status.get
  override def attempts: Int = Attempts.get
  override def responseCode: Option[String] = opt(ResponseCode.get)
  override def createdAt: java.util.Date = CreationDate.get
  override def lastAttemptAt: Option[java.util.Date] = Option(LastAttemptDate.get)
}

object UtilityPaymentCallback extends UtilityPaymentCallback with LongKeyedMetaMapper[UtilityPaymentCallback] {
  override def dbTableName = "UtilityPaymentCallback"
  override def dbIndexes = UniqueIndex(CallbackId) :: Index(TransactionRequestId) :: super.dbIndexes
}
