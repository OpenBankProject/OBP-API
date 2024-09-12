package code.api.v1_4_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.cache.Caching
import java.util.Date
import code.api.util.APIUtil.{EmptyBody, PrimaryDataBody, ResourceDoc}
import code.api.util.ApiTag.ResourceDocTag
import code.api.util.Glossary.glossaryItems
import code.api.util.{APIUtil, ApiRole, ConnectorField, CustomJsonFormats, ExampleValue, I18NUtil, PegdownOptions}
import code.bankconnectors.LocalMappedConnector.getAllEndpointTagsBox
import com.openbankproject.commons.model.ListResult
import code.crm.CrmEvent.CrmEvent
import com.openbankproject.commons.model.TransactionRequestTypeCharge
import com.openbankproject.commons.model.{Product, _}
import com.openbankproject.commons.model.enums.I18NResourceDocField
import com.openbankproject.commons.util.{EnumValue, JsonUtils, OBPEnumeration, ReflectUtils}
import net.liftweb.common.Full
import net.liftweb.json
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.{Extraction, Formats, JDouble, JInt, JString}
import net.liftweb.json.JsonAST.{JArray, JBool, JNothing, JObject, JValue}
import net.liftweb.util.StringHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import org.apache.commons.lang3.StringUtils
import scalacache.memoization.cacheKeyExclude

import java.util.regex.Pattern
import java.lang.reflect.Field
import java.util.UUID.randomUUID
import scala.concurrent.duration._

object JSONFactory1_4_0 extends MdcLoggable{
  implicit def formats: Formats = CustomJsonFormats.formats
  case class PostCustomerJson(
                          customer_number : String,
                          legal_name : String,
                          mobile_phone_number : String,
                          email : String,
                          face_image : CustomerFaceImageJson,
                          date_of_birth: Date,
                          relationship_status: String,
                          dependants: Int,
                          dob_of_dependants: List[Date],
                          highest_education_attained: String,
                          employment_status: String,
                          kyc_status: Boolean,
                          last_ok_date: Date)


  case class CustomerJsonV140(customer_id: String,
                          customer_number : String,
                          legal_name : String,
                          mobile_phone_number : String,
                          email : String,
                          face_image : CustomerFaceImageJson,
                          date_of_birth: Date,
                          relationship_status: String,
                          dependants: Int,
                          dob_of_dependants: List[Date],
                          highest_education_attained: String,
                          employment_status: String,
                          kyc_status: Boolean,
                          last_ok_date: Date)

  case class CustomersJsonV140(customers: List[CustomerJsonV140])

  case class CustomerFaceImageJson(url : String, date : Date)

  case class CustomerMessagesJson(messages : List[CustomerMessageJson])
  case class CustomerMessageJson(id : String, date : Date, message : String, from_department : String, from_person : String)

  case class AddCustomerMessageJson(message : String, from_department : String, from_person : String)

  case class LicenseJsonV140(id : String, name : String)

  case class MetaJsonV140(license : LicenseJsonV140)

  case class LocationJsonV140(latitude : Double, longitude : Double)

  case class DriveUpStringJson(hours : String)
  case class LobbyStringJson(hours : String)

  case class BranchJson(id : String,
                        name : String,
                        address : AddressJsonV140,
                        location : LocationJsonV140,
                        lobby : LobbyStringJson,
                        drive_up: DriveUpStringJson,
                        meta : MetaJsonV140,
                        branch_routing: BranchRoutingJsonV141) // This is bad branch_routing should not have been put in V140

  case class BranchesJson (branches : List[BranchJson])

  //case class BranchesJsonV300 (branches : List[BranchJsonV300])


  case class AtmJson(id : String,
                     name : String,
                     address : AddressJsonV140,
                     location : LocationJsonV140,
                     meta : MetaJsonV140)

  case class AtmsJson (atms : List[AtmJson])


  // Note this case class has country (not countryCode) and it is missing county
  case class AddressJsonV140(line_1 : String, line_2 : String, line_3 : String, city : String, state : String, postcode : String, country : String)
  
  case class TransactionRequestBodyJson (
    val to: TransactionRequestAccount,
    val value : AmountOfMoney,
    val description : String
  )
  
  case class TransactionRequestJson (
    id: TransactionRequestId,
    `type` : String,
    from: TransactionRequestAccount,
    details: TransactionRequestBodyJson, 
    body: TransactionRequestBodyJson,
    transaction_ids: String,
    status: String,
    start_date: Date,
    end_date: Date,
    challenge: TransactionRequestChallenge,
    charge: TransactionRequestCharge,
    charge_policy: String,
    counterparty_id :CounterpartyId,
    name :String,
    this_bank_id : BankId,
    this_account_id : AccountId,
    this_view_id :ViewId,
    other_account_routing_scheme : String,
    other_account_routing_address : String,
    other_bank_routing_scheme : String,
    other_bank_routing_address : String,
    is_beneficiary :Boolean
  )

