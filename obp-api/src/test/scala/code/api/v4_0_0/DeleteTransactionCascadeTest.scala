package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanDeleteTransactionCascade
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.metadata.comments.MappedComment
import code.metadata.narrative.MappedNarrative
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.transactionattribute.MappedTransactionAttribute
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.scalatest.Tag

class DeleteTransactionCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteTransactionCascade))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "accounts" / bankAccount.id / "transactions" / "id").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "accounts" / bankAccount.id / "transactions" / "id").DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanDeleteTransactionCascade)
    }
  }
  feature(s"test $ApiEndpoint1 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      val (fromBankId, fromAccountId, transactionId) = createTransactionRequestForDeleteCascade(bankId)
      
      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteTransactionCascade.toString)
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / fromBankId /
        "accounts" / fromAccountId / "transactions" / transactionId).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)

      When("We try to delete one more time we should get 404")
      makeDeleteRequest(request400).code should equal(404)

      When("We assure there are no all transaction related date")
      checkAllTransactionRelatedData(fromBankId, fromAccountId, transactionId) should be (true)
    }
  }  

}
