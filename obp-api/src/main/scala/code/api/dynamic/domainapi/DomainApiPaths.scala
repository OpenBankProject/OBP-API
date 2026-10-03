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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.Constant.ApiPathZero
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.domainapi.DomainApiRoute
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards, ApiVersion}
import org.json4s.JsonAST.{JObject, JValue}

/**
 * This object holds the rules that relate a Domain API's URLs to OBP's own, in one place.
 *
 * A Domain API publishes the Dynamic Entities and Dynamic Resource Docs of one space under a base path,
 * so that `/carbon-registry/v1/activity` reaches what OBP serves at
 * `/obp/v7.0.0/banks/SYS/dynamic-entities/activity`. Two things need the same mapping: the front door
 * ([[Http4sDomainApi]]), which rewrites an incoming call to the OBP URL, and the Domain API's OpenAPI
 * file, which rewrites each documented OBP URL to the published one. Both read the functions here, so
 * the published documentation cannot describe a path the front door does not serve, or the reverse.
 *
 * The same holds for the one thing a Domain API changes in a response: a Dynamic Entity record response
 * leaves out `bank_id`, because the base path already fixes the space. [[responseUnderDomainApi]] is
 * applied both to the real response and to the documented example.
 */
object DomainApiPaths {

  /** What the front door records on a request it rewrote: which Domain API, and the path that was called. */
  case class DomainApiCall(domainApiId: String, basePath: String, calledPath: String)

  val domainApiCallKey: org.typelevel.vault.Key[DomainApiCall] =
    org.typelevel.vault.Key.newKey[IO, DomainApiCall].unsafeRunSync()

  private val obp = ApiStandards.obp.toString
  private val v700 = ApiVersion.v7_0_0.toString
  private val dynamicEndpoint = ApiShortVersions.`dynamic-endpoint`.toString
  private val dynamicResourceDocSegment = "dynamic-resource-doc"
  private val dynamicEntitiesSegment = "dynamic-entities"

  /**
   * The first path segments OBP serves itself. A base path may not start with one of them, so a Domain
   * API can never be confused with, or hide, one of OBP's own URLs. The front door also runs last in the
   * request chain, after every OBP route, as a second line of defence.
   */
  def reservedFirstSegments: Set[String] = Set(
    obp, ApiPathZero, "open-banking", ConstantsBG.berlinGroupVersion1.urlPrefix, ConstantsBG.berlinGroupVersion2.urlPrefix,
    "my", "apps", "status", "health", "alive", "banks", "oauth", "dauth", "siwe", ".well-known", "static",
    "openapi.json", "openapi.yaml"
  )

  /**
   * Path segments a Dynamic Entity URL already gives a meaning to right after the space, and the names of
   * the Domain API's own documentation. A Dynamic Resource Doc whose path starts with one of them would be
   * hidden under a Domain API, so it counts as a clash.
   */
  val reservedUnderBasePath: Set[String] = Set("my", "public", "community", "openapi.json", "openapi.yaml")

  private val Segment = "[a-z0-9]([a-z0-9.-]*[a-z0-9])?"
  private val MajorVersionSegment = "v(0|[1-9][0-9]*)".r
  private val SemanticVersion = "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)".r

  /** None when the base path is acceptable, otherwise why not. */
  def basePathProblem(basePath: String): Option[String] = {
    val segments = basePath.split("/", -1).toList
    if (segments.length < 2 || segments.length > 5) Some("it must have two to five segments")
    else if (!segments.forall(_.matches(Segment))) Some("each segment must be lowercase letters, digits, hyphens or dots")
    else if (reservedFirstSegments.contains(segments.head)) Some(s"its first segment, ${segments.head}, is one OBP serves")
    else if (MajorVersionSegment.unapplySeq(segments.last).isEmpty) Some("its last segment must be the major version, vN")
    else None
  }

  /** The N of the `vN` that ends a base path. */
  def majorOf(basePath: String): Option[Int] =
    basePath.split("/").lastOption.collect { case MajorVersionSegment(n) => n.toInt }

  /** A version is MAJOR.MINOR.PATCH, and its MAJOR is the base path's. */
  def versionFits(version: String, basePath: String): Boolean = version match {
    case SemanticVersion(major, _, _) => majorOf(basePath).contains(major.toInt)
    case _ => false
  }

