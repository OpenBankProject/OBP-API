 /**
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

 Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
		by
		Simon Redfern : simon AT tesobe DOT com
		Everett Sochowski: everett AT tesobe DOT com
		Ayoub Benali : ayoub AT tesobe Dot com
*/
package code.snippet
import net.liftweb.http.InMemoryResponse
import net.liftweb.common.Full
import net.liftweb.http.S
import code.model.{Nonce, Consumer, Token}
import net.liftweb.mapper.By
import java.util.Date
import java.net.URLEncoder
import javax.crypto.spec.SecretKeySpec
import net.liftweb.util.Helpers
import code.model.TokenType
import TokenType._
import code.model.dataAccess.OBPUser
import scala.xml.NodeSeq
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

object OAuthAuthorisation {

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""

  def shouldNotLogUserOut() : Boolean = {
    S.param("logUserOut") match {
      case Full("false") => true
      case _ => false
    }
  }

  def hideFailedLoginMessageIfNeeded() = {
    S.param("failedLogin") match {
      case Full("true") => NOOP_SELECTOR
      case _ => ".login-error" #> ""
    }
  }

  // this method is specific to the authorization page ( where the user login to grant access
  // to the application (step 2))
  def tokenCheck =
    S.param("oauth_token") match {
      case Full(token) =>
        Token.find(By(Token.key, Helpers.urlDecode(token.toString)), By(Token.tokenType, TokenType.Request)) match {
          case Full(appToken) =>
            //check if the token is still valid
            if (appToken.isValid) {
              if (OBPUser.loggedIn_? && shouldNotLogUserOut()) {
                var verifier = ""
                // if the user is logged in and no verifier have been generated
                if (appToken.verifier.isEmpty) {
                  val randomVerifier = appToken.gernerateVerifier
                  //the user is logged in so we have the current user
                  val obpUser = OBPUser.currentUser.get
                  //FIXME: The whole snippet still use OBPUser, we must change it to the User trait
                  //link the token with the concrete API User
                  obpUser.user.obj.map{
                    u  => {
                      //We want ApiUser.id because it is unique, unlike the id given by a provider
                      // i.e. two different providers can have a user with id "bob"
                      appToken.userForeignKey(u.id.get)
                    }
                  }
                  if (appToken.save())
                    verifier = randomVerifier
                } else
                  verifier = appToken.verifier

                // show the verifier if the application does not support
                // redirection
                if (appToken.callbackURL.is == "oob")
                  "#verifier *" #> verifier &
                    "#errorMessage" #> "" &
                    "#account" #> ""
                else {
                  //send the user to another obp page that handles the redirect
                  val oauthQueryParams: List[(String, String)] = ("oauth_token", token) :: ("oauth_verifier",verifier) :: Nil
                  val applicationRedirectionUrl = appendParams(appToken.callbackURL, oauthQueryParams)
                  val encodedApplicationRedirectionUrl = urlEncode(applicationRedirectionUrl)
                  val redirectionUrl = Props.get("hostname", "") + OAuthWorkedThanks.menu.loc.calcDefaultHref
                  val redirectionParam = List(("redirectUrl",encodedApplicationRedirectionUrl))
                  S.redirectTo(appendParams(redirectionUrl, redirectionParam))
                }
              } else {
                val currentUrl = S.uriAndQueryString.getOrElse("/")
                if(OBPUser.loggedIn_?) {
                  OBPUser.logUserOut()
                  //Bit of a hack here, but for reasons I haven't had time to discover, if this page doesn't get
                  //refreshed here the session vars OBPUser.loginRedirect and OBPUser.failedLoginRedirect don't get set
                  //properly and the redirect after login gets messed up. -E.S.
                  S.redirectTo(currentUrl)
                }
                //if login succeeds, reload the page with logUserOut=false to process it
                OBPUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List(("logUserOut", "false")))))
                //if login fails, just reload the page with the login form visible
                OBPUser.failedLoginRedirect.set(Full(Helpers.appendParams(currentUrl, List(("failedLogin", "true")))))
                //the user is not logged in so we show a login form
                Consumer.find(By(Consumer.id, appToken.consumerId)) match {
                  case Full(consumer) => {
                    hideFailedLoginMessageIfNeeded &
                    "#applicationName" #> consumer.name &
                      "#verifierBloc" #> NodeSeq.Empty &
                      "#errorMessage" #> NodeSeq.Empty &
                      {
                        ".login [action]" #> OBPUser.loginPageURL &
                          ".forgot [href]" #> {
                            val href = for {
                              menu <- OBPUser.resetPasswordMenuLoc
                            } yield menu.loc.calcDefaultHref

                            href getOrElse "#"
                          } &
                          ".signup [href]" #>
                          OBPUser.signUpPath.foldLeft("")(_ + "/" + _)
                      }
                  }
                  case _ =>
                    "#errorMessage" #> "Application not found" &
                      "#userAccess" #> NodeSeq.Empty
                }
              }
            } else
              "#errorMessage" #> "Token expired" &
                "#userAccess" #> NodeSeq.Empty
          case _ =>
            "#errorMessage" #> "This token does not exist" &
              "#userAccess" #> NodeSeq.Empty
        }
      case _ =>
        "#errorMessage" #> "There is no Token" &
          "#userAccess" #> NodeSeq.Empty
    }

	//looks for expired tokens and nonces and delete them
	def dataBaseCleaner : Unit = {
		import net.liftweb.util.Schedule
		import net.liftweb.mapper.By_<
		Schedule.schedule(dataBaseCleaner _, 1 hour)

		val currentDate = new Date()

		/*
			As in "wrong timestamp" function, 3 minutes is the timestamp limit where we accept
			requests. So this function will delete nonces which have a timestamp older than
			currentDate - 3 minutes
		*/
		val timeLimit = new Date(currentDate.getTime + 180000)

		//delete expired tokens and nonces
		(Token.findAll(By_<(Token.expirationDate,currentDate)) ++ Nonce.findAll(By_<(Nonce.timestamp,timeLimit))).foreach(t => t.delete_!)
	}
}