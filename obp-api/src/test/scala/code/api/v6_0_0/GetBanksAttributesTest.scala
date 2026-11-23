package code.api.v6_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v4_0_0.BankAttributeJsonV400
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

// Get Banks returns each bank's active attributes, so a caller can pick banks by attribute from one list
// (for example the Open Corridor participants).
class GetBanksAttributesTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getBanks))

  feature(s"$ApiEndpoint1 shows bank attributes - $VersionOfApi") {
    scenario("An active attribute shows on its bank and an inactive one does not", ApiEndpoint1, VersionOfApi) {
      val bankId = testBankId1.value
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateBankAttribute.toString)

      Given("an active OPEN_CORRIDOR_PARTICIPANT attribute and an inactive one on the bank")
      val create = (v5_1_0_Request / "banks" / bankId / "attribute").POST <@ (user1)
      makePostRequest(create, write(BankAttributeJsonV400("OPEN_CORRIDOR_PARTICIPANT", "BOOLEAN", "true", Some(true)))).code should equal(201)
      makePostRequest(create, write(BankAttributeJsonV400("INACTIVE_MARKER", "STRING", "x", Some(false)))).code should equal(201)

      When("anyone lists the banks")
      val response = makeGetRequest((v6_0_0_Request / "banks").GET)

      Then("the bank carries the active attribute only")
      response.code should equal(200)
      val bank = response.body.extract[BanksJsonV600].banks.find(_.bank_id == bankId).get
      val attributes = bank.attributes.getOrElse(Nil)
      attributes.exists(a => a.name == "OPEN_CORRIDOR_PARTICIPANT" && a.value == "true") shouldBe true
      attributes.exists(_.name == "INACTIVE_MARKER") shouldBe false

      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }
}
