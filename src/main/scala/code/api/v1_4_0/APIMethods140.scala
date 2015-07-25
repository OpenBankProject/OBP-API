package code.api.v1_4_0

import code.api.APIFailure
import code.api.v1_4_0.JSONFactory1_4_0.AddCustomerMessageJson
import code.atms.Atms
import code.branches.Branches
import code.crm.CrmEvent
import code.customerinfo.{CustomerMessages, CustomerInfo}
import code.model.dataAccess.APIUser
import code.model.{BankId, User}
import code.products.Products
import net.liftweb.common.Box
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.http.rest.RestHelper
import code.api.util.APIUtil._
import net.liftweb.json.Extraction
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.common.Full
import code.util.Helper._

import collection.mutable.ArrayBuffer

import code.api.util.APIUtil.ResourceDoc


trait APIMethods140 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>


  val Implementations1_4_0 = new Object(){


    val resourceDocs = ArrayBuffer[ResourceDoc]()

    def getLocalResourceDocs : Option[List[ResourceDoc]] =
    {
      Some(resourceDocs.toList)
    }


    resourceDocs += ResourceDoc(1, "GET", "banks/BANK_ID/customer", "Get customer information about the logged in customer")

    lazy val getCustomerInfo : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer info"
            info <- CustomerInfo.customerInfoProvider.vend.getInfo(bankId, u) ~> APIFailure("No customer info found", 204)
          } yield {
            val json = JSONFactory1_4_0.createCustomerInfoJson(info)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(2, "GET", "banks/BANK_ID/customer/messages", "Get messages for the logged in customer")

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


    resourceDocs += ResourceDoc(3, "POST", "banks/BANK_ID/customer/CUSTOMER_NUMBER", "Add a message for the customer specified by CUSTOMER_NUMBER")


    lazy val addCustomerMessage : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: customerNumber ::  "messages" :: Nil JsonPost json -> _ => {
        user => {
          for {
            postedData <- tryo{json.extract[AddCustomerMessageJson]} ?~! "Incorrect json format"
            customer <- CustomerInfo.customerInfoProvider.vend.getUser(bankId, customerNumber) ?~! "No customer found"
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

    // Provides resource documents so that live docs (currently on Sofi) can use to display API documentation
    lazy val getResourceDocs : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: Nil JsonGet _ => {
        user => {
          for {
            rd <- getLocalResourceDocs
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createResourceDocsJson(rd)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

  }

}
