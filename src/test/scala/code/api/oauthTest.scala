package code.api

import org.scalatest._
import dispatch._
import oauth._
import OAuth._
import net.liftweb.util.Helpers._
import net.liftweb.http.S
import code.api.test.{ServerSetup, APIResponse}
import code.model.dataAccess.OBPUser
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import code.model.TokenType._
import org.scalatest.selenium._
import org.openqa.selenium.WebDriver


case class OAuhtResponse(
  code: Int,
  body: String
)

class OAuthTest extends ServerSetup{

  val oauthRequest = baseRequest / "oauth"



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
      email("testuser1@example.com").
      password(user1Password).
      validated(true).
      firstName(randomString(10)).
      lastName(randomString(10)).
      saveMe

  lazy val consumer = new Consumer (testConsumer.key,testConsumer.secret)
  lazy val notRegisteredConsumer = new Consumer (randomString(5),randomString(5))

  def getRequest(req: Request, headers : Map[String,String] = Map()): h.HttpPackage[OAuhtResponse] = {
    val jsonReq = req <:< headers
    val jsonHandler = jsonReq >- {s => s}
    h x jsonHandler{
       case (code, _, _, body) => OAuhtResponse(code, body())
    }
  }

  def getRequestToken(consumer: Consumer, callback: String): h.HttpPackage[OAuhtResponse] = {
    val request = (oauthRequest / "initiate").POST <@ (consumer, callback)
    getRequest(request)
  }

  def extractToken(body: String) : Token = {
    val oauthParams = body.split("&")
    val token = oauthParams(0).split("=")(1)
    val secret = oauthParams(1).split("=")(1)
    Token(token, secret)
  }

  /************************ the tags ************************/

  object RequestToken extends Tag("requestToken")
  object Validator extends Tag("validator")


  /************************ the tests ************************/
  feature("request token"){
    scenario("we get a request token", RequestToken) {
      Given("The application is registered and does not have a callback URL")
      When("the request is sent")
      val reply = getRequestToken(consumer, OAuth.oob)
      Then("we should get a 200 created code")
      reply.code should equal (200)
      And("we can extract the token")
      val requestToken = extractToken(reply.body)
    }
    scenario("we get a request token with a callback URL", RequestToken) {
      Given("The application is registered and have a callback URL")
      When("the request is sent")
      val reply = getRequestToken(consumer, "localhost:8080/app")
      Then("we should get a 200 created code")
      reply.code should equal (200)
      And("we can extract the token")
      val requestToken = extractToken(reply.body)
    }
    scenario("we don't get a request token since the application is not registered", RequestToken) {
      Given("The application not registered")
      When("the request is sent")
      val reply = getRequestToken(notRegisteredConsumer, OAuth.oob)
      Then("we should get a 401 created code")
      reply.code should equal (401)
    }
    scenario("we don't get a request token since the application is not registered even with a callback URL", RequestToken) {
      Given("The application not registered")
      When("the request is sent")
      val reply = getRequestToken(notRegisteredConsumer, "localhost:8080/app")
      Then("we should get a 401 created code")
      reply.code should equal (401)
    }
  }
  feature("validator"){
    scenario("user login and get redirected to the application back", Validator){
      Given("we will use a valid request token")
      val reply = getRequestToken(consumer, "http://localhost:8000")
      val requestToken = extractToken(reply.body)
      val loginPage = (oauthRequest / "authorize" <<? List(("oauth_token", requestToken.value))).to_uri.toString
      object browser extends HtmlUnit{
        implicit val driver = webDriver
        def getVerifier(loginPage: String, userName: String, password: String) : String = {
          go.to(loginPage)
          textField("username").value = userName
          val pwField = NameQuery("password").webElement
          pwField.clear()
          pwField.sendKeys(password)
          click on XPathQuery("""//input[@type='submit']""")
          val newURL = currentUrl
          val params = newURL.split("&")
          val verifier = params(1).split("=")(0)
          close()
          quit()
          verifier
        }
      }
      browser.getVerifier(loginPage, user1.email.get, user1Password).nonEmpty should equal (true)
    }
    scenario("user login and is asked to enter the verifier", Validator){
      Given("we will use a valid request token")
      val reply = getRequestToken(consumer, OAuth.oob)
      val requestToken = extractToken(reply.body)
      val loginPage = (oauthRequest / "authorize" <<? List(("oauth_token", requestToken.value))).to_uri.toString
      object browser extends HtmlUnit{
        implicit val driver = webDriver
        def getVerifier(loginPage: String, userName: String, password: String) : String = {
          go.to(loginPage)
          textField("username").value = userName
          val pwField = NameQuery("password").webElement
          pwField.clear()
          pwField.sendKeys(password)
          click on XPathQuery("""//input[@type='submit']""")
          val x = XPathQuery("""//div[@id='verifier']""").element
          x.text
        }
      }
      browser.getVerifier(loginPage, user1.email.get, user1Password).nonEmpty should equal (true)
    }
  }
}
