package code.crm

import code.api.util.ErrorMessages._
import code.api.util.DoobieUtil
import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.model.dataAccess.ResourceUser
import code.users.Users
import com.openbankproject.commons.model.BankId
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

import java.util.Date

/** One CRM-event row, standing in for the Lift entity in return types. */
case class CrmEventRow(
  crmEventId: CrmEventId,
  bankId: BankId,
  userIdPrimaryKey: Long,
  customerName: String,
  customerNumber: String,
  category: String,
  detail: String,
  channel: String,
  scheduledDate: Date,
  actualDate: Date,
  result: String
) extends CrmEvent {
  override def user: ResourceUser =
    Users.users.vend.getResourceUserByResourceUserId(userIdPrimaryKey).openOrThrowException(attemptedToOpenAnEmptyBox)
}

/**
 * Doobie implementation of the CRM-event store, replacing the Lift MappedCrmEvent entity.
 *
 * mUserId stores ResourceUser's internal BIGINT primary key (resourceuser.id), resolved back to
 * a live ResourceUser on read via
 * Users.users.vend.getResourceUserByResourceUserId - same as the Mapper entity's own user getter,
 * including throwing when the id doesn't resolve to a row.
 *
 * Three indexes: UNIQUE on mcrmeventid, plain on mbankid and muserid (the last one is Lift's
 * auto-index for the MappedLongForeignKey column, not an explicit dbIndexes entry).
 */
object DoobieCrmEventProvider extends CrmEventProvider {

  // Every column except the primary key is nullable, and the sandbox importer deliberately leaves
  // mUserId, mScheduledDate and mResult unset ("Note: We are not saving API User, Result or
  // Scheduled Date" in LocalMappedConnectorDataImport), so rows written before this store existed
  // hold SQL NULL there. Binding them bare made doobie raise NonNullableColumnRead and fail the
  // whole listing. Each column is collapsed the way its Mapper field read a NULL:
  // MappedLongForeignKey -> 0L, MappedString/MappedDateTime -> null.
  private def rowOf(r: Row): CrmEventRow =
    CrmEventRow(
      crmEventId = CrmEventId(r._1.orNull),
      bankId = BankId(r._2.orNull),
      userIdPrimaryKey = r._3.getOrElse(0L),
      customerName = r._4.orNull,
      customerNumber = r._5.orNull,
      category = r._6.orNull,
      detail = r._7.orNull,
      channel = r._8.orNull,
      scheduledDate = r._9.map(t => new Date(t.getTime)).orNull,
      actualDate = r._10.map(t => new Date(t.getTime)).orNull,
      result = r._11.orNull
    )

  private type Row = (Option[String], Option[String], Option[Long], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[String])

  private val selectCols: Fragment =
    fr"""SELECT mcrmeventid, mbankid, muserid, mcustomername, mcustomernumber, mcategory, mdetail, mchannel, mscheduleddate, mactualdate, mresult
         FROM mappedcrmevent"""

  override protected def getEventsFromProvider(bankId: BankId): Option[List[CrmEvent]] =
    Some(
      DoobieUtil.runQuery((selectCols ++ fr"WHERE mbankid = ${bankId.value}").query[Row].to[List]).map(rowOf)
    )

  override protected def getEventsFromProvider(bankId: BankId, user: ResourceUser): Option[List[CrmEvent]] =
    Some(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${bankId.toString} AND muserid = ${user.userPrimaryKey.value}").query[Row].to[List]
      ).map(rowOf)
    )

  override protected def getEventFromProvider(crmEventId: CrmEventId): Option[CrmEvent] =
    DoobieUtil.runQuery((selectCols ++ fr"WHERE mcrmeventid = ${crmEventId.value} LIMIT 1").query[Row].option).map(rowOf)

  /**
   * Direct create used by the sandbox importer (LocalMappedConnectorDataImport) and by
   * MappedCrmEventProviderTest. userIdPrimaryKey/scheduledDate/result default the same way the
   * Mapper fields did when left unset (0L / epoch / "") - the sandbox importer never sets
   * mUserId, mScheduledDate or mResult (see its "Note: We are not saving API User, Result or
   * Scheduled Date" comment).
   */
  def createEvent(
    bankId: String,
    crmEventId: String,
    category: String,
    detail: String,
    channel: String,
    actualDate: Date,
    customerName: String,
    customerNumber: String,
    userIdPrimaryKey: Long = 0L,
    scheduledDate: Date = new Date(0L),
    result: String = ""
  ): CrmEventRow = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcrmevent
              (mcrmeventid, mbankid, muserid, mcustomername, mcustomernumber, mcategory, mdetail, mchannel,
               mscheduleddate, mactualdate, mresult, createdat, updatedat)
            VALUES ($crmEventId, $bankId, $userIdPrimaryKey, $customerName, $customerNumber, $category, $detail, $channel,
                    ${new java.sql.Timestamp(scheduledDate.getTime)}, ${new java.sql.Timestamp(actualDate.getTime)}, $result,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
        .update.run)
    CrmEventRow(CrmEventId(crmEventId), BankId(bankId), userIdPrimaryKey, customerName, customerNumber, category, detail, channel, scheduledDate, actualDate, result)
  }

  def bulkDelete(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcrmevent".update.run)
    true
  }
}
