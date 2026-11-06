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

package code.accountaccessrequest

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object AccountAccessRequestTrait extends SimpleInjector {
  val accountAccessRequest = new Inject(() => buildOne) {}

  def buildOne: AccountAccessRequestProvider = MappedAccountAccessRequestProvider
}

trait AccountAccessRequestProvider {
  def createAccountAccessRequest(
    bankId: String,
    accountId: String,
    viewId: String,
    isSystemView: Boolean,
    requestorUserId: String,
    targetUserId: String,
    businessJustification: String
  ): Box[AccountAccessRequestTrait]

  def getById(accountAccessRequestId: String): Box[AccountAccessRequestTrait]

  def getByAccount(bankId: String, accountId: String): Box[List[AccountAccessRequestTrait]]

  def getByAccountAndStatus(bankId: String, accountId: String, status: String): Box[List[AccountAccessRequestTrait]]

  def getByRequestorUserId(requestorUserId: String): Box[List[AccountAccessRequestTrait]]

  def getByUserAccountView(
    targetUserId: String,
    bankId: String,
    accountId: String,
    viewId: String
  ): Box[AccountAccessRequestTrait]

  def updateStatus(
    accountAccessRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[AccountAccessRequestTrait]
}

trait AccountAccessRequestTrait {
  def accountAccessRequestId: String
  def bankId: String
  def accountId: String
  def viewId: String
  def isSystemView: Boolean
  def requestorUserId: String
  def targetUserId: String
  def businessJustification: String
  def status: String
  def checkerUserId: String
  def checkerComment: String
  def created: Date
  def updated: Date
}
