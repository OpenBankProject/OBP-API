/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.retention

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.Date

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import org.json4s._
import org.json4s.native.JsonMethods.{compact, render}

import scala.util.Try

/**
 * The vocabulary of data retention: the resource types a policy can govern, the actions, the date a
 * resource's retention period is counted from, and the run modes and statuses. See RETENTION_PLAN.md.
 */
object Retention {

  val ResourceDynamicEntityRecord = "dynamic_entity_record"
  val ResourceFile = "file"
  val ResourceChatMessage = "chat_message"
  val ResourceChatRoom = "chat_room"
  val ResourceCustomer = "customer"
  val ResourceConsent = "consent"
  val resourceTypes: List[String] =
    List(ResourceDynamicEntityRecord, ResourceFile, ResourceChatMessage, ResourceChatRoom, ResourceCustomer, ResourceConsent)

  val ActionArchive = "archive"
  val ActionDelete = "delete"
  val ActionAnonymise = "anonymise"
  val actions: List[String] = List(ActionArchive, ActionDelete, ActionAnonymise)
  /** The register's action for an archived resource deleted from the archive when its archive period ended. */
  val ActionDeleteFromArchive = "delete_from_archive"

  val BasisCreated = "created"
  val BasisUpdated = "updated"
  val BasisLastActivity = "last_activity"
  val BasisExpires = "expires"
  val BasisFieldPrefix = "field:"

  val ModePreview = "preview"
  val ModeExecute = "execute"
  val modes: List[String] = List(ModePreview, ModeExecute)

  val StatusPending = "pending"
  val StatusRunning = "running"
  val StatusDone = "done"
  val StatusRefused = "refused"
  val StatusFailed = "failed"
  val statuses: List[String] = List(StatusPending, StatusRunning, StatusDone, StatusRefused, StatusFailed)

  val DecisionApprove = "approve"
  val DecisionReject = "reject"
  val DecisionApproved = "approved"
  val DecisionRejected = "rejected"

  /** What an anonymised Customer's legal name becomes, and how a run recognises one it already anonymised. */
  val AnonymisedLegalName = "ANONYMISED"

  /** The longest period a policy may name, in days: a hundred years. */
  val MaxDays = 36500

  val SystemSpace: String = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID

  /**
   * The bank ids a space is stored under, for the tables that do not all use the SYS sentinel. A
   * system chat room is stored with an empty bank id, and a file of the system space may be.
   */
  def storedBankIds(bankId: String): List[String] =
    if (bankId == SystemSpace) List(SystemSpace, "") else List(bankId)

  /** The date bases a policy for this resource type may count from. `field:<name>` is accepted on top for dynamic entity records. */
  def dateBases(resourceType: String): List[String] = resourceType match {
    case ResourceDynamicEntityRecord => List(BasisCreated, BasisUpdated)
    case ResourceFile => List(BasisCreated)
    case ResourceChatMessage => List(BasisCreated, BasisUpdated)
    case ResourceChatRoom => List(BasisCreated, BasisUpdated, BasisLastActivity)
    case ResourceCustomer => List(BasisCreated, BasisUpdated)
    case ResourceConsent => List(BasisCreated, BasisUpdated, BasisExpires)
    case _ => Nil
  }

  def defaultDateBasis(resourceType: String): String =
    if (resourceType == ResourceConsent) BasisExpires else BasisCreated

  def isValidDateBasis(resourceType: String, basis: String): Boolean =
    dateBases(resourceType).contains(basis) ||
      (resourceType == ResourceDynamicEntityRecord && basis.startsWith(BasisFieldPrefix) && basis.stripPrefix(BasisFieldPrefix).trim.nonEmpty)

  def actionsFor(resourceType: String): List[String] =
    if (resourceType == ResourceCustomer) actions else List(ActionArchive, ActionDelete)

