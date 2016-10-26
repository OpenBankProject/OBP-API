package code.api.v2_1_0

import java.text.SimpleDateFormat
import code.api.util.ApiRole._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJSON
import code.api.v2_0_0.{EntitlementJSON, JSONFactory200, EntitlementJSONs, TransactionRequestBodyJSON}
import code.api.v2_1_0.JSONFactory210._
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.fx.fx
import code.model._

import net.liftweb.http.Req
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.util.Helper._
import net.liftweb.json.JsonDSL._

import code.api.APIFailure
import code.api.util.APIUtil._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import net.liftweb.common.{Empty, Full, Box}
import net.liftweb.http.JsonResponse
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers._
import net.liftweb.json._
import net.liftweb.json.Serialization.{read, write}


trait APIMethods210 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  // helper methods end here

  val Implementations2_1_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson: JValue = Nil
    val apiVersion: String = "2_1_0"

    val exampleDateString: String = "22/08/2013"
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)

    val codeContext = CodeContext(resourceDocs, apiRelations)


    // TODO Add example body below

    resourceDocs += ResourceDoc(
      sandboxDataImport,
      apiVersion,
      "sandboxDataImport",
      "POST",
      "/sandbox/data-import",
      "Create sandbox",
      s"""Import bulk data into the sandbox (Authenticated access).
          |
          |This call can be used to create banks, users, accounts and transactions which are stored in the local RDBMS.
          |
          |The user needs to have CanCreateSandbox entitlement.
          |
          |An example of an import set of data (json) can be found [here](https://raw.githubusercontent.com/OpenBankProject/OBP-API/develop/src/main/scala/code/api/sandbox/example_data/2016-04-28/example_import.json)
         |${authenticationRequiredMessage(true)}
          |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val sandboxDataImport: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Import data into the sandbox
      case "sandbox" :: "data-import" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            allowDataImportProp <- Props.get("allow_sandbox_data_import") ~> APIFailure("Data import is disabled for this API instance.", 403)
            allowDataImport <- Helper.booleanToBox(allowDataImportProp == "true") ~> APIFailure("Data import is disabled for this API instance.", 403)
            canCreateSandbox <- booleanToBox(hasEntitlement("", u.userId, CanCreateSandbox), s"$CanCreateSandbox entitlement required")
            importData <- tryo {json.extract[SandboxDataImport]} ?~ "invalid json"
            importWorked <- OBPDataImport.importer.vend.importData(importData)
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
      }
    }


    val getTransactionRequestTypesIsPublic = Props.getBool("apiOptions.getTransactionRequestTypesIsPublic", true)

    resourceDocs += ResourceDoc(
      getTransactionRequestTypesSupportedByBank,
      apiVersion,
      "getTransactionRequestTypesSupportedByBank",
      "GET",
      "/banks/BANK_ID/transaction-request-types",
      "Get the Transaction Request Types supported by the bank",
      s"""Get the list of the Transaction Request Types supported by the bank.
        |
        |${authenticationRequiredMessage(!getTransactionRequestTypesIsPublic)}
        |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagBank, apiTagTransactionRequest))


    lazy val getTransactionRequestTypesSupportedByBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Get transaction request types supported by the bank
      case "banks" :: BankId(bankId) :: "transaction-request-types" :: Nil JsonGet _ => {
        user =>
          for {
            u <- if(getTransactionRequestTypesIsPublic)
              Box(Some(1))
            else
              user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
            transactionRequestTypes <- tryo(Props.get("transactionRequests_supported_types", ""))
          } yield {
            // Format the data as json
            val json = JSONFactory210.createTransactionRequestTypeJSON(transactionRequestTypes.split(",").toList)
            // Return
            successJsonResponse(Extraction.decompose(json))
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
        |In OBP, a `transaction request` may or may not result in a `transaction`. A `transaction` only has one possible state: completed.
        |
        |A `transaction request` on the other hand can have one of several states.
        |
        |Think of `transactions` as items in a bank statement that represent the movement of money.
        |
        |Think of `transaction requests` as orders to move money which may or may not succeeed and result in a `transaction`.
        |
        |A `transaction request` might create a security challenge that needs to be answered before the `transaction request` proceeds.
        |
        |Transaction Requests contain charge information giving the client the opporunity to proceed or not (as long as the challenge level is appropriate).
        |
        |Transaction Requests can have one of several Transaction Request Types which expect different bodies. The escaped body is returned in the details key of the GET response.
        |This provides some commonality and one URL for many differrent payment or transfer types with enough flexilbity to validate them differently.
        |
        |The payer is set in the URL. Money comes out of the BANK_ID and ACCOUNT_ID specified in the UR
        |
        |The payee is set in the request body. Money goes into the BANK_ID and ACCOUNT_IDO specified in the request body.
        |
        |In sandbox mode, TRANSACTION_REQUEST_TYPE is commonly set to SANDBOX_TAN. See getTransactionRequestTypesSupportedByBank for all supported types.
        |
        |In sandbox mode, if the amount is less than 1000 (any currency, unless it is set differently on this server), the transaction request will create a transaction without a challenge, else a challenge will need to be answered.
        |
        |You can transfer between different currency accounts. (new in 2.0.0). The currency in body must match the sending account.
        |
        |The following static FX rates are available in sandbox mode:
        |
        |${exchangeRates}
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
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      Extraction.decompose(TransactionRequestBodyJSON (
        TransactionRequestAccountJSON("BANK_ID", "ACCOUNT_ID"),
        AmountOfMoneyJSON("EUR", "100.53"),
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

              // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
              validTransactionRequestTypes <- tryo{Props.get("transactionRequests_supported_types", "")}
              // Use a list instead of a string to avoid partial matches
              validTransactionRequestTypesList <- tryo{validTransactionRequestTypes.split(",")}
              isValidTransactionRequestType <- tryo(assert(transactionRequestType.value != "TRANSACTION_REQUEST_TYPE" && validTransactionRequestTypesList.contains(transactionRequestType.value))) ?~! s"${ErrorMessages.InvalidTransactionRequestType} : Invalid value is: '${transactionRequestType.value}' Valid values are: ${validTransactionRequestTypes}"

              transDetailsJson <- transactionRequestType.value match {
                case "SANDBOX_TAN" => tryo {
                  json.extract[TransactionRequestDetailsSandBoxTanJSON]
                } ?~ {
                  ErrorMessages.InvalidJsonFormat
                }
                case "SEPA" => tryo {
                  json.extract[TransactionRequestDetailsSEPAJSON]
                } ?~ {
                  ErrorMessages.InvalidJsonFormat
                }
                case "FREE_FORM" => tryo {
                  json.extract[TransactionRequestDetailsFreeFormJSON]
                } ?~ {
                  ErrorMessages.InvalidJsonFormat
                }
              }

              transDetails <- transactionRequestType.value match {
                case "SANDBOX_TAN" => tryo{getTransactionRequestDetailsSandBoxTanFromJson(transDetailsJson.asInstanceOf[TransactionRequestDetailsSandBoxTanJSON])}
                case "SEPA" => tryo{getTransactionRequestDetailsSEPAFromJson(transDetailsJson.asInstanceOf[TransactionRequestDetailsSEPAJSON])}
                case "FREE_FORM" => tryo{getTransactionRequestDetailsFreeFormFromJson(transDetailsJson.asInstanceOf[TransactionRequestDetailsFreeFormJSON])}
              }

              fromBank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {ErrorMessages.AccountNotFound}
              isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true || hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true , ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)

              // Prevent default value for transaction request type (at least).
              transferCurrencyEqual <- tryo(assert(transDetailsJson.value.currency == fromAccount.currency)) ?~! {"Transfer body currency and holder account currency must be the same."}

              transDetailsSerialized <- transactionRequestType.value match {
                case "FREE_FORM" => tryo{
                  implicit val formats = Serialization.formats(NoTypeHints)
                  write(json)
                }
                case _ => tryo{
                  implicit val formats = Serialization.formats(NoTypeHints)
                  write(transDetailsJson)
                }
              }

              createdTransactionRequest <- transactionRequestType.value match {
                case "SANDBOX_TAN" => {
                  for {
                    toBankId <- Full(BankId(transDetailsJson.asInstanceOf[TransactionRequestDetailsSandBoxTanJSON].to.bank_id))
                    toAccountId <- Full(AccountId(transDetailsJson.asInstanceOf[TransactionRequestDetailsSandBoxTanJSON].to.account_id))
                    toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}

                    createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Full(toAccount), transactionRequestType, transDetails, transDetailsSerialized)
                  } yield createdTransactionRequest

                }
                case "SEPA" => {
                  Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Empty, transactionRequestType, transDetails, transDetailsSerialized)
                }
                case "FREE_FORM" => {
                  Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Empty, transactionRequestType, transDetails, transDetailsSerialized)
                }
              }
            } yield {
              // Explicitly format as v2.1.0 json
              val json = JSONFactory210.createTransactionRequestWithChargeJSON(createdTransactionRequest)
              createdJsonResponse(Extraction.decompose(json))
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
        |* Details including Currency, Value, Description and other initiation information specific to each type. (Could potentialy include a list of future transactions.)
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
              fromBank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {ErrorMessages.AccountNotFound}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              transactionRequests <- Connector.connector.vend.getTransactionRequests210(u, fromAccount)
            }
              yield {
                // Format the data as V2.0.0 json
                val json = JSONFactory210.createTransactionRequestJSONs(transactionRequests)
                successJsonResponse(Extraction.decompose(json))
              }
          } else {
            Full(errorJsonResponse("Sorry, Transaction Requests are not enabled in this API instance."))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getRoles,
      apiVersion,
      "getRoles",
      "GET",
      "/roles",
      "Get Roles",
      """Returns all available roles
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

    lazy val getRoles: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "roles" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            // isSuperAdmin <- booleanToBox(isSuperAdmin(u.userId)) ?~ "Logged user is not super admin!"
          }
          yield {
            // Format the data as V2.1.0 json
            val json = JSONFactory210.createAvailableRolesJSON(ApiRole.availableRoles.sorted)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getEntitlementsByBankAndUser,
      apiVersion,
      "getEntitlementsByBankAndUser",
      "GET",
      "/banks/BANK_ID/users/USER_ID/entitlements",
      "Get Entitlements specified by BANK_ID and USER_ID",
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


    lazy val getEntitlementsByBankAndUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~ {ErrorMessages.BankNotFound}
            usr <- User.findByUserId(userId) ?~! ErrorMessages.UserNotFoundById
            requiredEntitlements = CanGetEntitlementsForAnyUserAtOneBank ::
                                   CanGetEntitlementsForAnyUserAtAnyBank::
                                   Nil
            requiredEntitlementsTxt = requiredEntitlements.mkString(" or ")
            hasAtLeastOneEntitlement <- booleanToBox(hasAtLeastOneEntitlement(bankId.value, u.userId, requiredEntitlements), s"$requiredEntitlementsTxt entitlements required")
            entitlements <- Entitlement.entitlement.vend.getEntitlements(userId)
            filteredEntitlements <- tryo{entitlements.filter(_.bankId == bankId.value)}
          }
          yield {
            var json = EntitlementJSONs(Nil)
            // Format the data as V2.1.0 json
            if (isSuperAdmin(userId)) {
              // If the user is SuperAdmin add it to the list
              json = EntitlementJSONs(JSONFactory200.createEntitlementJSONs(filteredEntitlements).list:::List(EntitlementJSON("", "SuperAdmin", "")))
              successJsonResponse(Extraction.decompose(json))
            } else {
              json = JSONFactory200.createEntitlementJSONs(filteredEntitlements)
            }
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }


  }
}

object APIMethods210 {
}
