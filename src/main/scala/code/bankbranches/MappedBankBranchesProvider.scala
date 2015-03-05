package code.bankbranches

import code.bankbranches.BankBranches.{DataLicense, BankBranchId, Address, BankBranch}
import code.model.BankId
import code.util.DefaultStringField
import net.liftweb.mapper._

object MappedBankBranchesProvider extends BankBranchesProvider {
  override protected def branchData(bank: BankId): List[BankBranch] =
    MappedBankBranch.findAll(By(MappedBankBranch.mBankId, bank.value))

  override protected def branchDataLicense(bank: BankId): Option[DataLicense] =
    MappedDataLicense.find(By(MappedDataLicense.mBankId, bank.value))
}

class MappedBankBranch extends BankBranch with LongKeyedMapper[MappedBankBranch] with IdPK {

  override def getSingleton = MappedBankBranch

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


  override def branchId: BankBranchId = BankBranchId(mBranchId.get)
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

object MappedBankBranch extends MappedBankBranch with LongKeyedMetaMapper[MappedBankBranch] {
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