  def createCustomerJson(cInfo : Customer) : CustomerJsonV140 = {

    CustomerJsonV140(
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = cInfo.dateOfBirth,
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents,
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate
    )



  }

  def createCustomersJson(customers : List[Customer]) : CustomersJsonV140 = {
    CustomersJsonV140(customers.map(createCustomerJson))
  }

  def createCustomerMessageJson(cMessage : CustomerMessage) : CustomerMessageJson = {
    CustomerMessageJson(id = cMessage.messageId,
                        date = cMessage.date,
                        message = cMessage.message,
                        from_department = cMessage.fromDepartment,
                        from_person = cMessage.fromPerson)
  }

  def createCustomerMessagesJson(messages : List[CustomerMessage]) : CustomerMessagesJson = {
    CustomerMessagesJson(messages.map(createCustomerMessageJson))
  }

  // Accept a license object and return its json representation
  def createLicenseJson(license : LicenseT) : LicenseJsonV140 = {
    LicenseJsonV140(license.id, license.name)
  }

  def createLocationJson(location : LocationT) : LocationJsonV140 = {
    LocationJsonV140(location.latitude, location.longitude)
  }


  def createDriveUpStringJson(hours : String) : DriveUpStringJson = {
    DriveUpStringJson(hours)
  }

  def createLobbyStringJson(hours : String) : LobbyStringJson = {
    LobbyStringJson(hours)
  }

  def createMetaJson(meta: MetaT) : MetaJsonV140 = {
    MetaJsonV140(createLicenseJson(meta.license))
  }


  // Accept an address object and return its json representation
  def createAddressJson(address : AddressT) : AddressJsonV140 = {
    AddressJsonV140(address.line1, address.line2, address.line3, address.city, address.state, address.postCode, address.countryCode)
  }

  // Branches

  def createBranchJson(branch: BranchT) : BranchJson = {
    BranchJson(branch.branchId.value,
                branch.name,
                createAddressJson(branch.address),
                createLocationJson(branch.location),
                createLobbyStringJson(branch.lobbyString.map(_.hours).getOrElse("")),
                createDriveUpStringJson(branch.driveUpString.map(_.hours).getOrElse("")),
                createMetaJson(branch.meta),
                BranchRoutingJsonV141(
                  scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
                  address = branch.branchRouting.map(_.scheme).getOrElse("")
                )
    )
  }

//  def createBranchJson(branch: BranchT) : BranchJson = {
//    BranchJson(branch.branchId.value,
//      branch.name,
//      createAddressJson(branch.address),
//      createLocationJson(branch.location),
//      createLobbyStringJson(branch.lobbyString.getOrElse("")),
//      createDriveUpStringJson(branch.driveUpString.getOrElse("")),
//      createMetaJson(branch.meta),
//      BranchRoutingJsonV141(
//        scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
//        address = branch.branchRouting.map(_.address).getOrElse("")
//      )
//    )
//  }




  def createBranchesJson(branchesList: List[BranchT]) : BranchesJson = {
    BranchesJson(branchesList.map(createBranchJson))
  }

  // Atms

  def createAtmJson(atm: AtmT) : AtmJson = {
    AtmJson(atm.atmId.value,
      atm.name,
      createAddressJson(atm.address),
      createLocationJson(atm.location),
      createMetaJson(atm.meta))
  }

  def createAtmsJson(AtmsList: List[AtmT]) : AtmsJson = {
    AtmsJson(AtmsList.map(createAtmJson))
  }

  // Products


  case class ProductJson(code : String,
                        name : String,
                        category: String,
                        family : String,
                        super_family : String,
                        more_info_url: String,
                        meta : MetaJsonV140)

  case class ProductsJson (products : List[ProductJson])



  def createProductJson(product: Product) : ProductJson = {
    ProductJson(product.code.value,
      product.name,
      product.category,
      product.family,
      product.superFamily,
      product.moreInfoUrl,
      createMetaJson(product.meta))
  }

  def createProductsJson(productsList: List[Product]) : ProductsJson = {
    ProductsJson(productsList.map(createProductJson))
  }


  // Crm Events
  case class CrmEventJson(
    id: String,
    bank_id: String,
    customer_name : String,
    customer_number : String,
    category : String,
    detail : String,
    channel : String,
    scheduled_date : Date,
    actual_date: Date,
    result: String)

  case class CrmEventsJson (crm_events : List[CrmEventJson])

  def createCrmEventJson(crmEvent: CrmEvent) : CrmEventJson = {
    CrmEventJson(
      id = crmEvent.crmEventId.value,
      bank_id = crmEvent.bankId.value,
      customer_name = crmEvent.customerName,
      customer_number = crmEvent.customerNumber,
      category = crmEvent.category,
      detail = crmEvent.detail,
      channel = crmEvent.channel,
      scheduled_date = crmEvent.scheduledDate,
      actual_date = crmEvent.actualDate,
      result = crmEvent.result)
  }

