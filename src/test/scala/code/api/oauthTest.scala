package code.api

import org.scalatest._
import dispatch._
import oauth._
import OAuth._
import net.liftweb.util.Helpers._
import code.api.test.{ServerSetup, APIResponse}
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import code.model.TokenType._

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


  /************************ the tags ************************/

  object RequestToken extends Tag("requestToken")

  /************************ the tests ************************/
  feature("request token"){
    scenario("we get a request token", RequestToken) {
      Given("The application is registered and does not have a callback URL")
      When("the request is sent")
      val reply = getRequestToken(consumer, OAuth.oob)
      Then("we should get a 200 created code")
      reply.code should equal (200)
      And("we can extract the token")
      val oauthParams = reply.body.split("&")
      val token = oauthParams(0).split("=")(1)
      val secret = oauthParams(1).split("=")(1)
      val requestToken = Token(token, secret)
    }
    scenario("we get a request token with a callback URL", RequestToken) {
      Given("The application is registered and have a callback URL")
      When("the request is sent")
      val reply = getRequestToken(consumer, "localhost:8080/app")
      Then("we should get a 200 created code")
      reply.code should equal (200)
      And("we can extract the token")
      val oauthParams = reply.body.split("&")
      val token = oauthParams(0).split("=")(1)
      val secret = oauthParams(1).split("=")(1)
      val requestToken = Token(token, secret)
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
}
