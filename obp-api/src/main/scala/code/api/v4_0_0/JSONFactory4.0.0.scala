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
package code.api.v4_0_0

import java.util.Date

import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil
import code.api.util.APIUtil.{stringOptionOrNull, stringOrNull}
import code.api.v1_2_1.JSONFactory.{createAmountOfMoneyJSON, createOwnersJSON}
import code.api.v1_2_1.{BankRoutingJsonV121, JSONFactory, UserJSONV121, ViewJSONV121}
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestChargeJsonV200
import code.api.v2_1_0.ResourceUserJSON
import code.api.v3_0_0.JSONFactory300.createAccountRoutingsJSON
import code.api.v3_0_0.{CustomerAttributeResponseJsonV300, ViewBasicV300}
import code.api.v3_1_0.AccountAttributeResponseJson
import code.api.v3_1_0.JSONFactory310.createAccountAttributeJson
import code.directdebit.DirectDebitTrait
import code.entitlement.Entitlement
import code.model.{Consumer, ModeratedBankAccountCore}
import code.standingorders.StandingOrderTrait
import code.transactionChallenge.MappedExpectedChallengeAnswer
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}

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
                              user_id: String,
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
                                                challenges: List[ChallengeJsonV400],
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

case class ModeratedCoreAccountJsonV400(
                                         id: String,
                                         bank_id: String,
                                         label: String,
                                         number: String,
                                         owners: List[UserJSONV121],
                                         product_code: String,
                                         balance: AmountOfMoneyJsonV121,
                                         account_routings: List[AccountRoutingJsonV121],
                                         views_basic: List[ViewBasicV300],
                                         account_attributes: List[AccountAttributeResponseJson],
                                         tags: List[AccountTagJSON]
                                       )

case class ModeratedAccountJSON400(
                                    id : String,
                                    label : String,
                                    number : String,
                                    owners : List[UserJSONV121],
                                    product_code : String,
                                    balance : AmountOfMoneyJsonV121,
                                    views_available : List[ViewJSONV121],
                                    bank_id : String,
                                    account_routings :List[AccountRoutingJsonV121],
                                    account_attributes: List[AccountAttributeResponseJson],
                                    tags: List[AccountTagJSON]
                                  )

case class AccountTagJSON(
                           id : String,
                           value : String,
                           date : Date,
                           user : UserJSONV121
                         )

case class EntitlementJsonV400(
                                entitlement_id: String,
                                role_name: String,
                                bank_id: String,
                                user_id: String
                              )

case class EntitlementsJsonV400(
                                list: List[EntitlementJsonV400]
                              )

case class AccountTagsJSON(
                            tags: List[AccountTagJSON]
                          )
case class PostAccountTagJSON(
                               value : String
                             )
case class PostCustomerPhoneNumberJsonV400(mobile_phone_number: String)
case class PostDirectDebitJsonV400(customer_id: String,
                                   user_id: String,
                                   counterparty_id: String,
                                   date_signed: Option[Date],
                                   date_starts: Date, 
                                   date_expires: Option[Date]
                                  )

case class DirectDebitJsonV400(direct_debit_id: String,
                               bank_id: String,
                               account_id: String,
                               customer_id: String,
                               user_id: String,
                               counterparty_id: String,
                               date_signed: Date,
                               date_starts: Date,
                               date_expires: Date,
                               date_cancelled: Date,
                               active: Boolean)
case class When(frequency: String, detail: String)
case class PostStandingOrderJsonV400(customer_id: String,
                                     user_id: String,
                                     counterparty_id: String,
                                     amount : AmountOfMoneyJsonV121,
                                     when: When,
                                     date_signed: Option[Date],
                                     date_starts: Date,
                                     date_expires: Option[Date]
                                    )

case class StandingOrderJsonV400(standing_order_id: String,
                                 bank_id: String,
                                 account_id: String,
                                 customer_id: String,
                                 user_id: String,
                                 counterparty_id: String,
                                 amount: AmountOfMoneyJsonV121,
                                 when: When,
                                 date_signed: Date,
                                 date_starts: Date,
                                 date_expires: Date,
                                 date_cancelled: Date,
                                 active: Boolean)
case class PostViewJsonV400(view_id: String, is_system: Boolean)
case class PostAccountAccessJsonV400(user_id: String, view: PostViewJsonV400)
case class RevokedJsonV400(revoked: Boolean)

// the data from endpoint, extract as valid JSON
case class TransactionRequestBodyRefundJsonV400(
  to: TransactionRequestAccountJsonV140,
  value: AmountOfMoneyJsonV121,
  description: String,
  refund:RefundJson
) extends TransactionRequestCommonBodyJSON

