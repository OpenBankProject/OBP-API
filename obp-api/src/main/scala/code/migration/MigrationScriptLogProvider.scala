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

import net.liftweb.util.SimpleInjector


object MigrationScriptLogProvider extends SimpleInjector {

  val migrationScriptLogProvider = new Inject(() => buildOne) {}

  def buildOne: MigrationScriptLogProvider = MappedMigrationScriptLogProvider
}

trait MigrationScriptLogProvider {
  def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String): Boolean
  def isExecuted(name: String): Boolean
  def getMigrationScriptLogs(): List[MigrationScriptLogTrait]
}
