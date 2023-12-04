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

import code.api.OAuthHandshake
import code.model.Consumer
import code.token.Tokens
import code.util.Helper
import code.util.Helper.{MdcLoggable, ObpS}
import net.liftweb.util.Helpers._
import net.liftweb.http.S
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.By
import net.liftweb.sitemap.Menu

/**
 * When a mobile app handles an oauth redirect, we can get left with a dangling page open in
 * their browser. By coming through this page before redirecting to the url specified by the app,
 * we at least make sure that the dangling page says something nice on it.
 */
class OAuthWorkedThanks extends MdcLoggable {

  def thanks = {
    val redirectUrl = ObpS.param("redirectUrl").map(urlDecode(_))
    logger.debug(s"OAuthWorkedThanks.thanks.redirectUrl $redirectUrl")
    //extract the clean(omit the parameters) redirect url from request url
    val requestedRedirectURL = Helper.getStaticPortionOfRedirectURL(redirectUrl.openOr("invalidRequestedRedirectURL")) openOr("invalidRequestedRedirectURL")
    logger.debug(s"OAuthWorkedThanks.thanks.requestedRedirectURL $requestedRedirectURL")
    
    val requestedOauthToken = Helper.extractOauthToken(redirectUrl.openOr("No Oauth Token here")) openOr("No Oauth Token here")
    logger.debug(s"OAuthWorkedThanks.thanks.requestedOauthToken $requestedOauthToken")
    
    // 1st, find token --> 2rd, find consumer -->3rd ,find the RedirectUrl.
    val consumer = Tokens.tokens.vend.getTokenByKey(requestedOauthToken).map(_.consumer).flatten
    val validRedirectURL= consumer.map(_.redirectURL.get).getOrElse("invalidRedirectURL")
    val appName = consumer.map(_.name.get).getOrElse("Default_App")
    logger.debug(s"OAuthWorkedThanks.thanks.validRedirectURL $validRedirectURL")
    
    redirectUrl match {
      case Full(url) =>
        //this redirect url is checked by following, no open redirect issue.
        // TODO maybe handle case of extra trailing / on the url ?

        val incorrectRedirectUrlMessage =  s"The validRedirectURL is $validRedirectURL but the requestedRedirectURL was $requestedRedirectURL"


        if(validRedirectURL.equals(requestedRedirectURL)) {
          "#redirect-link [href]" #> url &
          ".app-name"#> appName //there may be several places to be modified in html, so here use the class, not the id.
        }else{
          logger.info(incorrectRedirectUrlMessage)
          "#oauth-done-thanks *" #> s"Sorry, the App requested a redirect to a URL that is not registered. $incorrectRedirectUrlMessage - Note to application developers: You can set the redirect URL you will use at consumer registration - or update it with PUT /management/consumers...."
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