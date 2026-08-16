package code.atms

import code.api.util.{DoobieUtil, OBPLimit}
import code.setup.ServerSetup
import com.openbankproject.commons.model.{AtmId, BankId}
import doobie._
import doobie.implicits._

/**
 * The atm table must be empty at the start of every test.
 *
 * Right now that happens for free: MappedAtm is still in Boot.ToSchemify.models, and every reset
 * path loops that list calling bulkDelete_!!. The moment the entity leaves ToSchemify - the next
 * step of this table's migration - the loop stops clearing atm rows and nothing else does, unless
 * an explicit Doobie DELETE is added to all four reset paths (ServerSetup,
 * TestConnectorSetupWithStandardPermissions, LocalMappedConnectorTestSetup, and
 * SandboxDataLoadingTest's own beforeEach).
 *
 * A leak there does not fail here first. It fails somewhere far away, as a count that is one too
 * high in a suite that never mentions ATMs, hours of bisecting later. This test exists to make the
 * failure land on the change that caused it.
 *
 * It is deliberately written against the raw table rather than the provider: the point is whether
 * the ROWS are gone, not whether a provider method filters them.
 */
class AtmTableResetIsolationTest extends ServerSetup {

  private def atmRowCount(): Long =
    DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM mappedatm".query[Long].unique)

  private def insertAtm(bankId: String, atmId: String): Unit = {
    val atm = Atms.Atm(
      atmId = AtmId(atmId),
      bankId = BankId(bankId),
      name = s"reset-probe-$atmId",
      address = com.openbankproject.commons.model.Address(
        line1 = "l1", line2 = "l2", line3 = "l3", city = "c", county = Some(""),
        state = "s", postCode = "p", countryCode = "de"),
      location = com.openbankproject.commons.model.Location(1.0, 2.0, None, None),
      meta = com.openbankproject.commons.model.Meta(
        com.openbankproject.commons.model.License("l", "L")),
      OpeningTimeOnMonday = None, ClosingTimeOnMonday = None,
      OpeningTimeOnTuesday = None, ClosingTimeOnTuesday = None,
      OpeningTimeOnWednesday = None, ClosingTimeOnWednesday = None,
      OpeningTimeOnThursday = None, ClosingTimeOnThursday = None,
      OpeningTimeOnFriday = None, ClosingTimeOnFriday = None,
      OpeningTimeOnSaturday = None, ClosingTimeOnSaturday = None,
      OpeningTimeOnSunday = None, ClosingTimeOnSunday = None,
      isAccessible = None, locatedAt = None, moreInfo = None, hasDepositCapability = None
    )
    Atms.atmsProvider.vend.createOrUpdateAtm(atm)
  }

  Feature("the atm table is cleared by the per-test-class database reset") {

    Scenario("rows written by one test class do not survive the reset") {
      Given("the table is empty at the start of this class")
      // resetDatabaseForTestClass runs in beforeAll, so this is the state every class inherits.
      atmRowCount() should equal(0L)

      When("a test writes atm rows through the provider")
      insertAtm("reset-probe-bank", "reset-probe-atm-1")
      insertAtm("reset-probe-bank", "reset-probe-atm-2")

      Then("they are really persisted")
      atmRowCount() should equal(2L)

      And("they are visible through the provider, i.e. committed rather than pending")
      Atms.atmsProvider.vend
        .getAtms(BankId("reset-probe-bank"), List(OBPLimit(1000)))
        .map(_.size) should equal(Some(2))

      When("the same reset the next test class will run is applied")
      resetDatabaseForTestClass()

      Then("no atm rows are left for that class to trip over")
      // Fails the moment MappedAtm leaves ToSchemify without an explicit Doobie DELETE being
      // added to the reset paths - which is the next step of this table's migration.
      atmRowCount() should equal(0L)
    }
  }
}
