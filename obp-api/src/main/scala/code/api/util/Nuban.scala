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

import com.openbankproject.commons.model.Bank

import scala.util.Random

/**
 * The Nigeria Uniform Bank Account Number (NUBAN), as set out by the Central Bank of Nigeria (CBN)
 * in its revised NUBAN standard of 2020.
 *
 * A NUBAN has ten digits: a nine digit serial number chosen by the bank, then one check digit. The
 * check digit is worked out from the bank's six digit institution code and the serial number:
 *
 *  - Write the institution code and the serial number one after the other, giving 15 digits.
 *  - Multiply the digits in turn by 3, 7, 3, 3, 7, 3, 3, 7, 3, 3, 7, 3, 3, 7, 3 and add the results.
 *  - Take the sum modulo 10 and subtract it from 10. The check digit is the result, or 0 when the result is 10.
 *
 * A deposit money bank has a three digit CBN bank code, which becomes the institution code by
 * putting 000 in front of it (011 becomes 000011). Another financial institution has a five digit
 * code, which becomes the institution code by putting 9 in front of it (50547 becomes 950547).
 *
 * For banks with a three digit code, this gives the same check digit as the first NUBAN standard
 * of 2010, because the added zeros add nothing to the sum.
 *
 * In OBP, a bank's CBN code is kept in a bank routing with scheme NG.BANK_CODE. An account's NUBAN is
 * kept in an account routing with scheme NG.NUBAN, and is also the account's number.
 */
object Nuban {

  /** The account routing scheme for a NUBAN. */
  val NubanScheme = "NG.NUBAN"

  /** The bank routing scheme for a bank's CBN code: 3 digits for a bank, 5 for another financial institution, or the 6 digit institution code. */
  val BankCodeScheme = "NG.BANK_CODE"

  private val weights = List(3, 7, 3, 3, 7, 3, 3, 7, 3, 3, 7, 3, 3, 7, 3)

  private def allDigits(value: String, length: Int): Boolean =
    value != null && value.length == length && value.forall(_.isDigit)

  /** True when `scheme` names a NUBAN: NG.NUBAN or NUBAN, in any letter case. */
  def isNubanScheme(scheme: String): Boolean =
    RoutingRules.sameScheme(scheme, NubanScheme) || RoutingRules.sameScheme(scheme, "NUBAN")

  /** True when `scheme` names a CBN bank code: NG.BANK_CODE, in any letter case. */
  def isBankCodeScheme(scheme: String): Boolean = RoutingRules.sameScheme(scheme, BankCodeScheme)

  /** The six digit institution code for a CBN bank code of 3, 5 or 6 digits, or None for anything else. */
  def institutionCodeOf(bankCode: String): Option[String] = Option(bankCode).map(_.trim) match {
    case Some(code) if allDigits(code, 3) => Some("000" + code)
    case Some(code) if allDigits(code, 5) => Some("9" + code)
    case Some(code) if allDigits(code, 6) => Some(code)
    case _ => None
  }

  /** The check digit for a nine digit serial number at the bank with this six digit institution code. */
  def checkDigit(institutionCode: String, serialNumber: String): Int = {
    require(allDigits(institutionCode, 6), s"An institution code has 6 digits, not '$institutionCode'.")
    require(allDigits(serialNumber, 9), s"A NUBAN serial number has 9 digits, not '$serialNumber'.")
    val sum = (institutionCode + serialNumber).map(_.asDigit).zip(weights).map { case (digit, weight) => digit * weight }.sum
    (10 - sum % 10) % 10
  }

  /** The NUBAN for a nine digit serial number at the bank with this six digit institution code. */
  def nubanOf(institutionCode: String, serialNumber: String): String =
    serialNumber + checkDigit(institutionCode, serialNumber)

  /** True when `nuban` is ten digits whose last digit is the right check digit for the bank with this CBN bank code. */
  def isValid(bankCode: String, nuban: String): Boolean = {
    val number = Option(nuban).map(_.trim).getOrElse("")
    allDigits(number, 10) && institutionCodeOf(bankCode).exists(code => checkDigit(code, number.take(9)) == number.last.asDigit)
  }

  /** A NUBAN with a random serial number at the bank with this six digit institution code. */
  def randomNuban(institutionCode: String): String =
    nubanOf(institutionCode, (1 to 9).map(_ => Random.nextInt(10)).mkString)

  /**
   * The bank's CBN bank code, from its NG.BANK_CODE bank routings. Left when the bank has none, or
   * has two different ones, or the one it has is not 3, 5 or 6 digits. The Left is the reason.
   */
  def bankCodeOf(bank: Bank): Either[String, String] =
    RoutingRules.bankRoutingAddressesOf(bank, BankCodeScheme).distinct match {
      case code :: Nil if institutionCodeOf(code).isDefined => Right(code)
      case code :: Nil => Left(s"The bank's $BankCodeScheme routing '$code' is not 3, 5 or 6 digits.")
      case Nil => Left(s"The bank has no $BankCodeScheme bank routing.")
      case codes => Left(s"The bank has more than one $BankCodeScheme bank routing: ${codes.mkString(", ")}.")
    }
}
