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

package code.setup

import code.bankconnectors.Connector
import net.liftweb.util.Helpers._
import code.api.util.ErrorMessages._
import com.openbankproject.commons.model.{AccountId, User}

trait PrivateUser2AccountsAndSetUpWithTestData {

  self: ServerSetupWithTestData with DefaultUsers =>

  /**
   * Adds some private accounts for authuser2 to the DB so that not all accounts in the DB are public
   * (which is at the time of writing, the default created in ServerSetup)
   *
   * Also adds some public accounts to which user1 does not have owner access
   *
   * Also adds some private accounts for user1 that are not public
   */
  def accountTestsSpecificDBSetup(): Unit = {

    val banks =  Connector.connector.vend.getBanksLegacy(None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox)

    def generateAccounts(owner: User) = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
        createAccountRelevantResource(Some(owner), bank.bankId, AccountId(randomString(10)), randomString(10))
      }
    })

    //fake bank accounts

    //private accounts for authuser1 (visible to authuser1)
    generateAccounts(resourceUser1)
    //private accounts for authuser2 (not visible to authuser1)
    generateAccounts(resourceUser2)

    //public accounts owned by authuser2 (visible to authuser1)
    //create accounts
    val accounts = generateAccounts(resourceUser2)
    //add public views
    accounts.foreach(acc => createPublicView(acc.bankId, acc.accountId))
  }

}
