package code.snippet

import net.liftweb.util.Helpers._
import code.model.traits.BankAccount
import net.liftweb.common.Full
import code.model.dataAccess.OBPUser
import code.model.traits.User

class AccountsOverview {

  def publicAccounts = {
    //TODO: In the future once we get more bank accounts we will probably want some sort of pagination or limit on the number of accounts displayed
    val publicAccounts = BankAccount.publicAccounts
    "*" #> publicAccounts.map(acc => {
      ".account *" #> acc.label
    })
  }
  
  def privateAccounts = {
    
    def loggedInSnippet(user: User) = {
      val accountsWithMoreThanAnonAccess = user.accountsWithMoreThanAnonAccess
      "*" #> accountsWithMoreThanAnonAccess.map(acc => {
        ".account *" #> acc.label
      })
    }
    
    def loggedOutSnippet = {
      "#nothing" #> ""
    }
    
    OBPUser.currentUser match {
      case Full(u) => loggedInSnippet(u)
      case _ => loggedOutSnippet
    }
  }
  
}