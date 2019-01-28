package code.api.berlin.group.v1_3

import java.text.SimpleDateFormat
import java.util.Date

import code.api.util.APIUtil
import code.api.v2_1_0.IbanJson
import code.model.{CoreAccount, ModeratedBankAccount, ModeratedTransaction}
import code.transactionrequests.TransactionRequests.TransactionRequest
import scala.collection.immutable.List
import net.liftweb.json.JValue

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_BERLIN_GROUP_1_3 {

  implicit val formats = net.liftweb.json.DefaultFormats

  trait links
  case class Balances(balances: String) extends links
  case class Transactions(trasactions: String) extends links
  case class ViewAccount(viewAccount: String) extends links
  case class AdditionalProp1(additionalProp1: String) extends links
  case class AdditionalProp2(additionalProp2: String) extends links
  case class AdditionalProp3(additionalProp3: String) extends links
  
  case class CoreAccountBalancesJson(
    balanceAmount:AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String = "closingBooked",
    lastChangeDateTime: String = "2019-01-28T06:26:52.185Z",
    referenceDate: String = "string",
    lastCommittedTransaction: String = "string",
  )
  case class CoreAccountJsonV13(
    resourceId: String,
    iban: String,
    bban: String ="BARC12345612345678",
    msisdn: String ="+49 170 1234567",
    currency: String,
    name: String,
    product: String ="string",
    cashAccountType: String,
    status: String="enabled",
    bic: String,
    linkedAccounts: String ="string",
    usage: String ="PRIV",
    details: String ="string",
    balances: CoreAccountBalancesJson = CoreAccountBalancesJson(),
    _links: List[links],
  )

  case class CoreAccountsJsonV13(accounts: List[CoreAccountJsonV13])
  
  case class AmountOfMoneyV13(
    currency : String,
    content : String
  )
  case class ClosingBookedBody(
    amount : AmountOfMoneyV13,
    date: String //eg:  “2017-10-25”, this is not a valid datetime (not java.util.Date)
  )
  case class ExpectedBody(
    amount : AmountOfMoneyV13,
    lastActionDateTime: Date
  )
  case class AccountBalance(
    closingBooked: ClosingBookedBody,
    expected: ExpectedBody
  )
  case class AccountBalances(`balances`: List[AccountBalance])
  
  case class TransactionsJsonV1(
    transactions_booked: List[TransactionJsonV1],
    transactions_pending: List[TransactionJsonV1],
    _links: List[ViewAccount]
  )
  
  case class TransactionJsonV1(
    transactionId: String,
    creditorName: String,
    creditorAccount: IbanJson,
    amount: AmountOfMoneyV13,
    bookingDate: Date,
    valueDate: Date,
    remittanceInformationUnstructured: String
  )

  def createTransactionListJSON(coreAccounts: List[CoreAccount]): CoreAccountsJsonV13 = {
    CoreAccountsJsonV13(coreAccounts.map(
      x => CoreAccountJsonV13(
        resourceId = x.id,
        iban = if (x.accountRoutings.headOption.isDefined && x.accountRoutings.head.scheme == "IBAN") x.accountRoutings.head.address else "",
        currency = "",
        name = x.label,
        status = "",
        cashAccountType = "",
        bic="",
        _links = Balances(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.id}/balances") 
          :: Transactions(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.id}/transactions") 
          :: AdditionalProp1("/v1/payments/sepa-credit-transfers/1234-wertiq-983")
          :: AdditionalProp2("/v1/payments/sepa-credit-transfers/1234-wertiq-983")
          :: AdditionalProp3("/v1/payments/sepa-credit-transfers/1234-wertiq-983")
          :: Nil
        )
       )
    )
  }

  def createAccountBalanceJSON(moderatedAccount: ModeratedBankAccount, transactionRequests: List[TransactionRequest]) = {
    // get the latest end_date of `COMPLETED` transactionRequests
    val latestCompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status == "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    //get the latest end_date of !`COMPLETED` transactionRequests
    val latestUncompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status != "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    // get the SUM of the amount of all !`COMPLETED` transactionRequests
    val sumOfAllUncompletedTransactionrequests = transactionRequests.filter(_.status != "COMPLETED").map(_.body.value.amount).map(BigDecimal(_)).sum
    // sum of the unCompletedTransactions and the account.balance is the current expectd amount:
    val sumOfAll = (BigDecimal(moderatedAccount.balance) + sumOfAllUncompletedTransactionrequests).toString()

    AccountBalances(
      AccountBalance(
        closingBooked = ClosingBookedBody(
          amount = AmountOfMoneyV13(currency = moderatedAccount.currency.getOrElse(""), content = moderatedAccount.balance),
          date = APIUtil.DateWithDayFormat.format(latestCompletedEndDate)
        ),
        expected = ExpectedBody(
          amount = AmountOfMoneyV13(currency = moderatedAccount.currency.getOrElse(""),
                                    content = sumOfAll),
          lastActionDateTime = latestUncompletedEndDate)
      ) :: Nil
    )
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV1 = {
    TransactionJsonV1(
      transactionId = transaction.id.value,
      creditorName = "",
      creditorAccount = IbanJson(APIUtil.stringOptionOrNull(transaction.bankAccount.get.iban)),
      amount = AmountOfMoneyV13(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      bookingDate = transaction.startDate.get,
      valueDate = transaction.finishDate.get,
      remittanceInformationUnstructured = APIUtil.stringOptionOrNull(transaction.description)
    )
  }
  
  def createTransactionFromRequestJSON(transactionRequest : TransactionRequest) : TransactionJsonV1 = {
    TransactionJsonV1(
      transactionId = transactionRequest.id.value,
      creditorName = transactionRequest.name,
      creditorAccount = IbanJson(transactionRequest.from.account_id),
      amount = AmountOfMoneyV13(transactionRequest.charge.value.currency, transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      valueDate = transactionRequest.end_date,
      remittanceInformationUnstructured = transactionRequest.body.description
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV1 = {
    TransactionsJsonV1(
      transactions_booked =transactions.map(createTransactionJSON),
      transactions_pending =transactionRequests.filter(_.status!="COMPLETED").map(createTransactionFromRequestJSON),
      _links = ViewAccount(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${transactionRequests.head.from.account_id}/balances")::Nil
    )
  }

}
