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

package code.api.dynamic.entity.helper

import org.scalatest.{FlatSpec, Matchers}

/**
 * Pure unit tests for [[DynamicEntityHelper.isSchemaCompatibleChange]]: the rule that decides whether a
 * definition update may be applied to a Dynamic Entity that already has rows. No server / DB.
 *
 * metadataJson is stored as the whole definition request, `{"FooBar": {...}}`; the bare inner object is
 * accepted too.
 */
class SchemaCompatibleChangeSpec extends FlatSpec with Matchers {

  private def wrap(inner: String, name: String = "FooBar") = s"""{"$name":$inner}"""

  private val baseInner =
    """{"description":"d","required":["name"],"properties":{
      |  "name":{"type":"string","example":"x","maxLength":20},
      |  "number":{"type":"integer","example":1},
      |  "site_ref":{"type":"reference:Site","example":"abc"}}}""".stripMargin
  private val base = wrap(baseInner)

  private def ok(newInner: String, newName: String = "FooBar") =
    DynamicEntityHelper.isSchemaCompatibleChange("FooBar", base, newName, wrap(newInner, newName))

  "isSchemaCompatibleChange" should "accept the identical definition, in outer or bare form" in {
    ok(baseInner) shouldBe true
    DynamicEntityHelper.isSchemaCompatibleChange("FooBar", base, "FooBar", baseInner) shouldBe true
  }

  it should "accept adding indexed / index / description / example / min-max length / role settings" in {
    ok("""{"description":"changed","required":["name"],"properties":{
        |  "name":{"type":"string","example":"y","maxLength":40,"minLength":1,"indexed":true,"description":"n","read_role_required":true},
        |  "number":{"type":"integer","example":2,"indexed":true,"index":"scalar","write_role":"CanX"},
        |  "site_ref":{"type":"reference:Site","example":"def","indexed":true}}}""".stripMargin) shouldBe true
  }

  it should "accept shrinking required" in {
    ok("""{"required":[],"properties":{"name":{"type":"string","example":"x"},"number":{"type":"integer","example":1},"site_ref":{"type":"reference:Site","example":"a"}}}""") shouldBe true
  }

  it should "reject growing required" in {
    ok("""{"required":["name","number"],"properties":{"name":{"type":"string","example":"x"},"number":{"type":"integer","example":1},"site_ref":{"type":"reference:Site","example":"a"}}}""") shouldBe false
  }

  it should "reject a changed property type (including a changed reference target)" in {
    ok("""{"required":["name"],"properties":{"name":{"type":"string","example":"x"},"number":{"type":"string","example":"1"},"site_ref":{"type":"reference:Site","example":"a"}}}""") shouldBe false
    ok("""{"required":["name"],"properties":{"name":{"type":"string","example":"x"},"number":{"type":"integer","example":1},"site_ref":{"type":"reference:Plot","example":"a"}}}""") shouldBe false
  }

  it should "reject an added or removed property" in {
    ok("""{"required":["name"],"properties":{"name":{"type":"string","example":"x"},"number":{"type":"integer","example":1},"site_ref":{"type":"reference:Site","example":"a"},"extra":{"type":"string","example":"e"}}}""") shouldBe false
    ok("""{"required":["name"],"properties":{"name":{"type":"string","example":"x"},"number":{"type":"integer","example":1}}}""") shouldBe false
  }

  it should "reject a renamed entity and unparseable input" in {
    ok(baseInner, newName = "FooBaz") shouldBe false
    DynamicEntityHelper.isSchemaCompatibleChange("FooBar", base, "FooBar", "not json") shouldBe false
    DynamicEntityHelper.isSchemaCompatibleChange("FooBar", "not json", "FooBar", base) shouldBe false
    DynamicEntityHelper.isSchemaCompatibleChange("FooBar", base, "FooBar", """{"Other":{"properties":{}}}""") shouldBe false
  }
}
