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
  def enqueue(row: MetricRow): Unit = {
    queue.add(row)
  }

  /**
   * Drain the queue and batch-insert all pending metrics via Doobie.
   */
  private[code] def flush(): Unit = {
    try {
      val batch = new java.util.ArrayList[MetricRow]()
      var item = queue.poll()
      while (item != null) {
        batch.add(item)
        item = queue.poll()
      }

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
            responsebody, sourceip, targetip, apiinstanceid, consent_reference_id,
            certificate_trust, certificate_trust_detail, auth_type
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        // Use Option[String] so Doobie handles nullable fields via Put[Option[String]]
        // instead of Put[String] which throws "oops, null" on null values
        val insert = Update[
          (Option[String], Option[String], Timestamp, Long, Option[String], Option[String],
           Option[String], Option[String], Option[String],
           Option[String], Option[String], Int, Option[String],
           Option[String], Option[String], Option[String], Option[String], Option[String],
           Option[String], Option[String], Option[String])
        ](insertSql)

        val values = rows.map { r =>
          (
            Option(r.userId), Option(r.url), new Timestamp(if (r.date != null) r.date.getTime else 0L),
            r.duration, Option(r.userName), Option(r.appName),
            Option(r.developerEmail), Option(r.consumerId), Option(r.implementedByPartialFunction),
            Option(r.implementedInVersion), Option(r.verb), r.httpCode, Option(r.correlationId),
            Option(r.responseBody), Option(r.sourceIp), Option(r.targetIp), Option(r.apiInstanceId),
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
      }
    } catch {
      case e: Exception =>
        // JDBC batch failures wrap the real cause in the SQLException chain (getNextException),
        // which the default stack trace does NOT print — without this, the log shows only
        // "Batch entry 0 ... was aborted: call getNextException" and the actual reason
        // (e.g. "value too long for type character varying(N)") is lost and metrics are
        // silently dropped. Walk the chain so the root cause is always logged.
        logger.error(s"MetricBatchWriter says: flush failed${sqlChainDetail(e)}", e)
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
