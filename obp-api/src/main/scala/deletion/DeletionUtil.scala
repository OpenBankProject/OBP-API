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

package deletion

import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

import java.sql.Connection
import scala.util.control.NonFatal

object DeletionUtil {
  /**
   * Runs a cascade as one unit: when it throws, every row it wrote is restored before the exception goes on.
   *
   * Within a request the connection is the request's own (RequestScopeConnection), which ignores rollback and is
   * committed when the request ends, whatever the response. So the cascade rolls back to a savepoint taken here
   * instead. Outside a request this is a savepoint within the transaction DB.use already runs.
   */
  def databaseAtomicTask[R](blockOfCode: => R): R = {
    DB.use(DefaultConnectionIdentifier){ conn =>
      withSavepoint(conn.connection)(blockOfCode)
    }
  }

  /** Runs `blockOfCode` after a savepoint on `connection`, rolling back to it when the block throws. */
  private[deletion] def withSavepoint[R](connection: Connection)(blockOfCode: => R): R = {
    val savepoint = connection.setSavepoint()
    val result =
      try blockOfCode
      catch {
        case e: Throwable =>
          try connection.rollback(savepoint)
          catch { case rollbackFailure: Throwable => e.addSuppressed(rollbackFailure) }
          throw e
      }
    // Oracle's and SQL Server's drivers do not release savepoints; the savepoint then ends with the transaction.
    try connection.releaseSavepoint(savepoint)
    catch { case NonFatal(_) => }
    result
  }
}
