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
package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.files.{File, ScanStatus}
import code.model.dataAccess.{MappedBank, SelfServiceBank}
import code.util.BankNameGenerator
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

/**
 * OBP now records the banks made with Create My Bank in SelfServiceBank, because anyone may make one and grant
 * themselves its Roles, so those Roles vouch for nothing.
 *
 * 1. A bank in MappedBank made before the record existed is recorded when its id has exactly the shape Create My Bank
 *    gives (BankNameGenerator.isGenerated: three words from its lists and a four-character hex suffix), with its
 *    CreatedByUserId. A bank named by hand practically never has that shape. Create My Bank stored its banks through
 *    the connector, so one made on a backend connector is not in MappedBank and is not found here.
 * 2. In a self-service bank, a file written before scanning existed (scan status NULL or empty, which elsewhere counts
 *    as clean) is set to pending, so its content waits for a scan like a new upload there.
 */
object MigrationOfSelfServiceBanks {

  def markAndHoldFiles(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val report = scala.collection.mutable.ListBuffer[String]()

    if (DbFunction.tableExists(MappedBank)) {
      val generated = MappedBank.findAll().filter(bank => BankNameGenerator.isGenerated(bank.permalink.get))
        .filterNot(bank => SelfServiceBank.isSelfService(bank.permalink.get))
      generated.foreach(bank => SelfServiceBank.record(bank.permalink.get, bank.CreatedByUserId.get))
      report += s"Banks: recorded ${generated.size} as self-service"
    }

    if (DbFunction.tableExists(File) && DbFunction.tableExists(SelfServiceBank)) {
      val selfServiceBankIds = SelfServiceBank.findAll().map(_.BankId.get)
      // SQL rather than Mapper: Mapper would read a NULL status back as its default.
      val held = DB.use(DefaultConnectionIdentifier) { connection =>
        selfServiceBankIds.map { bankId =>
          val statement = connection.prepareStatement(
            s"UPDATE ${File._dbTableNameLC} SET ${File.ScanStatus.dbColumnName} = ? " +
              s"WHERE ${File.BankId.dbColumnName} = ? AND (${File.ScanStatus.dbColumnName} IS NULL OR ${File.ScanStatus.dbColumnName} = '')")
          try {
            statement.setString(1, ScanStatus.Pending)
            statement.setString(2, bankId)
            statement.executeUpdate()
          } finally statement.close()
        }.sum
      }
      report += s"Files: set $held file(s) in self-service banks to pending"
    }

    saveLog(name, APIUtil.gitCommit, isSuccessful = true, startDate, System.currentTimeMillis(), report.mkString("; "))
    true
  }
}
