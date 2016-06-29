package code.api.v2_0_0

import java.text.SimpleDateFormat
import java.util.Calendar

import code.TransactionTypes.TransactionType
import code.api.APIFailure
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_2_1.OBPAPI1_2_1._
import code.api.v1_2_1.{APIMethods121, AmountOfMoneyJSON => AmountOfMoneyJSON121, JSONFactory => JSONFactory121}
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, CustomerFaceImageJson, TransactionRequestAccountJSON}
import code.entitlement.Entitlement
import code.search.{elasticsearchMetrics, elasticsearchWarehouse}
import net.liftweb.http.CurrentReq
//import code.api.v2_0_0.{CreateCustomerJson}

import code.model.dataAccess.OBPUser
import net.liftweb.mapper.By

//import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.JSONFactory200._
import code.bankconnectors.Connector
import code.fx.fx
import code.kycchecks.KycChecks
import code.kycdocuments.KycDocuments
import code.kycmedias.KycMedias
import code.kycstatuses.KycStatuses
import code.model._
import code.model.dataAccess.BankAccountCreation
import code.socialmedia.SocialMediaHandle
import code.transactionrequests.TransactionRequests

import code.meetings.Meeting
import code.usercustomerlinks.UserCustomerLink

import net.liftweb.common.{Full, _}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.customer.{MockCustomerFaceImage, Customer}
import code.util.Helper._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.json.Extraction
import net.liftweb.json.JsonDSL._


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

  val Implementations2_0_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson: JValue = Nil
    val apiVersion: String = "2_0_0"

    val exampleDateString: String = "22/08/2013"
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
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
      false,
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


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
      true,
      List(apiTagAccount, apiTagPrivateData))


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
      false,
      List(apiTagAccount, apiTagPublicData))






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
      false,
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData)
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
      true,
      List(apiTagAccount, apiTagPrivateData))


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
      false,
      List(apiTagAccount, apiTagPublicData))

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
      false,
      apiTagAccount ::  Nil)

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
      true,
      List(apiTagAccount, apiTagTransaction))

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
        |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
        |This call provides balance and other account information via delegated authenticaiton using OAuth.
        |
        |OAuth authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
        |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      true,
      false,
      apiTagAccount ::  Nil)

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
      false,
      List(apiTagAccount, apiTagView, apiTagEntitlement)
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
      false,
      List(apiTagAccount, apiTagView, apiTagEntitlement))

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
      "Create Account at bank specified by BANK_ID with Id specified by NEW_ACCOUNT_ID",
      "Note: Type is currently ignored and Amount must be zero. You can update the account label with another call (see updateAccountLabel)",
      Extraction.decompose(CreateAccountJSON("An user_id","CURRENT", AmountOfMoneyJSON121("EUR", "0"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagAccount)
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
            loggedInUser <- user ?~! ErrorMessages.UserNotLoggedIn
            jsonBody <- tryo (json.extract[CreateAccountJSON]) ?~ ErrorMessages.InvalidJsonFormat
            user_id <- tryo (if (jsonBody.user_id.nonEmpty) jsonBody.user_id else loggedInUser.userId) ?~ s"Problem getting user_id"
            postedOrLoggedInUser <- User.findByUserId(user_id) ?~! ErrorMessages.UserNotFoundById
            bank <- Bank(bankId) ?~ s"Bank $bankId not found"
            hasRoles <- booleanToBox(hasEntitlement(bankId.value, loggedInUser.userId, IsHackathonDeveloper) == true || hasEntitlement(bankId.value, loggedInUser.userId, CanCreateAccount) == true, s"Logged in user must have assigned role $CanCreateAccount or $IsHackathonDeveloper")
            initialBalanceAsString <- tryo (jsonBody.balance.amount) ?~ s"Problem getting balance amount"
            accountType <- tryo(jsonBody.`type`) ?~ s"Problem getting type"
            initialBalanceAsNumber <- tryo {BigDecimal(initialBalanceAsString)} ?~! ErrorMessages.InvalidInitalBalance
            isTrue <- booleanToBox(0 == initialBalanceAsNumber) ?~ s"Initial balance must be zero"
            currency <- tryo (jsonBody.balance.currency) ?~ s"Problem getting balance currency"
            // TODO Since this is a PUT, we should replace the resource if it already exists but will need to check persmissions
            accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty,
              s"Account with id $accountId already exists at bank $bankId")
            bankAccount <- Connector.connector.vend.createSandboxBankAccount(bankId, accountId, currency, initialBalanceAsNumber, postedOrLoggedInUser.name)
          } yield {
            BankAccountCreation.setAsOwner(bankId, accountId, postedOrLoggedInUser)

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
      s"""Get Transaction Types for the bank specified by BANK_ID:
          |
          |Lists the possible Transaction Types available at the bank (as opposed to Transaction Request Types which are the possible ways Transactions can be created by this API Server).
          |
          |  * id : Unique transaction type id across the API instance. SHOULD be a UUID. MUST be unique.
          |  * bank_id : The bank that supports this TransactionType
          |  * short_code : A short code (SHOULD have no-spaces) which MUST be unique across the bank. May be stored with Transactions to link here
          |  * summary : A succinct summary
          |  * description : A longer description
          |  * charge : The charge to the customer for each one of these
          |
          |${authenticationRequiredMessage(!getTransactionTypesIsPublic)}""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      false,
      List(apiTagBank)
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



    import net.liftweb.json.JsonAST._
    import net.liftweb.json.Extraction._
    import net.liftweb.json.Printer._
    val exchangeRates = pretty(render(decompose(fx.exchangeRates)))

    resourceDocs += ResourceDoc(
      createTransactionRequest,
      apiVersion,
      "createTransactionRequest",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests",
      "Create Transaction Request.",
      s"""Initiate a Payment via a Transaction Request.
        |
        |This is the preferred method to create a payment and supersedes makePayment in 1.2.1.
        |
        |PSD2 Context: Third party access access to payments is a core tenent of PSD2.
        |
        |This call satisfies that requirement from several perspectives:
        |
        |1) A transaction can be initiated by a third party application.
        |
        |2) The customer is informed of the charge that will incurred.
        |
        |3) The call uses delegated authentication (OAuth)
        |
        |See [this python code](https://github.com/OpenBankProject/Hello-OBP-DirectLogin-Python/blob/master/hello_payments.py) for a complete example of this flow.
        |
        |In sandbox mode, if the amount is less than 100 (any currency), the transaction request will create a transaction without a challenge, else a challenge will need to be answered.
        |
        |You can transfer between different currency accounts. (new in 2.0.0). The currency in body must match the sending account.
        |
        |Currently TRANSACTION_REQUEST_TYPE must be set to SANDBOX_TAN
        |
        |The following static FX rates are available in sandbox mode:
        |
        |${exchangeRates}
        |
        |
        |The payer is set in the URL. Money comes out of the BANK_ID and ACCOUNT_ID specified in the URL
        |
        |The payee is set in the request body. Money goes into the BANK_ID and ACCOUNT_IDO specified in the request body.
        |
        |
        |
        |
        |""",
      Extraction.decompose(TransactionRequestBodyJSON (
        TransactionRequestAccountJSON("BANK_ID", "ACCOUNT_ID"),
        AmountOfMoneyJSON121("EUR", "100.53"),
        "A description for the transaction to be created"
      )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagTransactionRequest))

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
              isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true || hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true , ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
              toBankId <- tryo(BankId(transBodyJson.to.bank_id))
              toAccountId <- tryo(AccountId(transBodyJson.to.account_id))
              toAccount <- tryo{BankAccount(toBankId, toAccountId).get} ?~! {ErrorMessages.CounterpartyNotFound}
              // Prevent default value for transaction request type (at least).
              // Consider: Add valid list of Transaction Request Types to Props "transactionRequests_supported_types" and use that below
              isValidTransactionRequestType <- tryo(assert(transactionRequestType.value != "TRANSACTION_REQUEST_TYPE")) ?~! s"${ErrorMessages.InvalidTransactionRequestType} : Invalid value is: '${transactionRequestType.value}' Valid values are: ${TransactionRequests.CHALLENGE_SANDBOX_TAN}"
              transferCurrencyEqual <- tryo(assert(transBodyJson.value.currency == fromAccount.currency)) ?~! {"Transfer body currency and holder account currency must be the same."}
              createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv200(u, fromAccount, toAccount, transactionRequestType, transBody)
            } yield {
              // Explicitly format as v2.0.0 json
              val json = JSONFactory200.createTransactionRequestWithChargeJSON(createdTransactionRequest)
              createdJsonResponse(Extraction.decompose(json))
            }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }

    resourceDocs += ResourceDoc(
      answerTransactionRequestChallenge,
      apiVersion,
      "answerTransactionRequestChallenge",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge.",
      "In Sandbox mode, any string that can be converted to a positive integer will be accepted as an answer.",
      Extraction.decompose(ChallengeAnswerJSON("89123812", "123345")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagTransactionRequest))

    lazy val answerTransactionRequestChallenge: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              fromBank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~! {"Unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              answerJson <- tryo{json.extract[ChallengeAnswerJSON]} ?~ {"Invalid json format"}
              //TODO check more things here
              answerOk <- Connector.connector.vend.answerTransactionRequestChallenge(transReqId, answerJson.answer)
              //create transaction and insert its id into the transaction request
              transactionRequest <- Connector.connector.vend.createTransactionAfterChallengev200(u, transReqId)
            } yield {

              // Format explicitly as v2.0.0 json
              val json = JSONFactory200.createTransactionRequestWithChargeJSON(transactionRequest)
              //successJsonResponse(Extraction.decompose(json))

              val successJson = Extraction.decompose(json)
              successJsonResponse(successJson, 202)
            }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getTransactionRequests,
      apiVersion,
      "getTransactionRequests",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get Transaction Requests." ,
      """Returns transaction requests for account specified by ACCOUNT_ID at bank specified by BANK_ID.
        |
        |The VIEW_ID specified must be 'owner' and the user must have access to this view.
        |
        |Version 2.0.0 now returns charge information.
        |
        |Transaction Requests serve to initiate transactions that may or may not proceed. They contain information including:
        |
        |* Transaction Request Id
        |* Type
        |* Status (INITIATED, COMPLETED)
        |* Challenge (in order to confirm the request)
        |* From Bank / Account
        |* Body including To Account, Currency, Value, Description and other initiation information. (Could potentialy include a list of future transactions.)
        |* Related Transactions
        |
        |PSD2 Context: PSD2 requires transparency of charges to the customer.
        |This endpoint provides the charge that would be applied if the Transaction Request proceeds - and a record of that charge there after.
        |The customer can proceed with the Transaction by answering the security challenge.
        |
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagTransactionRequest))

    lazy val getTransactionRequests: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              fromBank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~! {ErrorMessages.AccountNotFound}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              transactionRequests <- Connector.connector.vend.getTransactionRequests(u, fromAccount)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createTransactionRequestJSONs(transactionRequests)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createUser,
      apiVersion,
      "createUser",
      "POST",
      "/users",
      "Create User.",
      s"""Creates OBP user.
        | No authorisation (currently) required.
        |
        | Mimics current webform to Register.
        |
        | Requires username(email) and password.
        |
        | Returns 409 error if username not unique.
        |
        | May require validation of email address.
        |
        |""",
      Extraction.decompose(CreateUserJSON("someone@example.com", "my-secure-password", "James", "Brown")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagOnboarding, apiTagUser))

    lazy val createUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: Nil JsonPost json -> _ => {
        user =>
          for {
            postedData <- tryo {json.extract[CreateUserJSON]} ?~! ErrorMessages.InvalidJsonFormat
          } yield {
            if (OBPUser.find(By(OBPUser.email, postedData.email)).isEmpty) {
              val userCreated = OBPUser.create
                .firstName(postedData.first_name)
                .lastName(postedData.last_name)
                .email(postedData.email)
                .password(postedData.password)
                .validated(true) // TODO Get this from Props
                .saveMe()
              if (userCreated.saved_?) {
                val json = JSONFactory200.createUserJSONfromOBPUser(userCreated)
                successJsonResponse(Extraction.decompose(json), 201)
              }
              else
                Full(errorJsonResponse("Error occurred during user creation."))
            }
            else {
              Full(errorJsonResponse("User with the same email already exists."))
            }
          }
      }
    }



    resourceDocs += ResourceDoc(
      createMeeting,
      apiVersion,
      "createMeeting",
      "POST",
      "/banks/BANK_ID/meetings",
      "Create Meeting: Initiate a video conference/call with the bank.",
      """The Meetings resource contains meta data about video/other conference sessions, not the video/audio/chat itself.
        |
        |The actual conferencing is handled by external providers. Currently OBP supports tokbox video conferences (WIP).
        |
        |This is not a recomendation of tokbox per se.
        |
        |provider_id determines the provider of the meeting / video chat service. MUST be url friendly (no spaces).
        |
        |purpose_id explains the purpose of the chat. onboarding | mortgage | complaint etc. MUST be url friendly (no spaces).
        |
        |Login is required.
        |
        |This call is **experimental**. Currently staff_user_id is not set. Further calls will be needed to correctly set this.
      """.stripMargin,
      Extraction.decompose(CreateMeetingJSON("tokbox", "onboarding")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))


    lazy val createMeeting: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("meeting.tokbox_enabled", false)) {
            for {
              // TODO use these keys to get session and tokens from tokbox
              providerApiKey <- Props.get("meeting.tokbox_api_key") ~> APIFailure(ErrorMessages.MeetingApiKeyNotConfigured, 403)
              providerSecret <- Props.get("meeting.tokbox_api_secret") ~> APIFailure(ErrorMessages.MeetingApiSecretNotConfigured, 403)
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              postedData <- tryo {json.extract[CreateMeetingJSON]} ?~ ErrorMessages.InvalidJsonFormat
              now = Calendar.getInstance().getTime()
              sessionId <- tryo{code.opentok.OpenTokUtil.getSession.getSessionId()}
              customerToken <- tryo{code.opentok.OpenTokUtil.generateTokenForPublisher(60)}
              staffToken <- tryo{code.opentok.OpenTokUtil.generateTokenForModerator(60)}
              meeting <- Meeting.meetingProvider.vend.createMeeting(bank.bankId, u, u, postedData.provider_id, postedData.purpose_id, now, sessionId, customerToken, staffToken)
            } yield {
              // Format the data as V2.0.0 json
              val json = JSONFactory200.createMeetingJSON(meeting)
              successJsonResponse(Extraction.decompose(json), 201)
            }
          } else {
            Full(errorJsonResponse(ErrorMessages.MeetingsNotSupported))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getMeetings,
      apiVersion,
      "getMeetings",
      "GET",
      "/banks/BANK_ID/meetings",
      "Get Meetings",
      """Meetings contain meta data about, and are used to facilitate, video conferences / chats etc.
        |
        |The actual conference/chats are handled by external services.
        |
        |Login is required.
        |
        |This call is **experimental** and will require further authorisation in the future.
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))


    lazy val getMeetings: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("meeting.tokbox_enabled", false)) {
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              fromBank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}

              providerApiKey <- Props.get("meeting.tokbox_api_key") ~> APIFailure(ErrorMessages.MeetingApiKeyNotConfigured, 403)
              providerSecret <- Props.get("meeting.tokbox_api_secret") ~> APIFailure(ErrorMessages.MeetingApiSecretNotConfigured, 403)
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              // now = Calendar.getInstance().getTime()
              meetings <- Meeting.meetingProvider.vend.getMeetings(bank.bankId, u)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createMeetingJSONs(meetings)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse(ErrorMessages.MeetingsNotSupported))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getMeeting,
      apiVersion,
      "getMeeting",
      "GET",
      "/banks/BANK_ID/meetings/MEETING_ID",
      "Get Meeting specified by BANK_ID / MEETING_ID",
      """Meetings contain meta data about, and are used to facilitate, video conferences / chats etc.
        |
        |The actual conference/chats are handled by external services.
        |
        |Login is required.
        |
        |This call is **experimental** and will require further authorisation in the future.
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))


    lazy val getMeeting: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "meetings" :: meetingId :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("meeting.tokbox_enabled", false)) {
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              fromBank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              providerApiKey <- Props.get("meeting.tokbox_api_key") ~> APIFailure(ErrorMessages.MeetingApiKeyNotConfigured, 403)
              providerSecret <- Props.get("meeting.tokbox_api_secret") ~> APIFailure(ErrorMessages.MeetingApiSecretNotConfigured, 403)
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
              meeting <- Meeting.meetingProvider.vend.getMeeting(bank.bankId, u, meetingId)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createMeetingJSON(meeting)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse(ErrorMessages.MeetingsNotSupported))
          }
      }
    }

    //

    resourceDocs += ResourceDoc(
      createCustomer,
      apiVersion,
      "createCustomer",
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer.",
      """Add a customer linked to the user specified by user_id
        |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
        |This call may require additional permissions/role in the future.
        |For now the authenticated user can create at most one linked customer.
        |Dates need to be in the format 2013-01-21T23:08:00Z
        |OAuth authentication is required.
        |""",
      Extraction.decompose(CreateCustomerJson("user_id to attach this customer to e.g. 123213", "new customer number 687687678", "Joe David Bloggs",
        "+44 07972 444 876", "person@example.com", CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate),
        exampleDate, "Single", 1, List(exampleDate), "Bachelor’s Degree", "Employed", true, exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagCustomer))

    lazy val createCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! "User must be logged in to post Customer" // TODO. CHECK user has role to create a customer / create a customer for another user id.
            bank <- tryo(Bank(bankId).get) ?~! {ErrorMessages.BankNotFound}
            canCreateCustomer <- Entitlement.entitlement.vend.getEntitlement(bank.bankId.value, u.userId, CanCreateCustomer.toString) ?~ {ErrorMessages.UserDoesNotHaveRole + CanCreateCustomer +"."}
            postedData <- tryo{json.extract[CreateCustomerJson]} ?~! ErrorMessages.InvalidJsonFormat
            checkAvailable <- tryo(assert(Customer.customerProvider.vend.checkCustomerNumberAvailable(bankId, postedData.customer_number) == true)) ?~! ErrorMessages.CustomerNumberAlreadyExists
            // TODO The user id we expose should be a uuid . For now we have a long direct from the database.
            customer_user <- User.findByUserId(postedData.user_id) ?~! ErrorMessages.UserNotFoundById
            customer <- booleanToBox(Customer.customerProvider.vend.getCustomer(bankId, customer_user).isEmpty) ?~ ErrorMessages.CustomerAlreadyExistsForUser
            customer <- Customer.customerProvider.vend.addCustomer(bankId,
              customer_user,
              postedData.customer_number,
              postedData.legal_name,
              postedData.mobile_phone_number,
              postedData.email,
              MockCustomerFaceImage(postedData.face_image.date, postedData.face_image.url),
              postedData.date_of_birth,
              postedData.relationship_status,
              postedData.dependants,
              postedData.dob_of_dependants,
              postedData.highest_education_attained,
              postedData.employment_status,
              postedData.kyc_status,
              postedData.last_ok_date) ?~! "Could not create customer"
          } yield {
            val successJson = Extraction.decompose(customer)
            successJsonResponse(successJson, 201)
          }
      }
    }



    resourceDocs += ResourceDoc(
      getCurrentUser,
      apiVersion,
      "getCurrentUser", // TODO can we get this string from the val two lines above?
      "GET",
      "/users/current",
      "Get Current User",
      """Get the logged in user
        |
        |Login is required.
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagUser))


    lazy val getCurrentUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "current" :: Nil JsonGet _ => {
        user =>
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createUserJSON(u)
                successJsonResponse(Extraction.decompose(json))
              }
      }
    }


    resourceDocs += ResourceDoc(
      getUser,
      apiVersion,
      "getUser",
      "GET",
      "/users/USER_EMAIL",
      "Get User by Email Address",
      """Get the user by email address
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagUser))


    lazy val getUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: userEmail :: Nil JsonGet _ => {
        user =>
            for {
              l <- user ?~ ErrorMessages.UserNotLoggedIn
              b <- Bank.all.headOption //TODO: This is a temp workaround
              canGetAnyUser <- booleanToBox(hasEntitlement(b.bankId.value, l.userId, ApiRole.CanGetAnyUser), "CanGetAnyUser entitlement required")
              // Workaround to get userEmail address directly from URI without needing to URL-encode it
              u <- OBPUser.getApiUserByEmail(CurrentReq.value.uri.split("/").last)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createUserJSON(u)
                successJsonResponse(Extraction.decompose(json))
              }
      }
    }


    resourceDocs += ResourceDoc(
      createUserCustomerLinks,
      apiVersion,
      "createUserCustomerLinks",
      "POST",
      "/banks/user_customer_links",
      "Create user customer link.",
      """Link a customer and an user
        |This call may require additional permissions/role in the future.
        |For now the authenticated user can create at most one linked customer.
        |OAuth authentication is required.
        |""",
      Extraction.decompose(CreateUserCustomerLinkJSON("be106783-b4fa-48e6-b102-b178a11a8e9b", "02141bc6-0a69-4fba-b4db-a17e5fbbbdcc")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagUser, apiTagCustomer))

    lazy val createUserCustomerLinks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: "user_customer_links" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! "User must be logged in to post user customer link"
            postedData <- tryo{json.extract[CreateUserCustomerLinkJSON]} ?~! ErrorMessages.InvalidJsonFormat
            user_id <- booleanToBox(postedData.user_id.nonEmpty) ?~ "Field user_id is not defined in the posted json!"
            user <- User.findByUserId(postedData.user_id) ?~! ErrorMessages.UserNotFoundById
            customer_id <- booleanToBox(postedData.customer_id.nonEmpty) ?~ "Field customer_id is not defined in the posted json!"
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(postedData.customer_id) ?~ ErrorMessages.CustomerNotFoundByCustomerId
            userCustomerLink <- booleanToBox(UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(postedData.user_id, postedData.customer_id).isEmpty == true) ?~ ErrorMessages.CustomerAlreadyExistsForUser
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(postedData.user_id, postedData.customer_id, exampleDate, true) ?~! "Could not create user_customer_links"
          } yield {
            val successJson = Extraction.decompose(code.api.v2_0_0.JSONFactory200.createUserCustomerLinkJSON(userCustomerLink))
            successJsonResponse(successJson, 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      addEntitlement,
      apiVersion,
      "addEntitlement",
      "POST",
      "/users/USER_ID/entitlements",
      "Add Entitlement to a user.",
      """Create Entitlement. Grant Role to User.
        |
        |Entitlements are used to grant system or bank level roles to users. (For account level privileges, see Views)
        |
        |Authentication is required and the user needs to be a Super Admin. Super Admins are listed in the Props file.""",
      Extraction.decompose(CreateEntitlementJSON("obp-bank-x-gh", "CanQueryOtherUser")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List())

    lazy val addEntitlement : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add access for specific user to a list of views
      case "users" :: userId :: "entitlements" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            isSuperAdmin <- booleanToBox(isSuperAdmin(u.userId)) ?~ "Logged user is not super admin!"
            user <- User.findByUserId(userId) ?~! ErrorMessages.UserNotFoundById
            postedData <- tryo{json.extract[CreateEntitlementJSON]} ?~ "wrong format JSON"
            bank <- booleanToBox(Bank(BankId(postedData.bank_id)).isEmpty == false || postedData.bank_id.nonEmpty == false) ?~! {ErrorMessages.BankNotFound}
            role <- tryo{valueOf(postedData.role_name)} ?~! "wrong role name"
            hasEntitlement <- booleanToBox(hasEntitlement(postedData.bank_id, userId, role) == false, "Entitlement already exists for the user.")
            addedEntitlement <- Entitlement.entitlement.vend.addEntitlement(postedData.bank_id, userId, postedData.role_name)
          } yield {
            val viewJson = JSONFactory200.createEntitlementJSON(addedEntitlement)
            successJsonResponse(Extraction.decompose(viewJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getEntitlements,
      apiVersion,
      "getEntitlements",
      "GET",
      "/users/USER_ID/entitlements",
      "Get Entitlements specified by USER_ID",
      """
        |
        |Login is required.
        |
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagUser, apiTagEntitlement))


    lazy val getEntitlements: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        user =>
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              // isSuperAdmin <- booleanToBox(isSuperAdmin(u.userId)) ?~ "User is not super admin!"
              entitlements <- Entitlement.entitlement.vend.getEntitlements(userId)
            }
            yield {
              // Format the data as V2.0.0 json
              val json = JSONFactory200.createEntitlementJSONs(entitlements)
              successJsonResponse(Extraction.decompose(json))
            }
      }
    }

    resourceDocs += ResourceDoc(
      deleteEntitlement,
      apiVersion,
      "deleteEntitlement",
      "DELETE",
      "/users/USER_ID/entitlement/ENTITLEMENT_ID",
      "Delete Entitlement specified by ENTITLEMENT_ID for an user specified by USER_ID",
      """
        |
        |Authentication is required and the user needs to be a Super Admin.
        |Super Admins are listed in the Props file.
        |
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagUser, apiTagEntitlement))


    lazy val deleteEntitlement: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: userId :: "entitlement" :: entitlementId :: Nil JsonDelete _ => {
        user =>
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              isSuperAdmin <- booleanToBox(isSuperAdmin(u.userId)) ?~ "User is not super admin!"
              entitlement <- tryo{Entitlement.entitlement.vend.getEntitlement(entitlementId)} ?~ "EntitlementId not found"
              deleted <- Entitlement.entitlement.vend.deleteEntitlement(entitlement)
            }
            yield noContentJsonResponse
      }
    }


    resourceDocs += ResourceDoc(
      getAllEntitlements,
      apiVersion,
      "getAllEntitlements",
      "GET",
      "/entitlements",
      "Get all Entitlements",
      """
        |
        |Login is required.
        |
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      true,
      true,
      true,
      List(apiTagUser, apiTagEntitlement))


    lazy val getAllEntitlements: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "entitlements" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            isSuperAdmin <- booleanToBox(isSuperAdmin(u.userId)) ?~ "Logged user is not super admin!"
            entitlements <- Entitlement.entitlement.vend.getEntitlements
          }
          yield {
            // Format the data as V2.0.0 json
            val json = JSONFactory200.createEntitlementJSONs(entitlements)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    // TODO Put message into doc below if not enabled (but continue to show API Doc)
    resourceDocs += ResourceDoc(
        elasticSearchWarehouse,
        apiVersion,
        "elasticSearchWarehouse",
        "GET",
        "/search/warehouse",
        "Search Warehouse Data Via Elasticsearch",
        """
          |Search warehouse data via Elastic Search.
          |
          |Login is required.
          |
          |CanSearchWarehouse entitlement is required to search warehouse data!
          |
          |Send your email, name, project name and user_id to the admins to get access.
          |
          |Elastic (search) is used in the background. See links below for syntax.
          |
          |
          |parameters:
          |
          | esType  - elasticsearch type
          |
          | simple query:
          |
          | q       - plain_text_query
          |
          | df      - default field to search
          |
          | sort    - field to sort on
          |
          | size    - number of hits returned, default 10
          |
          | from    - show hits starting from
          |
          | json query:
          |
          | source  - JSON_query_(URL-escaped)
          |
          |
          |Example usage:
          |
          |GET /search/warehouse/q=findThis
          |
          |or:
          |
          |GET /search/warehouse/source={"query":{"query_string":{"query":"findThis"}}}
          |
          |
          |Note!!
          |
          |The whole JSON query string MUST be URL-encoded:
          |
          |* For {  use %7B
          |* For }  use %7D
          |* For : use %3A
          |* For " use %22
          |
          |etc..
          |
          |
          |
          |Only q, source and esType are passed to Elastic
          |
          |Elastic simple query: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-uri-request.html
          |
          |Elastic JSON query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-filter-context.html
          |
          |You can specify the esType thus: /search/warehouse/esType=type&q=a
          |
        """,
        emptyObjectJson,
        emptyObjectJson,
        emptyObjectJson :: Nil,
        false,
        false,
        false,
        List())

    val esw = new elasticsearchWarehouse
    lazy val elasticSearchWarehouse: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "search" :: "warehouse" :: queryString :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            b <- Bank.all.headOption //TODO: This is a temp workaround
            canSearchWarehouse <- Entitlement.entitlement.vend.getEntitlement(b.bankId.toString, u.userId, ApiRole.CanSearchWarehouse.toString) ?~ "CanSearchWarehouse entitlement required"
          } yield {
            successJsonResponse(Extraction.decompose(esw.searchProxy(u.userId, queryString)))
          }
      }
    }

    // TODO Put message into doc below if not enabled (but continue to show API Doc)
    resourceDocs += ResourceDoc(
        elasticSearchMetrics,
        apiVersion,
        "elasticSearchMetrics",
        "GET",
        "/search/metrics",
        "Search Metrics Data Via Elastic (search)",
        """
          |Search metrics data via Elastic Search.
          |
          |Login is required.
          |
          |CanSearchMetrics entitlement is required to search metrics data.
          |
          |
          |parameters:
          |
          | esType  - elasticsearch type
          |
          | simple query:
          |
          | q       - plain_text_query
          |
          | df      - default field to search
          |
          | sort    - field to sort on
          |
          | size    - number of hits returned, default 10
          |
          | from    - show hits starting from
          |
          | json query:
          |
          | source  - JSON_query_(URL-escaped)
          |
          |
          |example usage:
          |
          | /search/metrics/q=findThis
          |
          |or:
          |
          | /search/metrics/source={"query":{"query_string":{"query":"findThis"}}}
          |
          |
          |Note!!
          |
          |The whole JSON query string MUST be URL-encoded:
          |
          |* For {  use %7B
          |* For }  use %7D
          |* For : use %3A
          |* For " use %22
          |
          |etc..
          |
          |
          |
          |Only q, source and esType are passed to Elastic
          |
          |Elastic simple query: https://www.elastic.co/guide/en/elasticsearch/reference/current/search-uri-request.html
          |
          |Elastic JSON query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-filter-context.html
          |
          |
        """,
        emptyObjectJson,
        emptyObjectJson,
        emptyObjectJson :: Nil,
        false,
        false,
        false,
        List())

    val esm = new elasticsearchMetrics
    lazy val elasticSearchMetrics: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "search" :: "metrics" :: queryString :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            b <- Bank.all.headOption //TODO: This is a temp workaround
            canSearchMetrics <- Entitlement.entitlement.vend.getEntitlement(b.bankId.toString, u.userId, ApiRole.CanSearchMetrics.toString) ?~ "CanSearchMetrics entitlement required"
          } yield {
            successJsonResponse(Extraction.decompose(esm.searchProxy(u.userId, queryString)))
          }
      }
    }

  }


}

object APIMethods200 {
}
