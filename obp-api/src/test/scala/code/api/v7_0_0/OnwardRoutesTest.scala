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

import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canUpdateOnwardRoutes
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, InvalidJsonValue, OpenCorridorDisabled, OpenCorridorOnwardRouteNamesMember, UserHasMissingRoles}
import code.api.v6_0_0.{BankJsonV600, BanksJsonV600, OnwardRoutesJsonV600, V600ServerSetup}
import code.entitlement.Entitlement
import code.model.dataAccess.MappedBank
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

// Onward routes: a member states which banks on other OBP instances can be reached through it, and anyone can read them.
class OnwardRoutesTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"
  def v6 = baseRequest / "obp" / "v6.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object UpdateEndpoint extends Tag(nameOf(Http4s700OnwardRoutes.updateOnwardRoutes))
  object GetEndpoint extends Tag(nameOf(Http4s700OnwardRoutes.getOnwardRoutes))

  private def routesUrl(bankId: String) = v7 / "banks" / bankId / "open-corridor" / "onward-routes"
  private def message(response: code.setup.APIResponse) = response.body.extract[ErrorMessage].message

  // A BIC unique to this test run, so routes from other runs never match it.
  private def uniqueBic(): String = "T" + APIUtil.generateUUID().replace("-", "").take(7).toUpperCase

  // One route to Bank C on another OBP instance, reached by the given BIC.
  private def oneRoute(bic: String): String =
    s"""{"onward_routes": [{
       |  "full_name": "Bank C",
       |  "bank_routings": [{"scheme": "BIC", "address": "$bic"}],
       |  "obp_host": "obp-b.example.org",
       |  "country": "NG",
       |  "currencies": ["KES", "NGN"],
       |  "rates": [{"from": "KES", "to": "NGN", "rate": "11.20"}]
       |}]}""".stripMargin

  // user1 may update the onward routes of the bank.
  private def grantUpdate(bankId: String): Unit =
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateOnwardRoutes.toString)

  feature(s"test ${UpdateEndpoint.name} - who may call it") {
    scenario("Without a login the caller gets 401", UpdateEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      val response = makePutRequest(routesUrl(testBankId1.value).PUT, oneRoute(uniqueBic()))
      response.code should equal(401)
      message(response) should equal(AuthenticatedUserIsRequired)
    }

    scenario("A user without CanUpdateOnwardRoutes gets 403", UpdateEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      val response = makePutRequest(routesUrl(testBankId2.value).PUT <@ user2, oneRoute(uniqueBic()))
      response.code should equal(403)
      message(response) should startWith(UserHasMissingRoles)
    }

    scenario("With Open Corridor off the update is refused", UpdateEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "false")
      grantUpdate(testBankId1.value)
      val response = makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, oneRoute(uniqueBic()))
      response.code should equal(400)
      message(response) should startWith(OpenCorridorDisabled)
    }
  }

  feature(s"test ${UpdateEndpoint.name} and ${GetEndpoint.name} - routes are stored and shown") {
    scenario("A stated route shows in Get Onward Routes, Get Bank and Get Banks, and is replaced by the next list", UpdateEndpoint, GetEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      grantUpdate(testBankId1.value)
      val bic = uniqueBic()

      When("the bank states one route")
      val update = makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, oneRoute(bic))
      Then("it gets the route back, confirmed now")
      update.code should equal(200)
      val stated = update.body.extract[OnwardRoutesJsonV600].onward_routes
      stated.map(_.bank_routings.map(_.address)) should equal(List(List(bic)))
      stated.head.country should equal("NG")
      stated.head.rates.map(_.rate) should equal(List("11.20"))
      stated.head.valid_until.after(stated.head.last_confirmed) shouldBe true

      And("anyone can read it, without logging in")
      val read = makeGetRequest(routesUrl(testBankId1.value).GET)
      read.code should equal(200)
      read.body.extract[OnwardRoutesJsonV600].onward_routes.map(_.full_name) should equal(List("Bank C"))

      And("Get Bank and Get Banks (v6.0.0) show it on the bank, and the bank it reaches is not listed as a bank")
      val bank = makeGetRequest((v6 / "banks" / testBankId1.value).GET)
      bank.code should equal(200)
      bank.body.extract[BankJsonV600].onward_routes.flatMap(_.bank_routings.map(_.address)) should equal(List(bic))
      val banks = makeGetRequest((v6 / "banks").GET).body.extract[BanksJsonV600].banks
      banks.find(_.bank_id == testBankId1.value).get.onward_routes.flatMap(_.bank_routings.map(_.address)) should equal(List(bic))
      banks.exists(_.bank_routings.exists(_.address == bic)) shouldBe false
      And("a bank that carries nothing on has an empty list")
      banks.find(_.bank_id == testBankId2.value).get.onward_routes should equal(Nil)

      When("the bank sends an empty list")
      makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, """{"onward_routes": []}""").code should equal(200)
      Then("its routes are gone at once")
      makeGetRequest(routesUrl(testBankId1.value).GET).body.extract[OnwardRoutesJsonV600].onward_routes should equal(Nil)
    }

    scenario("Routes and rates are left out once their validity has passed", UpdateEndpoint, GetEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      grantUpdate(testBankId1.value)
      makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, oneRoute(uniqueBic())).code should equal(200)

      When("rates are valid for no time at all")
      setPropsValues("open_corridor.onward_route_rate_validity_seconds" -> "0")
      Then("the route is still shown, without its rate")
      val withoutRates = makeGetRequest(routesUrl(testBankId1.value).GET).body.extract[OnwardRoutesJsonV600].onward_routes
      withoutRates.size should equal(1)
      withoutRates.head.rates should equal(Nil)

      When("routes are valid for no time at all")
      setPropsValues("open_corridor.onward_route_validity_seconds" -> "0")
      Then("the route is left out")
      makeGetRequest(routesUrl(testBankId1.value).GET).body.extract[OnwardRoutesJsonV600].onward_routes should equal(Nil)

      setPropsValues("open_corridor.onward_route_validity_seconds" -> "3600", "open_corridor.onward_route_rate_validity_seconds" -> "300")
    }
  }

  feature(s"test ${UpdateEndpoint.name} - routes that are refused") {
    scenario("A route to a member of this OBP instance is refused: a member is paid directly", UpdateEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      grantUpdate(testBankId1.value)
      val memberBic = uniqueBic()
      val memberBankId = "onward." + APIUtil.generateUUID().take(8)
      MappedBank.create.permalink(memberBankId).fullBankName("Member").shortBankName("Member")
        .mBankRoutingScheme("BIC").mBankRoutingAddress(memberBic).saveMe()
      val response = makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, oneRoute(memberBic))
      response.code should equal(400)
      message(response) should startWith(OpenCorridorOnwardRouteNamesMember)
      message(response) should include(memberBankId)
    }

    scenario("A route with the OBP scheme, a bad country, an unknown currency or a bad rate is refused", UpdateEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      grantUpdate(testBankId1.value)
      val bic = uniqueBic()
      val refused = List(
        oneRoute(bic).replace(s""""scheme": "BIC", "address": "$bic"""", """"scheme": "OBP", "address": "some.bank"""") -> "OBP scheme",
        oneRoute(bic).replace(""""country": "NG"""", """"country": "Nigeria"""") -> "country",
        oneRoute(bic).replace(""""currencies": ["KES", "NGN"]""", """"currencies": ["KES", "XQQ"]""") -> "XQQ",
        oneRoute(bic).replace(""""rate": "11.20"""", """"rate": "-1"""") -> "positive decimal",
        oneRoute(bic).replace(""""from": "KES"""", """"from": "USD"""") -> "not one of the route's currencies"
      )
      refused.foreach { case (body, why) =>
        val response = makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, body)
        withClue(why) {
          response.code should equal(400)
          message(response) should startWith(InvalidJsonValue)
          message(response) should include(why)
        }
      }
    }

    scenario("A list naming the same bank twice is refused", UpdateEndpoint, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      grantUpdate(testBankId1.value)
      val bic = uniqueBic()
      val route = oneRoute(bic).stripPrefix("""{"onward_routes": [""").stripSuffix("]}")
      val response = makePutRequest(routesUrl(testBankId1.value).PUT <@ user1, s"""{"onward_routes": [$route, $route]}""")
      response.code should equal(400)
      message(response) should include("appears more than once")
    }
  }
}
