/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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

This product includes software developed at
TESOBE (http://www.tesobe.com/)
by
Simon Redfern : simon AT tesobe DOT com
Stefan Bethge : stefan AT tesobe DOT com
Everett Sochowski : everett AT tesobe DOT com
Ayoub Benali: ayoub AT tesobe DOT com


TESOBE Ltd.
Osloer Str. 16/17
Berlin 13359, Germany
Email: contact@tesobe.com

*/


package code.snippet

import code.util.Helper
import net.liftweb.common.{Failure, Full, Empty}
import net.liftweb.http.S
import code.model.{Nonce, Consumer, Token}
import net.liftweb.mapper.By
import java.util.Date
import net.liftweb.util.{CssSel, Helpers, Props}
import code.model.TokenType
import code.model.dataAccess.OBPUser
import scala.xml.NodeSeq
import net.liftweb.util.Helpers._
import code.util.Helper.NOOP_SELECTOR

object OAuthAuthorisation {

  val LogUserOutParam = "logUserOut"
  val FailedLoginParam = "failedLogin"

  val ErrorMessageSel = "#errorMessage"
  val VerifierBlocSel = "#verifierBloc"

  def shouldNotLogUserOut(): Boolean = {
    S.param(LogUserOutParam) match {
      case Full("false") => true
      case Empty => true
      case _ => false
    }
  }

  def hideFailedLoginMessageIfNeeded() = {
    S.param(FailedLoginParam) match {
      case Full("true") => NOOP_SELECTOR
      case _ => ".login-error" #> ""
    }
  }

  // this method is specific to the authorization page ( where the user logs in to grant access
  // to the application (step 2))
  def tokenCheck = {

    def error(msg: String): CssSel = {
      ErrorMessageSel #> S.?(msg) &
        "#userAccess" #> NodeSeq.Empty &
        VerifierBlocSel #> NodeSeq.Empty
    }

    //TODO: refactor into something nicer / more readable
    def validTokenCase(appToken: Token, unencodedTokenParam: String): CssSel = {
      if (OBPUser.loggedIn_? && shouldNotLogUserOut()) {
        var verifier = ""
        // if the user is logged in and no verifier have been generated
        if (appToken.verifier.isEmpty) {
          val randomVerifier = appToken.gernerateVerifier
          //the user is logged in so we have the current user
          val obpUser = OBPUser.currentUser.get

          //link the token with the concrete API User
          obpUser.user.obj.map {
            u => {
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
            ErrorMessageSel #> "" &
            "#account" #> ""
        else {
          //send the user to another obp page that handles the redirect
          val oauthQueryParams: List[(String, String)] = ("oauth_token", unencodedTokenParam) ::("oauth_verifier", verifier) :: Nil
          val applicationRedirectionUrl = appendParams(appToken.callbackURL, oauthQueryParams)
          val encodedApplicationRedirectionUrl = urlEncode(applicationRedirectionUrl)
          val redirectionUrl = Props.get("hostname", "") + OAuthWorkedThanks.menu.loc.calcDefaultHref
          val redirectionParam = List(("redirectUrl", encodedApplicationRedirectionUrl))
          S.redirectTo(appendParams(redirectionUrl, redirectionParam))
        }
      } else {
        val currentUrl = S.uriAndQueryString.getOrElse("/")
        /*if (OBPUser.loggedIn_?) {
          OBPUser.logUserOut()
          //Bit of a hack here, but for reasons I haven't had time to discover, if this page doesn't get
          //refreshed here the session vars OBPUser.loginRedirect and OBPUser.failedLoginRedirect don't get set
          //properly and the redirect after login gets messed up. -E.S.
          S.redirectTo(currentUrl)
        }*/

        //if login succeeds, reload the page with logUserOut=false to process it
        OBPUser.loginRedirect.set(Full(Helpers.appendParams(currentUrl, List((LogUserOutParam, "false")))))
        //if login fails, just reload the page with the login form visible
        OBPUser.failedLoginRedirect.set(Full(Helpers.appendParams(currentUrl, List((FailedLoginParam, "true")))))
        //the user is not logged in so we show a login form
        Consumer.find(By(Consumer.id, appToken.consumerId)) match {
          case Full(consumer) => {
            hideFailedLoginMessageIfNeeded &
              "#applicationName" #> consumer.name &
              VerifierBlocSel #> NodeSeq.Empty &
              ErrorMessageSel #> NodeSeq.Empty & {
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
          case _ => error("Application not found")
        }
      }
    }

    //TODO: improve error messages
    val cssSel = for {
      tokenParam <- S.param("oauth_token") ?~! "There is no Token."
      token <- Token.find(By(Token.key, Helpers.urlDecode(tokenParam.toString)), By(Token.tokenType, TokenType.Request)) ?~! "This token does not exist"
      tokenValid <- Helper.booleanToBox(token.isValid, "Token expired")
    } yield {
      validTokenCase(token, tokenParam)
    }

    cssSel match {
      case Full(sel) => sel
      case Failure(msg, _, _) => error(msg)
      case _ => error("unknown error")
    }

  }

  //looks for expired tokens and nonces and deletes them
  def dataBaseCleaner: Unit = {
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
    (Token.findAll(By_<(Token.expirationDate, currentDate)) ++ Nonce.findAll(By_<(Nonce.timestamp, timeLimit))).foreach(t => t.delete_!)
  }
}