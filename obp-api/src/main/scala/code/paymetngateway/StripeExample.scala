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

package code.paymetngateway

import java.util
import java.util.Map

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.PaymentIntent


object StripeExample {
  def main(args: Array[String]): Unit = {
    // Set your secret key: remember to change this to your live secret key in production
    // See your keys here: https://dashboard.stripe.com/account/apikeys
    Stripe.apiKey = "sk_test_ksWQ0..."
    val paymentIntentParams = new util.HashMap[String, Any]
    paymentIntentParams.put("amount", 999)
    
    paymentIntentParams.put("currency", "sek")
    val payment_method_types = new util.ArrayList[String]
    payment_method_types.add("card")
    paymentIntentParams.put("payment_method_types", payment_method_types)
    paymentIntentParams.put("receipt_email", "marko@tesobe.com")

    try {
      val paymentIntent = PaymentIntent.create(paymentIntentParams.asInstanceOf[Map[String, Object]])
      println(paymentIntent)
    } catch {
      case e: StripeException => e.printStackTrace()
    }
  }
}
