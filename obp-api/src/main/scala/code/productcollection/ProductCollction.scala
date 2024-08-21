package code.productcollection

import code.api.util.APIUtil
import com.openbankproject.commons.model.ProductCollection
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future


object ProductCollectionX extends SimpleInjector {

  val productCollection = new Inject(buildOne _) {}

  def buildOne: ProductCollectionProvider = MappedProductCollectionProvider 
  
}

trait ProductCollectionProvider {
  def getProductCollection(collectionCode: String): Future[Box[List[ProductCollection]]]
  def getOrCreateProductCollection(collectionCode: String, productCodes: List[String]): Future[Box[List[ProductCollection]]]
}
