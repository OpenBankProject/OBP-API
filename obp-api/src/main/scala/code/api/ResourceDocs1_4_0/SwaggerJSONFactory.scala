package code.api.ResourceDocs1_4_0

import java.util.Date

import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages._
import code.api.util._
import net.liftweb
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._

import scala.collection.immutable.ListMap
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

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
  
    //Collect all mandatory fields and make an appropriate string
    // eg return :  "required": ["id","name","bank","banks"],  
    val required =
      for {
        f <- entity.getClass.getDeclaredFields //get all the field name in the class
        if f.getType.toString.contains("Option") == false
      } yield {
        f.getName
      }
    val requiredFields = required.toList mkString("[\"", "\",\"", "\"]")
    //Make part of mandatory fields
    val requiredFieldsPart = if (required.length > 0) """"required": """ + requiredFields + "," else ""
    //Make whole swagger definition of an entity
    
    //Get fields of runtime entities and put they into structure Map(nameOfField -> fieldAsObject)
    //eg: 
    //  ExampleJSON (                   
    //   id = 5,                         
    //   name = "Tesobe",                
    //   bank = Bank("gh.29.uk")        
    //   banks = List(Bank("gh.29.uk")) 
    //  )                               
    // -->
    //   mapOfFields = Map(
    //     id -> 5,
    //     name -> Tesobe,
    //     bank -> Bank(gh.29.uk),
    //     banks -> List(Bank(gh.29.uk))
    //   )
    //TODO this maybe not so useful now, the input is the case-classes now.
    val r = currentMirror.reflect(entity)
    val mapOfFields = r.symbol.typeSignature.members.toStream
      .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s)}
      .map(r => r.symbol.name.toString.trim -> r.get)
      .toMap

    //Iterate over Map and use pattern matching to extract type of field of runtime entity and make an appropriate swagger Data Types for it
    //reference to https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#data-types
    // two pattern matching here: 
    //     Swagger Data Types, eg: (name -> Tesobe) Boolean --> "name": {"type":"string"}
    //     Specific OBP JSON Classes,(bank -> Bank(gh.29.uk)) --> "bank": {"$ref":"#/definitions/Bank"}
    // from up mapOfFields[String, Any]  --> List[String]
    //      id -> 5                      --> "id" : {"type":"integer", "format":"int32"}             
    //      name -> Tesobe,              --> "name" : {"type":"string"}              
    //      bank -> Bank(gh.29.uk),      --> "bank": {"$ref":"#/definitions/Bank"}    
    //      banks -> List(Bank(gh.29.uk) --> "banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val properties = for {
      (key, value) <- mapOfFields
      // Uncomment this to debug problems with json data etc.
      // _ = print("\n val properties for comprehension: " + key + " is " + value)
    } yield {
      value match {
        //TODO: this maybe wrong, JValue will have many types: JObject, JBool, JInt, JDouble , but here we just map one type `String`
        case i:JValue                     => "\""  + key + """": {"type":"string","example":"This is a json String."}"""
        case Some(i:JValue)               => "\""  + key + """": {"type":"string","example":"This is a json String."}"""
        case List(i: JValue, _*)          => "\""  + key + """": {"type":"array", "items":{"type":"string","example":"This is a json String."}}"""
        case Some(List(i: JValue, _*))    => "\""  + key + """": {"type":"array", "items":{"type":"string","example":"This is a json String."}}"""
          
        //Boolean - 4 kinds
        case i: Boolean                    => "\""  + key + """": {"type":"boolean", "example":"""" +i+"\"}"
        case Some(i: Boolean)              => "\""  + key + """": {"type":"boolean", "example":"""" +i+"\"}"
        case List(i: Boolean, _*)          => "\""  + key + """": {"type":"array", "items":{"type": "boolean"}}"""
        case Some(List(i: Boolean, _*))    => "\""  + key + """": {"type":"array", "items":{"type": "boolean"}}"""
        //String   
        case i: String                     => "\""  + key + """": {"type":"string","example":"""" +i+"\"}"
        case Some(i: String)               => "\""  + key + """": {"type":"string","example":"""" +i+"\"}"
        case List(i: String, _*)           => "\""  + key + """": {"type":"array", "items":{"type": "string"}}"""
        case Some(List(i: String, _*))     => "\""  + key + """": {"type":"array", "items":{"type": "string"}}"""
        //Int 
        case i: Int                        => "\""  + key + """": {"type":"integer", "format":"int32","example":"""" +i+"\"}"
        case Some(i: Int)                  => "\""  + key + """": {"type":"integer", "format":"int32","example":"""" +i+"\"}"
        case List(i: Int, _*)              => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case Some(List(i: Int, _*))        => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        //Long
        case i: Long                       => "\""  + key + """": {"type":"integer", "format":"int64","example":"""" +i+"\"}"
        case Some(i: Long)                 => "\""  + key + """": {"type":"integer", "format":"int64","example":"""" +i+"\"}"
        case List(i: Long, _*)             => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case Some(List(i: Long, _*))       => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        //Float
        case i: Float                      => "\""  + key + """": {"type":"number", "format":"float","example":"""" +i+"\"}"
        case Some(i: Float)                => "\""  + key + """": {"type":"number", "format":"float","example":"""" +i+"\"}"
        case List(i: Float, _*)            => "\""  + key + """": {"type":"array", "items":{"type": "float"}}"""
        case Some(List(i: Float, _*))      => "\""  + key + """": {"type":"array", "items":{"type": "float"}}"""
        //Double
        case i: Double                     => "\""  + key + """": {"type":"number", "format":"double","example":"""" +i+"\"}"
        case Some(i: Double)               => "\""  + key + """": {"type":"number", "format":"double","example":"""" +i+"\"}"
        case List(i: Double, _*)           => "\""  + key + """": {"type":"array", "items":{"type": "double"}}"""
        case Some(List(i: Double, _*))     => "\""  + key + """": {"type":"array", "items":{"type": "double"}}"""
        //Date
        case i: Date                       => "\""  + key + """": {"type":"string", "format":"date","example":"""" +i+"\"}"
        case Some(i: Date)                 => "\""  + key + """": {"type":"string", "format":"date","example":"""" +i+"\"}"
        case List(i: Date, _*)             => "\""  + key + """": {"type":"array", "items":{"type":"string", "format":"date"}}"""
        case Some(List(i: Date, _*))       => "\""  + key + """": {"type":"array", "items":{"type":"string", "format":"date"}}"""
       
        //List case classes.  
        case List(f)                       => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        case List(f,_*)                    => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        case List(Some(f))                 => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        case List(Some(f),_*)              => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        case Some(List(f))                 => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        case Some(List(f,_*))              => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        //Single object
        case Some(f)                       => "\""  + key + """": {"$ref":"#/definitions/""" +f.getClass.getSimpleName +"\"}"
        case null                          => "unknown"
        case f                             => "\""  + key + """": {"$ref":"#/definitions/""" +f.getClass.getSimpleName +"\"}"
        // TODO resolve the warning patterns after a variable pattern cannot match (SLS 8.1.1)
        // case _ => "unknown"
      }
    }
    //Exclude all unrecognised fields and make part of fields definition
    // add comment and filter unknow
    // fields --> "id" : {"type":"integer", "format":"int32"} ,"name" : {"type":"string"} ,"bank": {"$ref":"#/definitions/Bank"} ,"banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val fields: String = properties filter (_.contains("unknown") == false) mkString (",")
    //val definition = "\"" + entity.getClass.getSimpleName + "\":{" + requiredFieldsPart + """"properties": {""" + fields + """}}"""
    val definition = "\"" + entity.getClass.getSimpleName + "\":{" +requiredFieldsPart+ """"properties": {""" + fields + """}}"""
    definition
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
  def loadDefinitions(resourceDocList: List[ResourceDoc], allSwaggerDefinitionCaseClasses: Array[AnyRef]): liftweb.json.JValue = {
  
    implicit val formats = CustomJsonFormats.formats
  
    //Translate every entity(JSON Case Class) in a list to appropriate swagger format
    val listOfExampleRequestBodyDefinition =
      for (e <- resourceDocList if e.exampleRequestBody != null)
        yield {
          translateEntity(e.exampleRequestBody)
        }
    val listOfSuccessRequestBodyDefinition =
      for (e <- resourceDocList if e.successResponseBody != null)
        yield {
          translateEntity(e.successResponseBody)
        }
    val listNestingMissDefinition =
      for (e <- allSwaggerDefinitionCaseClasses.toList if e!= null)
        yield {
          translateEntity(e)
        }
  
    val errorMessageList = ErrorMessages.allFields.toList
    val listErrorDefinition =
      for (e <- errorMessageList if e != null)
        yield {
          s""""Error${e._1 }": {
               "properties": {
                 "message": {
                    "type": "string",
                    "example": "${e._2}"
                 }
               }
             }"""
        }
    
    //Add a comma between elements of a list and make a string 
    val particularDefinitionsPart = (
      listErrorDefinition 
        :::listOfExampleRequestBodyDefinition 
        :::listNestingMissDefinition
        :::listOfSuccessRequestBodyDefinition
      ) mkString (",")
  
    //Make a final string
    val definitions = "{\"definitions\":{" + particularDefinitionsPart + "}}"
    //Make a jsonAST from a string
    parse(definitions)
  }
}
