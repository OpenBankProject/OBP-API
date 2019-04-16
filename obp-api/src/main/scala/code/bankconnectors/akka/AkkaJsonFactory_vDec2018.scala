package code.bankconnectors.akka

import java.lang
import java.util.Date

import code.api.util.APIUtil
import code.model.dataAccess.MappedBankAccountData
import com.openbankproject.commons.dto.{InboundAccount, InboundCustomer, InboundTransaction}
import com.openbankproject.commons.model._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

import scala.collection.immutable.List
import scala.math.BigDecimal

object InboundTransformerDec2018 {
 /*
   * Customer
   */
  case class AkkaDec2018Customer(
                                  customerId: String,
                                  bankId: String,
                                  number: String,
                                  legalName: String,
                                  mobileNumber: String,
                                  email: String,
                                  faceImage: CustomerFaceImage,
                                  dateOfBirth: Date,
                                  relationshipStatus: String,
                                  dependents: Integer,
                                  dobOfDependents: List[Date],
                                  highestEducationAttained: String,
                                  employmentStatus: String,
                                  creditRating: CreditRating,
                                  creditLimit: CreditLimit,
                                  kycStatus: lang.Boolean,
                                  lastOkDate: Date,
                                  title: String = "", //These new fields for V310, not from Connector for now. 
                                  branchId: String = "", //These new fields for V310, not from Connector for now. 
                                  nameSuffix: String = "", //These new fields for V310, not from Connector for now. 
                                ) extends Customer

  def toCustomer(customer: InboundCustomer): Customer = {
    AkkaDec2018Customer(
      customerId = customer.customerId,
      bankId = customer.bankId,
      number = customer.number,
      legalName = customer.legalName,
      mobileNumber = customer.mobileNumber,
      email = customer.email,
      faceImage = customer.faceImage,
      dateOfBirth = customer.dateOfBirth,
      relationshipStatus = customer.relationshipStatus,
      dependents = customer.dependents,
      dobOfDependents = customer.dobOfDependents,
      highestEducationAttained = customer.highestEducationAttained,
      employmentStatus = customer.employmentStatus,
      creditRating = customer.creditRating,
      creditLimit = customer.creditLimit,
      kycStatus = customer.kycStatus,
      lastOkDate = customer.lastOkDate,
      title = "", 
      branchId = "", 
      nameSuffix = "",
    )
  }

  def toCustomers(customers: List[InboundCustomer]): List[Customer] = {
    customers.map(toCustomer)
  }

  /*
   * Transaction
   */
  def toTransaction(t: InboundTransaction): Transaction = {
    new Transaction(
      uuid = t.uuid,
      id = t.id,
      thisAccount = t.thisAccount,
      otherAccount = t.otherAccount,
      transactionType = t.transactionType,
      amount = t.amount,
      currency = t.currency,
      description = t.description,
      startDate = t.startDate,
      finishDate = t.finishDate,
      balance = t.balance
    )
  }

  def toTransactions(t: List[InboundTransaction]): List[Transaction] = {
    t.map(toTransaction)
  }

  /*
   * Bank
   */
  


  /*
   * Account
   */
  case class BankAccountDec2018(a: InboundAccount) extends BankAccount {
    override def accountId: AccountId = AccountId(a.accountId)

    override def accountType: String = a.accountType

    override def balance: BigDecimal = BigDecimal(a.balanceAmount)

    override def currency: String = a.balanceCurrency

    override def name: String = a.owners.head

    override def swift_bic: Option[String] = Some("swift_bic")

    override def iban: Option[String] = Some("iban")

    override def number: String = a.accountNumber

    override def bankId: BankId = BankId(a.bankId)

    override def lastUpdate: Date = APIUtil.DateWithMsFormat.parse(today.getTime.toString)

    override def accountHolder: String = a.owners.head

    override
    def label: String = (for {
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, a.accountId))
    } yield {
      d.getLabel
    }).getOrElse(a.accountNumber)

    override def accountRoutingScheme: String = a.accountRoutingScheme

    override def accountRoutingAddress: String = a.accountRoutingAddress

    override def accountRoutings: List[AccountRouting] = List()

    override def branchId: String = a.branchId

    override def accountRules: List[AccountRule] = a.accountRules
  }

  def toAccount(account: InboundAccount): BankAccount = BankAccountDec2018(account)


}