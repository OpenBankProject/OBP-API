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

package code.api.util

import code.setup.PropsReset
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * This suite checks the redirect URL rules that every Consumer redirect URL must meet before it is stored.
 */
class RedirectUrlValidationTest extends FeatureSpec with Matchers with GivenWhenThen with PropsReset {

  feature("RedirectUrlValidation accepts the allowed forms") {
    scenario("https, loopback http, reverse-domain app schemes, several entries and an empty value") {
      List(
        "https://a.com/cb",
        "https://a.com:8443/cb?x=1",
        "http://localhost:5173/cb",
        "http://127.0.0.1:8080/cb",
        "http://[::1]:8080/cb",
        "com.example.app:/cb",
        "x-com.tesobe.helloobp.ios://callback",
        "https://a.com/cb https://b.com/cb",
        "https://a.com/cb,http://localhost:3000/cb",
        "",
        "   "
      ).foreach { value =>
        withClue(value) { RedirectUrlValidation.firstProblem(value) shouldBe None }
      }
    }
  }

  feature("RedirectUrlValidation rejects everything else") {
    scenario("script schemes, wildcards, user information, fragments, public http and missing schemes") {
      List(
        "javascript:alert(1)",
        "data:text/html;base64,PHNjcmlwdD4=",
        "vbscript:x",
        "file:///etc/passwd",
        "https://a.com,javascript:x",
        "https://*.a.com",
        "https://user@evil.com",
        "https://a.com/cb#fragment",
        "http://example.com",
        "myapp://callback",
        "www.openbankproject.com",
        "https:///cb"
      ).foreach { value =>
        withClue(value) { RedirectUrlValidation.firstProblem(value) should not be None }
      }
    }

    scenario("the problem names the rejected entry") {
      RedirectUrlValidation.firstProblem("https://a.com/cb javascript:alert(1)").get should startWith("'javascript:alert(1)'")
    }
  }

  feature("RedirectUrlValidation.validEntries") {
    scenario("keeps only the allowed entries of a stored value") {
      RedirectUrlValidation.validEntries("https://a.com/cb,javascript:x http://example.com http://localhost/cb") shouldBe
        List("https://a.com/cb", "http://localhost/cb")
    }
  }

  feature("redirect_url_allowed_hosts") {
    scenario("unset or empty, it puts no limit on hosts") {
      setPropsValues("redirect_url_allowed_hosts" -> "")
      RedirectUrlValidation.allowedHosts shouldBe Nil
      RedirectUrlValidation.consumerRedirectUrlError("https://any-third-party.example/cb", applyHostList = true) shouldBe None
      RedirectUrlValidation.allowedHostsDescription should include("any host")
    }

    scenario("set, it allows listed hosts, hosts under a listed domain, app schemes and the API's own host") {
      setPropsValues(
        "redirect_url_allowed_hosts" -> "portal.example.com, .bank.example",
        "hostname" -> "https://api.example.org"
      )
      RedirectUrlValidation.allowedHosts should contain allOf ("portal.example.com", ".bank.example", "api.example.org")
      List(
        "https://portal.example.com/cb",
        "https://PORTAL.example.com/cb",
        "https://bank.example/cb",
        "https://app.bank.example/cb",
        "https://api.example.org/cb",
        "com.example.app:/cb"
      ).foreach { value =>
        withClue(value) { RedirectUrlValidation.consumerRedirectUrlError(value, applyHostList = true) shouldBe None }
      }
      RedirectUrlValidation.allowedHostsDescription should include("portal.example.com")
    }

    scenario("set, it refuses other hosts with RedirectUrlHostNotAllowed") {
      setPropsValues(
        "redirect_url_allowed_hosts" -> "portal.example.com, .bank.example",
        "hostname" -> "https://api.example.org"
      )
      List(
        "https://evil.example/cb",
        "https://portal.example.com.evil.example/cb",
        "https://notbank.example/cb",
        "http://localhost:5173/cb"
      ).foreach { value =>
        withClue(value) {
          RedirectUrlValidation.consumerRedirectUrlError(value, applyHostList = true).get should startWith(ErrorMessages.RedirectUrlHostNotAllowed)
        }
      }
    }

    scenario("set, it is not applied where applyHostList is false, and a rule break still wins") {
      setPropsValues("redirect_url_allowed_hosts" -> "portal.example.com")
      RedirectUrlValidation.consumerRedirectUrlError("https://tpp.example/cb", applyHostList = false) shouldBe None
      RedirectUrlValidation.consumerRedirectUrlError("javascript:alert(1)", applyHostList = true).get should startWith(ErrorMessages.InvalidRedirectUrl)
    }
  }
}
