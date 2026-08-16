package code.atms

import code.api.util.{OBPLimit, OBPOffset, OBPQueryParam}
import code.util.Helper.optionBooleanToString
import code.util.{TwentyFourHourClockString, UUIDString}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

// The Lift AtmsProvider implementation was removed: Atms.buildOne now returns
// DoobieAtmsProvider. The MappedAtm entity below is still live (ToSchemify, sandbox
// import, MxOF JSON) and is removed in a later step of this table's migration.


class MappedAtm extends AtmT with LongKeyedMapper[MappedAtm] with IdPK with CreatedUpdated {

  override def getSingleton: code.atms.MappedAtm.type = MappedAtm

  object mBankId extends UUIDString(this)
  object mName extends MappedString(this, 255)

  object mAtmId extends UUIDString(this)

  // Exposed inside address. See below
  object mLine1 extends MappedString(this, 255)
  object mLine2 extends MappedString(this, 255)
  object mLine3 extends MappedString(this, 255)
  object mCity extends MappedString(this, 255)
  object mCounty extends MappedString(this, 255)
  object mState extends MappedString(this, 255)
  object mCountryCode extends MappedString(this, 2)
  object mPostCode extends MappedString(this, 20)

  object mlocationLatitude extends MappedDouble(this)
  object mlocationLongitude extends MappedDouble(this)

  // Exposed inside meta.license See below
  object mLicenseId extends UUIDString(this)
  object mLicenseName extends MappedString(this, 255)


  // Drive Up
  object mOpeningTimeOnMonday extends TwentyFourHourClockString(this)
  object mClosingTimeOnMonday extends TwentyFourHourClockString(this)

  object mOpeningTimeOnTuesday extends TwentyFourHourClockString(this)
  object mClosingTimeOnTuesday extends TwentyFourHourClockString(this)

  object mOpeningTimeOnWednesday extends TwentyFourHourClockString(this)
  object mClosingTimeOnWednesday extends TwentyFourHourClockString(this)

  object mOpeningTimeOnThursday extends TwentyFourHourClockString(this)
  object mClosingTimeOnThursday extends TwentyFourHourClockString(this)

  object mOpeningTimeOnFriday extends TwentyFourHourClockString(this)
  object mClosingTimeOnFriday extends TwentyFourHourClockString(this)

  object mOpeningTimeOnSaturday extends TwentyFourHourClockString(this)
  object mClosingTimeOnSaturday extends TwentyFourHourClockString(this)

  object mOpeningTimeOnSunday extends TwentyFourHourClockString(this)
  object mClosingTimeOnSunday extends TwentyFourHourClockString(this)



  object mIsAccessible extends MappedString(this, 1) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown

  object mLocatedAt extends MappedString(this, 32)
  object mMoreInfo extends MappedString(this, 128)

  object mHasDepositCapability extends MappedString(this, 1)
  
  object mSupportedLanguages extends MappedText(this)
  object mServices extends MappedText(this)
  object mNotes extends MappedText(this)
  object mAccessibilityFeatures extends MappedText(this)
  object mSupportedCurrencies extends MappedText(this)
  object mLocationCategories extends MappedText(this)
  object mMinimumWithdrawal extends MappedString(this, 255)
  object mBranchIdentification extends MappedString(this, 255)
  object mSiteIdentification extends MappedString(this, 255)
  object mSiteName extends MappedString(this, 255)
  object mCashWithdrawalNationalFee extends MappedString(this, 255)
  object mCashWithdrawalInternationalFee extends MappedString(this, 255)
  object mBalanceInquiryFee extends MappedString(this, 255)
  object mAtmType extends MappedString(this, 255)
  object mPhone extends MappedString(this, 255)


  override def atmId: AtmId = AtmId(mAtmId.get)

  override def bankId : BankId = BankId(mBankId.get)
  override def name: String = mName.get

  override def address: com.openbankproject.commons.model.Address = Address(
    line1 = mLine1.get,
    line2 = mLine2.get,
    line3 = mLine3.get,
    city = mCity.get,
    county = if(mCounty == null || mCounty =="") None else Some(mCounty.get),
    state = mState.get,
    countryCode = mCountryCode.get,
    postCode = mPostCode.get
  )

