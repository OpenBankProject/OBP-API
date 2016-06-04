package code.examplething

import code.branches.Branches._
import code.examplething.Thing.{Bar, Foo, ThingId, Thing}
import code.model.BankId

import code.common.{Address, License, Location, Meta}

import code.util.DefaultStringField
import net.liftweb.common.Box
import net.liftweb.mapper._
import org.joda.time.Hours

import scala.util.Try

object MappedThingProvider extends ThingProvider {

  override protected def getThingFromProvider(branchId: BranchId): Option[Thing] =
    MappedThing.find(By(MappedThing.mBranchId, branchId.value))

  override protected def getThingsFromProvider(bankId: BankId): Option[List[Thing]] = {
    Some(MappedThing.findAll(By(MappedThing.mBankId, bankId.value)))
  }
}

class MappedThing extends Thing with LongKeyedMapper[MappedThing] with IdPK {

  override def getSingleton = MappedThing

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

  override def thingId: ThingId = ThingId(mBranchId.get)
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

  override def foo: Foo = new Foo {
    override def hours: String = mLobbyHours
  }

  override def bar: Bar = new Bar {
    override def hours: String = mDriveUpHours
  }


  override def location: Location = new Location {
    override def latitude: Double = mlocationLatitude
    override def longitude: Double = mlocationLongitude
  }

}

//
object MappedThing extends MappedThing with LongKeyedMetaMapper[MappedThing] {
  override def dbIndexes = UniqueIndex(mBankId, mBranchId) :: Index(mBankId) :: super.dbIndexes
}

