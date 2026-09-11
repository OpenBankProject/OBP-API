package code.webhook

import code.api.util.{APIUtil, DoobieUtil, _}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import scala.collection.immutable.List
import scala.concurrent.Future

/** A bank-scoped account-notification webhook registration. */
case class BankAccountNotificationWebhook(
  webhookId: String,
  bankId: String,
  triggerName: String,
  url: String,
  httpMethod: String,
  httpProtocol: String,
  createdByUserId: String
) extends BankAccountNotificationWebhookTrait

object BankAccountNotificationWebhook {

  private val selectColumns =
    fr"""SELECT webhookid, bankid, triggername, url, httpmethod, httpprotocol, createdbyuserid
         FROM bankaccountnotificationwebhook"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String])

  private def fromRow(row: Row): BankAccountNotificationWebhook = row match {
    case (webhookId, bankId, triggerName, url, httpMethod, httpProtocol, createdByUserId) =>
      BankAccountNotificationWebhook(webhookId.orNull, bankId.orNull, triggerName.orNull,
        url.orNull, httpMethod.orNull, httpProtocol.orNull, createdByUserId.orNull)
  }

  private def query(condition: Fragment): List[BankAccountNotificationWebhook] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(bankId: String, userId: String, triggerName: String, url: String, httpMethod: String,
             httpProtocol: String): BankAccountNotificationWebhook = {
    val webhookId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO bankaccountnotificationwebhook
            (webhookid, bankid, triggername, url, httpmethod, httpprotocol, createdbyuserid,
             createdat, updatedat)
            VALUES ($webhookId, $bankId, $triggerName, $url, $httpMethod, $httpProtocol, $userId,
             $now, $now)"""
        .update.run)
    BankAccountNotificationWebhook(webhookId, bankId, triggerName, url, httpMethod, httpProtocol,
      userId)
  }

  def findById(webhookId: String): Box[BankAccountNotificationWebhook] =
    query(fr"WHERE webhookid = $webhookId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByUserId(userId: String): List[BankAccountNotificationWebhook] =
    query(fr"WHERE createdbyuserid = $userId ORDER BY updatedat DESC, id DESC")

  /** See MappedAccountWebhook.findAllFiltered for why the id ordering is explicit. */
  def findAllFiltered(queryParams: List[OBPQueryParam]): List[BankAccountNotificationWebhook] = {
    val where = queryParams.collectFirst { case OBPUserId(value) => fr"WHERE createdbyuserid = $value" }
      .getOrElse(Fragment.empty)
    val limit = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offset = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    query(where ++ fr"ORDER BY id ASC" ++ limit ++ offset)
  }

  /** The delivery path: every bank-level webhook registered for this trigger. */
  def findAllByBankIdAndTrigger(bankId: String, triggerName: String): List[BankAccountNotificationWebhook] =
    query(fr"WHERE bankid = $bankId AND triggername = $triggerName ORDER BY id ASC")

  def delete(webhookId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM bankaccountnotificationwebhook WHERE webhookid = $webhookId".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountnotificationwebhook".update.run)
    ()
  }
}

object MappedBankAccountNotificationWebhookProvider extends BankAccountNotificationWebhookProvider {

  override def getBankAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[BankAccountNotificationWebhookTrait]] =
    Future(BankAccountNotificationWebhook.findById(webhookId))

  override def getBankAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[BankAccountNotificationWebhookTrait]]] =
    Future(Full(BankAccountNotificationWebhook.findAllByUserId(userId)))

  override def getBankAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[BankAccountNotificationWebhookTrait]]] =
    Future(Full(BankAccountNotificationWebhook.findAllFiltered(queryParams)))

  override def createBankAccountNotificationWebhookFuture(
    bankId: String,
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String,
  ): Future[Box[BankAccountNotificationWebhookTrait]] =
    Future(Full(BankAccountNotificationWebhook.insert(bankId, userId, triggerName, url, httpMethod,
      httpProtocol)))

  override def deleteBankAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] =
    Future(BankAccountNotificationWebhook.findById(webhookId)
      .map(_ => BankAccountNotificationWebhook.delete(webhookId)))
}