case class RefundJson(
  transaction_id: String
)
case class CustomerAttributeJsonV400(
  name: String,
  `type`: String,
  value: String,
)

case class CustomerAttributesResponseJson(
  customer_attributes: List[CustomerAttributeResponseJsonV300]
)
case class TransactionAttributeJsonV400(
  name: String,
  `type`: String,
  value: String,
)

case class TransactionAttributeResponseJson(
  transaction_attribute_id: String,
  name: String,
  `type`: String,
  value: String
)

case class TransactionAttributesResponseJson(
  transaction_attributes: List[TransactionAttributeResponseJson]
)

case class ConsumerJson(consumer_id: String,
                        key: String,
                        secret: String,
                        app_name: String,
                        app_type: String,
                        description: String,
                        developer_email: String,
                        redirect_url: String,
                        created_by_user_id: String,
                        created_by_user: ResourceUserJSON,
                        enabled: Boolean,
                        created: Date
                       )

case class AttributeDefinitionJsonV400(
                                        name: String,
                                        category: String,
                                        `type`: String,
                                        description: String,
                                        alias: String,
                                        can_be_seen_on_views: List[String],
                                        is_active: Boolean
                                      )

case class AttributeDefinitionResponseJsonV400(attribute_definition_id: String,
                                               bank_id: String,
                                               name: String,
                                               category: String,
                                               `type`: String,
                                               description: String,
                                               alias: String,
                                               can_be_seen_on_views: List[String],
                                               is_active: Boolean
                                              )

