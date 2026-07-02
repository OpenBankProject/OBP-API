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
 * `updatedat` is bumped alongside the status so the write matches what the replaced Lift
 * saveMe() (CreatedUpdated trait) persisted.
 */
object DoobieConsentStatusQueries {

  /** Transition mstatus from an expected guard value to a new value, keyed by row id
   *  (for call sites already holding the loaded MappedConsent). Returns affected rows (0 or 1). */
  def conditionalStatusTransition(consentRowId: Long, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $newStatus,
                mlastactiondate = NOW(),
                updatedat = NOW()
            WHERE id = $consentRowId
              AND mstatus = $guardStatus""".update.run
    )

  /** Transition mstatus from an expected guard value to a new value, keyed by consent id.
   *  Used by the skip-SCA auto-accept in the createConsent endpoints (v3.1.0 / v5.0.0 / v5.1.0),
   *  which hold only the consentId — no extra SELECT needed to obtain the row id.
   *  Returns affected rows (0 or 1). */
  def conditionalStatusTransitionByConsentId(consentId: String, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $newStatus,
                mlastactiondate = NOW(),
                updatedat = NOW()
            WHERE mconsentid = $consentId
              AND mstatus = $guardStatus""".update.run
    )

  /** Revoke unless already at the given terminal status. Returns affected rows (0 or 1). */
  def conditionalRevoke(consentRowId: Long, revokedStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent
            SET mstatus = $revokedStatus,
                mlastactiondate = NOW(),
                updatedat = NOW()
            WHERE id = $consentRowId
              AND mstatus <> $revokedStatus""".update.run
    )
}
