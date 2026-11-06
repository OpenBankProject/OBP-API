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

package deletion

import code.accountattribute.DoobieAccountAttributeProvider
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.bankconnectors.{Connector, DoobieBankAccountRoutingQueries}
import code.cards.MappedPhysicalCard
import code.entitlement.MappedEntitlement
import code.api.util.DoobieUtil
import code.model.dataAccess.MappedBankAccount
import code.views.system.{AccountAccess, ViewDefinition}
import code.webhook.MappedAccountWebhook
import com.openbankproject.commons.model.{AccountId, BankId}
import deletion.DeletionUtil.databaseAtomicTask
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

import scala.collection.immutable.List

object DeleteAccountCascade {

  def delete(bankId: BankId, accountId: AccountId): Boolean = {
    val doneTasks =
      deleteTransactions(bankId, accountId) ::
        deleteEntitlements(bankId, accountId) ::
        deleteAccountAccess(bankId, accountId) ::
        deleteCustomViews(bankId, accountId) ::
        deleteAccountAttributes(bankId, accountId) ::
        deleteAccountWebhooks(bankId, accountId) ::
        deleteBankAccountData(bankId, accountId) ::
        deleteCards(accountId) ::
        deleteAccountRoutings(bankId, accountId) ::
        deleteAccount(bankId, accountId) ::
        Nil
    doneTasks.forall(_ == true)
  }
  
  def atomicDelete(bankId: BankId, accountId: AccountId): Box[Boolean] = databaseAtomicTask {
    delete(bankId, accountId) match {
      case true =>
        Full(true)
      case false =>
        DB.rollback(DefaultConnectionIdentifier)
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }

  private def deleteAccount(bankId: BankId, accountId: AccountId): Boolean = {
    MappedBankAccount.delete(bankId.value, accountId.value
    )
  }
  private def deleteEntitlements(bankId: BankId, accountId: AccountId): Boolean = {
    // user_fk holds RESOURCEUSER's numeric key; resolve each to the public user id as before, with
    // an unresolvable key contributing "" exactly as the Lift foreign key did.
    val userIds = AccountAccess.findAllByAccountId(accountId.value)
      .map(a => code.model.dataAccess.ResourceUser.findByPrimaryKey(a.userPrimaryKey)
        .map(_.userId).getOrElse(""))
    MappedEntitlement.deleteByBankIdAndUserIds(bankId.value, userIds)
  }
  
  private def deleteCards(accountId: AccountId): Boolean = {
    MappedBankAccount.findAllByAccountId(accountId.value
    ) map (
      account =>
        MappedPhysicalCard.deleteByAccountKey(account.accountPrimaryKey)
    )
  }.forall(_ == true)
  
  private def deleteBankAccountData(bankId: BankId, accountId: AccountId): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedbankaccountdata WHERE bankid = ${bankId.value} AND accountid = ${accountId.value}"
        .update.run)
    true
  }  
  private def deleteAccountWebhooks(bankId: BankId, accountId: AccountId): Boolean = {
    MappedAccountWebhook.deleteByBankAccount(bankId.value, accountId.value)
  }   
  private def deleteAccountAttributes(bankId: BankId, accountId: AccountId): Boolean = {
    DoobieAccountAttributeProvider.deleteAccountAttributesByBankAndAccount(bankId.value, accountId.value)
  }
  private def deleteCustomViews(bankId: BankId, accountId: AccountId): Boolean = {
    ViewDefinition.deleteByBankAccount(bankId.value, accountId.value)
  }  
  private def deleteAccountAccess(bankId: BankId, accountId: AccountId): Boolean = {
    AccountAccess.deleteByBankIdAccountId(bankId, accountId)
  }
  private def deleteAccountRoutings(bankId: BankId, accountId: AccountId): Boolean = {
    DoobieBankAccountRoutingQueries.deleteByBankAccount(bankId, accountId)
    true
  }

  private def deleteTransactions(bankId: BankId, accountId: AccountId): Boolean = {
    val deletedTransactions: Box[List[Boolean]] =
      for(
        (transactions, _) <- Connector.connector.vend.getTransactionsLegacy(bankId, accountId, None, Nil)
      ) yield {
        transactions.map {
          t =>
            DeleteTransactionCascade.delete(bankId, accountId, t.id)
        }
      }
    deletedTransactions.map(_.forall(_ == true)) match {
      case Full(true) =>
        true
      case _ =>
        false
    }
  }
}