  /**
   * Two base paths overlap when one is the other or starts with it, segment by segment. Overlapping base
   * paths are refused, so a call never has more than one Domain API it could belong to.
   */
  def overlap(a: String, b: String): Boolean = {
    val (as, bs) = (a.split("/").toList, b.split("/").toList)
    as.startsWith(bs) || bs.startsWith(as)
  }

  /** The Domain API a request path is under, and the segments after its base path. */
  def find(routes: List[DomainApiRoute], pathSegments: List[String]): Option[(DomainApiRoute, List[String])] =
    routes.collectFirst {
      case route if pathSegments.startsWith(route.basePathSegments) => (route, pathSegments.drop(route.basePathSegments.length))
    }

  /** The OBP path a call to a Dynamic Entity under a Domain API is served at. */
  def dynamicEntityPath(space: String, rest: List[String]): List[String] =
    obp :: v700 :: "banks" :: space :: dynamicEntitiesSegment :: rest

  /** The OBP path a call to a Dynamic Resource Doc under a Domain API is served at. */
  def dynamicResourceDocPath(space: String, rest: List[String]): List[String] =
    obp :: dynamicEndpoint :: "banks" :: space :: dynamicResourceDocSegment :: rest

  /**
   * The path under the base path at which a documented endpoint is published, from its ResourceDoc's
   * request URL: the v7.0.0 Dynamic Entity docs (`/banks/SPACE/dynamic-entities/...`) and the Dynamic
   * Resource Docs (`/banks/SPACE/dynamic-resource-doc/...`). Anything else is not published (None).
   */
  def publishedPath(space: String, docRequestUrl: String): Option[String] = {
    // A doc's request URL may carry the prefix of the version it is served in (/obp/v7.0.0/...); the
    // space starts at `banks`.
    val segments = docRequestUrl.split("/").filter(_.nonEmpty).toList.dropWhile(_ != "banks")
    segments match {
      case "banks" :: `space` :: kind :: rest if rest.nonEmpty && (kind == dynamicEntitiesSegment || kind == dynamicResourceDocSegment) =>
        Some(rest.mkString("/", "/", ""))
      case _ => None
    }
  }

  /** A path template with each placeholder (an all-capitals segment) reduced to one form, for comparison. */
  private def templateKey(path: String): String =
    path.split("/").filter(_.nonEmpty).map(s => if (s.matches("[A-Z][A-Z0-9_]*")) "{}" else s).mkString("/", "/", "")

  /**
   * The verb and path pairs that more than one endpoint of the space would publish, and the Dynamic
   * Resource Docs whose path starts with a segment a Dynamic Entity URL or the documentation already uses.
   * Each is described for the person who has to resolve it.
   */
  def clashes(space: String, docs: List[ResourceDoc]): List[String] = {
    val published = docs.flatMap(doc => publishedPath(space, doc.requestUrl).map(path => (doc, path)))
    val duplicates = published
      .groupBy { case (doc, path) => (doc.requestVerb.toUpperCase, templateKey(path)) }
      .collect { case ((verb, key), entries) if entries.length > 1 =>
        s"$verb $key (${entries.map(_._1.partialFunctionName).sorted.mkString(", ")})"
      }.toList
    val hidden = published.collect {
      case (doc, path) if doc.requestUrl.contains(s"/$dynamicResourceDocSegment/") &&
        reservedUnderBasePath.contains(path.split("/").filter(_.nonEmpty).headOption.getOrElse("")) =>
        s"${doc.requestVerb.toUpperCase} $path (${doc.partialFunctionName}) starts with a reserved segment"
    }
    (duplicates ++ hidden).sorted
  }

  /** A Dynamic Entity record response as a Domain API returns it: without `bank_id`. */
  def responseUnderDomainApi(response: JObject): JObject =
    JObject(response.obj.filterNot(_._1 == "bank_id"))

  /** The same rule applied to a documented example, which may be any JSON. */
  def exampleUnderDomainApi(example: Any): Any = example match {
    case o: JObject => responseUnderDomainApi(o)
    case other => other
  }
}
