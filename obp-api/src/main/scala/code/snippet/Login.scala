/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.snippet

import code.api.OpenIdConnectConfig
import code.api.util.{APIUtil, CustomJsonFormats}
import code.model.dataAccess.{Admin, AuthUser}
import net.liftweb.http.{S, SHtml}
import net.liftweb.util.Helpers._
import net.liftweb.util.CssSel
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue

import scala.xml.NodeSeq

class Login {

  def loggedIn = {
    if(!AuthUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".logout [href]" #> {
        AuthUser.logoutPath.foldLeft("")(_ + "/" + _)
      } &
      "#loggedIn-username *" #> AuthUser.getCurrentUserUsername
    }
  }

  def loggedOut = {
    if(AuthUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    } else {
      ".login [href]" #> AuthUser.loginPageURL &
      ".forgot [href]" #> {
        val href = for {
          menu <- AuthUser.lostPasswordMenuLoc
        } yield menu.loc.calcDefaultHref
        href getOrElse "#"
      } & {
        ".signup [href]" #> {
         AuthUser.signUpPath.foldLeft("")(_ + "/" + _)
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


  import APIUtil.activeBrand


  // Used to display custom message to users when they login.
  // For instance we can use it to display example login on a sandbox
    def customiseLogin : CssSel = {
      val specialLoginInstructions  = scala.xml.Unparsed(getWebUiPropsValue("webui_login_page_special_instructions", ""))
      // In case we use Extraction.decompose
      implicit val formats = CustomJsonFormats.formats
      "#login-special-instructions *" #> specialLoginInstructions &
      "#brand [value]" #> activeBrand
    }
  
  // End of class
}
