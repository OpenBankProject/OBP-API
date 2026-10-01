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

import cats.effect.IO
import code.api.dynamic.entity.query.{DynamicQuery, DynamicQueryDeclaration}
import code.api.util.APIUtil.Http4sEndpointIO
import code.api.util.{APIUtil, CallContext}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.JsonAliases.compactRender
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Request, Response, Status}
import org.json4s.JsonAST.{JInt, JObject, JString}

/**
 * This object turns a Dynamic Query declaration into the handler of a Dynamic Resource Doc whose
 * `programming_lang` is `Query`. Nothing is compiled: each request is answered by
 * [[DynamicQuery.run]] for the caller, in the Dynamic Entity space of the doc (`bankId`, None for the
 * system space). The doc's own Roles are enforced by the middleware before this runs, as for any
 * Dynamic Resource Doc.
 */
object DynamicQueryEndpoint extends MdcLoggable {

  private val jsonContentType = `Content-Type`(MediaType.application.json)

  def apply(declaration: DynamicQueryDeclaration, bankId: Option[String]): Http4sEndpointIO = new Http4sEndpointIO {
    override def isDefinedAt(req: Request[IO]): Boolean = true

    override def apply(req: Request[IO]): CallContext => IO[Response[IO]] = { cc =>
      val callerParams = req.uri.query.multiParams.map { case (name, values) => name -> values.toList }
      IO.blocking {
        DynamicQuery.run(bankId, declaration, callerParams, cc.user.map(_.userId).toOption, APIUtil.getConsumerPrimaryKey(Some(cc)))
      }.map {
        case Right(result) => json(Status.Ok, result)
        case Left(failure) => json(Status.fromInt(failure.status).getOrElse(Status.BadRequest),
          JObject(List("code" -> JInt(failure.status), "message" -> JString(failure.message))))
      }.handleError { e =>
        logger.warn(s"DynamicQueryEndpoint says: the Dynamic Query from '${declaration.from}' failed", e)
        json(Status.InternalServerError, JObject(List("code" -> JInt(500), "message" -> JString(s"OBP-50000: Unknown Error. ${e.getMessage}"))))
      }
    }
  }

  private def json(status: Status, body: JObject): Response[IO] =
    Response[IO](status).withEntity(compactRender(body)).withContentType(jsonContentType)
}
