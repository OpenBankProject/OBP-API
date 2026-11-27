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

package code.opencorridorroutes

import code.api.util.APIUtil
import net.liftweb.mapper._
import org.json4s._
import org.json4s.native.Serialization

import java.util.Date

/**
 * Onward routes: a bank's statement, on its own record, that banks on other OBP instances can be reached through it.
 *
 * A bank that is a member of two OBP instances can carry payments between them. Its own Bank Node states which
 * banks on its other instances it can reach, by replacing its whole list from time to time. Each replacement
 * confirms every route in it. A route is shown only while it is within the validity period this instance sets,
 * so a bank whose node stops confirming drops out by itself, without anyone removing it.
 *
 * A route never makes the bank it reaches a member here: that bank is not listed by Get Banks, and appears only
 * inside the onward routes of the bank that reaches it.
 */
class OnwardRoute extends LongKeyedMapper[OnwardRoute] with IdPK {
  def getSingleton = OnwardRoute

  /** The member bank that states the route: the one a payment is paid to. */
  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
  }
  /** Where the route is in the bank's list, so the list reads back in the order it was given. */
  object Position extends MappedInt(this) {
    override def dbColumnName = "position"
  }
  /** The full name of the bank the route reaches. */
  object FullName extends MappedString(this, 255) {
    override def dbColumnName = "full_name"
  }
  /** The routings of the bank the route reaches, as a JSON list of scheme and address pairs. */
  object BankRoutings extends MappedText(this) {
    override def dbColumnName = "bank_routings"
  }
  /** The OBP instance the bank the route reaches is a member of, named by its host. */
  object ObpHost extends MappedString(this, 255) {
    override def dbColumnName = "obp_host"
  }
  /** The ISO 3166 alpha-2 country of the bank the route reaches. */
  object Country extends MappedString(this, 8) {
    override def dbColumnName = "country"
  }
  /** The currencies a payment on this instance may be sent in for this route, comma separated. */
  object Currencies extends MappedString(this, 255) {
    override def dbColumnName = "currencies"
  }
  /** The bank's exchange rates for this route, as a JSON list of from, to and rate. */
  object Rates extends MappedText(this) {
    override def dbColumnName = "rates"
  }
  /** When the bank last confirmed the route. Validity is worked out from this when the route is read. */
  object LastConfirmed extends MappedDateTime(this) {
    override def dbColumnName = "last_confirmed"
  }
}

object OnwardRoute extends OnwardRoute with LongKeyedMetaMapper[OnwardRoute] {
  override def dbTableName = "OnwardRoute"
  override def dbIndexes = Index(BankId) :: super.dbIndexes
}

/** One routing of the bank a route reaches, for example BIC DEMCZZ22. */
case class OnwardRouteBankRouting(scheme: String, address: String)

/** One exchange rate on a route: one unit of `from` buys `rate` units of `to`. */
case class OnwardRouteRate(from: String, to: String, rate: BigDecimal, validUntil: Date)

/** A route as it is read: with the times it was confirmed and stays valid until. */
case class OnwardRouteView(
  bankId: String,
  fullName: String,
  bankRoutings: List[OnwardRouteBankRouting],
  obpHost: String,
  country: String,
  currencies: List[String],
  rates: List[OnwardRouteRate],
  lastConfirmed: Date,
  validUntil: Date
)

/** The stored form of a rate. The rate is kept as a decimal string, so no precision is lost. */
case class OnwardRouteStoredRate(from: String, to: String, rate: String)

/** A route as a bank states it, before it is stored. */
case class OnwardRouteInput(
  fullName: String,
  bankRoutings: List[OnwardRouteBankRouting],
  obpHost: String,
  country: String,
  currencies: List[String],
  rates: List[(String, String, BigDecimal)]
)

object OnwardRoutes {

  /** How long a confirmed route stays valid, in seconds. The bank's node confirms well within it. */
  def routeValiditySeconds: Int =
    APIUtil.getPropsAsIntValue("open_corridor.onward_route_validity_seconds", 3600)

