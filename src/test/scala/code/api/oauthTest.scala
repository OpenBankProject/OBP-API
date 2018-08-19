/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
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

import java.util.ResourceBundle
import code.api.oauth1a.OauthParams._
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ErrorMessages}
import code.consumer.Consumers
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import code.setup.ServerSetup
import code.util.Helper.MdcLoggable
import dispatch.Defaults._
import dispatch._
import net.liftweb.common.{Box, Failure}
import net.liftweb.http.LiftRules
import net.liftweb.util.Helpers._
import org.scalatest._
import org.scalatest.selenium._

import scala.concurrent.Await
import scala.concurrent.duration._

case class OAuthResponse(
  code: Int,
  body: String
)

class OAuthTest extends ServerSetup {

  def oauthRequest = baseRequest / "oauth"

  //a url that will be guaranteed to resolve when the oauth redirects us to it
  val selfCallback = APIUtil.getPropsValue("hostname").openOrThrowException("hostname not set")

  val accountValidationError = ResourceBundle.getBundle(LiftRules.liftCoreResourceName).getObject("account.validation.error").toString

  lazy val testConsumer = Consumers.consumers.vend.createConsumer(Some(randomString(40).toLowerCase), Some(randomString(40).toLowerCase), Some(true), Some("test application"), None, None, None, Some(selfCallback), None).openOrThrowException(attemptedToOpenAnEmptyBox)

  lazy val disabledTestConsumer = Consumers.consumers.vend.createConsumer(Some(randomString(40).toLowerCase), Some(randomString(40).toLowerCase), Some(false), Some("test application disabled"), None, None, None, Some(selfCallback), None).openOrThrowException(attemptedToOpenAnEmptyBox)

  lazy val user1Password = randomString(10)
  lazy val user1 =
    AuthUser.create.
      email(randomString(3)+"@example.com").
      username(randomString(9)).
      password(user1Password).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  lazy val user2Password = randomString(10)
  lazy val user2 =
    AuthUser.create.
      email(randomString(3)+"@example.com").
      username(randomString(9)).
      password(user2Password).
      validated(false).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  lazy val consumer = new Consumer (testConsumer.key.get,testConsumer.secret.get)
  lazy val disabledConsumer = new Consumer (disabledTestConsumer.key.get, disabledTestConsumer.secret.get)
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

