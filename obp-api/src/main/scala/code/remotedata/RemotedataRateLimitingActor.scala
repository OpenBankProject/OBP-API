package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.ratelimiting.{MappedRateLimitingProvider, RemotedataRateLimitingCaseClasses}
import code.util.Helper.MdcLoggable

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataRateLimitingActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedRateLimitingProvider
  val cc = RemotedataRateLimitingCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getAll() =>
      logger.debug("getAll()")
      mapper.getAll() pipeTo sender
      
    case cc.getAllByConsumerId(consumerId: String, date: Option[Date]) =>
      logger.debug(s"getAllByConsumerId($consumerId, $date)")
      mapper.getAllByConsumerId(consumerId, date) pipeTo sender
      
    case cc.getByConsumerId(consumerId: String, apiVersion: String, apiName: String, date: Option[Date]) =>
      logger.debug(s"getByConsumerId($consumerId, $apiVersion, $apiName, $date)")
      mapper.getByConsumerId(consumerId, apiVersion, apiName, date) pipeTo sender

    case cc.createOrUpdateConsumerCallLimits(id: String, fromDate: Date, toDate: Date, apiVersion: Option[String], apiName: Option[String], bankId: Option[String],perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]) =>
      logger.debug(s"createOrUpdateConsumerCallLimits($id, $fromDate, $toDate, ${apiVersion.getOrElse("None")}, ${apiName.getOrElse("None")}, ${bankId.getOrElse("None")}, ${perSecond.getOrElse("None")}, ${perMinute.getOrElse("None")}, ${perHour.getOrElse("None")}, ${perDay.getOrElse("None")}, ${perWeek.getOrElse("None")}, ${perMonth.getOrElse("None")})")
      mapper.createOrUpdateConsumerCallLimits(id, fromDate, toDate, apiVersion, apiName, bankId, perSecond, perMinute, perHour, perDay, perWeek, perMonth) pipeTo sender
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


