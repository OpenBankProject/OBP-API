package code.api.v3_0_0

import java.util.regex.Pattern

import code.accountholders.AccountHolders
import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{bankJSON, banksJSON, branchJsonV300, _}
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.JSONFactory
import code.api.v2_0_0.JSONFactory200
import code.api.v3_0_0.JSONFactory300._
import code.bankconnectors._
import code.consumer.Consumers
import code.entitlementrequest.EntitlementRequest
import code.metrics.APIMetrics
import code.model._
import code.scope.Scope
import code.search.elasticsearchWarehouse
import code.users.Users
import code.util.Helper
import code.util.Helper.booleanToBox
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.grum.geocalc.{Coordinate, EarthCalc, Point}
import com.openbankproject.commons.model._
import net.liftweb.common._
import net.liftweb.http.S
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{Extraction, compactRender}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait APIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations3_0_0 = new Object() {

    val implementedInApiVersion = ApiVersion.v3_0_0

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    resourceDocs += ResourceDoc(
      getViewsForBankAccount,
      implementedInApiVersion,
      nameOf(getViewsForBankAccount),
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
      viewsJsonV300,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagNewStyle))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (Full(u), callContext) <-  authorizedAccess(cc)
              (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
              _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView +"userId : " + u.userId + ". account : " + accountId){
                u.hasOwnerViewAccess(BankIdAccountId(account.bankId, account.accountId))
              }
            } yield {
              for {
                views <- Full(Views.views.vend.viewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
              } yield {
                (createViewsJSON(views), HttpCode.`200`(callContext))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    resourceDocs += ResourceDoc(
      createViewForBankAccount,
      implementedInApiVersion,
      nameOf(createViewForBankAccount),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Create View.",
      s"""Create a view on bank account
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
        | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
        |
        | You MUST use a leading _ (underscore) in the view name because other view names are reserved for OBP system views.
        | """,
      SwaggerDefinitionsJSON.createViewJson,
      viewJsonV300,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagNewStyle))

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          val res =
            for {
              (Full(u), callContext) <-  authorizedAccess(cc)
              createViewJson <- Future { tryo{json.extract[CreateViewJson]} } map {
                val msg = s"$InvalidJsonFormat The Json body should be the $CreateViewJson "
                x => unboxFullOrFail(x, callContext, msg)
              }
              //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
              _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat) {
                createViewJson.name.startsWith("_")
              }
              (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            } yield {
              for {
                view <- account createView (u, createViewJson)
              } yield {
                (JSONFactory300.createViewJSON(view), callContext.map(_.copy(httpCode = Some(201))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      getPermissionForUserForBankAccount,
      implementedInApiVersion,
      nameOf(getPermissionForUserForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID",
      "Get Account access for User.",
      s"""Returns the list of the views at BANK_ID for account ACCOUNT_ID that a user identified by PROVIDER_ID at their provider PROVIDER has access to.
         |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER.
         |
        |${authenticationRequiredMessage(true)}
         |
        |The user needs to have access to the owner view.""",
      emptyObjectJson,
      viewsJsonV300,
      List(UserNotLoggedIn,BankNotFound, AccountNotFound,UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagNewStyle))
  
    lazy val getPermissionForUserForBankAccount : OBPEndpoint = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            permission <- Future { account permission(u, provider, providerId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UserNoOwnerView, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (createViewsJSON(permission.views.sortBy(_.viewId.value)), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateViewForBankAccount,
      implementedInApiVersion,
      nameOf(updateViewForBankAccount),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID",
      "Update View.",
      s"""Update an existing view on a bank account
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
        |
        |The json sent is the same as during view creation (above), with one difference: the 'name' field
        |of a view is not editable (it is only set when a view is created)""",
      updateViewJSON,
      viewJsonV300,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagNewStyle)
    )

    lazy val updateViewForBankAccount : OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        cc =>
          val res =
            for {
              (Full(u), callContext) <-  authorizedAccess(cc)
              updateJson <- Future { tryo{json.extract[UpdateViewJSON]} } map {
                val msg = s"$InvalidJsonFormat The Json body should be the $UpdateViewJSON "
                x => unboxFullOrFail(x, callContext, msg)
              }
              //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
              _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat) {
                updateJson.metadata_view.startsWith("_")
              }
              _ <- Views.views.vend.viewFuture(ViewId(viewId.value), BankIdAccountId(bankId, accountId)) map {
                x => fullBoxOrException(
                  x ~> APIFailureNewStyle(s"$ViewNotFound. Check your post json body, metadata_view = ${updateJson.metadata_view}. It should be an existing VIEW_ID, eg: owner", 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              view <- NewStyle.function.view(viewId, BankIdAccountId(bankId, accountId), callContext)
              _ <- Helper.booleanToFuture(failMsg = SystemViewsCanNotBeModified) {
                !view.isSystem
              }
              (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            } yield {
              for {
                updatedView <- account.updateView(u, viewId, updateJson)
              } yield {
                (JSONFactory300.createViewJSON(updatedView), HttpCode.`200`(callContext))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      getPrivateAccountById,
      implementedInApiVersion,
      nameOf(getPrivateAccountById),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (Full)",
      """Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* IBAN
        |* Available views (sorted by short_name)
        |
        |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
        |
        |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
        |This call provides balance and other account information via delegated authentication using OAuth.
        |
        |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
        |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV300,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  apiTagNewStyle :: Nil)
    lazy val getPrivateAccountById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            _ <- NewStyle.function.hasViewAccess(view, u)
            moderatedAccount <- NewStyle.function.moderatedBankAccount(account, view, Full(u), callContext)
          } yield {
            (createCoreBankAccountJSON(moderatedAccount), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPublicAccountById,
      implementedInApiVersion,
      nameOf(getPublicAccountById),
      "GET",
      "/banks/BANK_ID/public/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Public Account by Id",
      s"""
        |Returns information about an account that has a public view.
        |
        |The account is specified by ACCOUNT_ID. The information is moderated by the view specified by VIEW_ID.
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* Routing
        |
        |
        |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
        |This call provides balance and other account information via delegated authentication using OAuth.
        |
        |${authenticationRequiredMessage(false)}
        |
        |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV300,
      List(BankNotFound,AccountNotFound,ViewNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccountPublic :: apiTagAccount ::  apiTagNewStyle :: Nil)

    lazy val getPublicAccountById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "public" :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, Some(cc))
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            moderatedAccount <- NewStyle.function.moderatedBankAccount(account, view, Empty, callContext)
          } yield {
            (createCoreBankAccountJSON(moderatedAccount), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCoreAccountById,
      implementedInApiVersion,
      nameOf(getCoreAccountById),
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      s"""Information returned about the account specified by ACCOUNT_ID:
        |
        |* Number - The human readable account number given by the bank that identifies the account.
        |* Label - A label given by the owner of the account
        |* Owners - Users that own this account
        |* Type - The type of account
        |* Balance - Currency and Value
        |* Account Routings - A list that might include IBAN or national account identifiers
        |* Account Rules - A list that might include Overdraft and other bank specific rules
        |
        |This call returns the owner view and requires access to that view.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV300,
      List(BankAccountNotFound,UnknownError),
      Catalogs(Core, PSD2, notOBWG),
      apiTagAccount ::  apiTagNewStyle :: Nil)
    lazy val getCoreAccountById : OBPEndpoint = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
          (Full(u), callContext) <-  authorizedAccess(cc)
          (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
          // Assume owner view was requested
          view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(account.bankId, account.accountId), callContext)
          _ <- NewStyle.function.hasViewAccess(view, u)
          moderatedAccount <- NewStyle.function.moderatedBankAccount(account, view, Full(u), callContext)
        } yield {
            (createCoreBankAccountJSON(moderatedAccount), HttpCode.`200`(callContext))
        }
      }
    }

    resourceDocs += ResourceDoc(
      corePrivateAccountsAllBanks,
      implementedInApiVersion,
      nameOf(corePrivateAccountsAllBanks),
      "GET",
      "/my/accounts",
      "Get Accounts at all Banks (private)",
      s"""Returns the list of accounts containing private views for the user.
         |Each account lists the views available to the user.
         |
        |${authenticationRequiredMessage(true)}
         |""",
      emptyObjectJson,
      coreAccountsJsonV300,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagPrivateData, apiTagNewStyle))


    apiRelations += ApiRelation(corePrivateAccountsAllBanks, getCoreAccountById, "detail")
    apiRelations += ApiRelation(corePrivateAccountsAllBanks, corePrivateAccountsAllBanks, "self")



    lazy val corePrivateAccountsAllBanks : OBPEndpoint = {
      //get private accounts for all banks
      case "my" :: "accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            (coreAccounts, callContext) <- NewStyle.function.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(coreAccounts), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getFirehoseAccountsAtOneBank,
      implementedInApiVersion,
      nameOf(getFirehoseAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/firehose/accounts/views/VIEW_ID",
      "Get Firehose Accounts at Bank",
      s"""
         |Get Accounts which have a firehose view assigned to them.
         |
         |This endpoint allows bulk access to accounts.
         |
         |Requires the CanUseFirehoseAtAnyBank Role
         |
         |To be shown on the list, each Account must have a firehose View linked to it.
         |
         |A firehose view has is_firehose = true
         |
         |For VIEW_ID try 'owner'
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountsJsonV300,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData, apiTagNewStyle),
      Some(List(canUseFirehoseAtAnyBank))
    )

    lazy val getFirehoseAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" :: "accounts"  :: "views" :: ViewId(viewId):: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg = FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  ) {
               canUseFirehose(u)
            }
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            availableBankIdAccountIdList <- Future {Views.views.vend.getAllFirehoseAccounts(bank.bankId, u) }
            moderatedAccounts = for {
              //Here is a new for-loop to get the moderated accouts for the firehose user, according to the viewId.
              //1 each accountId-> find a proper bankAccount object.
              //2 each bankAccount object find the proper view.
              //3 use view and user to moderate the bankaccount object.
              bankIdAccountId <- availableBankIdAccountIdList
              bankAccount <- Connector.connector.vend.getBankAccount(bankIdAccountId.bankId, bankIdAccountId.accountId) ?~! s"$BankAccountNotFound Current Bank_Id(${bankIdAccountId.bankId}), Account_Id(${bankIdAccountId.accountId}) "
              view <- Views.views.vend.view(viewId, bankIdAccountId)
              moderatedAccount <- bankAccount.moderatedBankAccount(view, Full(u), callContext) //Error handling is in lower method
            } yield {
              moderatedAccount
            }
          } yield {
            (JSONFactory300.createFirehoseCoreBankAccountJSON(moderatedAccounts), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getFirehoseTransactionsForBankAccount,
      implementedInApiVersion,
      nameOf(getFirehoseTransactionsForBankAccount),
      "GET",
      "/banks/BANK_ID/firehose/accounts/ACCOUNT_ID/views/VIEW_ID/transactions",
      "Get Firehose Transactions for Account",
      s"""
         |Get Transactions for an Account that has a firehose View.
         |
         |Allows bulk access to an account's transactions.
         |User must have the CanUseFirehoseAtAnyBank Role
         |
         |To find ACCOUNT_IDs, use the getFirehoseAccountsAtOneBank call.
         |
         |For VIEW_ID try 'owner'
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      transactionsJsonV300,
      List(UserNotLoggedIn, FirehoseViewsNotAllowedOnThisInstance, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData, apiTagNewStyle),
      Some(List(canUseFirehoseAtAnyBank)))

    lazy val getFirehoseTransactionsForBankAccount : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" :: "accounts" ::  AccountId(accountId) :: "views" :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (Full(u), callContext) <-  authorizedAccess(cc)
              _ <- Helper.booleanToFuture(failMsg = FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  ) {
               canUseFirehose(u)
              }
              (bankAccount, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
              view <- NewStyle.function.view(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            } yield {
              for {
              //Note: error handling and messages for getTransactionParams are in the sub method
                params <- createQueriesByHttpParams(callContext.get.requestHeaders)
                (transactions, callContext) <- bankAccount.getModeratedTransactions(Full(u), view, callContext, params: _*)
              } yield {
                (createTransactionsJson(transactions), HttpCode.`200`(callContext))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      getCoreTransactionsForBankAccount,
      implementedInApiVersion,
      nameOf(getCoreTransactionsForBankAccount),
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions",
      "Get Transactions for Account (Core)",
      s"""Returns transactions list (Core info) of the account specified by ACCOUNT_ID.
        |
        |${authenticationRequiredMessage(true)}
        |
        |Possible custom headers for pagination:
        |
        |* sort_direction=ASC/DESC ==> default value: DESC. The sort field is the completed date.
        |* limit=NUMBER ==> default value: 50
        |* offset=NUMBER ==> default value: 0
        |* from_date=DATE => default value: $DateWithMsForFilteringFromDateString
        |* to_date=DATE => default value: $DateWithMsForFilteringEenDateString
        |
        |**Date format parameter**: $DateWithMs($DateWithMsExampleString) ==> time zone is UTC.""",
      emptyObjectJson,
      coreTransactionsJsonV300,
      List(
        FilterSortDirectionError,
        FilterOffersetError,
        FilterLimitError ,
        FilterDateFormatError,
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransaction, apiTagAccount, apiTagNewStyle)
    )

    lazy val getCoreTransactionsForBankAccount : OBPEndpoint = {
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <-  authorizedAccess(cc)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            // Assume owner view was requested
            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            params <- createQueriesByHttpParamsFuture(callContext.get.requestHeaders)map {
              unboxFullOrFail(_, callContext, InvalidFilterParameterFormat)
            }
            (transactionsCore, callContext) <- Future { bankAccount.getModeratedTransactionsCore(user, view, callContext, params: _*)} map {
              unboxFullOrFail(_, callContext, UnknownError)
            }
          } yield {
            (createCoreTransactionsJSON(transactionsCore), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getTransactionsForBankAccount,
      implementedInApiVersion,
      nameOf(getTransactionsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions",
      "Get Transactions for Account (Full)",
      s"""Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
        |
        |${authenticationRequiredMessage(false)}
        |
        |Authentication is required if the view is not public.
        |
        |Possible custom headers for pagination:
        |
        |* sort_direction=ASC/DESC ==> default value: DESC. The sort field is the completed date.
        |* limit=NUMBER ==> default value: 50
        |* offset=NUMBER ==> default value: 0
        |* from_date=DATE => default value: $DateWithMsForFilteringFromDateString
        |* to_date=DATE => default value: $DateWithMsForFilteringEenDateString
        |
        |**Date format parameter**: $DateWithMs($DateWithMsExampleString) ==> time zone is UTC.""",
      emptyObjectJson,
      transactionsJsonV300,
      List(
        FilterSortDirectionError,
        FilterOffersetError,
        FilterLimitError ,
        FilterDateFormatError,
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransaction, apiTagAccount, apiTagNewStyle)
    )

    lazy val getTransactionsForBankAccount: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <-  authorizedAccess(cc)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            params <- createQueriesByHttpParamsFuture(callContext.get.requestHeaders)map {
              unboxFullOrFail(_, callContext, InvalidFilterParameterFormat)
            }
            //Note: error handling and messages for getTransactionParams are in the sub method
            (transactions, callContext) <- bankAccount.getModeratedTransactionsFuture(user, view, callContext, params: _*) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (createTransactionsJson(transactions), HttpCode.`200`(callContext))
          }
      }
    }


    // TODO Put message into doc below if not enabled (but continue to show API Doc)
    resourceDocs += ResourceDoc(
      dataWarehouseSearch,
      implementedInApiVersion,
      nameOf(dataWarehouseSearch),
      "POST",
      "/search/warehouse/INDEX",
      "Data Warehouse Search",
      s"""
        |Search the data warehouse and get row level results.
        |
        |${authenticationRequiredMessage(true)}
        |
        |CanSearchWarehouse entitlement is required. You can request the Role below.
        |
        |Elastic (search) is used in the background. See links below for syntax.
        |
        |Examples of usage:
        |
        |
        |POST /search/warehouse/THE_INDEX_YOU_WANT_TO_USE
        |
        |POST /search/warehouse/INDEX1,INDEX2
        |
        |POST /search/warehouse/ALL
        |
        |{ Any valid elasticsearch query DSL in the body }
        |
        |
        |[Elasticsearch query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
        |
        |[Elastic simple query](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-request-body.html)
        |
        |[Elastic aggregations](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-aggregations.html)
        |
        |
        """,
      ElasticSearchJSON(ElasticSearchQuery(EmptyElasticSearch())),
      emptyObjectJson, //TODO what is output here?
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagSearchWarehouse, apiTagNewStyle),
      Some(List(canSearchWarehouse)))
    val esw = new elasticsearchWarehouse
    lazy val dataWarehouseSearch: OBPEndpoint = {
      case "search" :: "warehouse" :: index :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canSearchWarehouse, callContext)
            _ <- Helper.booleanToFuture(failMsg = ElasticSearchDisabled) {
              esw.isEnabled()
            }
            indexPart <- Future { esw.getElasticSearchUri(index) } map {
              x => unboxFullOrFail(x, callContext, ElasticSearchIndexNotFound)
            }
            bodyPart <- Future { tryo(compactRender(json)) } map {
              x => unboxFullOrFail(x, callContext, ElasticSearchEmptyQueryBody)
            }
            result: esw.APIResponse <- esw.searchProxyAsyncV300(u.userId, indexPart, bodyPart)
          } yield {
            (esw.parseResponse(result), HttpCode.`201`(callContext))
          }
      }
    }
  
    case class Query(query: String)
    
    resourceDocs += ResourceDoc(
      dataWarehouseStatistics,
      implementedInApiVersion,
      nameOf(dataWarehouseStatistics),
      "POST",
      "/search/warehouse/statistics/INDEX/FIELD",
      "Data Warehouse Statistics",
      s"""
         |Search the data warehouse and get statistical aggregations over a warehouse field
         |
         |Does a stats aggregation over some numeric field:
         |
         |https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-stats-aggregation.html
         |
         |${authenticationRequiredMessage(true)}
         |
         |CanSearchWarehouseStats Role is required. You can request this below.
         |
         |Elastic (search) is used in the background. See links below for syntax.
         |
         |Examples of usage:
         |
         |POST /search/warehouse/statistics/INDEX/FIELD
         |
         |POST /search/warehouse/statistics/ALL/FIELD
         |
         |{ Any valid elasticsearch query DSL in the body }
         |
         |
         |[Elasticsearch query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
         |
         |[Elastic simple query](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-request-body.html)
         |
         |[Elastic aggregations](https://www.elastic.co/guide/en/elasticsearch/reference/6.2/search-aggregations.html)
         |
         |
        """,
      ElasticSearchJSON(ElasticSearchQuery(EmptyElasticSearch())),
      emptyObjectJson, //TODO what is output here?
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagSearchWarehouse, apiTagNewStyle),
      Some(List(canSearchWarehouseStatistics))
    )
    lazy val dataWarehouseStatistics: OBPEndpoint = {
      case "search" :: "warehouse" :: "statistics" :: index :: field :: Nil JsonPost json -> _ => {
        cc =>
          //if (field == "/") throw new RuntimeException("No aggregation field supplied") with NoStackTrace
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canSearchWarehouseStatistics, callContext)
            _ <- Helper.booleanToFuture(failMsg = ElasticSearchDisabled) {
              esw.isEnabled()
            }
            indexPart <- Future { esw.getElasticSearchUri(index) } map {
              x => unboxFullOrFail(x, callContext, ElasticSearchIndexNotFound)
            }
            bodyPart <- Future { tryo(compactRender(json)) } map {
              x => unboxFullOrFail(x, callContext, ElasticSearchEmptyQueryBody)
            }
            result <- esw.searchProxyStatsAsyncV300(u.userId, indexPart, bodyPart, field)
          } yield {
            (esw.parseResponse(result), HttpCode.`201`(callContext))
          }
      }
    }
    

    resourceDocs += ResourceDoc(
      getUser,
      implementedInApiVersion,
      nameOf(getUser),
      "GET",
      "/users/email/EMAIL/terminator",
      "Get Users by Email Address",
      s"""Get users by email address
        |
        |${authenticationRequiredMessage(true)}
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJsonV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))


    lazy val getUser: OBPEndpoint = {
      case "users" :: "email" :: email :: "terminator" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyUser, callContext)
            users <- Users.users.vend.getUserByEmailFuture(email)
          } yield {
            (JSONFactory300.createUserJSONs (users), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUserByUserId,
      implementedInApiVersion,
      nameOf(getUserByUserId),
      "GET",
      "/users/user_id/USER_ID",
      "Get User by USER_ID",
      s"""Get user by USER_ID
        |
        |${authenticationRequiredMessage(true)}
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJsonV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundById, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))


    lazy val getUserByUserId: OBPEndpoint = {
      case "users" :: "user_id" :: userId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyUser, callContext)
            user <- Users.users.vend.getUserByUserIdFuture(userId) map {
              x => unboxFullOrFail(x, callContext, UserNotFoundByUsername)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, callContext)
          } yield {
            (JSONFactory300.createUserJSON (user, entitlements), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUserByUsername,
      implementedInApiVersion,
      nameOf(getUserByUsername),
      "GET",
      "/users/username/USERNAME",
      "Get User by USERNAME",
      s"""Get user by USERNAME
        |
        |${authenticationRequiredMessage(true)}
        |
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJsonV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByUsername, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))


    lazy val getUserByUsername: OBPEndpoint = {
      case "users" :: "username" :: username :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyUser, callContext)
            user <- Users.users.vend.getUserByUserNameFuture(username) map {
              x => unboxFullOrFail(x, callContext, UserNotFoundByUsername)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, callContext)
          } yield {
            (JSONFactory300.createUserJSON (user, entitlements), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAdapter,
      implementedInApiVersion,
      nameOf(getAdapter),
      "GET",
      "/banks/BANK_ID/adapter",
      "Get Adapter Info for a bank",
      s"""Get basic information about the Adapter listening on behalf of this bank.
        |
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      adapterInfoJsonV300,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagApi, apiTagNewStyle))


    lazy val getAdapter: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "adapter" :: Nil JsonGet _ => {
          cc =>
            for {
              (_, callContext) <- anonymousAccess(cc)
              (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
              (ai, callContext) <- NewStyle.function.getAdapterInfo(callContext)
            } yield {
              (createAdapterInfoJson(ai), callContext)
            }
      }
    }


    // Create Branch
    val createBranchEntitlementsRequiredForSpecificBank = CanCreateBranch :: Nil
    val createBranchEntitlementsRequiredForAnyBank = CanCreateBranchAtAnyBank :: Nil
    val createBranchEntitlementsRequiredText = UserHasMissingRoles + createBranchEntitlementsRequiredForSpecificBank.mkString(" and ") + " entitlements are required OR " + createBranchEntitlementsRequiredForAnyBank.mkString(" and ")


    // TODO Put the RequiredEntitlements and AlternativeRequiredEntitlements in the Resource Doc and use that in the Partial Function?

    resourceDocs += ResourceDoc(
      createBranch,
      implementedInApiVersion,
      nameOf(createBranch),
      "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create Branch for the Bank.
          |
         |${authenticationRequiredMessage(true) }
          |
          |""",
      branchJsonV300,
      branchJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InsufficientAuthorisationToCreateBranch,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBranch),
      Some(List(canCreateBranch, canCreateBranchAtAnyBank))
    )

    lazy val createBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            _ <- booleanToBox(
              hasEntitlement(bank.bankId.value, u.userId, canCreateBranch) == true
              ||
              hasEntitlement("", u.userId, canCreateBranchAtAnyBank) == true
              , createBranchEntitlementsRequiredText
            )
            branchJsonV300 <- tryo {json.extract[BranchJsonV300]} ?~! {ErrorMessages.InvalidJsonFormat + " BranchJsonV300"}
            _ <- booleanToBox(branchJsonV300.bank_id == bank.bankId.value, "BANK_ID has to be the same in the URL and Body")
            branch <- transformToBranchFromV300(branchJsonV300) ?~! {ErrorMessages.CouldNotTransformJsonToInternalModel + " Branch"}
            success: BranchT <- Connector.connector.vend.createOrUpdateBranch(branch) ?~! {ErrorMessages.CountNotSaveOrUpdateResource + " Branch"}
          } yield {
            val json = JSONFactory300.createBranchJsonV300(success)
            createdJsonResponse(Extraction.decompose(json), 201)
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateBranch,
      implementedInApiVersion,
      nameOf(updateBranch),
      "PUT",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Update Branch",
      s"""Update an existing branch for a bank account (Authenticated access).
          |
         |${authenticationRequiredMessage(true) }
          |
          |""",
      postBranchJsonV300,
      branchJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InsufficientAuthorisationToCreateBranch,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBranch),
      Some(List(canCreateBranch, canCreateBranchAtAnyBank))
    )

    lazy val updateBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId)::  Nil JsonPut json -> _  => {
        cc =>
          for {
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            _ <- booleanToBox(
              hasEntitlement(bank.bankId.value, u.userId, canCreateBranch) == true
              ||
              hasEntitlement("", u.userId, canCreateBranchAtAnyBank) == true
              , createBranchEntitlementsRequiredText
            )
            postBranchJsonV300 <- tryo {json.extract[PostBranchJsonV300]} ?~! {ErrorMessages.InvalidJsonFormat + PostBranchJsonV300.toString()}
            branchJsonV300 = BranchJsonV300(
              id = branchId.value, 
              postBranchJsonV300.bank_id,
              postBranchJsonV300.name,
              postBranchJsonV300.address,
              postBranchJsonV300.location,
              postBranchJsonV300.meta,
              postBranchJsonV300.lobby,
              postBranchJsonV300.drive_up,
              postBranchJsonV300.branch_routing,
              postBranchJsonV300.is_accessible,
              postBranchJsonV300.accessibleFeatures,
              postBranchJsonV300.branch_type,
              postBranchJsonV300.more_info,
              postBranchJsonV300.phone_number)
            _ <- booleanToBox(branchJsonV300.bank_id == bank.bankId.value, "BANK_ID has to be the same in the URL and Body")
            branch <- transformToBranchFromV300(branchJsonV300) ?~! {ErrorMessages.CouldNotTransformJsonToInternalModel + " Branch"}
            success: BranchT <- Connector.connector.vend.createOrUpdateBranch(branch) ?~! {ErrorMessages.CountNotSaveOrUpdateResource + " Branch"}
          } yield {
            val json = JSONFactory300.createBranchJsonV300(success)
            createdJsonResponse(Extraction.decompose(json), 201)
          }
      }
    }
    
    
    val createAtmEntitlementsRequiredForSpecificBank = canCreateAtm ::  Nil
    val createAtmEntitlementsRequiredForAnyBank = canCreateAtmAtAnyBank ::  Nil

    val createAtmEntitlementsRequiredText = UserHasMissingRoles + createAtmEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createAtmEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createAtm,
      implementedInApiVersion,
      nameOf(createAtm),
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM for the Bank.
          |
         |${authenticationRequiredMessage(true) }
          |
          |""",
      atmJsonV300,
      atmJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagATM),
      Some(List(canCreateAtm,canCreateAtmAtAnyBank))
    )



    lazy val createAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" ::  Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            _ <- booleanToBox(hasAllEntitlements(bank.bankId.value, u.userId, createAtmEntitlementsRequiredForSpecificBank) == true
              ||
              hasAllEntitlements("", u.userId, createAtmEntitlementsRequiredForAnyBank),
              createAtmEntitlementsRequiredText)
            atmJson <- tryo {json.extract[AtmJsonV300]} ?~! ErrorMessages.InvalidJsonFormat
            atm <- transformToAtmFromV300(atmJson) ?~! {ErrorMessages.CouldNotTransformJsonToInternalModel + " Atm"}
            _ <- booleanToBox(atmJson.bank_id == bank.bankId.value, "BANK_ID has to be the same in the URL and Body")
            success <- Connector.connector.vend.createOrUpdateAtm(atm)
          } yield {
            val json = JSONFactory300.createAtmJsonV300(success)
            createdJsonResponse(Extraction.decompose(json), 201)
          }
      }
    }



    val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    resourceDocs += ResourceDoc(
      getBranch,
      implementedInApiVersion,
      nameOf(getBranch),
      "GET",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Get Branch",
      s"""Returns information about a single Branch specified by BANK_ID and BRANCH_ID including:
         |
          |* Name
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under.
         |
        |${authenticationRequiredMessage(!getBranchesIsPublic)}""",
      emptyObjectJson,
      branchJsonV300,
      List(
        UserNotLoggedIn,
        "License may not be set. meta.license.id and eta.license.name can not be empty",
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBranch, apiTagBank, apiTagNewStyle)
    )
    lazy val getBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId) :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <-
              getBranchesIsPublic match {
                case false => authorizedAccess(cc)
                case true => anonymousAccess(cc)
              }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (branch, callContext) <- NewStyle.function.getBranch(bankId, branchId, callContext)
          } yield {
            (JSONFactory300.createBranchJsonV300(branch), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getBranches,
      implementedInApiVersion,
      nameOf(getBranches),
      "GET",
      "/banks/BANK_ID/branches",
      "Get Branches for a Bank",
      s"""Returns information about branches for a single bank specified by BANK_ID including:
         |
        |* Name
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |* Structured opening hours
         |* Accessible flag
         |* Branch Type
         |* More Info
         |
         |Pagination:
         |
         |By default, 50 records are returned.
         |
         |You can use the url query parameters *limit* and *offset* for pagination
         |You can also use the follow url query parameters:
         |
         |  - city - string, find Branches those in this city, optional
         |
         |
         |  - withinMetersOf - number, find Branches within given meters distance, optional
         |  - nearLatitude - number, a position of latitude value, cooperate with withMetersOf do query filter, optional
         |  - nearLongitude - number, a position of longitude value, cooperate with withMetersOf do query filter, optional
         |
         |note: withinMetersOf, nearLatitude and nearLongitude either all empty or all have value.
         |
        |${authenticationRequiredMessage(!getBranchesIsPublic)}""",
      emptyObjectJson,
      branchesJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No branches available. License may not be set.",
        UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBranch, apiTagBank, apiTagNewStyle)
    )

    private[this] val branchCityPredicate = (city: Box[String], branchCity: String) => city.isEmpty || city.openOrThrowException("city should be have value!") == branchCity

    private[this] val distancePredicate = (withinMetersOf: Box[String], nearLatitude: Box[String], nearLongitude: Box[String], latitude: Double, longitude: Double) => {

      if(withinMetersOf.isEmpty && nearLatitude.isEmpty && nearLongitude.isEmpty) {
        true
      } else {
        // from point
        var lat = Coordinate.fromDegrees(nearLatitude.map(_.toDouble).openOrThrowException("latitude value should be a number!"))
        var lng = Coordinate.fromDegrees(nearLongitude.map(_.toDouble).openOrThrowException("latitude value should be a number!"))
        val fromPoint = Point.at(lat, lng)

        // current branch location point
        lat = Coordinate.fromDegrees(latitude)
        lng = Coordinate.fromDegrees(longitude)
        val branchLocation = Point.at(lat, lng)

        val distance = EarthCalc.harvesineDistance(branchLocation, fromPoint) //in meters
        withinMetersOf.map(_.toDouble).openOrThrowException("withinMetersOf value should be a number!") >= distance
      }
    }
    // regex to check string is a float
    private[this] val reg = Pattern.compile("^[-+]?(\\d+\\.?\\d*$|\\d*\\.?\\d+$)")

    lazy val getBranches : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: Nil JsonGet _ => {
        cc => {
          val limit = S.param("limit")
          val offset = S.param("offset")
          val city = S.param("city")
          val withinMetersOf = S.param("withinMetersOf")
          val nearLatitude = S.param("nearLatitude")
          val nearLongitude = S.param("nearLongitude")
          for {
            (_, callContext) <-
              getBranchesIsPublic match {
                case false => authorizedAccess(cc)
                case true => anonymousAccess(cc)
              }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidNumber } limit:${limit.getOrElse("")}") {
              limit match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c) == true)
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = maximumLimitExceeded) {
              limit match {
                case Full(i) if i.toInt > 10000 => false
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidNumber } offset:${offset.getOrElse("")}") {
              offset match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c) == true)
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = s"${MissingQueryParams} withinMetersOf, nearLatitude and nearLongitude must be either all empty or all float value, but currently their value are: withinMetersOf=${withinMetersOf.openOr("")}, nearLatitude=${nearLatitude.openOr("")} and nearLongitude=${nearLongitude.openOr("")}") {
              (withinMetersOf, nearLatitude, nearLongitude) match {
                case (Full(i), Full(j), Full(k)) => reg.matcher(i).matches() && reg.matcher(j).matches() && reg.matcher(k).matches()
                case (Empty, Empty, Empty) => true
                case _ => false
              }
            }
            (_, callContext)<- NewStyle.function.getBank(bankId, callContext)
            (branches, callContext) <- Connector.connector.vend.getBranchesFuture(bankId, callContext) map {
              case Full((List(), _)) | Empty =>
                fullBoxOrException(Empty ?~! BranchesNotFound)
              case Full((list, callContext)) =>
                val branchesWithLicense = for { branch <- list if branch.meta.license.name.size > 3 } yield branch
                if (branchesWithLicense.size == 0) fullBoxOrException(Empty ?~! branchesNotFoundLicense)
                else Full(branchesWithLicense)
              case Failure(msg, t, c) => Failure(msg, t, c)
              case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
            } map { unboxFull(_) } map {
              branches =>
                // Before we slice we need to sort in order to keep consistent results
                (branches.sortWith(_.branchId.value < _.branchId.value)
                  .filter(it => it.isDeleted != Some(true))
                  .filter(it => this.branchCityPredicate(city, it.address.city))
                  .filter(it => this.distancePredicate(withinMetersOf, nearLatitude, nearLongitude, it.location.latitude, it.location.longitude))
                  // Slice the result in next way: from=offset and until=offset + limit
                  .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
                  , callContext)
            }
          } yield {
            (JSONFactory300.createBranchesJson(branches), HttpCode.`200`(callContext))
          }
        }
      }
    }

    val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    resourceDocs += ResourceDoc(
      getAtm,
      implementedInApiVersion,
      nameOf(getAtm),
      "GET",
      "/banks/BANK_ID/atms/ATM_ID",
      "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID including:
         |
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |
         |
         |
          |${authenticationRequiredMessage(!getAtmsIsPublic)}""",
      emptyObjectJson,
      atmJsonV300,
      List(UserNotLoggedIn, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagATM, apiTagNewStyle)
    )
    lazy val getAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet req => {
        cc =>
          for {
            (_, callContext) <-
              getAtmsIsPublic match {
                case false => authorizedAccess(cc)
                case true => anonymousAccess(cc)
              }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, callContext)
          } yield {
            (JSONFactory300.createAtmJsonV300(atm), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getAtms,
      implementedInApiVersion,
      nameOf(getAtms),
      "GET",
      "/banks/BANK_ID/atms",
      "Get Bank ATMS",
      s"""Returns information about ATMs for a single bank specified by BANK_ID including:
         |
         |* Address
         |* Geo Location
         |* License the data under this endpoint is released under
         |
         |Pagination:
          |
          |By default, 100 records are returned.
          |
          |You can use the url query parameters *limit* and *offset* for pagination
         |
         |${authenticationRequiredMessage(!getAtmsIsPublic)}""",
      emptyObjectJson,
      atmJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No ATMs available. License may not be set.",
        UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagATM, apiTagNewStyle)
    )
    lazy val getAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet req => {
        cc => {
          val limit = S.param("limit")
          val offset = S.param("offset")
          for {
            (_, callContext) <-
              getAtmsIsPublic match {
                case false => authorizedAccess(cc)
                case true => anonymousAccess(cc)
              }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidNumber } limit:${limit.getOrElse("")}") {
              limit match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c) == true)
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = maximumLimitExceeded) {
              limit match {
                case Full(i) if i.toInt > 10000 => false
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidNumber } offset:${offset.getOrElse("")}") {
              offset match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c) == true)
                case _ => true
              }
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (atms, callContext) <- Connector.connector.vend.getAtmsFuture(bankId, callContext) map {
              case Full((List(),_)) | Empty =>
                fullBoxOrException(Empty ?~! atmsNotFound)
              case Full((list, _)) =>
                val branchesWithLicense = for { branch <- list if branch.meta.license.name.size > 3 } yield branch
                if (branchesWithLicense.size == 0) fullBoxOrException(Empty ?~! atmsNotFoundLicense)
                else Full(branchesWithLicense)
              case Failure(msg, t, c) => Failure(msg, t, c)
              case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
            } map { unboxFull(_) } map {
              branch =>
                // Before we slice we need to sort in order to keep consistent results
                (branch.sortWith(_.atmId.value < _.atmId.value)
                // Slice the result in next way: from=offset and until=offset + limit
                .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
                ,callContext)
            }
          } yield {
            (JSONFactory300.createAtmsJsonV300(atms), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getUsers,
      implementedInApiVersion,
      nameOf(getUsers),
      "GET",
      "/users",
      "Get all Users",
      s"""Get all users
        |
        |${authenticationRequiredMessage(true)}
        |
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJsonV200,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser)))

    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyUser,callContext)
            
            httpParams <- NewStyle.function.createHttpParams(cc.url)
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              x => unboxFullOrFail(x, callContext, InvalidFilterParameterFormat)
            }
            
            users <- Users.users.vend.getAllUsersF(obpQueryParams)
          } yield {
            (JSONFactory300.createUserJSONs (users), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCustomersForUser,
      implementedInApiVersion,
      nameOf(getCustomersForUser),
      "GET",
      "/users/current/customers",
      "Get Customers for Current User",
      s"""Gets all Customers that are linked to a User.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      emptyObjectJson,
      customerJsonV300,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagUser, apiTagNewStyle))



    // This can be considered a reference new style endpoint.
    // This is a partial function. The lazy value should have a meaningful name.
    lazy val getCustomersForUser : OBPEndpoint = {
      // This defines the URL path and method (GET) for which this partial function will accept the call.
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        // We have the Call Context (cc) object (provided through the OBPEndpoint type)
        // The Call Context contains the authorisation headers etc.
        cc => {
          for {
            // Extract the user from the headers and get an updated callContext
            (Full(u), callContext) <- authorizedAccess(cc)
            // Now here is the business logic.
            // Get The customers related to a user. Process the resonse which might be an Exception
            (customers,callContext) <- Connector.connector.vend.getCustomersByUserIdFuture(u.userId, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            // Create the JSON to return. We also return the callContext
            (JSONFactory300.createCustomersJson(customers), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCurrentUser,
      implementedInApiVersion,
      nameOf(getCurrentUser), // TODO can we get this string from the val two lines above?
      "GET",
      "/users/current",
      "Get User (Current)",
      s"""Get the logged in user
        |
        |${authenticationRequiredMessage(true)}
      """.stripMargin,
      emptyObjectJson,
      userJsonV200,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle))

    lazy val getCurrentUser: OBPEndpoint = {
      case "users" :: "current" :: Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
          } yield {
            val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(u).toOption
            (JSONFactory300.createUserInfoJSON (u, entitlements, permissions), HttpCode.`200`(callContext))
          }
        }
      }
    }
  
    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      implementedInApiVersion,
      nameOf(privateAccountsAtOneBank),
      "GET",
      "/banks/BANK_ID/accounts/private",
      "Get Accounts at Bank (Minimal).",
      s"""Returns the minimal list of private accounts at BANK_ID that the user has access to.
         |For each account, the API returns the ID, routing addresses and the views available to the current user.
         |
         |If you want to see more information on the Views, use the Account Detail call.
         |
         |
         |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      coreAccountsJsonV300,
      List(UserNotLoggedIn, BankNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagNewStyle)
    )
  
    lazy val privateAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            ((accounts, callContext)) <- Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(accounts), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPrivateAccountIdsbyBankId,
      implementedInApiVersion,
      nameOf(getPrivateAccountIdsbyBankId),
      "GET",
      "/banks/BANK_ID/accounts/account_ids/private",
      "Get Accounts at Bank (IDs only).",
      s"""Returns only the list of accounts ids at BANK_ID that the user has access to.
         |
         |Each account must have at least one private View.
         |
         |For each account the API returns its account ID.
         |
         |If you want to see more information on the Views, use the Account Detail call.
         |
         |
         |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      accountsIdsJsonV300,
      List(UserNotLoggedIn, BankNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagNewStyle)
    )
  
    lazy val getPrivateAccountIdsbyBankId : OBPEndpoint = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "account_ids" :: "private"::Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
             (_, callContext)<- NewStyle.function.getBank(bankId, callContext)
            bankAccountIds <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
          } yield {
            (JSONFactory300.createAccountsIdsByBankIdAccountIds(bankAccountIds), HttpCode.`200`(callContext))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getOtherAccountsForBankAccount,
      implementedInApiVersion,
      nameOf(getOtherAccountsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts",
      "Get Other Accounts of one Account.",
      s"""Returns data about all the other accounts that have shared at least one transaction with the ACCOUNT_ID at BANK_ID.
         |${authenticationRequiredMessage(false)}
         |
         |Authentication is required if the view VIEW_ID is not public.""",
      emptyObjectJson,
      otherAccountsJsonV300,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        ConnectorEmptyResponse,
        UnknownError
      ),
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount, apiTagNewStyle))
  
    lazy val getOtherAccountsForBankAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet req => {
        cc =>
          for {
            (u, callContext) <- authorizedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            otherBankAccounts <- NewStyle.function.moderatedOtherBankAccounts(account, view, u, callContext)
          } yield {
            val otherBankAccountsJson = createOtherBankAccountsJson(otherBankAccounts)
            (otherBankAccountsJson, HttpCode.`200`(callContext))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getOtherAccountByIdForBankAccount,
      implementedInApiVersion,
      nameOf(getOtherAccountByIdForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
      "Get Other Account by Id.",
      s"""Returns data about the Other Account that has shared at least one transaction with ACCOUNT_ID at BANK_ID.
         |${authenticationRequiredMessage(false)}
         |
         |Authentication is required if the view is not public.""",
      emptyObjectJson,
      otherAccountJsonV300,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        ConnectorEmptyResponse,
        UnknownError),
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount, apiTagNewStyle))
  
    lazy val getOtherAccountByIdForBankAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet _ => {
        cc =>
          for {
            (u, callContext) <- authorizedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            otherBankAccount <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, u, callContext)
          } yield {
            val otherBankAccountJson = createOtherBankAccount(otherBankAccount)
            (otherBankAccountJson, HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      addEntitlementRequest,
      implementedInApiVersion,
      nameOf(addEntitlementRequest),
      "POST",
      "/entitlement-requests",
      "Add Entitlement Request for current User.",
      s"""Create Entitlement Request.
        |
        |Any logged in User can use this endpoint to request an Entitlement
        |
        |Entitlements are used to grant System or Bank level roles to Users. (For Account level privileges, see Views)
        |
        |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
        |
        |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
        |
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        """.stripMargin,
      code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createEntitlementJSON,
      entitlementRequestJSON,
      List(
        UserNotLoggedIn,
        UserNotFoundById,
        InvalidJsonFormat,
        IncorrectRoleName,
        EntitlementIsBankRole,
        EntitlementIsSystemRole,
        EntitlementRequestAlreadyExists,
        EntitlementRequestCannotBeAdded,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle))

    lazy val addEntitlementRequest : OBPEndpoint = {
      case "entitlement-requests" :: Nil JsonPost json -> _ => {
        cc =>
          for {
              (Full(u), callContext) <- authorizedAccess(cc)
              postedData <- Future { tryo{json.extract[CreateEntitlementRequestJSON]} } map {
                val msg = s"$InvalidJsonFormat The Json body should be the $CreateEntitlementRequestJSON "
                x => unboxFullOrFail(x, callContext, msg)
              }
              _ <- Future { if (postedData.bank_id == "") Full() else NewStyle.function.getBank(bankId, callContext)}
              
              _ <- Helper.booleanToFuture(failMsg = IncorrectRoleName + postedData.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", ")) {
                availableRoles.exists(_ == postedData.role_name)
              }
              _ <- Helper.booleanToFuture(failMsg = if (ApiRole.valueOf(postedData.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole) {
                ApiRole.valueOf(postedData.role_name).requiresBankId == postedData.bank_id.nonEmpty
              }
              _ <- Helper.booleanToFuture(failMsg = EntitlementRequestAlreadyExists) {
                EntitlementRequest.entitlementRequest.vend.getEntitlementRequest(postedData.bank_id, u.userId, postedData.role_name).isEmpty
              }
              addedEntitlementRequest <- EntitlementRequest.entitlementRequest.vend.addEntitlementRequestFuture(postedData.bank_id, u.userId, postedData.role_name) map {
                x => unboxFullOrFail(x, callContext, EntitlementRequestCannotBeAdded)
              }
            } yield {
              (JSONFactory300.createEntitlementRequestJSON(addedEntitlementRequest), HttpCode.`201`(callContext))
            }
      }
    }


    resourceDocs += ResourceDoc(
      getAllEntitlementRequests,
      implementedInApiVersion,
      nameOf(getAllEntitlementRequests),
      "GET",
      "/entitlement-requests",
      "Get all Entitlement Requests",
      s"""
        |Get all Entitlement Requests
        |
        |${authenticationRequiredMessage(true)}
      """.stripMargin,
      emptyObjectJson,
      entitlementRequestsJSON,
      List(
        UserNotLoggedIn,
        UserNotSuperAdmin,
        ConnectorEmptyResponse,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canGetEntitlementRequestsAtAnyBank)))

    lazy val getAllEntitlementRequests : OBPEndpoint = {
      case "entitlement-requests" :: Nil JsonGet _ => {
        cc =>
          val allowedEntitlements = canGetEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + allowedEntitlementsTxt)("", u.userId, allowedEntitlements)
            entitlementRequests <- NewStyle.function.getEntitlementRequestsFuture(callContext)
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(entitlementRequests), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getEntitlementRequests,
      implementedInApiVersion,
      nameOf(getEntitlementRequests),
      "GET",
      "/users/USER_ID/entitlement-requests",
      "Get Entitlement Requests for a User.",
      s"""Get Entitlement Requests for a User.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        """.stripMargin,
      emptyObjectJson,
      entitlementRequestsJSON,
      List(
        UserNotLoggedIn,
        UserNotSuperAdmin,
        ConnectorEmptyResponse,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canGetEntitlementRequestsAtAnyBank)))

    lazy val getEntitlementRequests : OBPEndpoint = {
      case "users" :: userId :: "entitlement-requests" :: Nil JsonGet _ => {
        cc =>
          val allowedEntitlements = canGetEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            (Full(authorizedUser), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + allowedEntitlementsTxt)("", authorizedUser.userId, allowedEntitlements)
            entitlementRequests <- NewStyle.function.getEntitlementRequestsFuture(userId, callContext)
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(entitlementRequests), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getEntitlementRequestsForCurrentUser,
      implementedInApiVersion,
      nameOf(getEntitlementRequestsForCurrentUser),
      "GET",
      "/my/entitlement-requests",
      "Get Entitlement Requests for the current User.",
      s"""Get Entitlement Requests for the current User.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        """.stripMargin,
      emptyObjectJson,
      entitlementRequestsJSON,
      List(
        UserNotLoggedIn,
        UserNotSuperAdmin,
        ConnectorEmptyResponse,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      None)

    lazy val getEntitlementRequestsForCurrentUser : OBPEndpoint = {
      case "my" :: "entitlement-requests" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            entitlementRequests <- NewStyle.function.getEntitlementRequestsFuture(u.userId, callContext)
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(entitlementRequests), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      deleteEntitlementRequest,
      implementedInApiVersion,
      nameOf(deleteEntitlementRequest),
      "DELETE",
      "/entitlement-requests/ENTITLEMENT_REQUEST_ID",
      "Delete Entitlement Request",
      s"""Delete the Entitlement Request specified by ENTITLEMENT_REQUEST_ID for a user specified by USER_ID
        |
        |
        |${authenticationRequiredMessage(true)}
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserNotSuperAdmin,
        ConnectorEmptyResponse,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      Some(List(canDeleteEntitlementRequestsAtAnyBank)))

    lazy val deleteEntitlementRequest : OBPEndpoint = {
      case "entitlement-requests" :: entitlementRequestId :: Nil JsonDelete _ => {
        cc =>
          val allowedEntitlements = canDeleteEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = UserHasMissingRoles + allowedEntitlements.mkString(" or ")
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = allowedEntitlementsTxt)("", u.userId, allowedEntitlements)
            deleteEntitlementRequest <- EntitlementRequest.entitlementRequest.vend.deleteEntitlementRequestFuture(entitlementRequestId) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (Full(deleteEntitlementRequest), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getEntitlementsForCurrentUser,
      implementedInApiVersion,
      nameOf(getEntitlementsForCurrentUser),
      "GET",
      "/my/entitlements",
      "Get Entitlements for the current User.",
      s"""Get Entitlements for the current User.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        """.stripMargin,
      emptyObjectJson,
      entitlementJSONs,
      List(
        UserNotLoggedIn,
        UserNotSuperAdmin,
        ConnectorEmptyResponse,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagUser, apiTagNewStyle),
      None)

    lazy val getEntitlementsForCurrentUser : OBPEndpoint = {
      case "my" :: "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
          } yield {
            (JSONFactory200.createEntitlementJSONs(entitlements), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getApiGlossary,
      implementedInApiVersion,
      "glossary",
      "GET",
      "/api/glossary",
      "Get API Glossary",
      """Returns the glossary of the API
        |""",
      emptyObjectJson,
      glossaryItemsJsonV300,
      List(UnknownError),
      Catalogs(Core, PSD2, OBWG),
      apiTagDocumentation :: Nil)

    lazy val getApiGlossary : OBPEndpoint = {
      case "api" :: "glossary" :: Nil JsonGet req => _ => {
        val json = JSONFactory300.createGlossaryItemsJsonV300(getGlossaryItems)
        Full(successJsonResponse(Extraction.decompose(json)))
      }
    }
  
    resourceDocs += ResourceDoc(
      getAccountsHeld,
      implementedInApiVersion,
      nameOf(getAccountsHeld),
      "GET",
      "/banks/BANK_ID/accounts-held",
      "Get Accounts Held",
      s"""Get Accounts held by the current User if even the User has not been assigned the owner View yet.
        |
        |Can be used to onboard the account to the API - since all other account and transaction endpoints require views to be assigned.
        |
        |
        |${authenticationRequiredMessage(true)}
      """,
      emptyObjectJson,
      coreAccountsHeldJsonV300,
      List(UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagAccount, apiTagView, apiTagNewStyle)
    )
  
    lazy val getAccountsHeld : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts-held" ::  Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            availableAccounts <- Future{ AccountHolders.accountHolders.vend.getAccountsHeld(bankId, u)}
            accounts <- Connector.connector.vend.getBankAccountsHeldFuture(availableAccounts.toList, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(accounts), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAggregateMetrics,
      implementedInApiVersion,
      nameOf(getAggregateMetrics),
      "GET",
      "/management/aggregate-metrics",
      "Get Aggregate Metrics",
      s"""Returns aggregate metrics on api usage eg. total count, response time (in ms), etc.
        |
        |Should be able to filter on the following fields
        |
        |eg: /management/aggregate-metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5
        |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
        |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
        |&verb=GET&anon=false&app_name=MapperPostman
        |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
        |
        |1 from_date (defaults to the day before the current date): eg:from_date=$DateWithMsExampleString
        |
        |2 to_date (defaults to the current date) eg:to_date=$DateWithMsExampleString
        |
        |3 consumer_id  (if null ignore)
        |
        |4 user_id (if null ignore)
        |
        |5 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
        |
        |6 url (if null ignore), note: can not contain '&'.
        |
        |7 app_name (if null ignore)
        |
        |8 implemented_by_partial_function (if null ignore),
        |
        |9 implemented_in_version (if null ignore)
        |
        |10 verb (if null ignore)
        |
        |11 correlation_id (if null ignore)
        |
        |12 duration (if null ignore) non digit chars will be silently omitted
        |
        |13 exclude_app_names (if null ignore).eg: &exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
        |
        |14 exclude_url_patterns (if null ignore).you can design you own SQL NOT LIKE pattern. eg: &exclude_url_patterns=%management/metrics%,%management/aggregate-metrics%
        |
        |15 exclude_implemented_by_partial_functions (if null ignore).eg: &exclude_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
        |
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      aggregateMetricsJSONV300,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetric, apiTagAggregateMetrics, apiTagNewStyle),
      Some(List(canReadAggregateMetrics)))

      lazy val getAggregateMetrics : OBPEndpoint = {
        case "management" :: "aggregate-metrics" :: Nil JsonGet _ => {
          cc => {
            for {
              (Full(u), callContext) <- authorizedAccess(cc)
              _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadAggregateMetrics, callContext)
              httpParams <- NewStyle.function.createHttpParams(cc.url)
              obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
                x => unboxFullOrFail(x, callContext, InvalidFilterParameterFormat)
              }
              aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams) map {
                x => unboxFullOrFail(x, callContext, GetAggregateMetricsError)
              }
            } yield {
              (createAggregateMetricJson(aggregateMetrics), HttpCode.`200`(callContext))
            }
          }

      }
    }
  
    resourceDocs += ResourceDoc(
      addScope,
      implementedInApiVersion,
      nameOf(addScope),
      "POST",
      "/consumers/CONSUMER_ID/scopes",
      "Add Scope for a Consumer.",
      """Create Scope. Grant Role to Consumer.
        |
        |Scopes are used to grant System or Bank level roles to the Consumer (App). (For Account level privileges, see Views)
        |
        |For a System level Role (.e.g CanGetAnyUser), set bank_id to an empty string i.e. "bank_id":""
        |
        |For a Bank level Role (e.g. CanCreateAccount), set bank_id to a valid value e.g. "bank_id":"my-bank-id"
        |
        |""",
      SwaggerDefinitionsJSON.createScopeJson,
      scopeJson,
      List(
        UserNotLoggedIn,
        ConsumerNotFoundById,
        InvalidJsonFormat,
        IncorrectRoleName,
        EntitlementIsBankRole,
        EntitlementIsSystemRole,
        EntitlementAlreadyExists,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagScope, apiTagRole, apiTagNewStyle),
      Some(List(canCreateScopeAtOneBank, canCreateScopeAtAnyBank)))
  
    lazy val addScope : OBPEndpoint = {
      //add access for specific user to a list of views
      case "consumers" :: consumerId :: "scopes" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)

            consumerIdInt <- Future { tryo{consumerId.toInt} } map {
              val msg = s"$ConsumerNotFoundById Current Value is $consumerId"
              x => unboxFullOrFail(x, callContext, msg)
            }
            
            _ <- Future { Consumers.consumers.vend.getConsumerByPrimaryId(consumerIdInt) } map {
              x => unboxFullOrFail(x, callContext, ConsumerNotFoundById)
            }

            postedData <- Future { tryo{json.extract[CreateScopeJson]} } map {
              val msg = s"$InvalidJsonFormat The Json body should be the $CreateScopeJson "
              x => unboxFullOrFail(x, callContext, msg)
            }

            role <- Future { tryo{valueOf(postedData.role_name)} } map {
              val msg = IncorrectRoleName + postedData.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", ")
              x => unboxFullOrFail(x, callContext, msg)
            }
            
            _ <- Helper.booleanToFuture(failMsg = if (ApiRole.valueOf(postedData.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole) {
              ApiRole.valueOf(postedData.role_name).requiresBankId == postedData.bank_id.nonEmpty
            }
            
            allowedEntitlements = canCreateScopeAtOneBank :: canCreateScopeAtAnyBank :: Nil
            allowedEntitlementsTxt = s"$UserHasMissingRoles ${allowedEntitlements.mkString(", ")}!"

            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = allowedEntitlementsTxt)(postedData.bank_id, u.userId, allowedEntitlements)

            _ <- Helper.booleanToFuture(failMsg = BankNotFound) {
              postedData.bank_id.nonEmpty == false || Bank(BankId(postedData.bank_id), callContext).map(_._1).isEmpty == false
            }

            _ <- Helper.booleanToFuture(failMsg = EntitlementAlreadyExists) {
              hasScope(postedData.bank_id, consumerId, role) == false
            }
            
            addedEntitlement <- Future {Scope.scope.vend.addScope(postedData.bank_id, consumerId, postedData.role_name)} map { unboxFull(_) }
            
          } yield {
            (JSONFactory300.createScopeJson(addedEntitlement), HttpCode.`201`(callContext))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      deleteScope,
      implementedInApiVersion,
      nameOf(deleteScope),
      "DELETE",
      "/consumers/CONSUMER_ID/scope/SCOPE_ID",
      "Delete Consumer Scope",
      """Delete Consumer Scope specified by SCOPE_ID for an consumer specified by CONSUMER_ID
        |
        |Authentication is required and the user needs to be a Super Admin.
        |Super Admins are listed in the Props file.
        |
        |
      """.stripMargin,
      emptyObjectJson,
      emptyObjectJson,
      List(UserNotLoggedIn, UserNotSuperAdmin, EntitlementNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagScope, apiTagRole, apiTagEntitlement, apiTagNewStyle))

    lazy val deleteScope: OBPEndpoint = {
      case "consumers" :: consumerId :: "scope" :: scopeId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            consumer <- Future{callContext.get.consumer} map {
              x => unboxFullOrFail(x, callContext, InvalidConsumerCredentials)
            }
            _ <- Future {hasEntitlementAndScope("", u.userId, consumer.id.get.toString, canDeleteScopeAtAnyBank)}  map ( fullBoxOrException(_))
            scope <- Future{ Scope.scope.vend.getScopeById(scopeId) ?~! ScopeNotFound } map {
              val msg = s"$ScopeNotFound Current Value is $scopeId"
              x => unboxFullOrFail(x, callContext, msg)
            }
            _ <- Helper.booleanToFuture(failMsg = ConsumerDoesNotHaveScope) { scope.scopeId ==scopeId }
            _ <- Future {Scope.scope.vend.deleteScope(Full(scope))} 
          } yield
            (JsRaw(""), HttpCode.`200`(callContext))
      }
    }
  
    resourceDocs += ResourceDoc(
      getScopes,
      implementedInApiVersion,
      nameOf(getScopes),
      "GET",
      "/consumers/CONSUMER_ID/scopes",
      "Get Scopes for Consumer",
      s"""Get all the scopes for an consumer specified by CONSUMER_ID
        |
        |${authenticationRequiredMessage(true)}
        |
        |
      """.stripMargin,
      emptyObjectJson,
      scopeJsons,
      List(UserNotLoggedIn, UserNotSuperAdmin, EntitlementNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagScope, apiTagRole, apiTagEntitlement, apiTagNewStyle))
  
    lazy val getScopes: OBPEndpoint = {
      case "consumers" :: consumerId :: "scopes" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            consumer <- Future{callContext.get.consumer} map {
              x => unboxFullOrFail(x , callContext, InvalidConsumerCredentials)
            }
            _ <- Future {hasEntitlementAndScope("", u.userId, consumer.id.get.toString, canGetEntitlementsForAnyUserAtAnyBank)} flatMap {unboxFullAndWrapIntoFuture(_)}
            scopes <- Future { Scope.scope.vend.getScopesByConsumerId(consumerId)} map { unboxFull(_) }
          } yield
            (JSONFactory300.createScopeJSONs(scopes), HttpCode.`200`(callContext))
      }
    }

    resourceDocs += ResourceDoc(
      getBanks,
      implementedInApiVersion,
      nameOf(getBanks),
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
      apiTagBank :: apiTagNewStyle :: Nil)

    //The Json Body is totally the same as V121, just use new style endpoint.
    lazy val getBanks : OBPEndpoint = {
      case "banks" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (banks, callContext) <- NewStyle.function.getBanks(callContext)
          } yield 
            (JSONFactory300.createBanksJson(banks), HttpCode.`200`(callContext))
      }
    }
  
    resourceDocs += ResourceDoc(
      bankById,
      implementedInApiVersion,
      nameOf(bankById),
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
      apiTagBank :: apiTagNewStyle :: Nil)

    //The Json Body is totally the same as V121, just use new style endpoint.
    lazy val bankById : OBPEndpoint = {
      //get bank by id
      case "banks" :: BankId(bankId) :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
          } yield
            (JSONFactory.createBankJSON(bank), HttpCode.`200`(callContext))
      }
    }




  }
}
