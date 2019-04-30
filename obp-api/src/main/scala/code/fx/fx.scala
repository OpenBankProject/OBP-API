package code.fx

import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.{APIUtil, CustomJsonFormats}
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.BankId
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.Full
import net.liftweb.http.LiftRules
import net.liftweb.json._

import scala.concurrent.duration._

/**
  * Simple map of exchange rates.
  *
  * One pound -> X Euros etc.
  */
object fx extends MdcLoggable {

  val TTL = APIUtil.getPropsAsIntValue("code.fx.exchangeRate.cache.ttl.seconds", 0)

  // TODO For sandbox purposes we only need rough exchanges rates.
  // Make this easier

  //get data from : http://www.xe.com/de/currencyconverter/convert/?Amount=1&From=AUD&To=EUR
  val fallbackExchangeRates = {
    Map(
      "GBP" -> Map("EUR" -> 1.16278,     "USD" -> 1.24930,     "JPY" -> 141.373,     "AED" -> 4.58882,   "INR" -> 84.0950,    "KRW" -> 1433.92,   "XAF" -> 762.826,  "JOD" -> 0.936707,    "ILS" -> 4.70020,   "AUD" -> 1.63992   ,"HKD" -> 10.1468 ),
      "EUR" -> Map("GBP" -> 0.860011,    "USD" -> 1.07428,     "JPY" -> 121.567,     "AED" -> 3.94594,   "INR" -> 72.3136,    "KRW" -> 1233.03,   "XAF" -> 655.957,  "JOD" -> 0.838098,    "ILS" -> 4.20494,   "AUD" -> 1.49707   ,"HKD" -> 8.88926 ),
      "USD" -> Map("GBP" -> 0.800446,    "EUR" -> 0.930886,    "JPY" -> 113.161,     "AED" -> 3.67310,   "INR" -> 67.3135,    "KRW" -> 1147.78,   "XAF" -> 610.601,  "JOD" -> 0.708659,    "ILS" -> 3.55495,   "AUD" -> 1.27347   ,"HKD" -> 7.84766 ),
      "JPY" -> Map("GBP" -> 0.00707350,  "EUR" -> 0.00822592,  "USD" -> 0.00883695,  "AED" -> 0.0324590, "INR" -> 0.594846,   "KRW" -> 10.1428,   "XAF" -> 5.39585,  "JOD" -> 0.00639777,  "ILS" -> 0.0320926, "AUD" -> 0.0114819 ,"HKD" -> 0.0709891),
      "AED" -> Map("GBP" -> 0.217921,    "EUR" -> 0.253425,    "USD" -> 0.272250,    "JPY" -> 30.8081,   "INR" -> 18.3255,    "KRW" -> 312.482,   "XAF" -> 166.236,  "AED" -> 0.192964,    "ILS" -> 0.968033,  "AUD" -> 0.346779  ,"HKD" -> 2.13685 ),
      "INR" -> Map("GBP" -> 0.0118913,   "EUR" -> 0.0138287,   "USD" -> 0.0148559,   "JPY" -> 1.68111,   "AED" -> 0.0545671,  "KRW" -> 17.0512,   "XAF" -> 9.07101,  "JOD" -> 0.0110959 ,  "ILS" -> 0.0556764, "AUD" -> 0.0198319 ,"HKD" -> 0.109972 ),
      "KRW" -> Map("GBP" -> 0.000697389, "EUR" -> 0.000811008, "USD" -> 0.000871250, "JPY" -> 0.0985917, "AED" -> 0.00320019, "INR" -> 0.0586469, "XAF" -> 0.531986, "JOD" -> 0.000630634, "ILS" -> 0.00316552,"AUD" -> 0.00111694,"HKD" -> 0.00697233 ),
      "XAF" -> Map("GBP" -> 0.00131092,  "EUR" -> 0.00152449,  "USD" -> 0.00163773,  "JPY" -> 0.185328,  "AED" -> 0.00601555, "INR" -> 0.110241,  "KRW" -> 1.87975,  "JOD" -> 0.00127784,  "ILS" -> 0.00641333,"AUD" -> 0.00228226,"HKD" -> 0.0135503 ),
      "JOD" -> Map("GBP" -> 1.06757,     "EUR" -> 0.237707,    "USD" -> 1.41112,     "JPY" -> 156.304,   "AED" -> 5.18231,    "INR" -> 90.1236,   "KRW" -> 1585.68,  "XAF" -> 782.572,     "ILS" -> 5.02018,   "AUD" -> 1.63992   ,"HKD" -> 11.0687 ),
      "ILS" -> Map("GBP" -> 0.212763,    "EUR" -> 1.19318,     "USD" -> 0.281298,    "JPY" -> 31.1599,   "AED" -> 1.03302,    "INR" -> 17.9609,   "KRW" -> 315.903,  "XAF" -> 155.925,     "JOD" -> 0.199196,  "AUD" -> 0.352661  ,"HKD" -> 2.16985 ),
      "AUD" -> Map("GBP" -> 0.609788,    "EUR" -> 0.667969,    "USD" -> 0.785256,    "JPY" -> 87.0936,   "AED" -> 2.88368,    "INR" -> 50.4238,   "KRW" -> 895.304,  "XAF" -> 438.162,     "JOD" -> 0.556152,  "ILS" -> 2.83558   ,"HKD" -> 5.61346 ),
      "HKD" -> Map("GBP" -> 0.0985443,   "EUR" -> 0.112495,    "USD" -> 0.127427,    "JPY" -> 14.0867,   "AED" -> 0.467977,   "INR" -> 9.09325,   "KRW" -> 143.424,  "XAF" -> 73.8049,     "JOD" -> 0.0903452,  "ILS" -> 0.460862 ,"AUD" -> 0.178137 )
    )
  }
  
  
  def getFallbackExchangeRateCached(fromCurrency: String, toCurrency: String): Option[Double] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(TTL seconds) {
        getFallbackExchangeRate(fromCurrency, toCurrency)
      }
    }
  }
  def getFallbackExchangeRate(fromCurrency: String, toCurrency: String): Option[Double] = {
    case class ExchangeRate(
      code: String,
      alphaCode: String,
      numericCode: String,
      name: String,
      rate: Double,
      date: String,
      inverseRate: Double
    )
    implicit val formats = CustomJsonFormats.formats
    fromCurrency == toCurrency match {
      case true => 
        Some(1)
      case false =>
        val filename = s"/fallbackexchangerates/${fromCurrency.toLowerCase}.json"
        val source = LiftRules.loadResourceAsString(filename)
        source match {
          case Full(payload) =>
            val fxRate: ExchangeRate = (parse(payload) \ toCurrency.toLowerCase()).extract[ExchangeRate]
            Some(fxRate.rate)
          case _ =>
            val filename = s"/fallbackexchangerates/${toCurrency.toLowerCase}.json"
            val source = LiftRules.loadResourceAsString(filename)
            source match {
              case Full(payload) =>
                val fxRate: ExchangeRate = (parse(payload) \ fromCurrency.toLowerCase()).extract[ExchangeRate]
                Some(fxRate.inverseRate)
              case _ =>
                logger.debug(s"getFallbackExchangeRate Could not find / load $filename")
                None
            }
            
        }
    }
    
  }

  def getFallbackExchangeRate2nd(fromCurrency: String, toCurrency: String): Option[Double] = {
    if (fromCurrency == toCurrency) {
      Some(1)
    } else {
      //logger.debug(s"fromAmount is $fromAmount, toCurrency is ${toCurrency}")
      val rate: Option[Double] = try {
        // Get the translated name out of the map
        Some(fallbackExchangeRates.get(fromCurrency).get(toCurrency))
      }
      catch {
        case e: NoSuchElementException => None
      }
      rate
    }
  }

  def convert(amount: BigDecimal, exchangeRate: Option[Double]): BigDecimal = {
    val result = amount * exchangeRate.get // TODO handle if None
    result.setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }

  /** 
    * Exchange rate workflow:
    * 
    *                            1st try                                    2nd try                                    3rd try
                            +---------------+                +----------------------------------+              +----------------------+
       Get Exchange Rate    |               |  no match      |                                  |  no match    |                      |
      +-------------------->+    Connector  +--------------->+  resources/fallbackexchangerates +------------->+    hard coded Map    |
                            |               |                |         json files               |              |                      |
                            +-------+-------+                +------------------+---------------+              +----------+-----------+
                                    |                                           |                                         |
                                    | match                                     | match                                   | match
        CBS response                |                                           |                                         |
      <-----------------------------+                                           |                                         |
        OBP response                                                            |                                         |
      <-------------------------------------------------------------------------+                                         |
        OBP response                                                                                                      |
      <-------------------------------------------------------------------------------------------------------------------+

    */
  def exchangeRate(fromCurrency: String, toCurrency: String, bankId: Option[String] = None): Option[Double] = {
    bankId match {
      case None =>
        getFallbackExchangeRateCached(fromCurrency, toCurrency).orElse(getFallbackExchangeRate2nd(fromCurrency, toCurrency))
      case Some(id) =>
        Connector.connector.vend.getCurrentFxRateCached(BankId(id), fromCurrency, toCurrency).map(_.conversionValue).toOption match {
          case None =>
            getFallbackExchangeRateCached(fromCurrency, toCurrency).orElse(getFallbackExchangeRate2nd(fromCurrency, toCurrency))
          case exchangeRate => exchangeRate
        }
    }
  }
  

  def main (args: Array[String]): Unit = {
    org.scalameta.logger.elem(exchangeRate("USD", "EUR"))
  }

}



