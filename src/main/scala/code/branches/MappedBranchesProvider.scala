package code.branches

import code.branches.Branches._
import code.common._
import code.model.BankId
import code.util.{TwelveHourClockString, UUIDString}
import net.liftweb.mapper._

import net.liftweb.common.{Box, Logger}

object MappedBranchesProvider extends BranchesProvider {

  private val logger = Logger(classOf[BranchesProvider])

  override protected def getBranchFromProvider(branchId: BranchId): Option[BranchT] =
    MappedBranch.find(By(MappedBranch.mBranchId, branchId.value))

  override protected def getBranchesFromProvider(bankId: BankId): Option[List[BranchT]] = {
    logger.debug(s"getBranchesFromProvider says bankId is $bankId")
    val branches: Option[List[BranchT]] = Some(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value)))
    branches
  }
}

class MappedBranch extends BranchT with LongKeyedMapper[MappedBranch] with IdPK {

  override def getSingleton = MappedBranch


  object mBankId extends UUIDString(this)
  object mName extends MappedString(this, 255)

  object mBranchId extends UUIDString(this)

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

  object mLobbyHours extends MappedString(this, 2000)
  object mDriveUpHours extends MappedString(this, 2000)
  object mBranchRoutingScheme extends MappedString(this, 32)
  object mBranchRoutingAddress extends MappedString(this, 64)

  // Lobby
  object mLobbyOpeningTimeOnMonday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnMonday extends TwelveHourClockString(this)

  object mLobbyOpeningTimeOnTuesday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnTuesday extends TwelveHourClockString(this)

  object mLobbyOpeningTimeOnWednesday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnWednesday extends TwelveHourClockString(this)

  object mLobbyOpeningTimeOnThursday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnThursday extends TwelveHourClockString(this)

  object mLobbyOpeningTimeOnFriday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnFriday extends TwelveHourClockString(this)

  object mLobbyOpeningTimeOnSaturday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnSaturday extends TwelveHourClockString(this)

  object mLobbyOpeningTimeOnSunday extends TwelveHourClockString(this)
  object mLobbyClosingTimeOnSunday extends TwelveHourClockString(this)


  // Drive Up
  object mDriveUpOpeningTimeOnMonday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnMonday extends TwelveHourClockString(this)

  object mDriveUpOpeningTimeOnTuesday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnTuesday extends TwelveHourClockString(this)

  object mDriveUpOpeningTimeOnWednesday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnWednesday extends TwelveHourClockString(this)

  object mDriveUpOpeningTimeOnThursday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnThursday extends TwelveHourClockString(this)

  object mDriveUpOpeningTimeOnFriday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnFriday extends TwelveHourClockString(this)

  object mDriveUpOpeningTimeOnSaturday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnSaturday extends TwelveHourClockString(this)

  object mDriveUpOpeningTimeOnSunday extends TwelveHourClockString(this)
  object mDriveUpClosingTimeOnSunday extends TwelveHourClockString(this)



  object mIsAccessible extends MappedString(this, 1) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown

  object mBranchType extends MappedString(this, 32)
  object mMoreInfo extends MappedString(this, 128)
  object mPhoneNumber extends MappedString(this, 32)

  override def branchId: BranchId = BranchId(mBranchId.get)
  override def name: String = mName.get

  // If not set, use BRANCH_ID and this value
  override def branchRouting: Option[RoutingT] = Some(new RoutingT {
    override def scheme: String = {
      if (mBranchRoutingScheme == null || mBranchRoutingScheme == "") "BRANCH_ID" else mBranchRoutingScheme.get
    }
    override def address: String = {
        if (mBranchRoutingAddress == null || mBranchRoutingAddress == "") mBranchId.get else mBranchRoutingAddress
      }
  })


  override def bankId: BankId = BankId(mBankId.get)

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

  override def lobbyString = Some(new LobbyStringT {
    override def hours: String = mLobbyHours.get
  })
  override def location =
    Location(
    latitude = mlocationLatitude.get,
    longitude = mlocationLongitude.get,
      None,
      None
  )

  override def driveUpString = Some(new DriveUpStringT {
    override def hours: String = mDriveUpHours.get
  }
  )


// Opening / Closing times are expected to have the format 24 hour format e.g. 13:45
// but could also be 25:44 if we want to represent a time after midnight.

  override def lobby = Some(
    Lobby(
    monday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnMonday.get,
      closingTime = mLobbyClosingTimeOnMonday.get
    ),
    tuesday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnTuesday.get,
      closingTime = mLobbyClosingTimeOnTuesday.get
    ),
    wednesday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnWednesday.get,
      closingTime = mLobbyClosingTimeOnWednesday.get
    ),
    thursday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnThursday.get,
      closingTime = mLobbyClosingTimeOnThursday.get
    ),
    friday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnFriday.get,
      closingTime = mLobbyClosingTimeOnFriday.get
    ),
    saturday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnSaturday.get,
      closingTime = mLobbyClosingTimeOnSaturday.get
    ),
    sunday = OpeningTimes(
      openingTime = mLobbyOpeningTimeOnSunday.get,
      closingTime = mLobbyClosingTimeOnSunday.get
    )
  )
  )
  // Opening / Closing times are expected to have the format 24 hour format e.g. 13:45
  // but could also be 25:44 if we want to represent a time after midnight.
  override def driveUp = Some(
    DriveUp(
    monday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnMonday.get,
      closingTime = mDriveUpClosingTimeOnMonday.get
    ),
    tuesday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnTuesday.get,
      closingTime = mDriveUpClosingTimeOnTuesday.get
    ),
    wednesday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnWednesday.get,
      closingTime = mDriveUpClosingTimeOnWednesday.get
    ),
    thursday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnThursday.get,
      closingTime = mDriveUpClosingTimeOnThursday.get
    ),
    friday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnFriday.get,
      closingTime = mDriveUpClosingTimeOnFriday.get
    ),
    saturday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnSaturday.get,
      closingTime = mDriveUpClosingTimeOnSaturday.get
    ),
    sunday = OpeningTimes(
      openingTime = mDriveUpOpeningTimeOnSunday.get,
      closingTime = mDriveUpClosingTimeOnSunday.get
    )
  )
  )




  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  override def  isAccessible = mIsAccessible.get match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  override def  branchType = Some(mBranchType.get)
  override def  moreInfo = Some(mMoreInfo.get)
  override def  phoneNumber = Some(mPhoneNumber.get)

}

//
object MappedBranch extends MappedBranch with LongKeyedMetaMapper[MappedBranch] {
  override def dbIndexes = UniqueIndex(mBankId, mBranchId) :: Index(mBankId) :: super.dbIndexes
}

/*
For storing the data license(s) (conceived for open data e.g. branches)
Currently used as one license per bank for all open data?
Else could store a link to this with each open data record - or via config for each open data type
 */


//class MappedLicense extends License with LongKeyedMapper[MappedLicense] with IdPK {
//  override def getSingleton = MappedLicense
//
//  object mBankId extends UUIDString(this)
//  object mName extends MappedString(this, 123)
//  object mUrl extends MappedString(this, 2000)
//
//  override def name: String = mName.get
//  override def url: String = mUrl.get
//}
//
//
//object MappedLicense extends MappedLicense with LongKeyedMetaMapper[MappedLicense] {
//  override  def dbIndexes = Index(mBankId) :: super.dbIndexes
//}