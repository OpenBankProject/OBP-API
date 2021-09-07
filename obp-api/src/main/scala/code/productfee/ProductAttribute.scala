package code.productfee

/* For ProductFee */

import code.api.util.APIUtil
import com.openbankproject.commons.model.{BankId, ProductCode, ProductFee}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future
import scala.math.BigDecimal

object ProductFeeX extends SimpleInjector {

  val productFeeProvider = new Inject(buildOne _) {}

  def buildOne: ProductFeeProvider = MappedProductFeeProvider

  // Helper to get the count out of an option
  def countOfProductFee(listOpt: Option[List[ProductFee]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait ProductFeeProvider {

  private val logger = Logger(classOf[ProductFeeProvider])

  def getProductFeesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductFee]]]

  def getProductFeeById(feeId: String): Future[Box[ProductFee]]

  def createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    feeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String
  ): Future[Box[ProductFee]]
  
  def deleteProductFee(feeId: String): Future[Box[Boolean]]
}

class RemotedataProductFeeCaseClasses {
  case class getProductFeesFromProvider(bankId: BankId, productCode: ProductCode)

  case class getProductFeeById(feeId: String)

  case class createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    feeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String
  )

  case class deleteProductFee(feeId: String)
}

object RemotedataProductFeeCaseClasses extends RemotedataProductFeeCaseClasses
