package code.api.util.poc.storedprocedure

import java.sql.{DriverManager, SQLException}

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

object PostgreSQLFunctionOutputingSimpleValue  extends MdcLoggable {
  lazy val conn = DriverManager.getConnection(dbUrl, user, password)
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
  val user: String = "obp"
  val password: String = "f"
  
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
