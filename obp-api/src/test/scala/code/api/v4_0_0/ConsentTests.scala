package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class ConsentTests extends V400ServerSetupAsync with DefaultUsers {

   override def beforeAll() {
     super.beforeAll()
   }

   override def afterAll() {
     super.afterAll()
   }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getConsents))

  feature("Assuring that endpoint createBank works as expected - v4.0.0") {

    scenario(s"We try to consume endpoint $ApiEndpoint1 - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / "SOME_BANK" / "my" / "consents").GET
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario(s"We try to consume endpoint $ApiEndpoint1 - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v4_0_0_Request / "banks" / "SOME_BANK_WHICH_SHOULD_NOT_EXIST" / "my" / "consents").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 404")
      And("We should get a message: " + ErrorMessages.BankNotFound)
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(ErrorMessages.BankNotFound)
    } 
    
  }
 }