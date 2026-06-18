package code.api.util

import org.json4s._
import com.openbankproject.commons.util.JsonAliases.parse

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
    Option(getClass.getResourceAsStream(filename))
      .map(is => scala.io.Source.fromInputStream(is, "UTF-8").mkString)
      .map(payload => parse(payload).extract[CurrenciesJson])
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
