package code.api.v2_0_0

import java.text.SimpleDateFormat

import code.api.util.APIUtil


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

  // New 2.0.0
  private def bankAccountBasicListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(bankAccountBasicList(bankAccounts, user))
  }

  private def bankAccountBasicList(bankAccounts: List[BankAccount], user : Box[User]): List[AccountBasicJSON] = {
    val accJson : List[AccountBasicJSON] = bankAccounts.map( account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[ViewBasicJSON] =
        views.map( v => {
          JSONFactory.createViewBasicJSON(v)
        })
      JSONFactory.createAccountBasicJSON(account,viewsAvailable)
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
      "/accounts/private",
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

    lazy val privateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for all banks
      case "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
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
      "/banks/BANK_ID/accounts/private",
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

    // This contains an approach to surface the same resource via different end point in case of "one" bank.
    // The "one" path is experimental and might be removed.
    lazy val privateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            bank <- Bank(bankId)
          } yield {
            privateAccountsAtOneBankResult(bank, u)
          }
      }
      case "one" :: "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            bank <- Bank(BankId("abc"))
          } yield {
            privateAccountsAtOneBankResult(bank, u)
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
            u <- user ?~! "User must be logged in"
            cNumber <- tryo(customerNumber) ?~! {"Unknown customer"}
          } yield {
            val kycDocuments = KycDocuments.kycDocumentProvider.vend.getKycDocuments(cNumber)
            val json = JSONFactory.createKycDocumentsJSON(kycDocuments)
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
            u <- user ?~! "User must be logged in"
            cNumber <- tryo(customerNumber) ?~! {"Unknown customer"}
          } yield {
            val kycMedias = KycMedias.kycMediaProvider.vend.getKycMedias(cNumber)
            val json = JSONFactory.createKycMediasJSON(kycMedias)
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
            u <- user ?~! "User must be logged in."
            cNumber <- tryo(customerNumber) ?~! {"Unknown customer"}
          } yield {
            val kycChecks = KycChecks.kycCheckProvider.vend.getKycChecks(cNumber)
            val json = JSONFactory.createKycChecksJSON(kycChecks)
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
            u <- user ?~! "User must be logged in"
            cNumber <- tryo(customerNumber) ?~! {"Unknown customer"}
          } yield {
            val kycStatuses = KycStatuses.kycStatusProvider.vend.getKycStatuses(cNumber)
            val json = JSONFactory.createKycStatusesJSON(kycStatuses)
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
            u <- user ?~! "User must be logged in"
            cNumber <- tryo(customerNumber) ?~! {"Unknown customer"}
          } yield {
            val kycSocialMedias = SocialMediaHandle.socialMediaHandleProvider.vend.getSocialMedias(cNumber)
            val json = JSONFactory.createSocialMediasJSON(kycSocialMedias)
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
            u <- user ?~! "User must be logged in to post Document"
            postedData <- tryo{json.extract[KycDocumentJSON]} ?~! "Incorrect json format"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! "Customer not found"
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
            u <- user ?~! "User must be logged in to post Document"
            postedData <- tryo{json.extract[KycMediaJSON]} ?~! "Incorrect json format"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! "Customer not found"
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
            u <- user ?~! "User must be logged in to post Document"
            postedData <- tryo{json.extract[KycCheckJSON]} ?~! "Incorrect json format"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! "Customer not found"
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
            u <- user ?~! "User must be logged"
            postedData <- tryo{json.extract[KycStatusJSON]} ?~! "Incorrect json format"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! "Customer not found"
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
            u <- user ?~! "User must be logged in"
            postedData <- tryo{json.extract[SocialMediaJSON]} ?~! "Incorrect json format"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! "Customer not found"
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



  }
}

object APIMethods200 {
}