  case class Browser() extends HtmlUnit with MdcLoggable{
    implicit val driver = webDriver
    def getVerifier(loginPage: String, userName: String, password: String) : Box[String] = {
      tryo{
        go.to(loginPage)
        textField("username").value = userName
        val pwField = NameQuery("password").webElement
        pwField.clear()
        pwField.sendKeys(password)
        click on XPathQuery("""//input[@type='submit']""")
        val newURL = currentUrl
        val newPageSource = pageSource
        val verifier =
          if(newURL.contains("verifier"))
          {
            logger.info("redirected during oauth")
            val params = newURL.split("&")
            params(1).split("=")(1)
          } 
          else if(newPageSource.contains(ErrorMessages.UsernameHasBeenLocked ))
          {
            logger.info("the username has been locked")
            XPathQuery(ErrorMessages.UsernameHasBeenLocked).element.text
          }
          else if(newPageSource.contains(accountValidationError))
          {
            logger.info(accountValidationError)
            accountValidationError
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
    val loginPage = (oauthRequest / "authorize" <<? List((TokenName, requestToken))).toRequest.getUrl
    b.getVerifier(loginPage, userName, password)
  }

  def getVerifier(userName: String, password: String): Box[String] = {
    val b = Browser()
    val loginPage = (oauthRequest / "authorize").toRequest.getUrl
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
    scenario("We don't get a request token since the application is not enabled", RequestToken, Oauth) {
      Given("The application is not enabled")
      When("The request is sent")
      val reply = getRequestToken(disabledConsumer, oob)
      Then("We should get a 401 code")
      reply.code should equal (401)
      And("We should get message")
      reply.body should equal (ErrorMessages.InvalidConsumerCredentials)
    }
  }

  feature("Verifier"){
    scenario("the user login and get redirected to the application back", Verifier, Oauth){
      Given("we will use a valid request token")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)
      When("the browser is launched to login")
      val verifier = getVerifier(requestToken.value, user1.username.get, user1Password)
      Then("we should get a verifier")
      verifier.openOrThrowException(attemptedToOpenAnEmptyBox).nonEmpty should equal (true)
    }
    scenario("the user login and is asked to enter the verifier manually", Verifier, Oauth){
      Given("we will use a valid request token")
      val reply = getRequestToken(consumer, oob)
      val requestToken = extractToken(reply.body)
      When("the browser is launched to login")
      val verifier = getVerifier(requestToken.value, user1.username.get, user1Password)
      Then("we should get a verifier")
      verifier.isEmpty should equal (false)
    }
    scenario("the user cannot login because there is no token", Verifier, Oauth){
      Given("there will be no token")
      When("the browser is launched to login")
      val verifier = getVerifier(user1.username.get, user1Password)
      Then("we should not get a verifier")
      verifier.isEmpty should equal (true)
    }
    scenario("the user cannot login because the token does not exist", Verifier, Oauth){
      Given("we will use a random request token")
      When("the browser is launched to login")
      val verifier = getVerifier(randomString(4), user1.username.get, user1Password)
      Then("we should not get a verifier")
      verifier.isEmpty should equal (true)
    }
  }
  feature("access token"){
    scenario("we get an access token without a callback", AccessToken, Oauth){
      Given("we will first get a request token and a verifier")
      val reply = getRequestToken(consumer, oob)
      val requestToken = extractToken(reply.body)
      val verifier = getVerifier(requestToken.value, user1.username.get, user1Password)
      When("when we ask for an access token")
      val accessToken = getAccessToken(consumer, requestToken, verifier.openOrThrowException(attemptedToOpenAnEmptyBox))
      Then("we should get an access token")
      extractToken(accessToken.body)
    }
    scenario("we get an access token with a callback", AccessToken, Oauth){
      Given("we will first get a request token and a verifier")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)
      val verifier = getVerifier(requestToken.value, user1.username.get, user1Password)
      When("when we ask for an access token")
      val accessToken = getAccessToken(consumer, requestToken, verifier.openOrThrowException(attemptedToOpenAnEmptyBox))
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
      val verifier = getVerifier(requestToken.value, user1.username.get, user1Password)
      When("when we ask for an access token with a request token")
      val randomRequestToken = Token(randomString(5), randomString(5))
      val accessTokenReply = getAccessToken(consumer, randomRequestToken, verifier.openOrThrowException(attemptedToOpenAnEmptyBox))
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

  feature("Login in locked") {
    scenario("valid Username, invalid password, login in too many times. The username will be locked", Verifier, Oauth) {
      Given("we will use a valid request token to get the valid username and password")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)
      
      Then("we set the valid username, invalid password and try more than 5 times")
      val invalidPassword = "wrongpassword"
      var verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      verifier = getVerifier(requestToken.value, user1.username.get, invalidPassword)
      
      Then("we should get a locked account verifier")
      verifier.asInstanceOf[Failure].msg.contains(ErrorMessages.UsernameHasBeenLocked)


      Then("We login in with valid username and password, it will still be failed")
      verifier = getVerifier(requestToken.value, user1.username.get, user1Password)

      Then("we should get a locked account verifier")
      verifier.asInstanceOf[Failure].msg.contains(ErrorMessages.UsernameHasBeenLocked)
      
      Then("We unlock the username")
      LoginAttempt.resetBadLoginAttempts(user1.username.get)

    }
  }

  feature("We try to login with a user which is not verified") {
    scenario("Valid username, valid password but user is not verified", Verifier, Oauth) {
      Given("we will use a valid request token to get the valid username and password")
      val reply = getRequestToken(consumer, selfCallback)
      val requestToken = extractToken(reply.body)

      Then("we set the valid username, valid  password and try to login")
      val verifier = getVerifier(requestToken.value, user2.username.get, user2Password)

      Then("we should get a message: " + accountValidationError)
      verifier.contains(accountValidationError) should equal (true)
    }
  }

}