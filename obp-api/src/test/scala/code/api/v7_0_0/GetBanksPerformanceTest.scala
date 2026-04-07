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
 */
class GetBanksPerformanceTest extends ServerSetupWithTestData {

  object PerfTag extends Tag("GetBanksPerf")

  private val http4sServer = Http4sTestServer
  private val baseUrl      = s"http://${http4sServer.host}:${http4sServer.port}"

  private val WarmupRequests     = 20
  private val TotalRequests      = 200
  private val ConcurrentThreads  = 20
  private val TimeoutSeconds      = 60

  // ── HTTP helper ────────────────────────────────────────────────────────────

  /** Returns elapsed milliseconds. Throws on non-2xx only if assertSuccess=true. */
  private def timedGet(path: String): Long = {
    val t0 = System.nanoTime()
    val req = url(s"$baseUrl$path")
    val response = Http.default(req.setHeader("Accept", "*/*") > as.Response(_.getStatusCode))
    Await.result(response, 10.seconds)
    (System.nanoTime() - t0) / 1000000L
  }

  // ── Stats helper ───────────────────────────────────────────────────────────

  case class Stats(
    label:    String,
    n:        Int,
    errors:   Int,
    min:      Long,
    max:      Long,
    mean:     Double,
    median:   Long,
    p95:      Long,
    p99:      Long,
    rps:      Double   // requests per second (wall-clock)
  ) {
    override def toString: String =
      f"""
         |  ── $label ──
         |  Requests : $n%d  (errors: $errors%d)
         |  Min      : $min%d ms
         |  Median   : $median%d ms
         |  Mean     : $mean%.1f ms
         |  P95      : $p95%d ms
         |  P99      : $p99%d ms
         |  Max      : $max%d ms
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
    val times   = new ConcurrentLinkedQueue[Long]()
    val errors  = new java.util.concurrent.atomic.AtomicInteger(0)
    val latch   = new CountDownLatch(total)
    val pool    = Executors.newFixedThreadPool(threads)
    val wallStart = System.nanoTime()

    (0 until total).foreach { _ =>
      pool.submit(new Runnable {
        def run(): Unit =
          try {
            times.add(timedGet(path))
          } catch {
            case _: Exception => errors.incrementAndGet()
          } finally {
            latch.countDown()
          }
      })
    }

    latch.await(TimeoutSeconds, TimeUnit.SECONDS)
    pool.shutdown()
    val wallMs = (System.nanoTime() - wallStart) / 1000000L

    (times.asScala.toSeq, errors.get(), wallMs)
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  override def afterAll(): Unit = {
    super.afterAll()
    // Terminate actor system before JVM shutdown hooks run to prevent
    // ConsentScheduler from accessing H2 after DB_CLOSE_ON_EXIT closes it.
    ObpActorSystem.localActorSystem.terminate()
  }

  // ── Tests ──────────────────────────────────────────────────────────────────

  feature("GET /banks performance: v6.0.0 (Lift bridge) vs v7.0.0 (native http4s)") {

    scenario(s"Compare response times under $TotalRequests requests / $ConcurrentThreads threads", PerfTag) {

      // ── warm up both endpoints ─────────────────────────────────────────────
      println(s"\n[PERF] Warming up with $WarmupRequests requests per endpoint...")
      (0 until WarmupRequests).foreach { _ =>
        timedGet("/obp/v6.0.0/banks")
        timedGet("/obp/v7.0.0/banks")
      }

      // ── v6.0.0 load ────────────────────────────────────────────────────────
      println(s"[PERF] Running v6.0.0 load ($TotalRequests requests, $ConcurrentThreads threads)...")
      val (v6Times, v6Errors, v6Wall) = runLoad("/obp/v6.0.0/banks", TotalRequests, ConcurrentThreads)

      // ── v7.0.0 load ────────────────────────────────────────────────────────
      println(s"[PERF] Running v7.0.0 load ($TotalRequests requests, $ConcurrentThreads threads)...")
      val (v7Times, v7Errors, v7Wall) = runLoad("/obp/v7.0.0/banks", TotalRequests, ConcurrentThreads)

      // ── stats ──────────────────────────────────────────────────────────────
      val v6Stats = computeStats("v6.0.0  GET /banks  (Lift bridge)", v6Times, v6Errors, v6Wall)
      val v7Stats = computeStats("v7.0.0  GET /banks  (native http4s)", v7Times, v7Errors, v7Wall)

      val medianDiffPct =
        if (v6Stats.median > 0)
          (v6Stats.median - v7Stats.median) * 100.0 / v6Stats.median
        else 0.0

      val meanDiffPct =
        if (v6Stats.mean > 0)
          (v6Stats.mean - v7Stats.mean) * 100.0 / v6Stats.mean
        else 0.0

      // ── print results ──────────────────────────────────────────────────────
      println(
        s"""
           |
           |════════════════════════════════════════════════
           |  GET /banks — Performance Comparison
           |  Config: $TotalRequests requests / $ConcurrentThreads concurrent threads
           |════════════════════════════════════════════════
           |${v6Stats.toString}
           |${v7Stats.toString}
           |
           |  ── Delta (positive = v7.0.0 faster) ──
           |  Median : ${f"$medianDiffPct%.1f"}% faster
           |  Mean   : ${f"$meanDiffPct%.1f"}% faster
           |════════════════════════════════════════════════""".stripMargin
      )

      // ── assertions ─────────────────────────────────────────────────────────
      withClue("v6.0.0 should have no errors: ") { v6Stats.errors shouldBe 0 }
      withClue("v7.0.0 should have no errors: ") { v7Stats.errors shouldBe 0 }
      withClue("v6.0.0 should have full sample: ") { v6Times.size shouldBe TotalRequests }
      withClue("v7.0.0 should have full sample: ") { v7Times.size shouldBe TotalRequests }
    }
  }
}
