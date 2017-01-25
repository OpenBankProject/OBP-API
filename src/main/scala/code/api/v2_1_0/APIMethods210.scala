package code.api.v2_1_0

import java.text.SimpleDateFormat
import java.util.Date

import code.TransactionTypes.TransactionType
import code.api.util.ApiRole._
import code.api.util.{APIUtil, ApiRole, ErrorMessages}
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_3_0.{JSONFactory1_3_0, _}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.{TransactionRequestBodyJSON,_}
import code.api.v2_1_0.JSONFactory210._
import code.atms.Atms
import code.atms.Atms.AtmId
import code.bankconnectors.Connector
import code.branches.Branches
import code.branches.Branches.BranchId
import code.customer.{Customer, MockCreditLimit, MockCreditRating, MockCustomerFaceImage}
import code.entitlement.Entitlement
import code.fx.fx
import code.metadata.counterparties.{Counterparties}
import code.model.dataAccess.OBPUser
import code.model.{BankId, ViewId, _}
import code.products.Products.ProductCode
import code.usercustomerlinks.UserCustomerLink
import net.liftweb.http.Req
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
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
import net.liftweb.json.Serialization.{write}


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
      Catalogs(notCore, notPSD2, notOBWG),
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
            importData <- tryo {json.extract[SandboxDataImport]} ?~ {ErrorMessages.InvalidJsonFormat}
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
      Catalogs(notCore, notPSD2, notOBWG),
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
        |The payee is set in the request body. Money goes into the BANK_ID and ACCOUNT_ID specified in the request body.
        |
        |In sandbox mode, TRANSACTION_REQUEST_TYPE is commonly set to SANDBOX_TAN. See getTransactionRequestTypesSupportedByBank for all supported types.
        |
        |In sandbox mode, if the amount is less than 1000 EUR (any currency, unless it is set differently on this server), the transaction request will create a transaction without a challenge, else the Transaction Request will be set to INITIALISED and a challenge will need to be answered.
        |
        |If a challenge is created you must answer it using Answer Transaction Request Challenge before the Transaction is created.
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
        TransactionRequestAccountJSON("bank_id", "account_id"),
        AmountOfMoneyJSON("EUR", "100.53"),
        "A description for the transaction to be created"
      )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val createTransactionRequest: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
            /* TODO:
             * this function is so complicated and duplicate code on case classes: TransactionRequestDetailsSandBoxTan,TransactionRequestDetailsSandBoxTanJSON,TransactionRequestDetailsSandBoxTanResponse different classes in defferent places
             * check if user has access using the view that is given (now it checks if user has access to owner view), will need some new permissions for transaction requests
             * test: functionality, error messages if user not given or invalid, if any other value is not existing
            */
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidAccountIdFormat
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat

              fromBank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {ErrorMessages.AccountNotFound}
              isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true || hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true, ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)

              availableViews <- Full(fromAccount.permittedViews(user))
              view <- View.fromUrl(viewId, fromAccount) ?~! {ErrorMessages.ViewNotFound}
              canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}

              // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
              validTransactionRequestTypes <- tryo{Props.get("transactionRequests_supported_types", "")}
              // Use a list instead of a string to avoid partial matches
              validTransactionRequestTypesList <- tryo{validTransactionRequestTypes.split(",")}
              // Check transactionRequestType is not "TRANSACTION_REQUEST_TYPE" which is the place holder (probably redundant because of check below)
              // Check that transactionRequestType is included in the Props
              isValidTransactionRequestType <- tryo(assert(transactionRequestType.value != "TRANSACTION_REQUEST_TYPE" && validTransactionRequestTypesList.contains(transactionRequestType.value))) ?~! s"${ErrorMessages.InvalidTransactionRequestType} : The invalid value is: '${transactionRequestType.value}' Valid values are: ${validTransactionRequestTypes}"

              transDetailsJson <- transactionRequestType.value match {
                case "SANDBOX_TAN" => tryo {
                  json.extract[TransactionRequestDetailsSandBoxTanJSON]
                } ?~ {
                  ErrorMessages.InvalidJsonFormat
                }
                case "COUNTERPARTY" => tryo {
                  json.extract[TransactionRequestDetailsCounterpartyJSON]
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
                case "COUNTERPARTY" => tryo{getTransactionRequestDetailsCounterpartyFromJson(transDetailsJson.asInstanceOf[TransactionRequestDetailsCounterpartyJSON])}
                case "SEPA" => tryo{getTransactionRequestDetailsSEPAFromJson(transDetailsJson.asInstanceOf[TransactionRequestDetailsSEPAJSON])}
                case "FREE_FORM" => tryo{getTransactionRequestDetailsFreeFormFromJson(transDetailsJson.asInstanceOf[TransactionRequestDetailsFreeFormJSON])}
              }


              //Check the validate for amount and currency
              isValidAmountNumber <-  tryo(BigDecimal(transDetails.value.amount)) ?~!ErrorMessages.InvalidNumber
              isValidCurrencyISOCode <- tryo(assert(isValidCurrencyISOCode(transDetails.value.currency)))?~!ErrorMessages.InvalidISOCurrencyCode

              // Prevent default value for transaction request type (at least).
              transferCurrencyEqual <- tryo(assert(transDetailsJson.value.currency == fromAccount.currency)) ?~! {s"${ErrorMessages.InvalidTransactionRequestCurrency} From Account Currency is ${fromAccount.currency} Requested Transaction Currency is: ${transDetailsJson.value.currency}"}

              amountOfMoneyJSON <- Full(AmountOfMoneyJSON(transDetails.value.currency, transDetails.value.amount))

              // Note: These store in the table TransactionRequestv210
              createdTransactionRequest <- transactionRequestType.value match {
                case "SANDBOX_TAN" => {
                  for {
                    toBankId <- Full(BankId(transDetailsJson.asInstanceOf[TransactionRequestDetailsSandBoxTanJSON].to.bank_id))
                    toAccountId <- Full(AccountId(transDetailsJson.asInstanceOf[TransactionRequestDetailsSandBoxTanJSON].to.account_id))
                    toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}
                    transDetailsSerialized <- tryo {
                      implicit val formats = Serialization.formats(NoTypeHints)
                      write(json)
                    }
                    createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Full(toAccount), transactionRequestType, transDetails, transDetailsSerialized)
                  } yield createdTransactionRequest
                }
                case "COUNTERPARTY" => {
                  for {
                    //For COUNTERPARTY, Use the counterpartyId to find the counterparty and set up the toAacount
                    toCounterpartyId<- Full(transDetailsJson.asInstanceOf[TransactionRequestDetailsCounterpartyJSON].to.counterpartyId)
                    counterparty <- Connector.connector.vend.getCounterpartyByCounterpartyId(CounterpartyId(toCounterpartyId)) ?~! {ErrorMessages.CounterpartyNotFoundByCounterpartyId}
                    isBeneficiary <- booleanToBox(counterparty.isBeneficiary == true, ErrorMessages.CounterpartyBeneficiaryPermit)
                    toBankId <- Full(BankId(counterparty.otherBankId ))
                    toAccountId <- Full(AccountId(counterparty.otherAccountId))
                    toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.BankAccountNotFound}

                    // Following four lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part.
                    transactionRequestAccountJSON = TransactionRequestAccountJSON(toBankId.value, toAccountId.value)
                    detailDescription = transDetailsJson.asInstanceOf[TransactionRequestDetailsCounterpartyJSON].description
                    transactionRequestDetailsCounterpartyResponseJSON = TransactionRequestDetailsCounterpartyResponseJSON(toCounterpartyId.toString,transactionRequestAccountJSON, amountOfMoneyJSON, detailDescription.toString)
                    transResponseDetails = getTransactionRequestDetailsCounterpartyResponseFromJson(transactionRequestDetailsCounterpartyResponseJSON)

                    //Serialize the new format COUNTERPARTY data.
                    transDetailsResponseSerialized <-tryo{
                      implicit val formats = Serialization.formats(NoTypeHints)
                      write(transResponseDetails)
                    }
                    createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Full(toAccount), transactionRequestType, transResponseDetails, transDetailsResponseSerialized)
                  } yield createdTransactionRequest

                }
                case "SEPA" => {
                  for {
                    //For SEPA, Use the iban to find the counterparty and set up the toAccount
                    toIban<-  Full(transDetailsJson.asInstanceOf[TransactionRequestDetailsSEPAJSON].to.iban)
                    counterparty <- Counterparties.counterparties.vend.getCounterpartyByIban(toIban) ?~! {ErrorMessages.CounterpartyNotFoundByIban}
                    isBeneficiary <- booleanToBox(counterparty.isBeneficiary == true , ErrorMessages.CounterpartyBeneficiaryPermit)
                    toBankId <- Full(BankId(counterparty.otherBankId ))
                    toAccountId <- Full(AccountId(counterparty.otherAccountId))
                    toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}

                    // Following four lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part.
                    transactionRequestAccountJSON = TransactionRequestAccountJSON(toBankId.value, toAccountId.value)
                    detailDescription = transDetailsJson.asInstanceOf[TransactionRequestDetailsSEPAJSON].description
                    transactionRequestDetailsSEPAResponseJSON = TransactionRequestDetailsSEPAResponseJSON(toIban.toString,transactionRequestAccountJSON, amountOfMoneyJSON, detailDescription.toString)
                    transResponseDetails = getTransactionRequestDetailsSEPAResponseJSONFromJson(transactionRequestDetailsSEPAResponseJSON)

                    //Serialize the new format SEPA data.
                    transDetailsResponseSerialized <-tryo{
                      implicit val formats = Serialization.formats(NoTypeHints)
                      write(transResponseDetails)
                    }
                    createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Full(toAccount), transactionRequestType, transResponseDetails, transDetailsResponseSerialized)
                  } yield createdTransactionRequest
                }
                case "FREE_FORM" => {
                  for {
                    // Following three lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part.
                    transactionRequestAccountJSON <- Full(TransactionRequestAccountJSON(fromAccount.bankId.value, fromAccount.accountId.value))
                    // The FREE_FORM discription is empty, so make it "" in the following code
                    transactionRequestDetailsFreeFormResponseJSON = TransactionRequestDetailsFreeFormResponseJSON(transactionRequestAccountJSON,amountOfMoneyJSON,"")
                    transResponseDetails <- Full(getTransactionRequestDetailsFreeFormResponseJson(transactionRequestDetailsFreeFormResponseJSON))

                    transDetailsResponseSerialized<-tryo{
                      implicit val formats = Serialization.formats(NoTypeHints)
                      write(transResponseDetails)
                    }
                    createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u, fromAccount, Full(fromAccount), transactionRequestType, transResponseDetails, transDetailsResponseSerialized)
                  } yield
                    createdTransactionRequest
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val answerTransactionRequestChallenge: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u: User <- user ?~ ErrorMessages.UserNotLoggedIn
              isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidAccountIdFormat
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
              fromBank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {"Unknown bank account"}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
              answerJson <- tryo{json.extract[ChallengeAnswerJSON]} ?~ {"Invalid json format"}
              //TODO check more things here
              answerOk <- Connector.connector.vend.answerTransactionRequestChallenge(transReqId, answerJson.answer)
              //create transaction and insert its id into the transaction request
              transactionRequest <- Connector.connector.vend.createTransactionAfterChallengev210(u, transReqId)
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
      Catalogs(Core, PSD2, OBWG),
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
      Catalogs(Core, PSD2, OBWG),
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagUser, apiTagEntitlement))


    lazy val getEntitlementsByBankAndUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~ {ErrorMessages.BankNotFound}
            usr <- User.findByUserId(userId) ?~! ErrorMessages.UserNotFoundById
            allowedEntitlements = CanGetEntitlementsForAnyUserAtOneBank ::
                                  CanGetEntitlementsForAnyUserAtAnyBank::
                                  Nil
            allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
            hasAtLeastOneEntitlement <- booleanToBox(hasAtLeastOneEntitlement(bankId.value, u.userId, allowedEntitlements), s"$allowedEntitlementsTxt entitlements required")
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

    resourceDocs += ResourceDoc(
      getConsumer,
      apiVersion,
      "getConsumer",
      "GET",
      "/management/consumers/CONSUMER_ID",
      "Get Consumer",
      s"""Get the Consumer specified by CONSUMER_ID.
        |
        |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)


    lazy val getConsumer: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanGetConsumers), s"$CanGetConsumers entitlement required")
            consumerIdToLong <- tryo{consumerId.toLong} ?~! "Invalid CONSUMER_ID"
            consumer <- Consumer.find(By(Consumer.id, consumerIdToLong))
          } yield {
            // Format the data as json
            val json = ConsumerJSON(consumer.id, consumer.name, consumer.appType.toString(), consumer.description, consumer.developerEmail, consumer.redirectURL, consumer.createdByUserId, consumer.isActive, consumer.createdAt)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getConsumers,
      apiVersion,
      "getConsumers",
      "GET",
      "/management/consumers",
      "Get Consumers",
      s"""Get the all Consumers.
          |
        |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)


    lazy val getConsumers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanGetConsumers), s"$CanGetConsumers entitlement required")
            consumers <- Some(Consumer.findAll())
          } yield {
            // Format the data as json
            val json = createConsumerJSONs(consumers.sortWith(_.id < _.id))
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      enableDisableConsumers,
      apiVersion,
      "enableDisableConsumers",
      "PUT",
      "/management/consumers/CONSUMER_ID",
      "Enable or Disable Consumers",
      s"""Enable/Disable a Consumer specified by CONSUMER_ID.
        |
        |""",
      Extraction.decompose(PutEnabledJSON(false)),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)


    lazy val enableDisableConsumers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            putData <- tryo{json.extract[PutEnabledJSON]} ?~! ErrorMessages.InvalidJsonFormat
            hasEntitlement <- putData.enabled match {
              case true  => booleanToBox(hasEntitlement("", u.userId, ApiRole.CanEnableConsumers), s"$CanEnableConsumers entitlement required")
              case false => booleanToBox(hasEntitlement("", u.userId, ApiRole.CanDisableConsumers), s"$CanDisableConsumers entitlement required")
            }
            consumer <- Consumer.find(By(Consumer.id, consumerId.toLong))
          } yield {
            // Format the data as json
            consumer.isActive(putData.enabled).save
            val json = PutEnabledJSON(consumer.isActive)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }



    resourceDocs += ResourceDoc(
      addCardsForBank,
      apiVersion,
      "addCardsForBank",
      "POST",
      "/banks/BANK_ID/cards",
      "Add cards for a bank",
      s"""Import bulk data into the sandbox (Authenticated access).
          |
          |This is can be used to create cards which are stored in the local RDBMS.
          |${authenticationRequiredMessage(true)}
          |""",
      Extraction.decompose(PostPhysicalCardJSON(bank_card_number="4012888888881881",
        name_on_card="Internet pay",
        issue_number="34",
        serial_number ="6546",
        valid_from_date=new Date(),
        expires_date=new Date(),
        enabled=true,
        cancelled=false,
        on_hot_list=false,
        technology ="",
        networks=List(),
        allows=List(),
        account_id="",
        replacement = ReplacementJSON(requested_date = new Date(), reason_requested = "stolen"),
        pin_reset=List(PinResetJSON(requested_date = new Date(), reason_requested = "routine_security"), PinResetJSON(requested_date = new Date(), reason_requested = "forgot")),
        collected=new Date(),
        posted=new Date() )),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val addCardsForBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            canCreateCardsForBank <- booleanToBox(hasEntitlement("", u.userId, CanCreateCardsForBank), s"CanCreateCardsForBank entitlement required")
            postJson <- tryo {json.extract[PostPhysicalCardJSON]} ?~ {ErrorMessages.InvalidJsonFormat}
            postedAllows <- postJson.allows match {
              case List() => booleanToBox(true)
              case _ => booleanToBox(postJson.allows.forall(a => CardAction.availableValues.contains(a))) ?~ {"Allowed values are: " + CardAction.availableValues.mkString(", ")}
            }
            account <- BankAccount(bankId, AccountId(postJson.account_id)) ?~! {ErrorMessages.AccountNotFound}
            card <- Connector.connector.vend.AddPhysicalCard(
                                bankCardNumber=postJson.bank_card_number,
                                nameOnCard=postJson.name_on_card,
                                issueNumber=postJson.issue_number,
                                serialNumber=postJson.serial_number,
                                validFrom=postJson.valid_from_date,
                                expires=postJson.expires_date,
                                enabled=postJson.enabled,
                                cancelled=postJson.cancelled,
                                onHotList=postJson.on_hot_list,
                                technology=postJson.technology,
                                networks= postJson.networks,
                                allows= postJson.allows,
                                accountId= postJson.account_id,
                                bankId=bankId.value,
                                replacement= None,
                                pinResets= List(),
                                collected= Option(CardCollectionInfo(postJson.collected)),
                                posted= Option(CardPostedInfo(postJson.posted))
                              )
          } yield {
            val cardJson = JSONFactory1_3_0.createPhysicalCardJSON(card, u)
            successJsonResponse(Extraction.decompose(cardJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUsers,
      apiVersion,
      "getUsers",
      "GET",
      "/users",
      "Get all Users",
      """Get all users
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getUsers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: Nil JsonGet _ => {
        user =>
          for {
            l <- user ?~ ErrorMessages.UserNotLoggedIn
            canGetAnyUser <- booleanToBox(hasEntitlement("", l.userId, ApiRole.CanGetAnyUser), "CanGetAnyUser entitlement required")
            users <- tryo{OBPUser.getApiUsers()}
          } yield {
            // Format the data as V2.0.0 json
            val json = JSONFactory200.createUserJSONs(users)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    val getTransactionTypesIsPublic = Props.getBool("apiOptions.getTransactionTypesIsPublic", true)

    resourceDocs += ResourceDoc(
      createTransactionType,
      apiVersion,
      "createTransactionType",
      "PUT",
      "/banks/BANK_ID/transaction-types",
      "Create Transaction Type offered by the bank",
      // TODO get the documentation of the parameters from the scala doc of the case class we return
      s"""Create Transaction Types for the bank specified by BANK_ID:
          |
          |  * id : Unique transaction type id across the API instance. SHOULD be a UUID. MUST be unique.
          |  * bank_id : The bank that supports this TransactionType
          |  * short_code : A short code (SHOULD have no-spaces) which MUST be unique across the bank. May be stored with Transactions to link here
          |  * summary : A succinct summary
          |  * description : A longer description
          |  * charge : The charge to the customer for each one of these
          |
          |${authenticationRequiredMessage(getTransactionTypesIsPublic)}""",
      Extraction.decompose(TransactionTypeJSON(TransactionTypeId("wuwjfuha234678"), "1", "2", "3", "4", AmountOfMoneyJSON("EUR", "123"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagBank)
    )



    lazy val createTransactionType: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "transaction-types" ::  Nil JsonPut json -> _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! ErrorMessages.BankNotFound
            postedData <- tryo {json.extract[TransactionTypeJSON]} ?~! ErrorMessages.InvalidJsonFormat
            cancreateTransactionType <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateTransactionType) == true,ErrorMessages.InsufficientAuthorisationToCreateTransactionType)
            returnTranscationType <- TransactionType.TransactionTypeProvider.vend.createOrUpdateTransactionType(postedData)
          } yield {
            successJsonResponse(Extraction.decompose(returnTranscationType))
          }
        }
      }
    }


    val getAtmsIsPublic = Props.getBool("apiOptions.getAtmsIsPublic", true)

    resourceDocs += ResourceDoc(
      getAtm,
      apiVersion,
      "getAtm",
      "GET",
      "/banks/BANK_ID/atms/ATM_ID",
      "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID including:
          |
          |* Address
          |* Geo Location
          |* License the data under this endpoint is released under
          |
          |${authenticationRequiredMessage(!getAtmsIsPublic)}""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getAtm: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet _ => {
        user => {
          for {
          // Get atm from the active provider
            u <- if (getAtmsIsPublic)
              Box(Some(1))
            else
              user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            atm  <- Box(Atms.atmsProvider.vend.getAtm(atmId)) ?~! {ErrorMessages.AtmNotFoundByAtmId}
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createAtmJson(atm)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    val getBranchesIsPublic = Props.getBool("apiOptions.getBranchesIsPublic", true)

    resourceDocs += ResourceDoc(
      getBranch,
      apiVersion,
      "getBranch",
      "GET",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Get Bank Branch",
      s"""Returns information about branches for a single bank specified by BANK_ID and BRANCH_ID including:
          |
          |* Name
          |* Address
          |* Geo Location
          |* License the data under this endpoint is released under
          |
        |${authenticationRequiredMessage(!getBranchesIsPublic)}""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId) :: Nil JsonGet _ => {
        user => {
          for {
            u <- if (getBranchesIsPublic)
              Box(Some(1))
            else
              user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            branch <- Box(Branches.branchesProvider.vend.getBranch(branchId)) ?~! {ErrorMessages.BranchNotFoundByBranchId}
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createBranchJson(branch)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    val getProductsIsPublic = Props.getBool("apiOptions.getProductsIsPublic", true)


    resourceDocs += ResourceDoc(
      getProduct,
      apiVersion,
      "getProduct",
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID and PRODUCT_CODE including:
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
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getProduct: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonGet _ => {
        user => {
          for {
          // Get product from the active provider
            u <- if (getProductsIsPublic)
              Box(Some(1))
            else
              user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            product <- Connector.connector.vend.getProduct(bankId, productCode)?~! {ErrorMessages.ProductNotFoundByProductCode}
          } yield {
            // Format the data as json
            val json = JSONFactory210.createProductJson(product)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

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
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getProducts : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet _ => {
        user => {
          for {
          // Get products from the active provider
            u <- if(getProductsIsPublic)
              Box(Some(1))
            else
              user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            products <- Connector.connector.vend.getProducts(bankId)?~!  {ErrorMessages.ProductNotFoundByProductCode}
          } yield {
            // Format the data as json
            val json = JSONFactory210.createProductsJson(products)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


    resourceDocs += ResourceDoc(
      createCounterparty,
      apiVersion,
      "createCounterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create counterparty for an account",
      s"""Create counterparty.
          |
          |Counterparties are created for the account / view
          |They are how the user of the view (e.g. account owner) refers to the other side of the transaction
          |
          |name is the human readable name (e.g. Piano teacher, Miss Nipa)
          |
          |other_bank_id is an (internal) ID for the bank of the bank of the counterparty (if known)
          |
          |other_account_id is an (internal) ID for the bank account of the counterparty (if known)
          |
          |other_account_provider is a code that tells the system where that bank is hosted. Will be OBP if its known to the API. Usage of this flag (in API / connectors) is work in progress.
          |
          |account_routing_scheme is a code that dictates the nature of the account_routing_address e.g. IBAN
          |
          |account_routing_address is an instance of account_routing_scheme that can be used to route payments to external systems. e.g. an IBAN number
          |
          |bank_routing_scheme is a code that dictates the nature of the bank_routing_address e.g. "BIC",
          |
          |bank_routing_address is an instance of bank_routing_scheme
          |
          |is_beneficiary must be set to true in order to send payments to this counterparty
          |
          |The view specified by VIEW_ID must have the canAddCounterparty permission
          |
          |${authenticationRequiredMessage(true)}
          |""",
      Extraction.decompose(PostCounterpartyJSON(
        name="",
        other_bank_id="",
        other_account_id="12345",
        other_account_provider="OBP",
        other_account_routing_scheme="IBAN",
        other_account_routing_address="7987987-2348987-234234",
        other_bank_routing_scheme="BIC",
        other_bank_routing_address="123456",
        is_beneficiary = true
      )),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List())


    lazy val createCounterparty: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidAccountIdFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! ErrorMessages.BankNotFound
            account <- BankAccount(bankId, AccountId(accountId.value)) ?~! {ErrorMessages.AccountNotFound}
            postJson <- tryo {json.extract[PostCounterpartyJSON]} ?~ {ErrorMessages.InvalidJsonFormat}
            availableViews <- Full(account.permittedViews(user))
            view <- View.fromUrl(viewId, account) ?~! {ErrorMessages.ViewNotFound}
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
            canAddCounterparty <- booleanToBox(view.canAddCounterparty == true, "Current view does not have permission for the action")
            checkAvailable <- tryo(assert(Counterparties.counterparties.vend.
              checkCounterpartyAvailable(postJson.name,bankId.value, accountId.value,viewId.value) == true)
            ) ?~! ErrorMessages.CounterpartyAlreadyExists
            couterparty <- Counterparties.counterparties.vend.createCounterparty(createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              name=postJson.name,
              otherBankId =postJson.other_bank_id,
              otherAccountId =postJson.other_account_id,
              otherAccountRoutingScheme=postJson.other_account_routing_scheme,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              isBeneficiary=postJson.is_beneficiary
            )
            metadata <- Counterparties.counterparties.vend.getMetadata(bankId, accountId, couterparty.counterPartyId) ?~ "Cannot find the metadata"
            moderated <- Connector.connector.vend.getCounterparty(bankId, accountId, couterparty.counterPartyId).flatMap(oAcc => view.moderate(oAcc))
          } yield {
            val list = createCounterpartJSON(moderated, metadata, couterparty)
            successJsonResponse(Extraction.decompose(list))
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
      Extraction.decompose(PostCustomerJson("user_id to attach this customer to e.g. 123213",
        "new customer number 687687678", "Joe David Bloggs",
        "+44 07972 444 876", "person@example.com",
        CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate),
        exampleDate,
        "Single",
        1,
        List(exampleDate),
        CustomerCreditRatingJSON(rating = "5", source = "Credit biro"),
        AmountOfMoneyJSON(currency = "EUR", amount = "5000"),
        "Bachelor’s Degree",
        "Employed",
        true,
        exampleDate)),
      emptyObjectJson,
      emptyObjectJson :: Nil,
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
            u <- user ?~! "User must be logged in to post Customer" // TODO. CHECK user has role to create a customer / create a customer for another user id.
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! ErrorMessages.InvalidBankIdFormat
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            postedData <- tryo{json.extract[PostCustomerJson]} ?~! ErrorMessages.InvalidJsonFormat
            requiredEntitlements = CanCreateCustomer ::
              CanCreateUserCustomerLink ::
              Nil
            requiredEntitlementsTxt = requiredEntitlements.mkString(" and ")
            hasEntitlements <- booleanToBox(hasAllEntitlements(bankId.value, u.userId, requiredEntitlements), s"$requiredEntitlementsTxt entitlements required")
            checkAvailable <- tryo(assert(Customer.customerProvider.vend.checkCustomerNumberAvailable(bankId, postedData.customer_number) == true)) ?~! ErrorMessages.CustomerNumberAlreadyExists
            user_id <- tryo (if (postedData.user_id.nonEmpty) postedData.user_id else u.userId) ?~ s"Problem getting user_id"
            customer_user <- User.findByUserId(user_id) ?~! ErrorMessages.UserNotFoundById
            userCustomerLinks <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinks
            //Find all user to customer links by user_id
            userCustomerLinks <- tryo(userCustomerLinks.filter(u => u.userId.equalsIgnoreCase(user_id)))
            customerIds: List[String] <-  tryo(userCustomerLinks.map(p => p.customerId))
            //Try to find an existing customer at BANK_ID
            alreadyHasCustomer <-booleanToBox(customerIds.forall(x => Customer.customerProvider.vend.getCustomer(x, bank.bankId).isEmpty == true)) ?~ ErrorMessages.CustomerAlreadyExistsForUser
            // TODO we still store the user inside the customer, we should only store the user in the usercustomer link
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
              postedData.last_ok_date,
              Option(MockCreditRating(postedData.credit_rating.rating, postedData.credit_rating.source)),
              Option(MockCreditLimit(postedData.credit_limit.currency, postedData.credit_limit.amount))) ?~! "Could not create customer"
            userCustomerLink <- booleanToBox(UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(user_id, customer.customerId).isEmpty == true) ?~ ErrorMessages.CustomerAlreadyExistsForUser
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(user_id, customer.customerId, exampleDate, true) ?~! "Could not create user_customer_links"
          } yield {
            val json = JSONFactory210.createCustomerJson(customer)
            val successJson = Extraction.decompose(json)
            successJsonResponse(successJson, 201)
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
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagCustomer))

    lazy val getCustomers : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            //bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            customerIds: List[String] <- tryo{UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(u.userId).map(x=>x.customerId)} ?~! ErrorMessages.CustomerDoNotExistsForUser
          } yield {
            val json = JSONFactory210.createCustomersJson(APIUtil.getCustomers(customerIds))
            println("APIUtil.getCustomers(customerIds) " + APIUtil.getCustomers(customerIds))
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

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
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer))

    lazy val getCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            ucls <- tryo{UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(u.userId)} ?~! ErrorMessages.CustomerDoNotExistsForUser
            ucl <- tryo{ucls.find(x=>Customer.customerProvider.vend.getBankIdByCustomerId(x.customerId) == bankId.value)}
            isEmpty <- booleanToBox(ucl.size > 0, ErrorMessages.CustomerDoNotExistsForUser)
            u <- ucl
            info <- Customer.customerProvider.vend.getCustomerByCustomerId(u.customerId) ?~! ErrorMessages.CustomerNotFoundByCustomerId
          } yield {
            val json = JSONFactory210.createCustomerJson(info)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      updateBranch,
      apiVersion,
      "updateBranch",
      "PUT",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Update Branch",
      s"""Update an existing branch for a bank account (Authenticated access).
         |${authenticationRequiredMessage(true)}
         |""",
      Extraction.decompose(BranchJsonPut("gh.29.fi", "OBP",
        AddressJson("VALTATIE 8", "", "", "AKAA", "", "", "37800"),
        LocationJson(1.2, 2.1),
        MetaJson(LicenseJson("","")),
        LobbyJson(""),
        DriveUpJson("")
      )),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val updateBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId)::  Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            branch <- tryo {json.extract[BranchJsonPut]} ?~ ErrorMessages.InvalidJsonFormat
            canCreateBranch <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true,ErrorMessages.InsufficientAuthorisationToCreateBranch)
            //package the BranchJsonPut to toBranchJsonPost, to call the createOrUpdateBranch method
            branchPost <- toBranchJsonPost(branchId,branch)
            success <- Connector.connector.vend.createOrUpdateBranch(branchPost)
          } yield {
            val json = JSONFactory1_4_0.createBranchJson(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createBranch,
      apiVersion,
      "createBranch",
      "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create branch for the bank (Authenticated access).
          |${authenticationRequiredMessage(true)}
          |""",
      Extraction.decompose(BranchJsonPost("123","gh.29.fi", "OBP",
        AddressJson("VALTATIE 8", "", "", "AKAA", "", "", "37800"),
        LocationJson(1.2, 2.1),
        MetaJson(LicenseJson("", "")),
        LobbyJson(""),
        DriveUpJson("")
      )),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))

    lazy val createBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)?~! {ErrorMessages.BankNotFound}
            branch <- tryo {json.extract[BranchJsonPost]} ?~ ErrorMessages.InvalidJsonFormat
            canCreateBranch <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true,ErrorMessages.InsufficientAuthorisationToCreateBranch)
            success <- Connector.connector.vend.createOrUpdateBranch(branch)
          } yield {
           val json = JSONFactory1_4_0.createBranchJson(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateConsumerRedirectUrl,
      apiVersion,
      "updateConsumerRedirectUrl",
      "PUT",
      "/management/consumers/CONSUMER_ID/users/USER_ID",
      "Update Consumer RedirectUrl",
      s"""Update an existing redirectUrl for a Consumer specified by CONSUMER_ID and USER_ID. """,
      Extraction.decompose(ConsumerRedirectUrlJSON("consumer_key_xxx","http://localhost:8888")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)
    
    lazy val updateConsumerRedirectUrl: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: "users" :: userId :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanUpdateConsumerRedirectUrl), s"$CanUpdateConsumerRedirectUrl entitlement required")
            postJson <- tryo {json.extract[ConsumerRedirectUrlJSON]} ?~ ErrorMessages.InvalidJsonFormat
            consumerIdToLong <- tryo{consumerId.toLong} ?~! "Invalid CONSUMER_ID. "
            consumer <- Connector.connector.vend.getConsumer(consumerIdToLong,postJson.consumer_key) ?~! {ErrorMessages.ConsumerNotFound}
            //only the developer that created the Consumer should be able to edit it
            isSameUseIDCreateTheConsumer <- tryo(assert(consumer.createdByUserId.equals(userId)))?~! "Only the developer that created the consumer key should be able to edit it, please use the right USER_ID! "
          } yield {
            //update the redirectURL and isactive (set to false when change redirectUrl) field in consumer table 
            val success = consumer.redirectURL(postJson.redirect_url).isActive(false).saveMe()
            val json = JSONFactory210.createConsumerJSON(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }
  }
}

object APIMethods210 {
}