/**
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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
import net.liftweb.common.Full
import net.liftweb.http.SHtml
import net.liftweb.http.RequestVar
import net.liftweb.util.FieldError
import net.liftweb.util.Helpers

class ConsumerRegistration {

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""

  private object nameVar extends RequestVar("")
  private object appTypeVar extends RequestVar[Consumer.appType.enum.AppType](Consumer.appType.enum.values.head)
  private object descriptionVar extends RequestVar("")
  private object devEmailVar extends RequestVar("")

  def registerForm = {

    def registerWithoutWarnings =
      register &
      ".registration-error" #> ""

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
    	"name=app-description *" #> descriptionVar.get &
    	"name=app-developer [value]" #> devEmailVar.get
      } &
      ".success" #> ""
    }

    def showResults(consumer : Consumer) = {
      //thanks for registering, here's your key, etc.
      ".app-name *" #> consumer.name.get &
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
      showResults(consumer)
    }

    def showErrors(errors : List[FieldError]) = {
      register &
      "#registration-error *" #> errors.map(_.msg.toString).mkString(", ")
    }

    def analyseResult = {
      val name = S.param("app-name") getOrElse ""
      val appType =
        S.param("app-type").flatMap(
          typeString => Consumer.appType.enum.values.find(_.toString == typeString)
        ) getOrElse Consumer.appType.enum.values.head

      val appDescription = S.param("app-description") getOrElse ""
      val developerEmail = S.param("app-developer") getOrElse ""

      val consumer =
        Consumer.create.
          name(name).
          appType(appType).
          description(appDescription).
          developerEmail(developerEmail)

      val errors = consumer.validate
      nameVar.set(name)
      appTypeVar.set(appType)
      descriptionVar.set(appDescription)
      devEmailVar.set(developerEmail)

      if(errors.isEmpty)
        saveAndShowResults(consumer)
      else
        showErrors(errors)
    }

    if(S.post_?) analyseResult
    else register

  }

}