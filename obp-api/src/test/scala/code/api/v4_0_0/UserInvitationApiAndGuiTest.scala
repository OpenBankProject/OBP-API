package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanCreateUserInvitation, CanGetUserInvitation}
import code.api.util.ErrorMessages.{CannotGetUserInvitation, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.users.{UserInvitation, UserInvitationProvider}
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.tryo
import org.scalatest.Tag
import org.scalatestplus.selenium.HtmlUnit

class UserInvitationApiAndGuiTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createUserInvitation))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getUserInvitationAnonymous))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getUserInvitation))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getUserInvitations))


  case class UserInvitationAtBrowser() extends HtmlUnit with MdcLoggable {
    implicit val driver = webDriver

    def closeAndQuit(): Unit = {
      close()
      quit()
    }

    def checkPrepoulatedFields(loginPage: String, userInvitation: UserInvitation): Box[Boolean] = {
      tryo {
        go.to(loginPage)
        val firstNameOk = textField("firstName").value == userInvitation.firstName
        val lastNameOk = textField("lastName").value == userInvitation.lastName
        val emailOk = textField("devEmail").value == userInvitation.email
        val companyNameOk = textField("companyName").value == userInvitation.company
        val countryOk = textField("country").value == userInvitation.country

        firstNameOk && lastNameOk && emailOk && companyNameOk && countryOk
      }
    }
    def checkSubmitButtonDisabled(loginPage: String, userInvitation: UserInvitation): Box[Boolean] = {
      tryo {
        go.to(loginPage)
        val consentForCollectingCheckbox = checkbox("consent_for_collecting_checkbox").isSelected
        val privacyCheckbox = checkbox("user_invitation_privacy_checkbox").isSelected
        val termsCheckboxValue = checkbox("user_invitation_terms_checkbox").isSelected
        val button = driver.findElementById("submit-button")

        ((termsCheckboxValue && privacyCheckbox && consentForCollectingCheckbox) == button.isEnabled) &&
          !button.isEnabled
      }
    }
    def checkSubmitButtonDisabled2(loginPage: String, userInvitation: UserInvitation): Box[Boolean] = {
      tryo {
        go.to(loginPage)
        checkbox("user_invitation_privacy_checkbox").select
        val consentForCollectingCheckbox = checkbox("consent_for_collecting_checkbox").isSelected
        val privacyCheckbox = checkbox("user_invitation_privacy_checkbox").isSelected
        val termsCheckboxValue = checkbox("user_invitation_terms_checkbox").isSelected
        val button = driver.findElementById("submit-button")

        ((termsCheckboxValue && privacyCheckbox && consentForCollectingCheckbox) == button.isEnabled) &&
          !button.isEnabled
      }
    }
    def checkSubmitButtonDisabled3(loginPage: String, userInvitation: UserInvitation): Box[Boolean] = {
      tryo {
        go.to(loginPage)
        checkbox("user_invitation_terms_checkbox").select
        val consentForCollectingCheckbox = checkbox("consent_for_collecting_checkbox").isSelected
        val privacyCheckbox = checkbox("user_invitation_privacy_checkbox").isSelected
        val termsCheckboxValue = checkbox("user_invitation_terms_checkbox").isSelected
        val button = driver.findElementById("submit-button")

        ((termsCheckboxValue && privacyCheckbox && consentForCollectingCheckbox) == button.isEnabled) &&
          !button.isEnabled
      }
    }
    def checkSubmitButtonEnabled(loginPage: String, userInvitation: UserInvitation): Box[Boolean] = {
      tryo {
        go.to(loginPage)
        checkbox("consent_for_collecting_checkbox").select
        checkbox("user_invitation_privacy_checkbox").select
        checkbox("user_invitation_terms_checkbox").select
        val consentForCollectingCheckbox = checkbox("consent_for_collecting_checkbox").isSelected
        val privacyCheckbox = checkbox("user_invitation_privacy_checkbox").isSelected
        val termsCheckboxValue = checkbox("user_invitation_terms_checkbox").isSelected
        val button = driver.findElementById("submit-button")

        ((termsCheckboxValue && privacyCheckbox && consentForCollectingCheckbox) == button.isEnabled) &&
          button.isEnabled
      }
    }


  }
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST <@(user1)
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("error should be " + UserHasMissingRoles + CanCreateUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanCreateUserInvitation)
    }
  }
  feature(s"test $ApiEndpoint1 and $ApiEndpoint4 version $VersionOfApi - Successful response") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateUserInvitation.toString)
      Then("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitation").POST <@(user1)
      val postJson = SwaggerDefinitionsJSON.userInvitationPostJsonV400
      val response400 = makePostRequest(request400, write(postJson))
      Then("We get successful response")
      response400.code should equal(201)
      val userInvitation = response400.body.extract[UserInvitationJsonV400]

      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanGetUserInvitation.toString)
      Then(s"We make a request $ApiEndpoint4")
      val request = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We get successful response")
      response.code should equal(200)
      val userInvitations = response.body.extract[UserInvitationsJsonV400]
      userInvitations.user_invitations.exists(i => i.email == userInvitation.email) should equal(true)


      val invitation: UserInvitation = UserInvitationProvider.userInvitationProvider.vend.getUserInvitations(testBankId1)
        .getOrElse(Nil)
        .filter(i => i.email == userInvitation.email)
        .head

      val b = UserInvitationAtBrowser()
      val pageUrl = (baseRequest / "user-invitation" <<? List(("id", invitation.secretKey.toString))).toRequest.getUrl
      b.checkPrepoulatedFields(pageUrl, invitation) should equal(true)
      b.checkSubmitButtonDisabled(pageUrl, invitation) should equal(true)
      b.checkSubmitButtonDisabled2(pageUrl, invitation) should equal(true)
      b.checkSubmitButtonDisabled3(pageUrl, invitation) should equal(true)
      b.checkSubmitButtonEnabled(pageUrl, invitation) should equal(true)
      b.closeAndQuit()
    }
  }


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").POST <@(user1)
      val postJson = PostUserInvitationAnonymousJsonV400(secret_key = 0L)
      val response400 = makePostRequest(request400, write(postJson))
      Then("error should be " + CannotGetUserInvitation)
      response400.code should equal(404)
      response400.body.extract[ErrorMessage].message should be(CannotGetUserInvitation)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations" / "secret-link").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations" / "secret-link").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetUserInvitation)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId1.value / "user-invitations").GET <@(user1)
      val response400 = makeGetRequest(request400)
      Then("error should be " + UserHasMissingRoles + CanGetUserInvitation)
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetUserInvitation)
    }
  }
  
  
}
