package code.productcollection

import code.api.util.APIUtil
import code.remotedata.RemotedataProductCollection
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object ProductCollection extends SimpleInjector {

  val productCollection = new Inject(buildOne _) {}

  def buildOne: ProductCollectionProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedProductCollectionProvider
      case true => RemotedataProductCollection     // We will use Akka as a middleware
    }
}

trait ProductCollectionProvider {
  def getProductCollection(collectionCode: String): Future[Box[List[ProductCollection]]]
  def getOrCreateProductCollection(collectionCode: String, productCodes: List[String]): Future[Box[List[ProductCollection]]]
}

trait ProductCollection {
  def collectionCode: String
  def productCode: String
}

class RemotedataProductCollectionCaseClasses {
  case class getProductCollection(collectionCode: String)
  case class getOrCreateProductCollection(collectionCode: String, productCodes: List[String])
}

object RemotedataProductCollectionCaseClasses extends RemotedataProductCollectionCaseClasses