  def createCrmEventsJson(crmEventList: List[CrmEvent]) : CrmEventsJson = {
    CrmEventsJson(crmEventList.map(createCrmEventJson))
  }


  // Used to describe where an API call is implemented
  case class ImplementedByJson (
    version : String, // Short hand for the version e.g. "1_4_0" means Implementations1_4_0
    function : String // The val / partial function that implements the call e.g. "getBranches"
  )

  case class ResourceDocMeta(
    response_date: Date,
    count: Int
  )
  // Used to describe the OBP API calls for documentation and API discovery purposes
  case class ResourceDocJson(operation_id: String,
                             implemented_by: ImplementedByJson,
                             request_verb: String,
                             request_url: String,
                             summary: String,
                             description: String, //This will be a `HTML` format.
                             description_markdown: String,// This will be a `MARK_DOWN` format.
                             example_request_body: scala.Product,
                             success_response_body: scala.Product,
                             error_response_bodies: List[String],
                             tags: List[String],
                             typed_request_body: JValue, //JSON Schema --> https://spacetelescope.github.io/understanding-json-schema/index.html
                             typed_success_response_body: JValue, //JSON Schema --> https://spacetelescope.github.io/understanding-json-schema/index.html
                             roles: Option[List[ApiRole]] = None,
                             is_featured: Boolean,
                             special_instructions: String,
                             specified_url: String, // Derived value. The Url when called under a certain version.
                             connector_methods: List[String], // this is the connector methods which need to be connected by this endpoint.
                             created_by_bank_id: Option[String] = None
                            )



  // Creates the json resource_docs
  case class ResourceDocsJson (
    resource_docs : List[ResourceDocJson],
    meta: Option[ResourceDocMeta] = None
  )

  /**
   * get the glossaryItem.title by the input string
   * @param parameter from the request URL, eg: BANK_ID
   * @return Bank.bank_id
   */
  def getGlossaryItemTitle(parameter: String): String = {
    def isUrlParameter(): Boolean = {
      List(
        "BANK_ID", 
        "ACCOUNT_ID", 
        "CUSTOMER_ID",
        "TRANSACTION_ID",
        "ATTRIBUTE_ID",
        "VIEW_ID",
        "USER_ID",
        "PRODUCT_CODE",
        "PRODUCT_ID",
        "OPERATION_ID",
        "ENDPOINT_TAG_ID",
      ).exists(_ == parameter)
    }
    parameter match {
      case _ if isUrlParameter() =>
        glossaryItems
          .find(_.title.toLowerCase.contains(s"${parameter.toLowerCase}"))
          .map(_.title).getOrElse("").replaceAll(" ","-")
      case _ =>
        glossaryItems
          .find(_.title.toLowerCase.equals(s"${parameter.toLowerCase}"))
          .map(_.title).getOrElse("").replaceAll(" ","-")
    }
  }
  
  /**
   * will find the ExampleValue.bankIdExample by the parameter(BANK_ID),and return the ExampleValue.bankIdExample.value
   * @also see the usage from JSONFactory1_4_0Test 
   * @param parameter from the request URL, eg: BANK_ID
   * @return ExampleValue.bankIdExample.value ,eg: gh.29.uk
   */
  def getExampleFieldValue(parameter: String): String = {
    val exampleValueFieldName = APIUtil.firstCharToLowerCase(StringHelpers.camelify(parameter.toLowerCase).capitalize)+"Example"
    ExampleValue.exampleNameToValue.get(exampleValueFieldName) match {
      case Some(ConnectorField(value, _)) => value
      case _ =>
        //The ExampleValue are not totally finished, lots of fields are missing here. so we first hide them. and show them in the log
        logger.trace(s"getExampleFieldValue: there is no $exampleValueFieldName variable in ExampleValue object")
        parameter
    }
  }

  //urlParameters can be /xxx/banks/BANK_ID/accounts/ACCOUNT_ID?some=one
  //List(BANK_ID,ACCOUNT_ID, ...)
  val isPathParam = {
    val pattern = Pattern.compile("[A-Z_]+")
    (str: String) => pattern.matcher(str).matches()
  }
  /**
   * prepare the markdown string for each parameter from the URL.
   * @also see the usage from JSONFactory1_4_0Test
   * @param requestUrl eg: /obp/v4.0.0/banks/BANK_ID/accounts/account_ids/private
   * @return list all the parameters, eg:
   *         **URL Parameters**:
   *         [BANK_ID](/glossary#Bank.bank_id):gh.29.uk
   */
  def prepareUrlParameterDescription(requestUrl: String, urlParametersI18n: String): String = {
    val noQueryParamUrl = StringUtils.substringBefore(requestUrl, "?")
    //1rd: get the parameters from URL:
    val findMatches = StringUtils.split(noQueryParamUrl, "/")
      .filter(isPathParam)

    if(findMatches.nonEmpty) {
      val urlParameters: List[String] = findMatches.toList.sorted
      val parametersDescription: List[String] = urlParameters.map(i => prepareDescription(i, Nil))
      parametersDescription.mkString(s"\n\n\n**$urlParametersI18n**", "", "\n")
    } else {
      ""
    }
  }

