 /**
 Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

*/
package code.snippet

import net.liftweb.util.Helpers._
import net.liftweb.http.S
import net.liftweb.common.Full
import net.liftweb.sitemap.Menu

/**
 * When a mobile app handles an oauth redirect, we can get left with a dangling page open in
 * their browser. By coming through this page before redirecting to the url specified by the app,
 * we at least make sure that the dangling page says something nice on it.
 */
class OAuthWorkedThanks {

  def thanks = {
    val redirectUrl = S.param("redirectUrl").map(urlDecode(_))
    
    redirectUrl match {
      case Full(url) => {
        "#redirect-link [href]" #> url
      }
      case _ => {
        "#thanks *" #> "Error"
      }
    }
  }
  
}

object OAuthWorkedThanks {
  
  val menu = {
    val menuable = Menu.i("OAuth Thanks") / "oauth" / "thanks"
    menuable.toMenu
  }
  
}