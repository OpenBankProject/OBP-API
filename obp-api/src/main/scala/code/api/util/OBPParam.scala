package code.api.util

import java.util.Date

import code.api.util.APIUtil._
import net.liftweb.common.Box
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
/**
 * Restrict a transaction query to one direction, so the database applies the restriction and any
 * page limit together.
 *
 * `credits = true` keeps money in, `false` keeps money out. Zero counts as a credit, which is what
 * UK Open Banking states and what UKAmounts.creditDebitIndicator implements -- the two have to
 * agree, or a row could be labelled one direction in the response and selected as the other.
 *
 * A connector that does not honour this returns more rows than asked for, never fewer, so callers
 * that use it to enforce a consent's scope must still filter what comes back.
 */
case class OBPTransactionDirection(credits: Boolean) extends OBPQueryParam
object OBPTransactionDirection {

  /**
   * Where a credit starts, in the smallest currency unit. Inclusive, because zero is a credit.
   *
   * Lives beside the param because two enforcements have to agree on it: the connector's SQL
   * predicate and the endpoint's filter over the rows that came back. A connector holding its own
   * literal could drift from the filter, and a row would then be selected by one and dropped by the
   * other.
   */
  val creditFloorInSmallestUnit = 0L

  /**
   * Whether the restriction this param expresses keeps a row of the given amount.
   *
   * The in-memory statement of what the connector's query does, so a test can hold the two sides of
   * the rule against each other without standing up a database. Not a substitute for reading real
   * rows through the real query -- uk_direction_paging.py does that -- but it pins the boundary.
   */
  def admits(param: OBPQueryParam, amountInSmallestUnit: Long): Boolean = param match {
    case OBPTransactionDirection(true) => amountInSmallestUnit >= creditFloorInSmallestUnit
    case OBPTransactionDirection(false) => amountInSmallestUnit < creditFloorInSmallestUnit
    case _ => true
  }
}
case class OBPConsumerId(value: String) extends OBPQueryParam
case class OBPSortBy(value: String) extends OBPQueryParam
case class OBPAzp(value: String) extends OBPQueryParam
case class OBPIss(value: String) extends OBPQueryParam
case class OBPConsentId(value: String) extends OBPQueryParam
case class OBPConsentReferenceId(value: String) extends OBPQueryParam
// PeerTrust.Resolution.mode on the metric row: "direct", "forwarded" or "none".
case class OBPCertificateTrust(value: String) extends OBPQueryParam
case class OBPUserId(value: String) extends OBPQueryParam
// Multiple user ids, matched with SQL IN — used by self-service endpoints that lock the
// user filter to a server-resolved set (e.g. /my/metrics: the human plus their consent-agents).
case class OBPUserIds(values: List[String]) extends OBPQueryParam
case class ProviderProviderId(value: String) extends OBPQueryParam
case class OBPStatus(value: String) extends OBPQueryParam
case class OBPBankId(value: String) extends OBPQueryParam
case class OBPAccountId(value: String) extends OBPQueryParam
case class OBPUrl(value: String) extends OBPQueryParam
case class OBPAppName(value: String) extends OBPQueryParam
case class OBPExcludeAppNames(values: List[String]) extends OBPQueryParam
case class OBPIncludeAppNames(values: List[String]) extends OBPQueryParam
case class OBPImplementedByPartialFunction(value: String) extends OBPQueryParam
case class OBPImplementedInVersion(value: String) extends OBPQueryParam
case class OBPVerb(value: String) extends OBPQueryParam
case class OBPAnon(value: Boolean) extends OBPQueryParam
case class OBPCorrelationId(value: String) extends OBPQueryParam
case class OBPDuration(value: Long) extends OBPQueryParam
case class OBPHttpStatusCode(value: Int) extends OBPQueryParam
case class OBPExcludeUrlPatterns(values: List[String]) extends OBPQueryParam
case class OBPIncludeUrlPatterns(values: List[String]) extends OBPQueryParam
case class OBPExcludeImplementedByPartialFunctions(value: List[String]) extends OBPQueryParam
case class OBPIncludeImplementedByPartialFunctions(value: List[String]) extends OBPQueryParam
case class OBPFunctionName(value: String) extends OBPQueryParam
case class OBPConnectorName(value: String) extends OBPQueryParam
case class OBPEmpty() extends OBPQueryParam
case class OBPCustomerId(value: String) extends OBPQueryParam
case class OBPLockedStatus(value: String) extends OBPQueryParam
case class OBPIsDeleted(value: Boolean) extends OBPQueryParam
case class OBPRoleName(value: String) extends OBPQueryParam
case class OBPProvider(value: String) extends OBPQueryParam
case class OBPUsername(value: String) extends OBPQueryParam
case class OBPEmail(value: String) extends OBPQueryParam

object OBPQueryParam {
  val LIMIT = "limit"
  val OFFSET = "offset"
  val FROM_DATE = "fromDate"
  val TO_DATE = "toDate"

  private val defaultFromDate = APIUtil.DateWithMsFormat.format(APIUtil.theEpochTime)
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
