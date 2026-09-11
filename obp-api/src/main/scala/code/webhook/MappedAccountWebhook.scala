package code.webhook

import code.api.util.{APIUtil, DoobieUtil, _}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

/** A per-account webhook registration. */
case class MappedAccountWebhook(
  accountWebhookId: String,
  bankId: String,
  accountId: String,
  triggerName: String,
  url: String,
  httpMethod: String,
  httpProtocol: String,
  createdByUserId: String,
  private val active: Boolean
) extends AccountWebhook {
  def isActive(): Boolean = active
}

object MappedAccountWebhook {

  private val selectColumns =
    fr"""SELECT maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod,
                mhttpprotocol, mcreatedbyuserid, misactive
         FROM mappedaccountwebhook"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Boolean])

  private def fromRow(row: Row): MappedAccountWebhook = row match {
    case (accountWebhookId, bankId, accountId, triggerName, url, httpMethod, httpProtocol,
          createdByUserId, isActive) =>
      MappedAccountWebhook(accountWebhookId.orNull, bankId.orNull, accountId.orNull,
        triggerName.orNull, url.orNull, httpMethod.orNull, httpProtocol.orNull,
        createdByUserId.orNull, isActive.getOrElse(false))
  }

  private def query(condition: Fragment): List[MappedAccountWebhook] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(bankId: String, accountId: String, userId: String, triggerName: String, url: String,
             httpMethod: String, httpProtocol: String, isActive: Boolean): MappedAccountWebhook = {
    val accountWebhookId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedaccountwebhook
            (maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol,
             mcreatedbyuserid, misactive, createdat, updatedat)
            VALUES ($accountWebhookId, $bankId, $accountId, $triggerName, $url, $httpMethod,
             $httpProtocol, $userId, $isActive, $now, $now)"""
        .update.run)
    MappedAccountWebhook(accountWebhookId, bankId, accountId, triggerName, url, httpMethod,
      httpProtocol, userId, isActive)
  }

  def findById(accountWebhookId: String): Box[MappedAccountWebhook] =
    query(fr"WHERE maccountwebhookid = $accountWebhookId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  // Newest first — updatedat orders this listing, so setActive stamps it.
  def findAllByUserId(userId: String): List[MappedAccountWebhook] =
    query(fr"WHERE mcreatedbyuserid = $userId ORDER BY updatedat DESC, id DESC")

  /**
   * Filters are applied only when supplied, matching the Mapper QueryParam list. An explicit
   * id ordering is added because LIMIT/OFFSET without one is not deterministic; Mapper relied on
   * the database's scan order, which is this.
   */
  def findAllFiltered(queryParams: List[OBPQueryParam]): List[MappedAccountWebhook] = {
    val userId = queryParams.collectFirst { case OBPUserId(value) => fr"mcreatedbyuserid = $value" }
    val bankId = queryParams.collectFirst { case OBPBankId(value) => fr"mbankid = $value" }
    val accountId = queryParams.collectFirst { case OBPAccountId(value) => fr"maccountid = $value" }
    val conditions = List(userId, bankId, accountId).flatten
    val where =
      if (conditions.isEmpty) Fragment.empty
      else fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
    val limit = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offset = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    query(where ++ fr"ORDER BY id ASC" ++ limit ++ offset)
  }

  /** The delivery path: only active webhooks registered for this account and trigger. */
  def findActiveFor(bankId: String, accountId: String, triggerName: String): List[MappedAccountWebhook] =
    query(fr"""WHERE misactive = true AND mbankid = $bankId AND maccountid = $accountId
               AND mtriggername = $triggerName ORDER BY id ASC""")

  def setActive(accountWebhookId: String, isActive: Boolean): Box[MappedAccountWebhook] =
    findById(accountWebhookId).flatMap { _ =>
      DoobieUtil.runUpdate(
        sql"""UPDATE mappedaccountwebhook SET misactive = $isActive,
                updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
              WHERE maccountwebhookid = $accountWebhookId""".update.run)
      findById(accountWebhookId)
    }

  def deleteByBankAccount(bankId: String, accountId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedaccountwebhook WHERE mbankid = $bankId AND maccountid = $accountId"
        .update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountwebhook".update.run)
    ()
  }
}

object MappedAccountWebhookProvider extends AccountWebhookProvider {

  override def getAccountWebhookByIdFuture(accountWebhookId: String): Future[Box[AccountWebhook]] =
    Future(MappedAccountWebhook.findById(accountWebhookId))

  override def getAccountWebhooksByUserIdFuture(userId: String): Future[Box[List[AccountWebhook]]] =
    Future(Full(MappedAccountWebhook.findAllByUserId(userId)))

  override def getAccountWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebhook]]] =
    Future(Full(MappedAccountWebhook.findAllFiltered(queryParams)))

  override def createAccountWebhookFuture(bankId: String,
                                          accountId: String,
                                          userId: String,
                                          triggerName: String,
                                          url: String,
                                          httpMethod: String,
                                          httpProtocol: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] =
    Future(Full(MappedAccountWebhook.insert(bankId, accountId, userId, triggerName, url,
      httpMethod, httpProtocol, isActive)))

  override def updateAccountWebhookFuture(accountWebhookId: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] =
    Future(tryo(MappedAccountWebhook.setActive(accountWebhookId, isActive)).flatMap(identity))
}
