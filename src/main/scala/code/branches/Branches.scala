package code.branches

// Need to import these one by one because in same package!
import code.branches.Branches.{Branch, BranchId}

import code.model.{BankId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Branches extends SimpleInjector {

  case class BranchId(value : String)

  trait License {
    def id : String
    def name : String
  }

  trait Meta {
    def license : License
  }


  trait Address {
    def line1 : String
    def line2 : String
    def line3 : String
    def city : String
    def county : String
    def state : String
    def postCode : String
    //ISO_3166-1_alpha-2
    def countryCode : String
  }

  trait Branch {
    def branchId : BranchId
    def name : String
    def address : Address
    def location : Location
    def meta : Meta
    def lobby : Lobby
    def driveUp : DriveUp
  }


  trait Lobby {
   def hours : String
  }

  trait DriveUp {
    def hours : String
  }


  trait Location {
    def latitude: Double
    def longitude: Double
  }


  val branchesProvider = new Inject(buildOne _) {}

  def buildOne: BranchesProvider = MappedBranchesProvider


  // Helper to get the count out of an option
  def countOfBranches (listOpt: Option[List[Branch]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait BranchesProvider {

  private val logger = Logger(classOf[BranchesProvider])


  /*
  Common logic for returning branches.
  Implementation details in branchesData
   */
  final def getBranches(bankId : BankId) : Option[List[Branch]] = {
    // If we get branches filter them
    getBranchesFromProvider(bankId) match {
      case Some(branches) => {

        val branchesWithLicense = for {
         branch <- branches if branch.meta.license.name.size > 3 && branch.meta.license.name.size > 3
        } yield branch

        //Option(b.filter(x => x.meta.license.name != "" && x.meta.license.url != ""))
        Option(branchesWithLicense)
      }
      case None => None
    }
  }

  /*
  Return one Branch
   */
  final def getBranch(branchId : BranchId) : Option[Branch] = {
    // Filter out if no license data
    getBranchFromProvider(branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getBranchFromProvider(branchId : BranchId) : Option[Branch]
  protected def getBranchesFromProvider(bank : BankId) : Option[List[Branch]]

// End of Trait
}


