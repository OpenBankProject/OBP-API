package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

object DoobieConsentSchedulerQueries {

  def conditionallyUpdateStatus(
    consentPrimaryKey: Long,
    guardStatus: String,
    newStatus: String,
    newNote: String
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE mappedconsent
          SET mstatus = $newStatus,
              mnote = $newNote,
              mstatusupdatedatetime = NOW()
          WHERE id = $consentPrimaryKey
            AND mstatus = $guardStatus""".update.run
  )

  def conditionallyExpireValidBerlinGroupConsent(
    consentPrimaryKey: Long,
    newNote: String
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE mappedconsent
          SET mstatus = ${"expired"},
              mnote = $newNote,
              mstatusupdatedatetime = NOW()
          WHERE id = $consentPrimaryKey
            AND mstatus IN ('valid', 'VALID')""".update.run
  )
}
