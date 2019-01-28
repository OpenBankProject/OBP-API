package code.api.builder.AccountInformationServiceAISApi

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_AccountInformationServiceAISApi { self: RestHelper =>
  val ImplementationsAccountInformationServiceAISApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
    
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
       "createConsent",
       "POST", 
       "/v1/consents", 
       "Create consent",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "v1":: "consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "deleteConsent",
       "DELETE", 
       "/v1/consents/CONSENTID", 
       "Delete Consent",
       "", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "v1":: "consents" :: consentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountList, 
       apiVersion, 
       "getAccountList",
       "GET", 
       "/v1/accounts", 
       "Read Account List",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getAccountList : OBPEndpoint = {
       case "v1":: "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
  
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
       "getBalances",
       "GET", 
       "/v1/accounts/ACCOUNT_ID/balances", 
       "Read Balance",
       "", 
       json.parse(""""""),
       json.parse("""{
  "balances" : [
    {
      "balanceAmount": {
        "amount": "123",
        "currency": "EUR"
      },
      "balanceType": "closingBooked",
      "lastChangeDateTime": "2019-01-28T06:55:48.831Z",
      "lastCommittedTransaction": "string",
      "referenceDate": "string"
    }
  ],
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "v1":: "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
          (json.parse("""{
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccount, 
       apiVersion, 
       "getCardAccount",
       "GET", 
       "/card-accounts", 
       "Reads a list of card accounts",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getCardAccount : OBPEndpoint = {
       case "card-accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getCardAccountBalances",
       "GET", 
       "/card-accounts/ACCOUNT_ID/balances", 
       "Read card account balances",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getCardAccountBalances : OBPEndpoint = {
       case "card-accounts" :: account_id:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getCardAccountTransactionList",
       "GET", 
       "/card-accounts/ACCOUNT_ID/transactions", 
       "Read transaction list of an account",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getCardAccountTransactionList : OBPEndpoint = {
       case "card-accounts" :: account_id:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentAuthorisation, 
       apiVersion, 
       "getConsentAuthorisation",
       "GET", 
       "/v1/consents/CONSENTID/authorisations", 
       "Get Consent Authorisation Sub-Resources Request",
       "", 
       json.parse(""""""),
       json.parse("""{
  "authorisationIds" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getConsentInformation",
       "GET", 
       "/v1/consents/CONSENTID", 
       "Get Consent Request",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "v1":: "consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getConsentScaStatus",
       "GET", 
       "/v1/consents/CONSENTID/authorisations/AUTHORISATIONID", 
       "Read the SCA status of the consent authorisation.",
       "", 
       json.parse(""""""),
       json.parse("""{
  "scaStatus" : "psuAuthenticated"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getConsentStatus",
       "GET", 
       "/v1/consents/CONSENTID/status", 
       "Consent status request",
       "", 
       json.parse(""""""),
       json.parse("""{
  "consentStatus" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getTransactionDetails",
       "GET", 
       "/v1/accounts/ACCOUNT_ID/transactions/RESOURCEID", 
       "Read Transaction Details",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getTransactionDetails : OBPEndpoint = {
       case "v1":: "accounts" :: account_id:: "transactions" :: resourceid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "getTransactionList",
       "GET", 
       "/v1/accounts/ACCOUNT_ID/transactions/", 
       "Read transaction list of an account",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "v1":: "accounts" :: account_id:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       readAccountDetails, 
       apiVersion, 
       "readAccountDetails",
       "GET", 
       "/v1/accounts/ACCOUNT_ID", 
       "Read Account Details",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val readAccountDetails : OBPEndpoint = {
       case "v1":: "accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "readCardAccount",
       "GET", 
       "/card-accounts/ACCOUNT_ID", 
       "Reads details about a card account",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val readCardAccount : OBPEndpoint = {
       case "card-accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "startConsentAuthorisation",
       "POST", 
       "/v1/consents/CONSENTID/authorisations", 
       "Start the authorisation process for a consent",
       "", 
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
       AccountInformationServiceAISApi :: Nil
     )

     lazy val startConsentAuthorisation : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       "updateConsentsPsuData",
       "PUT", 
       "/v1/consents/CONSENTID/authorisations/AUTHORISATIONID", 
       "Update PSU Data for consents",
       "", 
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val updateConsentsPsuData : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse(""""""""), callContext)
           }
         }
       }

  }
}



