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

package code.scheduler

import java.util.{Date, UUID}

import code.consent.{ConsentStatus, MappedConsent}
import code.setup.ServerSetup
import com.openbankproject.commons.util.ApiStandards
import net.liftweb.mapper.{By, DB}

/**
 * The unfinishedObpConsents task: OBP consents still INITIATED after the time limit are rejected, so an app that
 * reuses a pending consent is never stuck on one. Seeds real consent rows, backdates them with SQL (the ORM sets
 * updatedAt itself on save) and runs the task directly.
 */
class ConsentSchedulerTest extends ServerSetup {

  private val sixHours = 21600
  private val hourMs = 3600000L

  private def consent(status: ConsentStatus.Value, apiStandard: String, hoursAgo: Long): String = {
    val consentId = UUID.randomUUID.toString
    MappedConsent.create
      .mConsentId(consentId)
      .mStatus(status.toString)
      .mApiStandard(apiStandard)
      .saveMe()
    DB.runUpdate(
      s"UPDATE ${MappedConsent.dbTableName} SET ${MappedConsent.updatedAt.dbColumnName} = ? " +
        s"WHERE ${MappedConsent.mConsentId.dbColumnName} = ?",
      List(new Date(new Date().getTime - hoursAgo * hourMs), consentId)
    )
    consentId
  }

  private def statusOf(consentId: String): String =
    MappedConsent.find(By(MappedConsent.mConsentId, consentId)).map(_.mStatus.get).openOr("missing")

  feature("Rejecting OBP consents that stay INITIATED") {

    scenario("An OBP consent INITIATED for longer than the limit is rejected, with a note saying why") {
      val id = consent(ConsentStatus.INITIATED, ApiStandards.obp.toString, hoursAgo = 7)
      ConsentScheduler.unfinishedObpConsents(sixHours)
      statusOf(id) shouldBe ConsentStatus.REJECTED.toString
      MappedConsent.find(By(MappedConsent.mConsentId, id)).map(_.mNote.get).openOr("") should include("not accepted within 21600 seconds")
    }

    scenario("A recent INITIATED consent, one already ACCEPTED and one of another standard are left alone") {
      val recent = consent(ConsentStatus.INITIATED, ApiStandards.obp.toString, hoursAgo = 1)
      val accepted = consent(ConsentStatus.ACCEPTED, ApiStandards.obp.toString, hoursAgo = 7)
      val otherStandard = consent(ConsentStatus.INITIATED, "BG", hoursAgo = 7)
      ConsentScheduler.unfinishedObpConsents(sixHours)
      statusOf(recent) shouldBe ConsentStatus.INITIATED.toString
      statusOf(accepted) shouldBe ConsentStatus.ACCEPTED.toString
      statusOf(otherStandard) shouldBe ConsentStatus.INITIATED.toString
    }
  }
}
