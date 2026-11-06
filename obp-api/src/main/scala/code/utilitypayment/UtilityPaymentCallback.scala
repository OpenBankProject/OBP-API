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

package code.utilitypayment

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

/**
 * Per-request callback registry for UTILITY transaction-requests. When a caller
 * supplies a `callback_url` on a UTILITY payment, OBP persists a row here and
 * fires a fire-and-forget POST of the final result to that URL via
 * [[UtilityCallbackDispatcher]].
 *
 * Distinct from the account-event webhook system (code.webhook.*): those are
 * standing, account-scoped subscriptions; this is a one-shot callback bound to
 * a single transaction request.
 */
object UtilityPaymentCallbacks extends SimpleInjector {
  val utilityPaymentCallback = new Inject(() => buildOne) {}

  def buildOne: UtilityPaymentCallbackProvider = DoobieUtilityPaymentCallbackProvider
}

object UtilityCallbackStatus {
  val Registered = "REGISTERED"
  val Delivered  = "DELIVERED"
  val Failed     = "FAILED"
}

trait UtilityPaymentCallbackProvider {
  def createCallback(
    callbackId: String,
    transactionRequestId: String,
    callbackUrl: String,
    identifierType: String,
    identifier: String,
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String
  ): Box[UtilityPaymentCallbackTrait]

  def getCallbackByTransactionRequestId(transactionRequestId: String): Box[UtilityPaymentCallbackTrait]

  /** Record a delivery attempt outcome (increments the attempt counter). */
  def recordAttempt(
    callbackId: String,
    status: String,
    responseCode: Option[String]
  ): Box[UtilityPaymentCallbackTrait]
}

trait UtilityPaymentCallbackTrait {
  def callbackId: String
  def transactionRequestId: String
  def callbackUrl: String
  def identifierType: String
  def identifier: String
  def fromBankId: String
  def fromAccountId: String
  def createdByUserId: String
  def status: String
  def attempts: Int
  def responseCode: Option[String]
  def createdAt: java.util.Date
  def lastAttemptAt: Option[java.util.Date]
}

