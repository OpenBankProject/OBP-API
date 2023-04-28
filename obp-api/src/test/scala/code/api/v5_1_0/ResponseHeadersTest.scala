package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResponseHeader
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v5_1_0.APIMethods510.Implementations5_1_0
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class ResponseHeadersTest extends V510ServerSetup with DefaultUsers {

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
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createAtm))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getAtms))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.deleteAtm))

  lazy val bankId = randomBankId

  def getETagHeader(response: APIResponse): String = {
    response.headers.map(_.get(ResponseHeader.ETag)).getOrElse("")
  }
  def getAtms() = {
    makeGetRequest((v5_1_0_Request / "banks" / bankId / "atms").GET)
  }
  
  feature(s"Test ETag Header Response") {
    scenario(s"Test ETag Header Response", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      
      val ETag1 = getETagHeader(getAtms())
      
      When("We make the CREATE ATMs")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteAtmAtAnyBank.toString)
      val requestCreate = (v5_1_0_Request / "banks" / bankId / "atms").POST <@ (user1)
      val responseCreate = makePostRequest(requestCreate, write(atmJsonV510.copy(
        bank_id = bankId,
        atm_type = "atm_type1",
        phone = "12345")))
      Then("We should get a 201")
      responseCreate.code should equal(201)
      val atmId = responseCreate.body.extract[AtmJsonV510].id.getOrElse("")

      val ETag2 = getETagHeader(getAtms())

      // If we add atm response MUST be different
      ETag1 should not equal ETag2
      
      // If we do not change anything responses MUST be the same
      val ETag3 = getETagHeader(getAtms())
      val ETag4 = getETagHeader(getAtms())
      ETag3 should equal(ETag4)

      Then("We Delete the ATM")
      val requestOneDelete = (v5_1_0_Request / "banks" / bankId / "atms" / atmId).DELETE<@ (user1)
      Then("We should get a 204")
      val responseOneDelete = makeDeleteRequest(requestOneDelete)
      responseOneDelete.code should equal(204)

      // After we delete the atm responses MUST be different
      val ETag5 = getETagHeader(getAtms())
      ETag4 should not equal ETag5
    }
  }
}