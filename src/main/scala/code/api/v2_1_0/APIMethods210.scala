package code.api.v2_1_0

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.TransactionTypes.TransactionType
import code.api.util.ApiRole._
import code.api.util.{APIUtil, ApiRole, ErrorMessages}
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_3_0.{JSONFactory1_3_0, _}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.{TransactionRequestBodyJSON, _}
import code.api.v2_1_0.JSONFactory210._
import code.api.v2_2_0.JSONFactory220
import code.atms.Atms
import code.atms.Atms.AtmId
import code.bankconnectors._
import code.branches.Branches
import code.branches.Branches.BranchId
import code.consumer.Consumers
import code.customer.{Customer, MockCreditLimit, MockCreditRating, MockCustomerFaceImage}
import code.entitlement.Entitlement
import code.fx.fx
import code.metadata.counterparties.Counterparties
import code.metrics.{APIMetric, APIMetrics}
import code.model.dataAccess.{AuthUser, MappedBankAccount}
import code.model.{BankAccount, BankId, ViewId, _}
import code.products.Products.ProductCode
import code.usercustomerlinks.UserCustomerLink
import net.liftweb.http.{Req, S}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.api.util.APIUtil._
import code.api.{APIFailure, ChargePolicy}
import code.metadata.counterparties._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import code.util.Helper._
import net.liftweb.common.{Box, Full}
import net.liftweb.http.JsonResponse
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import net.liftweb.util.Helpers._


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
            importData <- tryo {json.extract[SandboxDataImport]} ?~! {ErrorMessages.InvalidJsonFormat}
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


    import net.liftweb.json.Extraction._
    import net.liftweb.json.JsonAST._
    import net.liftweb.json.Printer._
    val exchangeRates = pretty(render(decompose(fx.exchangeRates)))


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




    // Transaction Request General case (no TRANSACTION_REQUEST_TYPE specified)
    resourceDocs += ResourceDoc(
      createTransactionRequest,
      apiVersion,
      "createTransactionRequest",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests",
      "Create Transaction Request.",
      s"""$transactionRequestGeneralText
         |
       """.stripMargin,
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

    // COUNTERPARTY
    resourceDocs += ResourceDoc(
      createTransactionRequest,
      apiVersion,
      "createTransactionRequest",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests",
      "Create Transaction Request (COUNTERPARTY)",
      s"""$transactionRequestGeneralText
         |
         |Special instructions for COUNTERPARTY:
         |
         |When using a COUNTERPARTY to create a Transaction Request, specificy the counterparty_id in the body of the request.
         |The routing details of the counterparty will be forwarded for the transfer.
         |
       """.stripMargin,
      Extraction.decompose(TransactionRequestBodyCounterpartyJSON(
        CounterpartyIdJson("lalalalwieuryi79878987fds"),
        AmountOfMoneyJSON("EUR", "100.53"),
        "A description for the transaction to the counterparty",
      "SHARED"
      )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest))


    val lowAmount  = AmountOfMoneyJSON("EUR", "12.50")
    val sharedChargePolicy = ChargePolicy.withName("SHARED")

    // Transaction Request (SEPA)
    resourceDocs += ResourceDoc(
      createTransactionRequest,
      apiVersion,
      "createTransactionRequest",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests",
      "Create Transaction Request (SEPA)",
      s"""$transactionRequestGeneralText
         |
         |Special instructions for SEPA:
         |
         |When using a SEPA Transaction Request, you specify the IBAN of a Counterparty in the body of the request.
         |The routing details (IBAN) of the counterparty will be forwarded to the core banking system for the transfer.
         |
       """.stripMargin,
      Extraction.decompose(TransactionRequestBodySEPAJSON(lowAmount, IbanJson("IBAN-798789873234"), "This is a SEPA Transaction Request", sharedChargePolicy.toString)
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
              u <- user ?~ ErrorMessages.UserNotLoggedIn
              isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value))) ?~! ErrorMessages.InvalidAccountIdFormat
              isValidBankIdFormat <- tryo(assert(isValidID(bankId.value))) ?~! ErrorMessages.InvalidBankIdFormat
              fromBank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
              fromAccount <- BankAccount(bankId, accountId) ?~! {ErrorMessages.AccountNotFound}
              view <- View.fromUrl(viewId, fromAccount) ?~! {ErrorMessages.ViewNotFound}
              isOwnerOrHasEntitlement <- booleanToBox(u.ownerAccess(fromAccount) == true ||
                hasEntitlement(fromAccount.bankId.value, u.userId, CanCreateAnyTransactionRequest) == true, ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
              isValidTransactionRequestType <- tryo(assert(Props.get("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value))) ?~!
                s"${ErrorMessages.InvalidTransactionRequestType}: '${transactionRequestType.value}'"

              // Check the input JSON format, here is just check the common parts of all four tpyes
              transDetailsJson <- tryo {json.extract[TransactionRequestBodyCommonJSON]} ?~! ErrorMessages.InvalidJsonFormat
              isValidAmountNumber <- tryo(BigDecimal(transDetailsJson.value.amount)) ?~! ErrorMessages.InvalidNumber
              isPositiveAmount <- booleanToBox(isValidAmountNumber > BigDecimal("0"), ErrorMessages.NotPositiveAmount)
              isValidCurrencyISOCode <- tryo(assert(isValidCurrencyISOCode(transDetailsJson.value.currency))) ?~! ErrorMessages.InvalidISOCurrencyCode

              // Prevent default value for transaction request type (at least).
              transferCurrencyEqual <- tryo(assert(transDetailsJson.value.currency == fromAccount.currency)) ?~! {s"${ErrorMessages.InvalidTransactionRequestCurrency} " +
                s"From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.value.currency}"}
              amountOfMoneyJSON <- Full(AmountOfMoneyJSON(transDetailsJson.value.currency, transDetailsJson.value.amount))

              isMapped: Boolean <- Full((Props.get("connector").get.toString).equalsIgnoreCase("mapped"))

              createdTransactionRequest <- transactionRequestType.value match {
                case "SANDBOX_TAN" => {
                  for {
                    transactionRequestBodySandboxTan <- tryo(json.extract[TransactionRequestBodySandBoxTanJSON]) ?~! ErrorMessages.InvalidJsonFormat
                    toBankId <- Full(BankId(transactionRequestBodySandboxTan.to.bank_id))
                    toAccountId <- Full(AccountId(transactionRequestBodySandboxTan.to.account_id))
                    toAccount <- BankAccount(toBankId, toAccountId) ?~! {ErrorMessages.CounterpartyNotFound}
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
                case "COUNTERPARTY" => {
                  for {
                    //For COUNTERPARTY, Use the counterpartyId to find the toCounterparty and set up the toAccount
                    transactionRequestBodyCounterparty <- tryo {json.extract[TransactionRequestBodyCounterpartyJSON]} ?~! {ErrorMessages.InvalidJsonFormat}
                    toCounterpartyId <- Full(transactionRequestBodyCounterparty.to.counterparty_id)
                    // Get the Counterparty by id
                    toCounterparty <- Connector.connector.vend.getCounterpartyByCounterpartyId(CounterpartyId(toCounterpartyId)) ?~! {ErrorMessages.CounterpartyNotFoundByCounterpartyId}

                    // Check we can send money to it.
                    isBeneficiary <- booleanToBox(toCounterparty.isBeneficiary == true, ErrorMessages.CounterpartyBeneficiaryPermit)

                    // Get the Routing information from the Counterparty for the payment backend
                    toBankId <- Full(BankId(toCounterparty.otherBankRoutingAddress))
                    toAccountId <-Full(AccountId(toCounterparty.otherAccountRoutingAddress))

                    // Use otherAccountRoutingScheme and otherBankRoutingScheme to determine how we validate the toBank and toAccount.
                    // i.e. Only validate toBankId and toAccountId if they are both OBP
                    // i.e. if it is OBP we can expect the account to exist locally.
                    // This is so developers can follow the COUNTERPARTY flow in the sandbox

                    //if it is OBP, we call the local database, just for sandbox test case
                    toAccount <- if(isMapped && toCounterparty.otherAccountRoutingScheme =="OBP" && toCounterparty.otherBankRoutingScheme=="OBP")
                      LocalMappedConnector.createOrUpdateMappedBankAccount(toBankId, toAccountId, fromAccount.currency)
                    //if it is remote, we do not need the bankaccount, we just send the counterparty to remote, remote make the transaction
                    else
                      Full(new MappedBankAccount())

                    // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                    transactionRequestAccountJSON = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)
                    chargePolicy = transactionRequestBodyCounterparty.charge_policy
                    chargePolicyIsValid<-tryo(assert(ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy)))) ?~! ErrorMessages.InvalidChargePolicy
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
                case "SEPA" => {
                  for {
                    //For SEPA, Use the iban to find the toCounterparty and set up the toAccount
                    transDetailsSEPAJson <- tryo {json.extract[TransactionRequestBodySEPAJSON]} ?~! {ErrorMessages.InvalidJsonFormat}
                    toIban <- Full(transDetailsSEPAJson.to.iban)
                    toCounterparty <- Connector.connector.vend.getCounterpartyByIban(toIban) ?~! {ErrorMessages.CounterpartyNotFoundByIban}
                    isBeneficiary <- booleanToBox(toCounterparty.isBeneficiary == true, ErrorMessages.CounterpartyBeneficiaryPermit)
                    toBankId <- Full(BankId(toCounterparty.otherBankRoutingAddress))
                    toAccountId <-Full(AccountId(toCounterparty.otherAccountRoutingAddress))

                    //if the connector is mapped, we get the data from local mapper
                    toAccount <- if(isMapped)
                      LocalMappedConnector.createOrUpdateMappedBankAccount(toBankId, toAccountId, fromAccount.currency)
                    else
                    //if it is remote, we do not need the bankaccount, we just send the counterparty to remote, remote make the transaction
                      Full(new MappedBankAccount())

                    // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                    transactionRequestAccountJSON = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)
                    chargePolicy = transDetailsSEPAJson.charge_policy
                    chargePolicyIsValid<-tryo(assert(ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))))?~! {ErrorMessages.InvalidChargePolicy}
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
                case "FREE_FORM" => {
                  for {
                    transactionRequestBodyFreeForm <- Full(json.extract[TransactionRequestBodyFreeFormJSON])
                    // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
                    transactionRequestAccountJSON <- Full(TransactionRequestAccountJSON(fromAccount.bankId.value, fromAccount.accountId.value))
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
              answerOk <- Connector.connector.vend.answerTransactionRequestChallenge(transReqId, answerJson.answer)
              challengeAnswerOk <- Connector.connector.vend.validateChallengeAnswer(answerJson.id, answerJson.answer)

              //check the transReqId validation.
              existingTransactionRequest <- Connector.connector.vend.getTransactionRequestImpl(transReqId) ?~! {ErrorMessages.InvalidTransactionRequestId}

              //check the input transactionRequestType is same as when the user create the existingTransactionRequest
              existingTransactionRequestType <- Full(existingTransactionRequest.`type`)
              isSameTransReqType <- booleanToBox(existingTransactionRequestType.equals(transactionRequestType.value),s"${ErrorMessages.TransactionRequestTypeHasChanged} It should be :'$existingTransactionRequestType' ")

              //check the changle id is same as when the user create the existingTransactionRequest
              isSameChallengeId <- booleanToBox(existingTransactionRequest.challenge.id.equals(answerJson.id),{ErrorMessages.InvalidTransactionRequesChallengeId})

              //check the change statue wheather is initiated, only retreive INITIATED transaction requests.
              isTransReqStatueInitiated <- booleanToBox(existingTransactionRequest.status.equals("INITIATED"),ErrorMessages.TransactionRequestStatusNotInitiated)

              transactionRequest <- Connector.connector.vend.createTransactionAfterChallengev210(u, transReqId, transactionRequestType)
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
            consumerIdToLong <- tryo{consumerId.toLong} ?~! ErrorMessages.InvalidConsumerId
            consumer <- Consumers.consumers.vend.getConsumerByConsumerId(consumerIdToLong)
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
            consumer <- Consumers.consumers.vend.getConsumerByConsumerId(consumerId.toLong)
            updatedConsumer <- Consumers.consumers.vend.updateConsumer(consumer.id, None, None, Some(putData.enabled), None, None, None, None, None, None) ?~! "Cannot update Consumer"
          } yield {
            // Format the data as json
            val json = PutEnabledJSON(updatedConsumer.isActive)
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
            postJson <- tryo {json.extract[PostPhysicalCardJSON]} ?~! {ErrorMessages.InvalidJsonFormat}
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
            postJson <- tryo {json.extract[PostCounterpartyJSON]} ?~! {ErrorMessages.InvalidJsonFormat}
            availableViews <- Full(account.permittedViews(user))
            view <- View.fromUrl(viewId, account) ?~! {ErrorMessages.ViewNotFound}
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
            canAddCounterparty <- booleanToBox(view.canAddCounterparty == true, "The current view does not have can_add_counterparty permission. Please use a view with that permission or add the permission to this view.")
            checkAvailable <- tryo(assert(Counterparties.counterparties.vend.
              checkCounterpartyAvailable(postJson.name,bankId.value, accountId.value,viewId.value) == true)
            ) ?~! ErrorMessages.CounterpartyAlreadyExists
            counterparty <- Counterparties.counterparties.vend.createCounterparty(createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              name=postJson.name,
              otherAccountRoutingScheme=postJson.other_account_routing_scheme,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              isBeneficiary=postJson.is_beneficiary
            )
//            Now just comment the following lines, keep the same return tpyle of  V220 "getCounterpartiesForAccount".
//            metadata <- Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterparty.counterpartyId) ?~ "Cannot find the metadata"
//            moderated <- Connector.connector.vend.getCounterparty(bankId, accountId, counterparty.counterpartyId).flatMap(oAcc => view.moderate(oAcc))
          } yield {
            val list = JSONFactory220.createCounterpartyJSON(counterparty)
//            Now just comment the following lines, keep the same return tpyle of  V220 "getCounterpartiesForAccount".
//            val list = createCounterpartJSON(moderated, metadata, couterparty)
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
      Extraction.decompose(code.api.v2_1_0.PostCustomerJson("user_id to attach this customer to e.g. 123213",
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
            branch <- tryo {json.extract[BranchJsonPut]} ?~! ErrorMessages.InvalidJsonFormat
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
            branch <- tryo {json.extract[BranchJsonPost]} ?~! ErrorMessages.InvalidJsonFormat
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
      "/management/consumers/CONSUMER_ID/consumer/redirect_url",
      "Update Consumer RedirectUrl",
      s"""Update an existing redirectUrl for a Consumer specified by CONSUMER_ID.
         |
         | CONSUMER_ID can be obtained after you register the application. 
         | 
         | Or use the endpoint 'Get Consumers' to get it  
         | 
       """.stripMargin,
      Extraction.decompose(ConsumerRedirectUrlJSON("http://localhost:8888")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)
    
    lazy val updateConsumerRedirectUrl: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "redirect_url" :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanUpdateConsumerRedirectUrl), s"$CanUpdateConsumerRedirectUrl entitlement required")
            postJson <- tryo {json.extract[ConsumerRedirectUrlJSON]} ?~! ErrorMessages.InvalidJsonFormat
            consumerIdToLong <- tryo{consumerId.toLong} ?~! ErrorMessages.InvalidConsumerId 
            consumer <- Consumers.consumers.vend.getConsumerByConsumerId(consumerIdToLong) ?~! {ErrorMessages.ConsumerNotFoundByConsumerId}
            //only the developer that created the Consumer should be able to edit it
            isLoginUserCreatedTheConsumer <- tryo(assert(consumer.createdByUserId.equals(user.get.userId)))?~! ErrorMessages.UserNoPermissionUpdateConsumer
            //update the redirectURL and isactive (set to false when change redirectUrl) field in consumer table
            updatedConsumer <- Consumers.consumers.vend.updateConsumer(consumer.id, None, None, Some(false), None, None, None, None, Some(postJson.redirect_url), None) ?~! "Cannot update Consumer"
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
        |Filters Part 2.
        |
        |add more fileds to filter
        |
        |eg: /management/metrics?start_date=2016-03-05&end_date=2017-03-08&limit=10000&offset=0&anon=false&app_name=hognwei&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&user_name=susan.uk.29@example.com&consumer_id=78
        |
        |Should be able to filter on:
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
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)

    lazy val getMetrics : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "metrics" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            hasEntitlement <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanReadMetrics), s"$CanReadMetrics entitlement required")
  
            //Note: Filters Part 1:
            //?start_date=100&end_date=1&limit=200&offset=0
  
            inputDateFormat <- Full(new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH))
            // set the long,long ago as the default date.
            defautStartDate <- Full("0000-00-00")
            tomorrowDate <- Full(new Date(now.getTime + 1000 * 60 * 60 * 24 * 1).toInstant.toString)
  
            //(defaults to one week before current date
            startDate <- tryo(inputDateFormat.parse(S.param("start_date").getOrElse(defautStartDate))) ?~!
              s"${ErrorMessages.InvalidDateFormat } start_date:${S.param("start_date").get }. Support format is yyyy-MM-dd"
            // defaults to current date
            endDate <- tryo(inputDateFormat.parse(S.param("end_date").getOrElse(tomorrowDate))) ?~!
              s"${ErrorMessages.InvalidDateFormat } end_date:${S.param("end_date").get }. Support format is yyyy-MM-dd"
            // default 200, return 200 items
            limit <- tryo(S.param("limit").getOrElse("200").toInt) ?~!
              s"${ErrorMessages.InvalidNumber } limit:${S.param("limit").get }"
            // default0, start from page 0
            offset <- tryo(S.param("offset").getOrElse("0").toInt) ?~!
              s"${ErrorMessages.InvalidNumber } offset:${S.param("offset").get }"
  
            metrics <- Full(APIMetrics.apiMetrics.vend.getAllMetrics(List(OBPLimit(limit), OBPOffset(offset), OBPFromDate(startDate), OBPToDate(endDate)): _*))
  
            //Because of "rd.getDate().before(startDatePlusOneDay)" exclude the startDatePlusOneDay, so we need to plus one day more then today.
            // add because of endDate is yyyy-MM-dd format, it started from 0, so it need to add 2 days.
            //startDatePlusOneDay <- Full(inputDateFormat.parse((new Date(endDate.getTime + 1000 * 60 * 60 * 24 * 2)).toInstant.toString))
            
            ///filterByDate <- Full(metrics.toList.filter(rd => (rd.getDate().after(startDate)) && (rd.getDate().before(startDatePlusOneDay))))
  
            /** pages: 
              * eg: total=79
              * offset=0, limit =50
              *  filterByDate.slice(0,50)
              * offset=1, limit =50
              *  filterByDate.slice(50*1,50+50*1)--> filterByDate.slice(50,100)
              * offset=2, limit =50
              *  filterByDate.slice(50*2,50+50*2)-->filterByDate.slice(100,150)
              */
            //filterByPages <- Full(filterByDate.slice(offset * limit, (offset * limit + limit)))

            //Filters Part 2.
            //eg: /management/metrics?start_date=100&end_date=1&limit=200&offset=0
            //    &user_id=c7b6cb47-cb96-4441-8801-35b57456753a&consumer_id=78&app_name=hognwei&implemented_in_version=v2.1.0&verb=GET&anon=true
            // consumer_id (if null ignore)
            // user_id (if null ignore)
            // anon true => return where user_id is null. false => return where where user_id is not null(if null ignore)
            // url (if null ignore)
            // app_name (if null ignore)
            // implemented_by_partial_function (if null ignore)
            // implemented_in_version (if null ignore)
            // verb (if null ignore)
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

            filterByFields: List[APIMetric] = metrics
              .filter(rd => (if (!consumerId.isEmpty) rd.getConsumerId().equals(consumerId.get) else true))
              .filter(rd => (if (!userId.isEmpty) rd.getUserId().equals(userId.get) else true))
              .filter(rd => (if (!anon.isEmpty && anon.get.equals("true")) (rd.getUserId().equals("null")) else true))
              .filter(rd => (if (!anon.isEmpty && anon.get.equals("false")) (!rd.getUserId().equals("null")) else true))
              //TODO url can not contain '&', if url is /management/metrics?start_date=100&end_date=1&limit=200&offset=0, it can not work.
              .filter(rd => (if (!url.isEmpty) rd.getUrl().equals(url.get) else true))
              .filter(rd => (if (!appName.isEmpty) rd.getAppName.equals(appName.get) else true))
              .filter(rd => (if (!implementedByPartialFunction.isEmpty) rd.getImplementedByPartialFunction().equals(implementedByPartialFunction.get) else true))
              .filter(rd => (if (!implementedInVersion.isEmpty) rd.getImplementedInVersion().equals(implementedInVersion.get) else true))
              .filter(rd => (if (!verb.isEmpty) rd.getVerb().equals(verb.get) else true))
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