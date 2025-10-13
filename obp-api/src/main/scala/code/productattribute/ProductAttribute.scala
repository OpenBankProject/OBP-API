package code.productattribute

/* For ProductAttribute */

import code.api.util.APIUtil
import code.productAttributeattribute.MappedProductAttributeProvider
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductCode}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.concurrent.Future

object ProductAttributeX extends SimpleInjector {

  val productAttributeProvider = new Inject(buildOne _) {}

  def buildOne: ProductAttributeProvider = MappedProductAttributeProvider

  // Helper to get the count out of an option
  def countOfProductAttribute(listOpt: Option[List[ProductAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait ProductAttributeProvider extends MdcLoggable {

  def getProductAttributesFromProvider(bank: BankId, productCode: ProductCode): Future[Box[List[ProductAttribute]]]

  def getProductAttributeById(productAttributeId: String): Future[Box[ProductAttribute]]

  def createOrUpdateProductAttribute(bankId : BankId,
                                     productCode: ProductCode,
                                     productAttributeId: Option[String],
                                     name: String,
                                     attributeType: ProductAttributeType.Value,
                                     value: String,
                                     isActive: Option[Boolean]): Future[Box[ProductAttribute]]
  def deleteProductAttribute(productAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}
