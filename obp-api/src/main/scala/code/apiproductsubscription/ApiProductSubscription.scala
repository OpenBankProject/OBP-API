package code.apiproductsubscription

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

import java.util.Date

/**
 * One Consumer holding one API Product for a period, with a status.
 * See API_PRODUCT_SUBSCRIPTION_PLAN.md and the Glossary item "API Product Subscription".
 */
class ApiProductSubscription extends ApiProductSubscriptionTrait with LongKeyedMapper[ApiProductSubscription] with IdPK with CreatedUpdated {
  def getSingleton = ApiProductSubscription

  object ApiProductSubscriptionId extends MappedUUID(this)
  object BankId extends UUIDString(this)
  object ApiProductCode extends MappedString(this, 50)
  // Consumer.consumerId is a MappedString(250); mirror that rather than UUIDString so any existing id fits.
  object ConsumerId extends MappedString(this, 250)
  object Status extends MappedString(this, 20)
  object StartDate extends MappedDateTime(this)
  // null = open-ended
  object EndDate extends MappedDateTime(this)
  object CreatedByUserId extends UUIDString(this)
  // The RateLimiting row Phase 3 creates for this subscription; empty when none.
  object RateLimitingId extends MappedString(this, 50)

  override def apiProductSubscriptionId: String = ApiProductSubscriptionId.get
  override def bankId: String = BankId.get
  override def apiProductCode: String = ApiProductCode.get
  override def consumerId: String = ConsumerId.get
  override def status: String = Status.get
  override def startDate: Date = StartDate.get
  override def endDate: Option[Date] = Option(EndDate.get)
  override def createdByUserId: String = CreatedByUserId.get
  override def rateLimitingId: Option[String] = Option(RateLimitingId.get).filter(_.nonEmpty)
  override def createdAtDate: Date = createdAt.get
  override def updatedAtDate: Date = updatedAt.get
}

object ApiProductSubscription extends ApiProductSubscription with LongKeyedMetaMapper[ApiProductSubscription] {
  // No unique constraint on (ConsumerId, BankId, ApiProductCode): cancelled rows are history.
  // The provider enforces at most one non-cancelled subscription per (consumerId, bankId, apiProductCode).
  override def dbIndexes = UniqueIndex(ApiProductSubscriptionId) :: Index(ConsumerId) :: Index(BankId, ApiProductCode) :: super.dbIndexes
}

trait ApiProductSubscriptionTrait {
  def apiProductSubscriptionId: String
  def bankId: String
  def apiProductCode: String
  def consumerId: String
  def status: String
  def startDate: Date
  def endDate: Option[Date]
  def createdByUserId: String
  def rateLimitingId: Option[String]
  def createdAtDate: Date
  def updatedAtDate: Date
}

/** The status machine. Only the transitions listed here are legal; `cancelled` is terminal. */
object ApiProductSubscriptionStatus {
  val Requested = "requested"
  val Active = "active"
  val PastDue = "past_due"
  val Suspended = "suspended"
  val Cancelled = "cancelled"

  val all: List[String] = List(Requested, Active, PastDue, Suspended, Cancelled)

  def isValid(status: String): Boolean = all.contains(status)

  private val transitions: Map[String, Set[String]] = Map(
    Requested -> Set(Active, Cancelled),
    Active    -> Set(PastDue, Suspended, Cancelled),
    PastDue   -> Set(Active, Suspended, Cancelled),
    Suspended -> Set(Active, Cancelled),
    Cancelled -> Set.empty
  )

  def canTransition(from: String, to: String): Boolean = transitions.get(from).exists(_.contains(to))

  def allowedFrom(from: String): Set[String] = transitions.getOrElse(from, Set.empty)
}
