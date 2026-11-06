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
import code.api.util.APIUtil
import code.setup.ServerSetup
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.scalatest.Tag

/**
 * This suite covers the created and updated timestamps on a Dynamic Entity record.
 *
 * A record used to carry no timestamps at all, so nobody could tell when it was written or last
 * changed. That matters wherever records are someone's own data, for example a farmer's soil samples,
 * because the provenance of such a record has to be stated. The records table now has the columns
 * createdat and updatedat. A record written before those columns existed holds NULL in both, and
 * the provider reports that as None instead of inventing a time.
 */
class DynamicDataTimestampsTest extends ServerSetup {

  object DynamicDataTimestamps extends Tag("DynamicDataTimestamps")

  private def dataProvider = DynamicDataProvider.connectorMethodProvider.vend

  /** The id field name MappedDynamicDataProvider.getIdName derives from the entity name. */
  private def idFieldNameOf(entityName: String): String =
    s"${entityName}_Id".replaceAll("(?<=[a-z0-9])(?=[A-Z])|-", "_").toLowerCase

  private def bodyWithId(entityName: String, id: String, name: String): JObject =
    (idFieldNameOf(entityName) -> id) ~ ("name" -> name)

  private def uniqueEntityName(prefix: String): String = s"${prefix}${APIUtil.generateUUID().take(8).replace("-", "")}"

  feature("A Dynamic Entity record carries created and updated timestamps") {

    scenario("a new record has both timestamps", DynamicDataTimestamps) {
      val entityName = uniqueEntityName("TimestampCreate")
      val before = System.currentTimeMillis()
      val saved = dataProvider.save(Some("bank_timestamps"), entityName, bodyWithId(entityName, "R1", "first"), None, false)
        .openOrThrowException("the record should save")

      saved.createdDate.isDefined should equal(true)
      saved.updatedDate.isDefined should equal(true)
      // A column may store whole seconds only, so allow a second of rounding either way.
      saved.createdDate.get.getTime should be >= (before - 1000)

      And("the timestamps read back from the database")
      val readBack = dataProvider.get(Some("bank_timestamps"), entityName, "R1", None, false)
        .openOrThrowException("the record should be found")
      readBack.createdDate.isDefined should equal(true)
      readBack.updatedDate.isDefined should equal(true)
    }

    scenario("an update moves the updated timestamp and keeps the created one", DynamicDataTimestamps) {
      val entityName = uniqueEntityName("TimestampUpdate")
      val saved = dataProvider.save(Some("bank_timestamps"), entityName, bodyWithId(entityName, "R2", "first"), None, false)
        .openOrThrowException("the record should save")
      val createdOnSave = saved.createdDate.get

      // Longer than one second, so the change shows even where the column stores whole seconds.
      Thread.sleep(1100)
      dataProvider.update(Some("bank_timestamps"), entityName, bodyWithId(entityName, "R2", "second"), "R2", None, false)
        .openOrThrowException("the record should update")

      val readBack = dataProvider.get(Some("bank_timestamps"), entityName, "R2", None, false)
        .openOrThrowException("the record should be found")
      readBack.createdDate.map(_.getTime / 1000) should equal(Some(createdOnSave.getTime / 1000))
      readBack.updatedDate.get.after(readBack.createdDate.get) should equal(true)
    }

    scenario("a record written before the columns existed reports no timestamps", DynamicDataTimestamps) {
      val entityName = uniqueEntityName("TimestampLegacy")
      dataProvider.save(Some("bank_timestamps"), entityName, bodyWithId(entityName, "R3", "first"), None, false)
        .openOrThrowException("the record should save")

      Given("a row whose timestamp columns are NULL, as on an instance upgraded from before they existed")
      DB.use(DefaultConnectionIdentifier) { connection =>
        val statement = connection.prepareStatement(
          "UPDATE dynamicdata SET createdat = NULL, updatedat = NULL WHERE dynamicentityname = ?")
        try { statement.setString(1, entityName); statement.executeUpdate() } finally statement.close()
      }

      Then("both timestamps read as None")
      val legacy = dataProvider.get(Some("bank_timestamps"), entityName, "R3", None, false)
        .openOrThrowException("the record should be found")
      legacy.createdDate should equal(None)
      legacy.updatedDate should equal(None)

      When("the record is updated")
      dataProvider.update(Some("bank_timestamps"), entityName, bodyWithId(entityName, "R3", "second"), "R3", None, false)
        .openOrThrowException("the record should update")

      Then("only the updated timestamp is filled, because the creation time was never recorded")
      val updated = dataProvider.get(Some("bank_timestamps"), entityName, "R3", None, false)
        .openOrThrowException("the record should be found")
      updated.createdDate should equal(None)
      updated.updatedDate.isDefined should equal(true)
    }
  }
}
