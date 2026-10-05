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

import java.util.concurrent.TimeUnit

/**
 * This class records Telemetry for a writer that queues records in memory and writes them to the
 * database in batches (the API Metrics and Connector Metrics writers).
 *
 * Such a writer can fall behind (its queue grows) or lose records (a failed flush drops the whole
 * batch), and before this neither showed anywhere but the log. The queue is a
 * ConcurrentLinkedQueue, whose size() walks the whole queue, so the depth is not read from it: it
 * is the rows queued minus the rows taken off the queue by a flush, whether written or lost.
 *
 * @param writer the value of the `writer` tag, for example "api_metrics"
 */
class BatchWriterTelemetry(writer: String) {

  private val queuedRows = Telemetry.counter("obp.api.batch_writer.rows", "writer" -> writer, "result" -> "queued")
  private val writtenRows = Telemetry.counter("obp.api.batch_writer.rows", "writer" -> writer, "result" -> "written")
  private val lostRows = Telemetry.counter("obp.api.batch_writer.rows", "writer" -> writer, "result" -> "lost")

  Telemetry.gauge("obp.api.batch_writer.queue.depth", "writer" -> writer)(
    queuedRows.count() - writtenRows.count() - lostRows.count())

  def queued(): Unit = queuedRows.increment()

  /** A flush wrote `rows` rows, taking `nanos`. */
  def written(rows: Int, nanos: Long): Unit = {
    writtenRows.increment(rows.toDouble)
    Telemetry.timer("obp.api.batch_writer.flushes", "writer" -> writer, "result" -> "success").record(nanos, TimeUnit.NANOSECONDS)
  }

  /** A flush failed after taking `rows` rows off the queue, so they are gone. */
  def lost(rows: Int, nanos: Long): Unit = {
    lostRows.increment(rows.toDouble)
    Telemetry.timer("obp.api.batch_writer.flushes", "writer" -> writer, "result" -> "failure").record(nanos, TimeUnit.NANOSECONDS)
  }
}
