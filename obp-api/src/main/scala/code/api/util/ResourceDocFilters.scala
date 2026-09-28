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
package code.api.util

import java.util.concurrent.atomic.AtomicReference

import code.api.Constant
import code.api.util.ApiTag.ResourceDocTag
import code.api.util.APIUtil.ResourceDoc

/**
 * This class holds the `tags` and `functions` filters of a documentation request in the form that
 * may go into a cache key: sorted, without repeats, and (for ResourceDoc listings) limited to values
 * that some ResourceDoc actually carries.
 *
 * The problem it solves: the resource-docs routes cache each rendered document under a key built
 * from the filters. Built from the raw request values, the key had as many variations as a caller
 * cared to invent (`?tags=Account,Bank`, `?tags=Bank,Account`, `?tags=Account,junk123`), and each
 * variation forced a fresh render and a new cache entry, which is how a scanner can defeat the
 * cache. The constructor is private, so the only way to get a value of this type is through one of
 * the builders below, and `APIUtil.createResourceDocCacheKey` accepts only this type: a raw request
 * value cannot reach a cache key.
 *
 * None of this changes a response. The filters match by membership (a document is kept when it
 * carries any listed tag, or is any listed function), so the order and repeats of the values do
 * not matter, and a value that no document carries matches nothing. When every value given is
 * unknown the filter becomes an empty list, which matches no document, exactly as the unknown
 * values did.
 */
final case class ResourceDocFilters private (
  tags: Option[List[ResourceDocTag]],
  functions: Option[List[String]]
)

object ResourceDocFilters {

  /**
   * Filters for a ResourceDoc listing (resource-docs, Swagger, OpenAPI, bank level): sorted,
   * de-duplicated, and limited to [[ResourceDocVocabulary]].
   */
  def forResourceDocs(tagValues: Option[List[String]], functionValues: Option[List[String]]): ResourceDocFilters = {
    val vocabulary = ResourceDocVocabulary.current()
    ResourceDocFilters(
      tags = tagValues.map(values => canonical(values).filter(vocabulary.tags.contains).map(ResourceDocTag(_))),
      functions = functionValues.map(values => canonical(values).filter(vocabulary.functions.contains))
    )
  }

  /**
   * Filters for a listing of something other than ResourceDocs (message docs), whose values
   * [[ResourceDocVocabulary]] does not describe: sorted and de-duplicated only.
   */
  def normalisedOnly(tagValues: Option[List[String]], functionValues: Option[List[String]]): ResourceDocFilters =
    ResourceDocFilters(
      tags = tagValues.map(values => canonical(values).map(ResourceDocTag(_))),
      functions = functionValues.map(canonical)
    )

  private def canonical(values: List[String]): List[String] = values.distinct.sorted
}

/**
 * This object is the list of every tag and every function name that a ResourceDoc on this instance
 * carries, static and dynamic together. It is what [[ResourceDocFilters.forResourceDocs]] checks
 * request values against.
 *
 * The static part comes from `APIUtil.allStaticResourceDocs`, every static ResourceDoc of every
 * version, which does not change after start-up. The dynamic part (Dynamic Entities, Dynamic
 * Endpoints, dynamic resource docs, and the v7.0.0 form of Dynamic Entity docs) changes at runtime,
 * so it is rebuilt when the dynamic resource-docs cache namespace version changes (every Dynamic
 * Entity write bumps it) and at most [[DynamicMaxAgeMillis]] after it was built, because a Dynamic
 * Endpoint change does not bump that version. Cached dynamic documents can already be up to their
 * cache TTL old, so this bound is well inside existing behaviour.
 *
 * The list is a superset for any one request, whose documents are a subset of all of these, so
 * leaving out a value that is not in it can never leave out a document.
 */
object ResourceDocVocabulary {

  final case class Vocabulary(tags: Set[String], functions: Set[String])

  val DynamicMaxAgeMillis: Long = 30000L

  private def vocabularyOf(docs: Iterable[ResourceDoc]): Vocabulary =
    Vocabulary(docs.flatMap(_.tags.map(_.tag)).toSet, docs.map(_.partialFunctionName).toSet)

  private lazy val staticVocabulary: Vocabulary = vocabularyOf(APIUtil.allStaticResourceDocs)

  private final case class DynamicSnapshot(namespaceVersion: Long, builtAtMillis: Long, vocabulary: Vocabulary)

  private val dynamicSnapshot = new AtomicReference[Option[DynamicSnapshot]](None)

  private def dynamicDocs: List[ResourceDoc] =
    APIUtil.allDynamicResourceDocs ++ code.api.dynamic.entity.helper.DynamicEntityHelper.v700Doc

  private def dynamicVocabulary(): Vocabulary = {
    val namespaceVersion = Constant.getCacheNamespaceVersion(Constant.RD_DYNAMIC_NAMESPACE)
    val now = System.currentTimeMillis()
    dynamicSnapshot.get() match {
      case Some(snapshot) if snapshot.namespaceVersion == namespaceVersion && now - snapshot.builtAtMillis < DynamicMaxAgeMillis =>
        snapshot.vocabulary
      case _ =>
        val rebuilt = DynamicSnapshot(namespaceVersion, now, vocabularyOf(dynamicDocs))
        dynamicSnapshot.set(Some(rebuilt))
        rebuilt.vocabulary
    }
  }

  /** Every tag and function name a ResourceDoc on this instance carries now. */
  def current(): Vocabulary = {
    val dynamic = dynamicVocabulary()
    Vocabulary(staticVocabulary.tags ++ dynamic.tags, staticVocabulary.functions ++ dynamic.functions)
  }

  /** Forget the dynamic part, so the next call rebuilds it (tests, and after bulk dynamic changes). */
  def refreshDynamic(): Unit = dynamicSnapshot.set(None)
}
