package code.consent

import code.api.util.{APIUtil, DoobieUtil}
import code.model.Consumer
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

object MappedConsentRequestProvider extends ConsentRequestProvider {

  override def getConsentRequestById(consentRequestId: String): Box[ConsentRequest] =
    ConsentRequest.findByConsentRequestId(consentRequestId)

  override def createConsentRequest(consumer: Option[Consumer], payload: Option[String]): Box[ConsentRequest] =
    // The consumer is genuinely optional and the column is nullable, so an absent one is stored as
    // NULL rather than "". An absent payload is stored as "", which is what Mapper did.
    tryo(ConsentRequest.insert(consumer.map(_.consumerId), payload.getOrElse("")))
}

/**
 * A request for a consent, saved before the consent exists.
 *
 * The whole request body is kept verbatim in `payload`; when the consent is finally created it is
 * built from that JSON, so this row is the record of what was actually asked for.
 *
 * `consumerId` may be null - it names the application that asked, and calls without a consumer
 * attached leave it unset.
 */
case class ConsentRequest(
  consentRequestId: String,
  payload: String,
  consumerId: String
) extends ConsentRequestTrait

object ConsentRequest {

  private val selectColumns =
    fr"SELECT consentrequestid, payload, consumerid FROM consentrequest"

  private type Row = (Option[String], Option[String], Option[String])

  private def fromRow(row: Row): ConsentRequest = row match {
    case (consentRequestId, payload, consumerId) =>
      ConsentRequest(consentRequestId.orNull, payload.orNull, consumerId.orNull)
  }

  private def query(condition: Fragment): List[ConsentRequest] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findByConsentRequestId(consentRequestId: String): Box[ConsentRequest] =
    query(fr"WHERE consentrequestid = ${Option(consentRequestId)} LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(consumerId: Option[String], payload: String): ConsentRequest = {
    val consentRequestId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    // consumerId arrives as Option and is bound as one: a caller with no consumer, and a consumer
    // whose id is itself null, both have to reach the column as SQL NULL rather than throw.
    DoobieUtil.runUpdate(
      sql"""INSERT INTO consentrequest
            (consentrequestid, payload, consumerid, createdat, updatedat)
            VALUES ($consentRequestId, ${Option(payload)}, ${consumerId.flatMap(Option(_))},
             $now, $now)"""
        .update.run)
    ConsentRequest(consentRequestId, payload, consumerId.flatMap(Option(_)).orNull)
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM consentrequest".update.run)
    ()
  }
}
