package code.api.ResourceDocs1_4_0

import java.util.{Date, Objects}

import code.api.util.APIUtil.{HTTPParam, EmptyBody, JArrayBody, PrimaryDataBody, ResourceDoc}
import code.api.util.ErrorMessages._
import code.api.util._
import com.openbankproject.commons.util.{ApiVersion, EnumValue, JsonAble, JsonUtils, OBPEnumeration, ReflectUtils, ScannedApiVersion, SwaggerTypes}
import org.json4s.JsonAST.JValue
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._
import java.lang.{Boolean => XBoolean, Double => XDouble, Float => XFloat, Integer => XInt, Long => XLong, String => XString}
import java.math.{BigDecimal => JBigDecimal}

// Commented out: Lift endpoints removed (AUOpenBanking, Polish, STET)
//import code.api.AUOpenBanking.v1_0_0.ApiCollector
import code.api.Constant
//import code.api.Polish.v2_1_1_1.OBP_PAPI_2_1_1_1
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{NotSupportedYet, notSupportedYet}
//import code.api.STET.v1_4.OBP_STET_1_4
import code.api.UKOpenBanking.v2_0_0.OBP_UKOpenBanking_200
import code.api.UKOpenBanking.v3_1_0.OBP_UKOpenBanking_310
import code.api.UKOpenBanking.v4_0_1.OBP_UKOpenBanking_401
import code.api.berlin.group.v1_3.{OBP_BERLIN_GROUP_1_3, OBP_BERLIN_GROUP_1_3_Alias}
import code.api.v1_4_0.JSONFactory1_4_0
import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.util.StringHelpers

import scala.collection.mutable.ListBuffer
import com.openbankproject.commons.model.ListResult
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box.tryo
import net.liftweb.common.{EmptyBox, Full}
import com.openbankproject.commons.util.json

import scala.reflect.runtime.universe

object SwaggerJSONFactory extends MdcLoggable {
  // GenTraversableLike is gone in 2.13. This alias only ever feeds reflective subtype tests
  // against declared field types - List[X], Seq[X], Set[X] - so it needs to be a supertype of all
  // of them and nothing more; no method is ever called through it.
  //
  // IterableOnce, not Iterable, and the difference is not cosmetic. These tests run through
  // scala-reflect at run time, and 2.13's Iterable carries a deep base-class graph (IterableOps,
  // IterableFactoryDefaults and friends) that the runtime member search walks for every candidate
  // field. With Iterable here, SwaggerFactoryUnitTest hangs and then dies with a StackOverflowError
  // inside FindMembers/AsSeenFromMap. IterableOnce is a two-method trait, which is as shallow as
  // 2.12's GenTraversableLike was, and it is also the closest match to the GenTraversableOnce the
  // rest of this migration replaced.
  //
  // Runtime pattern matches that go on to call head or nonEmpty match Iterable directly rather
  // than going through this alias, since IterableOnce has neither.
  type Coll[T] = IterableOnce[T]

  /**
   * Escapes a string value to be safely included in JSON.
   * Handles quotes, backslashes, newlines, and other special characters.
   */
  private def escapeJsonString(value: String): String = {
    if (value == null) return ""
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
  }

  /**
   * Safely converts any value to a JSON example string.
   * Handles JValue, String, and other types with proper escaping.
   */
  private def safeExampleValue(value: Any): String = {
    value match {
      case null | None => ""
      case v: JValue => try { escapeJsonString(JsonUtils.toString(v)) } catch { case e: Exception => logger.warn(s"Failed to convert JValue to string for example: ${e.getMessage}"); "" }
      case v: String => escapeJsonString(v)
      case v => escapeJsonString(v.toString)
    }
  }
  //Info Object
  //link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#infoObject
  case class InfoJson(
    title: String,
    description: String,
    contact: InfoContactJson,
    version: String
  )
  //Contact Object
  //https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#contactObject
  case class InfoContactJson(
    name: String,
    url: String,
    email: String
  )
  
  // Security Definitions Object
  // link->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#securityDefinitionsObject
  case class SecurityDefinitionsJson(
    directLogin: DirectLoginJson ,
    gatewayLogin: GatewayLoginJson
  )
  // NOTE: For full authentication documentation, see the securitySchemes in OpenAPI31JSONFactory.scala
  // which is the source of truth. These Swagger 2.0 definitions are kept for backward compatibility.
  case class DirectLoginJson(
    `type`: String = "apiKey",
    description: String = "Direct Login authentication. POST to /obp/v6.0.0/my/logins/direct with header 'DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY' to obtain a token. Then use header 'DirectLogin: token=YOUR_TOKEN' on subsequent requests. See the OpenAPI 3.1 spec for full details.",
    in: String = "header",
    name: String = "DirectLogin"
  )

  case class GatewayLoginJson(
    `type`: String = "apiKey",
    description: String = "Gateway Login authentication. The gateway sends a JWT in the Authorization header signed with a pre-shared secret. See the OpenAPI 3.1 spec for full details.",
    in: String = "header",
    name: String = "Authorization"
  )
  
  //Security Requirement Object
  //link -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#securityRequirementObject
  case class SecurityJson(
    directLogin: List[String] = Nil,
    gatewayLogin: List[String] = Nil
  )
  
  sealed trait ResponseObjectSchemaJson

  case class RefSchemaJson(`$ref`: String) extends ResponseObjectSchemaJson
  case class BasicTypeSchemaJson(`type`: String) extends ResponseObjectSchemaJson
  case class ListResultSchemaJson(listResult: ListResult[List[_]]) extends ResponseObjectSchemaJson with JsonAble {

    override def toJValue(implicit format: Formats): json.JValue = {
      val ListResult(name, head::_) = listResult
      val schema = buildSwaggerSchema(ReflectUtils.getType(head), head)
      val definition =
          s"""
            |{
            |     "type": "object",
            |     "required": [
            |          "$name"
            |     ],
            |     "properties": {
            |          "$name": {
            |               "type": "array",
            |               "items": $schema
            |          }
            |     }
            |}
            |""".stripMargin
      try {
        json.parse(definition)
      } catch {
        case e: Exception =>
          logger.error(s"Failed to parse ListResult schema JSON: ${e.getMessage}\nJSON was: $definition")
          throw new RuntimeException(s"Invalid JSON in ListResult schema generation: ${e.getMessage}", e)
      }
    }
  }
  case class JObjectSchemaJson(jObject: JObject) extends ResponseObjectSchemaJson with JsonAble {

    override def toJValue(implicit format: Formats): json.JValue = {
      val schema = buildSwaggerSchema(SwaggerTypes.tJObject, jObject)
      try {
        json.parse(schema)
      } catch {
        case e: Exception =>
          logger.error(s"Failed to parse JObject schema JSON: ${e.getMessage}\nSchema was: $schema")
          throw new RuntimeException(s"Invalid JSON in JObject schema generation: ${e.getMessage}", e)
      }
    }

  }
  case class JArraySchemaJson(jArray: JArray) extends ResponseObjectSchemaJson with JsonAble {

    override def toJValue(implicit format: Formats): json.JValue = {
      val schema = buildSwaggerSchema(SwaggerTypes.tJArray, jArray)
      try {
        json.parse(schema)
      } catch {
        case e: Exception =>
          logger.error(s"Failed to parse JArray schema JSON: ${e.getMessage}\nSchema was: $schema")
          throw new RuntimeException(s"Invalid JSON in JArray schema generation: ${e.getMessage}", e)
      }
    }

  }

  object ResponseObjectSchemaJson {
    def apply(`$ref`: String): ResponseObjectSchemaJson =
      RefSchemaJson(`$ref`)

    def apply(listResult: ListResult[List[_]]): ResponseObjectSchemaJson =
      ListResultSchemaJson(listResult)

    def apply(bodyExample: PrimaryDataBody[_]) : ResponseObjectSchemaJson = bodyExample match {
      case JArrayBody(v) => JArraySchemaJson(v)
      case _ => BasicTypeSchemaJson(bodyExample.swaggerDataTypeName)
    }

    def apply(jObject:JObject) = JObjectSchemaJson(jObject)

    def getRequestBodySchema(value: Any): Option[ResponseObjectSchemaJson] =
      getSchema(value)

    def getResponseBodySchema(value: Any): Option[ResponseObjectSchemaJson] =
      getSchema(value)

