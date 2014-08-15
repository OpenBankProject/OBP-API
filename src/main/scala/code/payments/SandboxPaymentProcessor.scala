package code.payments

import code.bankconnectors.LocalConnector
import code.model.BankAccount
import net.liftweb.common.{Loggable, Full, Failure, Box}
import net.liftweb.util.Helpers._
import code.model.dataAccess.Account
import code.model.dataAccess.HostedBank
import java.text.SimpleDateFormat
import net.liftweb.json.JsonAST.JValue
import java.util.{TimeZone, Date}
import code.model.dataAccess.OBPEnvelope
import net.liftweb.mongodb.BsonDSL._
import code.model.operations._

/**
 * This payment processor only works with the current MongoDB implemented transactions
 *  (e.g. OBPEnvelope). When that is split out into its own library, this implementation should
 *  go with it as it is fundamentally linked to it.
 */
object SandboxPaymentProcessor extends PaymentProcessor with Loggable {

  /**
   * WARNING!!! There is no check currently being done that the new transaction + update balance
   *  of the account receiving the money were properly saved. This payment implementation is for
   *  demo/sandbox/test purposes only!
   *
   *  I have not bothered to spend time doing anything about this. I see no point in trying to
   *  implement ACID transactions in mongodb for a test sandbox.
   */
  def makePayment(fromAccount : BankAccount, toAccount : BankAccount, amt : BigDecimal) : PaymentOperation = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //this is the transaction that gets attached to the account of the person making the payment
    val transactionResult = createTransaction(fromAccount, toAccount.bankPermalink,
      toAccount.permalink, fromTransAmt)

    // this creates the transaction that gets attached to the account of the person receiving the payment
    createTransaction(toAccount, fromAccount.bankPermalink, fromAccount.permalink, toTransAmt)

    val operationTime = now

    def unspecifiedFailure = new FailedPayment(
      operationId = "",
      failureMessage = "server error",
      startDate = operationTime,
      finishDate = operationTime
    )

    transactionResult match {
      case Full((obpEnv, thisMongoAcc)) => {
        //dependency on LocalConnector here, but this whole sandbox processor is dependent on specific implemenations anyways
        val transaction = LocalConnector.createTransaction(obpEnv, thisMongoAcc)

        transaction match {
          case Some(t) => {
            new CompletedPayment(
              operationId = "",
              transaction = t,
              startDate = operationTime,
              finishDate = operationTime
            )
          }
          case _ => unspecifiedFailure
        }
      }
      case Failure(msg, _ , _) => {
        new FailedPayment(
          operationId = "",
          failureMessage = msg,
          startDate = operationTime,
          finishDate = operationTime
        )
      }
      case _ => {
        unspecifiedFailure
      }
    }


  }

  // also returns the mongodb Account object for the account initiating the payment
  private def createTransaction(account : BankAccount, otherBankId : String,
                        otherAccountId : String, amount : BigDecimal) : Box[(OBPEnvelope, Account)] = {

    val oldBalance = account.balance

    for {
      otherBank <- HostedBank.find("permalink" -> otherBankId) ?~! "no other bank found"
      //yeah dumb, but blame the bad mongodb structure that attempts to use foreign keys
      otherAccs = Account.findAll(("permalink" -> otherAccountId))
      otherAcc <- Box(otherAccs.filter(_.bankPermalink == otherBank.permalink.get).headOption) ?~! s"no other acc found. ${otherAccs.size} searched for matching bank ${otherBank.id.get.toString} :: ${otherAccs.map(_.toString)}"
      transTime = now
      thisAccs = Account.findAll(("permalink" -> account.permalink))
      thisAcc <- Box(thisAccs.filter(_.bankPermalink == account.bankPermalink).headOption) ?~! s"no this acc found. ${thisAccs.size} searched for matching bank ${account.bankPermalink}?"
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
            ("kind" -> thisAcc.kind.get) ~
            ("bank" ->
              ("IBAN" -> account.iban.getOrElse("")) ~
                ("national_identifier" -> account.nationalIdentifier) ~
                ("name" -> account.bankPermalink))) ~
          ("other_account" ->
            ("holder" -> otherAcc.holder.get) ~
              ("number" -> otherAcc.number.get) ~
              ("kind" -> otherAcc.kind.get) ~
              ("bank" ->
                ("IBAN" -> "") ~
                  ("national_identifier" -> otherBank.national_identifier.get) ~
                  ("name" -> otherBank.name.get))) ~
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
      saved <- saveTransaction(envJson)
    } yield {
      (saved, thisAcc)
    }
  }

  def saveTransaction(transactionJS : JValue) : Box[OBPEnvelope] = {

    val envelope: Box[OBPEnvelope] = OBPEnvelope.envlopesFromJvalue(transactionJS)

    if(envelope.isDefined) {
      val e : OBPEnvelope = envelope.get
      val accountNumber = e.obp_transaction.get.this_account.get.number.get
      val bankName = e.obp_transaction.get.this_account.get.bank.get.name.get
      val accountKind = e.obp_transaction.get.this_account.get.kind.get
      val holder = e.obp_transaction.get.this_account.get.holder.get
      //Get all accounts with this account number and kind
      import code.model.dataAccess.Account
      //TODO: would be nicer to incorporate the bank into the query here but I'm not sure it's possible
      //with the reference to the bank document
      val accounts = Account.findAll(("number" -> accountNumber) ~ ("kind" -> accountKind) ~ ("holder" -> holder))
      //Now get the one that actually belongs to the right bank
      val findFunc = (x : Account) => {
        x.bankPermalink == bankName
      }
      val wantedAccount = accounts.find(findFunc)
      wantedAccount match {
        case Some(account) => {
          def updateAccountBalance() = {
            logger.debug("Updating current balance for " + bankName + "/" + accountNumber + "/" + accountKind)
            account.balance(e.obp_transaction.get.details.get.new_balance.get.amount.get).save
            logger.debug("Saving new transaction")
            val metadataCreated = e.createMetadataReference
            if(metadataCreated.isDefined) e.save
            else Failure("Server error, problem creating transaction metadata")
          }
          account.lastUpdate(new Date)
          updateAccountBalance()
          Full(e)
        }
        case _ => Failure("couldn't save transaction: no account balance to update")
      }
    } else {
      Failure("couldn't save transaction")
    }
  }

}
