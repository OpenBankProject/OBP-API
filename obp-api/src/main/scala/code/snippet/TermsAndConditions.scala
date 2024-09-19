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

import code.model.dataAccess.AuthUser
import code.users.UserAgreementProvider
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import net.liftweb.http.{S, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._

class TermsAndConditions extends MdcLoggable {

  def updateForm: CssSel = {

    def submitButtonDefense(): Unit = {
      updateUserAgreement()
    }

    def skipButtonDefense(): Unit = {
      S.redirectTo("/")
    }

    def displayContent = {
      if(AuthUser.currentUser.isDefined) {
        "block"
      } else {
        "none"
      }
    }

    def update = {
      val username = AuthUser.currentUser.flatMap(_.user.foreign.map(_.name)).getOrElse("")
      "#terms-and-conditions-username *" #> username &
      "type=submit" #> SHtml.submit(s"${Helper.i18n("outdated.policy.button.accept")}", () => submitButtonDefense) &
      "type=reset" #> SHtml.submit(s"${Helper.i18n("outdated.policy.button.skip")}", () => skipButtonDefense) &
        "#form_terms_and_conditions [style]" #> s"display: $displayContent;"
    }
    update
  }

  private def updateUserAgreement() = {
    if(AuthUser.currentUser.isDefined) {
      val agreementText = getWebUiPropsValue("webui_terms_and_conditions", "not set")
      // val hashedAgreementText = HashUtil.Sha256Hash(agreementText)
      UserAgreementProvider.userAgreementProvider.vend.createUserAgreement(
        AuthUser.currentUser.flatMap(_.user.foreign.map(_.userId)).getOrElse(""),
        "terms_and_conditions",
        agreementText)
      S.redirectTo("/")
    }
  }
  
}
