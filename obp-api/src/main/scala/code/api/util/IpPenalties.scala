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
package code.api.util

import java.util.concurrent.atomic.AtomicReference

import code.api.JedisMethod
import code.api.cache.Redis
import code.util.Helper.MdcLoggable
import com.google.common.net.InetAddresses
import com.openbankproject.commons.util.JsonAliases.{compactRender, parse}
import org.json4s.{Extraction, Formats}

import scala.util.Try

/**
 * This object keeps the list of IP addresses an operator has put under a temporary rate limit, a
 * "penalty", and checks requests against it.
 *
 * It exists for incidents like the NMB scan of 2026-09-23: when one address is hammering the
 * instance, an operator can slow that address right down for a while, on every endpoint, without a
 * restart or a props change. A penalty is a per-minute limit for one address (0 refuses every
 * request) with an expiry time; when it expires it disappears by itself, so nothing stays
 * restricted by mistake. Penalties are always enforced: an operator set them on purpose.
 *
 * Each penalty is one Redis key with a TTL, so every instance sees the same list. Each instance
 * reads it into a local copy at most every [[RefreshMillis]], so checking a request costs a map
 * lookup, not a Redis call, even under attack. Reading fails open: with Redis unreachable there are
 * no penalties, and requests are not refused because of the list.
 *
 * The address is the one `Http4sCallContextBuilder.clientIp` resolves, the same one the other
 * per-IP limits use. Traffic that reaches OBP-API through a proxy which does not pass on the client
 * address shows the proxy's address, and penalising that would restrict every user behind it.
 */
object IpPenalties extends MdcLoggable {

  implicit private val formats: Formats = CustomJsonFormats.formats

  /** One penalty. Times are epoch milliseconds. `perMinuteLimit` 0 refuses every request. */
  final case class Penalty(
    ipAddress: String,
    perMinuteLimit: Long,
    reason: String,
    createdByUserId: String,
    createdAtMillis: Long,
    expiresAtMillis: Long
  )

  val RefreshMillis: Long = 5000L
  val MaxDurationMinutes: Long = 7L * 24 * 60
  val MaxReasonLength: Int = 255

  private def keyPrefix: String = s"${code.api.Constant.getGlobalCacheNamespacePrefix}ip_penalty_"
  private def keyFor(ipAddress: String): String = keyPrefix + ipAddress
  private def counterKeyFor(ipAddress: String): String = s"${keyPrefix}count_$ipAddress"

  /** An IPv4 or IPv6 literal, never a host name (so no lookup happens). Returned in canonical form. */
  def canonicalAddress(value: String): Option[String] =
    Option(value).map(_.trim).filter(v => v.nonEmpty && InetAddresses.isInetAddress(v))
      .map(v => InetAddresses.toAddrString(InetAddresses.forString(v)))

  // ===== The list =====

  private final case class Snapshot(loadedAtMillis: Long, penalties: Map[String, Penalty])
  private val snapshot = new AtomicReference[Snapshot](Snapshot(0L, Map.empty))

  private def readAll(): Map[String, Penalty] =
    Redis.scanKeys(s"$keyPrefix*")
      .filterNot(_.startsWith(s"${keyPrefix}count_"))
      .flatMap(key => Try(Redis.use(JedisMethod.GET, key, None, None)).toOption.flatten)
      .flatMap(json => Try(parse(json).extract[Penalty]).toOption)
      .filter(_.expiresAtMillis > System.currentTimeMillis())
      .map(p => p.ipAddress -> p).toMap

  /** The penalties in force, from the local copy (refreshed at most every [[RefreshMillis]]). */
  def active(): Map[String, Penalty] = {
    val now = System.currentTimeMillis()
    val current = snapshot.get()
    if (now - current.loadedAtMillis < RefreshMillis) current.penalties.filter(_._2.expiresAtMillis > now)
    else {
      val penalties = try readAll() catch {
        case e: Throwable =>
          logger.debug(s"IpPenalties.active says: could not read penalties, treating the list as empty: ${e.getMessage}")
          Map.empty[String, Penalty]
      }
      snapshot.set(Snapshot(now, penalties))
      penalties
    }
  }

