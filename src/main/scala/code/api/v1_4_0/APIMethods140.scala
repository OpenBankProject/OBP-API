package code.api.v1_4_0

import code.api.util.APIUtil.isValidCurrencyISOCode
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.api.util.{APIUtil, ApiRole, ApiVersion, NewStyle}
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.CreateCustomerJson
import code.bankconnectors.Connector
import code.usercustomerlinks.UserCustomerLink
import code.util.Helper
import code.views.Views
import net.liftweb.common.{Box, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.concurrent.Future

// JObject creation
import code.api.APIFailure
import code.api.v1_2_1.{APIInfoJSON, APIMethods121, HostedBy}
import code.api.v1_3_0.APIMethods130

import scala.collection.mutable.ArrayBuffer
//import code.api.v2_0_0.{OBPAPI2_0_0, APIMethods200}

// So we can include resource docs from future versions
//import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{ResourceDoc, authenticationRequiredMessage, _}
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages._
import code.atms.Atms
import code.branches.Branches
import code.crm.CrmEvent
import code.customer.{Customer, CustomerFaceImage, CustomerMessages}
import code.model._
import code.products.Products
import code.util.Helper._

import scala.concurrent.ExecutionContext.Implicits.global

trait APIMethods140 extends MdcLoggable with APIMethods130 with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: RestHelper =>

  val Implementations1_4_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson = EmptyClassJson()
    val apiVersion : ApiVersion = ApiVersion.v1_4_0 // was noV i.e.  "1_4_0"
    val apiVersionStatus : String = "STABLE"

    resourceDocs += ResourceDoc(
      getCustomer,
      apiVersion,
      "getCustomer",
      "GET",
      "/banks/BANK_ID/customer",
      "Get customer for logged in user",
      """Information about the currently authenticated user.
      |
      |Authentication via OAuth is required.""",
      emptyObjectJson,
      customerJsonV140,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer))

    lazy val getCustomer : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonGet _ => {
        cc => {
          for {
            u <- cc.user ?~! ErrorMessages.UserNotLoggedIn
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
            ucls <- tryo{UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(u.userId)} ?~! ErrorMessages.UserCustomerLinksNotFoundForUser
            ucl <- tryo{ucls.find(x=>Customer.customerProvider.vend.getBankIdByCustomerId(x.customerId) == bankId.value)}
            _ <- booleanToBox(ucl.size > 0, ErrorMessages.UserCustomerLinksNotFoundForUser)
            u <- ucl
            info <- Customer.customerProvider.vend.getCustomerByCustomerId(u.customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val json = JSONFactory1_4_0.createCustomerJson(info)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCustomerMessages,
      apiVersion,
      "getCustomerMessages",
      "GET",
      "/banks/BANK_ID/customer/messages",
      "Get Customer Messages (current)",
      """Get messages for the logged in customer
      |Messages sent to the currently authenticated user.
      |
      |Authentication via OAuth is required.""",
      emptyObjectJson,
      customerMessagesJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMessage, apiTagCustomer))

    lazy val getCustomerMessages  : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customer" :: "messages" :: Nil JsonGet _ => {
        cc =>{
          for {
            u <- cc.user ?~! ErrorMessages.UserNotLoggedIn
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
            //au <- ResourceUser.find(By(ResourceUser.id, u.apiId))
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
      addCustomerMessage,
      apiVersion,
      "addCustomerMessage",
      "POST",
      "/banks/BANK_ID/customer/CUSTOMER_ID/messages",
      "Add Customer Message.",
      "Add a message for the customer specified by CUSTOMER_ID",
      // We use Extraction.decompose to convert to json
      addCustomerMessageJson,
      successMessage,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMessage, apiTagCustomer, apiTagPerson)
    )

    // TODO Add Role

    lazy val addCustomerMessage : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customer" :: customerId ::  "messages" :: Nil JsonPost json -> _ => {
        cc =>{
          for {
            postedData <- tryo{json.extract[AddCustomerMessageJson]} ?~! "Incorrect json format"
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
            customer <- Customer.customerProvider.vend.getCustomerByCustomerId(customerId) ?~ ErrorMessages.CustomerNotFoundByCustomerId
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByCustomerId(customer.customerId) ?~! ErrorMessages.UserCustomerLinksNotFoundForUser
            user <- User.findByUserId(userCustomerLink.userId) ?~! ErrorMessages.UserNotFoundById
            _ <- booleanToBox(
              CustomerMessages.customerMessageProvider.vend.addMessage(
                user, bankId, postedData.message, postedData.from_department, postedData.from_person),
              "Server error: could not add message")
          } yield {
            successJsonResponse(Extraction.decompose(successMessage), 201)
          } 
        }
      }
    }


    val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    resourceDocs += ResourceDoc(
      getBranches,
      apiVersion,
      "getBranches",
      "GET",
      "/banks/BANK_ID/branches",
      "Get Bank Branches",
      s"""Returns information about branches for a single bank specified by BANK_ID including:
        |
        |* Name
        |* Address
        |* Geo Location
        |* License the data under this endpoint is released under
        |
        |
        |
        |Pagination:
        |By default, 50 records are returned.
        |
        |You can use the url query parameters *limit* and *offset* for pagination
        |
        |${authenticationRequiredMessage(!getBranchesIsPublic)}""",
      emptyObjectJson,
      branchesJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No branches available. License may not be set.",
        UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBranch)
    )

    lazy val getBranches : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: Nil JsonGet req => {
        cc =>{
          for {
            _ <- if(getBranchesIsPublic)
              Box(Some(1))
            else
              cc.user ?~! UserNotLoggedIn
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
            // Get branches from the active provider
            httpParams <- createHttpParamsByUrl(cc.url)
            obpQueryParams <- createQueriesByHttpParams(httpParams)
            branches <- Box(Branches.branchesProvider.vend.getBranches(bankId, obpQueryParams: _*)) ~> APIFailure("No branches available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createBranchesJson(branches)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


    val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    resourceDocs += ResourceDoc(
      getAtms,
      apiVersion,
      "getAtms",
      "GET",
      "/banks/BANK_ID/atms",
      "Get Bank ATMS",
      s"""Returns information about ATMs for a single bank specified by BANK_ID including:
         |
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |
         |
         |Pagination:
         |By default, 50 records are returned.
         |
         |You can use the url query parameters *limit* and *offset* for pagination
         |
         |
         |${authenticationRequiredMessage(!getAtmsIsPublic)}""",
      emptyObjectJson,
      atmsJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No ATMs available. License may not be set.",
        UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet req => {
        cc =>{
          for {
          // Get atms from the active provider

            _ <- if(getAtmsIsPublic)
              Box(Some(1))
            else
              cc.user ?~! UserNotLoggedIn
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
            
            httpParams <- createHttpParamsByUrl(cc.url)
            obpQueryParams <- createQueriesByHttpParams(httpParams)
            atms <- Box(Atms.atmsProvider.vend.getAtms(bankId, obpQueryParams:_*)) ~> APIFailure("No ATMs available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createAtmsJson(atms)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


    val getProductsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getProductsIsPublic", true)


    resourceDocs += ResourceDoc(
      getProducts,
      apiVersion,
      "getProducts",
      "GET",
      "/banks/BANK_ID/products",
      "Get Bank Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID including:
        |
        |* Name
        |* Code
        |* Category
        |* Family
        |* Super Family
        |* More info URL
        |* Description
        |* Terms and Conditions
        |* License the data under this endpoint is released under
        |${authenticationRequiredMessage(!getProductsIsPublic)}""",
      emptyObjectJson,
      productsJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No products available.",
        "License may not be set.",
        UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getProducts : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet _ => {
        cc =>{
          for {
          // Get products from the active provider
            _ <- if(getProductsIsPublic)
              Box(Some(1))
            else
              cc.user ?~! UserNotLoggedIn
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
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
      getCrmEvents,
      apiVersion,
      "getCrmEvents",
      "GET",
      "/banks/BANK_ID/crm-events",
      "Get CRM Events",
      "",
      emptyObjectJson,
      crmEventsJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No CRM Events available.",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer)
    )

    // TODO Require Role

    lazy val getCrmEvents : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "crm-events" :: Nil JsonGet _ => {
        cc =>{
          for {
            // Get crm events from the active provider
            _ <- cc.user ?~! UserNotLoggedIn
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
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

    /*
     transaction requests (new payments since 1.4.0)
    */

    resourceDocs += ResourceDoc(
      getTransactionRequestTypes,
      apiVersion,
      "getTransactionRequestTypes",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types",
      "Get Transaction Request Types for Account",
      """Returns the Transation Request Types that the account specified by ACCOUNT_ID and view specified by VIEW_ID has access to.
        |
        |These are the ways this API Server can create a Transaction via a Transaction Request
        |(as opposed to Transaction Types which include external types too e.g. for Transactions created by core banking etc.)
        |
        | A Transaction Request Type internally determines:
        |
        | * the required Transaction Request 'body' i.e. fields that define the 'what' and 'to' of a Transaction Request,
        | * the type of security challenge that may be be raised before the Transaction Request proceeds, and
        | * the threshold of that challenge.
        |
        | For instance in a 'SANDBOX_TAN' Transaction Request, for amounts over 1000 currency units, the user must supply a positive integer to complete the Transaction Request and create a Transaction.
        |
        | This approach aims to provide only one endpoint for initiating transactions, and one that handles challenges, whilst still allowing flexibility with the payload and internal logic.
        | 
      """.stripMargin,
      emptyObjectJson,
      transactionRequestTypesJsonV140,
      List(
        UserNotLoggedIn,
        BankNotFound,
        AccountNotFound,
        "Please specify a valid value for CURRENCY of your Bank Account. "
        ,"Current user does not have access to the view ",
        "account not found at bank",
        "user does not have access to owner view",
        TransactionRequestsNotEnabled,
        UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val getTransactionRequestTypes: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
          Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.isEnabledTransactionRequests()
            (bank, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            (fromAccount, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            failMsg = ErrorMessages.InvalidISOCurrencyCode.concat("Please specify a valid value for CURRENCY of your Bank Account. ")
            _ <- NewStyle.function.tryons(failMsg, 400, callContext) {
              assert(isValidCurrencyISOCode(fromAccount.currency))
            }
            view <- NewStyle.function.view(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {
              u.hasViewAccess(view)
            } 
            transactionRequestTypes <- Future(Connector.connector.vend.getTransactionRequestTypes(u, fromAccount)) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
            transactionRequestTypeCharges <- Future(Connector.connector.vend.getTransactionRequestTypeCharges(bankId, accountId, viewId, transactionRequestTypes)) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            val json = JSONFactory1_4_0.createTransactionRequestTypesJSONs(transactionRequestTypeCharges)
            (json, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getTransactionRequests,
      apiVersion,
      "getTransactionRequests",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get all Transaction Requests.",
      "",
      emptyObjectJson,
      transactionRequest,
      List(
        UserNotLoggedIn,
        BankNotFound,
        AccountNotFound,
        "Current user does not have access to the view",
        "account not found at bank",
        "user does not have access to owner view",
        UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val getTransactionRequests: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        cc =>
          if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false)) {
            for {
              u <- cc.user ?~ ErrorMessages.UserNotLoggedIn
              (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {ErrorMessages.AccountNotFound}
              view <- Views.views.vend.view(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId))
              _ <- booleanToBox(u.hasViewAccess(view), UserNoPermissionAccessView)
              transactionRequests <- Connector.connector.vend.getTransactionRequests(u, fromAccount)
            }
            yield {
              // TODO return 1.4.0 version of Transaction Requests!
              val successJson = Extraction.decompose(transactionRequests)
              successJsonResponse(successJson)
            }
          } else {
            Full(errorJsonResponse(TransactionRequestsNotEnabled))
          }
      }
    }



    case class TransactionIdJson(transaction_id : String)

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
        |In sandbox mode, if the amount is < 100 the transaction request will create a transaction without a challenge, else a challenge will need to be answered.
        |If a challenge is created you must answer it using Answer Transaction Request Challenge before the Transaction is created.
        |
        |Please see later versions of this call in 2.0.0 or 2.1.0.
        |""",
      transactionRequestBodyJsonV140,
      transactionRequestJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        CounterpartyNotFound,
        "Counterparty and holder accounts have differing currencies.",
        "Request currency and holder account currency can't be different.",
        "Amount not convertible to number",
        "account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}",
        "user does not have access to owner view",
        "amount ${body.value.amount} not convertible to number",
        "Cannot send payment to account with different currency",
        "Can't send a payment with a value of 0 or less.",
        TransactionRequestsNotEnabled,
        UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val createTransactionRequest: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
          TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        cc =>
          if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false)) {
            for {
              /* TODO:
               * check if user has access using the view that is given (now it checks if user has access to owner view), will need some new permissions for transaction requests
               * test: functionality, error messages if user not given or invalid, if any other value is not existing
              */
              u <- cc.user ?~ ErrorMessages.UserNotLoggedIn
              transBodyJson <- tryo{json.extract[TransactionRequestBodyJsonV140]} ?~ {ErrorMessages.InvalidJsonFormat}
              transBody <- tryo{getTransactionRequestBodyFromJson(transBodyJson)}
              (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {ErrorMessages.AccountNotFound}
              toBankId <- tryo(BankId(transBodyJson.to.bank_id))
              toAccountId <- tryo(AccountId(transBodyJson.to.account_id))
              toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}
              _ <- tryo(assert(fromAccount.currency == toAccount.currency)) ?~! {"Counterparty and holder accounts have differing currencies."}
              _ <- tryo(assert(transBodyJson.value.currency == fromAccount.currency)) ?~! {"Request currency and holder account currency can't be different."}
              _ <- tryo {BigDecimal(transBodyJson.value.amount)} ?~! s"Amount ${transBodyJson.value.amount} not convertible to number"
              createdTransactionRequest <- Connector.connector.vend.createTransactionRequest(u, fromAccount, toAccount, transactionRequestType, transBody)
              oldTransactionRequest <- transforOldTransactionRequest(createdTransactionRequest)
            } yield {
              val json = Extraction.decompose(oldTransactionRequest)
              createdJsonResponse(json)
            }
          } else {
            Full(errorJsonResponse(TransactionRequestsNotEnabled))
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
      """
        |In Sandbox mode, any string that can be converted to a possitive integer will be accepted as an answer. 
        |
      """.stripMargin,
      challengeAnswerJSON,
      transactionRequestJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        InvalidJsonFormat,
        "Current user does not have access to the view ",
        "Couldn't create Transaction",
        TransactionRequestsNotEnabled,
        "Need a non-empty answer",
        "Need a numeric TAN",
        "Need a positive TAN",
        "unknown challenge type",
        "Sorry, you've used up your allowed attempts.",
        "Error getting Transaction Request",
        "Transaction Request not found",
        "Couldn't create Transaction",
        UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val answerTransactionRequestChallenge: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        cc =>
          if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false)) {
            for {
              u <- cc.user ?~ ErrorMessages.UserNotLoggedIn
              (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
              view <- Views.views.vend.view(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId))
              _ <- booleanToBox(u.hasViewAccess(view), UserNoPermissionAccessView)
              answerJson <- tryo{json.extract[ChallengeAnswerJSON]} ?~ InvalidJsonFormat
              //TODO check more things here
              _ <- Connector.connector.vend.answerTransactionRequestChallenge(transReqId, answerJson.answer)
              //create transaction and insert its id into the transaction request
              transactionRequest <- Connector.connector.vend.createTransactionAfterChallenge(u, transReqId)
              oldTransactionRequest <- transforOldTransactionRequest(transactionRequest)
            } yield {
              val successJson = Extraction.decompose(oldTransactionRequest)
              successJsonResponse(successJson, 202)
            }
          } else {
            Full(errorJsonResponse(TransactionRequestsNotEnabled))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addCustomer,
      apiVersion,
      "addCustomer",
      "POST",
      "/banks/BANK_ID/customer",
      "Add a customer.",
      s"""Add a customer linked to the currently authenticated user.
         |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
         |This call may require additional permissions/role in the future.
         |For now the authenticated user can create at most one linked customer.
         |Dates need to be in the format 2013-01-21T23:08:00Z
         |${authenticationRequiredMessage(true) }
         |Note: This call is depreciated in favour of v.2.0.0 createCustomer
         |""",
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createCustomerJson,
      customerJsonV140,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        "entitlements required",
        CustomerNumberAlreadyExists,
        "Problem getting user_id",
        UserNotFoundById,
        "Could not create customer",
        "Could not create user_customer_links",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer),
      Some(List(canCreateCustomer, canCreateUserCustomerLink)))

    lazy val addCustomer : OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! "User must be logged in to post Customer"
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! {ErrorMessages.BankNotFound}
            postedData <- tryo{json.extract[CreateCustomerJson]} ?~! ErrorMessages.InvalidJsonFormat
            requiredEntitlements = ApiRole.canCreateCustomer :: ApiRole.canCreateUserCustomerLink :: Nil
            requiredEntitlementsTxt = requiredEntitlements.mkString(" and ")
            _ <- booleanToBox(hasAllEntitlements(bankId.value, u.userId, requiredEntitlements), s"$requiredEntitlementsTxt entitlements required")
            _ <- tryo(assert(Customer.customerProvider.vend.checkCustomerNumberAvailable(bankId, postedData.customer_number) == true)) ?~! ErrorMessages.CustomerNumberAlreadyExists
            user_id <- tryo{if (postedData.user_id.nonEmpty) postedData.user_id else u.userId} ?~ s"Problem getting user_id"
            _ <- User.findByUserId(user_id) ?~! ErrorMessages.UserNotFoundById
            customer <- Customer.customerProvider.vend.addCustomer(bankId,
                postedData.customer_number,
                postedData.legal_name,
                postedData.mobile_phone_number,
                postedData.email,
                CustomerFaceImage(postedData.face_image.date, postedData.face_image.url),
                postedData.date_of_birth,
                postedData.relationship_status,
                postedData.dependants,
                postedData.dob_of_dependants,
                postedData.highest_education_attained,
                postedData.employment_status,
                postedData.kyc_status,
                postedData.last_ok_date,
                None,
                None,
                "",
                "",
                ""
            ) ?~! "Could not create customer"
            _ <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(user_id, customer.customerId, DateWithMsExampleObject, true) ?~! "Could not create user_customer_links"
          } yield {
            val successJson = JSONFactory1_4_0.createCustomerJson(customer)
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }



    if (Props.devMode) {
      resourceDocs += ResourceDoc(
        dummy(apiVersion, apiVersionStatus),
        apiVersion,
        "testResourceDoc",
        "GET",
        "/dummy",
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
        apiInfoJSON,
        List(UserNotLoggedIn, UnknownError),
        Catalogs(notCore, notPSD2, notOBWG),
        List(apiTagDocumentation))
      }



    def dummy(apiVersion : ApiVersion, apiVersionStatus: String) : OBPEndpoint = {
      case "dummy" :: Nil JsonGet req => {
        cc =>
          val apiDetails: JValue = {
            val hostedBy = new HostedBy("Dummy Org", "contact@example.com", "12345", "https://www.example.com")
            val apiInfoJSON = new APIInfoJSON(apiVersion.vDottedApiVersion, apiVersionStatus, gitCommit, "DUMMY", hostedBy)
            Extraction.decompose(apiInfoJSON)
          }

          Full(successJsonResponse(apiDetails, 200))
      }
    }

  }
}