  override def meta: com.openbankproject.commons.model.Meta = Meta (
    license = License (
      id = mLicenseId.get,
     name = mLicenseName.get
    )
  )

  override def location: com.openbankproject.commons.model.Location = Location(
    latitude = mlocationLatitude.get,
    longitude = mlocationLongitude.get,
    None,
    None
  )


  override def  OpeningTimeOnMonday: Some[String] = Some(mOpeningTimeOnMonday.get)
  override def  ClosingTimeOnMonday: Some[String] = Some(mClosingTimeOnMonday.get)

  override def  OpeningTimeOnTuesday: Some[String] = Some(mOpeningTimeOnTuesday.get)
  override def  ClosingTimeOnTuesday: Some[String] = Some(mClosingTimeOnTuesday.get)

  override def  OpeningTimeOnWednesday: Some[String] = Some(mOpeningTimeOnWednesday.get)
  override def  ClosingTimeOnWednesday: Some[String] = Some(mClosingTimeOnWednesday.get)

  override def  OpeningTimeOnThursday: Some[String] = Some(mOpeningTimeOnThursday.get)
  override def  ClosingTimeOnThursday: Some[String] = Some(mClosingTimeOnThursday.get)

  override def  OpeningTimeOnFriday: Some[String] = Some(mOpeningTimeOnFriday.get)
  override def  ClosingTimeOnFriday: Some[String] = Some(mClosingTimeOnFriday.get)

  override def  OpeningTimeOnSaturday: Some[String] = Some(mOpeningTimeOnSaturday.get)
  override def  ClosingTimeOnSaturday: Some[String] = Some(mClosingTimeOnSaturday.get)

  override def  OpeningTimeOnSunday: Some[String] = Some(mOpeningTimeOnSunday.get)
  override def  ClosingTimeOnSunday: Some[String] = Some(mClosingTimeOnSunday.get)


  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  override def  isAccessible = mIsAccessible.get match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  override def  locatedAt: Some[String] = Some(mLocatedAt.get)
  override def  moreInfo: Some[String] = Some(mMoreInfo.get)

  override def  hasDepositCapability = mHasDepositCapability.get match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  override def  supportedLanguages = mSupportedLanguages.get match {
    case value: String if value.nonEmpty => Some (value.split(",").toList)
    case _ => None
  }

  override def services: Option[List[String]] = mServices.get match {
    case value: String if value.nonEmpty => Some (value.split(",").toList)
    case _ => None
  }
  
  override def notes: Option[List[String]] = mNotes.get match {
    case value: String if value.nonEmpty=> Some (value.split(",").toList)
    case _ => None
  }
  
  override def accessibilityFeatures: Option[List[String]] = mAccessibilityFeatures.get match {
    case value: String if value.nonEmpty=> Some (value.split(",").toList)
    case _ => None
  }
  
  override def supportedCurrencies: Option[List[String]] = mSupportedCurrencies.get match {
    case value: String if value.nonEmpty=> Some (value.split(",").toList)
    case _ => None
  }
  
  override def minimumWithdrawal: Option[String] = mMinimumWithdrawal.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }
  override def branchIdentification: Option[String] = mBranchIdentification.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }
  override def locationCategories: Option[List[String]] = mLocationCategories.get match {
    case value: String if value.nonEmpty => Some (value.split(",").toList)
    case _ => None
  }
  override def siteIdentification: Option[String] = mSiteIdentification.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }
  override def siteName: Option[String] = mSiteName.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }
  override def cashWithdrawalNationalFee: Option[String] = mCashWithdrawalNationalFee.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }
  override def cashWithdrawalInternationalFee: Option[String] = mCashWithdrawalInternationalFee.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }
  override def balanceInquiryFee: Option[String] = mBalanceInquiryFee.get match {
    case value: String if value.nonEmpty => Some (value)
    case _ => None
  }

  override def atmType: Option[String] = mAtmType.get match {
    case value: String if value.nonEmpty => Some(value)
    case _ => None
  }

  override def phone: Option[String] = mPhone.get match {
    case value: String if value.nonEmpty => Some(value)
    case _ => None
  }

}

//
object MappedAtm extends MappedAtm with LongKeyedMetaMapper[MappedAtm] {
  override def dbIndexes = UniqueIndex(mBankId, mAtmId) :: Index(mBankId) :: super.dbIndexes
}

