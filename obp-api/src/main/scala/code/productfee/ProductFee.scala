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

package code.productfee

/* For ProductFee */

import code.api.util.APIUtil
import com.openbankproject.commons.model.{BankId, ProductCode, ProductFeeTrait}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.concurrent.Future
import scala.math.BigDecimal

object ProductFeeX extends SimpleInjector {

  val productFeeProvider = new Inject(() => buildOne) {}

  def buildOne: ProductFeeProvider = MappedProductFeeProvider

  // Helper to get the count out of an option
  def countOfProductFee(listOpt: Option[List[ProductFeeTrait]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait ProductFeeProvider extends MdcLoggable {

  def getProductFeesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductFeeTrait]]]

  def getProductFeeById(productFeeId: String): Future[Box[ProductFeeTrait]]

  def createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    productFeeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String
  ): Future[Box[ProductFeeTrait]]

  def deleteProductFee(productFeeId: String): Future[Box[Boolean]]
}
