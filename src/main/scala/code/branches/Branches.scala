package code.branches


/* For branches */

// Need to import these one by one because in same package!
import code.branches.Branches.{Branch, BranchId}
import code.common.{Address, License, Location, Meta}
import code.model.BankId
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Branches extends SimpleInjector {

  case class BranchId(value : String)

  object BranchId {
    def unapply(id : String) = Some(BranchId(id))
  }

  trait Branch {
    def branchId : BranchId
    def bankId : BankId
    def name : String
    def address : Address
    def location : Location
    def lobby : Lobby
    def driveUp : DriveUp
    def meta : Meta
    def branchRoutingScheme: String
    def branchRoutingAddress: String
  }

  trait Lobby {
   def hours : String
  }

  trait DriveUp {
    def hours : String
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
  final def getBranch(branchId : BranchId) : Option[Branch] = {
    // Filter out if no license data
    getBranchFromProvider(branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getBranchFromProvider(branchId : BranchId) : Option[Branch]
  protected def getBranchesFromProvider(bank : BankId) : Option[List[Branch]]

// End of Trait
}