case class AttributeDefinitionsResponseJsonV400(
                                                 attributes: List[AttributeDefinitionResponseJsonV400]
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

  def createTransactionRequestWithChargeJSON(tr : TransactionRequest, challenges: List[MappedExpectedChallengeAnswer]) : TransactionRequestWithChargeJSON400 = {
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
      challenges = {
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
          challenges.map(
            e => ChallengeJsonV400(id = stringOrNull(e.challengeId), user_id = e.expectedUserId, allowed_attempts = tr.challenge.allowed_attempts, challenge_type = stringOrNull(tr.challenge.challenge_type), link = link)
          )
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

  
  def createNewCoreBankAccountJson(account : ModeratedBankAccountCore, 
                                   availableViews: List[View],
                                   accountAttributes: List[AccountAttribute], 
                                   tags: List[TransactionTag]) : ModeratedCoreAccountJsonV400 =  {
    new ModeratedCoreAccountJsonV400 (
      account.accountId.value,
      stringOrNull(account.bankId.value),
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()),""),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance.getOrElse("")),
      createAccountRoutingsJSON(account.accountRoutings),
      views_basic = availableViews.map(view => code.api.v3_0_0.ViewBasicV300(id = view.viewId.value, short_name = view.name, description = view.description, is_public = view.isPublic)),
      accountAttributes.map(createAccountAttributeJson),
      tags.map(createAccountTagJSON)
    )
  }


  def createBankAccountJSON(account : ModeratedBankAccountCore,
                            viewsAvailable : List[ViewJSONV121],
                            accountAttributes: List[AccountAttribute],
                            tags: List[TransactionTag]) : ModeratedAccountJSON400 =  {
    new ModeratedAccountJSON400(
      account.accountId.value,
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), ""),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance.getOrElse("")),
      viewsAvailable,
      stringOrNull(account.bankId.value),
      createAccountRoutingsJSON(account.accountRoutings),
      accountAttributes.map(createAccountAttributeJson),
      tags.map(createAccountTagJSON)
    )
  }


  def createAccountTagsJSON(tags : List[TransactionTag]) : AccountTagsJSON = {
    new AccountTagsJSON(tags.map(createAccountTagJSON))
  }
  def createAccountTagJSON(tag : TransactionTag) : AccountTagJSON = {
    new AccountTagJSON(
      id = tag.id_,
      value = tag.value,
      date = tag.datePosted,
      user = JSONFactory.createUserJSON(tag.postedBy)
    )
  }
  def createEntitlementJSONs(entitlements: List[Entitlement]): EntitlementsJsonV400 = {
    EntitlementsJsonV400(entitlements.map(entitlement => EntitlementJsonV400(
      entitlement_id = entitlement.entitlementId,
      role_name = entitlement.roleName,
      bank_id = entitlement.bankId,
      user_id = entitlement.userId
    )))
  }

  def createDirectDebitJSON(directDebit: DirectDebitTrait): DirectDebitJsonV400 = {
    DirectDebitJsonV400(direct_debit_id = directDebit.directDebitId,
      bank_id = directDebit.bankId,
      account_id = directDebit.accountId,
      customer_id = directDebit.customerId,
      user_id = directDebit.userId,
      counterparty_id = directDebit.counterpartyId,
      date_signed = directDebit.dateSigned,
      date_cancelled = directDebit.dateCancelled,
      date_starts = directDebit.dateStarts,
      date_expires = directDebit.dateExpires,
      active = directDebit.active)
  }
  def createStandingOrderJSON(standingOrder: StandingOrderTrait): StandingOrderJsonV400 = {
    StandingOrderJsonV400(standing_order_id = standingOrder.standingOrderId,
      bank_id = standingOrder.bankId,
      account_id = standingOrder.accountId,
      customer_id = standingOrder.customerId,
      user_id = standingOrder.userId,
      counterparty_id = standingOrder.counterpartyId,
      amount = AmountOfMoneyJsonV121(standingOrder.amountValue.toString(), standingOrder.amountCurrency),
      when = When(frequency = standingOrder.whenFrequency, detail = standingOrder.whenDetail),
      date_signed = standingOrder.dateSigned,
      date_cancelled = standingOrder.dateCancelled,
      date_starts = standingOrder.dateStarts,
      date_expires = standingOrder.dateExpires,
      active = standingOrder.active)
  }

  def createCustomerAttributeJson(customerAttribute: CustomerAttribute) : CustomerAttributeResponseJsonV300 = {
    CustomerAttributeResponseJsonV300(
      customer_attribute_id = customerAttribute.customerAttributeId,
      name = customerAttribute.name,
      `type` = customerAttribute.attributeType.toString,
      value = customerAttribute.value
    )
  }

  def createCustomerAttributesJson(customerAttributes: List[CustomerAttribute]) : CustomerAttributesResponseJson = {
    CustomerAttributesResponseJson (customerAttributes.map( customerAttribute => CustomerAttributeResponseJsonV300(
      customer_attribute_id = customerAttribute.customerAttributeId,
      name = customerAttribute.name,
      `type` = customerAttribute.attributeType.toString,
      value = customerAttribute.value
    )))
  }

  def createTransactionAttributeJson(transactionAttribute: TransactionAttribute) : TransactionAttributeResponseJson = {
    TransactionAttributeResponseJson(
      transaction_attribute_id = transactionAttribute.transactionAttributeId,
      name = transactionAttribute.name,
      `type` = transactionAttribute.attributeType.toString,
      value = transactionAttribute.value
    )
  }

  def createTransactionAttributesJson(transactionAttributes: List[TransactionAttribute]) : TransactionAttributesResponseJson = {
    TransactionAttributesResponseJson (transactionAttributes.map( transactionAttribute => TransactionAttributeResponseJson(
      transaction_attribute_id = transactionAttribute.transactionAttributeId,
      name = transactionAttribute.name,
      `type` = transactionAttribute.attributeType.toString,
      value = transactionAttribute.value
    )))
  }

  def createConsumerJSON(c: Consumer, user: Box[User]): ConsumerJson = {

    val resourceUserJSON = user match {
      case Full(resourceUser) => ResourceUserJSON(
        user_id = resourceUser.userId,
        email = resourceUser.emailAddress,
        provider_id = resourceUser.idGivenByProvider,
        provider = resourceUser.provider,
        username = resourceUser.name
      )
      case _ => null
    }

    ConsumerJson(consumer_id=c.consumerId.get,
      key=c.key.get,
      secret=c.secret.get,
      app_name=c.name.get,
      app_type=c.appType.toString(),
      description=c.description.get,
      developer_email=c.developerEmail.get,
      redirect_url=c.redirectURL.get,
      created_by_user_id =c.createdByUserId.get,
      created_by_user =resourceUserJSON,
      enabled=c.isActive.get,
      created=c.createdAt.get
    )
  }


  def createAttributeDefinitionJson(attributeDefinition: AttributeDefinition) : AttributeDefinitionResponseJsonV400 = {
    AttributeDefinitionResponseJsonV400(
      attribute_definition_id = attributeDefinition.attributeDefinitionId,
      bank_id = attributeDefinition.bankId.value,
      name = attributeDefinition.name,
      category = attributeDefinition.category.toString,
      `type` = attributeDefinition.`type`.toString,
      description = attributeDefinition.description,
      can_be_seen_on_views = attributeDefinition.canBeSeenOnViews,
      alias = attributeDefinition.alias,
      is_active = attributeDefinition.isActive,
    )
  }

  def createAttributeDefinitionsJson(attributeDefinitions: List[AttributeDefinition]) : AttributeDefinitionsResponseJsonV400 = {
    AttributeDefinitionsResponseJsonV400(attributeDefinitions.map(createAttributeDefinitionJson))
  }
  
}

