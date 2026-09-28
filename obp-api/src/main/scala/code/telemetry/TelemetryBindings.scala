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

import code.api.cache.RedisLogger
import code.api.util.JsonSchemaGenerator
import code.api.v2_2_0.MessageDocsJsonCache
import code.util.{Helper, SecureLogging}

/**
 * This object registers, with Telemetry, counters and levels that other parts of OBP-API already
 * keep for themselves (mostly `AtomicLong` counters with public getters, written before Telemetry
 * existed).
 *
 * It reads them through their getters when Telemetry is collected, so the code that counts does
 * not change and pays nothing extra. Registering them here, in one list, rather than in each
 * object, also keeps Telemetry's start-up from initialising those objects in an awkward order:
 * Helper, in particular, is initialised by almost everything.
 */
object TelemetryBindings {

  def bindAll(): Unit = {
    bindLogging()
    bindMessageDocs()
    bindRedisLogger()
    bindIpPenalties()
  }

  /** How many addresses are under an operator's temporary limit. Never which ones: an address is not a tag. */
  private def bindIpPenalties(): Unit =
    Telemetry.gauge("obp.api.ip_penalties.active")(code.api.util.IpPenalties.active().size.toDouble)

  /** The log dispatch pool (Helper.MdcLoggable) and log masking. */
  private def bindLogging(): Unit = {
    Telemetry.gauge("obp.api.log.dispatch.queue.depth")(Helper.mdcLogQueueDepth.toDouble)
    Telemetry.functionCounter("obp.api.log.dispatch.entries", "result" -> "dispatched")(Helper.mdcLogDispatchedCount)
    Telemetry.functionCounter("obp.api.log.dispatch.entries", "result" -> "dropped")(Helper.mdcLogDroppedCount)
    Telemetry.functionCounter("obp.api.log.dispatch.entries", "result" -> "inline")(Helper.mdcLogInlineCount)
    Telemetry.functionCounter("obp.api.log.masking.calls")(SecureLogging.maskCalls)
  }

  /** The connector JSON Schema and the v2.2.0 message-docs response, which are built once and cached. */
  private def bindMessageDocs(): Unit = {
    Telemetry.functionCounter("obp.api.json_schema.generations")(JsonSchemaGenerator.generatorCalls)
    Telemetry.functionCounter("obp.api.message_docs.generations")(MessageDocsJsonCache.generatorCalls)
    Telemetry.functionCounter("obp.api.message_docs.shared.gets", "result" -> "hit")(MessageDocsJsonCache.sharedHits)
    Telemetry.functionCounter("obp.api.message_docs.shared.gets", "result" -> "miss")(
      MessageDocsJsonCache.sharedGets - MessageDocsJsonCache.sharedHits)
    Telemetry.functionCounter("obp.api.message_docs.shared.sets")(MessageDocsJsonCache.sharedSets)
  }

  /** Shipping of log entries to Redis. */
  private def bindRedisLogger(): Unit =
    Telemetry.gauge("obp.api.redis_logger.consecutive_failures")(RedisLogger.consecutiveFailureCount.toDouble)
}
