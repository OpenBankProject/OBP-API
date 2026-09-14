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

package code.api.dynamic.endpoint.helper

import org.json4s._
import scala.language.implicitConversions
import cats.effect.IO
import code.api.util.APIUtil.{Http4sEndpointIO, OBPReturnType}
import code.api.util.DynamicUtil.{Sandbox, Validation}
import code.api.util.{CallContext, CustomJsonFormats, DynamicUtil}
import org.http4s.{Request, Response}

/**
 * Super-trait of a dynamic compiled endpoint. The dynamically-compiled code (Piece C) extends this
 * and supplies the `process` method body.
 *
 * Native http4s contract (replaces the former Lift one
 * `process(callContext, request: Req, pathParams): Box[JsonResponse]`): the body
 * receives the http4s `Request[IO]` and returns an `IO[Response[IO]]`. The implicit
 * [[DynamicCompileEndpoint.obpReturnTypeToIOResponse]] lets a body whose last expression is an
 * `OBPReturnType[T]` (the familiar `Future.successful((json, HttpCode.\`200\`(cc)))` style) be used
 * directly — the response status is taken from `CallContext.httpCode` (set by `HttpCode.xxx`).
 */
trait DynamicCompileEndpoint {
  implicit val formats = CustomJsonFormats.formats

  // * is any bankId
  val boundBankId: String

  protected def process(callContext: CallContext, request: Request[IO], pathParams: Map[String, String]): IO[Response[IO]]

  val endpoint: Http4sEndpointIO = new Http4sEndpointIO {
    override def isDefinedAt(x: Request[IO]): Boolean = true

    override def apply(request: Request[IO]): CallContext => IO[Response[IO]] = { cc =>
      val Some(pathParams) = cc.resourceDocument.map(_.getPathParams(request.uri.path.segments.toList.map(_.encoded)))

      validateDependencies()

      Sandbox.sandbox(boundBankId).runInSandboxIO {
        process(cc, request, pathParams)
      }
    }
  }

  private def validateDependencies() = {
    val dependencies = DynamicUtil.getDynamicCodeDependentMethods(this.getClass, "process".==)
    Validation.validateDependency(dependencies)
  }
}

object DynamicCompileEndpoint {
  import org.json4s.Extraction
  import com.openbankproject.commons.util.JsonAliases.prettyRender
  import org.json4s.JsonDSL._
  import org.http4s.Status

  /**
   * Native error response helper for dynamic-code bodies, replacing the former
   * `Full(errorJsonResponse(msg))` (a Lift `Box[JsonResponse]`). Renders the standard OBP error
   * shape `{ "code", "message" }` with the given HTTP status (default 400).
   */
  def errorResponse(message: String, code: Int = 400): IO[Response[IO]] = {
    val json = ("code" -> code) ~ ("message" -> message)
    IO.pure(Response[IO](Status.fromInt(code).getOrElse(Status.BadRequest)).withEntity(prettyRender(json)))
  }

  /**
   * Convert an `OBPReturnType[T]` (= `Future[(T, Option[CallContext])]`) to a native
   * `IO[Response[IO]]`, the http4s replacement for the former
   * `scalaFutureToBoxedJsonResponse` (which produced a Lift `Box[JsonResponse]`). The HTTP status
   * comes from `CallContext.httpCode` (set by `NewStyle.HttpCode.xxx`), defaulting to 200; the
   * value is rendered as JSON via Lift-json, matching the previous response shape.
   */
  implicit def obpReturnTypeToIOResponse[T](scf: OBPReturnType[T])(implicit m: Manifest[T]): IO[Response[IO]] =
    IO.fromFuture(IO(scf)).map { case (value, ccOpt) =>
      val code = ccOpt.flatMap(_.httpCode).getOrElse(200)
      val jsonString = prettyRender(Extraction.decompose(value)(CustomJsonFormats.formats))
      Response[IO](Status.fromInt(code).getOrElse(Status.Ok)).withEntity(jsonString)
    }
}
