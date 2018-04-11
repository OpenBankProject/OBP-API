package code.api.v2_0_0

import code.api.OAuthResponse
import code.api.util.APIUtil.OAuth.{Consumer, _}
import code.consumer.Consumers
import code.model.{Consumer => OBPConsumer}
import dispatch._
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers._
import org.scalatest.{BeforeAndAfter, Tag}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class CreateUserTest extends V200ServerSetup with BeforeAndAfter {

  object CreateUser extends Tag("createUser")

  val FIRSTNAME = randomString(8).toLowerCase
  val LASTNAME = randomString(16).toLowerCase
  val USERNAME = randomString(10).toLowerCase
  val EMAIL = randomString(10).toLowerCase + "@example.com"
  val PASSWORD = randomString(20)

  //setup Consumer
  val KEY = randomString(40).toLowerCase
  val SECRET = randomString(40).toLowerCase

  before {
    val testConsumer = Consumers.consumers.vend.createConsumer(Some(KEY), Some(SECRET), Some(true), Some("test application"), None, None, None, None, None)
  }

  override lazy val consumer = new Consumer(KEY, SECRET)

  def oauthRequest = baseRequest / "oauth"
  def directLoginRequest = baseRequest / "my" / "logins" / "direct"

  val accessControlOriginHeader = ("Access-Control-Allow-Origin", "*")
  val validHeader = ("Authorization", "DirectLogin username=%s, password=%s, consumer_key=%s".
    format(USERNAME, PASSWORD, KEY))
  val validHeaders = List(accessControlOriginHeader, validHeader)

  private def getAPIResponse(req : Req) : OAuthResponse = {
    Await.result(
      for(response <- Http(req > as.Response(p => p)))
        yield OAuthResponse(response.getStatusCode, response.getResponseBody), Duration.Inf)
  }

  private def sendPostRequest(req: Req): OAuthResponse = {
    val postReq = req.POST
    getAPIResponse(postReq)
  }

  private def getRequestToken(consumer: Consumer, callback: String): OAuthResponse = {
    val request = (oauthRequest / "initiate").POST <@ (consumer, callback)
    sendPostRequest(request)
  }

  def extractToken(body: String) : Token = {
    val oauthParams = body.split("&")
    val token = oauthParams(0).split("=")(1)
    val secret = oauthParams(1).split("=")(1)
    Token(token, secret)
  }


  feature("we can create an user and login as newly created user using both directLogin and OAuth") {

    scenario("we create an user with email, first name, last name, username and password", CreateUser) {
      When("we create a new user")
      val params = Map("email" -> EMAIL,
        "username" -> USERNAME,
        "password" -> PASSWORD,
        "first_name" -> FIRSTNAME,
        "last_name" -> LASTNAME)

      var request = (v2_0Request / "users").POST
      var response = makePostRequest(request, write(params))
      Then("we should get a 201 created code")
      response.code should equal(201)
    }

    scenario("we login using directLogin as newly created user", CreateUser) {
      When("we request a directLogin token")
      var request = directLoginRequest
      var response = makePostRequestAdditionalHeader(request, "", validHeaders)
      var token = "INVALID"
      Then("we should get a 201 - OK and a token")
      response.code should equal(201)
      response.body match {
        case JObject(List(JField(name, JString(value)))) =>
          name should equal("token")
          value.length should be > 0
          token = value
        case _ => fail("Expected a token")
      }
      token.size should not equal (0)
    }

    scenario("we login using OAuth as newly created user", CreateUser) {
      When("the request an OAuth token")
      val reply = getRequestToken(consumer, oob)
      Then("we should get a 200 - OK and a token")
      reply.code should equal (200)
      val requestToken = extractToken(reply.body)
      requestToken.value.size should not equal (0)
    }

    scenario("we try to create a same user again", CreateUser) {
      When("we create a same user")
      val params = Map("email" -> EMAIL,
        "username" -> USERNAME,
        "password" -> PASSWORD,
        "first_name" -> FIRSTNAME,
        "last_name" -> LASTNAME)

      val request = (v2_0Request / "users").POST
      val response = makePostRequest(request, write(params))
      Then("we should get a 409 created code")
      response.code should equal(409)
    }

  }
}