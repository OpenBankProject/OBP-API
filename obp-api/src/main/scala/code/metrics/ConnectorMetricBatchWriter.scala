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
 * Batched connector-metric writer. Mirrors MetricBatchWriter: metrics are enqueued
 * in memory and flushed to the database periodically via a multi-value INSERT.
 *
 * Configuration:
 *   - connector.metrics.batch.interval.seconds: flush interval (default: 5)
 */
object ConnectorMetricBatchWriter extends MdcLoggable {

  case class ConnectorMetricRow(
    connectorName: String,
    functionName: String,
    correlationId: String,
    date: Date,
    duration: Long,
    requestParams: String,
    isSuccessful: Boolean,
    apiInstanceId: String
  )

  private val queue = new ConcurrentLinkedQueue[ConnectorMetricRow]()

  private val flushIntervalSeconds =
    APIUtil.getPropsAsLongValue("connector.metrics.batch.interval.seconds", 5L)

  private val started = new AtomicBoolean(false)

  def start(): Unit = {
    if (started.compareAndSet(false, true)) {
      val scheduler = Executors.newSingleThreadScheduledExecutor { r =>
        val t = new Thread(r, "connector-metric-batch-writer")
        t.setDaemon(true)
        t
      }
      scheduler.scheduleWithFixedDelay(
        () => flush(),
        flushIntervalSeconds,
        flushIntervalSeconds,
        TimeUnit.SECONDS
      )
      logger.info(s"ConnectorMetricBatchWriter says: started (flushInterval=${flushIntervalSeconds}s)")
    }
  }

  def enqueue(row: ConnectorMetricRow): Unit = {
    queue.add(row)
  }

  private[code] def flush(): Unit = {
    try {
      val batch = new java.util.ArrayList[ConnectorMetricRow]()
      var item = queue.poll()
      while (item != null) {
        batch.add(item)
        item = queue.poll()
      }

      if (!batch.isEmpty) {
        val rows = {
          val buf = scala.collection.mutable.ListBuffer.empty[ConnectorMetricRow]
          val it = batch.iterator()
          while (it.hasNext) buf += it.next()
          buf.toList
        }

        val insertSql = """
          INSERT INTO mappedconnectormetric (
            connectorname, functionname, correlationid, date_c,
            duration, requestparams, issuccessful, apiinstanceid
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """

        val insert = Update[
          (Option[String], Option[String], Option[String], Timestamp,
           Long, Option[String], Boolean, Option[String])
        ](insertSql)

        val values = rows.map { r =>
          (
            Option(r.connectorName),
            Option(r.functionName),
            Option(r.correlationId),
            new Timestamp(if (r.date != null) r.date.getTime else 0L),
            r.duration,
            Option(r.requestParams),
            r.isSuccessful,
            Option(r.apiInstanceId)
          )
        }

        // Explicit commit: background thread has no Lift request context, so
        // DoobieUtil uses the shared HikariCP pool with Strategy.void.
        val program: ConnectionIO[Int] = for {
          n <- insert.updateMany(values)
          _ <- FC.commit
        } yield n
        val count = DoobieUtil.runQuery(program)
        logger.debug(s"ConnectorMetricBatchWriter says: flushed $count connector metrics via doobie-pool")
      }
    } catch {
      case e: Exception =>
        logger.error(s"ConnectorMetricBatchWriter says: flush failed", e)
    }
  }
}
