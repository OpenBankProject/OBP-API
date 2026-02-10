package bootstrap.http4s

import cats.effect._
import code.api.util.APIUtil
import code.api.util.http4s.Http4sApp
import com.comcast.ip4s._
import org.http4s.ember.server._

object Http4sServer extends IOApp {

  //Start OBP relevant objects and settings; this step MUST be executed first
  // new bootstrap.http4s.Http4sBoot().boot
  new bootstrap.liftweb.Boot().boot

  val port = APIUtil.getPropsAsIntValue("http4s.port",8086)
  val host = APIUtil.getPropsValue("http4s.host","127.0.0.1")
  
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
