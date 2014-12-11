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

import code.users.UserAuth
import net.liftweb.common.Loggable
import code.model.Nonce

object OAuthAuthorisation extends Loggable{
  import net.liftweb.common.{Box, Full, Failure}
  import net.liftweb.http.{S, RequestVar, SHtml}
  import net.liftweb.util.Helpers._
  import scala.util.{Either, Right, Left}
  import code.model.{Token, Consumer}
  import code.model.Consumer
  import code.util.Helper
  import code.model.dataAccess.APIUser


  class FormField[T](
                      val defaultValue: T,
                      //the message in case the field value is not valid
                      val errorMessage: String,
                      //the id of the error notice node (span/div) in the template where
                      //to show the error message
                      val errorNodeId: String
                      ) extends RequestVar[T](defaultValue){
    /**
     * Left (the error case) contains the pair (error node Id,  error message)
     * Right Unit
     */
    def validate : Either[(String, String),Unit] =
      if(this.is == defaultValue)
        Left((this.errorNodeId, errorMessage))
      else
        Right()
  }

  private object userId extends FormField[String]("", "User Id empty !", "userIdError")
  private object password extends FormField[String]("", "Password empty !", "passwordError")

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""

  private def hideFailedLoginMessageIfNeeded() = {
    S.param("failedLogin") match {
      case Full("true") => NOOP_SELECTOR
      case _ => ".login-error" #> ""
    }
  }

  private def validate(fields: Seq[FormField[_]]) : Seq[(String,String)] = {
    fields
      .map{_.validate}
      .collect{
      case l: Left[(String,String), Unit] => l.a
    }
  }

  private def checkTokenValidity(token: Token): Box[Unit] = {
    if(token.isValid){
      Full()
    }
    else
      Failure("token expired")
  }

  def render ={
    import net.liftweb.mapper.By
    import net.liftweb.http.js.JsCmd
    import scala.xml.NodeSeq

    val cssSelctor =
      for{
        tokenParam <- S.param("oauth_token") ?~ "no oauth_token param"
        appToken <- Token.getRequestToken(urlDecode(tokenParam)) ?~ s"token $tokenParam does not exist"
        _ <- checkTokenValidity(appToken)
        consumer <- Consumer.find(By(Consumer.id, appToken.consumerId)) ?~ s"application not found"
      } yield{

        def processInputs(): JsCmd = {

          val fields = userId :: password :: Nil
          val errors = validate(fields)
          if (errors.isEmpty){
            UserAuth.authChecker.vend.getAPIUser(userId, password) match {
              case Full(user) => {
                val verifier = getVerifier(appToken, user)
                if (appToken.callbackURL.is == "oob"){
                  import scala.xml.Unparsed
                  import net.liftweb.http.js.JsCmds.{SetHtml, JsHideId}
                  lazy val textMessage = <div>Please come back to the application where you come from and enter the following code: </div>
                  def verifierMessage(verifier: String) = <div id="verifier">{verifier}</div>

                  Helper.JsHideByClass("error")&
                    SetHtml("verifierBloc",textMessage ++ verifierMessage(verifier)) &
                    JsHideId("account")
                }
                else {
                  import net.liftweb.http.js.JsCmds.RedirectTo
                  import net.liftweb.util.Props

                  //send the user to another obp page that handles the redirect
                  val oauthQueryParams: List[(String, String)] = ("oauth_token", tokenParam) :: ("oauth_verifier",verifier) :: Nil
                  val applicationRedirectionUrl = appendParams(appToken.callbackURL, oauthQueryParams)
                  val encodedApplicationRedirectionUrl = urlEncode(applicationRedirectionUrl)
                  val redirectionPageURL = Props.get("hostname", "") + OAuthWorkedThanks.menu.loc.calcDefaultHref
                  val redirectionURLParams = List(("redirectUrl",encodedApplicationRedirectionUrl))
                  val redirectionURL = appendParams(redirectionPageURL, redirectionURLParams)
                  RedirectTo(redirectionURL)
                }
              }
              case Failure(msg, _, _)=> {
                S.error("errorMessage",msg)
                Helper.JsShowByClass("hide-during-ajax")
              }
              case _ =>{
                S.error("errorMessage","Could not authenticate user. Please try later.")
                Helper.JsShowByClass("hide-during-ajax")
              }
            }
          }
          else{
            errors.foreach{
              e => S.error(e._1, e._2)
            }
            Helper.JsShowByClass("hide-during-ajax")
          }
        }
        "form [class+]" #> "login" &
          "#applicationName" #> consumer.name &
          "#userId" #> SHtml.textElem(userId,("placeholder","user-1234")) &
          "#password" #> SHtml.passwordElem(password,("placeholder","***********")) &
          "#processSubmit" #> SHtml.hidden(processInputs)
      }
    cssSelctor match {
      case Full(cssSel) => {
        logger.info("every thing is ok")
        cssSel
      }
      case Failure(msg, _,_) => {
        logger.error("failure")
        S.error("errorMessage", msg)
        "#userAccess" #> NodeSeq.Empty
      }
      case _ =>{
        logger.warn("empty ! ")
        NOOP_SELECTOR
      }
    }
  }

  private def getVerifier(token: Token, user: APIUser): String = {
    if (token.verifier.isEmpty){
      val randomVerifier = token.gernerateVerifier
      token.userForeignKey(user.id.get)
      token.save()
      randomVerifier
    }
    else{
      token.verifier
    }
  }
  //looks for expired tokens and nonces and delete them
  def dataBaseCleaner : Unit = {
    import net.liftweb.util.Schedule
    import net.liftweb.mapper.By_<
    import java.util.Date

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