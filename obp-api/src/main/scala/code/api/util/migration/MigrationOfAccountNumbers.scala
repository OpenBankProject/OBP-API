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

package code.api.util.migration

import code.api.util.{APIUtil, RoutingRules}
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import net.liftweb.mapper.By

/**
 * An account's number belongs in an account number routing (scheme ACCOUNT_NUMBER, or another
 * spelling such as AccountNumber or NUMBER). The account number field is the deprecated fallback,
 * and an account whose two numbers differ cannot be looked up by its number.
 *
 * Before this migration, OBP gave every account created through the API a random number in the
 * account number field, even when the request gave the number as a routing. This migration copies
 * the routing's number into the field wherever the two differ, because the routing is where the
 * number belongs.
 *
 * An account with two account number routings that hold different numbers is left as it is and
 * listed in the migration log, because there is no way to tell which number is right.
 */
object MigrationOfAccountNumbers {

  def copyRoutingNumbersIntoNumberField(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    if (!DbFunction.tableExists(BankAccountRouting)) {
      saveLog(name, commitId, isSuccessful = false, startDate, System.currentTimeMillis(), "BankAccountRouting table does not exist")
      false
    } else {
      val numberRoutings = BankAccountRouting.findAll()
        .filter(routing => RoutingRules.isAccountNumberScheme(routing.accountRouting.scheme))
        .filter(routing => Option(routing.accountRouting.address).exists(_.trim.nonEmpty))
      val byAccount = numberRoutings.groupBy(routing => (routing.bankId.value, routing.accountId.value))

      var updated = List.empty[String]
      var skipped = List.empty[String]
      byAccount.foreach { case ((bankId, accountId), routings) =>
        routings.map(_.accountRouting.address.trim).distinct match {
          case number :: Nil =>
            MappedBankAccount.find(By(MappedBankAccount.bank, bankId), By(MappedBankAccount.theAccountId, accountId))
              .filter(account => Option(account.accountNumber.get).map(_.trim).getOrElse("") != number)
              .foreach { account =>
                account.accountNumber(number).saveMe()
                updated = s"$bankId/$accountId" :: updated
              }
          case several =>
            skipped = s"$bankId/$accountId (${several.mkString(", ")})" :: skipped
        }
      }

      val comment =
        s"""Copied the account number routing's number into the account number field of ${updated.size} accounts.
           |Accounts with two different account number routings, left as they are: ${if (skipped.isEmpty) "none" else skipped.mkString("; ")}.
           |Updated accounts (bank_id/account_id): ${if (updated.isEmpty) "none" else updated.mkString(", ")}.""".stripMargin
      saveLog(name, commitId, isSuccessful = true, startDate, System.currentTimeMillis(), comment)
      true
    }
  }
}
