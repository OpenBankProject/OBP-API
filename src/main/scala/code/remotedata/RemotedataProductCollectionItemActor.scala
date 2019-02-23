package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.productcollectionitem.{MappedProductCollectionItemProvider, RemotedataProductCollectionItemCaseClasses}
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataProductCollectionItemActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedProductCollectionItemProvider
  val cc = RemotedataProductCollectionItemCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getProductCollectionItems(collectionCode: String) =>
      logger.debug("getProductCollectionItems(" + collectionCode + ")")
      mapper.getProductCollectionItems(collectionCode) pipeTo sender
      
    case cc.getProductCollectionItemsTree(collectionCode: String, bankId: String) =>
      logger.debug("getProductCollectionItems(" + collectionCode + ", " + bankId + ")")
      mapper.getProductCollectionItemsTree(collectionCode, bankId) pipeTo sender
      
    case cc.getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String]) =>
      logger.debug("getOrCreateProductCollectionItem(" + collectionCode +  ", " + memberProductCodes + ")")
      mapper.getOrCreateProductCollectionItem(collectionCode, memberProductCodes) pipeTo sender
      

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


