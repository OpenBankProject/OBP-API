package code.atms

import code.atms.Atms._
import code.common.{Address, License, Location, Meta}
import code.model.BankId
import code.util.DefaultStringField
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

  object mBankId extends DefaultStringField(this)
  object mName extends DefaultStringField(this)

  object mAtmId extends DefaultStringField(this)

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

}

//
object MappedAtm extends MappedAtm with LongKeyedMetaMapper[MappedAtm] {
  override def dbIndexes = UniqueIndex(mBankId, mAtmId) :: Index(mBankId) :: super.dbIndexes
}

