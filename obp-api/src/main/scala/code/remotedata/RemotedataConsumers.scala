package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.util.APIUtil
import code.consumer.{ConsumersProvider, RemotedataConsumersCaseClasses}
import code.model._
import com.google.common.cache.CacheBuilder
import net.liftweb.common._
import scalacache.ScalaCache
import scalacache.guava.GuavaCache

import scala.concurrent.Future


object RemotedataConsumers extends ObpActorInit with ConsumersProvider {

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  val getConsumerTTL  = APIUtil.getPropsValue("connector.cache.ttl.seconds.getConsumer", "6000").toInt * 1000 // Miliseconds

  val cc = RemotedataConsumersCaseClasses
  
  def getConsumerByPrimaryIdFuture(id: Long): Future[Box[Consumer]] =
    (actor ? cc.getConsumerByPrimaryIdFuture(id)).mapTo[Box[Consumer]]

  def getConsumerByPrimaryId(id: Long): Box[Consumer] = getValueFromFuture(
    (actor ? cc.getConsumerByPrimaryId(id)).mapTo[Box[Consumer]]
  )
  def getConsumerByConsumerId(consumerId: String): Box[Consumer] =  getValueFromFuture(
    (actor ? cc.getConsumerByConsumerId(consumerId)).mapTo[Box[Consumer]]
  )
  def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]] =
    (actor ? cc.getConsumerByConsumerIdFuture(consumerId)).mapTo[Box[Consumer]]

  def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]] =
    (actor ? cc.getConsumerByConsumerKeyFuture(consumerKey)).mapTo[Box[Consumer]]

  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] =  getValueFromFuture(
    (actor ? cc.getConsumerByConsumerKey(consumerKey)).mapTo[Box[Consumer]]
  )

  def getConsumersByUserIdFuture(id: String): Future[List[Consumer]] =
    (actor ? cc.getConsumersByUserIdFuture(id)).mapTo[List[Consumer]]

  def getConsumersFuture(): Future[List[Consumer]] =
    (actor ? cc.getConsumersFuture()).mapTo[List[Consumer]]

  def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String], clientCertificate: Option[String] = None): Box[Consumer] = getValueFromFuture(
    (actor ? cc.createConsumer(key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId, clientCertificate)).mapTo[Box[Consumer]]
  )

  def deleteConsumer(consumer: Consumer): Boolean =  getValueFromFuture(
    (actor ? cc.deleteConsumer(consumer)).mapTo[Boolean]
  )

  def updateConsumer(id: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] = getValueFromFuture(
    (actor ? cc.updateConsumer(id, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId)).mapTo[Box[Consumer]]
  )
  def updateConsumerCallLimits(id: Long, perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Future[Box[Consumer]] =
    (actor ? cc.updateConsumerCallLimits(id, perSecond, perMinute, perHour, perDay, perWeek, perMonth)).mapTo[Box[Consumer]]
  
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
                          createdByUserId: Option[String]): Box[Consumer] = getValueFromFuture(
    (actor ? cc.getOrCreateConsumer(consumerId, key, secret, aud, azp, iss, sub, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId)).mapTo[Box[Consumer]]
  )
  def populateMissingUUIDs(): Boolean = getValueFromFuture(
    (actor ? cc.populateMissingUUIDs()).mapTo[Boolean]
  )

}
