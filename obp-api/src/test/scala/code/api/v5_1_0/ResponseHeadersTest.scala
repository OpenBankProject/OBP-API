package code.api.v5_1_0

import java.util.Date

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.v5_1_0.APIMethods510.Implementations5_1_0
import code.api.{RequestHeader, ResponseHeader}
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
  def getAtmsWithIfNotMatchHeader(eTag: String) = {
    makeGetRequest((v5_1_0_Request / "banks" / bankId / "atms").GET, List((RequestHeader.`If-None-Match`, eTag)))
  }
  def getAtmsWithIfModifiedSinceHeader(sinceDate: String) = {
    makeGetRequest((v5_1_0_Request / "banks" / bankId / "atms").GET, List((RequestHeader.`If-Modified-Since`, sinceDate)))
  }
  def getAtmsWithIfModifiedSinceHeader(sinceDate: String, consumerAndToken: Option[(Consumer, Token)]) = {
    makeGetRequest((v5_1_0_Request / "banks" / bankId / "atms").GET <@(consumerAndToken), List((RequestHeader.`If-Modified-Since`, sinceDate)))
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


  /**
   * Caching of unchanged resources
   *
   * Another typical use of the ETag header is to cache resources that are unchanged. 
   * If a user visits a given URL again (that has an ETag set), and it is stale (too old to be considered usable), 
   * the client will send the value of its ETag along in an If-None-Match header field:
   *
   * If-None-Match: "33a64df551425fcc55e4d42a148795d9f25f89d4"
   *
   * The server compares the client's ETag (sent with If-None-Match) with the ETag for its current version of the resource, 
   * and if both values match (that is, the resource has not changed), the server sends back a 304 Not Modified status, 
   * without a body, which tells the client that the cached version of the response is still good to use (fresh).
   */
  feature(s"Test ETag Header Response - If-Not-Match") {
    scenario(s"Test ETag Header Response", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      val ETag1 = getETagHeader(getAtms())
      getAtmsWithIfNotMatchHeader(ETag1).code should equal(304)
    }
  }

  feature(s"Test Request Header - If-Modified-Since") {
    scenario(s"Test ETag Header Response", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      val sinceDateString = APIUtil.DateWithSecondsFormat.format(new Date())
      val firstCall = getAtmsWithIfModifiedSinceHeader(sinceDateString)
      firstCall.code should equal(200)

      // We create an ATM in the meantime
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreate = (v5_1_0_Request / "banks" / bankId / "atms").POST <@ (user1)
      val responseCreate = makePostRequest(requestCreate, write(atmJsonV510.copy(
        bank_id = bankId,
        atm_type = "atm_type1",
        phone = "12345")))
      Then("We should get a 201")
      responseCreate.code should equal(201)

      // Due to the async task regarding cache we must wait some time
      Thread.sleep(1000)

      val secondCall = getAtmsWithIfModifiedSinceHeader(APIUtil.DateWithSecondsFormat.format(new Date()))
      secondCall.code should equal(200)

      // Due to the async task regarding cache we must wait some time
      Thread.sleep(1000)

      val thirdCall = getAtmsWithIfModifiedSinceHeader(APIUtil.DateWithSecondsFormat.format(new Date()))
      thirdCall.code should equal(304)
    }
  }

  
  feature(s"Test Request Header - If-Modified-Since - Logged In User") {
    scenario(s"Test ETag Header Response", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      val sinceDateString = APIUtil.DateWithSecondsFormat.format(new Date())
      val firstCall = getAtmsWithIfModifiedSinceHeader(sinceDateString, user1)
      firstCall.code should equal(200)

      // We create an ATM in the meantime
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreate = (v5_1_0_Request / "banks" / bankId / "atms").POST <@ (user1)
      val responseCreate = makePostRequest(requestCreate, write(atmJsonV510.copy(
        bank_id = bankId,
        atm_type = "atm_type1",
        phone = "12345")))
      Then("We should get a 201")
      responseCreate.code should equal(201)

      // Due to the async task regarding cache we must wait some time
      Thread.sleep(1000)

      val secondCall = getAtmsWithIfModifiedSinceHeader(APIUtil.DateWithSecondsFormat.format(new Date()), user1)
      secondCall.code should equal(200)

      // Due to the async task regarding cache we must wait some time
      Thread.sleep(1000)

      val thirdCall = getAtmsWithIfModifiedSinceHeader(APIUtil.DateWithSecondsFormat.format(new Date()), user1)
      thirdCall.code should equal(304)
    }
  }
}