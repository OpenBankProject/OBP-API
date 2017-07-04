package code.api.v2_0_0

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

import code.TransactionTypes.TransactionType
import code.api.APIFailure
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.{APIUtil, ApiRole, ErrorMessages}
import code.api.v1_2_1.OBPAPI1_2_1._
import code.api.v1_2_1.{APIMethods121, SuccessMessage, AmountOfMoneyJsonV121 => AmountOfMoneyJSON121, JSONFactory => JSONFactory121}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, CustomerFaceImageJson, TransactionRequestAccountJsonV140}
import code.api.v2_0_0.JSONFactory200.bankAccountsListToJson
import code.entitlement.Entitlement
import code.model.BankId
import code.search.{elasticsearchMetrics, elasticsearchWarehouse}
import net.liftweb.http.CurrentReq
import code.model.dataAccess.AuthUser
import net.liftweb.mapper.By
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
import net.liftweb.util.Helpers.{tryo, _}
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.customer.{MockCustomerFaceImage, Customer}
import code.util.Helper._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.json.Extraction
import net.liftweb.json.JsonDSL._
import code.api.ResourceDocs1_4_0.SwaggerJSONFactory._
import code.api.util.ErrorMessages._

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
      val viewsAvailable : List[BasicViewJson] =
        views.map( v => {
          JSONFactory200.createBasicViewJSON(v)
        })
      JSONFactory200.createBasicAccountJSON(account,viewsAvailable)
    })
    accJson
  }

  private def coreBankAccountList(callerContext: CallerContext, codeContext: CodeContext, bankAccounts: List[BankAccount], user : Box[User]): List[CoreAccountJSON] = {
    val accJson : List[CoreAccountJSON] = bankAccounts.map(account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[BasicViewJson] =
        views.map( v => {
          JSONFactory200.createBasicViewJSON(v)
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

    val emptyObjectJson = EmptyClassJson()
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
      "Get all Accounts at all Banks.",
      s"""Get all accounts at all banks the User has access to (Authenticated + Anonymous access).
         |Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views. If
         |the user is authenticated, the list will contain non-public accounts to which the user has access, in addition to
         |all public accounts.
         |
         |${authenticationRequiredMessage(false)}
         |""",
      emptyObjectJson,
      basicAccountsJSON,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val allAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for all banks (private + public)
      case "accounts" :: Nil JsonGet json => {
        user =>
          Full(successJsonResponse(bankAccountBasicListToJson(BankAccount.accounts(user), user)))
      }
    }

    resourceDocs += ResourceDoc(
      corePrivateAccountsAllBanks,
      apiVersion,
      "corePrivateAccountsAllBanks",
      "GET",
      "/my/accounts",
      "Get Accounts at all Banks (Private)",
      s"""Get private accounts at all banks (Authenticated access)
        |Returns the list of accounts containing private views for the user at all banks.
        |For each account the API returns the ID and the available views.
        |
        |${authenticationRequiredMessage(true)}
        |""",
      emptyObjectJson,
      coreAccountsJSON,
      List(UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData))


    apiRelations += ApiRelation(corePrivateAccountsAllBanks, getCoreAccountById, "detail")
    apiRelations += ApiRelation(corePrivateAccountsAllBanks, corePrivateAccountsAllBanks, "self")



        lazy val corePrivateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
          //get private accounts for all banks
          case "my" :: "accounts" :: Nil JsonGet json => {
            user =>

              for {
                u <- user ?~! ErrorMessages.UserNotLoggedIn
              } yield {
                val availableAccounts = BankAccount.nonPublicAccounts(u)
                val coreBankAccountListJson = coreBankAccountListToJson(CallerContext(corePrivateAccountsAllBanks), codeContext, availableAccounts, Full(u))
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
      "Get Public Accounts at all Banks.",
      s"""Get public accounts at all banks (Anonymous access).
        |Returns accounts that contain at least one public view (a view where is_public is true)
        |For each account the API returns the ID and the available views.
        |
        |${authenticationRequiredMessage(false)}
        |""",
      emptyObjectJson,
      basicAccountsJSON,
      List(UserNotLoggedIn,"Could not get accounts.",UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagPublicData))






    lazy val publicAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for all banks
      case "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          for {
            publicAccountsJson <- tryo{bankAccountBasicListToJson(BankAccount.publicAccounts, Empty)} ?~! "Could not get accounts."
          } yield {
            Full(successJsonResponse(publicAccountsJson))
          }
      }
    }




    resourceDocs += ResourceDoc(
      allAccountsAtOneBank,
      apiVersion,
      "allAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts",
      "Get Accounts at one Bank (Public and Private).",
      s"""Get accounts at one bank that the user has access to (Authenticated + Anonymous access).
        |Returns the list of accounts at BANK_ID that the user has access to.
        |For each account the API returns the account ID and the available views.
        |
        |If the user is not authenticated, the list will contain only the accounts providing public views.
        |${authenticationRequiredMessage(false)}
      """,
      emptyObjectJson,
      basicAccountsJSON,
      List(BankNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData)
    )

    lazy val allAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for a single bank (private + public)
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet json => {
        user =>
          for{
            bank <- Bank(bankId) ?~! BankNotFound
          } yield {
            val availableAccounts = bank.accounts(user)
            successJsonResponse(bankAccountBasicListToJson(availableAccounts, user))
          }
      }
    }




    def privateAccountsAtOneBankResult (bank: Bank, u: User) = {
      val availableAccounts = bank.nonPublicAccounts(u)
      successJsonResponse(bankAccountBasicListToJson(availableAccounts, Full(u)))
    }

    def corePrivateAccountsAtOneBankResult (callerContext: CallerContext, codeContext: CodeContext, bank: Bank, u: User) = {
      val availableAccounts = bank.nonPublicAccounts(u)
      successJsonResponse(coreBankAccountListToJson(callerContext, codeContext, availableAccounts, Full(u)))
    }

    resourceDocs += ResourceDoc(
      corePrivateAccountsAtOneBank,
      apiVersion,
      "corePrivateAccountsAtOneBank",
      "GET",
      "/my/banks/BANK_ID/accounts",
      "Get Accounts at Bank (Private)",
      s"""Get private accounts at one bank (Authenticated access).
        |Returns the list of accounts containing private views for the user at BANK_ID.
        |For each account the API returns the ID and label. To also see the list of Views, see privateAccountsAtOneBank
        |
        |
        |This call MAY have an alias /bank/accounts but ONLY if defaultBank is set in Props
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      coreAccountsJSON,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData))

    apiRelations += ApiRelation(corePrivateAccountsAtOneBank, createAccount, "new")
    apiRelations += ApiRelation(corePrivateAccountsAtOneBank, corePrivateAccountsAtOneBank, "self")


    // This contains an approach to surface a resource via different end points in case of a default bank.
    // The second path is experimental
    lazy val corePrivateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // get private accounts for a single bank
      case "my" :: "banks" :: BankId(bankId) :: "accounts" ::  Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)

          } yield {
            corePrivateAccountsAtOneBankResult(CallerContext(corePrivateAccountsAtOneBank), codeContext, bank, u)
          }
      }
      // Also we support accounts/private to maintain compatibility with 1.4.0
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)

          } yield {
            corePrivateAccountsAtOneBankResult(CallerContext(corePrivateAccountsAtOneBank), codeContext, bank, u)
          }
      }
      // Supports idea of default bank
      case "bank" :: "accounts" :: Nil JsonGet json => {
        println("in accounts")
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(BankId(defaultBankId))
          } yield {
            corePrivateAccountsAtOneBankResult(CallerContext(corePrivateAccountsAtOneBank), codeContext, bank, u)
          }
      }

    }


    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      apiVersion,
      "privateAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get private accounts at one bank (Authenticated access).",
      s"""Returns the list of private (non-public) accounts at BANK_ID that the user has access to.
        |For each account the API returns the ID and the available views.
        |
        |If you want to see more information on the Views, use the Account Detail call.
        |If you want less information about the account, use the /my accounts call
        |
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      basicAccountsJSON,
      List(UserNotLoggedIn, BankNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      apiTagAccount :: Nil)

    lazy val privateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! BankNotFound
          } yield {
            val availableAccounts = bank.nonPublicAccounts(u)
            successJsonResponse(bankAccountsListToJson(availableAccounts, Full(u)))
          }
      }
    }






    resourceDocs += ResourceDoc(
      publicAccountsAtOneBank,
      apiVersion,
      "publicAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/public",
      "Get Accounts at Bank (Public)",
      """Returns a list of the public accounts (Anonymous access) at BANK_ID. For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is not required.""",
      emptyObjectJson,
      basicAccountsJSON,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
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
      "/customers/CUSTOMER_ID/kyc_documents",
      "Get KYC Documents for Customer",
      s"""Get KYC (know your customer) documents for a customer
        |Get a list of documents that affirm the identity of the customer
        |Passport, driving licence etc.
        |${authenticationRequiredMessage(false)}""",
      emptyObjectJson,
      kycDocumentsJSON,
      List(UserNotLoggedIn, CustomerNotFoundByCustomerId, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc))

    lazy val getKycDocuments  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerId :: "kyc_documents" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val kycDocuments = KycDocuments.kycDocumentProvider.vend.getKycDocuments(customerId)
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
      "/customers/CUSTOMER_ID/kyc_media",
      "Get KYC Media for a customer",
      s"""Get KYC media (scans, pictures, videos) that affirms the identity of the customer.
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      kycMediasJSON,
      List(UserNotLoggedIn, CustomerNotFoundByCustomerId, UnknownError),
    Catalogs(notCore, notPSD2, notOBWG),
    List(apiTagCustomer, apiTagKyc))

    lazy val getKycMedia  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerId :: "kyc_media" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val kycMedias = KycMedias.kycMediaProvider.vend.getKycMedias(customer.number)
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
      "/customers/CUSTOMER_ID/kyc_checks",
      "Get KYC Checks for current Customer",
      s"""Get KYC checks for the logged in customer
        |Messages sent to the currently authenticated user.
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      kycChecksJSON,
      List(UserNotLoggedIn, CustomerNotFoundByCustomerId, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc))

    lazy val getKycChecks  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerId :: "kyc_checks" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val kycChecks = KycChecks.kycCheckProvider.vend.getKycChecks(customerId)
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
      "/customers/CUSTOMER_ID/kyc_statuses",
      "Get the KYC statuses for a customer",
      s"""Get the KYC statuses for a customer over time
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      kycStatusesJSON,
      List(UserNotLoggedIn, CustomerNotFoundByCustomerId, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc))

    lazy val getKycStatuses  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerId :: "kyc_statuses" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val kycStatuses = KycStatuses.kycStatusProvider.vend.getKycStatuses(customerId)
            val json = JSONFactory200.createKycStatusesJSON(kycStatuses)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getSocialMediaHandles,
      apiVersion,
      "getSocialMediaHandles",
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/social_media_handles",
      "Get social media handles for a customer",
      s"""Get social media handles for a customer.
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      socialMediasJSON,
      List(UserNotLoggedIn, UserHasMissingRoles, CustomerNotFoundByCustomerId, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc))

    lazy val getSocialMediaHandles  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "social_media_handles" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! BankNotFound
            canGetSocialMediaHandles <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanGetSocialMediaHandles), UserHasMissingRoles + CanGetSocialMediaHandles)
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val kycSocialMedias = SocialMediaHandle.socialMediaHandleProvider.vend.getSocialMedias(customer.number)
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
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_documents/KYC_DOCUMENT_ID",
      "Add KYC Document.",
      "Add a KYC document for the customer specified by CUSTOMER_ID. KYC Documents contain the document type (e.g. passport), place of issue, expiry etc. ",
      PostKycDocumentJSON("1234", "passport", "123567", exampleDate, "London", exampleDate),
      kycDocumentJSON,
      List(UserNotLoggedIn, InvalidJsonFormat, InvalidBankIdFormat, BankNotFound, CustomerNotFoundByCustomerId,"Server error: could not add KycDocument", UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc)
    )

    // TODO customerNumber should be in the url but not also in the postedData

    lazy val addKycDocument : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "kyc_documents" :: documentId :: Nil JsonPut json -> _ => {
        // customerNumber is duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[PostKycDocumentJSON]} ?~! ErrorMessages.InvalidJsonFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
            kycDocumentCreated <-
              KycDocuments.kycDocumentProvider.vend.addKycDocuments(
                bankId.value,
                customerId,
                documentId,
                postedData.customer_number,
                postedData.`type`,
                postedData.number,
                postedData.issue_date,
                postedData.issue_place,
                postedData.expiry_date) ?~
              "Server error: could not add KycDocument"
          } yield {
            val json = JSONFactory200.createKycDocumentJSON(kycDocumentCreated)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addKycMedia,
      apiVersion,
      "addKycMedia",
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_media/KYC_MEDIA_ID",
      "Add KYC Media.",
      "Add some KYC media for the customer specified by CUSTOMER_ID. KYC Media resources relate to KYC Documents and KYC Checks and contain media urls for scans of passports, utility bills etc.",
      PostKycMediaJSON(
        "1239879", 
        "image", 
        "http://www.example.com/id-docs/123/image.png",
        exampleDate, 
        "wuwjfuha234678", 
        "98FRd987auhf87jab"
      ),
      kycMediaJSON,
      List(UserNotLoggedIn, InvalidJsonFormat, InvalidBankIdFormat, CustomerNotFoundByCustomerId, ServerAddDataError, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc)
    )

    lazy val addKycMedia : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "kyc_media" :: mediaId :: Nil JsonPut json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[PostKycMediaJSON]} ?~! ErrorMessages.InvalidJsonFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
            kycMediaCreated <- KycMedias.kycMediaProvider.vend.addKycMedias(
                bankId.value,
                customerId,
                mediaId,
                postedData.customer_number,
                postedData.`type`,
                postedData.url,
                postedData.date,
                postedData.relates_to_kyc_document_id,
                postedData.relates_to_kyc_check_id) ?~! ServerAddDataError //TODO Use specific message!
          } yield {
            val json = JSONFactory200.createKycMediaJSON(kycMediaCreated)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addKycCheck,
      apiVersion,
      "addKycCheck",
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_check/KYC_CHECK_ID",
      "Add KYC Check",
      "Add a KYC check for the customer specified by CUSTOMER_ID. KYC Checks store details of checks on a customer made by the KYC team, their comments and a satisfied status.",
      postKycCheckJSON,
      kycCheckJSON,
      List(UserNotLoggedIn, InvalidJsonFormat,InvalidBankIdFormat, BankNotFound, CustomerNotFoundByCustomerId, ServerAddDataError, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc)
    )

    lazy val addKycCheck : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "kyc_check" :: checkId :: Nil JsonPut json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[PostKycCheckJSON]} ?~! ErrorMessages.InvalidJsonFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
            kycCheckCreated <- KycChecks.kycCheckProvider.vend.addKycChecks(
                bankId.value,
                customerId,
                checkId,
                postedData.customer_number,
                postedData.date,
                postedData.how,
                postedData.staff_user_id,
                postedData.staff_name,
                postedData.satisfied,
                postedData.comments) ?~! ServerAddDataError //TODO Use specific message!
          } yield {
            val json = JSONFactory200.createKycCheckJSON(kycCheckCreated)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addKycStatus,
      apiVersion,
      "addKycStatus",
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_statuses",
      "Add KYC Status",
      "Add a kyc_status for the customer specified by CUSTOMER_ID. KYC Status is a timeline of the KYC status of the customer",
      postKycStatusJSON,
      kycStatusJSON,
      List(UserNotLoggedIn, InvalidJsonFormat, InvalidBankIdFormat,UnknownError, BankNotFound ,ServerAddDataError ,CustomerNotFoundByCustomerId),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc)
    )

    lazy val addKycStatus : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "kyc_statuses" :: Nil JsonPut json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[PostKycStatusJSON]} ?~! ErrorMessages.InvalidJsonFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
            kycStatusCreated <- KycStatuses.kycStatusProvider.vend.addKycStatus(
                bankId.value,
                customerId,
                postedData.customer_number,
                postedData.ok,
                postedData.date) ?~! ServerAddDataError //TODO Use specific message!
          } yield {
            val json = JSONFactory200.createKycStatusJSON(kycStatusCreated)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      addSocialMediaHandle,
      apiVersion,
      "addSocialMediaHandle",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/social_media_handles",
      "Add Social Media Handle",
      "Add a social media handle for the customer specified by CUSTOMER_ID.",
      socialMediaJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat, 
        InvalidBankIdFormat, 
        UserHasMissingRoles,
        CustomerNotFoundByCustomerId,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer)
    )

    lazy val addSocialMediaHandle : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "social_media_handles" :: Nil JsonPost json -> _ => {
        // customerNumber is in url and duplicated in postedData. remove from that?
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            postedData <- tryo{json.extract[SocialMediaJSON]} ?~! ErrorMessages.InvalidJsonFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            canAddSocialMediaHandle <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanAddSocialMediaHandle), UserHasMissingRoles + CanAddSocialMediaHandle)
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
            kycSocialMediaCreated <- booleanToBox(
              SocialMediaHandle.socialMediaHandleProvider.vend.addSocialMedias(
                postedData.customer_number,
                postedData.`type`,
                postedData.handle,
                postedData.date_added,
                postedData.date_activated),
              "Server error: could not add")
          } yield {
            successJsonResponse(Extraction.decompose(successMessage), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCoreAccountById,
      apiVersion,
      "getCoreAccountById",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      """Information returned about the account specified by ACCOUNT_ID:
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* IBAN
        |
        |This call returns the owner view and requires access to that view.
        |
        |
        |OAuth authentication is required""",
      emptyObjectJson,
      moderatedCoreAccountJSON,
      List(BankAccountNotFound,UnknownError),
      Catalogs(Core, PSD2, notOBWG),
      apiTagAccount ::  Nil)

    lazy val getCoreAccountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet json => {

        user =>
          // TODO return specific error if bankId == "BANK_ID" or accountID == "ACCOUNT_ID"
          // Should be a generic guard we can use for all calls (also for userId etc.)
          for {
            account <- BankAccount(bankId, accountId) ?~ BankAccountNotFound
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
      "Get Transactions for Account (Core)",
      """Returns transactions list (Core info) of the account specified by ACCOUNT_ID.
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
      moderatedCoreAccountJSON,
      List(BankAccountNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagTransaction))
    
    //Note: we already have the method: getTransactionsForBankAccount in V121.
    //The only difference here is "Core implies 'owner' view" 
    lazy val getCoreTransactionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get transactions
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet json => {
        user =>

          for {
            params <- getTransactionParams(json)
            bankAccount <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
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
      "Get Account by Id (Full)",
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
      moderatedAccountJSON,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  Nil)

    lazy val accountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet json => {
        user =>
          for {
            bank <- Bank(bankId) ?~ BankNotFound // Check bank exists.
            account <- BankAccount(bank.bankId, accountId) ?~ {ErrorMessages.AccountNotFound} // Check Account exists.
            availableViews <- Full(account.permittedViews(user))
            view <- View.fromUrl(viewId, account) ?~! {ErrorMessages.ViewNotFound}
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~! UserNoPermissionAccessView
            moderatedAccount <- account.moderatedBankAccount(view, user)
          } yield {
            val viewsAvailable = availableViews.map(JSONFactory121.createViewJSON).sortBy(_.short_name)
            val moderatedAccountJson = JSONFactory121.createBankAccountJSON(moderatedAccount, viewsAvailable)
            successJsonResponse(Extraction.decompose(moderatedAccountJson))
          }
      }
    }

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
      permissionsJSON,
      List(UserNotLoggedIn, BankNotFound, AccountNotFound ,UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagView, apiTagEntitlement)
    )

    lazy val getPermissionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get access
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn // Check we have a user (rather than error or empty)
            bank <- Bank(bankId) ?~! BankNotFound // Check bank exists.
            account <- BankAccount(bank.bankId, accountId) ?~! {ErrorMessages.AccountNotFound} // Check Account exists.
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
      viewsJSONV121,
      List(UserNotLoggedIn,BankNotFound, AccountNotFound,UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagView, apiTagEntitlement))

    lazy val getPermissionForUserForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn // Check we have a user (rather than error or empty)
            bank <- Bank(bankId) ?~! BankNotFound // Check bank exists.
            account <- BankAccount(bank.bankId, accountId) ?~! {ErrorMessages.AccountNotFound} // Check Account exists.
            permission <- account permission(u, providerId, userId)
          } yield {
            // TODO : Note this is using old createViewsJSON without can_add_counterparty etc.
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
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
        |
        |
        |The User can create an Account for themself or an Account for another User if they have CanCreateAccount role.
        |
        |If USER_ID is not specified the account will be owned by the logged in User.
        |
        |Note: The Amount must be zero.""".stripMargin,
      CreateAccountJSON("A user_id","CURRENT", "Label", AmountOfMoneyJSON121("EUR", "0")),
      coreAccountJSON,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidUserId,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        UserNotFoundById,
        InvalidAccountBalanceAmount,
        InvalidAccountType,
        InvalidAccountInitialBalance,
        InvalidAccountBalanceCurrency,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount)
    )

    apiRelations += ApiRelation(createAccount, createAccount, "self")
    apiRelations += ApiRelation(createAccount, getCoreAccountById, "detail")

    // Note: This doesn't currently work (links only have access to same version resource docs). TODO fix me.
    apiRelations += ApiRelation(createAccount, Implementations1_2_1.updateAccountLabel, "update_label")


    lazy val createAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        user => {

          for {
            loggedInUser <- user ?~! ErrorMessages.UserNotLoggedIn
            jsonBody <- tryo (json.extract[CreateAccountJSON]) ?~! ErrorMessages.InvalidJsonFormat
            user_id <- tryo (if (jsonBody.user_id.nonEmpty) jsonBody.user_id else loggedInUser.userId) ?~! ErrorMessages.InvalidUserId
            isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidAccountIdFormat
            isValidBankId <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            postedOrLoggedInUser <- User.findByUserId(user_id) ?~! ErrorMessages.UserNotFoundById
            bank <- Bank(bankId) ?~! s"Bank $bankId not found"
            // User can create account for self or an account for another user if they have CanCreateAccount role
            isAllowed <- booleanToBox(hasEntitlement(bankId.value, loggedInUser.userId, CanCreateAccount) == true || (user_id == loggedInUser.userId) , s"User must either create account for self or have role $CanCreateAccount")
            initialBalanceAsString <- tryo (jsonBody.balance.amount) ?~! ErrorMessages.InvalidAccountBalanceAmount
            accountType <- tryo(jsonBody.`type`) ?~! ErrorMessages.InvalidAccountType
            accountLabel <- tryo(jsonBody.`type`) //?~! ErrorMessages.InvalidAccountLabel
            initialBalanceAsNumber <- tryo {BigDecimal(initialBalanceAsString)} ?~! ErrorMessages.InvalidAccountInitialBalance
            isTrue <- booleanToBox(0 == initialBalanceAsNumber) ?~! s"Initial balance must be zero"
            currency <- tryo (jsonBody.balance.currency) ?~! ErrorMessages.InvalidAccountBalanceCurrency
            // TODO Since this is a PUT, we should replace the resource if it already exists but will need to check persmissions
            accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty,
              s"Account with id $accountId already exists at bank $bankId")
            bankAccount <- Connector.connector.vend.createSandboxBankAccount(
              bankId, accountId, accountType, 
              accountLabel, currency, initialBalanceAsNumber, 
              postedOrLoggedInUser.name,
              "", //added new field in V220
              "", //added new field in V220
              "" //added new field in V220
            )
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
      "Get Transaction Types offered by the bank",
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
      transactionTypesJsonV200,
      List(BankNotFound, UnknownError),
      Catalogs(Core, PSD2, notOBWG),
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
            bank <- Bank(bankId) ?~! BankNotFound
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
        |In sandbox mode, if the amount is less than 100 (any currency), the transaction request will create a transaction without a challenge, else the Transaction Request will be set to INITIALISED and a challenge will need to be answered.|
        |If a challenge is created you must answer it using Answer Transaction Request Challenge before the Transaction is created.
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
        |${authenticationRequiredMessage(true)}
        |
        |""",
      transactionRequestBodyJsonV200,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        UserNoPermissionAccessView,
        InsufficientAuthorisationToCreateTransactionRequest,
        CounterpartyNotFound,
        InvalidTransactionRequestType,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
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
              u <- user ?~! ErrorMessages.UserNotLoggedIn
              transBodyJson <- tryo{json.extract[TransactionRequestBodyJsonV200]} ?~! InvalidJsonFormat
              transBody <- tryo{getTransactionRequestBodyFromJson(transBodyJson)}
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
              isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! InvalidAccountIdFormat
              fromBank <- Bank(bankId) ?~! BankNotFound
              fromAccount <- BankAccount(bankId, accountId) ?~! AccountNotFound

              availableViews <- Full(fromAccount.permittedViews(user))
              view <- View.fromUrl(viewId, fromAccount) ?~! ViewNotFound
              canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~! UserNoPermissionAccessView

              isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true || hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true , InsufficientAuthorisationToCreateTransactionRequest)
              toBankId <- tryo(BankId(transBodyJson.to.bank_id))
              toAccountId <- tryo(AccountId(transBodyJson.to.account_id))
              toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}
              // Prevent default value for transaction request type (at least).
              // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
              validTransactionRequestTypes <- tryo{Props.get("transactionRequests_supported_types", "")}
              // Use a list instead of a string to avoid partial matches
              validTransactionRequestTypesList <- tryo{validTransactionRequestTypes.split(",")}
              isValidTransactionRequestType <- tryo(assert(transactionRequestType.value != "TRANSACTION_REQUEST_TYPE" && validTransactionRequestTypesList.contains(transactionRequestType.value))) ?~! s"${InvalidTransactionRequestType} : Invalid value is: '${transactionRequestType.value}' Valid values are: ${validTransactionRequestTypes}"
              transferCurrencyEqual <- tryo(assert(transBodyJson.value.currency == fromAccount.currency)) ?~! InvalidTransactionRequestCurrency
              createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv200(u, fromAccount, toAccount, transactionRequestType, transBody)
            } yield {
              // Explicitly format as v2.0.0 json
              val json = JSONFactory200.createTransactionRequestWithChargeJSON(createdTransactionRequest)
              createdJsonResponse(Extraction.decompose(json))
            }
          } else {
            Full(errorJsonResponse(TransactionDisabled))
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
      ChallengeAnswerJSON("89123812", "123345"),
      transactionRequestWithChargeJson,
      List(
        UserNotLoggedIn,
        InvalidAccountIdFormat,
          InvalidBankIdFormat,
        BankNotFound,
        UserNoPermissionAccessView,
        InvalidJsonFormat,
        InvalidTransactionRequestId,
        TransactionRequestTypeHasChanged,
        InvalidTransactionRequesChallengeId,
        TransactionRequestStatusNotInitiated,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val answerTransactionRequestChallenge: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u: User <- user ?~! ErrorMessages.UserNotLoggedIn
              isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidAccountIdFormat
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
              fromBank <- Bank(bankId) ?~! BankNotFound
              fromAccount <- BankAccount(bankId, accountId) ?~! BankNotFound
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~! UserNoPermissionAccessView


              // Note: These checks are not in the ideal order. See version 2.1.0 which supercedes this

              answerJson <- tryo{json.extract[ChallengeAnswerJSON]} ?~! InvalidJsonFormat
              answerOk <- Connector.connector.vend.answerTransactionRequestChallenge(transReqId, answerJson.answer)
              //check the transReqId validation.
              existingTransactionRequest <- Connector.connector.vend.getTransactionRequestImpl(transReqId) ?~! {ErrorMessages.InvalidTransactionRequestId}

              //check the input transactionRequestType is same as when the user create the existingTransactionRequest
              existingTransactionRequestType <- Full(existingTransactionRequest.`type`)
              isSameTransReqType <- booleanToBox(existingTransactionRequestType.equals(transactionRequestType.value),s"${ErrorMessages.TransactionRequestTypeHasChanged} It should be :'$existingTransactionRequestType' ")

              //check the challenge id is same as when the user create the existingTransactionRequest
              isSameChallengeId <- booleanToBox(existingTransactionRequest.challenge.id.equals(answerJson.id),{ErrorMessages.InvalidTransactionRequesChallengeId})

              //check the challenge statue whether is initiated, only retreive INITIATED transaction requests.
              isTransReqStatueInitiated <- booleanToBox(existingTransactionRequest.status.equals("INITIATED"),ErrorMessages.TransactionRequestStatusNotInitiated)

              //create transaction and insert its id into the transaction request
              transactionRequest <- Connector.connector.vend.createTransactionAfterChallengev200(u, transReqId, transactionRequestType)
            } yield {

              // Format explicitly as v2.0.0 json
              val json = JSONFactory200.createTransactionRequestWithChargeJSON(transactionRequest)
              //successJsonResponse(Extraction.decompose(json))

              val successJson = Extraction.decompose(json)
              createdJsonResponse(successJson)
            }
          } else {
            Full(errorJsonResponse(TransactionDisabled))
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
      transactionRequestWithChargesJson,
      List(UserNotLoggedIn, BankNotFound, AccountNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val getTransactionRequests: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~! UserNotLoggedIn
              fromBank <- Bank(bankId) ?~! BankNotFound
              fromAccount <- BankAccount(bankId, accountId) ?~! AccountNotFound
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~! UserNoPermissionAccessView
              transactionRequests <- Connector.connector.vend.getTransactionRequests(u, fromAccount)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createTransactionRequestJSONs(transactionRequests)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse(TransactionDisabled))
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
      createUserJson,
      userJSONV200,
      List(UserNotLoggedIn, InvalidJsonFormat, InvalidStrongPasswordFormat ,"Error occurred during user creation.", "User with the same username already exists." , UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagOnboarding, apiTagUser))

    lazy val createUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: Nil JsonPost json -> _ => {
        user =>
          for {
            postedData <- tryo {json.extract[CreateUserJson]} ?~! ErrorMessages.InvalidJsonFormat
            isValidStrongPassword <- tryo(assert(isValidStrongPassword(postedData.password))) ?~! ErrorMessages.InvalidStrongPasswordFormat
          } yield {
            if (AuthUser.find(By(AuthUser.username, postedData.username)).isEmpty) {
              val userCreated = AuthUser.create
                .firstName(postedData.first_name)
                .lastName(postedData.last_name)
                .username(postedData.username)
                .email(postedData.email)
                .password(postedData.password)
                .validated(true) // TODO Get this from Props
              if(userCreated.validate.size > 0){
                Full(errorJsonResponse(userCreated.validate.map(_.msg).mkString(";")))
              }
              else
              {
                userCreated.saveMe()
                if (userCreated.saved_?) {
                  val json = JSONFactory200.createUserJSONfromAuthUser(userCreated)
                  successJsonResponse(Extraction.decompose(json), 201)
                }
                else
                  Full(errorJsonResponse("Error occurred during user creation."))
              }
            }
            else {
              Full(errorJsonResponse("User with the same username already exists.", 409))
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
      "Create Meeting (video conference/call)",
      """Create Meeting: Initiate a video conference/call with the bank.
        |
        |The Meetings resource contains meta data about video/other conference sessions, not the video/audio/chat itself.
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
      CreateMeetingJson("tokbox", "onboarding"),
      meetingJson,
      List(
        UserNotLoggedIn,
        MeetingApiKeyNotConfigured,
        MeetingApiSecretNotConfigured,
        InvalidBankIdFormat,
        BankNotFound,
        InvalidJsonFormat,
        MeetingsNotSupported,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))


    lazy val createMeeting: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("meeting.tokbox_enabled", false)) {
            for {
              // TODO use these keys to get session and tokens from tokbox
              providerApiKey <- Props.get("meeting.tokbox_api_key") ~> APIFailure(MeetingApiKeyNotConfigured, 403)
              providerSecret <- Props.get("meeting.tokbox_api_secret") ~> APIFailure(MeetingApiSecretNotConfigured, 403)
              u <- user ?~! UserNotLoggedIn
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
              bank <- Bank(bankId) ?~! BankNotFound
              postedData <- tryo {json.extract[CreateMeetingJson]} ?~! InvalidJsonFormat
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
            Full(errorJsonResponse(MeetingsNotSupported))
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
      meetingsJson,
      List(
        UserNotLoggedIn,
        MeetingApiKeyNotConfigured,
        MeetingApiSecretNotConfigured,
        BankNotFound,
        MeetingsNotSupported,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))


    lazy val getMeetings: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("meeting.tokbox_enabled", false)) {
            for {
              u <- user ?~! ErrorMessages.UserNotLoggedIn
              fromBank <- Bank(bankId) ?~! BankNotFound
              providerApiKey <- Props.get("meeting.tokbox_api_key") ~> APIFailure(ErrorMessages.MeetingApiKeyNotConfigured, 403)
              providerSecret <- Props.get("meeting.tokbox_api_secret") ~> APIFailure(ErrorMessages.MeetingApiSecretNotConfigured, 403)
              u <- user ?~! ErrorMessages.UserNotLoggedIn
              bank <- Bank(bankId) ?~! BankNotFound
              // now = Calendar.getInstance().getTime()
              meetings <- Meeting.meetingProvider.vend.getMeetings(bank.bankId, u)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createMeetingJSONs(meetings)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse(MeetingsNotSupported))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getMeeting,
      apiVersion,
      "getMeeting",
      "GET",
      "/banks/BANK_ID/meetings/MEETING_ID",
      "Get Meeting",
      """Get Meeting specified by BANK_ID / MEETING_ID
        |Meetings contain meta data about, and are used to facilitate, video conferences / chats etc.
        |
        |The actual conference/chats are handled by external services.
        |
        |Login is required.
        |
        |This call is **experimental** and will require further authorisation in the future.
      """.stripMargin,
      emptyObjectJson,
      meetingJson,
      List(
        UserNotLoggedIn, 
        BankNotFound, 
        MeetingApiKeyNotConfigured,
        MeetingApiSecretNotConfigured, 
        MeetingNotFound, 
        MeetingsNotSupported,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))


    lazy val getMeeting: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "meetings" :: meetingId :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("meeting.tokbox_enabled", false)) {
            for {
              u <- user ?~! UserNotLoggedIn
              fromBank <- Bank(bankId) ?~! BankNotFound
              providerApiKey <- Props.get("meeting.tokbox_api_key") ~> APIFailure(ErrorMessages.MeetingApiKeyNotConfigured, 403)
              providerSecret <- Props.get("meeting.tokbox_api_secret") ~> APIFailure(ErrorMessages.MeetingApiSecretNotConfigured, 403)
              bank <- Bank(bankId) ?~! BankNotFound
              meeting <- Meeting.meetingProvider.vend.getMeeting(bank.bankId, u, meetingId)  ?~! {ErrorMessages.MeetingNotFound}
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


    resourceDocs += ResourceDoc(
      createCustomer,
      apiVersion,
      "createCustomer",
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer.",
      s"""Add a customer linked to the user specified by user_id
        |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
        |This call may require additional permissions/role in the future.
        |For now the authenticated user can create at most one linked customer.
        |Dates need to be in the format 2013-01-21T23:08:00Z
        |${authenticationRequiredMessage(true)}
        |""",
      createCustomerJson,
      customerJsonV140,
      List(
        InvalidBankIdFormat,
        UserNotLoggedIn,
        BankNotFound,
        CustomerNumberAlreadyExists,
        UserHasMissingRoles,
        UserNotFoundById,
        CreateConsumerError,
        CustomerAlreadyExistsForUser,
        CreateUserCustomerLinksError,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagCustomer))



    // TODO
    // Separate customer creation (keep here) from customer linking (remove from here)
    // Remove user_id from CreateCustomerJson
    // Logged in user must have CanCreateCustomer (should no longer be able create customer for own user)
    // Add ApiLink to createUserCustomerLink

    lazy val createCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn// TODO. CHECK user has role to create a customer / create a customer for another user id.
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            postedData <- tryo{json.extract[CreateCustomerJson]} ?~! ErrorMessages.InvalidJsonFormat
            requiredEntitlements = CanCreateCustomer ::
                                   CanCreateUserCustomerLink ::
                                   Nil
            requiredEntitlementsTxt = requiredEntitlements.mkString(" and ")
            hasEntitlements <- booleanToBox(hasAllEntitlements(bankId.value, u.userId, requiredEntitlements), UserHasMissingRoles + requiredEntitlementsTxt)
            checkAvailable <- tryo(assert(Customer.customerProvider.vend.checkCustomerNumberAvailable(bankId, postedData.customer_number) == true)) ?~! ErrorMessages.CustomerNumberAlreadyExists
            user_id <- tryo (if (postedData.user_id.nonEmpty) postedData.user_id else u.userId) ?~! s"Problem getting user_id"
            customer_user <- User.findByUserId(user_id) ?~! ErrorMessages.UserNotFoundById
            customer <- Customer.customerProvider.vend.addCustomer(bankId,
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
              postedData.last_ok_date,
              None,
              None) ?~! CreateConsumerError
            userCustomerLink <- booleanToBox(UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(user_id, customer.customerId).isEmpty == true) ?~! ErrorMessages.CustomerAlreadyExistsForUser
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(user_id, customer.customerId, exampleDate, true) ?~! CreateUserCustomerLinksError
          } yield {
            val json = JSONFactory1_4_0.createCustomerJson(customer)
            val successJson = Extraction.decompose(json)
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
      "Get User (Current)",
      """Get the logged in user
        |
        |Login is required.
      """.stripMargin,
      emptyObjectJson,
      userJSONV200,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getCurrentUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "current" :: Nil JsonGet _ => {
        user =>
            for {
              u <- user ?~! ErrorMessages.UserNotLoggedIn
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
      "Get Users by Email Address",
      """Get users by email address
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJSONV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: userEmail :: Nil JsonGet _ => {
        user =>
            for {
              l <- user ?~! ErrorMessages.UserNotLoggedIn
              canGetAnyUser <- booleanToBox(hasEntitlement("", l.userId, ApiRole.CanGetAnyUser), UserHasMissingRoles + CanGetAnyUser )
              // Workaround to get userEmail address directly from URI without needing to URL-encode it
              users <- tryo{AuthUser.getResourceUsersByEmail(CurrentReq.value.uri.split("/").last)} ?~! {ErrorMessages.UserNotFoundByEmail}
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory200.createUserJSONs(users)
                successJsonResponse(Extraction.decompose(json))
              }
      }
    }



    // createUserCustomerLinks
    val createUserCustomerLinksEntitlementsRequiredForSpecificBank = CanCreateUserCustomerLink :: Nil
    val createUserCustomerLinksEntitlementsRequiredForAnyBank = CanCreateUserCustomerLinkAtAnyBank :: Nil
    val createUserCustomerLinksrequiredEntitlementsText = createUserCustomerLinksEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createUserCustomerLinksEntitlementsRequiredForAnyBank.mkString(" and ") + " entitlements are required."

    resourceDocs += ResourceDoc(
      createUserCustomerLinks,
      apiVersion,
      "createUserCustomerLinks",
      "POST",
      "/banks/BANK_ID/user_customer_links",
      "Create User Customer Link.",
      s"""Link a User to a Customer
        |
        |${authenticationRequiredMessage(true)}
        |
        |$createUserCustomerLinksrequiredEntitlementsText
        |""",
      createUserCustomerLinkJson,
      userCustomerLinkJson,
      List(
        UserNotLoggedIn,
        InvalidBankIdFormat, 
        BankNotFound, 
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId, 
        UserHasMissingRoles,
        CustomerAlreadyExistsForUser, 
        CreateUserCustomerLinksError,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagCustomer))

    // TODO
    // Allow multiple UserCustomerLinks per user (and bank)

    lazy val createUserCustomerLinks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId):: "user_customer_links" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            postedData <- tryo{json.extract[CreateUserCustomerLinkJson]} ?~! ErrorMessages.InvalidJsonFormat
            user_id <- booleanToBox(postedData.user_id.nonEmpty) ?~! "Field user_id is not defined in the posted json!"
            user <- User.findByUserId(postedData.user_id) ?~! ErrorMessages.UserNotFoundById
            customer_id <- booleanToBox(postedData.customer_id.nonEmpty) ?~! "Field customer_id is not defined in the posted json!"
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(postedData.customer_id) ?~! ErrorMessages.CustomerNotFoundByCustomerId
            hasEntitlements <- booleanToBox(hasAllEntitlements(bankId.value, u.userId, createUserCustomerLinksEntitlementsRequiredForSpecificBank) ||
                                            hasAllEntitlements("", u.userId, createUserCustomerLinksEntitlementsRequiredForAnyBank),
                                            s"$createUserCustomerLinksrequiredEntitlementsText")
            _ <- booleanToBox(customer.bank == bank.bankId.value, "Bank of the customer specified by the CUSTOMER_ID has to matches BANK_ID")
            _ <- booleanToBox(UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(postedData.user_id, postedData.customer_id).isEmpty == true) ?~! CustomerAlreadyExistsForUser
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(postedData.user_id, postedData.customer_id, new Date(), true) ?~! CreateUserCustomerLinksError
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
      "Add Entitlement for a User.",
      """Create Entitlement. Grant Role to User.
        |
        |Entitlements are used to grant System or Bank level roles to Users. (For Account level privileges, see Views)
        |
        |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
        |
        |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
        |
        |Authentication is required and the user needs to be a Super Admin. Super Admins are listed in the Props file.""",
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createEntitlementJSON,
      entitlementJSON,
      List(
        UserNotLoggedIn,
        UserNotFoundById, 
        InvalidInputJsonFormat,
        IncorrectRoleName,
        EntitlementIsBankRole, 
        EntitlementIsSystemRole, 
        "Entitlement already exists for the user.",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser))

    lazy val addEntitlement : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add access for specific user to a list of views
      case "users" :: userId :: "entitlements" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- User.findByUserId(userId) ?~! ErrorMessages.UserNotFoundById
            postedData <- tryo{json.extract[CreateEntitlementJSON]} ?~! InvalidInputJsonFormat
            _ <- tryo{valueOf(postedData.role_name)} ?~! IncorrectRoleName
            _ <- booleanToBox(ApiRole.valueOf(postedData.role_name).requiresBankId == postedData.bank_id.nonEmpty) ?~!
              {if (ApiRole.valueOf(postedData.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole}
            allowedEntitlements = CanCreateEntitlementAtOneBank ::
                                  CanCreateEntitlementAtAnyBank ::
                                  Nil
            _ <- booleanToBox(isSuperAdmin(u.userId) || hasAtLeastOneEntitlement(postedData.bank_id, u.userId, allowedEntitlements) == true) ?~! {"Logged user is not super admin or does not have entitlements: " + allowedEntitlements.mkString(", ") + "!"}
            _ <- booleanToBox(postedData.bank_id.nonEmpty == false || Bank(BankId(postedData.bank_id)).isEmpty == false) ?~! BankNotFound
            role <- tryo{valueOf(postedData.role_name)} ?~! "wrong role name"
            _ <- booleanToBox(hasEntitlement(postedData.bank_id, userId, role) == false, "Entitlement already exists for the user." )
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
      entitlementJSONs,
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagUser, apiTagEntitlement))


    lazy val getEntitlements: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        user =>
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              _ <- booleanToBox(hasEntitlement("", u.userId, CanGetEntitlementsForAnyUserAtAnyBank), UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank)
              _ <- Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
              u <- user ?~! ErrorMessages.UserNotLoggedIn
              _ <- booleanToBox(hasEntitlement("", u.userId, CanGetEntitlementsForAnyUserAtAnyBank), UserHasMissingRoles + CanGetEntitlementsForAnyUserAtAnyBank )
              entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
            }
            yield {
              var json = EntitlementJSONs(Nil)
              // Format the data as V2.0.0 json
              if (isSuperAdmin(userId)) {
                // If the user is SuperAdmin add it to the list
                json = EntitlementJSONs(JSONFactory200.createEntitlementJSONs(entitlements).list:::List(EntitlementJSON("", "SuperAdmin", "")))
                successJsonResponse(Extraction.decompose(json))
              } else {
                json = JSONFactory200.createEntitlementJSONs(entitlements)
              }
              // Return
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
      "Delete Entitlement",
      """Delete Entitlement specified by ENTITLEMENT_ID for an user specified by USER_ID
        |
        |Authentication is required and the user needs to be a Super Admin.
        |Super Admins are listed in the Props file.
        |
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn, "User is not super admin!", "EntitlementId not found", UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagUser, apiTagEntitlement))


    lazy val deleteEntitlement: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: userId :: "entitlement" :: entitlementId :: Nil JsonDelete _ => {
        user =>
            for {
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              _ <- booleanToBox(isSuperAdmin(u.userId)) ?~ "User is not super admin!"
              entitlement <- tryo{Entitlement.entitlement.vend.getEntitlementById(entitlementId)} ?~ "EntitlementId not found"
              _ <- Entitlement.entitlement.vend.deleteEntitlement(entitlement)
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
      entitlementJSONs,
      List(UserNotLoggedIn, "Logged user is not super admin!", UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagUser, apiTagEntitlement))


    lazy val getAllEntitlements: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "entitlements" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(isSuperAdmin(u.userId)) ?~! "Logged user is not super admin!"
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
        emptyObjectJson, //TODO what is output here?
        List(UserNotLoggedIn, BankNotFound, UserHasMissingRoles, UnknownError),
        Catalogs(notCore, notPSD2, notOBWG),
        List())

    val esw = new elasticsearchWarehouse
    lazy val elasticSearchWarehouse: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "search" :: "warehouse" :: queryString :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- Entitlement.entitlement.vend.getEntitlement("", u.userId, ApiRole.CanSearchWarehouse.toString) ?~! {UserHasMissingRoles + CanSearchWarehouse}
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
        "Search API Metrics via Elasticsearch.",
        """
          |Search the API calls made to this API instance via Elastic Search.
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
        List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
        Catalogs(notCore, notPSD2, notOBWG),
        List())

    val esm = new elasticsearchMetrics
    lazy val elasticSearchMetrics: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "search" :: "metrics" :: queryString :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- Entitlement.entitlement.vend.getEntitlement("", u.userId, ApiRole.CanSearchMetrics.toString) ?~! {UserHasMissingRoles + CanSearchMetrics}
          } yield {
            successJsonResponse(Extraction.decompose(esm.searchProxy(u.userId, queryString)))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCustomers,
      apiVersion,
      "getCustomers",
      "GET",
      "/users/current/customers",
      "Get all customers for logged in user",
      """Information about the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      customersJsonV140,
      List(UserNotLoggedIn, CustomerDoNotExistsForUser, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagCustomer))

    lazy val getCustomers : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            //bank <- Bank(bankId) ?~! BankNotFound
            customerIds: List[String] <- tryo{UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(u.userId).map(x=>x.customerId)} ?~! CustomerDoNotExistsForUser
          } yield {
            val json = JSONFactory1_4_0.createCustomersJson(APIUtil.getCustomers(customerIds))
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }
  }
}

object APIMethods200 {
}
