package code.examplething


// Need to import these one by one because in same package!
import code.branches.Branches.{Branch, BranchId}

import code.common.{Address, License, Location, Meta}
import code.examplething.Thing.Thing

import code.model.{BankId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Thing extends SimpleInjector {

  case class ThingId(value : String)

  trait Thing {
    def thingId : ThingId
    def name : String
    def address : Address
    def location : Location
    def foo : Foo
    def bar : Bar
    def meta : Meta
  }

  trait Foo {
   def hours : String
  }

  trait Bar {
    def hours : String
  }

  val thing1Provider = new Inject(buildOne _) {}

  def buildOne: ThingProvider = MappedThingProvider


  // Helper to get the count out of an option
  def countOfThing1(listOpt: Option[List[Thing]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait ThingProvider {

  private val logger = Logger(classOf[ThingProvider])


  /*
  Common logic for returning Things
  Implementation details in Thing provider
   */
  final def getThings(bankId : BankId) : Option[List[Thing]] = {
    getThingsFromProvider(bankId) match {
      case Some(branches) => {

        val branchesWithLicense = for {
         branch <- branches if branch.meta.license.name.size > 3
        } yield branch
        Option(branchesWithLicense)
      }
      case None => None
    }
  }

  /*
  Return one Thing
   */
  final def getThing(branchId : BranchId) : Option[Thing] = {
    // Filter out if no license data
    getThingFromProvider(branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getThingFromProvider(branchId : BranchId) : Option[Thing]
  protected def getThingsFromProvider(bank : BankId) : Option[List[Thing]]

// End of Trait
}

