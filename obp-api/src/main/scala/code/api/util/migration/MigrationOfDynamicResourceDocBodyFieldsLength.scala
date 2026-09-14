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
import code.dynamicResourceDoc.DynamicResourceDoc
import net.liftweb.common.Full
import net.liftweb.mapper.Schemifier

object MigrationOfDynamicResourceDocBodyFieldsLength {

  def alterColumnsType(name: String): Boolean = {
    DbFunction.tableExists(DynamicResourceDoc) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """
                    |-- A realistic dynamic-endpoint request/response body example (or full error
                    |-- response list) routinely exceeds varchar(255) once it has more than a
                    |-- handful of JSON fields
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN examplerequestbody VARCHAR(MAX);
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN successresponsebody VARCHAR(MAX);
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN errorresponsebodies VARCHAR(MAX);
                    |""".stripMargin
              case _ =>
                () =>
                  """
                    |-- A realistic dynamic-endpoint request/response body example (or full error
                    |-- response list) routinely exceeds varchar(255) once it has more than a
                    |-- handful of JSON fields
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN examplerequestbody TYPE text;
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN successresponsebody TYPE text;
                    |ALTER TABLE dynamicresourcedoc ALTER COLUMN errorresponsebodies TYPE text;
                    |""".stripMargin
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$executedSql
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${DynamicResourceDoc._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
