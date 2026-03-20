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
 *   - metrics.batch.size: flush threshold (default: 50)
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
    targetIp: String
  )

  private val queue = new ConcurrentLinkedQueue[MetricRow]()

  private val batchSize = APIUtil.getPropsAsIntValue("metrics.batch.size", 50)
  private val flushIntervalSeconds = APIUtil.getPropsAsLongValue("metrics.batch.interval.seconds", 5L)

  private val started = new AtomicBoolean(false)

  /** Check if the batch writer scheduler has been started. */
  def isStarted: Boolean = started.get()

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
      logger.info(s"MetricBatchWriter says: started (batchSize=$batchSize, flushInterval=${flushIntervalSeconds}s)")
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
  private def flush(): Unit = {
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
            userid_c, url_c, date_c, duration_c, username_c, appname_c,
            developeremail_c, consumerid_c, implementedbypartialfunction,
            implementedinversion, verb, httpcode, correlationid,
            responsebody, sourceip, targetip
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        // Use Option[String] so Doobie handles nullable fields properly via Put[Option[String]]
        val insert = Update[
          (Option[String], Option[String], Timestamp, Long, Option[String], Option[String],
           Option[String], Option[String], Option[String],
           Option[String], Option[String], Int, Option[String],
           Option[String], Option[String], Option[String])
        ](insertSql)

        val values = rows.map { r =>
          (
            Option(r.userId), Option(r.url), new Timestamp(if (r.date != null) r.date.getTime else 0L),
            r.duration, Option(r.userName), Option(r.appName),
            Option(r.developerEmail), Option(r.consumerId), Option(r.implementedByPartialFunction),
            Option(r.implementedInVersion), Option(r.verb), r.httpCode, Option(r.correlationId),
            Option(r.responseBody), Option(r.sourceIp), Option(r.targetIp)
          )
        }

        val program: ConnectionIO[Int] = insert.updateMany(values)
        val count = DoobieUtil.runQuery(program)
        logger.debug(s"MetricBatchWriter says: flushed $count metrics via doobie-pool")
      }
    } catch {
      case e: Exception =>
        logger.error(s"MetricBatchWriter says: flush failed", e)
    }
  }
}
