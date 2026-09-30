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

import java.sql.Timestamp
import java.util.Date
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * Batched metric writer that uses the Doobie connection pool instead of Lift's pool.
 *
 * Metrics are enqueued in memory and flushed to the database periodically or when
 * the queue reaches a configurable threshold. This prevents metric writes from
 * competing with API request handling for Lift/HikariPool-1 connections.
 *
 * Configuration:
 *   - metrics.batch.interval.seconds: flush interval (default: 5)
 */
object MetricBatchWriter extends MdcLoggable {

  case class MetricRow(
    userId: String,
    url: String,
    date: Date,
    duration: Long,
    userName: String,
    appName: String,
    developerEmail: String,
    consumerId: String,
    implementedByPartialFunction: String,
    implementedInVersion: String,
    verb: String,
    httpCode: Int,
    correlationId: String,
    responseBody: String,
    sourceIp: String,
    targetIp: String,
    forwardedFor: String,
    apiInstanceId: String,
    consentReferenceId: String,
    certificateTrust: String,
    certificateTrustDetail: String,
    authType: String
  )

  private val queue = new ConcurrentLinkedQueue[MetricRow]()

  private val flushIntervalSeconds = APIUtil.getPropsAsLongValue("metrics.batch.interval.seconds", 5L)

  private val started = new AtomicBoolean(false)

  /**
   * Start the background flush scheduler. Safe to call multiple times; only the first call starts it.
   */
  def start(): Unit = {
    if (started.compareAndSet(false, true)) {
      val scheduler = Executors.newSingleThreadScheduledExecutor { r =>
        val t = new Thread(r, "metric-batch-writer")
        t.setDaemon(true)
        t
      }
      scheduler.scheduleWithFixedDelay(
        () => flush(),
        flushIntervalSeconds,
        flushIntervalSeconds,
        TimeUnit.SECONDS
      )
      logger.info(s"MetricBatchWriter says: started (flushInterval=${flushIntervalSeconds}s)")
    }
  }

  /**
   * Enqueue a metric for batched writing. Never blocks the calling thread.
   * The background scheduler handles all flushing.
   */
  // Rows queued, written and lost, and the queue depth, for Telemetry.
  private lazy val telemetry = new code.telemetry.BatchWriterTelemetry("api_metrics")

  def enqueue(row: MetricRow): Unit = {
    queue.add(fitToColumns(row))
    telemetry.queued()
  }

  /**
   * This cuts each text value of a row to the width of its column in the metric table.
   *
   * The rows of one flush are inserted as a single batch, and the database rejects a value that
   * is longer than its column ("value too long for type character varying(N)"). One such value
   * would therefore lose every metric in the flush, not only its own. Several values come from
   * the caller (X-Forwarded-For, X-Forwarded-Host, the correlation id, the URL), so without this
   * any caller could make everyone's metrics disappear with one long header. The widths are read
   * from MappedMetric, so they cannot drift from the table definition.
   */
  private[metrics] def fitToColumns(row: MetricRow): MetricRow = {
    def fit(value: String, column: net.liftweb.mapper.MappedString[MappedMetric]): String =
      if (value != null && value.length > column.maxLen) value.substring(0, column.maxLen) else value
    val table = MappedMetric
    row.copy(
      userId = fit(row.userId, table.userId),
      url = fit(row.url, table.url),
      userName = fit(row.userName, table.userName),
      appName = fit(row.appName, table.appName),
      developerEmail = fit(row.developerEmail, table.developerEmail),
      consumerId = fit(row.consumerId, table.consumerId),
      implementedByPartialFunction = fit(row.implementedByPartialFunction, table.implementedByPartialFunction),
      implementedInVersion = fit(row.implementedInVersion, table.implementedInVersion),
      verb = fit(row.verb, table.verb),
      correlationId = fit(row.correlationId, table.correlationId),
      sourceIp = fit(row.sourceIp, table.sourceIp),
      targetIp = fit(row.targetIp, table.targetIp),
      forwardedFor = fit(row.forwardedFor, table.forwardedFor),
      apiInstanceId = fit(row.apiInstanceId, table.apiInstanceId),
      consentReferenceId = fit(row.consentReferenceId, table.consentReferenceId),
      certificateTrust = fit(row.certificateTrust, table.certificateTrust),
      certificateTrustDetail = fit(row.certificateTrustDetail, table.certificateTrustDetail),
      authType = fit(row.authType, table.authType)
    )
  }

