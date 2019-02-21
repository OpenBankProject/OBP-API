package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.productcollection.{MappedProductCollectionProvider, RemotedataProductCollectionCaseClasses}
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataProductCollectionActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedProductCollectionProvider
  val cc = RemotedataProductCollectionCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getProductCollection(collectionCode: String) =>
      logger.debug("getProductCollection(" + collectionCode + ")")
      mapper.getProductCollection(collectionCode) pipeTo sender
      
    case cc.getOrCreateProductCollection(collectionCode: String, productCodes: List[String]) =>
      logger.debug("getOrCreateProductCollection(" + collectionCode +  ", " + productCodes + ")")
      mapper.getOrCreateProductCollection(collectionCode, productCodes) pipeTo sender
      

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


