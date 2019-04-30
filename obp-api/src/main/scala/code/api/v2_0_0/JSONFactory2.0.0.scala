/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v2_0_0

import java.util.Date

import code.TransactionTypes.TransactionType.TransactionType
import code.api.util.CustomJsonFormats
import code.api.v1_2_1.{JSONFactory => JSONFactory121, MinimalBankJSON => MinimalBankJSON121, ThisAccountJSON => ThisAccountJSON121, UserJSONV121 => UserJSON121}
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeJsonV140, CustomerFaceImageJson, TransactionRequestAccountJsonV140}
import code.entitlement.Entitlement
import code.kycchecks.KycCheck
import code.kycdocuments.KycDocument
import code.kycmedias.KycMedia
import code.kycstatuses.KycStatus
import code.model._
import code.model.dataAccess.AuthUser
import code.socialmedia.SocialMedia
import code.transactionrequests.TransactionRequests._
import code.users.Users
import com.openbankproject.commons.model.{BankAccount, _}
import net.liftweb.common.{Box, Full}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue



// New in 2.0.0
case class LinkJson(
  href: String,
  rel: String,
  method: String
)

case class LinksJson(
  _links: List[LinkJson]
)

case class ResultAndLinksJson(
  result: JValue,
  links: LinksJson
)

case class CreateUserJson(
  email: String,
  username: String,
  password: String,
  first_name: String,
  last_name: String
)

case class CreateUsersJson(
  users: List[CreateUserJson]
)

case class CreateMeetingJson(
  provider_id: String,
  purpose_id: String
)

case class MeetingJson(
  meeting_id: String,
  provider_id: String,
  purpose_id: String,
  bank_id: String,
  present: MeetingPresentJson,
  keys: MeetingKeysJson,
  when: Date
)

case class MeetingsJson(
  meetings: List[MeetingJson]
)


case class MeetingKeysJson(
  session_id: String,
  staff_token: String,
  customer_token: String
)

case class MeetingPresentJson(
  staff_user_id: String,
  customer_user_id: String

)

case class UserCustomerLinkJson(
  user_customer_link_id: String,
  customer_id: String,
  user_id: String,
  date_inserted: Date,
  is_active: Boolean
)
case class UserCustomerLinksJson(l: List[UserCustomerLinkJson])

case class CreateUserCustomerLinkJson(user_id: String, customer_id: String)

case class BasicViewJson(
  val id: String,
  val short_name: String,
  val is_public: Boolean
)

case class BasicAccountsJSON(
  accounts : List[BasicAccountJSON]
)

// Basic Account has basic View
case class BasicAccountJSON(
                             id : String,
                             label : String,
                             bank_id : String,
                             views_available : List[BasicViewJson]
)


// Json used in account creation
case class CreateAccountJSON(
                             user_id : String,
                             label   : String,
                              `type` : String,
                             balance : AmountOfMoneyJsonV121
                           )

// No view in core
case class CoreAccountJSON(
                             id : String,
                             label : String,
                             bank_id : String,
                             _links: JValue
                           )

case class CoreAccountsJSON( accounts: List[CoreAccountJSON])

case class PostKycDocumentJSON(
                                customer_number: String,
                                `type`: String,
                                number: String,
                                issue_date: Date,
                                issue_place: String,
                                expiry_date: Date
                              )
case class KycDocumentJSON(
                            bank_id: String,
                            customer_id: String,
                            id: String,
                            customer_number: String,
                            `type`: String,
                            number: String,
                            issue_date: Date,
                            issue_place: String,
                            expiry_date: Date
                          )
case class KycDocumentsJSON(documents: List[KycDocumentJSON])

case class PostKycMediaJSON(
                         customer_number: String,
                         `type`: String,
                         url: String,
                         date: Date,
                         relates_to_kyc_document_id: String,
                         relates_to_kyc_check_id: String
                       )
