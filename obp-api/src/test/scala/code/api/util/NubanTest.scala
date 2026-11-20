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

import com.openbankproject.commons.model.{BankCommons, BankId, BankRoutingJson}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * The NUBAN rules on their own, with no database. The worked example is the one in the CBN's revised
 * NUBAN standard of 2020: bank code 011, serial number 000001457, NUBAN 0000014579.
 */
class NubanTest extends FeatureSpec with Matchers with GivenWhenThen {

  object NubanRules extends Tag("Nuban")

  private def bankWith(routings: List[BankRoutingJson]) =
    BankCommons(BankId("ng-bank"), "NGB", "Nigerian Bank", "", "", "", "", "", "", routings)

  feature("The check digit") {

    scenario("The CBN's worked example: bank 011, serial 000001457 gives 0000014579", NubanRules) {
      Nuban.checkDigit("000011", "000001457") should equal(9)
      Nuban.nubanOf("000011", "000001457") should equal("0000014579")
    }

    scenario("A three digit bank code gives the same NUBAN as its six digit institution code", NubanRules) {
      Nuban.institutionCodeOf("011") should equal(Some("000011"))
      Nuban.isValid("011", "0000014579") should equal(true)
      Nuban.isValid("000011", "0000014579") should equal(true)
    }

    scenario("A five digit code of another financial institution gets a 9 in front", NubanRules) {
      Nuban.institutionCodeOf("50547") should equal(Some("950547"))
      val nuban = Nuban.nubanOf("950547", "123456789")
      Nuban.isValid("50547", nuban) should equal(true)
      Nuban.isValid("950547", nuban) should equal(true)
    }

    scenario("A sum that is a multiple of ten gives the check digit 0, not 10", NubanRules) {
      // 000000 then 000000000: every digit is 0, so the sum is 0.
      Nuban.checkDigit("000000", "000000000") should equal(0)
      // 000000 then 000000010: the 8th serial digit has weight 7, giving 7, so the check digit is 3.
      Nuban.checkDigit("000000", "000000010") should equal(3)
    }

    scenario("Every other last digit is refused", NubanRules) {
      (0 to 9).filterNot(_ == 9).foreach(digit => Nuban.isValid("011", "000001457" + digit) should equal(false))
    }

    scenario("A NUBAN that is right for one bank is wrong for another", NubanRules) {
      Nuban.isValid("011", "0000014579") should equal(true)
      Nuban.isValid("058", "0000014579") should equal(false)
    }

    scenario("Anything but ten digits is refused, and so is a bank code of the wrong length", NubanRules) {
      List("", "000001457", "00000145790", "00000145 9", "A000014579", null).foreach(nuban => Nuban.isValid("011", nuban) should equal(false))
      List("", "01", "0111", "011111A", null).foreach(code => Nuban.institutionCodeOf(code) should equal(None))
      Nuban.isValid("0111", "0000014579") should equal(false)
    }

    scenario("A random NUBAN is always valid at its bank", NubanRules) {
      (1 to 200).foreach { _ =>
        val nuban = Nuban.randomNuban("000011")
        nuban should fullyMatch regex "[0-9]{10}"
        Nuban.isValid("011", nuban) should equal(true)
      }
    }
  }

  feature("Scheme names") {

    scenario("NG.NUBAN and NUBAN name a NUBAN in any letter case, and are not account number schemes", NubanRules) {
      List("NG.NUBAN", "ng.nuban", "NUBAN", "Nuban", "NG_NUBAN").foreach(scheme => Nuban.isNubanScheme(scheme) should equal(true))
      List("ACCOUNT_NUMBER", "IBAN", "NG.BANK_CODE", "", null).foreach(scheme => Nuban.isNubanScheme(scheme) should equal(false))
      RoutingRules.isAccountNumberScheme("NG.NUBAN") should equal(false)
    }
  }

  feature("The bank's CBN code") {

    scenario("Taken from the bank's NG.BANK_CODE routing", NubanRules) {
      Nuban.bankCodeOf(bankWith(List(BankRoutingJson("BIC", "ABCDNGLA"), BankRoutingJson("NG.BANK_CODE", "011")))) should equal(Right("011"))
    }

    scenario("Missing, two different codes, or a code of the wrong length: no NUBAN can be worked out", NubanRules) {
      Nuban.bankCodeOf(bankWith(List(BankRoutingJson("BIC", "ABCDNGLA")))).isLeft should equal(true)
      Nuban.bankCodeOf(bankWith(List(BankRoutingJson("NG.BANK_CODE", "011"), BankRoutingJson("NG.BANK_CODE", "058")))).isLeft should equal(true)
      Nuban.bankCodeOf(bankWith(List(BankRoutingJson("NG.BANK_CODE", "0111")))).isLeft should equal(true)
    }

    scenario("The same code in the routing list and the single routing pair is one code", NubanRules) {
      val bank = bankWith(List(BankRoutingJson("NG.BANK_CODE", "011"))).copy(bankRoutingScheme = "NG.BANK_CODE", bankRoutingAddress = "011")
      Nuban.bankCodeOf(bank) should equal(Right("011"))
    }
  }
}
