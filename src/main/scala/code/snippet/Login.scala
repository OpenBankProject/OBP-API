/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.snippet

import code.model.dataAccess.{OBPUser,Account}
import scala.xml.NodeSeq
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import net.liftweb.util.CssSel
import net.liftweb.http.S
import code.model.dataAccess.Admin
import net.liftweb.http.SHtml

class Login {

  def loggedIn = {
    if(!OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".logout [href]" #> {
        OBPUser.logoutPath.foldLeft("")(_ + "/" + _)
      } &
      ".username *" #> OBPUser.currentUser.get.email.get
    }
  }

  def loggedOut = {
    if(OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    } else {
      ".login [action]" #> OBPUser.loginPageURL &
      ".forgot [href]" #> {
        val href = for {
          menu <- OBPUser.lostPasswordMenuLoc
        } yield menu.loc.calcDefaultHref
        href getOrElse "#"
      } & {
        ".signup [href]" #> {
         OBPUser.signUpPath.foldLeft("")(_ + "/" + _)
        }
      }
    }
  }
  
  def adminLogout : CssSel = {
    if(Admin.loggedIn_?) {
      val current = Admin.currentUser
      "#admin-username" #> current.map(_.email.get) &
      "#admin-logout-clickme [onclick+]" #> SHtml.onEvent(s => {
        Admin.logoutCurrentUser
        S.redirectTo("/")
      })
    } else {
      "#admin-logout" #> ""
    }
  }

}