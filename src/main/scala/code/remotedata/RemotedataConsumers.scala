package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.consumer.{ConsumersProvider, RemotedataConsumersCaseClasses}
import code.model._
import com.google.common.cache.CacheBuilder
import net.liftweb.common._
import net.liftweb.util.Props

import scala.concurrent.Future
import scala.concurrent.duration._
import scalacache.{Flags, ScalaCache}
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoizeSync}


object RemotedataConsumers extends ObpActorInit with ConsumersProvider {

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  val getConsumerTTL  = Props.get("connector.cache.ttl.seconds.getConsumer", "6000").toInt * 1000 // Miliseconds

  val cc = RemotedataConsumersCaseClasses

  def getConsumerByPrimaryId(id: Long): Box[Consumer] =
    extractFutureToBox(actor ? cc.getConsumerByPrimaryId(id))

  def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]] =
    (actor ? cc.getConsumerByConsumerKeyFuture(consumerKey)).mapTo[Box[Consumer]]

  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] = {

    def getConsumerByConsumerKey(consumerKey: String)(implicit @cacheKeyExclude flags: Flags): Box[Consumer] = memoizeSync(getConsumerTTL millisecond) {
      extractFutureToBox(actor ? cc.getConsumerByConsumerKey(consumerKey))
    }

    def getConsumer(consumerKey: String, skipCache: Boolean): Box[Consumer] = {
      implicit val flags = Flags(readsEnabled = !skipCache)
      getConsumerByConsumerKey(consumerKey)
    }

    // First try to obtain cache value,
    getConsumer(consumerKey, false) match {
      case Full(x)  => Full(x) // Success
      case _ => getConsumer(consumerKey, true) // Failure - make full round trip i.e. without caching
    }
  }

  def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] =
    extractFutureToBox(actor ? cc.createConsumer(key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

  def updateConsumer(id: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] =
    extractFutureToBox(actor ? cc.updateConsumer(id, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

  def getOrCreateConsumer(consumerId: Option[String], key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] =
    extractFutureToBox(actor ? cc.getOrCreateConsumer(consumerId, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))


}
