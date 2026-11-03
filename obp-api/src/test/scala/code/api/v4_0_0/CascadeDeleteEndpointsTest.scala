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

package code.api.v4_0_0

import code.api.util.APIUtil
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.Http4sResourceDocAggregation
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.api.v7_0_0.Http4s700DynamicEntityDefinitions
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * With allow_cascade_delete=true, which every ordinary shard runs with, the cascade-delete
 * endpoints are routed and documented. CascadeDeleteDisabledTest covers the default, false, in a
 * JVM of its own: the routes and ResourceDocs are fixed when the API objects initialise.
 */
class CascadeDeleteEndpointsTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  feature("allow_cascade_delete=true") {
    scenario("The prop is on in this JVM", VersionOfApi) {
      CascadeDeleteEndpoints.enabled should be(true)
    }

    scenario("Every cascade-delete endpoint is routed and listed in the Resource Docs", VersionOfApi) {
      val found = CascadeDeleteEndpoints.found
      found("v4.0.0 routed docs") should equal(CascadeDeleteEndpoints.v400Names.toSet)
      found("v4.0.0 catalog") should equal(CascadeDeleteEndpoints.v400Names.toSet)
      found("v6.0.0 routed docs") should equal(CascadeDeleteEndpoints.v600Names.toSet)
      found("v6.0.0 catalog") should equal((CascadeDeleteEndpoints.v400Names ++ CascadeDeleteEndpoints.v600Names).toSet)
      found("v7.0.0 routed docs") should equal(CascadeDeleteEndpoints.v700Names.toSet)
    }
  }
}

/** The cascade-delete endpoints that allow_cascade_delete switches on and off. */
object CascadeDeleteEndpoints {

  val v400Names: List[String] = List(
    nameOf(Implementations4_0_0.deleteTransactionCascade),
    nameOf(Implementations4_0_0.deleteAccountCascade),
    nameOf(Implementations4_0_0.deleteBankCascade),
    nameOf(Implementations4_0_0.deleteProductCascade),
    nameOf(Implementations4_0_0.deleteCustomerCascade)
  )
  val v600Names: List[String] = List(nameOf(Implementations6_0_0.deleteSystemDynamicEntityCascade))
  val v700Names: List[String] = List(nameOf(Http4s700DynamicEntityDefinitions.deleteDynamicEntityDefinitionCascade))

  private def names(docs: Iterable[ResourceDoc]): Set[String] = docs.map(_.partialFunctionName).toSet

  /** The cascade-delete names in each list the endpoints are routed or documented from. */
  def found: Map[String, Set[String]] = Map(
    "v4.0.0 routed docs" -> (names(Implementations4_0_0.orderedResourceDocs) intersect v400Names.toSet),
    "v4.0.0 catalog" -> (names(Http4sResourceDocAggregation.v400) intersect v400Names.toSet),
    "v6.0.0 routed docs" -> (names(Implementations6_0_0.orderedResourceDocs) intersect v600Names.toSet),
    "v6.0.0 catalog" -> (names(Http4sResourceDocAggregation.v600) intersect (v400Names ++ v600Names).toSet),
    "v7.0.0 routed docs" -> (names(Implementations7_0_0.orderedResourceDocs) intersect v700Names.toSet)
  )

  def enabled: Boolean = APIUtil.cascadeDeleteEnabled
}
