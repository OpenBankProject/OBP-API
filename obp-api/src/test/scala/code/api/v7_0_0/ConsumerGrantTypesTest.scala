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

import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.isApplicationActingAsItself
import code.api.util.ApiRole.{CanGetAnyUser, CanGetConsumers, CanUpdateConsumerGrantTypes}
import code.api.util.ErrorMessages.{InvalidConsumerGrantTypes, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.consumer.{ConsumerGrantTypes, Consumers}
import code.entitlement.Entitlement
import code.model.Consumer
import code.model.dataAccess.AuthUser
import code.scope.Scope
import code.setup.TestPasswordConfig
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

class ConsumerGrantTypesTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getConsumerGrantTypes")
  object ApiEndpoint2 extends Tag("updateConsumerGrantTypes")

  def v7 = baseRequest / "obp" / "v7.0.0"
  private def grantTypesPath(consumerId: String) = v7 / "management" / "consumers" / consumerId / "grant-types"
  private def body(grantTypes: String*) = compact(render("grant_types" -> grantTypes.toList))
  private def message(r: code.setup.APIResponse) = r.body.extract[ErrorMessage].message

  /** Sets testConsumer2's grant types for the block, then clears them. */
  private def withGrantTypes(grantTypes: String*)(f: => Unit): Unit = {
    await(Consumers.consumers.vend.updateConsumerGrantTypes(testConsumer2.id.get, grantTypes.toList))
    try f finally await(Consumers.consumers.vend.updateConsumerGrantTypes(testConsumer2.id.get, Nil))
  }
  private def await[T](f: scala.concurrent.Future[T]): T = scala.concurrent.Await.result(f, scala.concurrent.duration.Duration(10, "seconds"))

  private def jwt(claims: (String, String)*): String = {
    val builder = claims.foldLeft(new JWTClaimsSet.Builder()) { case (b, (k, v)) => b.claim(k, v) }
    val signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build())
    signed.sign(new MACSigner(("x" * 32).getBytes("UTF-8")))
    signed.serialize()
  }

  feature("The rules") {

    scenario("parsing and the two kinds of Consumer", VersionOfApi) {
      def consumer(value: String) = Consumer.create.grantTypes(value)
      ConsumerGrantTypes.parse(" Client_Credentials, ,client_credentials ") should equal(List("client_credentials"))
      ConsumerGrantTypes.unknown(List("client_credentials", "password")) should equal(List("password"))

      val notSet = consumer("")
      ConsumerGrantTypes.isSet(notSet) should be(false)
      ConsumerGrantTypes.allows(notSet, ConsumerGrantTypes.DirectLogin) should be(true)
      ConsumerGrantTypes.oauth2GrantTypes(notSet) should equal(ConsumerGrantTypes.DefaultOAuth2)

      val applicationOnly = consumer("client_credentials")
      ConsumerGrantTypes.isClientCredentialsOnly(applicationOnly) should be(true)
      ConsumerGrantTypes.allows(applicationOnly, ConsumerGrantTypes.DirectLogin) should be(false)

      val peopleOnly = consumer("authorization_code,refresh_token,direct_login")
      ConsumerGrantTypes.excludesClientCredentials(peopleOnly) should be(true)
      ConsumerGrantTypes.oauth2GrantTypes(peopleOnly) should equal(List("authorization_code", "refresh_token"))

      val both = consumer("authorization_code,client_credentials")
      ConsumerGrantTypes.isClientCredentialsOnly(both) should be(false)
      ConsumerGrantTypes.excludesClientCredentials(both) should be(false)
    }

    scenario("client-credentials tokens are recognised", VersionOfApi) {
      ConsumerGrantTypes.isClientCredentialsToken(jwt("grant_type" -> "client_credentials", "sub" -> "u1", "azp" -> "app")) should be(true)
      ConsumerGrantTypes.isClientCredentialsToken(jwt("sub" -> "app", "azp" -> "app")) should be(true)
      // Keycloak's service account
      ConsumerGrantTypes.isClientCredentialsToken(jwt("sub" -> "9b1c", "azp" -> "app", "preferred_username" -> "service-account-app")) should be(true)
      // A person
      ConsumerGrantTypes.isClientCredentialsToken(jwt("sub" -> "9b1c", "azp" -> "app", "preferred_username" -> "alice")) should be(false)
    }

    scenario("a Consumer's grant types decide whether the application acts as itself", VersionOfApi) {
      val consumerPk = testConsumer2.id.get.toString
      isApplicationActingAsItself(resourceUser2.userId, consumerPk) should be(false)
      withGrantTypes("client_credentials") {
        isApplicationActingAsItself(resourceUser2.userId, consumerPk) should be(true)
      }
      withGrantTypes("authorization_code") {
        isApplicationActingAsItself("", consumerPk) should be(false)
      }
    }
  }

  feature("Get and Update Consumer Grant Types") {

    scenario("need their Roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      val get = makeGetRequest(grantTypesPath(testConsumer2.consumerId.get).GET <@ (user3))
      get.code should equal(403)
      message(get) should include(UserHasMissingRoles)
      val put = makePutRequest(grantTypesPath(testConsumer2.consumerId.get).PUT <@ (user3), body("client_credentials"))
      put.code should equal(403)
      message(put) should include(UserHasMissingRoles)
    }

    scenario("set, read and clear them; unknown values are refused", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateConsumerGrantTypes.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConsumers.toString)
      val path = grantTypesPath(testConsumer2.consumerId.get)
      try {
        val unknown = makePutRequest(path.PUT <@ (user1), body("password"))
        unknown.code should equal(400)
        message(unknown) should include(InvalidConsumerGrantTypes)
        makePutRequest(path.PUT <@ (user1), body("refresh_token")).code should equal(400)

        val set = makePutRequest(path.PUT <@ (user1), body("Client_Credentials"))
        set.code should equal(200)
        (set.body \ "grant_types").extract[List[String]] should equal(List("client_credentials"))

        val read = makeGetRequest(path.GET <@ (user1))
        read.code should equal(200)
        (read.body \ "oauth2_grant_types").extract[List[String]] should equal(List("client_credentials"))

        val cleared = makePutRequest(path.PUT <@ (user1), body())
        cleared.code should equal(200)
        (cleared.body \ "grant_types").extract[List[String]] should equal(Nil)
        (cleared.body \ "oauth2_grant_types").extract[List[String]] should equal(ConsumerGrantTypes.DefaultOAuth2)
      } finally {
        await(Consumers.consumers.vend.updateConsumerGrantTypes(testConsumer2.id.get, Nil))
      }
    }
  }

  feature("Calls through a Consumer follow its grant types") {

    def currentUser = (v7 / "users" / "current").GET
    def users = (v6_0_0_Request / "users").GET

    scenario("not set: people and the application call through it as before", VersionOfApi) {
      makeGetRequest(currentUser <@ (user2)).code should equal(200)
      val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, CanGetAnyUser.toString)
      try makeGetRequest(users <@ (application2)).code should equal(200)
      finally Scope.scope.vend.deleteScope(scope)
    }

    scenario("client credentials only: a person is refused, the application acts as itself", VersionOfApi) {
      val scope = Scope.scope.vend.addScope("", testConsumer2.id.get.toString, CanGetAnyUser.toString)
      try withGrantTypes("client_credentials") {
        val person = makeGetRequest(currentUser <@ (user2))
        person.code should equal(401)
        message(person) should include("OBP-20410")
        val application = makeGetRequest(users <@ (application2))
        withClue(application.body) { application.code should equal(200) }
      } finally Scope.scope.vend.deleteScope(scope)
    }

    scenario("no client credentials: the application is refused, a person calls as before", VersionOfApi) {
      // The test requests carry their tokens as DirectLogin, so the person needs direct_login here.
      withGrantTypes("authorization_code", "direct_login") {
        val application = makeGetRequest(users <@ (application2))
        application.code should equal(401)
        message(application) should include("OBP-20410")
        val person = makeGetRequest(currentUser <@ (user2))
        withClue(person.body) { person.code should equal(200) }
      }
    }
  }

  feature("DirectLogin follows the Consumer's grant types") {

    scenario("a Consumer without direct_login issues no DirectLogin token", VersionOfApi) {
      val key = randomString(40).toLowerCase
      val username = "grant.types." + randomString(8).toLowerCase
      val password = TestPasswordConfig.VALID_PASSWORD
      AuthUser.create.email(s"$username@example.com").username(username).password(password).validated(true)
        .firstName("Grant").lastName("Types").saveMe()
      val consumer = Consumers.consumers.vend.createConsumer(Some(key), Some(randomString(40).toLowerCase), Some(true),
        Some("grant types test " + randomString(6)), None, Some("description"), Some("grant.types@example.com"),
        None, None, None, None, None).openOrThrowException("consumer")
      val login = v6_0_0_Request / "my" / "logins" / "direct"
      val headers = List(("DirectLogin", s"username=$username, password=$password, consumer_key=$key"))
      try {
        await(Consumers.consumers.vend.updateConsumerGrantTypes(consumer.id.get, List("client_credentials")))
        val refused = makePostRequestAdditionalHeader(login, "", headers)
        refused.code should equal(401)
        message(refused) should include("OBP-20410")

        await(Consumers.consumers.vend.updateConsumerGrantTypes(consumer.id.get, List("direct_login")))
        makePostRequestAdditionalHeader(login, "", headers).code should equal(201)
      } finally {
        AuthUser.find(By(AuthUser.username, username)).foreach(_.delete_!)
      }
    }
  }
}
