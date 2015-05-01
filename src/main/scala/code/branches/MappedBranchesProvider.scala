package code.branches

import code.branches.Branches._
import code.model.BankId
import code.util.DefaultStringField
import net.liftweb.common.Box
import net.liftweb.mapper._

import scala.util.Try

object MappedBranchesProvider extends BranchesProvider {

  override protected def getBranchFromProvider(branchId: BranchId): Option[Branch] =
  MappedBranch.find(By(MappedBranch.mBranchId, branchId.value))

  override protected def getBranchesFromProvider(bankId: BankId): Option[List[Branch]] = {
    Some(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value)))
  }

//  override protected def branchLicense(bank: BankId): Option[License] =
//    MappedDataLicense.find(By(MappedDataLicense.mBankId, bank.value))
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
  object mLine4 extends DefaultStringField(this)
  object mLine5 extends DefaultStringField(this)
  object mCountryCode extends MappedString(this, 2)
  object mPostCode extends DefaultStringField(this)


  // Exposed inside meta.license See below
  object mLicenseName extends DefaultStringField(this)
  object mLicenseUrl extends DefaultStringField(this)

  override def branchId: BranchId = BranchId(mBranchId.get)
  override def name: String = mName.get

  override def address: Address = new Address {
    override def line1: String = mLine1.get
    override def line2: String = mLine2.get
    override def line3: String = mLine3.get
    override def line4: String = mLine4.get
    override def line5: String = mLine5.get
    override def countryCode: String = mCountryCode.get
    override def postCode: String = mPostCode.get
  }

  override def meta: Meta = new Meta {
    override def license: License = new License {
      override def name: String = mLicenseName.get
      override def url: String = mLicenseUrl.get
    }
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