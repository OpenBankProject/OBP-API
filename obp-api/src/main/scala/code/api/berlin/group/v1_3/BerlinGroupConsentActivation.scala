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
 * "allAccounts" consent leaves open, marks the consent valid, and binds it to the PSU.
 *
 * The consent authorisation (PUT /consents/{id}/authorisations/{id}) performs the same steps inline,
 * interleaved with checking the answer. A signing basket checks the answer once for all its members and
 * then activates each consent here. The order is the one the consent route documents: grant before the
 * status changes, so a consent never becomes valid without the access it names; bind last.
 *
 * Idempotent, so that a basket resumed after a stop can run it again: a consent already valid and bound
 * to this PSU is returned as it is.
 */
object BerlinGroupConsentActivation {

  def activate(consent: ConsentTrait, psu: User, callContext: Option[CallContext]): Future[ConsentTrait] =
    if (consent.status == ConsentStatus.valid.toString && consent.userId == psu.userId) Future.successful(consent)
    else for {
      _ <- Consent.grantBerlinGroupAvailableAccountsAccess(psu, consent)
        .map(unboxFullOrFail(_, callContext, ConsentAccountAccessCannotBeGranted))
      valid <- Future(Consents.consentProvider.vend.updateConsentStatus(consent.consentId, ConsentStatus.valid))
        .map(unboxFullOrFail(_, callContext, ConsentUpdateStatusError))
      _ <- Consent.bindBerlinGroupConsentToPsu(consent.consentId, psu, callContext)
    } yield valid
}
