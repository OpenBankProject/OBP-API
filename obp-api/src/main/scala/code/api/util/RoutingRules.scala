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

import code.api.Constant
import code.api.util.ErrorMessages._
import code.api.v1_2_1.BankRoutingJsonV121
import com.openbankproject.commons.model.{AccountRoutingJsonV121, Bank, BankAccount, BankRoutingJson}
import net.liftweb.common.{Box, Empty, Failure, Full}

/**
 * The one place that says what a bank's and an account's routings are, and how to find a bank or an
 * account by one of them. Responses and lookups both use it, so they cannot disagree.
 *
 * A bank's BIC belongs in a bank routing with scheme BIC. A bank's routings are its bankRoutings list
 * and its older single bank routing pair (bankRoutingScheme / bankRoutingAddress). The deprecated
 * swift_bic field is used only when no bank routing holds a BIC. The same BIC in both places is fine.
 * Two different BICs are an error on lookups.
 *
 * An account's number works the same way. It belongs in an account routing with scheme
 * ACCOUNT_NUMBER, and the account number field is the deprecated fallback.
 *
 * Everything here works on the commons Bank and BankAccount, so the rules hold whichever connector
 * supplied them. A connector may send any field as null.
 */
object RoutingRules {

  /** The scheme name responses use for a BIC. */
  val BicScheme = "BIC"

  /** The scheme name responses use for an account number. */
  val AccountNumberScheme = "ACCOUNT_NUMBER"

  // Two spellings of one scheme compare equal when they agree after dropping letter case and
  // everything that is not a letter or a digit: AccountNumber, ACCOUNT_NUMBER and account-number.
  private def schemeKey(scheme: String): String =
    Option(scheme).getOrElse("").toUpperCase.filter(_.isLetterOrDigit)

  private val bicKeys = Set("BIC", "SWIFTBIC")
  private val accountNumberKeys = Set("ACCOUNTNUMBER", "ACCOUNTNO", "NUMBER")

  private def clean(value: String): String = Option(value).map(_.trim).getOrElse("")

  /** True when `scheme` names a BIC: BIC or SWIFT_BIC, in any letter case. */
  def isBicScheme(scheme: String): Boolean = bicKeys.contains(schemeKey(scheme))

  /** True when `scheme` names an account number: ACCOUNT_NUMBER, ACCOUNT_NO or NUMBER, in any letter case. */
  def isAccountNumberScheme(scheme: String): Boolean = accountNumberKeys.contains(schemeKey(scheme))

  /** True when two scheme names are spellings of the same scheme. */
  def sameScheme(a: String, b: String): Boolean =
    (isBicScheme(a) && isBicScheme(b)) ||
      (isAccountNumberScheme(a) && isAccountNumberScheme(b)) ||
      (schemeKey(a).nonEmpty && schemeKey(a) == schemeKey(b))

  // ─── Banks ──────────────────────────────────────────────────────────────────

  /**
   * The bank's own routings: its bankRoutings list, then its single bank routing pair. OBP-scheme
   * entries are left out, because the OBP routing is always the bank id. Entries with an empty
   * scheme or address are left out. The same routing listed twice is kept once.
   */
  private def storedRoutingsOf(bank: Bank): List[BankRoutingJson] = {
    val fromList = Option(bank.bankRoutings).getOrElse(Nil).filter(_ != null)
    val fromPair = List(BankRoutingJson(bank.bankRoutingScheme, bank.bankRoutingAddress))
    (fromList ++ fromPair)
      .map(r => BankRoutingJson(clean(r.scheme), clean(r.address)))
      .filter(r => r.scheme.nonEmpty && r.address.nonEmpty && !Constant.isImplicitOBPBankScheme(r.scheme))
      .foldLeft(List.empty[BankRoutingJson]) { (kept, r) =>
        if (kept.exists(k => sameScheme(k.scheme, r.scheme) && k.address.equalsIgnoreCase(r.address))) kept
        else kept :+ r
      }
  }

  /** The addresses of the bank's own routings with this scheme (any spelling of it), in the order they are stored. */
  def bankRoutingAddressesOf(bank: Bank, scheme: String): List[String] =
    storedRoutingsOf(bank).filter(r => sameScheme(r.scheme, scheme)).map(_.address)

