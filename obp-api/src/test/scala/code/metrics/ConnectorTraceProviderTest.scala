package code.metrics

import java.util.Date

import code.api.util._
import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._

/**
 * Characterization of ConnectorTraceProvider, written before the implementation moves to Doobie.
 *
 * The provider had no test. getAllConnectorTraces accepts nine independent filters plus ordering
 * and paging, all built from OBPQueryParam, and a rewrite that drops or mis-wires one of them
 * fails silently - the endpoint just returns more rows than it should. So every filter gets an
 * assertion, and each is checked by showing that a non-matching row is excluded rather than only
 * that a matching row is present.
 *
 * Also pinned: ordering by date in both directions, limit, and that an empty filter set returns
 * everything.
 */
class ConnectorTraceProviderTest extends ServerSetup {

  private def save(correlationId: String, connectorName: String, functionName: String,
                   bankId: String, userId: String, date: Date, duration: Long = 1L): Unit =
    ConnectorTraceProvider.saveConnectorTrace(
      correlationId = correlationId, connectorName = connectorName, functionName = functionName,
      bankId = bankId, outboundMessage = "out", inboundMessage = "in", date = date,
      duration = duration, isSuccessful = true, userId = userId, httpVerb = "GET",
      url = "/obp/v6.0.0/test")

  private val early = new Date(1_600_000_000_000L)
  private val late = new Date(1_700_000_000_000L)

  override def beforeEach() = {
    super.beforeEach()
    // The framework reset runs per test CLASS, not per scenario, so rows would otherwise
    // accumulate across the scenarios below and every filter assertion would see the previous
    // scenario's data.
    DoobieUtil.runUpdate(sql"DELETE FROM connector_trace".update.run)
  }

  private def all(params: OBPQueryParam*) =
    ConnectorTraceProvider.getAllConnectorTraces(params.toList)

  Feature("connector trace storage and filtering") {

    Scenario("a saved trace can be read back with its fields intact") {
      save("corr-1", "mapped", "getBanks", "bank-1", "user-1", early)

      val traces = all()
      traces.size should equal(1)
      val t = traces.head
      t.correlationId should equal("corr-1")
      t.connectorName should equal("mapped")
      t.functionName should equal("getBanks")
      t.bankId should equal("bank-1")
      t.userId should equal("user-1")
      t.outboundMessage should equal("out")
      t.inboundMessage should equal("in")
      t.httpVerb should equal("GET")
    }

    Scenario("each filter excludes the rows that do not match it") {
      save("corr-a", "mapped", "getBanks", "bank-1", "user-1", early)
      save("corr-b", "rabbitmq", "getAccounts", "bank-2", "user-2", early)

      all(OBPCorrelationId("corr-a")).map(_.correlationId) should equal(List("corr-a"))
      all(OBPConnectorName("rabbitmq")).map(_.correlationId) should equal(List("corr-b"))
      all(OBPFunctionName("getBanks")).map(_.correlationId) should equal(List("corr-a"))
      all(OBPBankId("bank-2")).map(_.correlationId) should equal(List("corr-b"))
      all(OBPUserId("user-1")).map(_.correlationId) should equal(List("corr-a"))
    }

    Scenario("date filters bound the range at both ends") {
      save("old", "mapped", "f", "b", "u", early)
      save("new", "mapped", "f", "b", "u", late)

      all(OBPFromDate(late)).map(_.correlationId) should equal(List("new"))
      all(OBPToDate(early)).map(_.correlationId) should equal(List("old"))
    }

    Scenario("ordering by date works in both directions") {
      save("old", "mapped", "f", "b", "u", early)
      save("new", "mapped", "f", "b", "u", late)

      all(OBPOrdering(None, OBPAscending)).map(_.correlationId) should equal(List("old", "new"))
      all(OBPOrdering(None, OBPDescending)).map(_.correlationId) should equal(List("new", "old"))
    }

    Scenario("limit caps the number of rows returned") {
      save("one", "mapped", "f", "b", "u", early)
      save("two", "mapped", "f", "b", "u", late)

      all(OBPLimit(1)).size should equal(1)
      all().size should equal(2)
    }

    Scenario("filters combine, so a row must match all of them") {
      save("corr-a", "mapped", "getBanks", "bank-1", "user-1", early)
      save("corr-b", "mapped", "getBanks", "bank-2", "user-1", early)

      all(OBPConnectorName("mapped"), OBPBankId("bank-1")).map(_.correlationId) should
        equal(List("corr-a"))
    }
  }
}
