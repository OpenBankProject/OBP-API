package code.api.util.migration

import code.api.util.{APIUtil, JwtUtil}
import code.api.util.migration.Migration.saveLog
import code.consent.MappedConsent
import net.liftweb.mapper._
import net.liftweb.common.Full
import code.util.Helper.MdcLoggable

object MigrationOfConsentJwtPayload extends MdcLoggable {

  def backfillJwtPayload(name: String): Boolean = {
    val startDate = System.currentTimeMillis()
    val commitId: String = APIUtil.gitCommit
    var isSuccessful = false
    var count = 0

    try {
      val consents = MappedConsent.findAll(
        NullRef(MappedConsent.mJsonWebTokenPayload),
        By_>(MappedConsent.mJsonWebToken, "")
      )
      consents.foreach { consent =>
        val jwt = consent.mJsonWebToken.get
        if (jwt != null && jwt.nonEmpty) {
          JwtUtil.getSignedPayloadAsJson(jwt) match {
            case Full(payload) =>
              consent.mJsonWebTokenPayload(payload).save
              count += 1
            case _ =>
              logger.warn(s"MigrationOfConsentJwtPayload says: failed to decode JWT for consent ${consent.mConsentId.get}")
          }
        }
      }
      isSuccessful = true
    } catch {
      case e: Exception =>
        logger.error(s"MigrationOfConsentJwtPayload says: ${e.getMessage}", e)
    }

    val endDate = System.currentTimeMillis()
    val comment = s"Backfilled jwt_payload for $count consents"
    saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
    isSuccessful
  }
}
