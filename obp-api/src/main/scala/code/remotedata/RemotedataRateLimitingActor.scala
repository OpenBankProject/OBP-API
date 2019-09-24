package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.ratelimiting.{MappedRateLimitingProvider, RemotedataRateLimitingCaseClasses}
import code.util.Helper.MdcLoggable

import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataRateLimitingActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedRateLimitingProvider
  val cc = RemotedataRateLimitingCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getAll() =>
      logger.debug("getAll()")
      mapper.getAll() pipeTo sender

    case cc.createOrUpdateConsumerCallLimits(id: String, perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]) =>
      logger.debug("createOrUpdateConsumerCallLimits(" + id + ", " + perSecond.getOrElse("None")+ ", " + perMinute.getOrElse("None") + ", " + perHour.getOrElse("None") + ", " + perDay.getOrElse("None") + ", " + perWeek.getOrElse("None") + ", " + perMonth.getOrElse("None") + ")")
      mapper.createOrUpdateConsumerCallLimits(id, perSecond, perMinute, perHour, perDay, perWeek, perMonth) pipeTo sender
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


