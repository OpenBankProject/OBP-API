package code.bankconnectors

import code.api.util.APIUtil.InboundMessageBase
import code.model.{AccountId, BankAccount, BankId}
import code.model.dataAccess.MappedBankAccountData
import java.text.SimpleDateFormat
import java.util.{Date, Locale}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

case class InboundAccountJune2017(
                                   errorCode: String,
                                   bankId: String,
                                   branchId: String,
                                   accountId: String,
                                   accountNumber: String,
                                   accountType: String,
                                   balanceAmount: String,
                                   balanceCurrency: String,
                                   owners: List[String],
                                   viewsToGenerate: List[String],
                                   bankRoutingScheme:String,
                                   bankRoutingAddress:String,
                                   branchRoutingScheme:String,
                                   branchRoutingAddress:String,
                                   accountRoutingScheme:String,
                                   accountRoutingAddress:String
                                 ) extends InboundMessageBase with InboundAccountCommon

case class BankAccountJune2017(r: InboundAccountJune2017) extends BankAccount {

  def accountId: AccountId = AccountId(r.accountId)
  def accountType: String = r.accountType
  def balance: BigDecimal = BigDecimal(r.balanceAmount)
  def currency: String = r.balanceCurrency
  def name: String = r.owners.head
  // Note: swift_bic--> swiftBic, but it extends from BankAccount
  def swift_bic: Option[String] = Some("swift_bic")
  // Note: deprecated, extends from BankAccount
  def iban: Option[String] = Some("iban")
  def number: String = r.accountNumber
  def bankId: BankId = BankId(r.bankId)
  def lastUpdate: Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(today.getTime.toString)
  def accountHolder: String = r.owners.head

  // Fields modifiable from OBP are stored in mapper
  def label: String = (for {
    d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
  } yield {
    d.getLabel
  }).getOrElse(r.accountNumber)

  def accountRoutingScheme: String = r.accountRoutingScheme
  def accountRoutingAddress: String = r.accountRoutingAddress
  def branchId: String = r.branchId

}