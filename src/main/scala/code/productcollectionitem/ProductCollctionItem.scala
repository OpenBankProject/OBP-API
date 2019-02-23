package code.productcollectionitem

import code.api.util.APIUtil
import code.productattribute.ProductAttribute.ProductAttribute
import code.products.MappedProduct
import code.remotedata.RemotedataProductCollectionItem
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object ProductCollectionItem extends SimpleInjector {

  val productCollectionItem = new Inject(buildOne _) {}

  def buildOne: ProductCollectionItemProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedProductCollectionItemProvider
      case true => RemotedataProductCollectionItem   // We will use Akka as a middleware
    }
}

trait ProductCollectionItemProvider {
  def getProductCollectionItemsTree(collectionCode: String, bankId: String): Future[Box[List[(ProductCollectionItem, MappedProduct, List[ProductAttribute])]]]
  def getProductCollectionItems(collectionCode: String): Future[Box[List[ProductCollectionItem]]]
  def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String]): Future[Box[List[ProductCollectionItem]]]
}

trait ProductCollectionItem {
  def collectionCode: String
  def memberProductCode: String
}

class RemotedataProductCollectionItemCaseClasses {
  case class getProductCollectionItemsTree(collectionCode: String, bankId: String)
  case class getProductCollectionItems(collectionCode: String)
  case class getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String])
}

object RemotedataProductCollectionItemCaseClasses extends RemotedataProductCollectionItemCaseClasses