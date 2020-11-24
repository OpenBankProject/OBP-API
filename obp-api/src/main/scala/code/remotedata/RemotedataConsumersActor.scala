package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.consumer.RemotedataConsumersCaseClasses
import code.model.{MappedConsumersProvider, _}
import code.util.Helper.MdcLoggable

class RemotedataConsumersActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedConsumersProvider
  val cc = RemotedataConsumersCaseClasses

  def receive: PartialFunction[Any, Unit] = {
      
    case cc.getConsumerByPrimaryIdFuture(id: Long) =>
      logger.debug(s"getConsumerByPrimaryIdFuture($id)")
      sender ! (mapper.getConsumerByPrimaryId(id))

    case cc.getConsumerByPrimaryId(id: Long) =>
      logger.debug(s"getConsumerByPrimaryId($id)")
      sender ! (mapper.getConsumerByPrimaryId(id))

    case cc.getConsumerByConsumerKey(consumerKey: String) =>
      logger.debug(s"getConsumerByConsumerKey(*****)")
      sender ! (mapper.getConsumerByConsumerKey(consumerKey))

    case cc.getConsumerByConsumerKeyFuture(consumerKey: String) =>
      logger.debug("sgetConsumerByConsumerKeyFuture(*****)")
      sender ! (mapper.getConsumerByConsumerKey(consumerKey))

    case cc.getConsumerByConsumerId(consumerId: String) =>
      logger.debug(s"getConsumerByConsumerId(*****)")
      sender ! (mapper.getConsumerByConsumerId(consumerId))
      
    case cc.getConsumerByConsumerIdFuture(consumerId: String) =>
      logger.debug(s"getConsumerByConsumerIdFuture(*****)")
      sender ! (mapper.getConsumerByConsumerId(consumerId))

    case cc.getConsumersByUserIdFuture(id: String) =>
      logger.debug(s"getConsumersByUserIdFuture($id)")
      sender ! (mapper.getConsumersByUserId(id))

    case cc.getConsumersFuture() =>
      logger.debug(s"getConsumersFuture()")
      sender ! (mapper.getConsumers())

    case cc.createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String], clientCertificate: Option[String]) =>
      logger.debug(s"createConsumer(*****, *****, ${isActive.getOrElse("None")}, ${name.getOrElse("None")}, ${appType.getOrElse("None")}, ${description.getOrElse("None")}, ${developerEmail.getOrElse("None")}, ${redirectURL.getOrElse("None")}, ${createdByUserId.getOrElse("None")}, ${clientCertificate.getOrElse("None")})")
      sender ! (mapper.createConsumer(key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId, clientCertificate))

    case cc.updateConsumer(id: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]) =>
      logger.debug(s"updateConsumer($id, *****, *****, ${isActive.getOrElse("None")}, ${name.getOrElse("None")}, ${appType.getOrElse("None")}, ${description.getOrElse("None")}, ${developerEmail.getOrElse("None")}, ${redirectURL.getOrElse("None")}, ${createdByUserId.getOrElse("None")})")
      sender ! (mapper.updateConsumer(id, key, secret, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

     case cc.updateConsumerCallLimits(id: Long, perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]) =>
      logger.debug(s"updateConsumerCallLimits($id, ${perSecond.getOrElse("None")}, ${perMinute.getOrElse("None")}, ${perHour.getOrElse("None")}, ${perDay.getOrElse("None")}, ${perWeek.getOrElse("None")}, ${perMonth.getOrElse("None")})")
      sender ! (mapper.updateConsumerCallLimitsRemote(id, perSecond, perMinute, perHour, perDay, perWeek, perMonth))

    case cc.getOrCreateConsumer(consumerId: Option[String], key: Option[String], secret: Option[String], aud: Option[String], azp: Option[String], iss: Option[String], sub: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]) =>
      logger.debug(s"getOrCreateConsumer(${consumerId.getOrElse("None")}, *****, *****, ${aud.getOrElse("None")}, ${azp.getOrElse("None")}, ${iss.getOrElse("None")}, ${sub.getOrElse("None")}, ${name.getOrElse("None")}, ${appType.getOrElse("None")}, ${description.getOrElse("None")}, ${developerEmail.getOrElse("None")}, ${redirectURL.getOrElse("None")}, ${createdByUserId.getOrElse("None")})")
      sender ! (mapper.getOrCreateConsumer(consumerId, key, secret, aud, azp, iss, sub, isActive, name, appType, description, developerEmail, redirectURL, createdByUserId))

    case cc.populateMissingUUIDs() =>
      logger.debug("populateMissingUUIDs()")
      sender ! (mapper.populateMissingUUIDs())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


