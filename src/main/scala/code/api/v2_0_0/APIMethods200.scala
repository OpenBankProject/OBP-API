package code.api.v2_0_0

import java.text.SimpleDateFormat

import code.TransactionTypes.TransactionType
import code.api.APIFailure
import code.api.util.APIUtil
import code.api.util.ErrorMessages
import code.api.v1_2_1.OBPAPI1_2_1._

import code.api.v1_2_1.{JSONFactory => JSONFactory121, AmountOfMoneyJSON => AmountOfMoneyJSON121, APIMethods121}


import code.api.v1_4_0.JSONFactory1_4_0._

import code.bankconnectors.Connector
import code.model.dataAccess.{BankAccountCreation}


import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json
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
import code.customer.{CustomerMessages, Customer}
import code.util.Helper._
import net.liftweb.http.js.JE.JsRaw

import net.liftweb.json.{ShortTypeHints, DefaultFormats, Extraction}


trait APIMethods200 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here


  val defaultBankId = Props.get("defaultBank.bank_id", "DEFAULT_BANK_ID_NOT_SET")



  // shows a small representation of View
  private def bankAccountBasicListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(basicBankAccountList(bankAccounts, user))
  }

  // Shows accounts without view
  private def coreBankAccountListToJson(callerContext: CallerContext, codeContext: CodeContext, bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(coreBankAccountList(callerContext, codeContext, bankAccounts, user))
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

  private def coreBankAccountList(callerContext: CallerContext, codeContext: CodeContext, bankAccounts: List[BankAccount], user : Box[User]): List[CoreAccountJSON] = {
    val accJson : List[CoreAccountJSON] = bankAccounts.map(account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[BasicViewJSON] =
        views.map( v => {
          JSONFactory200.createViewBasicJSON(v)
        })

      val dataContext = DataContext(user, Some(account.bankId), Some(account.accountId), Empty, Empty, Empty)

      val links = code.api.util.APIUtil.getHalLinks(callerContext, codeContext, dataContext)

      JSONFactory200.createCoreAccountJSON(account, links)
    })
    accJson
  }



  // helper methods end here

  val Implementations2_0_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "2_0_0"

    val exampleDateString : String ="22/08/2013"
    val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)

    val codeContext = CodeContext(resourceDocs, apiRelations)





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


    apiRelations += ApiRelation(privateAccountsAllBanks, getCoreAccountById, "detail")
    apiRelations += ApiRelation(privateAccountsAllBanks, privateAccountsAllBanks, "self")



        lazy val privateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
          //get private accounts for all banks
          case "my" :: "accounts" :: Nil JsonGet json => {
            user =>

              for {
                u <- user ?~ ErrorMessages.UserNotLoggedIn
              } yield {
                val availableAccounts = BankAccount.nonPublicAccounts(u)
                val coreBankAccountListJson = coreBankAccountListToJson(CallerContext(privateAccountsAllBanks), codeContext, availableAccounts, Full(u))
                val response = successJsonResponse(coreBankAccountListJson)
                response
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

    def corePrivateAccountsAtOneBankResult (callerContext: CallerContext, codeContext: CodeContext, bank: Bank, u: User) = {
      val availableAccounts = bank.nonPublicAccounts(u)
      successJsonResponse(coreBankAccountListToJson(callerContext, codeContext, availableAccounts, Full(u)))
    }



    // This contains an approach to surface a resource via different end points in case of a default bank.
    // The second path is experimental
    lazy val privateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {


      //get private accounts for a single bank
      case "my" :: "banks" :: BankId(bankId) :: "accounts" ::  Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)

          } yield {
            corePrivateAccountsAtOneBankResult(CallerContext(privateAccountsAtOneBank), codeContext, bank, u)
          }
      }
      case "bank" :: "accounts" :: Nil JsonGet json => {
        println("in accounts")
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(BankId(defaultBankId))
          } yield {
            corePrivateAccountsAtOneBankResult(CallerContext(privateAccountsAtOneBank), codeContext, bank, u)
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {ErrorMessages.CustomerNotFound}
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {ErrorMessages.CustomerNotFound}
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {ErrorMessages.CustomerNotFound}
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {ErrorMessages.CustomerNotFound}
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            cNumber <- tryo(customerNumber) ?~! {ErrorMessages.CustomerNotFound}
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
      "KYC Documents contain the document type (e.g. passport), place of issue, expiry etc. ",
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[KycDocumentJSON]} ?~! ErrorMessages.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! ErrorMessages.CustomerNotFound
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
      "KYC Media resources relate to KYC Documents and KYC Checks and contain media urls for scans of passports, utility bills etc.",
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[KycMediaJSON]} ?~! ErrorMessages.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! ErrorMessages.CustomerNotFound
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
      "KYC Checks store details of checks on a customer made by the KYC team, their comments and a satisfied status.",
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[KycCheckJSON]} ?~! ErrorMessages.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! ErrorMessages.CustomerNotFound
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
      "KYC Status is a timeline of the KYC status of the customer",
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[KycStatusJSON]} ?~! ErrorMessages.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! ErrorMessages.CustomerNotFound
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
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[SocialMediaJSON]} ?~! ErrorMessages.InvalidJsonFormat
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! ErrorMessages.CustomerNotFound
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


    // Copied from 1.2.1 and modified

    resourceDocs += ResourceDoc(
      accountById,
      apiVersion,
      "accountById",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get account by id.",
      """Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* IBAN
        |* Available views (sorted by short_name)
        |
        |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
        |
        |OAuth authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      true,
      apiTagAccounts ::  Nil)

    lazy val accountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn // Check we have a user (rather than error or empty)
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound} // Check bank exists.
            account <- tryo(BankAccount(bank.bankId, accountId).get) ?~! {ErrorMessages.AccountNotFound} // Check Account exists.
            availableViews <- Full(account.permittedViews(user))
            view <- tryo(View.fromUrl(viewId, account).get) ?~! {ErrorMessages.ViewNotFound}
            moderatedAccount <- account.moderatedBankAccount(view, user)
          } yield {
            val viewsAvailable = availableViews.map(JSONFactory121.createViewJSON).sortBy(_.short_name)
            val moderatedAccountJson = JSONFactory121.createBankAccountJSON(moderatedAccount, viewsAvailable)
            successJsonResponse(Extraction.decompose(moderatedAccountJson))
          }
      }
    }


    /////


    resourceDocs += ResourceDoc(
      getPermissionsForBankAccount,
      apiVersion,
      "getPermissionsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions",
      "Get access.",
      """Returns the list of the permissions at BANK_ID for account ACCOUNT_ID, with each time a pair composed of the user and the views that he has access to.
        |
        |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts, apiTagViews, apiTagEntitlements)
    )

    lazy val getPermissionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get access
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn // Check we have a user (rather than error or empty)
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound} // Check bank exists.
            account <- tryo(BankAccount(bank.bankId, accountId).get) ?~! {ErrorMessages.AccountNotFound} // Check Account exists.
            permissions <- account permissions u
          } yield {
            val permissionsJSON = JSONFactory121.createPermissionsJSON(permissions.sortBy(_.user.emailAddress))
            successJsonResponse(Extraction.decompose(permissionsJSON))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPermissionForUserForBankAccount,
      apiVersion,
      "getPermissionForUserForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID",
      "Get access for specific user.",
      """Returns the list of the views at BANK_ID for account ACCOUNT_ID that a USER_ID at their provider PROVIDER_ID has access to.
        |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
        |
        |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts, apiTagViews, apiTagEntitlements))

    lazy val getPermissionForUserForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn // Check we have a user (rather than error or empty)
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound} // Check bank exists.
            account <- tryo(BankAccount(bank.bankId, accountId).get) ?~! {ErrorMessages.AccountNotFound} // Check Account exists.
            permission <- account permission(u, providerId, userId)
          } yield {
            val views = JSONFactory121.createViewsJSON(permission.views.sortBy(_.viewId.value))
            successJsonResponse(Extraction.decompose(views))
          }
      }
    }



    resourceDocs += ResourceDoc(
      createAccount,
      apiVersion,
      "createAccount",
      "PUT",
      "/banks/BANK_ID/accounts/NEW_ACCOUNT_ID",
      "Create an Account at bank specified by BANK_ID with Id specified by NEW_ACCOUNT_ID",
      "Note: Type is currently ignored and Amount must be zero. You can update the account label with another call (see updateAccountLabel)",
      Extraction.decompose(CreateAccountJSON("CURRENT", AmountOfMoneyJSON121("EUR", "0"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      List(apiTagAccounts)
    )

    apiRelations += ApiRelation(createAccount, createAccount, "self")
    apiRelations += ApiRelation(createAccount, getCoreAccountById, "detail")

    // Note: This doesn't currently work (links only have access to same version resource docs). TODO fix me.
    apiRelations += ApiRelation(createAccount, Implementations1_2_1.updateAccountLabel, "update_label")


    lazy val createAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // TODO document this code (make the extract work): "JsonPut json -> _ =>"
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        user => {

          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            jsonBody <- tryo (json.extract[CreateAccountJSON]) ?~ ErrorMessages.InvalidJsonFormat
            initialBalanceAsString <- tryo (jsonBody.balance.amount) ?~ s"Problem getting balance amount"
            accountType <- tryo(jsonBody.`type`) ?~ s"Problem getting type"
            initialBalanceAsNumber <- tryo {BigDecimal(initialBalanceAsString)} ?~! ErrorMessages.InvalidInitalBalance
            isTrue <- booleanToBox(0 == initialBalanceAsNumber) ?~ s"Initial balance must be zero"
            currency <- tryo (jsonBody.balance.currency) ?~ s"Problem getting balance currency"
            bank <- Bank(bankId) ?~ s"Bank $bankId not found"
            // TODO Since this is a PUT, we should replace the resource if it already exists but will need to check persmissions
            accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty,
              s"Account with id $accountId already exists at bank $bankId")
            bankAccount <- Connector.connector.vend.createSandboxBankAccount(bankId, accountId, currency, initialBalanceAsNumber, u.name)
          } yield {
            BankAccountCreation.setAsOwner(bankId, accountId, u)

            val dataContext = DataContext(user, Some(bankAccount.bankId), Some(bankAccount.accountId), Empty, Empty, Empty)
            val links = code.api.util.APIUtil.getHalLinks(CallerContext(createAccount), codeContext, dataContext)
            val json = JSONFactory200.createCoreAccountJSON(bankAccount, links)

            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }



    val getTransactionTypesIsPublic = Props.getBool("apiOptions.getTransactionTypesIsPublic", true)


    resourceDocs += ResourceDoc(
      getTransactionTypes,
      apiVersion,
      "getTransactionTypes",
      "GET",
      "/banks/BANK_ID/transaction-types",
      "Get transaction-types offered by the bank",
      // TODO get the documentation of the parameters from the scala doc of the case class we return
      s"""Returns transaction types for the bank specified by BANK_ID:
          |
          |  * id : Unique transaction type id across the API instance. Ideally a UUID
          |  * bank_id : The bank that supports this TransactionType
          |  * short_code : A short code (ideally-no-spaces) which is unique across the bank. Could be stored with Transactions to link here
          |  * summary : A succinct summary
          |  * description : A longer description
          |  * customer_fee : The fee to the customer for each one of these
          |${authenticationRequiredMessage(!getTransactionTypesIsPublic)}""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      false,
      List(apiTagBanks)
    )

    lazy val getTransactionTypes : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "transaction-types" :: Nil JsonGet _ => {
        user => {
          for {
          // Get Transaction Types from the active provider
            u <- if(getTransactionTypesIsPublic)
              Box(Some(1))
            else
              user ?~! "User must be logged in to retrieve Transaction Types data"
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            transactionTypes <- TransactionType.TransactionTypeProvider.vend.getTransactionTypesForBank(bank.bankId) // ~> APIFailure("No transation types available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory200.createTransactionTypeJSON(transactionTypes)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    ///

    resourceDocs += ResourceDoc(
      createTransactionRequest,
      apiVersion,
      "createTransactionRequest",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests",
      "Create Transaction Request.",
      """Initiate a Payment via a Transaction Request.
        |
        |This is the preferred method to create a payment and supersedes makePayment in 1.2.1.
        |
        |See [this python code](https://github.com/OpenBankProject/Hello-OBP-DirectLogin-Python/blob/master/hello_payments.py) for a complete example of this flow.
        |
        |In sandbox mode, if the amount is less than 100 the transaction request will create a transaction without a challenge, else a challenge will need to be answered.""",
      Extraction.decompose(TransactionRequestBodyJSON (
        TransactionRequestAccountJSON("BANK_ID", "ACCOUNT_ID"),
        AmountOfMoneyJSON121("EUR", "100.53"),
        "A description for the transaction to be created",
        "one of the transaction types possible for the account"
      )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      List(apiTagPayment))

    import code.fx.fx

    lazy val createTransactionRequest: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
            /* TODO:
             * check if user has access using the view that is given (now it checks if user has access to owner view), will need some new permissions for transaction requests
             * test: functionality, error messages if user not given or invalid, if any other value is not existing
            */
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              transBodyJson <- tryo{json.extract[TransactionRequestBodyJSON]} ?~ {ErrorMessages.InvalidJsonFormat}
              transBody <- tryo{getTransactionRequestBodyFromJson(transBodyJson)}
              fromBank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~! {ErrorMessages.AccountNotFound}
              toBankId <- tryo(BankId(transBodyJson.to.bank_id))
              toAccountId <- tryo(AccountId(transBodyJson.to.account_id))
              toAccount <- tryo{BankAccount(toBankId, toAccountId).get} ?~! {ErrorMessages.CounterpartyNotFound}
              //accountsCurrencyEqual <- tryo(assert(fromAccount.currency == toAccount.currency)) ?~! {"Counterparty and holder accounts have differing currencies."}
              //transferCurrencyEqual <- tryo(assert(transBodyJson.value.currency == fromAccount.currency)) ?~! {"Request currency and holder account currency can't be different."}
              rawAmt <- tryo {BigDecimal(transBodyJson.value.amount)} ?~! s"Amount ${transBodyJson.value.amount} not convertible to number"
              rate <- tryo{fx.exchangeRate (fromAccount.currency, toAccount.currency)} ?~! {"This currency convertion not supported."}
              convertedAmount <- rate.map(r => r * rawAmt)
              createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv200(u, fromAccount, toAccount, transactionRequestType, transBody)
            } yield {
              val json = Extraction.decompose(createdTransactionRequest)
              createdJsonResponse(json)
            }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }





///


  }
}

object APIMethods200 {
}
