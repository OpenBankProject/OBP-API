/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v5_1_0

import org.json4s._
import code.api.Constant
import code.api.util.ErrorMessages._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetTransactionRequestAtAnyBank, CanGetTransactionRequestAtOneBank, CanUpdateTransactionRequestStatusAtAnyBank, CanUpdateTransactionRequestStatusAtOneBank}
import code.api.v2_1_0.{CounterpartyIdJson, TransactionRequestBodyCounterpartyJSON, TransactionRequestWithChargeJSONs210}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage, TransactionRequestAttributeJsonV400}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID

class TransactionRequestTest extends V510ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object GetTransactionRequests extends Tag(nameOf(Implementations5_1_0.getTransactionRequests))
  object CreateTransactionRequestCounterparty extends Tag(nameOf(Implementations4_0_0.createTransactionRequestCounterparty))
  object GetTransactionRequestById extends Tag(nameOf(Implementations5_1_0.getTransactionRequestById))
  object UpdateTransactionRequestStatus extends Tag(nameOf(Implementations5_1_0.updateTransactionRequestStatus))

  feature("Get Transaction Requests - v5.1.0")
  {
    scenario("We will Get Transaction Requests - user is NOT logged in", GetTransactionRequests, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / testBankId1.value  / "accounts" / testAccountId0.value / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response510.body.extract[ErrorMessage].message should equal (AuthenticatedUserIsRequired)
    }
    scenario("We will Get Transaction Requests - user is logged in", GetTransactionRequests, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / testBankId1.value  / "accounts" / testAccountId0.value / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We should get a 200")
      response510.code should equal(200)
      response510.body.extract[TransactionRequestsJsonV510]
    }
    scenario("We will try to Get Transaction Requests for someone else account - user is logged in", GetTransactionRequests, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (
        v5_1_0_Request / "banks" / testBankId1.value  / "accounts" / testAccountId0.value / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET <@ (user2)
      val response510 = makeGetRequest(request510)
      Then("We should get a 403")
      response510.code should equal(403)
      And("error should be " + UserNoPermissionAccessView)
      response510.body.extract[ErrorMessage].message contains (UserNoPermissionAccessView) shouldBe (true)
    }

    scenario("We will try to Get Transaction Requests with Attributes", GetTransactionRequests, CreateTransactionRequestCounterparty, VersionOfApi) {
      val bankId = testBankId1.value
      val accountId = testAccountId1.value
      val ownerView = Constant.SYSTEM_OWNER_VIEW_ID
      
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);

      val createTransReqRequest = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId /
        ownerView / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST  <@ (user1)

      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(counterparty.counterpartyId),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        attributes = Some(List(
          TransactionRequestAttributeJsonV400( 
           name = "Invoice_Number",
          attribute_type ="STRING" ,
          value = "1"),
          TransactionRequestAttributeJsonV400( 
           name = "Reference_Number",
          attribute_type ="STRING" ,
          value = "2"),
        )))
      
      val response1 = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty))
      response1.code should equal(201)

      val response2 = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty.copy(attributes = Some(List(
        TransactionRequestAttributeJsonV400(
          name = "Invoice_Number",
          attribute_type = "STRING",
          value = "1")
      )))))
      response2.code should equal(201)

      val response3 = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty.copy(attributes = None)))
      response3.code should equal(201)
      
      {
        When("We make a request v5.1.0")
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.length > 3 shouldBe (true)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.map(_.attributes).toString().contains("Invoice_Number") shouldBe (true)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.map(_.attributes).toString().contains("Reference_Number") shouldBe (true)
      }
      
      {
        When("we try with url parameters")
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET <@ (user1)<<? (List(("Reference_Number", "2")))
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.length == 1 shouldBe (true)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.map(_.attributes).toString().contains("Invoice_Number") shouldBe (false)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.map(_.attributes).toString().contains("Reference_Number") shouldBe (true)
      }
      
      {
        When("we try with url parameters")
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET <@ (user1)<<? (List(("Invoice_Number", "2")))
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.length == 0 shouldBe (true)
      }
      
      {
        When("we try with url parameters")
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-requests").GET <@ (user1)<<? (List(("Invoice_Number13", "2")))
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[TransactionRequestsJsonV510].transaction_requests.length == 0 shouldBe (true)
      }
      
    }
  }

  feature(s"$GetTransactionRequestById - $VersionOfApi") {
    scenario(s"We will $GetTransactionRequestById - user is NOT logged in", GetTransactionRequestById, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "transaction-requests" / "TRANSACTION_REQUEST_ID").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response510.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario(s"We will $GetTransactionRequestById - user is logged in", GetTransactionRequestById, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "transaction-requests" / "TRANSACTION_REQUEST_ID").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We should get a 403")
      Then("error should start " + UserHasMissingRoles + CanGetTransactionRequestAtOneBank + " or " + CanGetTransactionRequestAtAnyBank)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetTransactionRequestAtOneBank + " or " + CanGetTransactionRequestAtAnyBank)
    }
  }



  feature(s"$UpdateTransactionRequestStatus - $VersionOfApi") {
    scenario(s"We will $UpdateTransactionRequestStatus - user is NOT logged in", UpdateTransactionRequestStatus, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "transaction-requests" / "TRANSACTION_REQUEST_ID").PUT
      val putJson = PostTransactionRequestStatusJsonV510(TransactionRequestStatus.COMPLETED.toString)
      val response510 = makePutRequest(request510, write(putJson))
      Then("We should get a 401")
      response510.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response510.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario(s"We will $UpdateTransactionRequestStatus - user is logged in", UpdateTransactionRequestStatus, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "transaction-requests" / "TRANSACTION_REQUEST_ID").PUT <@(user1)
      val putJson = PostTransactionRequestStatusJsonV510(TransactionRequestStatus.COMPLETED.toString)
      val response510 = makePutRequest(request510, write(putJson))
      Then("We should get a 403")
      Then("error should start with " + UserHasMissingRoles + CanUpdateTransactionRequestStatusAtOneBank + " or " + CanUpdateTransactionRequestStatusAtAnyBank)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanUpdateTransactionRequestStatusAtOneBank + " or " + CanUpdateTransactionRequestStatusAtAnyBank)
    }
  }

}
