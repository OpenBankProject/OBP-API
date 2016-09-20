/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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

import code.model.dataAccess.{Admin, OBPUser}
import net.liftweb.http.{S, SHtml}
import net.liftweb.util.Helpers._
import net.liftweb.util.{CssSel, Props}

import scala.xml.NodeSeq

class Login {

  def loggedIn = {
    if(!OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".logout [href]" #> {
        OBPUser.logoutPath.foldLeft("")(_ + "/" + _)
      } &
      ".username *" #> OBPUser.getCurrentUserUsername
    }
  }

  def loggedOut = {
    if(OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    } else {
      ".login [href]" #> OBPUser.loginPageURL &
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


  // Used to display custom message to users when they login.
  // For instance we can use it to display example login on a sandbox
    def customiseLogin : CssSel = {
      val specialLoginInstructions  = scala.xml.Unparsed(Props.get("webui_login_page_special_instructions", ""))
      // In case we use Extraction.decompose
      implicit val formats = net.liftweb.json.DefaultFormats
      "#login_special_instructions *" #> specialLoginInstructions
    }


  def openIdConnectButton : CssSel = {
    if(Props.getBool("allow_openidconnect", false)){
      val but =
        """
          <input class="button" style="float: none !important;" value="OpenID" id="openid-button" type="image" onclick="lock.show();" src="%s" />
        """.format(
          Props.get("openidconnect.button_logo").openOrThrowException("no openidconnect.button_logo set")
        )
      val button  = scala.xml.Unparsed(s"""$but""")
      "#openid_button" #> button
    } else {
      "#openid_button" #> ""
	   }
  }

  def openIdConnectScripts : CssSel = {
    if(Props.getBool("allow_openidconnect", false)){
      val ext =
        """
          <script src="%s"></script>
        """.format(
          Props.get("openidconnect.login_ui_url").openOrThrowException("no openidconnect.login_ui_url set")
        )
      val scr =
        """
        <script type="text/javascript">
          var lock = new Auth0Lock('%s', '%s', {
            auth: {
              redirectUrl: '%s',
              responseType: 'code',
              params: {
                scope: 'openid email'
              }
            }
          });
        </script>
        """.format(
          Props.get("openidconnect.clientId").openOrThrowException("no openidconnect.clientId set"),
          Props.get("openidconnect.domain").openOrThrowException("no openidconnect.domain set"),
          Props.get("openidconnect.callbackURL").openOrThrowException("no openidconnect.callbackURL set")
        )
      val scripts = scala.xml.Unparsed(s"""$ext $scr""")
      "#openid_scripts" #> scripts
    } else {
      "#openid_scripts" #> ""
    }
  }


  // End of class
}
