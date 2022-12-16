package code.api.util


import java.util.Date
import java.util.UUID.randomUUID
import akka.http.scaladsl.model.HttpMethod
import code.DynamicEndpoint.{DynamicEndpointProvider, DynamicEndpointT}
import code.api.{APIFailureNewStyle, Constant, JsonResponseException}
import code.api.Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ApiRole.canCreateAnyTransactionRequest
import code.api.util.ErrorMessages.{InsufficientAuthorisationToCreateTransactionRequest, _}
import code.api.ResourceDocs1_4_0.ResourceDocs140.ImplementationsResourceDocs
import code.api.v1_2_1.OBPAPI1_2_1.Implementations1_2_1
import code.api.v1_4_0.OBPAPI1_4_0.Implementations1_4_0
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_1_0.OBPAPI2_1_0.Implementations2_1_0
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.authtypevalidation.{AuthenticationTypeValidationProvider, JsonAuthTypeValidation}
import code.bankconnectors.Connector
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import code.consumer.Consumers
import com.openbankproject.commons.model.DirectDebitTrait
import code.dynamicEntity.{DynamicEntityProvider, DynamicEntityT}
import code.entitlement.Entitlement
import code.entitlementrequest.EntitlementRequest
import code.fx.{MappedFXRate, fx}
import com.openbankproject.commons.model.FXRate
import code.metadata.counterparties.Counterparties
import code.methodrouting.{MethodRoutingCommons, MethodRoutingProvider, MethodRoutingT}
import code.model._
import code.apicollectionendpoint.{ApiCollectionEndpointTrait, MappedApiCollectionEndpointsProvider}
import code.apicollection.{ApiCollectionTrait, MappedApiCollectionsProvider}
import code.model.dataAccess.{AuthUser, BankAccountRouting}
import code.standingorders.StandingOrderTrait
import code.usercustomerlinks.UserCustomerLink
import code.users.{UserAgreement, UserAgreementProvider, UserAttribute, UserInvitation, UserInvitationProvider, Users}
import code.util.Helper
import com.openbankproject.commons.util.{ApiVersion, JsonUtils}
import code.views.Views
import code.webhook.AccountWebhook
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.dto.{CustomerAndAttribute, ProductCollectionItemsTree}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.model.{AccountApplication, Bank, Customer, CustomerAddress, Product, ProductCollection, ProductCollectionItem, TaxResidence, UserAuthContext, UserAuthContextUpdate, _}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Failure, Full, ParamFailure}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{JField, JInt, JNothing, JNull, JObject, JString, JValue, _}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import java.security.AccessControlException

import scala.collection.immutable.{List, Nil}
import scala.concurrent.Future
import scala.math.BigDecimal
import scala.reflect.runtime.universe.MethodSymbol
import code.validation.{JsonSchemaValidationProvider, JsonValidation}
import net.liftweb.http.JsonResponse
import net.liftweb.util.Props
import code.api.JsonResponseException
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.v4_0_0.JSONFactory400
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo}
import code.bankattribute.BankAttribute
import code.connectormethod.{ConnectorMethodProvider, JsonConnectorMethod}
import code.customeraccountlinks.CustomerAccountLinkTrait
import code.dynamicMessageDoc.{DynamicMessageDocProvider, JsonDynamicMessageDoc}
import code.dynamicResourceDoc.{DynamicResourceDocProvider, JsonDynamicResourceDoc}
import code.endpointMapping.{EndpointMappingProvider, EndpointMappingT}
import code.endpointTag.EndpointTagT
import code.util.Helper.MdcLoggable
import code.views.system.AccountAccess
import net.liftweb.mapper.By

