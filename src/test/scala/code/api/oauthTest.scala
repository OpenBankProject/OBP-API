/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api

import code.api.util.APIUtil
import net.liftweb.util.Props
import org.scalatest._
import dispatch._, Defaults._
import net.liftweb.util.Helpers._
import net.liftweb.http.S
import net.liftweb.common.{Box, Loggable}
import code.api.test.{ServerSetup, APIResponse}
import code.model.dataAccess.OBPUser
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import code.model.TokenType._
import org.scalatest.selenium._
import org.openqa.selenium.WebDriver
import scala.concurrent.duration._
import scala.concurrent.Await
import APIUtil.OAuth._

case class OAuthResponse(
  code: Int,
  body: String
)

class OAuthTest extends ServerSetup {

  def oauthRequest = baseRequest / "oauth"

  //a url that will be guaranteed to resolve when the oauth redirects us to it
  val selfCallback = Props.get("hostname").openOrThrowException("hostname not set")

  lazy val testConsumer =
    OBPConsumer.create.
      name("test application").
      isActive(true).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      saveMe

  lazy val user1Password = randomString(10)
  lazy val user1 =
    OBPUser.create.
      email(randomString(3)+"@example.com").
      password(user1Password).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  lazy val consumer = new Consumer (testConsumer.key,testConsumer.secret)
  lazy val notRegisteredConsumer = new Consumer (randomString(5),randomString(5))

  private def getAPIResponse(req : Req) : OAuthResponse = {
    Await.result(
      for(response <- Http(req > as.Response(p => p)))
        yield OAuthResponse(response.getStatusCode, response.getResponseBody)
    , Duration.Inf)
  }

  def sendPostRequest(req: Req): OAuthResponse = {
    val postReq = req.POST
    getAPIResponse(postReq)
  }

  def getRequestToken(consumer: Consumer, callback: String): OAuthResponse = {
    val request = (oauthRequest / "initiate").POST <@ (consumer, callback)
    sendPostRequest(request)
  }

  def getAccessToken(consumer: Consumer, requestToken : Token, verifier : String): OAuthResponse = {
    val request = (oauthRequest / "token").POST <@ (consumer, requestToken, verifier)
    sendPostRequest(request)
  }

  def extractToken(body: String) : Token = {
    val oauthParams = body.split("&")
    val token = oauthParams(0).split("=")(1)
    val secret = oauthParams(1).split("=")(1)
    Token(token, secret)
  }

  case class Browser() extends HtmlUnit with Loggable{
    implicit val driver = webDriver
    def getVerifier(loginPage: String, userName: String, password: String) : Box[String] = {
      tryo{
        go.to(loginPage)
        emailField("username").value = userName
        val pwField = NameQuery("password").webElement
        pwField.clear()
        pwField.sendKeys(password)
        click on XPathQuery("""//input[@type='submit']""")
        val newURL = currentUrl
        val verifier =
          if(newURL.contains("verifier"))
          {
            logger.info("redirected during oauth")
            val params = newURL.split("&")
            params(1).split("=")(1)
          }
          else{
            logger.info("the verifier is in the page")
            XPathQuery("""//div[@id='verifier']""").element.text
          }
        close()
        quit()
        verifier
      }
    }
  }

  def getVerifier(requestToken: String, userName: String, password: String): Box[String] = {
    val b = Browser()
    val loginPage = (oauthRequest / "authorize" <<? List(("oauth_token", requestToken))).build().getUrl
    b.getVerifier(loginPage, userName, password)
  }

  def getVerifier(userName: String, password: String): Box[String] = {
    val b = Browser()
    val loginPage = (oauthRequest / "authorize").build().getUrl
    b.getVerifier(loginPage, userName, password)
  }

  /************************ the tags ************************/
  object Current extends Tag("current")
  object Oauth extends Tag("oauth")
  object RequestToken extends Tag("requestToken")
  object AccessToken extends Tag("accessToken")
  object Verifier extends Tag("verifier")

