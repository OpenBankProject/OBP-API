package code.accountaccessrequest

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.model.enums.AccountAccessRequestStatus
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

/**
 * One maker/checker request to grant a user a view on an account.
 *
 * `id` (the internal row id) is carried because the atomic status transition keys off it — see
 * updateStatus below.
 */
case class AccountAccessRequest(
  id: Long,
  accountAccessRequestId: String,
  bankId: String,
  accountId: String,
  viewId: String,
  isSystemView: Boolean,
  requestorUserId: String,
  targetUserId: String,
  businessJustification: String,
  status: String,
  checkerUserId: String,
  checkerComment: String,
  created: Date,
  updated: Date
) extends AccountAccessRequestTrait

object AccountAccessRequest {

  private val selectColumns =
    fr"""SELECT id, accountaccessrequestid, bankid, accountid, viewid, issystemview,
                requestoruserid, targetuserid, businessjustification, status,
                checkeruserid, checkercomment, createdat, updatedat
         FROM AccountAccessRequest"""

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[Boolean], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def fromRow(row: Row): AccountAccessRequest = row match {
    case (id, accountAccessRequestId, bankId, accountId, viewId, isSystemView, requestorUserId,
          targetUserId, businessJustification, status, checkerUserId, checkerComment, created, updated) =>
      AccountAccessRequest(id, accountAccessRequestId.orNull, bankId.orNull, accountId.orNull,
        viewId.orNull, isSystemView.getOrElse(false), requestorUserId.orNull, targetUserId.orNull,
        businessJustification.orNull, status.orNull, checkerUserId.orNull, checkerComment.orNull,
        created.orNull, updated.orNull)
  }

  private def query(condition: Fragment): List[AccountAccessRequest] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[AccountAccessRequest] =
    query(condition ++ fr"LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(bankId: String, accountId: String, viewId: String, isSystemView: Boolean,
             requestorUserId: String, targetUserId: String, businessJustification: String): AccountAccessRequest = {
    val newId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val initiated = AccountAccessRequestStatus.INITIATED.toString
    DoobieUtil.runUpdate(
      sql"""INSERT INTO AccountAccessRequest
            (accountaccessrequestid, bankid, accountid, viewid, issystemview, requestoruserid,
             targetuserid, businessjustification, status, checkeruserid, checkercomment,
             createdat, updatedat)
            VALUES ($newId, $bankId, $accountId, $viewId, $isSystemView, $requestorUserId,
             $targetUserId, $businessJustification, $initiated, '', '', $now, $now)"""
        .update.run)
    val id = DoobieUtil.runQuery(
      sql"SELECT id FROM AccountAccessRequest WHERE accountaccessrequestid = $newId".query[Long].unique)
    AccountAccessRequest(id, newId, bankId, accountId, viewId, isSystemView, requestorUserId,
      targetUserId, businessJustification, initiated, "", "", now, now)
  }

  def findByAccountAccessRequestId(accountAccessRequestId: String): Box[AccountAccessRequest] =
    one(fr"WHERE accountaccessrequestid = $accountAccessRequestId")

  /** Newest first, matching the Mapper's OrderBy(id, Descending). */
  def findAllByBankIdAndAccountId(bankId: String, accountId: String): List[AccountAccessRequest] =
    query(fr"WHERE bankid = $bankId AND accountid = $accountId ORDER BY id DESC")

  def findAllByBankIdAccountIdAndStatus(bankId: String, accountId: String, status: String): List[AccountAccessRequest] =
    query(fr"WHERE bankid = $bankId AND accountid = $accountId AND status = $status ORDER BY id DESC")

  def findAllByRequestorUserId(requestorUserId: String): List[AccountAccessRequest] =
    query(fr"WHERE requestoruserid = $requestorUserId ORDER BY id DESC")

  def findInitiatedByUserAccountView(targetUserId: String, bankId: String, accountId: String,
                                     viewId: String): Box[AccountAccessRequest] =
    one(fr"""WHERE targetuserid = $targetUserId AND bankid = $bankId AND accountid = $accountId
             AND viewid = $viewId AND status = ${AccountAccessRequestStatus.INITIATED.toString}""")

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM AccountAccessRequest".update.run)
    ()
  }
}

object MappedAccountAccessRequestProvider extends AccountAccessRequestProvider {

  override def createAccountAccessRequest(
    bankId: String,
    accountId: String,
    viewId: String,
    isSystemView: Boolean,
    requestorUserId: String,
    targetUserId: String,
    businessJustification: String
  ): Box[AccountAccessRequestTrait] = tryo {
    AccountAccessRequest.insert(bankId, accountId, viewId, isSystemView, requestorUserId,
      targetUserId, businessJustification)
  }

  override def getById(accountAccessRequestId: String): Box[AccountAccessRequestTrait] =
    AccountAccessRequest.findByAccountAccessRequestId(accountAccessRequestId)

  override def getByAccount(bankId: String, accountId: String): Box[List[AccountAccessRequestTrait]] =
    tryo(AccountAccessRequest.findAllByBankIdAndAccountId(bankId, accountId))

  override def getByAccountAndStatus(bankId: String, accountId: String, status: String): Box[List[AccountAccessRequestTrait]] =
    tryo(AccountAccessRequest.findAllByBankIdAccountIdAndStatus(bankId, accountId, status))

  override def getByRequestorUserId(requestorUserId: String): Box[List[AccountAccessRequestTrait]] =
    tryo(AccountAccessRequest.findAllByRequestorUserId(requestorUserId))

  override def getByUserAccountView(
    targetUserId: String,
    bankId: String,
    accountId: String,
    viewId: String
  ): Box[AccountAccessRequestTrait] =
    AccountAccessRequest.findInitiatedByUserAccountView(targetUserId, bankId, accountId, viewId)

  override def updateStatus(
    accountAccessRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[AccountAccessRequestTrait] = {
    AccountAccessRequest.findByAccountAccessRequestId(accountAccessRequestId).flatMap { request =>
      // Atomic guarded transition: an access request is actioned once, from INITIATED. The loser of a
      // concurrent approve/decline gets 0 rows -> Failure, instead of silently overwriting the decision.
      val rows = code.bankconnectors.DoobieBusinessStatusQueries.conditionalAccountAccessRequestStatus(
        request.id, AccountAccessRequestStatus.INITIATED.toString, status, checkerUserId, checkerComment)
      if (rows == 1) AccountAccessRequest.findByAccountAccessRequestId(accountAccessRequestId)
      else Failure(ErrorMessages.AccountAccessRequestStatusNotInitiated)
    }
  }
}
