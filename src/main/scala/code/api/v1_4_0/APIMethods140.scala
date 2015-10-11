package code.api.v1_4_0

import java.text.SimpleDateFormat
import java.util.Date

import code.bankconnectors.Connector
import code.transactionrequests.TransactionRequests.{TransactionRequestBody, TransactionRequestAccount}
import net.liftweb.common.{Failure, Loggable, Box, Full}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{ShortTypeHints, DefaultFormats, Extraction}
import net.liftweb.json.JsonAST.{JField, JObject, JValue}
import net.liftweb.util.Helpers.tryo
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Props
import net.liftweb.json.JsonAST.JValue

import scala.collection.immutable.Nil

// JObject creation
import collection.mutable.ArrayBuffer

import code.api.APIFailure
import code.api.v1_2_1.APIMethods121
import code.api.v1_3_0.APIMethods130
import code.api.v1_4_0.JSONFactory1_4_0._
import code.atms.Atms
import code.branches.Branches
import code.crm.CrmEvent
import code.customer.{MockCustomerFaceImage, CustomerMessages, Customer}
import code.model._
import code.products.Products
import code.api.util.APIUtil._
import code.util.Helper._
import code.api.util.APIUtil.ResourceDoc
import java.text.SimpleDateFormat

trait APIMethods140 extends Loggable with APIMethods130 with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: RestHelper =>

  val Implementations1_4_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "1_4_0"

    val exampleDateString : String ="22/08/2013"
    val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)


    def getResourceDocsList : Option[List[ResourceDoc]] =
    {
      // Get the Resource Docs for this and previous versions of the API
      val cumulativeResourceDocs = resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
      // Sort by endpoint, verb. Thus / is shown first then /accounts and /banks etc. Seems to read quite well like that.
      Some(cumulativeResourceDocs.toList.sortBy(rd => (rd.requestUrl, rd.requestVerb)))
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "getCustomer",
      "GET",
      "/banks/BANK_ID/customer",
      "Get customer for logged in user",
      """Information about the currently authenticated user.
      |
      |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson)

    lazy val getCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve Customer"
            //TODO: whats wrong with this?? (tests fail)
            //bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            info <- Customer.customerProvider.vend.getCustomer(bankId, u) ?~ "No customer information found for current user"
          } yield {
            val json = JSONFactory1_4_0.createCustomerJson(info)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "getCustomerMessages",
      "GET",
      "/banks/BANK_ID/customer/messages",
      "Get messages for the logged in customer",
      """Messages sent to the currently authenticated user.

Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson)

    lazy val getCustomerMessages  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "messages" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
            //bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            //au <- APIUser.find(By(APIUser.id, u.apiId))
            //role <- au.isCustomerMessageAdmin ~> APIFailure("User does not have sufficient permissions", 401)
          } yield {
            val messages = CustomerMessages.customerMessageProvider.vend.getMessages(u, bankId)
            val json = JSONFactory1_4_0.createCustomerMessagesJson(messages)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "addCustomerMessage",
      "POST",
      "/banks/BANK_ID/customer/CUSTOMER_NUMBER/messages",
      "Add a message for the customer specified by CUSTOMER_NUMBER",
      "",
      // We use Extraction.decompose to convert to json
      Extraction.decompose(AddCustomerMessageJson("message to send", "from department", "from person")),
      emptyObjectJson
    )

    lazy val addCustomerMessage : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: customerNumber ::  "messages" :: Nil JsonPost json -> _ => {
        user => {
          for {
            postedData <- tryo{json.extract[AddCustomerMessageJson]} ?~! "Incorrect json format"
            //bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- Customer.customerProvider.vend.getUser(bankId, customerNumber) ?~! "No customer found"
            messageCreated <- booleanToBox(
              CustomerMessages.customerMessageProvider.vend.addMessage(
                customer, bankId, postedData.message, postedData.from_department, postedData.from_person),
              "Server error: could not add message")
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "getBranches",
      "GET",
      "/banks/BANK_ID/branches",
      "Get branches for the bank",
      """Returns information about branches for a single bank specified by BANK_ID including:
        |
        |* Name
        |* Address
        |* Geo Location
        |* License the data under this endpoint is released under
        |
        |Authentication via OAuth *may* be required.""",
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getBranches : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve Branches data"
            // bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            // Get branches from the active provider
            branches <- Box(Branches.branchesProvider.vend.getBranches(bankId)) ~> APIFailure("No branches available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createBranchesJson(branches)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }



    resourceDocs += ResourceDoc(
      apiVersion,
      "getAtms",
      "GET",
      "/banks/BANK_ID/atms",
      "Get ATMS for the bank",
      """Returns information about ATMs for a single bank specified by BANK_ID including:

* Address
* Geo Location
* License the data under this endpoint is released under

Authentication via OAuth *may* be required.""",
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getAtms : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet _ => {
        user => {
          for {
          // Get atms from the active provider
            u <- user ?~! "User must be logged in to retrieve ATM data"
            //bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            atms <- Box(Atms.atmsProvider.vend.getAtms(bankId)) ~> APIFailure("No ATMs available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createAtmsJson(atms)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


    resourceDocs += ResourceDoc(
      apiVersion,
      "getProducts",
      "GET",
      "/banks/BANK_ID/products",
      "Get products offered by the bank",
      """Returns information about financial products offered by a bank specified by BANK_ID including:
        |
        |* Name
        |* Code
        |* Category
        |* Family
        |* Super Family
        |* More info URL
        |* Description
        |* Terms and Conditions
        |* License the data under this endpoint is released under""",
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getProducts : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet _ => {
        user => {
          for {
          // Get products from the active provider
            u <- user ?~! "User must be logged in to retrieve Products data"
            //bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            products <- Box(Products.productsProvider.vend.getProducts(bankId)) ~> APIFailure("No products available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createProductsJson(products)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


    resourceDocs += ResourceDoc(
      apiVersion,
      "getCrmEvents",
      "GET",
      "/banks/BANK_ID/crm-events",
      "Get CRM Events for the logged in user",
      "",
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getCrmEvents : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "crm-events" :: Nil JsonGet _ => {
        user => {
          for {
            // Get crm events from the active provider
            u <- user ?~! "User must be logged in to retrieve CRM Event information"
            //bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            crmEvents <- Box(CrmEvent.crmEventProvider.vend.getCrmEvents(bankId)) ~> APIFailure("No CRM Events available.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createCrmEventsJson(crmEvents)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "getResourceDocs",
      "GET",
      "/resource-docs/obp",
      "Get Resource Documentation in OBP format.",
      "Returns documentation about the resources on this server including example body for POST or PUT requests.",
      emptyObjectJson,
      emptyObjectJson
    )

    // Provides resource documents so that live docs (currently on Sofi) can display API documentation
    lazy val getResourceDocs : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: "obp" :: Nil JsonGet _ => {
        user => {
          for {
            rd <- getResourceDocsList
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createResourceDocsJson(rd)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    /*
     transaction requests (new payments since 1.4.0)
    */

    resourceDocs += ResourceDoc(
      apiVersion,
      "getTransactionRequestTypes",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types",
      "Get supported Transaction Request types.",
      "",
      emptyObjectJson,
      emptyObjectJson)

    lazy val getTransactionRequestTypes: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
          Nil JsonGet _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              fromBank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~ {"Unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              transactionRequestTypes <- Connector.connector.vend.getTransactionRequestTypes(u, fromAccount)
            } yield {
                val successJson = Extraction.decompose(transactionRequestTypes)
                successJsonResponse(successJson)
              }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "getTransactionRequests",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get all Transaction Requests.",
      "",
      emptyObjectJson,
      emptyObjectJson)

    lazy val getTransactionRequests: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              fromBank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~! {"Unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              transactionRequests <- Connector.connector.vend.getTransactionRequests(u, fromAccount)
            }
            yield {
              val successJson = Extraction.decompose(transactionRequests)
              successJsonResponse(successJson)
            }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }



    case class TransactionIdJson(transaction_id : String)

    resourceDocs += ResourceDoc(
      apiVersion,
      "createTransactionRequest",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests",
      "Create Transaction Request.",
      "",
      emptyObjectJson,
      emptyObjectJson)

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
              u <- user ?~ "User not found"
              transBodyJson <- tryo{json.extract[TransactionRequestBodyJSON]} ?~ {"Invalid json format"}
              transBody <- tryo{getTransactionRequestBodyFromJson(transBodyJson)}
              fromBank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~! {"Unknown bank account"}
              toBankId <- tryo(BankId(transBodyJson.to.bank_id))
              toAccountId <- tryo(AccountId(transBodyJson.to.account_id))
              toAccount <- tryo{BankAccount(toBankId, toAccountId).get} ?~! {"Unknown counterparty account"}
              accountsCurrencyEqual <- tryo(assert(fromAccount.currency == toAccount.currency)) ?~! {"Counterparty and holder accounts have differing currencies."}
              transferCurrencyEqual <- tryo(assert(transBodyJson.value.currency == fromAccount.currency)) ?~! {"Request currency and holder account currency can't be different."}
              rawAmt <- tryo {BigDecimal(transBodyJson.value.amount)} ?~! s"Amount ${transBodyJson.value.amount} not convertible to number"
              createdTransactionRequest <- Connector.connector.vend.createTransactionRequest(u, fromAccount, toAccount, transactionRequestType, transBody)
            } yield {
              val json = Extraction.decompose(createdTransactionRequest)
              createdJsonResponse(json)
            }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }



    resourceDocs += ResourceDoc(
      apiVersion,
      "answerTransactionRequestChallenge",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
      "Answer Transaction Request Challenge.",
      "",
      emptyObjectJson,
      emptyObjectJson)

    lazy val answerTransactionRequestChallenge: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              fromBank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~! {"Unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              answerJson <- tryo{json.extract[ChallengeAnswerJSON]} ?~ {"Invalid json format"}
              //TODO check more things here
              answerOk <- Connector.connector.vend.answerTransactionRequestChallenge(transReqId, answerJson.answer)
              //create transaction and insert its id into the transaction request
              transactionRequest <- Connector.connector.vend.createTransactionAfterChallenge(u, transReqId)
            } yield {
              val successJson = Extraction.decompose(transactionRequest)
              successJsonResponse(successJson, 202)
            }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }




    resourceDocs += ResourceDoc(
      apiVersion,
      "addCustomer",
      "POST",
      "/banks/BANK_ID/customer",
      "Add a customer.",
      """Add a customer linked to the currently authenticated user.
        |This call is experimental and will require additional permissions/role in the future.
        |For now the authenticated user can create at most one linked customer.
        |OAuth authentication is required.
        |""",
      Extraction.decompose(CustomerJson("687687678", "Joe David Bloggs",
        "+44 07972 444 876", "person@example.com", CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate))),
      emptyObjectJson)

    lazy val addCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! "User must be logged in to post Customer"
            bank <- tryo(Bank(bankId).get) ?~! {"Unknown bank id"}
            customer <- booleanToBox(Customer.customerProvider.vend.getCustomer(bankId, u).isEmpty) ?~ "Customer already exists for this user."
            postedData <- tryo{json.extract[CustomerJson]} ?~! "Incorrect json format"
            customer <- Customer.customerProvider.vend.addCustomer(bankId,
                u,
                postedData.customer_number,
                postedData.legal_name,
                postedData.mobile_phone_number,
                postedData.email,
                MockCustomerFaceImage(postedData.face_image.date, postedData.face_image.url)) ?~! "Could not create customer"
          } yield {
            val successJson = Extraction.decompose(customer)
            successJsonResponse(successJson)
          }
      }
    }



    if (Props.devMode) {
      resourceDocs += ResourceDoc(
        apiVersion,
        "getTransactionRequests",
        "GET",
        "/i-do-not-exist-i-will-404",
        "I am only a test resource Doc",
        """
            |
            |#This should be H1
            |
            |##This should be H2
            |
            |###This should be H3
            |
            |####This should be H4
            |
            |Here is a list with two items:
            |
            |* One
            |* Two
            |
            |There are underscores by them selves _
            |
            |There are _underscores_ around a word
            |
            |There are underscores_in_words
            |
            |There are 'underscores_in_words_inside_quotes'
            |
            |There are (underscores_in_words_in_brackets)
            |
            |_etc_...""",
          emptyObjectJson,
          emptyObjectJson)
      }
  }
}
