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

package code.bankconnectors

import code.api.util.ErrorMessages._
import code.api.util.RoutingRules
import code.model.dataAccess.{BankAccountRouting, MappedBank, MappedBankAccount}
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Finding banks and accounts by routing, against data stored by the local database connector.
 *
 * RoutingRulesTest covers the rules on the commons models. This suite covers the same rules for data
 * that went through the database: a bank's BIC stored in its bank routing or in its deprecated
 * swift_bic field, and an account's number stored in an ACCOUNT_NUMBER account routing or in its
 * deprecated account number field.
 */
class RoutingLookupTest extends ServerSetupWithTestData with DefaultUsers {

  object RoutingLookup extends Tag("RoutingLookup")

  // A BIC no real bank can have: ZZ is not assigned to any country. Each test gets its own.
  private def newBic(): String = ("T" + randomString(3) + "ZZ22").toUpperCase

  private def newBankId(): String = "bank." + randomString(8).toLowerCase

  private def storeBank(bankId: String, routingScheme: String = "", routingAddress: String = "", swiftBic: String = ""): MappedBank =
    MappedBank.create
      .permalink(bankId)
      .fullBankName("Routing Lookup Test Bank")
      .shortBankName("RLT")
      .mBankRoutingScheme(routingScheme)
      .mBankRoutingAddress(routingAddress)
      .swiftBIC(swiftBic)
      .saveMe()

  private def allBanks(): List[Bank] =
    Await.result(Connector.connector.vend.getBanks(None), 20.seconds).map(_._1).openOrThrowException("getBanks failed")

  private def findBank(scheme: String, address: String): Box[Bank] =
    RoutingRules.findBank(allBanks(), scheme, address)

  private def newAccount(bankId: BankId, number: String = ""): BankAccount = {
    val account = createAccountRelevantResource(Some(resourceUser1), bankId, AccountId("acc-" + randomString(8).toLowerCase), "EUR")
    MappedBankAccount.find(By(MappedBankAccount.bank, bankId.value), By(MappedBankAccount.theAccountId, account.accountId.value))
      .openOrThrowException("account not stored").accountNumber(number).saveMe()
  }

  private def storeRouting(account: BankAccount, scheme: String, address: String): Unit =
    BankAccountRouting.create
      .BankId(account.bankId.value)
      .AccountId(account.accountId.value)
      .AccountRoutingScheme(scheme)
      .AccountRoutingAddress(address)
      .saveMe()

  private def findAccount(bankId: Option[BankId], scheme: String, address: String): Box[AccountId] =
    Connector.connector.vend.getBankAccountByRoutingLegacy(bankId, scheme, address, None).map(_._1.accountId)

  private def failsWith[T](result: Box[T], error: String): Unit = result match {
    case f: Failure => f.msg should include(error.split(":").head)
    case other => fail(s"Expected a Failure with ${error.split(":").head}, got $other")
  }

  feature("Finding a stored bank by its BIC") {

    scenario("The BIC is stored in the bank routing", RoutingLookup) {
      val bic = newBic(); val bankId = newBankId()
      storeBank(bankId, routingScheme = "BIC", routingAddress = bic)
      findBank("BIC", bic).map(_.bankId.value) should equal(Full(bankId))
    }

    scenario("The BIC is stored only in swift_bic", RoutingLookup) {
      val bic = newBic(); val bankId = newBankId()
      storeBank(bankId, routingScheme = "BLZ", routingAddress = randomString(8), swiftBic = bic)
      findBank("BIC", bic).map(_.bankId.value) should equal(Full(bankId))
    }

    scenario("The same BIC is stored in both", RoutingLookup) {
      val bic = newBic(); val bankId = newBankId()
      storeBank(bankId, routingScheme = "BIC", routingAddress = bic, swiftBic = bic)
      findBank("BIC", bic).map(_.bankId.value) should equal(Full(bankId))
    }

    scenario("Two different BICs are stored: looking up either is an error", RoutingLookup) {
      val inRouting = newBic(); val inSwift = newBic()
      storeBank(newBankId(), routingScheme = "BIC", routingAddress = inRouting, swiftBic = inSwift)
      failsWith(findBank("BIC", inRouting), BankBicConflict)
      failsWith(findBank("BIC", inSwift), BankBicConflict)
    }

    scenario("Two stored banks share a BIC, one in its bank routing and one in swift_bic", RoutingLookup) {
      val bic = newBic()
      storeBank(newBankId(), routingScheme = "BIC", routingAddress = bic)
      storeBank(newBankId(), swiftBic = bic)
      failsWith(findBank("BIC", bic), BankRoutingNotUnique)
    }

    scenario("No stored bank has the BIC", RoutingLookup) {
      findBank("BIC", newBic()) should equal(Empty)
    }
  }

