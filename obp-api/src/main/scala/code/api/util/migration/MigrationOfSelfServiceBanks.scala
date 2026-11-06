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
import code.api.util.DoobieUtil
import code.files.ScanStatus
import code.model.dataAccess.{MappedBank, SelfServiceBank}
import code.util.BankNameGenerator
import doobie.implicits._

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

    if (DbFunction.tableExistsByName("mappedbank")) {
      val generated = MappedBank.findAll().filter(bank => BankNameGenerator.isGenerated(bank.bankId.value))
        .filterNot(bank => SelfServiceBank.isSelfService(bank.bankId.value))
      generated.foreach(bank => SelfServiceBank.record(bank.bankId.value, bank.createdByUserId))
      report += s"Banks: recorded ${generated.size} as self-service"
    }

    if (DbFunction.tableExistsByName("file_t") && DbFunction.tableExistsByName("selfservicebank")) {
      // A NULL or empty status counts as clean elsewhere, so both are moved to pending.
      val held = SelfServiceBank.allBankIds().map { bankId =>
        DoobieUtil.runUpdate(
          sql"""UPDATE file_t SET scanstatus = ${ScanStatus.Pending}
                WHERE bankid = $bankId AND (scanstatus IS NULL OR scanstatus = '')""".update.run)
      }.sum
      report += s"Files: set $held file(s) in self-service banks to pending"
    }

    saveLog(name, APIUtil.gitCommit, isSuccessful = true, startDate, System.currentTimeMillis(), report.mkString("; "))
    true
  }
}
