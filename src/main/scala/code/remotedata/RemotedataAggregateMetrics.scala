package code.remotedata

import java.util.Date
import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metrics._


object RemotedataAggregateMetrics extends ObpActorInit with AggregateMetrics {

  val cc = RemotedataAggregateMetricsCaseClasses

  def getAllAggregateMetrics(startDate: Date, endDate: Date): List[Double] =
    extractFuture(actor ? cc.getAllAggregateMetrics(startDate: Date, endDate: Date))
}