    private def getSchema(value: Any): Option[ResponseObjectSchemaJson] = {
      value match {
        case JNothing => None
        case EmptyBody => None
        case example: PrimaryDataBody[_] => Some(ResponseObjectSchemaJson(example))
        case example: JObject => Some(JObjectSchemaJson(example))
        case example: ListResult[_] =>
          val listResult = example.asInstanceOf[ListResult[List[_]]]
          Some(ResponseObjectSchemaJson(listResult))
          //TODO if value is List, need to be modified to Array later.
        case s:scala.Product if(!value.isInstanceOf[List[scala.Product]])  => Some(ResponseObjectSchemaJson(s"#/definitions/${s.getClass.getSimpleName}"))
        case _ => Some(ResponseObjectSchemaJson(s"#/definitions/NotSupportedYet"))
      }
    }
  }

  //Response Object 
  // links -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#responsesObject
  abstract class ResponseBaseObjectJson(
    optionalFields: String*
  ) {
    def description: Option[String]
  }
  
  case class ResponseObjectJson(
    description: Option[String],
    schema: Option[ResponseObjectSchemaJson]
  ) extends  ResponseBaseObjectJson
  
  case class ResponseNoContentObjectJson(
    description: Option[String]
  ) extends  ResponseBaseObjectJson
  
  // Operation Object 
  // links -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#operation-object
  case class OperationObjectJson(
    tags: List[String],
    summary: String,
    security: List[SecurityJson] = SecurityJson()::Nil,
    description: String,
    operationId: String,
    parameters: List[OperationParameter],
    responses: Map[String, ResponseBaseObjectJson]
  )
  //Parameter Object
  //link -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#parameterObject
  
  trait OperationParameter {
    def in: String
    def name: String
    def description: String
    def required: Boolean
  }
  case class OperationParameterPathJson (
    in: String = "path",
    name: String = "BANK_ID",
    description: String = "BANK_ID",
    required: Boolean = true,
    `type`: String ="string"
  )extends OperationParameter
  
  case class OperationParameterBodyJson (
    in: String = "body",
    name: String = "body",
    description: String = "BANK_BODY",
    required: Boolean = true,
    schema: ResponseObjectSchemaJson = ResponseObjectSchemaJson("#/definitions/BasicViewJSON")
  )extends OperationParameter
  
  case class SwaggerResourceDoc(
    swagger: String,
    info: InfoJson,
    host: String,
    basePath: String,
    schemes: List[String],
    securityDefinitions: SecurityDefinitionsJson,
    security: List[SecurityJson],
    paths: Map[String, Map[String, OperationObjectJson]]
  )
  
