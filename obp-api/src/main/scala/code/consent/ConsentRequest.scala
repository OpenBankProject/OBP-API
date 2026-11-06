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
