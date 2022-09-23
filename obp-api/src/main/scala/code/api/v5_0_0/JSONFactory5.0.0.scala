/**
  * Open Bank Project - API
  * Copyright (C) 2011-2019, TESOBE GmbH
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
  * TESOBE GmbH
  * Osloerstrasse 16/17
  * Berlin 13359, Germany
  * *
  * This product includes software developed at
  * TESOBE (http://www.tesobe.com/)
  *
  */
package code.api.v5_0_0

import java.util.Date

import code.api.util.APIUtil.stringOrNull
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, MetaJsonV140}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_1_0.PostConsentEntitlementJsonV310
import code.api.v4_0_0.BankAttributeBankResponseJsonV400
import code.bankattribute.BankAttribute
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, Bank, UserAuthContext, UserAuthContextUpdate}
import net.liftweb.json.JsonAST.JValue

import scala.collection.immutable.List

case class PostBankJson500(
    id: Option[String],
    bank_code: String,
    full_name: Option[String],
    logo: Option[String],
    website: Option[String],
    bank_routings: Option[List[BankRoutingJsonV121]],
    attributes: Option[List[BankAttributeBankResponseJsonV400]]
)

case class BankJson500(
    id: String,
    bank_code: String,
    full_name: String,
    logo: String,
    website: String,
    bank_routings: List[BankRoutingJsonV121],
    attributes: Option[List[BankAttributeBankResponseJsonV400]]
)

case class CreateAccountRequestJsonV500(
    user_id : Option[String],
    label   : String,
    product_code : String,
    balance : Option[AmountOfMoneyJsonV121],
    branch_id : Option[String],
    account_routings: Option[List[AccountRoutingJsonV121]]
)

case class PostCustomerJsonV500(
   legal_name: String,
   mobile_phone_number: String,
   email: Option[String] = None,
   face_image: Option[CustomerFaceImageJson] = None,
   date_of_birth: Option[Date] = None,
   relationship_status: Option[String] = None,
   dependants: Option[Int] = None,
   dob_of_dependants: Option[List[Date]] = None,
   credit_rating: Option[CustomerCreditRatingJSON] = None,
   credit_limit: Option[AmountOfMoneyJsonV121] = None,
   highest_education_attained: Option[String] = None,
   employment_status: Option[String] = None,
   kyc_status: Option[Boolean] = None,
   last_ok_date: Option[Date] = None,
   title: Option[String] = None,
   branch_id: Option[String] = None,
   name_suffix: Option[String] = None
)

case class PutProductJsonV500(
   parent_product_code: String, 
   name: String, 
   more_info_url: Option[String] = None, 
   terms_and_conditions_url: Option[String] = None, 
   description: Option[String] = None, 
   meta: Option[MetaJsonV140] = None,
)

case class UserAuthContextJsonV500(
  user_auth_context_id: String,
  user_id: String,
  key: String,
  value: String,
  time_stamp: Date,
  consumer_id: String,
)

case class UserAuthContextsJsonV500(
  user_auth_contexts: List[UserAuthContextJsonV500]
)

case class UserAuthContextUpdateJsonV500(
  user_auth_context_update_id: String,
  user_id: String,
  key: String,
  value: String,
  status: String,
  consumer_id: String,
)


case class PostConsentRequestResponseJson(consentRequestId: String)

case class ConsentRequestResponseJson(
  consent_request_id: String, 
  payload : JValue, 
  consumer_id : String
)
case class AccountAccessV500(
//  bank_routing: Option[BankRoutingJsonV121],
//  branch_routing: Option[BranchRoutingJsonV141],
  account_routing: AccountRoutingJsonV121,
  view_id: String
)

case class PostConsentRequestJsonV500(
  everything: Boolean,
  account_access: List[AccountAccessV500],
  entitlements: Option[List[PostConsentEntitlementJsonV310]],
  consumer_id: Option[String],
  email: Option[String],
  phone_number: Option[String],
  valid_from: Option[Date],
  time_to_live: Option[Long]
)

case class ConsentJsonV500(consent_id: String, jwt: String, status: String, consent_request_id: Option[String])
object JSONFactory500 {

  def createUserAuthContextJson(userAuthContext: UserAuthContext): UserAuthContextJsonV500 = {
    UserAuthContextJsonV500(
      user_auth_context_id= userAuthContext.userAuthContextId,
      user_id = userAuthContext.userId,
      key = userAuthContext.key,
      value = userAuthContext.value,
      time_stamp = userAuthContext.timeStamp,
      consumer_id = userAuthContext.consumerId,
    )
  }
  
  def createUserAuthContextsJson(userAuthContext: List[UserAuthContext]): UserAuthContextsJsonV500 = {
    UserAuthContextsJsonV500(userAuthContext.map(createUserAuthContextJson))
  }

  def createUserAuthContextUpdateJson(userAuthContextUpdate: UserAuthContextUpdate): UserAuthContextUpdateJsonV500 = {
    UserAuthContextUpdateJsonV500(
      user_auth_context_update_id= userAuthContextUpdate.userAuthContextUpdateId,
      user_id = userAuthContextUpdate.userId,
      key = userAuthContextUpdate.key,
      value = userAuthContextUpdate.value,
      status = userAuthContextUpdate.status,
      consumer_id = userAuthContextUpdate.consumerId
    )
  }

  def createBankJSON500(bank: Bank, attributes: List[BankAttribute] = Nil): BankJson500 = {
    val obp = BankRoutingJsonV121("OBP", bank.bankId.value)
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val routings = bank.bankRoutingScheme match {
      case "OBP" => bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case "BIC" => obp :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case _ => obp :: bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
    }
    new BankJson500(
      stringOrNull(bank.bankId.value),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoUrl),
      stringOrNull(bank.websiteUrl),
      routings.filter(a => stringOrNull(a.address) != null),
      Option(
        attributes.filter(_.isActive == Some(true)).map(a => BankAttributeBankResponseJsonV400(
          name = a.name,
          value = a.value)
        )
      )
    )
  }
  
}

