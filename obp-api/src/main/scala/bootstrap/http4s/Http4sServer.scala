package bootstrap.http4s

import cats.effect._
import code.api.util.APIUtil
import code.api.util.http4s.{Http4sApp, Http4sConfigUtil}
import com.comcast.ip4s._
import org.http4s.ember.server._

object Http4sServer extends IOApp {

  //Start OBP relevant objects and settings; this step MUST be executed first
  // new bootstrap.http4s.Http4sBoot().boot
  new bootstrap.liftweb.Boot().boot
  
  // Get bind address: use bind_address prop if set, otherwise parse from hostname
  // Note: hostname prop must remain unchanged as it may be used for local_provider_name fallback
  val host =  Http4sConfigUtil.parseHostname(APIUtil.getPropsValue("bind_address",code.api.Constant.HostName))
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
