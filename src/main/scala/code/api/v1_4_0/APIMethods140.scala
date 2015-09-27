package code.api.v1_4_0

import code.bankconnectors.Connector
import code.transfers.Transfers.{TransferId, TransferBody, TransferAccount}
import net.liftweb.common.{Failure, Loggable, Box, Full}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{ShortTypeHints, DefaultFormats, Extraction}
import net.liftweb.json.JsonAST.{JField, JObject, JValue}
import net.liftweb.json.Serialization._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Props

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
import code.customer.{CustomerMessages, Customer}
import code.model._
import code.products.Products
import code.api.util.APIUtil._
import code.util.Helper._
import code.api.util.APIUtil.ResourceDoc
import code.transfers.Transfers._

trait APIMethods140 extends Loggable with APIMethods130 with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: RestHelper =>

  val Implementations1_4_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "1_4_0"

    implicit val formats = DefaultFormats.withHints(
      ShortTypeHints(List(classOf[TransferBody])))

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
      emptyObjectJson,
      emptyObjectJson)

    lazy val getCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve Customer"
            info <- Customer.customerProvider.vend.getCustomer(bankId, u) ~> APIFailure("No Customer found for current User", 204)
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
      emptyObjectJson,
      emptyObjectJson)

    lazy val getCustomerMessages  : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: "messages" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer messages"
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
      "/banks/BANK_ID/customer/CUSTOMER_NUMBER",
      "Add a message for the customer specified by CUSTOMER_NUMBER",
      // We use Extraction.decompose to convert to json
      Extraction.decompose(AddCustomerMessageJson("message to send", "from department", "from person")),
      emptyObjectJson
    )

    lazy val addCustomerMessage : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: customerNumber ::  "messages" :: Nil JsonPost json -> _ => {
        user => {
          for {
            postedData <- tryo{json.extract[AddCustomerMessageJson]} ?~! "Incorrect json format"
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
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getBranches : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve Branches data"
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
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getAtms : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet _ => {
        user => {
          for {
          // Get atms from the active provider
            u <- user ?~! "User must be logged in to retrieve ATM data"
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
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getProducts : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet _ => {
        user => {
          for {
          // Get products from the active provider
            u <- user ?~! "User must be logged in to retrieve Products data"
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
      emptyObjectJson,
      emptyObjectJson
    )

    lazy val getCrmEvents : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "crm-events" :: Nil JsonGet _ => {
        user => {
          for {
            // Get crm events from the active provider
            u <- user ?~! "User must be logged in to retrieve CRM Event information"
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
      "/resource-docs",
      "Get the API calls that are documented on this server. (This call).",
      emptyObjectJson,
      emptyObjectJson
    )

    // Provides resource documents so that live docs (currently on Sofi) can display API documentation
    lazy val getResourceDocs : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: Nil JsonGet _ => {
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
     transfers (new payments since 1.4.0)
    */

    resourceDocs += ResourceDoc(
      apiVersion,
      "getTransferTypes",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transfer-types",
      "Get supported Transfer types.",
      emptyObjectJson,
      emptyObjectJson)

    lazy val getTransferTypes: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transfer-types" ::
          Nil JsonGet _ => {
        user =>
          if (Props.getBool("payments_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              fromBank <- tryo(Bank(bankId).get) ?~ {"unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~ {"unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"current user does not have access to the view " + viewId}
              transferTypes <- Connector.connector.vend.getTransferTypes(u, fromAccount)
            }
              yield {
                val successJson = Extraction.decompose(transferTypes)
                successJsonResponse(successJson)
              }
          } else {
            Failure("Sorry, payments are not enabled in this API instance.")
          }
      }
    }

    resourceDocs += ResourceDoc(
      apiVersion,
      "getTransfers",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transfers",
      "Get all Transfers.",
      emptyObjectJson,
      emptyObjectJson)

    lazy val getTransfers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transfers" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("payments_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              fromBank <- tryo(Bank(bankId).get) ?~ {"unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~ {"unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"current user does not have access to the view " + viewId}
              transfers <- Connector.connector.vend.getTransfers(u, fromAccount)
            }
            yield {
              val successJson = Extraction.decompose(transfers)
              successJsonResponse(successJson)
            }
          } else {
            Failure("Sorry, payments are not enabled in this API instance.")
          }
      }
    }



    case class TransactionIdJson(transaction_id : String)

    resourceDocs += ResourceDoc(
      apiVersion,
      "createTransfer",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transfer-types/TRANSFER_TYPE/transfers",
      "Create Transfer.",
      emptyObjectJson,
      emptyObjectJson)

    lazy val createTransfer: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transfer-types" ::
          TransferType(transferType) :: "transfers" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("payments_enabled", false)) {
            for {
              /* TODO:
               * check if user has access using the view that is given (now it checks if user has access to owner view), will need some new permissions for transfers
               * test: functionality, error messages if user not given or invalid, if any other value is not existing
              */
              u <- user ?~ "User not found"
              transBodyJson <- tryo{json.extract[TransferBodyJSON]} ?~ {"invalid json format"}
              transBody <- tryo{getTransferBodyFromJson(transBodyJson)}
              fromBank <- tryo(Bank(bankId).get) ?~ {"unknown bank id"}
              fromAccount <- tryo(BankAccount(bankId, accountId).get) ?~ {"unknown bank account"}
              toBankId <- tryo(BankId(transBodyJson.to.bank_id))
              toAccountId <- tryo(AccountId(transBodyJson.to.account_id))
              toAccount <- tryo{BankAccount(toBankId, toAccountId).get} ?~ {"unknown counterparty account"}
              accountsCurrencyEqual <- tryo(assert(BankAccount(bankId, accountId).get.currency == toAccount.currency)) ?~ {"Counterparty and holder account have differing currencies."}
              rawAmt <- tryo {BigDecimal(transBodyJson.value.amount)} ?~! s"amount ${transBodyJson.value.amount} not convertible to number"
              createdTransfer <- Connector.connector.vend.createTransfer(u, fromAccount, toAccount, transferType, transBody)
            } yield {
              val successJson = Extraction.decompose(createdTransfer)
              successJsonResponse(successJson)
            }
          } else {
            Failure("Sorry, payments are not enabled in this API instance.")
          }

      }
    }
  }

}
