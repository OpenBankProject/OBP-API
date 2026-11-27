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

package code.api.util

import java.util.Date

import code.api.util.ErrorMessages._
import code.api.v1_2_1.BankRoutingJsonV121
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Empty, Failure, Full}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * The rules for a bank's BIC and an account's number, on the commons models, with no server.
 *
 * A bank's BIC belongs in its bank routing (scheme BIC). Its swift_bic field is deprecated and is
 * used only when the bank routing has no BIC. The same BIC in both is fine. Two different BICs are an
 * error on lookups. An account's number follows the same rule: it belongs in an ACCOUNT_NUMBER
 * account routing, and the account number field is the deprecated fallback.
 *
 * Every model here is built the way a connector could send it, nulls included, because the rules
 * must hold whichever connector supplied the bank or the account.
 */
class RoutingRulesTest extends FeatureSpec with Matchers with GivenWhenThen {

  object RoutingRulesTag extends Tag("RoutingRules")

  // A bank as any connector could send it. Every field except the bank id may be null.
  private def bank(
    bankId: String = "bank.routing.test",
    routingScheme: String = "",
    routingAddress: String = "",
    swiftBic: String = ""
  ): Bank = BankCommons(
    bankId = BankId(bankId),
    shortName = "Test",
    fullName = "Test Bank",
    logoUrl = "",
    websiteUrl = "",
    bankRoutingScheme = routingScheme,
    bankRoutingAddress = routingAddress,
    swiftBic = swiftBic,
    nationalIdentifier = ""
  )

  // An account as any connector could send it. The number and the routings may be null.
  private def account(
    accountId: String = "account-routing-test",
    number: String = "",
    routings: List[AccountRouting] = Nil
  ): BankAccount = BankAccountCommons(
    accountId = AccountId(accountId),
    accountType = "CURRENT",
    balance = BigDecimal(0),
    currency = "EUR",
    name = "Test",
    label = "",
    number = number,
    bankId = BankId("bank.routing.test"),
    lastUpdate = new Date(),
    branchId = "",
    accountRoutings = routings,
    accountRules = Nil,
    accountHolder = ""
  )

  private def bicEntries(routings: List[BankRoutingJsonV121]) = routings.filter(r => RoutingRules.isBicScheme(r.scheme))
  private def obpEntries(routings: List[BankRoutingJsonV121]) = routings.filter(_.scheme == "OBP")
  private def numberEntries(routings: List[AccountRoutingJsonV121]) = routings.filter(r => RoutingRules.isAccountNumberScheme(r.scheme))

  private def failsWith[T](result: Box[T], error: String): Unit = result match {
    case f: Failure => f.msg should include(error.split(":").head)
    case other => fail(s"Expected a Failure with ${error.split(":").head}, got $other")
  }

  feature("Which scheme names a BIC") {

    scenario("BIC and SWIFT_BIC, in any letter case and in camel case, name a BIC", RoutingRulesTag) {
      List("BIC", "bic", "Bic", "SWIFT_BIC", "swift_bic", "SwiftBic", "SWIFTBIC").foreach { scheme =>
        withClue(s"scheme $scheme: ") { RoutingRules.isBicScheme(scheme) should equal(true) }
      }
    }

    scenario("Other schemes, an empty scheme and a null scheme do not", RoutingRulesTag) {
      List("BLZ", "OBP", "IBAN", "", null).foreach { scheme =>
        withClue(s"scheme $scheme: ") { RoutingRules.isBicScheme(scheme) should equal(false) }
      }
    }
  }

