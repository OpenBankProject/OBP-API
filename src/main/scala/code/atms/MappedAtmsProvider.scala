package code.atms

import code.atms.Atms._
import code.common.{Address, License, Location, Meta}
import code.model.BankId
import code.util.{TwentyFourHourClockString, MediumString, UUIDString}
import net.liftweb.mapper._

object MappedAtmsProvider extends AtmsProvider {

  override protected def getAtmFromProvider(atmId: AtmId): Option[Atm] =
  MappedAtm.find(By(MappedAtm.mAtmId, atmId.value))

  override protected def getAtmsFromProvider(bankId: BankId): Option[List[Atm]] = {
    Some(MappedAtm.findAll(By(MappedAtm.mBankId, bankId.value)))
  }


}

class MappedAtm extends Atm with LongKeyedMapper[MappedAtm] with IdPK {

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

  object mBranchType extends MappedString(this, 32)
  object mMoreInfo extends MappedString(this, 128)



  override def atmId: AtmId = AtmId(mAtmId.get)

  override def bankId : BankId = BankId(mBankId.get)
  override def name: String = mName.get

  override def address: Address = new Address {
    override def line1: String = mLine1.get
    override def line2: String = mLine2.get
    override def line3: String = mLine3.get
    override def city: String = mCity.get
    override def county: String = mCounty.get
    override def state: String = mState.get
    override def countryCode: String = mCountryCode.get
    override def postCode: String = mPostCode.get
  }

  override def meta: Meta = new Meta {
    override def license: License = new License {
      override def id: String = mLicenseId.get
      override def name: String = mLicenseName.get
    }
  }

  override def location: Location = new Location {
    override def latitude: Double = mlocationLatitude
    override def longitude: Double = mlocationLongitude
  }


  override def  OpeningTimeOnMonday : String = mOpeningTimeOnMonday.get
  override def  ClosingTimeOnMonday : String = mClosingTimeOnMonday.get

  override def  OpeningTimeOnTuesday : String = mOpeningTimeOnTuesday.get
  override def  ClosingTimeOnTuesday : String = mClosingTimeOnTuesday.get

  override def  OpeningTimeOnWednesday : String = mOpeningTimeOnWednesday.get
  override def  ClosingTimeOnWednesday : String = mClosingTimeOnWednesday.get

  override def  OpeningTimeOnThursday : String = mOpeningTimeOnThursday.get
  override def  ClosingTimeOnThursday: String = mClosingTimeOnThursday.get

  override def  OpeningTimeOnFriday : String = mOpeningTimeOnFriday.get
  override def  ClosingTimeOnFriday : String = mClosingTimeOnFriday.get

  override def  OpeningTimeOnSaturday : String = mOpeningTimeOnSaturday.get
  override def  ClosingTimeOnSaturday : String = mClosingTimeOnSaturday.get

  override def  OpeningTimeOnSunday: String = mOpeningTimeOnSunday.get
  override def  ClosingTimeOnSunday : String = mClosingTimeOnSunday.get


  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  override def  isAccessible : String = mIsAccessible.get

  override def  branchType : String = mBranchType.get
  override def  moreInfo : String = mMoreInfo.get




}

//
object MappedAtm extends MappedAtm with LongKeyedMetaMapper[MappedAtm] {
  override def dbIndexes = UniqueIndex(mBankId, mAtmId) :: Index(mBankId) :: super.dbIndexes
}