  /**
   * this will create the markdown description for the parameter. 
   * @param parameter BANK_ID
   * @return [BANK_ID](/glossary#Bank.bank_id):gh.29.uk 
   */
  def prepareDescription(parameter: String, optionalTypeFields: List[(String, Boolean)]): String = {
    val glossaryItemTitle = getGlossaryItemTitle(parameter)
    val exampleFieldValue = getExampleFieldValue(parameter)
    def boldIfMandatory() = {
      optionalTypeFields.exists(i => i._1 == parameter && i._2 == false) match {
        case true =>
          s"**$parameter**"
        case false =>
          s"$parameter"
      }
    }
    if(glossaryItemTitle.contains("jsonstring")){
      "" 
    } else {
    s"""
       |
       |[${boldIfMandatory()}](/glossary#$glossaryItemTitle): $exampleFieldValue
       |
       |""".stripMargin
  }
  }

  def getAllFields(jsonBody: scala.Product): List[Field] = {
    def loopAllFields(rootFields: List[Field]) = {
      val fields = for {
        field <- jsonBody.productIterator.toList if (field.isInstanceOf[scala.Product] && field != jsonBody)
        fields = getAllFields(field.asInstanceOf[scala.Product])
      } yield
        fields
      (rootFields ++ fields.flatten).toSet.toList
    }

    //The root level is a list: eg: List[Users]
    if(jsonBody.isInstanceOf[List[Any]] && jsonBody.productIterator.toList.nonEmpty){
      val rootFields: List[Field] = jsonBody.productIterator.toSet.head.getClass.getDeclaredFields.toList
      loopAllFields(rootFields)
    }else {
      jsonBody match {
        case JvalueCaseClass(jValue) =>
          val types = Nil
          types
        case _ =>
          val rootFields: List[Field] = jsonBody.getClass().getDeclaredFields().toSet.toList
          loopAllFields(rootFields)
      }
    }
  }
  
  def checkFieldOption(jsonBody: scala.Product, rootFields: List[Field]) = {
    val types = rootFields.map(f => (f.getName(), f.getType().getCanonicalName().contains("Option")))
    (decompose(jsonBody), types)
  }
  
    
  def prepareJsonFieldDescription(jsonBody: scala.Product, jsonType: String, jsonRequestBodyFieldsI18n: String, jsonResponseBodyFieldsI18n: String): String = {
    val allFields = getAllFields(jsonBody)
    val (jsonBodyJValue: json.JValue, allFieldsAndOptionStatus) = checkFieldOption(jsonBody, allFields)
    // Group by is mandatory criteria and sort those 2 groups by name of the field
    val jsonBodyFieldsOptional = JsonUtils.collectFieldNames(jsonBodyJValue).keySet.toList
      .filter(x => allFieldsAndOptionStatus.exists(i => i._1 == x && i._2 == true)).sorted
    val jsonBodyFieldsMandatory = JsonUtils.collectFieldNames(jsonBodyJValue).keySet.toList
      .filter(x => allFieldsAndOptionStatus.exists(i => i._1 == x && i._2 == false)).sorted
    val jsonBodyFields = jsonBodyFieldsMandatory ::: jsonBodyFieldsOptional

    val jsonFieldsDescription = jsonBodyFields.map(i => prepareDescription(i, allFieldsAndOptionStatus))
    
    val jsonTitleType = if (jsonType.contains("request")) s"\n\n\n**$jsonRequestBodyFieldsI18n**\n\n" else  s"\n\n\n**$jsonResponseBodyFieldsI18n**\n\n"

    jsonFieldsDescription.mkString(jsonTitleType,"","\n")
  }
  
  //cache key will only contain "operationId + locale"
  
