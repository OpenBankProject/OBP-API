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

package code.organisation

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Organisations extends SimpleInjector {
  val organisation = new Inject(() => buildOne) {}

  def buildOne: OrganisationProvider = MappedOrganisationProvider
}

/**
 * Organisations live in a space: a bank, or SYS (Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) for the
 * system space. The organisation_id is unique across all spaces, so a reference to an Organisation needs
 * only its id.
 */
trait OrganisationProvider {
  def createOrganisation(
    bankId: String,
    organisationId: String,
    name: String,
    website: Option[String],
    logoUrl: Option[String],
    organisationType: Option[String],
    status: String,
    visibility: String,
    createdByUserId: String
  ): Box[OrganisationTrait]

  /** The Organisation with this id, in whichever space it lives. */
  def getOrganisation(organisationId: String): Box[OrganisationTrait]

  /** The Organisation with this id, only if it lives in the space `bankId`. */
  def getOrganisation(bankId: String, organisationId: String): Box[OrganisationTrait]

  def getOrganisations(bankId: String): Future[Box[List[OrganisationTrait]]]

  def updateOrganisation(
    bankId: String,
    organisationId: String,
    name: Option[String],
    website: Option[String],
    logoUrl: Option[String],
    organisationType: Option[String],
    status: Option[String],
    visibility: Option[String]
  ): Box[OrganisationTrait]

  def deleteOrganisation(bankId: String, organisationId: String): Box[Boolean]
}

trait OrganisationTrait {
  def organisationId: String
  /** A bank id, or SYS for the system space. */
  def bankId: String
  def name: String
  def website: Option[String]
  def logoUrl: Option[String]
  /** Free text, e.g. startup, bank, university, regulator, partner. */
  def organisationType: Option[String]
  def status: String
  def visibility: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}
