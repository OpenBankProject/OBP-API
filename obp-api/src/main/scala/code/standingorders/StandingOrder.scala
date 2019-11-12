package code.standingorders

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.math.BigDecimal


object StandingOrders extends SimpleInjector {
  val provider = new Inject(buildOne _) {}
  def buildOne: StandingOrderProvider = MappedStandingOrderProvider
}

trait StandingOrderProvider {
  def createStandingOrder(bankId: String,
                          accountId: String,
                          customerId: String,
                          userId: String,
                          counterpartyId: String,
                          amountValue: BigDecimal,
                          amountCurrency: String,
                          whenFrequency: String,
                          whenDetail: String,
                          dateSigned: Date,
                          dateStarts: Date,
                          dateExpires: Option[Date]
                       ): Box[StandingOrderTrait]
  def getStandingOrdersByCustomer(customerId: String) : List[StandingOrderTrait]
  def getStandingOrdersByUser(userId: String) : List[StandingOrderTrait]
}

trait StandingOrderTrait {
  def standingOrderId: String
  def bankId: String
  def accountId: String
  def customerId: String
  def userId: String
  def counterpartyId: String
  def amountValue : BigDecimal
  def amountCurrency: String
  def whenFrequency: String
  def whenDetail: String
  def dateSigned: Date
  def dateCancelled: Date
  def dateStarts: Date
  def dateExpires: Date
  def active: Boolean
}