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
