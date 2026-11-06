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
package code.consumeraccountaccess

import java.util.Date

import com.openbankproject.commons.model.{AccountId, BankId, ViewId}
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object ConsumerAccountAccessDbProvider extends ConsumerAccountAccessProvider {

  override def grant(consumerId: String, bankId: BankId, accountId: AccountId, viewId: ViewId, grantedByUserId: String): Box[ConsumerAccountAccessTrait] =
    tryo {
      ConsumerAccountAccess.create
        .ConsumerAccountAccessId(java.util.UUID.randomUUID().toString)
        .ConsumerId(consumerId)
        .BankId(bankId.value)
        .AccountId(accountId.value)
        .ViewId(viewId.value)
        .GrantedByUserId(grantedByUserId)
        .saveMe()
    }

  override def get(bankId: BankId, accountId: AccountId, consumerAccountAccessId: String): Box[ConsumerAccountAccessTrait] =
    ConsumerAccountAccess.find(
      By(ConsumerAccountAccess.ConsumerAccountAccessId, consumerAccountAccessId),
      By(ConsumerAccountAccess.BankId, bankId.value),
      By(ConsumerAccountAccess.AccountId, accountId.value))

  override def find(consumerId: String, bankId: BankId, accountId: AccountId, viewId: ViewId): Box[ConsumerAccountAccessTrait] =
    ConsumerAccountAccess.find(
      By(ConsumerAccountAccess.ConsumerId, consumerId),
      By(ConsumerAccountAccess.BankId, bankId.value),
      By(ConsumerAccountAccess.AccountId, accountId.value),
      By(ConsumerAccountAccess.ViewId, viewId.value))

  override def getByAccount(bankId: BankId, accountId: AccountId): Box[List[ConsumerAccountAccessTrait]] =
    tryo {
      ConsumerAccountAccess.findAll(
        By(ConsumerAccountAccess.BankId, bankId.value),
        By(ConsumerAccountAccess.AccountId, accountId.value),
        OrderBy(ConsumerAccountAccess.id, Ascending))
    }

  override def revoke(consumerAccountAccessId: String): Box[Boolean] =
    ConsumerAccountAccess.find(By(ConsumerAccountAccess.ConsumerAccountAccessId, consumerAccountAccessId))
      .flatMap(row => tryo(row.delete_!))
}

class ConsumerAccountAccess extends ConsumerAccountAccessTrait with LongKeyedMapper[ConsumerAccountAccess] with IdPK {
  def getSingleton = ConsumerAccountAccess

  object ConsumerAccountAccessId extends MappedString(this, 36) {
    override def dbColumnName = "consumer_account_access_id"
  }
  object ConsumerId extends MappedString(this, 250) {
    override def dbColumnName = "consumer_id"
  }
  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
  }
  object AccountId extends MappedString(this, 255) {
    override def dbColumnName = "account_id"
  }
  object ViewId extends MappedString(this, 255) {
    override def dbColumnName = "view_id"
  }
  object GrantedByUserId extends MappedString(this, 255) {
    override def dbColumnName = "granted_by_user_id"
  }
  object CreationDate extends MappedDateTime(this) {
    override def dbColumnName = "created_at"
    override def defaultValue = new Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def dbColumnName = "updated_at"
    override def defaultValue = new Date()
  }

  override def consumerAccountAccessId: String = ConsumerAccountAccessId.get
  override def consumerId: String = ConsumerId.get
  override def bankId: String = BankId.get
  override def accountId: String = AccountId.get
  override def viewId: String = ViewId.get
  override def grantedByUserId: String = GrantedByUserId.get
  override def createdAt: Date = CreationDate.get
}

object ConsumerAccountAccess extends ConsumerAccountAccess with LongKeyedMetaMapper[ConsumerAccountAccess] {
  override def dbTableName = "consumer_account_access"
  override def dbIndexes =
    UniqueIndex(ConsumerId, BankId, AccountId, ViewId) ::
      UniqueIndex(ConsumerAccountAccessId) ::
      Index(BankId, AccountId) ::
      super.dbIndexes
}
