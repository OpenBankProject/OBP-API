package code.branches

import code.bankconnectors._
import code.branches.Branches._
import code.common._
import code.model.BankId
import code.util.{TwentyFourHourClockString, UUIDString}
import net.liftweb.mapper.{By, _}
import net.liftweb.common.{Box, Logger}

object MappedBranchesProvider extends BranchesProvider {

  private val logger = Logger(classOf[BranchesProvider])

  override protected def getBranchFromProvider(bankId: BankId, branchId: BranchId): Option[BranchT] =
    MappedBranch.find(
      By(MappedBranch.mBankId, bankId.value),
      By(MappedBranch.mBranchId, branchId.value)
    )

  override protected def getBranchesFromProvider(bankId: BankId, queryParams: OBPQueryParam*): Option[List[BranchT]] = {
    logger.debug(s"getBranchesFromProvider says bankId is $bankId")
  
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedBranch](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedBranch](value) }.headOption
    
    val optionalParams : Seq[QueryParam[MappedBranch]] = Seq(limit.toSeq, offset.toSeq).flatten
    val mapperParams = Seq(By(MappedBranch.mBankId, bankId.value)) ++ optionalParams
    
    val branches: Option[List[BranchT]] = Some(MappedBranch.findAll(mapperParams:_*))
  
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
  object mLobbyOpeningTimeOnMonday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnMonday extends TwentyFourHourClockString(this)

  object mLobbyOpeningTimeOnTuesday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnTuesday extends TwentyFourHourClockString(this)

  object mLobbyOpeningTimeOnWednesday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnWednesday extends TwentyFourHourClockString(this)

  object mLobbyOpeningTimeOnThursday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnThursday extends TwentyFourHourClockString(this)

  object mLobbyOpeningTimeOnFriday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnFriday extends TwentyFourHourClockString(this)

  object mLobbyOpeningTimeOnSaturday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnSaturday extends TwentyFourHourClockString(this)

  object mLobbyOpeningTimeOnSunday extends TwentyFourHourClockString(this)
  object mLobbyClosingTimeOnSunday extends TwentyFourHourClockString(this)


  // Drive Up
  object mDriveUpOpeningTimeOnMonday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnMonday extends TwentyFourHourClockString(this)

  object mDriveUpOpeningTimeOnTuesday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnTuesday extends TwentyFourHourClockString(this)

  object mDriveUpOpeningTimeOnWednesday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnWednesday extends TwentyFourHourClockString(this)

  object mDriveUpOpeningTimeOnThursday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnThursday extends TwentyFourHourClockString(this)

  object mDriveUpOpeningTimeOnFriday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnFriday extends TwentyFourHourClockString(this)

  object mDriveUpOpeningTimeOnSaturday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnSaturday extends TwentyFourHourClockString(this)

  object mDriveUpOpeningTimeOnSunday extends TwentyFourHourClockString(this)
  object mDriveUpClosingTimeOnSunday extends TwentyFourHourClockString(this)



  object mIsAccessible extends MappedString(this, 1) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown
  object mAccessibleFeatures extends MappedString(this,250)

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
        if (mBranchRoutingAddress == null || mBranchRoutingAddress == "") mBranchId.get else mBranchRoutingAddress.get
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
    monday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnMonday.get,
      closingTime = mLobbyClosingTimeOnMonday.get
    )),
    tuesday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnTuesday.get,
      closingTime = mLobbyClosingTimeOnTuesday.get
    )),
    wednesday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnWednesday.get,
      closingTime = mLobbyClosingTimeOnWednesday.get
    )),
    thursday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnThursday.get,
      closingTime = mLobbyClosingTimeOnThursday.get
    )),
    friday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnFriday.get,
      closingTime = mLobbyClosingTimeOnFriday.get
    )),
    saturday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnSaturday.get,
      closingTime = mLobbyClosingTimeOnSaturday.get
    )),
    sunday = List(OpeningTimes(
      openingTime = mLobbyOpeningTimeOnSunday.get,
      closingTime = mLobbyClosingTimeOnSunday.get
    ))
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

  override def accessibleFeatures: Option[String] = Some(mAccessibleFeatures.get)

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