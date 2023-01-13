package code.api.dynamic.entity

import code.DynamicData.{DynamicData, DynamicDataProvider}
import code.api.Constant.PARAM_LOCALE
import code.api.dynamic.endpoint.helper.{DynamicEndpointHelper, MockResponseHolder}
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper.DynamicReq
import code.api.dynamic.endpoint.helper.MockResponseHolder
import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo, EntityName}
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.endpointMapping.EndpointMappingCommons
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.util.{ApiVersion, JsonUtils}
import net.liftweb.common._
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

trait APIMethodsDynamicEntity {
  self: RestHelper =>

  val ImplementationsDynamicEntity = new ImplementationsDynamicEntity()

  class ImplementationsDynamicEntity {

    val implementedInApiVersion = ApiVersion.`dynamic-entity`

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()

    // createDynamicEntityDoc and updateDynamicEntityDoc are dynamic, So here dynamic create resourceDocs
    def resourceDocs = staticResourceDocs

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)

    private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
      if (box.isInstanceOf[Failure]) {
        val failure = box.asInstanceOf[Failure]
        // change the internal db column name 'dynamicdataid' to entity's id name
        val msg = failure.msg.replace(DynamicData.DynamicDataId.dbColumnName, StringUtils.uncapitalize(entityName) + "Id")
        val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
        fullBoxOrException[T](changedMsgFailure)
      }