  /************************ the tests ************************/
  feature("request token"){
    scenario("we get a request token", RequestToken, Oauth) {
      Given("The application is registered and does not have a callback URL")
      When("the request is sent")
      val reply = getRequestToken(consumer, oob)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("we can extract the token form the body")
      val requestToken = extractToken(reply.body)
    }
    scenario("we get a request token with a callback URL", RequestToken, Oauth) {
      Given("The application is registered and have a callback URL")
      When("the request is sent")
      val reply = getRequestToken(consumer, "localhost:8080/app")
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("we can extract the token form the body")
      val requestToken = extractToken(reply.body)
    }
    scenario("we don't get a request token since the application is not registered", RequestToken, Oauth) {
      Given("The application not registered")
      When("the request is sent")
      val reply = getRequestToken(notRegisteredConsumer, oob)
      Then("we should get a 401 code")
      reply.code should equal (401)
    }
    scenario("we don't get a request token since the application is not registered even with a callback URL", RequestToken, Oauth) {
      Given("The application not registered")
      When("the request is sent")
      val reply = getRequestToken(notRegisteredConsumer, "localhost:8080/app")
      Then("we should get a 401 code")
      reply.code should equal (401)
    }
  }

  feature("Verifier"){
    scenario("the user login and get redirected to the application back", Verifier, Oauth){
      Given("we will use a valid request token")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)
      When("the browser is launched to login")
      val verifier = getVerifier(requestToken.value, user1.email.get, user1Password)
      Then("we should get a verifier")
      verifier.get.nonEmpty should equal (true)
    }
    scenario("the user login and is asked to enter the verifier manually", Verifier, Oauth){
      Given("we will use a valid request token")
      val reply = getRequestToken(consumer, oob)
      val requestToken = extractToken(reply.body)
      When("the browser is launched to login")
      val verifier = getVerifier(requestToken.value, user1.email.get, user1Password)
      Then("we should get a verifier")
      verifier.isEmpty should equal (false)
    }
    scenario("the user cannot login because there is no token", Verifier, Oauth){
      Given("there will be no token")
      When("the browser is launched to login")
      val verifier = getVerifier(user1.email.get, user1Password)
      Then("we should not get a verifier")
      verifier.isEmpty should equal (true)
    }
    scenario("the user cannot login because the token does not exist", Verifier, Oauth){
      Given("we will use a random request token")
      When("the browser is launched to login")
      val verifier = getVerifier(randomString(4), user1.email.get, user1Password)
      Then("we should not get a verifier")
      verifier.isEmpty should equal (true)
    }
  }
  feature("access token"){
    scenario("we get an access token without a callback", AccessToken, Oauth){
      Given("we will first get a request token and a verifier")
      val reply = getRequestToken(consumer, oob)
      val requestToken = extractToken(reply.body)
      val verifier = getVerifier(requestToken.value, user1.email.get, user1Password)
      When("when we ask for an access token")
      val accessToken = getAccessToken(consumer, requestToken, verifier.get)
      Then("we should get an access token")
      extractToken(accessToken.body)
    }
    scenario("we get an access token with a callback", AccessToken, Oauth){
      Given("we will first get a request token and a verifier")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)
      val verifier = getVerifier(requestToken.value, user1.email.get, user1Password)
      When("when we ask for an access token")
      val accessToken = getAccessToken(consumer, requestToken, verifier.get)
      Then("we should get an access token")
      extractToken(accessToken.body)
    }
    scenario("we don't get an access token because the verifier is wrong", AccessToken, Oauth){
      Given("we will first get a request token and a random verifier")
      val reply = getRequestToken(consumer, oob)
      val requestToken = extractToken(reply.body)
      When("when we ask for an access token")
      val accessTokenReply = getAccessToken(consumer, requestToken, randomString(5))
      Then("we should get a 401")
      accessTokenReply.code should equal (401)
    }
    scenario("we don't get an access token because the request token is wrong", AccessToken, Oauth){
      Given("we will first get request token and a verifier")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)
      val verifier = getVerifier(requestToken.value, user1.email.get, user1Password)
      When("when we ask for an access token with a request token")
      val randomRequestToken = Token(randomString(5), randomString(5))
      val accessTokenReply = getAccessToken(consumer, randomRequestToken, verifier.get)
      Then("we should get a 401")
      accessTokenReply.code should equal (401)
    }
    scenario("we don't get an access token because the requestToken and the verifier are wrong", AccessToken, Oauth){
      Given("we will first get request token and a verifier")
      val reply = getRequestToken(consumer, selfCallback)
      When("when we ask for an access token with a request token")
      val randomRequestToken = Token(randomString(5), randomString(5))
      val accessTokenReply = getAccessToken(consumer, randomRequestToken, randomString(5))
      Then("we should get a 401")
      accessTokenReply.code should equal (401)
    }
  }
}