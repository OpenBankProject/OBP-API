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
import code.api.util.APIUtil.ResourceDoc.isPathVariable
import code.domainapi.DomainApiRoute
import code.dynamicEntity.DynamicEntityProvider
import code.dynamicResourceDoc.DynamicResourceDocProvider
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

  /**
   * What the front door records on a request it rewrote: which Domain API, and the URL that was called (path
   * and query string, the shape of CallContext.url), which API Metrics record as `domain_api_url`.
   */
  case class DomainApiCall(domainApiId: String, basePath: String, calledUrl: String)

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
   * hidden under a Domain API, so it is refused, and so is a Dynamic Entity named one of them.
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

  /** This is one Dynamic Resource Doc as the path rules below see it. */
  case class ResourceDocPath(dynamicResourceDocId: Option[String], verb: String, path: String, name: String) {
    def segments: List[String] = path.split("/").filter(_.nonEmpty).toList
    def describe: String = s"${verb.toUpperCase} $path ($name)"
  }

  /**
   * Two paths are ambiguous when one request could match both: they have as many segments, and at each
   * position the segments are equal or at least one of them is a path variable (an all-capitals segment).
   */
  def ambiguous(a: List[String], b: List[String]): Boolean =
    a.length == b.length && a.zip(b).forall { case (x, y) => x == y || isPathVariable(x) || isPathVariable(y) }

  /**
   * This lists why a Dynamic Resource Doc's path would be ambiguous in its space, given the space's
   * Dynamic Entity names and its other Dynamic Resource Docs; empty when it is not.
   *
   * The Dynamic Entities and Dynamic Resource Docs of one space share one set of paths when a Domain API
   * publishes the space under its base path, where a Dynamic Entity owns every path that starts with its
   * name. The rules hold in every space, with or without a Domain API, so that one can be registered over
   * any space at any time. A path may not start with a path variable (it would match every entity name),
   * with a segment a Dynamic Entity URL or the Domain API's documentation already uses, or with the name
   * of one of the space's Dynamic Entities, and no other doc of the same verb may match a request it
   * matches. A doc is never compared with itself (the same dynamicResourceDocId).
   */
  def resourceDocAmbiguities(doc: ResourceDocPath, entityNames: List[String], otherDocs: List[ResourceDocPath]): List[String] =
    doc.segments match {
      case Nil => List(s"${doc.describe} has no path segment")
      case first :: _ =>
        val variableFirst =
          if (isPathVariable(first)) List(s"${doc.describe} starts with the path variable $first, which would also match every Dynamic Entity name") else Nil
        val reserved =
          if (reservedUnderBasePath.contains(first)) List(s"${doc.describe} starts with $first, which a Dynamic Entity URL or a Domain API's documentation already uses") else Nil
        val entities = entityNames.filter(_.equalsIgnoreCase(first))
          .map(entityName => s"${doc.describe} starts with $first, the name of the Dynamic Entity $entityName")
        val docs = otherDocs
          .filterNot(other => doc.dynamicResourceDocId.isDefined && other.dynamicResourceDocId == doc.dynamicResourceDocId)
          .filter(other => other.verb.equalsIgnoreCase(doc.verb) && ambiguous(other.segments, doc.segments))
          .map(other => s"${doc.describe} and ${other.describe} would both match one request")
        variableFirst ++ reserved ++ entities ++ docs
    }

  /**
   * This lists why a Dynamic Entity name would be ambiguous in its space, given the space's Dynamic
   * Resource Docs: a doc whose path starts with the name (compared ignoring case), or a name that is a
   * segment a Dynamic Entity URL or a Domain API's documentation already uses. Empty when it is neither.
   */
  def entityNameAmbiguities(entityName: String, docs: List[ResourceDocPath]): List[String] = {
    val reserved =
      if (reservedUnderBasePath.contains(entityName.toLowerCase)) List(s"the Dynamic Entity name $entityName is a segment a Dynamic Entity URL or a Domain API's documentation already uses") else Nil
    reserved ++ docs.filter(_.segments.headOption.exists(_.equalsIgnoreCase(entityName)))
      .map(doc => s"${doc.describe} starts with $entityName, the name of the Dynamic Entity")
  }

  /**
   * This lists every ambiguity among a space's Dynamic Entities and Dynamic Resource Docs, each pair once.
   * Writes are checked one at a time, so this finds only what predates the rules; a Domain API is
   * refused over a space while the list is not empty.
   */
  def ambiguitiesInSpace(entityNames: List[String], docs: List[ResourceDocPath]): List[String] = {
    val docAmbiguities = docs.zipWithIndex.flatMap { case (doc, index) => resourceDocAmbiguities(doc, entityNames, docs.drop(index + 1)) }
    val entityAmbiguities = entityNames.flatMap(entityNameAmbiguities(_, Nil))
    (docAmbiguities ++ entityAmbiguities).distinct.sorted
  }

  /** The names of the Dynamic Entities of a space (None for the system space), read from the database. */
  def entityNamesIn(space: Option[String]): List[String] =
    DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities(space, false).map(_.entityName)

  /** The Dynamic Resource Docs of a space (None for the system space), read from the database, not a cache. */
  def resourceDocPathsIn(space: Option[String]): List[ResourceDocPath] =
    DynamicResourceDocProvider.provider.vend.getAllInSpace(space)
      .map(doc => ResourceDocPath(doc.dynamicResourceDocId, doc.requestVerb, doc.requestUrl, doc.partialFunctionName))

  /** [[resourceDocAmbiguities]] for a doc about to be created (no id) or moved to a new verb or path (its id). */
  def storedResourceDocAmbiguities(space: Option[String], dynamicResourceDocId: Option[String], verb: String, path: String, name: String): List[String] =
    resourceDocAmbiguities(ResourceDocPath(dynamicResourceDocId, verb, path, name), entityNamesIn(space), resourceDocPathsIn(space))

  /** [[entityNameAmbiguities]] for a Dynamic Entity about to be created or renamed in a space. */
  def storedEntityNameAmbiguities(space: Option[String], entityName: String): List[String] =
    entityNameAmbiguities(entityName, resourceDocPathsIn(space))

  /** [[ambiguitiesInSpace]] for a space as the database holds it now. */
  def storedAmbiguitiesInSpace(space: Option[String]): List[String] =
    ambiguitiesInSpace(entityNamesIn(space), resourceDocPathsIn(space))

  /** A Dynamic Entity record response as a Domain API returns it: without `bank_id`. */
  def responseUnderDomainApi(response: JObject): JObject =
    JObject(response.obj.filterNot(_._1 == "bank_id"))

  /** The same rule applied to a documented example, which may be any JSON. */
  def exampleUnderDomainApi(example: Any): Any = example match {
    case o: JObject => responseUnderDomainApi(o)
    case other => other
  }
}
