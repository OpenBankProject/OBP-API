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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import scala.concurrent.ExecutionContext

/** Dedicated bounded pool for blocking JDBC connection-acquisition (and gRPC dispatch),
 *  kept off the CPU-sized global pool so blocked I/O cannot starve request Futures.
 *
 *  The default of 50 covers the sum of the Hikari maximumPoolSize values it serves
 *  (main pool 20 + stored-procedure pool 20) with headroom; override via the
 *  `blocking_io_pool.size` prop if the Hikari pools are resized.
 */
object BlockingIoExecutionContext {
  private val size = APIUtil.getPropsAsIntValue("blocking_io_pool.size", 50)
  private val counter = new AtomicInteger(0)
  private val threadFactory: ThreadFactory = (r: Runnable) => {
    val t = new Thread(r, s"obp-blocking-io-${counter.incrementAndGet()}")
    t.setDaemon(true)
    t
  }
  val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(size, threadFactory))
}
