package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AccountTagTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.addTagForViewOnAccount))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.deleteTagForViewOnAccount))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getTagsForViewOnAccount))

  lazy val accountTag = SwaggerDefinitionsJSON.accountTagJSON
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val view = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "metadata" / "tags").POST
      val response400 = makePostRequest(request400, write(accountTag))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "metadata" / "tags" / "DOES_NOT_MATTER_FOR_THIS").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "metadata" / "tags").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 and $ApiEndpoint2 and $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1, ApiEndpoint2, ApiEndpoint3) {
      
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "metadata" / "tags").POST <@ (user1)
      val response = makePostRequest(request, write(accountTag))

      Then("We should get a 201 and check the response body")
      response.code should equal(201)
      response.body.extract[AccountTagJSON]

      val requestGet = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "metadata" / "tags").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      responseGet.code should equal(200)
      val tags = responseGet.body.extract[AccountTagsJSON].tags
      tags.exists(_.value == accountTag.value) equals true
      val tagId = tags.map(_.id).headOption.getOrElse("")

      val requestDelete = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "metadata" / "tags" / tagId).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should equal(200)
    }
  }


  
  
}