  private def storedBicsOf(bank: Bank): List[String] =
    storedRoutingsOf(bank).filter(r => isBicScheme(r.scheme)).map(_.address.toUpperCase).distinct

  private def swiftBicOf(bank: Bank): Option[String] =
    Option(clean(bank.swiftBic)).filter(_.nonEmpty).map(_.toUpperCase)

  /**
   * The bank's BIC: Full(Some(bic)), Full(None) when it has none, or a Failure (BankBicConflict)
   * when it holds two different BICs.
   */
  def bicOf(bank: Bank): Box[Option[String]] = {
    def conflict = Failure(s"$BankBicConflict (bank_id: ${bank.bankId.value})")
    (storedBicsOf(bank), swiftBicOf(bank)) match {
      case (Nil, swift) => Full(swift)
      case (bic :: Nil, None) => Full(Some(bic))
      case (bic :: Nil, Some(swift)) if swift == bic => Full(Some(bic))
      case _ => conflict
    }
  }

  /**
   * The bank's routings as responses show them: exactly one OBP entry first, then at most one BIC,
   * then the bank's other routings in the order they are stored. No entry has an empty address.
   * When a bank holds two different BICs, the response shows the one in its bank routings, because
   * that is where the BIC belongs. Lookups report the conflict.
   */
  def bankRoutingsOf(bank: Bank): List[BankRoutingJsonV121] = {
    val stored = storedRoutingsOf(bank)
    val bic = storedBicsOf(bank).headOption.orElse(swiftBicOf(bank))
    val others = stored.filterNot(r => isBicScheme(r.scheme))
    BankRoutingJsonV121("OBP", bank.bankId.value) ::
      bic.map(BankRoutingJsonV121(BicScheme, _)).toList :::
      others.map(r => BankRoutingJsonV121(r.scheme, r.address))
  }

  /** True when the bank has this routing, without checking it for conflicts. */
  private def hasRouting(bank: Bank, scheme: String, address: String): Boolean = {
    val wanted = clean(address)
    if (Constant.isImplicitOBPBankScheme(scheme)) bank.bankId.value == wanted
    else if (isBicScheme(scheme)) (swiftBicOf(bank).toList ++ storedBicsOf(bank)).exists(_.equalsIgnoreCase(wanted))
    else storedRoutingsOf(bank).exists(r => sameScheme(r.scheme, scheme) && r.address.equalsIgnoreCase(wanted))
  }

  /**
   * The one bank among `banks` that has this routing. Empty when none has it. A Failure when more
   * than one has it (BankRoutingNotUnique), or when a bank that has the BIC asked for holds two
   * different BICs (BankBicConflict).
   */
  def findBank(banks: List[Bank], scheme: String, address: String): Box[Bank] = {
    val matching = Option(banks).getOrElse(Nil).filter(_ != null).filter(hasRouting(_, scheme, address))
    val conflicting =
      if (isBicScheme(scheme)) matching.map(bicOf).collectFirst { case f: Failure => f }
      else None
    conflicting match {
      case Some(failure) => failure
      case None => matching match {
        case bank :: Nil => Full(bank)
        case Nil => Empty
        case several => Failure(s"$BankRoutingNotUnique (scheme: $scheme, address: $address, bank_ids: ${several.map(_.bankId.value).mkString(", ")})")
      }
    }
  }

  /**
   * Bank routings from a create or update request, ready to store: trimmed, with every spelling of
   * BIC written as BIC and the BIC in capitals, without OBP-scheme or empty entries, and with each
   * routing kept once. A Failure when they hold two different BICs.
   */
  def bankRoutingsToStore(requested: List[BankRoutingJsonV121]): Box[List[BankRoutingJson]] = {
    val cleaned = Option(requested).getOrElse(Nil).filter(_ != null)
      .map(r => BankRoutingJson(clean(r.scheme), clean(r.address)))
      .filter(r => r.scheme.nonEmpty && r.address.nonEmpty && !Constant.isImplicitOBPBankScheme(r.scheme))
      .map(r => if (isBicScheme(r.scheme)) BankRoutingJson(BicScheme, r.address.toUpperCase) else r)
      .distinct
    val bics = cleaned.filter(_.scheme == BicScheme).map(_.address)
    if (bics.size > 1) Failure(s"$InvalidJsonValue A bank can have only one BIC. These bank_routings have ${bics.size}: ${bics.mkString(", ")}.")
    else Full(cleaned)
  }

