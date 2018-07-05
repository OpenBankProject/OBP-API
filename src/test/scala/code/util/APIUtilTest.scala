/**
  * Open Bank Project - API
  * Copyright (C) 2011-2016, TESOBE Ltd
  **
  *This program is free software: you can redistribute it and/or modify
  *it under the terms of the GNU Affero General Public License as published by
  *the Free Software Foundation, either version 3 of the License, or
  *(at your option) any later version.
  **
  *This program is distributed in the hope that it will be useful,
  *but WITHOUT ANY WARRANTY; without even the implied warranty of
  *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *GNU Affero General Public License for more details.
  **
  *You should have received a copy of the GNU Affero General Public License
*along with this program.  If not, see <http://www.gnu.org/licenses/>.
  **
 *Email: contact@tesobe.com
*TESOBE Ltd
*Osloerstrasse 16/17
*Berlin 13359, Germany
  **
 *This product includes software developed at
  *TESOBE (http://www.tesobe.com/)
  * by
  *Simon Redfern : simon AT tesobe DOT com
  *Stefan Bethge : stefan AT tesobe DOT com
  *Everett Sochowski : everett AT tesobe DOT com
  *Ayoub Benali: ayoub AT tesobe DOT com
  *
 */

package code.util

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil
import code.api.util.APIUtil.{DateWithMsFormat, DefaultFromDate, DefaultToDate}
import code.api.util.ErrorMessages._
import code.bankconnectors._
import code.metrics.OBPUrlDateQueryParam
import code.util.Helper.MdcLoggable
import net.liftweb.common
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.provider.HTTPParam
import org.scalatest.{FeatureSpec, Matchers}

class APIUtilTest extends FeatureSpec with Matchers with MdcLoggable  {
  
  val startDateString = "2010-05-10T01:20:03"
  val startDateStringWrongFormat = "2010-05-10 01:20:03"
  val endDateString = "2017-05-22T01:02:03"
  val endDateStringWrongFormat = "2017-05-22 01:02:03"
  val inputStringDateFormat = APIUtil.DateWithSecondsFormat
  val startDateObject: Date = inputStringDateFormat.parse(startDateString)
  val endDateObject: Date = inputStringDateFormat.parse(endDateString)
  
  feature("test APIUtil.getHttpRequestUrlParams method") 
  {
    scenario("no parameters in the URL") 
    {
      val httpRequestUrl= "/obp/v3.1.0/management/metrics/top-consumers"
      val obpQueryParamPlain = APIUtil.getHttpRequestUrlParams(httpRequestUrl)
      obpQueryParamPlain should be (Full(OBPUrlDateQueryParam(None,None)))
    }

    scenario(s"only one `from_date` in URL") 
    {
      val httpRequestUrl= s"/obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString"
      val obpQueryParamPlain = APIUtil.getHttpRequestUrlParams(httpRequestUrl)
      obpQueryParamPlain should be (Full(OBPUrlDateQueryParam(Some(startDateObject), None)))
    }

    scenario(s"only one `to_date` in URL") 
    {
      val httpRequestUrl= s"/obp/v3.1.0/management/metrics/top-consumers?to_date=$endDateString"
      val obpQueryParamPlain = APIUtil.getHttpRequestUrlParams(httpRequestUrl)
      obpQueryParamPlain should be (Full(OBPUrlDateQueryParam(None,Some(endDateObject))))
    }

    scenario(s"Both `from_date` and `to_date` in URL") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString&to_date=$endDateString"
      val obpQueryParamPlain = APIUtil.getHttpRequestUrlParams(httpRequestUrl)
      obpQueryParamPlain should be (Full(OBPUrlDateQueryParam((Some(startDateObject)),Some(endDateObject))))
    }


    scenario(s" exceptions in date format, it should return failure ") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateStringWrongFormat&to_date=$endDateString"
      val obpQueryParamPlain = APIUtil.getHttpRequestUrlParams(httpRequestUrl)
      obpQueryParamPlain.toString contains (InvalidDateFormat)  should be (true)

