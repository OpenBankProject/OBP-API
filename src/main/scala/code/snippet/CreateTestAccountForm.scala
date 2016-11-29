package code.snippet

import code.bankconnectors.Connector
import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml
import code.model.{Bank, AccountId, BankId, BankAccount}
import code.util.Helper._
import net.liftweb.common.{Empty, Full, Failure, Box}
import net.liftweb.http.js.JsCmds.SetHtml
import net.liftweb.http.js.JsCmd
import scala.xml.NodeSeq
import net.liftweb.http.js.jquery.JqJsCmds.{Show, Hide}
import code.model.dataAccess.{OBPUser, BankAccountCreation}

object CreateTestAccountForm{

  def render = {

    var accountId = ""
    var bankId = ""
    var currency = ""
    var initialBalance = ""
    var accountType = ""
    var accountLabel = ""

    val processForm : () => JsCmd = () => {
      val createdAccount = createAccount(AccountId(accountId), BankId(bankId), accountType, accountLabel, currency, initialBalance)
      createdAccount match {
        case Full(acc) => showSuccess(acc)
        case Failure(msg, _, _) => showError(msg)
        case _ => showError("Error. Could not create account")
      }
    }

    val banks = Connector.connector.vend.getBanks
    val bankOptions = banks.map{b =>
      val id = b.bankId.value
      (id, id)
    }

    "@bank-id" #> SHtml.select(bankOptions, Empty, bankId = _) &
    "@account-id" #> SHtml.text(accountId, accountId = _) &
    "@currency" #> SHtml.text("EUR", currency = _) & //Would be nice to be able to set initial value in template
    "@initial-balance" #> SHtml.text("1000.00", initialBalance = _) & //Would be nice to be able to set initial value in template
    "type=submit" #> SHtml.ajaxSubmit("Create Account", processForm) //Would be nice to be able to set initial value in template
  }

  def showSuccess(createdAccount : BankAccount) : JsCmd = {
    hideErrorMessage &
    showSuccessMessage &
    SetHtml("created-account-id", <span>{createdAccount.accountId.value}</span>) &
    SetHtml("created-account-bank-id", <span>{createdAccount.bankId.value}</span>) &
    SetHtml("created-account-initial-balance", <span>{createdAccount.balance}</span>) &
    SetHtml("created-account-currency", <span>{createdAccount.currency}</span>)
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
  def createAccount(accountId : AccountId, bankId : BankId, accountType: String, accountLabel: String, currency : String, initialBalance : String) : Box[BankAccount] =  {

    val currencies = code.fx.fx.exchangeRates.keys.toList

    if(accountId.value == "") Failure("Account id cannot be empty")
    else if(bankId.value == "") Failure("Bank id cannot be empty")
    else if(currency == "") Failure("Currency cannot be empty")
    else if(initialBalance == "") Failure("Initial balance cannot be empty")
    else if(!currencies.exists(_ == currency)) Failure("Allowed currencies are: " + currencies.mkString(", "))
    else {
      for {
        initialBalanceAsNumber <- tryo {BigDecimal(initialBalance)} ?~! "Initial balance must be a number, e.g 1000.00"
        currentObpUser <- OBPUser.currentUser ?~! "You need to be logged in to create an account"
        user <- currentObpUser.user.obj ?~ "Server error: could not identify user"
        bank <- Bank(bankId) ?~ s"Bank $bankId not found"
        accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty,
          s"Account with id $accountId already exists at bank $bankId")
        bankAccount <- Connector.connector.vend.createSandboxBankAccount(bankId, accountId, accountType, accountLabel, currency, initialBalanceAsNumber, user.name)
      } yield {
        BankAccountCreation.setAsOwner(bankId, accountId, user)
        bankAccount
      }
    }

  }

}
