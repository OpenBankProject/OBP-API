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

import cats.data.NonEmptyList
import code.accountattribute.DoobieAccountAttributeProvider
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import code.api.util.{ConsentJWT, CustomJsonFormats, DoobieUtil, JwtUtil}
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.bankconnectors.{Connector, DoobieBankAccountRoutingQueries}
import code.cardattribute.DoobieCardAttributeProvider
import code.cards.MappedPhysicalCard
import code.consent.Consents
import code.entitlement.MappedEntitlement
import code.model.dataAccess.MappedBankAccount
import code.views.system.{AccountAccess, ViewDefinition}
import code.webhook.MappedAccountWebhook
import com.openbankproject.commons.model.{AccountId, BankId}
import com.openbankproject.commons.util.JsonAliases
import deletion.DeletionUtil.databaseAtomicTask
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.json4s.Formats

import scala.collection.immutable.List
import org.json4s.jvalue2extractable

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
        // Throws, so databaseAtomicTask restores what delete has removed.
        fullBoxOrException(Empty ~> APIFailureNewStyle(CouldNotDeleteCascade, 400))
    }
  }

  private def run(statement: Fragment): Boolean = {
    DoobieUtil.runUpdate(statement.update.run)
    true
  }

  /** `column IN (...)`; callers skip the statement when the list is empty, as Mapper's ByList did. */
  private def in[A: doobie.util.Put](column: Fragment, values: List[A]): Fragment =
    Fragments.in(column, NonEmptyList.fromListUnsafe(values.distinct))

  private def deleteAccount(bankId: BankId, accountId: AccountId): Boolean = {
    MappedBankAccount.delete(bankId.value, accountId.value)
  }
  private def deleteEntitlements(bankId: BankId, accountId: AccountId): Boolean = {
    // user_fk holds RESOURCEUSER's numeric key; resolve each to the public user id as before, with
    // an unresolvable key contributing "" exactly as the Lift foreign key did. Only this bank's
    // account of that id is meant: another bank may hold an account with the same id.
    val userIds = AccountAccess.findAllByAccountId(accountId.value)
      .filter(_.bankId == bankId.value)
      .map(a => code.model.dataAccess.ResourceUser.findByPrimaryKey(a.userPrimaryKey)
        .map(_.userId).getOrElse(""))
    MappedEntitlement.deleteByBankIdAndUserIds(bankId.value, userIds)
  }
  
  private def deleteCards(bankId: BankId, accountId: AccountId): Boolean = {
    MappedBankAccount.findAllByAccountId(accountId.value).filter(_.bankId == bankId) map (
      account => {
        val cardIds = DoobieUtil.runQuery(
          sql"SELECT mcardid FROM mappedphysicalcard WHERE maccount = ${account.accountPrimaryKey}"
            .query[Option[String]].to[List]).flatten
        DoobieCardAttributeProvider.deleteForCards(bankId.value, cardIds) &&
          MappedPhysicalCard.deleteByAccountKey(account.accountPrimaryKey)
      }
    )
  }.forall(_ == true)
  
  private def deleteBankAccountData(bankId: BankId, accountId: AccountId): Boolean =
    run(sql"DELETE FROM mappedbankaccountdata WHERE bankid = ${bankId.value} AND accountid = ${accountId.value}")
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
  // A holder row that outlives its account makes the next refresh of the holder's accounts
  // (AuthUser.refreshUser, run e.g. when the holder creates another account) grant the system views on it again.
  private def deleteAccountHolders(bankId: BankId, accountId: AccountId): Boolean =
    run(sql"""DELETE FROM mapperaccountholders
              WHERE accountbankpermalink = ${bankId.value} AND accountpermalink = ${accountId.value}""")
  private def deleteAccountRoutings(bankId: BankId, accountId: AccountId): Boolean = {
    DoobieBankAccountRoutingQueries.deleteByBankAccount(bankId, accountId)
    true
  }

  // The counterparties this account created, with the rows that hang off each of them.
  // Counterparties of other accounts that point at this account are theirs and are kept.
  private def deleteCounterparties(bankId: BankId, accountId: AccountId): Boolean = {
    val counterparties = DoobieUtil.runQuery(
      sql"""SELECT id, mcounterpartyid FROM mappedcounterparty
            WHERE mthisbankid = ${bankId.value} AND mthisaccountid = ${accountId.value}"""
        .query[(Long, Option[String])].to[List])
    val primaryKeys = counterparties.map(_._1)
    val counterpartyIds = counterparties.flatMap(_._2)
    val bespokesDeleted = primaryKeys.isEmpty ||
      run(fr"DELETE FROM mappedcounterpartybespoke WHERE" ++ in(fr"mcounterparty", primaryKeys))
    val attributesDeleted = counterpartyIds.isEmpty ||
      run(fr"DELETE FROM counterpartyattribute WHERE" ++ in(fr"counterpartyid", counterpartyIds))
    val limitsDeleted =
      run(sql"DELETE FROM counterpartylimit WHERE bankid = ${bankId.value} AND accountid = ${accountId.value}")
    val counterpartiesDeleted =
      run(sql"DELETE FROM mappedcounterparty WHERE mthisbankid = ${bankId.value} AND mthisaccountid = ${accountId.value}")
    bespokesDeleted && attributesDeleted && limitsDeleted && counterpartiesDeleted
  }

  // Metadata covers both the counterparties created on this account and the ones derived from its transactions.
  private def deleteCounterpartyMetadata(bankId: BankId, accountId: AccountId): Boolean = {
    val locations = DoobieUtil.runQuery(
      sql"""SELECT physicallocation, corporatelocation FROM mappedcounterpartymetadata
            WHERE thisbankid = ${bankId.value} AND thisaccountid = ${accountId.value}"""
        .query[(Option[Long], Option[Long])].to[List])
    val locationIds = locations.flatMap { case (physical, corporate) => physical.toList ++ corporate.toList }.filter(_ > 0)
    val locationsDeleted = locationIds.isEmpty ||
      run(fr"DELETE FROM mappedcounterpartywheretag WHERE" ++ in(fr"id", locationIds))
    val metadataDeleted =
      run(sql"DELETE FROM mappedcounterpartymetadata WHERE thisbankid = ${bankId.value} AND thisaccountid = ${accountId.value}")
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
    val consentIds = DoobieUtil.runQuery(
      sql"SELECT DISTINCT consentid FROM expectedchallengeanswer WHERE consentid IS NOT NULL AND consentid <> ''"
        .query[String].to[List])
      .flatMap(Consents.consentProvider.vend.getConsentByConsentId(_).toList)
      .filter { consent =>
        JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken)
          .flatMap(payload => tryo(JsonAliases.parse(payload).extract[ConsentJWT]))
          .exists(_.views.exists(view => view.bank_id == bankId.value && view.account_id == accountId.value))
      }
      .map(_.consentId)
    consentIds.isEmpty || run(fr"DELETE FROM expectedchallengeanswer WHERE" ++ in(fr"consentid", consentIds))
  }
  private def deleteTransactionRequestChallenges(bankId: BankId, accountId: AccountId): Boolean = {
    val transactionRequestIds = DoobieUtil.runQuery(
      sql"""SELECT mtransactionrequestid FROM mappedtransactionrequest
            WHERE mfrom_bankid = ${bankId.value} AND mfrom_accountid = ${accountId.value}"""
        .query[Option[String]].to[List]).flatten
    transactionRequestIds.isEmpty ||
      run(fr"DELETE FROM expectedchallengeanswer WHERE" ++ in(fr"transactionrequestid", transactionRequestIds))
  }
  private def deleteTransactionRequests(bankId: BankId, accountId: AccountId): Boolean =
    run(sql"""DELETE FROM mappedtransactionrequest
              WHERE mfrom_bankid = ${bankId.value} AND mfrom_accountid = ${accountId.value}""")
  private def deleteDoubleEntryBookTransactions(bankId: BankId, accountId: AccountId): Boolean = {
    val debitSide = run(sql"""DELETE FROM doubleentrybooktransaction
              WHERE debittransactionbankid = ${bankId.value} AND debittransactionaccountid = ${accountId.value}""")
    val creditSide = run(sql"""DELETE FROM doubleentrybooktransaction
              WHERE credittransactionbankid = ${bankId.value} AND credittransactionaccountid = ${accountId.value}""")
    debitSide && creditSide
  }
}
