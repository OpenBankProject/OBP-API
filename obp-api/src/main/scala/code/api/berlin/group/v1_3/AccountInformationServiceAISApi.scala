package code.api.builder.AccountInformationServiceAISApi

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.{JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiTag, ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
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

            
     resourceDocs += ResourceDoc(
       createConsent,
       apiVersion,
       nameOf(createConsent),
       "POST",
       "/consents",
       "Create consent",
       s"""${mockedDataText(true)}
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
  "access" : {
    "balances" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "availableAccounts" : "allAccounts",
    "accounts" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "transactions" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "allPsd2" : "allAccounts"
  },
  "combinedServiceIndicator" : false,
  "validUntil" : "2020-12-31",
  "recurringIndicator" : false,
  "frequencyPerDay" : 4
}"""),
       json.parse("""{
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
  },
  "consentId" : { },
  "scaMethods" : "",
  "_links" : {
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "self" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithTransactionAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "status" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "consentStatus" : { },
  "message" : "message"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
  },
  "consentId" : { },
  "scaMethods" : "",
  "_links" : {
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithAuthenticationMethodSelection" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "self" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithTransactionAuthorisation" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "status" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "consentStatus" : { },
  "message" : "message"
}"""), callContext)
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
       s"""${mockedDataText(true)}
            The TPP can delete an account information consent object if needed.""",
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "consents" :: consentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (NotImplemented, callContext)
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
      "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
      "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getAccountList : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
  
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
  
            bankId = BankId(defaultBankId)
  
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
  
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            
            Full((coreAccounts,callContext1)) <- {Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)}
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createTransactionListJSON(coreAccounts), callContext)
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
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
            (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), accountId, callContext)
            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})") {(u.hasViewAccess(view))}
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
      "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
      "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "cardAccounts" : [ {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
      "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
       s"""${mockedDataText(true)}
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
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getCardAccountBalances : OBPEndpoint = {
       case "card-accounts" :: account_id:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "balances" : "",
  "cardAccount" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccountTransactionList,
       apiVersion,
       nameOf(getCardAccountTransactionList),
       "GET",
       "/card-accounts/ACCOUNT_ID/transactions",
       "Read transaction list of an account",
       s"""${mockedDataText(false)}
Reads account data from a given card account addressed by "account-id".
""",
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "_links" : {
    "download" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "cardTransactions" : {
    "booked" : "",
    "_links" : {
      "next" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "last" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "previous" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "cardAccount" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "first" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "pending" : ""
  },
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
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getCardAccountTransactionList : OBPEndpoint = {
       case "card-accounts" :: AccountId(account_id):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {

            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}

            bankId = BankId(defaultBankId)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, account_id, callContext)

            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 

            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(Full(u), view, callContext, params: _*)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            } yield {
              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(transactions, transactionRequests), callContext)
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
       s"""${mockedDataText(true)}
