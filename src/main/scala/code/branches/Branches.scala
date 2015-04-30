package code.branches

// Need to import these one by one because in same package!
import code.branches.Branches.{Branch, BranchId}

import code.model.{BankId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

import scala.util.Try

object Branches extends SimpleInjector {

  case class BranchId(value : String)

  trait License {
    def name : String = "simon says"
    def url : String  = "www.google.com"
  }

  trait Meta {
    def license : License = new License {} // Note: {} used to instantiate an anonymous class of the License trait
  }

  trait Address {
    def line1 : String
    def line2 : String
    def line3 : String
    def line4 : String
    def line5 : String
    def postCode : String
    //ISO_3166-1_alpha-2
    def countryCode : String
  }

  trait Branch {
    def branchId : BranchId
    def name : String
    //def address : Address
    //def meta : Meta = new Meta {} // Note: {} used to instantiate an anonymous class of the Meta trait
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
        getBranchesFromProvider(bankId)
  }

  /*

  Return one Branch
  Needs bankId so we can get the licence related to the bank (BranchId should be unique anyway)
   */
  final def getBranch(branchId : BranchId) : Option[Branch] = {
//    // Only return the data if we have a license!
//    branchDataLicense(bankId) match {
//      case Some(license) =>
        getBranchFromProvider(branchId)
//      case None => {
//        logger.warn(s"getBranch says: No branch data license found for bank ${bankId.value}")
//        None
//      }

  }

  protected def getBranchFromProvider(branchId : BranchId) : Option[Branch]
  protected def getBranchesFromProvider(bank : BankId) : Option[List[Branch]]

// End of Trait
}


