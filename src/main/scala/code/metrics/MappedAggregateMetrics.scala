package code.metrics

import java.util.Date
import net.liftweb.mapper._

object MappedAggregateMetrics extends AggregateMetrics {
  override def getAllAggregateMetrics(startDate: Date, endDate: Date): List[Double] = {

    val dbQuery = s"SELECT count(*), avg(duration), min(duration), max(duration) FROM mappedmetric WHERE date_c >= '$startDate' AND date_c <= '$endDate'"
    val queryResult = DB.runQuery(dbQuery)

    val totalCount = (queryResult._2(0))(0)
    val avgResponseTime = "%.2f".format(((queryResult._2(0))(1)).toDouble)
    val minResponseTime = (queryResult._2(0))(2)
    val maxResponseTime = (queryResult._2(0))(3)


    List(totalCount.toDouble, avgResponseTime.toDouble, minResponseTime.toDouble, maxResponseTime.toDouble)
  }

}