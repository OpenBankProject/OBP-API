package code.api.v2_1_0

import java.text.SimpleDateFormat
import java.util.Date

import code.TransactionTypes.TransactionType
import code.api.util.ApiRole._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_3_0.{JSONFactory1_3_0, _}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, TransactionRequestAccountJSON}
import code.api.v2_0_0._
import code.api.v2_1_0.JSONFactory210._
import code.atms.Atms
import code.atms.Atms.AtmId
import code.bankconnectors.Connector
import code.branches.Branches
import code.branches.Branches.BranchId
import code.entitlement.Entitlement
import code.fx.fx
import code.metadata.counterparties.MappedCounterpartyMetadata
import code.metadata.counterparties.Counterparties
import code.model.dataAccess.OBPUser
import code.model.{BankId, ViewId, _}
import code.products.Products
import code.products.Products.ProductCode
import net.liftweb.http.{CurrentReq, Req}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.Serialization._
import net.liftweb.mapper.By
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
      Catalogs(notCore,notPSD2,notOBWG),
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
      Catalogs(notCore,notPSD2,notOBWG),
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
        TransactionRequestAccountJSON("bank_id", "account_id"),
        AmountOfMoneyJSON("eur", "100.53"),
        "A description for the transaction to be created"
      )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core,PSD2,OBWG),
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
              // Check transactionRequestType is not "TRANSACTION_REQUEST_TYPE" which is the place holder (probably redundant because of check below)
              // Check that transactionRequestType is included in the Props
              isValidTransactionRequestType <- tryo(assert(transactionRequestType.value != "TRANSACTION_REQUEST_TYPE" && validTransactionRequestTypesList.contains(transactionRequestType.value))) ?~! s"${ErrorMessages.InvalidTransactionRequestType} : The invalid value is: '${transactionRequestType.value}' Valid values are: ${validTransactionRequestTypes}"

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
                case "SEPA" => {
                  for {
                  //for SEPA, the user do not send the Bank_ID and Acound_ID,so this will search for the bank firstly.
                    toIban<-  Full(transDetailsJson.asInstanceOf[TransactionRequestDetailsSEPAJSON].iban)
                    counterpartiesFields <- Counterparties.counterparties.vend.getCounterpartyByIban(toIban) ?~! {ErrorMessages.CounterpartyNotFoundByIban}
                    isBeneficiary <- booleanToBox(counterpartiesFields.isBeneficiary == true , ErrorMessages.CounterpartyBeneficiaryPermit)
                    toBankId <- Full(BankId(counterpartiesFields.otherBankId ))
                    toAccountId <- Full(AccountId(counterpartiesFields.otherAccountId))
                    toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}

                    // Following four lines: just transfer the details body ,add Bank_Id and Account_Id in the Detail part.
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
                    // Following three lines: just transfer the details body ,add Bank_Id and Account_Id in the Detail part.
                    transactionRequestAccountJSON <- Full(TransactionRequestAccountJSON(fromAccount.bankId.value, fromAccount.accountId.value))
                    // the Free form the discription is empty, so make it "" in the following code
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
      Catalogs(Core,PSD2,OBWG),
      List(apiTagTransactionRequest))

    lazy val answerTransactionRequestChallenge: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: TransactionRequestId(transReqId) :: "challenge" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u: User <- user ?~ ErrorMessages.UserNotLoggedIn
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
      Catalogs(Core,PSD2,OBWG),
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
      Catalogs(Core,PSD2,OBWG),
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
      Catalogs(Core,PSD2,OBWG),
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
      Catalogs(notCore,notPSD2,notOBWG),
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
            val json = ConsumerJSON(consumer.id, consumer.name, consumer.appType.toString(), consumer.description, consumer.developerEmail, consumer.isActive, consumer.createdAt)
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
      Catalogs(notCore,notPSD2,notOBWG),
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
      Catalogs(notCore,notPSD2,notOBWG),
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
      Catalogs(notCore,notPSD2,notOBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val addCardsForBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            canCreateCardsForBank <- booleanToBox(hasEntitlement("", u.userId, CanCreateCardsForBank), s"CanCreateCardsForBank entitlement required")
            postJson <- tryo {json.extract[PostPhysicalCardJSON]} ?~ "invalid json"
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
      Catalogs(Core,notPSD2,notOBWG),
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
      Extraction.decompose(TransactionTypeJSON(TransactionTypeId("wuwjfuha234678"), "1", "2", "3", "4", AmountOfMoneyJSON("eur", "123"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore,notPSD2,notOBWG),
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
      Catalogs(notCore,notPSD2,OBWG),
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
      Catalogs (notCore,notPSD2,OBWG),
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
      Catalogs(notCore,notPSD2,OBWG),
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
            product <- Box(Products.productsProvider.vend.getProduct(bankId, productCode, true)) ?~! {ErrorMessages.ProductNotFoundByProductCode}
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createProductJson(product)
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
      s"""(Authenticated access).
          |
          |Create a counterparty.
          |${authenticationRequiredMessage(true)}
          |""",
      Extraction.decompose(PostCounterpartyJSON(
        name = "",
        counterparty_bank_id ="",
        primary_routing_scheme="IBAN",
        primary_routing_address="7987987-2348987-234234")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore,notPSD2,notOBWG),
      List())


    lazy val createCounterparty: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! ErrorMessages.BankNotFound
            account <- BankAccount(bankId, AccountId(accountId.value)) ?~! {ErrorMessages.AccountNotFound}
            postJson <- tryo {json.extract[PostCounterpartyJSON]} ?~ "invalid json"
            couterparty <- Counterparties.counterparties.vend.addCounterparty(createdByUserId=u.userId,
              bankId=bankId.value,
              accountId=accountId.value,
              name=postJson.name,
              counterPartyBankId =postJson.counterparty_bank_id,
              primaryRoutingScheme=postJson.primary_routing_scheme,
              primaryRoutingAddress=postJson.primary_routing_address)
            metadata <- Counterparties.counterparties.vend.getMetadata(bankId, accountId, couterparty.counterPartyId) ?~ "Cannot find the metadata"
            availableViews <- Full(account.permittedViews(user))
            view <- View.fromUrl(viewId, account) ?~! {ErrorMessages.ViewNotFound}
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
            moderated <- Connector.connector.vend.getCounterparty(bankId, accountId, couterparty.counterPartyId).flatMap(oAcc => view.moderate(oAcc))
          } yield {
            val list = createCounterpartJSON(moderated, metadata, couterparty)
            successJsonResponse(Extraction.decompose(list))
          }
      }
    }
  }
}

object APIMethods210 {
}