Return a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
       json.parse(""""""),
       json.parse("""{
  "authorisationIds" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "authorisationIds" : ""
}"""), callContext)
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
       s"""${mockedDataText(true)}
Returns the content of an account information consent object. 
This is returning the data for the TPP especially in cases, 
where the consent was directly managed between ASPSP and PSU e.g. in a re-direct SCA Approach.
""",
       json.parse(""""""),
       json.parse("""{
  "access" : {
    "balances" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "availableAccounts" : "allAccounts",
    "accounts" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "transactions" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "allPsd2" : "allAccounts"
  },
  "consentStatus" : { },
  "validUntil" : "2020-12-31",
  "lastActionDate" : "2018-07-01",
  "recurringIndicator" : false,
  "frequencyPerDay" : 4
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "access" : {
    "balances" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "availableAccounts" : "allAccounts",
    "accounts" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "transactions" : [ {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    }, {
      "bban" : "BARC12345612345678",
      "maskedPan" : "123456xxxxxx1234",
      "iban" : "FR7612345987650123456789014",
      "currency" : "EUR",
      "msisdn" : "+49 170 1234567",
      "pan" : "5409050000000000"
    } ],
    "allPsd2" : "allAccounts"
  },
  "consentStatus" : { },
  "validUntil" : "2020-12-31",
  "lastActionDate" : "2018-07-01",
  "recurringIndicator" : false,
  "frequencyPerDay" : 4
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentScaStatus,
       apiVersion,
       nameOf(getConsentScaStatus),
       "GET",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Read the SCA status of the consent authorisation.",
       s"""${mockedDataText(true)}
This method returns the SCA status of a consent initiation's authorisation sub-resource.
""",
       json.parse(""""""),
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""), callContext)
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
       s"""${mockedDataText(true)}
            Read the status of an account information consent resource.""",
       json.parse(""""""),
       json.parse("""{
  "consentStatus" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "consents" :: consentid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "consentStatus" : { }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionDetails,
       apiVersion,
       nameOf(getTransactionDetails),
       "GET",
       "/accounts/ACCOUNT_ID/transactions/RESOURCEID",
       "Read Transaction Details",
       s"""${mockedDataText(true)}
Reads transaction details from a given transaction addressed by "resourceId" on a given account addressed by "account-id". 
This call is only available on transactions as reported in a JSON format.

**Remark:** Please note that the PATH might be already given in detail by the corresponding entry of the response of the 
"Read Transaction List" call within the _links subfield.
""",
       json.parse(""""""),
       json.parse("""{
  "debtorAccount" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  },
  "creditorName" : "Creditor Name",
  "_links" : {
    "transactionDetails" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "remittanceInformationStructured" : "remittanceInformationStructured",
  "ultimateCreditor" : "Ultimate Creditor",
  "bankTransactionCode" : "PMNT-RCDT-ESCT",
  "debtorName" : "Debtor Name",
  "valueDate" : "2000-01-23",
  "endToEndId" : "endToEndId",
  "transactionId" : "transactionId",
  "ultimateDebtor" : "Ultimate Debtor",
  "exchangeRate" : "",
  "creditorAccount" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  },
  "mandateId" : "mandateId",
  "purposeCode" : { },
  "transactionAmount" : {
    "amount" : "123",
    "currency" : "EUR"
  },
  "proprietaryBankTransactionCode" : { },
  "bookingDate" : { },
  "remittanceInformationUnstructured" : "remittanceInformationUnstructured",
  "checkId" : "checkId",
  "creditorId" : "creditorId",
  "entryReference" : "entryReference"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getTransactionDetails : OBPEndpoint = {
       case "accounts" :: account_id:: "transactions" :: resourceid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "debtorAccount" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  },
  "creditorName" : "Creditor Name",
  "_links" : {
    "transactionDetails" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "remittanceInformationStructured" : "remittanceInformationStructured",
  "ultimateCreditor" : "Ultimate Creditor",
  "bankTransactionCode" : "PMNT-RCDT-ESCT",
  "debtorName" : "Debtor Name",
  "valueDate" : "2000-01-23",
  "endToEndId" : "endToEndId",
  "transactionId" : "transactionId",
  "ultimateDebtor" : "Ultimate Debtor",
  "exchangeRate" : "",
  "creditorAccount" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  },
  "mandateId" : "mandateId",
  "purposeCode" : { },
  "transactionAmount" : {
    "amount" : "123",
    "currency" : "EUR"
  },
  "proprietaryBankTransactionCode" : { },
  "bookingDate" : { },
  "remittanceInformationUnstructured" : "remittanceInformationUnstructured",
  "checkId" : "checkId",
  "creditorId" : "creditorId",
  "entryReference" : "entryReference"
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
Read transaction reports or transaction lists of a given account ddressed by "account-id", depending on the steering parameter "bookingStatus" together with balances.

For a given account, additional parameters are e.g. the attributes "dateFrom" and "dateTo". 
The ASPSP might add balance information, if transaction lists without balances are not supported.
""",
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "_links" : {
    "download" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "transactions" : {
    "booked" : "",
    "_links" : {
      "next" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "last" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "previous" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "account" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
      "first" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "pending" : ""
  },
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
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "accounts" :: AccountId(account_id):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {

            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}

            bankId = BankId(defaultBankId)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, account_id, callContext)

            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 

            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(Full(u), view, callContext, params: _*)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            } yield {
              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(transactions, transactionRequests), callContext)
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
       s"""${mockedDataText(true)}
Reads details about an account, with balances where required. 
It is assumed that a consent of the PSU to 
this access is already given and stored on the ASPSP system. 
The addressed details of this account depends then on the stored consent addressed by consentId, 
respectively the OAuth2 access token.

**NOTE:** The account-id can represent a multicurrency account. 
In this case the currency code is set to "XXX".

Give detailed information about the addressed account.

Give detailed information about the addressed account together with balance information
""",
       json.parse(""""""),
       json.parse("""{
  "cashAccountType" : { },
  "product" : "product",
  "resourceId" : "resourceId",
  "bban" : "BARC12345612345678",
  "_links" : {
    "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val readAccountDetails : OBPEndpoint = {
       case "accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "cashAccountType" : { },
  "product" : "product",
  "resourceId" : "resourceId",
  "bban" : "BARC12345612345678",
  "_links" : {
    "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
}"""), callContext)
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
       s"""${mockedDataText(true)}
Reads details about a card account. 
It is assumed that a consent of the PSU to this access is already given 
and stored on the ASPSP system. The addressed details of this account depends 
then on the stored consent addressed by consentId, respectively the OAuth2 
access token.
""",
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "product" : "product",
  "resourceId" : "resourceId",
  "maskedPan" : "123456xxxxxx1234",
  "_links" : {
    "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
       case "card-accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "balances" : "",
  "product" : "product",
  "resourceId" : "resourceId",
  "maskedPan" : "123456xxxxxx1234",
  "_links" : {
    "balances" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "transactions" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
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
}"""), callContext)
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
       s"""${mockedDataText(true)}
Create an authorisation sub-resource and start the authorisation process of a consent. 
The message might in addition transmit authentication and authorisation related data.

his method is iterated n times for a n times SCA authorisation in a 
corporate context, each creating an own authorisation sub-endpoint for 
the corresponding PSU authorising the consent.

The ASPSP might make the usage of this access method unnecessary, 
since the related authorisation resource will be automatically created by 
the ASPSP after the submission of the consent data with the first POST consents call.

The start authorisation process is a process which is needed for creating a new authorisation 
or cancellation sub-resource. 

This applies in the following scenarios:

  * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding Payment 
    Initiation Response that an explicit start of the authorisation process is needed by the TPP. 
    The 'startAuthorisation' hyperlink can transport more information about data which needs to be 
    uploaded by using the extended forms.
    * 'startAuthorisationWithPsuIdentfication', 
    * 'startAuthorisationWithPsuAuthentication' #TODO
    * 'startAuthorisationWithAuthentciationMethodSelection' 
  * The related payment initiation cannot yet be executed since a multilevel SCA is mandated.
  * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding 
    Payment Cancellation Response that an explicit start of the authorisation process is needed by the TPP. 
    The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded 
    by using the extended forms as indicated above.
  * The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for 
    executing the cancellation.
  * The signing basket needs to be authorised yet.
""",
       json.parse(""""""),
       json.parse("""{
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
  },
  "scaMethods" : "",
  "scaStatus" : "psuAuthenticated",
  "_links" : {
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "selectAuthenticationMethod" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "authoriseTransaction" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "updatePsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "psuMessage" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val startConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : "data"
  },
  "scaMethods" : "",
  "scaStatus" : "psuAuthenticated",
  "_links" : {
    "scaStatus" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithEncryptedPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaRedirect" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "selectAuthenticationMethod" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "startAuthorisationWithPsuAuthentication" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "authoriseTransaction" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "scaOAuth" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    "updatePsuIdentification" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
  },
  "chosenScaMethod" : "",
  "psuMessage" : { }
}"""), callContext)
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
       s"""${mockedDataText(true)}
This method update PSU data on the consents  resource if needed. 
It may authorise a consent within the Embedded SCA Approach where needed.

Independently from the SCA Approach it supports e.g. the selection of 
the authentication method and a non-SCA PSU authentication.

This methods updates PSU data on the cancellation authorisation resource if needed. 

There are several possible Update PSU Data requests in the context of a consent request if needed, 
which depends on the SCA approach:

* Redirect SCA Approach:
  A specific Update PSU Data Request is applicable for 
    * the selection of authentication methods, before choosing the actual SCA approach.
* Decoupled SCA Approach:
  A specific Update PSU Data Request is only applicable for
  * adding the PSU Identification, if not provided yet in the Payment Initiation Request or the Account Information Consent Request, or if no OAuth2 access token is used, or
  * the selection of authentication methods.
* Embedded SCA Approach: 
  The Update PSU Data Request might be used 
  * to add credentials as a first factor authentication data of the PSU and
  * to select the authentication method and
  * transaction authorisation.

The SCA Approach might depend on the chosen SCA method. 
For that reason, the following possible Update PSU Data request can apply to all SCA approaches:

* Select an SCA method in case of several SCA methods are available for the customer.

There are the following request types on this access path:
  * Update PSU Identification
  * Update PSU Authentication
  * Select PSU Autorization Method 
    WARNING: This method need a reduced header, 
    therefore many optional elements are not present. 
    Maybe in a later version the access path will change.
  * Transaction Authorisation
    WARNING: This method need a reduced header, 
    therefore many optional elements are not present. 
    Maybe in a later version the access path will change.
""",
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val updateConsentsPsuData : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse(""""""""), callContext)
           }
         }
       }

}