  feature("A bank's BIC") {

    scenario("The BIC is only in the bank routing", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22")) should equal(Full(Some("DEMAZZ22")))
    }

    scenario("The BIC is only in the deprecated swift_bic field", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "BLZ", routingAddress = "10010010", swiftBic = "DEMAZZ22")) should equal(Full(Some("DEMAZZ22")))
    }

    scenario("The same BIC is in both", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22", swiftBic = "DEMAZZ22")) should equal(Full(Some("DEMAZZ22")))
    }

    scenario("The same BIC is in both, written in a different letter case and with spaces round it", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22", swiftBic = " demazz22 ")) should equal(Full(Some("DEMAZZ22")))
    }

    scenario("Two different BICs are an error", RoutingRulesTag) {
      failsWith(RoutingRules.bicOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22", swiftBic = "DEMBZZ22")), BankBicConflict)
    }

    scenario("The bank routing names its scheme SWIFT_BIC", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "SWIFT_BIC", routingAddress = "DEMAZZ22")) should equal(Full(Some("DEMAZZ22")))
    }

    scenario("The bank has no BIC", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "BLZ", routingAddress = "10010010")) should equal(Full(None))
    }

    scenario("A bank routing with the BIC scheme but an empty address falls back to swift_bic", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = "BIC", routingAddress = "", swiftBic = "DEMAZZ22")) should equal(Full(Some("DEMAZZ22")))
    }

    scenario("A connector sent every routing field as null", RoutingRulesTag) {
      RoutingRules.bicOf(bank(routingScheme = null, routingAddress = null, swiftBic = null)) should equal(Full(None))
    }
  }

  feature("A bank's routings as responses show them") {

    scenario("There is exactly one OBP entry, it comes first and its address is the bank id", RoutingRulesTag) {
      val routings = RoutingRules.bankRoutingsOf(bank(bankId = "bank.a", routingScheme = "BIC", routingAddress = "DEMAZZ22"))
      obpEntries(routings) should equal(List(BankRoutingJsonV121("OBP", "bank.a")))
      routings.head should equal(BankRoutingJsonV121("OBP", "bank.a"))
    }

    scenario("A stored OBP routing that is not the bank id is replaced by the bank id", RoutingRulesTag) {
      val routings = RoutingRules.bankRoutingsOf(bank(bankId = "bank.a", routingScheme = "OBP", routingAddress = "something-else"))
      obpEntries(routings) should equal(List(BankRoutingJsonV121("OBP", "bank.a")))
    }

    scenario("A BIC only in the bank routing gives one BIC entry", RoutingRulesTag) {
      bicEntries(RoutingRules.bankRoutingsOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22"))) should equal(
        List(BankRoutingJsonV121("BIC", "DEMAZZ22")))
    }

    scenario("A BIC only in swift_bic gives one BIC entry", RoutingRulesTag) {
      bicEntries(RoutingRules.bankRoutingsOf(bank(swiftBic = "DEMAZZ22"))) should equal(
        List(BankRoutingJsonV121("BIC", "DEMAZZ22")))
    }

    scenario("The same BIC in both gives one BIC entry, not two", RoutingRulesTag) {
      bicEntries(RoutingRules.bankRoutingsOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22", swiftBic = "DEMAZZ22"))) should equal(
        List(BankRoutingJsonV121("BIC", "DEMAZZ22")))
    }

    scenario("A bank routing named SWIFT_BIC is shown as BIC", RoutingRulesTag) {
      bicEntries(RoutingRules.bankRoutingsOf(bank(routingScheme = "SWIFT_BIC", routingAddress = "DEMAZZ22"))) should equal(
        List(BankRoutingJsonV121("BIC", "DEMAZZ22")))
    }

    // Lookups fail on two different BICs. A response still shows the bank, with the BIC from its
    // bank routing, because the bank routing is where the BIC belongs.
    scenario("Two different BICs: the response shows only the BIC in the bank routing", RoutingRulesTag) {
      bicEntries(RoutingRules.bankRoutingsOf(bank(routingScheme = "BIC", routingAddress = "DEMAZZ22", swiftBic = "DEMBZZ22"))) should equal(
        List(BankRoutingJsonV121("BIC", "DEMAZZ22")))
    }

    scenario("A bank with no BIC has no BIC entry, not an empty one", RoutingRulesTag) {
      val routings = RoutingRules.bankRoutingsOf(bank(routingScheme = "BLZ", routingAddress = "10010010"))
      bicEntries(routings) should equal(Nil)
      routings.foreach(r => r.address should not be empty)
    }

    scenario("A BIC in swift_bic and another scheme in the bank routing are both shown", RoutingRulesTag) {
      RoutingRules.bankRoutingsOf(bank(bankId = "bank.a", routingScheme = "BLZ", routingAddress = "10010010", swiftBic = "DEMAZZ22")) should equal(
        List(BankRoutingJsonV121("OBP", "bank.a"), BankRoutingJsonV121("BIC", "DEMAZZ22"), BankRoutingJsonV121("BLZ", "10010010")))
    }

    scenario("A connector sent every routing field as null: only the OBP entry is shown", RoutingRulesTag) {
      RoutingRules.bankRoutingsOf(bank(bankId = "bank.a", routingScheme = null, routingAddress = null, swiftBic = null)) should equal(
        List(BankRoutingJsonV121("OBP", "bank.a")))
    }
  }

  feature("Finding a bank by a routing") {

    val bicInRouting = bank(bankId = "bank.routing", routingScheme = "BIC", routingAddress = "DEMAZZ22")
    val bicInSwift = bank(bankId = "bank.swift", routingScheme = "BLZ", routingAddress = "10010010", swiftBic = "DEMBZZ22")
    val bicInBoth = bank(bankId = "bank.both", routingScheme = "BIC", routingAddress = "DEMCZZ22", swiftBic = "DEMCZZ22")
    val noBic = bank(bankId = "bank.nobic", routingScheme = "BLZ", routingAddress = "20020020")
    val allNull = bank(bankId = "bank.null", routingScheme = null, routingAddress = null, swiftBic = null)
    val banks = List(bicInRouting, bicInSwift, bicInBoth, noBic, allNull)

    scenario("By a BIC in the bank routing", RoutingRulesTag) {
      RoutingRules.findBank(banks, "BIC", "DEMAZZ22") should equal(Full(bicInRouting))
    }

    scenario("By a BIC only in swift_bic", RoutingRulesTag) {
      RoutingRules.findBank(banks, "BIC", "DEMBZZ22") should equal(Full(bicInSwift))
    }

    scenario("By a BIC that is in both", RoutingRulesTag) {
      RoutingRules.findBank(banks, "BIC", "DEMCZZ22") should equal(Full(bicInBoth))
    }

    scenario("By a BIC written in lower case and with spaces round it", RoutingRulesTag) {
      RoutingRules.findBank(banks, "BIC", " demazz22 ") should equal(Full(bicInRouting))
    }

    scenario("By a BIC, with the scheme written bic or SWIFT_BIC", RoutingRulesTag) {
      RoutingRules.findBank(banks, "bic", "DEMAZZ22") should equal(Full(bicInRouting))
      RoutingRules.findBank(banks, "SWIFT_BIC", "DEMBZZ22") should equal(Full(bicInSwift))
    }

    scenario("By a BIC no bank has", RoutingRulesTag) {
      RoutingRules.findBank(banks, "BIC", "DEMZZZ22") should equal(Empty)
    }

    scenario("Two banks share a BIC, one in its bank routing and one in swift_bic", RoutingRulesTag) {
      val twin = bank(bankId = "bank.twin", swiftBic = "DEMAZZ22")
      failsWith(RoutingRules.findBank(twin :: banks, "BIC", "DEMAZZ22"), BankRoutingNotUnique)
    }

    scenario("Two banks share a BIC, both in their bank routing", RoutingRulesTag) {
      val twin = bank(bankId = "bank.twin", routingScheme = "BIC", routingAddress = "DEMAZZ22")
      failsWith(RoutingRules.findBank(twin :: banks, "BIC", "DEMAZZ22"), BankRoutingNotUnique)
    }

    scenario("The bank with this BIC in its bank routing has a different BIC in swift_bic", RoutingRulesTag) {
      val conflicting = bank(bankId = "bank.conflict", routingScheme = "BIC", routingAddress = "DEMDZZ22", swiftBic = "DEMEZZ22")
      failsWith(RoutingRules.findBank(conflicting :: banks, "BIC", "DEMDZZ22"), BankBicConflict)
    }

    scenario("The bank with this BIC in swift_bic has a different BIC in its bank routing", RoutingRulesTag) {
      // The BIC asked for is only in the deprecated field, and the bank routing says otherwise. The
      // bank is not silently passed over: the conflict is reported.
      val conflicting = bank(bankId = "bank.conflict", routingScheme = "BIC", routingAddress = "DEMDZZ22", swiftBic = "DEMEZZ22")
      failsWith(RoutingRules.findBank(conflicting :: banks, "BIC", "DEMEZZ22"), BankBicConflict)
    }

    scenario("By the OBP scheme and by OBP_BANK_ID, the address is the bank id", RoutingRulesTag) {
      RoutingRules.findBank(banks, "OBP", "bank.swift") should equal(Full(bicInSwift))
      RoutingRules.findBank(banks, "OBP_BANK_ID", "bank.swift") should equal(Full(bicInSwift))
    }

    scenario("By another scheme in the bank routing", RoutingRulesTag) {
      RoutingRules.findBank(banks, "BLZ", "20020020") should equal(Full(noBic))
    }

    scenario("Responses and lookups agree: every routing a response shows finds that bank", RoutingRulesTag) {
      banks.foreach { b =>
        RoutingRules.bankRoutingsOf(b).foreach { r =>
          withClue(s"bank ${b.bankId.value}, routing ${r.scheme} ${r.address}: ") {
            RoutingRules.findBank(banks, r.scheme, r.address).map(_.bankId) should equal(Full(b.bankId))
          }
        }
      }
    }
  }

  feature("Which scheme names an account number") {

    scenario("ACCOUNT_NUMBER, AccountNumber, ACCOUNT_NO and NUMBER, in any letter case, name an account number", RoutingRulesTag) {
      List("ACCOUNT_NUMBER", "account_number", "AccountNumber", "accountNumber", "ACCOUNT_NO", "NUMBER", "number", "Number").foreach { scheme =>
        withClue(s"scheme $scheme: ") { RoutingRules.isAccountNumberScheme(scheme) should equal(true) }
      }
    }

    scenario("Other schemes, an empty scheme and a null scheme do not", RoutingRulesTag) {
      List("IBAN", "OBP", "", null).foreach { scheme =>
        withClue(s"scheme $scheme: ") { RoutingRules.isAccountNumberScheme(scheme) should equal(false) }
      }
    }
  }

  feature("An account's number") {

    scenario("The number is only in an ACCOUNT_NUMBER account routing", RoutingRulesTag) {
      RoutingRules.accountNumberOf(account(routings = List(AccountRouting("ACCOUNT_NUMBER", "0123456789")))) should equal(Full(Some("0123456789")))
    }

    scenario("The number is only in an account routing named AccountNumber", RoutingRulesTag) {
      RoutingRules.accountNumberOf(account(routings = List(AccountRouting("AccountNumber", "0123456789")))) should equal(Full(Some("0123456789")))
    }

    scenario("The number is only in the deprecated account number field", RoutingRulesTag) {
      RoutingRules.accountNumberOf(account(number = "0123456789")) should equal(Full(Some("0123456789")))
    }

    scenario("The same number is in both", RoutingRulesTag) {
      RoutingRules.accountNumberOf(account(number = "0123456789", routings = List(AccountRouting("ACCOUNT_NUMBER", "0123456789")))) should equal(Full(Some("0123456789")))
    }

    scenario("Two different numbers are an error", RoutingRulesTag) {
      failsWith(RoutingRules.accountNumberOf(account(number = "0123456789", routings = List(AccountRouting("ACCOUNT_NUMBER", "9876543210")))), AccountNumberConflict)
    }

    scenario("The account has no number", RoutingRulesTag) {
      RoutingRules.accountNumberOf(account(routings = List(AccountRouting("IBAN", "GB29NWBK60161331926819")))) should equal(Full(None))
    }

    scenario("A connector sent the number and the routings as null", RoutingRulesTag) {
      RoutingRules.accountNumberOf(account(number = null, routings = null)) should equal(Full(None))
    }
  }

  feature("An account's routings as responses show them") {

    scenario("There is exactly one OBP entry, it comes first and its address is the account id", RoutingRulesTag) {
      val routings = RoutingRules.accountRoutingsOf(account(accountId = "acc-1", routings = List(AccountRouting("OBP", "something-else"))))
      routings.filter(_.scheme == "OBP") should equal(List(AccountRoutingJsonV121("OBP", "acc-1")))
      routings.head should equal(AccountRoutingJsonV121("OBP", "acc-1"))
    }

    scenario("A number only in the account number field gives one ACCOUNT_NUMBER entry", RoutingRulesTag) {
      numberEntries(RoutingRules.accountRoutingsOf(account(number = "0123456789"))) should equal(
        List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "0123456789")))
    }

    scenario("A number only in an account routing named AccountNumber is shown as ACCOUNT_NUMBER", RoutingRulesTag) {
      numberEntries(RoutingRules.accountRoutingsOf(account(routings = List(AccountRouting("AccountNumber", "0123456789"))))) should equal(
        List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "0123456789")))
    }

    scenario("The same number in both gives one ACCOUNT_NUMBER entry, not two", RoutingRulesTag) {
      numberEntries(RoutingRules.accountRoutingsOf(account(number = "0123456789", routings = List(AccountRouting("ACCOUNT_NUMBER", "0123456789"))))) should equal(
        List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "0123456789")))
    }

    // As for banks: lookups fail on two different numbers, and a response shows the one in the
    // account routing, because the account routing is where the number belongs.
    scenario("Two different numbers: the response shows only the number in the account routing", RoutingRulesTag) {
      numberEntries(RoutingRules.accountRoutingsOf(account(number = "0123456789", routings = List(AccountRouting("ACCOUNT_NUMBER", "9876543210"))))) should equal(
        List(AccountRoutingJsonV121("ACCOUNT_NUMBER", "9876543210")))
    }

    scenario("An account with no number has no ACCOUNT_NUMBER entry, and other routings are kept", RoutingRulesTag) {
      RoutingRules.accountRoutingsOf(account(accountId = "acc-1", routings = List(AccountRouting("IBAN", "GB29NWBK60161331926819")))) should equal(
        List(AccountRoutingJsonV121("OBP", "acc-1"), AccountRoutingJsonV121("IBAN", "GB29NWBK60161331926819")))
    }

    scenario("A connector sent the number and the routings as null: only the OBP entry is shown", RoutingRulesTag) {
      RoutingRules.accountRoutingsOf(account(accountId = "acc-1", number = null, routings = null)) should equal(
        List(AccountRoutingJsonV121("OBP", "acc-1")))
    }
  }
}
