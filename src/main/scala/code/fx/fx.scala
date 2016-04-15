package code.fx

import code.model.AmountOfMoney
import net.liftweb.common.{Full, Loggable}

/**
  * Created by simonredfern on 14/04/2016.
  */
object fx extends Loggable {

  // TODO fix this map!

//  val exchangeRates = {
//    Map(
//      "GBP" -> Map("EUR" -> 1.26, "USD" -> 1.41, "JPY" -> 154.68, "AED" -> 5.20, "INR" -> 94.28, "EUR" -> .1),
//      "EUR" -> Map("USD" -> 1.13, "JPY" -> 123.16, "AED" -> 4.14, "INR" -> 75.08, "EUR" -> 0.1, "USD" -> .1),
//      "USD" -> Map("JPY" -> 109.31, "AED" -> 3.67, "INR" -> 66.65, "EUR" -> 0.1, "USD" -> 1, "GBP" -> 0.1),
//      "JPY" -> Map("AED" -> 0.034, "INR" -> 0.61, "EUR" -> 0.0081, "USD" -> 0.0091, "JPY" -> 1),
//      "AED" -> Map("INR" -> 18.14, "EUR" -> 0.24, "USD" -> 0.27, "JPY" -> 29.76, "GBP" -> 0.1),
//      "INR" -> Map("EUR" -> 0.013, "USD" -> 0.015, "JPY" -> 1.64, "AED" -> 0.055, "GBP" -> 0.1)
//    )
//  }





  val exchangeRates = {
    Map(
      "AED" -> Map("INR" -> 1.26),
      "USD" -> Map("INR" -> 1.13)
    )
  }



  def exchangeRate(fromCurrency: String, toCurrency: String): Option[Double] = {

    if (fromCurrency == toCurrency) {
      Some(1)
    } else {

      //logger.debug(s"fromAmount is $fromAmount, toCurrency is ${toCurrency}")
      val rate: Option[Double] = try {
        // Get the translated name out of the map
        Some(exchangeRates.get(fromCurrency).get(toCurrency))


      }
      catch {
        case e: NoSuchElementException => None
      }
      rate
    }
  }

}



