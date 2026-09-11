package code

import cats.effect._
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil
import code.api.util.http4s.Http4sApp
import com.comcast.ip4s._
import net.liftweb.common.Logger
import org.http4s.ember.server._

import java.util.UUID
import scala.concurrent.duration._

/**
 * Test Server - Singleton http4s server for integration tests.
 *
 * Replaces the former Jetty-based TestServer. Uses Http4sApp.httpApp
 * (same as production) to ensure test/prod parity.
 *
 * Boot.boot() is called directly for Lift initialization — no servlet
 * context is needed.
 *
 * The server starts synchronously on object initialization so that
 * all test classes can rely on it being ready.
 */
object TestServer {

  private val logger = Logger("code.TestServer")

  val host = "localhost"
  val port = APIUtil.getPropsAsIntValue("tests.port", 8000)
  val externalHost = APIUtil.getPropsValue("external.hostname")
  val externalPort = APIUtil.getPropsAsIntValue("external.port")

  // Before anything touches the database. Boot creates the schema and every test class then
  // deletes the contents of 140 tables, so this is the last point at which pointing at the wrong
  // database is still harmless. Every path that reaches a database comes through here: the 98
  // ServerSetup suites, ConcurrentRaceSetup (via ServerSetupWithTestData), SandboxDataLoadingTest
  // and DefaultUsers all reference TestServer.
  code.setup.DisposableDatabaseGuard.assertDisposable()

  // Initialize Lift framework (replaces WebAppContext bootstrap)
  logger.info("[TestServer] Initializing Lift framework via Boot.boot()")
  new bootstrap.liftweb.Boot().boot
  logger.info("[TestServer] Lift framework initialized")

  // Start http4s EmberServer synchronously
  private implicit val runtime: IORuntime = IORuntime.global
  private var serverFiber: Option[FiberIO[Nothing]] = None

  private def startServer(): Unit = synchronized {
    logger.info(s"[TestServer] Starting http4s EmberServer on $host:$port (HTTP/1.1 only)")

    val serverResource = EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).getOrElse(ipv4"127.0.0.1"))
      .withPort(Port.fromInt(port).getOrElse(port"8000"))
      .withHttpApp(Http4sApp.httpApp)
      .withShutdownTimeout(1.second)
      // EmberServer defaults to HTTP/1.1 only (no HTTP/2)
      // This ensures compatibility with Dispatch HTTP client
      .build

    serverFiber = Some(
      serverResource
        .use(_ => IO.never)
        .start
        .unsafeRunSync()
    )

    // Actively poll until the server accepts TCP connections, instead of a fixed 2s sleep.
    // Saves ~1.5s of fixed wait on fast machines and is more robust on slow ones.
    val readyDeadline = System.currentTimeMillis() + 10000
    var serverReady = false
    while (!serverReady && System.currentTimeMillis() < readyDeadline) {
      try {
        val probe = new java.net.Socket()
        probe.connect(new java.net.InetSocketAddress(host, port), 200)
        probe.close()
        serverReady = true
      } catch {
        case _: Throwable => Thread.sleep(50)
      }
    }
    logger.info(s"[TestServer] http4s EmberServer started on $host:$port (ready=$serverReady)")
  }

  // Auto-start on object initialization
  startServer()

  // Register shutdown hook for clean teardown
  sys.addShutdownHook {
    logger.info("[TestServer] Shutting down http4s EmberServer")
    serverFiber.foreach(_.cancel.unsafeRunSync())
  }

  // Public API — unchanged from original
  val userId1 = Some(UUID.randomUUID.toString)
  val userId2 = Some(UUID.randomUUID.toString)
  val userId3 = Some(UUID.randomUUID.toString)
  val userId4 = Some(UUID.randomUUID.toString)

  val resourceUser1Name = "resourceUser1"
  val resourceUser2Name = "resourceUser2"
  val resourceUser3Name = "resourceUser3"
  val resourceUser4Name = "resourceUser4"
}
