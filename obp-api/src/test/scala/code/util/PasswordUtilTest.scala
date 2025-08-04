/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

package code.api.util

import code.util.Helper.MdcLoggable
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class PasswordUtilTest extends FeatureSpec with Matchers with GivenWhenThen with MdcLoggable {

  feature("Evaluate password strength using Zxcvbn") {

    scenario("Very weak password should return low score and be unacceptable") {
      Given("a common password '12345678'")
      val password = "12345678"

      When("measured with zxcvbn")
      val strength = PasswordUtil.getStrength(password)

      Then("the score should be 0 and it should be unacceptable")
      strength.getScore should be <= 1
      PasswordUtil.isAcceptable(password) should be (false)
    }

    scenario("Moderate password should be acceptable") {
      Given("a moderately strong password 'OpenBank2025$'")
      val password = "OpenBank2025$"

      When("measured with zxcvbn")
      val strength = PasswordUtil.getStrength(password)

      Then("the score should be >= 3 and it should be acceptable")
      strength.getScore should be >= 3
      PasswordUtil.isAcceptable(password) should be (true)
    }

    scenario("Strong password with emoji and unicode should be acceptable") {
      Given("a complex password '🔥MySecurę密码2025!'")
      val password = "🔥MySecurę密码2025!"

      When("measured with zxcvbn")
      val strength = PasswordUtil.getStrength(password)

      Then("the score should be >= 3 and it should be acceptable")
      strength.getScore should be >= 3
      PasswordUtil.isAcceptable(password) should be (true)
    }

    scenario("Very strong password should be clearly acceptable") {
      Given("a very strong password 'G@lacticSafe#AlphaZebra99!!'")
      val password = "G@lacticSafe#AlphaZebra99!!"

      When("measured with zxcvbn")
      val strength = PasswordUtil.getStrength(password)

      Then("the score should be 4 and it should be acceptable")
      strength.getScore should be (4)
      PasswordUtil.isAcceptable(password) should be (true)
    }

  }
}
