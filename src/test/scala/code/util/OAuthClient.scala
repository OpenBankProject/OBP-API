/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
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

package code.util

import java.net.{URI, URLDecoder}

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}
import oauth.signpost.basic.{DefaultOAuthConsumer, DefaultOAuthProvider}
import oauth.signpost.{OAuthConsumer, OAuthProvider}
import org.apache.http.client.utils.URLEncodedUtils
import org.openqa.selenium._
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import code.api.oauth1a.OauthParams._
import scala.collection.JavaConversions._

sealed trait Provider {
  val name : String

  val apiBaseUrl : String
  val requestTokenUrl : String
  val accessTokenUrl : String
  val authorizeUrl : String
  val signupUrl : Option[String]

  /**
   * Can't do oAuthProvider = new DefaultOAuthProvider(requestTokenUrl, accessTokenUrl, authorizeUrl)
   * here as the Strings all evaluate at null at this point in object creation
   */
  val oAuthProvider : OAuthProvider

  val consumerKey : String
  val consumerSecret : String
}

trait DefaultProvider extends Provider {
  val name = "The Open Bank Project Demo"

  val baseUrl = APIUtil.getPropsValue("target_api_hostname", "")
  val apiBaseUrl = baseUrl + "/obp"
  val requestTokenUrl = baseUrl + "/oauth/initiate"
  val accessTokenUrl = baseUrl + "/oauth/token"
  val authorizeUrl = baseUrl + "/oauth/authorize"
  val signupUrl = Some(baseUrl + "/user_mgt/sign_up")

  lazy val oAuthProvider : OAuthProvider = new DefaultOAuthProvider(requestTokenUrl, accessTokenUrl, authorizeUrl)

  val consumerKey = APIUtil.getPropsValue("obp_consumer_key", "")
  val consumerSecret = APIUtil.getPropsValue("obp_secret_key", "")
}

object OBPDemo extends DefaultProvider

/*case class Consumer(consumerKey : String, consumerSecret : String) {
  val oAuthConsumer : OAuthConsumer = new DefaultOAuthConsumer(consumerKey, consumerSecret)
}*/

case class Credential(provider : Provider, consumer : OAuthConsumer, readyToSign : Boolean)

object OAuthClient extends MdcLoggable {
  var credentials : Option[Credential] = None
  var mostRecentLoginAttemptProvider : Box[Provider] = Empty

  def getAuthorizedCredential() : Option[Credential] = {
    credentials.filter(_.readyToSign)
  }

  def currentApiBaseUrl : String = {
    getAuthorizedCredential().map(_.provider.apiBaseUrl).getOrElse(OBPDemo.apiBaseUrl)
  }

  def setNewCredential(provider : Provider) : Credential = {
    val consumer = new DefaultOAuthConsumer(provider.consumerKey, provider.consumerSecret)
    val credential = Credential(provider, consumer, false)

    credentials = Some(credential)
    credential
  }

  def handleCallback(verifier : Box[String], provider: Provider) = {
    val success = for {
      consumer <- Box(credentials.map(_.consumer)) ?~ "No consumer found for callback"
      verifier <- verifier ?~ "Didn't get oauth_verifier"
    } yield {
      //after this, consumer is ready to sign requests
      provider.oAuthProvider.retrieveAccessToken(consumer, verifier)
      //update the session credentials
      val newCredential = Credential(provider, consumer, true)
      credentials = Some(newCredential)
    }

    success match {
      case Full(_) => Empty
      case Failure(msg, _, _) => logger.warn(msg)
      case _ => logger.warn("Something went wrong in an oauth callback and there was no error message set for it")
    }
    Empty
  }

  def authenticateWithOBPCredentials(username: String, password: String) = {
    val provider = OBPDemo
    mostRecentLoginAttemptProvider = Full(provider)
    val credential = setNewCredential(provider)

    val authUrl = provider.oAuthProvider.retrieveRequestToken(credential.consumer, "http://dummyurl.com/oauthcallback")

    //use selenium to submit login form
    val webdriver = new HtmlUnitDriver()
    webdriver.get(authUrl)
    webdriver.findElement(By.xpath("//input[@name='username']")).sendKeys(username)
    val element = webdriver.findElement(By.xpath("//input[@name='password']"))
    element.sendKeys(password)
    element.submit()

    //get redirect urls oauth_verifier
    val params = URLEncodedUtils.parse(new URI(webdriver.getCurrentUrl()), "UTF-8")
    var verifier : Box[String] = Empty
    params.foreach(p => {
      if (p.getName() == "redirectUrl") {
        val decoded = URLDecoder.decode(p.getValue())
        val params = URLEncodedUtils.parse(new URI(decoded), "UTF-8")
        params.foreach(p => {
          if (p.getName() == VerifierName) {
            verifier = Full(p.getValue())
          }
        })
      }
    })

    handleCallback(verifier, provider)
  }

  def loggedIn : Boolean = credentials.map(_.readyToSign).getOrElse(false)

  def logoutAll() = {
    credentials = None
  }
}