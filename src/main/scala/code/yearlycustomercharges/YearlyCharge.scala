package code.yearlycustomercharges

import code.api.util.APIUtil
import code.model.{BankId, CustomerId}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object YearlyCharge extends SimpleInjector {

    val yearlyChargeProvider = new Inject(buildOne _) {}


  // This determines the provider we use
  def buildOne: YearlyChargeProvider =
    APIUtil.getPropsValue("provider.thing").openOr("mapped") match {
      case "mapped" => MappedYearlyChargeProvider
      case _ => MappedYearlyChargeProvider
    }

}

case class YearlyChargeId(value : String)

// WIP
trait YearlyCharge {
  def year : Int

  //  def customerNumber : String
  //
  //
  //  def categoryId : String
  //  def forcastIndictor : String
  //  def typeId : String
  //  def natureId : String
  //  def charge : AmountOfMoney
  //  def updateDate : Date



}




trait YearlyChargeProvider {

  private val logger = Logger(classOf[YearlyChargeProvider])


  /*
  Common logic for returning or changing Things
  Datasource implementation details are in Thing provider
   */
  final def getYearlyCharges(bankId : BankId, customerId: CustomerId, year: Int) : Option[List[YearlyCharge]] = {
    getYearlyChargesFromProvider(bankId, customerId, year) match {
      case Some(things) => {

        val certainThings = for {
         thing <- things // if filter etc. if need be
        } yield thing
        Option(certainThings)
      }
      case None => None
    }
  }


  protected def getYearlyChargesFromProvider(bank : BankId, customerId: CustomerId, year: Int) : Option[List[YearlyCharge]]

}

