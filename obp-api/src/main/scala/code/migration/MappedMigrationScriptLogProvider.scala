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

package code.migration

import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.mapper.{By, OrderBy, Descending}

object MappedMigrationScriptLogProvider extends MigrationScriptLogProvider with MdcLoggable {
  override def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String): Boolean = {
    MigrationScriptLog.find(By(MigrationScriptLog.Name, name), By(MigrationScriptLog.IsSuccessful, isSuccessful)) match {
      case Full(log) => 
        log
          .Name(name)
          .CommitId(commitId)
          .IsSuccessful(isSuccessful)
          .StartDate(startDate)
          .EndDate(endDate)
          .Remark(comment)
          .save
      case _ =>
        MigrationScriptLog
          .create
          .Name(name)
          .CommitId(commitId)
          .IsSuccessful(isSuccessful)
          .StartDate(startDate)
          .EndDate(endDate)
          .Remark(comment)
          .save
    }
  }
  override def isExecuted(name: String): Boolean = {
    MigrationScriptLog.find(
      By(MigrationScriptLog.Name, name),
      By(MigrationScriptLog.IsSuccessful, true)
    ).isDefined
  }

  override def getMigrationScriptLogs(): List[MigrationScriptLogTrait] = {
    MigrationScriptLog.findAll(OrderBy(MigrationScriptLog.createdAt, Descending))
  }
}

