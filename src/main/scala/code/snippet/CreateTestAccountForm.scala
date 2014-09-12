package code.snippet

import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml
import code.model.{BankId, BankAccount}
import code.util.Helper._
import net.liftweb.common.{Empty, Full, Failure, Box}
import net.liftweb.http.js.JsCmds.SetHtml
import net.liftweb.http.js.JsCmd
import scala.xml.NodeSeq
import net.liftweb.http.js.jquery.JqJsCmds.{Show, Hide}
import code.model.dataAccess.{OBPUser, HostedBank, Account, BankAccountCreation}
import com.tesobe.model.BankAccountNumber
import net.liftweb.mongodb.BsonDSL._

object CreateTestAccountForm{

  def render = {

    var accountId = ""
    var bankId = ""
    var currency = ""
    var initialBalance = ""

    val processForm : () => JsCmd = () => {
      val createdAccount = createAccount(accountId, BankId(bankId), currency, initialBalance)
      createdAccount match {
        case Full(acc) => showSuccess(acc)
        case Failure(msg, _, _) => showError(msg)
        case _ => showError("Error. Could not create account")
      }
    }

    //TODO: would be nice to avoid tying this to the mongodb class
    val banks = HostedBank.findAll
    val bankOptions = banks.map{b =>
      val permalink = b.permalink.get
      (permalink, permalink)
    }

    "@bank-id" #> SHtml.select(bankOptions, Empty, bankId = _) &
    "@account-id" #> SHtml.text(accountId, accountId = _) &
    "@currency" #> SHtml.text("EUR", currency = _) & //Would be nice to be able to set initial value in template
    "@initial-balance" #> SHtml.text("1000.00", initialBalance = _) & //Would be nice to be able to set initial value in template
    "type=submit" #> SHtml.ajaxSubmit("Create Account", processForm) //Would be nice to be able to set initial value in template
  }

  /**
   * TODO: would be better for this to take a BankAccount (api data structure) instead of an Account (mongodb model)
   */
  def showSuccess(createdAccount : Account) : JsCmd = {
    hideErrorMessage &
    showSuccessMessage &
    SetHtml("created-account-id", <span>{createdAccount.permalink.get}</span>) &
    SetHtml("created-account-bank-id", <span>{createdAccount.bankPermalink}</span>) &
    SetHtml("created-account-initial-balance", <span>{createdAccount.balance.get}</span>) &
    SetHtml("created-account-currency", <span>{createdAccount.currency.get}</span>)
  }

  def showError(msg : String) : JsCmd = {
    hideSuccessMessage &
    showErrorMessage(msg)
  }

  private val hideErrorMessage = SetHtml("create-account-error-message", NodeSeq.Empty)
  private val showSuccessMessage = Show("account-created-successfully")
  private val hideSuccessMessage = Hide("account-created-successfully")
  private def showErrorMessage(msg : String) = SetHtml("create-account-error-message", <span>{msg}</span>)

  /**
   * Attempts to create a new account, based on form params
   * @return a box containing the created account or reason for account creation failure
   */
  def createAccount(accountId : String, bankId : BankId, currency : String, initialBalance : String) : Box[Account] =  {
    if(accountId == "") Failure("Account id cannot be empty")
    else if(bankId.value == "") Failure("Bank id cannot be empty")
    else if(currency == "") Failure("Currency cannot be empty")
    else if(initialBalance == "") Failure("Initial balance cannot be empty")
    else {
      for {
        initialBalanceAsNumber <- tryo {BigDecimal(initialBalance)} ?~! "Initial balance must be a number, e.g 1000.00"
        currentObpUser <- OBPUser.currentUser ?~! "You need to be logged in to create an account"
        user <- currentObpUser.user.obj ?~ "Server error: could not identify user"
        bank <- HostedBank.find(bankId) ?~ s"Bank $bankId not found"//Bank(bankId) ?~ s"Bank $bankId not found"
        accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty,
          s"Account with id $accountId already exists at bank $bankId")
      } yield {
        //TODO: refactor into a single private api call, and have this return Box[BankAccount] instead of Account?
        val (bankAccount,hostedAccount) = BankAccountCreation.createAccount(new BankAccountNumber {
          override val accountNumber: String = accountId
        }, bank, user)

        //set currency and initial balance
        bankAccount.currency(currency).balance(initialBalanceAsNumber).save

        BankAccountCreation.setAsOwner(bankId, accountId, hostedAccount, user)
        bankAccount
      }
    }

  }

}
