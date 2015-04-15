package code.bankbranches

import code.bankbranches.BankBranches.{BankBranch, DataLicense, BranchData}
import code.model.{BranchId, BankId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object BankBranches extends SimpleInjector {

  case class BankBranchId(value : String)
  case class BranchData(branches : List[BankBranch], license : DataLicense)

  trait DataLicense {
    def name : String
    def url : String
  }

  trait BankBranch {
    def branchId : BankBranchId
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

  val bankBranchesProvider = new Inject(buildOne _) {}

  def buildOne: BankBranchesProvider = MappedBankBranchesProvider

}

trait BankBranchesProvider {

  private val logger = Logger(classOf[BankBranchesProvider])

  final def getBranches(bank : BankId) : Option[BranchData] = {
    branchDataLicense(bank) match {
      case Some(license) =>
        Some(BranchData(branchData(bank), license))
      case None => {
        logger.info(s"No branch data license found for bank ${bank.value}")
        None
      }
    }
  }

  // TODO work in progress. Add singular BranchData
  final def getBranch(bank : BankId, branch : BranchId) : Option[BranchData] = {
    branchDataLicense(bank) match {
      case Some(license) =>
        Some(BranchData(branchData(bank), license))
      case None => {
        logger.info(s"No branch data license found for bank ${bank.value}")
        None
      }
    }
  }







  protected def branchData(bank : BankId) : List[BankBranch]
  protected def branchDataLicense(bank : BankId) : Option[DataLicense]
}


