/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
