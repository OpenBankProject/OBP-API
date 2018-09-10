package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.consumer.RemotedataConsumersCaseClasses
import code.model.{MappedConsumersProvider, _}
import code.util.Helper.MdcLoggable

class RemotedataConsumersActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedConsumersProvider
  val cc = RemotedataConsumersCaseClasses

  def receive = {

    case cc.getConsumerByPrimaryIdFuture(id: Long) =>
      logger.debug("getConsumerByPrimaryIdFuture(" + id +")")
      sender ! (mapper.getConsumerByPrimaryId(id))

    case cc.getConsumerByPrimaryId(id: Long) =>
      logger.debug("getConsumerByPrimaryId(" + id +")")
      sender ! extractResult(mapper.getConsumerByPrimaryId(id))

    case cc.getConsumerByConsumerKey(consumerKey: String) =>
      logger.debug("getConsumerByConsumerKey(" + "*****" +")")
      sender ! extractResult(mapper.getConsumerByConsumerKey(consumerKey))

    case cc.getConsumerByConsumerKeyFuture(consumerKey: String) =>
      logger.debug("getConsumerByConsumerKeyFuture(" + "*****" +")")
      sender ! (mapper.getConsumerByConsumerKey(consumerKey))

    case cc.getConsumerByConsumerIdFuture(consumerId: String) =>
      logger.debug("getConsumerByConsumerIdFuture(" + "*****" +")")
      sender ! (mapper.getConsumerByConsumerId(consumerId))

    case cc.getConsumersByUserIdFuture(id: String) =>
      logger.debug("getConsumersByUserIdFuture(" + id +")")
      sender ! (mapper.getConsumersByUserId(id))

    case cc.getConsumersFuture() =>
      logger.debug("getConsumersFuture()")
      sender ! (mapper.getConsumers())

    case cc.createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]) =>
      logger.debug("createConsumer(" + "*****" + ", " + "*****" + ", " + isActive.getOrElse("None") + ", " + name.getOrElse("None") + ", " + appType.getOrElse("None") + ", " + description.getOrElse("None") + ", " + developerEmail.getOrElse("None") + ", " + redirectURL.getOrElse("None") + ", " + createdByUserId.getOrElse("None") + ")")
      sender ! extractResult(mapper.createConsumer(key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

    case cc.updateConsumer(id: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]) =>
      logger.debug("updateConsumer(" + id + ", " + "*****" + ", " + "*****" + ", " + isActive.getOrElse("None") + ", " + name.getOrElse("None") + ", " + appType.getOrElse("None") + ", " + description.getOrElse("None") + ", " + developerEmail.getOrElse("None") + ", " + redirectURL.getOrElse("None") + ", " + createdByUserId.getOrElse("None") + ")")
      sender ! extractResult(mapper.updateConsumer(id, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

     case cc.updateConsumerCallLimits(id: Long, perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]) =>
      logger.debug("updateConsumerCallLimits(" + id + ", " + perMinute.getOrElse("None") + ", " + perHour.getOrElse("None") + ", " + perDay.getOrElse("None") + ", " + perWeek.getOrElse("None") + ", " + perMonth.getOrElse("None") + ")")
      sender ! (mapper.updateConsumerCallLimitsRemote(id, perMinute, perHour, perDay, perWeek, perMonth))

    case cc.getOrCreateConsumer(consumerId: Option[String], key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]) =>
      logger.debug("getOrCreateConsumer(" + consumerId.getOrElse("None") + ", " + "*****" + ", " + "*****" + ", " + isActive.getOrElse("None") + ", " + name.getOrElse("None") + ", " + appType.getOrElse("None") + ", " + description.getOrElse("None") + ", " + developerEmail.getOrElse("None") + ", " + redirectURL.getOrElse("None") + ", " + createdByUserId.getOrElse("None") + ")")
      sender ! extractResult(mapper.getOrCreateConsumer(consumerId, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))


    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


