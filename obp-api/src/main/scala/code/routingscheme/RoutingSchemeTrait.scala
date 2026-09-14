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

package code.routingscheme

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object RoutingSchemes extends SimpleInjector {
  val routingScheme = new Inject(() => buildOne) {}

  def buildOne: RoutingSchemeProvider = MappedRoutingSchemeProvider
}

trait RoutingSchemeProvider {
  def createRoutingScheme(
    scheme: String,
    country: String,
    category: String,
    addressPattern: String,
    secondaryAddressPattern: Option[String],
    exampleAddress: String,
    description: String,
    downstreamRails: List[String],
    status: String,
    createdByUserId: String
  ): Box[RoutingSchemeTrait]

  def getRoutingScheme(scheme: String): Box[RoutingSchemeTrait]

  def getRoutingSchemes(
    country: Option[String],
    category: Option[String],
    status: Option[String],
    rail: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[RoutingSchemeTrait], Int)]]

  def updateRoutingScheme(
    scheme: String,
    addressPattern: Option[String],
    secondaryAddressPattern: Option[String],
    exampleAddress: Option[String],
    description: Option[String],
    downstreamRails: Option[List[String]],
    status: Option[String]
  ): Box[RoutingSchemeTrait]

  def deleteRoutingScheme(scheme: String): Box[Boolean]

  // ── Per-bank support ───────────────────────────────────────────────────────

  def getBankSupportedRoutingSchemes(bankId: String): Future[Box[List[BankSupportedRoutingSchemeTrait]]]

  def putBankSupportedRoutingScheme(
    bankId: String,
    scheme: String,
    enabled: Boolean,
    bankNotes: Option[String]
  ): Box[BankSupportedRoutingSchemeTrait]
}

trait RoutingSchemeTrait {
  def scheme: String
  def country: String
  def category: String
  def addressPattern: String
  def secondaryAddressPattern: Option[String]
  def exampleAddress: String
  def description: String
  def downstreamRails: List[String]
  def status: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}

trait BankSupportedRoutingSchemeTrait {
  def bankId: String
  def scheme: String
  def enabled: Boolean
  def bankNotes: Option[String]
}
