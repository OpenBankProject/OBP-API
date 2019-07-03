/**
  * Open Bank Project - API
  * Copyright (C) 2011-2018, TESOBE Ltd
  * *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  * *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.
  * *
  * You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  * *
  * Email: contact@tesobe.com
  * TESOBE Ltd
  * Osloerstrasse 16/17
  * Berlin 13359, Germany
  * *
  * This product includes software developed at
  * TESOBE (http://www.tesobe.com/)
  *
  */
package code.api.v4_0_0

import code.api.util.APIUtil.stringOrNull
import code.api.v1_2_1.BankRoutingJsonV121
import com.openbankproject.commons.model.Bank

import scala.collection.immutable.List

case class BankJson400(
                        id: String,
                        short_name: String,
                        full_name: String,
                        logo: String,
                        website: String,
                        bank_routings: List[BankRoutingJsonV121]
                      )

case class BanksJson400(banks: List[BankJson400])

object JSONFactory400 {
  def createBankJSON400(bank: Bank): BankJson400 = {
    val obp = BankRoutingJsonV121("OBP", bank.bankId.value)
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val routings = bank.bankRoutingScheme match {
      case "OBP" => bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case "BIC" => obp :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case _ => obp :: bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
    }
    new BankJson400(
      stringOrNull(bank.bankId.value),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoUrl),
      stringOrNull(bank.websiteUrl),
      routings.filter(a => stringOrNull(a.address) != null)
    )
  }

  def createBanksJson(l: List[Bank]): BanksJson400 = {
    BanksJson400(l.map(createBankJSON400))
  }
}

