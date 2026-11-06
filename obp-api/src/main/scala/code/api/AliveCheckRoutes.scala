/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
