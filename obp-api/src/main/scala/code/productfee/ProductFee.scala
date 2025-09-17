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

  val productFeeProvider = new Inject(buildOne _) {}

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
