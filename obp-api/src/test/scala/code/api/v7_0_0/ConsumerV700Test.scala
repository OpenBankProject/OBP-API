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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.v5_1_0.ConsumerJsonV510
import code.api.v6_0_0.{ConsumerJsonV600, V600ServerSetup}
import code.api.v7_0_0.JSONFactory700.{ConsumerDetailsJsonV700, ConsumerJsonV700}
import code.entitlement.Entitlement
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.JObject
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/** v7.0.0 Get Consumer and the four Consumer field updates answer without consumer_key; v6.0.0 Get Consumer redacts it. */
class ConsumerV700Test extends V600ServerSetup {
  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object GetConsumer extends Tag("getConsumer")
  object UpdateConsumerName extends Tag("updateConsumerName")
  object UpdateConsumerRedirectURL extends Tag("updateConsumerRedirectURL")
  object UpdateConsumerLogoURL extends Tag("updateConsumerLogoURL")
  object UpdateConsumerCertificate extends Tag("updateConsumerCertificate")

  private def fieldNames(body: org.json4s.JValue): Set[String] = body match {
    case JObject(fields) => fields.map(_._1).toSet
    case _ => Set.empty
  }

  private def createConsumerAsUser1(): (String, String) = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateConsumer.toString)
    val response = makePostRequest((v5_1_0_Request / "management" / "consumers").POST <@ (user1),
      write(SwaggerDefinitionsJSON.createConsumerRequestJsonV510))
    response.code should equal(201)
    val created = response.body.extract[ConsumerJsonV510]
    (created.consumer_id, created.consumer_key)
  }

  feature(s"Consumer responses without the key, version $VersionOfApi") {
    scenario("A user without the Role cannot get a Consumer", GetConsumer, VersionOfApi) {
      val response = makeGetRequest((v7_0_0_Request / "management" / "consumers" / testConsumer.consumerId.get).GET <@ (user1))
      response.code should equal(403)
      response.body.toString should include(canGetConsumers.toString)
    }

    scenario("Get Consumer has no consumer_key in v7.0.0 and a redacted one in v6.0.0", GetConsumer, VersionOfApi) {
      val (consumerId, consumerKey) = createConsumerAsUser1()
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetConsumers.toString)

      val v7 = makeGetRequest((v7_0_0_Request / "management" / "consumers" / consumerId).GET <@ (user1))
      v7.code should equal(200)
      v7.body.extract[ConsumerDetailsJsonV700].consumer_id should equal(consumerId)
      fieldNames(v7.body) should not contain("consumer_key")
      v7.body.toString should not include(consumerKey)

      val v6 = makeGetRequest((v6_0_0_Request / "management" / "consumers" / consumerId).GET <@ (user1))
      v6.code should equal(200)
      v6.body.extract[ConsumerJsonV600].consumer_key should equal(APIUtil.RedactedConsumerKey)
      v6.body.toString should not include(consumerKey)
    }

    scenario("The four updates change the field and answer without consumer_key",
      UpdateConsumerName, UpdateConsumerRedirectURL, UpdateConsumerLogoURL, UpdateConsumerCertificate, VersionOfApi) {
      val (consumerId, consumerKey) = createConsumerAsUser1()
      List(canUpdateConsumerName, canUpdateConsumerRedirectUrl, canUpdateConsumerLogoUrl, canUpdateConsumerCertificate)
        .foreach(role => Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString))
      def consumerPath(field: String) = v7_0_0_Request / "management" / "consumers" / consumerId / "consumer" / field

      val name = makePutRequest(consumerPath("name").PUT <@ (user1), write(SwaggerDefinitionsJSON.consumerNameJson))
      name.code should equal(200)
      name.body.extract[ConsumerJsonV700].app_name should equal(SwaggerDefinitionsJSON.consumerNameJson.app_name)

      val redirect = makePutRequest(consumerPath("redirect_url").PUT <@ (user1), write(SwaggerDefinitionsJSON.consumerRedirectUrlJSON))
      redirect.code should equal(200)
      redirect.body.extract[ConsumerJsonV700].redirect_url should equal(SwaggerDefinitionsJSON.consumerRedirectUrlJSON.redirect_url)

      val logo = makePutRequest(consumerPath("logo_url").PUT <@ (user1), write(SwaggerDefinitionsJSON.consumerLogoUrlJson))
      logo.code should equal(200)
      logo.body.extract[ConsumerJsonV700].logo_url should equal(Some(SwaggerDefinitionsJSON.consumerLogoUrlJson.logo_url))

      val certificate = makePutRequest(consumerPath("certificate").PUT <@ (user1), write(SwaggerDefinitionsJSON.consumerCertificateJson))
      certificate.code should equal(200)
      certificate.body.extract[ConsumerJsonV700].certificate_pem should equal(SwaggerDefinitionsJSON.consumerCertificateJson.certificate)

      List(name, redirect, logo, certificate).foreach { response =>
        fieldNames(response.body) should not contain("consumer_key")
        response.body.toString should not include(consumerKey)
      }
    }

    scenario("The v5.1.0 updates keep the field but redact the key", UpdateConsumerName, VersionOfApi) {
      val (consumerId, consumerKey) = createConsumerAsUser1()
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canUpdateConsumerName.toString)
      val response = makePutRequest((v5_1_0_Request / "management" / "consumers" / consumerId / "consumer" / "name").PUT <@ (user1),
        write(SwaggerDefinitionsJSON.consumerNameJson))
      response.code should equal(200)
      response.body.extract[ConsumerJsonV510].consumer_key should equal(APIUtil.RedactedConsumerKey)
      response.body.toString should not include(consumerKey)
    }
  }
}