  /** The end of the retention period: a resource whose trigger date is on or before this date is due. */
  def cutoff(retainDays: Int, now: Date = new Date()): Date =
    new Date(now.getTime - retainDays.toLong * 86400000L)

  def plusDays(date: Date, days: Int): Date = new Date(date.getTime + days.toLong * 86400000L)

  /**
   * Does the resource match the policy's condition? The field is read from the top level of the
   * resource's JSON (a dynamic entity record's own fields; for the other types, the fields of their
   * JSON copy), and compared with the value as text: `true`, `42` and `CLOSED` all match.
   */
  def conditionMatches(condition: Option[RetentionCondition], view: JValue): Boolean = condition match {
    case None => true
    case Some(RetentionCondition(field, value)) => (view \ field) match {
      case JNothing => false
      case JNull => value == "null"
      case JString(s) => s == value
      case JBool(b) => b.toString == value
      case JInt(i) => i.toString == value
      case JDouble(d) => Try(BigDecimal(value) == BigDecimal(d)).getOrElse(false)
      case JDecimal(d) => Try(BigDecimal(value) == d).getOrElse(false)
      case other => compact(render(other)) == value
    }
  }

  /**
   * The date in a dynamic entity record's field, for a `field:<name>` date basis: an ISO 8601 date
   * (`2026-01-31`, read as midnight UTC) or date and time (`2026-01-31T10:00:00Z`). A record whose
   * field is missing or is not such a date has no trigger date, and no policy counting from that field
   * matches it.
   */
  def dateIn(view: JValue, field: String): Option[Date] = (view \ field) match {
    case JString(s) =>
      val text = s.trim
      Try(Date.from(OffsetDateTime.parse(text).toInstant))
        .orElse(Try(Date.from(Instant.parse(text))))
        .orElse(Try(Date.from(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC))))
        .orElse(Try(Date.from(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant)))
        .toOption
    case _ => None
  }

  def iso(date: Date): JValue = Option(date).map(d => JString(Instant.ofEpochMilli(d.getTime).toString)).getOrElse(JNull)
  def text(value: String): JValue = Option(value).map(JString(_)).getOrElse(JNull)
}

/**
 * The props of data retention. Every value is read when it is used, so a change to the props takes
 * effect without a restart where the props source allows it. All of them are documented in
 * sample.props.template.
 */
object RetentionProps {
  /** Runs that would move resources into the retention archive are refused unless this is true. */
  def archiveEnabled: Boolean = APIUtil.getPropsAsBoolValue("retention.archive.enabled", false)
  /** Runs that would delete or anonymise resources, or delete them from the archive, are refused unless this is true. */
  def deleteEnabled: Boolean = APIUtil.getPropsAsBoolValue("retention.delete.enabled", false)
  /**
   * No resource leaves the live tables, by any action, sooner than this many days after its trigger
   * date: a policy with a shorter retain_days is refused, and an existing one (from before the prop was
   * raised) runs as though its retain_days were this. 1825 days, five years, by default.
   */
  val MinimumRetainDaysDefault = 1825
  def minimumRetainDays: Int = math.max(0, APIUtil.getPropsAsIntValue("retention.minimum_retain_days", MinimumRetainDaysDefault))
  def schedulerEnabled: Boolean = APIUtil.getPropsAsBoolValue("retention.scheduler.enabled", false)
  /** 3593 seconds, a prime just under an hour, so the runs drift across the clock rather than lining up with other schedulers. */
  val SchedulerIntervalInSecondsDefault = 3593L
  def schedulerIntervalInSeconds: Long =
    APIUtil.getPropsAsLongValue("retention.scheduler.interval_in_seconds", SchedulerIntervalInSecondsDefault)
  /** The most resources one run disposes of. What is left is disposed of by the next run. */
  val MaxItemsPerRunDefault = 1000
  def maxItemsPerRun: Int = math.max(1, APIUtil.getPropsAsIntValue("retention.run.max_items", MaxItemsPerRunDefault))
}
