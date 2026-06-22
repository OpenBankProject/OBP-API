package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

object DoobieTransactionRequestQueries {

  /**
   * Atomically locks the transaction request row using SELECT FOR UPDATE.
   * This ensures that concurrent MFA challenge answers cannot be processed simultaneously
   * for the same transaction request.
   */
  def atomicallyLockTransactionRequest(transReqId: String): ConnectionIO[String] = {
    sql"SELECT mstatus FROM mappedtransactionrequest WHERE mtransactionrequestid = $transReqId FOR UPDATE".query[String].unique
  }

  def lockTransactionRequest(transReqId: String): Box[String] = {
    tryo {
      DoobieUtil.runUpdate(atomicallyLockTransactionRequest(transReqId))
    }
  }
}
