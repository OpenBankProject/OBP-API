package code.snippet

import code.bankconnectors.Connector
import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml
import code.model.{BankX, BankAccountX}
import code.util.Helper._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.http.js.JsCmds.SetHtml
import net.liftweb.http.js.JsCmd
import code.api.util.ErrorMessages._
import code.fx.fx

import scala.xml.NodeSeq
import net.liftweb.http.js.jquery.JqJsCmds.{Hide, Show}
import code.model.dataAccess.{AuthUser, BankAccountCreation}
import code.users.Users
import com.openbankproject.commons.model.{AccountId, BankAccount, BankId}

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

    val banks = Connector.connector.vend.getBanksLegacy(None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox)
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
    SetHtml("create-account-id", <span>{createdAccount.accountId.value}</span>) &
    SetHtml("create-account-bank-id", <span>{createdAccount.bankId.value}</span>) &
    SetHtml("create-account-initial-balance", <span>{createdAccount.balance}</span>) &
    SetHtml("create-account-currency", <span>{createdAccount.currency}</span>)
  }

  def showError(msg : String) : JsCmd = {
    hideSuccessMessage &
    showErrorMessage &
    SetHtml("create-account-error", <span>{msg}</span>)
  }

  private val showSuccessMessage = Show("create-account-success")
  private val hideSuccessMessage = Hide("create-account-success")
  private def showErrorMessage = Show("create-account-error")
  private val hideErrorMessage = Hide("create-account-error")

  /**
   * Attempts to create a new account, based on form params
   * @return a box containing the created account or reason for account creation failure
   */
  def createAccount(accountId : AccountId, bankId : BankId, accountType: String, accountLabel: String, currency : String, initialBalance : String) : Box[BankAccount] =  {

    if(accountId.value == "") Failure("Account id cannot be empty")
    else if(bankId.value == "") Failure("Bank id cannot be empty")
    else if(currency == "") Failure("Currency cannot be empty")
    else if(initialBalance == "") Failure("Initial balance cannot be empty")
    else if(fx.exchangeRate(currency, "EUR", Some(bankId.value)).isEmpty) Failure(s"Currency($currency) is not allowed, please call `Create Fx` for BANK_ID($BankId) and `EUR` first! ")
    else {
      for {
        initialBalanceAsNumber <- tryo {BigDecimal(initialBalance)} ?~! "Initial balance must be a number, e.g 1000.00"
        currentAuthUser <- AuthUser.currentUser ?~! "You need to be logged in to create an account"
        user <- Users.users.vend.getResourceUserByResourceUserId(currentAuthUser.user.get) ?~ "Server error: could not identify user"
        (bank, callContext) <- BankX(bankId, None) ?~ s"Bank $bankId not found"
        accountDoesNotExist <- booleanToBox(BankAccountX(bankId, accountId).isEmpty,
          s"Account with id $accountId already exists at bank $bankId")
        bankAccount <- Connector.connector.vend.createBankAccountLegacy(bankId, accountId, accountType, accountLabel, currency, initialBalanceAsNumber, user.name,
                                                                         "", List.empty)//added field in V220

        //1 Create or Update the `Owner` for the new account
        //2 Add permission to the user
        //3 Set the user as the account holder
        _ = BankAccountCreation.setAccountHolderAndRefreshUserAccountAccessLegacy(bankId, accountId, user, callContext)
      } yield {
        bankAccount
      }
    }

  }

}
