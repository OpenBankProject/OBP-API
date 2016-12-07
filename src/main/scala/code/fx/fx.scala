package code.fx

import net.liftweb.common.Loggable

/**
  * Simple map of exchange rates.
  *
  * One pound -> X Euros etc.
  */
object fx extends Loggable {

  // TODO For sandbox purposes we only need rough exchanges rates.
  // Make this easier

  val exchangeRates = {
    Map(
      "GBP" -> Map("EUR" -> 1.26,    "USD" -> 1.42,    "JPY" -> 154.47, "AED" -> 5.22,   "INR" -> 94.66,  "KRW" -> 1409.23, "XAF" -> 1.0),
      "EUR" -> Map("USD" -> 1.13,    "JPY" -> 122.71,  "AED" -> 4.14,   "INR" -> 75.20,  "GBP" -> 0.79,   "KRW" -> 1254.34, "XAF" -> 1.0),
      "USD" -> Map("JPY" -> 108.77,  "AED" -> 3.67,    "INR" -> 66.65,  "GBP" -> 0.70,   "EUR" -> 0.89,   "KRW" -> 1135.18, "XAF" -> 1.0),
      "JPY" -> Map("AED" -> 0.034,   "INR" -> 0.61,    "GBP" -> 0.0065, "EUR" -> 0.0081, "USD" -> 0.0092, "KRW" -> 10.82,   "XAF" -> 1.0),
      "AED" -> Map("INR" -> 18.15,   "GBP" -> 0.19,    "EUR" -> 0.24,   "USD" -> 0.27,   "JPY" -> 29.61,  "KRW" -> 309.09,  "XAF" -> 1.0),
      "INR" -> Map("GBP" -> 0.011,   "EUR" -> 0.013,   "USD" -> 0.015,  "JPY" -> 1.63,   "AED" -> 0.055,  "KRW" -> 17.11,   "XAF" -> 1.0),
      "KRW" -> Map("EUR" -> 0.00080, "USD" -> 0.00088, "JPY" -> 0.092,  "AED" -> 0.0032, "INR" -> 0.058,  "GBP" -> 0.00071, "XAF" -> 1.0),
      "XAF" -> Map("GBP" -> 1.0,     "EUR" -> 1.0,     "USD" -> 1.0,    "JPY" -> 1.0,    "AED" -> 1.0,    "INR" -> 1.0,     "KRW" -> 1.0)
    )
  }



  def convert(amount: BigDecimal, exchangeRate: Option[Double]): BigDecimal = {
    val result = amount * exchangeRate.get // TODO handle if None
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



