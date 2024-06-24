package code.api.v5_1_0
import java.util.UUID
import code.api.Constant.{SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID, SYSTEM_OWNER_VIEW_ID}
import code.api.util.ErrorMessages._
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CounterpartyLimitTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createCounterpartyLimit))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getCounterpartyLimit))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.updateCounterpartyLimit))
  object GetAccountAccessByUserId extends Tag(nameOf(Implementations5_1_0.deleteCounterpartyLimit))

  
  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val ownerView = SYSTEM_OWNER_VIEW_ID
  lazy val managerCustomView = SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID
  lazy val postCounterpartyLimitV510 = code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postCounterpartyLimitV510
  lazy val counterparty = createCounterparty(bankId, bankAccount.id, bankAccount.id, true, UUID.randomUUID.toString);
  

  feature(s"test $ApiEndpoint1  Authorized access") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id /"views" / ownerView /"counterparties" / counterparty.counterpartyId).POST
      val response510 = makePostRequest(request510, write(postCounterpartyLimitV510))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
}
