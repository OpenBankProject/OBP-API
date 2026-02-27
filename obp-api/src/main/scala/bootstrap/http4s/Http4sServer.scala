package bootstrap.http4s

import cats.effect._
import code.api.util.APIUtil
import code.api.util.http4s.Http4sApp
import com.comcast.ip4s._
import org.http4s.Uri
import org.http4s.ember.server._

object Http4sServer extends IOApp {

  //Start OBP relevant objects and settings; this step MUST be executed first
  // new bootstrap.http4s.Http4sBoot().boot
  new bootstrap.liftweb.Boot().boot
 
  // Parse hostname - support both "127.0.0.1" and "http://127.0.0.1" formats
  private def parseHostname(hostnameValue: String): String = {
    val trimmed = hostnameValue.trim
    // Try to parse as URI first
    Uri.fromString(trimmed).toOption
      .flatMap(_.host.map(_.renderString))
      .getOrElse(trimmed) // If not a valid URI, use as-is
  }

  val host = parseHostname(code.api.Constant.HostName)
  val port = APIUtil.getPropsAsIntValue("dev.port",8080)

  // Use shared httpApp configuration (same as tests)
  val httpApp = Http4sApp.httpApp
  
  override def run(args: List[String]): IO[ExitCode] = EmberServerBuilder
    .default[IO]
    .withHost(Host.fromString(host).get)
    .withPort(Port.fromInt(port).get)
    .withHttpApp(httpApp)
    .build
    .use(_ => IO.never)
    .as(ExitCode.Success)
}
