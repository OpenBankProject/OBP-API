package code.api.ResourceDocs1_4_0

import java.util.{Date, Objects}

import code.api.util.APIUtil.{EmptyBody, JArrayBody, PrimaryDataBody, ResourceDoc}
import code.api.util.ErrorMessages._
import code.api.util._
import com.openbankproject.commons.util.{ApiVersion, EnumValue, JsonAble, OBPEnumeration, ReflectUtils}
import net.liftweb
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._
import java.lang.{Boolean => XBoolean, Double => XDouble, Float => XFloat, Integer => XInt, Long => XLong, String => XString}
import java.math.{BigDecimal => JBigDecimal}

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.util.StringHelpers

import scala.collection.mutable.ListBuffer
import code.api.v3_1_0.ListResult
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.JsonUtils
import net.liftweb.common.{EmptyBox, Full}
import net.liftweb.json

import scala.collection.GenTraversableLike
import scala.reflect.runtime.universe

object SwaggerJSONFactory extends MdcLoggable {
  type Coll[T] = GenTraversableLike[T, _]
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
  case class DirectLoginJson(
    `type`: String = "apiKey",
    description: String = "https://github.com/OpenBankProject/OBP-API/wiki/Direct-Login", // TODO replace with Glossary link
    in: String = "header",
    name: String = "Authorization"
  )
  
