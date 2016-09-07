package code.branches

import code.branches.Branches._
import code.model.BankId

import code.common.{Address, License, Location, Meta}

import code.util.DefaultStringField
import net.liftweb.common.Box
import net.liftweb.mapper._
import org.joda.time.Hours

import scala.util.Try

object MappedBranchesProvider extends BranchesProvider {

  override protected def getBranchFromProvider(branchId: BranchId): Option[Branch] =
    MappedBranch.find(By(MappedBranch.mBranchId, branchId.value))

  override protected def getBranchesFromProvider(bankId: BankId): Option[List[Branch]] = {
    Some(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value)))
  }
}

class MappedBranch extends Branch with LongKeyedMapper[MappedBranch] with IdPK {

  override def getSingleton = MappedBranch

  object mBankId extends DefaultStringField(this)
  object mName extends DefaultStringField(this)

  object mBranchId extends DefaultStringField(this)

  // Exposed inside address. See below
  object mLine1 extends DefaultStringField(this)
  object mLine2 extends DefaultStringField(this)
  object mLine3 extends DefaultStringField(this)
  object mCity extends DefaultStringField(this)
  object mCounty extends DefaultStringField(this)
  object mState extends DefaultStringField(this)
  object mCountryCode extends MappedString(this, 2)
  object mPostCode extends DefaultStringField(this)

  object mlocationLatitude extends MappedDouble(this)
  object mlocationLongitude extends MappedDouble(this)

  // Exposed inside meta.license See below
  object mLicenseId extends DefaultStringField(this)
  object mLicenseName extends DefaultStringField(this)

  object mLobbyHours extends DefaultStringField(this)
  object mDriveUpHours extends DefaultStringField(this)

  override def branchId: BranchId = BranchId(mBranchId.get)
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