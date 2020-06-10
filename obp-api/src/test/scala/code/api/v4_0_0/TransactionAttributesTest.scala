package code.api.v4_0_0


import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class TransactionAttributesTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createTransactionAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateTransactionAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getTransactionAttributes))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getTransactionAttributeById))


  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val postTransactionAttributeJsonV400 = SwaggerDefinitionsJSON.transactionAttributeJsonV400
  lazy val putTransactionAttributeJsonV400 = SwaggerDefinitionsJSON.transactionAttributeJsonV400.copy(name="test")
  lazy val view = "owner"

  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attribute").POST
      val response400 = makePostRequest(request400, write(postTransactionAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postTransactionAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      lazy val bankId = testBankId1.value
      lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
      lazy val accountId = bankAccount.id
      lazy val postTransactionAttributeJsonV400 = SwaggerDefinitionsJSON.transactionAttributeJsonV400
      lazy val putTransactionAttributeJsonV400 = SwaggerDefinitionsJSON.transactionAttributeJsonV400.copy(name="test")
      lazy val view = bankAccount.views_available.map(_.id).headOption.getOrElse("owner")
      lazy val transaction = randomTransactionViaEndpoint(bankId, bankAccount.id, view)
      lazy val transactionId = transaction.id
      
      
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionAttributeAtOneBank.toString)

      val responseWithRole = makePostRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.extract[TransactionAttributeResponseJson].name equals("test") should be (true) 
      responseWithRole.body.extract[TransactionAttributeResponseJson].value equals(postTransactionAttributeJsonV400.value) should be (true)
      responseWithRole.body.extract[TransactionAttributeResponseJson].`type` equals(postTransactionAttributeJsonV400.`type`) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" / "transactionAttributeId").PUT
      val response400 = makePutRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" / "transactionAttributeId").PUT <@ (user1)
      val response400 = makePutRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionAttributeAtOneBank.toString)

      val responseWithRole = makePostRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.extract[TransactionAttributeResponseJson].name equals("test") should be (true)
      responseWithRole.body.extract[TransactionAttributeResponseJson].value equals(postTransactionAttributeJsonV400.value) should be (true)
      responseWithRole.body.extract[TransactionAttributeResponseJson].`type` equals(postTransactionAttributeJsonV400.`type`) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - wrong transactionAttributeId") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" / "transactionAttributeId").PUT <@ (user1)
      val response400 = makePutRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateTransactionAttributeAtOneBank.toString)

      val responseWithRole = makePutRequest(request400, write(putTransactionAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(400)
      responseWithRole.toString contains TransactionAttributeNotFound should be (true)

    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - with transactionAttributeId") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {

      lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
      lazy val transactionId = transaction.id
      
      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateTransactionAttributeAtOneBank.toString)

      Then("we create the Transaction Attribute ")
      val transactionAttributeId = createTransactionAttributeEndpoint(bankId:String, accountId:String, transactionId:String,  user1)
     

      val requestWithId = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" / transactionAttributeId).PUT <@ (user1)
      val responseWithId = makePutRequest(requestWithId, write(putTransactionAttributeJsonV400))

      responseWithId.body.extract[TransactionAttributeResponseJson].name  equals("test") should be (true)
      responseWithId.body.extract[TransactionAttributeResponseJson].value  equals(putTransactionAttributeJsonV400.value) should be (true)
      responseWithId.body.extract[TransactionAttributeResponseJson].`type`  equals(putTransactionAttributeJsonV400.`type`) should be (true)
    }
  }

    feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access - with role - wrong transactionAttributeId") {
      scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {

        lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
        lazy val transactionId = transaction.id
        
        When("We make a request v4.0.0")
        Then("we create the Transaction Attribute ")
        val transactionAttributeId = createTransactionAttributeEndpoint(bankId:String, accountId:String, transactionId:String,  user1)


        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" ).GET <@ (user1)
        val response400 = makeGetRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetTransactionAttributesAtOneBank.toString)

        val responseWithRole = makeGetRequest(request400)
        Then("We should get a 200")
        responseWithRole.code should equal(200)
      }
    }

    feature(s"test $ApiEndpoint4 version $VersionOfApi - authorized access - with role - with transactionAttributeId") {
      scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {

        lazy val transaction = randomTransactionViaEndpoint(bankId, accountId, view)
        lazy val transactionId = transaction.id
        
        Then("we create the Transaction Attribute ")
        val transactionAttributeId = createTransactionAttributeEndpoint(bankId, accountId, transactionId, user1)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canGetTransactionAttributeAtOneBank.toString)

        val requestWithId = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" / transactionAttributeId).GET <@ (user1)
        val responseWithId = makeGetRequest(requestWithId)

        responseWithId.body.extract[TransactionAttributeResponseJson].name equals(postTransactionAttributeJsonV400.name) should be (true)
        responseWithId.body.extract[TransactionAttributeResponseJson].value equals(postTransactionAttributeJsonV400.value) should be (true)
        responseWithId.body.extract[TransactionAttributeResponseJson].`type` equals(postTransactionAttributeJsonV400.`type`) should be (true)
      }
    }
  
}
