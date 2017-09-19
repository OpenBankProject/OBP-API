package code.api.v2_1_0

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.TransactionTypes.TransactionType
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.TransactionDisabled
import code.api.util.{APIUtil, ApiRole}
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v1_3_0.{JSONFactory1_3_0, _}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0._
import code.api.v2_1_0.JSONFactory210._
import code.api.v2_2_0.JSONFactory220
import code.atms.Atms
import code.atms.Atms.AtmId
import code.bankconnectors.{OBPQueryParam, _}
import code.branches.Branches
import code.branches.Branches.BranchId
import code.consumer.Consumers
import code.customer.{Customer, MockCreditLimit, MockCreditRating, MockCustomerFaceImage}
import code.entitlement.Entitlement
import code.fx.fx
import code.metadata.counterparties.Counterparties
import code.metrics.{APIMetric, APIMetrics}
import code.model.dataAccess.{AuthUser, MappedBankAccount, ResourceUser}
import code.model.{BankAccount, BankId, ViewId, _}
import code.products.Products.ProductCode
import code.transactionrequests.TransactionRequests
import code.usercustomerlinks.UserCustomerLink
import code.api.util.APIUtil.getCustomers
import code.transactionChallenge.ExpectedChallengeAnswer
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.util.Helper.booleanToBox
import net.liftweb.http.{Req, S}
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.{APIFailure, ChargePolicy}
import code.metadata.counterparties._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import code.util.Helper._
import net.liftweb.common.{Box, Full}
import net.liftweb.http.JsonResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import net.liftweb.util.Helpers._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._


