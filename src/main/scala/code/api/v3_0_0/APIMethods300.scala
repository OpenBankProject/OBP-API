package code.api.v3_0_0

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.{Date, Locale}

import code.accountholder.AccountHolders
import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{bankJSON, banksJSON, _}
import code.api.util.APIUtil.{canGetAtm, _}
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.util.Glossary.GlossaryItem
import code.api.util._
import code.api.v1_2_1.{BankJSON, BanksJSON, JSONFactory}
import code.api.v2_0_0.{CreateEntitlementJSON, JSONFactory200}
import code.api.v3_0_0.JSONFactory300._
import code.atms.Atms.AtmId
import code.bankconnectors._
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.branches.Branches
import code.branches.Branches.BranchId
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.entitlementrequest.EntitlementRequest
import code.metrics.{APIMetrics, MappedMetric, OBPUrlQueryParams}
import code.model.{BankId, ViewId, _}
import code.search.elasticsearchWarehouse
import code.users.Users
import code.util.Helper
import code.util.Helper.{DateFormatWithCurrentTimeZone, booleanToBox}
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.{Extraction, compactRender}
import net.liftweb.mapper.DB
import net.liftweb.util.Helpers.{now, tryo}

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.scope.Scope
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers._




trait APIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations3_0_0 = new Object() {

