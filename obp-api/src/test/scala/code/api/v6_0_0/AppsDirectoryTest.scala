/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v6_0_0

import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag


class AppsDirectoryTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations6_0_0.getAppsDirectory))

  feature("Get Apps Directory v6.0.0") {

    scenario("We get apps directory without authentication - should succeed", VersionOfApi, ApiEndpoint) {
      When("We call the apps-directory endpoint without authentication")
      val request = (v6_0_0_Request / "apps-directory").GET
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)
    }

    scenario("We get apps directory with authentication - should also succeed", VersionOfApi, ApiEndpoint) {
      When("We call the apps-directory endpoint with authentication")
      val request = (v6_0_0_Request / "apps-directory").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)
    }

    scenario("Response only contains explicitly whitelisted keys", VersionOfApi, ApiEndpoint) {
      When("We call the apps-directory endpoint")
      val request = (v6_0_0_Request / "apps-directory").GET
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("Every returned key should be in the explicit whitelist")
      val props = (response.body \ "apps_directory").children
      props.foreach { prop =>
        val name = (prop \ "name").extract[String]
        withClue(s"Key '$name' should be in appDiscoveryWhitelist: ") {
          APIUtil.appDiscoveryWhitelist should contain(name)
        }
      }
    }

    scenario("Response does not contain sensitive keywords in keys", VersionOfApi, ApiEndpoint) {
      When("We call the apps-directory endpoint")
      val request = (v6_0_0_Request / "apps-directory").GET
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("No key should contain any sensitive keyword")
      val props = (response.body \ "apps_directory").children
      props.foreach { prop =>
        val name = (prop \ "name").extract[String].toLowerCase
        APIUtil.sensitiveKeywords.foreach { keyword =>
          name should not include(keyword)
        }
      }
    }

    scenario("Response does not contain sensitive keywords in values", VersionOfApi, ApiEndpoint) {
      When("We call the apps-directory endpoint")
      val request = (v6_0_0_Request / "apps-directory").GET
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("No value should contain any sensitive keyword (must be masked or excluded)")
      val props = (response.body \ "apps_directory").children
      props.foreach { prop =>
        val value = (prop \ "value").extract[String].toLowerCase
        if (value != "****") {
          APIUtil.sensitiveKeywords.foreach { keyword =>
            value should not include(keyword)
          }
        }
      }
    }

    scenario("Response does not expose internal infrastructure props", VersionOfApi, ApiEndpoint) {
      When("We call the apps-directory endpoint")
      val request = (v6_0_0_Request / "apps-directory").GET
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("Internal infrastructure keys should not be present")
      val props = (response.body \ "apps_directory").children
      val names = props.map(p => (p \ "name").extract[String])
      names should not contain("connector")
      names should not contain("write_metrics")
      names should not contain("db.driver")
      names should not contain("cache.redis.url")
      names should not contain("cache.redis.port")
      names should not contain("mail.smtp.host")
      names should not contain("es.metrics.host")
    }
  }

  feature("Apps Directory unit-level checks v6.0.0") {

    scenario("maskSensitivePropValue masks keys containing sensitive keywords", VersionOfApi, ApiEndpoint) {
      APIUtil.maskSensitivePropValue("db_password", "mysecretpw") should equal("****")
      APIUtil.maskSensitivePropValue("oauth_token_url", "https://example.com") should equal("****")
      APIUtil.maskSensitivePropValue("api_secret", "abc123") should equal("****")
      APIUtil.maskSensitivePropValue("jdbc_connection", "jdbc:postgresql://localhost") should equal("****")
      APIUtil.maskSensitivePropValue("some_passphrase", "value") should equal("****")
      APIUtil.maskSensitivePropValue("my_credential", "value") should equal("****")
      APIUtil.maskSensitivePropValue("authorization_header", "Bearer xyz") should equal("****")
    }

    scenario("maskSensitivePropValue masks values containing sensitive keywords", VersionOfApi, ApiEndpoint) {
      APIUtil.maskSensitivePropValue("some_prop", "contains_password_here") should equal("****")
      APIUtil.maskSensitivePropValue("some_prop", "jdbc:postgresql://localhost") should equal("****")
    }

    scenario("maskSensitivePropValue does not mask safe values", VersionOfApi, ApiEndpoint) {
      APIUtil.maskSensitivePropValue("hostname", "https://api.example.com") should equal("https://api.example.com")
      APIUtil.maskSensitivePropValue("webui_api_explorer_url", "https://explorer.example.com") should equal("https://explorer.example.com")
      APIUtil.maskSensitivePropValue("api_port", "8080") should equal("8080")
    }

    scenario("getAppDiscoveryPairs only returns explicitly whitelisted keys", VersionOfApi, ApiEndpoint) {
      val pairs = APIUtil.getAppDiscoveryPairs
      pairs.foreach { case (key, _) =>
        withClue(s"Key '$key' should be in appDiscoveryWhitelist: ") {
          APIUtil.appDiscoveryWhitelist should contain(key)
        }
      }
    }

    scenario("getAppDiscoveryPairs does not return keys with sensitive keywords", VersionOfApi, ApiEndpoint) {
      val pairs = APIUtil.getAppDiscoveryPairs
      pairs.foreach { case (key, _) =>
        APIUtil.sensitiveKeywords.foreach { keyword =>
          withClue(s"Key '$key' should not contain sensitive keyword '$keyword': ") {
            key.toLowerCase should not include(keyword)
          }
        }
      }
    }

    scenario("getAppDiscoveryPairs values are never raw sensitive data", VersionOfApi, ApiEndpoint) {
      val pairs = APIUtil.getAppDiscoveryPairs
      pairs.foreach { case (key, value) =>
        if (value != "****") {
          APIUtil.sensitiveKeywords.foreach { keyword =>
            withClue(s"Value for key '$key' should not contain sensitive keyword '$keyword': ") {
              value.toLowerCase should not include(keyword)
            }
          }
        }
      }
    }

    scenario("appDiscoveryWhitelist contains portal_external_url", VersionOfApi, ApiEndpoint) {
      APIUtil.appDiscoveryWhitelist should contain("portal_external_url")
    }

    scenario("appDiscoveryWhitelist does not include sensitive keys", VersionOfApi, ApiEndpoint) {
      APIUtil.appDiscoveryWhitelist.foreach { key =>
        APIUtil.sensitiveKeywords.foreach { keyword =>
          withClue(s"Whitelisted key '$key' should not contain sensitive keyword '$keyword': ") {
            key.toLowerCase should not include(keyword)
          }
        }
      }
    }
  }

}
