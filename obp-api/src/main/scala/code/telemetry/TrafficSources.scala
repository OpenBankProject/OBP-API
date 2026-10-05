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
package code.telemetry

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable

/**
 * This object answers "where is the traffic on this instance coming from, right now?": the busiest
 * Consumers, the busiest client IP addresses, and the busiest pairs of caller and endpoint, over the
 * last 1, 5 or 15 minutes.
 *
 * It is not Telemetry in the Prometheus sense and is never exported there: it names Consumers and
 * IP addresses, which must not become series (docs/telemetry_conventions.md, section 6). It is read
 * only through a Role-gated endpoint, lives only in this instance's memory, and forgets everything
 * after 15 minutes. It needs no database, so it keeps working when API Metrics are off or the
 * database is the thing under strain.
 *
 * Memory is bounded whatever the traffic: each minute has three [[HeavyHitters]] tables (200
 * Consumers, 200 addresses, 500 caller and endpoint pairs), and 15 minutes are kept. Counts are
 * estimates with a stated bound; see HeavyHitters for the algorithm (Space-Saving) and its authors.
 *
 * Every request is recorded once, at the outermost layer (Http4sApp), which sees refused and
 * unmatched requests as well as served ones. Inner layers fill in a [[Note]] carried as a request
 * attribute: ResourceDocMiddleware and the resource-docs routes set the endpoint and, after
 * authentication, the Consumer; the IP limiters set a refusal.
 */
object TrafficSources {

  val ConsumerSlots = 200
  val AddressSlots = 200
  val CallerEndpointSlots = 500
  val MinutesKept = 15
  val EndpointsKeptPerCaller = 32
  val ConsumersKeptPerAddress = 8
  val AddressesKeptPerConsumer = 16
  val UsersKeptPerConsumer = 16
  val PeersKeptPerMinute = 64

  // ===== What inner layers tell the recording point =====

  /** Filled in by inner layers while a request is served; read once when it is recorded. */
  final class Note {
    @volatile var operationId: Option[String] = None
    @volatile var apiVersion: Option[String] = None
    @volatile var consumerId: Option[String] = None
    @volatile var consumerName: Option[String] = None
    @volatile var userId: Option[String] = None
    @volatile var refusedBy: Option[String] = None
  }

  // ===== Keys =====

  sealed trait Caller { def kind: String; def value: String }
  final case class ConsumerCaller(consumerId: String) extends Caller { val kind = "consumer"; def value: String = consumerId }
  final case class AddressCaller(ipAddress: String) extends Caller { val kind = "ip"; def value: String = ipAddress }

  /** Requests to paths no endpoint serves are grouped under one endpoint: scanners send endless distinct paths. */
  val UnmatchedEndpoint = "unmatched"
  /** Requests that no documented endpoint answered and that were not a 404, e.g. status pages and CORS preflight. */
  val OtherEndpoint = "other"

  // ===== Details kept per entry =====

  class StatusCounts {
    var status2xx = 0L; var status4xx = 0L; var status5xx = 0L; var refused = 0L; var unmatched = 0L
    def add(status: Int, isRefused: Boolean, isUnmatched: Boolean): Unit = {
      if (status >= 200 && status < 300) status2xx += 1
      else if (status >= 400 && status < 500) status4xx += 1
      else if (status >= 500) status5xx += 1
      if (isRefused) refused += 1
      if (isUnmatched) unmatched += 1
    }
  }

  final class ConsumerDetails extends StatusCounts {
    var name = ""
    val endpoints = mutable.LinkedHashSet.empty[String]
    // For Deployment Checks: an application calling for many users from one address does not pass
    // on its users' addresses.
    val addresses = mutable.LinkedHashSet.empty[String]
    val users = mutable.LinkedHashSet.empty[String]
    var lastIp = ""
    var firstSeen = 0L; var lastSeen = 0L
  }

  final class AddressDetails extends StatusCounts {
    val endpoints = mutable.LinkedHashSet.empty[String]
    val consumers = mutable.LinkedHashSet.empty[String]
    var firstSeen = 0L; var lastSeen = 0L
  }

  final class CallerEndpointDetails extends StatusCounts {
    var apiVersion = ""
    var totalDurationMillis = 0L; var maxDurationMillis = 0L
    var lastSeen = 0L
  }

  /**
   * How the minute's client addresses were decided (for Deployment Checks): whether a forwarding
   * header came with the request, whether it was believed, and which TCP peers sent requests.
   */
  final class ForwardingCounts {
    var requests = 0L
    var withForwardingHeader = 0L
    var headerHonoured = 0L
    var headerFromUntrustedPeer = 0L
    val peers = mutable.LinkedHashSet.empty[String]
    val peersSendingHeader = mutable.LinkedHashSet.empty[String]

    def add(resolution: code.api.util.RemoteIpUtil.Resolution): Unit = synchronized {
      requests += 1
      if (resolution.forwardingHeaderPresent) withForwardingHeader += 1
      if (resolution.headerHonoured) headerHonoured += 1
      if (resolution.headerFromUntrustedPeer) headerFromUntrustedPeer += 1
      addCapped(peers, resolution.socketPeer, PeersKeptPerMinute)
      if (resolution.forwardingHeaderPresent) addCapped(peersSendingHeader, resolution.socketPeer, PeersKeptPerMinute)
    }
  }

  // ===== One minute =====

  final class Minute(val startMillis: Long) {
    val consumers = new HeavyHitters[String, ConsumerDetails](ConsumerSlots, () => new ConsumerDetails)
    val addresses = new HeavyHitters[String, AddressDetails](AddressSlots, () => new AddressDetails)
    val callerEndpoints = new HeavyHitters[(Caller, String), CallerEndpointDetails](CallerEndpointSlots, () => new CallerEndpointDetails)
    val forwarding = new ForwardingCounts
  }

