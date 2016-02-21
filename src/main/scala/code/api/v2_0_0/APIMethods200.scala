package code.api.v2_0_0

import java.text.SimpleDateFormat

import code.api.util.APIUtil
import code.api.util.APIStrings

import code.api.v1_2_1.{JSONFactory => JSONFactory121, APIMethods121}


import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.Extraction
import net.liftweb.common._
import code.model._
import net.liftweb.json.JsonAST.JValue
import APIUtil._
import net.liftweb.util.Helpers._
import net.liftweb.http.rest.RestHelper
import net.liftweb.common.Full
import code.kycdocuments.KycDocuments
import code.kycmedias.KycMedias
import code.kycstatuses.KycStatuses
import code.kycchecks.KycChecks
import code.socialmedia.{SocialMediaHandle, SocialMedia}
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import net.liftweb.json.JsonDSL._
import code.customer.{Customer}
import code.util.Helper._
import net.liftweb.http.js.JE.JsRaw


trait APIMethods200 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here


  val defaultBankId = Props.get("defaultBank.bank_id", "DEFAULT_BANK_ID_NOT_SET")


  // New 2.0.0
  // shows a small representation of View
  private def bankAccountBasicListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(basicBankAccountList(bankAccounts, user))
  }

  // Shows accounts without view
  private def coreBankAccountListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(basicBankAccountList(bankAccounts, user))
  }

  private def basicBankAccountList(bankAccounts: List[BankAccount], user : Box[User]): List[BasicAccountJSON] = {
    val accJson : List[BasicAccountJSON] = bankAccounts.map(account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[BasicViewJSON] =
        views.map( v => {
          JSONFactory200.createViewBasicJSON(v)
        })
      JSONFactory200.createBasicAccountJSON(account,viewsAvailable)
    })
    accJson
  }


  private def coreBankAccountList(bankAccounts: List[BankAccount], user : Box[User]): List[BasicAccountJSON] = {
    val accJson : List[BasicAccountJSON] = bankAccounts.map(account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[BasicViewJSON] =
        views.map( v => {
          JSONFactory200.createViewBasicJSON(v)
        })
      JSONFactory200.createBasicAccountJSON(account,viewsAvailable)
    })
    accJson
  }



  // helper methods end here

  val Implementations2_0_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "2_0_0"

    val exampleDateString : String ="22/08/2013"
    val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)

    resourceDocs += ResourceDoc(
      allAccountsAllBanks,
      apiVersion,
      "allAccountsAllBanks",
      "GET",
      "/accounts",
      "Get accounts at all banks (Authenticated + Anonymous access).",
      """Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views. If
         |the user is authenticated, the list will contain non-public accounts to which the user has access, in addition to
         |all public accounts.
         |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts, apiTagPrivateData, apiTagPublicData))

    lazy val allAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for all banks (private + public)
      case "accounts" :: Nil JsonGet json => {
        user =>
          Full(successJsonResponse(bankAccountBasicListToJson(BankAccount.accounts(user), user)))
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAllBanks,
      apiVersion,
      "privateAccountsAllBanks",
      "GET",
      "/my/accounts",
      "Get private accounts at all banks (Authenticated access).",
      """Returns the list of accounts containing private views for the user at all banks.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      List(apiTagAccounts, apiTagPrivateData))


    // TODO This should be more "core" i.e. don't return views.

    lazy val privateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for all banks
      case "my" :: "accounts" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ APIStrings.UserNotLoggedIn
          } yield {
            val availableAccounts = BankAccount.nonPublicAccounts(u)
            successJsonResponse(bankAccountBasicListToJson(availableAccounts, Full(u)))
          }
      }
    }


    resourceDocs += ResourceDoc(
      publicAccountsAllBanks,
      apiVersion,
      "publicAccountsAllBanks",
      "GET",
      "/accounts/public",
      "Get public accounts at all banks (Anonymous access).",
      """Returns the list of accounts containing public views at all banks
        |For each account the API returns the ID and the available views. Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts, apiTagPublicData))

    lazy val publicAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for all banks
      case "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          val publicAccountsJson = bankAccountBasicListToJson(BankAccount.publicAccounts, Empty)
          Full(successJsonResponse(publicAccountsJson))
      }
    }




    resourceDocs += ResourceDoc(
      allAccountsAtOneBank,
      apiVersion,
      "allAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts",
      "Get accounts at one bank (Authenticated + Anonymous access).",
      """Returns the list of accounts at BANK_ID that the user has access to.
        |For each account the API returns the account ID and the available views.
        |
        |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views.
      """,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts, apiTagPrivateData, apiTagPublicData)
    )

    lazy val allAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for a single bank (private + public)
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet json => {
        user =>
          for{
            bank <- Bank(bankId)
          } yield {
            val availableAccounts = bank.accounts(user)
            successJsonResponse(bankAccountBasicListToJson(availableAccounts, user))
          }
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      apiVersion,
      "privateAccountsAtOneBank",
      "GET",
      "/my/banks/BANK_ID/accounts",
      "Get private accounts at one bank (Authenticated access).",
      """Returns the list of accounts containing private views for the user at BANK_ID.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      List(apiTagAccounts, apiTagPrivateData))


    def privateAccountsAtOneBankResult (bank: Bank, u: User) = {
      val availableAccounts = bank.nonPublicAccounts(u)
      successJsonResponse(bankAccountBasicListToJson(availableAccounts, Full(u)))
    }

    def corePrivateAccountsAtOneBankResult (bank: Bank, u: User) = {
      val availableAccounts = bank.nonPublicAccounts(u)
      successJsonResponse(coreBankAccountListToJson(availableAccounts, Full(u)))
    }


    // This contains an approach to surface a resource via different end points in case of a default bank.
    // The second path is experimental
    lazy val privateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for a single bank
      case "my" :: "banks" :: BankId(bankId) :: "accounts" ::  Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ APIStrings.UserNotLoggedIn
            bank <- Bank(bankId)
          } yield {
            corePrivateAccountsAtOneBankResult(bank, u)
          }
      }
      case "bank" :: "accounts" :: Nil JsonGet json => {
        println("in accounts")
        user =>
          for {
            u <- user ?~ APIStrings.UserNotLoggedIn
            bank <- Bank(BankId(defaultBankId))
          } yield {
            corePrivateAccountsAtOneBankResult(bank, u)
          }
      }

    }

    resourceDocs += ResourceDoc(
      publicAccountsAtOneBank,
      apiVersion,
      "publicAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/public",
      "Get public accounts at one bank (Anonymous access).",
      """Returns a list of the public accounts at BANK_ID. For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is not required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts, apiTagPublicData))

    lazy val publicAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          for {
            bank <- Bank(bankId)
          } yield {
            val publicAccountsJson = bankAccountBasicListToJson(bank.publicAccounts, Empty)
            successJsonResponse(publicAccountsJson)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getKycDocuments,
      apiVersion,
      "getKycDocuments",
      "GET",
      "/customers/CUSTOMER_NUMBER/kyc_documents",
      "Get KYC (know your customer) documents for a customer",
      """Get a list of documents that affirm the identity of the customer
        |Passport, driving licence etc.
        |Authentication is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc))

    lazy val getKycDocuments  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_documents" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {APIStrings.CustomerNotFound}
          } yield {
            val kycDocuments = KycDocuments.kycDocumentProvider.vend.getKycDocuments(cNumber)
            val json = JSONFactory200.createKycDocumentsJSON(kycDocuments)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


  resourceDocs += ResourceDoc(
      getKycMedia,
      apiVersion,
      "getKycMedia",
      "GET",
      "/customers/CUSTOMER_NUMBER/kyc_media",
      "Get KYC Media for a customer",
      """Get KYC media (scans, pictures, videos) that affirms the identity of the customer.
        |
        |Authentication is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
    false,
    false,
    List(apiTagCustomer, apiTagKyc))

    lazy val getKycMedia  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_media" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {APIStrings.CustomerNotFound}
          } yield {
            val kycMedias = KycMedias.kycMediaProvider.vend.getKycMedias(cNumber)
            val json = JSONFactory200.createKycMediasJSON(kycMedias)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getKycChecks,
      apiVersion,
      "getKycChecks",
      "GET",
      "/customers/CUSTOMER_NUMBER/kyc_checks",
      "Get KYC checks for the logged in customer",
      """Messages sent to the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc))

    lazy val getKycChecks  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_checks" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {APIStrings.CustomerNotFound}
          } yield {
            val kycChecks = KycChecks.kycCheckProvider.vend.getKycChecks(cNumber)
            val json = JSONFactory200.createKycChecksJSON(kycChecks)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }
    resourceDocs += ResourceDoc(
      getKycStatuses,
      apiVersion,
      "getKycStatuses",
      "GET",
      "/customers/CUSTOMER_NUMBER/kyc_statuses",
      "Get the KYC statuses for a customer",
      """Get the KYC statuses for a customer over time
        |
        |Authentication is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc))

    lazy val getKycStatuses  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_statuses" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {APIStrings.CustomerNotFound}
          } yield {
            val kycStatuses = KycStatuses.kycStatusProvider.vend.getKycStatuses(cNumber)
            val json = JSONFactory200.createKycStatusesJSON(kycStatuses)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getSocialMediaHandles,
      apiVersion,
      "getSocialMedia",
      "GET",
      "/customers/CUSTOMER_NUMBER/social_media_handles",
      "Get social media handles for a customer",
      """Get social media handles for a customer.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc))

    lazy val getSocialMediaHandles  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "social_media_handles" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {APIStrings.CustomerNotFound}
          } yield {
            val kycSocialMedias = SocialMediaHandle.socialMediaHandleProvider.vend.getSocialMedias(cNumber)
            val json = JSONFactory200.createSocialMediasJSON(kycSocialMedias)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }




    resourceDocs += ResourceDoc(
      addKycDocument,
      apiVersion,
      "addKycDocument",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_NUMBER/kyc_documents",
      "Add a KYC document for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycDocumentJSON("wuwjfuha234678", "1234", "passport", "123567", exampleDate, "London", exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc)
    )

    // TODO customerNumber should be in the url but not also in the postedData

    lazy val addKycDocument : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerNumber :: "kyc_documents" :: Nil JsonPost json -> _ => {
        // customerNumber is duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            postedData <- tryo{json.extract[KycDocumentJSON]} ?~! APIStrings.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {APIStrings.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! APIStrings.CustomerNotFound
            kycDocumentCreated <- booleanToBox(
              KycDocuments.kycDocumentProvider.vend.addKycDocuments(
                postedData.id,
                postedData.customer_number,
                postedData.`type`,
                postedData.number,
                postedData.issue_date,
                postedData.issue_place,
                postedData.expiry_date),
              "Server error: could not add KycDocument")
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addKycMedia,
      apiVersion,
      "addKycMedia",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_NUMBER/kyc_media",
      "Add some KYC media for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycMediaJSON("73hyfgayt6ywerwerasd", "1239879", "image", "http://www.example.com/id-docs/123/image.png", exampleDate, "wuwjfuha234678", "98FRd987auhf87jab")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc)
    )

    lazy val addKycMedia : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerNumber :: "kyc_media" :: Nil JsonPost json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            postedData <- tryo{json.extract[KycMediaJSON]} ?~! APIStrings.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {APIStrings.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! APIStrings.CustomerNotFound
            kycDocumentCreated <- booleanToBox(
              KycMedias.kycMediaProvider.vend.addKycMedias(
                postedData.id,
                postedData.customer_number,
                postedData.`type`,
                postedData.url,
                postedData.date,
                postedData.relates_to_kyc_document_id,
                postedData.relates_to_kyc_check_id),
              "Server error: could not add message")
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addKycCheck,
      apiVersion,
      "addKycCheck",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_NUMBER/kyc_check",
      "Add a KYC check for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycCheckJSON("98FRd987auhf87jab", "1239879", exampleDate, "online_meeting", "67876", "Simon Redfern", true, "")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc)
    )

    lazy val addKycCheck : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerNumber :: "kyc_check" :: Nil JsonPost json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            postedData <- tryo{json.extract[KycCheckJSON]} ?~! APIStrings.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {APIStrings.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! APIStrings.CustomerNotFound
            kycCheckCreated <- booleanToBox(
              KycChecks.kycCheckProvider.vend.addKycChecks(
                postedData.id,
                postedData.customer_number,
                postedData.date,
                postedData.how,
                postedData.staff_user_id,
                postedData.staff_name,
                postedData.satisfied,
                postedData.comments),
              "Server error: could not add message")
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addKycStatus,
      apiVersion,
      "addKycStatus",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_NUMBER/kyc_statuses",
      "Add a kyc_status for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycStatusJSON("8762893876", true, exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer, apiTagKyc)
    )

    lazy val addKycStatus : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerNumber :: "kyc_statuses" :: Nil JsonPost json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            postedData <- tryo{json.extract[KycStatusJSON]} ?~! APIStrings.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {APIStrings.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! APIStrings.CustomerNotFound
            kycStatusCreated <- booleanToBox(
              KycStatuses.kycStatusProvider.vend.addKycStatus(
                postedData.customer_number,
                postedData.ok,
                postedData.date),
              "Server error: could not add message")
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addSocialMediaHandle,
      apiVersion,
      "addSocialMediaHandle",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_NUMBER/social_media",
      "Add a social media handle for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(SocialMediaJSON("8762893876", "twitter", "susan@example.com",  exampleDate, exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagCustomer)
    )

    lazy val addSocialMediaHandle : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerNumber :: "social_media" :: Nil JsonPost json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! APIStrings.UserNotLoggedIn
            postedData <- tryo{json.extract[SocialMediaJSON]} ?~! APIStrings.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {APIStrings.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! APIStrings.CustomerNotFound
            kycSocialMediaCreated <- booleanToBox(
              SocialMediaHandle.socialMediaHandleProvider.vend.addSocialMedias(
                postedData.customer_number,
                postedData.`type`,
                postedData.handle,
                postedData.date_added,
                postedData.date_activated),
              "Server error: could not add")
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCoreAccountById,
      apiVersion,
      "coreAccountById",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get account by id.",
      """Information returned about an account specified by ACCOUNT_ID:
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* IBAN
        |
        |
        |OAuth authentication is required""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      apiTagAccounts ::  Nil)

    lazy val getCoreAccountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet json => {

        user =>
          // TODO return specific error if bankId == "BANK_ID" or accountID == "ACCOUNT_ID"
          // Should be a generic guard we can use for all calls (also for userId etc.)
          for {
            account <- BankAccount(bankId, accountId)
            availableviews <- Full(account.permittedViews(user))
            // Assume owner view was requested
            view <- View.fromUrl( ViewId("owner"), account)
            moderatedAccount <- account.moderatedBankAccount(view, user)
          } yield {
            val viewsAvailable = availableviews.map(JSONFactory121.createViewJSON)
            val moderatedAccountJson = JSONFactory200.createCoreBankAccountJSON(moderatedAccount, viewsAvailable)
            val response = successJsonResponse(Extraction.decompose(moderatedAccountJson))
            response
          }
      }
    }



    resourceDocs += ResourceDoc(
      getCoreTransactionsForBankAccount,
      apiVersion,
      "getCoreTransactionsForBankAccount",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions",
      "Get transactions.",
      """Returns transactions list of the account specified by ACCOUNT_ID.
        |
        |Authentication is required.
        |
        |Possible custom headers for pagination:
        |
        |* obp_sort_by=CRITERIA ==> default value: "completed" field
        |* obp_sort_direction=ASC/DESC ==> default value: DESC
        |* obp_limit=NUMBER ==> default value: 50
        |* obp_offset=NUMBER ==> default value: 0
        |* obp_from_date=DATE => default value: date of the oldest transaction registered (format below)
        |* obp_to_date=DATE => default value: date of the newest transaction registered (format below)
        |
        |**Date format parameter**: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2014-07-01T00:00:00.000Z) ==> time zone is UTC.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      List(apiTagAccounts, apiTagTransactions))

    lazy val getCoreTransactionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get transactions
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet json => {
        user =>

          for {
            params <- APIMethods121.getTransactionParams(json)
            bankAccount <- BankAccount(bankId, accountId)
            // Assume owner view was requested
            view <- View.fromUrl( ViewId("owner"), bankAccount)
            transactions <- bankAccount.getModeratedTransactions(user, view, params : _*)
          } yield {
            val json = JSONFactory200.createCoreTransactionsJSON(transactions)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }




  }
}

object APIMethods200 {
}
