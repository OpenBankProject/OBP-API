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

package code.api.v5_0_0

import org.scalatest.Ignore
import code.api.v4_0_0.{APIInfoJson400, BanksJson400}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

@Ignore
class RootAndBanksTest extends V500ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)

  feature(s"V500 public read endpoints - $VersionOfApi") {

    scenario("GET /root returns API info", VersionOfApi) {
      val request = (v5_0_0_Request / "root").GET
      val response = makeGetRequest(request)
      response.code should equal(200)
      val apiInfo = response.body.extract[APIInfoJson400]
      apiInfo.version.nonEmpty shouldBe true
      apiInfo.version_status.nonEmpty shouldBe true
      apiInfo.git_commit.nonEmpty shouldBe true
      apiInfo.connector.nonEmpty shouldBe true
    }

    scenario("GET /banks returns banks list", VersionOfApi) {
      val request = (v5_0_0_Request / "banks").GET
      val response = makeGetRequest(request)
      response.code should equal(200)
      val banks = response.body.extract[BanksJson400]
      banks.banks.nonEmpty shouldBe true
    }
  }
}

