package code.atms

/* For atms */

// Need to import these one by one because in same package!

import code.atms.Atms.{Atm, AtmId}
import code.model.BankId
import code.common._
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Atms extends SimpleInjector {

  case class AtmId(value : String)

  object AtmId {
    def unapply(id : String) = Some(AtmId(id))
  }

  trait Atm {
    def atmId : AtmId
    def bankId : BankId
    def name : String
    def address : AddressT
    def location : LocationT
    def meta : MetaT

    def  OpeningTimeOnMonday : String
    def  ClosingTimeOnMonday : String

    def  OpeningTimeOnTuesday : String
    def  ClosingTimeOnTuesday : String

    def  OpeningTimeOnWednesday : String
    def  ClosingTimeOnWednesday : String

    def  OpeningTimeOnThursday : String
    def  ClosingTimeOnThursday: String

    def  OpeningTimeOnFriday : String
    def  ClosingTimeOnFriday : String

    def  OpeningTimeOnSaturday : String
    def  ClosingTimeOnSaturday : String

    def  OpeningTimeOnSunday: String
    def  ClosingTimeOnSunday : String

    def  isAccessible : String

    def  branchType : String
    def  moreInfo : String


  }

  val atmsProvider = new Inject(buildOne _) {}

  def buildOne: AtmsProvider = MappedAtmsProvider

  // Helper to get the count out of an option
  def countOfAtms (listOpt: Option[List[Atm]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait AtmsProvider {

  private val logger = Logger(classOf[AtmsProvider])


  /*
  Common logic for returning atms.
   */
  final def getAtms(bankId : BankId) : Option[List[Atm]] = {
    // If we get atms filter them
    getAtmsFromProvider(bankId) match {
      case Some(atms) => {

        val atmsWithLicense = for {
         branch <- atms if branch.meta.license.name.size > 3 && branch.meta.license.name.size > 3
        } yield branch
        Option(atmsWithLicense)
      }
      case None => None
    }
  }

  /*
  Return one Atm
   */
  final def getAtm(branchId : AtmId) : Option[Atm] = {
    // Filter out if no license data
    getAtmFromProvider(branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getAtmFromProvider(branchId : AtmId) : Option[Atm]
  protected def getAtmsFromProvider(bank : BankId) : Option[List[Atm]]

// End of Trait
}






