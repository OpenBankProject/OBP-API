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

package code.metrics

import code.api.cache.Redis
import code.api.Constant
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global

import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * When each Consumer last called the API, kept in Redis only, never in the database.
 *
 * One hash, `{namespace}consumer_last_call`, maps consumer_id to epoch seconds, so one HGETALL
 * answers for every Consumer. It is lost when Redis restarts; `{namespace}consumer_last_call_since`
 * says when the hash started filling, so a reader can tell "no call since then" from "never".
 *
 * Each node writes a Consumer at most once per ThrottleSeconds, so a busy Consumer costs one Redis
 * write a minute per node, not one per call. The write is async and its failure is only logged:
 * the API never waits for, or fails because of, this.
 */
object ConsumerLastCall extends MdcLoggable {

  val ThrottleSeconds = 60

  // consumer_id -> epoch seconds this node last wrote for it
  private val lastWritten = new ConcurrentHashMap[String, java.lang.Long]()

  def hashKey: String = s"${Constant.getGlobalCacheNamespacePrefix}consumer_last_call"
  def sinceKey: String = s"${hashKey}_since"

  case class LastCalls(trackedSince: Option[Date], lastCalls: Map[String, Date])

  /** Called once per API call. Returns the write it started, if the throttle let one through. */
  def record(consumerId: String, at: Date): Option[Future[Unit]] =
    if (consumerId == null || consumerId.isEmpty || at == null) None
    else {
      val seconds = at.getTime / 1000
      if (!claim(consumerId, seconds)) None
      else Some(Future {
        try Redis.hashSetIfGreater(hashKey, sinceKey, consumerId, seconds)
        catch {
          case NonFatal(e) =>
            // Let the next call retry rather than wait out the throttle.
            lastWritten.remove(consumerId, seconds: java.lang.Long)
            logger.debug(s"ConsumerLastCall says: could not record consumer_id $consumerId: ${e.getMessage}")
        }
      })
    }

  /** Atomically take this node's write slot for the Consumer, so concurrent calls write once. */
  private def claim(consumerId: String, seconds: Long): Boolean = {
    val previous = lastWritten.get(consumerId)
    if (previous == null) lastWritten.putIfAbsent(consumerId, seconds) == null
    else if (seconds - previous < ThrottleSeconds) false
    else lastWritten.replace(consumerId, previous, seconds)
  }

  def getAll(): LastCalls = {
    val raw = Redis.hashGetAll(hashKey)
    val since = Redis.use(code.api.JedisMethod.GET, sinceKey)
    def toDate(seconds: String): Option[Date] = scala.util.Try(new Date(seconds.toLong * 1000)).toOption
    LastCalls(
      trackedSince = since.flatMap(toDate),
      lastCalls = raw.flatMap { case (consumerId, seconds) => toDate(seconds).map(consumerId -> _) }
    )
  }

  /** Tests only: forget this node's throttle so the next call for a Consumer writes. */
  private[code] def forgetThrottle(): Unit = lastWritten.clear()
}
