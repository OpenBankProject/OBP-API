/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v5_1_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class BankAccountBalanceTest extends V510ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object Create extends Tag(nameOf(Implementations5_1_0.createBankAccountBalance))
  object Update extends Tag(nameOf(Implementations5_1_0.updateBankAccountBalance))
  object Delete extends Tag(nameOf(Implementations5_1_0.deleteBankAccountBalance))
  object GetAll extends Tag(nameOf(Implementations5_1_0.getAllBankAccountBalances))
  object GetOne extends Tag(nameOf(Implementations5_1_0.getBankAccountBalanceById))

  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val balanceId = createMockBalance(bankId, accountId)
  
  def createMockBalance(bankId: String, accountId: String): String = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateBankAccountBalance.toString)
    val json = bankAccountBalanceRequestJsonV510
    val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances").POST <@ user1
    val response = makePostRequest(request, write(json))
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    (response.body.extract[BankAccountBalanceResponseJsonV510].balance_id)
  }
  
  feature("Create Bank Account Balance") {
    
    scenario("401 Unauthorized", Create, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances").POST
      val response = makePostRequest(request, write(bankAccountBalanceRequestJsonV510))
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("403 Forbidden (no role)", Create, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances").POST <@ user1
      val response = makePostRequest(request, write(bankAccountBalanceRequestJsonV510))
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.UserHasMissingRoles + CanCreateBankAccountBalance.toString)
    }

    scenario("201 Success + Field Echo", Create, VersionOfApi) {
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateBankAccountBalance.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances").POST <@ user1
      val response = makePostRequest(request, write(bankAccountBalanceRequestJsonV510))
      response.code should equal(201)
      val created = response.body.extract[BankAccountBalanceResponseJsonV510]
      created.balance_type should equal(bankAccountBalanceRequestJsonV510.balance_type)
      created.balance_amount should equal(bankAccountBalanceRequestJsonV510.balance_amount)
      created.account_id should equal(accountId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Update Bank Account Balance") {
    
    scenario("401 Unauthorized", Update, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).PUT
      val response = makePutRequest(request, write(bankAccountBalanceRequestJsonV510))
      response.code should equal(401)
    }

    scenario("403 Forbidden", Update, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).PUT <@ user1
      val response = makePutRequest(request, write(bankAccountBalanceRequestJsonV510))
      response.code should equal(403)
    }

    scenario("200 Success", Update, VersionOfApi) {
      lazy val bankId = testBankId1.value
      lazy val accountId = testAccountId1.value
      lazy val balanceId = createMockBalance(bankId, accountId)
      
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateBankAccountBalance.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).PUT <@ user1
      val response = makePutRequest(request, write(bankAccountBalanceRequestJsonV510))
      response.code should equal(200) 
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Delete Bank Account Balance") {
    lazy val bankId = testBankId1.value
    lazy val accountId = testAccountId1.value
    lazy val balanceId = createMockBalance(bankId, accountId)
    
    scenario("401 Unauthorized", Delete, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).DELETE
      val response = makeDeleteRequest(request)
      response.code should equal(401)
    }

    scenario("403 Forbidden", Delete, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      response.code should equal(403)
    }

    scenario("204 Success", Delete, VersionOfApi) {
      lazy val bankId = testBankId1.value
      lazy val accountId = testAccountId1.value
      lazy val balanceId = createMockBalance(bankId, accountId)
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteBankAccountBalance.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).DELETE <@ user1
      val response = makeDeleteRequest(request)
      response.code should equal(204)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature("Get All Bank Account Balances") {
    lazy val bankId = testBankId1.value
    lazy val accountId = testAccountId1.value
    lazy val balanceId = createMockBalance(bankId, accountId)
    
    scenario("401 Unauthorized", GetAll, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances").GET
      val response = makeGetRequest(request)
      response.code should equal(401)
    }

    scenario("200 Success", GetAll, VersionOfApi) {
      lazy val bankId = testBankId1.value
      lazy val accountId = testAccountId1.value
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances").GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(200)
    }
  }

  feature("Get Bank Account Balance by ID") {
    lazy val bankId = testBankId1.value
    lazy val accountId = testAccountId1.value
    
    scenario("401 Unauthorized", GetOne, VersionOfApi) {
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).GET
      val response = makeGetRequest(request)
      response.code should equal(401)
    }

    scenario("200 Success", GetOne, VersionOfApi) {
      lazy val bankId = testBankId1.value
      lazy val accountId = testAccountId1.value
      lazy val balanceId = createMockBalance(bankId, accountId)
      val request = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "balances" / balanceId).GET <@ user1
      val response = makeGetRequest(request)
      response.code should equal(200)
    }
  }
}