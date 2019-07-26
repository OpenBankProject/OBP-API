package code.api.ResourceDocs1_4_0

import java.util.{Date, Objects}

import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util._
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._
import java.lang.{Boolean => JBoolean, Double => JDouble, Float => JFloat, Integer => JInt, Long => JLong}
import java.math.{BigDecimal => JBigDecimal}

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.util.StringHelpers

import scala.collection.mutable.ListBuffer
import code.api.v3_1_0.ListResult
import net.liftweb.common.{EmptyBox, Full}

import scala.reflect.runtime.universe

object SwaggerJSONFactory {
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
  
  case class ResponseObjectSchemaJson(
    `$ref`: String
  )
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
    //TODO, try to make it work with reflection using rd.successResponseBody.extract[BanksJSON], but successResponseBody is JValue, that's tricky
    def setReferenceObject(rd: ResourceDoc): Option[ResponseObjectSchemaJson] = {
      val caseClassName = rd.successResponseBody match {
        case s:scala.Product => s.getClass.getSimpleName
        case _ => "NoSupportYet"
      }
      Some(ResponseObjectSchemaJson(s"#/definitions/${caseClassName}"))
    }

    implicit val formats = CustomJsonFormats.formats

    val infoTitle = "Open Bank Project API"
    val infoDescription = "An Open Source API for Banks. (c) TESOBE Ltd. 2011 - 2018. Licensed under the AGPL and commercial licences."
    val infoContact = InfoContactJson("TESOBE Ltd. / Open Bank Project", "https://openbankproject.com" ,"contact@tesobe.com")
    val infoApiVersion = requestedApiVersion
    val info = InfoJson(infoTitle, infoDescription, infoContact, infoApiVersion.toString)
    val host = APIUtil.getPropsValue("hostname", "unknown host").replaceFirst("http://", "").replaceFirst("https://", "")
    val basePath = "/"
    val schemas = List("http", "https")
    // Paths Object
    // link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#paths-object
    // setting up the following fileds of swagger json,eg apiFunction = bankById
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
            //TODO, this is for Post Body 
            parameters =
              if (rd.requestVerb.toLowerCase == "get" || rd.requestVerb.toLowerCase == "delete"){
                pathParameters
               } else{
                val caseClassName = rd.exampleRequestBody match {
                  case s:scala.Product => s.getClass.getSimpleName
                  case _ => "NoSupportYet"
                }
                OperationParameterBodyJson(schema=ResponseObjectSchemaJson(s"#/definitions/${caseClassName}")) :: pathParameters
              },
            responses =
              rd.requestVerb.toLowerCase match {
                case "get" => 
                  Map(
                    "200" -> ResponseObjectJson(Some("Success"), setReferenceObject(rd)),
                    "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFildNameByValue(rd.errorResponseBodies.head)}")))
                  )
                case "post" =>  
                  Map(
                    "201" -> ResponseObjectJson(Some("Success"), setReferenceObject(rd)),
                    "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFildNameByValue(rd.errorResponseBodies.head)}")))
                  )
                case "put" =>
                  Map(
                    "200" -> ResponseObjectJson(Some("Success"), setReferenceObject(rd)),
                    "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFildNameByValue(rd.errorResponseBodies.head)}")))
                  )
                case "delete" =>
                  Map(
                    "204" -> ResponseObjectJson(Some("Success"), setReferenceObject(rd)),
                    "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFildNameByValue(rd.errorResponseBodies.head)}")))
                  )
                case _ =>
                  Map(
                    "200" -> ResponseObjectJson(Some("Success"), setReferenceObject(rd)),
                    "400"-> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson(s"#/definitions/Error${getFildNameByValue(rd.errorResponseBodies.head)}")))
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

      def isTypeOf[T: TypeTag]: Boolean = paramType <:< typeTag[T].tpe
      def isOneOfType[T: TypeTag, D: TypeTag]: Boolean = isTypeOf[T] || isTypeOf[D]

      paramType match {
        //TODO: this maybe wrong, JValue will have many types: JObject, JBool, JInt, JDouble , but here we just map one type `String`
        case _ if(isTypeOf[JValue])                   => s""""$paramName": {"type":"string","example":"This is a json String."}"""
        case _ if(isTypeOf[Option[JValue]])           => s""""$paramName": {"type":"string","example":"This is a json String."}"""
        case _ if(isTypeOf[List[JValue]])             => s""""$paramName": {"type":"array", "items":{"type":"string","example":"This is a json String."}}"""
        case _ if(isTypeOf[Option[List[JValue]]])     => s""""$paramName": {"type":"array", "items":{"type":"string","example":"This is a json String."}}"""

        //Boolean - 4 kinds
        case _ if(isOneOfType[Boolean, JBoolean])                            => s""""$paramName": {"type":"boolean", "example": "$exampleValue"}"""
        case _ if(isOneOfType[Option[Boolean], Option[JBoolean]])            => s""""$paramName": {"type":"boolean", "example": "$exampleValue"}"""
        case _ if(isOneOfType[List[Boolean], List[JBoolean]])                => s""""$paramName": {"type":"array", "items":{"type": "boolean"}}"""
        case _ if(isOneOfType[Option[List[Boolean]],Option[List[JBoolean]]]) => s""""$paramName": {"type":"array", "items":{"type": "boolean"}}"""

        //String
        case t if(isTypeOf[String] || isEnumeration(t))                                         => s""""$paramName": {"type":"string","example":"$exampleValue"}"""
        case t if(isTypeOf[List[String]] || isNestEnumeration[List[_]](t))                      => s""""$paramName": {"type":"array", "items":{"type": "string"}}"""
        case t if(isTypeOf[Option[List[String]]] || isNestEnumeration[Option[List[_]]](t))      => s""""$paramName": {"type":"array", "items":{"type": "string"}}"""
        case t if(isTypeOf[Option[String]] || isNestEnumeration[Option[_]](t))                  => s""""$paramName": {"type":"string","example":"$exampleValue"}"""

        //Int
        case _ if(isOneOfType[Int, JInt])                             => s""""$paramName": {"type":"integer", "format":"int32","example":"$exampleValue"}"""
        case _ if(isOneOfType[Option[Int], Option[JInt]])             => s""""$paramName": {"type":"integer", "format":"int32","example":"$exampleValue"}"""
        case _ if(isOneOfType[List[Int], List[JInt]])                 => s""""$paramName": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case _ if(isOneOfType[Option[List[Int]], Option[List[JInt]]]) => s""""$paramName": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        //Long
        case _ if(isOneOfType[Long, JLong])                             => s""""$paramName": {"type":"integer", "format":"int64","example":"$exampleValue"}"""
        case _ if(isOneOfType[Option[Long], Option[JLong]])             => s""""$paramName": {"type":"integer", "format":"int64","example":"$exampleValue"}"""
        case _ if(isOneOfType[List[Long], List[JLong]])                 => s""""$paramName": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case _ if(isOneOfType[Option[List[Long]], Option[List[JLong]]]) => s""""$paramName": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        //Float
        case _ if(isOneOfType[Float, JFloat])                             => s""""$paramName": {"type":"number", "format":"float","example":"$exampleValue"}"""
        case _ if(isOneOfType[Option[Float], Option[JFloat]])             => s""""$paramName": {"type":"number", "format":"float","example":"$exampleValue"}"""
        case _ if(isOneOfType[List[Float], List[JFloat]])                 => s""""$paramName": {"type":"array", "items":{"type": "float"}}"""
        case _ if(isOneOfType[Option[List[Float]], Option[List[JFloat]]]) => s""""$paramName": {"type":"array", "items":{"type": "float"}}"""
        //Double
        case _ if(isOneOfType[Double, JDouble])                             => s""""$paramName": {"type":"number", "format":"double","example":"$exampleValue"}"""
        case _ if(isOneOfType[Option[Double], Option[JDouble]])             => s""""$paramName": {"type":"number", "format":"double","example":"$exampleValue"}"""
        case _ if(isOneOfType[List[Double], List[JDouble]])                 => s""""$paramName": {"type":"array", "items":{"type": "double"}}"""
        case _ if(isOneOfType[Option[List[Double]], Option[List[JDouble]]]) => s""""$paramName": {"type":"array", "items":{"type": "double"}}"""
        //BigDecimal
        case _ if(isOneOfType[BigDecimal, JBigDecimal])                             => s""""$paramName": {"type":"string", "format":"double","example":"$exampleValue"}"""
        case _ if(isOneOfType[Option[BigDecimal], Option[JBigDecimal]])             => s""""$paramName": {"type":"string", "format":"double","example":"$exampleValue"}"""
        case _ if(isOneOfType[List[BigDecimal], List[JBigDecimal]])                 => s""""$paramName": {"type":"array", "items":{"type": "string", "format":"double","example":"123.321"}}"""
        case _ if(isOneOfType[Option[List[BigDecimal]], Option[List[JBigDecimal]]]) => s""""$paramName": {"type":"array", "items":{"type": "string", "format":"double","example":"123.321"}}"""
        //Date
        case _ if(isOneOfType[Date, Option[Date]])                   => s""""$paramName": {"type":"string", "format":"date","example":"$exampleValue"}"""
        case _ if(isOneOfType[List[Date], Option[List[Date]]])       => s""""$paramName": {"type":"array", "items":{"type":"string", "format":"date"}}"""

        //List case classes.
        case t if(isOneOfType[List[Option[_]], Option[List[_]]])  => s""""$paramName": {"type": "array", "items":{"$$ref": "#/definitions/${getRefEntityName(t, paramValue)}"}}"""
        case t if(isOneOfType[List[_], Option[_]])                => s""""$paramName": {"type": "array", "items":{"$$ref": "#/definitions/${getRefEntityName(t, paramValue)}"}}"""
        //Single object
        case t                                                    => s""""$paramName": {"$$ref":"#/definitions/${getRefEntityName(t, paramValue)}"}"""
      }
    })

    //Exclude all unrecognised fields and make part of fields definition
    // add comment
    // fields --> "id" : {"type":"integer", "format":"int32"} ,"name" : {"type":"string"} ,"bank": {"$ref":"#/definitions/Bank"} ,"banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val fields: String = paramNameToType mkString (",")
    val definition = s""""${entityType.typeSymbol.name}":{$requiredFieldsPart "properties": {$fields}}"""
    definition
  }

  /**
    * all not swagger ref type
    */
  private[this] val noneRefTypes = List(
    typeOf[JValue]
    , typeOf[Option[JValue]]
    , typeOf[List[JValue]]
    , typeOf[Option[List[JValue]]]

    //Boolean - 4 kinds
    , typeOf[Boolean], typeOf[JBoolean]
    , typeOf[Option[Boolean]], typeOf[ Option[JBoolean]]
    , typeOf[List[Boolean]], typeOf[ List[JBoolean]]
    , typeOf[Option[List[Boolean]]], typeOf[Option[List[JBoolean]]]
    //String
    , typeOf[String]
    , typeOf[Option[String]]
    , typeOf[List[String]]
    , typeOf[Option[List[String]]]
    //Int
    , typeOf[Int], typeOf[JInt]
    , typeOf[Option[Int]], typeOf[ Option[JInt]]
    , typeOf[List[Int]], typeOf[ List[JInt]]
    , typeOf[Option[List[Int]]], typeOf[ Option[List[JInt]]]
    //Long
    , typeOf[Long], typeOf[JLong]
    , typeOf[Option[Long]], typeOf[ Option[JLong]]
    , typeOf[List[Long]], typeOf[ List[JLong]]
    , typeOf[Option[List[Long]]], typeOf[ Option[List[JLong]]]
    //Float
    , typeOf[Float], typeOf[JFloat]
    , typeOf[Option[Float]], typeOf[ Option[JFloat]]
    , typeOf[List[Float]], typeOf[ List[JFloat]]
    , typeOf[Option[List[Float]]], typeOf[ Option[List[JFloat]]]
    //Double
    , typeOf[Double], typeOf[JDouble]
    , typeOf[Option[Double]], typeOf[ Option[JDouble]]
    , typeOf[List[Double]], typeOf[ List[JDouble]]
    , typeOf[Option[List[Double]]], typeOf[ Option[List[JDouble]]]
    //BigDecimal
    , typeOf[BigDecimal], typeOf[JBigDecimal]
    , typeOf[Option[BigDecimal]], typeOf[ Option[JBigDecimal]]
    , typeOf[List[BigDecimal]], typeOf[ List[JBigDecimal]]
    , typeOf[Option[List[BigDecimal]]], typeOf[ Option[List[JBigDecimal]]]
    //Date
    , typeOf[Date], typeOf[Option[Date]]
    , typeOf[List[Date]], typeOf[ Option[List[Date]]]
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
      case seq: Seq[_] => seq.toList.flatMap(getNestedRefEntities(_, excludeTypes))
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

    implicit val formats = CustomJsonFormats.formats

    val docEntityExamples: List[AnyRef] = (resourceDocList.map(_.exampleRequestBody.asInstanceOf[AnyRef]) :::
                                           resourceDocList.map(_.successResponseBody.asInstanceOf[AnyRef])
                                          ).filterNot(Objects.isNull)

    val allDocExamples = getAllEntities(docEntityExamples)
    val allDocExamplesClazz = allDocExamples.map(_.getClass)

    val definitionExamples = getAllEntities(allSwaggerDefinitionCaseClasses.toList)
    val definitionExamplesClazz = definitionExamples.map(_.getClass)

    val examples = definitionExamples.filter(it => allDocExamplesClazz.contains(it.getClass)) :::
      allDocExamples.filterNot(it => definitionExamplesClazz.contains(it.getClass))

    val translatedEntities = examples
                              .distinctBy(_.getClass)
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
      case args if(args.isEmpty || isEntityAbstract) => {
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
