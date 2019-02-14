package code.productcollectionitem

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object ProductCollectionItem extends SimpleInjector {

  val productCollectionItem = new Inject(buildOne _) {}

  def buildOne: ProductCollectionItemProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedProductCollectionItemProvider
      // case true => RemotedataCustomerAddress     // We will use Akka as a middleware
    }
}

trait ProductCollectionItemProvider {
  def getProductCollectionItems(collectionCode: String): Future[Box[List[ProductCollectionItem]]]
  def getOrCreateProductCollection(collectionCode: String, productCodes: List[String]): Future[Box[List[ProductCollectionItem]]]
}

trait ProductCollectionItem {
  def collectionCode: String
  def memberProductCode: String
}