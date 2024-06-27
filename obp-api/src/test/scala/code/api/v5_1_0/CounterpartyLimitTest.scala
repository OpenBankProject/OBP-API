package code.api.v5_1_0
import java.util.UUID
import code.api.Constant.{SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID, SYSTEM_OWNER_VIEW_ID}
import code.api.util.ErrorMessages._
import code.api.util.APIUtil.OAuth._
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
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.deleteCounterpartyLimit))

  
  val bankId = testBankId1.value
  val accountId = testAccountId1.value
  val ownerView = SYSTEM_OWNER_VIEW_ID
  val postCounterpartyLimitV510 = code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postCounterpartyLimitV510
  val putCounterpartyLimitV510 = PostCounterpartyLimitV510(
    max_single_amount = 1,
    max_monthly_amount = 2,
    max_number_of_monthly_transactions = 3,
    max_yearly_amount = 4,
    max_number_of_yearly_transactions = 5
  )
  

  feature(s"test $ApiEndpoint1  Authorized access") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,VersionOfApi) {
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString); 
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST
      val response510 = makePostRequest(request510, write(postCounterpartyLimitV510))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").PUT
        val response510 = makePutRequest(request510, write(postCounterpartyLimitV510))
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET
        val response510 = makeGetRequest(request510)
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").DELETE
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

      }
    }
    
    scenario("We will call the endpoint success case", ApiEndpoint1, ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,VersionOfApi) {
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);   
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitV510))
      Then("We should get a 201")
      response510.code should equal(201)
      response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(postCounterpartyLimitV510.max_monthly_amount)
      response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(postCounterpartyLimitV510.max_number_of_monthly_transactions)
      response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(postCounterpartyLimitV510.max_number_of_yearly_transactions)
      response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(postCounterpartyLimitV510.max_single_amount)
      response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(postCounterpartyLimitV510.max_yearly_amount)

      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(postCounterpartyLimitV510.max_monthly_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(postCounterpartyLimitV510.max_number_of_monthly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(postCounterpartyLimitV510.max_number_of_yearly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(postCounterpartyLimitV510.max_single_amount)
        response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(postCounterpartyLimitV510.max_yearly_amount)
        
      }
      
      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").PUT<@ (user1)
        val response510 = makePutRequest(request510, write(putCounterpartyLimitV510))
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(putCounterpartyLimitV510.max_monthly_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(putCounterpartyLimitV510.max_number_of_monthly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(putCounterpartyLimitV510.max_number_of_yearly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(putCounterpartyLimitV510.max_single_amount)
        response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(putCounterpartyLimitV510.max_yearly_amount)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(putCounterpartyLimitV510.max_monthly_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(putCounterpartyLimitV510.max_number_of_monthly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(putCounterpartyLimitV510.max_number_of_yearly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(putCounterpartyLimitV510.max_single_amount)
        response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(putCounterpartyLimitV510.max_yearly_amount)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").DELETE<@ (user1)
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 204")
        response510.code should equal(204)
      }
    }
    
    scenario("We will call the endpoint wrong bankId case", ApiEndpoint1, ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,VersionOfApi) {
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);   
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitV510))
      Then("We should get a 404")
      response510.code should equal(404)
      response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)

      {
        val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
        
      }
      
      {

        val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").PUT<@ (user1)
        val response510 = makePutRequest(request510, write(putCounterpartyLimitV510))
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / "wrongId"  / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").DELETE<@ (user1)
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
      }
    }
  }
}