case class KycMediaJSON(
                         bank_id: String,
                         customer_id: String,
                         id: String,
                         customer_number: String,
                         `type`: String,
                         url: String,
                         date: Date,
                         relates_to_kyc_document_id: String,
                         relates_to_kyc_check_id: String
                       )
case class KycMediasJSON(medias: List[KycMediaJSON])

case class PostKycCheckJSON(
                         customer_number: String,
                         date: Date,
                         how: String,
                         staff_user_id: String,
                         staff_name: String,
                         satisfied: Boolean,
                         comments: String
                       )
case class KycCheckJSON(
                         bank_id: String,
                         customer_id: String,
                         id: String,
                         customer_number: String,
                         date: Date,
                         how: String,
                         staff_user_id: String,
                         staff_name: String,
                         satisfied: Boolean,
                         comments: String
                       )
case class KycChecksJSON(checks: List[KycCheckJSON])

case class PostKycStatusJSON(
                          customer_number: String,
                          ok: Boolean,
                          date: Date
                        )
case class KycStatusJSON(
                          customer_id: String,
                          customer_number: String,
                          ok: Boolean,
                          date: Date
                        )
case class KycStatusesJSON(statuses: List[KycStatusJSON])

case class SocialMediaJSON(
   customer_number: String,
   `type`: String,
   handle: String,
   date_added: Date,
   date_activated: Date
)
case class SocialMediasJSON(checks: List[SocialMediaJSON])

case class CreateCustomerJson(
                             title: String,
                             branchId: String,
                             nameSuffix: String,
                             user_id: String,
                             customer_number : String,
                             legal_name : String,
                             mobile_phone_number : String,
                             email : String,
                             face_image : CustomerFaceImageJson,
                             date_of_birth: Date,
                             relationship_status: String,
                             dependants: Int,
                             dob_of_dependants: List[Date],
                             highest_education_attained: String,
                             employment_status: String,
                             kyc_status: Boolean,
                             last_ok_date: Date)




// TODO Use the scala doc of a case class in the Resource Doc if a case class is given as a return type


/** A TransactionType categorises a transaction on a bank statement.
  *
  * i.e. it justifies the reason for a transaction on a bank statement to exist
  * e.g. a bill-payment, ATM-withdrawal, interest-payment or some kind of fee to the customer.
  *
  * This is the JSON respresentation (v2.0.0) of the object
  *
  * @param id Unique id across the API instance. Ideally a UUID
  * @param bank_id The bank that supports this TransactionType
  * @param short_code A short code (ideally-no-spaces) which is unique across the bank. Should map to transaction.details.types
  * @param summary A succinct summary
  * @param description A longer description
  * @param charge The fee to the customer for each one of these
  */

case class TransactionTypeJsonV200(
  id: TransactionTypeId,
  bank_id: String,
  short_code: String,
  summary: String,
  description: String,
  charge: AmountOfMoneyJsonV121
)

case class TransactionTypesJsonV200(
  transaction_types: List[TransactionTypeJsonV200]
)



/*
v2.0.0 Json Representation of TransactionRequest
 */

case class TransactionRequestChargeJsonV200(
  val summary: String,
  val value: AmountOfMoneyJsonV121
)

case class TransactionRequestJsonV200(
  id: String,
  `type`: String,
  from: TransactionRequestAccountJsonV140,
  body: TransactionRequestBodyJsonV200,
  transaction_ids: String,
  status: String,
  start_date: Date,
  end_date: Date,
  challenge: ChallengeJsonV140
)


case class TransactionRequestWithChargeJson(
  id: String,
  `type`: String,
  from: TransactionRequestAccountJsonV140,
  details: TransactionRequestBody,
  transaction_ids: String,
  status: String,
  start_date: Date,
  end_date: Date,
  challenge: ChallengeJsonV140,
  charge: TransactionRequestChargeJsonV200
)


case class TransactionRequestWithChargesJson(
  transaction_requests_with_charges: List[TransactionRequestWithChargeJson]
)


