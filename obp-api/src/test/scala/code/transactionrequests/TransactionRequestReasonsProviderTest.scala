package code.transactionrequests

import code.api.util.{APIUtil, DoobieUtil}
import code.setup.ServerSetup
import doobie.implicits._

/**
 * Characterization of transaction-request-reasons storage.
 *
 * Nothing in the codebase reads this table back - LocalMappedConnector.saveTransactionRequestReasons
 * is a pure write, called alongside a transaction request's creation and never queried again. So
 * there is no production read path whose test would catch a column-mapping mistake; this reads
 * the row back directly to confirm the write actually lands with the right values.
 */
class TransactionRequestReasonsProviderTest extends ServerSetup {

  Feature("transaction request reasons storage") {

    Scenario("create writes every column correctly") {
      val trId = APIUtil.generateUUID()
      DoobieTransactionRequestReasonsQueries.create(
        transactionRequestId = trId,
        code = "MS03",
        documentNumber = "DOC-1",
        amount = "12.34",
        currency = "EUR",
        description = "a reason"
      )

      val row = DoobieUtil.runQuery(
        sql"""SELECT code, documentnumber, amount, currency, description
              FROM transactionrequestreasons WHERE transactionrequestid = $trId"""
          .query[(String, String, String, String, String)].unique)

      row should equal(("MS03", "DOC-1", "12.34", "EUR", "a reason"))
    }

    Scenario("multiple reasons for the same transaction request are not deduplicated") {
      val trId = APIUtil.generateUUID()
      DoobieTransactionRequestReasonsQueries.create(trId, "MS03", "DOC-1", "1.00", "EUR", "first")
      DoobieTransactionRequestReasonsQueries.create(trId, "MS03", "DOC-1", "1.00", "EUR", "second")

      val count = DoobieUtil.runQuery(
        sql"SELECT COUNT(*) FROM transactionrequestreasons WHERE transactionrequestid = $trId"
          .query[Int].unique)
      count should equal(2)
    }
  }
}
