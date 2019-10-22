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
