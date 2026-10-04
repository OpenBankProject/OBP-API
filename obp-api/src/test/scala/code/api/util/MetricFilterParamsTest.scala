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

import code.api.util.APIUtil.HTTPParam
import org.scalatest.{FlatSpec, Matchers}

/**
 * This suite checks that the documented metric filters reach a query. A filter name is mapped to its query
 * param in one place and read in another (APIUtil.createQueriesByHttpParams); a name mapped but not read was
 * silently ignored, which is how consent_reference_id and certificate_trust went unapplied.
 */
class MetricFilterParamsTest extends FlatSpec with Matchers {

  private def queries(name: String, value: String): List[OBPQueryParam] =
    APIUtil.createQueriesByHttpParams(List(HTTPParam(name, List(value)))).openOrThrowException("filters")

  "createQueriesByHttpParams" should "read consent_reference_id" in {
    queries("consent_reference_id", "fd13b9af") should contain(OBPConsentReferenceId("fd13b9af"))
  }

  it should "read certificate_trust" in {
    queries("certificate_trust", "forwarded") should contain(OBPCertificateTrust("forwarded"))
  }

  it should "read domain_api_url" in {
    queries("domain_api_url", "/carbon-registry/v1/") should contain(OBPDomainApiUrl("/carbon-registry/v1/"))
  }
}
