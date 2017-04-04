package code.api.v2_2_0

import java.text.SimpleDateFormat

import code.api.util.APIUtil.isValidCurrencyISOCode
import code.api.util.ApiRole.{CanCreateAccount, CanCreateBranch}
import code.api.util.ErrorMessages
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.JSONFactory200
import code.api.v2_1_0.BranchJsonPost
import code.bankconnectors.{Connector, KafkaJSONFactory_vMar2017}
import code.model.dataAccess.BankAccountCreation
import code.model.{BankId, ViewId, _}
import net.liftweb.common.Empty
import net.liftweb.http.Req
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.api.util.APIUtil._
import code.util.Helper._
import net.liftweb.common.{Box, Full}
import net.liftweb.http.JsonResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Helpers._


trait APIMethods220 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  // helper methods end here

  val Implementations2_2_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson: JValue = Nil
    val apiVersion: String = "2_2_0"

    val exampleDateString: String = "22/08/2013"
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)

    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      getViewsForBankAccount,
      apiVersion,
      "getViewsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account.",
      """#Views
        |
        |
        |Views in Open Bank Project provide a mechanism for fine grained access control and delegation to Accounts and Transactions. Account holders use the 'owner' view by default. Delegated access is made through other views for example 'accountants', 'share-holders' or 'tagging-application'. Views can be created via the API and each view has a list of entitlements.
        |
        |Views on accounts and transactions filter the underlying data to redact certain fields for certain users. For instance the balance on an account may be hidden from the public. The way to know what is possible on a view is determined in the following JSON.
        |
        |**Data:** When a view moderates a set of data, some fields my contain the value `null` rather than the original value. This indicates either that the user is not allowed to see the original data or the field is empty.
        |
        |There is currently one exception to this rule; the 'holder' field in the JSON contains always a value which is either an alias or the real name - indicated by the 'is_alias' field.
        |
        |**Action:** When a user performs an action like trying to post a comment (with POST API call), if he is not allowed, the body response will contain an error message.
        |
        |**Metadata:**
        |Transaction metadata (like images, tags, comments, etc.) will appears *ONLY* on the view where they have been created e.g. comments posted to the public view only appear on the public view.
        |
        |The other account metadata fields (like image_URL, more_info, etc.) are unique through all the views. Example, if a user edits the 'more_info' field in the 'team' view, then the view 'authorities' will show the new value (if it is allowed to do it).
        |
        |# All
        |*Optional*
        |
        |Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
        |
        |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val getViewsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            views <- account views u  // In other words: views = account.views(u) This calls BankingData.scala BankAccount.views
          } yield {
            val viewsJSON = JSONFactory220.createViewsJSON(views)
            successJsonResponse(Extraction.decompose(viewsJSON))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createViewForBankAccount,
      apiVersion,
      "createViewForBankAccount",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Create View.",
      """#Create a view on bank account
        |
        | OAuth authentication is required and the user needs to have access to the owner view.
        | The 'alias' field in the JSON can take one of three values:
        |
        | * _public_: to use the public alias if there is one specified for the other account.
        | * _private_: to use the public alias if there is one specified for the other account.
        |
        | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
        |
        | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
        |
        | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
        |
        | You should use a leading _ (underscore) for the view name because other view names may become reserved by OBP internally
        | """,
      Extraction.decompose(CreateViewJSON("_name-of-view-to-create", "Description of view", false, "_public_", true, List("can_see_transaction_start_date", "can_see_bank_account_label", "can_see_tags", "can_add_counterparty"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val createViewForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ "user not found"
            json <- tryo{json.extract[CreateViewJSON]} ?~ "wrong JSON format"
            account <- BankAccount(bankId, accountId)
            view <- account createView (u, json)
          } yield {
            val viewJSON = JSONFactory220.createViewJSON(view)
            successJsonResponse(Extraction.decompose(viewJSON), 201)
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateViewForBankAccount,
      apiVersion,
      "updateViewForBankAccount",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID",
      "Update View.",
      """Update an existing view on a bank account
        |
        |OAuth authentication is required and the user needs to have access to the owner view.
        |
        |The json sent is the same as during view creation (above), with one difference: the 'name' field
        |of a view is not editable (it is only set when a view is created)""",
      Extraction.decompose(UpdateViewJSON("New description of view", false, "_public_", true, List("can_see_transaction_start_date", "can_see_bank_account_label"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val updateViewForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            u <- user ?~ "user not found"
            updateJson <- tryo{json.extract[UpdateViewJSON]} ?~ "wrong JSON format"
            updatedView <- account.updateView(u, viewId, updateJson)
          } yield {
            val viewJSON = JSONFactory220.createViewJSON(updatedView)
            successJsonResponse(Extraction.decompose(viewJSON), 200)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCurrentFxRate,
      apiVersion,
      "getCurrentFxRate",
      "GET",
      "/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE",
      "Get Current FxRate",
      """Get the latest FXRate specified by FROM_CURRENCY_CODE and TO_CURRENCY_CODE """,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)

    lazy val getCurrentFxRate: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "fx" :: fromCurrencyCode :: toCurrencyCode :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            isValidCurrencyISOCodeFrom <- tryo(assert(isValidCurrencyISOCode(fromCurrencyCode))) ?~! ErrorMessages.InvalidISOCurrencyCode
            isValidCurrencyISOCodeTo <- tryo(assert(isValidCurrencyISOCode(toCurrencyCode))) ?~! ErrorMessages.InvalidISOCurrencyCode
            fxRate <- tryo(Connector.connector.vend.getCurrentFxRate(fromCurrencyCode, toCurrencyCode).get) ?~! ErrorMessages.FXCurrencyCodeCombinationsNotSupported
          } yield {
            val viewJSON = JSONFactory220.createFXRateJSON(fxRate)
            successJsonResponse(Extraction.decompose(viewJSON))
          }
      }
    }    

    //

    resourceDocs += ResourceDoc(
      getCounterpartiesForAccount,
      apiVersion,
      "getCounterpartiesForAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties of one Account.",
      s"""Get the counterparties for the account / view.
          |
          |${authenticationRequiredMessage(true)}
          |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, PSD2, OBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagCounterparty))

    lazy val getCounterpartiesForAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get other accounts for one account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)?~! {ErrorMessages.ViewNotFound}
            canAddCounterparty <- booleanToBox(view.canAddCounterparty == true, "The current view does not have can_add_counterparty permission. Please use a view with that permission or add the permission to this view.")
            availableViews <- Full(account.permittedViews(user))
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~ {"Current user does not have access to the view " + viewId}
            counterparties <- Connector.connector.vend.getCounterparties(bankId,accountId,viewId) ?~ {"Connector.connector.vend.getCounterparties faild, can not get data from Database or Kafka .." }
          } yield {
            val counterpartiesJson = JSONFactory220.createCounterpartiesJSON(counterparties)
            successJsonResponse(Extraction.decompose(counterpartiesJson))
          }
      }
    }






    resourceDocs += ResourceDoc(
      getMessageDocs,
      apiVersion,
      "getMessageDocs",
      "GET",
      "/message-docs/mar2017",
      "Get Message Docs",
      """These message docs provide example messages sent by OBP to the (Kafka) message queue for processing by the Core Banking / Payment system Adapter - together with an example expected response and possible error codes.
        | Integrators can use these messages to build Adapters that provide core banking services to OBP.
        | Note: To enable Kafka connector and this message format, you must set conenctor=kafka_vMar2017 in your Props
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApiInfo)
    )

    lazy val getMessageDocs: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "message-docs" :: "mar2017" :: Nil JsonGet _ => {
        user => {
          for {
            connector <- tryo{Connector.getObjectInstance(s"""code.bankconnectors.KafkaMappedConnector_vMar2017""")}
            messageDocs <- tryo{connector.messageDocs.toList}
          } yield {
            val json = KafkaJSONFactory_vMar2017.createMessageDocsJson(messageDocs)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }
  
  
    resourceDocs += ResourceDoc(
      createBank,
      apiVersion,
      "createBank",
      "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |${authenticationRequiredMessage(true) }
         |""",
      Extraction.decompose(
        BankJSON(
          id = "gh.29.uk",
          full_name = "uk",
          short_name = "uk",
          logo_url = "https://static.openbankproject.com/images/sandbox/bank_x.png",
          website_url = "https://www.example.com",
          swift_bic = "IIIGGB22",
          national_identifier = "UK97ZZZ1234567890",
          bank_routing = BankRoutingJSON(
            bank_routing_scheme = "IIIGGB22",
            bank_routing_address = "UK97ZZZ1234567890"
          )
        )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      Nil
    )
  
    lazy val createBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- tryo{ json.extract[BankJSON] } ?~! ErrorMessages.InvalidJsonFormat
            success <- Connector.connector.vend.createOrUpdateBank(
              bank.id,
              bank.full_name,
              bank.short_name,
              bank.logo_url,
              bank.website_url,
              bank.swift_bic,
              bank.national_identifier,
              bank.bank_routing.bank_routing_scheme,
              bank.bank_routing.bank_routing_address
            )
          } yield {
            val json = JSONFactory220.createBankJSON(success)
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
         |${authenticationRequiredMessage(true) }
         |""",
      Extraction.decompose(
        BranchJSON(
          id = "123",
          bank_id = "gh.29.uk",
          name = "OBP",
          address = AddressJson("VALTATIE 8", "", "", "AKAA", "", "", "37800"),
          location = LocationJson(1.2, 2.1),
          meta = MetaJson(LicenseJson("copyright2016", "copyright2016")),
          lobby = LobbyJson("Ma-Pe 10:00-16:30"),
          drive_up = DriveUpJson("Ma-Pe 09:00-14:00"),
          branch_routing = BranchRoutingJSON(
            branch_routing_scheme = "IIIGGB22",
            branch_routing_address = "UK97ZZZ1234567890"
          )
        )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, OBWG),
      Nil
    )
  
    lazy val createBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)?~! {ErrorMessages.BankNotFound}
            canCreateBranch <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true, ErrorMessages.InsufficientAuthorisationToCreateBranch)
            branch <- tryo {json.extract[BranchJSON]} ?~! ErrorMessages.InvalidJsonFormat
            success <- Connector.connector.vend.createOrUpdateBranch(
              BranchJsonPost(
                branch.id,
                branch.bank_id,
                branch.name,
                branch.address,
                branch.location,
                branch.meta,
                branch.lobby,
                branch.drive_up
              ),
              branch.branch_routing.branch_routing_scheme,
              branch.branch_routing.branch_routing_address
            )
          } yield {
            val json = JSONFactory220.createBranchJSON(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }

  
    resourceDocs += ResourceDoc(
      createAccount,
      apiVersion,
      "createAccount",
      "PUT",
      "/banks/BANK_ID/accounts/NEW_ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by NEW_ACCOUNT_ID.
        |
        |
        |The User can create an Account for themself or an Account for another User if they have CanCreateAccount role.
        |
        |If USER_ID is not specified the account will be owned by the logged in User.
        |
        |Note: The Amount must be zero.""".stripMargin,
      Extraction.decompose(
        CreateAccountJSON( 
          user_id = "66214b8e-259e-44ad-8868-3eb47be70646",
          label = "Label",
          `type` = "CURRENT",
          balance = AmountOfMoneyJSON(
            "EUR",
            "0"
          ),
          branch_id = "1234",
          account_routing = AccountRoutingJSON(
            account_routing_scheme = "OBP",
            account_routing_address = "UK123456"
          )
        )
      ),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount)
    )
  
  
    lazy val createAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        user => {
        
          for {
            loggedInUser <- user ?~! ErrorMessages.UserNotLoggedIn
            jsonBody <- tryo (json.extract[CreateAccountJSON]) ?~ ErrorMessages.InvalidJsonFormat
            user_id <- tryo (if (jsonBody.user_id.nonEmpty) jsonBody.user_id else loggedInUser.userId) ?~ ErrorMessages.InvalidUserId
            isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidAccountIdFormat
            isValidBankId <- tryo(assert(isValidID(accountId.value)))?~! ErrorMessages.InvalidBankIdFormat
            postedOrLoggedInUser <- User.findByUserId(user_id) ?~! ErrorMessages.UserNotFoundById
            bank <- Bank(bankId) ?~ s"Bank $bankId not found"
            // User can create account for self or an account for another user if they have CanCreateAccount role
            isAllowed <- booleanToBox(hasEntitlement(bankId.value, loggedInUser.userId, CanCreateAccount) == true || (user_id == loggedInUser.userId) , 
                                      s"User must either create account for self or have role $CanCreateAccount")
            initialBalanceAsString <- tryo (jsonBody.balance.amount) ?~ ErrorMessages.InvalidAccountBalanceAmount
            accountType <- tryo(jsonBody.`type`) ?~ ErrorMessages.InvalidAccountType
            accountLabel <- tryo(jsonBody.`type`) //?~ ErrorMessages.InvalidAccountLabel
            initialBalanceAsNumber <- tryo {BigDecimal(initialBalanceAsString)} ?~! ErrorMessages.InvalidAccountInitialBalance
            isTrue <- booleanToBox(0 == initialBalanceAsNumber) ?~ s"Initial balance must be zero"
            currency <- tryo (jsonBody.balance.currency) ?~ ErrorMessages.InvalidAccountBalanceCurrency
            accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty,
                                                s"Account with id $accountId already exists at bank $bankId")
            bankAccount <- Connector.connector.vend.createSandboxBankAccount(
              bankId,
              accountId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              jsonBody.branch_id,
              jsonBody.account_routing.account_routing_scheme,
              jsonBody.account_routing.account_routing_address
            )
          } yield {
            BankAccountCreation.setAsOwner(bankId, accountId, postedOrLoggedInUser)
          
            val json = JSONFactory220.createAccountJSON(user_id, bankAccount)
          
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }
  
  
  
  }
}

object APIMethods220 {
}
