package code.metrics

import java.util.Date

import code.bankconnectors.{OBPImplementedByPartialFunction, _}
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import code.api.util.APIUtil._


import scala.collection.immutable.Nil

object MappedAggregateMetrics extends AggregateMetrics {


//  override def getAllAggregateMetrics(queryParams: List[OBPQueryParam]): Box[Long] = {
override def getAllAggregateMetrics(queryParams: List[OBPQueryParam]): List[Double] = {

  val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedMetric.date, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedMetric.date, date) }.headOption

    val optionalParams: Seq[QueryParam[MappedMetric]] = Seq(
      fromDate.toSeq,
      toDate.toSeq
    ).flatten


    val totalCount = MappedMetric.count(optionalParams: _*)

    val dbQuery = "select avg(duration), min(duration), max(duration) from mappedmetric"

    val queryResult = DB.runQuery(dbQuery)

    val avgDuration = (queryResult._2(0))(0)
    val minDuration = (queryResult._2(0))(1)
    val maxDuration = (queryResult._2(0))(2)


    //Full(MappedMetric.count(optionalParams: _*))
  List(totalCount.toDouble, avgDuration.toDouble, minDuration.toDouble, maxDuration.toDouble)
  }

}
