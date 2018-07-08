package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.bankconnectors.OBPQueryParam
import code.customer.{AmountOfMoneyTrait => _}
import code.metrics._
import net.liftweb.common.Box
import scala.concurrent.Future


object RemotedataMetrics extends ObpActorInit with APIMetrics {

  val cc = RemotedataMetricsCaseClasses

  def saveMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, consumerId: String, implementedByPartialFunction: String, implementedInVersion: String, verb: String, correlationId: String) : Unit =
    extractFuture(actor ? cc.saveMetric(userId, url, date, duration, userName, appName, developerEmail, consumerId, implementedByPartialFunction, implementedInVersion, verb, correlationId))

//  def getAllGroupedByUrl() : Map[String, List[APIMetric]] =
//    extractFuture(actor ? cc.getAllGroupedByUrl())
//
//  def getAllGroupedByDay() : Map[Date, List[APIMetric]] =
//    extractFuture(actor ? cc.getAllGroupedByDay())
//
//  def getAllGroupedByUserId() : Map[String, List[APIMetric]] =
//    extractFuture(actor ? cc.getAllGroupedByUserId())

  def getAllMetrics(queryParams: List[OBPQueryParam]): List[APIMetric] =
    extractFuture(actor ? cc.getAllMetrics(queryParams))
  
  override def getAllAggregateMetricsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AggregateMetrics]]] ={
    logger.debug(s"RemotedataMetrics.getAllAggregateMetrics($queryParams)")
    (actor ? cc.getAllAggregateMetricsFuture(queryParams)).mapTo[Box[List[AggregateMetrics]]]
  }
  
  override def getTopApisFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopApi]]] ={
    (actor ? cc.getTopApisFuture(queryParams: List[OBPQueryParam])).mapTo[Box[List[TopApi]]]
  }

  override def getTopConsumersFuture(queryParams: List[OBPQueryParam]): Future[Box[List[TopConsumer]]]  ={
    (actor ? cc.getTopConsumersFuture(queryParams)).mapTo[Box[List[TopConsumer]]]
  }
  def bulkDeleteMetrics(): Boolean =
    extractFuture(actor ? cc.bulkDeleteMetrics())


}
