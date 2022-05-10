package code.api.dynamic

import code.DynamicData.{DynamicData, DynamicDataProvider}
import code.api.dynamic.helper.DynamicEndpointHelper.DynamicReq
import code.api.dynamic.helper.{DynamicEndpointHelper, DynamicEntityHelper, DynamicEntityInfo, EntityName, MockResponseHolder}
import code.api.util.APIUtil.{fullBoxOrException, _}
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

trait APIMethodsDynamic {
  self: RestHelper =>

  val ImplementationsDynamic = new ImplementationsDynamic()

  class ImplementationsDynamic {

    val implementedInApiVersion = ApiVersion.v4_0_0

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
            params.forall { kv =>
              val (path, values) = kv
              values.exists(JsonUtils.isFieldEquals(jValue, path, _))
            }
          }

          JArray(filteredWithFieldValue)
      }
    }

    lazy val genericEndpoint: OBPEndpoint = {
      case EntityName(bankId, entityName, id) JsonGet req => { cc =>
        val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val isGetAll = StringUtils.isBlank(id)

        val operation: DynamicEntityOperation = if (StringUtils.isBlank(id)) GET_ALL else GET_ONE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
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

          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, Some(cc))

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

      case EntityName(bankId, entityName, _) JsonPost json -> _ => { cc =>
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val operation: DynamicEntityOperation = CREATE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
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
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canCreateRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), None, bankId, None, Some(cc))
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
      case EntityName(bankId, entityName, id) JsonPut json -> _ => { cc =>
        val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
        val operation: DynamicEntityOperation = UPDATE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
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
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canUpdateRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(cc))
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc = callContext) {
            box.isDefined
          }
          (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), Some(id), bankId, None, Some(cc))
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
      case EntityName(bankId, entityName, id) JsonDelete _ => { cc =>
        val operation: DynamicEntityOperation = DELETE
        val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> entityName)
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
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canDeleteRole(entityName, bankId), callContext)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }

          (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(cc))
          _ <- Helper.booleanToFuture(EntityNotFoundByEntityId, 404, cc = callContext) {
            box.isDefined
          }
          (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Some(id), bankId, None, Some(cc))
          deleteResult: JBool = unboxResult(box.asInstanceOf[Box[JBool]], entityName)
        } yield {
          (deleteResult, HttpCode.`204`(Some(cc)))
        }
      }
    }

    lazy val dynamicEndpoint: OBPEndpoint = {
      case DynamicReq(url, json, method, params, pathParams, role, operationId, mockResponse, bankId) => { cc =>
        // process before authentication interceptor, get intercept result
        val resourceDoc = DynamicEndpointHelper.doc.find(_.operationId == operationId)
        val callContext = cc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)
        val beforeInterceptResult: Box[JsonResponse] = beforeAuthenticateInterceptResult(Option(callContext), operationId)
        if (beforeInterceptResult.isDefined) beforeInterceptResult
        else for {
          (Full(u), callContext) <- authenticatedAccess(callContext) // Inject operationId into Call Context. It's used by Rate Limiting.
          _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, role, callContext)

          // validate request json payload
          httpRequestMethod = cc.verb
          path = StringUtils.substringAfter(cc.url, DynamicEndpointHelper.urlPrefix)

          // process after authentication interceptor, get intercept result
          jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
            case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
          })
          _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
            jsonResponse.isEmpty
          }
          (box, callContext) <- if (DynamicEndpointHelper.isDynamicEntityResponse(url)) {
            for {
              (endpointMapping, callContext) <- if (DynamicEndpointHelper.isDynamicEntityResponse(url)) {
                NewStyle.function.getEndpointMappingByOperationId(bankId, operationId, cc.callContext)
              } else {
                Future.successful((EndpointMappingCommons(None, "", "", "", None), callContext))
              }
              requestMappingString = endpointMapping.requestMapping
              requestMappingJvalue = net.liftweb.json.parse(requestMappingString)
              responseMappingString = endpointMapping.responseMapping
              responseMappingJvalue = net.liftweb.json.parse(responseMappingString)

              responseBody <- if (method.value.equalsIgnoreCase("get")) {
                for {
                  (entityName, entityIdKey, entityIdValueFromUrl) <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                  }
                  dynamicData <- Future {
                    DynamicDataProvider.connectorMethodProvider.vend.getAll(entityName)
                  }
                  dynamicJsonData = JArray(dynamicData.map(it => net.liftweb.json.parse(it.dataJson)).map(_.asInstanceOf[JObject]))
                  //                //We only get the value, but not sure the field name of it.
                  //                // we can get the field name from the mapping: `primary_query_key`
                  //                //requestBodyMapping --> Convert `RequestJson` --> `DynamicEntity Model.`  
                  //                targetRequestBody = JsonUtils.buildJson(json, requestBodySchemeJvalue)
                  //                requestBody = targetRequestBody match {
                  //                  case j@JObject(_) => Some(j)
                  //                  case _ => None
                  //                }
                  result = if (method.value.equalsIgnoreCase("get") && entityIdValueFromUrl.isDefined) {
                    DynamicEndpointHelper.getObjectByKeyValuePair(dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                  } else {
                    val newParams = DynamicEndpointHelper.convertToMappingQueryParams(responseMappingJvalue, params)
                    DynamicEndpointHelper.getObjectsByParams(dynamicJsonData, newParams)
                  }
                  responseBodyScheme = DynamicEndpointHelper.prepareMappingFields(responseMappingJvalue)
                  responseBody = JsonUtils.buildJson(result, responseBodyScheme)
                } yield {
                  responseBody
                }
              } else if (method.value.equalsIgnoreCase("post")) {
                for {
                  (entityName, entityIdKey, entityIdValueFromUrl) <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                  }
                  //build the entity body according to the request json and mapping
                  entityBody = JsonUtils.buildJson(json, requestMappingJvalue)
                  (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(entityBody.asInstanceOf[JObject]), None, None, None, Some(cc))
                  singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
                  responseBodyScheme = DynamicEndpointHelper.prepareMappingFields(responseMappingJvalue)
                  responseBody = JsonUtils.buildJson(singleObject, responseBodyScheme)
                } yield {
                  responseBody
                }
              } else if (method.value.equalsIgnoreCase("delete")) {
                for {
                  (entityName, entityIdKey, entityIdValueFromUrl) <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                  }
                  dynamicData = DynamicDataProvider.connectorMethodProvider.vend.getAll(entityName)
                  dynamicJsonData = JArray(dynamicData.map(it => net.liftweb.json.parse(it.dataJson)).map(_.asInstanceOf[JObject]))
                  entityObject = DynamicEndpointHelper.getObjectByKeyValuePair(dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                  isDeleted <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    val entityIdName = DynamicEntityHelper.createEntityId(entityName)
                    val entityIdValue = (entityObject \ entityIdName).asInstanceOf[JString].s
                    DynamicDataProvider.connectorMethodProvider.vend.delete(entityName, entityIdValue).head
                  }
                } yield {
                  JBool(isDeleted)
                }
              } else if (method.value.equalsIgnoreCase("put")) {
                for {
                  (entityName, entityIdKey, entityIdValueFromUrl) <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                  }
                  dynamicData = DynamicDataProvider.connectorMethodProvider.vend.getAll(entityName)
                  dynamicJsonData = JArray(dynamicData.map(it => net.liftweb.json.parse(it.dataJson)).map(_.asInstanceOf[JObject]))
                  entityObject = DynamicEndpointHelper.getObjectByKeyValuePair(dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                  _ <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    val entityIdName = DynamicEntityHelper.createEntityId(entityName)
                    val entityIdValue = (entityObject \ entityIdName).asInstanceOf[JString].s
                    DynamicDataProvider.connectorMethodProvider.vend.delete(entityName, entityIdValue).head
                  }
                  entityBody = JsonUtils.buildJson(json, requestMappingJvalue)
                  (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(entityBody.asInstanceOf[JObject]), None, None, None, Some(cc))
                  singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
                  responseBodyScheme = DynamicEndpointHelper.prepareMappingFields(responseMappingJvalue)
                  responseBody = JsonUtils.buildJson(singleObject, responseBodyScheme)
                } yield {
                  responseBody
                }
              } else {
                NewStyle.function.tryons(s"$InvalidEndpointMapping `request_mapping` must  be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                  DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                }
                throw new RuntimeException(s"$NotImplemented We only support the Http Methods GET and POST . The current method is: ${method.value}")
              }
            } yield {
              (Full(("code", 200) ~ ("value", responseBody)), callContext)
            }
          } else {
            MockResponseHolder.init(mockResponse) { // if target url domain is `obp_mock`, set mock response to current thread
              NewStyle.function.dynamicEndpointProcess(url, json, method, params, pathParams, callContext)
            }
          }
        } yield {
          box match {
            case Full(v) =>
              val code = (v \ "code").asInstanceOf[JInt].num.toInt
              (v \ "value", callContext.map(_.copy(httpCode = Some(code))))

            case e: Failure =>
              val changedMsgFailure = e.copy(msg = s"$InternalServerError ${e.msg}")
              fullBoxOrException[JValue](changedMsgFailure)
              ??? // will not execute to here, Because the failure message is thrown by upper line.
          }

        }
      }
    }
  }
}

object APIMethodsDynamic extends RestHelper with APIMethodsDynamic {
  lazy val newStyleEndpoints: List[(String, String)] = ImplementationsDynamic.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

