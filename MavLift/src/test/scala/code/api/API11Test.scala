package code.api.v1_1

import org.scalatest._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import dispatch._
import code.api.test.{ServerSetup, APIResponse}


@RunWith(classOf[JUnitRunner])
class API1_1Test extends ServerSetup{

  val v1_1Request = baseRequest / "obp" / "v1.1"

  def getAPIInfo = {
    val request = v1_1Request
    makeGetRequest(request)
  }

  /************************ the tests ************************/
  feature("base line URL works"){
    scenario("we get the api information") {
       Given("The user is not logged in")
       When("the request is sent")
       val reply = getAPIInfo
       Then("we should get a 200 created code")
       reply.code should equal (200)
    }
  }
}
