package code.api.v2_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanUpdateConsumerRedirectUrl
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNoPermissionUpdateConsumer}
import code.setup.DefaultUsers
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.Serialization.write

class UpdateConsumerRedirectUrlTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint 'updateConsumerRedirectUrl' works as expected - v2.1.0") {

    val consumerRedirectUrlJSON = ConsumerRedirectUrlJSON("x-com.tesobe.helloobp.ios://callback")

    scenario("Try to Update Redirect Url without proper role ") {

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id.get / "consumer" / "redirect_url" ).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 403")
      println(responsePut.body)
      responsePut.code should equal(403)

      val error = (responsePut.body \ "error" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get a message " + UserHasMissingRoles + CanUpdateConsumerRedirectUrl)
      error should equal(UserHasMissingRoles + CanUpdateConsumerRedirectUrl)
    }

    scenario("Try to Update Redirect Url created by other user ") {

      Then("We add entitlement to user2")
      addEntitlement("", resourceUser2.userId, CanUpdateConsumerRedirectUrl.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement("", resourceUser2.userId, ApiRole.canUpdateConsumerRedirectUrl)
      hasEntitlement should equal(true)

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id.get / "consumer" / "redirect_url" ).PUT <@ (user2)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 400")
      responsePut.code should equal(400)

      val error = (responsePut.body \ "error" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get a message " + UserNoPermissionUpdateConsumer)
      error.toString contains (UserNoPermissionUpdateConsumer) should be (true)
    }

    scenario("Try to Update Redirect Url successfully ") {

      Then("We add entitlement to user1")
      addEntitlement("", resourceUser1.userId, CanUpdateConsumerRedirectUrl.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement("", resourceUser1.userId, ApiRole.canUpdateConsumerRedirectUrl)
      hasEntitlement should equal(true)

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id.get / "consumer" / "redirect_url" ).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 201")
      println(responsePut.body)
      responsePut.code should equal(201)

      val field = (responsePut.body \ "redirect_url" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get an updated url " + consumerRedirectUrlJSON.redirect_url)
      field should equal(consumerRedirectUrlJSON.redirect_url)
    }



  }


}
