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

package code.payeelookup

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object PayeeLookups extends SimpleInjector {
  val payeeLookup = new Inject(() => buildOne) {}

  def buildOne: PayeeLookupProvider = MappedPayeeLookupProvider
}

trait PayeeLookupProvider {
  def createPayeeLookup(
    lookupId: String,
    identifierType: String,
    identifier: String,
    fspId: Option[String],
    networkProvider: Option[String],
    fullName: String,
    accountCategory: Option[String],
    accountType: Option[String],
    identityType: Option[String],
    identityValue: Option[String],
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String,
    ttlSeconds: Long
  ): Box[PayeeLookupTrait]

  /** Returns the lookup if found and not expired. Expired rows are NOT auto-deleted. */
  def getActivePayeeLookup(lookupId: String): Box[PayeeLookupTrait]
}

trait PayeeLookupTrait {
  def lookupId: String
  def identifierType: String
  def identifier: String
  def fspId: Option[String]
  def networkProvider: Option[String]
  def fullName: String
  def accountCategory: Option[String]
  def accountType: Option[String]
  def identityType: Option[String]
  def identityValue: Option[String]
  def fromBankId: String
  def fromAccountId: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def expiresAt: java.util.Date
  def isExpired: Boolean = expiresAt.getTime <= System.currentTimeMillis()
}
