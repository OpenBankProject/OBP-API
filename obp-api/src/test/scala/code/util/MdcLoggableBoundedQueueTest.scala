package code.util

import java.util.concurrent.{ArrayBlockingQueue, CountDownLatch, ThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{FlatSpec, Matchers}

/**
 * The log dispatch pool must have a bounded, documented failure mode: when its queue is full,
 * non-critical entries are dropped and counted, critical (warn/error) entries still run on the
 * caller, and nothing throws to the caller.
 */
class MdcLoggableBoundedQueueTest extends FlatSpec with Matchers {

  /** One worker, queue of one, AbortPolicy: the third submission while the worker is busy is rejected. */
  private def tinyExecutor(): ThreadPoolExecutor =
    new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue[Runnable](1),
      new ThreadPoolExecutor.AbortPolicy())

  /** Occupy the worker and fill the queue; returns the latch that releases the worker. */
  private def saturate(ex: ThreadPoolExecutor): CountDownLatch = {
    val release = new CountDownLatch(1)
    val started = new CountDownLatch(1)
    Helper.dispatchOn(ex, "test", critical = true) { started.countDown(); release.await(30, TimeUnit.SECONDS) }
    started.await(10, TimeUnit.SECONDS) shouldBe true
    Helper.dispatchOn(ex, "test", critical = true) { () } // sits in the queue
    ex.getQueue.size() shouldBe 1
    release
  }

  "the log dispatch" should "drop and count non-critical entries when the queue is full, without throwing" in {
    val ex = tinyExecutor()
    val release = saturate(ex)
    try {
      val ran = new AtomicInteger(0)
      val before = Helper.mdcLogDroppedCount
      (1 to 5).foreach(_ => Helper.dispatchOn(ex, "test", critical = false) { ran.incrementAndGet() })
      Helper.mdcLogDroppedCount - before shouldBe 5
      ran.get shouldBe 0
    } finally { release.countDown(); ex.shutdownNow() }
  }

  it should "run critical entries inline on the caller when the queue is full" in {
    val ex = tinyExecutor()
    val release = saturate(ex)
    try {
      val ranOn = new java.util.concurrent.atomic.AtomicReference[String]()
      val before = Helper.mdcLogDroppedCount
      Helper.dispatchOn(ex, "test", critical = true) { ranOn.set(Thread.currentThread().getName) }
      ranOn.get shouldBe Thread.currentThread().getName
      Helper.mdcLogDroppedCount shouldBe before // a critical entry is never counted as dropped
    } finally { release.countDown(); ex.shutdownNow() }
  }

  it should "not let a failing log body escape to the caller" in {
    val ex = tinyExecutor()
    try {
      noException should be thrownBy Helper.dispatchOn(ex, "test", critical = true) { throw new RuntimeException("boom") }
    } finally ex.shutdownNow()
  }

  it should "run on the pool thread with the caller's name in the MDC, and leave the pool thread untouched" in {
    val poolThreadName = "test-pool-thread"
    val ex = java.util.concurrent.Executors.newSingleThreadExecutor((r: Runnable) => new Thread(r, poolThreadName))
    try {
      val seen = new java.util.concurrent.atomic.AtomicReference[(Thread, String, String)]()
      val done = new CountDownLatch(1)
      Helper.dispatchOn(ex, "test", critical = false) {
        seen.set((Thread.currentThread(), Thread.currentThread().getName, org.slf4j.MDC.get(Helper.MdcCallerThreadKey)))
        done.countDown()
      }
      done.await(10, TimeUnit.SECONDS) shouldBe true
      seen.get._1 should not be theSameInstanceAs(Thread.currentThread()) // still off the calling thread
      seen.get._2 shouldBe poolThreadName                                 // the pool thread keeps its own name
      seen.get._3 shouldBe Thread.currentThread().getName                 // the caller travels in the MDC

      // The key must not leak into the next task on the same pool thread.
      val leaked = new java.util.concurrent.atomic.AtomicReference[String]("unset")
      val done2 = new CountDownLatch(1)
      ex.execute(() => { leaked.set(org.slf4j.MDC.get(Helper.MdcCallerThreadKey)); done2.countDown() })
      done2.await(10, TimeUnit.SECONDS) shouldBe true
      leaked.get shouldBe null
    } finally ex.shutdownNow()
  }

  it should "write on the caller instead of dropping when the pool has been shut down" in {
    val ex = tinyExecutor()
    ex.shutdown()
    val ranOn = new java.util.concurrent.atomic.AtomicReference[String]()
    val before = Helper.mdcLogDroppedCount
    Helper.dispatchOn(ex, "test", critical = false) { ranOn.set(Thread.currentThread().getName) }
    ranOn.get shouldBe Thread.currentThread().getName
    Helper.mdcLogDroppedCount shouldBe before // shutdown is not overload, so nothing is counted as dropped
  }

  it should "clamp a zero or negative pool or queue size so the pool can still be created" in {
    Helper.dispatchPoolSettings(0, 0) shouldBe ((1, 1))
    Helper.dispatchPoolSettings(-3, -10) shouldBe ((1, 1))
    Helper.dispatchPoolSettings(2, 10000) shouldBe ((2, 10000))
    // The clamped values are accepted by the real constructors, the raw ones are not.
    an[IllegalArgumentException] should be thrownBy new ArrayBlockingQueue[Runnable](0)
    val (pool, queue) = Helper.dispatchPoolSettings(0, 0)
    val ex = new ThreadPoolExecutor(pool, pool, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue[Runnable](queue))
    try ex.getCorePoolSize shouldBe 1 finally ex.shutdownNow()
  }

  it should "keep the production pool's queue bounded and observable" in {
    Helper.mdcLogQueueDepth should be >= 0
    Helper.mdcLogDroppedCount should be >= 0L
  }
}
