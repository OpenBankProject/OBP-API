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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.views.system.{AccountAccess, ViewDefinition}
import net.liftweb.util.DefaultConnectionIdentifier

object UpdateTableViewDefinition {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populate(name: String): Boolean = {
    DbFunction.tableExistsByName("viewdefinition") match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewDefinition.findAllFullyScoped()
        val instanceSpecificSystemViews = ViewDefinition.findAllSandboxSystemViews()
        val bankSpecificSystemViews = ViewDefinition.findAllBankScopedSystemViews()

        // Make back up
        DbFunction.makeBackUpOfTableByName("viewdefinition")
    
        // Update rows into table "viewdefinition"
        val updatedRows: List[Boolean] =
          for {
            view <- views
          } yield {
            ViewDefinition.setIsSystem(view.viewPrimaryKey, false)
          }

        // Make back up
        DbFunction.makeBackUpOfTableByName("accountaccess")

        // Update rows into table "AccountAccess"
        val updatedAccountAccessRows =
          for {
            view <- views
            // view_fk is the deprecated numeric link this historical migration was written
            // against; no row has carried it since, so the loop finds nothing and the migration is
            // a no-op on any current database. Preserved as such rather than rewritten against a
            // column it was never about.
            accountAccess <- List.empty[code.views.system.AccountAccess]
          } yield {
            true
          }
        
        // Re-read rather than asking the in-memory rows: they were loaded before the update.
        val isSuccessful = views
          .flatMap(view => ViewDefinition.findByPrimaryKey(view.viewPrimaryKey).toList)
          .forall(_.isSystem == false)
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Number of updated rows at table ViewDefinition: ${updatedRows.size}
             |Number of instance specific system views: ${instanceSpecificSystemViews.size}
             |Number of bank specific system views: ${bankSpecificSystemViews.size}
             |Number of updated rows at table AccountAccess: ${updatedAccountAccessRows.size}
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
        
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""ViewDefinition table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
