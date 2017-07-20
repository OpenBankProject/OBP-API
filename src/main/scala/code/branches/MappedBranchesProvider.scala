package code.branches

import code.branches.Branches._
import code.model.BankId

import code.common.{Address, License, Location, Meta}

import code.util.{TwentyFourHourClockString, TwentyFourHourClockString$, UUIDString, DefaultStringField}
import net.liftweb.common.Box
import net.liftweb.mapper._
import org.joda.time.Hours

import scala.util.Try

object MappedBranchesProvider extends BranchesProvider {

  override protected def getBranchFromProvider(branchId: BranchId): Option[Branch] =
    MappedBranch.find(By(MappedBranch.mBranchId, branchId.value))

  override protected def getBranchesFromProvider(bankId: BankId): Option[List[Branch]] = {
    Some(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value))
      .map(
        branch =>
          branch.branchRoutingScheme == null && branch.branchRoutingAddress ==null match {
            case true => branch.mBranchRoutingScheme("OBP_BRANCH_ID").mBranchRoutingAddress(branch.branchId.value)
            case _ => branch
          }
      )
    )
  }
}

class MappedBranch extends Branch with LongKeyedMapper[MappedBranch] with IdPK {

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

  object mBranchType extends MappedString(this, 32)
  object mMoreInfo extends MappedString(this, 128)

  override def branchId: BranchId = BranchId(mBranchId.get)
  override def name: String = mName.get
  override def branchRoutingScheme: String = mBranchRoutingScheme.get
  override def branchRoutingAddress: String = mBranchRoutingAddress.get
  override def bankId: BankId = BankId(mBankId.get)

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

  override def lobby: Lobby = new Lobby {
    override def hours: String = mLobbyHours
  }

  override def driveUp: DriveUp = new DriveUp {
    override def hours: String = mDriveUpHours
  }


  override def location: Location = new Location {
    override def latitude: Double = mlocationLatitude
    override def longitude: Double = mlocationLongitude
  }


  // Opening / Closing times are expected to have the format 24 hour format e.g. 13:45
  // but could also be 25:44 if we want to represent a time after midnight.
  override def  lobbyOpeningTimeOnMonday : String = mLobbyOpeningTimeOnMonday.get
  override def  lobbyClosingTimeOnMonday : String = mLobbyClosingTimeOnMonday.get

  override def  lobbyOpeningTimeOnTuesday : String = mLobbyOpeningTimeOnTuesday.get
  override def  lobbyClosingTimeOnTuesday : String = mLobbyClosingTimeOnTuesday.get

  override def  lobbyOpeningTimeOnWednesday : String = mLobbyOpeningTimeOnWednesday.get
  override def  lobbyClosingTimeOnWednesday : String = mLobbyClosingTimeOnWednesday.get

  override def  lobbyOpeningTimeOnThursday : String = mLobbyOpeningTimeOnThursday.get
  override def  lobbyClosingTimeOnThursday: String = mLobbyClosingTimeOnThursday.get

  override def  lobbyOpeningTimeOnFriday : String = mLobbyOpeningTimeOnFriday.get
  override def  lobbyClosingTimeOnFriday : String = mLobbyClosingTimeOnFriday.get

  override def  lobbyOpeningTimeOnSaturday : String = mLobbyOpeningTimeOnSaturday.get
  override def  lobbyClosingTimeOnSaturday : String = mLobbyClosingTimeOnSaturday.get

  override def  lobbyOpeningTimeOnSunday: String = mLobbyOpeningTimeOnSunday.get
  override def  lobbyClosingTimeOnSunday : String = mLobbyClosingTimeOnSunday.get

  override def  driveUpOpeningTimeOnMonday : String = mDriveUpOpeningTimeOnMonday.get
  override def  driveUpClosingTimeOnMonday : String = mDriveUpClosingTimeOnMonday.get

  override def  driveUpOpeningTimeOnTuesday : String = mDriveUpOpeningTimeOnTuesday.get
  override def  driveUpClosingTimeOnTuesday : String = mDriveUpClosingTimeOnTuesday.get

  override def  driveUpOpeningTimeOnWednesday : String = mDriveUpOpeningTimeOnWednesday.get
  override def  driveUpClosingTimeOnWednesday : String = mDriveUpClosingTimeOnWednesday.get

  override def  driveUpOpeningTimeOnThursday : String = mDriveUpOpeningTimeOnThursday.get
  override def  driveUpClosingTimeOnThursday: String = mDriveUpClosingTimeOnThursday.get

  override def  driveUpOpeningTimeOnFriday : String = mDriveUpOpeningTimeOnFriday.get
  override def  driveUpClosingTimeOnFriday : String = mDriveUpClosingTimeOnFriday.get

  override def  driveUpOpeningTimeOnSaturday : String = mDriveUpOpeningTimeOnSaturday.get
  override def  driveUpClosingTimeOnSaturday : String = mDriveUpClosingTimeOnSaturday.get

  override def  driveUpOpeningTimeOnSunday: String = mDriveUpOpeningTimeOnSunday.get
  override def  driveUpClosingTimeOnSunday : String = mDriveUpClosingTimeOnSunday.get


  // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
  override def  isAccessible : String = mIsAccessible.get

  override def  branchType : String = mBranchType.get
  override def  moreInfo : String = mMoreInfo.get

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
//  object mBankId extends DefaultStringField(this)
//  object mName extends DefaultStringField(this)
//  object mUrl extends DefaultStringField(this)
//
//  override def name: String = mName.get
//  override def url: String = mUrl.get
//}
//
//
//object MappedLicense extends MappedLicense with LongKeyedMetaMapper[MappedLicense] {
//  override  def dbIndexes = Index(mBankId) :: super.dbIndexes
//}