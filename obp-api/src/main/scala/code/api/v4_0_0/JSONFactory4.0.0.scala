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

import java.util.Date

import code.api.util.APIUtil
import code.api.util.APIUtil.stringOrNull
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestChargeJsonV200
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.ACCOUNT_OTP
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, Bank, TransactionRequest, TransactionRequestBodyAllTypes}

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


case class ChallengeJsonV400(
                              id: String,
                              allowed_attempts : Int,
                              challenge_type: String,
                              link: String
                             )

case class TransactionRequestWithChargeJSON400(
                                                id: String,
                                                `type`: String,
                                                from: TransactionRequestAccountJsonV140,
                                                details: TransactionRequestBodyAllTypes,
                                                transaction_ids: List[String],
                                                status: String,
                                                start_date: Date,
                                                end_date: Date,
                                                challenge: ChallengeJsonV400,
                                                charge : TransactionRequestChargeJsonV200
                                              )
case class PostResetPasswordUrlJsonV400(username: String, email: String, user_id: String)
case class ResetPasswordUrlJsonV400(reset_password_url: String)

case class APIInfoJson400(
                        version : String,
                        version_status: String,
                        git_commit : String,
                        connector : String,
                        hosted_by : HostedBy400,
                        hosted_at : HostedAt400,
                        energy_source : EnergySource400
                      )
case class HostedBy400(
                     organisation : String,
                     email : String,
                     phone : String,
                     organisation_website: String
                   )
case class HostedAt400(
                     organisation : String,
                     organisation_website: String
                   )
case class EnergySource400(
                         organisation : String,
                         organisation_website: String
                       )

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

  def createTransactionRequestWithChargeJSON(tr : TransactionRequest) : TransactionRequestWithChargeJSON400 = {
    new TransactionRequestWithChargeJSON400(
      id = stringOrNull(tr.id.value),
      `type` = stringOrNull(tr.`type`),
      from = try{TransactionRequestAccountJsonV140 (
        bank_id = stringOrNull(tr.from.bank_id),
        account_id = stringOrNull(tr.from.account_id)
      )} catch {case _ : Throwable => null},
      details = try{tr.body} catch {case _ : Throwable => null},
      transaction_ids = tr.transaction_ids::Nil,
      status = stringOrNull(tr.status),
      start_date = tr.start_date,
      end_date = tr.end_date,
      // Some (mapped) data might not have the challenge. TODO Make this nicer
      challenge = {
        try {
          val otpViaWebFormPath = APIUtil.getPropsValue("hostname", "") + List(
            "/otp?flow=transaction_request&bankId=",
            stringOrNull(tr.from.bank_id),
            "&accountId=",
            stringOrNull(tr.from.account_id),
            "&viewId=owner",
            "&transactionRequestType=",
            stringOrNull(tr.`type`),
            "&transactionRequestId=",
            stringOrNull(tr.id.value),
            "&id=",
            stringOrNull(tr.challenge.id)
          ).mkString("")
          
          val otpViaApiPath = APIUtil.getPropsValue("hostname", "") + List(
            "/obp/v4.0.0/banks/",
            stringOrNull(tr.from.bank_id),
            "/accounts/",
            stringOrNull(tr.from.account_id),
            "/owner",
            "/transaction-request-types/",
            stringOrNull(tr.`type`),
            "/transaction-requests/challenge").mkString("")
          val link = tr.challenge.challenge_type match  {
            case challengeType if challengeType == TransactionChallengeTypes.OTP_VIA_WEB_FORM.toString => otpViaWebFormPath
            case challengeType if challengeType == TransactionChallengeTypes.OTP_VIA_API.toString => otpViaApiPath
            case _ => ""
          }  
          ChallengeJsonV400(id = stringOrNull(tr.challenge.id), allowed_attempts = tr.challenge.allowed_attempts, challenge_type = stringOrNull(tr.challenge.challenge_type), link = link)
        }
        // catch { case _ : Throwable => ChallengeJSON (id = "", allowed_attempts = 0, challenge_type = "")}
        catch { case _ : Throwable => null}
      },
      charge = try {TransactionRequestChargeJsonV200 (summary = stringOrNull(tr.charge.summary),
        value = AmountOfMoneyJsonV121(currency = stringOrNull(tr.charge.value.currency),
          amount = stringOrNull(tr.charge.value.amount))
      )} catch {case _ : Throwable => null}
    )
  }
}

