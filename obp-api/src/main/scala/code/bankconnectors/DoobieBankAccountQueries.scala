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

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

object DoobieBankAccountQueries {

  /**
   * Atomically updates the bank account balance using a database row lock (SELECT FOR UPDATE).
   * 
   * @param bankId The bank ID
   * @param accountId The account ID
   * @param amount The amount to add (can be negative for deductions)
   * @return The new balance after the update
   */
  def atomicallyUpdateBalance(bankId: String, accountId: String, amount: Long): ConnectionIO[Long] = {
    for {
      // 1. Lock the row and get the current balance
      currentBalance <- sql"SELECT accountbalance FROM mappedbankaccount WHERE bank = $bankId AND theaccountid = $accountId FOR UPDATE".query[Long].unique
      
      newBalance = currentBalance + amount
      
      // 2. Update the row with the new balance
      _ <- sql"UPDATE mappedbankaccount SET accountbalance = $newBalance WHERE bank = $bankId AND theaccountid = $accountId".update.run
    } yield newBalance
  }

  def updateBalance(bankId: String, accountId: String, amount: Long): Box[Long] = {
    tryo {
      DoobieUtil.runUpdate(atomicallyUpdateBalance(bankId, accountId, amount))
    }
  }
}
