/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v5_1_0

import org.json4s.jvalue2monadic
import org.json4s.jvalue2extractable
import code.api.RequestHeader
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.ConsentDisabled
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.consent.MappedConsent
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * With consents.allowed off a consent can never be used as a login, so none is created either: the create endpoints
 * answer OBP-35007 before sending a challenge or storing anything.
 */
class ConsentsDisabledTest extends V510ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object CreateConsent extends Tag(nameOf(Implementations5_1_0.createConsent))

  private val consumerKeyHeader = List((RequestHeader.`Consumer-Key`, user1.map(_._1.key).getOrElse("SHOULD_NOT_HAPPEN")))
  private lazy val body = write(SwaggerDefinitionsJSON.postConsentImplicitJsonV310.copy(entitlements = Nil, views = Nil,
    consumer_id = Some(testConsumer.consumerId)))

  feature(s"Creating a consent while consents are not allowed - $VersionOfApi") {

    scenario("v5.1.0 and v6.0.0 Create Consent (IMPLICIT) are refused with OBP-35007 and store nothing", CreateConsent, VersionOfApi) {
      setPropsValues("consents.allowed" -> "false")
      val before = MappedConsent.count()
      for (version <- List("v5.1.0", "v6.0.0")) {
        val request = (baseRequest / "obp" / version / "my" / "consents" / "IMPLICIT").POST <@ (user1)
        val response = makePostRequest(request, body, consumerKeyHeader)
        response.code should equal(401)
        response.body.extract[ErrorMessage].message should include(ConsentDisabled.trim)
      }
      MappedConsent.count() should equal(before)
    }

    scenario("With consents allowed the same request gets past the switch", CreateConsent, VersionOfApi) {
      setPropsValues("consents.allowed" -> "true")
      val request = (v5_1_0_Request / "my" / "consents" / "IMPLICIT").POST <@ (user1)
      val response = makePostRequest(request, body, consumerKeyHeader)
      (response.body \ "message").extractOpt[String].getOrElse("") should not include "OBP-35007"
    }
  }
}
