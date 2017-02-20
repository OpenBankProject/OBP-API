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

import code.api.OAuthHandshake
import code.model.Consumer
import code.util.Helper
import net.liftweb.util.Helpers._
import net.liftweb.http.S
import net.liftweb.common.Full
import net.liftweb.mapper.By
import net.liftweb.sitemap.Menu

/**
 * When a mobile app handles an oauth redirect, we can get left with a dangling page open in
 * their browser. By coming through this page before redirecting to the url specified by the app,
 * we at least make sure that the dangling page says something nice on it.
 */
class OAuthWorkedThanks {

  def thanks = {
    val redirectUrl = S.param("redirectUrl").map(urlDecode(_))
    
    //extract the clean(omit the parameters) redirect url from request url
    val requestedRedirectURL = Helper.extractCleanRedirectURL(redirectUrl.openOr("wrongurl")) openOr("wrongurl")
    //get the current app CONSUMER_KEY
    val appConsumerKey = OAuthHandshake.currentAppConsumerKey
    //get the redirectURL from CONSUMER table
    val validRedirectURL = Consumer.getRedirectURLByConsumerKey(appConsumerKey)
    
    redirectUrl match {
      case Full(url) =>
        //this redirect url is checked by following, no open redirect issue.
        if(validRedirectURL.equals(requestedRedirectURL)) {
          "#redirect-link [href]" #> url
        }else{
          "#oauth-done-thanks *" #> "Sorry, the App requested a redirect to a URL that is not registered. Note to application developers: You can set the redirect URL you will use at consumer registration - or update it with PUT /management/consumers...."
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