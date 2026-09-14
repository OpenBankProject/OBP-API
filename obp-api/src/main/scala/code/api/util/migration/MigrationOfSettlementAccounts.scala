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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import code.api.util.APIUtil
import code.api.util.migration.Migration.saveLog
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import net.liftweb.common.Full
import net.liftweb.mapper.By

import scala.util.Try

object MigrationOfSettlementAccounts {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def populate(name: String): Boolean = {

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit

    val banks = MappedBank.findAll()

    // For each bank
    val insertedSettlementAccounts = banks.map(bank => {

      // Insert the default settlement accounts if they doesn't exist

      val insertedIncomingSettlementAccount = MappedBankAccount.find(By(MappedBankAccount.bank, bank.bankId.value), By(MappedBankAccount.theAccountId, INCOMING_SETTLEMENT_ACCOUNT_ID)) match {
        case Full(_) =>
          Try {
            Console.println(s"Settlement BankAccount(${bank.bankId.value}, $INCOMING_SETTLEMENT_ACCOUNT_ID) found.")
            0
          }
        case _ =>
          Try {
            MappedBankAccount.create
              .bank(bank.bankId.value)
              .theAccountId(INCOMING_SETTLEMENT_ACCOUNT_ID)
              .accountCurrency("EUR")
              .accountBalance(0)
              .kind("SETTLEMENT")
              .holder(bank.fullName)
              .accountName("Default incoming settlement account")
              .accountLabel("Settlement account: Do not delete!")
              .saveMe()
            Console.println(s"Creating settlement BankAccount(${bank.bankId.value}, $INCOMING_SETTLEMENT_ACCOUNT_ID).")
            1
          }
      }

      val insertedOutgoingSettlementAccount = MappedBankAccount.find(By(MappedBankAccount.bank, bank.bankId.value), By(MappedBankAccount.theAccountId, OUTGOING_SETTLEMENT_ACCOUNT_ID)) match {
        case Full(_) =>
          Try {
            Console.println(s"Settlement BankAccount(${bank.bankId.value}, $OUTGOING_SETTLEMENT_ACCOUNT_ID) found.")
            0
          }
        case _ =>
          Try {
            MappedBankAccount.create
              .bank(bank.bankId.value)
              .theAccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID)
              .accountCurrency("EUR")
              .accountBalance(0)
              .kind("SETTLEMENT")
              .holder(bank.fullName)
              .accountName("Default outgoing settlement account")
              .accountLabel("Settlement account: Do not delete!")
              .saveMe()
            Console.println(s"Creating settlement BankAccount(${bank.bankId.value}, $OUTGOING_SETTLEMENT_ACCOUNT_ID).")
            1
          }
      }
      for {
        inc <- insertedIncomingSettlementAccount
        out <- insertedOutgoingSettlementAccount
      } yield inc + out
    })

    val isSuccessful = insertedSettlementAccounts.forall(_.isSuccess)
    val endDate = System.currentTimeMillis()
    val comment: String =
      s"""Number of default settlement accounts inserted at table MappedBankAccount: ${insertedSettlementAccounts.map(_.getOrElse(0)).sum}
         |""".stripMargin
    saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
    isSuccessful
  }
}
