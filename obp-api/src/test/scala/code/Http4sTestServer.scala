package code

import cats.effect._
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil
import code.api.util.http4s.Http4sApp
import com.comcast.ip4s._
import net.liftweb.common.Logger
import org.http4s.ember.server._

import scala.concurrent.duration._

/**
 * HTTP4S Test Server - Singleton server for integration tests
 * 
 * Follows the same pattern as TestServer but for the http4s bridge integration suite.
 * Started once when first accessed, shared across all test classes.
 * 
 * IMPORTANT: This reuses Http4sApp.httpApp (same as production) to ensure
 * tests run against the exact same server configuration as production.
 * This eliminates code duplication and ensures we test the real server.
 * 
 * Usage in tests:
 *   val http4sServer = Http4sTestServer
 *   val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"
 */
object Http4sTestServer {

  private val logger = Logger("code.Http4sTestServer")

  val host = "127.0.0.1"
  val port = APIUtil.getPropsAsIntValue("http4s.test.port", 8087)

  // Create IORuntime for server lifecycle
  private implicit val runtime: IORuntime = IORuntime.global

  // Server state
  private var serverFiber: Option[FiberIO[Nothing]] = None
  private var isStarted: Boolean = false

  /**
   * Start the HTTP4S server in background
   * Called automatically on first access
   */
  private def startServer(): Unit = synchronized {
    if (!isStarted) {
      logger.info(s"[HTTP4S TEST SERVER] Starting on $host:$port")
      
      // Ensure Lift is initialized first (done by TestServer)
      // This is critical - Lift must be fully initialized before HTTP4S bridge can work
      val _ = TestServer.host  // Triggers TestServer object initialization (Boot + http4s)
      
      // Use the shared Http4sApp.httpApp to ensure we test the exact same configuration as production
      val serverResource = EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString(host).getOrElse(ipv4"127.0.0.1"))
        .withPort(Port.fromInt(port).getOrElse(port"8087"))
        .withHttpApp(Http4sApp.httpApp)  // Reuse production httpApp - single source of truth!
        .withShutdownTimeout(1.second)
        .build
      
      // Start server in background fiber
      serverFiber = Some(
        serverResource
          .use(_ => IO.never)
          .start
          .unsafeRunSync()
      )
      
      // Actively poll until the server accepts TCP connections, instead of a fixed 2s sleep.
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

      isStarted = true
      logger.info(s"[HTTP4S TEST SERVER] Started successfully on $host:$port")
    }
  }

  /**
   * Stop the HTTP4S server
   * Called during JVM shutdown
   */
  def stopServer(): Unit = synchronized {
    if (isStarted) {
      logger.info("[HTTP4S TEST SERVER] Stopping...")
      serverFiber.foreach(_.cancel.unsafeRunSync())
      serverFiber = None
      isStarted = false
      logger.info("[HTTP4S TEST SERVER] Stopped")
    }
  }

  /**
   * Check if server is running
   */
  def isRunning: Boolean = isStarted

  // Register shutdown hook
  sys.addShutdownHook {
    stopServer()
  }

  // Auto-start on first access (lazy initialization)
  startServer()
}
