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


// Need to import these one by one because in same package!
import code.api.util.APIUtil
import com.openbankproject.commons.model.BankId
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object Thing extends SimpleInjector {

    val thingProvider = new Inject(() => buildOne) {}
    def buildOne: ThingProvider = MappedThingProvider

  //If you set props `provider.thing`, you can set to different providers
//  // This determines the provider we use
//  def buildOne: ThingProvider =
//    APIUtil.getPropsValue("provider.thing").openOr("mapped") match {
//      case "mapped" => MappedThingProvider
//      case _ => MappedThingProvider
//    }

}

case class ThingId(value : String)

trait Thing {
  def thingId : ThingId
  def something : String
  def foo : Foo
  def bar : Bar
}

trait Foo {
 def fooSomething : String
}

trait Bar {
  def barSomething : String
}


/*
A trait that defines interfaces to Thing
i.e. a ThingProvider should provide these:
 */

trait ThingProvider extends MdcLoggable {


  /*
  Common logic for returning or changing Things
  Datasource implementation details are in Thing provider
   */
  final def getThings(bankId : BankId) : Option[List[Thing]] = {
    getThingsFromProvider(bankId) match {
      case Some(things) => {

        val certainThings = for {
         thing <- things
        } yield thing
        Option(certainThings)
      }
      case None => None
    }
  }

  /*
  Return one Thing
   */
  final def getThing(thingId : ThingId) : Option[Thing] = {
    // Could do something here
    getThingFromProvider(thingId)  //.filter...
  }

  protected def getThingFromProvider(thingId : ThingId) : Option[Thing]
  protected def getThingsFromProvider(bank : BankId) : Option[List[Thing]]

}
