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

package code.api.v6_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, Nuban}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.model.dataAccess.{MappedBankAccount, MappedBankRouting}
import code.setup.{APIResponse, DefaultUsers}
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, BankRoutingJson, ErrorMessage}
import net.liftweb.common.Full
import net.liftweb.mapper.By
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * Creating an account with a NUBAN (Nigeria Uniform Bank Account Number).
 *
 * The request names the scheme NG.NUBAN. With an empty address, OBP works out a NUBAN from the bank's
 * CBN code, which the bank keeps in a bank routing with scheme NG.BANK_CODE. A NUBAN is the account's
 * number: it is stored in the account number field and as an NG.NUBAN routing. Versions before v6.0.0
 * show the NG.NUBAN routing. From v6.0.0, the same number is also shown as ACCOUNT_NUMBER.
 */
class NubanAccountCreationTest extends V600ServerSetup with DefaultUsers {

  object NubanAccounts extends Tag("NubanAccountCreation")

  private val bankCode = "011"

  private def giveBankCode(code: String): Unit =
    MappedBankRouting.replaceRoutingsOf(testBankId1.value, List(BankRoutingJson(Nuban.BankCodeScheme, code)))

  override def beforeEach(): Unit = {
    super.beforeEach()
    giveBankCode(bankCode)
  }

  override def afterEach(): Unit = {
    MappedBankRouting.replaceRoutingsOf(testBankId1.value, Nil)
    super.afterEach()
  }

  // Creates an account through v4.0.0 as user1, who may create accounts at the bank.
  private def create(routings: List[AccountRoutingJsonV121]) = {
    val canCreate = Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    try makePostRequest((v4_0_0_Request / "banks" / testBankId1.value / "accounts").POST <@ (user1), write(
      SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(
        user_id = resourceUser1.userId,
        balance = AmountOfMoneyJsonV121("NGN", "0"),
        account_routings = routings)))
    finally Entitlement.entitlement.vend.deleteEntitlement(canCreate)
  }

  private def numberFieldOf(accountId: String): String =
    MappedBankAccount.find(By(MappedBankAccount.bank, testBankId1.value), By(MappedBankAccount.theAccountId, accountId))
      .map(_.accountNumber.get).openOr("")

  private def routingsShownBy(version: String, accountId: String): List[AccountRoutingJsonV121] = {
    val response = makeGetRequest((baseRequest / "obp" / version / "my" / "banks" / testBankId1.value / "accounts" / accountId / "account").GET <@ (user1))
    response.code should equal(200)
    (response.body \ "account_routings").extract[List[AccountRoutingJsonV121]]
  }

  private def findBy(scheme: String, number: String) =
    Connector.connector.vend.getBankAccountByRoutingLegacy(Some(testBankId1), scheme, number, None).map(_._1.accountId.value)

  private def errorOf(response: APIResponse) = response.body.extract[ErrorMessage].message

  // A valid NUBAN at bank 011 that no other test uses.
  private def newValidNuban(): String = Nuban.randomNuban("000011")

