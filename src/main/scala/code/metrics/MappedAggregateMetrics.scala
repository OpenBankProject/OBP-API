package code.metrics

import java.util.Date
import net.liftweb.mapper._

object MappedAggregateMetrics extends AggregateMetrics {
  override def getAllAggregateMetrics(startDate: Date, endDate: Date): List[Double] = {
    //val fromDate = startDate
    //val toDate = endDate

    val dbQuery = s"SELECT count(*), avg(duration), min(duration), max(duration) FROM mappedmetric WHERE date_c >= '$startDate' AND date_c <= '$endDate'"
    val queryResult = DB.runQuery(dbQuery)

    val totalCount = (queryResult._2(0))(0)
    val avgDuration = (queryResult._2(0))(1)
    val minDuration = (queryResult._2(0))(2)
    val maxDuration = (queryResult._2(0))(3)

    //Full(MappedMetric.count(optionalParams: _*))
    //
    List(totalCount.toDouble, avgDuration.toDouble, minDuration.toDouble, maxDuration.toDouble)
  }

}