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

package code.accountcreation

import java.util.Date

import code.consent.Consents
import code.users.{UserReference, Users}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

/**
 * Who created a bank account, and for whom.
 *
 * The account itself is created through the Connector, which may keep it outside OBP's database, so
 * the creator cannot be a column of the account. This table holds one row per account created
 * through v7.0.0 Create Account. It records both ids, like a Counterparty or a Dynamic Entity
 * webhook: the caller, which is an agent's own id when an agent made the call, and the person the
 * call was made for. See UserReference.AccountCreation_CreatedByUserId. When an agent made the call,
 * it also records the consent_reference_id of the Consent it acted under, the id the metrics record
 * for the same call.
 */
object AccountCreations extends SimpleInjector {
  val accountCreationProvider = new Inject(() => buildOne) {}
  def buildOne: AccountCreationProvider = AccountCreationDbProvider
}

trait AccountCreationTrait {
  def bankId: String
  def accountId: String
  def createdByUserId: String
  def createdByOnBehalfOfUserId: String
  /** The Consent the agent acted under; None when the caller acted for themselves. */
  def consentReferenceId: Option[String]
  def createdAt: Date
}

trait AccountCreationProvider {
  /** Records that `createdByUserId`, the authenticated caller, created the account. */
  def record(bankId: String, accountId: String, createdByUserId: String): Box[AccountCreationTrait]
  def get(bankId: String, accountId: String): Box[AccountCreationTrait]
  /** The records of those of `accountIds` at `bankId` that have one, in one query. */
  def getForAccounts(bankId: String, accountIds: List[String]): List[AccountCreationTrait]
}

object AccountCreationDbProvider extends AccountCreationProvider {

  override def record(bankId: String, accountId: String, createdByUserId: String): Box[AccountCreationTrait] =
    for {
      attribution <- Users.users.vend.attributionOf(createdByUserId, UserReference.AccountCreation_CreatedByUserId)
      consentReferenceId = attribution.consentId
        .flatMap(id => Consents.consentProvider.vend.getConsentByConsentId(id).toOption)
        .map(_.consentReferenceId)
      row <- tryo {
        AccountCreation.create
          .BankId(bankId)
          .AccountId(accountId)
          .CreatedByUserId(attribution.userId)
          .CreatedByOnBehalfOfUserId(attribution.onBehalfOfUserId)
          .ConsentReferenceId(consentReferenceId.orNull)
          .saveMe()
      }
    } yield row

  override def get(bankId: String, accountId: String): Box[AccountCreationTrait] =
    AccountCreation.find(By(AccountCreation.BankId, bankId), By(AccountCreation.AccountId, accountId))

  override def getForAccounts(bankId: String, accountIds: List[String]): List[AccountCreationTrait] =
    if (accountIds.isEmpty) Nil
    else AccountCreation.findAll(By(AccountCreation.BankId, bankId), ByList(AccountCreation.AccountId, accountIds))
}

class AccountCreation extends AccountCreationTrait with LongKeyedMapper[AccountCreation] with IdPK {
  def getSingleton = AccountCreation

  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
  }
  object AccountId extends MappedString(this, 255) {
    override def dbColumnName = "account_id"
  }
  object CreatedByUserId extends MappedString(this, 255) {
    override def dbColumnName = "created_by_user_id"
  }
  object CreatedByOnBehalfOfUserId extends MappedString(this, 255) {
    override def dbColumnName = "created_by_on_behalf_of_user_id"
  }
  object ConsentReferenceId extends MappedString(this, 255) {
    override def dbColumnName = "consent_reference_id"
  }
  object CreatedAt extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new Date()
  }

  override def bankId: String = BankId.get
  override def accountId: String = AccountId.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdByOnBehalfOfUserId: String = CreatedByOnBehalfOfUserId.get
  override def consentReferenceId: Option[String] = Option(ConsentReferenceId.get).filter(_.nonEmpty)
  override def createdAt: Date = CreatedAt.get
}

object AccountCreation extends AccountCreation with LongKeyedMetaMapper[AccountCreation] {
  override def dbTableName = "account_creation"
  override def dbIndexes = UniqueIndex(BankId, AccountId) :: super.dbIndexes
}