  def createLocalisedResourceDocJsonCached(
    operationId: String, // this will be in the cacheKey
    locale: Option[String],// this will be in the cacheKey
    resourceDocUpdatedTags: ResourceDoc,
    isVersion4OrHigher:Boolean,
    urlParametersI18n:String ,
    jsonRequestBodyFieldsI18n:String,
    jsonResponseBodyFieldsI18n:String
  ): ResourceDocJson = {
      val fieldsDescription =
        if (resourceDocUpdatedTags.tags.toString.contains("Dynamic-Entity")
          || resourceDocUpdatedTags.tags.toString.contains("Dynamic-Endpoint")
          || resourceDocUpdatedTags.roles.toString.contains("DynamicEntity")
          || resourceDocUpdatedTags.roles.toString.contains("DynamicEntities")
          || resourceDocUpdatedTags.roles.toString.contains("DynamicEndpoint")) {
          ""
        } else {
          //1st: prepare the description from URL
          val urlParametersDescription: String = prepareUrlParameterDescription(resourceDocUpdatedTags.requestUrl, urlParametersI18n)
          //2rd: get the fields description from the post json body:
          val exampleRequestBodyFieldsDescription =
            if (resourceDocUpdatedTags.requestVerb == "POST") {
              prepareJsonFieldDescription(resourceDocUpdatedTags.exampleRequestBody, "request", jsonRequestBodyFieldsI18n, jsonResponseBodyFieldsI18n)
            } else {
              ""
            }
          //3rd: get the fields description from the response body:
          //response body can be a nest class, need to loop all the fields.
          val responseFieldsDescription = prepareJsonFieldDescription(resourceDocUpdatedTags.successResponseBody, "response", jsonRequestBodyFieldsI18n, jsonResponseBodyFieldsI18n)
          urlParametersDescription ++ exampleRequestBodyFieldsDescription ++ responseFieldsDescription
        }

        val resourceDocDescription = I18NUtil.ResourceDocTranslation.translate(
          I18NResourceDocField.DESCRIPTION,
          resourceDocUpdatedTags.operationId,
          locale,
          resourceDocUpdatedTags.description.stripMargin.trim
        )
        val description = resourceDocDescription ++ fieldsDescription
        val summary = resourceDocUpdatedTags.summary.replaceFirst("""\.(\s*)$""", "$1") // remove the ending dot in summary
        val translatedSummary = I18NUtil.ResourceDocTranslation.translate(I18NResourceDocField.SUMMARY, resourceDocUpdatedTags.operationId, locale, summary)

       val resourceDoc = ResourceDocJson(
          operation_id = resourceDocUpdatedTags.operationId,
          request_verb = resourceDocUpdatedTags.requestVerb,
          request_url = resourceDocUpdatedTags.requestUrl,
          summary = translatedSummary,
          // Strip the margin character (|) and line breaks and convert from markdown to html
          description = PegdownOptions.convertPegdownToHtmlTweaked(description), //.replaceAll("\n", ""),
          description_markdown = description,
          example_request_body = resourceDocUpdatedTags.exampleRequestBody,
          success_response_body = resourceDocUpdatedTags.successResponseBody,
          error_response_bodies = resourceDocUpdatedTags.errorResponseBodies,
          implemented_by = ImplementedByJson(resourceDocUpdatedTags.implementedInApiVersion.fullyQualifiedVersion, resourceDocUpdatedTags.partialFunctionName), // was resourceDocUpdatedTags.implementedInApiVersion.noV
          tags = resourceDocUpdatedTags.tags.map(i => i.tag),
          typed_request_body = createTypedBody(resourceDocUpdatedTags.exampleRequestBody),
          typed_success_response_body = createTypedBody(resourceDocUpdatedTags.successResponseBody),
          roles = resourceDocUpdatedTags.roles,
          is_featured = resourceDocUpdatedTags.isFeatured,
          special_instructions = PegdownOptions.convertPegdownToHtmlTweaked(resourceDocUpdatedTags.specialInstructions.getOrElse("").stripMargin),
          specified_url = resourceDocUpdatedTags.specifiedUrl.getOrElse(""),
          connector_methods = resourceDocUpdatedTags.connectorMethods,
          created_by_bank_id = if (isVersion4OrHigher) resourceDocUpdatedTags.createdByBankId else None // only for V400 we show the bankId
        )

        logger.trace(s"createLocalisedResourceDocJsonCached value is $resourceDoc")
        resourceDoc
    }
  
  
  def createLocalisedResourceDocJson(rd: ResourceDoc, isVersion4OrHigher:Boolean, locale: Option[String], urlParametersI18n:String ,jsonRequestBodyFieldsI18n:String, jsonResponseBodyFieldsI18n:String) : ResourceDocJson = {
    // We MUST recompute all resource doc values due to translation via Web UI props --> now need to wait $CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL seconds
    val userDefinedEndpointTags = getAllEndpointTagsBox(rd.operationId).map(endpointTag =>ResourceDocTag(endpointTag.tagName))
    val resourceDocWithUserDefinedEndpointTags: ResourceDoc = rd.copy(tags = userDefinedEndpointTags++ rd.tags)
    
    val cacheKey = s"operationId:${resourceDocWithUserDefinedEndpointTags.operationId}-locale:$locale- isVersion4OrHigher:$isVersion4OrHigher".intern()
    val cacheValueFromRedis = Caching.getLocalisedResourceDocCache(cacheKey)
    
    if(cacheValueFromRedis.isDefined){
      json.parse(cacheValueFromRedis.get).extract[ResourceDocJson] 
    }else{
      val resourceDocJson = createLocalisedResourceDocJsonCached(
        resourceDocWithUserDefinedEndpointTags.operationId,
        locale: Option[String],
        resourceDocWithUserDefinedEndpointTags,
        isVersion4OrHigher: Boolean,
        urlParametersI18n: String,
        jsonRequestBodyFieldsI18n: String,
        jsonResponseBodyFieldsI18n: String
      )
      val jsonString = json.compactRender(Extraction.decompose(resourceDocJson))
      Caching.setLocalisedResourceDocCache(cacheKey,jsonString)
      
      resourceDocJson
    }
    
  }

