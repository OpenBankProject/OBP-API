/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.examplething


import code.util.UUIDString
import com.openbankproject.commons.model.BankId
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

