package code.examplething


import code.model.BankId


import code.util.DefaultStringField
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

  object bankId_ extends DefaultStringField(this)
  object name_ extends DefaultStringField(this)

  object thingId_ extends DefaultStringField(this)

  object fooSomething_ extends DefaultStringField(this)
  object barSomething_ extends DefaultStringField(this)

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

