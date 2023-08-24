package code.api.v5_1_0


import code.api.Constant
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{apiCollectionJson400, apiCollectionsJson400, apiInfoJson400, postApiCollectionJson400, revokedConsentJsonV310, _}
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, BankNotFound, ConsentNotFound, InvalidJsonFormat, UnknownError, UserNotFoundByUserId, UserNotLoggedIn, _}
import code.api.util.FutureUtil.{EndpointContext, EndpointTimeout}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200}
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAggregateMetricJson
import code.api.v3_1_0.ConsentJsonV310
import code.api.v3_1_0.JSONFactory310.createBadLoginStatusJson
import code.api.v4_0_0.{JSONFactory400, PostApiCollectionJson400}
import code.api.v5_0_0.ConsentJsonV500
import code.atmattribute.AtmAttribute
import code.bankconnectors.Connector
import code.consent.Consents
import code.loginattempts.LoginAttempt
import code.metrics.APIMetrics
import code.model.dataAccess.MappedBankAccount
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.userlocks.UserLocksProvider
import code.users.Users
import code.util.Helper
import code.views.Views
import code.views.system.{AccountAccess, ViewDefinition}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.CustomerAndAttribute
import com.openbankproject.commons.model.enums.{AtmAttributeType, UserAttributeType}
import com.openbankproject.commons.model.{AtmId, AtmT, BankId, Permission}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.{Box, Full}
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.parse
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


trait APIMethods510 {
  self: RestHelper =>

  val Implementations5_1_0 = new Implementations510()

  class Implementations510 {

    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_1_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)



