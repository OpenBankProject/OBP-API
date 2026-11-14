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

package code.apiproduct

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

trait ApiProductsProvider {
  def createOrUpdateApiProduct(
    bankId: String,
    apiProductCode: String,
    parentApiProductCode: String,
    name: String,
    category: String,
    moreInfoUrl: String,
    termsAndConditionsUrl: String,
    description: String,
    collectionId: String,
    monthlySubscriptionCurrency: String,
    monthlySubscriptionAmount: String,
    perSecondCallLimit: Long,
    perMinuteCallLimit: Long,
    perHourCallLimit: Long,
    perDayCallLimit: Long,
    perWeekCallLimit: Long,
    perMonthCallLimit: Long,
    tags: List[String]
  ): Box[ApiProductTrait]

  def getApiProductByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[ApiProductTrait]

  def getApiProductsByBankId(
    bankId: String,
    tag: Option[String] = None
  ): List[ApiProductTrait]

  def deleteApiProduct(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean]
}

object MappedApiProductsProvider extends MdcLoggable with ApiProductsProvider {

  override def createOrUpdateApiProduct(
    bankId: String,
    apiProductCode: String,
    parentApiProductCode: String,
    name: String,
    category: String,
    moreInfoUrl: String,
    termsAndConditionsUrl: String,
    description: String,
    collectionId: String,
    monthlySubscriptionCurrency: String,
    monthlySubscriptionAmount: String,
    perSecondCallLimit: Long,
    perMinuteCallLimit: Long,
    perHourCallLimit: Long,
    perDayCallLimit: Long,
    perWeekCallLimit: Long,
    perMonthCallLimit: Long,
    tags: List[String]
  ): Box[ApiProductTrait] = {
    val existing = ApiProduct.findByBankIdAndCode(bankId, apiProductCode)
    val encodedTags = ApiProduct.encodeTags(tags)
    existing match {
      case net.liftweb.common.Full(_) =>
        tryo(
          ApiProduct.updateByBankIdAndCode(
            bankId, apiProductCode, parentApiProductCode, name, category, moreInfoUrl,
            termsAndConditionsUrl, description, collectionId, monthlySubscriptionCurrency,
            monthlySubscriptionAmount, perSecondCallLimit, perMinuteCallLimit, perHourCallLimit,
            perDayCallLimit, perWeekCallLimit, perMonthCallLimit, encodedTags
          ).openOrThrowException("the row just matched must still be readable")
        )
      case _ =>
        tryo(
          ApiProduct.insert(
            bankId, apiProductCode, parentApiProductCode, name, category, moreInfoUrl,
            termsAndConditionsUrl, description, collectionId, monthlySubscriptionCurrency,
            monthlySubscriptionAmount, perSecondCallLimit, perMinuteCallLimit, perHourCallLimit,
            perDayCallLimit, perWeekCallLimit, perMonthCallLimit, encodedTags
          )
        )
    }
  }

  override def getApiProductByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[ApiProductTrait] = ApiProduct.findByBankIdAndCode(bankId, apiProductCode)

  override def getApiProductsByBankId(
    bankId: String,
    tag: Option[String] = None
  ): List[ApiProductTrait] = {
    tag.map(_.trim.toLowerCase).filter(_.nonEmpty) match {
      case Some(t) => ApiProduct.findAllByBankIdAndTag(bankId, t)
      case None => ApiProduct.findAllByBankId(bankId)
    }
  }

  override def deleteApiProduct(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean] = ApiProduct.findByBankIdAndCode(bankId, apiProductCode)
    .map(_ => ApiProduct.deleteByBankIdAndCode(bankId, apiProductCode))
}
