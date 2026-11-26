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
import code.consent.{ConsentItem, MappedConsent}
import code.setup.ServerSetup
import net.liftweb.db.{DB, SuperConnection}
import net.liftweb.mapper.{By, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier
import org.scalatest.Tag

import scala.collection.mutable.ListBuffer
import scala.util.Try

/**
 * Upgrading a database written by a build from before consents had their own reference id.
 *
 * Such a build had no consent_reference_id column on mappedconsent: a consent's reference was its
 * row id, and consent_item rows pointed at that id. The current model declares the column with a
 * unique index, so on the first boot of a current build Schemifier adds the column, which is NULL
 * in every existing row, and then creates the unique index over it. SQL Server allows only one
 * NULL in a unique index, so with two or more existing consents that boot used to stop there.
 *
 * Each scenario rebuilds that old shape in the test database and then runs what Boot runs before
 * and during schemifyAll(), followed by the migration that finishes the change.
 */
class ConsentReferenceIdUpgradeTest extends ServerSetup {

  object ConsentReferenceIdUpgrade extends Tag("ConsentReferenceIdUpgrade")

  private val driver = APIUtil.getPropsValue("db.driver").openOr("org.h2.Driver")
  private val isSqlServer = driver.contains("sqlserver")
  private val isH2 = driver.contains("org.h2.Driver")

  private val consentTable = "mappedconsent"
  private val referenceColumn = "consent_reference_id"
  private val referenceIndex = "mappedconsent_consent_reference_id"
  private val referenceConstraint = "uq_mappedconsent_consent_reference_id"
  private val consentBackup = "backup_2026_05_mappedconsent"
  private val consentItemBackup = "backup_2026_05_consent_item"

  private def withConnection[T](work: SuperConnection => T): T =
    DB.use(DefaultConnectionIdentifier) { connection =>
      val result = work(connection)
      if (!connection.getAutoCommit) connection.commit()
      result
    }

  private def execute(sql: String): Unit = withConnection { connection =>
    val statement = connection.createStatement()
    try statement.execute(sql) finally statement.close()
  }

  private def dropTableIfExists(table: String): Unit =
    if (DbFunction.tableExistsByName(table)) execute(s"DROP TABLE $table")

  /** The reference id stored for each consent row, keyed by the row id. */
  private def storedReferenceIds(rowIds: Seq[Long]): Map[Long, Option[String]] = withConnection { connection =>
    val statement = connection.prepareStatement(s"SELECT id, $referenceColumn FROM $consentTable WHERE id = ?")
    try {
      rowIds.map { rowId =>
        statement.setLong(1, rowId)
        val rs = statement.executeQuery()
        try {
          rs.next() shouldBe true
          rowId -> Option(rs.getString(2))
        } finally rs.close()
      }.toMap
    } finally statement.close()
  }

  private def columnIsNullable(table: String, column: String): Option[Boolean] = withConnection { connection =>
    val schema = Schemifier.getDefaultSchemaName(connection)
    val rs = connection.getMetaData.getColumns(null, schema, null, null)
    try {
      val found = ListBuffer.empty[Boolean]
      while (rs.next()) {
        if (rs.getString(3).equalsIgnoreCase(table) && rs.getString(4).equalsIgnoreCase(column))
          found += (rs.getInt(11) == java.sql.DatabaseMetaData.columnNullable)
      }
      found.headOption
    } finally rs.close()
  }

  private def rowCount(table: String, where: String): Int = withConnection { connection =>
    val statement = connection.createStatement()
    try {
      val rs = statement.executeQuery(s"SELECT COUNT(*) FROM $table WHERE $where")
      try { rs.next(); rs.getInt(1) } finally rs.close()
    } finally statement.close()
  }

  /**
   * Take the consent table back to the shape an older build left: no reference id column, no
   * index or constraint on it, and no view reading it (v_consent selects the column, so the column
   * cannot be dropped while the view exists; the migration recreates the view).
   */
  private def rebuildPreReferenceIdShape(): Unit = {
    execute("DROP VIEW IF EXISTS v_consent")
    if (isSqlServer) {
      execute(
        s"""IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = '$referenceConstraint' AND parent_object_id = OBJECT_ID('$consentTable'))
           |  ALTER TABLE $consentTable DROP CONSTRAINT $referenceConstraint""".stripMargin)
      execute(
        s"""IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('$consentTable') AND name = '$referenceIndex')
           |  DROP INDEX $referenceIndex ON $consentTable""".stripMargin)
      execute(
        s"""IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('$consentTable') AND name = '$referenceColumn')
           |  ALTER TABLE $consentTable DROP COLUMN $referenceColumn""".stripMargin)
    } else {
      execute(s"ALTER TABLE $consentTable DROP CONSTRAINT IF EXISTS $referenceConstraint")
      execute(s"DROP INDEX IF EXISTS $referenceIndex")
      execute(s"ALTER TABLE $consentTable DROP COLUMN IF EXISTS $referenceColumn")
    }
  }

  feature("A database from before consents had their own reference id can be upgraded") {

    scenario("existing consents get distinct reference ids and the unique index is created", ConsentReferenceIdUpgrade) {
      val run = APIUtil.generateUUID().take(8)
      dropTableIfExists(consentBackup)
      dropTableIfExists(consentItemBackup)
      val consentViewExisted = DbFunction.tableExistsByName("v_consent")

      Given("three consents, each with a consent item that points at the consent's row id")
      val consents = (1 to 3).map { n =>
        MappedConsent.create.mUserId(s"upgrade-$run").mStatus("VALID").mApiStandard(s"test-$n").saveMe()
      }
      val rowIds = consents.map(_.id.get)
      val items = rowIds.map { rowId =>
        ConsentItem.create.consentReferenceId(rowId.toString).itemType("ROLE").bankId(s"bank-$run").saveMe()
      }

      try {
        And("the consent table has the shape an older build left: no reference id column")
        rebuildPreReferenceIdShape()
        columnIsNullable(consentTable, referenceColumn) shouldBe None

        When("Boot's pre-Schemifier steps run, followed by Schemifier")
        Migration.database.prepareSchemaBeforeSchemifier()
        Schemifier.schemify(true, Schemifier.infoF _, MappedConsent)

        Then("every existing consent has a reference id, and no two share one")
        val referenceIds = storedReferenceIds(rowIds)
        referenceIds.values.foreach(_ should not be empty)
        referenceIds.values.flatten.toSet.size shouldBe rowIds.size

        And("the unique index on the reference id exists")
        DbFunction.indexExistsByName(consentTable, referenceIndex) shouldBe true

        And("the consents as they were before the upgrade are kept in the backup table")
        DbFunction.tableExistsByName(consentBackup) shouldBe true
        rowCount(consentBackup, s"id IN (${rowIds.mkString(",")})") shouldBe rowIds.size

        if (!isH2) {
          When("the migration that finishes the change runs")
          MigrationOfConsentReferenceIdUuid.migrate(s"consentReferenceIdUpgradeTest_$run") shouldBe true

          Then("the reference ids written before Schemifier are kept")
          storedReferenceIds(rowIds) shouldBe referenceIds

          And("the column no longer accepts NULL")
          columnIsNullable(consentTable, referenceColumn) shouldBe Some(false)

          And("each consent item now points at its consent's reference id instead of its row id")
          items.zip(rowIds).foreach { case (item, rowId) =>
            ConsentItem.find(By(ConsentItem.id, item.id.get)).map(_.consentReferenceId.get).toOption shouldBe
              referenceIds(rowId)
          }
        }
      } finally {
        // Plain SQL, because the consent table may still lack the column the mapper selects, and each
        // step on its own, so that a failure here cannot hide the one the scenario reported.
        Try(execute(s"DELETE FROM consent_item WHERE id IN (${items.map(_.id.get).mkString(",")})"))
        Try(execute(s"DELETE FROM $consentTable WHERE id IN (${rowIds.mkString(",")})"))
        // With this scenario's consents gone the table is empty, so Schemifier can put back whatever
        // a failed run left out.
        Try(Schemifier.schemify(true, Schemifier.infoF _, MappedConsent))
        Try(dropTableIfExists(consentBackup))
        Try(dropTableIfExists(consentItemBackup))
        // Leave the view as the suite found it for the suites that run after this one in the same JVM.
        if (consentViewExisted && !DbFunction.tableExistsByName("v_consent"))
          Try(MigrationOfConsentView.addConsentView(s"consentReferenceIdUpgradeTest_view_$run"))
      }
    }
  }
}
