package code.fx

import net.liftweb.common.Loggable

/**
  * Created by simonredfern on 14/04/2016.
  */
object fx extends Loggable {

  val exchangeRates = {
    Map(
      "GBP" -> Map("EUR" -> 1.26, "USD" -> 1.42, "JPY" -> 154.47, "AED" -> 5.22, "INR" -> 94.66),
      "EUR" -> Map("USD" -> 1.13, "JPY" -> 122.71, "AED" -> 4.14, "INR" -> 75.20, "GBP" -> 0.79),
      "USD" -> Map("JPY" -> 108.77, "AED" -> 3.67, "INR" -> 66.65, "GBP" -> 0.70, "EUR" -> 0.89),
      "JPY" -> Map("AED" -> 0.034, "INR" -> 0.61, "GBP" -> 0.0065, "EUR" -> 0.0081, "USD" -> 0.0092),
      "AED" -> Map("INR" -> 18.15, "GBP" -> 0.19, "EUR" -> 0.24, "USD" -> 0.27, "JPY" -> 29.61),
      "INR" -> Map("GBP" -> 0.011, "EUR" -> 0.013, "USD" -> 0.015, "JPY" -> 1.63, "AED" -> 0.055)
    )
  }



  def convert(amount: BigDecimal, exchangeRate: Option[Double]): BigDecimal = {
    val result = amount * exchangeRate.get
    result.setScale(2, BigDecimal.RoundingMode.HALF_UP)
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