    val implementedInApiVersion: ApiVersion = ApiVersion.v3_0_0 // was noV

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    resourceDocs += ResourceDoc(
      getViewsForBankAccount,
      implementedInApiVersion,
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
      viewsJsonV300,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView +"userId : " + u.userPrimaryId + ". account : " + accountId){
                u.hasOwnerViewAccess(BankIdAccountId(account.bankId, account.accountId))
              }
            } yield {
              for {
                views <- Full(Views.views.vend.viewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
              } yield {
                (createViewsJSON(views), callContext.map(_.copy(httpCode = Some(200))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    resourceDocs += ResourceDoc(
      createViewForBankAccount,
      implementedInApiVersion,
      "createViewForBankAccount",
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
      List(apiTagView, apiTagAccount))

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              createViewJson <- Future { tryo{json.extract[CreateViewJson]} } map {
                val msg = s"$InvalidJsonFormat The Json body should be the $CreateViewJson "
                x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
              _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat) {
                createViewJson.name.startsWith("_")
              }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
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
      "getPermissionForUserForBankAccount",
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
      List(apiTagView, apiTagAccount, apiTagUser))
  
    lazy val getPermissionForUserForBankAccount : OBPEndpoint = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bank <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            account <- Future { BankAccount(bankId, accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(AccountNotFound, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            permission <- Future { account permission(u, provider, providerId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UserNoOwnerView, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (createViewsJSON(permission.views.sortBy(_.viewId.value)), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateViewForBankAccount,
      implementedInApiVersion,
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
      updateViewJSON,
      viewJsonV300,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount)
    )

    lazy val updateViewForBankAccount : OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              updateJson <- Future { tryo{json.extract[UpdateViewJSON]} } map {
                val msg = s"$InvalidJsonFormat The Json body should be the $UpdateViewJSON "
                x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
              _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat) {
                updateJson.metadata_view.startsWith("_")
              }
              _ <- Views.views.vend.viewFuture(ViewId(viewId.value), BankIdAccountId(bankId, accountId)) map {
                x => fullBoxOrException(
                  x ~> APIFailureNewStyle(s"$ViewNotFound. Check your post json body, metadata_view = ${updateJson.metadata_view}. It should be an existing VIEW_ID, eg: owner", 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(bankId, accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              _ <- Helper.booleanToFuture(failMsg = SystemViewsCanNotBeModified) {
                !view.isSystem
              }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
            } yield {
              for {
                updatedView <- account.updateView(u, viewId, updateJson)
              } yield {
                (JSONFactory300.createViewJSON(updatedView), callContext.map(_.copy(httpCode = Some(200))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      getPrivateAccountById,
      implementedInApiVersion,
      "getPrivateAccountById",
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
        |This call provides balance and other account information via delegated authenticaiton using OAuth.
        |
        |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
        |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV300,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  Nil)
    lazy val getPrivateAccountById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(account.bankId, account.accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {
                (u.hasViewAccess(view))
              }
            } yield {
              for {
                moderatedAccount <- account.moderatedBankAccount(view, user)
              } yield {
                (createCoreBankAccountJSON(moderatedAccount), callContext.map(_.copy(httpCode = Some(200))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      getPublicAccountById,
      implementedInApiVersion,
      "getPublicAccountById",
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
        |This call provides balance and other account information via delegated authenticaiton using OAuth.
        |
        |${authenticationRequiredMessage(false)}
        |
        |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV300,
      List(BankNotFound,AccountNotFound,ViewNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccountPublic :: apiTagAccount ::  Nil)

    lazy val getPublicAccountById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "public" :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            account <- Future { BankAccount(bankId, accountId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(account.bankId, account.accountId)) map {
              val msg = s"$ViewNotFound(${viewId.value})"
              x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            moderatedAccount <- Future {account.moderatedBankAccount(view, Empty) } map {
              x => fullBoxOrException(x)
            } map { unboxFull(_) }
          } yield {
            (createCoreBankAccountJSON(moderatedAccount), None)
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCoreAccountById,
      implementedInApiVersion,
      "getCoreAccountById",
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
      apiTagAccount ::  Nil)
    lazy val getCoreAccountById : OBPEndpoint = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            account <- Future { BankAccount(bankId, accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            // Assume owner view was requested
            view <- Views.views.vend.viewFuture(ViewId("owner"), BankIdAccountId(account.bankId, account.accountId)) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {(u.hasViewAccess(view))}
          } yield {
            for {
              moderatedAccount <- account.moderatedBankAccount(view, user)
            } yield {
              (createCoreBankAccountJSON(moderatedAccount), callContext.map(_.copy(httpCode = Some(200))))
            }
          }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      corePrivateAccountsAllBanks,
      implementedInApiVersion,
      "corePrivateAccountsAllBanks",
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
      List(apiTagAccount, apiTagPrivateData))


    apiRelations += ApiRelation(corePrivateAccountsAllBanks, getCoreAccountById, "detail")
    apiRelations += ApiRelation(corePrivateAccountsAllBanks, corePrivateAccountsAllBanks, "self")



    lazy val corePrivateAccountsAllBanks : OBPEndpoint = {
      //get private accounts for all banks
      case "my" :: "accounts" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            coreAccounts <- {Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)}
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(coreAccounts.getOrElse(Nil)), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getFirehoseAccountsAtOneBank,
      implementedInApiVersion,
      "getFirehoseAccountsAtOneBank",
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
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData),
      Some(List(canUseFirehoseAtAnyBank))
    )

    lazy val getFirehoseAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" :: "accounts"  :: "views" :: ViewId(viewId):: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  ) {
               canUseFirehose(u)
            }
            bankBox <- Future { Bank(bankId) } map {x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))}
            bank<- unboxFullAndWrapIntoFuture(bankBox)
            availableBankIdAccountIdList <- Future {Views.views.vend.getAllFirehoseAccounts(bank.bankId, u) }
            moderatedAccounts = for {
              //Here is a new for-loop to get the moderated accouts for the firehose user, according to the viewId.
              //1 each accountId-> find a proper bankAccount object.
              //2 each bankAccount object find the proper view.
              //3 use view and user to moderate the bankaccount object.
              bankIdAccountId <- availableBankIdAccountIdList
              bankAccount <- Connector.connector.vend.getBankAccount(bankIdAccountId.bankId, bankIdAccountId.accountId) ?~! s"$BankAccountNotFound Current Bank_Id(${bankIdAccountId.bankId}), Account_Id(${bankIdAccountId.accountId}) "
              view <- Views.views.vend.view(viewId, bankIdAccountId)
              moderatedAccount <- bankAccount.moderatedBankAccount(view, user) //Error handling is in lower method
            } yield {
              moderatedAccount
            }
          } yield {
            (JSONFactory300.createFirehoseCoreBankAccountJSON(moderatedAccounts), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getFirehoseTransactionsForBankAccount,
      implementedInApiVersion,
      "getFirehoseTransactionsForBankAccount",
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
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData),
      Some(List(canUseFirehoseAtAnyBank)))

    lazy val getFirehoseTransactionsForBankAccount : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" :: "accounts" ::  AccountId(accountId) :: "views" :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              _ <- Helper.booleanToFuture(failMsg = FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  ) {
               canUseFirehose(u)
              }
              bankAccount <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
            } yield {
              for {
              //Note: error handling and messages for getTransactionParams are in the sub method
                params <- createQueriesByHttpParams(callContext.get.requestHeaders)
                transactions <- bankAccount.getModeratedTransactions(user, view, callContext, params: _*)
              } yield {
                (createTransactionsJson(transactions), callContext.map(_.copy(httpCode = Some(200))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      getCoreTransactionsForBankAccount,
      implementedInApiVersion,
      "getCoreTransactionsForBankAccount",
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
      List(apiTagTransaction, apiTagAccount)
    )

    lazy val getCoreTransactionsForBankAccount : OBPEndpoint = {
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              bankAccount <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              // Assume owner view was requested
              view <- Views.views.vend.viewFuture(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
            } yield {
              for {
                //Note: error handling and messages for getTransactionParams are in the sub method
                params <- createQueriesByHttpParams(callContext.get.requestHeaders)
                transactionsCore <- bankAccount.getModeratedTransactionsCore(user, view, callContext, params: _*)
              } yield {
                (createCoreTransactionsJSON(transactionsCore), callContext.map(_.copy(httpCode = Some(200))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    resourceDocs += ResourceDoc(
      getTransactionsForBankAccount,
      implementedInApiVersion,
      "getTransactionsForBankAccount",
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
      List(apiTagTransaction, apiTagAccount)
    )

    lazy val getTransactionsForBankAccount: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              bankAccount <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              // Assume owner view was requested
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
            } yield {
              for {
              //Note: error handling and messages for getTransactionParams are in the sub method
                params <- createQueriesByHttpParams(callContext.get.requestHeaders)
                transactions <- bankAccount.getModeratedTransactions(user, view, callContext, params: _*)
              } yield {
                (createTransactionsJson(transactions), callContext.map(_.copy(httpCode = Some(200))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    // TODO Put message into doc below if not enabled (but continue to show API Doc)
    resourceDocs += ResourceDoc(
      dataWarehouseSearch,
      implementedInApiVersion,
      "dataWarehouseSearch",
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
      List(apiTagSearchWarehouse),
      Some(List(canSearchWarehouse)))
    val esw = new elasticsearchWarehouse
    lazy val dataWarehouseSearch: OBPEndpoint = {
      case "search" :: "warehouse" :: index :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanSearchWarehouse) {
              hasEntitlement("", u.userId, ApiRole.canSearchWarehouse)
            }
            indexPart <- Future { esw.getElasticSearchUri(index) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ElasticSearchIndexNotFound, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            bodyPart <- Future { tryo(compactRender(json)) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ElasticSearchEmptyQueryBody, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            result: esw.APIResponse <- esw.searchProxyAsyncV300(u.userId, indexPart, bodyPart)
          } yield {
            (esw.parseResponse(result), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }
  
    case class Query(query: String)
    
    resourceDocs += ResourceDoc(
      dataWarehouseStatistics,
      implementedInApiVersion,
      "dataWarehouseStatistics",
      "POST",
      "/search/warehouse/statistics/FIELD",
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
      List(apiTagSearchWarehouse),
      Some(List(canSearchWarehouseStatistics))
    )
    lazy val dataWarehouseStatistics: OBPEndpoint = {
      case "search" :: "warehouse" :: "statistics" :: index :: field :: Nil JsonPost json -> _ => {
        cc =>
          //if (field == "/") throw new RuntimeException("No aggregation field supplied") with NoStackTrace
          for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanSearchWarehouseStatistics) {
              hasEntitlement("", u.userId, ApiRole.canSearchWarehouseStatistics)
            }
            indexPart <- Future { esw.getElasticSearchUri(index) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ElasticSearchIndexNotFound, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            bodyPart <- Future { tryo(compactRender(json)) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ElasticSearchEmptyQueryBody, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            result <- esw.searchProxyStatsAsyncV300(u.userId, indexPart, bodyPart, field)
          } yield {
            (esw.parseResponse(result), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }
    

    resourceDocs += ResourceDoc(
      getUser,
      implementedInApiVersion,
      "getUser",
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
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUser: OBPEndpoint = {
      case "users" :: "email" :: email :: "terminator" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
              hasEntitlement("", u.userId, ApiRole.canGetAnyUser)
            }
            users <- Users.users.vend.getUserByEmailFuture(email)
          } yield {
            (JSONFactory300.createUserJSONs (users), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUserByUserId,
      implementedInApiVersion,
      "getUserByUserId",
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
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUserByUserId: OBPEndpoint = {
      case "users" :: "user_id" :: userId :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
              hasEntitlement("", u.userId, ApiRole.canGetAnyUser)
            }
            user <- Users.users.vend.getUserByUserIdFuture(userId) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UserNotFoundByUsername, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
          } yield {
            (JSONFactory300.createUserJSON (Full(user), entitlements), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUserByUsername,
      implementedInApiVersion,
      "getUserByUsername",
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
      List(apiTagUser),
      Some(List(canGetAnyUser)))


    lazy val getUserByUsername: OBPEndpoint = {
      case "users" :: "username" :: username :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
              hasEntitlement("", u.userId, ApiRole.canGetAnyUser)
            }
            user <- Users.users.vend.getUserByUserNameFuture(username) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UserNotFoundByUsername, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
          } yield {
            (JSONFactory300.createUserJSON (Full(user), entitlements), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAdapter,
      implementedInApiVersion,
      "getAdapter",
      "GET",
      "/banks/BANK_ID/adapter",
      "Get Adapter Info",
      s"""Get basic information about the Adapter listening on behalf of this bank.
        |
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      adapterInfoJsonV300,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagApi))


    lazy val getAdapter: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "adapter" :: Nil JsonGet _ => {
        _ =>
          for {
            _ <- Bank(bankId) ?~! BankNotFound
            ai: InboundAdapterInfoInternal <- Connector.connector.vend.getAdapterInfo() ?~ s"$ConnectorEmptyResponse or not implemented for this instance "
          }
          yield {
            successJsonResponseNewStyle(createAdapterInfoJson(ai), None)
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
      "createBranch",
      "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create Branch for the Bank.
          |
         |${authenticationRequiredMessage(true) }
          |
         |$createBranchEntitlementsRequiredText
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
            bank <- Bank(bankId)?~! BankNotFound
            _ <- booleanToBox(
              hasEntitlement(bank.bankId.value, u.userId, canCreateBranch) == true
              ||
              hasEntitlement("", u.userId, canCreateBranchAtAnyBank) == true
              , createBranchEntitlementsRequiredText
            )
            branchJsonV300 <- tryo {json.extract[BranchJsonV300]} ?~! {ErrorMessages.InvalidJsonFormat + " BranchJsonV300"}
            _ <- booleanToBox(branchJsonV300.bank_id == bank.bankId.value, "BANK_ID has to be the same in the URL and Body")
            branch <- transformToBranchFromV300(branchJsonV300) ?~! {ErrorMessages.CouldNotTransformJsonToInternalModel + " Branch"}
            success: Branches.BranchT <- Connector.connector.vend.createOrUpdateBranch(branch) ?~! {ErrorMessages.CountNotSaveOrUpdateResource + " Branch"}
          } yield {
            val json = JSONFactory300.createBranchJsonV300(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }


    val createAtmEntitlementsRequiredForSpecificBank = canCreateAtm ::  Nil
    val createAtmEntitlementsRequiredForAnyBank = canCreateAtmAtAnyBank ::  Nil

    val createAtmEntitlementsRequiredText = UserHasMissingRoles + createAtmEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createAtmEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createAtm,
      implementedInApiVersion,
      "createAtm",
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM for the Bank.
          |
         |${authenticationRequiredMessage(true) }
          |
         |$createAtmEntitlementsRequiredText
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
            bank <- Bank(bankId)?~! BankNotFound
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
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }



    val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    resourceDocs += ResourceDoc(
      getBranch,
      implementedInApiVersion,
      "getBranch",
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
      List(apiTagBranch, apiTagBank)
    )
    lazy val getBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId) :: Nil JsonGet _ => {
        cc => {
          for {
            (user, callContext) <- extractCallContext(cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotLoggedIn) {
              canGetBranch(getBranchesIsPublic, user)
            }
            _ <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))
            }
            branch <- Connector.connector.vend.getBranchFuture(bankId, branchId) map {
              val msg: String = s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and meta.license.name can not be empty"
              x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createBranchJsonV300(branch), callContext.map(_.copy(httpCode = Some(200))))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getBranches,
      implementedInApiVersion,
      "getBranches",
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
         |
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
      List(apiTagBranch, apiTagBank)
    )
    lazy val getBranches : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: Nil JsonGet _ => {
        cc => {
          val limit = S.param("limit")
          val offset = S.param("offset")
          for {
            (user, callContext) <- extractCallContext(cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotLoggedIn) {
              canGetBranch(getBranchesIsPublic, user)
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
            _ <- Future { Bank(bankId) } map { x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight))) }
            branches <- Connector.connector.vend.getBranchesFuture(bankId) map {
              case Full(List()) | Empty =>
                fullBoxOrException(Empty ?~! BranchesNotFound)
              case Full(list) =>
                val branchesWithLicense = for { branch <- list if branch.meta.license.name.size > 3 } yield branch
                if (branchesWithLicense.size == 0) fullBoxOrException(Empty ?~! branchesNotFoundLicense)
                else Full(branchesWithLicense)
            } map { unboxFull(_) } map {
              // Before we slice we need to sort in order to keep consistent results
              _.sortWith(_.branchId.value < _.branchId.value)
              // Slice the result in next way: from=offset and until=offset + limit
               .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
            }
          } yield {
            (JSONFactory300.createBranchesJson(branches), callContext.map(_.copy(httpCode = Some(200))))
          }
        }
      }
    }

    val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    resourceDocs += ResourceDoc(
      getAtm,
      implementedInApiVersion,
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
         |
         |
          |${authenticationRequiredMessage(!getAtmsIsPublic)}""",
      emptyObjectJson,
      atmJsonV300,
      List(UserNotLoggedIn, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagATM)
    )
    lazy val getAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotLoggedIn) {
              canGetAtm(getAtmsIsPublic, user)
            }
            _ <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))
            }
            atm <- Connector.connector.vend.getAtmFuture(bankId,atmId) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(AtmNotFoundByAtmId, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createAtmJsonV300(atm), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getAtms,
      implementedInApiVersion,
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
      List(apiTagATM)
    )
    lazy val getAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet req => {
        cc => {
          val limit = S.param("limit")
          val offset = S.param("offset")
          for {
            (user, callContext) <- extractCallContext(cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotLoggedIn) {
              canGetBranch(getBranchesIsPublic, user)
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
            _ <- Future { Bank(bankId) } map { x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight))) }
            atms <- Connector.connector.vend.getAtmsFuture(bankId) map {
              case Full(List()) | Empty =>
                fullBoxOrException(Empty ?~! atmsNotFound)
              case Full(list) =>
                val branchesWithLicense = for { branch <- list if branch.meta.license.name.size > 3 } yield branch
                if (branchesWithLicense.size == 0) fullBoxOrException(Empty ?~! atmsNotFoundLicense)
                else Full(branchesWithLicense)
            } map { unboxFull(_) } map {
              // Before we slice we need to sort in order to keep consistent results
              _.sortWith(_.atmId.value < _.atmId.value)
                // Slice the result in next way: from=offset and until=offset + limit
                .slice(offset.getOrElse("0").toInt, offset.getOrElse("0").toInt + limit.getOrElse("100").toInt)
            }
          } yield {
            (JSONFactory300.createAtmsJsonV300(atms), callContext.map(_.copy(httpCode = Some(200))))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getUsers,
      implementedInApiVersion,
      "getUsers",
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
      List(apiTagUser),
      Some(List(canGetAnyUser)))

    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
              hasEntitlement("", u.userId, ApiRole.canGetAnyUser)
            }
            
            httpParams <- createHttpParamsByUrlFuture(cc.url) map { unboxFull(_) }
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidFilterParameterFormat, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            users <- Users.users.vend.getAllUsersF(obpQueryParams)
          } yield {
            (JSONFactory300.createUserJSONs (users), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCustomersForUser,
      implementedInApiVersion,
      "getCustomersForUser",
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
      customerJsonV210,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagUser))



    // This can be considered a reference new style endpoint.
    // This is a partial function. The lazy value should have a meaningful name.
    lazy val getCustomersForUser : OBPEndpoint = {
      // This defines the URL path and method (GET) for which this partial function will accept the call.
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        // We have the Call Context (cc) object (provided through the OBPEndpoint type)
        // The Call Context contains the authorisation headers etc.
        cc => {
          for {
            // Extract the (boxed) user from the headers and get an updated callContext
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            // Unbox the user so we can use its attributes e.g. username
            u <- unboxFullAndWrapIntoFuture{ user }
            // Now here is the business logic.
            // Get The customers related to a user. Process the resonse which might be an Exception
            customers <- Connector.connector.vend.getCustomersByUserIdFuture(u.userId, callContext) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            // Create the JSON to return. We also return the callContext
            (JSONFactory300.createCustomersJson(customers), callContext.map(_.copy(httpCode = Some(200))))
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
      List(apiTagUser))

    lazy val getCurrentUser: OBPEndpoint = {
      case "users" :: "current" :: Nil JsonGet _ => {
        cc => {
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(u.userId) map {
              getFullBoxOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createUserJSON (user, entitlements), callContext.map(_.copy(httpCode = Some(200))))
          }
        }
      }
    }
  
    resourceDocs += ResourceDoc(
      privateAccountsAtOneBank,
      implementedInApiVersion,
      "privateAccountsAtOneBank",
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
      List(apiTagAccount)
    )
  
    lazy val privateAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bank <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))
            }
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            accounts <- Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(accounts), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPrivateAccountIdsbyBankId,
      implementedInApiVersion,
      "getPrivateAccountIdsbyBankId",
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
      List(apiTagAccount)
    )
  
    lazy val getPrivateAccountIdsbyBankId : OBPEndpoint = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "account_ids" :: "private"::Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bank <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))
            }
            bankAccountIds <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
          } yield {
            (JSONFactory300.createAccountsIdsByBankIdAccountIds(bankAccountIds), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getOtherAccountsForBankAccount,
      implementedInApiVersion,
      "getOtherAccountsForBankAccount",
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
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount))
  
    lazy val getOtherAccountsForBankAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet req => {
        cc =>
          for {
            account <- Connector.connector.vend.checkBankAccountExists(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccounts <- account.moderatedOtherBankAccounts(view, cc.user)
          } yield {
            val otherBankAccountsJson = createOtherBankAccountsJson(otherBankAccounts)
            successJsonResponse(Extraction.decompose(otherBankAccountsJson))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getOtherAccountByIdForBankAccount,
      implementedInApiVersion,
      "getOtherAccountByIdForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
      "Get Other Account by Id.",
      s"""Returns data about the Other Account that has shared at least one transaction with ACCOUNT_ID at BANK_ID.
         |${authenticationRequiredMessage(false)}
         |
         |Authentication is required if the view is not public.""",
      emptyObjectJson,
      otherAccountJsonV300,
      List(BankAccountNotFound, UnknownError),
      Catalogs(notCore, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount))
  
    lazy val getOtherAccountByIdForBankAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet req => {
        cc =>
          for {
            account <- Connector.connector.vend.checkBankAccountExists(bankId, accountId) ?~! BankAccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            otherBankAccount <- account.moderatedOtherBankAccount(other_account_id, view, cc.user)
          } yield {
            val otherBankAccountJson = createOtherBankAccount(otherBankAccount)
            successJsonResponse(Extraction.decompose(otherBankAccountJson))
          }
      }
    }


    resourceDocs += ResourceDoc(
      addEntitlementRequest,
      implementedInApiVersion,
      "addEntitlementRequest",
      "POST",
      "/entitlement-requests",
      "Add Entitlement Request for a Logged User.",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser))

    lazy val addEntitlementRequest : OBPEndpoint = {
      case "entitlement-requests" :: Nil JsonPost json -> _ => {
        cc =>
          for {
              (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture(user)
              postedData <- Future { tryo{json.extract[CreateEntitlementRequestJSON]} } map {
                val msg = s"$InvalidJsonFormat The Json body should be the $CreateEntitlementRequestJSON "
                x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              _ <- Future { if (postedData.bank_id == "") Full() else Bank(BankId(postedData.bank_id)) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, callContext.map(_.toLight)))
              }
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
                x => fullBoxOrException(x ~> APIFailureNewStyle(EntitlementRequestCannotBeAdded, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
            } yield {
              (JSONFactory300.createEntitlementRequestJSON(addedEntitlementRequest), callContext.map(_.copy(httpCode = Some(200))))
            }
      }
    }


    resourceDocs += ResourceDoc(
      getAllEntitlementRequests,
      implementedInApiVersion,
      "getAllEntitlementRequests",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementRequestsAtAnyBank)))

    lazy val getAllEntitlementRequests : OBPEndpoint = {
      case "entitlement-requests" :: Nil JsonGet _ => {
        cc =>
          val allowedEntitlements = canGetEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture(user)
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + allowedEntitlementsTxt) {
              hasAtLeastOneEntitlement("", u.userId, allowedEntitlements)
            }
            getEntitlementRequests <- EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture() map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(getEntitlementRequests), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getEntitlementRequests,
      implementedInApiVersion,
      "getEntitlementRequests",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementRequestsAtAnyBank)))

    lazy val getEntitlementRequests : OBPEndpoint = {
      case "users" :: userId :: "entitlement-requests" :: Nil JsonGet _ => {
        cc =>
          val allowedEntitlements = canGetEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture(user)
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + allowedEntitlementsTxt) {
              hasAtLeastOneEntitlement("", u.userId, allowedEntitlements)
            }
            getEntitlementRequests <- EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture(userId) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(getEntitlementRequests), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getEntitlementRequestsForCurrentUser,
      implementedInApiVersion,
      "getEntitlementRequestsForCurrentUser",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None)

    lazy val getEntitlementRequestsForCurrentUser : OBPEndpoint = {
      case "my" :: "entitlement-requests" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture(user)
            getEntitlementRequests <- EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture(u.userId) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(getEntitlementRequests), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      deleteEntitlementRequest,
      implementedInApiVersion,
      "deleteEntitlementRequest",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canDeleteEntitlementRequestsAtAnyBank)))

    lazy val deleteEntitlementRequest : OBPEndpoint = {
      case "entitlement-requests" :: entitlementRequestId :: Nil JsonDelete _ => {
        cc =>
          val allowedEntitlements = canDeleteEntitlementRequestsAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture(user)
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + allowedEntitlementsTxt) {
              hasAtLeastOneEntitlement("", u.userId, allowedEntitlements)
            }
            deleteEntitlementRequest <- EntitlementRequest.entitlementRequest.vend.deleteEntitlementRequestFuture(entitlementRequestId) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (Full(deleteEntitlementRequest), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getEntitlementsForCurrentUser,
      implementedInApiVersion,
      "getEntitlementsForCurrentUser",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None)

    lazy val getEntitlementsForCurrentUser : OBPEndpoint = {
      case "my" :: "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture(user)
            getEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(u.userId) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory200.createEntitlementJSONs(getEntitlements), callContext.map(_.copy(httpCode = Some(200))))
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
      "getAccountsHeld",
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
      List(apiTagAccount, apiTagView)
    )
  
    lazy val getAccountsHeld : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts-held" ::  Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bank <- Future { Bank(bankId) } map { getFullBoxOrFail(_, callContext, BankNotFound,400) }
            availableAccounts <- Future{ AccountHolders.accountHolders.vend.getAccountsHeld(bankId, u)}
            accounts <- Connector.connector.vend.getCoreBankAccountsHeldFuture(availableAccounts.toList, callContext) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(accounts), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAggregateMetrics,
      implementedInApiVersion,
      "getAggregateMetrics",
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
      List(apiTagMetric, apiTagAggregateMetrics),
      Some(List(canReadAggregateMetrics)))

      lazy val getAggregateMetrics : OBPEndpoint = {
        case "management" :: "aggregate-metrics" :: Nil JsonGet _ => {
          cc => {
            for {
              (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              
              _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanReadAggregateMetrics) {
                hasEntitlement("", u.userId, ApiRole.canReadAggregateMetrics)
              }
              
              httpParams <- createHttpParamsByUrlFuture(cc.url) map { unboxFull(_) }
              
              obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidFilterParameterFormat, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              
              
              aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(GetAggregateMetricsError, 400, callContext.map(_.toLight)))
              } map { unboxFull(_) }
              
            } yield {
              (createAggregateMetricJson(aggregateMetrics), callContext.map(_.copy(httpCode = Some(200))))
            }
          }

      }
    }
  
    resourceDocs += ResourceDoc(
      addScope,
      implementedInApiVersion,
      "addScope",
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
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canCreateScopeAtOneBank, canCreateScopeAtAnyBank)))
  
    lazy val addScope : OBPEndpoint = {
      //add access for specific user to a list of views
      case "consumers" :: consumerId :: "scopes" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }

            consumerIdInt <- Future { tryo{consumerId.toInt} } map {
              val msg = s"$ConsumerNotFoundById Current Value is $consumerId"
              x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            _ <- Future { Consumers.consumers.vend.getConsumerByPrimaryId(consumerIdInt) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ConsumerNotFoundById, 400, callContext.map(_.toLight)))
            }

            postedData <- Future { tryo{json.extract[CreateScopeJson]} } map {
              val msg = s"$InvalidJsonFormat The Json body should be the $CreateScopeJson "
              x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            role <- Future { tryo{valueOf(postedData.role_name)} } map {
              val msg = IncorrectRoleName + postedData.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", ")
              x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            _ <- Helper.booleanToFuture(failMsg = if (ApiRole.valueOf(postedData.role_name).requiresBankId) EntitlementIsBankRole else EntitlementIsSystemRole) {
              ApiRole.valueOf(postedData.role_name).requiresBankId == postedData.bank_id.nonEmpty
            }
            
            allowedEntitlements = canCreateScopeAtOneBank :: canCreateScopeAtAnyBank :: Nil

            _ <- Helper.booleanToFuture(failMsg = s"$UserHasMissingRoles ${allowedEntitlements.mkString(", ")}!") {
               hasAtLeastOneEntitlement(postedData.bank_id, u.userId, allowedEntitlements)
            }

            _ <- Helper.booleanToFuture(failMsg = BankNotFound) {
              postedData.bank_id.nonEmpty == false || Bank(BankId(postedData.bank_id)).isEmpty == false
            }

            _ <- Helper.booleanToFuture(failMsg = EntitlementAlreadyExists) {
              hasScope(postedData.bank_id, consumerId, role) == false
            }
            
            addedEntitlement <- Future {Scope.scope.vend.addScope(postedData.bank_id, consumerId, postedData.role_name)} map { unboxFull(_) }
            
          } yield {
            (JSONFactory300.createScopeJson(addedEntitlement), callContext.map(_.copy(httpCode = Some(200))))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      deleteScope,
      implementedInApiVersion,
      "deleteScope",
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
      List(apiTagRole, apiTagUser, apiTagEntitlement))

    lazy val deleteScope: OBPEndpoint = {
      case "consumers" :: consumerId :: "scope" :: scopeId :: Nil JsonDelete _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user } 
            
            consumer <- Future{callContext.get.consumer} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConsumerCredentials, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            _ <- Future {hasEntitlementAndScope("", u.userId, consumer.id.get.toString, canDeleteScopeAtAnyBank)}  map ( fullBoxOrException(_))
            scope <- Future{ Scope.scope.vend.getScopeById(scopeId) ?~! ScopeNotFound } map {
              val msg = s"$ScopeNotFound Current Value is $scopeId"
              x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            _ <- Helper.booleanToFuture(failMsg = ConsumerDoesNotHaveScope) { scope.scopeId ==scopeId }
            
            _ <- Future {Scope.scope.vend.deleteScope(Full(scope))} 
          } yield
            (JsRaw(""), callContext.map(_.copy(httpCode = Some(200))))
      }
    }
  
    resourceDocs += ResourceDoc(
      getScopes,
      implementedInApiVersion,
      "getScopes",
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
      List(apiTagRole, apiTagUser, apiTagEntitlement))
  
    lazy val getScopes: OBPEndpoint = {
      case "consumers" :: consumerId :: "scopes" :: Nil JsonGet _ => {
        cc =>
          for {
            
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            consumer <- Future{callContext.get.consumer} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConsumerCredentials, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            _ <- Future {hasEntitlementAndScope("", u.userId, consumer.id.get.toString, canGetEntitlementsForAnyUserAtAnyBank)} flatMap {unboxFullAndWrapIntoFuture(_)}
            scopes <- Future { Scope.scope.vend.getScopesByConsumerId(consumerId)} map { unboxFull(_) }
           
          } yield
            (JSONFactory300.createScopeJSONs(scopes), callContext.map(_.copy(httpCode = Some(200))))
      }
    }

    resourceDocs += ResourceDoc(
      getBanks,
      implementedInApiVersion,
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

    //The Json Body is totally the same as V121, just use new style endpoint.
    lazy val getBanks : OBPEndpoint = {
      case "banks" :: Nil JsonGet req => {
        cc =>
          for {
            banksBox: Box[List[Bank]] <- Connector.connector.vend.getBanksFuture()
            banks <- unboxFullAndWrapIntoFuture{ banksBox }
          } yield 
            (JSONFactory300.createBanksJson(banks), Some(cc.copy(httpCode = Some(200))))
      }
    }
  
    resourceDocs += ResourceDoc(
      bankById,
      implementedInApiVersion,
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

    //The Json Body is totally the same as V121, just use new style endpoint.
    lazy val bankById : OBPEndpoint = {
      //get bank by id
      case "banks" :: BankId(bankId) :: Nil JsonGet req => {
        cc =>
          for {
            bankBox <- Connector.connector.vend.getBankFuture(bankId)
            bank <- unboxFullAndWrapIntoFuture{ bankBox }
          } yield
            (JSONFactory.createBankJSON(bank), Some(cc.copy(httpCode = Some(200))))
      }
    }

    /* WIP
        resourceDocs += ResourceDoc(
          getOtherAccountsForBank,
          apiVersion,
          "getOtherAccountsForBank",
          "GET",
          "/banks/BANK_ID/other_accounts",
          "Get Other Accounts of a Bank.",
          s"""Returns data about all the other accounts at BANK_ID.
              |This is a fireho
              |${authenticationRequiredMessage(true)}
              |""",
          emptyObjectJson,
          otherAccountsJSON,
          List(
            BankAccountNotFound,
            UnknownError
          ),
          Catalogs(notCore, PSD2, OBWG),
          List(apiTagPerson, apiTagUser, apiTagAccount, apiTagCounterparty))

        lazy val getOtherAccountsForBank : OBPEndpoint = {
          //get other accounts for one account
          case "banks" :: BankId(bankId) :: "other_accounts" :: Nil JsonGet req => {
            cc =>
              for {
                _ <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
                account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
                view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
                otherBankAccounts <- account.moderatedOtherBankAccounts(view, user)
              } yield {
                val otherBankAccountsJson = JSONFactory.createOtherBankAccountsJSON(otherBankAccounts)
                successJsonResponse(Extraction.decompose(otherBankAccountsJson))
              }
          }
        }
    */






  }
}
