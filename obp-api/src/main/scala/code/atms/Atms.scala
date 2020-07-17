package code.atms

/* For atms */

// Need to import these one by one because in same package!

import code.api.util.OBPQueryParam
import com.openbankproject.commons.model._
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Atms extends SimpleInjector {

  case class Atm (
    atmId : AtmId,
    bankId : BankId,
    name : String,
    address : Address,
    location : Location,
    meta : Meta,

    OpeningTimeOnMonday : Option[String],
    ClosingTimeOnMonday : Option[String],

    OpeningTimeOnTuesday : Option[String],
    ClosingTimeOnTuesday : Option[String],

    OpeningTimeOnWednesday : Option[String],
    ClosingTimeOnWednesday : Option[String],

    OpeningTimeOnThursday : Option[String],
    ClosingTimeOnThursday: Option[String],

    OpeningTimeOnFriday : Option[String],
    ClosingTimeOnFriday : Option[String],

    OpeningTimeOnSaturday : Option[String],
    ClosingTimeOnSaturday : Option[String],

    OpeningTimeOnSunday: Option[String],
    ClosingTimeOnSunday : Option[String],

    isAccessible : Option[Boolean],

    locatedAt : Option[String],
    moreInfo : Option[String],
    hasDepositCapability : Option[Boolean]
  ) extends AtmT

  val atmsProvider = new Inject(buildOne _) {}

  def buildOne: AtmsProvider = MappedAtmsProvider

  // Helper to get the count out of an option
  def countOfAtms (listOpt: Option[List[AtmT]]) : Int = {
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
  final def getAtms(bankId : BankId, queryParams: List[OBPQueryParam]) : Option[List[AtmT]] = {
    // If we get atms filter them
    getAtmsFromProvider(bankId,queryParams) match {
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
  final def getAtm(bankId: BankId, branchId : AtmId) : Option[AtmT] = {
    // Filter out if no license data
    getAtmFromProvider(bankId,branchId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getAtmFromProvider(bankId: BankId, branchId : AtmId) : Option[AtmT]
  protected def getAtmsFromProvider(bank : BankId, queryParams: List[OBPQueryParam]) : Option[List[AtmT]]

// End of Trait
}






