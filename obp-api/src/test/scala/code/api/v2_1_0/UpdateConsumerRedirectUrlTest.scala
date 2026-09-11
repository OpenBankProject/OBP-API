package code.api.v2_1_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ApiRole.CanUpdateConsumerRedirectUrl
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNoPermissionUpdateConsumer}
import code.setup.DefaultUsers
import org.json4s.JsonAST.JString
import org.json4s.native.Serialization.write

class UpdateConsumerRedirectUrlTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  Feature("Assuring that endpoint 'updateConsumerRedirectUrl' works as expected - v2.1.0") {

    val consumerRedirectUrlJSON = ConsumerRedirectUrlJSON("x-com.tesobe.helloobp.ios://callback")

    Scenario("Try to Update Redirect Url without proper role ") {

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id / "consumer" / "redirect_url" ).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 403")
      println(responsePut.body)
      responsePut.code should equal(403)

      val error = (responsePut.body \ "message" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get a message " + UserHasMissingRoles + CanUpdateConsumerRedirectUrl)
      error should equal(UserHasMissingRoles + CanUpdateConsumerRedirectUrl)
    }

    Scenario("Try to Update Redirect Url created by other user ") {

      Then("We add entitlement to user2")
      addEntitlement("", resourceUser2.userId, CanUpdateConsumerRedirectUrl.toString)
      val hasEntitlement = APIUtil.hasEntitlement("", resourceUser2.userId, ApiRole.canUpdateConsumerRedirectUrl)
      hasEntitlement should equal(true)

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id / "consumer" / "redirect_url" ).PUT <@ (user2)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 400")
      responsePut.code should equal(400)

      val error = (responsePut.body \ "message" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get a message " + UserNoPermissionUpdateConsumer)
      error.toString contains (UserNoPermissionUpdateConsumer) should be (true)
    }

    Scenario("Try to Update Redirect Url successfully ") {

      Then("We add entitlement to user1")
      addEntitlement("", resourceUser1.userId, CanUpdateConsumerRedirectUrl.toString)
      val hasEntitlement = APIUtil.hasEntitlement("", resourceUser1.userId, ApiRole.canUpdateConsumerRedirectUrl)
      hasEntitlement should equal(true)

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id / "consumer" / "redirect_url" ).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 200")
      println(responsePut.body)
      responsePut.code should equal(200)

      val field = (responsePut.body \ "redirect_url" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get an updated url " + consumerRedirectUrlJSON.redirect_url)
      field should equal(consumerRedirectUrlJSON.redirect_url)
    }



  }


}