case class TransactionRequestBodyJsonV200(
  to: TransactionRequestAccountJsonV140,
  value: AmountOfMoneyJsonV121,
  description: String
)

case class CreateEntitlementJSON(bank_id: String, role_name: String)
case class EntitlementJSON(entitlement_id: String, role_name: String, bank_id: String)
case class EntitlementJSONs(list: List[EntitlementJSON])

object JSONFactory200 extends CustomJsonFormats {

  def privateBankAccountsListToJson(bankAccounts: List[BankAccount], privateViewsUserCanAccess : List[View]): JValue = {
    val accJson : List[BasicAccountJSON] = bankAccounts.map( account => {
      val viewsAvailable : List[BasicViewJson] =
        privateViewsUserCanAccess
          .filter(v =>v.bankId==account.bankId && v.accountId ==account.accountId && v.isPrivate)//filter the view for this account.
          .map(createBasicViewJSON(_))
          .distinct
      createBasicAccountJSON(account,viewsAvailable)
    })
    val accounts = new BasicAccountsJSON(accJson)
    Extraction.decompose(accounts)
  }





  // Modified in 2.0.0

  //transaction requests
  def getTransactionRequestBodyFromJson(body: TransactionRequestBodyJsonV200) : TransactionRequestBody = {
    val toAcc = TransactionRequestAccount (
      bank_id = body.to.bank_id,
      account_id = body.to.account_id
    )
    val amount = AmountOfMoney (
      currency = body.value.currency,
      amount = body.value.amount
    )

    TransactionRequestBody (
      to = toAcc,
      value = amount,
      description = body.description
    )
  }

  // New in 2.0.0


