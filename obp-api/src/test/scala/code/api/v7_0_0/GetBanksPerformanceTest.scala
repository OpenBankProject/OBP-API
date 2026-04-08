package code.api.v7_0_0

import code.Http4sTestServer
import code.actorsystem.ObpActorSystem
import code.setup.ServerSetupWithTestData
import dispatch.Defaults._
import dispatch._
import org.scalatest.Tag

import java.util.concurrent.{CountDownLatch, ConcurrentLinkedQueue, Executors, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Performance comparison: GET /banks — v6.0.0 (Lift bridge) vs v7.0.0 (native http4s)
 *
 * Both endpoints hit the same TestServer (Http4sApp.httpApp) so infrastructure
 * overhead is identical. The only difference is the code path:
 *   v6.0.0 → Http4sLiftWebBridge → S.init → Lift dispatch → OBPAPI6_0_0
 *   v7.0.0 → ResourceDocMiddleware → Http4s700.getBanks (native IO)
 *
 * Run with: -Dtest=GetBanksPerformanceTest
 *
 * Test structure:
 *   1. Serial (1 thread)       — per-request overhead without any thread contention
 *   2. High concurrency        — throughput and tail latency under load (20 threads)
 *   3. Concurrency scaling     — tabular view: 1 / 5 / 10 / 20 threads side by side
 *
 * Ordering bias mitigation: scenario 1 runs v7 first, scenario 2 runs v6 first.
 * This distributes any residual JVM / H2 warmup advantage evenly across versions.
 */
class GetBanksPerformanceTest extends ServerSetupWithTestData {

  object PerfTag extends Tag("GetBanksPerf")

  private val http4sServer  = Http4sTestServer
  private val baseUrl       = s"http://${http4sServer.host}:${http4sServer.port}"
  private val TimeoutSeconds = 120

  // ── HTTP helper ────────────────────────────────────────────────────────────

  /** Issues a GET request and returns elapsed milliseconds. */
  private def timedGet(path: String): Long = {
    val t0  = System.nanoTime()
    val req = url(s"$baseUrl$path")
    val response = Http.default(req.setHeader("Accept", "*/*") > as.Response(_.getStatusCode))
    Await.result(response, 10.seconds)
    (System.nanoTime() - t0) / 1000000L
  }

  // ── Stats ──────────────────────────────────────────────────────────────────

  case class Stats(
    label:  String,
    n:      Int,
    errors: Int,
    min:    Long,
    max:    Long,
    mean:   Double,
    median: Long,
    p95:    Long,
    p99:    Long,
    rps:    Double
  ) {
    /** Max - min: measures how tight the latency distribution is. */
    def spread: Long = max - min

    override def toString: String =
      f"""  ── $label ──
         |  Requests : $n%d  (errors: $errors%d)
         |  Min      : $min%d ms
         |  Median   : $median%d ms
         |  Mean     : $mean%.1f ms
         |  P95      : $p95%d ms
         |  P99      : $p99%d ms
         |  Max      : $max%d ms
         |  Spread   : $spread%d ms  (max - min)
         |  RPS      : $rps%.1f req/s""".stripMargin
  }

  private def computeStats(label: String, times: Seq[Long], errors: Int, wallMs: Long): Stats = {
    val sorted = times.sorted
    val n      = sorted.size
    Stats(
      label  = label,
      n      = n + errors,
      errors = errors,
      min    = sorted.head,
      max    = sorted.last,
      mean   = sorted.sum.toDouble / n,
      median = sorted(n / 2),
      p95    = sorted((n * 0.95).toInt.min(n - 1)),
      p99    = sorted((n * 0.99).toInt.min(n - 1)),
      rps    = (n + errors) * 1000.0 / wallMs
    )
  }

  // ── Load runner ────────────────────────────────────────────────────────────

  /**
   * Fire `total` requests across `threads` concurrent workers.
   * Returns (successful elapsed times, error count, wall-clock ms).
   */
  private def runLoad(path: String, total: Int, threads: Int): (Seq[Long], Int, Long) = {
    val times  = new ConcurrentLinkedQueue[Long]()
    val errors = new java.util.concurrent.atomic.AtomicInteger(0)
    val latch  = new CountDownLatch(total)
    val pool   = Executors.newFixedThreadPool(threads)
    val wallStart = System.nanoTime()

    (0 until total).foreach { _ =>
      pool.submit(new Runnable {
        def run(): Unit =
          try { times.add(timedGet(path)) }
          catch { case _: Exception => errors.incrementAndGet() }
          finally { latch.countDown() }
      })
    }

    latch.await(TimeoutSeconds, TimeUnit.SECONDS)
    pool.shutdown()
    val wallMs = (System.nanoTime() - wallStart) / 1000000L
    (times.asScala.toSeq, errors.get(), wallMs)
  }

  // ── Warmup ─────────────────────────────────────────────────────────────────

  private def warmup(requests: Int = 30): Unit = {
    println(s"\n[PERF] Warming up with $requests requests per endpoint...")
    (0 until requests).foreach { _ =>
      timedGet("/obp/v6.0.0/banks")
      timedGet("/obp/v7.0.0/banks")
    }
    println("[PERF] Warmup complete.")
  }

  // ── Delta printer ──────────────────────────────────────────────────────────

  private def printComparison(title: String, v6: Stats, v7: Stats): Unit = {
    // Positive value = v7 is better (lower latency or higher RPS).
    def pct(v6val: Double, v7val: Double): String = {
      if (v6val == 0) "n/a"
      else f"${(v6val - v7val) * 100.0 / v6val}%.1f%%"
    }
    println(
      s"""
         |════════════════════════════════════════════════
         |  $title
         |════════════════════════════════════════════════
         |${v6.toString}
         |${v7.toString}
         |
         |  ── Delta (positive = v7.0.0 better) ──
         |  Median : ${pct(v6.median.toDouble, v7.median.toDouble)}
         |  Mean   : ${pct(v6.mean, v7.mean)}
         |  P99    : ${pct(v6.p99.toDouble, v7.p99.toDouble)}
         |  Spread : ${pct(v6.spread.toDouble, v7.spread.toDouble)}
         |  RPS    : ${pct(v6.rps, v7.rps)}  (negative here means v7 lower; expected at low concurrency)
         |════════════════════════════════════════════════""".stripMargin
    )
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  override def afterAll(): Unit = {
    super.afterAll()
    ObpActorSystem.localActorSystem.terminate()
  }

  // ── Tests ──────────────────────────────────────────────────────────────────

  feature("GET /banks performance: v6.0.0 (Lift bridge) vs v7.0.0 (native http4s)") {

    /**
     * Single thread removes all concurrency effects and measures pure per-request overhead:
     * ResourceDocMiddleware traversal, CallContext building, IO.fromFuture handoff.
     * v7 first here to counterbalance the ordering in the next scenario.
     */
    scenario("Serial (1 thread) — per-request overhead without contention", PerfTag) {
      warmup()

      val total = 60

      // v7 runs first in this scenario to counterbalance ordering bias.
      val (v7Times, v7Errors, v7Wall) = runLoad("/obp/v7.0.0/banks", total, 1)
      val (v6Times, v6Errors, v6Wall) = runLoad("/obp/v6.0.0/banks", total, 1)

      val v6 = computeStats("v6.0.0  GET /banks  (Lift bridge)",   v6Times, v6Errors, v6Wall)
      val v7 = computeStats("v7.0.0  GET /banks  (native http4s)", v7Times, v7Errors, v7Wall)

      printComparison(s"Serial (1 thread / $total requests)", v6, v7)

      withClue("v6.0.0 errors: ") { v6.errors shouldBe 0 }
      withClue("v7.0.0 errors: ") { v7.errors shouldBe 0 }
      withClue("v6.0.0 full sample: ") { v6Times.size shouldBe total }
      withClue("v7.0.0 full sample: ") { v7Times.size shouldBe total }
    }

    /**
     * High concurrency reveals thread-pool exhaustion in Lift (thread-per-request) vs
     * cats-effect IO (non-blocking). Expected: v6 may win median (hot Lift path is fast
     * when threads are free), but v7 should win P99 and spread (no queuing).
     * v6 runs first here to counterbalance the previous scenario.
     */
    scenario("High concurrency (20 threads) — throughput and tail latency under load", PerfTag) {
      warmup()

      val total   = 200
      val threads = 20

      // v6 first in this scenario (v7 was first in the serial scenario — balanced).
      val (v6Times, v6Errors, v6Wall) = runLoad("/obp/v6.0.0/banks", total, threads)
      val (v7Times, v7Errors, v7Wall) = runLoad("/obp/v7.0.0/banks", total, threads)

      val v6 = computeStats("v6.0.0  GET /banks  (Lift bridge)",   v6Times, v6Errors, v6Wall)
      val v7 = computeStats("v7.0.0  GET /banks  (native http4s)", v7Times, v7Errors, v7Wall)

      printComparison(s"High concurrency ($threads threads / $total requests)", v6, v7)

      withClue("v6.0.0 errors: ") { v6.errors shouldBe 0 }
      withClue("v7.0.0 errors: ") { v7.errors shouldBe 0 }
      withClue("v6.0.0 full sample: ") { v6Times.size shouldBe total }
      withClue("v7.0.0 full sample: ") { v7Times.size shouldBe total }
      // Architectural guarantee: IO runtime never queues threads, so v7 tail must be tighter.
      withClue("v7.0.0 P99 should be <= v6.0.0 P99 under high concurrency: ") {
        v7.p99 should be <= v6.p99
      }
      withClue("v7.0.0 spread (max - min) should be <= v6.0.0 spread: ") {
        v7.spread should be <= v6.spread
      }
    }

    /**
     * Shows how each version scales as concurrency increases.
     * Prints a side-by-side table: median (ms) | P99 (ms) | RPS at each thread count.
     *
     * Limitation: levels run sequentially in the same JVM, so each level inherits the
     * cumulative JVM/H2 warmup of all prior levels. By level 4 (20 threads), the JVM has
     * processed ~1400 prior requests and H2 has all bank rows pinned in memory.
     * This prevents v6 thread-pool exhaustion from appearing (requests complete too fast
     * to saturate the pool). The standalone high-concurrency scenario is authoritative
     * for tail-latency comparison; use this table only to observe per-request overhead
     * and linear throughput scaling.
     */
    scenario("Concurrency scaling — 1 / 5 / 10 / 20 threads", PerfTag) {
      // requestsPerLevel must be large enough relative to threads for thread-pool
      // exhaustion to appear in v6 (Lift thread-per-request). Rule of thumb: >= threads * 10.
      // At lower values the burst ends before the queue forms and v6 P99 looks artificially low.
      val requestsPerLevel = 200
      val levels           = List(1, 5, 10, 20)

      case class Row(threads: Int, v6: Stats, v7: Stats)
      val rows = levels.map { threads =>
        // Re-warmup before each level so rows start from a comparable thermal state.
        // Without this, each row benefits from all prior rows' JIT/H2 cache buildup,
        // making the table measure warmup accumulation rather than concurrency scaling.
        warmup(20)
        val (v6Times, v6Errors, v6Wall) = runLoad("/obp/v6.0.0/banks", requestsPerLevel, threads)
        val (v7Times, v7Errors, v7Wall) = runLoad("/obp/v7.0.0/banks", requestsPerLevel, threads)
        Row(
          threads,
          computeStats(s"v6 @$threads threads", v6Times, v6Errors, v6Wall),
          computeStats(s"v7 @$threads threads", v7Times, v7Errors, v7Wall)
        )
      }

      val header = f"  ${"Threads"}%-8s  ${"v6 med"}%8s  ${"v7 med"}%8s  ${"v6 P99"}%8s  ${"v7 P99"}%8s  ${"v6 RPS"}%8s  ${"v7 RPS"}%8s  ${"P99 delta"}%10s"
      val sep    = "  " + "-" * (header.length - 2)
      val tableRows = rows.map { r =>
        val p99delta = if (r.v6.p99 > 0) f"${(r.v6.p99 - r.v7.p99) * 100.0 / r.v6.p99}%.0f%%" else "n/a"
        f"  ${r.threads}%-8d  ${r.v6.median}%8d  ${r.v7.median}%8d  ${r.v6.p99}%8d  ${r.v7.p99}%8d  ${r.v6.rps}%8.1f  ${r.v7.rps}%8.1f  $p99delta%10s"
      }

      println(
        s"""
           |════════════════════════════════════════════════
           |  Concurrency Scaling — $requestsPerLevel requests per level
           |  Columns: median (ms) | P99 (ms) | RPS | P99 delta (positive = v7 better)
           |════════════════════════════════════════════════
           |$header
           |$sep
           |${tableRows.mkString("\n")}
           |$sep
           |════════════════════════════════════════════════""".stripMargin
      )

      // Correctness assertions only — no latency assertions.
      // Sequential levels share cumulative JVM/H2 warmup: each row runs after all prior
      // rows, so v6 at level 4 (20 threads) benefits from ~600 prior requests of JIT
      // and H2 buffer cache. The table is observational; tail-latency assertions belong
      // in the standalone high-concurrency scenario where warmup state is controlled.
      rows.foreach { r =>
        withClue(s"v6.0.0 errors at ${r.threads} threads: ") { r.v6.errors shouldBe 0 }
        withClue(s"v7.0.0 errors at ${r.threads} threads: ") { r.v7.errors shouldBe 0 }
      }
    }
  }
}
