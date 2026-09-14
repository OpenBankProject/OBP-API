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

import code.api.Constant
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.accountholders.MapperAccountHolders
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.MappedBankAccount
import code.views.system.AccountAccess
import net.liftweb.mapper.{By, ByList, DB}
import net.liftweb.util.DefaultConnectionIdentifier

object BankAccountHoldersAndOwnerViewAccess {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def saveInfoBankAccountHoldersAndOwnerViewAccessInfo(name: String): Boolean = {
    DbFunction.tableExists(MapperAccountHolders) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        
        val accountHolderInfo =
          for {
            bankAccount <- MappedBankAccount.findAll()
            accountHolder = MapperAccountHolders.findAll(
              By(MapperAccountHolders.accountBankPermalink, bankAccount.bankId.value),
              By(MapperAccountHolders.accountPermalink, bankAccount.accountId.value)
            )
          } yield {
            (bankAccount.bankId.value, bankAccount.accountId.value, accountHolder.size > 0)
          }
        val isSuccessful = true
        val bankAccountsWithoutAnHolder = accountHolderInfo.filter(_._3 == false)

        val ownerViewInfo =
          for {
            (bankId, accountId, _) <- bankAccountsWithoutAnHolder
            ownerViewAccess = AccountAccess.findAll(
              By(AccountAccess.bank_id, bankId),
              By(AccountAccess.account_id, accountId),
              ByList(AccountAccess.view_id, List(Constant.SYSTEM_OWNER_VIEW_ID, "_owner"))
            )
          } yield {
            (bankId, accountId, ownerViewAccess.size > 0)
          }
        val bankAccountsWithoutAnOwnerView = ownerViewInfo.filter(_._3 == false)
        
        val bankAccountsWithoutAnHolderText = bankAccountsWithoutAnHolder.map(holder => (holder._1, holder._2)).mkString(", ")
        val bankAccountsWithoutAnOwnerViewText = bankAccountsWithoutAnOwnerView.map(accountAccess => (accountAccess._1, accountAccess._2)).mkString(", ")
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Bank accounts without an account holder: ${bankAccountsWithoutAnHolderText}
             |Bank accounts without an account holder and owner view as well: ${bankAccountsWithoutAnOwnerViewText}
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
        
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""MapperAccountHolders table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
