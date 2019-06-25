package code.api.berlin.group.model.consent


import code.api.util.ErrorMessages
import code.util.MappedUUID
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.util.Random



class MappedBerlinGroupConsent extends BerlinGroupConsent with LongKeyedMapper[MappedBerlinGroupConsent] with IdPK with CreatedUpdated {

  def getSingleton = MappedBerlinGroupConsent

  object mConsentId extends MappedUUID(this)
  object mUserId extends MappedString(this, 36)
  object mStatus extends MappedUUID(this)
  object mRecurringIndicator extends MappedBoolean(this)
  object mValidUntil extends MappedDate(this)
  object mFrequencyPerDay extends MappedInt(this)
  object mCombinedServiceIndicator extends MappedBoolean(this)
  object mLastActionDate extends MappedDate(this)

  override def consentId: String = mConsentId.get
  override def userId: String = mUserId.get
  override def status: String = mStatus.get
  override def recurringIndicator: Boolean = mRecurringIndicator.get
  override def validUntil = mValidUntil.get
  override def frequencyPerDay = mFrequencyPerDay.get
  override def combinedServiceIndicator = mCombinedServiceIndicator.get
  override def lastActionDate = mLastActionDate.get
}

object MappedBerlinGroupConsent extends MappedBerlinGroupConsent with LongKeyedMetaMapper[MappedBerlinGroupConsent] {
  override def dbIndexes = UniqueIndex(mConsentId) :: super.dbIndexes
}
