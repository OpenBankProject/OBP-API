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
import code.accountholders.MapperAccountHolders
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.{ConsentJWT, CustomJsonFormats, JwtUtil}
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.bankconnectors.Connector
import code.cardattribute.MappedCardAttribute
import code.cards.MappedPhysicalCard
import code.consent.Consents
import code.counterpartyattribute.CounterpartyAttribute
import code.counterpartylimit.CounterpartyLimit
import code.entitlement.MappedEntitlement
import code.metadata.counterparties.{MappedCounterparty, MappedCounterpartyBespoke, MappedCounterpartyMetadata, MappedCounterpartyWhereTag}
import code.model.dataAccess.{BankAccountRouting, DoubleEntryBookTransaction, MappedBankAccount, MappedBankAccountData}
import code.transactionChallenge.MappedExpectedChallengeAnswer
import code.transactionrequests.MappedTransactionRequest
import code.views.system.{AccountAccess, ViewDefinition}
import code.webhook.MappedAccountWebhook
import com.openbankproject.commons.model.{AccountId, BankId}
import com.openbankproject.commons.util.JsonAliases
import deletion.DeletionUtil.databaseAtomicTask
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.db.DB
import net.liftweb.mapper.{By, ByList, NotBy}
import net.liftweb.util.DefaultConnectionIdentifier
import net.liftweb.util.Helpers.tryo
import org.json4s.Formats

import scala.collection.immutable.List

object DeleteAccountCascade {

  def delete(bankId: BankId, accountId: AccountId): Boolean = {
    // The tasks run in list order: the challenges are found through the account's transaction
    // requests, so they must go before deleteTransactionRequests and deleteTransactions remove them.
    val doneTasks =
      deleteConsentChallenges(bankId, accountId) ::
        deleteTransactionRequestChallenges(bankId, accountId) ::
        deleteTransactionRequests(bankId, accountId) ::
        deleteDoubleEntryBookTransactions(bankId, accountId) ::
        deleteTransactions(bankId, accountId) ::
        deleteEntitlements(bankId, accountId) ::
        deleteAccountAccess(bankId, accountId) ::
        deleteAccountHolders(bankId, accountId) ::
        deleteCustomViews(bankId, accountId) ::
        deleteAccountAttributes(bankId, accountId) ::
        deleteAccountWebhooks(bankId, accountId) ::
        deleteBankAccountData(bankId, accountId) ::
        deleteCards(bankId, accountId) ::
        deleteAccountRoutings(bankId, accountId) ::
        deleteCounterparties(bankId, accountId) ::
        deleteCounterpartyMetadata(bankId, accountId) ::
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
    val userIds = AccountAccess.findAll(
      By(AccountAccess.bank_id, bankId.value),
      By(AccountAccess.account_id, accountId.value)
    ).map(_.user_fk.foreign.map(_.userId).getOrElse(""))
    MappedEntitlement.bulkDelete_!!(
      By(MappedEntitlement.mBankId, bankId.value),
      ByList(MappedEntitlement.mUserId, userIds)
    )
  }
  
