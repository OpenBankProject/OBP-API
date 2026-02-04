package code

import cats.effect._
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil
import code.api.util.http4s.Http4sLiftWebBridge
import com.comcast.ip4s._
import org.http4s._
import org.http4s.ember.server._
import org.http4s.implicits._

import scala.concurrent.duration._

/**
 * HTTP4S Test Server - Singleton server for integration tests
 * 
 * Follows the same pattern as TestServer (Jetty/Lift) but for HTTP4S.
 * Started once when first accessed, shared across all test classes.
 * 
 * Usage in tests:
 *   val http4sServer = Http4sTestServer
 *   val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"
 */
object Http4sTestServer {

  val host = "127.0.0.1"
  val port = APIUtil.getPropsAsIntValue("http4s.test.port", 8087)

  // Create IORuntime for server lifecycle
  private implicit val runtime: IORuntime = IORuntime.global

  // Server state
  private var serverFiber: Option[FiberIO[Nothing]] = None
  private var isStarted: Boolean = false

  /**
   * Build HTTP4S routes (same as Http4sServer.scala)
   */
  private def buildHttpApp: HttpApp[IO] = {
    type HttpF[A] = cats.data.OptionT[IO, A]
    
    val baseServices: HttpRoutes[IO] = cats.data.Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
      code.api.v5_0_0.Http4s500.wrappedRoutesV500Services.run(req)
        .orElse(code.api.v7_0_0.Http4s700.wrappedRoutesV700Services.run(req))
        .orElse(Http4sLiftWebBridge.routes.run(req))
    }
    
    val services: HttpRoutes[IO] = Http4sLiftWebBridge.withStandardHeaders(baseServices)
    services.orNotFound
  }

  /**
   * Start the HTTP4S server in background
   * Called automatically on first access
   */
  private def startServer(): Unit = synchronized {
    if (!isStarted) {
      println(s"[HTTP4S TEST SERVER] Starting on $host:$port")
      
      // Ensure Lift is initialized first (done by TestServer)
      // This is critical - Lift must be fully initialized before HTTP4S bridge can work
      val _ = TestServer.server
      
      val serverResource = EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString(host).getOrElse(ipv4"127.0.0.1"))
        .withPort(Port.fromInt(port).getOrElse(port"8087"))
        .withHttpApp(buildHttpApp)
        .withShutdownTimeout(1.second)
        .build
      
      // Start server in background fiber
      serverFiber = Some(
        serverResource
          .use(_ => IO.never)
          .start
          .unsafeRunSync()
      )
      
      // Wait for server to be ready
      Thread.sleep(2000)
      
      isStarted = true
      println(s"[HTTP4S TEST SERVER] Started successfully on $host:$port")
    }
  }

  /**
   * Stop the HTTP4S server
   * Called during JVM shutdown
   */
  def stopServer(): Unit = synchronized {
    if (isStarted) {
      println("[HTTP4S TEST SERVER] Stopping...")
      serverFiber.foreach(_.cancel.unsafeRunSync())
      serverFiber = None
      isStarted = false
      println("[HTTP4S TEST SERVER] Stopped")
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