trait APIMethods210 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  // helper methods end here

  val Implementations2_1_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson = EmptyClassJson()
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
      successMessage,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        DataImportDisabled,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val sandboxDataImport: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Import data into the sandbox
      case "sandbox" :: "data-import" :: Nil JsonPost json -> _ => {
        user =>
          for {
            importData <- tryo {json.extract[SandboxDataImport]} ?~! {InvalidJsonFormat}
            u <- user ?~! UserNotLoggedIn
            allowDataImportProp <- Props.get("allow_sandbox_data_import") ~> APIFailure(DataImportDisabled, 403)
            allowDataImport <- Helper.booleanToBox(allowDataImportProp == "true") ~> APIFailure(DataImportDisabled, 403)
            canCreateSandbox <- booleanToBox(hasEntitlement("", u.userId, CanCreateSandbox), s"$UserHasMissingRoles $CanCreateSandbox")
            importWorked <- OBPDataImport.importer.vend.importData(importData)
          } yield {
            successJsonResponse(Extraction.decompose(successMessage), 201)
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
      transactionRequestTypesJSON,
      List(UserNotLoggedIn, UnknownError),
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
              user ?~! UserNotLoggedIn
            bank <- Bank(bankId) ?~! {BankNotFound}
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


    import net.liftweb.json.Extraction._
    import net.liftweb.json.JsonAST._
    val exchangeRates = prettyRender(decompose(fx.exchangeRates))


    // This text is used in the various Create Transaction Request resource docs
    val transactionRequestGeneralText =
      s"""Initiate a Payment via creating a Transaction Request.
          |
          |In OBP, a `transaction request` may or may not result in a `transaction`. However, a `transaction` only has one possible state: completed.
          |
          |A `Transaction Request` can have one of several states.
          |
          |`Transactions` are modeled on items in a bank statement that represent the movement of money.
          |
          |`Transaction Requests` are requests to move money which may or may not succeeed and thus result in a `Transaction`.
          |
          |A `Transaction Request` might create a security challenge that needs to be answered before the `Transaction Request` proceeds.
          |
          |Transaction Requests contain charge information giving the client the opportunity to proceed or not (as long as the challenge level is appropriate).
          |
          |Transaction Requests can have one of several Transaction Request Types which expect different bodies. The escaped body is returned in the details key of the GET response.
          |This provides some commonality and one URL for many different payment or transfer types with enough flexibility to validate them differently.
          |
          |The payer is set in the URL. Money comes out of the BANK_ID and ACCOUNT_ID specified in the URL.
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
          |
          |Transaction Requests satisfy PSD2 requirements thus:
          |
          |1) A transaction can be initiated by a third party application.
          |
          |2) The customer is informed of the charge that will incurred.
          |
          |3) The call supports delegated authentication (OAuth)
          |
          |See [this python code](https://github.com/OpenBankProject/Hello-OBP-DirectLogin-Python/blob/master/hello_payments.py) for a complete example of this flow.
          |
          |There is further documentation [here](https://github.com/OpenBankProject/OBP-API/wiki/Transaction-Requests)
          |
          |${authenticationRequiredMessage(true)}
          |
          |"""




    // SANDBOX_TAN. (we no longer create a resource doc for the general case)
    resourceDocs += ResourceDoc(
      createTransactionRequestSandboxTan,
      apiVersion,
      "createTransactionRequestSandboxTan",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SANDBOX_TAN/transaction-requests",
      "Create Transaction Request (SANDBOX_TAN)",
      s"""When using SANDBOX_TAN, the payee is set in the request body.
         |
         |Money goes into the BANK_ID and ACCOUNT_ID specified in the request body.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyJsonV200,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    // COUNTERPARTY
    resourceDocs += ResourceDoc(
      createTransactionRequestCouterparty,
      apiVersion,
      "createTransactionRequestCouterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests",
      "Create Transaction Request (COUNTERPARTY)",
      s"""
         |Special instructions for COUNTERPARTY:
         |
         |When using a COUNTERPARTY to create a Transaction Request, specificy the counterparty_id in the body of the request.
         |The routing details of the counterparty will be forwarded for the transfer.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyCounterpartyJSON,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))


    val lowAmount  = AmountOfMoneyJsonV121("EUR", "12.50")
    val sharedChargePolicy = ChargePolicy.withName("SHARED")

    // Transaction Request (SEPA)
    resourceDocs += ResourceDoc(
      createTransactionRequestSepa,
      apiVersion,
      "createTransactionRequestSepa",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests",
      "Create Transaction Request (SEPA)",
      s"""
         |Special instructions for SEPA:
         |
         |When using a SEPA Transaction Request, you specify the IBAN of a Counterparty in the body of the request.
         |The routing details (IBAN) of the counterparty will be forwarded to the core banking system for the transfer.
         |
         |$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodySEPAJSON,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))


    // FREE_FORM.
    resourceDocs += ResourceDoc(
      createTransactionRequestFreeForm,
      apiVersion,
      "createTransactionRequestFreeForm",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests",
      "Create Transaction Request (FREE_FORM).",
      s"""$transactionRequestGeneralText
         |
       """.stripMargin,
      transactionRequestBodyJsonV200,
      transactionRequestWithChargeJSON210,
      List(
        UserNotLoggedIn,
        UserNotLoggedIn,
        InvalidBankIdFormat,
        InvalidAccountIdFormat,
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        ViewNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        UserNoPermissionAccessView,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))




    // Different Transaction Request approaches:
    lazy val createTransactionRequestSandboxTan = createTransactionRequest
    lazy val createTransactionRequestSepa = createTransactionRequest
    lazy val createTransactionRequestCouterparty = createTransactionRequest
    lazy val createTransactionRequestFreeForm = createTransactionRequest

    // This handles the above cases
    lazy val createTransactionRequest: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        TransactionRequestType(transactionRequestType) :: "transaction-requests" :: Nil JsonPost json -> _ => {
        user =>
          for {
            _ <- booleanToBox(Props.getBool("transactionRequests_enabled", false)) ?~ TransactionDisabled
            u <- user ?~ UserNotLoggedIn
            _ <- tryo(assert(isValidID(accountId.value))) ?~! InvalidAccountIdFormat
            _ <- tryo(assert(isValidID(bankId.value))) ?~! InvalidBankIdFormat
            _ <- Bank(bankId) ?~! {BankNotFound}
            fromAccount <- BankAccount(bankId, accountId) ?~! {AccountNotFound}
            _ <- View.fromUrl(viewId, fromAccount) ?~! {ViewNotFound}
            isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true ||
              hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true, InsufficientAuthorisationToCreateTransactionRequest)
            _ <- tryo(assert(Props.get("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value))) ?~!
              s"${InvalidTransactionRequestType}: '${transactionRequestType.value}'"

            // Check the input JSON format, here is just check the common parts of all four tpyes
            transDetailsJson <- tryo {json.extract[TransactionRequestBodyCommonJSON]} ?~! InvalidJsonFormat
            isValidAmountNumber <- tryo(BigDecimal(transDetailsJson.value.amount)) ?~! InvalidNumber
            _ <- booleanToBox(isValidAmountNumber > BigDecimal("0"), NotPositiveAmount)
            _ <- tryo(assert(isValidCurrencyISOCode(transDetailsJson.value.currency))) ?~! InvalidISOCurrencyCode

            // Prevent default value for transaction request type (at least).
            _ <- tryo(assert(transDetailsJson.value.currency == fromAccount.currency)) ?~! {s"${InvalidTransactionRequestCurrency} " +
              s"From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.value.currency}"}
            amountOfMoneyJSON <- Full(AmountOfMoneyJsonV121(transDetailsJson.value.currency, transDetailsJson.value.amount))

            isMapped: Boolean <- Full((Props.get("connector").openOrThrowException("Attempted to open an empty Box.").toString).equalsIgnoreCase("mapped"))

            createdTransactionRequest <- TransactionRequestTypes.withName(transactionRequestType.value) match {
              case SANDBOX_TAN => {
                for {
                  transactionRequestBodySandboxTan <- tryo(json.extract[TransactionRequestBodySandBoxTanJSON]) ?~! s"${InvalidJsonFormat}, it should be SANDBOX_TAN input format"
                  toBankId <- Full(BankId(transactionRequestBodySandboxTan.to.bank_id))
                  toAccountId <- Full(AccountId(transactionRequestBodySandboxTan.to.account_id))
                  toAccount <- BankAccount(toBankId, toAccountId) ?~! {CounterpartyNotFound}
                  transDetailsSerialized <- tryo {write(transactionRequestBodySandboxTan)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     toAccount,
                                                                                                     new MappedCounterparty(), //in SANDBOX_TAN, toCounterparty is empty
                                                                                                     transactionRequestType,
                                                                                                     transactionRequestBodySandboxTan,
                                                                                                     transDetailsSerialized,
                                                                                                     sharedChargePolicy.toString) //in SANDBOX_TAN, ChargePolicy set default "SHARED"
                } yield createdTransactionRequest
              }
              case COUNTERPARTY => {
                for {
                  //For COUNTERPARTY, Use the counterpartyId to find the toCounterparty and set up the toAccount
                  transactionRequestBodyCounterparty <- tryo {json.extract[TransactionRequestBodyCounterpartyJSON]} ?~! s"${InvalidJsonFormat}, it should be COUNTERPARTY input format"
                  toCounterpartyId <- Full(transactionRequestBodyCounterparty.to.counterparty_id)
                  // Get the Counterparty by id
                  toCounterparty <- Connector.connector.vend.getCounterpartyByCounterpartyId(CounterpartyId(toCounterpartyId)) ?~! {CounterpartyNotFoundByCounterpartyId}

                  // Check we can send money to it.
                  _ <- booleanToBox(toCounterparty.isBeneficiary == true, CounterpartyBeneficiaryPermit)

                  // Get the Routing information from the Counterparty for the payment backend
                  toBankId <- Full(BankId(toCounterparty.otherBankRoutingAddress))
                  toAccountId <-Full(AccountId(toCounterparty.otherAccountRoutingAddress))

                  // Use otherAccountRoutingScheme and otherBankRoutingScheme to determine how we validate the toBank and toAccount.
                  // i.e. Only validate toBankId and toAccountId if they are both OBP
                  // i.e. if it is OBP we can expect the account to exist locally.
                  // This is so developers can follow the COUNTERPARTY flow in the sandbox

                  //if it is OBP, we call the local database, just for sandbox test case
                  toAccount <- if(toCounterparty.otherAccountRoutingScheme =="OBP" && toCounterparty.otherBankRoutingScheme=="OBP")
                    LocalMappedConnector.createOrUpdateMappedBankAccount(toBankId, toAccountId, fromAccount.currency)
                  //if it is remote, we do not need the bankaccount, we just send the counterparty to remote, remote make the transaction
                  else
                    Full(new MappedBankAccount())

                  // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                  transactionRequestAccountJSON = TransactionRequestAccountJsonV140(toAccount.bankId.value, toAccount.accountId.value)
                  chargePolicy = transactionRequestBodyCounterparty.charge_policy
                  _ <-tryo(assert(ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy)))) ?~! InvalidChargePolicy
                  transactionRequestDetailsMapperCounterparty = TransactionRequestDetailsMapperCounterpartyJSON(toCounterpartyId.toString,
                                                                                                                transactionRequestAccountJSON,
                                                                                                                amountOfMoneyJSON,
                                                                                                                transactionRequestBodyCounterparty.description,
                                                                                                                transactionRequestBodyCounterparty.charge_policy)
                  transDetailsSerialized <- tryo {write(transactionRequestDetailsMapperCounterparty)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     toAccount,
                                                                                                     toCounterparty,
                                                                                                     transactionRequestType,
                                                                                                     transactionRequestBodyCounterparty,
                                                                                                     transDetailsSerialized,
                                                                                                     chargePolicy)
                } yield createdTransactionRequest

              }
              case SEPA => {
                for {
                  //For SEPA, Use the iban to find the toCounterparty and set up the toAccount
                  transDetailsSEPAJson <- tryo {json.extract[TransactionRequestBodySEPAJSON]} ?~! s"${InvalidJsonFormat}, it should be SEPA input format"
                  toIban <- Full(transDetailsSEPAJson.to.iban)
                  toCounterparty <- Connector.connector.vend.getCounterpartyByIban(toIban) ?~! {CounterpartyNotFoundByIban}
                  _ <- booleanToBox(toCounterparty.isBeneficiary == true, CounterpartyBeneficiaryPermit)
                  toBankId <- Full(BankId(toCounterparty.otherBankRoutingAddress))
                  toAccountId <-Full(AccountId(toCounterparty.otherAccountRoutingAddress))

                  //if the connector is mapped, we get the data from local mapper
                  toAccount <- if(isMapped)
                    // TODO should not create bank account here!!
                    LocalMappedConnector.createOrUpdateMappedBankAccount(toBankId, toAccountId, fromAccount.currency)
                  else
                  //if it is remote, we do not need the bankaccount, we just send the counterparty to remote, remote make the transaction
                    Full(new MappedBankAccount())

                  // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                  transactionRequestAccountJSON = TransactionRequestAccountJsonV140(toAccount.bankId.value, toAccount.accountId.value)
                  chargePolicy = transDetailsSEPAJson.charge_policy
                  _ <-tryo(assert(ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))))?~! {InvalidChargePolicy}
                  transactionRequestDetailsSEPARMapperJSON = TransactionRequestDetailsMapperSEPAJSON(toIban.toString,
                                                                                                      transactionRequestAccountJSON,
                                                                                                      amountOfMoneyJSON,
                                                                                                      transDetailsSEPAJson.description,
                                                                                                      chargePolicy)
                  transDetailsSerialized <- tryo {write(transactionRequestDetailsSEPARMapperJSON)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     toAccount,
                                                                                                     toCounterparty,
                                                                                                     transactionRequestType,
                                                                                                     transDetailsSEPAJson,
                                                                                                     transDetailsSerialized,
                                                                                                     chargePolicy)
                } yield createdTransactionRequest
              }
              case FREE_FORM => {
                for {
                  transactionRequestBodyFreeForm <- Full(json.extract[TransactionRequestBodyFreeFormJSON]) ?~! s"${InvalidJsonFormat}, it should be FREE_FORM input format"
                  // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                  transactionRequestAccountJSON <- Full(TransactionRequestAccountJsonV140(fromAccount.bankId.value, fromAccount.accountId.value))
                  transactionRequestDetailsMapperFreeForm = TransactionRequestDetailsMapperFreeFormJSON(transactionRequestAccountJSON,
                                                                                                        amountOfMoneyJSON,
                                                                                                        transactionRequestBodyFreeForm.description)
                  transDetailsSerialized <- tryo {write(transactionRequestDetailsMapperFreeForm)(Serialization.formats(NoTypeHints))}
                  createdTransactionRequest <- Connector.connector.vend.createTransactionRequestv210(u,
                                                                                                     viewId,
                                                                                                     fromAccount,
                                                                                                     fromAccount,//in FREE_FORM, we only use toAccount == fromAccount
                                                                                                     new MappedCounterparty(), //in FREE_FORM, we only use toAccount, toCounterparty is empty
                                                                                                     transactionRequestType,
                                                                                                     transactionRequestBodyFreeForm,
                                                                                                     transDetailsSerialized,
                                                                                                     sharedChargePolicy.toString)
                } yield
                  createdTransactionRequest
              }
            }
          } yield {
            val json = JSONFactory210.createTransactionRequestWithChargeJSON(createdTransactionRequest)
            createdJsonResponse(Extraction.decompose(json))
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
      challengeAnswerJSON,
      transactionRequestWithChargeJson,
      List(
        UserNotLoggedIn,
        InvalidBankIdFormat, 
        InvalidAccountIdFormat, 
        InvalidJsonFormat,
        BankNotFound,
        UserNoPermissionAccessView,
        TransactionRequestStatusNotInitiated,
        TransactionRequestTypeHasChanged,
        InvalidTransactionRequesChallengeId,
        AllowedAttemptsUsedUp,
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
              // Check we have a User
              u: User <- user ?~ UserNotLoggedIn

              // Check format of bankId supplied in URL
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat

              // Check format of accountId supplied in URL
              isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! InvalidAccountIdFormat

              // Check the Posted JSON is valid
              challengeAnswerJson <- tryo{json.extract[ChallengeAnswerJSON]} ?~ {InvalidJsonFormat}

              // Check Bank exists on this server
              fromBank <- Bank(bankId) ?~! {BankNotFound}

              // Check Account exists on this server
              fromAccount <- BankAccount(bankId, accountId) ?~! {BankAccountNotFound}

              // Check User has access to the View
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~ {UserNoPermissionAccessView}

              // Check transReqId is valid
              existingTransactionRequest <- Connector.connector.vend.getTransactionRequestImpl(transReqId) ?~! {InvalidTransactionRequestId}

              // Check the Transaction Request is still INITIATED
              isTransReqStatueInitiated <- booleanToBox(existingTransactionRequest.status.equals("INITIATED"),TransactionRequestStatusNotInitiated)

              // Check the input transactionRequestType is the same as when the user created the TransactionRequest
              existingTransactionRequestType <- Full(existingTransactionRequest.`type`)
              isSameTransReqType <- booleanToBox(existingTransactionRequestType.equals(transactionRequestType.value),s"${TransactionRequestTypeHasChanged} It should be :'$existingTransactionRequestType' ")

              // Check the challengeId is valid for this existingTransactionRequest
              isSameChallengeId <- booleanToBox(existingTransactionRequest.challenge.id.equals(challengeAnswerJson.id),{InvalidTransactionRequesChallengeId})
            
              //Check the allowed attemps, Note: not support yet, the default value is 3
              allowedAttemptOK <- booleanToBox((existingTransactionRequest.challenge.allowed_attempts > 0),AllowedAttemptsUsedUp)

              //Check the challenge type, Note: not support yet, the default value is SANDBOX_TAN
              challengeTypeOK <- booleanToBox((existingTransactionRequest.challenge.challenge_type == TransactionRequests.CHALLENGE_SANDBOX_TAN),AllowedAttemptsUsedUp)
            
              challengeAnswerOBP <- ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.validateChallengeAnswerInOBPSide(challengeAnswerJson.id, challengeAnswerJson.answer)
              challengeAnswerOBPOK <- booleanToBox((challengeAnswerOBP == true),InvalidChallengeAnswer)

              challengeAnswerKafka <- Connector.connector.vend.validateChallengeAnswer(challengeAnswerJson.id, challengeAnswerJson.answer)
              challengeAnswerKafkaOK <- booleanToBox((challengeAnswerKafka == true),InvalidChallengeAnswer)

              // All Good, proceed with the Transaction creation...
              transactionRequest <- Connector.connector.vend.createTransactionAfterChallengev210(u, transReqId, transactionRequestType)
            } yield {
              // Format explicitly as v2.0.0 json
              val json = JSONFactory200.createTransactionRequestWithChargeJSON(transactionRequest)
              //successJsonResponse(Extraction.decompose(json))
              val successJson = Extraction.decompose(json)
              successJsonResponse(successJson, 202)
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
      transactionRequestWithChargeJSONs210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        AccountNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))

    lazy val getTransactionRequests: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        user =>
          if (Props.getBool("transactionRequests_enabled", false)) {
            for {
              u <- user ?~ UserNotLoggedIn
              fromBank <- Bank(bankId) ?~! {BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {AccountNotFound}
              view <- tryo(fromAccount.permittedViews(user).find(_ == viewId)) ?~! {UserHasMissingRoles + viewId}
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
      availableRolesJSON,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagUser, apiTagEntitlement))

    lazy val getRoles: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "roles" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ UserNotLoggedIn
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
      entitlementJSONs,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagUser, apiTagEntitlement))


    lazy val getEntitlementsByBankAndUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "users" :: userId :: "entitlements" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~ UserNotLoggedIn
            bank <- Bank(bankId) ?~ {BankNotFound}
            usr <- User.findByUserId(userId) ?~! UserNotFoundById
            allowedEntitlements = CanGetEntitlementsForAnyUserAtOneBank ::
                                  CanGetEntitlementsForAnyUserAtAnyBank::
                                  Nil
            allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
            hasAtLeastOneEntitlement <- booleanToBox(hasAtLeastOneEntitlement(bankId.value, u.userId, allowedEntitlements), UserHasMissingRoles+allowedEntitlementsTxt)
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
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
      consumerJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidConsumerId,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)


    lazy val getConsumer: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanGetConsumers), UserHasMissingRoles + CanGetConsumers)
            consumerIdToLong <- tryo{consumerId.toLong} ?~! InvalidConsumerId
            consumer <- Consumers.consumers.vend.getConsumerByPrimaryId(consumerIdToLong)
          } yield {
            val json = createConsumerJSON(consumer)
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
      consumersJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)


    lazy val getConsumers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: Nil JsonGet _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanGetConsumers), UserHasMissingRoles + CanGetConsumers )
            consumers <- Some(Consumer.findAll())
          } yield {
            // Format the data as json
            val json = createConsumerJSONs(consumers.sortWith(_.id.get < _.id.get))
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
      putEnabledJSON,
      putEnabledJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)


    lazy val enableDisableConsumers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            putData <- tryo{json.extract[PutEnabledJSON]} ?~! InvalidJsonFormat
            _ <- putData.enabled match {
              case true  => booleanToBox(hasEntitlement("", u.userId, ApiRole.CanEnableConsumers), UserHasMissingRoles + CanEnableConsumers )
              case false => booleanToBox(hasEntitlement("", u.userId, ApiRole.CanDisableConsumers),UserHasMissingRoles + CanDisableConsumers )
            }
            consumer <- Consumers.consumers.vend.getConsumerByPrimaryId(consumerId.toLong)
            updatedConsumer <- Consumers.consumers.vend.updateConsumer(consumer.id.get, None, None, Some(putData.enabled), None, None, None, None, None, None) ?~! "Cannot update Consumer"
          } yield {
            // Format the data as json
            val json = PutEnabledJSON(updatedConsumer.isActive.get)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }



    resourceDocs += ResourceDoc(
      addCardForBank,
      apiVersion,
      "addCardsForBank",
      "POST",
      "/banks/BANK_ID/cards",
      "Create Card",
      s"""Create Card at bank specified by BANK_ID .
          |
          |${authenticationRequiredMessage(true)}
          |""",
      postPhysicalCardJSON,
      physicalCardJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCard, apiTagPrivateData, apiTagPublicData))


    lazy val addCardForBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
            canCreateCardsForBank <- booleanToBox(hasEntitlement(bankId.value, u.userId, CanCreateCardsForBank), UserHasMissingRoles +CanCreateCardsForBank)
            postJson <- tryo {json.extract[PostPhysicalCardJSON]} ?~! {InvalidJsonFormat}
            postedAllows <- postJson.allows match {
              case List() => booleanToBox(true)
              case _ => booleanToBox(postJson.allows.forall(a => CardAction.availableValues.contains(a))) ?~! {"Allowed values are: " + CardAction.availableValues.mkString(", ")}
            }
            account <- BankAccount(bankId, AccountId(postJson.account_id)) ?~! {AccountNotFound}
            card <- Connector.connector.vend.createOrUpdatePhysicalCard(
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
      usersJSONV200,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getUsers: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: Nil JsonGet _ => {
        user =>
          for {
            l <- user ?~ UserNotLoggedIn
            canGetAnyUser <- booleanToBox(hasEntitlement("", l.userId, ApiRole.CanGetAnyUser), UserHasMissingRoles +CanGetAnyUser )
            users <- tryo{AuthUser.getResourceUsers()}
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
      transactionTypeJsonV200,
      transactionType,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        InsufficientAuthorisationToCreateTransactionType,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagBank)
    )



    lazy val createTransactionType: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "transaction-types" ::  Nil JsonPut json -> _ => {
        user => {
          for {
            u <- user ?~! UserNotLoggedIn
            bank <- Bank(bankId) ?~! BankNotFound
            postedData <- tryo {json.extract[TransactionTypeJsonV200]} ?~! InvalidJsonFormat
            cancreateTransactionType <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateTransactionType) == true,InsufficientAuthorisationToCreateTransactionType)
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
      atmJson,
      List(UserNotLoggedIn, BankNotFound, AtmNotFoundByAtmId, UnknownError),
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
              user ?~! UserNotLoggedIn
            bank <- Bank(bankId) ?~! {BankNotFound}
            atm  <- Box(Atms.atmsProvider.vend.getAtm(bankId, atmId)) ?~! {AtmNotFoundByAtmId}
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
          | meta.license.id and eta.license.name fields must not be empty. 
          |
          |* Name
          |* Address
          |* Geo Location
          |* License the data under this endpoint is released under
          |
        |${authenticationRequiredMessage(!getBranchesIsPublic)}""",
      emptyObjectJson,
      branchJson,
      List(
        UserNotLoggedIn, 
        "License may not be set. meta.license.id and eta.license.name can not be empty",
        UnknownError
      ),
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
              user ?~! UserNotLoggedIn
            bank <- Bank(bankId) ?~! {BankNotFound}
            branch <- Box(Branches.branchesProvider.vend.getBranch(bankId, branchId)) ?~! s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and meta.license.name can not be empty"
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
      productJsonV210,
      List(
        UserNotLoggedIn,
        ProductNotFoundByProductCode,
        UnknownError
      ),
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
              user ?~! UserNotLoggedIn
            bank <- Bank(bankId) ?~! {BankNotFound}
            product <- Connector.connector.vend.getProduct(bankId, productCode)?~! {ProductNotFoundByProductCode}
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
      productsJsonV210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        ProductNotFoundByProductCode,
        UnknownError
      ),
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
              user ?~! UserNotLoggedIn
            bank <- Bank(bankId) ?~! {BankNotFound}
            products <- Connector.connector.vend.getProducts(bankId)?~!  {ProductNotFoundByProductCode}
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
      postCounterpartyJSON,
      counterpartyJsonV220,
      List(
        UserNotLoggedIn,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        BankNotFound,
        AccountNotFound,
        InvalidJsonFormat,
        ViewNotFound,
        CounterpartyAlreadyExists,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List())


    lazy val createCounterparty: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! InvalidAccountIdFormat
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
            bank <- Bank(bankId) ?~! BankNotFound
            account <- BankAccount(bankId, AccountId(accountId.value)) ?~! {AccountNotFound}
            postJson <- tryo {json.extract[PostCounterpartyJSON]} ?~! {InvalidJsonFormat}
            availableViews <- Full(account.permittedViews(user))
            view <- View.fromUrl(viewId, account) ?~! {ViewNotFound}
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~! {"Current user does not have access to the view " + viewId}
            canAddCounterparty <- booleanToBox(view.canAddCounterparty == true, "The current view does not have can_add_counterparty permission. Please use a view with that permission or add the permission to this view.")
            checkAvailable <- tryo(assert(Counterparties.counterparties.vend.
              checkCounterpartyAvailable(postJson.name,bankId.value, accountId.value,viewId.value) == true)
            ) ?~! CounterpartyAlreadyExists
            counterparty <- Counterparties.counterparties.vend.createCounterparty(createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              name=postJson.name,
              otherAccountRoutingScheme=postJson.other_account_routing_scheme,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=postJson.other_branch_routing_scheme,
              otherBranchRoutingAddress=postJson.other_branch_routing_address,
              isBeneficiary=postJson.is_beneficiary
            )
//            Now just comment the following lines, keep the same return tpyle of  V220 "getCounterpartiesForAccount".
//            metadata <- Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterparty.counterpartyId) ?~! "Cannot find the metadata"
//            moderated <- Connector.connector.vend.getCounterparty(bankId, accountId, counterparty.counterpartyId).flatMap(oAcc => view.moderate(oAcc))
          } yield {
            val list = JSONFactory220.createCounterpartyJSON(counterparty)
//            Now just comment the following lines, keep the same return tpyle of  V220 "getCounterpartiesForAccount".
//            val list = createCounterpartJSON(moderated, metadata, couterparty)
            successJsonResponse(Extraction.decompose(list))
          }
      }
    }





    val createCustomerEntitlementsRequiredForSpecificBank = CanCreateCustomer ::
      CanCreateUserCustomerLink ::
      Nil
    val createCustomerEntitlementsRequiredForAnyBank = CanCreateCustomerAtAnyBank ::
      CanCreateUserCustomerLinkAtAnyBank ::
      Nil
    val createCustomeEntitlementsRequiredText = createCustomerEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createCustomerEntitlementsRequiredForAnyBank.mkString(" and ") + " entitlements required."

    resourceDocs += ResourceDoc(
      createCustomer,
      apiVersion,
      "createCustomer",
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer.",
      s"""Add a customer linked to the user specified by user_id
          |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
          |Dates need to be in the format 2013-01-21T23:08:00Z
          |
          |${authenticationRequiredMessage(true)}
          |
          |$createCustomeEntitlementsRequiredText
          |""",
      postCustomerJsonV210,
      customerJsonV210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        CustomerNumberAlreadyExists,
        UserNotFoundById,
        CustomerAlreadyExistsForUser,
        CreateConsumerError,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagCustomer))

    // TODO in next version?
    // Separate customer creation (keep here) from customer linking (remove from here)
    // Remove user_id from CreateCustomerJson

    // Note: Logged in user can no longer create a customer for himself


    lazy val createCustomer : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn // TODO. CHECK user has role to create a customer / create a customer for another user id.
            isValidBankIdFormat <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
            bank <- Bank(bankId) ?~! {BankNotFound}
            postedData <- tryo{json.extract[PostCustomerJsonV210]} ?~! InvalidJsonFormat
            hasEntitlements <- booleanToBox(hasAllEntitlements(bankId.value, u.userId, createCustomerEntitlementsRequiredForSpecificBank)
                                            ||
                                            hasAllEntitlements("", u.userId, createCustomerEntitlementsRequiredForAnyBank),
                                            s"$createCustomeEntitlementsRequiredText")
            checkAvailable <- tryo(assert(Customer.customerProvider.vend.checkCustomerNumberAvailable(bankId, postedData.customer_number) == true)) ?~! CustomerNumberAlreadyExists
            user_id <- tryo (if (postedData.user_id.nonEmpty) postedData.user_id else u.userId) ?~! s"Problem getting user_id"
            customer_user <- User.findByUserId(user_id) ?~! UserNotFoundById
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
              Option(MockCreditRating(postedData.credit_rating.rating, postedData.credit_rating.source)),
              Option(MockCreditLimit(postedData.credit_limit.currency, postedData.credit_limit.amount))) ?~! CreateConsumerError
            userCustomerLink <- booleanToBox(UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(user_id, customer.customerId).isEmpty == true) ?~! CustomerAlreadyExistsForUser
            userCustomerLink <- UserCustomerLink.userCustomerLink.vend.createUserCustomerLink(user_id, customer.customerId, exampleDate, true) ?~! CreateUserCustomerLinksError
          } yield {
            val json = JSONFactory210.createCustomerJson(customer)
            val successJson = Extraction.decompose(json)
            successJsonResponse(successJson, 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCustomersForUser,
      apiVersion,
      "getCustomersForUser",
      "GET",
      "/users/current/customers",
      "Get Customers for Current User",
      """Gets all Customers that are linked to a User.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      metricsJson,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagCustomer))

    lazy val getCustomersForUser : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! UserNotLoggedIn
            //bank <- Bank(bankId) ?~! {BankNotFound}
            customers <- tryo{Customer.customerProvider.vend.getCustomersByUserId(u.userId)} ?~! UserCustomerLinksNotFoundForUser
          } yield {
            val json = JSONFactory210.createCustomersJson(customers)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCustomersForCurrentUserAtBank,
      apiVersion,
      "getCustomersForCurrentUserAtBank",
      "GET",
      "/banks/BANK_ID/customers",
      "Get Customers for current User at Bank",
      s"""Retuns a list of Customers at the Bank that are linked to the currently authenticated User.
        |
        |
        |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      customerJsonV210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UserCustomerLinksNotFoundForUser,
        CustomerNotFoundByCustomerId,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer))

    lazy val getCustomersForCurrentUserAtBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! UserNotLoggedIn
            _ <- Bank(bankId) ?~! {BankNotFound}
            customers <- tryo{Customer.customerProvider.vend.getCustomersByUserId(u.userId)} ?~! UserCustomerLinksNotFoundForUser
            // Filter so we only see the ones for the bank in question
            bankCustomers = customers.filter(_.bankId==bankId.value)
          } yield {
            val json = JSONFactory210.createCustomersJson(bankCustomers)
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
      branchJsonPut,
      branchJson,
      List(
        UserNotLoggedIn, 
        BankNotFound, 
        InvalidJsonFormat, 
        InsufficientAuthorisationToCreateBranch, 
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val updateBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId)::  Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~ UserNotLoggedIn
            bank <- Bank(bankId) ?~! {BankNotFound}
            branchJsonPutV210 <- tryo {json.extract[BranchJsonPutV210]} ?~! InvalidJsonFormat
            _ <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true, InsufficientAuthorisationToCreateBranch)
            //package the BranchJsonPut to toBranchJsonPost, to call the createOrUpdateBranch method
            // branchPost <- toBranchJsonPost(branchId, branchJsonPutV210)

            branch <- transformToBranch(branchId, branchJsonPutV210)
            success <- Connector.connector.vend.createOrUpdateBranch(branch)
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
      branchJsonPost,
      branchJson,
      List(
        UserNotLoggedIn, 
        BankNotFound, 
        InvalidJsonFormat, 
        InsufficientAuthorisationToCreateBranch, 
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))

    lazy val createBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ UserNotLoggedIn
            bank <- Bank(bankId)?~! {BankNotFound}
            branchJsonPostV210 <- tryo {json.extract[BranchJsonPostV210]} ?~! InvalidJsonFormat
            canCreateBranch <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true, InsufficientAuthorisationToCreateBranch)
            branch <- transformToBranch(branchJsonPostV210)
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
      "/management/consumers/CONSUMER_ID/consumer/redirect_url",
      "Update Consumer RedirectUrl",
      s"""Update an existing redirectUrl for a Consumer specified by CONSUMER_ID.
         |
         | CONSUMER_ID can be obtained after you register the application. 
         | 
         | Or use the endpoint 'Get Consumers' to get it  
         | 
       """.stripMargin,
      consumerRedirectUrlJSON,
      consumerJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil
    )
    
    lazy val updateConsumerRedirectUrl: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "redirect_url" :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~ UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanUpdateConsumerRedirectUrl), UserHasMissingRoles + CanUpdateConsumerRedirectUrl )
            postJson <- tryo {json.extract[ConsumerRedirectUrlJSON]} ?~! InvalidJsonFormat
            consumerIdToLong <- tryo{consumerId.toLong} ?~! InvalidConsumerId 
            consumer <- Consumers.consumers.vend.getConsumerByPrimaryId(consumerIdToLong) ?~! {ConsumerNotFoundByConsumerId}
            //only the developer that created the Consumer should be able to edit it
            isLoginUserCreatedTheConsumer <- tryo(assert(consumer.createdByUserId.equals(user.openOrThrowException("Attempted to open an empty Box.").userId)))?~! UserNoPermissionUpdateConsumer
            //update the redirectURL and isactive (set to false when change redirectUrl) field in consumer table
            updatedConsumer <- Consumers.consumers.vend.updateConsumer(consumer.id.get, None, None, Some(false), None, None, None, None, Some(postJson.redirect_url), None) ?~! UpdateConsumerError
          } yield {
            val json = JSONFactory210.createConsumerJSON(updatedConsumer)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getMetrics,
      apiVersion,
      "getMetrics",
      "GET",
      "/management/metrics",
      "Get Metrics",
      """Get the all metrics
        |
        |require CanReadMetrics role
        |
        |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
        |
        |Should be able to filter on the following metrics fields
        |
        |eg: /management/metrics?start_date=2017-03-01&end_date=2017-03-04&limit=50&offset=2
        |
        |1 start_date (defaults to one week before current date): eg:start_date=2017-03-01
        |
        |2 end_date (defaults to current date) eg:end_date=2017-03-05
        |
        |3 limit (for pagination: defaults to 200)  eg:limit=200
        |
        |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
        |
        |eg: /management/metrics?start_date=2016-03-05&end_date=2017-03-08&limit=10000&offset=0&anon=false&app_name=hognwei&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&user_name=susan.uk.29@example.com&consumer_id=78
        |
        |Other filters:
        |
        |5 consumer_id  (if null ignore)
        |
        |6 user_id (if null ignore)
        |
        |7 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
        |
        |8 url (if null ignore), note: can not contain '&'. 
        |
        |9 app_name (if null ignore)
        |
        |10 implemented_by_partial_function (if null ignore),
        |
        |11 implemented_in_version (if null ignore)
        |
        |12 verb (if null ignore)
        |
      """.stripMargin,
      emptyObjectJson,
      metricsJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)

    lazy val getMetrics : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "metrics" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanReadMetrics), UserHasMissingRoles + CanReadMetrics )
  
            //Note: Filters Part 1: //eg: /management/metrics?start_date=2010-05-22&end_date=2017-05-22&limit=200&offset=0
  
            inputDateFormat <- Full(new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH))
            // set the long,long ago as the default date.
            defautStartDate <- Full("0000-00-00")
            tomorrowDate <- Full(new Date(now.getTime + 1000 * 60 * 60 * 24 * 1).toInstant.toString)
  
            //(defaults to one week before current date
            startDate <- tryo(inputDateFormat.parse(S.param("start_date").getOrElse(defautStartDate))) ?~!
              s"${InvalidDateFormat } start_date:${S.param("start_date").get }. Support format is yyyy-MM-dd"
            // defaults to current date
            endDate <- tryo(inputDateFormat.parse(S.param("end_date").getOrElse(tomorrowDate))) ?~!
              s"${InvalidDateFormat } end_date:${S.param("end_date").get }. Support format is yyyy-MM-dd"
            // default 1000, return 1000 items
            limit <- tryo(
                        S.param("limit") match {
                          case Full(l) if (l.toInt > 10000) => 10000
                          case Full(l)                      => l.toInt
                          case _                            => 1000
                        }
                      ) ?~!  s"${InvalidNumber } limit:${S.param("limit").get }"
            // default0, start from page 0
            offset <- tryo(S.param("offset").getOrElse("0").toInt) ?~! s"${InvalidNumber } offset:${S.param("offset").get }"
  
            //Because of "rd.getDate().before(startDatePlusOneDay)" exclude the startDatePlusOneDay, so we need to plus one day more then today.
            // add because of endDate is yyyy-MM-dd format, it started from 0, so it need to add 2 days.
            //startDatePlusOneDay <- Full(inputDateFormat.parse((new Date(endDate.getTime + 1000 * 60 * 60 * 24 * 2)).toInstant.toString))
  
            //Filters Part 2. -- the optional varibles:
            //eg: /management/metrics?start_date=2010-05-22&end_date=2017-05-22&limit=200&offset=0&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&consumer_id=78&app_name=hognwei&implemented_in_version=v2.1.0&verb=GET&anon=true
            consumerId <- Full(S.param("consumer_id")) //(if null ignore)
            userId <- Full(S.param("user_id")) //(if null ignore)
            anon <- Full(S.param("anon")) // (if null ignore) true => return where user_id is null.false => return where user_id is not null.
            url <- Full(S.param("url")) // (if null ignore)
            appName <- Full(S.param("app_name")) // (if null ignore)
            implementedByPartialFunction <- Full(S.param("implemented_by_partial_function")) //(if null ignore)           
            implementedInVersion <- Full(S.param("implemented_in_version")) // (if null ignore)
            verb <- Full(S.param("verb")) // (if null ignore)

            anonIsValid <- tryo(if (!anon.isEmpty) {
              assert(anon.get.equals("true") || anon.get.equals("false"))
            }) ?~! s"value anon:${anon.get } is Wrong . anon only have two value true or false or omit anon field"

            parameters = new collection.mutable.ListBuffer[OBPQueryParam]()
            setFilterPart1 <- Full(parameters += OBPLimit(limit) +=OBPOffset(offset) += OBPFromDate(startDate)+= OBPToDate(endDate))


            // TODO check / comment this logic

            setFilterPart2 <- if (!consumerId.isEmpty)
              Full(parameters += OBPConsumerId(consumerId.openOrThrowException("Attempted to open an empty Box.")))
            else if (!userId.isEmpty)
              Full(parameters += OBPUserId(userId.openOrThrowException("Attempted to open an empty Box.")))
            else if (!url.isEmpty)
              Full(parameters += OBPUrl(url.openOrThrowException("Attempted to open an empty Box.")))
            else if (!appName.isEmpty)
              Full(parameters += OBPAppName(appName.openOrThrowException("Attempted to open an empty Box.")))
            else if (!implementedInVersion.isEmpty)
              Full(parameters += OBPImplementedInVersion(implementedInVersion.openOrThrowException("Attempted to open an empty Box.")))
            else if (!implementedByPartialFunction.isEmpty)
              Full(parameters += OBPImplementedByPartialFunction(implementedByPartialFunction.openOrThrowException("Attempted to open an empty Box.")))
            else if (!verb.isEmpty)
              Full(parameters += OBPVerb(verb.openOrThrowException("Attempted to open an empty Box.")))
            else
              Full(parameters)
            
            metrics <- Full(APIMetrics.apiMetrics.vend.getAllMetrics(parameters.toList))
     
            // the anon field is not in database, so here use different way to filer it.
            filterByFields: List[APIMetric] = metrics
              .filter(m => (if (!anon.isEmpty && anon.get.equals("true")) (m.getUserId().equals("null")) else true))
              .filter(m => (if (!anon.isEmpty && anon.get.equals("false")) (!m.getUserId().equals("null")) else true))
            
          } yield {
            val json = JSONFactory210.createMetricsJson(filterByFields)
            successJsonResponse(Extraction.decompose(json)(DateFormatWithCurrentTimeZone))
          }
        }
      }
    }
  }
}

object APIMethods210 {
}