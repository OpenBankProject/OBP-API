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

import code.api.util.APIUtil
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import code.util.HydraUtil.integrateWithHydra
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.Helpers._
import sh.ory.hydra.api.AdminApi
import sh.ory.hydra.model.{AcceptConsentRequest, RejectRequest}

import scala.jdk.CollectionConverters.seqAsJavaListConverter

class ConsentScreen extends MdcLoggable {

  private object skipConsentScreenVar extends RequestVar(false)
  private object consentChallengeVar extends RequestVar(S.param("consent_challenge").getOrElse(""))
  private object csrfVar extends RequestVar(S.param("_csrf").getOrElse(""))

  def submitAllowAction: Unit = {
    integrateWithHydra match {
      case true if !consentChallengeVar.isEmpty =>
        val acceptConsentRequestBody = new AcceptConsentRequest
        val adminApi: AdminApi = new AdminApi
        acceptConsentRequestBody.setRemember(skipConsentScreenVar.is)
        acceptConsentRequestBody.setRememberFor(0L)
        acceptConsentRequestBody.setGrantScope(List("openid").asJava)
        val completedRequest = adminApi.acceptConsentRequest(consentChallengeVar.is, acceptConsentRequestBody)
        S.redirectTo(completedRequest.getRedirectTo)
      case false =>
        S.redirectTo("/") // Home page
    }
  }
  
  def submitDenyAction: Unit = {
    integrateWithHydra match {
      case true if !consentChallengeVar.isEmpty =>
        val rejectRequestBody = new RejectRequest
        rejectRequestBody.setError("access_denied")
        rejectRequestBody.setErrorDescription("The resource owner denied the request")
        val adminApi: AdminApi = new AdminApi
        val completedRequest = adminApi.rejectConsentRequest(consentChallengeVar.is, rejectRequestBody)
        S.redirectTo("/") // Home page
      case false =>
        S.redirectTo("/") // Home page
    }
  }
  
  def consentScreenForm = {
    val username = AuthUser.getCurrentUser.map(_.name).getOrElse("")
    val clientId = getWebUiPropsValue("webui_hydra_oidc_client", "OpenID Connect Provider")
    "#username *" #> username &
      "#client *" #> clientId &
    "form" #> {
      "#skip_consent_screen_checkbox" #> SHtml.checkbox(skipConsentScreenVar, skipConsentScreenVar(_)) &
        "#allow_access_to_consent" #> SHtml.submit(s"Allow access", () => submitAllowAction) &
        "#deny_access_to_consent" #> SHtml.submit(s"Deny access", () => submitDenyAction)
    }
  }
  
}
