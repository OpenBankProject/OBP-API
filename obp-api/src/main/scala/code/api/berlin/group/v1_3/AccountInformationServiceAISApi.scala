package code.api.builder.AccountInformationServiceAISApi

import code.api.APIFailureNewStyle
import code.api.Constant.{SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID}
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{PostConsentResponseJson, _}
import code.api.berlin.group.v1_3.model._
import code.api.berlin.group.v1_3.{BgSpecValidation, JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass}
import code.api.util.APIUtil.{passesPsd2Aisp, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.NewStyle.function.extractQueryParams
import code.api.util._
import code.api.util.newstyle.ViewNewStyle
import code.consent.{ConsentStatus, Consents}
import code.context.{ConsentAuthContextProvider, UserAuthContextProvider}
import code.model
import code.model._
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{ChallengeType, StrongCustomerAuthenticationStatus, SuppliedAnswerType}
import net.liftweb
import net.liftweb.common.{Empty, Full}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object APIMethods_AccountInformationServiceAISApi extends RestHelper {
    val apiVersion = ConstantsBG.berlinGroupVersion1
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createConsent ::
      deleteConsent ::
      getAccountList ::
      getBalances ::
      getCardAccounts ::
      getCardAccountBalances ::
      getCardAccountTransactionList ::
      getConsentAuthorisation ::
      getConsentInformation ::
      getConsentScaStatus ::
      getConsentStatus ::
      getTransactionDetails ::
      getTransactionList ::
      getAccountDetails ::
      readCardAccount ::
      startConsentAuthorisationTransactionAuthorisation ::
      startConsentAuthorisationUpdatePsuAuthentication ::
      startConsentAuthorisationSelectPsuAuthenticationMethod ::
      updateConsentsPsuDataTransactionAuthorisation ::
      updateConsentsPsuDataUpdatePsuAuthentication ::
      updateConsentsPsuDataUpdateAuthorisationConfirmation ::
      updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod ::
      Nil
    lazy val newStyleEndpoints: List[(String, String)] = resourceDocs.map {
      rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
    }.toList


    private def checkAccountAccess(viewId: ViewId, u: User, account: BankAccount, callContext: Option[CallContext]) = {
      Future {
        Helper.booleanToBox(u.hasViewAccess(BankIdAccountId(account.bankId, account.accountId), viewId, callContext))
      } map {
        unboxFullOrFail(_, callContext, s"$NoViewReadAccountsBerlinGroup ${viewId.value} userId : ${u.userId}. account : ${account.accountId}", 403)
      }
    }

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

frequencyPerDay:
       This field indicates the requested maximum frequency for an access without PSU involvement per day.
       For a one-off access, this attribute is set to "1".
       The frequency needs to be greater equal to one.
       If not otherwise agreed bilaterally between TPP and ASPSP, the frequency is less equal to 4.
recurringIndicator:
       "true", if the consent is for recurring access to the account data.
       "false", if the consent is for one access to the account data.
""",
       PostConsentJson(
         access = ConsentAccessJson(
           accounts = Option(List( ConsentAccessAccountsJson(
             iban = Some(ExampleValue.ibanExample.value),
             bban = None,
             pan = None,
             maskedPan = None,
             msisdn = None,
             currency = None,
           ))),
           balances = None,
           transactions = None,
           availableAccounts = None,
           allPsd2 = None
         ),
         recurringIndicator = true,
         validUntil = "2020-12-31",
         frequencyPerDay = 4,
         combinedServiceIndicator = Some(false)
       ),
       PostConsentResponseJson(
         consentId = "1234-wertiq-983",
         consentStatus = "received",
         _links = ConsentLinksV13(Some(Href("/v1.3/consents/1234-wertiq-983/authorisations")))
       ),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "consents" :: Nil JsonPost json -> _  =>  {
         cc =>
           for {
             (_, callContext) <- applicationAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             createdByUser: Option[User] <- callContext.map(_.user).getOrElse(Empty) match {
               case Full(user) => Future(Some(user))
               case _ => Future(None)
             }
             failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentJson "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               json.extract[PostConsentJson]
             }

             _ <- if (consentJson.access.availableAccounts.isDefined) {
               for {
                 _ <- Helper.booleanToFuture(failMsg = BerlinGroupConsentAccessAvailableAccounts, cc = callContext) {
                   consentJson.access.availableAccounts.contains("allAccounts")
                 }
                 _ <- Helper.booleanToFuture(failMsg = BerlinGroupConsentAccessRecurringIndicator, cc = callContext) {
                   !consentJson.recurringIndicator
                 }
                 _ <- Helper.booleanToFuture(failMsg = BerlinGroupConsentAccessFrequencyPerDay, cc = callContext) {
                   consentJson.frequencyPerDay == 1
                 }
               } yield Full(())
             } else {
               Helper.booleanToFuture(
                 failMsg = BerlinGroupConsentAccessIsEmpty, cc = callContext) {
                 consentJson.access.accounts.isDefined ||
                   consentJson.access.balances.isDefined ||
                   consentJson.access.transactions.isDefined
               }
             }

             upperLimit = APIUtil.getPropsAsIntValue("berlin_group_frequency_per_day_upper_limit", 4)
             _ <- Helper.booleanToFuture(failMsg = FrequencyPerDayError, cc=callContext) {
               consentJson.frequencyPerDay > 0 && consentJson.frequencyPerDay <= upperLimit
             }

             _ <- Helper.booleanToFuture(failMsg = FrequencyPerDayMustBeOneError, cc=callContext) {
               consentJson.recurringIndicator ||
                 !consentJson.recurringIndicator && consentJson.frequencyPerDay == 1
             }

             failMsg = BgSpecValidation.getErrorMessage(consentJson.validUntil)
             validUntil = BgSpecValidation.getDate(consentJson.validUntil)
             _ <- Helper.booleanToFuture(failMsg, 400, callContext) {
               failMsg.isEmpty
             }

             _ <- NewStyle.function.getBankAccountsByIban(consentJson.access.accounts.getOrElse(Nil).map(_.iban.getOrElse("")), callContext)

             createdConsent <- Future(Consents.consentProvider.vend.createBerlinGroupConsent(
               createdByUser,
               callContext.flatMap(_.consumer),
               recurringIndicator = consentJson.recurringIndicator,
               validUntil = validUntil,
               frequencyPerDay = consentJson.frequencyPerDay,
               combinedServiceIndicator = consentJson.combinedServiceIndicator.getOrElse(false),
               apiStandard = Some(apiVersion.apiStandard),
               apiVersion = Some(apiVersion.apiShortVersion)
             )) map {
               i => connectorEmptyResponse(i, callContext)
             }
             consentJWT <-
             Consent.createBerlinGroupConsentJWT(
               createdByUser,
               consentJson,
               createdConsent.secret,
               createdConsent.consentId,
               callContext.flatMap(_.consumer).map(_.consumerId.get),
               Some(validUntil),
               callContext
             ) map {
               i => connectorEmptyResponse(i, callContext)
             }
             _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)) map {
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
       EmptyBody,
       EmptyBody,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")   :: apiTagBerlinGroupM :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "consents" :: consentId :: Nil JsonDelete _ => {
         cc =>
           for {
             (_, callContext) <- applicationAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound, 403)
             }
             consumerIdFromConsent = consent.mConsumerId.get
             consumerIdFromCurrentCall = callContext.map(_.consumer.map(_.consumerId.get).getOrElse("None")).getOrElse("None")
             _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, failCode = 403, cc = cc.callContext) {
               consumerIdFromConsent == consumerIdFromCurrentCall
             }
             _ <- Future(Consents.consentProvider.vend.revokeBerlinGroupConsent(consentId)) map {
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
       EmptyBody,
       json.parse("""{
                    |  "accounts": [
                    |    {
                    |      "resourceId": "3dc3d5b3-7023-4848-9853-f5400a64e80f",
                    |      "iban": "DE2310010010123456789",
                    |      "currency": "EUR",
                    |      "product": "Girokonto",
                    |      "cashAccountType": "CACC",
                    |      "name": "Main Account",
                    |      "_links": {
                    |        "balances": {
                    |          "href": "/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/balances"
                    |        }
                    |      }
                    |    },
                    |    {
                    |      "resourceId": "3dc3d5b3-7023-4848-9853-f5400a64e81g",
                    |      "iban": "DE2310010010123456788",
                    |      "currency": "USD",
                    |      "product": "Fremdwährungskonto",
                    |      "cashAccountType": "CACC",
                    |      "name": "US Dollar Account",
                    |      "_links": {
                    |        "balances": {
                    |          "href": "/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e81g/balances"
                    |        }
                    |      }
                    |    }
                    |  ]
                    |}""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getAccountList : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            withBalanceParam <- NewStyle.function.tryons(s"$InvalidUrlParameters withBalance parameter can only take two values: TRUE or FALSE!", 400, callContext) {

              val withBalance = APIUtil.getHttpRequestUrlParam(cc.url, "withBalance")
              
              if(withBalance.isEmpty)Some(false) else Some(withBalance.toBoolean)
            }
            _ <- passesPsd2Aisp(callContext)
            (availablePrivateAccounts, callContext) <- NewStyle.function.getAccountListOfBerlinGroup(u, callContext)
            (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
            (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
            (accounts, callContext) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
            bankAccountsFiltered = accounts.filter(bankAccount =>
              bankAccount.attributes.toList.flatten.find(attribute =>
                attribute.name.equals("CashAccountTypeCode")&&
                attribute.`type`.equals("STRING")&&
                attribute.value.equalsIgnoreCase("card")
              ).isEmpty)

            (balances, callContext) <-  code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountsBalances(
              bankAccountsFiltered.map(_.accountId),
              callContext
            )
             
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountListJson(
              bankAccountsFiltered,
              canReadBalancesAccounts,
              canReadTransactionsAccounts,
              u,
              withBalanceParam,
              balances
            ), callContext)
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
       EmptyBody,
       json.parse("""{
  "account":{
    "iban":"DE91 1000 0000 0123 4567 89"
  },
  "balances":[{
    "balanceAmount":{
      "currency":"EUR",
      "amount":"50.89"
    },
    "balanceType":"AC",
    "lastChangeDateTime":"yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "lastCommittedTransaction":"String",
    "referenceDate":"2018-03-08"
  }]
}
"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(accountId, callContext)
            _ <- checkAccountAccess(ViewId(SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID), u, account, callContext)
            (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
              accountId,
              callContext
            )
             
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountBalanceJSON(account, accountBalances), HttpCode.`200`(callContext))
           }
         }
       }

     resourceDocs += ResourceDoc(
       getCardAccounts,
       apiVersion,
       nameOf(getCardAccounts),
       "GET",
       "/card-accounts",
       "Reads a list of card accounts",
       s"""${mockedDataText(false)}
Reads a list of card accounts with additional information, e.g. balance information.
It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
The addressed list of card accounts depends then on the PSU ID and the stored consent addressed by consentId,
respectively the OAuth2 access token.
""",
       EmptyBody,
       json.parse("""{
  "cardAccounts": [
    {
      "resourceId": "3d9a81b3-a47d-4130-8765-a9c0ff861b99",
      "maskedPan": "525412******3241",
      "currency": "EUR",
      "name": "Main",
      "product": "Basic Credit",
      "status": "enabled",
      "creditLimit": {
        "currency": "EUR",
        "amount": 15000
      },
      "_links": {
        "balances": {
          "href": "/v1/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99/balances"
        }
      }
    }
  ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: Nil
     )

     lazy val getCardAccounts : OBPEndpoint = {
       case "card-accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
             //The card contains the account object, it mean the card account.
             (_, callContext) <- NewStyle.function.getPhysicalCardsForUser(u, callContext)
             (accounts, callContext) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
             (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
             (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
             //also see `getAccountList` endpoint
             bankAccountsFiltered = accounts.filter(bankAccount =>
               bankAccount.attributes.toList.flatten.find(attribute=>
                   attribute.name.equals("CashAccountTypeCode")&&
                   attribute.`type`.equals("STRING")&&
                 attribute.value.equalsIgnoreCase("card")
               ).isDefined)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardAccountListJson(
               bankAccountsFiltered,
               canReadBalancesAccounts,
               canReadTransactionsAccounts,
               u
             ),
               callContext
             )
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
       EmptyBody,
       json.parse("""{
  "cardAccount":{
    "iban":"DE91 1000 0000 0123 4567 89"
  },
  "balances":[{
    "balanceAmount":{
      "currency":"EUR",
      "amount":"50.89"
    },
    "balanceType":"AC",
    "lastChangeDateTime":"yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "lastCommittedTransaction":"String",
    "referenceDate":"2018-03-08"
  }]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: Nil
     )

     lazy val getCardAccountBalances : OBPEndpoint = {
       case "card-accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(accountId, callContext)
             _ <- checkAccountAccess(ViewId(SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID), u, account, callContext)
             (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
               accountId,
               callContext
             )
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardAccountBalanceJSON(account, accountBalances), HttpCode.`200`(callContext))
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
       EmptyBody,
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
                        "_links": {
                          "cardAccount": {
                            "href": "/v1.3/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99"
                          }
                        }
                      }
                    }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM ::Nil
     )

     lazy val getCardAccountTransactionList : OBPEndpoint = {
       case "card-accounts" :: AccountId(accountId):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             (bankAccount: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(accountId, callContext)
             (bank, callContext) <- NewStyle.function.getBank(bankAccount.bankId, callContext)
             viewId = ViewId(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
             bankIdAccountId = BankIdAccountId(bankAccount.bankId, bankAccount.accountId)
             view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, bankIdAccountId, Full(u), callContext)
             params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }
//             (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount, callContext)} map {
//               x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
//             } map { unboxFull(_) }
             (transactions, callContext) <- model.toBankAccountExtended(bankAccount).getModeratedTransactionsFuture(bank, Full(u), view, callContext, params) map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardTransactionsJson(bankAccount, transactions), callContext)
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
       EmptyBody,
       json.parse("""{
  "authorisationIds" : "faa3657e-13f0-4feb-a6c3-34bf21a9ae8e"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             (challenges, callContext) <-  NewStyle.function.getChallengesByConsentId(consentId, callContext)
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.AuthorisationJsonV13(challenges.map(_.challengeId)), HttpCode.`200`(callContext))
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
       EmptyBody,
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
       List(UserNotLoggedIn, ConsentNotFound, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "consents" :: consentId :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- applicationAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, s"$ConsentNotFound ($consentId)")
             }
             consumerIdFromConsent = consent.mConsumerId.get
             consumerIdFromCurrentCall = callContext.map(_.consumer.map(_.consumerId.get).getOrElse("None")).getOrElse("None")
             _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, failCode = 403, cc = cc.callContext) {
               consumerIdFromConsent == consumerIdFromCurrentCall
             }
           } yield {
             (createGetConsentResponseJson(consent), HttpCode.`200`(callContext))
           }
         }
       }


     resourceDocs += ResourceDoc(
       getConsentScaStatus,
       apiVersion,
       nameOf(getConsentScaStatus),
       "GET",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Read the SCA status of the consent authorisation",
       s"""${mockedDataText(false)}
This method returns the SCA status of a consent initiation's authorisation sub-resource.
""",
       EmptyBody,
       json.parse("""{
  "scaStatus" : "started"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: authorisationId :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, s"$ConsentNotFound ($consentId)", 403)
             }
             (challenges, callContext) <-  NewStyle.function.getChallengesByConsentId(consentId, callContext)
           } yield {
             val challengeStatus = challenges.filter(_.challengeId == authorisationId)
               .flatMap(_.scaStatus).headOption.map(_.toString).getOrElse("None")
             (JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challengeStatus), HttpCode.`200`(callContext))
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
       EmptyBody,
       json.parse("""{
                      "consentStatus": "received"
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")   :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "consents" :: consentId:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- applicationAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound, 403)
             }
           } yield {
             val status = consent.status
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
       s"""${mockedDataText(false)}
Reads transaction details from a given transaction addressed by "transactionId" on a given account addressed
by "account-id". This call is only available on transactions as reported in a JSON format.

**Remark:** Please note that the PATH might be already given in detail by the corresponding entry of the response
of the "Read Transaction List" call within the _links subfield.

            """,
       EmptyBody,
       json.parse("""{
  "description": "Example for transaction details",
  "value": {
    "transactionsDetails": {
      "transactionId": "1234567",
      "creditorName": "John Miles",
      "creditorAccount": {
        "iban": "DE67100100101306118605"
      },
      "mandateId": "Mandate-2018-04-20-1234-Identification of Mandates, e.g. a SEPA Mandate ID.",
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
       ApiTag("Account Information Service (AIS)")  :: Nil
     )

     lazy val getTransactionDetails : OBPEndpoint = {
       case "accounts" :: accountId :: "transactions" :: transactionId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(user), callContext) <- authenticatedAccess(cc)
             (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
             viewId = ViewId(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
             bankIdAccountId = BankIdAccountId(account.bankId, account.accountId)
             view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, bankIdAccountId, Full(user), callContext)
             (moderatedTransaction, callContext) <- account.moderatedTransactionFuture(TransactionId(transactionId), view, Some(user), callContext) map {
               unboxFullOrFail(_, callContext, GetTransactionsException)
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createTransactionJson(account, moderatedTransaction), callContext)
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
Read transaction reports or transaction lists of a given account addressed by "account-id",
depending on the steering parameter "bookingStatus" together with balances.
For a given account, additional parameters are e.g. the attributes "dateFrom" and "dateTo".
The ASPSP might add balance information, if transaction lists without balances are not supported. """,
       EmptyBody,
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
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            (bankAccount: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(accountId, callContext)
            (bank, callContext) <- NewStyle.function.getBank(bankAccount.bankId, callContext)
            viewId = ViewId(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
            bankIdAccountId = BankIdAccountId(bankAccount.bankId, bankAccount.accountId)
            view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, bankIdAccountId, Full(u), callContext)
            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            bookingStatus = APIUtil.getHttpRequestUrlParam(cc.url, "bookingStatus")
            _ <- Helper.booleanToFuture(s"$InvalidUrlParameters bookingStatus parameter must take two one of those values : booked, pending or both!", 400, callContext) {
              bookingStatus match {
                case "booked" | "pending" | "both" => true
                case _ => false
              }
            }
            (transactions, callContext) <-bankAccount.getModeratedTransactionsFuture(bank, Full(u), view, callContext, params) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            } yield {
              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(bankAccount, transactions, bookingStatus), callContext)
            }
         }
       }

     resourceDocs += ResourceDoc(
       getAccountDetails,
       apiVersion,
       nameOf(getAccountDetails),
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
       EmptyBody,
       json.parse("""{
  "account": {
    "resourceId": "3dc3d5b3-7023-4848-9853-f5400a64e80f",
    "iban": "FR7612345987650123456789014",
    "currency": "EUR",
    "product": "Girokonto",
    "cashAccountType": "CACC",
    "name": "Main Account",
    "_links": {
      "balances": {
        "href": "/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/balances"
      },
      "transactions": {
        "href": "/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/transactions"
      }
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val getAccountDetails : OBPEndpoint = {
       case "accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             withBalanceParam <- NewStyle.function.tryons(s"$InvalidUrlParameters withBalance parameter can only take two values: TRUE or FALSE!", 400, callContext) {
               val withBalance = APIUtil.getHttpRequestUrlParam(cc.url, "withBalance")
               if (withBalance.isEmpty) Some(false) else Some(withBalance.toBoolean)
             }
             _ <- passesPsd2Aisp(callContext)
             (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
             (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
             (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
             _ <- checkAccountAccess(ViewId(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID), u, account, callContext)
             (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
               AccountId(accountId),
               callContext
             )
           } yield {
             (
               JSONFactory_BERLIN_GROUP_1_3.createAccountDetailsJson(
                 account,
                 canReadBalancesAccounts,
                 canReadTransactionsAccounts,
                 withBalanceParam,
                 accountBalances,
                 u
               ),
               callContext
             )
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
       EmptyBody,
       json.parse("""{
                    |  "cardAccount": {
                    |    "resourceId": "3d9a81b3-a47d-4130-8765-a9c0ff861b99",
                    |    "maskedPan": "525412******3241",
                    |    "currency": "EUR",
                    |    "name": "Main",
                    |    "product": "Basic Credit",
                    |    "status": "enabled",
                    |    "creditLimit": {
                    |      "currency": "EUR",
                    |      "amount": "15000"
                    |    },
                    |    "_links": {
                    |      "balances": {
                    |        "href": "/v1/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99/balances"
                    |      },
                    |      "transactions": {
                    |        "href": "/v1/card-accounts/3d9a81b3-a47d-4130-8765-a9c0ff861b99/transactions"
                    |      }
                    |    }
                    |  }
                    |}""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: Nil
     )

     lazy val readCardAccount : OBPEndpoint = {
       case "card-accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             (account: BankAccount, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
             (canReadBalancesAccounts, callContext) <- NewStyle.function.getAccountCanReadBalancesOfBerlinGroup(u, callContext)
             (canReadTransactionsAccounts, callContext) <- NewStyle.function.getAccountCanReadTransactionsOfBerlinGroup(u, callContext)
             _ <- checkAccountAccess(ViewId(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID), u, account, callContext)
             withBalanceParam <- NewStyle.function.tryons(s"$InvalidUrlParameters withBalance parameter can only take two values: TRUE or FALSE!", 400, callContext) {
               val withBalance = APIUtil.getHttpRequestUrlParam(cc.url, "withBalance")
               if (withBalance.isEmpty) Some(false) else Some(withBalance.toBoolean)
             }
             (accountBalances, callContext) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(
               AccountId(accountId),
               callContext
             )
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createCardAccountDetailsJson(
               account,
               canReadBalancesAccounts,
               canReadTransactionsAccounts,
               withBalanceParam,
               accountBalances,
               u
             ), callContext)
           }
       }
     }

     def generalStartConsentAuthorisationSummary(isMockedData:Boolean) =
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

"""
  
     resourceDocs += ResourceDoc(
       startConsentAuthorisationTransactionAuthorisation,
       apiVersion,
       nameOf(startConsentAuthorisationTransactionAuthorisation),
       "POST",
       "/consents/CONSENTID/authorisations",
       "Start the authorisation process for a consent(transactionAuthorisation)",
       generalStartConsentAuthorisationSummary(false),
       json.parse("""{"scaAuthenticationData":""}"""),
       json.parse("""{
                       "scaStatus": "received",
                       "psuMessage": "Please use your BankApp for transaction Authorisation.",
                       "authorisationId": "123auth456.",
                       "_links":
                         {
                           "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                         }
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val startConsentAuthorisationTransactionAuthorisation : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: Nil JsonPost json -> _ if checkTransactionAuthorisation(json)=> {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound, 403)
              }
             (challenges, callContext) <- NewStyle.function.createChallengesC2(
               List(u.userId),
               ChallengeType.BERLIN_GROUP_CONSENT_CHALLENGE,
               None,
               getSuggestedDefaultScaMethod(),
               Some(StrongCustomerAuthenticationStatus.received),
               Some(consentId),
               None,
               callContext
             )
             //NOTE: in OBP it support multiple challenges, but in Berlin Group it has only one challenge. The following guard is to make sure it return the 1st challenge properly.
             challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge,400, callContext) {
               challenges.head
             }
           } yield {
             (createStartConsentAuthorisationJson(consent, challenge), HttpCode.`201`(callContext))
           }
         }
       }
  
     resourceDocs += ResourceDoc(
       startConsentAuthorisationUpdatePsuAuthentication,
       apiVersion,
       nameOf(startConsentAuthorisationUpdatePsuAuthentication),
       "POST",
       "/consents/CONSENTID/authorisations",
       "Start the authorisation process for a consent(updatePsuAuthentication)",
       generalStartConsentAuthorisationSummary(true),
       json.parse("""{
         "psuData": {
           "password": "start12"
         }
       }"""),
       json.parse("""{
                       "scaStatus": "received",
                       "psuMessage": "Please use your BankApp for transaction Authorisation.",
                       "authorisationId": "123auth456.",
                       "_links":
                         {
                           "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                         }
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val startConsentAuthorisationUpdatePsuAuthentication : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: Nil JsonPost json -> _ if checkUpdatePsuAuthentication(json)=> {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
           } yield {
             (liftweb.json.parse(
               """{
                   "scaStatus": "received",
                   "psuMessage": "Please use your BankApp for transaction Authorisation.",
                   "authorisationId": "123auth456.",
                   "_links":
                     {
                       "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                     }
               }"""),HttpCode.`201`(callContext))
           }
         }
       }
  
     resourceDocs += ResourceDoc(
       startConsentAuthorisationSelectPsuAuthenticationMethod,
       apiVersion,
       nameOf(startConsentAuthorisationSelectPsuAuthenticationMethod),
       "POST",
       "/consents/CONSENTID/authorisations",
       "Start the authorisation process for a consent(selectPsuAuthenticationMethod)",
       generalStartConsentAuthorisationSummary(true),
       json.parse("""{"authenticationMethodId":""}"""),
       json.parse("""{
                       "scaStatus": "received",
                       "psuMessage": "Please use your BankApp for transaction Authorisation.",
                       "authorisationId": "123auth456.",
                       "_links":
                         {
                           "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                         }
                     }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)") :: apiTagBerlinGroupM :: Nil
     )

     lazy val startConsentAuthorisationSelectPsuAuthenticationMethod : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: Nil JsonPost json -> _ if checkSelectPsuAuthenticationMethod(json)=> {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
           } yield {
             (liftweb.json.parse(
               """{
                   "scaStatus": "received",
                   "psuMessage": "Please use your BankApp for transaction Authorisation.",
                   "authorisationId": "123auth456.",
                   "_links":
                     {
                       "scaStatus":  {"href":"/v1.3/consents/qwer3456tzui7890/authorisations/123auth456"}
                     }
               }"""),HttpCode.`201`(callContext))
           }
         }
       }
  
     def generalUpdateConsentsPsuDataSummary(isMockedData: Boolean) =
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

          """
          
     resourceDocs += ResourceDoc(
       updateConsentsPsuDataTransactionAuthorisation,
       apiVersion,
       nameOf(updateConsentsPsuDataTransactionAuthorisation),
       "PUT",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Update PSU Data for consents (transactionAuthorisation)",
       generalUpdateConsentsPsuDataSummary(false),
       json.parse("""{"scaAuthenticationData":"123"}"""),
       ScaStatusResponse(
         scaStatus = "received",
         _links = Some(LinksAll(scaStatus = Some(HrefType(Some(s"/v1.3/consents/1234-wertiq-983/authorisations")))))
       ),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val updateConsentsPsuDataTransactionAuthorisation : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: authorisationId :: Nil JsonPut jsonPut -> _ if checkTransactionAuthorisation(jsonPut) => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound, 403)
             }
             failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionAuthorisation "
             updateJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               jsonPut.extract[TransactionAuthorisation]
             }
             (_, callContext) <- NewStyle.function.getChallenge(authorisationId, callContext)
             (challenge, callContext) <- NewStyle.function.validateChallengeAnswerC4(
               ChallengeType.BERLIN_GROUP_CONSENT_CHALLENGE,
               None,
               Some(consentId),
               authorisationId,
               updateJson.scaAuthenticationData,
               SuppliedAnswerType.PLAIN_TEXT_VALUE,
               callContext
             )
             consent <- challenge.scaStatus match {
               case Some(status) if status == StrongCustomerAuthenticationStatus.finalised => // finalised
                 Future(Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.valid))
               case Some(status) if status == StrongCustomerAuthenticationStatus.failed => // failed
                 Future(Consents.consentProvider.vend.updateConsentStatus(consentId, ConsentStatus.rejected))
               case _ => // all other cases
                 Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
             }
             _ <- NewStyle.function.tryons(ConsentUpdateStatusError, 400, callContext) {
               consent.toList.size == 1
             }
             _ <- Future {
               val authContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(u.userId)
                 .map(_.map(i => BasicUserAuthContext(i.key, i.value)))
               ConsentAuthContextProvider.consentAuthContextProvider.vend.createOrUpdateConsentAuthContexts(consentId, authContexts.getOrElse(Nil))
             } map {
               unboxFullOrFail(_, callContext, ConsentUserAuthContextCannotBeAdded)
             }
             _ <- Future(Consents.consentProvider.vend.updateConsentUser(consentId, u)) map {
               unboxFullOrFail(_, callContext, ConsentUserCannotBeAdded)
             }
           } yield {
             (createPutConsentResponseJson(consent.toList.head), HttpCode.`200`(callContext))
           }
         }
       }
          
     resourceDocs += ResourceDoc(
       updateConsentsPsuDataUpdatePsuAuthentication,
       apiVersion,
       nameOf(updateConsentsPsuDataUpdatePsuAuthentication),
       "PUT",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Update PSU Data for consents (updatePsuAuthentication)",
       generalUpdateConsentsPsuDataSummary(true),
       json.parse("""{"psuData": {"password": "start12"}}""".stripMargin),
       json.parse("""{ 
         |          "scaStatus": "psuAuthenticated",
         |          "_links": {
         |           "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
         |          }
         |        }""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val updateConsentsPsuDataUpdatePsuAuthentication : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: authorisationId :: Nil JsonPut jsonPut -> _ if checkUpdatePsuAuthentication(jsonPut) => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             
           } yield {
             (liftweb.json.parse(
               """{ 
                 | "scaStatus": "psuAuthenticated",
                 | "_links": {
                 |  "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
                 | }
                 |}""".stripMargin), HttpCode.`200`(callContext))
           }
         }
       }
          
     resourceDocs += ResourceDoc(
       updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod,
       apiVersion,
       nameOf(updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod),
       "PUT",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Update PSU Data for consents (selectPsuAuthenticationMethod)",
       generalUpdateConsentsPsuDataSummary(true),
       json.parse("""{
                    |  "authenticationMethodId": "myAuthenticationID"
                    |}""".stripMargin),
       json.parse("""{ 
                    |          "scaStatus": "scaMethodSelected",
                    |          "chosenScaMethod": {
                    |          "authenticationType": "SMS_OTP",
                    |          "authenticationMethodId": "myAuthenticationID"},
                    |          "challengeData": {
                    |          "otpMaxLength": 6,
                    |          "otpFormat": "integer"},
                    |          "_links": {
                    |             "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
                    |          }
                    |        }""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val updateConsentsPsuDataUpdateSelectPsuAuthenticationMethod : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: authorisationId :: Nil JsonPut jsonPut -> _ if checkSelectPsuAuthenticationMethod(jsonPut) => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             
           } yield {
             (liftweb.json.parse(
               """{
                 |  "scaStatus": "scaMethodSelected",
                 |  "chosenScaMethod": {
                 |    "authenticationType": "SMS_OTP",
                 |    "authenticationMethodId": "myAuthenticationID"},
                 |  "challengeData": {
                 |    "otpMaxLength": 6,
                 |    "otpFormat": "integer"},
                 |  "_links": {
                 |    "authoriseTransaction": {"href": "/psd2/v1/payments/1234-wertiq-983/authorisations/123auth456"}
                 |  }
                 |}""".stripMargin), HttpCode.`200`(callContext))
           }
         }
       }
  
     resourceDocs += ResourceDoc(
       updateConsentsPsuDataUpdateAuthorisationConfirmation,
       apiVersion,
       nameOf(updateConsentsPsuDataUpdateAuthorisationConfirmation),
       "PUT",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Update PSU Data for consents (authorisationConfirmation)",
       generalUpdateConsentsPsuDataSummary(true),
       json.parse("""{"confirmationCode":"confirmationCode"}"""),
       json.parse("""{ 
                    |          "scaStatus": "finalised",
                    |          "_links":{
                    |            "status":  {"href":"/v1/payments/sepa-credit-transfers/qwer3456tzui7890/status"}
                    |          }
                    |        }""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val updateConsentsPsuDataUpdateAuthorisationConfirmation : OBPEndpoint = {
       case "consents" :: consentId :: "authorisations" :: authorisationId :: Nil JsonPut jsonPut -> _ if checkAuthorisationConfirmation(jsonPut) => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
           } yield {
             (json.parse(
               """{ 
                 |  "scaStatus": "finalised",
                 |  "_links":{
                 |    "status":  {"href":"/v1/payments/sepa-credit-transfers/qwer3456tzui7890/status"}
                 |  }
                 |}""".stripMargin),
             HttpCode.`200`(callContext))
           }
         }
       }
  
}



