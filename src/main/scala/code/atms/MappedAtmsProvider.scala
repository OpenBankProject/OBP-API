package code.atms

import code.atms.Atms._
import code.bankconnectors.{OBPLimit, OBPOffset, OBPQueryParam}
import code.common._
import code.model.BankId
import code.util.{TwentyFourHourClockString, UUIDString}
import net.liftweb.mapper._

object MappedAtmsProvider extends AtmsProvider {

  override protected def getAtmFromProvider(bankId: BankId, atmId: AtmId): Option[AtmT] =
  MappedAtm.find(By(MappedAtm.mAtmId, atmId.value),By(MappedAtm.mBankId, bankId.value))

  override protected def getAtmsFromProvider(bankId: BankId, queryParams: OBPQueryParam*): Option[List[AtmT]] = {
  
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedAtm](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedAtm](value) }.headOption
  
    val optionalParams : Seq[QueryParam[MappedAtm]] = Seq(limit.toSeq, offset.toSeq).flatten
    val mapperParams = Seq(By(MappedAtm.mBankId, bankId.value)) ++ optionalParams
    
    Some(MappedAtm.findAll(mapperParams:_*))
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


}

//
object MappedAtm extends MappedAtm with LongKeyedMetaMapper[MappedAtm] {
  override def dbIndexes = UniqueIndex(mBankId, mAtmId) :: Index(mBankId) :: super.dbIndexes
}

