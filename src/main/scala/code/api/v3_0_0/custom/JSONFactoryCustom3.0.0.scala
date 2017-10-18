/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

 */
package code.api.v3_0_0.custom

import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.common._

//for create transaction request
case class ToAccountTransferToPhoneJson(
  mobile_phone_number: String
)

case class FromAccountTransfer (
  mobile_phone_number: String,
  nickname: String
)

case class TransactionRequestBodyTransferToPhoneJson(
  value: AmountOfMoneyJsonV121,
  description: String,
  message: String,
  from: FromAccountTransfer,
  to: ToAccountTransferToPhoneJson
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAtmKycDocumentJson(
  `type`: String,
  number: String
)

case class ToAccountTransferToAtmJson(
  legal_name: String,
  date_of_birth: String,
  mobile_phone_number: String,
  kyc_document: ToAccountTransferToAtmKycDocumentJson
)

case class TransactionRequestBodyTransferToAtmJson(
  value: AmountOfMoneyJsonV121,
  description: String,
  message: String,
  from: FromAccountTransfer,
  to: ToAccountTransferToAtmJson
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAccountAccountJson(
  number: String,
  iban: String
)

case class ToAccountTransferToAccountJson(
  name: String,
  bank_code: String,
  branch_number : String,
  account:ToAccountTransferToAccountAccountJson
)

case class TransactionRequestBodyTransferToAccount(
  value: AmountOfMoneyJsonV121,
  description: String,
  transfer_type: String,
  future_date: String,
  to: ToAccountTransferToAccountJson
) extends TransactionRequestCommonBodyJSON

object JSONFactory300{
}