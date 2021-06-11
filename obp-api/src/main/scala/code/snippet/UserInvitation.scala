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

import code.api.util.ErrorMessages
import code.users.UserInvitationProvider
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.BankId
import net.liftweb.common.Box
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

import scala.collection.immutable.List

class UserInvitation extends MdcLoggable {

  private object firstNameVar extends RequestVar("")
  private object lastNameVar extends RequestVar("")
  private object companyVar extends RequestVar("")
  private object countryVar extends RequestVar("None")
  private object devEmailVar extends RequestVar("")
  
  // Can be used to show link to an online form to collect more information about the App / Startup
  val registrationMoreInfoUrl = getWebUiPropsValue("webui_post_user_invitation_more_info_url", "")
  
  val registrationConsumerButtonValue: String = getWebUiPropsValue("webui_post_user_invitation_submit_button_value", "Proceed")

  
  def registerForm: CssSel = {

    val countries = List(("None", "None"), ("Bahrain", "Bahrain"), ("Germany", "Germany"), ("Mexico", "Mexico"), ("UK", "UK"))
    val secretLink = S.param("id").getOrElse("0")
    val userInvitation = UserInvitationProvider.userInvitationProvider.vend.getUserInvitationBySecretLink(secretLink.toLong)
    firstNameVar.set(userInvitation.map(_.firstName).getOrElse("None"))
    lastNameVar.set(userInvitation.map(_.lastName).getOrElse("None"))
    devEmailVar.set(userInvitation.map(_.email).getOrElse("None"))
    companyVar.set(userInvitation.map(_.company).getOrElse("None"))
    countryVar.set(userInvitation.map(_.country).getOrElse("Bahrain"))

    def submitButtonDefense: Unit = {
      
    }

    def register = {
      "form" #> {
          "#country" #> SHtml.select(countries, Box!! countryVar.is, countryVar(_)) &
          "#firstName" #> SHtml.text(firstNameVar.is, firstNameVar(_)) &
          "#lastName" #> SHtml.text(lastNameVar.is, lastNameVar(_)) &
          "#companyName" #> SHtml.text(companyVar.is, companyVar(_)) &
          "#devEmail" #> SHtml.text(devEmailVar, devEmailVar(_)) &
          "type=submit" #> SHtml.submit(s"$registrationConsumerButtonValue", () => submitButtonDefense)
      } &
      "#register-consumer-success" #> ""
    }
    register

  }
  
}
