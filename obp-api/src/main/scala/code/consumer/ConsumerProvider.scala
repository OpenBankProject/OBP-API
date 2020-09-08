package code.consumer

import code.api.util.APIUtil
import code.model.{AppType, Consumer, MappedConsumersProvider}
import code.remotedata.RemotedataConsumers
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Consumers extends SimpleInjector {

  val consumers = new Inject(buildOne _) {}

  def buildOne: ConsumersProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
    case false  => MappedConsumersProvider
    case true => RemotedataConsumers     // We will use Akka as a middleware
  }

}


// Question: This should never be the entry point?
trait ConsumersProvider {
  def getConsumerByPrimaryIdFuture(id: Long): Future[Box[Consumer]]
  def getConsumerByPrimaryId(id: Long): Box[Consumer]
  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer]
  def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]]
  def getConsumerByConsumerId(consumerId: String): Box[Consumer]
  def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]]
  def getConsumersByUserIdFuture(userId: String): Future[List[Consumer]]
  def getConsumersFuture(): Future[List[Consumer]]
  def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
  def deleteConsumer(consumer: Consumer): Boolean
  def updateConsumer(id: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer]
  def updateConsumerCallLimits(id: Long, perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Future[Box[Consumer]]
  def getOrCreateConsumer(consumerId: Option[String], 
                          key: Option[String], 
                          secret: Option[String],
                          aud: Option[String],
                          azp: Option[String],
                          iss: Option[String],
                          sub: Option[String], 
                          isActive: Option[Boolean], 
                          name: Option[String], 
                          appType: Option[AppType], 
                          description: Option[String], 
                          developerEmail: Option[String], 
                          redirectURL: Option[String], 
                          createdByUserId: Option[String]): Box[Consumer]
  def populateMissingUUIDs(): Boolean
}


// Question: This should always be the entry point?
class RemotedataConsumersCaseClasses {
  case class getConsumerByPrimaryIdFuture(id: Long)
  case class getConsumerByPrimaryId(id: Long)
  case class getConsumerByConsumerKey(consumerKey: String)
  case class getConsumerByConsumerKeyFuture(consumerKey: String)
  case class getConsumerByConsumerId(consumerId: String)
  case class getConsumerByConsumerIdFuture(consumerId: String)
  case class getConsumersByUserIdFuture(userId: String)
  case class getConsumersFuture()
  case class createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
  case class updateConsumer(id: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String])
  case class deleteConsumer(consumer: Consumer)
  case class updateConsumerCallLimits(id: Long, perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String])
  case class getOrCreateConsumer(consumerId: Option[String], 
                                 key: Option[String], 
                                 secret: Option[String],
                                 aud: Option[String],
                                 azp: Option[String],
                                 iss: Option[String],
                                 sub: Option[String], 
                                 isActive: Option[Boolean], 
                                 name: Option[String], 
                                 appType: Option[AppType], 
                                 description: Option[String], 
                                 developerEmail: Option[String], 
                                 redirectURL: Option[String], 
                                 createdByUserId: Option[String])
  case class populateMissingUUIDs()
}

object RemotedataConsumersCaseClasses extends RemotedataConsumersCaseClasses