  /** Forget the local copy, so the next check reads Redis again (after a change on this instance). */
  def refresh(): Unit = snapshot.set(Snapshot(0L, Map.empty))

  def find(ipAddress: String): Option[Penalty] = canonicalAddress(ipAddress).flatMap(active().get)

  /** Every penalty in Redis now, soonest to expire first. For the management endpoints, which must not show a stale copy. */
  def listAll(): List[Penalty] =
    Try(readAll()).getOrElse(Map.empty[String, Penalty]).values.toList.sortBy(_.expiresAtMillis)

  /** Whether the address has a penalty in Redis now. */
  def exists(ipAddress: String): Boolean =
    canonicalAddress(ipAddress).exists(address => Try(readAll().contains(address)).getOrElse(false))

  /** Adds a penalty. Fails when the address already has one: remove it first to change it. */
  def add(ipAddress: String, perMinuteLimit: Long, durationMinutes: Long, reason: String, createdByUserId: String): Either[String, Penalty] =
    canonicalAddress(ipAddress) match {
      case None => Left(ErrorMessages.InvalidIpAddress)
      case Some(address) if Try(readAll().contains(address)).getOrElse(false) => Left(ErrorMessages.IpPenaltyAlreadyExists)
      case Some(address) =>
        val now = System.currentTimeMillis()
        val penalty = Penalty(address, perMinuteLimit, reason, createdByUserId, now, now + durationMinutes * 60000L)
        Redis.use(JedisMethod.SET, keyFor(address), Some((durationMinutes * 60).toInt), Some(compactRender(Extraction.decompose(penalty))))
        refresh()
        logger.warn(s"IpPenalties.add says: $address limited to $perMinuteLimit per minute for $durationMinutes minutes by user $createdByUserId: $reason")
        Right(penalty)
    }

  /** Removes a penalty. False when the address had none. */
  def remove(ipAddress: String): Boolean =
    canonicalAddress(ipAddress) match {
      case Some(address) if Try(readAll().contains(address)).getOrElse(false) =>
        Redis.use(JedisMethod.DELETE, keyFor(address), None, None)
        Redis.use(JedisMethod.DELETE, counterKeyFor(address), None, None)
        refresh()
        logger.warn(s"IpPenalties.remove says: penalty on $address removed")
        true
      case _ => false
    }

  // ===== Checking a request =====

  /** A request from a penalised address that went over its limit, with seconds until the minute resets. */
  final case class Refusal(penalty: Penalty, retryAfterSeconds: Long)

  /**
   * Counts a request from `ipAddress` against its penalty, if it has one. Returns a refusal when the
   * address is over its per-minute limit; otherwise None. Addresses without a penalty cost a map
   * lookup and nothing else.
   */
  def check(ipAddress: String): Option[Refusal] =
    find(ipAddress).flatMap { penalty =>
      val (ttl, current) =
        if (penalty.perMinuteLimit == 0L) (60L, 1L) // refuse every request; no need to count
        else RateLimitingUtil.incrementCounter(counterKeyFor(penalty.ipAddress), RateLimitingPeriod.PER_MINUTE)
      // current == -1: Redis unavailable for the counter. Fail open, like the other limiters.
      if (current >= 0 && current > penalty.perMinuteLimit) {
        code.telemetry.Telemetry.counter("obp.api.ip_penalties.refused").increment()
        Some(Refusal(penalty, math.max(1L, ttl)))
      } else None
    }

  /** The 429 body text for a refused request. */
  def refusedMessage(refusal: Refusal): String =
    s"${ErrorMessages.TooManyRequestsIpPenalty} This address is limited to ${refusal.penalty.perMinuteLimit} requests per minute until " +
      s"${java.time.Instant.ofEpochMilli(refusal.penalty.expiresAtMillis)}."
}
