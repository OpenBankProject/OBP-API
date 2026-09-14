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

import code.api.util.APIUtil
import code.api.util.ErrorMessages.{CreateProductFeeError, UpdateProductFeeError}
import code.util.UUIDString
import com.openbankproject.commons.model.{BankId, ProductCode, ProductFeeTrait}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedBoolean, _}
import net.liftweb.util.Helpers.tryo

import java.math.MathContext
import scala.math.BigDecimal
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

object MappedProductFeeProvider extends ProductFeeProvider {

  override def getProductFeesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductFeeTrait]]] =
    Future {
      Box !!  ProductFee.findAll(
          By(ProductFee.BankId, bankId.value),
          By(ProductFee.ProductCode, productCode.value)
        )
    }

  override def getProductFeeById(productFeeId: String): Future[Box[ProductFeeTrait]] = Future {
     ProductFee.find(By(ProductFee.ProductFeeId, productFeeId))
  }

  override def createOrUpdateProductFee(
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
  ): Future[Box[ProductFeeTrait]] =  {
     productFeeId match {
      case Some(id) => Future {
         ProductFee.find(By(ProductFee.ProductFeeId, id)) match {
            case Full(productFee) => tryo {
              productFee
                .BankId(bankId.value)
                .ProductCode(productCode.value)
                .Name(name)
                .IsActive(isActive)
                .MoreInfo(moreInfo)
                .Currency(currency)
                .Amount(amount)
                .Frequency(frequency)
                .Type(`type`)
                .saveMe()
            } ?~! s"$UpdateProductFeeError"
            case _ => Empty
          }
      }
      case None => Future {
        tryo {
          ProductFee
            .create
            .ProductFeeId(APIUtil.generateUUID)
            .BankId(bankId.value)
            .ProductCode(productCode.value)
            .Name(name)
            .IsActive(isActive)
            .MoreInfo(moreInfo)
            .Currency(currency)
            .Amount(amount)
            .Frequency(frequency)
            .Type(`type`)
            .saveMe()
        } ?~! s"$CreateProductFeeError"
      }
    }
  }

  override def deleteProductFee(productFeeId: String): Future[Box[Boolean]] = Future {
    tryo(
      ProductFee.bulkDelete_!!(By(ProductFee.ProductFeeId, productFeeId))
    )
  }
}

class ProductFee extends ProductFeeTrait with LongKeyedMapper[ProductFee] with IdPK {

  override def getSingleton = ProductFee

  object BankId extends UUIDString(this) 

  object ProductCode extends MappedString(this, 50) 
  
  object ProductFeeId extends UUIDString(this) 

  object Name extends MappedString(this, 100)
  
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }
  
  object MoreInfo extends MappedString(this, 255)

  object Currency extends MappedString(this, 50)
  
  object Amount extends MappedDecimal(this, MathContext.DECIMAL128, 2)
  
  object Frequency extends MappedString(this, 255)
  
  object Type extends MappedString(this, 255)
  

  override def bankId: BankId = com.openbankproject.commons.model.BankId(BankId.get)

  override def productCode: ProductCode = com.openbankproject.commons.model.ProductCode(ProductCode.get)
  
  override def productFeeId: String = ProductFeeId.get

  override def name: String = Name.get

  override def isActive: Boolean = IsActive.get

  override def moreInfo: String = MoreInfo.get

  override def currency: String = Currency.get

  override def amount: BigDecimal = Amount.get
  
  override def frequency: String = Frequency.get
  
  override def `type`: String = Type.get
  
}

object ProductFee extends ProductFee with LongKeyedMetaMapper[ProductFee]  {
  override def dbIndexes = Index(BankId) :: Index(ProductFeeId) :: super.dbIndexes 
}

