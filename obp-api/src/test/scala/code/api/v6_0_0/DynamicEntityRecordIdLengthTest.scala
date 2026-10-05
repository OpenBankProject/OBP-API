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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.JsonAliases._
import org.json4s.JsonAST.JArray
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * These tests cover the length of the id of a single Dynamic Entity record.
 *
 * A caller may supply that id in the request body rather than let one be generated, which is how an
 * entity is given a natural key such as a country code or the name of a scheme. The column holding the
 * id used to be 36 characters wide, the length of a UUID, so any natural key longer than that failed
 * deep in the database driver and the caller was told only "value too long for type character
 * varying(36)". The column is now 255 characters wide, the same width as the two other columns that
 * hold this same id, and an id longer than the column is refused up front with a message that says so.
 *
 * The first scenario locks in the width, the second locks in the error.
 */
class DynamicEntityRecordIdLengthTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)

  private val entityName = "record_id_length_probe"

  /** A single string field, plus the personal endpoints so no entity role is needed to write a record. */
  private def entityDefinition: JValue =
    ("entity_name" -> entityName) ~
      ("has_personal_entity" -> true) ~
      ("schema" ->
        ("description" -> "Entity used to test the length of a record id.") ~
          ("required" -> List("name")) ~
          ("properties" ->
            ("name" -> ("type" -> "string") ~ ("example" -> "Alice"))))

  private def createEntityDefinition(): String = {
    Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanCreateDynamicEntityDefinition.toString)
    val request = (v6_0_0_Request / "management" / "system-dynamic-entities").POST <@ (user1)
    val response = makePostRequest(request, write(entityDefinition))
    response.code should equal(201)
    (response.body \ "dynamic_entity_id").extract[String]
  }

  private def deleteEntityDefinition(dynamicEntityId: String): Unit = {
    Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, CanDeleteDynamicEntityDefinition.toString)
    makeDeleteRequest((v6_0_0_Request / "management" / "system-dynamic-entities" / dynamicEntityId).DELETE <@ (user1))
  }

  /** The name of the id field of this entity, as the API generates it: `<entity name>_id`. */
  private val recordIdField = s"${entityName}_id"

  feature("The id of a Dynamic Entity record may be supplied by the caller") {

    scenario("An id longer than a UUID is stored and can be read back", VersionOfApi) {
      val dynamicEntityId = createEntityDefinition()
      try {
        // The exact natural key that used to fail: 53 characters, well past the old 36 character column.
        val naturalKey = "OTHER_PERMANENTLY_CHEMICALLY_BOUND_CARBON_IN_PRODUCTS"
        naturalKey.length should be > 36

        When("a record is created with that id in the request body")
        val createRequest = (dynamicEntity_Request / "my" / entityName).POST <@ (user1)
        val createResponse = makePostRequest(createRequest, write((recordIdField -> naturalKey) ~ ("name" -> "Alice")))

        Then("the record is created and keeps the id it was given")
        createResponse.code should equal(201)
        (createResponse.body \ entityName \ recordIdField).extract[String] should equal(naturalKey)

        And("it can be read back by that same id")
        val getResponse = makeGetRequest((dynamicEntity_Request / "my" / entityName / naturalKey).GET <@ (user1))
        getResponse.code should equal(200)
        (getResponse.body \ entityName \ recordIdField).extract[String] should equal(naturalKey)
        (getResponse.body \ entityName \ "name").extract[String] should equal("Alice")
      } finally {
        deleteEntityDefinition(dynamicEntityId)
      }
    }

    scenario("An id longer than the column is refused with a clear error and nothing is stored", VersionOfApi) {
      val dynamicEntityId = createEntityDefinition()
      try {
        val tooLongId = "x" * 256

        When("a record is created with an id longer than the column")
        val createRequest = (dynamicEntity_Request / "my" / entityName).POST <@ (user1)
        val createResponse = makePostRequest(createRequest, write((recordIdField -> tooLongId) ~ ("name" -> "Alice")))

        Then("the request is rejected as a bad request, not as a server error")
        createResponse.code should equal(400)

        And("the message names the limit and the length that was given")
        val message = createResponse.body.extract[ErrorMessage].message
        message should include(DynamicEntityRecordIdTooLong)
        message should include("255")
        message should include("256")

        And("no record was stored")
        val listResponse = makeGetRequest((dynamicEntity_Request / "my" / entityName).GET <@ (user1))
        listResponse.code should equal(200)
        (listResponse.body \ s"${entityName}_list").asInstanceOf[JArray].arr.size should equal(0)
      } finally {
        deleteEntityDefinition(dynamicEntityId)
      }
    }
  }
}
