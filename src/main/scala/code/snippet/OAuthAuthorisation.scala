/**
 * Open Bank Project
 *
 * Copyright 2011,2012 TESOBE / Music Pictures Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Open Bank Project (http://www.openbankproject.com)
 * Copyright 2011,2012 TESOBE / Music Pictures Ltd
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 * by
 * Simon Redfern : simon AT tesobe DOT com
 * Everett Sochowski: everett AT tesobe DOT com
 * Ayoub Benali : ayoub AT tesobe Dot com
 */
package code.snippet
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.Req
import net.liftweb.http.GetRequest
import net.liftweb.http.PostRequest
import net.liftweb.http.LiftResponse
import net.liftweb.common.Box
import net.liftweb.http.InMemoryResponse
import net.liftweb.common.{ Full, Empty }
import net.liftweb.http.S
import code.model.{ Nonce, Consumer, Token }
import net.liftweb.mapper.By
import java.util.Date
import java.net.{ URLEncoder, URLDecoder }
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import net.liftweb.util.Helpers
import code.model.AppType._
import code.model.TokenType
import TokenType._
import scala.compat.Platform
import code.model.dataAccess.OBPUser
import scala.xml.NodeSeq
import net.liftweb.util.Helpers._

object OAuthAuthorisation {

  
  def shouldNotLogUserOut() : Boolean = {
    S.param("logUserOut") match {
      case Full("false") => true
      case _ => false
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
                  val user = OBPUser.currentUser.get
                  //FIXME: The whole snippet still use OBPUser, we must change it to the User trait
                  appToken.userId(user.id_)
                  if (appToken.save())
                    verifier = randomVerifier
                } else
                  verifier = appToken.verifier

                // show the verifier if the application does not support
                // redirection
                if (appToken.callbackURL.is == "oob")
                  "#verify-code *" #> verifier &
                    "#errorMessage" #> "" &
                    "#account" #> ""
                else {
                  //redirect the user to the application with the verifier
                  S.redirectTo(appToken.callbackURL + "?oauth_token=" + token +
                    "&oauth_verifier=" + verifier)
                  "#verifier" #> "you should be redirected"
                }
              } else {
                if(OBPUser.loggedIn_?) OBPUser.logUserOut()
                val currentUrl = S.uriAndQueryString.getOrElse("/")
                OBPUser.redirectToOnLogin.set(Helpers.appendParams(currentUrl, List(("logUserOut", "false"))))
                //the user is not logged in so we show a login form
                Consumer.find(By(Consumer.id, appToken.consumerId)) match {
                  case Full(consumer) => {
                    "#applicationName" #> consumer.name &
                      "#verifier" #> NodeSeq.Empty &
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