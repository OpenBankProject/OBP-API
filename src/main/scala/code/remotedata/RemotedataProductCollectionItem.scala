package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.productcollectionitem.{ProductCollectionItem, ProductCollectionItemProvider, RemotedataProductCollectionItemCaseClasses}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataProductCollectionItem extends ObpActorInit with ProductCollectionItemProvider {

  val cc = RemotedataProductCollectionItemCaseClasses

  override def getProductCollectionItems(collectionCode: String): Future[Box[List[ProductCollectionItem]]] ={
    (actor ? cc.getProductCollectionItems(collectionCode)).mapTo[Box[List[ProductCollectionItem]]]
  }

  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String]): Future[Box[List[ProductCollectionItem]]] ={
    (actor ? cc.getOrCreateProductCollectionItem(collectionCode, memberProductCodes)).mapTo[Box[List[ProductCollectionItem]]]
  }
}
