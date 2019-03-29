package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.productcollection.{ProductCollectionProvider, RemotedataProductCollectionCaseClasses}
import com.openbankproject.commons.model.ProductCollection
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataProductCollection extends ObpActorInit with ProductCollectionProvider {

  val cc = RemotedataProductCollectionCaseClasses

  override def getProductCollection(collectionCode: String): Future[Box[List[ProductCollection]]] ={
    (actor ? cc.getProductCollection(collectionCode)).mapTo[Box[List[ProductCollection]]]
  }

  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String]): Future[Box[List[ProductCollection]]] = {
    (actor ? cc.getOrCreateProductCollection(collectionCode, productCodes)).mapTo[Box[List[ProductCollection]]]
  }
}