  def createBasicViewJSON(view : View) : BasicViewJson = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    new BasicViewJson(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      is_public = view.isPublic
    )
  }


  def createBasicAccountJSON(account : BankAccount, basicViewsAvailable : List[BasicViewJson] ) : BasicAccountJSON = {
    new BasicAccountJSON(
      account.accountId.value,
      stringOrNull(account.label),
      account.bankId.value,
      basicViewsAvailable
    )
  }

  // Contains only minimal info (could have more if owner) plus links
  def createCoreAccountJSON(account : BankAccount, links: JValue ) : CoreAccountJSON = {
    val coreAccountJson = new CoreAccountJSON(
      account.accountId.value,
      stringOrNull(account.label),
      account.bankId.value,
      links
    )
    coreAccountJson
  }


  case class ModeratedCoreAccountJSON(
                                   id : String,
                                   label : String,
                                   number : String,
                                   owners : List[UserJSON121],
                                   `type` : String,
                                   balance : AmountOfMoneyJsonV121,
                                   IBAN : String,
                                   swift_bic: String,
                                   bank_id : String,
                                   account_routing:AccountRoutingJsonV121
                                 )

  case class CoreTransactionsJSON(
                               transactions: List[CoreTransactionJSON]
                             )

  case class CoreTransactionJSON(
                              id : String,
                              account : ThisAccountJSON121,
                              counterparty : CoreCounterpartyJSON,
                              details : CoreTransactionDetailsJSON
                            )



  case class CoreAccountHolderJSON(
                                name : String
                              )


  case class CoreCounterpartyJSON(
                                   id : String,
                                   holder : CoreAccountHolderJSON,
                                   number : String,
                                   kind : String,
                                   IBAN : String,
                                   swift_bic: String,
                                   bank : MinimalBankJSON121
                             )




  def createCoreTransactionsJSON(transactions: List[ModeratedTransaction]) : CoreTransactionsJSON = {
    new CoreTransactionsJSON(transactions.map(createCoreTransactionJSON))
  }

  case class CoreTransactionDetailsJSON(
                                     `type` : String,
                                     description : String,
                                     posted : Date,
                                     completed : Date,
                                     new_balance : AmountOfMoneyJsonV121,
                                     value : AmountOfMoneyJsonV121
                                   )



  case class UserJsonV200(
                       user_id: String,
                       email : String,
                       provider_id: String,
                       provider : String,
                       username : String,
                       entitlements : EntitlementJSONs
                     )

  case class UsersJsonV200(
                      users: List[UserJsonV200]
                      )


  def createUserJSONfromAuthUser(user : AuthUser) : UserJsonV200 = {
    val (userId, provider, providerId,  entitlements) = Users.users.vend.getUserByResourceUserId(user.user.get) match {
      case Full(u) => (u.userId,u.provider,u.idGivenByProvider, u.assignedEntitlements)
      case _       => ("","","", List())
    }
    new UserJsonV200(user_id = userId,
      email = user.email.get,
      username = stringOrNull(user.username.get),
      provider_id = stringOrNull(providerId),
      provider = stringOrNull(provider),
      entitlements = createEntitlementJSONs(entitlements)
    )
  }



  def createUserJSON(user : User) : UserJsonV200 = {
    new UserJsonV200(
      user_id = user.userId,
      email = user.emailAddress,
      username = stringOrNull(user.name),
      provider_id = user.idGivenByProvider,
      provider = stringOrNull(user.provider),
      entitlements = createEntitlementJSONs(user.assignedEntitlements)
    )
  }

  def createUserJSON(user : Box[User]) : UserJsonV200 = {
    user match {
      case Full(u) => createUserJSON(u)
      case _ => null
    }
  }

  def createUserJSONs(users : List[User]) : UsersJsonV200 = {
    UsersJsonV200(users.map(createUserJSON))
  }



  def createUserJSONfromAuthUser(user : Box[AuthUser]) : UserJsonV200 = {
    user match {
      case Full(u) => createUserJSONfromAuthUser(u)
      case _ => null
    }
  }





  def createCoreTransactionDetailsJSON(transaction : ModeratedTransaction) : CoreTransactionDetailsJSON = {
    new CoreTransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = JSONFactory121.createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= JSONFactory121.createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }


  def createCoreTransactionJSON(transaction : ModeratedTransaction) : CoreTransactionJSON = {
    new CoreTransactionJSON(
      id = transaction.id.value,
      account = transaction.bankAccount.map(JSONFactory121.createThisAccountJSON).getOrElse(null),
      counterparty = transaction.otherBankAccount.map(createCoreCounterparty).getOrElse(null),
      details = createCoreTransactionDetailsJSON(transaction)
    )
  }





  case class CounterpartiesJSON(
                                 counterparties : List[CoreCounterpartyJSON]
                              )


  def createCoreCounterparty(bankAccount : ModeratedOtherBankAccount) : CoreCounterpartyJSON = {
    new CoreCounterpartyJSON(
      id = bankAccount.id,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.kind),
      IBAN = stringOptionOrNull(bankAccount.iban),
      swift_bic = stringOptionOrNull(bankAccount.swift_bic),
      bank = JSONFactory121.createMinimalBankJSON(bankAccount),
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias)
    )
  }



  def createAccountHolderJSON(owner : User, isAlias : Boolean) : CoreAccountHolderJSON = {
    // Note we are not using isAlias
    new CoreAccountHolderJSON(
      name = owner.name
    )
  }

  def createAccountHolderJSON(name : String, isAlias : Boolean) : CoreAccountHolderJSON = {
    // Note we are not using isAlias
    new CoreAccountHolderJSON(
      name = name
    )
  }



  def createCoreBankAccountJSON(account : ModeratedBankAccount) : ModeratedCoreAccountJSON =  {
    val bankName = account.bankName.getOrElse("")
    new ModeratedCoreAccountJSON (
      account.accountId.value,
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      JSONFactory121.createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      stringOptionOrNull(account.accountType),
      JSONFactory121.createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      stringOptionOrNull(account.iban),
      stringOptionOrNull(None),//set it None for V200
      stringOrNull(account.bankId.value),
      AccountRoutingJsonV121(stringOptionOrNull(account.accountRoutingScheme),stringOptionOrNull(account.accountRoutingAddress))
    )
  }






  def createKycDocumentJSON(kycDocument : KycDocument) : KycDocumentJSON = {
    new KycDocumentJSON(
      bank_id = kycDocument.bankId,
      customer_id = kycDocument.customerId,
      id = kycDocument.idKycDocument,
      customer_number = kycDocument.customerNumber,
      `type` = kycDocument.`type`,
      number = kycDocument.number,
      issue_date = kycDocument.issueDate,
      issue_place = kycDocument.issuePlace,
      expiry_date = kycDocument.expiryDate
    )
  }

  def createKycDocumentsJSON(messages : List[KycDocument]) : KycDocumentsJSON = {
    KycDocumentsJSON(messages.map(createKycDocumentJSON))
  }

  def createKycMediaJSON(kycMedia : KycMedia) : KycMediaJSON = {
    new KycMediaJSON(
      bank_id = kycMedia.bankId,
      customer_id = kycMedia.customerId,
      id = kycMedia.idKycMedia,
      customer_number = kycMedia.customerNumber,
      `type` = kycMedia.`type`,
      url = kycMedia.url,
      date = kycMedia.date,
      relates_to_kyc_document_id = kycMedia.relatesToKycDocumentId,
      relates_to_kyc_check_id = kycMedia.relatesToKycCheckId
    )
  }
  def createKycMediasJSON(messages : List[KycMedia]) : KycMediasJSON = {
    KycMediasJSON(messages.map(createKycMediaJSON))
  }

  def createKycCheckJSON(kycCheck : KycCheck) : KycCheckJSON = {
    new KycCheckJSON(
      bank_id = kycCheck.bankId,
      customer_id = kycCheck.customerId,
      id = kycCheck.idKycCheck,
      customer_number = kycCheck.customerNumber,
      date = kycCheck.date,
      how = kycCheck.how,
      staff_user_id = kycCheck.staffUserId,
      staff_name = kycCheck.staffName,
      satisfied = kycCheck.satisfied,
      comments = kycCheck.comments
    )
  }
  def createKycChecksJSON(messages : List[KycCheck]) : KycChecksJSON = {
    KycChecksJSON(messages.map(createKycCheckJSON))
  }

  def createKycStatusJSON(kycStatus : KycStatus) : KycStatusJSON = {
    new KycStatusJSON(
      customer_id = kycStatus.customerId,
      customer_number = kycStatus.customerNumber,
      ok = kycStatus.ok,
      date = kycStatus.date
    )
  }
  def createKycStatusesJSON(messages : List[KycStatus]) : KycStatusesJSON = {
    KycStatusesJSON(messages.map(createKycStatusJSON))
  }

  def createSocialMediaJSON(socialMedia : SocialMedia) : SocialMediaJSON = {
    new SocialMediaJSON(
      customer_number = socialMedia.customerNumber,
      `type` = socialMedia.`type`,
      handle = socialMedia.handle,
      date_added = socialMedia.dateAdded,
      date_activated = socialMedia.dateActivated
    )
  }
  def createSocialMediasJSON(messages : List[SocialMedia]) : SocialMediasJSON = {
    SocialMediasJSON(messages.map(createSocialMediaJSON))
  }


  /** Creates v2.0.0 representation of a TransactionType
    *
    * @param transactionType An internal TransactionType instance
    * @return a v2.0.0 representation of a TransactionType
    */

