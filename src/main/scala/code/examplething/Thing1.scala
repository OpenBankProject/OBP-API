package code.examplething


// Need to import these one by one because in same package!
import code.branches.Branches.{Branch, BranchId}

import code.common.{Address, License, Location, Meta}

import code.model.{BankId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Thing1 extends SimpleInjector {

  case class Thing1Id(value : String)

  trait Thing1 {
    def branchId : Thing1Id
    def name : String
    def address : Address
    def location : Location
    def thing2 : Thing2
    def thing3 : Thing3
    def meta : Meta
  }

  trait Thing2 {
   def hours : String
  }

  trait Thing3 {
    def hours : String
  }

  val thing1Provider = new Inject(buildOne _) {}

  def buildOne: Thing1Provider = MappedThing1Provider


  // Helper to get the count out of an option
  def countOfThing1(listOpt: Option[List[Thing1]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait Thing1Provider {

  private val logger = Logger(classOf[Thing1Provider])


  /*
  Common logic for returning branches.
  Implementation details in branchesData
   */
  final def getThing1s(bankId : BankId) : Option[List[Branch]] = {
    // If we get branches filter them
    getBranchesFromProvider(bankId) match {
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
  Return one Branch
   */
  final def getThing(branchId : BranchId) : Option[Branch] = {
    // Filter out if no license data
    getBranchFromProvider(branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getBranchFromProvider(branchId : BranchId) : Option[Branch]
  protected def getBranchesFromProvider(bank : BankId) : Option[List[Branch]]

// End of Trait
}

