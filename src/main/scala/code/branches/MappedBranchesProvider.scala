package code.branches

import code.branches.Branches.{DataLicense, BranchId, Address, Branch}
import code.model.BankId
import code.util.DefaultStringField
import net.liftweb.mapper._

object MappedBranchesProvider extends BranchesProvider {
  override protected def branchData(bank: BankId): List[Branch] =
    MappedBranch.findAll(By(MappedBranch.mBankId, bank.value))

  override protected def branchDataLicense(bank: BankId): Option[DataLicense] =
    MappedDataLicense.find(By(MappedDataLicense.mBankId, bank.value))
}

class MappedBranch extends Branch with LongKeyedMapper[MappedBranch] with IdPK {

  override def getSingleton = MappedBranch

  object mBankId extends DefaultStringField(this)
  object mName extends DefaultStringField(this)

  object mBranchId extends DefaultStringField(this)

  object mLine1 extends DefaultStringField(this)
  object mLine2 extends DefaultStringField(this)
  object mLine3 extends DefaultStringField(this)
  object mLine4 extends DefaultStringField(this)
  object mLine5 extends DefaultStringField(this)

  object mCountryCode extends MappedString(this, 2)
  object mPostCode extends DefaultStringField(this)


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
}

object MappedBranch extends MappedBranch with LongKeyedMetaMapper[MappedBranch] {
  override def dbIndexes = UniqueIndex(mBankId, mBranchId) :: Index(mBankId) :: super.dbIndexes
}

class MappedDataLicense extends DataLicense with LongKeyedMapper[MappedDataLicense] with IdPK {
  override def getSingleton = MappedDataLicense

  object mBankId extends DefaultStringField(this)
  object mName extends DefaultStringField(this)
  object mUrl extends DefaultStringField(this)

  override def name: String = mName.get
  override def url: String = mUrl.get
}

object MappedDataLicense extends MappedDataLicense with LongKeyedMetaMapper[MappedDataLicense] {
  override  def dbIndexes = Index(mBankId) :: super.dbIndexes
}