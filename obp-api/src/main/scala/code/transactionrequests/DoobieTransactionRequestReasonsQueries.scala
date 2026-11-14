package code.transactionrequests

import java.sql.Timestamp

import code.api.util.{APIUtil, DoobieUtil}
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * Doobie implementation of the transaction-request-reasons store, replacing the Lift
 * TransactionRequestReasons entity.
 *
 * This is a write-only audit record: nothing in the codebase reads it back. create is the whole
 * contract - there is no unique index (multiple reasons naturally attach to one
 * transactionRequestId) and no update or delete path to preserve.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieTransactionRequestReasonsQueries {

  def create(
    transactionRequestId: String,
    code: String,
    documentNumber: String,
    amount: String,
    currency: String,
    description: String
  ): Unit = {
    val id = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis)
    DoobieUtil.runUpdate(
      sql"""INSERT INTO transactionrequestreasons
              (transactionrequestreasonid, transactionrequestid, code, documentnumber, amount, currency, description, createdat, updatedat)
            VALUES ($id, $transactionRequestId, $code, $documentNumber, $amount, $currency, $description, $now, $now)"""
        .update.run)
    ()
  }
}
