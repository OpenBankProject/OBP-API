package code.api.util

import java.util.Date

import code.api.util.APIUtil._
import net.liftweb.common.Box
import net.liftweb.http.provider.HTTPParam
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List

class OBPQueryParam
trait OBPOrder { def orderValue : Int }
object OBPOrder {
  def apply(s: Option[String]): OBPOrder = s match {
    case Some("asc") => OBPAscending
    case Some("ASC")=> OBPAscending
    case _ => OBPDescending
  }
}
object OBPAscending extends OBPOrder { def orderValue = 1 }
object OBPDescending extends OBPOrder { def orderValue = -1}
case class OBPLimit(value: Int) extends OBPQueryParam
case class OBPOffset(value: Int) extends OBPQueryParam
case class OBPFromDate(value: Date) extends OBPQueryParam
case class OBPToDate(value: Date) extends OBPQueryParam
case class OBPOrdering(field: Option[String], order: OBPOrder) extends OBPQueryParam
case class OBPConsumerId(value: String) extends OBPQueryParam
case class OBPUserId(value: String) extends OBPQueryParam
case class OBPBankId(value: String) extends OBPQueryParam
case class OBPAccountId(value: String) extends OBPQueryParam
case class OBPUrl(value: String) extends OBPQueryParam
case class OBPAppName(value: String) extends OBPQueryParam
case class OBPExcludeAppNames(values: List[String]) extends OBPQueryParam
case class OBPImplementedByPartialFunction(value: String) extends OBPQueryParam
case class OBPImplementedInVersion(value: String) extends OBPQueryParam
case class OBPVerb(value: String) extends OBPQueryParam
case class OBPAnon(value: Boolean) extends OBPQueryParam
case class OBPCorrelationId(value: String) extends OBPQueryParam
case class OBPDuration(value: Long) extends OBPQueryParam
case class OBPExcludeUrlPatterns(values: List[String]) extends OBPQueryParam
case class OBPExcludeImplementedByPartialFunctions(value: List[String]) extends OBPQueryParam
case class OBPFunctionName(value: String) extends OBPQueryParam
case class OBPConnectorName(value: String) extends OBPQueryParam
case class OBPEmpty() extends OBPQueryParam
case class OBPCustomerId(value: String) extends OBPQueryParam
case class OBPLockedStatus(value: String) extends OBPQueryParam

object OBPQueryParam {
  val LIMIT = "limit"
  val OFFSET = "offset"
  val FROM_DATE = "fromDate"
  val TO_DATE = "toDate"

  private val defaultFromDate = APIUtil.DateWithMsFormat.format(APIUtil.DefaultFromDate)
  private val defaultToDate = APIUtil.DateWithMsFormat.format(APIUtil.DefaultToDate)

  def getLimit(queryParams: List[OBPQueryParam]) : Int = {
     queryParams.collectFirst { case OBPLimit(value) => value }.getOrElse(100)
  }
  def getOffset(queryParams: List[OBPQueryParam]) : Int = {
    queryParams.collectFirst { case OBPOffset(value) => value }.getOrElse(0)
  }
  def getFromDate(queryParams: List[OBPQueryParam]) : String = {
    queryParams.collectFirst { case OBPFromDate(date) => APIUtil.DateWithMsFormat.format(date) }.getOrElse(defaultFromDate)
  }
  def getToDate(queryParams: List[OBPQueryParam]) : String = {
    queryParams.collectFirst { case OBPToDate(date) => APIUtil.DateWithMsFormat.format(date) }.getOrElse(defaultToDate)
  }

  def toLimit(limit: Box[String]): Box[OBPLimit] = limit.filter(StringUtils.isNotBlank).map(_.toInt).map(OBPLimit(_))

  def toOffset(offset: Box[String]): Box[OBPOffset] = offset.filter(StringUtils.isNotBlank).map(_.toInt).map(OBPOffset(_))

  def toFromDate(fromDate: Box[String]): Box[OBPFromDate] = fromDate.filter(StringUtils.isNotBlank).flatMap(APIUtil.parseDate(_)).map(OBPFromDate(_))

  def toToDate(toDate: Box[String]): Box[OBPToDate] = toDate.filter(StringUtils.isNotBlank).flatMap(APIUtil.parseDate(_)).map(OBPToDate(_))

  def toOBPQueryParams(limit: Int, offset: Int, fromDate: String, toDate: String): List[OBPQueryParam] = {
    val hTTPParams = List(
      HTTPParam("limit", List(limit.toString)),
      HTTPParam("offset", List(offset.toString)),
      HTTPParam("from_date", List(fromDate)),
      HTTPParam("to_date", List(toDate))
    )
    createQueriesByHttpParams(hTTPParams).getOrElse(Nil)
  }
}
