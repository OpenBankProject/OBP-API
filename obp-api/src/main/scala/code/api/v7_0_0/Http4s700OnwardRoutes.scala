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
import code.api.util.ApiRole.canUpdateOnwardRoutes
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{APIUtil, CustomJsonFormats, Glossary, NewStyle, RoutingRules}
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v6_0_0.{JSONFactory600, OnwardRoutesJsonV600}
import code.opencorridorroutes.{OnwardRouteBankRouting, OnwardRouteInput, OnwardRoutes}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import java.util.Date
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/** One exchange rate a bank offers on an onward route: one unit of `from` buys `rate` units of `to`. */
case class PutOnwardRouteRateJsonV700(from: String, to: String, rate: String)

/** One onward route as a bank states it: a bank on another OBP instance that can be reached through it. */
case class PutOnwardRouteJsonV700(
  full_name: String,
  bank_routings: List[BankRoutingJsonV121],
  obp_host: String,
  country: String,
  currencies: List[String],
  rates: Option[List[PutOnwardRouteRateJsonV700]]
)

/** A bank's whole list of onward routes. It replaces the bank's earlier list. */
case class PutOnwardRoutesJsonV700(onward_routes: List[PutOnwardRouteJsonV700])

/**
 * Onward routes: the banks on other OBP instances that can be reached through a member of this one.
 * The member's own Bank Node replaces the list from time to time, and each replacement confirms every route in it.
 * Anyone can read them, here and in Get Banks and Get Bank (v6.0.0).
 */
object Http4s700OnwardRoutes {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val CountryPattern = "^[A-Z]{2}$".r
  private val RatePattern = "^[0-9]+(\\.[0-9]+)?$".r

  /** The problems with a list of routes, in plain words. Empty when the list is valid. */
  def problemsWith(body: PutOnwardRoutesJsonV700): List[String] = {
    // Every routing pair in the list, normalised, with the route it belongs to.
    val pairs = body.onward_routes.flatMap(route =>
      route.bank_routings.map(routing => (routing.scheme.trim.toUpperCase, routing.address.trim.toUpperCase)))
    val duplicatePairs = pairs.groupBy(identity).collect { case (pair, uses) if uses.size > 1 => pair }.toList
    val listProblems =
      duplicatePairs.map { case (scheme, address) => s"the routing $scheme $address appears more than once: list each bank once" }
    val routeProblems = body.onward_routes.zipWithIndex.flatMap { case (route, index) =>
      val where = s"onward_routes[$index]"
      val currencies = route.currencies.map(_.trim.toUpperCase)
      val rates = route.rates.getOrElse(Nil)
      List(
        if (route.full_name.trim.isEmpty) Some(s"$where.full_name must not be empty") else None,
        if (route.bank_routings.isEmpty) Some(s"$where.bank_routings must name at least one routing") else None,
        if (route.bank_routings.exists(routing => routing.scheme.trim.isEmpty || routing.address.trim.isEmpty))
          Some(s"$where.bank_routings: each routing needs a scheme and an address") else None,
        // A bank_id only means something on its own OBP instance, so it cannot name a bank on another one.
        if (route.bank_routings.exists(routing => code.api.Constant.isImplicitOBPBankScheme(routing.scheme.trim.toUpperCase)))
          Some(s"$where.bank_routings: the OBP scheme names a bank_id, which only means something on its own OBP instance. Use a routing such as BIC") else None,
        if (route.obp_host.trim.isEmpty) Some(s"$where.obp_host must not be empty") else None,
        if (CountryPattern.findFirstIn(route.country.trim.toUpperCase).isEmpty)
          Some(s"$where.country must be an ISO 3166 alpha-2 country code, such as NG") else None,
        if (currencies.isEmpty) Some(s"$where.currencies must name at least one currency") else None,
        currencies.find(currency => !APIUtil.isValidCurrencyISOCode(currency))
          .map(currency => s"$where.currencies: $currency is not a known currency code"),
        rates.find(rate => !currencies.contains(rate.from.trim.toUpperCase))
          .map(rate => s"$where.rates: ${rate.from} is not one of the route's currencies"),
        rates.find(rate => !APIUtil.isValidCurrencyISOCode(rate.to.trim.toUpperCase))
          .map(rate => s"$where.rates: ${rate.to} is not a known currency code"),
        rates.find(rate => rate.from.trim.equalsIgnoreCase(rate.to.trim))
          .map(rate => s"$where.rates: a rate from ${rate.from} to ${rate.to} converts nothing"),
        rates.find(rate => RatePattern.findFirstIn(rate.rate.trim).isEmpty || BigDecimal(rate.rate.trim) <= 0)
          .map(rate => s"$where.rates: ${rate.rate} is not a positive decimal number"),
        if (rates.map(rate => (rate.from.trim.toUpperCase, rate.to.trim.toUpperCase)).distinct.size != rates.size)
          Some(s"$where.rates: give each pair of currencies once") else None
      ).flatten
    }
    listProblems ++ routeProblems
  }

  /** The routes as the store takes them, normalised: schemes, countries and currencies in capitals. */
  def toInputs(body: PutOnwardRoutesJsonV700): List[OnwardRouteInput] =
    body.onward_routes.map(route => OnwardRouteInput(
      fullName = route.full_name.trim,
      bankRoutings = route.bank_routings.map(routing => OnwardRouteBankRouting(routing.scheme.trim.toUpperCase, routing.address.trim)),
      obpHost = route.obp_host.trim,
      country = route.country.trim.toUpperCase,
      currencies = route.currencies.map(_.trim.toUpperCase).distinct,
      rates = route.rates.getOrElse(Nil).map(rate => (rate.from.trim.toUpperCase, rate.to.trim.toUpperCase, BigDecimal(rate.rate.trim)))
    ))