  case class GatewayLoginJson(
    `type`: String = "apiKey",
    description: String = "https://github.com/OpenBankProject/OBP-API/wiki/Gateway-Login", // TODO replace with Glossary link
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
      json.parse(definition)
    }
  }
  case class JObjectSchemaJson(jObject: JObject) extends ResponseObjectSchemaJson with JsonAble {

    override def toJValue(implicit format: Formats): json.JValue = {
      val schema = buildSwaggerSchema(typeOf[JObject], jObject)
      json.parse(schema)
    }

  }
  case class JArraySchemaJson(jArray: JArray) extends ResponseObjectSchemaJson with JsonAble {

    override def toJValue(implicit format: Formats): json.JValue = {
      val schema = buildSwaggerSchema(typeOf[JArray], jArray)
      json.parse(schema)
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

    def getRequestBodySchema(rd: ResourceDoc): Option[ResponseObjectSchemaJson] =
      getSchema(rd.exampleRequestBody)

    def getResponseBodySchema(rd: ResourceDoc): Option[ResponseObjectSchemaJson] =
      getSchema(rd.successResponseBody)

    private def getSchema(value: Any): Option[ResponseObjectSchemaJson] = {
      value match {
        case EmptyBody => None
        case example: PrimaryDataBody[_] => Some(ResponseObjectSchemaJson(example))
        case example: JObject => Some(JObjectSchemaJson(example))
        case example: ListResult[_] =>
          val listResult = example.asInstanceOf[ListResult[List[_]]]
          Some(ResponseObjectSchemaJson(listResult))
        case s:scala.Product => Some(ResponseObjectSchemaJson(s"#/definitions/${s.getClass.getSimpleName}"))
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
    *   catalogs: Catalogs,
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
  def createSwaggerResourceDoc(resourceDocList: List[ResourceDoc], requestedApiVersion: ApiVersion): SwaggerResourceDoc = {
    
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

    val infoTitle = "Open Bank Project API"
    val infoDescription = "An Open Source API for Banks. (c) TESOBE GmbH. 2011 - 2018. Licensed under the AGPL and commercial licences."
    val infoContact = InfoContactJson("TESOBE GmbH. / Open Bank Project", "https://openbankproject.com" ,"contact@tesobe.com")
    val infoApiVersion = requestedApiVersion
    val info = InfoJson(infoTitle, infoDescription, infoContact, infoApiVersion.toString)
    val host = APIUtil.getPropsValue("hostname", "unknown host").replaceFirst("http://", "").replaceFirst("https://", "")
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
    val paths: ListMap[String, Map[String, OperationObjectJson]] = resourceDocList.groupBy(x => x.specifiedUrl.getOrElse(x.requestUrl)).toSeq.sortBy(x => x._1).map { mrd =>
      
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
      if(path.contains("/{API_VERSION}"))
        pathParameters = OperationParameterPathJson(name="API_VERSION", description="eg:v2.2.0, v3.0.0") :: pathParameters
  
      val operationObjects: Map[String, OperationObjectJson] = mrd._2.map(rd =>
        (rd.requestVerb.toLowerCase,
          OperationObjectJson(
            tags = rd.tags.map(_.tag),
            summary = rd.summary,
            description = PegdownOptions.convertPegdownToHtml(rd.description.stripMargin).replaceAll("\n", ""),
            operationId =
              rd.partialFunctionName match {
                //No longer need this special case since all transaction request Resource Docs have explicit URL
                //case "createTransactionRequest" => s"${rd.apiVersion.toString }-${rd.apiFunction.toString}-${UUID.randomUUID().toString}"
                // Note: The operationId should not start with a number becuase Javascript constructors may use it to build variables.
                case _ => s"${rd.implementedInApiVersion.fullyQualifiedVersion }-${rd.partialFunctionName.toString }"
              },
            parameters ={
              val description = rd.exampleRequestBody match {
                case EmptyBody => ""
                case example: PrimaryDataBody[_] => s"${example.swaggerDataTypeName} type value."
                case s:scala.Product => s"${s.getClass.getSimpleName} object that needs to be added."
                case _ => "NotSupportedYet type that needs to be added."
              }
              ResponseObjectSchemaJson.getRequestBodySchema(rd) match {
                case Some(schema) =>
                  OperationParameterBodyJson(
                    description = description,
                    schema = schema) :: pathParameters
                case None => pathParameters
              }
            },
            responses = {
              val successKey = rd.requestVerb.toLowerCase match {
                case "post" => "201"
                case "delete" => "204"
                case _ => "200"
              }

              Map(
                successKey -> ResponseObjectJson(Some("Success"), ResponseObjectSchemaJson.getResponseBodySchema(rd)),
                "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFieldNameByValue(rd.errorResponseBodies.head)}")))
              )
            }

          )
        )
      ).toMap
      (path, operationObjects.toSeq.sortBy(m => m._1).toMap)
    }(collection.breakOut)

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
      .filterNot(_._2 <:< typeOf[Option[_]])
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
        case Some(v) => v
        case None => ""
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

  private def buildSwaggerSchema(paramType: Type, exampleValue: Any): String = {
    def isTypeOf[T: TypeTag]: Boolean = {
      val tpe2 = typeTag[T].tpe
      paramType <:< tpe2
    }

    def isOneOfType[T: TypeTag, D: TypeTag]: Boolean = isTypeOf[T] || isTypeOf[D]

    def isAnyOfType[T: TypeTag, D: TypeTag, E: TypeTag]: Boolean = isTypeOf[T] || isTypeOf[D] || isTypeOf[E]

    // enum all values to Array structure string: ["red", "green", "other"]
    def enumsToString(enumTp: Type) = {
      val enumType: Type = ReflectUtils.getDeepGenericType(enumTp).head
      OBPEnumeration.getValuesByType(enumType).map(it => s""""$it"""").mkString(",")
    }
    def example = exampleValue match {
        case null => ""
        case v: JValue => s""", "example": "${JsonUtils.toString(v)}" """
        case v => s""", "example": "$v" """
      }

    paramType match {
      case _ if isTypeOf[EnumValue]                    => s""" {"type":"string","enum": [${enumsToString(paramType)}]}"""
      case _ if isTypeOf[Option[EnumValue]]            => s""" {"type":"string","enum": [${enumsToString(paramType)}]}"""
      case _ if isTypeOf[Coll[EnumValue]]             => s""" {"type":"array", "items":{"type":"string","enum": [${enumsToString(paramType)}]}}"""
      case _ if isTypeOf[Option[Coll[EnumValue]]]     => s""" {"type":"array", "items":{"type":"string","enum": [${enumsToString(paramType)}]}}"""

      //Boolean - 4 kinds
      case _ if isAnyOfType[Boolean, JBool, XBoolean]                                         => s""" {"type":"boolean" $example}"""
      case _ if isAnyOfType[Option[Boolean], Option[JBool], Option[XBoolean]]                 => s""" {"type":"boolean" $example}"""
      case _ if isAnyOfType[Coll[Boolean], Coll[JBool], Coll[XBoolean]]                       => s""" {"type":"array", "items":{"type": "boolean"}}"""
      case _ if isAnyOfType[Option[Coll[Boolean]],Option[Coll[JBool]],Option[Coll[XBoolean]]] => s""" {"type":"array", "items":{"type": "boolean"}}"""

      //String
      case t if isAnyOfType[String, JString, XString] || isEnumeration(t)                                                  => s""" {"type":"string" $example}"""
      case t if isAnyOfType[Coll[String], Coll[JString], Coll[XString]] || isNestEnumeration[List[_]](t)                         => s""" {"type":"array", "items":{"type": "string"}}"""
      case t if isAnyOfType[Option[Coll[String]], Option[Coll[JString]], Option[Coll[XString]]] || isNestEnumeration[Option[List[_]]](t) => s""" {"type":"array", "items":{"type": "string"}}"""
      case t if isAnyOfType[Option[String], Option[JString], Option[XString]] || isNestEnumeration[Option[_]](t)                   => s""" {"type":"string" $example}"""

      //Int
      case _ if isAnyOfType[Int, JInt, XInt]                                           => s""" {"type":"integer", "format":"int32" $example}"""
      case _ if isAnyOfType[Option[Int], Option[JInt], Option[XInt]]                   => s""" {"type":"integer", "format":"int32" $example}"""
      case _ if isAnyOfType[Coll[Int], Coll[JInt], Coll[XInt]]                         => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      case _ if isAnyOfType[Option[Coll[Int]], Option[Coll[JInt]], Option[Coll[XInt]]] => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      //Long
      case _ if isOneOfType[Long, XLong]                             => s""" {"type":"integer", "format":"int64" $example}"""
      case _ if isOneOfType[Option[Long], Option[XLong]]             => s""" {"type":"integer", "format":"int64" $example}"""
      case _ if isOneOfType[Coll[Long], Coll[XLong]]                 => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      case _ if isOneOfType[Option[Coll[Long]], Option[Coll[XLong]]] => s""" {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
      //Float
      case _ if isOneOfType[Float, XFloat]                             => s""" {"type":"number", "format":"float" $example}"""
      case _ if isOneOfType[Option[Float], Option[XFloat]]             => s""" {"type":"number", "format":"float" $example}"""
      case _ if isOneOfType[Coll[Float], Coll[XFloat]]                 => s""" {"type":"array", "items":{"type": "float"}}"""
      case _ if isOneOfType[Option[Coll[Float]], Option[Coll[XFloat]]] => s""" {"type":"array", "items":{"type": "float"}}"""
      //Double
      case _ if isAnyOfType[Double, JDouble, XDouble]                                           => s""" {"type":"number", "format":"double" $example}"""
      case _ if isAnyOfType[Option[Double], Option[JDouble], Option[XDouble]]                   => s""" {"type":"number", "format":"double" $example}"""
      case _ if isAnyOfType[Coll[Double], Coll[JDouble], Coll[XDouble]]                         => s""" {"type":"array", "items":{"type": "double"}}"""
      case _ if isAnyOfType[Option[Coll[Double]], Option[Coll[JDouble]], Option[Coll[XDouble]]] => s""" {"type":"array", "items":{"type": "double"}}"""
      //BigDecimal
      case _ if isOneOfType[BigDecimal, JBigDecimal]                             => s""" {"type":"string", "format":"double" $example}"""
      case _ if isOneOfType[Option[BigDecimal], Option[JBigDecimal]]             => s""" {"type":"string", "format":"double" $example}"""
      case _ if isOneOfType[Coll[BigDecimal], Coll[JBigDecimal]]                 => s""" {"type":"array", "items":{"type": "string", "format":"double","example":"123.321"}}"""
      case _ if isOneOfType[Option[Coll[BigDecimal]], Option[Coll[JBigDecimal]]] => s""" {"type":"array", "items":{"type": "string", "format":"double","example":"123.321"}}"""
      //Date
      case _ if isOneOfType[Date, Option[Date]]                   => s""" {"type":"string", "format":"date","example":"${APIUtil.DateWithSecondsFormat.format(exampleValue)}"}"""
      case _ if isOneOfType[Coll[Date], Option[Coll[Date]]]       => s""" {"type":"array", "items":{"type":"string", "format":"date"}}"""

      //List or Array Option data.
      case t if isOneOfType[Coll[Option[_]], Array[Option[_]]]  =>
        val tp = ReflectUtils.getNestTypeArg(t, 0, 0)
        val value = exampleValue match {
          case v: Array[_] => v.headOption.flatMap(_.asInstanceOf[Option[_]]).orNull
          case coll: Coll[_]  => coll.headOption.flatMap(_.asInstanceOf[Option[_]]).orNull
          case _ => null
        }
        s""" {"type": "array", "items":${buildSwaggerSchema(tp, value)}}"""

      // Option List or Array data
      case t if isOneOfType[Option[Coll[_]], Option[Array[_]]] =>
        val tp = ReflectUtils.getNestTypeArg(t, 0, 0)
        val value = exampleValue match {
          case Some(v: Array[_]) if v.nonEmpty => v.head
          case Some(coll :Coll[_]) if coll.nonEmpty  => coll.head
          case _ => null
        }
        s""" {"type": "array", "items":${buildSwaggerSchema(tp, value)}}"""

      // List or Array data
      case t if isOneOfType[Coll[_], Array[_]]   =>
        val tp = ReflectUtils.getNestTypeArg(t, 0)
        val value = exampleValue match {
          case v: Array[_] => v.head
          case coll : Coll[_] if coll.nonEmpty => coll.head
          case _ => null
        }
        s""" {"type": "array", "items":${buildSwaggerSchema(tp, value)}}"""

      //Option data
      case t if isTypeOf[Option[_]]               =>
        val tp = ReflectUtils.getNestTypeArg(t, 0)
        val value = exampleValue match {
          case Some(v) => v
          case None => null
          case v =>  v
        }
        buildSwaggerSchema(tp, value)

      //JValue type
      case _ if exampleValue == JNull || exampleValue == JNothing => throw new RuntimeException("Example should neither be JNothing nor JNull")

      case _ if isTypeOf[JArray]                   =>
        exampleValue match {
          case JArray(v ::_) => s""" {"type": "array", "items":${buildSwaggerSchema(JsonUtils.getType(v), v)} }"""
          case _ =>
            logger.error(s"Empty JArray is not allowed in request body and response body example.")
            throw new RuntimeException("JArray type should not be empty.")
        }

      case _ if isTypeOf[JObject]         =>
        val JObject(jFields) = exampleValue
        val allFields = for {
          JField(name, v) <- jFields
        } yield s""" "$name": ${buildSwaggerSchema(JsonUtils.getType(v), v)} """

        val requiredFields = if (jFields.isEmpty) "" else  jFields.map(_.name).map(name => s""" "$name" """).mkString("[", ",", "]")

        s""" {"type":"object", "properties": { ${allFields.mkString(",")} }, "required": $requiredFields }"""

      case _ if isTypeOf[JValue] =>
        Objects.nonNull(exampleValue)
        val jValue = exampleValue.asInstanceOf[JValue]
        buildSwaggerSchema(JsonUtils.getType(jValue), exampleValue)

      //Single object
      case t                                                    => s""" {"$$ref":"#/definitions/${getRefEntityName(t, exampleValue)}"}"""
    }
  }

  /**
    * all not swagger ref type
    */
  private[this] val noneRefTypes = List(
    typeOf[JValue]
    , typeOf[Option[JValue]]
    , typeOf[Coll[JValue]]
    , typeOf[Option[Coll[JValue]]]

    //Boolean - 4 kinds
    , typeOf[Boolean], typeOf[JBool], typeOf[XBoolean]
    , typeOf[Option[Boolean]], typeOf[ Option[JBool]], typeOf[ Option[XBoolean]]
    , typeOf[Coll[Boolean]], typeOf[ Coll[JBool]], typeOf[ Coll[XBoolean]]
    , typeOf[Option[Coll[Boolean]]], typeOf[Option[Coll[JBool]]], typeOf[Option[Coll[XBoolean]]]
    //String
    , typeOf[String], typeOf[JString], typeOf[XString]
    , typeOf[Option[String]], typeOf[Option[JString]], typeOf[Option[XString]]
    , typeOf[Coll[String]], typeOf[Coll[JString]], typeOf[Coll[XString]]
    , typeOf[Option[Coll[String]]], typeOf[Option[Coll[JString]]] , typeOf[Option[Coll[XString]]]
    //Int
    , typeOf[Int], typeOf[JInt], typeOf[XInt]
    , typeOf[Option[Int]], typeOf[ Option[JInt]], typeOf[ Option[XInt]]
    , typeOf[Coll[Int]], typeOf[ Coll[JInt]], typeOf[ Coll[XInt]]
    , typeOf[Option[Coll[Int]]], typeOf[ Option[Coll[JInt]]], typeOf[ Option[Coll[XInt]]]
    //Long
    , typeOf[Long], typeOf[XLong]
    , typeOf[Option[Long]], typeOf[ Option[XLong]]
    , typeOf[Coll[Long]], typeOf[ Coll[XLong]]
    , typeOf[Option[Coll[Long]]], typeOf[ Option[Coll[XLong]]]
    //Float
    , typeOf[Float], typeOf[XFloat]
    , typeOf[Option[Float]], typeOf[ Option[XFloat]]
    , typeOf[Coll[Float]], typeOf[ Coll[XFloat]]
    , typeOf[Option[Coll[Float]]], typeOf[ Option[Coll[XFloat]]]
    //Double
    , typeOf[Double], typeOf[JDouble], typeOf[XDouble]
    , typeOf[Option[Double]], typeOf[ Option[JDouble]], typeOf[ Option[XDouble]]
    , typeOf[Coll[Double]], typeOf[ Coll[JDouble]], typeOf[ Coll[XDouble]]
    , typeOf[Option[Coll[Double]]], typeOf[ Option[Coll[JDouble]]], typeOf[ Option[Coll[XDouble]]]
    //BigDecimal
    , typeOf[BigDecimal], typeOf[JBigDecimal]
    , typeOf[Option[BigDecimal]], typeOf[ Option[JBigDecimal]]
    , typeOf[Coll[BigDecimal]], typeOf[ Coll[JBigDecimal]]
    , typeOf[Option[Coll[BigDecimal]]], typeOf[ Option[Coll[JBigDecimal]]]
    //Date
    , typeOf[Date], typeOf[Option[Date]]
    , typeOf[Coll[Date]], typeOf[ Option[Coll[Date]]]
  )

  /**
    * check whether given type is a swagger ref type in definitions
     * @param tp
    * @return
    */
  private[this] def isSwaggerRefType(tp: Type): Boolean = ! noneRefTypes.exists(tp <:< _)

  /**
    * get all nested swagger ref type objects
    * @param entities to do extract objects list
    * @return  a list of include original list and nested objects
    */
  private def getAllEntities(entities: List[AnyRef]) = {
    val notNullEntities = entities.filter(null !=)
    val existsEntityTypes: Set[universe.Type] = notNullEntities.map(ReflectUtils.getType).toSet

    (notNullEntities ::: notNullEntities.flatMap(getNestedRefEntities(_, existsEntityTypes)))
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
      case v if(! ReflectUtils.isObpObject(v)) => Nil
      case _ => {
        val entityType = ReflectUtils.getType(obj)
        val constructorParamList = ReflectUtils.getPrimaryConstructor(entityType).paramLists.headOption.getOrElse(Nil)
        // if exclude current obj, the result list tail will be Nil
        val resultTail = if(excludeTypes.exists(entityType =:=)) Nil else List(obj)

        val refValues: List[Any] = constructorParamList
          .filter(it => isSwaggerRefType(it.info) && !excludeTypes.exists(_ =:= it.info))
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
  def loadDefinitions(resourceDocList: List[ResourceDoc], allSwaggerDefinitionCaseClasses: Seq[AnyRef]): liftweb.json.JValue = {

    // filter function: not null and not type of EnumValue, PrimaryDataBody, JObject, JArray.
    val predicate: Any => Boolean = {
      val excludeTypes: Set[Class[_]] = Set(classOf[EnumValue], classOf[ListResult[_]], classOf[PrimaryDataBody[_]], classOf[JValue])
      any => any != null && !excludeTypes.exists(_.isInstance(any))
    }

    val docEntityExamples: List[AnyRef] = (resourceDocList.map(_.exampleRequestBody.asInstanceOf[AnyRef]) :::
                                           resourceDocList.map(_.successResponseBody.asInstanceOf[AnyRef])
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

    val errorMessages: Set[AnyRef] = resourceDocList.flatMap(_.errorResponseBodies).toSet

    val errorDefinitions = ErrorMessages.allFields
      .filterNot(null ==)
      .filter(it => errorMessages.contains(it._2))
      .toList
      .map(it => {
        val (errorName, errorMessage) = it
        s""""Error$errorName": {
        |  "properties": {
        |    "message": {
        |       "type": "string",
        |       "example": "$errorMessage"
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
    parse(definitions)
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

  private def isNestEnumeration[T: TypeTag](tp: Type) = {
    def isNestEnum = isEnumeration(ReflectUtils.getNestFirstTypeArg(tp))
    implicitly[TypeTag[T]].tpe match {
      case t if(tp <:< t && isNestEnum) => true
      case _ => false
    }
  }
}