  private def deleteCards(bankId: BankId, accountId: AccountId): Boolean = {
    MappedBankAccount.findAll(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.theAccountId, accountId.value)
    ) map (
      account => {
        val cardIds = MappedPhysicalCard.findAll(By(MappedPhysicalCard.mAccount, account.id.get)).map(_.mCardId.get)
        MappedCardAttribute.deleteForCards(cardIds) &&
          MappedPhysicalCard.bulkDelete_!!(
            By(MappedPhysicalCard.mAccount, account.id.get)
          )
      }
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
  // A holder row that outlives its account makes the next refresh of the holder's accounts
  // (AuthUser.refreshUser, run e.g. when the holder creates another account) grant the system views on it again.
  private def deleteAccountHolders(bankId: BankId, accountId: AccountId): Boolean = {
    MapperAccountHolders.bulkDelete_!!(
      By(MapperAccountHolders.accountBankPermalink, bankId.value),
      By(MapperAccountHolders.accountPermalink, accountId.value)
    )
  }
  private def deleteAccountRoutings(bankId: BankId, accountId: AccountId): Boolean = {
    BankAccountRouting.bulkDelete_!!(
      By(BankAccountRouting.BankId, bankId.value),
      By(BankAccountRouting.AccountId, accountId.value)
    )
  }

  // The counterparties this account created, with the rows that hang off each of them.
  // Counterparties of other accounts that point at this account are theirs and are kept.
  private def deleteCounterparties(bankId: BankId, accountId: AccountId): Boolean = {
    val counterparties = MappedCounterparty.findAll(
      By(MappedCounterparty.mThisBankId, bankId.value),
      By(MappedCounterparty.mThisAccountId, accountId.value)
    )
    val primaryKeys = counterparties.map(_.id.get)
    val counterpartyIds = counterparties.map(_.counterpartyId)
    val bespokesDeleted = primaryKeys.isEmpty ||
      MappedCounterpartyBespoke.bulkDelete_!!(ByList(MappedCounterpartyBespoke.mCounterparty, primaryKeys))
    val attributesDeleted = counterpartyIds.isEmpty ||
      CounterpartyAttribute.bulkDelete_!!(ByList(CounterpartyAttribute.CounterpartyId_, counterpartyIds))
    val limitsDeleted = CounterpartyLimit.bulkDelete_!!(
      By(CounterpartyLimit.BankId, bankId.value),
      By(CounterpartyLimit.AccountId, accountId.value)
    )
    val counterpartiesDeleted = MappedCounterparty.bulkDelete_!!(
      By(MappedCounterparty.mThisBankId, bankId.value),
      By(MappedCounterparty.mThisAccountId, accountId.value)
    )
    bespokesDeleted && attributesDeleted && limitsDeleted && counterpartiesDeleted
  }

  // Metadata covers both the counterparties created on this account and the ones derived from its transactions.
  private def deleteCounterpartyMetadata(bankId: BankId, accountId: AccountId): Boolean = {
    val metadata = MappedCounterpartyMetadata.findAll(
      By(MappedCounterpartyMetadata.thisBankId, bankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, accountId.value)
    )
    val locationIds = metadata.flatMap(m => List(m.physicalLocation.get, m.corporateLocation.get)).filter(_ > 0)
    val locationsDeleted = locationIds.isEmpty ||
      MappedCounterpartyWhereTag.bulkDelete_!!(ByList(MappedCounterpartyWhereTag.id, locationIds))
    val metadataDeleted = MappedCounterpartyMetadata.bulkDelete_!!(
      By(MappedCounterpartyMetadata.thisBankId, bankId.value),
      By(MappedCounterpartyMetadata.thisAccountId, accountId.value)
    )
    locationsDeleted && metadataDeleted
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

  // A consent names its accounts only in the views of its JWT, so the consents that still have
  // challenges are read and those whose JWT names this account lose them. The consents are found
  // through their challenges rather than through users: a Berlin Group consent awaiting its first
  // challenge answer has no PSU bound to it yet. A consent bound to other accounts as well loses its
  // challenges too: they record the authorisation of the consent as a whole.
  private def deleteConsentChallenges(bankId: BankId, accountId: AccountId): Boolean = {
    implicit val formats: Formats = CustomJsonFormats.formats
    val consentIds = MappedExpectedChallengeAnswer.findAll(NotBy(MappedExpectedChallengeAnswer.ConsentId, ""))
      .map(_.ConsentId.get).distinct
      .flatMap(Consents.consentProvider.vend.getConsentByConsentId(_).toList)
      .filter { consent =>
        JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken)
          .flatMap(payload => tryo(JsonAliases.parse(payload).extract[ConsentJWT]))
          .exists(_.views.exists(view => view.bank_id == bankId.value && view.account_id == accountId.value))
      }
      .map(_.consentId)
    consentIds.isEmpty || MappedExpectedChallengeAnswer.bulkDelete_!!(
      ByList(MappedExpectedChallengeAnswer.ConsentId, consentIds)
    )
  }
  private def deleteTransactionRequestChallenges(bankId: BankId, accountId: AccountId): Boolean = {
    val transactionRequestIds = MappedTransactionRequest.findAll(
      By(MappedTransactionRequest.mFrom_BankId, bankId.value),
      By(MappedTransactionRequest.mFrom_AccountId, accountId.value)
    ).map(_.mTransactionRequestId.get)
    MappedExpectedChallengeAnswer.bulkDelete_!!(
      ByList(MappedExpectedChallengeAnswer.TransactionRequestId, transactionRequestIds)
    )
  }
  private def deleteTransactionRequests(bankId: BankId, accountId: AccountId): Boolean = {
    MappedTransactionRequest.bulkDelete_!!(
      By(MappedTransactionRequest.mFrom_BankId, bankId.value),
      By(MappedTransactionRequest.mFrom_AccountId, accountId.value)
    )
  }
  private def deleteDoubleEntryBookTransactions(bankId: BankId, accountId: AccountId): Boolean = {
    val debitSide = DoubleEntryBookTransaction.bulkDelete_!!(
      By(DoubleEntryBookTransaction.DebitTransactionBankId, bankId.value),
      By(DoubleEntryBookTransaction.DebitTransactionAccountId, accountId.value)
    )
    val creditSide = DoubleEntryBookTransaction.bulkDelete_!!(
      By(DoubleEntryBookTransaction.CreditTransactionBankId, bankId.value),
      By(DoubleEntryBookTransaction.CreditTransactionAccountId, accountId.value)
    )
    debitSide && creditSide
  }
}
