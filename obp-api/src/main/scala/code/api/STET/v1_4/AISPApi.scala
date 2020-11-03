package code.api.STET.v1_4

import code.api.APIFailureNewStyle
import code.api.STET.v1_4.JSONFactory_STET_1_4._
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object APIMethods_AISPApi extends RestHelper {
    val apiVersion =  OBP_STET_1_4.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsBalancesGet ::
      accountsGet ::
      accountsTransactionsGet ::
      consentsPut ::
      endUserIdentityGet ::
      trustedBeneficiariesGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsBalancesGet, 
       apiVersion, 
       nameOf(accountsBalancesGet),
       "GET", 
       "/accounts/ACCOUNTRESOURCEID/balances", 
       "Retrieval of an account balances report (AISP)",
       s"""${mockedDataText(false)}
            ### Description

This call returns a set of balances for a given PSU account that is specified by the AISP through an account resource Identification

### Prerequisites

* The TPP has been registered by the Registration Authority for the AISP role
* The TPP and the PSU have a contract that has been enrolled by the ASPSP
  * At this step, the ASPSP has delivered an OAUTH2 "Authorization Code" or "Resource Owner Password" access token to the TPP (cf. § 3.4.2).

* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its OAUTH2 "Authorization Code" or "Resource Owner Password" access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.
* The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.
* The TPP has previously retrieved the list of available accounts for the PSU

### Business flow

The AISP requests the ASPSP on one of the PSU's accounts.  
The ASPSP answers by providing a list of balances on this account.

* The ASPSP must provide at least the accounting balance on the account.
* The ASPSP can provide other balance restitutions, e.g. instant balance, as well, if possible.
* Actually, from the PSD2 perspective, any other balances that are provided through the Web-Banking service of the ASPSP must also be provided by this ASPSP through the API.


            """,
       emptyObjectJson,
       json.parse("""{
                    |  "balances": [
                    |    {
                    |      "name": "Solde comptable au 12/01/2017",
                    |      "balanceAmount": {
                    |        "currency": "EUR",
                    |        "amount": "123.45"
                    |      },
                    |      "balanceType": "CLBD",
                    |      "lastCommittedTransaction": "A452CH"
                    |    }
                    |  ],
                    |  "_links": {
                    |    "self": {
                    |      "href": "v1/accounts/Alias1/balances-report"
                    |    },
                    |    "parent-list": {
                    |      "href": "v1/accounts"
                    |    },
                    |    "transactions": {
                    |      "href": "v1/accounts/Alias1/transactions"
                    |    }
                    |  }
                    |}
                    |""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AISP") :: Nil
     )

     lazy val accountsBalancesGet : OBPEndpoint = {
       case "accounts" :: accountresourceid:: "balances" :: Nil JsonGet _ => {
         cc => 
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
             (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
             (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), AccountId(accountresourceid), callContext)
             view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
             moderatedAccount <- Future {bankAccount.moderatedBankAccount(view, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), Full(u), callContext)} map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }
          } yield {
             (createAccountBalanceJSON(moderatedAccount), HttpCode.`200`(callContext))
          }
       }
     }
            
     resourceDocs += ResourceDoc(
       accountsGet, 
       apiVersion, 
       nameOf(accountsGet),
       "GET", 
       "/accounts", 
       "Retrieval of the PSU accounts (AISP)",
       s"""${mockedDataText(false)}
            ### Description

This call returns all payment accounts that are relevant the PSU on behalf of whom the AISP is connected. Thanks to HYPERMEDIA, each account is returned with the links aiming to ease access to the relevant transactions and balances. The result may be subject to pagination (i.e. retrieving a partial result in case of having too many results) through a set of pages by the ASPSP. Thereafter, the AISP may ask for the first, next, previous or last page of results.

### Prerequisites

* The TPP has been registered by the Registration Authority for the AISP role.
* The TPP and the PSU have a contract that has been enrolled by the ASPSP
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its OAUTH2 "Authorization Code" or "Resource Owner Password" access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.
* The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.

### Business Flow

The TPP sends a request to the ASPSP for retrieving the list of the PSU payment accounts. The ASPSP computes the relevant PSU accounts and builds the answer as an accounts list. The result may be subject to pagination in order to avoid an excessive result set. Each payment account will be provided with its characteristics.

            """,
       emptyObjectJson,
       json.parse("""{
                    |  "accounts": [
                    |    {
                    |      "resourceId": "Alias1",
                    |      "bicFi": "BNKAFRPPXXX",
                    |      "name": "Compte de Mr et Mme Dupont",
                    |      "usage": "PRIV",
                    |      "cashAccountType": "CACC",
                    |      "currency": "EUR",
                    |      "psuStatus": "Co-account Holder",
                    |      "_links": {
                    |        "balances": {
                    |          "href": "v1/accounts/Alias1/balances"
                    |        },
                    |        "transactions": {
                    |          "href": "v1/accounts/Alias1/transactions"
                    |        }
                    |      }
                    |    }
                    |  ],
                    |  "_links": {
                    |    "self": {
                    |      "href": "v1/accounts?page=2"
                    |    },
                    |    "first": {
                    |      "href": "v1/accounts"
                    |    },
                    |    "last": {
                    |      "href": "v1/accounts?page=last",
                    |      "templated": true
                    |    },
                    |    "next": {
                    |      "href": "v1/accounts?page=3",
                    |      "templated": true
                    |    },
                    |    "prev": {
                    |      "href": "v1/accounts",
                    |      "templated": true
                    |    }
                    |  }
                    |}""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AISP") :: Nil
     )

     lazy val accountsGet : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc => 
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
  
              _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
    
              bankId = BankId(defaultBankId)
    
              (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
    
              availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)

             (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
              
            } yield {
              (createTransactionListJSON(accounts), callContext)
            }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsTransactionsGet, 
       apiVersion, 
       nameOf(accountsTransactionsGet),
       "GET", 
       "/accounts/ACCOUNTRESOURCEID/transactions", 
       "Retrieval of an account transaction set (AISP)",
       s"""${mockedDataText(false)}
            ### Description

This call returns transactions for an account for a given PSU account that is specified by the AISP through an account resource identification. The request may use some filter parameter in order to restrict the query

* on a given imputation date range
* past a given incremental technical identification

The result may be subject to pagination (i.e. retrieving a partial result in case of having too many results) through a set of pages by the ASPSP. Thereafter, the AISP may ask for the first, next, previous or last page of results.

### Prerequisites

* The TPP has been registered by the Registration Authority for the AISP role
* The TPP and the PSU have a contract that has been enrolled by the ASPSP
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its OAUTH2 "Authorization Code" or "Resource Owner Password" access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) is any.
* The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.
* The TPP has previously retrieved the list of available accounts for the PSU

### Business flow

The AISP requests the ASPSP on one of the PSU's accounts. It may specify some selection criteria. The ASPSP answers by a set of transactions that matches the query. The result may be subject to pagination in order to avoid an excessive result set.

            """,
       emptyObjectJson,
       json.parse("""{
                    |  "transactions": [
                    |    {
                    |      "entryReference": "AF5T2",
                    |      "transactionAmount": {
                    |        "currency": "EUR",
                    |        "amount": "12.25"
                    |      },
                    |      "creditDebitIndicator": "CRDT",
                    |      "status": "BOOK",
                    |      "bookingDate": "2018-02-12",
                    |      "remittanceInformation": [
                    |        "SEPA CREDIT TRANSFER from PSD2Company"
                    |      ]
                    |    }
                    |  ],
                    |  "_links": {
                    |    "self": {
                    |      "href": "v1/accounts/Alias1/transactions"
                    |    },
                    |    "parent-list": {
                    |      "href": "v1/accounts"
                    |    },
                    |    "balances": {
                    |      "href": "v1/accounts/Alias1/balances"
                    |    },
                    |    "last": {
                    |      "href": "v1/accounts/sAlias1/transactions?page=last"
                    |    },
                    |    "next": {
                    |      "href": "v1/accounts/Alias1/transactions?page=3"
                    |    }
                    |  }
                    |}""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AISP") :: Nil
     )

     lazy val accountsTransactionsGet : OBPEndpoint = {
       case "accounts" :: accountresourceid:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
            
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
            
            bankId = BankId(defaultBankId)
            
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, AccountId(accountresourceid), callContext)
            
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 
            
            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(bank, Full(u), view, BankIdAccountId(bankId,bankAccount.accountId), callContext, params)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            } yield {
              (createTransactionsJson(transactions, transactionRequests), callContext)
            }
         }
       }
            
     resourceDocs += ResourceDoc(
       consentsPut, 
       apiVersion, 
       nameOf(consentsPut),
       "PUT", 
       "/consents", 
       "Forwarding the PSU consent (AISP)",
       s"""${mockedDataText(true)}
            ### Description

In the mixed detailed consent on accounts

* the AISP captures the consent of the PSU
* then it forwards this consent to the ASPSP

This consent replaces any prior consent that was previously sent by the AISP.

### Prerequisites

* The TPP has been registered by the Registration Authority for the AISP role.
* The TPP and the PSU have a contract that has been enrolled by the ASPSP
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its OAUTH2 "Authorization Code" or "Resource Owner Password" access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.
* The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.

### Business Flow

The PSU specifies to the AISP which of his/her accounts will be accessible and which functionalities should be available. The AISP forwards these settings to the ASPSP. The ASPSP answers by HTTP201 return code.

            """,
       json.parse("""{
  "balances" : [ {
    "iban" : "YY64COJH41059545330222956960771321"
  } ],
  "trustedBeneficiaries" : true,
  "psuIdentity" : true
}"""),
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val consentsPut : OBPEndpoint = {
       case "consents" :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       endUserIdentityGet, 
       apiVersion, 
       nameOf(endUserIdentityGet),
       "GET", 
       "/end-user-identity", 
       "Retrieval of the identity of the end-user (AISP)",
       s"""${mockedDataText(true)}
            ### Description

This call returns the identity of the PSU (end-user).

### Prerequisites

* The TPP has been registered by the Registration Authority for the AISP role.
* The TPP and the PSU have a contract that has been enrolled by the ASPSP
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its OAUTH2 "Authorization Code" or "Resource Owner Password" access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.
* The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.

### Business Flow

The AISP asks for the identity of the PSU. The ASPSP answers with the identity, i.e. first and last names of the end-user.

            """,
       emptyObjectJson,
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val endUserIdentityGet : OBPEndpoint = {
       case "end-user-identity" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       trustedBeneficiariesGet, 
       apiVersion, 
       nameOf(trustedBeneficiariesGet),
       "GET", 
       "/trusted-beneficiaries", 
       "Retrieval of the trusted beneficiaries list (AISP)",
       s"""${mockedDataText(true)}
            ### Description

This call returns all trusted beneficiaries that have been set by the PSU. Those beneficiaries can benefit from an SCA exemption during payment initiation. The result may be subject to pagination (i.e. retrieving a partial result in case of having too many results) through a set of pages by the ASPSP. Thereafter, the AISP may ask for the first, next, previous or last page of results.

### Prerequisites

* The TPP has been registered by the Registration Authority for the AISP role.
* The TPP and the PSU have a contract that has been enrolled by the ASPSP
* The TPP and the ASPSP have successfully processed a mutual check and authentication
* The TPP has presented its OAUTH2 "Authorization Code" or "Resource Owner Password" access token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.
* The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.

### Business Flow

The AISP asks for the trusted beneficiaries list. The ASPSP answers with a list of beneficiary details structure.

            """,
       emptyObjectJson,
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AISP") :: apiTagMockedData :: Nil
     )

     lazy val trustedBeneficiariesGet : OBPEndpoint = {
       case "trusted-beneficiaries" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

}



