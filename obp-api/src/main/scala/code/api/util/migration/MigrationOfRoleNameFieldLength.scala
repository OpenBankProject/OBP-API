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
import code.entitlement.MappedEntitlement
import code.entitlementrequest.MappedEntitlementRequest
import code.scope.MappedScope
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfRoleNameFieldLength {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def alterRoleNameLength(name: String): Boolean = {
    val entitlementTableExists = DbFunction.tableExists(MappedEntitlement)
    val entitlementRequestTableExists = DbFunction.tableExists(MappedEntitlementRequest)
    val scopeTableExists = DbFunction.tableExists(MappedScope)

    if (!entitlementTableExists || !entitlementRequestTableExists || !scopeTableExists) {
      val startDate = System.currentTimeMillis()
      val commitId: String = APIUtil.gitCommit
      val isSuccessful = false
      val endDate = System.currentTimeMillis()
      val comment: String =
        s"""One or more required tables do not exist:
           |entitlement table exists: $entitlementTableExists
           |entitlementrequest table exists: $entitlementRequestTableExists
           |scope table exists: $scopeTableExists
           |""".stripMargin
      saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
      return isSuccessful
    }

    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    var isSuccessful = false

    val executedSql =
      DbFunction.maybeWrite(true, Schemifier.infoF _) {
        APIUtil.getPropsValue("db.driver") match {
          case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
            () =>
              """
                |ALTER TABLE mappedentitlement ALTER COLUMN mrolename varchar(255);
                |ALTER TABLE mappedentitlementrequest ALTER COLUMN mrolename varchar(255);
                |ALTER TABLE mappedscope ALTER COLUMN mrolename varchar(255);
                |""".stripMargin
          case _ =>
            () =>
              """
                |ALTER TABLE mappedentitlement ALTER COLUMN mrolename TYPE varchar(255);
                |ALTER TABLE mappedentitlementrequest ALTER COLUMN mrolename TYPE varchar(255);
                |ALTER TABLE mappedscope ALTER COLUMN mrolename TYPE varchar(255);
                |""".stripMargin
        }
      }

    val endDate = System.currentTimeMillis()
    val comment: String =
      s"""Executed SQL: 
         |$executedSql
         |
         |Increased mrolename column length from 64 to 255 characters in three tables:
         |  - mappedentitlement
         |  - mappedentitlementrequest
         |  - mappedscope
         |
         |This allows for longer dynamic entity names and role names.
         |""".stripMargin
    isSuccessful = true
    saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
    isSuccessful
  }
}