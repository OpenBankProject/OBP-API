package code.api

import cats.effect.IO
import org.http4s.{Charset, HttpRoutes, MediaType}
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

/**
 * Native http4s route for the `GET /alive` liveness probe.
 *
 * Mirrors `code.api.aliveCheck`'s Lift serve block. Replaces
 * `LiftRules.statelessDispatch.append(aliveCheck)` in `Boot.scala`.
 *
 * Note: `StatusPage` also exposes `/health` and `/status`. `/alive` is kept
 * as a separate route because external monitoring/load-balancers may already
 * be wired to it.
 */
object AliveCheckRoutes {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "alive" =>
      Ok("true").map(_.withContentType(`Content-Type`(MediaType.application.json, Charset.`UTF-8`)))
  }
}
