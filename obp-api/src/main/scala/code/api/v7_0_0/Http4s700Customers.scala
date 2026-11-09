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
import code.api.util.{CallContext, CustomJsonFormats, NewStyle, OBPAscending, OBPLimit, OBPOffset, OBPOrdering, OBPQueryParam}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.customerWithAttributesJsonV600
import code.api.v6_0_0.JSONFactory600
import code.bankconnectors.Connector
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, Customer, CustomerId}
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

  /** The query parameters that page and sort the list. Every other query parameter filters on a Customer Attribute. */
  val PagingParameters: List[String] = List("limit", "offset", "sort_direction")

  /**
   * name -> accepted values, from the query parameters that are not paging parameters. A value may
   * list several accepted values separated by commas (a name cannot be repeated: the middleware
   * refuses duplicate query parameter names on every endpoint).
   */
  def attributeFilters(query: Map[String, List[String]]): Map[String, List[String]] =
    query.collect { case (name, values) if !PagingParameters.contains(name) =>
      name -> values.flatMap(_.split(",").map(_.trim)).filter(_.nonEmpty).distinct
    }.filter(_._2.nonEmpty)

  /**
   * The bank's Customers that have, for every filter name, an attribute of that name with one of
   * its values. Each name is asked of the connector on its own and the results are intersected, so
   * the rule does not depend on how a connector combines several names.
   */
  private def customersMatching(bankId: BankId, filters: Map[String, List[String]], cc: CallContext): Future[List[Customer]] =
    for {
      idsPerName <- Future.sequence(filters.toList.map { case (name, values) =>
        NewStyle.function.getCustomerIdsByAttributeNameValues(bankId, Map(name -> values), Some(cc)).map(_._1.map(_.value).toSet)
      })
      ids = idsPerName.reduceOption(_ intersect _).getOrElse(Set.empty[String])
      found <- Future.sequence(ids.toList.map(id => Connector.connector.vend.getCustomerByCustomerId(id, Some(cc)).map(_.map(_._1).toOption)))
    } yield found.flatten.filter(_.bankId == bankId.value)

  /** The same ordering and page the provider applies to an unfiltered list: last_ok_date, then customer_id. */
  private def page(customers: List[Customer], queryParams: List[OBPQueryParam]): List[Customer] = {
    val byId = customers.sortBy(_.customerId)
    val ordered = queryParams.collectFirst { case OBPOrdering(_, order) => order } match {
      case Some(OBPAscending) => byId.sortBy(c => Option(c.lastOkDate).map(_.getTime).getOrElse(0L))
      case Some(_)            => byId.sortBy(c => -Option(c.lastOkDate).map(_.getTime).getOrElse(0L))
      case None               => byId
    }
    val offset = queryParams.collectFirst { case OBPOffset(value) => value }.getOrElse(0)
    val limit = queryParams.collectFirst { case OBPLimit(value) => value }
    val rest = ordered.drop(math.max(offset, 0))
    limit.map(l => rest.take(math.max(l, 0))).getOrElse(rest)
  }

  // Route: GET /obp/v7.0.0/banks/BANK_ID/customers
  lazy val getCustomersAtOneBank: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / _ / "customers" =>
      EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
        val filters = attributeFilters(req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList })
        for {
          (queryParams, _) <- NewStyle.function.extractQueryParams(req.uri.renderString, PagingParameters, Some(cc))
          customers <-
            if (filters.isEmpty) NewStyle.function.getCustomers(bank.bankId, Some(cc), queryParams)
            else customersMatching(bank.bankId, filters, cc).map(page(_, queryParams))
          withAttributes <- Future.sequence(customers.map { customer =>
            NewStyle.function.getCustomerAttributes(bank.bankId, CustomerId(customer.customerId), Some(cc))
              .map { case (attributes, _) => JSONFactory600.createCustomerWithAttributesJson(customer, attributes) }
          })
        } yield CustomersWithAttributesJsonV700(withAttributes)
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
       |**Filtering by Customer Attributes:** every query parameter other than the paging parameters below
       |is the name of a Customer Attribute, and its value the attribute value a Customer must have.
       |Several accepted values are separated by commas; different names must all match.
       |A name can be given only once (as on every endpoint, a repeated query parameter name is refused).
       |
       |Examples:
       |
       |* `?organisation_kind=university` returns the Customers whose attribute `organisation_kind` is `university`.
       |* `?organisation_kind=university,regulator` returns those whose `organisation_kind` is either.
       |* `?organisation_kind=university&city=Muscat` returns those with both attributes.
       |
       |Without attribute parameters all Customers at the bank are returned.
       |
       |**Paging parameters:**
       |
       |* limit: maximum number of Customers to return (optional)
       |* offset: number of Customers to skip (optional)
       |* sort_direction: ASC or DESC, by last_ok_date (optional)
       |
       |When attributes are filtered, limit and offset count the matching Customers only.
       |
       |Dates are returned as **YYYY-MM-DD**.
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    EmptyBody,
    CustomersWithAttributesJsonV700(List(customerWithAttributesJsonV600)),
    List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
    List(apiTagCustomer, apiTagUser),
    Some(canGetCustomersAtOneBank :: Nil),
    http4sPartialFunction = Some(getCustomersAtOneBank)
  )
}
