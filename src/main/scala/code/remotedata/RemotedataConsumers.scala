package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.consumer.{ConsumersProvider, RemotedataConsumersCaseClasses}
import code.model._
import net.liftweb.common._


object RemotedataConsumers extends ObpActorInit with ConsumersProvider {

  val cc = RemotedataConsumersCaseClasses

  def getConsumerByConsumerId(consumerId: Long): Box[Consumer] =
    extractFutureToBox(actor ? cc.getConsumerByConsumerId(consumerId))

  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] =
    extractFutureToBox(actor ? cc.getConsumerByConsumerKey(consumerKey))

  def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] =
    extractFutureToBox(actor ? cc.createConsumer(key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

  def updateConsumer(consumerId: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] =
    extractFutureToBox(actor ? cc.updateConsumer(consumerId, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

}
