package code.payments

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import net.liftweb.mongodb.BsonDSL._
import code.bankconnectors.Connector
import code.model.{AccountId, BankId, BankAccount, TransactionId}
import code.model.dataAccess.{OBPEnvelope, Account}
import net.liftweb.common.{Loggable, Full, Failure, Box}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers._

//Works with connectors where AccountType = Account
trait SandboxLocalConnectorPayments extends PaymentProcessor {

  self : Connector with Loggable =>

  protected def makePaymentImpl(fromAccount : Account, toAccount : Account, amt : BigDecimal) : Box[TransactionId] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //this is the transaction that gets attached to the account of the person making the payment
    val createdFromTrans = createTransaction(fromAccount, toAccount, fromTransAmt)

    // this creates the transaction that gets attached to the account of the person receiving the payment
    createTransaction(toAccount, fromAccount, toTransAmt)

    //assumes OBPEnvelope id is what gets used as the Transaction id in the API. If that gets changed, this needs to
    //be updated (the tests should fail if it doesn't)
    createdFromTrans.map(t => TransactionId(t.transactionId.get))
  }

  private def createTransaction(account : Account, otherAccount : Account, amount : BigDecimal) : Box[OBPEnvelope] = {

    val oldBalance = account.balance

    for {
      otherBank <- Connector.connector.vend.getBank(otherAccount.bankId) ?~! "no other bank found"
      transTime = now
      //mongodb/the lift mongo thing wants a literal Z in the timestamp, apparently
      envJsonDateFormat = {
        val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
        simpleDateFormat
      }

      envJson =
      ("obp_transaction" ->
        ("this_account" ->
          ("holder" -> account.owners.headOption.map(_.name).getOrElse("")) ~ //TODO: this is rather fragile...
            ("number" -> account.number) ~
            ("kind" -> account.accountType) ~
            ("bank" ->
              ("IBAN" -> account.iban.getOrElse("")) ~
                ("national_identifier" -> account.nationalIdentifier) ~
                ("name" -> account.bankId.value))) ~
          ("other_account" ->
            ("holder" -> otherAccount.accountHolder) ~
              ("number" -> otherAccount.number) ~
              ("kind" -> otherAccount.accountType) ~
              ("bank" ->
                ("IBAN" -> "") ~
                  ("national_identifier" -> otherBank.nationalIdentifier) ~
                  ("name" -> otherBank.fullName))) ~
          ("details" ->
            ("type_en" -> "") ~
              ("type_de" -> "") ~
              ("posted" ->
                ("$dt" -> envJsonDateFormat.format(transTime))
                ) ~
              ("completed" ->
                ("$dt" -> envJsonDateFormat.format(transTime))
                ) ~
              ("new_balance" ->
                ("currency" -> account.currency) ~
                  ("amount" -> (oldBalance + amount).toString)) ~
              ("value" ->
                ("currency" -> account.currency) ~
                  ("amount" -> amount.toString))))
      saved <- saveTransaction(envJson, account)
    } yield {
      saved
    }
  }

  def saveTransaction(transactionJS : JValue, thisAccount : Account) : Box[OBPEnvelope] = {

    val envelope: Box[OBPEnvelope] = OBPEnvelope.envlopesFromJvalue(transactionJS)

    if(envelope.isDefined) {
      val e : OBPEnvelope = envelope.get
      logger.debug(s"Updating current balance for ${thisAccount.bankName} / ${thisAccount.accountNumber} / ${thisAccount.accountType}")
      thisAccount.accountBalance(e.obp_transaction.get.details.get.new_balance.get.amount.get).save
      logger.debug("Saving new transaction")
      Full(e.save)
    } else {
      Failure("couldn't save transaction")
    }
  }

}
