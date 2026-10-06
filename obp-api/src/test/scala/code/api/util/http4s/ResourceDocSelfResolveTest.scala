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

package code.api.util.http4s

import code.api.util.APIUtil.ResourceDoc
import code.setup.ServerSetup
import org.http4s.Uri
import org.scalatest.Tag

import scala.collection.mutable.ArrayBuffer

/**
 * This test checks that every registered ResourceDoc is the doc the middleware picks for
 * a request to that doc's own URL.
 *
 * ResourceDocMiddleware does not know which http4s route will serve a request: it re-matches
 * the URL against the ResourceDoc templates and takes the doc it finds for roles, the login
 * requirement, the bank/account/view lookups, the operationId (telemetry, enable/disable,
 * JSON-schema and auth-type validation), Force-Error and currency upper-casing. If one
 * template also matches another doc's URL and wins, the other endpoint is validated and
 * reported under the wrong doc while its own handler still runs, so its own tests pass.
 * That happened with `.../transaction-request-types/MOBILE_WALLET/transaction-requests` in
 * v7.0.0, which matched UTILITY, BULK and OPEN_CORRIDOR_PROMISE requests, and with HOLD in
 * v6.0.0, which matched CARDANO and Ethereum requests.
 *
 * For each version's middleware catalog, the test sends the doc's template itself as the
 * request path (placeholders such as `BANK_ID` stand in for values, and every template
 * matches them) and asserts the matcher returns that same doc. Docs that share a verb and
 * URL with another doc in the same catalog are skipped, as no matcher could tell them apart.
 */
class ResourceDocSelfResolveTest extends ServerSetup {

  object ResourceDocSelfResolveTag extends Tag("ResourceDocSelfResolve")

  /**
   * Each OBP version's `resourceDocs` buffer is filled when its nested `Implementations` object
   * initialises, which normally waits for the first request. Naming the object here forces that.
   */
  private def loaded(implementations: Any, docs: ArrayBuffer[ResourceDoc]): ArrayBuffer[ResourceDoc] = docs

  /** Each catalog is the `resourceDocs` buffer a version passes to `ResourceDocMiddleware.apply`. */
  private def catalogs: List[(String, ArrayBuffer[ResourceDoc])] = List(
    "v1.2.1" -> loaded(code.api.v1_2_1.Http4s121.Implementations1_2_1, code.api.v1_2_1.Http4s121.resourceDocs),
    "v1.3.0" -> loaded(code.api.v1_3_0.Http4s130.Implementations1_3_0, code.api.v1_3_0.Http4s130.resourceDocs),
    "v1.4.0" -> loaded(code.api.v1_4_0.Http4s140.Implementations1_4_0, code.api.v1_4_0.Http4s140.resourceDocs),
    "v2.0.0" -> loaded(code.api.v2_0_0.Http4s200.Implementations2_0_0, code.api.v2_0_0.Http4s200.resourceDocs),
    "v2.1.0" -> loaded(code.api.v2_1_0.Http4s210.Implementations2_1_0, code.api.v2_1_0.Http4s210.resourceDocs),
    "v2.2.0" -> loaded(code.api.v2_2_0.Http4s220.Implementations2_2_0, code.api.v2_2_0.Http4s220.resourceDocs),
    "v3.0.0" -> loaded(code.api.v3_0_0.Http4s300.Implementations3_0_0, code.api.v3_0_0.Http4s300.resourceDocs),
    "v3.1.0" -> loaded(code.api.v3_1_0.Http4s310.Implementations3_1_0, code.api.v3_1_0.Http4s310.resourceDocs),
    "v4.0.0" -> loaded(code.api.v4_0_0.Http4s400.Implementations4_0_0, code.api.v4_0_0.Http4s400.resourceDocs),
    "v5.0.0" -> loaded(code.api.v5_0_0.Http4s500.Implementations5_0_0, code.api.v5_0_0.Http4s500.resourceDocs),
    "v5.1.0" -> loaded(code.api.v5_1_0.Http4s510.Implementations5_1_0, code.api.v5_1_0.Http4s510.resourceDocs),
    "v6.0.0" -> loaded(code.api.v6_0_0.Http4s600.Implementations6_0_0, code.api.v6_0_0.Http4s600.resourceDocs),
    "v7.0.0" -> loaded(code.api.v7_0_0.Http4s700.Implementations7_0_0, code.api.v7_0_0.Http4s700.resourceDocs),
    "Berlin Group v1.3" -> code.api.berlin.group.v1_3.Http4sBGv13.resourceDocs,
    "Berlin Group v2" -> code.api.berlin.group.v2.Http4sBGv2.resourceDocs,
    "UK Open Banking v2.0.0" -> code.api.UKOpenBanking.v2_0_0.Http4sUKOBv200.resourceDocs,
    "UK Open Banking v3.1.0" -> code.api.UKOpenBanking.v3_1_0.Http4sUKOBv310.resourceDocs,
    "UK Open Banking v4.0.1" -> code.api.UKOpenBanking.v4_0_1.Http4sUKOBv401.resourceDocs
  )

  private def describe(doc: ResourceDoc): String =
    s"${doc.partialFunctionName} (${doc.requestVerb} ${doc.requestUrl})"

  /** Returns one line per doc whose own URL resolves to a different doc, or to none. */
  private def misresolved(docs: ArrayBuffer[ResourceDoc]): List[String] = {
    val index = ResourceDocMatcher.buildIndex(docs)
    val sameVerbAndUrlCount = docs.groupBy(doc => (doc.requestVerb.toUpperCase, doc.requestUrl)).mapValues(_.size)
    docs.toList
      .filter(doc => sameVerbAndUrlCount((doc.requestVerb.toUpperCase, doc.requestUrl)) == 1)
      .flatMap { doc =>
        val version = doc.implementedInApiVersion
        val path = Uri.Path.unsafeFromString(s"/${version.urlPrefix}/${version}${doc.requestUrl}")
        ResourceDocMatcher.findResourceDoc(doc.requestVerb, path, index) match {
          case Some(resolved) if resolved eq doc => None
          case Some(resolved) => Some(s"${describe(doc)} resolves to ${describe(resolved)}")
          case None => Some(s"${describe(doc)} resolves to no doc")
        }
      }
  }

  feature("Every ResourceDoc is the doc the middleware picks for its own URL") {
    catalogs.foreach { case (label, docs) =>
      scenario(s"$label: each doc's own URL resolves to that doc", ResourceDocSelfResolveTag) {
        docs should not be empty
        val problems = misresolved(docs)
        withClue(s"\n${problems.mkString("\n")}\n") {
          problems shouldBe empty
        }
      }
    }
  }
}