  /**
   * Drain the queue and batch-insert all pending metrics via Doobie.
   */
  private[code] def flush(): Unit = {
    val flushStart = System.nanoTime()
    // Rows taken off the queue by this flush. If the write fails they are lost, so Telemetry counts them.
    var drainedRows = 0
    try {
      val batch = new java.util.ArrayList[MetricRow]()
      var item = queue.poll()
      while (item != null) {
        batch.add(item)
        item = queue.poll()
      }
      drainedRows = batch.size()

      if (!batch.isEmpty) {
        val rows = {
          val buf = scala.collection.mutable.ListBuffer.empty[MetricRow]
          val it = batch.iterator()
          while (it.hasNext) buf += it.next()
          buf.toList
        }

        val insertSql = """
          INSERT INTO metric (
            userid, url, date_c, duration, username, appname,
            developeremail, consumerid, implementedbypartialfunction,
            implementedinversion, verb, httpcode, correlationid,
            responsebody, sourceip, targetip, forwarded_for, apiinstanceid, consent_reference_id,
            certificate_trust, certificate_trust_detail, auth_type
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        // Use Option[String] so Doobie handles nullable fields via Put[Option[String]]
        // instead of Put[String] which throws "oops, null" on null values
        val insert = Update[
          (Option[String], Option[String], Timestamp, Long, Option[String], Option[String],
           Option[String], Option[String], Option[String],
           Option[String], Option[String], Int, Option[String],
           Option[String], Option[String], Option[String], Option[String], Option[String], Option[String],
           Option[String], Option[String], Option[String])
        ](insertSql)

        val values = rows.map { r =>
          (
            Option(r.userId), Option(r.url), new Timestamp(if (r.date != null) r.date.getTime else 0L),
            r.duration, Option(r.userName), Option(r.appName),
            Option(r.developerEmail), Option(r.consumerId), Option(r.implementedByPartialFunction),
            Option(r.implementedInVersion), Option(r.verb), r.httpCode, Option(r.correlationId),
            Option(r.responseBody), Option(r.sourceIp), Option(r.targetIp), Option(r.forwardedFor), Option(r.apiInstanceId),
            Option(r.consentReferenceId),
            Option(r.certificateTrust), Option(r.certificateTrustDetail),
            Option(r.authType)
          )
        }

        // Explicit commit needed: the background thread has no Lift request context,
        // so DoobieUtil falls back to the shared HikariCP pool (autoCommit=false)
        // with Strategy.void (no auto-commit/rollback).
        val program: ConnectionIO[Int] = for {
          n <- insert.updateMany(values)
          _ <- FC.commit
        } yield n
        val count = DoobieUtil.runQuery(program)
        logger.debug(s"MetricBatchWriter says: flushed $count metrics via doobie-pool")
        telemetry.written(drainedRows, System.nanoTime() - flushStart)
      }
    } catch {
      case e: Exception =>
        // JDBC batch failures wrap the real cause in the SQLException chain (getNextException),
        // which the default stack trace does NOT print — without this, the log shows only
        // "Batch entry 0 ... was aborted: call getNextException" and the actual reason
        // (e.g. "value too long for type character varying(N)") is lost and metrics are
        // silently dropped. Walk the chain so the root cause is always logged.
        logger.error(s"MetricBatchWriter says: flush failed${sqlChainDetail(e)}", e)
        if (drainedRows > 0) telemetry.lost(drainedRows, System.nanoTime() - flushStart)
    }
  }

  /** Render the nested java.sql.SQLException chain (getNextException), which the default
    * Throwable stack trace omits. Returns "" when there is no SQL chain to add. */
  private def sqlChainDetail(t: Throwable): String = {
    val details = scala.collection.mutable.ListBuffer.empty[String]
    var cause: Throwable = t
    while (cause != null) {
      cause match {
        case sql: java.sql.SQLException =>
          var next = sql.getNextException
          while (next != null) {
            details += s"${next.getClass.getSimpleName}: ${next.getMessage}"
            next = next.getNextException
          }
        case _ =>
      }
      cause = cause.getCause
    }
    if (details.isEmpty) "" else details.mkString(" [SQL chain: ", " | ", "]")
  }
}
