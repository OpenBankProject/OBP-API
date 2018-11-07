package code.api.v2_2_0

import java.text.SimpleDateFormat
import java.util.{Date, Locale, UUID}

import code.actorsystem.ObpActorConfig
import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.{CreateViewJsonV121, UpdateViewJsonV121}
import code.api.v2_1_0._
import code.api.v2_2_0.JSONFactory220.transformV220ToBranch
import code.bankconnectors._
import code.bankconnectors.vMar2017.JsonFactory_vMar2017
import code.consumer.Consumers
import code.metadata.counterparties.{Counterparties, MappedCounterparty}
import code.metrics.{ConnectorMetric, ConnectorMetricsProvider}
import code.model.dataAccess.BankAccountCreation
import code.model.{BankId, ViewId, _}
import code.util.Helper
import code.util.Helper._
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



trait APIMethods220 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations2_2_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson = EmptyClassJson()
    val implementedInApiVersion: ApiVersion = ApiVersion.v2_2_0 // was String "2_2_0"

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
      viewsJSONV220,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagView, apiTagAccount, apiTagNewStyle))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView +"userId : " + u.userId + ". account : " + accountId) {
              u.hasOwnerViewAccess(BankIdAccountId(account.bankId, account.accountId))
            }
            views <- Future(Views.views.vend.viewsForAccount(BankIdAccountId(account.bankId, account.accountId)))
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView))

    lazy val createViewForBankAccount : OBPEndpoint = {
      //creates a view on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            createViewJsonV121 <- tryo{json.extract[CreateViewJsonV121]} ?~!InvalidJsonFormat
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _<- booleanToBox(createViewJsonV121.name.startsWith("_"), InvalidCustomViewFormat)
            u <- cc.user ?~!UserNotLoggedIn
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
      "Update View.",
      s"""Update an existing view on a bank account
        |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagView)
    )

    lazy val updateViewForBankAccount : OBPEndpoint = {
      //updates a view on a bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            updateJsonV121 <- tryo{json.extract[UpdateViewJsonV121]} ?~!InvalidJsonFormat
            //customer views are started ith `_`,eg _life, _work, and System views startWith letter, eg: owner
            _ <- booleanToBox(viewId.value.startsWith("_"), InvalidCustomViewFormat)
            view <- Views.views.vend.view(viewId, BankIdAccountId(bankId, accountId))
            _ <- booleanToBox(!view.isSystem, SystemViewsCanNotBeModified)
            u <- cc.user ?~!UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~!BankAccountNotFound
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
            val viewJSON = JSONFactory220.createViewJSON(updatedView)
            successJsonResponse(Extraction.decompose(viewJSON), 200)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCurrentFxRate,
      implementedInApiVersion,
      "getCurrentFxRate",
      "GET",
      "/banks/BANK_ID/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE",
      "Get Current FxRate",
      """Get the latest FXRate specified by BANK_ID, FROM_CURRENCY_CODE and TO_CURRENCY_CODE """,
      emptyObjectJson,
      fXRateJSON,
      List(InvalidISOCurrencyCode,UserNotLoggedIn,FXCurrencyCodeCombinationsNotSupported, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagFx, apiTagNewStyle))

    lazy val getCurrentFxRate: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "fx" :: fromCurrencyCode :: toCurrencyCode :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <-  authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.tryons(failMsg = InvalidISOCurrencyCode,400, callContext) {
              assert(isValidCurrencyISOCode(fromCurrencyCode))
            }
            _ <- NewStyle.function.tryons(failMsg = InvalidISOCurrencyCode,400, callContext) {
              assert(isValidCurrencyISOCode(toCurrencyCode))
            }
            fxRate <- Future(Connector.connector.vend.getCurrentFxRate(bankId, fromCurrencyCode, toCurrencyCode)) map {
              unboxFullOrFail(_, callContext, FXCurrencyCodeCombinationsNotSupported,400)
            }
          } yield {
            val viewJSON = JSONFactory220.createFXRateJSON(fxRate)
            (viewJSON, HttpCode.`200`(callContext))
          }

      }
    }

    resourceDocs += ResourceDoc(
      getExplictCounterpartiesForAccount,
      implementedInApiVersion,
      "getExplictCounterpartiesForAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties",
      "Get Counterparties (Explicit).",
      s"""Get the Counterparties (Explicit) for the account / view.
          |
          |${authenticationRequiredMessage(true)}
          |""".stripMargin,
      emptyObjectJson,
      counterpartiesJsonV220,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        ViewNotFound,
        NoViewPermission,
        UserNoPermissionAccessView,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCounterparty, apiTagAccount))

    lazy val getExplictCounterpartiesForAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"${NoViewPermission}canAddCounterparty") {
              view.canAddCounterparty == true
            }
            _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {
              u.hasViewAccess(view)
            }
            (counterparties, callContext) <- NewStyle.function.getCounterparties(bankId,accountId,viewId, callContext)
            //Here we need create the metadata for all the explicit counterparties. maybe show them in json response.
            //Note: actually we need update all the counterparty metadata when they from adapter. Some counterparties may be the first time to api, there is no metadata.
            _ <- Helper.booleanToFuture(CreateOrUpdateCounterpartyMetadataError, 400) {
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
      getExplictCounterpartyById,
      implementedInApiVersion,
      "getExplictCounterpartyById",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID",
      "Get Counterparty by Counterparty Id.(Explicit).",
      s"""Information returned about the Counterparty specified by COUNTERPARTY_ID:
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      emptyObjectJson,
      counterpartyWithMetadataJson,
      List(UserNotLoggedIn, BankNotFound, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagCounterparty, apiTagCounterpartyMetaData)
    )
  
    lazy val getExplictCounterpartyById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: CounterpartyId(counterpartyId) :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"${NoViewPermission}canAddCounterparty") {
              view.canAddCounterparty == true
            }
            _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {
              u.hasViewAccess(view)
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
      """These message docs provide example messages sent by OBP to the (Kafka) message queue for processing by the Core Banking / Payment system Adapter - together with an example expected response and possible error codes.
        | Integrators can use these messages to build Adapters that provide core banking services to OBP.
        | 
        | `CONNECTOR`:kafka_vMar2017 , kafka_vJune2017, kafka_vSept2018 ... 
      """.stripMargin,
      emptyObjectJson,
      messageDocsJson,
      List(UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagDocumentation, apiTagApi)
    )

    lazy val getMessageDocs: OBPEndpoint = {
      case "message-docs" :: connector :: Nil JsonGet _ => {
        cc => {
          for {
            //kafka_vJune2017 --> vJune2017 : get the valid version for search the connector object.
            connectorObject <- tryo{Connector.getConnectorInstance(connector)} ?~! s"$InvalidConnector Current Input is $connector. It should be eg: kafka_vJune2017, kafka_vSept2018..."
            messageDocs <- Full{connectorObject.messageDocs.toList} 
          } yield {
            val json = JSONFactory220.createMessageDocsJson(messageDocs)
            successJsonResponse(Extraction.decompose(json))
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
         |${authenticationRequiredMessage(true) }
         |""",
      bankJSONV220,
      bankJSONV220,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            bank <- tryo{ json.extract[BankJSONV220] } ?~! ErrorMessages.InvalidJsonFormat
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            consumer <- cc.consumer ?~! ErrorMessages.InvalidConsumerCredentials
            _ <- hasEntitlementAndScope("", u.userId, consumer.id.get.toString,  canCreateBank)
            success <- Connector.connector.vend.createOrUpdateBank(
              bank.id,
              bank.full_name,
              bank.short_name,
              bank.logo_url,
              bank.website_url,
              bank.swift_bic,
              bank.national_identifier,
              bank.bank_routing.scheme,
              bank.bank_routing.address
            )
          } yield {
            val json = JSONFactory220.createBankJSON(success)
            createdJsonResponse(Extraction.decompose(json))
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
      branchJsonV220,
      branchJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InsufficientAuthorisationToCreateBranch,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      Nil,
      Some(List(canCreateBranch,canCreateBranchAtAnyBank))
    )

    lazy val createBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" ::  Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            canCreateBranch <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, canCreateBranch) == true
              ||
              hasEntitlement("", u.userId, canCreateBranchAtAnyBank)
              , createBranchEntitlementsRequiredText)
            branchJsonV220 <- tryo {json.extract[BranchJsonV220]} ?~! ErrorMessages.InvalidJsonFormat
            branch <- transformV220ToBranch(branchJsonV220)
            success <- Connector.connector.vend.createOrUpdateBranch(branch)
          } yield {
            val json = JSONFactory220.createBranchJson(success)
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
      atmJsonV220,
      atmJsonV220,
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
            canCreateAtm <- booleanToBox(hasAllEntitlements(bank.bankId.value, u.userId, createAtmEntitlementsRequiredForSpecificBank) == true
              ||
              hasAllEntitlements("", u.userId, createAtmEntitlementsRequiredForAnyBank),
              createAtmEntitlementsRequiredText)
            atmJson <- tryo {json.extract[AtmJsonV220]} ?~! ErrorMessages.InvalidJsonFormat
            atm <- JSONFactory220.transformToAtmFromV220(atmJson) ?~! {ErrorMessages.CouldNotTransformJsonToInternalModel + " Atm"}
            success <- Connector.connector.vend.createOrUpdateAtm(atm)
          } yield {
            val json = JSONFactory220.createAtmJson(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }



    val createProductEntitlementsRequiredForSpecificBank = canCreateProduct ::  Nil
    val createProductEntitlementsRequiredForAnyBank = canCreateProductAtAnyBank ::  Nil

    val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createProductEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createProduct,
      implementedInApiVersion,
      "createProduct",
      "PUT",
      "/banks/BANK_ID/products",
      "Create Product",
      s"""Create or Update Product for the Bank.
          |
         |${authenticationRequiredMessage(true) }
          |
         |$createProductEntitlementsRequiredText
          |""",
      productJsonV220,
      productJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )



    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" ::  Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            _ <- booleanToBox(hasAllEntitlements(bank.bankId.value, u.userId, createProductEntitlementsRequiredForSpecificBank) == true
              ||
              hasAllEntitlements("", u.userId, createProductEntitlementsRequiredForAnyBank),
              createProductEntitlementsRequiredText)
            product <- tryo {json.extract[ProductJsonV220]} ?~! ErrorMessages.InvalidJsonFormat
            success <- Connector.connector.vend.createOrUpdateProduct(
                bankId = product.bank_id,
                code = product.code,
                name = product.name,
                category = product.category,
                family = product.family,
                superFamily = product.super_family,
                moreInfoUrl = product.more_info_url,
                details = product.details,
                description = product.description,
                metaLicenceId = product.meta.license.id,
                metaLicenceName = product.meta.license.name
            )
          } yield {
            val json = JSONFactory220.createProductJson(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }



    val createFxEntitlementsRequiredForSpecificBank = canCreateFxRate ::  Nil
    val createFxEntitlementsRequiredForAnyBank = canCreateFxRateAtAnyBank ::  Nil

    val createFxEntitlementsRequiredText = UserHasMissingRoles + createFxEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createFxEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createFx,
      implementedInApiVersion,
      "createFx",
      "PUT",
      "/banks/BANK_ID/fx",
      "Create Fx",
      s"""Create or Update Fx for the Bank.
          |
         |${authenticationRequiredMessage(true) }
          |
         |$createFxEntitlementsRequiredText
          |""",
      fxJsonV220,
      fxJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagFx),
      Some(List(canCreateFxRate, canCreateFxRateAtAnyBank))
    )



    lazy val createFx: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "fx" ::  Nil JsonPut json -> _ => {
        cc =>
          for {
            u <- cc.user ?~!ErrorMessages.UserNotLoggedIn
            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            canCreateFx <- booleanToBox(hasAllEntitlements(bank.bankId.value, u.userId, createFxEntitlementsRequiredForSpecificBank) == true
              ||
              hasAllEntitlements("", u.userId, createFxEntitlementsRequiredForAnyBank),
              createFxEntitlementsRequiredText)
            fx <- tryo {json.extract[FXRateJsonV220]} ?~! ErrorMessages.InvalidJsonFormat
            success <- Connector.connector.vend.createOrUpdateFXRate(
              bankId = fx.bank_id,
              fromCurrencyCode = fx.from_currency_code,
              toCurrencyCode = fx.to_currency_code,
              conversionValue = fx.conversion_value,
              inverseConversionValue = fx.inverse_conversion_value,
              effectiveDate = fx.effective_date
            )
          } yield {
            val json = JSONFactory220.createFXRateJSON(success)
            createdJsonResponse(Extraction.decompose(json))
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
        AccountIdAlreadyExsits,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount,apiTagOnboarding),
      Some(List(canCreateAccount))
    )


    lazy val createAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        cc =>{
          for {
            jsonBody <- tryo (json.extract[CreateAccountJSONV220]) ?~! InvalidJsonFormat
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            loggedInUser <- cc.user ?~! UserNotLoggedIn
            user_id <- tryo (if (jsonBody.user_id.nonEmpty) jsonBody.user_id else loggedInUser.userId) ?~! InvalidUserId
            _ <- tryo(assert(isValidID(accountId.value)))?~! InvalidAccountIdFormat
            _ <- tryo(assert(isValidID(accountId.value)))?~! InvalidBankIdFormat
            postedOrLoggedInUser <- User.findByUserId(user_id) ?~! UserNotFoundById
            // User can create account for self or an account for another user if they have CanCreateAccount role
            _ <- booleanToBox(hasEntitlement(bankId.value, loggedInUser.userId, canCreateAccount) == true || (user_id == loggedInUser.userId) ,
              s"${UserHasMissingRoles} CanCreateAccount or create account for self")
            initialBalanceAsString <- tryo (jsonBody.balance.amount) ?~! InvalidAccountBalanceAmount
            accountType <- tryo(jsonBody.`type`) ?~! InvalidAccountType
            accountLabel <- tryo(jsonBody.label) //?~! ErrorMessages.InvalidAccountLabel
            initialBalanceAsNumber <- tryo {BigDecimal(initialBalanceAsString)} ?~! InvalidAccountInitialBalance
            _ <- booleanToBox(0 == initialBalanceAsNumber) ?~! InitialBalanceMustBeZero
            currency <- tryo (jsonBody.balance.currency) ?~!ErrorMessages.InvalidAccountBalanceCurrency
            _ <- booleanToBox(BankAccount(bankId, accountId).isEmpty, AccountIdAlreadyExsits)
            bankAccount <- Connector.connector.vend.createSandboxBankAccount(
              bankId,
              accountId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              jsonBody.branch_id,
              jsonBody.account_routing.scheme,
              jsonBody.account_routing.address
            )
          } yield {
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            BankAccountCreation.setAsOwner(bankId, accountId, postedOrLoggedInUser)

            val json = JSONFactory220.createAccountJSON(user_id, bankAccount)

            successJsonResponse(Extraction.decompose(json))
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
      emptyObjectJson,
      configurationJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      apiTagApi :: apiTagNewStyle :: Nil,
      Some(List(canGetConfig)))

    lazy val config: OBPEndpoint = {
      case "config" :: Nil JsonGet _ =>
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetConfig)("", u.userId, ApiRole.canGetConfig)
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
        |eg: /management/connector/metrics?from_date=$DateWithDayExampleString&to_date=$DateWithDayExampleString&limit=50&offset=2
        |
        |1 from_date (defaults to one week before current date): eg:from_date=$DateWithDayExampleString
        |
        |2 to_date (defaults to current date) eg:to_date=$DateWithDayExampleString
        |
        |3 limit (for pagination: defaults to 1000)  eg:limit=2000
        |
        |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
        |
        |eg: /management/connector/metrics?from_date=$DateWithDayExampleString&to_date=$DateWithDayExampleString&limit=100&offset=300
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
      emptyObjectJson,
      connectorMetricsJson,
      List(
        InvalidDateFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMetric, apiTagApi),
      Some(List(canGetConnectorMetrics)))

    lazy val getConnectorMetrics : OBPEndpoint = {
      case "management" :: "connector" :: "metrics" :: Nil JsonGet _ => {
        cc => {
          for {
            u <- cc.user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.canGetConnectorMetrics), s"$CanGetConnectorMetrics entitlement required")
            httpParams <- createHttpParamsByUrl(cc.url)
            obpQueryParams <- createQueriesByHttpParams(httpParams)
            metrics <- Full(ConnectorMetricsProvider.metrics.vend.getAllConnectorMetrics(obpQueryParams))
          } yield {
            val json = JSONFactory220.createConnectorMetricsJson(metrics)
            successJsonResponse(Extraction.decompose(json)(DateFormatWithCurrentTimeZone))
          }
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
        new Date()
      ),
      ConsumerPostJSON(
        "Some app name",
        "App type",
        "Description",
        "some.email@example.com",
        "Some redirect url",
        "Created by UUID",
        true,
        new Date()
      ),
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil,
      Some(List(canCreateConsumer)))


    lazy val createConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.canCreateConsumer), UserHasMissingRoles + CanCreateConsumer )
            postedJson <- tryo {json.extract[ConsumerPostJSON]} ?~! InvalidJsonFormat
            consumer <- Consumers.consumers.vend.createConsumer(Some(generateUUID()),
                                                                Some(generateUUID()),
                                                                Some(postedJson.enabled),
                                                                Some(postedJson.app_name),
                                                                None,
                                                                Some(postedJson.description),
                                                                Some(postedJson.developer_email),
                                                                Some(postedJson.redirect_url),
                                                                Some(u.userId)
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
         |other_account_secondary_routing_address : if it is IBan, it should be unique for each counterparty. 
         |
         |other_branch_routing_scheme : eg: branchId or any other strings or you can leave it empty, not useful in sandbox mode.
         |
         |other_branch_routing_address : eg: `branch-id-123` or you can leave it empty, not useful in sandbox mode.
         |
         |is_beneficiary : must be set to `true` in order to send payments to this counterparty
         |
         |bespoke: It support list of key-value, you can add it to the counterarty.
         |
         |bespoke.key : any info-key you want to add to this counerparty
         | 
         |bespoke.value : any info-value you want to add to this counerparty
         |
         |The view specified by VIEW_ID must have the canAddCounterparty permission
         |
         |A minimal example for TransactionRequestType == COUNTERPARTY
         | {
         |  "name": "Tesobe1",
         |  "description": "Good Company",
         |  "other_bank_routing_scheme": "bankId",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "accountId",
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
         |  "other_bank_routing_scheme": "bankId",
         |  "other_bank_routing_address": "gh.29.uk",
         |  "other_account_routing_scheme": "accountId",
         |  "other_account_routing_address": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
         |  "other_account_secondary_routing_scheme": "IBAN",
         |  "other_account_secondary_routing_address": "DE89 3704 0044 0532 0130 00",
         |  "is_beneficiary": true,
         |  "other_branch_routing_scheme": "",
         |  "other_branch_routing_address": "",
         |  "bespoke": []
         |}
         |
         |${authenticationRequiredMessage(true)}
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCounterparty, apiTagAccount))
  
  
    lazy val createCounterparty: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            u <- cc.user ?~! UserNotLoggedIn
            _ <- tryo(assert(isValidID(accountId.value)))?~! InvalidAccountIdFormat
            _ <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
            (bank, callContext ) <- Bank(bankId, Some(cc)) ?~! s"$BankNotFound Current BANK_ID = $bankId"
            (account, callContext) <- Connector.connector.vend.checkBankAccountExists(bankId, AccountId(accountId.value), Some(cc)) ?~! s"$AccountNotFound Current ACCOUNT_ID = ${accountId.value}"
            postJson <- tryo {json.extract[PostCounterpartyJSON]} ?~! {InvalidJsonFormat+PostCounterpartyJSON}
            view <- Views.views.vend.view(viewId, BankIdAccountId(account.bankId, account.accountId))
            _ <- booleanToBox(u.hasViewAccess(view), UserNoPermissionAccessView)
            _ <- booleanToBox(view.canAddCounterparty == true, "The current view does not have can_add_counterparty permission. Please use a view with that permission or add the permission to this view.")
            _ <- tryo(assert(Counterparties.counterparties.vend.
              checkCounterpartyAvailable(postJson.name,bankId.value, accountId.value,viewId.value) == true)
            ) ?~! CounterpartyAlreadyExists
            
            //If it is sandbox mode, the counterparty is a real obp bank account. the bank_id is `other_bank_routing_address` and account_id is `other_account_routing_address`.
            //So we can check the existence of counterparty when we create it . 
            _<- if (APIUtil.isSandboxMode){
              for{
                _ <- booleanToBox(postJson.description.length <= 36, s"$InvalidValueLength. The maxsinec length of `description` filed is ${MappedCounterparty.mDescription.maxLen}")
                (bank, callContext) <- Bank(BankId(postJson.other_bank_routing_address), Some(cc)) ?~! s"$CounterpartyNotFound Current BANK_ID = ${postJson.other_bank_routing_address}."
                account <- Connector.connector.vend.checkBankAccountExists(BankId(postJson.other_bank_routing_address), AccountId(postJson.other_account_routing_address),Some(cc)) ?~! s"$CounterpartyNotFound Current BANK_ID = ${postJson.other_bank_routing_address}. and Current ACCOUNT_ID = ${postJson.other_account_routing_address}. "
              } yield {
                account
              }
            }
            else
              Full()
              
            (counterparty, callConext) <- Connector.connector.vend.createCounterparty(
              name=postJson.name,
              description=postJson.description,
              createdByUserId=u.userId,
              thisBankId=bankId.value,
              thisAccountId=accountId.value,
              thisViewId = viewId.value,
              otherAccountRoutingScheme=postJson.other_account_routing_scheme,
              otherAccountRoutingAddress=postJson.other_account_routing_address,
              otherAccountSecondaryRoutingScheme=postJson.other_account_secondary_routing_scheme,
              otherAccountSecondaryRoutingAddress=postJson.other_account_secondary_routing_address,
              otherBankRoutingScheme=postJson.other_bank_routing_scheme,
              otherBankRoutingAddress=postJson.other_bank_routing_address,
              otherBranchRoutingScheme=postJson.other_branch_routing_scheme,
              otherBranchRoutingAddress=postJson.other_branch_routing_address,
              isBeneficiary=postJson.is_beneficiary,
              bespoke=postJson.bespoke.map(bespoke =>CounterpartyBespoke(bespoke.key,bespoke.value))
            , Some(cc))
          
            counterpartyMetadata <- Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, postJson.name) ?~! CreateOrUpdateCounterpartyMetadataError
  
          } yield {
            val list = JSONFactory220.createCounterpartyWithMetadataJSON(counterparty,counterpartyMetadata)
            successJsonResponse(Extraction.decompose(list))
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
         |${authenticationRequiredMessage(true)}""",
      emptyObjectJson,
      customerViewsJsonV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        AccountNotFound,
        ViewNotFound
      ),
      Catalogs(Core, notPSD2, OBWG),
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