  feature("OBP works out the NUBAN") {

    scenario("NG.NUBAN with an empty address: a valid NUBAN is assigned and shown the same everywhere", NubanAccounts) {
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", "")))
      response.code should equal(201)
      val account = response.body.extract[CreateAccountResponseJsonV310]

      Then("the NUBAN is valid at the bank, and is the account's number")
      val nuban = numberFieldOf(account.account_id)
      Nuban.isValid(bankCode, nuban) should equal(true)
      account.account_routings should equal(List(AccountRoutingJsonV121("NG.NUBAN", nuban)))

      And("versions before v6.0.0 show the NG.NUBAN routing, and from v6.0.0 also ACCOUNT_NUMBER")
      routingsShownBy("v5.1.0", account.account_id) should equal(List(AccountRoutingJsonV121("NG.NUBAN", nuban)))
      List("v6.0.0", "v7.0.0").foreach { version =>
        val shown = routingsShownBy(version, account.account_id)
        shown should contain(AccountRoutingJsonV121("ACCOUNT_NUMBER", nuban))
        shown should contain(AccountRoutingJsonV121("NG.NUBAN", nuban))
      }

      And("the account is found by the NUBAN under every spelling of both schemes")
      List("NG.NUBAN", "NUBAN", "nuban", "ACCOUNT_NUMBER", "AccountNumber", "number").foreach { scheme =>
        withClue(s"lookup by $scheme: ") { findBy(scheme, nuban) should equal(Full(account.account_id)) }
      }
    }

    scenario("NUBAN is accepted as a spelling of NG.NUBAN, and stored as NG.NUBAN", NubanAccounts) {
      val response = create(List(AccountRoutingJsonV121("NUBAN", "")))
      response.code should equal(201)
      val account = response.body.extract[CreateAccountResponseJsonV310]
      account.account_routings.map(_.scheme) should equal(List("NG.NUBAN"))
      Nuban.isValid(bankCode, numberFieldOf(account.account_id)) should equal(true)
    }

    scenario("Another financial institution with a five digit code gets NUBANs worked out with 9 in front", NubanAccounts) {
      giveBankCode("50547")
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", "")))
      response.code should equal(201)
      val nuban = numberFieldOf(response.body.extract[CreateAccountResponseJsonV310].account_id)
      Nuban.isValid("950547", nuban) should equal(true)
    }

    scenario("Assigned NUBANs are all different", NubanAccounts) {
      val numbers = (1 to 5).map { _ =>
        val response = create(List(AccountRoutingJsonV121("NG.NUBAN", "")))
        response.code should equal(201)
        numberFieldOf(response.body.extract[CreateAccountResponseJsonV310].account_id)
      }
      numbers.distinct.size should equal(5)
    }

    scenario("A bank with no NG.BANK_CODE routing cannot assign a NUBAN: 400", NubanAccounts) {
      MappedBankRouting.replaceRoutingsOf(testBankId1.value, Nil)
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", "")))
      response.code should equal(400)
      errorOf(response) should startWith(NubanBankCodeNotUsable.split(":").head)
    }

    scenario("A bank with two different NG.BANK_CODE routings cannot assign a NUBAN: 400", NubanAccounts) {
      MappedBankRouting.replaceRoutingsOf(testBankId1.value, List(BankRoutingJson("NG.BANK_CODE", "011"), BankRoutingJson("NG.BANK_CODE", "058")))
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", "")))
      response.code should equal(400)
      errorOf(response) should startWith(NubanBankCodeNotUsable.split(":").head)
    }
  }

  feature("A NUBAN given in the request") {

    scenario("On a sandbox, a valid NUBAN is kept", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val nuban = newValidNuban()
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", nuban)))
      response.code should equal(201)
      numberFieldOf(response.body.extract[CreateAccountResponseJsonV310].account_id) should equal(nuban)
    }

    scenario("On a sandbox, a NUBAN with the wrong check digit is refused with 400", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val nuban = newValidNuban()
      val wrong = nuban.take(9) + ((nuban.last.asDigit + 1) % 10)
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", wrong)))
      response.code should equal(400)
      errorOf(response) should startWith(NubanCheckDigitInvalid.split(":").head)
    }

    scenario("On a sandbox, the same number as NG.NUBAN and ACCOUNT_NUMBER is one number", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val nuban = newValidNuban()
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", nuban), AccountRoutingJsonV121("NG.NUBAN", nuban)))
      response.code should equal(201)
      val account = response.body.extract[CreateAccountResponseJsonV310]
      account.account_routings.toSet should equal(Set(AccountRoutingJsonV121("ACCOUNT_NUMBER", nuban), AccountRoutingJsonV121("NG.NUBAN", nuban)))
    }

    scenario("On a sandbox, an ACCOUNT_NUMBER with an empty NG.NUBAN must itself be a valid NUBAN", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "1234567890"), AccountRoutingJsonV121("NG.NUBAN", "")))
      response.code should equal(400)
      errorOf(response) should startWith(NubanCheckDigitInvalid.split(":").head)
    }

    scenario("On a sandbox, a NUBAN and a different ACCOUNT_NUMBER are refused with 400", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "1234567890"), AccountRoutingJsonV121("NG.NUBAN", newValidNuban())))
      response.code should equal(400)
      errorOf(response) should startWith(TooManyAccountNumbers.split(":").head)
    }

    scenario("On a sandbox, a NUBAN another account at the bank has is refused with 409", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val nuban = newValidNuban()
      create(List(AccountRoutingJsonV121("NG.NUBAN", nuban))).code should equal(201)
      val response = create(List(AccountRoutingJsonV121("NUBAN", nuban)))
      response.code should equal(409)
      errorOf(response) should startWith(AccountNumberAlreadyExists.split(":").head)
    }

    scenario("Not on a sandbox, a given NUBAN is refused with 400", NubanAccounts) {
      setPropsValues("allow_sandbox_account_creation" -> "false")
      val response = create(List(AccountRoutingJsonV121("NG.NUBAN", newValidNuban())))
      response.code should equal(400)
      errorOf(response) should startWith(AccountNumberAssignedByBank.split(":").head)
    }
  }
}
