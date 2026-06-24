package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

object DoobieConsentSchedulerQueries {

  def conditionallyUpdateStatus(
    consentRowId: Long,
    guardStatus: String,
    newStatus: String,
    newNote: String
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE mappedconsent
          SET mstatus = $newStatus,
              mnote = $newNote,
              mstatusupdatedatetime = NOW()
          WHERE id = $consentRowId
            AND mstatus = $guardStatus""".update.run
  )

  def conditionallyExpireValidBerlinGroupConsent(
    consentRowId: Long,
    newNote: String
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE mappedconsent
          SET mstatus = ${"expired"},
              mnote = $newNote,
              mstatusupdatedatetime = NOW()
          WHERE id = $consentRowId
            AND mstatus IN ('valid', 'VALID')""".update.run
  )
}
