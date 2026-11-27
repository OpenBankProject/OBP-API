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

package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages._
import code.api.util.migration.MigrationOfAccountNumbers
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import code.setup.DefaultUsers
import com.openbankproject.commons.model.{AccountId, AccountRoutingJsonV121, AmountOfMoneyJsonV121, ErrorMessage}
import net.liftweb.common.Full
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * The bank assigns account numbers. A request to create an account names the scheme: an
 * ACCOUNT_NUMBER routing with an empty address, or no account number routing at all, asks for a
 * number. A number can be given only on a sandbox (allow_sandbox_account_creation=true) or by a user
 * with CanCreateAccountWithAccountNumber. Either way the number is stored as an ACCOUNT_NUMBER
 * routing, with the same value in the deprecated account number field, so the two never differ.
 */
class AccountNumberAssignmentTest extends V400ServerSetup with DefaultUsers {

  object AccountNumbers extends Tag("AccountNumberAssignment")

  private def requestWith(routings: List[AccountRoutingJsonV121]) =
    SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(
      user_id = resourceUser1.userId,
      balance = AmountOfMoneyJsonV121("EUR", "0"),
      account_routings = routings)

  // Creates an account through v4.0.0 as user1, who may create accounts at the bank.
  private def create(routings: List[AccountRoutingJsonV121], extraRole: Option[ApiRole] = None) = {
    val canCreate = Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    val extra = extraRole.map(role => Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, role.toString))
    try makePostRequest((v4_0_0_Request / "banks" / testBankId1.value / "accounts").POST <@ (user1), write(requestWith(routings)))
    finally {
      Entitlement.entitlement.vend.deleteEntitlement(canCreate)
      extra.foreach(Entitlement.entitlement.vend.deleteEntitlement)
    }
  }

  private def numberRoutingsIn(account: CreateAccountResponseJsonV310) =
    account.account_routings.filter(r => code.api.util.RoutingRules.isAccountNumberScheme(r.scheme))

  private def numberFieldOf(accountId: String): String =
    MappedBankAccount.find(By(MappedBankAccount.bank, testBankId1.value), By(MappedBankAccount.theAccountId, accountId))
      .map(_.accountNumber.get).openOr("")

  private def findByNumber(scheme: String, number: String) =
    Connector.connector.vend.getBankAccountByRoutingLegacy(Some(testBankId1), scheme, number, None).map(_._1.accountId.value)

  private def newNumber(): String = "9" + (1 to 9).map(_ => scala.util.Random.nextInt(10)).mkString

  // The account's routings as v6.0.0 shows them, from Get Account by Id (Core) as its owner.
  private def v6RoutingsOf(accountId: String): List[AccountRoutingJsonV121] = {
    val response = makeGetRequest((baseRequest / "obp" / "v6.0.0" / "my" / "banks" / testBankId1.value / "accounts" / accountId / "account").GET <@ (user1))
    response.code should equal(200)
    (response.body \ "account_routings").extract[List[AccountRoutingJsonV121]]
  }
  private def v6NumbersOf(accountId: String) = v6RoutingsOf(accountId).filter(_.scheme == "ACCOUNT_NUMBER")

  feature("The bank assigns an account number") {

    scenario("No account number routing: a ten digit number is assigned, v4.0.0 shows the routings it was given, v6.0.0 shows the number", AccountNumbers) {
      val iban = AccountRoutingJsonV121("IBAN", "GB29NWBK6016" + newNumber())
      val response = create(List(iban))
      response.code should equal(201)
      val account = response.body.extract[CreateAccountResponseJsonV310]
      account.account_routings should equal(List(iban))
      val number = numberFieldOf(account.account_id)
      number should fullyMatch regex "[0-9]{10}"
      v6NumbersOf(account.account_id) should equal(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number)))
      findByNumber("ACCOUNT_NUMBER", number) should equal(Full(account.account_id))
    }

    scenario("An ACCOUNT_NUMBER routing with an empty address: a number is assigned", AccountNumbers) {
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "")))
      response.code should equal(201)
      val account = response.body.extract[CreateAccountResponseJsonV310]
      numberRoutingsIn(account) should equal(Nil)
      val number = numberFieldOf(account.account_id)
      number should fullyMatch regex "[0-9]{10}"
      v6NumbersOf(account.account_id) should equal(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number)))
    }

    scenario("Assigned numbers are all different", AccountNumbers) {
      val numbers = (1 to 5).map { _ =>
        val response = create(Nil)
        response.code should equal(201)
        numberFieldOf(response.body.extract[CreateAccountResponseJsonV310].account_id)
      }
      numbers.distinct.size should equal(5)
    }

    scenario("An empty address for a scheme OBP cannot assign is refused with 400", AccountNumbers) {
      val response = create(List(AccountRoutingJsonV121("IBAN", "")))
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(AccountRoutingAddressNotAssigned.split(":").head)
    }
  }

  feature("A number given in the request") {

    scenario("On a sandbox, a number given as NUMBER is kept as NUMBER for v4.0.0, shown as ACCOUNT_NUMBER by v6.0.0, and finds the account", AccountNumbers) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val number = newNumber()
      val response = create(List(AccountRoutingJsonV121("NUMBER", number)))
      response.code should equal(201)
      val account = response.body.extract[CreateAccountResponseJsonV310]
      numberRoutingsIn(account) should equal(List(AccountRoutingJsonV121("NUMBER", number)))
      numberFieldOf(account.account_id) should equal(number)
      v6NumbersOf(account.account_id) should equal(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number)))
      findByNumber("NUMBER", number) should equal(Full(account.account_id))
      findByNumber("ACCOUNT_NUMBER", number) should equal(Full(account.account_id))
    }

    scenario("On a sandbox, a number another account at the bank has is refused with 409", AccountNumbers) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val number = newNumber()
      create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number))).code should equal(201)
      val response = create(List(AccountRoutingJsonV121("AccountNumber", number)))
      response.code should equal(409)
      response.body.extract[ErrorMessage].message should startWith(AccountNumberAlreadyExists.split(":").head)
    }

    scenario("Two different numbers in one request are refused with 400", AccountNumbers) {
      setPropsValues("allow_sandbox_account_creation" -> "true")
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", newNumber()), AccountRoutingJsonV121("NUMBER", newNumber())))
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(TooManyAccountNumbers.split(":").head)
    }

    scenario("Not on a sandbox, a given number is refused with 400", AccountNumbers) {
      setPropsValues("allow_sandbox_account_creation" -> "false")
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", newNumber())))
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(AccountNumberAssignedByBank.split(":").head)
    }

    scenario("Not on a sandbox, a user with CanCreateAccountWithAccountNumber may give a number", AccountNumbers) {
      setPropsValues("allow_sandbox_account_creation" -> "false")
      val number = newNumber()
      val response = create(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number)), Some(ApiRole.canCreateAccountWithAccountNumber))
      response.code should equal(201)
      numberRoutingsIn(response.body.extract[CreateAccountResponseJsonV310]) should equal(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number)))
    }

    scenario("Not on a sandbox, no number given: a number is still assigned", AccountNumbers) {
      setPropsValues("allow_sandbox_account_creation" -> "false")
      create(Nil).code should equal(201)
    }
  }

  feature("Fixing accounts stored before the bank assigned numbers") {

    def storedAccount(number: String) = {
      val account = createAccountRelevantResource(Some(resourceUser1), testBankId1, AccountId("acc-" + randomString(8).toLowerCase), "EUR")
      MappedBankAccount.find(By(MappedBankAccount.bank, testBankId1.value), By(MappedBankAccount.theAccountId, account.accountId.value))
        .openOrThrowException("account not stored").accountNumber(number).saveMe()
    }
    def storeRouting(accountId: String, scheme: String, address: String) =
      BankAccountRouting.create.BankId(testBankId1.value).AccountId(accountId).AccountRoutingScheme(scheme).AccountRoutingAddress(address).saveMe()

    scenario("The routing's number is copied into the account number field where they differ, and nowhere else", AccountNumbers) {
      val differing = storedAccount("4831907265"); val routed = newNumber()
      storeRouting(differing.accountId.value, "AccountNumber", routed)
      val same = storedAccount("5555555555")
      storeRouting(same.accountId.value, "ACCOUNT_NUMBER", "5555555555")
      val fieldOnly = storedAccount("6666666666")
      val twoRoutings = storedAccount("7777777777")
      storeRouting(twoRoutings.accountId.value, "ACCOUNT_NUMBER", newNumber())
      storeRouting(twoRoutings.accountId.value, "NUMBER", newNumber())

      findByNumber("ACCOUNT_NUMBER", routed).isDefined should equal(false)

      MigrationOfAccountNumbers.copyRoutingNumbersIntoNumberField("copyAccountNumberRoutingsIntoNumberField-test") should equal(true)

      numberFieldOf(differing.accountId.value) should equal(routed)
      findByNumber("ACCOUNT_NUMBER", routed) should equal(Full(differing.accountId.value))
      numberFieldOf(same.accountId.value) should equal("5555555555")
      numberFieldOf(fieldOnly.accountId.value) should equal("6666666666")
      numberFieldOf(twoRoutings.accountId.value) should equal("7777777777")
    }
  }
}
