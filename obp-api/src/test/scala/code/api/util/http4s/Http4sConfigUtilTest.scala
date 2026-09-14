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

package code.api.util.http4s

import org.scalatest.{FlatSpec, Matchers}

class Http4sConfigUtilTest extends FlatSpec with Matchers {
  
  "parseHostname" should "extract hostname from plain IP address" in {
    Http4sConfigUtil.parseHostname("127.0.0.1") shouldBe "127.0.0.1"
  }
  
  it should "extract hostname from HTTP URI" in {
    Http4sConfigUtil.parseHostname("http://127.0.0.1:8080") shouldBe "127.0.0.1"
  }
  
  it should "extract hostname from HTTPS URI" in {
    Http4sConfigUtil.parseHostname("https://api.example.com") shouldBe "api.example.com"
  }
  
  it should "handle localhost" in {
    Http4sConfigUtil.parseHostname("localhost") shouldBe "localhost"
  }
  
  it should "handle URI with path" in {
    Http4sConfigUtil.parseHostname("http://example.com/path") shouldBe "example.com"
  }
  
  it should "trim whitespace" in {
    Http4sConfigUtil.parseHostname("  127.0.0.1  ") shouldBe "127.0.0.1"
  }
  
  it should "handle URI with port" in {
    Http4sConfigUtil.parseHostname("http://localhost:8080") shouldBe "localhost"
  }
  
  it should "handle domain names" in {
    Http4sConfigUtil.parseHostname("example.com") shouldBe "example.com"
  }
  
  it should "handle full URL with protocol, port and path" in {
    Http4sConfigUtil.parseHostname("https://api.example.com:443/v1/endpoint") shouldBe "api.example.com"
  }
}
