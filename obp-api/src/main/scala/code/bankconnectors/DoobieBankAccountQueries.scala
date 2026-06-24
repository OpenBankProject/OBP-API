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
