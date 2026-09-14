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

import java.util.Date
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}

import net.liftweb.common.Full
import org.scalatest.{FlatSpec, Matchers}

import scala.jdk.CollectionConverters._

/**
 * SimpleDateFormat is not thread-safe: parse and format both mutate the internal Calendar,
 * so a shared instance silently produces wrong dates (or throws) under concurrency.
 * APIUtil keeps its shared formats in ThreadLocals; these tests hammer the two hot paths
 * (from_date/to_date parsing and response formatting) from many threads and assert every
 * result is present and identical.
 */
class DateFormatConcurrencyTest extends FlatSpec with Matchers {

  private val threads = 32
  private val iterationsPerThread = 200

  private def hammer[T](task: () => T): List[T] = {
    val pool = Executors.newFixedThreadPool(threads)
    val startGate = new CountDownLatch(1)
    val results = new java.util.concurrent.ConcurrentLinkedQueue[T]()
    try {
      (1 to threads).foreach { _ =>
        pool.submit(new Runnable {
          override def run(): Unit = {
            startGate.await()
            (1 to iterationsPerThread).foreach(_ => results.add(task()))
          }
        })
      }
      startGate.countDown()
      pool.shutdown()
      pool.awaitTermination(60, TimeUnit.SECONDS) shouldBe true
      results.asScala.toList
    } finally {
      pool.shutdownNow()
    }
  }

  "APIUtil.parseObpStandardDate" should "return the same Full(date) from every concurrent call" in {
    val input = "2100-01-01T01:01:01.000Z"
    val expected = APIUtil.parseObpStandardDate(input)
    expected shouldBe a[Full[_]]

    val results = hammer(() => APIUtil.parseObpStandardDate(input))
    results should have size (threads * iterationsPerThread)
    all(results) shouldBe expected
  }

  "APIUtil.DateWithMsFormat" should "format and re-parse to the same instant from every concurrent call" in {
    val instant = new Date(4102448461000L) // fixed instant, avoids per-run drift
    val expected = APIUtil.DateWithMsFormat.format(instant)

    val results = hammer { () =>
      val formatted = APIUtil.DateWithMsFormat.format(instant)
      val roundTripped = APIUtil.DateWithMsFormat.parse(formatted)
      (formatted, roundTripped)
    }
    results should have size (threads * iterationsPerThread)
    all(results.map(_._1)) shouldBe expected
    all(results.map(_._2.getTime)) shouldBe instant.getTime
  }
}
