package code.standingorders

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import com.openbankproject.commons.model.StandingOrderTrait
import scala.math.BigDecimal


object StandingOrders extends SimpleInjector {
  val provider = new Inject(() => buildOne) {}
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

