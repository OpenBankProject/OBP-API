/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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

import net.liftweb.util.Helpers._
import code.model.Consumer
import net.liftweb.http.S
import net.liftweb.http.RequestVar
import net.liftweb.util.FieldError
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import net.liftweb.common.Loggable
import code.util.Helper.NOOP_SELECTOR

class ConsumerRegistration extends Loggable {

  //TODO: for security reasons this snippet and the template must be re-factored
  //to use the lift build in form function(SHtml._) so we can hide to what
  //the input fields are mapped to in the server side !!

  private object nameVar extends RequestVar("")
  private object redirectionURLVar extends RequestVar("")
  private object appTypeVar extends RequestVar[Consumer.appType.enum.AppType](Consumer.appType.enum.values.head)
  private object descriptionVar extends RequestVar("")
  private object devEmailVar extends RequestVar("")

  def registerForm = {

    def registerWithoutWarnings =
      register &
      "#registration-errors" #> ""

    def register = {
      ".register" #> {
        ".app-type-option" #> {
          val appTypes = Consumer.appType.enum.values.map(appType => appType.toString)
            appTypes.map(t => {
              val selected = appTypeVar.get.toString == t

              def markIfSelected =
                if(selected) "* [selected]" #> "selected"
                else NOOP_SELECTOR

              markIfSelected &
              "* *" #> t &
              "* [value]" #> t
            })
          } &
      	"name=app-name [value]" #> nameVar.get &
        "name=app-user-authentication-url [value]" #> redirectionURLVar.get &
      	"name=app-description *" #> descriptionVar.get &
      	"name=app-developer [value]" #> devEmailVar.get
      } &
      ".success" #> ""
    }

    def showResults(consumer : Consumer) = {
      //thanks for registering, here's your key, etc.
      ".app-name *" #> consumer.name.get &
      ".app-user-authentication-url *" #> consumer.userAuthenticationURL &
      ".app-type *" #> consumer.appType.get.toString &
      ".app-description *" #> consumer.description.get &
      ".app-developer *" #> consumer.developerEmail.get &
      ".auth-key *" #> consumer.key.get &
      ".secret-key *" #> consumer.secret.get &
      ".registration" #> ""
    }

    def saveAndShowResults(consumer : Consumer) = {
      consumer.isActive(true).
        key(Helpers.randomString(40).toLowerCase).
        secret(Helpers.randomString(40).toLowerCase).
        save

      notifyRegistrationOccurred(consumer)

      showResults(consumer)
    }

    def showErrors(errors : List[FieldError]) = {
      val errorsString = errors.map(_.msg.toString)
      register &
      "#registration-errors *" #> {
        ".error *" #>
          errorsString.map({ e=>
            ".errorContent *" #> e
        })
      }
    }

    def analyseResult = {
      val name = S.param("app-name") getOrElse ""
      val appType =
        S.param("app-type").flatMap(
          typeString => Consumer.appType.enum.values.find(_.toString == typeString)
        ) getOrElse Consumer.appType.enum.values.head

      val appDescription = S.param("app-description") getOrElse ""
      val appRedirectionUrl = S.param("app-user-authentication-url") getOrElse ""
      val developerEmail = S.param("app-developer") getOrElse ""

      val consumer =
        Consumer.create.
          name(name).
          appType(appType).
          description(appDescription).
          developerEmail(developerEmail).
          userAuthenticationURL(appRedirectionUrl)

      val errors = consumer.validate
      nameVar.set(name)
      appTypeVar.set(appType)
      descriptionVar.set(appDescription)
      devEmailVar.set(developerEmail)
      redirectionURLVar.set(appRedirectionUrl)

      if(errors.isEmpty)
        saveAndShowResults(consumer)
      else
        showErrors(errors)
    }

    if(S.post_?) analyseResult
    else registerWithoutWarnings

  }


  def notifyRegistrationOccurred(registered : Consumer) = {
    import net.liftweb.util.Mailer
    import net.liftweb.util.Mailer._

    val mailSent = for {
      // e.g mail.api.consumer.registered.sender.address=no-reply@example.com
      from <- Props.get("mail.api.consumer.registered.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      // no spaces, comma separated e.g. mail.api.consumer.registered.notification.addresses=notify@example.com,notify2@example.com,notify3@example.com
      toAddressesString <- Props.get("mail.api.consumer.registered.notification.addresses") ?~ "Could not send mail: Missing props param for 'to'"
    } yield {

      val thisApiInstance = Props.get("hostname", "unknown host")
      val registrationMessage = s"New user signed up for API keys on $thisApiInstance. \n" +
      		s"Email: ${registered.developerEmail.get} \n" +
      		s"App name: ${registered.name.get} \n" +
      		s"App type: ${registered.appType.get.toString} \n" +
      		s"App description: ${registered.description.get}"

      //technically doesn't work for all valid email addresses so this will mess up if someone tries to send emails to "foo,bar"@example.com
      val to = toAddressesString.split(",").toList
      val toParams = to.map(To(_))
      val params = PlainMailBodyType(registrationMessage) :: toParams

      //this is an async call
      Mailer.sendMail(
        From(from),
        Subject(s"New API user registered on $thisApiInstance"),
        params :_*
      )
    }

    //if Mailer.sendMail wasn't called (note: this actually isn't checking if the mail failed to send as that is being done asynchronously)
    if(mailSent.isEmpty)
      this.logger.warn(s"API consumer registration failed: $mailSent")

  }

}