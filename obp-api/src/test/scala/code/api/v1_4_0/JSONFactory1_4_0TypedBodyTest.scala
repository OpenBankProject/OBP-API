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

package code.api.v1_4_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.CustomJsonFormats
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * Covers the JSON Schema made from an endpoint's example body (createTypedBody).
 *
 * Some example bodies, such as the UK Open Banking ones, are written as JSON text and held in a
 * JvalueCaseClass. The schema made from such a body must describe the JSON itself. It must not
 * describe the JvalueCaseClass, whose one field, jvalueToCaseclass, is not part of the API.
 */
class JSONFactory1_4_0TypedBodyTest extends FeatureSpec
  with GivenWhenThen
  with Matchers
  with CustomJsonFormats {

  private val body = JvalueCaseClass(parse(
    """{"Data": {"AccountId": "22289", "Amount": 10.5}, "Links": {"Self": "https://example.com"}}"""
  ))

  feature("The schema of an example body written as JSON text") {

    scenario("The schema describes the JSON, not the wrapper") {
      When("a schema is made from the wrapped body")
      val schema = JSONFactory1_4_0.createTypedBody(body)

      Then("the top-level properties are the body's own fields")
      (schema \ "properties" \ "jvalueToCaseclass") shouldBe JNothing
      (schema \ "properties" \ "Data" \ "properties" \ "AccountId" \ "type").extract[String] shouldBe "string"
      (schema \ "properties" \ "Data" \ "properties" \ "Amount" \ "type").extract[String] shouldBe "number"
      (schema \ "properties" \ "Links" \ "properties" \ "Self" \ "type").extract[String] shouldBe "string"
    }

    scenario("A wrapped body inside another body is described by its JSON too") {
      When("a schema is made from a case class holding a wrapped body")
      val schema = parse(JSONFactory1_4_0.translateEntity(JvalueCaseClassHolder(body), false))

      Then("the field's schema describes the JSON inside the wrapper")
      (schema \ "properties" \ "inner" \ "properties" \ "jvalueToCaseclass") shouldBe JNothing
      (schema \ "properties" \ "inner" \ "properties" \ "Data" \ "type").extract[String] shouldBe "object"
    }
  }

  feature("Empty example values") {

    scenario("An empty object or a null in the example puts no limit on the field") {
      When("a schema is made from an example with an empty object and a null")
      val schema = JSONFactory1_4_0.createTypedBody(JvalueCaseClass(parse(
        """{"Meta": {"FirstAvailableDateTime": {}, "LastAvailableDateTime": null, "TotalPages": 1}}"""
      )))

      Then("those fields have an empty schema, which accepts any value")
      (schema \ "properties" \ "Meta" \ "properties" \ "FirstAvailableDateTime") shouldBe JObject(Nil)
      (schema \ "properties" \ "Meta" \ "properties" \ "LastAvailableDateTime") shouldBe JObject(Nil)
      (schema \ "properties" \ "Meta" \ "properties" \ "TotalPages" \ "type").extract[String] shouldBe "integer"
    }
  }

  feature("Required fields") {

    scenario("A field of a Scala class is required unless it is an Option") {
      When("a schema is made from a case class with plain and optional fields")
      val schema = JSONFactory1_4_0.createTypedBody(
        TypedBodyExample("gh.29.uk", Some("A bank"), None, List(TypedBodyItem("1", None))))

      Then("the plain fields are required and the optional ones are not")
      (schema \ "required").extract[List[String]] shouldBe List("bank_id", "items")
      (schema \ "properties" \ "full_name" \ "type").extract[String] shouldBe "string"

      And("the same rule applies to the items of a list")
      (schema \ "properties" \ "items" \ "items" \ "required").extract[List[String]] shouldBe List("id")
    }

    scenario("No field of an example written as JSON text is required") {
      When("a schema is made from a wrapped body")
      val schema = JSONFactory1_4_0.createTypedBody(body)

      Then("it lists no required fields, because the JSON gives no types")
      (schema \ "required") shouldBe JNothing
      (schema \ "properties" \ "Data" \ "required") shouldBe JNothing
    }
  }
}

case class JvalueCaseClassHolder(inner: JvalueCaseClass)
case class TypedBodyItem(id: String, note: Option[String])
case class TypedBodyExample(bank_id: String, full_name: Option[String], logo: Option[String], items: List[TypedBodyItem])
