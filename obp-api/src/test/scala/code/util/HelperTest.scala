/**
 * Open Bank Project - API
 * Copyright (C) 2011-2019, TESOBE GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Email: contact@tesobe.com
 * TESOBE GmbH.
 * Osloer Strasse 16/17
 * Berlin 13359, Germany
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 *
 */

package code.util


import code.api.util._
import code.setup.PropsReset
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}


class HelperTest extends FeatureSpec with Matchers with GivenWhenThen with PropsReset {

  feature("test APIUtil.basicUrlValidation method") {
    val testString1 = "http://localhost:8082/oauthcallback?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018"
    val testString2 = "http://localhost:8082?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018"

    Helper.extractCleanRedirectURL(testString1).head should be("http://localhost:8082/oauthcallback")
    Helper.extractCleanRedirectURL(testString2).head should be("http://localhost:8082")

  }

}