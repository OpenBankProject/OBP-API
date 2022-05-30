package code.atms

import code.api.util.{OBPLimit, OBPOffset, OBPQueryParam}
import code.bankconnectors.LocalMappedConnector.getAtmLegacy
import code.util.Helper.optionBooleanToString
import code.util.{TwentyFourHourClockString, UUIDString}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

object MappedAtmsProvider extends AtmsProvider {

  override protected def getAtmFromProvider(bankId: BankId, atmId: AtmId): Option[AtmT] =
  MappedAtm.find(By(MappedAtm.mAtmId, atmId.value),By(MappedAtm.mBankId, bankId.value))

  override protected def getAtmsFromProvider(bankId: BankId, queryParams: List[OBPQueryParam]): Option[List[AtmT]] = {
  
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedAtm](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedAtm](value) }.headOption
  
    val optionalParams : Seq[QueryParam[MappedAtm]] = Seq(limit.toSeq, offset.toSeq).flatten
    val mapperParams = Seq(By(MappedAtm.mBankId, bankId.value)) ++ optionalParams
    
    Some(MappedAtm.findAll(mapperParams:_*))
  }

  override def createOrUpdateAtm(atm: AtmT): Box[AtmT] = {

    val isAccessibleString = optionBooleanToString(atm.isAccessible)
    val hasDepositCapabilityString = optionBooleanToString(atm.hasDepositCapability)
    val supportedLanguagesString = atm.supportedLanguages.map(_.mkString(",")).getOrElse("")
    val servicesString = atm.services.map(_.mkString(",")).getOrElse("")
    val accessibilityFeaturesString = atm.accessibilityFeatures.map(_.mkString(",")).getOrElse("")
    val supportedCurrenciesString = atm.supportedCurrencies.map(_.mkString(",")).getOrElse("")
    val notesString = atm.notes.map(_.mkString(",")).getOrElse("")
    val locationCategoriesString = atm.locationCategories.map(_.mkString(",")).getOrElse("")

    //check the atm existence and update or insert data
    getAtmLegacy(atm.bankId, atm.atmId) match {
      case Full(mappedAtm: MappedAtm) =>
        tryo {
          mappedAtm.mName(atm.name)
            .mLine1(atm.address.line1)
            .mLine2(atm.address.line2)
            .mLine3(atm.address.line3)
            .mCity(atm.address.city)
            .mCounty(atm.address.county.getOrElse(""))
            .mCountryCode(atm.address.countryCode)
            .mState(atm.address.state)
            .mPostCode(atm.address.postCode)
            .mlocationLatitude(atm.location.latitude)
            .mlocationLongitude(atm.location.longitude)
            .mLicenseId(atm.meta.license.id)
            .mLicenseName(atm.meta.license.name)
            .mOpeningTimeOnMonday(atm.OpeningTimeOnMonday.orNull)
            .mClosingTimeOnMonday(atm.ClosingTimeOnMonday.orNull)

            .mOpeningTimeOnTuesday(atm.OpeningTimeOnTuesday.orNull)
            .mClosingTimeOnTuesday(atm.ClosingTimeOnTuesday.orNull)

            .mOpeningTimeOnWednesday(atm.OpeningTimeOnWednesday.orNull)
            .mClosingTimeOnWednesday(atm.ClosingTimeOnWednesday.orNull)

            .mOpeningTimeOnThursday(atm.OpeningTimeOnThursday.orNull)
            .mClosingTimeOnThursday(atm.ClosingTimeOnThursday.orNull)

            .mOpeningTimeOnFriday(atm.OpeningTimeOnFriday.orNull)
            .mClosingTimeOnFriday(atm.ClosingTimeOnFriday.orNull)

            .mOpeningTimeOnSaturday(atm.OpeningTimeOnSaturday.orNull)
            .mClosingTimeOnSaturday(atm.ClosingTimeOnSaturday.orNull)

            .mOpeningTimeOnSunday(atm.OpeningTimeOnSunday.orNull)
            .mClosingTimeOnSunday(atm.ClosingTimeOnSunday.orNull)
            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown
            .mLocatedAt(atm.locatedAt.orNull)
            .mMoreInfo(atm.moreInfo.orNull)
            .mHasDepositCapability(hasDepositCapabilityString)
            .mSupportedLanguages(supportedLanguagesString)
            .mServices(servicesString)
            .mNotes(notesString)
            .mAccessibilityFeatures(accessibilityFeaturesString)
            .mSupportedCurrencies(supportedCurrenciesString)
            .mLocationCategories(locationCategoriesString)
            .mMinimumWithdrawal(atm.minimumWithdrawal.orNull)
            .mBranchIdentification(atm.branchIdentification.orNull)
            .mSiteIdentification(atm.siteIdentification.orNull)
            .mSiteName(atm.siteName.orNull)
            .mCashWithdrawalNationalFee(atm.cashWithdrawalNationalFee.orNull)
            .mCashWithdrawalInternationalFee(atm.cashWithdrawalInternationalFee.orNull)
            .mBalanceInquiryFee(atm.balanceInquiryFee.orNull)
            .saveMe()
        }
      case _ =>
        tryo {
          MappedAtm.create
            .mAtmId(atm.atmId.value)
            .mBankId(atm.bankId.value)
            .mName(atm.name)
            .mLine1(atm.address.line1)
            .mLine2(atm.address.line2)
            .mLine3(atm.address.line3)
            .mCity(atm.address.city)
            .mCounty(atm.address.county.getOrElse(""))
            .mCountryCode(atm.address.countryCode)
            .mState(atm.address.state)
            .mPostCode(atm.address.postCode)
            .mlocationLatitude(atm.location.latitude)
            .mlocationLongitude(atm.location.longitude)
            .mLicenseId(atm.meta.license.id)
            .mLicenseName(atm.meta.license.name)
            .mOpeningTimeOnMonday(atm.OpeningTimeOnMonday.orNull)
            .mClosingTimeOnMonday(atm.ClosingTimeOnMonday.orNull)

            .mOpeningTimeOnTuesday(atm.OpeningTimeOnTuesday.orNull)
            .mClosingTimeOnTuesday(atm.ClosingTimeOnTuesday.orNull)

            .mOpeningTimeOnWednesday(atm.OpeningTimeOnWednesday.orNull)
            .mClosingTimeOnWednesday(atm.ClosingTimeOnWednesday.orNull)

            .mOpeningTimeOnThursday(atm.OpeningTimeOnThursday.orNull)
            .mClosingTimeOnThursday(atm.ClosingTimeOnThursday.orNull)

            .mOpeningTimeOnFriday(atm.OpeningTimeOnFriday.orNull)
            .mClosingTimeOnFriday(atm.ClosingTimeOnFriday.orNull)

            .mOpeningTimeOnSaturday(atm.OpeningTimeOnSaturday.orNull)
            .mClosingTimeOnSaturday(atm.ClosingTimeOnSaturday.orNull)

            .mOpeningTimeOnSunday(atm.OpeningTimeOnSunday.orNull)
            .mClosingTimeOnSunday(atm.ClosingTimeOnSunday.orNull)
            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown
            .mLocatedAt(atm.locatedAt.orNull)
            .mMoreInfo(atm.moreInfo.orNull)
            .mHasDepositCapability(hasDepositCapabilityString)
            .mSupportedLanguages(supportedLanguagesString)
            .mServices(servicesString)
            .mNotes(notesString)
            .mAccessibilityFeatures(accessibilityFeaturesString)
            .mSupportedCurrencies(supportedCurrenciesString)
            .mLocationCategories(locationCategoriesString)
            .mMinimumWithdrawal(atm.minimumWithdrawal.orNull)
            .mBranchIdentification(atm.branchIdentification.orNull)
            .mSiteIdentification(atm.siteIdentification.orNull)
            .mSiteName(atm.siteName.orNull)
            .mCashWithdrawalNationalFee(atm.cashWithdrawalNationalFee.orNull)
            .mCashWithdrawalInternationalFee(atm.cashWithdrawalInternationalFee.orNull)
            .mBalanceInquiryFee(atm.balanceInquiryFee.orNull)
            .saveMe()
        }
    }
  }

  override def deleteAtm(atm: AtmT): Box[Boolean] = {
    MappedAtm.find(By(MappedAtm.mAtmId, atm.atmId.value)).map(_.delete_!)
  }

}

