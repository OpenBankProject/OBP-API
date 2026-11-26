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

import code.accountholders.MapperAccountHolders
import code.api.util.APIUtil
import code.api.util.migration.Migration.DbFunction
import code.entitlement.MappedEntitlement
import code.setup.{DefaultUsers, ServerSetup}
import net.liftweb.db.DB
import net.liftweb.mapper.{BaseMetaMapper, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier
import org.scalatest.Tag

import scala.util.Try

/**
 * Upgrading a database written by a build from before two tables had a unique index on their
 * natural key.
 *
 * Such a build let the same account holder link (user, bank, account) and the same entitlement
 * (bank, user, role) be stored more than once. The current models declare a unique index on those
 * columns, so on the first boot of a current build Schemifier runs CREATE UNIQUE INDEX over the
 * existing rows. Boot removes the duplicate rows before that, keeping the lowest id of each key.
 *
 * Each scenario rebuilds the old shape: it drops the unique index and stores duplicate rows. It
 * then runs what Boot runs before and during schemifyAll().
 */
class DeduplicateBeforeUniqueIndexTest extends ServerSetup with DefaultUsers {

  object DeduplicateBeforeUniqueIndex extends Tag("DeduplicateBeforeUniqueIndex")

  private val driver = APIUtil.getPropsValue("db.driver").openOr("org.h2.Driver")
  private val isSqlServer = driver.contains("sqlserver")

  private def execute(sql: String): Unit = DB.use(DefaultConnectionIdentifier) { connection =>
    val statement = connection.createStatement()
    try statement.execute(sql) finally statement.close()
    if (!connection.getAutoCommit) connection.commit()
  }

  private def dropIndexIfExists(table: String, index: String): Unit =
    if (isSqlServer)
      execute(
        s"""IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('$table') AND name = '$index')
           |  DROP INDEX $index ON $table""".stripMargin)
    else
      execute(s"DROP INDEX IF EXISTS $index")

  /** The ids still stored among `ids`. */
  private def remainingIds(table: String, ids: Seq[Long]): Set[Long] = DB.use(DefaultConnectionIdentifier) { connection =>
    val statement = connection.createStatement()
    try {
      val rs = statement.executeQuery(s"SELECT id FROM $table WHERE id IN (${ids.mkString(",")})")
      try {
        Iterator.continually(rs).takeWhile(_.next()).map(_.getLong(1)).toSet
      } finally rs.close()
    } finally statement.close()
  }

  // H2 keeps unquoted table names in upper case, and the metadata lookup takes the name as given.
  // Postgres cuts a name longer than 63 characters down to 63 when it stores it, and the account
  // holder index name is longer than that, so its stored name is looked for too.
  private def indexExists(table: String, index: String): Boolean =
    List(table, table.toUpperCase).exists { t =>
      DbFunction.indexExistsByName(t, index) || DbFunction.indexExistsByName(t, index.take(63))
    }

  /**
   * Rebuild the old shape of `table`, run Boot's pre-Schemifier steps and Schemifier, and check
   * that one row per key is left, the one with the lowest id, and that the unique index exists.
   */
  private def upgradeKeepsLowestIdPerKey(
    table: String,
    index: String,
    model: BaseMetaMapper,
    storeRows: () => (Seq[Long], Seq[Long])
  ): Unit = {
    var stored: Seq[Long] = Nil
    try {
      Given(s"the $table table has no unique index on its natural key")
      dropIndexIfExists(table, index)
      indexExists(table, index) shouldBe false

      And("it holds two duplicates of one key and one row with another key")
      val (duplicates, other) = storeRows()
      stored = duplicates ++ other

      When("Boot's pre-Schemifier steps run, followed by Schemifier")
      Migration.database.prepareSchemaBeforeSchemifier()
      Schemifier.schemify(true, Schemifier.infoF _, model)

      Then("only the duplicate with the higher id is removed")
      remainingIds(table, stored) shouldBe (duplicates.min +: other).toSet

      And("the unique index exists")
      indexExists(table, index) shouldBe true
    } finally {
      // Plain SQL, each step on its own, so that a failure here cannot hide the one the scenario
      // reported. With the duplicates gone Schemifier can put back an index a failed run left out.
      if (stored.nonEmpty) Try(execute(s"DELETE FROM $table WHERE id IN (${stored.mkString(",")})"))
      Try(Schemifier.schemify(true, Schemifier.infoF _, model))
    }
  }

  feature("Duplicate rows are removed before Schemifier creates a unique index on their key") {

    scenario("account holders: one row is left per user, bank and account", DeduplicateBeforeUniqueIndex) {
      val run = APIUtil.generateUUID().take(8)
      val userPrimaryKey = resourceUser1.userPrimaryKey.value
      def holder(account: String): Long =
        MapperAccountHolders.create
          .user(userPrimaryKey)
          .accountBankPermalink(s"bank-$run")
          .accountPermalink(account)
          .source(s"upgrade-$run")
          .saveMe().id.get

      upgradeKeepsLowestIdPerKey(
        table = "mapperaccountholders",
        index = "mapperaccountholders_user_c_accountbankpermalink_accountpermalink",
        model = MapperAccountHolders,
        storeRows = () => (Seq(holder(s"account-$run"), holder(s"account-$run")), Seq(holder(s"other-$run")))
      )
    }

    scenario("entitlements: one row is left per bank, user and role", DeduplicateBeforeUniqueIndex) {
      val run = APIUtil.generateUUID().take(8)
      def entitlement(role: String): Long =
        MappedEntitlement.create
          .mBankId(s"bank-$run")
          .mUserId(s"user-$run")
          .mRoleName(role)
          .saveMe().id.get

      upgradeKeepsLowestIdPerKey(
        table = "mappedentitlement",
        index = "mappedentitlement_mbankid_muserid_mrolename",
        model = MappedEntitlement,
        storeRows = () => (Seq(entitlement(s"Role$run"), entitlement(s"Role$run")), Seq(entitlement(s"Other$run")))
      )
    }
  }
}
