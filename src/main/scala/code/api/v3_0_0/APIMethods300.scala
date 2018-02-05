package code.api.v3_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{canGetAtm, _}
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, CallContext, ErrorMessages}
import code.api.v3_0_0.JSONFactory300._
import code.atms.Atms.AtmId
import code.bankconnectors.Connector
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.branches.Branches
import code.branches.Branches.BranchId
import code.entitlement.Entitlement
import code.entitlementrequest.EntitlementRequest
import code.model.{BankId, ViewId, _}
import code.search.elasticsearchWarehouse
import code.users.Users
import code.util.Helper
import code.util.Helper.booleanToBox
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future




trait APIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations3_0_0 = new Object() {

    val implementedInApiVersion: String = "3_0_0" // TODO Use ApiVersions enumeration

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
      viewsJsonV300,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet json => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ?~! BankAccountNotFound)
              } map { unboxFull(_) }
            } yield {
              for {
                views <- account views u  // In other words: views = account.views(u) This calls BankingData.scala BankAccount.views
              } yield {
                (createViewsJSON(views), callContext)
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
      """Create a view on bank account
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
      SwaggerDefinitionsJSON.createViewJson,
      viewJsonV300,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              u <- unboxFullAndWrapIntoFuture{ user }
              postedData <- Future { tryo{json.extract[CreateViewJson]} } map {
                x => fullBoxOrException(x ?~! s"$InvalidJsonFormat The Json body should be the $CreateViewJson ")
              } map { unboxFull(_) }
              //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
              _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat) {
                postedData.name.startsWith("_")
              }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ?~! BankAccountNotFound)
              } map { unboxFull(_) }
            } yield {
              for {
                view <- account createView (u, postedData)
              } yield {
                (JSONFactory300.createViewJSON(view), callContext.map(_.copy(httpCode = Some(201))))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    resourceDocs += ResourceDoc(
      updateViewForBankAccount,
      implementedInApiVersion,
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
      updateViewJSON,
      viewJsonV300,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView)
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
                x => fullBoxOrException(x ?~! s"$InvalidJsonFormat The Json body should be the $UpdateViewJSON ")
              } map { unboxFull(_) }
              //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
              _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat) {
                viewId.value.startsWith("_")
              }
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(bankId, accountId)) map {
                x => fullBoxOrException(x ?~! ViewNotFound)
              } map { unboxFull(_) }
              _ <- Helper.booleanToFuture(failMsg = SystemViewsCanNotBeModified) {
                !view.isSystem
              }
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ?~! BankAccountNotFound)
              } map { unboxFull(_) }
            } yield {
              for {
                updatedView <- account.updateView(u, viewId, updateJson)
              } yield {
                (JSONFactory300.createViewJSON(updatedView), callContext)
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    resourceDocs += ResourceDoc(
      accountById,
      implementedInApiVersion,
      "accountById",
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
        |
        |This endpoint works with firehose.
        |
        |""".stripMargin,
      emptyObjectJson,
      moderatedAccountJsonV300,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  Nil)
    lazy val accountById : OBPEndpoint = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet json => {
        cc =>
          val res =
            for {
              (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
              account <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ?~! BankAccountNotFound)
              } map { unboxFull(_) }
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(account.bankId, account.accountId)) map {
                x => fullBoxOrException(x ?~! ViewNotFound)
              } map { unboxFull(_) }
              availableViews <- (account.permittedViewsFuture(user))
              _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {
                (availableViews.contains(view))
              }
            } yield {
              for {
                moderatedAccount <- account.moderatedBankAccount(view, user)
              } yield {
                val viewsAvailable = availableViews.map(JSONFactory300.createViewJSON).sortBy(_.short_name)
                (createCoreBankAccountJSON(moderatedAccount, viewsAvailable), callContext)
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    resourceDocs += ResourceDoc(
      getCoreAccountById,
      implementedInApiVersion,
      "getCoreAccountById",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      """Information returned about the account specified by ACCOUNT_ID:
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
        |OAuth authentication is required
        |
        |This endpoint works with firehose.
        |
        |""".stripMargin,
      emptyObjectJson,
      moderatedCoreAccountJsonV300,
      List(BankAccountNotFound,UnknownError),
      Catalogs(Core, PSD2, notOBWG),
      apiTagAccount ::  Nil)
    lazy val getCoreAccountById : OBPEndpoint = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet json => {
        cc =>
          val res =
            for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            account <- Future { BankAccount(bankId, accountId, callContext) } map {
              x => fullBoxOrException(x ?~! BankAccountNotFound)
            } map { unboxFull(_) }
            availableViews <- (account.permittedViewsFuture(user))
            // Assume owner view was requested
            view <- Views.views.vend.viewFuture(ViewId("owner"), BankIdAccountId(account.bankId, account.accountId)) map {
              x => fullBoxOrException(x ?~! ViewNotFound)
            } map { unboxFull(_) }
          } yield {
            for {
              moderatedAccount <- account.moderatedBankAccount(view, user)
            } yield {
              val viewsAvailable = availableViews.map(JSONFactory300.createViewJSON)
              (createCoreBankAccountJSON(moderatedAccount, viewsAvailable), callContext)
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
      "Get Accounts at all Banks (Private)",
      s"""Get private accounts at all banks (Authenticated access)
         |Returns the list of accounts containing private views for the user at all banks.
         |For each account the API returns the ID and the available views.
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
      case "my" :: "accounts" :: Nil JsonGet json => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            availableAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            coreAccounts <- {Connector.connector.vend.getCoreBankAccountsFuture(availableAccounts, callContext)}
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(coreAccounts.getOrElse(Nil)), callContext)
          }
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
        |* obp_sort_direction=ASC/DESC ==> default value: DESC. The sort field is the completed date.
        |* obp_limit=NUMBER ==> default value: 50
        |* obp_offset=NUMBER ==> default value: 0
        |* obp_from_date=DATE => default value: Thu Jan 01 01:00:00 CET 1970 (format below)
        |* obp_to_date=DATE => default value: 3049-01-01
        |
        |**Date format parameter**: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2014-07-01T00:00:00.000Z) ==> time zone is UTC.""",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagTransaction, apiTagAccount)
    )

    lazy val getCoreTransactionsForBankAccount : OBPEndpoint = {
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet json => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              bankAccount <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ?~! BankAccountNotFound)
              } map { unboxFull(_) }
              // Assume owner view was requested
              view <- Views.views.vend.viewFuture(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId)) map {
                x => fullBoxOrException(x ?~! ViewNotFound)
              } map { unboxFull(_) }
            } yield {
              for {
                //Note: error handling and messages for getTransactionParams are in the sub method
                params <- getTransactionParams(json)
                transactionsCore <- bankAccount.getModeratedTransactionsCore(user, view, params: _*)(callContext)
              } yield {
                (createCoreTransactionsJSON(transactionsCore), callContext)
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
        |* obp_sort_direction=ASC/DESC ==> default value: DESC. The sort field is the completed date.
        |* obp_limit=NUMBER ==> default value: 50
        |* obp_offset=NUMBER ==> default value: 0
        |* obp_from_date=DATE => default value: date of the oldest transaction registered (format below)
        |* obp_to_date=DATE => default value: 3049-01-01
        |
        |**Date format parameter**: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2014-07-01T00:00:00.000Z) ==> time zone is UTC.""",
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
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet json => {
        cc =>
          val res =
            for {
              (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
              bankAccount <- Future { BankAccount(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ?~! BankAccountNotFound)
              } map { unboxFull(_) }
              // Assume owner view was requested
              view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId)) map {
                x => fullBoxOrException(x ?~! ViewNotFound)
              } map { unboxFull(_) }
            } yield {
              for {
              //Note: error handling and messages for getTransactionParams are in the sub method
                params <- getTransactionParams(json)
                transactions <- bankAccount.getModeratedTransactions(user, view, params: _*)(callContext)
              } yield {
                (createTransactionsJson(transactions), callContext)
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }


    // TODO Put message into doc below if not enabled (but continue to show API Doc)
    resourceDocs += ResourceDoc(
      elasticSearchWarehouseV300,
      implementedInApiVersion,
      "elasticSearchWarehouseV300",
      "POST",
      "/search/warehouse",
      "Search Warehouse Data Via Elasticsearch",
      s"""
        |Search warehouse data via Elastic Search.
        |
        |${authenticationRequiredMessage(true)}
        |
        |CanSearchWarehouse entitlement is required to search warehouse data!
        |
        |Send your email, name, project name and user_id to the admins to get access.
        |
        |Elastic (search) is used in the background. See links below for syntax.
        |
        |This version differs from v2.0.0
        |
        |
        |
        |Example of usage:
        |
        |POST /search/warehouse
        |
        |{
        |  "es_uri_part": "/THE_INDEX_YOU_WANT_TO_USE/_search?pretty=true",
        |  "es_body_part": {
        |    "query": {
        |      "range": {
        |        "postDate": {
        |          "from": "2011-12-10",
        |          "to": "2011-12-12"
        |        }
        |      }
        |    }
        |  }
        |}
        |
        |Elastic simple query: https://www.elastic.co/guide/en/elasticsearch/reference/5.3/search-uri-request.html
        |
        |Elastic JSON query: https://www.elastic.co/guide/en/elasticsearch/reference/5.3/query-filter-context.html
        |
        |Elastic aggregations: https://www.elastic.co/guide/en/elasticsearch/reference/5.3/search-aggregations.html
        |
        |
        """,
      ElasticSearchJSON(es_uri_part = "/_search", es_body_part = EmptyClassJson()),
      emptyObjectJson, //TODO what is output here?
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagDataWarehouse),
      Some(List(canSearchWarehouse)))
    // TODO Rewrite as New Style Endpoint
    val esw = new elasticsearchWarehouse
    lazy val elasticSearchWarehouseV300: OBPEndpoint = {
      case "search" :: "warehouse" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! ErrorMessages.UserNotLoggedIn
            _ <- Entitlement.entitlement.vend.getEntitlement("", u.userId, ApiRole.CanSearchWarehouse.toString) ?~! {UserHasMissingRoles + CanSearchWarehouse}
          } yield {
            import net.liftweb.json._
            val uriPart = compactRender(json \ "es_uri_part")
            val bodyPart = compactRender(json \ "es_body_part")
            successJsonResponse(Extraction.decompose(esw.searchProxyV300(u.userId, uriPart, bodyPart)))
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
      Catalogs(Core, notPSD2, notOBWG),
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
            (JSONFactory300.createUserJSONs (users), callContext)
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
      Catalogs(Core, notPSD2, notOBWG),
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
              x => fullBoxOrException(x ?~! UserNotFoundByUsername)
            } map { unboxFull(_) }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
          } yield {
            (JSONFactory300.createUserJSON (Full(user), entitlements), callContext)
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
      Catalogs(Core, notPSD2, notOBWG),
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
              x => fullBoxOrException(x ?~! UserNotFoundByUsername)
            } map { unboxFull(_) }
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
          } yield {
            (JSONFactory300.createUserJSON (Full(user), entitlements), callContext)
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
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagApi))


    lazy val getAdapter: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "adapter" :: Nil JsonGet _ => {
        _ =>
          for {
            _ <- Bank(bankId) ?~! BankNotFound
            ai: InboundAdapterInfoInternal <- Connector.connector.vend.getAdapterInfo() ?~ "Not implemented"
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



    val getBranchesIsPublic = Props.getBool("apiOptions.getBranchesIsPublic", true)

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
              x => fullBoxOrException(x ?~! BankNotFound)
            }
            branch <- Connector.connector.vend.getBranchFuture(bankId, branchId) map {
              val msg: String = s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and meta.license.name can not be empty"
              x => fullBoxOrException(x ?~! msg)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createBranchJsonV300(branch), callContext)
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
         |By default, 100 records are returned.
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
      Catalogs(Core, notPSD2, OBWG),
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
            _ <- Future { Bank(bankId) } map { x => fullBoxOrException(x ?~! BankNotFound) }
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
            (JSONFactory300.createBranchesJson(branches), callContext)
          }
        }
      }
    }

    val getAtmsIsPublic = Props.getBool("apiOptions.getAtmsIsPublic", true)

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
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet json => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotLoggedIn) {
              canGetAtm(getAtmsIsPublic, user)
            }
            _ <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ?~! BankNotFound)
            }
            atm <- Connector.connector.vend.getAtmFuture(bankId,atmId) map {
              x => fullBoxOrException(x ?~! AtmNotFoundByAtmId)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createAtmJsonV300(atm), callContext)
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
      Catalogs(Core, notPSD2, OBWG),
      List(apiTagATM)
    )
    lazy val getAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet json => {
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
            _ <- Future { Bank(bankId) } map { x => fullBoxOrException(x ?~! BankNotFound) }
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
            (JSONFactory300.createAtmsJsonV300(atms), callContext)
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
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagUser),
      Some(List(canGetAnyUser)))

    lazy val getUsers: OBPEndpoint = {
      case "users" :: Nil JsonGet _ => {
        cc =>
          val s = S
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
              hasEntitlement("", u.userId, ApiRole.canGetAnyUser)
            }
            users <- Users.users.vend.getAllUsersF()
          } yield {
            (JSONFactory300.createUserJSONs (users), callContext)
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




    lazy val getCustomersForUser : OBPEndpoint = {
      case "users" :: "current" :: "customers" :: Nil JsonGet _ => {
        cc => {
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            customers <- Connector.connector.vend.getCustomersByUserIdFuture(u.userId)(callContext) map {
              x => fullBoxOrException(x ?~! ConnectorEmptyResponse)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createCustomersJson(customers), callContext)
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
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(u.userId)
          } yield {
            (JSONFactory300.createUserJSON (user, entitlements), callContext)
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
      "Get private accounts at one bank.",
      s"""Returns the list of private accounts at BANK_ID that the user has access to.
         |For each account the API returns the ID and the available views.
         |
        |If you want to see more information on the Views, use the Account Detail call.
         |If you want less information about the account, use the /my accounts call
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
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet json => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bank <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ?~! BankNotFound)
            }
            availableAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            accounts <- Connector.connector.vend.getCoreBankAccountsFuture(availableAccounts, callContext) map {
              x => fullBoxOrException(x ?~! ConnectorEmptyResponse)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createCoreAccountsByCoreAccountsJSON(accounts), callContext)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getPrivateAccountIdsbyBankId,
      implementedInApiVersion,
      "getPrivateAccountIdsbyBankId",
      "GET",
      "/banks/BANK_ID/accounts/account_ids/private",
      "Get private accounts ids at one bank.",
      s"""Returns the list of private accounts ids at BANK_ID that the user has access to.
         |For each account the API returns the ID
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
      case "banks" :: BankId(bankId) :: "accounts" :: "account_ids" :: "private"::Nil JsonGet json => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bank <- Future { Bank(bankId) } map {
              x => fullBoxOrException(x ?~! BankNotFound)
            }
            bankAccountIds <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
          } yield {
            (JSONFactory300.createAccountsIdsByBankIdAccountIds(bankAccountIds), callContext)
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
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet json => {
        cc =>
          for {
            account <- Connector.connector.vend.checkBankAccountExists(bankId, accountId) ?~! BankAccountNotFound
            view <- View.fromUrl(viewId, account)
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
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet json => {
        cc =>
          for {
            account <- Connector.connector.vend.checkBankAccountExists(bankId, accountId) ?~! BankAccountNotFound
            view <- View.fromUrl(viewId, account)
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
                x => fullBoxOrException(x ?~! s"$InvalidJsonFormat The Json body should be the $CreateEntitlementRequestJSON ")
              } map { unboxFull(_) }
              _ <- Future { if (postedData.bank_id == "") Full() else Bank(BankId(postedData.bank_id)) } map {
                x => fullBoxOrException(x ?~! BankNotFound)
              }
              _ <- Helper.booleanToFuture(failMsg = IncorrectRoleName + postedData.role_name + ". Possible roles are " + ApiRole.availableRoles.sorted.mkString(", ")) {
                availableRoles.exists(_ == postedData.role_name)
              }
              _ <- Helper.booleanToFuture(failMsg = EntitlementRequestAlreadyExists) {
                EntitlementRequest.entitlementRequest.vend.getEntitlementRequest(postedData.bank_id, u.userId, postedData.role_name).isEmpty
              }
              addedEntitlementRequest <- EntitlementRequest.entitlementRequest.vend.addEntitlementRequestFuture(postedData.bank_id, u.userId, postedData.role_name) map {
                x => fullBoxOrException(x ?~! EntitlementRequestCannotBeAdded)
              } map { unboxFull(_) }
            } yield {
              (JSONFactory300.createEntitlementRequestJSON(addedEntitlementRequest), callContext)
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
              x => fullBoxOrException(x ?~! ConnectorEmptyResponse)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(getEntitlementRequests), callContext)
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
              x => fullBoxOrException(x ?~! ConnectorEmptyResponse)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(getEntitlementRequests), callContext)
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
              x => fullBoxOrException(x ?~! ConnectorEmptyResponse)
            } map { unboxFull(_) }
          } yield {
            (JSONFactory300.createEntitlementRequestsJSON(getEntitlementRequests), callContext)
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
              x => fullBoxOrException(x ?~! ConnectorEmptyResponse)
            } map { unboxFull(_) }
          } yield {
            (Full(deleteEntitlementRequest), callContext)
          }
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
          case "banks" :: BankId(bankId) :: "other_accounts" :: Nil JsonGet json => {
            cc =>
              for {
                _ <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
                account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
                view <- View.fromUrl(viewId, account)
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

object APIMethods300 {
}
