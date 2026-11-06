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

package code.apiproductsubscription

import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.util.Helpers.tryo

import java.util.Date

trait ApiProductSubscriptionsProvider {
  def createApiProductSubscription(
    bankId: String,
    apiProductCode: String,
    consumerId: String,
    status: String,
    startDate: Date,
    endDate: Option[Date],
    createdByUserId: String
  ): Box[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionById(apiProductSubscriptionId: String): Box[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionsByConsumerId(consumerId: String): List[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionsByConsumerIds(consumerIds: List[String]): List[ApiProductSubscriptionTrait]

  def getApiProductSubscriptionsByBankIdAndProductCode(bankId: String, apiProductCode: String): List[ApiProductSubscriptionTrait]

  /** The one subscription that is not `cancelled` for this consumer and product, if any. */
  def getNonCancelledApiProductSubscription(consumerId: String, bankId: String, apiProductCode: String): Box[ApiProductSubscriptionTrait]

  /**
   * Moves the status. Refuses (Failure) a transition that is not in ApiProductSubscriptionStatus.
   * `endDate`, when given, replaces the stored end date.
   */
  def updateApiProductSubscriptionStatus(apiProductSubscriptionId: String, newStatus: String, endDate: Option[Date]): Box[ApiProductSubscriptionTrait]

  def setRateLimitingId(apiProductSubscriptionId: String, rateLimitingId: Option[String]): Box[ApiProductSubscriptionTrait]

  def deleteApiProductSubscription(apiProductSubscriptionId: String): Box[Boolean]
}

object DoobieApiProductSubscriptionsProvider extends MdcLoggable with ApiProductSubscriptionsProvider {

  override def createApiProductSubscription(
    bankId: String,
    apiProductCode: String,
    consumerId: String,
    status: String,
    startDate: Date,
    endDate: Option[Date],
    createdByUserId: String
  ): Box[ApiProductSubscriptionTrait] =
    if (!ApiProductSubscriptionStatus.isValid(status)) Failure(s"Invalid status: $status")
    else tryo {
      ApiProductSubscription.insert(bankId, apiProductCode, consumerId, status, startDate,
        endDate, createdByUserId)
    }

  override def getApiProductSubscriptionById(apiProductSubscriptionId: String): Box[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findById(apiProductSubscriptionId)

  override def getApiProductSubscriptionsByConsumerId(consumerId: String): List[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findByConsumerId(consumerId).map(r => r: ApiProductSubscriptionTrait)

  override def getApiProductSubscriptionsByConsumerIds(consumerIds: List[String]): List[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findByConsumerIds(consumerIds).map(r => r: ApiProductSubscriptionTrait)

  override def getApiProductSubscriptionsByBankIdAndProductCode(bankId: String, apiProductCode: String): List[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findByBankIdAndProductCode(bankId, apiProductCode)
      .map(r => r: ApiProductSubscriptionTrait)

  override def getNonCancelledApiProductSubscription(consumerId: String, bankId: String, apiProductCode: String): Box[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findNonCancelled(consumerId, bankId, apiProductCode)

  override def updateApiProductSubscriptionStatus(apiProductSubscriptionId: String, newStatus: String, endDate: Option[Date]): Box[ApiProductSubscriptionTrait] =
    ApiProductSubscription.findById(apiProductSubscriptionId).flatMap { row =>
      if (!ApiProductSubscriptionStatus.canTransition(row.status, newStatus))
        Failure(s"Invalid status transition: ${row.status} -> $newStatus")
      else ApiProductSubscription.updateStatus(apiProductSubscriptionId, newStatus, endDate)
    }

  override def setRateLimitingId(apiProductSubscriptionId: String, rateLimitingId: Option[String]): Box[ApiProductSubscriptionTrait] =
    ApiProductSubscription.setRateLimitingId(apiProductSubscriptionId, rateLimitingId)

  override def deleteApiProductSubscription(apiProductSubscriptionId: String): Box[Boolean] =
    ApiProductSubscription.findById(apiProductSubscriptionId) match {
      case Full(_) => tryo(ApiProductSubscription.delete(apiProductSubscriptionId))
      case _ => Full(false)
    }
}
