package code.consumer

import code.model.{AppType, Consumer}
import code.remotedata.RemotedataConsumers
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Consumers extends SimpleInjector {

  val consumers = new Inject(buildOne _) {}

  def buildOne: ConsumersProvider = RemotedataConsumers

}


// Question: This should never be the entry point?
trait ConsumersProvider {
  def getConsumerByConsumerId(consumerId: Long): Box[Consumer]
  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer]
  def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
  def updateConsumer(consumerId: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
}


// Question: This should always be the entry point?
class RemotedataConsumersCaseClasses {
  case class getConsumerByConsumerId(consumerId: Long)
  case class getConsumerByConsumerKey(consumerKey: String)
  case class createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
  case class updateConsumer(consumerId: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
}

object RemotedataConsumersCaseClasses extends RemotedataConsumersCaseClasses
