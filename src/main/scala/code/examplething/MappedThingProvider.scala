package code.examplething


import code.model.BankId


import code.util.{UUIDString}
import net.liftweb.common.Box
import net.liftweb.mapper._



object MappedThingProvider extends ThingProvider {

  override protected def getThingFromProvider(thingId: ThingId): Option[Thing] =
    MappedThing.find(By(MappedThing.thingId_, thingId.value))

  override protected def getThingsFromProvider(bankId: BankId): Option[List[Thing]] = {
    Some(MappedThing.findAll(By(MappedThing.bankId_, bankId.value)))
  }
}

class MappedThing extends Thing with LongKeyedMapper[MappedThing] with IdPK {

  override def getSingleton = MappedThing

  object bankId_ extends UUIDString(this)
  object name_ extends MappedString(this, 255)

  object thingId_ extends MappedString(this, 30)

  object fooSomething_ extends MappedString(this, 255)
  object barSomething_ extends MappedString(this, 255)

  override def thingId: ThingId = ThingId(thingId_.get)
  override def something: String = name_.get


  override def foo: Foo = new Foo {
    override def fooSomething: String = fooSomething_.get
  }

  override def bar: Bar = new Bar {
    override def barSomething: String = barSomething_.get
  }


}


object MappedThing extends MappedThing with LongKeyedMetaMapper[MappedThing] {
  override def dbIndexes = UniqueIndex(bankId_, thingId_) :: Index(bankId_) :: super.dbIndexes
}

