package code.api.v2_2_0

import java.text.SimpleDateFormat
import java.util.{Date, Locale, UUID}

import code.actorsystem.ObpActorConfig
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{isValidCurrencyISOCode, _}
import code.api.util.ApiRole._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.v1_4_0.JSONFactory1_4_0.{AddressJson, LocationJson, MetaJson}



import code.api.v2_1_0._
import code.api.v2_2_0._
import code.api.v2_1_0.JSONFactory210.createConsumerJSONs
import code.bankconnectors._
import code.consumer.Consumers
import code.metrics.{ConnectorMetric, ConnMetrics}
import code.model.dataAccess.BankAccountCreation
import code.model.{BankId, ViewId, _}
import code.util.Helper._
import net.liftweb.common.{Box, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req, S}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.{randomString, tryo}
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer


trait APIMethods220 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  private def getConfigInfoJSON(): JValue = {
    val apiConfiguration: JValue = {

      val f1 = CachedFunctionJSON("getBank", Props.get("connector.cache.ttl.seconds.getBank", "0").toInt)
      val f2 = CachedFunctionJSON("getBanks", Props.get("connector.cache.ttl.seconds.getBanks", "0").toInt)
      val f3 = CachedFunctionJSON("getAccount", Props.get("connector.cache.ttl.seconds.getAccount", "0").toInt)
      val f4 = CachedFunctionJSON("getAccounts", Props.get("connector.cache.ttl.seconds.getAccounts", "0").toInt)
      val f5 = CachedFunctionJSON("getTransaction", Props.get("connector.cache.ttl.seconds.getTransaction", "0").toInt)
      val f6 = CachedFunctionJSON("getTransactions", Props.get("connector.cache.ttl.seconds.getTransactions", "0").toInt)
      val f7 = CachedFunctionJSON("getCounterpartyFromTransaction", Props.get("connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0").toInt)
      val f8 = CachedFunctionJSON("getCounterpartiesFromTransaction", Props.get("connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0").toInt)

      val akkaPorts = PortJSON("remotedata.local.port", ObpActorConfig.localPort.toString) :: PortJSON("remotedata.port", ObpActorConfig.remotePort) :: Nil
      val akka = AkkaJSON(akkaPorts, ObpActorConfig.akka_loglevel)
      val cache = f1::f2::f3::f4::f5::f6::f7::f8::Nil

      val metrics = MetricsJSON("es.metrics.port.tcp", Props.get("es.metrics.port.tcp", "9300")) ::
                    MetricsJSON("es.metrics.port.http", Props.get("es.metrics.port.tcp", "9200")) ::
                    Nil
      val warehouse = WarehouseJSON("es.warehouse.port.tcp", Props.get("es.warehouse.port.tcp", "9300")) ::
                      WarehouseJSON("es.warehouse.port.http", Props.get("es.warehouse.port.http", "9200")) ::
                      Nil

      val apiConfigJSON = ConfigurationJSON(akka, ElasticSearchJSON(metrics, warehouse), cache)
      Extraction.decompose(apiConfigJSON)
    }
    apiConfiguration
  }
  // helper methods end here

  val Implementations2_2_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson = EmptyClassJson()
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
      viewsJSONV220,
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
      createViewJson,
      viewJSONV220,
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
      updateViewJSON,
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
      "/banks/BANK_ID/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE",
      "Get Current FxRate",
      """Get the latest FXRate specified by BANK_ID, FROM_CURRENCY_CODE and TO_CURRENCY_CODE """,
      emptyObjectJson,
      fXRateJSON,
      List(InvalidISOCurrencyCode,UserNotLoggedIn,FXCurrencyCodeCombinationsNotSupported, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)

    lazy val getCurrentFxRate: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankid) :: "fx" :: fromCurrencyCode :: toCurrencyCode :: Nil JsonGet json => {
        user =>
          for {
            bank <- Bank(bankId)?~! BankNotFound
            isValidCurrencyISOCodeFrom <- tryo(assert(isValidCurrencyISOCode(fromCurrencyCode))) ?~! ErrorMessages.InvalidISOCurrencyCode
            isValidCurrencyISOCodeTo <- tryo(assert(isValidCurrencyISOCode(toCurrencyCode))) ?~! ErrorMessages.InvalidISOCurrencyCode
            u <- user ?~! UserNotLoggedIn
            fxRate <- tryo(Connector.connector.vend.getCurrentFxRate(bankId, fromCurrencyCode, toCurrencyCode).get) ?~! ErrorMessages.FXCurrencyCodeCombinationsNotSupported
          } yield {
            val viewJSON = JSONFactory220.createFXRateJSON(fxRate)
            successJsonResponse(Extraction.decompose(viewJSON))
          }
      }
    }    

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
      counterpartiesJsonV220,
      List(
        UserNotLoggedIn, 
        BankAccountNotFound, 
        ViewNotFound, 
        ViewNoPermission,
        UserNoPermissionAccessView, 
        UnknownError
      ),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagPerson, apiTagUser, apiTagAccount, apiTagCounterparty))

    lazy val getCounterpartiesForAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get other accounts for one account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "counterparties" :: Nil JsonGet json => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            account <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- View.fromUrl(viewId, account)?~! ViewNotFound
            canAddCounterparty <- booleanToBox(view.canAddCounterparty == true, s"${ViewNoPermission}canAddCounterparty")
            canUserAccessView <- Full(account.permittedViews(user).find(_ == viewId)) ?~! UserNoPermissionAccessView
            counterparties <- Connector.connector.vend.getCounterparties(bankId,accountId,viewId) 
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
      messageDocsJson,
      List(UnknownError),
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
      bankJSONV220,
      bankJSONV220,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBank)
    )
  
    lazy val createBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: Nil JsonPost json -> _ => {
        user =>
          for {
            bank <- tryo{ json.extract[BankJSONV220] } ?~! ErrorMessages.InvalidJsonFormat
            u <- user ?~!ErrorMessages.UserNotLoggedIn
            canCreateBank <- booleanToBox(hasEntitlement("", u.userId, CanCreateBank) == true, ErrorMessages.InsufficientAuthorisationToCreateBank)
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
      branchJsonV220,
      branchJsonV220,
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
            canCreateBranch <- booleanToBox(hasEntitlement(bank.bankId.value, u.userId, CanCreateBranch) == true
              ||
              hasEntitlement("", u.userId, CanCreateBranchAtAnyBank)
              , createBranchEntitlementsRequiredText)
            branch <- tryo {json.extract[BranchJsonV220]} ?~! ErrorMessages.InvalidJsonFormat
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
              branch.branch_routing.scheme,
              branch.branch_routing.address
            )
          } yield {
            val json = JSONFactory220.createBranchJson(success)
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
      atmJsonV220,
      atmJsonV220,
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
            canCreateAtm <- booleanToBox(hasAllEntitlements(bank.bankId.value, u.userId, createAtmEntitlementsRequiredForSpecificBank) == true
              ||
              hasAllEntitlements("", u.userId, createAtmEntitlementsRequiredForAnyBank),
              createAtmEntitlementsRequiredText)
            atm <- tryo {json.extract[AtmJsonV220]} ?~! ErrorMessages.InvalidJsonFormat
            success <- Connector.connector.vend.createOrUpdateAtm(
              AtmJsonPost(
                atm.id,
                atm.bank_id,
                atm.name,
                atm.address,
                atm.location,
                atm.meta
              )
            )
          } yield {
            val json = JSONFactory220.createAtmJson(success)
            createdJsonResponse(Extraction.decompose(json))
          }
      }
    }



    val createProductEntitlementsRequiredForSpecificBank = CanCreateProduct ::  Nil
    val createProductEntitlementsRequiredForAnyBank = CanCreateProductAtAnyBank ::  Nil

    val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createProductEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createProduct,
      apiVersion,
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
      Nil
    )



    lazy val createProduct: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "products" ::  Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~!ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)?~! BankNotFound
            canCreateProduct <- booleanToBox(hasAllEntitlements(bank.bankId.value, u.userId, createProductEntitlementsRequiredForSpecificBank) == true
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



    val createFxEntitlementsRequiredForSpecificBank = CanCreateFxRate ::  Nil
    val createFxEntitlementsRequiredForAnyBank = CanCreateFxRateAtAnyBank ::  Nil

    val createFxEntitlementsRequiredText = UserHasMissingRoles + createFxEntitlementsRequiredForSpecificBank.mkString(" and ") + " OR " + createFxEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      createFx,
      apiVersion,
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
      Nil
    )



    lazy val createFx: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "fx" ::  Nil JsonPut json -> _ => {
        user =>
          for {
            u <- user ?~!ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId)?~! BankNotFound
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
      apiVersion,
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
      List(apiTagAccount)
    )
  
  
    lazy val createAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        user => {
          for {
            jsonBody <- tryo (json.extract[CreateAccountJSONV220]) ?~! InvalidJsonFormat
            bank <- Bank(bankId) ?~! BankNotFound
            loggedInUser <- user ?~! UserNotLoggedIn
            user_id <- tryo (if (jsonBody.user_id.nonEmpty) jsonBody.user_id else loggedInUser.userId) ?~! InvalidUserId
            isValidAccountIdFormat <- tryo(assert(isValidID(accountId.value)))?~! InvalidAccountIdFormat
            isValidBankId <- tryo(assert(isValidID(accountId.value)))?~! InvalidBankIdFormat
            postedOrLoggedInUser <- User.findByUserId(user_id) ?~! UserNotFoundById
            // User can create account for self or an account for another user if they have CanCreateAccount role
            isAllowed <- booleanToBox(hasEntitlement(bankId.value, loggedInUser.userId, CanCreateAccount) == true || (user_id == loggedInUser.userId) ,
              s"${UserHasMissingRoles} CanCreateAccount or create account for self")
            initialBalanceAsString <- tryo (jsonBody.balance.amount) ?~! InvalidAccountBalanceAmount
            accountType <- tryo(jsonBody.`type`) ?~! InvalidAccountType
            accountLabel <- tryo(jsonBody.label) //?~! ErrorMessages.InvalidAccountLabel
            initialBalanceAsNumber <- tryo {BigDecimal(initialBalanceAsString)} ?~! InvalidAccountInitialBalance
            isTrue <- booleanToBox(0 == initialBalanceAsNumber) ?~! InitialBalanceMustBeZero
            currency <- tryo (jsonBody.balance.currency) ?~!ErrorMessages.InvalidAccountBalanceCurrency
            accountDoesNotExist <- booleanToBox(BankAccount(bankId, accountId).isEmpty, AccountIdAlreadyExsits)
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
      apiVersion,
      "config",
      "GET",
      "/config",
      "The configuration of the API",
      """Returns information about:
        |
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
      Catalogs(Core, notPSD2, OBWG),
      apiTagApiInfo :: Nil)

    lazy val config : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "config" :: Nil JsonGet _ => user => for {
        u <- user ?~! ErrorMessages.UserNotLoggedIn
        _ <- booleanToBox(hasEntitlement("", u.userId, CanGetConfig), s"$UserHasMissingRoles $CanGetConfig")
      } yield {
        successJsonResponse(getConfigInfoJSON(), 200)
      }
    }



    resourceDocs += ResourceDoc(
      getConnectorMetrics,
      apiVersion,
      "getConnectorMetrics",
      "GET",
      "/management/connector/metrics",
      "Get Connector Metrics",
      """Get the all metrics
        |
        |require $CanGetConnectorMetrics role
        |
        |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/connector/metrics
        |
        |Should be able to filter on the following metrics fields
        |
        |eg: /management/connector/metrics?start_date=2017-03-01&end_date=2017-03-04&limit=50&offset=2
        |
        |1 start_date (defaults to one week before current date): eg:start_date=2017-03-01
        |
        |2 end_date (defaults to current date) eg:end_date=2017-03-05
        |
        |3 limit (for pagination: defaults to 200)  eg:limit=200
        |
        |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
        |
        |eg: /management/connector/metrics?start_date=2016-03-05&end_date=2017-03-08&limit=10000&offset=0&anon=false&app_name=hognwei&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&user_name=susan.uk.29@example.com&consumer_id=78
        |
        |Other filters:
        |
        |5 connector_name  (if null ignore)
        |
        |6 function_name (if null ignore)
        |
        |7 obp_api_request_id (if null ignore)
        |
      """.stripMargin,
      emptyObjectJson,
      connectorMetricsJson,
      List(
        InvalidDateFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      Nil)

    lazy val getConnectorMetrics : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "connector" :: "metrics" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanGetConnectorMetrics), s"$CanGetConnectorMetrics entitlement required")

            //Note: Filters Part 1:
            //?start_date=100&end_date=1&limit=200&offset=0

            inputDateFormat <- Full(new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH))
            // set the long,long ago as the default date.
            nowTime <- Full(System.currentTimeMillis())
            defaultStartDate <- Full(new Date(nowTime - (1000 * 60)).toInstant.toString)  // 1 minute ago
            _  <- tryo{println(defaultStartDate + "defaultStartDate")}
            defaultEndDate <- Full(new Date(nowTime).toInstant.toString)

            //(defaults to one week before current date
            startDate <- tryo(inputDateFormat.parse(S.param("start_date").getOrElse(defaultStartDate))) ?~!
              s"${InvalidDateFormat } start_date:${S.param("start_date").get }. Support format is yyyy-MM-dd"
            // defaults to current date
            endDate <- tryo(inputDateFormat.parse(S.param("end_date").getOrElse(defaultEndDate))) ?~!
              s"${InvalidDateFormat } end_date:${S.param("end_date").get }. Support format is yyyy-MM-dd"
            // default 1000, return 1000 items
            limit <- tryo(
              S.param("limit") match {
                case Full(l) if (l.toInt > 1000) => 1000
                case Full(l)                      => l.toInt
                case _                            => 1000
              }
            ) ?~!  s"${InvalidNumber } limit:${S.param("limit").get }"
            // default0, start from page 0
            offset <- tryo(S.param("offset").getOrElse("0").toInt) ?~!
              s"${InvalidNumber } offset:${S.param("offset").get }"

            metrics <- Full(ConnMetrics.metrics.vend.getAllConnectorMetrics(List(OBPLimit(limit), OBPOffset(offset), OBPFromDate(startDate), OBPToDate(endDate))))

            //Because of "rd.getDate().before(startDatePlusOneDay)" exclude the startDatePlusOneDay, so we need to plus one day more then today.
            // add because of endDate is yyyy-MM-dd format, it started from 0, so it need to add 2 days.
            //startDatePlusOneDay <- Full(inputDateFormat.parse((new Date(endDate.getTime + 1000 * 60 * 60 * 24 * 2)).toInstant.toString))

            ///filterByDate <- Full(metrics.toList.filter(rd => (rd.getDate().after(startDate)) && (rd.getDate().before(startDatePlusOneDay))))

            /** pages:
              * eg: total=79
              * offset=0, limit =50
              *  filterByDate.slice(0,50)
              * offset=1, limit =50
              *  filterByDate.slice(50*1,50+50*1)--> filterByDate.slice(50,100)
              * offset=2, limit =50
              *  filterByDate.slice(50*2,50+50*2)-->filterByDate.slice(100,150)
              */
            //filterByPages <- Full(filterByDate.slice(offset * limit, (offset * limit + limit)))

            //Filters Part 2.
            //eg: /management/metrics?start_date=100&end_date=1&limit=200&offset=0
            //    &user_id=c7b6cb47-cb96-4441-8801-35b57456753a&consumer_id=78&app_name=hognwei&implemented_in_version=v2.1.0&verb=GET&anon=true
            // consumer_id (if null ignore)
            // user_id (if null ignore)
            // anon true => return where user_id is null. false => return where where user_id is not null(if null ignore)
            // url (if null ignore)
            // app_name (if null ignore)
            // implemented_by_partial_function (if null ignore)
            // implemented_in_version (if null ignore)
            // verb (if null ignore)
            connectorName <- Full(S.param("connector_name")) //(if null ignore)
            functionName <- Full(S.param("function_name")) //(if null ignore)
            obpApiRequestId <- Full(S.param("obp_api_request_id")) // (if null ignore) true => return where user_id is null.false => return where user_id is not null.


            filterByFields: List[ConnectorMetric] = metrics
              .filter(rd => (if (!connectorName.isEmpty) rd.getConnectorName().equals(connectorName.get) else true))
              .filter(rd => (if (!functionName.isEmpty) rd.getFunctionName().equals(functionName.get) else true))
              //TODO url can not contain '&', if url is /management/metrics?start_date=100&end_date=1&limit=200&offset=0, it can not work.
              .filter(i => (if (!obpApiRequestId.isEmpty) i.getCorrelationId().equals(obpApiRequestId.get) else true))
          } yield {
            val json = JSONFactory220.createConnectorMetricsJson(filterByFields)
            successJsonResponse(Extraction.decompose(json)(DateFormatWithCurrentTimeZone))
          }
        }
      }
    }


    resourceDocs += ResourceDoc(
      createConsumer,
      apiVersion,
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
      Nil)


    lazy val createConsumer: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "management" :: "consumers" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! UserNotLoggedIn
            _ <- booleanToBox(hasEntitlement("", u.userId, ApiRole.CanCreateConsumer), UserHasMissingRoles + CanCreateConsumer )
            postedJson <- tryo {json.extract[ConsumerPostJSON]} ?~! InvalidJsonFormat
            consumer <- Consumers.consumers.vend.createConsumer(Some(UUID.randomUUID().toString),
                                                                Some(UUID.randomUUID().toString),
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


  }
}

object APIMethods220 {
}
