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
package code.api.v6_0_0

import code.DynamicData.DynamicDataProvider
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider, DynamicEntityT}
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.JsonAliases.parse
import org.json4s.JsonAST.{JField, JObject, JString, JValue}
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A `reference:` field points at a record in its own space, and nowhere else.
 *
 * A space is the bank a Dynamic Entity belongs to; the instance wide one is the system space. Two
 * spaces may each hold an entity of the same name, and since the record id became unique per space
 * rather than per instance (`DynamicData.dbIndexes`), they may each hold a record of the same id as
 * well. Validation that ignores the space therefore answers a question nobody asked: it says a
 * reference is good because *somewhere* on the instance a record with that id exists, which may be
 * another bank's. The joins have always been per space (`Http4sDynamicEntity.childJoinInfo`), so
 * this is validation catching up with them rather than a new restriction.
 *
 * Cross space links are not being forbidden forever, only kept out of `reference:`. When they are
 * wanted they get a name of their own, such as `cross-space-ref`, so that the meaning of an
 * existing definition never changes underneath the data. See DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md.
 */
class DynamicEntityReferenceSpaceTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  private val owner = "reference-space-test-owner"
  private val suffix = java.util.UUID.randomUUID().toString.take(8)
  private val parentEntity = s"parent_$suffix"
  private val childEntity = s"child_$suffix"
  private lazy val otherSpace = Some(testBankId1.value)

  /** The definition JSON in the shape the validating constructor expects. */
  private def definitionJson(entity: String, propertiesJson: String): JObject = parse(
    s"""{
       |  "$entity": {
       |    "description": "An entity used by DynamicEntityReferenceSpaceTest.",
       |    "required": [],
       |    "properties": $propertiesJson
       |  }
       |}""".stripMargin).asInstanceOf[JObject]

  private val plainProperties =
    """{ "name": { "type": "string", "example": "Alice", "description": "a name" } }"""

  private def referenceProperties(target: String) =
    s"""{ "parent_ref": { "type": "reference:$target", "example": "00000000-0000-0000-0000-000000000000", "description": "the parent" } }"""

  /** Register a definition without going through the validating constructor. */
  private def register(entity: String, propertiesJson: String, space: Option[String]): DynamicEntityT =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(
      DynamicEntityCommons(
        entityName = entity,
        metadataJson = s"""{"$entity":{"description":"d","required":[],"properties":$propertiesJson}}""",
        dynamicEntityId = None,
        userId = owner,
        bankId = space,
        hasPersonalEntity = false,
        hasCommunityAccess = true
      )
    ).openOrThrowException(s"could not register $entity")

  /** Save a record in one space and return its id. */
  private def saveRecord(entity: String, space: Option[String]): String = {
    val id = java.util.UUID.randomUUID().toString
    val body = JObject(List(
      JField(s"${entity}_id", JString(id)),
      JField("name", JString("Alice"))
    ))
    DynamicDataProvider.connectorMethodProvider.vend
      .save(space, entity, body, Some(owner), false)
      .openOrThrowException(s"could not save a $entity record")
    id
  }

  private def validationErrorOf(entity: DynamicEntityT, referenceValue: String): Option[String] = {
    val body = JObject(List(
      JField(s"${childEntity}_id", JString(java.util.UUID.randomUUID().toString)),
      JField("parent_ref", JString(referenceValue))
    ))
    Await.result(entity.validateEntityJson(body, None), 30.seconds)
  }

  feature(s"A reference points inside its own space - $VersionOfApi") {

    scenario("A definition cannot declare a reference to an entity in another space", VersionOfApi) {
      Given(s"$parentEntity exists in the system space only")
      register(parentEntity, plainProperties, None)

      When(s"a definition in a bank space declares reference:$parentEntity")
      val thrown = intercept[IllegalArgumentException] {
        DynamicEntityCommons(
          definitionJson(childEntity, referenceProperties(parentEntity)),
          None, owner, otherSpace)
      }

      Then("it is refused, because that entity is not in this space")
      withClue(s"the message should name the offending type. Message was: ${thrown.getMessage}\n") {
        thrown.getMessage should include("parent_ref")
      }
    }

    scenario("A record's reference cannot resolve to a record in another space", VersionOfApi) {
      Given(s"$parentEntity exists in the system space and in a bank space, each holding one record")
      register(parentEntity, plainProperties, None)
      register(parentEntity, plainProperties, otherSpace)
      val idInTheSystemSpace = saveRecord(parentEntity, None)
      val idInTheBankSpace = saveRecord(parentEntity, otherSpace)

      And(s"$childEntity lives in the system space and references $parentEntity")
      val child = register(childEntity, referenceProperties(parentEntity), None)

      When("a record references the parent in its own space")
      Then("it is accepted")
      validationErrorOf(child, idInTheSystemSpace) should equal(None)

      When("a record references a parent record that lives in the other space")
      val error = validationErrorOf(child, idInTheBankSpace)

      Then("it is refused, although a record with that id does exist somewhere on the instance")
      withClue("a reference must not resolve across spaces: ") {
        error.isDefined should equal(true)
      }
    }
  }
}
