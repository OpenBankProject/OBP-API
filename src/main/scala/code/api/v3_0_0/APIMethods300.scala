package code.api.v3_0_0

import code.api.APIFailure
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v2_0_0.JSONFactory200
import code.api.v3_0_0.JSONFactory300._
import code.atms.Atms
import code.atms.Atms.AtmId
import code.bankconnectors.{Connector, OBPLimit, OBPOffset}
import code.branches.{Branches, InboundAdapterInfo}
import code.branches.Branches.BranchId
import code.entitlement.Entitlement
import code.model.dataAccess.AuthUser
import code.model.{BankId, ViewId, _}
import code.search.elasticsearchWarehouse
import code.users.Users
import code.util.Helper.booleanToBox
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req, S}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer





trait APIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here

  private def coreBankAccountListToJson(callerContext: CallerContext, codeContext: CodeContext, bankAccounts: List[BankAccount], user : Box[User]): JValue = {
    Extraction.decompose(coreBankAccountList(callerContext, codeContext, bankAccounts, user))
  }

  private def coreBankAccountList(callerContext: CallerContext, codeContext: CodeContext, bankAccounts: List[BankAccount], user : Box[User]): List[CoreAccountJsonV300] = {
    val accJson : List[CoreAccountJsonV300] = bankAccounts.map(account => {
      val views = account.permittedViews(user)
      val viewsAvailable : List[BasicViewJson] =
        views.map( v => {
          JSONFactory300.createBasicViewJSON(v)
        })

      val dataContext = DataContext(user, Some(account.bankId), Some(account.accountId), Empty, Empty, Empty)

      JSONFactory300.createCoreAccountJSON(account)
    })
    accJson
  }
  val Implementations3_0_0 = new Object() {

    val apiVersion: String = "3_0_0"
    
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
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
      viewsJsonV300,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))
  
    lazy val getViewsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~ UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            views <- account views u  // In other words: views = account.views(u) This calls BankingData.scala BankAccount.views
          } yield {
            val viewsJSON = createViewsJSON(views)
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
  
    lazy val createViewForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        user =>
          for {
            json <- tryo{json.extract[CreateViewJson]} ?~!InvalidJsonFormat
            u <- user ?~!UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- account createView (u, json)
          } yield {
            val viewJSON = JSONFactory300.createViewJSON(view)
            createdJsonResponse(Extraction.decompose(viewJSON))
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
  
    lazy val updateViewForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        user =>
          for {
            updateJson <- tryo{json.extract[UpdateViewJSON]} ?~!InvalidJsonFormat
            u <- user ?~!UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~!BankAccountNotFound
            updatedView <- account.updateView(u, viewId, updateJson)
          } yield {
            val viewJSON = JSONFactory300.createViewJSON(updatedView)
            successJsonResponse(Extraction.decompose(viewJSON))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      accountById,
      apiVersion,
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
        |OAuth authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
        |""",
      emptyObjectJson,
      moderatedAccountJSON,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagAccount ::  Nil)

    lazy val accountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet json => {
        user =>
          for {
            bank <- Bank(bankId) ?~ BankNotFound
            account <- BankAccount(bank.bankId, accountId) ?~ ErrorMessages.AccountNotFound
            view <- View.fromUrl(viewId, account) ?~! {ErrorMessages.ViewNotFound}
            availableViews <- Full(account.permittedViews(user))
            canUserAccessView <- tryo(availableViews.find(_ == viewId)) ?~! UserNoPermissionAccessView
            moderatedAccount <- account.moderatedBankAccount(view, user)
          } yield {
            val viewsAvailable = availableViews.map(JSONFactory300.createViewJSON).sortBy(_.short_name)
            val moderatedAccountJson = createBankAccountJSON(moderatedAccount, viewsAvailable)
            successJsonResponse(Extraction.decompose(moderatedAccountJson))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCoreAccountById,
      apiVersion,
      "getCoreAccountById",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
      "Get Account by Id (Core)",
      """Information returned about the account specified by ACCOUNT_ID:
        |
        |* Number
        |* Owners
        |* Type
        |* Balance
        |* IBAN
        |
        |This call returns the owner view and requires access to that view.
        |
        |
        |OAuth authentication is required""",
      emptyObjectJson,
      moderatedCoreAccountJSON,
      List(BankAccountNotFound,UnknownError),
      Catalogs(Core, PSD2, notOBWG),
      apiTagAccount ::  Nil)

    lazy val getCoreAccountById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get account by id (assume owner view requested)
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet json => {
        user =>
          for {
            account <- BankAccount(bankId, accountId) ?~ BankAccountNotFound
            availableviews <- Full(account.permittedViews(user))
            // Assume owner view was requested
            view <- View.fromUrl( ViewId("owner"), account)
            moderatedAccount <- account.moderatedBankAccount(view, user)
          } yield {
            val viewsAvailable = availableviews.map(JSONFactory300.createViewJSON)
            val moderatedAccountJson = createCoreBankAccountJSON(moderatedAccount, viewsAvailable)
            successJsonResponse(Extraction.decompose(moderatedAccountJson))
          }
      }
    }

    resourceDocs += ResourceDoc(
      corePrivateAccountsAllBanks,
      apiVersion,
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



    lazy val corePrivateAccountsAllBanks : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get private accounts for all banks
      case "my" :: "accounts" :: Nil JsonGet json => {
        user =>

          for {
            u <- user ?~! UserNotLoggedIn
          } yield {
            val availableAccounts = BankAccount.nonPublicAccounts(u)
            val coreBankAccountListJson = coreBankAccountListToJson(CallerContext(corePrivateAccountsAllBanks), codeContext, availableAccounts, Full(u))
            val response = successJsonResponse(coreBankAccountListJson)
            response
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCoreTransactionsForBankAccount,
      apiVersion,
      "getCoreTransactionsForBankAccount",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions",
      "Get Transactions for Account (Core)",
      """Returns transactions list (Core info) of the account specified by ACCOUNT_ID.
        |
        |Authentication is required.
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
      moderatedCoreAccountJSON,
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
      List(apiTagAccount, apiTagTransaction)
    )
  
    lazy val getCoreTransactionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet json => {
        user =>
          for {
            //Note: error handling and messages for getTransactionParams are in the sub method
            params <- getTransactionParams(json)
            u <- user ?~! UserNotLoggedIn
            bankAccount <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            // Assume owner view was requested
            view <- View.fromUrl(ViewId("owner"), bankAccount) ?~! ViewNotFound
            transactions <- bankAccount.getModeratedTransactions(user, view, params : _*)
          } yield {
            val json = createCoreTransactionsJSON(transactions)
            successJsonResponse(Extraction.decompose(json))
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
      transactionsJSON,
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
      List(apiTagAccount, apiTagTransaction)
    )
  
    lazy val getTransactionsForBankAccount: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet json => {
        user =>
          for {
            //Note: error handling and messages for getTransactionParams are in the sub method
            params <- getTransactionParams(json)
            u <- user ?~! UserNotLoggedIn
            bankAccount <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- View.fromUrl(viewId, bankAccount) ?~! ViewNotFound
            transactions <- bankAccount.getModeratedTransactions(user, view, params: _*)
          } yield {
            val json = createTransactionsJson(transactions)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }


    // TODO Put message into doc below if not enabled (but continue to show API Doc)
    resourceDocs += ResourceDoc(
      elasticSearchWarehouseV300,
      apiVersion,
      "elasticSearchWarehouseV300",
      "POST",
      "/search/warehouse",
      "Search Warehouse Data Via Elasticsearch",
      """
        |Search warehouse data via Elastic Search.
        |
        |Login is required.
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
      List())

    val esw = new elasticsearchWarehouse
    lazy val elasticSearchWarehouseV300: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "search" :: "warehouse" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- Entitlement.entitlement.vend.getEntitlement("", u.userId, ApiRole.CanSearchWarehouse.toString) ?~! {UserHasMissingRoles + CanSearchWarehouse}
          } yield {
            import net.liftweb.json._
            val uriPart = compact(render(json \ "es_uri_part"))
            val bodyPart = compact(render(json \ "es_body_part"))
            successJsonResponse(Extraction.decompose(esw.searchProxyV300(u.userId, uriPart, bodyPart)))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getUser,
      apiVersion,
      "getUser",
      "GET",
      "/users/email/EMAIL/terminator",
      "Get Users by Email Address",
      """Get users by email address
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJSONV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByEmail, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getUser: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "email" :: email :: "terminator" :: Nil JsonGet _ => {
        user =>
          for {
            l <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", l.userId, ApiRole.CanGetAnyUser), UserHasMissingRoles + CanGetAnyUser )
            users <- tryo{AuthUser.getResourceUsersByEmail(email)} ?~! {ErrorMessages.UserNotFoundByEmail}
          }
          yield {
            // Format the data as V2.0.0 json
            val json = JSONFactory200.createUserJSONs(users)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUserByUserId,
      apiVersion,
      "getUserByUserId",
      "GET",
      "/users/user_id/USER_ID",
      "Get User by USER_ID",
      """Get user by USER_ID
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJSONV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundById, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getUserByUserId: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "user_id" :: userId :: Nil JsonGet _ => {
        user =>
          for {
            l <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", l.userId, ApiRole.CanGetAnyUser), UserHasMissingRoles + CanGetAnyUser )
            user <- tryo{Users.users.vend.getUserByUserId(userId)} ?~! {ErrorMessages.UserNotFoundById}
          }
            yield {
              // Format the data as V2.0.0 json
              val json = JSONFactory200.createUserJSON(user)
              successJsonResponse(Extraction.decompose(json))
            }
      }
    }

    resourceDocs += ResourceDoc(
      getUserByUsername,
      apiVersion,
      "getUserByUsername",
      "GET",
      "/users/username/USERNAME",
      "Get User by USERNAME",
      """Get user by USERNAME
        |
        |Login is required.
        |CanGetAnyUser entitlement is required,
        |
      """.stripMargin,
      emptyObjectJson,
      usersJSONV200,
      List(UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByUsername, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getUserByUsername: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "users" :: "username" :: username :: Nil JsonGet _ => {
        user =>
          for {
            l <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", l.userId, ApiRole.CanGetAnyUser), UserHasMissingRoles + CanGetAnyUser )
            user <- tryo{Users.users.vend.getUserByUserName(username)} ?~! {ErrorMessages.UserNotFoundByUsername}
          }
            yield {
              // Format the data as V2.0.0 json
              val json = JSONFactory200.createUserJSON(user)
              successJsonResponse(Extraction.decompose(json))
            }
      }
    }


    resourceDocs += ResourceDoc(
      getAdapter,
      apiVersion,
      "getAdapter",
      "GET",
      "/banks/BANK_ID/adapter",
      "Get Info Of Adapter",
      """Get a basic Adapter info
        |
        |Login is required.
        |
      """.stripMargin,
      emptyObjectJson,
      usersJSONV200,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, notPSD2, notOBWG),
      List(apiTagPerson, apiTagUser))


    lazy val getAdapter: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "adapter" :: Nil JsonGet _ => {
        user =>
          for {
            // _ <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- Bank(bankId) ?~! BankNotFound
            ai: InboundAdapterInfo <- Connector.connector.vend.getAdapterInfo() ?~ "Not implemented"
          }
          yield {
            successJsonResponseFromCaseClass(ai)
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
      apiVersion,
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
      Nil
    )

    lazy val createBranch: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~!ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)?~! BankNotFound
            _ <- booleanToBox(
              hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true
              ||
              hasEntitlement("", u.userId, CanCreateBranchAtAnyBank) == true
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


    val createAtmEntitlementsRequiredForSpecificBank = CanCreateAtm ::  Nil
    val createAtmEntitlementsRequiredForAnyBank = CanCreateAtmAtAnyBank ::  Nil

    val createAtmEntitlementsRequiredText = UserHasMissingRoles + createAtmEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createAtmEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createAtm,
      apiVersion,
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
      Nil
    )



    lazy val createAtm: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "atms" ::  Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~!ErrorMessages.UserNotLoggedIn
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
      apiVersion,
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
            _ <- Bank(bankId) ?~! {BankNotFound}

            branch <- Box(Branches.branchesProvider.vend.getBranch(bankId, branchId)) ?~! s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and meta.license.name can not be empty"


//            branches <- { Branches.branchesProvider.vend.getBranches(bankId) match {
//              case Some(l) => Full(l)
//              case _ => Empty
//            }} ?~!  s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and eta.license.name can not be empty"
//            branch <- Box(branches.filter(_.branchId.value==branchId.value)) ?~!
//              s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and eta.license.name can not be empty"
          } yield {
            // Format the data as json
            val json = JSONFactory300.createBranchJsonV300(branch)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getBranches,
      apiVersion,
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
        |${authenticationRequiredMessage(!getBranchesIsPublic)}""",
      emptyObjectJson,
      branchesJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No branches available. License may not be set.",
        UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getBranches : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "branches" :: Nil JsonGet _ => {
        user => {
          for {
            _ <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            u <- if(getBranchesIsPublic)
              Box(Some(1))
            else
              user ?~! UserNotLoggedIn
            // Get branches from the active provider
          
          limit <- tryo(
              S.param("limit") match {
                case Full(l) if (l.toInt > 1000) => 1000
                case Full(l)                      => l.toInt
                case _                            => 100
              }
            ) ?~!  s"${InvalidNumber } limit:${S.param("limit").get }"
            // default0, start from page 0
            offset <- tryo(S.param("offset").getOrElse("0").toInt) ?~!
              s"${InvalidNumber } offset:${S.param("offset").get }"
          
          
            branches <- Box(Branches.branchesProvider.vend.getBranches(bankId,OBPLimit(limit), OBPOffset(offset))) ~> APIFailure("No branches available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory300.createBranchesJson(branches)

            // val x = print("\n getBranches json is: " + json)
            successJsonResponse(Extraction.decompose(json))
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
      atmJsonV300,
      List(UserNotLoggedIn, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getAtm: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet json => {
        user => {
          for {
          // Get atm from the active provider
            u <- if (getAtmsIsPublic)
              Box(Some(1))
            else
              user ?~! UserNotLoggedIn
            _ <- Bank(bankId) ?~! {BankNotFound}
            limit <- tryo(
              S.param("limit") match {
                case Full(l) if (l.toInt > 1000) => 1000
                case Full(l)                      => l.toInt
                case _                            => 50
              }
            ) ?~!  s"${InvalidNumber } limit:${S.param("limit").get }"
            // default0, start from page 0
            offset <- tryo(S.param("offset").getOrElse("0").toInt) ?~!
              s"${InvalidNumber } offset:${S.param("offset").get }"
          
            atms <- {Atms.atmsProvider.vend.getAtms(bankId,OBPLimit(limit), OBPOffset(offset)) match {
              case Some(l) => Full(l)
              case _ => Empty
            }} ?~!  {AtmNotFoundByAtmId}
            atm <- Box(atms.filter(_.atmId.value==atmId.value)) ?~!
              {AtmNotFoundByAtmId}
          } yield {
            // Format the data as json
            val json = JSONFactory300.createAtmJsonV300(atm)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getAtms,
      apiVersion,
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
         |${authenticationRequiredMessage(!getAtmsIsPublic)}""",
      emptyObjectJson,
      atmJsonV300,
      List(
        UserNotLoggedIn,
        BankNotFound,
        "No ATMs available. License may not be set.",
        UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      List(apiTagBank)
    )

    lazy val getAtms : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet json => {
        user => {
          for {
          // Get atms from the active provider

            u <- if(getAtmsIsPublic)
              Box(Some(1))
            else
              user ?~! UserNotLoggedIn
            _ <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            limit <- tryo(
              S.param("limit") match {
                case Full(l) if (l.toInt > 1000) => 1000
                case Full(l)                      => l.toInt
                case _                            => 50
              }
            ) ?~!  s"${InvalidNumber } limit:${S.param("limit").get }"
            // default0, start from page 0
            offset <- tryo(S.param("offset").getOrElse("0").toInt) ?~!
              s"${InvalidNumber } offset:${S.param("offset").get }"
            atms <- Box(Atms.atmsProvider.vend.getAtms(bankId, OBPLimit(limit), OBPOffset(offset))) ~> APIFailure("No ATMs available. License may not be set.", 204)
          } yield {
            // Format the data as json
            val json = JSONFactory300.createAtmsJsonV300(atms)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


  }
}

object APIMethods300 {
}
