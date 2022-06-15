package code.atms

/* For atms */

// Need to import these one by one because in same package!

import code.api.util.OBPQueryParam
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

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
    hasDepositCapability : Option[Boolean],
    supportedLanguages : Option[List[String]] = None,
    services: Option[List[String]] = None,
    accessibilityFeatures: Option[List[String]] = None,
    supportedCurrencies: Option[List[String]] = None,
    notes: Option[List[String]] = None,
    locationCategories: Option[List[String]] = None,
    minimumWithdrawal: Option[String] = None,
    branchIdentification: Option[String] = None,
    siteIdentification: Option[String] = None,
    siteName: Option[String] = None,
    cashWithdrawalNationalFee: Option[String] = None,
    cashWithdrawalInternationalFee: Option[String] = None,
    balanceInquiryFee: Option[String] = None,
    
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
    getAtmsFromProvider(bankId,queryParams)
  }

  /*
  Return one Atm
   */
  final def getAtm(bankId: BankId, branchId : AtmId) : Option[AtmT] = {
    getAtmFromProvider(bankId,branchId)
  }

  protected def getAtmFromProvider(bankId: BankId, branchId : AtmId) : Option[AtmT]
  protected def getAtmsFromProvider(bank : BankId, queryParams: List[OBPQueryParam]) : Option[List[AtmT]]
  def createOrUpdateAtm(atm: AtmT): Box[AtmT]
  def deleteAtm(atm: AtmT): Box[Boolean]
// End of Trait
}






