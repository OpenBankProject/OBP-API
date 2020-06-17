package code.directdebit

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import com.openbankproject.commons.model.DirectDebitTrait


object DirectDebits extends SimpleInjector {
  val directDebitProvider = new Inject(buildOne _) {}
  def buildOne: DirectDebitProvider = MappedDirectDebitProvider
}

trait DirectDebitProvider {
  def createDirectDebit(bankId: String, 
                        accountId: String,
                        customerId: String,
                        userId: String,
                        counterpartyId: String,
                        dateSigned: Date,
                        dateStarts: Date,
                        dateExpires: Option[Date]
                       ): Box[DirectDebitTrait]
  def getDirectDebitsByCustomer(customerId: String) : List[DirectDebitTrait]
  def getDirectDebitsByUser(userId: String) : List[DirectDebitTrait]
}
