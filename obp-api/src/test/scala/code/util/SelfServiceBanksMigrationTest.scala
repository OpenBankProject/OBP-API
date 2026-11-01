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
package code.util

import code.api.util.APIUtil
import code.api.util.migration.MigrationOfSelfServiceBanks
import code.files.{File, Files, ScanStatus}
import code.model.dataAccess.{MappedBank, SelfServiceBank}
import code.setup.ServerSetup
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier
import org.scalatest.Tag

/**
 * Recognising the banks Create My Bank made before banks recorded it, and holding their unscanned files as pending.
 */
class SelfServiceBanksMigrationTest extends ServerSetup {

  object SelfServiceBanks extends Tag("SelfServiceBanks")

  private val suffix = APIUtil.generateUUID().take(8)

  feature("Self-service banks") {

    scenario("A bank id has the generated shape only with words from the lists and a hex suffix", SelfServiceBanks) {
      BankNameGenerator.isGenerated(BankNameGenerator.generate().bankId) shouldBe true
      BankNameGenerator.isGenerated("gh.29.uk") shouldBe false
      BankNameGenerator.isGenerated("granite-astra-falcon") shouldBe false
      BankNameGenerator.isGenerated("granite-astra-falcon-zzzz") shouldBe false
      BankNameGenerator.isGenerated("notaword-astra-falcon-4f2a") shouldBe false
    }

    scenario("A generated-looking bank is recorded as self-service and its unscanned files wait; other banks are left", SelfServiceBanks) {
      val generated = BankNameGenerator.generate().bankId
      val named = s"hand-named-$suffix"
      List(generated, named).foreach { id =>
        MappedBank.create.permalink(id).fullBankName(id).shortBankName(id).saveMe()
      }
      def oldFile(bankId: String): String = {
        val file = Files.provider.vend.createFile(bankId, "0" * 64, "image/png", "old.png", isPublic = false,
          "someone", Array[Byte](1, 2, 3)).openOrThrowException("file")
        // As stored before scanning existed: no status at all.
        DB.use(DefaultConnectionIdentifier) { connection =>
          val statement = connection.prepareStatement(
            s"UPDATE ${File._dbTableNameLC} SET ${File.ScanStatus.dbColumnName} = NULL WHERE ${File.FileId.dbColumnName} = ?")
          try { statement.setString(1, file.fileId); statement.executeUpdate() } finally statement.close()
        }
        file.fileId
      }
      val heldFile = oldFile(generated)
      val keptFile = oldFile(named)

      MigrationOfSelfServiceBanks.markAndHoldFiles(s"testSelfServiceBanks_$suffix") shouldBe true
      SelfServiceBank.isSelfService(generated) shouldBe true
      SelfServiceBank.isSelfService(named) shouldBe false
      Files.provider.vend.getFile(generated, heldFile).map(_.scanStatus).openOr("") shouldBe ScanStatus.Pending
      Files.provider.vend.getFile(named, keptFile).map(_.scanStatus).openOr("") shouldBe ScanStatus.Clean
    }
  }
}
