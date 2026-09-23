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

import java.io.File

import code.DynamicData.DynamicDataProvider
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.setup.ServerSetup
import net.liftweb.common.Full
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.scalatest.Tag

import scala.io.Source

/**
 * This suite covers the rule that a Dynamic Entity record's identifier is unique within one space
 * and one entity, rather than across the whole instance.
 *
 * A space is a bank, and a record may be given a natural key instead of a generated identifier: a
 * country code, or the name of a scheme. Two spaces may therefore each legitimately hold a record
 * called DE, and until the unique index carried the bank id and the entity name as well, the second
 * one was refused over an identifier that was never meant to be unique instance-wide.
 *
 * Allowing that needs the bank id column to hold a real value for a record belonging to no bank,
 * because Postgres treats SQL NULLs as distinct inside a unique index, so an index over a nullable
 * bank id would enforce nothing at all for precisely those rows. That value is
 * Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, and the second feature below is what stops a real
 * bank ever being created under the same identifier and quietly merging with the system level data.
 */
class DynamicEntitySystemLevelBankIdTest extends ServerSetup {

  object DynamicEntitySpaceScope extends Tag("DynamicEntitySpaceScope")

  private def dataProvider = DynamicDataProvider.connectorMethodProvider.vend

  /**
   * A record body carrying its own id field, which is what gives the record its natural key.
   * The field name has to match what MappedDynamicDataProvider.getIdName derives from the entity
   * name, otherwise the supplied key is ignored and a UUID is generated instead.
   */
  private def idFieldNameOf(entityName: String): String =
    s"${entityName}_Id".replaceAll("(?<=[a-z0-9])(?=[A-Z])|-", "_").toLowerCase

  private def bodyWithId(entityName: String, id: String): JObject =
    (idFieldNameOf(entityName) -> id) ~ ("name" -> s"record $id")

  feature("A record's identifier is unique within its space, not across the whole instance") {

    scenario("two spaces can each hold a record with the same natural key", DynamicEntitySpaceScope) {
      val entityName = "CountryScopeTest"
      val first  = dataProvider.save(Some("bank_one"), entityName, bodyWithId(entityName, "DE"), None, false)
      val second = dataProvider.save(Some("bank_two"), entityName, bodyWithId(entityName, "DE"), None, false)
      first  shouldBe a[Full[_]]
      second shouldBe a[Full[_]]
      first.map(_.bankId)  shouldBe Full(Some("bank_one"))
      second.map(_.bankId) shouldBe Full(Some("bank_two"))
    }

    scenario("a system level record and a record in a space can share one", DynamicEntitySpaceScope) {
      val entityName = "CountrySystemScopeTest"
      dataProvider.save(None, entityName, bodyWithId(entityName, "DE"), None, false) shouldBe a[Full[_]]
      dataProvider.save(Some("bank_three"), entityName, bodyWithId(entityName, "DE"), None, false) shouldBe a[Full[_]]
    }

    // The sentinel is a storage detail. Everything above the provider still describes a system
    // level record as belonging to no bank at all, which is what keeps the rest of the codebase
    // behaving as before -- including the projection table naming, which hashes this very Option.
    scenario("a system level record still reads back as belonging to no bank", DynamicEntitySpaceScope) {
      val entityName = "CountryReadBackTest"
      dataProvider.save(None, entityName, bodyWithId(entityName, "FR"), None, false)
        .map(_.bankId) shouldBe Full(None)
      dataProvider.get(None, entityName, "FR", None, false).map(_.bankId) shouldBe Full(None)
    }

    scenario("one space still cannot hold the same key twice", DynamicEntitySpaceScope) {
      val entityName = "CountryDuplicateTest"
      dataProvider.save(Some("bank_four"), entityName, bodyWithId(entityName, "ES"), None, false) shouldBe a[Full[_]]
      dataProvider.save(Some("bank_four"), entityName, bodyWithId(entityName, "ES"), None, false) should not be a[Full[_]]
    }
  }

  feature("The system level bank id can never be created as a real bank id") {

    /**
     * Every endpoint accepting a caller supplied bank id writes its own minimum length check rather
     * than calling a shared one, so this walks the sources and holds all of them to the same floor
     * at once. That duplication is exactly why the rule is worth pinning: a further endpoint that
     * forgets the line, or a relaxation of one that has it, would make the sentinel creatable and
     * let a real bank quietly merge with the system level records.
     */
    scenario("every minimum length rule on BANK_ID requires more characters than it has", DynamicEntitySpaceScope) {
      val root = List(new File("src/main/scala/code/api"), new File("obp-api/src/main/scala/code/api"))
        .find(_.isDirectory)
        .getOrElse(fail("cannot locate the api sources - this guard must not pass by failing to look"))

      def scalaFilesUnder(dir: File): List[File] =
        Option(dir.listFiles).toList.flatten.flatMap { f =>
          if (f.isDirectory) scalaFilesUnder(f)
          else if (f.getName.endsWith(".scala")) List(f) else Nil
        }

      // Matches the comparison itself, e.g. `bank.id.length > 3` or `postJson.bank_id.length > 3`.
      val minimumLengthCheck = """\.length\s*>\s*(\d+)""".r

      val checks = scalaFilesUnder(root).flatMap { file =>
        val source = Source.fromFile(file, "UTF-8")
        try {
          source.getLines().toList.zipWithIndex.collect {
            case (line, i) if (line.contains("bank.id") || line.contains("bank_id") ||
                               line.contains("postJson.id")) &&
                              minimumLengthCheck.findFirstMatchIn(line).isDefined =>
              val smallestAccepted = minimumLengthCheck.findFirstMatchIn(line).get.group(1).toInt + 1
              (s"${file.getPath}:${i + 1}", smallestAccepted)
          }
        } finally source.close()
      }

      withClue("no minimum length rule was found at all - the scan itself has stopped working: ") {
        checks.size should be >= 5
      }
      checks.foreach { case (where, smallestAccepted) =>
        withClue(s"$where accepts a bank id of $smallestAccepted characters, which no longer excludes " +
                 s"'$DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID': ") {
          smallestAccepted should be > DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID.length
        }
      }
    }

    // Stated on its own because it is the half of the rule that does NOT hold. The sentinel passes
    // the shared charset and maximum length check, so if the minimum length rules above ever go,
    // there is nothing standing behind them.
    scenario("the shared charset and length rule does not by itself exclude it", DynamicEntitySpaceScope) {
      APIUtil.checkShortString(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID) shouldBe code.util.Helper.SILENCE_IS_GOLDEN
    }
  }
}
