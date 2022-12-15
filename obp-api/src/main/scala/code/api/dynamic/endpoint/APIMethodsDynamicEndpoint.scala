package code.api.dynamic.endpoint

import code.DynamicData.{DynamicData, DynamicDataProvider}
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

trait APIMethodsDynamicEndpoint {
  self: RestHelper =>

  val ImplementationsDynamicEndpoint = new ImplementationsDynamicEndpoint()

  class ImplementationsDynamicEndpoint {

    val implementedInApiVersion = ApiVersion.`dynamic-endpoint`

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
                    DynamicDataProvider.connectorMethodProvider.vend.getAll(bankId, entityName, None, false)
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
                  (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(entityBody.asInstanceOf[JObject]), None, None, None, None, false, Some(cc))
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
                  dynamicData = DynamicDataProvider.connectorMethodProvider.vend.getAll(bankId, entityName, None,false)
                  dynamicJsonData = JArray(dynamicData.map(it => net.liftweb.json.parse(it.dataJson)).map(_.asInstanceOf[JObject]))
                  entityObject = DynamicEndpointHelper.getObjectByKeyValuePair(dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                  isDeleted <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    val entityIdName = DynamicEntityHelper.createEntityId(entityName)
                    val entityIdValue = (entityObject \ entityIdName).asInstanceOf[JString].s
                    DynamicDataProvider.connectorMethodProvider.vend.delete(bankId, entityName, entityIdValue, None, false).head
                  }
                } yield {
                  JBool(isDeleted)
                }
              } else if (method.value.equalsIgnoreCase("put")) {
                for {
                  (entityName, entityIdKey, entityIdValueFromUrl) <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    DynamicEndpointHelper.getEntityNameKeyAndValue(responseMappingString, pathParams)
                  }
                  dynamicData = DynamicDataProvider.connectorMethodProvider.vend.getAll(bankId, entityName, None, false)
                  dynamicJsonData = JArray(dynamicData.map(it => net.liftweb.json.parse(it.dataJson)).map(_.asInstanceOf[JObject]))
                  entityObject = DynamicEndpointHelper.getObjectByKeyValuePair(dynamicJsonData, entityIdKey, entityIdValueFromUrl.get)
                  _ <- NewStyle.function.tryons(s"$InvalidEndpointMapping `response_mapping` must be linked to at least one valid dynamic entity!", 400, cc.callContext) {
                    val entityIdName = DynamicEntityHelper.createEntityId(entityName)
                    val entityIdValue = (entityObject \ entityIdName).asInstanceOf[JString].s
                    DynamicDataProvider.connectorMethodProvider.vend.delete(bankId, entityName, entityIdValue, None, false).head
                  }
                  entityBody = JsonUtils.buildJson(json, requestMappingJvalue)
                  (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(entityBody.asInstanceOf[JObject]), None, bankId, None, None, false, Some(cc))
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

object APIMethodsDynamicEndpoint extends RestHelper with APIMethodsDynamicEndpoint {
  lazy val newStyleEndpoints: List[(String, String)] = ImplementationsDynamicEndpoint.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

