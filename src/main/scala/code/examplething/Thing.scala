package code.examplething


// Need to import these one by one because in same package!
import code.api.util.APIUtil
import code.model.BankId
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Thing extends SimpleInjector {

    val thingProvider = new Inject(buildOne _) {}
   // def buildOne: ThingProvider = MappedThingProvider


  // This determines the provider we use
  def buildOne: ThingProvider =
    APIUtil.getPropsValue("provider.thing").openOr("mapped") match {
      case "mapped" => MappedThingProvider
      case _ => MappedThingProvider
    }

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

trait ThingProvider {

  private val logger = Logger(classOf[ThingProvider])


  /*
  Common logic for returning or changing Things
  Datasource implementation details are in Thing provider
   */
  final def getThings(bankId : BankId) : Option[List[Thing]] = {
    getThingsFromProvider(bankId) match {
      case Some(things) => {

        val certainThings = for {
         thing <- things // if thing.meta.license.name.size > 3
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