  // Route: GET /obp/v7.0.0/banks/BANK_ID/open-corridor/onward-routes
  lazy val getOnwardRoutes: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "banks" / _ / "open-corridor" / "onward-routes" =>
      EndpointHelpers.withBank(req) { (bank, _) =>
        Future(JSONFactory600.createOnwardRoutesJsonV600(OnwardRoutes.validForBank(bank.bankId.value)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOnwardRoutes),
    "GET",
    "/banks/BANK_ID/open-corridor/onward-routes",
    "Get Onward Routes",
    s"""Get the onward routes of BANK_ID: the banks on other OBP instances that can be reached through it.
       |
       |Only routes the bank's Bank Node has confirmed within the validity period this instance sets are returned. Exchange rates whose own, shorter validity has passed are left out of their route. A bank that does not carry payments on has an empty list.
       |
       |The same routes appear as `onward_routes` in Get Banks and Get Bank (v6.0.0). See ${Glossary.getGlossaryItemLink("Onward Routes")}.
       |
       |${userAuthenticationMessage(false)}
       |""".stripMargin,
    EmptyBody,
    OnwardRoutesJsonV600(List(JSONFactory600.onwardRouteJsonV600Example)),
    List($BankNotFound, UnknownError),
    apiTagBank :: apiTagTransactionRequest :: Nil,
    None,
    http4sPartialFunction = Some(getOnwardRoutes)
  )

  // Route: PUT /obp/v7.0.0/banks/BANK_ID/open-corridor/onward-routes
  lazy val updateOnwardRoutes: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "banks" / _ / "open-corridor" / "onward-routes" =>
      EndpointHelpers.withUserAndBankAndBody[PutOnwardRoutesJsonV700, OnwardRoutesJsonV600](req) { (_, bank, body, cc) =>
        for {
          _ <- Helper.booleanToFuture(OpenCorridorDisabled, cc = Some(cc)) {
            APIUtil.getPropsAsBoolValue("open_corridor_enabled", false)
          }
          problems = problemsWith(body)
          _ <- Helper.booleanToFuture(s"$InvalidJsonValue ${problems.mkString(". ")}.", cc = Some(cc)) {
            problems.isEmpty
          }
          (banks, _) <- NewStyle.function.getBanks(Some(cc))
          // A route to one of this instance's own members is refused: a member is paid directly.
          membersNamed = toInputs(body).flatMap(_.bankRoutings).flatMap(routing =>
            RoutingRules.findBank(banks, routing.scheme, routing.address) match {
              case Full(member) => Some(s"${routing.scheme} ${routing.address} is ${member.bankId.value}")
              case _ => None
            })
          _ <- Helper.booleanToFuture(s"$OpenCorridorOnwardRouteNamesMember ${membersNamed.mkString(", ")}.", cc = Some(cc)) {
            membersNamed.isEmpty
          }
          routes <- Future(OnwardRoutes.replaceForBank(bank.bankId.value, toInputs(body), new Date()))
        } yield JSONFactory600.createOnwardRoutesJsonV600(routes)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateOnwardRoutes),
    "PUT",
    "/banks/BANK_ID/open-corridor/onward-routes",
    "Update Onward Routes",
    s"""Replace the onward routes of BANK_ID: the banks on other OBP instances that can be reached through it.
       |
       |A bank that is a member of this OBP instance and of another can carry payments between them. Its own Bank Node states which banks on its other OBP instances it can reach, by sending its whole list here from time to time. The list replaces the bank's earlier one, so a route the bank no longer lists is gone at once. Sending the list confirms every route in it.
       |
       |A route stays valid for `open_corridor.onward_route_validity_seconds` after it was confirmed (default 3600), and each exchange rate on it for `open_corridor.onward_route_rate_validity_seconds` (default 300). After that it is left out of every response, so a bank whose node stops confirming drops out by itself. An empty list removes all the bank's routes.
       |
       |Each route names the bank it reaches by its routings (for example BIC) and its full name. It also gives the OBP instance that bank is on (`obp_host`), its ISO 3166 country, and the currencies a payment may be sent in. `rates` are optional: the bank's exchange rates for payments it converts, as decimal strings.
       |
       |A route is refused in three cases. It names a member of this OBP instance, which is paid directly. It uses the OBP scheme, whose bank_id only means something on its own OBP instance. Or it lists the same bank twice.
       |
       |Requires `open_corridor_enabled=true` on this instance.
       |
       |**Who may call it.** An application whose Consumer holds the Scope CanUpdateOnwardRoutes at BANK_ID, calling as itself. This is normally the bank's own Bank Node, with an OAuth2 client-credentials token. The operator grants the Scope to the members that may carry payments between OBP instances. A person with the Role CanUpdateOnwardRoutes at BANK_ID may also call it.
       |
       |See ${Glossary.getGlossaryItemLink("Onward Routes")}.
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    PutOnwardRoutesJsonV700(List(PutOnwardRouteJsonV700(
      full_name = "Bank C",
      bank_routings = List(BankRoutingJsonV121("BIC", "DEMCZZ22")),
      obp_host = "obp.example.org",
      country = "NG",
      currencies = List("KES", "NGN"),
      rates = Some(List(PutOnwardRouteRateJsonV700("KES", "NGN", "11.20")))
    ))),
    OnwardRoutesJsonV600(List(JSONFactory600.onwardRouteJsonV600Example)),
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, OpenCorridorDisabled, InvalidJsonFormat, InvalidJsonValue,
         OpenCorridorOnwardRouteNamesMember, $BankNotFound, UnknownError),
    apiTagBank :: apiTagTransactionRequest :: Nil,
    Some(List(canUpdateOnwardRoutes)),
    http4sPartialFunction = Some(updateOnwardRoutes)
  )
}