      val httpRequestUrl2= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString&to_date=$endDateStringWrongFormat"
      val obpQueryParamPlain2 = APIUtil.getHttpRequestUrlParams(httpRequestUrl)
      obpQueryParamPlain2.toString contains (InvalidDateFormat)  should be (true)
    }
  } 

  feature("test APIUtil.getHttpRequestUrlParam method") 
  {
    scenario("no parameters in the URL") 
    {
      val httpRequestUrl= "/obp/v3.1.0/management/metrics/top-consumers"
      val returnValue = APIUtil.getHttpRequestUrlParam(httpRequestUrl,"from_date")
      returnValue should be (Empty)
    }
    
    scenario(s"only one `from_date` in URL") 
    {
      val httpRequestUrl= s"/obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString"
      val startdateValue = APIUtil.getHttpRequestUrlParam(httpRequestUrl,"from_date")
      startdateValue should be (Full(s"$startDateString"))
    }
    
    
    scenario(s"Both `from_date` and `to_date` in URL") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString&to_date=$endDateString"
      val startdateValue = APIUtil.getHttpRequestUrlParam(httpRequestUrl,"from_date")
      startdateValue should be (Full(s"$startDateString"))
      val endDateValue = APIUtil.getHttpRequestUrlParam(httpRequestUrl,"to_date")
      endDateValue should be (Full(s"$endDateString"))
      val noneFieldValue = APIUtil.getHttpRequestUrlParam(httpRequestUrl,"none_field")
      noneFieldValue should be (Empty)
    }
    
    scenario(s"test the error case, eg: not proper parameter name") 
    {
      val httpRequestUrl= s"httpRequestUrl = /obp/v3.1.0/management/metrics/top-consumers?from_date=$startDateString&to_date=$endDateString"
      val noneFieldValue = APIUtil.getHttpRequestUrlParam(httpRequestUrl,"none_field")
      noneFieldValue should be (Empty)
    }
  } 
  
  feature("test APIUtil.getHttpValues method") 
  {
    scenario("test the one value case in HTTPParam , eg: (one name : one value)") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date","2001-07-01T00:00:00.000Z"))
      val returnValue = APIUtil.getHttpValues(httpParams, "from_date")
      returnValue should be (List("2001-07-01T00:00:00.000Z"))
    }
    
    scenario(s"test the many values case in HTTPParam, eg (one name : value1,value2,value3)") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List("2001-07-01T00:00:00.000Z","2002-07-01T00:00:00.000Z")))
      val returnValue = APIUtil.getHttpValues(httpParams, "from_date")
      returnValue should be (List("2001-07-01T00:00:00.000Z","2002-07-01T00:00:00.000Z"))
    }
    
    scenario(s"test error cases, get wrong name ") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List("2001-07-01T00:00:00.000Z","2002-07-01T00:00:00.000Z")))
      val returnValue = APIUtil.getHttpValues(httpParams, "wrongName")
      returnValue should be (Empty)
    }
    
    scenario(s"test None case, httpParams == Empty ") 
    {
      val httpParams: List[HTTPParam] = List.empty[HTTPParam]
      val returnValue = APIUtil.getHttpValues(httpParams, "wrongName")
      returnValue should be (Empty)
    }
  }
  
  feature("test APIUtil.parseObpStandardDate method") 
  {
    scenario(s"test the correct format") 
    {
      val correctDateFormatString = "2001-07-01T00:00:00.000Z"
      val returnValue: Box[Date] = APIUtil.parseObpStandardDate(correctDateFormatString)
      returnValue.isDefined should be (true)
      returnValue.openOrThrowException("") should be (DateWithMsFormat.parse(correctDateFormatString))
    }
    
    scenario(s"test the wrong data format") 
    {
      val returnValue: Box[Date] = APIUtil.parseObpStandardDate("2001-07-01T00:00:00.000+0000")
      returnValue.isDefined should be (false)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
  }
  
  feature("test APIUtil.getSortDirection method") 
  {
    scenario(s"test the correct case: ASC or DESC") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("sort_direction", List("ASC")))
      val returnValue = APIUtil.getSortDirection(httpParams)
      returnValue.isDefined should be (true)
      returnValue.openOrThrowException("") should be (OBPAscending)
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("sort_direction", List("wrongValue")))
      val returnValue = APIUtil.getSortDirection(httpParams)
      returnValue.toString contains FilterSortDirectionError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam. It will return the default Sort Direction = DESC ") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("ASC")))
      val returnValue = APIUtil.getSortDirection(httpParams)
      returnValue should be (Full(OBPDescending))
    }
  }
  
  feature("test APIUtil.getFromDate method") 
  {
    scenario(s"test the correct case") 
    {
      val correctDateFormatString = "2001-07-01T00:00:00.000Z"
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List(correctDateFormatString)))
      val returnValue = APIUtil.getFromDate(httpParams)
      returnValue should be (Full(OBPFromDate(DateWithMsFormat.parse(correctDateFormatString))))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("from_date", List("wrongValue")))
      val returnValue = APIUtil.getFromDate(httpParams)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("2001-07-01T00:00:00.000Z")))
      val returnValue = APIUtil.getFromDate(httpParams)
      returnValue should be (OBPFromDate(DefaultFromDate))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = APIUtil.getFromDate(httpParams)
      returnValue should be (OBPFromDate(DefaultFromDate))
    }
  }
  
  feature("test APIUtil.getToDate method") 
  {
    scenario(s"test the correct case") 
    {
      val correctDateFormatString = "2001-07-01T00:00:00.000Z"
      val httpParams: List[HTTPParam] = List(HTTPParam("to_date", List(correctDateFormatString)))
      val returnValue = APIUtil.getToDate(httpParams)
      returnValue should be (Full(OBPToDate(DateWithMsFormat.parse(correctDateFormatString))))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("to_date", List("wrongValue")))
      val returnValue = APIUtil.getToDate(httpParams)
      returnValue.toString contains FilterDateFormatError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("2001-07-01T00:00:00.000Z")))
      val returnValue = APIUtil.getToDate(httpParams)
      returnValue should be (OBPToDate(DefaultToDate))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = APIUtil.getToDate(httpParams)
      returnValue should be (OBPToDate(DefaultToDate))
    }
  }
  
  feature("test APIUtil.getOffset method") 
  {
    scenario(s"test the correct case: offset = 100") 
    {
      val correcOffset = "100"
      val httpParams: List[HTTPParam] = List(HTTPParam("offset", List(correcOffset)))
      val returnValue = APIUtil.getOffset(httpParams)
      returnValue should be (Full(OBPOffset(100)))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("offset", List("wrongValue")))
      val returnValue = APIUtil.getOffset(httpParams)
      returnValue.toString contains FilterOffersetError should be (true)
      
      val httpParams2: List[HTTPParam] = List(HTTPParam("offset", List("-1")))
      val returnValue2 = APIUtil.getOffset(httpParams)
      returnValue2.toString contains FilterOffersetError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("100")))
      val returnValue = APIUtil.getOffset(httpParams)
      returnValue should be (OBPOffset(0))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = APIUtil.getOffset(httpParams)
      returnValue should be (OBPOffset(0))
    }
  }
  
  feature("test APIUtil.getLimit method") 
  {
    scenario(s"test the correct case: limit = 100") 
    {
      val correcOffset = "100"
      val httpParams: List[HTTPParam] = List(HTTPParam("limit", List(correcOffset)))
      val returnValue = APIUtil.getLimit(httpParams)
      returnValue should be (Full(OBPLimit(100)))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("limit", List("wrongValue")))
      val returnValue = APIUtil.getLimit(httpParams)
      returnValue.toString contains FilterLimitError should be (true)
      
      val httpParams2: List[HTTPParam] = List(HTTPParam("limit", List("-1")))
      val returnValue2 = APIUtil.getLimit(httpParams)
      returnValue2.toString contains FilterLimitError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("100")))
      val returnValue = APIUtil.getLimit(httpParams)
      returnValue should be (OBPLimit(50))
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = APIUtil.getLimit(httpParams)
      returnValue should be (OBPLimit(50))
    }
  }
  
  feature("test APIUtil.getAnon method") 
  {
    scenario(s"test the correct case: limit = 100") 
    {
      val correcOffset = "true"
      val httpParams: List[HTTPParam] = List(HTTPParam("anon", List(correcOffset)))
      val returnValue = APIUtil.getAnon(httpParams)
      returnValue should be (Full(OBPAnon(true)))
    }
    
    scenario(s"test the wrong case: wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("anon", List("wrongValue")))
      val returnValue = APIUtil.getAnon(httpParams)
      returnValue.toString contains FilterAnonFormatError should be (true)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("true")))
      val returnValue = APIUtil.getAnon(httpParams)
      returnValue should be (common.Empty)
    }
    
    scenario(s"test the wrong case: wrong name (wrongName) and wrong values (wrongValue) in HTTPParam") 
    {
      val httpParams: List[HTTPParam] = List(HTTPParam("wrongName", List("wrongValue")))
      val returnValue = APIUtil.getAnon(httpParams)
      returnValue should be (common.Empty)
    }
  }
}