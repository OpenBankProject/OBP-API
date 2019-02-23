package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.productattribute.ProductAttribute.ProductAttribute
import code.productcollectionitem.{ProductCollectionItem, ProductCollectionItemProvider, RemotedataProductCollectionItemCaseClasses}
import code.products.MappedProduct
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataProductCollectionItem extends ObpActorInit with ProductCollectionItemProvider {

  val cc = RemotedataProductCollectionItemCaseClasses

  override def getProductCollectionItems(collectionCode: String): Future[Box[List[ProductCollectionItem]]] ={
    (actor ? cc.getProductCollectionItems(collectionCode)).mapTo[Box[List[ProductCollectionItem]]]
  }
  override def getProductCollectionItemsTree(collectionCode: String, bankId: String): Future[Box[List[(ProductCollectionItem, MappedProduct, List[ProductAttribute])]]] ={
    (actor ? cc.getProductCollectionItemsTree(collectionCode, bankId)).mapTo[Box[List[(ProductCollectionItem, MappedProduct, List[ProductAttribute])]]]
  }

  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String]): Future[Box[List[ProductCollectionItem]]] ={
    (actor ? cc.getOrCreateProductCollectionItem(collectionCode, memberProductCodes)).mapTo[Box[List[ProductCollectionItem]]]
  }
}
