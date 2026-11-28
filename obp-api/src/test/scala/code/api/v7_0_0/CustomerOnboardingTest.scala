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
package code.api.v7_0_0

import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateCustomer, CanCreateUserCustomerLink, CanSendEmailToCustomerAtOneBank}
import code.api.v2_0_0.CreateUserCustomerLinkJson
import code.api.v6_0_0.{CustomerJsonV600, PostCustomerJsonV600, V600ServerSetup}
import code.scope.Scope
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This suite checks that an application acting as itself, such as a programme website, onboards a person as a
 * Customer of its space with the Create Customer and Create User Customer Link Scopes, and can then email them.
 */
class CustomerOnboardingTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"
  def v6 = baseRequest / "obp" / "v6.0.0"
  def v4 = baseRequest / "obp" / "v4.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object Onboarding extends Tag("customerOnboarding")

  private lazy val bankId = testBankId2.value

  private def withScopes[T](roles: String*)(f: => T): T = {
    val added = roles.map(role => Scope.scope.vend.addScope(bankId, testConsumer2.id.toString, role))
    try f finally added.foreach(Scope.scope.vend.deleteScope)
  }

  feature("An application onboards a person as a Customer, and emails them") {

    scenario("With the Scopes, an application acting as itself creates a Customer, links a User, and emails the Customer", Onboarding, VersionOfApi) {
      setPropsValues("customer_emails.sender_address" -> "cohort@example.com")
      val body = write(PostCustomerJsonV600(legal_name = "Ali Example", mobile_phone_number = "+968 9000 0000",
        email = Some("ali@example.com")))
      makePostRequest((v6 / "banks" / bankId / "customers").POST <@ (application2), body).code should equal(403)

      withScopes(CanCreateCustomer.toString, CanCreateUserCustomerLink.toString, CanSendEmailToCustomerAtOneBank.toString) {
        val created = makePostRequest((v6 / "banks" / bankId / "customers").POST <@ (application2), body)
        withClue(created.body) { created.code should equal(201) }
        val customerId = created.body.extract[CustomerJsonV600].customer_id

        val link = makePostRequest((v4 / "banks" / bankId / "user_customer_links").POST <@ (application2),
          write(CreateUserCustomerLinkJson(resourceUser2.userId, customerId)))
        withClue(link.body) { link.code should equal(201) }

        val email = makePostRequest((v7 / "banks" / bankId / "customers" / customerId / "emails").POST <@ (application2),
          write(PostCustomerEmailJsonV700("Your application was received", "Thank you.", None)))
        withClue(email.body) { email.code should equal(201) }
      }
    }
  }
}
