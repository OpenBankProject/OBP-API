package code.api.util

import net.liftweb.common.Full
import net.liftweb.http.LiftRules
import net.liftweb.json.parse

object CurrencyUtil {
  implicit val formats = CustomJsonFormats.formats
  case class CurrenciesJson(currencies: List[CurrencyJson])
  case class CurrencyJson(entity: String,
                          currency: String,
                          alphanumeric_code: Option[String],
                          number_code: Option[Int],
                          minor_unit: Option[String]
                        )
  def getCurrencies(): Option[CurrenciesJson] = {
    val filename = s"/currency/currency.json"
    val source = LiftRules.loadResourceAsString(filename)
    
    source match {
      case Full(payload) =>
        val currencies = parse(payload).extract[CurrenciesJson]
        Some(currencies)
      case _ =>  None
    }
  }
  
  def getCurrencyCodes(): List[String] = {
    getCurrencies.map(_.currencies
      .filter(_.alphanumeric_code.isDefined)
      .map(_.alphanumeric_code.getOrElse(""))).headOption.getOrElse(Nil)
  }

  def main(args: Array[String]): Unit = {
    println(getCurrencyCodes())
  }
}
