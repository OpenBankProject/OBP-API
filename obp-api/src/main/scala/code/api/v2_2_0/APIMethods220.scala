package code.api.v2_2_0

import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.util.newstyle.ViewNewStyle
import code.api.v1_2_1.{CreateViewJsonV121, JSONFactory, UpdateViewJsonV121}
import code.api.v2_1_0._
import code.api.v2_2_0.JSONFactory220.transformV220ToBranch
import code.api.v3_1_0.PostPutProductJsonV310
import code.api.v4_0_0.AtmJsonV400
import code.bankconnectors._
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.metadata.counterparties.{Counterparties, MappedCounterparty}
import code.metrics.ConnectorMetricsProvider
import code.model._
import code.model.dataAccess.BankAccountCreation
import code.util.Helper
import code.util.Helper._
import code.views.Views
import code.views.system.ViewPermission
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.StringHelpers

import java.util.Date
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future



trait APIMethods220 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations2_2_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    
    val implementedInApiVersion = ApiVersion.v2_2_0

    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      root,
      implementedInApiVersion,
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
            (JSONFactory.getApiInfoJSON(OBPAPI2_2_0.version, OBPAPI2_2_0.versionStatus), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      getViewsForBankAccount,
      implementedInApiVersion,
      "getViewsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account",
      s"""#Views
        |
        |
        |Views in Open Bank Project provide a mechanism for fine grained access control and delegation to Accounts and Transactions. Account holders use the 'owner' view by default. 
        |Delegated access is made through other views for example 'accountants', 'share-holders' or 'tagging-application'. Views can be created via the API and each view has a list of entitlements.
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
      viewsJSONV220,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagView, apiTagAccount))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            permission <- NewStyle.function.permission(bankId, accountId, u, callContext)
            anyViewContainsCanSeeAvailableViewsForBankAccountPermission =  permission.views.map(_.allowed_actions.exists(_ ==  CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT)).find(true == _).getOrElse(false)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT}` permission on any your views",
              cc= callContext
            ){
              anyViewContainsCanSeeAvailableViewsForBankAccountPermission
            }
            views <- Future(Views.views.vend.availableViewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
          } yield {
            val viewsJSON = JSONFactory220.createViewsJSON(views)
            (viewsJSON, HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createViewForBankAccount,
      implementedInApiVersion,
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
        | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
        |
        | You should use a leading _ (underscore) for the view name because other view names may become reserved by OBP internally
        | """,
      createViewJsonV121,
      viewJSONV220,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagAccount, apiTagView, apiTagOldStyle))

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            createViewJsonV121 <- tryo{json.extract[CreateViewJsonV121]} ?~!InvalidJsonFormat
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _<- booleanToBox(isValidCustomViewName(createViewJsonV121.name), InvalidCustomViewFormat+s"Current view_name (${createViewJsonV121.name})")
            u <- cc.user ?~!UserNotLoggedIn
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
            permission <- Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
            anyViewContainsCanCreateCustomViewPermission =  permission.views.map(_.allowed_actions.exists(_ ==CAN_CREATE_CUSTOM_VIEW)).find(_ == true).getOrElse(false)
            
            _ <- booleanToBox(
              anyViewContainsCanCreateCustomViewPermission,
              s"${ErrorMessages.CreateCustomViewError} You need the `${CAN_CREATE_CUSTOM_VIEW}` permission on any your views"
            )
            view <- Views.views.vend.createCustomView(BankIdAccountId(bankId, accountId), createViewJson) ?~ CreateCustomViewError
          } yield {
            val viewJSON = JSONFactory220.createViewJSON(view)
            successJsonResponse(Extraction.decompose(viewJSON), 201)
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateViewForBankAccount,
      implementedInApiVersion,
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
      viewJSONV220,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagAccount, apiTagView, apiTagOldStyle)
    )

    lazy val updateViewForBankAccount : OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            updateJsonV121 <- tryo{json.extract[UpdateViewJsonV121]} ?~!InvalidJsonFormat
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _ <- booleanToBox(viewId.value.startsWith("_"), InvalidCustomViewFormat+s"Current view_name (${viewId.value})")
            view <- APIUtil.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), cc.user, Some(cc))
            _ <- booleanToBox(!view.isSystem, SystemViewsCanNotBeModified)
            u <- cc.user ?~!UserNotLoggedIn
            account <- BankAccountX(bankId, accountId) ?~!BankAccountNotFound
            updateViewJson = UpdateViewJSON(
              description = updateJsonV121.description,
              metadata_view = view.metadataView, //this only used from V300, here just copy from currentView . 
              is_public = updateJsonV121.is_public,
              which_alias_to_use = updateJsonV121.which_alias_to_use,
              hide_metadata_if_alias_used = updateJsonV121.hide_metadata_if_alias_used,
              allowed_actions = updateJsonV121.allowed_actions
            )

            permission <- Views.views.vend.permission(BankIdAccountId(account.bankId, account.accountId), u)
            anyViewContainsCancanUpdateCustomViewPermission =  permission.views.map(_.allowed_actions.exists(_ == CAN_UPDATE_CUSTOM_VIEW)).find(true == _).getOrElse(false)
              
            _ <- booleanToBox(
              anyViewContainsCancanUpdateCustomViewPermission,
              s"${ErrorMessages.CreateCustomViewError} You need the `${(CAN_UPDATE_CUSTOM_VIEW)}` permission on any your views"
            )
            updatedView <- Views.views.vend.updateCustomView(BankIdAccountId(bankId, accountId), viewId, updateViewJson) ?~ CreateCustomViewError
          } yield {
            val viewJSON = JSONFactory220.createViewJSON(updatedView)
            successJsonResponse(Extraction.decompose(viewJSON), 200)
          }
      }
    }

    // Not used yet.
    val getFxIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getFxIsPublic", false)


    resourceDocs += ResourceDoc(
      getCurrentFxRate,
      implementedInApiVersion,
      "getCurrentFxRate",
      "GET",
      "/banks/BANK_ID/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE",
      "Get Current FxRate",
      """Get the latest FX rate specified by BANK_ID, FROM_CURRENCY_CODE and TO_CURRENCY_CODE
        |
        |OBP may try different sources of FX rate information depending on the Connector in operation.
        |
        |For example we want to convert EUR => USD:
        |
        |OBP will:
        |1st try - Connector (database, core banking system or external FX service)
        |2nd try part 1 - fallbackexchangerates/eur.json
        |2nd try part 2 - fallbackexchangerates/usd.json (the inverse rate is used)
        |3rd try - Hardcoded map of FX rates.
        |
        |![FX Flow](https://user-images.githubusercontent.com/485218/60005085-1eded600-966e-11e9-96fb-798b102d9ad0.png)
        |
      """.stripMargin,
      EmptyBody,
      fXRateJSON,
      List(InvalidISOCurrencyCode,UserNotLoggedIn,FXCurrencyCodeCombinationsNotSupported, UnknownError),
      List(apiTagFx))

    val getCurrentFxRateIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getCurrentFxRateIsPublic", false)

    lazy val getCurrentFxRate: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "fx" :: fromCurrencyCode :: toCurrencyCode :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getCurrentFxRateIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            _ <- Helper.booleanToFuture(failMsg = ConsumerHasMissingRoles + CanReadFx, cc=callContext) {
              checkScope(bankId.value, getConsumerPrimaryKey(callContext), ApiRole.canReadFx)
            }
            fromCurrencyCodeUpperCase = fromCurrencyCode.toUpperCase
            toCurrencyCodeUpperCase = toCurrencyCode.toUpperCase
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.isValidCurrencyISOCode(fromCurrencyCodeUpperCase, callContext)
            _ <- NewStyle.function.isValidCurrencyISOCode(toCurrencyCodeUpperCase, callContext)
            fxRate <- NewStyle.function.getExchangeRate(bankId, fromCurrencyCodeUpperCase, toCurrencyCodeUpperCase, callContext)
          } yield {
            val viewJSON = JSONFactory220.createFXRateJSON(fxRate)
            (viewJSON, HttpCode.`200`(callContext))
          }

      }
    }

    resourceDocs += ResourceDoc(
      getExplicitCounterpartiesForAccount,
      implementedInApiVersion,
      "getExplicitCounterpartiesForAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit)",
      s"""This endpoints gets the explicit Counterparties on an Account / View.
          |
          |For a general introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
          |
          |${userAuthenticationMessage(true)}
          |""".stripMargin,
      EmptyBody,
      counterpartiesJsonV220,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        NoViewPermission,
        UserNoPermissionAccessView,
        UnknownError
      ),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagAccount, apiTagPsd2))

    lazy val getExplicitCounterpartiesForAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.NoViewPermission} You need the `${(CAN_GET_COUNTERPARTY)}` permission on the View(${viewId.value} )",
              cc = callContext
            ) {
              ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_GET_COUNTERPARTY)
            }
            (counterparties, callContext) <- NewStyle.function.getCounterparties(bankId,accountId,viewId, callContext)
            //Here we need create the metadata for all the explicit counterparties. maybe show them in json response.
            //Note: actually we need update all the counterparty metadata when they from adapter. Some counterparties may be the first time to api, there is no metadata.
            _ <- Helper.booleanToFuture(CreateOrUpdateCounterpartyMetadataError, 400, cc=callContext) {
              {
                for {
                  counterparty <- counterparties
                } yield {
                  Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, counterparty.name) match {
                    case Full(_) => true
                    case _ => false
                  }
                }
              }.forall(_ == true)
            }
          } yield {
            val counterpartiesJson = JSONFactory220.createCounterpartiesJSON(counterparties)
            (counterpartiesJson, HttpCode.`200`(callContext))
          }
      }
    }

  
    resourceDocs += ResourceDoc(
      getExplicitCounterpartyById,
      implementedInApiVersion,
      "getExplicitCounterpartyById",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Counterparty Id (Explicit)",
      s"""Information returned about the Counterparty specified by COUNTERPARTY_ID:
         |
         |${userAuthenticationMessage(true)}
         |""".stripMargin,
      EmptyBody,
      counterpartyWithMetadataJson,
      List(UserNotLoggedIn, BankNotFound, UnknownError),
      List(apiTagCounterparty, apiTagPSD2PIS, apiTagCounterpartyMetaData, apiTagPsd2)
    )
  
    lazy val getExplicitCounterpartyById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, account.accountId), Some(u), callContext)

            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.NoViewPermission} You need the `${(CAN_GET_COUNTERPARTY)}` permission on the View(${viewId.value} )",
              cc = callContext
            ) {
              ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_GET_COUNTERPARTY)
            }
            
            counterpartyMetadata <- NewStyle.function.getMetadata(bankId, accountId, counterpartyId.value, callContext)
            (counterparty, callContext) <- NewStyle.function.getCounterpartyTrait(bankId, accountId, counterpartyId.value, callContext)
          } yield {
            val counterpartyJson = JSONFactory220.createCounterpartyWithMetadataJSON(counterparty,counterpartyMetadata)
            (counterpartyJson, HttpCode.`200`(callContext))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getMessageDocs,
      implementedInApiVersion,
      "getMessageDocs",
      "GET",
      "/message-docs/CONNECTOR",
      "Get Message Docs",
      """These message docs provide example messages sent by OBP to the (RabbitMq) message queue for processing by the Core Banking / Payment system Adapter - together with an example expected response and possible error codes.
        | Integrators can use these messages to build Adapters that provide core banking services to OBP.
        |
        | Note: API Explorer provides a Message Docs page where these messages are displayed.
        | 
        | `CONNECTOR`: rest_vMar2019, stored_procedure_vDec2019 ...
      """.stripMargin,
      EmptyBody,
      messageDocsJson,
      List(InvalidConnector, UnknownError),
      List(apiTagDocumentation, apiTagApi)
    )

    lazy val getMessageDocs: OBPEndpoint = {
      case "message-docs" :: connector :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            connectorObject <- Future(tryo{Connector.getConnectorInstance(connector)}) map { i =>
              val msg = s"$InvalidConnector Current Input is $connector. It should be eg: rest_vMar2019..."
              unboxFullOrFail(i, cc.callContext, msg)
            }
          } yield {
            val json = JSONFactory220.createMessageDocsJson(connectorObject.messageDocs.toList)
            (json, HttpCode.`200`(cc.callContext))
          }
        }
      }
    }


    resourceDocs += ResourceDoc(
      createBank,
      implementedInApiVersion,
      "createBank",
      "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |${userAuthenticationMessage(true) }
         |""",
      bankJSONV220,
      bankJSONV220,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      List(apiTagBank, apiTagOldStyle),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            bank <- tryo{ json.extract[BankJSONV220] } ?~! ErrorMessages.InvalidJsonFormat
            _ <- Helper.booleanToBox(
              bank.id.length > 5,s"$InvalidJsonFormat Min length of BANK_ID should be 5 characters.")
            
            checkShortStringValue = APIUtil.checkShortString(bank.id)
            
            _ <- Helper.booleanToBox(checkShortStringValue == SILENCE_IS_GOLDEN, s"$checkShortStringValue.")
            
            _ <- Helper.booleanToBox(
              !`checkIfContains::::` (bank.id), s"$InvalidJsonFormat BANK_ID can not contain `::::` characters")
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            consumer <- cc.consumer ?~! ErrorMessages.InvalidConsumerCredentials
            _ <- NewStyle.function.hasEntitlementAndScope("", u.userId, consumer.id.get.toString,  canCreateBank, cc.callContext)
            success <- Connector.connector.vend.createOrUpdateBank(
              bank.id,
              bank.full_name,
              bank.short_name,
              bank.logo_url,
              bank.website_url,
              bank.swift_bic,
              bank.national_identifier,
              bank.bank_routing.scheme,
              bank.bank_routing.address,
              Some(cc)
            )
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserId(u.userId)
            
            entitlementsByBank = entitlements.filter(_.bankId==bank.id)
            _ <- entitlementsByBank.filter(_.roleName == CanCreateEntitlementAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Full()
              case false =>
                Full(Entitlement.entitlement.vend.addEntitlement(bank.id, u.userId, CanCreateEntitlementAtOneBank.toString()))
            }
            _ <- entitlementsByBank.filter(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Full()
              case false =>
                Full(Entitlement.entitlement.vend.addEntitlement(bank.id, u.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
            }
          } yield {
            val json = JSONFactory220.createBankJSON(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }



    // Create Branch
    val createBranchEntitlementsRequiredForSpecificBank = CanCreateBranch :: Nil
    val createBranchEntitlementsRequiredForAnyBank = CanCreateBranchAtAnyBank :: Nil

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
         |${userAuthenticationMessage(true) }
         |
         |""",
      branchJsonV220,
      branchJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InsufficientAuthorisationToCreateBranch,
        UnknownError
      ),
      List(apiTagBranch, apiTagOpenData),
      Some(List(canCreateBranch,canCreateBranchAtAnyBank))
    )

    lazy val createBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Future(
              NewStyle.function.hasAllEntitlements(bank.bankId.value, u.userId, canCreateBranch::Nil, canCreateBranchAtAnyBank::Nil, cc.callContext)
            )
            branchJsonV220 <- NewStyle.function.tryons(failMsg = InvalidJsonFormat + " BranchJsonV300", 400, callContext) {
              json.extract[BranchJsonV220]
            }
            _ <- Helper.booleanToFuture(failMsg = "BANK_ID has to be the same in the URL and Body", 400, callContext) {
              branchJsonV220.bank_id == bank.bankId.value
            }
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, cc.callContext) {
              transformV220ToBranch(branchJsonV220).head
            }
            (success, callContext)  <- NewStyle.function.createOrUpdateBranch(branch, callContext)
          } yield {
            (JSONFactory220.createBranchJson(success), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createAtm,
      implementedInApiVersion,
      "createAtm",
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM for the Bank.
          |
          |${userAuthenticationMessage(true) }
          |
          |""",
      atmJsonV220,
      atmJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canCreateAtm,canCreateAtmAtAnyBank))
    )



    lazy val createAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" ::  Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            atmJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV400]}", 400, callContext) {
              json.extract[AtmJsonV220]
            }
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createAtmEntitlementsRequiredText)(bankId.value, u.userId, createAtmEntitlements, callContext)
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, callContext){atmJsonV400.bank_id == bankId.value}
            atm <- NewStyle.function.tryons(ErrorMessages.CouldNotTransformJsonToInternalModel + " Atm", 400, callContext) {
              JSONFactory220.transformToAtmFromV220(atmJson).head
            }
            (atm, callContext) <- NewStyle.function.createOrUpdateAtm(atm, callContext)
          } yield {
            (JSONFactory220.createAtmJson(atm), HttpCode.`201`(callContext))
          }
      }
    }



    val createProductEntitlementsRequiredForSpecificBank = canCreateProduct ::  Nil
    val createProductEntitlementsRequiredForAnyBank = canCreateProductAtAnyBank ::  Nil

    resourceDocs += ResourceDoc(
      createProduct,
      implementedInApiVersion,
      "createProduct",
      "PUT",
      "/banks/BANK_ID/products",
      "Create Product",
      s"""Create or Update Product for the Bank.
          |
         |${userAuthenticationMessage(true) }
          |
          |""",
      productJsonV220,
      productJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )



    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" ::  Nil JsonPut json -> _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostPutProductJsonV310 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductJsonV220]
            }
            (success, callContext) <- NewStyle.function.createOrUpdateProduct(
              bankId = bankId.value,
              code = product.code,
              parentProductCode = None,
              name = product.name,
              category = product.category,
              family = product.family,
              superFamily = product.super_family,
              moreInfoUrl = product.more_info_url,
              termsAndConditionsUrl = null,
              details = product.details,
              description = product.description,
              metaLicenceId = product.meta.license.id,
              metaLicenceName = product.meta.license.name,
              callContext
            )
          } yield {
            (JSONFactory220.createProductJson(success), HttpCode.`201`(callContext))
          }
      }
    }



    val createFxEntitlementsRequiredForSpecificBank = canCreateFxRate ::  Nil
    val createFxEntitlementsRequiredForAnyBank = canCreateFxRateAtAnyBank ::  Nil

    resourceDocs += ResourceDoc(
      createFx,
      implementedInApiVersion,
      "createFx",
      "PUT",
      "/banks/BANK_ID/fx",
      "Create Fx",
      s"""Create or Update Fx for the Bank.
          |
          |Example:
          |
          |“from_currency_code”:“EUR”,
          |“to_currency_code”:“USD”,
          |“conversion_value”: 1.136305,
          |“inverse_conversion_value”: 1 / 1.136305 = 0.8800454103431737,
          |
          | Thus 1 Euro = 1.136305 US Dollar
          | and
          | 1 US Dollar = 0.8800 Euro
          |
          |
         |${userAuthenticationMessage(true) }
          |
          |""",
      fxJsonV220,
      fxJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagFx),
      Some(List(canCreateFxRate, canCreateFxRateAtAnyBank))
    )



    lazy val createFx: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "fx" ::  Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Future {
              NewStyle.function.hasAllEntitlements(
                bank.bankId.value, 
                u.userId, 
                createFxEntitlementsRequiredForSpecificBank, 
                createFxEntitlementsRequiredForAnyBank, 
                callContext
              )
            }
            fx <- NewStyle.function.tryons(ErrorMessages.InvalidJsonFormat, 400, callContext) {
              json.extract[FXRateJsonV220]
            }
            _ <- NewStyle.function.isValidCurrencyISOCode(fx.from_currency_code, callContext)
            _ <- NewStyle.function.isValidCurrencyISOCode(fx.to_currency_code, callContext)
            (fxRate, callContext)<- NewStyle.function.createOrUpdateFXRate(
              bankId = fx.bank_id,
              fromCurrencyCode = fx.from_currency_code,
              toCurrencyCode = fx.to_currency_code,
              conversionValue = fx.conversion_value,
              inverseConversionValue = fx.inverse_conversion_value,
              effectiveDate = fx.effective_date,
              callContext
            )
          } yield {
            val viewJSON = JSONFactory220.createFXRateJSON(fxRate)
            (viewJSON, HttpCode.`201`(callContext))
          }
      }
    }






    resourceDocs += ResourceDoc(
      createAccount,
      implementedInApiVersion,
      "createAccount",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
        |
        |
        |The User can create an Account for themself or an Account for another User if they have CanCreateAccount role.
        |
        |If USER_ID is not specified the account will be owned by the logged in User.
        |
        |The type field should be a product_code from Product.
        |
        |Note: The Amount must be zero.""".stripMargin,
      createAccountJSONV220,
      createAccountJSONV220,
      List(
        InvalidJsonFormat,
        BankNotFound,
        UserNotLoggedIn,
        InvalidUserId,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        UserNotFoundById,
        UserHasMissingRoles,
        InvalidAccountBalanceAmount,
        InvalidAccountInitialBalance,
        InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency,
        AccountIdAlreadyExists,
        UnknownError
      ),
      List(apiTagAccount,apiTagOnboarding),
      Some(List(canCreateAccount))
    )


    lazy val createAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        cc =>{
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $CreateAccountJSONV220 "
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateAccountJSONV220]
            }

            loggedInUserId = u.userId
            userIdAccountOwner = if (createAccountJson.user_id.nonEmpty) createAccountJson.user_id else loggedInUserId
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext){
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext){
              isValidID(accountId.value)
            }

            (postedOrLoggedInUser,callContext) <- NewStyle.function.findByUserId(userIdAccountOwner, callContext)

            // User can create account for self or an account for another user if they have CanCreateAccount role
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext){
              isValidID(accountId.value)
            }

            _ <- if(userIdAccountOwner == loggedInUserId) Future.successful(Full(Unit))
                 else NewStyle.function.hasEntitlement(bankId.value, loggedInUserId, canCreateAccount, callContext, s"${UserHasMissingRoles} $canCreateAccount or create account for self")

            initialBalanceAsString = createAccountJson.balance.amount
            accountType = createAccountJson.`type`
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, callContext) {
              BigDecimal(initialBalanceAsString)
            }

            _ <-  Helper.booleanToFuture(InitialBalanceMustBeZero, cc=callContext){0 == initialBalanceAsNumber}

            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode, cc=callContext){isValidCurrencyISOCode(createAccountJson.balance.currency)}


            currency = createAccountJson.balance.currency

            (_, callContext ) <- NewStyle.function.getBank(bankId, callContext)

            (bankAccount,callContext) <- NewStyle.function.createBankAccount(
              bankId,
              accountId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id,
              List(AccountRouting(createAccountJson.account_routing.scheme, createAccountJson.account_routing.address)),
              callContext
            )
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bankId, accountId, postedOrLoggedInUser, callContext)
          } yield {
            (JSONFactory220.createAccountJSON(userIdAccountOwner, bankAccount), HttpCode.`200`(callContext))

          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      config,
      implementedInApiVersion,
      "config",
      "GET",
      "/config",
      "Get API Configuration",
      """Returns information about:
        |
        |* API Config
        |* Akka ports
        |* Elastic search ports
        |* Cached function """,
      EmptyBody,
      configurationJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      apiTagApi  :: Nil,
      Some(List(canGetConfig)))

    lazy val config: OBPEndpoint = {
      case "config" :: Nil JsonGet _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConfig, callContext)
          } yield {
            (JSONFactory220.getConfigInfoJSON(), callContext)
          }
    }



    resourceDocs += ResourceDoc(
      getConnectorMetrics,
      implementedInApiVersion,
      "getConnectorMetrics",
      "GET",
      "/management/connector/metrics",
      "Get Connector Metrics",
      s"""Get the all metrics
        |
        |require CanGetConnectorMetrics role
        |
        |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/connector/metrics
        |
        |Should be able to filter on the following metrics fields
        |
        |eg: /management/connector/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
        |
        |1 from_date (defaults to one week before current date): eg:from_date=$DateWithMsExampleString
        |
        |2 to_date (defaults to current date) eg:to_date=$DateWithMsExampleString
        |
        |3 limit (for pagination: defaults to 1000)  eg:limit=2000
        |
        |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
        |
        |eg: /management/connector/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=100&offset=300
        |
        |Other filters:
        |
        |5 connector_name  (if null ignore)
        |
        |6 function_name (if null ignore)
        |
        |7 correlation_id (if null ignore)
        |
      """.stripMargin,
      EmptyBody,
      connectorMetricsJson,
      List(
        InvalidDateFormat,
        UnknownError
      ),
      List(apiTagMetric, apiTagApi),
      Some(List(canGetConnectorMetrics)))

    lazy val getConnectorMetrics : OBPEndpoint = {
      case "management" :: "connector" :: "metrics" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConnectorMetrics, callContext)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
            metrics <- Future(ConnectorMetricsProvider.metrics.vend.getAllConnectorMetrics(obpQueryParams))
          } yield {
            (JSONFactory220.createConnectorMetricsJson(metrics), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createConsumer,
      implementedInApiVersion,
      "createConsumer",
      "POST",
      "/management/consumers",
      "Post a Consumer",
      s"""Create a Consumer (Authenticated access).
         |
        |""",
      ConsumerPostJSON(
        "Test",
        "Test",
        "Description",
        "some@email.com",
        "redirecturl",
        "createdby",
        true,
        new Date(),
        """-----BEGIN CERTIFICATE-----
          |client_certificate_content
          |-----END CERTIFICATE-----""".stripMargin
      ),
      ConsumerPostJSON(
        "Some app name",
        "App type",
        "Description",
        "some.email@example.com",
        "Some redirect url",
        "Created by UUID",
        true,
        new Date(),
        """-----BEGIN CERTIFICATE-----
          |client_certificate_content
          |-----END CERTIFICATE-----""".stripMargin
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canCreateConsumer)))


    lazy val createConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn
            _ <- NewStyle.function.ownEntitlement("", u.userId, ApiRole.canCreateConsumer, cc.callContext)
              postedJson <- tryo {json.extract[ConsumerPostJSON]} ?~! InvalidJsonFormat
            consumer <- Consumers.consumers.vend.createConsumer(Some(generateUUID()),
                                                                Some(generateUUID()),
                                                                Some(postedJson.enabled),
                                                                Some(postedJson.app_name),
                                                                None,
                                                                Some(postedJson.description),
                                                                Some(postedJson.developer_email),
                                                                Some(postedJson.redirect_url),
                                                                Some(u.userId),
                                                                Some(postedJson.clientCertificate),
                                                                None,
                                                                None,
                                                               )
          } yield {
            // Format the data as json
            val json = JSONFactory220.createConsumerJSON(consumer)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      createCounterparty,
      implementedInApiVersion,
      "createCounterparty",
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Create Counterparty (Explicit)",
      s"""Create Counterparty (Explicit) for an Account.
         |
         |In OBP, there are two types of Counterparty.
         |
         |* Explicit Counterparties (those here) which we create explicitly and are used in COUNTERPARTY Transaction Requests
         |
         |* Implicit Counterparties (AKA Other Accounts) which are generated automatically from the other sides of Transactions.
         |
          |Explicit Counterparties are created for the account / view
         |They are how the user of the view (e.g. account owner) refers to the other side of the transaction
         |
         |name : the human readable name (e.g. Piano teacher, Miss Nipa)
         |
         |description : the human readable name (e.g. Piano teacher, Miss Nipa)
         |
         |bank_routing_scheme : eg: bankId or bankCode or any other strings
         |
         |bank_routing_address : eg: `gh.29.uk`, must be valid sandbox bankIds
         |
         |account_routing_scheme : eg: AccountId or AccountNumber or any other strings
         |
         |account_routing_address : eg: `1d65db7c-a7b2-4839-af41-95`, must be valid accountIds
         |
         |other_account_secondary_routing_scheme : eg: IBan or any other strings
         |
         |other_account_secondary_routing_address : if it is an IBAN, it should be unique for each counterparty. 
         |
         |other_branch_routing_scheme : eg: branchId or any other strings or you can leave it empty, not useful in sandbox mode.
         |
         |other_branch_routing_address : eg: `branch-id-123` or you can leave it empty, not useful in sandbox mode.
         |
         |is_beneficiary : must be set to `true` in order to send payments to this counterparty
         |
         |bespoke: It supports a list of key-value, you can add it to the counterparty.
         |
         |bespoke.key : any info-key you want to add to this counterparty
         | 
         |bespoke.value : any info-value you want to add to this counterparty
         |
         |The view specified by VIEW_ID must have the canAddCounterparty permission
         |
         |A minimal example for TransactionRequestType == COUNTERPARTY
         | {
         |  "name": "Tesobe1",
         |  "description": "Good Company",
         |  "other_bank_routing_scheme": "OBP",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "OBP",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "is_beneficiary": true,
         |  "other_account_secondary_routing_scheme": "",
         |  "other_account_secondary_routing_address": "",
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         | 
         |A minimal example for TransactionRequestType == SEPA
         | 
         | {
         |  "name": "Tesobe2",
         |  "description": "Good Company",
         |  "other_bank_routing_scheme": "OBP",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "OBP",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "other_account_secondary_routing_scheme": "IBAN",
         |  "other_account_secondary_routing_address": "DE89 3704 0044 0532 0130 00",
         |  "is_beneficiary": true,
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      postCounterpartyJSON,
      counterpartyWithMetadataJson,
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
      List(apiTagCounterparty, apiTagAccount))
  
  
    lazy val createCounterparty: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {isValidID(accountId.value)}
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {isValidID(bankId.value)}
            (bank, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCounterpartyJSON", 400, cc.callContext) {
              json.extract[PostCounterpartyJSON]
            }
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.NoViewPermission} You need the `${(CAN_ADD_COUNTERPARTY)}` permission on the View(${viewId.value} )",
              cc = callContext
            ) {
              ViewPermission.findViewPermissions(view).exists(_.permission.get == CAN_ADD_COUNTERPARTY)
            }
            (counterparty, callContext) <- Connector.connector.vend.checkCounterpartyExists(postJson.name, bankId.value, accountId.value, viewId.value, callContext)

            _ <- Helper.booleanToFuture(CounterpartyAlreadyExists.replace("value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
              s"COUNTERPARTY_NAME(${postJson.name}) for the BANK_ID(${bankId.value}) and ACCOUNT_ID(${accountId.value}) and VIEW_ID($viewId)"), cc=callContext){
              counterparty.isEmpty
            }
            _ <- booleanToFuture(s"$InvalidValueLength. The maximum length of `description` field is ${MappedCounterparty.mDescription.maxLen}", cc=callContext){
              postJson.description.length <= 36
            }

            //If other_account_routing_scheme=="OBP" or other_account_secondary_routing_address=="OBP" we will check if it is a real obp bank account.
            (_, callContext)<- if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_routing_scheme.equalsIgnoreCase("OBP")){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            } else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("OBP") && postJson.other_account_secondary_routing_scheme.equalsIgnoreCase("OBP")){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(postJson.other_bank_routing_address), Some(cc))
                (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_secondary_routing_address), callContext)

              } yield {
                (account, callContext)
              }
            }else if (postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NUMBER")|| postJson.other_bank_routing_scheme.equalsIgnoreCase("ACCOUNT_NO")) {
              for {
                bankIdOption <- Future.successful(if (postJson.other_bank_routing_address.isEmpty) None else Some(postJson.other_bank_routing_address))
                (account, callContext) <- NewStyle.function.getBankAccountByNumber(
                  bankIdOption.map(BankId(_)),
                  postJson.other_bank_routing_address,
                  callContext)
              } yield {
                (account, callContext)
              }
            }else
              Future{(Full(), Some(cc))}


            otherAccountRoutingSchemeOBPFormat = if(postJson.other_account_routing_scheme.equalsIgnoreCase("AccountNo")) "ACCOUNT_NUMBER" else StringHelpers.snakify(postJson.other_account_routing_scheme).toUpperCase

            
            (counterparty, callContext) <- NewStyle.function.createCounterparty(
              name=postJson.name,
              description=postJson.description,
              currency = "",
              createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              otherAccountRoutingScheme=otherAccountRoutingSchemeOBPFormat,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme=postJson.other_account_secondary_routing_scheme,
              otherAccountSecondaryRoutingAddress=postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=postJson.other_branch_routing_scheme,
              otherBranchRoutingAddress=postJson.other_branch_routing_address,
              isBeneficiary=postJson.is_beneficiary,
              bespoke=postJson.bespoke.map(bespoke =>CounterpartyBespoke(bespoke.key,bespoke.value))
              , callContext)

            (counterpartyMetadata, callContext) <- NewStyle.function.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, postJson.name, callContext)
  
          } yield {
            (JSONFactory220.createCounterpartyWithMetadataJSON(counterparty,counterpartyMetadata), HttpCode.`201`(callContext))
          }
      }
    }


/*
    resourceDocs += ResourceDoc(
      getCustomerViewsForAccount,
      apiVersion,
      "getCustomerViews",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/customer-views",
      "Get Customers that have access to a View",
      s"""Returns the Customers (and the Users linked to the Customer) that have access to the view:
          |
          |* Customer: legal_name, customer_number, customer_id
          |* User: username, user_id, email
          |* View: view_id
          |
         |${authenticationRequiredMessage(true)}""".stripMargin,
      EmptyBody,
      customerViewsJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        AccountNotFound,
        ViewNotFound
      ),
      List(apiTagAccount, apiTagCustomer, apiTagView)
    )

    lazy val getCustomerViewsForAccount : OBPEndpoint = {
      //get account by id
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "customer-views" :: Nil JsonGet req => {
        cc =>
          for {
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~ BankNotFound
            account <- BankAccount(bank.bankId, accountId) ?~ ErrorMessages.AccountNotFound
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
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

*/



/*
    lazy val getCustomerViewsForAccount : OBPEndpoint = {
      case "management" :: "connector" :: "metrics" :: Nil JsonGet _ => {
        cc =>{
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanGetConnectorMetrics), s"$CanGetConnectorMetrics entitlement required")

                     } yield {
            val json = {}
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }

*/

/*




 */

  }
}