  // ─── Accounts ───────────────────────────────────────────────────────────────

  private def storedAccountRoutingsOf(account: BankAccount): List[AccountRoutingJsonV121] =
    Option(account.accountRoutings).getOrElse(Nil).filter(_ != null)
      .map(r => AccountRoutingJsonV121(clean(r.scheme), clean(r.address)))
      .filter(r => r.scheme.nonEmpty && r.address.nonEmpty)

  private def routedNumbersOf(account: BankAccount): List[String] =
    storedAccountRoutingsOf(account).filter(r => isAccountNumberScheme(r.scheme)).map(_.address).distinct

  private def numberFieldOf(account: BankAccount): Option[String] =
    Option(clean(account.number)).filter(_.nonEmpty)

  /**
   * The account's number: Full(Some(number)), Full(None) when it has none, or a Failure
   * (AccountNumberConflict) when it holds two different numbers.
   */
  def accountNumberOf(account: BankAccount): Box[Option[String]] = {
    def conflict = Failure(s"$AccountNumberConflict (bank_id: ${account.bankId.value}, account_id: ${account.accountId.value})")
    (routedNumbersOf(account), numberFieldOf(account)) match {
      case (Nil, field) => Full(field)
      case (number :: Nil, None) => Full(Some(number))
      case (number :: Nil, Some(field)) if field == number => Full(Some(number))
      case _ => conflict
    }
  }

  /** True when the account has this number, in an account routing or in its account number field. */
  def hasAccountNumber(account: BankAccount, number: String): Boolean =
    (routedNumbersOf(account) ++ numberFieldOf(account).toList).contains(clean(number))

  /**
   * The account's routings as responses show them: exactly one OBP entry first, then the account's
   * other routings in the order they are stored, with at most one ACCOUNT_NUMBER entry. A number held
   * only in the account number field comes last. No entry has an empty address. When an account holds
   * two different numbers, the response shows the one in its account routings. Lookups report the
   * conflict.
   */
  def accountRoutingsOf(account: BankAccount): List[AccountRoutingJsonV121] =
    accountRoutingsOf(
      account.accountId.value,
      Option(account.accountRoutings).getOrElse(Nil).filter(_ != null).map(r => AccountRoutingJsonV121(r.scheme, r.address)),
      account.number)

  /**
   * The same, for a response built from a moderated account: `routings` and `number` are what the
   * view lets the caller see, so a number the view hides is never added. `number` may be null or empty.
   */
  def accountRoutingsOf(accountId: String, routings: List[AccountRoutingJsonV121], number: String): List[AccountRoutingJsonV121] = {
    val stored = Option(routings).getOrElse(Nil).filter(_ != null)
      .map(r => AccountRoutingJsonV121(clean(r.scheme), clean(r.address)))
      .filter(r => r.scheme.nonEmpty && r.address.nonEmpty)
      .filterNot(r => Constant.isImplicitOBPAccountScheme(r.scheme))
    val withOneNumber = stored.foldLeft(List.empty[AccountRoutingJsonV121]) { (kept, r) =>
      if (isAccountNumberScheme(r.scheme)) {
        if (kept.exists(k => isAccountNumberScheme(k.scheme))) kept
        else kept :+ AccountRoutingJsonV121(AccountNumberScheme, r.address)
      }
      else if (kept.contains(r)) kept
      else kept :+ r
    }
    val numberFromField =
      if (withOneNumber.exists(r => isAccountNumberScheme(r.scheme))) Nil
      else Option(clean(number)).filter(_.nonEmpty).map(AccountRoutingJsonV121(AccountNumberScheme, _)).toList
    AccountRoutingJsonV121("OBP", accountId) :: withOneNumber ::: numberFromField
  }
}
