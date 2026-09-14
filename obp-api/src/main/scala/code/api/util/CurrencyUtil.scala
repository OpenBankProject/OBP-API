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