    staticResourceDocs += ResourceDoc(
      root(OBPAPI5_1_0.version, OBPAPI5_1_0.versionStatus),
      implementedInApiVersion,
      "root",
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Hosted at information
        |* Energy source information
        |* Git Commit""",
      EmptyBody,
      apiInfoJson400,
      List(UnknownError, "no connector set"),
      apiTagApi  :: Nil)

    def root (apiVersion : ApiVersion, apiVersionStatus: String) : OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
            (JSONFactory510.getApiInfoJSON(apiVersion,apiVersionStatus), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      waitingForGodot,
      implementedInApiVersion,
      nameOf(waitingForGodot),
      "GET",
      "/waiting-for-godot",
      "Waiting For Godot",
      """Waiting For Godot
        |
        |Uses query parameter "sleep" in milliseconds.
        |For instance: .../waiting-for-godot?sleep=50 means postpone response in 50 milliseconds.
        |""".stripMargin,
      EmptyBody,
      WaitingForGodotJsonV510(sleep_in_milliseconds = 50),
      List(UnknownError, "no connector set"),
      apiTagApi  :: Nil)

    lazy val waitingForGodot: OBPEndpoint = {
      case "waiting-for-godot" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          implicit val timeout = EndpointTimeout(Constant.mediumEndpointTimeoutInMillis) // Set endpoint timeout explicitly
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            sleep: String = httpParams.filter(_.name == "sleep").headOption
              .map(_.values.headOption.getOrElse("0")).getOrElse("0")
            sleepInMillis: Long = tryo(sleep.trim.toLong).getOrElse(0)
            _ <- Future(Thread.sleep(sleepInMillis))
          } yield {
            (JSONFactory510.waitingForGodot(sleepInMillis), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getAllApiCollections,
      implementedInApiVersion,
      nameOf(getAllApiCollections),
      "GET",
      "/management/api-collections",
      "Get All API Collections",
      s"""Get All API Collections.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApiCollection),
      Some(canGetAllApiCollections :: Nil)
    )

    lazy val getAllApiCollections: OBPEndpoint = {
      case "management" :: "api-collections" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (apiCollections, callContext) <- NewStyle.function.getAllApiCollections(cc.callContext)
          } yield {
            (JSONFactory400.createApiCollectionsJsonV400(apiCollections), HttpCode.`200`(callContext))
          }
      }
    }
    staticResourceDocs += ResourceDoc(
      createNonPersonalUserAttribute,
      implementedInApiVersion,
      nameOf(createNonPersonalUserAttribute),
      "POST",
      "/users/USER_ID/non-personal/attributes",
      "Create Non Personal User Attribute",
      s""" Create Non Personal User Attribute
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      userAttributeJsonV510,
      userAttributeResponseJsonV510,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canCreateNonPersonalUserAttribute))
    )

    lazy val createNonPersonalUserAttribute: OBPEndpoint = {
      case "users" :: userId ::"non-personal":: "attributes" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $UserAttributeJsonV510 "
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userId, cc.callContext)
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[UserAttributeJsonV510]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${UserAttributeType.DOUBLE}(12.1234), ${UserAttributeType.STRING}(TAX_NUMBER), ${UserAttributeType.INTEGER} (123)and ${UserAttributeType.DATE_WITH_DAY}(2012-04-23)"
            userAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              UserAttributeType.withName(postedData.`type`)
            }
            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId,
              None,
              postedData.name,
              userAttributeType,
              postedData.value,
              false,
              callContext
              )
          } yield {
            (JSONFactory510.createUserAttributeJson(userAttribute), HttpCode.`201`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      deleteNonPersonalUserAttribute,
      implementedInApiVersion,
      nameOf(deleteNonPersonalUserAttribute),
      "DELETE",
      "/users/USER_ID/non-personal/attributes/USER_ATTRIBUTE_ID",
      "Delete Non Personal User Attribute",
      s"""Delete the Non Personal User Attribute specified by ENTITLEMENT_REQUEST_ID for a user specified by USER_ID
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidConnectorResponse,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canDeleteNonPersonalUserAttribute)))

    lazy val deleteNonPersonalUserAttribute: OBPEndpoint = {
      case "users" :: userId :: "non-personal" :: "attributes" :: userAttributeId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
            (deleted,callContext) <- Connector.connector.vend.deleteUserAttribute(
              userAttributeId: String,
              callContext: Option[CallContext]
            ) map {
            i => (connectorEmptyResponse (i._1, callContext), i._2)
          }
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getNonPersonalUserAttributes,
      implementedInApiVersion,
      nameOf(getNonPersonalUserAttributes),
      "GET",
      "/users/USER_ID/non-personal/attributes",
      "Get Non Personal User Attributes",
      s"""Get Non Personal User Attribute for a user specified by USER_ID
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidConnectorResponse,
        UnknownError
      ),
      List(apiTagUser),
      Some(List(canGetNonPersonalUserAttributes)))

    lazy val getNonPersonalUserAttributes: OBPEndpoint = {
      case "users" :: userId :: "non-personal" ::"attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- authenticatedAccess(cc)
            (user, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
            (userAttributes,callContext) <- NewStyle.function.getNonPersonalUserAttributes(
              user.userId,
              callContext,
            ) 
          } yield {
            (JSONFactory510.createUserAttributesJson(userAttributes), HttpCode.`200`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      getEntitlementsAndPermissions,
      implementedInApiVersion,
      "getEntitlementsAndPermissions",
      "GET",
      "/users/USER_ID/entitlements-and-permissions",
      "Get Entitlements and Permissions for a User",
      s"""
         |
         |
      """.stripMargin,
      EmptyBody,
      userJsonV300,
      List(
        $UserNotLoggedIn,
        UserNotFoundByUserId, 
        UserHasMissingRoles, 
        UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)))


    lazy val getEntitlementsAndPermissions: OBPEndpoint = {
      case "users" :: userId :: "entitlements-and-permissions" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (user, callContext) <- NewStyle.function.getUserByUserId(userId, cc.callContext)
            entitlements <- NewStyle.function.getEntitlementsByUserId(userId, callContext)
          } yield {
            val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(user).toOption
            (JSONFactory300.createUserInfoJSON (user, entitlements, permissions), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    staticResourceDocs += ResourceDoc(
      customViewNamesCheck,
      implementedInApiVersion,
      nameOf(customViewNamesCheck),
      "GET",
      "/management/system/integrity/custom-view-names-check",
      "Check Custom View Names",
      s"""Check custom view names.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil)
    )

    lazy val customViewNamesCheck: OBPEndpoint = {
      case "management" :: "system" :: "integrity" :: "custom-view-names-check" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            incorrectViews: List[ViewDefinition] <- Future {
              ViewDefinition.getCustomViews().filter { view =>
                view.viewId.value.startsWith("_") == false
              }
            }
          } yield {
            (JSONFactory510.getCustomViewNamesCheck(incorrectViews), HttpCode.`200`(cc.callContext))
          }
      }
    }    
    staticResourceDocs += ResourceDoc(
      systemViewNamesCheck,
      implementedInApiVersion,
      nameOf(systemViewNamesCheck),
      "GET",
      "/management/system/integrity/system-view-names-check",
      "Check System View Names",
      s"""Check system view names.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil)
    )

    lazy val systemViewNamesCheck: OBPEndpoint = {
      case "management" :: "system" :: "integrity" :: "system-view-names-check" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            incorrectViews: List[ViewDefinition] <- Future {
              ViewDefinition.getSystemViews().filter { view =>
                view.viewId.value.startsWith("_") == true
              }
            }
          } yield {
            (JSONFactory510.getSystemViewNamesCheck(incorrectViews), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      accountAccessUniqueIndexCheck,
      implementedInApiVersion,
      nameOf(accountAccessUniqueIndexCheck),
      "GET",
      "/management/system/integrity/account-access-unique-index-1-check",
      "Check Unique Index at Account Access",
      s"""Check unique index at account access table.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil)
    )

    lazy val accountAccessUniqueIndexCheck: OBPEndpoint = {
      case "management" :: "system" :: "integrity" :: "account-access-unique-index-1-check" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            groupedRows: Map[String, List[AccountAccess]] <- Future {
              AccountAccess.findAll().groupBy { a => 
                s"${a.bank_id.get}-${a.account_id.get}-${a.view_id.get}-${a.user_fk.get}-${a.consumer_id.get}"
              }.filter(_._2.size > 1) // Extract only duplicated rows
            }
          } yield {
            (JSONFactory510.getAccountAccessUniqueIndexCheck(groupedRows), HttpCode.`200`(cc.callContext))
          }
      }
    }    
    staticResourceDocs += ResourceDoc(
      accountCurrencyCheck,
      implementedInApiVersion,
      nameOf(accountCurrencyCheck),
      "GET",
      "/management/system/integrity/banks/BANK_ID/account-currency-check",
      "Check for Sensible Currencies",
      s"""Check for sensible currencies at Bank Account model
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil)
    )

    lazy val accountCurrencyCheck: OBPEndpoint = {
      case "management" :: "system" :: "integrity"  :: "banks" :: BankId(bankId) :: "account-currency-check" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            currencies: List[String] <- Future {
              MappedBankAccount.findAll().map(_.accountCurrency.get).distinct
            }
            (bankCurrencies, callContext) <- NewStyle.function.getCurrentCurrencies(bankId, cc.callContext)
          } yield {
            (JSONFactory510.getSensibleCurrenciesCheck(bankCurrencies, currencies), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getCurrenciesAtBank,
      implementedInApiVersion,
      nameOf(getCurrenciesAtBank),
      "GET",
      "/banks/BANK_ID/currencies",
      "Get Currencies at a Bank",
      """Get Currencies specified by BANK_ID
        |
      """.stripMargin,
      emptyObjectJson,
      currenciesJsonV510,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagFx)
    )

    lazy val getCurrenciesAtBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "currencies" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Helper.booleanToFuture(failMsg = ConsumerHasMissingRoles + CanReadFx, cc=cc.callContext) {
              checkScope(bankId.value, getConsumerPrimaryKey(cc.callContext), ApiRole.canReadFx)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
            (currencies, callContext) <- NewStyle.function.getCurrentCurrencies(bankId, callContext)
          } yield {
            val json = CurrenciesJsonV510(currencies.map(CurrencyJsonV510(_)))
            (json, HttpCode.`200`(callContext))
          }

      }
    }


    staticResourceDocs += ResourceDoc(
      orphanedAccountCheck,
      implementedInApiVersion,
      nameOf(orphanedAccountCheck),
      "GET",
      "/management/system/integrity/banks/BANK_ID/orphaned-account-check",
      "Check for Orphaned Accounts",
      s"""Check for orphaned accounts at Bank Account model
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil)
    )

    lazy val orphanedAccountCheck: OBPEndpoint = {
      case "management" :: "system" :: "integrity"  :: "banks" :: BankId(bankId) :: "orphaned-account-check" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            accountAccesses: List[String] <- Future {
              AccountAccess.findAll(By(AccountAccess.bank_id, bankId.value)).map(_.account_id.get)
            }
            bankAccounts <- Future {
              MappedBankAccount.findAll(By(MappedBankAccount.bank, bankId.value)).map(_.accountId.value)
            }
          } yield {
            val orphanedAccounts: List[String] = accountAccesses.filterNot { accountAccess =>
              bankAccounts.contains(accountAccess)
            }
            (JSONFactory510.getOrphanedAccountsCheck(orphanedAccounts), HttpCode.`200`(cc.callContext))
          }
      }
    }








    staticResourceDocs += ResourceDoc(
      createAtmAttribute,
      implementedInApiVersion,
      nameOf(createAtmAttribute),
      "POST",
      "/banks/BANK_ID/atms/ATM_ID/attributes",
      "Create ATM Attribute",
      s""" Create ATM Attribute
         |
         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      atmAttributeJsonV510,
      atmAttributeResponseJsonV510,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canCreateAtmAttribute, canCreateAtmAttributeAtAnyBank))
    )

    lazy val createAtmAttribute : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "attributes" :: Nil JsonPost json -> _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AtmAttributeJsonV510 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AtmAttributeJsonV510]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AtmAttributeType.DOUBLE}(12.1234), ${AtmAttributeType.STRING}(TAX_NUMBER), ${AtmAttributeType.INTEGER}(123) and ${AtmAttributeType.DATE_WITH_DAY}(2012-04-23)"
            bankAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              AtmAttributeType.withName(postedData.`type`)
            }
            (atmAttribute, callContext) <- NewStyle.function.createOrUpdateAtmAttribute(
              bankId,
              atmId,
              None,
              postedData.name,
              bankAttributeType,
              postedData.value,
              postedData.is_active,
              callContext: Option[CallContext]
            )
          } yield {
            (JSONFactory510.createAtmAttributeJson(atmAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAtmAttributes,
      implementedInApiVersion,
      nameOf(getAtmAttributes),
      "GET",
      "/banks/BANK_ID/atms/ATM_ID/attributes",
      "Get ATM Attributes",
      s""" Get ATM Attributes
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      atmAttributesResponseJsonV510,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canGetAtmAttribute, canGetAtmAttributeAtAnyBank))
    )

    lazy val getAtmAttributes : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "attributes" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (attributes, callContext) <- NewStyle.function.getAtmAttributesByAtm(bankId, atmId, callContext)
          } yield {
            (JSONFactory510.createAtmAttributesJson(attributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getAtmAttribute,
      implementedInApiVersion,
      nameOf(getAtmAttribute),
      "GET",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID",
      "Get ATM Attribute By ATM_ATTRIBUTE_ID",
      s""" Get ATM Attribute By ATM_ATTRIBUTE_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      atmAttributeResponseJsonV510,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canGetAtmAttribute, canGetAtmAttributeAtAnyBank))
    )

    lazy val getAtmAttribute : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "attributes" :: atmAttributeId :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (attribute, callContext) <- NewStyle.function.getAtmAttributeById(atmAttributeId, callContext)
          } yield {
            (JSONFactory510.createAtmAttributeJson(attribute), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      updateAtmAttribute,
      implementedInApiVersion,
      nameOf(updateAtmAttribute),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID",
      "Update ATM Attribute",
      s""" Update ATM Attribute. 
         |
         |Update an ATM Attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      atmAttributeJsonV510,
      atmAttributeResponseJsonV510,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canUpdateAtmAttribute, canUpdateAtmAttributeAtAnyBank))
    )

    lazy val updateAtmAttribute : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "attributes" :: atmAttributeId :: Nil JsonPut json -> _ =>{
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AtmAttributeJsonV510 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AtmAttributeJsonV510]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AtmAttributeType.DOUBLE}(12.1234), ${AtmAttributeType.STRING}(TAX_NUMBER), ${AtmAttributeType.INTEGER}(123) and ${AtmAttributeType.DATE_WITH_DAY}(2012-04-23)"
            atmAttributeType <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              AtmAttributeType.withName(postedData.`type`)
            }
            (_, callContext) <- NewStyle.function.getAtmAttributeById(atmAttributeId, cc.callContext)
            (atmAttribute, callContext) <- NewStyle.function.createOrUpdateAtmAttribute(
              bankId,
              atmId,
              Some(atmAttributeId),
              postedData.name,
              atmAttributeType,
              postedData.value,
              postedData.is_active,
              callContext: Option[CallContext]
            )
          } yield {
            (JSONFactory510.createAtmAttributeJson(atmAttribute), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      deleteAtmAttribute,
      implementedInApiVersion,
      nameOf(deleteAtmAttribute),
      "DELETE",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID",
      "Delete ATM Attribute",
      s""" Delete ATM Attribute
         |
         |Delete a Atm Attribute by its id.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canDeleteAtmAttribute, canDeleteAtmAttributeAtAnyBank))
    )

    lazy val deleteAtmAttribute : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: "attributes" :: atmAttributeId ::  Nil JsonDelete _=> {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (atmAttribute, callContext) <- NewStyle.function.deleteAtmAttribute(atmAttributeId, callContext)
          } yield {
            (Full(atmAttribute), HttpCode.`204`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getConsentByConsentId,
      implementedInApiVersion,
      nameOf(getConsentByConsentId),
      "GET",
      "/consumer/consents/CONSENT_ID",
      "Get Consent By Consent Id",
      s"""
         |
         |This endpoint gets the Consent By consent id.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentJsonV500,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2))
    lazy val getConsentByConsentId: OBPEndpoint = {
      case "consumer" :: "consents" :: consentId :: Nil  JsonGet _  => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            consent <- Future { Consents.consentProvider.vend.getConsentByConsentId(consentId)} map {
              unboxFullOrFail(_, cc.callContext, ConsentNotFound)
            }
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, cc = cc.callContext) {
              consent.mUserId == cc.userId
            }
          } yield {
            (JSONFactory510.getConsentInfoJson(consent), HttpCode.`200`(cc))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      revokeConsentAtBank,
      implementedInApiVersion,
      nameOf(revokeConsentAtBank),
      "DELETE",
      "/banks/BANK_ID/consents/CONSENT_ID",
      "Revoke Consent at Bank",
      s"""
         |Revoke Consent specified by CONSENT_ID
         |
         |There are a few reasons you might need to revoke an application’s access to a user’s account:
         |  - The user explicitly wishes to revoke the application’s access
         |  - You as the service provider have determined an application is compromised or malicious, and want to disable it
         |  - etc.
         ||
         |OBP as a resource server stores access tokens in a database, then it is relatively easy to revoke some token that belongs to a particular user.
         |The status of the token is changed to "REVOKED" so the next time the revoked client makes a request, their token will fail to validate.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      revokedConsentJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2), 
      Some(List(canRevokeConsentAtBank))
    )

    lazy val revokeConsentAtBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents" :: consentId :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              unboxFullOrFail(_, callContext, ConsentNotFound)
            }
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, cc=callContext) {
              consent.mUserId == user.userId
            }
            consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), HttpCode.`200`(callContext))
          }
      }
    }
    
   staticResourceDocs += ResourceDoc(
     selfRevokeConsent,
      implementedInApiVersion,
      nameOf(selfRevokeConsent),
      "DELETE",
      "/my/consent/current",
      "Revoke Consent used in the Current Call",
      s"""
         |Revoke Consent specified by Consent-Id at Request Header
         |
         |There are a few reasons you might need to revoke an application’s access to a user’s account:
         |  - The user explicitly wishes to revoke the application’s access
         |  - You as the service provider have determined an application is compromised or malicious, and want to disable it
         |  - etc.
         ||
         |OBP as a resource server stores access tokens in a database, then it is relatively easy to revoke some token that belongs to a particular user.
         |The status of the token is changed to "REVOKED" so the next time the revoked client makes a request, their token will fail to validate.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      revokedConsentJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2)
    )
    lazy val selfRevokeConsent: OBPEndpoint = {
      case "my" :: "consent" :: "current" :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            consentId = getConsentIdRequestHeaderValue(cc.requestHeaders).getOrElse("")
            _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              unboxFullOrFail(_, callContext, ConsentNotFound)
            }
            consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), HttpCode.`200`(callContext))
          }
      }
    }
    
    
   staticResourceDocs += ResourceDoc(
     mtlsClientCertificateInfo,
      implementedInApiVersion,
      nameOf(mtlsClientCertificateInfo),
      "GET",
      "/my/mtls/certificate/current",
      "Provide client's certificate info of a current call",
      s"""
         |Provide client's certificate info of a current call specified by PSD2-CERT value at Request Header
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      certificateInfoJsonV510,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2)
    )
    lazy val mtlsClientCertificateInfo: OBPEndpoint = {
      case "my" :: "mtls" :: "certificate" :: "current" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(_), callContext) <- authenticatedAccess(cc)
            info <- Future(X509.getCertificateInfo(APIUtil.`getPSD2-CERT`(cc.requestHeaders))) map {
              unboxFullOrFail(_, callContext, X509GeneralError)
            }
          } yield {
            (info, HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      updateMyApiCollection,
      implementedInApiVersion,
      nameOf(updateMyApiCollection),
      "PUT",
      "/my/api-collections/API_COLLECTION_ID",
      "Update My Api Collection By API_COLLECTION_ID",
      s"""Update Api Collection for logged in user.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      postApiCollectionJson400,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection)
    )

    lazy val updateMyApiCollection: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionId :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionJson400]
            }
            (_, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, cc.callContext)
            (apiCollection, callContext) <- NewStyle.function.updateApiCollection(
              apiCollectionId,
              putJson.api_collection_name,
              putJson.is_sharable,
              putJson.description.getOrElse(""),
              callContext
            )
          } yield {
            (JSONFactory400.createApiCollectionJsonV400(apiCollection), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getUserByProviderAndUsername,
      implementedInApiVersion,
      nameOf(getUserByProviderAndUsername),
      "GET",
      "/users/provider/PROVIDER/username/USERNAME",
      "Get User by USERNAME",
      s"""Get user by PROVIDER and USERNAME
         |
         |${authenticationRequiredMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      userJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByProviderAndUsername, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser))
    )
    
    lazy val getUserByProviderAndUsername: OBPEndpoint = {
      case "users" :: "provider" :: provider :: "username" :: username :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            user <- Users.users.vend.getUserByProviderAndUsernameFuture(provider, username) map {
              x => unboxFullOrFail(x, cc.callContext, UserNotFoundByProviderAndUsername, 404)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
            isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
          } yield {
            (JSONFactory400.createUserInfoJSON(user, entitlements, None, isLocked), HttpCode.`200`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getUserLockStatus,
      implementedInApiVersion,
      nameOf(getUserLockStatus),
      "GET",
      "/users/PROVIDER/USERNAME/lock-status",
      "Get User Lock Status",
      s"""
         |Get User Login Status.
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      badLoginStatusJson,
      List(UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canReadUserLockedStatus))
    )
    lazy val getUserLockStatus: OBPEndpoint = {
      //get private accounts for all banks
      case "users" ::provider :: username :: "lock-status" :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadUserLockedStatus, callContext)
            _ <- Users.users.vend.getUserByProviderAndUsernameFuture(provider, username) map {
              x => unboxFullOrFail(x, callContext, UserNotFoundByProviderAndUsername, 404)
            }
            badLoginStatus <- Future {
              LoginAttempt.getOrCreateBadLoginStatus(provider, username)
            } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404)
            }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      unlockUserByProviderAndUsername,
      implementedInApiVersion,
      nameOf(unlockUserByProviderAndUsername),
      "PUT",
      "/users/PROVIDER/USERNAME/lock-status",
      "Unlock the user",
      s"""
         |Unlock a User.
         |
         |(Perhaps the user was locked due to multiple failed login attempts)
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      badLoginStatusJson,
      List(UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canUnlockUser)))
    lazy val unlockUserByProviderAndUsername: OBPEndpoint = {
      //get private accounts for all banks
      case "users" ::  provider :: username :: "lock-status" :: Nil JsonPut req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUnlockUser, callContext)
            _ <- Users.users.vend.getUserByProviderAndUsernameFuture(provider, username) map {
              x => unboxFullOrFail(x, callContext, UserNotFoundByProviderAndUsername, 404)
            }
            _ <- Future {
              LoginAttempt.resetBadLoginAttempts(provider, username)
            }
            _ <- Future {
              UserLocksProvider.unlockUser(provider, username)
            }
            badLoginStatus <- Future {
              LoginAttempt.getOrCreateBadLoginStatus(provider, username)
            } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404)
            }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      lockUserByProviderAndUsername,
      implementedInApiVersion,
      nameOf(lockUserByProviderAndUsername),
      "POST",
      "/users/PROVIDER/USERNAME/locks",
      "Lock the user",
      s"""
         |Lock a User.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      userLockStatusJson,
      List($UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canLockUser)))
    lazy val lockUserByProviderAndUsername: OBPEndpoint = {
      case "users" :: provider :: username :: "locks" :: Nil JsonPost req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- SS.user
            userLocks <- Future {
              UserLocksProvider.lockUser(provider, username)
            } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404)
            }
          } yield {
            (JSONFactory400.createUserLockStatusJson(userLocks), HttpCode.`200`(callContext))
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
         |12 include_app_names (if null ignore).eg: &include_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |13 include_url_patterns (if null ignore).you can design you own SQL LIKE pattern. eg: &include_url_patterns=%management/metrics%,%management/aggregate-metrics%
         |
         |14 include_implemented_by_partial_functions (if null ignore).eg: &include_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      aggregateMetricsJSONV300,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMetric, apiTagAggregateMetrics),
      Some(List(canReadAggregateMetrics)))

    lazy val getAggregateMetrics: OBPEndpoint = {
      case "management" :: "aggregate-metrics" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadAggregateMetrics, callContext)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
            aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams,true) map {
              x => unboxFullOrFail(x, callContext, GetAggregateMetricsError)
            }
          } yield {
            (createAggregateMetricJson(aggregateMetrics), HttpCode.`200`(callContext))
          }
        }

      }
    }



    staticResourceDocs += ResourceDoc(
      getCustomersForUserIdsOnly,
      implementedInApiVersion,
      nameOf(getCustomersForUserIdsOnly),
      "GET",
      "/users/current/customers/customer_ids",
      "Get Customers for Current User (IDs only)",
      s"""Gets all Customers Ids that are linked to a User.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customersWithAttributesJsonV300,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser)
    )
    
    lazy val getCustomersForUserIdsOnly : OBPEndpoint = {
      case "users" :: "current" :: "customers" :: "customer_ids" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (customers, callContext) <- Connector.connector.vend.getCustomersByUserId(cc.userId, cc.callContext) map {
              connectorEmptyResponse(_, cc.callContext)
            }
          } yield {
            (JSONFactory510.createCustomersIds(customers), HttpCode.`200`(callContext))
          }
        }
      }
    }
    

    staticResourceDocs += ResourceDoc(
      createAtm,
      implementedInApiVersion,
      nameOf(createAtm),
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM.""",
      postAtmJsonV510,
      atmJsonV510,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canCreateAtm, canCreateAtmAtAnyBank))
    )
    lazy val createAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonPost json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            atmJsonV510 <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV510]}", 400, cc.callContext) {
              val atm = json.extract[PostAtmJsonV510]
              //Make sure the Create contains proper ATM ID
              atm.id.get
              atm
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, cc.callContext) {
              atmJsonV510.bank_id == bankId.value
            }
            atm <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Atm", 400, cc.callContext) {
              JSONFactory510.transformToAtmFromV510(atmJsonV510)
            }
            (atm, callContext) <- NewStyle.function.createOrUpdateAtm(atm, cc.callContext)
            (atmAttributes, callContext) <- NewStyle.function.getAtmAttributesByAtm(bankId, atm.atmId, callContext)
          } yield {
            (JSONFactory510.createAtmJsonV510(atm, atmAttributes), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateAtm,
      implementedInApiVersion,
      nameOf(updateAtm),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID",
      "UPDATE ATM",
      s"""Update ATM.""",
      atmJsonV510.copy(id = None, attributes = None),
      atmJsonV510,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canUpdateAtm, canUpdateAtmAtAnyBank))
    )
    lazy val updateAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonPut json -> _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            atmJsonV510 <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV510]}", 400, callContext) {
              json.extract[AtmJsonV510]
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, callContext) {
              atmJsonV510.bank_id == bankId.value
            }
            atm <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Atm", 400, callContext) {
              JSONFactory510.transformToAtmFromV510(atmJsonV510.copy(id = Some(atmId.value)))
            }
            (atm, callContext) <- NewStyle.function.createOrUpdateAtm(atm, callContext)
            (atmAttributes, callContext) <- NewStyle.function.getAtmAttributesByAtm(bankId, atm.atmId, callContext)
          } yield {
            (JSONFactory510.createAtmJsonV510(atm, atmAttributes), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
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
         |${authenticationRequiredMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmsJsonV510,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagATM)
    )
    lazy val getAtms: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonGet _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          val limit = S.param("limit")
          val offset = S.param("offset")
          for {
            (_, callContext) <- getAtmsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            _ <- Helper.booleanToFuture(failMsg = s"${InvalidNumber} limit:${limit.getOrElse("")}", cc = callContext) {
              limit match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c) == true)
                case _ => true
              }
            }
            _ <- Helper.booleanToFuture(failMsg = maximumLimitExceeded, cc = callContext) {
              limit match {
                case Full(i) if i.toInt > 10000 => false
                case _ => true
              }
            }
            (atms, callContext) <- NewStyle.function.getAtmsByBankId(bankId, offset, limit, callContext)

            atmAndAttributesTupleList: List[(AtmT, List[AtmAttribute])] <-  Future.sequence(atms.map(
              atm => NewStyle.function.getAtmAttributesByAtm(bankId, atm.atmId, callContext).map(_._1).map(
                attributes =>{
                   (atm-> attributes)
                }
              )))
            
          } yield {
            (JSONFactory510.createAtmsJsonV510(atmAndAttributesTupleList), HttpCode.`200`(callContext))
          }
      }
    }


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
         |* ATM Attributes
         |
         |
         |
         |${authenticationRequiredMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmJsonV510,
      List(UserNotLoggedIn, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      List(apiTagATM)
    )
    lazy val getAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonGet req => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (_, callContext) <- getAtmsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, callContext)
            (atmAttributes, callContext) <- NewStyle.function.getAtmAttributesByAtm(bankId, atmId, callContext)
          } yield {
            (JSONFactory510.createAtmJsonV510(atm, atmAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteAtm,
      implementedInApiVersion,
      nameOf(deleteAtm),
      "DELETE",
      "/banks/BANK_ID/atms/ATM_ID",
      "Delete ATM",
      s"""Delete ATM.
         |
         |This will also delete all its attributes.
         |
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagATM),
      Some(List(canDeleteAtmAtAnyBank, canDeleteAtm))
    )
    lazy val deleteAtm: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: AtmId(atmId) :: Nil JsonDelete _ => {
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (atm, callContext) <- NewStyle.function.getAtm(bankId, atmId, cc.callContext)
            (deleted, callContext) <- NewStyle.function.deleteAtm(atm, callContext)
            (atmAttributes, callContext) <- NewStyle.function.deleteAtmAttributesByAtmId(atmId, callContext) 
          } yield {
            (Full(deleted && atmAttributes), HttpCode.`204`(callContext))
          }
      }
    }

  }
}

object APIMethods510 extends RestHelper with APIMethods510 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_1_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

