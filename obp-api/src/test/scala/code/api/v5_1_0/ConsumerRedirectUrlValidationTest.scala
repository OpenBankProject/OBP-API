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

package code.api.v5_1_0

import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{InvalidRedirectUrl, RedirectUrlHostNotAllowed}
import code.api.v2_1_0.{ConsumerPostJSON, ConsumerRedirectUrlJSON}
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.model.Consumer
import code.setup.APIResponse
import net.liftweb.util.Helpers.randomString
import org.json4s._
import org.json4s.native.Serialization.write

/**
 * This suite checks that every endpoint that writes a Consumer's redirect URL rejects one that breaks the
 * redirect URL rules (code.api.util.RedirectUrlValidation), and that the OIDC client endpoints never hand
 * out a stored entry that breaks them.
 */
class ConsumerRedirectUrlValidationTest extends V510ServerSetup {

  private val v2_1_0 = baseRequest / "obp" / "v2.1.0"
  private val v2_2_0 = baseRequest / "obp" / "v2.2.0"
  private val v4_0_0 = baseRequest / "obp" / "v4.0.0"
  private val v6_0_0 = baseRequest / "obp" / "v6.0.0"

  private val badRedirectUrl = "https://app.example.com/cb,javascript:alert(1)"

  private def createConsumerRequestJson(redirectUrl: String) = CreateConsumerRequestJsonV510(
    app_name = "redirect-test-" + randomString(8), app_type = "Confidential", description = "redirect test",
    developer_email = "developer@example.com", company = "Example", redirect_url = redirectUrl,
    enabled = true, client_certificate = None, logo_url = None)

  private def consumerPostJson(redirectUrl: String) = ConsumerPostJSON(
    "redirect-test-" + randomString(8), "Confidential", "redirect test", "developer@example.com",
    redirectUrl, "", true, new Date(), "")

  private def messageOf(body: JValue): String = (body \ "message") match {
    case JString(message) => message
    case _ => ""
  }

  private def shouldBeRejected(response: APIResponse): Unit = {
    response.code should equal(400)
    messageOf(response.body) should startWith(InvalidRedirectUrl)
    messageOf(response.body) should include("javascript:alert(1)")
  }

  private def createConsumerOwnedByUser1(redirectUrl: String): String = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
    val response = makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1), write(createConsumerRequestJson(redirectUrl)))
    response.code should equal(201)
    (response.body \ "consumer_id").extract[String]
  }

  feature("Creating a Consumer with a redirect URL that breaks the rules is rejected") {

    scenario("v5.1.0 createConsumer") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      shouldBeRejected(makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1), write(createConsumerRequestJson(badRedirectUrl))))
    }

    scenario("v5.1.0 createMyConsumer") {
      shouldBeRejected(makePostRequest((v5_1_0_Request / "my" / "consumers").POST <@ (user1), write(createConsumerRequestJson(badRedirectUrl))))
    }

    scenario("v4.0.0 createConsumer") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      shouldBeRejected(makePostRequest((v4_0_0 / "management" / "consumers").POST <@ (user1), write(consumerPostJson(badRedirectUrl))))
    }

    scenario("v2.2.0 createConsumer") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      shouldBeRejected(makePostRequest((v2_2_0 / "management" / "consumers").POST <@ (user1), write(consumerPostJson(badRedirectUrl))))
    }

    scenario("an allowed redirect URL is still accepted") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      val response = makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1),
        write(createConsumerRequestJson("https://app.example.com/cb http://localhost:5173/cb com.example.app:/cb")))
      response.code should equal(201)
    }
  }

  feature("redirect_url_allowed_hosts limits the hosts a Consumer redirect URL may point to") {

    scenario("with the prop unset, any host that meets the rules is accepted") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      val response = makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1),
        write(createConsumerRequestJson("https://any-third-party.example/cb")))
      response.code should equal(201)
    }

    scenario("with the prop set, an unlisted host is refused with RedirectUrlHostNotAllowed and a listed one is accepted") {
      setPropsValues("redirect_url_allowed_hosts" -> ".bank.example")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)

      val refused = makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1),
        write(createConsumerRequestJson("https://evil.example/cb")))
      refused.code should equal(400)
      messageOf(refused.body) should startWith(RedirectUrlHostNotAllowed)

      val accepted = makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1),
        write(createConsumerRequestJson("https://portal.bank.example/cb")))
      accepted.code should equal(201)
    }
  }

  feature("Updating a Consumer's redirect URL to one that breaks the rules is rejected") {

    scenario("v5.1.0 updateConsumerRedirectURL") {
      val consumerId = createConsumerOwnedByUser1("https://app.example.com/cb")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerRedirectUrl.toString)
      shouldBeRejected(makePutRequest((v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "redirect_url").PUT <@ (user1),
        write(ConsumerRedirectUrlJSON(badRedirectUrl))))
    }

    scenario("v2.1.0 updateConsumerRedirectUrl") {
      val consumerId = createConsumerOwnedByUser1("https://app.example.com/cb")
      val primaryId = Consumers.consumers.vend.getConsumerByConsumerId(consumerId).map(_.id.get).openOrThrowException("consumer just created")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerRedirectUrl.toString)
      shouldBeRejected(makePutRequest((v2_1_0 / "management" / "consumers" / primaryId.toString / "consumer" / "redirect_url").PUT <@ (user1),
        write(ConsumerRedirectUrlJSON(badRedirectUrl))))
    }
  }

  feature("The OIDC client endpoints leave out stored entries that break the rules") {

    scenario("getOidcClient returns only the allowed entries of a redirect URL saved before the rules existed") {
      // Saved without validation, as a Consumer written before the redirect URL rules existed would be.
      val key = randomString(40).toLowerCase
      Consumer.create.key(key).secret(randomString(40).toLowerCase).name("legacy-" + randomString(8))
        .description("legacy").isActive(true)
        .redirectURL("https://app.example.com/cb,javascript:alert(1) http://public.example.com/cb").saveMe()

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetOidcClient.toString)
      val response = makeGetRequest((v6_0_0 / "oidc" / "clients" / key).GET <@ (user1))
      response.code should equal(200)
      (response.body \ "redirect_uris").extract[List[String]] should equal(List("https://app.example.com/cb"))
    }
  }
}
