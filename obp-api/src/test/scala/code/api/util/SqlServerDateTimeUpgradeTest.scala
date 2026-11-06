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

package code.api.util

import code.setup.ServerSetup
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

import scala.collection.mutable.ListBuffer

/**
 * Lift Mapper creates DATETIME columns on SQL Server, which cannot hold a date before 1753. At boot every
 * such column is upgraded to DATETIME2 (SqlServerDateTimeUpgrade). A column that an index, a default or a
 * NOT NULL depends on must go through that upgrade with the dependents intact: SQL Server refuses to
 * change the type of a column under an index (error 5074), and the upgrade must not turn a NOT NULL
 * column into a nullable one.
 *
 * These scenarios talk to SQL Server catalog views, so they run only when the test database is SQL Server.
 */
class SqlServerDateTimeUpgradeTest extends ServerSetup {

  private val probe = "obp_datetime_probe"

  private def sqlServerOnly(): Unit =
    if (!DBUtil.isSqlServer) cancel("This scenario inspects SQL Server catalog views and runs only on SQL Server")

  private def execute(sql: String): Unit =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val statement = connection.createStatement()
      try {
        statement.execute(sql)
        connection.commit()
      } finally statement.close()
    }

  private def rows[A](sql: String)(read: java.sql.ResultSet => A): List[A] =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val statement = connection.createStatement()
      try {
        val result = statement.executeQuery(sql)
        try {
          val buffer = ListBuffer[A]()
          while (result.next()) buffer += read(result)
          buffer.toList
        } finally result.close()
      } finally statement.close()
    }

  /** The type name and nullability of a column. */
  private def column(table: String, name: String): Option[(String, Boolean)] =
    rows(
      s"""SELECT ty.name, c.is_nullable
         |FROM sys.columns c JOIN sys.types ty ON c.user_type_id = ty.user_type_id
         |WHERE c.object_id = OBJECT_ID('$table') AND c.name = '$name'""".stripMargin
    )(r => (r.getString(1), r.getBoolean(2))).headOption

  /** An index as (unique, key columns with direction, included columns). */
  private def index(table: String, name: String): Option[(Boolean, List[String], List[String])] = {
    val parts = rows(
      s"""SELECT i.is_unique, ic.is_included_column, ic.is_descending_key, c.name
         |FROM sys.indexes i
         |JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
         |JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
         |WHERE i.object_id = OBJECT_ID('$table') AND i.name = '$name'
         |ORDER BY ic.is_included_column, ic.key_ordinal, c.column_id""".stripMargin
    )(r => (r.getBoolean(1), r.getBoolean(2), r.getBoolean(3), r.getString(4)))
    if (parts.isEmpty) None
    else Some((
      parts.head._1,
      parts.filterNot(_._2).map { case (_, _, descending, c) => if (descending) s"$c DESC" else c },
      parts.filter(_._2).map(_._4)
    ))
  }

  private def dropProbe(): Unit = execute(s"IF OBJECT_ID('$probe') IS NOT NULL DROP TABLE $probe")

  override def beforeAll(): Unit = {
    super.beforeAll()
    if (DBUtil.isSqlServer) dropProbe()
  }

  override def afterAll(): Unit = {
    if (DBUtil.isSqlServer) dropProbe()
    super.afterAll()
  }

  feature("Upgrading DATETIME columns to DATETIME2 on SQL Server") {

    scenario("A column under an index, with a default, or declared NOT NULL keeps all of that") {
      sqlServerOnly()
      Given("a table whose DATETIME columns are indexed, defaulted and NOT NULL")
      execute(
        s"""CREATE TABLE $probe (
           |  id INT IDENTITY(1,1) PRIMARY KEY,
           |  d DATETIME NOT NULL,
           |  e DATETIME NULL CONSTRAINT df_${probe}_e DEFAULT (GETDATE()),
           |  f DATETIME NULL)""".stripMargin)
      execute(s"CREATE UNIQUE INDEX ix_${probe}_d_e ON $probe (d, e)")
      execute(s"CREATE INDEX ix_${probe}_f ON $probe (f DESC) INCLUDE (d)")

      When("the upgrade runs")
      val result = SqlServerDateTimeUpgrade.upgradeAll()

      Then("none of the probe columns is reported as failed")
      result.failed.filter(_.contains(probe)) shouldBe Nil

      And("every column is DATETIME2, and NOT NULL is still NOT NULL")
      column(probe, "d") shouldBe Some(("datetime2", false))
      column(probe, "e") shouldBe Some(("datetime2", true))
      column(probe, "f") shouldBe Some(("datetime2", true))

      And("both indexes are back as they were")
      index(probe, s"ix_${probe}_d_e") shouldBe Some((true, List("d", "e"), Nil))
      index(probe, s"ix_${probe}_f") shouldBe Some((false, List("f DESC"), List("d")))

      And("the default is still there")
      rows(s"SELECT name FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID('$probe')")(_.getString(1)) shouldBe List(s"df_${probe}_e")

      And("a date before 1753 can be stored")
      execute(s"INSERT INTO $probe (d, e, f) VALUES ('1100-01-01', '1100-01-02', '1100-01-03')")
      rows(s"SELECT COUNT(*) FROM $probe WHERE d < '1753-01-01'")(_.getInt(1)) shouldBe List(1)

      When("the upgrade runs again")
      val second = SqlServerDateTimeUpgrade.upgradeAll()

      Then("there is nothing left to do for the probe")
      (second.altered ++ second.failed).filter(_.contains(probe)) shouldBe Nil
    }

    scenario("No DATETIME column is left in the tables of the database after boot") {
      sqlServerOnly()
      val remaining = rows(
        """SELECT t.name + '.' + c.name
          |FROM sys.columns c
          |JOIN sys.tables t ON t.object_id = c.object_id
          |JOIN sys.types ty ON c.user_type_id = ty.user_type_id
          |WHERE ty.name = 'datetime'
          |ORDER BY 1""".stripMargin
      )(_.getString(1))
      withClue("columns still DATETIME after the boot upgrade: ") {
        remaining shouldBe Nil
      }
    }
  }
}
