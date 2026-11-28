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

import code.model.Consumer
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
 * Who may act for a Consumer without a Role. A personal Consumer (no Organisation) is owned by the User who
 * created it. A Consumer that belongs to an Organisation is owned by the members of that Organisation while
 * it is active, and no longer by its creator alone, so it stays with the team when someone leaves.
 * This is the one check behind the /my/consumers/... endpoints and the developer's side of API Product
 * Subscriptions.
 */
object ConsumerOwnership {

  /** The Organisation a Consumer belongs to, if any. */
  def organisationIdOf(consumer: Consumer): Option[String] =
    Option(consumer.organisationId).map(_.trim).filter(_.nonEmpty)

  /** True if the User may act for the Consumer: see the description of this object. */
  def canActFor(consumer: Consumer, userId: String): Boolean =
    organisationIdOf(consumer) match {
      case Some(organisationId) => isActiveMember(organisationId, userId)
      case None => Option(consumer.createdByUserId).contains(userId)
    }

  /** True if the User is an admin of the Consumer's active Organisation. */
  def isAdminOfConsumersOrganisation(consumer: Consumer, userId: String): Boolean =
    organisationIdOf(consumer).exists(organisationId => isActiveAdmin(organisationId, userId))

  def isActiveMember(organisationId: String, userId: String): Boolean =
    Organisations.getMember(organisationId, userId).isDefined && organisationIsActive(organisationId)

  def isActiveAdmin(organisationId: String, userId: String): Boolean =
    Organisations.getMember(organisationId, userId).exists(_.memberRole == Organisations.RoleAdmin) &&
      organisationIsActive(organisationId)

  private def organisationIsActive(organisationId: String): Boolean =
    Organisations.getOrganisation(organisationId).exists(_.status == Organisations.StatusActive)

  /** The Consumers the User may act for: their personal ones, and those of their active Organisations. */
  def consumersFor(userId: String): Future[List[Consumer]] = Future {
    val created = Consumer.findAllByCreatedByUserId(userId).filter(organisationIdOf(_).isEmpty)
    val organisationIds = Organisations.getOrganisationsOfUser(userId)
      .collect { case (organisation, _) if organisation.status == Organisations.StatusActive => organisation.organisationId }
    val ofOrganisations =
      if (organisationIds.isEmpty) Nil else Consumer.findAllByOrganisationIds(organisationIds)
    (created ++ ofOrganisations).groupBy(_.consumerId).values.map(_.head).toList.sortBy(_.name.toLowerCase)
  }

  /** The number of Consumers that belong to the Organisation. */
  def consumerCount(organisationId: String): Long =
    Consumer.countByOrganisationId(organisationId)
}
