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

import java.io.IOException

import code.util.Helper.MdcLoggable
import okhttp3._

/**
 * Fire-and-forget delivery of UTILITY payment callbacks.
 *
 * On a UTILITY transaction-request that carried a `callback_url`, the endpoint
 * persists a [[UtilityPaymentCallback]] row and calls [[deliver]] with the final
 * result payload. We POST asynchronously and record the outcome on the row; the
 * caller's request is never blocked on the callback, and a failed/unreachable
 * callback URL does not fail the payment.
 */
object UtilityCallbackDispatcher extends MdcLoggable {

  private val client = new OkHttpClient
  private val jsonType = MediaType.parse("application/json; charset=utf-8")

  /**
   * @param callbackId the persisted UtilityPaymentCallback.CallbackId
   * @param callbackUrl absolute URL to POST the result to
   * @param payload     JSON body (already rendered)
   */
  def deliver(callbackId: String, callbackUrl: String, payload: String): Unit = {
    val body = RequestBody.create(jsonType, payload)
    val request = new Request.Builder().url(callbackUrl).post(body).build()
    try {
      client.newCall(request).enqueue(new Callback() {
        def onFailure(call: Call, e: IOException): Unit = {
          logger.warn(s"[UtilityCallbackDispatcher] delivery failed for callbackId=$callbackId url=$callbackUrl: ${e.getMessage}")
          UtilityPaymentCallbacks.utilityPaymentCallback.vend
            .recordAttempt(callbackId, UtilityCallbackStatus.Failed, None)
        }

        def onResponse(call: Call, response: Response): Unit = {
          val responseBody = response.body
          try {
            val code = response.code()
            val status = if (response.isSuccessful) UtilityCallbackStatus.Delivered else UtilityCallbackStatus.Failed
            logger.debug(s"[UtilityCallbackDispatcher] callbackId=$callbackId url=$callbackUrl responded $code")
            UtilityPaymentCallbacks.utilityPaymentCallback.vend
              .recordAttempt(callbackId, status, Some(code.toString))
          } finally if (responseBody != null) responseBody.close()
        }
      })
    } catch {
      // Malformed URL or client-level failure — record and swallow; never fail the payment.
      case e: Exception =>
        logger.warn(s"[UtilityCallbackDispatcher] could not enqueue callbackId=$callbackId url=$callbackUrl: ${e.getMessage}")
        UtilityPaymentCallbacks.utilityPaymentCallback.vend
          .recordAttempt(callbackId, UtilityCallbackStatus.Failed, None)
    }
  }
}