def createTransactionTypeJSON(transactionType : TransactionType) : TransactionTypeJsonV200 = {
    new TransactionTypeJsonV200(
      id = transactionType.id,
      bank_id = transactionType.bankId.toString,
      short_code = transactionType.shortCode,
      summary = transactionType.summary,
      description = transactionType.description,
      charge = new AmountOfMoneyJsonV121(currency = transactionType.charge.currency, amount = transactionType.charge.amount)
    )
  }
  def createTransactionTypeJSON(transactionTypes : List[TransactionType]) : TransactionTypesJsonV200 = {
    TransactionTypesJsonV200(transactionTypes.map(createTransactionTypeJSON))
  }




  /** Creates v2.0.0 representation of a TransactionType
    *
    * @param tr An internal TransactionRequest instance
    * @return a v2.0.0 representation of a TransactionRequest
    */

  def createTransactionRequestWithChargeJSON(tr : TransactionRequest) : TransactionRequestWithChargeJson = {
    new TransactionRequestWithChargeJson(
      id = tr.id.value,
      `type` = tr.`type`,
      from = TransactionRequestAccountJsonV140 (
        bank_id = tr.from.bank_id,
        account_id = tr.from.account_id),
      details = TransactionRequestBody(tr.body.to_sandbox_tan.get, tr.body.value, tr.body.description),
      transaction_ids = tr.transaction_ids,
      status = tr.status,
      start_date = tr.start_date,
      end_date = tr.end_date,
      // Some (mapped) data might not have the challenge. TODO Make this nicer
      challenge = {
        try {ChallengeJsonV140 (id = tr.challenge.id, allowed_attempts = tr.challenge.allowed_attempts, challenge_type = tr.challenge.challenge_type)}
        // catch { case _ : Throwable => ChallengeJSON (id = "", allowed_attempts = 0, challenge_type = "")}
        catch { case _ : Throwable => null}
      },
      charge = TransactionRequestChargeJsonV200 (summary = tr.charge.summary,
                                              value = AmountOfMoneyJsonV121(currency = tr.charge.value.currency,
                                                                        amount = tr.charge.value.amount)
      )
    )
  }
  def createTransactionRequestJSONs(trs : List[TransactionRequest]) : TransactionRequestWithChargesJson = {
    TransactionRequestWithChargesJson(trs.map(createTransactionRequestWithChargeJSON))
  }

  def createMeetingJSON(meeting : Meeting) : MeetingJson = {
    MeetingJson(meeting_id = meeting.meetingId,
                provider_id = meeting.providerId,
                purpose_id = meeting.purposeId,
                bank_id = meeting.bankId,
                present = MeetingPresentJson(staff_user_id = meeting.present.staffUserId,
                                              customer_user_id = meeting.present.customerUserId),
                keys = MeetingKeysJson(session_id = meeting.keys.sessionId,
                                        staff_token = meeting.keys.staffToken,
                                        customer_token = meeting.keys.customerToken),
                when = meeting.when)

  }

  def createMeetingJSONs(meetings : List[Meeting]) : MeetingsJson = {
    MeetingsJson(meetings.map(createMeetingJSON))
  }

  def createUserCustomerLinkJSON(ucl: code.usercustomerlinks.UserCustomerLink) = {
    UserCustomerLinkJson(user_customer_link_id = ucl.userCustomerLinkId,
      customer_id = ucl.customerId,
      user_id = ucl.userId,
      date_inserted = ucl.dateInserted,
      is_active = ucl.isActive
    )
  }

  def createUserCustomerLinkJSONs(ucls: List[code.usercustomerlinks.UserCustomerLink]): UserCustomerLinksJson = {
    UserCustomerLinksJson(ucls.map(createUserCustomerLinkJSON))
  }

  def createEntitlementJSON(e: Entitlement): EntitlementJSON = {
    EntitlementJSON(entitlement_id = e.entitlementId,
      role_name = e.roleName,
      bank_id = e.bankId)
  }

  def createEntitlementJSONs(l: List[Entitlement]): EntitlementJSONs = {
    EntitlementJSONs(l.map(createEntitlementJSON))
  }

  // Copied from 1.2.1 (import just this def instead?)
  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text

  // Copied from 1.2.1 (import just this def instead?)
  def stringOptionOrNull(text : Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }






}