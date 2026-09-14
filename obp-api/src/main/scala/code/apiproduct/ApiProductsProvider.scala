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
import net.liftweb.mapper.{By, Like}
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
    val existing = ApiProduct.find(
      By(ApiProduct.BankId, bankId),
      By(ApiProduct.ApiProductCode, apiProductCode)
    )
    val encodedTags = ApiProduct.encodeTags(tags)
    existing match {
      case net.liftweb.common.Full(product) =>
        tryo(
          product
            .ParentApiProductCode(parentApiProductCode)
            .Name(name)
            .Category(category)
            .MoreInfoUrl(moreInfoUrl)
            .TermsAndConditionsUrl(termsAndConditionsUrl)
            .Description(description)
            .CollectionId(collectionId)
            .MonthlySubscriptionCurrency(monthlySubscriptionCurrency)
            .MonthlySubscriptionAmount(monthlySubscriptionAmount)
            .PerSecondCallLimit(perSecondCallLimit)
            .PerMinuteCallLimit(perMinuteCallLimit)
            .PerHourCallLimit(perHourCallLimit)
            .PerDayCallLimit(perDayCallLimit)
            .PerWeekCallLimit(perWeekCallLimit)
            .PerMonthCallLimit(perMonthCallLimit)
            .Tags(encodedTags)
            .saveMe()
        )
      case _ =>
        tryo(
          ApiProduct
            .create
            .BankId(bankId)
            .ApiProductCode(apiProductCode)
            .ParentApiProductCode(parentApiProductCode)
            .Name(name)
            .Category(category)
            .MoreInfoUrl(moreInfoUrl)
            .TermsAndConditionsUrl(termsAndConditionsUrl)
            .Description(description)
            .CollectionId(collectionId)
            .MonthlySubscriptionCurrency(monthlySubscriptionCurrency)
            .MonthlySubscriptionAmount(monthlySubscriptionAmount)
            .PerSecondCallLimit(perSecondCallLimit)
            .PerMinuteCallLimit(perMinuteCallLimit)
            .PerHourCallLimit(perHourCallLimit)
            .PerDayCallLimit(perDayCallLimit)
            .PerWeekCallLimit(perWeekCallLimit)
            .PerMonthCallLimit(perMonthCallLimit)
            .Tags(encodedTags)
            .saveMe()
        )
    }
  }

  override def getApiProductByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[ApiProductTrait] = ApiProduct.find(
    By(ApiProduct.BankId, bankId),
    By(ApiProduct.ApiProductCode, apiProductCode)
  )

  override def getApiProductsByBankId(
    bankId: String,
    tag: Option[String] = None
  ): List[ApiProductTrait] = {
    val baseParams = List(By(ApiProduct.BankId, bankId))
    val params = tag.map(_.trim.toLowerCase).filter(_.nonEmpty) match {
      case Some(t) => baseParams :+ Like(ApiProduct.Tags, s"%|$t|%")
      case None => baseParams
    }
    ApiProduct.findAll(params: _*)
  }

  override def deleteApiProduct(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean] = ApiProduct.find(
    By(ApiProduct.BankId, bankId),
    By(ApiProduct.ApiProductCode, apiProductCode)
  ).map(_.delete_!)
}
