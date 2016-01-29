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
import code.socialmedia.{SocialMedias, SocialMedia}

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
      emptyObjectJson :: Nil)

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
      emptyObjectJson :: Nil)

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
      emptyObjectJson :: Nil)

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
      emptyObjectJson :: Nil)

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
      emptyObjectJson :: Nil)


    def privateAccountsAtOneBankResult (bank: Bank, u: User) = {
      val availableAccounts = bank.nonPublicAccounts(u)
      successJsonResponse(bankAccountBasicListToJson(availableAccounts, Full(u)))
    }

    // This contains an approach to surface the same resource via different end point in case of "one" bank.
    // Experimental and might be removed.
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
      emptyObjectJson :: Nil)

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
      "/customers/CUSTOMER_NUMBER/kyc_document",
      "Get documents for the logged in customer",
      """Messages sent to the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val getKycDocuments  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_document" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
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
      getKycMedias,
      apiVersion,
      "getKycMedias",
      "GET",
      "/customers/CUSTOMER_NUMBER/kyc_media",
      "Get medias for the logged in customer",
      """Messages sent to the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val getKycMedias  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_media" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
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
      getKycCheck,
      apiVersion,
      "getKycCheck",
      "GET",
      "/customers/CUSTOMER_NUMBER/kyc_check",
      "Get checks for the logged in customer",
      """Messages sent to the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val getKycCheck  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_check" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
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
      "/customers/CUSTOMER_NUMBER/kyc_status",
      "Get statuses for the logged in customer",
      """Messages sent to the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val getKycStatuses  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "kyc_status" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
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
      getSocialMedia,
      apiVersion,
      "getSocialMedia",
      "GET",
      "/customers/CUSTOMER_NUMBER/social_media",
      "Get social medias for the logged in customer",
      """Messages sent to the currently authenticated user.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil)

    lazy val getSocialMedia  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "customers" :: customerNumber :: "social_media" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
            cNumber <- tryo(customerNumber) ?~! {"Unknown customer"}
          } yield {
            val kycSocialMedias = SocialMedias.socialMediaProvider.vend.getSocialMedias(cNumber)
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
      "/banks/BANK_ID/customer/kyc_document",
      "Add a kyc_document for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycDocumentJSON("wuwjfuha234678", "1234", "passport", "123567", exampleDate, "London", exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil
    )

    lazy val addKycDocument : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "kyc_document" :: Nil JsonPost json -> _ => {
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
              "Server error: could not add message")
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
      "/banks/BANK_ID/customer/kyc_media",
      "Add a kyc_media for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycMediaJSON("73hyfgayt6ywerwerasd", "1239879", "image", "http://www.example.com/id-docs/123/image.png", exampleDate, "wuwjfuha234678", "98FRd987auhf87jab")),
      emptyObjectJson,
      emptyObjectJson :: Nil
    )

    lazy val addKycMedia : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "kyc_media" :: Nil JsonPost json -> _ => {
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
      "/banks/BANK_ID/customer/kyc_check",
      "Add a kyc_check for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycCheckJSON("98FRd987auhf87jab", "1239879", exampleDate, "online_meeting", "67876", "Simon Redfern", true, "")),
      emptyObjectJson,
      emptyObjectJson :: Nil
    )

    lazy val addKycCheck : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "kyc_check" :: Nil JsonPost json -> _ => {
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
      "/banks/BANK_ID/customer/kyc_status",
      "Add a kyc_status for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(KycStatusJSON("8762893876", true, exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil
    )

    lazy val addKycStatus : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "kyc_status" :: Nil JsonPost json -> _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to post Document"
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
      addSocialMedia,
      apiVersion,
      "addSocialMedia",
      "POST",
      "/banks/BANK_ID/customer/social_media",
      "Add a social_media for the customer specified by CUSTOMER_NUMBER",
      "",
      Extraction.decompose(SocialMediaJSON("8762893876", "twitter", "susan@example.com",  exampleDate, exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil
    )

    lazy val addSocialMedia : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "social_media" :: Nil JsonPost json -> _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to post Document"
            postedData <- tryo{json.extract[SocialMediaJSON]} ?~! "Incorrect json format"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, postedData.customer_number) ?~! "Customer not found"
            kycSocialMediaCreated <- booleanToBox(
              SocialMedias.socialMediaProvider.vend.addSocialMedias(
                postedData.customer_number,
                postedData.`type`,
                postedData.handle,
                postedData.date_added,
                postedData.date_activated),
              "Server error: could not add message")
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