  def createResourceDocsJson(resourceDocList: List[ResourceDoc], isVersion4OrHigher:Boolean, locale: Option[String]) : ResourceDocsJson = {
    val urlParametersI18n = I18NUtil.ResourceDocTranslation.translate(
      I18NResourceDocField.URL_PARAMETERS,
      "resourceDocUrlParametersString_i180n",
      locale,
      "URL Parameters:"
    )

    val jsonRequestBodyFields = I18NUtil.ResourceDocTranslation.translate(
      I18NResourceDocField.JSON_REQUEST_BODY_FIELDS,
      "resourceDocJsonRequestBodyFieldsString_i180n",
      locale,
      "JSON request body fields:"
    )
    val jsonResponseBodyFields = I18NUtil.ResourceDocTranslation.translate(
      I18NResourceDocField.JSON_RESPONSE_BODY_FIELDS,
      "resourceDocJsonResponseBodyFieldsString_i180n",
      locale,
      "JSON response body fields:"
    )
    
    if(isVersion4OrHigher){
      ResourceDocsJson(
        resourceDocList.map(createLocalisedResourceDocJson(_,isVersion4OrHigher, locale, urlParametersI18n, jsonRequestBodyFields, jsonResponseBodyFields)),
        meta=Some(ResourceDocMeta(new Date(), resourceDocList.length))
      )
    } else {
      ResourceDocsJson(resourceDocList.map(createLocalisedResourceDocJson(_,false, locale, urlParametersI18n, jsonRequestBodyFields, jsonResponseBodyFields)))
    }
  }
  
