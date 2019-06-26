package code.api.berlin.group.v1_3_1

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
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
import com.github.dwickern.macros.NameOf.nameOf
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.api.berlin.group.v1_3_1.JSONFactory_BERLIN_GROUP_1_3_3
import code.api.util.ApiTag

object APIMethods_AccountInformationServiceAISApi extends RestHelper {
    val apiVersion =  JSONFactory_BERLIN_GROUP_1_3_3.apiVersion
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
            """,
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : [ "data", "data" ]
  },
  "consentId" : { },
  "scaMethods" : "",
  "_links" : {
    "scaStatus" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithEncryptedPsuAuthentication" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "scaRedirect" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithAuthenticationMethodSelection" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithPsuAuthentication" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "scaOAuth" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "self" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithPsuIdentification" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisation" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithTransactionAuthorisation" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "status" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
            The TPP can delete an account information consent object if needed.

            """,
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "consents" :: consentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse(""""""), callContext)
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
       s"""${mockedDataText(true)}
            Read the identifiers of the available payment account together with booking balance information, depending on the consent granted. 
            It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system. 
            The addressed list of accounts depends then on the PSU ID and the stored consent addressed by consentId, respectively the OAuth2 access token. 
            Returns all identifiers of the accounts, to which an account access has been granted to through the /consents endpoint by the PSU. 
            In addition, relevant information about the accounts and hyperlinks to corresponding account information resources are provided if a related consent has been already granted. 
            
            Remark: Note that the /consents endpoint optionally offers to grant an access on all available payment accounts of a PSU. 
            In this case, this endpoint will deliver the information about all available payment accounts of the PSU at this ASPSP.

            """,
       json.parse(""""""),
       json.parse("""{
  "accounts" : [ {
    "cashAccountType" : { },
    "product" : "product",
    "resourceId" : "resourceId",
    "bban" : "BARC12345612345678",
    "_links" : {
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getAccountList : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "accounts" : [ {
    "cashAccountType" : { },
    "product" : "product",
    "resourceId" : "resourceId",
    "bban" : "BARC12345612345678",
    "_links" : {
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
}"""), callContext)
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
       s"""${mockedDataText(true)}
            Reads account data from a given account addressed by "account-id". 
            
            **Remark:** This account-id can be a tokenised identification due to data protection reason since the path 
            information might be logged on intermediary servers within the ASPSP sphere. This account-id then can be 
            retrieved by the "GET Account List" call. The account-id is constant at least throughout the lifecycle of a 
            given consent.

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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "accounts" :: account_id:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "balances" : "",
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
       getCardAccount, 
       apiVersion, 
       nameOf(getCardAccount),
       "GET", 
       "/card-accounts", 
       "Reads a list of card accounts",
       s"""${mockedDataText(true)}
            Reads a list of card accounts with additional information, e.g. balance information. It is assumed that a 
            consent of the PSU to this access is already given and stored on the ASPSP system. The addressed list of card accounts 
            depends then on the PSU ID and the stored consent addressed by consentId, respectively the OAuth2 access token.

            """,
       json.parse(""""""),
       json.parse("""{
  "cardAccounts" : [ {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getCardAccount : OBPEndpoint = {
       case "card-accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "cardAccounts" : [ {
    "balances" : "",
    "product" : "product",
    "resourceId" : "resourceId",
    "maskedPan" : "123456xxxxxx1234",
    "_links" : {
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
      "balances" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "transactions" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
            Reads balance data from a given card account addressed by "account-id". Remark: This account-id can be a tokenised 
            identification due to data protection reason since the path information might be logged on intermediary servers within 
            the ASPSP sphere. This account-id then can be retrieved by the "GET Card Account List" call

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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getCardAccountBalances : OBPEndpoint = {
       case "card-accounts" :: account_id:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
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
       s"""${mockedDataText(true)}
            Reads account data from a given card account addressed by "account-id".

            """,
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "_links" : {
    "download" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "cardTransactions" : {
    "booked" : "",
    "_links" : {
      "next" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "last" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "previous" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "cardAccount" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "first" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getCardAccountTransactionList : OBPEndpoint = {
       case "card-accounts" :: account_id:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "balances" : "",
  "_links" : {
    "download" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "cardTransactions" : {
    "booked" : "",
    "_links" : {
      "next" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "last" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "previous" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "cardAccount" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "first" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
       nameOf(getConsentAuthorisation),
       "GET", 
       "/consents/CONSENTID/authorisations", 
       "Get Consent Authorisation Sub-Resources Request",
       s"""${mockedDataText(true)}
            Return a list of all authorisation subresources IDs which have been created. This function returns an 
            array of hyperlinks to all generated authorisation sub-resources.

            """,
       json.parse(""""""),
       json.parse("""{
  "authorisationIds" : ""
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
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
            Returns the content of an account information consent object. This is returning the data for the TPP especially 
            in cases, where the consent was directly managed between ASPSP and PSU e.g. in a re-direct SCA Approach.

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
  "_links" : {
    "card-account" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "account" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "consentStatus" : { },
  "validUntil" : "2020-12-31",
  "lastActionDate" : "2018-07-01",
  "recurringIndicator" : false,
  "frequencyPerDay" : 4
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
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
  "_links" : {
    "card-account" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "account" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
       ApiTag("Account Information Service (AIS)") ::ApiTag("Common Services") :: apiTagMockedData :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
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
            Read the status of an account information consent resource.

            """,
       json.parse(""""""),
       json.parse("""{
  "consentStatus" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "consents" :: consentid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
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
  "additionalInformation" : "additionalInformation",
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
    "transactionDetails" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "remittanceInformationStructured" : "remittanceInformationStructured",
  "ultimateCreditor" : "Ultimate Creditor",
  "bankTransactionCode" : "PMNT-RCDT-ESCT",
  "debtorName" : "Debtor Name",
  "valueDate" : "2000-01-23",
  "endToEndId" : "endToEndId",
  "transactionId" : "transactionId",
  "currencyExchange" : "",
  "ultimateDebtor" : "Ultimate Debtor",
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
  "remittanceInformationUnstructured" : "Ref Number Merchant",
  "checkId" : "checkId",
  "creditorId" : "creditorId",
  "entryReference" : "entryReference"
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionDetails : OBPEndpoint = {
       case "accounts" :: account_id:: "transactions" :: transactionid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "additionalInformation" : "additionalInformation",
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
    "transactionDetails" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "remittanceInformationStructured" : "remittanceInformationStructured",
  "ultimateCreditor" : "Ultimate Creditor",
  "bankTransactionCode" : "PMNT-RCDT-ESCT",
  "debtorName" : "Debtor Name",
  "valueDate" : "2000-01-23",
  "endToEndId" : "endToEndId",
  "transactionId" : "transactionId",
  "currencyExchange" : "",
  "ultimateDebtor" : "Ultimate Debtor",
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
  "remittanceInformationUnstructured" : "Ref Number Merchant",
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
       s"""${mockedDataText(true)}
            Read transaction reports or transaction lists of a given account ddressed by "account-id", 
            depending on the steering parameter "bookingStatus" together with balances. 
            For a given account, additional parameters are e.g. the attributes "dateFrom" and "dateTo". 
            The ASPSP might add balance information, if transaction lists without balances are not supported.

            """,
       json.parse(""""""),
       json.parse("""{
  "balances" : "",
  "_links" : {
    "download" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "transactions" : {
    "booked" : "",
    "_links" : {
      "next" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "last" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "previous" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "account" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "first" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "accounts" :: account_id:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "balances" : "",
  "_links" : {
    "download" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "transactions" : {
    "booked" : "",
    "_links" : {
      "next" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "last" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "previous" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "account" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      },
      "first" : {
        "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
      }
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
       nameOf(readAccountDetails),
       "GET", 
       "/accounts/ACCOUNT_ID", 
       "Read Account Details",
       s"""${mockedDataText(true)}
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
    "balances" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "transactions" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val readAccountDetails : OBPEndpoint = {
       case "accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "cashAccountType" : { },
  "product" : "product",
  "resourceId" : "resourceId",
  "bban" : "BARC12345612345678",
  "_links" : {
    "balances" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "transactions" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
    "balances" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "transactions" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: Nil
     )

     lazy val readCardAccount : OBPEndpoint = {
       case "card-accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "balances" : "",
  "product" : "product",
  "resourceId" : "resourceId",
  "maskedPan" : "123456xxxxxx1234",
  "_links" : {
    "balances" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "transactions" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
  "authorisationId" : "123auth456",
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : [ "data", "data" ]
  },
  "scaMethods" : "",
  "scaStatus" : "psuAuthenticated",
  "_links" : {
    "scaStatus" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithEncryptedPsuAuthentication" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "scaRedirect" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "selectAuthenticationMethod" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithPsuAuthentication" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "authoriseTransaction" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "scaOAuth" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "updatePsuIdentification" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
  },
  "chosenScaMethod" : "",
  "psuMessage" : { }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") ::ApiTag("Common Services") :: apiTagMockedData :: Nil
     )

     lazy val startConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "authorisationId" : "123auth456",
  "challengeData" : {
    "otpMaxLength" : 0,
    "additionalInformation" : "additionalInformation",
    "image" : "image",
    "imageLink" : "http://example.com/aeiou",
    "otpFormat" : "characters",
    "data" : [ "data", "data" ]
  },
  "scaMethods" : "",
  "scaStatus" : "psuAuthenticated",
  "_links" : {
    "scaStatus" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithEncryptedPsuAuthentication" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "scaRedirect" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "selectAuthenticationMethod" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "startAuthorisationWithPsuAuthentication" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "authoriseTransaction" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "scaOAuth" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    },
    "updatePsuIdentification" : {
      "href" : "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
    }
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
       json.parse(""""""),
       json.parse(""""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Information Service (AIS)") ::ApiTag("Common Services") :: apiTagMockedData :: Nil
     )

     lazy val updateConsentsPsuData : OBPEndpoint = {
       case "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse(""""""""), callContext)
           }
         }
       }

}



