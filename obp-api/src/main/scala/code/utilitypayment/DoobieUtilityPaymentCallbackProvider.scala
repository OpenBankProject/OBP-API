package code.utilitypayment

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}

case class UtilityPaymentCallbackRow(
  callbackId: String,
  transactionRequestId: String,
  callbackUrl: String,
  identifierType: String,
  identifier: String,
  fromBankId: String,
  fromAccountId: String,
  createdByUserId: String,
  status: String,
  attempts: Int,
  responseCode: Option[String],
  createdAt: java.util.Date,
  lastAttemptAt: Option[java.util.Date]
) extends UtilityPaymentCallbackTrait

object DoobieUtilityPaymentCallbackProvider extends UtilityPaymentCallbackProvider {

  private def opt(s: String): Option[String] =
    if (s == null || s.isEmpty) None else Some(s)

  private val selectColumns =
    fr"""SELECT callbackid, transactionrequestid, callbackurl, identifiertype, identifier,
                frombankid, fromaccountid, createdbyuserid, status, attempts, responsecode,
                creationdate, lastattemptdate
         FROM utilitypaymentcallback"""

  private def fromRow(row: (String, String, String, String, String, String, String, String, String, Int, String, java.sql.Timestamp, Option[java.sql.Timestamp])): UtilityPaymentCallbackRow =
    row match {
      case (callbackId, transactionRequestId, callbackUrl, identifierType, identifier,
            fromBankId, fromAccountId, createdByUserId, status, attempts, responseCode,
            createdAt, lastAttemptAt) =>
        UtilityPaymentCallbackRow(
          callbackId, transactionRequestId, callbackUrl, identifierType, identifier,
          fromBankId, fromAccountId, createdByUserId, status, attempts, opt(responseCode),
          createdAt, lastAttemptAt)
    }

  override def createCallback(
    callbackId: String,
    transactionRequestId: String,
    callbackUrl: String,
    identifierType: String,
    identifier: String,
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String
  ): Box[UtilityPaymentCallbackTrait] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    try {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO utilitypaymentcallback
              (callbackid, transactionrequestid, callbackurl, identifiertype, identifier,
               frombankid, fromaccountid, createdbyuserid, status, attempts, responsecode, creationdate)
              VALUES
              ($callbackId, $transactionRequestId, $callbackUrl, $identifierType, $identifier,
               $fromBankId, $fromAccountId, $createdByUserId, ${UtilityCallbackStatus.Registered}, 0, '', $now)"""
          .update.run)
      Full(UtilityPaymentCallbackRow(
        callbackId, transactionRequestId, callbackUrl, identifierType, identifier,
        fromBankId, fromAccountId, createdByUserId, UtilityCallbackStatus.Registered, 0, None, now, None))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def getCallbackByTransactionRequestId(transactionRequestId: String): Box[UtilityPaymentCallbackTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE transactionrequestid = $transactionRequestId")
        .query[(String, String, String, String, String, String, String, String, String, Int, String, java.sql.Timestamp, Option[java.sql.Timestamp])]
        .option
    ) match {
      case Some(row) => Full(fromRow(row))
      case None => Empty
    }

  override def recordAttempt(
    callbackId: String,
    status: String,
    responseCode: Option[String]
  ): Box[UtilityPaymentCallbackTrait] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE callbackid = $callbackId")
        .query[(String, String, String, String, String, String, String, String, String, Int, String, java.sql.Timestamp, Option[java.sql.Timestamp])]
        .option
    ) match {
      case Some(row) =>
        val current = fromRow(row)
        val newAttempts = current.attempts + 1
        val now = new java.sql.Timestamp(System.currentTimeMillis())
        DoobieUtil.runUpdate(
          sql"""UPDATE utilitypaymentcallback
                SET status = $status, attempts = $newAttempts, responsecode = ${responseCode.getOrElse("")}, lastattemptdate = $now
                WHERE callbackid = $callbackId"""
            .update.run)
        Full(current.copy(status = status, attempts = newAttempts, responseCode = responseCode, lastAttemptAt = Some(now)))
      case None => Empty
    }
}
