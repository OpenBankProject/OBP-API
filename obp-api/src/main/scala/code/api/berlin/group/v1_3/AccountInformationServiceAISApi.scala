package code.api.builder.AccountInformationServiceAISApi

import java.text.SimpleDateFormat

import code.api.APIFailureNewStyle
import code.api.BerlinGroup.{AuthenticationType, ScaStatus}
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.{JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import code.api.util.APIUtil.{defaultBankId, passesPsd2Aisp, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiTag, NewStyle}
import code.bankconnectors.Connector
import code.consent.{ConsentStatus, Consents}
import code.database.authorisation.Authorisations
import code.model._
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}
import net.liftweb.common.Full
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object APIMethods_AccountInformationServiceAISApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createConsent ::
      deleteConsent ::
      getAccountList ::
      getBalances ::
      getCardAccount ::
      getCardAccountBalances ::
      getCardAccountTransactionList ::
      getConsentAuthorisation ::
      getConsentInformation ::
      getConsentScaStatus ::
      getConsentStatus ::
      getTransactionDetails ::
      getTransactionList ::
      readAccountDetails ::
      readCardAccount ::
      startConsentAuthorisation ::
      updateConsentsPsuData ::
      Nil
    lazy val newStyleEndpoints: List[(String, String)] = resourceDocs.map {
      rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
    }.toList
            
     resourceDocs += ResourceDoc(
       createConsent,
       apiVersion,
       nameOf(createConsent),
       "POST",
       "/consents",
       "Create consent",
       s"""${mockedDataText(false)}
This method create a consent resource, defining access rights to dedicated accounts of 
a given PSU-ID. These accounts are addressed explicitly in the method as 
parameters as a core function.

**Side Effects**
When this Consent Request is a request where the "recurringIndicator" equals "true", 
and if it exists already a former consent for recurring access on account information 
for the addressed PSU, then the former consent automatically expires as soon as the new 
consent request is authorised by the PSU.

Optional Extension:
As an option, an ASPSP might optionally accept a specific access right on the access on all psd2 related services for all available accounts. 

As another option an ASPSP might optionally also accept a command, where only access rights are inserted without mentioning the addressed account. 
The relation to accounts is then handled afterwards between PSU and ASPSP. 
This option is not supported for the Embedded SCA Approach. 
As a last option, an ASPSP might in addition accept a command with access rights
  * to see the list of available payment accounts or
  * to see the list of available payment accounts with balances.
""",
       json.parse("""{
                      "access": {"accounts": []},
                      "recurringIndicator": false,
                      "validUntil": "2020-12-31",
                      "frequencyPerDay": 4,
                      "combinedServiceIndicator": false
                    }"""),
       json.parse("""{
                      "consentStatus": "received",
                      "consentId": "1234-wertiq-983",
                      "_links": {
                        "startAuthorisation": {
                          "href": "/v1.3/consents/1234-wertiq-983/authorisations"
                        }
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "consents" :: Nil JsonPost json -> _  =>  {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentJson "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               json.extract[PostConsentJson]
             }

             failMsg = s"$InvalidDateFormat Current `validUntil` field is ${consentJson.validUntil}. Please use this format ${DateWithDayFormat.toPattern}!"
             validUntil <- NewStyle.function.tryons(failMsg, 400, callContext) {
               new SimpleDateFormat(DateWithDay).parse(consentJson.validUntil)
             }
             
             failMsg = s"$InvalidJsonContent Only Support empty accounts List for now. It will return an accessible account List. "
             _ <- Helper.booleanToFuture(failMsg) {consentJson.access.accounts.get.isEmpty}
             
             createdConsent <- Future(Consents.consentProvider.vend.createBerlinGroupConsent(
               u,
               recurringIndicator = consentJson.recurringIndicator,
               validUntil = validUntil,
               frequencyPerDay = consentJson.frequencyPerDay,
               combinedServiceIndicator = consentJson.combinedServiceIndicator
             )) map {
               i => connectorEmptyResponse(i, callContext)
             }
/*             _ <- Future(Authorisations.authorisationProvider.vend.createAuthorization(
               "",
               createdConsent.consentId,
               AuthenticationType.SMS_OTP.toString,
               "",
               ScaStatus.received.toString,
               "12345" // TODO Implement SMS sending
             )) map {
               unboxFullOrFail(_, callContext, s"$UnknownError ")
             }*/
           } yield {
             (createPostConsentResponseJson(createdConsent), HttpCode.`201`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteConsent,
       apiVersion,
       nameOf(deleteConsent),
       "DELETE",
       "/consents/CONSENTID",
       "Delete Consent",
       s"""${mockedDataText(false)}
            The TPP can delete an account information consent object if needed.""",
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")   :: apiTagBerlinGroupM :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "consents" :: consentId :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(user), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
             consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (JsRaw(""), HttpCode.`204`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountList,
       apiVersion,
       nameOf(getAccountList),
       "GET",
       "/accounts",
       "Read Account List",
       s"""${mockedDataText(false)}
Read the identifiers of the available payment account together with 
booking balance information, depending on the consent granted.

It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system. 
The addressed list of accounts depends then on the PSU ID and the stored consent addressed by consentId, 
respectively the OAuth2 access token. 

Returns all identifiers of the accounts, to which an account access has been granted to through 
the /consents endpoint by the PSU. 
In addition, relevant information about the accounts and hyperlinks to corresponding account 
information resources are provided if a related consent has been already granted.

Remark: Note that the /consents endpoint optionally offers to grant an access on all available 
payment accounts of a PSU. 
In this case, this endpoint will deliver the information about all available payment accounts 
of the PSU at this ASPSP.
""",
       json.parse(""""""),
       json.parse("""{
  "accounts" : [ {
    "cashAccountType" : { },
    "product" : "product",
    "resourceId" : "resourceId",
    "bban" : "BARC12345612345678",
    "_links" : {
      "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "usage" : "PRIV",
    "balances" : "",
    "iban" : "FR7612345987650123456789014",
    "linkedAccounts" : "linkedAccounts",
    "name" : "name",
    "currency" : "EUR",
    "details" : "details",
    "msisdn" : "+49 170 1234567",
    "bic" : "AAAADEBBXXX",
    "status" : { }
  }, {
    "cashAccountType" : { },
    "product" : "product",
    "resourceId" : "resourceId",
    "bban" : "BARC12345612345678",
    "_links" : {
      "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "usage" : "PRIV",
    "balances" : "",
    "iban" : "FR7612345987650123456789014",
    "linkedAccounts" : "linkedAccounts",
    "name" : "name",
    "currency" : "EUR",
    "details" : "details",
    "msisdn" : "+49 170 1234567",
    "bic" : "AAAADEBBXXX",
    "status" : { }
  } ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil,
       connectorMethods= Some(List("obp.getBank","obp.getBankAccounts"))
     )

     lazy val getAccountList : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
  
            bankId = BankId(defaultBankId)
  
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
  
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            
            (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountListJson(accounts, u), callContext)
          }
         }
       }
            
     resourceDocs += ResourceDoc(
       getBalances,
       apiVersion,
       nameOf(getBalances),
       "GET",
       "/accounts/ACCOUNT_ID/balances",
       "Read Balance",
       s"""${mockedDataText(false)}
Reads account data from a given account addressed by "account-id". 

**Remark:** This account-id can be a tokenised identification due to data protection reason since the path 
information might be logged on intermediary servers within the ASPSP sphere. 
This account-id then can be retrieved by the "GET Account List" call.

The account-id is constant at least throughout the lifecycle of a given consent.
""",
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "account" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
            (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), accountId, callContext)
            _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView +"userId : " + u.userId + ". account : " + accountId){
              u.hasOwnerViewAccess(BankIdAccountId(bankAccount.bankId, bankAccount.accountId))
            }
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountBalanceJSON(bankAccount, transactionRequests), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccount,
       apiVersion,
       nameOf(getCardAccount),
       "GET",
       "/card-accounts",
       "Reads a list of card accounts",
       s"""${mockedDataText(true)}
Reads a list of card accounts with additional information, e.g. balance information. 
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system. 
The addressed list of card accounts depends then on the PSU ID and the stored consent addressed by consentId, 
respectively the OAuth2 access token. 
""",
       json.parse(""""""),
       json.parse("""{
  "cardAccounts" : [ {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "usage" : "PRIV",
    "name" : "name",
    "creditLimit" : {
      "amount" : "123",
      "currency" : "EUR"
    },
    "currency" : "EUR",
    "details" : "details",
    "status" : { }
  }, {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "usage" : "PRIV",
    "name" : "name",
    "creditLimit" : {
      "amount" : "123",
      "currency" : "EUR"
    },
    "currency" : "EUR",
    "details" : "details",
    "status" : { }
  } ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getCardAccount : OBPEndpoint = {
       case "card-accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "cardAccounts" : [ {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "usage" : "PRIV",
    "name" : "name",
    "creditLimit" : {
      "amount" : "123",
      "currency" : "EUR"
    },
    "currency" : "EUR",
    "details" : "details",
    "status" : { }
  }, {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "usage" : "PRIV",
    "name" : "name",
    "creditLimit" : {
      "amount" : "123",
      "currency" : "EUR"
    },
    "currency" : "EUR",
    "details" : "details",
    "status" : { }
  } ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccountBalances,
       apiVersion,
       nameOf(getCardAccountBalances),
       "GET",
       "/card-accounts/ACCOUNT_ID/balances",
       "Read card account balances",
       s"""${mockedDataText(false)}
Reads balance data from a given card account addressed by 
"account-id". 

Remark: This account-id can be a tokenised identification due 
to data protection reason since the path information might be 
logged on intermediary servers within the ASPSP sphere. 
This account-id then can be retrieved by the 
"GET Card Account List" call
""",
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "cardAccount" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil,
       connectorMethods = Some(List("obp.getBank","obp.checkBankAccountExists" ,"obp.getTransactionRequests210"))
     )

     lazy val getCardAccountBalances : OBPEndpoint = {
       case "card-accounts" :: accountId:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
             (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
             (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), AccountId(accountId), callContext)
             _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView +"userId : " + u.userId + ". account : " + accountId){
               u.hasOwnerViewAccess(BankIdAccountId(bankAccount.bankId, bankAccount.accountId))
             }
             (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardAccountBalanceJSON(bankAccount, transactionRequests), HttpCode.`200`(callContext))
           }
       }
     }
            
     resourceDocs += ResourceDoc(
       getCardAccountTransactionList,
       apiVersion,
       nameOf(getCardAccountTransactionList),
       "GET",
       "/card-accounts/ACCOUNT_ID/transactions",
       "Read transaction list of a card account",
       s"""${mockedDataText(false)}
Reads account data from a given card account addressed by "account-id".
""",
       json.parse(""""""),
       json.parse("""{
                      "cardAccount": {
                        "maskedPan": "525412******3241"
                      },
                      "transactions": {
                        "booked": [
                          {
                            "cardTransactionId": "201710020036959",
                            "transactionAmount": {
                              "currency": "EUR",
                              "amount": "256.67"
                            },
                            "transactionDate": "2017-10-25",
                            "bookingDate": "2017-10-26",
                            "originalAmount": {
                              "currency": "SEK",
                              "amount": "2499"
                            },
                            "cardAcceptorAddress": {
                              "city": "STOCKHOLM",
                              "country": "SE"
                            },
                            "maskedPan": "525412******3241",
                            "proprietaryBankTransactionCode": "PURCHASE",
                            "invoiced": false,
                            "transactionDetails": "WIFIMARKET.SE"
                          },
                          {
                            "cardTransactionId": "201710020091863",
                            "transactionAmount": {
                              "currency": "EUR",
                              "amount": "10.72"
                            },
                            "transactionDate": "2017-10-25",
                            "bookingDate": "2017-10-26",
                            "originalAmount": {
                              "currency": "SEK",
                              "amount": "99"
                            },
                            "cardAcceptorAddress": {
                              "city": "STOCKHOLM",
                              "country": "SE"
                            },
                            "maskedPan": "525412******8999",
                            "proprietaryBankTransactionCode": "PURCHASE",
                            "invoiced": false,
                            "transactionDetails": "ICA SUPERMARKET SKOGHA"
                          }
                        ],
                        "pending": [],
                        "_links": {
                          "cardAccount": {
                            "href": "/v1.3/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99"
                          }
                        }
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM ::Nil
     )

     lazy val getCardAccountTransactionList : OBPEndpoint = {
       case "card-accounts" :: AccountId(account_id):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {

             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)

             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}

             bankId = BankId(defaultBankId)

             (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)

             (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, account_id, callContext)

             view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)

             params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }

             (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }

             (transactions, callContext) <- bankAccount.getModeratedTransactionsFuture(bank, Full(u), view, BankIdAccountId(bankId,bankAccount.accountId), callContext, params) map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }

           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardTransactionsJson(bankAccount, transactions, transactionRequests), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentAuthorisation,
       apiVersion,
       nameOf(getConsentAuthorisation),
       "GET",
       "/consents/CONSENTID/authorisations",
       "Get Consent Authorisation Sub-Resources Request",
       s"""${mockedDataText(false)}
Return a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
       json.parse(""""""),
       json.parse("""{
  "authorisationIds" : "faa3657e-13f0-4feb-a6c3-34bf21a9ae8e"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             authorisations <- Future(Authorisations.authorisationProvider.vend.getAuthorizationByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, s"$UnknownError ")
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.AuthorisationJsonV13(authorisations.map(_.authorisationId)), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentInformation,
       apiVersion,
       nameOf(getConsentInformation),
       "GET",
       "/consents/CONSENTID",
       "Get Consent Request",
       s"""${mockedDataText(false)}
Returns the content of an account information consent object. 
This is returning the data for the TPP especially in cases, 
where the consent was directly managed between ASPSP and PSU e.g. in a re-direct SCA Approach.
""",
       json.parse(""""""),
       json.parse("""{
                      "access": {
                        "accounts": [
                          {
                            "bban": "BARC12345612345678",
                            "maskedPan": "123456xxxxxx1234",
                            "iban": "FR7612345987650123456789014",
                            "currency": "EUR",
                            "msisdn": "+49 170 1234567",
                            "pan": "5409050000000000"
                          },
                          {
                            "bban": "BARC12345612345678",
                            "maskedPan": "123456xxxxxx1234",
                            "iban": "FR7612345987650123456789014",
                            "currency": "EUR",
                            "msisdn": "+49 170 1234567",
                            "pan": "5409050000000000"
                          }
                        ]
                      },
                      "recurringIndicator": false,
                      "validUntil": "2020-12-31",
                      "frequencyPerDay": 4,
                      "combinedServiceIndicator": false,
                      "lastActionDate": "2019-06-30",
                      "consentStatus": "received"
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "consents" :: consentId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, s"$ConsentNotFound ($consentId)")
               }
           } yield {
             (createGetConsentResponseJson(consent), HttpCode.`200`(callContext))
           }
         }
       }



      def tweakStatusNames(status: String) = {
        val scaStatus = status
          .replace(ConsentStatus.INITIATED.toString, "started")
          .replace(ConsentStatus.ACCEPTED.toString, "finalised")
          .replace(ConsentStatus.REJECTED.toString, "failed")
        scaStatus
      }
            
     resourceDocs += ResourceDoc(
       getConsentScaStatus,
       apiVersion,
       nameOf(getConsentScaStatus),
       "GET",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Read the SCA status of the consent authorisation.",
       s"""${mockedDataText(false)}
This method returns the SCA status of a consent initiation's authorisation sub-resource.
""",
       json.parse(""""""),
       json.parse("""{
  "scaStatus" : "started"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: authorisationId :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, s"$ConsentNotFound ($consentId)")
             }
             authorisation <- Future(Authorisations.authorisationProvider.vend.getAuthorizationByAuthorizationId(
               authorisationId
             )) map {
               unboxFullOrFail(_, callContext, s"$AuthorisationNotFound Current AUTHORISATION_ID($authorisationId)")
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(authorisation.scaStatus), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentStatus,
       apiVersion,
       nameOf(getConsentStatus),
       "GET",
       "/consents/CONSENTID/status",
       "Consent status request",
       s"""${mockedDataText(false)}
            Read the status of an account information consent resource.""",
       json.parse(""""""),
       json.parse("""{
                      "consentStatus": "received"
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")   :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "consents" :: consentId:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
           } yield {
             val status = consent.status.toLowerCase()
               .replace(ConsentStatus.REVOKED.toString.toLowerCase(), "revokedByPsu")
             (JSONFactory_BERLIN_GROUP_1_3.ConsentStatusJsonV13(status), HttpCode.`200`(callContext))
           }
             
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionDetails,
       apiVersion,
       nameOf(getTransactionDetails),
       "GET", 
       "/accounts/ACCOUNT_ID/transactions/TRANSACTIONID", 
       "Read Transaction Details",
       s"""${mockedDataText(true)}
Reads transaction details from a given transaction addressed by "transactionId" on a given account addressed 
by "account-id". This call is only available on transactions as reported in a JSON format. 

**Remark:** Please note that the PATH might be already given in detail by the corresponding entry of the response 
of the "Read Transaction List" call within the _links subfield.

            """,
       json.parse(""""""),
       json.parse("""{
  "description": "Example for transaction details",
  "value": {
    "transactionsDetails": {
      "transactionId": "1234567",
      "creditorName": "John Miles",
      "creditorAccount": {
        "iban": "DE67100100101306118605"
      },
      "mandateId": "Mandate-2018-04-20-1234",
      "transactionAmount": {
        "currency": "EUR",
        "amount": "-256.67"
      },
      "bookingDate": "2017-10-25",
      "valueDate": "2017-10-26",
      "remittanceInformationUnstructured": "Example 1",
      "bankTransactionCode": "PMNT-RCVD-ESDD"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getTransactionDetails : OBPEndpoint = {
       case "accounts" :: account_id:: "transactions" :: transactionid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "description": "Example for transaction details",
  "value": {
    "transactionsDetails": {
      "transactionId": "1234567",
      "creditorName": "John Miles",
      "creditorAccount": {
        "iban": "DE67100100101306118605"
      },
      "mandateId": "Mandate-2018-04-20-1234",
      "transactionAmount": {
        "currency": "EUR",
        "amount": "-256.67"
      },
      "bookingDate": "2017-10-25",
      "valueDate": "2017-10-26",
      "remittanceInformationUnstructured": "Example 1",
      "bankTransactionCode": "PMNT-RCVD-ESDD"
    }
  }
}"""), callContext)
           }
         }
       }

     resourceDocs += ResourceDoc(
       getTransactionList,
       apiVersion,
       nameOf(getTransactionList),
       "GET",
       "/accounts/ACCOUNT_ID/transactions",
       "Read transaction list of an account",
       s"""${mockedDataText(false)}
Read transaction reports or transaction lists of a given account ddressed by "account-id",
depending on the steering parameter "bookingStatus" together with balances.
For a given account, additional parameters are e.g. the attributes "dateFrom" and "dateTo".
The ASPSP might add balance information, if transaction lists without balances are not supported. """,
       json.parse(""""""),
       json.parse("""{
                      "account": {
                        "iban": "DE2310010010123456788"
                      },
                      "transactions": {
                        "booked": [
                          {
                            "transactionId": "1234567",
                            "creditorName": "John Miles",
                            "creditorAccount": {
                              "iban": "DE67100100101306118605"
                            },
                            "transactionAmount": {
                              "currency": "EUR",
                              "amount": "256.67"
                            },
                            "bookingDate": "2017-10-25",
                            "valueDate": "2017-10-26",
                            "remittanceInformationUnstructured": "Example 1"
                          },
                          {
                            "transactionId": "1234568",
                            "debtorName": "Paul Simpson",
                            "debtorAccount": {
                              "iban": "NL76RABO0359400371"
                            },
                            "transactionAmount": {
                              "currency": "EUR",
                              "amount": "343.01"
                            },
                            "bookingDate": "2017-10-25",
                            "valueDate": "2017-10-26",
                            "remittanceInformationUnstructured": "Example 2"
                          }
                        ],
                        "pending": [
                          {
                            "transactionId": "1234569",
                            "creditorName": "Claude Renault",
                            "creditorAccount": {
                              "iban": "FR7612345987650123456789014"
                            },
                            "transactionAmount": {
                              "currency": "EUR",
                              "amount": "-100.03"
                            },
                            "valueDate": "2017-10-26",
                            "remittanceInformationUnstructured": "Example 3"
                          }
                        ],
                        "_links": {
                          "account": {
                            "href": "/v1.3/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f"
                          }
                        }
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "accounts" :: AccountId(account_id):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {

            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- passesPsd2Aisp(callContext)

            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}

            bankId = BankId(defaultBankId)

            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, account_id, callContext)

            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 

            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <-bankAccount.getModeratedTransactionsFuture(bank, Full(u), view, BankIdAccountId(bankId,bankAccount.accountId), callContext, params) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            } yield {
              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(bankAccount, transactions, transactionRequests), callContext)
            }
         }
       }
            
     resourceDocs += ResourceDoc(
       readAccountDetails,
       apiVersion,
       nameOf(readAccountDetails),
       "GET",
       "/accounts/ACCOUNT_ID",
       "Read Account Details",
       s"""${mockedDataText(false)}
Reads details about an account, with balances where required. 
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system. 
The addressed details of this account depends then on the stored consent addressed by consentId, 
respectively the OAuth2 access token. **NOTE:** The account-id can represent a multicurrency account. 
In this case the currency code is set to "XXX". Give detailed information about the addressed account. 
Give detailed information about the addressed account together with balance information

            """,
       json.parse(""""""),
       json.parse("""{
  "cashAccountType" : { },
  "product" : "product",
  "resourceId" : "resourceId",
  "bban" : "BARC12345612345678",
  "_links" : {
    "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "usage" : "PRIV",
  "balances" : "",
  "iban" : "FR7612345987650123456789014",
  "linkedAccounts" : "linkedAccounts",
  "name" : "name",
  "currency" : "EUR",
  "details" : "details",
  "msisdn" : "+49 170 1234567",
  "bic" : "AAAADEBBXXX",
  "status" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil,
       connectorMethods=Some(List("obp.checkBankAccountExists"))
     )

     lazy val readAccountDetails : OBPEndpoint = {
       case "accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
             (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), AccountId(accountId), callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createAccountDetailsJson(bankAccount, u), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       readCardAccount,
       apiVersion,
       nameOf(readCardAccount),
       "GET",
       "/card-accounts/ACCOUNT_ID",
       "Reads details about a card account",
       s"""${mockedDataText(false)}
Reads details about a card account. 
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system. 
The addressed details of this account depends then on the stored consent addressed by consentId, 
respectively the OAuth2 access token.
""",
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "product" : "product",
  "resourceId" : "resourceId",
  "maskedPan" : "123456xxxxxx1234",
  "_links" : {
    "balances" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983",
    "transactions" : "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "usage" : "PRIV",
  "name" : "name",
  "creditLimit" : {
    "amount" : "123",
    "currency" : "EUR"
  },
  "currency" : "EUR",
  "details" : "details",
  "status" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val readCardAccount : OBPEndpoint = {
       case "card-accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             _ <- Helper.booleanToFuture(failMsg = DefaultBankIdNotSet) {
               defaultBankId != "DEFAULT_BANK_ID_NOT_SET"
             }
             (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), AccountId(accountId), callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardAccountDetailsJson(bankAccount, u), callContext)
           }
       }
     }

     resourceDocs += ResourceDoc(
       startConsentAuthorisation,
       apiVersion,
       nameOf(startConsentAuthorisation),
       "POST",
       "/consents/CONSENTID/authorisations",
       "Start the authorisation process for a consent",
       s"""${mockedDataText(false)}
Create an authorisation sub-resource and start the authorisation process of a consent. 
The message might in addition transmit authentication and authorisation related data. 
his method is iterated n times for a n times SCA authorisation in a corporate context, 
each creating an own authorisation sub-endpoint for the corresponding PSU authorising the consent. 
The ASPSP might make the usage of this access method unnecessary, since the related authorisation
 resource will be automatically created by the ASPSP after the submission of the consent data with the 
 first POST consents call. The start authorisation process is a process which is needed for creating 
 a new authorisation or cancellation sub-resource. 
 
 This applies in the following scenarios: * The ASPSP has indicated with an 'startAuthorisation' hyperlink 
 in the preceding Payment Initiation Response that an explicit start of the authorisation process is needed by the TPP. 
 The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded by using 
 the extended forms. 
 * 'startAuthorisationWithPsuIdentfication', 
 * 'startAuthorisationWithPsuAuthentication' 
 * 'startAuthorisationWithEncryptedPsuAuthentication' 
 * 'startAuthorisationWithAuthentciationMethodSelection' 
 * The related payment initiation cannot yet be executed since a multilevel SCA is mandated. 
 * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceding Payment Cancellation 
 Response that an explicit start of the authorisation process is needed by the TPP. 
 
 The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded by 
 using the extended forms as indicated above. 
 * The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for executing the cancellation. 
 * The signing basket needs to be authorised yet.

""",
       json.parse(""""""),
       json.parse("""{
                       "scaStatus": "received",
                       "psuMessage": "Please use your BankApp for transaction Authorisation.",
                       "_links":
                         {
                           "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                         }
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val startConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
              }
             authorization <- Future(Authorisations.authorisationProvider.vend.createAuthorization(
               "",
               consent.consentId,
               AuthenticationType.SMS_OTP.toString,
               "",
               ScaStatus.received.toString,
               "12345" // TODO Implement SMS sending
             )) map {
               unboxFullOrFail(_, callContext, s"$UnknownError ")
             }
           } yield {
             (createStartConsentAuthorisationJson(consent, authorization), HttpCode.`201`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updateConsentsPsuData,
       apiVersion,
       nameOf(updateConsentsPsuData),
       "PUT",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Update PSU Data for consents",
       s"""${mockedDataText(false)}
This method update PSU data on the consents resource if needed. It may authorise a consent within the Embedded 
SCA Approach where needed. Independently from the SCA Approach it supports 
e.g. the selection of the authentication method and a non-SCA PSU authentication. 
This methods updates PSU data on the cancellation authorisation resource if needed. 
There are several possible Update PSU Data requests in the context of a consent request if needed, 
which depends on the SCA approach: * Redirect SCA Approach: A specific Update PSU Data Request is applicable 
for 
* the selection of authentication methods, before choosing the actual SCA approach. 
* Decoupled SCA Approach: A specific Update PSU Data Request is only applicable for 
* adding the PSU Identification, if not provided yet in the Payment Initiation Request or the Account Information Consent Request, 
or if no OAuth2 access token is used, or 
* the selection of authentication methods. 
* Embedded SCA Approach: The Update PSU Data Request might be used 
* to add credentials as a first factor authentication data of the PSU and 
* to select the authentication method and 
* transaction authorisation. 
The SCA Approach might depend on the chosen SCA method. For that reason, 
the following possible Update PSU Data request can apply to all SCA approaches: 
* Select an SCA method in case of several SCA methods are available for the customer. There are the following request types on this access path: 
* Update PSU Identification * Update PSU Authentication 
* Select PSU Autorization Method WARNING: This method need a reduced header, therefore many optional elements are not present. 
Maybe in a later version the access path will change. 
* Transaction Authorisation WARNING: This method need a reduced header, therefore many optional elements are not present. 
Maybe in a later version the access path will change.

            """,
       json.parse("""{
                      "access": {"accounts": []},
                      "recurringIndicator": false,
                      "validUntil": "2020-12-31",
                      "frequencyPerDay": 4,
                      "combinedServiceIndicator": false
                    }"""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val updateConsentsPsuData : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: authorisationId :: Nil JsonPut jsonPut -> _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
             authorisation <- Future(Authorisations.authorisationProvider.vend.getAuthorizationByAuthorizationId(
               authorisationId
             )) map {
               unboxFullOrFail(_, callContext, s"$AuthorisationNotFound Current AUTHORISATION_ID($authorisationId)")
             }
             failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentJson "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               jsonPut.extract[PostConsentJson]
             }

             failMsg = s"$InvalidDateFormat Current `validUntil` field is ${consentJson.validUntil}. Please use this format ${DateWithDayFormat.toPattern}!"
             validUntil <- NewStyle.function.tryons(failMsg, 400, callContext) {
               new SimpleDateFormat(DateWithDay).parse(consentJson.validUntil)
             }

             failMsg = s"$InvalidJsonContent Only Support empty accounts List for now. It will return an accessible account List. "
             _ <- Helper.booleanToFuture(failMsg) {consentJson.access.accounts.get.isEmpty}
             consent <- Future(Consents.consentProvider.vend.updateBerlinGroupConsent(
               consentId,
               u,
               recurringIndicator = consentJson.recurringIndicator,
               validUntil = validUntil,
               frequencyPerDay = consentJson.frequencyPerDay,
               combinedServiceIndicator = consentJson.combinedServiceIndicator
             )) map {
               i => connectorEmptyResponse(i, callContext)
             }
             } yield {
             (createPostConsentResponseJson(consent), HttpCode.`200`(callContext))
           }
         }
       }
  
}