  private def minuteStart(millis: Long): Long = millis - millis % 60000L

  private val minutes = new AtomicReference[List[Minute]](Nil) // newest first

  private def minuteFor(now: Long): Minute = {
    val start = minuteStart(now)
    minutes.get() match {
      case current :: _ if current.startMillis == start => current
      case _ => synchronized {
        minutes.get() match {
          case current :: _ if current.startMillis == start => current
          case kept =>
            val fresh = new Minute(start)
            minutes.set((fresh :: kept).filter(_.startMillis > start - MinutesKept * 60000L))
            fresh
        }
      }
    }
  }

  private def addCapped(set: mutable.LinkedHashSet[String], value: String, limit: Int): Unit =
    if (set.contains(value) || set.size < limit) set += value

  /**
   * Records one request. `note` is what inner layers filled in; `status` and `durationMillis` are the
   * response's. A request counts under its Consumer when one was authenticated, and always under its
   * client address.
   */
  def record(note: Note, resolution: code.api.util.RemoteIpUtil.Resolution, status: Int, durationMillis: Long,
             now: Long = System.currentTimeMillis()): Unit = {
    val minute = minuteFor(now)
    minute.forwarding.add(resolution)
    val ipAddress = resolution.clientIp
    val isRefused = note.refusedBy.isDefined || status == 429
    val endpoint = note.refusedBy.map(limiter => s"refused:$limiter")
      .orElse(note.operationId)
      .getOrElse(if (status == 404) UnmatchedEndpoint else OtherEndpoint)
    val isUnmatched = endpoint == UnmatchedEndpoint
    val address = Option(ipAddress).filter(_.nonEmpty).getOrElse("unknown")

    note.consumerId.foreach { consumerId =>
      minute.consumers.offer(consumerId) { d =>
        d.add(status, isRefused, isUnmatched)
        note.consumerName.foreach(d.name = _)
        addCapped(d.endpoints, endpoint, EndpointsKeptPerCaller)
        d.lastIp = address
        addCapped(d.addresses, address, AddressesKeptPerConsumer)
        note.userId.foreach(addCapped(d.users, _, UsersKeptPerConsumer))
        if (d.firstSeen == 0L) d.firstSeen = now
        d.lastSeen = now
      }
    }
    minute.addresses.offer(address) { d =>
      d.add(status, isRefused, isUnmatched)
      addCapped(d.endpoints, endpoint, EndpointsKeptPerCaller)
      note.consumerId.foreach(addCapped(d.consumers, _, ConsumersKeptPerAddress))
      if (d.firstSeen == 0L) d.firstSeen = now
      d.lastSeen = now
    }
    val caller: Caller = note.consumerId.map(ConsumerCaller(_)).getOrElse(AddressCaller(address))
    minute.callerEndpoints.offer((caller, endpoint)) { d =>
      d.add(status, isRefused, isUnmatched)
      note.apiVersion.foreach(d.apiVersion = _)
      d.totalDurationMillis += durationMillis
      d.maxDurationMillis = math.max(d.maxDurationMillis, durationMillis)
      d.lastSeen = now
    }
  }

  // ===== Reading: merging the minutes of a window =====

  /**
   * One key's totals over a window. The true number of requests is within `error` of `requests`:
   * `error` adds up each minute's overcount for the key and, for each full minute table the key was
   * missing from, that minute's smallest count (the key may have been there below it).
   */
  final case class Merged[K, D](key: K, requests: Long, error: Long, details: List[D])

  private def merge[K, D](tables: List[HeavyHitters[K, D]]): List[Merged[K, D]] = {
    val snapshots = tables.map(t => (t, t.snapshot.map(e => e.key -> e).toMap, t.isFull, t.minCount))
    val keys = snapshots.flatMap(_._2.keys).distinct
    keys.map { key =>
      var requests = 0L; var error = 0L; val details = List.newBuilder[D]
      snapshots.foreach { case (_, byKey, full, min) =>
        byKey.get(key) match {
          case Some(entry) => requests += entry.count; error += entry.error; details += entry.details
          case None if full => error += min
          case None => ()
        }
      }
      Merged(key, requests, error, details.result())
    }.sortBy(m => -m.requests)
  }

  /** The window's minutes, newest first, and the time its oldest minute started. */
  private def window(minutesBack: Int, now: Long): List[Minute] = {
    val from = minuteStart(now) - (minutesBack - 1) * 60000L
    minutes.get().filter(_.startMillis >= from)
  }

  def consumers(minutesBack: Int, now: Long = System.currentTimeMillis()): List[Merged[String, ConsumerDetails]] =
    merge(window(minutesBack, now).map(_.consumers))

  def addresses(minutesBack: Int, now: Long = System.currentTimeMillis()): List[Merged[String, AddressDetails]] =
    merge(window(minutesBack, now).map(_.addresses))

  def callerEndpoints(minutesBack: Int, now: Long = System.currentTimeMillis()): List[Merged[(Caller, String), CallerEndpointDetails]] =
    merge(window(minutesBack, now).map(_.callerEndpoints))

  /** The forwarding counts of the window's minutes, newest first. */
  def forwarding(minutesBack: Int, now: Long = System.currentTimeMillis()): List[ForwardingCounts] =
    window(minutesBack, now).map(_.forwarding)

  /** Forget everything (tests). */
  def clear(): Unit = minutes.set(Nil)
}
