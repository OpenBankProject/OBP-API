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

package code.api.util.poc.storedprocedure

import java.sql.{DriverManager, ResultSet, SQLException}

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

object CallingStoredProcedureReturningResultSet extends MdcLoggable {
  lazy val conn = DriverManager.getConnection(dbUrl, mySqlUser, mySqlPassword)
  /**
    * MySQL stored procedure:
    * CREATE DEFINER=`root`@`localhost` PROCEDURE `authuser`(IN rate INT)
    * BEGIN
    *     SELECT * FROM authuser;
    * END
    */
    
  val dbUrl: String = APIUtil.getPropsValue("db.url", "jdbc:mysql://localhost:3306/testdb8?useSSL=false")
  val mySqlUser: String = "YOUR_MSSQL_USER"
  val mySqlPassword: String = "YOUR_MYSQL_PASSWORD"

  def main(args: Array[String]): Unit = {
    try {
      
      val statement = conn.prepareCall("{call authuser(?)}")
      statement.setInt(1, 5)
      var hadResults: Boolean = statement.execute()
      // print headings
      println("| First name | Last name | Email |")
      println("==================================")
      while (hadResults) {
        val resultSet: ResultSet = statement.getResultSet
        // process result set
        while (resultSet.next()) {
          val firstName: String = resultSet.getString("firstname")
          val lastName: String = resultSet.getString("lastname")
          val email: String = resultSet.getString("email")
          println(s"| $firstName | $lastName | $email |")
        }
        hadResults = statement.getMoreResults
      }
      statement.close()
    } catch {
      case ex: SQLException => ex.printStackTrace()
    }
  }

}
