package code.snippet

import code.model.User
import scala.xml.NodeSeq
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import net.liftweb.util.CssSel
import net.liftweb.http.S

class Login {

  def loggedIn = {
    if(!User.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".logout [href]" #> {
        User.logoutPath.foldLeft("")(_ + "/" + _)
      }
    }
  }
  
  def loggedOut : CssSel = {
    if(User.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".login [action]" #> User.loginPageURL &
      ".forgot [href]" #> {
        val href = for {
          menu <- User.resetPasswordMenuLoc
        } yield menu.loc.calcDefaultHref
        href getOrElse "#"
      } & {
        ".signup [href]" #> {
         User.signUpPath.foldLeft("")(_ + "/" + _)
        }
      }
    }
  }
  
}