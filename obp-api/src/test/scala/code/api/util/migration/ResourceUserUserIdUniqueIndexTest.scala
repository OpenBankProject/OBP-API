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
import code.api.util.migration.Migration.DbFunction
import code.model.dataAccess.ResourceUser
import code.setup.ServerSetup
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier
import org.scalatest.Tag

import scala.util.Try

/**
 * Upgrading a database written by a build from before resourceuser.userid_ had a unique index.
 *
 * The migration addUniqueIndexOnResourceUserUserId creates that index. A database can hold
 * resource users with no user id, or two resource users with the same user id, and the index
 * cannot be created over them: every database refuses the repeated value, and SQL Server also
 * refuses a second NULL. The migration must give those users their own user ids first, so that
 * the boot that runs it can finish.
 */
class ResourceUserUserIdUniqueIndexTest extends ServerSetup {

  object ResourceUserUserIdUniqueIndex extends Tag("ResourceUserUserIdUniqueIndex")

  private val driver = APIUtil.getPropsValue("db.driver").openOr("org.h2.Driver")
  private val isSqlServer = driver.contains("sqlserver")

  private val table = ResourceUser._dbTableNameLC
  private val idColumn = ResourceUser.id.dbColumnName
  private val userIdColumn = ResourceUser.userId_.dbColumnName
  private val index = "resourceuser_userid_unique"

  private def execute(sql: String): Unit = DB.use(DefaultConnectionIdentifier) { connection =>
    val statement = connection.createStatement()
    try statement.execute(sql) finally statement.close()
    if (!connection.getAutoCommit) connection.commit()
  }

  private def dropIndexIfExists(): Unit =
    if (isSqlServer)
      execute(
        s"""IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('$table') AND name = '$index')
           |  DROP INDEX $index ON $table""".stripMargin)
    else
      execute(s"DROP INDEX IF EXISTS $index")

  private def setUserId(rowId: Long, userId: Option[String]): Unit =
    execute(s"UPDATE $table SET $userIdColumn = ${userId.map(u => s"'$u'").getOrElse("NULL")} WHERE $idColumn = $rowId")

  /** The user id stored for each row, keyed by row id. Rows that no longer exist are left out. */
  private def storedUserIds(rowIds: Seq[Long]): Map[Long, Option[String]] = DB.use(DefaultConnectionIdentifier) { connection =>
    val statement = connection.createStatement()
    try {
      val rs = statement.executeQuery(s"SELECT $idColumn, $userIdColumn FROM $table WHERE $idColumn IN (${rowIds.mkString(",")})")
      try {
        Iterator.continually(rs).takeWhile(_.next()).map(r => r.getLong(1) -> Option(r.getString(2))).toMap
      } finally rs.close()
    } finally statement.close()
  }

  // H2 keeps unquoted table names in upper case, and the metadata lookup takes the name as given.
  private def indexExists: Boolean =
    List(table, table.toUpperCase).exists(DbFunction.indexExistsByName(_, index))

  feature("The unique index on resourceuser.userid_ can be added to a database that breaks it") {

    scenario("users without a user id and users sharing one each get their own, and the index is created",
      ResourceUserUserIdUniqueIndex) {
      val run = APIUtil.generateUUID().take(8)
      def user(n: Int): Long =
        ResourceUser.create.provider_(s"upgrade-$run").providerId(s"user-$n").name_(s"user-$n").saveMe().id.get

      var rowIds: Seq[Long] = Nil
      try {
        Given("the resource user table has no unique index on userid_")
        dropIndexIfExists()
        indexExists shouldBe false

        And("two users share one user id and two users have none")
        rowIds = (1 to 5).map(user)
        val Seq(first, sameAsFirst, noUserIdA, noUserIdB, other) = rowIds
        val shared = storedUserIds(Seq(first))(first).get
        setUserId(sameAsFirst, Some(shared))
        setUserId(noUserIdA, None)
        setUserId(noUserIdB, None)
        val otherUserId = storedUserIds(Seq(other))(other)

        When("the migration that adds the unique index runs")
        MigrationOfUserIdIndexes.addUniqueIndexOnResourceUserUserId(s"resourceUserUserIdUniqueIndexTest_$run") shouldBe true

        Then("no user was removed")
        val after = storedUserIds(rowIds)
        after.keySet shouldBe rowIds.toSet

        And("the user with the lowest row id keeps the shared user id, and the other users keep theirs")
        after(first) shouldBe Some(shared)
        after(other) shouldBe otherUserId

        And("every user has a user id, and no two share one")
        after.values.foreach(_ should not be empty)
        after.values.flatten.toSet.size shouldBe rowIds.size

        And("the unique index exists")
        indexExists shouldBe true
      } finally {
        // Each step on its own, so that a failure here cannot hide the one the scenario reported.
        if (rowIds.nonEmpty) Try(execute(s"DELETE FROM $table WHERE $idColumn IN (${rowIds.mkString(",")})"))
      }
    }
  }
}
