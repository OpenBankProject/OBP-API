package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

/**
 * Atomic, guarded status transitions for `mappedconsent`, used by the HTTP-facing
 * consent state machine (checkAnswer / revoke / skip-SCA accept).
 *
 * Each method is a single conditional UPDATE keyed by the row id with a status guard, so the
 * check and the write cannot interleave across concurrent requests. The returned affected-row
 * count tells the caller whether it won the transition (1) or lost it to a concurrent request (0).
 *
 * Row-id keyed (not consent-id) because every call site already holds the loaded MappedConsent.
 */
object DoobieConsentStatusQueries {

  /** Transition mstatus from an expected guard value to a new value. Returns affected rows (0 or 1). */
  def conditionalStatusTransition(consentRowId: Long, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $newStatus,
                mlastactiondate = NOW()
            WHERE id = $consentRowId
              AND mstatus = $guardStatus""".update.run
    )

  /** Revoke unless already at the given terminal status. Returns affected rows (0 or 1). */
  def conditionalRevoke(consentRowId: Long, revokedStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $revokedStatus,
                mlastactiondate = NOW()
            WHERE id = $consentRowId
              AND mstatus <> $revokedStatus""".update.run
    )
}