class MappedAtm extends AtmT with LongKeyedMapper[MappedAtm] with IdPK {

  override def getSingleton = MappedAtm

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


  override def atmId: AtmId = AtmId(mAtmId.get)

  override def bankId : BankId = BankId(mBankId.get)
  override def name: String = mName.get

  override def address = Address(
    line1 = mLine1.get,
    line2 = mLine2.get,
    line3 = mLine3.get,
    city = mCity.get,
    county = Some(mCounty.get),
    state = mState.get,
    countryCode = mCountryCode.get,
    postCode = mPostCode.get
  )

  override def meta = Meta (
    license = License (
      id = mLicenseId.get,
     name = mLicenseName.get
    )
  )

  override def location = Location(
    latitude = mlocationLatitude.get,
    longitude = mlocationLongitude.get,
    None,
    None
  )


  override def  OpeningTimeOnMonday = Some(mOpeningTimeOnMonday.get)
  override def  ClosingTimeOnMonday = Some(mClosingTimeOnMonday.get)

  override def  OpeningTimeOnTuesday = Some(mOpeningTimeOnTuesday.get)
  override def  ClosingTimeOnTuesday = Some(mClosingTimeOnTuesday.get)

  override def  OpeningTimeOnWednesday = Some(mOpeningTimeOnWednesday.get)
  override def  ClosingTimeOnWednesday = Some(mClosingTimeOnWednesday.get)

