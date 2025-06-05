package code.consumer

import code.api.util.{APIUtil, CallContext, OBPQueryParam}
import code.model.{AppType, Consumer, MappedConsumersProvider}
import com.openbankproject.commons.model.{BankIdAccountId, User, View}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Consumers extends SimpleInjector {

  val consumers = new Inject(buildOne _) {}

  def buildOne: ConsumersProvider = MappedConsumersProvider

}


// Question: This should never be the entry point?
trait ConsumersProvider {
  def getConsumerByPrimaryIdFuture(id: Long): Future[Box[Consumer]]
  def getConsumerByPrimaryId(id: Long): Box[Consumer]
  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer]
  def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]]
  def getConsumerByPemCertificate(pem: String): Box[Consumer]
  def getConsumerByConsumerId(consumerId: String): Box[Consumer]
  def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]]
  def getConsumersByUserIdFuture(userId: String): Future[List[Consumer]]
  def getConsumersFuture(httpParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[Consumer]]
  def createConsumer(
    key: Option[String],
    secret: Option[String],
    isActive: Option[Boolean], 
    name: Option[String], 
    appType: Option[AppType], 
    description: Option[String], 
    developerEmail: Option[String], 
    redirectURL: Option[String],
    createdByUserId: Option[String],
    clientCertificate: Option[String],
    company: Option[String],
    logoURL: Option[String]
  ): Box[Consumer]
  def deleteConsumer(consumer: Consumer): Boolean
  def updateConsumer(id: Long,
                     key: Option[String] = None,
                     secret: Option[String] = None,
                     isActive: Option[Boolean] = None,
                     name: Option[String] = None,
                     appType: Option[AppType] = None,
                     description: Option[String] = None,
                     developerEmail: Option[String] = None,
                     redirectURL: Option[String] = None,
                     createdByUserId: Option[String] = None,
                     LogoURL: Option[String] = None,
                     certificate: Option[String] = None,
  ): Box[Consumer]
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
                          createdByUserId: Option[String],
                          certificate: Option[String] = None,
                          logoUrl: Option[String] = None
                         ): Box[Consumer]
  def populateMissingUUIDs(): Boolean
  
}