  /**
    *Package the SwaggerResourceDoc with the ResourceDoc.
    * Note: the definitions of SwaggerResourceDoc only contains Error part,
    *       other specific OBP JSON part is filled by def "loadDefinitions(resourceDocList: List[ResourceDoc])"
    * case class ResourceDoc(
    *   partialFunction : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    *   apiVersion: String, 
    *   apiFunction: String, 
    *   requestVerb: String, 
    *   requestUrl: String, 
    *   summary: String, 
    *   description: String, 
    *   exampleRequestBody: JValue, 
    *   successResponseBody: JValue, 
    *   errorResponseBodies: List[JValue], 
    *   tags: List[ResourceDocTag]
    * )
    * 
    * -->
    * case class SwaggerResourceDoc(
    *   swagger: String,
    *   info: InfoJson,
    *   host: String,
    *   basePath: String,
    *   schemes: List[String],
    *   securityDefinitions: SecurityDefinitionsJson,
    *   security: List[SecurityJson],
    *   paths: Map[String, Map[String, OperationObjectJson]],
    * )
    *
    * @param resourceDocList     list of ResourceDoc
    * @param requestedApiVersion eg: 2_2_0
    * @return
    */
  def createSwaggerResourceDoc(resourceDocList: List[JSONFactory1_4_0.ResourceDocJson], requestedApiVersion: ApiVersion): SwaggerResourceDoc = {
    
    //reference to referenceObject: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#referenceObject  
    //according to the apiFunction name, prepare the reference 
    // eg: set the following "$ref" field: 
    //    "path": "/banks/BANK_ID": {
    //      "get": {
    //      "responses": {
    //      "200": {
    //      "schema": {
    //         "$ref": "#/definitions/BankJSON"

    implicit val formats = CustomJsonFormats.formats

    val (infoTitle, infoDescription) = 
      requestedApiVersion match {
          case obpStandardVersion if(ApiVersion.standardVersions.contains(obpStandardVersion)) =>("Open Bank Project API", s"An Open Source API for Banks. (c) TESOBE GmbH. 2011 - ${APIUtil.currentYear}. Licensed under the AGPL and commercial licences.")
          case otherStandardVersion  =>
            val apiVersion = otherStandardVersion.asInstanceOf[ScannedApiVersion]
            val standard= apiVersion.apiStandard
            val urlPrefix= apiVersion.urlPrefix
            (
              s"${standard} ${urlPrefix.split("-").map(_.capitalize).mkString(" ")}",
              // Commented out: STET / Polish / AUOpenBanking Lift endpoints removed
              if (apiVersion == OBP_UKOpenBanking_200.apiVersion
                || OBP_UKOpenBanking_310.apiVersion == OBP_UKOpenBanking_200.apiVersion
                || apiVersion == OBP_UKOpenBanking_401.apiVersion
              )  s"custom, proprietary license: personal use is allowed and free, modifications or re-publishing is not allowed"
              else if (apiVersion == OBP_BERLIN_GROUP_1_3.apiVersion
                || apiVersion == OBP_BERLIN_GROUP_1_3_Alias.apiVersion
              ) "Creative Commons Attribution-NoDerivatives 4.0 International (CC BY-ND)"
              else 
                s"License: Unknown"
            )
      }
    
    val infoContact = InfoContactJson("TESOBE GmbH. / Open Bank Project", "https://openbankproject.com" ,"contact@tesobe.com")
    val infoApiVersion = requestedApiVersion
    val info = InfoJson(infoTitle, infoDescription, infoContact, infoApiVersion.toString)
    val host = Constant.HostName.replaceFirst("http://", "").replaceFirst("https://", "")
    val basePath = "/"
    val schemas = List("http", "https")
    // Paths Object
    // link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#paths-object
    // setting up the following fields of swagger json,eg apiFunction = bankById
    //  "paths": {
    //    "/banks/BANK_ID": --> mrd._1
    //      "get": {        --> all following from mrd._2
    //      "tags": [ "1_2_1"],
    //      "summary": "Get Bank",
    //      "description": "<p>Get the bank specified by BANK_ID....
    //      "operationId": "1_2_1-bankById",
    //      "responses": {
    //        "200": {
    //          "description": "Success",
    //          "schema": { "$ref": "#/definitions/BankJSON" }
    //        },
    //        "400": {
    //          "description": "Error",
    //          "schema": {"$ref": "#/definitions/Error"
    val pathPairs = resourceDocList.groupBy(x => x.specified_url).toSeq.sortBy(x => x._1).map { mrd =>
      
      //`/banks/BANK_ID` --> `/obp/v3.0.0/banks/BANK_ID` 
      val pathAddedObpandVersion = mrd._1
      //`/obp/v3.0.0/banks/BANK_ID` --> `/obp/v3.0.0/banks/{BANK_ID}`
      val path =
        pathAddedObpandVersion
        .replaceAll("/BANK_ID", "/{BANK_ID}")
        .replaceAll("/ACCOUNT_ID", "/{ACCOUNT_ID}")
        .replaceAll("/VIEW_ID", "/{VIEW_ID}")
        .replaceAll("/USER_ID", "/{USER_ID}")
        .replaceAll("/TRANSACTION_ID", "/{TRANSACTION_ID}")
        .replaceAll("/TRANSACTION_REQUEST_TYPE", "/{TRANSACTION_REQUEST_TYPE}")
        .replaceAll("/TRANSACTION_REQUEST_ID", "/{TRANSACTION_REQUEST_ID}")
        .replaceAll("/PROVIDER_ID", "/{PROVIDER_ID}")
        .replaceAll("/OTHER_ACCOUNT_ID", "/{OTHER_ACCOUNT_ID}")
        .replaceAll("/FROM_CURRENCY_CODE", "/{FROM_CURRENCY_CODE}")
        .replaceAll("/TO_CURRENCY_CODE", "/{TO_CURRENCY_CODE}")
        .replaceAll("/COMMENT_ID", "/{COMMENT_ID}")
        .replaceAll("/TAG_ID", "/{TAG_ID}")
        .replaceAll("/IMAGE_ID", "/{IMAGE_ID}")
        .replaceAll("/CUSTOMER_ID", "/{CUSTOMER_ID}")
        .replaceAll("/BRANCH_ID", "/{BRANCH_ID}")
        .replaceAll("/NEW_ACCOUNT_ID", "/{NEW_ACCOUNT_ID}")
        .replaceAll("/CONSUMER_ID", "/{CONSUMER_ID}")
        .replaceAll("/USER_EMAIL", "/{USER_EMAIL}")
        .replaceAll("/ENTITLEMENT_ID", "/{ENTITLEMENT_ID}")
        .replaceAll("/KYC_CHECK_ID", "/{KYC_CHECK_ID}")
        .replaceAll("/KYC_DOCUMENT_ID", "/{KYC_DOCUMENT_ID}")
        .replaceAll("/KYC_MEDIA_ID", "/{KYC_MEDIA_ID}")
        .replaceAll("/AMT_ID", "/{AMT_ID}")
        .replaceAll("/API_VERSION", "/{API_VERSION}")
        .replaceAll("/CUSTOMER_ADDRESS_ID", "/{CUSTOMER_ADDRESS_ID}")
        .replaceAll("/TAX_RESIDENCE_ID", "/{TAX_RESIDENCE_ID}")
        .replaceAll("/CARD_ID", "/{CARD_ID}")
        .replaceAll("/CARD_ATTRIBUTE_ID", "/{CARD_ATTRIBUTE_ID}")
        .replaceAll("/PRODUCT_CODE", "/{PRODUCT_CODE}")
        .replaceAll("/ACCOUNT_ATTRIBUTE_ID", "/{ACCOUNT_ATTRIBUTE_ID}")
        .replaceAll("/ACCOUNT_APPLICATION_ID", "/{ACCOUNT_APPLICATION_ID}")
        .replaceAll("/DYNAMIC_ENTITY_ID", "/{DYNAMIC_ENTITY_ID}")
        .replaceAll("/METHOD_ROUTING_ID", "/{METHOD_ROUTING_ID}")
        .replaceAll("/WEB_UI_PROPS_ID", "/{WEB_UI_PROPS_ID}")
        .replaceAll("/ATM_ID", "/{ATM_ID}")
        .replaceAll("/ATM_ATTRIBUTE_ID", "/{ATM_ATTRIBUTE_ID}")
        .replaceAll("/CONSENT_ID", "/{CONSENT_ID}")
        .replaceAll("/PRODUCT_ATTRIBUTE_ID", "/{PRODUCT_ATTRIBUTE_ID}")
        .replaceAll("/SCA_METHOD", "/{SCA_METHOD}")
        .replaceAll("/SCOPE_ID", "/{SCOPE_ID}")
        .replaceAll("/ENTITLEMENT_REQUEST_ID", "/{ENTITLEMENT_REQUEST_ID}")
        .replaceAll("/INDEX", "/{INDEX}")
        .replaceAll("/FIELD", "/{FIELD}")
        .replaceAll("/USER_AUTH_CONTEXT_ID", "/{USER_AUTH_CONTEXT_ID}")
        .replaceAll("/AUTH_CONTEXT_UPDATE_ID", "/{AUTH_CONTEXT_UPDATE_ID}")
        .replaceAll("/Email", "/{Email}")
        .replaceAll("/USERNAME", "/{USERNAME}")
        .replaceAll("/PROVIDER", "/{PROVIDER}")
        .replaceAll("/REQUEST_ID", "/{REQUEST_ID}")
        .replaceAll("/MEETING_ID", "/{MEETING_ID}")
        .replaceAll("/COLLECTION_CODE", "/{COLLECTION_CODE}")
        .replaceAll("/COUNTERPARTY_ID", "/{COUNTERPARTY_ID}")
        .replaceAll("/COUNTERPARTY_NAME", "/{COUNTERPARTY_NAME}")
      
      var pathParameters = List.empty[OperationParameter]
      if(path.contains("/{BANK_ID}"))
        pathParameters = OperationParameterPathJson(name="BANK_ID", description="The bank id") :: pathParameters
      if(path.contains("/{ACCOUNT_ID}"))
        pathParameters = OperationParameterPathJson(name="ACCOUNT_ID", description="The account id") :: pathParameters
      if(path.contains("/{VIEW_ID}"))
        pathParameters = OperationParameterPathJson(name="VIEW_ID", description="The view id") :: pathParameters
      if(path.contains("/{USER_ID}"))
        pathParameters = OperationParameterPathJson(name="USER_ID", description="The user id") :: pathParameters
      if(path.contains("/{TRANSACTION_ID}"))
        pathParameters = OperationParameterPathJson(name="TRANSACTION_ID", description="The transaction id") :: pathParameters
      if(path.contains("/{TRANSACTION_REQUEST_TYPE}"))
        pathParameters = OperationParameterPathJson(name="TRANSACTION_REQUEST_TYPE", description="The transaction request type") :: pathParameters
      if(path.contains("/{TRANSACTION_REQUEST_ID}"))
        pathParameters = OperationParameterPathJson(name="TRANSACTION_REQUEST_ID", description="The transaction request id") :: pathParameters
      if(path.contains("/{PROVIDER_ID}"))
        pathParameters = OperationParameterPathJson(name="PROVIDER_ID", description="The provider id") :: pathParameters
      if(path.contains("/{OTHER_ACCOUNT_ID}"))
        pathParameters = OperationParameterPathJson(name="OTHER_ACCOUNT_ID", description="The other account id") :: pathParameters
      if(path.contains("/{FROM_CURRENCY_CODE}"))
        pathParameters = OperationParameterPathJson(name="FROM_CURRENCY_CODE", description="The from currency code") :: pathParameters
      if(path.contains("/{TO_CURRENCY_CODE}"))
        pathParameters = OperationParameterPathJson(name="TO_CURRENCY_CODE", description="The to currency code") :: pathParameters
      if(path.contains("/{COMMENT_ID}"))
        pathParameters = OperationParameterPathJson(name="COMMENT_ID", description="The comment id") :: pathParameters
      if(path.contains("/{TAG_ID}"))
        pathParameters = OperationParameterPathJson(name="TAG_ID", description="The tag id") :: pathParameters
      if(path.contains("/{IMAGE_ID}"))
        pathParameters = OperationParameterPathJson(name="IMAGE_ID", description="The image id") :: pathParameters
      if(path.contains("/{CUSTOMER_ID}"))
        pathParameters = OperationParameterPathJson(name="CUSTOMER_ID", description="The customer id") :: pathParameters
      if(path.contains("/{BRANCH_ID}"))
        pathParameters = OperationParameterPathJson(name="BRANCH_ID", description="The branch id") :: pathParameters
      if(path.contains("/{NEW_ACCOUNT_ID}"))
        pathParameters = OperationParameterPathJson(name="NEW_ACCOUNT_ID", description="new account id") :: pathParameters
      if(path.contains("/{CONSUMER_ID}"))
        pathParameters = OperationParameterPathJson(name="CONSUMER_ID", description="new consumer id") :: pathParameters
      if(path.contains("/{USER_EMAIL}"))
        pathParameters = OperationParameterPathJson(name="USER_EMAIL", description="The user email id") :: pathParameters
      if(path.contains("/{ENTITLEMENT_ID}"))
        pathParameters = OperationParameterPathJson(name="ENTITLEMENT_ID", description="The entitblement id") :: pathParameters
      if(path.contains("/{KYC_CHECK_ID}"))
        pathParameters = OperationParameterPathJson(name="KYC_CHECK_ID", description="The kyc check id") :: pathParameters
      if(path.contains("/{KYC_DOCUMENT_ID}"))
        pathParameters = OperationParameterPathJson(name="KYC_DOCUMENT_ID", description="The kyc document id") :: pathParameters
      if(path.contains("/{KYC_MEDIA_ID}"))
        pathParameters = OperationParameterPathJson(name="KYC_MEDIA_ID", description="The kyc media id") :: pathParameters
      if(path.contains("/{AMT_ID}"))
        pathParameters = OperationParameterPathJson(name="AMT_ID", description="The kyc media id") :: pathParameters
      if(path.contains("/{CUSTOMER_ADDRESS_ID}"))
        pathParameters = OperationParameterPathJson(name="CUSTOMER_ADDRESS_ID", description= "the customer address id") :: pathParameters
      if(path.contains("/{TAX_RESIDENCE_ID}"))
        pathParameters = OperationParameterPathJson(name="TAX_RESIDENCE_ID", description= "the tax residence id") :: pathParameters
      if(path.contains("/{CARD_ID}"))
        pathParameters = OperationParameterPathJson(name="CARD_ID", description= "the card id") :: pathParameters
      if(path.contains("/{CARD_ATTRIBUTE_ID}"))
        pathParameters = OperationParameterPathJson(name="CARD_ATTRIBUTE_ID", description= "the card attribute id") :: pathParameters
      if(path.contains("/{PRODUCT_CODE}"))
        pathParameters = OperationParameterPathJson(name="PRODUCT_CODE", description= "the product code") :: pathParameters
      if(path.contains("/{ACCOUNT_ATTRIBUTE_ID}"))
        pathParameters = OperationParameterPathJson(name="ACCOUNT_ATTRIBUTE_ID", description= "the account attribute id ") :: pathParameters
      if(path.contains("/{ACCOUNT_APPLICATION_ID}"))
        pathParameters = OperationParameterPathJson(name="ACCOUNT_APPLICATION_ID", description= "the account application id ") :: pathParameters
      if(path.contains("/{DYNAMIC_ENTITY_ID}"))
        pathParameters = OperationParameterPathJson(name="DYNAMIC_ENTITY_ID", description= "the dynamic entity id ") :: pathParameters
      if(path.contains("/{METHOD_ROUTING_ID}"))
        pathParameters = OperationParameterPathJson(name="METHOD_ROUTING_ID", description= "the method routing id ") :: pathParameters
      if(path.contains("/{WEB_UI_PROPS_ID}"))
        pathParameters = OperationParameterPathJson(name="WEB_UI_PROPS_ID", description= "the web ui props id") :: pathParameters
      if(path.contains("/{ATM_ID}"))
        pathParameters = OperationParameterPathJson(name="ATM_ID", description= "the atm id") :: pathParameters
      if(path.contains("/{ATM_ATTRIBUTE_ID}"))
        pathParameters = OperationParameterPathJson(name="ATM_ATTRIBUTE_ID", description= "the atm attribute id") :: pathParameters
      if(path.contains("/{CONSENT_ID}"))
        pathParameters = OperationParameterPathJson(name="CONSENT_ID", description= "the consent id") :: pathParameters
      if(path.contains("/{PRODUCT_ATTRIBUTE_ID}"))
        pathParameters = OperationParameterPathJson(name="PRODUCT_ATTRIBUTE_ID", description= "the product attribute id") :: pathParameters
      if(path.contains("/{SCA_METHOD}"))
        pathParameters = OperationParameterPathJson(name="SCA_METHOD", description= "the sca method") :: pathParameters
      if(path.contains("/{SCOPE_ID}"))
        pathParameters = OperationParameterPathJson(name="SCOPE_ID", description= "the scope id") :: pathParameters
      if(path.contains("/{ENTITLEMENT_REQUEST_ID}"))
        pathParameters = OperationParameterPathJson(name="ENTITLEMENT_REQUEST_ID", description= "the entitlement request id") :: pathParameters
      if(path.contains("/{INDEX}"))
        pathParameters = OperationParameterPathJson(name="INDEX", description= "the elastic search index") :: pathParameters
      if(path.contains("/{FIELD}"))
        pathParameters = OperationParameterPathJson(name="FIELD", description= "the elastic search field") :: pathParameters
      if(path.contains("/{USER_AUTH_CONTEXT_ID}"))
        pathParameters = OperationParameterPathJson(name="USER_AUTH_CONTEXT_ID", description= "the user auth context id") :: pathParameters
      if(path.contains("/{AUTH_CONTEXT_UPDATE_ID}"))
        pathParameters = OperationParameterPathJson(name="AUTH_CONTEXT_UPDATE_ID", description= "the auth context update id") :: pathParameters
      if(path.contains("/{Email}"))
        pathParameters = OperationParameterPathJson(name="Email", description= "the user email address") :: pathParameters
      if(path.contains("/{USERNAME}"))
        pathParameters = OperationParameterPathJson(name="USERNAME", description= "the user name") :: pathParameters
      if(path.contains("/{PROVIDER}"))
        pathParameters = OperationParameterPathJson(name="PROVIDER", description= "the user PROVIDER") :: pathParameters
      if(path.contains("/{REQUEST_ID}"))
        pathParameters = OperationParameterPathJson(name="REQUEST_ID", description= "the request id") :: pathParameters
      if(path.contains("/{MEETING_ID}"))
        pathParameters = OperationParameterPathJson(name="MEETING_ID", description= "the meeting id") :: pathParameters
      if(path.contains("/{COLLECTION_CODE}"))
        pathParameters = OperationParameterPathJson(name="COLLECTION_CODE", description= "the collection code") :: pathParameters
      if(path.contains("/{COUNTERPARTY_ID}"))
        pathParameters = OperationParameterPathJson(name="COUNTERPARTY_ID", description= "the counterparty id") :: pathParameters
      if(path.contains("/{COUNTERPARTY_NAME}"))
        pathParameters = OperationParameterPathJson(name="COUNTERPARTY_NAME", description= "the counterparty name") :: pathParameters
      if(path.contains("/{API_VERSION}"))
        pathParameters = OperationParameterPathJson(name="API_VERSION", description="eg:v2.2.0, v3.0.0") :: pathParameters
  
      val operationObjects: Map[String, OperationObjectJson] = mrd._2.map(rd =>
        (rd.request_verb.toLowerCase,
          OperationObjectJson(
            tags = rd.tags,
            summary = rd.summary,
            description = PegdownOptions.convertPegdownToHtmlTweaked(rd.description.stripMargin).replaceAll("\n", ""),
            operationId = s"${rd.operation_id}",
            parameters ={
              val description = rd.example_request_body match {
                case JNothing => ""
                case EmptyBody => ""
                case example: PrimaryDataBody[_] => s"${example.swaggerDataTypeName} type value."
                case s:scala.Product => s"${s.getClass.getSimpleName} object that needs to be added."
                case _ => "NotSupportedYet type that needs to be added."
              }
              ResponseObjectSchemaJson.getRequestBodySchema(rd.example_request_body) match {
                case Some(schema) =>
                  OperationParameterBodyJson(
                    description = description,
                    schema = schema) :: pathParameters
                case None => pathParameters
              }
            },
            responses = {
              val successKey = rd.request_verb.toLowerCase match {
                case "post" => "201"
                case "delete" => "204"
                case _ => "200"
              }

              Map(
                successKey -> ResponseObjectJson(Some("Success"), ResponseObjectSchemaJson.getResponseBodySchema(rd.success_response_body)),
                "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFieldNameByValue(rd.error_response_bodies.head)}")))
              )
            }

          )
        )
      ).toMap
      (path, operationObjects.toSeq.sortBy(m => m._1).toMap)
    // breakOut is removed in 2.13. Collecting the pairs and handing them to ListMap builds the
    // same value on both versions, at the cost of one intermediate sequence that breakOut avoided.
    // Order is unaffected: the sortBy above fixes it and ListMap preserves insertion order.
    }
    val paths: ListMap[String, Map[String, OperationObjectJson]] = ListMap(pathPairs: _*)

    SwaggerResourceDoc(
      swagger = "2.0",
      info = info,
      host = host,
      basePath = basePath,
      schemes = schemas,
      securityDefinitions = SecurityDefinitionsJson(DirectLoginJson(),GatewayLoginJson()), //default value
      security = SecurityJson()::Nil, //default value
      paths = paths
    )
  }
  
  /**
    * @param entity - Any, maybe a case class, maybe a list ,maybe a string
    *               ExampleJSON (
    *               id = 5,
    *               name = "Tesobe",
    *               bank = Bank("gh.29.uk")
    *               banks = List(Bank("gh.29.uk"))
    *               )
    * @return - String, with Swagger format  
    *         "ExampleJSON":
    *         { 
    *           "required": ["id","name","bank","banks"],    
    *           "properties":
    *           { 
    *             "id": {"type":"integer", "format":"int32"}, 
    *             "Tesobe": {"type":"string"},
    *             "bank": {"$ref": "#/definitions/BankJSON"},
    *             "banks": {"type": "array", "items":{"$ref": "#/definitions/BanksJSON"}}
    *         }
    */
  def translateEntity(entity: Any): String = {

    val entityType = ReflectUtils.getType(entity)

    val nameToValue: Map[String, Any] = entity match {
      case  ListResult(name, results) => Map((name, results))
      case _ => ReflectUtils.getConstructorArgs(entity)
    }


    val nameToType: Map[String, Type] = entity match {
      case listResult: ListResult[_] => Map((listResult.name, listResult.itemType))
      case _ => ReflectUtils.getConstructorArgTypes(entity)
    }


    val convertParamName = (name: String) =>  entity match {
      case _ : JsonFieldReName => StringHelpers.snakify(name)
      case _ => name
    }

    //Collect all mandatory fields and make an appropriate string
    // eg return :  "required": ["id","name","bank","banks"],
    val required = nameToType
      .filterNot(_._2 <:< SwaggerTypes.tOptionWildcard)
      .map(_._1)
      .map(convertParamName)
      .map(it => s""" "$it" """)

    //Make part of mandatory fields
    val requiredFieldsPart = if (required.isEmpty) "" else  required.mkString(""" "required": [""", ",", """], """)



    val paramNameToType: Iterable[String] = nameToValue.map(it => {
      //TODO, what does this `invokeMethod` return?
      val paramName = convertParamName(it._1)

      val paramType = nameToType(it._1)
      val paramValue = it._2

      val exampleValue = paramValue match {
        case Some(v) => v // Here it will get the value from Option/Box,
        case _ => paramValue
      }

      val definition = buildSwaggerSchema(paramType, exampleValue)

      s""" "$paramName": $definition """
    })

    //Exclude all unrecognised fields and make part of fields definition
    // add comment
    // fields --> "id" : {"type":"integer", "format":"int32"} ,"name" : {"type":"string"} ,"bank": {"$ref":"#/definitions/Bank"} ,"banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val fields: String = paramNameToType mkString (",")
    val definition = s""""${entityType.typeSymbol.name}":{$requiredFieldsPart "properties": {$fields}}"""
    definition
  }

  private def buildSwaggerSchema(declaredType: Type, exampleValue: Any): String = {
    // A type argument that erased to java.lang.Object carries no information, so recover it from
    // the example value before dispatching. See refineErasedTypeArgument.
    val paramType: Type = refineErasedTypeArgument(declaredType, exampleValue)

    // Scala 3 cannot synthesise a TypeTag for a generic type parameter (see SwaggerTypes'
    // docstring), so these take the runtime Type as an ordinary value instead of as a
    // TypeTag-context-bound type parameter. Call sites pass a SwaggerTypes.tXxx constant.
    def isTypeOf(t: Type): Boolean = paramType <:< t

    def isOneOfType(t: Type, d: Type): Boolean = isTypeOf(t) || isTypeOf(d)

    def isAnyOfType(t: Type, d: Type, e: Type): Boolean = isTypeOf(t) || isTypeOf(d) || isTypeOf(e)

    // enum all values to Array structure string: ["red", "green", "other"]
    def enumsToString(enumTp: Type) = {
      val enumType: Type = ReflectUtils.getDeepGenericType(enumTp).head
      OBPEnumeration.getValuesByType(enumType).map(it => s""""$it"""").mkString(",")
    }
    def example = exampleValue match {
        case null | None => ""
        case v => s""", "example": "${safeExampleValue(v)}" """
      }

    paramType match {
      case _ if isTypeOf(SwaggerTypes.tEnumValue)                    => s""" {"type":"string","enum": [${enumsToString(paramType)}]}"""
      case _ if isTypeOf(SwaggerTypes.tOptionEnumValue)            => s""" {"type":"string","enum": [${enumsToString(paramType)}]}"""
      case _ if isTypeOf(SwaggerTypes.tCollEnumValue)             => s""" {"type":"array", "items":{"type":"string","enum": [${enumsToString(paramType)}]}}"""
      case _ if isTypeOf(SwaggerTypes.tOptionCollEnumValue)     => s""" {"type":"array", "items":{"type":"string","enum": [${enumsToString(paramType)}]}}"""

      //Boolean - 4 kinds
      case _ if isAnyOfType(SwaggerTypes.tBoolean, SwaggerTypes.tJBool, SwaggerTypes.tXBoolean)                                         => s""" {"type":"boolean" $example}"""
      case _ if exampleValue.isInstanceOf[Boolean]                                            => s""" {"type":"boolean" $example}""" //TODO. Here need to be enhanced.
      case _ if isAnyOfType(SwaggerTypes.tOptionBoolean, SwaggerTypes.tOptionJBool, SwaggerTypes.tOptionXBoolean)                 => s""" {"type":"boolean" $example}"""
      case _ if isAnyOfType(SwaggerTypes.tCollBoolean, SwaggerTypes.tCollJBool, SwaggerTypes.tCollXBoolean)                       => s""" {"type":"array", "items":{"type": "boolean"}}"""
      case _ if isAnyOfType(SwaggerTypes.tOptionCollBoolean, SwaggerTypes.tOptionCollJBool, SwaggerTypes.tOptionCollXBoolean) => s""" {"type":"array", "items":{"type": "boolean"}}"""

      //String
      case t if isAnyOfType(SwaggerTypes.tString, SwaggerTypes.tJString, SwaggerTypes.tXString) || isEnumeration(t)                                                  => s""" {"type":"string" $example}"""
      // Option before Coll, as every other scalar block here already has it. Coll is IterableOnce,
      // which 2.13's Option implements and 2.12's did not, so Coll[String] answers true for
      // Option[String] and this was the one block whose order let that through - publishing every
      // optional string as an array of strings.
      //
      // Only the type test moves. These cases each carry a second, independent clause testing for
      // an enumeration, and those are ordered among themselves: isNestEnumeration digs to the
      // innermost type argument, so Option[List[Colour]] satisfies isNestEnumeration for Option[_]
      // exactly as well as for Option[List[_]], and only the latter is right for it.
      // Carrying the Option[_] enumeration clause up here with the type test made every optional
      // list of enumerations a string. It stays below, after the list forms have had their turn.
      case t if isAnyOfType(SwaggerTypes.tOptionString, SwaggerTypes.tOptionJString, SwaggerTypes.tOptionXString)                                            => s""" {"type":"string" $example}"""
      case t if isAnyOfType(SwaggerTypes.tCollString, SwaggerTypes.tCollJString, SwaggerTypes.tCollXString) || isNestEnumeration(SwaggerTypes.tListWildcard, t)                         => s""" {"type":"array", "items":{"type": "string"}}"""
      case t if isAnyOfType(SwaggerTypes.tOptionCollString, SwaggerTypes.tOptionCollJString, SwaggerTypes.tOptionCollXString) || isNestEnumeration(SwaggerTypes.tOptionListWildcard, t) => s""" {"type":"array", "items":{"type": "string"}}"""
      case t if isNestEnumeration(SwaggerTypes.tOptionWildcard, t)                                                                          => s""" {"type":"string" $example}"""

      //Int
      case _ if isAnyOfType(SwaggerTypes.tInt, SwaggerTypes.tJInt, SwaggerTypes.tXInt)                                           => s""" {"type":"integer", "format":"int32" $example}"""
      case _ if isAnyOfType(SwaggerTypes.tOptionInt, SwaggerTypes.tOptionJInt, SwaggerTypes.tOptionXInt)                   => s""" {"type":"integer", "format":"int32" $example}"""
      case _ if isAnyOfType(SwaggerTypes.tCollInt, SwaggerTypes.tCollJInt, SwaggerTypes.tCollXInt)                         => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      case _ if isAnyOfType(SwaggerTypes.tOptionCollInt, SwaggerTypes.tOptionCollJInt, SwaggerTypes.tOptionCollXInt) => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      //Long
      case _ if isOneOfType(SwaggerTypes.tLong, SwaggerTypes.tXLong)                             => s""" {"type":"integer", "format":"int64" $example}"""
      case _ if isOneOfType(SwaggerTypes.tOptionLong, SwaggerTypes.tOptionXLong)             => s""" {"type":"integer", "format":"int64" $example}"""
      case _ if isOneOfType(SwaggerTypes.tCollLong, SwaggerTypes.tCollXLong)                 => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      case _ if isOneOfType(SwaggerTypes.tOptionCollLong, SwaggerTypes.tOptionCollXLong) => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      //Float
      case _ if isOneOfType(SwaggerTypes.tFloat, SwaggerTypes.tXFloat)                             => s""" {"type":"number", "format":"float" $example}"""
      case _ if isOneOfType(SwaggerTypes.tOptionFloat, SwaggerTypes.tOptionXFloat)             => s""" {"type":"number", "format":"float" $example}"""
      case _ if isOneOfType(SwaggerTypes.tCollFloat, SwaggerTypes.tCollXFloat)                 => s""" {"type":"array", "items":{"type": "float"}}"""
      case _ if isOneOfType(SwaggerTypes.tOptionCollFloat, SwaggerTypes.tOptionCollXFloat) => s""" {"type":"array", "items":{"type": "float"}}"""
      //Double
      case _ if isAnyOfType(SwaggerTypes.tDouble, SwaggerTypes.tJDouble, SwaggerTypes.tXDouble)                                           => s""" {"type":"number", "format":"double" $example}"""
      case _ if isAnyOfType(SwaggerTypes.tOptionDouble, SwaggerTypes.tOptionJDouble, SwaggerTypes.tOptionXDouble)                   => s""" {"type":"number", "format":"double" $example}"""
      case _ if isAnyOfType(SwaggerTypes.tCollDouble, SwaggerTypes.tCollJDouble, SwaggerTypes.tCollXDouble)                         => s""" {"type":"array", "items":{"type": "double"}}"""
      case _ if isAnyOfType(SwaggerTypes.tOptionCollDouble, SwaggerTypes.tOptionCollJDouble, SwaggerTypes.tOptionCollXDouble) => s""" {"type":"array", "items":{"type": "double"}}"""
      //BigDecimal
      case _ if isOneOfType(SwaggerTypes.tBigDecimal, SwaggerTypes.tJBigDecimal)                             => s""" {"type":"string", "format":"double" $example}"""
      case _ if isOneOfType(SwaggerTypes.tOptionBigDecimal, SwaggerTypes.tOptionJBigDecimal)             => s""" {"type":"string", "format":"double" $example}"""
      case _ if isOneOfType(SwaggerTypes.tCollBigDecimal, SwaggerTypes.tCollJBigDecimal)                 => s""" {"type":"array", "items":{"type": "string", "format":"double","example":"123.321"}}"""
      case _ if isOneOfType(SwaggerTypes.tOptionCollBigDecimal, SwaggerTypes.tOptionCollJBigDecimal) => s""" {"type":"array", "items":{"type": "string", "format":"double","example":"123.321"}}"""
      //Date
      case _ if isOneOfType(SwaggerTypes.tDate, SwaggerTypes.tOptionDate)                   => {
        val valueBox = tryo {s"""${APIUtil.DateWithSecondsFormat.format(exampleValue)}"""}
        if(valueBox.isEmpty) logger.debug(s"Date/Option[Date] field - current example value is: $paramType - $exampleValue")
        val value = valueBox.getOrElse(APIUtil.DateWithSecondsExampleString)
        s""" {"type":"string", "format":"date","example":"$value"}"""
      }
      case _ if isOneOfType(SwaggerTypes.tCollDate, SwaggerTypes.tOptionCollDate)       => s""" {"type":"array", "items":{"type":"string", "format":"date"}}"""

      //List or Array Option data.
      case t if isOneOfType(SwaggerTypes.tCollOptionWildcard, SwaggerTypes.tArrayOptionWildcard)  =>
        val tp = ReflectUtils.getNestTypeArg(t, 0, 0)
        val value = exampleValue match {
          case v: Array[_] => v.headOption.flatMap(_.asInstanceOf[Option[_]]).orNull
          case coll: Iterable[_]  => coll.headOption.flatMap(_.asInstanceOf[Option[_]]).orNull
          case _ => null
        }
        s""" {"type": "array", "items":${buildSwaggerSchema(tp, value)}}"""

      // Option List or Array data
      case t if isOneOfType(SwaggerTypes.tOptionCollWildcard, SwaggerTypes.tOptionArrayWildcard) =>
        val tp = ReflectUtils.getNestTypeArg(t, 0, 0)
        val value = exampleValue match {
          case Some(v: Array[_]) if v.nonEmpty => v.head
          case Some(coll: Iterable[_]) if coll.nonEmpty  => coll.head
          case (v: Array[_]) if v.nonEmpty => v.head
          case (coll: Iterable[_]) if coll.nonEmpty => coll.head
          case _ => null
        }
        s""" {"type": "array", "items":${buildSwaggerSchema(tp, value)}}"""

      // List or Array data. Not an Option: Coll is IterableOnce, which 2.13's Option implements, so
      // without this guard every Option the cases above did not name by type - an Option of a case
      // class, of a JValue - is published as an array of it. Option[Coll[_]] is already handled
      // above, so what this excludes falls to the Option case below, which unwraps and recurses.
      case t if isOneOfType(SwaggerTypes.tCollWildcard, SwaggerTypes.tArrayWildcard) && !isTypeOf(SwaggerTypes.tOptionWildcard)  =>
        val tp = ReflectUtils.getNestTypeArg(t, 0)
        val value = exampleValue match {
          case v: Array[_] => v.head
          case coll: Iterable[_] if coll.nonEmpty => coll.head
          case _ => null
        }
        s""" {"type": "array", "items":${buildSwaggerSchema(tp, value)}}"""

      //Option data
      case t if isTypeOf(SwaggerTypes.tOptionWildcard)               =>
        val tp = ReflectUtils.getNestTypeArg(t, 0)
        val value = exampleValue match {
          case Some(v) => v
          case None => null
          case v =>  v
        }
        buildSwaggerSchema(tp, value)

      //JValue type
      case _ if exampleValue == JNull || exampleValue == JNothing => throw new RuntimeException("Example should neither be JNothing nor JNull")

      case _ if isTypeOf(SwaggerTypes.tJArray)                   =>
        exampleValue match {
          case JArray(v ::_) => s""" {"type": "array", "items":${buildSwaggerSchema(JsonUtils.getType(v), v)} }"""
          case _ => s""" {"type": "array","items": {}}""" //if array is empty, we can not know the type here.
//          case _ =>
//            logger.error(s"Empty JArray is not allowed in request body and response body example.")
//            throw new RuntimeException("JArray type should not be empty.")
        }

      case _ if isTypeOf(SwaggerTypes.tJObject)         =>
        val JObject(jFields) = exampleValue
        val allFields = for {
          JField(name, v) <- jFields
        } yield s""" "$name": ${buildSwaggerSchema(JsonUtils.getType(v), v)} """

        val requiredFields = if (jFields.isEmpty) "[]" else  jFields.map(_.name).map(name => s""" "$name" """).mkString("[", ",", "]")

        if(requiredFields.equals("[]")) {
          s""" {"type":"object", "properties": { ${allFields.mkString(",")} } }"""
        } else{
          s""" {"type":"object", "properties": { ${allFields.mkString(",")} }, "required": $requiredFields }"""
        }

      case _ if isTypeOf(SwaggerTypes.tJValue) =>
        // The guard here used to be `Objects.nonNull(exampleValue)`, which returns a Boolean and
        // discards it - it never stopped anything, and a null example reached JsonUtils.getType,
        // whose own requireNonNull then threw. The collection branches above hand null down
        // whenever the example collection is empty, so this was always reachable; it surfaces now
        // because the array-shaped bodies reworked for 2.13 take that path more often. An unknown
        // example describes the field as a plain object rather than failing the whole document.
        if (exampleValue == null) """ {"type":"object"}"""
        else buildSwaggerSchema(JsonUtils.getType(exampleValue.asInstanceOf[JValue]), exampleValue)

      //Single object
      case t                                                    => s""" {"$$ref":"#/definitions/${getRefEntityName(t, exampleValue)}"}"""
    }
  }

  /**
   * The Scala type of a type argument that the class file erased to `java.lang.Object`, recovered
   * from the example value.
   *
   * `buildSwaggerSchema` decides a field's shape by comparing its runtime `Type` against constants
   * such as `SwaggerTypes.tLong`, and that runtime `Type` comes from `scala-reflect`, which reads
   * ScalaSig - an attribute only Scala 2 classes carry. On a Scala 3-compiled class it falls back
   * to the class file's Java generic signature, and there a *value type* cannot be a type argument:
   * `Option[Long]` is emitted as `scala.Option<java.lang.Object>` (`javap -v` on any of these
   * confirms it). Reference types are unaffected - `Option[String]` keeps `<java.lang.String>` -
   * which is why this is specifically about `Option[Boolean]`, `Option[Int]`, `Option[Long]`,
   * `Option[Float]` and `Option[Double]`, and about the same value types nested in a collection.
   *
   * Without this, the "Option data" case unwraps `Option[Object]` and recurses with the element
   * type `java.lang.Object`, which matches none of the scalar cases and falls all the way through
   * to the final `case t => {"$$ref": ...}` - publishing `{"$$ref":"#/definitions/Long"}` where the
   * contract says `{"type":"integer","format":"int64"}`, and a `$$ref` to a definition that does not
   * exist in the document at that. Measured on the whole published surface: 68 definitions across
   * eight API versions.
   *
   * The example value is the only runtime source of the erased type, and it is one this generator
   * already relies on everywhere else (`getRefEntityName` picks the entity type off the value by
   * the same reasoning, and a Boolean case just below already had an ad-hoc `isInstanceOf` rescue
   * for exactly this). It cannot help when the example is `None`/absent - there is no value to
   * inspect - so the example values themselves have to be present; `SwaggerNoDanglingRefTest`
   * fails on any field where they are not, rather than leaving it to be noticed downstream.
   *
   * Only the value types SwaggerTypes actually names are mapped. Anything else is left alone, so a
   * genuinely `Object`-typed field keeps behaving exactly as before.
   */
  private[this] def refineErasedTypeArgument(tp: Type, exampleValue: Any): Type =
    if (tp.typeSymbol.fullName != "java.lang.Object") tp
    else exampleValue match {
      case _: java.lang.Boolean => SwaggerTypes.tBoolean
      case _: java.lang.Integer => SwaggerTypes.tInt
      case _: java.lang.Long    => SwaggerTypes.tLong
      case _: java.lang.Float   => SwaggerTypes.tFloat
      case _: java.lang.Double  => SwaggerTypes.tDouble
      case _                    => tp
    }

  /**
    * all not swagger ref type
    */
  private[this] val noneRefTypes = List(
    SwaggerTypes.tJValue
    , SwaggerTypes.tOptionJValue
    , SwaggerTypes.tCollJValue
    , SwaggerTypes.tOptionCollJValue

    //Boolean - 4 kinds
    , SwaggerTypes.tBoolean, SwaggerTypes.tJBool, SwaggerTypes.tXBoolean
    , SwaggerTypes.tOptionBoolean, SwaggerTypes.tOptionJBool, SwaggerTypes.tOptionXBoolean
    , SwaggerTypes.tCollBoolean, SwaggerTypes.tCollJBool, SwaggerTypes.tCollXBoolean
    , SwaggerTypes.tOptionCollBoolean, SwaggerTypes.tOptionCollJBool, SwaggerTypes.tOptionCollXBoolean
    //String
    , SwaggerTypes.tString, SwaggerTypes.tJString, SwaggerTypes.tXString
    , SwaggerTypes.tOptionString, SwaggerTypes.tOptionJString, SwaggerTypes.tOptionXString
    , SwaggerTypes.tCollString, SwaggerTypes.tCollJString, SwaggerTypes.tCollXString
    , SwaggerTypes.tOptionCollString, SwaggerTypes.tOptionCollJString , SwaggerTypes.tOptionCollXString
    //Int
    , SwaggerTypes.tInt, SwaggerTypes.tJInt, SwaggerTypes.tXInt
    , SwaggerTypes.tOptionInt, SwaggerTypes.tOptionJInt, SwaggerTypes.tOptionXInt
    , SwaggerTypes.tCollInt, SwaggerTypes.tCollJInt, SwaggerTypes.tCollXInt
    , SwaggerTypes.tOptionCollInt, SwaggerTypes.tOptionCollJInt, SwaggerTypes.tOptionCollXInt
    //Long
    , SwaggerTypes.tLong, SwaggerTypes.tXLong
    , SwaggerTypes.tOptionLong, SwaggerTypes.tOptionXLong
    , SwaggerTypes.tCollLong, SwaggerTypes.tCollXLong
    , SwaggerTypes.tOptionCollLong, SwaggerTypes.tOptionCollXLong
    //Float
    , SwaggerTypes.tFloat, SwaggerTypes.tXFloat
    , SwaggerTypes.tOptionFloat, SwaggerTypes.tOptionXFloat
    , SwaggerTypes.tCollFloat, SwaggerTypes.tCollXFloat
    , SwaggerTypes.tOptionCollFloat, SwaggerTypes.tOptionCollXFloat
    //Double
    , SwaggerTypes.tDouble, SwaggerTypes.tJDouble, SwaggerTypes.tXDouble
    , SwaggerTypes.tOptionDouble, SwaggerTypes.tOptionJDouble, SwaggerTypes.tOptionXDouble
    , SwaggerTypes.tCollDouble, SwaggerTypes.tCollJDouble, SwaggerTypes.tCollXDouble
    , SwaggerTypes.tOptionCollDouble, SwaggerTypes.tOptionCollJDouble, SwaggerTypes.tOptionCollXDouble
    //BigDecimal
    , SwaggerTypes.tBigDecimal, SwaggerTypes.tJBigDecimal
    , SwaggerTypes.tOptionBigDecimal, SwaggerTypes.tOptionJBigDecimal
    , SwaggerTypes.tCollBigDecimal, SwaggerTypes.tCollJBigDecimal
    , SwaggerTypes.tOptionCollBigDecimal, SwaggerTypes.tOptionCollJBigDecimal
    //Date
    , SwaggerTypes.tDate, SwaggerTypes.tOptionDate
    , SwaggerTypes.tCollDate, SwaggerTypes.tOptionCollDate
  )

  /**
    * check whether given type is a swagger ref type in definitions
     * @param tp
    * @return
    */
  private[this] def isSwaggerRefType(tp: Type): Boolean = ! noneRefTypes.exists(tp <:< _)

  /**
    * A handful of Scala 3-compiled third-party classes on the classpath (observed: cats-effect's
    * `Par` trait, whose abstract type member `ParallelF` has no runtime companion class) trip
    * scala.reflect.runtime's classfile fallback with `AssertionError: no symbol could be loaded
    * from class ...$ParallelF$` - not because the entity's own type is unreflectable, but because
    * resolving its *owner chain* (e.g. the enclosing Http4sXXX.ImplementationsX_Y_Z object, whose
    * signature transitively references IO's companion) walks into that dependency. Any case class
    * nested in such an object hits this identically, so it can't be worked around per-entity;
    * treat it as "can't reflect this one" and keep going rather than 400ing the whole document.
    */
  private[this] def safeGetType(obj: Any): Option[universe.Type] = {
    // NonFatal covers AssertionError too - it excludes only VirtualMachineError, ThreadDeath,
    // InterruptedException, LinkageError and ControlThrowable, none of which this can throw.
    try Some(ReflectUtils.getType(obj)) catch {
      case scala.util.control.NonFatal(e) =>
        logger.warn(s"SwaggerJSONFactory: could not reflect the type of ${obj.getClass.getName}, excluding it from Swagger schema generation: ${e.getMessage}")
        None
    }
  }

  /**
    * get all nested swagger ref type objects
    * @param entities to do extract objects list
    * @return  a list of include original list and nested objects
    */
  private def getAllEntities(entities: List[AnyRef]) = {
    val notNullEntities = entities.filter(null.!=)
    val notSupportYetEntity = entities.filter(_.getClass.getSimpleName.equals(NotSupportedYet.getClass.getSimpleName.replace("$","")))
    val existsEntityTypes: Set[universe.Type] = notNullEntities.flatMap(safeGetType).toSet

    (notSupportYetEntity ::: notNullEntities ::: notNullEntities.flatMap(getNestedRefEntities(_, existsEntityTypes)))
      .distinctBy(_.getClass)
  }

  /**
    * extract all nested swagger ref type objects, exclude given types,
    * swagger ref type is this ref type in swagger definitions, for example : "$ref": "#/definitions/AccountId"
    * @param obj to do extract
    * @param excludeTypes exclude these types
    * @return all nested swagger ref type object, include all deep nested ref object
    */
  private[this] def getNestedRefEntities(obj: Any, excludeTypes: Set[Type]): List[Any] = {

    obj match {
      case (Nil  | None | null) => Nil
      case v if(v.getClass.getName == "scala.Enumeration$Val") => Nil // there is no way to check an object is a Enumeration by call method, so here use ugly way
      case _: EmptyBox => Nil
      case seq: Seq[_] if(seq.isEmpty) => Nil
      case Some(v) => getNestedRefEntities(v, excludeTypes)
      case Full(v) => getNestedRefEntities(v, excludeTypes)
      case coll: Coll[_] => coll.toList.flatMap(getNestedRefEntities(_, excludeTypes))
      case v if(! ReflectUtils.isObpObject(v) && !obj.isInstanceOf[HTTPParam]) => Nil
      case _ => safeGetType(obj) match {
        // Can't reflect this entity's own type (see safeGetType) - it still belongs in the
        // definitions list, but its fields can't be walked, so surface it as a leaf.
        case None => List(obj)
        case Some(entityType) =>
          val constructorParamList = ReflectUtils.getPrimaryConstructor(entityType).paramLists.headOption.getOrElse(Nil)
          // if exclude current obj, the result list tail will be Nil
          val resultTail = if(excludeTypes.exists(entityType.=:=)) Nil else List(obj)

          val refValues: List[Any] = constructorParamList
            .filter(it => isSwaggerRefType(it.info) && !excludeTypes.exists(_.=:=(it.info)))
            .map(it => {
              val paramName = it.name.toString
              val value = ReflectUtils.invokeMethod(obj, paramName)
              if(Objects.isNull(value) && isSwaggerRefType(it.info)) {
                throw new IllegalStateException(s"object ${obj} field $paramName should not be null.")
              }
              value
            }).filterNot(it => it == null || it == Nil || it == None || it.isInstanceOf[EmptyBox])

          refValues.flatMap(getNestedRefEntities(_, excludeTypes)) ::: resultTail
      }
    }

  }

  /**
    * exclude duplicate items for a list, if found duplicate items, previous will be kept
    * @param list to do distinct list
    * @tparam T element type
    * @return no duplicated items
    */
  private[this] implicit class DistinctList[T](list: List[T]) {
    def distinctBy[D](f: T=>D): List[T] = {
      val existsElements = ListBuffer.empty[D]
      val collectElements = ListBuffer.empty[T]
      list.foreach{ it=>
        val checkValue = f(it)
        if(!existsElements.contains(checkValue)) {
          existsElements += checkValue
          collectElements += it
        }
      }
      collectElements.toList
    }
  }

  /**
    * @param resourceDocList 
    * @return - JValue, with Swagger format, many following Strings
    *         {
    *         "definitions":{
    *           "ExampleJSON":
    *           { 
    *             "required": ["id","name","bank","banks"],    
    *             "properties":
    *             { 
    *               "id": {"type":"integer", "format":"int32"}, 
    *               "Tesobe": {"type":"string"},
    *               "bank": {"$ref": "#/definitions/BankJSON"},
    *               "banks": {"type": "array", "items":{"$ref": "#/definitions/BanksJSON"}
    *             }
    *           }
    *         } ...
    */
  // link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#definitionsObject
  def loadDefinitions(resourceDocList: List[JSONFactory1_4_0.ResourceDocJson], allSwaggerDefinitionCaseClasses: Seq[AnyRef]): org.json4s.JValue = {

    // filter function: not null and not type of EnumValue, PrimaryDataBody, JObject, JArray.
    val predicate: Any => Boolean = {
      val excludeTypes: Set[Class[_]] = Set(classOf[EnumValue], classOf[ListResult[_]], classOf[PrimaryDataBody[_]], classOf[JValue])
      any => any != null && !excludeTypes.exists(_.isInstance(any))
    }

    val docEntityExamples: List[AnyRef] = (List(notSupportedYet):::
                                           resourceDocList.map(_.example_request_body.asInstanceOf[AnyRef]) :::
                                           resourceDocList.map(_.success_response_body.asInstanceOf[AnyRef])
                                          ).filter(predicate)

    val allDocExamples = getAllEntities(docEntityExamples)
    val allDocExamplesClazz = allDocExamples.map(_.getClass)

    val definitionExamples = getAllEntities(allSwaggerDefinitionCaseClasses.toList)
    val definitionExamplesClazz = definitionExamples.map(_.getClass)

    val examples = definitionExamples.filter(it => allDocExamplesClazz.contains(it.getClass)) :::
      allDocExamples.filterNot(it => definitionExamplesClazz.contains(it.getClass))


    val translatedEntities = examples
                              .distinctBy(_.getClass)
                              .filter(predicate)
                              .map(translateEntity)

    val errorMessages: Set[AnyRef] = resourceDocList.flatMap(_.error_response_bodies).toSet

    val errorDefinitions = ErrorMessages.allFields
      .filterNot(null.==)
      .filter(it => errorMessages.contains(it._2))
      .toList
      .map(it => {
        val (errorName, errorMessage) = it
        val escapedMessage = escapeJsonString(errorMessage.toString)
        s""""Error$errorName": {
        |  "properties": {
        |    "message": {
        |       "type": "string",
        |       "example": "$escapedMessage"
        |    }
        |  }
         }""".stripMargin
      })



    //Add a comma between elements of a list and make a string 
    val particularDefinitionsPart = (
        errorDefinitions :::
        translatedEntities
      ) mkString (",")
  
    //Make a final string
    val definitions = "{\"definitions\":{" + particularDefinitionsPart + "}}"
    //Make a jsonAST from a string
    try {
      parse(definitions)
    } catch {
      case e: Exception =>
        logger.error(s"Failed to parse Swagger definitions JSON: ${e.getMessage}")
        logger.error(s"JSON was: ${definitions.take(500)}...")
        throw new RuntimeException(s"Invalid JSON in Swagger definitions generation. This may be due to unescaped special characters in examples or field names. Error: ${e.getMessage}", e)
    }
  }


  /**
    * get entity type by type and value,
    * if tp is not generic, extract entity type from value
    * else if tp is generic but the nested type parameter is abstract, extract entity type from value
    * else get the nested type argument from tp
    * @param tp  type of to do extract entity type
    * @param value the value of type tp
    * @return entity type name
    */
  private def getRefEntityName(tp: Type, value: Any): String = {
    val nestTypeArg = ReflectUtils.getNestFirstTypeArg(tp)

    def isEntityAbstract = {
      val typeSymbol = nestTypeArg.typeSymbol
      typeSymbol.isAbstract || (typeSymbol.isClass && typeSymbol.asClass.isAbstract)
    }

    // if tp is not generic type or tp is generic type but it's nested type argument is abstract, then get the nested type by value
    val entityType = tp.typeArgs match {
      case args if value != null && (args.isEmpty || isEntityAbstract) => {
        val nestValue = value match {
          case Some(head::_) => head
          case Some(v) => v
          case Some(head)::_ => head
          case head::_ => head
          case other => other
        }
        ReflectUtils.getType(nestValue)
      }
      case _ => nestTypeArg
    }

    entityType.typeSymbol.name.toString
  }

  private def isEnumeration(tp: Type) = tp.typeSymbol.isClass && tp.typeSymbol.asClass.fullName == "scala.Enumeration.Value"

  // enumType takes the place of the old T: TypeTag context bound - see SwaggerTypes' docstring
  // for why Scala 3 cannot synthesise one for a generic type parameter here. Call sites pass a
  // SwaggerTypes.tXxx constant, e.g. isNestEnumeration(SwaggerTypes.tOptionWildcard, tp).
  private def isNestEnumeration(enumType: Type, tp: Type): Boolean = {
    def isNestEnum = isEnumeration(ReflectUtils.getNestFirstTypeArg(tp))
    enumType match {
      case t if(tp <:< t && isNestEnum) => true
      case _ => false
    }
  }
}
