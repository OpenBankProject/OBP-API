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
import net.liftweb.util.SimpleInjector

/**
 * Account access granted to a Consumer: the Consumer, acting as itself, may use this view of this account.
 *
 * It sits next to account access granted to a User (code.views.system.AccountAccess) and is kept in its own
 * table. AccountAccess.consumer_id means something else: that a User's grant may only be used through that
 * Consumer. The grant is consulted only for an application acting as itself (its own client-credentials
 * User), and only on endpoints whose auth mode admits an application (see APIUtil.hasConsumerAccountAccess).
 */
object ConsumerAccountAccesses extends SimpleInjector {
  val consumerAccountAccessProvider = new Inject(() => buildOne) {}
  def buildOne: ConsumerAccountAccessProvider = ConsumerAccountAccessDbProvider
}

trait ConsumerAccountAccessTrait {
  def consumerAccountAccessId: String
  /** The Consumer's consumer_id (its public id, not its key). */
  def consumerId: String
  def bankId: String
  def accountId: String
  def viewId: String
  def grantedByUserId: String
  def createdAt: Date
}

trait ConsumerAccountAccessProvider {
  def grant(consumerId: String, bankId: BankId, accountId: AccountId, viewId: ViewId, grantedByUserId: String): Box[ConsumerAccountAccessTrait]
  def get(bankId: BankId, accountId: AccountId, consumerAccountAccessId: String): Box[ConsumerAccountAccessTrait]
  def find(consumerId: String, bankId: BankId, accountId: AccountId, viewId: ViewId): Box[ConsumerAccountAccessTrait]
  def getByAccount(bankId: BankId, accountId: AccountId): Box[List[ConsumerAccountAccessTrait]]
  def revoke(consumerAccountAccessId: String): Box[Boolean]
}
