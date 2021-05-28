package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{endpointMappingJson, jsonCodeTemplate, supportedCurrenciesJson}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanCreateEndpointMapping, _}
import code.api.util.ErrorMessages.{UserNotLoggedIn, _}
import code.api.v3_0_0.{AtmJsonV300, OBPAPI3_0_0}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.endpointMapping.EndpointMappingCommons
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AtmsTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.updateAtmSupportedCurrencies))
  object ApiEndpoint2 extends Tag(nameOf(OBPAPI3_0_0.Implementations3_0_0.createAtm))

  feature("We need to first create Atm and update the supported-currencies") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1,ApiEndpoint2, VersionOfApi) {
      val bankId = testBankId1;
      val postAtmJson = SwaggerDefinitionsJSON.atmJsonV300.copy(bank_id= testBankId1.value)
      val postSupportedCurrenciesJson = SwaggerDefinitionsJSON.supportedCurrenciesJson
      
      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))

      
      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV300].id
      
      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "supported-currencies").PUT <@ (user1)
      
      val responseUpdate  = makePutRequest(update, write(postSupportedCurrenciesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmSupportedCurrenciesJson].supported_languages should be (postSupportedCurrenciesJson.supported_languages)
    }
  }
}
