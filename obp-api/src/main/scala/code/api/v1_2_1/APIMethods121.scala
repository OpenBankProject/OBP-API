package code.api.v1_2_1

import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.util.newstyle.ViewNewStyle
import code.bankconnectors._
import code.metadata.counterparties.Counterparties
import code.model.{BankAccountX, BankX, ModeratedTransactionMetadata, UserX, toBankAccountExtended, toBankExtended}
import code.util.Helper
import code.util.Helper.booleanToBox
import code.views.Views
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common._
import net.liftweb.http.JsonResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers._

import java.net.URL
import java.util.UUID.randomUUID
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait APIMethods121 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val apiMethods121GetTransactionsTTL = APIUtil.getPropsValue("api.cache.ttl.seconds.APIMethods121.getTransactions", "0").toInt * 1000 // Miliseconds

  // helper methods begin here

  private def privateBankAccountsListToJson(bankAccounts: List[BankAccount], privateViewsUserCanAccess: List[View]): JValue = {
    val accJson : List[AccountJSON] = bankAccounts.map( account => {
      val viewsAvailable : List[ViewJSONV121] =
        (privateViewsUserCanAccess
          .filter(v =>v.bankId==account.bankId && v.accountId ==account.accountId && v.isPrivate)//filter the view for this account.
          .map(JSONFactory.createViewJSON(_))
          .distinct) ++ 
        (privateViewsUserCanAccess
            .filter(v =>v.isSystem && v.isPrivate)//plus the system views.
            .map(JSONFactory.createViewJSON(_))
            .distinct)
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
      (account, callContext) <- BankAccountX(bankId, accountId, callContext) ?~! BankAccountNotFound
      view <- APIUtil.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), user, callContext)
      (moderatedTransaction, callContext) <- account.moderatedTransaction(transactionID, view, BankIdAccountId(bankId,accountId), user, callContext)
      metadata <- Box(moderatedTransaction.metadata) ?~ { s"$NoViewPermission can_see_transaction_metadata. Current ViewId($viewId)" }
    } yield metadata
  }
  private def moderatedTransactionMetadataFuture(bankId : BankId, accountId : AccountId, viewId : ViewId, transactionID : TransactionId, user : Box[User], callContext: Option[CallContext]): Future[ModeratedTransactionMetadata] = {
    for {
      (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
      view: View <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), user, callContext)
      (moderatedTransaction, callContext) <- account.moderatedTransactionFuture(transactionID, view, user, callContext) map {
        unboxFullOrFail(_, callContext, GetTransactionsException)
      }
      metadata <- Future(moderatedTransaction.metadata) map {
        unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_transaction_metadata. Current ViewId($viewId)")
      }
    } yield metadata
  }

  // helper methods end here

  val Implementations1_2_1 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiVersion = ApiVersion.v1_2_1 // was String "1_2_1"
    val apiVersionStatus : String = "STABLE"

    resourceDocs += ResourceDoc(
      root,
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
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil)

    lazy val root : OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
            (JSONFactory.getApiInfoJSON(apiVersion,apiVersionStatus), HttpCode.`200`(cc.callContext))
          }
      }
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
      EmptyBody,
      banksJSON,
      List(UnknownError),
      apiTagBank :: apiTagPsd2 :: apiTagOldStyle :: Nil)

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
          for((banks, callContext)<- Connector.connector.vend.getBanksLegacy(Some(cc)))
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
      EmptyBody,
      bankJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      apiTagBank :: apiTagPsd2 :: apiTagOldStyle :: Nil)


    lazy val bankById : OBPEndpoint = {
      //get bank by id
      case "banks" :: BankId(bankId) :: Nil JsonGet req => {
        cc =>
          def bankToJson(bank : Bank) : JValue = {
            val bankJSON = JSONFactory.createBankJSON(bank)
            Extraction.decompose(bankJSON)
          }
          for((bank, callContext)<- BankX(bankId, Some(cc)) ?~! BankNotFound)
          yield successJsonResponse(bankToJson(bank))
      }
    }


    resourceDocs += ResourceDoc(
      getPrivateAccountsAllBanks,
      apiVersion,
      "getPrivateAccountsAllBanks",
      "GET",
      "/accounts",
      "Get accounts at all banks (Private, inc views)",
      s"""Returns the list of accounts at that the user has access to at all banks.
         |For each account the API returns the account ID and the available views.
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UserNotLoggedIn, UnknownError),
      apiTagAccount :: apiTagPsd2 :: apiTagOldStyle :: Nil)

    //TODO double check with `lazy val privateAccountsAllBanks :`, they are the same now.
    lazy val getPrivateAccountsAllBanks : OBPEndpoint = {
      //get accounts for all banks (private + public)
      case "accounts" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            (privateViewsUserCanAccess, privateAccountAccess) <- Full(Views.views.vend.privateViewsUserCanAccess(u))
            availablePrivateAccounts <- Full(BankAccountX.privateAccounts(privateAccountAccess))
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
      "Get private accounts at all banks (Authenticated access)",
      """Returns the list of private accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is required.
        |
        |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UserNotLoggedIn, UnknownError),
      apiTagAccount :: apiTagPsd2 :: apiTagOldStyle :: Nil)

    lazy val privateAccountsAllBanks : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: "private" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            (privateViewsUserCanAccess, privateAccountAccess) <- Full(Views.views.vend.privateViewsUserCanAccess(u))
            privateAccounts <- Full(BankAccountX.privateAccounts(privateAccountAccess))
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
      "Get public accounts at all banks (Anonymous access)",
      """
        |Returns the list of private accounts the user has access to at all banks.
        |For each account the API returns the ID and the available views. 
        |Authentication via OAuth is required.
        |
        |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UnknownError),
      apiTagAccount :: apiTagOldStyle :: Nil)

    lazy val publicAccountsAllBanks : OBPEndpoint = {
      //get public accounts for all banks
      case "accounts" :: "public" :: Nil JsonGet req => {
        cc =>
          for{
            (publicViews, publicAccountAccess) <- Full(Views.views.vend.publicViews)
            publicAccounts <- Full(BankAccountX.publicAccounts(publicAccountAccess))
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
      "Get accounts at bank (Private)",
      s"""Returns the list of accounts at BANK_ID that the user has access to.
        |For each account the API returns the account ID and the available views.
        |
        |${userAuthenticationMessage(true)}
        |
      """,
      EmptyBody,
      accountJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      apiTagAccount :: apiTagOldStyle :: Nil)

    lazy val getPrivateAccountsAtOneBank : OBPEndpoint = {
      //get accounts for a single bank (private + public)
      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet req => {
        cc =>
          for{
            u <- cc.user ?~! ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- BankX(bankId, Some(cc)) ?~! BankNotFound
          } yield {
            val (privateViewsUserCanAccessAtOneBank, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)
            val availablePrivateAccounts = bank.privateAccounts(privateAccountAccess)
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
      "Get private accounts at one bank",
      s"""Returns the list of private accounts at BANK_ID that the user has access to.
        |For each account the API returns the ID and the available views.
        |
        |${userAuthenticationMessage(true)}
        |
        |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      List(apiTagAccount, apiTagPsd2, apiTagOldStyle))

    lazy val privateAccountsAtOneBank : OBPEndpoint = {
      //get private accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "private" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            (bank, callContext) <- BankX(bankId, Some(cc)) ?~! BankNotFound
          } yield {
            val (privateViewsUserCanAccessAtOneBank, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)
            val availablePrivateAccounts = bank.privateAccounts(privateAccountAccess)
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
      "Get public accounts at one bank (Anonymous access)",
      """Returns a list of the public accounts at BANK_ID. For each account the API returns the ID and the available views.
        |
        |Authentication via OAuth is not required.
        |
        |""".stripMargin,
      EmptyBody,
      accountJSON,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      apiTagAccountPublic :: apiTagAccount :: apiTagPublicData ::  apiTagOldStyle :: Nil)

    lazy val publicAccountsAtOneBank : OBPEndpoint = {
      //get public accounts for a single bank
      case "banks" :: BankId(bankId) :: "accounts" :: "public" :: Nil JsonGet req => {
        cc =>
          for {
            (bank, callContext) <- BankX(bankId, Some(cc)) ?~! BankNotFound
            (publicViewsForBank, publicAccountAccess) <- Full(Views.views.vend.publicViewsForBank(bank.bankId))
            publicAccounts<- Full(bank.publicAccounts(publicAccountAccess))
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
      "Get account by id",
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
         |${userAuthenticationMessage(false)}
         |
         |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
         |
         |""".stripMargin,
      EmptyBody,
      moderatedAccountJSON,
      List(UserNotLoggedIn, UnknownError, BankAccountNotFound),
      apiTagAccount ::  apiTagOldStyle :: Nil)

    lazy val accountById : OBPEndpoint = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            (account, callContext) <- BankAccountX(bankId, accountId, Some(cc)) ?~! BankAccountNotFound
            availableviews <- Full(Views.views.vend.privateViewsUserCanAccessForAccount(u, BankIdAccountId(account.bankId, account.accountId)))
            view <- APIUtil.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), Some(u), callContext)
            moderatedAccount <- account.moderatedBankAccount(view, BankIdAccountId(bankId, accountId), cc.user, callContext)
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
      "Update Account Label",
      s"""Update the label for the account. The label is how the account is known to the account owner e.g. 'My savings account'
         |
         |
         |${userAuthenticationMessage(true)}
         |
       """.stripMargin,
      updateAccountJSON,
      successMessage,
      List(InvalidJsonFormat, UserNotLoggedIn, UnknownError, BankAccountNotFound, "user does not have access to owner view on account"),
      List(apiTagAccount)
    )

    lazy val updateAccountLabel : OBPEndpoint = {
      //change account label
      // TODO Remove BANK_ID AND ACCOUNT_ID from the body? (duplicated in URL)
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[UpdateAccountJSON] }
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            permission <- NewStyle.function.permission(account.bankId, account.accountId, u, callContext)
            anyViewContainsCanUpdateBankAccountLabelPermission =  permission.views.map(_.allowed_actions.exists(_ ==  CAN_UPDATE_BANK_ACCOUNT_LABEL)).find(true == _).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_UPDATE_BANK_ACCOUNT_LABEL)}` permission on any your views",
              cc = callContext
            ) {
              anyViewContainsCanUpdateBankAccountLabelPermission
            }
            (success, callContext) <- 
              Connector.connector.vend.updateAccountLabel(bankId, accountId, json.label, callContext) map { i =>
              (unboxFullOrFail(i._1, i._2, 
                s"$UpdateBankAccountLabelError Current BankId is $bankId and Current AccountId is $accountId", 404), i._2)
            }
          } yield {
            (successMessage, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getViewsForBankAccount,
      apiVersion,
      "getViewsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account",
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
         |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      viewsJSONV121,
      List(UserNotLoggedIn, BankAccountNotFound, UnknownError, "user does not have owner access"),
      List(apiTagView, apiTagAccount, apiTagOldStyle))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            bankAccount <- BankAccountX(bankId, accountId) ?~! BankAccountNotFound
            permission <- Views.views.vend.permission(BankIdAccountId(bankAccount.bankId, bankAccount.accountId), u)
            anyViewContainsCanSeeAvailableViewsForBankAccountPermission =  permission.views.map(_.allowed_actions.exists(_ == CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT)).find(_.==(true)).getOrElse(false)
            _ <- Helper.booleanToBox(
              anyViewContainsCanSeeAvailableViewsForBankAccountPermission, 
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT)}` permission on any your views"
            )
            views <- Full(Views.views.vend.availableViewsForAccount(BankIdAccountId(bankAccount.bankId, bankAccount.accountId)))
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
      "Create View",
      s"""#Create a view on bank account
        |
        | ${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
        | The 'alias' field in the JSON can take one of three values:
        |
        | * _public_: to use the public alias if there is one specified for the other account.
        | * _private_: to use the private alias if there is one specified for the other account.
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
      List(apiTagAccount, apiTagView, apiTagOldStyle)
    )

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            createViewJsonV121 <- tryo{json.extract[CreateViewJsonV121]} ?~ InvalidJsonFormat
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _<- booleanToBox(isValidCustomViewName(createViewJsonV121.name), InvalidCustomViewFormat+s"Current view_name (${createViewJsonV121.name})")
            account <- BankAccountX(bankId, accountId) ?~! BankAccountNotFound
            createViewJson = CreateViewJson(
              createViewJsonV121.name,
              createViewJsonV121.description,
              metadata_view = "", //this only used from V300
              createViewJsonV121.is_public,
              createViewJsonV121.which_alias_to_use,
              createViewJsonV121.hide_metadata_if_alias_used,
              createViewJsonV121.allowed_actions
            )
            anyViewContainsCanCreateCustomViewPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_CREATE_CUSTOM_VIEW))).getOrElse(Nil).find(_.==(true)).getOrElse(false)
            _ <- booleanToBox(
              anyViewContainsCanCreateCustomViewPermission,
              s"${ErrorMessages.CreateCustomViewError} You need the `${(CAN_CREATE_CUSTOM_VIEW)}` permission on any your views"
            )
            view <- Views.views.vend.createCustomView(BankIdAccountId(bankId,accountId), createViewJson)?~ CreateCustomViewError
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
      "Update View",
      s"""Update an existing view on a bank account
        |
        |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
        |
        |The json sent is the same as during view creation (above), with one difference: the 'name' field
        |of a view is not editable (it is only set when a view is created)""",
      updateViewJsonV121,
      viewJSONV121,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      List(apiTagAccount, apiTagView, apiTagOldStyle)
    )
    
    //TODO. remove and replace it with V510.
    lazy val updateViewForBankAccount: OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId
      ) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            updateJsonV121 <- tryo{ json.extract[UpdateViewJsonV121] } ?~ InvalidJsonFormat
            account <- BankAccountX(bankId, accountId) ?~! BankAccountNotFound
            u <- cc.user ?~  UserNotLoggedIn
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _ <- booleanToBox(viewId.value.startsWith("_"), InvalidCustomViewFormat +s"Current view_id (${viewId.value})")
            view <- Views.views.vend.customView(viewId, BankIdAccountId(bankId, accountId)) ?~! ViewNotFound
            _ <- booleanToBox(!view.isSystem, SystemViewsCanNotBeModified)
            updateViewJson = UpdateViewJSON(
              description = updateJsonV121.description,
              metadata_view = view.metadataView, //this only used from V300, here just copy from currentView . 
              is_public = updateJsonV121.is_public,
              which_alias_to_use = updateJsonV121.which_alias_to_use,
              hide_metadata_if_alias_used = updateJsonV121.hide_metadata_if_alias_used,
              allowed_actions = updateJsonV121.allowed_actions
            )
            anyViewContainsCanUpdateCustomViewPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_CUSTOM_VIEW))).getOrElse(Nil).find(_.==(true)).getOrElse(false)
            _ <- booleanToBox(
              anyViewContainsCanUpdateCustomViewPermission,
              s"${ErrorMessages.CreateCustomViewError} You need the `${(CAN_UPDATE_CUSTOM_VIEW)}` permission on any your views"
            )
            updatedView <- Views.views.vend.updateCustomView(BankIdAccountId(bankId, accountId),viewId,  updateViewJson) ?~ CreateCustomViewError
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
      "Delete Custom View",
      "Deletes the custom view specified by VIEW_ID on the bank account specified by ACCOUNT_ID at bank BANK_ID",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      List(apiTagView, apiTagAccount)
    )
  
    lazy val deleteViewForBankAccount: OBPEndpoint = {
      //deletes a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId
      ) :: "views" :: ViewId(viewId) :: Nil JsonDelete req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            // custom views start with `_` eg _play, _work, and System views start with a letter, eg: owner
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat+s"Current view_name (${viewId.value})", cc=callContext) { viewId.value.startsWith("_") }
            _ <- ViewNewStyle.customView(viewId, BankIdAccountId(bankId, accountId), callContext)

            anyViewContainsCanDeleteCustomViewPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_DELETE_CUSTOM_VIEW))).getOrElse(Nil).find(_.==(true)).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_DELETE_CUSTOM_VIEW)}` permission on any your views",
              cc = callContext
            ) {
              anyViewContainsCanDeleteCustomViewPermission
            }

            deleted <- ViewNewStyle.removeCustomView(viewId, BankIdAccountId(bankId, accountId),callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getPermissionsForBankAccount,
      apiVersion,
      "getPermissionsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions",
      "Get access",
      s"""Returns the list of the permissions at BANK_ID for account ACCOUNT_ID, with each time a pair composed of the user and the views that he has access to.
        |
        |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      permissionsJSON,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagView, apiTagAccount, apiTagEntitlement, apiTagOldStyle)
    )
  
    lazy val getPermissionsForBankAccount: OBPEndpoint = {
      //get access
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: Nil JsonGet req => {
        cc =>
          for {
            u <- cc.user ?~  UserNotLoggedIn
            account <- BankAccountX(bankId, accountId) ?~! BankAccountNotFound
            anyViewContainsCanSeeViewsWithPermissionsForAllUsersPermission = Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
              .map(_.views.map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS))).getOrElse(Nil).find(_.==(true)).getOrElse(false)
            _ <- booleanToBox(
              anyViewContainsCanSeeViewsWithPermissionsForAllUsersPermission,
              s"${ErrorMessages.CreateCustomViewError} You need the `${(CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS)}` permission on any your views"
            )
            permissions = Views.views.vend.permissions(BankIdAccountId(bankId, accountId))
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
      "Get access for specific user",
      s"""Returns the list of the views at BANK_ID for account ACCOUNT_ID that a USER_ID at their provider PROVIDER_ID has access to.
        |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for USER_ID and PROVIDER_ID.
        |
        |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      viewsJSONV121,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have access to owner view on account"
    ),
      List(apiTagAccount, apiTagView, apiTagEntitlement, apiTagOldStyle)
    )
  
  
    lazy val getPermissionForUserForBankAccount: OBPEndpoint = {
      //get access for specific user
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: Nil JsonGet req => {
        cc =>
          for {
            loggedInUser <- cc.user ?~  UserNotLoggedIn
            account <- BankAccountX(bankId, accountId) ?~! BankAccountNotFound
            loggedInUserPermissionBox = Views.views.vend.permission(BankIdAccountId(bankId, accountId), loggedInUser)
            anyViewContainsCanSeeViewsWithPermissionsForOneUserPermission = loggedInUserPermissionBox.map(_.views.map(_.allowed_actions.exists(_ == CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER)))
              .getOrElse(Nil).find(_.==(true)).getOrElse(false)
            _ <- booleanToBox(
              anyViewContainsCanSeeViewsWithPermissionsForOneUserPermission,
              s"${ErrorMessages.CreateCustomViewError} You need the `${(CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER)}` permission on any your views"
            )
            userFromURL <- UserX.findByProviderId(provider, providerId) ?~! UserNotFoundByProviderAndProvideId
            permission <- Views.views.vend.permission(BankIdAccountId(bankId, accountId), userFromURL)
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
      "Grant User access to a list of views",
      s"""Grants the user identified by PROVIDER_ID at their provider PROVIDER access to a list of views at BANK_ID for account ACCOUNT_ID.
        |
        |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for PROVIDER_ID and PROVIDER.
        |
        |${userAuthenticationMessage(true)}
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
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val addPermissionForUserForBankAccountForMultipleViews : OBPEndpoint = {
      //add access for specific user to a list of views
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            failMsg = "wrong format JSON"
            viewIds <- NewStyle.function.tryons(failMsg, 400, callContext) { json.extract[ViewIdsJson] }
            (addedViews, callContext) <- ViewNewStyle.grantAccessToMultipleViews(
              account, u, 
              viewIds.views.map(viewIdString => BankIdAccountIdViewId(bankId, accountId,ViewId(viewIdString))), 
              provider, 
              providerId,
              callContext
            )
          } yield {
            (JSONFactory.createViewsJSON(addedViews), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addPermissionForUserForBankAccountForOneView,
      apiVersion,
      "addPermissionForUserForBankAccountForOneView",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views/VIEW_ID",
      "Grant User access to View",
      s"""Grants the User identified by PROVIDER_ID at PROVIDER access to the view VIEW_ID at BANK_ID for account ACCOUNT_ID.
          |
          |All url parameters must be [%-encoded](http://en.wikipedia.org/wiki/Percent-encoding), which is often especially relevant for PROVIDER and PROVIDER_ID.
          |
          |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
          |
          |Granting access to a public view will return an error message, as the user already has access.""",
      EmptyBody, // No Json body required
      viewJSONV121,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        UserLacksPermissionCanGrantAccessToViewForTargetAccount,
        "could not save the privilege",
        "user does not have access to owner view on account"
        ),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val addPermissionForUserForBankAccountForOneView : OBPEndpoint = {
      //add access for specific user to a specific view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: ViewId(viewId) :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            (addedView, callContext) <- ViewNewStyle.grantAccessToView(account, u, BankIdAccountIdViewId(bankId, accountId, viewId), provider, providerId, callContext)
          } yield {
            val viewJson = JSONFactory.createViewJSON(addedView)
            (viewJson, HttpCode.`201`(callContext))
          }
      }
    }


    val generalRevokeAccessToViewText : String =
      """
        |The User is identified by PROVIDER_ID at their PROVIDER.
        |
        |The Account is specified by BANK_ID and ACCOUNT_ID.
        |
        |The View is specified by VIEW_ID.
        |
        |
        |PROVIDER (may be a URL so) must be URL Encoded.
        |
        |PROVIDER_ID is normally equivalent to USERNAME. However, see Get User by ID or GET Current User for Provider information.
        |
        |Attempting to revoke access to a public view will return an error message.
        |
        |An Account Owner cannot revoke access to an Owner View unless at least one other User has Owner View access.
        |
      """.stripMargin


    resourceDocs += ResourceDoc(
      removePermissionForUserForBankAccountForOneView,
      apiVersion,
      "removePermissionForUserForBankAccountForOneView",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views/VIEW_ID",
      "Revoke access to one View",
      s"""Revokes access to a View on an Account for a certain User.
         |
         |$generalRevokeAccessToViewText
        |
        |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "could not save the privilege",
        "user does not have access to owner view on account",
        UnknownError
      ),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagEntitlement, apiTagOwnerRequired))

    lazy val removePermissionForUserForBankAccountForOneView : OBPEndpoint = {
      //delete access for specific user to one view
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: ViewId(viewId) :: Nil JsonDelete req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            _ <- ViewNewStyle.revokeAccessToView(account, u, BankIdAccountIdViewId(bankId, accountId, viewId), provider, providerId, callContext)
          } yield {
            (Full(""), HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      removePermissionForUserForBankAccountForAllViews,
      apiVersion,
      "removePermissionForUserForBankAccountForAllViews",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER/PROVIDER_ID/views",
      "Revoke access to all Views on Account",
      s""""Revokes access to all Views on an Account for a certain User.
         |
         |$generalRevokeAccessToViewText
         |
        |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have access to owner view on account"
        ),
      List(apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired))

    lazy val removePermissionForUserForBankAccountForAllViews : OBPEndpoint = {
      //delete access for specific user to all the views
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "permissions" :: provider :: providerId :: "views" :: Nil JsonDelete req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            _ <- NewStyle.function.revokeAllAccountAccess(account, u, provider, providerId, callContext)
          } yield {
            (Full(""), HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountsForBankAccount,
      apiVersion,
      "getOtherAccountsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts",
      "Get Other Accounts of one Account",
      s"""Returns data about all the other accounts that have shared at least one transaction with the ACCOUNT_ID at BANK_ID.
        |${userAuthenticationMessage(false)}
        |Authentication is required if the view VIEW_ID is not public.""",
      EmptyBody,
      otherAccountsJSON,
      List(
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagAccount, apiTagPsd2, apiTagOldStyle))

    lazy val getOtherAccountsForBankAccount : OBPEndpoint = {
      //get other accounts for one account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), cc.user, callContext)
            (otherBankAccounts, callContext) <- NewStyle.function.moderatedOtherBankAccounts(account, view, cc.user, callContext)
          } yield {
            (JSONFactory.createOtherBankAccountsJSON(otherBankAccounts), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountByIdForBankAccount,
      apiVersion,
      "getOtherAccountByIdForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
      "Get Other Account by Id",
      s"""Returns data about the Other Account that has shared at least one transaction with ACCOUNT_ID at BANK_ID.
         |${userAuthenticationMessage(false)}
         |Authentication is required if the view is not public.""",
      EmptyBody,
      otherAccountJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagCounterparty, apiTagAccount))

    lazy val getOtherAccountByIdForBankAccount : OBPEndpoint = {
      //get one other account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (u, callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), u, callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, u, callContext)
          } yield {
            val otherBankAccountJson = JSONFactory.createOtherBankAccount(otherBankAccount)
            (otherBankAccountJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getOtherAccountMetadata,
      apiVersion,
      "getOtherAccountMetadata",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata",
      "Get Other Account Metadata",
      """Get metadata of one other account.
        |Returns only the metadata about one other bank account (OTHER_ACCOUNT_ID) that had shared at least one transaction with ACCOUNT_ID at BANK_ID.
        |
        |Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      otherAccountMetadataJSON,
      List(UserNotLoggedIn, UnknownError, "the view does not allow metadata access"),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val getOtherAccountMetadata : OBPEndpoint = {
      //get metadata of one other account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
          } yield {
            val metadataJson = JSONFactory.createOtherAccountMetaDataJSON(otherBankAccount.metadata.get)
            (metadataJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCounterpartyPublicAlias,
      apiVersion,
      "getCounterpartyPublicAlias",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Get public alias of other bank account",
      s"""Returns the public alias of the other account OTHER_ACCOUNT_ID.
        |${userAuthenticationMessage(false)}
        |${userAuthenticationMessage(true)} if the view is not public.""",
      EmptyBody,
      aliasJSON,
      List(
        BankAccountNotFound,
        UnknownError,
        "the view does not allow metadata access",
        "the view does not allow public alias access"
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val getCounterpartyPublicAlias : OBPEndpoint = {
      //get public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a public alias", cc=callContext) {
              otherBankAccount.metadata.get.publicAlias.isDefined
            }
          } yield {
            val aliasJson = JSONFactory.createAliasJSON(otherBankAccount.metadata.get.publicAlias.get)
            (aliasJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addCounterpartyPublicAlias,
      apiVersion,
      "addCounterpartyPublicAlias",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Add public alias to other bank account",
      s"""Creates the public alias for the other account OTHER_ACCOUNT_ID.
         |
         |${userAuthenticationMessage(false)}
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyPublicAlias : OBPEndpoint = {
      //add public alias to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            } 
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a public alias", cc=callContext) {
              otherBankAccount.metadata.get.addPublicAlias.isDefined
            }
            aliasJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[AliasJSON]
            }
            (added, _) <- Future(Counterparties.counterparties.vend.addPublicAlias(other_account_id, aliasJson.alias)) map { i =>
              (unboxFullOrFail(i, callContext, "Alias cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("public alias added"), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyPublicAlias,
      apiVersion,
      "updateCounterpartyPublicAlias",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/public_alias",
      "Update public alias of other bank account",
      s"""Updates the public alias of the other account / counterparty OTHER_ACCOUNT_ID.
        |
        |${userAuthenticationMessage(false)}
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPublicAlias : OBPEndpoint = {
      //update public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating a public alias", cc=callContext) {
              otherBankAccount.metadata.get.addPublicAlias.isDefined
            }
            aliasJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[AliasJSON]
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addPublicAlias(other_account_id, aliasJson.alias)) map { i =>
              (unboxFullOrFail(i, callContext, "Alias cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("public alias updated"), HttpCode.`200`(callContext))
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
         |${userAuthenticationMessage(false)}
         |Authentication is required if the view is not public.""",
      EmptyBody,
      EmptyBody,
      List(
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting the public alias",
        "Alias cannot be deleted",
        UnknownError
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPublicAlias : OBPEndpoint = {
      //delete public alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "public_alias" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting a public alias", cc=callContext) {
              otherBankAccount.metadata.get.addPublicAlias.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.addPublicAlias(other_account_id, "")) map { i =>
              (unboxFullOrFail(i, callContext, "Alias cannot be deleted", 400), i)
            }
            if(deleted)
          } yield {
            ("", HttpCode.`204`(callContext))
          }
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
        |${userAuthenticationMessage(false)}
        |Authentication is required if the view is not public.""",
      EmptyBody,
      aliasJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow private alias access",
        UnknownError
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val getOtherAccountPrivateAlias : OBPEndpoint = {
      //get private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a private alias", cc=callContext) {
              otherBankAccount.metadata.get.privateAlias.isDefined
            }
          } yield {
            val aliasJson = JSONFactory.createAliasJSON(otherBankAccount.metadata.get.privateAlias.get)
            (aliasJson, HttpCode.`200`(callContext))
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
         |${userAuthenticationMessage(false)}
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addOtherAccountPrivateAlias : OBPEndpoint = {
      //add private alias to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a private alias", cc=callContext) {
              otherBankAccount.metadata.get.addPrivateAlias.isDefined
            }
            aliasJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[AliasJSON]
            }
            (added, _) <- Future(Counterparties.counterparties.vend.addPrivateAlias(other_account_id, aliasJson.alias)) map { i =>
              (unboxFullOrFail(i, callContext, "Alias cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("private alias added"), HttpCode.`201`(callContext))
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
        |${userAuthenticationMessage(false)}
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPrivateAlias : OBPEndpoint = {
      //update private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating a private alias", cc=callContext) {
              otherBankAccount.metadata.get.addPrivateAlias.isDefined
            }
            aliasJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[AliasJSON]
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addPrivateAlias(other_account_id, aliasJson.alias)) map { i =>
              (unboxFullOrFail(i, callContext, "Alias cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("private alias updated"), HttpCode.`200`(callContext))
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
        |${userAuthenticationMessage(false)}
        |Authentication is required if the view is not public.""",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting the private alias",
        "Alias cannot be deleted",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPrivateAlias : OBPEndpoint = {
      //delete private alias of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "private_alias" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting a private alias", cc=callContext) {
              otherBankAccount.metadata.get.addPrivateAlias.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.addPrivateAlias(other_account_id, "")) map { i =>
              (unboxFullOrFail(i, callContext, "Alias cannot be deleted", 400), i)
            }
            if(deleted)
          } yield {
            ("", HttpCode.`204`(callContext))
          }
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
      "Add a description of the counter party from the perpestive of the account e.g. My dentist",
      moreInfoJSON,
      successMessage,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        InvalidJsonFormat,
        NoViewPermission,
        "the view " + viewIdSwagger + "does not allow adding more info",
        "More Info cannot be added",
        UnknownError
      ),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyMoreInfo : OBPEndpoint = {
      //add more info to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding more info", cc=callContext) {
              otherBankAccount.metadata.get.addMoreInfo.isDefined
            }
            moreInfoJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[MoreInfoJSON]
            }
            (added, _) <- Future(Counterparties.counterparties.vend.addMoreInfo(other_account_id, moreInfoJson.more_info)) map { i =>
              (unboxFullOrFail(i, callContext, "More Info cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("more info added"), HttpCode.`201`(callContext))
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
      "Update the more info description of the counter party from the perpestive of the account e.g. My dentist",
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyMoreInfo : OBPEndpoint = {
      //update more info of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating more info", cc=callContext) {
              otherBankAccount.metadata.get.addMoreInfo.isDefined
            }
            moreInfoJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[MoreInfoJSON]
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addMoreInfo(other_account_id, moreInfoJson.more_info)) map { i =>
              (unboxFullOrFail(i, callContext, "More Info cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("more info updated"), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyMoreInfo,
      apiVersion,
      "deleteCounterpartyMoreInfo",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/more_info",
      "Delete more info of other bank account",
      "",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting more info",
        "More Info cannot be deleted",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyMoreInfo : OBPEndpoint = {
      //delete more info of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "more_info" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting more info", cc=callContext) {
              otherBankAccount.metadata.get.addMoreInfo.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.addMoreInfo(other_account_id, "")) map { i =>
              (unboxFullOrFail(i, callContext, "More Info cannot be deleted", 400), i)
            }
            if(deleted)
          } yield {
            ("", HttpCode.`204`(callContext))
          }
      }
    }

    //TODO: get url of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyUrl,
      apiVersion,
      "addCounterpartyUrl",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Add url to other bank account",
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))


    lazy val addCounterpartyUrl : OBPEndpoint = {
      //add url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a url", cc=callContext) {
              otherBankAccount.metadata.get.addURL.isDefined
            }
            urlJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[UrlJSON]
            }
            (added, _) <- Future(Counterparties.counterparties.vend.addURL(other_account_id, urlJson.URL)) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("url added"), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCounterpartyUrl,
      apiVersion,
      "updateCounterpartyUrl",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Update url of other bank account",
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyUrl : OBPEndpoint = {
      //update url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating a url", cc=callContext) {
              otherBankAccount.metadata.get.addURL.isDefined
            }
            urlJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[UrlJSON]
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addURL(other_account_id, urlJson.URL)) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("url updated"), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyUrl,
      apiVersion,
      "deleteCounterpartyUrl",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/url",
      "Delete url of other bank account",
      "",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting a url",
        "URL cannot be deleted",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyUrl : OBPEndpoint = {
      //delete url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "url" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting a url", cc=callContext) {
              otherBankAccount.metadata.get.addURL.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.addURL(other_account_id, "")) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be deleted", 400), i)
            }
            if(deleted)
          } yield {
            ("", HttpCode.`204`(callContext))
          }
      }
    }

    //TODO: get image url of counterparty?

    resourceDocs += ResourceDoc(
      addCounterpartyImageUrl,
      apiVersion,
      "addCounterpartyImageUrl",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/image_url",
      "Add image url to other bank account",
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyImageUrl : OBPEndpoint = {
      //add image url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding an image url", cc=callContext) {
              otherBankAccount.metadata.get.addImageURL.isDefined
            }
            imageUrlJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[ImageUrlJSON]
            }
            (added, _) <- Future(Counterparties.counterparties.vend.addImageURL(other_account_id, imageUrlJson.image_URL)) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("image url added"), HttpCode.`201`(callContext))
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyImageUrl : OBPEndpoint = {
      //update image url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating an image url", cc=callContext) {
              otherBankAccount.metadata.get.addImageURL.isDefined
            }
            imageUrlJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[ImageUrlJSON]
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addImageURL(other_account_id, imageUrlJson.image_URL)) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("image url updated"), HttpCode.`200`(callContext))
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
      "Delete image url of other bank account",
      EmptyBody,
      EmptyBody,
      List(UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty)) // Tag general then specific for consistent sorting

    lazy val deleteCounterpartyImageUrl : OBPEndpoint = {
      //delete image url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "image_url" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting an image url", cc=callContext) {
              otherBankAccount.metadata.get.addImageURL.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.addImageURL(other_account_id, "")) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be deleted", 400), i)
            }
            if(deleted)
          } yield {
            ("", HttpCode.`204`(callContext))
          }
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
      "Add open corporates url to other bank account",
      openCorporateUrlJSON,
      successMessage,
      List(
        BankAccountNotFound,
        InvalidJsonFormat,
        "the view does not allow metadata access",
        "the view does not allow adding an open corporate url",
        "URL cannot be added",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyOpenCorporatesUrl : OBPEndpoint = {
      //add open corporate url to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding an open corporate url", cc=callContext) {
              otherBankAccount.metadata.get.addOpenCorporatesURL.isDefined
            }
            openCorpUrl <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[OpenCorporateUrlJSON]
            }
            (added, _) <- Future(Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, openCorpUrl.open_corporates_URL)) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("open corporate url added"), HttpCode.`201`(callContext))
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
      "Update open corporate url of other bank account",
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyOpenCorporatesUrl : OBPEndpoint = {
      //update open corporate url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating an open corporate url", cc=callContext) {
              otherBankAccount.metadata.get.addOpenCorporatesURL.isDefined
            }
            openCorpUrl <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[OpenCorporateUrlJSON]
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, openCorpUrl.open_corporates_URL)) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("open corporate url updated"), HttpCode.`200`(callContext))
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
      "Delete open corporate url of other bank account",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "the view does not allow deleting an open corporate url",
        "URL cannot be deleted",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyOpenCorporatesUrl : OBPEndpoint = {
      //delete open corporate url of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "open_corporates_url" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting an open corporate url", cc=callContext) {
              otherBankAccount.metadata.get.addOpenCorporatesURL.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.addOpenCorporatesURL(other_account_id, "")) map { i =>
              (unboxFullOrFail(i, callContext, "URL cannot be deleted", 400), i)
            }
            if(deleted)
          } yield {
            ("", HttpCode.`204`(callContext))
          }
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyCorporateLocation : OBPEndpoint = {
      //add corporate location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a corporate location", cc=callContext) {
              otherBankAccount.metadata.get.addCorporateLocation.isDefined
            }
            corpLocationJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[CorporateLocationJSON]
            }
            _ <- Helper.booleanToFuture(failMsg = "Coordinates not possible", 400, callContext) {
              checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude).isDefined
            }
            (added, _) <- Future(
              Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude)
            ) map { i =>
              (unboxFullOrFail(i, callContext, "Corporate Location cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("corporate location added"), HttpCode.`201`(callContext))
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyCorporateLocation : OBPEndpoint = {
      //update corporate location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating a corporate location", cc=callContext) {
              otherBankAccount.metadata.get.addCorporateLocation.isDefined
            }
            corpLocationJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[CorporateLocationJSON]
            }
            _ <- Helper.booleanToFuture(failMsg = "Coordinates not possible", 400, callContext) {
              checkIfLocationPossible(corpLocationJson.corporate_location.latitude, corpLocationJson.corporate_location.longitude).isDefined
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addCorporateLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), corpLocationJson.corporate_location.longitude, corpLocationJson.corporate_location.latitude)) map { i =>
              (unboxFullOrFail(i, callContext, "Corporate Location cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("corporate location updated"), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyCorporateLocation,
      apiVersion,
      "deleteCounterpartyCorporateLocation",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/corporate_location",
      "Delete Counterparty Corporate Location",
      "Delete corporate location of other bank account. Delete the geolocation of the counterparty's registered address",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        "the view does not allow metadata access",
        "Corporate Location cannot be deleted",
        "Delete not completed",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyCorporateLocation : OBPEndpoint = {
      //delete corporate location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "corporate_location" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting a Corporate Location", cc=callContext) {
              otherBankAccount.metadata.get.deleteCorporateLocation.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.deleteCorporateLocation(other_account_id)) map { i =>
              (unboxFullOrFail(i, callContext, "Corporate Location cannot be deleted", 400), i)
            }
            _ <- Helper.booleanToFuture(failMsg = "Delete not completed", cc=callContext) {
              deleted
            }
          } yield {
            ("", HttpCode.`204`(callContext))
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
      "Add physical location to other bank account",
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val addCounterpartyPhysicalLocation : OBPEndpoint = {
      //add physical location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts" :: other_account_id :: "metadata" :: "physical_location" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow adding a physical location", cc=callContext) {
              otherBankAccount.metadata.get.addPhysicalLocation.isDefined
            }
            physicalLocationJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[PhysicalLocationJSON]
            }
            _ <- Helper.booleanToFuture(failMsg = "Coordinates not possible", 400, callContext) {
              checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude).isDefined
            }
            (added, _) <- Future(
              Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude)
            ) map { i =>
              (unboxFullOrFail(i, callContext, "Physical Location cannot be added", 400), i)
            }
            if(added)
          } yield {
            (SuccessMessage("physical location added"), HttpCode.`201`(callContext))
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
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val updateCounterpartyPhysicalLocation : OBPEndpoint = {
      //update physical location to other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "physical_location" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow updating a physical location", cc=callContext) {
              otherBankAccount.metadata.get.addPhysicalLocation.isDefined
            }
            physicalLocationJson <- NewStyle.function.tryons(failMsg = InvalidJsonFormat, 400, callContext) {
              json.extract[PhysicalLocationJSON]
            }
            _ <- Helper.booleanToFuture(failMsg = "Coordinates not possible", 400, callContext) {
              checkIfLocationPossible(physicalLocationJson.physical_location.latitude, physicalLocationJson.physical_location.longitude).isDefined
            }
            (updated, _) <- Future(Counterparties.counterparties.vend.addPhysicalLocation(other_account_id, u.userPrimaryKey, (now:TimeSpan), physicalLocationJson.physical_location.longitude, physicalLocationJson.physical_location.latitude)) map { i =>
              (unboxFullOrFail(i, callContext, "Physical Location cannot be updated", 400), i)
            }
            if(updated)
          } yield {
            (SuccessMessage("physical location updated"), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCounterpartyPhysicalLocation,
      apiVersion,
      "deleteCounterpartyPhysicalLocation",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID/metadata/physical_location",
      "Delete Counterparty Physical Location",
      "Delete physical location of other bank account",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        "Physical Location cannot be deleted",
        "Delete not completed",
        UnknownError),
      List(apiTagCounterpartyMetaData, apiTagCounterparty))

    lazy val deleteCounterpartyPhysicalLocation : OBPEndpoint = {
      //delete physical location of other bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "other_accounts":: other_account_id :: "metadata" :: "physical_location" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (otherBankAccount, callContext) <- NewStyle.function.moderatedOtherBankAccount(account, other_account_id, view, Full(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"$NoViewPermission can_see_other_account_metadata. Current ViewId($viewId)", cc=callContext) {
              otherBankAccount.metadata.isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = "the view " + viewId + "does not allow deleting a Physical Location", cc=callContext) {
              otherBankAccount.metadata.get.deletePhysicalLocation.isDefined
            }
            (deleted, _) <- Future(Counterparties.counterparties.vend.deletePhysicalLocation(other_account_id)) map { i =>
              (unboxFullOrFail(i, callContext, "Physical Location cannot be deleted", 400), i)
            }
            _ <- Helper.booleanToFuture(failMsg = s"Delete not completed", cc=callContext) {
              deleted
            }
          } yield {
            ("", HttpCode.`204`(callContext))
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
      s"""Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
         |
         |Authentication via OAuth is required if the view is not public.
         |
         |${urlParametersDocument(true, true)}
         |
         |""",
      EmptyBody,
      transactionsJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagAccount, apiTagPsd2, apiTagOldStyle))
  
  
  
  
    private def getTransactionsForBankAccountCached(
      paramsBox:  Box[List[OBPQueryParam]],
      user: Box[User],
      accountId: AccountId,
      bankId: BankId,
      viewId : ViewId
    ): Box[JsonResponse] = {
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value field with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(apiMethods121GetTransactionsTTL millisecond) {
          for {
            params <- paramsBox
            bankAccount <- BankAccountX(bankId, accountId)
            (bank, callContext) <- BankX(bankId, None) ?~! BankNotFound
            view <- APIUtil.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), user, None)
            (transactions, callContext) <- bankAccount.getModeratedTransactions(bank, user, view, BankIdAccountId(bankId, accountId), None, params )
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
      "Get Transaction by Id",
      s"""Returns one transaction specified by TRANSACTION_ID of the account ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
         |
         |${userAuthenticationMessage(false)}
         |Authentication is required if the view is not public.
         |
         |
         |""",
      EmptyBody,
      transactionJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagPsd2, apiTagOldStyle))

    lazy val getTransactionByIdForBankAccount : OBPEndpoint = {
      //get transaction by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet req => {
        cc =>
          for {
            (account, callContext) <- BankAccountX(bankId, accountId, Some(cc)) ?~! BankAccountNotFound
            view <- APIUtil.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), cc.user, None)
            (moderatedTransaction, callContext) <- account.moderatedTransaction(transactionId, view, BankIdAccountId(bankId,accountId), cc.user, Some(cc))
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
      "Get a Transaction Narrative",
      """Returns the account owner description of the transaction [moderated](#1_2_1-getViewsForBankAccount) by the view.
         |
         |Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      transactionNarrativeJSON,
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getTransactionNarrative : OBPEndpoint = {
      //get narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            narrative <- Future(metadata.ownerComment) map {
              unboxFullOrFail(_, cc.callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            val narrativeJson = JSONFactory.createTransactionNarrativeJSON(narrative)
            (narrativeJson, HttpCode.`200`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addTransactionNarrative,
      apiVersion,
      "addTransactionNarrative",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Add a Transaction Narrative",
      s"""Creates a description of the transaction TRANSACTION_ID.
         |
         |Note: Unlike other items of metadata, there is only one "narrative" per transaction accross all views.
         |If you set narrative via a view e.g. view-x it will be seen via view-y (as long as view-y has permission to see the narrative).
         |
         |${userAuthenticationMessage(false)}
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
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addTransactionNarrative : OBPEndpoint = {
      //add narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            narrativeJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[TransactionNarrativeJSON] }
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            addNarrative <- Future(metadata.addOwnerComment) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_add_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            addNarrative(narrativeJson.narrative)
            val successJson = SuccessMessage("narrative added")
            (successJson, HttpCode.`201`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateTransactionNarrative,
      apiVersion,
      "updateTransactionNarrative",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Update a Transaction Narrative",
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
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val updateTransactionNarrative : OBPEndpoint = {
      //update narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            narrativeJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[TransactionNarrativeJSON] }
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            addNarrative <- Future(metadata.addOwnerComment) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_add_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            addNarrative(narrativeJson.narrative)
            val successJson = SuccessMessage("narrative updated")
            (successJson, HttpCode.`200`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteTransactionNarrative,
      apiVersion,
      "deleteTransactionNarrative",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative",
      "Delete a Transaction Narrative",
      """Deletes the description of the transaction TRANSACTION_ID.
         |
         |Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteTransactionNarrative : OBPEndpoint = {
      //delete narrative
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "narrative" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            addNarrative <- Future(metadata.addOwnerComment) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            addNarrative("")
            val successJson = SuccessMessage("")
            (successJson, HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCommentsForViewOnTransaction,
      apiVersion,
      "getCommentsForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments",
      "Get Transaction Comments",
      """Returns the transaction TRANSACTION_ID comments made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
         |
         |Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      transactionCommentsJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getCommentsForViewOnTransaction : OBPEndpoint = {
      //get comments
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            comments <- Future(metadata.comments) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            val commentsJson = JSONFactory.createTransactionCommentsJSON(comments)
            (commentsJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addCommentForViewOnTransaction,
      apiVersion,
      "addCommentForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments",
      "Add a Transaction Comment",
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
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addCommentForViewOnTransaction : OBPEndpoint = {
      //add comment
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            commentJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[PostTransactionCommentJSON] }
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            addCommentFunc <- Future(metadata.addComment) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
            postedComment <- Future(addCommentFunc(user.userPrimaryKey, viewId, commentJson.value, now)) map {
              unboxFullOrFail(_, callContext, s"Cannot add the comment ${commentJson.value}")
            }
          } yield {
            val successJson = JSONFactory.createTransactionCommentJSON(postedComment)
            (successJson, HttpCode.`201`(callContext))
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
      "Delete a Transaction Comment",
      """Delete the comment COMMENT_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
         |
         |Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the comment.""",
      EmptyBody,
      EmptyBody,
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UserNotLoggedIn,
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteCommentForViewOnTransaction : OBPEndpoint = {
      //delete comment
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "comments":: commentId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(user), callContext)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            delete <- Future(metadata.deleteComment(commentId, Full(user), account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "")
            }
          } yield {
            val successJson = SuccessMessage("")
            (successJson, HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getTagsForViewOnTransaction,
      apiVersion,
      "getTagsForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags",
      "Get Transaction Tags",
      """Returns the transaction TRANSACTION_ID tags made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
         Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      transactionTagJSON,
      List(
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError
      ),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getTagsForViewOnTransaction : OBPEndpoint = {
      //get tags
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            tags <- Future(metadata.tags) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            val tagsJson = JSONFactory.createTransactionTagsJSON(tags)
            (tagsJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addTagForViewOnTransaction,
      apiVersion,
      "addTagForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags",
      "Add a Transaction Tag",
      s"""Posts a tag about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addTagForViewOnTransaction : OBPEndpoint = {
      //add a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            tagJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[PostTransactionTagJSON] }
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            addTagFunc <- Future(metadata.addTag) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
            postedTag <- Future(addTagFunc(user.userPrimaryKey, viewId, tagJson.value, now)) map {
              unboxFullOrFail(_, callContext, s"Cannot add the tag ${tagJson.value}")
            }
          } yield {
            val successJson = JSONFactory.createTransactionTagJSON(postedTag)
            (successJson, HttpCode.`201`(callContext))
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
      "Delete a Transaction Tag",
      """Deletes the tag TAG_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
        |Authentication via OAuth is required. The user must either have owner privileges for this account, 
        |or must be the user that posted the tag.
        |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(NoViewPermission,
           ViewNotFound,
           UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteTagForViewOnTransaction : OBPEndpoint = {
      //delete a tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "tags" :: tagId :: Nil JsonDelete _ => {

        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(user), callContext)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            delete <- Future(metadata.deleteTag(tagId, Full(user), bankAccount, view, callContext)) map {
              unboxFullOrFail(_, callContext, "")
            }
          } yield {
            val successJson = SuccessMessage("")
            (successJson, HttpCode.`204`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getImagesForViewOnTransaction,
      apiVersion,
      "getImagesForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images",
      "Get Transaction Images",
      """Returns the transaction TRANSACTION_ID images made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
         Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      transactionImagesJSON,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        ViewNotFound,
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getImagesForViewOnTransaction : OBPEndpoint = {
      //get images
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            images <- Future(metadata.images) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            val imagesJson = JSONFactory.createTransactionImagesJSON(images)
            (imagesJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addImageForViewOnTransaction,
      apiVersion,
      "addImageForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images",
      "Add a Transaction Image",
      s"""Posts an image about a transaction TRANSACTION_ID on a [view](#1_2_1-getViewsForBankAccount) VIEW_ID.
         |
         |${userAuthenticationMessage(true) }
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
      List(apiTagTransactionMetaData, apiTagTransaction)
    )

    lazy val addImageForViewOnTransaction : OBPEndpoint = {
      //add an image
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            imageJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[PostTransactionImageJSON] }
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(u), callContext)
            addImageFunc <- Future(metadata.addImage) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
            url <- NewStyle.function.tryons(s"$InvalidUrl Could not parse url string as a valid URL", 400, callContext) { new URL(imageJson.URL) }
            postedImage <- Future(addImageFunc(u.userPrimaryKey, viewId, imageJson.label, now, url.toString)) map {
              unboxFullOrFail(_, callContext, s"Cannot add the tag ${imageJson.label}")
            }
          } yield {
            val successJson = JSONFactory.createTransactionImageJSON(postedImage)
            (successJson, HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteImageForViewOnTransaction,
      apiVersion,
      "deleteImageForViewOnTransaction",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images/IMAGE_ID",
      "Delete a Transaction Image",
      """Deletes the image IMAGE_ID about the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
         |
         |Authentication via OAuth is required. The user must either have owner privileges for this account, or must be the user that posted the image.""",
      EmptyBody,
      EmptyBody,
      List(
        BankAccountNotFound,
        NoViewPermission,
        UserNotLoggedIn,
        "You must be able to see images in order to delete them",
        "Image not found for this transaction",
        "Deleting images not permitted for this view",
        "Deleting images not permitted for the current user",
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteImageForViewOnTransaction : OBPEndpoint = {
      //delete an image
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "images" :: imageId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(user), callContext)
            (account, _) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            delete <- Future(metadata.deleteImage(imageId, Full(user), account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "")
            }
          } yield {
            val successJson = SuccessMessage("")
            (successJson, HttpCode.`204`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getWhereTagForViewOnTransaction,
      apiVersion,
      "getWhereTagForViewOnTransaction",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Get a Transaction where Tag",
      """Returns the "where" Geo tag added to the transaction TRANSACTION_ID made on a [view](#1_2_1-getViewsForBankAccount) (VIEW_ID).
        |It represents the location where the transaction has been initiated.
        |
        |Authentication via OAuth is required if the view is not public.""",
      EmptyBody,
      transactionWhereJSON,
      List(BankAccountNotFound,
           NoViewPermission,
           ViewNotFound,
           UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val getWhereTagForViewOnTransaction : OBPEndpoint = {
      //get where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            where <- Future(metadata.whereTag) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_see_owner_comment. Current ViewId($viewId)")
            }
          } yield {
            val json = JSONFactory.createLocationJSON(where)
            val whereJson = TransactionWhereJSON(json)
            (whereJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addWhereTagForViewOnTransaction,
      apiVersion,
      "addWhereTagForViewOnTransaction",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Add a Transaction where Tag",
      s"""Creates a "where" Geo tag on a transaction TRANSACTION_ID in a [view](#1_2_1-getViewsForBankAccount).
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val addWhereTagForViewOnTransaction : OBPEndpoint = {
      //add where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            addWhereTag <- Future(metadata.addWhereTag) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_add_where_tag. Current ViewId($viewId)")
            }
            whereJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[PostTransactionWhereJSON] }
            correctCoordinates <- Future(checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)) map {
              unboxFullOrFail(_, callContext, "Coordinates not possible")
            }
            if(addWhereTag(user.userPrimaryKey, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
          } yield {
            val successJson = SuccessMessage("where tag added")
            (successJson, HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateWhereTagForViewOnTransaction,
      apiVersion,
      "updateWhereTagForViewOnTransaction",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Update a Transaction where Tag",
      s"""Updates the "where" Geo tag on a transaction TRANSACTION_ID in a [view](#1_2_1-getViewsForBankAccount).
         |
         |${userAuthenticationMessage(true)}
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
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val updateWhereTagForViewOnTransaction : OBPEndpoint = {
      //update where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, Full(user), callContext)
            addWhereTag <- Future(metadata.addWhereTag) map {
              unboxFullOrFail(_, callContext, s"$NoViewPermission can_add_where_tag. Current ViewId($viewId)")
            }
            whereJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { json.extract[PostTransactionWhereJSON] }
            correctCoordinates <- Future(checkIfLocationPossible(whereJson.where.latitude, whereJson.where.longitude)) map {
              unboxFullOrFail(_, callContext, "Coordinates not possible")
            }
            if(addWhereTag(user.userPrimaryKey, viewId, now, whereJson.where.longitude, whereJson.where.latitude))
          } yield {
            val successJson = SuccessMessage("where tag updated")
            (successJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteWhereTagForViewOnTransaction,
      apiVersion,
      "deleteWhereTagForViewOnTransaction",
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where",
      "Delete a Transaction Tag",
      s"""Deletes the where tag of the transaction TRANSACTION_ID made on [view](#1_2_1-getViewsForBankAccount).
         |
        |${userAuthenticationMessage(true)}
        |
        |The user must either have owner privileges for this account, or must be the user that posted the geo tag.""",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        NoViewPermission,
        UserNotLoggedIn,
        ViewNotFound,
        "there is no tag to delete",
        "Delete not completed",
        UnknownError),
      List(apiTagTransactionMetaData, apiTagTransaction))

    lazy val deleteWhereTagForViewOnTransaction : OBPEndpoint = {
      //delete where tag
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "metadata" :: "where" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), user, callContext)
            metadata <- moderatedTransactionMetadataFuture(bankId, accountId, viewId, transactionId, user, callContext)
            delete <- Future(metadata.deleteWhereTag(viewId, user, account, view, callContext)) map {
              unboxFullOrFail(_, callContext, "Delete not completed")
            }
          } yield {
            val successJson = SuccessMessage("")
            (successJson, HttpCode.`204`(callContext))
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
      EmptyBody,
      otherAccountJSON,
      List(BankAccountNotFound, UnknownError),
      List(apiTagTransaction, apiTagCounterparty))

    lazy val getOtherAccountForTransaction : OBPEndpoint = {
      //get other account of a transaction
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions":: TransactionId(transactionId) :: "other_account" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            (moderatedTransaction, callContext) <- account.moderatedTransactionFuture(transactionId, view, Full(u), callContext) map {
              unboxFullOrFail(_, callContext, GetTransactionsException)
            }
            _ <- NewStyle.function.tryons(GetTransactionsException, 400, callContext) {
              moderatedTransaction.otherBankAccount.isDefined
            }
          } yield {
            val otherBankAccountJson = JSONFactory.createOtherBankAccount(moderatedTransaction.otherBankAccount.get)
            (otherBankAccountJson, HttpCode.`200`(callContext))
          }

      }
    }

  }
}

object APIMethods121 {
}