  /** How long a confirmed exchange rate stays valid, in seconds. Rates move faster than reach, so this is shorter. */
  def rateValiditySeconds: Int =
    APIUtil.getPropsAsIntValue("open_corridor.onward_route_rate_validity_seconds", 300)

  private implicit val formats: Formats = Serialization.formats(NoTypeHints)

  /** Replace the bank's whole list with `routes`, all confirmed at `confirmedAt`. A route the bank no longer lists is gone at once. */
  def replaceForBank(bankId: String, routes: List[OnwardRouteInput], confirmedAt: Date): List[OnwardRouteView] = {
    // The bank's earlier list is removed first: the new list replaces it whole.
    OnwardRoute.findAll(By(OnwardRoute.BankId, bankId)).foreach(_.delete_!)
    // Each route is stored in the order the bank gave it.
    routes.zipWithIndex.foreach { case (route, position) =>
      OnwardRoute.create
        .BankId(bankId)
        .Position(position)
        .FullName(route.fullName)
        .BankRoutings(Serialization.write(route.bankRoutings))
        .ObpHost(route.obpHost)
        .Country(route.country)
        .Currencies(route.currencies.mkString(","))
        .Rates(Serialization.write(route.rates.map { case (from, to, rate) => OnwardRouteStoredRate(from, to, rate.bigDecimal.toPlainString) }))
        .LastConfirmed(confirmedAt)
        .saveMe()
    }
    // Read back what is now valid, as any reader would see it.
    validForBank(bankId)
  }

  /** The bank's routes that are still valid, in the bank's order. Expired routes and expired rates are left out. */
  def validForBank(bankId: String): List[OnwardRouteView] =
    validForBanks(List(bankId)).getOrElse(bankId, Nil)

  /** The still-valid routes of each of the given banks, by bank_id. A bank with none has no entry. */
  def validForBanks(bankIds: List[String]): Map[String, List[OnwardRouteView]] =
    if (bankIds.isEmpty) Map.empty
    else {
      val now = new Date()
      // Ordered by position, so each bank's routes read back in the order the bank gave them.
      OnwardRoute.findAll(ByList(OnwardRoute.BankId, bankIds.distinct), OrderBy(OnwardRoute.Position, Ascending))
        .map(toView)
        .filter(_.validUntil.after(now))
        .map(route => route.copy(rates = route.rates.filter(_.validUntil.after(now))))
        .groupBy(_.bankId)
    }

  /** Every still-valid route on this instance, of every bank. Used to find which banks reach a given bank. */
  def allValid(): List[OnwardRouteView] = {
    val now = new Date()
    OnwardRoute.findAll(OrderBy(OnwardRoute.BankId, Ascending), OrderBy(OnwardRoute.Position, Ascending))
      .map(toView)
      .filter(_.validUntil.after(now))
      .map(route => route.copy(rates = route.rates.filter(_.validUntil.after(now))))
  }

  private def toView(row: OnwardRoute): OnwardRouteView = {
    val lastConfirmed = row.LastConfirmed.get
    // Validity comes from the current setting, so a change to it applies to stored routes at once.
    val validUntil = new Date(lastConfirmed.getTime + routeValiditySeconds * 1000L)
    val rateValidUntil = new Date(lastConfirmed.getTime + rateValiditySeconds * 1000L)
    OnwardRouteView(
      bankId = row.BankId.get,
      fullName = row.FullName.get,
      bankRoutings = scala.util.Try(Serialization.read[List[OnwardRouteBankRouting]](row.BankRoutings.get)).getOrElse(Nil),
      obpHost = row.ObpHost.get,
      country = row.Country.get,
      currencies = Option(row.Currencies.get).map(_.split(",").toList.map(_.trim).filter(_.nonEmpty)).getOrElse(Nil),
      rates = scala.util.Try(Serialization.read[List[OnwardRouteStoredRate]](row.Rates.get)).getOrElse(Nil)
        .map(stored => OnwardRouteRate(stored.from, stored.to, BigDecimal(stored.rate), rateValidUntil)),
      lastConfirmed = lastConfirmed,
      validUntil = validUntil
    )
  }
}
