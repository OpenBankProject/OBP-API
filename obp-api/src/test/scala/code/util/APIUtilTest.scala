/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.util

import code.api.Constant
import code.api.util.APIUtil.{DateWithMsFormat, DefaultToDate, theEpochTime, _}
import code.api.util.ErrorMessages._
import code.api.util._
import code.setup.PropsReset
import code.util.Helper.SILENCE_IS_GOLDEN
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.UserAuthContextCommons
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.{JValue, parse}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

class APIUtilTest extends FeatureSpec with Matchers with GivenWhenThen with PropsReset {

  val DefaultFromDateString = APIUtil.epochTimeString
  val DefaultToDateString = APIUtil.DefaultToDateString
  val startDateString = DefaultFromDateString
  val startDateStringWrongFormat = "Wrong Date Format"
  val endDateString = DefaultToDateString
  val endDateStringWrongFormat = "Wrong Date Format"
  val inputStringDateFormat = DateWithMsFormat
  val startDateObject: Date = DateWithMsFormat.parse(DefaultFromDateString)
  val endDateObject: Date = DateWithMsFormat.parse(DefaultToDateString)

  feature("Test the value of dateString formatted by DateWithMsFormat") {
    scenario("Check the formatted dateString value") {
      val dateString = inputStringDateFormat.format(new Date())
//      println(s"dateString value: $dateString")
      dateString should not be "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    }
  }
  
  ZonedDateTime.now(ZoneId.of("UTC"))

  feature("test APIUtil.dateRangesOverlap method") {
    
    val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
    val twoDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(2)
    val tomorrow = ZonedDateTime.now(ZoneId.of("UTC")).plusDays(2)
    val dayAfterTomorrow = ZonedDateTime.now(ZoneId.of("UTC")).plusDays(1)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
    
    scenario("Date intervals do not overlap"){
      val interval1 = DateInterval(Date.from(twoDayAgo.toInstant()), Date.from(oneDayAgo.toInstant()))
      val interval2 = DateInterval(Date.from(tomorrow.toInstant()), Date.from(dayAfterTomorrow.toInstant()))
      dateRangesOverlap(interval1, interval2) should be (false)
    }
    scenario("Date intervals overlap"){
      val interval1 = DateInterval(Date.from(twoDayAgo.toInstant()), Date.from(tomorrow.toInstant()))
      val interval2 = DateInterval(Date.from(oneDayAgo.toInstant()), Date.from(dayAfterTomorrow.toInstant()))
      dateRangesOverlap(interval1, interval2) should be (true)
    }
  }
  
  feature("test APIUtil.getHttpRequestUrlParam method") 
  {
    scenario("no parameters in the URL") 
    {
      val httpRequestUrl= "/obp/v3.1.0/management/metrics/top-consumers"
      val returnValue = getHttpRequestUrlParam(httpRequestUrl,"from_date")
      returnValue should be ("")
    }
    
    scenario(s"only one `from_date` in URL") 
    {
      val httpRequestUrl= s"/obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString"
      val startdateValue = getHttpRequestUrlParam(httpRequestUrl,"from_date")
      startdateValue should be (s"$startDateString")
    }
    
    
    scenario(s"Both `from_date` and `to_date` in URL") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString&to_date=$endDateString"
      val startdateValue = getHttpRequestUrlParam(httpRequestUrl,"from_date")
      startdateValue should be (s"$startDateString")
      val endDateValue = getHttpRequestUrlParam(httpRequestUrl,"to_date")
      endDateValue should be (s"$endDateString")
      val noneFieldValue = getHttpRequestUrlParam(httpRequestUrl,"none_field")
      noneFieldValue should be ("")
    }
    
    scenario(s"test some space in the URL, eg: /obp/v3.0.0/management/aggregate-metrics?app_name=API Manager Local Dev ") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.0.0/management/aggregate-metrics?app_name=API Manager Local Dev "
      val startdateValue = getHttpRequestUrlParam(httpRequestUrl,"app_name")
      startdateValue should be (s"API Manager Local Dev ")
    }
    
    
    scenario(s"test the error case, eg: not proper parameter name") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString&to_date=$endDateString"
      val noneFieldValue = getHttpRequestUrlParam(httpRequestUrl,"none_field")
      noneFieldValue should be ("")
    }
  } 
  
  feature("test APIUtil.getHttpValues method") 
  {
    scenario("test the one value case in HTTPParam , eg: (one name : one value)") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date",s"$DateWithMsExampleString"))
      val returnValue = getHttpValues(httpParams, "from_date")
      returnValue should be (List(s"$DateWithMsExampleString"))
    }
    
    scenario(s"test the many values case in HTTPParam, eg (one name : value1,value2,value3)") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List(s"$DateWithMsExampleString",s"$DateWithMsExampleString")))
      val returnValue = getHttpValues(httpParams, "from_date")
      returnValue should be (List(s"$DateWithMsExampleString",s"$DateWithMsExampleString"))
    }
    
    
    scenario(s"test the many values case in HTTPParam, eg (exclude_app_names : value1,value2,value3)") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("exclude_app_names", List("value1","value2", "value3")))
      val returnValue = getHttpValues(httpParams, "exclude_app_names")
      returnValue should be (List("value1","value2", "value3"))
    }
    
    scenario(s"test error cases, get wrong name ") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List(s"$DateWithMsExampleString",s"$DateWithMsExampleString")))
      val returnValue = getHttpValues(httpParams, "wrongName")
      returnValue should be (Empty)
    }
    
    scenario(s"test None case, httpParams == Empty ") 
    {
      val httpParams: List[HTTPParam] = List.empty[HTTPParam]
      val returnValue = getHttpValues(httpParams, "wrongName")
      returnValue should be (Empty)
    }
  }
  
  feature("test APIUtil.parseObpStandardDate method") 
  {
    scenario(s"test the correct format- DateWithMsFormat") 
    {
      val correctDateFormatString = DateWithMsExampleString
      val returnValue: Box[Date] = parseObpStandardDate(correctDateFormatString)
      returnValue.isDefined should be (true)
      returnValue.openOrThrowException("") should be (DateWithMsFormat.parse(correctDateFormatString))
    }
    
    scenario(s"test the correct format- DateWithMsRollbackFormat") 
    {
      val correctDateFormatString = DateWithMsRollbackExampleString
      val returnValue: Box[Date] = parseObpStandardDate(correctDateFormatString)
      returnValue should be (Full(DateWithMsRollbackFormat.parse(correctDateFormatString)))
    }
    
    
    scenario(s"test the wrong data format") 
    {
      val returnValue: Box[Date] = parseObpStandardDate("2001.07-01T00:00:00.000+0000")
      returnValue.isDefined should be (false)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
  }
  
  feature("test APIUtil.getSortDirection method") 
  {
    scenario(s"test the correct case: ASC or DESC") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("sort_direction", List("ASC")))
      val returnValue = getSortDirection(httpParams)
      returnValue.isDefined should be (true)
      returnValue.openOrThrowException("") should be (OBPAscending)
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("sort_direction", List("wrongValue")))
      val returnValue = getSortDirection(httpParams)
      returnValue.toString contains FilterSortDirectionError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam. It will return the default Sort Direction = DESC ") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("ASC")))
      val returnValue = getSortDirection(httpParams)
      returnValue should be (Full(OBPDescending))
    }
  }

  implicit val fromDateOrdering = new Ordering[OBPFromDate] {
    override def compare(x: OBPFromDate, y: OBPFromDate): Int = if (x.value.after(y.value)) {
      1
    } else if(y.value.after(x.value)) {
      -1
    } else {
      0
    }
  }

  feature("test APIUtil.getFromDate method") 
  {
    scenario(s"test the correct case") 
    {
      val correctDateFormatString = s"$DateWithMsExampleString"
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List(correctDateFormatString)))
      val returnValue = getFromDate(httpParams)
      returnValue should be (Full(OBPFromDate(DateWithMsFormat.parse(correctDateFormatString))))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List("wrongValue")))
      val returnValue = getFromDate(httpParams)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
    
    scenario("test the wrong case: wrong name (wrongName) in HTTPParam")
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List(s"$DateWithMsExampleString")))
      val startTime = OBPFromDate(theEpochTime)
      val returnValue = getFromDate(httpParams)
      returnValue.toString should startWith("Full(OBPFromDate")

      val currentTime = OBPFromDate(theEpochTime)
      val beWithinTolerance = be  >= startTime and be <= currentTime
      returnValue.orNull should beWithinTolerance
    }
    
    scenario("test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam")
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val startTime = OBPFromDate(theEpochTime)
      val returnValue = getFromDate(httpParams)
      returnValue.toString should startWith("Full(OBPFromDate")

      val currentTime = OBPFromDate(theEpochTime)
      val beWithinTolerance = be  >= startTime and be <= currentTime
      returnValue.orNull should beWithinTolerance
    }
  }

  implicit val toDateOrdering = new Ordering[OBPToDate] {
    override def compare(x: OBPToDate, y: OBPToDate): Int = if (x.value.after(y.value)) {
      1
    } else if(y.value.after(x.value)) {
      -1
    } else {
      0
    }
  }

  feature("test APIUtil.getToDate method") 
  {
    scenario(s"test the correct case") 
    {
      val correctDateFormatString = s"$DateWithMsExampleString"
      val httpParams: List[HTTPParam] = List(HTTPParam("to_date", List(correctDateFormatString)))
      val returnValue = getToDate(httpParams)
      returnValue should be (Full(OBPToDate(DateWithMsFormat.parse(correctDateFormatString))))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("to_date", List("wrongValue")))
      val returnValue = getToDate(httpParams)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List(s"$DateWithMsExampleString")))

      val startTime = OBPToDate(DefaultToDate)

      val returnValue = getToDate(httpParams)
      returnValue.toString should startWith("Full(OBPToDate")

      val currentTime = OBPToDate(ToDateInFuture)
      val beWithinTolerance = be  >= startTime and be <= currentTime
      returnValue.orNull should beWithinTolerance
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))

      val startTime = OBPToDate(DefaultToDate)

      val returnValue = getToDate(httpParams)
      returnValue.toString should startWith("Full(OBPToDate")

      val currentTime = OBPToDate(ToDateInFuture)
      val beWithinTolerance = be  >= startTime and be <= currentTime
      returnValue.orNull should beWithinTolerance
    }
  }
  
  feature("test APIUtil.getOffset method") 
  {
    scenario(s"test the correct case: offset = 100") 
    {
      val correctValue = "100"
      val httpParams: List[HTTPParam] = List(HTTPParam("offset", List(correctValue)))
      val returnValue = getOffset(httpParams)
      returnValue should be (Full(OBPOffset(100)))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("offset", List("wrongValue")))
      val returnValue = getOffset(httpParams)
      returnValue.toString contains FilterOffersetError should be (true)
      
      val httpParams2: List[HTTPParam] = List(HTTPParam("offset", List("-1")))
      val returnValue2 = getOffset(httpParams)
      returnValue2.toString contains FilterOffersetError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("100")))
      val returnValue = getOffset(httpParams)
      returnValue should be (OBPOffset(0))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = getOffset(httpParams)
      returnValue should be (OBPOffset(0))
    }
  }
  
  feature("test APIUtil.getLimit method") 
  {
    scenario(s"test the correct case: limit = 100") 
    {
      val correctValue = "100"
      val httpParams: List[HTTPParam] = List(HTTPParam("limit", List(correctValue)))
      val returnValue = getLimit(httpParams)
      returnValue should be (Full(OBPLimit(100)))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("limit", List("wrongValue")))
      val returnValue = getLimit(httpParams)
      returnValue.toString contains FilterLimitError should be (true)
      
      val httpParams2: List[HTTPParam] = List(HTTPParam("limit", List("-1")))
      val returnValue2 = getLimit(httpParams)
      returnValue2.toString contains FilterLimitError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("100")))
      val returnValue = getLimit(httpParams)
      returnValue should be (OBPLimit(Constant.Pagination.limit))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = getLimit(httpParams)
      returnValue should be (OBPLimit(Constant.Pagination.limit))
    }
  }
  
  feature("test APIUtil.getHttpParamValuesByName method") 
  {
    scenario(s"test the correct case, single value = anon") 
    {
      val correctValue = "true"
      val httpParams: List[HTTPParam] = List(HTTPParam("anon", List(correctValue)))
      val returnValue = getHttpParamValuesByName(httpParams, "anon")
      returnValue should be (Full(OBPAnon(true)))
    }
    
    scenario(s"test the correct case, exclude_app_names=API_EXPLOER,SOFIT") 
    {
      val correctValue = List("API_EXPLOER","SOFIT")
      val httpParams: List[HTTPParam] = List(HTTPParam("exclude_app_names", correctValue))
      val returnValue = getHttpParamValuesByName(httpParams, "exclude_app_names")
      returnValue should be (Full(OBPExcludeAppNames(correctValue)))
    }
    
    scenario(s"test the correct case2, multi values = anon,consumer_id") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("anon", "true"), HTTPParam("consumer_id", "1"))
      val returnValue = getHttpParamValuesByName(httpParams, "anon")
      returnValue should be (Full(OBPAnon(true)))
      val returnValue1 = getHttpParamValuesByName(httpParams, "consumer_id")
      returnValue1 should be (Full(OBPConsumerId("1")))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("anon", List("wrongValue")))
      val returnValue = getHttpParamValuesByName(httpParams, "anon")
      returnValue.toString contains FilterAnonFormatError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("true")))
      val returnValue = getHttpParamValuesByName(httpParams, "anon")
      returnValue should be (Full(OBPEmpty()))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = getHttpParamValuesByName(httpParams, "anon")
      returnValue should be (Full(OBPEmpty()))
    }
  }
  
  feature("test APIUtil.getHttpParams method") 
  {
    val RetrunDefaultParams = Full(List(OBPLimit(Constant.Pagination.limit),OBPOffset(0),OBPOrdering(None,OBPDescending), OBPFromDate(startDateObject),OBPToDate(endDateObject)))
    
    scenario(s"test the correct case1: with default parameters") 
    {
      val ExpectResult = RetrunDefaultParams 
      
      val httpParams: List[HTTPParam] = List(
        HTTPParam("from_date",List(s"$DefaultFromDateString")),
        HTTPParam("to_date",List(s"$DefaultToDateString"))
      )
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case2: contains the `anon` ") 
    {
      val ExpectResult = 
        Full(List(OBPLimit(Constant.Pagination.limit),OBPOffset(Constant.Pagination.offset),OBPOrdering(None,OBPDescending)
                  ,OBPFromDate(startDateObject),OBPToDate(endDateObject),
                  OBPAnon(true)))
      val httpParams: List[HTTPParam] = List(
        HTTPParam("from_date",List(s"$DefaultFromDateString")),
        HTTPParam("to_date",List(s"$DefaultToDateString")),
        HTTPParam("anon", "true")
      )
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case3: contains the `anon` and `consumer_id` ") 
    {
      val ExpectResult = 
        Full(List(OBPLimit(Constant.Pagination.limit),OBPOffset(Constant.Pagination.offset),OBPOrdering(None,OBPDescending),
             OBPFromDate(startDateObject),OBPToDate(endDateObject),
             OBPAnon(true),OBPConsumerId("1")))
      val httpParams: List[HTTPParam] = List(
        HTTPParam("from_date",List(s"$DefaultFromDateString")),
        HTTPParam("to_date",List(s"$DefaultToDateString")),
        HTTPParam("anon", "true"), 
        HTTPParam("consumer_id", "1")
      )
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case4: contains all the fields") 
    {
      val ExpectResult = 
        Full(List(OBPLimit(Constant.Pagination.limit), OBPOffset(Constant.Pagination.offset), OBPOrdering(None,OBPDescending),
                  OBPFromDate(startDateObject), OBPToDate(endDateObject),
                  OBPAnon(true), OBPConsumerId("1"), OBPUserId("2"), OBPUrl("obp/v1.2.1/getBanks"),
                  OBPAppName("PlaneApp"), OBPImplementedByPartialFunction("getBanks"),
                  OBPImplementedInVersion("v1.2.1"), OBPVerb("GET"), OBPCorrelationId("123"), OBPDuration(1000),
                  OBPExcludeAppNames(List("TrainApp", "BusApp")), OBPExcludeUrlPatterns(List("%/obp/v1.2.1%")),
                  OBPExcludeImplementedByPartialFunctions(List("getBank", "getAccounts")),
                  OBPIncludeAppNames(List("TrainApp", "BusApp")), OBPIncludeUrlPatterns(List("%/obp/v1.2.1%")),
                  OBPIncludeImplementedByPartialFunctions(List("getBank", "getAccounts"))))
      val httpParams: List[HTTPParam] = List(
        HTTPParam("from_date",List(s"$DefaultFromDateString")),
        HTTPParam("to_date",List(s"$DefaultToDateString")),
        HTTPParam("anon", "true"), 
        HTTPParam("consumer_id", "1"), 
        HTTPParam("user_id", "2"), 
        HTTPParam("url", "obp/v1.2.1/getBanks"), 
        HTTPParam("app_name","PlaneApp"),
        HTTPParam("implemented_by_partial_function","getBanks"),
        HTTPParam("implemented_in_version","v1.2.1"),
        HTTPParam("verb","GET"),
        HTTPParam("correlation_id","123"),
        HTTPParam("duration","1000"),
        HTTPParam("exclude_app_names",List("TrainApp","BusApp")),
        HTTPParam("exclude_url_patterns","%/obp/v1.2.1%"),
        HTTPParam("exclude_implemented_by_partial_functions",List("getBank","getAccounts")),
        HTTPParam("include_app_names",List("TrainApp","BusApp")),
        HTTPParam("include_url_patterns","%/obp/v1.2.1%"),
        HTTPParam("include_implemented_by_partial_functions",List("getBank","getAccounts"))
      )
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue should be (ExpectResult)
    }
    
    
    scenario(s"test the wrong case: values (wrongValue)- limit in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("limit", List("wrongValue")))
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue.toString contains FilterLimitError should be (true)
    }
    
    
    scenario(s"test the wrong case: wrong values - anon (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("anon", List("wrongValue")))
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue.toString contains FilterAnonFormatError should be (true)
    }
    
    
    scenario(s"test the wrong case: wrong values-offset(wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("offset", List("wrongValue")))
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue.toString contains FilterOffersetError should be (true)
      
      val httpParams2: List[HTTPParam] = List(HTTPParam("offset", List("-1")))
      val returnValue2 = createQueriesByHttpParams(httpParams)
      returnValue2.toString contains FilterOffersetError should be (true)
    }
    
    scenario(s"test the wrong case: wrong values - duration (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(
        HTTPParam("from_date",List(s"$DefaultFromDateString")),
        HTTPParam("to_date",List(s"$DefaultToDateString")),
        HTTPParam("duration", List("wrongValue"))
      )
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue.toString contains FilterDurationFormatError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(
        HTTPParam("from_date",List(s"$DefaultFromDateString")),
        HTTPParam("to_date",List(s"$DefaultToDateString")),
        HTTPParam("wrongName", List("true"))
      )
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue should be (RetrunDefaultParams)
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("to_date", List("wrongValue")))
      val returnValue = createQueriesByHttpParams(httpParams)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
    
  }
  
  feature("test APIUtil.createHttpParamsByUrl method") 
  {
    val RetrunDefaultParams = Full(List(OBPLimit(Constant.Pagination.limit),OBPOffset(Constant.Pagination.offset),OBPOrdering(None,OBPDescending), OBPFromDate(startDateObject),OBPToDate(endDateObject)))
    
    scenario(s"test the correct case1: all the params are in the `URL` ") 
    {
      val ExpectResult = Full(List(HTTPParam("sort_direction",List("ASC")), HTTPParam("from_date",List(s"$DateWithMsExampleString")), 
                                   HTTPParam("to_date",List(s"$DateWithMsExampleString")), HTTPParam("limit",List("10")), HTTPParam("offset",List("3")), 
                                   HTTPParam("anon",List("false")), HTTPParam("consumer_id",List("5")), HTTPParam("user_id",List("66214b8e-259e-44ad-8868-3eb47be70646")), 
                                   HTTPParam("url",List("/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions")), 
                                   HTTPParam("app_name",List("MapperPostman")), HTTPParam("implemented_by_partial_function",List("getTransactionsForBankAccount")), 
                                   HTTPParam("implemented_in_version",List("v3.0.0")), HTTPParam("verb",List("GET")), HTTPParam("correlation_id",List("123")), 
                                   HTTPParam("duration",List("100")), 
                                   HTTPParam("exclude_app_names",List("API-EXPLORER","API-Manager","SOFI","null","SOFIT")), 
                                   HTTPParam("exclude_url_patterns",List("%25management/metrics%25","%management/aggregate-metrics%")), 
                                   HTTPParam("exclude_implemented_by_partial_functions",List("getMetrics","getConnectorMetrics","getAggregateMetrics")),
                                   HTTPParam("include_app_names",List("API-EXPLORER","API-Manager","SOFI","SOFIT")), 
                                   HTTPParam("include_url_patterns",List("%25management/metrics%25","%management/aggregate-metrics%")), 
                                   HTTPParam("include_implemented_by_partial_functions",List("getMetrics","getConnectorMetrics","getAggregateMetrics")))) 
      
      val httpRequestUrl = "/obp/v3.0.0/management/aggregate-metrics?" +
        s"offset=3&" +
        s"limit=10&" +
        s"sort_direction=ASC&" +
        s"from_date=$DateWithMsExampleString&" +
        s"to_date=$DateWithMsExampleString&" +
        s"consumer_id=5&user_id=66214b8e-259e-44ad-8868-3eb47be70646&" +
        "implemented_by_partial_function=getTransactionsForBankAccount&" +
        "implemented_in_version=v3.0.0&" +
        "url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions&" +
        "verb=GET&" +
        "anon=false&" +
        "app_name=MapperPostman&" +
        "exclude_app_names=API-EXPLORER,API-Manager,SOFI,null,SOFIT&" +
        "exclude_url_patterns=%25management/metrics%25,%management/aggregate-metrics%&" +
        "exclude_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics&"+ 
        "correlation_id=123&duration=100&"+
        "include_app_names=API-EXPLORER,API-Manager,SOFI,SOFIT&" +
        "include_url_patterns=%25management/metrics%25,%management/aggregate-metrics%&" +
        "include_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics&"
      
      val returnValue = createHttpParamsByUrl(httpRequestUrl)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case2: no parameters in the Url ") 
    {
      val ExpectResult = Full(List())
      val httpRequestUrl = "/obp/v3.0.0/management/aggregate-metrics"
      val returnValue = createHttpParamsByUrl(httpRequestUrl)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case3: some params are in the `URL` ") 
    {
      val ExpectResult = Full(List(HTTPParam("sort_direction",List("ASC")), HTTPParam("from_date",List(s"$DateWithMsExampleString")), 
                                   HTTPParam("to_date",List(s"$DateWithMsExampleString")), HTTPParam("limit",List("10")), HTTPParam("offset",List("3")), 
                                   HTTPParam("consumer_id",List("5")), HTTPParam("user_id",List("66214b8e-259e-44ad-8868-3eb47be70646")), 
                                   HTTPParam("implemented_by_partial_function",List("getTransactionsForBankAccount")), 
                                   HTTPParam("implemented_in_version",List("v3.0.0"))))
      val httpRequestUrl = "/obp/v3.0.0/management/aggregate-metrics?" +
        s"offset=3&limit=10&sort_direction=ASC&from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5&user_id=66214b8e-259e-44ad-8868-3eb47be70646&" +
        "implemented_by_partial_function=getTransactionsForBankAccount&implemented_in_version=v3.0.0"
      val returnValue = createHttpParamsByUrl(httpRequestUrl)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case4: error case None in `=` right side ") 
    {
      val ExpectResult = Full(List())
      val httpRequestUrl = s"/obp/v3.0.0/management/aggregate-metrics?from_date="
      val returnValue = createHttpParamsByUrl(httpRequestUrl)
      returnValue should be (ExpectResult)
    }
    
    scenario(s"test the correct case4: include_app_names,include_url_patterns,include_implemented_by_partial_functions") 
    {
      val ExpectResult = Full(List(
        HTTPParam("include_app_names",List("API-EXPLORER","API-Manager")),
        HTTPParam("include_url_patterns", List("%25management/metrics%25", "%management/aggregate-metrics%")),
        HTTPParam("include_implemented_by_partial_functions", List("getMetrics"))))
      val httpRequestUrl = s"/obp/v3.0.0/management/aggregate-metrics?include_app_names=API-EXPLORER,API-Manager&include_url_patterns=%25management/metrics%25,%management/aggregate-metrics%&include_implemented_by_partial_functions=getMetrics"
      val returnValue = createHttpParamsByUrl(httpRequestUrl)
      returnValue should be (ExpectResult)
    }
  }

  feature("test APIUtil.firstCharToLowerCase method") {
    APIUtil.firstCharToLowerCase("ABC") should be ("aBC")
    APIUtil.firstCharToLowerCase("") should be ("")
    APIUtil.firstCharToLowerCase(null) should be ("")
    APIUtil.firstCharToLowerCase("aaaa") should be ("aaaa")
  }

  /**
   * should add the follow to test.default.props
   * ## should be "hello_foo_bar__good luck__"
   * hello.world=hello_${foo.bar}__good ${greeting.${compose.exp}}__
   * foo.bar=foo_bar
   * compose.exp=word
   * greeting.word=luck
   */
  feature("test APIUtil.getPropsValue support expression") {
    setPropsValues(
      "hello.world" -> "hello_${foo.bar}__good ${greeting.${compose.exp}}__",
      "foo.bar" -> "foo_bar",
      "compose.exp" -> "word",
      "greeting.word" -> "luck"
    )
    APIUtil.getPropsValue("hello.world") should be("hello_foo_bar__good luck__")
  }

  feature("test APIUtil.getObpFormatOperationId method") {
    APIUtil.getObpFormatOperationId("OBPv4_0_0-dynamicEntity_deleteFooBar33") should be ("OBPv4.0.0-dynamicEntity_deleteFooBar33")
    APIUtil.getObpFormatOperationId("OBPv3.0.0-getCoreAccountById") should be ("OBPv3.0.0-getCoreAccountById")
    APIUtil.getObpFormatOperationId("xxx") should be ("xxx")
  }
  
  feature("test APIUtil.basicUriAndQueryStringValidation method") {
    val testString1 = "https%3A%2F%2Fapisandbox.openbankproject.com%2Foauth%2Fauthorize%3Fnext%3D%2Fen%2Fusers%2Fmyuser%26oauth_token%3DWTOBT2YRCTMI1BCCF4XAIKRXPLLZDZPFAIL5K03Z%26oauth_verifier%3D45381"
    val testString2 = "http%3A%2F%2Flocalhost%3A8016%3Foauth_token%3DEBRZBMOPDXEUGGJP421FPFGK01IY2DGM5O3TLVSK%26oauth_verifier%3D63461"
    val testString3 = "myapp://callback?oauth_token=%3DEBRZBMOPDXEUGGJP421FPFGK01IY2DGM5O3TLVSK%26oauth_verifier%3D63461"
    val testString4 = "fb00000000:://callback?oauth_token=%3DEBRZBMOPDXEUGGJP421FPFGK01IY2DGM5O3TLVSK%26oauth_verifier%3D63461"
    val testString5 = "http://127.0.0.1:8000/oauth/authorize?next=/en/metrics/api/&oauth_token=TN0124OCPRCL4KUJRF5LNLVMRNHTVZPJDBS2PNWU&oauth_verifier=10470"
    
    APIUtil.basicUriAndQueryStringValidation(testString1) should be (true)
    APIUtil.basicUriAndQueryStringValidation(testString2) should be (true)
    APIUtil.basicUriAndQueryStringValidation(testString3) should be (true)
    APIUtil.basicUriAndQueryStringValidation(testString4) should be (true)
    APIUtil.basicUriAndQueryStringValidation(testString5) should be (true)
    
  }

  feature("test APIUtil.getBankIdAccountIdPairsFromUserAuthContexts method") {

    scenario(s"Test the Success cases") {
      val userAuthContexts = List(UserAuthContextCommons(
        userAuthContextId = "",
        userId = "",
        key = "BANK_ID::::CUSTOMER_NUMBER",
        value = "bank_id1::::customer_number1",
        timeStamp = new Date(0),
        consumerId = ""
      ), UserAuthContextCommons(
        userAuthContextId = "",
        userId = "",
        key = "BANK_ID::::CUSTOMER_NUMBER",
        value = "bank_id2::::customer_number2",
        timeStamp = new Date(0),
        consumerId = ""
      ), UserAuthContextCommons(
        userAuthContextId = "",
        userId = "",
        key = "BANK_ID::::CUSTOMER_NUMBER",
        value = "bank_id3::::customer_number3",
        timeStamp = new Date(0),
        consumerId = ""
      ))

      val expectedValue = Set(("bank_id1", "customer_number1"), ("bank_id2", "customer_number2"), ("bank_id3", "customer_number3"))
      val actualValue = APIUtil.getBankIdAccountIdPairsFromUserAuthContexts(userAuthContexts)
      actualValue should be(expectedValue)
    }

    scenario(s"Test the Empty cases") {
      val userAuthContexts = List(UserAuthContextCommons(
        userAuthContextId = "",
        userId = "",
        key = "BANK_ID1::::CUSTOMER_NUMBER",
        value = "bank_id1::::customer_number1",
        timeStamp = new Date(0),
        consumerId = ""
      ), UserAuthContextCommons(
        userAuthContextId = "",
        userId = "",
        key = "BANK_ID:::CUSTOMER_NUMBER",
        value = "bank_id2::::customer_number2",
        timeStamp = new Date(0),
        consumerId = ""
      ))

      val expectedValue = Set()
      val actualValue = APIUtil.getBankIdAccountIdPairsFromUserAuthContexts(userAuthContexts)
      actualValue should be(expectedValue)
    }

    scenario(s"Test the getAllObpIdKeyValuePairs method") {
      val json: JValue = parse(
        """{
          |  "account_id": "1",
          |  "id": "2",
          |  "customer_id": "3",
          |  "age": 1,
          |  "catchphrase": {
          |    "one": {
          |      "atm_id": "4",
          |      "value": {
          |        "one": {
          |          "one": {
          |            "bank_id": "5",
          |            "transaction_id": "6"
          |          },
          |          "user_id": "7"
          |        },
          |        "id":"8"
          |      }
          |    },
          |    "card_id": "9"
          |  }
          |}""".stripMargin)
      
      val actualValue = APIUtil.getAllObpIdKeyValuePairs(json)
      val expectedValue= List(
        ("account_id","1"),
        ("id","2"),
        ("customer_id","3"),
        ("atm_id","4"),
        ("bank_id","5"),
        ("transaction_id","6"),
        ("user_id","7"),
        ("id","8"),
        ("card_id","9")
      )
      actualValue should be(expectedValue) 
    }
    

    scenario(s"Test the checkObpId method") {
      val id1 = "gh.29.uk"
      val id2 = "1313_.121"
      val id3 = APIUtil.generateUUID()
      val id7 = "" //the empty string

      //      error cases
      val id4 = "+123313" //do not support +
      val id5 = "@#$" //do not support @#$
      val id6 = APIUtil.generateUUID() +"1" //the max length is 36

      val actualValue1 = APIUtil.checkObpId(id1)
      val actualValue2 = APIUtil.checkObpId(id2)
      val actualValue3 = APIUtil.checkObpId(id3)

      val actualValue4 = APIUtil.checkObpId(id4)
      val actualValue5 = APIUtil.checkObpId(id5)
      val actualValue6 = APIUtil.checkObpId(id6)
      val actualValue7 = APIUtil.checkObpId(id7)
      
      
      SILENCE_IS_GOLDEN should be(actualValue1)
      SILENCE_IS_GOLDEN should be(actualValue2)
      SILENCE_IS_GOLDEN should be(actualValue3)
      SILENCE_IS_GOLDEN should be(actualValue7)

      actualValue4 contains (InvalidValueCharacters) shouldBe (true)
      actualValue5 contains (InvalidValueCharacters) shouldBe (true)
      actualValue6 contains (InvalidValueLength) shouldBe (true)
      
    }

  }

  feature(s"test ${nameOf(APIUtil.basicPasswordValidation _)} and ${nameOf(APIUtil.fullPasswordValidation _)}") {
    
    scenario(s"Test the ${nameOf(APIUtil.basicPasswordValidation _)} method") {
      val firefoxStrongPasswordProposal = "9YF]gZnXzAENM+]"

      basicPasswordValidation(firefoxStrongPasswordProposal) shouldBe (SILENCE_IS_GOLDEN) //  SILENCE_IS_GOLDEN
      basicPasswordValidation("Abc!123 xyz") shouldBe (SILENCE_IS_GOLDEN) //  SILENCE_IS_GOLDEN
      basicPasswordValidation("SuperStrong#123") shouldBe (SILENCE_IS_GOLDEN) //  SILENCE_IS_GOLDEN
      basicPasswordValidation("Hello World!") shouldBe (SILENCE_IS_GOLDEN) //  SILENCE_IS_GOLDEN
      basicPasswordValidation(" ") shouldBe (SILENCE_IS_GOLDEN) //  SILENCE_IS_GOLDEN allow space so far

      basicPasswordValidation("shortá") shouldBe (InvalidValueCharacters) //  ErrorMessages.InvalidValueCharacters
      basicPasswordValidation("a" * 513) shouldBe (InvalidValueLength) //  ErrorMessages.InvalidValueLength 

    }

    scenario(s"Test the ${nameOf(APIUtil.fullPasswordValidation _)}  method") {
      val firefoxStrongPasswordProposal = "9YF]gZnXzAENM+]"

      fullPasswordValidation(firefoxStrongPasswordProposal) shouldBe true//  true
      fullPasswordValidation("Abcd!123xyz") shouldBe true //  true
      fullPasswordValidation("SuperStrong#123") shouldBe true //  true
      fullPasswordValidation("Abcdefgh!1") shouldBe true //  true
      fullPasswordValidation("short1!") shouldBe false //  false（too short）
      fullPasswordValidation("alllowercase123!") shouldBe false //  false（no capital letter）
      fullPasswordValidation("ALLUPPERCASE123!") shouldBe false//  false（no smaller case letter）
      fullPasswordValidation("NoSpecialChar123") shouldBe false//  false（not special character）
    }
    
  }

}