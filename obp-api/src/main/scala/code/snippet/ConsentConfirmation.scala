/**
 * Open Bank Project - API
 * Copyright (C) 2011-2019, TESOBE GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Email: contact@tesobe.com
 * TESOBE GmbH.
 * Osloer Strasse 16/17
 * Berlin 13359, Germany
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 *
 */
package code.snippet

import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue
import net.liftweb.http.{RequestVar, S, SHtml}
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._
import sh.ory.hydra.model.{AcceptConsentRequest, ConsentRequestSession, RejectRequest}

import scala.jdk.CollectionConverters.{asScalaBufferConverter, seqAsJavaListConverter}


class ConsentConfirmation extends MdcLoggable {

  private object submitButtonDefenseFlag extends RequestVar("")

  private object cancelButtonDefenseFlag extends RequestVar("")


  val confirmConsentButtonValue = getWebUiPropsValue("webui_post_confirm_consent_submit_button_value", "Yes, I confirm")
  val rejectConsentButtonValue = getWebUiPropsValue("webui_post_reject_consent_submit_button_value", "Cancel")

  def registerForm: CssSel = {

    def submitButtonDefense: Unit = {
      submitButtonDefenseFlag("true")
    }

    def cancelButtonDefense: Unit = {
      cancelButtonDefenseFlag("true")
    }


    def formElement(ele: => CssSel): CssSel =
      "form" #> {
        "type=submit" #> SHtml.submit(s"$confirmConsentButtonValue", () => submitButtonDefense) &
          "type=button" #> SHtml.submit(s"$rejectConsentButtonValue", () => cancelButtonDefense) &
          ele
      }


    val consentChallengeBox = S.param("consent_challenge")
    if (consentChallengeBox.isEmpty) {
      return formElement {
        "#confirm-errors" #> "Please login first."
      }
    }
    val consentChallenge = consentChallengeBox.orNull
    if (cancelButtonDefenseFlag.get == "true") {
      val rejectRequest = new RejectRequest()
      rejectRequest.setError("access_denied")
      rejectRequest.setErrorDescription("The resource owner denied the request")
      val rejectResponse = AuthUser.hydraAdmin.rejectConsentRequest(consentChallenge, rejectRequest)
      AuthUser.logUserOut()
      return S.redirectTo(rejectResponse.getRedirectTo)
    }

    val consentResponse = AuthUser.hydraAdmin.getConsentRequest(consentChallenge)

    if (S.post_?) {
      val scopes = S.params("consent_scope")
      val consentRequest = new AcceptConsentRequest()
      consentRequest.setGrantScope(scopes.asJava)
      consentRequest.setGrantAccessTokenAudience(consentResponse.getRequestedAccessTokenAudience)
      consentRequest.setRemember(false)
      consentRequest.setRememberFor(3600) // TODO set in props
      consentRequest.setSession(new ConsentRequestSession())

      val acceptConsentResponse = AuthUser.hydraAdmin.acceptConsentRequest(consentChallenge, consentRequest)
      S.redirectTo(acceptConsentResponse.getRedirectTo)
    } else {
      if (consentResponse.getSkip) {
        val requestBody = new AcceptConsentRequest()
        requestBody.setGrantScope(consentResponse.getRequestedScope)
        requestBody.setGrantAccessTokenAudience(consentResponse.getRequestedAccessTokenAudience)
        val requestSession = new ConsentRequestSession()
        requestBody.setSession(requestSession)
        val skipResponse = AuthUser.hydraAdmin.acceptConsentRequest(consentChallenge, requestBody)
        S.redirectTo(skipResponse.getRedirectTo)
      } else {
        formElement {
          "#confirm-errors" #> "" &
            "#consent_challenge [value]" #> consentChallenge &
            "#scope_group" #> consentResponse.getRequestedScope.asScala.map { scope =>
              "@consent_scope [value]" #> scope &
                "@consent_scope_label [for]" #> scope &
                "@consent_scope_label *" #> scope
            }
        }

      }
    }
  }
}
