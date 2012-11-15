package code.snippet

import net.liftweb.util.Helpers._
import code.model.traits.BankAccount
import net.liftweb.common.Full
import code.model.dataAccess.OBPUser
import code.model.traits.User
import code.model.implementedTraits.Anonymous
import code.model.implementedTraits.Owner
import code.model.implementedTraits.Board
import code.model.implementedTraits.Authorities
import code.model.implementedTraits.Team
import code.model.implementedTraits.OurNetwork

class AccountsOverview {

  def publicAccounts = {
    //TODO: In the future once we get more bank accounts we will probably want some sort of pagination or limit on the number of accounts displayed
    val publicAccounts = BankAccount.publicAccounts.sortBy(acc => acc.label)
    ".accountList" #> publicAccounts.map(acc => {
      ".accLink *" #> acc.label &
      //TODO: Would be nice to be able to calculate this is in a way that would be less fragile in terms of maintenance
      ".accLink [href]" #> { "/banks/" + acc.bankPermalink + "/accounts/" + acc.permalink + "/" + Anonymous.permalink } 
    })
  }
  
  def authorisedAccounts = {
    
    def loggedInSnippet(user: User) = {
      val accountsWithMoreThanAnonAccess = user.accountsWithMoreThanAnonAccess.toList.sortBy(acc => acc.label)
      ".accountList" #> accountsWithMoreThanAnonAccess.map(acc => {
        ".accLink *" #> acc.label &
        //TODO: Would be nice to be able to calculate this is in a way that would be less fragile in terms of maintenance
        ".accLink [href]" #> { 
          val permittedViews = user.permittedViews(acc)
          val highestViewPermalink = {
            //Make sure that the link always points to the same view by giving some order instead of picking the first one
            if(permittedViews.contains(Owner)) Owner.permalink
            else if (permittedViews.contains(Board)) Board.permalink
            else if (permittedViews.contains(Authorities)) Authorities.permalink
            else if (permittedViews.contains(Team)) Team.permalink
            else OurNetwork.permalink
           }
          "/banks/" + acc.bankPermalink + "/accounts/" + acc.permalink + "/" + highestViewPermalink
        } 
      })
    }
    
    def loggedOutSnippet = {
      ".authorised_accounts" #> ""
    }
    
    OBPUser.currentUser match {
      case Full(u) => loggedInSnippet(u)
      case _ => loggedOutSnippet
    }
  }
  
}