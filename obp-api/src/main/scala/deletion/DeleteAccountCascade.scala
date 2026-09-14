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

import code.accountattribute.MappedAccountAttribute
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.bankconnectors.Connector
import code.cards.MappedPhysicalCard
import code.entitlement.MappedEntitlement
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount, MappedBankAccountData}
import code.views.system.{AccountAccess, ViewDefinition}
import code.webhook.MappedAccountWebhook
import com.openbankproject.commons.model.{AccountId, BankId}
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.mapper.{By, ByList}
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
    MappedBankAccount.bulkDelete_!!(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.theAccountId, accountId.value)
    )
  }
  private def deleteEntitlements(bankId: BankId, accountId: AccountId): Boolean = {
    val userIds = AccountAccess.findAll(By(AccountAccess.account_id, accountId.value)).map(_.user_fk.foreign.map(_.userId).getOrElse(""))
    MappedEntitlement.bulkDelete_!!(
      By(MappedEntitlement.mBankId, bankId.value),
      ByList(MappedEntitlement.mUserId, userIds)
    )
  }
  
  private def deleteCards(accountId: AccountId): Boolean = {
    MappedBankAccount.findAll(
      By(MappedBankAccount.theAccountId, accountId.value)
    ) map (
      account =>
        MappedPhysicalCard.bulkDelete_!!(
          By(MappedPhysicalCard.mAccount, account.id.get)
        )
    )
  }.forall(_ == true)
  
  private def deleteBankAccountData(bankId: BankId, accountId: AccountId): Boolean = {
    MappedBankAccountData.bulkDelete_!!(
      By(MappedBankAccountData.bankId, bankId.value),
      By(MappedBankAccountData.accountId, accountId.value)
    )
  }  
  private def deleteAccountWebhooks(bankId: BankId, accountId: AccountId): Boolean = {
    MappedAccountWebhook.bulkDelete_!!(
      By(MappedAccountWebhook.mBankId, bankId.value),
      By(MappedAccountWebhook.mAccountId, accountId.value)
    )
  }   
  private def deleteAccountAttributes(bankId: BankId, accountId: AccountId): Boolean = {
    MappedAccountAttribute.bulkDelete_!!(
      By(MappedAccountAttribute.mBankIdId, bankId.value),
      By(MappedAccountAttribute.mAccountId, accountId.value)
    )
  }  
  private def deleteCustomViews(bankId: BankId, accountId: AccountId): Boolean = {
    ViewDefinition.bulkDelete_!!(
      By(ViewDefinition.bank_id, bankId.value),
      By(ViewDefinition.account_id, accountId.value)
    )
  }  
  private def deleteAccountAccess(bankId: BankId, accountId: AccountId): Boolean = {
    AccountAccess.bulkDelete_!!(
      By(AccountAccess.bank_id, bankId.value),
      By(AccountAccess.account_id, accountId.value)
    )
  }
  private def deleteAccountRoutings(bankId: BankId, accountId: AccountId): Boolean = {
    BankAccountRouting.bulkDelete_!!(
      By(BankAccountRouting.BankId, bankId.value),
      By(BankAccountRouting.AccountId, accountId.value)
    )
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
