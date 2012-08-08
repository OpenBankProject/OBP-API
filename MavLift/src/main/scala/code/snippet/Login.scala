package code.snippet

import code.model.dataAccess.{OBPUser,Account}

import scala.xml.NodeSeq
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import net.liftweb.util.CssSel
import net.liftweb.http.S

class Login {

  def loggedIn = {
    if(!OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".logout [href]" #> {
        OBPUser.logoutPath.foldLeft("")(_ + "/" + _)
      } &
      ".username *" #> OBPUser.currentUser.get.email.get &
      ".account-number *" #> Account.currentAccount.get.number.get
    }
  }
  
  def loggedOut = {
    if(OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    } else {
      ".login [action]" #> OBPUser.loginPageURL &
      ".forgot [href]" #> {
        val href = for {
          menu <- OBPUser.resetPasswordMenuLoc
        } yield menu.loc.calcDefaultHref
        href getOrElse "#"
      } & {
        ".signup [href]" #> {
         OBPUser.signUpPath.foldLeft("")(_ + "/" + _)
        }
      }
    }
  }

  def redirectTesobeAnonymousIfLoggedOut = {
    if(!OBPUser.loggedIn_?){
      S.redirectTo("/accounts/tesobe/anonymous")
    }  else {
      "*" #> NodeSeq.Empty
    }
  }

}