object NewStyle extends MdcLoggable{
  lazy val endpoints: List[(String, String)] = List(
    (nameOf(ImplementationsResourceDocs.getResourceDocsObp), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations1_2_1.deleteWhereTagForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getOtherAccountForTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getOtherAccountMetadata), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getCounterpartyPublicAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addCounterpartyMoreInfo), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateCounterpartyMoreInfo), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyMoreInfo), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addCounterpartyPublicAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addCounterpartyUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateCounterpartyUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyCorporateLocation), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyPhysicalLocation), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addCounterpartyImageUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateCounterpartyImageUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyImageUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addCounterpartyOpenCorporatesUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateCounterpartyOpenCorporatesUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyOpenCorporatesUrl), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addOtherAccountPrivateAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateCounterpartyPrivateAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyPrivateAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateCounterpartyPublicAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCounterpartyPublicAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getOtherAccountPrivateAlias), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addWhereTagForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateWhereTagForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateAccountLabel), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getWhereTagForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addImageForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteImageForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getImagesForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addTagForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteTagForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getTagsForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addCommentForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteCommentForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getCommentsForViewOnTransaction), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteTransactionNarrative), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.updateTransactionNarrative), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addTransactionNarrative), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.getTransactionNarrative), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.deleteViewForBankAccount), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_2_1.addPermissionForUserForBankAccountForOneView), ApiVersion.v1_2_1.toString),
    (nameOf(Implementations1_4_0.getTransactionRequestTypes), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations1_4_0.addCustomerMessage), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations2_0_0.getAllEntitlements), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.publicAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.privateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.corePrivateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycDocuments), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycMedia), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycStatuses), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycChecks), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycDocument), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycMedia), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycStatus), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycCheck), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addEntitlement), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.deleteEntitlement), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getTransactionTypes), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getPermissionsForBankAccount), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.publicAccountsAllBanks), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_1_0.getEntitlementsByBankAndUser), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations2_1_0.getRoles), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations2_1_0.getCustomersForCurrentUserAtBank), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations2_1_0.getMetrics), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations2_1_0.createTransactionType), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations2_1_0.getTransactionRequestTypesSupportedByBank), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations2_2_0.config), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getMessageDocs), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getViewsForBankAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getCurrentFxRate), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getExplictCounterpartiesForAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getExplictCounterpartyById), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.createAccount), ApiVersion.v2_2_0.toString)
  )

  object HttpCode {
    def `200`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(200)))
    }
    def `201`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(201)))
    }
    def `202`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(202)))
    }
    def `204`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(204)))
    }
    def `401`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(401)))
    }
    def `200`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(200)))
    }
    def `201`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(201)))
    }
    def `204`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(204)))
    }
    def `404`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(404)))
    }
  }


  object function {

    import com.openbankproject.commons.ExecutionContext.Implicits.global

    private def validateBankId(bankId: Option[String], callContext: Option[CallContext]): Unit = {
      bankId.foreach(validateBankId(_, callContext))
    }

    private def validateBankId(bankId: String, callContext: Option[CallContext]): Unit = try {
      BankId.checkPermission(bankId)
    } catch {
      case _: AccessControlException =>
        val correlationId = callContext.map(_.correlationId).getOrElse("none")
        throw JsonResponseException(s"$DynamicResourceDocMethodPermission No permission of operate bank $bankId", 400, correlationId)
    }

    def getBranch(bankId : BankId, branchId : BranchId, callContext: Option[CallContext]): OBPReturnType[BranchT] = {
      Connector.connector.vend.getBranch(bankId, branchId, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(BranchNotFoundByBranchId, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    /**
      * delete a branch, just set isDeleted field to true, marks it is deleted
      * also check this: https://github.com/OpenBankProject/OBP-API/issues/1241
      * @param branch to be deleted branch
      * @param callContext call context
      * @return true is mean delete success, false is delete fail
      */
    def deleteBranch(branch : BranchT, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      val deletedBranch = Branch(
        branch.branchId ,
        branch.bankId ,
        branch.name ,
        branch.address ,
        branch.location ,
        branch.lobbyString.map(it => LobbyString(it.hours)),
        branch.driveUpString.map(it => DriveUpString(it.hours)),
        branch.meta: Meta,
        branch.branchRouting.map(it => Routing(it.scheme, it.address)),
        branch.lobby,
        branch.driveUp,
        branch.isAccessible,
        branch.accessibleFeatures,
        branch.branchType,
        branch.moreInfo,
        branch.phoneNumber,
        Some(true)
      )
      Future {
        Connector.connector.vend.createOrUpdateBranch(deletedBranch) map {
          i =>  (i.isDeleted.get, callContext)
        }
      } map { unboxFull(_) }
    }

    def getAtm(bankId : BankId, atmId : AtmId, callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.getAtm(bankId, atmId, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(AtmNotFoundByAtmId, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def updateAtmSupportedLanguages(bankId : BankId, atmId : AtmId, supportedLanguages: List[String], callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.updateAtmSupportedLanguages(bankId, atmId, supportedLanguages, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(UpdateAtmSupportedLanguagesException, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def updateAtmSupportedCurrencies(bankId : BankId, atmId : AtmId, supportedCurrencies: List[String], callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.updateAtmSupportedCurrencies(bankId, atmId, supportedCurrencies, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(UpdateAtmSupportedCurrenciesException, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def updateAtmAccessibilityFeatures(bankId : BankId, atmId : AtmId, supportedCurrencies: List[String], callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.updateAtmAccessibilityFeatures(bankId, atmId, supportedCurrencies, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(UpdateAtmAccessibilityFeaturesException, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def updateAtmServices(bankId : BankId, atmId : AtmId, services: List[String], callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.updateAtmServices(bankId, atmId, services, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(UpdateAtmServicesException, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def updateAtmNotes(bankId : BankId, atmId : AtmId, notes: List[String], callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.updateAtmNotes(bankId, atmId, notes, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(UpdateAtmNotesException, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def updateAtmLocationCategories(bankId : BankId, atmId : AtmId, locationCategories: List[String], callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.updateAtmLocationCategories(bankId, atmId, locationCategories, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(UpdateAtmLocationCategoriesException, 404, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }
    
    def createOrUpdateAtm(atm: AtmT, callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.createOrUpdateAtm(atm, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CreateAtmError", 400), i._2)
      } 
    }    
    def deleteAtm(atm: AtmT, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteAtm(atm, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$DeleteAtmError", 400), i._2)
      } 
    }

    def createSystemLevelEndpointTag(operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[EndpointTagT] = {
      Connector.connector.vend.createSystemLevelEndpointTag(operationId, tagName, callContext) map { 
        i => (unboxFullOrFail(i._1, callContext, s"$CreateEndpointTagError", 400), i._2)
      }
    }
    
    def updateSystemLevelEndpointTag(endpointTagId: String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[EndpointTagT] = {
     Connector.connector.vend.updateSystemLevelEndpointTag(endpointTagId: String, operationId:String, tagName:String, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$UpdateEndpointTagError", 400), i._2)
      }
    }

    def createBankLevelEndpointTag(bankId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[EndpointTagT] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createBankLevelEndpointTag(bankId, operationId, tagName, callContext) map { 
        i => (unboxFullOrFail(i._1, callContext, s"$CreateEndpointTagError", 400), i._2)
      }
    }
    
    def updateBankLevelEndpointTag(bankId:String, endpointTagId: String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[EndpointTagT] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.updateBankLevelEndpointTag(bankId, endpointTagId, operationId, tagName, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$UpdateEndpointTagError", 400), i._2)
      }
    }
    
    def checkSystemLevelEndpointTagExists(operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.getSystemLevelEndpointTag(operationId: String, tagName: String, callContext) map {
        i => (i._1.isDefined, i._2)
      }
    }
    
    def checkBankLevelEndpointTagExists(bankId: String, operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.getBankLevelEndpointTag(bankId: String, operationId: String, tagName: String, callContext) map {
        i => (i._1.isDefined, i._2)
      }
    }

    def getEndpointTag(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[EndpointTagT] = {
      Connector.connector.vend.getEndpointTagById(endpointTagId, callContext) map {
        i => (unboxFullOrFail(i._1,  callContext, s"$EndpointTagNotFoundByEndpointTagId Current ENDPOINT_TAG_ID is $endpointTagId", 404), i._2)
      }
    }

    def deleteEndpointTag(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteEndpointTag(endpointTagId, callContext) map {
        i => (unboxFullOrFail(i._1,  callContext, s"$UnknownEndpointTagError Current ENDPOINT_TAG_ID is $endpointTagId", 404), i._2)
      }
    }

    def getSystemLevelEndpointTags(operationId : String, callContext: Option[CallContext]) : OBPReturnType[List[EndpointTagT]] = {
      Connector.connector.vend.getSystemLevelEndpointTags(operationId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetEndpointTags Current OPERATION_ID is $operationId", 404), i._2)
      }
    }

    def getBankLevelEndpointTags(bankId:String, operationId : String, callContext: Option[CallContext]) : OBPReturnType[List[EndpointTagT]] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.getBankLevelEndpointTags(bankId, operationId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetEndpointTags Current OPERATION_ID is $operationId", 404), i._2)
      }
    }
    
    def getBank(bankId : BankId, callContext: Option[CallContext]) : OBPReturnType[Bank] = {
      Connector.connector.vend.getBank(bankId, callContext) map {
        unboxFullOrFail(_, callContext, s"$BankNotFound Current BankId is $bankId", 404)
      }
    }
    def getBanks(callContext: Option[CallContext]) : Future[(List[Bank], Option[CallContext])] = {
      Connector.connector.vend.getBanks(callContext: Option[CallContext]) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getAllAtms(callContext: Option[CallContext]) : Future[(List[AtmT], Option[CallContext])] = {
      Connector.connector.vend.getAllAtms(callContext: Option[CallContext]) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def createOrUpdateBank(bankId: String,
                           fullBankName: String,
                           shortBankName: String,
                           logoURL: String,
                           websiteURL: String,
                           swiftBIC: String,
                           national_identifier: String,
                           bankRoutingScheme: String,
                           bankRoutingAddress: String,
                           callContext: Option[CallContext]): OBPReturnType[Bank] = {
      validateBankId(bankId, callContext)
      Future {
        Connector.connector.vend.createOrUpdateBank(
          bankId,
          fullBankName,
          shortBankName,
          logoURL,
          websiteURL,
          swiftBIC,
          national_identifier,
          bankRoutingScheme,
          bankRoutingAddress
        ) map {
          i =>  (i, callContext)
        }
      } map { unboxFull(_) }
    }
    def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[BankAccount] = {
      Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 404 ), i._2)
      }
    }
    def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[List[BankAccount]] = {
      Connector.connector.vend.getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetBankAccounts", 400 ), i._2)
      }
    }
    
    def getAccountListOfBerlinGroup(user : User, callContext: Option[CallContext]): OBPReturnType[List[BankIdAccountId]] = {
      val viewIds = List(ViewId(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID))
      Views.views.vend.getPrivateBankAccountsFuture(user, viewIds) map { i =>
        if(i.isEmpty) {
          (unboxFullOrFail(Empty, callContext, NoViewReadAccountsBerlinGroup , 403), callContext)
        } else {
          (i, callContext )
        }
      }
    }

    def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[AccountsBalances] = {
      Connector.connector.vend.getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetBankAccounts", 400 ), i._2)
      }
    }

    def getBankAccountsWithAttributes(bankId: BankId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[List[FastFirehoseAccount]] = {
      Connector.connector.vend.getBankAccountsWithAttributes(bankId, queryParams, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetBankAccountsWithAttributes", 400 ), i._2)
      }
    }

    def getBankAccountBalances(bankIdAccountId: BankIdAccountId, callContext: Option[CallContext]): OBPReturnType[AccountBalances] = {
      Connector.connector.vend.getBankAccountBalances(bankIdAccountId: BankIdAccountId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetBankAccounts", 400 ), i._2)
      }
    }

    def getAccountRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]) : OBPReturnType[BankAccountRouting] = {
      Future(Connector.connector.vend.getAccountRouting(bankId: Option[BankId], scheme: String, address : String, callContext: Option[CallContext])) map { i =>
        unboxFullOrFail(i, callContext,s"$AccountRoutingNotFound Current scheme is $scheme, current address is $address, current bankId is $bankId", 404 )
      }
    }

    def getBankAccountByRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Future(Connector.connector.vend.getBankAccountByRouting(bankId: Option[BankId], scheme: String, address : String, callContext: Option[CallContext])) map { i =>
        unboxFullOrFail(i, callContext,s"$BankAccountNotFoundByAccountRouting Current scheme is $scheme, current address is $address, current bankId is $bankId", 404 )
      }
    }

    def getAccountRoutingsByScheme(bankId: Option[BankId], scheme: String, callContext: Option[CallContext]) : OBPReturnType[List[BankAccountRouting]] = {
      Connector.connector.vend.getAccountRoutingsByScheme(bankId: Option[BankId], scheme: String, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$AccountRoutingNotFound Current scheme is $scheme, current bankId is $bankId", 404 ), i._2)
      }
    }

    def getBankAccountByAccountId(accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccountByAccountId(accountId : AccountId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFoundByAccountId Current account_id is $accountId", 404 ), i._2)
      }
    }

    def getBankAccountsByIban(ibans : List[String], callContext: Option[CallContext]) : OBPReturnType[List[BankAccount]] = {
      Future.sequence(ibans.map( iban =>
        Connector.connector.vend.getBankAccountByIban(iban : String, callContext: Option[CallContext]) map { i =>
          (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFoundByIban Current IBAN is $iban", 404 ), i._2)
        }  
      )).map(t => (t.map(_._1), callContext))
    }
    def getBankAccountByIban(iban : String, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccountByIban(iban : String, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFoundByIban Current IBAN is $iban", 404 ), i._2)
      }
    }
    def getToBankAccountByIban(iban : String, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccountByIban(iban : String, callContext: Option[CallContext]) map { i =>
        i._1 match {
          case Full(account) => (account, i._2)
          case _ =>
            val account = BankAccountInMemory(
              accountRoutings = List(
                AccountRouting(scheme = AccountRoutingScheme.IBAN.toString, address = iban)
              )
            )
            (account, i._2)
        }
      }
    }

    def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) } map { i =>
        (unboxFullOrFail(i._1, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 404), i._2)
      }

    def getBankSettlementAccounts(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[List[BankAccount]] = {
      Connector.connector.vend.getBankSettlementAccounts(bankId: BankId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankNotFound Current BankId is $bankId", 404 ), i._2)
      }
    }

    def getTransaction(bankId: BankId, accountId : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None) : OBPReturnType[Transaction] = {
      Connector.connector.vend.getTransaction(bankId, accountId, transactionId, callContext) map {
        x => (unboxFullOrFail(x._1, callContext, TransactionNotFound, 404), x._2)
      }
    }
    
    def permissions(account: BankAccount, user: User) = Future {
      account.permissions(user)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) } 
    
    def removeView(account: BankAccount, user: User, viewId: ViewId) = Future {
      account.removeView(user, viewId)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def grantAccessToView(account: BankAccount, u: User, viewIdBankIdAccountId : ViewIdBankIdAccountId, provider : String, providerId: String) = Future {
      account.grantAccessToView(u, viewIdBankIdAccountId, provider, providerId)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def grantAccessToMultipleViews(account: BankAccount, u: User, viewIdBankIdAccountIds : List[ViewIdBankIdAccountId], provider : String, providerId: String) = Future {
      account.grantAccessToMultipleViews(u, viewIdBankIdAccountIds, provider, providerId)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def revokeAccessToView(account: BankAccount, u: User, viewIdBankIdAccountId : ViewIdBankIdAccountId, provider : String, providerId: String) = Future {
      account.revokeAccessToView(u, viewIdBankIdAccountId, provider, providerId)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def revokeAllAccountAccess(account: BankAccount, u: User, provider : String, providerId: String) = Future {
      account.revokeAllAccountAccess(u, provider, providerId)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def moderatedBankAccountCore(account: BankAccount, view: View, user: Box[User], callContext: Option[CallContext]) = Future {
      account.moderatedBankAccountCore(view, BankIdAccountId(account.bankId, account.accountId), user, callContext)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def moderatedOtherBankAccounts(account: BankAccount, 
                                   view: View, 
                                   user: Box[User], 
                                   callContext: Option[CallContext]): Future[List[ModeratedOtherBankAccount]] = 
      Future(account.moderatedOtherBankAccounts(view, BankIdAccountId(account.bankId, account.accountId), user)) map { connectorEmptyResponse(_, callContext) }    
    def moderatedOtherBankAccount(account: BankAccount,
                                  counterpartyId: String, 
                                  view: View, 
                                  user: Box[User], 
                                  callContext: Option[CallContext]): Future[ModeratedOtherBankAccount] = 
      Future(account.moderatedOtherBankAccount(counterpartyId, view, BankIdAccountId(account.bankId, account.accountId), user, callContext)) map { connectorEmptyResponse(_, callContext) }

    def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams:  List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[List[TransactionCore]] =
      Connector.connector.vend.getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams:  List[OBPQueryParam], callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetTransactions", 400 ), i._2)
      }
    def checkOwnerViewAccessAndReturnOwnerView(user: User, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) : Future[View] = {
      Future {user.checkOwnerViewAccessAndReturnOwnerView(bankAccountId)}  map {
        unboxFullOrFail(_, callContext, s"$UserNoOwnerView" +"userId : " + user.userId + ". bankId : " + s"${bankAccountId.bankId}" + ". accountId : " + s"${bankAccountId.accountId}")
      }
    }

    def checkViewAccessAndReturnView(viewId : ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]) : Future[View] = {
      Future{
        APIUtil.checkViewAccessAndReturnView(viewId, bankAccountId, user)
      } map {
        unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView")
      }
    }
    def checkAccountAccessAndGetView(viewId : ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]) : Future[View] = {
      Future{
        APIUtil.checkViewAccessAndReturnView(viewId, bankAccountId, user)
      } map {
        unboxFullOrFail(_, callContext, s"$NoAccountAccessOnView ${viewId.value}", 403)
      }
    }
    def checkViewsAccessAndReturnView(firstView : ViewId, secondView : ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]) : Future[View] = {
      Future{
        APIUtil.checkViewAccessAndReturnView(firstView, bankAccountId, user).or(
          APIUtil.checkViewAccessAndReturnView(secondView, bankAccountId, user)
        )
      } map {
        unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView")
      }
    }
    def checkBalancingTransactionAccountAccessAndReturnView(doubleEntryTransaction: DoubleEntryTransaction, user: Option[User], callContext: Option[CallContext]) : Future[View] = {
      val debitBankAccountId = BankIdAccountId(
        doubleEntryTransaction.debitTransactionBankId, 
        doubleEntryTransaction.debitTransactionAccountId
      )
      val creditBankAccountId = BankIdAccountId(
        doubleEntryTransaction.creditTransactionBankId, 
        doubleEntryTransaction.creditTransactionAccountId
      )
      val ownerViewId = ViewId(Constant.SYSTEM_OWNER_VIEW_ID)
      Future{
        APIUtil.checkViewAccessAndReturnView(ownerViewId, debitBankAccountId, user).or(
          APIUtil.checkViewAccessAndReturnView(ownerViewId, creditBankAccountId, user)
        )
      } map {
        unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView")
      }
    }
    
    def checkAuthorisationToCreateTransactionRequest(viewId : ViewId, bankAccountId: BankIdAccountId, user: User, callContext: Option[CallContext]) : Future[Boolean] = {
      Future{
        
        lazy val hasCanCreateAnyTransactionRequestRole = APIUtil.hasEntitlement(bankAccountId.bankId.value, user.userId, canCreateAnyTransactionRequest) 
        
        lazy val consumerIdFromCallContext = callContext.map(_.consumer.map(_.consumerId.get).getOrElse(""))
        
        lazy val view = APIUtil.checkViewAccessAndReturnView(viewId, bankAccountId, Some(user), consumerIdFromCallContext)

        lazy val canAddTransactionRequestToAnyAccount = view.map(_.canAddTransactionRequestToAnyAccount).getOrElse(false)
        
        //1st check the admin level role/entitlement `canCreateAnyTransactionRequest`
        if(hasCanCreateAnyTransactionRequestRole) {
          Full(true) 
        //2rd: check if the user have the view access and the view has the `canAddTransactionRequestToAnyAccount` permission
        } else if (canAddTransactionRequestToAnyAccount) {
          Full(true)
        } else{
          Empty
        }
      } map {
        unboxFullOrFail(_, callContext, s"$InsufficientAuthorisationToCreateTransactionRequest " +
          s"Current ViewId(${viewId.value})," +
          s"current UserId(${user.userId})"+
          s"current ConsumerId(${callContext.map(_.consumer.map(_.consumerId.get).getOrElse("")).getOrElse("")})"
        )
      }
    }
    
    def customView(viewId : ViewId, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.customViewFuture(viewId, bankAccountId) map {
        unboxFullOrFail(_, callContext, s"$ViewNotFound. Current ViewId is $viewId")
      }
    }    
    
    def systemView(viewId : ViewId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.systemViewFuture(viewId) map {
        unboxFullOrFail(_, callContext, s"$SystemViewNotFound. Current ViewId is $viewId")
      }
    }    
    def systemViews(): Future[List[View]] = {
      Views.views.vend.getSystemViews()
    }
    def grantAccessToCustomView(view : View, user: User, callContext: Option[CallContext]) : Future[View] = {
      view.isSystem match {
        case false =>
          Future(Views.views.vend.grantAccessToCustomView(ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId), user)) map {
            unboxFullOrFail(_, callContext, s"$CannotGrantAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case true =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for system views.")
          }
      }
    }
    def revokeAccessToCustomView(view : View, user: User, callContext: Option[CallContext]) : Future[Boolean] = {
      view.isSystem match {
        case false =>
          Future(Views.views.vend.revokeAccess(ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId), user)) map {
            unboxFullOrFail(_, callContext, s"$CannotRevokeAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case true =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for system views.")
          }
      }
    }
    def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user: User, callContext: Option[CallContext]) : Future[View] = {
      view.isSystem match {
        case true =>
          Future(Views.views.vend.grantAccessToSystemView(bankId, accountId, view, user)) map {
            unboxFullOrFail(_, callContext, s"$CannotGrantAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case false =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for custom views.")
          }
      }
    }
    def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user: User, callContext: Option[CallContext]) : Future[Boolean] = {
      view.isSystem match {
        case true =>
          Future(Views.views.vend.revokeAccessToSystemView(bankId, accountId, view, user)) map {
            unboxFullOrFail(_, callContext, s"$CannotRevokeAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case false =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for custom views.")
          }
      }
    }
    
    def canGrantAccessToView(bankId: BankId, accountId: AccountId, user: User, callContext: Option[CallContext]) : Future[Box[Boolean]] = {
      Helper.wrapStatementToFuture(UserMissOwnerViewOrNotAccountHolder) {
        canGrantAccessToViewCommon(bankId, accountId, user)
      }
    }

    def canRevokeAccessToView(bankId: BankId, accountId: AccountId, user: User, callContext: Option[CallContext]) : Future[Box[Boolean]] = {
      Helper.wrapStatementToFuture(UserMissOwnerViewOrNotAccountHolder) {
        canRevokeAccessToViewCommon(bankId, accountId, user)
      }
    }
    def createSystemView(view: CreateViewJson, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.createSystemView(view) map {
        unboxFullOrFail(_, callContext, s"$CreateSystemViewError")
      }
    }
    def updateSystemView(viewId: ViewId, view: UpdateViewJSON, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.updateSystemView(viewId, view) map {
        unboxFullOrFail(_, callContext, s"$UpdateSystemViewError")
      }
    }
    def deleteSystemView(viewId : ViewId, callContext: Option[CallContext]) : Future[Boolean] = {
      Views.views.vend.removeSystemView(viewId) map {
        unboxFullOrFail(_, callContext, s"$DeleteSystemViewError")
      }
    }

    def getConsumerByConsumerId(consumerId: String, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId) map {
        unboxFullOrFail(_, callContext, s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId", 404)
      }
    }
    def checkConsumerByConsumerId(consumerId: String, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId) map {
        unboxFullOrFail(_, callContext, s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId", 404)
      } map {
        c => c.isActive.get match {
          case true => c
          case false => unboxFullOrFail(Empty, callContext, s"$ConsumerIsDisabled ConsumerId: $consumerId")
        }
      }
    }

    def getAccountWebhooks(queryParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[AccountWebhook]] = {
      AccountWebhook.accountWebhook.vend.getAccountWebhooksFuture(queryParams) map {
        unboxFullOrFail(_, callContext, GetWebhooksError)
      }
    }

    def getConsumerByPrimaryId(id: Long, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByPrimaryIdFuture(id) map {
        unboxFullOrFail(_, callContext, ConsumerNotFoundByConsumerId, 404)
      }
    }
    def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[List[Customer]] = {
      Connector.connector.vend.getCustomers(bankId, callContext, queryParams) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getCustomersAtAllBanks(callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[List[Customer]] = {
      Connector.connector.vend.getCustomersAtAllBanks(callContext, queryParams) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getCustomersByCustomerPhoneNumber(bankId : BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[List[Customer]] = {
      Connector.connector.vend.getCustomersByCustomerPhoneNumber(bankId, phoneNumber, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getCustomerByCustomerId(customerId : String, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerId(customerId, callContext) map {
        unboxFullOrFail(_, callContext, s"$CustomerNotFoundByCustomerId. Current CustomerId($customerId)", 404)
      }
    }
    def checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse", 400), i._2) 
      }
    }
    def getCustomerByCustomerNumber(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerNumber(customerNumber, bankId, callContext) map {
        unboxFullOrFail(_, callContext, CustomerNotFound, 404)
      }
    }


    def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[CustomerAddress]] = {
      Connector.connector.vend.getCustomerAddress(customerId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def createCustomerAddress(customerId: String,
                              line1: String,
                              line2: String,
                              line3: String,
                              city: String,
                              county: String,
                              state: String,
                              postcode: String,
                              countryCode: String,
                              status: String,
                              tags: String,
                              callContext: Option[CallContext]): OBPReturnType[CustomerAddress] = {
      Connector.connector.vend.createCustomerAddress(
        customerId,
        line1,
        line2,
        line3,
        city,
        county,
        state,
        postcode,
        countryCode,
        tags,
        status,
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def updateCustomerAddress(customerAddressId: String,
                              line1: String,
                              line2: String,
                              line3: String,
                              city: String,
                              county: String,
                              state: String,
                              postcode: String,
                              countryCode: String,
                              status: String,
                              tags: String,
                              callContext: Option[CallContext]): OBPReturnType[CustomerAddress] = {
      Connector.connector.vend.updateCustomerAddress(
        customerAddressId,
        line1,
        line2,
        line3,
        city,
        county,
        state,
        postcode,
        countryCode,
        tags,
        status,
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def deleteCustomerAddress(customerAddressId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteCustomerAddress(customerAddressId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[TaxResidence] = {
      Connector.connector.vend.createTaxResidence(customerId, domain, taxNumber, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getTaxResidences(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[TaxResidence]] = {
      Connector.connector.vend.getTaxResidence(customerId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def deleteTaxResidence(taxResienceId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteTaxResidence(taxResienceId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String, company: String, country: String, purpose: String, callContext: Option[CallContext]): OBPReturnType[UserInvitation] = Future {
      val response: Box[UserInvitation] = UserInvitationProvider.userInvitationProvider.vend.createUserInvitation(bankId, firstName, lastName, email, company, country, purpose)
      (unboxFullOrFail(response, callContext, s"$CannotCreateUserInvitation", 400), callContext)
    }
    def getUserInvitation(bankId: BankId, secretLink: Long, callContext: Option[CallContext]): OBPReturnType[UserInvitation] = Future {
      val response: Box[UserInvitation] = UserInvitationProvider.userInvitationProvider.vend.getUserInvitation(bankId, secretLink)
      (unboxFullOrFail(response, callContext, s"$CannotGetUserInvitation", 400), callContext)
    }
    def getUserInvitations(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[List[UserInvitation]] = Future {
      val response = UserInvitationProvider.userInvitationProvider.vend.getUserInvitations(bankId)
      (unboxFullOrFail(response, callContext, s"$CannotGetUserInvitation", 400), callContext)
    }
    
    def getAdapterInfo(callContext: Option[CallContext]): OBPReturnType[InboundAdapterInfoInternal] = {
        Connector.connector.vend.getAdapterInfo(callContext) map {
          connectorEmptyResponse(_, callContext)
        }
    }

    def getEntitlementsByUserId(userId: String, callContext: Option[CallContext]): Future[List[Entitlement]] = {
      Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(userId) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getAgreementByUserId(userId: String, agreementType: String, callContext: Option[CallContext]): Future[Box[UserAgreement]] = {
      Future(UserAgreementProvider.userAgreementProvider.vend.getUserAgreement(userId, agreementType))
    }

    def getEntitlementsByBankId(bankId: String, callContext: Option[CallContext]): Future[List[Entitlement]] = {
      Entitlement.entitlement.vend.getEntitlementsByBankId(bankId) map {
        connectorEmptyResponse(_, callContext)
      }
    }

    def getEntitlementRequestsFuture(userId: String, callContext: Option[CallContext]): Future[List[EntitlementRequest]] = {
      EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture(userId) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getEntitlementRequestsFuture(callContext: Option[CallContext]): Future[List[EntitlementRequest]] = {
      EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture() map {
        connectorEmptyResponse(_, callContext)
      }
    }

    def getCounterparties(bankId : BankId, accountId : AccountId, viewId : ViewId, callContext: Option[CallContext]): OBPReturnType[List[CounterpartyTrait]] = {
      Connector.connector.vend.getCounterparties(bankId,accountId,viewId, callContext) map { i=>
        (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getMetadata(bankId : BankId, accountId : AccountId, counterpartyId : String, callContext: Option[CallContext]): Future[CounterpartyMetadata] = {
      Future(Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyId)) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(CounterpartyNotFoundByCounterpartyId, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getCounterpartyTrait(bankId : BankId, accountId : AccountId, counterpartyId : String, callContext: Option[CallContext]): OBPReturnType[CounterpartyTrait] = 
    {
      Connector.connector.vend.getCounterpartyTrait(bankId, accountId, counterpartyId, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$CounterpartyNotFoundByCounterpartyId Current counterpartyId($counterpartyId) ", 400),
          i._2)
      }
    }

    def isEnabledTransactionRequests(callContext: Option[CallContext]): Future[Box[Unit]] = Helper.booleanToFuture(failMsg = TransactionRequestsNotEnabled, cc=callContext)(APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false))

    /**
      * Wraps a Future("try") block around the function f and
      * @param f - the block of code to evaluate
      * @return <ul>
      *   <li>Future(result of the evaluation of f) if f doesn't throw any exception
      *   <li>a Failure if f throws an exception with message = failMsg and code = failCode
      *   </ul>
      */
    def tryons[T](failMsg: String, failCode: Int = 400, callContext: Option[CallContext])(f: => T)(implicit m: Manifest[T]): Future[T]= {
      Future {
        tryo {
          f
        }
      } map {
        x => unboxFullOrFail(x, callContext, failMsg, failCode)
      }
    }

    def extractHttpParamsFromUrl(url: String): Future[List[HTTPParam]] = {
      createHttpParamsByUrlFuture(url) map { unboxFull(_) }
    }
    def createObpParams(httpParams: List[HTTPParam], allowedParams: List[String], callContext: Option[CallContext]): OBPReturnType[List[OBPQueryParam]] = {
      val httpParamsAllowed = httpParams.filter(
        x => allowedParams.contains(x.name)
      )
      createQueriesByHttpParamsFuture(httpParamsAllowed, callContext)
    }
    
    def extractQueryParams(url: String,
                           allowedParams: List[String],
                           callContext: Option[CallContext]): OBPReturnType[List[OBPQueryParam]] = {
      val httpParams = createHttpParamsByUrl(url).toList.flatten
      val httpParamsAllowed = httpParams.filter(
        x => allowedParams.contains(x.name)
      )
      createQueriesByHttpParamsFuture(httpParamsAllowed, callContext)
    }
    
    
    def isValidCurrencyISOCode(currencyCode: String,  callContext: Option[CallContext]) = {
      tryons(failMsg = InvalidISOCurrencyCode+s" Current currencyCode is $currencyCode",400, callContext) {
        assert(APIUtil.isValidCurrencyISOCode(currencyCode))
      }
    }
    def isValidCurrencyISOCode(currencyCode: String, failMsg: String, callContext: Option[CallContext])= {
      tryons(failMsg = failMsg,400, callContext) {
        assert(APIUtil.isValidCurrencyISOCode(currencyCode))
      }
    }

    /**
     * as check entitlement methods return map parameter,
     * do request payload validation with json-schema
     * @param callContext callContext
     * @param boxResult hasXXEntitlement method return value, if validation fail, return fail box or throw exception for Future type
     * @tparam T
     * @return
     */
    private def validateRequestPayload[T](callContext: Option[CallContext])(boxResult: Box[T]): Box[T] = {
      val interceptResult: Option[JsonResponse] = callContext.flatMap(_.resourceDocument)
        .filter(v => v.isNotEndpointAuthCheck)                           // endpoint not do auth check automatic
        .flatMap(v => afterAuthenticateInterceptResult(callContext, v.operationId)) // request payload validation error message

      if(boxResult.isEmpty || interceptResult.isEmpty) {
        boxResult
      } else {
        val Some(jsonResponse) = interceptResult
        throw JsonResponseException(jsonResponse)
      }
    }

    def hasEntitlement(bankId: String, userId: String, role: ApiRole, callContext: Option[CallContext], errorMsg: String = ""): Future[Box[Unit]] = {
      val errorInfo =
        if(StringUtils.isBlank(errorMsg)&& !bankId.isEmpty) UserHasMissingRoles + role.toString() + s" at Bank($bankId)" 
        else if(StringUtils.isBlank(errorMsg)&& bankId.isEmpty) UserHasMissingRoles + role.toString() 
        else errorMsg

      Helper.booleanToFuture(errorInfo, cc=callContext) {
        APIUtil.hasEntitlement(bankId, userId, role)
      } map validateRequestPayload(callContext)
    }
    // scala not allow overload method both have default parameter, so this method name is just in order avoid the same name with hasEntitlement
    def ownEntitlement(bankId: String, userId: String, role: ApiRole,callContext: Option[CallContext], errorMsg: String = ""): Box[Unit] = {
      val errorInfo = if(StringUtils.isBlank(errorMsg)) UserHasMissingRoles + role.toString()
                      else errorMsg
      val boxResult = Helper.booleanToBox(APIUtil.hasEntitlement(bankId, userId, role), errorInfo)
      validateRequestPayload(callContext)(boxResult)
    }
    
    def hasAtLeastOneEntitlement(failMsg: => String)(bankId: String, userId: String, roles: List[ApiRole], callContext: Option[CallContext]): Future[Box[Unit]] =
      Helper.booleanToFuture(failMsg, cc=callContext) {
        APIUtil.hasAtLeastOneEntitlement(bankId, userId, roles)
      } map validateRequestPayload(callContext) 
    
    def handleEntitlementsAndScopes(failMsg: => String)(bankId: String, userId: String, roles: List[ApiRole], callContext: Option[CallContext]): Future[Box[Unit]] =
      Helper.booleanToFuture(failMsg, cc=callContext) {
        APIUtil.handleEntitlementsAndScopes(bankId, userId, APIUtil.getConsumerPrimaryKey(callContext),roles)
      } map validateRequestPayload(callContext)

    def hasAtLeastOneEntitlement(bankId: String, userId: String, roles: List[ApiRole], callContext: Option[CallContext]): Future[Box[Unit]] = {
      val errorMessage = if (roles.filter(_.requiresBankId).isEmpty) UserHasMissingRoles + roles.mkString(" or ") else UserHasMissingRoles + roles.mkString(" or ") + s" for BankId($bankId)."
      hasAtLeastOneEntitlement(errorMessage)(bankId, userId, roles, callContext)
    }
    def handleEntitlementsAndScopes(bankId: String, userId: String, roles: List[ApiRole], callContext: Option[CallContext]): Future[Box[Unit]] = {
      val errorMessage = if (roles.filter(_.requiresBankId).isEmpty) UserHasMissingRoles + roles.mkString(" or ") else UserHasMissingRoles + roles.mkString(" or ") + s" for BankId($bankId)."
      handleEntitlementsAndScopes(errorMessage)(bankId, userId, roles, callContext)
    }

    def hasAllEntitlements(bankId: String, userId: String, roles: List[ApiRole], callContext: Option[CallContext]): Box[Unit] = {
      val errorMessage = if (roles.filter(_.requiresBankId).isEmpty) 
        s"$UserHasMissingRoles${roles.mkString(" and ")} entitlements are required." 
      else 
        s"$UserHasMissingRoles${roles.mkString(" and ")} entitlements are required for BankId($bankId)."
        
      val boxResult = Helper.booleanToBox(APIUtil.hasAllEntitlements(bankId, userId, roles), errorMessage)
      validateRequestPayload(callContext)(boxResult)
    }

    def hasAllEntitlements(bankId: String, userId: String, specificBankRoles: List[ApiRole], anyBankRoles: List[ApiRole], callContext: Option[CallContext]): Box[Unit] = {
      val errorMsg = UserHasMissingRoles + specificBankRoles.mkString(" and ") + " OR " + anyBankRoles.mkString(" and ") + " entitlements are required."
      val boxResult = Helper.booleanToBox(
        APIUtil.hasAllEntitlements(bankId, userId, specificBankRoles) || APIUtil.hasAllEntitlements("", userId, anyBankRoles),
        errorMsg)
      validateRequestPayload(callContext)(boxResult)
    }

    def hasEntitlementAndScope(bankId: String, userId: String, consumerId: String, role: ApiRole, callContext: Option[CallContext]): Box[EntitlementAndScopeStatus] = {
      val boxResult = APIUtil.hasEntitlementAndScope(bankId, userId, consumerId, role)
      validateRequestPayload(callContext)(boxResult)
    }

    def createUserAuthContext(user: User, key: String, value: String,  callContext: Option[CallContext]): OBPReturnType[UserAuthContext] = {
      Connector.connector.vend.createUserAuthContext(user.userId, key, value, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      } map {
        result =>
          //We will call the `refreshUserAccountAccess` after we successfully create the UserAuthContext
          // because `createUserAuthContext` is a connector method, here is the entry point for OBP to refreshUser
          AuthUser.refreshUser(user, callContext)
          result
      }
    }
    def createUserAuthContextUpdate(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[UserAuthContextUpdate] = {
      Connector.connector.vend.createUserAuthContextUpdate(userId, key, value, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[List[UserAuthContext]] = {
      Connector.connector.vend.getUserAuthContexts(userId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteUserAuthContexts(userId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def deleteUserAuthContextById(user: User, userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteUserAuthContextById(userAuthContextId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }map {
        result =>
          // We will call the `refreshUserAccountAccess` after we successfully delete the UserAuthContext
          // because `deleteUserAuthContextById` is a connector method, here is the entry point for OBP to refreshUser
          AuthUser.refreshUser(user, callContext)
          result
      }
    }
    
    def deleteUser(userPrimaryKey: UserPrimaryKey, callContext: Option[CallContext]): OBPReturnType[Boolean] = Future {
      AuthUser.scrambleAuthUser(userPrimaryKey) match {
        case Full(true) =>
          Users.users.vend.scrambleDataOfResourceUser(userPrimaryKey) match {
            case Full(true) =>
              val createdByUserInvitationId: String = Users.users.vend.getUserByResourceUserId(userPrimaryKey.value).flatMap(_.createdByUserInvitationId).getOrElse(generateUUID())
              (UserInvitationProvider.userInvitationProvider.vend.scrambleUserInvitation(createdByUserInvitationId).getOrElse(false), callContext)
            case _ =>
              (false, callContext)
          }
        case _ =>
          (false, callContext)
      }
    }

    def findByUserId(userId: String, callContext: Option[CallContext]): OBPReturnType[User] = {
      Future { UserX.findByUserId(userId).map(user =>(user, callContext))} map {
        unboxFullOrFail(_, callContext, s"$UserNotFoundById Current USER_ID($userId)", 404)
      }
    }
  
    def getOrCreateResourceUser(username: String, provider: String, callContext: Option[CallContext]): OBPReturnType[User] = {
      Future { UserX.getOrCreateDauthResourceUser(username, provider).map(user =>(user, callContext))} map {
        unboxFullOrFail(_, callContext, s"$CannotGetOrCreateUser Current USERName($username) PROVIDER ($provider)", 404)
      }
    }
  
    def createTransactionRequestv210(
      u: User,
      viewId: ViewId,
      fromAccount: BankAccount,
      toAccount: BankAccount,
      transactionRequestType: TransactionRequestType,
      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
      detailsPlain: String,
      chargePolicy: String,
      challengeType: Option[String],
      scaMethod: Option[SCA],
      callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
    {
      Connector.connector.vend.createTransactionRequestv210(
        u: User,
        viewId: ViewId,
        fromAccount: BankAccount,
        toAccount: BankAccount,
        transactionRequestType: TransactionRequestType,
        transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
        detailsPlain: String,
        chargePolicy: String,
        challengeType: Option[String],
        scaMethod: Option[SCA],
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetTransactionRequests210", 400), i._2)
      }
    }  
    def createTransactionRequestv400(
                                      u: User,
                                      viewId: ViewId,
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      transactionRequestType: TransactionRequestType,
                                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                      detailsPlain: String,
                                      chargePolicy: String,
                                      challengeType: Option[ChallengeType.Value],
                                      scaMethod: Option[SCA],
                                      reasons: Option[List[TransactionRequestReason]],
                                      berlinGroupPayments: Option[SepaCreditTransfersBerlinGroupV13],
                                      callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
    {
      Connector.connector.vend.createTransactionRequestv400(
        u: User,
        viewId: ViewId,
        fromAccount: BankAccount,
        toAccount: BankAccount,
        transactionRequestType: TransactionRequestType,
        transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
        detailsPlain: String,
        chargePolicy: String,
        challengeType = challengeType.map(_.toString),
        scaMethod: Option[SCA],
        reasons: Option[List[TransactionRequestReason]],
        berlinGroupPayments: Option[SepaCreditTransfersBerlinGroupV13],
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetTransactionRequests210", 400), i._2)
      }
    }

    def notifyTransactionRequest(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[TransactionRequestStatusValue] = {
      Connector.connector.vend.notifyTransactionRequest(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$TransactionRequestStatusNotInitiated Can't notify TransactionRequestId(${transactionRequest.id}) ", 400), i._2)
      }
    }
    
    def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[CounterpartyTrait] = 
    {
      Connector.connector.vend.getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$CounterpartyNotFoundByCounterpartyId Current counterpartyId($counterpartyId) ", 400),
          i._2)
      }
    }
    
    def deleteCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Boolean] = 
    {
      Connector.connector.vend.deleteCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$DeleteCounterpartyError Current counterpartyId($counterpartyId) ", 400),
          i._2)
      }
    }
    def getBankAccountFromCounterparty(counterparty: CounterpartyTrait, isOutgoingAccount: Boolean, callContext: Option[CallContext]) : Future[BankAccount] =
    {
      Future{BankAccountX.getBankAccountFromCounterparty(counterparty, isOutgoingAccount)} map {
        unboxFullOrFail(_, callContext, s"$UnknownError ")
      }
    }
    
    def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) : OBPReturnType[CounterpartyTrait] = 
    {
      Connector.connector.vend.getCounterpartyByIban(iban: String, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(
          i._1, 
          callContext, 
          s"$CounterpartyNotFoundByIban. Please check how do you create Counterparty, " +
            s"set the proper IBan value to `other_account_secondary_routing_address`. Current Iban = $iban ",
          404),
          i._2)
        
      }
    }

    def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) : OBPReturnType[CounterpartyTrait] =
    {
      Connector.connector.vend.getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(
          i._1,
          callContext,
          s"$CounterpartyNotFoundByIban. Please check how do you create Counterparty, " +
            s"set the proper Iban value to `other_account_secondary_routing_address`. Current Iban = $iban. " +
            s"Check also the bankId and the accountId, Current BankId = ${bankId.value}, Current AccountId = ${accountId.value}",
          404),
          i._2)
      }
    }

    def getOrCreateCounterparty(
      name: String,
      description: String,
      currency: String,
      createdByUserId: String,
      thisBankId: String,
      thisAccountId: String,
      thisViewId: String,
      otherBankRoutingScheme: String,
      otherBankRoutingAddress: String,
      otherBranchRoutingScheme: String,
      otherBranchRoutingAddress: String,
      otherAccountRoutingScheme: String,
      otherAccountRoutingAddress: String,
      otherAccountSecondaryRoutingScheme: String,
      otherAccountSecondaryRoutingAddress: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[CounterpartyTrait] =
    {
      Connector.connector.vend.getOrCreateCounterparty(
        name: String,
        description: String,
        currency: String,
        createdByUserId: String,
        thisBankId: String,
        thisAccountId: String,
        thisViewId: String,
        otherBankRoutingScheme: String,
        otherBankRoutingAddress: String,
        otherBranchRoutingScheme: String,
        otherBranchRoutingAddress: String,
        otherAccountRoutingScheme: String,
        otherAccountRoutingAddress: String,
        otherAccountSecondaryRoutingScheme: String,
        otherAccountSecondaryRoutingAddress: String,
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(
          i._1,
          callContext,
          s"$CreateCounterpartyError.",
          404),
          i._2)
      }
    }
    
    def getCounterpartyByRoutings(
      otherBankRoutingScheme: String,
      otherBankRoutingAddress: String,
      otherBranchRoutingScheme: String,
      otherBranchRoutingAddress: String,
      otherAccountRoutingScheme: String,
      otherAccountRoutingAddress: String,
      otherAccountSecondaryRoutingScheme: String,
      otherAccountSecondaryRoutingAddress: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[CounterpartyTrait] =
    {
      Connector.connector.vend.getCounterpartyByRoutings(
        otherBankRoutingScheme: String,
        otherBankRoutingAddress: String,
        otherBranchRoutingScheme: String,
        otherBranchRoutingAddress: String,
        otherAccountRoutingScheme: String,
        otherAccountRoutingAddress: String,
        otherAccountSecondaryRoutingScheme: String,
        otherAccountSecondaryRoutingAddress: String,
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(
          i._1,
          callContext,
          s"$CounterpartyNotFoundByRoutings. Current routings: " +
            s"otherBankRoutingScheme($otherBankRoutingScheme), " +
            s"otherBankRoutingAddress($otherBankRoutingAddress)"+
            s"otherBranchRoutingScheme($otherBranchRoutingScheme)"+
            s"otherBranchRoutingAddress($otherBranchRoutingAddress)"+
            s"otherAccountRoutingScheme($otherAccountRoutingScheme)"+
            s"otherAccountRoutingAddress($otherAccountRoutingAddress)"+
            s"otherAccountSecondaryRoutingScheme($otherAccountSecondaryRoutingScheme)"+
            s"otherAccountSecondaryRoutingAddress($otherAccountSecondaryRoutingAddress)"+
          404),
          i._2)
      }
    }
    
    def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): OBPReturnType[TransactionRequest] = 
    {
      //Note: this method is not over kafka yet, so use Future here.
      Future{ Connector.connector.vend.getTransactionRequestImpl(transactionRequestId, callContext)} map {
        unboxFullOrFail(_, callContext, s"$InvalidTransactionRequestId Current TransactionRequestId($transactionRequestId) ")
      }
    }
    

    def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = 
     Connector.connector.vend.validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]) map { i =>
       (unboxFullOrFail(i._1, callContext, s"$InvalidChallengeAnswer "), i._2)
      }

    def allChallengesSuccessfullyAnswered(
      bankId: BankId,
      accountId: AccountId,
      transReqId: TransactionRequestId,
      callContext: Option[CallContext]
    ): OBPReturnType[Boolean] = 
     Connector.connector.vend.allChallengesSuccessfullyAnswered(
       bankId: BankId,
       accountId: AccountId,
       transReqId: TransactionRequestId,
       callContext: Option[CallContext]
       ) map { i =>
       (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse"), i._2)
     }
    
    def validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]): OBPReturnType[IbanChecker] = 
     Connector.connector.vend.validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]) map { i =>
       (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse() "), i._2)
      }

    def validateChallengeAnswerC2(
      challengeType: ChallengeType.Value,
      transactionRequestId: Option[String], 
      consentId: Option[String], 
      challengeId: String, 
      hashOfSuppliedAnswer: String, 
      callContext: Option[CallContext]
    ): OBPReturnType[ChallengeTrait] = {
      if(challengeType == ChallengeType.BERLINGROUP_PAYMENT_CHALLENGE && transactionRequestId.isEmpty ){
        Future{ throw new Exception(s"$UnknownError The following parameters can not be empty for BERLINGROUP_PAYMENT_CHALLENGE challengeType: paymentId($transactionRequestId) ")}
      }else if(challengeType == ChallengeType.BERLINGROUP_CONSENT_CHALLENGE && consentId.isEmpty ){
        Future{ throw new Exception(s"$UnknownError The following parameters can not be empty for BERLINGROUP_CONSENT_CHALLENGE challengeType: consentId($consentId) ")}
      }else{
        Connector.connector.vend.validateChallengeAnswerC2(
          transactionRequestId: Option[String],
          consentId: Option[String],
          challengeId: String,
          hashOfSuppliedAnswer: String,
          callContext: Option[CallContext]
        ) map { i =>
          (unboxFullOrFail(i._1, callContext, s"$InvalidChallengeAnswer "), i._2)
        }
      }
    }

    /**
     * 
     * @param userIds OBP support multiple challenges, we can ask different users to answer different challenges
     * @param challengeType OBP support different challenge types, @see the Enum ChallengeType
     * @param scaMethod @see the Enum StrongCustomerAuthentication
     * @param scaStatus @see the Enum StrongCustomerAuthenticationStatus
     * @param transactionRequestId it is also the BelinGroup PaymentId
     * @param consentId 
     * @param authenticationMethodId this is used for BelinGroup Consent
     * @param callContext
     * @return
     */
    def createChallengesC2(
      userIds: List[String],
      challengeType: ChallengeType.Value,
      transactionRequestId: Option[String],
      scaMethod: Option[SCA],
      scaStatus: Option[SCAStatus],//Only use for BerlinGroup Now
      consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
      authenticationMethodId: Option[String],
      callContext: Option[CallContext]
    ) : OBPReturnType[List[ChallengeTrait]] = {
      if(challengeType == ChallengeType.BERLINGROUP_PAYMENT_CHALLENGE && (transactionRequestId.isEmpty || scaStatus.isEmpty || scaMethod.isEmpty)){
        Future{ throw new Exception(s"$UnknownError The following parameters can not be empty for BERLINGROUP_PAYMENT challengeType: paymentId($transactionRequestId), scaStatus($scaStatus), scaMethod($scaMethod) ")}
      }else if(challengeType == ChallengeType.BERLINGROUP_CONSENT_CHALLENGE && (consentId.isEmpty || scaStatus.isEmpty || scaMethod.isEmpty)){
        Future{ throw new Exception(s"$UnknownError The following parameters can not be empty for BERLINGROUP_CONSENT challengeType: consentId($consentId), scaStatus($scaStatus), scaMethod($scaMethod) ")}
      }else{
        Connector.connector.vend.createChallengesC2(
          userIds: List[String],
          challengeType: ChallengeType.Value,
          transactionRequestId: Option[String],
          scaMethod: Option[SCA],
          scaStatus: Option[SCAStatus],//Only use for BerlinGroup Now
          consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
          authenticationMethodId: Option[String],
          callContext: Option[CallContext]
        ) map { i =>
          (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateChallenge ", 400), i._2)
        }
      }
    }
    
    def getChallengesByTransactionRequestId(
      transactionRequestId: String, 
      callContext:  Option[CallContext]
    ): OBPReturnType[List[ChallengeTrait]] = {
      Connector.connector.vend.getChallengesByTransactionRequestId(
        transactionRequestId: String,
        callContext:  Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidChallengeTransactionRequestId Current transactionRequestId($transactionRequestId) ", 400), i._2)
      }
    }    
    def getChallengesByConsentId(
      consentId: String, 
      callContext:  Option[CallContext]
    ): OBPReturnType[List[ChallengeTrait]] = {
      Connector.connector.vend.getChallengesByConsentId(
        consentId: String,
        callContext:  Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidChallengeTransactionRequestId Current transactionRequestId($consentId) ", 400), i._2)
      }
    }

    def getChallenge(
      challengeId: String, 
      callContext:  Option[CallContext]
    ): OBPReturnType[ChallengeTrait] = {
      Connector.connector.vend.getChallenge(
        challengeId: String,
        callContext:  Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidChallengeChallengeId Current challengeId($challengeId) ", 400), i._2)
      }
    } 
    
    def createTransactionAfterChallengeV300(
      initiator: User,
      fromAccount: BankAccount,
      transReqId: TransactionRequestId,
      transactionRequestType: TransactionRequestType,
      callContext: Option[CallContext]): OBPReturnType[TransactionRequest] = 
    {
      Connector.connector.vend.createTransactionAfterChallengev300(
        initiator: User,
        fromAccount: BankAccount,
        transReqId: TransactionRequestId,
        transactionRequestType: TransactionRequestType,
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateTransactionAfterChallengev300 ", 400), i._2)
      }
      
    }
    
    def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
      Connector.connector.vend.createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateTransactionAfterChallengev300 ", 400), i._2)
      } 
    
    def makePaymentv210(fromAccount: BankAccount,
                      toAccount: BankAccount,
                      transactionRequestId: TransactionRequestId,
                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                      amount: BigDecimal,
                      description: String,
                      transactionRequestType: TransactionRequestType,
                      chargePolicy: String,
                      callContext: Option[CallContext]): OBPReturnType[TransactionId]=
      Connector.connector.vend.makePaymentv210(
        fromAccount: BankAccount,
        toAccount: BankAccount,
        transactionRequestId: TransactionRequestId,
        transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
        amount: BigDecimal,
        description: String,
        transactionRequestType: TransactionRequestType,
        chargePolicy: String, 
        callContext: Option[CallContext]
      ) map { i => 
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForMakePayment ",400), i._2)
      }

    def saveDoubleEntryBookTransaction(doubleEntryTransaction: DoubleEntryTransaction, callContext: Option[CallContext]): OBPReturnType[DoubleEntryTransaction] =
      Connector.connector.vend.saveDoubleEntryBookTransaction(doubleEntryTransaction: DoubleEntryTransaction, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForSaveDoubleEntryBookTransaction ", 400), i._2)
      }

    def getDoubleEntryBookTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[DoubleEntryTransaction] =
      Connector.connector.vend.getDoubleEntryBookTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$DoubleEntryTransactionNotFound ", 404), i._2)
      }
    def getBalancingTransaction(transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[DoubleEntryTransaction] =
      Connector.connector.vend.getBalancingTransaction(transactionId: TransactionId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$DoubleEntryTransactionNotFound ", 404), i._2)
      }

    def cancelPaymentV400(transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[CancelPayment] = {
      Connector.connector.vend.cancelPaymentV400(transactionId: TransactionId, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCancelPayment ",400), i._2)
      }
    }

    def createOrUpdateProductAttribute(
      bankId: BankId,
      productCode: ProductCode,
      productAttributeId: Option[String],
      name: String,
      attributeType: ProductAttributeType.Value,
      value: String,
      isActive: Option[Boolean],
      callContext: Option[CallContext]
    ): OBPReturnType[ProductAttribute] = {
      Connector.connector.vend.createOrUpdateProductAttribute(
        bankId: BankId,
        productCode: ProductCode,
        productAttributeId: Option[String],
        name: String,
        attributeType: ProductAttributeType.Value,
        value: String,
        isActive: Option[Boolean],
        callContext: Option[CallContext]
      ) map {
          i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def createOrUpdateProductFee(
      bankId: BankId,
      productCode: ProductCode,
      productFeeId: Option[String],
      name: String,
      isActive: Boolean,
      moreInfo: String,
      currency: String,
      amount: BigDecimal,
      frequency: String,
      `type`: String,
      callContext: Option[CallContext]
    ): OBPReturnType[ProductFeeTrait] = {
      Connector.connector.vend.createOrUpdateProductFee(
        bankId: BankId,
        productCode: ProductCode,
        productFeeId: Option[String],
        name: String,
        isActive: Boolean,
        moreInfo: String,
        currency: String,
        amount: BigDecimal,
        frequency: String,
        `type`: String,
        callContext: Option[CallContext]
      ) map { 
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getProductFeeById(
      productFeeId: String, 
      callContext: Option[CallContext]
    ) : OBPReturnType[ProductFeeTrait] =
      Connector.connector.vend.getProductFeeById(
        productFeeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, ProductFeeNotFoundById + " {" + productFeeId + "}", 404), i._2)
      }

    def deleteProductFee(
      productFeeId: String, 
      callContext: Option[CallContext]
    ) : OBPReturnType[Boolean] =
      Connector.connector.vend.deleteProductFee(
        productFeeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, ProductFeeNotFoundById + " {" + productFeeId + "}", 404), i._2)
      }

    def getProductFeesFromProvider(
      bankId: BankId,
      productCode: ProductCode,
      callContext: Option[CallContext]
    ): OBPReturnType[List[ProductFeeTrait]] = {
      Connector.connector.vend.getProductFeesFromProvider(
        bankId: BankId,
        productCode: ProductCode,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    
    def createOrUpdateBankAttribute(
      bankId: BankId,
      bankAttributeId: Option[String],
      name: String,
      attributeType: BankAttributeType.Value,
      value: String,
      isActive: Option[Boolean],
      callContext: Option[CallContext]
    ): OBPReturnType[BankAttribute] = {
      Connector.connector.vend.createOrUpdateBankAttribute(
        bankId: BankId,
        bankAttributeId: Option[String],
        name: String,
        attributeType: BankAttributeType.Value,
        value: String,
        isActive: Option[Boolean],
        callContext: Option[CallContext]
      ) map {
          i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getBankAttributesByBank(bank: BankId,callContext: Option[CallContext]): OBPReturnType[List[BankAttribute]] = {
      Connector.connector.vend.getBankAttributesByBank(
        bank: BankId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getProductAttributesByBankAndCode(
                                           bank: BankId,
                                           productCode: ProductCode,
                                           callContext: Option[CallContext]
                                         ): OBPReturnType[List[ProductAttribute]] = {
      Connector.connector.vend.getProductAttributesByBankAndCode(
        bank: BankId,
        productCode: ProductCode,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def getProductAttributeById(
      productAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[ProductAttribute] = {
      Connector.connector.vend.getProductAttributeById(
        productAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def getBankAttributeById(
      bankAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[BankAttribute] = {
      Connector.connector.vend.getBankAttributeById(
        bankAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def deleteBankAttribute(
      bankAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteBankAttribute(
        bankAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    } 
    
    def deleteProductAttribute(
      productAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteProductAttribute(
        productAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[AccountAttribute] = 
      Connector.connector.vend.getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    
    def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]): OBPReturnType[TransactionAttribute] = 
      Connector.connector.vend.getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]) map {
        x => (unboxFullOrFail(x._1, callContext, TransactionAttributeNotFound, 400), x._2)
      }

    def createOrUpdateAccountAttribute(
                                        bankId: BankId,
                                        accountId: AccountId,
                                        productCode: ProductCode,
                                        accountAttributeId: Option[String],
                                        name: String,
                                        attributeType: AccountAttributeType.Value,
                                        value: String,
                                        productInstanceCode: Option[String],
                                        callContext: Option[CallContext]
                                      ): OBPReturnType[AccountAttribute] = {
      Connector.connector.vend.createOrUpdateAccountAttribute(
        bankId: BankId,
        accountId: AccountId,
        productCode: ProductCode,
        accountAttributeId: Option[String],
        name: String,
        attributeType: AccountAttributeType.Value,
        value: String,
        productInstanceCode: Option[String],
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createOrUpdateCustomerAttribute(
      bankId: BankId,
      customerId: CustomerId,
      customerAttributeId: Option[String],
      name: String,
      attributeType: CustomerAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[CustomerAttribute] = {
      Connector.connector.vend.createOrUpdateCustomerAttribute(
        bankId: BankId,
        customerId: CustomerId,
        customerAttributeId: Option[String],
        name: String,
        attributeType: CustomerAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createOrUpdateTransactionAttribute(
      bankId: BankId,
      transactionId: TransactionId,
      transactionAttributeId: Option[String],
      name: String,
      attributeType: TransactionAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[TransactionAttribute] = {
      Connector.connector.vend.createOrUpdateTransactionAttribute(
        bankId: BankId,
        transactionId: TransactionId,
        transactionAttributeId: Option[String],
        name: String,
        attributeType: TransactionAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def getUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[List[UserAttribute]] = {
      Connector.connector.vend.getUserAttributes(
        userId: String, callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    } 
    
    def getUserAttributesByUsers(userIds: List[String], callContext: Option[CallContext]): OBPReturnType[List[UserAttribute]] = {
      Connector.connector.vend.getUserAttributesByUsers(
        userIds, callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def createOrUpdateUserAttribute(
      userId: String,
      userAttributeId: Option[String],
      name: String,
      attributeType: UserAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[UserAttribute] = {
      Connector.connector.vend.createOrUpdateUserAttribute(
        userId: String,
        userAttributeId: Option[String],
        name: String,
        attributeType: UserAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def createAccountAttributes(bankId: BankId,
                                accountId: AccountId,
                                productCode: ProductCode,
                                accountAttributes: List[ProductAttribute],
                                productInstanceCode: Option[String],
                                callContext: Option[CallContext]): OBPReturnType[List[AccountAttribute]] = {
      Connector.connector.vend.createAccountAttributes(
        bankId: BankId,
        accountId: AccountId,
        productCode: ProductCode,
        accountAttributes,
        productInstanceCode: Option[String],
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getAccountAttributesByAccount(bankId: BankId,
                                      accountId: AccountId,
                                      callContext: Option[CallContext]): OBPReturnType[List[AccountAttribute]] = {
      Connector.connector.vend.getAccountAttributesByAccount(
        bankId: BankId,
        accountId: AccountId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getAccountAttributesForAccounts(bankId: BankId,
                                        accounts: List[BankAccount],
                                        callContext: Option[CallContext]): OBPReturnType[ List[ (BankAccount, List[AccountAttribute]) ]] = {
      Future.sequence(accounts.map( account =>
        Connector.connector.vend.getAccountAttributesByAccount(bankId, AccountId(account.accountId.value), callContext: Option[CallContext]) map { i =>
          (connectorEmptyResponse(i._1.map(x => (account,x)), callContext), i._2)
        }
      )).map(t => (t.map(_._1), callContext))
    }
    
    def getModeratedAccountAttributesByAccount(bankId: BankId, 
                                               accountId: AccountId, 
                                               viewId: ViewId, 
                                               callContext: Option[CallContext]): OBPReturnType[List[AccountAttribute]] = {
      Connector.connector.vend.getAccountAttributesByAccountCanBeSeenOnView(
        bankId: BankId,
        accountId: AccountId,
        viewId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getModeratedAccountAttributesByAccounts(accounts: List[BankIdAccountId],
                                                viewId: ViewId, 
                                                callContext: Option[CallContext]): OBPReturnType[List[AccountAttribute]] = {
      Connector.connector.vend.getAccountAttributesByAccountsCanBeSeenOnView(
        accounts,
        viewId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getModeratedAttributesByTransactions(bankId: BankId,
                                             transactionIds: List[TransactionId],
                                             viewId: ViewId,
                                             callContext: Option[CallContext]): OBPReturnType[List[TransactionAttribute]] = {
      Connector.connector.vend.getTransactionAttributesByTransactionsCanBeSeenOnView(
        bankId,
        transactionIds,
        viewId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getCustomerAttributes(bankId: BankId,
      customerId: CustomerId,
      callContext: Option[CallContext]): OBPReturnType[List[CustomerAttribute]] = {
      Connector.connector.vend.getCustomerAttributes(
        bankId: BankId,
        customerId: CustomerId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    /**
     * get CustomerAttribute according name and values
     * @param bankId CustomerAttribute must belongs the bank
     * @param nameValues key is attribute name, value is attribute values.
     *                   CustomerAttribute name must equals name,
     *                   CustomerAttribute value must be one of values
     * @param callContext
     * @return filtered CustomerAttribute.customerId
     */
    def getCustomerIdsByAttributeNameValues(
                                            bankId: BankId,
                                            nameValues: Map[String, List[String]],
                                            callContext: Option[CallContext]): Future[(List[CustomerId], Option[CallContext])] =
      Connector.connector.vend.getCustomerIdsByAttributeNameValues(bankId, nameValues, callContext)  map {
        i =>
          val customerIds: Box[List[CustomerId]] = i._1.map(_.map(CustomerId(_)))
          (connectorEmptyResponse(customerIds, callContext), i._2)
      }

    def getTransactionIdsByAttributeNameValues(
      bankId: BankId,
      nameValues: Map[String, List[String]],
      callContext: Option[CallContext]): Future[(List[TransactionId], Option[CallContext])] =
      Connector.connector.vend.getTransactionIdsByAttributeNameValues(bankId, nameValues, callContext)  map {
        i =>
          val transactionIds: Box[List[TransactionId]] = i._1.map(_.map(TransactionId(_)))
          (connectorEmptyResponse(transactionIds, callContext), i._2)
      }
    
    
    def getCustomerAttributesForCustomers(
      customers: List[Customer],
      callContext: Option[CallContext]): OBPReturnType[List[CustomerAndAttribute]] = {
      Connector.connector.vend.getCustomerAttributesForCustomers(
        customers: List[Customer],
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def getTransactionAttributes(bankId: BankId,
      transactionId: TransactionId,
      callContext: Option[CallContext]): OBPReturnType[List[TransactionAttribute]] = {
      Connector.connector.vend.getTransactionAttributes(
        bankId: BankId,
        transactionId: TransactionId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }    
    def getModeratedTransactionAttributes(bankId: BankId,
                                          transactionId: TransactionId,
                                          viewId: ViewId,
      callContext: Option[CallContext]): OBPReturnType[List[TransactionAttribute]] = {
      Connector.connector.vend.getTransactionAttributesCanBeSeenOnView(
        bankId: BankId,
        transactionId: TransactionId,
        viewId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getCustomerAttributeById(
      customerAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[CustomerAttribute] = {
      Connector.connector.vend.getCustomerAttributeById(
        customerAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext,CustomerAttributeNotFound), i._2)
      }
    }
    
    
    def createAccountApplication(
                                  productCode: ProductCode,
                                  userId: Option[String],
                                  customerId: Option[String],
                                  callContext: Option[CallContext]
                                ): OBPReturnType[AccountApplication] =
      Connector.connector.vend.createAccountApplication(productCode, userId, customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, CreateAccountApplicationError, 400), i._2)
      }


    def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[List[AccountApplication]] =
      Connector.connector.vend.getAllAccountApplication(callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }

    def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[AccountApplication] =
      Connector.connector.vend.getAccountApplicationById(accountApplicationId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$AccountApplicationNotFound Current Account-Application-Id($accountApplicationId)", 400), i._2)
      }

    def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[AccountApplication] =
      Connector.connector.vend.updateAccountApplicationStatus(accountApplicationId, status, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$UpdateAccountApplicationStatusError  Current Account-Application-Id($accountApplicationId)", 400), i._2)
      }

    def findUsers(userIds: List[String], callContext: Option[CallContext]): OBPReturnType[List[User]] = Future {
      val userList = userIds.filterNot(StringUtils.isBlank).distinct.map(UserX.findByUserId).collect {
        case Full(user) => user
      }
      (userList, callContext)
    }

    def createBankAccount(
      bankId: BankId,
      accountId: AccountId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutings: List[AccountRouting],
      callContext: Option[CallContext]
    ): OBPReturnType[BankAccount] = 
      Connector.connector.vend.createBankAccount(
        bankId: BankId,
        accountId: AccountId,
        accountType: String,
        accountLabel: String,
        currency: String,
        initialBalance: BigDecimal,
        accountHolderName: String,
        branchId: String,
        accountRoutings: List[AccountRouting],
        callContext
      ) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }

    def addBankAccount(
      bankId: BankId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutings: List[AccountRouting],
      callContext: Option[CallContext]
    ): OBPReturnType[BankAccount] =
      Connector.connector.vend.addBankAccount(
        bankId: BankId,
        accountType: String,
        accountLabel: String,
        currency: String,
        initialBalance: BigDecimal,
        accountHolderName: String,
        branchId: String,
        accountRoutings: List[AccountRouting],
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }
    
    def updateBankAccount(
                           bankId: BankId,
                           accountId: AccountId,
                           accountType: String,
                           accountLabel: String,
                           branchId: String,
                           accountRoutings: List[AccountRouting],
                           callContext: Option[CallContext]
                         ): OBPReturnType[BankAccount] =
      Connector.connector.vend.updateBankAccount(
        bankId: BankId,
        accountId: AccountId,
        accountType: String,
        accountLabel: String,
        branchId: String,
        accountRoutings,
        callContext
      ) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }
    
    def findCustomers(customerIds: List[String], callContext: Option[CallContext]): OBPReturnType[List[Customer]] = {
      val customerList = customerIds.filterNot(StringUtils.isBlank).distinct
        .map(Consumers.consumers.vend.getConsumerByConsumerIdFuture)
      Future.foldLeft(customerList)(List.empty[Customer]) { (prev, next) =>
         next match {
           case Full(customer:Customer) => customer :: prev
           case _ => prev
        }
      } map {
        (_, callContext)
      }
    }

    def getOrCreateProductCollection(collectionCode: String, 
                                     productCodes: List[String], 
                                     callContext: Option[CallContext]): OBPReturnType[List[ProductCollection]] = {
      Connector.connector.vend.getOrCreateProductCollection(collectionCode, productCodes, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProduct(bankId : BankId, productCode : ProductCode, callContext: Option[CallContext]) : OBPReturnType[Product] =
      Future {Connector.connector.vend.getProduct(bankId : BankId, productCode : ProductCode)} map {
        i => (unboxFullOrFail(i, callContext, ProductNotFoundByProductCode + " {" + productCode.value + "}", 404), callContext)
      }
    
    def getProductCollection(collectionCode: String, 
                             callContext: Option[CallContext]): OBPReturnType[List[ProductCollection]] = {
      Connector.connector.vend.getProductCollection(collectionCode, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getOrCreateProductCollectionItems(collectionCode: String,
                                          memberProductCodes: List[String],
                                          callContext: Option[CallContext]): OBPReturnType[List[ProductCollectionItem]] = {
      Connector.connector.vend.getOrCreateProductCollectionItem(collectionCode, memberProductCodes, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollectionItems(collectionCode: String,
                                          callContext: Option[CallContext]): OBPReturnType[List[ProductCollectionItem]] = {
      Connector.connector.vend.getProductCollectionItem(collectionCode, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollectionItemsTree(collectionCode: String, 
                                      bankId: String,
                                      callContext: Option[CallContext]): OBPReturnType[List[(ProductCollectionItem, Product, List[ProductAttribute])]] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.getProductCollectionItemsTree(collectionCode, bankId, callContext) map {
        i => {
          val data: Box[List[ProductCollectionItemsTree]] = i._1
          val tupleData: Box[List[(ProductCollectionItemCommons, ProductCommons, List[ProductAttributeCommons])]] =
            data.map(boxValue=> boxValue.map(it => (it.productCollectionItem, it.product, it.attributes)))
          (unboxFullOrFail(tupleData, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
        }
      }
    }
      
    
    def getExchangeRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String, callContext: Option[CallContext]): Future[FXRate] =
      Future(Connector.connector.vend.getCurrentFxRate(bankId, fromCurrencyCode, toCurrencyCode)) map {
        fallbackFxRate =>
          fallbackFxRate match {
            case Empty =>
              val rate = fx.exchangeRate(fromCurrencyCode, toCurrencyCode)
              val inverseRate = fx.exchangeRate(toCurrencyCode, fromCurrencyCode)
              (rate, inverseRate) match {
                case (Some(r), Some(ir)) =>
                  Full(
                    MappedFXRate.create
                      .mBankId(bankId.value)
                      .mFromCurrencyCode(fromCurrencyCode)
                      .mToCurrencyCode(toCurrencyCode)
                      .mConversionValue(r)
                      .mInverseConversionValue(ir)
                      .mEffectiveDate(new Date())
                  )
                case _ => fallbackFxRate
              }
            case _ => fallbackFxRate
          }
      } map {
        unboxFullOrFail(_, callContext, FXCurrencyCodeCombinationsNotSupported)
      }
    
    def createMeeting(
      bankId: BankId,
      staffUser: User,
      customerUser: User,
      providerId: String,
      purposeId: String,
      when: Date,
      sessionId: String,
      customerToken: String,
      staffToken: String,
      creator: ContactDetails,
      invitees: List[Invitee],
      callContext: Option[CallContext]
    ): OBPReturnType[Meeting] =
    {
      Connector.connector.vend.createMeeting(
        bankId: BankId,
        staffUser: User,
        customerUser: User,
        providerId: String,
        purposeId: String,
        when: Date,
        sessionId: String,
        customerToken: String,
        staffToken: String,
        creator: ContactDetails,
        invitees: List[Invitee],
        callContext: Option[CallContext]
    ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not createMeeting in the backend. ", 400), i._2)
      }
    }
    
    def getMeetings(
      bankId : BankId, 
      user: User,
      callContext: Option[CallContext]
    ) :OBPReturnType[List[Meeting]] ={
      Connector.connector.vend.getMeetings(
        bankId : BankId,
        user: User,
        callContext: Option[CallContext]
      ) map {
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not getMeetings in the backend. ", 400), i._2)
        }
      }
    
    def getMeeting(
      bankId: BankId,
      user: User, 
      meetingId : String,
      callContext: Option[CallContext]
    ) :OBPReturnType[Meeting]={ 
      Connector.connector.vend.getMeeting(
        bankId: BankId,
        user: User,
        meetingId : String,
        callContext: Option[CallContext]
    ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$MeetingNotFound Current MeetingId(${meetingId}) ", 400), i._2)
      }
    }

    def getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[List[CoreAccount]] = 
      {
        Connector.connector.vend.getCoreBankAccounts(bankIdAccountIds, callContext) map {
          i => (unboxFullOrFail(i, callContext, s"$InvalidConnectorResponse Can not ${nameOf(getCoreBankAccountsFuture(bankIdAccountIds, callContext))} in the backend. ", 400))
        }
      }

    def getBankAccountsHeldFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[List[AccountHeld]] = {
      Connector.connector.vend.getBankAccountsHeld(bankIdAccountIds, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Cannot ${nameOf(getBankAccountsHeldFuture(bankIdAccountIds, callContext))} in the backend. ", 400), i._2)
      }
    }
    def getAccountsHeld(bankId: BankId, user: User, callContext: Option[CallContext]): OBPReturnType[List[BankIdAccountId]] = {
      Connector.connector.vend.getAccountsHeld(bankId, user, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Cannot ${nameOf(getAccountsHeld(bankId, user, callContext))} in the backend. ", 400), i._2)
      }
    }

    def createOrUpdateKycCheck(bankId: String,
                                customerId: String,
                                id: String,
                                customerNumber: String,
                                date: Date,
                                how: String,
                                staffUserId: String,
                                mStaffName: String,
                                mSatisfied: Boolean,
                                comments: String,
                                callContext: Option[CallContext]): OBPReturnType[KycCheck] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createOrUpdateKycCheck(bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments, callContext)
       .map {
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycCheck in the backend. ", 400), i._2)
       }
    }

    def createOrUpdateKycDocument(bankId: String,
                                  customerId: String,
                                  id: String,
                                  customerNumber: String,
                                  `type`: String,
                                  number: String,
                                  issueDate: Date,
                                  issuePlace: String,
                                  expiryDate: Date,
                                  callContext: Option[CallContext]): OBPReturnType[KycDocument] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createOrUpdateKycDocument(
          bankId,
          customerId,
          id,
          customerNumber,
          `type`,
          number,
          issueDate,
          issuePlace,
          expiryDate,
          callContext)
        .map {
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycDocument in the backend. ", 400), i._2)
        }
    }

    def createOrUpdateKycMedia(bankId: String,
                               customerId: String,
                               id: String,
                               customerNumber: String,
                               `type`: String,
                               url: String,
                               date: Date,
                               relatesToKycDocumentId: String,
                               relatesToKycCheckId: String,
                               callContext: Option[CallContext]): OBPReturnType[KycMedia] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createOrUpdateKycMedia(
        bankId,
        customerId,
        id,
        customerNumber,
        `type`,
        url,
        date,
        relatesToKycDocumentId,
        relatesToKycCheckId,
        callContext
      ).map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycMedia in the backend. ", 400), i._2)
      }
    }

    def createOrUpdateKycStatus(bankId: String,
      customerId: String,
      customerNumber: String,
      ok: Boolean,
      date: Date,
      callContext: Option[CallContext]): OBPReturnType[KycStatus] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createOrUpdateKycStatus(
        bankId,
        customerId,
        customerNumber,
        ok,
        date,
        callContext
      ).map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycStatus in the backend. ", 400), i._2)
      }
    }

    def getKycChecks(customerId: String,
                              callContext: Option[CallContext]
                             ): OBPReturnType[List[KycCheck]] = {
      Connector.connector.vend.getKycChecks(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }

    def getKycDocuments(customerId: String,
                                 callContext: Option[CallContext]
                                ): OBPReturnType[List[KycDocument]] = {
      Connector.connector.vend.getKycDocuments(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }

    def getKycMedias(customerId: String,
                              callContext: Option[CallContext]
                             ): OBPReturnType[List[KycMedia]] = {
      Connector.connector.vend.getKycMedias(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }

    def getKycStatuses(customerId: String,
                                callContext: Option[CallContext]
                               ): OBPReturnType[List[KycStatus]] = {
      Connector.connector.vend.getKycStatuses(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }


    def createMessage(user : User,
                      bankId : BankId,
                      message : String,
                      fromDepartment : String,
                      fromPerson : String,
                      callContext: Option[CallContext]) : OBPReturnType[CustomerMessage] = {
      Connector.connector.vend.createMessage(
        user : User,
        bankId : BankId,
        message : String,
        fromDepartment : String,
        fromPerson : String,
        callContext: Option[CallContext]) map {
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Can not create message in the backend.", 400), i._2)
      }
    }

    def createCustomerMessage(
      customer: Customer,
      bankId: BankId,
      transport: String,
      message: String,
      fromDepartment: String,
      fromPerson: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[CustomerMessage] = {
      Connector.connector.vend.createCustomerMessage(
        customer: Customer,
        bankId : BankId,
        transport: String,
        message : String,
        fromDepartment : String,
        fromPerson : String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Can not create message in the backend.", 400), i._2)
      }
    }
    
     def getCustomerMessages(customer : Customer, bankId : BankId, callContext: Option[CallContext]) : OBPReturnType[List[CustomerMessage]] = {
      Connector.connector.vend.getCustomerMessages(
        customer : Customer, 
        bankId : BankId,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Can not get messages in the backend.", 400), i._2)
      }
    }

    def getUserCustomerLinkByCustomerId(customerId: String, callContext: Option[CallContext]) : OBPReturnType[UserCustomerLink] = {
      Future {UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByCustomerId(customerId)}   map {
        i => (unboxFullOrFail(i, callContext, s"$UserCustomerLinksNotFoundForUser Current customerId ($customerId)", 400), callContext)
      }
    }
    def getOCreateUserCustomerLink(bankId: BankId, customerNumber: String, userId: String, callContext: Option[CallContext]) : OBPReturnType[UserCustomerLink] = {
      Connector.connector.vend.getCustomerByCustomerNumber(customerNumber, bankId, callContext) map {
        _ match {
            case Full(tuple) => 
              UserCustomerLink.userCustomerLink.vend.getOCreateUserCustomerLink(userId, tuple._1.customerId, new Date(), true)
            case _ =>
              Empty
        }
      } map {
        i => (unboxFullOrFail(i, callContext, s"$CreateUserCustomerLinksError Current customerId ($customerNumber)", 400), callContext)
      }
    }

    def createCustomer(
                        bankId: BankId,
                        legalName: String,
                        mobileNumber: String,
                        email: String,
                        faceImage:
                        CustomerFaceImageTrait,
                        dateOfBirth: Date,
                        relationshipStatus: String,
                        dependents: Int,
                        dobOfDependents: List[Date],
                        highestEducationAttained: String,
                        employmentStatus: String,
                        kycStatus: Boolean,
                        lastOkDate: Date,
                        creditRating: Option[CreditRatingTrait],
                        creditLimit: Option[AmountOfMoneyTrait],
                        title: String,
                        branchId: String,
                        nameSuffix: String,
                        callContext: Option[CallContext]): OBPReturnType[Customer] = 
      Connector.connector.vend.createCustomer(
        bankId: BankId,
        legalName: String,
        mobileNumber: String,
        email: String,
        faceImage:
          CustomerFaceImageTrait,
        dateOfBirth: Date,
        relationshipStatus: String,
        dependents: Int,
        dobOfDependents: List[Date],
        highestEducationAttained: String,
        employmentStatus: String,
        kycStatus: Boolean,
        lastOkDate: Date,
        creditRating: Option[CreditRatingTrait],
        creditLimit: Option[AmountOfMoneyTrait],
        title: String,
        branchId: String,
        nameSuffix: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, CreateCustomerError), i._2)
      }
    def createCustomerC2(
                        bankId: BankId,
                        legalName: String,
                        customerNumber: String,
                        mobileNumber: String,
                        email: String,
                        faceImage:
                        CustomerFaceImageTrait,
                        dateOfBirth: Date,
                        relationshipStatus: String,
                        dependents: Int,
                        dobOfDependents: List[Date],
                        highestEducationAttained: String,
                        employmentStatus: String,
                        kycStatus: Boolean,
                        lastOkDate: Date,
                        creditRating: Option[CreditRatingTrait],
                        creditLimit: Option[AmountOfMoneyTrait],
                        title: String,
                        branchId: String,
                        nameSuffix: String,
                        callContext: Option[CallContext]): OBPReturnType[Customer] = 
      Connector.connector.vend.createCustomerC2(
        bankId: BankId,
        legalName: String,
        customerNumber: String,
        mobileNumber: String,
        email: String,
        faceImage:
          CustomerFaceImageTrait,
        dateOfBirth: Date,
        relationshipStatus: String,
        dependents: Int,
        dobOfDependents: List[Date],
        highestEducationAttained: String,
        employmentStatus: String,
        kycStatus: Boolean,
        lastOkDate: Date,
        creditRating: Option[CreditRatingTrait],
        creditLimit: Option[AmountOfMoneyTrait],
        title: String,
        branchId: String,
        nameSuffix: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, CreateCustomerError), i._2)
      }

    def updateCustomerScaData(customerId: String,
                              mobileNumber: Option[String],
                              email: Option[String],
                              customerNumber: Option[String],
                              callContext: Option[CallContext]): OBPReturnType[Customer] =
      Connector.connector.vend.updateCustomerScaData(
        customerId,
        mobileNumber,
        email,
        customerNumber,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerError), i._2)
      }
    def updateCustomerCreditData(customerId: String,
                                 creditRating: Option[String],
                                 creditSource: Option[String],
                                 creditLimit: Option[AmountOfMoney],
                                 callContext: Option[CallContext]): OBPReturnType[Customer] =
      Connector.connector.vend.updateCustomerCreditData(
        customerId,
        creditRating,
        creditSource,
        creditLimit,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerError), i._2)
      }

    def updateCustomerGeneralData(customerId: String,
                                  legalName: Option[String] = None,
                                  faceImage: Option[CustomerFaceImageTrait] = None,
                                  dateOfBirth: Option[Date] = None,
                                  relationshipStatus: Option[String] = None,
                                  dependents: Option[Int] = None,
                                  highestEducationAttained: Option[String] = None,
                                  employmentStatus: Option[String] = None,
                                  title: Option[String] = None,
                                  branchId: Option[String] = None,
                                  nameSuffix: Option[String] = None,
                                  callContext: Option[CallContext]): OBPReturnType[Customer] =
      Connector.connector.vend.updateCustomerGeneralData(
        customerId,
        legalName,
        faceImage,
        dateOfBirth,
        relationshipStatus,
        dependents,
        highestEducationAttained,
        employmentStatus,
        title,
        branchId,
        nameSuffix,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerError), i._2)
      }

    def createPhysicalCard(
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
      issueNumber: String,
      serialNumber: String,
      validFrom: Date,
      expires: Date,
      enabled: Boolean,
      cancelled: Boolean,
      onHotList: Boolean,
      technology: String,
      networks: List[String],
      allows: List[String],
      accountId: String,
      bankId: String,
      replacement: Option[CardReplacementInfo],
      pinResets: List[PinResetInfo],
      collected: Option[CardCollectionInfo],
      posted: Option[CardPostedInfo],
      customerId: String,
      cvv: String,
      brand: String,
      callContext: Option[CallContext]
    ): OBPReturnType[PhysicalCard] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createPhysicalCard(
        bankCardNumber: String,
        nameOnCard: String,
        cardType: String,
        issueNumber: String,
        serialNumber: String,
        validFrom: Date,
        expires: Date,
        enabled: Boolean,
        cancelled: Boolean,
        onHotList: Boolean,
        technology: String,
        networks: List[String],
        allows: List[String],
        accountId: String,
        bankId: String,
        replacement: Option[CardReplacementInfo],
        pinResets: List[PinResetInfo],
        collected: Option[CardCollectionInfo],
        posted: Option[CardPostedInfo],
        customerId: String,
        cvv: String,
        brand: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CreateCardError"), i._2)
      }
    }

    def updatePhysicalCard(
      cardId: String,
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
      issueNumber: String,
      serialNumber: String,
      validFrom: Date,
      expires: Date,
      enabled: Boolean,
      cancelled: Boolean,
      onHotList: Boolean,
      technology: String,
      networks: List[String],
      allows: List[String],
      accountId: String,
      bankId: String,
      replacement: Option[CardReplacementInfo],
      pinResets: List[PinResetInfo],
      collected: Option[CardCollectionInfo],
      posted: Option[CardPostedInfo],
      customerId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[PhysicalCardTrait] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.updatePhysicalCard(
        cardId: String,
        bankCardNumber: String,
        nameOnCard: String,
        cardType: String,
        issueNumber: String,
        serialNumber: String,
        validFrom: Date,
        expires: Date,
        enabled: Boolean,
        cancelled: Boolean,
        onHotList: Boolean,
        technology: String,
        networks: List[String],
        allows: List[String],
        accountId: String,
        bankId: String,
        replacement: Option[CardReplacementInfo],
        pinResets: List[PinResetInfo],
        collected: Option[CardCollectionInfo],
        posted: Option[CardPostedInfo],
        customerId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$UpdateCardError"), i._2)
      }
    }

    def getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) : OBPReturnType[List[PhysicalCard]] =
      Connector.connector.vend.getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, CardNotFound), i._2)
      }

    def getPhysicalCardByCardNumber(bankCardNumber: String,  callContext:Option[CallContext]) : OBPReturnType[PhysicalCardTrait] = {
      Connector.connector.vend.getPhysicalCardByCardNumber(bankCardNumber: String,  callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, InvalidCardNumber), i._2)
      }
    }

    def getPhysicalCardForBank(bankId: BankId, cardId:String ,callContext:Option[CallContext]) : OBPReturnType[PhysicalCardTrait] =
      Connector.connector.vend.getPhysicalCardForBank(bankId: BankId, cardId: String, callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardNotFound Current CardId($cardId)"), i._2)
      }

    def deletePhysicalCardForBank(bankId: BankId, cardId:String ,callContext:Option[CallContext]) : OBPReturnType[Boolean] =
      Connector.connector.vend.deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardNotFound Current CardId($cardId)"), i._2)
      }
    def getMethodRoutingsByMethodName(methodName: Box[String]): Future[List[MethodRoutingT]] = Future {
      this.getMethodRoutings(methodName.toOption)
    }
    def checkMethodRoutingAlreadyExists(methodRouting: MethodRoutingT, callContext:Option[CallContext]): OBPReturnType[Boolean] = Future {
      val methodRoutingCommons: MethodRoutingCommons = {
        val commons: MethodRoutingCommons = methodRouting
        commons.copy(methodRoutingId = None, parameters = commons.parameters.sortBy(_.key))
      }

      val exists: Boolean =
        this.getMethodRoutings(Some(methodRouting.methodName), Option(methodRouting.isBankIdExactMatch), methodRouting.bankIdPattern)
          .exists {v =>
              val commons: MethodRoutingCommons = v
              methodRoutingCommons == commons.copy(methodRoutingId = None, parameters = commons.parameters.sortBy(_.key))
          }

      val notExists = if(exists) Empty else Full(true)
      (unboxFullOrFail(notExists, callContext, s"$ExistingMethodRoutingError Please modify the following parameters:" +
        s"is_bank_id_exact_match(${methodRouting.isBankIdExactMatch}), " +
        s"method_name(${methodRouting.methodName}), " +
        s"bank_id_pattern(${methodRouting.bankIdPattern.getOrElse("")})"
      ), callContext)
    }
    def getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]) =
      Connector.connector.vend.getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardAttributeNotFound Current CardAttributeId($cardAttributeId)"), i._2)
      }
    
    
    def createOrUpdateCardAttribute(
      bankId: Option[BankId],
      cardId: Option[String],
      cardAttributeId: Option[String],
      name: String,
      attributeType: CardAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[CardAttribute] = {
      Connector.connector.vend.createOrUpdateCardAttribute(
        bankId: Option[BankId],
        cardId: Option[String],
        cardAttributeId: Option[String],
        name: String,
        attributeType: CardAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getCardAttributesFromProvider(
      cardId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[List[CardAttribute]] = {
      Connector.connector.vend.getCardAttributesFromProvider(
        cardId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId,
                                                    callContext: Option[CallContext]): OBPReturnType[List[TransactionRequestAttributeTrait]] = {
      Connector.connector.vend.getTransactionRequestAttributesFromProvider(
        transactionRequestId: TransactionRequestId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getTransactionRequestAttributes(bankId: BankId,
                                        transactionRequestId: TransactionRequestId,
                                        callContext: Option[CallContext]): OBPReturnType[List[TransactionRequestAttributeTrait]] = {
      Connector.connector.vend.getTransactionRequestAttributes(
        bankId: BankId,
        transactionRequestId: TransactionRequestId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                       transactionRequestId: TransactionRequestId,
                                                       viewId: ViewId,
                                                       callContext: Option[CallContext]): OBPReturnType[List[TransactionRequestAttributeTrait]] = {
      Connector.connector.vend.getTransactionRequestAttributesCanBeSeenOnView(
        bankId: BankId,
        transactionRequestId: TransactionRequestId,
        viewId: ViewId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getTransactionRequestAttributeById(transactionRequestAttributeId: String,
                                           callContext: Option[CallContext]): OBPReturnType[TransactionRequestAttributeTrait] = {
      Connector.connector.vend.getTransactionRequestAttributeById(
        transactionRequestAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        x => (unboxFullOrFail(x._1, callContext, TransactionRequestAttributeNotFound, 400), x._2)
      }
    }

    def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]],
                                                      callContext: Option[CallContext]): OBPReturnType[List[String]] = {
      Connector.connector.vend.getTransactionRequestIdsByAttributeNameValues(
        bankId: BankId,
        params: Map[String, List[String]],
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                  transactionRequestId: TransactionRequestId,
                                                  transactionRequestAttributeId: Option[String],
                                                  name: String,
                                                  attributeType: TransactionRequestAttributeType.Value,
                                                  value: String,
                                                  callContext: Option[CallContext]): OBPReturnType[TransactionRequestAttributeTrait] = {
      Connector.connector.vend.createOrUpdateTransactionRequestAttribute(
        bankId: BankId,
        transactionRequestId: TransactionRequestId,
        transactionRequestAttributeId: Option[String],
        name: String,
        attributeType: TransactionRequestAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createTransactionRequestAttributes(bankId: BankId,
                                           transactionRequestId: TransactionRequestId,
                                           transactionRequestAttributes: List[TransactionRequestAttributeTrait],
                                           callContext: Option[CallContext]): OBPReturnType[List[TransactionRequestAttributeTrait]] = {
      Connector.connector.vend.createTransactionRequestAttributes(
        bankId: BankId,
        transactionRequestId: TransactionRequestId,
        transactionRequestAttributes: List[TransactionRequestAttributeTrait],
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def deleteTransactionRequestAttribute(transactionRequestAttributeId: String,
                                          callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteTransactionRequestAttribute(
        transactionRequestAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createOrUpdateMethodRouting(methodRouting: MethodRoutingT) = Future {
      MethodRoutingProvider.connectorMethodProvider.vend.createOrUpdate(methodRouting)
     }

    def deleteMethodRouting(methodRoutingId: String) = Future {
      MethodRoutingProvider.connectorMethodProvider.vend.delete(methodRoutingId)
    }

    def getMethodRoutingById(methodRoutingId : String, callContext: Option[CallContext]): OBPReturnType[MethodRoutingT] = {
      val methodRoutingBox: Box[MethodRoutingT] = MethodRoutingProvider.connectorMethodProvider.vend.getById(methodRoutingId)
      Future{
        val methodRouting = unboxFullOrFail(methodRoutingBox, callContext, MethodRoutingNotFoundByMethodRoutingId)
        (methodRouting, callContext)
      }
    }

    private[this] val methodRoutingTTL = APIUtil.getPropsValue(s"methodRouting.cache.ttl.seconds", "0").toInt 

    def getMethodRoutings(methodName: Option[String], isBankIdExactMatch: Option[Boolean] = None, bankIdPattern: Option[String] = None): List[MethodRoutingT] = {
      import scala.concurrent.duration._

      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(methodRoutingTTL second) {
          MethodRoutingProvider.connectorMethodProvider.vend.getMethodRoutings(methodName, isBankIdExactMatch, bankIdPattern)
        }
      }
    }

    def createOrUpdateEndpointMapping(bankId: Option[String], endpointMapping: EndpointMappingT, callContext: Option[CallContext]) = {
      validateBankId(bankId, callContext)
      Future {
        (EndpointMappingProvider.endpointMappingProvider.vend.createOrUpdate(bankId, endpointMapping), callContext)
      } map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def deleteEndpointMapping(bankId: Option[String], endpointMappingId: String, callContext: Option[CallContext]) = {
      validateBankId(bankId, callContext)
      Future {
        (EndpointMappingProvider.endpointMappingProvider.vend.delete(bankId, endpointMappingId), callContext)
      } map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getEndpointMappingById(bankId: Option[String], endpointMappingId : String, callContext: Option[CallContext]): OBPReturnType[EndpointMappingT] = {
      validateBankId(bankId, callContext)

      val endpointMappingBox: Box[EndpointMappingT] = EndpointMappingProvider.endpointMappingProvider.vend.getById(bankId, endpointMappingId)
      Future{
        val endpointMapping = unboxFullOrFail(endpointMappingBox, callContext, s"$EndpointMappingNotFoundByEndpointMappingId Current ENDPOINT_MAPPING_ID is $endpointMappingId", 404)
        (endpointMapping, callContext)
      }
    }

    def getEndpointMappingByOperationId(bankId: Option[String], operationId : String, callContext: Option[CallContext]): OBPReturnType[EndpointMappingT] = {
      validateBankId(bankId, callContext)

      val endpointMappingBox: Box[EndpointMappingT] = EndpointMappingProvider.endpointMappingProvider.vend.getByOperationId(bankId, operationId)
      Future{
        val endpointMapping = unboxFullOrFail(endpointMappingBox, callContext, s"$EndpointMappingNotFoundByOperationId Current OPERATION_ID is $operationId",404)
        (endpointMapping, callContext)
      }
    }

    private[this] val endpointMappingTTL = APIUtil.getPropsValue(s"endpointMapping.cache.ttl.seconds", "0").toInt

    def getEndpointMappings(bankId: Option[String], callContext: Option[CallContext]): OBPReturnType[List[EndpointMappingT]] = {
      import scala.concurrent.duration._

      validateBankId(bankId, callContext)

      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(endpointMappingTTL second) {
          Future{(EndpointMappingProvider.endpointMappingProvider.vend.getAllEndpointMappings(bankId), callContext)}
        }
      }
    }
    
    private def createDynamicEntity(dynamicEntity: DynamicEntityT, callContext: Option[CallContext]): Future[Box[DynamicEntityT]] = {
      val existsDynamicEntity = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(dynamicEntity.bankId, dynamicEntity.entityName)

      if(existsDynamicEntity.isDefined) {
        val errorMsg = if (dynamicEntity.bankId.isEmpty)
          s"$DynamicEntityNameAlreadyExists current entityName is '${dynamicEntity.entityName}'."
        else
          s"$DynamicEntityNameAlreadyExists current entityName is '${dynamicEntity.entityName}' bankId is ${dynamicEntity.bankId.getOrElse("")}."
          
        return Helper.booleanToFuture(errorMsg, cc=callContext)(existsDynamicEntity.isEmpty).map(_.asInstanceOf[Box[DynamicEntityT]])
      }

      Future {
        DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(dynamicEntity)
      }
    }

    private def updateDynamicEntity(dynamicEntity: DynamicEntityT, dynamicEntityId: String , callContext: Option[CallContext]): Future[Box[DynamicEntityT]] = {
      val originEntity = DynamicEntityProvider.connectorMethodProvider.vend.getById(dynamicEntity.bankId, dynamicEntityId)
      // if can't find by id, return 404 error
      val idNotExistsMsg = s"$DynamicEntityNotFoundByDynamicEntityId dynamicEntityId = ${dynamicEntity.dynamicEntityId.get}."

      if (originEntity.isEmpty) {
        return Helper.booleanToFuture(idNotExistsMsg, 404, cc=callContext)(originEntity.isDefined).map(_.asInstanceOf[Box[DynamicEntityT]])
      }

      val originEntityName = originEntity.map(_.entityName).orNull
      // if entityName changed and the new entityName already exists, return error message
      if(dynamicEntity.entityName != originEntityName) {
        val existsDynamicEntity = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(dynamicEntity.bankId, dynamicEntity.entityName)

        if(existsDynamicEntity.isDefined) {
          val errorMsg = if (dynamicEntity.bankId.isDefined)
            s"$DynamicEntityNameAlreadyExists current entityName is '${dynamicEntity.entityName}' bankId(${dynamicEntity.bankId.getOrElse("")})"
          else
            s"$DynamicEntityNameAlreadyExists current entityName is '${dynamicEntity.entityName}'."
            
          return Helper.booleanToFuture(errorMsg, cc=callContext)(existsDynamicEntity.isEmpty).map(_.asInstanceOf[Box[DynamicEntityT]])
        }
      }

      Future {
        DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(dynamicEntity)
      }

    }

    def createOrUpdateDynamicEntity(dynamicEntity: DynamicEntityT, callContext: Option[CallContext]): Future[Box[DynamicEntityT]] =
      dynamicEntity.dynamicEntityId match {
        case Some(dynamicEntityId) => updateDynamicEntity(dynamicEntity, dynamicEntityId, callContext)
        case None => createDynamicEntity(dynamicEntity, callContext)
      }

    /**
     * delete one DynamicEntity and corresponding entitlement and dynamic entitlement
     * @param dynamicEntityId
     * @return
     */
    def deleteDynamicEntity(bankId: Option[String], dynamicEntityId: String): Future[Box[Boolean]] = {
      validateBankId(bankId, None)
      Future {
        for {
          entity <- DynamicEntityProvider.connectorMethodProvider.vend.getById(bankId, dynamicEntityId)
          deleteEntityResult <- DynamicEntityProvider.connectorMethodProvider.vend.delete(entity)
          deleteEntitleMentResult <- if (deleteEntityResult) {
            Entitlement.entitlement.vend.deleteDynamicEntityEntitlement(entity.entityName, entity.bankId)
          } else {
            Box !! false
          }
        } yield {
          if (deleteEntitleMentResult) {
            DynamicEntityInfo.roleNames(entity.entityName, entity.bankId).foreach(ApiRole.removeDynamicApiRole(_))
          }
          deleteEntitleMentResult
        }
      }
    }

    def getDynamicEntityById(bankId: Option[String], dynamicEntityId : String, callContext: Option[CallContext]): OBPReturnType[DynamicEntityT] = {
      validateBankId(bankId, callContext)

      val dynamicEntityBox: Box[DynamicEntityT] = DynamicEntityProvider.connectorMethodProvider.vend.getById(bankId, dynamicEntityId)
      val dynamicEntity = unboxFullOrFail(dynamicEntityBox, callContext, DynamicEntityNotFoundByDynamicEntityId, 404)
      Future{
        (dynamicEntity, callContext)
      }
    }

    def getDynamicEntityByEntityName(bankId: Option[String], entityName : String, callContext: Option[CallContext]): OBPReturnType[Box[DynamicEntityT]] = {
      validateBankId(bankId, callContext)
      Future {
        val boxedDynamicEntity = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(bankId, entityName)
        (boxedDynamicEntity, callContext)
      }
    }

    private[this] val dynamicEntityTTL = {
      if(Props.testMode || Props.devMode) 0
      else APIUtil.getPropsValue(s"dynamicEntity.cache.ttl.seconds", "30").toInt
    }

    def getDynamicEntities(bankId: Option[String], returnBothBankAndSystemLevel: Boolean): List[DynamicEntityT] = {
      import scala.concurrent.duration._

      validateBankId(bankId, None)

      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(dynamicEntityTTL second) {
          DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities(bankId, returnBothBankAndSystemLevel)
        }
      }
    }
    
    def getDynamicEntitiesByUserId(userId: String): List[DynamicEntityT] = {
      import scala.concurrent.duration._

      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(dynamicEntityTTL second) {
          DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntitiesByUserId(userId: String)
        }
      }
    }
    
    def makeHistoricalPayment(
      fromAccount: BankAccount,
      toAccount: BankAccount,
      posted:  Date,
      completed: Date,
      amount: BigDecimal,
      currency: String,
      description: String,
      transactionRequestType: String,
      chargePolicy: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[TransactionId] =
      Connector.connector.vend.makeHistoricalPayment(
        fromAccount: BankAccount,
        toAccount: BankAccount,
        posted:  Date,
        completed: Date,
        amount: BigDecimal,
        currency: String,
        description: String,
        transactionRequestType: String,
        chargePolicy: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CreateTransactionsException"), i._2)
      }

    def invokeDynamicConnector(operation: DynamicEntityOperation,
                               entityName: String,
                               requestBody: Option[JObject],
                               entityId: Option[String],
                               bankId: Option[String],
                               queryParameters: Option[Map[String, List[String]]],
                               userId: Option[String],
                               isPersonalEntity: Boolean,
                               callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
      import DynamicEntityOperation._
      validateBankId(bankId, callContext)

      val dynamicEntityBox = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(bankId, entityName)
      // do validate, any validate process fail will return immediately
      if(dynamicEntityBox.isEmpty) {
        return Helper.booleanToFuture(s"$DynamicEntityNotExists entity's name is '$entityName'", cc=callContext)(false)
          .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
      }

      //To make sure create and update contain the requestBody
      if(operation == CREATE || operation == UPDATE) {
        if(requestBody.isEmpty) {
          throw new RuntimeException(s"$InvalidJsonFormat requestBody is required for $operation operation.")
        }
      }
      
      //To make sure the GET_ONE/UPDATE/DELETE contain the entityId
      if(operation == GET_ONE || operation == UPDATE || operation == DELETE) {
        if (entityId.isEmpty || entityId.filter(StringUtils.isBlank).isDefined) {
          throw new RuntimeException(s"$InvalidJsonFormat entityId is required for $operation operation.")
        }
      }
      
      // check if there is (entityIdName, entityIdValue) pair in the requestBody, if no, we will add it. 
      val requestBodyDynamicInstance: Option[JObject] = requestBody.map { requestJsonJObject =>
       
        val entityIdName = DynamicEntityHelper.createEntityId(entityName)
        
        val entityIdValue = requestJsonJObject \ entityIdName

        entityIdValue match {
            //if the value is not existing, we need to add the `(entityIdName, entityIdValue)` pair
          case JNothing | JNull => requestJsonJObject ~ (entityIdName -> entityId.getOrElse(generateUUID()))
            // if it is there, we just return it. 
          case _: JString => requestJsonJObject
            //If it is Integer, we need to cast it to String.
          case JInt(i) => JsonUtils.transformField(requestJsonJObject){
            case (jField, "") if jField.name == entityIdName => JField(entityIdName, JString(i.toString))
          }.asInstanceOf[JObject]
            //If it contains `entityIdName` and value is EmptyString, we need to set the `entityId or new UUID` for the value.
          case _ => JsonUtils.transformField(requestJsonJObject){
            case (jField, "") if jField.name == entityIdName => JField(entityIdName, JString(entityId.getOrElse(generateUUID())))
          }.asInstanceOf[JObject]
        }
      }

      requestBodyDynamicInstance match {
          // @(variable-binding pattern), we can use the empty variable 
          // If there is not instance in requestBody, we just call the `dynamicEntityProcess` directly.
        case empty @None => 
          Connector.connector.vend.dynamicEntityProcess(operation, entityName, empty, entityId, bankId, queryParameters, userId, isPersonalEntity, callContext)
        // @(variable-binding pattern), we can use both v and body variables.
        case requestBody @Some(body) =>
          // If the request body is existing, we need to validate the body first. 
          val dynamicEntity: DynamicEntityT = dynamicEntityBox.openOrThrowException(DynamicEntityNotExists)
          dynamicEntity.validateEntityJson(body, callContext).flatMap {
            // If there is no error in the request body
            case None => 
              Connector.connector.vend.dynamicEntityProcess(operation, entityName, requestBody, entityId, bankId, queryParameters, userId,  isPersonalEntity, callContext)
            // If there are errors, we need to show them to end user. 
            case Some(errorMsg) => 
              Helper.booleanToFuture(s"$DynamicEntityInstanceValidateFail details: $errorMsg", cc=callContext)(false)
              .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
          }
      }
    }
    def dynamicEndpointProcess(url: String, jValue: JValue, method: HttpMethod, params: Map[String, List[String]], pathParams: Map[String, String],
      callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
      Connector.connector.vend.dynamicEndpointProcess(url, jValue, method, params, pathParams, callContext)
    }


    def createDirectDebit(bankId : String, 
                          accountId: String, 
                          customerId: String,
                          userId: String,
                          couterpartyId: String,
                          dateSigned: Date,
                          dateStarts: Date,
                          dateExpires: Option[Date], 
                          callContext: Option[CallContext]): OBPReturnType[DirectDebitTrait] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createDirectDebit(
        bankId, 
        accountId, 
        customerId, 
        userId, 
        couterpartyId, 
        dateSigned, 
        dateStarts, 
        dateExpires, 
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createStandingOrder(bankId: String,
                            accountId: String,
                            customerId: String,
                            userId: String,
                            couterpartyId: String,
                            amountValue: BigDecimal,
                            amountCurrency: String,
                            whenFrequency: String,
                            whenDetail: String,
                            dateSigned: Date,
                            dateStarts: Date,
                            dateExpires: Option[Date],
                            callContext: Option[CallContext]): OBPReturnType[StandingOrderTrait] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.createStandingOrder(
        bankId,
        accountId,
        customerId,
        userId,
        couterpartyId,
        amountValue,
        amountCurrency,
        whenFrequency,
        whenDetail,
        dateSigned,
        dateStarts,
        dateExpires,
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    private lazy val supportedConnectorNames: Set[String] = {
       APIUtil.getPropsValue("connector", "star") match {
         case "star" =>
           APIUtil.getPropsValue("starConnector_supported_types", "mapped")
           .split(',').map(_.trim).toSet
         case conn => Set(conn)
       }
    }

    def getSupportedConnectorNames(): List[String] = {
      Connector.nameToConnector.keys
        .filter(it => supportedConnectorNames.exists(it.startsWith(_)))
        .toList
    }

    def getConnectorByName(connectorName: String): Option[Connector] = {
      if(supportedConnectorNames.exists(connectorName.startsWith _)) {
        Connector.nameToConnector.get(connectorName).map(_())
      } else {
        None
      }
    }

    def getConnectorMethod(connectorName: String, methodName: String): Option[MethodSymbol] = {
      getConnectorByName(connectorName).flatMap(_.callableMethods.get(methodName))
    }

    def createDynamicEndpoint(bankId:Option[String], userId: String, swaggerString: String, callContext: Option[CallContext]): OBPReturnType[DynamicEndpointT] = {
      validateBankId(bankId, callContext)
      Future {
        (DynamicEndpointProvider.connectorMethodProvider.vend.create(bankId: Option[String], userId, swaggerString), callContext)
      } map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def updateDynamicEndpointHost(bankId: Option[String], userId: String, swaggerString: String, callContext: Option[CallContext]): OBPReturnType[DynamicEndpointT] = {
      validateBankId(bankId, callContext)
      Future {
        (DynamicEndpointProvider.connectorMethodProvider.vend.updateHost(bankId, userId, swaggerString), callContext)
      } map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getDynamicEndpoint(bankId: Option[String], dynamicEndpointId: String, callContext: Option[CallContext]): OBPReturnType[DynamicEndpointT] = {
      validateBankId(bankId, callContext)

      val dynamicEndpointBox: Box[DynamicEndpointT] = DynamicEndpointProvider.connectorMethodProvider.vend.get(bankId, dynamicEndpointId)
      val errorMessage = 
        if(bankId.isEmpty)
          DynamicEndpointNotFoundByDynamicEndpointId.replace("DYNAMIC_ENDPOINT_ID.", s"DYNAMIC_ENDPOINT_ID($dynamicEndpointId).")
        else
          DynamicEndpointNotFoundByDynamicEndpointId.replace("for DYNAMIC_ENDPOINT_ID.",s"for DYNAMIC_ENDPOINT_ID($dynamicEndpointId) and BANK_ID(${bankId.getOrElse("")}).")
      val dynamicEndpoint = unboxFullOrFail(dynamicEndpointBox, callContext, errorMessage, 404)
      Future{
        (dynamicEndpoint, callContext)
      }
    }

    def getDynamicEndpoints(bankId: Option[String], callContext: Option[CallContext]): OBPReturnType[List[DynamicEndpointT]] = {
      validateBankId(bankId, callContext)
      Future {
        (DynamicEndpointProvider.connectorMethodProvider.vend.getAll(bankId), callContext)
      }
    }

    def getDynamicEndpointsByUserId(userId: String, callContext: Option[CallContext]): OBPReturnType[List[DynamicEndpointT]] = Future {
      (DynamicEndpointProvider.connectorMethodProvider.vend.getDynamicEndpointsByUserId(userId), callContext)
    }
    /**
     * delete one DynamicEndpoint and corresponding entitlement and dynamic entitlement
     * @param dynamicEndpointId
     * @param callContext
     * @return
     */
    def deleteDynamicEndpoint(bankId: Option[String], dynamicEndpointId: String, callContext: Option[CallContext]): Future[Box[Boolean]] = {
      validateBankId(bankId, callContext)

      val dynamicEndpoint: OBPReturnType[DynamicEndpointT] = this.getDynamicEndpoint(bankId, dynamicEndpointId, callContext)
        for {
          (entity, _) <- dynamicEndpoint
          deleteEndpointResult: Box[Boolean] = {
            val roles = DynamicEndpointHelper.getRoles(bankId, dynamicEndpointId).map(_.toString())
            roles.foreach(ApiRole.removeDynamicApiRole(_))
            val rolesDeleteResult: Box[Boolean] = Entitlement.entitlement.vend.deleteEntitlements(roles)
              Box !! (rolesDeleteResult == Full(true))
            } 
          deleteSuccess = if(deleteEndpointResult.isDefined && deleteEndpointResult.head) {
            tryo {DynamicEndpointProvider.connectorMethodProvider.vend.delete(bankId, dynamicEndpointId)}
          }else{
            Full(false)
          }
        } yield {
          deleteSuccess
        }
    }

    def deleteCustomerAttribute(customerAttributeId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteCustomerAttribute(customerAttributeId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createCounterparty(
      name: String,
      description: String,
      currency: String,
      createdByUserId: String,
      thisBankId: String,
      thisAccountId: String,
      thisViewId: String,
      otherAccountRoutingScheme: String,
      otherAccountRoutingAddress: String,
      otherAccountSecondaryRoutingScheme: String,
      otherAccountSecondaryRoutingAddress: String,
      otherBankRoutingScheme: String,
      otherBankRoutingAddress: String,
      otherBranchRoutingScheme: String,
      otherBranchRoutingAddress: String,
      isBeneficiary:Boolean,
      bespoke: List[CounterpartyBespoke],
      callContext: Option[CallContext]) : OBPReturnType[CounterpartyTrait] = {
      Future {
        unboxFullOrFail(Connector.connector.vend.createCounterparty(
          name: String,
          description: String,
          currency: String,
          createdByUserId: String,
          thisBankId: String,
          thisAccountId: String,
          thisViewId: String,
          otherAccountRoutingScheme: String,
          otherAccountRoutingAddress: String,
          otherAccountSecondaryRoutingScheme: String,
          otherAccountSecondaryRoutingAddress: String,
          otherBankRoutingScheme: String,
          otherBankRoutingAddress: String,
          otherBranchRoutingScheme: String,
          otherBranchRoutingAddress: String,
          isBeneficiary:Boolean,
          bespoke: List[CounterpartyBespoke],
          callContext: Option[CallContext]
        ), callContext, s"$CreateCounterpartyError ")
      }}

    def getOrCreateMetadata(
      bankId: BankId, 
      accountId : AccountId, 
      counterpartyId:String, 
      counterpartyName:String,
      callContext: Option[CallContext]
    )  : OBPReturnType[CounterpartyMetadata]= {
      Future{(unboxFullOrFail(Counterparties.counterparties.vend.getOrCreateMetadata(bankId: BankId,
        accountId : AccountId,
        counterpartyId:String,
        counterpartyName:String
      ), callContext, CreateOrUpdateCounterpartyMetadataError), callContext)}
    }
    def deleteMetadata(
      bankId: BankId, 
      accountId : AccountId, 
      counterpartyId:String, 
      callContext: Option[CallContext]
    )  : OBPReturnType[Boolean]= {
      Future{(unboxFullOrFail(Counterparties.counterparties.vend.deleteMetadata(
        bankId: BankId,
        accountId : AccountId,
        counterpartyId:String
      ), callContext, DeleteCounterpartyMetadataError), callContext)}
    }

    def getPhysicalCardsForUser(user : User, callContext: Option[CallContext]) : OBPReturnType[List[PhysicalCard]] = {
      Connector.connector.vend.getPhysicalCardsForUser(user : User, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardNotFound"), i._2)
      }
    }

    def checkUKConsent(user: User, callContext: Option[CallContext]) = Future {
      Consent.checkUKConsent(user, callContext)
    } map { fullBoxOrException(_) }


    def getApiCollectionById(apiCollectionId : String, callContext: Option[CallContext]) : OBPReturnType[ApiCollectionTrait] = {
      Future(MappedApiCollectionsProvider.getApiCollectionById(apiCollectionId)) map {
        i => (unboxFullOrFail(i, callContext, s"$ApiCollectionNotFound Please specify a valid value for API_COLLECTION_ID. Current API_COLLECTION_ID($apiCollectionId) "), callContext)
      }
    }

    def getApiCollectionByUserIdAndCollectionName(userId : String, apiCollectionName : String, callContext: Option[CallContext]) : OBPReturnType[ApiCollectionTrait] = {
      Future(MappedApiCollectionsProvider.getApiCollectionByUserIdAndCollectionName(userId, apiCollectionName)) map {
        i => (unboxFullOrFail(i, callContext, s"$ApiCollectionNotFound Please specify a valid value for API_COLLECTION_NAME. Current API_COLLECTION_NAME($apiCollectionName) "), callContext)
      }
    }

    def getApiCollectionsByUserId(userId : String, callContext: Option[CallContext]) : OBPReturnType[List[ApiCollectionTrait]] = {
      Future(MappedApiCollectionsProvider.getApiCollectionsByUserId(userId), callContext) 
    }

    def getAllApiCollections(callContext: Option[CallContext]) : OBPReturnType[List[ApiCollectionTrait]] = {
      Future(MappedApiCollectionsProvider.getAllApiCollections(), callContext) 
    }

    def getFeaturedApiCollections(callContext: Option[CallContext]) : OBPReturnType[List[ApiCollectionTrait]] = {
      //we get the getFeaturedApiCollectionIds from props, and remove the deplication there.
      val featuredApiCollectionIds =  APIUtil.getPropsValue("featured_api_collection_ids","").split(",").map(_.trim).toSet.toList
      //We filter the isDefined and is isSharable collections.
      val apiCollections = featuredApiCollectionIds.map(MappedApiCollectionsProvider.getApiCollectionById).filter(_.isDefined).filter(_.head.isSharable).map(_.head)
      Future{(apiCollections.sortBy(_.apiCollectionName), callContext)}
    }
    
    def createApiCollection(
      userId: String,
      apiCollectionName: String,
      isSharable: Boolean,
      description: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[ApiCollectionTrait] = {
      Future(MappedApiCollectionsProvider.createApiCollection(
        userId: String,
        apiCollectionName: String,
        isSharable: Boolean,
        description: String)
      ) map {
        i => (unboxFullOrFail(i, callContext, CreateApiCollectionError), callContext)
      }
    }

    def getUserByUserId(userId : String, callContext: Option[CallContext]) : OBPReturnType[User] = {
      Users.users.vend.getUserByUserIdFuture(userId) map {
        x => (unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current USER_ID($userId) "),callContext)
      }
    }
    def getUsersByUserIds(userIds : List[String], callContext: Option[CallContext]) : OBPReturnType[List[User]] = {
      val users = for {
        userId <- userIds
        user <- Users.users.vend.getUserByUserId(userId)
        //(attributes, callContext) <- NewStyle.function.getUserAttributes(userId, callContext)
      } yield {
        user
      }
      Future(users,callContext)
    }

    def deleteApiCollectionById(apiCollectionId : String, callContext: Option[CallContext]) : OBPReturnType[Boolean] = {
      Future(MappedApiCollectionsProvider.deleteApiCollectionById(apiCollectionId)) map {
        i => (unboxFullOrFail(i, callContext, s"$DeleteApiCollectionError Current API_COLLECTION_ID($apiCollectionId) "), callContext)
      }
    }

    def createApiCollectionEndpoint(
      apiCollectionId: String,
      operationId: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[ApiCollectionEndpointTrait] = {
      Future(MappedApiCollectionEndpointsProvider.createApiCollectionEndpoint(
        apiCollectionId: String,
        operationId: String
      )) map {
        i => (unboxFullOrFail(i, callContext, CreateApiCollectionEndpointError), callContext)
      }
    }

    def getApiCollectionEndpointById(apiCollectionEndpointId : String, callContext: Option[CallContext]) : OBPReturnType[ApiCollectionEndpointTrait] = {
      Future(MappedApiCollectionEndpointsProvider.getApiCollectionEndpointById(apiCollectionEndpointId)) map {
        i => (unboxFullOrFail(i, callContext, s"$ApiCollectionEndpointNotFound Please specify a valid value for API_COLLECTION_ENDPOINT_ID. " +
          s"Current API_COLLECTION_ENDPOINT_ID($apiCollectionEndpointId) "), callContext)
      }
    }

    def getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollectionId:String, operationId : String, callContext: Option[CallContext]) : OBPReturnType[ApiCollectionEndpointTrait] = {
      Future(MappedApiCollectionEndpointsProvider.getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollectionId, operationId)) map {
        i => (unboxFullOrFail(i, callContext, s"$ApiCollectionEndpointNotFound Current API_COLLECTION_ID($apiCollectionId) and OPERATION_ID($operationId) "), callContext)
      }
    }

    def getApiCollectionEndpoints(apiCollectionId : String, callContext: Option[CallContext]) : OBPReturnType[List[ApiCollectionEndpointTrait]] = {
      Future(MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(apiCollectionId), callContext)
    }

    def deleteApiCollectionEndpointById(apiCollectionEndpointById : String, callContext: Option[CallContext]) : OBPReturnType[Boolean] = {
      Future(MappedApiCollectionEndpointsProvider.deleteApiCollectionEndpointById(apiCollectionEndpointById)) map {
        i => (unboxFullOrFail(i, callContext, s"$DeleteApiCollectionEndpointError Current API_COLLECTION_ENDPOINT_ID($apiCollectionEndpointById) "), callContext)
      }
    }

    def createJsonSchemaValidation(validation: JsonValidation, callContext: Option[CallContext]): OBPReturnType[JsonValidation] =
      Future {
        val newValidation = JsonSchemaValidationProvider.validationProvider.vend.create(validation)
        val errorMsg = s"$UnknownError Can not create JSON Schema Validation in the backend. "
        (unboxFullOrFail(newValidation, callContext, errorMsg, 400), callContext)
      }

    def updateJsonSchemaValidation(operationId: String, jsonschema: String, callContext: Option[CallContext]): OBPReturnType[JsonValidation] =
      Future {
        val updatedValidation = JsonSchemaValidationProvider.validationProvider.vend.update(JsonValidation(operationId, jsonschema))
        val errorMsg = s"$UnknownError Can not update JSON Schema Validation in the backend. "
        (unboxFullOrFail(updatedValidation, callContext, errorMsg, 400), callContext)
      }

    def getJsonSchemaValidations(callContext: Option[CallContext]): OBPReturnType[List[JsonValidation]] =
      Future {
        val validations: List[JsonValidation] = JsonSchemaValidationProvider.validationProvider.vend.getAll()
        validations -> callContext
      }

    def getJsonSchemaValidationByOperationId(operationId: String, callContext: Option[CallContext]): OBPReturnType[JsonValidation] =
      Future {
        val validation = JsonSchemaValidationProvider.validationProvider.vend.getByOperationId(operationId)
        (unboxFullOrFail(validation, callContext, JsonSchemaValidationNotFound, 400), callContext)
      }

    def deleteJsonSchemaValidation(operationId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result = JsonSchemaValidationProvider.validationProvider.vend.deleteByOperationId(operationId)
        (unboxFullOrFail(result, callContext, ValidationDeleteError, 400), callContext)
      }

    def isJsonSchemaValidationExists(operationId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result = JsonSchemaValidationProvider.validationProvider.vend.getByOperationId(operationId)
        (result.isDefined, callContext)
      }

    // authTypeValidation related functions
    def createAuthenticationTypeValidation(AuthTypeValidation: JsonAuthTypeValidation, callContext: Option[CallContext]): OBPReturnType[JsonAuthTypeValidation] =
      Future {
        val newAuthTypeValidation = AuthenticationTypeValidationProvider.validationProvider.vend.create(AuthTypeValidation)
        val errorMsg = s"$UnknownError Can not create Authentication Type Validation in the backend. "
        (unboxFullOrFail(newAuthTypeValidation, callContext, errorMsg, 400), callContext)
      }

    def updateAuthenticationTypeValidation(operationId: String, authTypes: List[AuthenticationType], callContext: Option[CallContext]): OBPReturnType[JsonAuthTypeValidation] =
      Future {
        val updatedAuthTypeValidation = AuthenticationTypeValidationProvider.validationProvider.vend.update(JsonAuthTypeValidation(operationId, authTypes))
        val errorMsg = s"$UnknownError Can not update Authentication Type Validation in the backend. "
        (unboxFullOrFail(updatedAuthTypeValidation, callContext, errorMsg, 400), callContext)
      }

    def getAuthenticationTypeValidations(callContext: Option[CallContext]): OBPReturnType[List[JsonAuthTypeValidation]] =
      Future {
        val AuthTypeValidations: List[JsonAuthTypeValidation] = AuthenticationTypeValidationProvider.validationProvider.vend.getAll()
        AuthTypeValidations -> callContext
      }

    def getAuthenticationTypeValidationByOperationId(operationId: String, callContext: Option[CallContext]): OBPReturnType[JsonAuthTypeValidation] =
      Future {
        val AuthTypeValidation = AuthenticationTypeValidationProvider.validationProvider.vend.getByOperationId(operationId)
        (unboxFullOrFail(AuthTypeValidation, callContext, AuthenticationTypeValidationNotFound, 400), callContext)
      }

    def deleteAuthenticationTypeValidation(operationId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result = AuthenticationTypeValidationProvider.validationProvider.vend.deleteByOperationId(operationId)
        (unboxFullOrFail(result, callContext, AuthenticationTypeValidationDeleteError, 400), callContext)
      }

    def isAuthenticationTypeValidationExists(operationId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result = AuthenticationTypeValidationProvider.validationProvider.vend.getByOperationId(operationId)
        (result.isDefined, callContext)
      }


    def createJsonConnectorMethod(connectorMethod: JsonConnectorMethod, callContext: Option[CallContext]): OBPReturnType[JsonConnectorMethod] =
      Future {
        val newInternalConnector = ConnectorMethodProvider.provider.vend.create(connectorMethod)
        val errorMsg = s"$UnknownError Can not create Connector Method in the backend. "
        (unboxFullOrFail(newInternalConnector, callContext, errorMsg, 400), callContext)
      }

    def updateJsonConnectorMethod(connectorMethodId: String, connectorMethodBody: String, programmingLang: String, callContext: Option[CallContext]): OBPReturnType[JsonConnectorMethod] =
      Future {
        val updatedConnectorMethod = ConnectorMethodProvider.provider.vend.update(connectorMethodId, connectorMethodBody, programmingLang)
        val errorMsg = s"$UnknownError Can not update Connector Method in the backend. "
        (unboxFullOrFail(updatedConnectorMethod, callContext, errorMsg, 400), callContext)
      }

    def connectorMethodExists(connectorMethodId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result =  ConnectorMethodProvider.provider.vend.getById(connectorMethodId)
        (result.isDefined, callContext)
      }

    def connectorMethodNameExists(connectorMethodName: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result =  ConnectorMethodProvider.provider.vend.getByMethodNameWithoutCache(connectorMethodName)
        (result.isDefined, callContext)
      }
    
    def getJsonConnectorMethods(callContext: Option[CallContext]): OBPReturnType[List[JsonConnectorMethod]] =
      Future {
        val connectorMethods: List[JsonConnectorMethod] = ConnectorMethodProvider.provider.vend.getAll()
        connectorMethods -> callContext
      }

    def getJsonConnectorMethodById(connectorMethodId: String, callContext: Option[CallContext]): OBPReturnType[JsonConnectorMethod] =
      Future {
        val connectorMethod = ConnectorMethodProvider.provider.vend.getById(connectorMethodId)
        (unboxFullOrFail(connectorMethod, callContext, s"$ConnectorMethodNotFound Current CONNECTOR_METHOD_ID(${connectorMethodId})", 400), callContext)
      }

    def isJsonDynamicResourceDocExists(bankId: Option[String], requestVerb : String, requestUrl : String,  callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result = DynamicResourceDocProvider.provider.vend.getByVerbAndUrl(bankId, requestVerb, requestUrl)
        (result.isDefined, callContext)
      }

    def createJsonDynamicResourceDoc(bankId: Option[String], dynamicResourceDoc: JsonDynamicResourceDoc, callContext: Option[CallContext]): OBPReturnType[JsonDynamicResourceDoc] =
      Future {
        val newInternalConnector = DynamicResourceDocProvider.provider.vend.create(bankId, dynamicResourceDoc)
        val errorMsg = s"$UnknownError Can not create Dynamic Resource Doc in the backend. "
        (unboxFullOrFail(newInternalConnector, callContext, errorMsg, 400), callContext)
      }

    def updateJsonDynamicResourceDoc(bankId: Option[String], entity: JsonDynamicResourceDoc, callContext: Option[CallContext]): OBPReturnType[JsonDynamicResourceDoc] =
      Future {
        val updatedConnectorMethod = DynamicResourceDocProvider.provider.vend.update(bankId, entity: JsonDynamicResourceDoc)
        val errorMsg = s"$UnknownError Can not update Dynamic Resource Doc in the backend. "
        (unboxFullOrFail(updatedConnectorMethod, callContext, errorMsg, 400), callContext)
      }

    def isJsonDynamicResourceDocExists(bankId: Option[String], dynamicResourceDocId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result =  DynamicResourceDocProvider.provider.vend.getById(bankId, dynamicResourceDocId)
        (result.isDefined, callContext)
      }

    def getJsonDynamicResourceDocs(bankId: Option[String], callContext: Option[CallContext]): OBPReturnType[List[JsonDynamicResourceDoc]] =
      Future {
        val dynamicResourceDocs: List[JsonDynamicResourceDoc] = DynamicResourceDocProvider.provider.vend.getAll(bankId)
        dynamicResourceDocs -> callContext
      }

    def getJsonDynamicResourceDocById(bankId: Option[String], dynamicResourceDocId: String, callContext: Option[CallContext]): OBPReturnType[JsonDynamicResourceDoc] =
      Future {
        val dynamicResourceDoc = DynamicResourceDocProvider.provider.vend.getById(bankId, dynamicResourceDocId)
        (unboxFullOrFail(dynamicResourceDoc, callContext, s"$DynamicResourceDocNotFound Current DYNAMIC_RESOURCE_DOC_ID(${dynamicResourceDocId})", 400), callContext)
      }

    def deleteJsonDynamicResourceDocById(bankId: Option[String], dynamicResourceDocId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val dynamicResourceDoc = DynamicResourceDocProvider.provider.vend.deleteById(bankId, dynamicResourceDocId)
        (unboxFullOrFail(dynamicResourceDoc, callContext, s"$DynamicResourceDocDeleteError Current DYNAMIC_RESOURCE_DOC_ID(${dynamicResourceDocId})", 400), callContext)
      }

    def createJsonDynamicMessageDoc(bankId: Option[String], dynamicMessageDoc: JsonDynamicMessageDoc, callContext: Option[CallContext]): OBPReturnType[JsonDynamicMessageDoc] =
      Future {
        val newInternalConnector = DynamicMessageDocProvider.provider.vend.create(bankId, dynamicMessageDoc)
        val errorMsg = s"$UnknownError Can not create Dynamic Message Doc in the backend. "
        (unboxFullOrFail(newInternalConnector, callContext, errorMsg, 400), callContext)
      }

    def updateJsonDynamicMessageDoc(bankId: Option[String], entity: JsonDynamicMessageDoc, callContext: Option[CallContext]): OBPReturnType[JsonDynamicMessageDoc] =
      Future {
        val updatedConnectorMethod = DynamicMessageDocProvider.provider.vend.update(bankId: Option[String], entity: JsonDynamicMessageDoc)
        val errorMsg = s"$UnknownError Can not update Dynamic Message Doc  in the backend. "
        (unboxFullOrFail(updatedConnectorMethod, callContext, errorMsg, 400), callContext)
      }

    def isJsonDynamicMessageDocExists(bankId: Option[String], process: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val result =  DynamicMessageDocProvider.provider.vend.getByProcess(bankId, process)
        (result.isDefined, callContext)
      }

    def getJsonDynamicMessageDocs(bankId: Option[String], callContext: Option[CallContext]): OBPReturnType[List[JsonDynamicMessageDoc]] =
      Future {
        val dynamicMessageDocs: List[JsonDynamicMessageDoc] = DynamicMessageDocProvider.provider.vend.getAll(bankId)
        dynamicMessageDocs -> callContext
      }

    def getJsonDynamicMessageDocById(bankId: Option[String], dynamicMessageDocId: String, callContext: Option[CallContext]): OBPReturnType[JsonDynamicMessageDoc] =
      Future {
        val dynamicMessageDoc = DynamicMessageDocProvider.provider.vend.getById(bankId, dynamicMessageDocId)
        (unboxFullOrFail(dynamicMessageDoc, callContext, s"$DynamicMessageDocNotFound Current DYNAMIC_RESOURCE_DOC_ID(${dynamicMessageDocId})", 400), callContext)
      }

    def deleteJsonDynamicMessageDocById(bankId: Option[String], dynamicMessageDocId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Future {
        val dynamicMessageDoc = DynamicMessageDocProvider.provider.vend.deleteById(bankId, dynamicMessageDocId)
        (unboxFullOrFail(dynamicMessageDoc, callContext, s"$DynamicMessageDocDeleteError", 400), callContext)
      }

    def validateUserAuthContextUpdateRequest(bankId: String, userId: String, key: String, value: String, scaMethod: String, callContext: Option[CallContext]): OBPReturnType[UserAuthContextUpdate] = {
      validateBankId(bankId, callContext)

      Connector.connector.vend.validateUserAuthContextUpdateRequest(bankId, userId, key, value, scaMethod, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def checkAnswer(authContextUpdateId: String, challenge: String, callContext: Option[CallContext]):  OBPReturnType[UserAuthContextUpdate] = {
      Connector.connector.vend.checkAnswer(authContextUpdateId: String, challenge: String, callContext: Option[CallContext]) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String, callContext: Option[CallContext]): OBPReturnType[CustomerAccountLinkTrait] =
      Connector.connector.vend.createCustomerAccountLink(customerId: String, bankId, accountId: String, relationshipType: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, CreateCustomerAccountLinkError), i._2)
      }
    
    def getCustomerAccountLinksByCustomerId(customerId: String, callContext: Option[CallContext]): OBPReturnType[List[CustomerAccountLinkTrait]] =
      Connector.connector.vend.getCustomerAccountLinksByCustomerId(customerId: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, GetCustomerAccountLinksError), i._2)
      }
    
    def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String, callContext: Option[CallContext]): OBPReturnType[List[CustomerAccountLinkTrait]] =
      Connector.connector.vend.getCustomerAccountLinksByBankIdAccountId(bankId, accountId: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, GetCustomerAccountLinksError), i._2)
      }
    
    def getCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]): OBPReturnType[CustomerAccountLinkTrait] =
      Connector.connector.vend.getCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, CustomerAccountLinkNotFoundById), i._2)
      }
    
    def deleteCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] =
      Connector.connector.vend.deleteCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, DeleteCustomerAccountLinkError), i._2)
      }
    
    def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String, callContext: Option[CallContext]): OBPReturnType[CustomerAccountLinkTrait] =
      Connector.connector.vend.updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerAccountLinkError), i._2)
      }
  }

}
