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

package code.accountholders

import code.api.util.APIUtil
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountHolders extends SimpleInjector {

  val accountHolders = new Inject(() => buildOne) {}

  def buildOne: AccountHolders = MapperAccountHolders

}

trait AccountHolders {

  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User]
  def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId]

  /**
   * if source == None, we return all accountHeld for the user.
   * if set source == Some(null) or Some(""), we only return the OBP created (source == null) accountHeld for the user.
   * if set source == Some("UserAuthContext"), we only return the user auth context created accountHeld for the user.
   * @param source
   * @return
   */
  def getAccountsHeldByUser(user: User, source: Option[String] = None): Set[BankIdAccountId]
  /** Links the account to its holder. The holder is the on-behalf-of user of `user`
   *  (UserReference.AccountHoldersUser): a consent user never holds an account, the user its
   *  consent names does. Same user for an original user. */
  def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId, source: Option[String] = None): Box[MapperAccountHolders] //There is no AccountHolder trait, database structure different with view
  def deleteAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[Boolean] 
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}


