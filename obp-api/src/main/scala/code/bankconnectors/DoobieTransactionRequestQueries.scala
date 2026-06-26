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
   *
   * Returns the locked row's status as Some, or None when the request id does not exist.
   * `.option` (not `.unique`) is deliberate: a missing row must not raise an exception, so
   * the caller can let the downstream lookup return the correct 404 instead of a misleading
   * "lock failed" 400.
   */
  def atomicallyLockTransactionRequest(transReqId: String): ConnectionIO[Option[String]] = {
    sql"SELECT mstatus FROM mappedtransactionrequest WHERE mtransactionrequestid = $transReqId FOR UPDATE".query[String].option
  }

  /**
   * Box semantics: Full(Some(status)) = row locked; Full(None) = request id does not exist
   * (query ran cleanly); Failure = a genuine DB/lock error. Callers check `.isDefined` on the
   * Box, so only a real lock failure short-circuits with 400 — a missing row falls through.
   */
  def lockTransactionRequest(transReqId: String): Box[Option[String]] = {
    tryo {
      DoobieUtil.runUpdate(atomicallyLockTransactionRequest(transReqId))
    }
  }
}