  feature("Finding a stored account by its account number") {

    scenario("The number is stored only in the account number field", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      val account = newAccount(bankId, number = "0123456789")
      findAccount(Some(bankId), "ACCOUNT_NUMBER", "0123456789") should equal(Full(account.accountId))
    }

    scenario("The number is stored only in an ACCOUNT_NUMBER account routing", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      val account = newAccount(bankId)
      storeRouting(account, "ACCOUNT_NUMBER", "0123456789")
      findAccount(Some(bankId), "ACCOUNT_NUMBER", "0123456789") should equal(Full(account.accountId))
    }

    scenario("The number is stored in an account routing named AccountNumber, and looked up as ACCOUNT_NUMBER", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      val account = newAccount(bankId)
      storeRouting(account, "AccountNumber", "0123456789")
      findAccount(Some(bankId), "ACCOUNT_NUMBER", "0123456789") should equal(Full(account.accountId))
      findAccount(Some(bankId), "AccountNumber", "0123456789") should equal(Full(account.accountId))
      findAccount(Some(bankId), "number", "0123456789") should equal(Full(account.accountId))
    }

    scenario("The same number is stored in both", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      val account = newAccount(bankId, number = "0123456789")
      storeRouting(account, "ACCOUNT_NUMBER", "0123456789")
      findAccount(Some(bankId), "ACCOUNT_NUMBER", "0123456789") should equal(Full(account.accountId))
    }

    scenario("Two different numbers are stored: looking up either is an error", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      val account = newAccount(bankId, number = "0123456789")
      storeRouting(account, "ACCOUNT_NUMBER", "9876543210")
      failsWith(findAccount(Some(bankId), "ACCOUNT_NUMBER", "0123456789"), AccountNumberConflict)
      failsWith(findAccount(Some(bankId), "ACCOUNT_NUMBER", "9876543210"), AccountNumberConflict)
    }

    scenario("Two accounts at the same bank have the same number", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      newAccount(bankId, number = "0123456789")
      newAccount(bankId, number = "0123456789")
      failsWith(findAccount(Some(bankId), "ACCOUNT_NUMBER", "0123456789"), AccountRoutingNotUnique)
    }

    scenario("Accounts at two banks have the same number: each is found at its own bank", RoutingLookup) {
      val bankA = BankId(storeBank(newBankId()).permalink.get)
      val bankB = BankId(storeBank(newBankId()).permalink.get)
      val accountA = newAccount(bankA, number = "0123456789")
      val accountB = newAccount(bankB, number = "0123456789")
      findAccount(Some(bankA), "ACCOUNT_NUMBER", "0123456789") should equal(Full(accountA.accountId))
      findAccount(Some(bankB), "ACCOUNT_NUMBER", "0123456789") should equal(Full(accountB.accountId))
    }
  }

  feature("Finding a stored account by a bank routing and an account routing together") {

    // The resolver reports some failures by throwing, so a thrown error is turned into a Failure here.
    def byRoutings(bank: BankRoutingJson, account: BranchRoutingJsonV141): Box[AccountId] =
      try {
        Await.result(Connector.connector.vend.getBankAccountByRoutings(
          BankAccountRoutings(bank = bank, account = account, branch = AccountRoutingJsonV121("", "")), None), 20.seconds)
          ._1.map(_.accountId)
      } catch {
        case e: Exception => Failure(String.valueOf(e.getMessage))
      }

    scenario("The bank is named by its BIC and the account by its number", RoutingLookup) {
      val bic = newBic()
      val bankId = BankId(storeBank(newBankId(), routingScheme = "BIC", routingAddress = bic).permalink.get)
      val account = newAccount(bankId, number = "0123456789")
      byRoutings(BankRoutingJson("BIC", bic), BranchRoutingJsonV141("ACCOUNT_NUMBER", "0123456789")) should equal(Full(account.accountId))
    }

    scenario("The bank is named by a BIC stored only in swift_bic and the account by its number", RoutingLookup) {
      val bic = newBic()
      val bankId = BankId(storeBank(newBankId(), swiftBic = bic).permalink.get)
      val account = newAccount(bankId, number = "0123456789")
      byRoutings(BankRoutingJson("BIC", bic), BranchRoutingJsonV141("ACCOUNT_NUMBER", "0123456789")) should equal(Full(account.accountId))
    }

    scenario("The bank is named by its OBP bank id and the account by its number", RoutingLookup) {
      val bankId = BankId(storeBank(newBankId()).permalink.get)
      val account = newAccount(bankId, number = "0123456789")
      byRoutings(BankRoutingJson("OBP", bankId.value), BranchRoutingJsonV141("ACCOUNT_NUMBER", "0123456789")) should equal(Full(account.accountId))
    }
  }
}
