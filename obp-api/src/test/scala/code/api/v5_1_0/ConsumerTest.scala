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
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
 */
package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{InvalidJsonFormat, UserNotLoggedIn}
import code.api.v3_1_0.ConsumerJsonV310
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ConsumerTest extends V510ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object CreateConsumer extends Tag(nameOf(Implementations5_1_0.createConsumer))
  object GetConsumers extends Tag(nameOf(Implementations5_1_0.getConsumers))
  object UpdateConsumerRedirectURL extends Tag(nameOf(Implementations5_1_0.updateConsumerRedirectURL))
  object UpdateConsumerLogoURL extends Tag(nameOf(Implementations5_1_0.updateConsumerLogoURL))
  object UpdateConsumerName extends Tag(nameOf(Implementations5_1_0.updateConsumerName))
  object UpdateConsumerCertificate extends Tag(nameOf(Implementations5_1_0.updateConsumerCertificate))
  object GetConsumer extends Tag(nameOf(Implementations5_1_0.getConsumer))
  object CreateMyConsumer extends Tag(nameOf(Implementations5_1_0.createMyConsumer))

  feature("Test all error cases ") {
    scenario("We test the authentication errors", UpdateConsumerName, GetConsumer, CreateConsumer, GetConsumers, UpdateConsumerRedirectURL, UpdateConsumerLogoURL, UpdateConsumerCertificate,  VersionOfApi) {
      When("We make a request v5.1.0")
      lazy val createConsumerRequestJson = SwaggerDefinitionsJSON.createConsumerRequestJsonV510
      val requestApiEndpoint1 = (v5_1_0_Request / "management" / "consumers").POST
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(createConsumerRequestJson))

      val requestApiEndpoint2 = (v5_1_0_Request / "management" / "consumers").GET
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)

      val requestApiEndpoint3= (v5_1_0_Request / "management" / "consumers" / "CONSUMER_ID" / "consumer" / "redirect_url").PUT
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(createConsumerRequestJson))

      val requestApiEndpoint4 = (v5_1_0_Request /"management" / "consumers" / "CONSUMER_ID" / "consumer" / "logo_url").PUT
      val responseApiEndpoint4 = makePutRequest(requestApiEndpoint4, write(createConsumerRequestJson))

      val requestApiUpdateConsumerName = (v5_1_0_Request /"management" / "consumers" / "CONSUMER_ID" / "consumer" / "name").PUT
      val responseApiUpdateConsumerName = makePutRequest(requestApiUpdateConsumerName, write(createConsumerRequestJson))

      val requestApiUpdateConsumerCertificate = (v5_1_0_Request /"management" / "consumers" / "CONSUMER_ID" / "consumer" / "certificate").PUT
      val responseApiUpdateConsumerCertificate = makePutRequest(requestApiUpdateConsumerCertificate, write(createConsumerRequestJson))

      val requestApiEndpoint6 = (v5_1_0_Request / "my" / "consumers").POST
      val responseApiEndpoint6 = makePostRequest(requestApiEndpoint6, write(createConsumerRequestJson))
      
      Then(s"we should get the error messages")
      responseApiEndpoint1.code should equal(401)
      responseApiEndpoint2.code should equal(401)
      responseApiEndpoint3.code should equal(401)
      responseApiEndpoint4.code should equal(401)
      responseApiEndpoint6.code should equal(401)
      responseApiEndpoint1.body.toString contains(s"$UserNotLoggedIn") should be (true)
      responseApiEndpoint2.body.toString contains(s"$UserNotLoggedIn") should be (true)
      responseApiEndpoint3.body.toString contains(s"$UserNotLoggedIn") should be (true)
      responseApiEndpoint4.body.toString contains(s"$UserNotLoggedIn") should be (true)
      responseApiEndpoint6.body.toString contains(s"$UserNotLoggedIn") should be (true)

      responseApiUpdateConsumerName.code should equal(401)
      responseApiUpdateConsumerName.body.toString contains(s"$UserNotLoggedIn") should be (true)

      responseApiUpdateConsumerCertificate.code should equal(401)
      responseApiUpdateConsumerCertificate.body.toString contains(s"$UserNotLoggedIn") should be (true)

      // Endpoint GetConsumer
      val requestApiEndpoint5 = (v5_1_0_Request / "management" / "consumers" / "whatever").GET
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      responseApiEndpoint5.code should equal(401)
      responseApiEndpoint5.body.toString contains(s"$UserNotLoggedIn") should be (true)
    }

    scenario("We test the missing roles errors", UpdateConsumerName, GetConsumer, CreateConsumer, GetConsumers, UpdateConsumerRedirectURL, UpdateConsumerLogoURL, UpdateConsumerCertificate, VersionOfApi) {
      When("We make a request v5.1.0")

      lazy val wrongJsonForTesting = SwaggerDefinitionsJSON.routing
      val requestApiEndpoint1 = (v5_1_0_Request / "management" / "consumers").POST<@ (user1)
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(wrongJsonForTesting))

      val requestApiEndpoint2 = (v5_1_0_Request / "management" / "consumers").GET <@ (user1)
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)

      val requestApiEndpoint3= (v5_1_0_Request / "management" / "consumers" / "CONSUMER_ID" / "consumer" / "redirect_url").PUT<@ (user1)
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(wrongJsonForTesting))

      val requestApiEndpoint4 = (v5_1_0_Request /"management" / "consumers" / "CONSUMER_ID" / "consumer" / "logo_url").PUT<@ (user1)
      val responseApiEndpoint4 = makePutRequest(requestApiEndpoint4, write(wrongJsonForTesting))

      val requestApiUpdateConsumerName = (v5_1_0_Request /"management" / "consumers" / "CONSUMER_ID" / "consumer" / "name").PUT<@ (user1)
      val responseApiUpdateConsumerName = makePutRequest(requestApiUpdateConsumerName, write(wrongJsonForTesting))

      val requestApiUpdateConsumerCertificate = (v5_1_0_Request / "management" / "consumers" / "CONSUMER_ID" / "consumer" / "certificate").PUT <@ (user1)
      val responseApiUpdateConsumerCertificate = makePutRequest(requestApiUpdateConsumerCertificate, write(wrongJsonForTesting))

      Then(s"we should get the error messages")
      responseApiEndpoint1.code should equal(403)
      responseApiEndpoint1.body.toString contains(s"$canCreateConsumer") should be (true)
      responseApiEndpoint2.code should equal(403)
      responseApiEndpoint2.body.toString contains(s"$canGetConsumers") should be (true)
      responseApiEndpoint3.code should equal(403)
      responseApiEndpoint3.body.toString contains(s"$canUpdateConsumerRedirectUrl") should be (true)
      responseApiEndpoint4.code should equal(403)
      responseApiEndpoint4.body.toString contains(s"$canUpdateConsumerLogoUrl") should be (true)
      responseApiUpdateConsumerName.code should equal(403)
      responseApiUpdateConsumerName.body.toString contains(s"$canUpdateConsumerName") should be (true)

      responseApiUpdateConsumerCertificate.code should equal(403)
      responseApiUpdateConsumerCertificate.body.toString contains(s"$canUpdateConsumerCertificate") should be (true)

      // Endpoint GetConsumer
      val requestApiEndpoint5 = (v5_1_0_Request / "management" / "consumers" / "whatever").GET <@ user1
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)
      responseApiEndpoint5.code should equal(403)
      responseApiEndpoint5.body.toString contains (s"$canGetConsumers") should be(true)
    }

    scenario("We added the proper roles, but wrong json", UpdateConsumerName, CreateConsumer, GetConsumers, UpdateConsumerRedirectURL, UpdateConsumerLogoURL,  VersionOfApi) {
      When("we first grant the missing roles:")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerLogoUrl.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerRedirectUrl.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerName.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerCertificate.toString)

      When("We make a request v5.1.0")
      lazy val wrongJsonForTesting = SwaggerDefinitionsJSON.postApiCollectionJson400
      val requestApiEndpoint1 = (v5_1_0_Request / "management" / "consumers").POST<@ (user1)
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(wrongJsonForTesting))

      val requestApiEndpoint3= (v5_1_0_Request / "management" / "consumers" / "CONSUMER_ID" / "consumer" / "redirect_url").PUT<@ (user1)
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(wrongJsonForTesting))

      val requestApiEndpoint4 = (v5_1_0_Request /"management" / "consumers" / "CONSUMER_ID" / "consumer" / "logo_url").PUT<@ (user1)
      val responseApiEndpoint4 = makePutRequest(requestApiEndpoint4, write(wrongJsonForTesting))

      val requestApiUpdateConsumerName = (v5_1_0_Request / "management" / "consumers" / "CONSUMER_ID" / "consumer" / "name").PUT <@ (user1)
      val responseApiUpdateConsumerName = makePutRequest(requestApiUpdateConsumerName, write(wrongJsonForTesting))

      val requestApiUpdateConsumerCertificate = (v5_1_0_Request / "management" / "consumers" / "CONSUMER_ID" / "consumer" / "certificate").PUT <@ (user1)
      val responseApiUpdateConsumerCertificate = makePutRequest(requestApiUpdateConsumerCertificate, write(wrongJsonForTesting))

      Then(s"we should get the error messages")
      responseApiEndpoint1.code should equal(400)
      responseApiEndpoint1.body.toString contains(s"$InvalidJsonFormat") should be (true)
      responseApiEndpoint3.code should equal(400)
      responseApiEndpoint3.body.toString contains(s"$InvalidJsonFormat") should be (true)
      responseApiEndpoint4.code should equal(400)
      responseApiEndpoint4.body.toString contains(s"$InvalidJsonFormat") should be (true)
      responseApiUpdateConsumerName.code should equal(400)
      responseApiUpdateConsumerName.body.toString contains(s"$InvalidJsonFormat") should be (true)

      responseApiUpdateConsumerCertificate.code should equal(400)
      responseApiUpdateConsumerCertificate.body.toString contains(s"$InvalidJsonFormat") should be (true)
    }
  }

  feature(s"test all successful cases") {
    scenario("we create, update and get consumers", UpdateConsumerName, GetConsumer, CreateConsumer, GetConsumers, UpdateConsumerRedirectURL, UpdateConsumerLogoURL, VersionOfApi) {

      When("we first grant the missing roles:")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetConsumers.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerLogoUrl.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerRedirectUrl.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerName.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerCertificate.toString)

      lazy val createConsumerRequestJsonV510 = SwaggerDefinitionsJSON.createConsumerRequestJsonV510
      lazy val consumerRedirectUrlJSON = SwaggerDefinitionsJSON.consumerRedirectUrlJSON
      lazy val consumerLogoUrlJson = SwaggerDefinitionsJSON.consumerLogoUrlJson
      lazy val consumerNameJson = SwaggerDefinitionsJSON.consumerNameJson
      lazy val consumerCertificateJson = SwaggerDefinitionsJSON.consumerCertificateJson
      val requestApiEndpoint1 = (v5_1_0_Request / "management" / "consumers").POST<@ (user1)
      val responseApiEndpoint1 = makePostRequest(requestApiEndpoint1, write(createConsumerRequestJsonV510))
      val consumerId = responseApiEndpoint1.body.extract[ConsumerJsonV510].consumer_id

      val requestApiEndpoint2 = (v5_1_0_Request / "management" / "consumers").GET <@ (user1)
      val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)

      val requestApiEndpoint3= (v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "redirect_url").PUT<@ (user1)
      val responseApiEndpoint3 = makePutRequest(requestApiEndpoint3, write(consumerRedirectUrlJSON))
      val redirectUrl = responseApiEndpoint3.body.extract[ConsumerJsonV510].redirect_url
      redirectUrl shouldBe(consumerRedirectUrlJSON.redirect_url)
      
      
      val requestApiEndpoint4 = (v5_1_0_Request /"management" / "consumers" / consumerId / "consumer" / "logo_url").PUT<@ (user1)
      val responseApiEndpoint4 = makePutRequest(requestApiEndpoint4, write(consumerLogoUrlJson))
      val logoUrl = responseApiEndpoint4.body.extract[ConsumerJsonV510].logo_url
      logoUrl.head shouldBe(consumerLogoUrlJson.logo_url)

      val requestApiUpdateConsumerName = (v5_1_0_Request /"management" / "consumers" / consumerId / "consumer" / "name").PUT<@ (user1)
      val responseApiUpdateConsumerName = makePutRequest(requestApiUpdateConsumerName, write(consumerNameJson))
      val name = responseApiUpdateConsumerName.body.extract[ConsumerJsonV510].app_name
      name shouldBe(consumerNameJson.app_name)

      val requestApiUpdateConsumerCertificate = (v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "certificate").PUT <@ (user1)
      val responseApiUpdateConsumerCertificate = makePutRequest(requestApiUpdateConsumerCertificate, write(consumerCertificateJson))
      val certificatePem = responseApiUpdateConsumerCertificate.body.extract[ConsumerJsonV510].certificate_pem
      certificatePem shouldBe(consumerCertificateJson.certificate)
      
      Then(s"we should get the error messages")
      responseApiEndpoint1.code should equal(201)
      responseApiEndpoint2.code should equal(200)
      responseApiEndpoint3.code should equal(200)
      responseApiEndpoint4.code should equal(200)
      
      Then("we try to get the consumers and check it ")
      
      {
        val requestApiEndpoint2 = (v5_1_0_Request / "management" / "consumers").GET <@ (user1)
        val responseApiEndpoint2 = makeGetRequest(requestApiEndpoint2)

        val consumers = responseApiEndpoint2.body.extract[ConsumersJsonV510].consumers
        consumers.find(_.consumer_id ==consumerId).head.redirect_url shouldBe(consumerRedirectUrlJSON.redirect_url)
        consumers.find(_.consumer_id ==consumerId).head.logo_url.head shouldBe(consumerLogoUrlJson.logo_url)

        // Endpoint GetConsumer
        val requestApiEndpoint5 = (v5_1_0_Request / "management" / "consumers" / consumerId).GET <@ user1
        val consumer = makeGetRequest(requestApiEndpoint5).body.extract[ConsumerJsonV310]
        consumer.consumer_id shouldBe consumerId
      }


      val requestApiEndpoint6 = (v5_1_0_Request / "my" / "consumers").POST<@ (user1)
      val responseApiEndpoint6 = makePostRequest(requestApiEndpoint6, write(createConsumerRequestJsonV510))
      val consumerJson6 = responseApiEndpoint6.body.extract[ConsumerJsonOnlyForPostResponseV510]
      consumerJson6.app_name shouldBe createConsumerRequestJsonV510.app_name
      consumerJson6.redirect_url shouldBe createConsumerRequestJsonV510.redirect_url
      consumerJson6.logo_url.headOption shouldBe createConsumerRequestJsonV510.logo_url.headOption
      consumerJson6.description shouldBe createConsumerRequestJsonV510.description
      consumerJson6.developer_email shouldBe createConsumerRequestJsonV510.developer_email
      consumerJson6.enabled shouldBe createConsumerRequestJsonV510.enabled
      consumerJson6.certificate_pem shouldBe createConsumerRequestJsonV510.client_certificate.head

    }
  }

}
