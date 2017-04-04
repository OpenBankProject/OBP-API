package code.api.v1_2_1

import code.api.util.APIUtil
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.Extraction
import net.liftweb.common._
import code.model._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import APIUtil._
import net.liftweb.util.Helpers._
import net.liftweb.http.rest.RestHelper
import java.net.URL
import net.liftweb.util.{True, Props}
import code.bankconnectors._
import code.bankconnectors.OBPOffset
import code.bankconnectors.OBPFromDate
import code.bankconnectors.OBPToDate
import code.metadata.counterparties.Counterparties
import code.model.CreateViewJSON
import net.liftweb.common.Full
import code.model.UpdateViewJSON

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import net.liftweb.json.JsonDSL._






case class MakePaymentJson(
  bank_id : String,
  account_id : String,
  amount : String)

trait APIMethods121 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here

  private def bankAccountsListToJson(bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    val accJson : List[AccountJSON] = bankAccounts.map( account => {
      val views = account permittedViews user
      val viewsAvailable : List[ViewJSON] =
        views.map( v => {
          JSONFactory.createViewJSON(v)
        })
      JSONFactory.createAccountJSON(account,viewsAvailable)
    })

    val accounts = new AccountsJSON(accJson)
    Extraction.decompose(accounts)
  }

  def checkIfLocationPossible(lat:Double,lon:Double) : Box[Unit] = {
    if(scala.math.abs(lat) <= 90 & scala.math.abs(lon) <= 180)
      Full()
    else
      Failure("Coordinates not possible")
  }

  private def moderatedTransactionMetadata(bankId : BankId, accountId : AccountId, viewId : ViewId, transactionID : TransactionId, user : Box[User]) : Box[ModeratedTransactionMetadata] ={
    for {
      account <- BankAccount(bankId, accountId)
      view <- View.fromUrl(viewId, account)
      moderatedTransaction <- account.moderatedTransaction(transactionID, view, user)
      metadata <- Box(moderatedTransaction.metadata) ?~ {"view " + viewId + " does not authorize metadata access"}
    } yield metadata
  }

  private def getApiInfoJSON(apiVersion : String, apiVersionStatus : String) = {
    val apiDetails: JValue = {

      val organisation = Props.get("hosted_by.organisation", "TESOBE")
      val email = Props.get("hosted_by.email", "contact@tesobe.com")
      val phone = Props.get("hosted_by.phone", "+49 (0)30 8145 3994")

      val connector = Props.get("connector").openOrThrowException("no connector set")

      val hostedBy = new HostedBy(organisation, email, phone)
      val apiInfoJSON = new APIInfoJSON(apiVersion, apiVersionStatus, gitCommit, connector, hostedBy)
      Extraction.decompose(apiInfoJSON)
    }
    apiDetails
  }

  // helper methods end here

  val Implementations1_2_1 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "1_2_1"
    val apiVersionStatus : String = "STABLE"

    resourceDocs += ResourceDoc(
      root(apiVersion, apiVersionStatus),
      apiVersion,
      "root",
      "GET",
      "/root",
      "The root of the API",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, notPSD2, OBWG),
      apiTagApiInfo :: Nil)

    def root(apiVersion : String, apiVersionStatus: String) : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "root" :: Nil JsonGet json => user => Full(successJsonResponse(getApiInfoJSON(apiVersion, apiVersionStatus), 200))
      case Nil JsonGet json => user => Full(successJsonResponse(getApiInfoJSON(apiVersion, apiVersionStatus), 200))
    }


    resourceDocs += ResourceDoc(
      getBanks,
      apiVersion,
      "getBanks",
      "GET",
      "/banks",
      "Get Banks",
      """Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      emptyObjectJson,
      decompose(BanksJSON(List(BankJSON("gh.29.uk", "EFG", "Eurobank", "None", "www.eurobank.rs",BankRoutingJSON("obp","gh.29.uk"))))),
      emptyObjectJson :: Nil,
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get banks
      case "banks" :: Nil JsonGet json => {
        user =>
          def banksToJson(banksList: List[Bank]): JValue = {
            val banksJSON: List[BankJSON] = banksList.map(b => {
              JSONFactory.createBankJSON(b)
            })
            val banks = new BanksJSON(banksJSON)
            Extraction.decompose(banks)
          }

          Full(successJsonResponse(banksToJson(Bank.all)))
      }
    }


    resourceDocs += ResourceDoc(
      bankById,
      apiVersion,
      "bankById",
      "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID
        |Returns information about a single bank specified by BANK_ID including:
        |
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      emptyObjectJson,
      decompose(BankJSON("gh.29.uk", "EFG", "Eurobank", "None", "www.eurobank.rs",BankRoutingJSON("obp","gh.29.uk"))),
      emptyObjectJson :: Nil,
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)


    lazy val bankById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get bank by id
      case "banks" :: BankId(bankId) :: Nil JsonGet json => {
        user =>
          def bankToJson(bank : Bank) : JValue = {
            val bankJSON = JSONFactory.createBankJSON(bank)
            Extraction.decompose(bankJSON)
          }
          for(bank <- Bank(bankId))
          yield successJsonResponse(bankToJson(bank))
      }
    }


    resourceDocs += ResourceDoc(
      allAccountsAllBanks,
      apiVersion,
      "allAccountsAllBanks",
      "GET",
      "/accounts",
      "Get accounts at all banks (Authenticated + Anonymous access).",
      """Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views. If
         |the user is authenticated, the list will contain non-public accounts to which the user has access, in addition to
         |all public accounts.
         |
         |Note for those upgrading from v1.2:
         |The v1.2 version of this call was buggy in that it did not include public accounts if an authenticated user made the call.
         |If you need the previous behaviour, please use the API call for private accounts (..../accounts/private).
         |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, PSD2, OBWG),
      apiTagAccount :: Nil)

    lazy val allAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for all banks (private + public)
      case "accounts" :: Nil JsonGet json => {
        user =>
          Full(successJsonResponse(bankAccountsListToJson(BankAccount.accounts(user), user)))
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAllBanks,
      apiVersion,
      "privateAccountsAllBanks",
      "GET",
      "/accounts/private",
      "Get private accounts at all banks (Authenticated access).",
      """Returns the list of private (non-public) accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, PSD2, OBWG),
      apiTagAccount :: Nil)

    lazy val privateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for all banks
      case "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
          } yield {
            val availableAccounts = BankAccount.nonPublicAccounts(u)
            successJsonResponse(bankAccountsListToJson(availableAccounts, Full(u)))
          }
      }
    }

    resourceDocs += ResourceDoc(
      publicAccountsAllBanks,
      apiVersion,
      "publicAccountsAllBanks",
      "GET",
      "/accounts/public",
      "Get public accounts at all banks (Anonymous access).",
      """Returns the list of private (non-public) accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views. Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount :: Nil)

    lazy val publicAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for all banks
      case "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          val publicAccountsJson = bankAccountsListToJson(BankAccount.publicAccounts, Empty)
          Full(successJsonResponse(publicAccountsJson))
      }
    }

    resourceDocs += ResourceDoc(
      allAccountsAtOneBank,
      apiVersion,
      "allAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts",
      "Get accounts at one bank (Autheneticated + Anonymous access).",
      """Returns the list of accounts at BANK_ID that the user has access to.
        |For each account the API returns the account ID and the available views.
        |
        |If the user is not authenticated via OAuth, the list will contain only the accounts providing public views.
        |
        |Note for those upgrading from v1.2:
        |The v1.2 version of this call was buggy in that it did not include public accounts if an authenticated user made the call.
        |If you need the previous behaviour, please use the API call for private accounts (..../accounts/private)
      """,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount :: Nil)

    lazy val allAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get accounts for a single bank (private + public)
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet json => {
        user =>
          for{
            bank <- Bank(bankId)
          } yield {
            val availableAccounts = bank.accounts(user)
            successJsonResponse(bankAccountsListToJson(availableAccounts, user))
          }
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      apiVersion,
      "privateAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get private accounts at one bank (Authenticated access).",
      """Returns the list of private (non-public) accounts at BANK_ID that the user has access to.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(Core, PSD2, OBWG),
      apiTagAccount :: Nil)

    lazy val privateAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            bank <- Bank(bankId)
          } yield {
            val availableAccounts = bank.nonPublicAccounts(u)
            successJsonResponse(bankAccountsListToJson(availableAccounts, Full(u)))
          }
      }
    }

    resourceDocs += ResourceDoc(
      publicAccountsAtOneBank,
      apiVersion,
      "publicAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/public",
      "Get public accounts at one bank (Anonymous access).",
      """Returns a list of the public accounts at BANK_ID. For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is not required.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount :: apiTagPublicData ::  Nil)

    lazy val publicAccountsAtOneBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "public" :: Nil JsonGet json => {
        user =>
          for {
            bank <- Bank(bankId)
          } yield {
            val publicAccountsJson = bankAccountsListToJson(bank.publicAccounts, Empty)
            successJsonResponse(publicAccountsJson)
          }
      }
    }

    resourceDocs += ResourceDoc(
      accountById,
      apiVersion,
      "accountById",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get account by id.",
      s"""Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
         |
         |* Number
         |* Owners
         |* Type
         |* Balance
         |* IBAN
         |* Available views
         |
         |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
         |
         |${authenticationRequiredMessage(false)}
         |
         |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  Nil)

    lazy val accountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            availableviews <- Full(account.permittedViews(user))
            view <- View.fromUrl(viewId, account)
            moderatedAccount <- account.moderatedBankAccount(view, user)
          } yield {
            val viewsAvailable = availableviews.map(JSONFactory.createViewJSON)
            val moderatedAccountJson = JSONFactory.createBankAccountJSON(moderatedAccount, viewsAvailable)
            successJsonResponse(Extraction.decompose(moderatedAccountJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateAccountLabel,
      apiVersion,
      "updateAccountLabel",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Update Account Label.",
      "Update the label for the account. The label is how the account is known to the account owner e.g. 'My savings account' ",
      Extraction.decompose(UpdateAccountJSON("ACCOUNT_ID of the account we want to update", "New label", "BANK_ID")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagMetaData))

    lazy val updateAccountLabel : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //change account label
      // TODO Use PATCH instead? Remove BANK_ID AND ACCOUNT_ID from the body? (duplicated in URL)
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ "user not found"
            json <- tryo { json.extract[UpdateAccountJSON] } ?~ "wrong JSON format"
            account <- BankAccount(bankId, accountId)
          } yield {
            account.updateLabel(u, json.label)
            successJsonResponse(Extraction.decompose(SuccessMessage("ok")), 200)
          }
      }
    }

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
            val viewsJSON = JSONFactory.createViewsJSON(views)
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
        | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.""",
      Extraction.decompose(CreateViewJSON("Name of view to create", "Description of view (this example is public, uses the public alias, and has limited access to account data)", true, "_public_", true, List("can_see_transaction_start_date", "can_see_bank_account_label", "can_see_tags"))),
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
            val viewJSON = JSONFactory.createViewJSON(view)
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
            val viewJSON = JSONFactory.createViewJSON(updatedView)
            successJsonResponse(Extraction.decompose(viewJSON), 200)
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteViewForBankAccount,
      apiVersion,
      "deleteViewForBankAccount",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID",
      "Delete View",
      "Deletes the view specified by VIEW_ID on the bank account specified by ACCOUNT_ID at bank BANK_ID.",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val deleteViewForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //deletes a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonDelete json => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            view <- account removeView (u, viewId)
          } yield noContentJsonResponse
      }
    }

    resourceDocs += ResourceDoc(
      getPermissionsForBankAccount,
      apiVersion,
      "getPermissionsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions",
      "Get access.",
      """Returns the list of the permissions at BANK_ID for account ACCOUNT_ID, with each time a pair composed of the user and the views that he has access to.
        |
        |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView, apiTagEntitlement)
    )

    lazy val getPermissionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get access
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            permissions <- account permissions u
          } yield {
            val permissionsJSON = JSONFactory.createPermissionsJSON(permissions)
            successJsonResponse(Extraction.decompose(permissionsJSON))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPermissionForUserForBankAccount,
      apiVersion,
      "getPermissionForUserForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID",
      "Get access for specific user.",
      """Returns the list of the views at BANK_ID for account ACCOUNT_ID that a USER_ID at their provider PROVIDER_ID has access to.
         |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
         |
         |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView, apiTagEntitlement))

    lazy val getPermissionForUserForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            permission <- account permission(u, providerId, userId)
          } yield {
            val views = JSONFactory.createViewsJSON(permission.views)
            successJsonResponse(Extraction.decompose(views))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addPermissionForUserForBankAccountForMultipleViews,
      apiVersion,
      "addPermissionForUserForBankAccountForMultipleViews",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views",
      "Grant User access to a list of views.",
      """Grants the user USER_ID at their provider PROVIDER_ID access to a list of views at BANK_ID for account ACCOUNT_ID.
         |
         |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
         |
         |OAuth authentication is required and the user needs to have access to the owner view.""",
      Extraction.decompose(ViewIdsJson(List("owner","auditor","investor"))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagView, apiTagEntitlement, apiTagOwnerRequired))

    lazy val addPermissionForUserForBankAccountForMultipleViews : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add access for specific user to a list of views
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: "views" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            viewIds <- tryo{json.extract[ViewIdsJson]} ?~ "wrong format JSON"
            addedViews <- account addPermissions(u, viewIds.views.map(viewIdString => ViewUID(ViewId(viewIdString), bankId, accountId)), providerId, userId)
          } yield {
            val viewJson = JSONFactory.createViewsJSON(addedViews)
            successJsonResponse(Extraction.decompose(viewJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      addPermissionForUserForBankAccountForOneView,
      apiVersion,
      "addPermissionForUserForBankAccountForOneView",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views/VIEW_ID",
      "Grant User access to View.",
      """Grants the user USER_ID at their provider PROVIDER_ID access to the view VIEW_ID at BANK_ID for account ACCOUNT_ID. All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
          |
          |OAuth authentication is required and the user needs to have access to the owner view.
          |
          |Granting access to a public view will return an error message, as the user already has access.""",
      emptyObjectJson, // No Json body required
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagView, apiTagEntitlement, apiTagOwnerRequired))

    lazy val addPermissionForUserForBankAccountForOneView : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add access for specific user to a specific view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: "views" :: ViewId(viewId) :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            // TODO Check Error cases
            addedView <- account addPermission(u, ViewUID(viewId, bankId, accountId), providerId, userId)
          } yield {
            val viewJson = JSONFactory.createViewJSON(addedView)
            successJsonResponse(Extraction.decompose(viewJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      removePermissionForUserForBankAccountForOneView,
      apiVersion,
      "removePermissionForUserForBankAccountForOneView",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views/VIEW_ID",
      "Revoke access to one View.",
      """Revokes the user USER_ID at their provider PROVIDER_ID access to the view VIEW_ID at BANK_ID for account ACCOUNT_ID.
        |
        |Revoking a user access to a public view will return an error message.
        |
        |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagView, apiTagEntitlement, apiTagOwnerRequired))

    lazy val removePermissionForUserForBankAccountForOneView : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete access for specific user to one view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: "views" :: ViewId(viewId) :: Nil JsonDelete json => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            isRevoked <- account revokePermission(u, ViewUID(viewId, bankId, accountId), providerId, userId)
            if(isRevoked)
          } yield noContentJsonResponse
      }
    }

    resourceDocs += ResourceDoc(
      removePermissionForUserForBankAccountForAllViews,
      apiVersion,
      "removePermissionForUserForBankAccountForAllViews",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views",
      "Revoke access to all Views on Account",
      """Revokes the user USER_ID at their provider PROVIDER_ID access to all the views at BANK_ID for account ACCOUNT_ID.
        |
        |OAuth authentication is required and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagView, apiTagEntitlement, apiTagOwnerRequired))

    lazy val removePermissionForUserForBankAccountForAllViews : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete access for specific user to all the views
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: "views" :: Nil JsonDelete json => {
        user =>
          for {
            u <- user ?~ "user not found"
            account <- BankAccount(bankId, accountId)
            isRevoked <- account revokeAllPermissions(u, providerId, userId)
            if(isRevoked)
          } yield noContentJsonResponse
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountsForBankAccount,
      apiVersion,
      "getOtherAccountsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts",
      "Get Other Accounts of one Account.",
      s"""Returns data about all the other accounts that have shared at least one transaction with the ACCOUNT_ID at BANK_ID.
        |${authenticationRequiredMessage(false)}
        |Authentication is required if the view VIEW_ID is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagCounterparty))

    lazy val getOtherAccountsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get other accounts for one account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccounts <- account.moderatedOtherBankAccounts(view, user)
          } yield {
            val otherBankAccountsJson = JSONFactory.createOtherBankAccountsJSON(otherBankAccounts)
            successJsonResponse(Extraction.decompose(otherBankAccountsJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountByIdForBankAccount,
      apiVersion,
      "getOtherAccountByIdForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
      "Get Other Account by Id.",
      s"""Returns data about the Other Account that has shared at least one transaction with ACCOUNT_ID at BANK_ID.
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagAccount, apiTagCounterparty))

    lazy val getOtherAccountByIdForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get one other account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
          } yield {
            val otherBankAccountJson = JSONFactory.createOtherBankAccount(otherBankAccount)
            successJsonResponse(Extraction.decompose(otherBankAccountJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountMetadata,
      apiVersion,
      "getOtherAccountMetadata",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata",
      "Get Other Account Metadata.",
      """Get metadata of one other account.
        |Returns only the metadata about one other bank account (OTHER_ACCOUNT_ID) that had shared at least one transaction with ACCOUNT_ID at BANK_ID.
        |
        |Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val getOtherAccountMetadata : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get metadata of one other account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
          } yield {
            val metadataJson = JSONFactory.createOtherAccountMetaDataJSON(metadata)
            successJsonResponse(Extraction.decompose(metadataJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCounterpartyPublicAlias,
      apiVersion,
      "getCounterpartyPublicAlias",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Get public alias of other bank account.",
      s"""Returns the public alias of the other account OTHER_ACCOUNT_ID.
        |${authenticationRequiredMessage(false)}
        |OAuth authentication is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val getCounterpartyPublicAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            alias <- Box(metadata.publicAlias) ?~ {"the view " + viewId + "does not allow public alias access"}
          } yield {
            val aliasJson = JSONFactory.createAliasJSON(alias)
            successJsonResponse(Extraction.decompose(aliasJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addCounterpartyPublicAlias,
      apiVersion,
      "addCounterpartyPublicAlias",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Add public alias to other bank account.",
      s"""Creates the public alias for the other account OTHER_ACCOUNT_ID.
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.
         |
         |Note: Public aliases are automatically generated for new 'other accounts / counterparties', so this call should only be used if
         |the public alias was deleted.
         |
         |The VIEW_ID parameter should be a view the caller is permitted to access to and that has permission to create public aliases.""",
      Extraction.decompose(AliasJSON("An Alias")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addCounterpartyPublicAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add public alias to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPost json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow adding a public alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addPublicAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be added"}
            if(added)
          } yield {
            successJsonResponse(Extraction.decompose(SuccessMessage("public alias added")), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyPublicAlias,
      apiVersion,
      "updateCounterpartyPublicAlias",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Update public alias of other bank account.",
      s"""Updates the public alias of the other account / counterparty OTHER_ACCOUNT_ID.
        |
        |${authenticationRequiredMessage(false)}
        |Authentication is required if the view is not public.""",
      Extraction.decompose(AliasJSON("An Alias")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPublicAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow updating the public alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addPublicAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be updated"}
            if(added)
          } yield {
            successJsonResponse(Extraction.decompose(SuccessMessage("public alias updated")))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyPublicAlias,
      apiVersion,
      "deleteCounterpartyPublicAlias",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Delete Counterparty Public Alias",
      s"""Deletes the public alias of the other account OTHER_ACCOUNT_ID.
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPublicAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow deleting the public alias"}
            added <- Counterparties.counterparties.vend.addPublicAlias(other_account_id, "") ?~ {"Alias cannot be deleted"}
            if(added)
          } yield noContentJsonResponse
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountPrivateAlias,
      apiVersion,
      "getOtherAccountPrivateAlias",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Get Other Account Private Alias",
      s"""Returns the private alias of the other account OTHER_ACCOUNT_ID.
        |
        |${authenticationRequiredMessage(false)}
        |Authentication is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val getOtherAccountPrivateAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            alias <- Box(metadata.privateAlias) ?~ {"the view " + viewId + "does not allow private alias access"}
          } yield {
            val aliasJson = JSONFactory.createAliasJSON(alias)
            successJsonResponse(Extraction.decompose(aliasJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addOtherAccountPrivateAlias,
      apiVersion,
      "addOtherAccountPrivateAlias",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Create Other Account Private Alias",
      s"""Creates a private alias for the other account OTHER_ACCOUNT_ID.
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.""",
      Extraction.decompose(AliasJSON("An Alias")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addOtherAccountPrivateAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add private alias to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPost json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow adding a private alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addPrivateAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be added"}
            if(added)
          } yield {
            val successJson = SuccessMessage("private alias added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyPrivateAlias,
      apiVersion,
      "updateCounterpartyPrivateAlias",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Update Counterparty Private Alias",
      s"""Updates the private alias of the counterparty (AKA other account) OTHER_ACCOUNT_ID.
        |
        |${authenticationRequiredMessage(false)}
        |Authentication is required if the view is not public.""",
      Extraction.decompose(AliasJSON("An Alias")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPrivateAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow updating the private alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {"wrong JSON format"}
            updated <- Counterparties.counterparties.vend.addPrivateAlias(other_account_id, aliasJson.alias) ?~ {"Alias cannot be updated"}
            if(updated)
          } yield {
            val successJson = SuccessMessage("private alias updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyPrivateAlias,
      apiVersion,
      "deleteCounterpartyPrivateAlias",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/private_alias",
      "Delete Counterparty Private Alias",
      s"""Deletes the private alias of the other account OTHER_ACCOUNT_ID.
        |
        |${authenticationRequiredMessage(false)}
        |Authentication is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPrivateAlias : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow deleting the private alias"}
            added <- Counterparties.counterparties.vend.addPrivateAlias(other_account_id, "") ?~ {"Alias cannot be deleted"}
            if(added)
          } yield noContentJsonResponse
      }
    }

    //TODO: get more info of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyMoreInfo,
      apiVersion,
      "addCounterpartyMoreInfo",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Add Counterparty More Info",
      "Add a description of the counter party from the perpestive of the account e.g. My dentist.",
      Extraction.decompose(MoreInfoJSON("More info")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addCounterpartyMoreInfo : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add more info to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonPost json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow adding more info"}
            moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addMoreInfo(other_account_id, moreInfoJson.more_info) ?~ {"More Info cannot be added"}
            if(added)
          } yield {
            val successJson = SuccessMessage("more info added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyMoreInfo,
      apiVersion,
      "updateCounterpartyMoreInfo",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Update Counterparty More Info",
      "Update the more info description of the counter party from the perpestive of the account e.g. My dentist.",
      Extraction.decompose(MoreInfoJSON("More info")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyMoreInfo : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update more info of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow updating more info"}
            moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {"wrong JSON format"}
            updated <- Counterparties.counterparties.vend.addMoreInfo(other_account_id, moreInfoJson.more_info) ?~ {"More Info cannot be updated"}
            if(updated)
          } yield {
            val successJson = SuccessMessage("more info updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyMoreInfo,
      apiVersion,
      "deleteCounterpartyMoreInfo",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Delete more info of other bank account.",
      "",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyMoreInfo : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete more info of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow deleting more info"}
            deleted <- Counterparties.counterparties.vend.addMoreInfo(other_account_id, "") ?~ {"More Info cannot be deleted"}
            if(deleted)
          } yield noContentJsonResponse
      }
    }

    //TODO: get url of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyUrl,
      apiVersion,
      "addCounterpartyUrl",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Add url to other bank account.",
      "A url which represents the counterparty (home page url etc.)",
      Extraction.decompose(UrlJSON("www.example.com")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))


    lazy val addCounterpartyUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonPost json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow adding a url"}
            urlJson <- tryo{(json.extract[UrlJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addURL(other_account_id, urlJson.URL) ?~ {"URL cannot be added"}
            if(added)
          } yield {
            val successJson = SuccessMessage("url added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyUrl,
      apiVersion,
      "updateCounterpartyUrl",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Update url of other bank account.",
      "A url which represents the counterparty (home page url etc.)",
      Extraction.decompose(UrlJSON("www.example.com")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow updating a url"}
            urlJson <- tryo{(json.extract[UrlJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addURL(other_account_id, urlJson.URL) ?~ {"URL cannot be updated"}
            if(added)
          } yield {
            val successJson = SuccessMessage("url updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyUrl,
      apiVersion,
      "deleteCounterpartyUrl",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Delete url of other bank account.",
      "",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow deleting a url"}
            added <- Counterparties.counterparties.vend.addURL(other_account_id, "") ?~ {"URL cannot be deleted"}
            if(added)
          } yield noContentJsonResponse
      }
    }

    //TODO: get image url of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyImageUrl,
      apiVersion,
      "addCounterpartyImageUrl",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Add image url to other bank account.",
      "Add a url that points to the logo of the counterparty",
      Extraction.decompose(ImageUrlJSON("www.example.com/logo.png")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addCounterpartyImageUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add image url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonPost json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow adding an image url"}
            imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addImageURL(other_account_id, imageUrlJson.image_URL) ?~ {"URL cannot be added"}
            if(added)
          } yield {
            val successJson = SuccessMessage("image url added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyImageUrl,
      apiVersion,
      "updateCounterpartyImageUrl",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Update Counterparty Image Url",
      "Update the url that points to the logo of the counterparty",
      Extraction.decompose(ImageUrlJSON("www.example.com/logo.png")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyImageUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update image url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow updating an image url"}
            imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {"wrong JSON format"}
            updated <- Counterparties.counterparties.vend.addImageURL(other_account_id, imageUrlJson.image_URL) ?~ {"URL cannot be updated"}
            if(updated)
          } yield {
            val successJson = SuccessMessage("image url updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyImageUrl,
      apiVersion,
      "deleteCounterpartyImageUrl",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Delete Counterparty Image URL",
      "Delete image url of other bank account.",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty)) // Tag general then specific for consistent sorting

    lazy val deleteCounterpartyImageUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete image url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow deleting an image url"}
            deleted <- Counterparties.counterparties.vend.addImageURL(other_account_id, "") ?~ {"URL cannot be deleted"}
            if(deleted)
          } yield noContentJsonResponse
      }
    }

    //TODO: get open corporates url of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyOpenCorporatesUrl,
      apiVersion,
      "addCounterpartyOpenCorporatesUrl",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/open_corporates_url",
      "Add Open Corporates URL to Counterparty",
      "Add open corporates url to other bank account.",
      Extraction.decompose(OpenCorporateUrlJSON("https://opencorporates.com/companies/gb/04351490")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addCounterpartyOpenCorporatesUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add open corporate url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonPost json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow adding an open corporate url"}
            openCorpUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {"wrong JSON format"}
            added <- Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, openCorpUrl.open_corporates_URL) ?~ {"URL cannot be added"}
            if(added)
          } yield {
            val successJson = SuccessMessage("open corporate url added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyOpenCorporatesUrl,
      apiVersion,
      "updateCounterpartyOpenCorporatesUrl",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/open_corporates_url",
      "Update Open Corporates Url of Counterparty",
      "Update open corporate url of other bank account.",
      Extraction.decompose(OpenCorporateUrlJSON("https://opencorporates.com/companies/gb/04351490")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyOpenCorporatesUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update open corporate url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonPut json -> _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow updating an open corporate url"}
            openCorpUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {"wrong JSON format"}
            updated <- Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, openCorpUrl.open_corporates_URL) ?~ {"URL cannot be updated"}
            if(updated)
          } yield {
            val successJson = SuccessMessage("open corporate url updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyOpenCorporatesUrl,
      apiVersion,
      "deleteCounterpartyOpenCorporatesUrl",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/open_corporates_url",
      "Delete Counterparty Open Corporates URL",
      "Delete open corporate url of other bank account.",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyOpenCorporatesUrl : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete open corporate url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow deleting an open corporate url"}
            deleted <- Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, "") ?~ {"URL cannot be deleted"}
            if(deleted)
          } yield noContentJsonResponse
      }
    }

    //TODO: get corporate location of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyCorporateLocation,
      apiVersion,
      "addCounterpartyCorporateLocation",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Add Corporate Location to Counterparty",
      "Add the geolocation of the counterparty's registered address",
      Extraction.decompose(CorporateLocationJSON(JSONFactory.createLocationPlainJSON(52.5571573,13.3728025))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addCounterpartyCorporateLocation : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add corporate location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow adding a corporate location"}
            corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {"wrong JSON format"}
            correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
            added <- Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.resourceUserId, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude) ?~ {"Corporate Location cannot be deleted"}
            if(added)
          } yield {
            val successJson = SuccessMessage("corporate location added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyCorporateLocation,
      apiVersion,
      "updateCounterpartyCorporateLocation",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Update Counterparty Corporate Location",
      "Update the geolocation of the counterparty's registered address",
      Extraction.decompose(CorporateLocationJSON(JSONFactory.createLocationPlainJSON(52.5571573,13.3728025))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyCorporateLocation : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update corporate location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow updating a corporate location"}
            corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {"wrong JSON format"}
            correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
            updated <- Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.resourceUserId, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude) ?~ {"Corporate Location cannot be updated"}
            if(updated)
          } yield {
            val successJson = SuccessMessage("corporate location updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyCorporateLocation,
      apiVersion,
      "deleteCounterpartyCorporateLocation",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Delete Counterparty Corporate Location.",
      "Delete corporate location of other bank account. Delete the geolocation of the counterparty's registered address",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyCorporateLocation : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete corporate location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonDelete _ => {
        user =>
          for {
            u <- user
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            deleted <- Counterparties.counterparties.vend.deleteCorporateLocation(other_account_id) ?~ {"Corporate Location cannot be deleted"}
          } yield {
            if(deleted)
              noContentJsonResponse
            else
              errorJsonResponse("Delete not completed")
          }
      }
    }

    //TODO: get physical location of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyPhysicalLocation,
      apiVersion,
      "addCounterpartyPhysicalLocation",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Add physical location to other bank account.",
      "Add geocoordinates of the counterparty's main location",
      Extraction.decompose(PhysicalLocationJSON(JSONFactory.createLocationPlainJSON(52.5571573,13.3728025))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val addCounterpartyPhysicalLocation : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add physical location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "metadata" :: "physical_location" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow adding a physical location"}
            physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {"wrong JSON format"}
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            added <- Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.resourceUserId, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude) ?~ {"Physical Location cannot be added"}
            if(added)
          } yield {
            val successJson = SuccessMessage("physical location added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyPhysicalLocation,
      apiVersion,
      "updateCounterpartyPhysicalLocation",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Update Counterparty Physical Location",
      "Update geocoordinates of the counterparty's main location",
      Extraction.decompose(PhysicalLocationJSON(JSONFactory.createLocationPlainJSON(52.5571573,13.3728025))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPhysicalLocation : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update physical location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "physical_location" :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow updating a physical location"}
            physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {"wrong JSON format"}
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            updated <- Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.resourceUserId, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude) ?~ {"Physical Location cannot be updated"}
            if(updated)
          } yield {
            val successJson = SuccessMessage("physical location updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyPhysicalLocation,
      apiVersion,
      "deleteCounterpartyPhysicalLocation",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Delete Counterparty Physical Location.",
      "Delete physical location of other bank account.",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPhysicalLocation : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete physical location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "physical_location" :: Nil JsonDelete _ => {
        user =>
          for {
            u <- user
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, user)
            metadata <- Box(otherBankAccount.metadata) ?~ {"the view " + viewId + "does not allow metadata access"}
            deleted <- Counterparties.counterparties.vend.deletePhysicalLocation(other_account_id) ?~ {"Physical Location cannot be deleted"}
          } yield {
            if(deleted)
              noContentJsonResponse
            else
              errorJsonResponse("Delete not completed")
          }
      }
    }

    resourceDocs += ResourceDoc(
      getTransactionsForBankAccount,
      apiVersion,
      "getTransactionsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions",
      "Get Transactions for Account (Full)",
      """Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
         |
         |Authentication via OAuth is required if the view is not public.
         |
         |Possible custom headers for pagination:
         |
         |* obp_sort_by=CRITERIA ==> default value: "completed" field
         |* obp_sort_direction=ASC/DESC ==> default value: DESC
         |* obp_limit=NUMBER ==> default value: 50
         |* obp_offset=NUMBER ==> default value: 0
         |* obp_from_date=DATE => default value: date of the oldest transaction registered (format below)
         |* obp_to_date=DATE => default value: date of the newest transaction registered (format below)
         |
         |**Date format parameter**: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2014-07-01T00:00:00.000Z) ==> time zone is UTC.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagTransaction))

    lazy val getTransactionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get transactions
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet json => {
        user =>

          for {
            params <- APIMethods121.getTransactionParams(json)
            bankAccount <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, bankAccount)
            transactions <- bankAccount.getModeratedTransactions(user, view, params : _*)
          } yield {
            val json = JSONFactory.createTransactionsJSON(transactions)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getTransactionByIdForBankAccount,
      apiVersion,
      "getTransactionByIdForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/transaction",
      "Get Transaction by Id.",
      s"""Returns one transaction specified by TRANSACTION_ID of the account ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.
         |
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagTransaction))

    lazy val getTransactionByIdForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get transaction by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            moderatedTransaction <- account.moderatedTransaction(transactionId, view, user)
          } yield {
            val json = JSONFactory.createTransactionJSON(moderatedTransaction)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getTransactionNarrative,
      apiVersion,
      "getTransactionNarrative",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Get narrative.",
      """Returns the account owner description of the transaction [moderated](#1_2_1-getViewsForBankAccount) by the view.
         |
         |Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val getTransactionNarrative : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonGet json => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            narrative <- Box(metadata.ownerComment) ?~ { "view " + viewId + " does not authorize narrative access" }
          } yield {
            val narrativeJson = JSONFactory.createTransactionNarrativeJSON(narrative)
            successJsonResponse(Extraction.decompose(narrativeJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addTransactionNarrative,
      apiVersion,
      "addTransactionNarrative",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Add narrative.",
      s"""Creates a description of the transaction TRANSACTION_ID.
         |
         |Note: Unlike other items of metadata, there is only one "narrative" per transaction accross all views.
         |If you set narrative via a view e.g. view-x it will be seen via view-y (as long as view-y has permission to see the narrative).
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.
         |""",
      Extraction.decompose(TransactionNarrativeJSON("My new (old!) piano")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val addTransactionNarrative : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user
            narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {"wrong json format"}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
            addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow adding a narrative"}
          } yield {
            addNarrative(narrativeJson.narrative)
            val successJson = SuccessMessage("narrative added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateTransactionNarrative,
      apiVersion,
      "updateTransactionNarrative",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Update narrative.",
      """Updates the description of the transaction TRANSACTION_ID.
         |
         |Authentication via OAuth is required if the view is not public.""",
      Extraction.decompose(TransactionNarrativeJSON("My new (old!) piano")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val updateTransactionNarrative : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user
            narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {"wrong json format"}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
            addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow updating a narrative"}
          } yield {
            addNarrative(narrativeJson.narrative)
            val successJson = SuccessMessage("narrative updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteTransactionNarrative,
      apiVersion,
      "deleteTransactionNarrative",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Delete narrative.",
      """Deletes the description of the transaction TRANSACTION_ID.
         |
         |Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val deleteTransactionNarrative : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonDelete _ => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            addNarrative <- Box(metadata.addOwnerComment) ?~ {"view " + viewId + " does not allow deleting the narrative"}
          } yield {
            addNarrative("")
            noContentJsonResponse
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCommentsForViewOnTransaction,
      apiVersion,
      "getCommentsForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments",
      "Get comments.",
      """Returns the transaction TRANSACTION_ID comments made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
         |
         |Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val getCommentsForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get comments
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonGet json => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            comments <- Box(metadata.comments) ?~ { "view " + viewId + " does not authorize comments access" }
          } yield {
            val json = JSONFactory.createTransactionCommentsJSON(comments)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addCommentForViewOnTransaction,
      apiVersion,
      "addCommentForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments",
      "Add comment.",
      """Posts a comment about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${authenticationRequiredMessage(false)}
         |
         |Authentication is required since the comment is linked with the user.""",
      Extraction.decompose(PostTransactionCommentJSON("Why did we spend money on this again?")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val addCommentForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add comment
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user
            commentJson <- tryo{json.extract[PostTransactionCommentJSON]} ?~ {"wrong json format"}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
            addCommentFunc <- Box(metadata.addComment) ?~ {"view " + viewId + " does not authorize adding comments"}
            postedComment <- addCommentFunc(u.resourceUserId, viewId, commentJson.value, now)
          } yield {
            successJsonResponse(Extraction.decompose(JSONFactory.createTransactionCommentJSON(postedComment)),201)
          }
      }
    }

    // Not able to update a comment (delete and add another)

    resourceDocs += ResourceDoc(
      deleteCommentForViewOnTransaction,
      apiVersion,
      "deleteCommentForViewOnTransaction",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments/COMMENT_ID",
      "Delete comment.",
      """Delete the comment COMMENT_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
         |
         |Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the comment.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val deleteCommentForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete comment
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments":: commentId :: Nil JsonDelete _ => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            delete <- metadata.deleteComment(commentId, user, account)
          } yield {
            noContentJsonResponse
          }
      }
    }

    resourceDocs += ResourceDoc(
      getTagsForViewOnTransaction,
      apiVersion,
      "getTagsForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags",
      "Get tags.",
      """Returns the transaction TRANSACTION_ID tags made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).

Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val getTagsForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get tags
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonGet json => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            tags <- Box(metadata.tags) ?~ { "view " + viewId + " does not authorize tag access" }
          } yield {
            val json = JSONFactory.createTransactionTagsJSON(tags)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addTagForViewOnTransaction,
      apiVersion,
      "addTagForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags",
      "Add a tag.",
      s"""Posts a tag about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${authenticationRequiredMessage(true)}
         |
         |Authentication is required as the tag is linked with the user.""",
      Extraction.decompose(PostTransactionTagJSON("holiday")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val addTagForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {

        user =>
          for {
            u <- user
            tagJson <- tryo{json.extract[PostTransactionTagJSON]} // TODO Error handling
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
            addTagFunc <- Box(metadata.addTag) ?~ {"view " + viewId + " does not authorize adding tags"}
            postedTag <- addTagFunc(u.resourceUserId, viewId, tagJson.value, now)
          } yield {
            successJsonResponse(Extraction.decompose(JSONFactory.createTransactionTagJSON(postedTag)), 201)
          }
      }
    }

    // No update tag (delete and add another)

    resourceDocs += ResourceDoc(
      deleteTagForViewOnTransaction,
      apiVersion,
      "deleteTagForViewOnTransaction",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags/TAG_ID",
      "Delete a tag.",
      """Deletes the tag TAG_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).

Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the tag.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val deleteTagForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {

        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            bankAccount <- BankAccount(bankId, accountId)
            deleted <- metadata.deleteTag(tagId, user, bankAccount)
          } yield {
            noContentJsonResponse
          }
      }
    }

    resourceDocs += ResourceDoc(
      getImagesForViewOnTransaction,
      apiVersion,
      "getImagesForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images",
      "Get images.",
      """Returns the transaction TRANSACTION_ID images made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).

Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val getImagesForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get images
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonGet json => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            images <- Box(metadata.images) ?~ { "view " + viewId + " does not authorize images access" }
          } yield {
            val json = JSONFactory.createTransactionImagesJSON(images)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addImageForViewOnTransaction,
      apiVersion,
      "addImageForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images",
      "Add an image.",
      s"""Posts an image about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${authenticationRequiredMessage(true)}
         |
         |The image is linked with the user.""",
      Extraction.decompose(PostTransactionImageJSON("The new printer", "www.example.com/images/printer.png")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val addImageForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add an image
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user
            imageJson <- tryo{json.extract[PostTransactionImageJSON]}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u))
            addImageFunc <- Box(metadata.addImage) ?~ {"view " + viewId + " does not authorize adding images"}
            url <- tryo{new URL(imageJson.URL)} ?~! "Could not parse url string as a valid URL"
            postedImage <- addImageFunc(u.resourceUserId, viewId, imageJson.label, now, url.toString)
          } yield {
            successJsonResponse(Extraction.decompose(JSONFactory.createTransactionImageJSON(postedImage)),201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteImageForViewOnTransaction,
      apiVersion,
      "deleteImageForViewOnTransaction",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images/IMAGE_ID",
      "Delete an image",
      """Deletes the image IMAGE_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
         |
         |Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the image.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val deleteImageForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete an image
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: imageId :: Nil JsonDelete _ => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            bankAccount <- BankAccount(bankId, accountId)
            deleted <- Box(metadata.deleteImage(imageId, user, bankAccount))
          } yield {
            noContentJsonResponse
          }
      }
    }

    resourceDocs += ResourceDoc(
      getWhereTagForViewOnTransaction,
      apiVersion,
      "getWhereTagForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Get where tag.",
      """Returns the "where" Geo tag added to the transaction TRANSACTION_ID made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
        |It represents the location where the transaction has been initiated.
        |
        |Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val getWhereTagForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonGet json => {
        user =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            where <- Box(metadata.whereTag) ?~ { "view " + viewId + " does not authorize where tag access" }
          } yield {
            val json = JSONFactory.createLocationJSON(where)
            val whereJson = TransactionWhereJSON(json)
            successJsonResponse(Extraction.decompose(whereJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addWhereTagForViewOnTransaction,
      apiVersion,
      "addWhereTagForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Add where tag.",
      s"""Creates a "where" Geo tag on a transaction TRANSACTION_ID in a [view](#1_2_1-getViewsForBankAccount).
         |
         |${authenticationRequiredMessage(true)}
         |
         |The geo tag is linked with the user.""",
      Extraction.decompose(PostTransactionWhereJSON(JSONFactory.createLocationPlainJSON(52.5571573,13.3728025))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val addWhereTagForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //add where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user
            view <- View.fromUrl(viewId, accountId, bankId)
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            addWhereTag <- Box(metadata.addWhereTag) ?~ {"the view " + viewId + "does not allow adding a where tag"}
            whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {"wrong JSON format"}
            correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
            if(addWhereTag(u.resourceUserId, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
          } yield {
            val successJson = SuccessMessage("where tag added")
            successJsonResponse(Extraction.decompose(successJson), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateWhereTagForViewOnTransaction,
      apiVersion,
      "updateWhereTagForViewOnTransaction",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Update where tag.",
      s"""Updates the "where" Geo tag on a transaction TRANSACTION_ID in a [view](#1_2_1-getViewsForBankAccount).
         |
         |${authenticationRequiredMessage(true)}
         |
         |The geo tag is linked with the user.""",
      Extraction.decompose(PostTransactionWhereJSON(JSONFactory.createLocationPlainJSON(52.5571573,13.3728025))),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val updateWhereTagForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //update where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user
            view <- View.fromUrl(viewId, accountId, bankId)
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            addWhereTag <- Box(metadata.addWhereTag) ?~ {"the view " + viewId + "does not allow updating a where tag"}
            whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {"wrong JSON format"}
            correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
            if(addWhereTag(u.resourceUserId, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
          } yield {
            val successJson = SuccessMessage("where tag updated")
            successJsonResponse(Extraction.decompose(successJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteWhereTagForViewOnTransaction,
      apiVersion,
      "deleteWhereTagForViewOnTransaction",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Delete where tag.",
      s"""Deletes the where tag of the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
         |
        |${authenticationRequiredMessage(true)}
        |
        |The user must either have owner privileges for this account, or must be the user that posted the geo tag.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetaData, apiTagTransaction))

    lazy val deleteWhereTagForViewOnTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //delete where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonDelete _ => {
        user =>
          for {
            bankAccount <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, bankAccount)
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, user)
            deleted <- metadata.deleteWhereTag(viewId, user, bankAccount)
          } yield {
            if(deleted)
              noContentJsonResponse
            else
              errorJsonResponse("Delete not completed")
          }
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountForTransaction,
      apiVersion,
      "getOtherAccountForTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/other_account",
      "Get Other Account of Transaction",
      """Get other account of a transaction.
         |Returns details of the other party involved in the transaction, moderated by the [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).

Authentication via OAuth is required if the view is not public.""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransaction, apiTagCounterparty))

    lazy val getOtherAccountForTransaction : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get other account of a transaction
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions":: TransactionId(transactionId) :: "other_account" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId)
            view <- View.fromUrl(viewId, account)
            transaction <- account.moderatedTransaction(transactionId, view, user)
            moderatedOtherBankAccount <- transaction.otherBankAccount
          } yield {
            val otherBankAccountJson = JSONFactory.createOtherBankAccount(moderatedOtherBankAccount)
            successJsonResponse(Extraction.decompose(otherBankAccountJson))
          }

      }
    }

    case class TransactionIdJson(transaction_id : String)

    resourceDocs += ResourceDoc(
      makePayment,
      apiVersion,
      "makePayment",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions",
      "Make Payment.",
      """This is an experimental call, currently only implemented in the OBP sandbox instance up to version 1.3.0. It is very minimal
         |and was superseded by Transaction Requests in version 1.4.0.
         |
         |This will only work if account to pay exists at the bank specified in the json, and if that account has the same currency as that of the payee.
         |
         |There are no checks for 'sufficient funds' at the moment, so it is possible to go into unlimited overdraft.""",
      Extraction.decompose(MakePaymentJson("To BANK_ID", "To ACCOUNT_ID", "12.45")),
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionRequest))

    lazy val makePayment : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonPost json -> _ => {
        user =>
          if (Props.getBool("payments_enabled", false)) {
            for {
              u <- user ?~ "User not found"
              makeTransJson <- tryo{json.extract[MakePaymentJson]} ?~ {"wrong json format"}
              rawAmt <- tryo {BigDecimal(makeTransJson.amount)} ?~! s"amount ${makeTransJson.amount} not convertible to number"
              toAccountUID = BankAccountUID(BankId(makeTransJson.bank_id), AccountId(makeTransJson.account_id))
              createdPaymentId <- Connector.connector.vend.makePayment(u, BankAccountUID(bankId, accountId), toAccountUID, rawAmt, "")
            } yield {
              val successJson = Extraction.decompose(TransactionIdJson(createdPaymentId.value))
              successJsonResponse(successJson)
            }
          } else{
            Failure("Sorry, payments are not enabled in this API instance.")
          }

      }
    }
  }
}

object APIMethods121 {
  import java.util.Date

  object DateParser {

    /**
    * first tries to parse dates using this pattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2012-07-01T00:00:00.000Z) ==> time zone is UTC
    * in case of failure (for backward compatibility reason), try "yyyy-MM-dd'T'HH:mm:ss.SSSZ" (2012-07-01T00:00:00.000+0000) ==> time zone has to be specified
    */
    def parse(date: String): Box[Date] = {
      import java.text.SimpleDateFormat

      val defaultFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      val fallBackFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

      val parsedDate = tryo{
        defaultFormat.parse(date)
      }

      lazy val fallBackParsedDate = tryo{
        fallBackFormat.parse(date)
      }

      if(parsedDate.isDefined){
        Full(parsedDate.get)
      }
      else if(fallBackParsedDate.isDefined){
        Full(fallBackParsedDate.get)
      }
      else{
        Failure(s"Failed to parse date string. Please use this format ${defaultFormat.toPattern} or that one ${fallBackFormat.toPattern}")
      }
    }
  }

  private def getSortDirection(req: Req): Box[OBPOrder] = {
    req.header("obp_sort_direction") match {
      case Full(v) => {
        if(v.toLowerCase == "desc" || v.toLowerCase == "asc"){
          Full(OBPOrder(Some(v.toLowerCase)))
        }
        else{
          Failure("obp_sort_direction parameter can only take two values: DESC or ASC")
        }
      }
      case _ => Full(OBPOrder(None))
    }
  }

  private def getFromDate(req: Req): Box[OBPFromDate] = {
    val date: Box[Date] = req.header("obp_from_date") match {
      case Full(d) => {
        DateParser.parse(d)
      }
      case _ => {
        Full(new Date(0))
      }
    }

    date.map(OBPFromDate(_))
  }

  private def getToDate(req: Req): Box[OBPToDate] = {
    val date: Box[Date] = req.header("obp_to_date") match {
      case Full(d) => {
        DateParser.parse(d)
      }
      case _ => Full(new Date())
    }

    date.map(OBPToDate(_))
  }

  private def getOffset(req: Req): Box[OBPOffset] = {
    val msg = "wrong value for obp_offset parameter. Please send a positive integer (=>0)"
    getPaginationParam(req, "obp_offset", 0, 0, msg)
    .map(OBPOffset(_))
  }

  private def getLimit(req: Req): Box[OBPLimit] = {
    val msg = "wrong value for obp_limit parameter. Please send a positive integer (=>1)"
    getPaginationParam(req, "obp_limit", 50, 1, msg)
    .map(OBPLimit(_))
  }

  private def getPaginationParam(req: Req, paramName: String, defaultValue: Int, minimumValue: Int, errorMsg: String): Box[Int]= {
    req.header(paramName) match {
      case Full(v) => {
        tryo{
          v.toInt
        } match {
          case Full(value) => {
            if(value >= minimumValue){
              Full(value)
            }
            else{
              Failure(errorMsg)
            }
          }
          case _ => Failure(errorMsg)
        }
      }
      case _ => Full(defaultValue)
    }
  }

  def getTransactionParams(req: Req): Box[List[OBPQueryParam]] = {
    for{
      sortDirection <- getSortDirection(req)
      fromDate <- getFromDate(req)
      toDate <- getToDate(req)
      limit <- getLimit(req)
      offset <- getOffset(req)
    }yield{

      /**
       * sortBy is currently disabled as it would open up a security hole:
       *
       * sortBy as currently implemented will take in a parameter that searches on the mongo field names. The issue here
       * is that it will sort on the true value, and not the moderated output. So if a view is supposed to return an alias name
       * rather than the true value, but someone uses sortBy on the other bank account name/holder, not only will the returned data
       * have the wrong order, but information about the true account holder name will be exposed due to its position in the sorted order
       *
       * This applies to all fields that can have their data concealed... which in theory will eventually be most/all
       *
       */
      //val sortBy = json.header("obp_sort_by")
      val sortBy = None
      val ordering = OBPOrdering(sortBy, sortDirection)
      limit :: offset :: ordering :: fromDate :: toDate :: Nil
    }
  }
}
