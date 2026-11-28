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

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import org.json4s.{Formats, NoTypeHints}
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
 *
 * One row of the table onwardroute, as it is stored. The routings and the rates are JSON, in the columns
 * bank_routings and rates; the currencies are comma separated.
 */
case class OnwardRoute(
  /** The member bank that states the route: the one a payment is paid to. */
  bankId: String,
  /** Where the route is in the bank's list, so the list reads back in the order it was given. */
  position: Int,
  /** The full name of the bank the route reaches. */
  fullName: String,
  /** The routings of the bank the route reaches, as a JSON list of scheme and address pairs. */
  bankRoutings: String,
  /** The OBP instance the bank the route reaches is a member of, named by its host. */
  obpHost: String,
  /** The ISO 3166 alpha-2 country of the bank the route reaches. */
  country: String,
  /** The currencies a payment on this instance may be sent in for this route, comma separated. */
  currencies: String,
  /** The bank's exchange rates for this route, as a JSON list of from, to and rate. */
  rates: String,
  /** When the bank last confirmed the route. Validity is worked out from this when the route is read. */
  lastConfirmed: Date
)

object OnwardRoute {

  private type Row = (Option[String], Option[Int], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[java.sql.Timestamp])

  private val selectColumns =
    fr"""SELECT bank_id, position, full_name, bank_routings, obp_host, country, currencies, rates, last_confirmed
         FROM onwardroute"""

  private def fromRow(row: Row): OnwardRoute = row match {
    case (bankId, position, fullName, bankRoutings, obpHost, country, currencies, rates, lastConfirmed) =>
      OnwardRoute(bankId.orNull, position.getOrElse(0), fullName.orNull, bankRoutings.orNull, obpHost.orNull,
        country.orNull, currencies.orNull, rates.orNull, lastConfirmed.map(t => new Date(t.getTime)).orNull)
  }

  /** The routes of the given banks, by bank and then by position. */
  def findAllOfBanks(bankIds: List[String]): List[OnwardRoute] =
    // Mapper's ByList with an empty list matched nothing.
    if (bankIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"bank_id", cats.data.NonEmptyList.fromListUnsafe(bankIds.distinct))
      DoobieUtil.runQuery((selectColumns ++ fr"WHERE" ++ in ++ fr"ORDER BY position ASC, id ASC").query[Row].to[List])
        .map(fromRow)
    }

  /** Every route of every bank, by bank and then by position. */
  def findAll(): List[OnwardRoute] =
    DoobieUtil.runQuery((selectColumns ++ fr"ORDER BY bank_id ASC, position ASC, id ASC").query[Row].to[List])
      .map(fromRow)

  /** Replaces the bank's whole list with `routes`, in one transaction (the request's, inside a request). */
  def replaceAllOfBank(bankId: String, routes: List[OnwardRoute]): Unit = {
    import cats.implicits._
    val program: ConnectionIO[Unit] = for {
      _ <- sql"DELETE FROM onwardroute WHERE bank_id = ${Option(bankId)}".update.run
      _ <- routes.traverse_ { route =>
        val lastConfirmed = Option(route.lastConfirmed).map(d => new java.sql.Timestamp(d.getTime))
        sql"""INSERT INTO onwardroute
              (bank_id, position, full_name, bank_routings, obp_host, country, currencies, rates, last_confirmed)
              VALUES (${Option(bankId)}, ${route.position}, ${Option(route.fullName)}, ${Option(route.bankRoutings)},
               ${Option(route.obpHost)}, ${Option(route.country)}, ${Option(route.currencies)}, ${Option(route.rates)},
               $lastConfirmed)""".update.run
      }
    } yield ()
    DoobieUtil.runUpdate(program)
  }
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
    // Each route is stored in the order the bank gave it.
    OnwardRoute.replaceAllOfBank(bankId, routes.zipWithIndex.map { case (route, position) =>
      OnwardRoute(
        bankId = bankId,
        position = position,
        fullName = route.fullName,
        bankRoutings = Serialization.write(route.bankRoutings),
        obpHost = route.obpHost,
        country = route.country,
        currencies = route.currencies.mkString(","),
        rates = Serialization.write(route.rates.map { case (from, to, rate) => OnwardRouteStoredRate(from, to, rate.bigDecimal.toPlainString) }),
        lastConfirmed = confirmedAt)
    })
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
      OnwardRoute.findAllOfBanks(bankIds)
        .flatMap(toView)
        .filter(_.validUntil.after(now))
        .map(route => route.copy(rates = route.rates.filter(_.validUntil.after(now))))
        .groupBy(_.bankId)
    }

  /** Every still-valid route on this instance, of every bank. Used to find which banks reach a given bank. */
  def allValid(): List[OnwardRouteView] = {
    val now = new Date()
    OnwardRoute.findAll()
      .flatMap(toView)
      .filter(_.validUntil.after(now))
      .map(route => route.copy(rates = route.rates.filter(_.validUntil.after(now))))
  }

  // A route that was never confirmed has no validity to work out, and is not valid. Every route is
  // stored with the time it was confirmed, so this is only a row written some other way.
  private def toView(row: OnwardRoute): Option[OnwardRouteView] = Option(row.lastConfirmed).map { lastConfirmed =>
    // Validity comes from the current setting, so a change to it applies to stored routes at once.
    val validUntil = new Date(lastConfirmed.getTime + routeValiditySeconds * 1000L)
    val rateValidUntil = new Date(lastConfirmed.getTime + rateValiditySeconds * 1000L)
    OnwardRouteView(
      bankId = row.bankId,
      fullName = row.fullName,
      bankRoutings = scala.util.Try(Serialization.read[List[OnwardRouteBankRouting]](row.bankRoutings)).getOrElse(Nil),
      obpHost = row.obpHost,
      country = row.country,
      currencies = Option(row.currencies).map(_.split(",").toList.map(_.trim).filter(_.nonEmpty)).getOrElse(Nil),
      rates = scala.util.Try(Serialization.read[List[OnwardRouteStoredRate]](row.rates)).getOrElse(Nil)
        .map(stored => OnwardRouteRate(stored.from, stored.to, BigDecimal(stored.rate), rateValidUntil)),
      lastConfirmed = lastConfirmed,
      validUntil = validUntil
    )
  }
}