  //please check issue first: https://github.com/OpenBankProject/OBP-API/issues/877
  //change: 
  // { "first_name": "George"} -->  {"type": "object","properties": {"first_name": {"type": "string" }
  /**
    * 
    * @param entity can be any entity, primitive or any references 
    * @param isArray is a Array or not. If it is Array the output format is different .
    * @return
    *         the OBP type format. 
    */
  def translateEntity(entity: Any, isArray:Boolean): String = {
    val extractedEntity = entity match {
      case Full(v) => v
      case Some(v) => v
      case v => v
    }

    val mapOfFields: Map[String, Any] = extractedEntity match {

      case ListResult(name, results) => Map((name, results))
      case JObject(jFields) => jFields.map(it => (it.name, it.value)).toMap
      case _ => ReflectUtils.getFieldValues(extractedEntity.asInstanceOf[AnyRef])()
    }

    val convertParamName = (name: String) =>  extractedEntity match {
      case _ : JsonFieldReName => StringHelpers.snakify(name)
      case _ => name
    }

    val properties = for {
      (name, value) <- mapOfFields
      key = convertParamName(name)
      if(value != None)
    } yield {
      value match {
        //Date -- should be the first
        case i: Date                       => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case Some(i: Date)                 => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case List(i: Date, _*)             => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}""" 
        case Some(List(i: Date, _*))       => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}"""

        //Boolean - 4 kinds
        case _: Boolean | _: JBool         => "\""  + key + """": {"type":"boolean"}"""
        case Some(i: Boolean)              => "\""  + key + """": {"type":"boolean"}"""
        case List(i: Boolean, _*)          => "\""  + key + """": {"type": "array","items": {"type": "boolean"}}"""
        case Some(List(i: Boolean, _*))    => "\""  + key + """": {"type": "array","items": {"type": "boolean"}}"""
          
        //String --> Some field calleds `date`, we will treat the field as a `date` object.
        //String --> But for some are not, eg: `date_of_birth` and `future_date` in V300Custom  
        case i: String if(key.contains("date")&& i.length != "20181230".length)  => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case Some(i: String) if(key.contains("date")&& i.length != "20181230".length)  => "\""  + key + """": {"type": "string","format": "date-time"}"""
        case List(i: String, _*) if(key.contains("date")&& i.length != "20181230".length)  => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}"""
        case Some(List(i: String, _*)) if(key.contains("date")&& i.length != "20181230".length)  => "\""  + key + """": {"type": "array","items": {"type": "string","format": "date-time"}}"""
         
        //String-->
        case _: String| _:JString          => "\""  + key + """": {"type":"string"}"""
        case e: EnumValue                  => {
          val enumValues = OBPEnumeration.getValuesByInstance(e)
            .map(it => s""""$it"""")
            .mkString("[", ", ", "]")
          "\""  + key + s"""": {"type":"string","enum": $enumValues}"""
        }
        case Some(i: String)               => "\""  + key + """": {"type":"string"}"""
        case List(i: String, _*)           => "\""  + key + """": {"type": "array","items": {"type": "string"}}""" 
        case Some(List(i: String, _*))     => "\""  + key + """": {"type": "array","items": {"type": "string"}}"""
        case Array(i: String, _*)           => "\""  + key + """": {"type": "array","items": {"type": "string"}}"""
        case Some(Array(i: String, _*))     => "\""  + key + """": {"type": "array","items": {"type": "string"}}"""
        //Int
        case _: Int | _:JInt               => "\""  + key + """": {"type":"integer"}"""
        case Some(i: Int)                  => "\""  + key + """": {"type":"integer"}"""
        case List(i: Int, _*)              => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        case Some(List(i: Int, _*))        => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        //Long
        case i: Long                       => "\""  + key + """": {"type":"integer"}"""
        case Some(i: Long)                 => "\""  + key + """": {"type":"integer"}"""
        case List(i: Long, _*)             => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        case Some(List(i: Long, _*))       => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        //Float
        case i: Float                      => "\""  + key + """": {"type":"number"}"""
        case Some(i: Float)                => "\""  + key + """": {"type":"number"}"""
        case List(i: Float, _*)            => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        case Some(List(i: Float, _*))      => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        //Double
        case _: Double | _: JDouble        => "\""  + key + """": {"type":"number"}"""
        case Some(i: Double)               => "\""  + key + """": {"type":"number"}"""
        case List(i: Double, _*)           => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        case Some(List(i: Double, _*))     => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        //BigInt
        case i: BigInt                     => "\""  + key + """": {"type":"integer"}"""
        case Some(i: BigInt)               => "\""  + key + """": {"type":"integer"}"""
        case List(i: BigInt, _*)           => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        case Some(List(i: BigInt, _*))     => "\""  + key + """": {"type": "array","items": {"type": "integer"}}"""
        // BigDecimal
        case i: BigDecimal                 => "\""  + key + """": {"type":"number"}"""
        case Some(i: BigDecimal)           => "\""  + key + """": {"type":"number"}"""
        case List(i: BigDecimal, _*)       => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""
        case Some(List(i: BigDecimal, _*)) => "\""  + key + """": {"type": "array","items": {"type": "number"}}"""

        //List case classes.
        case JArray(List(f,_*))            => "\""  + key + """":""" +translateEntity(f,true)
        case List(f)                       => "\""  + key + """":""" +translateEntity(f,true)
        case List(f,_*)                    => "\""  + key + """":""" +translateEntity(f,true)
        case List(Some(f))                 => "\""  + key + """":""" +translateEntity(f,true)
        case List(Some(f),_*)              => "\""  + key + """":""" +translateEntity(f,true)
        case Some(List(f))                 => "\""  + key + """":""" +translateEntity(f,true)
        case Some(List(f,_*))              => "\""  + key + """":""" +translateEntity(f,true)
        //Single object
        case Some(f)                       => "\""  + key + """":""" +translateEntity(f,false)
        case null                          => "\""  + key + """":{"type":"null"}"""
        case f                             => "\""  + key + """":""" +translateEntity(f,f.getClass().isArray())
        // TODO resolve the warning patterns after a variable pattern cannot match (SLS 8.1.1)
        // case _ => "unknown"
      }
    }
    //Exclude all unrecognised fields and make part of fields definition
    // add comment and filter unknow
    // fields --> "id" : {"type":"integer", "format":"int32"} ,"name" : {"type":"string"} ,"bank": {"$ref":"#/definitions/Bank"} ,"banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val fields: String = properties filter (_.contains("unknown") == false) mkString (",")
    //val definition = "\"" + entity.getClass.getSimpleName + "\":{" + requiredFieldsPart + """"properties": {""" + fields + """}}"""
    val definition = if (isArray)
        """{ "type": "array",  "items" : {"type": "object","properties": {""" + fields + """}}}""" 
      else 
        """{ "type": "object", "properties" : {""" + fields + """}}"""
    definition
  }
  
  def createTypedBody(exampleRequestBody: scala.Product): JValue = {
    def res = translateEntity(exampleRequestBody,false)
    exampleRequestBody match {
      case EmptyBody => JNothing
      case _: PrimaryDataBody[_] => json.parse(res) \ "value"
      case _ => json.parse(res)
    }
  }

  //transaction requests
  def getTransactionRequestBodyFromJson(body: TransactionRequestBodyJsonV140) : TransactionRequestBody = {
    val toAcc = TransactionRequestAccount (
      bank_id = body.to.bank_id,
      account_id = body.to.account_id
    )
    val amount = AmountOfMoney (
      currency = body.value.currency,
      amount = body.value.amount
    )

    TransactionRequestBody (
      to = toAcc,
      value = amount,
      description = body.description
    )
  }
  
  /**
    * package the transactionRequestTypeCharge
    */
  def createTransactionRequestTypesJSON(transactionRequestTypeCharges: TransactionRequestTypeCharge): TransactionRequestTypeJsonV140 = {
    TransactionRequestTypeJsonV140(transactionRequestTypeCharges.transactionRequestTypeId,
      TransactionRequestChargeJsonV140(transactionRequestTypeCharges.chargeSummary,
        AmountOfMoneyJsonV121(transactionRequestTypeCharges.chargeCurrency, transactionRequestTypeCharges.chargeAmount)))
  }

  /**
    * package the transactionRequestTypeCharges
    */
  def createTransactionRequestTypesJSONs(transactionRequestTypeCharges: List[TransactionRequestTypeCharge]): TransactionRequestTypesJsonV140 = {
    TransactionRequestTypesJsonV140(transactionRequestTypeCharges.map(createTransactionRequestTypesJSON))
  }

  case class TransactionRequestAccountJsonV140 (
                             bank_id: String,
                             account_id : String
                            )

  case class TransactionRequestBodyJsonV140 (
                              to: TransactionRequestAccountJsonV140,
                              value : AmountOfMoneyJsonV121,
                              description : String,
                              challenge_type : String
                             )

  case class TransactionRequestJsonV140(
                          id: String,
                          `type`: String,
                          from: TransactionRequestAccountJsonV140,
                          body: TransactionRequestBodyJsonV140,
                          transaction_ids: String,
                          status: String,
                          start_date: Date,
                          end_date: Date,
                          challenge: ChallengeJsonV140
                          )

  case class ChallengeJsonV140 (
                           id: String,
                           allowed_attempts : Int,
                           challenge_type: String
                          )

  case class ChallengeAnswerJSON (
                             id: String,
                             answer : String
                           )

  case class TransactionRequestChargeJsonV140(
    val summary: String,
    val value : AmountOfMoneyJsonV121
  )

  case class TransactionRequestTypeJsonV140(value: String, charge: TransactionRequestChargeJsonV140)

  case class TransactionRequestTypesJsonV140(transaction_request_types: List[TransactionRequestTypeJsonV140])


  // It seems we can't overload this function i.e. have to give it specific name because
  // else cant use it with a nested case class when the top case class is a different version
  def transformToLocationFromV140(locationJsonV140: LocationJsonV140): Location = {
    Location (
      latitude = locationJsonV140.latitude,
      longitude = locationJsonV140.longitude,
      date = None,
      user = None
    )
  }


  def transformV140ToLicence(licenseJsonV140: LicenseJsonV140): License = {
    License (
      id = licenseJsonV140.id,
      name = licenseJsonV140.name
    )
  }


  def transformToMetaFromV140(metaJsonV140: MetaJsonV140): Meta = {
    Meta (
      license = transformV140ToLicence (
        metaJsonV140.license)
    )
  }


  def transformToAddressFromV140(addressJsonV140: AddressJsonV140): Address = {
    Address(
      line1 = addressJsonV140.line_1,
      line2 = addressJsonV140.line_2,
      line3 = addressJsonV140.line_3,
      city = addressJsonV140.city,
      county = None,
      state = addressJsonV140.state,
      postCode = addressJsonV140.postcode,
      countryCode = addressJsonV140.country // May not be a code
    )
  }
  
  //We get rid of JValue in transaction request class. We need keep the response body the same as before. (compatability) 
  def transforOldTransactionRequest(transactionRequest: TransactionRequest) =
    Full(TransactionRequestJson(
      id = transactionRequest.id,
      `type` = transactionRequest.`type`,
      from = transactionRequest.from,
      details = TransactionRequestBodyJson(
        transactionRequest.body.to_sandbox_tan.getOrElse(null), 
        transactionRequest.body.value, 
        transactionRequest.body.description
      ),
      body = TransactionRequestBodyJson(
        transactionRequest.body.to_sandbox_tan.getOrElse(null), 
        transactionRequest.body.value, 
        transactionRequest.body.description
      ),
      transaction_ids = transactionRequest.transaction_ids, 
      status = transactionRequest.status,
      start_date = transactionRequest.start_date,
      end_date = transactionRequest.end_date,
      challenge = transactionRequest.challenge,
      charge = transactionRequest.charge,
      charge_policy = transactionRequest.charge_policy,
      counterparty_id = transactionRequest.counterparty_id,
      name = transactionRequest.name,
      this_bank_id  = transactionRequest.this_bank_id,
      this_account_id  = transactionRequest.this_account_id,
      this_view_id  = transactionRequest.this_view_id,
      other_account_routing_scheme  = transactionRequest.other_account_routing_scheme,
      other_account_routing_address  = transactionRequest.other_account_routing_address,
      other_bank_routing_scheme  = transactionRequest.other_bank_routing_scheme,
      other_bank_routing_address  = transactionRequest.other_bank_routing_address,
      is_beneficiary  = transactionRequest.is_beneficiary
    ))





}
