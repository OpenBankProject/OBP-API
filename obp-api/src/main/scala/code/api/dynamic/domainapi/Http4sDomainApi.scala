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

package code.api.dynamic.domainapi

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.Constant.HostName
import code.api.ResourceDocs1_4_0.OpenAPI31JSONFactory
import code.api.ResourceDocs1_4_0.OpenAPI31JSONFactory.{InfoJson, ServerJson}
import code.api.cache.Caching
import code.api.dynamic.domainapi.DomainApiPaths.{DomainApiCall, domainApiCallKey}
import code.api.dynamic.endpoint.Http4sDynamicEndpoint
import code.api.dynamic.entity.Http4sDynamicEntity
import code.api.util.APIUtil.ResourceDoc
import code.api.util.{APIUtil, YAMLUtils}
import code.api.v1_4_0.JSONFactory1_4_0
import code.domainapi.{DomainApiRoute, DomainApis}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods.compact

/**
 * This object is the front door of the Domain APIs: it serves the URLs under each registered base path.
 *
 * A call under a base path is rewritten to the OBP URL of the same endpoint (see [[DomainApiPaths]]) and
 * handed to the handler that serves that URL, so authentication, Roles, Consents, rate limiting,
 * row-level access, field restrictions, Dynamic Query checks and metrics all run exactly as they do for
 * the OBP URL. A Domain API grants nothing. A Dynamic Entity of the space is tried first, then a Dynamic
 * Resource Doc of the space; registering or updating a Domain API is refused while two of the space's
 * endpoints would answer the same verb and path, so the order only matters for a clash created later.
 *
 * `BASE_PATH/openapi.json` and `BASE_PATH/openapi.yaml` serve the Domain API's own OpenAPI document.
 *
 * It is wired into Http4sApp.baseServices last, just before the JSON 404, so no OBP route can be hidden
 * by a base path; base paths are also refused when their first segment is one OBP serves.
 */
object Http4sDomainApi extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  private val jsonContentType = `Content-Type`(MediaType.application.json, Charset.`UTF-8`)
  private val yamlContentType = `Content-Type`(new MediaType("application", "yaml"), Charset.`UTF-8`)

  private def withPath(req: Request[IO], segments: List[String]): Request[IO] =
    req.withUri(req.uri.withPath(Uri.Path.unsafeFromString(segments.mkString("/", "/", ""))))

  lazy val routes: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
      val segments = req.uri.path.segments.map(_.encoded).toList
      DomainApiPaths.find(DomainApis.domainApiProvider.vend.routes(), segments) match {
        case None => OptionT.none[IO, Response[IO]]
        case Some((route, rest)) =>
          rest match {
            case "openapi.json" :: Nil if req.method == Method.GET =>
              OptionT.liftF(IO(openApiJson(route)).map(body =>
                Response[IO](Status.Ok).withEntity(body).withContentType(jsonContentType)))
            case "openapi.yaml" :: Nil if req.method == Method.GET =>
              OptionT.liftF(IO(openApiYaml(route)).map(body =>
                Response[IO](Status.Ok).withEntity(body).withContentType(yamlContentType)))
            case Nil => OptionT.none[IO, Response[IO]]
            case _ =>
              val marked = req.withAttribute(domainApiCallKey,
                DomainApiCall(route.domainApiId, route.basePath, req.uri.path.renderString))
              Http4sDynamicEntity.wrappedRoutesDynamicEntityV700.run(withPath(marked, DomainApiPaths.dynamicEntityPath(route.bankId, rest)))
                .orElse(Http4sDynamicEndpoint.wrappedRoutesDynamicEndpoint.run(withPath(marked, DomainApiPaths.dynamicResourceDocPath(route.bankId, rest))))
          }
      }
    }

  /**
   * The ResourceDocs of a space's endpoints that a Domain API publishes: the v7.0.0 Dynamic Entity docs
   * and the Dynamic Resource Docs of that space.
   */
  def spaceDocs(space: String): List[ResourceDoc] =
    APIUtil.allDynamicResourceDocsIn(ApiVersion.v7_0_0).filter(doc => APIUtil.dynamicResourceDocBelongsToSpace(doc, space))

  /** The space's docs as the Domain API publishes them: at their published path, examples without bank_id. */
  def publishedDocs(route: DomainApiRoute): List[ResourceDoc] =
    spaceDocs(route.bankId).flatMap { doc =>
      DomainApiPaths.publishedPath(route.bankId, doc.requestUrl).map { path =>
        val published = doc.copy(requestUrl = path, successResponseBody = DomainApiPaths.exampleUnderDomainApi(doc.successResponseBody))
        published.connectorMethods = doc.connectorMethods
        published
      }
    }

  /** The Domain API's OpenAPI 3.1 document: the published docs, with the Domain API's own title, version and server. */
  def openApi(route: DomainApiRoute): JValue = {
    val docsJson = JSONFactory1_4_0.createResourceDocsJson(publishedDocs(route), isVersion4OrHigher = true, locale = None).resource_docs
    val document = OpenAPI31JSONFactory.createOpenAPI31Json(docsJson, route.version, HostName).copy(
      info = InfoJson(title = route.title, version = route.version, description = Some(route.description).filter(_.nonEmpty)),
      servers = List(ServerJson(url = s"$HostName/${route.basePath}", description = Some(route.title))))
    OpenAPI31JSONFactory.OpenAPI31JsonFormats.toJValue(document)
  }

  // Cached with the dynamic resource docs, which are cleared whenever a Dynamic Entity or Dynamic Resource
  // Doc changes. The key carries everything of the registration the document shows.
  private def cached(route: DomainApiRoute, format: String)(build: => String): String = {
    val key = s"domain-api-openapi:$format:${route.domainApiId}:${route.basePath}:${route.version}:${(route.title + route.description).hashCode}"
    Caching.getDynamicResourceDocCache(key).getOrElse {
      val rendered = build
      Caching.setDynamicResourceDocCache(key, rendered)
      rendered
    }
  }

  def openApiJson(route: DomainApiRoute): String = cached(route, "json")(compact(org.json4s.native.JsonMethods.render(openApi(route))))

  def openApiYaml(route: DomainApiRoute): String = cached(route, "yaml")(YAMLUtils.jValueToYAMLSafe(openApi(route), "# Error converting to YAML"))
}