      box.openOrThrowException("impossible error")
    }

    //TODO temp solution to support query by field name and value
    private def filterDynamicObjects(resultList: JArray, req: Req): JArray = {
      req.params match {
        case map if map.isEmpty => resultList
        case params =>
          val filteredWithFieldValue = resultList.arr.filter { jValue =>
            params.filter(_._1!=PARAM_LOCALE).forall { kv =>
              val (path, values) = kv
              values.exists(JsonUtils.isFieldEquals(jValue, path, _))
            }
          }

          JArray(filteredWithFieldValue)
      }
    }

    lazy val genericEndpoint: OBPEndpoint = {
      case EntityName(bankId, entityName, id, isPersonalEntity) JsonGet req => { cc =>
        val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val isGetAll = StringUtils.isBlank(id)

        // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
        val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
        val splitName = s"""${capitalizedNameParts.mkString(" ")}"""
        val splitNameWithBankId = if (bankId.isDefined)
          s"""$splitName(${bankId.getOrElse("")})"""
        else
          s"""$splitName"""
        val mySplitNameWithBankId = s"My$splitNameWithBankId"

        val operation: DynamicEntityOperation = if (StringUtils.isBlank(id)) GET_ALL else GET_ONE
        val resourceDoc = if(isPersonalEntity) 
          DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId) 
        else 
          DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)
        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if (beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.

          (_, callContext) <-
            if (bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful {
                ("", callContext)
              }
            }

          _ <- if (isPersonalEntity) {
            Future.successful(true)
          } else {
            NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
          }

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, 
            Some(u.userId),
            isPersonalEntity,
            Some(cc)
          )

          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc = callContext) {
            box.isDefined
          }
        } yield {
          val jValue = if (isGetAll) {
            val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
            if (bankId.isDefined) {
              val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
              val result: JObject = (listName -> filterDynamicObjects(resultList, req))
              bankIdJobject merge result
            } else {
              val result: JObject = (listName -> filterDynamicObjects(resultList, req))
              result
            }
          } else {
            val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
            if (bankId.isDefined) {
              val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
              val result: JObject = (singleName -> singleObject)
              bankIdJobject merge result
            } else {
              val result: JObject = (singleName -> singleObject)
              result
            }
          }
          (jValue, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(bankId, entityName, _, isPersonalEntity) JsonPost json -> _ => { cc =>
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val operation: DynamicEntityOperation = CREATE
        // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
        val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
        val splitName = s"""${capitalizedNameParts.mkString(" ")}"""
        val splitNameWithBankId = if (bankId.isDefined)
          s"""$splitName(${bankId.getOrElse("")})"""
        else
          s"""$splitName"""
        val mySplitNameWithBankId = s"My$splitNameWithBankId"
        
        val resourceDoc = if(isPersonalEntity) 
          DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
        else
          DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
          
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)

        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if (beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          (_, callContext) <-
            if (bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful {
                ("", callContext)
              }
            }

          _ <- if (isPersonalEntity) {
            Future.successful(true)
          } else {
            NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canCreateRole(entityName, bankId), callContext)
          }

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), None, bankId, None, Some(u.userId),  isPersonalEntity, Some(cc))
          singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
        } yield {
          val result: JObject = (singleName -> singleObject)
          val entity = if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            bankIdJobject merge result
          } else {
            result
          }
          (entity, HttpCode.`201`(Some(cc)))
        }
      }
      case EntityName(bankId, entityName, id, isPersonalEntity) JsonPut json -> _ => { cc =>
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val operation: DynamicEntityOperation = UPDATE
        // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
        val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
        val splitName = s"""${capitalizedNameParts.mkString(" ")}"""
        val splitNameWithBankId = if (bankId.isDefined)
          s"""$splitName(${bankId.getOrElse("")})"""
        else
          s"""$splitName"""
        val mySplitNameWithBankId = s"My$splitNameWithBankId"

        val resourceDoc = if(isPersonalEntity)
          DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
        else
          DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
          
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)

        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if (beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          (_, callContext) <-
            if (bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful {
                ("", callContext)
              }
            }
          _ <- if (isPersonalEntity) {
            Future.successful(true)
          } else {
            NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canUpdateRole(entityName, bankId), callContext)
          }

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None,
            Some(u.userId),
            isPersonalEntity,
            Some(cc))
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc = callContext) {
            box.isDefined
          }
          (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), Some(id), bankId, None,
            Some(u.userId),
            isPersonalEntity,
            Some(cc))
          singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
        } yield {
          val result: JObject = (singleName -> singleObject)
          val entity = if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            bankIdJobject merge result
          } else {
            result
          }
          (entity, HttpCode.`200`(Some(cc)))
        }
      }
      case EntityName(bankId, entityName, id, isPersonalEntity) JsonDelete _ => { cc =>
        val operation: DynamicEntityOperation = DELETE
        // e.g: "someMultiple-part_Name" -> ["Some", "Multiple", "Part", "Name"]
        val capitalizedNameParts = entityName.split("(?<=[a-z0-9])(?=[A-Z])|-|_").map(_.capitalize).filterNot(_.trim.isEmpty)
        val splitName = s"""${capitalizedNameParts.mkString(" ")}"""
        val splitNameWithBankId = if (bankId.isDefined)
          s"""$splitName(${bankId.getOrElse("")})"""
        else
          s"""$splitName"""
        val mySplitNameWithBankId = s"My$splitNameWithBankId"

        val resourceDoc = if(isPersonalEntity)
          DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
        else
          DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
          
        val operationId = resourceDoc.map(_.operationId).orNull
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)

        // process before authentication interceptor, get intercept result
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if (beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          (_, callContext) <-
            if (bankId.isDefined) { //if it is the bank level entity, we need to check the bankId
              NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
            } else {
              Future.successful {
                ("", callContext)
              }
            }

          _ <- if (isPersonalEntity) {
            Future.successful(true)
          } else {
            NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canDeleteRole(entityName, bankId), callContext)
          }

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None,
            Some(u.userId),
            isPersonalEntity,
            Some(cc)
          )
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc = callContext) {
            box.isDefined
          }
          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Some(id), bankId, None,
            Some(u.userId),
            isPersonalEntity,
            Some(cc)
          )
          deleteResult: JBool = unboxResult(box.asInstanceOf[Box[JBool]], entityName)
        } yield {
          (deleteResult, HttpCode.`200`(Some(cc)))
        }
      }
    }
  }
}

object APIMethodsDynamicEntity extends RestHelper with APIMethodsDynamicEntity {
  lazy val newStyleEndpoints: List[(String, String)] = ImplementationsDynamicEntity.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

