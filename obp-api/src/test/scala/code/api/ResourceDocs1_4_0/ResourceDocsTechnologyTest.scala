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

package code.api.ResourceDocs1_4_0

import org.json4s._
import code.api.Constant
import code.setup.{PropsReset, ServerSetup}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JArray, JNothing, JNull, JString}

class ResourceDocsTechnologyTest extends ServerSetup with PropsReset {
  private val v600 = ApiVersion.v6_0_0.toString
  private val v500 = ApiVersion.v5_0_0.toString

  feature("ResourceDocs implemented_by.technology") {

    scenario(s"$v600 resource-docs should include implemented_by.technology") {
      setPropsValues("resource_docs_requires_role" -> "false")

      val request = (baseRequest / "obp" / v600 / "resource-docs" / v600 / "obp").GET
      val response = makeGetRequest(request)

      response.code should equal(200)
      (response.body \ "resource_docs") match {
        case JArray(docs) =>
          // At least one doc should have a technology field (http4s or liftweb).
          // v5.0.0 has no technology field at all; v6.0.0 exposes it for all docs.
          val hasTechnology = docs.exists { doc =>
            (doc \ "implemented_by" \ "technology") match {
              case JString(t) => t == Constant.TECHNOLOGY_HTTP4S || t == Constant.TECHNOLOGY_LIFTWEB
              case _          => false
            }
          }
          hasTechnology should be(true)
        case _ =>
          fail("Expected resource_docs field to be an array")
      }
    }

    scenario(s"$v500 resource-docs should not include implemented_by.technology") {
      setPropsValues("resource_docs_requires_role" -> "false")

      val request = (baseRequest / "obp" / v500 / "resource-docs" / v500 / "obp").GET
      val response = makeGetRequest(request)

      response.code should equal(200)
      (response.body \ "resource_docs") match {
        case JArray(docs) =>
          val technology = docs.head \ "implemented_by" \ "technology"
          technology match {
            case JNothing | JNull => succeed
            case _ => fail("Expected implemented_by.technology to be absent for v5.0.0 resource-docs")
          }
        case _ =>
          fail("Expected resource_docs field to be an array")
      }
    }
  }
}