  override def  OpeningTimeOnThursday = Some(mOpeningTimeOnThursday.get)
  override def  ClosingTimeOnThursday = Some(mClosingTimeOnThursday.get)

  override def  OpeningTimeOnFriday = Some(mOpeningTimeOnFriday.get)
  override def  ClosingTimeOnFriday = Some(mClosingTimeOnFriday.get)

  override def  OpeningTimeOnSaturday = Some(mOpeningTimeOnSaturday.get)
  override def  ClosingTimeOnSaturday = Some(mClosingTimeOnSaturday.get)

  override def  OpeningTimeOnSunday = Some(mOpeningTimeOnSunday.get)
  override def  ClosingTimeOnSunday = Some(mClosingTimeOnSunday.get)


  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  override def  isAccessible = mIsAccessible.get match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  override def  locatedAt = Some(mLocatedAt.get)
  override def  moreInfo = Some(mMoreInfo.get)

  override def  hasDepositCapability = mHasDepositCapability.get match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  override def  supportedLanguages = mSupportedLanguages.get match {
    case value: String => Some (value.split(",").toList)
    case _ => None
  }

  override def services: Option[List[String]] = mServices.get match {
    case value: String => Some (value.split(",").toList)
    case _ => None
  }
  
  override def notes: Option[List[String]] = mNotes.get match {
    case value: String => Some (value.split(",").toList)
    case _ => None
  }
  
  override def accessibilityFeatures: Option[List[String]] = mAccessibilityFeatures.get match {
    case value: String => Some (value.split(",").toList)
    case _ => None
  }
  
  override def supportedCurrencies: Option[List[String]] = mSupportedCurrencies.get match {
    case value: String => Some (value.split(",").toList)
    case _ => None
  }
  
  override def minimumWithdrawal: Option[String] = mMinimumWithdrawal.get match {
    case value: String => Some (value)
    case _ => None
  }
  override def branchIdentification: Option[String] = mBranchIdentification.get match {
    case value: String => Some (value)
    case _ => None
  }
  override def locationCategories: Option[List[String]] = mLocationCategories.get match {
    case value: String => Some (value.split(",").toList)
    case _ => None
  }
  override def siteIdentification: Option[String] = mSiteIdentification.get match {
    case value: String => Some (value)
    case _ => None
  }
  override def siteName: Option[String] = mSiteName.get match {
    case value: String => Some (value)
    case _ => None
  }
  override def cashWithdrawalNationalFee: Option[String] = mCashWithdrawalNationalFee.get match {
    case value: String => Some (value)
    case _ => None
  }
  override def cashWithdrawalInternationalFee: Option[String] = mCashWithdrawalInternationalFee.get match {
    case value: String => Some (value)
    case _ => None
  }
  override def balanceInquiryFee: Option[String] = mBalanceInquiryFee.get match {
    case value: String => Some (value)
    case _ => None
  }

}

//
object MappedAtm extends MappedAtm with LongKeyedMetaMapper[MappedAtm] {
  override def dbIndexes = UniqueIndex(mBankId, mAtmId) :: Index(mBankId) :: super.dbIndexes
}

