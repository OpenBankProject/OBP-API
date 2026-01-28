package bootstrap.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.util.APIUtil
import com.comcast.ip4s._
import org.http4s._
import org.http4s.ember.server._
import org.http4s.implicits._

import scala.language.higherKinds
object Http4sServer extends IOApp {

  //Start OBP relevant objects and settings; this step MUST be executed first 
  new bootstrap.http4s.Http4sBoot().boot

  val port = APIUtil.getPropsAsIntValue("http4s.port",8086)
  val host = APIUtil.getPropsValue("http4s.host","127.0.0.1")
  
  type HttpF[A] = OptionT[IO, A]

  val services: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
    code.api.v5_0_0.Http4s500.wrappedRoutesV500Services.run(req)
      .orElse(code.api.v7_0_0.Http4s700.wrappedRoutesV700Services.run(req))
  }

  val httpApp: Kleisli[IO, Request[IO], Response[IO]] = (services).orNotFound
  
  override def run(args: List[String]): IO[ExitCode] = EmberServerBuilder
    .default[IO]
    .withHost(Host.fromString(host).get)
    .withPort(Port.fromInt(port).get)
    .withHttpApp(httpApp)
    .build
    .use(_ => IO.never)
    .as(ExitCode.Success)
}
