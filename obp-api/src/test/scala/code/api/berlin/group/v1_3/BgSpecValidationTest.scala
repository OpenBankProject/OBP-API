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

package code.api.berlin.group.v1_3

import code.api.berlin.group.v1_3.BgSpecValidation._
import code.api.v4_0_0.V400ServerSetup
import org.scalatest.Tag

import java.time.LocalDate
import java.util.Date

class BgSpecValidationTest extends V400ServerSetup {

  // Test tags
  object File extends Tag("BgSpecValidation.scala")
  object Function1 extends Tag("validateValidUntil")
  object Function2 extends Tag("getErrorMessage")
  object Function3 extends Tag("getDate")
  object Function4 extends Tag("formatToISODate")

  feature(s"Test function: $Function1 at file $File") {

    scenario("Reject past date", Function1) {
      When("The client provides a date in the past")
      val yesterday = LocalDate.now().minusDays(1).toString

      Then("It should be rejected")
      val error = getErrorMessage(yesterday)
      error should include("cannot be in the past")
    }

    scenario("Accept today's date", Function1) {
      When("The client provides today's date")
      val today = LocalDate.now().toString

      Then("It should be accepted")
      val error = getErrorMessage(today)
      error shouldBe ""
    }

    scenario("Accept exactly 180 days in the future", Function1) {
      When("The client provides the maximum allowed date (180 days)")
      val maxDay = MaxValidDays.toString

      Then("It should be accepted")
      val error = getErrorMessage(maxDay)
      error shouldBe ""
    }

    scenario("Reject date beyond 180 days", Function1) {
      When("The client provides a date 181 days in the future")
      val tooFar = MaxValidDays.plusDays(1).toString

      Then("It should be rejected")
      val error = getErrorMessage(tooFar)
      error should include("exceeds the maximum allowed period")
    }

    scenario("Reject invalid date format", Function1) {
      When("The client provides a date in wrong format")
      val invalid = "2025/12/31"

      Then("It should be rejected")
      val error = getErrorMessage(invalid)
      error should include("invalid")
    }
  }

  feature(s"Test function: $Function2 and $Function3 at file $File") {

    scenario("getDate returns valid Date for correct input", Function3) {
      When("We provide a valid ISO date")
      val today = LocalDate.now().toString
      val result = getDate(today)

      Then("It should return a non-null Date")
      result shouldBe a[Date]
    }

    scenario("getDate returns null for invalid input", Function3) {
      When("We provide an invalid date format")
      val result = getDate("2025/12/31")

      Then("It should return null")
      result shouldBe null
    }
  }

  feature(s"Test function: $Function4 at file $File") {

    scenario("formatToISODate formats a valid Date", Function4) {
      When("We pass a valid Date object")
      val today = new Date()
      val formatted = formatToISODate(today)

      Then("It should return an ISO date string")
      formatted should fullyMatch regex """\d{4}-\d{2}-\d{2}"""
    }

    scenario("formatToISODate handles null gracefully", Function4) {
      When("We pass null")
      val formatted = formatToISODate(null)

      Then("It should return empty string")
      formatted shouldBe ""
    }
  }
}
