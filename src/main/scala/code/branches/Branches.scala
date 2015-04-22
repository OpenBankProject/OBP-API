package code.branches

// Need to import these one by one because in same package!
import code.branches.Branches.{Branch, DataLicense, BranchesData, BranchData, BranchId}

import code.model.{BankId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Branches extends SimpleInjector {

  case class BranchId(value : String)
  case class BranchesData(branches : List[Branch], license : DataLicense)
  case class BranchData(branch : Option[Branch], license : DataLicense)

  trait DataLicense {
    def name : String
    def url : String
  }

  trait Branch {
    def branchId : BranchId
    def name : String
    def address : Address
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

  val branchesProvider = new Inject(buildOne _) {}

  def buildOne: BranchesProvider = MappedBranchesProvider

}

trait BranchesProvider {

  private val logger = Logger(classOf[BranchesProvider])


  /*
  Common logic for returning branches.
  Implementation details in branchesData

   */

  final def getBranches(bankId : BankId) : Option[BranchesData] = {
    branchDataLicense(bankId) match {
      case Some(license) =>
        Some(BranchesData(branchesData(bankId), license))
      case None => {
        logger.warn(s"getBranches says: No branch data license found for bank ${bankId.value}")
        None
      }
    }
  }

  /*

  Return one Branch
  Needs bankId so we can get the licence related to the bank (BranchId should be unique anyway)
   */
  final def getBranch(bankId: BankId, branchId : BranchId) : Option[BranchData] = {
    // Only return the data if we have a license!
    branchDataLicense(bankId) match {
      case Some(license) =>
        Some(BranchData(branchData(branchId), license))
      case None => {
        logger.warn(s"getBranch says: No branch data license found for bank ${bankId.value}")
        None
      }
    }
  }

  protected def branchData(branchId : BranchId) : Option[Branch]
  protected def branchesData(bank : BankId) : List[Branch]
  protected def branchDataLicense(bank : BankId) : Option[DataLicense]


// End of Trait
}


