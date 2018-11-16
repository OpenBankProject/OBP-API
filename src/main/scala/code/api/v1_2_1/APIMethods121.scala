package code.api.v1_2_1

import java.net.URL
import java.util.Random
import java.util.UUID.randomUUID

import com.tesobe.CacheKeyFromArguments
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors._
import code.metadata.comments.Comments
import code.metadata.counterparties.Counterparties
import code.model.{CreateViewJson, UpdateViewJSON, _}
import code.util.Helper.booleanToBox
import code.views.Views
import com.google.common.cache.CacheBuilder
import net.liftweb.common.{Full, _}
import net.liftweb.http.JsonResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.language.postfixOps
import scalacache.ScalaCache
import scalacache.guava.GuavaCache

trait APIMethods121 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val apiMethods121GetTransactionsTTL = APIUtil.getPropsValue("connector.cache.ttl.seconds.APIMethods121.getTransactions", "0").toInt * 1000 // Miliseconds

  // helper methods begin here

  private def privateBankAccountsListToJson(bankAccounts: List[BankAccount], privateViewsUserCanAccess: List[View]): JValue = {
    val accJson : List[AccountJSON] = bankAccounts.map( account => {
      val viewsAvailable : List[ViewJSONV121] =
        privateViewsUserCanAccess
          .filter(v =>v.bankId==account.bankId && v.accountId ==account.accountId && v.isPrivate)//filter the view for this account.
          .map(JSONFactory.createViewJSON(_))
          .distinct
      JSONFactory.createAccountJSON(account,viewsAvailable)
    })

    val accounts = new AccountsJSON(accJson)
    Extraction.decompose(accounts)
  }

  private def publicBankAccountsListToJson(bankAccounts: List[BankAccount], publicViews: List[View]): JValue = {
    val accJson : List[AccountJSON] = bankAccounts.map( account => {
      val viewsAvailable : List[ViewJSONV121] =
        publicViews
          .filter(v =>v.bankId==account.bankId && v.accountId ==account.accountId && v.isPublic)
          .map(v => JSONFactory.createViewJSON(v))
          .distinct
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

  private def moderatedTransactionMetadata(bankId : BankId, accountId : AccountId, viewId : ViewId, transactionID : TransactionId, user : Box[User], callContext: Option[CallContext]) : Box[ModeratedTransactionMetadata] ={
    for {
      (account, callContext) <- BankAccount(bankId, accountId, callContext) ?~! BankAccountNotFound
      view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
      (moderatedTransaction, callContext) <- account.moderatedTransaction(transactionID, view, user, callContext)
      metadata <- Box(moderatedTransaction.metadata) ?~ { s"$NoViewPermission can_see_transaction_metadata. Current ViewId($viewId)" }
    } yield metadata
  }

  private def getApiInfoJSON(apiVersion : ApiVersion, apiVersionStatus : String) = {
    val apiDetails: JValue = {

      val organisation = APIUtil.getPropsValue("hosted_by.organisation", "TESOBE")
      val email = APIUtil.getPropsValue("hosted_by.email", "contact@tesobe.com")
      val phone = APIUtil.getPropsValue("hosted_by.phone", "+49 (0)30 8145 3994")
      val organisationWebsite = APIUtil.getPropsValue("organisation_website", "https://www.tesobe.com")

      val connector = APIUtil.getPropsValue("connector").openOrThrowException("no connector set")

      val hostedBy = new HostedBy(organisation, email, phone, organisationWebsite)
      val apiInfoJSON = new APIInfoJSON(apiVersion.vDottedApiVersion(), apiVersionStatus, gitCommit, connector, hostedBy)
      Extraction.decompose(apiInfoJSON)
    }
    apiDetails
  }

  // helper methods end here

  val Implementations1_2_1 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson = EmptyClassJson()
    val apiVersion : ApiVersion = ApiVersion.v1_2_1 // was String "1_2_1"
    val apiVersionStatus : String = "STABLE"

    resourceDocs += ResourceDoc(
      root(apiVersion, apiVersionStatus),
      apiVersion,
      "root",
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      emptyObjectJson,
      apiInfoJSON,
      List(UnknownError, "no connector set"),
      Catalogs(Core, PSD2, OBWG),
      apiTagApi :: Nil)

    def root(apiVersion : ApiVersion, apiVersionStatus: String) : OBPEndpoint = {
      case "root" :: Nil JsonGet req => cc =>Full(successJsonResponse(getApiInfoJSON(apiVersion, apiVersionStatus), 200))
      case Nil JsonGet req => cc =>Full(successJsonResponse(getApiInfoJSON(apiVersion, apiVersionStatus), 200))
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
      banksJSON,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getBanks : OBPEndpoint = {
      //get banks
      case "banks" :: Nil JsonGet req => {
        cc =>
          def banksToJson(banksList: List[Bank]): JValue = {
            val banksJSON: List[BankJSON] = banksList.map(b => {
              JSONFactory.createBankJSON(b)
            })
            val banks = new BanksJSON(banksJSON)
            Extraction.decompose(banks)
          }
          for((banks, callContext)<- Connector.connector.vend.getBanks(Some(cc)))
            yield(successJsonResponse(banksToJson(banks)))
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
      bankJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)


    lazy val bankById : OBPEndpoint = {
      //get bank by id
      case "banks" :: BankId(bankId) :: Nil JsonGet req => {
        cc =>
          def bankToJson(bank : Bank) : JValue = {
            val bankJSON = JSONFactory.createBankJSON(bank)
            Extraction.decompose(bankJSON)
          }
          for((bank, callContext)<- Bank(bankId, Some(cc)) ?~! BankNotFound)
          yield successJsonResponse(bankToJson(bank))
      }
    }


    resourceDocs += ResourceDoc(
      getPrivateAccountsAllBanks,
      apiVersion,
      "getPrivateAccountsAllBanks",
      "GET",
      "/accounts",
      "Get accounts at all banks (Private, inc views).",
      s"""Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      emptyObjectJson,
      accountJSON,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      apiTagAccount :: Nil)

    //TODO double check with `lazy val privateAccountsAllBanks :`, they are the same now.
    lazy val getPrivateAccountsAllBanks : OBPEndpoint = {
      //get accounts for all banks (private + public)
      case "accounts" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            privateViewsUserCanAccess <- Full(Views.views.vend.privateViewsUserCanAccess(u))
            availablePrivateAccounts <- Full(BankAccount.privateAccounts(privateViewsUserCanAccess))
          } yield {
            successJsonResponse(privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccess))
          }
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAllBanks,
      apiVersion,
      "privateAccountsAllBanks",
      "GET",
      "/accounts/private",
      "Get private accounts at all banks (Authenticated access).",
      """Returns the list of private accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.
        |
        |""".stripMargin,
      emptyObjectJson,
      accountJSON,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      apiTagAccount :: Nil)

    lazy val privateAccountsAllBanks : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: "private" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            privateViewsUserCanAccess <- Full(Views.views.vend.privateViewsUserCanAccess(u))
            privateAccounts <- Full(BankAccount.privateAccounts(privateViewsUserCanAccess))
          } yield {
            successJsonResponse(privateBankAccountsListToJson(privateAccounts, privateViewsUserCanAccess))
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
      """
        |Returns the list of private accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views. 
        |Authentication via OAuth is required.
        |
        |""".stripMargin,
      emptyObjectJson,
      accountJSON,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount :: Nil)

    lazy val publicAccountsAllBanks : OBPEndpoint = {
      //get public accounts for all banks
      case "accounts" :: "public" :: Nil JsonGet req => {
        cc =>
          for{
            publicViews <- Full(Views.views.vend.publicViews)
            publicAccounts <- Full(BankAccount.publicAccounts(publicViews))
            publicAccountsJson <- Full(publicBankAccountsListToJson(publicAccounts, publicViews))
          } yield{
            successJsonResponse(publicAccountsJson)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPrivateAccountsAtOneBank,
      apiVersion,
      "getPrivateAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts",
      "Get accounts at bank (Private).",
      s"""Returns the list of accounts at BANK_ID that the user has access to.
        |For each account the API returns the account ID and the available views.
        |
        |${authenticationRequiredMessage(true)}
        |
      """,
      emptyObjectJson,
      accountJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount :: Nil)

    //TODO, double check with `lazy val privateAccountsAtOneBank`, they are the same now.
    lazy val getPrivateAccountsAtOneBank : OBPEndpoint = {
      //get accounts for a single bank (private + public)
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet req => {
        cc =>
          for{
            u <- cc.user ?~! ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
          } yield {
            val privateViewsUserCanAccessAtOneBank = Views.views.vend.privateViewsUserCanAccess(u).filter(_.bankId == bankId)
            val availablePrivateAccounts = bank.privateAccounts(privateViewsUserCanAccessAtOneBank)
            successJsonResponse(privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccessAtOneBank))
          }
      }
    }

    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      apiVersion,
      "privateAccountsAtOneBank",
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get private accounts at one bank.",
      s"""Returns the list of private accounts at BANK_ID that the user has access to.
        |For each account the API returns the ID and the available views.
        |
        |${authenticationRequiredMessage(true)}
        |
        |""".stripMargin,
      emptyObjectJson,
      accountJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount))

    lazy val privateAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
          } yield {
            val privateViewsUserCanAccessAtOneBank = Views.views.vend.privateViewsUserCanAccess(u).filter(_.bankId == bankId)
            val availablePrivateAccounts = bank.privateAccounts(privateViewsUserCanAccessAtOneBank)
            successJsonResponse(privateBankAccountsListToJson(availablePrivateAccounts, privateViewsUserCanAccessAtOneBank))
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
        |Authentication via OAuth is not required.
        |
        |""".stripMargin,
      emptyObjectJson,
      accountJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccountPublic :: apiTagAccount :: apiTagPublicData ::  Nil)

    lazy val publicAccountsAtOneBank : OBPEndpoint = {
      //get public accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "public" :: Nil JsonGet req => {
        cc =>
          for {
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            publicViewsForBank <- Full(Views.views.vend.publicViewsForBank(bank.bankId))
            publicAccounts<- Full(bank.publicAccounts(publicViewsForBank))
          } yield {
            val publicAccountsJson = publicBankAccountsListToJson(publicAccounts, publicViewsForBank)
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
         |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
         |
         |""".stripMargin,
      emptyObjectJson,
      moderatedAccountJSON,
      List(UserNotLoggedIn, UnknownError, BankAccountNotFound),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  Nil)

    lazy val accountById : OBPEndpoint = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            (account, callContext) <- BankAccount(bankId, accountId, Some(cc)) ?~! BankAccountNotFound
            availableviews <- Full(Views.views.vend.privateViewsUserCanAccessForAccount(u, BankIdAccountId(account.bankId, account.accountId)))
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            moderatedAccount <- account.moderatedBankAccount(view, cc.user)
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
      s"""Update the label for the account. The label is how the account is known to the account owner e.g. 'My savings account'
         |
         |
         |${authenticationRequiredMessage(true)}
         |
       """.stripMargin,
      updateAccountJSON,
      successMessage,
      List(InvalidJsonFormat, UserNotLoggedIn, UnknownError, BankAccountNotFound, "user does not have access to owner view on account"),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount)
    )

    lazy val updateAccountLabel : OBPEndpoint = {
      //change account label
      // TODO Remove BANK_ID AND ACCOUNT_ID from the body? (duplicated in URL)
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            json <- tryo { json.extract[UpdateAccountJSON] } ?~ InvalidJsonFormat
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
          } yield {
            account.updateLabel(u, json.label)
            successJsonResponse(Extraction.decompose(successMessage), 200)
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
      s"""#Views
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
         |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.""",
      emptyObjectJson,
      viewsJSONV121,
      List(UserNotLoggedIn, BankAccountNotFound, UnknownError, "user does not have owner access"),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            _ <- booleanToBox(u.hasOwnerViewAccess(BankIdAccountId(account.bankId, account.accountId)), UserNoOwnerView +"userId : " + u.userId + ". account : " + accountId)
            views <- Full(Views.views.vend.viewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
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
      s"""#Create a view on bank account
        |
        | ${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
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
      createViewJsonV121,
      viewJSONV121,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        BankAccountNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView)
    )

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            createViewJsonV121 <- tryo{json.extract[CreateViewJsonV121]} ?~ InvalidJsonFormat
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _<- booleanToBox(createViewJsonV121.name.startsWith("_"), InvalidCustomViewFormat)
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            createViewJson = CreateViewJson(
              createViewJsonV121.name,
              createViewJsonV121.description,
              metadata_view = "", //this only used from V300
              createViewJsonV121.is_public,
              createViewJsonV121.which_alias_to_use,
              createViewJsonV121.hide_metadata_if_alias_used,
              createViewJsonV121.allowed_actions
            )
            view <- account createView (u, createViewJson)
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
      s"""Update an existing view on a bank account
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
        |
        |The json sent is the same as during view creation (above), with one difference: the 'name' field
        |of a view is not editable (it is only set when a view is created)""",
      updateViewJsonV121,
      viewJSONV121,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView)
    )
  
    lazy val updateViewForBankAccount: OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId
      ) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            updateJsonV121 <- tryo{ json.extract[UpdateViewJsonV121] } ?~ InvalidJsonFormat
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            u <- cc.user ?~  UserNotLoggedIn
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _ <- booleanToBox(viewId.value.startsWith("_"), InvalidCustomViewFormat)
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankId, accountId))
            _ <- booleanToBox(!view.isSystem, SystemViewsCanNotBeModified)
            updateViewJson = UpdateViewJSON(
              updateJsonV121.description,
              metadata_view = view.metadataView, //this only used from V300, here just copy from currentView . 
              updateJsonV121.is_public,
              updateJsonV121.which_alias_to_use,
              updateJsonV121.hide_metadata_if_alias_used,
              updateJsonV121.allowed_actions
            )
            updatedView <- account.updateView(u, viewId, updateViewJson)
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount)
    )
  
    lazy val deleteViewForBankAccount: OBPEndpoint = {
      //deletes a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId
      ) :: "views" :: ViewId(viewId) :: Nil JsonDelete req => {
        cc =>
          for {
            //customer views are started ith `_`,eg _lift, _work, and System views startWith letter, eg: owner
            _ <- booleanToBox(viewId.value.startsWith("_"), InvalidCustomViewFormat)
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankId, accountId))
            _ <- booleanToBox(!view.isSystem, SystemViewsCanNotBeModified)
            
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- account removeView(u, viewId)
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
      s"""Returns the list of the permissions at BANK_ID for account ACCOUNT_ID, with each time a pair composed of the user and the views that he has access to.
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.""",
      emptyObjectJson,
      permissionsJSON,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagEntitlement)
    )
  
    lazy val getPermissionsForBankAccount: OBPEndpoint = {
      //get access
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
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
      s"""Returns the list of the views at BANK_ID for account ACCOUNT_ID that a USER_ID at their provider PROVIDER_ID has access to.
        |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.""",
      emptyObjectJson,
      viewsJSONV121,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have access to owner view on account"
    ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView, apiTagEntitlement)
    )
  
  
    lazy val getPermissionForUserForBankAccount: OBPEndpoint = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: providerId :: userId :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
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
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views",
      "Grant User access to a list of views.",
      s"""Grants the user identified by PROVIDER_ID at their provider PROVIDER access to a list of views at BANK_ID for account ACCOUNT_ID.
        |
        |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for PROVIDER_ID and PROVIDER.
        |
        |${authenticationRequiredMessage(true)}
        |
        |The User needs to have access to the owner view.""",
      viewIdsJson,
      viewsJSONV121,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "wrong format JSON",
        "could not save the privilege",
        "user does not have access to owner view on account"
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val addPermissionForUserForBankAccountForMultipleViews : OBPEndpoint = {
      //add access for specific user to a list of views
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            viewIds <- tryo{json.extract[ViewIdsJson]} ?~ "wrong format JSON"
            addedViews <- account addPermissions(u, viewIds.views.map(viewIdString => ViewIdBankIdAccountId(ViewId(viewIdString), bankId, accountId)), provider, providerId)
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
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views/VIEW_ID",
      "Grant User access to View.",
      s"""Grants the User identified by PROVIDER_ID at PROVIDER access to the view VIEW_ID at BANK_ID for account ACCOUNT_ID.
          |
          |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for PROVIDER and PROVIDER_ID.
          |
          |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
          |
          |Granting access to a public view will return an error message, as the user already has access.""",
      emptyObjectJson, // No Json body required
      viewJSONV121,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "could not save the privilege",
        "user does not have access to owner view on account"
        ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val addPermissionForUserForBankAccountForOneView : OBPEndpoint = {
      //add access for specific user to a specific view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: ViewId(viewId) :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            // TODO Check Error cases
            addedView <- account addPermission(u, ViewIdBankIdAccountId(viewId, bankId, accountId), provider, providerId)
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
      s"""Revokes the user identified by PROVIDER_ID at their provider PROVIDER access to the view VIEW_ID at BANK_ID for account ACCOUNT_ID.
        |
        |Revoking a user access to a public view will return an error message.
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "could not save the privilege",
        "user does not have access to owner view on account",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagEntitlement, apiTagOwnerRequired))

    lazy val removePermissionForUserForBankAccountForOneView : OBPEndpoint = {
      //delete access for specific user to one view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: ViewId(viewId) :: Nil JsonDelete req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            isRevoked <- account revokePermission(u, ViewIdBankIdAccountId(viewId, bankId, accountId), provider, providerId)
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
      s"""Revokes the user identified by PROVIDER_ID at their provider PROVIDER access to all the views at BANK_ID for account ACCOUNT_ID.
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have access to owner view on account"
        ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val removePermissionForUserForBankAccountForAllViews : OBPEndpoint = {
      //delete access for specific user to all the views
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: Nil JsonDelete req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            isRevoked <- account revokeAllPermissions(u, provider, providerId)
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
      otherAccountsJSON,
      List(
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount))

    lazy val getOtherAccountsForBankAccount : OBPEndpoint = {
      //get other accounts for one account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet req => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccounts <- account.moderatedOtherBankAccounts(view, cc.user)
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
      otherAccountJSON,
      List(BankAccountNotFound, UnknownError),
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount))

    lazy val getOtherAccountByIdForBankAccount : OBPEndpoint = {
      //get one other account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet req => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~!BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
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
      otherAccountMetadataJSON,
      List(UserNotLoggedIn, UnknownError, "the view does not allow metadata access"),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val getOtherAccountMetadata : OBPEndpoint = {
      //get metadata of one other account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: Nil JsonGet req => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
        |${authenticationRequiredMessage(true)} if the view is not public.""",
      emptyObjectJson,
      aliasJSON,
      List(
        BankAccountNotFound,
        UnknownError,
        "the view does not allow metadata access",
        "the view does not allow public alias access"
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val getCounterpartyPublicAlias : OBPEndpoint = {
      //get public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonGet req => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      aliasJSON,
      successMessage,
      List(
        BankAccountNotFound,
        InvalidJsonFormat,
        UnknownError,
        "the view does not allow metadata access",
        "the view does not allow adding a public alias",
        "Alias cannot be added",
        "public alias added"
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyPublicAlias : OBPEndpoint = {
      //add public alias to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow adding a public alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {InvalidJsonFormat}
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
      aliasJSON,
      successMessage,
      List(
        BankAccountNotFound,
        InvalidJsonFormat,
        UserNotLoggedIn,
        "the view does not allow metadata access",
        "the view does not allow updating the public alias",
        "Alias cannot be updated",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPublicAlias : OBPEndpoint = {
      //update public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addAlias <- Box(metadata.addPublicAlias) ?~ {"the view " + viewId + "does not allow updating the public alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {InvalidJsonFormat}
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
      List(
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting the public alias",
        "Alias cannot be deleted",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPublicAlias : OBPEndpoint = {
      //delete public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      aliasJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow private alias access",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val getOtherAccountPrivateAlias : OBPEndpoint = {
      //get private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonGet req => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      aliasJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow adding a private alias",
        "Alias cannot be added",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addOtherAccountPrivateAlias : OBPEndpoint = {
      //add private alias to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow adding a private alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {InvalidJsonFormat}
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
      aliasJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow updating the private alias",
        "Alias cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPrivateAlias : OBPEndpoint = {
      //update private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addAlias <- Box(metadata.addPrivateAlias) ?~ {"the view " + viewId + "does not allow updating the private alias"}
            aliasJson <- tryo{(json.extract[AliasJSON])} ?~ {InvalidJsonFormat}
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting the private alias",
        "Alias cannot be deleted",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPrivateAlias : OBPEndpoint = {
      //delete private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      moreInfoJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        "the view " + viewId + "does not allow adding more info",
        "More Info cannot be added",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyMoreInfo : OBPEndpoint = {
      //add more info to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow adding more info"}
            moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {InvalidJsonFormat}
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
      moreInfoJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow updating more info",
        "More Info cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyMoreInfo : OBPEndpoint = {
      //update more info of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addMoreInfo <- Box(metadata.addMoreInfo) ?~ {"the view " + viewId + "does not allow updating more info"}
            moreInfoJson <- tryo{(json.extract[MoreInfoJSON])} ?~ {InvalidJsonFormat}
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting more info",
        "More Info cannot be deleted",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyMoreInfo : OBPEndpoint = {
      //delete more info of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      urlJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow adding a url",
        "URL cannot be added",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))


    lazy val addCounterpartyUrl : OBPEndpoint = {
      //add url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow adding a url"}
            urlJson <- tryo{(json.extract[UrlJSON])} ?~ {InvalidJsonFormat}
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
      urlJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        ViewNotFound,
        "URL cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyUrl : OBPEndpoint = {
      //update url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addUrl <- Box(metadata.addURL) ?~ {"the view " + viewId + "does not allow updating a url"}
            urlJson <- tryo{(json.extract[UrlJSON])} ?~ {InvalidJsonFormat}
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting a url",
        "URL cannot be deleted",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyUrl : OBPEndpoint = {
      //delete url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      imageUrlJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow adding an image url",
        "URL cannot be added",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyImageUrl : OBPEndpoint = {
      //add image url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow adding an image url"}
            imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {InvalidJsonFormat}
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
      imageUrlJSON,
      successMessage,
      List(
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow updating an image url",
        "URL cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyImageUrl : OBPEndpoint = {
      //update image url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addImageUrl <- Box(metadata.addImageURL) ?~ {"the view " + viewId + "does not allow updating an image url"}
            imageUrlJson <- tryo{(json.extract[ImageUrlJSON])} ?~ {InvalidJsonFormat}
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
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty)) // Tag general then specific for consistent sorting

    lazy val deleteCounterpartyImageUrl : OBPEndpoint = {
      //delete image url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      openCorporateUrlJSON,
      successMessage,
      List(
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow adding an open corporate url",
        "URL cannot be added",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyOpenCorporatesUrl : OBPEndpoint = {
      //add open corporate url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow adding an open corporate url"}
            openCorpUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {InvalidJsonFormat}
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
      openCorporateUrlJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow updating an open corporate url",
        "URL cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyOpenCorporatesUrl : OBPEndpoint = {
      //update open corporate url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addOpenCorpUrl <- Box(metadata.addOpenCorporatesURL) ?~ {"the view " + viewId + "does not allow updating an open corporate url"}
            openCorpUrl <- tryo{(json.extract[OpenCorporateUrlJSON])} ?~ {InvalidJsonFormat}
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting an open corporate url",
        "URL cannot be deleted",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyOpenCorporatesUrl : OBPEndpoint = {
      //delete open corporate url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      corporateLocationJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow adding a corporate location",
        "Coordinates not possible",
        "Corporate Location cannot be deleted",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyCorporateLocation : OBPEndpoint = {
      //add corporate location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow adding a corporate location"}
            corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {InvalidJsonFormat}
            correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
            added <- Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude) ?~ {"Corporate Location cannot be deleted"}
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
      corporateLocationJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow updating a corporate location",
        "Coordinates not possible",
        "Corporate Location cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyCorporateLocation : OBPEndpoint = {
      //update corporate location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addCorpLocation <- Box(metadata.addCorporateLocation) ?~ {"the view " + viewId + "does not allow updating a corporate location"}
            corpLocationJson <- tryo{(json.extract[CorporateLocationJSON])} ?~ {InvalidJsonFormat}
            correctCoordinates <- checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude)
            updated <- Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude) ?~ {"Corporate Location cannot be updated"}
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "Corporate Location cannot be deleted",
        "Delete not completed",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyCorporateLocation : OBPEndpoint = {
      //delete corporate location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonDelete _ => {
        cc =>
          for {
            u <- cc.user
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
      physicalLocationJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow adding a physical location",
        "Coordinates not possible",
        "Physical Location cannot be added",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyPhysicalLocation : OBPEndpoint = {
      //add physical location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "metadata" :: "physical_location" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow adding a physical location"}
            physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {InvalidJsonFormat}
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            added <- Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude) ?~ {"Physical Location cannot be added"}
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
      physicalLocationJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow updating a physical location",
        "Coordinates not possible",
        "Physical Location cannot be updated",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPhysicalLocation : OBPEndpoint = {
      //update physical location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "physical_location" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
            addPhysicalLocation <- Box(metadata.addPhysicalLocation) ?~ {"the view " + viewId + "does not allow updating a physical location"}
            physicalLocationJson <- tryo{(json.extract[PhysicalLocationJSON])} ?~ {InvalidJsonFormat}
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            correctCoordinates <- checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude)
            updated <- Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude) ?~ {"Physical Location cannot be updated"}
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        "Physical Location cannot be deleted",
        "Delete not completed",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPhysicalLocation : OBPEndpoint = {
      //delete physical location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "physical_location" :: Nil JsonDelete _ => {
        cc =>
          for {
            u <- cc.user
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
            metadata <- Box(otherBankAccount.metadata) ?~ { s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)" }
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
         |**Date format parameter**: $DateWithMs($DateWithMsExampleString) ==> time zone is UTC.""",
      emptyObjectJson,
      transactionsJSON,
      List(BankAccountNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransaction, apiTagAccount))
  
  
  
  
    private def getTransactionsForBankAccountCached(
      paramsBox:  Box[List[OBPQueryParam]],
      user: Box[User],
      accountId: AccountId,
      bankId: BankId,
      viewId : ViewId
    ): Box[JsonResponse] = {
      /**
        * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(apiMethods121GetTransactionsTTL millisecond) {
          for {
            params <- paramsBox
            bankAccount <- BankAccount(bankId, accountId)
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId))
            (transactions, callContext) <- bankAccount.getModeratedTransactions(user, view, None, params: _* )
          } yield {
            val json = JSONFactory.createTransactionsJSON(transactions)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }
  
    lazy val getTransactionsForBankAccount : OBPEndpoint =  {
      //get transactions
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
        val paramsBox: Box[List[OBPQueryParam]] = createQueriesByHttpParams(req.request.headers)
        cc => getTransactionsForBankAccountCached(
          paramsBox:  Box[List[OBPQueryParam]],
          cc.user: Box[User],
          accountId: AccountId,
          bankId: BankId,
          viewId : ViewId
        )
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
      transactionJSON,
      List(BankAccountNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransaction))

    lazy val getTransactionByIdForBankAccount : OBPEndpoint = {
      //get transaction by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet req => {
        cc =>
          for {
            (account, callContext) <- BankAccount(bankId, accountId, Some(cc)) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            (moderatedTransaction, callContext) <- account.moderatedTransaction(transactionId, view, cc.user, Some(cc))
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
      transactionNarrativeJSON,
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getTransactionNarrative : OBPEndpoint = {
      //get narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonGet req => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            narrative <- Box(metadata.ownerComment) ?~ { s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)" }
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
      transactionNarrativeJSON,
      successMessage,
      List(
        InvalidJsonFormat,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addTransactionNarrative : OBPEndpoint = {
      //add narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user
            narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {InvalidJsonFormat}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u), Some(cc))
            addNarrative <- Box(metadata.addOwnerComment) ?~ { s"$NoViewPermission can_add_owner_comment. Current ViewId($viewId)" }
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
      transactionNarrativeJSON,
      successMessage,
      List(InvalidJsonFormat,
           BankAccountNotFound,
           NoViewPermission,
           ViewNotFound,
           UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val updateTransactionNarrative : OBPEndpoint = {
      //update narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user
            narrativeJson <- tryo{json.extract[TransactionNarrativeJSON]} ?~ {InvalidJsonFormat}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u), Some(cc))
            addNarrative <- Box(metadata.addOwnerComment) ?~ { s"$NoViewPermission can_add_owner_comment. Current ViewId($viewId)" }
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteTransactionNarrative : OBPEndpoint = {
      //delete narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonDelete _ => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            addNarrative <- Box(metadata.addOwnerComment) ?~ { s"$NoViewPermission can_delete_owner_comment. Current ViewId($viewId)" }
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
      transactionCommentsJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getCommentsForViewOnTransaction : OBPEndpoint = {
      //get comments
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonGet req => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            comments <- Box(metadata.comments) ?~! { s"$NoViewPermission can_see_comments. Current ViewId($viewId)" }
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
      postTransactionCommentJSON,
      transactionCommentJSON,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addCommentForViewOnTransaction : OBPEndpoint = {
      //add comment
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user
            commentJson <- tryo{json.extract[PostTransactionCommentJSON]} ?~ {InvalidJsonFormat}
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u), Some(cc))
            addCommentFunc <- Box(metadata.addComment) ?~ { s"$NoViewPermission can_add_comment. Current ViewId($viewId)" }
            postedComment <- addCommentFunc(u.userPrimaryKey, viewId, commentJson.value, now)
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
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UserNotLoggedIn,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteCommentForViewOnTransaction : OBPEndpoint = {
      //delete comment
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments":: commentId :: Nil JsonDelete _ => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            delete <- metadata.deleteComment(commentId, cc.user, account)
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
      transactionTagJSON,
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getTagsForViewOnTransaction : OBPEndpoint = {
      //get tags
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonGet req => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            tags <- Box(metadata.tags) ?~ { s"$NoViewPermission can_see_tags. Current ViewId($viewId)" }
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
      postTransactionTagJSON,
      transactionTagJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addTagForViewOnTransaction : OBPEndpoint = {
      //add a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {

        cc =>
          for {
            u <- cc.user
            tagJson <- tryo{json.extract[PostTransactionTagJSON]} ?~ { s"$InvalidJsonFormat Check your Post Json Body." }
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u), Some(cc))
            addTagFunc <- Box(metadata.addTag) ?~ { s"$NoViewPermission can_add_tag. Current ViewId($viewId)" }
            postedTag <- addTagFunc(u.userPrimaryKey, viewId, tagJson.value, now)
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
        |Authentication via OAuth is required. The user must either have owner privileges for this account, 
        |or must be the user that posted the tag.
        |""".stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      List(NoViewPermission,
           ViewNotFound,
           UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteTagForViewOnTransaction : OBPEndpoint = {
      //delete a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {

        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            bankAccount <- BankAccount(bankId, accountId)?~! BankAccountNotFound
            deleted <- metadata.deleteTag(tagId, cc.user, bankAccount)
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
      transactionImagesJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getImagesForViewOnTransaction : OBPEndpoint = {
      //get images
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonGet req => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            images <- Box(metadata.images) ?~ { s"$NoViewPermission can_see_images. Current ViewId($viewId)" }
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
         |${authenticationRequiredMessage(true) }
         |
         |The image is linked with the user.""",
      postTransactionImageJSON,
      transactionImageJSON,
      List(
        InvalidJsonFormat,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        InvalidUrl,
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction)
    )

    lazy val addImageForViewOnTransaction : OBPEndpoint = {
      //add an image
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user
            imageJson <- tryo{json.extract[PostTransactionImageJSON]} ?~! InvalidJsonFormat
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, Full(u), Some(cc))
            addImageFunc <- Box(metadata.addImage) ?~ { s"$NoViewPermission can_add_image. Current ViewId($viewId)" }
            url <- tryo{new URL(imageJson.URL)} ?~! s"$InvalidUrl Could not parse url string as a valid URL"
            postedImage <- addImageFunc(u.userPrimaryKey, viewId, imageJson.label, now, url.toString)
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
      List(
        BankAccountNotFound,
        NoViewPermission,
        UserNotLoggedIn,
        "You must be able to see images in order to delete them",
        "Image not found for this transaction",
        "Deleting images not permitted for this view",
        "Deleting images not permitted for the current user",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteImageForViewOnTransaction : OBPEndpoint = {
      //delete an image
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: imageId :: Nil JsonDelete _ => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            bankAccount <- BankAccount(bankId, accountId)?~! BankAccountNotFound
            deleted <- Box(metadata.deleteImage(imageId, cc.user, bankAccount))
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
      transactionWhereJSON,
      List(BankAccountNotFound,
           NoViewPermission,
           ViewNotFound,
           UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getWhereTagForViewOnTransaction : OBPEndpoint = {
      //get where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonGet req => {
        cc =>
          for {
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            where <- Box(metadata.whereTag) ?~ { s"$NoViewPermission can_see_where_tag. Current ViewId($viewId)" }
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
      postTransactionWhereJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        ViewNotFound,
        NoViewPermission,
        "Coordinates not possible",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addWhereTagForViewOnTransaction : OBPEndpoint = {
      //add where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankId, accountId))
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            addWhereTag <- Box(metadata.addWhereTag) ?~ { s"$NoViewPermission can_add_where_tag. Current ViewId($viewId)" }
            whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {InvalidJsonFormat}
            correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
            if(addWhereTag(u.userPrimaryKey, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
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
      postTransactionWhereJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        ViewNotFound,
        NoViewPermission,
        "Coordinates not possible",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val updateWhereTagForViewOnTransaction : OBPEndpoint = {
      //update where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankId, accountId))
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            addWhereTag <- Box(metadata.addWhereTag) ?~ { s"$NoViewPermission can_add_where_tag. Current ViewId($viewId)" }
            whereJson <- tryo{(json.extract[PostTransactionWhereJSON])} ?~ {InvalidJsonFormat}
            correctCoordinates <- checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)
            if(addWhereTag(u.userPrimaryKey, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
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
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        UserNotLoggedIn,
        ViewNotFound,
        "there is no tag to delete",
        "Delete not completed",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteWhereTagForViewOnTransaction : OBPEndpoint = {
      //delete where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonDelete _ => {
        cc =>
          for {
            bankAccount <- BankAccount(bankId, accountId)?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankAccount.bankId,bankAccount.accountId))
            metadata <- moderatedTransactionMetadata(bankId, accountId, viewId, transactionId, cc.user, Some(cc))
            deleted <- metadata.deleteWhereTag(viewId, cc.user, bankAccount)
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
      otherAccountJSON,
      List(BankAccountNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransaction, apiTagCounterparty))

    lazy val getOtherAccountForTransaction : OBPEndpoint = {
      //get other account of a transaction
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions":: TransactionId(transactionId) :: "other_account" :: Nil JsonGet req => {
        cc =>
          for {
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            (transaction, callerContext) <- account.moderatedTransaction(transactionId, view, cc.user, Some(cc))
            moderatedOtherBankAccount <- transaction.otherBankAccount
          } yield {
            val otherBankAccountJson = JSONFactory.createOtherBankAccount(moderatedOtherBankAccount)
            successJsonResponse(Extraction.decompose(otherBankAccountJson))
          }

      }
    }

  /*

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
      makePaymentJson,
      transactionIdJson,
      List(
        UserNotLoggedIn,
        "amount not convertible to number",
        "Sorry, payments are not enabled in this API instance.",
        "account not found at bank",
        "user does not have access to owner view",
        "Cannot send payment to account with different currency",
        "Can't send a payment with a value of 0 or less.",
        "Sorry, payments are not enabled in this API instance.",
        UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransactionRequest))

*/

    /*

    lazy val makePayment : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonPost json -> _ => {
        sc
          if (APIUtil.getPropsAsBoolValue("payments_enabled", false)) {
            for {
              u <- cc.user ?~ UserNotLoggedIn
              makeTransJson <- tryo{json.extract[MakePaymentJson]} ?~ {InvalidJsonFormat}
              rawAmt <- tryo {BigDecimal(makeTransJson.amount)} ?~! s"amount ${makeTransJson.amount} not convertible to number"
              toAccountUID = BankIdAccountId(BankId(makeTransJson.bank_id), AccountId(makeTransJson.account_id))
              createdPaymentId <- Connector.connector.vend.makePayment(u, BankIdAccountId(bankId, accountId), toAccountUID, rawAmt, "")
            } yield {
              val successJson = Extraction.decompose(TransactionIdJson(createdPaymentId.value))
              successJsonResponse(successJson)
            }
          } else{
            Failure("Sorry, payments are not enabled in this API instance.")
          }

      }
    }


    */

  }
}

object APIMethods121 {
}
