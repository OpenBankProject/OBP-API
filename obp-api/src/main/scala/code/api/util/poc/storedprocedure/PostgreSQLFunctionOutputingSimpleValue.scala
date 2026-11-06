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

import java.sql.{DriverManager, SQLException}

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

object PostgreSQLFunctionOutputingSimpleValue  extends MdcLoggable {
  lazy val conn = DriverManager.getConnection(dbUrl, databaseUser, databasePassword)
  /**
    * PostgreSQL function
    */
    val ddl = """-- Function: public.count_rows(character varying, character varying)
                |
                |-- DROP FUNCTION public.count_rows(character varying, character varying);
                |
                |CREATE OR REPLACE FUNCTION public.count_rows(
                |    IN name character varying,
                |    IN email character varying,
                |    OUT count bigint)
                |  RETURNS bigint AS
                |$BODY$
                |    BEGIN
                |        SELECT COUNT(*) INTO count
                |        FROM viewdefinition;
                |    END;
                |$BODY$
                |  LANGUAGE plpgsql VOLATILE
                |  COST 100;
                |ALTER FUNCTION public.count_rows(character varying, character varying)
                |  OWNER TO obp;
                |  """.stripMargin
  val dbUrl: String = APIUtil.getPropsValue("db.url", "jdbc:postgresql://localhost:5432/obp_mapped")
  val databaseUser: String = "YOUR_DATABASE_USER"
  val databasePassword: String = "YOUR_DATABASE_PASSWORD"
  
  def main(args: Array[String]): Unit = {
    try {
      val statement = conn.prepareCall("{call count_rows(?, ?)}")
      statement.setString(1, "Simon Redfern")
      statement.setString(2, "simon@tesobe.com")
      statement.execute()
      statement.close()
      println("PostgreSQL Function called successfully!")
    } catch {
      case ex: SQLException => ex.printStackTrace()
    }
  }

}
