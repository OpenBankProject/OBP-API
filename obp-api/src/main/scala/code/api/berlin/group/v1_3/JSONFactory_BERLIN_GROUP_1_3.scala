package code.api.berlin.group.v1_3

import java.util.Date

import code.api.util.{APIUtil, CustomJsonFormats}
import code.model.ModeratedTransaction
import com.openbankproject.commons.model.{BankAccount, CoreAccount, TransactionRequest}
import net.liftweb.json.JValue

import scala.collection.immutable.List

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_BERLIN_GROUP_1_3 extends CustomJsonFormats {

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
  case class AccountBalance(
    amount : AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String = "closingBooked",
    lastChangeDateTime: String = "string",
    lastCommittedTransaction: String = "string",
    referenceDate: String = "string",
    
  )
  case class BalanceAccount(
    bban: String = "BARC12345612345678",
    currency: String =  "EUR",
    iban : String =  "FR7612345987650123456789014",
    maskedPan: String =  "123456xxxxxx1234",
    msisdn : String =  "+49 170 1234567",
    pan: String ="5409050000000000"
  )
  case class AccountBalancesV13(
    account:BalanceAccount= BalanceAccount(),
    `balances`: List[AccountBalance] = AccountBalance() ::Nil
  )
  case class TransactionsLinksV13(
    download: String =  "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp1: String =  "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp2: String =  "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp3: String =  "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  )
  case class TransactionsV13TransactionsLinks(
    account: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    first: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    next: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    previous: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    last: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp1: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp2: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp3: String ="/v1/payments/sepa-credit-transfers/1234-wertiq-983"    
  )
  case class ExchangeRateJson(
    sourceCurrency: String = "EUR",
    rate: String = "string",
    unitCurrency: String = "string",
    targetCurrency: String = "EUR",
    rateDate: String = "string",
    rateContract: String = "string"    
  )
  case class CreditorAccountJson(
    iban: String = "FR7612345987650123456789014",
    bban: String = "BARC12345612345678",
    pan: String =  "5409050000000000",
    maskedPan: String =  "123456xxxxxx1234",
    msisdn: String =  "+49 170 1234567",
    currency: String =  "EUR"
  )
  case class TransactionJsonV13Links(
    transactionDetails: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp1: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp2: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp3: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983"    
  )
  case class TransactionJsonV13(
    transactionId: String,
    entryReference: String ="string",
    endToEndId: String ="string",
    mandateId: String ="string",
    checkId: String ="string",
    creditorId: String ="string",
    bookingDate: Date,
    valueDate: Date,
    transactionAmount: AmountOfMoneyV13,
    exchangeRate: ExchangeRateJson = ExchangeRateJson(),
    creditorName: String,
    creditorAccount: CreditorAccountJson = CreditorAccountJson(),
    ultimateCreditor: String = "Ultimate Creditor",
    debtorName: String = "Debtor Name",
    debtorAccount: CreditorAccountJson = CreditorAccountJson(),
    ultimateDebtor: String = "Ultimate Debtor",
    remittanceInformationUnstructured: String= "string",
    remittanceInformationStructured: String = "string",
    purposeCode: String = "BKDF",
    bankTransactionCode: String = "PMNT-RCDT-ESCT",
    proprietaryBankTransactionCode: String = "string",
    _links: TransactionJsonV13Links = TransactionJsonV13Links(),
  )
  
  case class TransactionsV13Transactions(
    booked: List[TransactionJsonV13], 
    pending: List[TransactionJsonV13],
    _links: TransactionsV13TransactionsLinks = TransactionsV13TransactionsLinks()
  )
  case class TransactionsJsonV13Balance(
    balanceAmount :  AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String =  "closingBooked",
    lastChangeDateTime: String = "2019-01-28T13:32:26.290Z",
    referenceDate: String = "string",
    lastCommittedTransaction: String =  "string"
  )
  case class TransactionsJsonV13(
    account:BalanceAccount = BalanceAccount(),
    transactions:TransactionsV13Transactions,
    balances: List[TransactionsJsonV13Balance] = TransactionsJsonV13Balance() :: Nil,
    _links: TransactionsLinksV13 = TransactionsLinksV13()
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

  def createAccountBalanceJSON(bankAccount: BankAccount, transactionRequests: List[TransactionRequest]) = {
    // get the latest end_date of `COMPLETED` transactionRequests
    val latestCompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status == "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    //get the latest end_date of !`COMPLETED` transactionRequests
    val latestUncompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status != "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    // get the SUM of the amount of all !`COMPLETED` transactionRequests
    val sumOfAllUncompletedTransactionrequests = transactionRequests.filter(_.status != "COMPLETED").map(_.body.value.amount).map(BigDecimal(_)).sum
    // sum of the unCompletedTransactions and the account.balance is the current expectd amount:
    val sumOfAll = (bankAccount.balance+ sumOfAllUncompletedTransactionrequests).toString()

    AccountBalancesV13(
      account = BalanceAccount(
        currency = bankAccount.currency,
        iban = bankAccount.iban.getOrElse("")
      ),
      `balances` = AccountBalance(
        amount = AmountOfMoneyV13(
          currency = bankAccount.currency,
          content = bankAccount.balance.toString()
        ),
        balanceType = bankAccount.accountType,
        lastChangeDateTime = APIUtil.DateWithDayFormat.format(latestCompletedEndDate),
        lastCommittedTransaction = if(latestUncompletedEndDate ==null) null else latestUncompletedEndDate.toString
      ) :: Nil
    ) 
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV13 = {
    TransactionJsonV13(
      transactionId = transaction.id.value,
      creditorName = "",
      creditorAccount = CreditorAccountJson(iban = APIUtil.stringOptionOrNull(transaction.bankAccount.get.iban)),
      transactionAmount = AmountOfMoneyV13(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      bookingDate = transaction.startDate.get,
      valueDate = transaction.finishDate.get,
      remittanceInformationUnstructured = APIUtil.stringOptionOrNull(transaction.description)
    )
  }
  
  def createTransactionFromRequestJSON(transactionRequest : TransactionRequest) : TransactionJsonV13 = {
    TransactionJsonV13(
      transactionId = transactionRequest.id.value,
      creditorName = transactionRequest.name,
      creditorAccount = CreditorAccountJson(iban = transactionRequest.from.account_id),
      transactionAmount = AmountOfMoneyV13(transactionRequest.charge.value.currency, transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      valueDate = transactionRequest.end_date,
      remittanceInformationUnstructured = transactionRequest.body.description
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV13 = {
    TransactionsJsonV13(
      transactions= TransactionsV13Transactions(
        booked= transactions.map(createTransactionJSON),
        pending = transactionRequests.filter(_.status!="COMPLETED").map(createTransactionFromRequestJSON)
      )
    )
  }

}
