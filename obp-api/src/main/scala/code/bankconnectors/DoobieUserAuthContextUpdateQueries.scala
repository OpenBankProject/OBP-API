package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

/**
 * Atomic, guarded status transition for `mappeduserauthcontextupdate`.
 *
 * The challenge-answer path checks status == INITIATED then writes ACCEPTED/REJECTED as two
 * separate operations; this collapses them into one conditional UPDATE so two concurrent correct
 * answers cannot both be accepted. Returns affected rows (0 or 1).
 */
object DoobieUserAuthContextUpdateQueries {

  def conditionalStatusTransition(userAuthContextUpdateId: Long, guardStatus: String, newStatus: String): Int =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappeduserauthcontextupdate
            SET mstatus = $newStatus,
                updatedat = NOW()
            WHERE id = $userAuthContextUpdateId
              AND mstatus = $guardStatus""".update.run
    )
}
