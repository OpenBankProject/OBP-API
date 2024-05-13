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

import code.api.Constant
import code.api.Constant.localIdentityProvider
import code.api.util.{APIUtil, HashUtil, SecureRandomUtil}
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users
import code.users.{UserAgreementProvider, UserInvitationProvider, Users}
import code.util.Helper
import code.util.Helper.{MdcLoggable, ObpS}
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

import java.time.{Duration, ZoneId, ZoneOffset, ZonedDateTime}
import java.util.Date

class TermsAndConditions extends MdcLoggable {

  private object firstNameVar extends RequestVar("")
  private object lastNameVar extends RequestVar("")
  private object companyVar extends RequestVar("")
  private object countryVar extends RequestVar("None")
  private object devEmailVar extends RequestVar("")
  private object usernameVar extends RequestVar("")
  private object consentForCollectingMandatoryCheckboxVar extends RequestVar(true)
  private object privacyCheckboxVar extends RequestVar(false)

  def updateForm: CssSel = {

    def submitButtonDefense(): Unit = {
      updateUserAgreement()
    }

    def skipButtonDefense(): Unit = {
      S.redirectTo("/")
    }

    def update = {
      "type=submit" #> SHtml.submit(s"Accept", () => submitButtonDefense) &
      "type=reset" #> SHtml.submit(s"Skip", () => skipButtonDefense)
    }
    update
  }

  private def updateUserAgreement() = {
    if(AuthUser.currentUser.isDefined) {
      val agreementText = getWebUiPropsValue("webui_terms_and_conditions", "not set")
      // val hashedAgreementText = HashUtil.Sha256Hash(agreementText)
      UserAgreementProvider.userAgreementProvider.vend.createOrUpdateUserAgreement(
        AuthUser.currentUser.flatMap(_.user.foreign.map(_.userId)).getOrElse(""), "terms_and_conditions", agreementText)
      S.redirectTo("/")
    }

  }
  
}
