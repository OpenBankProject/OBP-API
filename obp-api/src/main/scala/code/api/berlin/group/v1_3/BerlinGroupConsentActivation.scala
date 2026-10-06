/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.api.berlin.group.v1_3

import code.api.util.APIUtil.unboxFullOrFail
import code.api.util.ErrorMessages.{ConsentAccountAccessCannotBeGranted, ConsentUpdateStatusError}
import code.api.util.{CallContext, Consent}
import code.consent.{ConsentStatus, ConsentTrait, Consents}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User

import scala.concurrent.Future

/**
 * Makes a Berlin Group consent valid once the PSU's SCA for it has succeeded: grants the access an
 * "allAccounts" consent leaves open, binds the consent to the PSU, and marks it valid.
 *
 * The consent authorisation (PUT /consents/{id}/authorisations/{id}) performs the same steps inline,
 * interleaved with checking the answer. A signing basket checks the answer once for all its members and
 * then activates each consent here.
 *
 * It can be run again after a stop at any point. The status changes last, so a stop leaves the consent
 * `received`, with its access granted and perhaps already bound to the PSU, and running it again completes
 * it. A consent made valid by an earlier version of this method, which set the status before binding, and
 * left unbound by a stop between the two, is completed by binding it. A consent already valid and bound to
 * this PSU is returned as it is.
 */
object BerlinGroupConsentActivation {

  private def bound(consent: ConsentTrait): Option[String] = Consent.present(consent.userId)

  /** Whether activating this consent for this PSU can still lead to a valid consent bound to them. */
  def canActivate(consent: ConsentTrait, psuUserId: String): Boolean =
    consent.status == ConsentStatus.received.toString ||
      (consent.status == ConsentStatus.valid.toString && bound(consent).forall(_ == psuUserId))

  def activate(consent: ConsentTrait, psu: User, callContext: Option[CallContext]): Future[ConsentTrait] =
    if (consent.status == ConsentStatus.valid.toString) {
      bound(consent) match {
        case Some(user) if user == psu.userId => Future.successful(consent)
        // Valid but not bound: a stop between the two steps. Bind it; the access was granted before.
        case None => Consent.bindBerlinGroupConsentToPsu(consent.consentId, psu, callContext).map(_ => consent)
        case Some(_) => Future.failed(new IllegalStateException(s"The consent is already valid for another PSU"))
      }
    } else for {
      _ <- Consent.grantBerlinGroupAvailableAccountsAccess(psu, consent)
        .map(unboxFullOrFail(_, callContext, ConsentAccountAccessCannotBeGranted))
      _ <- Consent.bindBerlinGroupConsentToPsu(consent.consentId, psu, callContext)
      valid <- Future(Consents.consentProvider.vend.updateConsentStatus(consent.consentId, ConsentStatus.valid))
        .map(unboxFullOrFail(_, callContext, ConsentUpdateStatusError))
    } yield valid
}
