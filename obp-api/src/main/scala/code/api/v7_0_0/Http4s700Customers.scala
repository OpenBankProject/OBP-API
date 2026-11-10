/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
package code.api.v7_0_0

import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc, userAuthenticationMessage}
import code.api.util.ApiRole.canGetCustomersAtOneBank
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle, OBPAscending, OBPLimit, OBPOffset, OBPOrdering, OBPQueryParam}
import code.util.Helper
import net.liftweb.common.{Empty, Failure, Full}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.customerWithAttributesJsonV600
import code.api.v6_0_0.JSONFactory600
import code.bankconnectors.Connector
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, Customer}
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s700Customers {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  /** The query parameters that page and sort the list. */
  val PagingParameters: List[String] = List("limit", "offset", "sort_direction")

  /**
   * Query parameters that are never taken for attribute names: the paging parameters, the other
   * parameters OBP-API reads on list endpoints, and names starting with obp_ or _ (cache busters).
   */
  private val ReservedParameters: Set[String] = PagingParameters.toSet ++ Set("from_date", "to_date", "sort_by")
  private def reserved(name: String): Boolean = ReservedParameters.contains(name) || name.startsWith("obp_") || name.startsWith("_")

  /**
   * name -> accepted values, from the query parameters that are not reserved. A value lists several
   * accepted values separated by commas (a name cannot be repeated: the middleware refuses duplicate
   * query parameter names on every endpoint). A name whose values are all empty maps to Nil.
   */
  def attributeFilters(query: Map[String, List[String]]): Map[String, List[String]] =
    query.collect { case (name, values) if !reserved(name) =>
      name -> values.flatMap(_.split(",").map(_.trim)).filter(_.nonEmpty).distinct
    }

  /**
   * The ids of the bank's Customers that have, for every filter name, an attribute of that name with
   * one of its values. Each name is asked of the connector on its own and the results are
   * intersected, so the rule does not depend on how a connector combines several names.
   */
  private def matchingCustomerIds(bankId: BankId, filters: Map[String, List[String]], cc: CallContext): Future[Set[String]] =
    Future.sequence(filters.toList.map { case (name, values) =>
      NewStyle.function.getCustomerIdsByAttributeNameValues(bankId, Map(name -> values), Some(cc)).map(_._1.map(_.value).toSet)
    }).map(_.reduceOption(_ intersect _).getOrElse(Set.empty[String]))

  /**
   * One page of the matching Customers, ordered by customer_id (sort_direction ASC or DESC). Only the
   * page is loaded. An attribute whose Customer no longer exists is skipped; a connector failure fails
   * the request.
   */
  private def pageOfMatchingCustomers(bankId: BankId, ids: Set[String], queryParams: List[OBPQueryParam], cc: CallContext): Future[List[Customer]] = {
    val ascending = queryParams.collectFirst { case OBPOrdering(_, order) => order }.forall(_ == OBPAscending)
    val ordered = if (ascending) ids.toList.sorted else ids.toList.sorted.reverse
    val offset = math.max(queryParams.collectFirst { case OBPOffset(value) => value }.getOrElse(0), 0)
    val limit = queryParams.collectFirst { case OBPLimit(value) => math.max(value, 0) }
    val pageIds = limit.fold(ordered.drop(offset))(l => ordered.slice(offset, offset + l))
    Future.sequence(pageIds.map { id =>
      Connector.connector.vend.getCustomerByCustomerId(id, Some(cc)).map {
        case Full((customer, _)) => Some(customer)
        case Empty => None
        case failure: Failure => Some(APIUtil.connectorEmptyResponse[Customer](failure, Some(cc)))
      }
    }).map(_.flatten.filter(_.bankId == bankId.value))
  }

  // Route: GET /obp/v7.0.0/banks/BANK_ID/customers
  lazy val getCustomersAtOneBank: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / _ / "customers" =>
      EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
        val filters = attributeFilters(req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList })
        val withoutValue = filters.collect { case (name, Nil) => name }.toList.sorted
        for {
          _ <- Helper.booleanToFuture(s"$InvalidUrlParameters Customer Attribute filters need a value: ${withoutValue.mkString(", ")}", 400, Some(cc)) {
            withoutValue.isEmpty
          }
          (queryParams, _) <- NewStyle.function.extractQueryParams(req.uri.renderString, PagingParameters, Some(cc))
          customers <-
            if (filters.isEmpty) NewStyle.function.getCustomers(bank.bankId, Some(cc), queryParams)
            else matchingCustomerIds(bank.bankId, filters, cc).flatMap(pageOfMatchingCustomers(bank.bankId, _, queryParams, cc))
          (withAttributes, _) <-
            if (customers.isEmpty) Future.successful((Nil, Some(cc)))
            else NewStyle.function.getCustomerAttributesForCustomers(customers, Some(cc))
        } yield CustomersWithAttributesJsonV700(withAttributes.map(ca => JSONFactory600.createCustomerWithAttributesJson(ca.customer, ca.attributes)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getCustomersAtOneBank),
    "GET",
    "/banks/BANK_ID/customers",
    "Get Customers at Bank",
    s"""Get the Customers at the bank, each with its Customer Attributes, optionally filtered by Customer Attributes.
       |
       |**Filtering by Customer Attributes:** a query parameter is the name of a Customer Attribute and its value
       |the attribute value a Customer must have. Several accepted values are separated by commas, so a value
       |cannot itself contain a comma; different names must all match. A name can be given only once (as on every
       |endpoint, a repeated query parameter name is refused), and a filter without a value is refused.
       |
       |Not taken for attribute names: limit, offset, sort_direction, from_date, to_date, sort_by, and names
       |starting with obp_ or _.
       |
       |Examples:
       |
       |* `?organisation_kind=university` returns the Customers whose attribute `organisation_kind` is `university`.
       |* `?organisation_kind=university,regulator` returns those whose `organisation_kind` is either.
       |* `?organisation_kind=university&city=Muscat` returns those with both attributes.
       |
       |**Paging:**
       |
       |* limit: maximum number of Customers to return (default 50)
       |* offset: number of Customers to skip (default 0)
       |* sort_direction: ASC or DESC (default DESC)
       |
       |Without attribute filters the Customers at the bank are ordered by last_ok_date, as in v6.0.0. With
       |attribute filters the matching Customers are ordered by customer_id, and limit and offset count the
       |matching Customers only.
       |
       |Dates are returned as **YYYY-MM-DD**.
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    EmptyBody,
    CustomersWithAttributesJsonV700(List(customerWithAttributesJsonV600)),
    List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, InvalidUrlParameters, UnknownError),
    List(apiTagCustomer, apiTagUser),
    Some(canGetCustomersAtOneBank :: Nil),
    http4sPartialFunction = Some(getCustomersAtOneBank)
  )
}
