package code.consumer

import code.model.{AppType, Consumer, MappedConsumersProvider}
import code.remotedata.RemotedataConsumers
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}


object Consumers extends SimpleInjector {

  val consumers = new Inject(buildOne _) {}

  def buildOne: ConsumersProvider =
    Props.getBool("use_akka", false) match {
    case false  => MappedConsumersProvider
    case true => RemotedataConsumers     // We will use Akka as a middleware
  }

}


// Question: This should never be the entry point?
trait ConsumersProvider {
  def getConsumerByPrimaryId(id: Long): Box[Consumer]
  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer]
  def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
  def updateConsumer(consumerId: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
  def getOrCreateConsumer(consumerId: Option[String], key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
}


// Question: This should always be the entry point?
class RemotedataConsumersCaseClasses {
  case class getConsumerByPrimaryId(id: Long)
  case class getConsumerByConsumerKey(consumerKey: String)
  case class createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
  case class updateConsumer(consumerId: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
  case class getOrCreateConsumer(consumerId: Option[String], key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
}

object RemotedataConsumersCaseClasses extends RemotedataConsumersCaseClasses
